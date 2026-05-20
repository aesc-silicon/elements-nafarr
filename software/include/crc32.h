/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_CRC32_H
#define ELEMENTS_CRC32_H

/* info register bit masks */
#define CRC32_INFO_POLY_ORDER_MASK    (0xFF)
#define CRC32_INFO_INPUT_REFLECT      (1 << 8)
#define CRC32_INFO_OUTPUT_REFLECT     (1 << 9)
#define CRC32_INFO_XOROUT_PRESENT     (1 << 10)

struct crc32_regs {
	unsigned int ip_header;   /* 0x000 */
	unsigned int ip_version;  /* 0x004 */
	unsigned int info;        /* 0x008 - self-disclosure (read-only) */
	unsigned int control;     /* 0x00C - write any to init/reset CRC state */
	unsigned int data;        /* 0x010 - write-only, fold one 32-bit word */
	unsigned int result;      /* 0x014 - read-only, crc32_state XOR xor_out */
	unsigned int xor_out;     /* 0x018 - R/W, optional (reads 0 if absent) */
};

struct crc32_driver {
	volatile struct crc32_regs *regs;
};

int crc32_init_driver(struct crc32_driver *driver, unsigned int base_address);

/**
 * Reset the CRC state to the polynomial init value.
 */
void crc32_init(struct crc32_driver *driver);

/**
 * Fold one 32-bit word into the CRC state.
 */
void crc32_write(struct crc32_driver *driver, unsigned int word);

/**
 * Read the current CRC result (crc32_state XOR xor_out).
 */
unsigned int crc32_read(struct crc32_driver *driver);

/**
 * Read the info register to determine the compiled configuration.
 */
unsigned int crc32_info(struct crc32_driver *driver);

/**
 * Read the xorOut register. Returns 0 when not present.
 */
unsigned int crc32_get_xorout(struct crc32_driver *driver);

/**
 * Write the xorOut register. No-op when not present in hardware.
 */
void crc32_set_xorout(struct crc32_driver *driver, unsigned int value);

#endif
