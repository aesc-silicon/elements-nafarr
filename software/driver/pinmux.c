/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "pinmux.h"

void pinmux_init(struct pinmux_driver *driver, unsigned long base_address)
{
	driver->regs = (struct pinmux_regs *)base_address;
	driver->pin_count = PINMUX_PIN_COUNT(driver->regs->info);
	driver->options_count = PINMUX_OPTIONS_COUNT(driver->regs->info);
}

unsigned int pinmux_pin_count(struct pinmux_driver *driver)
{
	return driver->pin_count;
}

unsigned int pinmux_options_count(struct pinmux_driver *driver)
{
	return driver->options_count;
}

void pinmux_set(struct pinmux_driver *driver, unsigned int pin, unsigned int option)
{
	if (pin >= driver->pin_count)
		return;
	driver->regs->option[pin] = option;
}

unsigned int pinmux_get(struct pinmux_driver *driver, unsigned int pin)
{
	if (pin >= driver->pin_count)
		return 0;
	return driver->regs->option[pin];
}
