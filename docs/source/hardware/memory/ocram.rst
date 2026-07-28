.. _hardware-memory-ocram:

On-Chip RAM (OCRAM)
###################

The on-chip RAM is a single-port synchronous memory exposed as an ordinary memory-mapped
window (load/store). It has no configuration registers - it is a pure memory slave, so
there is no IP identification block. A generic behavioural model is used for FPGA and
simulation; a technology-specific variant instantiates foundry SRAM hard macros for
silicon.

Features
********

* Single-port synchronous RAM with a one-cycle read latency
* Burst-capable: serves multi-beat cache-line transfers up to the bus ``sizeBytes``, so it
  can back a cached (D$) master, not only single-word accesses
* Byte-enable writes via the bus write mask
* One outstanding message at a time (back-pressure until the response completes)

Variants
********

Generic
=======

``TileLinkOnChipRam(p, size)`` is the portable behavioural RAM (inferred ``Mem``). It is
TileLink-only and serves ``Get`` as N ``AccessAckData`` beats and ``Put`` as N ``A`` beats
followed by one ``AccessAck``.

IHP SG13G2
==========

The ``nafarr.memory.ocram.ihp.sg13g2`` variant instantiates SG13G2 SRAM hard macros. The
largest 32-bit macro is 2048x32 (8 kB); larger sizes are banked automatically, so ``size``
must be a positive multiple of 8 kB. It is available with a TileLink, AXI4-Shared or BMB
bus.

Configuration
*************

.. list-table:: Parameters
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - p
     - BusParameter
     - Bus parameter (``dataBytes`` = beat width, ``sizeBytes`` = max transfer / burst).
     -
   * - size
     - BigInt
     - RAM size in bytes. Power of two (generic); multiple of 8 kB (IHP SG13G2).
     -
