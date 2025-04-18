#ifndef ELEMNETS_UART
#define ELEMNETS_UART

struct uart_regs {
	unsigned int ip_api;
	unsigned int ip_version;
	unsigned int ip_widths;
	unsigned int ip_sampling_sizes;
	unsigned int ip_fifo_depths;
	unsigned int ip_permission;
	unsigned int read_write;
	unsigned int status;
	unsigned int clock_div;
	unsigned int frame_cfg;
	unsigned int irq_transmit_trigger;
	unsigned int ip;
	unsigned int ie;
};

struct uart_driver {
	volatile struct uart_regs *regs;
};

#define UART_CALC_FREQUENCY(freq, baud, bits) (freq / baud / bits)

int uart_init(struct uart_driver *driver, unsigned int base_address,
	unsigned int frequency);
void uart_puts(struct uart_driver *driver, unsigned char *str);
void uart_putc(struct uart_driver *driver, unsigned char c);
int uart_getc(struct uart_driver *driver, unsigned char *c);
int uart_irq_rx_enable(struct uart_driver *driver);
int uart_irq_rx_disable(struct uart_driver *driver);
int uart_irq_rx_ready(struct uart_driver *driver);

#endif
