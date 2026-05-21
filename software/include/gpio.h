/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_GPIO_H
#define ELEMENTS_GPIO_H

#ifndef GPIO_BANK_WIDTH
#define GPIO_BANK_WIDTH 32
#endif

#ifndef GPIO_MAX_BANKS
#define GPIO_MAX_BANKS 16
#endif

struct gpio_bank_regs {
	unsigned int input;
	unsigned int output;
	unsigned int direction;
	unsigned int irq_high_pending;
	unsigned int irq_high_enable;
	unsigned int irq_low_pending;
	unsigned int irq_low_enable;
	unsigned int irq_rising_pending;
	unsigned int irq_rising_enable;
	unsigned int irq_falling_pending;
	unsigned int irq_falling_enable;
};

struct gpio_regs {
	unsigned int ip_header;
	unsigned int ip_version;
	unsigned int ip_info;
	struct gpio_bank_regs banks[GPIO_MAX_BANKS];
};

struct gpio_driver {
	volatile struct gpio_regs *regs;
};

#define GPIO_IRQ_LEVEL_HIGH		(1 << 0)
#define GPIO_IRQ_LEVEL_LOW		(1 << 1)
#define GPIO_IRQ_RISING_EDGE		(1 << 2)
#define GPIO_IRQ_FALLING_EDGE		(1 << 3)

int gpio_init(struct gpio_driver *driver, unsigned long base_address);
unsigned int gpio_value_get(struct gpio_driver *driver, unsigned int pin);
void gpio_value_set(struct gpio_driver *driver, unsigned int pin);
void gpio_value_clr(struct gpio_driver *driver, unsigned int pin);
void gpio_dir_set(struct gpio_driver *driver, unsigned int pin);
void gpio_dir_clr(struct gpio_driver *driver, unsigned int pin);
int gpio_irq_enable(struct gpio_driver *driver, unsigned int pin, int flags);
int gpio_irq_disable(struct gpio_driver *driver, unsigned int pin, int flags);
int gpio_irq_ready(struct gpio_driver *driver, unsigned int pin, int flags);

#endif
