package nafarr.peripherals.com.chip2chip.phy

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._


class VirtualPhyTest extends AnyFunSuite {

  test("tx") {
    val compiled = SimConfig.withWave.compile {
      VirtualPhy.Tx()
    }
    compiled.doSim("0101011011") { dut =>
      dut.clockDomain.forkStimulus(10)

      val transmission = "0101011011"
      dut.io.fromLinkLayer.payload #= BigInt(transmission * 16, 2)
      dut.io.fromLinkLayer.valid #= false
      dut.io.phy.stall #= false
      dut.clockDomain.waitSampling(2)
      sleep(2)

      dut.io.fromLinkLayer.valid #= true
      dut.clockDomain.waitSampling(1)
      sleep(1)
      for (index <- 0 until 10) {
        dut.clockDomain.waitSampling(1)
        sleep(1)
        val bit = transmission.reverse(index).toString()
        assert(dut.io.phy.data.toBigInt == BigInt(bit * 16, 2))
        if (index == 9) {
          assert(dut.io.fromLinkLayer.ready.toBoolean == true)
        }
      }

      dut.io.fromLinkLayer.valid #= false
      dut.clockDomain.waitSampling(1)
      sleep(1)
      for (index <- 0 until 10) {
        dut.clockDomain.waitSampling(1)
        sleep(1)
        assert(dut.io.phy.data.toBigInt == BigInt("0" * 16, 2))
        if (index == 9) {
          assert(dut.io.fromLinkLayer.ready.toBoolean == false)
        }
      }

      dut.io.fromLinkLayer.valid #= true
      dut.clockDomain.waitSampling(1)
      sleep(1)
      for (index <- 0 until 10) {
        dut.clockDomain.waitSampling(1)
        sleep(1)
        val bit = transmission.reverse(index).toString()
        assert(dut.io.phy.data.toBigInt == BigInt(bit * 16, 2))
        if (index == 9) {
          assert(dut.io.fromLinkLayer.ready.toBoolean == true)
        }
      }

      dut.clockDomain.waitSampling(10)
    }
    compiled.doSim("stall") { dut =>
      dut.clockDomain.forkStimulus(10)

      val transmission = "0101011011"
      dut.io.fromLinkLayer.payload #= BigInt(transmission * 16, 2)
      dut.io.fromLinkLayer.valid #= false
      dut.io.phy.stall #= false
      dut.clockDomain.waitSampling(2)
      sleep(2)

      dut.io.fromLinkLayer.valid #= true
      dut.clockDomain.waitSampling(1)
      sleep(1)
      for (index <- 0 until 10) {
        dut.clockDomain.waitSampling(1)
        sleep(1)
        val bit = transmission.reverse(index).toString()
        assert(dut.io.phy.data.toBigInt == BigInt(bit * 16, 2))
        if (index == 9) {
          assert(dut.io.fromLinkLayer.ready.toBoolean == true)
        }
      }
      dut.io.phy.stall #= true

      dut.clockDomain.waitSampling(1)
      sleep(1)
      for (index <- 0 until 10) {
        dut.clockDomain.waitSampling(1)
        sleep(1)
        assert(dut.io.phy.data.toBigInt == BigInt("0" * 16, 2))
        if (index == 9) {
          assert(dut.io.fromLinkLayer.ready.toBoolean == false)
        }
      }

      dut.clockDomain.waitSampling(10)
    }
  }
  test("rx") {
    val compiled = SimConfig.withWave.compile {
      VirtualPhy.Rx()
    }
    compiled.doSim("0101011011") { dut =>
      dut.clockDomain.forkStimulus(10)

      dut.io.fromPhy.ready #= false
      dut.io.phy.data #= BigInt("FFFF", 16)
      dut.io.phy.enable #= false

      dut.clockDomain.waitSampling(2)

      val transmission = "0101011011"
      for (index <- 0 until 10) {
        dut.io.phy.enable #= true
        val bit = transmission(index).toString()
        dut.io.phy.data #= BigInt(bit * 16, 2)
        dut.clockDomain.waitSampling(1)
        sleep(1)
        assert(dut.io.phy.stall.toBoolean == true)
      }
      dut.io.phy.enable #= false
      assert(dut.io.fromPhy.valid.toBoolean == true)
      assert(dut.io.fromPhy.payload.toBigInt == BigInt(transmission.reverse * 16, 2))

      dut.clockDomain.waitSampling(2)
      sleep(1)

      dut.io.fromPhy.ready #= true
      assert(dut.io.fromPhy.valid.toBoolean == true)
      assert(dut.io.phy.stall.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      sleep(1)
      dut.io.fromPhy.ready #= false
      assert(dut.io.fromPhy.valid.toBoolean == false)
      assert(dut.io.phy.stall.toBoolean == false)

      dut.clockDomain.waitSampling(10)
    }
  }
}
