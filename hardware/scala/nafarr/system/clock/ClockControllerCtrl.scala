// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.clock

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import scala.collection.mutable.Map

import nafarr.blackboxes.xilinx.a7.XilinxPLL
import nafarr.blackboxes.lattice.ecp5.LatticePLL
import nafarr.system.reset.ResetControllerCtrl

object ClockControllerCtrl {

  case class Parameter(domains: List[ClockParameter]) {
    // TODO check synchronousWith is before

    def getDomainByName(name: String): (Int, ClockParameter) = {
      val domain = domains.find(_.name.equals(name)).get
      (domains.indexOf(domain), domain)
    }
  }

  case class Config(parameter: Parameter) extends Bundle {
    val enable = UInt(parameter.domains.length bits)
  }

  abstract class ClockControllerBase(parameter: Parameter) extends Component {
    addAttribute("keep_hierarchy", "yes")
    val io = new Bundle {
      val mainClock = in(Bool())
      val mainReset = in(Bool())
      val clocks = out(UInt(parameter.domains.length bits))
      val config = in(Config(parameter))
    }

    var generatedClocks = List[Bool]()
    var clockDict = Map[String, (ClockDomain, Int)]()
    def getClockDomainByName(name: String): ClockDomain = clockDict(name)._1
    def getPortIndexByName(name: String): Int = clockDict(name)._2
  }

  def connect(
      parameter: Parameter,
      clockCtrl: ClockControllerBase,
      resetCtrl: ResetControllerCtrl.ResetControllerBase
  ): Unit = {
    for ((domain, index) <- parameter.domains.zipWithIndex) {
      val cd = ClockDomain.internal(
        name = domain.name,
        frequency = FixedFrequency(domain.frequency),
        config = domain.resetConfig
      )
      cd.clock := clockCtrl.io.clocks(index)
      if (!domain.reset.isEmpty) {
        cd.reset := resetCtrl.io.resets(resetCtrl.getResetByName(domain.reset)._2)
      }
      if (!domain.synchronousWith.isEmpty()) {
        cd.setSynchronousWith(clockCtrl.getClockDomainByName(domain.synchronousWith))
      }
      clockCtrl.clockDict += domain.name -> (cd, index)
    }
  }

  case class ClockDividerController(
      parameter: Parameter,
      inputClock: ClockParameter,
      clocks: List[String]
  ) extends ClockControllerBase(parameter) {
    val inputHz = inputClock.frequency.toDouble

    val clockCtrlClockDomain = ClockDomain(
      clock = io.mainClock,
      reset = io.mainReset,
      frequency = FixedFrequency(inputClock.frequency),
      config = inputClock.resetConfig
    )

    val clockCtrl = new ClockingArea(clockCtrlClockDomain) {
      for (clockName <- clocks) {
        val (domainIndex, domain) = parameter.getDomainByName(clockName)
        val outputHz = domain.frequency.toDouble

        require(
          inputHz % outputHz == 0,
          s"Clock '$clockName': output frequency ${outputHz.toLong} Hz does not divide input frequency ${inputHz.toLong} Hz evenly"
        )
        val divider = (inputHz / outputHz).toInt
        require(
          divider >= 1,
          s"Clock '$clockName': divider $divider must be >= 1"
        )

        val clockOut = if (divider == 1) {
          io.mainClock
        } else {
          require(
            divider % 2 == 0,
            s"Clock '$clockName': divider $divider must be even to generate a 50% duty cycle clock"
          )
          val halfPeriod = divider / 2
          val divided = Reg(Bool()) init False
          if (halfPeriod == 1) {
            divided := !divided
          } else {
            val counter = Reg(UInt(log2Up(halfPeriod) bits)) init 0
            when(counter === halfPeriod - 1) {
              counter := 0
              divided := !divided
            } otherwise {
              counter := counter + 1
            }
          }
          divided
        }
        io.clocks(domainIndex) := clockOut
      }
    }
  }

