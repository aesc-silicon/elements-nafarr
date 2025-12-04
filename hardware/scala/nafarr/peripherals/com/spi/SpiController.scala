// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._
import nafarr.peripherals.PeripheralsComponent

object SpiController {
  object CmdMode extends SpinalEnum(binarySequential) {
    val DATA, CS = newElement()
  }

  case class CmdData(p: SpiControllerCtrl.Parameter) extends Bundle {
    val data = Bits(p.dataWidth bits)
    val read = Bool
  }

  case class CmdCs(p: SpiControllerCtrl.Parameter) extends Bundle {
    val enable = Bool
    val index = UInt(log2Up(p.io.csWidth) bits)
  }

  case class Cmd(p: SpiControllerCtrl.Parameter) extends Bundle {
    val mode = CmdMode()
    val args = Bits(Math.max(widthOf(CmdData(p)), widthOf(CmdCs(p))) bits)

    def isData = mode === CmdMode.DATA
    def isCs = mode === CmdMode.CS
    def argsData = {
      val ret = CmdData(p)
      ret.assignFromBits(args)
      ret
    }
    def argsCs = {
      val ret = CmdCs(p)
      ret.assignFromBits(args)
      ret
    }
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: SpiControllerCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val spi = master(Spi.Io(p.io))
      val interrupt = out(Bool)
    }

    val spiControllerCtrl = SpiControllerCtrl(p)
    spiControllerCtrl.io.spi <> io.spi
    io.interrupt := spiControllerCtrl.io.interrupt

    val busFactory = factory(io.bus)
    SpiControllerCtrl.Mapper(busFactory, spiControllerCtrl.io, p)
    SpiControllerCtrl.StreamMapper(busFactory, spiControllerCtrl.io, p)

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
\t\t\tcompatible = "elements,spi";
\t\t\treg = <0x$baseAddress 0x$regSize>;
\t\t\tstatus = "okay";"""
      if (irqNumber.isDefined) {
        dt += s"""
\t\t\tinterrupt-parent = <&plic>;
\t\t\tinterrupts = <$irqNumber 1>;"""
      }
      dt += s"""
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

case class Apb3SpiController(
    parameter: SpiControllerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends SpiController.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    ) { val dummy = 0 }

case class WishboneSpiController(
    parameter: SpiControllerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends SpiController.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMSpiController(
    parameter: SpiControllerCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends SpiController.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
