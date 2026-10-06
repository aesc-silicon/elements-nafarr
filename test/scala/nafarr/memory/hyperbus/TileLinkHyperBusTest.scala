// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus

import scala.collection.mutable

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
import spinal.lib.sim.{StreamDriver, StreamMonitor, StreamReadyRandomizer}

import nafarr.CheckTester._

class TileLinkHyperBusTest extends AnyFunSuite {
  val memoryBytes = 4096
  val lineBytes = 64

  def hyperBusParam = HyperBusCtrl.Parameter.default(List((BigInt(8) << 20, true)))

  // The sim MasterAgent needs the bus node description, which TileLinkParameter.simple lacks.
  def busParam(dataWidth: Int): TileLinkParameter =
    M2sParameters(
      M2sSupport(
        transfers = M2sTransfers(
          get = SizeRange.upTo(lineBytes),
          putFull = SizeRange.upTo(lineBytes),
          putPartial = SizeRange.upTo(lineBytes)
        ),
        addressWidth = 32,
        dataWidth = dataWidth
      ),
      sourceCount = 4
    ).toBusParameter()

  // The PHY derives its timing from the clock domain frequency.
  def withClock[T <: Component](body: => T): T = {
    val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
    val area = new ClockingArea(cd) {
      val dut = body
    }
    area.dut
  }

  test("Parameters") {
    for (dataWidth <- Seq(32, 64, 128)) {
      generationShouldPass(withClock(TileLinkHyperBus(hyperBusParam, busParam(dataWidth))))
      generationShouldPass(
        withClock(TileLinkHyperBusGenericPhyCluster(hyperBusParam, busParam(dataWidth)))
      )
      generationShouldPass(
        withClock(TileLinkHyperBusGenericDdrPhyCluster(hyperBusParam, busParam(dataWidth)))
      )
    }
  }

  for (dataWidth <- Seq(32, 64, 128)) {
    test(s"Bus adapter splits $dataWidth-bit beats into 32-bit words") {
      val compiled = SimConfig.withWave
        .workspaceName(s"HyperBusBusAdapter_$dataWidth")
        .compile(TileLinkHyperBus.BusAdapter(hyperBusParam, busParam(dataWidth)))

      compiled.doSim("busAdapter") { dut =>
        implicit val idAllocator = new IdAllocator(DebugId.width)
        val cd = dut.clockDomain
        cd.forkStimulus(10)
        // Bounds a hung transfer; a full run takes well under a tenth of this.
        SimTimeout(20000000)
        val agent = new MasterAgent(dut.io.bus, cd)
        val dataBytes = dataWidth / 8

        /* Model of the controller: one response per 32-bit word command, with random gaps */
        val memory = Array.fill[Byte](memoryBytes)(simRandom.nextInt(256).toByte)
        val reference = memory.clone()
        case class Command(address: Long, read: Boolean, strobe: Int, last: Boolean)
        val commands = mutable.ArrayBuffer[Command]()
        val responses = mutable.Queue[BigInt]()
        StreamReadyRandomizer(dut.io.controller, cd)
        StreamMonitor(dut.io.controller, cd) { c =>
          val address = c.addr.toLong
          val read = c.read.toBoolean
          val strobe = c.strobe.toInt
          commands += Command(address, read, strobe, c.last.toBoolean)
          if (read) {
            responses += (0 until 4)
              .map(i => BigInt(memory((address + i).toInt) & 0xff) << (8 * i))
              .sum
          } else {
            val data = c.data.toBigInt
            for (i <- 0 until 4 if ((strobe >> i) & 1) == 1) {
              memory((address + i).toInt) = ((data >> (8 * i)) & 0xff).toByte
            }
            responses += 0
          }
        }
        StreamDriver(dut.io.frontend, cd) { f =>
          if (responses.isEmpty) {
            false
          } else {
            f.id #= 0
            f.read #= false
            f.data #= responses.dequeue()
            f.last #= false
            f.error #= false
            true
          }
        }
        var readBeats = 0
        StreamMonitor(dut.io.bus.d, cd) { d =>
          if (d.opcode.toEnum == Opcode.D.ACCESS_ACK_DATA) readBeats += 1
        }
        cd.waitSampling(10)

        /* Each request becomes consecutive word commands with last on the final one */
        def checkCommands(address: Int, bytes: Int, read: Boolean, what: String): Unit = {
          val count = (bytes + 3) / 4
          val issued = commands.takeRight(count)
          assert(commands.size >= count, s"$what: missing commands")
          for ((c, i) <- issued.zipWithIndex) {
            assert(
              c.address == (address & ~3) + 4 * i,
              s"$what: word $i at 0x${c.address.toHexString}"
            )
            assert(c.read == read, s"$what: word $i has the wrong direction")
            assert(c.last == (i == count - 1), s"$what: last on word $i")
          }
          commands.clear()
        }
        def randomBytes(n: Int): Seq[Byte] = Seq.fill(n)(simRandom.nextInt(256).toByte)
        def get(address: Int, bytes: Int, what: String): Unit = {
          val data = agent.get(0, address, bytes).data.toSeq
          assert(data == reference.slice(address, address + bytes).toSeq, s"$what: data mismatch")
          checkCommands(address, bytes, read = true, what)
        }
        def putFull(address: Int, bytes: Int, what: String): Unit = {
          val data = randomBytes(bytes)
          agent.putFullData(0, address, data)
          data.copyToArray(reference, address)
          checkCommands(address, bytes, read = false, what)
        }

        /* Full lines: write and read back, one D beat per bus word */
        for (line <- 0 until memoryBytes / lineBytes) {
          putFull(line * lineBytes, lineBytes, s"write line $line")
        }
        readBeats = 0
        for (line <- 0 until memoryBytes / lineBytes) {
          get(line * lineBytes, lineBytes, s"read line $line")
        }
        val expectedBeats = memoryBytes / dataBytes
        assert(readBeats == expectedBeats, s"expected $expectedBeats read beats, got $readBeats")

        /* Random traffic: 1 to 64 bytes at naturally aligned addresses, full and partial */
        for (_ <- 0 until 400) {
          val bytes = 1 << simRandom.nextInt(log2Up(lineBytes) + 1)
          val address = simRandom.nextInt(memoryBytes / bytes) * bytes
          val what = s"$bytes bytes at 0x${address.toHexString}"
          simRandom.nextInt(3) match {
            case 0 => get(address, bytes, s"get $what")
            case 1 => putFull(address, bytes, s"put $what")
            case _ =>
              val data = randomBytes(bytes)
              val mask = Seq.fill(bytes)(simRandom.nextBoolean())
              agent.putPartialData(0, address, data, mask)
              for (i <- 0 until bytes if mask(i)) {
                reference(address + i) = data(i)
              }
              checkCommands(address, bytes, read = false, s"partial put $what")
          }
        }
        assert(memory.toSeq == reference.toSeq, "controller memory differs from the reference")
      }
    }
  }
}
