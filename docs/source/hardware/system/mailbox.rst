.. _hardware-system-mailbox:

Hardware Mailbox
################

The Hardware Mailbox provides symmetric FIFO-based message queues for inter-CPU
communication. Each channel carries 32-bit messages independently. CPU A
conventionally sends on channel 0 and receives on channel 1; CPU B does the
opposite. Both CPUs access the same register map.

Features
********

* Configurable number of symmetric channels (default: 2)
* Configurable FIFO depth per channel (small / medium / large presets)
* 32-bit message width matching the bus data width
* Per-channel status flags (empty, full) and occupancy counter
* Per-channel interrupt sources: not-empty (data arrived) and not-full (space available)
* Single combined interrupt output — asserted when any enabled channel interrupt fires

Interrupt Architecture
**********************

Each channel has independent ``irq_pending`` and ``irq_mask`` registers. An interrupt
source bit is set when its condition is true and the corresponding mask bit is enabled.
The hardware ORs all masked pending bits across all channels into a single ``interrupt``
output signal.

In the interrupt service routine, software must read the per-channel ``irq_pending``
registers to identify which channel fired, service the condition, and write ``1`` to the
corresponding pending bit to clear it.

Protocol
********

**Send (push):**

Write the 32-bit message payload to the channel's write register. If the FIFO is full,
the write is silently dropped. Software should check the full flag in the status register
before writing to avoid data loss.

**Receive (pop):**

Read the channel's read register to pop one message from the FIFO. The returned value is
the message payload. If the FIFO is empty, the read returns stale data. Software should
check the empty flag in the status register before reading.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 8 bit address and 32 bit data width.

Parameter
=========

.. list-table:: MailboxCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - depth
     - Int
     - FIFO depth per channel. Must be between 1 and 255.
     - 8
   * - channelCount
     - Int
     - Number of channels. Must be at least 2.
     - 2

``MailboxCtrl.Parameter`` has predefined presets for common use cases.

.. code-block:: scala

   object Parameter {
     def small()  = Parameter(depth = 4)
     def medium() = Parameter(depth = 8)
     def large()  = Parameter(depth = 16)
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0xF
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

**Self Disclosure:**

.. flat-table:: Self Disclosure Register
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x008
     - 15 - 8
     - depth
     -
     - Rx
     - FIFO depth per channel.
   * - 7 - 0
     - channels
     - 2
     - Rx
     - Number of channels.

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
   * - :rspan:`3` 0x00C
     - 3
     - full[1]
     - 0
     - Rx
     - Channel 1 FIFO full.
   * - 2
     - full[0]
     - 0
     - Rx
     - Channel 0 FIFO full.
   * - 1
     - empty[1]
     - 1
     - Rx
     - Channel 1 FIFO empty.
   * - 0
     - empty[0]
     - 1
     - Rx
     - Channel 0 FIFO empty.

The status register layout scales with ``channelCount``. Bits ``[channelCount-1:0]``
hold the empty flags and bits ``[2*channelCount-1:channelCount]`` hold the full flags.

**Channel Registers:**

Two sets of identical registers, one per channel. Channel 0 starts at 0x010;
channel 1 starts at 0x024. The stride between channels is 0x14.

.. flat-table:: Channel Registers (per channel, base = 0x010 + channel × 0x14)
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - base + 0x00
     - 31 - 0
     - write
     - —
     - xW
     - Push a 32-bit message into the channel FIFO. Dropped silently if full.
   * - base + 0x04
     - 31 - 0
     - read
     - —
     - Rx
     - Pop a 32-bit message from the channel FIFO. Returns stale data if empty.
   * - base + 0x08
     - 7 - 0
     - occupancy
     - 0
     - Rx
     - Number of messages currently stored in the FIFO.
   * - base + 0x0C
     - 1 - 0
     - irq_pending
     - 0
     - RW
     - Interrupt pending flags. Write ``1`` to a bit to clear it.
       Bit 0 = not-empty, bit 1 = not-full. All enabled pending bits across all
       channels are ORed into the single hardware interrupt output.
   * - base + 0x10
     - 1 - 0
     - irq_mask
     - 0
     - RW
     - Interrupt enable mask. Set a bit to enable the corresponding interrupt source.
       Bit 0 = not-empty, bit 1 = not-full.
