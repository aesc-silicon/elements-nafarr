package nafarr.peripherals.io.pio

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._
import spinal.lib.io.{TriStateArray, TriState}

object Pio {
  case class Parameter(width: Int) {
    require(width > 0, "At least one pin is required.")
  }

  case class Io(parameter: Parameter) extends Bundle {
    val pins = master(TriStateArray(parameter.width bits))
  }

  class Core[T <: spinal.core.Data with IMasterSlave](
      parameter: PioCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val pio = Io(parameter.io)
    }

    val ctrl = PioCtrl(parameter)
    ctrl.io.pio <> io.pio

    val mapper = PioCtrl.Mapper(factory(io.bus), ctrl.io, parameter)

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

case class Apb3Pio(
    parameter: PioCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Pio.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    ) { val dummy = 0 }

case class WishbonePio(
    parameter: PioCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(10, 32)
) extends Pio.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 10)),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMPio(
    parameter: PioCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends Pio.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
