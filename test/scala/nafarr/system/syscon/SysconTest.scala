// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.syscon

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest
import nafarr.{Vendor, Platform, PlatformClass, Product, Feature}

class SysconTest extends AnyFunSuite {

  def baseParam = Syscon.Parameter(
    vendor       = Vendor.AescSilicon,
    platform     = Platform.Hydrogen,
    platformClass = PlatformClass.NonMetal,
    product      = Product.ElemRV,
    refClockHz   = 24000000L
  )

  test("Apb3Parameter") {
    generationShouldPass(Apb3Syscon(baseParam))
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Carbon)))
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Sulfur)))
    generationShouldPass(Apb3Syscon(baseParam.copy(siliconMajor = 3, siliconMinor = 7)))
    generationShouldPass(Apb3Syscon(baseParam.copy(features = List(Feature.Uart, Feature.Gpio))))
    generationShouldPass(Apb3Syscon(baseParam.copy(refClockHz = 48000000L)))
  }

  test("TileLinkParameter") {
    generationShouldPass(TileLinkSyscon(baseParam))
    generationShouldPass(TileLinkSyscon(baseParam.copy(platform = Platform.Nitrogen)))
  }

  test("WishboneParameter") {
    generationShouldPass(WishboneSyscon(baseParam))
    generationShouldPass(WishboneSyscon(baseParam.copy(platform = Platform.Oxygen)))
  }

  def init(dut: Apb3Syscon): (Apb3Driver, Syscon.Regs) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    val regs = Syscon.Regs(dut.mapper.idCtrl.length)
    dut.clockDomain.forkStimulus(10)
    (driver, regs)
  }

  test("IpIdentification") {
    SimConfig.withWave.compile(Apb3Syscon(baseParam))
      .doSim("IpIdentification") { dut =>
        val (driver, _) = init(dut)
        IpIdentificationTest.V0.checkApi(driver, IpIdentification.Ids.Syscon)
        IpIdentificationTest.V0.checkVersion(driver, 1, 0, 0)
      }
  }

  test("Identity register") {
    val p = baseParam.copy(
      vendor = Vendor.AescSilicon,  // ordinal 0
      platform = Platform.Carbon,  // ordinal 1
      platformClass = PlatformClass.NonMetal,  // ordinal 0
      product = Product.ElemRV  // ordinal 0
    )
    SimConfig.withWave.compile(Apb3Syscon(p))
      .doSim("IdentityRegister") { dut =>
        val (driver, regs) = init(dut)
        SimTest.readField(driver, regs.identity,  7,  0, 0, "vendor=AescSilicon")
        SimTest.readField(driver, regs.identity, 15,  8, 1, "platform=Carbon")
        SimTest.readField(driver, regs.identity, 23, 16, 0, "product=ElemRV")
        SimTest.readField(driver, regs.identity, 31, 24, 0, "platformClass=NonMetal")
      }
  }

  test("Silicon revision registers") {
    val p = baseParam.copy(siliconMajor = 2, siliconMinor = 5)
    SimConfig.withWave.compile(Apb3Syscon(p))
      .doSim("SiliconRevision") { dut =>
        val (driver, regs) = init(dut)
        SimTest.read(driver, regs.siliconMajor, 2, "siliconMajor=2")
        SimTest.read(driver, regs.siliconMinor, 5, "siliconMinor=5")
      }
  }

  test("Features register") {
    // Uart=ordinal 2, Gpio=ordinal 3, Watchdog=ordinal 14
    val p = baseParam.copy(features = List(Feature.Uart, Feature.Gpio, Feature.Watchdog))
    val expectedMask = (1 << Feature.Uart.position) |
                       (1 << Feature.Gpio.position) |
                       (1 << Feature.Watchdog.position)
    SimConfig.withWave.compile(Apb3Syscon(p))
      .doSim("FeaturesRegister") { dut =>
        val (driver, regs) = init(dut)
        SimTest.read(driver, regs.features, expectedMask, "features bitmask")
      }
  }

  test("Features register - empty") {
    SimConfig.withWave.compile(Apb3Syscon(baseParam))
      .doSim("FeaturesEmpty") { dut =>
        val (driver, regs) = init(dut)
        SimTest.read(driver, regs.features, 0, "no features")
      }
  }

  test("RefClock register") {
    val p = baseParam.copy(refClockHz = 48000000L)
    SimConfig.withWave.compile(Apb3Syscon(p))
      .doSim("RefClockRegister") { dut =>
        val (driver, regs) = init(dut)
        SimTest.read(driver, regs.refClock, 48000000L, "refClockHz=48MHz")
      }
  }

  test("BuildDate register") {
    val ts = 1748000000L
    val p = baseParam.copy(buildDate = ts)
    SimConfig.withWave.compile(Apb3Syscon(p))
      .doSim("BuildDateRegister") { dut =>
        val (driver, regs) = init(dut)
        SimTest.read(driver, regs.buildDate, ts, "buildDate timestamp")
      }
  }

  test("All platforms generate") {
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Hydrogen)))
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Carbon)))
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Nitrogen)))
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Oxygen)))
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Phosphorus)))
    generationShouldPass(Apb3Syscon(baseParam.copy(platform = Platform.Sulfur)))
  }

  test("PlatformClass Alkali generates") {
    generationShouldPass(Apb3Syscon(baseParam.copy(platformClass = PlatformClass.Alkali)))
  }
}
