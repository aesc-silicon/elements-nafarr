package nafarr.system.plic

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._
import spinal.lib.misc.plic._

import scala.collection.mutable.ArrayBuffer

object Plic {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: PlicCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val interrupt = out(Bool)
      val sources = in(Bits(p.sources bits))
    }

    val gateways = ArrayBuffer[PlicGateway]()
    for (i <- 0 until p.sources) {
      gateways += PlicGatewayActiveHigh(
        source = io.sources(i),
        id = i,
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

case class Apb3Plic(
    parameter: PlicCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(22, 32)
) extends Plic.Core[Apb3](
      parameter,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    ) { val dummy = 0 }

case class WishbonePlic(
    parameter: PlicCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(20, 32)
) extends Plic.Core[Wishbone](
      parameter,
      Wishbone(busConfig.copy(addressWidth = 20)),
      WishboneSlaveFactory(_)
    ) { val dummy = 0 }

case class AvalonMMPlic(
    parameter: PlicCtrl.Parameter,
    busConfig: AvalonMMConfig = AvalonMMConfig.fixed(22, 32, 1)
) extends Plic.Core[AvalonMM](
      parameter,
      AvalonMM(busConfig),
      AvalonMMSlaveFactory(_)
    ) { val dummy = 0 }
