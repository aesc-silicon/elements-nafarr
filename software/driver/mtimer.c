#include "mtimer.h"

int mtimer_init(struct mtimer_driver *driver, unsigned int base_address)
{
	driver->regs = (struct mtimer_regs *)base_address;

	return 1;
}


unsigned int mtimer_sleep(struct mtimer_driver *driver, unsigned int cmp_high,
	unsigned int cmp_low)
{
	volatile struct mtimer_regs *mtimer = driver->regs;

	mtimer->cmp_low = cmp_low;
	mtimer->cmp_high = cmp_high;
	mtimer->cmp_ctrl = 0x1;

	while (!(mtimer->irq_pend & 0x1));
	mtimer->irq_clr = 0x1;

	return 1;
}
