/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "clock.h"

int main(void)
{
	struct clock_driver driver;
	static volatile struct clock_regs fake_regs;

	clock_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	clock_domain_count(&driver);
	clock_enable_get(&driver);
	clock_enable_set(&driver, 0x3);

	return 0;
}
