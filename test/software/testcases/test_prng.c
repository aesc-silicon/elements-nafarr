/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "prng.h"

int main(void)
{
	struct prng_driver driver;
	static volatile struct prng_regs fake_regs;

	prng_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	prng_enable(&driver);
	prng_seed(&driver, 0xdeadbeef);
	prng_read(&driver);
	prng_error_pending(&driver, PRNG_ERROR_ZERO_SEED);
	prng_error_clear(&driver, PRNG_ERROR_ZERO_SEED);
	prng_error_mask(&driver, PRNG_ERROR_ZERO_SEED);
	prng_error_unmask(&driver, PRNG_ERROR_ZERO_SEED);
	prng_disable(&driver);

	return 0;
}
