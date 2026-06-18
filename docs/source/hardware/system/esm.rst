.. _hardware-system-esm:

Error Signaling Module (ESM)
############################

The Error Signaling Module aggregates error outputs from multiple IP cores into one
central safety-aware hub. Each input is routed to one or more severity levels via a
single combined enable register per bank. Events are latched until cleared by software,
and a configurable grace-period counter gives the CPU a last-chance window before a hard
reset is requested.

The ESM is inspired by Texas Instruments' Error Signaling Module found in their
safety-critical TMS570 and AM2x microcontroller families.

Features
********

* Up to 256 independently configurable error inputs
* Two-FF synchroniser on every input (glitch filter, metastability protection)
* Four independently maskable severity levels per input: INFO, WARN, ERROR, FATAL
* An input can be assigned to multiple levels simultaneously
* Combined enable and pending registers pack all four levels into one 32-bit word
* Banks of 8 inputs scale the register interface beyond 32 inputs without breaking software
* Configurable grace-period counter for ERROR level (bypassed by FATAL)
* Software error injection for functional safety testing (ISO 26262 / IEC 61508)
* Write-once lock bit: freezes ERROR/FATAL routing and inject at runtime
* Master enable gate: no events captured while ESM is disabled
* Self-disclosure register exposing all compile-time parameters

Severity Levels
***************

.. list-table::
   :widths: 15 30 55
   :header-rows: 1

   * - Level
     - Output
     - Typical use
   * - INFO
     - ``infoInterrupt`` -> PLIC low-priority lane
     - Diagnostic events; can be polled. Examples: correctable ECC, PRNG bad seed.
   * - WARN
     - ``warnInterrupt`` -> PLIC high-priority lane
     - Recoverable faults requiring prompt software response.
   * - ERROR
     - ``errorSignal`` after grace-period counter
     - Serious faults; CPU has a configurable window to respond before reset.
   * - FATAL
     - ``errorSignal`` immediately
     - Unrecoverable faults; bypasses the grace-period counter entirely.

Enable/Pending Register Layout
*******************************

Each bank covers 8 inputs. The ``enable`` and ``pending`` registers share the same
32-bit layout, packing all four severity levels into one word:

.. list-table::
   :widths: 20 20 60
   :header-rows: 1

   * - Bits
     - Level
     - Description
   * - [31:24]
     - FATAL
     - One bit per input [7:0] of the bank. Locked after ESM lock.
   * - [23:16]
     - ERROR
     - One bit per input [7:0] of the bank. Locked after ESM lock.
   * - [15:8]
     - WARN
     - One bit per input [7:0] of the bank. Never locked.
   * - [7:0]
     - INFO
     - One bit per input [7:0] of the bank. Never locked.

To route input 2 to WARN and FATAL simultaneously::

    ESM_LEVEL_WARN(1u << 2) | ESM_LEVEL_FATAL(1u << 2)  /* = 0x04000400 */

The last bank may cover fewer than 8 inputs; upper bits within each level byte are
reserved and always read as zero.

Grace-Period Counter
********************

When any ERROR-level pending bit becomes set the grace-period counter loads
``errorCounter`` and starts counting down. If software clears all ERROR pending
bits before the counter reaches zero the counter resets and ``errorSignal`` is
never asserted from that event. If the counter expires while any ERROR pending
bit is still set ``errorSignal`` latches high and remains asserted until all
ERROR pending bits are cleared.

Setting ``errorCounter = 0`` makes ERROR behave identically to FATAL
(immediate assertion).

FATAL events assert ``errorSignal`` unconditionally and do not interact with
the counter.

Interrupt Architecture
**********************

Pending bits are pre-masked: a pending bit is set only when the corresponding
enable bit is also set. Pending bits are cleared by writing ``1`` (W1C). The same
input can contribute to multiple levels simultaneously.

