package nafarr.peripherals.com.uart

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import nafarr.IpIdentification
import nafarr.library.ClockDivider

object UartCtrl {
  def apply(p: Parameter = Parameter.default()) = UartCtrl(p)

  case class InitParameter(
      baudrate: Int = 0,
      dataLength: Int = 0,
      parity: Uart.ParityType.E = null,
      stop: Uart.StopType.E = null
  ) {
    def getBaudPeriod() = {
      (1000 * 1000 * 1000) / baudrate
    }
  }
  object InitParameter {
    def default(baudrate: Int) =
      InitParameter(baudrate, 7, Uart.ParityType.NONE, Uart.StopType.ONE)
    def disabled = InitParameter()
  }

  case class PermissionParameter(
      busCanWriteClockDividerConfig: Boolean,
      busCanWriteFrameConfig: Boolean
  ) {
    require(busCanWriteClockDividerConfig)
    require(busCanWriteFrameConfig)
  }
  object PermissionParameter {
    def granted = PermissionParameter(true, true)
    def restricted = PermissionParameter(false, false)
  }

  case class MemoryMappedParameter(
      txFifoDepth: Int,
      rxFifoDepth: Int
  ) {
    require(txFifoDepth > 0 && txFifoDepth < 256)
    require(rxFifoDepth > 0 && rxFifoDepth < 256)
  }
  object MemoryMappedParameter {
    def lightweight = MemoryMappedParameter(4, 4)
    def default = MemoryMappedParameter(16, 16)
    def full = MemoryMappedParameter(64, 64)
  }

  case class Parameter(
      init: InitParameter = InitParameter.disabled,
      permission: PermissionParameter = PermissionParameter.granted,
      memory: MemoryMappedParameter = MemoryMappedParameter.default,
      clockDividerWidth: Int = 20,
      dataWidthMax: Int = 9,
      dataWidthMin: Int = 5,
      preSamplingSize: Int = 1,
      samplingSize: Int = 5,
      postSamplingSize: Int = 2,
      interrupt: Boolean = true,
      flowControl: Boolean = true
  ) {
    require(dataWidthMax < 10 && dataWidthMax > 4)
    require(dataWidthMin < 10 && dataWidthMin > 4)
    require(preSamplingSize > 0)
    require(samplingSize > 0)
    require(postSamplingSize > 0)
    if ((samplingSize % 2) == 0)
      SpinalWarning(
        s"Majority vode requires an uneven sampling size at ${ScalaLocated.short}."
      )

    require(
      (init != null && init.baudrate > 0) ||
        (permission != null && permission.busCanWriteClockDividerConfig),
      "Clock divider value not set. Either configure a init baudrate or grant bus write access."
    )

    require(
      (init != null && init.dataLength > 0 && init.parity != null && init.stop != null) ||
        (permission != null && permission.busCanWriteFrameConfig),
      "Data frame values not set. Either configure an init or grant bus write access."
    )

    val samplesPerBit = preSamplingSize + samplingSize + postSamplingSize
    def getClockDivider(baudrate: Int): Int = {
      return (ClockDomain.current.frequency.getValue / baudrate / samplesPerBit).toInt - 1
    }
  }
  object Parameter {
    def lightweight = Parameter(
      init = InitParameter.disabled,
      permission = PermissionParameter.granted,
      memory = MemoryMappedParameter.lightweight
    )
    def default(baudrate: Int = 115200) = Parameter(
      init = InitParameter.disabled,
      permission = PermissionParameter.granted,
      memory = MemoryMappedParameter.default
    )
    def full(baudrate: Int = 115200) = Parameter(
      init = InitParameter.default(baudrate),
      permission = PermissionParameter.granted,
      memory = MemoryMappedParameter.full
    )
  }

  case class Config(p: Parameter) extends Bundle {
    val clockDivider = UInt(p.clockDividerWidth bits)
    val clockDividerReload = Bool
  }
  case class FrameConfig(p: Parameter) extends Bundle {
    val parity = Uart.ParityType()
    val stop = Uart.StopType()
    val dataLength = UInt(log2Up(p.dataWidthMax) bits)
  }

  case class Io(p: Parameter) extends Bundle {
    val config = in(Config(p))
    val frameConfig = in(FrameConfig(p))
    val uart = master(Uart.Io(p))
    val interrupt = out(Bool)
    val pendingInterrupts = in(Bits(2 bits))
    val write = slave(Stream(Bits(p.dataWidthMax bits)))
    val read = master(Stream(Bits(p.dataWidthMax bits)))
    val readIsFull = in(Bool)
  }

