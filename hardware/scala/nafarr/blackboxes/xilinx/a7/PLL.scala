// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.blackboxes.xilinx.a7

import spinal.core._
import spinal.core.sim._

object XilinxPLL {
  case class PLLE2_BASE(
      DIVCLK_DIVIDE: Int = 1,
      CLKFBOUT_MULT: Int = 5,
      CLKFBOUT_PHASE: Double = 0.0
  ) extends BlackBox {
    require(DIVCLK_DIVIDE >= 0, "DIVCLK_DIVIDE must be at least 0.")
    require(DIVCLK_DIVIDE <= 57, "DIVCLK_DIVIDE must be at most 56.")
    require(CLKFBOUT_MULT >= 2, "CLKFBOUT_MULT must be at least 2.")
    require(CLKFBOUT_MULT <= 64, "CLKFBOUT_MULT must be at most 64.")
    require(CLKFBOUT_PHASE >= 0.0, "CLKFBOUT_PHASE must be at least 0.0 degree.")
    require(CLKFBOUT_PHASE <= 360.0, "CLKFBOUT_PHASE must be at most 360.0 degree.")

    val CLKIN1 = in(Bool())
    val RST = in(Bool())
    val PWRDWN = in(Bool())

    val CLKOUT0 = out(Bool())
    val CLKOUT1 = out(Bool())
    val CLKOUT2 = out(Bool())
    val CLKOUT3 = out(Bool())
    val CLKOUT4 = out(Bool())
    val CLKOUT5 = out(Bool())
    val LOCKED = out(Bool())

    val CLKFBOUT = out(Bool())
    val CLKFBIN = in(Bool())

    val designClockDomain = ClockDomain.current
    mapCurrentClockDomain(CLKIN1)
    val multipliedFrequency = designClockDomain.frequency.getValue * CLKFBOUT_MULT

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/PLL.v")

    addGeneric("CLKIN1_PERIOD", (1000000000 / designClockDomain.frequency.getValue.toInt))
    addGeneric("DIVCLK_DIVIDE", DIVCLK_DIVIDE)
    addGeneric("CLKFBOUT_MULT", CLKFBOUT_MULT)
    addGeneric("CLKFBOUT_PHASE", CLKFBOUT_PHASE)

    def connect() = {
      if (this.designClockDomain.hasResetSignal) {
        RST := this.designClockDomain.reset
      } else {
        val reset = False
        val resetCounter = Reg(UInt(4 bits)).init(0)
        when(resetCounter =/= U(resetCounter.range -> true)) {
          resetCounter := resetCounter + 1
          reset := True
        }
        RST := reset
      }

      this.PWRDWN := False
      this.CLKFBIN := this.CLKFBOUT
      this
    }

    /* Formula to calculate the clock:
     *
     * CLKOUTn (MHz) = CLKIN1 (MHz) * CLKFBOUT_MULT (int)
     *                 ----------------------
     *                 DIVCLKn_DIVIDE (ns)
     *
     * Formula to calculate the divide value:
     *
     * DIVCLKn_DIVIDE (ns) = CLKIN1 (MHz) * CLKFBOUT_MULT (int)
     *                       ----------------------
     *                       CLKOUTn (MHz)
     */
    def addClock(
        clock: Bool,
        number: Int,
        divide: Int,
        phase: Double = 0.0,
        dutyCycle: Double = 0.5
    ) = {
      require(number >= 0, "PLL clock output must be at least 0.")
      require(number <= 5, "PLL clock output must be at most 5.")
      require(divide >= 1, "Divide must be at least 1.")
      require(divide <= 128, "Divide must be at most 128.")
      require(phase >= -360.0, "Phase must be at least -360.0 degree.")
      require(phase <= 360.0, "Phase must be at most 360.0 degree.")
      require(dutyCycle >= 0.01, "Duty cycle must be at least 0.01.")
      require(dutyCycle <= 0.99, "Duty cycle must be at most 0.99.")

      addGeneric("CLKOUT%d_DIVIDE".format(number), divide)
      addGeneric("CLKOUT%d_PHASE".format(number), phase)
      addGeneric("CLKOUT%d_DUTY_CYCLE".format(number), dutyCycle)
    }

    def addClock0(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT0, 0, divide, phase, dutyCycle)
      this.CLKOUT0
    }

