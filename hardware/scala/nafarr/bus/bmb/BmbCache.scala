// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.bmb

import spinal.core._
import spinal.lib._

import spinal.lib.fsm._
import spinal.lib.bus.bmb._
import spinal.lib.bus.misc.SizeMapping

object BmbCache {
  def apply(p: BmbParameter, words: Int) = BmbCache(p, words)

  def getOutputBmbParameter(p: BmbParameter, words: Int) = {
    BmbAccessParameter(
      addressWidth = p.access.addressWidth,
      dataWidth = p.access.dataWidth
    ).addSources(
      1,
      BmbSourceParameter(
        lengthWidth = log2Up(p.access.byteCount * words),
        contextWidth = 0,
        canWrite = false,
        alignment = BmbParameter.BurstAlignement.LENGTH,
        maximumPendingTransaction = 1
      )
    )
  }

  case class BmbCache(p: BmbParameter, words: Int) extends Component {
    val io = new Bundle {
      val input = slave(Bmb(p))
      val output = master(Bmb(getOutputBmbParameter(p, words)))
    }
    require(words > 0 && (words & (words - 1)) == 0, s"words must be a power of 2")

    val offset = log2Up(p.access.byteCount) + log2Up(words)

    val valid = RegInit(False)
    val address = Reg(UInt(io.input.cmd.p.access.addressWidth - offset bit)) init (0)
    val mem = Mem(Bits(p.access.dataWidth bits), words)
    val addressMatch = io.input.cmd.address(offset, address.getWidth bits) === address

    io.input.rsp.data := mem.readSync(
      io.input.cmd.address(log2Up(p.access.byteCount), log2Up(words) bits)
    )
    io.input.rsp.setSuccess()
    io.input.rsp.last := True

    val hit = io.input.cmd.valid && valid && addressMatch
    val miss = io.input.cmd.valid && (!addressMatch || !valid)
    io.input.cmd.ready := hit
    io.input.rsp.valid := RegNext(hit)
    io.input.rsp.source := RegNextWhen(io.input.cmd.source, hit)
    io.input.rsp.context := RegNextWhen(io.input.cmd.context, hit)

    io.output.cmd.valid := False
    io.output.rsp.ready := False
    val fsm = new StateMachine {
      val counter = Reg(UInt(log2Up(words) bits)).init(0)

      val idle: State = new State with EntryPoint {
        whenIsActive {
          when(miss) {
            counter := 0
            goto(cmd)
          }
        }
      }

      val cmd: State = new State {
        whenIsActive {
          io.output.cmd.valid := True
          when(io.output.cmd.fire) {
            goto(rsp)
          }
        }
      }

      val rsp: State = new State {
        whenIsActive {
          when(io.output.rsp.valid) {
            io.output.rsp.ready := True
            counter := counter + 1
            mem.write(counter, io.output.rsp.data)
            when(counter === (words - 1)) {
              address := io.input.cmd.payload.address(offset, address.getWidth bits)
              valid := True
              goto(idle)
            }
          }
        }
      }
    }

    io.output.cmd.payload.source := 0
    io.output.cmd.payload.address := (io.input.cmd.payload
      .address(offset, address.getWidth bits) ## B(offset bits, default -> False)).asUInt
    io.output.cmd.payload.length := (p.access.byteCount * words) - 1
    io.output.cmd.setRead()
    io.output.cmd.last := True
  }
}
