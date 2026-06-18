.. _hardware-peripherals-timer:

General-Purpose Timer
#####################

The general-purpose timer provides configurable counting, periodic interrupt
generation, and compare-match events. Multiple independent timer instances can
be instantiated in a single IP block, each with its own prescaler, counter, and
compare channels.

All timer logic is bus-driven. There are no external IO signals - the IP is
purely internal.

Features
********

* Configurable number of independent timer instances (``count``, 1-16)
* Configurable compare channels per timer (``channelCount``, 1-8)
* Configurable counter width (``width``, 1-32 bits)
* Optional clock prescaler per timer (``prescalerWidth``, 0 = no prescaler)
* Three operating modes: free-run, periodic (auto-reload), one-shot
* Counter preload: software can write the counter register while stopped
* Compare-match events: each channel fires independently when counter equals its compare register
* One-shot mode clears the enable bit automatically on completion
* Combined InterruptCtrl: single ``interrupt`` output with per-source pending and mask registers
* Supported buses: APB3, TileLink, Wishbone

Operating Modes
***************

.. list-table::
   :widths: 15 85
   :header-rows: 1

   * - Mode
     - Behaviour
   * - ``00`` free-run
     - Counter counts up from 0 to ``2^width - 1`` then wraps to 0. Overflow event fires on wrap.
   * - ``01`` periodic
     - Counter counts up from 0 to ``reload``, fires overflow event, and resets to 0. Repeats indefinitely while enabled.
   * - ``10`` one-shot
     - Counter counts up from 0 to ``reload``, fires overflow event, resets to 0, and clears the enable bit.

Parameters
**********

.. list-table::
   :widths: 20 15 65
   :header-rows: 1

   * - Parameter
     - Default
     - Description
   * - ``count``
     - ``1``
     - Number of independent timer instances (1-16)
   * - ``channelCount``
     - ``1``
     - Compare channels per timer instance (1-8)
   * - ``width``
     - ``32``
     - Counter and compare register width in bits (1-32)
   * - ``prescalerWidth``
     - ``16``
     - Prescaler counter width in bits. 0 disables the prescaler (counter ticks every clock cycle).

Register Map
************

.. list-table::
   :widths: 10 20 70
   :header-rows: 1

   * - Offset
     - Name
     - Description
   * - 0x000
     - ``ip_header``
     - IP Identification header
   * - 0x004
     - ``ip_version``
     - IP Identification version
   * - 0x008
     - ``info``
     - Compile-time parameters (read-only, see below)
   * - 0x00C
     - ``irq_pending``
     - Interrupt pending bits - sticky W1C, one bit per source
   * - 0x010
     - ``irq_mask``
     - Interrupt mask - 1 enables the corresponding source
   * - 0x014 + t x stride
     - ``control[t]``
     - Per-timer control register (see below)
   * - 0x018 + t x stride
     - ``prescaler[t]``
     - Prescaler reload value. Counter decrements; tick fires when it reaches 0 and reloads.
   * - 0x01C + t x stride
     - ``counter[t]``
     - Current counter value (R/W; write preloads the counter while stopped)
   * - 0x020 + t x stride
     - ``reload[t]``
     - Auto-reload value used by periodic and one-shot modes
   * - 0x024 + t x stride + ch x 4
     - ``compare[t][ch]``
     - Compare value for channel ``ch``

``stride = 0x10 + channelCount x 4``

Info Register (0x008)
=====================

.. list-table::
   :widths: 15 15 70
   :header-rows: 1

   * - Bits
     - Field
     - Description
   * - [7:0]
     - ``count``
     - Number of timer instances
   * - [15:8]
     - ``channelCount``
     - Compare channels per timer
   * - [23:16]
     - ``width``
     - Counter width in bits
   * - [31:24]
     - ``prescalerWidth``
     - Prescaler width in bits (0 = no prescaler)

Control Register (per timer)
=============================

.. list-table::
   :widths: 15 15 70
   :header-rows: 1

   * - Bits
     - Field
     - Description
   * - [0]
     - ``enable``
     - Start/stop the timer. Cleared automatically by hardware in one-shot mode.
   * - [2:1]
     - ``mode``
     - Operating mode: ``00`` = free-run, ``01`` = periodic, ``10`` = one-shot

Interrupt Pending / Mask Layout
================================

Bits are allocated per timer, with ``1 + channelCount`` bits each:

.. code-block:: text

   bit  t*(1+channelCount) + 0       timer t overflow
   bit  t*(1+channelCount) + 1       timer t compare channel 0
   bit  t*(1+channelCount) + 2       timer t compare channel 1
   ...
