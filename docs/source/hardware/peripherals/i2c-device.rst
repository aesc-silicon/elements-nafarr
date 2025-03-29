.. _hardware-peripherals-i2c-device:

I2C Device
##########

The I2C (Inter-Integrated Circuit) Device IP core is a flexible interface designed to handle and process I2C transmissions. It features an internal clock divider, automatic timeout detection for stalled transmissions, and a configurable sampling bit count.

This IP core does not include a bus interface or register mapping; instead, it relies on a backend tailored to the specific application. Communication between the core and the backend is facilitated through command and response data streams.

Data Streams
*************

**1. Command Stream**

The command stream transmits information from incoming I2C transmissions to the backend, providing all relevant details:

* **Data:** The 8-bit data received from the controller.
* **Reg:** A flag indicating whether the data represents a register address.
* **Read:** A flag specifying if the transmission is a read or write operation.

**2. Response**

The response stream returns data from the backend to be transmitted during the I2C SCL cycles:

* **Data:** The 8-bit data returned from the backend, relevant only for read operations.
* **Error:** A flag indicating an error if the transmission could not be processed correctly.

Configuration
*************

The I2C Device IP core lacks a built-in bus interface, as it relies on a custom backend to manage incoming transmissions.

Parameter
=========

`I2c.Parameter` defines the IO pins of the I2C Device controller.

.. list-table:: I2c.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - Interrupts
     - Int
     - Number of interrupt pins from the device to controller.
     - 0

`I2cDeviceCtrl.Parameter` configures the I2C Device controller.

.. list-table:: I2cDeviceCtrl.Parameter
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
   * - clockDividerWidth
     - Int
     - Width of the clock divider counter. Must be greater then 0.
     - 16
   * - timeoutWidth
     - Int
     - Timeout counter width to calculate a bus timeout. Must be greater then 0.
     - 16
   * - samplerWidth
     - Int
     - Number of sample bits. Must be greated then 2.
     - 3
   * - addressWidth
     - Int
     - I2C address width. Currently only 7 bit are supported.
     - 7

`I2cDeviceCtrl.Parameter` has some functions with pre-predefined parameters for common use-cases.

.. code-block:: scala

   object Parameter {
     def default(interrupts: Int = 0) = Parameter(io = I2c.Parameter(interrupts))
   }
