// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._

class HyperBusCtrlTest extends AnyFunSuite {

  def setSignalDefaults(dut: HyperBusCtrl.HyperBusCtrl) {
    dut.io.phy.cmd.ready #= false
    dut.io.phy.rdata.valid #= false
    dut.io.phy.rdata.payload.data #= BigInt(0)
    dut.io.phy.rdata.payload.last #= false
    dut.io.phy.rdata.payload.error #= false
    dut.io.phy.rdata.payload.aborted #= false
    dut.io.frontend.ready #= false
    dut.io.controller.valid #= false
    dut.io.config.latencyCycles #= BigInt(6)
    dut.io.config.cmd.valid #= false
    dut.io.config.rsp.ready #= false
  }

  // [1,0,3,2] byte lane order, matching the controller (self-inverse).
  def reorder(w: BigInt): BigInt = {
    val b0 = w & 0xff
    val b1 = (w >> 8) & 0xff
    val b2 = (w >> 16) & 0xff
    val b3 = (w >> 24) & 0xff
    (b2 << 24) | (b3 << 16) | (b0 << 8) | b1
  }

  // START field decode. SpinalHDL asBits packs the first-declared field at the
  // LSB: index(2) | latency(3) | burstLen(5) | read(1) | memory(1) (4 devices).
  def startIndex(args: BigInt): BigInt = args & 0x3
  def startRead(args: BigInt): Boolean = ((args >> 10) & 1) == 1
  def startMemory(args: BigInt): Boolean = ((args >> 11) & 1) == 1

  def fakeRegister(dut: HyperBusCtrl.HyperBusCtrl, read: Boolean = true) {
    fork {
      if (read) {
        dut.io.config.cmd.payload #= BigInt("1000100000000001", 2)
      } else {
        dut.io.config.cmd.payload #= BigInt("CAFE0801", 16)
      }
      dut.io.config.cmd.valid #= true
      dut.clockDomain.waitSamplingWhere(dut.io.config.cmd.ready.toBoolean)
      dut.io.config.cmd.valid #= false
    }
  }

  def fakeFrontend(dut: HyperBusCtrl.HyperBusCtrl, read: Boolean = true, address: BigInt = BigInt(104)) {
    fork {
      dut.io.controller.payload.id #= BigInt(13)
      dut.io.controller.payload.unaligned #= false
      dut.io.controller.payload.addr #= address
      dut.io.controller.payload.data #= BigInt("CAFEBABE", 16)
      dut.io.controller.payload.strobe #= BigInt("1111", 2)
      dut.io.controller.payload.read #= read
      dut.io.controller.payload.memory #= true
      dut.io.controller.payload.last #= true
      dut.io.controller.valid #= true
      dut.clockDomain.waitSamplingWhere(dut.io.controller.ready.toBoolean)
      dut.io.controller.valid #= false
    }
  }

  // Free-running PHY: accept every command, then stream `words` as read data.
  def phyResponder(dut: HyperBusCtrl.HyperBusCtrl, words: Seq[BigInt] = Seq()) {
    fork {
      dut.io.phy.cmd.ready #= true
    }
    fork {
      for ((w, i) <- words.zipWithIndex) {
        dut.io.phy.rdata.payload.data #= w
        dut.io.phy.rdata.payload.last #= (i == words.length - 1)
        dut.io.phy.rdata.payload.error #= false
        dut.io.phy.rdata.payload.aborted #= false
        dut.io.phy.rdata.valid #= true
        dut.clockDomain.waitSamplingWhere(dut.io.phy.rdata.ready.toBoolean)
      }
      dut.io.phy.rdata.valid #= false
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
      assert(dut.io.phy.rdata.ready.toBoolean == false)
      assert(dut.io.frontend.valid.toBoolean == false)
      assert(dut.io.controller.ready.toBoolean == false)
      assert(dut.io.config.cmd.ready.toBoolean == false)
      assert(dut.io.config.rsp.valid.toBoolean == false)
    }

    compiled.doSim("register read") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      fakeRegister(dut, true)
      phyResponder(dut, Seq(BigInt("00006355", 16)))

      dut.io.config.rsp.ready #= true
      dut.clockDomain.waitSamplingWhere(dut.io.config.rsp.valid.toBoolean)
      assert(dut.io.config.rsp.payload.toBigInt == BigInt("0101010101100011", 2))

      dut.clockDomain.waitSampling(50)
    }

    compiled.doSim("register write") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      fakeRegister(dut, false)
      phyResponder(dut, Seq(BigInt(0)))

      dut.io.config.rsp.ready #= true
      dut.clockDomain.waitSamplingWhere(dut.io.config.rsp.valid.toBoolean)
      assert(dut.io.config.rsp.payload.toBigInt == BigInt(0))

