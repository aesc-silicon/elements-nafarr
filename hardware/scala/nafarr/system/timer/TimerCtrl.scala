// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.timer

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.misc.InterruptCtrl

import nafarr.IpIdentification

object TimerCtrl {
  def apply(p: Parameter) = new TimerCtrl(p)

  case class Parameter(
      count: Int = 1,
      channelCount: Int = 1,
      width: Int = 32,
      prescalerWidth: Int = 16
  ) {
    require(count >= 1 && count <= 16, "count must be 1..16")
    require(channelCount >= 1 && channelCount <= 8, "channelCount must be 1..8")
    require(width >= 1 && width <= 32, "width must be 1..32")
    require(prescalerWidth >= 0 && prescalerWidth <= 32, "prescalerWidth must be 0..32")
    val timerStride: Int = 0x10 + channelCount * 0x04
    val irqSources: Int = count * (1 + channelCount)
  }
  object Parameter {
    def default() = Parameter()
    def small() = Parameter(count = 1, channelCount = 1, width = 16, prescalerWidth = 8)
    def medium() = Parameter(count = 1, channelCount = 2, width = 32, prescalerWidth = 16)
    def large() = Parameter(count = 4, channelCount = 2, width = 32, prescalerWidth = 16)
  }

  object Regs {
    def apply(base: BigInt, p: Parameter) = new Regs(base, p)
  }
  class Regs(base: BigInt, p: Parameter) {
    val info = base + 0x00
    val irqPending = base + 0x04
    val irqMask = base + 0x08
    private def timerBase(t: Int): BigInt = base + 0x0c + t * p.timerStride
    def control(t: Int) = timerBase(t) + 0x00
    def prescaler(t: Int) = timerBase(t) + 0x04
    def counter(t: Int) = timerBase(t) + 0x08
    def reload(t: Int) = timerBase(t) + 0x0c
    def compare(t: Int, ch: Int) = timerBase(t) + 0x10 + ch * 0x04
  }

  /** Per-timer configuration driven from the Mapper into the controller. */
  case class TimerInstanceConfig(p: Parameter) extends Bundle {
    val enable = Bool()
    val mode = Bits(2 bits) // 00=free-run 01=periodic 10=one-shot
    val reloadVal = UInt(p.width bits)
    val compareVals = Vec(UInt(p.width bits), p.channelCount)
  }

  /** Per-timer status driven from the controller back to the Mapper. */
  case class TimerInstanceStatus(p: Parameter) extends Bundle {
    val counter = UInt(p.width bits)
    val overflow = Bool()
    val compareMatch = Vec(Bool(), p.channelCount)
    val enableClear = Bool() // one-shot done: Mapper must clear the enable bit
  }

  case class Io(p: Parameter) extends Bundle {
    val config = in Vec (TimerInstanceConfig(p), p.count)
    val counterLoad = in Vec (Bool(), p.count)
    val counterIn = in Vec (UInt(p.width bits), p.count)
    val status = out Vec (TimerInstanceStatus(p), p.count)
    val prescaler = (p.prescalerWidth > 0) generate in(Vec(UInt(p.prescalerWidth bits), p.count))
  }

