/*
 * SPDX-FileCopyrightText: 2026 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <assert.h>
#include <pthread.h>
#include <stdint.h>

#include "i2c.h"

#define TEST_CMD_FIFO_DEPTH 7U
#define TEST_RSP_FIFO_DEPTH 3U

struct i2c_mock {
	volatile uint32_t *regs;
	uint32_t initial_value;
};

static void *respond_with_nack(void *arg)
{
	struct i2c_mock *mock = arg;

	while (mock->regs[I2C_REG_CMD_RESPONSE / sizeof(uint32_t)] ==
	       mock->initial_value) {}

	assert(mock->regs[I2C_REG_CMD_RESPONSE / sizeof(uint32_t)] == 0x151);
	mock->regs[I2C_REG_CMD_RESPONSE / sizeof(uint32_t)] = 0x100;
	mock->regs[I2C_REG_FIFO_STATUS / sizeof(uint32_t)] =
		(TEST_CMD_FIFO_DEPTH << 16) | 1U;

	return NULL;
}

int main(void)
{
	struct i2c_driver driver;
	static volatile uint32_t fake_regs[16];
	const uint32_t untouched = 0xa5a5a5a5;
	struct i2c_mock mock = {
		.regs = fake_regs,
		.initial_value = untouched,
	};
	pthread_t responder;
	unsigned char value;

	fake_regs[0] = untouched;
	fake_regs[1] = untouched;
	fake_regs[I2C_REG_FIFO_DEPTHS / sizeof(uint32_t)] =
		(TEST_CMD_FIFO_DEPTH << 8) | TEST_RSP_FIFO_DEPTH;

	assert(i2c_init(&driver, (unsigned long)fake_regs, 1234) == 1);
	assert(driver.fifo_size == TEST_CMD_FIFO_DEPTH);
	assert(fake_regs[I2C_REG_CLOCK_DIV / sizeof(uint32_t)] == 1234);
	assert(fake_regs[0] == untouched);
	assert(fake_regs[1] == untouched);

	fake_regs[I2C_REG_CMD_RESPONSE / sizeof(uint32_t)] = untouched;
	fake_regs[I2C_REG_FIFO_STATUS / sizeof(uint32_t)] =
		TEST_CMD_FIFO_DEPTH << 16;
	assert(pthread_create(&responder, NULL, respond_with_nack, &mock) == 0);
	assert(i2c_read(&driver, 0x50, 0, 1, &value) == -1);
	assert(pthread_join(responder, NULL) == 0);

	return 0;
}
