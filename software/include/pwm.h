/*
 * SPDX-FileCopyrightText: 2025 aesc silicon
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef ELEMENTS_PWM_H
#define ELEMENTS_PWM_H

/*
 * Register layout (all offsets from base):
 *   0x000  ip_header        — IP identification header
 *   0x004  ip_version       — IP identification version
 *   0x008  channel_config   — [7:0]=channels, [15:8]=clk_div_width, [23:16]=pulse_width, [31:24]=period_width
 *   0x00C  timing_config    — [7:0]=dead_time_width, [15:8]=shot_count_width
 *   0x010  permissions      — [0]=bus_can_write_clock_div
 *   0x014  irq_pending      — per-channel period-complete pending (W1C), bit N = channel N
 *   0x018  irq_enable       — per-channel period-complete enable mask
 *   0x01C  error_pending    — [0]=faultIn, [1+N]=channel N config error (W1C)
 *   0x020  error_enable     — error enable mask
 *
 * Per-channel registers (stride 0x24 per channel, starting at 0x024):
 *   ch_base = 0x024 + ch * 0x024
 *   ch_base + 0x00  control      — [0]=enable, [1]=invert, [2]=mode (0=edge, 1=center)
 *   ch_base + 0x04  clock_div    — clock divider value
 *   ch_base + 0x08  period       — period in ticks
 *   ch_base + 0x0C  rising_edge  — rising edge tick offset
 *   ch_base + 0x10  falling_edge — falling edge tick offset
 *   ch_base + 0x14  dead_time    — dead-time in ticks
 *   ch_base + 0x18  phase_offset — phase offset in ticks (applied on enable)
 *   ch_base + 0x1C  shot_count   — 0=continuous, N=run N periods then stop
 *   ch_base + 0x20  status       — [0]=config_error (falling>period), [1]=shot_done (read-only)
 */

/* channel_config register fields */
#define PWM_CHANNEL_COUNT(reg)    ((reg) & 0xFF)
#define PWM_CLK_DIV_WIDTH(reg)    (((reg) >> 8) & 0xFF)
#define PWM_PULSE_WIDTH(reg)      (((reg) >> 16) & 0xFF)
#define PWM_PERIOD_WIDTH(reg)     (((reg) >> 24) & 0xFF)

/* timing_config register fields */
#define PWM_DEAD_TIME_WIDTH(reg)  ((reg) & 0xFF)
#define PWM_SHOT_COUNT_WIDTH(reg) (((reg) >> 8) & 0xFF)

/* control register bits */
#define PWM_CTRL_ENABLE           (1U << 0)
#define PWM_CTRL_INVERT           (1U << 1)
#define PWM_CTRL_MODE_CENTER      (1U << 2)

/* status register bits */
#define PWM_STATUS_CONFIG_ERROR   (1U << 0)
#define PWM_STATUS_SHOT_DONE      (1U << 1)

/* error_pending / error_enable bits */
#define PWM_ERROR_FAULT_IN        (1U << 0)
/* bit (1 + ch) = channel ch config error */

#define PWM_CH_STRIDE             0x24U
#define PWM_CH_BASE(ch)           (0x24U + (ch) * PWM_CH_STRIDE)

struct pwm_regs {
	unsigned int ip_header;      /* 0x000 */
	unsigned int ip_version;     /* 0x004 */
	unsigned int channel_config; /* 0x008 */
	unsigned int timing_config;  /* 0x00C */
	unsigned int permissions;    /* 0x010 */
	unsigned int irq_pending;    /* 0x014 */
	unsigned int irq_enable;     /* 0x018 */
	unsigned int error_pending;  /* 0x01C */
	unsigned int error_enable;   /* 0x020 */
};

struct pwm_channel_regs {
	unsigned int control;        /* +0x00 */
	unsigned int clock_div;      /* +0x04 */
	unsigned int period;         /* +0x08 */
	unsigned int rising_edge;    /* +0x0C */
	unsigned int falling_edge;   /* +0x10 */
	unsigned int dead_time;      /* +0x14 */
	unsigned int phase_offset;   /* +0x18 */
	unsigned int shot_count;     /* +0x1C */
	unsigned int status;         /* +0x20 */
};

struct pwm_driver {
	volatile struct pwm_regs *regs;
	unsigned int channel_count;
};

void pwm_init(struct pwm_driver *driver, unsigned long base_address);

static inline volatile struct pwm_channel_regs *
pwm_channel(struct pwm_driver *driver, unsigned int ch)
{
	return (volatile struct pwm_channel_regs *)
		((unsigned char *)driver->regs + PWM_CH_BASE(ch));
}

unsigned int pwm_channel_count(struct pwm_driver *driver);

void pwm_channel_enable(struct pwm_driver *driver, unsigned int ch);
void pwm_channel_disable(struct pwm_driver *driver, unsigned int ch);
void pwm_channel_set_invert(struct pwm_driver *driver, unsigned int ch, int invert);
void pwm_channel_set_mode_center(struct pwm_driver *driver, unsigned int ch, int center);

void pwm_channel_set_clock_div(struct pwm_driver *driver, unsigned int ch, unsigned int div);
void pwm_channel_set_period(struct pwm_driver *driver, unsigned int ch, unsigned int period);
void pwm_channel_set_duty(struct pwm_driver *driver, unsigned int ch,
	unsigned int rising, unsigned int falling);
void pwm_channel_set_dead_time(struct pwm_driver *driver, unsigned int ch, unsigned int dt);
void pwm_channel_set_phase_offset(struct pwm_driver *driver, unsigned int ch, unsigned int offset);
void pwm_channel_set_shot_count(struct pwm_driver *driver, unsigned int ch, unsigned int count);

unsigned int pwm_channel_status(struct pwm_driver *driver, unsigned int ch);

void pwm_irq_enable(struct pwm_driver *driver, unsigned int channel_mask);
void pwm_irq_disable(struct pwm_driver *driver, unsigned int channel_mask);
unsigned int pwm_irq_pending(struct pwm_driver *driver);
void pwm_irq_clear(struct pwm_driver *driver, unsigned int channel_mask);

#endif
