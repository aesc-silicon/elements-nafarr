/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_CLOCK_H
#define ELEMENTS_CLOCK_H

struct clock_regs {
	unsigned int ip_header;   /* 0x000 — IP identification header */
	unsigned int ip_version;  /* 0x004 — IP identification version */
	unsigned int domains;     /* 0x008 — number of clock domains (read-only) */
	unsigned int enable;      /* 0x00C — enabled domain bitmask (1=enabled) */
};

struct clock_driver {
	volatile struct clock_regs *regs;
};

void clock_init(struct clock_driver *driver, unsigned long base_address);

unsigned int clock_domain_count(struct clock_driver *driver);
unsigned int clock_enable_get(struct clock_driver *driver);
void clock_enable_set(struct clock_driver *driver, unsigned int mask);

#endif
