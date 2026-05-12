// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.clock

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

case class ClockParameter(
    name: String,
    frequency: HertzNumber,
    reset: String = "",
    resetConfig: ClockDomainConfig =
      ClockDomainConfig(resetKind = spinal.core.SYNC, resetActiveLevel = LOW),
    synchronousWith: String = ""
)

object ClockController {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: ClockControllerCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val config = out(ClockControllerCtrl.Config(p))
    }
    val busCtrl = factory(io.bus)

    busCtrl.driveAndRead(io.config.enable, 0x0).init(U((0 until p.domains.length) -> true))
  }
}

case class Apb3ClockController(
    parameter: ClockControllerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends ClockController.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkClockController(
    parameter: ClockControllerCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends ClockController.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneClockController(
    parameter: ClockControllerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends ClockController.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
