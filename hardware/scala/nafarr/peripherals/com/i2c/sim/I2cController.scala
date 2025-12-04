// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.i2c

import spinal.core.{Bool, Bits}
import spinal.lib.io.ReadableOpenDrain
import spinal.core.sim._
import spinal.sim._

object I2cControllerSim {
  def start(sda: ReadableOpenDrain[Bool], scl: ReadableOpenDrain[Bool], tickPeriod: Long) = fork {
    sda.read #= true
    scl.read #= true

    sleep(tickPeriod * 1000)

    sda.read #= false
    sleep(tickPeriod * 1000)

    sleep(tickPeriod * 1000)

    scl.read #= false
    sleep(tickPeriod * 1000)
  }

  def stop(sda: ReadableOpenDrain[Bool], scl: ReadableOpenDrain[Bool], tickPeriod: Long) = fork {

    sleep(tickPeriod * 1000)

    scl.read #= true
    sleep(tickPeriod * 1000)

    sleep(tickPeriod * 1000)

    sda.read #= true
    sleep(tickPeriod * 1000)
  }

  def address(
      sda: ReadableOpenDrain[Bool],
      scl: ReadableOpenDrain[Bool],
      tickPeriod: Long,
      address: BigInt,
      write: Boolean
  ) = fork {
    (0 to 6).foreach { bitId =>
      writeBit(sda, scl, tickPeriod, ((address >> bitId) & 1) != 0)
    }

    writeBit(sda, scl, tickPeriod, write)
  }

  def readByte(
      sda: ReadableOpenDrain[Bool],
      scl: ReadableOpenDrain[Bool],
      tickPeriod: Long,
      compare: BigInt
  ) = fork {
    var buffer = 0
    (0 to 7).foreach { bitId =>
      if (readBit(sda, scl, tickPeriod))
        buffer |= 1 << bitId
    }
    assert(buffer == compare, s"Transmitted ${buffer} but expected ${compare}")
  }

  def writeByte(
      sda: ReadableOpenDrain[Bool],
      scl: ReadableOpenDrain[Bool],
      tickPeriod: Long,
      value: BigInt
  ) = fork {
    (0 to 7).foreach { bitId =>
      writeBit(sda, scl, tickPeriod, ((value >> bitId) & 1) != 0)
    }
  }

  def checkAck(sda: ReadableOpenDrain[Bool], scl: ReadableOpenDrain[Bool], tickPeriod: Long) =
    fork {
      sleep(tickPeriod * 1000)

      scl.read #= true
      assert(sda.write.toBoolean == true, "Address ACK not high")
      sleep(tickPeriod * 1000)

      sleep(tickPeriod * 1000)

      scl.read #= false
      sleep(30 * 1000)
      // assert(sda.write.toBoolean == false, "Address ACK not low after SCL")
      sleep((tickPeriod - 30) * 1000)
    }

  def checkNack(sda: ReadableOpenDrain[Bool], scl: ReadableOpenDrain[Bool], tickPeriod: Long) =
    fork {
      sleep(tickPeriod * 1000)

      scl.read #= true
      assert(sda.write.toBoolean == false, "Address NACK not low")
      sleep(tickPeriod * 1000)

      assert(sda.write.toBoolean == false, "Address NACK not low")
      sleep(tickPeriod * 1000)

      scl.read #= false
      sleep(30 * 1000)
      assert(sda.write.toBoolean == false, "Address NACK not low after SCL low")
      sleep((tickPeriod - 30) * 1000)
    }

  def sendAck(sda: ReadableOpenDrain[Bool], scl: ReadableOpenDrain[Bool], tickPeriod: Long) = fork {
    sda.write #= true
    sleep(tickPeriod * 1000)

    scl.read #= true
    sleep(tickPeriod * 1000)

    sleep(tickPeriod * 1000)

    scl.read #= false
    sda.write #= false
    sleep(tickPeriod * 1000)
  }

  def sendNack(sda: ReadableOpenDrain[Bool], scl: ReadableOpenDrain[Bool], tickPeriod: Long) =
    fork {
      sleep(tickPeriod * 1000)

      scl.read #= true
      sleep(tickPeriod * 1000)

      sleep(tickPeriod * 1000)

      scl.read #= false
      sleep(tickPeriod * 1000)
    }

  def writeBit(
      sda: ReadableOpenDrain[Bool],
      scl: ReadableOpenDrain[Bool],
      tickPeriod: Long,
      value: Boolean
  ) {
    sda.read #= value
    sleep(tickPeriod * 1000)

    scl.read #= true
    sleep(tickPeriod * 1000)

    sleep(tickPeriod * 1000)

    scl.read #= false
    sleep(tickPeriod * 1000)
    sda.read #= false
  }

  def readBit(
      sda: ReadableOpenDrain[Bool],
      scl: ReadableOpenDrain[Bool],
      tickPeriod: Long
  ): Boolean = {
    var value = false

    sleep(tickPeriod * 1000)

    scl.read #= true
    sleep(tickPeriod * 1000)

    value = sda.write.toBoolean
    sleep(tickPeriod * 1000)

    scl.read #= false
    sleep(tickPeriod * 1000)

    value
  }
}
