// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr HyperBus controller's config bus (nafarr.memory.hyperbus.HyperBusCtrl
// Mapper). Register-accurate config interface only - it does NOT perform HyperBus transactions. The
// HyperRAM data plane is modeled by a plain MappedMemory in the platform (@ 0x90000000), so the
// reset sequencing, latency programming and register-space accesses the bootrom issues here are
// no-ops: config writes are stored and read back, everything else acks without side effects.
//
// Register map (offsets from base; regOffset = IpIdentification.length = 8):
//   0x00 header       (RO)  IpIdentification (api<<24 | length<<16 | id) = 0x00080019
//   0x04 version      (RO)  major.minor.patch = 1.0.0
//   0x10 resetTrigger (WO)  write triggers the HyperRAM reset pulse; reads as 0
//   0x14 resetPulse   (RW)  reset pulse width (cycles)
//   0x18 resetHalt    (RW)  reset halt width (cycles)
//   0x20 latency      (RW)  latency cycles
//   0x30 register     (RW)  HyperRAM register-space access; no device -> reads as 0
//   0x34 fifoStatus   (RO)  occupancy[15:0] | availability[31:16]; always empty + free
//   0x40 errorPending (W1C) no errors modeled -> reads as 0
//   0x44 errorMask    (RW)  error mask
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class HyperBusCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public HyperBusCtrl(IMachine machine)
        {
            Reset();
        }

        public void Reset()
        {
            resetPulse = 15;
            resetHalt = 15;
            latency = 7;
            errorMask = 0;
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:       return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset:      return 0x01000000; // 1.0.0
                case ResetTriggerOffset: return 0;          // write-only trigger
                case ResetPulseOffset:   return (uint)resetPulse;
                case ResetHaltOffset:    return (uint)resetHalt;
                case LatencyOffset:      return (uint)latency;
                case RegisterOffset:     return 0;          // no HyperRAM device modeled
                case FifoStatusOffset:   return (RegisterFifoDepth << 16); // occupancy 0, availability full
                case ErrorPendingOffset: return 0;          // no errors modeled
                case ErrorMaskOffset:    return (uint)errorMask;
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            switch(offset)
            {
                case ResetTriggerOffset:
                    this.Log(LogLevel.Debug, "HyperRAM reset triggered");
                    return;
                case ResetPulseOffset:   resetPulse = (int)value; return;
                case ResetHaltOffset:    resetHalt = (int)value; return;
                case LatencyOffset:      latency = (int)(value & 0x7); return;
                case RegisterOffset:
                    this.Log(LogLevel.Debug, "HyperRAM register-space write 0x{0:X} (no device modeled)", value);
                    return;
                case ErrorPendingOffset: return; // W1C, nothing pending
                case ErrorMaskOffset:    errorMask = (int)value; return;
                case HeaderOffset:
                case VersionOffset:
                case FifoStatusOffset:
                    this.Log(LogLevel.Warning, "Write to read-only register at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }
        }

        public long Size => 0x1000;

        private int resetPulse;
        private int resetHalt;
        private int latency;
        private int errorMask;

        // IpIdentification header constants (HyperBusCtrl.scala: id = Hyperbus = 25, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 25;
        // registerCmdFifoDepth (HyperBusParameter) - reported as free availability so config never blocks.
        private const uint RegisterFifoDepth = 4;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long ResetTriggerOffset = 0x10;
        private const long ResetPulseOffset = 0x14;
        private const long ResetHaltOffset = 0x18;
        private const long LatencyOffset = 0x20;
        private const long RegisterOffset = 0x30;
        private const long FifoStatusOffset = 0x34;
        private const long ErrorPendingOffset = 0x40;
        private const long ErrorMaskOffset = 0x44;
    }
}
