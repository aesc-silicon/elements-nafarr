module PLLE2_BASE (
	input  CLKIN1,
	input  RST,
	input  PWRDWN,
	output CLKOUT0,
	output CLKOUT1,
	output CLKOUT2,
	output CLKOUT3,
	output CLKOUT4,
	output CLKOUT5,
	output LOCKED,
	output CLKFBOUT,
	input  CLKFBIN
);

parameter CLKIN1_PERIOD = 10;
parameter DIVCLK_DIVIDE = 1;
parameter CLKFBOUT_MULT = 5;
parameter CLKFBOUT_PHASE = 0.0;
parameter CLKOUT0_DIVIDE = 1;
parameter CLKOUT0_PHASE = 0.0;
parameter CLKOUT0_DUTY_CYCLE = 0.5;
parameter CLKOUT1_DIVIDE = 1;
parameter CLKOUT1_PHASE = 0.0;
parameter CLKOUT1_DUTY_CYCLE = 0.5;
parameter CLKOUT2_DIVIDE = 1;
parameter CLKOUT2_PHASE = 0.0;
parameter CLKOUT2_DUTY_CYCLE = 0.5;
parameter CLKOUT3_DIVIDE = 1;
parameter CLKOUT3_PHASE = 0.0;
parameter CLKOUT3_DUTY_CYCLE = 0.5;
parameter CLKOUT4_DIVIDE = 1;
parameter CLKOUT4_PHASE = 0.0;
parameter CLKOUT4_DUTY_CYCLE = 0.5;
parameter CLKOUT5_DIVIDE = 1;
parameter CLKOUT5_PHASE = 0.0;
parameter CLKOUT5_DUTY_CYCLE = 0.5;

/* Add dummy values */
assign LOCKED = 1'b0;
assign CLKFBOUT = 1'b0;

// CLKIN1_CLOCK = 1000000000 / CLKIN1_PERIOD
// CLKIN1_PERIOD * CLKFBOUT_MULT

endmodule
