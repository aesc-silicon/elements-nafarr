// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.pwm

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import nafarr.IpIdentification
import nafarr.library.ClockDivider

object PwmCtrl {
  def apply(p: Parameter = Parameter.default()) = PwmCtrl(p)

  case class InitParameter(clockDivider: Int) {}
  object InitParameter {
    def disabled = InitParameter(0)
  }

  case class PermissionParameter(busCanWriteClockDividerConfig: Boolean) {}
  object PermissionParameter {
    def granted = PermissionParameter(true)
  }

  case class Parameter(
      io: Pwm.Parameter,
      init: InitParameter = InitParameter.disabled,
      permission: PermissionParameter = PermissionParameter.granted,
      clockDividerWidth: Int = 20,
      channelPeriodWidth: Int = 20,
      channelPulseWidth: Int = 20,
      deadTimeWidth: Int = 8,
      shotCountWidth: Int = 8
  ) {
    require(channelPeriodWidth >= channelPulseWidth, "Channel pulse cannot be wider than period.")
  }
  object Parameter {
    def default(channels: Int = 1) = Parameter(Pwm.Parameter(channels))
  }

  case class ConfigChannel(p: Parameter) extends Bundle {
    val enable = Bool()
    val invert = Bool()
    val mode = Bool() // False = edge-aligned, True = center-aligned
    val clockDivider = UInt(p.clockDividerWidth bits)
    val period = UInt(p.channelPeriodWidth bits)
    val risingEdge = UInt(p.channelPulseWidth bits)
    val fallingEdge = UInt(p.channelPulseWidth bits)
    val deadTime = UInt(p.deadTimeWidth bits)
    val phaseOffset = UInt(p.channelPeriodWidth bits)
    val shotCount = UInt(p.shotCountWidth bits)
  }

  case class InterruptConfig(p: Parameter) extends Bundle {
    val valid = out(Bits(p.io.channels bits))
    val pending = in(Bits(p.io.channels bits))
  }

  case class Io(p: Parameter) extends Bundle {
    val pwm = Pwm.Io(p.io)
    val shadow = in(Vec(ConfigChannel(p), p.io.channels))
    val shotDone = out(Bits(p.io.channels bits))
    val interrupt = out(Bool)
    val irqPeriodComplete = InterruptConfig(p)
    val faultError = out(Bool)
    val errorPending = in(Bool)
  }

