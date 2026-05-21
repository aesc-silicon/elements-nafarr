/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "semaphore.h"

int main(void)
{
	struct semaphore_driver driver;
	static volatile struct semaphore_regs fake_regs;

	semaphore_init(&driver, 0xf0000000, 4);
	driver.regs = &fake_regs;

	semaphore_status(&driver);
	semaphore_claim(&driver, 0);
	semaphore_release(&driver, 0);

	return 0;
}
