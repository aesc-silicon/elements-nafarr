package nafarr.peripherals.io.pwm

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory

object PwmCtrl {
  def apply(p: Parameter) = PwmCtrl(p)

  case class InitParameter(clockDivider: Int) {}
  object InitParameter {
    def default = InitParameter(0)
  }

  case class PermissionParameter(busCanWriteClockDividerConfig: Boolean) {}
  object PermissionParameter {
    def default = PermissionParameter(true)
  }

  case class Parameter(
      io: Pwm.Parameter,
      channels: Int,
      permission: PermissionParameter = PermissionParameter.default,
      init: InitParameter = InitParameter.default,
      clockDividerWidth: Int = 20,
      channelPeriodWidth: Int = 20,
      channelPulseWidth: Int = 20
  ) {
    require(channels > 0, "At least one channel is required")
    require(channelPeriodWidth >= channelPulseWidth, "Channel pulse cannot be wider than period.")
  }
  object Parameter {
    def default(channels: Int) = Parameter(Pwm.Parameter(channels), channels)
  }

  case class Config(p: Parameter) extends Bundle {
    val clockDivider = UInt(p.clockDividerWidth bits)
  }
  case class ConfigChannel(p: Parameter) extends Bundle {
    val enable = Bool()
    val invert = Bool()
    val period = UInt(p.channelPeriodWidth bits)
    val pulse = UInt(p.channelPulseWidth bits)
  }

  case class Io(p: Parameter) extends Bundle {
    val pwm = Pwm.Io(p.io)
    val config = in(Config(p))
    val channels = in(Vec(ConfigChannel(p), p.channels))
  }

  case class PwmCtrl(p: Parameter) extends Component {
    val io = Io(p)

    val clockDivider = new Area {
      val counter = Reg(UInt(p.clockDividerWidth bits)).init(0)
      val tick = counter === 0

      counter := counter - 1
      when(tick) {
        counter := io.config.clockDivider
      }
    }

    for (i <- 0 until p.channels) {
      val channel = new Area {
        val periodCounter = Reg(UInt(p.channelPeriodWidth bits)).init(0)
        val pulseCounter = Reg(UInt(p.channelPulseWidth bits)).init(0)
        val lock = RegInit(True)
        def tick = !lock && clockDivider.tick

        io.pwm.output(i) := io.channels(i).invert
        when(io.channels(i).enable) {
          // Start output after first tick
          when(clockDivider.tick) {
            lock := False
          }
          when(tick) {
            periodCounter := periodCounter - 1
          }
          when(periodCounter === 0 && tick) {
            periodCounter := io.channels(i).period
            pulseCounter := io.channels(i).pulse
          }
          when(tick && pulseCounter =/= U(0)) {
            pulseCounter := pulseCounter - 1
          }
          when(pulseCounter =/= U(0) && !lock) {
            io.pwm.output(i) := !io.channels(i).invert
          }
        } otherwise {
          lock := True
          periodCounter := io.channels(i).period
          pulseCounter := io.channels(i).pulse
          io.pwm.output(i) := io.channels(i).invert
        }
      }
    }
  }

  /** Register mapping
    *
    * 0x0000|RW: Clock divier value to slow down the input clock for all channels
    * .
    * For each channel:
    * 0xxxx0|RW: Control register for channel X.
    * 0xxxx4|RW: Period timer for channel X.
    * 0xxxx8|RW: Pulse timer for channel X.
    */
  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val config = new Area {
      val cfg = Reg(ctrl.config)

      if (p.init != null && p.init.clockDivider != 0)
        cfg.clockDivider.init(p.init.clockDivider)
      else
        cfg.clockDivider.init(0)

      if (p.permission.busCanWriteClockDividerConfig)
        busCtrl.writeMultiWord(cfg.clockDivider, address = 0x0)
      else
        cfg.allowUnsetRegToAvoidLatch
      busCtrl.readMultiWord(cfg.clockDivider, address = 0x0)

      ctrl.config <> cfg
    }

    val channelCfg = Reg(ctrl.channels)
    for (i <- 0 until p.channels) {
      val offset = (i + 1) * 0x10

      channelCfg(i).enable.init(False)
      channelCfg(i).invert.init(False)
      channelCfg(i).period.init(0)
      channelCfg(i).pulse.init(0)

      busCtrl.readAndWrite(channelCfg(i).enable, address = 0x0 + offset, bitOffset = 0x0)
      busCtrl.readAndWrite(channelCfg(i).invert, address = 0x0 + offset, bitOffset = 0x1)
      busCtrl.readAndWrite(channelCfg(i).period, address = 0x4 + offset)
      busCtrl.readAndWrite(channelCfg(i).pulse, address = 0x8 + offset)
    }
    ctrl.channels := channelCfg
  }
}
