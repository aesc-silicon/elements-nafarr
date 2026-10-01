/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stdio.h>
#include <string.h>

#include "syscon.h"

#define CHECK(cond)                                                                 \
	do {                                                                        \
		if (!(cond)) {                                                      \
			printf("%s:%d: %s\n", __FILE__, __LINE__, #cond);           \
			return 1;                                                   \
		}                                                                   \
	} while (0)

int main(void)
{
	struct syscon_driver driver;
	static volatile struct syscon_regs fake_regs;

	syscon_init(&driver, 0xf0000000);
	CHECK(driver.regs == (volatile struct syscon_regs *)0xf0000000);
	driver.regs = &fake_regs;

	/* ElemRV on Nitrogen (NonMetal class), silicon 2.3, with UART and DMA */
	fake_regs.identity = (SYSCON_PLATFORM_CLASS_NON_METAL << 24) |
			     (SYSCON_PRODUCT_ELEMRV << 16) |
			     (SYSCON_PLATFORM_NITROGEN << 8) |
			     SYSCON_VENDOR_AESC_SILICON;
	fake_regs.silicon_rev = 0x00020003;
	fake_regs.build_date = 1790832382;
	fake_regs.ref_clock = 60000000;
	fake_regs.feature_info = SYSCON_FEATURE_WORDS;
	fake_regs.features[0] = (1U << SYSCON_FEATURE_UART) | (1U << SYSCON_FEATURE_DMA);

	CHECK(syscon_vendor(&driver) == SYSCON_VENDOR_AESC_SILICON);
	CHECK(syscon_platform(&driver) == SYSCON_PLATFORM_NITROGEN);
	CHECK(syscon_platform_class(&driver) == SYSCON_PLATFORM_CLASS_NON_METAL);
	CHECK(syscon_product(&driver) == SYSCON_PRODUCT_ELEMRV);
	CHECK(syscon_silicon_major(&driver) == 2);
	CHECK(syscon_silicon_minor(&driver) == 3);
	CHECK(syscon_build_date(&driver) == 1790832382);
	CHECK(syscon_ref_clock(&driver) == 60000000);

	CHECK(syscon_feature_words(&driver) == SYSCON_FEATURE_WORDS);
	CHECK(syscon_features(&driver, 0) == fake_regs.features[0]);
	CHECK(syscon_features(&driver, SYSCON_FEATURE_WORDS) == 0);
	CHECK(syscon_has_feature(&driver, SYSCON_FEATURE_UART));
	CHECK(syscon_has_feature(&driver, SYSCON_FEATURE_DMA));
	CHECK(!syscon_has_feature(&driver, SYSCON_FEATURE_GPIO));
	CHECK(!syscon_has_feature(&driver, SYSCON_FEATURE_COUNT));

	/* A feature word the hardware does not report reads as empty */
	fake_regs.feature_info = 0;
	CHECK(!syscon_has_feature(&driver, SYSCON_FEATURE_UART));

	CHECK(strcmp(syscon_vendor_name(SYSCON_VENDOR_AESC_SILICON), "AESC_SILICON") == 0);
	CHECK(strcmp(syscon_platform_name(SYSCON_PLATFORM_NITROGEN), "NITROGEN") == 0);
	CHECK(syscon_platform_name(SYSCON_PLATFORM_COUNT) == 0);
	CHECK(strcmp(syscon_product_name(SYSCON_PRODUCT_ELEMRV), "ELEMRV") == 0);
	CHECK(strcmp(syscon_platform_class_name(SYSCON_PLATFORM_CLASS_NON_METAL),
		     "NON_METAL") == 0);

	CHECK(strcmp(syscon_feature_name(SYSCON_FEATURE_I2C), "I2C") == 0);
	CHECK(strcmp(syscon_feature_name(SYSCON_FEATURE_SPI_FLASH), "SPI_FLASH") == 0);
	CHECK(strcmp(syscon_feature_name(SYSCON_FEATURE_DMA), "DMA") == 0);
	CHECK(syscon_feature_name(SYSCON_FEATURE_COUNT) == 0);
	for (unsigned int feature = 0; feature < SYSCON_FEATURE_COUNT; feature++) {
		CHECK(syscon_feature_name(feature) != 0);
	}

	return 0;
}
