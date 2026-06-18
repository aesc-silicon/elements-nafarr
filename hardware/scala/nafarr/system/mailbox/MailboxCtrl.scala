// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.mailbox

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl

import nafarr.IpIdentification

object MailboxCtrl {
  def apply(p: Parameter = Parameter.medium()) = MailboxCtrl(p)

  case class Parameter(depth: Int = 8, channelCount: Int = 2) {
    require(depth > 0 && depth < 256, "Mailbox FIFO depth must be between 1 and 255")
    require(channelCount >= 2, "Mailbox must have at least 2 channels")
  }
  object Parameter {
    def small() = Parameter(depth = 4)
    def medium() = Parameter(depth = 8)
    def large() = Parameter(depth = 16)
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val info = base + 0x00
    val status = base + 0x04
    private def channelBase(ch: Int) = base + 0x08 + ch * 0x14
    def write(ch: Int) = channelBase(ch) + 0x00
    def read(ch: Int) = channelBase(ch) + 0x04
    def occupancy(ch: Int) = channelBase(ch) + 0x08
    def interruptPending(ch: Int) = channelBase(ch) + 0x0c
    def interruptMask(ch: Int) = channelBase(ch) + 0x10
  }

  /** Hardware mailbox controller.
    *
    * Provides `p.channelCount` symmetric FIFO channels for inter-CPU messaging.
    * Each channel has an independent push/pop stream and occupancy counter.
    *
    * Convention: CPU A sends on channel 0 and receives on channel 1.
    *             CPU B sends on channel 1 and receives on channel 0.
    *
    * io.push            : in  - stream of messages pushed into each channel FIFO.
    * io.pop             : out - stream of messages popped from each channel FIFO.
    * io.occupancy       : out - number of entries currently stored per channel.
    * io.interrupt        : out - single interrupt, asserted when any channel has a
    *                              masked pending source (OR of all channel IRQ bits).
    * io.pendingInterrupts: in  - masked pending interrupt bits per channel, driven by Mapper.
    */
  case class MailboxCtrl(p: Parameter) extends Component {
    val io = new Bundle {
      val push = Vec(slave(Stream(Bits(32 bits))), p.channelCount)
      val pop = Vec(master(Stream(Bits(32 bits))), p.channelCount)
      val occupancy = out(Vec(UInt(log2Up(p.depth + 1) bits), p.channelCount))
      val interrupt = out Bool ()
      val pendingInterrupts = in(Vec(Bits(2 bits), p.channelCount))
    }

    for (ch <- 0 until p.channelCount) {
      val fifo = StreamFifo(Bits(32 bits), p.depth)
      fifo.io.push << io.push(ch)
      io.pop(ch) << fifo.io.pop
      io.occupancy(ch) := fifo.io.occupancy
    }

    io.interrupt := io.pendingInterrupts.map(_.orR).reduce(_ || _)
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: MailboxCtrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Mailbox, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    busCtrl.read(B(0, 16 bits) ## B(p.depth, 8 bits) ## B(p.channelCount, 8 bits), regs.info)

    val emptyBits = Bits(p.channelCount bits)
    val fullBits = Bits(p.channelCount bits)

    for (ch <- 0 until p.channelCount) {
      new Area {
        // Push: bus write fires data into the FIFO; silently drops if full.
        val pushStream = busCtrl.createAndDriveFlow(Bits(32 bits), regs.write(ch)).toStream
        ctrl.io.push(ch) << pushStream
        pushStream.ready.allowPruning()

        // Pop: bus read pops one entry; returns the payload register value.
        // Software must check the empty flag in the status register before reading.
        val popFire = Bool()
        popFire := False
        busCtrl.onRead(regs.read(ch)) { popFire := True }
        ctrl.io.pop(ch).ready := popFire
        busCtrl.read(ctrl.io.pop(ch).payload, regs.read(ch))

        busCtrl.read(ctrl.io.occupancy(ch).resize(32), regs.occupancy(ch))

        val irqCtrl = new InterruptCtrl(2)
        irqCtrl.driveFrom(busCtrl, regs.interruptPending(ch).toInt)
        irqCtrl.io.inputs(0) := ctrl.io.pop(ch).valid // not-empty
        irqCtrl.io.inputs(1) := ctrl.io.push(ch).ready // not-full
        ctrl.io.pendingInterrupts(ch) := irqCtrl.io.pendings

        emptyBits(ch) := !ctrl.io.pop(ch).valid
        fullBits(ch) := !ctrl.io.push(ch).ready
      }
    }

    busCtrl.read(B(0, 28 bits) ## fullBits ## emptyBits, regs.status)
  }
}
