// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.dma

import spinal.core._
import spinal.lib._

/** Four-phase DMA request handshake, driven by the peripheral (master).
  *
  *   1. The peripheral raises `req` while it can move one element.
  *   2. The DMA moves one element and waits for the bus response.
  *   3. The DMA raises `ack`; the peripheral drops `req` while `ack` is high.
  *   4. The DMA drops `ack` once it sees `req` low; `req` then follows the peripheral
  *      state again, which already reflects the completed access.
  *
  * Both signals hold their level until the other side responds, so they cross clock
  * domains through two-flop synchronizers ([[DmaHandshakeCc]]).
  */
case class DmaHandshake() extends Bundle with IMasterSlave {
  val req = Bool()
  val ack = Bool()

  // Inputs default to False so that unconnected handshakes need no wiring.
  override def asMaster(): Unit = {
    out(req)
    in(ack)
    ack.default(False)
  }

  override def asSlave(): Unit = {
    in(req)
    out(ack)
    req.default(False)
  }

  /** Peripheral side: request while `cond` holds, hold off while acknowledged. */
  def drive(cond: Bool): Unit = {
    req := RegNext(cond && !ack, init = False)
  }
}

/** DMA request handshakes of a FIFO-backed IP.
  *
  * tx: the write-side FIFO can accept one element (DMA memory -> peripheral).
  * rx: the read-side FIFO holds at least one element (DMA peripheral -> memory).
  */
case class DmaRequest() extends Bundle with IMasterSlave {
  val tx = DmaHandshake()
  val rx = DmaHandshake()

  override def asMaster(): Unit = {
    master(tx)
    master(rx)
  }

  def drive(txCond: Bool, rxCond: Bool): Unit = {
    tx.drive(txCond)
    rx.drive(rxCond)
  }
}

/** Synchronizes one [[DmaHandshake]] between the peripheral and the DMA clock domain.
  *
  * The DMA drops `ack` as soon as it sees `req` low, which can be before the peripheral saw
  * `ack` at all if the access itself cleared `req`. A late `ack` pulse would then produce a
  * `req` low that the DMA takes as the acknowledgement of its next element. The DMA side
  * therefore sees `req` high until `ack` has reached the peripheral domain and come back.
  */
case class DmaHandshakeCc(peripheralCd: ClockDomain, dmaCd: ClockDomain) extends Component {
  val io = new Bundle {
    val peripheral = slave(DmaHandshake())
    val dma = master(DmaHandshake())
  }

  val ackPeripheral = peripheralCd(BufferCC(io.dma.ack, False))
  io.peripheral.ack := ackPeripheral

  val dmaArea = new ClockingArea(dmaCd) {
    val req = BufferCC(io.peripheral.req, False)
    val ackEcho = BufferCC(ackPeripheral, False)
    io.dma.req := req || (io.dma.ack && !ackEcho)
  }
}
