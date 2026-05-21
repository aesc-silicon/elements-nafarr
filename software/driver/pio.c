/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "pio.h"

void pio_init(struct pio_driver *driver, unsigned long base_address)
{
	driver->regs = (struct pio_regs *)base_address;
}

void pio_enable(struct pio_driver *driver)
{
	driver->regs->control |= PIO_CTRL_ENABLE;
}

void pio_disable(struct pio_driver *driver)
{
	driver->regs->control &= ~PIO_CTRL_ENABLE;
}

void pio_set_stop_at_loop(struct pio_driver *driver, int enable)
{
	if (enable)
		driver->regs->control |= PIO_CTRL_STOP_AT_LOOP;
	else
		driver->regs->control &= ~PIO_CTRL_STOP_AT_LOOP;
}

void pio_program_reset(struct pio_driver *driver)
{
	driver->regs->fifo_status = 1;
}

void pio_program_write(struct pio_driver *driver, unsigned int cmd_word)
{
	driver->regs->read_write = cmd_word;
}

int pio_read(struct pio_driver *driver, unsigned int *result)
{
	unsigned int val = driver->regs->read_write;

	if (!(val & PIO_READ_VALID))
		return -1;

	*result = PIO_READ_RESULT(val);
	return 0;
}

void pio_irq_enable(struct pio_driver *driver, unsigned int mask)
{
	driver->regs->irq_enable |= mask;
}

void pio_irq_disable(struct pio_driver *driver, unsigned int mask)
{
	driver->regs->irq_enable &= ~mask;
}

unsigned int pio_irq_pending(struct pio_driver *driver)
{
	return driver->regs->irq_pending;
}

void pio_irq_clear(struct pio_driver *driver, unsigned int mask)
{
	driver->regs->irq_pending = mask;
}
