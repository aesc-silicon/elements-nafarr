// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.sim

import org.scalatest.funsuite.AnyFunSuite

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import nafarr.memory.hyperbus.{HyperBus, HyperBusCtrl}
import nafarr.memory.hyperbus.phy.HyperBusGenericDdrPhy

/** End-to-end round-trip for the full-rate DDR PHY + DDR device model. */
class HyperBusDdrRoundTripTest extends AnyFunSuite {

  case class DdrDut(p: HyperBusCtrl.Parameter) extends Component {
    val io = new Bundle {
      val controller = slave(Stream(HyperBus.ControllerInterface(p)))
      val frontend = master(Stream(HyperBus.FrontendInterface(p)))
      val latencyCycles = in(UInt(log2Up(6) bits))
    }
    val ctrl = HyperBusCtrl(p)
    val phy = HyperBusGenericDdrPhy(p)
    val model = W956A8MBYA.W956A8MBYADdr()

    ctrl.io.controller <> io.controller
    ctrl.io.frontend <> io.frontend
    ctrl.io.phy <> phy.io.phy

    ctrl.io.config.phy.reset.trigger := False
    ctrl.io.config.phy.reset.pulse := 0
    ctrl.io.config.phy.reset.halt := 0
    ctrl.io.config.latencyCycles := io.latencyCycles
    ctrl.io.config.cmd.valid := False
    ctrl.io.config.cmd.payload := 0
    ctrl.io.config.rsp.ready := False

    model.io.clock := ClockDomain.current.readClockWire
    model.io.ck := phy.io.hyperbus.ck
    model.io.ckN := !phy.io.hyperbus.ck
    model.io.csN := phy.io.hyperbus.cs(0)
    model.io.resetN := phy.io.hyperbus.reset
    model.io.dqIn := phy.io.hyperbus.dq.write
    phy.io.hyperbus.dq.read := model.io.dqOut
    model.io.rwdsIn := phy.io.hyperbus.rwds.write
    phy.io.hyperbus.rwds.read := model.io.rwdsOut
    model.io.dqIn.addTag(crossClockDomain)
    phy.io.hyperbus.dq.read.addTag(crossClockDomain)
    model.io.rwdsIn.addTag(crossClockDomain)
    phy.io.hyperbus.rwds.read.addTag(crossClockDomain)

  }

  val p = HyperBusCtrl.Parameter.default(List((BigInt(0x800000L), true)))
  lazy val compiled = SimConfig.withWave
    .withConfig(SpinalConfig(defaultClockDomainFrequency = FixedFrequency(100 MHz)))
    .addSimulatorFlag("--x-initial 0")
    .compile(DdrDut(p))

  def initSim(dut: DdrDut): Unit = {
    dut.clockDomain.forkStimulus(10)
    dut.io.controller.valid #= false
    dut.io.frontend.ready #= false
    dut.io.latencyCycles #= 7
    dut.clockDomain.waitSampling(50)
  }

