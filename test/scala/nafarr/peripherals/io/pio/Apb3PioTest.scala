// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.pio

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class Apb3PioTest extends AnyFunSuite {
  def fillCommands(apb: Apb3Driver, regOffset: Int, commands: List[BigInt]) {
    // Disable engine and reset write pointer
    apb.write(BigInt(regOffset), BigInt("00000", 2))
    apb.write(BigInt(regOffset + 8), BigInt("00000", 2))
    for (cmd <- commands) {
      apb.write(BigInt(regOffset + 4), cmd)
    }
    // Enable engine
    apb.write(BigInt(regOffset), BigInt("00001", 2))
  }

  def generateCmd(pin: Int, cmd: SpinalEnumElement[PioCtrl.CommandType.type], data: Option[BigInt] = None) = {
    data match {
      case Some(d) => (d << 8) | BigInt(pin << 4 | cmd.position)
      case None    => BigInt(pin << 4 | cmd.position)
    }
  }


  test("parameters") {
    generationShouldFail(Apb3Pio(PioCtrl.Parameter.default(0)))
    generationShouldPass(Apb3Pio(PioCtrl.Parameter.default(1)))
    generationShouldPass(Apb3Pio(PioCtrl.Parameter.default(16)))
    generationShouldFail(Apb3Pio(PioCtrl.Parameter.default(17)))

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
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), dataWidth = 25)
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
        apb.read(BigInt(4)) == BigInt("01010000", 16),
        "IP Identification 0x4 should return 01000000 - 1.1.0"
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
        apb.read(BigInt(regOffset + 8)) == BigInt("00000000", 16),
        "Unable to read 00000000 from Pio FIFO status"
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
      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 0"
      )
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 1"
      )

      dut.io.pio.pins.read #= BigInt("01", 2)
      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010001", 16),
        "Unable to read value 1 from Pio pin 0"
      )
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 1"
      )

      dut.io.pio.pins.read #= BigInt("10", 2)
      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010000", 16),
        "Unable to read value 0 from Pio pin 0"
      )
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010001", 16),
        "Unable to read value 1 from Pio pin 1"
      )

      dut.io.pio.pins.read #= BigInt("11", 2)
      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010001", 16),
        "Unable to read value 1 from Pio pin 0"
      )
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00010001", 16),
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

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "Default PIO output value should be 00"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("00", 2),
        "PIO direction value should be 00"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.HIGH)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
        "PIO output value should be 01"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("01", 2),
        "PIO direction value should be 01"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(1, PioCtrl.CommandType.HIGH)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("11", 2),
        "PIO output value should be 11"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(1, PioCtrl.CommandType.LOW)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
        "PIO output value should be 01"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.LOW)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "PIO output value should be 00"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.TOGGLE)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
        "PIO output value should be 01"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(1, PioCtrl.CommandType.TOGGLE)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("11", 2),
        "PIO output value should be 11"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.FLOAT)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("10", 2),
        "PIO output value should be 10"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("10", 2),
        "PIO direction value should be 10"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(1, PioCtrl.CommandType.FLOAT)
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "PIO output value should be 00"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("00", 2),
        "PIO direction value should be 00"
      )
    }

    compiled.doSim("toggle IO - SET") { dut =>
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

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "Default PIO output value should be 00"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("00", 2),
        "PIO direction value should be 00"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.HIGH_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("11", 2),
        "PIO output value should be 11"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.LOW_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "PIO output value should be 00"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.TOGGLE_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("11", 2),
        "PIO output value should be 11"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("11", 2),
        "PIO direction value should be 11"
      )

      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.FLOAT_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "PIO output value should be 00"
      )
      assert(
        dut.io.pio.pins.writeEnable.toBigInt == BigInt("00", 2),
        "PIO direction value should be 00"
      )
    }

    compiled.doSim("wait commands") { dut =>
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
      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.LOW),
        generateCmd(0, PioCtrl.CommandType.WAIT, Some(BigInt(5))),
        generateCmd(0, PioCtrl.CommandType.HIGH),
        generateCmd(0, PioCtrl.CommandType.WAIT, Some(BigInt(5))),
        generateCmd(0, PioCtrl.CommandType.LOW)
      ))
      // IDLE -> 1 Cycle
      // LOW -> 1 Cycle
      // IDLE -> 1 Cycle
      // WAIT -> 5 runs * 3 CLOCK TICKS + 1 Cycle
      // IDLE -> 1 Cycle
      // HIGH -> 1 Cycle
      for (index <- 0 to 21) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
          "PIO output value should be 00"
        )
        dut.clockDomain.waitSampling(1)
      }
      // WAIT -> 5 runs * 3 CLOCK TICKS + 1 Cycle
      // IDLE -> 1 Cycle
      // LOW -> 1 Cycle
      // Next clock
      for (index <- 0 until 19) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
          "PIO output value should be 01"
        )
        dut.clockDomain.waitSampling(1)
      }
      assert(
        dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
        "PIO output value should be 00"
      )
    }

    compiled.doSim("wait for commands") { dut =>
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
      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.HIGH),
        generateCmd(1, PioCtrl.CommandType.WAIT_FOR_HIGH),
        generateCmd(0, PioCtrl.CommandType.LOW),
        generateCmd(1, PioCtrl.CommandType.WAIT_FOR_LOW),
        generateCmd(0, PioCtrl.CommandType.HIGH)
      ))
      dut.clockDomain.waitSampling(3)
      for (index <- 0 to 50) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
          "PIO output value should be 01"
        )
        dut.clockDomain.waitSampling(1)
      }
      dut.io.pio.pins.read #= BigInt("10", 2)
      // 2 cycles input buffers
      // IDLE -> 1 Cycle
      // LOW -> 1 Cycle
      // Next clock
      for (index <- 0 to 5) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
          "PIO output value should be 01"
        )
        dut.clockDomain.waitSampling(1)
      }
      for (index <- 0 to 50) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
          "PIO output value should be 00"
        )
        dut.clockDomain.waitSampling(1)
      }
      dut.io.pio.pins.read #= BigInt("00", 2)
      // 2 cycles input buffers
      // IDLE -> 1 Cycle
      // HIGH -> 1 Cycle
      // Next clock
      for (index <- 0 to 5) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
          "PIO output value should be 00"
        )
        dut.clockDomain.waitSampling(1)
      }
      for (index <- 0 to 50) {
        assert(
          dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
          "PIO output value should be 01"
        )
        dut.clockDomain.waitSampling(1)
      }
    }

    compiled.doSim("loop commands") { dut =>
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
      fillCommands(apb, regOffset, List(
        generateCmd(0, PioCtrl.CommandType.HIGH),
        generateCmd(0, PioCtrl.CommandType.WAIT, Some(BigInt(5))),
        generateCmd(0, PioCtrl.CommandType.LOW),
        generateCmd(0, PioCtrl.CommandType.WAIT, Some(BigInt(5))),
        generateCmd(0, PioCtrl.CommandType.LOOP, Some(BigInt(3)))
      ))
      dut.clockDomain.waitSampling(3)
      for (loop <- 0 to 2) {
        // WAIT -> 5 runs * 3 CLOCK TICKS + 1 Cycle
        // IDLE -> 1 Cycle
        // LOW -> 1 Cycle
        for (index <- 0 to 18) {
          assert(
            dut.io.pio.pins.write.toBigInt == BigInt("01", 2),
            "PIO output value should be 01"
          )
          dut.clockDomain.waitSampling(1)
        }
        // WAIT -> 5 runs * 3 CLOCK TICKS + 1 Cycle
        // IDLE -> 1 Cycle
        // LOOP -> 1 Cycle
        // IDLE -> 1 Cycle
        // Next clock
        for (index <- 0 until 21) {
          assert(
            dut.io.pio.pins.write.toBigInt == BigInt("00", 2),
            "PIO output value should be 00"
          )
          dut.clockDomain.waitSampling(1)
        }
      }
    }
  }
}
