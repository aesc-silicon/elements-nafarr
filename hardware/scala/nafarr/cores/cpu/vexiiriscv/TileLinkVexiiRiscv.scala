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
import vexiiriscv.fetch.{FetchCachelessPlugin, FetchL1Plugin}
import vexiiriscv.execute.lsu.{LsuL1Plugin, LsuL1TlPlugin, LsuCachelessBusProvider, LsuCachelessBus}
import vexiiriscv.misc.{EmbeddedRiscvJtag, PrivilegedPlugin}
import vexiiriscv.prediction.GSharePlugin

object TileLinkVexiiRiscv {
  case class Parameter(
      plugins: Seq[Hostable],
      iBusParam: TileLinkParameter,
      dBusParam: TileLinkParameter,
      // Uncached/IO data bus, present only with a D$ (cached -> dBus, IO -> dIoBus).
      dIoBusParam: TileLinkParameter = null
  )
}

/** VexiiRiscv CPU core with two TileLink master ports.
  *
  * Designed to be instantiated inside a ClockingArea. The system clock domain
  * is captured automatically from the enclosing ClockingArea; only the debug
  * clock domain must be supplied explicitly.
  *
  * Parent responsibilities:
  *   iBus / dBus          - connect to the TileLink memory interconnect
  *   mtimerInterrupt      - drive from the machine timer peripheral
  *   globalInterrupt      - drive from the PLIC
  *   jtag                 - connect to top-level IO (slave direction)
  *   ndmreset             - register in debug clock domain and pass to the
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
  val dIoBus = TileLinkBus(
    if (parameter.dIoBusParam != null) parameter.dIoBusParam else parameter.dBusParam
  )
  val mtimerInterrupt = Bool()
  val globalInterrupt = Bool()
  val jtag = Jtag()
  val ndmreset = Bool()

  private val fetchCachelessPlugin = parameter.plugins.collectFirst {
    case p: FetchCachelessPlugin => p
  }
  val fetchL1Plugin = parameter.plugins.collectFirst { case p: FetchL1Plugin => p }
  val lsuL1Plugin = parameter.plugins.collectFirst { case p: LsuL1Plugin => p }
  val gsharePlugin = parameter.plugins.collectFirst { case p: GSharePlugin => p }
  private val lsuCachelessProvider =
    parameter.plugins.collectFirst { case p: LsuCachelessBusProvider => p }.get
  private val lsuL1TlPlugin = parameter.plugins.collectFirst { case p: LsuL1TlPlugin => p }

  /** I-cache bank RAMs, available after generateVerilog (post-blackboxing).
    * Searches the VexiiRiscv component tree for blackboxed Mem components
    * matching the cache bank memories by name.
    */
  def iCacheRams: Seq[Component] = {
    val memNames = fetchL1Plugin.map(_.logic.banks.map(_.mem.getName())).getOrElse(Nil).toSet
    val result = scala.collection.mutable.ArrayBuffer[Component]()
    fiber.vexii.walkComponents { c =>
      if (memNames.contains(c.getName())) result += c
    }
    result.toSeq
  }

  /** I-cache tag/way RAMs, available after generateVerilog (post-blackboxing).
    * Companion to iCacheRams, which only covers the data bank memories: the tag
    * memories live in the plugin's per-way areas (logic.ways) instead.
    */
  def iCacheTagRams: Seq[Component] = {
    val memNames = fetchL1Plugin.map(_.logic.ways.map(_.mem.getName())).getOrElse(Nil).toSet
    val result = scala.collection.mutable.ArrayBuffer[Component]()
    fiber.vexii.walkComponents { c =>
      if (memNames.contains(c.getName())) result += c
    }
    result.toSeq
  }

  /** D-cache bank RAMs (companion to iCacheRams, sourcing the LsuL1Plugin). */
  def dCacheRams: Seq[Component] = {
    val memNames = lsuL1Plugin.map(_.logic.banks.map(_.mem.getName())).getOrElse(Nil).toSet
    val result = scala.collection.mutable.ArrayBuffer[Component]()
    fiber.vexii.walkComponents { c =>
      if (memNames.contains(c.getName())) result += c
    }
    result.toSeq
  }

  /** D-cache tag/way RAMs (companion to iCacheTagRams). */
  def dCacheTagRams: Seq[Component] = {
    val memNames = lsuL1Plugin.map(_.logic.ways.map(_.mem.getName())).getOrElse(Nil).toSet
    val result = scala.collection.mutable.ArrayBuffer[Component]()
    fiber.vexii.walkComponents { c =>
      if (memNames.contains(c.getName())) result += c
    }
    result.toSeq
  }

  /** GShare branch-predictor RAMs (bank mems live under logic.mem.banks). */
  def gshareRams: Seq[Component] = {
    val memNames = gsharePlugin.map(_.logic.mem.banks.map(_.getName()).toSeq).getOrElse(Nil).toSet
    val result = scala.collection.mutable.ArrayBuffer[Component]()
    fiber.vexii.walkComponents { c =>
      if (memNames.contains(c.getName())) result += c
    }
    result.toSeq
  }
  private val privPlugin = parameter.plugins.collectFirst { case p: PrivilegedPlugin => p }.get
  private val jtagPlugin = parameter.plugins.collectFirst { case p: EmbeddedRiscvJtag => p }.get

  // Must be set before VexiiRiscv is instantiated inside the Fiber
  jtagPlugin.setDebugCd(debugCd)

  // All plugin.logic.* accesses must happen inside Fiber setup + awaitBuild.
  // Accessing them from the main elaboration thread deadlocks because the main
  // thread holds the SpinalHDL elaboration lock while plugin threads need it
  // to create signals.
  val fiber = Fiber setup new Area {
    val vexii = systemCd {
      VexiiRiscv(parameter.plugins)
    }
    Fiber.awaitBuild()

    // Interrupt wiring
    privPlugin.logic.harts(0).int.m.timer := mtimerInterrupt
    privPlugin.logic.harts(0).int.m.software := False
    privPlugin.logic.harts(0).int.m.external := globalInterrupt

    // Drive the time CSR (Zicntr). Free-running system-clock counter; only read
    // when the profile enables the time CSR, otherwise pruned.
    systemCd {
      val rdtimeCounter = Reg(UInt(64 bits)) init 0
      rdtimeCounter := rdtimeCounter + 1
      privPlugin.logic.rdtime := rdtimeCounter
    }

    // JTAG and non-debug-module reset (raw signals; caller handles CDC)
    ndmreset := jtagPlugin.logic.ndmreset
    jtag <> jtagPlugin.logic.jtag

    // -----------------------------------------------------------------------
    // Bridge instruction fetch -> TileLink
    //
    // Two variants are supported:
    //   - Cacheless: single-word GET, Flow response (no backpressure)
    //   - L1 cache:  cache-line GET (64 B), Stream response (multiple beats)
    // -----------------------------------------------------------------------
    (fetchCachelessPlugin, fetchL1Plugin) match {
      case (Some(plugin), None) =>
        val iBusNative = plugin.logic.bus
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

      case (None, Some(plugin)) =>
        val iBusNative = plugin.logic.bus
        iBus.a.valid := iBusNative.cmd.valid
        iBus.a.opcode := Opcode.A.GET()
        iBus.a.param := 0
        iBus.a.size := log2Up(iBusNative.p.lineSize)
        iBus.a.source := iBusNative.cmd.id.resized
        iBus.a.address := iBusNative.cmd.address.resized
        iBus.a.mask := B(parameter.iBusParam.dataBytes bits, default -> true)
        iBus.a.data := 0
        iBus.a.corrupt := False
        iBusNative.cmd.ready := iBus.a.ready

        iBusNative.rsp.valid := iBus.d.valid
        iBusNative.rsp.data := iBus.d.data
        iBusNative.rsp.error := iBus.d.denied || iBus.d.corrupt
        iBusNative.rsp.id := iBus.d.source.resized
        iBus.d.ready := iBusNative.rsp.ready

      case _ =>
        SpinalError("VexiiRiscv must have exactly one of FetchCachelessPlugin or FetchL1Plugin")
    }

    // -----------------------------------------------------------------------
    // Bridge LsuCachelessBus -> TileLink (read/write data bus)
    //
    // LsuCachelessBus.cmd: Stream { id, write, address, size, data, mask, ... }
    // LsuCachelessBus.rsp: Flow   { id, data, error }   (no backpressure)
    //
    // cmd.size is log2(bytes) - identical encoding to TileLink a.size, no
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
    def bridgeCacheless(native: LsuCachelessBus, target: TileLinkBus): Unit = {
      // Hazard tracking only matters when more than one transaction can be
      // outstanding; a single-outstanding bus can never overlap itself.
      val hazard = if (native.p.pendingMax > 1) {
        val hashWidth = 4
        val cmdHash = native.cmd.address(log2Up(native.p.dataWidth / 8), hashWidth bits)
        val pendings = List.fill(native.p.pendingMax)(new Area {
          val valid = RegInit(False)
          val hash = Reg(UInt(hashWidth bits))
          val mask = Reg(Bits(native.p.dataWidth / 8 bits))
          val io = Reg(Bool())
          val hazard = valid && (
            hash === cmdHash && (mask & native.cmd.mask).orR ||
              io && native.cmd.io
          )
        })
        when(target.d.valid) {
          pendings.onSel(target.d.source) { e => e.valid := False }
        }
        when(target.a.valid && target.a.ready) {
          pendings.onSel(native.cmd.id) { e =>
            e.valid := True
            e.hash := cmdHash
            e.mask := native.cmd.mask
            e.io := native.cmd.io
          }
        }
        pendings.map(_.hazard).orR
      } else False

      target.a.valid := native.cmd.valid && !hazard
      when(native.cmd.write) {
        target.a.opcode := Opcode.A.PUT_FULL_DATA()
      } otherwise {
        target.a.opcode := Opcode.A.GET()
      }
      target.a.param := 0
      target.a.size := native.cmd.size.resized
      target.a.source := native.cmd.id.resized
      target.a.address := native.cmd.address.resized
      target.a.mask := native.cmd.mask
      target.a.data := native.cmd.data
      target.a.corrupt := False
      native.cmd.ready := target.a.ready && !hazard

      native.rsp.valid := target.d.valid
      native.rsp.data := target.d.data
      native.rsp.error := target.d.denied
      native.rsp.id := target.d.source.resized
      target.d.ready := True
    }

    def idleTileLink(target: TileLinkBus): Unit = {
      target.a.valid := False
      target.a.opcode := Opcode.A.GET()
      target.a.param := 0
      target.a.size := 0
      target.a.source := 0
      target.a.address := 0
      target.a.mask := 0
      target.a.data := 0
      target.a.corrupt := False
      target.d.ready := True
    }

    // With a D$: cached traffic -> dBus (L1 TileLink master), uncached/IO -> dIoBus.
    // Without a D$: all data -> dBus, dIoBus idle.
    val cachelessBus = lsuCachelessProvider.getLsuCachelessBus()
    if (lsuL1TlPlugin.isDefined) {
      dBus <> lsuL1TlPlugin.get.bus
      bridgeCacheless(cachelessBus, dIoBus)
    } else {
      bridgeCacheless(cachelessBus, dBus)
      idleTileLink(dIoBus)
    }
  }
}
