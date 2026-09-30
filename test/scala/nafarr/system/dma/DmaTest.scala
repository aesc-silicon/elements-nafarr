// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.dma

import scala.collection.mutable

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, Opcode}
import spinal.lib.sim.StreamMonitor

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest
import nafarr.bus.tilelink.TileLinkSlaveModel

class DmaTest extends AnyFunSuite {

  test("Apb3Parameter") {
    generationShouldPass(Apb3Dma(DmaCtrl.Parameter.small()))
    generationShouldPass(Apb3Dma(DmaCtrl.Parameter.medium()))
    generationShouldPass(Apb3Dma(DmaCtrl.Parameter.large()))
    generationShouldPass(Apb3Dma(DmaCtrl.Parameter.default()))
    generationShouldPass(Apb3Dma(DmaCtrl.Parameter(channels = 1, requestLines = 0, burstBytes = 4)))
    generationShouldPass(
      Apb3Dma(DmaCtrl.Parameter(channels = 8, requestLines = 16, burstBytes = 4096))
    )
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(channels = 0)))
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(channels = 9)))
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(requestLines = 17)))
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(burstBytes = 2)))
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(burstBytes = 48)))
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(burstBytes = 8192)))
    generationShouldPass(Apb3Dma(DmaCtrl.Parameter(dataWidth = 64)))
    generationShouldPass(Apb3Dma(DmaCtrl.Parameter(dataWidth = 128, burstBytes = 16)))
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(dataWidth = 16)))
    generationShouldFail(Apb3Dma(DmaCtrl.Parameter(dataWidth = 128, burstBytes = 8)))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkDma(DmaCtrl.Parameter.default()))
    generationShouldPass(TileLinkDma(DmaCtrl.Parameter.small()))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneDma(DmaCtrl.Parameter.default()))
    generationShouldPass(WishboneDma(DmaCtrl.Parameter.small()))
  }

  // 2 channels, 4 request lines, 32-byte bursts
  def simParam = DmaCtrl.Parameter(channels = 2, requestLines = 4, burstBytes = 32)

  val RAM = 0x80000000L
  val PERIPH = 0xf0001000L

  def cfg(
      srcInc: Boolean = true,
      dstInc: Boolean = true,
      width: Int = 2,
      req: Int = -1,
      linked: Boolean = false,
      irqDone: Boolean = true,
      burstLimit: Int = 0
  ): BigInt = {
    var v = BigInt(0)
    if (srcInc) v |= BigInt(1) << DmaCtrl.Config.srcInc
    if (dstInc) v |= BigInt(1) << DmaCtrl.Config.dstInc
    v |= BigInt(width) << DmaCtrl.Config.width
    if (req >= 0) {
      v |= BigInt(1) << DmaCtrl.Config.reqEnable
      v |= BigInt(req) << DmaCtrl.Config.reqSel
    }
    if (linked) v |= BigInt(1) << DmaCtrl.Config.linked
    if (irqDone) v |= BigInt(1) << DmaCtrl.Config.irqDone
    v |= BigInt(burstLimit) << DmaCtrl.Config.burstLimit
    v
  }

  class Env(dut: Apb3Dma) {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = DmaCtrl.Regs(dut.mapper.idCtrl.length, simParam)
    val slave = new TileLinkSlaveModel(dut.io.mem, dut.clockDomain)
    dut.io.request.foreach(_.req #= false)
    dut.clockDomain.forkStimulus(10)
    dut.clockDomain.waitSampling(2)

    def program(
        ch: Int,
        config: BigInt,
        src: Long,
        dst: Long,
        length: Long,
        next: Long = 0
    ): Unit = {
      driver.write(regs.config(ch), config)
      driver.write(regs.src(ch), src)
      driver.write(regs.dst(ch), dst)
      driver.write(regs.length(ch), length)
      driver.write(regs.next(ch), next)
    }

    def start(ch: Int): Unit = driver.write(regs.control(ch), 1)
    def abort(ch: Int): Unit = driver.write(regs.control(ch), 2)
    def busy(ch: Int): Boolean = (driver.read(regs.control(ch)) & 1) == 1
    def error(ch: Int): Boolean = (driver.read(regs.control(ch)) & 2) == 2

    def waitIdle(ch: Int, maxPolls: Int = 5000): Unit = {
      var polls = 0
      while (busy(ch)) {
        polls += 1
        assert(polls < maxPolls, s"channel $ch did not finish")
      }
    }

    def writeDescriptor(
        address: Long,
        config: BigInt,
        src: Long,
        dst: Long,
        length: Long,
        next: Long
    ) = {
      slave.writeWord(address + 0x00, config)
      slave.writeWord(address + 0x04, src)
      slave.writeWord(address + 0x08, dst)
      slave.writeWord(address + 0x0c, length)
      slave.writeWord(address + 0x10, next)
    }

    def randomBytes(n: Int): Seq[Int] = Seq.fill(n)(simRandom.nextInt(256))
  }

  // The memory port is tested at every supported bus width, each in its own workspace.
  val dataWidths = Seq(32, 64, 128)
  private val compiledByWidth = mutable.Map[Int, SimCompiled[Apb3Dma]]()
  def compiled(width: Int): SimCompiled[Apb3Dma] =
    compiledByWidth.getOrElseUpdate(
      width,
      SimConfig.withWave
        .workspaceName(s"Apb3Dma_$width")
        .compile(Apb3Dma(simParam.copy(dataWidth = width)))
    )

  for (width <- dataWidths) test(s"IpIdentification ($width-bit)") {
    compiled(width).doSim("IpIdentification") { dut =>
      val env = new Env(dut)
      IpIdentificationTest.V0.checkApi(env.driver, IpIdentification.Ids.Dma)
      IpIdentificationTest.V0.checkVersion(env.driver, 1, 0, 0)
    }
  }

  for (width <- dataWidths) test(s"Info register ($width-bit)") {
    compiled(width).doSim("InfoRegister") { dut =>
      val env = new Env(dut)
      SimTest.readField(env.driver, env.regs.info, 7, 0, 2, "channels=2")
      SimTest.readField(env.driver, env.regs.info, 15, 8, 4, "requestLines=4")
      SimTest.readField(env.driver, env.regs.info, 23, 16, 5, "burstLog2=5")
    }
  }

  for (width <- dataWidths) test(s"Memory to memory copy with bursts ($width-bit)") {
    compiled(width).doSim("MemToMem") { dut =>
      val env = new Env(dut)
      val src = RAM + 0x100
      val dst = RAM + 0x1000
      val data = env.randomBytes(100)
      env.slave.fill(src, data)

      var writeBeats = 0
      StreamMonitor(dut.io.mem.a, dut.clockDomain) { a =>
        if (a.opcode.toEnum == Opcode.A.PUT_FULL_DATA) writeBeats += 1
      }
      env.program(0, cfg(), src, dst, data.length)
      env.start(0)
      env.waitIdle(0)

      assert(env.slave.readBytes(dst, data.length) == data, "copied data mismatch")
      assert(!env.error(0))
      // Chunks of 32 + 32 + 32 + 4 bytes, each written in full-width beats.
      val dataBytes = width / 8
      val expectedBeats = Seq(32, 32, 32, 4).map(c => (c + dataBytes - 1) / dataBytes).sum
      assert(writeBeats == expectedBeats, s"expected $expectedBeats write beats, got $writeBeats")
      SimTest.read(env.driver, env.regs.src(0), src + data.length, "src advanced")
      SimTest.read(env.driver, env.regs.dst(0), dst + data.length, "dst advanced")
      SimTest.read(env.driver, env.regs.length(0), 0, "length exhausted")
      SimTest.readField(env.driver, env.regs.irqPending, 0, 0, 1, "done pending")
      SimTest.readField(env.driver, env.regs.irqPending, 1, 1, 0, "no error pending")
      // 100 bytes from aligned addresses: 32+32+32+4 -> 4 transactions each way
      assert(env.slave.reads == 4, s"expected 4 read bursts, got ${env.slave.reads}")
      assert(env.slave.writes == 4, s"expected 4 write bursts, got ${env.slave.writes}")

      env.driver.write(env.regs.irqPending, 1)
      SimTest.read(env.driver, env.regs.irqPending, 0, "pending cleared")
    }
  }

  for (width <- dataWidths) test(s"Interrupt output follows mask ($width-bit)") {
    compiled(width).doSim("Interrupt") { dut =>
      val env = new Env(dut)
      val data = env.randomBytes(8)
      env.slave.fill(RAM, data)
      env.program(0, cfg(), RAM, RAM + 0x40, 8)
      env.start(0)
      env.waitIdle(0)
      assert(!dut.io.interrupt.toBoolean, "interrupt asserted while masked")
      env.driver.write(env.regs.irqMask, 1)
      dut.clockDomain.waitSampling(2)
      assert(dut.io.interrupt.toBoolean, "interrupt not asserted")
      env.driver.write(env.regs.irqPending, 1)
      dut.clockDomain.waitSampling(2)
      assert(!dut.io.interrupt.toBoolean, "interrupt not cleared")
    }
  }

  for (width <- dataWidths) test(s"Unaligned copy falls back to smaller chunks ($width-bit)") {
    compiled(width).doSim("Unaligned") { dut =>
      val env = new Env(dut)
      val src = RAM + 0x101
      val dst = RAM + 0x2003
      val data = env.randomBytes(37)
      env.slave.fill(src, data)
      // Guard bytes around the destination must survive.
      env.slave.write8(dst - 1, 0xa5)
      env.slave.write8(dst + data.length, 0x5a)

      env.program(0, cfg(), src, dst, data.length)
      env.start(0)
      env.waitIdle(0)

      assert(env.slave.readBytes(dst, data.length) == data, "copied data mismatch")
      assert(
        env.slave.read8(dst - 1) == 0xa5 && env.slave.read8(dst + data.length) == 0x5a,
        "guard bytes clobbered"
      )
      assert(!env.error(0))
    }
  }

  for (width <- dataWidths) test(s"Burst limit caps transaction size ($width-bit)") {
    compiled(width).doSim("BurstLimit") { dut =>
      val env = new Env(dut)
      val data = env.randomBytes(64)
      env.slave.fill(RAM, data)
      env.program(0, cfg(burstLimit = 3), RAM, RAM + 0x100, 64)
      env.start(0)
      env.waitIdle(0)
      assert(env.slave.readBytes(RAM + 0x100, 64) == data)
      assert(env.slave.reads == 8, s"expected 8 read bursts of 8 bytes, got ${env.slave.reads}")
    }
  }

  for (width <- dataWidths) test(s"Fixed source fills memory ($width-bit)") {
    compiled(width).doSim("Fill") { dut =>
      val env = new Env(dut)
      val pattern = RAM + 0x10
      env.slave.writeWord(pattern, BigInt("deadbeef", 16))
      env.program(0, cfg(srcInc = false), pattern, RAM + 0x200, 64)
      env.start(0)
      env.waitIdle(0)
      for (i <- 0 until 16) {
        assert(env.slave.readWord(RAM + 0x200 + i * 4) == BigInt("deadbeef", 16), s"word $i")
      }
      SimTest.read(env.driver, env.regs.src(0), pattern, "fixed src unchanged")
    }
  }

  for (width <- dataWidths) test(s"Request-gated peripheral to memory ($width-bit)") {
    compiled(width).doSim("PeriphToMem") { dut =>
      val env = new Env(dut)
      val data = env.randomBytes(20)
      val fifo = mutable.Queue(data: _*)
      def present(): Unit = {
        if (fifo.nonEmpty) {
          env.slave.writeWord(PERIPH, fifo.head)
        }
      }
      DmaHandshakeSim.drive(dut.io.request(1), dut.clockDomain)(fifo.nonEmpty)
      env.slave.onRead = (address, _) => {
        if (address == PERIPH) {
          fifo.dequeue()
          present()
        }
      }
      present()

      env.program(0, cfg(srcInc = false, width = 0, req = 1), PERIPH, RAM + 0x300, data.length)
      env.start(0)
      env.waitIdle(0)

      assert(env.slave.readBytes(RAM + 0x300, data.length) == data, "received data mismatch")
      assert(
        env.slave.reads == data.length,
        s"expected ${data.length} reads, got ${env.slave.reads}"
      )
      assert(fifo.isEmpty)
      assert(!env.error(0))
    }
  }

  for (width <- dataWidths) test(s"Request-gated memory to peripheral ($width-bit)") {
    compiled(width).doSim("MemToPeriph") { dut =>
      val env = new Env(dut)
      val data = env.randomBytes(16)
      env.slave.fill(RAM + 0x400, data)
      val received = mutable.ArrayBuffer[Int]()
      var ready = false
      DmaHandshakeSim.drive(dut.io.request(3), dut.clockDomain)(ready)
      env.slave.onWrite = (address, bytes) => {
        if (address == PERIPH) {
          assert(bytes == 2, s"expected 2-byte element, got $bytes")
          received += env.slave.read8(PERIPH)
          received += env.slave.read8(PERIPH + 1)
        }
      }

      StreamMonitor(dut.io.mem.a, dut.clockDomain) { a =>
        if (a.address.toLong == PERIPH && a.opcode.toEnum == Opcode.A.PUT_FULL_DATA) {
          val unused = (0 until width / 8).filter(b => !a.mask.toBigInt.testBit(b))
          val lanes = unused.map(b => (a.data.toBigInt >> (8 * b)) & 0xff)
          assert(lanes.forall(_ == 0), s"unmasked lanes not zero: ${a.data.toBigInt.toString(16)}")
        }
      }
      env.program(0, cfg(dstInc = false, width = 1, req = 3), RAM + 0x400, PERIPH, data.length)
      env.start(0)
      dut.clockDomain.waitSampling(200)
      assert(env.slave.writes == 0, "transfer ran without request")
      assert(env.busy(0))
      SimTest.readField(env.driver, env.regs.control(0), 2, 2, 0, "request inactive")

      ready = true
      SimTest.readField(env.driver, env.regs.control(0), 2, 2, 1, "request active")
      env.waitIdle(0)
      assert(received == data, "peripheral received wrong data")
      assert(env.slave.writes == data.length / 2)
    }
  }

  for (width <- dataWidths) test(s"Request handshake moves one element per acknowledge ($width-bit)") {
    compiled(width).doSim("ReqAck") { dut =>
      val env = new Env(dut)
      env.slave.fill(RAM + 0x400, env.randomBytes(8))
      val hs = dut.io.request(2)

      // A peripheral that ignores ack gets exactly one element.
      hs.req #= true
      env.program(0, cfg(dstInc = false, width = 0, req = 2), RAM + 0x400, PERIPH, 8)
      env.start(0)
      dut.clockDomain.waitSampling(200)
      assert(env.slave.writes == 1, s"req held high moved ${env.slave.writes} elements")
      assert(hs.ack.toBoolean, "ack not raised after the element")

      // Dropping req releases ack; a handshaking peripheral then drains the rest.
      hs.req #= false
      dut.clockDomain.waitSampling(3)
      assert(!hs.ack.toBoolean, "ack held after req dropped")
      assert(env.slave.writes == 1, "element moved without req")
      DmaHandshakeSim.drive(hs, dut.clockDomain)(true)
      env.waitIdle(0)
      assert(env.slave.writes == 8)
      assert(!env.error(0))
    }
  }

  test("Request handshake across clock domains") {
    val compiledCc = SimConfig.withWave.compile(DmaCcHarness(simParam))
    // (peripheral clock period, DMA clock period): slower and faster peripheral clock
    for ((slowPeriod, name) <- Seq((73, "SlowPeripheral"), (7, "FastPeripheral"))) {
      compiledCc.doSim(name) { dut =>
        val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
        val regs = DmaCtrl.Regs(dut.dma.mapper.idCtrl.length, simParam)
        val slave = new TileLinkSlaveModel(dut.io.mem, dut.clockDomain)
        val data = Seq.fill(24)(simRandom.nextInt(256))
        slave.fill(RAM, data)

        // Peripheral writes cross into the peripheral domain and their response crosses back.
        val crossing = 2 * (slowPeriod + 9) / 10 + 2
        slave.responseDelay = address => if (address == PERIPH) crossing else 0

        // Two-entry TX FIFO in the peripheral domain, drained every few peripheral cycles.
        val depth = 2
        val fifo = mutable.Queue[Int]()
        val sent = mutable.ArrayBuffer[Int]()
        slave.onWrite = (address, _) => {
          if (address == PERIPH) {
            fifo.enqueue(slave.read8(PERIPH))
            assert(fifo.size <= depth, "peripheral FIFO overflow")
          }
        }
        DmaHandshakeSim.drive(dut.io.periph, dut.slowCd)(fifo.size < depth)
        var tick = 0
        dut.slowCd.onSamplings {
          tick += 1
          if (tick % 5 == 0 && fifo.nonEmpty) {
            sent += fifo.dequeue()
          }
        }

        dut.clockDomain.forkStimulus(10)
        dut.slowCd.forkStimulus(slowPeriod)
        dut.clockDomain.waitSampling(10)

        driver.write(regs.config(0), cfg(dstInc = false, width = 0, req = 0))
        driver.write(regs.src(0), RAM)
        driver.write(regs.dst(0), PERIPH)
        driver.write(regs.length(0), data.length)
        driver.write(regs.control(0), 1)
        var polls = 0
        while ((driver.read(regs.control(0)) & 1) == 1) {
          polls += 1
          assert(polls < 20000, "channel did not finish")
        }
        assert((driver.read(regs.control(0)) & 2) == 0, "channel error")
        dut.slowCd.waitSampling(20)
        assert(sent ++ fifo == data, "peripheral received wrong data")
      }
    }
  }

  for (width <- dataWidths) test(s"Descriptor chain ($width-bit)") {
    compiled(width).doSim("Descriptors") { dut =>
      val env = new Env(dut)
      val desc0 = RAM + 0x3000
      val desc1 = RAM + 0x3020
      val a = env.randomBytes(16)
      val c = env.randomBytes(8)
      env.slave.fill(RAM + 0x500, a)
      env.slave.fill(RAM + 0x600, c)
      env.writeDescriptor(
        desc0,
        cfg(linked = true, irqDone = false),
        RAM + 0x500,
        RAM + 0x540,
        16,
        desc1
      )
      env.writeDescriptor(desc1, cfg(), RAM + 0x600, RAM + 0x640, 8, 0)

      env.program(0, cfg(linked = true, irqDone = false), 0, 0, 0, desc0)
      env.start(0)
      env.waitIdle(0)

      assert(env.slave.readBytes(RAM + 0x540, 16) == a, "descriptor 0 data mismatch")
      assert(env.slave.readBytes(RAM + 0x640, 8) == c, "descriptor 1 data mismatch")
      SimTest.read(env.driver, env.regs.next(0), 0, "chain terminated")
      SimTest.read(env.driver, env.regs.config(0), cfg(), "last descriptor config loaded")
      SimTest.readField(env.driver, env.regs.irqPending, 0, 0, 1, "done pending")
      assert(!env.error(0))
    }
  }

  for (width <- dataWidths) test(s"Bus error stops the channel ($width-bit)") {
    compiled(width).doSim("BusError") { dut =>
      val env = new Env(dut)
      env.slave.denyAt = address => address >= RAM + 0x800 && address < RAM + 0x900
      env.program(0, cfg(), RAM + 0x700, RAM + 0x800, 32)
      env.start(0)
      env.waitIdle(0)
      assert(env.error(0), "error flag not set")
      SimTest.readField(env.driver, env.regs.irqPending, 1, 1, 1, "error pending")
      SimTest.readField(env.driver, env.regs.irqPending, 0, 0, 0, "no done pending")
      SimTest.read(env.driver, env.regs.length(0), 32, "length untouched after failed chunk")

      // A new start clears the error and runs normally.
      env.slave.denyAt = (_: Long) => false
      env.start(0)
      env.waitIdle(0)
      assert(!env.error(0), "error flag not cleared by start")
    }
  }

  for (width <- dataWidths) test(s"Misaligned element access is rejected ($width-bit)") {
    compiled(width).doSim("Misaligned") { dut =>
      val env = new Env(dut)
      dut.io.request(0).req #= true
      env.program(0, cfg(width = 2, req = 0), RAM + 0x1, RAM + 0x100, 8)
      env.start(0)
      env.waitIdle(0)
      assert(env.error(0), "misaligned transfer accepted")
      assert(env.slave.reads == 0 && env.slave.writes == 0)
    }
  }

  for (width <- dataWidths) test(s"Abort stops a running channel ($width-bit)") {
    compiled(width).doSim("Abort") { dut =>
      val env = new Env(dut)
      env.slave.readyRandomizer.setFactor(0.1f)
      env.program(0, cfg(), RAM, RAM + 0x4000, 4096)
      env.start(0)
      dut.clockDomain.waitSampling(300)
      assert(env.busy(0))
      env.abort(0)
      env.waitIdle(0, maxPolls = 200)
      val left = env.driver.read(env.regs.length(0))
      assert(left > 0 && left < 4096, s"unexpected remaining length $left")
      assert(!env.error(0))
      SimTest.read(env.driver, env.regs.irqPending, 0, "abort raised an interrupt")
    }
  }

  for (width <- dataWidths) test(s"Two channels share the engine ($width-bit)") {
    compiled(width).doSim("TwoChannels") { dut =>
      val env = new Env(dut)
      val a = env.randomBytes(96)
      val b = env.randomBytes(96)
      env.slave.fill(RAM + 0x1000, a)
      env.slave.fill(RAM + 0x2000, b)
      env.program(0, cfg(), RAM + 0x1000, RAM + 0x5000, 96)
      env.program(1, cfg(), RAM + 0x2000, RAM + 0x6000, 96)
      env.start(0)
      env.start(1)
      SimTest.readField(env.driver, env.regs.status, 1, 0, 3, "both busy")
      env.waitIdle(0)
      env.waitIdle(1)
      assert(env.slave.readBytes(RAM + 0x5000, 96) == a, "channel 0 data mismatch")
      assert(env.slave.readBytes(RAM + 0x6000, 96) == b, "channel 1 data mismatch")
      SimTest.readField(env.driver, env.regs.irqPending, 3, 0, 5, "both done pending")
    }
  }

  for (width <- dataWidths) test(s"Registers are read-only while busy ($width-bit)") {
    compiled(width).doSim("BusyLock") { dut =>
      val env = new Env(dut)
      env.slave.readyRandomizer.setFactor(0.1f)
      env.program(0, cfg(), RAM, RAM + 0x4000, 2048)
      env.start(0)
      env.driver.write(env.regs.length(0), 4)
      env.driver.write(env.regs.src(0), 0x12345678L)
      val len = env.driver.read(env.regs.length(0))
      assert(len > 4, s"length overwritten while busy: $len")
      env.abort(0)
      env.waitIdle(0)
      env.driver.write(env.regs.src(0), 0x12345678L)
      SimTest.read(env.driver, env.regs.src(0), 0x12345678L, "src writable when idle")
    }
  }
}

/** Apb3Dma with request line 0 behind a [[DmaHandshakeCc]] from a separate peripheral clock. */
case class DmaCcHarness(p: DmaCtrl.Parameter) extends Component {
  val slowCd = ClockDomain.external("slow")
  val io = new Bundle {
    val bus = slave(Apb3(Apb3Config(12, 32)))
    val mem = master(TileLinkBus(p.memParam))
    val periph = slave(DmaHandshake())
  }
  val dma = Apb3Dma(p)
  dma.io.bus <> io.bus
  dma.io.mem <> io.mem
  val cc = DmaHandshakeCc(slowCd, ClockDomain.current)
  cc.io.peripheral <> io.periph
  dma.io.request(0) <> cc.io.dma
}
