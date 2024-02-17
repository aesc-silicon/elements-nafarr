package nafarr.memory.hyperbus

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._

class HyperBusCtrlTest extends AnyFunSuite {

  def setSignalDefaults(dut: HyperBusCtrl.HyperBusCtrl) {
      dut.io.phy.cmd.ready #= false
      dut.io.phy.rsp.valid #= false
      dut.io.frontend.ready #= false
      dut.io.controller.valid #= false
      dut.io.config.latencyCycles #= BigInt(6)
      dut.io.config.cmd.valid #= false
      dut.io.config.rsp.ready #= false
  }

  def fakeRegister(dut: HyperBusCtrl.HyperBusCtrl, read: Boolean = true) {
    val fakeRegisterFork = fork {
      if (read) {
        dut.io.config.cmd.payload #= BigInt("1000100000000001", 2)
      } else {
        dut.io.config.cmd.payload #= BigInt("CAFE0801", 16)
      }
      dut.io.config.cmd.valid #= true
      dut.clockDomain.waitSampling(1)
      sleep(2)
      dut.io.config.cmd.valid #= false
    }
  }

  def fakeFrontend(dut: HyperBusCtrl.HyperBusCtrl, read: Boolean = true) {
    val fakeFrontendFork = fork {
      dut.io.controller.payload.id #= BigInt(13)
      dut.io.controller.payload.unaligned #= false
      dut.io.controller.payload.addr #= BigInt(104)
      dut.io.controller.payload.data #= BigInt("CAFEBABE", 16)
      dut.io.controller.payload.strobe#= BigInt("0011", 2)
      dut.io.controller.payload.read #= read
      dut.io.controller.payload.memory #= true
      dut.io.controller.payload.last #= true
      dut.io.controller.valid #= true
      dut.clockDomain.waitSampling(1)
      sleep(2)
      dut.io.controller.valid #= false
    }
  }

