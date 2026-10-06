// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.ml.tsetlin

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  SlaveFactory => TileLinkSlaveFactory
}
import spinal.lib.bus.wishbone._
import nafarr.peripherals.PeripheralsComponent

object Tsetlin {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: TsetlinCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out(Bool())
    }

    val ctrl = TsetlinCtrl(p)
    val mapper = TsetlinCtrl.Mapper(factory(io.bus), ctrl.io, p)
    io.interrupt := mapper.interrupt

    override def getInterrupt = Some(io.interrupt)

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Tsetlin(
    parameter: TsetlinCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Tsetlin.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkTsetlin(
    parameter: TsetlinCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends Tsetlin.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneTsetlin(
    parameter: TsetlinCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Tsetlin.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
