// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

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
