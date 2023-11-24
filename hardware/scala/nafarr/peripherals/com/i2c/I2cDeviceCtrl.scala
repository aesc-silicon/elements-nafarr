package nafarr.peripherals.com.i2c

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl

object I2cDeviceCtrl {
  def apply(p: I2cCtrl.Parameter = I2cCtrl.Parameter.default) = I2cDeviceCtrl(p)

  object State extends SpinalEnum {
    val IDLE, REQ, RSP = newElement()
  }

  case class Config(p: I2cCtrl.Parameter) extends Bundle {
    val clockDivider = UInt(p.timerWidth bits)
    val timeout = UInt(p.timeoutWidth bits)
    val deviceAddr = Bits(p.addressWidth bits)
  }

  case class Io(p: I2cCtrl.Parameter) extends Bundle {
    val config = in(Config(p))
    val i2c = slave(I2c.Io(p.io))
    val interrupts = in(Bits(p.io.interrupts bits))
    val cmd = master(Stream(I2cDevice.Cmd(p)))
    val rsp = slave(Stream(I2cDevice.Rsp(p)))
  }

  case class IoFilter(i2c: I2c.Io, clockDivider: UInt, p: I2cCtrl.Parameter) extends Area {
    val timer = new Area {
      val counter = Reg(UInt(p.timerWidth bits)) init (0)
      val tick = counter === 0

      counter := counter - 1
      when(tick) {
        counter := clockDivider
      }
    }

    val sampler = new Area {
      val sclSync = BufferCC(i2c.scl.read, True)
      val sdaSync = BufferCC(i2c.sda.read, True)

      val sclSamples =
        History(that = sclSync, range = 0 until p.samplerWidth, when = timer.tick, init = True)
      val sdaSamples =
        History(that = sdaSync, range = 0 until p.samplerWidth, when = timer.tick, init = True)
    }

    val sda, scl = RegInit(True)
    when(timer.tick) {
      when(sampler.sdaSamples.map(_ =/= sda).andR) {
        sda := sampler.sdaSamples.last
      }
      when(sampler.sclSamples.map(_ =/= scl).andR) {
        scl := sampler.sclSamples.last
      }
    }
  }

  case class I2cDeviceCtrl(p: I2cCtrl.Parameter) extends Component {
    val io = Io(p)

    val filter = new IoFilter(io.i2c, io.config.clockDivider, p)

    val sclEdge = filter.scl.edges(True)
    val sdaEdge = filter.sda.edges(True)

    val detector = new Area {
      val start = filter.scl && sdaEdge.fall
      val stop = filter.scl && sdaEdge.rise
    }

    val timeout = new Area {
      val value = Reg(UInt(p.timeoutWidth bits)).init(0)
      val transmission = Reg(Bool).init(False)

      def trigger = value === io.config.timeout

      when(detector.start) {
        transmission := True
      }
      when(detector.stop) {
        transmission := False
      }
      when(detector.start || sclEdge.rise || sclEdge.fall) {
        value := 0
      }
      when(filter.timer.tick && transmission) {
        value := value + 1
      }
    }

    val ctrl = new Area {
      val state = RegInit(State.IDLE)
      val shiftRegister = Reg(Bits(11 bits))
      val bitCounter = Reg(UInt(5 bits))
      val frameCounter = Reg(UInt(4 bits))
      val transmission = Reg(Bool).init(False)

      val sdaWrite = Reg(Bool).init(False)

      val address = shiftRegister(p.addressWidth downto 1)
      val data = shiftRegister(7 downto 0)
      val read = Reg(Bool())
      val write = !read
      val response = new Area {
        val error = Reg(Bool())
        val data = Reg(Bits(8 bits))
      }
      val cmdLock = Reg(Bool())

      def isRWBit = bitCounter === 7
      def isAckBit = bitCounter === 8
      def isAddrFrame = frameCounter === 0
      def isRegFrame = frameCounter === 1
      def isDataFrame = !isAddrFrame && !isRegFrame
      def isInFrame = (isAddrFrame && bitCounter < U(p.addressWidth + 1, 5 bits)) ||
        (!isAddrFrame && bitCounter < U(8, 5 bits))
      def sendAck = (isAddrFrame || ((isRegFrame || isDataFrame) && write)) && isAckBit
      def sendData = (read && !isAddrFrame && isInFrame)

      when(detector.start || detector.stop || timeout.trigger) {
        state := State.IDLE
        bitCounter := U(bitCounter.range -> true)
        frameCounter := 0
        read := False
        response.error := True
        when(detector.start) {
          transmission := True
        } otherwise {
          transmission := False
        }
      }

      sdaWrite := False
      when(sendAck) {
        when(isAddrFrame && address === io.config.deviceAddr) {
          sdaWrite := True
        }
        when((isRegFrame || isDataFrame) && !response.error) {
          sdaWrite := True
        }
      }
      when(sendData) {
        sdaWrite := !response.data.reversed(bitCounter.resize(3))
      }

      when(sclEdge.rise && transmission) {
        when(isInFrame) {
          shiftRegister := shiftRegister(shiftRegister.high - 1 downto 0) ## filter.sda
          when(isAddrFrame && isRWBit) {
            read := filter.sda
          }
        }
      }
      when(sclEdge.fall && transmission) {
        bitCounter := bitCounter + 1
        cmdLock := False
        when(isAckBit) {
          bitCounter := 0
          frameCounter := frameCounter + 1
        }
      }

      io.cmd.valid := False
      io.cmd.payload.data := data
      io.cmd.payload.reg := isRegFrame
      io.cmd.payload.read := read
      io.rsp.ready := False
      switch(state) {
        is(State.IDLE) {
          when(
            !cmdLock && isAckBit && (isRegFrame || (read && isAddrFrame) || (write && isDataFrame))
          ) {
            state := State.REQ
          }
        }
        is(State.REQ) {
          io.cmd.valid := True
          when(io.cmd.fire) {
            state := State.RSP
          }
        }
        is(State.RSP) {
          when(io.rsp.valid) {
            cmdLock := True
            io.rsp.ready := True
            response.data := io.rsp.payload.data
            response.error := io.rsp.payload.error
            state := State.IDLE
          }
        }
      }
    }
    io.i2c.scl.write := False
    io.i2c.sda.write := ctrl.sdaWrite
    if (p.io.interrupts > 0) {
      io.i2c.interrupts := io.interrupts
    }
  }
}
