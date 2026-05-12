.. _hardware-peripherals-pio:

Programmable IO
###############

The Programmable IO (PIO) IP Core is a versatile and highly configurable module designed for generating and controlling low-speed digital interfaces. It features an internal state machine that executes a user-defined program stored in on-chip memory to drive IO signals in precise sequences. This allows the IP core to support a wide range of protocols and custom interfaces by bit-banging signals according to the programmed commands, without requiring continuous CPU intervention.

Program Memory
**************

Unlike a streaming FIFO approach, the PIO stores its program in an internal memory. Software writes instructions sequentially into the program memory via the bus interface. A write pointer tracks the next free slot, and an execution pointer tracks the currently executing instruction. Execution begins when the enable flag is set, which also resets both the execution pointer and the loop counter to zero.

This separation of loading and execution means software can fill the entire program buffer before starting the state machine, removing any real-time loading pressure.

To replace a running program, disable the controller, issue a program reset (which resets the write pointer), write the new instructions, then re-enable.

State Machine
*************

The state machine reads instructions from program memory and executes them in sequence. The following commands are supported:

**Single-pin commands** use the ``pin`` field to select the target IO pin:

1. **Signal High (HIGH)**

   * Sets the selected pin to a high logic level (output, driven high).
   * Command value: 0x0

2. **Signal Low (LOW)**

   * Sets the selected pin to a low logic level (output, driven low).
   * Command value: 0x2

3. **Float (FLOAT)**

   * Sets the selected pin to a high-impedance (input) state.
   * Command value: 0x4

4. **Toggle (TOGGLE)**

   * Toggles the direction of the selected pin (output ↔ input).
   * Command value: 0x6

5. **Wait (WAIT)**

   * Holds execution for a number of clock-divider ticks specified in the ``data`` field.
   * Command value: 0x8

6. **Wait for High (WAIT_FOR_HIGH)**

   * Stalls execution until the selected pin reads a high level.
   * Command value: 0x9

7. **Wait for Low (WAIT_FOR_LOW)**

   * Stalls execution until the selected pin reads a low level.
   * Command value: 0xA

8. **Read (READ)**

   * Sets the selected pin to input mode, waits for ``readDelayValue`` clock-divider ticks
     (configured globally via the read delay register) to allow the line to settle, then samples
     the pin and pushes the result into the read FIFO.
   * Stalls until the read FIFO accepts the result (backpressure).
   * The ``data`` field of a READ instruction is ignored.
   * Command value: 0xB

**Multi-pin commands** use the ``data`` field as a bitmask — each bit corresponds to one IO pin:

9. **Signal High Set (HIGH_SET)**

   * Sets all pins indicated by the ``data`` bitmask to output high.
   * Command value: 0x1

10. **Signal Low Set (LOW_SET)**

    * Sets all pins indicated by the ``data`` bitmask to output low.
    * Command value: 0x3

11. **Float Set (FLOAT_SET)**

    * Sets all pins indicated by the ``data`` bitmask to high-impedance (input).
    * Command value: 0x5

12. **Toggle Set (TOGGLE_SET)**

    * Toggles the direction of all pins indicated by the ``data`` bitmask.
    * Command value: 0x7

**Program flow commands:**

13. **Loop (LOOP)**

    * Controls program repetition.
    * ``data = 0``: jumps back to instruction 0 indefinitely (endless loop).
    * ``data = N``: runs the program N times in total, then advances past the LOOP instruction
      and asserts the ``loopDone`` interrupt.
    * If the ``stopAtLoop`` control bit is set when the LOOP instruction is reached, the state
      machine always advances past it regardless of ``data``, allowing software to gracefully
      terminate an endless loop. See the ``stopAtLoop`` register field.
    * Command value: 0xC

Command Encoding
****************

Each instruction is a 32-bit word with the following layout:

.. code-block:: text

   Bit  3 - 0 : command   (4-bit enum, see command values above)
   Bit  P - 4 : pin       (log2Up(io.width) bits, 0 for multi-pin commands)
   Bit 31 - Q : data      (dataWidth bits, bitmask for _SET commands, count for WAIT/READ/LOOP)

   where P = 3 + log2Up(io.width), Q = P + 1

Usage
*****

**Example: Blink pin 0 forever**

.. code-block:: c

   // 1. Stop and reset the program memory
   WRITE32(PIO_ENABLE, 0);
   WRITE32(PIO_STATUS, 1);          // any write triggers programReset

   // 2. Set clock divider
   WRITE32(PIO_CLK_DIV, 99);        // tick every 100 cycles

   // 3. Load program
   WRITE32(PIO_TX, CMD(HIGH, pin=0, data=0));
   WRITE32(PIO_TX, CMD(WAIT, pin=0, data=500));
   WRITE32(PIO_TX, CMD(LOW,  pin=0, data=0));
   WRITE32(PIO_TX, CMD(WAIT, pin=0, data=500));
   WRITE32(PIO_TX, CMD(LOOP, pin=0, data=0)); // endless

   // 4. Enable IRQ mask and start
   WRITE32(PIO_IRQ_MASK, 0x2);      // unmask loopDone (optional for endless)
   WRITE32(PIO_ENABLE, 1);          // rising edge resets execPtr and loopCounter

