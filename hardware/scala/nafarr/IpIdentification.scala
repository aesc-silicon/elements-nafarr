package nafarr

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory

object IpIdentification {
  def apply(id: SpinalEnumElement[Ids.type], major: Int, minor: Int, patch: Int) =
    IpIdentificationCtrl(id, major, minor, patch)

  object Ids extends SpinalEnum {
    val Gpio, Pio, Pwm, Uart, I2cController, I2cDevice, SpiController, SpiDevice, AesAccelerator,
        AesMaskedAccelerator, Reset, Clock = newElement()
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
