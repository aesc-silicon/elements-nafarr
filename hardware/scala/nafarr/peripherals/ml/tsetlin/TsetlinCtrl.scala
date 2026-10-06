// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.ml.tsetlin

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import nafarr.IpIdentification
import nafarr.blackboxes.muninn.Tcam

/** Tsetlin machine inference on Muninn clause arrays.
  *
  * Each row of the clause array is one clause. Rows are grouped by class, class-major,
  * `clausesPerClass` rows each; rows behind the last class are unused. Inside a class
  * even rows vote for it and odd rows against it. A search sums the votes per class
  * and picks the class with the highest sum (lowest index on ties).
  *
  * Several macros side by side (`tiles`) form wider clauses: their MATCH outputs are
  * ANDed. With `validColumn`, literal 0 is held at 0 during a search; a clause with
  * include bit 0 set never matches, which marks empty or unused clauses.
  */
object TsetlinCtrl {
  def apply(p: Parameter) = TsetlinCtrl(p)

  case class Parameter(
      tcam: Tcam.Parameter,
      classes: Int,
      tiles: Int = 1,
      validColumn: Boolean = true,
      perClass: Int = 0 // clauses per class; 0: as many as fit, rounded down to even
  ) {
    val rows = tcam.rows
    val literals = tcam.cols * tiles
    require(classes >= 1)
    val clausesPerClass = if (perClass > 0) perClass else rows / classes / 2 * 2
    require(
      clausesPerClass >= 2 && clausesPerClass % 2 == 0,
      "every class needs as many positive as negative clauses"
    )
    require(classes * clausesPerClass <= rows, "classes do not fit into the rows")
    require(tiles >= 1 && tiles <= 255)
    require(literals <= 2048 && rows <= 2048 && classes <= 256, "exceeds the register windows")
    val sumBits = log2Up(clausesPerClass / 2 + 1) + 1
    val literalWords = (literals + 31) / 32
    val rowWords = (rows + 31) / 32
  }
  object Parameter {
    def default(
        rows: Int = 64,
        cols: Int = 80,
        classes: Int = 4,
        hard: Boolean = true,
        variant: Tcam.Variant = Tcam.Variant.Dense
    ) = Parameter(Tcam.Parameter(rows, cols, hard, variant), classes)
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }

  /** Fixed windows keep the C register struct independent of the configuration. */
  class Regs(base: BigInt) {
    val array = base + 0x00 // [31:16] literals, [15:0] rows
    val classes = base + 0x04 // [31:16] clauses per class, [15:0] classes
    val options = base + 0x08 // [8] valid column, [7:0] tiles
    val ctrl = base + 0x0c // [1] irq enable, [0] start (write 1)
    val status = base + 0x10 // [1] done (write 1 to clear), [0] busy
    val row = base + 0x14 // row address for the next commit
    val commit = base + 0x18 // write: row buffer -> clause array row
    val result = base + 0x1c // [31:16] winning sum (signed), [15:0] winning class
    def data(word: Int) = 0x100 + 4 * word // row buffer
    def literal(word: Int) = 0x200 + 4 * word
    def matches(word: Int) = 0x300 + 4 * word
    def sum(cls: Int) = 0x400 + 4 * cls
  }

  case class Config(p: Parameter) extends Bundle {
    val rowData = Bits(p.literals bits)
    val literals = Bits(p.literals bits)
    val row = UInt(p.tcam.addressBits bits)
    val commit = Bool()
    val start = Bool()
  }

  case class Status(p: Parameter) extends Bundle {
    val busy = Bool()
    val done = Bool() // one cycle when a search has finished
    val matches = Bits(p.rows bits)
    val sums = Vec(SInt(p.sumBits bits), p.classes)
    val winner = UInt(16 bits)
    val winnerSum = SInt(p.sumBits bits)
  }

  case class Io(p: Parameter) extends Bundle {
    val config = in(Config(p))
    val status = out(Status(p))
  }

