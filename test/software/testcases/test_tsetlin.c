/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stddef.h>

#include "tsetlin.h"

/* the register layout must match TsetlinCtrl.Regs */
_Static_assert(offsetof(struct tsetlin_regs, array) == 0x08, "array");
_Static_assert(offsetof(struct tsetlin_regs, options) == 0x10, "options");
_Static_assert(offsetof(struct tsetlin_regs, ctrl) == 0x14, "ctrl");
_Static_assert(offsetof(struct tsetlin_regs, result) == 0x24, "result");
_Static_assert(offsetof(struct tsetlin_regs, data) == 0x100, "data");
_Static_assert(offsetof(struct tsetlin_regs, literals) == 0x200, "literals");
_Static_assert(offsetof(struct tsetlin_regs, matches) == 0x300, "matches");
_Static_assert(offsetof(struct tsetlin_regs, sums) == 0x400, "sums");

int main(void)
{
	struct tsetlin_driver driver;
	static volatile struct tsetlin_regs fake_regs;
	unsigned int include[3] = { 1, 0, 0 };
	int sum;

	fake_regs.array = (80 << 16) | 64;
	fake_regs.classes = (20 << 16) | 3;
	driver.regs = &fake_regs;
	tsetlin_init(&driver, (unsigned long)&fake_regs);
	if (driver.rows != 64 || driver.literals != 80 || driver.classes != 3 ||
	    driver.clauses_per_class != 20)
		return 1;

	tsetlin_irq_enable(&driver);
	if (tsetlin_write_clause(&driver, 3, include) || fake_regs.row != 3 || fake_regs.data[0] != 1)
		return 1;
	if (tsetlin_write_clause(&driver, 64, include) != -1)
		return 1;
	if (tsetlin_search_start(&driver, include) || fake_regs.ctrl != 3)
		return 1;
	tsetlin_search_done(&driver);
	fake_regs.result = (0xfffe << 16) | 2;
	if (tsetlin_result(&driver, &sum) != 2 || sum != -2)
		return 1;
	tsetlin_class_sum(&driver, 0);

	return 0;
}
