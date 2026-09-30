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

import nafarr.Feature
import nafarr.bus.tilelink.TileLinkCache
import nafarr.peripherals.SysconFeatures
import nafarr.peripherals.com.spi.{Spi, SpiControllerCtrl}

object TileLinkSpiXipController {

  /** Converts TileLink requests into commands of the 32-bit XIP engine.
    *
    * A GET fetches the covered 32-bit words from its 4-byte aligned address, at least one.
    * Response words are packed into D beats of `dataWidth / 32` words, starting at the
    * request's word position within the beat, so sub-beat GETs return their bytes in the
    * lanes TileLink expects. Any other request is denied.
    */
  case class BusAdapter(p: TileLinkParameter) extends Component {
    require(Seq(32, 64, 128).contains(p.dataWidth), "dataWidth must be 32, 64 or 128")
    require(p.sizeBytes <= 1024, "the XIP engine fetches at most 256 words per command")

    val io = new Bundle {
      val bus = slave(TileLinkBus(p))
      val cmd = master(Stream(SpiXipController.GenericInterface.Cmd()))
      val rsp = slave(Stream(SpiXipController.GenericInterface.Rsp()))
    }

    object State extends SpinalEnum {
      val IDLE, ERROR, CMD, RESPONSE = newElement()
    }

    val wordsPerBeat = p.dataWidth / 32
    val laneWidth = log2Up(wordsPerBeat)
    val a = io.bus.a
    val d = io.bus.d

    val source = Reg(p.source())
    val size = Reg(p.size())
    val command = Reg(SpiXipController.GenericInterface.Cmd())
    val startLane = Reg(UInt(laneWidth bits))
    val lane = Reg(UInt(laneWidth bits))
    val words = Vec(Reg(Bits(32 bits)), wordsPerBeat)
    val full = RegInit(False)
    val fullLast = Reg(Bool())

    // Word count of the request (a 1 or 2 byte GET still fetches one word) and its first word
    // position within a beat; lanes only exist for buses wider than 32 bits.
    val requestWords = ((U(1, 11 bits) |<< a.size) + 3) >> 2
    val wordLane =
      if (laneWidth > 0) a.address(p.dataBytesLog2Up - 1 downto 2) else U(0, 0 bits)
    val atLastLane = if (laneWidth > 0) lane === (wordsPerBeat - 1) else True

    a.ready := False
    io.cmd.valid := False
    io.cmd.payload := command
    io.rsp.ready := False
    d.valid := False
    d.opcode := Opcode.D.ACCESS_ACK_DATA()
    d.param := 0
    d.size := size
    d.source := source
    d.sink := 0
    d.denied := False
    d.data := words.asBits
    d.corrupt := False

    val state = RegInit(State.IDLE)
    switch(state) {
      is(State.IDLE) {
        a.ready := True
        when(a.valid) {
          source := a.source
          size := a.size
          command.addr := a.address.resize(24)(23 downto 2) @@ U(0, 2 bits)
          command.count := (requestWords - 1).resized
          if (laneWidth > 0) startLane := wordLane
          when(a.opcode === Opcode.A.GET()) {
            state := State.CMD
          } otherwise {
            // Write to a read-only flash controller: deny once the last beat is taken.
            when(a.isLast()) {
              state := State.ERROR
            }
          }
        }
      }
      is(State.ERROR) {
        d.opcode := Opcode.D.ACCESS_ACK()
        d.denied := True
        d.valid := True
        when(d.ready) {
          state := State.IDLE
        }
      }
      is(State.CMD) {
        io.cmd.valid := True
        when(io.cmd.ready) {
          if (laneWidth > 0) lane := startLane
          state := State.RESPONSE
        }
      }
      is(State.RESPONSE) {
        io.rsp.ready := !full
        when(io.rsp.fire) {
          if (laneWidth > 0) words(lane) := io.rsp.payload.data
          else words(0) := io.rsp.payload.data
          when(atLastLane || io.rsp.payload.last) {
            full := True
            fullLast := io.rsp.payload.last
          } otherwise {
            if (laneWidth > 0) lane := lane + 1
          }
        }
        d.valid := full
        when(d.fire) {
          full := False
          if (laneWidth > 0) lane := 0
          when(fullLast) {
            state := State.IDLE
          }
        }
      }
    }
  }
}

/** XIP (execute-in-place) SPI flash controller with a TileLink-UH (burst) data
  * interface.
  *
  * The controller is read-only.  Burst GET requests trigger an SPI transaction
  * that fetches the requested 32-bit words sequentially; they are packed into D
  * beats of the bus width.  Any non-GET request is acknowledged with `denied = true`.
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
) extends Component
    with SysconFeatures {
  val io = new Bundle {
    val bus = slave(TileLinkBus(busConfig))
    val cfgSpiBus = slave(TileLinkBus(cfgBusConfig))
    val cfgXipBus = slave(TileLinkBus(cfgBusConfig))
    val spi = master(Spi.Io(parameter.io))
    val interrupt = out(Bool)
  }

  override def sysconFeatures = Some(List(Feature.SpiFlash))

  val spiControllerCtrl = SpiControllerCtrl(parameter)
  spiControllerCtrl.io.spi <> io.spi
  io.interrupt := False

  val spiXipControllerCtrl = SpiXipControllerCtrl(parameter, 32)
  spiControllerCtrl.io.cmd << spiXipControllerCtrl.io.cmd
  spiXipControllerCtrl.io.rsp << spiControllerCtrl.io.rsp

  val cache = if (cacheWords > 0) TileLinkCache.Cache(busConfig, cacheWords) else null
  val busPort = if (cache != null) {
    cache.io.inner <> io.bus
    cache.io.invalidate := spiXipControllerCtrl.io.cacheInvalidate
    cache.io.outer
  } else io.bus

  val busAdapter = TileLinkSpiXipController.BusAdapter(busPort.p)
  busAdapter.io.bus <> busPort
  spiXipControllerCtrl.io.busCmd << busAdapter.io.cmd
  busAdapter.io.rsp << spiXipControllerCtrl.io.busRsp

  val cfgSpiBusFactory = new TileLinkSlaveFactory(io.cfgSpiBus, false)
  SpiControllerCtrl.Mapper(cfgSpiBusFactory, spiControllerCtrl.io, parameter)

  val cfgXipBusFactory = new TileLinkSlaveFactory(io.cfgXipBus, false)
  SpiXipControllerCtrl.Mapper(cfgXipBusFactory, spiXipControllerCtrl.io, parameter, cacheWords)
}
