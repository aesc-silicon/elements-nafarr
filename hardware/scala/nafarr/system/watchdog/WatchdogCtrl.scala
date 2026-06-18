// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.watchdog

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl

import nafarr.IpIdentification

object WatchdogCtrl {
  def apply(p: Parameter = Parameter.default()) = WatchdogCtrl(p)

  case class Parameter(
      count: Int = 1,
      width: Int = 32,
      prescalerWidth: Int = 16,
      windowed: Boolean = false,
      locked: Boolean = true
  ) {
    require(count >= 1 && count <= 255, "Watchdog count must be between 1 and 255")
    require(width >= 1 && width <= 32, "Watchdog counter width must be between 1 and 32")
    require(
      prescalerWidth >= 1 && prescalerWidth <= 32,
      "Prescaler width must be between 1 and 32"
    )
    val irqCount = if (windowed) 4 else 2
  }
  object Parameter {
    def default() = Parameter()
    def small() = Parameter(width = 16, prescalerWidth = 8)
    def windowed() = Parameter(windowed = true)
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val info = base + 0x00
    private def wdtBase(wdt: Int) = base + 0x04 + wdt * 0x20
    def control(wdt: Int) = wdtBase(wdt) + 0x00
    def prescaler(wdt: Int) = wdtBase(wdt) + 0x04
    def timeout(wdt: Int) = wdtBase(wdt) + 0x08
    def windowOpen(wdt: Int) = wdtBase(wdt) + 0x0c
    def status(wdt: Int) = wdtBase(wdt) + 0x10
    def irqPending(wdt: Int) = wdtBase(wdt) + 0x14
    def irqMask(wdt: Int) = wdtBase(wdt) + 0x18
    def kick(wdt: Int) = wdtBase(wdt) + 0x1c
  }

  /** Hardware watchdog controller.
    *
    * Provides `p.count` independent watchdog timers. Each watchdog has a configurable
    * prescaler and down-counter. When the counter reaches zero a timeout fires. In windowed
    * mode a kick is only valid while the counter is at or below `windowOpen`; an early kick
    * raises a window-violation event instead.
    *
    * Each watchdog has four independently maskable interrupt sources (two when not windowed):
    *   - inputs(0): timeout -> interrupt
    *   - inputs(1): timeout -> error
    *   - inputs(2): window violation -> interrupt  (windowed only)
    *   - inputs(3): window violation -> error      (windowed only)
    *
    * All masked pending bits across all watchdogs are OR-ed into the single `interrupt` output.
    *
    * io.enable          : in  - runtime enable per watchdog; rising edge reloads the counter.
    * io.prescalerVal    : in  - divide-by-(n+1) prescaler value per watchdog.
    * io.timeoutVal      : in  - counter reload value per watchdog.
    * io.windowOpenVal   : in  - kick is valid when counter <= windowOpenVal (windowed mode).
    * io.kick            : in  - pulse reloads the counter (or raises violation when windowed).
    * io.timeoutEvent    : out - one-cycle pulse when the counter reaches zero.
    * io.windowViolationEvent : out - one-cycle pulse on an early kick (windowed mode).
    * io.inWindow        : out - high while counter <= windowOpenVal and watchdog is enabled.
    * io.interrupt       : out - combined OR of all masked pending interrupt bits.
    * io.pendingInterrupts : in - masked pending bits per watchdog, driven by Mapper.
    */
  case class WatchdogCtrl(p: Parameter) extends Component {
    val io = new Bundle {
      val enable = in(Vec(Bool(), p.count))
      val prescalerVal = in(Vec(UInt(p.prescalerWidth bits), p.count))
      val timeoutVal = in(Vec(UInt(p.width bits), p.count))
      val windowOpenVal = in(Vec(UInt(p.width bits), p.count))
      val kick = in(Vec(Bool(), p.count))
      val timeoutEvent = out(Vec(Bool(), p.count))
      val windowViolationEvent = out(Vec(Bool(), p.count))
      val inWindow = out(Vec(Bool(), p.count))
      val interrupt = out Bool ()
      val error = out Bool ()
      val pendingInterrupts = in(Vec(Bits(p.irqCount bits), p.count))
    }

    for (i <- 0 until p.count) {
      val prescalerCnt = Reg(UInt(p.prescalerWidth bits)) init (0)
      val counter = Reg(UInt(p.width bits)) init (0)
      val enablePrev = RegNext(io.enable(i)) init (False)
      val enableRise = io.enable(i) && !enablePrev
      val tick = prescalerCnt === io.prescalerVal(i)

      io.timeoutEvent(i) := False
      io.windowViolationEvent(i) := False
      if (p.windowed) {
        io.inWindow(i) := io.enable(i) && (counter <= io.windowOpenVal(i))
      } else {
        io.inWindow(i) := False
      }

      when(enableRise) {
        counter := io.timeoutVal(i)
        prescalerCnt := 0
      } elsewhen (io.enable(i)) {
        when(io.kick(i)) {
          if (p.windowed) {
            when(counter <= io.windowOpenVal(i)) {
              counter := io.timeoutVal(i)
              prescalerCnt := 0
            } otherwise {
              io.windowViolationEvent(i) := True
            }
          } else {
            counter := io.timeoutVal(i)
            prescalerCnt := 0
          }
        } elsewhen (tick) {
          prescalerCnt := 0
          when(counter === 0) {
            io.timeoutEvent(i) := True
            counter := io.timeoutVal(i)
          } otherwise {
            counter := counter - 1
          }
        } otherwise {
          prescalerCnt := prescalerCnt + 1
        }
      }
    }

    io.interrupt := io.pendingInterrupts
      .map(bits => bits(0) || (if (p.windowed) bits(2) else False))
      .reduce(_ || _)
    io.error := io.pendingInterrupts
      .map(bits => bits(1) || (if (p.windowed) bits(3) else False))
      .reduce(_ || _)
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: WatchdogCtrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Watchdog, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    busCtrl.read(
      B(0, 6 bits) ## Bool(p.locked) ## Bool(p.windowed) ##
        B(p.prescalerWidth, 8 bits) ## B(p.width, 8 bits) ## B(p.count, 8 bits),
      regs.info
    )

    for (wdt <- 0 until p.count) {
      new Area {
        val enableReg = Reg(Bool()) init (False)
        val lockReg = if (p.locked) Reg(Bool()) init (False) else null
        val isLocked: Bool = if (p.locked) lockReg else False

        val controlFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.control(wdt))
        when(controlFlow.valid) {
          if (p.locked) {
            when(!isLocked) {
              enableReg := controlFlow.payload(0)
            } elsewhen (controlFlow.payload(0)) {
              enableReg := True
            }
            when(controlFlow.payload(1)) {
              lockReg := True
            }
          } else {
            enableReg := controlFlow.payload(0)
          }
        }
        val controlBits = Bits(2 bits)
        controlBits(0) := enableReg
        if (p.locked) {
          controlBits(1) := lockReg
        } else {
          controlBits(1) := False
        }
        busCtrl.read(controlBits.resize(32), regs.control(wdt))

        val prescalerReg = Reg(UInt(p.prescalerWidth bits)) init (~U(0, p.prescalerWidth bits))
        val prescalerFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.prescaler(wdt))
        when(prescalerFlow.valid && !isLocked) {
          prescalerReg := prescalerFlow.payload(p.prescalerWidth - 1 downto 0).asUInt
        }
        busCtrl.read(prescalerReg.resize(32), regs.prescaler(wdt))

        val timeoutReg = Reg(UInt(p.width bits)) init (~U(0, p.width bits))
        val timeoutFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.timeout(wdt))
        when(timeoutFlow.valid && !isLocked) {
          timeoutReg := timeoutFlow.payload(p.width - 1 downto 0).asUInt
        }
        busCtrl.read(timeoutReg.resize(32), regs.timeout(wdt))

