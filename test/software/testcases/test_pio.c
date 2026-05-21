/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "pio.h"

int main(void)
{
	struct pio_driver driver;
	static volatile struct pio_regs fake_regs;

	pio_init(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	pio_enable(&driver);
	pio_disable(&driver);
	pio_set_stop_at_loop(&driver, 1);
	pio_set_stop_at_loop(&driver, 0);

	pio_program_reset(&driver);
	pio_program_write(&driver, PIO_MAKE_CMD(PIO_CMD_HIGH, 0, 0));
	pio_program_write(&driver, PIO_MAKE_CMD(PIO_CMD_WAIT, 0, 100));
	pio_program_write(&driver, PIO_MAKE_CMD(PIO_CMD_LOOP, 0, 0));

	unsigned int result;
	pio_read(&driver, &result);

	pio_irq_enable(&driver, PIO_IRQ_RX_READY | PIO_IRQ_LOOP_DONE);
	pio_irq_disable(&driver, PIO_IRQ_RX_READY);
	pio_irq_pending(&driver);
	pio_irq_clear(&driver, PIO_IRQ_LOOP_DONE);

	return 0;
}
