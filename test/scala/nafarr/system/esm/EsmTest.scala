// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.esm

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

class EsmTest extends AnyFunSuite {
  test("Apb3Parameter") {
    generationShouldPass(Apb3Esm(EsmCtrl.Parameter.default(1)))
    generationShouldPass(Apb3Esm(EsmCtrl.Parameter.small(1)))
    generationShouldPass(Apb3Esm(EsmCtrl.Parameter.large(1)))
    generationShouldPass(Apb3Esm(EsmCtrl.Parameter(inputCount = 256)))
    generationShouldPass(Apb3Esm(EsmCtrl.Parameter(inputCount = 1, counterWidth = 1)))
    generationShouldPass(Apb3Esm(EsmCtrl.Parameter(inputCount = 1, counterWidth = 32)))
    generationShouldPass(Apb3Esm(EsmCtrl.Parameter(inputCount = 1, locked = false)))
    generationShouldFail(Apb3Esm(EsmCtrl.Parameter(inputCount = 0)))
    generationShouldFail(Apb3Esm(EsmCtrl.Parameter(inputCount = 257)))
    generationShouldFail(Apb3Esm(EsmCtrl.Parameter(inputCount = 1, counterWidth = 0)))
    generationShouldFail(Apb3Esm(EsmCtrl.Parameter(inputCount = 1, counterWidth = 33)))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkEsm(EsmCtrl.Parameter.default(1)))
    generationShouldPass(TileLinkEsm(EsmCtrl.Parameter.small(1)))
    generationShouldFail(TileLinkEsm(EsmCtrl.Parameter(inputCount = 0)))
    generationShouldFail(TileLinkEsm(EsmCtrl.Parameter(inputCount = 1, counterWidth = 0)))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneEsm(EsmCtrl.Parameter.default(1)))
    generationShouldPass(WishboneEsm(EsmCtrl.Parameter.small(1)))
    generationShouldFail(WishboneEsm(EsmCtrl.Parameter(inputCount = 0)))
    generationShouldFail(WishboneEsm(EsmCtrl.Parameter(inputCount = 1, counterWidth = 0)))
  }

  // 4 inputs = 1 bank (bankCount=1). locked=false keeps tests simple.
  def simParam = EsmCtrl.Parameter(inputCount = 4, counterWidth = 4, locked = false)

  def init(dut: Apb3Esm): (Apb3Driver, EsmCtrl.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = EsmCtrl.Regs(dut.mapper.idCtrl.length)
    dut.clockDomain.forkStimulus(10)
    dut.io.inputs #= 0
    (driver, regs)
  }

  test("IpIdentification") {
    SimConfig.withWave.compile(Apb3Esm(EsmCtrl.Parameter.default(1)))
      .doSim("IpIdentification") { dut =>
        val (driver, _) = init(dut)
        IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Esm)
        IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)
      }
  }

  test("Info register") {
    val p = EsmCtrl.Parameter(inputCount = 8, counterWidth = 10, locked = true)
    SimConfig.withWave.compile(Apb3Esm(p))
      .doSim("InfoRegister") { dut =>
        val (driver, regs) = init(dut)
        SimTest.readField(driver, regs.info, 8, 0, 8, "InputCount")
        SimTest.readField(driver, regs.info, 15, 9, 1, "BankCount")
        SimTest.readField(driver, regs.info, 23, 16, 10, "CounterWidth")
        SimTest.readField(driver, regs.info, 24, 24, 1, "Locked")
      }
  }

  test("INFO level - pending and interrupt") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("INFO_pending") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.control, 0x1)
        driver.write(regs.enable(0), 0x01)        // input 0 → INFO (bit 0)

        dut.io.inputs #= 1
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 0, 0, 1, "INFO pending after input")
        assert(dut.io.infoInterrupt.toBoolean, "infoInterrupt asserted")

        dut.io.inputs #= 0
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 0, 0, 1, "INFO pending after input")
        assert(dut.io.infoInterrupt.toBoolean, "infoInterrupt asserted")

        driver.write(regs.pending(0), 0x01)       // W1C INFO bit 0
        dut.clockDomain.waitSampling(2)

        SimTest.readField(driver, regs.pending(0), 0, 0, 0, "INFO pending cleared")
      }
  }

  test("WARN level - pending and interrupt") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("WARN_pending") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.control, 0x1)
        driver.write(regs.enable(0), 0x0200)      // input 1 → WARN (bit 9)

        dut.io.inputs #= 2
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 9, 9, 1, "WARN pending after input")
        assert(dut.io.warnInterrupt.toBoolean, "warnInterrupt asserted")

        dut.io.inputs #= 0
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 9, 9, 1, "WARN pending after input")
        assert(dut.io.warnInterrupt.toBoolean, "warnInterrupt asserted")

        driver.write(regs.pending(0), 0x200)      // W1C WARN bit 9
        dut.clockDomain.waitSampling(2)
        SimTest.readField(driver, regs.pending(0), 9, 9, 0, "WARN pending cleared")
      }
  }

  test("FATAL level - errorSignal immediate") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("FATAL_immediate") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.control, 0x1)
        driver.write(regs.enable(0), 0x01000000)  // input 0 → FATAL (bit 24)

        dut.io.inputs #= 1
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 24, 24, 1, "FATAL pending after input")
        assert(dut.io.errorSignal.toBoolean, "errorSignal asserted immediately on FATAL")
        SimTest.readField(driver, regs.status, 1, 1, 1, "errorSignal visible in status")
      }
  }

  test("ERROR level - errorSignal after counter") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("ERROR_counter") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        // counter=3: expires after 4 ticks
        driver.write(regs.errorCounter, 3)
        driver.write(regs.enable(0), 0x00010000)  // input 0 → ERROR (bit 16)
        driver.write(regs.control, 0x1)

        dut.io.inputs #= 1
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.status, 0, 0, 1, "Counter active after error input")
        assert(!dut.io.errorSignal.toBoolean, "errorSignal not yet asserted")

        dut.clockDomain.waitSampling(10)

        assert(dut.io.errorSignal.toBoolean, "errorSignal asserted after counter expiry")
        SimTest.readField(driver, regs.status, 1, 1, 1, "errorSignal in status")
      }
  }

  test("ERROR level - clear before counter expires prevents errorSignal") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("ERROR_early_clear") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.errorCounter, 15)
        driver.write(regs.enable(0), 0x00010000)  // input 0 → ERROR
        driver.write(regs.control, 0x1)

        dut.io.inputs #= 1
        dut.clockDomain.waitSampling(4)
        SimTest.readField(driver, regs.status, 0, 0, 1, "Counter active")

        dut.io.inputs #= 0
        driver.write(regs.pending(0), 0x00010000) // W1C ERROR bit 16
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.status, 0, 0, 0, "Counter reset after clear")
        assert(!dut.io.errorSignal.toBoolean, "No errorSignal after early clear")
      }
  }

  test("Multi-level routing - same input feeds INFO and WARN") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("multi_level") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.control, 0x1)
        driver.write(regs.enable(0), 0x0101)      // input 0 → INFO (bit 0) and WARN (bit 8)

        dut.io.inputs #= 1
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 0, 0, 1, "INFO pending")
        SimTest.readField(driver, regs.pending(0), 8, 8, 1, "WARN pending from same input")
        assert(dut.io.infoInterrupt.toBoolean, "infoInterrupt")
        assert(dut.io.warnInterrupt.toBoolean, "warnInterrupt")
      }
  }

  test("Inject - event without hardware input") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("inject") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.control, 0x5)           // enable + injectEnable
        driver.write(regs.enable(0), 0x02)        // input 1 → INFO (bit 1)
        driver.write(regs.inject(0), 0x02)        // inject input 1; no hardware input
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 1, 1, 1, "INFO pending from inject")
        assert(dut.io.infoInterrupt.toBoolean, "infoInterrupt via inject")

        driver.write(regs.control, 0x1)           // disable inject
        dut.clockDomain.waitSampling(2)
        SimTest.readField(driver, regs.pending(0), 1, 1, 1, "INFO pending latched after inject disable")
      }
  }

  test("Lock - freezes ERROR/FATAL enable and clears injectEnable") {
    val lockParam = EsmCtrl.Parameter(inputCount = 4, counterWidth = 4)
    SimConfig.withWave.compile(Apb3Esm(lockParam))
      .doSim("lock") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.enable(0), 0x000f0000)  // inputs 0-3 → ERROR (bits [19:16])
        driver.write(regs.control, 0x5)           // enable + injectEnable
        dut.clockDomain.waitSampling(1)
        SimTest.readField(driver, regs.control, 2, 2, 1, "injectEnable set before lock")

        driver.write(regs.control, 0x3)           // enable + lock
        dut.clockDomain.waitSampling(1)

        SimTest.readField(driver, regs.control, 1, 1, 1, "lock bit set")
        SimTest.readField(driver, regs.control, 2, 2, 0, "injectEnable cleared by lock")

        driver.write(regs.enable(0), 0x0)         // attempt to clear all — ERROR/FATAL must be ignored
        dut.clockDomain.waitSampling(1)
        SimTest.readField(driver, regs.enable(0), 19, 16, 0xf, "errorEnable unchanged after lock")
      }
  }

  test("Master enable gates pending capture") {
    SimConfig.withWave.compile(Apb3Esm(simParam))
      .doSim("master_enable") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.enable(0), 0x01)        // input 0 → INFO
        dut.io.inputs #= 1
        dut.clockDomain.waitSampling(4)

        SimTest.readField(driver, regs.pending(0), 0, 0, 0, "No INFO pending when ESM disabled")

        driver.write(regs.control, 0x1)
        dut.clockDomain.waitSampling(4)
        SimTest.readField(driver, regs.pending(0), 0, 0, 1, "INFO pending after ESM enabled")
      }
  }
}
