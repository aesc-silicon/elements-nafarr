/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_PRNG_H
#define ELEMENTS_PRNG_H

#define PRNG_ERROR_ZERO_SEED  (1 << 0)

struct prng_regs {
	unsigned int ip_header;      /* 0x000 */
	unsigned int ip_version;     /* 0x004 */
	unsigned int control;        /* 0x008 - bit 0 = enable */
	unsigned int error_pending;  /* 0x00C - bit 0 = zero seed (W1C) */
	unsigned int error_mask;     /* 0x010 - bit 0 = enable zero-seed error output */
	unsigned int seed;           /* 0x014 - write-only, non-zero */
	unsigned int output;         /* 0x018 - read-only */
};

struct prng_driver {
	volatile struct prng_regs *regs;
};

int prng_init(struct prng_driver *driver, unsigned int base_address);

void prng_enable(struct prng_driver *driver);
void prng_disable(struct prng_driver *driver);

/**
 * Reseed the PRNG. Returns 1 on success, 0 if seed is zero (invalid).
 */
int prng_seed(struct prng_driver *driver, unsigned int seed);

unsigned int prng_read(struct prng_driver *driver);

int prng_error_pending(struct prng_driver *driver, int flags);
void prng_error_clear(struct prng_driver *driver, int flags);
void prng_error_mask(struct prng_driver *driver, int flags);
void prng_error_unmask(struct prng_driver *driver, int flags);

#endif
