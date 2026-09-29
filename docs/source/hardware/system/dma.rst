.. _hardware-system-dma:

Direct Memory Access Controller (DMA)
#####################################

The DMA controller moves data between memory and memory-mapped peripherals without CPU
involvement. It is a TileLink bus master with a configurable number of channels, a
descriptor-chaining engine and per-peripheral request lines for flow control.

Features
********

* 1 to 8 channels sharing one transfer engine (round-robin arbitration per chunk)
* TileLink UH master port with naturally aligned bursts up to ``burstBytes``
* Memory-to-memory, memory-to-peripheral and peripheral-to-memory transfers
* Incrementing or fixed source and destination addresses
* 8, 16 and 32 bit element widths with automatic byte-lane steering
* Up to 16 request handshakes; a gated channel moves one element per handshake
* Descriptor chaining: a channel follows ``next`` pointers through memory-resident descriptors
* Per-channel done and error interrupts, abort, bus error and alignment error reporting
* No cache coherency logic: buffers are managed by software (see below)

Transfer Engine
***************

A transfer is executed as a sequence of *chunks*. Each chunk is one ``Get`` on the source
followed by one ``PutFullData`` on the destination; data is staged in an internal FIFO of
``burstBytes`` bytes.

* **Burst mode** applies when both addresses increment and no request line is enabled. The
  chunk is the largest power of two that satisfies the alignment of both addresses, the
  remaining length and ``burstLimit``. Unaligned buffers or odd lengths are handled by
  falling back to smaller chunks down to single bytes. ``width`` is ignored.
* **Element mode** applies when either address is fixed or a request line is enabled. Every
  chunk moves exactly one element of ``width`` bytes. ``src``, ``dst`` and ``length`` must
  be multiples of the element size, otherwise the channel stops with an error.

Between chunks the engine re-arbitrates, so several channels progress concurrently and an
abort takes effect at the next chunk boundary.

Request Lines
=============

Each ``request`` input is a four-phase handshake (``DmaHandshake``) driven by a peripheral:
``req`` is its "ready" indication, e.g. *TX FIFO not full* or *RX FIFO not empty*, and
``ack`` is returned by the DMA. A channel with ``req_enable`` set is only scheduled while its
selected ``req`` is high and ``ack`` is low, and moves one element per grant:

1. The peripheral raises ``req`` while it can move one element.
2. The DMA moves one element and waits for the bus response.
3. The DMA raises ``ack``; the peripheral holds ``req`` low while ``ack`` is high.
4. The DMA drops ``ack`` once it sees ``req`` low. ``req`` then follows the peripheral state
   again, which already reflects the completed access.

Both signals hold their level until the other side responds, so the handshake tolerates any
latency between the peripheral and the DMA. ``DmaHandshakeCc`` synchronizes one handshake
between a peripheral clock domain and the DMA clock domain. Unconnected ``req`` and ``ack``
inputs default to low. Channels without ``req_enable`` run whenever they are busy.

Descriptors
===========

A descriptor is five consecutive 32-bit words in memory (4-byte aligned):

.. list-table::
   :header-rows: 1
   :widths: 10 20 70

   * - Offset
     - Word
     - Description
   * - 0x00
     - config
     - Same layout as the channel ``config`` register.
   * - 0x04
     - src
     - Source address.
   * - 0x08
     - dst
     - Destination address.
   * - 0x0C
     - length
     - Transfer length in bytes.
   * - 0x10
     - next
     - Address of the next descriptor, 0 terminates the chain.

When a descriptor completes and its ``linked`` bit is set with ``next != 0``, the engine
loads the descriptor at ``next`` into the channel registers and continues. Starting a channel
with ``length = 0``, ``linked = 1`` and ``next`` pointing at the first descriptor runs a
complete chain from memory; the channel registers always mirror the descriptor currently
in flight. A ring is formed by pointing the last descriptor at the first; use ``abort`` to
leave it.

Cache Coherency
===============

The controller accesses memory directly and does not participate in CPU cache coherency.
On platforms with a data cache, software must either place DMA buffers in an uncached
region (e.g. an uncached alias of the memory) or use the ``Zicbom`` cache management
instructions: ``cbo.clean`` the source buffer before starting a memory-to-peripheral
transfer and ``cbo.inval`` the destination buffer after a peripheral-to-memory transfer
completes. Descriptors living in cacheable memory must be cleaned before the channel is
started.

Protocol
********

1. Write ``config``, ``src``, ``dst``, ``length`` and optionally ``next``.
2. Enable the desired sources in ``irq_mask``.
3. Write ``1`` to ``control`` to start. The channel reports ``busy`` until the descriptor
   (or chain) completes, an error occurs or the channel is aborted.
4. On completion the ``done`` pending bit is set when ``irq_done`` was set in the finishing
   descriptor; on failure the ``error`` pending bit and the channel ``error`` flag are set.
   Write ``1`` to the pending bit to clear it.
