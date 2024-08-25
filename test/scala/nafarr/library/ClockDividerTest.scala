package nafarr.library

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._

class ClockDividerTest extends AnyFunSuite {
  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = ClockDivider(20)
      dut
    }
    compiled.doSim("test tick") { dut =>
      dut.clockDomain.forkStimulus(10)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      dut.io.value #= 10
      dut.io.reload #= true
      dut.clockDomain.waitSampling(1)
      dut.io.reload #= false
      dut.clockDomain.waitSampling(1)
      for (_ <- 0 until 10) {
        assert(!dut.io.tick.toBoolean, "- ClockDivider shouldn't tick yet")
        dut.clockDomain.waitSampling(1)
      }
      assert(dut.io.tick.toBoolean, "ClockDivider should tick now!")
      dut.clockDomain.waitSampling(1)
      for (_ <- 0 until 10) {
        assert(!dut.io.tick.toBoolean, "- ClockDivider shouldn't tick yet")
        dut.clockDomain.waitSampling(1)
      }
      assert(dut.io.tick.toBoolean, "ClockDivider should tick now!")
      dut.clockDomain.waitSampling(1)
      assert(!dut.io.tick.toBoolean, "- ClockDivider shouldn't tick yet")
    }
    compiled.doSim("test reload") { dut =>
      dut.clockDomain.forkStimulus(10)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      dut.io.value #= 10
      dut.io.reload #= true
      dut.clockDomain.waitSampling(1)
      dut.io.reload #= false
      dut.clockDomain.waitSampling(5)
      dut.io.reload #= true
      dut.clockDomain.waitSampling(1)
      dut.io.reload #= false
      dut.clockDomain.waitSampling(1)
      for (_ <- 0 until 10) {
        assert(!dut.io.tick.toBoolean, "- ClockDivider shouldn't tick yet")
        dut.clockDomain.waitSampling(1)
      }
      assert(dut.io.tick.toBoolean, "ClockDivider should tick now!")
      dut.clockDomain.waitSampling(1)
      assert(!dut.io.tick.toBoolean, "- ClockDivider shouldn't tick yet")
    }
  }
}
