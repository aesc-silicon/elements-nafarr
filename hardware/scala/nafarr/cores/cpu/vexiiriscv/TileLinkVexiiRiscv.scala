// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.cores.cpu.vexiiriscv

import spinal.core._
import spinal.lib._
import spinal.core.fiber._
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}
import spinal.lib.com.jtag.Jtag
import spinal.lib.misc.plugin.Hostable

import vexiiriscv.VexiiRiscv
import vexiiriscv.fetch.FetchCachelessPlugin
import vexiiriscv.execute.lsu.LsuCachelessPlugin
import vexiiriscv.misc.{EmbeddedRiscvJtag, PrivilegedPlugin}

object TileLinkVexiiRiscv {
  case class Parameter(
      plugins: Seq[Hostable],
      iBusParam: TileLinkParameter,
      dBusParam: TileLinkParameter
  )
}

/** VexiiRiscv CPU core with two TileLink master ports.
  *
  * Designed to be instantiated inside a ClockingArea. The system clock domain
  * is captured automatically from the enclosing ClockingArea; only the debug
  * clock domain must be supplied explicitly.
  *
  * Parent responsibilities:
  *   iBus / dBus          — connect to the TileLink memory interconnect
  *   mtimerInterrupt      — drive from the machine timer peripheral
  *   globalInterrupt      — drive from the PLIC
  *   jtag                 — connect to top-level IO (slave direction)
  *   ndmreset             — register in debug clock domain and pass to the
  *                          system reset controller
  *
  * Bus parameters are kept in the Parameter case class so that platforms can
  * supply different sizeBytes, sourceWidth, etc. without modifying this file.
  * The bridge logic mirrors VexiiRiscv's own CachelessBusToTilelink /
  * LsuCachelessBusToTilelink classes while staying inside ElemRV's explicit
  * Area-based wiring style (no Node fabric or InterruptNode).
  */
