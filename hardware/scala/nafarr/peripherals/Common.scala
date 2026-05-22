// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals

import spinal.core._
import spinal.lib._
import nafarr.Feature

abstract class PeripheralsComponent extends Component {

  /** Generates a C header snippet for this IP's base address and any
    * IRQ/error number macros.
    *
    * Called by [[zibal.misc.BaremetalTools]] during SoC generation.
    * `name` is the SpinalHDL component name (e.g. `"gpio0Ctrl"`), `address`
    * is the absolute bus address, and `size` is the mapping size in bytes.
    *
    * Each implementation should emit at minimum:
    * {{{
    *   #define <NAME>_BASE   0x<address>
    * }}}
    * IRQ and error number macros are appended automatically by the tools layer.
    */
  def headerBareMetal(name: String, address: BigInt, size: BigInt): String

  /** Returns the interrupt output signal for IRQ-number lookup in header generation.
    * Override in Core classes that have an interrupt output.
    */
  def getInterrupt: Option[Bool] = None

  /** Returns the error output signal for ESM wiring and header generation.
    * Override in Core classes that have an error output.
    */
  def getError: Option[Bool] = None

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
