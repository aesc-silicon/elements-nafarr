/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "timer.h"

int main(void)
{
	struct timer_driver driver;
	/* extra space for per-timer registers beyond struct timer_regs */
	static unsigned char fake_buf[128];

	/* timer_init reads info register; pass fake buffer address directly */
	timer_init(&driver, (unsigned long)fake_buf);
	/* count == 0 from zeroed buffer; stride == 0x10 */

	timer_set_control(&driver, 0, TIMER_CTRL_ENABLE | TIMER_CTRL_MODE_PERIODIC);
	timer_get_control(&driver, 0);
	timer_set_prescaler(&driver, 0, 999);
	timer_set_reload(&driver, 0, 9999);
	timer_set_counter(&driver, 0, 0);
	timer_get_counter(&driver, 0);
	timer_set_compare(&driver, 0, 0, 5000);

	timer_irq_enable(&driver, TIMER_IRQ_OVERFLOW(0, 1));
	timer_irq_pending(&driver);
	timer_irq_clear(&driver, TIMER_IRQ_OVERFLOW(0, 1));
	timer_irq_disable(&driver, TIMER_IRQ_OVERFLOW(0, 1));

	return 0;
}
