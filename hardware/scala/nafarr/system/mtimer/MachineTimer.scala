// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.mtimer

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

object MachineTimer {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: MachineTimerCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out(Bool)
    }

    val ctrl = MachineTimerCtrl(p)
    io.interrupt := ctrl.io.interrupt

    val mapper = MachineTimerCtrl.Mapper(factory(io.bus), ctrl.io, p)

    val clockSpeed = ClockDomain.current.frequency.getValue.toInt
    def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      val regSize = "%04x".format(size.toInt)
      var dt = s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}
#define ${name.toUpperCase}_FREQ\t\t${clockSpeed}
"""
      dt
    }
  }
}

case class Apb3MachineTimer(
    parameter: MachineTimerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends MachineTimer.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkMachineTimer(
    parameter: MachineTimerCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends MachineTimer.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneMachineTimer(
    parameter: MachineTimerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends MachineTimer.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
