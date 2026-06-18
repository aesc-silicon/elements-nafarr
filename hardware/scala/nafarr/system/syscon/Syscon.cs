// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr syscon (nafarr.system.syscon.Syscon). All registers are read-only
// constants (IpIdentification + identity/siliconRev/buildDate/refClock/featureInfo/features),
// mirroring Syscon.scala's Mapper. Values are constructor parameters so each SoC can supply its own.
//
// Register map (offsets from base; Regs base = IpIdentification.length = 8):
//   0x00 header     (RO)  IpIdentification
//   0x04 version    (RO)
//   0x08 identity   (RO)  platformClass[31:24] | product[23:16] | platform[15:8] | vendor[7:0]
//   0x0C siliconRev (RO)  siliconMajor[31:16] | siliconMinor[15:0]
//   0x10 buildDate  (RO)
//   0x14 refClock   (RO)  reference clock in Hz
//   0x18 featureInfo(RO)  featureRegCount[7:0]
//   0x1C + i*4 features(i) (RO)  feature bitmask, 32 ordinals per register
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class Syscon : IDoubleWordPeripheral, IKnownSize
    {
        public Syscon(IMachine machine, uint refClockHz = 50000000, uint identity = 0,
                      uint siliconMajor = 0, uint siliconMinor = 0, uint buildDate = 0,
                      ulong featureMask = 0)
        {
            this.refClockHz = refClockHz;
            this.identity = identity;
            this.siliconRev = (siliconMajor << 16) | (siliconMinor & 0xFFFF);
            this.buildDate = buildDate;
            this.featureMask = featureMask;
            this.featureRegCount = featureMask == 0 ? 1 : (BitLength(featureMask) + 31) / 32;
        }

        public void Reset()
        {
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:     return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset:    return 0x01000000; // 1.0.0
                case IdentityOffset:   return identity;
                case SiliconRevOffset: return siliconRev;
                case BuildDateOffset:  return buildDate;
                case RefClockOffset:   return refClockHz;
                case FeatureInfoOffset:return featureRegCount & 0xFF;
            }
            if(offset >= FeaturesOffset && offset < FeaturesOffset + featureRegCount * 4)
            {
                var idx = (int)((offset - FeaturesOffset) / 4);
                return (uint)((featureMask >> (idx * 32)) & 0xFFFFFFFF);
            }
            this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
            return 0;
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            this.Log(LogLevel.Warning, "Write to read-only syscon register at offset 0x{0:X}", offset);
        }

        public long Size => 0x1000;

        private static uint BitLength(ulong v)
        {
            uint n = 0;
            while(v != 0) { n++; v >>= 1; }
            return n;
        }

        private readonly uint refClockHz;
        private readonly uint identity;
        private readonly uint siliconRev;
        private readonly uint buildDate;
        private readonly ulong featureMask;
        private readonly uint featureRegCount;

        // IpIdentification header constants (Syscon.scala: id = Syscon = 24, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 24;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long IdentityOffset = 0x08;
        private const long SiliconRevOffset = 0x0C;
        private const long BuildDateOffset = 0x10;
        private const long RefClockOffset = 0x14;
        private const long FeatureInfoOffset = 0x18;
        private const long FeaturesOffset = 0x1C;
    }
}
