package nafarr.peripherals.com.chip2chip

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.ListBuffer

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.amba4.axi.sim._
import nafarr.CheckTester._


class Axi4InterfaceTest extends AnyFunSuite {

  def loopback(dut: Interface.Axi4Interface) {
    var data = ListBuffer[BigInt]()
    for (index <- 0 until 10) {
      data += dut.io.txPhy.data.toBigInt
      dut.clockDomain.waitSampling(1)
      sleep(1)
    }
    println(data)

    dut.clockDomain.waitSampling(6)
    sleep(1)

    dut.io.rxPhy.enable #= true
    for (index <- 0 until 10) {
      val check = data(index)
      dut.io.rxPhy.data #= check
      dut.clockDomain.waitSampling(1)
      sleep(1)
    }
    dut.io.rxPhy.enable #= false
  }

  def loopback2(dut: Interface.Axi4Interface) {
    var data0 = ListBuffer[BigInt]()
    for (index <- 0 until 10) {
      data0 += dut.io.txPhy.data.toBigInt
      dut.clockDomain.waitSampling(1)
      sleep(1)
    }
    println(data0)

    dut.clockDomain.waitSampling(2)
    sleep(1)

    var data1 = ListBuffer[BigInt]()
    for (index <- 0 until 10) {
      data1 += dut.io.txPhy.data.toBigInt
      dut.clockDomain.waitSampling(1)
      sleep(1)
    }
    println(data1)

    dut.clockDomain.waitSampling(4)
    sleep(1)

    dut.io.rxPhy.enable #= true
    for (index <- 0 until 10) {
      val check = data0(index)
      dut.io.rxPhy.data #= check
      dut.clockDomain.waitSampling(1)
      sleep(1)
    }
    dut.io.rxPhy.enable #= false

    dut.clockDomain.waitSampling(2)
    sleep(1)

    dut.io.rxPhy.enable #= true
    for (index <- 0 until 10) {
      val check = data1(index)
      dut.io.rxPhy.data #= check
      dut.clockDomain.waitSampling(1)
      sleep(1)
    }
    dut.io.rxPhy.enable #= false
  }


