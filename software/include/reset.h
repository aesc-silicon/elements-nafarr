/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_RESET_H
#define ELEMENTS_RESET_H

struct reset_regs {
	unsigned int ip_header;    /* 0x000 — IP identification header */
	unsigned int ip_version;   /* 0x004 — IP identification version */
	unsigned int domains;      /* 0x008 — number of reset domains (read-only) */
	unsigned int enable;       /* 0x00C — enabled domain bitmask (1=enabled) */
	unsigned int trigger;      /* 0x010 — trigger bitmask; write 1 to assert reset */
	unsigned int acknowledge;  /* 0x014 — write any value to deassert pending resets */
};

struct reset_driver {
	volatile struct reset_regs *regs;
};

void reset_init(struct reset_driver *driver, unsigned long base_address);

unsigned int reset_domain_count(struct reset_driver *driver);
unsigned int reset_enable_get(struct reset_driver *driver);
void reset_enable_set(struct reset_driver *driver, unsigned int mask);
void reset_trigger(struct reset_driver *driver, unsigned int mask);
void reset_acknowledge(struct reset_driver *driver);

#endif
