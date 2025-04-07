package nafarr.crypto.aes

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import nafarr.IpIdentification

import nafarr.blackboxes.ihp.sg13g2._

object AesMaskedAcceleratorCtrl {
  def apply(p: Parameter = Parameter.default) = AesMaskedAcceleratorCtrl(p)

  case class Parameter(textLength: Int = 128, maskingDepth: Int = 280) {
    val keyDepth = textLength / 32
    val plaintextDepth = textLength / 32
    val ciphertextDepth = textLength / 32
  }

  object Parameter {
    def default() = Parameter()
  }

  case class AesBlackBox() extends BlackBox {
    val io = new Bundle {
      val clk = in(Bool)
      val reset = in(Bool)
      val enable = in(Bool)
      val pt1_payload = in(Bits(32 bits))
      val pt1_valid = in(Bool)
      val pt1_ready = out(Bool)
      val pt2_payload = in(Bits(32 bits))
      val pt2_valid = in(Bool)
      val pt2_ready = out(Bool)
      val key1_payload = in(Bits(32 bits))
      val key1_valid = in(Bool)
      val key1_ready = out(Bool)
      val key2_payload = in(Bits(32 bits))
      val key2_valid = in(Bool)
      val key2_ready = out(Bool)
      val ct1_payload = out(Bits(32 bits))
      val ct1_valid = out(Bool)
      val ct1_ready = in(Bool)
      val ct2_payload = out(Bits(32 bits))
      val ct2_valid = out(Bool)
      val ct2_ready = in(Bool)
      val m = in(Bits(28 bits))
      val done = out(Bool)
    }
    setBlackBoxName("AES_Masked")
    mapClockDomain(clock = io.clk, reset = io.reset)
    addRTLPath(System.getenv("NAFARR_BASE") + "/hardware/scala/nafarr/crypto/aes/AESMasked.v")
  }

  case class Io(p: Parameter) extends Bundle {
    val done = out(Bool())
    val enable = in(Bool())
    val key = slave(Stream(Bits(32 bits)))
    val plaintext = slave(Stream(Bits(32 bits)))
    val ciphertext = master(Stream(Bits(32 bits)))
    val masking = slave(Stream(Bits(28 bits)))
  }

  case class AesMaskedAcceleratorCtrl(p: Parameter) extends Component {
    val io = Io(p)

    val aes = new AesBlackBox()

    val key = StreamFifo(Bits(32 bits), p.keyDepth * 2)
    val plaintext = StreamFifo(Bits(32 bits), p.ciphertextDepth * 2)
    val ciphertext = Stream(Bits(32 bits))

    val enabled = Reg(Bool).init(False)
    val done = Reg(Bool).init(False)
    io.done := done

    aes.io.pt1_payload := B(0)
    aes.io.pt1_valid := False
    aes.io.pt2_payload := B(0)
    aes.io.pt2_valid := False
    aes.io.key1_payload := B(0)
    aes.io.key1_valid := False
    aes.io.key2_payload := B(0)
    aes.io.key2_valid := False
    aes.io.ct1_ready := False
    aes.io.ct2_ready := False

    key.io.push << io.key
    plaintext.io.push << io.plaintext
    io.ciphertext << ciphertext

    val mCounter = Reg(UInt(log2Up(p.maskingDepth) bits)).init(0)
    when(io.done || io.enable) {
      mCounter := 0
    }

    val ram = new Memory.RM_IHPSG13_1P_512x32_c2_bm_bist()
    ram.connectDefaults()
    ram.A_MEN := False
    ram.A_WEN := !enabled
    ram.A_REN := enabled
    ram.A_ADDR := mCounter.asBits.resized
    ram.A_DIN := io.masking.payload.resized
    ram.A_BM := B(ram.dataWidth bits, default -> True)

    aes.io.m := ram.A_DOUT.resized
    io.masking.ready := False
    when(io.masking.valid) {
      mCounter := mCounter + 1
      io.masking.ready := True
      ram.A_MEN := True
    }

    /* Always read from memory but don't increase address */
    when(enabled) {
      ram.A_MEN := True
    }

    when(enabled & (!key.io.pop.valid) & (!plaintext.io.pop.valid)) {
      mCounter := mCounter + 1
    }

    when(plaintext.io.occupancy <= U(4)) {
      aes.io.pt2_payload := plaintext.io.pop.payload
      aes.io.pt2_valid := plaintext.io.pop.valid
      plaintext.io.pop.ready := aes.io.pt2_ready
    } otherwise {
      aes.io.pt1_payload := plaintext.io.pop.payload
      aes.io.pt1_valid := plaintext.io.pop.valid
      plaintext.io.pop.ready := aes.io.pt1_ready
    }

    when(key.io.occupancy <= U(4)) {
      aes.io.key2_payload := key.io.pop.payload
      aes.io.key2_valid := key.io.pop.valid
      key.io.pop.ready := aes.io.key2_ready
    } otherwise {
      aes.io.key1_payload := key.io.pop.payload
      aes.io.key1_valid := key.io.pop.valid
      key.io.pop.ready := aes.io.key1_ready
    }

    when(aes.io.ct1_valid) {
      ciphertext.payload := aes.io.ct1_payload
      ciphertext.valid := aes.io.ct1_valid
      aes.io.ct1_ready := ciphertext.ready
    } otherwise {
      ciphertext.payload := aes.io.ct2_payload
      ciphertext.valid := aes.io.ct2_valid
      aes.io.ct2_ready := ciphertext.ready
    }

    aes.io.enable := enabled
    when(io.enable) {
      enabled := True
      done := False
    }
    when(aes.io.done) {
      enabled := False
      done := True
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.AesMaskedAccelerator, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val staticOffset = idCtrl.length
    val regOffset = staticOffset + 0x0

    busCtrl.read(ctrl.done, address = regOffset + 0x0)
    ctrl.enable := busCtrl.isWriting(regOffset + 0x0)

    ctrl.key << busCtrl.createAndDriveFlow(Bits(32 bits), address = regOffset + 0x4).toStream
    ctrl.plaintext << busCtrl.createAndDriveFlow(Bits(32 bits), address = regOffset + 0x8).toStream

    val (stream, fifoOccupancy) = ctrl.ciphertext.queueWithOccupancy(p.ciphertextDepth * 2)
    busCtrl.read(stream.payload, address = regOffset + 0xc)
    stream.ready := busCtrl.isReading(regOffset + 0xc)
    busCtrl.read(fifoOccupancy, address = regOffset + 0x00, bitOffset = 24)

    ctrl.masking << busCtrl.createAndDriveFlow(Bits(28 bits), address = regOffset + 0x10).toStream
  }
}
