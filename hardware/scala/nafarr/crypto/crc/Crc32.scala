// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.crc

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

object Crc32 {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: Crc32Ctrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
    }
    val busCtrl = factory(io.bus)
    val ctrl = Crc32Ctrl(p)
    val mapper = Crc32Ctrl.Mapper(busCtrl, ctrl, p)

    override def sysconFeatures = Some(List(Feature.Crc))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Crc32(
    p: Crc32Ctrl.Parameter = Crc32Ctrl.Parameter(),
    busConfig: Apb3Config = Apb3Config(8, 32)
) extends Crc32.Core[Apb3](
      p,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkCrc32(
    p: Crc32Ctrl.Parameter = Crc32Ctrl.Parameter(),
    busConfig: TileLinkParameter = TileLinkParameter.simple(8, 32, 4, 1)
) extends Crc32.Core[TileLinkBus](
      p,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneCrc32(
    p: Crc32Ctrl.Parameter = Crc32Ctrl.Parameter(),
    busConfig: WishboneConfig = WishboneConfig(8, 32)
) extends Crc32.Core[Wishbone](
      p,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    )
