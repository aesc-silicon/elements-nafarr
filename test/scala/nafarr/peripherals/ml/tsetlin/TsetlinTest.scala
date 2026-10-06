// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.ml.tsetlin

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest
import nafarr.blackboxes.muninn.Tcam

import scala.util.Random

class TsetlinTest extends AnyFunSuite {
  def model(rows: Int, cols: Int, classes: Int, tiles: Int = 1, perClass: Int = 0) =
    TsetlinCtrl.Parameter(
      Tcam.Parameter(rows, cols, hard = false),
      classes,
      tiles,
      perClass = perClass
    )

  test("Apb3TsetlinParameters") {
    generationShouldPass(Apb3Tsetlin(model(16, 8, 2)))
    generationShouldPass(Apb3Tsetlin(model(64, 80, 4)))
    generationShouldPass(Apb3Tsetlin(model(64, 80, 4, tiles = 2)))
    generationShouldPass(Apb3Tsetlin(TsetlinCtrl.Parameter.default())) // hard macro
    generationShouldPass(Apb3Tsetlin(model(64, 80, 3))) // 3 x 20, 4 rows unused
    generationShouldPass(Apb3Tsetlin(model(64, 160, 6))) // 6 x 10, 4 rows unused
    generationShouldPass(Apb3Tsetlin(model(64, 80, 3, perClass = 8))) // 40 rows unused
    generationShouldFail(Apb3Tsetlin(model(16, 8, 16))) // one clause per class
    generationShouldFail(Apb3Tsetlin(model(16, 8, 2, perClass = 3))) // odd
    generationShouldFail(Apb3Tsetlin(model(16, 8, 2, perClass = 10))) // too many rows
    generationShouldFail(Apb3Tsetlin(model(8, 8, 2))) // smaller than a macro
  }

  test("TileLinkTsetlinParameters") {
    generationShouldPass(TileLinkTsetlin(model(16, 8, 2)))
    generationShouldPass(TileLinkTsetlin(model(64, 160, 8)))
  }

  test("WishboneTsetlinParameters") {
    generationShouldPass(WishboneTsetlin(model(16, 8, 2)))
    generationShouldPass(WishboneTsetlin(model(64, 160, 8)))
  }

  def init(dut: Apb3Tsetlin): (Apb3Driver, TsetlinCtrl.Regs) = {
    dut.clockDomain.forkStimulus(10)
    val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
    dut.clockDomain.waitSampling(2)
    (apb, TsetlinCtrl.Regs(dut.mapper.idCtrl.length))
  }

  def words(bits: BigInt, n: Int): Seq[BigInt] =
    (0 until n).map(w => (bits >> (32 * w)) & BigInt("ffffffff", 16))

  /** Software reference: clause outputs, class sums and winner. */
  case class Reference(p: TsetlinCtrl.Parameter, include: Seq[BigInt]) {
    def clauses(lit: BigInt): Seq[Boolean] = {
      val l = if (p.validColumn) lit.clearBit(0) else lit
      val mask = (BigInt(1) << p.literals) - 1
      include.map(inc => (inc & (~l & mask)) == 0)
    }
    def sums(lit: BigInt): Seq[Int] = {
      val c = clauses(lit)
      (0 until p.classes).map { k =>
        val g = c.slice(k * p.clausesPerClass, (k + 1) * p.clausesPerClass)
        g.zipWithIndex.map { case (m, i) => if (!m) 0 else if (i % 2 == 0) 1 else -1 }.sum
      }
    }
    def winner(lit: BigInt): Int = { val s = sums(lit); s.indexOf(s.max) }
  }

