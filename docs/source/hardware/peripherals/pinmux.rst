.. _hardware-peripherals-pinmux:

Pinmux
######

A Pin Multiplexer (Pinmux) routes physical IO pins to one of several peripheral
input/output signals. Each physical pin can be connected to exactly one
peripheral at a time, selected by a per-pin option register. This allows a
small number of physical pins to serve many peripherals without requiring
dedicated pads for each function.

The Pinmux manages three sets of signals:

- **pins** — the physical bidirectional IO pads (tri-state: write, writeEnable, read).
- **inputs** — the full set of peripheral signals that can be routed to pins.
  Each peripheral contributes a slice of this bus.
- **options** — one selector value per pin, choosing which peripheral input
  slice is currently connected to that pin.

For each pin, the selected peripheral's ``write`` and ``writeEnable`` signals
are forwarded to the pad. The pad's ``read`` signal is forwarded back to the
selected peripheral only. Unselected peripherals see their ``read`` input held
low.

The mapping between physical pins and the subset of peripheral inputs they can
reach is fixed at elaboration time via the ``mapping`` parameter. A pin can
only select from the inputs listed for it in the mapping; attempting to set an
option value that falls outside the valid range for a pin results in undefined
behaviour (the output defaults to driven-low).

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

``Pinmux.Parameter`` defines the physical pin interface.

.. list-table:: Pinmux.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - width
     - Int
     - Number of physical IO pins. Must be greater than 0 and fit in 8 bits.
     -

``PinmuxCtrl.Parameter`` configures the Pinmux controller.

.. list-table:: PinmuxCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - io
     - Pinmux.Parameter
     - Physical pin parameters.
     -
   * - inputs
     - Int
     - Total number of peripheral input signals across all peripherals.
     -
   * - options
     - Int
     - Number of selectable inputs per pin. Determines the width of each option
       register. Must be greater than 0 and fit in 8 bits.
     -

The ``mapping`` argument passed to the constructor is an
``ArrayBuffer[(Int, List[Int])]`` that associates each physical pin index with
the list of peripheral input indices it can be connected to. The position of
an input index in the list determines the option value that selects it.

.. code-block:: scala

   // 12 pins, each connectable to 2 peripheral inputs.
   // Pin 0 → input 0 (option 0) or input 1 (option 1)
   // Pin 1 → input 2 (option 0) or input 3 (option 1), etc.
   val mapping = (0 until 12).map(i => (i, List(i * 2, i * 2 + 1))).to[ArrayBuffer]

   Apb3Pinmux(
     PinmuxCtrl.Parameter(Pinmux.Parameter(12), inputs = 24, options = 2),
     mapping
   )

.. note::

   ``inputs`` does not need to equal ``width × options``. It can be smaller,
   and the same input index may appear in the mapping list of multiple pins.
   This allows a single peripheral signal to be reachable from more than one
   physical pin simultaneously.

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0xD
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

**Self Disclosure:**

This block discloses the pin and option counts to software drivers.

.. flat-table:: Self Disclosure Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`2` 0x008
     - 31 - 16
     - Reserved
     - 0x0
     - Rx
     - Reserved. Reads as zero.
   * - 15 - 8
     - Options
     -
     - Rx
     - Number of selectable inputs per pin (``options`` parameter).
   * - 7 - 0
     - Width
     -
     - Rx
     - Number of physical IO pins (``Pinmux.Parameter.width``).

**Option Registers:**

One 32-bit register per physical pin, starting at 0x010. Each register holds
an 8-bit option selector; only the lower ``ceil(log2(options))`` bits are used
by the hardware. The remaining bits are stored but ignored.

.. flat-table:: Option Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x010
     - 7 - 0
     - Option[0]
     - 0x0
     - RW
     - Selects which peripheral input is connected to pin 0. Value N connects
       the N-th entry in the mapping list for pin 0.
   * - 0x014
     - 7 - 0
     - Option[1]
     - 0x0
     - RW
     - Selects which peripheral input is connected to pin 1.
   * - ...
     -
     -
     -
     -
     -
   * - 0x010 + pin × 0x4
     - 7 - 0
     - Option[pin]
     - 0x0
     - RW
     - Selects which peripheral input is connected to the given pin.
