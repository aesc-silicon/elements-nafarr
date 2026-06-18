.. _hardware-system-semaphore:

Hardware Semaphore
##################

The Hardware Semaphore provides atomic inter-process or inter-core locking without
requiring software-level compare-and-swap instructions. Each semaphore slot is claimed
by reading its register and released by writing to it. The claim flag is set on the same
bus transaction as the read, so no other bus master can interleave between the test and
the set.

Features
********

* Configurable number of semaphore slots (1 to 32)
* Atomic claim-on-read, release-on-write protocol
* Read-only STATUS register exposing the full taken bitmask
* Release takes priority over a simultaneous claim on the same slot

Claim Protocol
**************

Read the semaphore register for slot *n*:

* Returns **0** - the slot was free; the caller now holds it.
* Returns **1** - the slot was already taken; the caller must retry.

The returned value reflects the state *before* the read. The taken flag is set
atomically within the same transaction, so no interleaving is possible.

Release Protocol
****************

Write any value to the semaphore register for slot *n* to unconditionally release it.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 8 bit address and 32 bit data width.

Parameter
=========

``SemaphoreCtrl.Parameter`` configures the semaphore controller.

.. list-table:: SemaphoreCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - count
     - Int
     - Number of semaphore slots. Must be between 1 and 32.
     - 8

``SemaphoreCtrl.Parameter`` has predefined presets for common use cases.

.. code-block:: scala

   object Parameter {
     def small()  = Parameter(count = 4)
     def medium() = Parameter(count = 8)
     def large()  = Parameter(count = 16)
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0xE
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

**Self Disclosure:**

.. flat-table:: Self Disclosure Registers
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
     - count
     -
     - Rx
     - Number of semaphore slots in this instance.

**Status Register:**

.. flat-table:: Status Register
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x00C
     - count - 1 .. 0
     - taken
     - 0x0
     - Rx
     - Bitmask of currently taken slots. Bit *n* is set while slot *n* is held.

**Semaphore Registers:**

One 32-bit register per slot, starting at 0x010. Read to claim; write to release.

.. flat-table:: Semaphore Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x010
     - 0
     - SEM_0
     - 0x0
     - RW
     - Read: 0 = claimed successfully, 1 = already taken. Write: release slot 0.
   * - 0x014
     - 0
     - SEM_1
     - 0x0
     - RW
     - Read: 0 = claimed successfully, 1 = already taken. Write: release slot 1.
   * - ...
     -
     -
     -
     -
     -
   * - 0x010 + slot x 0x4
     - 0
     - SEM_n
     - 0x0
     - RW
     - Read: 0 = claimed successfully, 1 = already taken. Write: release slot *n*.
