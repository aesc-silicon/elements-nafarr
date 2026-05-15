// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.gpio

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import spinal.lib.io.{TriStateArray, TriState}
import nafarr.IpIdentification

object GpioCtrl {
  def apply(p: Parameter = Parameter.default()) = GpioCtrl(p)

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }

  class Regs(base: BigInt) {
    val info = base + 0x00
    private def bankBase(bank: Int): BigInt = base + 0x04 + bank * 0x2c
    def input(bank: Int) = bankBase(bank) + 0x00
    def output(bank: Int) = bankBase(bank) + 0x04
    def direction(bank: Int) = bankBase(bank) + 0x08
    def highPending(bank: Int) = bankBase(bank) + 0x0c
    def highEnable(bank: Int) = bankBase(bank) + 0x10
    def lowPending(bank: Int) = bankBase(bank) + 0x14
    def lowEnable(bank: Int) = bankBase(bank) + 0x18
    def risePending(bank: Int) = bankBase(bank) + 0x1c
    def riseEnable(bank: Int) = bankBase(bank) + 0x20
    def fallPending(bank: Int) = bankBase(bank) + 0x24
    def fallEnable(bank: Int) = bankBase(bank) + 0x28
  }

  case class Parameter(
      io: Gpio.Parameter,
      readBufferDepth: Int = 0,
      output: Option[Seq[Int]] = None,
      input: Option[Seq[Int]] = None,
      interrupt: Option[Seq[Int]] = None,
      invertWriteEnable: Boolean = false
  )
  object Parameter {
    def default(width: Int = 32, invertWriteEnable: Boolean = false) =
      Parameter(Gpio.Parameter(width), 1, invertWriteEnable = invertWriteEnable)
    def noInterrupt(width: Int = 32, invertWriteEnable: Boolean = false) =
      Parameter(
        Gpio.Parameter(width),
        1,
        interrupt = Some(Seq.empty),
        invertWriteEnable = invertWriteEnable
      )
    def onlyOutput(width: Int = 32, invertWriteEnable: Boolean = false) =
      Parameter(
        Gpio.Parameter(width),
        0,
        input = Some(Seq.empty),
        interrupt = Some(Seq.empty),
        invertWriteEnable = invertWriteEnable
      )
    def onlyInput(width: Int = 32) =
      Parameter(Gpio.Parameter(width), 0, output = Some(Seq.empty))
  }

  case class Config(p: Parameter) extends Bundle {
    val write = Bits(p.io.width bits)
    val direction = Bits(p.io.width bits)
  }
  case class InterruptConfig(p: Parameter) extends Bundle {
    val valid = out(Bits(p.io.width bits))
    val pending = in(Bits(p.io.width bits))
  }

  case class Io(p: Parameter) extends Bundle {
    val gpio = Gpio.Io(p.io)
    val config = in(Config(p))
    val value = out(Bits(p.io.width bits))
    val interrupt = out(Bool)
    val irqHigh = InterruptConfig(p)
    val irqLow = InterruptConfig(p)
    val irqRise = InterruptConfig(p)
    val irqFall = InterruptConfig(p)
  }

  case class GpioCtrl(p: Parameter) extends Component {
    val io = Io(p)

    val synchronized = Bits(io.gpio.pins.read.getWidth bits)
    if (p.readBufferDepth > 0) {
      io.value := BufferCC(io.gpio.pins.read, bufferDepth = Some(p.readBufferDepth))
      synchronized := io.value
    } else {
      io.value := io.gpio.pins.read
      synchronized := BufferCC(io.gpio.pins.read)
    }
    io.gpio.pins.write := io.config.write
    if (p.invertWriteEnable) {
      io.gpio.pins.writeEnable := ~io.config.direction
    } else {
      io.gpio.pins.writeEnable := io.config.direction
    }

    val last = RegNext(synchronized)

    io.irqHigh.valid := synchronized
    io.irqLow.valid := ~synchronized
    io.irqRise.valid := (synchronized & ~last)
    io.irqFall.valid := (~synchronized & last)

    io.interrupt := (io.irqHigh.pending | io.irqLow.pending |
      io.irqRise.pending | io.irqFall.pending).orR
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Gpio, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)

    val regs = Regs(idCtrl.length)

    val banks = (p.io.width / 32.0).ceil.toInt
    busCtrl.read(B(banks, 16 bits) ## B(p.io.width, 16 bits), regs.info)

    for (bank <- 0 until banks) {
      val pins = if (bank < banks - 1) 32 else if (p.io.width % 32 == 0) 32 else p.io.width % 32

      val irqHighCtrl = new InterruptCtrl(pins)
      irqHighCtrl.driveFrom(busCtrl, regs.highPending(bank).toInt)
      val irqLowCtrl = new InterruptCtrl(pins)
      irqLowCtrl.driveFrom(busCtrl, regs.lowPending(bank).toInt)
      val irqRiseCtrl = new InterruptCtrl(pins)
      irqRiseCtrl.driveFrom(busCtrl, regs.risePending(bank).toInt)
      val irqFallCtrl = new InterruptCtrl(pins)
      irqFallCtrl.driveFrom(busCtrl, regs.fallPending(bank).toInt)

      for (i <- 0 until pins) {
        val pin = bank * 32 + i
        // IO registers
        if (p.input.forall(_.contains(pin)))
          busCtrl.read(ctrl.value(pin), regs.input(bank), i)
        if (p.output.forall(_.contains(pin))) {
          busCtrl.driveAndRead(ctrl.config.write(pin), regs.output(bank), i).init(False)
        } else {
          busCtrl.read(False, regs.output(bank), i)
          ctrl.config.write(pin) := False
        }
        if (p.output.forall(_.contains(pin)) && p.input.forall(_.contains(pin))) {
          busCtrl.driveAndRead(ctrl.config.direction(pin), regs.direction(bank), i).init(False)
        } else {
          val direction = RegInit(Bool(p.output.forall(_.contains(pin))))
          direction.allowUnsetRegToAvoidLatch
          busCtrl.read(direction, regs.direction(bank), i)
          ctrl.config.direction(pin) := direction
        }
        // Interrupt controller
        if (p.interrupt.forall(_.contains(pin))) {
          irqHighCtrl.io.inputs(i) := ctrl.irqHigh.valid(pin)
          irqLowCtrl.io.inputs(i) := ctrl.irqLow.valid(pin)
          irqRiseCtrl.io.inputs(i) := ctrl.irqRise.valid(pin)
          irqFallCtrl.io.inputs(i) := ctrl.irqFall.valid(pin)
        } else {
          irqHighCtrl.io.inputs(i) := False
          irqLowCtrl.io.inputs(i) := False
          irqRiseCtrl.io.inputs(i) := False
          irqFallCtrl.io.inputs(i) := False
        }
        ctrl.irqHigh.pending(pin) := irqHighCtrl.io.pendings(i)
        ctrl.irqLow.pending(pin) := irqLowCtrl.io.pendings(i)
        ctrl.irqRise.pending(pin) := irqRiseCtrl.io.pendings(i)
        ctrl.irqFall.pending(pin) := irqFallCtrl.io.pendings(i)
      }
    }
  }
}
