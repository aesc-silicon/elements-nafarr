// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.cores.cpu.vexiiriscv

import spinal.core._
import spinal.lib.bus.tilelink.{BusParameter => TileLinkParameter}
import spinal.lib.misc.plugin.Hostable

import vexiiriscv.ParamSimple

case class VexiiRiscvCoreParameter(
    plugins: Seq[Hostable],
    iBusTlParam: TileLinkParameter,
    dBusTlParam: TileLinkParameter
)

object VexiiRiscvCoreParameter {
  def realtime(resetAddress: BigInt): VexiiRiscvCoreParameter = {
    val param = new ParamSimple()

    param.xlen = 32
    param.resetVector = resetAddress.toLong

    // RV32I: base integer only, no compressed instructions

    // No caches: deterministic memory access latency
    param.fetchL1Enable = false
    param.lsuL1Enable = false

    // No branch prediction: fully deterministic fetch
    param.withBtb = false
    param.withGShare = false
    param.withRas = false

    // Full forwarding bypass: reduces stalls without sacrificing determinism
    param.allowBypassFrom = 0

    // JTAG debug (clock domain set later by the platform via setDebugCd)
    param.privParam.withDebug = true
    param.embeddedJtagTap = true

    // Async register file: shallower pipeline, smaller area
    param.regFileSync = false

    // Iterative shifter: saves area at cost of multi-cycle shifts
    param.withIterativeShift = true

    // Relaxed branch/shift: better timing closure, still fully deterministic
    param.relaxedBranch = true
    param.relaxedShift = true

    param.fixIsaParams()
    val plugins = param.plugins()
    ParamSimple.setPma(plugins)

    // TileLink params: TL-UL (sizeBytes=4), sourceWidth=1 for pendingMax=2
    val iBusTlParam = TileLinkParameter.simple(32, 32, 4, 1)
    val dBusTlParam = TileLinkParameter.simple(32, 32, 4, 1)

    VexiiRiscvCoreParameter(plugins, iBusTlParam, dBusTlParam)
  }
}
