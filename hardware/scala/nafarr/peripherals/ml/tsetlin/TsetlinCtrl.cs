// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0
//
// Renode model of the Nafarr Tsetlin machine (nafarr.peripherals.ml.tsetlin.TsetlinCtrl),
// mirroring TsetlinCtrl.scala. A row of the clause array matches when every included literal
// is 1; class sums count the matching even rows minus the matching odd rows of each class;
// the winner is the class with the highest sum, the lowest index on ties. With validColumn,
// literal 0 is searched with 0, so rows with include bit 0 set never match.
//
// The RTL is busy for one cycle after a start; this model completes a search within the
// start write, so busy always reads 0 and done is set immediately. Commits and starts are
// therefore never dropped, which the RTL does while busy.
//
// Register map (offsets from base; Regs base = IpIdentification.length = 8):
//   0x000 header    (RO)  IpIdentification
//   0x004 version   (RO)
//   0x008 array     (RO)  [31:16] literals, [15:0] rows
//   0x00C classes   (RO)  [31:16] clauses per class, [15:0] classes
//   0x010 options   (RO)  [8] valid column, [7:0] tiles
//   0x014 ctrl      (RW)  [1] irq enable, [0] start (write 1, reads 0)
//   0x018 status    (R/W1C) [1] done, [0] busy
//   0x01C row       (RW)  row address for the next commit
//   0x020 commit    (WO)  row buffer -> clause array row
//   0x024 result    (RO)  [31:16] winning sum (signed), [15:0] winning class
//   0x100 + 4w data     (WO) row buffer
//   0x200 + 4w literals (WO) search literals, the same buffer as data
//
using System;
using Antmicro.Renode.Core;
using Antmicro.Renode.Logging;
using Antmicro.Renode.Peripherals.Bus;

namespace Antmicro.Renode.Peripherals.Miscellaneous
{
    public class TsetlinCtrl : IDoubleWordPeripheral, IKnownSize
    {
        public TsetlinCtrl(IMachine machine, int rows = 64, int columns = 80, int tiles = 1,
                           int classes = 4, int clausesPerClass = 0, bool validColumn = true)
        {
            this.rows = rows;
            this.literals = columns * tiles;
            this.tiles = tiles;
            this.classes = classes;
            this.clausesPerClass = clausesPerClass > 0 ? clausesPerClass : rows / classes / 2 * 2;
            this.validColumn = validColumn;
            if(this.clausesPerClass < 2 || this.clausesPerClass % 2 != 0)
            {
                throw new ArgumentException("every class needs as many positive as negative clauses");
            }
            if(classes * this.clausesPerClass > rows)
            {
                throw new ArgumentException("classes do not fit into the rows");
            }
            if(literals > MaxLiterals || rows > MaxRows || classes > MaxClasses)
            {
                throw new ArgumentException("exceeds the register windows");
            }
            literalWords = (literals + 31) / 32;
            addressMask = (1u << Math.Max(4, Log2Up(rows))) - 1;

            include = new uint[rows, literalWords];
            buffer = new uint[literalWords];

            IRQ = new GPIO();
            Reset();
        }

        public void Reset()
        {
            Array.Clear(include, 0, include.Length);
            Array.Clear(buffer, 0, buffer.Length);
            irqEnable = false;
            done = false;
            row = 0;
            winner = 0;
            winnerSum = 0;
            UpdateInterrupt();
        }

        public uint ReadDoubleWord(long offset)
        {
            switch(offset)
            {
                case HeaderOffset:  return (uint)(((Api & 0xFF) << 24) | ((Length & 0xFF) << 16) | (Id & 0xFFFF));
                case VersionOffset: return 0x01000000; // 1.0.0
                case ArrayOffset:   return ((uint)literals << 16) | (uint)rows;
                case ClassesOffset: return ((uint)clausesPerClass << 16) | (uint)classes;
                case OptionsOffset: return (validColumn ? 1u << 8 : 0u) | ((uint)tiles & 0xFF);
                case CtrlOffset:    return irqEnable ? CtrlIrqEnable : 0u;
                case StatusOffset:  return done ? StatusDone : 0u;
                case RowOffset:     return row;
                case ResultOffset:  return ((uint)(winnerSum & 0xFFFF) << 16) | (winner & 0xFFFF);
                default:
                    this.Log(LogLevel.Warning, "Unhandled read at offset 0x{0:X}", offset);
                    return 0;
            }
        }

