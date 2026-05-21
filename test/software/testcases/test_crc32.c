/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "crc32.h"

int main(void)
{
	struct crc32_driver driver;
	static volatile struct crc32_regs fake_regs;

	crc32_init_driver(&driver, 0xf0000000);
	driver.regs = &fake_regs;

	crc32_init(&driver);
	crc32_write(&driver, 0x12345678);
	crc32_read(&driver);
	crc32_info(&driver);
	crc32_get_xorout(&driver);
	crc32_set_xorout(&driver, 0xffffffff);

	return 0;
}
