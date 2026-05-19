// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.prng

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
import nafarr.peripherals.PeripheralsComponent
import nafarr.Feature

object Prng {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: PrngCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val error = out Bool ()
    }
    val busCtrl = factory(io.bus)
    val ctrl = PrngCtrl(p)
    val mapper = PrngCtrl.Mapper(busCtrl, ctrl, p)
    io.error := ctrl.io.error

    override def getError = Some(io.error)
    override def sysconFeatures = Some(List(Feature.Prng))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Prng(
    p: PrngCtrl.Parameter = PrngCtrl.Parameter(),
    busConfig: Apb3Config = Apb3Config(8, 32)
) extends Prng.Core[Apb3](
      p,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkPrng(
    p: PrngCtrl.Parameter = PrngCtrl.Parameter(),
    busConfig: TileLinkParameter = TileLinkParameter.simple(8, 32, 4, 1)
) extends Prng.Core[TileLinkBus](
      p,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishbonePrng(
    p: PrngCtrl.Parameter = PrngCtrl.Parameter(),
    busConfig: WishboneConfig = WishboneConfig(8, 32)
) extends Prng.Core[Wishbone](
      p,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    )
