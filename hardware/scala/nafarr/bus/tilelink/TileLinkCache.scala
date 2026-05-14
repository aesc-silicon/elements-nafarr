// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.tilelink

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}

/** Single-line direct-mapped read-only cache.
  *
  * Inner bus: TL-UL slave (one beat per transaction, from the CPU side).
  * Outer bus: TL-UH master (burst GET of `words` beats, to the memory side).
  *
  * On a hit  the response is returned from the internal Mem with one cycle of
  * read latency.  On a miss the FSM issues a burst GET on the outer bus, fills
  * the line word-by-word, then returns to idle so the pending inner request can
  * be served as a hit.
  *
  * @param p     Inner-bus TileLink parameter (TL-UL, sizeBytes = dataBytes).
  * @param words Number of data words in the cache line; must be a power of 2.
  */
object TileLinkCache {

  def getOuterParameter(p: TileLinkParameter, words: Int): TileLinkParameter =
    TileLinkParameter.simple(
      addressWidth = p.addressWidth,
      dataWidth = p.dataWidth,
      sizeBytes = p.dataBytes * words,
      sourceWidth = 1
    )

  case class Cache(p: TileLinkParameter, words: Int) extends Component {
    require(words > 0 && (words & (words - 1)) == 0, "words must be a power of 2")

    val outerP = getOuterParameter(p, words)

    val io = new Bundle {
      val inner = slave(TileLinkBus(p))
      val outer = master(TileLinkBus(outerP))
    }

    // -----------------------------------------------------------------------
    // Address decomposition
    //   [ tag | index | byteOffset ]
    //                               ^-- byteOffsetBits --^
    //          ^-- indexBits --^
    //  ^------------ lineBits ----------------^
    // -----------------------------------------------------------------------
    val byteOffsetBits = p.dataBytesLog2Up
    val indexBits = log2Up(words)
    val lineBits = byteOffsetBits + indexBits

    val inAddr = io.inner.a.address
    val inIndex = inAddr(byteOffsetBits, indexBits bits)
    val inTag = inAddr(lineBits, p.addressWidth - lineBits bits)

    // -----------------------------------------------------------------------
    // Cache storage: one line of `words` words + tag + valid
    // -----------------------------------------------------------------------
    val valid = RegInit(False)
    val tag = Reg(UInt(p.addressWidth - lineBits bits)) init 0
    val mem = Mem(Bits(p.dataWidth bits), words)

    val tagMatch = valid && (inTag === tag)

    // Do not accept a new inner request while a D-channel response is still
    // in flight (dValid high but not yet consumed).
    val dFree = !io.inner.d.valid || io.inner.d.ready

    val hit = io.inner.a.valid && tagMatch && dFree
    val miss = io.inner.a.valid && !tagMatch

    io.inner.a.ready := hit

    // -----------------------------------------------------------------------
    // Inner D channel
    // mem.readSync has one cycle of latency: register response metadata on
    // the hit cycle so that dValid and the RAM output are aligned.
    // -----------------------------------------------------------------------
    val dValid = RegInit(False)
    val dSource = Reg(p.source())
    val dSize = Reg(p.size())

    when(hit) {
      dValid := True
      dSource := io.inner.a.source
      dSize := io.inner.a.size
    } elsewhen (io.inner.d.ready) {
      dValid := False
    }

    io.inner.d.valid := dValid
    io.inner.d.opcode := Opcode.D.ACCESS_ACK_DATA()
    io.inner.d.param := 0
    io.inner.d.size := dSize
    io.inner.d.source := dSource
    io.inner.d.sink := 0
    io.inner.d.denied := False
    io.inner.d.data := mem.readSync(inIndex)
    io.inner.d.corrupt := False

    // -----------------------------------------------------------------------
    // Outer bus defaults (overridden by FSM states)
    // -----------------------------------------------------------------------
    // Line-aligned base address: zero the lower lineBits of inAddr.
    val lineBase = (inTag.asBits ## B(lineBits bits, default -> false)).asUInt

    io.outer.a.valid := False
    io.outer.a.opcode := Opcode.A.GET()
    io.outer.a.param := 0
    io.outer.a.size := U(outerP.sizeMax, outerP.sizeWidth bits)
    io.outer.a.source := 0
    io.outer.a.address := lineBase
    io.outer.a.mask := B(outerP.dataBytes bits, default -> true)
    io.outer.a.data := 0
    io.outer.a.corrupt := False
    io.outer.d.ready := False

    // -----------------------------------------------------------------------
    // Miss-fill FSM
    // -----------------------------------------------------------------------
    val fsm = new StateMachine {
      val counter = Reg(UInt(indexBits bits)) init 0

      val idle: State = new State with EntryPoint {
        whenIsActive {
          when(miss) {
            counter := 0
            valid := False
            goto(sendReq)
          }
        }
      }

      // Issue a single-beat burst GET on the outer bus.
      val sendReq: State = new State {
        whenIsActive {
          io.outer.a.valid := True
          when(io.outer.a.ready) {
            goto(recvData)
          }
        }
      }

      // Consume D-channel beats from the outer bus and fill the cache line.
      val recvData: State = new State {
        whenIsActive {
          io.outer.d.ready := True
          when(io.outer.d.valid) {
            mem.write(counter, io.outer.d.data)
            counter := counter + 1
            when(counter === (words - 1)) {
              tag := inTag
              valid := True
              goto(idle)
            }
          }
        }
      }
    }
  }
}
