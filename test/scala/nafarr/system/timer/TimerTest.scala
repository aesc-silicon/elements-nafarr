// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.timer

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

class TimerTest extends AnyFunSuite {

  test("Apb3Parameter") {
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter.small()))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter.medium()))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter.large()))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter.default()))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter(count = 4, channelCount = 2)))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter(count = 1, channelCount = 8)))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter(count = 16, channelCount = 1)))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter(width = 8)))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter(width = 16)))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter(prescalerWidth = 0)))
    generationShouldPass(Apb3Timer(TimerCtrl.Parameter(prescalerWidth = 32)))
    generationShouldFail(Apb3Timer(TimerCtrl.Parameter(count = 0)))
    generationShouldFail(Apb3Timer(TimerCtrl.Parameter(count = 17)))
    generationShouldFail(Apb3Timer(TimerCtrl.Parameter(channelCount = 0)))
    generationShouldFail(Apb3Timer(TimerCtrl.Parameter(channelCount = 9)))
    generationShouldFail(Apb3Timer(TimerCtrl.Parameter(width = 0)))
    generationShouldFail(Apb3Timer(TimerCtrl.Parameter(width = 33)))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkTimer(TimerCtrl.Parameter.default()))
    generationShouldPass(TileLinkTimer(TimerCtrl.Parameter(count = 2, channelCount = 2)))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneTimer(TimerCtrl.Parameter.default()))
    generationShouldPass(WishboneTimer(TimerCtrl.Parameter(count = 2, channelCount = 2)))
  }

  // 1 timer, 2 compare channels, 8-bit counter, 4-bit prescaler
  def simParam = TimerCtrl.Parameter(count = 1, channelCount = 2, width = 8, prescalerWidth = 4)

  def init(dut: Apb3Timer): (Apb3Driver, TimerCtrl.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs   = TimerCtrl.Regs(dut.mapper.idCtrl.length, simParam)
    dut.clockDomain.forkStimulus(10)
    (driver, regs)
  }

  test("IpIdentification") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("IpIdentification") { dut =>
        val (driver, _) = init(dut)
        IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Timer)
        IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)
      }
  }

  test("Info register") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("InfoRegister") { dut =>
        val (driver, regs) = init(dut)
        SimTest.readField(driver, regs.info,  7,  0, 1, "count=1")
        SimTest.readField(driver, regs.info, 15,  8, 2, "channelCount=2")
        SimTest.readField(driver, regs.info, 23, 16, 8, "width=8")
        SimTest.readField(driver, regs.info, 31, 24, 4, "prescalerWidth=4")
      }
  }

  test("Free-run: counter increments") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("FreeRun") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.prescaler(0), 0)     // tick every cycle
        driver.write(regs.control(0), 0x1)     // enable=1, mode=00 (free-run)
        dut.clockDomain.waitSampling(6)

        val cnt = driver.read(regs.counter(0))
        assert(cnt > 0, s"counter should have incremented, got $cnt")
        assert(cnt <= 10, s"counter ran too far, got $cnt")
      }
  }

  test("Free-run: counter preload") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("FreeRunPreload") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.counter(0), 0xE0)    // preload near overflow (width=8, max=0xFF)
        driver.write(regs.prescaler(0), 0)
        driver.write(regs.control(0), 0x1)     // free-run
        dut.clockDomain.waitSampling(4)

        val cnt = driver.read(regs.counter(0))
        // After 0xE0 + ~4 ticks it should have wrapped (0xFF → 0) and continued
        assert(cnt < 0xE0, s"counter should have wrapped, got 0x${f"$cnt%02x"}")
      }
  }

  test("Periodic: overflow pending fires and counter reloads") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("Periodic") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.prescaler(0), 0)     // tick every cycle
        driver.write(regs.reload(0), 3)        // period = 4 ticks (0→1→2→3→overflow→0)
        driver.write(regs.control(0), 0x3)     // enable=1, mode=01 (periodic)
        dut.clockDomain.waitSampling(10)

        SimTest.readField(driver, regs.irqPending, 0, 0, 1, "timer0 overflow pending")
        val cnt = driver.read(regs.counter(0))
        assert(cnt < 4, s"counter should have wrapped back, got $cnt")
      }
  }

  test("One-shot: overflow pending fires and enable clears") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("OneShot") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.prescaler(0), 0)
        driver.write(regs.reload(0), 3)
        driver.write(regs.control(0), 0x5)     // enable=1, mode=10 (one-shot)
        dut.clockDomain.waitSampling(10)

        SimTest.readField(driver, regs.irqPending, 0, 0, 1, "timer0 overflow pending")
        SimTest.readField(driver, regs.control(0), 0, 0, 0, "enable cleared after one-shot")
      }
  }

  test("One-shot: counter stops after completion") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("OneShotStop") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.prescaler(0), 0)
        driver.write(regs.reload(0), 2)
        driver.write(regs.control(0), 0x5)     // one-shot
        dut.clockDomain.waitSampling(10)

        val cnt1 = driver.read(regs.counter(0))
        dut.clockDomain.waitSampling(6)
        val cnt2 = driver.read(regs.counter(0))
        assert(cnt1 == cnt2, s"counter should not advance after one-shot: $cnt1 vs $cnt2")
      }
  }

  test("Compare match: pending fires on match") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("CompareMatch") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.compare(0, 0), 3)    // compare0 fires at counter=3
        driver.write(regs.reload(0), 7)
        driver.write(regs.prescaler(0), 0)
        driver.write(regs.control(0), 0x3)     // periodic
        dut.clockDomain.waitSampling(10)

        SimTest.readField(driver, regs.irqPending, 1, 1, 1, "timer0 compare0 pending")
      }
  }

  test("Compare match channel 1") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("CompareMatchCh1") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        driver.write(regs.compare(0, 1), 2)    // compare1 fires at counter=2
        driver.write(regs.reload(0), 7)
        driver.write(regs.prescaler(0), 0)
        driver.write(regs.control(0), 0x3)
        dut.clockDomain.waitSampling(10)

        SimTest.readField(driver, regs.irqPending, 2, 2, 1, "timer0 compare1 pending")
      }
  }

  test("Prescaler: controls tick rate") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("Prescaler") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        // prescaler=7 → tick every 8 cycles; after 24 cycles expect ~3 ticks
        driver.write(regs.prescaler(0), 7)
        driver.write(regs.control(0), 0x1)     // free-run
        dut.clockDomain.waitSampling(30)

        val cnt = driver.read(regs.counter(0))
        assert(cnt >= 1 && cnt <= 5, s"prescaled counter out of range, got $cnt")
      }
  }

  test("Interrupt output: fires when mask enabled") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("InterruptOutput") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        val irqMask = regs.irqPending + 4
        driver.write(irqMask, 0x1)             // enable bit 0 = timer0 overflow
        driver.write(regs.prescaler(0), 0)
        driver.write(regs.reload(0), 3)
        driver.write(regs.control(0), 0x3)     // periodic
        dut.clockDomain.waitSampling(10)

        assert(dut.io.interrupt.toBoolean, "interrupt should be high after overflow")
      }
  }

  test("Interrupt output: masked source does not fire") {
    SimConfig.withWave.compile(Apb3Timer(simParam))
      .doSim("InterruptMasked") { dut =>
        val (driver, regs) = init(dut)
        dut.clockDomain.waitSampling(2)

        // mask = 0x2 (compare0 only), overflow bit 0 not enabled
        val irqMask = regs.irqPending + 4
        driver.write(irqMask, 0x2)
        driver.write(regs.prescaler(0), 0)
        driver.write(regs.reload(0), 3)
        driver.write(regs.control(0), 0x3)     // overflow will fire, but is not masked-in
        dut.clockDomain.waitSampling(10)

        SimTest.readField(driver, regs.irqPending, 0, 0, 1, "overflow pending (raw)")
        assert(!dut.io.interrupt.toBoolean, "interrupt should be low — overflow not in mask")
      }
  }

  test("Multi-timer: independent operation") {
    val p = TimerCtrl.Parameter(count = 2, channelCount = 1, width = 8, prescalerWidth = 4)
    SimConfig.withWave.compile(Apb3Timer(p))
      .doSim("MultiTimer") { dut =>
        val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
        val regs   = TimerCtrl.Regs(dut.mapper.idCtrl.length, p)
        dut.clockDomain.forkStimulus(10)
        dut.clockDomain.waitSampling(2)

        // timer0: periodic, reload=3
        driver.write(regs.prescaler(0), 0)
        driver.write(regs.reload(0), 3)
        driver.write(regs.control(0), 0x3)

        // timer1: one-shot, reload=5
        driver.write(regs.prescaler(1), 0)
        driver.write(regs.reload(1), 5)
        driver.write(regs.control(1), 0x5)

        dut.clockDomain.waitSampling(12)

        // irq source 0 = timer0 overflow, irq source 1 = timer0 compare0
        // irq source 2 = timer1 overflow, irq source 3 = timer1 compare0
        SimTest.readField(driver, regs.irqPending, 0, 0, 1, "timer0 overflow pending")
        SimTest.readField(driver, regs.irqPending, 2, 2, 1, "timer1 overflow pending")
        SimTest.readField(driver, regs.control(1), 0, 0, 0, "timer1 enable cleared after one-shot")
      }
  }
}
