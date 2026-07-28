// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.phy

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import nafarr.memory.hyperbus.{HyperBus, HyperBusCtrl}

class HyperBusGenericPhyTest extends AnyFunSuite {

  // SpinalHDL packs the first-declared bundle field into the least significant
  // bits. CmdStart: index(2) | latency(3) | burstLen(5) | read(1) | memory(1).
  def startArgs(index: Int, latency: Int, burstLen: Int, read: Boolean, memory: Boolean): BigInt = {
    var v = BigInt(index & 0x3)
    v |= BigInt(latency & 0x7) << 2
    v |= BigInt(burstLen & 0x1f) << 5
    v |= BigInt(if (read) 1 else 0) << 10
    v |= BigInt(if (memory) 1 else 0) << 11
    v
  }
  // CmdWrite: data(32) | mask(4) | last(1).
  def writeArgs(data: BigInt, mask: Int, last: Boolean): BigInt = {
    var v = data & BigInt("ffffffff", 16)
    v |= BigInt(mask & 0xf) << 32
    v |= BigInt(if (last) 1 else 0) << 36
    v
  }

  def setSignalDefaults(dut: HyperBusGenericPhy.Phy) {
    dut.io.phy.cmd.valid #= false
    dut.io.phy.cmd.args #= BigInt(0)
    dut.io.phy.rdata.ready #= false
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

  // Push one command word into the PHY, waiting for the ready handshake.
  def pushCmd(dut: HyperBusGenericPhy.Phy, mode: SpinalEnumElement[HyperBus.Phy.CmdMode.type], args: BigInt) {
    dut.io.phy.cmd.valid #= true
    dut.io.phy.cmd.payload.mode #= mode
    dut.io.phy.cmd.payload.args #= args
    dut.clockDomain.waitSamplingWhere(dut.io.phy.cmd.ready.toBoolean)
    dut.io.phy.cmd.valid #= false
  }

  // CA byte pattern 0x55AA55AA55AA -> the six bytes sliced out MSB-first.
  val caPattern = BigInt("55AA55AA55AA", 16)

  def fillReadCommands(dut: HyperBusGenericPhy.Phy) {
    fork {
      pushCmd(dut, HyperBus.Phy.CmdMode.START, startArgs(0, 6, 0, read = true, memory = true))
      pushCmd(dut, HyperBus.Phy.CmdMode.CA, caPattern)
    }
  }

  def fillWriteCommands(dut: HyperBusGenericPhy.Phy, data: BigInt, mask: Int) {
    fork {
      pushCmd(dut, HyperBus.Phy.CmdMode.START, startArgs(0, 6, 0, read = false, memory = true))
      pushCmd(dut, HyperBus.Phy.CmdMode.CA, caPattern)
      pushCmd(dut, HyperBus.Phy.CmdMode.WRITE, writeArgs(data, mask, last = true))
    }
  }

  // The CA output timing is unchanged from the byte-level PHY: six bytes, four
  // edge-clocks each, MSB-first (55, AA, 55, AA, 55, AA).
  def validateCA(dut: HyperBusGenericPhy.Phy) {
    fork {
      dut.clockDomain.waitSampling(2)
      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1111", 2))
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.hyperbus.cs.toBigInt == BigInt("1110", 2))
      dut.clockDomain.waitSampling(5)
      sleep(2)
      assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("00000000", 2))
      val bytes = Seq(0x55, 0xaa, 0x55, 0xaa, 0x55, 0xaa)
      for (b <- bytes) {
        for (index <- 0 to 3) {
          dut.clockDomain.waitSampling(1)
          sleep(2)
          assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
          assert(dut.io.hyperbus.dq.write.toBigInt == BigInt(b))
        }
      }
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
      dut.io.phy.rdata.ready #= false
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
      dut.io.phy.rdata.ready #= false
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

      dut.clockDomain.waitSampling(100)
    }

    compiled.doSim("write word - CA and data out") { dut =>
      dut.clockDomain.forkStimulus(10)

      setSignalDefaults(dut)

      fillWriteCommands(dut, BigInt("ddccbbaa", 16), mask = 0)
      validateCA(dut)

      val checkData = fork {
        // CA drives DQ for six bytes, then a latency gap (writeEnable low), then
        // the four data bytes go out low-byte first, four edge-clocks each;
        // mask 0 -> RWDS driven low (all bytes written).
        dut.clockDomain.waitSampling(10) // into the CA output window
        dut.clockDomain.waitSamplingWhere(dut.io.hyperbus.dq.writeEnable.toBigInt == 0)
        dut.clockDomain.waitSamplingWhere(dut.io.hyperbus.dq.writeEnable.toBigInt != 0)
        val bytes = Seq(0xaa, 0xbb, 0xcc, 0xdd)
        for (b <- bytes) {
          sleep(2)
          assert(dut.io.hyperbus.dq.writeEnable.toBigInt == BigInt("11111111", 2))
          assert(dut.io.hyperbus.dq.write.toBigInt == BigInt(b), f"write byte 0x$b%02x")
          assert(dut.io.hyperbus.rwds.writeEnable.toBoolean == true)
          assert(dut.io.hyperbus.rwds.write.toBoolean == false)
          dut.clockDomain.waitSampling(4)
        }
      }

      dut.clockDomain.waitSampling(200)
      checkData.join()
    }

    compiled.doSim("read word") { dut =>
      dut.clockDomain.forkStimulus(10)

      setSignalDefaults(dut)

      fillReadCommands(dut)

      // Emulate the device: after CS setup, CA and latency, present four data
      // bytes, one per RWDS edge (four edge-clocks apart).
      val device = fork {
        dut.clockDomain.waitSampling(2 + 1 + 5)
        dut.clockDomain.waitSampling(6 * 4)
        dut.clockDomain.waitSampling(6 * 8)
        val bytes = Seq(0x11, 0x22, 0x33, 0x44)
        var strobe = false
        for (b <- bytes) {
          dut.io.hyperbus.dq.read #= BigInt(b)
          strobe = !strobe
          dut.io.hyperbus.rwds.read #= strobe
          dut.clockDomain.waitSampling(4)
        }
      }

      val checkRsp = fork {
        dut.io.phy.rdata.ready #= true
        dut.clockDomain.waitSamplingWhere(dut.io.phy.rdata.valid.toBoolean)
        // rdata assembles the captured bytes low-byte first: 0x44332211.
        assert(dut.io.phy.rdata.payload.data.toBigInt == BigInt("44332211", 16))
        assert(dut.io.phy.rdata.payload.last.toBoolean == true)
        assert(dut.io.phy.rdata.payload.error.toBoolean == false)
      }

      dut.clockDomain.waitSampling(300)
      device.join()
      checkRsp.join()
    }
  }
}