        if (p.windowed) {
          val windowOpenReg = Reg(UInt(p.width bits)) init (U(0, p.width bits))
          val windowOpenFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.windowOpen(wdt))
          when(windowOpenFlow.valid && !isLocked) {
            windowOpenReg := windowOpenFlow.payload(p.width - 1 downto 0).asUInt
          }
          busCtrl.read(windowOpenReg.resize(32), regs.windowOpen(wdt))
          ctrl.io.windowOpenVal(wdt) := windowOpenReg
        } else {
          busCtrl.read(B(0, 32 bits), regs.windowOpen(wdt))
          ctrl.io.windowOpenVal(wdt) := U(0, p.width bits)
        }

        ctrl.io.enable(wdt) := enableReg
        ctrl.io.prescalerVal(wdt) := prescalerReg
        ctrl.io.timeoutVal(wdt) := timeoutReg

        val statusBits = Bits(3 bits)
        statusBits(0) := enableReg
        if (p.locked) {
          statusBits(1) := lockReg
        } else {
          statusBits(1) := False
        }
        statusBits(2) := ctrl.io.inWindow(wdt)
        busCtrl.read(statusBits.resize(32), regs.status(wdt))

        val irqCtrl = new InterruptCtrl(p.irqCount)
        irqCtrl.driveFrom(busCtrl, regs.irqPending(wdt).toInt)
        irqCtrl.io.inputs(0) := ctrl.io.timeoutEvent(wdt)
        irqCtrl.io.inputs(1) := ctrl.io.timeoutEvent(wdt)
        if (p.windowed) {
          irqCtrl.io.inputs(2) := ctrl.io.windowViolationEvent(wdt)
          irqCtrl.io.inputs(3) := ctrl.io.windowViolationEvent(wdt)
        }
        ctrl.io.pendingInterrupts(wdt) := irqCtrl.io.pendings

        val kickFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.kick(wdt))
        ctrl.io.kick(wdt) := kickFlow.valid
        kickFlow.payload.allowPruning()
      }
    }
  }
}