5. Write ``2`` to ``control`` to abort a running channel. The channel goes idle after the
   current chunk without raising an interrupt.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all register buses are defined with 12 bit address and 32 bit data width. The
memory port is always TileLink (``BusParameter.simple(addressWidth, 32, burstBytes,
sourceWidth)``).

Parameter
=========

.. list-table:: DmaCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - channels
     - Int
     - Number of channels. Must be between 1 and 8.
     - 2
   * - requestLines
     - Int
     - Number of peripheral request inputs. Must be between 0 and 16.
     - 4
   * - burstBytes
     - Int
     - Maximum transfer size on the memory port and FIFO depth in bytes. Power of two
       between 4 and 4096.
     - 64
   * - addressWidth
     - Int
     - Memory port address width. Must be between 12 and 32.
     - 32
   * - sourceWidth
     - Int
     - Memory port TileLink source width. Must be between 1 and 8.
     - 1

.. code-block:: scala

   object Parameter {
     def default() = Parameter()
     def small()   = Parameter(channels = 1, requestLines = 4, burstBytes = 16)
     def medium()  = Parameter(channels = 2, requestLines = 8, burstBytes = 64)
     def large()   = Parameter(channels = 8, requestLines = 16, burstBytes = 256)
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x1A
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

**Global Registers:**

.. flat-table:: Global Registers
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
     - -
     - 0
     - Rx
     - Reserved.
   * - 23 - 16
     - burstLog2
     -
     - Rx
     - log2 of ``burstBytes``.
   * - 15 - 8
     - requestLines
     -
     - Rx
     - Number of request inputs.
   * - 7 - 0
     - channels
     -
     - Rx
     - Number of channels.
   * - 0x00C
     - 2 x channels - 1 downto 0
     - irq_pending
     - 0
     - RW
     - Pending interrupts, bit ``2 x ch`` = done, bit ``2 x ch + 1`` = error. Write 1 to
       clear.
   * - 0x010
     - 2 x channels - 1 downto 0
     - irq_mask
     - 0
     - RW
     - Interrupt enable per pending bit. The ``interrupt`` output is the OR of all masked
       pending bits.
   * - 0x014
     - channels - 1 downto 0
     - status
     - 0
     - Rx
     - Busy flag per channel.

**Per-Channel Registers:**

One set per channel. Channel 0 starts at 0x018; stride between channels is 0x20.

.. flat-table:: Per-Channel Registers (base = 0x018 + channel x 0x20)
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`2` base + 0x00
     - 2
     - request_active
     - 0
     - Rx
     - Level of the selected ``req``.
   * - 1
     - error / abort
     - 0
     - RW
     - Read: channel stopped with an error (bus error or misaligned element access);
       cleared by the next start. Write 1: abort the channel.
   * - 0
     - busy / start
     - 0
     - RW
     - Read: channel is running. Write 1: start the channel with the current registers.
       Ignored while busy.
   * - :rspan:`8` base + 0x04
     - 31 - 20
     - -
     - 0
     - RW
     - Reserved.
   * - 19 - 16
     - burst_limit
     - 0
     - RW
     - log2 of the largest burst in burst mode; 0 selects ``burstBytes``.
   * - 15 - 11
     - -
     - 0
     - RW
     - Reserved.
   * - 10
     - irq_done
     - 0
     - RW
     - Raise the done interrupt when this descriptor completes.
   * - 9
     - linked
     - 0
     - RW
     - Fetch the descriptor at ``next`` after completion (when ``next != 0``).
   * - 8 - 5
     - req_sel
     - 0
     - RW
     - Index of the request line gating this channel.
   * - 4
     - req_enable
     - 0
     - RW
     - Gate transfers by the selected request line (element mode).
   * - 3 - 2
     - width
     - 0
     - RW
     - Element size in element mode: 0 = 8 bit, 1 = 16 bit, 2 = 32 bit, 3 = invalid.
   * - 1
     - dst_inc
     - 0
     - RW
     - Increment the destination address after each chunk.
   * - 0
     - src_inc
     - 0
     - RW
     - Increment the source address after each chunk.
   * - base + 0x08
     - addressWidth - 1 downto 0
     - src
     - 0
     - RW
     - Source address. Advances during the transfer; read-only while busy.
   * - base + 0x0C
     - addressWidth - 1 downto 0
     - dst
     - 0
     - RW
     - Destination address. Advances during the transfer; read-only while busy.
   * - base + 0x10
     - 31 - 0
     - length
     - 0
     - RW
     - Remaining bytes. Counts down to 0; read-only while busy.
   * - base + 0x14
     - addressWidth - 1 downto 0
     - next
     - 0
     - RW
     - Address of the next descriptor, 0 terminates the chain. Read-only while busy.
