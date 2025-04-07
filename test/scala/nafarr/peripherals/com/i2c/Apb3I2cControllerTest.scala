package nafarr.peripherals.com.i2c

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver


class Apb3I2cControllerTest extends AnyFunSuite {
  def genCore(parameter: I2cControllerCtrl.Parameter) = {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Apb3I2cController(parameter)
      }
      area.dut
  }

  test("parameters") {
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.lightweight()))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.default()))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full()))
    generationShouldPass(genCore(I2cControllerCtrl.Parameter.full(1)))
    generationShouldPass {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter(100),
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter)
    }

    generationShouldFail {
      val parameter = I2cControllerCtrl.Parameter(
        io = I2c.Parameter(0),
        init = I2cControllerCtrl.InitParameter.disabled,
        permission = I2cControllerCtrl.PermissionParameter.restricted,
        memory = I2cControllerCtrl.MemoryMappedParameter.default
      )
      genCore(parameter)
    }
    generationShouldFail(genCore(I2cControllerCtrl.Parameter(io = I2c.Parameter(0), clockDividerWidth = 0)))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile(genCore(I2cControllerCtrl.Parameter.default()))

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
