.. _hardware-memory-hyperbus:

HyperBus Memory Controller (HyperBus)
#####################################

The HyperBus controller drives one or more HyperRAM / HyperFlash devices over the
8-bit double-data-rate HyperBus interface. The memory array is exposed as an ordinary
memory-mapped window (load/store), while a separate configuration register block
controls the PHY, access latency, the device configuration-register port, and a fault
controller. Two generic soft-PHYs are provided for FPGA and simulation: an oversampling
PHY (``HyperBusGenericPhy``, ``phy.clockDivider`` clocks per edge) and a full-rate DDR
PHY (``HyperBusGenericDdrPhy``) that moves two bytes per clock through ``SoftDdr`` with no
divider. A technology-specific PHY can be substituted for silicon. The controller and its
register map are identical across PHYs.

Features
********

* 8-bit DDR HyperBus master with a generic oversampling soft-PHY
* Memory-mapped data window plus a TL-UL configuration register port
* **Burst coalescing** - a contiguous multi-word burst is issued as a single linear
  HyperRAM burst (one command-address), streamed through a pipelined send/receive path
* **Abort/retry** - if the master under-runs the write stream or fails to drain the
  read responses, the burst is aborted and re-issued from the first uncommitted word,
  so every word is delivered exactly once and in order
* **Partitioning** - up to 8 devices, each a partition selected by address range with an
  independent read-permission flag
* Configurable HyperRAM access latency and RESET# pulse/halt timing
* Device configuration-register access port (e.g. CR0)
* Fault controller latching address, permission, timeout and unaligned faults into a
  single, maskable error output

Partitioning
************

Each entry of ``partitions`` describes one device: its size (bytes) and whether reads are
permitted. Partitions are concatenated from address 0, so partition *n* occupies
``[sum(sizes[0..n-1]), sum(sizes[0..n]))``. The controller decodes the access address to
the owning partition and asserts that device's chip select; an access outside every
partition raises an address fault, and a read of a non-readable partition raises a
permission fault.

Configuration
*************

Available bus architectures:

- TileLink (``dataBus`` TL-UH burst memory port + ``cfgBus`` TL-UL register port)
- AXI4-Shared
- BMB

By default, the configuration bus is defined with 10 bit address and 32 bit data width.

Parameter
=========

.. list-table:: HyperBusCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - hyperbus.partitions
     - List[(BigInt, Boolean)]
     - Per-device (size, readable) list. Up to 8 devices.
     -
   * - phy.clockDivider
     - Int
     - Soft-PHY oversampling divider (clocks per HyperBus edge = divider / 2).
     - 8
   * - frontend.storageDepth
     - Int
     - Depth of the front-end / retry buffer (max coalesced burst length in words).
     - 12
   * - init.latencyCycles
     - Int
     - Reset default for the HyperRAM access latency (3..7).
     - 7

.. code-block:: scala

   object Parameter {
     def default(partitions: List[(BigInt, Boolean)]) = Parameter(
       hyperbus = HyperBusParameter(partitions),
       phy      = PhyParameter(),
       frontend = FrontendParameter(),
       init     = InitParameter()
     )
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x19
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

.. flat-table:: Configuration Registers
   :widths: 10 12 15 10 12 41
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x010
     -
     - reset_trigger
     -
     - xW
     - Write any value to pulse RESET# using the configured pulse/halt widths.
   * - 0x014
     -
     - reset_pulse
     - 15
     - RW
     - RESET# assertion width, in clock cycles.
   * - 0x018
     -
     - reset_halt
     - 15
     - RW
     - Post-reset halt width before the first transaction, in clock cycles.
   * - 0x020
     - 2 - 0
     - latency
     - 7
     - RW
     - HyperRAM read/write access latency in cycles (3..7). Must match the device.
   * - :rspan:`2` 0x030
     - 31 - 16
     - reg_access (write)
     - 0x0
     - RW
     - Config-register port. **Write:** bit 15 = 1 read / 0 write, bits 14-0 =
       register address, bits 31-16 = write data.
   * - 15
     - reg_access read-bit
     -
     - RW
     - Set for a read command, clear for a write command.
   * - 14 - 0
     - reg_access address
     -
     - RW
     - Device configuration-register address. **Read** returns bit 31 = response
       valid, bit 16 = device fault, bits 15-0 = read data.
   * - 0x034
     - 31 - 16
     - cmd_availability
     -
     - Rx
     - Free entries in the register command FIFO (bits 15-0 hold response occupancy).
   * - 0x040
     - 3 - 0
     - error_pending
     - 0x0
     - RW
     - Latched faults, write 1 to clear: bit 0 address, bit 1 permission,
       bit 2 timeout, bit 3 unaligned.
   * - 0x044
     - 3 - 0
     - error_mask
     - 0x0
     - RW
     - Set a bit to report the corresponding fault on the error output.
