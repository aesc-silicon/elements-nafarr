.. _hardware-peripherals-pwm:

Pulse-Width Modulation
######################

The Pulse-Width Modulation (PWM) IP Core is a multi-channel controller for generating
precise PWM signals. Each channel operates independently with its own clock divider and
waveform registers, making the core suitable for motor control (H-bridge with dead-time
insertion), LED dimming, and multi-phase power conversion.

Features
********

* Configurable number of independent channels
* Per-channel clock divider for independent frequency scaling
* Two alignment modes per channel: edge-aligned (asymmetric) and center-aligned (symmetric)
* Waveform defined by ``risingEdge`` and ``fallingEdge`` thresholds for flexible duty cycle
* Shadow-buffered waveform registers — updates take effect glitch-free at the next period boundary
* Complementary output with programmable dead-time blanking (shoot-through protection)
* Phase offset: counters start at a configurable offset on enable or ``syncIn``
* N-shot mode: run a precise number of complete periods then freeze
* ``syncIn`` signal to align multiple channels to an external reference
* Per-channel ``syncOut`` pulse at the period boundary for daisy-chaining
* ``faultIn`` override: forces all outputs to safe idle state (highest priority)
* Per-channel period-complete interrupt

IO Signals
**********

.. list-table:: Pwm.Io
   :widths: 20 10 70
   :header-rows: 1

   * - Signal
     - Direction
     - Description
   * - output
     - out
     - Primary PWM outputs, one bit per channel.
   * - compOutput
     - out
     - Complementary outputs, one bit per channel. Inverse of ``output`` with dead-time
       blanking applied.
   * - syncOut
     - out
     - Sync pulse outputs, one bit per channel. Pulses for one clock cycle at each period
       boundary (edge-aligned: counter wrap; center-aligned: counter peak).
   * - syncIn
     - in
     - Global synchronisation input. Resets the counter of every enabled channel to its
       ``phaseOffset`` value on the same clock edge.
   * - faultIn
     - in
     - Global fault input (highest priority). While asserted, all primary and complementary
       outputs are forced to their safe idle state (the ``invert`` level), regardless of
       any other setting.
   * - interrupt
     - out
     - OR of all pending period-complete interrupts across all channels.

Operation
*********

Waveform Generation
===================

The output level of each channel is determined by comparing the running counter against
two thresholds, ``risingEdge`` and ``fallingEdge``:

* Output is **active** (``!invert``) when ``counter >= risingEdge && counter <= fallingEdge``.
* Output is **idle** (``invert``) outside that window.

Setting ``invert = 1`` swaps the active and idle levels, producing an active-low signal.

Edge-Aligned Mode (``mode = 0``)
=================================

The counter counts **down** from ``period`` to 0 and then reloads:

.. code-block:: text

   period ─┐           ┌─ period
           │           │
           └───────────┘
           ↑           ↑
        syncOut      syncOut   (pulses at counter wrap = 0)

The ``syncOut`` pulse is emitted when the counter wraps to ``period``. This mode produces
a left-aligned (asymmetric) waveform and minimises switching noise compared to center-aligned
mode when multiple channels are used at the same phase.

Center-Aligned Mode (``mode = 1``)
====================================

The counter counts **up** from 0 to ``period``, then back down to 0 (triangular):

.. code-block:: text

        syncOut
          ↓
   period /\
         /  \
        /    \
   0 ──/      \──

The ``syncOut`` pulse is emitted when the counter reaches ``period`` (the peak). This mode
produces a symmetric (center-aligned) waveform and is commonly used in motor drives.

Shadow Buffering
================

``risingEdge``, ``fallingEdge``, and ``period`` are shadow-buffered. Writing these
registers via the bus updates the shadow copy immediately, but the live waveform registers
are only latched at two moments:

1. On the **rising edge of enable** (channel freshly enabled).
2. At each **period boundary** (counter wrap or down-count to 0 in center-aligned mode).

This ensures that duty cycle and period changes are always applied atomically at a known
point in the waveform, with no glitches.

Dead-Time Insertion
===================

