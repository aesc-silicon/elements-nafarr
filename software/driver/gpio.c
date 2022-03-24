#include "gpio.h"
#include "soc.h"

int gpio_init(void)
{
	volatile struct gpio *gpio = (struct gpio *)GPIO_BASE;

	return 1;
}

unsigned int gpio_value_get(unsigned int pin)
{
	volatile struct gpio_regs *gpio = (struct gpio_regs *)GPIO_BASE;
	unsigned int val;

	val = gpio->data_in >> pin;

	return val & 0x1;
}

void gpio_value_set(unsigned int pin)
{
	volatile struct gpio_regs *gpio = (struct gpio_regs *)GPIO_BASE;
	unsigned int val;

	val = 1 << pin;
	val = gpio->data_out | val;

	gpio->data_out = val;
}

void gpio_value_clr(unsigned int pin)
{
	volatile struct gpio_regs *gpio = (struct gpio_regs *)GPIO_BASE;
	unsigned int val;

	val = 1 << pin;
	val = gpio->data_out & ~val;

	gpio->data_out = val;
}

void gpio_dir_set(unsigned int pin)
{
	volatile struct gpio_regs *gpio = (struct gpio_regs *)GPIO_BASE;
	unsigned int val;

	val = 1 << pin;
	val = gpio->dir_en | val;

	gpio->dir_en = val;
}

void gpio_dir_clr(unsigned int pin)
{
	volatile struct gpio_regs *gpio = (struct gpio_regs *)GPIO_BASE;
	unsigned int val;

	val = 1 << pin;
	val = gpio->dir_en & ~val;

	gpio->dir_en = val;
}

