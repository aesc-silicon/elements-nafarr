// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.uart

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

object Uart {
  case class Io(p: UartCtrl.Parameter) extends Bundle with IMasterSlave {
    val txd = Bool
    val rxd = Bool
    val cts = Bool
    val rts = Bool

    override def asMaster(): Unit = {
      out(txd)
      in(rxd)
      in(cts)
      out(rts)
    }
    override def asSlave(): Unit = {
      in(txd)
      out(rxd)
      out(cts)
      in(rts)
    }
  }

  object ParityType extends SpinalEnum(binarySequential) {
    val NONE, EVEN, ODD = newElement()
  }

  object StopType extends SpinalEnum(binarySequential) {
    val ONE, TWO = newElement()
    def toBitCount(that: C): UInt = (that === ONE) ? U"0" | U"1"
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: UartCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val uart = master(Io(p))
      val interrupt = out(Bool)
      val error = out(Bool)
    }

    val ctrl = UartCtrl(p)
    ctrl.io.uart <> io.uart
    io.interrupt := ctrl.io.interrupt
    io.error := ctrl.io.error

    val mapper = UartCtrl.Mapper(factory(io.bus), ctrl.io, p)

    val clockSpeed = ClockDomain.current.frequency.getValue.toInt
    override def getInterrupt = Some(io.interrupt)
    override def getError = Some(io.error)
    override def sysconFeatures = Some(List(Feature.Uart))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}
#define ${name.toUpperCase}_FREQ\t\t${clockSpeed}
#define ${name.toUpperCase}_BAUD\t\t${this.p.init.baudrate}
"""
    }
  }
}

case class Apb3Uart(
    parameter: UartCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Uart.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkUart(
    parameter: UartCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends Uart.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneUart(
    parameter: UartCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Uart.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
