/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_UART_H
#define ELEMENTS_UART_H

struct uart_regs {
	unsigned int ip_header;          /* 0x000 — IP identification header */
	unsigned int ip_version;         /* 0x004 — IP identification version */
	unsigned int data_width;         /* 0x008 — clock divider width and data width range */
	unsigned int sampling_size;      /* 0x00C — pre/post/majority sampling sizes */
	unsigned int fifo_depth;         /* 0x010 — TX/RX FIFO depths */
	unsigned int permissions;        /* 0x014 — bus write permission flags */
	unsigned int read_write;         /* 0x018 — TX write / RX read (bit 16 = RX valid) */
	unsigned int fifo_status;        /* 0x01C — [23:16]=TX vacancy, [31:24]=RX occupancy */
	unsigned int clock_div;          /* 0x020 — clock divider value */
	unsigned int frame_cfg;          /* 0x024 — [7:0]=data length, [15:8]=parity, [23:16]=stop */
	unsigned int transmit_trigger;   /* 0x028 — TX FIFO occupancy threshold for TX IRQ */
	unsigned int irq_pending;        /* 0x02C — IRQ pending (W1C): [0]=TX, [1]=RX, [2]=TX idle */
	unsigned int irq_enable;         /* 0x030 — IRQ enable mask */
	unsigned int error_pending;      /* 0x034 — error pending (W1C): [0]=framing, [1]=parity, [2]=overflow */
	unsigned int error_enable;       /* 0x038 — error enable mask */
};

struct uart_driver {
	volatile struct uart_regs *regs;
};

#define UART_CALC_FREQUENCY(freq, baud, bits) (freq / baud / bits)

/* IRQ pending/enable bits */
#define UART_IRQ_TX		(1U << 0)
#define UART_IRQ_RX		(1U << 1)
#define UART_IRQ_TX_IDLE	(1U << 2)

/* error pending/enable bits */
#define UART_ERROR_FRAMING	(1U << 0)
#define UART_ERROR_PARITY	(1U << 1)
#define UART_ERROR_OVERFLOW	(1U << 2)

int uart_init(struct uart_driver *driver, unsigned long base_address,
	unsigned int frequency);
void uart_puts(struct uart_driver *driver, unsigned char *str);
void uart_putc(struct uart_driver *driver, unsigned char c);
int uart_getc(struct uart_driver *driver, unsigned char *c);
int uart_irq_rx_enable(struct uart_driver *driver);
int uart_irq_rx_disable(struct uart_driver *driver);
int uart_irq_rx_ready(struct uart_driver *driver);

#endif
