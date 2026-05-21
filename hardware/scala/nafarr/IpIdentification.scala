// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory

object IpIdentification {
  def apply(id: SpinalEnumElement[Ids.type], major: Int, minor: Int, patch: Int) =
    IpIdentificationCtrl(id, major, minor, patch)

  object Ids extends SpinalEnum {
    val Gpio = newElement() // 0
    val Pio = newElement() // 1
    val Pwm = newElement() // 2
    val Uart = newElement() // 3
    val I2cController = newElement() // 4
    val I2cDevice = newElement() // 5
    val SpiController = newElement() // 6
    val SpiXipController = newElement() // 7
    val SpiDevice = newElement() // 8
    val AesAccelerator = newElement() // 9
    val AesMaskedAccelerator = newElement() // 10
    val Reset = newElement() // 11
    val Clock = newElement() // 12
    val Pinmux = newElement() // 13
    val Semaphore = newElement() // 14
    val Mailbox = newElement() // 15
    val Prng = newElement() // 16
    val Trng = newElement() // 17
    val Crc8 = newElement() // 18
    val Crc16 = newElement() // 19
    val Crc32 = newElement() // 20
    val Watchdog = newElement() // 21
    val Esm = newElement() // 22
    val Timer = newElement() // 23
  }

  case class IpIdentificationCtrl(
      id: SpinalEnumElement[Ids.type],
      major: Int,
      minor: Int,
      patch: Int
  ) extends Component {
    val io = new Bundle {
      val header = out Bits (32 bits)
      val version = out Bits (32 bits)
    }
    val length = 8
    val api = 0
    val header = RegInit(B(api, 8 bits) ## B(length, 8 bits) ## B(id, 16 bits))
    val version = RegInit(B(major, 8 bits) ## B(minor, 8 bits) ## B(patch, 16 bits))

    header.allowUnsetRegToAvoidLatch
    version.allowUnsetRegToAvoidLatch

    io.header := header
    io.version := version

    def driveFrom(busCtrl: BusSlaveFactory) = new Area {
      busCtrl.read(io.header, 0x0)
      busCtrl.read(io.version, 0x4)
    }
  }
}
