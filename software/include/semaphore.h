/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_SEMAPHORE_H
#define ELEMENTS_SEMAPHORE_H

#ifndef SEMAPHORE_MAX_COUNT
#define SEMAPHORE_MAX_COUNT 32
#endif

struct semaphore_regs {
	unsigned int ip_header;          /* 0x000 */
	unsigned int ip_version;         /* 0x004 */
	unsigned int info;               /* 0x008 — number of slots (read-only) */
	unsigned int status;             /* 0x00C — taken bitmask (read-only) */
	unsigned int semaphore[SEMAPHORE_MAX_COUNT]; /* 0x010 .. */
};

struct semaphore_driver {
	volatile struct semaphore_regs *regs;
	unsigned int count;
};

int semaphore_init(struct semaphore_driver *driver, unsigned int base_address,
		   unsigned int count);

/**
 * Attempt to claim semaphore slot @n.
 *
 * Returns 0 if the slot was free and is now held by the caller.
 * Returns 1 if the slot was already taken; the caller must retry.
 */
int semaphore_claim(struct semaphore_driver *driver, unsigned int n);

/**
 * Release semaphore slot @n.
 */
void semaphore_release(struct semaphore_driver *driver, unsigned int n);

/**
 * Return the raw taken bitmask (bit N set means slot N is held).
 */
unsigned int semaphore_status(struct semaphore_driver *driver);

#endif
