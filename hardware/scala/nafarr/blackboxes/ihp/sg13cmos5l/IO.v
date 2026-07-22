// Copyright 2024 IHP PDK Authors
// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: Apache-2.0

// type: Input
`timescale 1ns/10ps
`celldefine
module sg13cmos5l_IOPadIn (iovdd, iovss, vdd, vss, pad, p2c);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadOut4mA (iovdd, iovss, vdd, vss, pad, c2p);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadOut16mA (iovdd, iovss, vdd, vss, pad, c2p);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadOut30mA (iovdd, iovss, vdd, vss, pad, c2p);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadTriOut4mA (iovdd, iovss, vdd, vss, pad, c2p, c2p_en);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadTriOut16mA (iovdd, iovss, vdd, vss, pad, c2p, c2p_en);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadTriOut30mA (iovdd, iovss, vdd, vss, pad, c2p, c2p_en);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadInOut4mA (iovdd, iovss, vdd, vss, pad, c2p, c2p_en, p2c);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadInOut16mA (iovdd, iovss, vdd, vss, pad, c2p, c2p_en, p2c);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadInOut30mA (iovdd, iovss, vdd, vss, pad, c2p, c2p_en, p2c);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadAnalog (iovdd, iovss, vdd, vss, pad, padres);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
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
module sg13cmos5l_IOPadIOVss (iovdd, iovss, vdd, vss);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
endmodule
`endcelldefine

// type: IOVdd
`timescale 1ns/10ps
`celldefine
module sg13cmos5l_IOPadIOVdd (iovdd, iovss, vdd, vss);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
endmodule
`endcelldefine

// type: Vss
`timescale 1ns/10ps
`celldefine
module sg13cmos5l_IOPadVss (iovdd, iovss, vdd, vss);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
endmodule
`endcelldefine

// type: Vdd
`timescale 1ns/10ps
`celldefine
module sg13cmos5l_IOPadVdd (iovdd, iovss, vdd, vss);
	inout iovdd;
	inout iovss;
	inout vdd;
	inout vss;
endmodule
`endcelldefine
