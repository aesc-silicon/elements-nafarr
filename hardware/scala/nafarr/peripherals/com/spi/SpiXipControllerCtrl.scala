package nafarr.peripherals.com.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._

object SpiXipControllerCtrl {
  def apply(p: SpiCtrl.Parameter, dataBusConfig: Axi4Config) = SpiControllerCtrl(p, dataBusConfig)

  object State extends SpinalEnum {
    val IDLE, ENABLESPI, COMMAND, ADDRESS, DATA, DISABLESPI = newElement()
  }

  case class Io(p: SpiCtrl.Parameter, dataBusConfig: Axi4Config) extends Bundle {
    val bus = slave(Axi4ReadOnly(dataBusConfig))
    val cmd = master(Stream(SpiController.Cmd(p)))
    val rsp = slave(Flow(Bits(p.dataWidth bits)))
  }

  case class SpiControllerCtrl(p: SpiCtrl.Parameter, dataBusConfig: Axi4Config) extends Component {
    val io = Io(p, dataBusConfig)

    val cmdStream = Stream(SpiController.Cmd(p))
    cmdStream.valid := False
    cmdStream.payload.mode := SpiController.CmdMode.DATA
    cmdStream.payload.args := 0

    val rspFifo = StreamFifo(Bits(p.dataWidth bits), p.memory.rspFifoDepth)
    rspFifo.io.push.payload := io.rsp.payload
    rspFifo.io.push.valid := io.rsp.valid

    io.cmd << cmdStream

    val burst = new Area {
      val address = Reg(UInt(24 bits))
      val count = Reg(UInt(8 bits))
      val countResponse = Reg(UInt(8 bits))
      val size = Reg(UInt(3 bits))
      val burstType = Reg(Bits(2 bits))
      val id = Reg(cloneOf(io.bus.ar.id))
    }

    io.bus.readRsp.valid := False
    io.bus.readRsp.setOKAY()
    io.bus.readRsp.last := False
    io.bus.readRsp.id := burst.id
    val rspHandler = new Area {
      val data = Reg(Bits(dataBusConfig.dataWidth bits))
      val counter = Reg(UInt(log2Up(dataBusConfig.dataWidth / 8) bits)).init(0)
      val push = RegInit(False)

      rspFifo.io.pop.ready := False
      when(rspFifo.io.pop.valid && !push) {
        data(8 * counter, 8 bits) := rspFifo.io.pop.payload.asBits
        counter := counter + 1
        rspFifo.io.pop.ready := True
        when(counter === 3) {
          push := True
        }
      }
      when(push) {
        io.bus.readRsp.valid := True
        when(burst.countResponse === 0) {
          io.bus.readRsp.last := True
        }
        when(io.bus.readRsp.fire) {
          burst.countResponse := burst.countResponse - 1
          push := False
        }
      }
    }
    io.bus.readRsp.data := rspHandler.data

    val stateMachine = new Area {
      val state = RegInit(State.IDLE)
      val counter = new Area {
        val value = Reg(UInt(log2Up(dataBusConfig.dataWidth / 8) bits))
        def resetAddr = value := 3 - 1
        def resetData = value := (dataBusConfig.dataWidth / 8) - 1
        def nextValue = value := value - 1
      }

      io.bus.ar.ready := False
      switch(state) {
        is(State.IDLE) {
          when(io.bus.ar.valid) {
            io.bus.ar.ready := True
            burst.address := io.bus.ar.addr.resize(24)
            burst.count := io.bus.ar.len
            burst.countResponse := io.bus.ar.len
            burst.size := io.bus.ar.size
            burst.burstType := io.bus.ar.burst
            burst.id := io.bus.ar.id
            state := State.ENABLESPI
          }
        }
        is(State.ENABLESPI) {
          val enableSpi = SpiController.CmdCs(p)
          enableSpi.enable := True
          enableSpi.index := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.CS
          cmdStream.payload.args.assignFromBits(enableSpi.asBits.resized)

          when(cmdStream.ready) {
            state := State.COMMAND
          }
        }
        is(State.COMMAND) {
          val readCommand = SpiController.CmdData(p)
          readCommand.read := False
          readCommand.data := 3

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(readCommand.asBits)

          counter.resetAddr
          when(cmdStream.ready) {
            state := State.ADDRESS
          }
        }
        is(State.ADDRESS) {
          val addressCommand = SpiController.CmdData(p)
          addressCommand.read := False
          addressCommand.data := burst.address(8 * counter.value, 8 bits).asBits

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(addressCommand.asBits)

          when(cmdStream.ready) {
            when(counter.value === 0) {
              counter.resetData
              state := State.DATA
            } otherwise {
              counter.nextValue
            }
          }
        }
        is(State.DATA) {
          val dataCommand = SpiController.CmdData(p)
          dataCommand.read := True
          dataCommand.data := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.DATA
          cmdStream.payload.args.assignFromBits(dataCommand.asBits)

          when(cmdStream.ready) {
            when(counter.value === 0) {
              counter.resetData
              burst.count := burst.count - 1
              when(burst.count === 0) {
                state := State.DISABLESPI
              }
            } otherwise {
              counter.nextValue
            }
          }
        }
        is(State.DISABLESPI) {
          val enableSpi = SpiController.CmdCs(p)
          enableSpi.enable := False
          enableSpi.index := 0

          cmdStream.valid := True
          cmdStream.payload.mode := SpiController.CmdMode.CS
          cmdStream.payload.args.assignFromBits(enableSpi.asBits.resized)

          when(cmdStream.ready) {
            state := State.IDLE
          }
        }
      }
    }
  }
}
