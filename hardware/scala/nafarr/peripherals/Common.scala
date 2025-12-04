// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals

import spinal.core._
import spinal.lib._

abstract class PeripheralsComponent extends Component {
  def deviceTreeZephyr(
      name: String,
      address: BigInt,
      size: BigInt,
      irqNumber: Option[Int] = null
  ): String
  def headerBareMetal(
      name: String,
      address: BigInt,
      size: BigInt,
      irqNumber: Option[Int] = null
  ): String
}