  def checkPhyCmd(dut: HyperBusCtrl.HyperBusCtrl, read: Boolean = true, memory: Boolean = false) {
    val checkPhyCmdFork = fork {
      dut.clockDomain.waitSampling(2)
      sleep(2)
      dut.io.phy.cmd.ready #= true
      assert(dut.io.phy.cmd.valid.toBoolean == true)
      if (read) {
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000011100", 2))
      } else {
        if (memory) {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000001100", 2))
        } else {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000000", 2))
        }
      }
      assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("00", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.phy.cmd.valid.toBoolean == true)
      if (memory) {
        if (read) {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0010100000", 2))
        } else {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000100000", 2))
        }
      } else {
        if (read) {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0011100000", 2))
        } else {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0001100000", 2))
        }
      }
      assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("01", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.phy.cmd.valid.toBoolean == true)
      assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000000", 2))
      assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("01", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.phy.cmd.valid.toBoolean == true)
      if (memory) {
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000000", 2))
      } else {
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000001", 2))
      }
      assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("01", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.phy.cmd.valid.toBoolean == true)
      if (memory) {
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000001101", 2))
      } else {
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000000", 2))
      }
      assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("01", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.phy.cmd.valid.toBoolean == true)
      assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000000", 2))
      assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("01", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.phy.cmd.valid.toBoolean == true)
      if (memory) {
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000000", 2))
      } else {
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000001", 2))
      }
      assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("01", 2))
      if (memory) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.phy.cmd.valid.toBoolean == true)
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0BA", 16))
        assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("10", 2))
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.phy.cmd.valid.toBoolean == true)
        assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("2BE", 16))
        assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("10", 2))
      } else {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.phy.cmd.valid.toBoolean == true)
        if (read) {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0000000000", 2))
        } else {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("0CA", 16))
        }
        assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("10", 2))
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.phy.cmd.valid.toBoolean == true)
        if (read) {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("1000000000", 2))
        } else {
          assert(dut.io.phy.cmd.payload.args.toBigInt == BigInt("2FE", 16))
        }
        assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt("10", 2))
      }
      dut.clockDomain.waitSampling(1)
      sleep(2)
      dut.io.phy.cmd.ready #= false
    }
  }

  def sendPhyRsp(dut: HyperBusCtrl.HyperBusCtrl, memory: Boolean = false) {
    val sendPhyRspFork = fork {
      dut.clockDomain.waitSampling(40)
      dut.io.phy.rsp.payload.data #= BigInt("55", 16)
      dut.io.phy.rsp.payload.error #= false
      dut.io.phy.rsp.payload.last #= false
      dut.io.phy.rsp.valid #= true
      sleep(2)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.phy.rsp.payload.data #= BigInt("63", 16)
      dut.io.phy.rsp.payload.error #= false
      if (memory) {
        dut.io.phy.rsp.payload.last #= false
      } else {
        dut.io.phy.rsp.payload.last #= true
      }
      dut.io.phy.rsp.valid #= true
      sleep(2)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      if (memory) {
        dut.io.phy.rsp.payload.data #= BigInt("00", 16)
        dut.io.phy.rsp.payload.error #= false
        dut.io.phy.rsp.payload.last #= false
        dut.io.phy.rsp.valid #= true
        sleep(2)
        assert(dut.io.phy.rsp.ready.toBoolean == true)
        dut.clockDomain.waitSampling(1)
        dut.io.phy.rsp.payload.data #= BigInt("00", 16)
        dut.io.phy.rsp.payload.error #= false
        dut.io.phy.rsp.payload.last #= true
        dut.io.phy.rsp.valid #= true
        sleep(2)
        assert(dut.io.phy.rsp.ready.toBoolean == true)
        dut.clockDomain.waitSampling(1)
      }
      dut.io.phy.rsp.valid #= false
      sleep(2)
      assert(dut.io.phy.rsp.ready.toBoolean == false)
    }
  }

  def checkRegisterRsp(dut: HyperBusCtrl.HyperBusCtrl, read: Boolean = true) {
    val checkRegisterRspFork = fork {
      dut.clockDomain.waitSampling(50)
      assert(dut.io.config.rsp.valid.toBoolean == true)
      if (read) {
        assert(dut.io.config.rsp.payload.toBigInt == BigInt("0101010101100011", 2))
      } else {
        assert(dut.io.config.rsp.payload.toBigInt == BigInt(0))
      }
      dut.io.config.rsp.ready #= true
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.config.rsp.valid.toBoolean == false)
      dut.io.config.rsp.ready #= false
    }
  }

  def checkFrontendRsp(dut: HyperBusCtrl.HyperBusCtrl, read: Boolean = true) {
    val checkFrontendRspFork = fork {
      dut.clockDomain.waitSampling(50)
      assert(dut.io.frontend.valid.toBoolean == true)
      if (read) {
        assert(dut.io.frontend.payload.data.toBigInt == BigInt("5563", 16))
      } else {
        assert(dut.io.frontend.payload.data.toBigInt == BigInt(0))
      }
      assert(dut.io.frontend.payload.read.toBoolean == read)
      assert(dut.io.frontend.payload.last.toBoolean == true)
      dut.io.frontend.ready #= true
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.frontend.valid.toBoolean == false)
      dut.io.frontend.ready #= false
    }
  }

  test("HyperBusCtrl") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val hyperbusPartitions = List[(BigInt, Boolean)]((0x800000L, true))
        val dut = HyperBusCtrl(HyperBusCtrl.Parameter.default(hyperbusPartitions))
      }
      area.dut
    }
    compiled.doSim("default signals") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      assert(dut.io.phy.cmd.valid.toBoolean == false)
      assert(dut.io.phy.rsp.ready.toBoolean == false)
      assert(dut.io.frontend.valid.toBoolean == false)
      assert(dut.io.controller.ready.toBoolean == false)
      assert(dut.io.config.cmd.ready.toBoolean == false)
      assert(dut.io.config.rsp.valid.toBoolean == false)
    }
    compiled.doSim("register read") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      fakeRegister(dut)
      checkPhyCmd(dut)
      sendPhyRsp(dut)
      checkRegisterRsp(dut)

      dut.clockDomain.waitSampling(100)
    }

    compiled.doSim("register write") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      fakeRegister(dut, false)
      checkPhyCmd(dut, false)
      sendPhyRsp(dut)
      checkRegisterRsp(dut, false)

      dut.clockDomain.waitSampling(100)
    }

    compiled.doSim("memory read") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      fakeFrontend(dut)
      checkPhyCmd(dut, true, true)
      sendPhyRsp(dut, true)
      checkFrontendRsp(dut)

      dut.clockDomain.waitSampling(100)
    }

    compiled.doSim("memory write") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      fakeFrontend(dut, false)
      checkPhyCmd(dut, false, true)
      sendPhyRsp(dut, true)
      checkFrontendRsp(dut, false)

      dut.clockDomain.waitSampling(100)
    }
  }
}
