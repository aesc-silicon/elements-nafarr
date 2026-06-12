/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "clock.h"

void clock_init(struct clock_driver *driver, unsigned long base_address,
		unsigned int reference_hz)
{
	driver->regs = (struct clock_regs *)base_address;
	driver->reference_hz = reference_hz;
}

unsigned int clock_domain_count(struct clock_driver *driver)
{
	return driver->regs->domains & 0xFF;
}

unsigned int clock_get_rate(struct clock_driver *driver, unsigned int index)
{
	unsigned int ratio = driver->regs->domain[index].ratio;
	unsigned int mult = (ratio >> 16) & 0xFFFF;
	unsigned int div = ratio & 0xFFFF;

	if (div == 0)
		return 0;

	return (unsigned int)(((unsigned long long)driver->reference_hz * mult) / div);
}

int clock_is_enabled(struct clock_driver *driver, unsigned int index)
{
	return (driver->regs->domain[index].control & CLOCK_CTRL_ENABLE) != 0;
}

int clock_is_locked(struct clock_driver *driver, unsigned int index)
{
	return (driver->regs->domain[index].control & CLOCK_CTRL_LOCK) != 0;
}

void clock_enable(struct clock_driver *driver, unsigned int index)
{
	driver->regs->domain[index].control = CLOCK_CTRL_ENABLE;
}

void clock_disable(struct clock_driver *driver, unsigned int index)
{
	driver->regs->domain[index].control = 0;
}
