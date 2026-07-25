// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.cores.cpu.vexiiriscv

import spinal.core._
import spinal.lib._
import spinal.lib.com.jtag.Jtag
import spinal.lib.bus.tilelink.{Bus => TileLinkBus}

// Component boundary around the VexiiRiscv core so it can be hardened as a
// hierarchical macro. TileLinkVexiiRiscv is an Area (flattened into its
// parent); this wrapper gives it a module boundary and forwards the interface
// under the same field names, so callers keep using core.cpu.<signal>.
class VexiiRiscvBlock(
    p: TileLinkVexiiRiscv.Parameter,
    debugCd: ClockDomain
) extends Component {
  val cpu = new TileLinkVexiiRiscv(p, debugCd)

  val iBus = master(TileLinkBus(p.iBusParam))
  val dBus = master(TileLinkBus(p.dBusParam))
  val dIoBus = master(TileLinkBus(if (p.dIoBusParam != null) p.dIoBusParam else p.dBusParam))
  val mtimerInterrupt = in Bool ()
  val globalInterrupt = in Bool ()
  val jtag = slave(Jtag())
  val ndmreset = out Bool ()

  iBus <> cpu.iBus
  dBus <> cpu.dBus
  dIoBus <> cpu.dIoBus
  cpu.mtimerInterrupt := mtimerInterrupt
  cpu.globalInterrupt := globalInterrupt
  jtag <> cpu.jtag
  ndmreset := cpu.ndmreset

  def iCacheRams = cpu.iCacheRams
  def iCacheTagRams = cpu.iCacheTagRams
  def dCacheRams = cpu.dCacheRams
  def dCacheTagRams = cpu.dCacheTagRams
  def gshareRams = cpu.gshareRams
}
