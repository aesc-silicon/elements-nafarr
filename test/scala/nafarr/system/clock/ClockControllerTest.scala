// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.clock

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

case class ClockDividerController(
    parameter: ClockControllerCtrl.Parameter,
    inputClock: ClockParameter,
    clocks: List[String],
    busConfig: Apb3Config = Apb3Config(12, 32)
) extends Component {
  val io = new Bundle {
    val bus = slave(Apb3(busConfig))
    val mainClock = in(Bool())
    val mainReset = in(Bool())
    val outputs = out(UInt(parameter.domains.length bits))
  }

  val controller = Apb3ClockController(parameter, busConfig)
  val core = ClockControllerCtrl.ClockDividerController(parameter, inputClock, clocks)

  controller.io.bus <> io.bus
  core.io.mainClock := io.mainClock
  core.io.mainReset := io.mainReset
  core.io.config := controller.io.config

  io.outputs := core.io.clocks

  def idCtrl = controller.idCtrl
}

class ClockControllerTest extends AnyFunSuite {
  test("Apb3GpioParameters") {
    generationShouldPass(Apb3ClockController(ClockControllerCtrl.Parameter(List(
      ClockParameter("a", 100 MHz),
      ClockParameter("b", 50 MHz, synchronousWith="b"),
      ClockParameter("c", 25 MHz)
    ))))
    generationShouldFail(Apb3ClockController(ClockControllerCtrl.Parameter(List(
    ))))
    generationShouldFail(Apb3ClockController(ClockControllerCtrl.Parameter(List(
      ClockParameter("a", 100 MHz),
      ClockParameter("b", 50 MHz, synchronousWith="d"),
      ClockParameter("c", 25 MHz)
    ))))
  }

  test("TileLinkGpioParameters") {
    generationShouldPass(TileLinkClockController(ClockControllerCtrl.Parameter(List(
      ClockParameter("a", 100 MHz),
      ClockParameter("b", 50 MHz, synchronousWith="b"),
      ClockParameter("c", 25 MHz)
    ))))
    generationShouldFail(TileLinkClockController(ClockControllerCtrl.Parameter(List(
    ))))
    generationShouldFail(TileLinkClockController(ClockControllerCtrl.Parameter(List(
      ClockParameter("a", 100 MHz),
      ClockParameter("b", 50 MHz, synchronousWith="d"),
      ClockParameter("c", 25 MHz)
    ))))
  }

  test("WishboneGpioParameters") {
    generationShouldPass(WishboneClockController(ClockControllerCtrl.Parameter(List(
      ClockParameter("a", 100 MHz),
      ClockParameter("b", 50 MHz, synchronousWith="b"),
      ClockParameter("c", 25 MHz)
    ))))
    generationShouldFail(WishboneClockController(ClockControllerCtrl.Parameter(List(
    ))))
    generationShouldFail(WishboneClockController(ClockControllerCtrl.Parameter(List(
      ClockParameter("a", 100 MHz),
      ClockParameter("b", 50 MHz, synchronousWith="d"),
      ClockParameter("c", 25 MHz)
    ))))
  }

  private def initBase(bus: Apb3, cd: ClockDomain, idCtrlLength: BigInt): (Apb3Driver, ClockControllerCtrl.Regs) = {
    val apb = new Apb3Driver(bus, cd)
    val regs = ClockControllerCtrl.Regs(idCtrlLength)
    cd.waitFallingEdge()
    (apb, regs)
  }

  def init(dut: ClockDividerController): (Apb3Driver, ClockControllerCtrl.Regs) = {
    dut.clockDomain.forkStimulus(10)
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
    initBase(dut.io.bus, dut.clockDomain, dut.idCtrl.length)
  }

  test("ClockDividerController") {
    val compiled = SimConfig.withWave.addSimulatorFlag("--x-initial 0").compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = ClockDividerController(
          ClockControllerCtrl.Parameter(List(
            ClockParameter("a", 100 MHz),
            ClockParameter("b", 50 MHz),
            ClockParameter("c", 25 MHz),
            ClockParameter("d", 12.5 MHz)
          )),
          ClockParameter("input", 100 MHz),
          List("a", "b", "c", "d")
        )
      }
      area.dut
    }

    compiled.doSim("registerMap") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Clock)
      IpIdentificationTest.V0.checkVersion(apb, 1, 0, 0)

      /* Read domains */
      SimTest.readField(apb, regs.domains, 7, 0, 4,  "Reset domains")

      /* All domains are enabled */
      SimTest.readField(apb, regs.enable, 7, 0, BigInt("f", 16),  "Domains enabled")
    }

    compiled.doSim("clock output") { dut =>
      val (apb, regs) = init(dut)

      for (_ <- 0 until 32) {
        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0001", 2), "Found running clock")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0000", 2), "Found running clock")
      }

      dut.io.mainReset #= true

      for (_ <- 0 until 4) {
        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1111", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1110", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1101", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1100", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1011", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1010", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1001", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("1000", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0111", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0110", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0101", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0100", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0011", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0010", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0001", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0000", 2), "Wrong clock pattern")
      }

      dut.clockDomain.waitFallingEdge(10)
    }

    compiled.doSim("clock output - enable") { dut =>
      val (apb, regs) = init(dut)

      for (_ <- 0 until 32) {
        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0001", 2), "Found running clock")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0000", 2), "Found running clock")
      }

      apb.write(regs.enable, BigInt("0101", 2))
      dut.io.mainReset #= true

      for (_ <- 0 until 4) {
        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0101", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0100", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0101", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0100", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0001", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0000", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0001", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0000", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0101", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0100", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0101", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0100", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0001", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0000", 2), "Wrong clock pattern")

        dut.clockDomain.waitRisingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0001", 2), "Wrong clock pattern")
        dut.clockDomain.waitFallingEdge()
        sleep(1)
        SimTest.checkPins(dut.io.outputs.toBigInt, BigInt("0000", 2), "Wrong clock pattern")
      }

      dut.clockDomain.waitFallingEdge(10)
    }

  }
}
