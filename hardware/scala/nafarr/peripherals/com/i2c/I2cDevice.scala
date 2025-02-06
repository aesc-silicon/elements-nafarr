package nafarr.peripherals.com.i2c

import spinal.core._
import spinal.lib._

object I2cDevice {
  case class Cmd() extends Bundle {
    val data = Bits(8 bits)
    val reg = Bool
    val read = Bool
  }

  case class Rsp() extends Bundle {
    val data = Bits(8 bits)
    val error = Bool
  }
}
