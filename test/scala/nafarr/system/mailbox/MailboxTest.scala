// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.mailbox

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
import nafarr.system.dma.DmaHandshakeSim

class MailboxTest extends AnyFunSuite {
  test("Apb3Parameter") {
    generationShouldPass(Apb3Mailbox(MailboxCtrl.Parameter.small()))
    generationShouldPass(Apb3Mailbox(MailboxCtrl.Parameter.medium()))
    generationShouldPass(Apb3Mailbox(MailboxCtrl.Parameter.large()))
    generationShouldPass(Apb3Mailbox(MailboxCtrl.Parameter()))
    generationShouldPass(Apb3Mailbox(MailboxCtrl.Parameter(1)))
    generationShouldPass(Apb3Mailbox(MailboxCtrl.Parameter(1, 2)))
    generationShouldPass(Apb3Mailbox(MailboxCtrl.Parameter(255)))
    generationShouldFail(Apb3Mailbox(MailboxCtrl.Parameter(0)))
    generationShouldFail(Apb3Mailbox(MailboxCtrl.Parameter(256)))
    generationShouldFail(Apb3Mailbox(MailboxCtrl.Parameter(1, 1)))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkMailbox(MailboxCtrl.Parameter.small()))
    generationShouldPass(TileLinkMailbox(MailboxCtrl.Parameter.medium()))
    generationShouldPass(TileLinkMailbox(MailboxCtrl.Parameter.large()))
    generationShouldPass(TileLinkMailbox(MailboxCtrl.Parameter()))
    generationShouldPass(TileLinkMailbox(MailboxCtrl.Parameter(1)))
    generationShouldPass(TileLinkMailbox(MailboxCtrl.Parameter(1, 2)))
    generationShouldPass(TileLinkMailbox(MailboxCtrl.Parameter(255)))
    generationShouldFail(TileLinkMailbox(MailboxCtrl.Parameter(0)))
    generationShouldFail(TileLinkMailbox(MailboxCtrl.Parameter(256)))
    generationShouldFail(TileLinkMailbox(MailboxCtrl.Parameter(1, 1)))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneMailbox(MailboxCtrl.Parameter.small()))
    generationShouldPass(WishboneMailbox(MailboxCtrl.Parameter.medium()))
    generationShouldPass(WishboneMailbox(MailboxCtrl.Parameter.large()))
    generationShouldPass(WishboneMailbox(MailboxCtrl.Parameter()))
    generationShouldPass(WishboneMailbox(MailboxCtrl.Parameter(1)))
    generationShouldPass(WishboneMailbox(MailboxCtrl.Parameter(1, 2)))
    generationShouldPass(WishboneMailbox(MailboxCtrl.Parameter(255)))
    generationShouldFail(WishboneMailbox(MailboxCtrl.Parameter(0)))
    generationShouldFail(WishboneMailbox(MailboxCtrl.Parameter(256)))
    generationShouldFail(WishboneMailbox(MailboxCtrl.Parameter(1, 1)))
  }

  def init(dut: Apb3Mailbox): (Apb3Driver, MailboxCtrl.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = MailboxCtrl.Regs(dut.mapper.idCtrl.length)
    dut.clockDomain.forkStimulus(10)
    (driver, regs)
  }

  test("IpIdentification") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.medium())).doSim { dut =>
      val (driver, _) = init(dut)
      IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Mailbox)
      IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)
    }
  }

  test("Info register") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.medium())).doSim { dut =>
      val (driver, regs) = init(dut)
      SimTest.readField(driver, regs.info, 15, 8, 8, "Mailbox depth")
      SimTest.readField(driver, regs.info, 7, 0, 2, "Channel count")
    }
  }

  test("Status - initially empty") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.medium())).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)
      SimTest.readField(driver, regs.status, 1, 0, 0x3, "Both channels empty")
      SimTest.readField(driver, regs.status, 3, 2, 0x0, "Neither channel full")
    }
  }

  test("Channel 0 - push and pop") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.medium())).doSim { dut =>
      DmaHandshakeSim.release(dut.io.dmaRequest: _*)
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      driver.write(regs.write(0), 0xdeadbeefL)
      dut.clockDomain.waitSampling(2)

      SimTest.readField(driver, regs.status, 0, 0, 0, "Channel 0 not empty after push")
      SimTest.readField(driver, regs.occupancy(0), 31, 0, 1, "Channel 0 occupancy is 1")
      assert(dut.io.dmaRequest(0).rx.req.toBoolean, "DMA rx request low with data in channel 0")
      assert(dut.io.dmaRequest(0).tx.req.toBoolean, "DMA tx request low with space in channel 0")
      assert(!dut.io.dmaRequest(1).rx.req.toBoolean, "DMA rx request high on empty channel 1")
      DmaHandshakeSim.checkAck(dut.io.dmaRequest(0).rx, dut.clockDomain, "Mailbox rx")
      SimTest.read(driver, regs.read(0), 0xdeadbeefL, "Channel 0 pop value")
      dut.clockDomain.waitSampling(2)

      SimTest.readField(driver, regs.status, 0, 0, 1, "Channel 0 empty after pop")
      assert(!dut.io.dmaRequest(0).rx.req.toBoolean, "DMA rx request high after pop")
    }
  }

  test("Channel 1 - push and pop") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.medium())).doSim { dut =>
      DmaHandshakeSim.release(dut.io.dmaRequest: _*)
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      driver.write(regs.write(1), 0xcafebabeL)
      dut.clockDomain.waitSampling(2)

      SimTest.readField(driver, regs.status, 1, 1, 0, "Channel 1 not empty after push")
      SimTest.read(driver, regs.read(1), 0xcafebabeL, "Channel 1 pop value")
    }
  }

  test("Channel 0 - fill to full") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.small())).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      for (i <- 0 until dut.p.depth) {
        driver.write(regs.write(0), i)
        dut.clockDomain.waitSampling(1)
      }
      dut.clockDomain.waitSampling(2)

      SimTest.readField(driver, regs.status, 2, 2, 1, "Channel 0 full")
      assert(!dut.io.dmaRequest(0).tx.req.toBoolean, "DMA tx request high on full channel 0")

      for (i <- 0 until dut.p.depth) {
        SimTest.read(driver, regs.read(0), i, s"Channel 0 drain value $i")
        dut.clockDomain.waitSampling(1)
      }
    }
  }

  test("Interrupt - not-empty fires after push") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.medium())).doSim { dut =>
      val (driver, regs) = init(dut)
      dut.clockDomain.waitSampling(2)

      driver.write(regs.interruptMask(0), 0x1)
      dut.clockDomain.waitSampling(1)

      SimTest
        .readField(driver, regs.interruptPending(0), 0, 0, 0, "No pending interrupts initially")

      driver.write(regs.write(0), 0x1234)
      dut.clockDomain.waitSampling(2)

      SimTest
        .readField(driver, regs.interruptPending(0), 0, 0, 1, "Not-empty IRQ pending after push")

      driver.write(regs.interruptPending(0), 0x1)
      dut.clockDomain.waitSampling(1)
    }
  }

  test("DMA request") {
    SimConfig.withWave.compile(Apb3Mailbox(MailboxCtrl.Parameter.small())).doSim { dut =>
      DmaHandshakeSim.release(dut.io.dmaRequest: _*)
      val (driver, regs) = init(dut)
      val cd = dut.clockDomain
      val ch0 = dut.io.dmaRequest(0)
      val ch1 = dut.io.dmaRequest(1)
      cd.waitSampling(2)

      /* Idle: empty channels accept a message and hold none */
      for ((ch, name) <- Seq((ch0, "channel 0"), (ch1, "channel 1"))) {
        assert(ch.tx.req.toBoolean, s"DMA tx request low on empty $name")
        assert(!ch.rx.req.toBoolean, s"DMA rx request high on empty $name")
      }
      DmaHandshakeSim.checkAck(ch0.tx, cd, "Mailbox channel 0 tx")

      /* Fill channel 0: tx stops requesting, rx requests; channel 1 is unaffected */
      for (i <- 0 until dut.p.depth) {
        driver.write(regs.write(0), i)
      }
      cd.waitSampling(2)
      assert(!ch0.tx.req.toBoolean, "DMA tx request high on full channel 0")
      assert(ch0.rx.req.toBoolean, "DMA rx request low on full channel 0")
      assert(ch1.tx.req.toBoolean, "DMA tx request low on empty channel 1")
      assert(!ch1.rx.req.toBoolean, "DMA rx request high on empty channel 1")
      DmaHandshakeSim.checkAck(ch0.rx, cd, "Mailbox channel 0 rx")

      /* Drain channel 0: tx requests again, rx stops requesting */
      for (i <- 0 until dut.p.depth) {
        SimTest.read(driver, regs.read(0), i, s"Channel 0 drain value $i")
      }
      cd.waitSampling(2)
      assert(ch0.tx.req.toBoolean, "DMA tx request low on drained channel 0")
      assert(!ch0.rx.req.toBoolean, "DMA rx request high on drained channel 0")
    }
  }
}
