/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "pwm.h"

void pwm_init(struct pwm_driver *driver, unsigned long base_address)
{
	driver->regs = (struct pwm_regs *)base_address;
	driver->channel_count = PWM_CHANNEL_COUNT(driver->regs->channel_config);
}

unsigned int pwm_channel_count(struct pwm_driver *driver)
{
	return driver->channel_count;
}

void pwm_channel_enable(struct pwm_driver *driver, unsigned int ch)
{
	pwm_channel(driver, ch)->control |= PWM_CTRL_ENABLE;
}

void pwm_channel_disable(struct pwm_driver *driver, unsigned int ch)
{
	pwm_channel(driver, ch)->control &= ~PWM_CTRL_ENABLE;
}

void pwm_channel_set_invert(struct pwm_driver *driver, unsigned int ch, int invert)
{
	if (invert)
		pwm_channel(driver, ch)->control |= PWM_CTRL_INVERT;
	else
		pwm_channel(driver, ch)->control &= ~PWM_CTRL_INVERT;
}

void pwm_channel_set_mode_center(struct pwm_driver *driver, unsigned int ch, int center)
{
	if (center)
		pwm_channel(driver, ch)->control |= PWM_CTRL_MODE_CENTER;
	else
		pwm_channel(driver, ch)->control &= ~PWM_CTRL_MODE_CENTER;
}

void pwm_channel_set_clock_div(struct pwm_driver *driver, unsigned int ch, unsigned int div)
{
	pwm_channel(driver, ch)->clock_div = div;
}

void pwm_channel_set_period(struct pwm_driver *driver, unsigned int ch, unsigned int period)
{
	pwm_channel(driver, ch)->period = period;
}

void pwm_channel_set_duty(struct pwm_driver *driver, unsigned int ch,
	unsigned int rising, unsigned int falling)
{
	volatile struct pwm_channel_regs *c = pwm_channel(driver, ch);
	c->rising_edge = rising;
	c->falling_edge = falling;
}

void pwm_channel_set_dead_time(struct pwm_driver *driver, unsigned int ch, unsigned int dt)
{
	pwm_channel(driver, ch)->dead_time = dt;
}

void pwm_channel_set_phase_offset(struct pwm_driver *driver, unsigned int ch, unsigned int offset)
{
	pwm_channel(driver, ch)->phase_offset = offset;
}

void pwm_channel_set_shot_count(struct pwm_driver *driver, unsigned int ch, unsigned int count)
{
	pwm_channel(driver, ch)->shot_count = count;
}

unsigned int pwm_channel_status(struct pwm_driver *driver, unsigned int ch)
{
	return pwm_channel(driver, ch)->status;
}

void pwm_irq_enable(struct pwm_driver *driver, unsigned int channel_mask)
{
	driver->regs->irq_enable |= channel_mask;
}

void pwm_irq_disable(struct pwm_driver *driver, unsigned int channel_mask)
{
	driver->regs->irq_enable &= ~channel_mask;
}

unsigned int pwm_irq_pending(struct pwm_driver *driver)
{
	return driver->regs->irq_pending;
}

void pwm_irq_clear(struct pwm_driver *driver, unsigned int channel_mask)
{
	driver->regs->irq_pending = channel_mask;
}
