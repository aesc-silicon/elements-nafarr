package nafarr.peripherals.io.gpio

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class Apb3GpioTest extends AnyFunSuite {
  test("parameters") {
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.default()))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.noInterrupt()))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.onlyOutput()))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.onlyInput()))

    generationShouldFail(Apb3Gpio(GpioCtrl.Parameter.default(0)))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.default(1)))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.default(33)))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Gpio(GpioCtrl.Parameter(
          io = Gpio.Parameter(32),
          readBufferDepth = 1,
          input = Seq[Int](0, 1, 2, 3, 5, 7, 31),
          output = Seq[Int](0, 3, 4, 5, 6, 7, 31),
          interrupt = Seq[Int](0, 3, 5, 7, 31)
        )
      )
      dut.ctrl.io.value.simPublic()
      dut
    }
    compiled.doSim("testIO") { dut =>
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

      /* Init */
      dut.io.gpio.pins.read #= 0
      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )

      /* Check IP identification */
      assert(
        apb.read(BigInt(0)) == BigInt("00080000", 16),
        "IP Identification 0x0 should return 00080000 - API: 0, Length: 8, ID: 0"
      )
      assert(
        apb.read(BigInt(4)) == BigInt("01000000", 16),
        "IP Identification 0x4 should return 01000000 - 1.0.0"
      )

      /* Read bank and pin count */
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010020", 16),
        "Unable to read 00010020 from GPIO bank/pin declaration"
      )

      /* Check if input synchronization works */
      dut.io.gpio.pins.read #= BigInt("FFFFFFFF", 16)
      dut.clockDomain.waitFallingEdge(dut.ctrl.p.readBufferDepth)
      assert(
        dut.ctrl.io.value.toBigInt == BigInt("00000000", 16),
        "Input is not 0x00000000"
      )
      dut.clockDomain.waitFallingEdge(1)
      assert(
        dut.ctrl.io.value.toBigInt == BigInt("FFFFFFFF", 16),
        "Input is not 0xFFFFFFFF"
      )
      /* Check if value is accessible by APB */
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("800000AF", 16),
        "Unable to read 800000AF from GPIO read"
      )

      /* Check if GPIO write works */
      dut.clockDomain.waitFallingEdge()
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      dut.clockDomain.waitFallingEdge(1)
      assert(dut.io.gpio.pins.write.toBigInt == BigInt("800000F9", 16))
      assert(
        apb.read(BigInt(regOffset + 8)) == BigInt("800000F9", 16),
        "Unable to read 800000F9 from GPIO write"
      )

      /* Check if GPIO direction works */
      dut.clockDomain.waitFallingEdge()
      apb.write(BigInt(regOffset + 12), BigInt("FFFFFFFF", 16))
      dut.clockDomain.waitFallingEdge(1)
      assert(dut.io.gpio.pins.writeEnable.toBigInt == BigInt("800000F9", 16))
      assert(
        apb.read(BigInt(regOffset + 12)) == BigInt("800000F9", 16),
        "Unable to read 800000F9 from GPIO write"
      )

    }
    compiled.doSim("testIRQ") { dut =>
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

      /* Init */
      dut.io.gpio.pins.read #= 0
      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )

      /* Test interrupt on high signal */
      apb.write(BigInt(regOffset + 16), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 20), BigInt("FFFFFFFF", 16))
      dut.io.gpio.pins.read #= BigInt("FFFFFFFF", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth + 1) {
        assert(
          dut.io.interrupt.toBigInt == 0,
          f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
        )
        dut.clockDomain.waitFallingEdge()
      }
      assert(
        dut.io.interrupt.toBigInt == 1,
        f"Interrupt not pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )
      apb.write(BigInt(regOffset + 20), BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )

      /* Test interrupt on low signal */
      apb.write(BigInt(regOffset + 24), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 28), BigInt("FFFFFFFF", 16))
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth + 1) {
        assert(
          dut.io.interrupt.toBigInt == 0,
          f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
        )
        dut.clockDomain.waitFallingEdge()
      }
      assert(
        dut.io.interrupt.toBigInt == 1,
        f"Interrupt not pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )
      apb.write(BigInt(regOffset + 28), BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )

      /* Test interrupt on rising signal */
      apb.write(BigInt(regOffset + 32), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 36), BigInt("FFFFFFFF", 16))
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )
      dut.clockDomain.waitFallingEdge()
      dut.io.gpio.pins.read #= BigInt("FFFFFFFF", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth) {
        assert(
          dut.io.interrupt.toBigInt == 0,
          f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
        )
        dut.clockDomain.waitFallingEdge()
      }
      assert(
        dut.io.interrupt.toBigInt == 1,
        f"Interrupt not pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )
      apb.write(BigInt(regOffset + 36), BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )

      /* Test interrupt on falling signal */
      apb.write(BigInt(regOffset + 40), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 44), BigInt("FFFFFFFF", 16))
      dut.io.gpio.pins.read #= BigInt("FFFFFFFF", 16)
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )
      dut.clockDomain.waitFallingEdge()
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth) {
        assert(
          dut.io.interrupt.toBigInt == 0,
          f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
        )
        dut.clockDomain.waitFallingEdge()
      }
      assert(
        dut.io.interrupt.toBigInt == 1,
        f"Interrupt not pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )
      apb.write(BigInt(regOffset + 44), BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      assert(
        dut.io.interrupt.toBigInt == 0,
        f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
      )

    }
  }
}
