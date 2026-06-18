.. _hardware-system-reset:

Reset Controller
################

The Reset Controller manages multiple independent reset domains within a system. Each
domain has a configurable assertion delay, ensuring downstream logic is held in reset for
a minimum number of clock cycles before being released. The controller supports both
software-triggered resets (via bus writes) and hardware-triggered resets (via external
signals), with per-domain enable masking.

Features
********

* Configurable number of independent reset domains
* Per-domain assertion delay (minimum reset pulse width in clock cycles)
* Software-triggered reset via bus register write
* Hardware-triggered reset via external signal per domain
* Per-domain enable mask to suppress unwanted triggers
* Software acknowledge to clear pending software triggers
* Two implementations: ASIC (``DummyResetController``) and FPGA (``GeneratorResetController``)

Implementations
***************

DummyResetController (ASIC)
============================

Intended for ASIC designs. Takes explicit ``mainClock`` and ``mainReset`` inputs to build
its own internal clock domain with synchronous, active-low reset. Both software triggers
(via ``config.trigger``) and hardware triggers (via ``io.trigger``) are supported. The
final reset output is ANDed with ``mainReset``, so the external reset always overrides.

GeneratorResetController (FPGA)
================================

Intended for FPGA designs. Uses a ``BOOT`` reset kind, relying on the FPGA fabric to
handle the power-on reset automatically. Both software triggers (via ``config.trigger``)
and hardware triggers (via ``io.trigger``) are supported. Hardware triggers are gated by
the per-domain ``enable`` mask; software triggers are not. This results in logic suited
to FPGA synthesis flows where the power-on reset is handled by the fabric itself.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 12 bit address and 32 bit data width.

``ResetParameter`` defines a single reset domain.

.. list-table:: ResetParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - name
     - String
     - Identifier for this reset domain. Used to look up the domain by name.
     -
   * - delay
     - Int
     - Number of clock cycles the reset is held asserted after a trigger. Must be
       at least 1.
     -

``ResetControllerCtrl.Parameter`` configures the controller.

.. list-table:: ResetControllerCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - domains
     - List[ResetParameter]
     - List of reset domains. Must contain at least one entry.
     -

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0xB
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

.. flat-table:: Reset Controller Registers
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
     - Number of reset domains in this instance.
   * - 0x00C
     - N - 0
     - enable
     - all 1s
     - RW
     - Per-domain enable mask. When a bit is ``0``, hardware triggers for that domain
       are ignored. Software triggers via the ``trigger`` register are unaffected by
       this mask.
   * - 0x010
     - N - 0
     - trigger
     - 0
     - RW
     - Software reset trigger. Writing a ``1`` to bit *n* asserts a reset on domain
       *n*. Bits are cleared by writing to the ``acknowledge`` register.
   * - 0x014
     - -
     - acknowledge
     - -
     - W
     - Write-only. Any write to this address clears all pending software trigger bits
       in the ``trigger`` register.
