// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.pio

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import spinal.lib.io.{TriStateArray, TriState}
import nafarr.IpIdentification
import nafarr.library.ClockDivider

object PioCtrl {
  def apply(parameter: Parameter = Parameter.default()) = PioCtrl(parameter)

  case class InitParameter(clockDivider: Int = 0, readDelay: Int = 0) {}
  object InitParameter {
    def disabled = InitParameter(0, 0)
  }

  case class PermissionParameter(busCanWriteClockDividerConfig: Boolean) {}
  object PermissionParameter {
    def granted = PermissionParameter(true)
  }

  case class MemoryMappedParameter(
      commandFifoDepth: Int = 16,
      readFifoDepth: Int = 8
  ) {
    require(commandFifoDepth > 0 && commandFifoDepth < 256)
    require(readFifoDepth > 0 && readFifoDepth < 256)
  }
  object MemoryMappedParameter {
    def lightweight = MemoryMappedParameter(4, 2)
    def default = MemoryMappedParameter(16, 8)
    def full = MemoryMappedParameter(32, 16)
  }

  case class Parameter(
      io: Pio.Parameter,
      readBufferDepth: Int = 2,
      init: InitParameter = InitParameter.disabled,
      permission: PermissionParameter = PermissionParameter.granted,
      memory: MemoryMappedParameter = MemoryMappedParameter.default,
      dataWidth: Int = 24,
      clockDividerWidth: Int = 20,
      readDelayWidth: Int = 8
  ) {
    val ioDataWidth = io.width + dataWidth
    require(
      ioDataWidth + 2 <= 32,
      s"IO width and data width exceed with $ioDataWidth the 30 bit limit."
    )

    require(
      (init != null && init.clockDivider > 0) ||
        (permission != null && permission.busCanWriteClockDividerConfig),
      "Clock divider value not set. Either configure an init or grant bus write access."
    )

    val readWidth = 1
  }
  object Parameter {
    def default(pins: Int = 1) = Parameter(Pio.Parameter(pins))
    def light(pins: Int = 1) =
      Parameter(Pio.Parameter(pins), memory = MemoryMappedParameter.lightweight, dataWidth = 16)
  }

  object CommandType extends SpinalEnum(binarySequential) {
    val HIGH, LOW, WAIT, READ = newElement()
  }

  case class CommandContainer(parameter: Parameter) extends Bundle {
    val command = CommandType()
    val pin = UInt(log2Up(parameter.io.width) bits)
    val data = Bits(parameter.dataWidth bits)
  }

  case class ReadContainer(parameter: Parameter) extends Bundle {
    val result = Bits(parameter.readWidth bits)
  }

  case class Config(parameter: Parameter) extends Bundle {
    val clockDivider = UInt(parameter.clockDividerWidth bits)
    val readDelay = UInt(parameter.readDelayWidth bits)
  }

  case class Io(parameter: Parameter) extends Bundle {
    val pio = Pio.Io(parameter.io)
    val config = in(Config(parameter))
    val commands = slave(Stream(CommandContainer(parameter)))
    val read = master(Stream(ReadContainer(parameter)))
    val readIsFull = in(Bool)
  }

