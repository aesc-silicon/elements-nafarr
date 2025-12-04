package nafarr.peripherals.pinmux

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._
import spinal.lib.io.{TriStateArray, TriState}

import scala.collection.mutable.ArrayBuffer

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
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val pins = Io(p.io)
      val inputs = slave(TriStateArray(p.inputs bits))
    }
    val ctrl = PinmuxCtrl(p, mapping)
    ctrl.io.pins <> io.pins.pins
    ctrl.io.inputs <> io.inputs
    val mapper = PinmuxCtrl.Mapper(factory(io.bus), ctrl.io, p)

    def headerBareMetal(
        name: String,
        address: BigInt,
        size: BigInt,
        irqNumber: Option[Int] = null
    ) = {
      val baseAddress = "%08x".format(address.toInt)
      val regSize = "%04x".format(size.toInt)
      var dt = s"""#define ${name.toUpperCase}_BASE\t\t0x${baseAddress}\n"""
      dt
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
    ) { val dummy = 0 }

case class WishbonePinmux(
    parameter: PinmuxCtrl.Parameter,
    mapping: ArrayBuffer[(Int, List[Int])],
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Pinmux.Core[Wishbone](
      parameter,
      mapping,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMPinmux(
    parameter: PinmuxCtrl.Parameter,
    mapping: ArrayBuffer[(Int, List[Int])],
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends Pinmux.Core[AvalonMM](
      parameter,
      mapping,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
