// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.spi

import spinal.core._
import spinal.lib._

object SpiXipController {

  object GenericInterface {
    case class Cmd() extends Bundle {
      val addr = UInt(24 bits)
    }

    case class Rsp() extends Bundle {
      val data = Bits(32 bits)
    }
  }
}
