// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.tilelink

import scala.collection.mutable

import spinal.core._
import spinal.core.sim._
import spinal.lib.sim.{StreamDriver, StreamMonitor, StreamReadyRandomizer}
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, Opcode}

/** Simulation-only TileLink UL/UH slave backed by a sparse byte memory.
  *
  * Serves GET and PUT_{FULL,PARTIAL}_DATA bursts (multiples of 32-bit data), randomizes `a.ready`
  * and the D-channel issue delay. Hooks allow emulating peripherals and bus errors:
  *   - `denyAt`: transactions whose address matches are answered with `denied`.
  *   - `onRead` / `onWrite`: called once per transaction after it is serviced.
  *   - `responseDelay`: minimum cycles between a transaction and its D beats, e.g. to model
  *     a clock-domain crossing in front of a peripheral.
  */
class TileLinkSlaveModel(bus: TileLinkBus, cd: ClockDomain) {
  require(bus.p.dataWidth % 32 == 0)
  private val dataBytes = bus.p.dataBytes

  val mem = mutable.HashMap[Long, Byte]()
  var denyAt: Long => Boolean = (_: Long) => false
  var onRead: (Long, Int) => Unit = (_: Long, _: Int) => ()
  var onWrite: (Long, Int) => Unit = (_: Long, _: Int) => ()
  var responseDelay: Long => Int = (_: Long) => 0
  var reads = 0
  var writes = 0

  def read8(address: Long): Int = mem.getOrElse(address, 0.toByte) & 0xff
  def write8(address: Long, value: Int): Unit = mem(address) = value.toByte
  def readWord(address: Long): BigInt = {
    val base = address & ~3L
    (0 until 4).map(i => BigInt(read8(base + i)) << (8 * i)).sum
  }
  def writeWord(address: Long, value: BigInt): Unit = {
    val base = address & ~3L
    for (i <- 0 until 4) {
      write8(base + i, ((value >> (8 * i)) & 0xff).toInt)
    }
  }
  private def readBeat(address: Long): BigInt = {
    val base = address & ~(dataBytes - 1L)
    (0 until dataBytes).map(i => BigInt(read8(base + i)) << (8 * i)).sum
  }
  def fill(address: Long, bytes: Seq[Int]): Unit = {
    for ((b, i) <- bytes.zipWithIndex) {
      write8(address + i, b)
    }
  }
  def readBytes(address: Long, count: Int): Seq[Int] = {
    (0 until count).map(i => read8(address + i))
  }

  private case class Beat(
      opcode: Opcode.D.E,
      size: Int,
      source: Int,
      data: BigInt,
      denied: Boolean,
      readyAt: Long
  )
  private val dQueue = mutable.Queue[Beat]()
  private var cycle = 0L
  cd.onSamplings {
    cycle += 1
  }
  private var putAddress = 0L
  private var putBeats = 0
  private var putIndex = 0
  private var putDenied = false

  val readyRandomizer = StreamReadyRandomizer(bus.a, cd)

  StreamMonitor(bus.a, cd) { a =>
    val opcode = a.opcode.toEnum
    val size = a.size.toInt
    val address = a.address.toLong
    val source = a.source.toInt
    val bytes = 1 << size
    val beats = (bytes + dataBytes - 1) / dataBytes
    opcode match {
      case Opcode.A.GET =>
        val denied = denyAt(address)
        for (i <- 0 until beats) {
          val beatAddress = (address & ~(dataBytes - 1L)) + i * dataBytes
          val data = if (denied) BigInt(0) else readBeat(beatAddress)
          val readyAt = cycle + responseDelay(address)
          dQueue += Beat(Opcode.D.ACCESS_ACK_DATA, size, source, data, denied, readyAt)
        }
        if (!denied) {
          reads += 1
          onRead(address, bytes)
        }
      case Opcode.A.PUT_FULL_DATA | Opcode.A.PUT_PARTIAL_DATA =>
        if (putIndex == 0) {
          putAddress = address
          putBeats = beats
          putDenied = denyAt(address)
        }
        if (!putDenied) {
          val beatAddress = (putAddress & ~(dataBytes - 1L)) + putIndex * dataBytes
          val data = a.data.toBigInt
          val mask = a.mask.toBigInt
          for (b <- 0 until dataBytes) {
            if (mask.testBit(b)) {
              write8(beatAddress + b, ((data >> (8 * b)) & 0xff).toInt)
            }
          }
        }
        putIndex += 1
        if (putIndex == putBeats) {
          putIndex = 0
          val readyAt = cycle + responseDelay(putAddress)
          dQueue += Beat(Opcode.D.ACCESS_ACK, size, source, 0, putDenied, readyAt)
          if (!putDenied) {
            writes += 1
            onWrite(putAddress, bytes)
          }
        }
      case other =>
        throw new Exception(s"TileLinkSlaveModel: unsupported opcode $other")
    }
  }

  StreamDriver(bus.d, cd) { d =>
    if (dQueue.isEmpty || dQueue.head.readyAt > cycle) {
      false
    } else {
      val beat = dQueue.dequeue()
      d.opcode #= beat.opcode
      d.param #= 0
      d.size #= beat.size
      d.source #= beat.source
      if (d.sink.getBitsWidth > 0) {
        d.sink #= 0
      }
      d.denied #= beat.denied
      d.data #= beat.data
      d.corrupt #= false
      true
    }
  }
}