        public void WriteDoubleWord(long offset, uint value)
        {
            // commits and searches never overlap, so both windows map onto one buffer
            foreach(var window in new[] { DataOffset, LiteralsOffset })
            {
                if(offset >= window && offset < window + 4 * literalWords)
                {
                    var word = (offset - window) / 4;
                    buffer[word] = value & WordMask(word);
                    return;
                }
            }

            switch(offset)
            {
                case CtrlOffset:
                    irqEnable = (value & CtrlIrqEnable) != 0;
                    if((value & CtrlStart) != 0)
                    {
                        Search();
                    }
                    UpdateInterrupt();
                    return;
                case StatusOffset:
                    if((value & StatusDone) != 0)
                    {
                        done = false;
                    }
                    UpdateInterrupt();
                    return;
                case RowOffset:
                    row = value & addressMask;
                    return;
                case CommitOffset:
                    Commit();
                    return;
                default:
                    this.Log(LogLevel.Warning, "Unhandled or read-only write at offset 0x{0:X}", offset);
                    return;
            }
        }

        public long Size => 0x1000;

        public GPIO IRQ { get; }

        private void Commit()
        {
            if(row >= rows)
            {
                this.Log(LogLevel.Warning, "Commit to row {0} outside the {1} rows", row, rows);
                return;
            }
            for(var w = 0; w < literalWords; w++)
            {
                include[row, w] = buffer[w];
            }
        }

        private void Search()
        {
            var lit = (uint[])buffer.Clone();
            if(validColumn)
            {
                lit[0] &= ~1u;
            }

            winner = 0;
            winnerSum = int.MinValue;
            for(var c = 0; c < classes; c++)
            {
                var sum = 0;
                for(var j = 0; j < clausesPerClass; j++)
                {
                    if(Matches(c * clausesPerClass + j, lit))
                    {
                        sum += (j % 2 == 0) ? 1 : -1;
                    }
                }
                if(sum > winnerSum)
                {
                    winner = (uint)c;
                    winnerSum = sum;
                }
            }
            done = true;
        }

        private bool Matches(int r, uint[] lit)
        {
            for(var w = 0; w < literalWords; w++)
            {
                if((include[r, w] & ~lit[w]) != 0)
                {
                    return false;
                }
            }
            return true;
        }

        private uint WordMask(long word)
        {
            var bits = Math.Min(32, literals - 32 * (int)word);
            return bits == 32 ? 0xFFFFFFFFu : (1u << bits) - 1;
        }

        private void UpdateInterrupt()
        {
            IRQ.Set(done && irqEnable);
        }

        private static int Log2Up(int value)
        {
            var bits = 0;
            while((1 << bits) < value)
            {
                bits++;
            }
            return bits;
        }

        private readonly int rows;
        private readonly int literals;
        private readonly int tiles;
        private readonly int classes;
        private readonly int clausesPerClass;
        private readonly bool validColumn;
        private readonly int literalWords;
        private readonly uint addressMask;

        private readonly uint[,] include;
        private readonly uint[] buffer;
        private bool irqEnable;
        private bool done;
        private uint row;
        private uint winner;
        private int winnerSum;

        // IpIdentification header constants (TsetlinCtrl.scala: id = Tsetlin = 27, length = 8).
        private const int Api = 0;
        private const int Length = 8;
        private const int Id = 27;

        private const int MaxLiterals = 2048;
        private const int MaxRows = 2048;
        private const int MaxClasses = 256;

        private const uint CtrlStart = 1u << 0;
        private const uint CtrlIrqEnable = 1u << 1;
        private const uint StatusDone = 1u << 1;

        private const long HeaderOffset = 0x00;
        private const long VersionOffset = 0x04;
        private const long ArrayOffset = 0x08;
        private const long ClassesOffset = 0x0C;
        private const long OptionsOffset = 0x10;
        private const long CtrlOffset = 0x14;
        private const long StatusOffset = 0x18;
        private const long RowOffset = 0x1C;
        private const long CommitOffset = 0x20;
        private const long ResultOffset = 0x24;
        private const long DataOffset = 0x100;
        private const long LiteralsOffset = 0x200;
    }
}
