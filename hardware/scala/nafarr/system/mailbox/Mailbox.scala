// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.mailbox

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  SlaveFactory => TileLinkSlaveFactory
}
import spinal.lib.bus.wishbone._

object Mailbox {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: MailboxCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out(Bool())
    }
    val busCtrl = factory(io.bus)
    val ctrl = MailboxCtrl(p)
    val mapper = MailboxCtrl.Mapper(busCtrl, ctrl, p)
    io.interrupt := ctrl.io.interrupt
  }
}

case class Apb3Mailbox(
    p: MailboxCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(8, 32)
) extends Mailbox.Core[Apb3](
      p,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkMailbox(
    p: MailboxCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(8, 32, 4, 1)
) extends Mailbox.Core[TileLinkBus](
      p,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneMailbox(
    p: MailboxCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(8, 32)
) extends Mailbox.Core[Wishbone](
      p,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    )
