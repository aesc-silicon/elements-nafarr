package nafarr.peripherals.com.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.avalon._
import spinal.lib.bus.wishbone._

object SpiXipController {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: SpiCtrl.Parameter,
      dataBusConfig: Axi4Config,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      val dataBus = slave(Axi4ReadOnly(dataBusConfig))
      val spi = master(Spi.Io(p.io))
      val interrupt = out(Bool)
    }

    val spiControllerCtrl = SpiControllerCtrl(p)
    spiControllerCtrl.io.spi <> io.spi
    io.interrupt := False

    val spiXipControllerCtrl = SpiXipControllerCtrl(p, dataBusConfig)
    spiControllerCtrl.io.cmd << spiXipControllerCtrl.io.cmd
    spiXipControllerCtrl.io.rsp << spiControllerCtrl.io.rsp
    spiXipControllerCtrl.io.bus << io.dataBus

    val busFactory = factory(io.bus)
    SpiControllerCtrl.Mapper(busFactory, spiControllerCtrl.io, p)
  }
}

case class Axi4ReadOnlySpiXipController(
    parameter: SpiCtrl.Parameter,
    dataBusConfig: Axi4Config = Axi4Config(20, 32, 4),
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends SpiXipController.Core[Apb3](
      parameter,
      dataBusConfig,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    ) { val dummy = 0 }
