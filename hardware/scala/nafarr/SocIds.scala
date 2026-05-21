// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr

import spinal.core._

/** Vendor identifier. Never change existing ordinals. */
object Vendor extends SpinalEnum(binarySequential) {
  val AescSilicon = newElement() // 0
}

/** Platform identifier. Never change existing ordinals. */
object Platform extends SpinalEnum(binarySequential) {
  val Hydrogen = newElement() // 0
  val Carbon = newElement() // 1
  val Nitrogen = newElement() // 2
  val Oxygen = newElement() // 3
  val Phosphorus = newElement() // 4
  val Sulfur = newElement() // 5
}

/** Product identifier. Never change existing ordinals. */
object Product extends SpinalEnum(binarySequential) {
  val ElemRV = newElement() // 0
}

/** Platform class derived from the chemistry group of the platform element.
  *
  * NonMetal — non-metal elements (Hydrogen, Carbon, Nitrogen, Oxygen, Phosphorus, Sulfur):
  *            MCU class, M-mode only, no MMU.
  * Alkali   — alkali metal elements (Lithium, Sodium, Potassium, ...):
  *            MPU class, MMU, OS-capable.
  *
  * Never change existing ordinals.
  */
object PlatformClass extends SpinalEnum(binarySequential) {
  val NonMetal = newElement() // 0 — MCU (non-metal elements)
  val Alkali = newElement() // 1 — MPU (alkali metal elements)
}

/** SoC feature flags for the syscon feature register.
  *
  * Each element maps to a bit position in a 32-bit register via its ordinal.
  * An IP reports its feature(s) via `sysconFeatures` on its Core class so that
  * the SoC builder can populate the syscon parameter automatically.
  *
  * Never change existing ordinals — software depends on stable bit positions.
  */
object Feature extends SpinalEnum(binarySequential) {
  val I2c = newElement() // 0
  val Spi = newElement() // 1
  val Uart = newElement() // 2
  val Gpio = newElement() // 3
  val Pio = newElement() // 4
  val Pwm = newElement() // 5
  val Pinmux = newElement() // 6
  val Clock = newElement() // 7
  val Esm = newElement() // 8
  val Mailbox = newElement() // 9
  val Mtimer = newElement() // 10
  val Timer = newElement() // 11
  val Plic = newElement() // 12
  val Reset = newElement() // 13
  val Semaphore = newElement() // 14
  val Watchdog = newElement() // 15
  val Aes = newElement() // 16
  val Crc = newElement() // 17
  val Prng = newElement() // 18
  val Hyperbus = newElement() // 19
  val Ocram = newElement() // 20
  val SpiFlash = newElement() // 21
}