  def access(
      dut: DdrDut,
      read: Boolean,
      addr: BigInt,
      data: BigInt,
      strobe: BigInt
  ): Option[BigInt] = {
    val cd = dut.clockDomain
    dut.io.controller.valid #= true
    dut.io.controller.payload.id #= 0
    dut.io.controller.payload.read #= read
    dut.io.controller.payload.memory #= true
    dut.io.controller.payload.unaligned #= false
    dut.io.controller.payload.addr #= addr
    dut.io.controller.payload.data #= data
    dut.io.controller.payload.strobe #= strobe
    dut.io.controller.payload.last #= true
    var t = 0
    while (!dut.io.controller.ready.toBoolean && t < 8000) { cd.waitSampling(); t += 1 }
    dut.io.controller.valid #= false
    if (t >= 8000) return None
    dut.io.frontend.ready #= true
    t = 0
    while (!dut.io.frontend.valid.toBoolean && t < 8000) { cd.waitSampling(); t += 1 }
    if (t >= 8000) { dut.io.frontend.ready #= false; return None }
    val r = dut.io.frontend.payload.data.toBigInt
    cd.waitSampling()
    dut.io.frontend.ready #= false
    Some(r)
  }

  def expand(strobe: Int): BigInt =
    (0 until 4).foldLeft(BigInt(0)) { (m, i) =>
      if ((strobe & (1 << i)) != 0) m | (BigInt(0xff) << (i * 8)) else m
    }

  def roundTrip(dut: DdrDut, strobe: Int): Unit = {
    initSim(dut)
    val addr = BigInt(0x100)
    val bg = BigInt("11223344", 16)
    val nw = BigInt("aabbccdd", 16)
    val mask32 = BigInt("ffffffff", 16)
    assert(access(dut, read = false, addr, bg, 0xf).isDefined, "background write hung")
    dut.clockDomain.waitSampling(20)
    assert(access(dut, read = false, addr, nw, strobe).isDefined, "partial write hung")
    dut.clockDomain.waitSampling(20)
    val rd = access(dut, read = true, addr, 0, 0xf)
    assert(rd.isDefined, "read-back hung")
    val mask = expand(strobe)
    val expected = ((bg & (mask32 ^ mask)) | (nw & mask)) & mask32
    println(f"[ddr strobe 0x$strobe%x] read 0x${rd.get}%08x expected 0x$expected%08x")
    assert(rd.get == expected, f"strobe 0x$strobe%x: read 0x${rd.get}%08x != 0x$expected%08x")
  }

  test("ddr word 1111 (reference)") { compiled.doSim("ddr_1111") { d => roundTrip(d, 0xf) } }
  test("ddr byte0 0001") { compiled.doSim("ddr_0001") { d => roundTrip(d, 0x1) } }
  test("ddr byte3 1000") { compiled.doSim("ddr_1000") { d => roundTrip(d, 0x8) } }
  test("ddr halfword 0011") { compiled.doSim("ddr_0011") { d => roundTrip(d, 0x3) } }
  test("ddr noncontiguous 1010") { compiled.doSim("ddr_1010") { d => roundTrip(d, 0xa) } }

  def burst(dut: DdrDut, read: Boolean, base: BigInt, datas: Seq[BigInt]): Seq[BigInt] = {
    val cd = dut.clockDomain
    val n = datas.size
    val results = scala.collection.mutable.ArrayBuffer[BigInt]()
    dut.io.frontend.ready #= true
    val collector = fork {
      var guard = 0
      while (results.size < n && guard < 60000) {
        if (dut.io.frontend.valid.toBoolean) results += dut.io.frontend.payload.data.toBigInt
        cd.waitSampling(); guard += 1
      }
    }
    for ((d, i) <- datas.zipWithIndex) {
      dut.io.controller.valid #= true
      dut.io.controller.payload.id #= 0
      dut.io.controller.payload.read #= read
      dut.io.controller.payload.memory #= true
      dut.io.controller.payload.unaligned #= false
      dut.io.controller.payload.addr #= base + i * 4
      dut.io.controller.payload.data #= d
      dut.io.controller.payload.strobe #= 0xf
      dut.io.controller.payload.last #= (i == n - 1)
      var t = 0
      do { cd.waitSampling(); t += 1 } while (!dut.io.controller.ready.toBoolean && t < 20000)
    }
    dut.io.controller.valid #= false
    collector.join()
    dut.io.frontend.ready #= false
    assert(results.size == n, s"got ${results.size} of $n responses (read=$read)")
    results.toSeq
  }

  test("ddr burst 4 words round-trip") {
    compiled.doSim("ddr_burst4") { dut =>
      initSim(dut)
      val base = BigInt(0x200)
      val words = Seq("deadbeef", "cafebabe", "12345678", "0badf00d").map(BigInt(_, 16))
      burst(dut, read = false, base, words)
      dut.clockDomain.waitSampling(20)
      val rd = burst(dut, read = true, base, Seq.fill(words.size)(BigInt(0)))
      for (i <- words.indices) {
        println(f"[ddr burst $i] read 0x${rd(i)}%08x expected 0x${words(i)}%08x")
        assert(rd(i) == words(i), f"ddr burst word $i: 0x${rd(i)}%08x != 0x${words(i)}%08x")
      }
    }
  }
}
