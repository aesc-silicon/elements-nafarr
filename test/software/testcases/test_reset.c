/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "reset.h"

int main(void)
{
	struct reset_driver driver;
	static volatile struct reset_regs fake_regs;

	reset_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	reset_domain_count(&driver);
	reset_enable_get(&driver);
	reset_enable_set(&driver, 0x3);
	reset_trigger(&driver, 0x1);
	reset_acknowledge(&driver);

	return 0;
}
