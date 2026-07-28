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
import nafarr.Feature
import nafarr.peripherals.PeripheralsComponent

object HyperBus {

  object Phy {
    object CmdMode extends SpinalEnum(binarySequential) {
      val START, CA, WRITE = newElement()
    }

    case class CmdStart(p: HyperBusCtrl.Parameter) extends Bundle {
      val index = UInt(Math.max(log2Up(p.hyperbus.supportedDevices), 1) bits)
      val latency = UInt(log2Up(8) bits)
      val burstLen = UInt(log2Up(p.frontend.storageDepth + 1) bits) // read word count - 1
      val read = Bool
      val memory = Bool
    }

    case class CmdCa(p: HyperBusCtrl.Parameter) extends Bundle {
      val ca = Bits(48 bits)
    }

    case class CmdWrite(p: HyperBusCtrl.Parameter) extends Bundle {
      val data = Bits(p.frontend.dataWidth bits)
      val mask = Bits(p.frontend.dataWidth / 8 bits)
      val last = Bool
    }

    case class Cmd(p: HyperBusCtrl.Parameter) extends Bundle {
      val mode = CmdMode()
      val args = Bits(
        Math.max(widthOf(CmdStart(p)), Math.max(widthOf(CmdCa(p)), widthOf(CmdWrite(p)))) bits
      )

      def isStart = mode === CmdMode.START
      def isCa = mode === CmdMode.CA
      def isWrite = mode === CmdMode.WRITE
      def argsStart = {
        val ret = CmdStart(p)
        ret.assignFromBits(args)
        ret
      }
      def argsCa = {
        val ret = CmdCa(p)
        ret.assignFromBits(args)
        ret
      }
      def argsWrite = {
        val ret = CmdWrite(p)
        ret.assignFromBits(args)
        ret
      }
    }

    case class Rdata(p: HyperBusCtrl.Parameter) extends Bundle {
      val data = Bits(p.frontend.dataWidth bits)
      val last = Bool
      val error = Bool
      val aborted = Bool // starvation abort (controller not draining) -> retry
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
      val rdata = Stream(HyperBus.Phy.Rdata(p))
      val config = HyperBus.Phy.Config(p)

      override def asMaster(): Unit = {
        master(cmd)
        slave(rdata)
        master(config)
      }
      override def asSlave(): Unit = {
        slave(cmd)
        master(rdata)
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
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val phy = master(Phy.Interface(p))
      val frontend = master(Stream(FrontendInterface(p)))
      val controller = slave(Stream(ControllerInterface(p)))
      val error = out Bool ()
    }

    val ctrl = HyperBusCtrl(p)
    ctrl.io.phy <> io.phy
    ctrl.io.frontend <> io.frontend
    ctrl.io.controller <> io.controller

    val mapper = HyperBusCtrl.Mapper(factory(io.bus), ctrl.io, p)
    io.error := mapper.error

    override def getError = Some(io.error)
    override def sysconFeatures = Some(List(Feature.Hyperbus))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
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
