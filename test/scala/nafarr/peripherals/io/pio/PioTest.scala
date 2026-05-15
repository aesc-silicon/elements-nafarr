// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.pio

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

class PioTest extends AnyFunSuite {
  def fillCommands(apb: Apb3Driver, regs: PioCtrl.Regs, commands: List[BigInt]) {
    // Disable engine and reset write pointer
    apb.write(regs.control, BigInt("00000", 2))
    apb.write(regs.fifoStatus, BigInt("00000", 2))
    for (cmd <- commands) {
      apb.write(regs.readWrite, cmd)
    }
    // Enable engine
    apb.write(regs.control, BigInt("00001", 2))
  }

  def generateCmd(pin: Int, cmd: SpinalEnumElement[PioCtrl.CommandType.type], data: Option[BigInt] = None) = {
    data match {
      case Some(d) => (d << 8) | BigInt(pin << 4 | cmd.position)
      case None    => BigInt(pin << 4 | cmd.position)
    }
  }

  def init(dut: Apb3Pio): (Apb3Driver, PioCtrl.Regs) = {
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
    val regs = PioCtrl.Regs(dut.mapper.idCtrl.length)

    /* Wait for reset and check initialized state */
    dut.clockDomain.waitSampling(2)
    dut.clockDomain.waitFallingEdge()

    return (apb, regs)
  }

