// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.semaphore

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  SlaveFactory => TileLinkSlaveFactory
}
import spinal.lib.bus.wishbone._

/** Hardware semaphore peripheral.
  *
  * Provides `p.count` atomic semaphore slots accessible via a memory-mapped
  * register interface.
  *
  * Register map (after the 8-byte IpIdentification header):
  *
  *   base+0x00  STATUS  (R)    Bitmask of taken slots; bit N = semaphore N taken.
  *   base+0x04  SEM_0   (R/W)  Read to claim slot 0; write to release slot 0.
  *   base+0x08  SEM_1   (R/W)  ...
  *   ...
  *
  * Claim protocol (read):
  *   Returns 0 — semaphore was free; caller now holds it.
  *   Returns 1 — semaphore was already taken; caller must retry.
  *   The returned value reflects the state *before* the read; the claim flag
  *   is set on the same bus transaction so no other master can interleave.
  *
  * Release protocol (write):
  *   Any write to the semaphore register unconditionally releases it.
  */
object Semaphore {

  class Core[T <: spinal.core.Data with IMasterSlave](
      p: SemaphoreCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
    }
    val busCtrl = factory(io.bus)
    val ctrl = SemaphoreCtrl(p)
    val mapper = SemaphoreCtrl.Mapper(busCtrl, ctrl, p)
  }
}

case class TileLinkSemaphore(
    p: SemaphoreCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(8, 32, 4, 1)
) extends Semaphore.Core[TileLinkBus](
      p,
      TileLinkBus(busConfig),
      new TileLinkSlaveFactory(_, false)
    )

case class Apb3Semaphore(
    p: SemaphoreCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(8, 32)
) extends Semaphore.Core[Apb3](
      p,
      Apb3(busConfig),
      Apb3SlaveFactory(_)
    )

case class WishboneSemaphore(
    p: SemaphoreCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(8, 32)
) extends Semaphore.Core[Wishbone](
      p,
      Wishbone(busConfig),
      WishboneSlaveFactory(_)
    )
