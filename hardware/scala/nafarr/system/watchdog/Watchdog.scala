// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.watchdog

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

object Watchdog {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: WatchdogCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out(Bool())
      val error = out(Bool())
    }
    val busCtrl = factory(io.bus)
    val ctrl = WatchdogCtrl(p)
    val mapper = WatchdogCtrl.Mapper(busCtrl, ctrl, p)
    io.interrupt := ctrl.io.interrupt
    io.error := ctrl.io.error

    override def getInterrupt = Some(io.interrupt)
    override def getError = Some(io.error)
    override def sysconFeatures = Some(List(Feature.Watchdog))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Watchdog(
    p: WatchdogCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Watchdog.Core[Apb3](
      p,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkWatchdog(
    p: WatchdogCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 4, 1)
) extends Watchdog.Core[TileLinkBus](
      p,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneWatchdog(
    p: WatchdogCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(12, 32)
) extends Watchdog.Core[Wishbone](
      p,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    )
