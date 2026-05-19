// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.reset

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

case class DummyResetController(
    parameter: ResetControllerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Component {
  val io = new Bundle {
    val bus = slave(Apb3(busConfig))
    val mainClock = in(Bool())
    val mainReset = in(Bool())
    val trigger = in(UInt(parameter.domains.length bits))
    val resets = out(UInt(parameter.domains.length bits))
  }

  val controller = Apb3ResetController(parameter, busConfig)
  val core = ResetControllerCtrl.DummyResetController(parameter)

  controller.io.bus <> io.bus
  core.io.mainReset := io.mainReset
  core.io.mainClock := io.mainClock
  core.io.config := controller.io.config
  core.io.trigger := io.trigger

  io.resets := core.io.resets

  def idCtrl = controller.idCtrl
}

case class GeneratorResetController(
    parameter: ResetControllerCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Component {
  val io = new Bundle {
    val bus = slave(Apb3(busConfig))
    val mainClock = in(Bool())
    val trigger = in(UInt(parameter.domains.length bits))
    val resets = out(UInt(parameter.domains.length bits))
  }

  val controller = Apb3ResetController(parameter, busConfig)
  val core = ResetControllerCtrl.GeneratorResetController(parameter)

  controller.io.bus <> io.bus
  core.io.mainReset := False
  core.io.mainClock := io.mainClock
  core.io.config := controller.io.config
  core.io.trigger := io.trigger

  io.resets := core.io.resets

  def idCtrl = controller.idCtrl
}

class ResetControllerTest extends AnyFunSuite {
  test("Apb3ResetControllerParameters") {
    generationShouldPass(Apb3ResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1)
    ))))
    generationShouldPass(Apb3ResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1),
      ResetParameter("b", 1)
    ))))
    generationShouldFail(Apb3ResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 0)
    ))))
    generationShouldFail(Apb3ResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1),
      ResetParameter("b", 0)
    ))))
    generationShouldFail(Apb3ResetController(ResetControllerCtrl.Parameter(List(
    ))))
  }

  test("TileLinkResetControllerParameters") {
    generationShouldPass(TileLinkResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1)
    ))))
    generationShouldPass(TileLinkResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1),
      ResetParameter("b", 1)
    ))))
    generationShouldFail(TileLinkResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 0)
    ))))
    generationShouldFail(TileLinkResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1),
      ResetParameter("b", 0)
    ))))
    generationShouldFail(TileLinkResetController(ResetControllerCtrl.Parameter(List(
    ))))
  }

  test("WishboneResetControllerParameters") {
    generationShouldPass(WishboneResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1)
    ))))
    generationShouldPass(WishboneResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1),
      ResetParameter("b", 1)
    ))))
    generationShouldFail(WishboneResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 0)
    ))))
    generationShouldFail(WishboneResetController(ResetControllerCtrl.Parameter(List(
      ResetParameter("a", 1),
      ResetParameter("b", 0)
    ))))
    generationShouldFail(WishboneResetController(ResetControllerCtrl.Parameter(List(
    ))))
  }

  private def initBase(bus: Apb3, cd: ClockDomain, idCtrlLength: BigInt): (Apb3Driver, ResetControllerCtrl.Regs) = {
    val apb = new Apb3Driver(bus, cd)
    val regs = ResetControllerCtrl.Regs(idCtrlLength)
    cd.waitFallingEdge()
    (apb, regs)
  }

  def init(dut: DummyResetController): (Apb3Driver, ResetControllerCtrl.Regs) = {
    dut.clockDomain.forkStimulus(10 * 1000)
    fork {
      dut.io.mainClock #= false
      sleep(10)
      while (true) {
        dut.io.mainClock #= true
        dut.clockDomain.clockToggle()
        sleep(5)
        dut.io.mainClock #= false
        dut.clockDomain.clockToggle()
        sleep(5)
      }
    }
    dut.io.mainReset #= false
    dut.io.trigger #= BigInt("0", 16)
    initBase(dut.io.bus, dut.clockDomain, dut.idCtrl.length)
  }

  def init(dut: GeneratorResetController): (Apb3Driver, ResetControllerCtrl.Regs) = {
    dut.clockDomain.forkStimulus(10 * 1000)
    fork {
      dut.io.mainClock #= false
      sleep(10)
      while (true) {
        dut.io.mainClock #= true
        dut.clockDomain.clockToggle()
        sleep(5)
        dut.io.mainClock #= false
        dut.clockDomain.clockToggle()
        sleep(5)
      }
    }
    dut.io.trigger #= BigInt("0", 16)
    initBase(dut.io.bus, dut.clockDomain, dut.idCtrl.length)
  }

  test("DummyResetController") {
    val compiled = SimConfig.withWave.addSimulatorFlag("--x-initial 0").compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = DummyResetController(ResetControllerCtrl.Parameter(List(
          ResetParameter("a", 8),
          ResetParameter("b", 16),
          ResetParameter("c", 32)
        )))
      }
      area.dut
    }

    compiled.doSim("registerMap") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Reset)
      IpIdentificationTest.V0.checkVersion(apb, 1, 0, 0)

      /* Read domains */
      SimTest.readField(apb, regs.domains, 7, 0, 3,  "Reset domains")
    }

    compiled.doSim("delay") { dut =>
      val (apb, regs) = init(dut)

      for (_ <- 0 until 8) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("0", 16), "Found non-active reset")
      }

      dut.io.mainReset #= true

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("0", 16), "Found non-active reset")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("1", 16), "Only domain a should be active")

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("1", 16), "Only domain a should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(20)
    }

    compiled.doSim("trigger domain a") { dut =>
      val (apb, regs) = init(dut)

      for (_ <- 0 until 8) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("0", 16), "Found non-active reset")
      }

      dut.io.mainReset #= true

      dut.clockDomain.waitFallingEdge(36)

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      apb.write(regs.trigger, BigInt("00000001", 16))
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      apb.write(regs.acknowledge, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("6", 16), "Only domain b, c should be active")
      }

      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // External triggers
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("1", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(2)

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("6", 16), "Only domain b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // Disable triggers
      apb.write(regs.enable, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("1", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(10)
    }

    compiled.doSim("trigger domain b") { dut =>
      val (apb, regs) = init(dut)

      for (_ <- 0 until 8) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("0", 16), "Found non-active reset")
      }

      dut.io.mainReset #= true

      dut.clockDomain.waitFallingEdge(36)

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      apb.write(regs.trigger, BigInt("00000002", 16))
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      apb.write(regs.acknowledge, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("5", 16), "Only domain a, c should be active")
      }

      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // External triggers
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("2", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(2)

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("5", 16), "Only domain a, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // Disable triggers
      apb.write(regs.enable, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("2", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(10)
    }

    compiled.doSim("trigger domain c") { dut =>
      val (apb, regs) = init(dut)

      for (_ <- 0 until 8) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("0", 16), "Found non-active reset")
      }

      dut.io.mainReset #= true

      dut.clockDomain.waitFallingEdge(36)

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      apb.write(regs.trigger, BigInt("00000004", 16))
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      apb.write(regs.acknowledge, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 32 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")
      }

      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // External triggers
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("4", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(2)

      for (_ <- 0 until 32 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // Disable triggers
      apb.write(regs.enable, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("4", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 32 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(10)
    }
  }

  test("GeneratorResetController") {
    val compiled = SimConfig.withWave.addSimulatorFlag("--x-initial 0").compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = GeneratorResetController(ResetControllerCtrl.Parameter(List(
          ResetParameter("a", 8),
          ResetParameter("b", 16),
          ResetParameter("c", 32)
        )))
      }
      area.dut
    }

    compiled.doSim("registerMap") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Reset)
      IpIdentificationTest.V0.checkVersion(apb, 1, 0, 0)

      /* Read domains */
      SimTest.readField(apb, regs.domains, 7, 0, 3,  "Reset domains")
    }

    compiled.doSim("delay") { dut =>
      val (apb, regs) = init(dut)

      for (_ <- 0 until 8 - 1 - 2) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("0", 16), "Found non-active reset")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("1", 16), "Only domain a should be active")

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("1", 16), "Only domain a should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(20)
    }

    compiled.doSim("trigger domain a") { dut =>
      val (apb, regs) = init(dut)

      dut.clockDomain.waitFallingEdge(36)

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      apb.write(regs.trigger, BigInt("00000001", 16))
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      apb.write(regs.acknowledge, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("6", 16), "Only domain b, c should be active")
      }

      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // External triggers
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("1", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(2)

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("6", 16), "Only domain b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // Disable triggers
      apb.write(regs.enable, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("1", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 8 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(10)
    }

    compiled.doSim("trigger domain b") { dut =>
      val (apb, regs) = init(dut)

      dut.clockDomain.waitFallingEdge(36)

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      apb.write(regs.trigger, BigInt("00000002", 16))
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      apb.write(regs.acknowledge, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("5", 16), "Only domain a, c should be active")
      }

      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // External triggers
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("2", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(2)

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("5", 16), "Only domain a, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // Disable triggers
      apb.write(regs.enable, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("2", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 16 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(10)
    }

    compiled.doSim("trigger domain c") { dut =>
      val (apb, regs) = init(dut)

      dut.clockDomain.waitFallingEdge(36)

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      apb.write(regs.trigger, BigInt("00000004", 16))
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      apb.write(regs.acknowledge, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 32 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")
      }

      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // External triggers
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("4", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(2)

      for (_ <- 0 until 32 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("3", 16), "Only domain a, b should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      // Disable triggers
      apb.write(regs.enable, BigInt("00000000", 16))
      dut.clockDomain.waitFallingEdge(10)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.io.trigger #= BigInt("4", 16)
      dut.clockDomain.waitFallingEdge()
      dut.io.trigger #= BigInt("0", 16)
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      dut.clockDomain.waitFallingEdge(3)

      for (_ <- 0 until 32 - 1) {
        dut.clockDomain.waitFallingEdge()
        SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")
      }

      dut.clockDomain.waitFallingEdge()
      SimTest.checkPins(dut.io.resets.toBigInt, BigInt("7", 16), "Only domain a, b, c should be active")

      dut.clockDomain.waitFallingEdge(10)
    }
  }

}
