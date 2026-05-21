/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "pinmux.h"

int main(void)
{
	struct pinmux_driver driver;
	/* extra space for option[] entries beyond the fixed header */
	static unsigned char fake_buf[128];

	/* pinmux_init reads info; pass fake buffer address directly */
	pinmux_init(&driver, (unsigned long)fake_buf);

	pinmux_pin_count(&driver);
	pinmux_options_count(&driver);
	/* pin_count == 0 from zeroed buffer; set/get guards return early */
	pinmux_set(&driver, 0, 0);
	pinmux_get(&driver, 0);

	return 0;
}
