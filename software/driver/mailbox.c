/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "mailbox.h"

int mailbox_init(struct mailbox_driver *driver, unsigned long base_address)
{
	driver->regs = (volatile struct mailbox_regs *)base_address;

	return 1;
}

int mailbox_write(struct mailbox_driver *driver, unsigned int ch,
		  unsigned int data)
{
	if (driver->regs->status & MAILBOX_STATUS_FULL(ch))
		return 0;

	driver->regs->channel[ch].write = data;

	return 1;
}

int mailbox_read(struct mailbox_driver *driver, unsigned int ch,
		 unsigned int *data)
{
	if (driver->regs->status & MAILBOX_STATUS_EMPTY(ch))
		return 0;

	*data = driver->regs->channel[ch].read;

	return 1;
}

unsigned int mailbox_status(struct mailbox_driver *driver)
{
	return driver->regs->status;
}

unsigned int mailbox_occupancy(struct mailbox_driver *driver, unsigned int ch)
{
	return driver->regs->channel[ch].occupancy;
}

int mailbox_irq_enable(struct mailbox_driver *driver, unsigned int ch,
		       int flags)
{
	driver->regs->channel[ch].irq_mask |= flags;

	return 1;
}

int mailbox_irq_disable(struct mailbox_driver *driver, unsigned int ch,
			int flags)
{
	driver->regs->channel[ch].irq_mask &= ~flags;

	return 1;
}

int mailbox_irq_pending(struct mailbox_driver *driver, unsigned int ch,
			int flags)
{
	return !!(driver->regs->channel[ch].irq_pending & flags);
}

void mailbox_irq_clear(struct mailbox_driver *driver, unsigned int ch,
		       int flags)
{
	driver->regs->channel[ch].irq_pending = flags;
}
