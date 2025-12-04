// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

/** @todo: change <> with the corrispective >> and << */
package nafarr.bus.wishbone

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import scala.collection.Seq
import spinal.lib.bus.wishbone._

/** Factory for [[spinal.lib.bus.wishbone.WishboneDecoder]] instances. */
object WishboneDecoder2 {

  /** Create a istance of a wishbone decoder/multiplexer
    * @param config it will use for configuring all the input/output wishbone port
    * @param decodings it will use for configuring the partial address decoder
    * @return a [[spinal.lib.bus.wishbone.WishboneDecoder]] instance
    */
  def apply(config: WishboneConfig, decodings: Seq[AddressMapping]) =
    new WishboneDecoder(config, decodings)

  /** Create an istance of WishboneDecoder, and autocconect all input/outputs
    * @param master connect the input to this master interface, the [[spinal.lib.bus.wishbone.Wishbone.config]] will be used for all input/output
    * @param slaves connect the ouput to the slaves, with the correct address
    * @return a [[spinal.lib.bus.wishbone.WishboneDecoder]] instance with all the input and output connected automatically
    */
  def apply(
      master: Wishbone,
      slaves: Seq[(Wishbone, AddressMapping)],
      shift: Int = 0
  ): WishboneDecoder = {
    val shiftedSlaves = slaves.map(x =>
      x._2 match {
        case SingleMapping(address) => SingleMapping(address >> shift)
        case MaskMapping(base, mask) => MaskMapping(base >> shift, mask >> shift)
        case UnmaskMapping(base, mask) => UnmaskMapping(base >> shift, mask >> shift)
        case SizeMapping(base, size) => SizeMapping(base >> shift, size >> shift)
      }
    )
    val decoder = new WishboneDecoder(master.config, shiftedSlaves)
    decoder.io.input <> master
    (slaves.map(_._1), decoder.io.outputs).zipped.map(_ << _)
    decoder.setPartialName(master, "decoder")
  }
}

/** Create a wishbone decoder/demultiplexer
  * @param config it will use for configuring all the input/output wishbone port
  * @param decodings it will use for configuring the partial address decoder
  */
class WishboneDecoder(config: WishboneConfig, decodings: Seq[AddressMapping]) extends Component {
  val io = new Bundle {
    val input = slave(Wishbone(config))
    val outputs = Vec(master(Wishbone(config)), decodings.size)
  }

  // permanently drive some slave iunput signal to save on logic usage
  io.outputs.map { out =>
    out.STB := io.input.STB
    out.DAT_MOSI := io.input.DAT_MOSI
    out.WE := io.input.WE
    out.ADR := io.input.ADR
    Wishbone.driveWeak(io.input.SEL, out.SEL, null, false, false)
    Wishbone.driveWeak(io.input.LOCK, out.LOCK, null, false, false)
    Wishbone.driveWeak(io.input.CTI, out.CTI, null, false, false)
    Wishbone.driveWeak(io.input.TGD_MOSI, out.TGD_MOSI, null, false, false)
    Wishbone.driveWeak(io.input.TGA, out.TGA, null, false, false)
    Wishbone.driveWeak(io.input.TGC, out.TGC, null, false, false)
    Wishbone.driveWeak(io.input.BTE, out.BTE, null, false, false)
  }

  // the selector will use the decodings list to select the right slave based on the address line
  val selector = Vec(decodings.map(_.hit(io.input.ADR) && io.input.CYC))
  val selectorIndex = OHToUInt(selector)

  // Generate the CYC sygnal for the selected slave
  (io.outputs.map(_.CYC), selector).zipped.foreach(_ := _)

  // Implementing the multiplexer logic, it thakes the one Hot bit vector/bit array as input
  val selectedOutput = io.outputs(selectorIndex)

  io.input.ACK := selectedOutput.ACK
  io.input.DAT_MISO := selectedOutput.DAT_MISO

  if (config.useSTALL) io.input.STALL := selectedOutput.STALL
  if (config.useERR) io.input.ERR := selectedOutput.ERR
  if (config.useRTY) io.input.RTY := selectedOutput.RTY
  if (config.useTGD) io.input.TGD_MISO := selectedOutput.TGD_MISO
}
