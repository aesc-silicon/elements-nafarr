// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.crc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.crypto.checksum._
import spinal.crypto._

import nafarr.IpIdentification

object Crc32Ctrl {
  def apply(p: Parameter = Parameter()) = Crc32Ctrl(p)

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val info = base + 0x00
    val control = base + 0x04
    val data = base + 0x08
    val result = base + 0x0c
    val xorOut = base + 0x10
  }

  /** Selects the CRC polynomial and its standard parameters.
    *
    * polynomial : CRCPolynomial from SpinalCrypto (e.g. CRC32.Standard).
    */
  case class InitParameter(
      polynomial: CRCPolynomial = CRC32.Standard
  )
  object InitParameter {
    def crc32() = InitParameter(CRC32.Standard)
    def crc32xfer() = InitParameter(CRC32.XFER)
  }

  case class Parameter(
      init: InitParameter = InitParameter.crc32(),
      inputReflect: Boolean = true,
      outputReflect: Boolean = true,
      xorOut: Boolean = false
  )
  object Parameter {
    def default() = Parameter()
    def withXorOut() = Parameter(xorOut = true)
  }

  /** Build a CRCCombinationalConfig from a Parameter.
    *
    * finalXor is always set to 0 here; XOR-out is applied by the Mapper so
    * that an optional runtime-configurable xorOut register can be used.
    */
  def config(p: Parameter) = CRCCombinationalConfig(
    new CRCPolynomial(
      polynomial = p.init.polynomial.polynomial,
      initValue = p.init.polynomial.initValue,
      inputReflected = p.inputReflect,
      outputReflected = p.outputReflect,
      finalXor = 0
    ),
    32 bits
  )

  /** CRC32 accelerator core.
    *
    * io.cmd    : slave Flow - INIT reloads the init value; UPDATE folds one
    *             32-bit word into the CRC state.
    * io.result : out - current CRC state, without finalXor (handled by Mapper).
    */
  case class Crc32Ctrl(p: Parameter) extends Component {
    val cfg = config(p)

    val io = new Bundle {
      val cmd = slave Flow (CRCCombinationalCmd(cfg))
      val result = out Bits (32 bits)
    }

    val crc = new CRCCombinational(cfg)
    crc.io.cmd <> io.cmd
    io.result := crc.io.crc
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Crc32Ctrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Crc32, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    // Info: self-disclosure register.
    //   bits[ 7: 0] = polynomial order (e.g. 32 for CRC32)
    //   bit       8  = inputReflect
    //   bit       9  = outputReflect
    //   bit      10  = xorOut register present
    busCtrl.read(
      B(0, 21 bits) ##
        Bool(p.xorOut) ##
        Bool(p.outputReflect) ##
        Bool(p.inputReflect) ##
        B(p.init.polynomial.polynomial.order, 8 bits),
      regs.info
    )

    // Control: any bus write triggers CRC init (reloads init value into state).
    val initPulse = Bool()
    initPulse := False
    busCtrl.onWrite(regs.control) {
      initPulse := True
    }

    // Data: bus write folds one 32-bit word into the CRC state.
    val dataFlow = busCtrl.createAndDriveFlow(Bits(32 bits), regs.data)

    // Drive CRC command. INIT takes priority; a bus can only issue one write
    // per cycle so both flags cannot be high simultaneously in practice.
    ctrl.io.cmd.valid := initPulse || dataFlow.valid
    ctrl.io.cmd.mode := CRCCombinationalCmdMode.UPDATE
    when(initPulse) {
      ctrl.io.cmd.mode := CRCCombinationalCmdMode.INIT
    }
    ctrl.io.cmd.data := dataFlow.payload

    // XorOut register (present only when p.xorOut = true).
    // Initialised from the polynomial's standard finalXor value so that the
    // default configuration matches the chosen CRC standard out of reset.
    // When disabled, reads as zero and the result register is not XOR-ed.
    // Result: current CRC state XOR xorOut (hardware finalises the standard).
    if (p.xorOut) {
      val xorOutReg = Reg(Bits(32 bits)) init (B(p.init.polynomial.finalXor, 32 bits))
      busCtrl.read(ctrl.io.result ^ xorOutReg, regs.result)
      busCtrl.readAndWrite(xorOutReg, regs.xorOut)
    } else {
      busCtrl.read(ctrl.io.result, regs.result)
      busCtrl.read(B(0, 32 bits), regs.xorOut)
    }
  }
}
