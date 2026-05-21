/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_TIMER_H
#define ELEMENTS_TIMER_H

struct timer_regs {
	unsigned int ip_header;   /* 0x000 */
	unsigned int ip_version;  /* 0x004 */
	unsigned int info;        /* 0x008 */
	unsigned int irq_pending; /* 0x00C — W1C */
	unsigned int irq_mask;    /* 0x010 */
};

/* info register fields */
#define TIMER_INFO_COUNT(r)          ((r) & 0xFF)
#define TIMER_INFO_CHANNEL_COUNT(r)  (((r) >> 8) & 0xFF)
#define TIMER_INFO_WIDTH(r)          (((r) >> 16) & 0xFF)
#define TIMER_INFO_PRESCALER_WIDTH(r)(((r) >> 24) & 0xFF)

/* control register fields */
#define TIMER_CTRL_ENABLE            (1U << 0)
#define TIMER_CTRL_MODE_FREE_RUN     (0U << 1)
#define TIMER_CTRL_MODE_PERIODIC     (1U << 1)
#define TIMER_CTRL_MODE_ONE_SHOT     (2U << 1)

/* interrupt bit for timer t with channel_count channels per timer */
#define TIMER_IRQ_OVERFLOW(t, cc)    (1U << ((t) * (1 + (cc))))
#define TIMER_IRQ_COMPARE(t, cc, ch) (1U << ((t) * (1 + (cc)) + 1 + (ch)))

struct timer_driver {
	volatile struct timer_regs *regs;
	unsigned int count;
	unsigned int channel_count;
	unsigned int stride; /* 0x10 + channel_count * 4 */
};

/*
 * Initialise the driver. Reads count and channel_count from the info register.
 * base_address: MMIO base of the timer IP.
 */
void timer_init(struct timer_driver *driver, unsigned int base_address);

/* Per-timer register access */
void         timer_set_control(struct timer_driver *driver, unsigned int t, unsigned int ctrl);
unsigned int timer_get_control(struct timer_driver *driver, unsigned int t);
void         timer_set_prescaler(struct timer_driver *driver, unsigned int t, unsigned int val);
void         timer_set_reload(struct timer_driver *driver, unsigned int t, unsigned int val);
unsigned int timer_get_counter(struct timer_driver *driver, unsigned int t);
void         timer_set_counter(struct timer_driver *driver, unsigned int t, unsigned int val);
void         timer_set_compare(struct timer_driver *driver, unsigned int t, unsigned int ch,
                               unsigned int val);

/* Interrupt helpers */
unsigned int timer_irq_pending(struct timer_driver *driver);
void         timer_irq_clear(struct timer_driver *driver, unsigned int mask);
void         timer_irq_enable(struct timer_driver *driver, unsigned int mask);
void         timer_irq_disable(struct timer_driver *driver, unsigned int mask);

#endif
