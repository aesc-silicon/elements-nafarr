/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "esm.h"

int main(void)
{
	struct esm_driver driver;
	static volatile struct esm_regs fake_regs;

	/* esm_init reads info to derive bank_count; pass fake address directly */
	esm_init(&driver, (unsigned long)&fake_regs);
	/* bank_count == 0 from zeroed info; override to exercise bank API */
	driver.bank_count = 1;

	esm_enable(&driver);
	esm_configure(&driver, 0, ESM_LEVEL_INFO(0x3) | ESM_LEVEL_WARN(0x3));
	esm_configure_counter(&driver, 100);
	esm_inject_enable(&driver);
	esm_inject_set(&driver, 0, 0x1);
	esm_raw(&driver, 0);
	esm_pending(&driver, 0);
	esm_clear(&driver, 0, ESM_LEVEL_INFO(0x3));
	esm_inject_clear(&driver, 0, 0x1);
	esm_inject_disable(&driver);
	esm_status(&driver);
	esm_disable(&driver);
	esm_lock(&driver);

	return 0;
}
