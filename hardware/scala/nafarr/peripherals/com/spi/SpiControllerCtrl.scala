package nafarr.peripherals.com.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import nafarr.IpIdentification
import nafarr.library.ClockDivider

object SpiControllerCtrl {
  def apply(p: SpiCtrl.Parameter = SpiCtrl.Parameter.default()) = SpiControllerCtrl(p)

  object State extends SpinalEnum {
    val Idle, Cs, CsSetup, CsHold, CsDisable, DataSingle, DataQuad = newElement()
  }

  case class Config(p: SpiCtrl.Parameter) extends Bundle {
    val clockDivider = UInt(p.clockDividerWidth bits)
    val cs = new Bundle {
      val activeHigh = Bits(p.io.csWidth bits)
      val setup = UInt(p.clockDividerWidth bits)
      val hold = UInt(p.clockDividerWidth bits)
      val disable = UInt(p.clockDividerWidth bits)
    }
  }

  object SpiBusWidth extends SpinalEnum(binarySequential) {
    val Single, Dual, Quad, Octa = newElement()
  }

  case class ModeConfig(p: SpiCtrl.Parameter) extends Bundle {
    val cpol = Bool
    val cpha = Bool
    val busWidth = SpiBusWidth()
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
    val ctrlEnable = RegInit(True)

    val ctrl = new ClockEnableArea(ctrlEnable) {

      val clockDivider = new ClockDivider(p.clockDividerWidth)
      clockDivider.io.value := io.config.clockDivider
      clockDivider.io.reload := False

      val dataCounter = new Area {
        val value = Reg(UInt(log2Up(p.dataWidth * 2) bits)).init(0)
        def reset() = value := 0
        def increment() = value := value + 1
        def isSingleLast() = value === 15
        def isDualLast() = value === 7
        def isQuadLast() = value === 3
      }

      val stateMachine = new Area {
        val cs = RegInit(B((1 << p.io.csWidth) - 1, p.io.csWidth bits))
        val buffer = Reg(Bits(p.dataWidth bits))
        val state = RegInit(State.Idle)

        io.cmd.ready := False
        switch(state) {
          is(State.Idle) {
            when(io.cmd.valid) {
              clockDivider.io.reload := True
              when(io.cmd.isCs && io.cmd.argsCs.enable) {
                clockDivider.io.value := io.config.cs.setup
                state := State.CsSetup
              }
              when(io.cmd.isCs && !io.cmd.argsCs.enable) {
                clockDivider.io.value := io.config.cs.hold
                state := State.CsHold
              }
              when(io.cmd.isData) {
                clockDivider.io.value := io.config.clockDivider
                dataCounter.reset()
                state := State.DataSingle
              }
            }
          }
          is(State.CsSetup) {
            cs(io.cmd.argsCs.index) := False
            when(clockDivider.io.tick) {
              io.cmd.ready := True
              state := State.Idle
            }
          }
          is(State.DataSingle) {
            when(clockDivider.io.tick) {
              dataCounter.increment()
              when(dataCounter.value.lsb) {
                buffer := (buffer ## io.spi.dq(1).read).resized
              }
              when (dataCounter.isSingleLast()) {
                io.cmd.ready := True
                state := State.Idle
              }
            }
          }
          is(State.CsHold) {
            when(clockDivider.io.tick) {
              clockDivider.io.value := io.config.cs.disable
              clockDivider.io.reload := True
              state := State.CsDisable
            }
          }
          is(State.CsDisable) {
            cs(io.cmd.argsCs.index) := True
            when(clockDivider.io.tick) {
              io.cmd.ready := True
              state := State.Idle
            }
          }

        }
      }
    }

    when(io.cmd.valid || !(ctrl.stateMachine.state === State.Idle)) {
      ctrlEnable := True
    } otherwise {
      ctrlEnable := False
    }

    // CMD responses
    io.rsp.valid := RegNext(
      io.cmd.fire && io.cmd.isData &&
        io.cmd.argsData.read
    ).init(False)
    io.rsp.payload := ctrl.stateMachine.buffer

    io.spi.cs := ctrl.stateMachine.cs ^ io.config.cs.activeHigh
    io.spi.sclk := RegNext(
      ((io.cmd.valid && io.cmd.isData) &&
        (ctrl.dataCounter.value.lsb ^ io.modeConfig.cpha)) ^
        io.modeConfig.cpol
    )
    io.spi.dq(0).write := RegNext(io.cmd.argsData.data(p.dataWidth - 1 - (ctrl.dataCounter.value >> 1)))
    io.spi.dq(0).writeEnable := True
    io.spi.dq(1).write := False
    io.spi.dq(1).writeEnable := False
    io.spi.dq(2).write := False
    io.spi.dq(2).writeEnable := False
    io.spi.dq(3).write := False
    io.spi.dq(3).writeEnable := False
    io.interrupt := io.pendingInterrupts.orR
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: SpiCtrl.Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.SpiController, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length

    busCtrl.read(
      B(0, 8 bits) ## B(p.io.csWidth, 16 bits) ## B(p.io.dataWidth, 8 bits),
      staticOffset
    )

    busCtrl.read(
      B(0, 24 bits) ## B(p.clockDividerWidth, 8 bits),
      staticOffset + 0x4
    )

    busCtrl.read(
      B(0, 16 bits) ## B(p.memory.cmdFifoDepth, 8 bits) ## B(p.memory.rspFifoDepth, 8 bits),
      staticOffset + 0x8
    )

    val permissionBits = Bool(p.permission.busCanWriteModeConfig) ##
      Bool(p.permission.busCanWriteClockDividerConfig)
    busCtrl.read(B(0, 32 - widthOf(permissionBits) bits) ## permissionBits, staticOffset + 0xc)
    val regOffset = staticOffset + 0x10

    val config = new Area {
      val cfg = Reg(ctrl.config)
      cfg.cs.activeHigh.init(0)
      if (p.init != null && p.init.frequency.toLong > 1) {
        val clock = U(
          ClockDomain.current.frequency.getValue.toLong / p.init.frequency.toLong / 2,
          p.clockDividerWidth bits
        )
        cfg.clockDivider.init(clock)
        cfg.cs.setup.init(clock)
        cfg.cs.hold.init(clock)
        cfg.cs.disable.init(clock)
      } else {
        cfg.clockDivider.init(0)
        cfg.cs.setup.init(0)
        cfg.cs.hold.init(0)
        cfg.cs.disable.init(0)
      }

      if (p.permission != null && p.permission.busCanWriteClockDividerConfig) {
        busCtrl.write(cfg.clockDivider, address = regOffset + 0x10)
        busCtrl.write(cfg.cs.setup, address = regOffset + 0x14)
        busCtrl.write(cfg.cs.hold, address = regOffset + 0x18)
        busCtrl.write(cfg.cs.disable, address = regOffset + 0x1C)
      }
      busCtrl.read(cfg.clockDivider, regOffset + 0x10)
      busCtrl.read(cfg.cs.setup, regOffset + 0x14)
      busCtrl.read(cfg.cs.hold, regOffset + 0x18)
      busCtrl.read(cfg.cs.disable, regOffset + 0x1c)

      val modeCfg = Reg(ctrl.modeConfig)
      if (p.init != null) {
        modeCfg.cpol.init(p.init.cpol)
        modeCfg.cpha.init(p.init.cpha)
        modeCfg.busWidth.init(p.init.busWidth)
      } else {
        modeCfg.cpol.init(False)
        modeCfg.cpha.init(False)
        modeCfg.busWidth.init(SpiBusWidth.Single)
      }
      if (p.permission != null && p.permission.busCanWriteModeConfig)
        busCtrl.write(cfg.clockDivider, address = regOffset + 0x20)
      busCtrl.read(cfg.clockDivider, regOffset + 0x20)

      ctrl.config <> cfg
      ctrl.modeConfig <> modeCfg
    }
  }

  case class StreamMapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: SpiCtrl.Parameter
  ) extends Area {
    val regOffset = 0x40

    val cmdLogic = new Area {
      val streamUnbuffered = Stream(SpiController.Cmd(p))
      streamUnbuffered.valid := busCtrl.isWriting(address = regOffset)
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

      busCtrl.createAndDriveFlow(SpiController.Cmd(p), address = regOffset).toStream
      val (stream, fifoAvailability) =
        streamUnbuffered.queueWithAvailability(p.memory.cmdFifoDepth)
      ctrl.cmd << stream
      busCtrl.read(fifoAvailability, address = regOffset + 0x04, 16)
    }

    val rspLogic = new Area {
      val (stream, fifoOccupancy) = ctrl.rsp.queueWithOccupancy(p.memory.rspFifoDepth)
      busCtrl.readStreamNonBlocking(
        stream,
        address = regOffset,
        validBitOffset = 31,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = regOffset + 0x04, 0)
    }

    val interruptCtrl = new Area {
      val irqCtrl = new InterruptCtrl(2)
      irqCtrl.driveFrom(busCtrl, regOffset + 0x8)
      irqCtrl.io.inputs(0) := !cmdLogic.stream.valid
      irqCtrl.io.inputs(1) := rspLogic.stream.valid
      ctrl.pendingInterrupts := irqCtrl.io.pendings
    }
  }
}
