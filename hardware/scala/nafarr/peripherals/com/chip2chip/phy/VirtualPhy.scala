package nafarr.peripherals.com.chip2chip.phy

import spinal.core._
import spinal.lib._

object VirtualPhy {

  case class Io(ioPins: Int = 16) extends Bundle with IMasterSlave {
    val data = Bits(ioPins bits)
    val enable = Bool()
    val stall = Bool()

    override def asMaster() = {
      out(data, enable)
      in(stall)
    }
    override def asSlave() = {
      in(data, enable)
      out(stall)
    }

    def <<(that: Io) = that >> this
    def >>(that: Io) = {
      that.data := this.data
      that.enable := this.enable
      this.stall := that.stall
    }
  }

  case class Tx(ioPins: Int = 16) extends Component {
    val io = new Bundle {
      val phy = master(Io(ioPins))
      val fromLinkLayer = slave(Stream(Bits(ioPins * 10 bits)))
    }

    val data = Reg(Bits(ioPins bits)).init(B(ioPins bits, default -> false))
    val enable = Reg(Bool()).init(False)
    val ready = Reg(Bool()).init(False)
    val run = Reg(Bool()).init(False)
    when(!run && io.fromLinkLayer.valid && !io.phy.stall && !ready) {
      run := True
    }

    val indexCounter = Reg(UInt(log2Up(10) bits)).init(0)
    ready := False
    when(run) {
      indexCounter := indexCounter + 1
      when(indexCounter === 9) {
        indexCounter := 0
        ready := True
        run := False
      }
    }

    enable := False
    when(run) {
      enable := True
    }
    for (index <- 0 until ioPins) {
      data(index) := False
      when(run) {
        data(index) := io.fromLinkLayer.payload.subdivideIn(10 bits)(index)(indexCounter)
      }
    }
    io.phy.data := data
    io.phy.enable := enable
    io.fromLinkLayer.ready := ready
  }

  case class Rx(ioPins: Int = 16) extends Component {
    val io = new Bundle {
      val phy = slave(Io(ioPins))
      val fromPhy = master(Stream(Bits(ioPins * 10 bits)))
    }

    val data = Vec(Reg(Bits(10 bits)), ioPins)
    val indexCounter = Reg(UInt(log2Up(10) bits)).init(0)
    val valid = Reg(Bool()).init(False)
    when(io.phy.enable) {
      indexCounter := indexCounter + 1
      when(indexCounter === 9) {
        indexCounter := 0
        valid := True
      }
    }
    when(valid && io.fromPhy.ready) {
      valid := False
    }

    for (index <- 0 until ioPins) {
      when(io.phy.enable) {
        data(index)(indexCounter) := io.phy.data(index)
      }
    }

    val stallNext = RegNext(io.phy.enable || valid && !io.fromPhy.ready)
    io.phy.stall := stallNext
    io.fromPhy.valid := valid && indexCounter === 0
    io.fromPhy.payload := data.asBits
  }
}
