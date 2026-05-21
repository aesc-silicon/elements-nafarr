/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "reset.h"

void reset_init(struct reset_driver *driver, unsigned long base_address)
{
	driver->regs = (struct reset_regs *)base_address;
}

unsigned int reset_domain_count(struct reset_driver *driver)
{
	return driver->regs->domains & 0xFF;
}

unsigned int reset_enable_get(struct reset_driver *driver)
{
	return driver->regs->enable;
}

void reset_enable_set(struct reset_driver *driver, unsigned int mask)
{
	driver->regs->enable = mask;
}

void reset_trigger(struct reset_driver *driver, unsigned int mask)
{
	driver->regs->trigger = mask;
}

void reset_acknowledge(struct reset_driver *driver)
{
	driver->regs->acknowledge = 1;
}
