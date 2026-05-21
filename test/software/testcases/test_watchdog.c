/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "watchdog.h"

int main(void)
{
	struct watchdog_driver driver;
	static volatile struct watchdog_regs fake_regs;

	watchdog_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	watchdog_configure(&driver, 0, 999, 9999);
	watchdog_configure_window(&driver, 0, 4999);
	watchdog_enable(&driver, 0);
	watchdog_status(&driver, 0);
	watchdog_kick(&driver, 0);
	watchdog_irq_enable(&driver, 0, WATCHDOG_IRQ_TIMEOUT_IRQ | WATCHDOG_IRQ_TIMEOUT_ERR);
	watchdog_irq_pending(&driver, 0, WATCHDOG_IRQ_TIMEOUT_IRQ);
	watchdog_irq_clear(&driver, 0, WATCHDOG_IRQ_TIMEOUT_IRQ);
	watchdog_irq_disable(&driver, 0, WATCHDOG_IRQ_TIMEOUT_IRQ | WATCHDOG_IRQ_TIMEOUT_ERR);
	watchdog_disable(&driver, 0);
	watchdog_lock(&driver, 0);

	return 0;
}
