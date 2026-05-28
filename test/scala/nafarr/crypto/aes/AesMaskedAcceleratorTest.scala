// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.aes

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._

class AesMaskedAcceleratorTest extends AnyFunSuite {

  def init(dut: Apb3AesMaskedAccelerator): (Apb3Driver, AesMaskedAcceleratorCtrl.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = AesMaskedAcceleratorCtrl.Regs(dut.mapper.idCtrl.length)
    dut.clockDomain.forkStimulus(10)
    return (driver, regs)
  }

  test("Apb3Parameter") {
    generationShouldPass(Apb3AesMaskedAccelerator(AesMaskedAcceleratorCtrl.Parameter.default()))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkAesMaskedAccelerator(AesMaskedAcceleratorCtrl.Parameter.default()))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneAesMaskedAccelerator(AesMaskedAcceleratorCtrl.Parameter.default()))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3AesMaskedAccelerator(AesMaskedAcceleratorCtrl.Parameter.default())
      dut
    }
    compiled.doSim("test") { dut =>
      val (apb, regs) = init(dut)

      /* Write Key */
      for (_ <- 0 until 8) {
        apb.write(regs.key, BigInt("FFFFFFFF", 16))
      }

      /* Write Plaintext */
      for (_ <- 0 until 8) {
        apb.write(regs.plaintext, BigInt("FFFFFFFF", 16))
      }

      apb.write(regs.masking, BigInt("FFFFFFFF", 16))

      dut.clockDomain.waitSampling(4)

      /* Start */
      apb.write(regs.control, BigInt("1", 16))

      dut.clockDomain.waitSampling(400)

      /* Write Key */
      for (_ <- 0 until 8) {
        apb.write(regs.key, BigInt("FFFFFFFF", 16))
      }

      for (_ <- 0 until 8) {
        val cipher = apb.read(regs.ciphertext)
        apb.write(regs.plaintext, cipher)
      }

      dut.clockDomain.waitSampling(4)

      /* Start */
      apb.write(regs.control, BigInt("1", 16))

      dut.clockDomain.waitSampling(400)
    }
  }
}