**Example: Run 3 times, receive loopDone interrupt, reload**

.. code-block:: c

   WRITE32(PIO_ENABLE, 0);
   WRITE32(PIO_STATUS, 1);

   WRITE32(PIO_TX, CMD(HIGH, 0, 0));
   WRITE32(PIO_TX, CMD(WAIT, 0, 100));
   WRITE32(PIO_TX, CMD(LOW,  0, 0));
   WRITE32(PIO_TX, CMD(WAIT, 0, 100));
   WRITE32(PIO_TX, CMD(LOOP, 0, 3)); // run 3 times, then loopDone

   WRITE32(PIO_IRQ_MASK, 0x2);
   WRITE32(PIO_ENABLE, 1);

   // In the ISR when loopDone fires:
   WRITE32(PIO_ENABLE, 0);
   WRITE32(PIO_STATUS, 1);          // reset write pointer
   // ... write new program ...
   WRITE32(PIO_ENABLE, 1);          // re-arm execPtr via rising edge

**Example: gracefully stop an endless loop and reload**

.. code-block:: c

   // Program is running an endless LOOP — stopping with stopAtLoop:
   //   1. Set stopAtLoop (bit 1). The state machine completes the current
   //      iteration and advances past the LOOP instruction on the next pass.
   WRITE32(PIO_ENABLE, READ32(PIO_ENABLE) | 0x2);

   //   2. Poll until execPtr == writePtr (state machine has idled).
   while ((READ32(PIO_STATUS) & 0xFF) != ((READ32(PIO_STATUS) >> 8) & 0xFF));

   //   3. Clear stopAtLoop, then reload.
   WRITE32(PIO_ENABLE, READ32(PIO_ENABLE) & ~0x2);
   WRITE32(PIO_ENABLE, 0);          // disable
   WRITE32(PIO_STATUS, 1);          // reset write pointer
   // ... write new program ...
   WRITE32(PIO_ENABLE, 1);          // re-arm and start

Applications
************

The flexibility of the Programmable IO IP Core makes it suitable for a variety of applications, including but not limited to:

* Custom serial communication protocols
* Precise GPIO signal sequencing without CPU polling
* Interface with legacy hardware with specific timing requirements
* Prototyping new digital communication standards

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

`Pio.Parameter` defines the IO pins of the programmable IO controller.

.. list-table:: Pio.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - Width
     - Int
     - Number of IO pins. Must be greater then 0.
     -

`PioCtrl.InitParameter` defines the initialization values for certain registers.

.. list-table:: PioCtrl.InitParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - clockDivider
     - Int
     - Initialization value of the internal clock divider
     - 0
   * - readDelay
     - Int
     - Initialization value of the register which delays read actions
     - 0

.. note::

   Parameters in InitParameter with a value of ``0`` are treated as disabled. This allows
   selectively initializing only certain registers.

`PioCtrl.MemoryMappedParameter` defines the program memory and read FIFO depths.

.. list-table:: PioCtrl.MemoryMappedParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - commandFifoDepth
     - Int
     - Depth of the program memory (maximum number of instructions).
     - 16
   * - readFifoDepth
     - Int
     - Depth of the read result FIFO.
     - 8

`PioCtrl.PermissionParameter` defines the permission rules for bus access.

.. list-table:: PioCtrl.PermissionParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - busCanWriteClockDividerConfig
     - Boolean
     - Toggles bus write access to the clock divider.
     -

`PioCtrl.Parameter` configures the programmable IO controller. It uses `Pio.Parameter` as parameter.

.. list-table:: PioCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - io
     - Pio.Parameter
     - Class with IO parameters
     -
   * - readBufferDepth
     - Int
     - Number of registers on the input path to stabilize read values. Disabled when 0.
     - 2
   * - init
     - PioCtrl.InitParameter
     - Class to parametrize the initialization values.
     - InitParameter.disabled
   * - permission
     - PioCtrl.PermissionParameter
     - Class to set bus access.
     - PermissionParameter.granted
   * - memory
     - PioCtrl.MemoryMappedParameter
     - Class to define program memory and read FIFO depth.
     - MemoryMappedParameter.default
   * - dataWidth
     - Int
     - Width of the data field in each instruction word.
     - 24
   * - clockDividerWidth
     - Int
     - Width of the clock divider counter.
     - 20
   * - readDelayWidth
     - Int
     - Width of the read delay counter.
     - 8
   * - interrupt
     - Boolean
     - Enables the interrupt controller with loopDone and RX data sources.
     - true
   * - error
     - Boolean
     - Enables the error controller with a read FIFO overflow source.
     - true

