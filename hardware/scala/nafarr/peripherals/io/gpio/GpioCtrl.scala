package nafarr.peripherals.io.gpio

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import spinal.lib.io.{TriStateArray, TriState}
import nafarr.IpIdentification

object GpioCtrl {
  def apply(p: Parameter = Parameter.default()) = GpioCtrl(p)

  case class Parameter(
      io: Gpio.Parameter,
      readBufferDepth: Int = 0,
      var output: Seq[Int] = null,
      var input: Seq[Int] = null,
      var interrupt: Seq[Int] = null,
      invertWriteEnable: Boolean = false
  ) {
    if (output == null)
      output = (0 until io.width)
    if (input == null)
      input = (0 until io.width)
    if (interrupt == null)
      interrupt = (0 until io.width)
  }
  object Parameter {
    def default(width: Int = 32, invertWriteEnable: Boolean = false) =
      Parameter(Gpio.Parameter(width), 1, null, null, null, invertWriteEnable)
    def noInterrupt(width: Int = 32, invertWriteEnable: Boolean = false) =
      Parameter(Gpio.Parameter(width), 1, null, null, Seq[Int](), invertWriteEnable)
    def onlyOutput(width: Int = 32, invertWriteEnable: Boolean = false) =
      Parameter(Gpio.Parameter(width), 0, null, Seq[Int](), Seq[Int](), invertWriteEnable)
    def onlyInput(width: Int = 32) =
      Parameter(Gpio.Parameter(width), 0, Seq[Int](), null, null)
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
    val offset = idCtrl.length

    val banks = (p.io.width / 32.0).ceil.toInt
    busCtrl.read(B(banks, 16 bits) ## B(p.io.width, 16 bits), offset)

    for (bank <- 0 until banks) {
      val pins = if (bank < banks - 1) 32 else if (p.io.width % 32 == 0) 32 else p.io.width % 32
      val baseAddr = offset + 4 + bank * 44
      val inputAddr = baseAddr
      val outputAddr = baseAddr + 4
      val directionAddr = baseAddr + 8

      val irqHighCtrl = new InterruptCtrl(pins)
      irqHighCtrl.driveFrom(busCtrl, baseAddr + 12)
      val irqLowCtrl = new InterruptCtrl(pins)
      irqLowCtrl.driveFrom(busCtrl, baseAddr + 20)
      val irqRiseCtrl = new InterruptCtrl(pins)
      irqRiseCtrl.driveFrom(busCtrl, baseAddr + 28)
      val irqFallCtrl = new InterruptCtrl(pins)
      irqFallCtrl.driveFrom(busCtrl, baseAddr + 36)

      for (i <- 0 until pins) {
        val pin = bank * 32 + i
        // IO registers
        if (p.input.contains(pin))
          busCtrl.read(ctrl.value(pin), inputAddr, i)
        if (p.output.contains(pin)) {
          busCtrl.driveAndRead(ctrl.config.write(pin), outputAddr, i).init(False)
        } else {
          busCtrl.read(False, outputAddr, i)
          ctrl.config.write(pin) := False
        }
        if (p.output.contains(pin) && p.input.contains(in)) {
          busCtrl.driveAndRead(ctrl.config.direction(pin), directionAddr, i).init(False)
        } else {
          val direction = RegInit(Bool(p.output.contains(pin)))
          direction.allowUnsetRegToAvoidLatch
          busCtrl.read(direction, directionAddr, i)
          ctrl.config.direction(pin) := direction
        }
        // Interrupt controller
        if (p.interrupt.contains(pin)) {
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
