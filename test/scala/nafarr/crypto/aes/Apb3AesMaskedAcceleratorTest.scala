// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.crypto.aes

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class Apb3AesMaskedAcceleratorTest extends AnyFunSuite {
  test("parameters") {
    generationShouldPass(Apb3AesMaskedAccelerator(AesMaskedAcceleratorCtrl.Parameter.default()))
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile {
      val dut = Apb3AesMaskedAccelerator(AesMaskedAcceleratorCtrl.Parameter.default())
      dut
    }
    compiled.doSim("test") { dut =>
      dut.clockDomain.forkStimulus(10)

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
      val staticOffset = dut.mapper.staticOffset
      val regOffset = dut.mapper.regOffset

      /* Write Key */
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))

      /* Write Plaintext */
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 8), BigInt("FFFFFFFF", 16))

      apb.write(BigInt(regOffset + 0x10), BigInt("FFFFFFFF", 16))

      dut.clockDomain.waitSampling(4)

      /* Start */
      apb.write(BigInt(regOffset + 0), BigInt("1", 16))

      dut.clockDomain.waitSampling(400)

      /* Write Key */
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))
      apb.write(BigInt(regOffset + 4), BigInt("FFFFFFFF", 16))

      for (_ <- 0 until 8) {
        val cipher = apb.read(BigInt(regOffset + 0xc))
        apb.write(BigInt(regOffset + 8), cipher)
      }

      dut.clockDomain.waitSampling(4)

      /* Start */
      apb.write(BigInt(regOffset + 0), BigInt("1", 16))

      dut.clockDomain.waitSampling(400)
    }
  }
}
