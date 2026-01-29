// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.blackboxes.ihp.sg13cmos5l

import spinal.core._
import spinal.lib.io.{TriState, ReadableOpenDrain}
import spinal.lib.History

object IhpCmosIo {
  def apply(edge: String, number: Int) = new IhpCmosIo(edge, number)
  def apply(edge: String, number: Int, clockGroup: String) = new IhpCmosIo(edge, number, clockGroup)

  class IhpCmosIo(val edge: String, val number: Int, val clockGroup: String = "") extends Bundle {
    val PAD = inout(Analog(Bool()))

    var cell = ""
    var cellName = ""
    var clockPort = ""
    def clockInput = clockPort.contains("in")
    def clockOutput = clockPort.contains("out")

    def <>(that: IOPadIn.sg13cmos5l_IOPadIn) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadIn"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      clockPort = "input_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadOut4mA.sg13cmos5l_IOPadOut4mA) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadOut4mA"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      clockPort = "output_4mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadOut16mA.sg13cmos5l_IOPadOut16mA) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadOut16mA"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      clockPort = "output_16mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadOut30mA.sg13cmos5l_IOPadOut30mA) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadOut30mA"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      clockPort = "output_30mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadInOut4mA.sg13cmos5l_IOPadInOut4mA) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadInOut4mA"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      clockPort = "inout_4mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadInOut16mA.sg13cmos5l_IOPadInOut16mA) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadInOut16mA"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      clockPort = "inout_16mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadInOut30mA.sg13cmos5l_IOPadInOut30mA) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadInOut30mA"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      clockPort = "inout_30mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadAnalog.sg13cmos5l_IOPadAnalog) = {
      that.pad := this.PAD
      cell = "sg13cmos5l_IOPadAnalog"
      cellName = s"sg13cmos5l_IOPad_${this.getName()}"
      that.setName(this.cellName)
    }
    def <>(that: IOPadIOVss.sg13cmos5l_IOPadIOVss) = {
      cell = "sg13cmos5l_IOPadIOVss"
    }
    def <>(that: IOPadIOVdd.sg13cmos5l_IOPadIOVdd) = {
      cell = "sg13cmos5l_IOPadIOVdd"
    }
    def <>(that: IOPadVss.sg13cmos5l_IOPadVss) = {
      cell = "sg13cmos5l_IOPadVss"
    }
    def <>(that: IOPadVdd.sg13cmos5l_IOPadVdd) = {
      cell = "sg13cmos5l_IOPadVdd"
    }
  }
}

object IOPadIn {
  def apply() = sg13cmos5l_IOPadIn()
  def apply(pin: Bool) = sg13cmos5l_IOPadIn().withBool(pin)

  case class sg13cmos5l_IOPadIn() extends BlackBox {
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

    p2c := pad

    def withBool(pin: Bool) = {
      pin := this.p2c
      this
    }
  }
}

object IOPadOut4mA {
  def apply() = sg13cmos5l_IOPadOut4mA()
  def apply(pin: Bool) = sg13cmos5l_IOPadOut4mA().withBool(pin)

  case class sg13cmos5l_IOPadOut4mA() extends BlackBox {
    val c2p = in(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

    pad := c2p

    def withBool(pin: Bool) = {
      this.c2p := pin
      this
    }
  }
}

object IOPadOut16mA {
  def apply() = sg13cmos5l_IOPadOut16mA()
  def apply(pin: Bool) = sg13cmos5l_IOPadOut16mA().withBool(pin)

  case class sg13cmos5l_IOPadOut16mA() extends BlackBox {
    val c2p = in(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

    pad := c2p

    def withBool(pin: Bool) = {
      this.c2p := pin
      this
    }
  }
}

object IOPadOut30mA {
  def apply() = sg13cmos5l_IOPadOut30mA()
  def apply(pin: Bool) = sg13cmos5l_IOPadOut30mA().withBool(pin)

  case class sg13cmos5l_IOPadOut30mA() extends BlackBox {
    val c2p = in(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

    pad := c2p

    def withBool(pin: Bool) = {
      this.c2p := pin
      this
    }
  }
}

object IOPadInOut4mA {
  def apply() = sg13cmos5l_IOPadInOut4mA()
  def apply(pin: TriState[Bool]) = sg13cmos5l_IOPadInOut4mA().withTriState(pin)
  def apply(pin: ReadableOpenDrain[Bool]) = sg13cmos5l_IOPadInOut4mA().withOpenDrain(pin)

  case class sg13cmos5l_IOPadInOut4mA() extends BlackBox {
    val c2p = in(Bool())
    val c2p_en = in(Bool())
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

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
  def apply() = sg13cmos5l_IOPadInOut16mA()
  def apply(pin: TriState[Bool]) = sg13cmos5l_IOPadInOut16mA().withTriState(pin)
  def apply(pin: ReadableOpenDrain[Bool]) = sg13cmos5l_IOPadInOut16mA().withOpenDrain(pin)

  case class sg13cmos5l_IOPadInOut16mA() extends BlackBox {
    val c2p = in(Bool())
    val c2p_en = in(Bool())
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

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
  def apply() = sg13cmos5l_IOPadInOut30mA()
  def apply(pin: TriState[Bool]) = sg13cmos5l_IOPadInOut30mA().withTriState(pin)
  def apply(pin: ReadableOpenDrain[Bool]) = sg13cmos5l_IOPadInOut30mA().withOpenDrain(pin)

  case class sg13cmos5l_IOPadInOut30mA() extends BlackBox {
    val c2p = in(Bool())
    val c2p_en = in(Bool())
    val p2c = out(Bool())
    val pad = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

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
  def apply() = sg13cmos5l_IOPadAnalog()

  case class sg13cmos5l_IOPadAnalog() extends BlackBox {
    val pad = inout(Analog(Bool()))
    val padres = inout(Analog(Bool()))

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/ihp/sg13cmos5l/IO.v"
    )

    padres <> pad
  }
}

object IOPadIOVss {
  def apply() = sg13cmos5l_IOPadIOVss()

  case class sg13cmos5l_IOPadIOVss() extends BlackBox {}
}

object IOPadIOVdd {
  def apply() = sg13cmos5l_IOPadIOVdd()

  case class sg13cmos5l_IOPadIOVdd() extends BlackBox {}
}

object IOPadVss {
  def apply() = sg13cmos5l_IOPadVss()

  case class sg13cmos5l_IOPadVss() extends BlackBox {}
}

object IOPadVdd {
  def apply() = sg13cmos5l_IOPadVdd()

  case class sg13cmos5l_IOPadVdd() extends BlackBox {}
}
