// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr PRNG (nafarr.crypto.prng.PrngCtrl), mirroring PrngCtrl.scala.
// Register-accurate: the output is a software xorshift32 (seeded via the seed register) so
// reads return a varying stream — it does NOT reproduce the RTL LFSR sequence (use co-sim for
// that). The error path feeds the ESM in hardware; here it is just register state.
//
// Register map (offsets from base; Regs base = IpIdentification.length = 8):
//   0x00 header       (RO)  IpIdentification
//   0x04 version      (RO)
//   0x08 control      (RW)  bit0 = enable
//   0x0C errorPending (R/W1C)
//   0x10 errorMask    (RW)
//   0x14 seed         (WO)  reseeds the generator
//   0x18 output       (RO)  next pseudo-random word
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
            enable = false;
            errorPending = 0;
            errorMask = 0;
            state = 0x1234567u; // non-zero default so output is not stuck at 0
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:       return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset:      return 0x01000000; // 1.0.0
                case ControlOffset:      return enable ? 1u : 0u;
                case ErrorPendingOffset: return (uint)errorPending;
                case ErrorMaskOffset:    return (uint)errorMask;
                case OutputOffset:       return enable ? NextRandom() : 0u;
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
                case ErrorMaskOffset:    errorMask = (int)value; return;
                case SeedOffset:         state = value == 0 ? 0x1234567u : value; return;
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

        private uint NextRandom()
        {
            // xorshift32
            state ^= state << 13;
            state ^= state >> 17;
            state ^= state << 5;
            return state;
        }

        private bool enable;
        private int errorPending;
        private int errorMask;
        private uint state;

        // IpIdentification header constants (PrngCtrl.scala: id = Prng = 16, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 16;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long ControlOffset = 0x08;
        private const long ErrorPendingOffset = 0x0C;
        private const long ErrorMaskOffset = 0x10;
        private const long SeedOffset = 0x14;
        private const long OutputOffset = 0x18;
    }
}
