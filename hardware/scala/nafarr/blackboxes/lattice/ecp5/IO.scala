package nafarr.blackboxes.lattice.ecp5

import spinal.core._
import spinal.core.sim._
import spinal.lib.io.{TriState, ReadableOpenDrain}
import spinal.lib.History

abstract class LatticeIo(pin: String) extends Bundle {

  var pinName = pin

  var ioStandardName = ""
  var ioTerm = ""
  var ioSlew = ""
  var clockSpeed: HertzNumber = 1 Hz
  var comment_ = ""
  var pullType = ""
  var isOpendrain = false

  def getPin() = this.pinName
  def getIoStandard() = this.ioStandardName
  def getTerm() = this.ioTerm
  def getSlew() = this.ioSlew
  def getClockSpeed() = this.clockSpeed
}

object LatticeCmosIo {
  def apply(pin: String) = new LatticeCmosIo(pin)

  class LatticeCmosIo(pin: String) extends LatticeIo(pin) {
    val PAD = inout(Analog(Bool()))
    ioStandard("LVCMOS33")
    def ioStandard(ioStandard: String) = {
      this.ioStandardName = ioStandard
      this
    }
    def clock(speed: HertzNumber) = {
      this.clockSpeed = speed
      this
    }
    def inTerm(term: String) = {
      this.ioTerm = term
      this
    }
    def slew(slew: String) = {
      this.ioSlew = slew
      this
    }
    def comment(comment: String) = {
      this.comment_ = comment
      this
    }
    def pullUp = {
      this.pull("UP")
    }
    def pullDown = {
      this.pull("DOWN")
    }
    def pull(pull: String) = {
      this.pullType = pull
      this
    }
    def opendrain = {
      this.isOpendrain = true
      this
    }
    def <>(that: FakeIo.FakeIo) = that.io.IO := this.PAD
    def <>(that: FakeI.FakeI) = that.io.I := this.PAD
    def <>(that: FakeO.FakeO) = this.PAD := that.io.O
  }
}

object FakeIo {
  def apply() = FakeIo()
  def apply(pin: TriState[Bool]) = FakeIo().withTriState(pin, false)
  def apply(pin: TriState[Bool], inverted: Boolean) = FakeIo().withTriState(pin, inverted)
  def apply(pin: ReadableOpenDrain[Bool]) = FakeIo().withReadableOpenDrain(pin)
  def apply(in: Bool, out: Bool, en: Bool) = FakeIo().withBools(in, out, en, false)
  def apply(in: Bool, out: Bool, en: Bool, inverted: Boolean) =
    FakeIo().withBools(in, out, en, inverted)

  case class FakeIo() extends Component {
    val io = new Bundle {
      val I, T = in(Bool())
      val O = out(Bool())
      val IO = inout(Analog(Bool()))
    }

    when(io.T) {
      io.IO := io.I
    }
    io.O := io.IO

    def withTriState(pin: TriState[Bool], inverted: Boolean) = {
      if (inverted)
        this.io.I := !pin.write
      else
        this.io.I := pin.write
      this.io.T := pin.writeEnable
      pin.read := this.io.O
      this
    }
    def withReadableOpenDrain(pin: ReadableOpenDrain[Bool]) = {
      this.withBools(pin.read, False, pin.write, false)
    }
    def withBools(in: Bool, out: Bool, en: Bool, inverted: Boolean) = {
      if (inverted)
        this.io.I := !out
      else
        this.io.I := out
      this.io.T := en
      in := this.io.O
      this
    }
  }
}

object FakeI {
  def apply() = FakeI()
  def apply(pin: Bool) = FakeI().withBool(pin)

  case class FakeI() extends Component {
    val io = new Bundle {
      val I = in(Bool())
      val O = out(Bool())
    }

    io.O := io.I

    def withBool(pin: Bool) = {
      pin := this.io.O
      this
    }
  }
}

object FakeO {
  def apply() = FakeO()
  def apply(pin: Bool) = FakeO().withBool(pin, false)
  def apply(pin: Bool, inverted: Boolean) = FakeO().withBool(pin, inverted)

  case class FakeO() extends Component {
    val io = new Bundle {
      val I = in(Bool())
      val O = out(Bool())
    }

    io.O := io.I

    def withBool(pin: Bool, inverted: Boolean) = {
      if (inverted)
        this.io.I := !pin
      else
        this.io.I := pin
      this
    }
    def driveHigh() = {
      this.io.I := True
      this
    }
    def driveLow() = {
      this.io.I := False
      this
    }
  }
}

object USRMCLK {
  def apply(pin: Bool) = USRMCLK().withBool(pin)

  case class USRMCLK() extends BlackBox {
    val USRMCLKI = in(Bool())
    val USRMCLKTS = in(Bool())

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/lattice/ecp5/IO.v")

    def withBool(pin: Bool) = {
      this.USRMCLKI := pin
      this.USRMCLKTS := True
    }
  }
}
