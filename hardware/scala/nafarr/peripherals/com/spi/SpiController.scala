// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.spi

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

object SpiController {
  object CmdMode extends SpinalEnum(binarySequential) {
    val DATA, CS, DUMMYCYCLES = newElement()
  }

  case class CmdData(p: SpiControllerCtrl.Parameter) extends Bundle {
    val data = Bits(p.dataWidth bits)
    val mode = Bits(p.modeWidth bits)
    val read = Bool
  }

  case class CmdCs(p: SpiControllerCtrl.Parameter) extends Bundle {
    val enable = Bool
    val index = UInt(log2Up(p.io.csWidth) bits)
  }

  case class CmdDummyCycles(p: SpiControllerCtrl.Parameter) extends Bundle {
    val cycles = UInt(log2Up(p.maxDummyCycles * 2) bits)
  }

  case class Cmd(p: SpiControllerCtrl.Parameter) extends Bundle {
    val mode = CmdMode()
    val args = Bits(Math.max(widthOf(CmdData(p)), widthOf(CmdCs(p))) bits)

    def isData = mode === CmdMode.DATA
    def isCs = mode === CmdMode.CS
    def isDummyCycles = mode === CmdMode.DUMMYCYCLES
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
    def argsDummyCycles = {
      val ret = CmdDummyCycles(p)
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

    override def getInterrupt = Some(io.interrupt)
    override def sysconFeatures = Some(List(Feature.Spi))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
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
    )

case class TileLinkSpiController(
    parameter: SpiControllerCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends SpiController.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneSpiController(
    parameter: SpiControllerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends SpiController.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
