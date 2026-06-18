// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.esm

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory

import nafarr.IpIdentification

object EsmCtrl {
  def apply(p: Parameter) = new EsmCtrl(p)
  def apply(inputCount: Int) = EsmCtrl(Parameter.default(inputCount))

  case class Parameter(
      inputCount: Int,
      counterWidth: Int = 24,
      locked: Boolean = true
  ) {
    require(inputCount >= 1 && inputCount <= 256, "ESM input count must be between 1 and 256")
    require(counterWidth >= 1 && counterWidth <= 32, "ESM counter width must be between 1 and 32")
    val bankCount: Int = (inputCount + 7) / 8
  }
  object Parameter {
    def default(inputCount: Int) = Parameter(inputCount)
    def small(inputCount: Int) = Parameter(inputCount, counterWidth = 16)
    def large(inputCount: Int) = Parameter(inputCount, counterWidth = 32)
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }

  /** Register map.
    *
    * Global registers start at `base` (= IpIdentification header length = 8):
    *   info          base + 0x00  - self-disclosure (read-only)
    *   control       base + 0x04  - enable, lock, injectEnable
    *   status        base + 0x08  - counterActive, errorSignal (read-only)
    *   errorCounter  base + 0x0c  - grace-period reload value (locked)
    *
    * Per-bank registers, stride 0x10, bank N base = base + 0x20 + N * 0x10:
    *   enable   +0x00  - combined INFO/WARN/ERROR/FATAL routing mask
    *   pending  +0x04  - combined INFO/WARN/ERROR/FATAL pending bits (W1C)
    *   raw      +0x08  - active inputs for this bank (read-only)
    *   inject   +0x0c  - injected inputs for this bank
    *
    * Enable/pending register layout (same for both):
    *   bits [31:24] = FATAL for inputs [7:0] of this bank
    *   bits [23:16] = ERROR for inputs [7:0] of this bank
    *   bits  [15:8] = WARN  for inputs [7:0] of this bank
    *   bits   [7:0] = INFO  for inputs [7:0] of this bank
    *
    * The last bank may cover fewer than 8 inputs; upper bits in each level byte
    * are unused and always read as zero.
    *
    * Lock scope: writes to ERROR/FATAL fields of enable (bits [31:16]) and to
    * errorCounter and inject are ignored once the ESM is locked.
    * INFO/WARN fields of enable (bits [15:0]) are never locked.
    */
  class Regs(base: BigInt) {
    val info = base + 0x00
    val control = base + 0x04
    val status = base + 0x08
    val errorCounter = base + 0x0c
    private def bankBase(bank: Int): BigInt = base + 0x20 + bank * 0x10
    def enable(bank: Int) = bankBase(bank) + 0x00
    def pending(bank: Int) = bankBase(bank) + 0x04
    def raw(bank: Int) = bankBase(bank) + 0x08
    def inject(bank: Int) = bankBase(bank) + 0x0c
  }

