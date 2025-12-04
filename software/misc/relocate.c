/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "relocate.h"

void relocate_code(unsigned int *src, unsigned int *dst, unsigned int size)
{
	unsigned int i;

	for (i = 0; i < size; i++)
		dst[i] = src[i];
}
