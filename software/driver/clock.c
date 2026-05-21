/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "clock.h"

void clock_init(struct clock_driver *driver, unsigned long base_address)
{
	driver->regs = (struct clock_regs *)base_address;
}

unsigned int clock_domain_count(struct clock_driver *driver)
{
	return driver->regs->domains & 0xFF;
}

unsigned int clock_enable_get(struct clock_driver *driver)
{
	return driver->regs->enable;
}

void clock_enable_set(struct clock_driver *driver, unsigned int mask)
{
	driver->regs->enable = mask;
}
