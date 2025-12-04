package nafarr.system.reset

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
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
    val staticOffset = idCtrl.length

    busCtrl.read(B(p.domains.length, 8 bits), staticOffset)
    val regOffset = idCtrl.length + 0x4

    busCtrl
      .driveAndRead(io.config.enable, regOffset + 0x0)
      .init(U((0 until p.domains.length) -> true))
    busCtrl.readAndWrite(trigger, regOffset + 0x4)
    busCtrl.onWrite(0x8)(acknowledge := True)

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
    ) { val dummy = 0 }

case class WishboneResetController(
    parameter: ResetControllerCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends ResetController.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMResetController(
    parameter: ResetControllerCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends ResetController.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
