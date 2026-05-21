/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "uart.h"

int main(void)
{
	struct uart_driver driver;
	static volatile struct uart_regs fake_regs;

	/* uart_init writes registers immediately; pass the fake address */
	uart_init(&driver, (unsigned long)&fake_regs, 115200);

	/* set TX vacancy so uart_putc does not spin */
	fake_regs.fifo_status = 0x00FF0000;

	uart_putc(&driver, 'A');
	uart_puts(&driver, (unsigned char *)"ok");

	unsigned char c;
	uart_getc(&driver, &c);

	uart_irq_rx_enable(&driver);
	uart_irq_rx_disable(&driver);
	uart_irq_rx_ready(&driver);

	return 0;
}
