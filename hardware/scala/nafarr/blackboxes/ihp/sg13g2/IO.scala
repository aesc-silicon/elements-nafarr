package nafarr.blackboxes.ihp.sg13g2

import spinal.core._
import spinal.lib.io.{TriState, ReadableOpenDrain}
import spinal.lib.History

object IhpCmosIo {
  def apply(edge: String, number: Int) = new IhpCmosIo(edge, number)

  class IhpCmosIo(edge: String, number: Int) extends Bundle {
    val PAD = inout(Analog(Bool()))

    var cell = ""
    val edge_ = edge
    val number_ = number

    def <>(that: IOPadIn.sg13g2_IOPadIn) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadIn"

    }
    def <>(that: IOPadOut4mA.sg13g2_IOPadOut4mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadOut4mA"
    }
    def <>(that: IOPadOut16mA.sg13g2_IOPadOut16mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadOut16mA"
    }
    def <>(that: IOPadOut30mA.sg13g2_IOPadOut30mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadOut30mA"
    }
    def <>(that: IOPadInOut4mA.sg13g2_IOPadInOut4mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadInOut4mA"
    }
    def <>(that: IOPadInOut16mA.sg13g2_IOPadInOut16mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadInOut16mA"
    }
    def <>(that: IOPadInOut30mA.sg13g2_IOPadInOut30mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadInOut30mA"
    }
    def <>(that: IOPadAnalog.sg13g2_IOPadAnalog) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadAnalog"
    }
    def <>(that: IOPadIOVss.sg13g2_IOPadIOVss) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadIOVss"
    }
    def <>(that: IOPadIOVdd.sg13g2_IOPadIOVdd) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadIOVdd"
    }
    def <>(that: IOPadVss.sg13g2_IOPadVss) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadVss"
    }
    def <>(that: IOPadVdd.sg13g2_IOPadVdd) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadVdd"
    }
  }
}

object IOPadIn {
  def apply() = sg13g2_IOPadIn()
  def apply(pin: Bool) = sg13g2_IOPadIn().withBool(pin)

  case class sg13g2_IOPadIn() extends BlackBox {
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    p2c := pad

    def withBool(pin: Bool) = {
      pin := this.p2c
      this
    }
  }
}

object IOPadOut4mA {
  def apply() = sg13g2_IOPadOut4mA()
  def apply(pin: Bool) = sg13g2_IOPadOut4mA().withBool(pin)

  case class sg13g2_IOPadOut4mA() extends BlackBox {
    val c2p = in(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    pad := c2p

    def withBool(pin: Bool) = {
      this.c2p := pin
      this
    }
  }
}

object IOPadOut16mA {
  def apply() = sg13g2_IOPadOut16mA()
  def apply(pin: Bool) = sg13g2_IOPadOut16mA().withBool(pin)

  case class sg13g2_IOPadOut16mA() extends BlackBox {
    val c2p = in(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    pad := c2p

    def withBool(pin: Bool) = {
      this.c2p := pin
      this
    }
  }
}

object IOPadOut30mA {
  def apply() = sg13g2_IOPadOut30mA()
  def apply(pin: Bool) = sg13g2_IOPadOut30mA().withBool(pin)

  case class sg13g2_IOPadOut30mA() extends BlackBox {
    val c2p = in(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    pad := c2p

    def withBool(pin: Bool) = {
      this.c2p := pin
      this
    }
  }
}

object IOPadInOut4mA {
  def apply() = sg13g2_IOPadInOut4mA()
  def apply(pin: TriState[Bool]) = sg13g2_IOPadInOut4mA().withTriState(pin)

  case class sg13g2_IOPadInOut4mA() extends BlackBox {
    val c2p = in(Bool())
    val c2p_en = in(Bool())
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    when(c2p_en) {
      pad := c2p
    }
    p2c := pad

    def withTriState(pin: TriState[Bool]) = {
      this.c2p := pin.write
      this.c2p_en := pin.writeEnable
      pin.read := this.p2c
      this
    }
    def withOpenDrain(pin: ReadableOpenDrain[Bool]) = {
      this.c2p := False
      this.c2p_en := pin.write
      pin.read := this.p2c
      this
    }
  }
}

object IOPadInOut16mA {
  def apply() = sg13g2_IOPadInOut16mA()
  def apply(pin: TriState[Bool]) = sg13g2_IOPadInOut16mA().withTriState(pin)

  case class sg13g2_IOPadInOut16mA() extends BlackBox {
    val c2p = in(Bool())
    val c2p_en = in(Bool())
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    when(c2p_en) {
      pad := c2p
    }
    p2c := pad

    def withTriState(pin: TriState[Bool]) = {
      this.c2p := pin.write
      this.c2p_en := pin.writeEnable
      pin.read := this.p2c
      this
    }
    def withOpenDrain(pin: ReadableOpenDrain[Bool]) = {
      this.c2p := False
      this.c2p_en := pin.write
      pin.read := this.p2c
      this
    }
  }
}

object IOPadInOut30mA {
  def apply() = sg13g2_IOPadInOut30mA()
  def apply(pin: TriState[Bool]) = sg13g2_IOPadInOut30mA().withTriState(pin)

  case class sg13g2_IOPadInOut30mA() extends BlackBox {
    val c2p = in(Bool())
    val c2p_en = in(Bool())
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    when(c2p_en) {
      pad := c2p
    }
    p2c := pad

    def withTriState(pin: TriState[Bool]) = {
      this.c2p := pin.write
      this.c2p_en := pin.writeEnable
      pin.read := this.p2c
      this
    }
    def withOpenDrain(pin: ReadableOpenDrain[Bool]) = {
      this.c2p := False
      this.c2p_en := pin.write
      pin.read := this.p2c
      this
    }
  }
}

object IOPadAnalog {
  def apply() = sg13g2_IOPadAnalog()

  case class sg13g2_IOPadAnalog() extends BlackBox {
    val pad = inout(Analog(Bool()))
    val padres = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")

    padres <> pad
  }
}

object IOPadIOVss {
  def apply() = sg13g2_IOPadIOVss()

  case class sg13g2_IOPadIOVss() extends BlackBox {
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")
  }
}

object IOPadIOVdd {
  def apply() = sg13g2_IOPadIOVdd()

  case class sg13g2_IOPadIOVdd() extends BlackBox {
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")
  }
}

object IOPadVss {
  def apply() = sg13g2_IOPadVss()

  case class sg13g2_IOPadVss() extends BlackBox {
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")
  }
}

object IOPadVdd {
  def apply() = sg13g2_IOPadVdd()

  case class sg13g2_IOPadVdd() extends BlackBox {
    val pad = inout(Analog(Bool()))

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/IO.v")
  }
}
