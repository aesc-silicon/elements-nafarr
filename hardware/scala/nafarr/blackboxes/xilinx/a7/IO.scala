package nafarr.blackboxes.xilinx.a7

import spinal.core._
import spinal.core.sim._
import spinal.lib.io.{TriState, ReadableOpenDrain}
import spinal.lib.History

abstract class XilinxIo(pin: String) extends Bundle {

  var pinName = pin

  var ioStandardName = ""
  var ioTerm = ""
  var ioSlew = ""
  var clockSpeed: HertzNumber = 1 Hz
  var dedicatedClockRoute = true
  var comment_ = ""
  var pullType = ""

  def getPin() = this.pinName
  def getIoStandard() = this.ioStandardName
  def getTerm() = this.ioTerm
  def getSlew() = this.ioSlew
  def getClockSpeed() = this.clockSpeed
}

object XilinxCmosIo {
  def apply(pin: String) = new XilinxCmosIo(pin)

  class XilinxCmosIo(pin: String) extends XilinxIo(pin) {
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
    def disableDedicatedClockRoute = {
      this.dedicatedClockRoute = false
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
    // Only valid for IBUF, OBUFT and IOBUF!
    def pull(pull: String) = {
      this.pullType = pull
      this
    }
    def <>(that: IOBUF.IOBUF) = that.IO := this.PAD
    def <>(that: IBUF.IBUF) = that.I := this.PAD
    def <>(that: OBUF.OBUF) = this.PAD := that.O
    def <>(that: OBUFT.OBUFT) = this.PAD := that.O
    def <>(that: PULLUP.PULLUP) = that.O := this.PAD
    def <>(that: PULLDOWN.PULLDOWN) = that.O := this.PAD
  }
}

object XilinxLvdsInput {
  case class Pos(pin: String) extends XilinxIo(pin) {
    val PAD = in(Bool())
    ioStandard("LVDS_25")
    def ioStandard(ioStandard: String) = {
      this.ioStandardName = ioStandard
      this
    }
    def clock(speed: HertzNumber) = {
      this.clockSpeed = speed
      this
    }
    def disableDedicatedClockRoute = {
      this.dedicatedClockRoute = false
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
    def <>(that: IBUFDS.IBUFDS) = that.I := this.PAD
  }
  case class Neg(pin: String) extends XilinxIo(pin) {
    val PAD = in(Bool())
    ioStandard("LVDS_25")
    def ioStandard(ioStandard: String) = {
      this.ioStandardName = ioStandard
      this
    }
    def clock(speed: HertzNumber) = {
      this.clockSpeed = speed
      this
    }
    def disableDedicatedClockRoute = {
      this.dedicatedClockRoute = false
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
    def <>(that: IBUFDS.IBUFDS) = that.IB := this.PAD
  }
}

object XilinxLvdsOutput {
  case class Pos(pin: String) extends XilinxIo(pin) {
    val PAD = out(Bool())
    ioStandard("LVDS_25")
    def ioStandard(ioStandard: String) = {
      this.ioStandardName = ioStandard
      this
    }
    def clock(speed: HertzNumber) = {
      this.clockSpeed = speed
      this
    }
    def disableDedicatedClockRoute = {
      this.dedicatedClockRoute = false
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
    def <>(that: IBUFDS.IBUFDS) = that.I := this.PAD
  }
  case class Neg(pin: String) extends XilinxIo(pin) {
    val PAD = out(Bool())
    ioStandard("LVDS_25")
    def ioStandard(ioStandard: String) = {
      this.ioStandardName = ioStandard
      this
    }
    def clock(speed: HertzNumber) = {
      this.clockSpeed = speed
      this
    }
    def disableDedicatedClockRoute = {
      this.dedicatedClockRoute = false
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
    def <>(that: IBUFDS.IBUFDS) = that.IB := this.PAD
  }
}

object IOBUF {
  def apply() = IOBUF()
  def apply(pin: TriState[Bool]) = IOBUF().withTriState(pin, false)
  def apply(pin: TriState[Bool], inverted: Boolean) = IOBUF().withTriState(pin, inverted)
  def apply(pin: ReadableOpenDrain[Bool]) = IOBUF().withReadableOpenDrain(pin)
  def apply(in: Bool, out: Bool, en: Bool) = IOBUF().withBools(in, out, en, false)
  def apply(in: Bool, out: Bool, en: Bool, inverted: Boolean) =
    IOBUF().withBools(in, out, en, inverted)

  case class IOBUF(
      DRIVE: Int = 12,
      IBUF_LOW_PWR: String = "TRUE",
      IOSTANDARD: String = "DEFAULT",
      SLEW: String = "SLOW"
  ) extends BlackBox {
    val I, T = in(Bool())
    val O = out(Bool())
    val IO = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/IO.v")

    addGeneric("DRIVE", DRIVE)
    addGeneric("IBUF_LOW_PWR", IBUF_LOW_PWR)
    addGeneric("IOSTANDARD", IOSTANDARD)
    addGeneric("SLEW", SLEW)

    when(T) {
      IO := I
    }
    O := IO

    def withTriState(pin: TriState[Bool], inverted: Boolean) = {
      if (inverted)
        this.I := !pin.write
      else
        this.I := pin.write
      this.T := !pin.writeEnable
      pin.read := this.O
      this
    }
    def withReadableOpenDrain(pin: ReadableOpenDrain[Bool]) = {
      this.withBools(pin.read, False, pin.write, false)
    }

    def withBools(in: Bool, out: Bool, en: Bool, inverted: Boolean) = {
      if (inverted)
        this.I := !out
      else
        this.I := out
      this.T := !en
      in := this.O
      this
    }

  }
}

object IBUF {
  def apply() = IBUF()
  def apply(pin: Bool) = IBUF().withBool(pin)

  case class IBUF(
      CAPACITANCE: String = "DONT_CARE",
      IBUF_DELAY_VALUE: Int = 0,
      IBUF_LOW_PWR: String = "TRUE",
      IFD_DELAY_VALUE: String = "AUTO",
      IOSTANDARD: String = "DEFAULT"
  ) extends BlackBox {
    val I = in(Bool())
    val O = out(Bool())

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/IO.v")

    addGeneric("IBUF_LOW_PWR", IBUF_LOW_PWR)
    addGeneric("IOSTANDARD", IOSTANDARD)

    O := I

    def withBool(pin: Bool) = {
      pin := this.O
      this
    }
  }
}

object OBUF {
  def apply() = OBUF()
  def apply(pin: Bool) = OBUF().withBool(pin)

  case class OBUF(
      CAPACITANCE: String = "DONT_CARE",
      DRIVE: Int = 12,
      IOSTANDARD: String = "DEFAULT"
  ) extends BlackBox {
    val I = in(Bool())
    val O = out(Bool())

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/IO.v")

    addGeneric("DRIVE", DRIVE)
    addGeneric("IOSTANDARD", IOSTANDARD)

    O := I

    def withBool(pin: Bool) = {
      this.I := pin
      this
    }
    def driveHigh() = {
      this.I := True
      this
    }
    def driveLow() = {
      this.I := False
      this
    }
  }
}

object OBUFT {
  def apply() = OBUFT()
  def apply(write: Bool, writeEnable: Bool) = OBUFT().withBools(write, writeEnable)

  case class OBUFT(
      CAPACITANCE: String = "DONT_CARE",
      DRIVE: Int = 12,
      IOSTANDARD: String = "DEFAULT"
  ) extends BlackBox {
    val I, T = in(Bool())
    val O = out(Bool())

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/IO.v")

    addGeneric("DRIVE", DRIVE)
    addGeneric("IOSTANDARD", IOSTANDARD)

    when(T) {
      O := I
    }

    def withBools(write: Bool, writeEnable: Bool) = {
      this.I := write
      this.T := writeEnable
      this
    }
  }
}

object PULLUP {
  def apply() = PULLUP()

  case class PULLUP() extends BlackBox {
    // Fake direction for SpinalHDL. Pin O is output!
    val O = in(Bool())

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/IO.v")
  }
}

object PULLDOWN {
  def apply() = PULLDOWN()

  case class PULLDOWN() extends BlackBox {
    // Fake direction for SpinalHDL. Pin O is output!
    val O = out(Bool())

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/IO.v")
  }
}

object IBUFDS {
  def apply() = IBUFDS()
  def apply(pin: Bool) = IBUFDS().withBool(pin)

  case class IBUFDS(
      CAPACITANCE: String = "DONT_CARE",
      DIFF_TERM: String = "FALSE",
      DQS_BIAS: String = "FALSE",
      IBUF_DELAY_VALUE: Int = 0,
      IBUF_LOW_PWR: String = "TRUE",
      IFD_DELAY_VALUE: String = "AUTO",
      IOSTANDARD: String = "DEFAULT"
  ) extends BlackBox {
    val I = in(Bool())
    val IB = in(Bool())
    val O = out(Bool())

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/IO.v")

    addGeneric("DIFF_TERM", DIFF_TERM)
    addGeneric("DQS_BIAS", DQS_BIAS)
    addGeneric("IBUF_LOW_PWR", IBUF_LOW_PWR)
    addGeneric("IOSTANDARD", IOSTANDARD)

    O := I

    def withBool(pin: Bool) = {
      pin := this.O
      this
    }
  }
}