  /** Error Signaling Module controller.
    *
    * Aggregates up to `p.inputCount` independent error inputs from other IP cores, grouped into
    * banks of 8 inputs. Each bank has a single combined enable register and a single combined
    * pending register that pack all four severity levels into one 32-bit word, allowing the input
    * space to scale beyond 32 bits without changes to the register interface. Each input is
    * synchronised through a 2-FF chain to filter glitches and prevent metastability. The ESM
    * routes events to one of four severity levels:
    *
    *   INFO  - masked, latched -> `infoInterrupt` (low-priority PLIC lane)
    *   WARN  - masked, latched -> `warnInterrupt` (high-priority PLIC lane)
    *   ERROR - masked, latched -> `errorSignal` after a configurable grace-period counter
    *   FATAL - masked, latched -> `errorSignal` immediately, bypassing the counter
    *
    * An input can be assigned to multiple levels simultaneously via independent bits in the
    * combined enable register. Pending bits are pre-masked (only enabled inputs are latched)
    * and cleared by writing 1 (W1C).
    *
    * The grace-period counter starts when any ERROR-level pending bit is set. If software clears
    * all ERROR pending bits before the counter expires the counter resets and `errorSignal` is
    * never asserted from that event. Setting `errorCounter = 0` makes ERROR behave like FATAL.
    *
    * Software error injection is controlled by `injectEnable` (control bit 2). When enabled,
    * writes to the inject registers OR with the synchronised hardware inputs. Locking the ESM
    * (control bit 1) atomically clears `injectEnable` and all inject bank registers, and freezes
    * the ERROR/FATAL fields of the enable registers, `errorCounter`, and the inject registers.
    *
    * io.inputs          : in  - raw error inputs from other IP cores.
    * io.injectInputs    : in  - inject register values, driven by Mapper.
    * io.injectEnable    : in  - gates inject OR path.
    * io.errorPendingAny : in  - OR of all ERROR pending bits (from Mapper).
    * io.fatalPendingAny : in  - OR of all FATAL pending bits (from Mapper).
    * io.infoPendingAny  : in  - OR of all INFO pending bits (from Mapper).
    * io.warnPendingAny  : in  - OR of all WARN pending bits (from Mapper).
    * io.counterPreload  : in  - grace-period counter reload value (from Mapper).
    * io.activeInputs    : out - synchronised inputs OR inject; feeds Mapper pending registers.
    * io.counterActive   : out - high while grace-period counter is counting.
    * io.counterExpired  : out - latched high when counter reaches zero; cleared by Mapper.
    * io.infoInterrupt   : out - asserted while any INFO pending bit is set.
    * io.warnInterrupt   : out - asserted while any WARN pending bit is set.
    * io.errorSignal     : out - asserted on FATAL event or after grace-period expiry.
    */
  case class EsmCtrl(p: Parameter) extends Component {
    val io = new Bundle {
      val inputs = in(Bits(p.inputCount bits))
      val injectInputs = in(Bits(p.inputCount bits))
      val injectEnable = in Bool ()
      val errorPendingAny = in Bool ()
      val fatalPendingAny = in Bool ()
      val infoPendingAny = in Bool ()
      val warnPendingAny = in Bool ()
      val counterPreload = in(UInt(p.counterWidth bits))
      val activeInputs = out(Bits(p.inputCount bits))
      val counterActive = out Bool ()
      val counterExpired = out Bool ()
      val infoInterrupt = out Bool ()
      val warnInterrupt = out Bool ()
      val errorSignal = out Bool ()
    }

    // Two-FF synchroniser: filters glitches and prevents metastability.
    val stage1 = RegNext(io.inputs, B(0, p.inputCount bits))
    val syncInputs = RegNext(stage1, B(0, p.inputCount bits))

    val injected = Bits(p.inputCount bits)
    injected := B(0, p.inputCount bits)
    when(io.injectEnable) {
      injected := io.injectInputs
    }
    io.activeInputs := syncInputs | injected

    // Grace-period counter for ERROR level.
    val counter = Reg(UInt(p.counterWidth bits)) init (0)
    val counterActiveReg = Reg(Bool()) init (False)
    val counterExpiredReg = Reg(Bool()) init (False)

    when(!io.errorPendingAny) {
      counterActiveReg := False
      counter := 0
      counterExpiredReg := False
    } elsewhen (!counterActiveReg && !counterExpiredReg) {
      when(io.counterPreload === 0) {
        counterExpiredReg := True
      } otherwise {
        counterActiveReg := True
        counter := io.counterPreload
      }
    } elsewhen (counterActiveReg) {
      when(counter === 0) {
        counterActiveReg := False
        counterExpiredReg := True
      } otherwise {
        counter := counter - 1
      }
    }

    io.counterActive := counterActiveReg
    io.counterExpired := counterExpiredReg
    io.infoInterrupt := io.infoPendingAny
    io.warnInterrupt := io.warnPendingAny
    io.errorSignal := counterExpiredReg || io.fatalPendingAny
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: EsmCtrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Esm, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    // info: locked[24] | counterWidth[23:16] | bankCount[15:9] | inputCount[8:0]
    busCtrl.read(
      B(0, 7 bits) ## Bool(p.locked) ## B(p.counterWidth, 8 bits) ## B(p.bankCount, 7 bits) ## B(
        p.inputCount,
        9 bits
      ),
      regs.info
    )

    // Control: enable[0], lock[1], injectEnable[2]
    val enableReg = Reg(Bool()) init (False)
    val lockReg = if (p.locked) Reg(Bool()) init (False) else null
    val isLocked: Bool = if (p.locked) lockReg else False
    val injectEnableReg = Reg(Bool()) init (False)

    // Per-bank inject registers (8-bit wide; one bit per bank input).
    val injectBankRegs = Vec(Reg(Bits(8 bits)) init (0), p.bankCount)

