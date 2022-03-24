#ifndef BOOTROM_UART
#define BOOTROM_UART

struct uart_regs {
	unsigned int read_write;
	unsigned int status;
	unsigned int clock_div;
	unsigned int frame_cfg;
	unsigned int ip;
	unsigned int ie;
};

int uart_init(void);
void uart_puts(unsigned char *str);
void uart_putc(unsigned char c);
int uart_getc(unsigned char *c);

#endif
