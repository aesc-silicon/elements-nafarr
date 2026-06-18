// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr pinmux (nafarr.peripherals.pinmux.PinmuxCtrl), mirroring PinmuxCtrl.scala.
// Register-accurate: stores the per-pin mux selection. Renode does not model physical pad routing,
// so the selection is read/write state only (drivers can program and read it back).
//
// Register map (offsets from base; Regs base = IpIdentification.length = 8):
//   0x00 header   (RO)  IpIdentification
//   0x04 version  (RO)
//   0x08 info     (RO)  options[15:8] | width[7:0]
//   0x10 + pin*4  option(pin) (RW)  selected mux option for that pin
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class PinmuxCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public PinmuxCtrl(IMachine machine, int width = 12, int options = 2)
        {
            this.width = width;
            this.options = options;
            option = new uint[width];
            Reset();
        }

        public void Reset()
        {
            for(var i = 0; i < width; i++)
            {
                option[i] = 0;
            }
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:  return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset: return 0x01000000; // 1.0.0
                case InfoOffset:    return (uint)(((options & 0xFF) << 8) | (width & 0xFF));
            }
            if(TryPin(offset, out var pin))
            {
                return option[pin];
            }
            this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
            return 0;
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            if(TryPin(offset, out var pin))
            {
                option[pin] = value;
                return;
            }
            this.Log(LogLevel.Warning, "Write to read-only/unhandled register at offset 0x{0:X}", offset);
        }

        public long Size => 0x1000;

        private bool TryPin(long offset, out int pin)
        {
            pin = 0;
            if(offset < OptionBaseOffset)
            {
                return false;
            }
            var rel = offset - OptionBaseOffset;
            if(rel % 4 != 0)
            {
                return false;
            }
            pin = (int)(rel / 4);
            return pin < width;
        }

        private readonly int width;
        private readonly int options;
        private readonly uint[] option;

        // IpIdentification header constants (PinmuxCtrl.scala: id = Pinmux = 13, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 13;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long InfoOffset = 0x08;
        private const long OptionBaseOffset = 0x10;
    }
}
