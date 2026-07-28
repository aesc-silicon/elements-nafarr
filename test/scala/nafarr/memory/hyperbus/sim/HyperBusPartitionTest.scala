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

/** Partitioning oracle: two independent W956A8MBYA chips on one HyperBus, each a
  * partition selected by address. Verifies the controller drives the right chip
  * select per address, keeps the two chips independent, and raises a permission
  * fault on a read of a non-readable partition.
  */
class HyperBusPartitionTest extends AnyFunSuite {

  // Two 4 MB partitions -> memorySpace 8 MB; partition 1 non-readable so we can
  // exercise the permission fault too. Writes are allowed to both.
  val p = HyperBusCtrl.Parameter.default(List((BigInt(0x400000L), true), (BigInt(0x400000L), false)))
  val base1 = BigInt(0x400000L) // first byte of partition 1

  case class PartitionDut(p: HyperBusCtrl.Parameter) extends Component {
    val io = new Bundle {
      val controller = slave(Stream(HyperBus.ControllerInterface(p)))
      val frontend = master(Stream(HyperBus.FrontendInterface(p)))
      val latencyCycles = in(UInt(log2Up(6) bits))
    }
    val ctrl = HyperBusCtrl(p)
    val phy = HyperBusGenericPhy(p)
    val chips = Seq.fill(2)(W956A8MBYA())

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

    val clk = ClockDomain.current.readClockWire
    for ((m, i) <- chips.zipWithIndex) {
      m.io.clock := clk
      m.io.ck := phy.io.hyperbus.ck
      m.io.ckN := !phy.io.hyperbus.ck
      m.io.csN := phy.io.hyperbus.cs(i)
      m.io.resetN := phy.io.hyperbus.reset
      m.io.dqIn := phy.io.hyperbus.dq.write
      m.io.rwdsIn := phy.io.hyperbus.rwds.write
      m.io.dqIn.addTag(crossClockDomain)
      m.io.rwdsIn.addTag(crossClockDomain)
    }
    // Only the selected chip drives the shared bus; the other is held in reset.
    val sel0 = phy.io.hyperbus.cs(0) === False
    phy.io.hyperbus.dq.read := Mux(sel0, chips(0).io.dqOut, chips(1).io.dqOut)
    phy.io.hyperbus.rwds.read := Mux(sel0, chips(0).io.rwdsOut, chips(1).io.rwdsOut)
    phy.io.hyperbus.dq.read.addTag(crossClockDomain)
    phy.io.hyperbus.rwds.read.addTag(crossClockDomain)
    phy.io.hyperbus.cs.simPublic()
  }

  lazy val compiled = SimConfig.withWave
    .withConfig(SpinalConfig(defaultClockDomainFrequency = FixedFrequency(100 MHz)))
    .addSimulatorFlag("--x-initial 0")
    .compile(PartitionDut(p))

  def initSim(dut: PartitionDut): Unit = {
    dut.clockDomain.forkStimulus(10)
    dut.io.controller.valid #= false
    dut.io.frontend.ready #= false
    dut.io.latencyCycles #= 7
    dut.clockDomain.waitSampling(50)
  }

  /** One access; returns (data, error) plus the set of chip-select lines that
    * were asserted during it (to verify address -> chip routing).
    */
  def access(
      dut: PartitionDut,
      read: Boolean,
      addr: BigInt,
      data: BigInt,
      strobe: BigInt
  ): (BigInt, Boolean, Set[Int]) = {
    val cd = dut.clockDomain
    val csSeen = scala.collection.mutable.Set[Int]()
    var running = true
    val mon = fork {
      while (running) {
        val cs = dut.phy.io.hyperbus.cs.toBigInt
        for (i <- 0 until 2) if (!cs.testBit(i)) csSeen += i
        cd.waitSampling()
      }
    }
    def waitFire(cond: => Boolean, max: Int = 8000): Boolean = {
      var t = 0
      do { cd.waitSampling(); t += 1 } while (!cond && t < max)
      cond
    }
    dut.io.controller.valid #= true
    dut.io.controller.payload.id #= 0
    dut.io.controller.payload.read #= read
    dut.io.controller.payload.memory #= true
    dut.io.controller.payload.unaligned #= false
    dut.io.controller.payload.addr #= addr
    dut.io.controller.payload.data #= data
    dut.io.controller.payload.strobe #= strobe
    dut.io.controller.payload.last #= true
    assert(waitFire(dut.io.controller.ready.toBoolean), "controller never accepted")
    dut.io.controller.valid #= false
    dut.io.frontend.ready #= true
    assert(waitFire(dut.io.frontend.valid.toBoolean), "frontend response missing")
    val r = dut.io.frontend.payload.data.toBigInt
    val err = dut.io.frontend.payload.error.toBoolean
    cd.waitSampling()
    dut.io.frontend.ready #= false
    cd.waitSampling(5)
    running = false
    mon.join()
    (r, err, csSeen.toSet)
  }

  test("address selects the chip and the two partitions are independent") {
    compiled.doSim("routing") { dut =>
      initSim(dut)
      val a0 = BigInt(0x100) // partition 0
      val a1 = base1 + 0x100 // partition 1
      val v0 = BigInt("11223344", 16)
      val v1 = BigInt("aabbccdd", 16)

      val (_, e0w, cs0w) = access(dut, read = false, a0, v0, 0xf)
      assert(!e0w && cs0w == Set(0), s"partition-0 write hit CS $cs0w (err=$e0w)")
      val (_, e1w, cs1w) = access(dut, read = false, a1, v1, 0xf)
      assert(!e1w && cs1w == Set(1), s"partition-1 write hit CS $cs1w (err=$e1w)")

      // Read partition 0 back (partition 1 is non-readable -> covered below).
      val (r0, e0r, cs0r) = access(dut, read = true, a0, 0, 0xf)
      println(f"[part0] read 0x$r0%08x expected 0x$v0%08x on CS $cs0r")
      assert(!e0r && cs0r == Set(0), s"partition-0 read hit CS $cs0r (err=$e0r)")
      assert(r0 == v0, f"partition-0 read 0x$r0%08x != 0x$v0%08x")
      // Independence: partition 0 unaffected by the partition-1 write to CS 1.
      assert(r0 == v0, "partition-0 content changed after partition-1 write")
    }
  }

  test("read of a non-readable partition raises a permission fault") {
    compiled.doSim("permission") { dut =>
      initSim(dut)
      val a1 = base1 + 0x200
      // Write is allowed to partition 1.
      val (_, ew, csw) = access(dut, read = false, a1, BigInt("deadbeef", 16), 0xf)
      assert(!ew && csw == Set(1), s"partition-1 write err=$ew cs=$csw")
      // Read is not: expect the frontend error flag set.
      val (_, er, _) = access(dut, read = true, a1, 0, 0xf)
      println(s"[perm] read of non-readable partition -> error=$er")
      assert(er, "expected a permission fault (frontend.error) on non-readable read")
    }
  }
}
