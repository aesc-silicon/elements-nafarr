.. _hardware-system-watchdog:

Hardware Watchdog Timer (WDT)
#############################

The Hardware Watchdog Timer provides one or more independent watchdog instances. Each
watchdog has a configurable prescaler and down-counter. Software must periodically kick
a watchdog to prevent it from timing out. An optional windowed mode restricts valid kicks
to a defined time window, catching both missing kicks (timeout) and runaway loops that
kick too frequently (window violation).

Features
********

* Configurable number of independent watchdog instances
* Per-watchdog configurable counter width and prescaler width
* Windowed mode: valid kick window defined by ``window_open`` threshold
* Write-once lock bit: prevents disabling and configuration changes once armed
* Self-disclosure register exposing all compile-time parameters
* Per-watchdog interrupt controller with four independently maskable sources (two without windowed mode)
* Single combined interrupt output - asserted when any enabled source fires

Interrupt Architecture
**********************

Each watchdog has four interrupt sources (two when ``windowed = false``):

- **Bit 0**: timeout -> interrupt (recoverable notification)
- **Bit 1**: timeout -> error (non-recoverable; typically routed to reset controller)
- **Bit 2**: window violation -> interrupt (windowed mode only)
- **Bit 3**: window violation -> error (windowed mode only)

Bits 0 and 1 share the same underlying timeout event; bits 2 and 3 share the window
violation event. This allows the SoC integrator to route each event independently to the
interrupt controller, reset controller, or both.

All masked pending bits across all watchdog instances are OR-ed into a single
``interrupt`` output. In the interrupt service routine, software reads the per-watchdog
``irq_pending`` registers to identify the source, services the condition, then writes
``1`` to the pending bit to clear it.

Protocol
********

**Setup:**

1. Write ``prescaler`` to set the tick frequency: ``f_tick = f_clk / (prescaler + 1)``.
2. Write ``timeout`` to set the reload value. The watchdog fires after ``(timeout + 1)``
   ticks.
3. If windowed mode is compiled in, write ``window_open`` to the counter value below which
   kicks are valid. A kick is valid when ``counter <= window_open``.
4. Write ``irq_mask`` to enable the desired interrupt sources.
5. Optionally write bit 1 of ``control`` to lock the configuration.
6. Write bit 0 of ``control`` to enable the watchdog. The counter loads with ``timeout``
   on the rising edge of the enable bit.

**Operation:**

- Write any value to ``kick`` while the counter is within the window to reload the counter.
- Writing ``kick`` outside the window (windowed mode) generates a window violation.
- When the counter reaches zero a timeout fires; the counter reloads automatically.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

.. list-table:: WatchdogCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - count
     - Int
     - Number of independent watchdog instances. Must be between 1 and 255.
     - 1
   * - width
     - Int
     - Counter bit width. Must be between 1 and 32.
     - 32
   * - prescalerWidth
     - Int
     - Prescaler bit width. Must be between 1 and 32.
     - 16
   * - windowed
     - Boolean
     - Enable windowed mode and ``window_open`` register.
     - false
   * - locked
     - Boolean
     - Include write-once lock bit in the control register.
     - true

.. code-block:: scala

   object Parameter {
     def default()  = Parameter()
     def small()    = Parameter(width = 16, prescalerWidth = 8)
     def windowed() = Parameter(windowed = true)
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x15
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
   * - :rspan:`4` 0x008
     - 31 - 26
     - -
     - 0
     - Rx
     - Reserved.
   * - 25
     - locked
     -
     - Rx
     - 1 if the lock feature is compiled in.
   * - 24
     - windowed
     -
     - Rx
     - 1 if windowed mode is compiled in.
   * - 23 - 16
     - prescalerWidth
     -
     - Rx
     - Prescaler bit width.
   * - 15 - 8
     - width
     -
     - Rx
     - Counter bit width.
   * - 7 - 0
     - count
     -
     - Rx
     - Number of watchdog instances.

**Per-Watchdog Registers:**

One set per watchdog instance. Instance 0 starts at 0x00C; stride between instances
is 0x20.

.. flat-table:: Per-Watchdog Registers (base = 0x00C + instance x 0x20)
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` base + 0x00
     - 1
     - lock
     - 0
     - RW
     - Write-once lock bit. Once set, disabling the watchdog and writes to
       ``prescaler``, ``timeout``, and ``window_open`` are ignored. Only present
       when ``locked = true``; reads 0 otherwise.
   * - 0
     - enable
     - 0
     - RW
     - Enable the watchdog. Rising edge reloads the counter with ``timeout``.
       Writing 0 is ignored when ``lock`` is set.
   * - base + 0x04
     - width - 1 downto 0
     - prescaler
     - max
     - RW
     - Prescaler reload value. Tick frequency = ``f_clk / (prescaler + 1)``.
       Write ignored when locked.
   * - base + 0x08
     - width - 1 downto 0
     - timeout
     - max
     - RW
     - Counter reload value. Timeout fires after ``(timeout + 1)`` ticks.
       Write ignored when locked.
   * - base + 0x0C
     - width - 1 downto 0
     - window_open
     - 0
     - RW
     - Kick valid threshold. A kick is accepted when ``counter <= window_open``.
       Reads 0 when ``windowed = false``. Write ignored when locked.
   * - :rspan:`2` base + 0x10
     - 2
     - inWindow
     - 0
     - Rx
     - High while the counter is within the kick window (``windowed`` only).
   * - 1
     - locked
     - 0
     - Rx
     - Current lock state. Reads 0 when ``locked = false``.
   * - 0
     - enabled
     - 0
     - Rx
     - Current enable state.
   * - base + 0x14
     - irqCount - 1 downto 0
     - irq_pending
     - 0
     - RW
     - Interrupt pending flags. Write ``1`` to clear a bit.
       Bit 0 = timeout interrupt, bit 1 = timeout error,
       bit 2 = violation interrupt, bit 3 = violation error
       (bits 2-3 only when ``windowed = true``).
   * - base + 0x18
     - irqCount - 1 downto 0
     - irq_mask
     - 0
     - RW
     - Interrupt enable mask. Set a bit to enable the corresponding source.
       Same bit layout as ``irq_pending``.
   * - base + 0x1C
     - -
     - kick
     - -
     - xW
     - Write any value to kick the watchdog. Reloads the counter when within the
       window; raises a window violation when outside the window (windowed mode).
