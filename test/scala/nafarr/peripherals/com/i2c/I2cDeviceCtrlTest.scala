// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.i2c

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._


class I2cDeviceCtrlTest extends AnyFunSuite {
  def genCore(parameter: I2cDeviceCtrl.Parameter) = {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = I2cDeviceCtrl(parameter)
      }
      area.dut
  }

  test("parameters") {
    generationShouldPass(genCore(I2cDeviceCtrl.Parameter.default()))
    generationShouldPass(genCore(I2cDeviceCtrl.Parameter.default(1)))
    generationShouldPass(genCore(I2cDeviceCtrl.Parameter.default(2)))

    generationShouldFail(genCore(I2cDeviceCtrl.Parameter(I2c.Parameter(0), clockDividerWidth = 0)))
    generationShouldFail(genCore(I2cDeviceCtrl.Parameter(I2c.Parameter(0), timeoutWidth = 0)))
    generationShouldFail(genCore(I2cDeviceCtrl.Parameter(I2c.Parameter(0), samplerWidth = 2)))
    generationShouldFail(genCore(I2cDeviceCtrl.Parameter(I2c.Parameter(0), addressWidth = 5)))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(50 MHz))
      val area = new ClockingArea(cd) {
        val dut = I2cDeviceCtrl(I2cDeviceCtrl.Parameter.default())
      }
      area.dut
    }
    compiled.doSim("wrongDeviceAddrNack") { dut =>
      dut.clockDomain.forkStimulus(20 * 1000)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(20 * 1000)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(10 * 1000)
        }
      }
      val baudrate = 2500
      val tickPeriod = baudrate / 4

      dut.io.config.clockDivider #= 100
      dut.io.config.timeout #= tickPeriod
      dut.io.config.deviceAddr #= BigInt("0110001", 2)
      dut.io.i2c.sda.read #= true
      dut.io.i2c.scl.read #= true
      dut.io.i2c.sda.write #= true
      dut.io.i2c.scl.write #= true

      dut.clockDomain.assertReset()
      sleep(100 * 1000)
      dut.clockDomain.deassertReset()

      val start = I2cControllerSim.start(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      start.join()

      val addr = I2cControllerSim.address(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                          BigInt("0000110", 2), false)
      addr.join()

      val addrNack = I2cControllerSim.checkNack(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      addrNack.join()

      sleep(1000 * 1000)
    }

    compiled.doSim("internalTimeout") { dut =>
      dut.clockDomain.forkStimulus(20 * 1000)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(20 * 1000)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(10 * 1000)
        }
      }
      val baudrate = 2500
      val tickPeriod = baudrate / 4

      dut.io.config.clockDivider #= 100
      dut.io.config.timeout #= 250
      dut.io.config.deviceAddr #= BigInt("0110001", 2)
      dut.io.i2c.sda.read #= true
      dut.io.i2c.scl.read #= true
      dut.io.i2c.sda.write #= true
      dut.io.i2c.scl.write #= true

      dut.clockDomain.assertReset()
      sleep(100 * 1000)
      dut.clockDomain.deassertReset()

      val start = I2cControllerSim.start(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      start.join()

      val addr = I2cControllerSim.address(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                          BigInt("0000110", 2), false)
      addr.join()

      val addrNack = I2cControllerSim.checkNack(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      addrNack.join()

      sleep(1000 * 1000)
    }

    compiled.doSim("readReg1") { dut =>
      dut.clockDomain.forkStimulus(20 * 1000)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(20 * 1000)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(10 * 1000)
        }
      }
      val baudrate = 2500
      val tickPeriod = baudrate / 4

      dut.io.config.clockDivider #= 1
      dut.io.config.clockDividerReload #= false
      dut.io.config.timeout #= tickPeriod
      dut.io.config.deviceAddr #= BigInt("0110000", 2)
      dut.io.i2c.sda.read #= true
      dut.io.i2c.scl.read #= true
      dut.io.i2c.sda.write #= true
      dut.io.i2c.scl.write #= true
      dut.io.cmd.ready #= true

      dut.io.rsp.valid #= true
      dut.io.rsp.payload.data #= BigInt("01101010", 2)
      dut.io.rsp.payload.error #= false

      dut.clockDomain.assertReset()
      sleep(100 * 1000)
      dut.clockDomain.deassertReset()

      val start = I2cControllerSim.start(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      start.join()

      val addr = I2cControllerSim.writeByte(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                            BigInt("00000110", 2))
      addr.join()

      val addrAck = I2cControllerSim.checkAck(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      addrAck.join()
      assert(dut.io.cmd.payload.read.toBoolean == false, "Payload should be WRITE, but is READ")

      val reg = I2cControllerSim.writeByte(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                           BigInt("10000000", 2))
      reg.join()

      val regAck = I2cControllerSim.checkAck(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      sleep(100 * 1000)
      assert(dut.io.cmd.payload.data.toBigInt == BigInt("00000001", 2),
        s"Expected ${BigInt("00000001", 2)} but received ${dut.io.cmd.payload.data.toBigInt}")
      assert(dut.io.cmd.payload.reg.toBoolean ==true, "Payload should be register, but is data")
      regAck.join()

      val start2 = I2cControllerSim.start(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      start2.join()

      val addr2 = I2cControllerSim.writeByte(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                            BigInt("10000110", 2))
      addr2.join()

      val addr2Ack = I2cControllerSim.checkAck(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      addr2Ack.join()
      assert(dut.io.cmd.payload.read.toBoolean == true, "Payload should be READ, but is WRITE")

      val read = I2cControllerSim.readByte(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                           BigInt("10101001", 2))
      read.join()

      val readAck = I2cControllerSim.sendAck(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      readAck.join()

      val stop2 = I2cControllerSim.stop(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      stop2.join()

      sleep(10000 * 1000)
    }

    compiled.doSim("writeReg1") { dut =>
      dut.clockDomain.forkStimulus(20 * 1000)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(20 * 1000)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(10 * 1000)
        }
      }
      val baudrate = 2500
      val tickPeriod = baudrate / 4

      dut.io.config.clockDivider #= 10
      dut.io.config.clockDividerReload #= false
      dut.io.config.timeout #= tickPeriod
      dut.io.config.deviceAddr #= BigInt("0110000", 2)
      dut.io.i2c.sda.read #= true
      dut.io.i2c.scl.read #= true
      dut.io.i2c.sda.write #= true
      dut.io.i2c.scl.write #= true
      dut.io.cmd.ready #= true

      dut.io.rsp.valid #= true
      dut.io.rsp.payload.data #= BigInt("01101010", 2)
      dut.io.rsp.payload.error #= false

      dut.clockDomain.assertReset()
      sleep(100 * 1000)
      dut.clockDomain.deassertReset()

      val start = I2cControllerSim.start(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      start.join()

      val addr = I2cControllerSim.writeByte(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                            BigInt("00000110", 2))
      addr.join()

      val addrAck = I2cControllerSim.checkAck(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      addrAck.join()

      val reg = I2cControllerSim.writeByte(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                           BigInt("10000000", 2))
      reg.join()

      val regAck = I2cControllerSim.checkAck(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      sleep(100 * 1000)
      assert(dut.io.cmd.payload.data.toBigInt == BigInt("00000001", 2),
        s"Expected ${BigInt("00000001", 2)} but received ${dut.io.cmd.payload.data.toBigInt}")
      assert(dut.io.cmd.payload.read.toBoolean == false, "Payload should be WRITE, but is READ")
      assert(dut.io.cmd.payload.reg.toBoolean == true, "Payload should be register, but is data")
      regAck.join()

      val write = I2cControllerSim.writeByte(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod,
                                           BigInt("01010110", 2))
      write.join()

      val writeAck = I2cControllerSim.checkAck(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      writeAck.join()
      assert(dut.io.cmd.payload.data.toBigInt == BigInt("01101010", 2),
        s"Expected ${BigInt("01101010", 2)} but received ${dut.io.cmd.payload.data.toBigInt}")
      assert(dut.io.cmd.payload.read.toBoolean == false, "Payload should be WRITE, but is READ")
      assert(dut.io.cmd.payload.reg.toBoolean == false, "Payload should be data, but is register")

      val stop = I2cControllerSim.stop(dut.io.i2c.sda, dut.io.i2c.scl, tickPeriod)
      stop.join()

      sleep(10000 * 1000)
    }
  }
}
