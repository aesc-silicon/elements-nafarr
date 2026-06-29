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
  def apply(p: SpiControllerCtrl.Parameter, dataWidth: Int, txFifoDepth: Int = 64) =
    SpiXipControllerCtrl(p, dataWidth, txFifoDepth)

  object State extends SpinalEnum {
    val IDLE, ENABLESPI, COMMAND, ADDRESS, DUMMYCYCLES, DATA, DISABLESPI = newElement()
  }

  /** Phases of the generic flash command engine.
    *
    * A command runs as an optional Write-Enable prefix (`WREN_*`), the command
    * frame itself (`ENABLE -> COMMAND -> [ADDRESS] -> [DATA] -> DISABLE`) and an
    * optional Write-In-Progress poll loop (`POLL_*`) that re-reads the status
    * register until the busy bit clears.  The EVCR configuration write is just
    * one instance of this engine (opcode 0x61, no address, a single data byte,
    * WREN yes, poll no).
    */
  object OpStep extends SpinalEnum {
    val IDLE, WREN_ENABLE, WREN_CMD, WREN_DISABLE, ENABLE, COMMAND, ADDRESS, DATA, DISABLE,
        POLL_ENABLE, POLL_CMD, POLL_READ, POLL_DISABLE, DONE = newElement()
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val configure = base + 0x00
    val config = base + 0x04
    val command = base + 0x08
    val address = base + 0x0c
    val length = base + 0x10
    val start = base + 0x14
    val txData = base + 0x18
    val status = base + 0x1c
  }

  case class Config(p: SpiControllerCtrl.Parameter) extends Bundle {
    val mode = Bits(p.modeWidth bits)
    val dummyCycles = UInt(log2Up(p.maxDummyCycles * 2) bits)
    val evcr = Bits(8 bits)
    val configure = Bool()
    // Generic flash command descriptor (program / erase / arbitrary opcode).
    val opcode = Bits(8 bits)
    val address = UInt(24 bits)
    val length = UInt(9 bits)
    val hasAddress = Bool()
    val needsWren = Bool()
    val needsPoll = Bool()
    val start = Bool()
  }

  case class Status(p: SpiControllerCtrl.Parameter, txFifoDepth: Int) extends Bundle {
    val busy = Bool()
    val statusReg = Bits(8 bits)
    val txAvailability = UInt(log2Up(txFifoDepth + 1) bits)
  }

  case class Io(p: SpiControllerCtrl.Parameter, txFifoDepth: Int) extends Bundle {
    val config = in(Config(p))
    val status = out(Status(p, txFifoDepth))
    val txData = slave(Flow(Bits(8 bits)))
    val busCmd = slave(Stream(SpiXipController.GenericInterface.Cmd()))
    val busRsp = master(Stream(SpiXipController.GenericInterface.Rsp()))
    val cmd = master(Stream(SpiController.Cmd(p)))
    val rsp = slave(Flow(Bits(p.dataWidth bits)))
  }

  case class SpiXipControllerCtrl(
      p: SpiControllerCtrl.Parameter,
      dataWidth: Int,
      txFifoDepth: Int = 64
  ) extends Component {
    val io = Io(p, txFifoDepth)

    val cmdStream = Stream(SpiController.Cmd(p))
    cmdStream.valid := False
    cmdStream.payload.mode := SpiController.CmdMode.DATA
    cmdStream.payload.args := 0

    val rspFifo = StreamFifo(Bits(p.dataWidth bits), p.memory.rspFifoDepth)
    rspFifo.io.push.payload := io.rsp.payload
    rspFifo.io.push.valid := io.rsp.valid

    io.cmd << cmdStream

    // Software-filled FIFO that feeds the program data bytes.
    val txFifo = StreamFifo(Bits(8 bits), txFifoDepth)
    txFifo.io.push.valid := io.txData.valid
    txFifo.io.push.payload := io.txData.payload
    txFifo.io.pop.ready := False
    io.status.txAvailability := txFifo.io.availability

    // A command (EVCR configure or a generic flash command) is requested.
    val cfgPending = RegInit(False)
    val cfgInProgress = RegInit(False)
    val pendingIsEvcr = RegInit(False)
    when(io.config.configure) {
      cfgPending := True
      pendingIsEvcr := True
    }
    when(io.config.start) {
      cfgPending := True
      pendingIsEvcr := False
    }

    val busy = RegInit(False)
    io.status.busy := busy

    val statusReg = Reg(Bits(8 bits)).init(0)
    when(io.rsp.valid && cfgInProgress) {
      statusReg := io.rsp.payload
    }
    io.status.statusReg := statusReg

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

    /** Generic flash command engine.
      *
      * Drives WREN / program / erase / status-poll sequences over the shared SPI
      * command stream while the read state machine is held in IDLE (mutual
      * exclusion via `cfgPending`).  The EVCR configuration write reuses the same
      * engine.
      */
    val commandEngine = new Area {
      val step = RegInit(OpStep.IDLE)
      val opcode = Reg(Bits(8 bits)).init(0)
      val address = Reg(UInt(24 bits)).init(0)
      val length = Reg(UInt(9 bits)).init(0)
      val hasAddress = RegInit(False)
      val needsWren = RegInit(False)
      val needsPoll = RegInit(False)
      val isEvcr = RegInit(False)
      val addrCounter = Reg(UInt(2 bits)).init(0)

      def sendCs(enable: Boolean) = {
        val cs = SpiController.CmdCs(p)
        cs.enable := Bool(enable)
        cs.index := 0
        cmdStream.valid := True
        cmdStream.payload.mode := SpiController.CmdMode.CS
        cmdStream.payload.args.assignFromBits(cs.asBits.resized)
      }

      def sendByte(value: Bits, read: Boolean, valid: Bool = True) = {
        val data = SpiController.CmdData(p)
        data.read := Bool(read)
        data.mode := 0
        data.data := value
        cmdStream.valid := valid
        cmdStream.payload.mode := SpiController.CmdMode.DATA
        cmdStream.payload.args.assignFromBits(data.asBits)
      }

      switch(step) {
        is(OpStep.IDLE) {
          when(stateMachine.state === State.IDLE && cfgPending) {
            cfgInProgress := True
            busy := True
            addrCounter := 2
            address := io.config.address
            when(pendingIsEvcr) {
              isEvcr := True
              opcode := 0x61
              hasAddress := False
              length := 1
              needsWren := True
              needsPoll := False
            } otherwise {
              isEvcr := False
              opcode := io.config.opcode
              hasAddress := io.config.hasAddress
              length := io.config.length
              needsWren := io.config.needsWren
              needsPoll := io.config.needsPoll
            }
            when(pendingIsEvcr || io.config.needsWren) {
              step := OpStep.WREN_ENABLE
            } otherwise {
              step := OpStep.ENABLE
            }
          }
        }
        is(OpStep.WREN_ENABLE) {
          sendCs(true)
          when(cmdStream.ready) {
            step := OpStep.WREN_CMD
          }
        }
        is(OpStep.WREN_CMD) {
          sendByte(B(0x06, 8 bits), read = false)
          when(cmdStream.ready) {
            step := OpStep.WREN_DISABLE
          }
        }
        is(OpStep.WREN_DISABLE) {
          sendCs(false)
          when(cmdStream.ready) {
            step := OpStep.ENABLE
          }
        }
        is(OpStep.ENABLE) {
          sendCs(true)
          when(cmdStream.ready) {
            step := OpStep.COMMAND
          }
        }
        is(OpStep.COMMAND) {
          val op = Bits(8 bits)
          when(isEvcr) {
            op := 0x61
          } otherwise {
            op := opcode
          }
          sendByte(op, read = false)
          when(cmdStream.ready) {
            addrCounter := 2
            when(hasAddress) {
              step := OpStep.ADDRESS
            }.elsewhen(isEvcr || length =/= 0) {
              step := OpStep.DATA
            }.otherwise {
              step := OpStep.DISABLE
            }
          }
        }
        is(OpStep.ADDRESS) {
          sendByte(address(8 * addrCounter, 8 bits).asBits, read = false)
          when(cmdStream.ready) {
            when(addrCounter === 0) {
              when(isEvcr || length =/= 0) {
                step := OpStep.DATA
              } otherwise {
                step := OpStep.DISABLE
              }
            } otherwise {
              addrCounter := addrCounter - 1
            }
          }
        }
        is(OpStep.DATA) {
          when(isEvcr) {
            sendByte(io.config.evcr, read = true)
            when(cmdStream.ready) {
              step := OpStep.DISABLE
            }
          } otherwise {
            sendByte(txFifo.io.pop.payload, read = false, valid = txFifo.io.pop.valid)
            when(cmdStream.fire) {
              txFifo.io.pop.ready := True
              when(length === 1) {
                step := OpStep.DISABLE
              } otherwise {
                length := length - 1
              }
            }
          }
        }
        is(OpStep.DISABLE) {
          sendCs(false)
          when(cmdStream.ready) {
            when(needsPoll) {
              step := OpStep.POLL_ENABLE
            } otherwise {
              step := OpStep.DONE
            }
          }
        }
        is(OpStep.POLL_ENABLE) {
          sendCs(true)
          when(cmdStream.ready) {
            step := OpStep.POLL_CMD
          }
        }
        is(OpStep.POLL_CMD) {
          sendByte(B(0x05, 8 bits), read = false)
          when(cmdStream.ready) {
            step := OpStep.POLL_READ
          }
        }
        is(OpStep.POLL_READ) {
          sendByte(B(0x00, 8 bits), read = true)
          when(cmdStream.ready) {
            step := OpStep.POLL_DISABLE
          }
        }
        is(OpStep.POLL_DISABLE) {
          sendCs(false)
          when(cmdStream.ready) {
            when(statusReg(0)) {
              step := OpStep.POLL_ENABLE
            } otherwise {
              step := OpStep.DONE
            }
          }
        }
        is(OpStep.DONE) {
          when(isEvcr) {
            configuration.latch
          }
          busy := False
          cfgPending := False
          cfgInProgress := False
          step := OpStep.IDLE
        }
      }
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      io: Io,
      p: SpiControllerCtrl.Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.SpiXipController, 1, 1, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    io.config.configure := False
    busCtrl.onWrite(regs.configure) {
      io.config.configure := True
    }
    val mode = Reg(io.config.mode).init(B(0))
    busCtrl.readAndWrite(mode, address = regs.config, bitOffset = 0)
    io.config.mode := mode

    val dummyCycles = Reg(io.config.dummyCycles).init(U(0))
    busCtrl.readAndWrite(dummyCycles, address = regs.config, bitOffset = 8)
    io.config.dummyCycles := dummyCycles

    busCtrl.driveAndRead(io.config.evcr, address = regs.config, bitOffset = 16)

    // Generic flash command descriptor.
    val opcode = Reg(io.config.opcode).init(B(0))
    busCtrl.readAndWrite(opcode, address = regs.command, bitOffset = 0)
    io.config.opcode := opcode

    val hasAddress = Reg(io.config.hasAddress).init(False)
    busCtrl.readAndWrite(hasAddress, address = regs.command, bitOffset = 8)
    io.config.hasAddress := hasAddress

    val needsWren = Reg(io.config.needsWren).init(False)
    busCtrl.readAndWrite(needsWren, address = regs.command, bitOffset = 9)
    io.config.needsWren := needsWren

    val needsPoll = Reg(io.config.needsPoll).init(False)
    busCtrl.readAndWrite(needsPoll, address = regs.command, bitOffset = 10)
    io.config.needsPoll := needsPoll

    val address = Reg(io.config.address).init(U(0))
    busCtrl.readAndWrite(address, address = regs.address, bitOffset = 0)
    io.config.address := address

    val length = Reg(io.config.length).init(U(0))
    busCtrl.readAndWrite(length, address = regs.length, bitOffset = 0)
    io.config.length := length

    io.config.start := False
    busCtrl.onWrite(regs.start) {
      io.config.start := True
    }

    io.txData.valid := False
    busCtrl.onWrite(regs.txData) {
      io.txData.valid := True
    }
    busCtrl.nonStopWrite(io.txData.payload, bitOffset = 0)

    busCtrl.read(io.status.busy, address = regs.status, bitOffset = 0)
    busCtrl.read(io.status.statusReg, address = regs.status, bitOffset = 8)
    busCtrl.read(io.status.txAvailability, address = regs.status, bitOffset = 16)
  }
}
