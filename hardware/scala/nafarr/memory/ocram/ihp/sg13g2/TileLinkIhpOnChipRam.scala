// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.ocram.ihp.sg13g2

import spinal.core._
import spinal.lib._
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}

import nafarr.blackboxes.ihp.sg13g2._

object TileLinkIhpOnChipRam {

  case class OnePort1Macro(p: TileLinkParameter, size: Int) extends Component {
    val io = new Bundle {
      val bus = slave(TileLinkBus(p))
    }

    val ram = Memory(32, size)
    ram.connectDefaults()

    val a = io.bus.a
    val d = io.bus.d

    // Accept a new request only when the D channel is free
    val dFree = !d.valid || d.ready
    a.ready := dFree

    val aFire = a.valid && a.ready
    val isWrite = a.opcode =/= Opcode.A.GET

    // RAM: word-addressed; strip the byte-offset bits from the TileLink byte address
    ram.A_ADDR := (a.address >> p.dataBytesLog2Up).resize(log2Up(size / (32 / 8))).asBits
    ram.A_DIN := a.data
    ram.A_MEN := aFire
    ram.A_WEN := isWrite
    ram.A_REN := !isWrite
    ram.A_BM := Cat(a.mask.asBools.map(_ #* 8))

    // Register response metadata to absorb the 1-cycle RAM read latency
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
    d.data := ram.A_DOUT
    d.corrupt := False
  }
}
