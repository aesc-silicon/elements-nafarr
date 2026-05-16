// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.pwm

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

class PwmTest extends AnyFunSuite {
  test("Apb3PwmParameters") {
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default()))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(1)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(2)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(3)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(4)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(5)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(6)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(7)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(8)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(9)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(10)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(11)))
    generationShouldPass(Apb3Pwm(PwmCtrl.Parameter.default(12)))

    generationShouldFail(Apb3Pwm(PwmCtrl.Parameter.default(0)))
  }

  test("TileLinkPwmParameters") {
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default()))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(1)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(2)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(3)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(4)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(5)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(6)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(7)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(8)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(9)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(10)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(11)))
    generationShouldPass(TileLinkPwm(PwmCtrl.Parameter.default(12)))

    generationShouldFail(TileLinkPwm(PwmCtrl.Parameter.default(0)))
  }

  test("WishbonePwmParameters") {
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default()))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(1)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(2)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(3)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(4)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(5)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(6)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(7)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(8)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(9)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(10)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(11)))
    generationShouldPass(WishbonePwm(PwmCtrl.Parameter.default(12)))

    generationShouldFail(WishbonePwm(PwmCtrl.Parameter.default(0)))
  }

  def init(dut: Apb3Pwm): (Apb3Driver, PwmCtrl.Regs) = {
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
    val regs = PwmCtrl.Regs(dut.mapper.idCtrl.length)

    /* Init */
    dut.io.pwm.syncIn #= false
    dut.io.pwm.faultIn #= false

    /* Wait for reset and check initialized state */
    dut.clockDomain.waitSampling(2)
    dut.clockDomain.waitFallingEdge()

    return (apb, regs)
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Pwm(PwmCtrl.Parameter.default(1))
      dut
    }
    compiled.doSim("basicRegisters") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Pwm)
      IpIdentificationTest.V0.checkVersion(apb, 1, 1, 0)

      /* Read channelPulseWidth, channelPeriodWidth, clockDividerWidth, io.channels */
      SimTest.readField(apb, regs.channelConfig, 31, 24, 20,  "Channel period width")
      SimTest.readField(apb, regs.channelConfig, 23, 16, 20,  "Channel pulse width")
      SimTest.readField(apb, regs.channelConfig, 15, 8, 20,  "Clock divider width")
      SimTest.readField(apb, regs.channelConfig, 7, 0, 1,  "IO channels")

      /* Read dead-time and shot-count widthss */
      SimTest.readField(apb, regs.timingConfig, 15, 8, 8,  "Shot count width")
      SimTest.readField(apb, regs.timingConfig, 7, 0, 8,  "Dead time width")

      /* Read permissions */
      SimTest.readField(apb, regs.permissions, 1, 0, 1, "Permissions")
    }

    compiled.doSim("channel0 - duty cycle") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))

      apb.write(regs.control(channel), BigInt("1", 16))
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(491)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      dut.clockDomain.waitSampling(1)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(498)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling()
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))
    }

    compiled.doSim("channel0 - duty cycle show count 2") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))
      apb.write(regs.shotCount(channel), BigInt("2", 10))

      apb.write(regs.control(channel), BigInt("1", 16))
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(489)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      SimTest.readField(apb, regs.status(channel), 1, 1, 0,  "Channel shot done set")

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(497)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling()
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      SimTest.readField(apb, regs.status(channel), 1, 1, 1,  "Channel shot done not set")

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
    }

    compiled.doSim("channel0 - duty cycle phase offset") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))
      apb.write(regs.phaseOffset(channel), BigInt("1", 10))

      apb.write(regs.control(channel), BigInt("1", 16))
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(100)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(491)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      dut.clockDomain.waitSampling(1)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(498)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling()
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))
    }

    compiled.doSim("channel0 - dead time") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))
      apb.write(regs.deadTime(channel), BigInt("1", 10))

      apb.write(regs.control(channel), BigInt("1", 16))
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(489)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(300)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      dut.clockDomain.waitSampling(1)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(398)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(300)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))
    }

    compiled.doSim("channel0 - duty cycle inverted") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))

      apb.write(regs.control(channel), BigInt("3", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(491)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      dut.clockDomain.waitSampling(1)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(498)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling()
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))
    }

    compiled.doSim("channel0 - start stop start") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      apb.write(regs.control(channel), BigInt("1", 16))
      dut.clockDomain.waitSampling(10)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(5 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(10)
      apb.write(regs.control(channel), BigInt("0", 16))
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("7", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))

      dut.clockDomain.waitSampling(90)
      apb.write(regs.control(channel), BigInt("1", 16))
      dut.clockDomain.waitSampling(100)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      dut.clockDomain.waitSampling(3 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(7 * 100)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
    }

    compiled.doSim("channel0 - center-aligned mode") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))

      apb.write(regs.control(channel), BigInt("5", 16))
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(491)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      dut.clockDomain.waitSampling(1)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(498)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling()
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
    }

    compiled.doSim("channel0 - sync in") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))

      apb.write(regs.control(channel), BigInt("1", 16))
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(491)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(100)

      dut.io.pwm.syncIn #= true
      dut.clockDomain.waitSampling(1)
      dut.io.pwm.syncIn #= false
      dut.clockDomain.waitSampling(2)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(496)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(1)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))

      dut.clockDomain.waitSampling(1)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(498)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling()
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000000", 16))
      dut.clockDomain.waitSampling(400)
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.syncOut.toBigInt == BigInt("00000001", 16))
    }

    compiled.doSim("channel0 - interrupt completed") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))
      apb.write(regs.interruptMask, BigInt("1", 16))
      apb.write(regs.control(channel), BigInt("1", 16))

      dut.clockDomain.waitSampling(2)

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "Period completed interrupt is pending")

      dut.clockDomain.waitSampling(899)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000001", 16), "Period completed interrupt not pending")
      apb.write(regs.interruptPending, BigInt("1", 16))
      dut.clockDomain.waitSampling(1)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "Period completed interrupt is pending")

      dut.clockDomain.waitSampling(898)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000001", 16), "Period completed interrupt not pending")
    }

    compiled.doSim("channel0 - fault input") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))
      apb.write(regs.errorMask, BigInt("1", 16))
      apb.write(regs.control(channel), BigInt("1", 16))

      dut.clockDomain.waitSampling(2)

      assert(dut.io.pwm.output.toBigInt == BigInt("00000001", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "Fault input error is pending")
      dut.io.pwm.faultIn #= true
      SimTest.read(apb, regs.errorPending, BigInt("00000001", 16), "Fault input error isn't pending")
      assert(dut.io.pwm.output.toBigInt == BigInt("00000000", 16))
      assert(dut.io.pwm.compOutput.toBigInt == BigInt("00000000", 16))
    }

    compiled.doSim("channel0 - config error - falling edge") { dut =>
      val (apb, regs) = init(dut)
      val channel = 0

      /* Init - Set clock divider to 1 us */
      apb.write(regs.clockDivider(channel), BigInt("99", 10))

      apb.write(regs.errorMask, BigInt("2", 16))
      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "Config error pending")

      /* Init channel 0: period=9, active [risingEdge=5, fallingEdge=9] -> 5/10 duty */
      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("9", 10))

      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "Config error pending")

      apb.write(regs.period(channel), BigInt("9", 10))
      apb.write(regs.risingEdge(channel), BigInt("5", 10))
      apb.write(regs.fallingEdge(channel), BigInt("10", 10))

      SimTest.read(apb, regs.errorPending, BigInt("00000002", 16), "Config error isn't pending")
    }

  }
}
