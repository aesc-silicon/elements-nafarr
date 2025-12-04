// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.plic

import spinal.core._
import spinal.lib._

object PlicCtrl {

  case class Parameter(
      sources: Int,
      priorityWidth: Int
  )
  object Parameter {
    def default(sources: Int) = Parameter(sources, 1)
  }

}