  case class PioCtrl(parameter: Parameter) extends Component {
    val io = Io(parameter)

    val value = Bits(parameter.io.width bits)
    if (parameter.readBufferDepth > 0) {
      value := BufferCC(io.pio.pins.read, bufferDepth = Some(parameter.readBufferDepth))
    } else {
      value := io.pio.pins.read
    }

    val clockDivider = ClockDivider(parameter.clockDividerWidth)
    clockDivider.io.value := io.config.clockDivider
    clockDivider.io.reload := False

    val fsm = new StateMachine {
      val counter = Reg(UInt(parameter.dataWidth bits)).init(0)
      val write = Reg(Bits(parameter.io.width bits)).init(0)
      val direction = Reg(Bits(parameter.io.width bits)).init(0)
      io.commands.ready := False
      io.read.valid := False
      val pinNumber = io.commands.payload.pin.resize(log2Up(parameter.io.width))
      val readContainer = ReadContainer(parameter)
      readContainer.result := value(pinNumber).asBits
      io.read.payload := readContainer

      val stateIdle: State = new State with EntryPoint {
        whenIsActive {
          when(io.commands.valid) {
            switch(io.commands.payload.command) {
              is(CommandType.HIGH) {
                goto(stateHigh)
              }
              is(CommandType.LOW) {
                goto(stateLow)
              }
              is(CommandType.WAIT) {
                goto(stateWait)
              }
              is(CommandType.READ) {
                goto(stateRead)
              }
            }
          }
        }
      }
      val stateHigh = new State {
        whenIsActive {
          direction(pinNumber) := True
          write(pinNumber) := True
          goto(stateIdle)
        }
        onExit(io.commands.ready := True)
      }
      val stateLow = new State {
        whenIsActive {
          direction(pinNumber) := True
          write(pinNumber) := False
          goto(stateIdle)
        }
        onExit(io.commands.ready := True)
      }
      val stateWait = new State {
        onEntry {
          clockDivider.io.reload := True
          counter := 0
        }
        whenIsActive {
          when(clockDivider.io.tick) {
            counter := counter + 1
          }
          when(counter.asBits === io.commands.payload.data) {
            goto(stateIdle)
          }
        }
        onExit(io.commands.ready := True)
      }
      val stateRead = new State {
        onEntry {
          clockDivider.io.reload := True
          counter := 0
        }
        whenIsActive {
          direction(pinNumber) := False
          counter := counter + 1
          when(counter.asBits === B(io.config.readDelay, parameter.dataWidth bits)) {
            io.read.valid := True
            goto(stateIdle)
          }
        }
        onExit(io.commands.ready := True)
      }
    }

    io.pio.pins.write := fsm.write
    io.pio.pins.writeEnable := fsm.direction
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Pio, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length

    busCtrl.read(
      B(p.readBufferDepth, 8 bits) ## B(p.clockDividerWidth, 8 bits) ##
        B(p.dataWidth, 8 bits) ## B(p.io.width, 8 bits),
      staticOffset
    )

    busCtrl.read(
      B(0, 16 bits) ## B(p.memory.readFifoDepth, 8 bits) ## B(p.memory.commandFifoDepth, 8 bits),
      staticOffset + 0x4
    )

    if (p.permission != null) {
      val permissionBits = Bool(p.permission.busCanWriteClockDividerConfig)
      busCtrl.read(B(0, 32 - 1 bits) ## permissionBits, staticOffset + 0x8)
    } else {
      busCtrl.read(B(0), staticOffset + 0x8)
    }
    val regOffset = staticOffset + 0xc

    val tx = new Area {
      val cmdContainer = CommandContainer(p)
      val streamUnbuffered =
        busCtrl.createAndDriveFlow(cmdContainer, address = regOffset + 0x00).toStream
      val (stream, fifoOccupancy) =
        streamUnbuffered.queueWithOccupancy(p.memory.commandFifoDepth)
      val fifoVacancy = p.memory.commandFifoDepth - fifoOccupancy
      busCtrl.read(fifoVacancy, address = regOffset + 0x04, bitOffset = 16)
      ctrl.commands << stream
      streamUnbuffered.ready.allowPruning()
    }

    val rx = new Area {
      val (stream, fifoOccupancy) = ctrl.read.queueWithOccupancy(p.memory.readFifoDepth)
      ctrl.readIsFull := fifoOccupancy >= p.memory.readFifoDepth - 1
      busCtrl.readStreamNonBlocking(
        stream,
        address = regOffset + 0x00,
        validBitOffset = 16,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = regOffset + 0x04, bitOffset = 24)
    }

    val clockDivider = Reg(UInt(p.clockDividerWidth bits))
    if (p.init != null && p.init.clockDivider != 0)
      clockDivider.init(p.init.clockDivider)
    if (p.permission != null && p.permission.busCanWriteClockDividerConfig)
      busCtrl.write(clockDivider, regOffset + 0x08)
    busCtrl.read(clockDivider, regOffset + 0x08)
    ctrl.config.clockDivider := clockDivider

    val readDelay = Reg(UInt(p.readDelayWidth bits))
    if (p.init != null && p.init.readDelay != 0)
      readDelay.init(p.init.readDelay)
    busCtrl.readAndWrite(readDelay, regOffset + 0x0c)
    ctrl.config.readDelay := readDelay
  }
}
