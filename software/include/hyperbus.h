/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_HYPERBUS_H
#define ELEMENTS_HYPERBUS_H

#include <stdint.h>

/*
 * HyperBus (HyperRAM) controller.
 *
 * The memory array itself is accessed directly through the controller's
 * memory-mapped window (e.g. 0x90000000) with ordinary load/store; this driver
 * only touches the configuration register block: PHY reset, access latency, the
 * device configuration-register port, FIFO status and the fault controller.
 */

/* Configuration register access port (reg_access) bit fields. */
#define HYPERBUS_REG_READ         (1u << 15) /* command: 1 = read, 0 = write   */
#define HYPERBUS_REG_ADDR(a)      ((a) & 0x7fffu)
#define HYPERBUS_REG_WDATA(d)     (((uint32_t)(d) & 0xffffu) << 16)
#define HYPERBUS_REG_RSP_VALID    (1u << 31) /* read: response present         */
#define HYPERBUS_REG_RSP_ERROR    (1u << 16) /* read: device signalled a fault */
#define HYPERBUS_REG_RSP_DATA(v)  ((v) & 0xffffu)

/* fifo_status bit fields. */
#define HYPERBUS_FIFO_RSP_OCCUPANCY(v)   ((v) & 0xffffu)
#define HYPERBUS_FIFO_CMD_AVAILABILITY(v) (((v) >> 16) & 0xffffu)

/* Fault controller sources (error_pending / error_mask). */
#define HYPERBUS_ERR_ADDRESS    (1u << 0) /* access outside any partition       */
#define HYPERBUS_ERR_PERMISSION (1u << 1) /* read of a non-readable partition   */
#define HYPERBUS_ERR_TIMEOUT    (1u << 2) /* transaction exceeded the timeout   */
#define HYPERBUS_ERR_UNALIGNED  (1u << 3) /* odd-address (straddle) not allowed */

struct hyperbus_regs {
	uint32_t ip_header;     /* 0x00 - IpIdentification header  */
	uint32_t ip_version;    /* 0x04 - IpIdentification version */
	uint32_t _rsvd0[2];     /* 0x08                            */
	uint32_t reset_trigger; /* 0x10 - xW: pulse the PHY reset  */
	uint32_t reset_pulse;   /* 0x14 - reset pulse width, cycles */
	uint32_t reset_halt;    /* 0x18 - reset halt width, cycles  */
	uint32_t _rsvd1;        /* 0x1C                            */
	uint32_t latency;       /* 0x20 - HyperRAM latency cycles (3..7) */
	uint32_t _rsvd2[3];     /* 0x24                            */
	uint32_t reg_access;    /* 0x30 - device config register port */
	uint32_t fifo_status;   /* 0x34 - Rx: cmd avail[31:16], rsp occ[15:0] */
	uint32_t _rsvd3[2];     /* 0x38                            */
	uint32_t error_pending; /* 0x40 - W1C pending faults       */
	uint32_t error_mask;    /* 0x44 - 1 = report source        */
};

struct hyperbus_driver {
	volatile struct hyperbus_regs *regs;
};

int hyperbus_init(struct hyperbus_driver *driver, unsigned long base_address);

/**
 * Set the HyperRAM read/write access latency in clock cycles (3..7). Must match
 * the device's configured latency (CR0).
 */
void hyperbus_set_latency(struct hyperbus_driver *driver, uint32_t cycles);

/**
 * Pulse the HyperBus RESET# line using the configured pulse/halt widths.
 */
void hyperbus_reset(struct hyperbus_driver *driver);

/**
 * Set the RESET# pulse and post-reset halt widths, in clock cycles.
 */
void hyperbus_set_reset_timing(struct hyperbus_driver *driver, uint32_t pulse,
			       uint32_t halt);

/**
 * Write a 16-bit value to device configuration register @reg_addr (e.g. CR0).
 */
void hyperbus_reg_write(struct hyperbus_driver *driver, uint16_t reg_addr,
			uint16_t value);

/**
 * Read device configuration register @reg_addr into @value. Blocks until the
 * response arrives. Returns 0 on success, -1 if the device signalled a fault.
 */
int hyperbus_reg_read(struct hyperbus_driver *driver, uint16_t reg_addr,
		      uint16_t *value);

/**
 * Return the raw fifo_status word (use the HYPERBUS_FIFO_* accessors).
 */
uint32_t hyperbus_fifo_status(struct hyperbus_driver *driver);

/**
 * Fault controller. @mask is a bitmask of HYPERBUS_ERR_* values.
 */
uint32_t hyperbus_error_pending(struct hyperbus_driver *driver);
void     hyperbus_error_clear(struct hyperbus_driver *driver, uint32_t mask);
void     hyperbus_error_mask(struct hyperbus_driver *driver, uint32_t mask);

#endif
