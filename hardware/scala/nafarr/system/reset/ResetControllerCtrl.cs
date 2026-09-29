// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr reset controller (nafarr.system.reset, register mapping in
// ResetController.scala). A software reset is requested by writing the domain bits to trigger
// and then writing acknowledge; all requested domains are reset at once and trigger is cleared.
//
// Acknowledging the system domain (systemDomain, default 0) requests a reset of the whole
// machine: CPU and all peripherals are reset and the machine's `reset` macro runs, so the
// .resc must reload the firmware there. Other domains (debug, flash) are only logged.
// The per-domain reset delay and the hardware triggers gated by enable are not modeled.
//
// Register map (offsets from base; Regs base = IpIdentification.length = 8):
//   0x00 header      (RO)  IpIdentification
//   0x04 version     (RO)
//   0x08 domains     (RO)  number of reset domains [7:0]
//   0x0C enable      (RW)  enables hardware triggers per domain, all set after reset
//   0x10 trigger     (RW)  requested domains
//   0x14 acknowledge (WO)  resets the requested domains and clears trigger
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class ResetControllerCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public ResetControllerCtrl(IMachine machine, int numberOfDomains = 3, int systemDomain = 0)
        {
            this.machine = machine;
            this.numberOfDomains = numberOfDomains;
            this.systemDomain = systemDomain;
            domainMask = numberOfDomains >= 32 ? uint.MaxValue : (1u << numberOfDomains) - 1;
            Reset();
        }

        public void Reset()
        {
            enable = domainMask;
            trigger = 0;
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:  return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset: return 0x01000000; // 1.0.0
                case DomainsOffset: return (uint)(numberOfDomains & 0xFF);
                case EnableOffset:  return enable;
                case TriggerOffset: return trigger;
                case AcknowledgeOffset: return 0;
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            switch(offset)
            {
                case EnableOffset:      enable = value & domainMask; return;
                case TriggerOffset:     trigger = value & domainMask; return;
                case AcknowledgeOffset: Acknowledge(); return;
                case HeaderOffset:
                case VersionOffset:
                case DomainsOffset:
                    this.Log(LogLevel.Warning, "Write to read-only register at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }
        }

        public long Size => 0x1000;

        private void Acknowledge()
        {
            var domains = trigger;
            trigger = 0;

            for(var i = 0; i < numberOfDomains; i++)
            {
                if((domains & (1u << i)) != 0 && i != systemDomain)
                {
                    this.Log(LogLevel.Info, "Reset of domain {0} (not modeled)", i);
                }
            }

            if((domains & (1u << systemDomain)) != 0)
            {
                this.Log(LogLevel.Info, "System domain reset, resetting machine");
                machine.RequestReset();
            }
        }

        private readonly IMachine machine;
        private readonly int numberOfDomains;
        private readonly int systemDomain;
        private readonly uint domainMask;
        private uint enable;
        private uint trigger;

        // IpIdentification header constants (ResetController.scala: id = Reset = 11, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 11;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long DomainsOffset = 0x08;
        private const long EnableOffset = 0x0C;
        private const long TriggerOffset = 0x10;
        private const long AcknowledgeOffset = 0x14;
    }
}
