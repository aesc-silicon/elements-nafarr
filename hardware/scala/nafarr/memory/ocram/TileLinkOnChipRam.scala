// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.ocram

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}

/** Generic single-port synchronous on-chip RAM, TileLink burst-capable.
  *
  * Serves multi-beat cache-line transfers up to the bus parameter's sizeBytes, so it can back a
  * cached (D$) master - not just single-word TL-UL:
  *   Get             : one A beat  -> N AccessAckData beats (address increments per beat)
  *   Put{Full,Partial}: N A beats  -> one AccessAck
  * Read latency is one cycle; one message is served at a time (a.ready is held low until the
  * response is complete). Byte-enable writes via the A-channel mask.
  *
  * @param p    TileLink bus parameter (sizeBytes = max transfer, dataBytes = beat width).
  * @param size RAM size in bytes; power of 2.
  */
case class TileLinkOnChipRam(p: TileLinkParameter, size: BigInt, singlePort: Boolean = true)
    extends Component {
  val io = new Bundle {
    val bus = slave(TileLinkBus(p))
  }

  val words = (size / p.dataBytes).toInt
  val ram = Mem(Bits(p.dataWidth bits), words)

  def rams: Seq[Component] = {
    val result = scala.collection.mutable.ArrayBuffer[Component]()
    this.walkComponents { c =>
      if (c.getName() == ram.getName()) result += c
    }
    result.toSeq
  }

  val a = io.bus.a
  val d = io.bus.d

  val maxBeats = (p.sizeBytes / p.dataBytes).toInt
  val beatCntWidth = log2Up(maxBeats + 1)
  def beatsOf(sz: UInt): UInt =
    (((U(1, 10 bits) |<< sz) + (p.dataBytes - 1)) >> p.dataBytesLog2Up).resize(beatCntWidth)
  def toWord(byteAddr: UInt): UInt = (byteAddr >> p.dataBytesLog2Up).resize(log2Up(words))

  val source = Reg(p.source())
  val reqSize = Reg(p.size())
  val cursor = Reg(UInt(log2Up(words) bits)) // next word to read/write
  val issueLeft = Reg(
    UInt(beatCntWidth bits)
  ) // read addrs still to issue / write beats still to take
  val emitLeft = Reg(UInt(beatCntWidth bits)) // read D beats still to emit

  val wrEnable = False
  val rwAddr = cursor.clone()
  rwAddr := cursor

  val readCmd = Stream(UInt(log2Up(words) bits))
  readCmd.valid := False
  readCmd.payload := cursor

  // Read and write never overlap, so a single port suffices and maps onto 1P
  // SRAM macros. The two-port variant needs a matching 2P macro.
  val readRsp = if (singlePort) {
    val rsp = Stream(Bits(p.dataWidth bits))
    val hold = Reg(Bits(p.dataWidth bits))
    val holdValid = RegInit(False)
    readCmd.ready := !holdValid || rsp.ready
    val issued = RegNext(readCmd.fire) init (False)
    val rdData = ram.readWriteSync(rwAddr, a.data, wrEnable || readCmd.fire, wrEnable, a.mask)
    when(issued) {
      hold := rdData
      holdValid := True
    } elsewhen (rsp.ready) {
      holdValid := False
    }
    rsp.valid := holdValid
    rsp.payload := hold
    rsp
  } else {
    ram.write(rwAddr, a.data, wrEnable, a.mask)
    ram.streamReadSync(readCmd)
  }
  readRsp.ready := False

  a.ready := False
  d.valid := False
  d.opcode := Opcode.D.ACCESS_ACK()
  d.param := 0
  d.size := reqSize
  d.source := source
  d.sink := 0
  d.denied := False
  d.data := readRsp.payload
  d.corrupt := False

  val fsm = new StateMachine {
    val idle: State = new State with EntryPoint
    val read: State = new State
    val write: State = new State
    val writeResp: State = new State

    idle.whenIsActive {
      a.ready := True
      when(a.fire) {
        source := a.source
        reqSize := a.size
        cursor := toWord(a.address)
        when(a.opcode === Opcode.A.GET()) {
          issueLeft := beatsOf(a.size)
          emitLeft := beatsOf(a.size)
          goto(read)
        } otherwise {
          wrEnable := True
          rwAddr := toWord(a.address)
          cursor := toWord(a.address) + 1
          issueLeft := beatsOf(a.size) - 1
          when(beatsOf(a.size) === 1) {
            goto(writeResp)
          } otherwise {
            goto(write)
          }
        }
      }
    }

    read.whenIsActive {
      readCmd.valid := issueLeft =/= 0
      when(readCmd.fire) {
        cursor := cursor + 1
        issueLeft := issueLeft - 1
      }
      d.valid := readRsp.valid
      d.opcode := Opcode.D.ACCESS_ACK_DATA()
      readRsp.ready := d.ready
      when(d.fire) {
        emitLeft := emitLeft - 1
        when(emitLeft === 1) {
          goto(idle)
        }
      }
    }

    write.whenIsActive {
      a.ready := True
      when(a.fire) {
        wrEnable := True
        cursor := cursor + 1
        issueLeft := issueLeft - 1
        when(issueLeft === 1) {
          goto(writeResp)
        }
      }
    }

    writeResp.whenIsActive {
      d.valid := True
      d.opcode := Opcode.D.ACCESS_ACK()
      when(d.fire) {
        goto(idle)
      }
    }
  }
}
