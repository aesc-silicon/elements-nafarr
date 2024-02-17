package nafarr.memory.hyperbus.phy

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import nafarr.memory.hyperbus.{HyperBus, HyperBusCtrl}

class HyperBusGenericPhyTest extends AnyFunSuite {

  def setSignalDefaults(dut: HyperBusGenericPhy.Phy) {
    dut.io.phy.cmd.valid #= false
    dut.io.phy.cmd.args #= BigInt(0)
    dut.io.phy.rsp.ready #= false
    dut.io.hyperbus.dq.read #= BigInt(0)
    dut.io.hyperbus.rwds.read #= false
    dut.io.phy.config.reset.pulse #= BigInt(20)
    dut.io.phy.config.reset.halt #= BigInt(20)
    dut.io.phy.config.reset.trigger #= false
    dut.clockDomain.waitSampling(25)

    assert(dut.io.hyperbus.reset.toBoolean == true)

    dut.io.phy.config.reset.pulse #= BigInt(20)
    dut.io.phy.config.reset.halt #= BigInt(20)
    dut.io.phy.config.reset.trigger #= false
  }

  def fillCommands(dut: HyperBusGenericPhy.Phy, read: Boolean, registerWrite: Boolean = false) {
    val fillCommandsFork = fork {
      dut.io.phy.cmd.valid #= true
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.CS
      if (read) {
        // read, 6 initial cycles, index 0
        dut.io.phy.cmd.payload.args #= BigInt("111000", 2)
      } else {
        if (registerWrite) {
          // write, 0 initial cycles, index 0
          dut.io.phy.cmd.payload.args #= BigInt("000000", 2)
        } else {
          // write, 6 initial cycles, index 0
          dut.io.phy.cmd.payload.args #= BigInt("011000", 2)
        }
      }
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.ADDR
      dut.io.phy.cmd.payload.args #= BigInt("01010101", 2)
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.ADDR
      dut.io.phy.cmd.payload.args #= BigInt("10101010", 2)
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.ADDR
      dut.io.phy.cmd.payload.args #= BigInt("01010101", 2)
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.ADDR
      dut.io.phy.cmd.payload.args #= BigInt("10101010", 2)
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.ADDR
      dut.io.phy.cmd.payload.args #= BigInt("01010101", 2)
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.ADDR
      dut.io.phy.cmd.payload.args #= BigInt("10101010", 2)
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.DATA
      if (!read) {
        // not last, mask data, data
        dut.io.phy.cmd.payload.args #= BigInt("0111001010", 2)
      } else {
        // not last, mask data, no data
        dut.io.phy.cmd.payload.args #= BigInt("0100000000", 2)
      }
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.payload.mode #= HyperBus.Phy.CmdMode.DATA
      if (!read) {
        // not last, mask data, data
        dut.io.phy.cmd.payload.args #= BigInt("1001010011", 2)
      } else {
        // last, dont mask data, no data
        dut.io.phy.cmd.payload.args #= BigInt("1000000000", 2)
      }
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.valid #= false
    }
  }

