#include "memc.h"

int memc_init(struct memc_driver *driver, unsigned int base_address)
{
	driver->regs = (struct memc_regs *)base_address;
	volatile struct memc_regs *memc = driver->regs;
	unsigned int reg;

	memc->reset_pulse = 0x20;
	memc->reset_hold = 0x40;
	memc->reset = 0x1;

	memc->timings = 0;

	set_latency_clocks(driver, 7);
	return 1;

	reg = read_register(driver, 0x800);
	set_latency_timings(driver, (reg >> 4) & 0xf);

#if ELEMENTS_MEMC_VARIABLE_LATENCY
	reg = read_register(driver, 0x800);
	reg &= ~(1 << 3);
	write_register(driver, 0x800, reg);
#endif

	set_latency_clocks(driver, ELEMENTS_MEMC_LATENCY_CYCLES);

	return 1;
}

unsigned short read_register(struct memc_driver *driver, unsigned int address)
{
	volatile struct memc_regs *memc = driver->regs;
	unsigned int result;

	memc->device_registers = 0x00008000 | (address & 0x7FFF);
	while ((memc->device_registers_queues & 0xFFFF) == 0) {}
	do {
		result = memc->device_registers;
	} while (!(result >> 31));

	return result & 0xFFFF;
}

void write_register(struct memc_driver *driver, unsigned int address, unsigned int value)
{
	volatile struct memc_regs *memc = driver->regs;
	unsigned int result;

	memc->device_registers = 0x0 | (address & 0x7FFF) | (value << 16);
	while ((memc->device_registers_queues & 0xFFFF) == 0) {}
	do {
		result = memc->device_registers;
	} while (!(result >> 31));
}

void set_latency_timings(struct memc_driver *driver, unsigned char latency)
{
	volatile struct memc_regs *memc = driver->regs;

	switch (latency & 0xf) {
		case 0xe:
			memc->timings = 3;
			break;
		case 0xf:
			memc->timings = 4;
			break;
		case 0:
			memc->timings = 5;
			break;
		case 1:
			memc->timings = 6;
			break;
		case 2:
			memc->timings = 7;
			break;
		default:
			memc->timings = 7;
	}
}

void set_latency_clocks(struct memc_driver *driver, unsigned char clocks)
{
	unsigned int value, reg;

	switch (clocks) {
		case 3:
			value = 0x3;
			break;
		case 4:
			value = 0xf;
			break;
		case 5:
			value = 0;
			break;
		case 6:
			value = 1;
			break;
		case 7:
			value = 2;
			break;
		default:
			value = 2;
			break;
	}

	reg = read_register(driver, 0x800);
	reg &= ~0x70;
	reg |= (value & 0xf) << 4;
	write_register(driver, 0x800, reg);
	set_latency_timings(driver, value & 0xf);
}
