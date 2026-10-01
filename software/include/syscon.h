/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_SYSCON_H
#define ELEMENTS_SYSCON_H

/* Register block and ordinals, generated from the Scala sources */
#include "syscon_defs.h"

/* identity register fields */
#define SYSCON_VENDOR(reg)         ((reg) & 0xFF)
#define SYSCON_PLATFORM(reg)       (((reg) >> 8) & 0xFF)
#define SYSCON_PRODUCT(reg)        (((reg) >> 16) & 0xFF)
#define SYSCON_PLATFORM_CLASS(reg) (((reg) >> 24) & 0xFF)

/* silicon_rev register fields */
#define SYSCON_SILICON_MAJOR(reg)  (((reg) >> 16) & 0xFFFF)
#define SYSCON_SILICON_MINOR(reg)  ((reg) & 0xFFFF)

/* feature_info register fields */
#define SYSCON_FEATURE_WORD_COUNT(reg) ((reg) & 0xFF)

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
unsigned int syscon_ref_clock(struct syscon_driver *driver);
unsigned int syscon_build_date(struct syscon_driver *driver);

/* Number of feature words the hardware provides */
unsigned int syscon_feature_words(struct syscon_driver *driver);

/* Feature word `word`, or 0 if the hardware or this header has no such word */
unsigned int syscon_features(struct syscon_driver *driver, unsigned int word);

/* Whether the SoC contains the IP with feature ordinal `feature` (SYSCON_FEATURE_*) */
int syscon_has_feature(struct syscon_driver *driver, unsigned int feature);

/* Names of ordinals, e.g. "NITROGEN" or "SPI_FLASH", or 0 for an unknown ordinal */
const char *syscon_vendor_name(unsigned int vendor);
const char *syscon_platform_name(unsigned int platform);
const char *syscon_product_name(unsigned int product);
const char *syscon_platform_class_name(unsigned int platform_class);
const char *syscon_feature_name(unsigned int feature);

#endif
