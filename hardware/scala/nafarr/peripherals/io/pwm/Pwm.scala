// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.pwm

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  SlaveFactory => TileLinkSlaveFactory
}
import spinal.lib.bus.wishbone._
import nafarr.Feature
import nafarr.peripherals.PeripheralsComponent

object Pwm {
  case class Parameter(channels: Int) {
    require(channels > 0, "At least one channel is required.")
  }

  case class Io(p: Parameter) extends Bundle {
    val output = out(Bits(p.channels bits))
    val compOutput = out(Bits(p.channels bits))
    val syncOut = out(Bits(p.channels bits))
    val syncIn = in(Bool)
    val faultIn = in(Bool)
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: PwmCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val pwm = Io(p.io)
      val interrupt = out(Bool)
      val error = out(Bool)
    }

    val ctrl = PwmCtrl(p)
    ctrl.io.pwm <> io.pwm
    io.interrupt := ctrl.io.interrupt
    io.error := ctrl.io.error

    val mapper = PwmCtrl.Mapper(factory(io.bus), ctrl.io, p)

    override def getInterrupt = Some(io.interrupt)
    override def getError = Some(io.error)
    override def sysconFeatures = Some(List(Feature.Pwm))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
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
    )

case class TileLinkPwm(
    parameter: PwmCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends Pwm.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishbonePwm(
    parameter: PwmCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Pwm.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
