package nafarr.memory.ocram.ihp.sg13g2

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.amba4.axi._

import nafarr.blackboxes.ihp.sg13g2._

object Axi4SharedIhpOnChipRam {
  def getAxiConfig(dataWidth: Int, byteCount: BigInt, idWidth: Int) = Axi4Config(
    addressWidth = log2Up(byteCount),
    dataWidth = dataWidth,
    idWidth = idWidth,
    useLock = false,
    useRegion = false,
    useCache = false,
    useProt = false,
    useQos = false
  )

  case class OnePort1024x8(dataWidth: Int, byteCount: BigInt, idWidth: Int) extends Component {
    val axiConfig = Axi4SharedOnChipRam.getAxiConfig(dataWidth, byteCount, idWidth)

    val io = new Bundle {
      val axi = slave(Axi4Shared(axiConfig))
    }

    val engine = new StateMachine {
      val counter = Reg(UInt(log2Up(axiConfig.bytePerWord) bits))
      val resp = Reg(Bits(axiConfig.bytePerWord * 8 bits))

      val ram = Memory.RM_IHPSG13_1P_1024x8_c2_bm_bist()
      ram.connectDefaults()
      ram.A_MEN := False
      ram.A_WEN := False
      ram.A_REN := False
      ram.A_ADDR := 0
      ram.A_DIN := 0
      ram.A_BM := B(ram.dataWidth bits, default -> True)

      io.axi.arw.ready := False

      io.axi.w.ready := False

      io.axi.r.valid := False
      io.axi.r.payload.data := resp
      io.axi.r.id := io.axi.arw.id
      io.axi.r.last := True // TODO Read burst not supported
      io.axi.r.setOKAY()

      io.axi.b.valid := False
      io.axi.b.setOKAY()
      io.axi.b.id := io.axi.arw.id

      val stateEntry: State = new State with EntryPoint {
        whenIsActive {
          counter := 0
          when(io.axi.arw.valid) {
            when(io.axi.arw.write) {
              goto(write)
            } otherwise {
              goto(read)
            }
          }
        }
      }
      val read: State = new State {
        whenIsActive {
          counter := counter + 1
          ram.A_MEN := True
          val strb = io.axi.arw.payload.size.mux(
            0 -> B"0001",
            1 -> B"0011",
            2 -> B"1111",
            default -> B"0000"
          )
          ram.A_REN := strb(counter)
          ram.A_ADDR := (io.axi.arw.addr + counter.resize(ram.addrWidth))
            .asBits(ram.addrWidth - 1 downto 0)
          resp := ram.A_DOUT ## resp(31 downto 8)
          when(counter === U(axiConfig.bytePerWord - 1)) {
            goto(readFinish)
          }
        }
      }
      val readFinish: State = new State {
        whenIsActive {
          resp := ram.A_DOUT ## resp(31 downto 8)
          goto(finish)
        }
      }
      val write: State = new State {
        whenIsActive {
          when(io.axi.w.valid) {
            counter := counter + 1
            ram.A_MEN := True
            ram.A_WEN := io.axi.w.strb(counter)
            ram.A_ADDR := (io.axi.arw.addr + counter.resize(ram.addrWidth))
              .asBits(ram.addrWidth - 1 downto 0)
            ram.A_DIN := io.axi.w.data.subdivideIn(8 bits)(counter)
            when(counter === U(axiConfig.bytePerWord - 1)) {
              io.axi.w.ready := io.axi.arw.valid && io.axi.arw.write
              goto(finish)
            }
          }
        }
      }
      val finish: State = new State {
        whenIsActive {
          io.axi.r.valid := io.axi.arw.valid && !io.axi.arw.write
          io.axi.b.valid := io.axi.arw.valid && io.axi.arw.write && io.axi.w.last
          io.axi.arw.ready := (io.axi.r.valid && io.axi.r.last) || io.axi.b.valid
          goto(stateEntry)
        }
      }
    }
  }
}
