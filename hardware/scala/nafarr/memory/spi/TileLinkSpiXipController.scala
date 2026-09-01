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

import nafarr.bus.tilelink.TileLinkCache
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
  * A read-only line cache is instantiated internally when `cacheWords > 0` and
  * is invalidated by every command-engine operation.
  */
case class TileLinkSpiXipController(
    parameter: SpiControllerCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter = TileLinkParameter.simple(10, 32, 4, 1),
    cacheWords: Int = 0
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

  val cache = if (cacheWords > 0) TileLinkCache.Cache(busConfig, cacheWords) else null
  val busPort = if (cache != null) {
    cache.io.inner <> io.bus
    cache.io.invalidate := spiXipControllerCtrl.io.cacheInvalidate
    cache.io.outer
  } else io.bus

  val dSource = RegNextWhen(busPort.a.source, busPort.a.ready)
  val dSize = RegNextWhen(busPort.a.size, busPort.a.ready)

  val spiCmd = SpiXipController.GenericInterface.Cmd()
  val aWords =
    ((U(1, 10 bits) |<< busPort.a.size) + (busPort.p.dataBytes - 1)) >> busPort.p.dataBytesLog2Up
  val alignedAddr =
    (busPort.a.address >> busPort.p.dataBytesLog2Up) @@ U(0, busPort.p.dataBytesLog2Up bits)
  spiCmd.addr := RegNextWhen(alignedAddr.resize(24), busPort.a.ready)
  spiCmd.count := RegNextWhen(
    (aWords - 1).resize(widthOf(spiCmd.count)),
    busPort.a.ready
  )
  spiXipControllerCtrl.io.busCmd.payload := spiCmd
  spiXipControllerCtrl.io.busCmd.valid := False

  // D-channel defaults (overridden per state below).
  busPort.a.ready := False
  busPort.d.valid := False
  busPort.d.opcode := Opcode.D.ACCESS_ACK_DATA()
  busPort.d.param := 0
  busPort.d.size := dSize
  busPort.d.source := dSource
  busPort.d.sink := 0
  busPort.d.denied := False
  busPort.d.data := spiXipControllerCtrl.io.busRsp.payload.data
  busPort.d.corrupt := False

  val stateMachine = new Area {
    val state = RegInit(RspState.IDLE)
    switch(state) {
      is(RspState.IDLE) {
        when(busPort.a.valid) {
          busPort.a.ready := True
          when(busPort.a.opcode === Opcode.A.GET()) {
            state := RspState.CMD
          } otherwise {
            // Write to a read-only flash controller: deny immediately.
            state := RspState.ERROR
          }
        }
      }
      is(RspState.ERROR) {
        busPort.d.opcode := Opcode.D.ACCESS_ACK()
        busPort.d.denied := True
        busPort.d.valid := True
        when(busPort.d.ready) {
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
          busPort.d.valid := True
          when(busPort.d.ready) {
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
  SpiXipControllerCtrl.Mapper(cfgXipBusFactory, spiXipControllerCtrl.io, parameter, cacheWords)
}
