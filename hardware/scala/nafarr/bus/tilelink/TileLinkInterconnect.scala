// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.tilelink

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}

/** 1-to-N address decoder for TileLink (no BCE).
  *
  * Routes A-channel requests to the matching slave by address.  All downstream
  * ports share the same TileLinkParameter as the upstream port; addresses are
  * resized automatically.
  *
  * D-channel responses from all slaves are merged back to the master using a
  * priority arbiter (lowest slave index wins when multiple are valid
  * simultaneously; this is safe because each source ID is only in-flight at one
  * slave at a time).
  */
case class TileLinkDecoder(
    masterParam: TileLinkParameter,
    mappings: Seq[SizeMapping]
) extends Component {
  val n = mappings.size

  val io = new Bundle {
    val up = slave(TileLinkBus(masterParam))
    val downs = Vec(mappings.map(_ => master(TileLinkBus(masterParam))))
  }

  // A channel: decode by address and fan out.
  val hits = Vec(mappings.map(m => m.hit(io.up.a.address)))

  for (i <- 0 until n) {
    io.downs(i).a.valid := io.up.a.valid && hits(i)
    io.downs(i).a.opcode := io.up.a.opcode
    io.downs(i).a.param := io.up.a.param
    io.downs(i).a.size := io.up.a.size
    io.downs(i).a.source := io.up.a.source
    io.downs(i).a.address := io.up.a.address.resized
    io.downs(i).a.mask := io.up.a.mask
    io.downs(i).a.data := io.up.a.data
    io.downs(i).a.corrupt := io.up.a.corrupt
  }
  io.up.a.ready := Vec(io.downs.zipWithIndex.map { case (d, i) => d.a.ready && hits(i) }).orR

  // D channel: priority merge.
  val dValids = Vec(io.downs.map(_.d.valid))
  val dChosen = OHMasking.first(dValids.asBits)

  io.up.d.valid := dValids.orR
  io.up.d.opcode := MuxOH(dChosen, io.downs.map(_.d.opcode))
  io.up.d.param := MuxOH(dChosen, io.downs.map(_.d.param))
  io.up.d.size := MuxOH(dChosen, io.downs.map(_.d.size))
  io.up.d.source := MuxOH(dChosen, io.downs.map(_.d.source))
  io.up.d.sink := 0
  io.up.d.denied := MuxOH(dChosen, io.downs.map(_.d.denied))
  io.up.d.data := MuxOH(dChosen, io.downs.map(_.d.data))
  io.up.d.corrupt := MuxOH(dChosen, io.downs.map(_.d.corrupt))

  for (i <- 0 until n) {
    io.downs(i).d.ready := io.up.d.ready && dChosen(i)
  }
}

/** N-to-1 round-robin arbiter for TileLink (no BCE).
  *
  * Merges N upstream master buses onto one downstream slave bus.  Each
  * upstream master's source ID is extended in the high bits with a master-ID
  * tag so that D-channel responses can be demultiplexed back to the correct
  * master.
  *
  * All upstream ports share the same TileLinkParameter.  The downstream port
  * has sourceWidth = masterParam.sourceWidth + log2Up(n).
  *
  * Round-robin priority: after each accepted A-channel transaction the
  * preferred master rotates.  n must be a power of 2.
  */
case class TileLinkArbiter(masterParam: TileLinkParameter, n: Int) extends Component {
  require(n >= 2, "TileLinkArbiter requires at least 2 masters")
  require((n & (n - 1)) == 0, "n must be a power of 2")

  val masterIdBits = log2Up(n)
  val downParam = TileLinkParameter.simple(
    masterParam.addressWidth,
    masterParam.dataWidth,
    masterParam.sizeBytes,
    masterParam.sourceWidth + masterIdBits
  )

  val io = new Bundle {
    val ups = Vec(Seq.fill(n)(slave(TileLinkBus(masterParam))))
    val down = master(TileLinkBus(downParam))
  }

  // Round-robin priority register.
  val prefer = Reg(UInt(masterIdBits bits)) init 0

  // Build a rotating view of the valid signals starting at `prefer`.
  // doubled(i) = aValids((prefer + i) % n)
  val aValids = Vec(io.ups.map(_.a.valid))
  val doubled = aValids.asBits ## aValids.asBits // 2*n bits
  val rotated = (doubled >> prefer).resize(n) // Bits(n bits)
  val firstInRotated = OHMasking.first(rotated) // one-hot in rotated order

  // Map rotated grants back to actual master indices.
  val aGrants = Vec(Bool(), n)
  for (k <- 0 until n) aGrants(k) := False
  for (i <- 0 until n) { // i = possible value of prefer
    when(prefer === i) {
      for (j <- 0 until n) {
        aGrants((j + i) % n) := firstInRotated(j)
      }
    }
  }

  // A channel to downstream.
  io.down.a.valid := aValids.orR
  io.down.a.opcode := MuxOH(aGrants.asBits, io.ups.map(_.a.opcode))
  io.down.a.param := MuxOH(aGrants.asBits, io.ups.map(_.a.param))
  io.down.a.size := MuxOH(aGrants.asBits, io.ups.map(_.a.size))
  io.down.a.address := MuxOH(aGrants.asBits, io.ups.map(_.a.address))
  io.down.a.mask := MuxOH(aGrants.asBits, io.ups.map(_.a.mask))
  io.down.a.data := MuxOH(aGrants.asBits, io.ups.map(_.a.data))
  io.down.a.corrupt := MuxOH(aGrants.asBits, io.ups.map(_.a.corrupt))

  // Prepend master-ID tag into the high bits of the downstream source.
  io.down.a.source := 0
  for (i <- 0 until n) {
    when(aGrants(i)) {
      io.down.a.source := (U(i, masterIdBits bits).asBits ## io
        .ups(i)
        .a
        .source
        .asBits).asUInt.resized
    }
  }

  for (i <- 0 until n) {
    io.ups(i).a.ready := io.down.a.ready && aGrants(i)
  }

  // Advance round-robin on each accepted A transaction.
  when(io.down.a.valid && io.down.a.ready) {
    prefer := (prefer + 1).resize(masterIdBits)
  }

  // D channel: demultiplex by master-ID tag in source high bits.
  val dMasterId = io.down.d.source.takeHigh(masterIdBits).asUInt
  for (i <- 0 until n) {
    val hit = dMasterId === i
    io.ups(i).d.valid := io.down.d.valid && hit
    io.ups(i).d.opcode := io.down.d.opcode
    io.ups(i).d.param := io.down.d.param
    io.ups(i).d.size := io.down.d.size
    io.ups(i).d.source := io.down.d.source.resize(masterParam.sourceWidth)
    io.ups(i).d.sink := 0
    io.ups(i).d.denied := io.down.d.denied
    io.ups(i).d.data := io.down.d.data
    io.ups(i).d.corrupt := io.down.d.corrupt
  }
  io.down.d.ready := Vec(io.ups.zipWithIndex.map { case (up, i) =>
    up.d.ready && (dMasterId === i)
  }).orR
}
