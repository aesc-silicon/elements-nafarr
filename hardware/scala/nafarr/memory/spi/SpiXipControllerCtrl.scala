// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory

import nafarr.IpIdentification
import nafarr.peripherals.com.spi.{Spi, SpiController, SpiControllerCtrl}

object SpiXipControllerCtrl {
  def apply(p: SpiControllerCtrl.Parameter, dataWidth: Int) =
    SpiXipControllerCtrl(p, dataWidth)

  object State extends SpinalEnum {
    val IDLE, ENABLESPI, COMMAND, ADDRESS, DUMMYCYCLES, DATA, DISABLESPI = newElement()
  }

  case class Config(p: SpiControllerCtrl.Parameter) extends Bundle {
    val mode = Bits(p.modeWidth bits)
    val dummyCycles = UInt(log2Up(p.maxDummyCycles * 2) bits)
    val evcr = Bits(8 bits)
    val configure = Bool()
  }

  case class Io(p: SpiControllerCtrl.Parameter) extends Bundle {
    val config = in(Config(p))
    val busCmd = slave(Stream(SpiXipController.GenericInterface.Cmd()))
    val busRsp = master(Stream(SpiXipController.GenericInterface.Rsp()))
    val cmd = master(Stream(SpiController.Cmd(p)))
    val rsp = slave(Flow(Bits(p.dataWidth bits)))
  }

