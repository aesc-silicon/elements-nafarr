// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.blackboxes.ihp.sg13g2

import spinal.core._
import spinal.lib.io.{TriState, ReadableOpenDrain}

import nafarr.blackboxes.ihp.common.Edge

object IhpCmosIo {
  def apply(edge: Edge.Value, number: Int) = new IhpCmosIo(edge, number)
  def apply(edge: Edge.Value, number: Int, clockGroup: String) =
    new IhpCmosIo(edge, number, clockGroup)

  class IhpCmosIo(val edge: Edge.Value, val number: Int, val clockGroup: String = "")
      extends Bundle {
    val PAD = inout(Analog(Bool()))

    var cell = ""
    var cellName = ""
    var clockPort = ""
    def clockInput = clockPort.contains("in")
    def clockOutput = clockPort.contains("out")

    def <>(that: IOPadIn.sg13g2_IOPadIn) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadIn"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      clockPort = "input_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadOut4mA.sg13g2_IOPadOut4mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadOut4mA"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      clockPort = "output_4mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadOut16mA.sg13g2_IOPadOut16mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadOut16mA"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      clockPort = "output_16mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadOut30mA.sg13g2_IOPadOut30mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadOut30mA"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      clockPort = "output_30mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadInOut4mA.sg13g2_IOPadInOut4mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadInOut4mA"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      clockPort = "inout_4mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadInOut16mA.sg13g2_IOPadInOut16mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadInOut16mA"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      clockPort = "inout_16mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadInOut30mA.sg13g2_IOPadInOut30mA) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadInOut30mA"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      clockPort = "inout_30mA_ports"
      that.setName(this.cellName)
    }
    def <>(that: IOPadAnalog.sg13g2_IOPadAnalog) = {
      that.pad := this.PAD
      cell = "sg13g2_IOPadAnalog"
      cellName = s"sg13g2_IOPad_${this.getName()}"
      that.setName(this.cellName)
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
  def apply(pin: ReadableOpenDrain[Bool]) = sg13g2_IOPadInOut4mA().withOpenDrain(pin)

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
  def apply(pin: ReadableOpenDrain[Bool]) = sg13g2_IOPadInOut16mA().withOpenDrain(pin)

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
  def apply(pin: ReadableOpenDrain[Bool]) = sg13g2_IOPadInOut30mA().withOpenDrain(pin)

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
