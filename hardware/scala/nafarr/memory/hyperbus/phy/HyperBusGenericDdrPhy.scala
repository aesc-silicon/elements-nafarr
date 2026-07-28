// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.phy

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import nafarr.memory.hyperbus.{HyperBus, HyperBusCtrl}

// Full-rate DDR PHY. ck runs at the domain clock via SoftDdr and two bytes move
// per cycle (rising + falling edge), so there is no clock divider. It is a pure
// slicer over the same word-level command/rdata interface as the serial PHY: the
// controller-built CA and write words are sliced into byte pairs and clocked out,
// and read byte pairs are reassembled into words.
object HyperBusGenericDdrPhy {
  def apply(p: HyperBusCtrl.Parameter) = HyperBusGenericDdrPhy.Phy(p)

  case class Phy(p: HyperBusCtrl.Parameter) extends Component {
    val io = new Bundle {
      val hyperbus = master(HyperBus.Io(p))
      val phy = slave(HyperBus.Phy.Interface(p))
    }

    val dataBytes = p.frontend.dataWidth / 8
    val cyclesPerWord = dataBytes / 2
    val caCycles = 48 / 16

    val phyIsIdle = False

    val reset = new Area {
      val value = RegInit(True)
      val counter = Reg(UInt(log2Up(p.phy.resetWidth) bits)).init(0)
      val pulse = Reg(UInt(log2Up(p.phy.resetPulseWidth) bits))
      val halt = Reg(UInt(log2Up(p.phy.resetHaltWidth) bits))
      val run = RegInit(False)
      val pendingReset = RegInit(False)

      def isReset = run === True

      when(io.phy.config.reset.trigger) {
        pendingReset := True
      }
      when(phyIsIdle && pendingReset) {
        pendingReset := False
        counter := 0
        run := True
      }
      when(run) {
        counter := counter + 1
        when(counter === 0) {
          pulse := io.phy.config.reset.pulse
          halt := io.phy.config.reset.halt
          value := False
        }
        when(counter === pulse) {
          value := True
        }
        when(counter === halt) {
          run := False
        }
      }
      io.hyperbus.reset := value
    }

    // ---- DDR I/O primitives -------------------------------------------------
    // ck is forwarded from the domain clock (rise high, fall low), gated.
    val ckEnable = RegInit(False)
    val ckOut = SoftDdr.Output(1)
    ckOut.io.rise := ckEnable.asBits
    ckOut.io.fall := B"0"
    io.hyperbus.ck := ckOut.io.q(0)

    val dqRise = Bits(8 bits)
    val dqFall = Bits(8 bits)
    val dqOe = RegInit(False)
    dqRise := 0
    dqFall := 0
    val dqOut = SoftDdr.Output(8)
    dqOut.io.rise := dqRise
    dqOut.io.fall := dqFall
    io.hyperbus.dq.write := dqOut.io.q
    io.hyperbus.dq.writeEnable := (default -> dqOe)

    val rwdsRise = Bits(1 bits)
    val rwdsFall = Bits(1 bits)
    val rwdsOe = RegInit(False)
    rwdsRise := B"0"
    rwdsFall := B"0"
    val rwdsOut = SoftDdr.Output(1)
    rwdsOut.io.rise := rwdsRise
    rwdsOut.io.fall := rwdsFall
    io.hyperbus.rwds.write := rwdsOut.io.q(0)
    io.hyperbus.rwds.writeEnable := rwdsOe

    // The DQ/RWDS pads are source-synchronous inputs captured by the DDR IO
    // registers; that async boundary is handled physically (IO cells + timing),
    // not with RTL synchronisers, so declare the crossing intentional.
    val dqIn = SoftDdr.Input(8)
    dqIn.io.d := io.hyperbus.dq.read
    dqIn.io.d.addTag(crossClockDomain)
    val rwdsIn = SoftDdr.Input(1)
    rwdsIn.io.d := io.hyperbus.rwds.read.asBits
    rwdsIn.io.d.addTag(crossClockDomain)
    // The SoftDdr rise path lags the fall path by one cycle; delay the fall
    // samples once more so a byte pair lines up in the same cycle.
    val dqRiseS = dqIn.io.rise
    val dqFallS = RegNext(dqIn.io.fall)
    val rwdsRiseS = rwdsIn.io.rise(0)

    val fsm = new StateMachine {
      val chipSelects = Reg(Bits(p.hyperbus.supportedDevices bits))
        .init(B(p.hyperbus.supportedDevices bits, default -> True))
      val readTransaction = Reg(Bool)
      val additionalLatency = RegInit(False)
      val latencyCycles = Reg(UInt(log2Up(8) bits))
      val burstLen = Reg(UInt(log2Up(p.frontend.storageDepth + 1) bits))

      val setupCycles = 2

      val cycle = Reg(UInt(log2Up(p.phy.transactionWidth + 50) bits)).init(0)
      val caIdx = Reg(UInt(log2Up(caCycles) bits))
      val half = Reg(UInt(log2Up(cyclesPerWord) bits))
      val wordCount = Reg(UInt(log2Up(p.frontend.storageDepth + 1) bits))
      val recvData = Reg(Bits(p.frontend.dataWidth bits))

      io.hyperbus.cs := chipSelects
      dqOe := False
      rwdsOe := False

      val cmdFifo = StreamFifo(HyperBus.Phy.Cmd(p), 12)
      cmdFifo.io.push << io.phy.cmd
      cmdFifo.io.pop.ready := False

      val rdataFifo = StreamFifo(HyperBus.Phy.Rdata(p), p.frontend.storageDepth)
      io.phy.rdata << rdataFifo.io.pop
      rdataFifo.io.push.valid := False
      rdataFifo.io.push.payload.data := 0
      rdataFifo.io.push.payload.last := False
      rdataFifo.io.push.payload.error := False
      rdataFifo.io.push.payload.aborted := False

      def caByte(index: UInt) = cmdFifo.io.pop.argsCa.ca.subdivideIn(8 bits)(index)

      val init: State = new State with EntryPoint {
        whenIsActive {
          phyIsIdle := True
          ckEnable := False
          when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isStart && !reset.isReset) {
            cmdFifo.io.pop.ready := True
            val s = cmdFifo.io.pop.argsStart
            if (p.hyperbus.supportedDevices == 1) {
              chipSelects(0) := False
            } else {
              chipSelects(s.index) := False
            }
            readTransaction := s.read
            latencyCycles := s.latency.resized
            burstLen := s.burstLen
            cycle := 0
            caIdx := 0
            half := 0
            wordCount := 0
            recvData := 0
            goto(chipSelectSetup)
          }
        }
      }

      val chipSelectSetup: State = new State {
        whenIsActive {
          cycle := cycle + 1
          when(cycle === setupCycles) {
            ckEnable := True
            cycle := 0
            goto(ca)
          }
        }
      }

      val ca: State = new State {
        whenIsActive {
          when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isCa) {
            dqOe := True
            dqRise := caByte(U(5) - (caIdx @@ U"0"))
            dqFall := caByte(U(4) - (caIdx @@ U"0"))
            when(caIdx === 1) {
              additionalLatency := rwdsRiseS
            }
            caIdx := caIdx + 1
            when(caIdx === caCycles - 1) {
              cmdFifo.io.pop.ready := True
              cycle := 0
              goto(latency)
            }
          }
        }
      }