    def addClock1(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT1, 1, divide, phase, dutyCycle)
      this.CLKOUT1
    }

    def addClock2(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT2, 2, divide, phase, dutyCycle)
      this.CLKOUT2
    }

    def addClock3(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT3, 3, divide, phase, dutyCycle)
      this.CLKOUT3
    }

    def addClock4(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT4, 4, divide, phase, dutyCycle)
      this.CLKOUT4
    }

    def addClock5(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT5, 5, divide, phase, dutyCycle)
      this.CLKOUT5
    }
  }

  case class PLLE2_ADV(
      DIVCLK_DIVIDE: Int = 1,
      CLKFBOUT_MULT: Int = 5,
      CLKFBOUT_PHASE: Double = 0.0
  ) extends BlackBox {
    require(DIVCLK_DIVIDE >= 0, "DIVCLK_DIVIDE must be at least 0.")
    require(DIVCLK_DIVIDE <= 57, "DIVCLK_DIVIDE must be at most 56.")
    require(CLKFBOUT_MULT >= 2, "CLKFBOUT_MULT must be at least 2.")
    require(CLKFBOUT_MULT <= 64, "CLKFBOUT_MULT must be at most 64.")
    require(CLKFBOUT_PHASE >= 0.0, "CLKFBOUT_PHASE must be at least 0.0 degree.")
    require(CLKFBOUT_PHASE <= 360.0, "CLKFBOUT_PHASE must be at most 360.0 degree.")

    val CLKFBIN = in(Bool())
    val CLKIN1 = in(Bool())
    val CLKIN2 = in(Bool())
    val CLKINSEL = in(Bool())

    val CLKFBOUT = out(Bool())
    val CLKOUT0 = out(Bool())
    val CLKOUT1 = out(Bool())
    val CLKOUT2 = out(Bool())
    val CLKOUT3 = out(Bool())
    val CLKOUT4 = out(Bool())
    val CLKOUT5 = out(Bool())

    val PWRDWN = in(Bool())
    val RST = in(Bool())
    val LOCKED = out(Bool())

    val DCLK = in(Bool())
    val DEN = in(Bool())
    val DWE = in(Bool())
    val DRDY = out(Bool())
    val DADDR = in(Bits(7 bits))
    val DI = in(Bits(16 bits))
    val DO = out(Bits(16 bits))

    val designClockDomain = ClockDomain.current
    mapCurrentClockDomain(CLKIN1)
    val multipliedFrequency = designClockDomain.frequency.getValue * CLKFBOUT_MULT

    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/xilinx/a7/PLL.v")

    addGeneric("BANDWIDTH", "HIGH")
    addGeneric("COMPENSATION", "EXTERNAL")

    addGeneric("CLKIN1_PERIOD", (1000000000 / designClockDomain.frequency.getValue.toInt))
    addGeneric("DIVCLK_DIVIDE", DIVCLK_DIVIDE)
    addGeneric("CLKFBOUT_MULT", CLKFBOUT_MULT)
    addGeneric("CLKFBOUT_PHASE", CLKFBOUT_PHASE)

    def connect() = {
      if (this.designClockDomain.hasResetSignal) {
        RST := this.designClockDomain.reset
      } else {
        val reset = False
        val resetCounter = Reg(UInt(4 bits)).init(0)
        when(resetCounter =/= U(resetCounter.range -> true)) {
          resetCounter := resetCounter + 1
          reset := True
        }
        RST := reset
      }

      this.PWRDWN := False
      this.CLKFBIN := this.CLKFBOUT
      this.CLKIN2 := False
      this.CLKINSEL := True
      this.DADDR := 0
      this.DCLK := False
      this.DEN := False
      this.DI := 0
      this.DWE := False
      this
    }

    /* Formula to calculate the clock:
     *
     * CLKOUTn (MHz) = CLKIN1 (MHz) * CLKFBOUT_MULT (int)
     *                 ----------------------
     *                 DIVCLKn_DIVIDE (ns)
     *
     * Formula to calculate the divide value:
     *
     * DIVCLKn_DIVIDE (ns) = CLKIN1 (MHz) * CLKFBOUT_MULT (int)
     *                       ----------------------
     *                       CLKOUTn (MHz)
     */
    def addClock(
        clock: Bool,
        number: Int,
        divide: Int,
        phase: Double = 0.0,
        dutyCycle: Double = 0.5
    ) = {
      require(number >= 0, "PLL clock output must be at least 0.")
      require(number <= 5, "PLL clock output must be at most 5.")
      require(divide >= 1, "Divide must be at least 1.")
      require(divide <= 128, "Divide must be at most 128.")
      require(phase >= -360.0, "Phase must be at least -360.0 degree.")
      require(phase <= 360.0, "Phase must be at most 360.0 degree.")
      require(dutyCycle >= 0.01, "Duty cycle must be at least 0.01.")
      require(dutyCycle <= 0.99, "Duty cycle must be at most 0.99.")

      addGeneric("CLKOUT%d_DIVIDE".format(number), divide)
      addGeneric("CLKOUT%d_PHASE".format(number), phase)
      addGeneric("CLKOUT%d_DUTY_CYCLE".format(number), dutyCycle)
    }

    def addClock0(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT0, 0, divide, phase, dutyCycle)
      this.CLKOUT0
    }

    def addClock1(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT1, 1, divide, phase, dutyCycle)
      this.CLKOUT1
    }

    def addClock2(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT2, 2, divide, phase, dutyCycle)
      this.CLKOUT2
    }

    def addClock3(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT3, 3, divide, phase, dutyCycle)
      this.CLKOUT3
    }

    def addClock4(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT4, 4, divide, phase, dutyCycle)
      this.CLKOUT4
    }

    def addClock5(desiredFrequency: HertzNumber, phase: Double = 0.0, dutyCycle: Double = 0.5) = {
      val divide = (multipliedFrequency / desiredFrequency).toInt
      this.addClock(this.CLKOUT5, 5, divide, phase, dutyCycle)
      this.CLKOUT5
    }
  }

}