``infoInterrupt`` and ``warnInterrupt`` are asserted while any pending bit in the
INFO or WARN field (across all banks) is set. ``errorSignal`` is asserted while the
grace-period counter has expired (ERROR) OR any FATAL pending bit is set.

In an interrupt service routine, software reads each bank's ``pending`` register,
uses the ``ESM_PENDING_INFO/WARN/ERROR/FATAL()`` macros to identify the level and
source, services the condition, and writes the mask back to clear the bits.

Error Injection
***************

Software injection is controlled by ``injectEnable`` (control bit 2). When
enabled, writing to a bank's inject register sets bits that are OR-ed with the
synchronised hardware inputs. The inject register can only be written when
``injectEnable = 1`` and the ESM is not locked.

Locking the ESM (writing control bit 1) atomically clears ``injectEnable``,
zeroes all inject bank registers, and freezes ERROR/FATAL enable fields and
``errorCounter``. This ensures injection cannot be accidentally re-enabled in
production after the safety configuration has been locked.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

.. list-table:: EsmCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - inputCount
     - Int
     - Number of error inputs. Must be between 1 and 256.
     - 32
   * - counterWidth
     - Int
     - Bit width of the grace-period counter. Must be between 1 and 32.
     - 24
   * - locked
     - Boolean
     - Include write-once lock bit in the control register.
     - true

.. code-block:: scala

   object Parameter {
     def default() = Parameter()
     def small()   = Parameter(counterWidth = 16)
     def large()   = Parameter(counterWidth = 32)
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x16
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

**Self Disclosure:**

.. flat-table:: Info Register (0x008)
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`3` 0x008
     - 31 - 25
     - -
     - 0
     - Rx
     - Reserved.
   * - 24
     - locked
     -
     - Rx
     - 1 if the lock feature is compiled in.
   * - 23 - 16
     - counterWidth
     -
     - Rx
     - Grace-period counter bit width.
   * - 15 - 9
     - bankCount
     -
     - Rx
     - Number of banks (= ceil(inputCount / 8)).
   * - 8 - 0
     - inputCount
     -
     - Rx
     - Number of error inputs.

**Global Registers:**

.. list-table:: Global Register Map
   :header-rows: 1
   :widths: 15 85

   * - Address
     - Description
   * - 0x00C
     - Control register (see below).
   * - 0x010
     - Status register (read-only): ``counterActive[0]``, ``errorSignal[1]``.
   * - 0x014
     - Error counter - grace-period reload value. Write ignored when locked.

.. flat-table:: Control Register (0x00C)
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`2` 0x00C
     - 2
     - injectEnable
     - 0
     - RW
     - Enable software error injection. Cleared on lock.
   * - 1
     - lock
     - 0
     - RW
     - Write-once lock bit. Freezes ERROR/FATAL enable, errorCounter, and inject
       registers. Only present when ``locked = true``; reads 0 otherwise.
   * - 0
     - enable
     - 0
     - RW
     - Master enable. When 0, no new events are captured. Writing 0 is ignored when locked.

**Per-Bank Registers:**

Banks are numbered 0 to ``bankCount - 1``. Bank N base address = ``0x028 + N * 0x10``.

.. list-table:: Per-Bank Register Offsets
   :header-rows: 1
   :widths: 15 15 70

   * - Offset
     - Name
     - Description
   * - +0x00
     - enable
     - Combined routing mask for all four levels (see layout above).
       ERROR/FATAL fields (bits [31:16]) are frozen when locked.
   * - +0x04
     - pending
     - Combined pending bits for all four levels. Write ``1`` to clear (W1C).
   * - +0x08
     - raw
     - Current active inputs for this bank (read-only). Reflects synchronised
       hardware inputs OR-ed with inject. Not gated by master enable.
   * - +0x0C
     - inject
     - Bits [7:0]: injected inputs. Writeable only when ``injectEnable = 1``
       and ESM is not locked. Cleared to zero on lock.
