/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "i2c.h"

#define ELEMENTS_READ			1
#define ELEMENTS_WRITE			0

#define ELEMENTS_CMD_START		0x0100
#define ELEMENTS_CMD_STOP		0x0200
#define ELEMENTS_CMD_READ		0x0400
#define ELEMENTS_CMD_WRITE		0x0000
#define ELEMENTS_CMD_ACK		0x0800
#define ELEMENTS_CMD_NACK		0x0000

#define ELEMENTS_ADDR_READ(addr)	(addr | 0x0001)
#define ELEMENTS_ADDR_WRITE(addr)	(addr & ~0x1)

#define ELEMENTS_RSP_ERROR		0x0100

static inline unsigned int i2c_reg_read(struct i2c_driver *driver,
					unsigned int offset)
{
	return *(volatile unsigned int *)(driver->base + offset);
}

static inline void i2c_reg_write(struct i2c_driver *driver,
				 unsigned int offset, unsigned int value)
{
	*(volatile unsigned int *)(driver->base + offset) = value;
}

int i2c_init(struct i2c_driver *driver, unsigned long base_address,
	     unsigned int clock_divider)
{
	driver->base = (volatile unsigned char *)base_address;

	i2c_reg_write(driver, I2C_REG_CLOCK_DIV, clock_divider);
	driver->fifo_size = I2C_CMD_FIFO_DEPTH(
		i2c_reg_read(driver, I2C_REG_FIFO_DEPTHS));

	return 1;
}

static inline void i2c_busy(struct i2c_driver *driver)
{
	while (I2C_CMD_FIFO_VACANCY(
		i2c_reg_read(driver, I2C_REG_FIFO_STATUS)) !=
	       driver->fifo_size) {}
}

static inline void i2c_wait(struct i2c_driver *driver)
{
	while (I2C_RSP_FIFO_OCCUPANCY(
		i2c_reg_read(driver, I2C_REG_FIFO_STATUS)) == 0) {}
}

int i2c_read(struct i2c_driver *driver, unsigned int addr, unsigned int reg,
	     unsigned int size, unsigned char *buf)
{
	unsigned int result;

	// Send device address ...
	i2c_busy(driver);
	i2c_reg_write(driver, I2C_REG_CMD_RESPONSE,
		      ELEMENTS_CMD_START | ELEMENTS_CMD_WRITE |
		      ELEMENTS_ADDR_READ(addr));
	i2c_wait(driver);
	if (i2c_reg_read(driver, I2C_REG_CMD_RESPONSE) & ELEMENTS_RSP_ERROR)
		return -1;

	// and register.
	i2c_busy(driver);
	i2c_reg_write(driver, I2C_REG_CMD_RESPONSE,
		      ELEMENTS_CMD_WRITE | ELEMENTS_CMD_STOP | (reg & 0xFF));
	i2c_wait(driver);
	if (i2c_reg_read(driver, I2C_REG_CMD_RESPONSE) & ELEMENTS_RSP_ERROR)
		return -2;

	// Send device address ...
	i2c_busy(driver);
	i2c_reg_write(driver, I2C_REG_CMD_RESPONSE,
		      ELEMENTS_CMD_START | ELEMENTS_CMD_WRITE |
		      ELEMENTS_ADDR_READ(addr));
	i2c_wait(driver);
	if (i2c_reg_read(driver, I2C_REG_CMD_RESPONSE) & ELEMENTS_RSP_ERROR)
		return -3;

	// and read data.
	for (int i = 0; i < size; i++) {
		i2c_busy(driver);

		if (i == (size - 1)) {
			i2c_reg_write(driver, I2C_REG_CMD_RESPONSE,
				      ELEMENTS_CMD_READ | ELEMENTS_CMD_NACK |
				      ELEMENTS_CMD_STOP);
		} else {
			i2c_reg_write(driver, I2C_REG_CMD_RESPONSE,
				      ELEMENTS_CMD_READ | ELEMENTS_CMD_ACK);
		}

		i2c_wait(driver);

		result = i2c_reg_read(driver, I2C_REG_CMD_RESPONSE);
		if (result & ELEMENTS_RSP_ERROR)
			return i;
		buf[i] = result & 0xFF;
	}

	return size;
}