      dut.clockDomain.waitSampling(50)
    }

    compiled.doSim("memory read") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      val word = BigInt("11223344", 16)
      fakeFrontend(dut, true)
      phyResponder(dut, Seq(word))

      dut.io.frontend.ready #= true
      dut.clockDomain.waitSamplingWhere(dut.io.frontend.valid.toBoolean)
      assert(dut.io.frontend.payload.data.toBigInt == reorder(word))
      assert(dut.io.frontend.payload.read.toBoolean == true)
      assert(dut.io.frontend.payload.last.toBoolean == true)

      dut.clockDomain.waitSampling(50)
    }

    compiled.doSim("memory write") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      fakeFrontend(dut, false)
      phyResponder(dut, Seq(BigInt(0)))

      dut.io.frontend.ready #= true
      dut.clockDomain.waitSamplingWhere(dut.io.frontend.valid.toBoolean)
      assert(dut.io.frontend.payload.data.toBigInt == BigInt(0))
      assert(dut.io.frontend.payload.read.toBoolean == false)
      assert(dut.io.frontend.payload.last.toBoolean == true)

      dut.clockDomain.waitSampling(50)
    }
  }

  def fakeFrontendBurst(dut: HyperBusCtrl.HyperBusCtrl, nwords: Int, base: BigInt) {
    fork {
      for (i <- 0 until nwords) {
        dut.io.controller.payload.id #= BigInt(13)
        dut.io.controller.payload.unaligned #= false
        dut.io.controller.payload.addr #= base + i * 4
        dut.io.controller.payload.data #= BigInt(0)
        dut.io.controller.payload.strobe #= BigInt("1111", 2)
        dut.io.controller.payload.read #= true
        dut.io.controller.payload.memory #= true
        dut.io.controller.payload.last #= (i == nwords - 1)
        dut.io.controller.valid #= true
        dut.clockDomain.waitSamplingWhere(dut.io.controller.ready.toBoolean)
      }
      dut.io.controller.valid #= false
    }
  }

  test("HyperBusCtrl-BurstRead") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val hyperbusPartitions = List[(BigInt, Boolean)]((0x800000L, true))
        val dut = HyperBusCtrl(HyperBusCtrl.Parameter.default(hyperbusPartitions))
      }
      area.dut
    }
    // Backpressured drain (models the cross-domain FifoCc / D$ draining at half
    // rate): the frontend consumer stalls between beats, forcing the response
    // path to buffer a full burst.
    compiled.doSim("16-word burst read, backpressured") { dut =>
      dut.clockDomain.forkStimulus(10)
      setSignalDefaults(dut)
      dut.clockDomain.waitSampling(5)

      val nwords = 16
      val words = (0 until nwords).map(w => BigInt(0x11223340L + w * 4))
      val got = scala.collection.mutable.ArrayBuffer[BigInt]()
      fork {
        while (got.length < nwords) {
          dut.io.frontend.ready #= true
          dut.clockDomain.waitSamplingWhere(dut.io.frontend.valid.toBoolean)
          got += dut.io.frontend.payload.data.toBigInt
          dut.io.frontend.ready #= false
          dut.clockDomain.waitSampling(5)
        }
      }
      fakeFrontendBurst(dut, nwords, BigInt(0))
      phyResponder(dut, words)

      dut.clockDomain.waitSampling(600)
      val expected = words.map(reorder)
      assert(got.length == nwords, s"expected $nwords responses, got ${got.length}")
      assert(
        got.toSeq == expected,
        s"scatter:\n  got=${got.map(w => f"$w%08x").mkString(" ")}\n  exp=${expected.map(w => f"$w%08x").mkString(" ")}"
      )
    }
  }

  test("HyperBusCtrl-Partitions") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val hyperbusPartitions = List[(BigInt, Boolean)](
          (0x800000L, true),
          (0x800000L, true),
          (0x800000L, true),
          (0x800000L, true)
        )
        val dut = HyperBusCtrl(HyperBusCtrl.Parameter.default(hyperbusPartitions))
      }
      area.dut
    }

    def checkPartition(name: String, address: BigInt, index: Int) {
      compiled.doSim(name) { dut =>
        dut.clockDomain.forkStimulus(10)
        setSignalDefaults(dut)
        dut.clockDomain.waitSampling(5)

        fakeFrontend(dut, true, address)
        // START is held (cmd.ready stays low) - sample its decoded fields.
        dut.clockDomain.waitSamplingWhere(dut.io.phy.cmd.valid.toBoolean)
        val args = dut.io.phy.cmd.payload.args.toBigInt
        assert(dut.io.phy.cmd.payload.mode.toBigInt == BigInt(0)) // START
        assert(startIndex(args) == BigInt(index), s"CS index for $name")
        assert(startRead(args), s"read bit for $name")
        assert(startMemory(args), s"memory bit for $name")

        dut.clockDomain.waitSampling(20)
      }
    }

    checkPartition("partition hits - CS0 low", BigInt(0), 0)
    checkPartition("partition hits - CS0 high", BigInt(0x7FFFFFL), 0)
    checkPartition("partition hits - CS1 low", BigInt(0x800000L), 1)
    checkPartition("partition hits - CS1 high", BigInt(0xFFFFFFL), 1)
    checkPartition("partition hits - CS2 low", BigInt(0x1000000L), 2)
    checkPartition("partition hits - CS2 high", BigInt(0x17FFFFFL), 2)
    checkPartition("partition hits - CS3 low", BigInt(0x1800000L), 3)
    checkPartition("partition hits - CS3 high", BigInt(0x1FFFFFFL), 3)
  }
}
