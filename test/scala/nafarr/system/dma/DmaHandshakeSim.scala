// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.dma

import spinal.core._
import spinal.core.sim._

object DmaHandshakeSim {

  /** Releases `ack` of every handshake of a peripheral under test. */
  def release(requests: DmaRequest*): Unit = {
    for (r <- requests) {
      r.tx.ack #= false
      r.rx.ack #= false
    }
  }

  /** Peripheral model: drives `req` from `cond` on every sampling of `cd`, like
    * [[DmaHandshake.drive]].
    */
  def drive(hs: DmaHandshake, cd: ClockDomain)(cond: => Boolean): Unit = {
    hs.req #= false
    cd.onSamplings {
      hs.req #= cond && !hs.ack.toBoolean
    }
  }

  /** Checks the peripheral side of a pending request: `req` drops while `ack` is set and
    * returns once `ack` is released.
    */
  def checkAck(hs: DmaHandshake, cd: ClockDomain, name: String): Unit = {
    assert(hs.req.toBoolean, s"$name: req low before ack")
    hs.ack #= true
    cd.waitSampling(3)
    assert(!hs.req.toBoolean, s"$name: req high while ack is set")
    hs.ack #= false
    cd.waitSampling(3)
    assert(hs.req.toBoolean, s"$name: req low after ack released")
  }
}
