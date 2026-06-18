.. _hardware-crypto-prng:

Pseudo-Random Number Generator (PRNG)
######################################

The PRNG provides a free-running 32-bit pseudo-random number stream based on a
Galois Linear Feedback Shift Register (LFSR). The LFSR uses a maximum-period
polynomial (x\ :sup:`32` + x\ :sup:`30` + x\ :sup:`26` + x\ :sup:`25` + 1),
yielding a sequence of 2\ :sup:`32` - 1 unique values before repeating.

The PRNG is not suitable for cryptographic use. It is intended for non-security
applications such as noise injection, test-pattern generation, traffic
randomisation, and EMI spread-spectrum.

.. warning::

   The LFSR will lock up permanently if its state reaches all-zeros. The hardware
   prevents this by rejecting any seed write of zero: the state is left unchanged
   and an error is raised instead. The initial seed after reset is 1.

Features
********

* 32-bit Galois LFSR with maximum-period taps
* Free-running: advances one step per clock cycle when enabled
* Software reseed via seed register
* Error signal routable to an Error Signal Module
* Enable bit to freeze output

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 8 bit address and 32 bit data width.

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x10
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

.. flat-table:: PRNG Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x008
     - 0
     - enable
     - 1
     - RW
     - Set to 1 to advance the LFSR every clock cycle. Clear to 0 to freeze the
       output.
   * - 0x00C
     - 0
     - error_pending
     - 0
     - RW
     - Error pending flag. Bit 0 = zero seed attempted. Write 1 to clear.
   * - 0x010
     - 0
     - error_mask
     - 0
     - RW
     - Error mask. Set bit 0 to route the zero-seed error to the ``error`` output
       signal.
   * - 0x014
     - 31 - 0
     - seed
     - -
     - xW
     - Write a non-zero value to reseed the LFSR immediately. Writing zero sets
       error pending bit 0 and leaves the LFSR state unchanged.
   * - 0x018
     - 31 - 0
     - output
     - 1
     - Rx
     - Current 32-bit LFSR state. Advances each clock cycle while ``enable`` is
       set.