class TileLinkVexiiRiscv(
    parameter: TileLinkVexiiRiscv.Parameter,
    debugCd: ClockDomain
) extends Area {

  // Capture the system clock domain from the enclosing ClockingArea
  private val systemCd = ClockDomain.current

  val iBus = TileLinkBus(parameter.iBusParam)
  val dBus = TileLinkBus(parameter.dBusParam)
  val mtimerInterrupt = Bool()
  val globalInterrupt = Bool()
  val jtag = Jtag()
  val ndmreset = Bool()

  private val fetchPlugin = parameter.plugins.collectFirst { case p: FetchCachelessPlugin => p }.get
  private val lsuPlugin = parameter.plugins.collectFirst { case p: LsuCachelessPlugin => p }.get
  private val privPlugin = parameter.plugins.collectFirst { case p: PrivilegedPlugin => p }.get
  private val jtagPlugin = parameter.plugins.collectFirst { case p: EmbeddedRiscvJtag => p }.get

  // Must be set before VexiiRiscv is instantiated inside the Fiber
  jtagPlugin.setDebugCd(debugCd)

  // All plugin.logic.* accesses must happen inside Fiber setup + awaitBuild.
  // Accessing them from the main elaboration thread deadlocks because the main
  // thread holds the SpinalHDL elaboration lock while plugin threads need it
  // to create signals.
  val fiber = Fiber setup new Area {
    systemCd {
      VexiiRiscv(parameter.plugins)
    }
    Fiber.awaitBuild()

    // Interrupt wiring
    privPlugin.logic.harts(0).int.m.timer := mtimerInterrupt
    privPlugin.logic.harts(0).int.m.software := False
    privPlugin.logic.harts(0).int.m.external := globalInterrupt

    // JTAG and non-debug-module reset (raw signals; caller handles CDC)
    ndmreset := jtagPlugin.logic.ndmreset
    jtag <> jtagPlugin.logic.jtag

    // -----------------------------------------------------------------------
    // Bridge CachelessBus → TileLink (read-only instruction fetch)
    //
    // CachelessBus.cmd: { valid, ready, id, address }
    // CachelessBus.rsp: Flow { id, word, error }      (no backpressure)
    //
    // The transaction id is carried through a.source so the CPU can match
    // out-of-order responses.  The fetch unit always requests exactly one
    // word, so a.size is fixed at dataBytesLog2Up.
    // -----------------------------------------------------------------------
    val iBusNative = fetchPlugin.logic.bus
    iBus.a.valid := iBusNative.cmd.valid
    iBus.a.opcode := Opcode.A.GET()
    iBus.a.param := 0
    iBus.a.size := parameter.iBusParam.dataBytesLog2Up
    iBus.a.source := iBusNative.cmd.id.resized
    iBus.a.address := iBusNative.cmd.address.resized
    iBus.a.mask := B(parameter.iBusParam.dataBytes bits, default -> true)
    iBus.a.data := 0
    iBus.a.corrupt := False
    iBusNative.cmd.ready := iBus.a.ready

    iBusNative.rsp.valid := iBus.d.valid
    iBusNative.rsp.word := iBus.d.data
    iBusNative.rsp.error := iBus.d.denied
    iBusNative.rsp.id := iBus.d.source.resized
    iBus.d.ready := True

    // -----------------------------------------------------------------------
    // Bridge LsuCachelessBus → TileLink (read/write data bus)
    //
    // LsuCachelessBus.cmd: Stream { id, write, address, size, data, mask, … }
    // LsuCachelessBus.rsp: Flow   { id, data, error }   (no backpressure)
    //
    // cmd.size is log2(bytes) — identical encoding to TileLink a.size, no
    // conversion needed.  PUT_FULL_DATA is correct: AddressToMask always
    // produces a mask that is "full" for the given access size (the mask
    // equals the size-aligned byte enables), which is what PUT_FULL_DATA
    // requires.
    //
    // Hazard tracker: stalls a new request when any inflight transaction
    // overlaps the same address bytes or I/O region.  This mirrors
    // LsuCachelessBusToTilelink in VexiiRiscv and is required for correctness
    // when the LSU has more than one outstanding transaction.
    // -----------------------------------------------------------------------
    val dBusNative = lsuPlugin.logic.bus
    val dHashWidth = 4
    val dCmdHash = dBusNative.cmd.address(log2Up(dBusNative.p.dataWidth / 8), dHashWidth bits)

    val dTracker = new Area {
      val pendings = List.fill(dBusNative.p.pendingMax)(new Area {
        val valid = RegInit(False)
        val hash = Reg(UInt(dHashWidth bits))
        val mask = Reg(Bits(dBusNative.p.dataWidth / 8 bits))
        val io = Reg(Bool())
        val hazard = valid && (
          hash === dCmdHash && (mask & dBusNative.cmd.mask).orR ||
            io && dBusNative.cmd.io
        )
      })

      // Release the entry when its response arrives (d.ready is always True)
      when(dBus.d.valid) {
        pendings.onSel(dBus.d.source) { e => e.valid := False }
      }
      // Claim an entry when the request fires on the A channel
      when(dBus.a.valid && dBus.a.ready) {
        pendings.onSel(dBusNative.cmd.id) { e =>
          e.valid := True
          e.hash := dCmdHash
          e.mask := dBusNative.cmd.mask
          e.io := dBusNative.cmd.io
        }
      }
    }

    val dHazard = dTracker.pendings.map(_.hazard).orR

    dBus.a.valid := dBusNative.cmd.valid && !dHazard
    when(dBusNative.cmd.write) {
      dBus.a.opcode := Opcode.A.PUT_FULL_DATA()
    } otherwise {
      dBus.a.opcode := Opcode.A.GET()
    }
    dBus.a.param := 0
    dBus.a.size := dBusNative.cmd.size.resized
    dBus.a.source := dBusNative.cmd.id.resized
    dBus.a.address := dBusNative.cmd.address.resized
    dBus.a.mask := dBusNative.cmd.mask
    dBus.a.data := dBusNative.cmd.data
    dBus.a.corrupt := False
    dBusNative.cmd.ready := dBus.a.ready && !dHazard

    dBusNative.rsp.valid := dBus.d.valid
    dBusNative.rsp.data := dBus.d.data
    dBusNative.rsp.error := dBus.d.denied
    dBusNative.rsp.id := dBus.d.source.resized
    dBus.d.ready := True
  }
}
