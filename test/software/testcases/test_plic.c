/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "plic.h"

int main(void)
{
	struct plic_driver driver;
	static unsigned int fake_plic[8];

	plic_init(&driver, 0xf0000000);

	/* redirect raw pointers to fake memory before dereferencing */
	driver.gateway_priority = &fake_plic[0];
	driver.gateway_pending  = &fake_plic[1];
	driver.target_enable    = &fake_plic[2];
	driver.claim            = &fake_plic[3];

	plic_irq_enable(&driver, 1);
	plic_irq_disable(&driver, 1);
	plic_irq_claim(&driver, 1);

	return 0;
}
