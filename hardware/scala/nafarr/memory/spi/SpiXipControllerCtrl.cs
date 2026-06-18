// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr SPI XIP controller's config bus (nafarr.memory.spi.SpiXipControllerCtrl
// Mapper). Register-accurate config interface only — it does NOT perform SPI/XIP flash transactions
// (the XIP data path is modeled by a plain flash memory in the platform). The bootrom programs this
// to configure the flash mode/dummy-cycles; here those writes are stored and read back.
//
// Register map (offsets from base; regOffset = IpIdentification.length = 8):
//   0x00 header    (RO)  IpIdentification
//   0x04 version   (RO)
//   0x08 configure (WO)  write triggers a (re)configuration; reads as 0
//   0x0C config    (RW)  mode[7:0] | dummyCycles[15:8] | evcr[23:16]
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class SpiXipControllerCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public SpiXipControllerCtrl(IMachine machine)
        {
            Reset();
        }

        public void Reset()
        {
            mode = 0;
            dummyCycles = 0;
            evcr = 0;
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:    return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset:   return 0x01000000; // 1.0.0
                case ConfigureOffset: return 0;          // write-only trigger
                case ConfigOffset:    return (uint)(((evcr & 0xFF) << 16) | ((dummyCycles & 0xFF) << 8) | (mode & 0xFF));
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            switch(offset)
            {
                case ConfigureOffset:
                    this.Log(LogLevel.Debug, "SPI XIP (re)configuration triggered");
                    return;
                case ConfigOffset:
                    mode = (int)(value & 0xFF);
                    dummyCycles = (int)((value >> 8) & 0xFF);
                    evcr = (int)((value >> 16) & 0xFF);
                    return;
                case HeaderOffset:
                case VersionOffset:
                    this.Log(LogLevel.Warning, "Write to read-only register at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }
        }

        public long Size => 0x1000;

        private int mode;
        private int dummyCycles;
        private int evcr;

        // IpIdentification header constants (SpiXipControllerCtrl.scala: id = SpiXipController = 7, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 7;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long ConfigureOffset = 0x08;
        private const long ConfigOffset = 0x0C;
    }
}
