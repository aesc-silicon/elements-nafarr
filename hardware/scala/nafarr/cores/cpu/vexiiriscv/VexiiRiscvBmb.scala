// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.cores.cpu.vexiiriscv

import spinal.core._
import spinal.lib._
import spinal.core.fiber._
import spinal.lib.bus.bmb._
import spinal.lib.com.jtag.Jtag
import spinal.lib.misc.plugin.Hostable

import vexiiriscv.VexiiRiscv
import vexiiriscv.fetch.FetchCachelessPlugin
import vexiiriscv.execute.lsu.LsuCachelessPlugin
import vexiiriscv.misc.{EmbeddedRiscvJtag, PrivilegedPlugin}

object VexiiRiscvBmb {
  case class Parameter(
      plugins: Seq[Hostable],
      iBusBmbParam: BmbParameter,
      dBusBmbParam: BmbParameter
  )
}

/** VexiiRiscv CPU core with two BMB master ports.
  *
  * Designed to be instantiated inside a ClockingArea. The system clock domain
  * is captured automatically from the enclosing ClockingArea; only the debug
  * clock domain must be supplied explicitly.
  *
  * Parent responsibilities:
  *   iBus / dBus          — connect to the BMB memory interconnect
  *   mtimerInterrupt      — drive from the machine timer peripheral
  *   globalInterrupt      — drive from the PLIC
  *   jtag                 — connect to top-level IO (slave direction)
  *   ndmreset             — register in debug clock domain and pass to the
  *                          system reset controller
  */
class VexiiRiscvBmb(
    parameter: VexiiRiscvBmb.Parameter,
    debugCd: ClockDomain
) extends Area {

  // Capture the system clock domain from the enclosing ClockingArea
  private val systemCd = ClockDomain.current

  val iBus = Bmb(parameter.iBusBmbParam)
  val dBus = Bmb(parameter.dBusBmbParam)
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

    // Bridge CachelessBus → BMB (read-only instruction fetch)
    val iBusNative = fetchPlugin.logic.bus
    iBus.cmd.valid := iBusNative.cmd.valid
    iBus.cmd.opcode := Bmb.Cmd.Opcode.READ
    iBus.cmd.address := iBusNative.cmd.address.resized
    iBus.cmd.length := 3
    iBus.cmd.last := True
    iBus.cmd.source := 0
    iBus.cmd.context := iBusNative.cmd.id.asBits.resized
    iBusNative.cmd.ready := iBus.cmd.ready

    iBusNative.rsp.valid := iBus.rsp.valid
    iBusNative.rsp.word := iBus.rsp.data
    iBusNative.rsp.error := iBus.rsp.isError
    iBusNative.rsp.id := iBus.rsp.context.asUInt.resized
    iBus.rsp.ready := True

    // Bridge LsuCachelessBus → BMB (read/write data bus)
    val dBusNative = lsuPlugin.logic.bus
    dBus.cmd.valid := dBusNative.cmd.valid
    dBus.cmd.opcode := dBusNative.cmd.write.asBits
    dBus.cmd.address := dBusNative.cmd.address.resized
    dBus.cmd.length := ((U(1) << dBusNative.cmd.size) - 1).resized
    dBus.cmd.last := True
    dBus.cmd.data := dBusNative.cmd.data
    dBus.cmd.mask := dBusNative.cmd.mask
    dBus.cmd.source := 0
    dBus.cmd.context := dBusNative.cmd.id.asBits.resized
    dBusNative.cmd.ready := dBus.cmd.ready

    dBusNative.rsp.valid := dBus.rsp.valid
    dBusNative.rsp.data := dBus.rsp.data
    dBusNative.rsp.error := dBus.rsp.isError
    dBusNative.rsp.id := dBus.rsp.context.asUInt.resized
    dBus.rsp.ready := True
  }
}
