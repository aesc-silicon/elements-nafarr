// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.spi

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.amba4.axi.sim.Axi4Master
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import nafarr.CheckTester._

import nafarr.peripherals.com.spi.{Spi, SpiControllerCtrl}

class Axi4ReadOnlySpiXipControllerTest extends AnyFunSuite {
  test("Axi4ReadOnlySpiXipControllerTest") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val dut = Axi4ReadOnlySpiXipController(SpiControllerCtrl.Parameter.xip())
      }
      area.dut
    }
    compiled.doSim("default signals") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.cfgSpiBus.PENABLE #= false
      dut.io.cfgXipBus.PENABLE #= false
      dut.io.dataBus.ar.valid #= false
      dut.io.dataBus.r.ready #= false
      dut.clockDomain.waitSampling(5)

      assert(dut.io.dataBus.ar.ready.toBoolean == false)
      assert(dut.io.dataBus.r.valid.toBoolean == false)

      dut.clockDomain.waitSampling(20)
    }
    compiled.doSim("single read") { dut =>
      dut.clockDomain.forkStimulus(10)

      dut.io.cfgSpiBus.PENABLE #= false
      dut.io.cfgXipBus.PENABLE #= false
      dut.io.dataBus.ar.valid #= false
      dut.io.dataBus.r.ready #= false
      dut.io.spi.dq.read #= BigInt("10", 2)
      dut.clockDomain.waitSampling(5)

      dut.io.dataBus.ar.valid #= true
      dut.io.dataBus.ar.addr #= BigInt("12345", 16)
      dut.io.dataBus.ar.len #= BigInt("00000000", 2)
      dut.io.dataBus.ar.size #= BigInt("011", 2)
      dut.io.dataBus.ar.burst #= BigInt("01", 2)
      dut.io.dataBus.ar.id #= BigInt("C", 16)
      sleep(2)
      assert(dut.io.dataBus.ar.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.dataBus.ar.valid #= false
      assert(dut.io.spi.cs.toBigInt == BigInt("1", 2))
      dut.clockDomain.waitSampling(4)
      for (_ <- 0 to 5) {
        sleep(2)
        assert(dut.io.spi.cs.toBigInt == BigInt("0", 2))
        dut.clockDomain.waitSampling(1000)
      }
      assert(dut.io.spi.cs.toBigInt == BigInt("0", 2))
      dut.clockDomain.waitSampling(700)
      assert(dut.io.dataBus.r.valid.toBoolean == true)
      assert(dut.io.dataBus.r.id.toBigInt == BigInt("C", 16))
      assert(dut.io.dataBus.r.data.toBigInt == BigInt("FFFFFFFF", 16))
      assert(dut.io.dataBus.r.last.toBoolean == true)
      sleep(2)
      dut.io.dataBus.r.ready #= true
      dut.clockDomain.waitSampling(1)
      assert(dut.io.dataBus.r.valid.toBoolean == true)
      dut.io.dataBus.r.ready #= false
      dut.clockDomain.waitSampling(20)
    }
/*

    No burst support right now.

    compiled.doSim("burst read") { dut =>
      dut.clockDomain.forkStimulus(10)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      dut.io.cfgBus.PENABLE #= false
      dut.io.dataBus.ar.valid #= false
      dut.io.dataBus.r.ready #= false
      dut.io.spi.dq.read #= BigInt("10", 2)
      dut.clockDomain.waitSampling(5)

      dut.io.dataBus.ar.valid #= true
      dut.io.dataBus.ar.addr #= BigInt("12345", 16)
      dut.io.dataBus.ar.len #= BigInt("00000001", 2)
      dut.io.dataBus.ar.size #= BigInt("011", 2)
      dut.io.dataBus.ar.burst #= BigInt("01", 2)
      dut.io.dataBus.ar.id #= BigInt("C", 16)
      sleep(2)
      assert(dut.io.dataBus.ar.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.dataBus.ar.valid #= false
      assert(dut.io.spi.cs.toBigInt == BigInt("1", 2))
      dut.clockDomain.waitSampling(3)
      for (_ <- 0 to 9) {
        sleep(2)
        assert(dut.io.spi.cs.toBigInt == BigInt("0", 2))
        dut.clockDomain.waitSampling(1000)
      }
      assert(dut.io.spi.cs.toBigInt == BigInt("1", 2))

      assert(dut.io.dataBus.r.valid.toBoolean == true)
      assert(dut.io.dataBus.r.id.toBigInt == BigInt("C", 16))
      assert(dut.io.dataBus.r.data.toBigInt == BigInt("FFFFFFFF", 16))
      assert(dut.io.dataBus.r.last.toBoolean == false)
      sleep(2)
      dut.io.dataBus.r.ready #= true
      dut.clockDomain.waitSampling(1)
      assert(dut.io.dataBus.r.valid.toBoolean == true)
      dut.io.dataBus.r.ready #= false

      dut.clockDomain.waitSampling(8)
      assert(dut.io.dataBus.r.valid.toBoolean == true)
      assert(dut.io.dataBus.r.id.toBigInt == BigInt("C", 16))
      assert(dut.io.dataBus.r.data.toBigInt == BigInt("FFFFFFFF", 16))
      assert(dut.io.dataBus.r.last.toBoolean == true)
      sleep(2)
      dut.io.dataBus.r.ready #= true
      dut.clockDomain.waitSampling(1)
      assert(dut.io.dataBus.r.valid.toBoolean == true)
      dut.io.dataBus.r.ready #= false

      dut.clockDomain.waitSampling(20)
    }
*/
  }
}
