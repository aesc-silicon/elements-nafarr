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

/** Transport between the debugger and the RISC-V Debug Module embedded in the core. */
sealed trait DebugTransport
object DebugTransport {

  /** JTAG TAP on dedicated pins: the JTAG DTM of the RISC-V Debug Specification (Ch. 6). */
  case object Jtag extends DebugTransport

  /** SWD (SWCLK/SWDIO) through an ARM SW-DP. This is a custom DTM, so a design using it
    * conforms to the RISC-V Debug Specification "with custom DTM".
    */
  case object Swd extends DebugTransport
}

/** Floating-point support of the performance profile. */
sealed trait Fpu
object Fpu {
  case object None extends Fpu

  /** F: single precision. */
  case object Single extends Fpu

  /** F and D: single and double precision; widens the data buses to 64 bits. */
  case object Double extends Fpu
}

case class VexiiRiscvCoreParameter(
    plugins: Seq[Hostable],
    iBusTlParam: TileLinkParameter,
    dBusTlParam: TileLinkParameter,
    dIoBusTlParam: TileLinkParameter = null
)

object VexiiRiscvCoreParameter {
  private def setDebugTransport(param: ParamSimple, transport: DebugTransport): Unit =
    transport match {
      case DebugTransport.Jtag => param.embeddedJtagTap = true
      case DebugTransport.Swd => param.embeddedSwd = true
    }

  def realtime(
      resetAddress: BigInt,
      iCacheSize: BigInt = 0,
      debugTriggers: BigInt = 0,
      withMul: Boolean = false,
      withCompressed: Boolean = false,
      withBarrelShifter: Boolean = false,
      mainRegions: Seq[SizeMapping] = Seq(SizeMapping(0x80000000L, 0x30000000L)),
      ioRegions: Seq[SizeMapping] = Seq(SizeMapping(0xf0000000L, 0x10000000L)),
      debugTransport: DebugTransport = DebugTransport.Jtag
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

    // Debug module (clock domain set later by the platform via setDebugCd)
    param.privParam.withDebug = true
    setDebugTransport(param, debugTransport)
    if (debugTriggers > 0) {
      param.privParam.debugTriggers = debugTriggers.toInt
      param.privParam.debugTriggersLsu = true
    }

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
      debugTriggers: BigInt = 0,
      btbSets: Int = 16,
      pmpRegions: Int = 8,
      withCompressed: Boolean = true,
      withCacheOps: Boolean = false,
      withBitManip: Boolean = false,
      withAtomics: Boolean = false,
      withDualIssue: Boolean = false,
      fpu: Fpu = Fpu.None,
      memDataWidth: Int = 32,
      mainRegions: Seq[SizeMapping] = Seq(SizeMapping(0x80000000L, 0x30000000L)),
      ioRegions: Seq[SizeMapping] = Seq(SizeMapping(0xf0000000L, 0x10000000L)),
      debugTransport: DebugTransport = DebugTransport.Jtag
  ): VexiiRiscvCoreParameter = {
    val param = new ParamSimple()
    val lineSize = 64

    param.xlen = 32
    param.resetVector = resetAddress.toLong

    require(
      Seq(32, 64, 128).contains(memDataWidth),
      s"memDataWidth must be 32, 64 or 128, got $memDataWidth"
    )
    // Minimum width of the instruction and data buses; dual-issue and D may widen them further.
    param.fetchMemDataWidthMin = memDataWidth
    param.lsuMemDataWidthMin = memDataWidth

    param.addISA("m")
    if (withCompressed) param.addISA("c")
    param.addISA("zicntr", "zihpm")
    // Zicbom (cbo.clean/flush/inval on the L1): software-managed coherency for DMA buffers.
    if (withCacheOps) param.addISA("zicbom")
    // Zba/Zbb/Zbc/Zbs bit manipulation.
    if (withBitManip) param.addISA("zba", "zbb", "zbc", "zbs")
    // A (Zaamo + Zalrsc), executed in the L1: AMOs only work on cacheable (main) regions.
    if (withAtomics) param.addISA("a")
    fpu match {
      case Fpu.None =>
      case Fpu.Single => param.addISA("f")
      case Fpu.Double => param.addISA("f", "d")
    }
    param.additionalPerformanceCounters = 4

    require(iCacheSize % lineSize == 0, s"iCacheSize must be a multiple of $lineSize")
    param.fetchL1Enable = true
    param.fetchL1Sets = (iCacheSize / lineSize).toInt
    param.fetchL1Ways = 1

    require(dCacheSize % lineSize == 0, s"dCacheSize must be a multiple of $lineSize")
    param.lsuL1Enable = true
    param.lsuL1Sets = (dCacheSize / lineSize).toInt
    param.lsuL1Ways = 1
    // No hardware coherency: other bus masters (e.g. DMA) rely on Zicbom cache operations.
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

    // Two decoders and execution lanes; fetching two instructions per cycle needs a 64-bit
    // instruction bus.
    if (withDualIssue) {
      param.decoders = 2
      param.lanes = 2
      param.withAlignerBuffer = true
      param.withDispatcherBuffer = true
    }

    param.privParam.withDebug = true
    setDebugTransport(param, debugTransport)
    if (debugTriggers > 0) {
      param.privParam.debugTriggers = debugTriggers.toInt
      param.privParam.debugTriggersLsu = true
    }

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

    // Bus widths as VexiiRiscv derives them: fetch from the decoder count, cached data from D,
    // uncached I/O from max(XLEN, FLEN).
    val ioDataWidth = if (fpu == Fpu.Double) 64 else 32
    val iBusTlParam = TileLinkParameter.simple(32, param.fetchMemDataWidth, lineSize, 1)
    val dBusTlParam = TileLinkParameter.simple(32, param.lsuMemDataWidth, lineSize, 1)
    val dIoBusTlParam = TileLinkParameter.simple(32, ioDataWidth, lineSize, 1)

    VexiiRiscvCoreParameter(plugins, iBusTlParam, dBusTlParam, dIoBusTlParam)
  }
}
