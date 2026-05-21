/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "watchdog.h"

int watchdog_init(struct watchdog_driver *driver, unsigned int base_address)
{
	driver->regs = (volatile struct watchdog_regs *)base_address;

	return 1;
}

void watchdog_configure(struct watchdog_driver *driver, unsigned int wdt,
			uint32_t prescaler, uint32_t timeout)
{
	driver->regs->wdt[wdt].prescaler = prescaler;
	driver->regs->wdt[wdt].timeout = timeout;
}

void watchdog_configure_window(struct watchdog_driver *driver, unsigned int wdt,
			       uint32_t window_open)
{
	driver->regs->wdt[wdt].window_open = window_open;
}

void watchdog_enable(struct watchdog_driver *driver, unsigned int wdt)
{
	driver->regs->wdt[wdt].control |= WATCHDOG_CTRL_ENABLE;
}

void watchdog_disable(struct watchdog_driver *driver, unsigned int wdt)
{
	driver->regs->wdt[wdt].control &= ~WATCHDOG_CTRL_ENABLE;
}

void watchdog_lock(struct watchdog_driver *driver, unsigned int wdt)
{
	driver->regs->wdt[wdt].control |= WATCHDOG_CTRL_LOCK;
}

void watchdog_kick(struct watchdog_driver *driver, unsigned int wdt)
{
	driver->regs->wdt[wdt].kick = 1;
}

uint32_t watchdog_status(struct watchdog_driver *driver, unsigned int wdt)
{
	return driver->regs->wdt[wdt].status;
}

void watchdog_irq_enable(struct watchdog_driver *driver, unsigned int wdt,
			 uint32_t flags)
{
	driver->regs->wdt[wdt].irq_mask |= flags;
}

void watchdog_irq_disable(struct watchdog_driver *driver, unsigned int wdt,
			  uint32_t flags)
{
	driver->regs->wdt[wdt].irq_mask &= ~flags;
}

int watchdog_irq_pending(struct watchdog_driver *driver, unsigned int wdt,
			 uint32_t flags)
{
	return !!(driver->regs->wdt[wdt].irq_pending & flags);
}

void watchdog_irq_clear(struct watchdog_driver *driver, unsigned int wdt,
			uint32_t flags)
{
	driver->regs->wdt[wdt].irq_pending = flags;
}
