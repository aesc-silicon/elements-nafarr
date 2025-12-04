// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.i2c

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import nafarr.IpIdentification
import nafarr.library.ClockDivider

object I2cControllerCtrl {
  def apply(p: Parameter = Parameter.default()) = I2cControllerCtrl(p)

  case class InitParameter(clockDivider: Int = 0) {}
  object InitParameter {
    def disabled = InitParameter(0)
  }

  case class PermissionParameter(
      busCanWriteClockDividerConfig: Boolean
  ) {}
  object PermissionParameter {
    def granted = PermissionParameter(true)
    def restricted = PermissionParameter(false)
  }

  case class MemoryMappedParameter(
      cmdFifoDepth: Int,
      rspFifoDepth: Int
  ) {
    require(cmdFifoDepth > 0 && cmdFifoDepth < 256)
    require(rspFifoDepth > 0 && rspFifoDepth < 256)
  }
  object MemoryMappedParameter {
    def lightweight = MemoryMappedParameter(4, 4)
    def default = MemoryMappedParameter(16, 16)
    def full = MemoryMappedParameter(64, 64)
  }

  case class Parameter(
      io: I2c.Parameter,
      init: InitParameter = InitParameter.disabled,
      permission: PermissionParameter = PermissionParameter.granted,
      memory: MemoryMappedParameter = MemoryMappedParameter.default,
      clockDividerWidth: Int = 16
  ) {
    require(
      (init != null && init.clockDivider > 0) ||
        (permission != null && permission.busCanWriteClockDividerConfig),
      "Clock divider value not set. Either configure an init or grant bus write access."
    )
    require(clockDividerWidth > 1, "Clock Divider width needs to be at least 1 bit")
  }

  object Parameter {
    def lightweight(interrupts: Int = 0) = Parameter(
      io = I2c.Parameter(interrupts),
      memory = MemoryMappedParameter.lightweight
    )
    def default(interrupts: Int = 0) = Parameter(
      io = I2c.Parameter(interrupts)
    )
    def full(interrupts: Int = 0) = Parameter(
      io = I2c.Parameter(interrupts),
      memory = MemoryMappedParameter.full
    )
  }

  object State extends SpinalEnum {
    val IDLE, START, SENDDATA, SENDACK, RECVDATA, RECVACK, STOP = newElement()
  }
  object Samples extends SpinalEnum {
    val FIRST, SECOND, THIRD, FOURTH = newElement()
  }

  case class Config(p: Parameter) extends Bundle {
    val config = Bits(31 bits)
    val clockDivider = UInt(p.clockDividerWidth bits)
    val clockDividerReload = Bool
  }

  case class Io(p: Parameter) extends Bundle {
    val config = in(Config(p))
    val i2c = master(I2c.Io(p.io))
    val interrupt = out(Bool)
    val pendingInterrupts = in(Bits(2 + p.io.interrupts bits))
    val cmd = slave(Stream(I2cController.Cmd()))
    val rsp = master(Stream(I2cController.Rsp()))
  }

