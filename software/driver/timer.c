/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "timer.h"

/* Per-timer registers start at regs base + 0x14, stride bytes apart.
 * Returns a pointer to word 0 (control) of timer t. */
static volatile unsigned int *timer_base(struct timer_driver *driver, unsigned int t)
{
	return (volatile unsigned int *)((unsigned char *)driver->regs + 0x14 +
					 t * driver->stride);
}

void timer_init(struct timer_driver *driver, unsigned int base_address)
{
	unsigned int info;

	driver->regs = (volatile struct timer_regs *)base_address;
	info = driver->regs->info;
	driver->count         = TIMER_INFO_COUNT(info);
	driver->channel_count = TIMER_INFO_CHANNEL_COUNT(info);
	driver->stride        = 0x10 + driver->channel_count * 4;
}

void timer_set_control(struct timer_driver *driver, unsigned int t, unsigned int ctrl)
{
	timer_base(driver, t)[0] = ctrl;
}

unsigned int timer_get_control(struct timer_driver *driver, unsigned int t)
{
	return timer_base(driver, t)[0];
}

void timer_set_prescaler(struct timer_driver *driver, unsigned int t, unsigned int val)
{
	timer_base(driver, t)[1] = val;
}

void timer_set_reload(struct timer_driver *driver, unsigned int t, unsigned int val)
{
	timer_base(driver, t)[3] = val;
}

unsigned int timer_get_counter(struct timer_driver *driver, unsigned int t)
{
	return timer_base(driver, t)[2];
}

void timer_set_counter(struct timer_driver *driver, unsigned int t, unsigned int val)
{
	timer_base(driver, t)[2] = val;
}

void timer_set_compare(struct timer_driver *driver, unsigned int t, unsigned int ch,
		       unsigned int val)
{
	timer_base(driver, t)[4 + ch] = val;
}

unsigned int timer_irq_pending(struct timer_driver *driver)
{
	return driver->regs->irq_pending;
}

void timer_irq_clear(struct timer_driver *driver, unsigned int mask)
{
	driver->regs->irq_pending = mask;
}

void timer_irq_enable(struct timer_driver *driver, unsigned int mask)
{
	driver->regs->irq_mask |= mask;
}

void timer_irq_disable(struct timer_driver *driver, unsigned int mask)
{
	driver->regs->irq_mask &= ~mask;
}
