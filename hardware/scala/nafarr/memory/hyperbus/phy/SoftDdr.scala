// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.phy

import spinal.core._
import spinal.lib._

object SoftDdr {

  private def fallingEdge: ClockDomain = {
    val cd = ClockDomain.current
    cd.copy(config = cd.config.copy(clockEdge = FALLING))
  }

  case class Output(width: Int = 1) extends Component {
    val io = new Bundle {
      val rise = in Bits (width bits)
      val fall = in Bits (width bits)
      val q = out Bits (width bits)
    }
    val riseReg = RegNext(io.rise)
    val fallReg = fallingEdge(RegNext(io.fall))
    io.q := Mux(ClockDomain.current.readClockWire, riseReg, fallReg)
  }

  case class Input(width: Int = 1) extends Component {
    val io = new Bundle {
      val d = in Bits (width bits)
      val rise = out Bits (width bits)
      val fall = out Bits (width bits)
    }
    io.rise := RegNext(io.d)
    io.fall := fallingEdge(RegNext(io.d))
  }
}
