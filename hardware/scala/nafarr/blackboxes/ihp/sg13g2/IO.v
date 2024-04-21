module sg13g2_IOPadIn (
  output wire p2c,
  inout  wire pad 
);

assign p2c = pad;

endmodule

module sg13g2_IOPadOut4mA (
  input  wire c2p,
  inout  wire pad 
);

assign pad = c2p;

endmodule

module sg13g2_IOPadOut16mA (
  input  wire c2p,
  inout  wire pad 
);

assign pad = c2p;

endmodule

module sg13g2_IOPadOut30mA (
  input  wire c2p,
  inout  wire pad 
);

assign pad = c2p;

endmodule

module sg13g2_IOPadTriOut4mA (
  input  wire c2p,
  input  wire c2p_en,
  inout  wire pad 
);

assign pad = (!c2p_en) ? c2p : 1'bz;

endmodule

module sg13g2_IOPadTriOut16mA (
  input  wire c2p,
  input  wire c2p_en,
  inout  wire pad 
);

assign pad = (!c2p_en) ? c2p : 1'bz;

endmodule

module sg13g2_IOPadTriOut30mA (
  input  wire c2p,
  input  wire c2p_en,
  inout  wire pad 
);

assign pad = (!c2p_en) ? c2p : 1'bz;

endmodule

module sg13g2_IOPadInOut4mA (
  input  wire c2p,
  input  wire c2p_en,
  output wire p2c,
  inout  wire pad 
);

assign pad = (!c2p_en) ? c2p : 1'bz;
assign p2c = pad;

endmodule

module sg13g2_IOPadInOut16mA (
  input  wire c2p,
  input  wire c2p_en,
  output wire p2c,
  inout  wire pad 
);

assign pad = (!c2p_en) ? c2p : 1'bz;
assign p2c = pad;

endmodule

module sg13g2_IOPadInOut30mA (
  input  wire c2p,
  input  wire c2p_en,
  output wire p2c,
  inout  wire pad 
);

assign pad = (!c2p_en) ? c2p : 1'bz;
assign p2c = pad;

endmodule



