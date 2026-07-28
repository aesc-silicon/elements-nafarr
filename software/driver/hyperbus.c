/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "hyperbus.h"

int hyperbus_init(struct hyperbus_driver *driver, unsigned long base_address)
{
	driver->regs = (volatile struct hyperbus_regs *)base_address;

	return 1;
}

void hyperbus_set_latency(struct hyperbus_driver *driver, uint32_t cycles)
{
	driver->regs->latency = cycles;
}

void hyperbus_reset(struct hyperbus_driver *driver)
{
	driver->regs->reset_trigger = 1;
}

void hyperbus_set_reset_timing(struct hyperbus_driver *driver, uint32_t pulse,
			       uint32_t halt)
{
	driver->regs->reset_pulse = pulse;
	driver->regs->reset_halt = halt;
}

void hyperbus_reg_write(struct hyperbus_driver *driver, uint16_t reg_addr,
			uint16_t value)
{
	driver->regs->reg_access = HYPERBUS_REG_ADDR(reg_addr) |
				   HYPERBUS_REG_WDATA(value);
}

int hyperbus_reg_read(struct hyperbus_driver *driver, uint16_t reg_addr,
		      uint16_t *value)
{
	uint32_t rsp;

	driver->regs->reg_access = HYPERBUS_REG_READ | HYPERBUS_REG_ADDR(reg_addr);

	do {
		rsp = driver->regs->reg_access;
	} while (!(rsp & HYPERBUS_REG_RSP_VALID));

	if (rsp & HYPERBUS_REG_RSP_ERROR) {
		return -1;
	}

	*value = HYPERBUS_REG_RSP_DATA(rsp);
	return 0;
}

uint32_t hyperbus_fifo_status(struct hyperbus_driver *driver)
{
	return driver->regs->fifo_status;
}

uint32_t hyperbus_error_pending(struct hyperbus_driver *driver)
{
	return driver->regs->error_pending;
}

void hyperbus_error_clear(struct hyperbus_driver *driver, uint32_t mask)
{
	driver->regs->error_pending = mask;
}

void hyperbus_error_mask(struct hyperbus_driver *driver, uint32_t mask)
{
	driver->regs->error_mask = mask;
}
