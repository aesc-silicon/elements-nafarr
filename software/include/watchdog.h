/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_WATCHDOG_H
#define ELEMENTS_WATCHDOG_H

#include <stdint.h>

#ifndef WATCHDOG_COUNT
#define WATCHDOG_COUNT 1
#endif

/* control register bits */
#define WATCHDOG_CTRL_ENABLE  (1u << 0)
#define WATCHDOG_CTRL_LOCK    (1u << 1)

/* status register bits */
#define WATCHDOG_STATUS_ENABLED   (1u << 0)
#define WATCHDOG_STATUS_LOCKED    (1u << 1)
#define WATCHDOG_STATUS_IN_WINDOW (1u << 2)

/*
 * Interrupt source bits (irq_pending / irq_mask).
 * Bits 2 and 3 are only present when the IP is compiled with windowed = true.
 *
 * A single hardware interrupt line is asserted when any enabled source fires.
 * In the ISR, read irq_pending per watchdog to identify the source and write
 * 1 to the corresponding bit to clear it.
 */
#define WATCHDOG_IRQ_TIMEOUT_IRQ     (1u << 0)
#define WATCHDOG_IRQ_TIMEOUT_ERR     (1u << 1)
#define WATCHDOG_IRQ_VIOLATION_IRQ   (1u << 2)
#define WATCHDOG_IRQ_VIOLATION_ERR   (1u << 3)

struct watchdog_wdt_regs {
	uint32_t control;     /* +0x00 - enable[0], lock[1]         */
	uint32_t prescaler;   /* +0x04 - tick = f_clk / (val + 1)   */
	uint32_t timeout;     /* +0x08 - fires after (val + 1) ticks */
	uint32_t window_open; /* +0x0C - kick valid when cnt <= val  */
	uint32_t status;      /* +0x10 - read-only                   */
	uint32_t irq_pending; /* +0x14 - write 1 to clear            */
	uint32_t irq_mask;    /* +0x18 - 1 = enable source           */
	uint32_t kick;        /* +0x1C - write any value to kick      */
};

struct watchdog_regs {
	uint32_t ip_header;                            /* 0x000 */
	uint32_t ip_version;                           /* 0x004 */
	uint32_t info;                                 /* 0x008 */
	struct watchdog_wdt_regs wdt[WATCHDOG_COUNT];  /* 0x00C */
};

struct watchdog_driver {
	volatile struct watchdog_regs *regs;
};

int watchdog_init(struct watchdog_driver *driver, unsigned int base_address);

/**
 * Configure prescaler and timeout for watchdog @wdt.
 * Must be called before enabling or locking.
 *
 * @prescaler: divide-by value; tick rate = f_clk / (prescaler + 1)
 * @timeout:   counter reload value; fires after (timeout + 1) ticks
 */
void watchdog_configure(struct watchdog_driver *driver, unsigned int wdt,
			uint32_t prescaler, uint32_t timeout);

/**
 * Set the window-open threshold for watchdog @wdt (windowed mode only).
 * A kick is valid when the counter is at or below @window_open.
 */
void watchdog_configure_window(struct watchdog_driver *driver, unsigned int wdt,
			       uint32_t window_open);

/**
 * Enable watchdog @wdt. The counter loads with the configured timeout value.
 */
void watchdog_enable(struct watchdog_driver *driver, unsigned int wdt);

/**
 * Disable watchdog @wdt. Has no effect when the lock bit is set.
 */
void watchdog_disable(struct watchdog_driver *driver, unsigned int wdt);

/**
 * Lock watchdog @wdt. Once locked, disable and configuration writes are
 * ignored until the next hardware reset.
 */
void watchdog_lock(struct watchdog_driver *driver, unsigned int wdt);

/**
 * Kick watchdog @wdt to prevent a timeout.
 * In windowed mode, kicking outside the window raises a window violation.
 */
void watchdog_kick(struct watchdog_driver *driver, unsigned int wdt);

uint32_t watchdog_status(struct watchdog_driver *driver, unsigned int wdt);

/**
 * Interrupt control.
 * @flags: bitmask of WATCHDOG_IRQ_* values
 */
void     watchdog_irq_enable(struct watchdog_driver *driver, unsigned int wdt,
			     uint32_t flags);
void     watchdog_irq_disable(struct watchdog_driver *driver, unsigned int wdt,
			      uint32_t flags);
int      watchdog_irq_pending(struct watchdog_driver *driver, unsigned int wdt,
			      uint32_t flags);
void     watchdog_irq_clear(struct watchdog_driver *driver, unsigned int wdt,
			    uint32_t flags);

#endif
