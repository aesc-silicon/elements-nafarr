// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.spi

import scala.collection.mutable

import org.scalatest.funsuite.AnyFunSuite

import nafarr.CheckTester._
import nafarr.peripherals.com.spi.SpiControllerCtrl

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

class TileLinkSpiXipControllerTest extends AnyFunSuite {
  val flashBytes = 4096
  val lineBytes = 64

  // The sim MasterAgent needs the bus node description, which TileLinkParameter.simple lacks.
  def busParam(dataWidth: Int): TileLinkParameter =
    M2sParameters(
      M2sSupport(
        transfers = M2sTransfers(
          get = SizeRange.upTo(lineBytes),
          putFull = SizeRange.upTo(lineBytes)
        ),
        addressWidth = 24,
        dataWidth = dataWidth
      ),
      sourceCount = 4
    ).toBusParameter()

  test("Parameters") {
    for (dataWidth <- Seq(32, 64, 128); cacheWords <- Seq(0, 4)) {
      generationShouldPass {
        val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
        val area = new ClockingArea(cd) {
          val dut = TileLinkSpiXipController(
            SpiControllerCtrl.Parameter.xip(),
            busParam(dataWidth),
            cacheWords = cacheWords
          )
        }
        area.dut
      }
    }
  }

  for (dataWidth <- Seq(32, 64, 128)) {
    test(s"Bus adapter packs 32-bit words into $dataWidth-bit beats") {
      val compiled = SimConfig.withWave
        .workspaceName(s"SpiXipBusAdapter_$dataWidth")
        .compile(TileLinkSpiXipController.BusAdapter(busParam(dataWidth)))

      compiled.doSim("busAdapter") { dut =>
        implicit val idAllocator = new IdAllocator(DebugId.width)
        val cd = dut.clockDomain
        cd.forkStimulus(10)
        // Bounds a hung transfer; a full run takes well under a tenth of this.
        SimTimeout(20000000)
        val agent = new MasterAgent(dut.io.bus, cd)
        val dataBytes = dataWidth / 8
        val flash = Array.fill[Byte](flashBytes)(simRandom.nextInt(256).toByte)
        def flashWord(address: Long): BigInt =
          (0 until 4).map(i => BigInt(flash((address + i).toInt) & 0xff) << (8 * i)).sum

        /* Model of the 32-bit XIP engine: count + 1 words per command, with random gaps */
        val commands = mutable.ArrayBuffer[(Long, Int)]()
        val words = mutable.Queue[(Long, Boolean)]()
        StreamReadyRandomizer(dut.io.cmd, cd)
        StreamMonitor(dut.io.cmd, cd) { cmd =>
          val address = cmd.addr.toLong
          val count = cmd.count.toInt + 1
          commands += ((address, count))
          for (i <- 0 until count) {
            words += ((address + 4 * i, i == count - 1))
          }
        }
        StreamDriver(dut.io.rsp, cd) { rsp =>
          if (words.isEmpty) {
            false
          } else {
            val (address, last) = words.dequeue()
            rsp.data #= flashWord(address)
            rsp.last #= last
            true
          }
        }
        var readBeats = 0
        StreamMonitor(dut.io.bus.d, cd) { d =>
          if (d.opcode.toEnum == Opcode.D.ACCESS_ACK_DATA) readBeats += 1
        }
        cd.waitSampling(10)

        def check(address: Int, bytes: Int, what: String): Unit = {
          val data = agent.get(0, address, bytes).data.toSeq
          assert(data == flash.slice(address, address + bytes).toSeq, s"$what: data mismatch")
          val (cmdAddress, cmdWords) = commands.last
          assert(cmdAddress == (address & ~3), s"$what: command address 0x${cmdAddress.toHexString}")
          assert(cmdWords == ((bytes + 3) / 4), s"$what: $cmdWords words requested")
        }

        /* Full lines: one command of 16 words, one D beat per bus word */
        for (line <- 0 until flashBytes / lineBytes) {
          check(line * lineBytes, lineBytes, s"line $line")
        }
        val expectedBeats = flashBytes / dataBytes
        assert(readBeats == expectedBeats, s"expected $expectedBeats read beats, got $readBeats")

        /* Random reads of 1 to 64 bytes at naturally aligned addresses */
        for (_ <- 0 until 300) {
          val bytes = 1 << simRandom.nextInt(log2Up(lineBytes) + 1)
          val address = simRandom.nextInt(flashBytes / bytes) * bytes
          check(address, bytes, s"get $bytes bytes at 0x${address.toHexString}")
        }

        /* Writes are denied and never reach the engine */
        val commandsBefore = commands.size
        val put = agent.putFullData(0, 0x100, Seq.fill(lineBytes)(0.toByte))
        assert(put.denied, "write to the flash was not denied")
        cd.waitSampling(10)
        assert(commands.size == commandsBefore, "write issued an engine command")
      }
    }
  }
}
