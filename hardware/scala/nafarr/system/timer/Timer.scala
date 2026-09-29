// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.timer

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
import nafarr.Feature
import nafarr.peripherals.PeripheralsComponent

object Timer {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: TimerCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out Bool ()
    }
    val ctrl = TimerCtrl(p)
    val mapper = TimerCtrl.Mapper(factory(io.bus), ctrl, p)
    io.interrupt := mapper.interrupt

    override def getInterrupt = Some(io.interrupt)
    override def sysconFeatures = Some(List(Feature.Timer))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Timer(
    p: TimerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Timer.Core[Apb3](p, Apb3(busConfig), Apb3SlaveFactory(_))

case class TileLinkTimer(
    p: TimerCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 4, 1)
) extends Timer.Core[TileLinkBus](p, TileLinkBus(busConfig), new TileLinkSlaveFactory(_, false))

case class WishboneTimer(
    p: TimerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(12, 32)
) extends Timer.Core[Wishbone](p, Wishbone(busConfig), WishboneSlaveFactory(_))