  def run(p: TsetlinCtrl.Parameter, searches: Int, name: String) {
    SimConfig.withWave.compile(Apb3Tsetlin(p)).doSim(name) { dut =>
      val (apb, regs) = init(dut)
      val rnd = new Random(1)

      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Tsetlin)
      IpIdentificationTest.V0.checkVersion(apb, 1, 0, 0)
      SimTest.readField(apb, regs.array, 15, 0, p.rows, "rows")
      SimTest.readField(apb, regs.array, 31, 16, p.literals, "literals")
      SimTest.readField(apb, regs.classes, 15, 0, p.classes, "classes")
      SimTest.readField(apb, regs.classes, 31, 16, p.clausesPerClass, "clauses per class")
      SimTest.readField(apb, regs.options, 7, 0, p.tiles, "tiles")
      SimTest.readField(apb, regs.options, 8, 8, 1, "valid column")

      // sparse clauses, as a trained Tsetlin machine has them
      val include = (0 until p.rows).map { _ =>
        (0 until p.literals).foldLeft(BigInt(0)) { (acc, b) =>
          if (b > 0 && rnd.nextInt(8) == 0) acc.setBit(b) else acc
        }
      }
      for ((inc, r) <- include.zipWithIndex) {
        for ((w, i) <- words(inc, p.literalWords).zipWithIndex) apb.write(regs.data(i), w)
        apb.write(regs.row, r)
        apb.write(regs.commit, 1)
      }
      val ref = Reference(p, include)

      apb.write(regs.ctrl, 2) // irq enable
      for (s <- 0 until searches) {
        val lit = BigInt(p.literals, rnd)
        for ((w, i) <- words(lit, p.literalWords).zipWithIndex) apb.write(regs.literal(i), w)
        apb.write(regs.ctrl, 3) // start, keep irq enable
        dut.clockDomain.waitSamplingWhere(dut.io.interrupt.toBoolean)

        val matches = (0 until p.rowWords).foldLeft(BigInt(0)) { (acc, w) =>
          acc | (apb.read(regs.matches(w)) << (32 * w))
        }
        val expect = ref.clauses(lit).zipWithIndex.foldLeft(BigInt(0)) { case (acc, (m, i)) =>
          if (m) acc.setBit(i) else acc
        }
        assert(matches == expect, f"search $s: matches $matches%x, expected $expect%x")
        for ((e, c) <- ref.sums(lit).zipWithIndex) {
          val got = apb.read(regs.sum(c)).toInt
          assert(got == e, s"search $s: class $c sum $got, expected $e")
        }
        val result = apb.read(regs.result)
        assert((result & 0xffff) == ref.winner(lit), s"search $s: winner ${result & 0xffff}")
        apb.write(regs.status, 2) // clear done
        dut.clockDomain.waitSampling()
        assert(!dut.io.interrupt.toBoolean, "interrupt still pending")
      }
    }
  }

  test("model") { run(model(16, 8, 2), 20, "model") }
  test("tiles") { run(model(32, 8, 4, tiles = 2), 20, "tiles") }
  test("unusedRows") { run(model(16, 8, 3), 20, "unusedRows") } // 3 x 4, rows 12-15 unused

  // the released macro's own behavioural model (latch based, CLK phases); runs when the
  // muninn-macros repository (MUNINN_MACROS or ext/muninn-macros) provides it
  test("macro") {
    val p = TsetlinCtrl.Parameter(Tcam.Parameter(64, 80, hard = true), 6)
    assume(Tcam.modelPath(p.tcam).isDefined, s"${p.tcam.name} not released")
    run(p, 20, "macro")
  }
  test("macroTiles") { // two macros form 160-literal clauses
    val p = TsetlinCtrl.Parameter(Tcam.Parameter(64, 80, hard = true), 6, tiles = 2)
    assume(Tcam.modelPath(p.tcam).isDefined, s"${p.tcam.name} not released")
    run(p, 20, "macroTiles")
  }
  test("macroConservative") {
    val tcam = Tcam.Parameter(64, 80, hard = true, Tcam.Variant.Conservative)
    val p = TsetlinCtrl.Parameter(tcam, 6)
    assume(Tcam.modelPath(p.tcam).isDefined, s"${p.tcam.name} not released")
    run(p, 20, "macroConservative")
  }
}
