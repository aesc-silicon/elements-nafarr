/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_CLOCK_H
#define ELEMENTS_CLOCK_H

/* CTRL register bit fields (one CTRL register per clock domain). */
#define CLOCK_CTRL_ENABLE	(1u << 31)  /* RW: 1 = domain clock running */
#define CLOCK_CTRL_LOCK		(1u << 30)  /* RO: 1 = source locked/ready */

struct clock_domain_regs {
	unsigned int control;     /* +0x0 — bit31 enable (RW), bit30 lock (RO) */
	unsigned int ratio;       /* +0x4 — bits31:16 mult, bits15:0 div (RO) */
};

struct clock_regs {
	unsigned int ip_header;   /* 0x000 — IP identification header */
	unsigned int ip_version;  /* 0x004 — IP identification version */
	unsigned int domains;     /* 0x008 — number of clock domains (read-only) */
	unsigned int reserved;    /* 0x00C — reserved */
	struct clock_domain_regs domain[]; /* 0x010 — per-domain block, stride 8 */
};

struct clock_driver {
	volatile struct clock_regs *regs;
	unsigned int reference_hz; /* reference clock, read from syscon */
};

void clock_init(struct clock_driver *driver, unsigned long base_address,
		unsigned int reference_hz);

unsigned int clock_domain_count(struct clock_driver *driver);

/* Rate of a domain in Hz: reference_hz * mult / div. */
unsigned int clock_get_rate(struct clock_driver *driver, unsigned int index);

int clock_is_enabled(struct clock_driver *driver, unsigned int index);
int clock_is_locked(struct clock_driver *driver, unsigned int index);

/* Gating critical domains (e.g. system) is ignored by hardware. */
void clock_enable(struct clock_driver *driver, unsigned int index);
void clock_disable(struct clock_driver *driver, unsigned int index);

#endif
