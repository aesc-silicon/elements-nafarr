/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "esm.h"

int esm_init(struct esm_driver *driver, unsigned long base_address)
{
	driver->regs = (volatile struct esm_regs *)base_address;
	driver->bank_count = (driver->regs->info >> 9) & 0x7Fu;
	return 1;
}

void esm_enable(struct esm_driver *driver)
{
	driver->regs->control |= ESM_CTRL_ENABLE;
}

void esm_disable(struct esm_driver *driver)
{
	driver->regs->control &= ~ESM_CTRL_ENABLE;
}

void esm_lock(struct esm_driver *driver)
{
	driver->regs->control |= ESM_CTRL_LOCK;
}

void esm_configure(struct esm_driver *driver, unsigned int bank, uint32_t mask)
{
	driver->regs->banks[bank].enable = mask;
}

void esm_configure_counter(struct esm_driver *driver, uint32_t counter)
{
	driver->regs->error_counter = counter;
}

void esm_inject_enable(struct esm_driver *driver)
{
	driver->regs->control |= ESM_CTRL_INJECT_ENABLE;
}

void esm_inject_disable(struct esm_driver *driver)
{
	driver->regs->control &= ~ESM_CTRL_INJECT_ENABLE;
}

void esm_inject_set(struct esm_driver *driver, unsigned int bank, uint32_t bits)
{
	driver->regs->banks[bank].inject |= bits;
}

void esm_inject_clear(struct esm_driver *driver, unsigned int bank, uint32_t bits)
{
	driver->regs->banks[bank].inject &= ~bits;
}

uint32_t esm_status(struct esm_driver *driver)
{
	return driver->regs->status;
}

uint32_t esm_raw(struct esm_driver *driver, unsigned int bank)
{
	return driver->regs->banks[bank].raw;
}

uint32_t esm_pending(struct esm_driver *driver, unsigned int bank)
{
	return driver->regs->banks[bank].pending;
}

void esm_clear(struct esm_driver *driver, unsigned int bank, uint32_t mask)
{
	driver->regs->banks[bank].pending = mask;
}
