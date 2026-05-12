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



}
