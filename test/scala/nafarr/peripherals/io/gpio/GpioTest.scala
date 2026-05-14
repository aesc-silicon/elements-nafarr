// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.io.gpio

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

class GpioTest extends AnyFunSuite {
  test("Apb3GpioParameters") {
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.default()))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.noInterrupt()))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.onlyOutput()))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.onlyInput()))

    generationShouldFail(Apb3Gpio(GpioCtrl.Parameter.default(0)))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.default(1)))
    generationShouldPass(Apb3Gpio(GpioCtrl.Parameter.default(33)))
  }

  test("TileLinkGpioParameters") {
    generationShouldPass(TileLinkGpio(GpioCtrl.Parameter.default()))
    generationShouldPass(TileLinkGpio(GpioCtrl.Parameter.noInterrupt()))
    generationShouldPass(TileLinkGpio(GpioCtrl.Parameter.onlyOutput()))
    generationShouldPass(TileLinkGpio(GpioCtrl.Parameter.onlyInput()))

    generationShouldFail(TileLinkGpio(GpioCtrl.Parameter.default(0)))
    generationShouldPass(TileLinkGpio(GpioCtrl.Parameter.default(1)))
    generationShouldPass(TileLinkGpio(GpioCtrl.Parameter.default(33)))
  }

  test("WishboneGpioParameters") {
    generationShouldPass(WishboneGpio(GpioCtrl.Parameter.default()))
    generationShouldPass(WishboneGpio(GpioCtrl.Parameter.noInterrupt()))
    generationShouldPass(WishboneGpio(GpioCtrl.Parameter.onlyOutput()))
    generationShouldPass(WishboneGpio(GpioCtrl.Parameter.onlyInput()))

    generationShouldFail(WishboneGpio(GpioCtrl.Parameter.default(0)))
    generationShouldPass(WishboneGpio(GpioCtrl.Parameter.default(1)))
    generationShouldPass(WishboneGpio(GpioCtrl.Parameter.default(33)))
  }

  def init(dut: Apb3Gpio): (Apb3Driver, BigInt, BigInt) = {
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

    /* Init */
    dut.io.gpio.pins.read #= 0

    /* Wait for reset and check initialized state */
    dut.clockDomain.waitSampling(2)
    dut.clockDomain.waitFallingEdge()
    assert(
      dut.io.interrupt.toBigInt == 0,
      f"Interrupt pending (0x${dut.io.interrupt.toBigInt}%08x)"
    )

    return (apb, dut.mapper.staticOffset, dut.mapper.regOffset)
  }

  def seqToBigInt(seq: Seq[Int]): BigInt = seq.foldLeft(BigInt(0))((acc, bit) => acc | (BigInt(1) << bit))

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Gpio(GpioCtrl.Parameter(
          io = Gpio.Parameter(32),
          readBufferDepth = 1,
          input = Some(Seq(0, 1, 2, 3, 5, 7, 31)),
          output = Some(Seq(0, 3, 4, 5, 6, 7, 31)),
          interrupt = Some(Seq(0, 3, 5, 7, 31))
        )
      )
      dut.ctrl.io.value.simPublic()
      dut
    }

    compiled.doSim("registerMap") { dut =>
      val (apb, staticOffset, _) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Gpio)
      IpIdentificationTest.V0.checkVersion(apb, 1, 0, 0)

      /* Read bank and pin count */
      SimTest.readField(apb, staticOffset, 31, 16, 1,  "GPIO bank count")
      SimTest.readField(apb, staticOffset, 15,  0, 32, "GPIO pin count")
    }

    compiled.doSim("testIO") { dut =>
      val (apb, _, regOffset) = init(dut)

      val inputMask = seqToBigInt(dut.parameter.input.get)
      val outputMask = seqToBigInt(dut.parameter.output.get)

      /* Check if input synchronization works */
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      dut.clockDomain.waitFallingEdge(dut.ctrl.p.readBufferDepth - 1)
      SimTest.checkPins(dut.ctrl.io.value.toBigInt, BigInt("00000000", 16), "GPIO output all low")
      dut.clockDomain.waitFallingEdge(1)
      SimTest.checkPins(dut.ctrl.io.value.toBigInt, BigInt("ffffffff", 16), "GPIO output all high")

      /* Check if input filter is working */
      SimTest.read(apb, regOffset, inputMask, f"Unable to get 0x${inputMask}%08x from GPIO read")

      /* Check if GPIO write works */
      dut.clockDomain.waitFallingEdge()
      apb.write(regOffset + 4, BigInt("ffffffff", 16))
      dut.clockDomain.waitFallingEdge(1)
      SimTest.checkPins(dut.io.gpio.pins.write.toBigInt, outputMask, f"GPIO output doesn't match 0x${outputMask}%08x")
      SimTest.read(apb, regOffset + 4, outputMask, f"Unable to get 0x${outputMask}%08x from GPIO write")

      /* Check if GPIO direction works */
      dut.clockDomain.waitFallingEdge()
      apb.write(regOffset + 8, BigInt("ffffffff", 16))
      dut.clockDomain.waitFallingEdge(1)

      SimTest.checkPins(dut.io.gpio.pins.writeEnable.toBigInt, outputMask, f"GPIO output enable doesn't match 0x${outputMask}%08x")
      SimTest.read(apb, regOffset + 8, outputMask, f"Unable to get 0x${outputMask}%08x from GPIO write")
    }

    compiled.doSim("testIRQ - Level High") { dut =>
      val (apb, _, regOffset) = init(dut)

      val irqMask = seqToBigInt(dut.parameter.interrupt.get)
      val irqPendingReg = regOffset + 12
      val irqMaskReg = regOffset + 16

      /* Test interrupt on high signal */
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      apb.write(irqMaskReg, BigInt("ffffffff", 16))
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth + 1) {
        SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
        dut.clockDomain.waitFallingEdge()
      }
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")
      SimTest.read(apb, irqPendingReg, irqMask, f"Wrong input pins triggered interrupt enable bits")

      /* Clear interrupt */
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      /* Enable interrupt again */
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      dut.clockDomain.waitFallingEdge(dut.ctrl.p.readBufferDepth + 1)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")
      /* Clear interrupt mask */
      apb.write(irqMaskReg, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
    }

    compiled.doSim("testIRQ - Level Low") { dut =>
      val (apb, _, regOffset) = init(dut)

      val irqMask = seqToBigInt(dut.parameter.interrupt.get)
      val irqPendingReg = regOffset + 20
      val irqMaskReg = regOffset + 24

      /* Test interrupt on low signal */
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      apb.write(irqMaskReg, BigInt("ffffffff", 16))
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth + 1) {
        SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
        dut.clockDomain.waitFallingEdge()
      }
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")
      SimTest.read(apb, irqPendingReg, irqMask, f"Wrong input pins triggered interrupt enable bits")

      /* Clear interrupt */
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      /* Enable interrupt again */
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      dut.clockDomain.waitFallingEdge(dut.ctrl.p.readBufferDepth + 1)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")
      /* Clear interrupt mask */
      apb.write(irqMaskReg, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
    }

    compiled.doSim("testIRQ - Rising Edge") { dut =>
      val (apb, _, regOffset) = init(dut)

      val irqMask = seqToBigInt(dut.parameter.interrupt.get)
      val irqPendingReg = regOffset + 28
      val irqMaskReg = regOffset + 32

      /* Test interrupt on rising signal */
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      apb.write(irqMaskReg, BigInt("ffffffff", 16))
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      dut.clockDomain.waitFallingEdge()
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth) {
        SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
        dut.clockDomain.waitFallingEdge()
      }
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")

      /* Clear interrupt */
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      /* Enable interrupt again */
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      dut.clockDomain.waitFallingEdge(dut.ctrl.p.readBufferDepth + 1)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")

      /* Clear interrupt mask */
      apb.write(irqMaskReg, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
    }

    compiled.doSim("testIRQ - Falling Edge") { dut =>
      val (apb, _, regOffset) = init(dut)

      val irqMask = seqToBigInt(dut.parameter.interrupt.get)
      val irqPendingReg = regOffset + 36
      val irqMaskReg = regOffset + 40

      /* Test interrupt on falling signal */
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      apb.write(irqMaskReg, BigInt("ffffffff", 16))
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      dut.clockDomain.waitFallingEdge()
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      for (_ <- 0 to dut.ctrl.p.readBufferDepth) {
        SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
        dut.clockDomain.waitFallingEdge()
      }
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")

      /* Clear interrupt */
      apb.write(irqPendingReg, BigInt("ffffffff", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
      /* Enable interrupt again */
      dut.io.gpio.pins.read #= BigInt("ffffffff", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.gpio.pins.read #= BigInt("00000000", 16)
      dut.clockDomain.waitFallingEdge(dut.ctrl.p.readBufferDepth + 1)
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt not pending")

      /* Clear interrupt mask */
      apb.write(irqMaskReg, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt pending")
    }

  }
}
