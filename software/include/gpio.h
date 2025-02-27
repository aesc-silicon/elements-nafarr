#ifndef ELEMNETS_GPIO
#define ELEMNETS_GPIO

struct gpio_regs {
	int data_in;
	int data_out;
	int dir_en;
	int reserved;
	int irq_high_pending;
	int irq_high_mask;
	int irq_low_pending;
	int irq_low_mask;
	int irq_rising_pending;
	int irq_rising_mask;
	int irq_falling_pending;
	int irq_falling_mask;
};

struct gpio_driver {
	volatile struct gpio_regs *regs;
};

#define GPIO_IRQ_LEVEL_HIGH		(1 << 0)
#define GPIO_IRQ_LEVEL_LOW		(1 << 1)
#define GPIO_IRQ_RISING_EDGE		(1 << 2)
#define GPIO_IRQ_FALLING_EDGE		(1 << 3)

int gpio_init(struct gpio_driver *driver, unsigned int base_address);
unsigned int gpio_value_get(struct gpio_driver *driver, unsigned int pin);
void gpio_value_set(struct gpio_driver *driver, unsigned int pin);
void gpio_value_clr(struct gpio_driver *driver, unsigned int pin);
void gpio_dir_set(struct gpio_driver *driver, unsigned int pin);
void gpio_dir_clr(struct gpio_driver *driver, unsigned int pin);
int gpio_irq_enable(struct gpio_driver *driver, unsigned int pin, int flags);
int gpio_irq_disable(struct gpio_driver *driver, unsigned int pin, int flags);
int gpio_irq_ready(struct gpio_driver *driver, unsigned int pin, int flags);

#endif
