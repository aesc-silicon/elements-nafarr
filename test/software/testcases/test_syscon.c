/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "syscon.h"

int main(void)
{
	struct syscon_driver driver;
	static volatile struct syscon_regs fake_regs;

	syscon_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	syscon_vendor(&driver);
	syscon_platform(&driver);
	syscon_platform_class(&driver);
	syscon_product(&driver);
	syscon_silicon_major(&driver);
	syscon_silicon_minor(&driver);
	syscon_features(&driver);
	syscon_ref_clock(&driver);
	syscon_build_date(&driver);
	syscon_has_feature(&driver, SYSCON_FEATURE_GPIO);

	return 0;
}
