// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.i2c

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
import nafarr.Feature
import nafarr.peripherals.PeripheralsComponent

object I2cController {
  case class Cmd() extends Bundle {
    val data = Bits(8 bits)
    val read = Bool
    val start = Bool
    val stop = Bool
    val ack = Bool
  }

  case class Rsp() extends Bundle {
    val data = Bits(8 bits)
    val error = Bool
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: I2cControllerCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val i2c = master(I2c.Io(p.io))
      val interrupt = out(Bool)
    }

    val i2cControllerCtrl = I2cControllerCtrl(p)
    i2cControllerCtrl.io.i2c <> io.i2c
    io.interrupt := i2cControllerCtrl.io.interrupt

    val mapper = I2cControllerCtrl.Mapper(factory(io.bus), i2cControllerCtrl.io, p)

    val clockSpeed = ClockDomain.current.frequency.getValue.toInt
    override def getInterrupt = Some(io.interrupt)
    override def sysconFeatures = Some(List(Feature.I2c))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}
#define ${name.toUpperCase}_FREQ\t\t${clockSpeed}
"""
    }
  }
}

case class Apb3I2cController(
    parameter: I2cControllerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends I2cController.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkI2cController(
    parameter: I2cControllerCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends I2cController.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneI2cController(
    parameter: I2cControllerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends I2cController.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