`PioCtrl.Parameter` has some functions with pre-defined parameters for common use-cases.

.. code-block:: scala

   object Parameter {
     def default(pins: Int = 1) =
       Parameter(Pio.Parameter(pins))
     def light(pins: Int = 1) =
       Parameter(Pio.Parameter(pins), memory = MemoryMappedParameter.lightweight, dataWidth = 16)
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x1
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x1
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

**Self Disclosure:**

This block discloses information about the IP core to software drivers to simplify them.

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
     - readBufferDepth
     -
     - Rx
     - Number of registers on the input path to stabilize read values.
   * - 23 - 16
     - clockDividerWidth
     -
     - Rx
     - Width of the clock divider counter.
   * - 15 - 8
     - dataWidth
     -
     - Rx
     - Width of the data field in each instruction word.
   * - 7 - 0
     - IO width
     -
     - Rx
     - Number of IO pins.
   * - :rspan:`1` 0x00C
     - 15 - 8
     - readFifoDepth
     -
     - Rx
     - Depth of the read result FIFO.
   * - 7 - 0
     - commandFifoDepth
     -
     - Rx
     - Depth of the program memory (maximum number of instructions).
   * - 0x010
     - 0
     - busCanWriteClockDividerConfig
     -
     - Rx
     - Flag whether the clock divider register is bus-writable.

**Control:**

.. flat-table:: Control Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x014
     - 1
     - stopAtLoop
     - 0
     - RW
     - When set, the next LOOP instruction encountered advances the execution pointer past the
       LOOP instruction instead of jumping back, regardless of the loop count. Once the execution
       pointer reaches the write pointer the state machine idles. Clear this bit after the
       program has stopped. Used by software to gracefully terminate an endless loop.
   * - 0
     - enable
     - 0
     - RW
     - Enables program execution. A low-to-high transition resets the execution pointer and
       loop counter to zero.

**Program Memory and Read FIFO:**

.. flat-table:: Program Memory and Read FIFO Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x018
     - 16
     - valid
     -
     - Rx
     - Set when the read FIFO returned a valid result. Must be checked before consuming the payload.
   * - 15 - 0
     - readResult
     -
     - Rx
     - Result sampled by the last READ command, consumed from the read FIFO on each read.
   * - 0x018
     - 31 - 0
     - programWrite
     -
     - xW
     - Writes one instruction word into program memory at the current write pointer position
       and advances the write pointer.
   * - :rspan:`2` 0x01C
     - 31 - 24
     - rxOccupancy
     -
     - Rx
     - Number of unread results currently in the read FIFO.
   * - 15 - 8
     - writePtr
     -
     - Rx
     - Current program write pointer (next free instruction slot).
   * - 7 - 0
     - execPtr
     -
     - Rx
     - Current execution pointer (instruction being executed).
   * - 0x01C
     - 31 - 0
     - programReset
     -
     - xW
     - Any write resets the write pointer to zero, allowing the program memory to be overwritten.

**Clock Divider and Read Delay:**

.. flat-table:: Clock Divider and Read Delay
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x020
     - clockDividerWidth - 0
     - clockDividerValue
     - Depends on `PioCtrl.InitParameter`
     - RW or Rx
     - Value for the clock divider counter. The divider produces one tick every ``value + 1``
       input clock cycles. Write access depends on ``busCanWriteClockDividerConfig``.
   * - 0x024
     - readDelayWidth - 0
     - readDelayValue
     - Depends on `PioCtrl.InitParameter`
     - RW
     - Number of clock-divider ticks to wait after switching a pin to input mode before sampling
       it during a READ command. Applies globally to all READ instructions. Set to 0 to sample
       immediately.

**Error Controller:**

Present when ``PioCtrl.Parameter.error = true``. Follows the standard interrupt controller
pattern: writing a ``1`` to a pending bit clears it; the mask register enables individual
error sources.

.. flat-table:: Error Controller Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x028
     - 0
     - readFifoOverflow pending
     - 0
     - RW
     - Sticky flag set when a READ result is dropped because the read FIFO is full.
       Write ``1`` to clear.
   * - 0x02C
     - 0
     - readFifoOverflow mask
     - 0
     - RW
     - Enables the read FIFO overflow error source.

**Interrupt Controller:**

Present when ``PioCtrl.Parameter.interrupt = true``. Same pattern as the error controller.

.. flat-table:: Interrupt Controller Registers
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x030
     - 1
     - loopDone pending
     - 0
     - RW
     - Sticky flag set when a LOOP N command finishes all N iterations. Write ``1`` to clear.
   * - 0
     - rxData pending
     - 0
     - RW
     - Sticky flag set when the read FIFO contains at least one result. Write ``1`` to clear.
   * - :rspan:`1` 0x034
     - 1
     - loopDone mask
     - 0
     - RW
     - Enables the loopDone interrupt source.
   * - 0
     - rxData mask
     - 0
     - RW
     - Enables the RX data available interrupt source.
