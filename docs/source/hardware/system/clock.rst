.. _hardware-system-clock:

Clock Controller
################

The Clock Controller generates and manages multiple independent clock domains from a
single input clock. Each domain has a configurable target frequency and optional reset
and synchronisation relationships. The controller exposes a per-domain software enable
mask over the bus interface, allowing software to gate individual clocks at runtime.

Features
********

* Configurable number of independent clock domains
* Per-domain target frequency specified at elaboration time
* Per-domain software enable mask via bus register
* Optional association with a reset domain from the Reset Controller
* Optional ``synchronousWith`` relationship between domains
* Three implementations: simulation/ASIC (``ClockDividerController``), Lattice ECP5
  (``LatticeECP5PllController``), and Xilinx 7-series (``XilinxPllController``)

Implementations
***************

ClockDividerController (ASIC / Simulation)
==========================================

Generates output clocks by integer division of the input clock. Each output frequency
must evenly divide the input frequency, and the divider must be even to guarantee a 50%
duty cycle. Output clocks start high after reset is released. Divider 1 passes the input
clock through unchanged.

LatticeECP5PllController (FPGA)
================================

Uses two ``EHXPLLL`` PLL primitives on Lattice ECP5 FPGAs to generate up to six output
clocks. The VCO frequency is configurable (default 400 MHz). The PLL parameters
(``clkIDiv``, ``clkFbDiv``, ``clkOpDiv``) are derived from the input clock and VCO
frequency at elaboration time.

XilinxPllController (FPGA)
============================

Uses a ``PLLE2_BASE`` primitive on Xilinx 7-series FPGAs to generate up to six output
clocks. The multiply factor is configurable and specified at elaboration time.

Connecting Clock and Reset Domains
===================================

``ClockControllerCtrl.connect`` wires the generated clock and reset outputs together
into SpinalHDL ``ClockDomain`` objects. For each domain, it creates an internal
``ClockDomain`` using the domain's frequency and reset configuration, connects the clock
output, and optionally connects a reset signal from a ``ResetControllerBase`` instance.
If ``synchronousWith`` is set, the domain is marked as synchronous with the referenced
domain.

.. code-block:: scala

   ClockControllerCtrl.connect(parameter, clockCtrl, resetCtrl)

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 12 bit address and 32 bit data width.

``ClockParameter`` defines a single clock domain.

.. list-table:: ClockParameter
   :widths: 25 20 30 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - name
     - String
     - Identifier for this clock domain.
     -
   * - frequency
     - HertzNumber
     - Target output frequency.
     -
   * - reset
     - String
     - Name of the reset domain from the Reset Controller to associate with this
       clock domain. Empty string means no reset connection.
     - ""
   * - resetConfig
     - ClockDomainConfig
     - Reset kind and polarity for this clock domain.
     - SYNC, active LOW
   * - synchronousWith
     - String
     - Name of another clock domain this domain is synchronous with. Must refer
       to a domain defined in the same ``Parameter``. Empty string means no
       synchronous relationship.
     - ""

``ClockControllerCtrl.Parameter`` configures the controller.

.. list-table:: ClockControllerCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - domains
     - List[ClockParameter]
     - List of clock domains. Must contain at least one entry. If
       ``synchronousWith`` is set on any domain, the referenced name must exist
       in this list.
     -

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0xC
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

.. flat-table:: Clock Controller Registers
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
     - domains
     -
     - Rx
     - Number of clock domains in this instance.
   * - 0x00C
     - N - 0
     - enable
     - all 1s
     - RW
     - Per-domain clock enable mask. Writing a ``0`` to bit *n* gates the clock
       output for domain *n*. All domains are enabled by default.
