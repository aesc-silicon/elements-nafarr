.. _hardware-system-syscon:

System Controller (Syscon)
##########################

The System Controller provides a read-only identification and configuration
register block. Software can query compile-time SoC parameters at runtime:
vendor, platform, product, silicon revision, feature flags, reference clock
frequency, and build timestamp.

All registers are constants derived from elaboration-time ``Parameter`` values.
No write access is defined; bus writes are silently ignored.

Features
********

* IP Identification block (header + version at 0x000-0x007)
* 32-bit identity register: vendor, platform, product, and platform class
* Independent silicon major/minor revision registers
* 32-bit feature flag register: bit N set when ``Feature`` element with ordinal N is present
* Reference clock frequency register (Hz)
* UNIX build timestamp register (seconds since epoch)
* Supported buses: APB3, TileLink, Wishbone

Parameters
**********

.. list-table::
   :widths: 20 15 65
   :header-rows: 1

   * - Parameter
     - Default
     - Description
   * - ``vendor``
     - *(required)*
     - Vendor identifier (``Vendor`` enum)
   * - ``platform``
     - *(required)*
     - Platform identifier (``Platform`` enum)
   * - ``platformClass``
     - *(required)*
     - Platform class (``PlatformClass`` enum): NonMetal (MCU) or Alkali (MPU)
   * - ``product``
     - *(required)*
     - Product identifier (``Product`` enum)
   * - ``refClockHz``
     - *(required)*
     - Board reference oscillator frequency in Hz
   * - ``siliconMajor``
     - ``0``
     - Silicon major revision
   * - ``siliconMinor``
     - ``1``
     - Silicon minor revision
   * - ``features``
     - ``List()``
     - Feature flags to set in the features register
   * - ``buildDate``
     - env ``BUILD_DATE`` or ``now``
     - UNIX timestamp (seconds). Read from the ``BUILD_DATE`` environment variable if set, otherwise captured at elaboration time

Register Map
************

.. list-table::
   :widths: 10 20 70
   :header-rows: 1

   * - Offset
     - Name
     - Description
   * - 0x000
     - ``ip_header``
     - IP Identification header (API, length, IP ID)
   * - 0x004
     - ``ip_version``
     - IP Identification version (major, minor, patch)
   * - 0x008
     - ``identity``
     - SoC identity (see below)
   * - 0x00C
     - ``silicon_major``
     - Silicon major revision
   * - 0x010
     - ``silicon_minor``
     - Silicon minor revision
   * - 0x014
     - ``features``
     - Feature flag bitmask
   * - 0x018
     - ``ref_clock``
     - Reference oscillator frequency (Hz)
   * - 0x01C
     - ``build_date``
     - Build UNIX timestamp (seconds)

Identity Register (0x008)
=========================

.. list-table::
   :widths: 15 15 70
   :header-rows: 1

   * - Bits
     - Field
     - Description
   * - [31:24]
     - ``platformClass``
     - Platform class ordinal (0=NonMetal/MCU, 1=Alkali/MPU)
   * - [23:16]
     - ``product``
     - Product ordinal (0=ElemRV)
   * - [15:8]
     - ``platform``
     - Platform ordinal (0=Hydrogen, 1=Carbon, 2=Nitrogen, 3=Oxygen, 4=Phosphorus, 5=Sulfur)
   * - [7:0]
     - ``vendor``
     - Vendor ordinal (0=AescSilicon)

Features Register (0x014)
=========================

Bit N is set when the ``Feature`` element with ordinal N appears in the
``features`` parameter list. The SoC builder populates this list automatically
by collecting ``sysconFeatures`` from each IP on the bus.

.. list-table::
   :widths: 10 20 70
   :header-rows: 1

   * - Bit
     - Feature
     - Description
   * - 0
     - ``I2c``
     - I2C controller or device present
   * - 1
     - ``Spi``
     - SPI controller or device present
   * - 2
     - ``Uart``
     - UART present
   * - 3
     - ``Gpio``
     - GPIO present
   * - 4
     - ``Pio``
     - Programmable I/O present
   * - 5
     - ``Pwm``
     - PWM present
   * - 6
     - ``Pinmux``
     - Pin multiplexer present
   * - 7
     - ``Clock``
     - Clock controller present
   * - 8
     - ``Esm``
     - Error Signaling Module present
   * - 9
     - ``Mailbox``
     - Mailbox present
   * - 10
     - ``Mtimer``
     - Machine-mode timer present
   * - 11
     - ``Plic``
     - Platform-level interrupt controller present
   * - 12
     - ``Reset``
     - Reset controller present
   * - 13
     - ``Semaphore``
     - Hardware semaphore present
   * - 14
     - ``Watchdog``
     - Watchdog timer present
   * - 15
     - ``Aes``
     - AES accelerator present
   * - 16
     - ``Crc``
     - CRC engine present
   * - 17
     - ``Prng``
     - Pseudo-random number generator present
   * - 18
     - ``Hyperbus``
     - HyperBus interface present
   * - 19
     - ``Ocram``
     - On-chip SRAM controller present
   * - 20
     - ``SpiFlash``
     - SPI flash controller present
