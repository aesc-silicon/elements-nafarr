#include "uart.h"
#include "soc.h"

int uart_init(void)
{
	volatile struct uart_regs *uart = (struct uart_regs *)UART_BASE;

	uart->clock_div = UART_FREQ / 115200 / 8;
	uart->frame_cfg = 7;

	return 1;
}

void uart_puts(unsigned char *str)
{
	while(*str != '\0') {
		uart_putc(*str);
		str++;
	}
}

void uart_putc(unsigned char c)
{
	volatile struct uart_regs *uart = (struct uart_regs *)UART_BASE;

	while ((uart->status & 0x00FF0000) == 0);
	uart->read_write = c;
}

int uart_getc(unsigned char *c)
{
	volatile struct uart_regs *uart = (struct uart_regs *)UART_BASE;
	int val;

	val = uart->read_write;
	if (val & 0x10000) {
		*c = val & 0xFF;
		return 0;
	}

	return -1;
}
