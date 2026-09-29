// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.spi

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import nafarr.system.dma.DmaHandshakeSim

class SpiControllerTest extends AnyFunSuite {
  test("Apb3SpiControllerParameters") {
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3SpiController(SpiControllerCtrl.Parameter.lightweight())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3SpiController(SpiControllerCtrl.Parameter.default())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3SpiController(SpiControllerCtrl.Parameter.xip())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3SpiController(SpiControllerCtrl.Parameter.full())
      }
      area.dut
    }
  }

  test("TileLinkSpiControllerParameters") {
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = TileLinkSpiController(SpiControllerCtrl.Parameter.lightweight())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = TileLinkSpiController(SpiControllerCtrl.Parameter.default())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = TileLinkSpiController(SpiControllerCtrl.Parameter.xip())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = TileLinkSpiController(SpiControllerCtrl.Parameter.full())
      }
      area.dut
    }
  }

  test("WishboneSpiControllerParameters") {
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = WishboneSpiController(SpiControllerCtrl.Parameter.lightweight())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = WishboneSpiController(SpiControllerCtrl.Parameter.default())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = WishboneSpiController(SpiControllerCtrl.Parameter.xip())
      }
      area.dut
    }
    generationShouldPass {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = WishboneSpiController(SpiControllerCtrl.Parameter.full())
      }
      area.dut
    }
  }

  test("DMA request") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3SpiController(SpiControllerCtrl.Parameter.default())
      }
      area.dut
    }

    compiled.doSim("dmaRequest") { dut =>
      DmaHandshakeSim.release(dut.io.dmaRequest)
      dut.clockDomain.forkStimulus(10)
      dut.io.spi.dq.read #= 0
      val apb = Apb3Driver(dut.io.bus, dut.clockDomain)
      val cd = dut.clockDomain
      val tx = dut.io.dmaRequest.tx
      val rx = dut.io.dmaRequest.rx
      val cmdReg = 0x50
      val fifoStatusReg = 0x54
      val readCmd = BigInt(1) << 24
      // One byte at the reset SCK of 100 kHz takes 80 us, i.e. 8000 cycles.
      val byteCycles = 8000
      cd.waitSampling(2)

      /* Idle: the empty command FIFO accepts an entry, no response is waiting */
      assert(tx.req.toBoolean, "DMA tx request low with empty command FIFO")
      assert(!rx.req.toBoolean, "DMA rx request high with empty response FIFO")
      DmaHandshakeSim.checkAck(tx, cd, "SPI tx")

      /* RX: a read command produces a response, reading it drains the FIFO */
      apb.write(cmdReg, readCmd | 0xa5)
      DmaHandshakeSim.waitReq(rx, cd, true, 2 * byteCycles, "SPI rx after a read command")
      DmaHandshakeSim.checkAck(rx, cd, "SPI rx")
      assert((apb.read(cmdReg) >> 31) == 1, "SPI response not valid")
      cd.waitSampling(2)
      assert(!rx.req.toBoolean, "DMA rx request high after response FIFO drained")

      /* TX: no request while the command FIFO is full, request again once one is consumed */
      val depth = SpiControllerCtrl.Parameter.default().memory.cmdFifoDepth
      for (_ <- 0 to depth) {
        apb.write(cmdReg, 0x5a)
      }
      cd.waitSampling(2)
      assert(((apb.read(fifoStatusReg) >> 16) & 0xffff) == 0, "SPI command FIFO not full")
      assert(!tx.req.toBoolean, "DMA tx request high with full command FIFO")
      DmaHandshakeSim.waitReq(tx, cd, true, 2 * byteCycles, "SPI tx after a command was consumed")
    }
  }
}
