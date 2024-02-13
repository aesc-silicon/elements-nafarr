package nafarr.peripherals.io.pwm

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._

object Pwm {
  case class Parameter(channels: Int) {}

  case class Io(p: Parameter) extends Bundle {
    val output = out(Bits(p.channels bits))
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: PwmCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val pwm = Io(p.io)
    }

    val ctrl = PwmCtrl(p)
    ctrl.io.pwm <> io.pwm

    val mapper = PwmCtrl.Mapper(factory(io.bus), ctrl.io, p)
  }
}

case class Apb3Pwm(
    parameter: PwmCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Pwm.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    ) { val dummy = 0 }

case class WishbonePwm(
    parameter: PwmCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(12, 32)
) extends Pwm.Core[Wishbone](
      parameter,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMPwm(
    parameter: PwmCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends Pwm.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
