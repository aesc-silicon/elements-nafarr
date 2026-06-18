// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode functional model of the Nafarr machine timer (nafarr.system.mtimer.MachineTimerCtrl).
//
// Register map (offsets from base), mirroring MachineTimerCtrl.Mapper:
//   0x00  counter low   (RO)  mtime[31:0]
//   0x04  counter high  (RO)  mtime[63:32]
//   0x08  compare low   (RW)  mtimecmp[31:0]
//   0x0C  compare high  (RW)  mtimecmp[63:32]
//
// Behavioral notes:
//   * The counter is "locked" (frozen) after reset and only starts incrementing
//     once the firmware writes a compare register - matching the RTL's lock/clear logic.
//   * The interrupt (MTIP) asserts when counter >= compare and is cleared by writing
//     a compare register. Wire IRQ to the CPU machine-timer line, e.g. `-> cpu@7`.
//
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;
using Antmicro.Renode.Peripherals.Timers;

namespace Antmicro.Renode.Peripherals.Timers
{
    public class MachineTimerCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public MachineTimerCtrl(IMachine machine, long frequency = 50000000)
        {
            IRQ = new GPIO();
            timer = new ComparingTimer(machine.ClockSource, (ulong)frequency, this, "mtime",
                                       limit: ulong.MaxValue, enabled: false, eventEnabled: true);
            timer.CompareReached += OnCompareReached;
            Reset();
        }

        public void Reset()
        {
            timer.Enabled = false;
            timer.Value = 0;
            compare = ulong.MaxValue;
            timer.Compare = compare;
            hit = false;
            IRQ.Set(false);
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case CounterLowOffset:
                    return (uint)(timer.Value & 0xFFFFFFFF);
                case CounterHighOffset:
                    return (uint)(timer.Value >> 32);
                case CompareLowOffset:
                    return (uint)(compare & 0xFFFFFFFF);
                case CompareHighOffset:
                    return (uint)(compare >> 32);
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            switch(offset)
            {
                case CompareLowOffset:
                    compare = (compare & 0xFFFFFFFF00000000) | value;
                    break;
                case CompareHighOffset:
                    compare = (compare & 0x00000000FFFFFFFF) | ((ulong)value << 32);
                    break;
                case CounterLowOffset:
                case CounterHighOffset:
                    this.Log(LogLevel.Warning, "Write to read-only counter at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }

            // Writing a compare register releases the lock and clears the pending interrupt.
            timer.Compare = compare;
            timer.Enabled = true;
            hit = false;
            IRQ.Set(false);
        }

        public long Size => 0x10;
        public GPIO IRQ { get; }

        private void OnCompareReached()
        {
            hit = true;
            IRQ.Set(true);
        }

        private ulong compare;
        private bool hit;
        private readonly ComparingTimer timer;

        private const long CounterLowOffset = 0x00;
        private const long CounterHighOffset = 0x04;
        private const long CompareLowOffset = 0x08;
        private const long CompareHighOffset = 0x0C;
    }
}
