// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.prng

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl
import spinal.crypto.misc.LFSR

import nafarr.IpIdentification

object PrngCtrl {
  def apply(p: Parameter = Parameter()) = PrngCtrl(p)

  case class Parameter()
  object Parameter {
    def default() = Parameter()
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val control = base + 0x00
    val errorPending = base + 0x04
    val errorMask = base + 0x08
    val seed = base + 0x0c
    val output = base + 0x10
  }

  /** Galois LFSR core with enable and external reseed.
    *
    * Advances the 32-bit state by one step per enabled clock cycle using the
    * Galois topology and maximum-period taps (x^32 + x^30 + x^26 + x^25 + 1).
    *
    * The caller is responsible for never asserting io.reseed with a zero seed —
    * a zero state causes the LFSR to lock up permanently.
    *
    * io.seed          : in  — new seed value; sampled when io.reseed is asserted.
    * io.reseed        : in  — pulse to load io.seed into the state register.
    * io.enable        : in  — advance the LFSR each clock cycle when True.
    * io.output        : out — current LFSR state.
    * io.error         : out — combined error signal (OR of masked pending errors).
    * io.pendingErrors : in  — masked pending error bits driven by the Mapper.
    */
  case class PrngCtrl(p: Parameter) extends Component {
    val io = new Bundle {
      val seed = in Bits (32 bits)
      val reseed = in Bool ()
      val enable = in Bool ()
      val output = out Bits (32 bits)
      val error = out Bool ()
      val pendingErrors = in Bits (1 bits)
    }

    val state = Reg(Bits(32 bits)) init (1)

    when(io.reseed) {
      state := io.seed
    } elsewhen (io.enable) {
      state := LFSR.Galois(state, LFSR.taps_32bits)
    }

    io.output := state
    io.error := io.pendingErrors.orR
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: PrngCtrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Prng, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    // Control: bit 0 = enable. PRNG runs freely by default.
    val enable = Reg(Bool()) init (True)
    busCtrl.readAndWrite(enable, regs.control)

    // Error: InterruptCtrl with one source — zero seed write attempt.
    // Pending and mask registers are created at errorPending and errorMask.
    val errCtrl = new InterruptCtrl(1)
    errCtrl.driveFrom(busCtrl, regs.errorPending.toInt)

    val seedFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.seed)

    errCtrl.io.inputs(0) := seedFlow.valid && (seedFlow.payload === 0)
    ctrl.io.pendingErrors := errCtrl.io.pendings

    // Output: read-only, returns current LFSR state.
    busCtrl.read(ctrl.io.output, regs.output)

    ctrl.io.seed := seedFlow.payload
    ctrl.io.reseed := seedFlow.valid && (seedFlow.payload =/= 0)
    ctrl.io.enable := enable
  }
}
