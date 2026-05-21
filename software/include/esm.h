/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_ESM_H
#define ELEMENTS_ESM_H

#include <stdint.h>

/* control register bits */
#define ESM_CTRL_ENABLE        (1u << 0)
#define ESM_CTRL_LOCK          (1u << 1)
#define ESM_CTRL_INJECT_ENABLE (1u << 2)

/* status register bits */
#define ESM_STATUS_COUNTER_ACTIVE (1u << 0)
#define ESM_STATUS_ERROR_SIGNAL   (1u << 1)

/*
 * Enable/pending register layout (one register covers all four levels for 8 inputs).
 *
 * Each 32-bit register is divided into four byte lanes, one per severity level:
 *   bits [31:24] = FATAL  for inputs [7:0] of the bank
 *   bits [23:16] = ERROR  for inputs [7:0] of the bank
 *   bits  [15:8] = WARN   for inputs [7:0] of the bank
 *   bits   [7:0] = INFO   for inputs [7:0] of the bank
 *
 * Macros to build a mask for the enable/pending registers:
 *   ESM_LEVEL_INFO/WARN/ERROR/FATAL(input_mask) — place input_mask in the right byte lane.
 *
 * Example: route inputs 0 and 1 to WARN and inputs 2 and 3 to FATAL:
 *   esm_configure(driver, bank, ESM_LEVEL_WARN(0x3) | ESM_LEVEL_FATAL(0xc));
 */
#define ESM_LEVEL_INFO(mask)  (((mask) & 0xFFu))
#define ESM_LEVEL_WARN(mask)  (((mask) & 0xFFu) << 8)
#define ESM_LEVEL_ERROR(mask) (((mask) & 0xFFu) << 16)
#define ESM_LEVEL_FATAL(mask) (((mask) & 0xFFu) << 24)

/* Extract per-level pending bits from a pending register value. */
#define ESM_PENDING_INFO(reg)  ((reg) & 0xFFu)
#define ESM_PENDING_WARN(reg)  (((reg) >> 8) & 0xFFu)
#define ESM_PENDING_ERROR(reg) (((reg) >> 16) & 0xFFu)
#define ESM_PENDING_FATAL(reg) (((reg) >> 24) & 0xFFu)

/*
 * Per-bank register block (stride 0x10).
 *
 * Each bank covers 8 inputs. For inputCount <= 8: only banks[0] is used.
 * The last bank may cover fewer than 8 inputs; upper bits are reserved/zero.
 *
 * Severity levels and their outputs:
 *   INFO  -> infoInterrupt  (low-priority PLIC lane)
 *   WARN  -> warnInterrupt  (high-priority PLIC lane)
 *   ERROR -> errorSignal    (after grace-period counter)
 *   FATAL -> errorSignal    (immediately, bypasses counter)
 *
 * Pending bits are write-1-to-clear (W1C). An input can be routed to
 * multiple levels simultaneously via the combined enable register.
 *
 * Lock scope: ERROR/FATAL fields of enable (bits [31:16]) and inject are
 * frozen once the ESM is locked. INFO/WARN fields (bits [15:0]) are never locked.
 */
struct esm_bank {
	uint32_t enable;  /* +0x00 [31:24]=FATAL,[23:16]=ERROR,[15:8]=WARN,[7:0]=INFO */
	uint32_t pending; /* +0x04 same layout, W1C */
	uint32_t raw;     /* +0x08 active inputs for this bank (read-only) */
	uint32_t inject;  /* +0x0C bits [7:0]: injected inputs; writable only when injectEnable && !locked */
};

/*
 * ESM register map.
 *
 * info register fields (read-only):
 *   bits  [8:0] = inputCount
 *   bits [15:9] = bankCount
 *   bits [23:16] = counterWidth
 *   bit  [24]   = locked
 *
 * The grace-period counter starts when any ERROR pending bit is set. If all
 * ERROR pending bits are cleared before it expires no errorSignal is raised.
 * Setting errorCounter = 0 makes ERROR behave like FATAL (immediate).
 *
 * Locking (ESM_CTRL_LOCK) atomically clears injectEnable, zeroes all inject
 * bank registers, and freezes ERROR/FATAL enables, errorCounter, and inject.
 */
struct esm_regs {
	uint32_t ip_header;    /* 0x000 */
	uint32_t ip_version;   /* 0x004 */
	uint32_t info;         /* 0x008 - inputCount[8:0] | bankCount[15:9] | counterWidth[23:16] | locked[24] */
	uint32_t control;      /* 0x00C */
	uint32_t status;       /* 0x010 */
	uint32_t error_counter;/* 0x014 - grace-period reload value; locked */
	uint32_t _pad[2];      /* 0x018-0x01F */
	struct esm_bank banks[32]; /* 0x020, stride 0x10; only banks[0..bankCount-1] are valid */
};

struct esm_driver {
	volatile struct esm_regs *regs;
	uint32_t bank_count;
};

int esm_init(struct esm_driver *driver, unsigned int base_address);

/* Master enable / disable. Disable is ignored when locked. */
void esm_enable(struct esm_driver *driver);
void esm_disable(struct esm_driver *driver);

/**
 * Lock the ESM. Atomically clears injectEnable, zeroes inject registers, and
 * freezes ERROR/FATAL enables, errorCounter, and inject registers.
 */
void esm_lock(struct esm_driver *driver);

/**
 * Write the combined enable register for a bank.
 * Build the mask with ESM_LEVEL_INFO/WARN/ERROR/FATAL(input_mask).
 * ERROR/FATAL fields (bits [31:16]) are ignored once locked.
 */
void esm_configure(struct esm_driver *driver, unsigned int bank, uint32_t mask);

/**
 * Configure the grace-period counter for ERROR level.
 * errorSignal asserts after (counter + 1) cycles if any ERROR pending bit
 * remains set. Set to 0 for immediate assertion (same behaviour as FATAL).
 * Write ignored when locked.
 */
void esm_configure_counter(struct esm_driver *driver, uint32_t counter);

/* Software error injection. */
void esm_inject_enable(struct esm_driver *driver);
void esm_inject_disable(struct esm_driver *driver);
void esm_inject_set(struct esm_driver *driver, unsigned int bank, uint32_t bits);
void esm_inject_clear(struct esm_driver *driver, unsigned int bank, uint32_t bits);

/* Read current status register. */
uint32_t esm_status(struct esm_driver *driver);

/* Read raw (synchronised) input state including active inject, for a bank. */
uint32_t esm_raw(struct esm_driver *driver, unsigned int bank);

/**
 * Read the pending register for a bank.
 * Use ESM_PENDING_INFO/WARN/ERROR/FATAL(value) to extract per-level bits.
 */
uint32_t esm_pending(struct esm_driver *driver, unsigned int bank);

/**
 * Clear pending bits for a bank (W1C).
 * Build the mask with ESM_LEVEL_INFO/WARN/ERROR/FATAL(input_mask).
 */
void esm_clear(struct esm_driver *driver, unsigned int bank, uint32_t mask);

#endif
