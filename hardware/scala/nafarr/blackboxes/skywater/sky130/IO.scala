package nafarr.blackboxes.skywater.sky130

import spinal.core._
import spinal.lib.io.{TriState, ReadableOpenDrain}
import spinal.lib.History

object Sky130CmosIo {
  def apply(edge: String, number: Int) = new Sky130CmosIo(edge, number)

  class Sky130CmosIo(val edge: String, val offset: Double) extends Bundle {
    val PAD = inout(Analog(Bool()))

    var cell = ""
    def getCellName = s"${cell}_${edge}_${offset.intValue()}"

    def <>(that: top_gpiov2.sky130_fd_io__top_gpiov2) = {
      that.PAD := this.PAD
      cell = "sky130_fd_io__top_gpiov2"
      that.setName(this.getCellName)
    }
  }
}

object top_gpiov2 {
  def apply() = sky130_fd_io__top_gpiov2()
  def apply(pin: TriState[Bool]) = sky130_fd_io__top_gpiov2().withTriState(pin)
  def apply(pin: ReadableOpenDrain[Bool]) = sky130_fd_io__top_gpiov2().withReadableOpenDrain(pin)

  case class sky130_fd_io__top_gpiov2() extends BlackBox {
    val PAD = inout(Analog(Bool()))
    /* low leakage mode */
    val ENABLE_H = in(Bool())
    val ENABLE_INP_H = in(Bool())
    val HLD_H_N = in(Bool())

    val HLD_OVR = in(Bool())
    val SLOW = in(Bool())
    val VTRIP_SEL = in(Bool())
    val INP_DIS = in(Bool())
    val DM = in(Bits(3 bits))

    val OE_N = in(Bool())
    val OUT = in(Bool())
    val IN = out(Bool())
    val IN_H = out(Bool())

    val PAD_A_ESD_0_H = inout(Analog(Bool()))
    val PAD_A_ESD_1_H = inout(Analog(Bool()))
    val PAD_A_NOESD_H = inout(Analog(Bool()))

    val ANALOG_EN = in(Bool())
    val ANALOG_SEL = in(Bool())
    val ANALOG_POL = in(Bool())

    val AMUXBUS_A = inout(Analog(Bool()))
    val AMUXBUS_B = inout(Analog(Bool()))

    val TIE_HI_ESD = out(Bool())
    val TIE_LO_ESD = out(Bool())

    val ENABLE_VDDA_H = in(Bool())
    val ENABLE_VSWITCH_H = in(Bool())
    val ENABLE_VDDIO = in(Bool())

    val analogFalse = Analog(Bool)
    analogFalse := False
    val analogTrue = Analog(Bool)
    analogTrue := True

    TIE_HI_ESD := analogTrue
    TIE_LO_ESD := analogFalse

    when(OE_N) {
      PAD := OUT
    }
    IN := PAD

    def connectDefault() = {
      this.ENABLE_H := this.TIE_HI_ESD
      this.ENABLE_INP_H := this.TIE_HI_ESD

      /* low leakage mode disabled for now */
      this.HLD_H_N := this.TIE_HI_ESD
      this.HLD_OVR := this.TIE_LO_ESD

      this.SLOW := this.TIE_LO_ESD
      this.VTRIP_SEL := this.TIE_LO_ESD

      this.INP_DIS := this.TIE_LO_ESD
      this.DM := this.TIE_HI_ESD ## this.TIE_HI_ESD ## this.TIE_HI_ESD

      this.ANALOG_EN := this.TIE_LO_ESD
      this.ANALOG_SEL := this.TIE_LO_ESD
      this.ANALOG_POL := this.TIE_LO_ESD

      this.ENABLE_VDDA_H := this.TIE_HI_ESD
      this.ENABLE_VSWITCH_H := this.TIE_LO_ESD
      this.ENABLE_VDDIO := this.TIE_HI_ESD

      this
    }

    def withTriState(pin: TriState[Bool]) = {
      this.connectDefault()
      this.OUT := pin.write
      this.OE_N := pin.writeEnable
      pin.read := this.IN
      this
    }
    def withReadableOpenDrain(pin: ReadableOpenDrain[Bool]) = {
      this.connectDefault()
      this.OUT := this.TIE_LO_ESD
      this.OE_N := pin.write
      pin.read := this.IN
      this
    }
    def asInput(pin: Bool) = {
      this.connectDefault()
      this.OUT := this.TIE_LO_ESD
      this.OE_N := this.TIE_HI_ESD
      pin := this.IN
      this
    }
    def asOutput(pin: Bool) = {
      this.connectDefault()
      this.OUT := pin
      this.OE_N := this.TIE_LO_ESD
      this
    }

  }
}
