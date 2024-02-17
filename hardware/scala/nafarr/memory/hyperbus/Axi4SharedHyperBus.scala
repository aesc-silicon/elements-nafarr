package nafarr.memory.hyperbus

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.amba3.apb._

object Axi4SharedHyperBus {
  def apply(p: HyperBusCtrl.Parameter) = Axi4SharedHyperBus(p)

  case class Axi4Data(axi4Config: Axi4Config) extends Bundle {
    val id = UInt(axi4Config.idWidth bits)
    val size = Bits(4 bits)
  }

  case class Axi4SharedHyperBus(
      p: HyperBusCtrl.Parameter,
      axi4Config: Axi4Config = Axi4Config(32, 32, 4),
      apb3Config: Apb3Config = Apb3Config(12, 32)
  ) extends Component {
    val io = new Bundle {
      val memory = slave(Axi4Shared(axi4Config))
      val bus = slave(Apb3(apb3Config))
      val phy = master(HyperBus.Phy.Interface(p))
    }

    val hyperbus = Apb3HyperBus(p, apb3Config)
    hyperbus.io.bus <> io.bus
    io.phy <> hyperbus.io.phy

    val axiDataStorage = StreamFifo(Axi4Data(axi4Config), p.frontend.storageDepth)

    val incomingScheduler = new StateMachine {
      val id = Reg(UInt(p.frontend.idLength bits)) init (0)
      when(io.memory.arw.fire) {
        id := id + 1
      }
      val burst = new Area {
        val address = Reg(UInt(axi4Config.addressWidth bits))
        val count = Reg(UInt(8 bits))
        val size = Reg(UInt(3 bits))
        val burstType = Reg(Bits(2 bits))
      }

      io.memory.arw.ready := False
      io.memory.w.ready := False

      hyperbus.io.controller.valid := False
      hyperbus.io.controller.payload.id := id
      hyperbus.io.controller.payload.read := False
      hyperbus.io.controller.payload.unaligned := False
      hyperbus.io.controller.payload.memory := True
      hyperbus.io.controller.payload.addr := 0
      hyperbus.io.controller.payload.data := 0
      hyperbus.io.controller.payload.strobe := (default -> false)
      hyperbus.io.controller.payload.last := False

      def getStrobe(size: UInt): Bits = size.mux(
        0 -> B"0001",
        1 -> B"0011",
        2 -> B"1111",
        default -> B"0000"
      )

      axiDataStorage.io.push.valid := False
      axiDataStorage.io.push.payload.id := io.memory.arw.id
      axiDataStorage.io.push.payload.size := getStrobe(io.memory.arw.size)

      val stateEntry: State = new State with EntryPoint {
        whenIsActive {
          when(io.memory.arw.valid) {
            burst.address := io.memory.arw.addr
            burst.count := io.memory.arw.len
            burst.size := io.memory.arw.size
            burst.burstType := io.memory.arw.burst

            when(axiDataStorage.io.push.ready) {
              io.memory.arw.ready := True
              axiDataStorage.io.push.valid := True
              when(io.memory.arw.write) {
                goto(stateWrite)
              } otherwise {
                goto(stateRead)
              }
            }
          }
        }
      }

      val stateWrite: State = new State {
        whenIsActive {

          hyperbus.io.controller.payload.unaligned := burst.address(0)
          hyperbus.io.controller.payload.addr := (B"0" ## burst.address(31 downto 1)).asUInt
          hyperbus.io.controller.payload.data := io.memory.w.data
          hyperbus.io.controller.payload.strobe := getStrobe(burst.size)

          when(io.memory.w.valid) {
            hyperbus.io.controller.valid := True
            io.memory.w.ready := hyperbus.io.controller.ready
            when(hyperbus.io.controller.ready) {
              when(burst.count === 0) {
                hyperbus.io.controller.payload.last := True
                goto(stateEntry)
              } otherwise {
                goto(stateWriteBurst)
              }
            }
          }
        }
      }

      val stateWriteBurst: State = new State {
        whenIsActive {
          val address = Axi4.incr(
            burst.address,
            burst.burstType,
            burst.count,
            burst.size,
            axi4Config.bytePerWord
          )

          hyperbus.io.controller.payload.unaligned := burst.address(0)
          hyperbus.io.controller.payload.addr := (B"0" ## burst.address(31 downto 1)).asUInt
          hyperbus.io.controller.payload.data := io.memory.w.data
          hyperbus.io.controller.payload.strobe := getStrobe(burst.size)

          when(io.memory.w.valid) {
            hyperbus.io.controller.valid := True
            io.memory.w.ready := hyperbus.io.controller.ready
            when(hyperbus.io.controller.ready) {
              burst.address := address
              burst.count := burst.count - 1
              when(burst.count === 0) {
                hyperbus.io.controller.payload.last := True
                goto(stateEntry)
              }
            }
          }
        }
      }

      val stateRead: State = new State {
        whenIsActive {
          hyperbus.io.controller.payload.read := True
          hyperbus.io.controller.payload.unaligned := burst.address(0)
          hyperbus.io.controller.payload.addr := (B"0" ## burst.address(31 downto 1)).asUInt
          hyperbus.io.controller.payload.strobe := getStrobe(burst.size)

          hyperbus.io.controller.valid := True
          when(hyperbus.io.controller.ready) {
            when(burst.count === 0) {
              hyperbus.io.controller.payload.last := True
              goto(stateEntry)
            } otherwise {
              goto(stateReadBurst)
            }
          }
        }
      }

      val stateReadBurst: State = new State {
        whenIsActive {
          val address = Axi4.incr(
            burst.address,
            burst.burstType,
            burst.count,
            burst.size,
            axi4Config.bytePerWord
          )

          hyperbus.io.controller.payload.read := True
          hyperbus.io.controller.payload.unaligned := burst.address(0)
          hyperbus.io.controller.payload.addr := (B"0" ## burst.address(31 downto 1)).asUInt
          hyperbus.io.controller.payload.strobe := getStrobe(burst.size)

          hyperbus.io.controller.valid := True
          when(hyperbus.io.controller.ready) {
            burst.address := address
            burst.count := burst.count - 1
            when(burst.count === 0) {
              hyperbus.io.controller.payload.last := True
              goto(stateEntry)
            }
          }
        }
      }
    }

    val outgoing = new Area {
      io.memory.r.id := axiDataStorage.io.pop.payload.id
      io.memory.r.data := axiDataStorage.io.pop.payload.size.mux(
        B"0001" -> hyperbus.io.frontend.payload.data(7 downto 0) #* 4,
        B"0011" -> hyperbus.io.frontend.payload.data(15 downto 0) #* 2,
        default -> hyperbus.io.frontend.payload.data
      )
      io.memory.r.resp := Axi4.resp.OKAY
      io.memory.r.last := hyperbus.io.frontend.payload.last
      io.memory.r.valid := hyperbus.io.frontend.payload.read && hyperbus.io.frontend.valid

      io.memory.b.id := axiDataStorage.io.pop.payload.id
      io.memory.b.resp := Axi4.resp.OKAY
      io.memory.b.valid := !hyperbus.io.frontend.payload.read && hyperbus.io.frontend.valid

      hyperbus.io.frontend.ready := io.memory.r.ready | io.memory.b.ready
      axiDataStorage.io.pop.ready := io.memory.r.ready | io.memory.b.ready
    }
  }
}
