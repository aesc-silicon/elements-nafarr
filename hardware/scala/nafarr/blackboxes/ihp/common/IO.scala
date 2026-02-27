// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.blackboxes.ihp.common

import spinal.core._

object PowerIoCellType extends Enumeration {
  val iovss, iovdd, vss, vdd = Value
}

case class PowerIoCell(name: String, cellType: PowerIoCellType.Value)

object IhpPowerIoCell {
  object SG13G2 {
    val IOVss = PowerIoCell("sg13g2_IOPadIOVss", PowerIoCellType.iovss)
    val IOVdd = PowerIoCell("sg13g2_IOPadIOVdd", PowerIoCellType.iovdd)
    val Vss = PowerIoCell("sg13g2_IOPadVss", PowerIoCellType.vss)
    val Vdd = PowerIoCell("sg13g2_IOPadVdd", PowerIoCellType.vdd)
  }
  object SG13CMOS5L {
    val IOVss = PowerIoCell("sg13cmos5l_IOPadIOVss", PowerIoCellType.iovss)
    val IOVdd = PowerIoCell("sg13cmos5l_IOPadIOVdd", PowerIoCellType.iovdd)
    val Vss = PowerIoCell("sg13cmos5l_IOPadVss", PowerIoCellType.vss)
    val Vdd = PowerIoCell("sg13cmos5l_IOPadVdd", PowerIoCellType.vdd)
  }
}

object Edge extends Enumeration {
  val North, South, East, West = Value

  override def toString: String = this match {
    case North => "north"
    case South => "south"
    case East => "east"
    case West => "west"
  }
}

object IhpPowerIo {
  def apply(edge: Edge.Value, number: Int, cell: PowerIoCell) =
    new IhpPowerIo(edge, number, cell)

  class IhpPowerIo(val edge: Edge.Value, val number: Int, cell: PowerIoCell) extends BlackBox {
    setDefinitionName(cell.name)
    addAttribute("keep")
    setName(cell.name + "_inst" + this.getName())
  }
}
