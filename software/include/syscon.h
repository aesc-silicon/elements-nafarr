/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_SYSCON_H
#define ELEMENTS_SYSCON_H

struct syscon_regs {
	unsigned int ip_header;      /* 0x000 — IP identification header */
	unsigned int ip_version;     /* 0x004 — IP identification version */
	unsigned int identity;       /* 0x008 — vendor/platform/product/class */
	unsigned int silicon_major;  /* 0x00C — silicon major revision */
	unsigned int silicon_minor;  /* 0x010 — silicon minor revision */
	unsigned int features;       /* 0x014 — feature flag bitmask */
	unsigned int ref_clock;      /* 0x018 — reference oscillator (Hz) */
	unsigned int build_date;     /* 0x01C — build UNIX timestamp (s) */
};

/* identity register fields */
#define SYSCON_VENDOR(reg)         ((reg) & 0xFF)
#define SYSCON_PLATFORM(reg)       (((reg) >> 8) & 0xFF)
#define SYSCON_PRODUCT(reg)        (((reg) >> 16) & 0xFF)
#define SYSCON_PLATFORM_CLASS(reg) (((reg) >> 24) & 0xFF)

/* vendor ordinals */
#define SYSCON_VENDOR_AESC_SILICON 0

/* platform ordinals */
#define SYSCON_PLATFORM_HYDROGEN   0
#define SYSCON_PLATFORM_CARBON     1
#define SYSCON_PLATFORM_NITROGEN   2
#define SYSCON_PLATFORM_OXYGEN     3
#define SYSCON_PLATFORM_PHOSPHORUS 4
#define SYSCON_PLATFORM_SULFUR     5

/* platform class ordinals */
#define SYSCON_CLASS_NONMETAL      0  /* MCU: M-mode only, no MMU */
#define SYSCON_CLASS_ALKALI        1  /* MPU: MMU, OS-capable */

/* product ordinals */
#define SYSCON_PRODUCT_ELEMRV      0

/* feature flag bits in the features register */
#define SYSCON_FEATURE_I2C         (1U << 0)
#define SYSCON_FEATURE_SPI         (1U << 1)
#define SYSCON_FEATURE_UART        (1U << 2)
#define SYSCON_FEATURE_GPIO        (1U << 3)
#define SYSCON_FEATURE_PIO         (1U << 4)
#define SYSCON_FEATURE_PWM         (1U << 5)
#define SYSCON_FEATURE_PINMUX      (1U << 6)
#define SYSCON_FEATURE_CLOCK       (1U << 7)
#define SYSCON_FEATURE_ESM         (1U << 8)
#define SYSCON_FEATURE_MAILBOX     (1U << 9)
#define SYSCON_FEATURE_MTIMER      (1U << 10)
#define SYSCON_FEATURE_PLIC        (1U << 11)
#define SYSCON_FEATURE_RESET       (1U << 12)
#define SYSCON_FEATURE_SEMAPHORE   (1U << 13)
#define SYSCON_FEATURE_WATCHDOG    (1U << 14)
#define SYSCON_FEATURE_AES         (1U << 15)
#define SYSCON_FEATURE_CRC         (1U << 16)
#define SYSCON_FEATURE_PRNG        (1U << 17)
#define SYSCON_FEATURE_HYPERBUS    (1U << 18)
#define SYSCON_FEATURE_OCRAM       (1U << 19)
#define SYSCON_FEATURE_SPI_FLASH   (1U << 20)

struct syscon_driver {
	volatile struct syscon_regs *regs;
};

void syscon_init(struct syscon_driver *driver, unsigned long base_address);

unsigned int syscon_vendor(struct syscon_driver *driver);
unsigned int syscon_platform(struct syscon_driver *driver);
unsigned int syscon_platform_class(struct syscon_driver *driver);
unsigned int syscon_product(struct syscon_driver *driver);
unsigned int syscon_silicon_major(struct syscon_driver *driver);
unsigned int syscon_silicon_minor(struct syscon_driver *driver);
unsigned int syscon_features(struct syscon_driver *driver);
unsigned int syscon_ref_clock(struct syscon_driver *driver);
unsigned int syscon_build_date(struct syscon_driver *driver);

int syscon_has_feature(struct syscon_driver *driver, unsigned int feature_bit);

#endif
