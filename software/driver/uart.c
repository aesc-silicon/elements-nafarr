/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "uart.h"

int uart_init(struct uart_driver *driver, unsigned long base_address,
		unsigned int frequency)
{
	driver->regs = (struct uart_regs *)base_address;
	volatile struct uart_regs *uart = driver->regs;

	uart->clock_div = frequency;
	uart->frame_cfg = 7;

	return 1;
}

void uart_puts(struct uart_driver *driver, unsigned char *str)
{
	while(*str != '\0') {
		uart_putc(driver, *str);
		str++;
	}
}

void uart_putc(struct uart_driver *driver, unsigned char c)
{
	volatile struct uart_regs *uart = driver->regs;

	/* wait for TX FIFO vacancy ([23:16] nonzero) */
	while ((uart->fifo_status & 0x00FF0000) == 0);
	uart->read_write = c;
}

int uart_getc(struct uart_driver *driver, unsigned char *c)
{
	volatile struct uart_regs *uart = driver->regs;
	unsigned int val;

	val = uart->read_write;
	if (val & 0x10000) {
		*c = val & 0xFF;
		return 0;
	}

	return -1;
}

int uart_irq_rx_enable(struct uart_driver *driver)
{
	volatile struct uart_regs *uart = driver->regs;

	uart->irq_enable |= UART_IRQ_RX;

	return 1;
}

int uart_irq_rx_disable(struct uart_driver *driver)
{
	volatile struct uart_regs *uart = driver->regs;

	uart->irq_enable &= ~UART_IRQ_RX;

	return 1;
}

int uart_irq_rx_ready(struct uart_driver *driver)
{
	volatile struct uart_regs *uart = driver->regs;

	return !!(uart->irq_pending & UART_IRQ_RX);
}