  test("Apb3PioParameters") {
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

  test("TileLinkPioParameters") {
    generationShouldFail(TileLinkPio(PioCtrl.Parameter.default(0)))
    generationShouldPass(TileLinkPio(PioCtrl.Parameter.default(1)))
    generationShouldPass(TileLinkPio(PioCtrl.Parameter.default(16)))
    generationShouldFail(TileLinkPio(PioCtrl.Parameter.default(17)))

    generationShouldPass(TileLinkPio(PioCtrl.Parameter.light()))

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), readBufferDepth = 0,
                                        permission = null, init = PioCtrl.InitParameter(100))
      TileLinkPio(parameter)
    }

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), readBufferDepth = 0,
                                       init = null)
      TileLinkPio(parameter)
    }

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(10), dataWidth = 20)
      TileLinkPio(parameter)
    }

    generationShouldFail {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), dataWidth = 25)
      TileLinkPio(parameter)
    }

    generationShouldFail {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), init = null, permission = null)
      TileLinkPio(parameter)
    }
  }

  test("WishbonePioParameters") {
    generationShouldFail(WishbonePio(PioCtrl.Parameter.default(0)))
    generationShouldPass(WishbonePio(PioCtrl.Parameter.default(1)))
    generationShouldPass(WishbonePio(PioCtrl.Parameter.default(16)))
    generationShouldFail(WishbonePio(PioCtrl.Parameter.default(17)))

    generationShouldPass(WishbonePio(PioCtrl.Parameter.light()))

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), readBufferDepth = 0,
                                        permission = null, init = PioCtrl.InitParameter(100))
      WishbonePio(parameter)
    }

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), readBufferDepth = 0,
                                       init = null)
      WishbonePio(parameter)
    }

    generationShouldPass {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(10), dataWidth = 20)
      WishbonePio(parameter)
    }

    generationShouldFail {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), dataWidth = 25)
      WishbonePio(parameter)
    }

    generationShouldFail {
      val parameter = PioCtrl.Parameter(io = Pio.Parameter(1), init = null, permission = null)
      WishbonePio(parameter)
    }
  }


  test("basic") {
    val compiled = SimConfig.withWave.compile {
      Apb3Pio(PioCtrl.Parameter(io=Pio.Parameter(2), init=PioCtrl.InitParameter(2, 2)))
    }

    compiled.doSim("basicRegisters") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Pio)
      IpIdentificationTest.V0.checkVersion(apb, 1, 1, 0)

      /* Read readBufferDepth, clockDividerWidth, dataWidth, io.Width */
      SimTest.readField(apb, regs.dataWidth, 31, 24, 2, "read buffer depth")
      SimTest.readField(apb, regs.dataWidth, 23, 16, 20, "clock divider width")
      SimTest.readField(apb, regs.dataWidth, 15, 8, 24, "FIFO data width")
      SimTest.readField(apb, regs.dataWidth, 7, 0, 2, "IO width")

      /* Read readFifoDepth, commandFifoDepth */
      SimTest.readField(apb, regs.fifoDepth, 15, 8, 8, "Read FIFO depth")
      SimTest.readField(apb, regs.fifoDepth, 7, 0, 16, "Command FIFO depth")

      /* Read permissions */
      SimTest.readField(apb, regs.permissions, 1, 0, 1, "Permissions")

      /* Read FIFO status */
      SimTest.readField(apb, regs.fifoStatus, 7, 0, 0, "FIFO Status - Exec pointer")
      SimTest.readField(apb, regs.fifoStatus, 15, 8, 0, "FIFO Status - Write pointer")
    }

    compiled.doSim("read value") { dut =>
      val (apb, regs) = init(dut)

      dut.io.pio.pins.read #= BigInt("00", 2)
      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      SimTest.read(apb, regs.readWrite, BigInt("00010000", 16), "Unable to read value 0 from Pio pin 0")
      SimTest.read(apb, regs.readWrite, BigInt("00010000", 16), "Unable to read value 0 from Pio pin 1")

      dut.io.pio.pins.read #= BigInt("01", 2)
      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      SimTest.read(apb, regs.readWrite, BigInt("00010001", 16), "Unable to read value 1 from Pio pin 0")
      SimTest.read(apb, regs.readWrite, BigInt("00010000", 16), "Unable to read value 0 from Pio pin 1")

      dut.io.pio.pins.read #= BigInt("10", 2)
      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      SimTest.read(apb, regs.readWrite, BigInt("00010000", 16), "Unable to read value 0 from Pio pin 0")
      SimTest.read(apb, regs.readWrite, BigInt("00010001", 16), "Unable to read value 1 from Pio pin 1")

      dut.io.pio.pins.read #= BigInt("11", 2)
      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.READ),
        generateCmd(1, PioCtrl.CommandType.READ)
      ))
      dut.clockDomain.waitSampling(15)
      SimTest.read(apb, regs.readWrite, BigInt("00010001", 16), "Unable to read value 1 from Pio pin 0")
      SimTest.read(apb, regs.readWrite, BigInt("00010001", 16), "Unable to read value 1 from Pio pin 1")
    }

    compiled.doSim("toggle IO") { dut =>
      val (apb, regs) = init(dut)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "Default PIO output value should be 00")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("00", 2), "Default PIO direction value should be 00")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.HIGH)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("01", 2), "PIO direction value should be 01")

      fillCommands(apb, regs, List(
        generateCmd(1, PioCtrl.CommandType.HIGH)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("11", 2), "PIO output value should be 11")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(1, PioCtrl.CommandType.LOW)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.LOW)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.TOGGLE)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(1, PioCtrl.CommandType.TOGGLE)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("11", 2), "PIO output value should be 11")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.FLOAT)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("10", 2), "PIO output value should be 10")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("10", 2), "PIO direction value should be 10")

      fillCommands(apb, regs, List(
        generateCmd(1, PioCtrl.CommandType.FLOAT)
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("00", 2), "PIO direction value should be 00")
    }

    compiled.doSim("toggle IO - SET") { dut =>
      val (apb, regs) = init(dut)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("00", 2), "PIO direction value should be 00")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.HIGH_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("11", 2), "PIO output value should be 11")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.LOW_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.TOGGLE_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("11", 2), "PIO output value should be 11")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("11", 2), "PIO direction value should be 11")

      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.FLOAT_SET, Some(BigInt("11", 2)))
      ))
      dut.clockDomain.waitSampling(4)

      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
      SimTest.checkPins(dut.io.pio.pins.writeEnable.toBigInt, BigInt("00", 2), "PIO direction value should be 00")
    }

    compiled.doSim("wait commands") { dut =>
      val (apb, regs) = init(dut)

      dut.io.pio.pins.read #= BigInt("00", 2)
      fillCommands(apb, regs, List(
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
        SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
        dut.clockDomain.waitSampling(1)
      }
      // WAIT -> 5 runs * 3 CLOCK TICKS + 1 Cycle
      // IDLE -> 1 Cycle
      // LOW -> 1 Cycle
      // Next clock
      for (index <- 0 until 19) {
        SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
        dut.clockDomain.waitSampling(1)
      }
      SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
    }

    compiled.doSim("wait for commands") { dut =>
      val (apb, regs) = init(dut)

      dut.io.pio.pins.read #= BigInt("00", 2)
      fillCommands(apb, regs, List(
        generateCmd(0, PioCtrl.CommandType.HIGH),
        generateCmd(1, PioCtrl.CommandType.WAIT_FOR_HIGH),
        generateCmd(0, PioCtrl.CommandType.LOW),
        generateCmd(1, PioCtrl.CommandType.WAIT_FOR_LOW),
        generateCmd(0, PioCtrl.CommandType.HIGH)
      ))
      dut.clockDomain.waitSampling(3)
      for (index <- 0 to 50) {
        SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
        dut.clockDomain.waitSampling(1)
      }
      dut.io.pio.pins.read #= BigInt("10", 2)
      // 2 cycles input buffers
      // IDLE -> 1 Cycle
      // LOW -> 1 Cycle
      // Next clock
      for (index <- 0 to 5) {
        SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
        dut.clockDomain.waitSampling(1)
      }
      for (index <- 0 to 50) {
        SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
        dut.clockDomain.waitSampling(1)
      }
      dut.io.pio.pins.read #= BigInt("00", 2)
      // 2 cycles input buffers
      // IDLE -> 1 Cycle
      // HIGH -> 1 Cycle
      // Next clock
      for (index <- 0 to 5) {
        SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
        dut.clockDomain.waitSampling(1)
      }
      for (index <- 0 to 50) {
        SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
        dut.clockDomain.waitSampling(1)
      }
    }

    compiled.doSim("loop commands") { dut =>
      val (apb, regs) = init(dut)

      dut.io.pio.pins.read #= BigInt("00", 2)
      fillCommands(apb, regs, List(
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
          SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("01", 2), "PIO output value should be 01")
          dut.clockDomain.waitSampling(1)
        }
        // WAIT -> 5 runs * 3 CLOCK TICKS + 1 Cycle
        // IDLE -> 1 Cycle
        // LOOP -> 1 Cycle
        // IDLE -> 1 Cycle
        // Next clock
        for (index <- 0 until 21) {
          SimTest.checkPins(dut.io.pio.pins.write.toBigInt, BigInt("00", 2), "PIO output value should be 00")
          dut.clockDomain.waitSampling(1)
        }
      }
    }
  }
}
