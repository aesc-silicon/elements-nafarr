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

import nafarr.{Feature, IpIdentification}
import nafarr.peripherals.PeripheralsComponent

case class ClockParameter(
    name: String,
    frequency: HertzNumber,
    reset: String = "",
    resetConfig: ClockDomainConfig =
      ClockDomainConfig(resetKind = spinal.core.SYNC, resetActiveLevel = LOW),
    synchronousWith: String = "",
    gateable: Boolean = true
)

object ClockController {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: ClockControllerCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val config = out(ClockControllerCtrl.Config(p))
    }
    val busCtrl = factory(io.bus)

    val idCtrl = IpIdentification(IpIdentification.Ids.Clock, 1, 1, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = ClockControllerCtrl.Regs(idCtrl.length)

    busCtrl.read(B(p.domains.length, 8 bits), regs.domains)

    for ((domain, index) <- p.domains.zipWithIndex) {
      val domainArea = new Area {
        val (mult, div) = p.ratioOf(domain)

        /* CTRL: enable at bit 31 (only writable bit), lock status at bit 30. */
        if (domain.gateable) {
          busCtrl.driveAndRead(io.config.enable(index), regs.control(index), 31).init(True)
        } else {
          io.config.enable(index) := True
          busCtrl.read(True, regs.control(index), 31)
        }
        busCtrl.read(True, regs.control(index), 30)

        /* RATIO: rate = reference * MULT / DIV. */
        busCtrl.read(B(div, 16 bits), regs.ratio(index), 0)
        busCtrl.read(B(mult, 16 bits), regs.ratio(index), 16)
      }.setName(s"domain_${domain.name}")
    }

    override def sysconFeatures = Some(List(Feature.Clock))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
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
