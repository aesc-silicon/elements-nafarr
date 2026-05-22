// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.semaphore

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

class SemaphoreTest extends AnyFunSuite {
  test("Apb3SemaphoreParameters") {
    generationShouldPass(Apb3Semaphore(SemaphoreCtrl.Parameter()))
    generationShouldPass(Apb3Semaphore(SemaphoreCtrl.Parameter.small()))
    generationShouldPass(Apb3Semaphore(SemaphoreCtrl.Parameter.medium()))
    generationShouldPass(Apb3Semaphore(SemaphoreCtrl.Parameter.large()))
    generationShouldPass(Apb3Semaphore(SemaphoreCtrl.Parameter(1)))
    generationShouldPass(Apb3Semaphore(SemaphoreCtrl.Parameter(31)))

    generationShouldFail(Apb3Semaphore(SemaphoreCtrl.Parameter(0)))
    generationShouldFail(Apb3Semaphore(SemaphoreCtrl.Parameter(33)))
  }

  test("TileLinkSemaphoreParameters") {
    generationShouldPass(TileLinkSemaphore(SemaphoreCtrl.Parameter()))
    generationShouldPass(TileLinkSemaphore(SemaphoreCtrl.Parameter.small()))
    generationShouldPass(TileLinkSemaphore(SemaphoreCtrl.Parameter.medium()))
    generationShouldPass(TileLinkSemaphore(SemaphoreCtrl.Parameter.large()))
    generationShouldPass(TileLinkSemaphore(SemaphoreCtrl.Parameter(1)))
    generationShouldPass(TileLinkSemaphore(SemaphoreCtrl.Parameter(31)))

    generationShouldFail(TileLinkSemaphore(SemaphoreCtrl.Parameter(0)))
    generationShouldFail(TileLinkSemaphore(SemaphoreCtrl.Parameter(33)))
  }

  test("WishboneSemaphoreParameters") {
    generationShouldPass(WishboneSemaphore(SemaphoreCtrl.Parameter()))
    generationShouldPass(WishboneSemaphore(SemaphoreCtrl.Parameter.small()))
    generationShouldPass(WishboneSemaphore(SemaphoreCtrl.Parameter.medium()))
    generationShouldPass(WishboneSemaphore(SemaphoreCtrl.Parameter.large()))
    generationShouldPass(WishboneSemaphore(SemaphoreCtrl.Parameter(1)))
    generationShouldPass(WishboneSemaphore(SemaphoreCtrl.Parameter(31)))

    generationShouldFail(WishboneSemaphore(SemaphoreCtrl.Parameter(0)))
    generationShouldFail(WishboneSemaphore(SemaphoreCtrl.Parameter(33)))
  }


  def init(dut: Apb3Semaphore): (Apb3Driver, SemaphoreCtrl.Regs) = {
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
    val regs = SemaphoreCtrl.Regs(dut.mapper.idCtrl.length, dut.p)

    /* Wait for reset and check initialized state */
    dut.clockDomain.waitSampling(2)
    dut.clockDomain.waitFallingEdge()

    return (apb, regs)
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Semaphore(SemaphoreCtrl.Parameter())
      dut
    }

    compiled.doSim("registerMap") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Semaphore)
      IpIdentificationTest.V0.checkVersion(apb, 1, 0, 0)

      /* Read bank and pin count */
      SimTest.readField(apb, regs.info, 7, 0, 8,  "Semaphore count")
    }

    compiled.doSim("testIO") { dut =>
      val (apb, regs) = init(dut)

      for (i <- 0 until dut.p.count) {
        SimTest.read(apb, regs.semaphore(i), 0, s"Semaphore $i already taken")
        SimTest.read(apb, regs.status, (1 << (i + 1)) - 1, "Wrong number of taken semaphores")
      }
      for (i <- 0 until dut.p.count) {
        SimTest.read(apb, regs.semaphore(i), 1, s"Semaphore $i not taken")
      }
      for (i <- 0 until dut.p.count) {
        apb.write(regs.semaphore(i), 0)
        SimTest.read(apb, regs.status, (1 << dut.p.count) - (1 << (i + 1)), "Wrong number of taken semaphores")
      }
    }

  }
}
