// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.bmb._
import spinal.lib.bus.wishbone._

import nafarr.peripherals.com.spi.{Spi, SpiControllerCtrl}

case class BmbSpiXipController(
    parameter: SpiControllerCtrl.Parameter,
    dataBusConfig: BmbParameter,
    cfgBusConfig: WishboneConfig
) extends Component {
  val io = new Bundle {
    val dataBus = slave(Bmb(dataBusConfig))
    val cfgBus = slave(Wishbone(cfgBusConfig.copy(addressWidth = 10)))
    val spi = master(Spi.Io(parameter.io))
    val interrupt = out(Bool)
  }

  object RspState extends SpinalEnum {
    val IDLE, ERROR, CMD, RESPONSE = newElement()
  }

  val spiControllerCtrl = SpiControllerCtrl(parameter)
  spiControllerCtrl.io.spi <> io.spi
  io.interrupt := False

  val spiXipControllerCtrl = SpiXipControllerCtrl(parameter, 32)
  spiControllerCtrl.io.cmd << spiXipControllerCtrl.io.cmd
  spiXipControllerCtrl.io.rsp << spiControllerCtrl.io.rsp
  spiXipControllerCtrl.io.busRsp.ready := False

  val spiCmd = SpiXipController.GenericInterface.Cmd()
  spiCmd.addr := RegNextWhen(io.dataBus.cmd.address.resize(24), io.dataBus.cmd.ready)
  spiXipControllerCtrl.io.busCmd.payload := spiCmd
  spiXipControllerCtrl.io.busCmd.valid := False

  io.dataBus.rsp.valid := False
  io.dataBus.cmd.ready := False
  io.dataBus.rsp.setError()
  io.dataBus.rsp.data := 0
  io.dataBus.rsp.last := True
  io.dataBus.rsp.source := RegNextWhen(io.dataBus.cmd.source, io.dataBus.cmd.ready)
  io.dataBus.rsp.context := RegNextWhen(io.dataBus.cmd.context, io.dataBus.cmd.ready)

  val stateMachine = new Area {
    val state = RegInit(RspState.IDLE)
    switch(state) {
      is(RspState.IDLE) {
        // Controller is Read-Only
        when(io.dataBus.cmd.valid && io.dataBus.cmd.isWrite) {
          io.dataBus.cmd.ready := True
          state := RspState.ERROR
        }
        when(io.dataBus.cmd.valid && io.dataBus.cmd.isRead) {
          io.dataBus.cmd.ready := True
          state := RspState.CMD
        }
      }
      is(RspState.ERROR) {
        io.dataBus.rsp.valid := True
        when(io.dataBus.rsp.ready) {
          state := RspState.IDLE
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
          io.dataBus.rsp.setSuccess()
          io.dataBus.rsp.data := spiXipControllerCtrl.io.busRsp.payload.data
          io.dataBus.rsp.valid := True
          when(io.dataBus.rsp.fire) {
            spiXipControllerCtrl.io.busRsp.ready := True
            state := RspState.IDLE
          }
        }
      }
    }
  }

  val cfgBusFactory = WishboneSlaveFactory(io.cfgBus)
  SpiControllerCtrl.Mapper(cfgBusFactory, spiControllerCtrl.io, parameter)
}
