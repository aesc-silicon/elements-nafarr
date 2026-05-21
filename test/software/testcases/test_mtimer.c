/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "mtimer.h"

int main(void)
{
	struct mtimer_driver driver;
	static volatile struct mtimer_regs fake_regs;

	/* mtimer_init writes cmp registers; pass fake address directly */
	mtimer_init(&driver, (unsigned long)&fake_regs);

	/* cycles == 0: compare = cnt_low + 0 = 0; while(0 > 0) exits immediately */
	mtimer_sleep32(&driver, 0);

	return 0;
}
