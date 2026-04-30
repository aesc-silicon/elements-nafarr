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
      readDelayWidth: Int = 8,
      interrupt: Boolean = true,
      error: Boolean = true
  ) {
    require(
      // 4-bit command + 4-bit pin + dataWidth bits data must fit in 32 bits
      dataWidth + 8 <= 32,
      s"dataWidth $dataWidth exceeds the 24-bit limit (4-bit command + 4-bit pin + dataWidth <= 32)."
    )
    require(
      io.width <= 16,
      s"IO width ${io.width} exceeds the 16-pin limit of the 4-bit pin field."
    )
    require(io.width <= dataWidth, s"IO width must be small or equal to data width.")

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
    val HIGH, HIGH_SET, LOW, LOW_SET, FLOAT, FLOAT_SET, TOGGLE, TOGGLE_SET, WAIT, WAIT_FOR_HIGH,
        WAIT_FOR_LOW, READ, LOOP = newElement()
  }

  case class CommandContainer(parameter: Parameter) extends Bundle {
    val command = CommandType()
    val pin = UInt(4 bits)
    val data = Bits(parameter.dataWidth bits)
  }

  case class ReadContainer(parameter: Parameter) extends Bundle {
    val result = Bits(parameter.readWidth bits)
  }

  case class Config(parameter: Parameter) extends Bundle {
    val clockDivider = UInt(parameter.clockDividerWidth bits)
    val readDelay = UInt(parameter.readDelayWidth bits)
    val enable = Bool()
    val stopAtLoop = Bool()
    val programReset = Bool()
  }

  case class Io(parameter: Parameter) extends Bundle {
    val pio = Pio.Io(parameter.io)
    val interrupt = out(Bool)
    val pendingInterrupts = in(Bits(2 bits))
    val config = in(Config(parameter))
    val programWrite = slave(Flow(CommandContainer(parameter)))
    val writePtr = out(UInt(log2Up(parameter.memory.commandFifoDepth) bits))
    val execPtr = out(UInt(log2Up(parameter.memory.commandFifoDepth) bits))
    val loopDone = out(Bool)
    val read = master(Stream(ReadContainer(parameter)))
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

    io.interrupt := io.pendingInterrupts.orR
    io.loopDone := False

    // Program memory
    val ptrWidth = log2Up(parameter.memory.commandFifoDepth)
    val programMem = Mem(CommandContainer(parameter), parameter.memory.commandFifoDepth)
    val writePtrReg = Reg(UInt(ptrWidth bits)).init(0)
    val execPtrReg = Reg(UInt(ptrWidth bits)).init(0)
    val loopCounter = Reg(UInt(parameter.dataWidth bits)).init(0)

    when(io.programWrite.valid) {
      programMem.write(writePtrReg, io.programWrite.payload)
      writePtrReg := writePtrReg + 1
    }
    when(io.config.programReset) {
      writePtrReg := 0
    }
    io.writePtr := writePtrReg
    io.execPtr := execPtrReg

    // Reset execution state on enable rising edge
    val enablePrev = RegNext(io.config.enable, False)
    when(io.config.enable && !enablePrev) {
      execPtrReg := 0
      loopCounter := 0
    }

    val currentCmd = programMem.readAsync(execPtrReg)
    val pinNumber = currentCmd.pin.resize(log2Up(parameter.io.width))

    val readContainer = ReadContainer(parameter)
    readContainer.result := value(pinNumber).asBits

    val fsm = new StateMachine {
      val counter = Reg(UInt(parameter.dataWidth bits)).init(0)
      val write = Reg(Bits(parameter.io.width bits)).init(0)
      val direction = Reg(Bits(parameter.io.width bits)).init(0)
      io.read.valid := False
      io.read.payload := readContainer

      val stateIdle: State = new State with EntryPoint {
        whenIsActive {
          when(io.config.enable && (execPtrReg < writePtrReg)) {
            switch(currentCmd.command) {
              is(CommandType.HIGH) {
                goto(stateHigh)
              }
              is(CommandType.HIGH_SET) {
                goto(stateHighSet)
              }
              is(CommandType.LOW) {
                goto(stateLow)
              }
              is(CommandType.LOW_SET) {
                goto(stateLowSet)
              }
              is(CommandType.FLOAT) {
                goto(stateFloat)
              }
              is(CommandType.FLOAT_SET) {
                goto(stateFloatSet)
              }
              is(CommandType.TOGGLE) {
                goto(stateToggle)
              }
              is(CommandType.TOGGLE_SET) {
                goto(stateToggleSet)
              }
              is(CommandType.WAIT) {
                goto(stateWait)
              }
              is(CommandType.WAIT_FOR_HIGH) {
                goto(stateWaitForHigh)
              }
              is(CommandType.WAIT_FOR_LOW) {
                goto(stateWaitForLow)
              }
              is(CommandType.READ) {
                goto(stateRead)
              }
              is(CommandType.LOOP) {
                goto(stateLoop)
              }
            }
          }
        }
      }

      // Single-cycle states: execute and advance execPtr in the same cycle
      val stateHigh = new State {
        whenIsActive {
          direction(pinNumber) := True
          write(pinNumber) := True
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }
      val stateHighSet = new State {
        whenIsActive {
          val mask = currentCmd.data(parameter.io.width - 1 downto 0)
          direction := direction | mask
          write := write | mask
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }
      val stateLow = new State {
        whenIsActive {
          direction(pinNumber) := True
          write(pinNumber) := False
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }
      val stateLowSet = new State {
        whenIsActive {
          val mask = currentCmd.data(parameter.io.width - 1 downto 0)
          direction := direction | mask
          write := write & ~mask
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }
      val stateFloat = new State {
        whenIsActive {
          direction(pinNumber) := False
          write(pinNumber) := False
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }
      val stateFloatSet = new State {
        whenIsActive {
          val mask = currentCmd.data(parameter.io.width - 1 downto 0)
          direction := direction & ~mask
          write := write & ~mask
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }
      val stateToggle = new State {
        whenIsActive {
          write(pinNumber) := !write(pinNumber)
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }
      val stateToggleSet = new State {
        whenIsActive {
          val mask = currentCmd.data(parameter.io.width - 1 downto 0)
          write := write ^ mask
          execPtrReg := execPtrReg + 1
          goto(stateIdle)
        }
      }

      // Multi-cycle states: advance execPtr only when done
      val stateWait = new State {
        onEntry {
          clockDivider.io.reload := True
          counter := 0
        }
        whenIsActive {
          when(clockDivider.io.tick) {
            counter := counter + 1
          }
          when(counter.asBits === currentCmd.data) {
            execPtrReg := execPtrReg + 1
            goto(stateIdle)
          }
        }
      }
      val stateWaitForHigh = new State {
        whenIsActive {
          when(value(pinNumber)) {
            execPtrReg := execPtrReg + 1
            goto(stateIdle)
          }
        }
      }
      val stateWaitForLow = new State {
        whenIsActive {
          when(!value(pinNumber)) {
            execPtrReg := execPtrReg + 1
            goto(stateIdle)
          }
        }
      }
      val stateRead = new State {
        onEntry {
          clockDivider.io.reload := True
          counter := 0
        }
        whenIsActive {
          direction(pinNumber) := False
          when(clockDivider.io.tick) {
            counter := counter + 1
          }
          when(counter === io.config.readDelay.resized) {
            io.read.valid := True
            when(io.read.ready) {
              execPtrReg := execPtrReg + 1
              goto(stateIdle)
            }
          }
        }
      }

      // LOOP: data=0 endless, data=N run N times total then fire loopDone
      // When stopAtLoop is set, always advance past the LOOP instruction so
      // execPtr reaches writePtr and flush can detect completion.
      val stateLoop = new State {
        whenIsActive {
          when(io.config.stopAtLoop) {
            loopCounter := 0
            execPtrReg := execPtrReg + 1
          } elsewhen (currentCmd.data === 0) {
            execPtrReg := 0
          } elsewhen (loopCounter < currentCmd.data.asUInt - 1) {
            loopCounter := loopCounter + 1
            execPtrReg := 0
          } otherwise {
            loopCounter := 0
            io.loopDone := True
            execPtrReg := execPtrReg + 1
          }
          goto(stateIdle)
        }
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
    val idCtrl = IpIdentification(IpIdentification.Ids.Pio, 1, 1, 0)
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

    // Basic control
    val enable = Reg(ctrl.config.enable).init(False)
    busCtrl.readAndWrite(enable, regOffset + 0x00, bitOffset = 0x0)
    ctrl.config.enable := enable

    val stopAtLoop = Reg(ctrl.config.stopAtLoop).init(False)
    busCtrl.readAndWrite(stopAtLoop, regOffset + 0x00, bitOffset = 0x1)
    ctrl.config.stopAtLoop := stopAtLoop

    val tx = new Area {
      // Write into program memory: each bus write advances writePtr
      val cmdContainer = CommandContainer(p)
      ctrl.programWrite << busCtrl.createAndDriveFlow(cmdContainer, address = regOffset + 0x04)

      // Program status (read) and reset (write) share the same address:
      //   read  -> execPtr[7:0] | writePtr[15:8] | rxOccupancy[31:24] (set by rx area)
      //   write -> pulse programReset (any write triggers reset)
      busCtrl.read(ctrl.execPtr, address = regOffset + 0x08, bitOffset = 0)
      busCtrl.read(ctrl.writePtr, address = regOffset + 0x08, bitOffset = 8)
      val programResetPulse = Bool()
      programResetPulse := False
      busCtrl.onWrite(regOffset + 0x08) {
        programResetPulse := True
      }
      ctrl.config.programReset := programResetPulse
    }

    val rx = new Area {
      val (stream, fifoOccupancy) = ctrl.read.queueWithOccupancy(p.memory.readFifoDepth)
      val readIsFull = fifoOccupancy >= p.memory.readFifoDepth - 1
      busCtrl.readStreamNonBlocking(
        stream,
        address = regOffset + 0x04,
        validBitOffset = 16,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = regOffset + 0x08, bitOffset = 24)
    }

    val clockDivider = Reg(UInt(p.clockDividerWidth bits))
    if (p.init != null && p.init.clockDivider != 0)
      clockDivider.init(p.init.clockDivider)
    if (p.permission != null && p.permission.busCanWriteClockDividerConfig)
      busCtrl.write(clockDivider, regOffset + 0x0c)
    busCtrl.read(clockDivider, regOffset + 0x0c)
    ctrl.config.clockDivider := clockDivider

    val readDelay = Reg(UInt(p.readDelayWidth bits))
    if (p.init != null && p.init.readDelay != 0)
      readDelay.init(p.init.readDelay)
    busCtrl.readAndWrite(readDelay, regOffset + 0x10)
    ctrl.config.readDelay := readDelay

    val error = new Area {
      if (p.error) {
        val errorCtrl = new InterruptCtrl(1)
        errorCtrl.driveFrom(busCtrl, regOffset + 0x14)
        errorCtrl.io.inputs(0) := rx.readIsFull && ctrl.read.valid // read FIFO is full error
      }
    }

    val interrupt = new Area {
      if (p.interrupt) {
        val irqCtrl = new InterruptCtrl(2)
        irqCtrl.driveFrom(busCtrl, regOffset + 0x1c)
        irqCtrl.io.inputs(0) := (rx.fifoOccupancy > 0)
        irqCtrl.io.inputs(1) := ctrl.loopDone
        ctrl.pendingInterrupts := irqCtrl.io.pendings
      } else {
        ctrl.pendingInterrupts := 0
      }
    }
  }
}
