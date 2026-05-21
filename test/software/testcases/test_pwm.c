/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "pwm.h"

int main(void)
{
	struct pwm_driver driver;
	/* extra space for per-channel registers beyond struct pwm_regs */
	static unsigned char fake_buf[256];

	/* pwm_init reads channel_config; pass fake buffer address directly */
	pwm_init(&driver, (unsigned long)fake_buf);
	/* channel_count == 0 from zeroed buffer; override to test channel API */
	driver.channel_count = 1;

	pwm_channel_count(&driver);
	pwm_channel_set_clock_div(&driver, 0, 999);
	pwm_channel_set_period(&driver, 0, 1000);
	pwm_channel_set_duty(&driver, 0, 250, 750);
	pwm_channel_set_dead_time(&driver, 0, 10);
	pwm_channel_set_phase_offset(&driver, 0, 0);
	pwm_channel_set_shot_count(&driver, 0, 0);
	pwm_channel_set_invert(&driver, 0, 0);
	pwm_channel_set_mode_center(&driver, 0, 0);
	pwm_channel_enable(&driver, 0);
	pwm_channel_status(&driver, 0);
	pwm_channel_disable(&driver, 0);

	pwm_irq_enable(&driver, 0x1);
	pwm_irq_disable(&driver, 0x1);
	pwm_irq_pending(&driver);
	pwm_irq_clear(&driver, 0x1);

	return 0;
}
