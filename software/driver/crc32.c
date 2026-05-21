/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "crc32.h"

int crc32_init_driver(struct crc32_driver *driver, unsigned long base_address)
{
	driver->regs = (volatile struct crc32_regs *)base_address;

	return 1;
}

void crc32_init(struct crc32_driver *driver)
{
	driver->regs->control = 1;
}

void crc32_write(struct crc32_driver *driver, unsigned int word)
{
	driver->regs->data = word;
}

unsigned int crc32_read(struct crc32_driver *driver)
{
	return driver->regs->result;
}

unsigned int crc32_info(struct crc32_driver *driver)
{
	return driver->regs->info;
}

unsigned int crc32_get_xorout(struct crc32_driver *driver)
{
	return driver->regs->xor_out;
}

void crc32_set_xorout(struct crc32_driver *driver, unsigned int value)
{
	driver->regs->xor_out = value;
}
