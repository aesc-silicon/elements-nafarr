package nafarr.memory.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._

import nafarr.peripherals.com.spi.{Spi, SpiControllerCtrl}

case class Axi4ReadOnlySpiXipController(
    parameter: SpiControllerCtrl.Parameter,
    dataBusConfig: Axi4Config = Axi4Config(20, 32, 4),
    cfgBusConfig: Apb3Config = Apb3Config(12, 32)
) extends Component {
  val io = new Bundle {
    val cfgBus = slave(Apb3(cfgBusConfig))
    val dataBus = slave(Axi4ReadOnly(dataBusConfig))
    val spi = master(Spi.Io(parameter.io))
    val interrupt = out(Bool)
  }

  object RspState extends SpinalEnum {
    val IDLE, CMD, RESPONSE = newElement()
  }

  val spiControllerCtrl = SpiControllerCtrl(parameter)
  spiControllerCtrl.io.spi <> io.spi
  io.interrupt := False

  val spiXipControllerCtrl = SpiXipControllerCtrl(parameter, 32)
  spiControllerCtrl.io.cmd << spiXipControllerCtrl.io.cmd
  spiXipControllerCtrl.io.rsp << spiControllerCtrl.io.rsp
  spiXipControllerCtrl.io.busRsp.ready := False

  val spiCmd = SpiXipController.GenericInterface.Cmd()
  spiCmd.addr := RegNextWhen(io.dataBus.ar.addr.resize(24), io.dataBus.ar.ready)
  spiXipControllerCtrl.io.busCmd.payload := spiCmd
  spiXipControllerCtrl.io.busCmd.valid := False

  io.dataBus.readCmd.ready := False
  io.dataBus.readRsp.valid := False
  io.dataBus.readRsp.data := 0
  io.dataBus.readRsp.id := RegNextWhen(io.dataBus.readCmd.id, io.dataBus.readCmd.ready)
  io.dataBus.readRsp.setOKAY()
  io.dataBus.readRsp.last := True

  val stateMachine = new Area {
    val state = RegInit(RspState.IDLE)
    switch(state) {
      is(RspState.IDLE) {
        // Controller is Read-Only
        when(io.dataBus.readCmd.valid) {
          io.dataBus.readCmd.ready := True
          state := RspState.CMD
        }
      }
      is(RspState.CMD) {
        spiXipControllerCtrl.io.busCmd.valid := True
        when(spiXipControllerCtrl.io.busCmd.fire) {
          state := RspState.RESPONSE
        }
      }
      is(RspState.RESPONSE) {
        when(spiXipControllerCtrl.io.busRsp.valid) {
          io.dataBus.readRsp.data := spiXipControllerCtrl.io.busRsp.payload.data
          io.dataBus.readRsp.valid := True
          when(io.dataBus.readRsp.fire) {
            spiXipControllerCtrl.io.busRsp.ready := True
            state := RspState.IDLE
          }
        }
      }
    }
  }

  val busFactory = Apb3SlaveFactory(io.cfgBus)
  SpiControllerCtrl.Mapper(busFactory, spiControllerCtrl.io, parameter)
}
