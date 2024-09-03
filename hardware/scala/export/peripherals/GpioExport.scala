package export.peripherals

import spinal.core._
import spinal.lib._

import nafarr.peripherals.io.gpio.{Apb3Gpio, Gpio, GpioCtrl}

object GpioExport {
  def main(args: Array[String]) {
    val parameter = GpioCtrl.Parameter(Gpio.Parameter(32), 2)
    val config = SpinalConfig(noRandBoot = false, targetDirectory = "export")

    config.generateVerilog {
      val controller = Apb3Gpio(parameter)
      controller
    }
    config.generateVhdl {
      val controller = Apb3Gpio(parameter)
      controller
    }
  }
}
