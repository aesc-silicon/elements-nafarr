// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.tilelink

import spinal.core._
import spinal.lib._
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter}

/** Lets one TileLink UL/UH transaction through at a time and restores the upstream `source`
  * on the response.
  *
  * `down` uses a narrower source width than `up`, so slaves that drop upper source bits can
  * sit behind an Arbiter, which extends `source` to route responses back to its masters.
  */
case class TileLinkSerializer(upParam: TileLinkParameter, downSourceWidth: Int = 1)
    extends Component {
  require(!upParam.withBCE, "TileLinkSerializer supports TL-UL/UH only")
  val downParam = upParam.copy(sourceWidth = downSourceWidth)

  val io = new Bundle {
    val up = slave(TileLinkBus(upParam))
    val down = master(TileLinkBus(downParam))
  }

  val pending = RegInit(False)
  val source = Reg(upParam.source())

  io.down.a << io.up.a.haltWhen(pending)
  io.down.a.source.removeAssignments() := 0
  io.up.d << io.down.d
  io.up.d.source.removeAssignments() := source

  when(io.up.a.fire) {
    source := io.up.a.source
    when(io.up.a.isLast()) {
      pending := True
    }
  }
  when(io.up.d.fire && io.up.d.isLast()) {
    pending := False
  }
}
