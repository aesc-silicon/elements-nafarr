/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "prng.h"

int prng_init(struct prng_driver *driver, unsigned long base_address)
{
	driver->regs = (volatile struct prng_regs *)base_address;

	return 1;
}

void prng_enable(struct prng_driver *driver)
{
	driver->regs->control |= 1;
}

void prng_disable(struct prng_driver *driver)
{
	driver->regs->control &= ~1;
}

int prng_seed(struct prng_driver *driver, unsigned int seed)
{
	if (seed == 0)
		return 0;

	driver->regs->seed = seed;

	return 1;
}

unsigned int prng_read(struct prng_driver *driver)
{
	return driver->regs->output;
}

int prng_error_pending(struct prng_driver *driver, int flags)
{
	return !!(driver->regs->error_pending & flags);
}

void prng_error_clear(struct prng_driver *driver, int flags)
{
	driver->regs->error_pending = flags;
}

void prng_error_mask(struct prng_driver *driver, int flags)
{
	driver->regs->error_mask |= flags;
}

void prng_error_unmask(struct prng_driver *driver, int flags)
{
	driver->regs->error_mask &= ~flags;
}
