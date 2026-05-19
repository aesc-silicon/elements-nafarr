// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.prng

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

class PrngTest extends AnyFunSuite {

  def init(dut: Apb3Prng): (Apb3Driver, PrngCtrl.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = PrngCtrl.Regs(0x8)
    dut.clockDomain.forkStimulus(10)
    return (driver, regs)
  }


  test("Apb3Parameter") {
    generationShouldPass(Apb3Prng(PrngCtrl.Parameter.default()))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkPrng(PrngCtrl.Parameter.default()))
  }

  test("WishboneParameter") {
    generationShouldPass(WishbonePrng(PrngCtrl.Parameter.default()))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Prng(PrngCtrl.Parameter())
      dut
    }

    compiled.doSim("registerMap") { dut =>
      val (driver, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Prng)
      IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)
    }

    compiled.doSim("Output advances each cycle when enabled") { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(4)

      val v0 = driver.read(regs.output)
      dut.clockDomain.waitSampling(1)
      val v1 = driver.read(regs.output)
      dut.clockDomain.waitSampling(1)
      val v2 = driver.read(regs.output)

      assert(v0 != v1, "PRNG output should change each cycle")
      assert(v1 != v2, "PRNG output should change each cycle")
      assert(v0 != v2, "PRNG output should not repeat after two cycles")
    }

    compiled.doSim("Output frozen when disabled") { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(4)

      driver.write(regs.control, 0)
      dut.clockDomain.waitSampling(2)

      val v0 = driver.read(regs.output)
      dut.clockDomain.waitSampling(4)
      val v1 = driver.read(regs.output)

      assert(v0 == v1, "PRNG output should be frozen when disabled")
    }

    compiled.doSim("Reseed changes output sequence") { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(4)

      driver.write(regs.control, 0)
      dut.clockDomain.waitSampling(2)

      driver.write(regs.seed, 0xdeadbeefL)
      dut.clockDomain.waitSampling(2)
      assert(driver.read(regs.output) == 0xdeadbeefL, "Output should reflect new seed")

      driver.write(regs.seed, 0x12345678L)
      dut.clockDomain.waitSampling(2)
      assert(driver.read(regs.output) == 0x12345678L, "Output should reflect second seed")
    }

    compiled.doSim("Zero seed write sets error pending, state unchanged") { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(4)

      // Disable and set a known seed so output is predictable
      driver.write(regs.control, 0)
      dut.clockDomain.waitSampling(1)
      driver.write(regs.seed, 0xabcd1234L)
      dut.clockDomain.waitSampling(2)
      val beforeZeroWrite = driver.read(regs.output)

      // Enable the error source in the mask
      driver.write(regs.errorMask, 1)
      dut.clockDomain.waitSampling(1)

      assert(driver.read(regs.errorPending) == 0, "Error pending should be clear initially")

      // Attempt zero seed
      driver.write(regs.seed, 0)
      dut.clockDomain.waitSampling(2)

      assert((driver.read(regs.errorPending) & 0x1) == 1, "Error pending should be set after zero seed write")
      assert(driver.read(regs.output) == beforeZeroWrite, "PRNG state should be unchanged")
      assert(dut.io.error.toBoolean, "Error output should be asserted")

      // Clear by writing 1 to pending bit
      driver.write(regs.errorPending, 1)
      dut.clockDomain.waitSampling(1)
      assert(driver.read(regs.errorPending) == 0, "Error pending should clear after write")
      assert(!dut.io.error.toBoolean, "Error output should deassert after clear")
    }
  }

}
