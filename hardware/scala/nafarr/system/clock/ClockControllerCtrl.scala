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
  def apply(
      parameter: Parameter,
      resetParameter: ResetControllerCtrl.Parameter,
      resetCtrl: ResetControllerCtrl.ResetControllerCtrl
  ) =
    ClockControllerCtrl(parameter, resetParameter, resetCtrl)

  case class Parameter(domains: List[ClockParameter]) {
    // TODO check synchronousWith is before

    def getDomainByName(name: String): (Int, ClockParameter) = {
      val domain = domains.find(_.name.equals(name)).get
      (domains.indexOf(domain), domain)
    }
  }

  case class Io(parameter: Parameter, resetParameter: ResetControllerCtrl.Parameter)
      extends Bundle {
    val clocks = in(UInt(parameter.domains.length bits))
    val resets = in(UInt(resetParameter.domains.length bits))
  }

  case class Config(parameter: Parameter) extends Bundle {
    val enable = UInt(parameter.domains.length bits)
  }

  case class ClockControllerCtrl(
      parameter: Parameter,
      resetParameter: ResetControllerCtrl.Parameter,
      resetCtrl: ResetControllerCtrl.ResetControllerCtrl
  ) extends Component {
    val io = new Bundle {
      val clocks = out(UInt(parameter.domains.length bits))
      val buildConnection = Io(parameter, resetParameter)
      val config = in(Config(parameter))
    }
    io.clocks := io.buildConnection.clocks

    var generatedClocks = List[Bool]()
    var clockDict = Map[String, ClockDomain]()
    for ((domain, index) <- parameter.domains.zipWithIndex) {
      clockDict += domain.name -> {
        val cd = ClockDomain.internal(
          name = domain.name,
          frequency = FixedFrequency(domain.frequency),
          config = domain.resetConfig
        )
        cd.clock := io.buildConnection.clocks(index)
        if (!domain.reset.isEmpty) {
          cd.reset := io.buildConnection.resets(resetCtrl.getResetByName(domain.reset)._2)
        }
        if (!domain.synchronousWith.isEmpty()) {
          cd.setSynchronousWith(getClockDomainByName(domain.synchronousWith))
        }
        cd
      }
    }
    def getClockDomainByName(name: String): ClockDomain = clockDict.get(name).get

    def buildXilinxPll(
        clock: Bool,
        resetCtrl: ResetControllerCtrl.ResetControllerCtrl,
        clockFrequency: HertzNumber,
        clocks: List[String],
        multiply: Int
    ) {
      val clockCtrlClockDomain = ClockDomain(
        clock = clock,
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
        io.buildConnection.clocks(domainIndex) := addClock(index, domain.frequency)
        generatedClocks = generatedClocks :+ getClockPin(index)
      }
      io.buildConnection.resets <> resetCtrl.io.resets
    }

    def buildLatticeECP5Pll(
        clock: Bool,
        resetCtrl: ResetControllerCtrl.ResetControllerCtrl,
        clockFrequency: HertzNumber,
        clocks: List[String],
        clockVco: HertzNumber = 400 MHz
    ) {

      val clockPair = (clockFrequency.toDouble / 1e6, clockVco.toDouble / 1e6)

      val (clkIDiv, clkFbDiv, clkOpDiv) = clockPair match {
        case (100, 400) => (2, 1, 8)
      }

      val clockCtrlClockDomain = ClockDomain(
        clock = clock,
        frequency = FixedFrequency(clockFrequency),
        config = ClockDomainConfig(
          resetKind = BOOT
        )
      )

      val clockCtrl = new ClockingArea(clockCtrlClockDomain) {
        val pll = LatticePLL.EHXPLLL().connect()
      }

      def addClock(index: Int, frequency: HertzNumber) = index match {
        case 0 => clockCtrl.pll.addClock0(frequency)
        case 1 => clockCtrl.pll.addClock1(frequency)
        case 2 => clockCtrl.pll.addClock2(frequency)
      }
      def getClockPin(index: Int) = index match {
        case 0 => clockCtrl.pll.CLKOS
        case 1 => clockCtrl.pll.CLKOS2
        case 2 => clockCtrl.pll.CLKOS3
      }

      for ((clock, index) <- clocks.zipWithIndex) {
        val (domainIndex, domain) = parameter.getDomainByName(clock)
        io.buildConnection.clocks(domainIndex) := addClock(index, domain.frequency)
        generatedClocks = generatedClocks :+ getClockPin(index)
      }
      io.buildConnection.resets <> resetCtrl.io.resets
      clockCtrl.pll.calculate(clkIDiv, clkFbDiv, clkOpDiv)
    }

    def buildDummy(clock: Bool, resetCtrl: ResetControllerCtrl.ResetControllerCtrl) {
      for (((domain), index) <- parameter.domains.zipWithIndex) {
        io.buildConnection.clocks(index) := clock
      }
      io.buildConnection.resets <> resetCtrl.io.resets
    }
  }
}
