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
import nafarr.system.dma.DmaHandshakeSim

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
      DmaHandshakeSim.release(dut.io.dmaRequest)
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
      assert(!dut.io.dmaRequest.tx.req.toBoolean, "DMA tx request high with full plaintext FIFO")
      assert(!dut.io.dmaRequest.rx.req.toBoolean, "DMA rx request high before encryption")

      /* Start */
      apb.write(regs.control, BigInt("1", 16))

      dut.clockDomain.waitSampling(400)
      assert(dut.io.dmaRequest.tx.req.toBoolean, "DMA tx request low with drained plaintext FIFO")
      assert(dut.io.dmaRequest.rx.req.toBoolean, "DMA rx request low with ciphertext available")
      DmaHandshakeSim.checkAck(dut.io.dmaRequest.rx, dut.clockDomain, "AES rx")

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

  test("DMA request") {
    val compiled = SimConfig.withWave.compile {
      Apb3AesMaskedAccelerator(AesMaskedAcceleratorCtrl.Parameter.default())
    }
    compiled.doSim("dmaRequest") { dut =>
      DmaHandshakeSim.release(dut.io.dmaRequest)
      val (apb, regs) = init(dut)
      val cd = dut.clockDomain
      val tx = dut.io.dmaRequest.tx
      val rx = dut.io.dmaRequest.rx
      cd.waitSampling(2)

      /* Idle: the empty plaintext FIFO accepts a word, no ciphertext is waiting */
      assert(tx.req.toBoolean, "DMA tx request low with empty plaintext FIFO")
      assert(!rx.req.toBoolean, "DMA rx request high without ciphertext")
      DmaHandshakeSim.checkAck(tx, cd, "AES tx")

      /* TX: no request while the plaintext FIFO is full */
      for (_ <- 0 until 8) {
        apb.write(regs.key, BigInt("FFFFFFFF", 16))
      }
      for (_ <- 0 until 8) {
        apb.write(regs.plaintext, BigInt("FFFFFFFF", 16))
      }
      apb.write(regs.masking, BigInt("FFFFFFFF", 16))
      cd.waitSampling(4)
      assert(!tx.req.toBoolean, "DMA tx request high with full plaintext FIFO")

      /* Encryption drains the plaintext FIFO and fills the ciphertext FIFO */
      apb.write(regs.control, BigInt("1", 16))
      DmaHandshakeSim.waitReq(rx, cd, true, 1000, "AES rx after encryption")
      DmaHandshakeSim.waitReq(tx, cd, true, 1000, "AES tx after encryption")
      DmaHandshakeSim.checkAck(rx, cd, "AES rx")

      /* RX: no request once all ciphertext words are read */
      for (_ <- 0 until 8) {
        apb.read(regs.ciphertext)
      }
      cd.waitSampling(2)
      assert(!rx.req.toBoolean, "DMA rx request high after reading all ciphertext")
    }
  }
}
