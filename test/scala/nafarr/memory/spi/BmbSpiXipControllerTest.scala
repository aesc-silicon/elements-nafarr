// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.spi

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.bmb._
import spinal.lib.bus.bmb.sim.BmbDriver
import spinal.lib.bus.wishbone._
import nafarr.CheckTester._

import nafarr.peripherals.com.spi.{Spi, SpiControllerCtrl}

class BmbSpiXipControllerTest extends AnyFunSuite {

  test("BmbSpiXipController") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val parameter = SpiControllerCtrl.Parameter.xip()
        val dataBusParameter = BmbParameter(
          addressWidth = 32,
          dataWidth = 32,
          lengthWidth = 6,
          sourceWidth = 4,
          contextWidth = 4
        )
        val cfgBusParameter = WishboneConfig(addressWidth = 32, dataWidth = 32)
        val dut = BmbSpiXipController(parameter, dataBusParameter, cfgBusParameter)
      }
      area.dut
    }
    compiled.doSim("single write - error") { dut =>
      dut.clockDomain.forkStimulus(10)

      val bmb = new BmbDriver(dut.io.dataBus, dut.clockDomain)
      bmb.write(0x0, 0x0)

      dut.clockDomain.waitSampling(10)
    }
    compiled.doSim("single read") { dut =>
      dut.clockDomain.forkStimulus(10)

      val bmb = new BmbDriver(dut.io.dataBus, dut.clockDomain)
      bmb.read(0x0)

      dut.clockDomain.waitSampling(100)
    }
    compiled.doSim("three read commands") { dut =>
      dut.clockDomain.forkStimulus(10)

      val bmb = new BmbDriver(dut.io.dataBus, dut.clockDomain)
      bmb.read(0x0)
      bmb.read(0x4)
      bmb.read(0x8)

      dut.clockDomain.waitSampling(100)
    }
  }
}
