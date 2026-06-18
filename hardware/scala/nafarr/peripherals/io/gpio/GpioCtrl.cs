// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode functional model of the Nafarr GPIO controller (nafarr.peripherals.io.gpio.GpioCtrl).
//
// Register map (offsets from base), mirroring GpioCtrl.scala + IpIdentification.scala:
//   0x00  header   (RO)  api[31:24] | length[23:16] | id[15:0]
//   0x04  version  (RO)  major[31:24] | minor[23:16] | patch[15:0]
//   0x08  info     (RO)  banks[31:16] | width[15:0]
//   then, per bank b, at (0x0C + b*0x2C):
//     +0x00 input        (RO)  pin read value
//     +0x04 output       (RW)  driven value
//     +0x08 direction    (RW)  1 = output, 0 = input
//     +0x0C highPending  (R: pending / W: write-1-to-clear)
//     +0x10 highEnable   (RW)
//     +0x14 lowPending   / +0x18 lowEnable
//     +0x1C risePending  / +0x20 riseEnable
//     +0x24 fallPending  / +0x28 fallEnable
//
// Behavioral notes:
//   * Pins configured as output loop back into the input register (read returns the
//     driven value), which is convenient for bare-metal self-tests.
//   * Input pins read external stimulus delivered via OnGPIO(...).
//   * Output pins drive Connections[pin] so you can wire e.g. `gpio0 -> led@0` in the .repl.
//
//
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using Antmicro.Renode.Core;
using Antmicro.Renode.Core.Structure;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.GPIOPort
{
    public class GpioCtrl : BaseGPIOPort, IDoubleWordPeripheral, IKnownSize
    {
        public GpioCtrl(IMachine machine, int numberOfPins = 12, int id = 0,
                          int versionMajor = 1, int versionMinor = 0, int versionPatch = 0)
            : base(machine, numberOfPins)
        {
            this.numberOfPins = numberOfPins;
            this.banks = (numberOfPins + 31) / 32;
            this.id = id;
            this.version = ((versionMajor & 0xFF) << 24) | ((versionMinor & 0xFF) << 16) | (versionPatch & 0xFFFF);

            IRQ = new GPIO();

            outputValue = new bool[numberOfPins];
            direction = new bool[numberOfPins];
            externalInput = new bool[numberOfPins];
            lastSampled = new bool[numberOfPins];
            driven = new bool[numberOfPins];

            highEnable = new bool[numberOfPins];
            lowEnable = new bool[numberOfPins];
            riseEnable = new bool[numberOfPins];
            fallEnable = new bool[numberOfPins];
            highPending = new bool[numberOfPins];
            lowPending = new bool[numberOfPins];
            risePending = new bool[numberOfPins];
            fallPending = new bool[numberOfPins];

            Reset();
        }

        public override void Reset()
        {
            base.Reset();
            for(var i = 0; i < numberOfPins; i++)
            {
                outputValue[i] = false;
                direction[i] = false;
                externalInput[i] = false;
                lastSampled[i] = false;
                driven[i] = false;
                highEnable[i] = lowEnable[i] = riseEnable[i] = fallEnable[i] = false;
                highPending[i] = lowPending[i] = risePending[i] = fallPending[i] = false;
            }
            UpdatePins();
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:
                    return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (id & 0xFFFF));
                case VersionOffset:
                    return (uint)version;
                case InfoOffset:
                    return (uint)(((banks & 0xFFFF) << 16) | (numberOfPins & 0xFFFF));
            }

            if(!TryResolveBankRegister(offset, out var bank, out var reg))
            {
                this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                return 0;
            }

            switch(reg)
            {
                case BankInput:       return Pack(bank, PinReadValue);
                case BankOutput:      return Pack(bank, i => outputValue[i]);
                case BankDirection:   return Pack(bank, i => direction[i]);
                case BankHighPending: return Pack(bank, i => highPending[i]);
                case BankHighEnable:  return Pack(bank, i => highEnable[i]);
                case BankLowPending:  return Pack(bank, i => lowPending[i]);
                case BankLowEnable:   return Pack(bank, i => lowEnable[i]);
                case BankRisePending: return Pack(bank, i => risePending[i]);
                case BankRiseEnable:  return Pack(bank, i => riseEnable[i]);
                case BankFallPending: return Pack(bank, i => fallPending[i]);
                case BankFallEnable:  return Pack(bank, i => fallEnable[i]);
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            if(offset == HeaderOffset || offset == VersionOffset || offset == InfoOffset)
            {
                this.Log(LogLevel.Warning, "Write to read-only register at offset 0x{0:X}", offset);
                return;
            }

            if(!TryResolveBankRegister(offset, out var bank, out var reg))
            {
                this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                return;
            }

            switch(reg)
            {
                case BankOutput:      Unpack(bank, value, (i, b) => outputValue[i] = b); break;
                case BankDirection:   Unpack(bank, value, (i, b) => direction[i] = b); break;
                case BankHighEnable:  Unpack(bank, value, (i, b) => highEnable[i] = b); break;
                case BankLowEnable:   Unpack(bank, value, (i, b) => lowEnable[i] = b); break;
                case BankRiseEnable:  Unpack(bank, value, (i, b) => riseEnable[i] = b); break;
                case BankFallEnable:  Unpack(bank, value, (i, b) => fallEnable[i] = b); break;
                // pending registers are write-1-to-clear
                case BankHighPending: Unpack(bank, value, (i, b) => { if(b) highPending[i] = false; }); break;
                case BankLowPending:  Unpack(bank, value, (i, b) => { if(b) lowPending[i] = false; }); break;
                case BankRisePending: Unpack(bank, value, (i, b) => { if(b) risePending[i] = false; }); break;
                case BankFallPending: Unpack(bank, value, (i, b) => { if(b) fallPending[i] = false; }); break;
                case BankInput:
                    this.Log(LogLevel.Warning, "Write to read-only input register at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }

            UpdatePins();
        }

        // External stimulus: drive an input pin from the .repl/monitor or another peripheral.
        public override void OnGPIO(int number, bool value)
        {
            if(number < 0 || number >= numberOfPins)
            {
                this.Log(LogLevel.Warning, "Ignoring GPIO stimulus on out-of-range pin {0}", number);
                return;
            }
            externalInput[number] = value;
            UpdatePins();
        }

        public long Size => 0x100;
        public GPIO IRQ { get; }

        private bool PinValue(int pin) => direction[pin] ? outputValue[pin] : externalInput[pin];
        private bool PinReadValue(int pin) => PinValue(pin); // outputs loop back into input register

        private void UpdatePins()
        {
            for(var i = 0; i < numberOfPins; i++)
            {
                var current = PinValue(i);
                var previous = lastSampled[i];

                // Latch pending bits while their (enabled) condition holds.
                if(highEnable[i] && current) highPending[i] = true;
                if(lowEnable[i] && !current) lowPending[i] = true;
                if(riseEnable[i] && current && !previous) risePending[i] = true;
                if(fallEnable[i] && !current && previous) fallPending[i] = true;

                lastSampled[i] = current;

                // Drive output connections; inputs are released (low). Log driven-value changes
                // so the GPIO output is observable in the monitor/log (GUI LED analyzer optional).
                var driving = direction[i] && outputValue[i];
                if(driving != driven[i])
                {
                    driven[i] = driving;
                    this.Log(LogLevel.Info, "GPIO output pin {0} = {1}", i, driving ? 1 : 0);
                }
                Connections[i].Set(driving);
            }

            var anyPending = highPending.Any(b => b) || lowPending.Any(b => b)
                          || risePending.Any(b => b) || fallPending.Any(b => b);
            IRQ.Set(anyPending);
        }

        private bool TryResolveBankRegister(long offset, out int bank, out long reg)
        {
            bank = 0;
            reg = 0;
            if(offset < FirstBankOffset)
            {
                return false;
            }
            var rel = offset - FirstBankOffset;
            bank = (int)(rel / BankStride);
            reg = rel % BankStride;
            return bank < banks && (reg % 4 == 0) && reg <= BankFallEnable;
        }

        private uint Pack(int bank, System.Func<int, bool> selector)
        {
            uint result = 0;
            var pinsInBank = PinsInBank(bank);
            for(var i = 0; i < pinsInBank; i++)
            {
                if(selector(bank * 32 + i))
                {
                    result |= 1u << i;
                }
            }
            return result;
        }

        private void Unpack(int bank, uint value, System.Action<int, bool> apply)
        {
            var pinsInBank = PinsInBank(bank);
            for(var i = 0; i < pinsInBank; i++)
            {
                apply(bank * 32 + i, (value & (1u << i)) != 0);
            }
        }

        private int PinsInBank(int bank)
        {
            var remaining = numberOfPins - bank * 32;
            return remaining >= 32 ? 32 : remaining;
        }

        private readonly int numberOfPins;
        private readonly int banks;
        private readonly int id;
        private readonly int version;

        private readonly bool[] outputValue;
        private readonly bool[] direction;
        private readonly bool[] externalInput;
        private readonly bool[] lastSampled;
        private readonly bool[] driven;

        private readonly bool[] highEnable;
        private readonly bool[] lowEnable;
        private readonly bool[] riseEnable;
        private readonly bool[] fallEnable;
        private readonly bool[] highPending;
        private readonly bool[] lowPending;
        private readonly bool[] risePending;
        private readonly bool[] fallPending;

        // IpIdentification header constants (IpIdentification.scala: length = 8, api = 0).
        private const int Api = 0;
        private const int Length = 8;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long InfoOffset = 0x08;
        private const long FirstBankOffset = 0x0C;
        private const long BankStride = 0x2C;

        private const long BankInput = 0x00;
        private const long BankOutput = 0x04;
        private const long BankDirection = 0x08;
        private const long BankHighPending = 0x0C;
        private const long BankHighEnable = 0x10;
        private const long BankLowPending = 0x14;
        private const long BankLowEnable = 0x18;
        private const long BankRisePending = 0x1C;
        private const long BankRiseEnable = 0x20;
        private const long BankFallPending = 0x24;
        private const long BankFallEnable = 0x28;
    }
}
