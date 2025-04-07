package nafarr.peripherals.com.spi

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class Apb3SpiControllerTest extends AnyFunSuite {
  test("parameters") {
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
