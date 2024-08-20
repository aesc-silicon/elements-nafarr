Export Verilog/VHDL
###################

SpinalHDL modules can be seamlessly integrated into SpinalHDL designs. However, many high-level languages remain incompatible with this library, and much of the existing infrastructure is still centered around Verilog and VHDL. To bridge this gap, you can export each IP core with the desired configuration, enabling their integration into existing architectures.

Below is an example of a GPIO Controller with 32 pins and 2 Flip-Flops on the input for value synchronization.

.. literalinclude:: ../../../hardware/scala/export/peripherals/GpioExport.scala
  :language: Scala

Exporting the GPIO Controler
============================

To export the GPIO Controller, run the following sbt command in the root directory. This will generate both Verilog and VHDL files, which will be saved in the `export/` directory.

.. code-block:: bash

    sbt "runMain export.peripherals.GpioExport"

Understanding the Scala Example
===============================

Let's break down the Scala example provided:

1. **Package Definition:**

   The first line defines the package name where the `GpioExport` object resides. This package name is important when executing sbt commands.

   .. code-block:: scala

      package export.peripherals

2. **Importing Required Packages:**

   The next step involves importing the necessary SpinalHDL core libraries and the specific IP core components.

   .. code-block:: scala

      import spinal.core._
      import spinal.lib._
      import nafarr.peripherals.io.gpio.{Apb3Gpio, Gpio, GpioCtrl}

3. **Defining the GpioExport Object:**

   The `GpioExport` object is defined with a main function, which sbt uses to invoke this object. Here, the IP core is parameterized using the corresponding parameter object.

   .. code-block:: scala

      object GpioExport {
        def main(args: Array[String]) {
          val parameter = GpioCtrl.Parameter(Gpio.Parameter(32), 2)
          val config = SpinalConfig(noRandBoot = false, targetDirectory = "export")
        }
      }

4. **Generating Verilog and VHDL Files:**

   Finally, the `generateVerilog` and `generateVhdl` methods are used to create the respective files. Various classes are available for different bus architectures, so choose the appropriate one and pass the required parameters.

   .. code-block:: scala

      config.generateVerilog {
        val controller = Apb3Gpio(parameter)
        controller
      }
      config.generateVhdl {
        val controller = Apb3Gpio(parameter)
        controller
      }

This configuration can be reused in other projects, providing flexibility and ease of integration across different architectures.
