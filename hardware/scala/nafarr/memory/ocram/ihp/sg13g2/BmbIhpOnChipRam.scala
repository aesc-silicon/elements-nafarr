// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.ocram.ihp.sg13g2

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.bmb._

import nafarr.blackboxes.ihp.sg13g2._

object BmbIhpOnChipRam {

  case class OnePort4Macros(p: BmbParameter, size: Int) extends Component {
    val io = new Bundle {
      val bus = slave(Bmb(p))
    }

    val ram0 = Memory(8, size / 4)
    ram0.connectDefaults()
    val ram1 = Memory(8, size / 4)
    ram1.connectDefaults()
    val ram2 = Memory(8, size / 4)
    ram2.connectDefaults()
    val ram3 = Memory(8, size / 4)
    ram3.connectDefaults()

    io.bus.cmd.ready := !io.bus.rsp.isStall
    io.bus.rsp.valid := RegNextWhen(io.bus.cmd.valid, io.bus.cmd.ready) init (False)
    io.bus.rsp.source := RegNextWhen(io.bus.cmd.source, io.bus.cmd.ready)
    io.bus.rsp.context := RegNextWhen(io.bus.cmd.context, io.bus.cmd.ready)
    io.bus.rsp.data := ram3.A_DOUT ## ram2.A_DOUT ## ram1.A_DOUT ## ram0.A_DOUT

    val addr = CombInit((io.bus.cmd.address >> p.access.wordRangeLength).resize(ram0.addrWidth))
    val data = CombInit(io.bus.cmd.data)
    val enable = CombInit(io.bus.cmd.fire)
    val write = CombInit(io.bus.cmd.isWrite)
    val mask = CombInit(io.bus.cmd.mask)

    ram0.A_ADDR := addr.asBits
    ram0.A_DIN := data(0, BitCount(8))
    ram0.A_MEN := io.bus.cmd.fire
    ram0.A_WEN := write
    ram0.A_REN := !write
    ram0.A_BM := mask(0) #* 8

    ram1.A_ADDR := addr.asBits
    ram1.A_DIN := data(8, BitCount(8))
    ram1.A_MEN := io.bus.cmd.fire
    ram1.A_WEN := write
    ram1.A_REN := !write
    ram1.A_BM := mask(1) #* 8

    ram2.A_ADDR := addr.asBits
    ram2.A_DIN := data(16, BitCount(8))
    ram2.A_MEN := io.bus.cmd.fire
    ram2.A_WEN := write
    ram2.A_REN := !write
    ram2.A_BM := mask(2) #* 8

    ram3.A_ADDR := addr.asBits
    ram3.A_DIN := data(24, BitCount(8))
    ram3.A_MEN := io.bus.cmd.fire
    ram3.A_WEN := write
    ram3.A_REN := !write
    ram3.A_BM := mask(3) #* 8

    io.bus.rsp.setSuccess()
    io.bus.rsp.last := True
  }

  case class OnePort1Macro(p: BmbParameter, size: Int) extends Component {
    val io = new Bundle {
      val bus = slave(Bmb(p))
    }

    def convertAddr(addr: UInt) = (addr >> p.access.wordRangeLength).resize(ram.addrWidth)

    val ram = Memory(32, size)
    ram.connectDefaults()

    io.bus.cmd.ready := False
    val valid = RegInit(False)
    val source = Reg(cloneOf(io.bus.cmd.source))
    val context = Reg(cloneOf(io.bus.cmd.context))
    val addrOffset = Reg(UInt(2 bits))
    val last = True
    io.bus.rsp.valid := valid
    io.bus.rsp.source := source
    io.bus.rsp.context := context
    io.bus.rsp.data := addrOffset.mux(
      U(1) -> ram.A_DOUT(7 downto 0) ## ram.A_DOUT(31 downto 8),
      U(2) -> ram.A_DOUT(15 downto 0) ## ram.A_DOUT(31 downto 16),
      U(3) -> ram.A_DOUT(23 downto 0) ## ram.A_DOUT(31 downto 24),
      default -> ram.A_DOUT
    )
    io.bus.rsp.last := last

    val addr = CombInit(convertAddr(io.bus.cmd.address))
    val data = CombInit(io.bus.cmd.data)
    val memEnable = False
    val write = CombInit(io.bus.cmd.isWrite)
    val mask = CombInit(io.bus.cmd.mask)

    ram.A_ADDR := addr.asBits
    ram.A_DIN := io.bus.cmd
      .address(0, 2 bits)
      .mux(
        U(1) -> data(23 downto 0) ## data(31 downto 24),
        U(2) -> data(15 downto 0) ## data(31 downto 16),
        U(3) -> data(7 downto 0) ## data(31 downto 8),
        default -> data
      )
    ram.A_MEN := memEnable
    ram.A_WEN := write
    ram.A_REN := !write
    ram.A_BM := Cat(mask.asBools.map(_ #* 8))

    io.bus.rsp.setSuccess()

    val fsm = new StateMachine {
      val counter = Reg(UInt(p.access.lengthWidth bits)).init(4)

      val idle: State = new State with EntryPoint {
        whenIsActive {
          when(io.bus.cmd.valid) {
            valid := io.bus.rsp.isFree
            memEnable := io.bus.rsp.isFree
            source := io.bus.cmd.source
            context := io.bus.cmd.context
            addrOffset := io.bus.cmd.address(0, 2 bits)
            when(io.bus.cmd.length > U(p.access.byteCount - 1)) {
              counter := 4
              goto(burst)
            } otherwise {
              io.bus.cmd.ready := io.bus.rsp.isFree
            }
          } otherwise {
            valid := False
          }
        }
      }

      val burst: State = new State {
        whenIsActive {
          when(io.bus.rsp.isFree) {
            valid := True
            memEnable := True
            last := False
            addr := convertAddr(Bmb.addToAddress(io.bus.cmd.address, counter, p))
            counter := counter + p.access.byteCount
            when(counter === (io.bus.cmd.length + 1)) {
              last := True
              io.bus.cmd.ready := True
              goto(idle)
            }
          } otherwise {
            valid := False
          }
        }
      }
    }
  }
}
