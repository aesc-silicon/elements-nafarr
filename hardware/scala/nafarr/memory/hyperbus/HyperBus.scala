// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._
import spinal.lib.io.{TriStateArray, TriState}

object HyperBus {

  object Phy {
    object CmdMode extends SpinalEnum(binarySequential) {
      val CS, ADDR, DATA = newElement()
    }

    case class CmdCs(p: HyperBusCtrl.Parameter) extends Bundle {
      val index = UInt(Math.max(log2Up(p.hyperbus.supportedDevices), 1) bits)
      val latencyCycles = UInt(log2Up(6) bits)
      val read = Bool
    }

    case class CmdAddr(p: HyperBusCtrl.Parameter) extends Bundle {
      val addr = Bits(p.hyperbus.dataWidth bits)
    }

    case class CmdData(p: HyperBusCtrl.Parameter) extends Bundle {
      val data = Bits(p.hyperbus.dataWidth bits)
      val mask = Bool()
      val last = Bool()
    }

    case class Cmd(p: HyperBusCtrl.Parameter) extends Bundle {
      val mode = CmdMode()
      val args = Bits(Math.max(widthOf(CmdData(p)), widthOf(CmdCs(p))) bits)

      def isCs = mode === CmdMode.CS
      def isAddr = mode === CmdMode.ADDR
      def isData = mode === CmdMode.DATA
      def argsCs = {
        val ret = CmdCs(p)
        ret.assignFromBits(args)
        ret
      }
      def argsAddr = {
        val ret = CmdAddr(p)
        ret.assignFromBits(args)
        ret
      }
      def argsData = {
        val ret = CmdData(p)
        ret.assignFromBits(args)
        ret
      }
    }

    case class Rsp(p: HyperBusCtrl.Parameter) extends Bundle {
      val data = Bits(p.hyperbus.dataWidth bits)
      val last = Bool
      val error = Bool
    }

    case class Config(p: HyperBusCtrl.Parameter) extends Bundle with IMasterSlave {
      val reset = new Bundle {
        val pulse = UInt(log2Up(p.phy.resetPulseWidth) bits)
        val halt = UInt(log2Up(p.phy.resetHaltWidth) bits)
        val trigger = Bool
      }

      override def asMaster(): Unit = {
        out(reset)
      }
      override def asSlave(): Unit = {
        in(reset)
      }
    }

    case class Interface(p: HyperBusCtrl.Parameter) extends Bundle with IMasterSlave {
      val cmd = Stream(HyperBus.Phy.Cmd(p))
      val rsp = Stream(HyperBus.Phy.Rsp(p))
      val config = HyperBus.Phy.Config(p)

      override def asMaster(): Unit = {
        master(cmd)
        slave(rsp)
        master(config)
      }
      override def asSlave(): Unit = {
        slave(cmd)
        master(rsp)
        slave(config)
      }
    }
  }

  case class ControllerInterface(p: HyperBusCtrl.Parameter) extends Bundle {
    val id = UInt(p.frontend.idLength bits)
    val read = Bool
    val memory = Bool
    val unaligned = Bool
    val addr = UInt(p.frontend.addrWidth bits)
    val data = Bits(p.frontend.dataWidth bits)
    val strobe = Bits(p.frontend.dataWidth / 8 bits)
    val last = Bool
  }

  case class FrontendInterface(p: HyperBusCtrl.Parameter) extends Bundle {
    val id = UInt(p.frontend.idLength bits)
    val read = Bool
    val data = Bits(p.frontend.dataWidth bits)
    val last = Bool
    val error = Bool
  }

  case class Io(p: HyperBusCtrl.Parameter) extends Bundle with IMasterSlave {
    val cs = Bits(p.hyperbus.supportedDevices bits)
    val ck = Bool
    val reset = Bool
    val dq = TriStateArray(p.phy.dataWidth bits)
    val rwds = TriState(Bool)

    override def asMaster(): Unit = {
      out(cs)
      out(ck)
      out(reset)
      master(dq)
      master(rwds)
    }
    override def asSlave(): Unit = {
      in(cs)
      in(ck)
      in(reset)
      slave(dq)
      slave(rwds)
    }
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: HyperBusCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val phy = master(Phy.Interface(p))
      val frontend = master(Stream(FrontendInterface(p)))
      val controller = slave(Stream(ControllerInterface(p)))
    }

    val ctrl = HyperBusCtrl(p)
    ctrl.io.phy <> io.phy
    ctrl.io.frontend <> io.frontend
    ctrl.io.controller <> io.controller

    val mapper = HyperBusCtrl.Mapper(factory(io.bus), ctrl.io, p)

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

case class Apb3HyperBus(
    parameter: HyperBusCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends HyperBus.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    ) { val dummy = 0 }

case class WishboneHyperBus(
    parameter: HyperBusCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends HyperBus.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMHyperBus(
    parameter: HyperBusCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends HyperBus.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
