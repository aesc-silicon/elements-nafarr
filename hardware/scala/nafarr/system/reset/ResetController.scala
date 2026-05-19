// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.reset

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

import nafarr.IpIdentification

case class ResetParameter(name: String, delay: Int)

object ResetController {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: ResetControllerCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val config = out(ResetControllerCtrl.Config(p))
    }
    val busCtrl = factory(io.bus)
    val trigger = Reg(io.config.trigger).init(0)
    val acknowledge = False
    when(acknowledge) {
      trigger := 0
    }

    val idCtrl = IpIdentification(IpIdentification.Ids.Reset, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = ResetControllerCtrl.Regs(idCtrl.length)

    busCtrl.read(B(p.domains.length, 8 bits), regs.domains)

    busCtrl
      .driveAndRead(io.config.enable, regs.enable)
      .init(U((0 until p.domains.length) -> true))
    busCtrl.readAndWrite(trigger, regs.trigger)
    busCtrl.onWrite(regs.acknowledge)(acknowledge := True)

    io.config.trigger := trigger
    io.config.acknowledge := acknowledge
  }
}

case class Apb3ResetController(
    parameter: ResetControllerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends ResetController.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkResetController(
    parameter: ResetControllerCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends ResetController.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneResetController(
    parameter: ResetControllerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends ResetController.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
