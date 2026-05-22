// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.gpio

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
import spinal.lib.io.{TriStateArray, TriState}
import nafarr.Feature
import nafarr.peripherals.PeripheralsComponent

object Gpio {
  case class Parameter(width: Int) {
    require(width > 0, "At least one pin is required.")
  }

  case class Io(p: Parameter) extends Bundle {
    val pins = master(TriStateArray(p.width bits))
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: GpioCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val gpio = Io(p.io)
      val interrupt = out(Bool)
    }

    val ctrl = GpioCtrl(p)
    ctrl.io.gpio <> io.gpio
    io.interrupt <> ctrl.io.interrupt

    val mapper = GpioCtrl.Mapper(factory(io.bus), ctrl.io, p)

    override def getInterrupt = Some(io.interrupt)
    override def sysconFeatures = Some(List(Feature.Gpio))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Gpio(
    parameter: GpioCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Gpio.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkGpio(
    parameter: GpioCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends Gpio.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneGpio(
    parameter: GpioCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Gpio.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
