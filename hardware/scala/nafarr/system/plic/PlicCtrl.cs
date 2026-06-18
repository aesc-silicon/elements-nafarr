// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr PLIC (nafarr.system.plic, SpinalHDL PlicMapping.sifive +
// PlicGatewayActiveHigh). Models the gateway latch faithfully so the Nafarr driver works:
//   * gateway: while !waitCompletion, ip follows source and latches waitCompletion;
//   * READ  of the claim/complete register => doClaim()      (ip := false),
//   * WRITE of the claim/complete register => doCompletion() (waitCompletion := false).
// Unlike a strict SiFive PLIC, a WRITE (completion) alone re-arms the gateway and re-evaluates
// the source, so the driver's "write the known source id to complete" (without a prior read-claim)
// is valid. Priority is hardwired to 1 and threshold to 0 (Plic.scala), so any enabled pending
// source is deliverable.
//
// Single context (context 0 -> the M-mode hart). Register map (offsets from base):
//   0x000000 + id*4    priority   (RO, reads 1 for valid sources; writes ignored)
//   0x001000 + w*4     pending    (RO, bit = gateway ip)
//   0x002000 + w*4     enable     (RW, context 0)
//   0x200000           threshold  (RW, context 0)
//   0x200004           claim/complete (READ = claim, WRITE = complete)
//
//
using System.Collections.Generic;
using System.Collections.ObjectModel;
using Antmicro.Renode.Core;
using Antmicro.Renode.Core.Structure;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.IRQControllers
{
    public class PlicCtrl : IDoubleWordPeripheral, IKnownSize, IGPIOReceiver, INumberedGPIOOutput
    {
        public PlicCtrl(IMachine machine, int numberOfSources = 31)
        {
            this.numberOfSources = numberOfSources;
            words = (numberOfSources + 1 + 31) / 32; // sources 0..numberOfSources, source 0 reserved

            var connections = new Dictionary<int, IGPIO> { { 0, new GPIO() } };
            Connections = new ReadOnlyDictionary<int, IGPIO>(connections);

            source = new bool[numberOfSources + 1];
            ip = new bool[numberOfSources + 1];
            waitCompletion = new bool[numberOfSources + 1];
            enabled = new bool[numberOfSources + 1];

            Reset();
        }

        public void Reset()
        {
            for(var i = 0; i <= numberOfSources; i++)
            {
                source[i] = ip[i] = waitCompletion[i] = enabled[i] = false;
            }
            threshold = 0;
            UpdateLine();
        }

        // Interrupt sources from peripherals (e.g. gpio0.IRQ -> plic@3).
        public void OnGPIO(int number, bool value)
        {
            if(number <= 0 || number > numberOfSources)
            {
                this.Log(LogLevel.Warning, "Ignoring interrupt on out-of-range source {0}", number);
                return;
            }
            source[number] = value;
            GatewayEvaluate(number);
            UpdateLine();
        }

        public uint ReadDoubleWord(long offset)
        {
            if(offset >= PriorityBase && offset < PendingBase)
            {
                var id = (int)(offset / 4);
                return (id >= 1 && id <= numberOfSources) ? 1u : 0u; // hardwired priority 1
            }
            if(offset >= PendingBase && offset < PendingBase + words * 4)
            {
                return PackWord((int)((offset - PendingBase) / 4), ip);
            }
            if(offset >= EnableBase && offset < EnableBase + words * 4)
            {
                return PackWord((int)((offset - EnableBase) / 4), enabled);
            }
            if(offset == ThresholdOffset)
            {
                return (uint)threshold;
            }
            if(offset == ClaimOffset)
            {
                return (uint)Claim();
            }
            this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
            return 0;
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            if(offset >= EnableBase && offset < EnableBase + words * 4)
            {
                UnpackWord((int)((offset - EnableBase) / 4), value, enabled);
                UpdateLine();
                return;
            }
            if(offset == ThresholdOffset)
            {
                threshold = (int)value;
                UpdateLine();
                return;
            }
            if(offset == ClaimOffset)
            {
                Complete((int)value);
                return;
            }
            if(offset >= PriorityBase && offset < PendingBase)
            {
                return; // priority hardwired; writes ignored
            }
            if(offset >= PendingBase && offset < PendingBase + words * 4)
            {
                this.Log(LogLevel.Warning, "Write to read-only pending register at offset 0x{0:X}", offset);
                return;
            }
            this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
        }

        public long Size => 0x4000000;
        public IReadOnlyDictionary<int, IGPIO> Connections { get; }

        // gateway: ip follows source and latches until a completion clears waitCompletion.
        private void GatewayEvaluate(int id)
        {
            if(!waitCompletion[id])
            {
                ip[id] = source[id];
                waitCompletion[id] = source[id];
            }
        }

        private int Claim()
        {
            // Highest-priority pending+enabled source; priorities are equal, so lowest id wins.
            for(var id = 1; id <= numberOfSources; id++)
            {
                if(enabled[id] && ip[id])
                {
                    ip[id] = false; // doClaim()
                    UpdateLine();
                    return id;
                }
            }
            return 0;
        }

        private void Complete(int id)
        {
            if(id <= 0 || id > numberOfSources)
            {
                return;
            }
            waitCompletion[id] = false; // doCompletion()
            GatewayEvaluate(id);        // re-arm: ip := source
            UpdateLine();
        }

        private void UpdateLine()
        {
            var pending = false;
            for(var id = 1; id <= numberOfSources; id++)
            {
                // priority (1) must exceed threshold to be deliverable.
                if(enabled[id] && ip[id] && 1 > threshold)
                {
                    pending = true;
                    break;
                }
            }
            Connections[0].Set(pending);
        }

        private uint PackWord(int word, bool[] bits)
        {
            uint result = 0;
            for(var b = 0; b < 32; b++)
            {
                var id = word * 32 + b;
                if(id >= 1 && id <= numberOfSources && bits[id])
                {
                    result |= 1u << b;
                }
            }
            return result;
        }

        private void UnpackWord(int word, uint value, bool[] bits)
        {
            for(var b = 0; b < 32; b++)
            {
                var id = word * 32 + b;
                if(id >= 1 && id <= numberOfSources)
                {
                    bits[id] = (value & (1u << b)) != 0;
                }
            }
        }

        private readonly int numberOfSources;
        private readonly int words;
        private readonly bool[] source;
        private readonly bool[] ip;
        private readonly bool[] waitCompletion;
        private readonly bool[] enabled;
        private int threshold;

        private const long PriorityBase = 0x000000;
        private const long PendingBase = 0x001000;
        private const long EnableBase = 0x002000;
        private const long ThresholdOffset = 0x200000;
        private const long ClaimOffset = 0x200004;
    }
}