  case class TsetlinCtrl(p: Parameter) extends Component {
    val io = Io(p)

    val tiles = (0 until p.tiles).map(_ => Tcam(p.tcam))

    // all macro inputs come from registers (see the timing in Tcam); row data and literals
    // are the bus registers, which only change on bus writes and so never in the cycle of a
    // commit or start
    val searchEnable = RegInit(False)
    val writeEnable = RegInit(False)
    val address = Reg(UInt(p.tcam.addressBits bits)) init (0)
    val literals = CombInit(io.config.literals)
    if (p.validColumn) literals(0) := False
    for ((t, i) <- tiles.zipWithIndex) {
      t.searchEnable := searchEnable
      t.writeEnable := writeEnable
      t.address := address
      t.writeData := io.config.rowData(i * p.tcam.cols, p.tcam.cols bits)
      t.literals := literals(i * p.tcam.cols, p.tcam.cols bits)
    }
    val clauses = tiles.map(_.matches).reduce(_ & _)

    val matches = Reg(Bits(p.rows bits)) init (0)
    val sums = Reg(Vec(SInt(p.sumBits bits), p.classes))
    sums.foreach(_.init(0))
    val winner = Reg(UInt(16 bits)) init (0)
    val winnerSum = Reg(SInt(p.sumBits bits)) init (0)
    val cls = Reg(UInt(log2Up(p.classes + 1) bits)) init (0)

    object State extends SpinalEnum {
      val Idle, Search, Sum, Argmax, Done = newElement()
    }
    val state = RegInit(State.Idle)
    writeEnable := False
    searchEnable := False

    switch(state) {
      is(State.Idle) {
        when(io.config.commit) {
          writeEnable := True
          address := io.config.row
        } elsewhen (io.config.start) {
          searchEnable := True
          state := State.Search
        }
      }
      is(State.Search) {
        // the search ran in the previous cycle; its result is valid on this edge
        matches := clauses
        state := State.Sum
      }
      is(State.Sum) {
        for (c <- 0 until p.classes) {
          val group = matches(c * p.clausesPerClass, p.clausesPerClass bits)
          val positive = CountOne((0 until p.clausesPerClass by 2).map(group(_)))
          val negative = CountOne((1 until p.clausesPerClass by 2).map(group(_)))
          sums(c) := (positive.resize(p.sumBits).asSInt - negative.resize(p.sumBits).asSInt)
        }
        winner := 0
        winnerSum := S(-(1 << (p.sumBits - 1)), p.sumBits bits)
        cls := 0
        state := State.Argmax
      }
      is(State.Argmax) {
        when(sums(cls.resized) > winnerSum) {
          winner := cls.resized
          winnerSum := sums(cls.resized)
        }
        cls := cls + 1
        when(cls === p.classes - 1) {
          state := State.Done
        }
      }
      is(State.Done) {
        state := State.Idle
      }
    }

    io.status.busy := state =/= State.Idle
    io.status.done := state === State.Done
    io.status.matches := matches
    io.status.sums := sums
    io.status.winner := winner
    io.status.winnerSum := winnerSum
  }

  case class Mapper(busCtrl: BusSlaveFactory, ctrl: Io, p: Parameter) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Tsetlin, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    busCtrl.read(B(p.literals, 16 bits) ## B(p.rows, 16 bits), regs.array)
    busCtrl.read(B(p.clausesPerClass, 16 bits) ## B(p.classes, 16 bits), regs.classes)
    busCtrl.read(Bool(p.validColumn) ## B(p.tiles, 8 bits), regs.options)

    val writeData = Bits(32 bits)
    busCtrl.nonStopWrite(writeData, 0)
    ctrl.config.start := False
    busCtrl.onWrite(regs.ctrl) {
      ctrl.config.start := writeData(0)
    }
    val irqEnable = busCtrl.createReadAndWrite(Bool(), regs.ctrl, 1) init (False)

    val done = RegInit(False) setWhen (ctrl.status.done)
    busCtrl.read(ctrl.status.busy, regs.status, 0)
    busCtrl.read(done, regs.status, 1)
    busCtrl.onWrite(regs.status) {
      when(writeData(1)) { done := False }
    }
    val interrupt = done && irqEnable

    busCtrl.driveAndRead(ctrl.config.row, regs.row) init (0)
    ctrl.config.commit := False
    busCtrl.onWrite(regs.commit) { ctrl.config.commit := True }
    busCtrl.read(
      ctrl.status.winnerSum.resize(16).asBits ## ctrl.status.winner.asBits,
      regs.result
    )

    val rowData = Reg(Bits(p.literals bits)) init (0)
    val literals = Reg(Bits(p.literals bits)) init (0)
    for (w <- 0 until p.literalWords) {
      val bits = Math.min(32, p.literals - 32 * w)
      busCtrl.driveAndRead(rowData(32 * w, bits bits), regs.data(w))
      busCtrl.driveAndRead(literals(32 * w, bits bits), regs.literal(w))
    }
    ctrl.config.rowData := rowData
    ctrl.config.literals := literals
    for (w <- 0 until p.rowWords) {
      val bits = Math.min(32, p.rows - 32 * w)
      busCtrl.read(ctrl.status.matches(32 * w, bits bits), regs.matches(w))
    }
    for (c <- 0 until p.classes) {
      busCtrl.read(ctrl.status.sums(c).resize(32).asBits, regs.sum(c))
    }
  }
}
