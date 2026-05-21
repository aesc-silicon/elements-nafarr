# SPDX-FileCopyrightText: 2026 aesc silicon
#
# SPDX-License-Identifier: Apache-2.0

DRIVERS  := clock crc32 esm gpio mailbox mtimer pinmux pio plic prng pwm reset semaphore syscon timer uart watchdog
CC       := gcc
CFLAGS   := -Wall -Werror -I../../software/include
SRCDIR   := ../../software/driver
TESTDIR  := testcases
BUILDDIR := build

TESTS    := $(addprefix $(BUILDDIR)/test_, $(DRIVERS))

.PHONY: all run clean

all: $(TESTS)

$(BUILDDIR):
	mkdir -p $(BUILDDIR)

define DRIVER_RULE
$(BUILDDIR)/test_$(1): $(TESTDIR)/test_$(1).c $(BUILDDIR)/$(1).o | $(BUILDDIR)
	$(CC) $(CFLAGS) $$^ -o $$@

$(BUILDDIR)/$(1).o: $(SRCDIR)/$(1).c | $(BUILDDIR)
	$(CC) $(CFLAGS) -c $$< -o $$@
endef

$(foreach d,$(DRIVERS),$(eval $(call DRIVER_RULE,$(d))))

run: all
	@failed=0; \
	for t in $(TESTS); do \
		printf "%-50s " "$$t"; \
		if ./$$t; then echo "PASS"; else echo "FAIL"; failed=$$((failed + 1)); fi; \
	done; \
	[ $$failed -eq 0 ]

clean:
	rm -rf $(BUILDDIR)
