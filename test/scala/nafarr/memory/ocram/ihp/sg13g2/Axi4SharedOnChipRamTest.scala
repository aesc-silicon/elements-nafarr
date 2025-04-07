package nafarr.memory.ocram.ihp.sg13g2


import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.amba4.axi.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import nafarr.CheckTester._

class Axi4SharedOnChipRamTest extends AnyFunSuite {

  def defaultSignals(dut: Axi4SharedIhpOnChipRam.OnePort1024x8) = {
    dut.clockDomain.forkStimulus(10)
    dut.io.axi.arw.valid #= false
    dut.io.axi.w.valid #= false
    dut.io.axi.r.ready #= false
    dut.io.axi.b.ready #= false
    dut.clockDomain.waitSampling(5)
  }

  def writeWord(dut: Axi4SharedIhpOnChipRam.OnePort1024x8, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(2)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= true

    dut.clockDomain.waitSampling(2)

    dut.io.axi.w.valid #= true
    dut.io.axi.w.last #= true
    dut.io.axi.w.payload.data #= data
    dut.io.axi.w.strb #= BigInt("1111", 2)

    dut.clockDomain.waitSampling(4)
    assert(dut.io.axi.w.ready.toBoolean == true)
    dut.io.axi.w.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == false)
  }

  def writeShort(dut: Axi4SharedIhpOnChipRam.OnePort1024x8, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(1)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= true

    dut.clockDomain.waitSampling(2)

    dut.io.axi.w.valid #= true
    dut.io.axi.w.last #= true
    dut.io.axi.w.payload.data #= data
    dut.io.axi.w.strb #= BigInt("0011", 2)

    dut.clockDomain.waitSampling(4)
    assert(dut.io.axi.w.ready.toBoolean == true)
    dut.io.axi.w.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == false)
  }

  def writeChar(dut: Axi4SharedIhpOnChipRam.OnePort1024x8, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(0)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= true

    dut.clockDomain.waitSampling(2)

    dut.io.axi.w.valid #= true
    dut.io.axi.w.last #= true
    dut.io.axi.w.payload.data #= data
    dut.io.axi.w.strb #= BigInt("0001", 2)

    dut.clockDomain.waitSampling(4)
    assert(dut.io.axi.w.ready.toBoolean == true)
    dut.io.axi.w.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == false)
  }

  def readAndCheckWord(dut: Axi4SharedIhpOnChipRam.OnePort1024x8, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(2)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= false

    dut.clockDomain.waitSampling(6)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.clockDomain.waitSampling(1)
    dut.io.axi.arw.valid #= false
    assert(dut.io.axi.r.valid.toBoolean == true)
    assert(dut.io.axi.r.payload.data.toBigInt == data)
    dut.io.axi.r.valid #= true
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.io.axi.r.valid #= false
  }

  def readAndCheckShort(dut: Axi4SharedIhpOnChipRam.OnePort1024x8, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(1)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= false

    dut.clockDomain.waitSampling(6)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.clockDomain.waitSampling(1)
    dut.io.axi.arw.valid #= false
    assert(dut.io.axi.r.valid.toBoolean == true)
    assert(dut.io.axi.r.payload.data.toBigInt == data)
    dut.io.axi.r.valid #= true
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.io.axi.r.valid #= false
  }

  def readAndCheckChar(dut: Axi4SharedIhpOnChipRam.OnePort1024x8, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(0)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= false

    dut.clockDomain.waitSampling(6)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.clockDomain.waitSampling(1)
    dut.io.axi.arw.valid #= false
    assert(dut.io.axi.r.valid.toBoolean == true)
    assert(dut.io.axi.r.payload.data.toBigInt == data)
    dut.io.axi.r.valid #= true
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.io.axi.r.valid #= false
  }

  test("Axi4SharedIhpOnChipRam") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Axi4SharedIhpOnChipRam.OnePort1024x8(32, 1 kB, 4)
      }
      area.dut
    }
    compiled.doSim("default signals") { dut =>
      defaultSignals(dut)
    }

    compiled.doSim("write + read 0x0000 - word") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("76543210", 16))

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("write + read 0x0001 - word") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000001", 16), BigInt("76543210", 16))
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000001", 16), BigInt("76543210", 16))

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("write + read 0x0002 - word") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000002", 16), BigInt("76543210", 16))
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000002", 16), BigInt("76543210", 16))

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("write + read 0x0003 - word") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000003", 16), BigInt("76543210", 16))
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000003", 16), BigInt("76543210", 16))

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("write + read - char") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      writeWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))

      writeChar(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("00000010", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckChar(dut, BigInt("00000000", 16), BigInt("10101010", 16))

      writeChar(dut, BigInt("00000001", 16), BigInt("98765432", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("00003210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckChar(dut, BigInt("00000001", 16), BigInt("32323232", 16))

      writeChar(dut, BigInt("00000002", 16), BigInt("11987654", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("00543210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckChar(dut, BigInt("00000002", 16), BigInt("54545454", 16))

      writeChar(dut, BigInt("00000003", 16), BigInt("22119876", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckChar(dut, BigInt("00000003", 16), BigInt("76767676", 16))

      writeChar(dut, BigInt("00000004", 16), BigInt("33221198", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000098", 16))
      readAndCheckChar(dut, BigInt("00000004", 16), BigInt("98989898", 16))

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("write + read - short") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      writeWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))

      writeShort(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("00003210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckShort(dut, BigInt("00000000", 16), BigInt("32323210", 16))

      writeShort(dut, BigInt("00000001", 16), BigInt("98765432", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("00543210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckShort(dut, BigInt("00000001", 16), BigInt("54545432", 16))

      writeShort(dut, BigInt("00000002", 16), BigInt("11987654", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000000", 16))
      readAndCheckShort(dut, BigInt("00000002", 16), BigInt("76767654", 16))

      writeShort(dut, BigInt("00000003", 16), BigInt("22119876", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00000098", 16))
      readAndCheckShort(dut, BigInt("00000003", 16), BigInt("98989876", 16))

      writeShort(dut, BigInt("00000004", 16), BigInt("33221198", 16))
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("76543210", 16))
      readAndCheckWord(dut, BigInt("00000004", 16), BigInt("00001198", 16))
      readAndCheckShort(dut, BigInt("00000004", 16), BigInt("11111198", 16))

      dut.clockDomain.waitSampling(20)
    }
  }
}
