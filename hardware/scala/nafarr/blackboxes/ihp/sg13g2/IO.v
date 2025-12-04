// Copyright 2024 IHP PDK Authors
// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: Apache-2.0

// type: Input
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadIn (pad, p2c);
	inout pad;
	output p2c;

	// Function
	assign p2c = pad;

	// Timing
	specify
		(p2c => pad) = 0;
	endspecify
endmodule
`endcelldefine

// type: Output4mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadOut4mA (pad, c2p);
	inout pad;
	input c2p;

	// Function
	assign pad = c2p;

	// Timing
	specify
		(pad => c2p) = 0;
	endspecify
endmodule
`endcelldefine

// type: Output16mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadOut16mA (pad, c2p);
	inout pad;
	input c2p;

	// Function
	assign pad = c2p;

	// Timing
	specify
		(pad => c2p) = 0;
	endspecify
endmodule
`endcelldefine

// type: Output30mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadOut30mA (pad, c2p);
	inout pad;
	input c2p;

	// Function
	assign pad = c2p;

	// Timing
	specify
		(pad => c2p) = 0;
	endspecify
endmodule
`endcelldefine

// type: TriStateOutput4mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadTriOut4mA (pad, c2p, c2p_en);
	inout pad;
	input c2p;
	input c2p_en;

	// Function
	assign pad = (c2p_en) ? c2p : 1'bz;

	// Timing
	specify
		(pad => c2p) = 0;
	endspecify
endmodule
`endcelldefine

// type: TriStateOutput16mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadTriOut16mA (pad, c2p, c2p_en);
	inout pad;
	input c2p;
	input c2p_en;

	// Function
	assign pad = (c2p_en) ? c2p : 1'bz;

	// Timing
	specify
		(pad => c2p) = 0;
	endspecify
endmodule
`endcelldefine

// type: TriStateOutput30mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadTriOut30mA (pad, c2p, c2p_en);
	inout pad;
	input c2p;
	input c2p_en;

	// Function
	assign pad = (c2p_en) ? c2p : 1'bz;

	// Timing
	specify
		(pad => c2p) = 0;
	endspecify
endmodule
`endcelldefine

// type: InputOutput4mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadInOut4mA (pad, c2p, c2p_en, p2c);
	inout pad;
	input c2p;
	input c2p_en;
	output p2c;

	// Function
	assign pad = (c2p_en) ? c2p : 1'bz;
	assign p2c = pad;

	// Timing
	specify
		(pad => c2p) = 0;
		(p2c => pad) = 0;
	endspecify
endmodule
`endcelldefine

// type: InputOutput4mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadInOut16mA (pad, c2p, c2p_en, p2c);
	inout pad;
	input c2p;
	input c2p_en;
	output p2c;

	// Function
	assign pad = (c2p_en) ? c2p : 1'bz;
	assign p2c = pad;

	// Timing
	specify
		(pad => c2p) = 0;
		(p2c => pad) = 0;
	endspecify
endmodule
`endcelldefine

// type: InputOutput4mA
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadInOut30mA (pad, c2p, c2p_en, p2c);
	inout pad;
	input c2p;
	input c2p_en;
	output p2c;

	// Function
	assign pad = (c2p_en) ? c2p : 1'bz;
	assign p2c = pad;

	// Timing
	specify
		(pad => c2p) = 0;
		(p2c => pad) = 0;
	endspecify
endmodule
`endcelldefine

// type: Analog
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadAnalog (pad, padres);
	inout pad;
	inout padres;

	// Function
	assign pad = padres;
	assign padres = pad;

	// Timing
	specify
		(pad => padres) = 0;
		(padres => pad) = 0;
	endspecify
endmodule
`endcelldefine

// type: IOVss
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadIOVss ();
endmodule
`endcelldefine

// type: IOVdd
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadIOVdd ();
endmodule
`endcelldefine

// type: Vss
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadVss ();
endmodule
`endcelldefine

// type: Vdd
`timescale 1ns/10ps
`celldefine
module sg13g2_IOPadVdd ();
endmodule
`endcelldefine
