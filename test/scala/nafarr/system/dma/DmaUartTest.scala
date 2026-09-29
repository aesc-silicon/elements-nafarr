// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.dma

import org.scalatest.funsuite.AnyFunSuite

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import spinal.lib.bus.tilelink.{Bus => TileLinkBus}

import nafarr.bus.tilelink.TileLinkSlaveModel
import nafarr.peripherals.com.uart.{TileLinkUart, UartCtrl}

object DmaUartHarness {
  val uartBase = 0xf0000000L
}

/** DMA controller and a TileLink UART with TX looped back to RX.
  *
  * DMA accesses to the UART page go to the UART, all others to `io.mem`. The UART TX and RX
  * request handshakes drive DMA request lines 0 and 1.
  */
case class DmaUartHarness(dmaParam: DmaCtrl.Parameter, uartParam: UartCtrl.Parameter)
    extends Component {
  val io = new Bundle {
    val bus = slave(Apb3(Apb3Config(12, 32)))
    val mem = master(TileLinkBus(dmaParam.memParam))
  }

  val dma = Apb3Dma(dmaParam)
  val uart = TileLinkUart(uartParam)
  dma.io.bus <> io.bus

  uart.io.uart.rxd := uart.io.uart.txd
  uart.io.uart.cts := False
  dma.io.request(0) <> uart.io.dmaRequest.tx
  dma.io.request(1) <> uart.io.dmaRequest.rx

  val dmaMem = dma.io.mem
  val uartBus = uart.io.bus
  val toUart = dmaMem.a.address(31 downto 12) === U(DmaUartHarness.uartBase >> 12, 20 bits)

  io.mem.a.valid := dmaMem.a.valid && !toUart
  io.mem.a.payload := dmaMem.a.payload
  uartBus.a.valid := dmaMem.a.valid && toUart
  uartBus.a.opcode := dmaMem.a.opcode
  uartBus.a.param := dmaMem.a.param
  uartBus.a.source := dmaMem.a.source.resized
  uartBus.a.address := dmaMem.a.address.resized
  uartBus.a.size := dmaMem.a.size.resized
  uartBus.a.mask := dmaMem.a.mask
  uartBus.a.data := dmaMem.a.data
  uartBus.a.corrupt := dmaMem.a.corrupt
  dmaMem.a.ready := Mux(toUart, uartBus.a.ready, io.mem.a.ready)

  // The DMA has one transaction in flight at a time, so the target of the last A beat owns D.
  val responseFromUart = RegNextWhen(toUart, dmaMem.a.fire) init (False)
  io.mem.d.ready := dmaMem.d.ready && !responseFromUart
  uartBus.d.ready := dmaMem.d.ready && responseFromUart
  dmaMem.d.valid := Mux(responseFromUart, uartBus.d.valid, io.mem.d.valid)
  dmaMem.d.payload := io.mem.d.payload
  when(responseFromUart) {
    dmaMem.d.opcode := uartBus.d.opcode
    dmaMem.d.param := uartBus.d.param
    dmaMem.d.source := uartBus.d.source.resized
    dmaMem.d.size := uartBus.d.size.resized
    dmaMem.d.denied := uartBus.d.denied
    dmaMem.d.data := uartBus.d.data
    dmaMem.d.corrupt := uartBus.d.corrupt
  }
}

class DmaUartTest extends AnyFunSuite {
  val dmaParam = DmaCtrl.Parameter(channels = 2, requestLines = 2, burstBytes = 16)
  val baudrate = 1000000

  test("UART loopback") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        // Small FIFOs: without request pacing the DMA would overflow them and lose bytes.
        val uartParam = UartCtrl.Parameter(
          init = UartCtrl.InitParameter.default(baudrate),
          permission = UartCtrl.PermissionParameter.granted,
          memory = UartCtrl.MemoryMappedParameter.lightweight
        )
        val dut = DmaUartHarness(dmaParam, uartParam)
      }
      area.dut
    }

    compiled.doSim("uartLoopback") { dut =>
      val cd = dut.clockDomain
      cd.forkStimulus(10)
      val driver = Apb3Driver(dut.io.bus, cd)
      val regs = DmaCtrl.Regs(dut.dma.mapper.idCtrl.length, dmaParam)
      val slave = new TileLinkSlaveModel(dut.io.mem, cd)
      val uartData =
        DmaUartHarness.uartBase + UartCtrl.Regs(dut.uart.mapper.idCtrl.length).readWrite.toLong
      val txBuffer = 0x80000000L
      val rxBuffer = 0x80001000L
      val data = Seq.fill(32)(simRandom.nextInt(256))
      slave.fill(txBuffer, data)
      cd.waitSampling(10)

      def gated(srcInc: Boolean, dstInc: Boolean, requestLine: Int): BigInt = {
        var config = BigInt(requestLine) << DmaCtrl.Config.reqSel
        config |= BigInt(1) << DmaCtrl.Config.reqEnable
        if (srcInc) config |= BigInt(1) << DmaCtrl.Config.srcInc
        if (dstInc) config |= BigInt(1) << DmaCtrl.Config.dstInc
        config // width 0: byte elements
      }
      def program(ch: Int, config: BigInt, src: Long, dst: Long): Unit = {
        driver.write(regs.config(ch), config)
        driver.write(regs.src(ch), src)
        driver.write(regs.dst(ch), dst)
        driver.write(regs.length(ch), data.length)
        driver.write(regs.next(ch), 0)
      }

      /* Channel 1: UART RX FIFO to memory; channel 0: memory to UART TX FIFO */
      program(1, gated(srcInc = false, dstInc = true, requestLine = 1), uartData, rxBuffer)
      program(0, gated(srcInc = true, dstInc = false, requestLine = 0), txBuffer, uartData)
      driver.write(regs.control(1), 1)
      driver.write(regs.control(0), 1)

      // 10 bits per byte at 1 Mbaud and 100 MHz: 1000 cycles per byte.
      var polls = 0
      while ((driver.read(regs.status) & 3) != 0) {
        polls += 1
        assert(polls < 20000, "DMA channels did not finish")
      }
      for (ch <- 0 until 2) {
        assert((driver.read(regs.control(ch)) & 2) == 0, s"channel $ch reported an error")
      }

      assert(slave.readBytes(rxBuffer, data.length) == data, "looped back data mismatch")
      assert(slave.reads == data.length, s"${slave.reads} buffer reads for ${data.length} bytes")
      assert(slave.writes == data.length, s"${slave.writes} buffer writes for ${data.length} bytes")
    }
  }
}
