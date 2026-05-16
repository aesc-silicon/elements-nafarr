// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.semaphore

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory

import nafarr.IpIdentification

object SemaphoreCtrl {

  case class Parameter(count: Int = 8) {
    require(count > 0 && count <= 32, "Semaphore count must be between 1 and 32")
  }

  object Regs {
    def apply(base: BigInt, count: Int) = new Regs(base, count)
  }

  class Regs(base: BigInt, count: Int) {
    /** Read-only bitmask of all taken slots; bit N = semaphore N taken. */
    val status = base + 0x00

    /** Per-semaphore register: read to claim, write to release. */
    def semaphore(i: Int): BigInt = {
      require(i >= 0 && i < count, s"Semaphore index $i out of range [0, $count)")
      base + 0x04 + i * 0x04
    }
  }

  /** Register-map frontend for SemaphoreCtrl.
    *
    * Wires a BusSlaveFactory to the claim/release/taken ports of a
    * SemaphoreCtrl component.  Instantiate this inside the parent Component
    * that owns both the bus and the ctrl subcomponent.
    */
  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: SemaphoreCtrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Semaphore, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)

    val regs = SemaphoreCtrl.Regs(idCtrl.length, p.count)

    // Intermediate signals — default to no-op; overridden per-slot below.
    val claimVec   = Vec(Bool(), p.count)
    val releaseVec = Vec(Bool(), p.count)

    for (i <- 0 until p.count) {
      claimVec(i)   := False
      releaseVec(i) := False

      // Read returns the state BEFORE the claim (old value) because
      // ctrl.io.taken reflects the registered state; the claim register
      // update happens at the next clock edge.
      busCtrl.read(ctrl.io.taken(i).asUInt.resize(32), regs.semaphore(i))
      busCtrl.onRead(regs.semaphore(i))  { claimVec(i)   := True }
      busCtrl.onWrite(regs.semaphore(i)) { releaseVec(i) := True }
    }

    busCtrl.read(ctrl.io.taken.resize(32), regs.status)

    ctrl.io.claim   := claimVec.asBits
    ctrl.io.release := releaseVec.asBits
  }
}

/** Hardware semaphore controller — no bus logic.
  *
  * Manages `p.count` semaphore slots as a registered bitmask.  Claim and
  * release are combinatorial inputs; the state updates at every clock edge.
  * Release takes priority over simultaneous claim on the same slot.
  *
  * This component can be driven by any frontend:
  *   - SemaphoreCtrl.Mapper  — memory-mapped register access
  *   - A custom I2C-device frontend — claim/release via I2C commands
  *   - Direct wiring         — software-managed from an Area
  *
  * io.claim   : in  — bitmask of slots to claim this cycle.
  * io.release : in  — bitmask of slots to release this cycle.
  * io.taken   : out — registered bitmask of currently taken slots.
  */
case class SemaphoreCtrl(p: SemaphoreCtrl.Parameter) extends Component {
  val io = new Bundle {
    val claim   = in  Bits(p.count bits)
    val release = in  Bits(p.count bits)
    val taken   = out Bits(p.count bits)
  }

  val state = Reg(Bits(p.count bits)) init 0
  // Release takes priority: a simultaneous claim+release on the same slot
  // leaves it free.
  state   := (state | io.claim) & ~io.release
  io.taken := state
}
