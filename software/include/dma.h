/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_DMA_H
#define ELEMENTS_DMA_H

#include <stdint.h>

#ifndef DMA_CHANNELS
#define DMA_CHANNELS 8
#endif

/* info register fields */
#define DMA_INFO_CHANNELS(r)      ((r) & 0xFF)
#define DMA_INFO_REQUEST_LINES(r) (((r) >> 8) & 0xFF)
#define DMA_INFO_BURST_LOG2(r)    (((r) >> 16) & 0xFF)

/* control register: write */
#define DMA_CTRL_START            (1u << 0)
#define DMA_CTRL_ABORT            (1u << 1)
/* control register: read */
#define DMA_CTRL_BUSY             (1u << 0)
#define DMA_CTRL_ERROR            (1u << 1)
#define DMA_CTRL_REQUEST_ACTIVE   (1u << 2)

/* config register / descriptor word 0 */
#define DMA_CFG_SRC_INC           (1u << 0)
#define DMA_CFG_DST_INC           (1u << 1)
#define DMA_CFG_WIDTH_8           (0u << 2)
#define DMA_CFG_WIDTH_16          (1u << 2)
#define DMA_CFG_WIDTH_32          (2u << 2)
#define DMA_CFG_REQ_ENABLE        (1u << 4)
#define DMA_CFG_REQ_SEL(n)        (((n) & 0xF) << 5)
#define DMA_CFG_LINKED            (1u << 9)
#define DMA_CFG_IRQ_DONE          (1u << 10)
#define DMA_CFG_BURST_LIMIT(log2) (((log2) & 0xF) << 16)

/* interrupt bits (irq_pending / irq_mask) */
#define DMA_IRQ_DONE(ch)          (1u << (2 * (ch)))
#define DMA_IRQ_ERROR(ch)         (1u << (2 * (ch) + 1))

struct dma_channel_regs {
	uint32_t control;  /* +0x00 */
	uint32_t config;   /* +0x04 */
	uint32_t src;      /* +0x08 */
	uint32_t dst;      /* +0x0C */
	uint32_t length;   /* +0x10 */
	uint32_t next;     /* +0x14 */
	uint32_t reserved[2];
};

struct dma_regs {
	uint32_t ip_header;                             /* 0x000 */
	uint32_t ip_version;                            /* 0x004 */
	uint32_t info;                                  /* 0x008 */
	uint32_t irq_pending;                           /* 0x00C - W1C */
	uint32_t irq_mask;                              /* 0x010 */
	uint32_t status;                                /* 0x014 - busy per channel */
	struct dma_channel_regs channel[DMA_CHANNELS];  /* 0x018 */
};

/* Memory-resident descriptor. Must be 4-byte aligned. */
struct dma_descriptor {
	uint32_t config;
	uint32_t src;
	uint32_t dst;
	uint32_t length;
	uint32_t next; /* physical address of the next descriptor, 0 terminates */
};

struct dma_driver {
	volatile struct dma_regs *regs;
	unsigned int channels;
	unsigned int request_lines;
	unsigned int burst_log2;
};

void dma_init(struct dma_driver *driver, unsigned long base_address);

/*
 * Program channel @ch with a single transfer. @config is a combination of
 * DMA_CFG_* values. The channel must be idle.
 */
void dma_configure(struct dma_driver *driver, unsigned int ch, uint32_t config,
		   uint32_t src, uint32_t dst, uint32_t length);

/*
 * Program channel @ch to execute the descriptor chain starting at @first.
 * Descriptors must be visible to the DMA (uncached or cleaned from the cache).
 */
void dma_configure_chain(struct dma_driver *driver, unsigned int ch,
			 const struct dma_descriptor *first);

void dma_start(struct dma_driver *driver, unsigned int ch);
void dma_abort(struct dma_driver *driver, unsigned int ch);
int  dma_busy(struct dma_driver *driver, unsigned int ch);
int  dma_error(struct dma_driver *driver, unsigned int ch);

/* Poll until channel @ch is idle. Returns 0 on success, -1 on error. */
int  dma_wait(struct dma_driver *driver, unsigned int ch);

/* Blocking memory-to-memory copy on channel @ch. Returns 0 on success, -1 on error. */
int  dma_memcpy(struct dma_driver *driver, unsigned int ch, void *dst, const void *src,
		uint32_t length);

/*
 * Interrupt control.
 * @flags: bitmask of DMA_IRQ_* values
 */
void     dma_irq_enable(struct dma_driver *driver, uint32_t flags);
void     dma_irq_disable(struct dma_driver *driver, uint32_t flags);
uint32_t dma_irq_pending(struct dma_driver *driver);
void     dma_irq_clear(struct dma_driver *driver, uint32_t flags);

#endif
