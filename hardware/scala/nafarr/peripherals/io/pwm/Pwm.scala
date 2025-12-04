// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.pwm

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._

object Pwm {
  case class Parameter(channels: Int) {
    require(channels > 0, "At least one channel is required.")
  }

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

    def headerBareMetal(
        name: String,
        address: BigInt,
        size: BigInt,
        irqNumber: Option[Int] = null
    ) = {
      val baseAddress = "%08x".format(address.toInt)
      val regSize = "%04x".format(size.toInt)
      var dt = s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
      dt
    }
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
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Pwm.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
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