  case class I2cControllerCtrl(p: Parameter) extends Component {
    val io = Io(p)
    val ctrlEnable = RegInit(True)

    val ctrl = new ClockEnableArea(ctrlEnable) {

      val clockDivider = new ClockDivider(p.clockDividerWidth)
      clockDivider.io.value := io.config.clockDivider
      clockDivider.io.reload := io.config.clockDividerReload

      val tickCounter = new Area {
        val value = Reg(UInt(2 bits)).init(0)
        def reset() = value := 0
        when(clockDivider.io.tick) {
          value := value + 1
        }
      }

      val dataCounter = new Area {
        val value = Reg(UInt(3 bits)).init(7)
        def reset() = value := 7
        def next() = value := value - 1
        def isLast() = value === 7
      }

      val stateMachine = new Area {
        val state = RegInit(State.IDLE)
        def firstState(cmd: I2cController.Cmd): Unit = {
          when(cmd.start) {
            state := State.START
          } otherwise {
            when(cmd.read) {
              state := State.RECVDATA
            } otherwise {
              state := State.SENDDATA
            }
          }
        }
        def skipIdle(valid: Bool, cmd: I2cController.Cmd): Unit = {
          when(valid) {
            firstState(cmd)
          } otherwise {
            state := State.IDLE
          }
        }
        val samples = RegInit(Samples.FIRST)
        val sclWrite = Reg(Bool).init(False)
        val sdaWrite = Reg(Bool).init(False)

        val ack = Reg(Bool).init(False)
        val hasStop = Reg(Bool).init(False)

        val rspValid = RegNext(False).init(False)
        val error = Reg(Bool)
        val data = Reg(Bits(8 bits))

        io.cmd.ready := False
        switch(state) {
          is(State.IDLE) {
            when(io.cmd.valid && clockDivider.io.tick) {
              tickCounter.reset()
              dataCounter.reset()
              firstState(io.cmd.payload)
            }
          }
          is(State.START) {
            when(clockDivider.io.tick) {
              when(tickCounter.value === 1) {
                sclWrite := False
              }
              when(tickCounter.value === 2) {
                sdaWrite := True
              }
              when(tickCounter.value === 3) {
                sclWrite := True
                state := State.SENDDATA
              }
            }
          }
          is(State.RECVDATA) {
            when(clockDivider.io.tick) {
              when(tickCounter.value === 0) {
                sdaWrite := False
                hasStop := io.cmd.payload.stop
              }
              when(tickCounter.value === 1) {
                sclWrite := False
              }
              when(tickCounter.value === 2) {
                data(dataCounter.value) := io.i2c.sda.read
                dataCounter.next()
              }
              when(tickCounter.value === 3) {
                sclWrite := True
                when(dataCounter.isLast()) {
                  state := State.RECVACK
                  error := False
                  rspValid := True
                }
              }
            }
          }
          is(State.RECVACK) {
            when(clockDivider.io.tick) {
              when(tickCounter.value === 0) {
                /* Do not ack when FIFO is full */
                sdaWrite := io.cmd.payload.ack & io.rsp.ready
              }
              when(tickCounter.value === 1) {
                io.cmd.ready := True
                sclWrite := False
              }
              when(tickCounter.value === 3) {
                sclWrite := True
                when(hasStop) {
                  state := State.STOP
                } otherwise {
                  skipIdle(io.cmd.valid, io.cmd.payload)
                }
              }
            }
          }
          is(State.SENDDATA) {
            when(clockDivider.io.tick) {
              when(tickCounter.value === 0) {
                hasStop := io.cmd.payload.stop
                sdaWrite := !io.cmd.payload.data(dataCounter.value)
              }
              when(tickCounter.value === 1) {
                sclWrite := False
                dataCounter.next()
              }
              when(tickCounter.value === 3) {
                sclWrite := True
                when(dataCounter.isLast()) {
                  state := State.SENDACK
                  io.cmd.ready := True
                }
              }
            }
          }
          is(State.SENDACK) {
            when(clockDivider.io.tick) {
              when(tickCounter.value === 0) {
                sdaWrite := False
              }
              when(tickCounter.value === 1) {
                sclWrite := False
              }
              when(tickCounter.value === 2) {
                error := io.i2c.sda.read
                data := 0
                rspValid := True
              }
              when(tickCounter.value === 3) {
                sclWrite := True
                when(hasStop) {
                  state := State.STOP
                } otherwise {
                  skipIdle(io.cmd.valid, io.cmd.payload)
                }
              }
            }
          }
          is(State.STOP) {
            when(clockDivider.io.tick) {
              when(tickCounter.value === 0) {
                sdaWrite := True
              }
              when(tickCounter.value === 1) {
                sclWrite := False
              }
              when(tickCounter.value === 2) {
                sdaWrite := False
              }
              when(tickCounter.value === 3) {
                skipIdle(io.cmd.valid, io.cmd.payload)
              }
            }
          }
        }
      }
    }
    when(io.cmd.valid || !(ctrl.stateMachine.state === State.IDLE)) {
      ctrlEnable := True
    } otherwise {
      ctrlEnable := False
    }

    io.rsp.valid := ctrl.stateMachine.rspValid
    io.rsp.payload.data := ctrl.stateMachine.data
    io.rsp.payload.error := ctrl.stateMachine.error

    io.i2c.scl.write := ctrl.stateMachine.sclWrite
    io.i2c.sda.write := ctrl.stateMachine.sdaWrite

    io.interrupt := io.pendingInterrupts.orR
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.I2cController, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length

    busCtrl.read(
      B(0, 24 bits) ## B(p.clockDividerWidth, 8 bits),
      staticOffset
    )

    busCtrl.read(
      B(0, 16 bits) ## B(p.memory.cmdFifoDepth, 8 bits) ## B(p.memory.rspFifoDepth, 8 bits),
      staticOffset + 0x4
    )

    val permissionBits = Bool(p.permission.busCanWriteClockDividerConfig)
    busCtrl.read(B(0, 32 - 1 bits) ## permissionBits, staticOffset + 0x8)
    val regOffset = staticOffset + 0xc

    val cmdLogic = new Area {
      val streamUnbuffered = Stream(I2cController.Cmd())
      streamUnbuffered.valid := busCtrl.isWriting(address = regOffset + 0x00)
      busCtrl.nonStopWrite(streamUnbuffered.data, bitOffset = 0)
      busCtrl.nonStopWrite(streamUnbuffered.start, bitOffset = 8)
      busCtrl.nonStopWrite(streamUnbuffered.stop, bitOffset = 9)
      busCtrl.nonStopWrite(streamUnbuffered.read, bitOffset = 10)
      busCtrl.nonStopWrite(streamUnbuffered.ack, bitOffset = 11)

      val (stream, fifoOccupancy) = streamUnbuffered.queueWithOccupancy(p.memory.cmdFifoDepth)
      val fifoVacancy = p.memory.cmdFifoDepth - fifoOccupancy
      busCtrl.read(fifoVacancy, address = regOffset + 0x04, 16)
      ctrl.cmd << stream
      streamUnbuffered.ready.allowPruning()
    }

    val rspLogic = new Area {
      val (stream, fifoOccupancy) = ctrl.rsp.queueWithOccupancy(p.memory.rspFifoDepth)
      busCtrl.readStreamNonBlocking(
        stream,
        address = regOffset + 0x00,
        validBitOffset = 31,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = regOffset + 0x04, 0)
    }

    val config = new Area {
      val cfg = Reg(ctrl.config)

      if (p.init != null && p.init.clockDivider != 0)
        cfg.clockDivider.init(p.init.clockDivider)
      if (p.permission != null && p.permission.busCanWriteClockDividerConfig)
        busCtrl.write(cfg.clockDivider, address = regOffset + 0x08)
      busCtrl.read(cfg.clockDivider, regOffset + 0x08)

      cfg.clockDividerReload := False
      busCtrl.onWrite(regOffset + 0x08) {
        cfg.clockDividerReload := True
      }

      ctrl.config <> cfg
    }

    val interruptCtrl = new Area {
      val cmdOccupancyTrigger = Reg(cloneOf(cmdLogic.fifoOccupancy))
      val cmdPreviousOccupancy = RegNext(cmdLogic.fifoOccupancy)
      busCtrl.readAndWrite(cmdOccupancyTrigger, regOffset + 0x10)
      val cmdTrigger =
        cmdLogic.fifoOccupancy === cmdOccupancyTrigger && cmdPreviousOccupancy === (cmdOccupancyTrigger + 1)

      val irqCtrl = new InterruptCtrl(2 + p.io.interrupts)
      irqCtrl.driveFrom(busCtrl, regOffset + 0x14)
      irqCtrl.io.inputs(0) := cmdTrigger
      irqCtrl.io.inputs(1) := rspLogic.stream.valid
      for (i <- 0 until p.io.interrupts) {
        irqCtrl.io.inputs(2 + i) := ctrl.i2c.interrupts(i)
      }
      ctrl.pendingInterrupts := irqCtrl.io.pendings
    }
  }
}
