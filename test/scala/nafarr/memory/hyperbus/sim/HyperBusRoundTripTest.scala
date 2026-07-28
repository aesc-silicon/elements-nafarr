// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.sim

import org.scalatest.funsuite.AnyFunSuite

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import nafarr.memory.hyperbus.{HyperBus, HyperBusCtrl}
import nafarr.memory.hyperbus.phy.HyperBusGenericPhy

/** End-to-end round-trip oracle: HyperBusCtrl + generic PHY + the W956A8MBYA
  * device model. Writes through the controller's memory (controller) interface
  * and reads back, checking byte correctness against the real device model
  * rather than the hand-derived byte muxes.
  *
  * The word case (strobe 1111 aligned) is the trusted reference. The partial /
  * non-contiguous strobe cases characterize the byte-lane gearbox: any that go
  * red (wrong bytes) or hang (no `last`) are gearbox bugs to be fixed by #2.
  */
class HyperBusRoundTripTest extends AnyFunSuite {

  case class RoundTripDut(p: HyperBusCtrl.Parameter) extends Component {
    val io = new Bundle {
      val controller = slave(Stream(HyperBus.ControllerInterface(p)))
      val frontend = master(Stream(HyperBus.FrontendInterface(p)))
      val latencyCycles = in(UInt(log2Up(6) bits))
    }
    val ctrl = HyperBusCtrl(p)
    val phy = HyperBusGenericPhy(p)
    val model = W956A8MBYA()

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

    phy.io.hyperbus.cs.simPublic() // for the burst CS-assertion count
  }

  val p = HyperBusCtrl.Parameter.default(List((BigInt(0x800000L), true)))
  lazy val compiled = SimConfig.withWave
    .withConfig(SpinalConfig(defaultClockDomainFrequency = FixedFrequency(100 MHz)))
    .addSimulatorFlag("--x-initial 0")
    .compile(RoundTripDut(p))

