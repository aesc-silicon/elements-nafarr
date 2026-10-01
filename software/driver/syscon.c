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
	return SYSCON_SILICON_MAJOR(driver->regs->silicon_rev);
}

unsigned int syscon_silicon_minor(struct syscon_driver *driver)
{
	return SYSCON_SILICON_MINOR(driver->regs->silicon_rev);
}

unsigned int syscon_ref_clock(struct syscon_driver *driver)
{
	return driver->regs->ref_clock;
}

unsigned int syscon_build_date(struct syscon_driver *driver)
{
	return driver->regs->build_date;
}

unsigned int syscon_feature_words(struct syscon_driver *driver)
{
	return SYSCON_FEATURE_WORD_COUNT(driver->regs->feature_info);
}

unsigned int syscon_features(struct syscon_driver *driver, unsigned int word)
{
	if (word >= SYSCON_FEATURE_WORDS || word >= syscon_feature_words(driver)) {
		return 0;
	}
	return driver->regs->features[word];
}

int syscon_has_feature(struct syscon_driver *driver, unsigned int feature)
{
	if (feature >= SYSCON_FEATURE_COUNT) {
		return 0;
	}
	return (syscon_features(driver, feature / 32) >> (feature % 32)) & 1;
}

#define SYSCON_NAME(name, ordinal) [ordinal] = #name,

static const char *const syscon_vendor_names[SYSCON_VENDOR_COUNT] = {
	SYSCON_FOR_EACH_VENDOR(SYSCON_NAME)
};

static const char *const syscon_platform_names[SYSCON_PLATFORM_COUNT] = {
	SYSCON_FOR_EACH_PLATFORM(SYSCON_NAME)
};

static const char *const syscon_product_names[SYSCON_PRODUCT_COUNT] = {
	SYSCON_FOR_EACH_PRODUCT(SYSCON_NAME)
};

static const char *const syscon_platform_class_names[SYSCON_PLATFORM_CLASS_COUNT] = {
	SYSCON_FOR_EACH_PLATFORM_CLASS(SYSCON_NAME)
};

static const char *const syscon_feature_names[SYSCON_FEATURE_COUNT] = {
	SYSCON_FOR_EACH_FEATURE(SYSCON_NAME)
};

static const char *syscon_name(const char *const *names, unsigned int count, unsigned int id)
{
	return id < count ? names[id] : 0;
}

const char *syscon_vendor_name(unsigned int vendor)
{
	return syscon_name(syscon_vendor_names, SYSCON_VENDOR_COUNT, vendor);
}

const char *syscon_platform_name(unsigned int platform)
{
	return syscon_name(syscon_platform_names, SYSCON_PLATFORM_COUNT, platform);
}

const char *syscon_product_name(unsigned int product)
{
	return syscon_name(syscon_product_names, SYSCON_PRODUCT_COUNT, product);
}

const char *syscon_platform_class_name(unsigned int platform_class)
{
	return syscon_name(syscon_platform_class_names, SYSCON_PLATFORM_CLASS_COUNT,
			   platform_class);
}

const char *syscon_feature_name(unsigned int feature)
{
	return syscon_name(syscon_feature_names, SYSCON_FEATURE_COUNT, feature);
}
