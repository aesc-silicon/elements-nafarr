#ifndef ELEMENTS_MEMC
#define ELEMENTS_MEMC

#define ELEMENTS_MEMC_VARIABLE_LATENCY 0
#define ELEMENTS_MEMC_LATENCY_CYCLES 7

struct memc_regs {
	unsigned int reserved[4];
	unsigned int reset;
	unsigned int reset_pulse;
	unsigned int reset_hold;
	unsigned int reserved1;
	unsigned int timings;
	unsigned int reserved2[3];
	unsigned int device_registers;
	unsigned int device_registers_queues;
};
struct memc_driver {
	volatile struct memc_regs *regs;
};

int memc_init(struct memc_driver *driver, unsigned int base_address);

unsigned short read_register(struct memc_driver *driver, unsigned int address);
void write_register(struct memc_driver *driver, unsigned int address,
		unsigned int value);
void set_latency_timings(struct memc_driver *driver, unsigned char latency);
void set_latency_clocks(struct memc_driver *driver, unsigned char clocks);

#endif
