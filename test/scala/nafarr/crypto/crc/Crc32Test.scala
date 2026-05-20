// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.crc

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

class Crc32Test extends AnyFunSuite {

  def init(dut: Apb3Crc32): (Apb3Driver, Crc32Ctrl.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = Crc32Ctrl.Regs(0x8)
    dut.clockDomain.forkStimulus(10)
    return (driver, regs)
  }

  test("Apb3Parameter") {
    generationShouldPass(Apb3Crc32(Crc32Ctrl.Parameter.default()))
    generationShouldPass(Apb3Crc32(Crc32Ctrl.Parameter.withXorOut()))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkCrc32(Crc32Ctrl.Parameter.default()))
    generationShouldPass(TileLinkCrc32(Crc32Ctrl.Parameter.withXorOut()))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneCrc32(Crc32Ctrl.Parameter.default()))
    generationShouldPass(WishboneCrc32(Crc32Ctrl.Parameter.withXorOut()))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Crc32(Crc32Ctrl.Parameter.default())
      dut
    }

    compiled.doSim("registerMap") { dut =>
      val (driver, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Crc32)
      IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)

      /* Info register: CRC32 Standard, no xorOut
       *   bits[7:0]  = 32 (polynomial order)
       *   bit 8      = 1  (inputReflect)
       *   bit 9      = 1  (outputReflect)
       *   bit 10     = 0  (xorOut absent)
       *   => 0x320
       */
      SimTest.readField(driver, regs.info, 7, 0, 32,  "Polynomial order")
      SimTest.readField(driver, regs.info, 8, 8, 1,  "Input reflect disabled")
      SimTest.readField(driver, regs.info, 9, 9, 1,  "Output reflect disabled")
      SimTest.readField(driver, regs.info, 10, 10, 0,  "XOR out enabled")
    }

    compiled.doSim("initLoadsInitValue") { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      /* Trigger INIT — write any value to control */
      driver.write(regs.control, 1)
      dut.clockDomain.waitSampling(2)

      /* CRC32 Standard initValue = 0xFFFFFFFF; xorOut disabled
       * => result reads back raw crc_state = 0xFFFFFFFF           */
      val result = driver.read(regs.result)
      assert(result == 0xFFFFFFFFL, f"xorOut register mismatch: 0x${result}%08x")
    }
  }

  test("basicWithXorOut") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Crc32(Crc32Ctrl.Parameter.withXorOut())
      dut
    }

    compiled.doSim("registerMap") { dut =>
      val (driver, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Crc32)
      IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)

      /* Info register: CRC32 Standard, no xorOut
       *   bits[7:0]  = 32 (polynomial order)
       *   bit 8      = 1  (inputReflect)
       *   bit 9      = 1  (outputReflect)
       *   bit 10     = 1  (xorOut enabled)
       *   => 0x320
       */
      SimTest.readField(driver, regs.info, 7, 0, 32,  "Polynomial order")
      SimTest.readField(driver, regs.info, 8, 8, 1,  "Input reflect disabled")
      SimTest.readField(driver, regs.info, 9, 9, 1,  "Output reflect disabled")
      SimTest.readField(driver, regs.info, 10, 10, 1,  "XOR out disabled")
    }

    compiled.doSim("initWithXorOut") { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      /* Trigger INIT */
      driver.write(regs.control, 1)
      dut.clockDomain.waitSampling(2)

      /* xorOut register defaults to CRC32.Standard.finalXor = 0xFFFFFFFF
       * crc_state after init = 0xFFFFFFFF; result = state ^ xorOut = 0x00000000 */
      val result = driver.read(regs.result)
      assert(result == 0x0L, f"result after init with xorOut mismatch: 0x${result}%08x")

      /* xorOut register should read back 0xFFFFFFFF */
      val result2 = driver.read(regs.xorOut)
      assert(result2 == 0xFFFFFFFFL, f"xorOut register mismatch: 0x${result2}%08x")
    }
  }
}
