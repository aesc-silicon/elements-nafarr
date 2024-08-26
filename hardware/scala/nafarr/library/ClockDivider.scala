package nafarr.library

import spinal.core._
import spinal.lib._

/*
 * This module will generate a high signal on io.tick for one cycle as soon as the
 * counter reached zero. Please keep in mind it will count for io.value + 1 cycles.
 */
case class ClockDivider(width: Int) extends Component {
  val io = new Bundle {
    val value = in(UInt(width bits))
    val reload = in(Bool)
    val tick = out(Bool)
  }

  val counter = Reg(UInt(width bits)).init(0)
  val tick = counter === 0
  def reload() = counter := io.value

  counter := counter - 1
  when(tick || io.reload) {
    this.reload()
  }
  io.tick := tick
}
