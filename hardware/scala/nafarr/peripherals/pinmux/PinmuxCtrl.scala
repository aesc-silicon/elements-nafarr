// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.pinmux

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.io.{TriStateArray, TriState}
import spinal.lib.io.ReadableOpenDrain
import nafarr.IpIdentification

import scala.collection.mutable.Map
import scala.collection.mutable.ArrayBuffer

object PinmuxCtrl {
  def apply(p: Parameter, mapping: ArrayBuffer[(Int, List[Int])]) = PinmuxCtrl(p, mapping)

  case class Parameter(io: Pinmux.Parameter, inputs: Int, options: Int) {
    require(io.width <= 255, "Pin width must fit in 8 bits for the width register.")
  }

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
    val idCtrl = IpIdentification(IpIdentification.Ids.Pinmux, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length

    busCtrl.read(B(0, 24 bits) ## B(p.io.width, 8 bits), staticOffset)
    val regOffset = idCtrl.length + 0x4

    for (pin <- 0 until p.io.width) {
      val option = Reg(UInt(8 bits)).init(U(0))
      val muxAddress = pin * 4
      busCtrl.readAndWrite(option, muxAddress + regOffset)
      ctrl.options(pin) := option(0, log2Up(p.options) bits)
    }
  }
}
