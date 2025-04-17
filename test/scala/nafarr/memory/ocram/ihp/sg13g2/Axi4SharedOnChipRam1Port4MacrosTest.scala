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

class Axi4SharedOnChipRam1Port4MacrosTest extends AnyFunSuite {

  def defaultSignals(dut: Axi4SharedIhpOnChipRam.OnePort4Macros) = {
    dut.clockDomain.forkStimulus(10)
    dut.io.axi.arw.valid #= false
    dut.io.axi.w.valid #= false
    dut.io.axi.r.ready #= false
    dut.io.axi.b.ready #= false
    dut.clockDomain.waitSampling(5)
  }

  def writeWord(dut: Axi4SharedIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt, offset: Int = 0) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(2)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= true

    dut.clockDomain.waitSampling(1)

    dut.io.axi.w.valid #= true
    dut.io.axi.w.last #= true
    dut.io.axi.w.payload.data #= data
    offset match {
      case 1 => dut.io.axi.w.strb #= BigInt("1110", 2)
      case 2 => dut.io.axi.w.strb #= BigInt("1100", 2)
      case 3 => dut.io.axi.w.strb #= BigInt("1000", 2)
      case _ => dut.io.axi.w.strb #= BigInt("1111", 2)
    }

    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.w.ready.toBoolean == true)
    dut.io.axi.w.valid #= false

    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
  }

  def writeShort(dut: Axi4SharedIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt, offset: Int = 0) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(1)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= true

    dut.clockDomain.waitSampling(1)

    dut.io.axi.w.valid #= true
    dut.io.axi.w.last #= true
    dut.io.axi.w.payload.data #= data
    offset match {
      case 1 => dut.io.axi.w.strb #= BigInt("0110", 2)
      case 2 => dut.io.axi.w.strb #= BigInt("1100", 2)
      case 3 => dut.io.axi.w.strb #= BigInt("1000", 2)
      case _ => dut.io.axi.w.strb #= BigInt("0011", 2)
    }

    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.w.ready.toBoolean == true)
    dut.io.axi.w.valid #= false

    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
  }

  def writeChar(dut: Axi4SharedIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt, offset: Int = 0) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(0)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= true

    dut.clockDomain.waitSampling(1)

    dut.io.axi.w.valid #= true
    dut.io.axi.w.last #= true
    dut.io.axi.w.payload.data #= data
    offset match {
      case 1 => dut.io.axi.w.strb #= BigInt("0010", 2)
      case 2 => dut.io.axi.w.strb #= BigInt("0100", 2)
      case 3 => dut.io.axi.w.strb #= BigInt("1000", 2)
      case _ => dut.io.axi.w.strb #= BigInt("0001", 2)
    }

    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.w.ready.toBoolean == true)
    dut.io.axi.w.valid #= false

    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
  }

  def readAndCheckWord(dut: Axi4SharedIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(2)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= false

    dut.io.axi.r.ready #= true

    dut.clockDomain.waitSampling(2)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == true)
    assert(dut.io.axi.r.payload.data.toBigInt == data)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.io.axi.r.ready #= false
  }

  def readAndCheckShort(dut: Axi4SharedIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(1)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= false

    dut.io.axi.r.ready #= true

    dut.clockDomain.waitSampling(2)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == true)
    assert(dut.io.axi.r.payload.data.toBigInt == data)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.io.axi.r.valid #= false
  }

  def readAndCheckChar(dut: Axi4SharedIhpOnChipRam.OnePort4Macros, address: BigInt, data: BigInt) = {
    dut.io.axi.arw.valid #= true
    dut.io.axi.arw.addr #= address
    dut.io.axi.arw.size #= BigInt(0)
    dut.io.axi.arw.len #= BigInt(0)
    dut.io.axi.arw.burst #= BigInt(1)
    dut.io.axi.arw.id #= BigInt(13)
    dut.io.axi.arw.write #= false

    dut.io.axi.r.ready #= true

    dut.clockDomain.waitSampling(2)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == true)
    assert(dut.io.axi.r.payload.data.toBigInt == data)
    assert(dut.io.axi.arw.ready.toBoolean == true)
    dut.io.axi.arw.valid #= false
    dut.clockDomain.waitSampling(1)
    assert(dut.io.axi.r.valid.toBoolean == false)
    dut.io.axi.r.valid #= false
  }

  test("Axi4SharedOnChipRam1Port4Macros") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Axi4SharedIhpOnChipRam.OnePort4Macros(32, 4 kB, 4)
      }
      area.dut
    }
    compiled.doSim("default signals") { dut =>
      defaultSignals(dut)
    }

    compiled.doSim("word") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeWord(dut, BigInt("00000000", 16), BigInt("44332211", 16))
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("44332211", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeWord(dut, BigInt("00000001", 16), BigInt("44332211", 16), 1)
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("44332200", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeWord(dut, BigInt("00000002", 16), BigInt("44332211", 16), 2)
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("44330000", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeWord(dut, BigInt("00000003", 16), BigInt("44332211", 16), 3)
      dut.clockDomain.waitSampling(2)
      readAndCheckWord(dut, BigInt("00000000", 16), BigInt("44000000", 16))

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("short") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeShort(dut, BigInt("00000000", 16), BigInt("22112211", 16))
      dut.clockDomain.waitSampling(2)
      readAndCheckShort(dut, BigInt("00000000", 16), BigInt("00002211", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeShort(dut, BigInt("00000001", 16), BigInt("22112211", 16), 1)
      dut.clockDomain.waitSampling(2)
      readAndCheckShort(dut, BigInt("00000000", 16), BigInt("00112200", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeShort(dut, BigInt("00000002", 16), BigInt("22112211", 16), 2)
      dut.clockDomain.waitSampling(2)
      readAndCheckShort(dut, BigInt("00000000", 16), BigInt("22110000", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeShort(dut, BigInt("00000003", 16), BigInt("22112211", 16), 3)
      dut.clockDomain.waitSampling(2)
      readAndCheckShort(dut, BigInt("00000000", 16), BigInt("22000000", 16))

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("char") { dut =>
      defaultSignals(dut)

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeChar(dut, BigInt("00000000", 16), BigInt("11111111", 16))
      dut.clockDomain.waitSampling(2)
      readAndCheckChar(dut, BigInt("00000000", 16), BigInt("00000011", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeChar(dut, BigInt("00000001", 16), BigInt("11111111", 16), 1)
      dut.clockDomain.waitSampling(2)
      readAndCheckChar(dut, BigInt("00000000", 16), BigInt("00001100", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeChar(dut, BigInt("00000002", 16), BigInt("11111111", 16), 2)
      dut.clockDomain.waitSampling(2)
      readAndCheckChar(dut, BigInt("00000000", 16), BigInt("00110000", 16))

      writeWord(dut, BigInt("00000000", 16), BigInt("00000000", 16))
      dut.clockDomain.waitSampling(2)
      writeChar(dut, BigInt("00000003", 16), BigInt("11111111", 16), 3)
      dut.clockDomain.waitSampling(2)
      readAndCheckChar(dut, BigInt("00000000", 16), BigInt("11000000", 16))

      dut.clockDomain.waitSampling(20)
    }
  }
}
