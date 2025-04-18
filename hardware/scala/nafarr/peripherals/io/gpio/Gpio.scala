package nafarr.peripherals.io.gpio

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._
import spinal.lib.io.{TriStateArray, TriState}
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

    def deviceTreeZephyr(
        name: String,
        address: BigInt,
        size: BigInt,
        irqNumber: Option[Int] = null
    ) = {
      val baseAddress = "%x".format(address.toInt)
      val regSize = "%04x".format(size.toInt)
      var dt = s"""
\t\t$name: $name@$baseAddress {
\t\t\tcompatible = "elements,gpio";
\t\t\treg = <0x$baseAddress 0x$regSize>;
\t\t\tstatus = "okay";"""
      if (irqNumber.isDefined) {
        dt += s"""
\t\t\tinterrupt-parent = <&plic>;
\t\t\tinterrupts = <$irqNumber 1>;"""
      }
      dt += s"""
\t\t\tgpio-controller;
\t\t\t#gpio-cells = <2>;
\t\t};"""
      dt
    }
    def headerBareMetal(
        name: String,
        address: BigInt,
        size: BigInt,
        irqNumber: Option[Int] = null
    ) = {
      val baseAddress = "%08x".format(address.toInt)
      val regSize = "%04x".format(size.toInt)
      var dt = s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
      if (irqNumber.isDefined)
        dt += s"""#define ${name.toUpperCase}_IRQ\t\t${irqNumber.get}\n"""
      dt
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
    ) { val dummy = 0 }

case class WishboneGpio(
    parameter: GpioCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(12, 32)
) extends Gpio.Core[Wishbone](
      parameter,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMGpio(
    parameter: GpioCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends Gpio.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
