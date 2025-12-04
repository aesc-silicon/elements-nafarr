// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.ocram.ihp.sg13g2


import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.bmb._
import spinal.lib.bus.bmb.sim.BmbDriver
import nafarr.CheckTester._

class BmbOnChipRam1Port4MacroTest extends AnyFunSuite {

  def defaultSignals(dut: BmbIhpOnChipRam.OnePort4Macros) = {
    dut.clockDomain.forkStimulus(10)
    dut.io.bus.cmd.valid #= false
    dut.io.bus.rsp.ready #= true
    dut.clockDomain.waitSampling(5)
  }

  def writeWord(dut: BmbIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.bus.cmd.valid #= true
    dut.io.bus.cmd.address #= address
    dut.io.bus.cmd.data #= data
    dut.io.bus.cmd.mask #= 0xF
    dut.io.bus.cmd.opcode #= Bmb.Cmd.Opcode.WRITE
    dut.clockDomain.waitSamplingWhere(dut.io.bus.cmd.ready.toBoolean)
    dut.io.bus.cmd.valid #= false
    dut.clockDomain.waitSamplingWhere(dut.io.bus.rsp.valid.toBoolean)
  }

  def writeShort(dut: BmbIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.bus.cmd.valid #= true
    dut.io.bus.cmd.address #= address
    dut.io.bus.cmd.data #= data
    dut.io.bus.cmd.mask #= 0x3
    dut.io.bus.cmd.opcode #= Bmb.Cmd.Opcode.WRITE
    dut.clockDomain.waitSamplingWhere(dut.io.bus.cmd.ready.toBoolean)
    dut.io.bus.cmd.valid #= false
    dut.clockDomain.waitSamplingWhere(dut.io.bus.rsp.valid.toBoolean)
  }

  def writeChar(dut: BmbIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.bus.cmd.valid #= true
    dut.io.bus.cmd.address #= address
    dut.io.bus.cmd.data #= data
    dut.io.bus.cmd.mask #= 0x1
    dut.io.bus.cmd.opcode #= Bmb.Cmd.Opcode.WRITE
    dut.clockDomain.waitSamplingWhere(dut.io.bus.cmd.ready.toBoolean)
    dut.io.bus.cmd.valid #= false
    dut.clockDomain.waitSamplingWhere(dut.io.bus.rsp.valid.toBoolean)
  }

  def readAndCheckWord(dut: BmbIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.bus.cmd.valid #= true
    dut.io.bus.cmd.address #= address
    dut.io.bus.cmd.opcode #= Bmb.Cmd.Opcode.READ
    dut.io.bus.cmd.mask #= 0xF
    dut.clockDomain.waitSamplingWhere(dut.io.bus.cmd.ready.toBoolean)
    dut.io.bus.cmd.valid #= false
    dut.clockDomain.waitSamplingWhere(dut.io.bus.rsp.valid.toBoolean)
    assert(dut.io.bus.rsp.data.toBigInt == data)
  }

  def readAndCheckShort(dut: BmbIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.bus.cmd.valid #= true
    dut.io.bus.cmd.address #= address
    dut.io.bus.cmd.opcode #= Bmb.Cmd.Opcode.READ
    dut.io.bus.cmd.mask #= 0x3
    dut.clockDomain.waitSamplingWhere(dut.io.bus.cmd.ready.toBoolean)
    dut.io.bus.cmd.valid #= false
    dut.clockDomain.waitSamplingWhere(dut.io.bus.rsp.valid.toBoolean)
    assert(dut.io.bus.rsp.data.toBigInt == data)
  }

  def readAndCheckChar(dut: BmbIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.bus.cmd.valid #= true
    dut.io.bus.cmd.address #= address
    dut.io.bus.cmd.opcode #= Bmb.Cmd.Opcode.READ
    dut.io.bus.cmd.mask #= 0x1
    dut.clockDomain.waitSamplingWhere(dut.io.bus.cmd.ready.toBoolean)
    dut.io.bus.cmd.valid #= false
    dut.clockDomain.waitSamplingWhere(dut.io.bus.rsp.valid.toBoolean)
    assert(dut.io.bus.rsp.data.toBigInt == data)
  }

  test("BmbOnChipRam1Port4Macro") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dataBusParameter = BmbParameter(
          addressWidth = 32,
          dataWidth = 32,
          lengthWidth = 6,
          sourceWidth = 4,
          contextWidth = 4
        )
        val dut = BmbIhpOnChipRam.OnePort4Macros(dataBusParameter, 0x1000)
      }
      area.dut
    }
    compiled.doSim("default signals") { dut =>
      defaultSignals(dut)
    }

    compiled.doSim("word") { dut =>
      defaultSignals(dut)

      writeWord(dut, 0x0000000, 0x00000000)
      dut.clockDomain.waitSampling(2)
      writeWord(dut, 0x0000000, 0x44332211)
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, 0x00000000, 0x44332211)

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("short") { dut =>
      defaultSignals(dut)

      writeWord(dut, 0x0000000, 0x00000000)
      dut.clockDomain.waitSampling(2)
      writeShort(dut, 0x0000000, 0x22112211)
      dut.clockDomain.waitSampling(2)
      readAndCheckShort(dut, 0x00000000, 0x00002211)

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("char") { dut =>
      defaultSignals(dut)

      writeWord(dut, 0x0000000, 0x00000000)
      dut.clockDomain.waitSampling(2)
      writeChar(dut, 0x0000000, 0x11111111)
      dut.clockDomain.waitSampling(2)
      readAndCheckChar(dut, 0x00000000, 0x00000011)

      dut.clockDomain.waitSampling(20)
    }

  }
}
