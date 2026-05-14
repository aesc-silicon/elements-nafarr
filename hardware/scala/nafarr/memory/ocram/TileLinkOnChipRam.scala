// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.ocram

import spinal.core._
import spinal.lib._
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}

/** Generic single-port synchronous on-chip RAM.
  *
  * Accepts TL-UL requests (sizeBytes = dataBytes).  Read latency is one cycle
  * (SpinalHDL synchronous Mem).  Only one request is outstanding at a time:
  * a.ready is held low while a D-channel response is in flight.
  *
  * Byte-enable writes are supported via the A-channel mask field.
  *
  * @param p    TileLink bus parameter (TL-UL; sizeBytes must equal dataBytes).
  * @param size RAM size in bytes; must be a power of 2.
  */
case class TileLinkOnChipRam(p: TileLinkParameter, size: BigInt) extends Component {
  val io = new Bundle {
    val bus = slave(TileLinkBus(p))
  }

  val words = (size / p.dataBytes).toInt
  val ram = Mem(Bits(p.dataWidth bits), words)

  val a = io.bus.a
  val d = io.bus.d

  // Accept a new request only when the D channel is free (no pending response).
  val dFree = !d.valid || d.ready
  a.ready := dFree

  val aFire = a.valid && a.ready
  val isWrite = a.opcode =/= Opcode.A.GET()
  val wordAddr = (a.address >> p.dataBytesLog2Up).resize(log2Up(words))

  // Byte-masked synchronous write.
  ram.write(wordAddr, a.data, aFire && isWrite, a.mask)

  // Register D-channel metadata to absorb the 1-cycle read latency.
  val dValid = RegInit(False)
  val dOpcode = Reg(Opcode.D())
  val dSource = Reg(p.source())
  val dSize = Reg(p.size())

  when(aFire) {
    dValid := True
    dOpcode := Mux(isWrite, Opcode.D.ACCESS_ACK(), Opcode.D.ACCESS_ACK_DATA())
    dSource := a.source
    dSize := a.size
  } elsewhen (d.ready) {
    dValid := False
  }

  d.valid := dValid
  d.opcode := dOpcode
  d.param := 0
  d.size := dSize
  d.source := dSource
  d.sink := 0
  d.denied := False
  d.data := ram.readSync(wordAddr, enable = aFire && !isWrite)
  d.corrupt := False
}
