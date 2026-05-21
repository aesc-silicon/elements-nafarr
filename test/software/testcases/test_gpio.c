/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "gpio.h"

int main(void)
{
	struct gpio_driver driver;
	static volatile struct gpio_regs fake_regs;

	gpio_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	gpio_value_get(&driver, 0);
	gpio_value_set(&driver, 0);
	gpio_value_clr(&driver, 0);
	gpio_dir_set(&driver, 0);
	gpio_dir_clr(&driver, 0);
	gpio_irq_enable(&driver, 0, GPIO_IRQ_LEVEL_HIGH | GPIO_IRQ_RISING_EDGE);
	gpio_irq_disable(&driver, 0, GPIO_IRQ_LEVEL_HIGH | GPIO_IRQ_RISING_EDGE);
	gpio_irq_ready(&driver, 0, GPIO_IRQ_LEVEL_HIGH | GPIO_IRQ_RISING_EDGE);

	return 0;
}
