// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.ocram

import org.scalatest.funsuite.AnyFunSuite

import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.tilelink.{
  BusParameter => TileLinkParameter,
  DebugId,
  M2sParameters,
  M2sSupport,
  M2sTransfers,
  Opcode,
  SizeRange
}
import spinal.lib.bus.tilelink.sim.{IdAllocator, MasterAgent}
import spinal.lib.sim.StreamMonitor

class TileLinkOnChipRamTest extends AnyFunSuite {
  val ramBytes = 4096
  val lineBytes = 64

  // The sim MasterAgent needs the bus node description, which TileLinkParameter.simple lacks.
  def busParam(dataWidth: Int): TileLinkParameter =
    M2sParameters(
      M2sSupport(
        transfers = M2sTransfers(
          get = SizeRange.upTo(lineBytes),
          putFull = SizeRange.upTo(lineBytes),
          putPartial = SizeRange.upTo(lineBytes)
        ),
        addressWidth = 12,
        dataWidth = dataWidth
      ),
      sourceCount = 4
    ).toBusParameter()

  for (dataWidth <- Seq(32, 64, 128); singlePort <- Seq(true, false)) {
    val ports = if (singlePort) "1-port" else "2-port"

    test(s"Bursts and sub-beat accesses ($dataWidth-bit, $ports)") {
      val compiled = SimConfig.withWave
        .workspaceName(s"TileLinkOnChipRam_${dataWidth}_$ports")
        .compile(TileLinkOnChipRam(busParam(dataWidth), ramBytes, singlePort))

      compiled.doSim("bursts") { dut =>
        implicit val idAllocator = new IdAllocator(DebugId.width)
        val cd = dut.clockDomain
        cd.forkStimulus(10)
        // Bounds a hung transfer; a full run takes well under a tenth of this.
        SimTimeout(20000000)
        val agent = new MasterAgent(dut.io.bus, cd)
        val dataBytes = dataWidth / 8
        val reference = Array.fill[Byte](ramBytes)(0)

        var readBeats = 0
        StreamMonitor(dut.io.bus.d, cd) { d =>
          if (d.opcode.toEnum == Opcode.D.ACCESS_ACK_DATA) readBeats += 1
        }
        cd.waitSampling(10)

        def randomBytes(n: Int): Seq[Byte] = Seq.fill(n)(simRandom.nextInt(256).toByte)
        def check(address: Int, bytes: Int, what: String): Unit = {
          val data = agent.get(0, address, bytes).data.toSeq
          val expected = reference.slice(address, address + bytes).toSeq
          assert(data == expected, s"$what: read mismatch at 0x${address.toHexString}")
        }

        /* Full-line bursts over the whole RAM: one D beat per bus word */
        for (line <- 0 until ramBytes / lineBytes) {
          val address = line * lineBytes
          val data = randomBytes(lineBytes)
          agent.putFullData(0, address, data)
          data.copyToArray(reference, address)
        }
        readBeats = 0
        for (line <- 0 until ramBytes / lineBytes) {
          check(line * lineBytes, lineBytes, s"line $line")
        }
        val expectedBeats = ramBytes / dataBytes
        assert(readBeats == expectedBeats, s"expected $expectedBeats read beats, got $readBeats")

        /* Random traffic: 1 to 64 bytes at naturally aligned addresses, full and partial */
        for (_ <- 0 until 400) {
          val bytes = 1 << simRandom.nextInt(log2Up(lineBytes) + 1)
          val address = simRandom.nextInt(ramBytes / bytes) * bytes
          simRandom.nextInt(3) match {
            case 0 =>
              check(address, bytes, s"get $bytes bytes")
            case 1 =>
              val data = randomBytes(bytes)
              agent.putFullData(0, address, data)
              data.copyToArray(reference, address)
            case _ =>
              val data = randomBytes(bytes)
              val mask = Seq.fill(bytes)(simRandom.nextBoolean())
              agent.putPartialData(0, address, data, mask)
              for (i <- 0 until bytes if mask(i)) {
                reference(address + i) = data(i)
              }
          }
        }
        for (line <- 0 until ramBytes / lineBytes) {
          check(line * lineBytes, lineBytes, s"final line $line")
        }
      }

      compiled.doSim("streaming") { dut =>
        implicit val idAllocator = new IdAllocator(DebugId.width)
        val cd = dut.clockDomain
        cd.forkStimulus(10)
        SimTimeout(1000000)
        val agent = new MasterAgent(dut.io.bus, cd)
        agent.driver.driver.d.factor = 1.0f
        var cycle = 0L
        cd.onSamplings { cycle += 1 }
        val beatCycles = scala.collection.mutable.ArrayBuffer[Long]()
        StreamMonitor(dut.io.bus.d, cd) { d =>
          if (d.opcode.toEnum == Opcode.D.ACCESS_ACK_DATA) beatCycles += cycle
        }
        cd.waitSampling(10)

        /* With the D channel always ready, a line read returns one beat per cycle */
        agent.get(0, 0, lineBytes)
        val beats = lineBytes / (dataWidth / 8)
        assert(beatCycles.size == beats, s"expected $beats beats, got ${beatCycles.size}")
        assert(
          beatCycles.last - beatCycles.head == beats - 1,
          s"read burst stalled: beats at cycles ${beatCycles.mkString(", ")}"
        )
      }
    }
  }
}