    val controlFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.control)
    when(controlFlow.valid) {
      if (p.locked) {
        when(controlFlow.payload(1)) {
          lockReg := True
          injectEnableReg := False
          injectBankRegs.foreach(_ := B(0, 8 bits))
        } otherwise {
          when(!isLocked) {
            enableReg := controlFlow.payload(0)
            injectEnableReg := controlFlow.payload(2)
          } elsewhen (controlFlow.payload(0)) {
            enableReg := True
          }
        }
      } else {
        enableReg := controlFlow.payload(0)
        injectEnableReg := controlFlow.payload(2)
      }
    }
    val controlBits = Bits(3 bits)
    controlBits(0) := enableReg
    if (p.locked) {
      controlBits(1) := lockReg
    } else {
      controlBits(1) := False
    }
    controlBits(2) := injectEnableReg
    busCtrl.read(controlBits.resize(32), regs.control)

    // Status (read-only).
    val statusBits = Bits(2 bits)
    statusBits(0) := ctrl.io.counterActive
    statusBits(1) := ctrl.io.errorSignal
    busCtrl.read(statusBits.resize(32), regs.status)

    // Error counter (locked).
    val counterReg = Reg(UInt(p.counterWidth bits)) init (~U(0, p.counterWidth bits))
    val counterFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.errorCounter)
    when(counterFlow.valid && !isLocked) {
      counterReg := counterFlow.payload(p.counterWidth - 1 downto 0).asUInt
    }
    busCtrl.read(counterReg.resize(32), regs.errorCounter)
    ctrl.io.counterPreload := counterReg

    // Gated inputs: active inputs qualified by master enable.
    val gatedInputs = Bits(p.inputCount bits)
    gatedInputs := B(0, p.inputCount bits)
    when(enableReg) {
      gatedInputs := ctrl.io.activeInputs
    }

    // Per-bank combined enable and pending registers (32-bit; layout per register:
    //   [31:24] = FATAL, [23:16] = ERROR, [15:8] = WARN, [7:0] = INFO).
    val enableBankRegs = Vec(Reg(Bits(32 bits)) init (0), p.bankCount)
    val pendingBankRegs = Vec(Reg(Bits(32 bits)) init (0), p.bankCount)

    for (bank <- 0 until p.bankCount) {
      val esmBank = new Area {
        val bankStart = bank * 8
        val bankEnd = (bankStart + 7) min (p.inputCount - 1)
        val bankWidth = bankEnd - bankStart + 1
        val bankSlice = bankEnd downto bankStart

        // Enable register.
        // INFO/WARN (bits [15:0]): always writable.
        // ERROR/FATAL (bits [31:16]): locked when ESM is locked.
        val enableFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.enable(bank))
        when(enableFlow.valid) {
          enableBankRegs(bank)(15 downto 0) := enableFlow.payload(15 downto 0)
        }
        when(enableFlow.valid && !isLocked) {
          enableBankRegs(bank)(31 downto 16) := enableFlow.payload(31 downto 16)
        }
        busCtrl.read(enableBankRegs(bank), regs.enable(bank))

        // Pending register (W1C).
        val pendingFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.pending(bank))
        val clearMask = Bits(32 bits)
        clearMask := B(0, 32 bits)
        when(pendingFlow.valid) {
          clearMask := pendingFlow.payload
        }

        val bankGated = gatedInputs(bankSlice).resize(8)
        val infoEvents = bankGated & enableBankRegs(bank)(7 downto 0)
        val warnEvents = bankGated & enableBankRegs(bank)(15 downto 8)
        val errorEvents = bankGated & enableBankRegs(bank)(23 downto 16)
        val fatalEvents = bankGated & enableBankRegs(bank)(31 downto 24)
        val newEvents = fatalEvents ## errorEvents ## warnEvents ## infoEvents

        pendingBankRegs(bank) := (pendingBankRegs(bank) | newEvents) & ~clearMask
        busCtrl.read(pendingBankRegs(bank), regs.pending(bank))

        // Raw (read-only, unaffected by master enable).
        busCtrl.read(ctrl.io.activeInputs(bankSlice).resize(32), regs.raw(bank))

        // Inject register (writable only when injectEnable && !locked).
        val injectFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.inject(bank))
        when(injectFlow.valid && injectEnableReg && !isLocked) {
          injectBankRegs(bank) := injectFlow.payload(7 downto 0)
        }
        busCtrl.read(injectBankRegs(bank).resize(32), regs.inject(bank))
      }.setName(s"bank_${bank}")
    }

    // Assemble full-width inject signal from per-bank registers.
    val injectFull = Bits(p.inputCount bits)
    for (bank <- 0 until p.bankCount) {
      val bankStart = bank * 8
      val bankEnd = (bankStart + 7) min (p.inputCount - 1)
      val bankWidth = bankEnd - bankStart + 1
      injectFull(bankEnd downto bankStart) := injectBankRegs(bank)(bankWidth - 1 downto 0)
    }
    ctrl.io.injectInputs := injectFull
    ctrl.io.injectEnable := injectEnableReg

    // Pending-any signals: OR-reduce the relevant level byte across all banks.
    ctrl.io.infoPendingAny := pendingBankRegs.map(_(7 downto 0).orR).reduce(_ || _)
    ctrl.io.warnPendingAny := pendingBankRegs.map(_(15 downto 8).orR).reduce(_ || _)
    ctrl.io.errorPendingAny := pendingBankRegs.map(_(23 downto 16).orR).reduce(_ || _)
    ctrl.io.fatalPendingAny := pendingBankRegs.map(_(31 downto 24).orR).reduce(_ || _)
  }
}
