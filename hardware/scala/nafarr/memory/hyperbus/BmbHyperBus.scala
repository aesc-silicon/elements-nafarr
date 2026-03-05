// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

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
    val size = UInt(p.access.lengthWidth bits)
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

      val address = Reg(UInt(dataBusConfig.access.addressWidth bits))

      hyperbus.io.controller.valid := False
      hyperbus.io.controller.payload.id := id
      hyperbus.io.controller.payload.read := io.dataBus.cmd.isRead
      hyperbus.io.controller.payload.unaligned := io.dataBus.cmd.address(0)
      hyperbus.io.controller.payload.memory := True
      hyperbus.io.controller.payload.addr := io.dataBus.cmd.address.resized
      hyperbus.io.controller.payload.data := io.dataBus.cmd.data
      hyperbus.io.controller.payload.strobe := getStrobe(io.dataBus.cmd.length)
      hyperbus.io.controller.payload.last := True

      bmbDataStorage.io.push.valid := False
      bmbDataStorage.io.push.payload.source := io.dataBus.cmd.source
      bmbDataStorage.io.push.payload.context := io.dataBus.cmd.context
      bmbDataStorage.io.push.payload.size := io.dataBus.cmd.length

      val incomingScheduler = new StateMachine {
        val counter = Reg(UInt(dataBusConfig.access.lengthWidth bits)).init(4)

        val idle: State = new State with EntryPoint {
          whenIsActive {
            when(io.dataBus.cmd.valid && bmbDataStorage.io.push.ready) {
              hyperbus.io.controller.valid := True
              when(hyperbus.io.controller.fire) {
                bmbDataStorage.io.push.valid := True
                when(io.dataBus.cmd.length > U(dataBusConfig.access.byteCount - 1)) {
                  counter := dataBusConfig.access.byteCount
                  hyperbus.io.controller.payload.last := False
                  address := Bmb.incr(io.dataBus.cmd.address, dataBusConfig)
                  goto(burst)
                } otherwise {
                  io.dataBus.cmd.ready := True
                }
              }
            }
          }
        }

        val burst: State = new State {
          whenIsActive {
            when(io.dataBus.cmd.valid && bmbDataStorage.io.push.ready) {
              hyperbus.io.controller.valid := True
              hyperbus.io.controller.payload.addr := address.resized
              when(hyperbus.io.controller.fire) {
                bmbDataStorage.io.push.valid := True
                counter := counter + dataBusConfig.access.byteCount
                hyperbus.io.controller.payload.last := False
                address := Bmb.incr(address, dataBusConfig)
                when(counter === ((io.dataBus.cmd.length - dataBusConfig.access.byteCount) + 1)) {
                  hyperbus.io.controller.payload.last := True
                  io.dataBus.cmd.ready := True
                  goto(idle)
                }
              }
            }
          }
        }
      }
    }

    val outgoing = new Area {
      io.dataBus.rsp.valid := hyperbus.io.frontend.valid
      io.dataBus.rsp.source := bmbDataStorage.io.pop.source
      io.dataBus.rsp.context := bmbDataStorage.io.pop.context
      io.dataBus.rsp.data := bmbDataStorage.io.pop.payload.size.mux(
        U(0) -> hyperbus.io.frontend.payload.data(7 downto 0) #* 4,
        U(1) -> hyperbus.io.frontend.payload.data(15 downto 0) #* 2,
        default -> hyperbus.io.frontend.payload.data
      )
      io.dataBus.rsp.setSuccess()
      io.dataBus.rsp.last := hyperbus.io.frontend.payload.last
      hyperbus.io.frontend.ready := io.dataBus.rsp.fire
      bmbDataStorage.io.pop.ready := io.dataBus.rsp.fire
    }
  }
}
