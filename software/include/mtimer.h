#ifndef BOOTROM_MTIMER
#define BOOTROM_MTIMER

struct mtimer_regs {
	int ip_info[4];
	unsigned int ctrl;
	unsigned int clk_div;
	unsigned int cnt_low;
	unsigned int cnt_high;
	unsigned int cmp_ctrl;
	unsigned int cmp_low;
	unsigned int cmp_high;
	unsigned int cmp_reserved;
	unsigned int irq_en;
	unsigned int irq_pend;
	unsigned int irq_clr;
};

unsigned int mtimer_arm(unsigned int cmp_high, unsigned int cmp_low);
unsigned int mtimer_irq_clr(void);

#endif