  /** Drive one memory access; returns the frontend data, or None if it never
    * completes within the bound (a hang = controller never asserts `last`).
    */
  def access(
      dut: RoundTripDut,
      read: Boolean,
      addr: BigInt,
      data: BigInt,
      strobe: BigInt,
      unaligned: Boolean
  ): Option[BigInt] = {
    val cd = dut.clockDomain
    dut.io.controller.valid #= true
    dut.io.controller.payload.id #= 0
    dut.io.controller.payload.read #= read
    dut.io.controller.payload.memory #= true
    dut.io.controller.payload.unaligned #= unaligned
    dut.io.controller.payload.addr #= addr
    dut.io.controller.payload.data #= data
    dut.io.controller.payload.strobe #= strobe
    dut.io.controller.payload.last #= true
    var t = 0
    while (!dut.io.controller.ready.toBoolean && t < 4000) { cd.waitSampling(); t += 1 }
    dut.io.controller.valid #= false
    if (t >= 4000) return None
    dut.io.frontend.ready #= true
    t = 0
    while (!dut.io.frontend.valid.toBoolean && t < 4000) { cd.waitSampling(); t += 1 }
    if (t >= 4000) { dut.io.frontend.ready #= false; return None }
    val r = dut.io.frontend.payload.data.toBigInt
    cd.waitSampling()
    dut.io.frontend.ready #= false
    Some(r)
  }

  /** Per-byte mask expansion of a 4-bit strobe into a 32-bit mask. */
  def expand(strobe: Int): BigInt =
    (0 until 4).foldLeft(BigInt(0)) { (m, i) =>
      if ((strobe & (1 << i)) != 0) m | (BigInt(0xff) << (i * 8)) else m
    }

  def initSim(dut: RoundTripDut): Unit = {
    dut.clockDomain.forkStimulus(10)
    dut.io.controller.valid #= false
    dut.io.frontend.ready #= false
    dut.io.latencyCycles #= 7
    dut.clockDomain.waitSampling(50)
  }

  /** Write a background word, overwrite with a partial (strobe) word, read back,
    * and check that exactly the strobed byte lanes changed.
    */
  def roundTrip(dut: RoundTripDut, strobe: Int, unaligned: Boolean): Unit = {
    initSim(dut)
    val addr = BigInt(0x100)
    val bg = BigInt("11223344", 16)
    val nw = BigInt("aabbccdd", 16)
    val mask32 = BigInt("ffffffff", 16)

    assert(
      access(dut, read = false, addr, bg, 0xf, unaligned = false).isDefined,
      "background write hung"
    )
    dut.clockDomain.waitSampling(20)

    val wr = access(dut, read = false, addr, nw, strobe, unaligned)
    assert(wr.isDefined, f"partial write (strobe 0x$strobe%x, unaligned=$unaligned) never completed (hang)")
    dut.clockDomain.waitSampling(20)

    val rd = access(dut, read = true, addr, 0, 0xf, unaligned = false)
    assert(rd.isDefined, "read-back hung")

    val mask = expand(strobe)
    val expected = ((bg & (mask32 ^ mask)) | (nw & mask)) & mask32
    println(f"[strobe 0x$strobe%x un=$unaligned] read 0x${rd.get}%08x expected 0x$expected%08x")
    assert(
      rd.get == expected,
      f"strobe 0x$strobe%x un=$unaligned: read 0x${rd.get}%08x != expected 0x$expected%08x"
    )
  }

  test("word 1111 aligned (reference)") { compiled.doSim("s_1111") { d => roundTrip(d, 0xf, false) } }
  test("byte0 0001 aligned") { compiled.doSim("s_0001") { d => roundTrip(d, 0x1, false) } }
  test("byte1 0010 aligned") { compiled.doSim("s_0010") { d => roundTrip(d, 0x2, false) } }
  test("byte2 0100 aligned") { compiled.doSim("s_0100") { d => roundTrip(d, 0x4, false) } }
  test("byte3 1000 aligned") { compiled.doSim("s_1000") { d => roundTrip(d, 0x8, false) } }
  test("halfword-low 0011 aligned") { compiled.doSim("s_0011") { d => roundTrip(d, 0x3, false) } }
  test("halfword-high 1100 aligned") { compiled.doSim("s_1100") { d => roundTrip(d, 0xc, false) } }
  test("noncontiguous 1010 aligned") { compiled.doSim("s_1010") { d => roundTrip(d, 0xa, false) } }
  test("noncontiguous 0101 aligned") { compiled.doSim("s_0101") { d => roundTrip(d, 0x5, false) } }

  // The `unaligned` flag (odd byte address) is only asserted by the AXI/BMB
  // masters; TileLink hardcodes it false. Odd-address straddle is not
  // implemented, so the controller now rejects it with an error response -
  // see "unaligned access is rejected with an error response" below.

  /** Drive an N-beat burst (word strobe) and return the N frontend responses.
    * The coalesced controller streams responses *during* the send, so a
    * background collector drains them (master always ready) while the main
    * thread pushes the beats.
    */
  def burst(dut: RoundTripDut, read: Boolean, base: BigInt, datas: Seq[BigInt]): Seq[BigInt] = {
    val cd = dut.clockDomain
    val n = datas.size
    // Advance at least one edge before re-checking, so a handshake still
    // asserted from the previous beat isn't miscounted as a fresh fire.
    def waitFire(cond: => Boolean, max: Int = 20000): Boolean = {
      var t = 0
      do { cd.waitSampling(); t += 1 } while (!cond && t < max)
      cond
    }
    val results = scala.collection.mutable.ArrayBuffer[BigInt]()
    dut.io.frontend.ready #= true
    // Collector: master is always ready, so every cycle frontend.valid is high is
    // exactly one popped response.
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
      assert(waitFire(dut.io.controller.ready.toBoolean), s"burst beat $i not accepted")
    }
    dut.io.controller.valid #= false
    collector.join()
    dut.io.frontend.ready #= false
    assert(results.size == n, s"got ${results.size} of $n responses (read=$read)")
    results.toSeq
  }

