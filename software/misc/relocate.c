#include "relocate.h"

void relocate_code(unsigned int *src, unsigned int *dst, unsigned int size)
{
	unsigned int i;

	for (i = 0; i < size; i++)
		dst[i] = src[i];
}
