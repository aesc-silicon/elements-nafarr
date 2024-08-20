.. _hardware-peripherals-gpio:

GPIO
####

A GPIO (General-Purpose Input/Output) controller with tri-state pins is a versatile digital interface that allows each pin to be configured as either an input or an output, with additional control for setting the direction of data flow. Here's a breakdown of its functionality:

1. **Input Mode:** When a pin is configured as an input, the controller reads the voltage level on the pin (either high or low) to determine the digital signal value. The pin is typically in a high-impedance state, meaning it does not drive any current and instead listens to the signal from external devices or circuits.

2. **Output Mode:** When configured as an output, the pin can drive a voltage level (high or low) to external circuits. This allows the GPIO to control other devices, such as LEDs, motors, or communication lines.

3. **Direction Control:** The direction of each pin (input or output) is controlled by a separate direction register or signal. This determines whether the pin acts as an input or an output. In some designs, a pin can dynamically switch between input and output modes based on the direction control, enabling more complex interactions.

4. **Tri-State Functionality on Output Pins:** The output pins implement tri-state functionality, meaning they can be placed in a high-impedance state (also known as "floating" or "Z" state) when not actively driving a signal. This allows the pin to effectively disconnect from the circuit, enabling other devices to control the line or preventing signal contention on shared data lines.

This combination of input, output, and tri-state control provides a flexible and powerful interface for interacting with various external components in embedded and digital systems.

The input path of the GPIO controller is synchronized using a configurable number of flip-flops to ensure signal metastability. By passing the input signal through a chain of flip-flops, the design reduces the likelihood of metastable states, which can occur when a signal changes close to the clock edge. The number of flip-flops used in this synchronization process is parametrizable, allowing the design to be tailored to specific timing and reliability requirements. This ensures that the input signal is stable and reliable before being processed further in the system.

Each input pin of the GPIO controller is connected to an interrupt controller, which provides advanced monitoring capabilities. This interrupt controller can be configured to detect various signal conditions on each input pin in parallel:

- **Low Level Detection:** Triggers an interrupt when the input pin remains at a low voltage level.
- **High Level Detection:** Triggers an interrupt when the input pin remains at a high voltage level.
- **Rising Edge Detection:** Triggers an interrupt when the input pin transitions from a low to a high voltage level.
- **Falling Edge Detection:** Triggers an interrupt when the input pin transitions from a high to a low voltage level.

These configurable detection modes allow the GPIO controller to respond to a wide range of events, enabling precise and flexible interrupt-driven designs. This makes the GPIO controller highly suitable for applications that require real-time monitoring and response to external signals.

Configuration
*************

Available bus architectures:

- APB3
- Wishbone
- AvalonMM

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

`Gpio.Parameter` defines the IO pins of the GPIO controller.

.. list-table:: Gpio.Parameter
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

`GpioCtrl.Parameter` configures the GPIO controller. It uses `Gpio.Parameter` as parameter.

.. list-table:: GpioCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - io
     - Gpio.Parameter
     - Class with IO parameters
     -
   * - readBufferDepth
     - Int
     - Number of registers on the input path to stabilize read values. Disabled when 0.
     - 0
   * - output
     - Seq[Int]
     - List of pin numbers which can drive an output signal. 'null' equals to all available pins.
     - null
   * - input
     - Seq[Int]
     - List of pin numbers which can read an input signal. 'null' equals to all available pins.
     - null
   * - interrupt
     - Seq[Int]
     - List of pin numbers which are interrupt capable. 'null' equals to all available pins.
     - null
   * - invertWriteEnable
     - Boolean
     - Inverts the tri-state write enable signal to active low, which is required by some IO pads.
     - false

`GpioCtrl.Parameter` has some functions with pre-predefined parameters for common use-cases.

.. code-block:: scala

   object Parameter {
     def default(width: Int = 32, invertWriteEnable: Boolean = false) =
       Parameter(Gpio.Parameter(width), 1, null, null, null, invertWriteEnable)
     def noInterrupt(width: Int = 32, invertWriteEnable: Boolean = false) =
       Parameter(Gpio.Parameter(width), 1, null, null, Seq[Int](), invertWriteEnable)
     def onlyOutput(width: Int = 32, invertWriteEnable: Boolean = false) =
       Parameter(Gpio.Parameter(width), 0, null, Seq[Int](), Seq[Int](), invertWriteEnable)
     def onlyInput(width: Int = 32) =
       Parameter(Gpio.Parameter(width), 0, Seq[Int](), null, null)
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x0
.. |ip-identification-major-version| replace:: 1
.. |ip-identification-minor-version| replace:: 0
.. |ip-identification-patch-version| replace:: 0

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
   * - :rspan:`1` 0x008
     - 31 - 16
     - GPIO banks
     -
     - Rx
     - Number of GPIO banks this controller has. Each bank can have up to 32 pins.
   * - 15 - 0
     - Length
     - Pins per bank
     - Rx
     - Total number of IO pins.

**IO Banks:**

The following registers are for each bank. First bank starts at `0x00C` and each other bank with an
offset of `0x2C`.

.. flat-table:: IO Bank Registers
   :widths: 10 10 20 10 50
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Permission
     - Description
   * - 0x00C
     - 31 - 0
     - Input
     - Rx
     - Input level value of each IO pin. Returns 0 on all unconnected bits.
   * - 0x010
     - 31 - 0
     - Output
     - RW
     - Output level of each IO pin when direction is set to out. Returns 0 on all unconnected bits.
   * - 0x014
     - 31 - 0
     - Direction
     - RW
     - Direction of each pin. High means output, low input. Returns 0 on all unconnected bits.
   * - 0x018
     - 31 - 0
     - Input high interrupt pending
     - RW
     - Returns pending interrupts for each pin during read. Clears interrupts during write.
   * - 0x01C
     - 31 - 0
     - Input high interrupt mask
     - RW
     - Interrupt mask to enable interrupts for IO pins.
   * - 0x020
     - 31 - 0
     - Input low interrupt pending
     - RW
     - Returns pending interrupts for each pin during read. Clears interrupts during write.
   * - 0x024
     - 31 - 0
     - Input low interrupt mask
     - RW
     - Interrupt mask to enable interrupts for IO pins.
   * - 0x028
     - 31 - 0
     - Input rising edge interrupt pending
     - RW
     - Returns pending interrupts for each pin during read. Clears interrupts during write.
   * - 0x02C
     - 31 - 0
     - Input rising edge interrupt mask
     - RW
     - Interrupt mask to enable interrupts for IO pins.
   * - 0x030
     - 31 - 0
     - Input falling edge interrupt pending
     - RW
     - Returns pending interrupts for each pin during read. Clears interrupts during write.
   * - 0x034
     - 31 - 0
     - Input falling edge interrupt mask
     - RW
     - Interrupt mask to enable interrupts for IO pins.
