// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.spi

import spinal.core._
import spinal.lib._
import spinal.lib.io.{TriStateArray, TriState}

object Spi {
  case class Parameter(
      csWidth: Int,
      dataWidth: Int
  ) {
    require(csWidth > 0 && csWidth < 17, "1 up to 16 chip select pins are supported")
    require(dataWidth == 2 || dataWidth == 4, "Only single and quad mode are supported")
  }

  case class Io(p: Spi.Parameter) extends Bundle with IMasterSlave {
    val cs = Bits(p.csWidth bits)
    val sclk = Bool
    // Single:
    //  dq[0] - mosi
    //  dp[1] - miso
    val dq = TriStateArray(p.dataWidth bits)

    override def asMaster(): Unit = {
      out(cs)
      out(sclk)
      master(dq)
    }
    override def asSlave(): Unit = {
      in(cs)
      in(sclk)
      slave(dq)
    }
  }
}
