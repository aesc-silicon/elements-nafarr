/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_PINMUX_H
#define ELEMENTS_PINMUX_H

/*
 * Register layout:
 *   0x000  ip_header   — IP identification header
 *   0x004  ip_version  — IP identification version
 *   0x008  info        — [7:0]=pin_count, [15:8]=options_count
 *   0x00C  (reserved)
 *   0x010 + pin*4      — option register for pin N (selects function index)
 */

struct pinmux_regs {
	unsigned int ip_header;   /* 0x000 */
	unsigned int ip_version;  /* 0x004 */
	unsigned int info;        /* 0x008 */
	unsigned int _reserved;   /* 0x00C */
	unsigned int option[];    /* 0x010 + pin*4 — flexible array, index by pin */
};

/* info register fields */
#define PINMUX_PIN_COUNT(reg)     ((reg) & 0xFF)
#define PINMUX_OPTIONS_COUNT(reg) (((reg) >> 8) & 0xFF)

struct pinmux_driver {
	volatile struct pinmux_regs *regs;
	unsigned int pin_count;
	unsigned int options_count;
};

void pinmux_init(struct pinmux_driver *driver, unsigned long base_address);

unsigned int pinmux_pin_count(struct pinmux_driver *driver);
unsigned int pinmux_options_count(struct pinmux_driver *driver);
void pinmux_set(struct pinmux_driver *driver, unsigned int pin, unsigned int option);
unsigned int pinmux_get(struct pinmux_driver *driver, unsigned int pin);

#endif
