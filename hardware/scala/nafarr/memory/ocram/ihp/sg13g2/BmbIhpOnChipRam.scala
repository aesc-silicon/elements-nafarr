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

    val ram = Memory(32, size)
    ram.connectDefaults()

    io.bus.cmd.ready := !io.bus.rsp.isStall
    io.bus.rsp.valid := RegNextWhen(io.bus.cmd.valid, io.bus.cmd.ready) init (False)
    io.bus.rsp.source := RegNextWhen(io.bus.cmd.source, io.bus.cmd.ready)
    io.bus.rsp.context := RegNextWhen(io.bus.cmd.context, io.bus.cmd.ready)
    io.bus.rsp.data := ram.A_DOUT

    val addr = CombInit((io.bus.cmd.address >> p.access.wordRangeLength).resize(ram.addrWidth))
    val data = CombInit(io.bus.cmd.data)
    val enable = CombInit(io.bus.cmd.fire)
    val write = CombInit(io.bus.cmd.isWrite)
    val mask = CombInit(io.bus.cmd.mask)

    ram.A_ADDR := addr.asBits
    ram.A_DIN := data
    ram.A_MEN := io.bus.cmd.fire
    ram.A_WEN := write
    ram.A_REN := !write
    ram.A_BM := Cat(mask.asBools.map(_ #* 8))

    io.bus.rsp.setSuccess()
    io.bus.rsp.last := True
  }
}
