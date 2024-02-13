package nafarr.peripherals.com.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl

object SpiControllerCtrl {
  def apply(p: SpiCtrl.Parameter = SpiCtrl.Parameter.default()) = SpiControllerCtrl(p)

  case class Config(p: SpiCtrl.Parameter) extends Bundle {
    val clockDivider = UInt(p.timerWidth bits)
    val cs = new Bundle {
      val activeHigh = Bits(p.io.csWidth bits)
      val setup = UInt(p.timerWidth bits)
      val hold = UInt(p.timerWidth bits)
      val disable = UInt(p.timerWidth bits)
    }
  }

  case class ModeConfig(p: SpiCtrl.Parameter) extends Bundle {
    val cpol = Bool
    val cpha = Bool
  }

  case class Io(p: SpiCtrl.Parameter) extends Bundle {
    val config = in(Config(p))
    val modeConfig = in(ModeConfig(p))
    val spi = master(Spi.Io(p.io))
    val interrupt = out(Bool)
    val pendingInterrupts = in(Bits(2 bits))
    val cmd = slave(Stream(SpiController.Cmd(p)))
    val rsp = master(Flow(Bits(p.dataWidth bits)))
  }

  case class SpiControllerCtrl(p: SpiCtrl.Parameter) extends Component {
    val io = Io(p)

    val timer = new Area {
      val counter = Reg(UInt(p.timerWidth bits))
      val reset = False
      val cs = new Area {
        val setupHit = counter === io.config.cs.setup
        val holdHit = counter === io.config.cs.hold
        val disableHit = counter === io.config.cs.disable
      }
      val clockDividerHit = counter === io.config.clockDivider

      counter := counter + 1
      when(reset) {
        counter := 0
      }
    }

    val fsm = new Area {
      val counter = Counter(p.dataWidth * 2)
      val buffer = Reg(Bits(p.dataWidth bits))
      val cs = RegInit(B((1 << p.io.csWidth) - 1, p.io.csWidth bits))

      io.cmd.ready := False
      when(io.cmd.valid) {
        when(io.cmd.isData) {
          when(timer.clockDividerHit) {
            counter.increment()
            timer.reset := True
            io.cmd.ready := counter.willOverflowIfInc
            when(counter.lsb) {
              buffer := (buffer ## io.spi.dq(1).read).resized
            }
          }
        } otherwise {
          when(io.cmd.argsCs.enable) {
            cs(io.cmd.argsCs.index) := False
            when(timer.cs.setupHit) {
              io.cmd.ready := True
            }
          } otherwise {
            when(!counter.lsb) {
              when(timer.cs.holdHit) {
                counter.increment()
                timer.reset := True
              }
            } otherwise {
              cs(io.cmd.argsCs.index) := True
              when(timer.cs.disableHit) {
                io.cmd.ready := True
              }
            }
          }
        }
      }
    }

    // CMD responses
    io.rsp.valid := RegNext(
      io.cmd.fire && io.cmd.isData &&
        io.cmd.argsData.read
    ).init(False)
    io.rsp.payload := fsm.buffer

    // Idle states
    when(!io.cmd.valid || io.cmd.ready) {
      fsm.counter := 0
      timer.reset := True
    }

    io.spi.cs := fsm.cs ^ io.config.cs.activeHigh
    io.spi.sclk := RegNext(
      ((io.cmd.valid && io.cmd.isData) &&
        (fsm.counter.lsb ^ io.modeConfig.cpha)) ^
        io.modeConfig.cpol
    )
    io.spi.dq(0).write := RegNext(io.cmd.argsData.data(p.dataWidth - 1 - (fsm.counter >> 1)))
    io.spi.dq(0).writeEnable := True
    io.spi.dq(1).write := False
    io.spi.dq(1).writeEnable := False
    io.interrupt := io.pendingInterrupts.orR
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: SpiCtrl.Parameter
  ) extends Area {

    val config = new Area {
      val cfg = Reg(ctrl.config)
      cfg.cs.activeHigh.init(0)
      if (p.init.frequency.toLong > 1) {
        val clock = U(
          ClockDomain.current.frequency.getValue.toLong / p.init.frequency.toLong / 2,
          p.timerWidth bits
        )
        cfg.clockDivider.init(clock)
        cfg.cs.setup.init(clock)
        cfg.cs.hold.init(clock)
        cfg.cs.disable.init(clock)
      } else {
        cfg.cs.setup.init(0)
        cfg.cs.hold.init(0)
        cfg.cs.disable.init(0)
      }

      val modeCfg = Reg(ctrl.modeConfig)
      if (p.init != null) {
        modeCfg.cpol.init(p.init.cpol)
        modeCfg.cpha.init(p.init.cpha)
      } else {
        modeCfg.cpol.init(False)
        modeCfg.cpha.init(False)
      }

      if (p.permission.busCanWriteModeConfig)
        busCtrl.readAndWrite(modeCfg, address = 0x08)
      else
        modeCfg.allowUnsetRegToAvoidLatch
      busCtrl.readAndWrite(cfg.cs.activeHigh, address = 0x08, bitOffset = 4)
      if (p.permission.busCanWriteClockDividerConfig) {
        busCtrl.writeMultiWord(cfg.clockDivider, address = 0x0c)
        busCtrl.readAndWrite(cfg.cs.setup, address = 0x10)
        busCtrl.readAndWrite(cfg.cs.hold, address = 0x14)
        busCtrl.readAndWrite(cfg.cs.disable, address = 0x18)
      } else {
        cfg.allowUnsetRegToAvoidLatch
        cfg.cs.allowUnsetRegToAvoidLatch
      }

      ctrl.config <> cfg
      ctrl.modeConfig <> modeCfg
    }
  }

  case class StreamMapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: SpiCtrl.Parameter
  ) extends Area {

    val cmdLogic = new Area {
      val streamUnbuffered = Stream(SpiController.Cmd(p))
      streamUnbuffered.valid := busCtrl.isWriting(address = 0x00)
      val dataCmd = SpiController.CmdData(p)
      busCtrl.nonStopWrite(dataCmd.data, bitOffset = 0)
      busCtrl.nonStopWrite(dataCmd.read, bitOffset = 24)
      val csCmd = SpiController.CmdCs(p)
      busCtrl.nonStopWrite(csCmd.index, bitOffset = 0)
      busCtrl.nonStopWrite(csCmd.enable, bitOffset = 24)
      busCtrl.nonStopWrite(streamUnbuffered.mode, bitOffset = 28)
      switch(streamUnbuffered.mode) {
        is(SpiController.CmdMode.DATA) {
          streamUnbuffered.args.assignFromBits(dataCmd.asBits)
        }
        is(SpiController.CmdMode.CS) {
          streamUnbuffered.args.assignFromBits(csCmd.asBits.resized)
        }
      }

      busCtrl.createAndDriveFlow(SpiController.Cmd(p), address = 0x00).toStream
      val (stream, fifoAvailability) =
        streamUnbuffered.queueWithAvailability(p.memory.cmdFifoDepth)
      ctrl.cmd << stream
      busCtrl.read(fifoAvailability, address = 0x04, 16)
    }

    val rspLogic = new Area {
      val (stream, fifoOccupancy) = ctrl.rsp.queueWithOccupancy(p.memory.rspFifoDepth)
      busCtrl.readStreamNonBlocking(
        stream,
        address = 0x00,
        validBitOffset = 31,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = 0x04, 0)
    }

    val interruptCtrl = new Area {
      val irqCtrl = new InterruptCtrl(2)
      irqCtrl.driveFrom(busCtrl, 0x1c)
      irqCtrl.io.inputs(0) := !cmdLogic.stream.valid
      irqCtrl.io.inputs(1) := rspLogic.stream.valid
      ctrl.pendingInterrupts := irqCtrl.io.pendings
    }
  }
}
