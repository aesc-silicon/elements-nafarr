/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "dma.h"

void dma_init(struct dma_driver *driver, unsigned long base_address)
{
	uint32_t info;

	driver->regs = (volatile struct dma_regs *)base_address;
	info = driver->regs->info;
	driver->channels = DMA_INFO_CHANNELS(info);
	driver->request_lines = DMA_INFO_REQUEST_LINES(info);
	driver->burst_log2 = DMA_INFO_BURST_LOG2(info);
}

void dma_configure(struct dma_driver *driver, unsigned int ch, uint32_t config,
		   uint32_t src, uint32_t dst, uint32_t length)
{
	volatile struct dma_channel_regs *c = &driver->regs->channel[ch];

	c->config = config;
	c->src = src;
	c->dst = dst;
	c->length = length;
	c->next = 0;
}

void dma_configure_chain(struct dma_driver *driver, unsigned int ch,
			 const struct dma_descriptor *first)
{
	volatile struct dma_channel_regs *c = &driver->regs->channel[ch];

	c->config = DMA_CFG_LINKED;
	c->src = 0;
	c->dst = 0;
	c->length = 0;
	c->next = (uint32_t)(unsigned long)first;
}

void dma_start(struct dma_driver *driver, unsigned int ch)
{
	driver->regs->channel[ch].control = DMA_CTRL_START;
}

void dma_abort(struct dma_driver *driver, unsigned int ch)
{
	driver->regs->channel[ch].control = DMA_CTRL_ABORT;
}

int dma_busy(struct dma_driver *driver, unsigned int ch)
{
	return (driver->regs->channel[ch].control & DMA_CTRL_BUSY) != 0;
}

int dma_error(struct dma_driver *driver, unsigned int ch)
{
	return (driver->regs->channel[ch].control & DMA_CTRL_ERROR) != 0;
}

int dma_wait(struct dma_driver *driver, unsigned int ch)
{
	while (dma_busy(driver, ch))
		;
	return dma_error(driver, ch) ? -1 : 0;
}

int dma_memcpy(struct dma_driver *driver, unsigned int ch, void *dst, const void *src,
	       uint32_t length)
{
	dma_configure(driver, ch, DMA_CFG_SRC_INC | DMA_CFG_DST_INC,
		      (uint32_t)(unsigned long)src, (uint32_t)(unsigned long)dst, length);
	dma_start(driver, ch);
	return dma_wait(driver, ch);
}

void dma_irq_enable(struct dma_driver *driver, uint32_t flags)
{
	driver->regs->irq_mask |= flags;
}

void dma_irq_disable(struct dma_driver *driver, uint32_t flags)
{
	driver->regs->irq_mask &= ~flags;
}

uint32_t dma_irq_pending(struct dma_driver *driver)
{
	return driver->regs->irq_pending;
}

void dma_irq_clear(struct dma_driver *driver, uint32_t flags)
{
	driver->regs->irq_pending = flags;
}
