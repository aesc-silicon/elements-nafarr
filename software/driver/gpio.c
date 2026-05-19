/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "gpio.h"

int gpio_init(struct gpio_driver *driver, unsigned int base_address)
{
	driver->regs = (struct gpio_regs *)base_address;

	return 1;
}

unsigned int gpio_value_get(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;

	return (bank->data_in >> bit) & 0x1;
}

void gpio_value_set(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;

	bank->data_out |= 1 << bit;
}

void gpio_value_clr(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;

	bank->data_out &= ~(1 << bit);
}

void gpio_dir_set(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;

	bank->dir_en |= 1 << bit;
}

void gpio_dir_clr(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;

	bank->dir_en &= ~(1 << bit);
}

int gpio_irq_enable(struct gpio_driver *driver, unsigned int pin, int flags)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;

	if (flags & GPIO_IRQ_LEVEL_HIGH) {
		bank->irq_high_pending |= 1 << bit;
		bank->irq_high_mask |= 1 << bit;
	}
	if (flags & GPIO_IRQ_LEVEL_LOW) {
		bank->irq_low_pending |= 1 << bit;
		bank->irq_low_mask |= 1 << bit;
	}
	if (flags & GPIO_IRQ_RISING_EDGE) {
		bank->irq_rising_pending |= 1 << bit;
		bank->irq_rising_mask |= 1 << bit;
	}
	if (flags & GPIO_IRQ_FALLING_EDGE) {
		bank->irq_falling_pending |= 1 << bit;
		bank->irq_falling_mask |= 1 << bit;
	}

	return 1;
}

int gpio_irq_disable(struct gpio_driver *driver, unsigned int pin, int flags)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;

	if (flags & GPIO_IRQ_LEVEL_HIGH)
		bank->irq_high_mask &= ~(1 << bit);
	if (flags & GPIO_IRQ_LEVEL_LOW)
		bank->irq_low_mask &= ~(1 << bit);
	if (flags & GPIO_IRQ_RISING_EDGE)
		bank->irq_rising_mask &= ~(1 << bit);
	if (flags & GPIO_IRQ_FALLING_EDGE)
		bank->irq_falling_mask &= ~(1 << bit);

	return 1;
}

int gpio_irq_ready(struct gpio_driver *driver, unsigned int pin, int flags)
{
	volatile struct gpio_bank_regs *bank = &driver->regs->banks[pin / GPIO_BANK_WIDTH];
	unsigned int bit = pin % GPIO_BANK_WIDTH;
	int ret = 0;

	if (flags & GPIO_IRQ_LEVEL_HIGH)
		ret |= !!(bank->irq_high_pending & (1 << bit));
	if (flags & GPIO_IRQ_LEVEL_LOW)
		ret |= !!(bank->irq_low_pending & (1 << bit));
	if (flags & GPIO_IRQ_RISING_EDGE)
		ret |= !!(bank->irq_rising_pending & (1 << bit));
	if (flags & GPIO_IRQ_FALLING_EDGE)
		ret |= !!(bank->irq_falling_pending & (1 << bit));

	return ret;
}
