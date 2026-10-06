/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_TSETLIN_H
#define ELEMENTS_TSETLIN_H

/* Tsetlin machine inference on Muninn clause arrays (nafarr.peripherals.ml.tsetlin). */

#define TSETLIN_MAX_WORDS	64	/* 2048 literals or rows */
#define TSETLIN_MAX_CLASSES	256

struct tsetlin_regs {
	unsigned int ip_header;
	unsigned int ip_version;
	unsigned int array;		/* [31:16] literals, [15:0] rows */
	unsigned int classes;		/* [31:16] clauses per class, [15:0] classes */
	unsigned int options;		/* [8] valid column, [7:0] tiles */
	unsigned int ctrl;		/* [1] irq enable, [0] start */
	unsigned int status;		/* [1] done (write 1 to clear), [0] busy */
	unsigned int row;
	unsigned int commit;
	unsigned int result;		/* [31:16] winning sum, [15:0] winning class */
	unsigned int reserved0[54];
	unsigned int data[TSETLIN_MAX_WORDS];		/* 0x100: row buffer */
	unsigned int literals[TSETLIN_MAX_WORDS];	/* 0x200 */
	unsigned int matches[TSETLIN_MAX_WORDS];	/* 0x300: clause outputs */
	int sums[TSETLIN_MAX_CLASSES];			/* 0x400: votes per class */
};

struct tsetlin_driver {
	volatile struct tsetlin_regs *regs;
	unsigned int rows;
	unsigned int literals;
	unsigned int classes;
	unsigned int clauses_per_class;	/* rows behind classes x clauses_per_class are unused */
};

#define TSETLIN_CTRL_START	(1 << 0)
#define TSETLIN_CTRL_IRQ_ENABLE	(1 << 1)
#define TSETLIN_STATUS_BUSY	(1 << 0)
#define TSETLIN_STATUS_DONE	(1 << 1)
#define TSETLIN_OPTIONS_TILES(options)		((options) & 0xff)
#define TSETLIN_OPTIONS_VALID_COLUMN		(1 << 8)

int tsetlin_init(struct tsetlin_driver *driver, unsigned long base_address);
/* include: one bit per literal, literal 0 is the valid column (1 = clause unused) */
int tsetlin_write_clause(struct tsetlin_driver *driver, unsigned int row,
			 const unsigned int *include);
int tsetlin_search_start(struct tsetlin_driver *driver, const unsigned int *literals);
int tsetlin_search_done(struct tsetlin_driver *driver);
int tsetlin_result(struct tsetlin_driver *driver, int *sum);
int tsetlin_class_sum(struct tsetlin_driver *driver, unsigned int cls);
void tsetlin_irq_enable(struct tsetlin_driver *driver);
void tsetlin_irq_disable(struct tsetlin_driver *driver);

#endif
