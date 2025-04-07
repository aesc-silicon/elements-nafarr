package nafarr.crypto.aes

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._

object AesMaskedAccelerator {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: AesMaskedAcceleratorCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
    }
    val ctrl = AesMaskedAcceleratorCtrl(p)
    val mapper = AesMaskedAcceleratorCtrl.Mapper(factory(io.bus), ctrl.io, p)

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

case class Apb3AesMaskedAccelerator(
    parameter: AesMaskedAcceleratorCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends AesMaskedAccelerator.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    ) { val dummy = 0 }

case class WishboneAesMaskedAccelerator(
    parameter: AesMaskedAcceleratorCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(12, 32)
) extends AesMaskedAccelerator.Core[Wishbone](
      parameter,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMAesMaskedAccelerator(
    parameter: AesMaskedAcceleratorCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(12, 32, 1)
) extends AesMaskedAccelerator.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
