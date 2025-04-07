package nafarr.peripherals.pinmux

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.io.{TriStateArray, TriState}
import spinal.lib.io.ReadableOpenDrain

import scala.collection.mutable.Map
import scala.collection.mutable.ArrayBuffer

object PinmuxCtrl {
  def apply(p: Parameter, mapping: ArrayBuffer[(Int, List[Int])]) = PinmuxCtrl(p, mapping)

  case class Parameter(io: Pinmux.Parameter, inputs: Int, options: Int) {}

  case class Io(parameter: Parameter) extends Bundle {
    val pins = master(TriStateArray(parameter.io.width bits))
    val inputs = slave(TriStateArray(parameter.inputs bits))
    val options = in(Vec(UInt(log2Up(parameter.options) bits), parameter.io.width))
  }

  val mapping = Map[String, List[String]]()

  case class PinmuxCtrl(p: Parameter, mapping: ArrayBuffer[(Int, List[Int])]) extends Component {
    val io = Io(p)

    for (index <- 0 until p.inputs) {
      io.inputs(index).read := False
    }

    for ((pin, inputs) <- mapping) {
      val option = io
        .options(pin)
        .muxList(for (index <- 0 until inputs.size) yield (index, io.inputs(inputs(index))))
      io.pins(pin).write := option.write
      io.pins(pin).writeEnable := option.writeEnable

      for ((input, index) <- inputs.zipWithIndex) {
        when(io.options(pin) === U(index)) {
          io.inputs(input).read := io.pins(pin).read
        }
      }
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    for (pin <- 0 until p.io.width) {
      val option = Reg(UInt(log2Up(p.options) bits)).init(U(0))
      val muxAddress = pin * 4
      busCtrl.readAndWrite(option, muxAddress)
      ctrl.options(pin) := option
    }
  }
}
