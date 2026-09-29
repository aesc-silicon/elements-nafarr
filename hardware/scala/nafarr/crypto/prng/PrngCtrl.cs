// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr PRNG (nafarr.crypto.prng.PrngCtrl), mirroring PrngCtrl.scala.
// The output is the same 32-bit Galois LFSR (x^32 + x^30 + x^26 + x^25 + 1, reset state 1)
// as the RTL. The RTL advances the LFSR once per enabled clock cycle; this model advances it
// once per enabled output read, so it produces the RTL sequence but not the RTL sample points.
// Writing a zero seed does not reseed and latches errorPending bit 0 instead. errorPending
// reads return pending & mask, as in the RTL. The error path feeds the ESM in hardware; here
// it is just register state.
//
// Register map (offsets from base; Regs base = IpIdentification.length = 8):
//   0x00 header       (RO)  IpIdentification
//   0x04 version      (RO)
//   0x08 control      (RW)  bit0 = enable
//   0x0C errorPending (R/W1C)
//   0x10 errorMask    (RW)
//   0x14 seed         (WO)  reseeds the generator (zero sets errorPending bit 0)
//   0x18 output       (RO)  current LFSR state
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class PrngCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public PrngCtrl(IMachine machine)
        {
            Reset();
        }

        public void Reset()
        {
            enable = true;
            errorPending = 0;
            errorMask = 0;
            state = 1;
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:       return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset:      return 0x01000000; // 1.0.0
                case ControlOffset:      return enable ? 1u : 0u;
                case ErrorPendingOffset: return (uint)(errorPending & errorMask);
                case ErrorMaskOffset:    return (uint)errorMask;
                case OutputOffset:       return ReadOutput();
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            switch(offset)
            {
                case ControlOffset:      enable = (value & 0x1) != 0; return;
                case ErrorPendingOffset: errorPending &= ~(int)value; return; // write-1-to-clear
                case ErrorMaskOffset:    errorMask = (int)value & ErrorMask; return;
                case SeedOffset:         WriteSeed(value); return;
                case HeaderOffset:
                case VersionOffset:
                case OutputOffset:
                    this.Log(LogLevel.Warning, "Write to read-only register at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }
        }

        public long Size => 0x1000;

        private uint ReadOutput()
        {
            var value = state;

            if(enable)
            {
                // Galois step: shift left, feed the MSB back into bit 0 and taps 30, 26, 25.
                var feedback = (state & 0x80000000u) != 0;
                state <<= 1;
                if(feedback)
                {
                    state ^= GaloisTaps;
                }
            }
            return value;
        }

        private void WriteSeed(uint value)
        {
            if(value == 0)
            {
                errorPending |= ZeroSeedError;
                this.Log(LogLevel.Warning, "Zero seed rejected");
                return;
            }
            state = value;
        }

        private bool enable;
        private int errorPending;
        private int errorMask;
        private uint state;

        // IpIdentification header constants (PrngCtrl.scala: id = Prng = 16, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 16;

        private const uint GaloisTaps = 0x46000001u;
        private const int ZeroSeedError = 0x1;
        private const int ErrorMask = 0x1;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long ControlOffset = 0x08;
        private const long ErrorPendingOffset = 0x0C;
        private const long ErrorMaskOffset = 0x10;
        private const long SeedOffset = 0x14;
        private const long OutputOffset = 0x18;
    }
}
