/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "clock.h"

#define FAKE_DOMAINS 2

int main(void)
{
	struct clock_driver driver;
	/*
	 * clock_regs ends in a flexible per-domain array, so back it with
	 * storage for the header plus a couple of domains.
	 */
	static volatile unsigned int fake_regs[(sizeof(struct clock_regs) +
		FAKE_DOMAINS * sizeof(struct clock_domain_regs)) /
		sizeof(unsigned int)];

	clock_init(&driver, 0xf0000000, 50000000);
	driver.regs = (volatile struct clock_regs *)fake_regs;

	clock_domain_count(&driver);
	clock_get_rate(&driver, 0);
	clock_is_enabled(&driver, 0);
	clock_is_locked(&driver, 0);
	clock_enable(&driver, 0);
	clock_disable(&driver, 0);

	return 0;
}
