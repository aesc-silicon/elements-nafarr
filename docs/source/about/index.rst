About
#####

Nafarr is developed using `SpinalHDL`_, a hardware description language based on Scala that allows for high-level hardware design. SpinalHDL can generate Verilog or VHDL files, enabling the use of existing tools and infrastructure while benefiting from the advanced features SpinalHDL offers. The project is organized with two key directories in the root: `hardware/`, which contains the SpinalHDL code for hardware design, and `software/`, which holds the software components that interact with or test the hardware.

.. _SpinalHDL: https://spinalhdl.github.io/SpinalDoc-RTD/master/index.html

Hardware
********

This directory contains all IP cores implemented as Scala modules. They are located under `hardware/scala/nafarr` and organized as follows.

.. list-table:: IP Hierarchy
   :widths: 50 50
   :header-rows: 1

   * - Directory
     - Purpose
   * - blackboxes
     - Has blackbox wrapper for different FPGA architectures or PDKs
   * - memory
     - External memory interfaces or internal memory blocks
   * - multimedia
     - Converter, pipelines, etc. to stream media content
   * - peripherals
     - Interfaces to external peripherals
   * - system
     - System components to manage the architecture

Software
********

IP cores may require software to function correctly. This directory includes simple bare-metal drivers, hardware testing software, or firmware.

Status
******

The following table lists all available IP cores and their status.

.. list-table:: Status Overview
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - package
     - Status
     - Link
   * - Gpio
     - nafarr.peripherals.io
     - OK
     - :ref:`hardware-peripherals-gpio`
   * - Pio
     - nafarr.peripherals.io
     - OK
     - :ref:`hardware-peripherals-pio`
   * - Pwm
     - nafarr.peripherals.io
     - OK
     - :ref:`hardware-peripherals-pwm`