  case class SpiXipControllerCtrl(p: SpiControllerCtrl.Parameter, dataWidth: Int)
      extends Component {
    val io = Io(p)

    val cmdStream = Stream(SpiController.Cmd(p))
    cmdStream.valid := False
    cmdStream.payload.mode := SpiController.CmdMode.DATA
    cmdStream.payload.args := 0

    val rspFifo = StreamFifo(Bits(p.dataWidth bits), p.memory.rspFifoDepth)
    rspFifo.io.push.payload := io.rsp.payload
    rspFifo.io.push.valid := io.rsp.valid

    io.cmd << cmdStream

    val cfgPending = RegInit(False)
    val cfgInProgress = RegInit(False)
    when(io.config.configure) {
      cfgPending := True
    }

    val burst = new Area {
      val address = Reg(UInt(24 bits))
      val count = Reg(UInt(8 bits))
      val countResponse = Reg(UInt(8 bits))
      val size = Reg(UInt(3 bits))
    }

    val configuration = new Area {
      val mode = Reg(Bits(p.modeWidth bits)).init(B(0))
      val dummyCycles = Reg(UInt(log2Up(p.maxDummyCycles * 2) bits)).init(U(0))
      val cmd = Bits(8 bits)
      switch(mode) {
        is(B(2)) {
          cmd := 0xe7
        }
        is(B(1)) {
          cmd := 0xbb
        }
        default {
          cmd := 0x03
        }
      }
      def latch {
        mode := io.config.mode
        dummyCycles := io.config.dummyCycles
      }
    }

    io.busRsp.valid := False
    io.busRsp.last := False
    val rspHandler = new Area {
      val data = Reg(Bits(dataWidth bits))
      val counter = Reg(UInt(log2Up(dataWidth / 8) bits)).init(0)
      val push = RegInit(False)

      rspFifo.io.pop.ready := False
      when(rspFifo.io.pop.valid && !push) {
        data(8 * counter, 8 bits) := rspFifo.io.pop.payload.asBits
        when(!cfgInProgress) {
          counter := counter + 1
        }
        rspFifo.io.pop.ready := True
        when(counter === 3) {
          push := True
        }
      }
      when(push) {
        io.busRsp.valid := True
        when(burst.countResponse === 0) {
          io.busRsp.last := True
        }
        when(io.busRsp.fire) {
          burst.countResponse := burst.countResponse - 1
          push := False
        }
      }
    }
    io.busRsp.data := rspHandler.data

    val stateMachine = new Area {
      val state = RegInit(State.IDLE)
      val counter = new Area {
        val value = Reg(UInt(log2Up(dataWidth / 8) bits))
        def resetAddr = value := 3 - 1
        def resetData = value := (dataWidth / 8) - 1
        def nextValue = value := value - 1
      }

      io.busCmd.ready := False
      switch(state) {
        is(State.IDLE) {
          when(io.busCmd.valid && !cfgPending) {
            io.busCmd.ready := True
            burst.address := io.busCmd.addr
            burst.count := io.busCmd.count
            burst.countResponse := io.busCmd.count
            burst.size := 0
            state := State.ENABLESPI
          }
        }
        is(State.ENABLESPI) {
          val enableSpi = SpiController.CmdCs(p)
          enableSpi.enable := True
          enableSpi.index := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.CS
          cmdStream.payload.args.assignFromBits(enableSpi.asBits.resized)

          when(cmdStream.ready) {
            state := State.COMMAND
          }
        }
        is(State.COMMAND) {
          val readCommand = SpiController.CmdData(p)
          readCommand.read := False
          readCommand.mode := configuration.mode
          readCommand.data := configuration.cmd

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(readCommand.asBits)

          counter.resetAddr
          when(cmdStream.ready) {
            state := State.ADDRESS
          }
        }
        is(State.ADDRESS) {
          val addressCommand = SpiController.CmdData(p)
          addressCommand.read := False
          addressCommand.mode := configuration.mode
          addressCommand.data := burst.address(8 * counter.value, 8 bits).asBits

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(addressCommand.asBits)

          when(cmdStream.ready) {
            when(counter.value === 0) {
              counter.resetData
              when(configuration.dummyCycles =/= U(0)) {
                state := State.DUMMYCYCLES
              } otherwise {
                state := State.DATA
              }
            } otherwise {
              counter.nextValue
            }
          }
        }
        is(State.DUMMYCYCLES) {
          val dummyCycles = SpiController.CmdDummyCycles(p)
          dummyCycles.cycles := configuration.dummyCycles

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DUMMYCYCLES
          cmdStream.payload.args.assignFromBits(dummyCycles.asBits.resized)

          when(cmdStream.ready) {
            state := State.DATA
          }
        }
        is(State.DATA) {
          val dataCommand = SpiController.CmdData(p)
          dataCommand.read := True
          dataCommand.mode := configuration.mode
          dataCommand.data := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(dataCommand.asBits)

          when(cmdStream.ready) {
            when(counter.value === 0) {
              counter.resetData
              burst.count := burst.count - 1
              when(burst.count === 0) {
                state := State.DISABLESPI
              }
            } otherwise {
              counter.nextValue
            }
          }
        }
        is(State.DISABLESPI) {
          val enableSpi = SpiController.CmdCs(p)
          enableSpi.enable := False
          enableSpi.index := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.CS
          cmdStream.payload.args.assignFromBits(enableSpi.asBits.resized)

          when(cmdStream.ready) {
            state := State.IDLE
          }
        }
      }
    }

    val configureFlash = new Area {
      object OpType extends SpinalEnum {
        val WRITE_ENABLE, WRITE_REGISTER, READ_REGISTER = newElement()
      }
      object OpStep extends SpinalEnum {
        val IDLE, ENABLESPI, COMMAND, ADDRESS, DATA, DISABLESPI = newElement()
      }
      val currentOp = Reg(OpType()).init(OpType.WRITE_ENABLE)
      val currentStep = Reg(OpStep()).init(OpStep.IDLE)

      switch(currentStep) {
        is(OpStep.IDLE) {
          when(stateMachine.state === State.IDLE && cfgPending) {
            cfgInProgress := True
            currentStep := OpStep.ENABLESPI
          }
        }
        is(OpStep.ENABLESPI) {
          val enableSpi = SpiController.CmdCs(p)
          enableSpi.enable := True
          enableSpi.index := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.CS
          cmdStream.payload.args.assignFromBits(enableSpi.asBits.resized)

          when(cmdStream.ready) {
            currentStep := OpStep.COMMAND
          }
        }
        is(OpStep.COMMAND) {
          val readCommand = SpiController.CmdData(p)
          readCommand.read := False
          readCommand.mode := 0
          when(currentOp === OpType.WRITE_ENABLE) {
            readCommand.data := 0x06
          } otherwise {
            readCommand.data := 0x61
          }

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(readCommand.asBits)

          when(cmdStream.ready) {
            when(currentOp === OpType.WRITE_ENABLE) {
              currentStep := OpStep.DISABLESPI
            } otherwise {
              currentStep := OpStep.DATA
            }
          }
        }
        is(OpStep.DATA) {
          val dataCommand = SpiController.CmdData(p)
          dataCommand.read := True
          dataCommand.mode := 0
          dataCommand.data := io.config.evcr

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(dataCommand.asBits)

          when(cmdStream.ready) {
            currentStep := OpStep.DISABLESPI
          }
        }
        is(OpStep.DISABLESPI) {
          val enableSpi = SpiController.CmdCs(p)
          enableSpi.enable := False
          enableSpi.index := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.CS
          cmdStream.payload.args.assignFromBits(enableSpi.asBits.resized)

          when(cmdStream.ready) {
            when(currentOp === OpType.WRITE_ENABLE) {
              currentOp := OpType.WRITE_REGISTER
              currentStep := OpStep.ENABLESPI
            } otherwise {
              currentOp := OpType.WRITE_ENABLE
              cfgPending := False
              cfgInProgress := False
              currentStep := OpStep.IDLE
              configuration.latch
            }
          }
        }
      }
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Config,
      p: SpiControllerCtrl.Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.SpiXipController, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length
    val regOffset = staticOffset + 0x0

    ctrl.configure := False
    busCtrl.onWrite(regOffset + 0x0) {
      ctrl.configure := True
    }
    val mode = Reg(ctrl.mode).init(B(0))
    busCtrl.readAndWrite(mode, address = regOffset + 0x4, bitOffset = 0)
    ctrl.mode := mode

    val dummyCycles = Reg(ctrl.dummyCycles).init(U(0))
    busCtrl.readAndWrite(dummyCycles, address = regOffset + 0x4, bitOffset = 8)
    ctrl.dummyCycles := dummyCycles

    busCtrl.driveAndRead(ctrl.evcr, address = regOffset + 0x4, bitOffset = 16)
  }
}
