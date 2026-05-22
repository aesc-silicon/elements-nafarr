// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.plic

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
import spinal.lib.misc.plic._

import scala.collection.mutable.ArrayBuffer

object Plic {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: PlicCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out(Bool)
      val sources = in(Bits(p.sources bits))
    }

    val gateways = ArrayBuffer[PlicGateway]()
    for (i <- 0 until p.sources) {
      gateways += PlicGatewayActiveHigh(
        source = io.sources(i),
        id = i + 1,
        priorityWidth = p.priorityWidth
      )
    }
    gateways.foreach(_.priority := 1)

    val targets = Seq(
      PlicTarget(
        id = 0,
        gateways = gateways,
        priorityWidth = p.priorityWidth
      )
    )
    targets.foreach(_.threshold := 0)
    val mapping = PlicMapper(factory(io.bus), PlicMapping.sifive)(
      gateways = gateways,
      targets = targets
    )

    io.interrupt := targets(0).iep

    override def sysconFeatures = Some(List(Feature.Plic))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Plic(
    parameter: PlicCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(22, 32)
) extends Plic.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkPlic(
    parameter: PlicCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(22, 32, 32, 4)
) extends Plic.Core[TileLinkBus](
      parameter,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishbonePlic(
    parameter: PlicCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(20, 32)
) extends Plic.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 20)),
      WishboneSlaveFactory(_)
    )
