package nafarr.memory.hyperbus

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.amba4.axi.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import nafarr.CheckTester._

class Axi4SharedHyperBusTest extends AnyFunSuite {

  test("Axi4SharedHyperBus") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val hyperbusPartitions = List[(BigInt, Boolean)]((0x800000L, true))
        val dut = Axi4SharedHyperBus(HyperBusCtrl.Parameter.default(hyperbusPartitions))
      }
      area.dut.hyperbus.io.controller.simPublic()
      area.dut.hyperbus.ctrl.io.controller.valid.simPublic()
      area.dut.hyperbus.ctrl.io.controller.payload.id.simPublic()
      area.dut.hyperbus.ctrl.io.controller.payload.read.simPublic()
      area.dut.hyperbus.ctrl.io.controller.payload.unaligned.simPublic()
      area.dut.hyperbus.ctrl.io.controller.payload.addr.simPublic()
      area.dut.hyperbus.ctrl.io.controller.payload.data.simPublic()
      area.dut.hyperbus.ctrl.io.controller.payload.strobe.simPublic()
      area.dut.hyperbus.ctrl.io.controller.payload.last.simPublic()
      area.dut.hyperbus.ctrl.io.frontend.simPublic()
      area.dut
    }
    compiled.doSim("default signals") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.memory.arw.valid #= false
      dut.io.memory.w.valid #= false
      dut.io.memory.r.ready #= false
      dut.io.memory.b.ready #= false
      dut.clockDomain.waitSampling(5)

      assert(dut.hyperbus.io.controller.valid.toBoolean == false)
      assert(dut.hyperbus.io.controller.payload.id.toBigInt == BigInt(0))
      assert(dut.hyperbus.io.controller.payload.read.toBoolean == false)
      assert(dut.hyperbus.io.controller.payload.unaligned.toBoolean == false)
      assert(dut.hyperbus.io.controller.payload.addr.toBigInt == BigInt(0))
      assert(dut.hyperbus.io.controller.payload.data.toBigInt == BigInt(0))
      assert(dut.hyperbus.io.controller.payload.strobe.toBigInt == BigInt(0))
      assert(dut.hyperbus.io.controller.payload.last.toBoolean == false)

      assert(dut.hyperbus.ctrl.io.frontend.ready.toBoolean == false)
    }

    compiled.doSim("single read - no write") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.memory.arw.valid #= false
      dut.io.memory.w.valid #= false
      dut.io.memory.r.ready #= false
      dut.io.memory.b.ready #= false
      dut.clockDomain.waitSampling(5)

      dut.io.memory.arw.valid #= true
      dut.io.memory.arw.addr #= BigInt(100)
      dut.io.memory.arw.size #= BigInt(2)
      dut.io.memory.arw.len #= BigInt(0)
      dut.io.memory.arw.burst #= BigInt(0)
      dut.io.memory.arw.id #= BigInt(13)
      dut.io.memory.arw.write #= false

      sleep(2)
      assert(dut.io.memory.arw.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      sleep(2)
      dut.io.memory.arw.valid #= false
      assert(dut.hyperbus.ctrl.io.controller.payload.id.toBigInt == BigInt(1))
      assert(dut.hyperbus.ctrl.io.controller.payload.read.toBoolean == true)
      assert(dut.hyperbus.ctrl.io.controller.payload.unaligned.toBoolean == false)
      assert(dut.hyperbus.ctrl.io.controller.payload.addr.toBigInt == BigInt(50))
      assert(dut.hyperbus.ctrl.io.controller.payload.data.toBigInt == BigInt(0))
      assert(dut.hyperbus.ctrl.io.controller.payload.strobe.toBigInt == BigInt("1111", 2))
      assert(dut.hyperbus.ctrl.io.controller.payload.last.toBoolean == true)
      assert(dut.hyperbus.ctrl.io.controller.valid.toBoolean == true)

      dut.clockDomain.waitSampling(5)
      dut.hyperbus.ctrl.io.frontend.payload.data #= BigInt(123456)
      dut.hyperbus.ctrl.io.frontend.payload.id #= BigInt(1)
      dut.hyperbus.ctrl.io.frontend.payload.last #= true
      dut.hyperbus.ctrl.io.frontend.payload.read #= true
      dut.hyperbus.ctrl.io.frontend.valid #= true

      dut.io.memory.r.ready #= true
      sleep(2)
      assert(dut.io.memory.r.id.toBigInt == BigInt(13))
      //assert(dut.io.memory.r.data.toBigInt == BigInt(123456))
      assert(dut.io.memory.r.resp.toBigInt == BigInt(0))
      assert(dut.io.memory.r.last.toBoolean == true)
      assert(dut.io.memory.r.valid.toBoolean == true)
      assert(dut.io.memory.b.valid.toBoolean == false)

      dut.clockDomain.waitSampling(20)
    }

    compiled.doSim("single write - no read") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.memory.arw.valid #= false
      dut.io.memory.w.valid #= false
      dut.io.memory.r.ready #= false
      dut.io.memory.b.ready #= false
      dut.clockDomain.waitSampling(5)

      dut.io.memory.arw.valid #= true
      dut.io.memory.arw.addr #= BigInt(100)
      dut.io.memory.arw.size #= BigInt(2)
      dut.io.memory.arw.len #= BigInt(0)
      dut.io.memory.arw.burst #= BigInt(0)
      dut.io.memory.arw.id #= BigInt(15)
      dut.io.memory.arw.write #= true

      dut.io.memory.w.data #= BigInt("10010110111111110000000010100101", 2)

      sleep(2)
      assert(dut.io.memory.arw.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.memory.w.valid #= true
      dut.io.memory.arw.valid #= false
      sleep(2)
      assert(dut.io.memory.w.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.memory.w.valid #= false

      assert(dut.hyperbus.ctrl.io.controller.payload.id.toBigInt == BigInt(1))
      assert(dut.hyperbus.ctrl.io.controller.payload.read.toBoolean == false)
      assert(dut.hyperbus.ctrl.io.controller.payload.unaligned.toBoolean == false)
      assert(dut.hyperbus.ctrl.io.controller.payload.addr.toBigInt == BigInt(50))
      assert(dut.hyperbus.ctrl.io.controller.payload.data.toBigInt == BigInt("10010110111111110000000010100101", 2))
      assert(dut.hyperbus.ctrl.io.controller.payload.strobe.toBigInt == BigInt("1111", 2))
      assert(dut.hyperbus.ctrl.io.controller.payload.last.toBoolean == true)
      assert(dut.hyperbus.ctrl.io.controller.valid.toBoolean == true)

      dut.clockDomain.waitSampling(5)

      dut.hyperbus.ctrl.io.frontend.payload.data #= BigInt(0)
      dut.hyperbus.ctrl.io.frontend.payload.id #= BigInt(1)
      dut.hyperbus.ctrl.io.frontend.payload.last #= true
      dut.hyperbus.ctrl.io.frontend.payload.read #= false
      dut.hyperbus.ctrl.io.frontend.valid #= true

      sleep(2)
      assert(dut.io.memory.r.valid.toBoolean == false)
      assert(dut.io.memory.b.id.toBigInt == BigInt(15))
      assert(dut.io.memory.b.resp.toBigInt == BigInt(0))
      assert(dut.io.memory.b.valid.toBoolean == true)

      dut.clockDomain.waitSampling(20)
    }
  }

  test("Axi4SharedHyperBus-Integration") {
    val compiled = SimConfig.withWave.compile {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val hyperbusPartitions = List[(BigInt, Boolean)]((0x800000L, true))
        val dut = Axi4SharedHyperBus(HyperBusCtrl.Parameter.default(hyperbusPartitions))
      }
      area.dut
    }

    compiled.doSim("default signals") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.memory.arw.valid #= false
      dut.io.memory.w.valid #= false
      dut.io.memory.r.ready #= false
      dut.io.memory.b.ready #= false
      dut.io.phy.cmd.ready #= false
      dut.io.phy.rsp.valid #= false
      dut.clockDomain.waitSampling(5)

      assert(dut.io.phy.cmd.valid.toBoolean == false)
      assert(dut.io.phy.rsp.ready.toBoolean == false)
    }
    compiled.doSim("single read - no write") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.memory.arw.valid #= false
      dut.io.memory.w.valid #= false
      dut.io.memory.r.ready #= false
      dut.io.memory.b.ready #= false
      dut.io.phy.cmd.ready #= false
      dut.io.phy.rsp.valid #= false
      dut.clockDomain.waitSampling(5)

      dut.io.memory.arw.valid #= true
      dut.io.memory.arw.addr #= BigInt(104)
      dut.io.memory.arw.size #= BigInt(2)
      dut.io.memory.arw.len #= BigInt(0)
      dut.io.memory.arw.burst #= BigInt(0)
      dut.io.memory.arw.id #= BigInt(7)
      dut.io.memory.arw.write #= false

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
      apb.write(BigInt("20", 16), BigInt("00000002", 16))

      sleep(2)
      assert(dut.io.memory.arw.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      sleep(2)
      dut.io.memory.arw.valid #= false


      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.ready #= true
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("10100", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(0))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("10100000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000110", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000100", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("1000000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.ready #= false

      // wait for response from HyperRAM device
      dut.clockDomain.waitSampling(19)
      assert(dut.io.phy.rsp.ready.toBoolean == false)
      dut.io.phy.rsp.valid #= true
      sleep(2)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("01010101", 2)
      dut.io.phy.rsp.payload.last #= false
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("10101010", 2)
      dut.io.phy.rsp.payload.last #= false
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("00110011", 2)
      dut.io.phy.rsp.payload.last #= false
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("11001100", 2)
      dut.io.phy.rsp.payload.last #= true
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      dut.io.phy.rsp.valid #= false
      sleep(2)
      assert(dut.io.phy.rsp.ready.toBoolean == false)


      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.memory.r.id.toBigInt == BigInt(7))
      assert(dut.io.memory.r.data.toBigInt == BigInt("33CC55AA", 16))
      assert(dut.io.memory.r.resp.toBigInt == BigInt(0))
      assert(dut.io.memory.r.last.toBoolean == true)
      assert(dut.io.memory.r.valid.toBoolean == true)
      assert(dut.io.memory.b.id.toBigInt == BigInt(7))
      assert(dut.io.memory.b.resp.toBigInt == BigInt(0))
      assert(dut.io.memory.b.valid.toBoolean == false)
      dut.io.memory.r.ready #= true
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.memory.r.valid.toBoolean == false)
      dut.io.memory.r.ready #= false

      dut.clockDomain.waitSampling(20)
    }
    compiled.doSim("single write - no read") { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.io.memory.arw.valid #= false
      dut.io.memory.w.valid #= false
      dut.io.memory.r.ready #= false
      dut.io.memory.b.ready #= false
      dut.io.phy.cmd.ready #= false
      dut.io.phy.rsp.valid #= false
      dut.clockDomain.waitSampling(5)

      dut.io.memory.arw.addr #= BigInt(104)
      dut.io.memory.arw.size #= BigInt(2)
      dut.io.memory.arw.len #= BigInt(0)
      dut.io.memory.arw.burst #= BigInt(0)
      dut.io.memory.arw.id #= BigInt(7)
      dut.io.memory.arw.write #= true

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
      apb.write(BigInt("20", 16), BigInt("00000002", 16))

      dut.io.memory.w.data #= BigInt("96ff00A5", 16)

      dut.io.memory.arw.valid #= true
      sleep(2)
      assert(dut.io.memory.arw.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.memory.w.valid #= true
      dut.io.memory.arw.valid #= false
      sleep(2)
      assert(dut.io.memory.w.ready.toBoolean == true)
      dut.clockDomain.waitSampling(1)
      dut.io.memory.w.valid #= false


      dut.clockDomain.waitSampling(2)
      dut.io.phy.cmd.ready #= true
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00100", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(0))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00100000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000110", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000000", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00000100", 2))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(1))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("00", 16))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("A5", 16))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("96", 16))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)

      sleep(2)
      assert(dut.io.phy.cmd.args.toBigInt == BigInt("2ff", 16))
      assert(dut.io.phy.cmd.mode.toBigInt == BigInt(2))
      dut.clockDomain.waitSampling(1)
      dut.io.phy.cmd.ready #= false

      // wait for response from HyperRAM device
      dut.clockDomain.waitSampling(19)
      assert(dut.io.phy.rsp.ready.toBoolean == false)
      dut.io.phy.rsp.valid #= true
      sleep(2)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("01010101", 2)
      dut.io.phy.rsp.payload.last #= false
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("10101010", 2)
      dut.io.phy.rsp.payload.last #= false
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("00110011", 2)
      dut.io.phy.rsp.payload.last #= false
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      assert(dut.io.phy.rsp.ready.toBoolean == true)
      dut.io.phy.rsp.payload.data #= BigInt("11001100", 2)
      dut.io.phy.rsp.payload.last #= true
      dut.io.phy.rsp.payload.error #= false
      dut.clockDomain.waitSampling(1)
      dut.io.phy.rsp.valid #= false
      sleep(2)
      assert(dut.io.phy.rsp.ready.toBoolean == false)


      dut.clockDomain.waitSampling(5)
      sleep(2)
      assert(dut.io.memory.r.id.toBigInt == BigInt(7))
      //assert(dut.io.memory.r.data.toBigInt == BigInt("33CC55AA", 16))
      assert(dut.io.memory.r.resp.toBigInt == BigInt(0))
      assert(dut.io.memory.r.last.toBoolean == true)
      assert(dut.io.memory.r.valid.toBoolean == false)
      assert(dut.io.memory.b.id.toBigInt == BigInt(7))
      assert(dut.io.memory.b.resp.toBigInt == BigInt(0))
      assert(dut.io.memory.b.valid.toBoolean == true)
      dut.io.memory.r.ready #= true
      dut.clockDomain.waitSampling(1)
      sleep(2)
      assert(dut.io.memory.r.valid.toBoolean == false)
      dut.io.memory.r.ready #= false

      dut.clockDomain.waitSampling(20)
    }
  }
}
