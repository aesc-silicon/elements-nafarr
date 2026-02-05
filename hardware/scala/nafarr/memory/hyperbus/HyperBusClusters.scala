// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb._
import spinal.lib.bus.wishbone._
import nafarr.memory.hyperbus.phy._

object BmbHyperBusGenericPhyCluster {
  def apply(p: HyperBusCtrl.Parameter, dataBusConfig: BmbParameter, cfgBusConfig: WishboneConfig) =
    BmbHyperBusGenericPhyCluster(p, dataBusConfig, cfgBusConfig)

  case class BmbHyperBusGenericPhyCluster(
      p: HyperBusCtrl.Parameter,
      dataBusConfig: BmbParameter,
      cfgBusConfig: WishboneConfig
  ) extends Component {
    val io = new Bundle {
      val dataBus = slave(Bmb(dataBusConfig))
      val cfgBus = slave(Wishbone(cfgBusConfig.copy(addressWidth = 10)))
      val hyperbus = master(HyperBus.Io(p))
    }
    val ctrl = BmbHyperBus(p, dataBusConfig, cfgBusConfig)
    val phy = HyperBusGenericPhy(p)
    ctrl.io.dataBus <> io.dataBus
    ctrl.io.cfgBus <> io.cfgBus
    ctrl.io.phy <> phy.io.phy
    io.hyperbus <> phy.io.hyperbus
  }
}
