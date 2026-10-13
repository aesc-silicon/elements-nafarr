/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMNETS_I2C
#define ELEMNETS_I2C

#define I2C_SPEED_STANDARD	100000U // 100 kHz
#define I2C_SPEED_FAST		400000U // 400 kHz

#define I2C_REG_FIFO_DEPTHS	0x00CU
#define I2C_REG_CMD_RESPONSE	0x014U
#define I2C_REG_FIFO_STATUS	0x018U
#define I2C_REG_CLOCK_DIV	0x01CU

#define I2C_CMD_FIFO_DEPTH(value)	(((value) >> 8) & 0xFFU)
#define I2C_CMD_FIFO_VACANCY(value)	(((value) >> 16) & 0xFFFFU)
#define I2C_RSP_FIFO_OCCUPANCY(value)	((value) & 0xFFFFU)

struct i2c_driver {
	volatile unsigned char *base;
	unsigned int fifo_size;
};

#define I2C_CALC_CLOCK(freq, speed) (freq / speed / 4)

int i2c_init(struct i2c_driver *driver, unsigned long base_address,
	     unsigned int clock_divider);
int i2c_read(struct i2c_driver *driver, unsigned int addr, unsigned int reg,
	     unsigned int size, unsigned char *buf);

#endif
