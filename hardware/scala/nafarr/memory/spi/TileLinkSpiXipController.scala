// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.spi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  Opcode,
  SlaveFactory => TileLinkSlaveFactory
}

import nafarr.peripherals.com.spi.{Spi, SpiControllerCtrl}

/** XIP (execute-in-place) SPI flash controller with a TileLink-UH (burst) data
  * interface.
  *
  * The controller is read-only.  Burst GET requests trigger an SPI transaction
  * that fetches the requested words sequentially and returns one D-beat per
  * word.  Any non-GET request is immediately acknowledged with `denied = true`.
  *
  * Configuration (SPI timing and XIP mode/dummy-cycles) is exposed via two
  * separate Wishbone slave ports (`cfgSpiBus` / `cfgXipBus`) that are
  * compatible with the existing `SpiControllerCtrl.Mapper` /
  * `SpiXipControllerCtrl.Mapper` register layouts.
  *
  * Cache placement: connect `TileLinkCache.Cache` between the CPU and this
  * controller on the outer (memory-side) TileLink port.
  */
case class TileLinkSpiXipController(
    parameter: SpiControllerCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter = TileLinkParameter.simple(10, 32, 4, 1)
) extends Component {
  val io = new Bundle {
    val bus = slave(TileLinkBus(busConfig))
    val cfgSpiBus = slave(TileLinkBus(cfgBusConfig))
    val cfgXipBus = slave(TileLinkBus(cfgBusConfig))
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

  // Register A-channel metadata when the request is accepted (a.ready high).
  val dSource = RegNextWhen(io.bus.a.source, io.bus.a.ready)
  val dSize = RegNextWhen(io.bus.a.size, io.bus.a.ready)

  // Build the SPI command from the accepted A-channel fields.
  // count encodes "words to fetch - 1": words = 1 << (size - dataBytesLog2Up).
  val spiCmd = SpiXipController.GenericInterface.Cmd()
  spiCmd.addr := RegNextWhen(io.bus.a.address.resize(24), io.bus.a.ready)
  spiCmd.count := RegNextWhen(
    ((U(1, 9 bits) |<< (io.bus.a.size - busConfig.dataBytesLog2Up)) - 1)
      .resize(widthOf(spiCmd.count)),
    io.bus.a.ready
  )
  spiXipControllerCtrl.io.busCmd.payload := spiCmd
  spiXipControllerCtrl.io.busCmd.valid := False

  // D-channel defaults (overridden per state below).
  io.bus.a.ready := False
  io.bus.d.valid := False
  io.bus.d.opcode := Opcode.D.ACCESS_ACK_DATA()
  io.bus.d.param := 0
  io.bus.d.size := dSize
  io.bus.d.source := dSource
  io.bus.d.sink := 0
  io.bus.d.denied := False
  io.bus.d.data := spiXipControllerCtrl.io.busRsp.payload.data
  io.bus.d.corrupt := False

  val stateMachine = new Area {
    val state = RegInit(RspState.IDLE)
    switch(state) {
      is(RspState.IDLE) {
        when(io.bus.a.valid) {
          io.bus.a.ready := True
          when(io.bus.a.opcode === Opcode.A.GET()) {
            state := RspState.CMD
          } otherwise {
            // Write to a read-only flash controller: deny immediately.
            state := RspState.ERROR
          }
        }
      }
      is(RspState.ERROR) {
        io.bus.d.opcode := Opcode.D.ACCESS_ACK()
        io.bus.d.denied := True
        io.bus.d.valid := True
        when(io.bus.d.ready) {
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
          io.bus.d.valid := True
          when(io.bus.d.ready) {
            spiXipControllerCtrl.io.busRsp.ready := True
            when(spiXipControllerCtrl.io.busRsp.payload.last) {
              state := RspState.IDLE
            }
          }
        }
      }
    }
  }

  val cfgSpiBusFactory = new TileLinkSlaveFactory(io.cfgSpiBus, false)
  SpiControllerCtrl.Mapper(cfgSpiBusFactory, spiControllerCtrl.io, parameter)

  val cfgXipBusFactory = new TileLinkSlaveFactory(io.cfgXipBus, false)
  SpiXipControllerCtrl.Mapper(cfgXipBusFactory, spiXipControllerCtrl.io.config, parameter)
}
