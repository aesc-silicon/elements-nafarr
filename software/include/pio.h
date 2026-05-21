/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_PIO_H
#define ELEMENTS_PIO_H

/*
 * Register layout (all offsets from base):
 *   0x000  ip_header        — IP identification header
 *   0x004  ip_version       — IP identification version
 *   0x008  data_width       — [7:0]=pin_count, [15:8]=data_width, [23:16]=clk_div_width, [31:24]=read_buf_depth
 *   0x00C  fifo_depth       — [7:0]=cmd_fifo_depth, [15:8]=read_fifo_depth
 *   0x010  permissions      — [0]=bus_can_write_clock_div
 *   0x014  control          — [0]=enable, [1]=stop_at_loop
 *   0x018  read_write       — write: program command word; read: [15:0]=result, [16]=valid
 *   0x01C  fifo_status      — read: [7:0]=exec_ptr, [15:8]=write_ptr, [31:24]=rx_occupancy
 *                             write: any value resets program write pointer
 *   0x020  clock_div        — clock divider value
 *   0x024  read_delay       — read delay in clock ticks
 *   0x028  error_pending    — [0]=read FIFO overflow (W1C)
 *   0x02C  error_enable     — error enable mask
 *   0x030  irq_pending      — [0]=rx data ready, [1]=loop done (W1C)
 *   0x034  irq_enable       — interrupt enable mask
 */

struct pio_regs {
	unsigned int ip_header;     /* 0x000 */
	unsigned int ip_version;    /* 0x004 */
	unsigned int data_width;    /* 0x008 */
	unsigned int fifo_depth;    /* 0x00C */
	unsigned int permissions;   /* 0x010 */
	unsigned int control;       /* 0x014 */
	unsigned int read_write;    /* 0x018 */
	unsigned int fifo_status;   /* 0x01C */
	unsigned int clock_div;     /* 0x020 */
	unsigned int read_delay;    /* 0x024 */
	unsigned int error_pending; /* 0x028 */
	unsigned int error_enable;  /* 0x02C */
	unsigned int irq_pending;   /* 0x030 */
	unsigned int irq_enable;    /* 0x034 */
};

/* data_width register fields */
#define PIO_PIN_COUNT(reg)       ((reg) & 0xFF)
#define PIO_DATA_WIDTH(reg)      (((reg) >> 8) & 0xFF)
#define PIO_CLK_DIV_WIDTH(reg)   (((reg) >> 16) & 0xFF)
#define PIO_READ_BUF_DEPTH(reg)  (((reg) >> 24) & 0xFF)

/* fifo_depth register fields */
#define PIO_CMD_FIFO_DEPTH(reg)  ((reg) & 0xFF)
#define PIO_READ_FIFO_DEPTH(reg) (((reg) >> 8) & 0xFF)

/* control register bits */
#define PIO_CTRL_ENABLE          (1U << 0)
#define PIO_CTRL_STOP_AT_LOOP    (1U << 1)

/* fifo_status register fields */
#define PIO_EXEC_PTR(reg)        ((reg) & 0xFF)
#define PIO_WRITE_PTR(reg)       (((reg) >> 8) & 0xFF)
#define PIO_RX_OCCUPANCY(reg)    (((reg) >> 24) & 0xFF)

/* read_write read fields */
#define PIO_READ_VALID           (1U << 16)
#define PIO_READ_RESULT(reg)     ((reg) & 0x1)

/* error_pending / error_enable bits */
#define PIO_ERROR_RX_OVERFLOW    (1U << 0)

/* irq_pending / irq_enable bits */
#define PIO_IRQ_RX_READY         (1U << 0)
#define PIO_IRQ_LOOP_DONE        (1U << 1)

/* Command types */
#define PIO_CMD_HIGH             0x0
#define PIO_CMD_HIGH_SET         0x1
#define PIO_CMD_LOW              0x2
#define PIO_CMD_LOW_SET          0x3
#define PIO_CMD_FLOAT            0x4
#define PIO_CMD_FLOAT_SET        0x5
#define PIO_CMD_TOGGLE           0x6
#define PIO_CMD_TOGGLE_SET       0x7
#define PIO_CMD_WAIT             0x8
#define PIO_CMD_WAIT_FOR_HIGH    0x9
#define PIO_CMD_WAIT_FOR_LOW     0xA
#define PIO_CMD_READ             0xB
#define PIO_CMD_LOOP             0xC

/* Build a 32-bit command word: [3:0]=cmd, [7:4]=pin, [31:8]=data */
#define PIO_MAKE_CMD(cmd, pin, data) \
	(((unsigned int)(data) << 8) | (((unsigned int)(pin) & 0xF) << 4) | ((unsigned int)(cmd) & 0xF))

struct pio_driver {
	volatile struct pio_regs *regs;
};

void pio_init(struct pio_driver *driver, unsigned long base_address);

void pio_enable(struct pio_driver *driver);
void pio_disable(struct pio_driver *driver);
void pio_set_stop_at_loop(struct pio_driver *driver, int enable);

void pio_program_reset(struct pio_driver *driver);
void pio_program_write(struct pio_driver *driver, unsigned int cmd_word);

int pio_read(struct pio_driver *driver, unsigned int *result);

void pio_irq_enable(struct pio_driver *driver, unsigned int mask);
void pio_irq_disable(struct pio_driver *driver, unsigned int mask);
unsigned int pio_irq_pending(struct pio_driver *driver);
void pio_irq_clear(struct pio_driver *driver, unsigned int mask);

#endif
