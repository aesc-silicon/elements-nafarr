// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.i2c

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import nafarr.system.dma.DmaHandshakeSim


class I2cControllerTest extends AnyFunSuite {
  def genCore[T <: spinal.core.Data with IMasterSlave](
      parameter: I2cControllerCtrl.Parameter,
      constructor: I2cControllerCtrl.Parameter => I2cController.Core[T]
  ): I2cController.Core[T] = {
    val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
    val area = new ClockingArea(cd) {
      val dut = constructor(parameter)
    }
    area.dut
  }

  test("Apb3I2cControllerParameters") {
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.lightweight(), Apb3I2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.default(), Apb3I2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full(), Apb3I2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full(1), Apb3I2cController(_)))
    generationShouldPass {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter(100),
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter, Apb3I2cController(_))
    }

    generationShouldFail {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter.disabled,
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter, Apb3I2cController(_))
    }
    generationShouldFail(genCore(I2cControllerCtrl.Parameter(io = I2c.Parameter(0), clockDividerWidth = 0), Apb3I2cController(_)))
  }

  test("TileLinkI2cControllerParameters") {
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.lightweight(), TileLinkI2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.default(), TileLinkI2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full(), TileLinkI2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full(1), TileLinkI2cController(_)))
    generationShouldPass {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter(100),
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter, TileLinkI2cController(_))
    }

    generationShouldFail {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter.disabled,
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter, TileLinkI2cController(_))
    }
    generationShouldFail(genCore(I2cControllerCtrl.Parameter(io = I2c.Parameter(0), clockDividerWidth = 0), TileLinkI2cController(_)))
  }

  test("WishboneI2cControllerParameters") {
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.lightweight(), WishboneI2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.default(), WishboneI2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full(), WishboneI2cController(_)))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full(1), WishboneI2cController(_)))
    generationShouldPass {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter(100),
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter, WishboneI2cController(_))
    }

    generationShouldFail {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter.disabled,
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter, WishboneI2cController(_))
    }
    generationShouldFail(genCore(I2cControllerCtrl.Parameter(io = I2c.Parameter(0), clockDividerWidth = 0), WishboneI2cController(_)))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile(genCore(I2cControllerCtrl.Parameter.default(), Apb3I2cController(_)))

    compiled.doSim("basicRegisters") { dut =>
      DmaHandshakeSim.release(dut.io.dmaRequest)
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

      /* DMA request lines: empty command FIFO accepts, empty response FIFO has nothing */
      assert(dut.io.dmaRequest.tx.req.toBoolean, "DMA tx request low with empty command FIFO")
      assert(!dut.io.dmaRequest.rx.req.toBoolean, "DMA rx request high with empty response FIFO")
      DmaHandshakeSim.checkAck(dut.io.dmaRequest.tx, dut.clockDomain, "I2C tx")

      /* Check IP identification */
      assert(
        apb.read(BigInt(0)) == BigInt("00080004", 16),
        "IP Identification 0x0 should return 00080001 - API: 0, Length: 8, ID: 4"
      )
      assert(
        apb.read(BigInt(4)) == BigInt("01000000", 16),
        "IP Identification 0x4 should return 01000000 - 1.0.0"
      )

      /* Read clockDividerWidth, timeoutWidth */
      assert(
        apb.read(BigInt(staticOffset)) == BigInt("00000010", 16),
        "Unable to read 00000010 from I2cController clockDivier width declaration"
      )

      /* Read cmd/rsp FIFO depth */
      assert(
        apb.read(BigInt(staticOffset + 4)) == BigInt("00001010", 16),
        "Unable to read 00001010 from I2cController FIFO depth declaration"
      )

      /* Read permissions */
      assert(
        apb.read(BigInt(staticOffset + 8)) == BigInt("00000001", 16),
        "Unable to read 00000001 from I2cController permission declaration"
      )

      /* Read FIFO status */
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00100000", 16),
        "Unable to read 00100000 from I2cController FIFO status"
      )
    }
  }

  test("DMA request") {
    // Clock divider set at reset: a divider written while the controller is idle only takes
    // effect after the reset value has counted down.
    val parameter =
      I2cControllerCtrl.Parameter.default().copy(init = I2cControllerCtrl.InitParameter(10))
    val compiled = SimConfig.withWave.compile(genCore(parameter, Apb3I2cController(_)))

    compiled.doSim("dmaRequest") { dut =>
      DmaHandshakeSim.release(dut.io.dmaRequest)
      val cd = dut.clockDomain
      cd.forkStimulus(10)
      val apb = new Apb3Driver(dut.io.bus, cd)
      val regOffset = dut.mapper.regOffset
      val tx = dut.io.dmaRequest.tx
      val rx = dut.io.dmaRequest.rx

      /* Open-drain lines with pull-ups and no device: write pulls a line low */
      dut.io.i2c.scl.read #= true
      dut.io.i2c.sda.read #= true
      cd.onSamplings {
        dut.io.i2c.scl.read #= !dut.io.i2c.scl.write.toBoolean
        dut.io.i2c.sda.read #= !dut.io.i2c.sda.write.toBoolean
      }
      cd.waitSampling(2)

      /* Idle: the empty command FIFO accepts an entry, no response is waiting */
      assert(tx.req.toBoolean, "DMA tx request low with empty command FIFO")
      assert(!rx.req.toBoolean, "DMA rx request high with empty response FIFO")
      DmaHandshakeSim.checkAck(tx, cd, "I2C tx")

      /* RX: a START + READ command produces a response, reading it drains the FIFO */
      apb.write(regOffset, (1 << 8) | (1 << 10))
      DmaHandshakeSim.waitReq(rx, cd, true, 5000, "I2C rx after a read command")
      DmaHandshakeSim.checkAck(rx, cd, "I2C rx")
      assert((apb.read(regOffset) >> 31) == 1, "I2C response not valid")
      cd.waitSampling(2)
      assert(!rx.req.toBoolean, "DMA rx request high after response FIFO drained")

      /* TX: no request while the command FIFO is full, request again once one is consumed */
      val depth = I2cControllerCtrl.Parameter.default().memory.cmdFifoDepth
      for (_ <- 0 to depth) {
        apb.write(regOffset, 0x55)
      }
      cd.waitSampling(2)
      assert(((apb.read(regOffset + 0x04) >> 16) & 0xffff) == 0, "I2C command FIFO not full")
      assert(!tx.req.toBoolean, "DMA tx request high with full command FIFO")
      DmaHandshakeSim.waitReq(tx, cd, true, 5000, "I2C tx after a command was consumed")
    }
  }
}
