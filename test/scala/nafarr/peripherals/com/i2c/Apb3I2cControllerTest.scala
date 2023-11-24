package nafarr.peripherals.com.i2c

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver


class Apb3I2cControllerTest extends AnyFunSuite {
  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3I2cController(I2cCtrl.Parameter.default)
      }
      area.dut
    }
  }
}
