// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.esm

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

object Esm {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: EsmCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val inputs = in(Bits(p.inputCount bits))
      val infoInterrupt = out Bool ()
      val warnInterrupt = out Bool ()
      val errorSignal = out Bool ()
    }
    val busCtrl = factory(io.bus)
    val ctrl = EsmCtrl(p)
    val mapper = EsmCtrl.Mapper(busCtrl, ctrl, p)
    ctrl.io.inputs := io.inputs
    io.infoInterrupt := ctrl.io.infoInterrupt
    io.warnInterrupt := ctrl.io.warnInterrupt
    io.errorSignal := ctrl.io.errorSignal

    override def sysconFeatures = Some(List(Feature.Esm))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Esm(
    p: EsmCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Esm.Core[Apb3](
      p,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkEsm(
    p: EsmCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 4, 1)
) extends Esm.Core[TileLinkBus](
      p,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishboneEsm(
    p: EsmCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(12, 32)
) extends Esm.Core[Wishbone](
      p,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    )
