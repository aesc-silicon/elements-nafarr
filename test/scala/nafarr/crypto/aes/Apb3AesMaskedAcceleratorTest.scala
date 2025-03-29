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
  }
}
