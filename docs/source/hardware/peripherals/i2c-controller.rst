.. _hardware-peripherals-i2c-controller:

I2C Controller
##############

This IP core is a highly configurable module designed for efficient communication with I2C devices. This controller integrates advanced features to manage data transmission, handle device interactions, and respond to various operational conditions.

Features
********

* **Internal Clock Divider:** Allows adjustment of the transmission speed to accommodate different I2C devices.
* **Interrupt Handling:**

  * Receives interrupts from connected I2C devices.
  * Generates interrupts for key events:

    * Data pushed into the response FIFO.
    * Command FIFO entries falling below a configurable threshold.

* **Command FIFO:** Enables efficient queuing of transmission commands for the controller.
* **Response FIFO:** Stores responses received from I2C devices for further processing.

Architecture
************

The I2C Controller IP core consists of the following primary components:

* Clock Divider Module:

  * Configures the speed of the I2C communication bus.
  * Provides compatibility with a variety of I2C devices operating at different frequencies.

* FIFO Buffers:

  * Command FIFO: A queue to manage outgoing transmission commands efficiently.
  * Response FIFO: A buffer to store incoming data or responses from I2C devices for processing.

* Interrupt Controller:

  * Handles device-originated interrupts.
  * Manages controller-generated interrupts based on events like FIFO thresholds or incoming responses.

Function Description
********************

Internal Clock Divider
~~~~~~~~~~~~~~~~~~~~~~

The internal clock divider provides flexibility to set the I2C clock frequency. This feature ensures compatibility with a wide range of devices operating at different speeds.

Interrupt Mechanism
~~~~~~~~~~~~~~~~~~~
The controller supports two types of interrupt sources:

1. **Device Interrupts**: Signals from connected devices indicating specific events.
2. **Controller-Generated Interrupts**:

   - **Response FIFO Interrupt**: Triggered when new data is pushed into the response FIFO.
   - **Command FIFO Threshold Interrupt**: Activated when the number of entries in the command FIFO falls below a pre-set threshold.

FIFOs
~~~~~
- **Command FIFO**:

  - Accepts commands from the host system.
  - Commands are translated into I2C transmissions.
- **Response FIFO**:

  - Captures and stores responses from I2C devices.
  - Ensures data integrity and simplifies response management.

Configuration Parameters
-------------------------
The IP core includes several configurable parameters to optimize its behavior for specific applications:
- **Clock Divider Width**: Defines the width of the clock divider register.
- **FIFO Depths**: Customizable depths for both Command and Response FIFOs.

Runtime Parameters
------------------
- **Clock Divider Value**: Sets the I2C transmission speed.
- **Command FIFO Threshold**: Specifies the entry count below which an interrupt is generated.


Configuration
*************

Available bus architectures:

- APB3
- Wishbone
- AvalonMM

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

`I2c.Parameter` defines the IO pins of the I2C controller.

.. list-table:: I2c.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - interrupts
     - Int
     - Number of interrupt pins from I2C devices.
     -

`I2cControllerCtrl.InitParameter` defines the initialization values for certian registers.

.. list-table:: I2cControllerCtrl.InitParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - clockDivider
     - Int
     - Default clockDivider value between system and I2C clock
     - 0

.. note::
   Parameter in InitParameter with a value "0" are equal to disabled. This allows to
   only set certain parameters.

`I2cControllerCtrl.PermissionParameter` defines the permission rules for bus access.

.. list-table:: I2cControllerCtrl.PermissionParameter
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

`I2cControllerCtrl.MemoryMappedParameter` defines the FIFO width.

.. list-table:: I2cControllerCtrl.MemoryMappedParameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - cmdFifoDepth
     - Int
     - FIFO depth for commands to the controller.
     -
   * - rspFifoDepth
     - Int
     - FIFO depth for responses out of the controller.
     -


`UartCtrl.Parameter` configures the UART controller.

.. list-table:: UartCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - io
     - I2c.Parameter
     - Class with IO parameters
     -
   * - init
     - I2cControllerCtrl.InitParameter
     - Class to parametrize the initialization values.
     - InitParameter.disabled
   * - permission
     - I2cControllerCtrl.PermissionParameter
     - Class to set bus access.
     - PermissionParameter.granted
   * - memory
     - I2cControllerCtrl.MemoryMappedParameter
     - Class to define FIFO depth.
     - MemoryMappedParameter.default
   * - clockDividerWidth
     - Int
     - Width of the clock divider counter.
     - 16

`I2cControllerCtrl.Parameter` has some functions with pre-predefined parameters for common use-cases.

.. code-block:: scala

   object Parameter {
     def lightweight(interrupts: Int = 0) = Parameter(
       io = I2c.Parameter(interrupts),
       memory = MemoryMappedParameter.lightweight
     )
     def default(interrupts: Int = 0) = Parameter(
       io = I2c.Parameter(interrupts)
     )
     def full(interrupts: Int = 0) = Parameter(
       io = I2c.Parameter(interrupts),
       memory = MemoryMappedParameter.full
     )
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x4
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
   * - 0x008
     - 7 - 0
     - clockDividerWidth
     -
     - Rx
     - Width of the clock divider counter.
   * - :rspan:`1` 0x00C
     - 15 - 8
     - cmdFifoDepth
     -
     - Rx
     - Depth of the command FIFO.
   * - 7 - 0
     - rspFifoDepth
     -
     - Rx
     - Depth of the response FIFO.
   * - 0x010
     - 1
     - busCanWriteClockDividerConfig
     -
     - Rx
     - Flag whether the clock divider is writable.

**Command and Response FIFO:**

.. flat-table:: Command and Response FIFO
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x014
     - 31
     - validBit
     -
     - Rx
     - This bit contains a 1 when the response FIFO returned valid payload.
   * - 15 - 0
     - responseFifo
     -
     - Rx
     - Payload from the response FIFO.
   * - 0x018
     - 31 - 0
     - commandFifo
     -
     - xW
     - Sends data to the controller which will be transmitted to an external component.
   * - :rspan:`2` 0x018
     - 31 - 16
     - vacany
     -
     - Rx
     - Number of occupied slots in the command FIFO.
   * - 15 - 0
     - occupancy
     -
     - Rx
     - Number of empty slots in the response FIFO.

**Clock Divider:**

.. flat-table:: Clock Divider
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
     - Depends on `I2cControllerCtrl.InitParameter`
     - RW or Rx
     - Value for the clock divider counter to divide down the input clock.

**Interrupt:**

.. flat-table:: Interrupt
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x020
     - 31 - 0
     - Command occupancy trigger
     -
     - RW
     - An command FIFO occupancy threshold which will trigger an interupt when the FIFO has the
       same amount of remaining entries.
   * - 0x024
     - (parameter.io.interrupts + 2) - 0
     - Interrupt pending
     -
     - RW
     - Returns pending interrupts for each interrupt source. Clears interrupts during write.
   * - 0x030
     - (parameter.io.interrupts + 2) - 0
     - Interrupt mask
     -
     - RW
     - Interrupt mask to enable interrupts.
