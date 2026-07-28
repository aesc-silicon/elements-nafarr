.. _hardware-memory-spixip:

SPI Execute-In-Place Flash Controller (SPI XIP)
###############################################

The SPI XIP controller attaches a SPI NOR flash device and exposes its contents as an
ordinary memory-mapped window, so instructions and data can be fetched directly with
load instructions (execute-in-place). Alongside the read window, a register-driven
command engine performs the operations that XIP reads cannot: programming, erasing, and
device configuration. The engine issues arbitrary flash opcodes, optionally wrapping them
in a write-enable (WREN) sequence and polling the status register until the device is
ready again.

Features
********

* Memory-mapped execute-in-place (XIP) read window - direct load access to flash
* Configurable SPI transfer ``mode`` and read ``dummy cycles``
* Generic flash-command engine: opcode, optional 24-bit address and program payload
* Optional automatic WREN wrapper and busy-polling around a command
* Software-filled TX FIFO supplying program data bytes
* Enhanced Volatile Configuration Register (EVCR) setup
* Status register exposing busy state, the device status byte, and TX FIFO space

Command Engine
**************

A programming or erase operation is issued through the command registers:

1. Write ``command`` with the flash ``opcode`` and the ``hasAddress``, ``needsWren`` and
   ``needsPoll`` flags.
2. If the opcode takes an address, write ``address``; if it carries a payload, write the
   byte count to ``length`` and push the bytes to ``tx_data``.
3. Write ``start``. The engine optionally sends WREN, sends the opcode (+ address +
   payload), and, when ``needsPoll`` is set, polls the status register until the write is
   complete.

Poll the ``busy`` bit of ``status`` (or the device status byte) to detect completion.
Device configuration - the SPI ``mode``, ``dummy cycles`` and the EVCR - is applied by
writing ``config`` and triggering ``configure``.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

.. list-table:: SpiXipControllerCtrl parameters
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - p
     - SpiControllerCtrl.Parameter
     - Underlying SPI controller parameter. Use ``Parameter.xip()`` for the XIP preset.
     -
   * - dataWidth
     - Int
     - SPI data-path width in bits.
     -
   * - txFifoDepth
     - Int
     - Depth of the software-filled program-data FIFO.
     - 64

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x07
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x1
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

.. flat-table:: Control Registers
   :widths: 10 12 15 10 12 41
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x008
     -
     - configure
     -
     - xW
     - Write any value to apply ``config`` (SPI mode, dummy cycles, EVCR) to the device.
   * - :rspan:`2` 0x00C
     - 23 - 16
     - evcr
     - 0x0
     - RW
     - Enhanced Volatile Configuration Register value written during ``configure``.
   * - 15 - 8
     - dummy_cycles
     - 0x0
     - RW
     - Number of dummy cycles for read commands.
   * - 7 - 0
     - mode
     - 0x0
     - RW
     - SPI transfer mode selector.
   * - :rspan:`3` 0x010
     - 10
     - needs_poll
     - 0x0
     - RW
     - Poll the status register until the device is ready after the command.
   * - 9
     - needs_wren
     - 0x0
     - RW
     - Wrap the command in a write-enable (WREN) sequence.
   * - 8
     - has_address
     - 0x0
     - RW
     - The opcode is followed by the 24-bit ``address``.
   * - 7 - 0
     - opcode
     - 0x0
     - RW
     - Flash command opcode.
   * - 0x014
     - 23 - 0
     - address
     - 0x0
     - RW
     - Flash address for address-bearing commands.
   * - 0x018
     - 8 - 0
     - length
     - 0x0
     - RW
     - Number of program-data bytes to send from the TX FIFO.
   * - 0x01C
     -
     - start
     -
     - xW
     - Write any value to launch the command described by the registers above.
   * - 0x020
     - 7 - 0
     - tx_data
     -
     - xW
     - Push one program-data byte into the TX FIFO.
   * - :rspan:`2` 0x024
     - 31 - 16
     - tx_availability
     -
     - Rx
     - Free entries in the program-data TX FIFO.
   * - 15 - 8
     - status_reg
     - 0x0
     - Rx
     - Last-read device status byte.
   * - 0
     - busy
     - 0x0
     - Rx
     - High while a command is in progress.
