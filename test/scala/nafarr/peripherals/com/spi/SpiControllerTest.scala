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

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3SpiController(SpiControllerCtrl.Parameter.default())
      }
      area.dut
    }
  }
}
