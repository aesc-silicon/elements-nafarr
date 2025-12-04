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
	volatile struct gpio_regs *gpio = driver->regs;
	unsigned int val;

	val = gpio->data_in >> pin;

	return val & 0x1;
}

void gpio_value_set(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_regs *gpio = driver->regs;
	unsigned int val;

	val = 1 << pin;
	val = gpio->data_out | val;

	gpio->data_out = val;
}

void gpio_value_clr(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_regs *gpio = driver->regs;
	unsigned int val;

	val = 1 << pin;
	val = gpio->data_out & ~val;

	gpio->data_out = val;
}

void gpio_dir_set(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_regs *gpio = driver->regs;
	unsigned int val;

	val = 1 << pin;
	val = gpio->dir_en | val;

	gpio->dir_en = val;
}

void gpio_dir_clr(struct gpio_driver *driver, unsigned int pin)
{
	volatile struct gpio_regs *gpio = driver->regs;
	unsigned int val;

	val = 1 << pin;
	val = gpio->dir_en & ~val;

	gpio->dir_en = val;
}

int gpio_irq_enable(struct gpio_driver *driver, unsigned int pin, int flags)
{
	volatile struct gpio_regs *gpio = driver->regs;

	if (flags & GPIO_IRQ_LEVEL_HIGH) {
		gpio->irq_high_pending |= 1 << pin;
		gpio->irq_high_mask |= 1 << pin;
	}
	if (flags & GPIO_IRQ_LEVEL_LOW) {
		gpio->irq_low_pending |= 1 << pin;
		gpio->irq_low_mask |= 1 << pin;
	}
	if (flags & GPIO_IRQ_RISING_EDGE) {
		gpio->irq_rising_pending |= 1 << pin;
		gpio->irq_rising_mask |= 1 << pin;
	}
	if (flags & GPIO_IRQ_FALLING_EDGE) {
		gpio->irq_falling_pending |= 1 << pin;
		gpio->irq_falling_mask |= 1 << pin;
	}

	return 1;
}

int gpio_irq_disable(struct gpio_driver *driver, unsigned int pin, int flags)
{
	volatile struct gpio_regs *gpio = driver->regs;

	if (flags & GPIO_IRQ_LEVEL_HIGH)
		gpio->irq_high_mask &= ~(1 << pin);
	if (flags & GPIO_IRQ_LEVEL_LOW)
		gpio->irq_low_mask &= ~(1 << pin);
	if (flags & GPIO_IRQ_RISING_EDGE)
		gpio->irq_rising_mask &= ~(1 << pin);
	if (flags & GPIO_IRQ_FALLING_EDGE)
		gpio->irq_falling_mask &= ~(1 << pin);

	return 1;
}

int gpio_irq_ready(struct gpio_driver *driver, unsigned int pin, int flags)
{
	volatile struct gpio_regs *gpio = driver->regs;
	int ret = 0;

	if (flags & GPIO_IRQ_LEVEL_HIGH)
		ret |= !!(gpio->irq_high_pending & (1 << pin));
	if (flags & GPIO_IRQ_LEVEL_LOW)
		ret |= !!(gpio->irq_low_pending & (1 << pin));
	if (flags & GPIO_IRQ_RISING_EDGE)
		ret |= !!(gpio->irq_rising_pending & (1 << pin));
	if (flags & GPIO_IRQ_FALLING_EDGE)
		ret |= !!(gpio->irq_falling_pending & (1 << pin));

	return ret;
}
