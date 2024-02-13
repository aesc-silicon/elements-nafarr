package nafarr.peripherals.com.spi

import spinal.core._
import spinal.lib._
import spinal.lib.io.{TriStateArray, TriState}

object Spi {
  case class Parameter(csWidth: Int) {}

  case class Io(p: Spi.Parameter) extends Bundle with IMasterSlave {
    val cs = Bits(p.csWidth bits)
    val sclk = Bool
    // dq[0] - mosi
    // dp[1] - miso
    val dq = TriStateArray(2 bits)

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
