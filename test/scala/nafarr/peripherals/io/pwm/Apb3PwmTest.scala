package nafarr.peripherals.io.pwm

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class Apb3PwmTest extends AnyFunSuite {
  test("parameters") {
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default()))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(1)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(2)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(3)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(4)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(5)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(6)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(7)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(8)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(9)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(10)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(11)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(12)))

    generationShouldFail(Apb3Pwm(PwmCtrl.Parameter.default(0)))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Pwm(PwmCtrl.Parameter.default(1))
      dut
    }
    compiled.doSim("basicRegisters") { dut =>
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
      val regOffset = dut.mapper.offset

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Check IP identification */
      assert(
        apb.read(BigInt(0)) == BigInt("00080002", 16),
        "IP Identification 0x0 should return 00080002 - API: 0, Length: 8, ID: 2"
      )
      assert(
        apb.read(BigInt(4)) == BigInt("01000000", 16),
        "IP Identification 0x4 should return 01000000 - 1.0.0"
      )

      /* Read channelPulseWidth, channelPeriodWidth, clockDividerWidth, io.channels */
      assert(
        apb.read(BigInt(regOffset)) == BigInt("14141401", 16),
        "Unable to read 14141401 from PWM config/channel declaration"
      )
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
      val regOffset = dut.mapper.offset

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Init - Set clock divider to 1 us */
      apb.write(BigInt(regOffset + 4), BigInt("99", 10))

      /* Init channel 0 */
      apb.write(BigInt(regOffset + 12), BigInt("9", 10))
      apb.write(BigInt(regOffset + 16), BigInt("5", 10))

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      apb.write(BigInt(regOffset + 8), BigInt("1", 16))
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
      val regOffset = dut.mapper.offset

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Init - Set clock divider to 1 us */
      apb.write(BigInt(regOffset + 4), BigInt("99", 10))

      /* Init channel 0 */
      apb.write(BigInt(regOffset + 12), BigInt("9", 10))
      apb.write(BigInt(regOffset + 16), BigInt("5", 10))

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      apb.write(BigInt(regOffset + 8), BigInt("1", 16))
      dut.clockDomain.waitSampling(100)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(10)
      apb.write(BigInt(regOffset + 8), BigInt("0", 16))
      apb.write(BigInt(regOffset + 12), BigInt("9", 10))
      apb.write(BigInt(regOffset + 16), BigInt("3", 10))

      dut.clockDomain.waitSampling(90)
      apb.write(BigInt(regOffset + 8), BigInt("1", 16))
      dut.clockDomain.waitSampling(100)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(3 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(7 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
    }
  }
}
