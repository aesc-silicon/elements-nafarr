// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.watchdog

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

class WatchdogTest extends AnyFunSuite {
  test("Apb3Parameter") {
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter.default()))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter.small()))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter.windowed()))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter(count = 4)))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter(width = 1)))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter(width = 32)))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter(prescalerWidth = 1)))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter(prescalerWidth = 32)))
    generationShouldPass(Apb3Watchdog(WatchdogCtrl.Parameter(locked = false)))
    generationShouldFail(Apb3Watchdog(WatchdogCtrl.Parameter(count = 0)))
    generationShouldFail(Apb3Watchdog(WatchdogCtrl.Parameter(width = 0)))
    generationShouldFail(Apb3Watchdog(WatchdogCtrl.Parameter(width = 33)))
    generationShouldFail(Apb3Watchdog(WatchdogCtrl.Parameter(prescalerWidth = 0)))
    generationShouldFail(Apb3Watchdog(WatchdogCtrl.Parameter(prescalerWidth = 33)))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkWatchdog(WatchdogCtrl.Parameter.default()))
    generationShouldPass(TileLinkWatchdog(WatchdogCtrl.Parameter.small()))
    generationShouldPass(TileLinkWatchdog(WatchdogCtrl.Parameter.windowed()))
    generationShouldFail(TileLinkWatchdog(WatchdogCtrl.Parameter(count = 0)))
    generationShouldFail(TileLinkWatchdog(WatchdogCtrl.Parameter(width = 33)))
    generationShouldFail(TileLinkWatchdog(WatchdogCtrl.Parameter(prescalerWidth = 0)))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneWatchdog(WatchdogCtrl.Parameter.default()))
    generationShouldPass(WishboneWatchdog(WatchdogCtrl.Parameter.small()))
    generationShouldPass(WishboneWatchdog(WatchdogCtrl.Parameter.windowed()))
    generationShouldFail(WishboneWatchdog(WatchdogCtrl.Parameter(count = 0)))
    generationShouldFail(WishboneWatchdog(WatchdogCtrl.Parameter(width = 33)))
    generationShouldFail(WishboneWatchdog(WatchdogCtrl.Parameter(prescalerWidth = 0)))
  }

  // Small counter widths to keep simulation fast.
  def simParam = WatchdogCtrl.Parameter(width = 4, prescalerWidth = 4, locked = false)
  def windowedParam = WatchdogCtrl.Parameter(width = 4, prescalerWidth = 4, windowed = true, locked = false)

  def init(dut: Apb3Watchdog): (Apb3Driver, WatchdogCtrl.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = WatchdogCtrl.Regs(dut.mapper.idCtrl.length)
    dut.clockDomain.forkStimulus(10)
    (driver, regs)
  }

  test("IpIdentification") {
    SimConfig.withWave.compile(Apb3Watchdog(WatchdogCtrl.Parameter.default())).doSim { dut =>
      val (driver, _) = init(dut)
      IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Watchdog)
      IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)
    }
  }

  test("Info register") {
    val p = WatchdogCtrl.Parameter(count = 2, width = 8, prescalerWidth = 10, windowed = true, locked = true)
    SimConfig.withWave.compile(Apb3Watchdog(p)).doSim { dut =>
      val (driver, regs) = init(dut)
      SimTest.readField(driver, regs.info, 7, 0, 2, "Count")
      SimTest.readField(driver, regs.info, 15, 8, 8, "Width")
      SimTest.readField(driver, regs.info, 23, 16, 10, "PrescalerWidth")
      SimTest.readField(driver, regs.info, 24, 24, 1, "Windowed")
      SimTest.readField(driver, regs.info, 25, 25, 1, "Locked")
    }
  }

  test("Timeout fires interrupt") {
    SimConfig.withWave.compile(Apb3Watchdog(simParam)).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      // prescalerVal=1 → tick every 2 cycles; timeoutVal=3 → fires after 4 ticks (8 cycles)
      driver.write(regs.prescaler(0), 1)
      driver.write(regs.timeout(0), 3)
      driver.write(regs.irqMask(0), 0x1)
      driver.write(regs.control(0), 0x1)
      dut.clockDomain.waitSampling(20)

      SimTest.readField(driver, regs.irqPending(0), 0, 0, 1, "Timeout IRQ pending after expiry")
      assert(dut.io.interrupt.toBoolean, "Combined interrupt asserted")
    }
  }

  test("Kick prevents timeout") {
    SimConfig.withWave.compile(Apb3Watchdog(simParam)).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      // timeout = 7 → 8 ticks at prescalerVal=1 → 16 cycles
      driver.write(regs.prescaler(0), 1)
      driver.write(regs.timeout(0), 7)
      driver.write(regs.irqMask(0), 0x1)
      driver.write(regs.control(0), 0x1)

      for (_ <- 0 until 5) {
        dut.clockDomain.waitSampling(10)
        driver.write(regs.kick(0), 1)
      }

      SimTest.readField(driver, regs.irqPending(0), 0, 0, 0, "No timeout after repeated kicks")
    }
  }

  test("Lock prevents config change") {
    SimConfig.withWave.compile(Apb3Watchdog(WatchdogCtrl.Parameter(width = 4, prescalerWidth = 4))).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      driver.write(regs.timeout(0), 0xa)
      driver.write(regs.control(0), 0x3) // enable + lock
      dut.clockDomain.waitSampling(1)

      driver.write(regs.timeout(0), 0x5) // attempt change — must be ignored
      dut.clockDomain.waitSampling(1)

      SimTest.readField(driver, regs.timeout(0), 3, 0, 0xa, "Timeout unchanged after lock")
      SimTest.readField(driver, regs.status(0), 1, 1, 1, "Lock bit visible in status")
    }
  }

  test("Lock prevents disable") {
    SimConfig.withWave.compile(Apb3Watchdog(WatchdogCtrl.Parameter(width = 4, prescalerWidth = 4))).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      driver.write(regs.control(0), 0x3) // enable + lock
      dut.clockDomain.waitSampling(1)

      driver.write(regs.control(0), 0x0) // attempt disable — must be ignored
      dut.clockDomain.waitSampling(1)

      SimTest.readField(driver, regs.status(0), 0, 0, 1, "Watchdog still enabled after lock")
    }
  }

  test("Windowed - valid kick resets counter") {
    SimConfig.withWave.compile(Apb3Watchdog(windowedParam)).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      // timeout=7, windowOpen=2: window opens when counter<=2, i.e. after 5 ticks (10 cycles)
      driver.write(regs.prescaler(0), 1)
      driver.write(regs.timeout(0), 7)
      driver.write(regs.windowOpen(0), 2)
      driver.write(regs.irqMask(0), 0x5) // timeout-irq and violation-irq
      driver.write(regs.control(0), 0x1)

      dut.clockDomain.waitSampling(12) // counter should be 1 (in window)

      SimTest.readField(driver, regs.status(0), 2, 2, 1, "inWindow asserted")
      driver.write(regs.kick(0), 1)
      dut.clockDomain.waitSampling(2)

      SimTest.readField(driver, regs.irqPending(0), 2, 2, 0, "No violation after valid kick")
    }
  }

  test("Windowed - early kick raises violation") {
    SimConfig.withWave.compile(Apb3Watchdog(windowedParam)).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      driver.write(regs.prescaler(0), 1)
      driver.write(regs.timeout(0), 7)
      driver.write(regs.windowOpen(0), 1)
      driver.write(regs.irqMask(0), 0x4) // violation-irq
      driver.write(regs.control(0), 0x1)

      dut.clockDomain.waitSampling(4) // counter ~5 or 6, outside window

      driver.write(regs.kick(0), 1) // early kick — violation
      dut.clockDomain.waitSampling(2)

      SimTest.readField(driver, regs.irqPending(0), 2, 2, 1, "Violation IRQ pending after early kick")
      assert(dut.io.interrupt.toBoolean, "Combined interrupt asserted on violation")
    }
  }
}
