package nafarr.peripherals.com.chip2chip

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import nafarr.peripherals.com.chip2chip.phy._

object Interface {

  case class Axi4Interface(config: Axi4Config, ioPins: Int = 16) extends Component {
    val io = new Bundle {
      val axiIn = slave(Axi4(config))
      val axiOut = master(Axi4(config))
      val txPhy = master(VirtualPhy.Io(ioPins))
      val rxPhy = slave(VirtualPhy.Io(ioPins))
    }

    val frontend = Frontend.Axi4Frontend(config)
    io.axiIn <> frontend.io.axiIn
    io.axiOut <> frontend.io.axiOut

    val txPhy = VirtualPhy.Tx(ioPins)
    io.txPhy <> txPhy.io.phy
    val rxPhy = VirtualPhy.Rx(ioPins)
    io.rxPhy <> rxPhy.io.phy

    val linkLayer = LinkLayer.LinkLayer(2, 2, ioPins, ioPins)
    frontend.io.toLinkLayer <> linkLayer.io.fromFrontend
    linkLayer.io.toPhy <> txPhy.io.fromLinkLayer
    rxPhy.io.fromPhy <> linkLayer.io.toLinkLayer
    linkLayer.io.toFrontend <> frontend.io.fromLinkLayer
  }
}
