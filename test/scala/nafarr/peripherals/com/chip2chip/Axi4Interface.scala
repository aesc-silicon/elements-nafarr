package nafarr.peripherals.com.chip2chip

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.ListBuffer

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.amba4.axi.sim._
import nafarr.CheckTester._

class Axi4InterfaceTest extends AnyFunSuite {

  test("Interface send out") {
    val compiled = SimConfig.withWave.compile {
      val config = Axi4Config(64, 128, 14)
      case class Chip2Chip(config: Axi4Config) extends Component {
        val io = new Bundle {
          val sender = new Bundle {
            val axiIn = slave(Axi4(config))
            val axiOut = master(Axi4(config))
          }
          val receiver = new Bundle {
            val axiIn = slave(Axi4(config))
            val axiOut = master(Axi4(config))
          }
        }

        val sender = Interface.Axi4Interface(config)
        sender.io.axiIn <> io.sender.axiIn
        io.sender.axiOut <> sender.io.axiOut
        val receiver = Interface.Axi4Interface(config)
        receiver.io.axiIn <> io.receiver.axiIn
        io.receiver.axiOut <> receiver.io.axiOut

        sender.io.txPhy(0) <> receiver.io.rxPhy(0)
        receiver.io.txPhy(0) <> sender.io.rxPhy(0)
      }

      Chip2Chip(config)
    }
    compiled.doSim("AXI4AR") { dut =>
      dut.sender.clockDomain.forkStimulus(period = 10)

      dut.io.sender.axiIn.aw.valid #= false
      dut.io.sender.axiIn.ar.valid #= false
      dut.io.sender.axiIn.w.valid #= false
      dut.io.sender.axiOut.r.valid #= false
      dut.io.sender.axiOut.b.valid #= false
      dut.io.sender.axiOut.aw.ready #= false
      dut.io.sender.axiOut.ar.ready #= false
      dut.io.sender.axiOut.w.ready #= false
      dut.io.sender.axiIn.r.ready #= false
      dut.io.sender.axiIn.b.ready #= false

      dut.io.receiver.axiIn.aw.valid #= false
      dut.io.receiver.axiIn.ar.valid #= false
      dut.io.receiver.axiIn.w.valid #= false
      dut.io.receiver.axiOut.r.valid #= false
      dut.io.receiver.axiOut.b.valid #= false
      dut.io.receiver.axiOut.aw.ready #= false
      dut.io.receiver.axiOut.ar.ready #= false
      dut.io.receiver.axiOut.w.ready #= false
      dut.io.receiver.axiIn.r.ready #= false
      dut.io.receiver.axiIn.b.ready #= false

      dut.sender.clockDomain.waitSampling(5)
      dut.io.sender.axiIn.ar.valid #= true
      dut.io.sender.axiIn.ar.addr #= BigInt("F" * 16, 16)
      dut.io.sender.axiIn.ar.id #= BigInt(0)
      dut.io.sender.axiIn.ar.region #= BigInt(0)
      dut.io.sender.axiIn.ar.len #= BigInt(3)
      dut.io.sender.axiIn.ar.size #= BigInt(5)
      dut.io.sender.axiIn.ar.burst #= BigInt(0)
      dut.io.sender.axiIn.ar.lock #= BigInt(0)
      dut.io.sender.axiIn.ar.cache #= BigInt(0)
      dut.io.sender.axiIn.ar.qos #= BigInt(0)
      dut.io.sender.axiIn.ar.prot #= BigInt(0)

      dut.sender.clockDomain.waitSampling(600)
      sleep(1)
      assert(dut.io.receiver.axiOut.ar.valid.toBoolean == true)
      assert(dut.io.receiver.axiOut.ar.addr.toBigInt == BigInt("F" * 16, 16))
      assert(dut.io.receiver.axiOut.ar.id.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.ar.region.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.ar.len.toBigInt == BigInt(3))
      assert(dut.io.receiver.axiOut.ar.size.toBigInt == BigInt(5))
      assert(dut.io.receiver.axiOut.ar.burst.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.ar.lock.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.ar.cache.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.ar.qos.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.ar.prot.toBigInt == BigInt(0))

      dut.sender.clockDomain.waitSampling(100)
    }

    compiled.doSim("AXI4AW") { dut =>
      dut.sender.clockDomain.forkStimulus(period = 10)

      dut.io.sender.axiIn.aw.valid #= false
      dut.io.sender.axiIn.ar.valid #= false
      dut.io.sender.axiIn.w.valid #= false
      dut.io.sender.axiOut.r.valid #= false
      dut.io.sender.axiOut.b.valid #= false
      dut.io.sender.axiOut.aw.ready #= false
      dut.io.sender.axiOut.ar.ready #= false
      dut.io.sender.axiOut.w.ready #= false
      dut.io.sender.axiIn.r.ready #= false
      dut.io.sender.axiIn.b.ready #= false

      dut.io.receiver.axiIn.aw.valid #= false
      dut.io.receiver.axiIn.ar.valid #= false
      dut.io.receiver.axiIn.w.valid #= false
      dut.io.receiver.axiOut.r.valid #= false
      dut.io.receiver.axiOut.b.valid #= false
      dut.io.receiver.axiOut.aw.ready #= false
      dut.io.receiver.axiOut.ar.ready #= false
      dut.io.receiver.axiOut.w.ready #= false
      dut.io.receiver.axiIn.r.ready #= false
      dut.io.receiver.axiIn.b.ready #= false

      dut.sender.clockDomain.waitSampling(5)
      dut.io.sender.axiIn.aw.valid #= true
      dut.io.sender.axiIn.aw.addr #= BigInt("F" * 16, 16)
      dut.io.sender.axiIn.aw.id #= BigInt(0)
      dut.io.sender.axiIn.aw.region #= BigInt(0)
      dut.io.sender.axiIn.aw.len #= BigInt(3)
      dut.io.sender.axiIn.aw.size #= BigInt(5)
      dut.io.sender.axiIn.aw.burst #= BigInt(0)
      dut.io.sender.axiIn.aw.lock #= BigInt(0)
      dut.io.sender.axiIn.aw.cache #= BigInt(0)
      dut.io.sender.axiIn.aw.qos #= BigInt(0)
      dut.io.sender.axiIn.aw.prot #= BigInt(0)

      dut.sender.clockDomain.waitSampling(600)
      sleep(1)
      assert(dut.io.receiver.axiOut.aw.valid.toBoolean == true)
      assert(dut.io.receiver.axiOut.aw.addr.toBigInt == BigInt("F" * 16, 16))
      assert(dut.io.receiver.axiOut.aw.id.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.aw.region.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.aw.len.toBigInt == BigInt(3))
      assert(dut.io.receiver.axiOut.aw.size.toBigInt == BigInt(5))
      assert(dut.io.receiver.axiOut.aw.burst.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.aw.lock.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.aw.cache.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.aw.qos.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.aw.prot.toBigInt == BigInt(0))

      dut.sender.clockDomain.waitSampling(100)
    }

    compiled.doSim("AXI4W") { dut =>
      dut.sender.clockDomain.forkStimulus(period = 10)

      dut.io.sender.axiIn.aw.valid #= false
      dut.io.sender.axiIn.ar.valid #= false
      dut.io.sender.axiIn.w.valid #= false
      dut.io.sender.axiOut.r.valid #= false
      dut.io.sender.axiOut.b.valid #= false
      dut.io.sender.axiOut.aw.ready #= false
      dut.io.sender.axiOut.ar.ready #= false
      dut.io.sender.axiOut.w.ready #= false
      dut.io.sender.axiIn.r.ready #= false
      dut.io.sender.axiIn.b.ready #= false

      dut.io.receiver.axiIn.aw.valid #= false
      dut.io.receiver.axiIn.ar.valid #= false
      dut.io.receiver.axiIn.w.valid #= false
      dut.io.receiver.axiOut.r.valid #= false
      dut.io.receiver.axiOut.b.valid #= false
      dut.io.receiver.axiOut.aw.ready #= false
      dut.io.receiver.axiOut.ar.ready #= false
      dut.io.receiver.axiOut.w.ready #= false
      dut.io.receiver.axiIn.r.ready #= false
      dut.io.receiver.axiIn.b.ready #= false

      dut.sender.clockDomain.waitSampling(5)
      dut.io.sender.axiIn.w.valid #= true
      dut.io.sender.axiIn.w.data #= BigInt("F" * 32, 16)
      dut.io.sender.axiIn.w.strb #= BigInt(0)
      dut.io.sender.axiIn.w.last #= false

      dut.sender.clockDomain.waitSampling(600)
      sleep(1)
      assert(dut.io.receiver.axiOut.w.valid.toBoolean == true)
      assert(dut.io.receiver.axiOut.w.data.toBigInt == BigInt("F" * 32, 16))
      assert(dut.io.receiver.axiOut.w.strb.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiOut.w.last.toBoolean == false)

      dut.sender.clockDomain.waitSampling(100)
    }

    compiled.doSim("AXI4R") { dut =>
      dut.sender.clockDomain.forkStimulus(period = 10)

      dut.io.sender.axiIn.aw.valid #= false
      dut.io.sender.axiIn.ar.valid #= false
      dut.io.sender.axiIn.w.valid #= false
      dut.io.sender.axiOut.r.valid #= false
      dut.io.sender.axiOut.b.valid #= false
      dut.io.sender.axiOut.aw.ready #= false
      dut.io.sender.axiOut.ar.ready #= false
      dut.io.sender.axiOut.w.ready #= false
      dut.io.sender.axiIn.r.ready #= false
      dut.io.sender.axiIn.b.ready #= false

      dut.io.receiver.axiIn.aw.valid #= false
      dut.io.receiver.axiIn.ar.valid #= false
      dut.io.receiver.axiIn.w.valid #= false
      dut.io.receiver.axiOut.r.valid #= false
      dut.io.receiver.axiOut.b.valid #= false
      dut.io.receiver.axiOut.aw.ready #= false
      dut.io.receiver.axiOut.ar.ready #= false
      dut.io.receiver.axiOut.w.ready #= false
      dut.io.receiver.axiIn.r.ready #= false
      dut.io.receiver.axiIn.b.ready #= false

      dut.sender.clockDomain.waitSampling(5)
      dut.io.sender.axiOut.r.valid #= true
      dut.io.sender.axiOut.r.data #= BigInt("F" * 32, 16)
      dut.io.sender.axiOut.r.resp #= BigInt(0)
      dut.io.sender.axiOut.r.last #= false

      dut.sender.clockDomain.waitSampling(600)
      sleep(1)
      assert(dut.io.receiver.axiIn.r.valid.toBoolean == true)
      assert(dut.io.receiver.axiIn.r.data.toBigInt == BigInt("F" * 32, 16))
      assert(dut.io.receiver.axiIn.r.resp.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiIn.r.last.toBoolean == false)

      dut.sender.clockDomain.waitSampling(100)
    }

    compiled.doSim("AXI4B") { dut =>
      dut.sender.clockDomain.forkStimulus(period = 10)

      dut.io.sender.axiIn.aw.valid #= false
      dut.io.sender.axiIn.ar.valid #= false
      dut.io.sender.axiIn.w.valid #= false
      dut.io.sender.axiOut.r.valid #= false
      dut.io.sender.axiOut.b.valid #= false
      dut.io.sender.axiOut.aw.ready #= false
      dut.io.sender.axiOut.ar.ready #= false
      dut.io.sender.axiOut.w.ready #= false
      dut.io.sender.axiIn.r.ready #= false
      dut.io.sender.axiIn.b.ready #= false

      dut.io.receiver.axiIn.aw.valid #= false
      dut.io.receiver.axiIn.ar.valid #= false
      dut.io.receiver.axiIn.w.valid #= false
      dut.io.receiver.axiOut.r.valid #= false
      dut.io.receiver.axiOut.b.valid #= false
      dut.io.receiver.axiOut.aw.ready #= false
      dut.io.receiver.axiOut.ar.ready #= false
      dut.io.receiver.axiOut.w.ready #= false
      dut.io.receiver.axiIn.r.ready #= false
      dut.io.receiver.axiIn.b.ready #= false

      dut.sender.clockDomain.waitSampling(5)
      dut.io.sender.axiOut.b.valid #= true
      dut.io.sender.axiOut.b.id #= BigInt(0)
      dut.io.sender.axiOut.b.resp #= BigInt(0)

      dut.sender.clockDomain.waitSampling(600)
      sleep(1)
      assert(dut.io.receiver.axiIn.b.valid.toBoolean == true)
      assert(dut.io.receiver.axiIn.b.id.toBigInt == BigInt(0))
      assert(dut.io.receiver.axiIn.b.resp.toBigInt == BigInt(0))

      dut.sender.clockDomain.waitSampling(100)
    }
  }
}