  test("burst 4 words round-trip") {
    compiled.doSim("burst4") { dut =>
      initSim(dut)
      val base = BigInt(0x200)
      val words = Seq("deadbeef", "cafebabe", "12345678", "0badf00d").map(BigInt(_, 16))
      burst(dut, read = false, base, words)
      dut.clockDomain.waitSampling(20)
      val rd = burst(dut, read = true, base, Seq.fill(words.size)(BigInt(0)))
      for (i <- words.indices) {
        println(f"[burst word $i] read 0x${rd(i)}%08x expected 0x${words(i)}%08x")
        assert(rd(i) == words(i), f"burst word $i: read 0x${rd(i)}%08x != 0x${words(i)}%08x")
      }
    }
  }

  test("single writes then burst read (mixed access)") {
    compiled.doSim("mixed") { dut =>
      initSim(dut)
      val base = BigInt(0x600)
      val words = Seq("11111111", "22222222", "33333333", "44444444").map(BigInt(_, 16))
      for ((w, i) <- words.zipWithIndex) {
        assert(
          access(dut, read = false, base + i * 4, w, 0xf, unaligned = false).isDefined,
          s"single write $i hung"
        )
        dut.clockDomain.waitSampling(20)
      }
      val rd = burst(dut, read = true, base, Seq.fill(words.size)(BigInt(0)))
      for (i <- words.indices) {
        println(f"[mixed word $i] read 0x${rd(i)}%08x expected 0x${words(i)}%08x")
        assert(rd(i) == words(i), f"mixed word $i: read 0x${rd(i)}%08x != 0x${words(i)}%08x")
      }
    }
  }

  /** Run a burst while counting CS assertions (csN high->low). One CA per burst
    * means the controller coalesced it into a single HyperRAM linear burst.
    */
  def burstCountingCs(
      dut: RoundTripDut,
      read: Boolean,
      base: BigInt,
      datas: Seq[BigInt]
  ): (Seq[BigInt], Int) = {
    // Baseline the monitor from a known CS-high (PHY idle) state.
    while (!dut.phy.io.hyperbus.cs.toBigInt.testBit(0)) dut.clockDomain.waitSampling()
    var cs = 0
    var prev = true
    var running = true
    val mon = fork {
      while (running) {
        val cur = dut.phy.io.hyperbus.cs.toBigInt.testBit(0)
        if (prev && !cur) cs += 1
        prev = cur
        dut.clockDomain.waitSampling()
      }
    }
    val r = burst(dut, read, base, datas)
    dut.clockDomain.waitSampling(5)
    running = false
    mon.join()
    (r, cs)
  }

  test("burst 8 words: correct data + one CA per burst") {
    compiled.doSim("burst8") { dut =>
      initSim(dut)
      val base = BigInt(0x400)
      val words = (0 until 8).map(i => BigInt("a1b2c3d0", 16) + i)
      val (_, wrCa) = burstCountingCs(dut, read = false, base, words)
      dut.clockDomain.waitSampling(20)
      val (rd, rdCa) = burstCountingCs(dut, read = true, base, Seq.fill(8)(BigInt(0)))
      for (i <- words.indices) {
        println(f"[burst8 word $i] read 0x${rd(i)}%08x expected 0x${words(i)}%08x")
        assert(rd(i) == words(i), f"burst8 word $i: 0x${rd(i)}%08x != 0x${words(i)}%08x")
      }
      println(s"[burst8] CS assertions: write=$wrCa read=$rdCa (expect 1 each)")
      assert(wrCa == 1, s"write burst issued $wrCa CAs, expected 1 (coalescing)")
      assert(rdCa == 1, s"read burst issued $rdCa CAs, expected 1 (coalescing)")
    }
  }

