// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode functional model of the Nafarr UART controller (nafarr.peripherals.com.uart.UartCtrl).
//
// Register map (offsets from base), mirroring UartCtrl.scala (Regs base = IpIdentification.length = 8):
//   0x00  header          (RO)  api[31:24] | length[23:16] | id[15:0]
//   0x04  version         (RO)  major[31:24] | minor[23:16] | patch[15:0]
//   0x08  dataWidth       (RO)  0 | dataWidthMin[23:16] | dataWidthMax[15:8] | clockDividerWidth[7:0]
//   0x0C  samplingSize    (RO)  0 | preSampling[23:16] | sampling[15:8] | postSampling[7:0]
//   0x10  fifoDepth       (RO)  0[31:16] | txFifoDepth[15:8] | rxFifoDepth[7:0]
//   0x14  permissions     (RO)  canWriteFrameConfig[1] | canWriteClockDivider[0]
//   0x18  readWrite       (RW)  write: push TX byte; read: rxValid[16] | payload[8:0]
//   0x1C  fifoStatus      (RO)  rxOccupancy[31:24] | txVacancy[23:16]
//   0x20  clockDivider    (RW)
//   0x24  frameConfig     (RW)  stop[17:16] | parity[9:8] | dataLength[?:0]
//   0x28  transmitTrigger (RW)
//   0x2C  interruptPending(R: pending / W: write-1-to-clear),  0x30 interruptEnable (RW)
//   0x34  errorPending    (R/W1C),                              0x38 errorEnable (RW)
//
// Behavioral notes:
//   * TX is instantaneous: a write to readWrite emits the character to the analyzer/console
//     and the TX FIFO is always reported empty (full vacancy).
//   * RX bytes injected by the host (WriteChar / analyzer input) queue up and are returned
//     through readWrite with the valid bit set.
//   * interruptPending bit1 = RX data available, bit2 = TX idle (ready for next byte).
//
//
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;
using Antmicro.Renode.Peripherals.UART;

namespace Antmicro.Renode.Peripherals.UART
{
    public class UartCtrl : UARTBase, IDoubleWordPeripheral, IKnownSize
    {
        public UartCtrl(IMachine machine,
                          int txFifoDepth = 64, int rxFifoDepth = 64,
                          int dataWidthMin = 5, int dataWidthMax = 9,
                          int clockDividerWidth = 20,
                          int preSamplingSize = 1, int samplingSize = 5, int postSamplingSize = 2,
                          bool canWriteClockDivider = true, bool canWriteFrameConfig = true,
                          int versionMajor = 1, int versionMinor = 1, int versionPatch = 0)
            : base(machine)
        {
            this.txFifoDepth = txFifoDepth;
            this.rxFifoDepth = rxFifoDepth;
            this.dataWidthMin = dataWidthMin;
            this.dataWidthMax = dataWidthMax;
            this.clockDividerWidth = clockDividerWidth;
            this.preSamplingSize = preSamplingSize;
            this.samplingSize = samplingSize;
            this.postSamplingSize = postSamplingSize;
            this.canWriteClockDivider = canWriteClockDivider;
            this.canWriteFrameConfig = canWriteFrameConfig;
            this.version = ((versionMajor & 0xFF) << 24) | ((versionMinor & 0xFF) << 16) | (versionPatch & 0xFFFF);

            IRQ = new GPIO();
            Reset();
        }

