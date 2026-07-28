// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode functional model of the Nafarr general-purpose timer (nafarr.system.timer.TimerCtrl),
// for ElemRV-N's single-timer / single-channel / 16-bit / 8-bit-prescaler configuration
// (TimerCtrl.Parameter.small()). Register-accurate; the counter advances in virtual time and
// raises IRQ on compare/overflow so timer-driven firmware runs. It is NOT a co-simulated RTL model
// (the kernel tick uses the machine timer); it exists so the general-purpose timer can stay
// functional and out of the co-simulation for speed. Wire IRQ to its PLIC source, e.g. `-> plic@2`.
//
// Register map (offsets from base; regOffset = IpIdentification.length = 8):
//   0x00 header    (RO)  IpIdentification (api<<24 | length<<16 | id=23) = 0x00080017
//   0x04 version   (RO)  1.0.0
//   0x08 info      (RO)  prescalerWidth<<24 | width<<16 | channelCount<<8 | count = 0x08100101
//   0x0C irqPending(W1C)  [0]=overflow [1]=compare-match; write-1-to-clear
//   0x10 irqMask   (RW)  output-IRQ enable per source
//   0x14 control   (RW)  [0]=enable [2:1]=mode (00=free-run 01=periodic 10=one-shot)
//   0x18 prescaler (RW)  clock divider (tick every prescaler+1)
//   0x1C counter   (RW)  read live value; write preloads while stopped
//   0x20 reload    (RW)  reload value (periodic)
//   0x24 compare   (RW)  channel-0 compare value (period in periodic/one-shot)
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;
using Antmicro.Renode.Peripherals.Timers;
using Antmicro.Renode.Time; // Direction, WorkMode

namespace Antmicro.Renode.Peripherals.Timers
{
    public class TimerCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public TimerCtrl(IMachine machine, long frequency = 30000000)
        {
            IRQ = new GPIO();
            timer = new LimitTimer(machine.ClockSource, (ulong)frequency, this, "timer",
                                   limit: CounterMax, direction: Direction.Ascending,
                                   enabled: false, workMode: WorkMode.Periodic,
                                   eventEnabled: true, autoUpdate: true);
            timer.LimitReached += OnLimitReached;
            Reset();
        }

        public void Reset()
        {
            control = 0;
            prescaler = 0;
            reload = 0;
            compare = 0;
            pending = 0;
            mask = 0;
            timer.Enabled = false;
            timer.Value = 0;
            UpdateTimer();
            IRQ.Set(false);
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:     return Header;
                case VersionOffset:    return 0x01000000; // 1.0.0
                case InfoOffset:       return Info;
                case IrqPendingOffset: return pending;
                case IrqMaskOffset:    return mask;
                case ControlOffset:    return control;
                case PrescalerOffset:  return prescaler;
                case CounterOffset:    return (uint)(timer.Value & CounterMask);
                case ReloadOffset:     return reload;
                case CompareOffset:    return compare;
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            switch(offset)
            {
                case IrqPendingOffset:  // write-1-to-clear
                    pending &= ~(value & 0x3u);
                    UpdateIrq();
                    return;
                case IrqMaskOffset:
                    mask = value & 0x3u;
                    UpdateIrq();
                    return;
                case ControlOffset:
                    control = value & 0x7u;
                    UpdateTimer();
                    return;
                case PrescalerOffset:
                    prescaler = value & 0xFFu;
                    UpdateTimer();
                    return;
                case CounterOffset:     // preload only takes effect while stopped (matches RTL)
                    if(!Enabled)
                    {
                        timer.Value = value & CounterMask;
                    }
                    return;
                case ReloadOffset:
                    reload = value & (uint)CounterMask;
                    return;
                case CompareOffset:
                    compare = value & (uint)CounterMask;
                    UpdateTimer();
                    return;
                case HeaderOffset:
                case VersionOffset:
                case InfoOffset:
                    this.Log(LogLevel.Warning, "Write to read-only register at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }
        }

        public long Size => 0x1000;
        public GPIO IRQ { get; }

        private bool Enabled => (control & 0x1u) != 0;
        private int Mode => (int)((control >> 1) & 0x3u); // 0=free-run 1=periodic 2=one-shot

        private void UpdateTimer()
        {
            // Period: compare (periodic/one-shot); full range for free-run or an unset compare.
            timer.Limit = (Mode == 0 || compare == 0) ? CounterMax : compare;
            timer.Divider = (ulong)(prescaler + 1);
            timer.Mode = (Mode == 2) ? WorkMode.OneShot : WorkMode.Periodic;
            timer.Enabled = Enabled;
        }

        private void OnLimitReached()
        {
            // Free-run -> overflow (bit 0); periodic/one-shot -> compare match (bit 1).
            pending |= (Mode == 0) ? 0x1u : 0x2u;
            if(Mode == 2) // one-shot: hardware clears the enable bit on completion
            {
                control &= ~0x1u;
                timer.Enabled = false;
            }
            UpdateIrq();
        }

        private void UpdateIrq()
        {
            IRQ.Set((pending & mask) != 0);
        }

        private uint control;
        private uint prescaler;
        private uint reload;
        private uint compare;
        private uint pending;
        private uint mask;
        private readonly LimitTimer timer;

        private const ulong CounterMask = 0xFFFF; // width = 16
        private const ulong CounterMax = 0xFFFF;
        // IpIdentification: id = Timer = 23, length = 8, api = 0.
        private const uint Header = (0u << 24) | (8u << 16) | 23u;
        // info: prescalerWidth=8, width=16, channelCount=1, count=1.
        private const uint Info = (8u << 24) | (16u << 16) | (1u << 8) | 1u;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long InfoOffset = 0x08;
        private const long IrqPendingOffset = 0x0C;
        private const long IrqMaskOffset = 0x10;
        private const long ControlOffset = 0x14;
        private const long PrescalerOffset = 0x18;
        private const long CounterOffset = 0x1C;
        private const long ReloadOffset = 0x20;
        private const long CompareOffset = 0x24;
    }
}
