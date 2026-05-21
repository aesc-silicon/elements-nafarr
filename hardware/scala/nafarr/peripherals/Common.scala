// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals

import spinal.core._
import spinal.lib._
import nafarr.Feature

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

  /** Returns the SoC feature flags this IP contributes to the syscon feature register.
    *
    * Override to report one or more [[Feature]] elements. The SoC builder collects
    * these across all IPs on each bus and passes the deduplicated list to
    * `SysconCtrl.Parameter`. Returns `None` by default (no feature advertised).
    *
    * Example (an IP that provides both AES and a generic crypto flag):
    * {{{
    *   override def sysconFeatures = Some(List(Feature.Aes))
    * }}}
    */
  def sysconFeatures: Option[List[Feature.E]] = None
}
