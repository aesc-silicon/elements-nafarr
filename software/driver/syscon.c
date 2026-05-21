/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "syscon.h"

void syscon_init(struct syscon_driver *driver, unsigned long base_address)
{
	driver->regs = (volatile struct syscon_regs *)base_address;
}

unsigned int syscon_vendor(struct syscon_driver *driver)
{
	return SYSCON_VENDOR(driver->regs->identity);
}

unsigned int syscon_platform(struct syscon_driver *driver)
{
	return SYSCON_PLATFORM(driver->regs->identity);
}

unsigned int syscon_platform_class(struct syscon_driver *driver)
{
	return SYSCON_PLATFORM_CLASS(driver->regs->identity);
}

unsigned int syscon_product(struct syscon_driver *driver)
{
	return SYSCON_PRODUCT(driver->regs->identity);
}

unsigned int syscon_silicon_major(struct syscon_driver *driver)
{
	return driver->regs->silicon_major;
}

unsigned int syscon_silicon_minor(struct syscon_driver *driver)
{
	return driver->regs->silicon_minor;
}

unsigned int syscon_features(struct syscon_driver *driver)
{
	return driver->regs->features;
}

unsigned int syscon_ref_clock(struct syscon_driver *driver)
{
	return driver->regs->ref_clock;
}

unsigned int syscon_build_date(struct syscon_driver *driver)
{
	return driver->regs->build_date;
}

int syscon_has_feature(struct syscon_driver *driver, unsigned int feature_bit)
{
	return (driver->regs->features & feature_bit) != 0;
}
