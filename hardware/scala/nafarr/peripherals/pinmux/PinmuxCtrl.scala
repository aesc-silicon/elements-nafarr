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

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }

  class Regs(base: BigInt) {
    val info = base + 0x00
    private def optionBase(pin: Int): BigInt = base + 0x04 + pin * 0x04
    def option(pin: Int) = optionBase(pin)
  }

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

    val pinsWrite = Vec(Bool(), p.io.width)
    val pinsWriteEnable = Vec(Bool(), p.io.width)
    val inputsRead = Vec(Bool(), p.inputs)

    inputsRead.foreach(_ := False)

    val mappingMap = mapping.toMap
    for (pin <- 0 until p.io.width) {
      mappingMap.get(pin) match {
        case Some(inputs) =>
          pinsWrite(pin) := False
          pinsWriteEnable(pin) := False
          for ((inputIdx, optionIdx) <- inputs.zipWithIndex) {
            when(io.options(pin) === U(optionIdx)) {
              pinsWrite(pin) := io.inputs.write(inputIdx)
              pinsWriteEnable(pin) := io.inputs.writeEnable(inputIdx)
              inputsRead(inputIdx) := io.pins.read(pin)
            }
          }
        case None =>
          pinsWrite(pin) := False
          pinsWriteEnable(pin) := False
      }
    }

    io.pins.write := pinsWrite.asBits
    io.pins.writeEnable := pinsWriteEnable.asBits
    io.inputs.read := inputsRead.asBits
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Pinmux, 1, 1, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    busCtrl.read(B(0, 16 bits) ## B(p.options, 8 bits) ## B(p.io.width, 8 bits), regs.info)

    for (pin <- 0 until p.io.width) {
      val option = Reg(UInt(8 bits)).init(U(0))
      busCtrl.readAndWrite(option, regs.option(pin))
      ctrl.options(pin) := option(0, log2Up(p.options) bits)
    }
  }
}