  test("Interface send out") {
    val compiled = SimConfig.withWave.compile {
      val config = Axi4Config(64, 128, 14)
      val dut = Interface.Axi4Interface(config)
      dut
    }
    compiled.doSim("AXI4AR") { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      dut.io.axiIn.aw.valid #= false
      dut.io.axiIn.ar.valid #= false
      dut.io.axiIn.w.valid #= false
      dut.io.axiOut.r.valid #= false
      dut.io.axiOut.b.valid #= false
      dut.io.txPhy.stall #= false
      dut.io.rxPhy.enable #= false
      dut.io.axiOut.aw.ready #= false
      dut.io.axiOut.ar.valid #= false
      dut.io.axiOut.w.valid #= false
      dut.io.axiIn.r.valid #= false
      dut.io.axiIn.b.valid #= false

      dut.clockDomain.waitSampling(5)
      dut.io.axiIn.ar.valid #= true
      dut.io.axiIn.ar.addr #= BigInt("F" * 16, 16)
      dut.io.axiIn.ar.id #= BigInt(0)
      dut.io.axiIn.ar.region #= BigInt(0)
      dut.io.axiIn.ar.len #= BigInt(3)
      dut.io.axiIn.ar.size #= BigInt(5)
      dut.io.axiIn.ar.burst #= BigInt(0)
      dut.io.axiIn.ar.lock #= BigInt(0)
      dut.io.axiIn.ar.cache #= BigInt(0)
      dut.io.axiIn.ar.qos #= BigInt(0)
      dut.io.axiIn.ar.prot #= BigInt(0)
      dut.clockDomain.waitSampling(3)
      sleep(1)
      dut.io.axiIn.ar.valid #= false

      dut.clockDomain.waitSampling(6)
      sleep(1)

      loopback(dut)

      dut.clockDomain.waitSampling(6)
      sleep(1)
      assert(dut.io.axiOut.ar.valid.toBoolean == true)
      assert(dut.io.axiOut.ar.addr.toBigInt == BigInt("F" * 16, 16))
      assert(dut.io.axiOut.ar.id.toBigInt == BigInt(0))
      assert(dut.io.axiOut.ar.region.toBigInt == BigInt(0))
      assert(dut.io.axiOut.ar.len.toBigInt == BigInt(3))
      assert(dut.io.axiOut.ar.size.toBigInt == BigInt(5))
      assert(dut.io.axiOut.ar.burst.toBigInt == BigInt(0))
      assert(dut.io.axiOut.ar.lock.toBigInt == BigInt(0))
      assert(dut.io.axiOut.ar.cache.toBigInt == BigInt(0))
      assert(dut.io.axiOut.ar.qos.toBigInt == BigInt(0))
      assert(dut.io.axiOut.ar.prot.toBigInt == BigInt(0))

      dut.clockDomain.waitSampling(10)
    }

    compiled.doSim("AXI4AW") { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      dut.io.axiIn.aw.valid #= false
      dut.io.axiIn.ar.valid #= false
      dut.io.axiIn.w.valid #= false
      dut.io.axiOut.r.valid #= false
      dut.io.axiOut.b.valid #= false
      dut.io.txPhy.stall #= false
      dut.io.rxPhy.enable #= false
      dut.io.axiOut.aw.ready #= false
      dut.io.axiOut.ar.valid #= false
      dut.io.axiOut.w.valid #= false
      dut.io.axiIn.r.valid #= false
      dut.io.axiIn.b.valid #= false

      dut.clockDomain.waitSampling(5)
      dut.io.axiIn.aw.valid #= true
      dut.io.axiIn.aw.addr #= BigInt("F" * 16, 16)
      dut.io.axiIn.aw.id #= BigInt(0)
      dut.io.axiIn.aw.region #= BigInt(0)
      dut.io.axiIn.aw.len #= BigInt(0)
      dut.io.axiIn.aw.size #= BigInt(0)
      dut.io.axiIn.aw.burst #= BigInt(0)
      dut.io.axiIn.aw.lock #= BigInt(0)
      dut.io.axiIn.aw.cache #= BigInt(0)
      dut.io.axiIn.aw.qos #= BigInt(0)
      dut.io.axiIn.aw.prot #= BigInt(0)
      dut.clockDomain.waitSampling(3)
      sleep(1)
      dut.io.axiIn.aw.valid #= false

      dut.clockDomain.waitSampling(6)
      sleep(1)

      loopback(dut)

      dut.clockDomain.waitSampling(6)
      sleep(1)
      assert(dut.io.axiOut.aw.valid.toBoolean == true)
      assert(dut.io.axiOut.aw.addr.toBigInt == BigInt("F" * 16, 16))
      assert(dut.io.axiOut.aw.id.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.region.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.len.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.size.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.burst.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.lock.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.cache.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.qos.toBigInt == BigInt(0))
      assert(dut.io.axiOut.aw.prot.toBigInt == BigInt(0))

      dut.clockDomain.waitSampling(10)
    }

    compiled.doSim("AXI4W") { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      dut.io.axiIn.aw.valid #= false
      dut.io.axiIn.ar.valid #= false
      dut.io.axiIn.w.valid #= false
      dut.io.axiOut.r.valid #= false
      dut.io.axiOut.b.valid #= false
      dut.io.txPhy.stall #= false
      dut.io.rxPhy.enable #= false
      dut.io.axiOut.aw.ready #= false
      dut.io.axiOut.ar.valid #= false
      dut.io.axiOut.w.valid #= false
      dut.io.axiIn.r.valid #= false
      dut.io.axiIn.b.valid #= false

      dut.clockDomain.waitSampling(5)
      dut.io.axiIn.w.valid #= true
      dut.io.axiIn.w.data #= BigInt("F" * 32, 16)
      dut.io.axiIn.w.strb #= BigInt(0)
      dut.io.axiIn.w.last #= false

      dut.clockDomain.waitSampling(3)
      sleep(1)
      dut.io.axiIn.w.valid #= false

      dut.clockDomain.waitSampling(6)
      sleep(1)

      loopback2(dut)

      dut.clockDomain.waitSampling(6)
      sleep(1)
      assert(dut.io.axiOut.w.valid.toBoolean == true)
      assert(dut.io.axiOut.w.data.toBigInt == BigInt("F" * 32, 16))
      assert(dut.io.axiOut.w.strb.toBigInt == BigInt(0))
      assert(dut.io.axiOut.w.last.toBoolean == false)

      dut.clockDomain.waitSampling(10)
    }

    compiled.doSim("AXI4R") { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      dut.io.axiIn.aw.valid #= false
      dut.io.axiIn.ar.valid #= false
      dut.io.axiIn.w.valid #= false
      dut.io.axiOut.r.valid #= false
      dut.io.axiOut.b.valid #= false
      dut.io.txPhy.stall #= false
      dut.io.rxPhy.enable #= false
      dut.io.axiOut.aw.ready #= false
      dut.io.axiOut.ar.valid #= false
      dut.io.axiOut.w.valid #= false
      dut.io.axiIn.r.valid #= false
      dut.io.axiIn.b.valid #= false

      dut.clockDomain.waitSampling(5)
      dut.io.axiOut.r.valid #= true
      dut.io.axiOut.r.data #= BigInt("F" * 32, 16)
      dut.io.axiOut.r.resp #= BigInt(0)
      dut.io.axiOut.r.last #= false

      dut.clockDomain.waitSampling(3)
      sleep(1)
      dut.io.axiOut.r.valid #= false

      dut.clockDomain.waitSampling(6)
      sleep(1)

      loopback2(dut)

      dut.clockDomain.waitSampling(6)
      sleep(1)
      assert(dut.io.axiIn.r.valid.toBoolean == true)
      assert(dut.io.axiIn.r.data.toBigInt == BigInt("F" * 32, 16))
      assert(dut.io.axiIn.r.resp.toBigInt == BigInt(0))
      assert(dut.io.axiIn.r.last.toBoolean == false)

      dut.clockDomain.waitSampling(10)
    }

    compiled.doSim("AXI4B") { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      dut.io.axiIn.aw.valid #= false
      dut.io.axiIn.ar.valid #= false
      dut.io.axiIn.w.valid #= false
      dut.io.axiOut.r.valid #= false
      dut.io.axiOut.b.valid #= false
      dut.io.txPhy.stall #= false
      dut.io.rxPhy.enable #= false
      dut.io.axiOut.aw.ready #= false
      dut.io.axiOut.ar.valid #= false
      dut.io.axiOut.w.valid #= false
      dut.io.axiIn.r.valid #= false
      dut.io.axiIn.b.valid #= false

      dut.clockDomain.waitSampling(5)
      dut.io.axiOut.b.valid #= true
      dut.io.axiOut.b.id #= BigInt(0)
      dut.io.axiOut.b.resp #= BigInt(0)
      dut.clockDomain.waitSampling(3)
      sleep(1)
      dut.io.axiOut.b.valid #= false

      dut.clockDomain.waitSampling(6)
      sleep(1)

      loopback(dut)

      dut.clockDomain.waitSampling(6)
      sleep(1)
      assert(dut.io.axiIn.b.valid.toBoolean == true)
      assert(dut.io.axiIn.b.id.toBigInt == BigInt(0))
      assert(dut.io.axiIn.b.resp.toBigInt == BigInt(0))

      dut.clockDomain.waitSampling(10)
    }
  }
}
