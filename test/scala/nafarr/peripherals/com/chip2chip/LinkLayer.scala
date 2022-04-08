package nafarr.peripherals.com.chip2chip

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._


class LinkLayerTest extends AnyFunSuite {

  test("LinkLayer outgoing") {
    val compiled = SimConfig.withWave.compile {
      val dut = LinkLayer.LinkLayer(2, 2)
      dut.outgoing.outputForSim.simPublic()
      dut
    }
    compiled.doSim("Pipeline") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.fromFrontend.payload #= BigInt("0" * 32, 16)
      dut.io.fromFrontend.valid #= false

      dut.io.toPhy.ready #= false

      dut.clockDomain.waitSampling(5)

      dut.io.fromFrontend.valid #= true
      assert(dut.io.fromFrontend.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.fromFrontend.valid #= false
      dut.clockDomain.waitSampling(4)
      sleep(1)
      assert(dut.io.toPhy.valid.toBoolean == false)
      dut.clockDomain.waitSampling(1)
      sleep(1)
      dut.io.toPhy.ready #= true
      assert(dut.io.toPhy.valid.toBoolean == true)
      assert(dut.outgoing.outputForSim.toBigInt == BigInt("0101011011", 2))
      dut.clockDomain.waitSampling(1)
/*
      sleep(1)
      assert(dut.io.toPhy.valid.toBoolean == true)
      assert(dut.toPhy.outputForSim.toBigInt == BigInt("0110001011", 2))
      dut.clockDomain.waitSampling(1)
*/
      sleep(1)
      dut.io.toPhy.ready #= false
      assert(dut.io.toPhy.valid.toBoolean == false)

      dut.clockDomain.waitSampling(10)
    }
  }
  test("LinkLayer incoming") {
    val compiled = SimConfig.withWave.compile {
      val dut = LinkLayer.LinkLayer(2, 5)
      dut
    }
    compiled.doSim("Pipeline") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.toLinkLayer.payload #= BigInt("0101011011" * 16, 2) // 0x0A
      dut.io.toLinkLayer.valid #= false
      dut.io.toFrontend.ready #= false

      dut.clockDomain.waitSampling(5)
      sleep(1)
      dut.io.toLinkLayer.valid #= true
      dut.clockDomain.waitSampling(4)
      sleep(1)
      assert(dut.io.toFrontend.valid.toBoolean == false)
      dut.clockDomain.waitSampling(1)
      sleep(1)
      assert(dut.io.toFrontend.valid.toBoolean == true)
      dut.io.toFrontend.ready #= true
      dut.clockDomain.waitSampling(4)
      sleep(1)
      dut.io.toFrontend.ready #= false
      dut.clockDomain.waitSampling(3)
      sleep(1)
      dut.io.toFrontend.ready #= true

      dut.clockDomain.waitSampling(10)
    }
  }
}
