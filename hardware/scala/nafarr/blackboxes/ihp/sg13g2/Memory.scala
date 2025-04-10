package nafarr.blackboxes.ihp.sg13g2

import spinal.core._

object Memory {

  case class RM_IHPSG13_1P_512x32_c2_bm_bist() extends BlackBox {
    val addrWidth = 9
    val dataWidth = 32

    val A_CLK = in(Bool())
    val A_MEN = in(Bool())
    val A_WEN = in(Bool())
    val A_REN = in(Bool())
    val A_ADDR = in(Bits(addrWidth bits))
    val A_DIN = in(Bits(dataWidth bits))
    val A_DLY = in(Bool())
    val A_DOUT = out(Bits(dataWidth bits))
    val A_BM = in(Bits(dataWidth bits))

    val A_BIST_CLK = in(Bool())
    val A_BIST_EN = in(Bool())
    val A_BIST_MEN = in(Bool())
    val A_BIST_WEN = in(Bool())
    val A_BIST_REN = in(Bool())
    val A_BIST_ADDR = in(Bits(addrWidth bits))
    val A_BIST_DIN = in(Bits(dataWidth bits))
    val A_BIST_BM = in(Bits(dataWidth bits))

    def connectDefaults() {
      this.A_DLY := True

      this.A_BIST_EN := False
      this.A_BIST_MEN := False
      this.A_BIST_WEN := False
      this.A_BIST_REN := False
      this.A_BIST_ADDR := 0
      this.A_BIST_DIN := 0
      this.A_BIST_BM := 0
    }

    mapCurrentClockDomain(A_CLK)
    mapCurrentClockDomain(A_BIST_CLK)

    addRTLPath(
      System.getenv(
        "NAFARR_BASE"
      ) + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/RM_IHPSG13_1P_core_behavioral_bm_bist.v"
    )
    addRTLPath(
      System.getenv(
        "NAFARR_BASE"
      ) + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/RM_IHPSG13_1P_512x32_c2_bm_bist.v"
    )
  }

  case class RM_IHPSG13_1P_1024x8_c2_bm_bist() extends BlackBox {
    val addrWidth = 10
    val dataWidth = 8

    val A_CLK = in(Bool())
    val A_MEN = in(Bool())
    val A_WEN = in(Bool())
    val A_REN = in(Bool())
    val A_ADDR = in(Bits(addrWidth bits))
    val A_DIN = in(Bits(dataWidth bits))
    val A_DLY = in(Bool())
    val A_DOUT = out(Bits(dataWidth bits))
    val A_BM = in(Bits(dataWidth bits))

    val A_BIST_CLK = in(Bool())
    val A_BIST_EN = in(Bool())
    val A_BIST_MEN = in(Bool())
    val A_BIST_WEN = in(Bool())
    val A_BIST_REN = in(Bool())
    val A_BIST_ADDR = in(Bits(addrWidth bits))
    val A_BIST_DIN = in(Bits(dataWidth bits))
    val A_BIST_BM = in(Bits(dataWidth bits))

    def connectDefaults() {
      this.A_DLY := True

      this.A_BIST_EN := False
      this.A_BIST_MEN := False
      this.A_BIST_WEN := False
      this.A_BIST_REN := False
      this.A_BIST_ADDR := 0
      this.A_BIST_DIN := 0
      this.A_BIST_BM := 0
    }

    mapCurrentClockDomain(A_CLK)
    mapCurrentClockDomain(A_BIST_CLK)

    addRTLPath(
      System.getenv(
        "NAFARR_BASE"
      ) + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/RM_IHPSG13_1P_core_behavioral_bm_bist.v"
    )
    addRTLPath(
      System.getenv(
        "NAFARR_BASE"
      ) + "/hardware/scala/nafarr/blackboxes/ihp/sg13g2/RM_IHPSG13_1P_1024x8_c2_bm_bist.v"
    )
  }
}
