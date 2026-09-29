// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.tilelink

import scala.collection.mutable

import org.scalatest.funsuite.AnyFunSuite

import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.tilelink.{BusParameter => TileLinkParameter, Opcode}
import spinal.lib.sim.{StreamDriver, StreamMonitor, StreamReadyRandomizer}

class TileLinkSerializerTest extends AnyFunSuite {

  // 2-bit source upstream (as behind a 2-master Arbiter), 1-bit source downstream.
  val upParam = TileLinkParameter.simple(32, 32, 16, 2)

  test("Restores source with one transaction in flight") {
    SimConfig.withWave.compile(TileLinkSerializer(upParam)).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      val slave = new TileLinkSlaveModel(dut.io.down, dut.clockDomain)
      slave.readyRandomizer.setFactor(0.5f)
      slave.responseDelay = _ => 3

      val base = 0x80000000L
      for (i <- 0 until 16) {
        slave.writeWord(base + i * 4, BigInt(0x1000 + i))
      }

      // Word GETs from alternating sources, then a 16-byte burst PUT.
      case class Req(opcode: Opcode.A.E, source: Int, address: Long, size: Int, data: BigInt)
      val requests = mutable.Queue[Req]()
      for (i <- 0 until 8) {
        requests += Req(Opcode.A.GET, i % 4, base + i * 4, 2, 0)
      }
      for (beat <- 0 until 4) {
        requests += Req(Opcode.A.PUT_FULL_DATA, 3, base + 0x40, 4, BigInt(0xa0 + beat))
      }

      // A beats forwarded downstream since the previous response.
      var beatsSinceResponse = 0
      StreamDriver(dut.io.up.a, dut.clockDomain) { a =>
        if (requests.isEmpty) {
          false
        } else {
          val r = requests.dequeue()
          a.opcode #= r.opcode
          a.param #= 0
          a.source #= r.source
          a.address #= r.address
          a.size #= r.size
          a.mask #= 0xf
          a.data #= r.data
          a.corrupt #= false
          true
        }
      }
      StreamMonitor(dut.io.down.a, dut.clockDomain) { _ =>
        beatsSinceResponse += 1
      }

      val responses = mutable.ArrayBuffer[(Int, BigInt, Int)]()
      StreamReadyRandomizer(dut.io.up.d, dut.clockDomain)
      StreamMonitor(dut.io.up.d, dut.clockDomain) { d =>
        responses += ((d.source.toInt, d.data.toBigInt, beatsSinceResponse))
        beatsSinceResponse = 0
      }

      dut.clockDomain.waitSamplingWhere(responses.size == 9)
      for (i <- 0 until 8) {
        assert(responses(i) == ((i % 4, BigInt(0x1000 + i), 1)), s"GET $i response")
      }
      assert(responses(8)._1 == 3, "PUT response source")
      assert(responses(8)._3 == 4, "PUT beats not forwarded as one transaction")
      for (beat <- 0 until 4) {
        assert(slave.readWord(base + 0x40 + beat * 4) == BigInt(0xa0 + beat), s"PUT beat $beat")
      }
    }
  }
}
