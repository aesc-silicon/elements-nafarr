// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.bmb

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.bmb._
import spinal.lib.bus.bmb.sim.BmbDriver
import nafarr.CheckTester._

class BmbCacheTest extends AnyFunSuite {

  def readWordHit(dut: BmbCache.BmbCache, address: BigInt, data: BigInt) = {
    dut.io.input.cmd.valid #= true
    dut.io.input.cmd.address #= address
    dut.io.input.cmd.opcode #= Bmb.Cmd.Opcode.READ
    dut.io.input.cmd.mask #= 0xF
    sleep(2)
    assert(dut.io.input.cmd.ready.toBoolean == true)
    assert(dut.hit.toBoolean == true)
    dut.clockDomain.waitSampling(1)
    dut.io.input.cmd.valid #= false
    sleep(2)
    assert(dut.io.input.cmd.ready.toBoolean == false)
    assert(dut.io.input.rsp.valid.toBoolean == true)
    assert(dut.io.input.rsp.payload.data.toBigInt == data)
    dut.clockDomain.waitSampling(1)
  }

  def readWordMiss(dut: BmbCache.BmbCache, address: BigInt) = {
    dut.io.input.cmd.valid #= true
    dut.io.input.cmd.address #= address
    dut.io.input.cmd.opcode #= Bmb.Cmd.Opcode.READ
    dut.io.input.cmd.mask #= 0xF
    sleep(2)
    assert(dut.io.input.cmd.ready.toBoolean == false)
    assert(dut.miss.toBoolean == true)
    dut.clockDomain.waitSampling(1)
    dut.io.output.cmd.ready #= true
    sleep(2)
    assert(dut.io.output.cmd.valid.toBoolean == true)
    dut.clockDomain.waitSampling(1)

    dut.io.output.rsp.valid #= true
    dut.io.output.rsp.data #= 0x11111111
    dut.io.output.rsp.last #= false
    sleep(2)
    assert(dut.io.output.rsp.ready.toBoolean == true)
    dut.clockDomain.waitSampling(1)

    dut.io.output.rsp.valid #= true
    dut.io.output.rsp.data #= 0x22222222
    dut.io.output.rsp.last #= false
    sleep(2)
    assert(dut.io.output.rsp.ready.toBoolean == true)
    dut.clockDomain.waitSampling(1)

    dut.io.output.rsp.valid #= true
    dut.io.output.rsp.data #= 0x33333333
    dut.io.output.rsp.last #= false
    sleep(2)
    assert(dut.io.output.rsp.ready.toBoolean == true)
    dut.clockDomain.waitSampling(1)

    dut.io.output.rsp.valid #= true
    dut.io.output.rsp.data #= 0x44444444
    dut.io.output.rsp.last #= true
    sleep(2)
    assert(dut.io.output.rsp.ready.toBoolean == true)
    dut.clockDomain.waitSampling(1)
    dut.io.output.rsp.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.input.cmd.ready.toBoolean == true)
    assert(dut.hit.toBoolean == true)
    dut.clockDomain.waitSampling(1)
    dut.io.input.cmd.valid #= false
    sleep(2)
    assert(dut.io.input.cmd.ready.toBoolean == false)
    dut.clockDomain.waitSampling(1)
  }

  def genCore(words: Int) = {
    val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
    val area = new ClockingArea(cd) {
      val parameter = BmbParameter(
        addressWidth = 32,
        dataWidth = 32,
        lengthWidth = 6,
        sourceWidth = 4,
        contextWidth = 4
      )
      val dut = BmbCache(parameter, words)
    }
    area.dut.hit.simPublic()
    area.dut.miss.simPublic()
    area.dut
  }

  test("parameters") {
    generationShouldPass(genCore(2))
    generationShouldPass(genCore(4))
    generationShouldFail(genCore(5))
  }

  test("BmbCacheTest") {
    val compiled = SimConfig.withWave.compile(genCore(4))

    compiled.doSim("read") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.input.cmd.valid #= false
      dut.io.output.cmd.ready #= false
      dut.clockDomain.waitSampling(5)

      readWordMiss(dut, 0x0)

      readWordHit(dut, 0x0, 0x11111111)
      readWordHit(dut, 0x4, 0x22222222)
      readWordHit(dut, 0x8, 0x33333333)
      readWordHit(dut, 0xC, 0x44444444)

      readWordMiss(dut, 0x10)

      readWordHit(dut, 0x10, 0x11111111)
      readWordHit(dut, 0x14, 0x22222222)
      readWordHit(dut, 0x18, 0x33333333)
      readWordHit(dut, 0x1C, 0x44444444)

      dut.clockDomain.waitSampling(100)
    }
  }
}