        public override void Reset()
        {
            base.Reset();
            clockDivider = 0;
            frameConfig = 0;
            transmitTrigger = 0;
            interruptEnable = 0;
            interruptPending = 0;
            errorEnable = 0;
            errorPending = 0;
            UpdateInterrupt();
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:
                    return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset:
                    return (uint)version;
                case DataWidthOffset:
                    return (uint)(((dataWidthMin & 0xFF) << 16) | ((dataWidthMax & 0xFF) << 8) | (clockDividerWidth & 0xFF));
                case SamplingSizeOffset:
                    return (uint)(((preSamplingSize & 0xFF) << 16) | ((samplingSize & 0xFF) << 8) | (postSamplingSize & 0xFF));
                case FifoDepthOffset:
                    return (uint)(((txFifoDepth & 0xFF) << 8) | (rxFifoDepth & 0xFF));
                case PermissionsOffset:
                    return (uint)((canWriteFrameConfig ? 0x2 : 0x0) | (canWriteClockDivider ? 0x1 : 0x0));
                case ReadWriteOffset:
                    if(TryGetCharacter(out var character))
                    {
                        UpdateInterrupt();
                        return (1u << 16) | character;
                    }
                    return 0;
                case FifoStatusOffset:
                    // TX always empty -> full vacancy; RX occupancy = queued bytes.
                    return (uint)(((Count & 0xFF) << 24) | ((txFifoDepth & 0xFF) << 16));
                case ClockDividerOffset:
                    return (uint)clockDivider;
                case FrameConfigOffset:
                    return (uint)frameConfig;
                case TransmitTriggerOffset:
                    return (uint)transmitTrigger;
                case InterruptPendingOffset:
                    return (uint)EffectivePending();
                case InterruptEnableOffset:
                    return (uint)interruptEnable;
                case ErrorPendingOffset:
                    return (uint)errorPending;
                case ErrorEnableOffset:
                    return (uint)errorEnable;
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            switch(offset)
            {
                case ReadWriteOffset:
                    TransmitCharacter((byte)(value & 0xFF));
                    // TX is instantaneous, so it immediately goes idle: latch the txIdle
                    // rising-edge interrupt once (cleared by write-1-to-clear), not a level.
                    interruptPending |= (1 << 2);
                    UpdateInterrupt();
                    return;
                case ClockDividerOffset:
                    if(canWriteClockDivider) clockDivider = (int)value;
                    return;
                case FrameConfigOffset:
                    if(canWriteFrameConfig) frameConfig = (int)value;
                    return;
                case TransmitTriggerOffset:
                    transmitTrigger = (int)value;
                    return;
                case InterruptPendingOffset:
                    interruptPending &= ~(int)value; // write-1-to-clear
                    UpdateInterrupt();
                    return;
                case InterruptEnableOffset:
                    interruptEnable = (int)value;
                    UpdateInterrupt();
                    return;
                case ErrorPendingOffset:
                    errorPending &= ~(int)value;
                    UpdateInterrupt();
                    return;
                case ErrorEnableOffset:
                    errorEnable = (int)value;
                    UpdateInterrupt();
                    return;
                case HeaderOffset:
                case VersionOffset:
                case DataWidthOffset:
                case SamplingSizeOffset:
                case FifoDepthOffset:
                case PermissionsOffset:
                case FifoStatusOffset:
                    this.Log(LogLevel.Warning, "Write to read-only register at offset 0x{0:X}", offset);
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled write at offset 0x{0:X}", offset);
                    return;
            }
        }

        public long Size => 0x100;
        public GPIO IRQ { get; }

        public override Bits StopBits => Bits.One;
        public override Parity ParityBit => Parity.None;
        public override uint BaudRate => 115200;

        // Called by UARTBase when a host character is queued for RX.
        protected override void CharWritten()
        {
            UpdateInterrupt();
        }

        protected override void QueueEmptied()
        {
            UpdateInterrupt();
        }

        // Latched edge interrupts (TX-idle bit2, trigger bit0) plus the RX-available level (bit1).
        private int EffectivePending()
        {
            var pending = interruptPending;
            if(Count > 0) pending |= (1 << 1); // RX available is a level condition
            return pending;
        }

        private void UpdateInterrupt()
        {
            // The interrupt line is the enabled subset of the pending bits; pending bits are not
            // re-masked here, matching the InterruptCtrl behaviour (mask gates the line, W1C clears).
            var line = (EffectivePending() & interruptEnable) != 0;
            IRQ.Set(line || ((errorPending & errorEnable) != 0));
        }

        private int clockDivider;
        private int frameConfig;
        private int transmitTrigger;
        private int interruptEnable;
        private int interruptPending;
        private int errorEnable;
        private int errorPending;

        private readonly int txFifoDepth;
        private readonly int rxFifoDepth;
        private readonly int dataWidthMin;
        private readonly int dataWidthMax;
        private readonly int clockDividerWidth;
        private readonly int preSamplingSize;
        private readonly int samplingSize;
        private readonly int postSamplingSize;
        private readonly bool canWriteClockDivider;
        private readonly bool canWriteFrameConfig;
        private readonly int version;

        // IpIdentification header constants (UartCtrl.scala: id = Uart = 3, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 3;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long DataWidthOffset = 0x08;
        private const long SamplingSizeOffset = 0x0C;
        private const long FifoDepthOffset = 0x10;
        private const long PermissionsOffset = 0x14;
        private const long ReadWriteOffset = 0x18;
        private const long FifoStatusOffset = 0x1C;
        private const long ClockDividerOffset = 0x20;
        private const long FrameConfigOffset = 0x24;
        private const long TransmitTriggerOffset = 0x28;
        private const long InterruptPendingOffset = 0x2C;
        private const long InterruptEnableOffset = 0x30;
        private const long ErrorPendingOffset = 0x34;
        private const long ErrorEnableOffset = 0x38;
    }
}
