// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package export.peripherals

import spinal.core._
import spinal.lib._

import nafarr.peripherals.com.i2c.{Apb3I2cController, I2c, I2cControllerCtrl}

object I2cControllerExport {
  def main(args: Array[String]) {
    val parameter = I2cControllerCtrl.Parameter.full(1)
    val config = SpinalConfig(noRandBoot = false, targetDirectory = "export")

    config.generateVerilog {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val controller = Apb3I2cController(parameter)
      }
      area.controller
    }
    config.generateVhdl {
      val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
      val area = new ClockingArea(cd) {
        val controller = Apb3I2cController(parameter)
      }
      area.controller
    }
  }
}
