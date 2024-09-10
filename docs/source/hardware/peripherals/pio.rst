.. _hardware-peripherals-pio:

Programmable IO
###############

The Programmable IO IP Core is a versatile and highly configurable module designed for generating and controlling low-speed digital interfaces. It features an internal state machine that can be programmed to drive the IO signal in various sequences. This allows the IP core to support a wide range of protocols and custom interfaces by bit-banging the signal according to the programmed commands.

State Machine
*************

The core of the Programmable IO IP Core is its internal state machine, which operates in four distinct states:

1. **Signal High (HIGH)**

   * In this state, the IO signal is set to a high logic level (1).
   * This state is used when the protocol requires a high signal on the IO line.
   * Command value: 0x0

2. **Signal Low (LOW)**

   * In this state, the IO signal is set to a low logic level (0).
   * This state is used when the protocol requires a low signal on the IO line.
   * Command value: 0x1

3. **Wait (WAIT)**

   * The WAIT state introduces a delay in the signal transition.
   * It is useful for timing control, ensuring that the signal remains in its current state for a specified period.
   * Command value: 0x2

4. **Read (READ)**

   * In the READ state, the core samples the IO signal and returns its value.
   * This state is essential for protocols that require reading data from the IO line.
   * Command value: 0x3

Command Interface
*****************

The state machine transitions between these states based on commands received via a control interface. Each command specifies the next state and any necessary parameters, such as the duration of the WAIT state or the number of pins to read in the READ state.

**Example Commands:**

* **SET_HIGH:** Transition to the HIGH state.
* **SET_LOW:** Transition to the LOW state.
* **SET_WAIT(n):** Enter the WAIT state for 'n' clock cycles.
* **READ:** Transition to the READ state and capture the current IO signal.

Usage
*****

To utilize the Programmable IO IP Core for bit-banging various low-speed interfaces, users must configure the state machine with a sequence of commands that reflect the desired protocol's timing and signal transitions. The commands can be sent in real-time, allowing dynamic reconfiguration based on operational needs.

**Example Sequence:**

1. **SET_LOW:** Start with the signal low.
2. **SET_WAIT(10):** Wait for 10 clock cycles.
3. **SET_HIGH:** Set the signal high.
4. **SET_WAIT(5):** Wait for 5 clock cycles.
5. **READ:** Read the signal to capture the input value.

This sequence could be part of a protocol where the signal is pulled low, held for a period, then raised, and a value is read from the line.

Applications
************

The flexibility of the Programmable IO IP Core makes it suitable for a variety of applications, including but not limited to:

* Custom serial communication protocols
* GPIO signal manipulation
* Interface with legacy hardware with specific timing requirements
* Prototyping new digital communication standards

Configuration
*************

Available bus architectures:

- APB3
- Wishbone
- AvalonMM

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

`PioCtrl.InitParameter` defines the initialization values for certian registers.

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

   Parameter in InitParameter with a value "0" are equal to disabled. This allows to
   only set certain parameters.

`PioCtrl.MemoryMappedParameter` defines the FIFO width.

.. list-table:: PioCtrl.MemoryMappedParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - commandFifoDepth
     - Int
     - FIFO depth for incoming commands.
     - 16
   * - readFifoDepth
     - Int
     - FIFO depth for outgoing read results.
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
     - Toggles bus access to the clock divider.
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
     - Class to define FIFO depth.
     - MemoryMappedParameter.default
   * - dataWidth
     - Int
     - Width of the data field used in the command FIFO to pass additional information to the
       controller.
     - 24
   * - clockDividerWidth
     - Int
     - Width of the clock divider counter.
     - 20
   * - readDelayWidth
     - Int
     - Width of the read delay counter.
     - 8

`PioCtrl.Parameter` has some functions with pre-predefined parameters for common use-cases.

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
.. |ip-identification-minor-version| replace:: 0x0
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
     - Width of the data field used in the command FIFO to pass additional information to the
       controller.
   * - 7 - 0
     - IO width
     -
     - Rx
     - Number of IO pins.
   * - :rspan:`2` 0x00C
     - 15 - 8
     - readFifoDepth
     -
     - Rx
     - Depth of the read FIFO.
   * - 7 - 0
     - commandFifoDepth
     -
     - Rx
     - Depth of the command FIFO.
   * - 0x010
     - 0
     - busCanWriteClockDividerConfig
     -
     - Rx
     - Flag whether the clock divider is writable.

**Command and Read FIFO:**

.. flat-table:: Command and Read FIFO
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x014
     - 16
     - validBit
     -
     - Rx
     - This bit contains a 1 when the result FIFO returned valid payload.
   * - 15 - 0
     - resultFifo
     -
     - Rx
     - Payload from the result FIFO.
   * - 0x014
     - 31 - 0
     - commandFifo
     -
     - xW
     - Sends a command to the controller with the following payload:
         * Lower two bits define the command.
         * Decimal number of the pin.
         * Additional data.
   * - :rspan:`2` 0x018
     - 31 - 24
     - occupancy
     -
     - Rx
     - Number of occupied slots in the result FIFO.
   * - 23 - 16
     - vacancy
     -
     - Rx
     - Number of empty slots in the command FIFO.

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
   * - 0x01C
     - clockDividerWidth - 0
     - clockDividerValue
     - Depends on `PioCtrl.InitParameter`
     - RW or Rx
     - Value for the clock divider counter to divide down the input clock.
   * - 0x020
     - readDelayWidth - 0
     - ReadDelayValue
     - Depends on `PioCtrl.InitParameter`
     - RW or Rx
     - Number of cycles to delay during the read action.