  case class LatticeECP5PllController(
      parameter: Parameter,
      inputClock: ClockParameter,
      clocks: List[String],
      clockVco: HertzNumber = 400 MHz
  ) extends ClockControllerBase(parameter) {
    val clockFrequency = inputClock.frequency
    val clockPair = (clockFrequency.toDouble / 1e6, clockVco.toDouble / 1e6)

    val (clkIDiv, clkFbDiv, clkOpDiv) = clockPair match {
      case (100, 400) => (2, 1, 8)
    }

    val clockCtrlClockDomain = ClockDomain(
      clock = io.mainClock,
      frequency = FixedFrequency(clockFrequency),
      config = ClockDomainConfig(
        resetKind = BOOT
      )
    )

    val clockCtrl = new ClockingArea(clockCtrlClockDomain) {
      val pll0 = LatticePLL.EHXPLLL().connect()
      val pll1 = LatticePLL.EHXPLLL().connect()
    }

    def addClock(index: Int, frequency: HertzNumber) = index match {
      case 0 => clockCtrl.pll0.addClock0(frequency)
      case 1 => clockCtrl.pll0.addClock1(frequency)
      case 2 => clockCtrl.pll0.addClock2(frequency)
      case 3 => clockCtrl.pll1.addClock0(frequency)
      case 4 => clockCtrl.pll1.addClock1(frequency)
      case 5 => clockCtrl.pll1.addClock2(frequency)
    }
    def getClockPin(index: Int) = index match {
      case 0 => clockCtrl.pll0.CLKOS
      case 1 => clockCtrl.pll0.CLKOS2
      case 2 => clockCtrl.pll0.CLKOS3
      case 3 => clockCtrl.pll1.CLKOS
      case 4 => clockCtrl.pll1.CLKOS2
      case 5 => clockCtrl.pll1.CLKOS3
    }

    for ((clock, index) <- clocks.zipWithIndex) {
      val (domainIndex, domain) = parameter.getDomainByName(clock)
      io.clocks(domainIndex) := addClock(index, domain.frequency)
      generatedClocks = generatedClocks :+ getClockPin(index)
    }
    clockCtrl.pll0.calculate(clkIDiv, clkFbDiv, clkOpDiv)
    clockCtrl.pll1.calculate(clkIDiv, clkFbDiv, clkOpDiv)
  }

  case class XilinxPllController(
      parameter: Parameter,
      inputClock: ClockParameter,
      clocks: List[String],
      multiply: Int
  ) extends ClockControllerBase(parameter) {
    val clockFrequency = inputClock.frequency

    val clockCtrlClockDomain = ClockDomain(
      clock = io.mainClock,
      frequency = FixedFrequency(clockFrequency),
      config = ClockDomainConfig(
        resetKind = BOOT
      )
    )

    val clockCtrl = new ClockingArea(clockCtrlClockDomain) {
      val pll = XilinxPLL.PLLE2_BASE(CLKFBOUT_MULT = multiply).connect()
    }

    def addClock(index: Int, frequency: HertzNumber) = index match {
      case 0 => clockCtrl.pll.addClock0(frequency)
      case 1 => clockCtrl.pll.addClock1(frequency)
      case 2 => clockCtrl.pll.addClock2(frequency)
      case 3 => clockCtrl.pll.addClock3(frequency)
      case 4 => clockCtrl.pll.addClock4(frequency)
      case 5 => clockCtrl.pll.addClock5(frequency)
    }
    def getClockPin(index: Int) = index match {
      case 0 => clockCtrl.pll.CLKOUT0
      case 1 => clockCtrl.pll.CLKOUT1
      case 2 => clockCtrl.pll.CLKOUT2
      case 3 => clockCtrl.pll.CLKOUT3
      case 4 => clockCtrl.pll.CLKOUT4
      case 5 => clockCtrl.pll.CLKOUT5
    }

    for ((clock, index) <- clocks.zipWithIndex) {
      val (domainIndex, domain) = parameter.getDomainByName(clock)
      io.clocks(domainIndex) := addClock(index, domain.frequency)
      generatedClocks = generatedClocks :+ getClockPin(index)
    }
  }
}
