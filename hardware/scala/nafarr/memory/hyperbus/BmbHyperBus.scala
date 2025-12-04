package nafarr.memory.hyperbus

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.bmb._
import spinal.lib.bus.wishbone._

object BmbHyperBus {
  def apply(p: HyperBusCtrl.Parameter, dataBusConfig: BmbParameter, cfgBusConfig: WishboneConfig) =
    BmbHyperBus(p, dataBusConfig, cfgBusConfig)

  case class BmbData(p: BmbParameter) extends Bundle {
    val source = UInt(p.access.sourceWidth bits)
    val context = Bits(p.access.contextWidth bits)
  }

  case class BmbHyperBus(
      p: HyperBusCtrl.Parameter,
      dataBusConfig: BmbParameter,
      cfgBusConfig: WishboneConfig
  ) extends Component {
    val io = new Bundle {
      val dataBus = slave(Bmb(dataBusConfig))
      val cfgBus = slave(Wishbone(cfgBusConfig.copy(addressWidth = 10)))
      val phy = master(HyperBus.Phy.Interface(p))
    }

    val hyperbus = WishboneHyperBus(p, cfgBusConfig)
    hyperbus.io.bus <> io.cfgBus
    io.phy <> hyperbus.io.phy

    val bmbDataStorage = StreamFifo(BmbData(dataBusConfig), p.frontend.storageDepth)

    val incomingScheduler = new Area {
      val id = Reg(UInt(p.frontend.idLength bits)) init (0)
      when(io.dataBus.cmd.fire) {
        id := id + 1
      }

      def getStrobe(size: UInt): Bits = size.mux(
        0 -> B"0001",
        1 -> B"0011",
        default -> B"1111"
      )

      io.dataBus.cmd.ready := False

      hyperbus.io.controller.valid := False
      hyperbus.io.controller.payload.id := id
      hyperbus.io.controller.payload.read := io.dataBus.cmd.isRead
      hyperbus.io.controller.payload.unaligned := io.dataBus.cmd.address(0)
      hyperbus.io.controller.payload.memory := True
      hyperbus.io.controller.payload.addr := io.dataBus.cmd
        .address(dataBusConfig.access.addressWidth - 1 downto 1)
        .resized
      hyperbus.io.controller.payload.data := io.dataBus.cmd.data
      hyperbus.io.controller.payload.strobe := getStrobe(io.dataBus.cmd.length)
      hyperbus.io.controller.payload.last := True

      bmbDataStorage.io.push.valid := False
      bmbDataStorage.io.push.payload.source := io.dataBus.cmd.source
      bmbDataStorage.io.push.payload.context := io.dataBus.cmd.context

      when(io.dataBus.cmd.valid && bmbDataStorage.io.push.ready) {
        hyperbus.io.controller.valid := True
        when(hyperbus.io.controller.fire) {
          bmbDataStorage.io.push.valid := True
          io.dataBus.cmd.ready := True
        }
      }
    }

    val outgoing = new Area {
      io.dataBus.rsp.valid := hyperbus.io.frontend.valid
      io.dataBus.rsp.source := bmbDataStorage.io.pop.source
      io.dataBus.rsp.context := bmbDataStorage.io.pop.context
      io.dataBus.rsp.data := hyperbus.io.frontend.payload.data
      io.dataBus.rsp.setSuccess()
      io.dataBus.rsp.last := hyperbus.io.frontend.payload.last
      hyperbus.io.frontend.ready := io.dataBus.rsp.fire
      bmbDataStorage.io.pop.ready := io.dataBus.rsp.fire
    }
  }
}
