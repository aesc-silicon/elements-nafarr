package nafarr.peripherals.io.pio

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class Apb3PioTest extends AnyFunSuite {
  test("parameters") {
    generationShouldFail(Apb3Pio(PioCtrl.Parameter.default(0)))
    generationShouldPass(Apb3Pio(PioCtrl.Parameter.default(1)))
    generationShouldPass(Apb3Pio(PioCtrl.Parameter.default(6)))
    generationShouldFail(Apb3Pio(PioCtrl.Parameter.default(7)))

    generationShouldPass(Apb3Pio(PioCtrl.Parameter.light()))

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), readBufferDepth = 0,
                                        permission = null, init = PioCtrl.InitParameter(100))
      Apb3Pio(parameter)
    }

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), readBufferDepth = 0,
                                       init = null)
      Apb3Pio(parameter)
    }

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(10), dataWidth = 20)
      Apb3Pio(parameter)
    }

    generationShouldFail {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), init = null, permission = null)
      Apb3Pio(parameter)
    }
  }
  test("basic") {
    val compiled = SimConfig.withWave.compile {
      Apb3Pio(PioCtrl.Parameter(io=Pio.Parameter(2), init=PioCtrl.InitParameter(2, 2)))
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
      val staticOffset = dut.mapper.staticOffset
      val regOffset = dut.mapper.regOffset

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Check IP identification */
      assert(
        apb.read(BigInt(0)) == BigInt("00080001", 16),
        "IP Identification 0x0 should return 00080001 - API: 0, Length: 8, ID: 1"
      )
      assert(
        apb.read(BigInt(4)) == BigInt("01000000", 16),
        "IP Identification 0x4 should return 01000000 - 1.0.0"
      )

      /* Read readBufferDepth, clockDividerWidth, dataWidth, io.Width */
      assert(
        apb.read(BigInt(staticOffset)) == BigInt("02141802", 16),
        "Unable to read 02141401 from Pio config/IO width declaration"
      )

      /* Read readFifoDepth, commandFifoDepth */
      assert(
        apb.read(BigInt(staticOffset + 4)) == BigInt("00000810", 16),
        "Unable to read 00000810 from Pio FIFO width declaration"
      )

      /* Read permissions */
      assert(
        apb.read(BigInt(staticOffset + 8)) == BigInt("00000001", 16),
        "Unable to read 00000001 from Pio permission declaration"
      )

      /* Read FIFO status */
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00100000", 16),
        "Unable to read 00100000 from Pio FIFO status"
      )
    }
    compiled.doSim("read value") { dut =>
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
      val regOffset = dut.mapper.regOffset

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      dut.io.pio.pins.read #= BigInt("00", 2)
      apb.write(BigInt(regOffset), BigInt("011", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 0"
      )
      apb.write(BigInt(regOffset), BigInt("111", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 1"
      )

      dut.io.pio.pins.read #= BigInt("01", 2)
      apb.write(BigInt(regOffset), BigInt("011", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010001", 16),
        "Unable to read value 1 from Pio pin 0"
      )
      apb.write(BigInt(regOffset), BigInt("111", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 1"
      )

      dut.io.pio.pins.read #= BigInt("10", 2)
      apb.write(BigInt(regOffset), BigInt("011", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 0"
      )
      apb.write(BigInt(regOffset), BigInt("111", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010001", 16),
        "Unable to read value 1 from Pio pin 1"
      )

      dut.io.pio.pins.read #= BigInt("11", 2)
      apb.write(BigInt(regOffset), BigInt("011", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010001", 16),
        "Unable to read value 1 from Pio pin 0"
      )
      apb.write(BigInt(regOffset), BigInt("111", 2))
      dut.clockDomain.waitSampling(10)
      assert(
        apb.read(BigInt(regOffset)) == BigInt("00010001", 16),
        "Unable to read value 1 from Pio pin 1"
      )
    }
    compiled.doSim("toggle IO") { dut =>
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
      val regOffset = dut.mapper.regOffset

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Set value HIGH */
      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "Default PIO output value should be 00"
      )
      apb.write(BigInt(regOffset), BigInt("000", 2))
      dut.clockDomain.waitSampling(4)
      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
        "PIO output value should be 01"
      )
      assert(dut.io.pio.pins.writeEnable.toBigInt == BigInt("01", 2))
      apb.write(BigInt(regOffset), BigInt("100", 2))
      dut.clockDomain.waitSampling(4)
      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("11", 2),
        "PIO output value should be 11"
      )
      assert(dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2))

      /* Wait 2 clock cycles and set LOW */
      apb.write(BigInt(regOffset), BigInt("10010", 2))
      apb.write(BigInt(regOffset), BigInt("001", 2))

      /* clock divider = 2 (3 cycles), 2 wait cycles result in 6 cycles + 4 cycles overhead */
      for (_ <- 0 until 10) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("11", 2),
          "PIO output value should be 11"
        )
        assert(dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2))
        dut.clockDomain.waitSampling(1)
      }

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("10", 2),
        "PIO output value should be 01"
      )
      assert(dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2))


      /* Wait 6 clock cycles and set LOW */
      apb.write(BigInt(regOffset), BigInt("110010", 2))
      apb.write(BigInt(regOffset), BigInt("101", 2))

      /* clock divider = 2 (3 cycles), 6 wait cycles result in 18 cycles + 4 cycles overhead */
      for (_ <- 0 until 22) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("10", 2),
          "PIO output value should be 11"
        )
        assert(dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2))
        dut.clockDomain.waitSampling(1)
      }

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "PIO output value should be 00"
      )
      assert(dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2))
    }
  }
}