  case class PwmCtrl(p: Parameter) extends Component {
    val io = Io(p)

    for (i <- 0 until p.io.channels) {
      val channel = new Area {
        val s = io.shadow(i)

        // Per-channel clock divider
        val clockDivider = new ClockDivider(p.clockDividerWidth)
        clockDivider.io.value := s.clockDivider
        clockDivider.io.reload := False
        def tick = clockDivider.io.tick

        // Shadow-buffered live waveform registers: latched on enable or period-end
        val periodLive = Reg(UInt(p.channelPeriodWidth bits)).init(0)
        val risingEdgeLive = Reg(UInt(p.channelPulseWidth bits)).init(0)
        val fallingEdgeLive = Reg(UInt(p.channelPulseWidth bits)).init(0)

        // Counter state
        val periodCounter = Reg(UInt(p.channelPeriodWidth bits)).init(0)
        val direction = Reg(Bool()).init(False) // False = up, True = down

        // N-shot state
        val shotDoneReg = Reg(Bool()).init(False)
        val shotRemaining = Reg(UInt(p.shotCountWidth bits)).init(0)
        val enablePrev = RegNext(s.enable, False)
        val enableRise = s.enable && !enablePrev

        // Dead-time state
        val deadTimeCounter = Reg(UInt(p.deadTimeWidth bits)).init(0)

        // Output comparison (at Area scope so RegNext can reference desired)
        val desired =
          periodCounter >= risingEdgeLive.resize(p.channelPeriodWidth) &&
            periodCounter <= fallingEdgeLive.resize(p.channelPeriodWidth)
        val desiredPrev = RegNext(desired, False)
        val transition = desired =/= desiredPrev
        val inDeadTime = s.deadTime =/= 0 && (transition || deadTimeCounter =/= 0)

        // Period-complete wire (set inside mode logic below)
        val periodComplete = Bool()
        periodComplete := False

        val shotActive = s.shotCount === 0 || !shotDoneReg

        // Output defaults
        io.pwm.output(i) := s.invert
        io.pwm.compOutput(i) := s.invert
        io.pwm.syncOut(i) := False
        io.irqPeriodComplete.valid(i) := False
        io.shotDone(i) := shotDoneReg

        // On enable rising edge: load live regs and reset counter state
        when(enableRise) {
          periodLive := s.period
          risingEdgeLive := s.risingEdge
          fallingEdgeLive := s.fallingEdge
          periodCounter := s.phaseOffset
          direction := False
          shotDoneReg := False
          shotRemaining := s.shotCount
        } elsewhen (s.enable && shotActive) {
          when(!s.mode) {
            // Edge-aligned: count down from periodLive to 0
            when(periodCounter === 0) {
              periodCounter := periodLive
              io.pwm.syncOut(i) := True
              periodComplete := True
            } elsewhen (tick) {
              periodCounter := periodCounter - 1
            }
          } otherwise {
            // Center-aligned: count up to periodLive, then back down to 0
            when(!direction) {
              when(periodCounter === periodLive) {
                direction := True
                io.pwm.syncOut(i) := True
              } elsewhen (tick) {
                periodCounter := periodCounter + 1
              }
            } otherwise {
              when(periodCounter === 0) {
                direction := False
                periodComplete := True
              } elsewhen (tick) {
                periodCounter := periodCounter - 1
              }
            }
          }

          // Latch shadow waveform registers on period-end
          when(periodComplete) {
            periodLive := s.period
            risingEdgeLive := s.risingEdge
            fallingEdgeLive := s.fallingEdge
          }

          // N-shot: decrement on period-end, freeze when last shot completes
          when(periodComplete && s.shotCount =/= 0) {
            when(shotRemaining > 1) {
              shotRemaining := shotRemaining - 1
            } otherwise {
              shotDoneReg := True
            }
          }

          io.irqPeriodComplete.valid(i) := periodComplete

          // Dead-time counter
          when(transition) {
            deadTimeCounter := s.deadTime
          } elsewhen (deadTimeCounter =/= 0 && tick) {
            deadTimeCounter := deadTimeCounter - 1
          }

          // Primary and complementary outputs with dead-time
          io.pwm.output(i) := Mux(desired && !inDeadTime, !s.invert, s.invert)
          io.pwm.compOutput(i) := Mux(!desired && !inDeadTime, !s.invert, s.invert)
        } otherwise {
          periodCounter := 0
          direction := False
          deadTimeCounter := 0
        }

        // syncIn: reset all enabled channel counters to their phase offset
        when(s.enable && io.pwm.syncIn) {
          periodCounter := s.phaseOffset
          direction := False
        }

        // faultIn: force all outputs to safe idle state (highest priority)
        when(io.pwm.faultIn) {
          io.pwm.output(i) := s.invert
          io.pwm.compOutput(i) := s.invert
        }
      }.setName(s"ch$i")
    }

    val faultInPrev = RegNext(io.pwm.faultIn, False)
    io.faultError := io.pwm.faultIn && !faultInPrev

    io.interrupt := io.irqPeriodComplete.pending.orR
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Pwm, 1, 1, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length

    // Static word 1: implementation widths and channel count
    busCtrl.read(
      B(p.channelPeriodWidth, 8 bits) ## B(p.channelPulseWidth, 8 bits) ##
        B(p.clockDividerWidth, 8 bits) ## B(p.io.channels, 8 bits),
      staticOffset
    )

    // Static word 2: dead-time and shot-count widths
    busCtrl.read(
      B(0, 16 bits) ## B(p.shotCountWidth, 8 bits) ## B(p.deadTimeWidth, 8 bits),
      staticOffset + 0x4
    )

    if (p.permission != null) {
      val permissionBits = Bool(p.permission.busCanWriteClockDividerConfig)
      busCtrl.read(B(0, 32 - 1 bits) ## permissionBits, staticOffset + 0x8)
    } else {
      busCtrl.read(B(0), staticOffset + 0x8)
    }

    val regOffset = staticOffset + 0xc

    val irqPeriodCompleteCtrl = new InterruptCtrl(p.io.channels)
    irqPeriodCompleteCtrl.driveFrom(busCtrl, regOffset + 0x00)
    for (i <- 0 until p.io.channels) {
      irqPeriodCompleteCtrl.io.inputs(i) := ctrl.irqPeriodComplete.valid(i)
      ctrl.irqPeriodComplete.pending(i) := irqPeriodCompleteCtrl.io.pendings(i)
    }

    // Channel shadow registers (shared with error module for configError detection)
    val channelCfg = Reg(ctrl.shadow)

    // Error module: faultIn rising edge (input 0) + per-channel configError (inputs 1..N)
    val errorCtrl = new InterruptCtrl(1 + p.io.channels)
    errorCtrl.driveFrom(busCtrl, regOffset + 0x08)
    errorCtrl.io.inputs(0) := ctrl.faultError
    for (i <- 0 until p.io.channels) {
      errorCtrl.io.inputs(1 + i) :=
        channelCfg(i).fallingEdge.resize(p.channelPeriodWidth) > channelCfg(i).period
    }
    ctrl.errorPending := errorCtrl.io.pendings.orR

    // Channel registers: stride 0x28 (40 bytes), starting at regOffset + 0x14
    for (i <- 0 until p.io.channels) {
      val channel = regOffset + 0x10 + i * 0x28

      channelCfg(i).enable.init(False)
      channelCfg(i).invert.init(False)
      channelCfg(i).mode.init(False)
      channelCfg(i).period.init(0)
      channelCfg(i).risingEdge.init(0)
      channelCfg(i).fallingEdge.init(0)
      channelCfg(i).deadTime.init(0)
      channelCfg(i).phaseOffset.init(0)
      channelCfg(i).shotCount.init(0)

      if (p.init != null && p.init.clockDivider != 0)
        channelCfg(i).clockDivider.init(p.init.clockDivider)
      else
        channelCfg(i).clockDivider.init(0)

      busCtrl.readAndWrite(channelCfg(i).enable, address = channel + 0x00, bitOffset = 0)
      busCtrl.readAndWrite(channelCfg(i).invert, address = channel + 0x00, bitOffset = 1)
      busCtrl.readAndWrite(channelCfg(i).mode, address = channel + 0x00, bitOffset = 2)

      if (p.permission != null && p.permission.busCanWriteClockDividerConfig)
        busCtrl.readAndWrite(channelCfg(i).clockDivider, address = channel + 0x04)
      else {
        channelCfg(i).clockDivider.allowUnsetRegToAvoidLatch
        busCtrl.read(channelCfg(i).clockDivider, address = channel + 0x04)
      }

      busCtrl.readAndWrite(channelCfg(i).period, address = channel + 0x08)
      busCtrl.readAndWrite(channelCfg(i).risingEdge, address = channel + 0x0c)
      busCtrl.readAndWrite(channelCfg(i).fallingEdge, address = channel + 0x10)
      busCtrl.readAndWrite(channelCfg(i).deadTime, address = channel + 0x14)
      busCtrl.readAndWrite(channelCfg(i).phaseOffset, address = channel + 0x18)
      busCtrl.readAndWrite(channelCfg(i).shotCount, address = channel + 0x1c)

      // Status register (read-only): configError[0], shotDone[1]
      val configError =
        channelCfg(i).fallingEdge.resize(p.channelPeriodWidth) > channelCfg(i).period
      busCtrl.read(
        B(0, 30 bits) ## ctrl.shotDone(i) ## configError,
        channel + 0x20
      )
    }
    ctrl.shadow := channelCfg
  }
}
