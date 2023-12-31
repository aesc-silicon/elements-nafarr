package nafarr.peripherals.io.pwm

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class Apb3PwmTest extends AnyFunSuite {
  test("parameters") {
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(1)))

    generationShouldFail(Apb3Pwm(PwmCtrl.Parameter.default(0)))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Pwm(PwmCtrl.Parameter.default(1))
      dut
    }
    compiled.doSim("channel1dutyCycle") { dut =>
      dut.clockDomain.forkStimulus(10)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Init - Set clock divider to 1 us */
      apb.write(BigInt("00", 16), BigInt("99", 10))

      /* Init channel 0 */
      apb.write(BigInt("14", 16), BigInt("9", 10))
      apb.write(BigInt("18", 16), BigInt("5", 10))

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      apb.write(BigInt("10", 16), BigInt("1", 16))
      dut.clockDomain.waitSampling(100)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
    }
    compiled.doSim("channel1StartStopStart") { dut =>
      dut.clockDomain.forkStimulus(10)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Init - Set clock divider to 1 us */
      apb.write(BigInt("00", 16), BigInt("99", 10))

      /* Init channel 0 */
      apb.write(BigInt("14", 16), BigInt("9", 10))
      apb.write(BigInt("18", 16), BigInt("5", 10))

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      apb.write(BigInt("10", 16), BigInt("1", 16))
      dut.clockDomain.waitSampling(100)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(10)
      apb.write(BigInt("10", 16), BigInt("0", 16))
      apb.write(BigInt("14", 16), BigInt("9", 10))
      apb.write(BigInt("18", 16), BigInt("3", 10))

      dut.clockDomain.waitSampling(90)
      apb.write(BigInt("10", 16), BigInt("1", 16))
      dut.clockDomain.waitSampling(100)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(3 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(7 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
    }
  }
}
