#ifndef BOOTROM_GPIO
#define BOOTROM_GPIO

struct gpio_regs {
	int data_in;
	int data_out;
	int dir_en;
	int irq_high_mask;
	int irq_high_pending;
	int irq_low_mask;
	int irq_low_pending;
	int irq_falling_mask;
	int irq_falling_pending;
	int irq_rising_mask;
	int irq_rising_pending;
};

int GPIO_init(void);
unsigned int gpio_value_get(unsigned int pin);
void gpio_value_set(unsigned int pin);
void gpio_value_clr(unsigned int pin);

void gpio_dir_set(unsigned int pin);
void gpio_dir_clr(unsigned int pin);

#endif
