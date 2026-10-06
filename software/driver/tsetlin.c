/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "tsetlin.h"

static unsigned int words(unsigned int bits)
{
	return (bits + 31) / 32;
}

int tsetlin_init(struct tsetlin_driver *driver, unsigned long base_address)
{
	driver->regs = (struct tsetlin_regs *)base_address;
	driver->rows = driver->regs->array & 0xffff;
	driver->literals = driver->regs->array >> 16;
	driver->classes = driver->regs->classes & 0xffff;
	driver->clauses_per_class = driver->regs->classes >> 16;

	return 1;
}

int tsetlin_write_clause(struct tsetlin_driver *driver, unsigned int row,
			 const unsigned int *include)
{
	if (row >= driver->rows || driver->regs->status & TSETLIN_STATUS_BUSY)
		return -1;
	for (unsigned int w = 0; w < words(driver->literals); w++)
		driver->regs->data[w] = include[w];
	driver->regs->row = row;
	driver->regs->commit = 1;

	return 0;
}

int tsetlin_search_start(struct tsetlin_driver *driver, const unsigned int *literals)
{
	if (driver->regs->status & TSETLIN_STATUS_BUSY)
		return -1;
	for (unsigned int w = 0; w < words(driver->literals); w++)
		driver->regs->literals[w] = literals[w];
	driver->regs->ctrl = (driver->regs->ctrl & TSETLIN_CTRL_IRQ_ENABLE) | TSETLIN_CTRL_START;

	return 0;
}

int tsetlin_search_done(struct tsetlin_driver *driver)
{
	return (driver->regs->status & TSETLIN_STATUS_DONE) != 0;
}

int tsetlin_result(struct tsetlin_driver *driver, int *sum)
{
	unsigned int result = driver->regs->result;

	driver->regs->status = TSETLIN_STATUS_DONE;
	if (sum)
		*sum = (short)(result >> 16);

	return result & 0xffff;
}

int tsetlin_class_sum(struct tsetlin_driver *driver, unsigned int cls)
{
	return cls < driver->classes ? driver->regs->sums[cls] : 0;
}

void tsetlin_irq_enable(struct tsetlin_driver *driver)
{
	driver->regs->ctrl = TSETLIN_CTRL_IRQ_ENABLE;
}

void tsetlin_irq_disable(struct tsetlin_driver *driver)
{
	driver->regs->ctrl = 0;
}
