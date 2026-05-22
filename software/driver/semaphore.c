/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "semaphore.h"

int semaphore_init(struct semaphore_driver *driver, unsigned int base_address,
		   unsigned int count)
{
	driver->regs = (volatile struct semaphore_regs *)base_address;
	driver->count = count;

	return 1;
}

int semaphore_claim(struct semaphore_driver *driver, unsigned int n)
{
	return driver->regs->semaphore[n] & 0x1;
}

void semaphore_release(struct semaphore_driver *driver, unsigned int n)
{
	driver->regs->semaphore[n] = 0;
}

unsigned int semaphore_status(struct semaphore_driver *driver)
{
	return driver->regs->status;
}
