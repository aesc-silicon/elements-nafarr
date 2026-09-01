// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.cores.cpu.vexiiriscv

import spinal.core._
import spinal.lib.bus.tilelink.{BusParameter => TileLinkParameter, M2sTransfers, SizeRange}
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.system.tag.{PmaRegion, PmaRegionImpl}
import spinal.lib.misc.plugin.Hostable

import vexiiriscv.ParamSimple
import vexiiriscv.memory.PmpParam
import vexiiriscv.execute.lsu.{LsuL1Plugin, LsuL1TlPlugin}
import vexiiriscv.prediction.GSharePlugin

case class VexiiRiscvCoreParameter(
    plugins: Seq[Hostable],
    iBusTlParam: TileLinkParameter,
    dBusTlParam: TileLinkParameter,
    dIoBusTlParam: TileLinkParameter = null
)

object VexiiRiscvCoreParameter {
  def realtime(
      resetAddress: BigInt,
      iCacheSize: BigInt = 0,
      withMul: Boolean = false,
      withCompressed: Boolean = false,
      withBarrelShifter: Boolean = false,
      mainRegions: Seq[SizeMapping] = Seq(SizeMapping(0x80000000L, 0x30000000L)),
      ioRegions: Seq[SizeMapping] = Seq(SizeMapping(0xf0000000L, 0x10000000L))
  ): VexiiRiscvCoreParameter = {
    val param = new ParamSimple()

    param.xlen = 32
    param.resetVector = resetAddress.toLong

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

    val pmaRegions: Seq[PmaRegion] =
      mainRegions.map(m =>
        new PmaRegionImpl(
          mapping = m,
          isMain = true,
          isExecutable = true,
          transfers = M2sTransfers(get = SizeRange.all, putFull = SizeRange.all)
        )
      ) ++ ioRegions.map(m =>
        new PmaRegionImpl(
          mapping = m,
          isMain = false,
          isExecutable = false,
          transfers = M2sTransfers(
            get = SizeRange.all,
            putFull = SizeRange.all,
            putPartial = SizeRange.all
          )
        )
      )
    ParamSimple.setPma(plugins, pmaRegions)

    // TileLink params: sizeBytes must match across iBus/dBus for the shared
    // decoder in the platform. Cache line size (64 B) when enabled, else 4 B.
    val sizeBytes = if (iCacheSize > 0) 64 else 4
    val iBusTlParam = TileLinkParameter.simple(32, 32, sizeBytes, 1)
    val dBusTlParam = TileLinkParameter.simple(32, 32, sizeBytes, 1)

    VexiiRiscvCoreParameter(plugins, iBusTlParam, dBusTlParam)
  }

  def performance(
      resetAddress: BigInt,
      iCacheSize: BigInt = 4096,
      dCacheSize: BigInt = 4096,
      btbSets: Int = 16,
      pmpRegions: Int = 8,
      withCompressed: Boolean = true,
      mainRegions: Seq[SizeMapping] = Seq(SizeMapping(0x80000000L, 0x30000000L)),
      ioRegions: Seq[SizeMapping] = Seq(SizeMapping(0xf0000000L, 0x10000000L))
  ): VexiiRiscvCoreParameter = {
    val param = new ParamSimple()
    val lineSize = 64

    param.xlen = 32
    param.resetVector = resetAddress.toLong

    param.addISA("m")
    if (withCompressed) param.addISA("c")
    param.addISA("zicntr", "zihpm")
    param.additionalPerformanceCounters = 4

    require(iCacheSize % lineSize == 0, s"iCacheSize must be a multiple of $lineSize")
    param.fetchL1Enable = true
    param.fetchL1Sets = (iCacheSize / lineSize).toInt
    param.fetchL1Ways = 1

    require(dCacheSize % lineSize == 0, s"dCacheSize must be a multiple of $lineSize")
    param.lsuL1Enable = true
    param.lsuL1Sets = (dCacheSize / lineSize).toInt
    param.lsuL1Ways = 1
    // Sole bus master: write-back D$ needs no coherency machinery.
    param.lsuL1Coherency = false
    // A store buffer is mandatory once lsuL1 is enabled.
    param.lsuStoreBufferSlots = 2
    param.lsuStoreBufferOps = 32

    param.withBtb = true
    param.withGShare = true
    param.withRas = true
    param.btbSets = btbSets
    param.gshareBytes = 256
    param.bootMemClear = true

    // U-mode + PMP for isolation (no supervisor/MMU at this class).
    param.privParam.withUser = true
    param.pmpParam = new PmpParam(
      pmpSize = pmpRegions,
      granularity = 4096,
      withTor = true,
      withNapot = true
    )

    param.allowBypassFrom = 0
    param.regFileSync = false
    param.withIterativeShift = false

    param.privParam.withDebug = true
    param.embeddedJtagTap = true

    param.fixIsaParams()
    val basePlugins = param.plugins()
    basePlugins.collectFirst { case p: LsuL1Plugin => p }.foreach(_.ackIdWidth = 0)
    basePlugins.collectFirst { case p: GSharePlugin => p }.foreach(_.counterWidth = 4)
    val plugins = basePlugins :+ new LsuL1TlPlugin()
    val pmaRegions: Seq[PmaRegion] =
      mainRegions.map(m =>
        new PmaRegionImpl(
          mapping = m,
          isMain = true,
          isExecutable = true,
          transfers = M2sTransfers(get = SizeRange.all, putFull = SizeRange.all)
        )
      ) ++ ioRegions.map(m =>
        new PmaRegionImpl(
          mapping = m,
          isMain = false,
          isExecutable = false,
          transfers = M2sTransfers(
            get = SizeRange.all,
            putFull = SizeRange.all,
            putPartial = SizeRange.all
          )
        )
      )
    ParamSimple.setPma(plugins, pmaRegions)

    val iBusTlParam = TileLinkParameter.simple(32, 32, lineSize, 1)
    val dBusTlParam = TileLinkParameter.simple(32, 32, lineSize, 1)
    val dIoBusTlParam = TileLinkParameter.simple(32, 32, lineSize, 1)

    VexiiRiscvCoreParameter(plugins, iBusTlParam, dBusTlParam, dIoBusTlParam)
  }
}