  /** Timer controller.
    *
    * Contains all counter and prescaler hardware. No bus logic.
    *
    * config       : per-timer enable, mode, reload, compare values (from Mapper registers).
    * counterLoad  : preload strobe - loads counterIn into the counter on the next edge.
    *               Hardware update has priority; preload only takes effect when stopped.
    * counterIn    : preload value written by software.
    * prescaler    : prescaler reload value (only present when prescalerWidth > 0).
    *
    * status.counter      : current counter value (readable by Mapper via bus).
    * status.overflow     : pulses one cycle on counter bound (all modes).
    * status.compareMatch : pulses one cycle when counter equals compareVals[ch].
    * status.enableClear  : pulses one cycle on completion of a one-shot run.
    *
    * Modes (mode[1:0]):
    *   00 free-run  - counts 0 to maxVal, wraps to 0.
    *   01 periodic  - counts 0 to reloadVal, resets to 0; repeats.
    *   10 one-shot  - counts 0 to reloadVal, resets to 0, asserts enableClear.
    */
  case class TimerCtrl(p: Parameter) extends Component {
    val io = Io(p)

    for (t <- 0 until p.count) {
      new Area {
        val cfg = io.config(t)
        val sts = io.status(t)

        // Prescaler: counts down from prescalerReg; generates tick on zero.
        val tick = Bool()
        if (p.prescalerWidth > 0) {
          val prescalerCounter = Reg(UInt(p.prescalerWidth bits)) init (0)
          tick := False
          when(prescalerCounter === 0) {
            tick := True
            prescalerCounter := io.prescaler(t)
          } otherwise {
            prescalerCounter := prescalerCounter - 1
          }
        } else {
          tick := True
        }

        // Counter register.
        val counterReg = Reg(UInt(p.width bits)) init (0)
        sts.counter := counterReg

        // Overflow: at maxVal for free-run, at reloadVal for periodic/one-shot.
        val maxVal = U((BigInt(1) << p.width) - 1, p.width bits)
        val atBound = Bool()
        when(cfg.mode === B"00") {
          atBound := counterReg === maxVal
        } otherwise {
          atBound := counterReg === cfg.reloadVal
        }

        sts.overflow := cfg.enable && tick && atBound
        sts.enableClear := cfg.enable && tick && atBound && (cfg.mode === B"10")

        // Compare matches (checked against current counter value each tick).
        for (ch <- 0 until p.channelCount) {
          sts.compareMatch(ch) := cfg.enable && tick && (counterReg === cfg.compareVals(ch))
        }

        // Counter update. Hardware has priority; preload only takes effect when stopped.
        when(cfg.enable && tick) {
          when(atBound) {
            counterReg := 0
          } otherwise {
            counterReg := counterReg + 1
          }
        } elsewhen (io.counterLoad(t)) {
          counterReg := io.counterIn(t)
        }
      }.setName(s"timer_$t")
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: TimerCtrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Timer, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length, p)

    // info: [7:0]=count [15:8]=channelCount [23:16]=width [31:24]=prescalerWidth
    busCtrl.read(
      B(p.prescalerWidth, 8 bits) ## B(p.width, 8 bits) ##
        B(p.channelCount, 8 bits) ## B(p.count, 8 bits),
      regs.info
    )

    // InterruptCtrl accumulates events; io.masks hardwired to all-ones so io.pendings
    // returns raw (unmasked) pending on bus reads. A separate maskReg drives the
    // interrupt output, keeping standard W1C-at-irqPending / mask-at-irqMask layout.
    val irqCtrl = new InterruptCtrl(p.irqSources)
    irqCtrl.io.masks := B((BigInt(1) << p.irqSources) - 1, p.irqSources bits)
    val clearFlow = busCtrl.createAndDriveFlow(Bits(p.irqSources bits), regs.irqPending.toInt)
    irqCtrl.io.clears := 0
    when(clearFlow.valid) { irqCtrl.io.clears := clearFlow.payload }
    busCtrl.read(irqCtrl.io.pendings.resized, regs.irqPending.toInt)
    val maskReg = Reg(Bits(p.irqSources bits)) init (0)
    busCtrl.readAndWrite(maskReg, regs.irqMask.toInt)
    val irqEvents = Vec(Bool(), p.irqSources)

    // Prescaler registers (only when prescalerWidth > 0).
    if (p.prescalerWidth > 0) {
      for (t <- 0 until p.count) {
        val prescalerReg = Reg(UInt(p.prescalerWidth bits)) init (0)
        busCtrl.readAndWrite(prescalerReg, regs.prescaler(t))
        ctrl.io.prescaler(t) := prescalerReg
      }
    } else {
      for (t <- 0 until p.count) {
        busCtrl.read(B(0, 32 bits), regs.prescaler(t))
      }
    }

    for (t <- 0 until p.count) {
      new Area {
        // Control register: hardware clears enable on one-shot completion.
        val controlFlow = busCtrl.createAndDriveFlow(Bits(3 bits), regs.control(t))
        val controlReg = Reg(Bits(3 bits)) init (0)
        when(ctrl.io.status(t).enableClear) {
          controlReg := B"000"
        } elsewhen (controlFlow.valid) {
          controlReg := controlFlow.payload
        }
        busCtrl.read(controlReg, regs.control(t))
        ctrl.io.config(t).enable := controlReg(0)
        ctrl.io.config(t).mode := controlReg(2 downto 1)

        // Reload register.
        val reloadReg = Reg(UInt(p.width bits)) init (0)
        busCtrl.readAndWrite(reloadReg, regs.reload(t))
        ctrl.io.config(t).reloadVal := reloadReg

        // Compare registers.
        for (ch <- 0 until p.channelCount) {
          val compareReg =
            Reg(UInt(p.width bits)) init (U((BigInt(1) << p.width) - 1, p.width bits))
          busCtrl.readAndWrite(compareReg, regs.compare(t, ch))
          ctrl.io.config(t).compareVals(ch) := compareReg
        }

        // Counter: bus reads live value; bus write preloads (when stopped).
        val counterFlow = busCtrl.createAndDriveFlow(UInt(p.width bits), regs.counter(t))
        ctrl.io.counterLoad(t) := counterFlow.valid
        ctrl.io.counterIn(t) := counterFlow.payload
        busCtrl.read(ctrl.io.status(t).counter, regs.counter(t))

        // Interrupt sources: overflow at irqBase, compare[ch] at irqBase+1+ch.
        val irqBase = t * (1 + p.channelCount)
        irqEvents(irqBase) := ctrl.io.status(t).overflow
        for (ch <- 0 until p.channelCount) {
          irqEvents(irqBase + 1 + ch) := ctrl.io.status(t).compareMatch(ch)
        }
      }.setName(s"timer_$t")
    }

    irqCtrl.io.inputs := irqEvents.asBits
    val interrupt = (irqCtrl.io.pendings & maskReg).orR
  }
}
