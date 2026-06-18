.. _hardware-crypto-crc:

CRC32 Accelerator
#################

The CRC32 accelerator offloads cyclic redundancy check computation from the
CPU. It processes one 32-bit word per clock cycle using a combinational CRC
engine (SpinalCrypto ``CRCCombinational``). There is no internal FIFO; the CPU
writes words one at a time and reads the result when finished.

The polynomial, input/output reflection, and optional XOR-out value are fixed
at elaboration time through the ``Parameter`` class. A self-disclosure
register allows software to detect the compiled configuration at runtime.

Features
********

* CRC32 in one clock cycle per 32-bit word
* Configurable polynomial via ``InitParameter`` (default: CRC32/ISO-HDLC)
* Configurable input and output bit-reflection
* Optional runtime-writable ``xorOut`` register for the final XOR step
* Self-disclosure ``info`` register (polynomial width, reflect flags,
  xorOut present)
* No FIFO - DMA support is reserved for a future high-performance variant

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 8 bit address and 32 bit data width.

``Parameter`` fields:

.. list-table::
   :widths: 20 15 65
   :header-rows: 1

   * - Field
     - Default
     - Description
   * - ``init``
     - ``InitParameter.crc32()``
     - Polynomial selection. ``InitParameter.crc32()`` selects CRC32/ISO-HDLC
       (Ethernet, zlib); ``InitParameter.crc32xfer()`` selects CRC32/XFER.
   * - ``inputReflect``
     - ``true``
     - Reflect each input word bit-by-bit before processing.
   * - ``outputReflect``
     - ``true``
     - Reflect the CRC state bit-by-bit before output.
   * - ``xorOut``
     - ``false``
     - When ``true``, expose a writable ``xorOut`` register initialised to the
       polynomial's standard final-XOR value. The ``result`` register returns
       ``crc_state XOR xorOut``.

Software Flow
*************

.. code-block:: c

   crc_init(driver);               /* reset state to polynomial init value */
   for (i = 0; i < n; i++)
       crc_write(driver, words[i]);
   uint32_t checksum = crc_read(driver);

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x12
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

.. flat-table:: CRC32 Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x008
     - 7 - 0
     - poly_order
     - 32
     - Rx
     - Polynomial order in bits (32 for all supported variants).
   * - 0x008
     - 8
     - input_reflect
     - 1
     - Rx
     - 1 if input bit-reflection is enabled at elaboration time.
   * - 0x008
     - 9
     - output_reflect
     - 1
     - Rx
     - 1 if output bit-reflection is enabled at elaboration time.
   * - 0x008
     - 10
     - xorout_present
     - 0
     - Rx
     - 1 if the ``xorOut`` register (0x018) is present and writable.
   * - 0x00C
     - 31 - 0
     - control
     - -
     - xW
     - Write any value to reset the CRC state to the polynomial ``initValue``.
   * - 0x010
     - 31 - 0
     - data
     - -
     - xW
     - Write a 32-bit word to fold it into the CRC state (one cycle).
   * - 0x014
     - 31 - 0
     - result
     - -
     - Rx
     - Current CRC state XOR ``xorOut``. Valid immediately after the write
       completes (combinational update).
   * - 0x018
     - 31 - 0
     - xor_out
     - poly finalXor
     - RW
     - Final XOR value applied to the CRC state before ``result`` is read.
       Present only when ``Parameter.xorOut = true``; reads as 0 otherwise.