  def validateCA(dut: HyperBusGenericPhy.Phy) {
    val validateCAFork = fork {
      dut.clockDomain.waitSampling(2)
      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1111", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1110", 2))
      dut.clockDomain.waitSampling(5)
      sleep(2)
      assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("00000000", 2))
      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("01010101", 2))
      }
      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("10101010", 2))
      }
      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("01010101", 2))
      }
      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("10101010", 2))
      }
      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("01010101", 2))
      }
      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("10101010", 2))
      }
    }
  }

  def sendData(dut: HyperBusGenericPhy.Phy, latencyCycles: Int, additionalCycle: Boolean) {
    val sendDataFork = fork {
      if (additionalCycle)
        dut.io.hyperbus.rwds.read #= true
      dut.clockDomain.waitSampling(23)
      dut.io.hyperbus.rwds.read #= false
      if (additionalCycle)
        dut.clockDomain.waitSampling(latencyCycles * 8)
      dut.clockDomain.waitSampling(latencyCycles * 8 + 4)
      sleep(2)
      dut.io.hyperbus.dq.read #= BigInt("01101001", 2)
      dut.io.hyperbus.rwds.read #= true
      dut.clockDomain.waitSampling(4)
      sleep(2)
      dut.io.hyperbus.dq.read #= BigInt("10010110", 2)
      dut.io.hyperbus.rwds.read #= false
      dut.clockDomain.waitSampling(4)
      sleep(2)
      dut.io.hyperbus.rwds.read #= false
      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1110", 2))
      dut.clockDomain.waitSampling(2)
      sleep(2)
      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1111", 2))
    }
  }

  def readData(dut: HyperBusGenericPhy.Phy, latencyCycles: Int, additionalCycle: Boolean) {
    val readDataFork = fork {
      if (additionalCycle)
        dut.io.hyperbus.rwds.read #= true
      dut.clockDomain.waitSampling(23)
      dut.io.hyperbus.rwds.read #= false
      if (additionalCycle)
        dut.clockDomain.waitSampling(latencyCycles * 8)
      dut.clockDomain.waitSampling(latencyCycles * 8 + 1)
      sleep(2)
      if (latencyCycles != 1) {
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt(0))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt(0))
        assert(dut.io.hyperbus.rwds.writeEnable.toBoolean == true)
        assert(dut.io.hyperbus.rwds.write.toBoolean == false)
      }

      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("11001010", 2))
        assert(dut.io.hyperbus.rwds.writeEnable.toBoolean == true)
        assert(dut.io.hyperbus.rwds.write.toBoolean == true)
      }
      for (index <- 0 to 3) {
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
        assert(dut.io.hyperbus.dq.write.toBigInt == BigInt("01010011", 2))
        assert(dut.io.hyperbus.rwds.writeEnable.toBoolean == true)
        assert(dut.io.hyperbus.rwds.write.toBoolean == false)
      }
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1111", 2))
    }
  }

  def checkResponse(dut: HyperBusGenericPhy.Phy, write: Boolean = false) {
    val checkResponseFork = fork {
        dut.clockDomain.waitSampling(150)
        sleep(2)
        assert(dut.io.phy.rsp.valid.toBoolean == true)
        if (!write)
          assert(dut.io.phy.rsp.payload.data.toBigInt == BigInt(0))
        assert(dut.io.phy.rsp.payload.error.toBoolean == false)
        assert(dut.io.phy.rsp.payload.last.toBoolean == false)
        dut.io.phy.rsp.ready #= true
        dut.clockDomain.waitSampling(1)
        sleep(2)
        assert(dut.io.phy.rsp.valid.toBoolean == true)
        if (!write)
          assert(dut.io.phy.rsp.payload.data.toBigInt == BigInt("10010110", 2))
        assert(dut.io.phy.rsp.payload.error.toBoolean == false)
        assert(dut.io.phy.rsp.payload.last.toBoolean == true)
        dut.clockDomain.waitSampling(1)
        sleep(2)
        dut.io.phy.rsp.ready #= false
        assert(dut.io.phy.rsp.valid.toBoolean == false)
    }
  }

  test("HyperBusGenericPhy") {
    val compiled = SimConfig.withWave.compile {

      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val hyperbusPartitions = List[(BigInt, Boolean)](
          (0x800000L, true),
          (0x800000L, true),
          (0x800000L, true),
          (0x800000L, true)
        )
        val dut = HyperBusGenericPhy(HyperBusCtrl.Parameter.default(hyperbusPartitions))
      }
      area.dut
    }
    compiled.doSim("default signals") { dut =>
      dut.clockDomain.forkStimulus(10)

      dut.io.phy.cmd.valid #= false
      dut.io.phy.cmd.args #= BigInt(0)
      dut.io.phy.rsp.ready #= false
      dut.io.hyperbus.dq.read #= BigInt(0)
      dut.io.hyperbus.rwds.read #= false
      dut.clockDomain.waitSampling(5)

      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1111", 2))
      assert(dut.io.hyperbus.dq.write.toBigInt == BigInt(0))
      assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt(0))
      assert(dut.io.hyperbus.rwds.write.toBoolean == false)
      assert(dut.io.hyperbus.rwds.writeEnable.toBoolean == false)
    }

    compiled.doSim("reset") { dut =>
      dut.clockDomain.forkStimulus(10)

      dut.io.phy.cmd.valid #= false
      dut.io.phy.cmd.args #= BigInt(0)
      dut.io.phy.rsp.ready #= false
      dut.io.hyperbus.dq.read #= BigInt(0)
      dut.io.hyperbus.rwds.read #= false
      dut.io.phy.config.reset.pulse #= BigInt(20)
      dut.io.phy.config.reset.halt #= BigInt(20)
      dut.io.phy.config.reset.trigger #= false
      dut.clockDomain.waitSampling(1)
      assert(dut.io.hyperbus.reset.toBoolean == true)
      dut.io.phy.config.reset.trigger #= true
      dut.clockDomain.waitSampling(1)
      sleep(2)
      dut.io.phy.config.reset.trigger #= false
      dut.clockDomain.waitSampling(20)
      assert(dut.io.hyperbus.reset.toBoolean == false)
      dut.clockDomain.waitSampling(20)
      assert(dut.io.hyperbus.reset.toBoolean == true)

      dut.clockDomain.waitSampling(20)

      dut.io.phy.config.reset.trigger #= true
      dut.clockDomain.waitSampling(1)
      dut.io.phy.config.reset.trigger #= false
      dut.clockDomain.waitSampling(2)
      sleep(2)
      assert(dut.io.hyperbus.reset.toBoolean == false)
      dut.clockDomain.waitSampling(20)
      sleep(2)
      assert(dut.io.hyperbus.reset.toBoolean == true)

      dut.clockDomain.waitSampling(100)
    }

    compiled.doSim("read - no additional cycle") { dut =>
      dut.clockDomain.forkStimulus(10)

      setSignalDefaults(dut)

      fillCommands(dut, true)
      validateCA(dut)
      sendData(dut, 6, false)
      checkResponse(dut)

      dut.clockDomain.waitSampling(300)
    }

    compiled.doSim("read - one additional cycle") { dut =>
      dut.clockDomain.forkStimulus(10)

      setSignalDefaults(dut)

      fillCommands(dut, true)
      validateCA(dut)
      sendData(dut, 6, true)
      checkResponse(dut)

      dut.clockDomain.waitSampling(300)
    }

    compiled.doSim("write - without initial latency") { dut =>
      dut.clockDomain.forkStimulus(10)

      setSignalDefaults(dut)

      fillCommands(dut, false, true)
      validateCA(dut)
      readData(dut, 1, false)
      checkResponse(dut, true)

      dut.clockDomain.waitSampling(200)
    }

    compiled.doSim("write - no additional cycle") { dut =>
      dut.clockDomain.forkStimulus(10)

      setSignalDefaults(dut)

      fillCommands(dut, false)
      validateCA(dut)
      readData(dut, 6, false)
      checkResponse(dut, true)

      dut.clockDomain.waitSampling(200)
    }

    compiled.doSim("write - one additional cycle") { dut =>
      dut.clockDomain.forkStimulus(10)

      setSignalDefaults(dut)

      fillCommands(dut, false)
      validateCA(dut)
      readData(dut, 6, true)
      checkResponse(dut, true)

      dut.clockDomain.waitSampling(300)
    }
  }
}
