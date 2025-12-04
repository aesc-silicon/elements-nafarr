/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMNETS_PLIC
#define ELEMNETS_PLIC

struct plic_driver {
	unsigned int *gateway_priority;
	unsigned int *gateway_pending;
	unsigned int *target_enable;
	unsigned int *claim;
};


int plic_init(struct plic_driver *driver, unsigned int base_address);
int plic_irq_enable(struct plic_driver *driver, unsigned int number);
int plic_irq_disable(struct plic_driver *driver, unsigned int number);
int plic_irq_claim(struct plic_driver *driver, unsigned int number);

#endif
