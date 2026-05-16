// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.pinmux

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

import scala.collection.mutable.ArrayBuffer

class PinmuxTest extends AnyFunSuite {
  test("Apb3GpioParameters") {
    generationShouldPass(Apb3Pinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 24, 2),
      (0 until 12).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))

    generationShouldPass(Apb3Pinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 36, 3),
      (0 until 12).map(i => (i, List(i * 3, i * 3 + 1, i * 3 + 2))).to[ArrayBuffer]
    ))

    generationShouldPass(Apb3Pinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 48, 4),
      (0 until 12).map(i => (i, List(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3))).to[ArrayBuffer]
    ))

    generationShouldPass(Apb3Pinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(255), 510, 2),
      (0 until 255).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))
    generationShouldFail(Apb3Pinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(256), 512, 2),
      (0 until 256).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))
  }

  test("TileLinkGpioParameters") {
    generationShouldPass(TileLinkPinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 24, 2),
      (0 until 12).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))

    generationShouldPass(TileLinkPinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 36, 3),
      (0 until 12).map(i => (i, List(i * 3, i * 3 + 1, i * 3 + 2))).to[ArrayBuffer]
    ))

    generationShouldPass(TileLinkPinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 48, 4),
      (0 until 12).map(i => (i, List(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3))).to[ArrayBuffer]
    ))

    generationShouldPass(TileLinkPinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(255), 510, 2),
      (0 until 255).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))
    generationShouldFail(TileLinkPinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(256), 512, 2),
      (0 until 256).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))
  }

  test("WishboneGpioParameters") {
    generationShouldPass(WishbonePinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 24, 2),
      (0 until 12).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))

    generationShouldPass(WishbonePinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 36, 3),
      (0 until 12).map(i => (i, List(i * 3, i * 3 + 1, i * 3 + 2))).to[ArrayBuffer]
    ))

    generationShouldPass(WishbonePinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(12), 48, 4),
      (0 until 12).map(i => (i, List(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3))).to[ArrayBuffer]
    ))

    generationShouldPass(WishbonePinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(255), 510, 2),
      (0 until 255).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))
    generationShouldFail(WishbonePinmux(
      PinmuxCtrl.Parameter(Pinmux.Parameter(256), 512, 2),
      (0 until 256).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
    ))
  }

  def init(dut: Apb3Pinmux): (Apb3Driver, PinmuxCtrl.Regs) = {
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
    val regs = PinmuxCtrl.Regs(dut.mapper.idCtrl.length)

    /* Init */
    dut.io.pins.pins.read #= 0
    dut.io.inputs.write #= 0
    dut.io.inputs.writeEnable #= 0

    /* Wait for reset and check initialized state */
    dut.clockDomain.waitSampling(2)
    dut.clockDomain.waitFallingEdge()

    return (apb, regs)
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3Pinmux(
        PinmuxCtrl.Parameter(Pinmux.Parameter(12), 24, 2),
        (0 until 12).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]
      )
      dut
    }

    compiled.doSim("registerMap") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Pinmux)
      IpIdentificationTest.V0.checkVersion(apb, 1, 0, 0)

      /* Read mux options and input pins */
      SimTest.readField(apb, regs.info, 15, 8, 2, "Number of input to output options")
      SimTest.readField(apb, regs.info, 7, 0, 12, "Number of input pins")
    }

    compiled.doSim("mux options - input") { dut =>
      val (apb, regs) = init(dut)

      SimTest.checkPins(dut.io.inputs.read.toBigInt, 0, "Internal read pins should be low")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, 0, "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, 0, "Output pin direction should be low")

      dut.clockDomain.waitFallingEdge()

      dut.io.pins.pins.read #= BigInt("00000001", 16)

      dut.clockDomain.waitFallingEdge()

      SimTest.checkPins(dut.io.inputs.read.toBigInt, BigInt("00000001", 16), "Internal read pins have wrong value")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, BigInt("00000000", 16), "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, BigInt("00000000", 16), "Default output pin direction should be low")

      dut.io.pins.pins.read #= BigInt("00000fff", 16)

      dut.clockDomain.waitFallingEdge()

      SimTest.checkPins(dut.io.inputs.read.toBigInt, BigInt("00555555", 16), "Internal read pins have wrong value")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, BigInt("00000000", 16), "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, BigInt("00000000", 16), "Default output pin direction should be low")

      dut.clockDomain.waitFallingEdge()

      for (i <- 0 to dut.parameter.io.width) {
        apb.write(regs.option(i), BigInt("1", 16))
      }

      dut.clockDomain.waitFallingEdge()

      SimTest.checkPins(dut.io.inputs.read.toBigInt, BigInt("00aaaaaa", 16), "Internal read pins have wrong value")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, BigInt("00000000", 16), "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, BigInt("00000000", 16), "Default output pin direction should be low")
    }

    compiled.doSim("mux options - output") { dut =>
      val (apb, regs) = init(dut)

      SimTest.checkPins(dut.io.inputs.read.toBigInt, 0, "Internal read pins should be low")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, 0, "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, 0, "Output pin direction should be low")

      dut.clockDomain.waitFallingEdge()

      dut.io.inputs.write #= BigInt("00000000", 16)
      dut.io.inputs.writeEnable #= BigInt("00000000", 16)

      dut.clockDomain.waitFallingEdge()

      SimTest.checkPins(dut.io.inputs.read.toBigInt, BigInt("00000000", 16), "Internal read pins have wrong value")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, BigInt("00000000", 16), "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, BigInt("00000000", 16), "Default output pin direction should be low")

      dut.io.inputs.write #= BigInt("00555555", 16)
      dut.io.inputs.writeEnable #= BigInt("00555555", 16)

      dut.clockDomain.waitFallingEdge()

      SimTest.checkPins(dut.io.inputs.read.toBigInt, BigInt("00000000", 16), "Internal read pins have wrong value")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, BigInt("00000fff", 16), "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, BigInt("00000fff", 16), "Default output pin direction should be low")

      dut.clockDomain.waitFallingEdge()

      for (i <- 0 to dut.parameter.io.width) {
        apb.write(regs.option(i), BigInt("1", 16))
      }

      dut.clockDomain.waitFallingEdge()

      SimTest.checkPins(dut.io.inputs.read.toBigInt, BigInt("00000000", 16), "Internal read pins have wrong value")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, BigInt("00000000", 16), "Output pin value should be low")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, BigInt("00000000", 16), "Default output pin direction should be low")

      dut.clockDomain.waitFallingEdge()

      dut.io.inputs.write #= BigInt("00aaaaaa", 16)
      dut.io.inputs.writeEnable #= BigInt("00aaaaaa", 16)

      dut.clockDomain.waitFallingEdge()

      SimTest.checkPins(dut.io.inputs.read.toBigInt, BigInt("00000000", 16), "Internal read pins have wrong value")
      SimTest.checkPins(dut.io.pins.pins.write.toBigInt, BigInt("00000fff", 16), "Output pin value should be high")
      SimTest.checkPins(dut.io.pins.pins.writeEnable.toBigInt, BigInt("00000fff", 16), "Default output pin direction should be high")
    }

  }
}