When ``deadTime > 0``, both outputs are forced to their idle state for ``deadTime`` clock
ticks whenever the primary output transitions. This prevents simultaneous conduction in
H-bridge topologies (shoot-through):

.. code-block:: text

   output     ───┐  ···  ┌─────
   compOutput ───┘  ···  └─────
                  ← dt →

Dead-time is applied symmetrically on both the rising and falling edges of ``output``.
Setting ``deadTime = 0`` disables dead-time insertion.

N-Shot Mode
===========

By default (``shotCount = 0``), each channel runs continuously. Setting ``shotCount = N``
causes the channel to run exactly N complete periods and then stop:

* On each period boundary the internal ``shotRemaining`` counter is decremented.
* When ``shotRemaining`` reaches 1, ``shotDone`` is set and the channel freezes.
* Re-enabling the channel (``enable`` rising edge) clears ``shotDone`` and reloads
  ``shotRemaining`` from the shadow register.

Phase Offset and Synchronisation
=================================

``phaseOffset`` controls where the counter starts when a channel is first enabled or when
a ``syncIn`` pulse is received. Setting different offsets on multiple channels distributes
their switching events in time, reducing ripple current in multi-phase converters.

``syncIn`` is a single global signal that resets **all** enabled channel counters to their
respective ``phaseOffset`` values simultaneously on the same clock edge.

Configuration
*************

Available bus architectures:

- APB3
- Wishbone
- AvalonMM

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

``Pwm.Parameter`` defines the number of channels.

.. list-table:: Pwm.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - channels
     - Int
     - Number of independent PWM channels. Must be greater than 0.
     -

``PwmCtrl.InitParameter`` defines the initialization values for certain registers.

.. list-table:: PwmCtrl.InitParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - clockDivider
     - Int
     - Initialization value of the per-channel clock divider.
     - 0

.. note::

   A value of ``0`` in ``InitParameter`` means the field is not initialized (disabled).
   This allows initializing only specific fields.

``PwmCtrl.PermissionParameter`` defines bus-access permission rules.

.. list-table:: PwmCtrl.PermissionParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - busCanWriteClockDividerConfig
     - Boolean
     - When ``false``, the clock divider register is read-only from the bus.
     -

``PwmCtrl.Parameter`` configures the PWM controller.

.. list-table:: PwmCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - io
     - Pwm.Parameter
     - IO parameters (channel count).
     -
   * - init
     - PwmCtrl.InitParameter
     - Initialization values.
     - InitParameter.disabled
   * - permission
     - PwmCtrl.PermissionParameter
     - Bus access permissions.
     - PermissionParameter.granted
   * - clockDividerWidth
     - Int
     - Bit width of the per-channel clock divider counter.
     - 20
   * - channelPeriodWidth
     - Int
     - Bit width of the period counter.
     - 20
   * - channelPulseWidth
     - Int
     - Bit width of the ``risingEdge`` and ``fallingEdge`` registers.
     - 20
   * - deadTimeWidth
     - Int
     - Bit width of the dead-time counter.
     - 8
   * - shotCountWidth
     - Int
     - Bit width of the N-shot counter.
     - 8

``PwmCtrl.Parameter`` provides a convenience factory:

