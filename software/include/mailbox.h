/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_MAILBOX_H
#define ELEMENTS_MAILBOX_H

#include <stdint.h>

#ifndef MAILBOX_CHANNEL_COUNT
#define MAILBOX_CHANNEL_COUNT 2
#endif

/*
 * Status register bit helpers. The hardware packs empty flags in the low
 * MAILBOX_CHANNEL_COUNT bits and full flags in the next MAILBOX_CHANNEL_COUNT
 * bits, so the full base shifts by the channel count.
 */
#define MAILBOX_STATUS_EMPTY(ch)  (1u << (ch))
#define MAILBOX_STATUS_FULL(ch)   (1u << (MAILBOX_CHANNEL_COUNT + (ch)))

/* Per-channel interrupt source bits (irq_pending / irq_mask registers). */
#define MAILBOX_IRQ_NOT_EMPTY  (1u << 0)
#define MAILBOX_IRQ_NOT_FULL   (1u << 1)

struct mailbox_channel_regs {
	uint32_t write;        /* 0x00 - push data (write-only) */
	uint32_t read;         /* 0x04 - pop data (read-only)   */
	uint32_t occupancy;    /* 0x08 */
	uint32_t irq_pending;  /* 0x0C - write 1 to clear */
	uint32_t irq_mask;     /* 0x10 - 1 = enable source */
};

struct mailbox_regs {
	uint32_t ip_header;                                      /* 0x000 */
	uint32_t ip_version;                                     /* 0x004 */
	uint32_t info;                                           /* 0x008 */
	uint32_t status;                                         /* 0x00C */
	struct mailbox_channel_regs channel[MAILBOX_CHANNEL_COUNT]; /* 0x010 */
};

struct mailbox_driver {
	volatile struct mailbox_regs *regs;
};

int          mailbox_init(struct mailbox_driver *driver, unsigned long base_address);

/**
 * Push a 32-bit message to channel @ch.
 * Returns 1 on success, 0 if the FIFO is full.
 */
int          mailbox_write(struct mailbox_driver *driver, unsigned int ch,
			   unsigned int data);

/**
 * Pop a 32-bit message from channel @ch and store it in @data.
 * Returns 1 if a message was available, 0 if the FIFO was empty.
 */
int          mailbox_read(struct mailbox_driver *driver, unsigned int ch,
			  unsigned int *data);

unsigned int mailbox_status(struct mailbox_driver *driver);
unsigned int mailbox_occupancy(struct mailbox_driver *driver, unsigned int ch);

/**
 * Interrupt control. The hardware ORs all masked pending bits across every
 * channel into a single interrupt output line. In the ISR, call
 * mailbox_irq_pending() for each channel to find the source, then
 * mailbox_irq_clear() to acknowledge it.
 *
 * @flags: bitmask of MAILBOX_IRQ_NOT_EMPTY / MAILBOX_IRQ_NOT_FULL
 */
int          mailbox_irq_enable(struct mailbox_driver *driver, unsigned int ch,
				int flags);
int          mailbox_irq_disable(struct mailbox_driver *driver, unsigned int ch,
				 int flags);
int          mailbox_irq_pending(struct mailbox_driver *driver, unsigned int ch,
				 int flags);
void         mailbox_irq_clear(struct mailbox_driver *driver, unsigned int ch,
			       int flags);

#endif
