package nafarr.peripherals.io.pwm

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import nafarr.IpIdentification
import nafarr.library.ClockDivider

object PwmCtrl {
  def apply(p: Parameter = Parameter.default()) = PwmCtrl(p)

  case class InitParameter(clockDivider: Int) {}
  object InitParameter {
    def disabled() = InitParameter(0)
  }

  case class PermissionParameter(busCanWriteClockDividerConfig: Boolean) {}
  object PermissionParameter {
    def granted() = PermissionParameter(true)
  }

  case class Parameter(
      io: Pwm.Parameter,
      init: InitParameter = InitParameter.disabled(),
      permission: PermissionParameter = PermissionParameter.granted(),
      clockDividerWidth: Int = 20,
      channelPeriodWidth: Int = 20,
      channelPulseWidth: Int = 20
  ) {
    require(channelPeriodWidth >= channelPulseWidth, "Channel pulse cannot be wider than period.")
  }
  object Parameter {
    def default(channels: Int = 1) = Parameter(Pwm.Parameter(channels))
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
    val channels = in(Vec(ConfigChannel(p), p.io.channels))
  }

  case class PwmCtrl(p: Parameter) extends Component {
    val io = Io(p)

    val clockDivider = new ClockDivider(p.clockDividerWidth)
    clockDivider.io.value := io.config.clockDivider
    clockDivider.io.reload := False

    for (i <- 0 until p.io.channels) {
      val channel = new Area {
        val periodCounter = Reg(UInt(p.channelPeriodWidth bits)).init(0)
        val pulseCounter = Reg(UInt(p.channelPulseWidth bits)).init(0)
        def tick = clockDivider.io.tick

        io.pwm.output(i) := io.channels(i).invert
        when(io.channels(i).enable) {
          when(periodCounter === 0) {
            periodCounter := io.channels(i).period
            pulseCounter := io.channels(i).pulse
          }
          when(tick) {
            periodCounter := periodCounter - 1
            when(pulseCounter =/= U(0)) {
              pulseCounter := pulseCounter - 1
            }
          }
          when(pulseCounter =/= U(0)) {
            io.pwm.output(i) := !io.channels(i).invert
          }
        } otherwise {
          periodCounter := io.channels(i).period
          pulseCounter := io.channels(i).pulse
        }
      }
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Pwm, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val offset = idCtrl.length

    busCtrl.read(
      B(p.channelPeriodWidth, 8 bits) ## B(p.channelPulseWidth, 8 bits) ##
        B(p.clockDividerWidth, 8 bits) ## B(p.io.channels, 8 bits),
      offset
    )

    val config = new Area {
      val cfg = Reg(ctrl.config)

      if (p.init != null && p.init.clockDivider != 0)
        cfg.clockDivider.init(p.init.clockDivider)
      else
        cfg.clockDivider.init(0)

      if (p.permission != null && p.permission.busCanWriteClockDividerConfig)
        busCtrl.write(cfg.clockDivider, address = offset + 0x04)
      else
        cfg.allowUnsetRegToAvoidLatch
      busCtrl.read(cfg.clockDivider, address = offset + 0x04)

      ctrl.config <> cfg
    }

    val channelCfg = Reg(ctrl.channels)
    for (i <- 0 until p.io.channels) {
      val channel = offset + 0x8 + i * 0x10

      channelCfg(i).enable.init(False)
      channelCfg(i).invert.init(False)
      channelCfg(i).period.init(0)
      channelCfg(i).pulse.init(0)

      busCtrl.readAndWrite(channelCfg(i).enable, address = channel + 0x0, bitOffset = 0x0)
      busCtrl.readAndWrite(channelCfg(i).invert, address = channel + 0x0, bitOffset = 0x1)
      busCtrl.readAndWrite(channelCfg(i).period, address = channel + 0x4)
      busCtrl.readAndWrite(channelCfg(i).pulse, address = channel + 0x8)
    }
    ctrl.channels := channelCfg
  }
}
