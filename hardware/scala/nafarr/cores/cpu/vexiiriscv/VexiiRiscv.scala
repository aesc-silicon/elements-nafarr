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
  def realtime(
      resetAddress: BigInt,
      iCacheSize: BigInt = 0,
      withMul: Boolean = false,
      withCompressed: Boolean = false,
      withBarrelShifter: Boolean = false
  ): VexiiRiscvCoreParameter = {
    val param = new ParamSimple()

    param.xlen = 32
    param.resetVector = resetAddress.toLong

    // Optional ISA extensions (base is RV32I)
    if (withMul) param.addISA("m")
    if (withCompressed) param.addISA("c")

    // Instruction cache: 1-way with 64 B lines, disabled when iCacheSize = 0
    val lineSize = 64
    param.fetchL1Enable = iCacheSize > 0
    if (iCacheSize > 0) {
      require(iCacheSize % lineSize == 0, s"iCacheSize must be a multiple of $lineSize")
      param.fetchL1Sets = (iCacheSize / lineSize).toInt
      param.fetchL1Ways = 1
    }

    // No data cache: deterministic data access latency
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

    // Barrel shifter: single-cycle shifts, more area
    // Iterative shifter: multi-cycle shifts, less area
    param.withIterativeShift = !withBarrelShifter

    // Relaxed branch/shift: better timing closure, still fully deterministic
    param.relaxedBranch = true
    param.relaxedShift = true

    param.fixIsaParams()
    val plugins = param.plugins()
    ParamSimple.setPma(plugins)

    // TileLink params: sizeBytes must match across iBus/dBus for the shared
    // decoder in the platform. Cache line size (64 B) when enabled, else 4 B.
    val sizeBytes = if (iCacheSize > 0) 64 else 4
    val iBusTlParam = TileLinkParameter.simple(32, 32, sizeBytes, 1)
    val dBusTlParam = TileLinkParameter.simple(32, 32, sizeBytes, 1)

    VexiiRiscvCoreParameter(plugins, iBusTlParam, dBusTlParam)
  }
}
