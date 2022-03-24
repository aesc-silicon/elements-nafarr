#include "mtimer.h"
#include "soc.h"

unsigned int mtimer_sleep(unsigned int cmp_high, unsigned int cmp_low)
{
	volatile struct mtimer_regs *mtimer = (struct mtimer_regs *)TIMER_BASE;

	mtimer->cmp_low = cmp_low;
	mtimer->cmp_high = cmp_high;
	mtimer->cmp_ctrl = 0x1;

	while (!(mtimer->irq_pend & 0x1));
	mtimer->irq_clr = 0x1;

	return 1;
}
