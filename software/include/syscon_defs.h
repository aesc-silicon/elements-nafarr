/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Generated from the Scala sources by nafarr.GenerateHeaders. Do not edit; regenerate
 * with: sbt "runMain nafarr.GenerateHeaders"
 */

#ifndef ELEMENTS_SYSCON_DEFS_H
#define ELEMENTS_SYSCON_DEFS_H

/* Register block */
#define SYSCON_FEATURE_WORDS               1

struct syscon_regs {
	unsigned int ip_header;                      /* 0x000: IP identification header */
	unsigned int ip_version;                     /* 0x004: IP identification version */
	unsigned int identity;                       /* 0x008: class, product, platform, vendor */
	unsigned int silicon_rev;                    /* 0x00C: major [31:16], minor [15:0] */
	unsigned int build_date;                     /* 0x010: build UNIX timestamp (s) */
	unsigned int ref_clock;                      /* 0x014: reference oscillator (Hz) */
	unsigned int feature_info;                   /* 0x018: feature words [7:0] */
	unsigned int features[SYSCON_FEATURE_WORDS]; /* 0x01C: ordinal N: bit N % 32, word N / 32 */
};

/* Vendor ordinals: identity [7:0] */
#define SYSCON_VENDOR_AESC_SILICON         0
#define SYSCON_VENDOR_COUNT                1
#define SYSCON_FOR_EACH_VENDOR(X) \
	X(AESC_SILICON, 0)

/* Platform ordinals: identity [15:8] */
#define SYSCON_PLATFORM_HYDROGEN           0
#define SYSCON_PLATFORM_CARBON             1
#define SYSCON_PLATFORM_NITROGEN           2
#define SYSCON_PLATFORM_OXYGEN             3
#define SYSCON_PLATFORM_PHOSPHORUS         4
#define SYSCON_PLATFORM_SULFUR             5
#define SYSCON_PLATFORM_COUNT              6
#define SYSCON_FOR_EACH_PLATFORM(X) \
	X(HYDROGEN, 0) \
	X(CARBON, 1) \
	X(NITROGEN, 2) \
	X(OXYGEN, 3) \
	X(PHOSPHORUS, 4) \
	X(SULFUR, 5)

/* Product ordinals: identity [23:16] */
#define SYSCON_PRODUCT_ELEMRV              0
#define SYSCON_PRODUCT_COUNT               1
#define SYSCON_FOR_EACH_PRODUCT(X) \
	X(ELEMRV, 0)

/* Platform class ordinals: identity [31:24] */
#define SYSCON_PLATFORM_CLASS_NON_METAL    0
#define SYSCON_PLATFORM_CLASS_ALKALI       1
#define SYSCON_PLATFORM_CLASS_COUNT        2
#define SYSCON_FOR_EACH_PLATFORM_CLASS(X) \
	X(NON_METAL, 0) \
	X(ALKALI, 1)

/* Feature ordinals */
#define SYSCON_FEATURE_I2C                 0
#define SYSCON_FEATURE_SPI                 1
#define SYSCON_FEATURE_UART                2
#define SYSCON_FEATURE_GPIO                3
#define SYSCON_FEATURE_PIO                 4
#define SYSCON_FEATURE_PWM                 5
#define SYSCON_FEATURE_PINMUX              6
#define SYSCON_FEATURE_CLOCK               7
#define SYSCON_FEATURE_ESM                 8
#define SYSCON_FEATURE_MAILBOX             9
#define SYSCON_FEATURE_MTIMER              10
#define SYSCON_FEATURE_TIMER               11
#define SYSCON_FEATURE_PLIC                12
#define SYSCON_FEATURE_RESET               13
#define SYSCON_FEATURE_SEMAPHORE           14
#define SYSCON_FEATURE_WATCHDOG            15
#define SYSCON_FEATURE_AES                 16
#define SYSCON_FEATURE_CRC                 17
#define SYSCON_FEATURE_PRNG                18
#define SYSCON_FEATURE_HYPERBUS            19
#define SYSCON_FEATURE_OCRAM               20
#define SYSCON_FEATURE_SPI_FLASH           21
#define SYSCON_FEATURE_DMA                 22
#define SYSCON_FEATURE_COUNT               23
#define SYSCON_FOR_EACH_FEATURE(X) \
	X(I2C, 0) \
	X(SPI, 1) \
	X(UART, 2) \
	X(GPIO, 3) \
	X(PIO, 4) \
	X(PWM, 5) \
	X(PINMUX, 6) \
	X(CLOCK, 7) \
	X(ESM, 8) \
	X(MAILBOX, 9) \
	X(MTIMER, 10) \
	X(TIMER, 11) \
	X(PLIC, 12) \
	X(RESET, 13) \
	X(SEMAPHORE, 14) \
	X(WATCHDOG, 15) \
	X(AES, 16) \
	X(CRC, 17) \
	X(PRNG, 18) \
	X(HYPERBUS, 19) \
	X(OCRAM, 20) \
	X(SPI_FLASH, 21) \
	X(DMA, 22)

#endif