  case class UartCtrl(p: Parameter) extends Component {
    val io = Io(p)

    val clockDivider = new ClockDivider(p.clockDividerWidth)
    clockDivider.io.value := io.config.clockDivider
    clockDivider.io.reload := io.config.clockDividerReload

    io.interrupt := io.pendingInterrupts.orR

    val tx = UartCtrlTx(p)
    tx.io.config <> io.frameConfig
    tx.io.samplingTick := clockDivider.io.tick
    tx.io.write << io.write
    io.uart.txd <> tx.io.txd

    val rx = UartCtrlRx(p)
    rx.io.config <> io.frameConfig
    rx.io.samplingTick := clockDivider.io.tick
    io.read << rx.io.read
    io.uart.rxd <> rx.io.rxd

    if (p.flowControl) {
      io.uart.rts := io.readIsFull
      tx.io.cts := !io.uart.cts
    } else {
      io.uart.rts := False
      tx.io.cts := True
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Uart, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length

    busCtrl.read(
      B(0, 8 bits) ## B(p.dataWidthMin, 8 bits) ## B(p.dataWidthMax, 8 bits) ##
        B(p.clockDividerWidth, 8 bits),
      staticOffset
    )

    busCtrl.read(
      B(0, 8 bits) ## B(p.preSamplingSize, 8 bits) ## B(p.samplingSize, 8 bits) ##
        B(p.postSamplingSize, 8 bits),
      staticOffset + 0x4
    )

    busCtrl.read(
      B(0, 16 bits) ## B(p.memory.txFifoDepth, 8 bits) ## B(p.memory.rxFifoDepth, 8 bits),
      staticOffset + 0x8
    )

    val permissionBits =
      Bool(p.permission.busCanWriteFrameConfig) ## Bool(p.permission.busCanWriteClockDividerConfig)
    busCtrl.read(B(0, 32 - permissionBits.getWidth bits) ## permissionBits, staticOffset + 0xc)
    val regOffset = staticOffset + 0x10

    val tx = new Area {
      val streamUnbuffered =
        busCtrl.createAndDriveFlow(Bits(p.dataWidthMax bits), address = regOffset + 0x00).toStream
      val (stream, fifoOccupancy) =
        streamUnbuffered.queueWithOccupancy(p.memory.txFifoDepth)
      val fifoVacancy = p.memory.txFifoDepth - fifoOccupancy
      busCtrl.read(fifoVacancy, address = regOffset + 0x04, bitOffset = 16)
      ctrl.write << stream
      streamUnbuffered.ready.allowPruning()
    }

    val rx = new Area {
      val (stream, fifoOccupancy) =
        ctrl.read.queueWithOccupancy(p.memory.rxFifoDepth)
      ctrl.readIsFull := fifoOccupancy >= p.memory.rxFifoDepth - 1
      busCtrl.readStreamNonBlocking(
        stream,
        address = regOffset + 0x0,
        validBitOffset = 16,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = regOffset + 0x04, bitOffset = 24)
    }

    val config = new Area {
      val cfg = Reg(ctrl.config)

      if (p.init != null && p.init.baudrate != 0)
        cfg.clockDivider.init(p.getClockDivider(p.init.baudrate))
      if (p.permission != null && p.permission.busCanWriteClockDividerConfig)
        busCtrl.write(cfg.clockDivider, address = regOffset + 0x08)
      busCtrl.read(cfg.clockDivider, regOffset + 0x08)

      cfg.clockDividerReload := False
      busCtrl.onWrite(regOffset + 0x08) {
        cfg.clockDividerReload := True
      }

      val frameCfg = Reg(ctrl.frameConfig)

      if (p.init != null && p.init.dataLength != 0)
        frameCfg.dataLength.init(p.init.dataLength)
      if (p.init != null && p.init.parity != null)
        frameCfg.parity.init(p.init.parity)
      if (p.init != null && p.init.stop != null)
        frameCfg.stop.init(p.init.stop)

      if (p.permission != null && p.permission.busCanWriteFrameConfig) {
        busCtrl.write(frameCfg.dataLength, address = regOffset + 0x0c, bitOffset = 0)
        busCtrl.write(frameCfg.parity, address = regOffset + 0x0c, bitOffset = 8)
        busCtrl.write(frameCfg.stop, address = regOffset + 0x0c, bitOffset = 16)
      }
      busCtrl.read(frameCfg.dataLength, address = regOffset + 0x0c, bitOffset = 0)
      busCtrl.read(frameCfg.parity, address = regOffset + 0x0c, bitOffset = 8)
      busCtrl.read(frameCfg.stop, address = regOffset + 0x0c, bitOffset = 16)

      ctrl.config <> cfg
      ctrl.frameConfig <> frameCfg
    }

    val interrupt = new Area {
      if (p.interrupt) {
        val txOccupancyTrigger = Reg(cloneOf(tx.fifoOccupancy))
        val txPreviousOccupancy = RegNext(tx.fifoOccupancy)
        busCtrl.readAndWrite(txOccupancyTrigger, regOffset + 0x10)
        val txTrigger =
          tx.fifoOccupancy === txOccupancyTrigger && txPreviousOccupancy === (txOccupancyTrigger + 1)

        val irqCtrl = new InterruptCtrl(2)
        irqCtrl.driveFrom(busCtrl, regOffset + 0x14)
        irqCtrl.io.inputs(0) := txTrigger
        irqCtrl.io.inputs(1) := ctrl.read.valid
        ctrl.pendingInterrupts := irqCtrl.io.pendings
      } else {
        ctrl.pendingInterrupts := 0
      }
    }
  }
}