.. code-block:: scala

   object Parameter {
     def default(channels: Int = 1) = Parameter(Pwm.Parameter(channels))
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x2
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

**Self Disclosure:**

This block discloses implementation widths and channel count to software drivers.

.. flat-table:: Self Disclosure Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`3` 0x008
     - 31 - 24
     - channelPeriodWidth
     -
     - Rx
     - Bit width of the period counter.
   * - 23 - 16
     - channelPulseWidth
     -
     - Rx
     - Bit width of the risingEdge/fallingEdge registers.
   * - 15 - 8
     - clockDividerWidth
     -
     - Rx
     - Bit width of the clock divider counter.
   * - 7 - 0
     - channels
     -
     - Rx
     - Number of channels.
   * - 0x00C
     - 0
     - busCanWriteClockDividerConfig
     -
     - Rx
     - ``1`` if the clock divider register is writable from the bus.
   * - :rspan:`1` 0x010
     - 15 - 8
     - shotCountWidth
     -
     - Rx
     - Bit width of the N-shot counter.
   * - 7 - 0
     - deadTimeWidth
     -
     - Rx
     - Bit width of the dead-time counter.

**Interrupts:**

Period-complete interrupts use a standard interrupt controller with one bit per channel.
Writing ``1`` to a bit in the IP register clears (acknowledges) that interrupt.

.. flat-table:: Interrupt Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x014
     - channels - 0
     - periodComplete IP
     - 0
     - RW1C
     - Pending period-complete flags, one bit per channel. Write ``1`` to clear.
   * - 0x018
     - channels - 0
     - periodComplete IE
     - 0
     - RW
     - Enable mask for period-complete interrupts, one bit per channel.

**Error Detection:**

The error module uses a standard interrupt controller with two classes of error source.
Writing ``1`` to a bit in the error IP register clears (acknowledges) that error.

.. flat-table:: Error Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x01C
     - 0
     - faultIn IP
     - 0
     - RW1C
     - Set on the rising edge of ``faultIn``. Indicates that a hardware fault was
       asserted. Write ``1`` to clear.
   * - channels - 1
     - configError IP
     - 0
     - RW1C
     - Per-channel configuration error flags. Bit *n+1* is set when
       ``fallingEdge > period`` in channel *n*'s shadow registers.
       Write ``1`` to clear.
   * - :rspan:`1` 0x020
     - 0
     - faultIn IE
     - 0
     - RW
     - Enable mask for the ``faultIn`` error interrupt.
   * - channels - 1
     - configError IE
     - 0
     - RW
     - Per-channel enable mask for configuration error interrupts.

**Channel Configuration:**

Each channel occupies 0x28 (40) bytes. Channel *n* starts at address
``0x024 + n * 0x028``.

.. flat-table:: Channel Registers (base = 0x024 + n * 0x028)
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Offset
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`2` +0x00
     - 0
     - enable
     - 0
     - RW
     - Enable the channel. On the rising edge of this bit, live waveform registers are
       latched from shadow and the counter is reset to ``phaseOffset``.
   * - 1
     - invert
     - 0
     - RW
     - Invert both outputs. When set, idle state is logic-high and active state is
       logic-low.
   * - 2
     - mode
     - 0
     - RW
     - Alignment mode. ``0`` = edge-aligned (count-down), ``1`` = center-aligned
       (triangle counter).
   * - +0x04
     - clockDividerWidth - 0
     - clockDivider
     - InitParameter
     - RW or Rx
     - Per-channel clock divider. The channel tick rate is
       ``f_clk / (clockDivider + 1)``. Read-only if
       ``busCanWriteClockDividerConfig = false``.
   * - +0x08
     - channelPeriodWidth - 0
     - period
     - 0
     - RW
     - Shadow period. Latched into the live period register at the next period boundary.
   * - +0x0C
     - channelPulseWidth - 0
     - risingEdge
     - 0
     - RW
     - Shadow rising-edge threshold. Output goes active when ``counter >= risingEdge``.
   * - +0x10
     - channelPulseWidth - 0
     - fallingEdge
     - 0
     - RW
     - Shadow falling-edge threshold. Output goes idle when ``counter > fallingEdge``.
   * - +0x14
     - deadTimeWidth - 0
     - deadTime
     - 0
     - RW
     - Dead-time in clock ticks. Both outputs are blanked for this many ticks on every
       output transition. ``0`` disables dead-time insertion.
   * - +0x18
     - channelPeriodWidth - 0
     - phaseOffset
     - 0
     - RW
     - Counter start value applied on enable or ``syncIn``. Use to distribute switching
       events across multiple channels.
   * - +0x1C
     - shotCountWidth - 0
     - shotCount
     - 0
     - RW
     - Number of complete periods to generate. ``0`` = continuous operation.
   * - :rspan:`1` +0x20
     - 0
     - configError
     -
     - Rx
     - Set when ``fallingEdge > period`` in the shadow registers, indicating an invalid
       configuration.
   * - 1
     - shotDone
     -
     - Rx
     - Set when an N-shot sequence has completed (only meaningful when
       ``shotCount != 0``).
