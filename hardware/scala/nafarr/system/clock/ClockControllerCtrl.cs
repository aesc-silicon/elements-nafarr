// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr clock controller (nafarr.system.clock, register mapping in
// ClockController.scala). Register-accurate: per-domain enable is RW, the running bit and the
// mult/div ratio are read-only. NOTE: ratios default to 1/1 (placeholder) - the real per-domain
// mult/div come from the SoC's synthesis-time clock config; use co-sim for exact rates.
//
// Register map (offsets from base; Regs base = IpIdentification.length = 8):
//   0x00 header   (RO)  IpIdentification
//   0x04 version  (RO)
//   0x08 domains  (RO)  number of clock domains [7:0]
//   0x10 + i*8  control(i)  bit31 enable (RW), bit30 running (RO, 1)
//   0x14 + i*8  ratio(i)    div[15:0] | mult[31:16] (RO); rate = reference * mult / div
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class ClockControllerCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public ClockControllerCtrl(IMachine machine, int numberOfDomains = 1, int mult = 1, int div = 1)
        {
            this.numberOfDomains = numberOfDomains;
            this.mult = mult;
            this.div = div;
            enable = new bool[numberOfDomains];
            Reset();
        }

        public void Reset()
        {
            for(var i = 0; i < numberOfDomains; i++)
            {
                enable[i] = true; // domains run by default
            }
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:  return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset: return 0x01010000; // 1.1.0
                case DomainsOffset: return (uint)(numberOfDomains & 0xFF);
            }
            if(TryDomain(offset, ControlOffset, out var ci))
            {
                return (uint)((enable[ci] ? (1u << 31) : 0u) | (1u << 30)); // running always set
            }
            if(TryDomain(offset, RatioOffset, out var ri))
            {
                return (uint)(((mult & 0xFFFF) << 16) | (div & 0xFFFF));
            }
            this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
            return 0;
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            if(TryDomain(offset, ControlOffset, out var ci))
            {
                enable[ci] = (value & (1u << 31)) != 0;
                return;
            }
            if(TryDomain(offset, RatioOffset, out _))
            {
                this.Log(LogLevel.Warning, "Write to read-only ratio register at offset 0x{0:X}", offset);
                return;
            }
            this.Log(LogLevel.Warning, "Write to read-only/unhandled register at offset 0x{0:X}", offset);
        }

        public long Size => 0x1000;

        // Per-domain block stride is 8: control at FirstBlock + i*8, ratio at FirstBlock + 4 + i*8.
        private bool TryDomain(long offset, long regInBlock, out int index)
        {
            index = 0;
            if(offset < FirstBlockOffset)
            {
                return false;
            }
            var rel = offset - FirstBlockOffset;
            if(rel % BlockStride != regInBlock)
            {
                return false;
            }
            index = (int)(rel / BlockStride);
            return index < numberOfDomains;
        }

        private readonly int numberOfDomains;
        private readonly int mult;
        private readonly int div;
        private readonly bool[] enable;

        // IpIdentification header constants (ClockController.scala: id = Clock = 12, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 12;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long DomainsOffset = 0x08;
        private const long FirstBlockOffset = 0x10;
        private const long BlockStride = 0x08;
        private const long ControlOffset = 0x00; // within block
        private const long RatioOffset = 0x04;   // within block
    }
}
