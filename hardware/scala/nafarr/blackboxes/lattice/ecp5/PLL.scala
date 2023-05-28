package nafarr.blackboxes.lattice.ecp5

import spinal.core._
import spinal.core.sim._

object LatticePLL {
  case class EHXPLLL() extends BlackBox {

    val CLKI = in(Bool())
    val CLKFB = in(Bool())
    val CLKINTFB = out(Bool())
    val PHASESEL0 = in(Bool())
    val PHASESEL1 = in(Bool())
    val PHASEDIR = in(Bool())
    val PHASESTEP = in(Bool())
    val PHASELOADREG = in(Bool())
    val STDBY = in(Bool())
    val RST = in(Bool())
    val ENCLKOP = in(Bool())
    val ENCLKOS = in(Bool())
    val ENCLKOS2 = in(Bool())
    val ENCLKOS3 = in(Bool())
    val PLLWAKESYNC = in(Bool())
    val CLKOP = out(Bool())
    val CLKOS = out(Bool())
    val CLKOS2 = out(Bool())
    val CLKOS3 = out(Bool())
    val LOCK = out(Bool())
    val INTLOCK = out(Bool())
    val REFCLK = out(Bool())

    val designClockDomain = ClockDomain.current
    mapCurrentClockDomain(CLKI)

    var clkos1DesiredFrequency = 0 Hz
    var clkos2DesiredFrequency = 0 Hz
    var clkos3DesiredFrequency = 0 Hz

    addRTLPath(
      System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/blackboxes/lattice/ecp5/PLL.v"
    )
    addGeneric("FEEDBK_PATH", "CLKOP")
    addGeneric("INTFB_WAKE", "DISABLED")
    addGeneric("STDBY_ENABLE", "DISABLED")
    addGeneric("DPHASE_SOURCE", "DISABLED")

    addGeneric("CLKOP_ENABLE".format(name), "ENABLED")
    addGeneric("CLKOP_CPHASE".format(name), 0)
    addGeneric("CLKOP_FPHASE".format(name), 0)

    addGeneric("OUTDIVIDER_MUXA", "DIVA")
    addGeneric("OUTDIVIDER_MUXB", "DIVB")
    addGeneric("OUTDIVIDER_MUXC", "DIVC")
    addGeneric("OUTDIVIDER_MUXD", "DIVD")

    def connect() = {
      this.CLKFB := this.CLKOP
      this.STDBY := False
      this.ENCLKOP := False
      this.ENCLKOS := False
      this.ENCLKOS2 := False
      this.ENCLKOS3 := False
      this.PLLWAKESYNC := False
      this.PHASESEL0 := False
      this.PHASESEL1 := False
      this.PHASEDIR := False
      this.PHASESTEP := False
      this.PHASELOADREG := False

      if (this.designClockDomain.hasResetSignal) {
        this.RST := !this.designClockDomain.reset
      } else {
        val reset = False
        val resetCounter = Reg(UInt(4 bits)).init(0)
        when(resetCounter =/= U(resetCounter.range -> true)) {
          resetCounter := resetCounter + 1
          reset := True
        }
        this.RST := reset
      }

      this
    }

    def addClock(clock: Bool, name: String) = {
      addGeneric("%s_ENABLE".format(name), "ENABLED")
      addGeneric("%s_CPHASE".format(name), 0)
      addGeneric("%s_FPHASE".format(name), 0)
    }

    def addClock0(desiredFrequency: HertzNumber) = {
      clkos1DesiredFrequency = desiredFrequency
      this.addClock(this.CLKOS, "CLKOS")
      this.CLKOS
    }

    def addClock1(desiredFrequency: HertzNumber) = {
      clkos2DesiredFrequency = desiredFrequency
      this.addClock(this.CLKOS2, "CLKOS2")
      this.CLKOS2
    }

    def addClock2(desiredFrequency: HertzNumber) = {
      clkos3DesiredFrequency = desiredFrequency
      this.addClock(this.CLKOS3, "CLKOS3")
      this.CLKOS3
    }

    /* Formula to calculate the clock:
     *
     * CLKVCO (MHz) = CLKI (MHz) * CLKOP_DIV (int) * CLKFB_DIV (int)
     *                ----------------------------------------------
     *                CLKI_DIV (int)
     *
     * CLKVCO must meet: 400 MHZ <= CLKVCO <= 800 MHz
     *
     *
     * CLKOP (MHz) = CLKVCO (MHz)
     *               ---------------
     *               CLKOP_DIV (int)
     *
     * CLKOP is only used as feedback clock to simplify divider values.
     * CLKOP must meet: 3.125 MHz <= CLKOP <= 400 MHz
     *
     * Formula to calculate the divide value:
     *
     * CLKOSn_DIV (int) = CLKVCO (MHz)
     *                    -------------
     *                    CLKOUTn (MHz)
     */

    private def VCO_MAX: HertzNumber = 800 MHz
    private def VCO_MIN: HertzNumber = 400 MHz

    private def CLK_OUT_MIN: HertzNumber = 3.125 MHz
    private def CLK_OUT_MAX: HertzNumber = 400 MHz

    def calculate(
        CLKI_DIV: Int = 1,
        CLKFB_DIV: Int = 1,
        CLKOP_DIV: Int = 1
    ) = {
      require(CLKI_DIV >= 1, "DIVCLK_DIVIDE must be at least 1.")
      require(CLKI_DIV <= 128, "DIVCLK_DIVIDE must be at most 128.")
      require(CLKFB_DIV >= 1, "CLKFB_DIV must be at least 1.")
      require(CLKFB_DIV <= 128, "CLKFB_DIV must be at most 128.")
      require(CLKOP_DIV >= 1, "CLKOP_DIV must be at least 1.")
      require(CLKOP_DIV <= 128, "CLKOP_DIV must be at most 128.")

      val clkVco = (designClockDomain.frequency.getValue * CLKOP_DIV * CLKFB_DIV) / CLKI_DIV

      require(clkVco <= VCO_MAX, s"Internal VCO clock is faster than ${VCO_MAX}.")
      require(clkVco >= VCO_MIN, s"Internal VCO clock is slower than ${VCO_MIN}.")

      val clkOp = clkVco / CLKOP_DIV
      require(clkOp <= CLK_OUT_MAX, s"Feedback CLKOP clock is faster than ${CLK_OUT_MAX}.")
      require(clkOp >= CLK_OUT_MIN, s"Feedback CLKOP clock is slower than ${CLK_OUT_MIN}.")

      addGeneric("CLKI_DIV", CLKI_DIV)
      addGeneric("CLKFB_DIV", CLKFB_DIV)
      addGeneric("CLKOP_DIV", CLKOP_DIV)

      if (clkos1DesiredFrequency > HertzNumber(0)) {
        require(clkos1DesiredFrequency <= CLK_OUT_MAX, s"CLKOS is faster than ${CLK_OUT_MAX}.")
        require(clkos1DesiredFrequency >= CLK_OUT_MIN, s"CLKOS is slower than ${CLK_OUT_MIN}.")
        addGeneric("CLKOS_DIV", (clkVco / clkos1DesiredFrequency).toInt)
      }
      if (clkos2DesiredFrequency > HertzNumber(0)) {
        require(clkos2DesiredFrequency <= CLK_OUT_MAX, s"CLKOS2 is faster than ${CLK_OUT_MAX}.")
        require(clkos2DesiredFrequency >= CLK_OUT_MIN, s"CLKOS2 is slower than ${CLK_OUT_MIN}.")
        addGeneric("CLKOS2_DIV", (clkVco / clkos2DesiredFrequency).toInt)
      }
      if (clkos3DesiredFrequency > HertzNumber(0)) {
        require(clkos3DesiredFrequency <= CLK_OUT_MAX, s"CLKOS3 is faster than ${CLK_OUT_MAX}.")
        require(clkos3DesiredFrequency >= CLK_OUT_MIN, s"CLKOS3 is slower than ${CLK_OUT_MIN}.")
        addGeneric("CLKOS3_DIV", (clkVco / clkos3DesiredFrequency).toInt)
      }
    }
  }
}
