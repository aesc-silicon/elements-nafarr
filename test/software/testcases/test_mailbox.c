/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "mailbox.h"

int main(void)
{
	struct mailbox_driver driver;
	static volatile struct mailbox_regs fake_regs;

	mailbox_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	mailbox_status(&driver);
	mailbox_occupancy(&driver, 0);

	unsigned int data;
	mailbox_write(&driver, 0, 0xdeadbeef);
	mailbox_read(&driver, 0, &data);

	mailbox_irq_enable(&driver, 0, MAILBOX_IRQ_NOT_EMPTY | MAILBOX_IRQ_NOT_FULL);
	mailbox_irq_pending(&driver, 0, MAILBOX_IRQ_NOT_EMPTY);
	mailbox_irq_clear(&driver, 0, MAILBOX_IRQ_NOT_EMPTY);
	mailbox_irq_disable(&driver, 0, MAILBOX_IRQ_NOT_EMPTY | MAILBOX_IRQ_NOT_FULL);

	return 0;
}
