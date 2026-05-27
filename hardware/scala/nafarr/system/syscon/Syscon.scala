// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.syscon

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  SlaveFactory => TileLinkSlaveFactory
}
import spinal.lib.bus.wishbone._

import nafarr.{IpIdentification, Vendor, Platform, PlatformClass, Product, Feature}
import nafarr.peripherals.PeripheralsComponent

object Syscon {

  case class Parameter(
      vendor: Vendor.E,
      platform: Platform.E,
      platformClass: PlatformClass.E,
      product: Product.E,
      refClockHz: Long,
      siliconMajor: Int = 0,
      siliconMinor: Int = 1,
      features: List[Feature.E] = List(),
      buildDate: Long = Parameter.buildTimestamp
  )
  object Parameter {
    def buildTimestamp: Long =
      sys.env.get("BUILD_DATE").map(_.toLong).getOrElse(System.currentTimeMillis() / 1000)
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val identity = base + 0x00
    val siliconRev = base + 0x04
    val buildDate = base + 0x08
    val refClock = base + 0x0c
    val featureRegCount = (Feature.elements.size + 31) / 32
    val featureInfo = base + 0x10
    def features(idx: Int) = base + 0x14 + (idx * 4)
  }

  /** All registers are read-only constants derived from compile-time Parameters.
    * No hardware logic is required; the mapper drives the bus directly.
    *
    * identity register layout:
    *   [31:24] = platformClass  (PlatformClass enum ordinal)
    *   [23:16] = product        (Product enum ordinal)
    *   [15:8]  = platform       (Platform enum ordinal)
    *   [7:0]   = vendor         (Vendor enum ordinal)
    *
    * features registers: one 32-bit register per group of 32 Feature ordinals.
    * features(0) covers ordinals 0-31, features(1) covers 32-63, etc.
    * Bit N in features(i) is set when ordinal (i*32 + N) is present in p.features.
    * Register count is derived from the Feature enum size.
    * Populated automatically by the SoC builder via sysconFeatures
    * on each IP's Core class.
    */
  case class Mapper(busCtrl: BusSlaveFactory, p: Parameter) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Syscon, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    // identity: [7:0]=vendor [15:8]=platform [23:16]=product [31:24]=platformClass
    busCtrl.read(
      B(p.platformClass, 8 bits) ## B(p.product, 8 bits) ## B(p.platform, 8 bits) ## B(
        p.vendor,
        8 bits
      ),
      regs.identity
    )

    busCtrl.read(B(p.siliconMajor, 16 bits) ## B(p.siliconMinor, 16 bits), regs.siliconRev)

    busCtrl.read(B(p.buildDate, 32 bits), regs.buildDate)

    busCtrl.read(B(p.refClockHz, 32 bits), regs.refClock)

    busCtrl.read(B(0, 24 bits) ## B(regs.featureRegCount, 8 bits), regs.featureInfo)

    // features: bit N in register i set for Feature with ordinal (i*32 + N)
    var featureMask: BigInt = 0
    p.features.foreach { f => featureMask |= BigInt(1) << f.position }
    for (i <- 0 until regs.featureRegCount) {
      val regBits = (featureMask >> (i * 32)) & ((BigInt(1) << 32) - 1)
      busCtrl.read(B(regBits, 32 bits), regs.features(i))
    }

  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
    }
    val busCtrl = factory(io.bus)
    val mapper = Mapper(busCtrl, p)

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Syscon(
    p: Syscon.Parameter,
    busConfig: Apb3Config = Apb3Config(8, 32)
) extends Syscon.Core[Apb3](
      p,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkSyscon(
    p: Syscon.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(8, 32, 4, 1)
) extends Syscon.Core[TileLinkBus](
      p,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneSyscon(
    p: Syscon.Parameter,
    busConfig: WishboneConfig = WishboneConfig(8, 32)
) extends Syscon.Core[Wishbone](
      p,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    )