      val latency: State = new State {
        whenIsActive {
          val target = (additionalLatency ? (latencyCycles @@ U"0") | latencyCycles.resize(
            latencyCycles.getWidth + 1
          ))
          cycle := cycle + 1
          when(cycle === target) {
            cycle := 0
            half := 0
            when(readTransaction) {
              goto(read)
            } otherwise {
              goto(write)
            }
          }
        }
      }

      // Capture is gated on the device's read strobe (RWDS). RWDS and DQ share
      // the same round-trip delay, so this self-aligns regardless of the exact
      // latency the device inserts.
      val read: State = new State {
        whenIsActive {
          when(rwdsRiseS === True) {
            val assembled = Bits(p.frontend.dataWidth bits)
            assembled := recvData
            assembled.subdivideIn(8 bits)((half @@ U"0")) := dqRiseS
            assembled.subdivideIn(8 bits)((half @@ U"1")) := dqFallS
            recvData := assembled
            half := half + 1
            when(half === cyclesPerWord - 1) {
              half := 0
              rdataFifo.io.push.valid := True
              rdataFifo.io.push.payload.data := assembled
              when(wordCount === burstLen) {
                rdataFifo.io.push.payload.last := True
                goto(end)
              }
              wordCount := wordCount + 1
            }
          }
        }
      }

      val write: State = new State {
        whenIsActive {
          rwdsOe := True
          when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isWrite) {
            val w = cmdFifo.io.pop.argsWrite
            dqOe := True
            dqRise := w.data.subdivideIn(8 bits)((half @@ U"0"))
            dqFall := w.data.subdivideIn(8 bits)((half @@ U"1"))
            rwdsRise := w.mask((half @@ U"0")).asBits
            rwdsFall := w.mask((half @@ U"1")).asBits
            half := half + 1
            when(half === cyclesPerWord - 1) {
              half := 0
              cmdFifo.io.pop.ready := True
              rdataFifo.io.push.valid := True
              rdataFifo.io.push.payload.last := w.last
              when(w.last) {
                goto(end)
              }
            }
          }
        }
      }

      // Hold CS low a few cycles past the last data edge so the round-trip
      // pipeline delay lands inside the device's CS window, then release.
      val endHold = 2
      val end: State = new State {
        onEntry {
          ckEnable := False
          cycle := 0
        }
        whenIsActive {
          cycle := cycle + 1
          when(cycle === endHold) {
            chipSelects := (default -> true)
          }
          when(cycle === endHold + setupCycles) {
            goto(init)
          }
        }
      }
    }
  }
}
