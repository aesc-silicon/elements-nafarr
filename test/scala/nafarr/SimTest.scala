// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr

import spinal.core._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

object SimTest {

  def checkPins(
    signal: => BigInt,
    expected: BigInt,
    description: String
  ): Unit = {
    val result = signal
    assert(
      result == expected,
      s"""Pin mismatch - $description
         |    Expected: 0x${expected.toString(16).toUpperCase}
         |    Received: 0x${result.toString(16).toUpperCase}""".stripMargin
    )
  }

  def read(
    bus: Apb3Driver,
    address: BigInt,
    expected: BigInt,
    description: String
  ): Unit = {
    val result = bus.read(address)
    assert(
      result == expected,
      s"""Register mismatch - $description
         |    Address:  0x${address.toString(16)}
         |    Expected: 0x${expected.toString(16).toUpperCase}
         |    Received: 0x${result.toString(16).toUpperCase}""".stripMargin
    )
  }

  def readField(
    bus: Apb3Driver,
    address: BigInt,
    high: Int,
    low: Int,
    expected: BigInt,
    description: String
  ): Unit = {
    val raw   = bus.read(address)
    val mask  = (BigInt(1) << (high - low + 1)) - 1
    val field = (raw >> low) & mask
    assert(
      field == expected,
      s"""Field mismatch - $description
         |    Address:  0x${address.toString(16)}  bits[$high:$low]
         |    Expected: 0x${expected.toString(16).toUpperCase}
         |    Received: 0x${field.toString(16).toUpperCase}  (raw: 0x${raw.toString(16).toUpperCase})""".stripMargin
    )
  }
}
