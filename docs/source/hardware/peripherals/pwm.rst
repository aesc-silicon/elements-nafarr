.. _hardware-peripherals-pwm:

Pulse-Width Modulation
######################

The Pulse-Width Modulation (PWM) IO Core is a configurable module designed to generate PWM signals with precise control over the signal's period and duty cycle. The core uses two internal counters to define the characteristics of the output waveform: the Period Counter and the Pulse Width Counter. These counters allow the PWM core to produce a rectangular wave with a defined frequency and duty cycle, making it suitable for applications such as motor control, LED dimming, and signal modulation.

Internal Counters
*****************

1. **Period Counter**

   * The Period Counter defines the total length of one PWM cycle, which determines the frequency of the output wave.
   * It counts the number of clock cycles that make up one full period of the PWM signal.
   * The frequency of the PWM signal is inversely proportional to the value of the Period Counter.

2. **Pulse Width Counter**

   * The Pulse Width Counter determines the duration within the period when the PWM signal is high (logic level 1).
   * It controls the duty cycle of the PWM signal, which is the ratio of the pulse width to the total period.
   * A higher Pulse Width Counter value increases the time the signal remains high during each period, resulting in a higher duty cycle.

Operation
*********

During each PWM cycle, the core follows this sequence:

1. **Initialization:** Both counters are initialized at the start of each period. The Period Counter sets the overall length of the PWM cycle, and the Pulse Width Counter sets the duration for which the signal remains high.

2. **Signal Toggle:** The PWM signal is set high at the start of the period. When the Pulse Width Counter reaches its defined value, the signal toggles to low (logic level 0).

3. **Cycle Completion:** The signal remains low until the Period Counter completes the full cycle. At the end of the period, the counters reset, and a new PWM cycle begins.

Configuration Parameters
************************

* **Period:** Configurable to define the frequency of the PWM signal. A lower period value results in a higher frequency.

* **Pulse Width:** Configurable to define the duty cycle. The pulse width value is set to a fraction of the period, determining how long the signal stays high during each cycle.

* **Invert Output:** Change the output value and start the PWM cycle with low (logic level 0).

**Example Configuration:**

* **Period = 1000 clock cycles:** The PWM signal has a period of 1000 clock cycles, setting the overall frequency of the waveform.

* **Pulse Width = 250 clock cycles:** The PWM signal remains high for 250 clock cycles within each period, giving a duty cycle of 25%.

Application
***********

The PWM IO Core is suitable for various applications that require precise control of signal timing and duty cycles, including:

* **Motor Speed Control:** Adjusting the duty cycle to control the speed of DC motors.
* **LED Dimming:** Varying the duty cycle to control the brightness of LEDs.
* **Signal Generation:** Creating modulated signals for communication systems.

Configuration
*************

Available bus architectures:

- APB3
- Wishbone
- AvalonMM

By default, all buses are defined with 12 bit address and 32 bit data width.

Parameter
=========

`Pwm.Parameter` defines the amount of channels in the PWM controller.

.. list-table:: Pwm.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - Channels
     - Int
     - Number of channels. Must be greater then 0.
     -

`PwmCtrl.InitParameter` defines the initialization values for certian registers.

.. list-table:: PwmCtrl.InitParameter
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

.. note::

   Parameter in InitParameter with a value "0" are equal to disabled. This allows to
   only set certain parameters.

`PwmCtrl.PermissionParameter` defines the permission rules for bus access.

.. list-table:: PwmCtrl.PermissionParameter
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

`PwmCtrl.Parameter` configures the PWM controller. It uses `Pwm.Parameter` as parameter.

.. list-table:: PwmCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - io
     - Pwm.Parameter
     - Class with IO parameters
     -
   * - init
     - PwmCtrl.InitParameter
     - Class to parametrize the initialization values.
     - InitParameter.disabled
   * - permission
     - PwmCtrl.PermissionParameter
     - Class to set bus access.
     - PermissionParameter.granted
   * - clockDividerWidth
     - Int
     - Width of the clock divider counter.
     - 20
   * - channelPeriodWidth
     - Int
     - Width of the period counter.
     - 20
   * - channelPulseWidth
     - Int
     - Width of the pulse counter.
     - 20

`PwmCtrl.Parameter` has some functions with pre-predefined parameters for common use-cases.

.. code-block:: scala

   object Parameter {
     def default(channels: Int = 1) = Parameter(Pwm.Parameter(channels))
   }

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0x2
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
     - channelPeriodWidth
     -
     - Rx
     - Width of the period counter.
   * - 23 - 16
     - channelPulseWidth
     -
     - Rx
     - Width of the pulse counter.
   * - 15 - 8
     - clockDividerWidth
     -
     - Rx
     - Width of the clock divider counter.
   * - 7 - 0
     - IO channels
     -
     - Rx
     - Number of channels.
   * - 0x00C
     - 0
     - busCanWriteClockDividerConfig
     -
     - Rx
     - Flag whether the clock divider is writable.

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
   * - 0x010
     - clockDividerWidth - 0
     - clockDividerValue
     - Depends on `PwmCtrl.InitParameter`
     - RW or Rx
     - Value for the clock divider counter to divide down the input clock.

**Channel Configuration:**

.. flat-table:: Channel Configuration
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - :rspan:`1` 0x014
     - 0
     - enable
     - 0
     - RW
     - This bit can toggle whether the channel should output a rectangular wave.
   * - 1
     - invert
     - 0
     - RW
     - Invert the output wave from active-high to active-low.
   * - 0x018
     - channelPeriodWidth - 0
     - periodWidth
     - 0
     - RW
     - Defines the number of cycles one wave period should be.
   * - 0x01C
     - channelPulseWidth - 0
     - channelWidth
     - 0
     - RW
     - Defines the number of cycles when the output wave level will change. This counter resets to
       default value as soon as the period is over.