  // A slow master back-pressures the frontend for the whole burst. The PHY's
  // rdataFifo buffers the entire (<= storageDepth) burst, so the read stays one
  // coalesced HyperRAM access and every word is delivered exactly once, in order
  // - no abort/retry needed.
  test("burst 8 words, slow master: correct data under back-pressure") {
    compiled.doSim("burst8slow") { dut =>
      initSim(dut)
      val cd = dut.clockDomain
      val base = BigInt(0x600)
      val words = (0 until 8).map(i => BigInt("b0b1b2c0", 16) + i)
      // Prime memory with a fast write burst.
      burst(dut, read = false, base, words)
      cd.waitSampling(20)
      // Baseline the CS monitor from a known CS-high (PHY idle) state.
      while (!dut.phy.io.hyperbus.cs.toBigInt.testBit(0)) cd.waitSampling()

      // Read back with a deliberately slow drain (accept 1 word, then stall).
      val results = scala.collection.mutable.ArrayBuffer[BigInt]()
      var cs = 0; var prevCs = true; var running = true
      val mon = fork {
        while (running) {
          val cur = dut.phy.io.hyperbus.cs.toBigInt.testBit(0)
          if (prevCs && !cur) cs += 1
          prevCs = cur
          cd.waitSampling()
        }
      }
      val collector = fork {
        var guard = 0
        while (results.size < 8 && guard < 400000) {
          dut.io.frontend.ready #= true
          if (dut.io.frontend.valid.toBoolean) results += dut.io.frontend.payload.data.toBigInt
          cd.waitSampling()
          dut.io.frontend.ready #= false
          cd.waitSampling(55) // long back-pressure held across the whole burst
          guard += 1
        }
        dut.io.frontend.ready #= false
      }
      def waitFire(cond: => Boolean, max: Int = 40000): Boolean = {
        var t = 0
        do { cd.waitSampling(); t += 1 } while (!cond && t < max)
        cond
      }
      for (i <- 0 until 8) {
        dut.io.controller.valid #= true
        dut.io.controller.payload.id #= 0
        dut.io.controller.payload.read #= true
        dut.io.controller.payload.memory #= true
        dut.io.controller.payload.unaligned #= false
        dut.io.controller.payload.addr #= base + i * 4
        dut.io.controller.payload.data #= 0
        dut.io.controller.payload.strobe #= 0xf
        dut.io.controller.payload.last #= (i == 7)
        assert(waitFire(dut.io.controller.ready.toBoolean), s"slow beat $i not accepted")
      }
      dut.io.controller.valid #= false
      collector.join()
      running = false
      mon.join()

      assert(results.size == 8, s"got ${results.size} of 8 responses")
      for (i <- words.indices) {
        println(f"[slow word $i] read 0x${results(i)}%08x expected 0x${words(i)}%08x")
        assert(results(i) == words(i), f"slow word $i: 0x${results(i)}%08x != 0x${words(i)}%08x")
      }
      println(s"[slow] read CS assertions=$cs (expect 1, buffered coalesced burst)")
      assert(cs == 1, s"expected a single coalesced CA (buffered), saw $cs")
    }
  }

  // Unaligned (odd-address) straddle is not implemented; the controller must
  // reject it with an error response rather than return wrong data.
  test("unaligned access is rejected with an error response") {
    compiled.doSim("unaligned_err") { dut =>
      initSim(dut)
      val cd = dut.clockDomain
      def waitFire(cond: => Boolean, max: Int = 8000): Boolean = {
        var t = 0
        do { cd.waitSampling(); t += 1 } while (!cond && t < max)
        cond
      }
      dut.io.controller.valid #= true
      dut.io.controller.payload.id #= 0
      dut.io.controller.payload.read #= true
      dut.io.controller.payload.memory #= true
      dut.io.controller.payload.unaligned #= true
      dut.io.controller.payload.addr #= 0x101
      dut.io.controller.payload.data #= 0
      dut.io.controller.payload.strobe #= 0xf
      dut.io.controller.payload.last #= true
      assert(waitFire(dut.io.controller.ready.toBoolean), "controller never accepted")
      dut.io.controller.valid #= false
      dut.io.frontend.ready #= true
      assert(waitFire(dut.io.frontend.valid.toBoolean), "no response to unaligned access")
      val err = dut.io.frontend.payload.error.toBoolean
      cd.waitSampling()
      dut.io.frontend.ready #= false
      println(s"[unaligned] frontend.error = $err")
      assert(err, "expected an error response for an unaligned access")
    }
  }
}
