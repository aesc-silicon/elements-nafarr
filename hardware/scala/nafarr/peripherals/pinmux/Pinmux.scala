// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.pinmux

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
import spinal.lib.io.{TriStateArray, TriState}

import scala.collection.mutable.ArrayBuffer
import nafarr.Feature
import nafarr.peripherals.PeripheralsComponent

object Pinmux {
  case class Parameter(width: Int) {
    require(width > 0, "At least one pin is required.")
  }

  case class Io(p: Parameter) extends Bundle {
    val pins = master(TriStateArray(p.width bits))
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: PinmuxCtrl.Parameter,
      mapping: ArrayBuffer[(Int, List[Int])],
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends PeripheralsComponent {
    val io = new Bundle {
      val bus = slave(busType())
      val pins = Io(p.io)
      val inputs = slave(TriStateArray(p.inputs bits))
    }
    val ctrl = PinmuxCtrl(p, mapping)
    ctrl.io.pins <> io.pins.pins
    ctrl.io.inputs <> io.inputs
    val mapper = PinmuxCtrl.Mapper(factory(io.bus), ctrl.io, p)

    override def sysconFeatures = Some(List(Feature.Pinmux))

    override def headerBareMetal(name: String, address: BigInt, size: BigInt) = {
      val baseAddress = "%08x".format(address.toInt)
      s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
    }
  }
}

case class Apb3Pinmux(
    parameter: PinmuxCtrl.Parameter,
    mapping: ArrayBuffer[(Int, List[Int])],
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Pinmux.Core[Apb3](
      parameter,
      mapping,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class TileLinkPinmux(
    parameter: PinmuxCtrl.Parameter,
    mapping: ArrayBuffer[(Int, List[Int])],
    busConfig: TileLinkParameter = TileLinkParameter.simple(12, 32, 32, 4)
) extends Pinmux.Core[TileLinkBus](
      parameter,
      mapping,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class WishbonePinmux(
    parameter: PinmuxCtrl.Parameter,
    mapping: ArrayBuffer[(Int, List[Int])],
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Pinmux.Core[Wishbone](
      parameter,
      mapping,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    )
