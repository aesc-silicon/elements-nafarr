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

import nafarr.Feature
import nafarr.peripherals.PeripheralsComponent
import nafarr.system.dma.DmaRequest

object Mailbox {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: MailboxCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out(Bool())
      val dmaRequest = Vec.fill(p.channelCount)(master(DmaRequest()))
    }
    val busCtrl = factory(io.bus)
    val ctrl = MailboxCtrl(p)
    val mapper = MailboxCtrl.Mapper(busCtrl, ctrl, p)
    io.interrupt := ctrl.io.interrupt
    for (ch <- 0 until p.channelCount) {
      io.dmaRequest(ch).drive(ctrl.io.push(ch).ready, ctrl.io.pop(ch).valid)
    }

    override def getInterrupt = Some(io.interrupt)
    override def getDmaRequests = io.dmaRequest.toSeq
    override def sysconFeatures = Some(List(Feature.Mailbox))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
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
