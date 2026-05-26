// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.ocram.ihp.sg13g2

import spinal.core._
import spinal.lib._
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}

import nafarr.blackboxes.ihp.sg13g2._

object TileLinkIhpOnChipRam {

  // Largest available 32-bit macro (1024x32 = 4 kB).
  // When size exceeds this, multiple macros are banked.
  private val bankSize = 4096

  case class OnePort(p: TileLinkParameter, size: Int) extends Component {
    private val bankCount = size / bankSize
    require(
      size > 0 && size % bankSize == 0,
      s"size must be a positive multiple of $bankSize bytes, got $size"
    )

    val io = new Bundle {
      val bus = slave(TileLinkBus(p))
    }

    val rams = Seq.tabulate(bankCount) { i =>
      val ram = Memory(32, bankSize)
      ram.setName(s"ram_bank$i")
      ram.connectDefaults()
      ram
    }

    val a = io.bus.a
    val d = io.bus.d

    val request = new Area {
      // Accept a new request only when the D channel is free
      val dFree = !d.valid || d.ready
      a.ready := dFree

      val fire = a.valid && a.ready
      val isWrite = a.opcode =/= Opcode.A.GET

      // Word address: strip the byte-offset bits from the TileLink byte address
      private val bankWordAddrWidth = log2Up(bankSize / 4)
      val wordAddr = (a.address >> p.dataBytesLog2Up).resize(log2Up(size / 4))
      val bankWordAddr = wordAddr(bankWordAddrWidth - 1 downto 0).asBits
      val bankSel =
        if (bankCount > 1)
          (wordAddr >> bankWordAddrWidth).resize(log2Up(bankCount))
        else
          null
    }

    val macros = new Area {
      for ((ram, i) <- rams.zipWithIndex) {
        ram.A_ADDR := request.bankWordAddr
        ram.A_DIN := a.data
        ram.A_WEN := request.isWrite
        ram.A_REN := !request.isWrite
        ram.A_BM := Cat(a.mask.asBools.map(_ #* 8))
        ram.A_MEN := request.fire && (
          if (bankCount > 1) request.bankSel === i else True
        )
      }
    }

    // Register response metadata to absorb the 1-cycle RAM read latency.
    // Also register the bank select so it aligns with A_DOUT.
    val response = new Area {
      val dValid = RegInit(False).setName("valid")
      val dOpcode = Reg(Opcode.D()).setName("opcode")
      val dSource = Reg(p.source()).setName("source")
      val dSize = Reg(p.size()).setName("size")
      val dBankSel =
        if (bankCount > 1)
          Reg(UInt(log2Up(bankCount) bits)).init(0).setName("bankSel")
        else
          null

      when(request.fire) {
        dValid := True
        dOpcode := Mux(request.isWrite, Opcode.D.ACCESS_ACK(), Opcode.D.ACCESS_ACK_DATA())
        dSource := a.source
        dSize := a.size
        if (bankCount > 1) dBankSel := request.bankSel
      } elsewhen (d.ready) {
        dValid := False
      }
    }

    d.valid := response.dValid
    d.opcode := response.dOpcode
    d.param := 0
    d.size := response.dSize
    d.source := response.dSource
    d.sink := 0
    d.denied := False
    d.data := (if (bankCount > 1) Vec(rams.map(_.A_DOUT))(response.dBankSel) else rams.head.A_DOUT)
    d.corrupt := False
  }
}
