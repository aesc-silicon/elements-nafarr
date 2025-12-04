// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.phy

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import nafarr.memory.hyperbus.{HyperBus, HyperBusCtrl}

object HyperBusGenericPhy {
  def apply(p: HyperBusCtrl.Parameter) = HyperBusGenericPhy.Phy(p)

  case class Phy(p: HyperBusCtrl.Parameter) extends Component {
    val io = new Bundle {
      val hyperbus = master(HyperBus.Io(p))
      val phy = slave(HyperBus.Phy.Interface(p))
    }

    val phyIsIdle = False

    val reset = new Area {
      val value = RegInit(True)
      val counter = Reg(UInt(log2Up(p.phy.resetWidth) bits)).init(0)
      val pulse = Reg(UInt(log2Up(p.phy.resetPulseWidth) bits))
      val halt = Reg(UInt(log2Up(p.phy.resetHaltWidth) bits))
      val run = RegInit(False)
      val pendingReset = RegInit(False)

      def isReset = run === True

      when(io.phy.config.reset.trigger) {
        pendingReset := True
      }
      when(phyIsIdle && pendingReset) {
        pendingReset := False
        counter := 0
        run := True
      }

      when(run) {
        counter := counter + 1
        when(counter === 0) {
          pulse := io.phy.config.reset.pulse
          halt := io.phy.config.reset.halt
          value := False
        }
        when(counter === pulse) {
          value := True
        }
        when(counter === halt) {
          run := False
        }
      }

      io.hyperbus.reset := value
    }

    val fsm = new StateMachine {
      val chipSelects = Reg(Bits(p.hyperbus.supportedDevices bits))
        .init(B(p.hyperbus.supportedDevices bits, default -> True))
      val bitCount = Reg(UInt(log2Up(6) bits))
      val additionalLatency = RegInit(False)
      val readTransaction = Reg(Bool)
      val setupClockCount = 5 * p.phy.clocksPerEdge - 1
      val latencyCycles = Reg(UInt(log2Up(6) + log2Up(p.phy.clockDivider) + 3 bits))
      val rwds = Reg(Bool)

      // TODO timeout

      val counter = new Area {
        val value = Reg(UInt(log2Up(p.phy.transactionWidth * p.phy.clockDivider + 50) bits)).init(0)
        val enableOutput = RegInit(False)
        value := value + 1

        def enableClock = enableOutput := True
        def disableClock = enableOutput := False
        def reset = value := 0
        def clock = !value(log2Up(p.phy.clocksPerEdge)) & enableOutput

        val states = new Area {
          def chipSelectSetup = value === p.phy.clocksPerEdge + 1
          def chipSelectTeardown = value === p.phy.clocksPerEdge

          def addrLast = value(log2Up(p.phy.clocksPerEdge) - 1 downto 0) === U(1)
          def readLast = value(log2Up(p.phy.clocksPerEdge) - 1 downto 0) === U(1)
          def writeLast = value(log2Up(p.phy.clocksPerEdge) - 1 downto 0) === U(1)

          def latencyCount1 = setupClockCount + (latencyCycles << log2Up(p.phy.clockDivider))
          def latencyCount2 = setupClockCount + (latencyCycles << (log2Up(p.phy.clockDivider) + 1))
          def accessReadLC1 = !additionalLatency && value > latencyCount1
          def accessReadLC2 = additionalLatency && value > latencyCount2
          def accessWriteLC1 =
            !additionalLatency && value > latencyCount1 + (p.phy.clocksPerEdge / 2)
          def accessWriteLC2 =
            additionalLatency && value > latencyCount2 + (p.phy.clocksPerEdge / 2)
        }
      }

      io.hyperbus.cs := chipSelects
      io.hyperbus.ck := counter.clock
      io.hyperbus.dq.write := 0
      io.hyperbus.dq.writeEnable := 0
      io.hyperbus.rwds.write := False
      io.hyperbus.rwds.writeEnable := False

      val synchronizer = new Area {
        val dq = BufferCC(io.hyperbus.dq.read, bufferDepth = 2)
        val rwds = BufferCC(io.hyperbus.rwds.read, bufferDepth = 2)
      }

      val cmdFifo = StreamFifo(HyperBus.Phy.Cmd(p), 12)
      cmdFifo.io.push << io.phy.cmd
      cmdFifo.io.pop.ready := False

      val rspFifo = StreamFifo(HyperBus.Phy.Rsp(p), 12)
      io.phy.rsp << rspFifo.io.pop
      rspFifo.io.push.valid := False
      rspFifo.io.push.payload.data := synchronizer.dq
      rspFifo.io.push.payload.error := False
      rspFifo.io.push.payload.last := False

      val init: State = new State with EntryPoint {
        whenIsActive {
          phyIsIdle := True
          when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isCs && !reset.isReset) {
            cmdFifo.io.pop.ready := True
            counter.reset
            if (p.hyperbus.supportedDevices == 1) {
              chipSelects(0) := False
            } else {
              chipSelects(cmdFifo.io.pop.argsCs.index) := False
            }
            readTransaction := cmdFifo.io.pop.argsCs.read
            latencyCycles := cmdFifo.io.pop.argsCs.latencyCycles.resized
            bitCount := 0
            rwds := True
            goto(chipSelectSetup)
          }
        }
      }
      val chipSelectSetup: State = new State {
        whenIsActive {
          when(counter.states.chipSelectSetup) {
            counter.enableClock
            goto(address)
          }
        }
      }
      val address: State = new State {
        whenIsActive {
          when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isAddr) {
            io.hyperbus.dq.writeEnable := default -> true
            io.hyperbus.dq.write := cmdFifo.io.pop.argsAddr.addr

            when(counter.states.addrLast) {
              bitCount := bitCount + 1
              cmdFifo.io.pop.ready := True
              when(bitCount === 2) {
                additionalLatency := synchronizer.rwds
              }
              when(bitCount === 5) {
                bitCount := 0
                when(readTransaction) {
                  goto(read)
                } otherwise {
                  goto(write)
                }
              }
            }
          }
        }
      }
      val read: State = new State {
        whenIsActive {
          when(counter.states.accessReadLC1 || counter.states.accessReadLC2) {
            when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isData) {
              when(synchronizer.rwds === rwds && counter.states.readLast) {
                // when(counter.states.readLast) {
                rspFifo.io.push.valid := True
                cmdFifo.io.pop.ready := True
                rwds := !rwds
                when(cmdFifo.io.pop.argsData.mask) {
                  rspFifo.io.push.payload.data := 0
                }
                when(cmdFifo.io.pop.argsData.last) {
                  rspFifo.io.push.payload.last := True
                  goto(end)
                }
              }
            }
          }
        }
      }
      val write: State = new State {
        whenIsActive {
          io.hyperbus.rwds.writeEnable := True
          when(counter.states.accessWriteLC1 || counter.states.accessWriteLC2) {
            when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isData) {
              io.hyperbus.rwds.write := cmdFifo.io.pop.argsData.mask
              io.hyperbus.dq.writeEnable := default -> true
              io.hyperbus.dq.write := cmdFifo.io.pop.argsData.data
              rspFifo.io.push.payload.data := 0
              when(counter.states.writeLast) {
                rspFifo.io.push.valid := True
                cmdFifo.io.pop.ready := True
                when(cmdFifo.io.pop.argsData.last) {
                  rspFifo.io.push.payload.last := True
                  goto(end)
                }
              }
            }
          }
        }
      }
      val end: State = new State {
        onEntry {
          chipSelects := (default -> true)
          counter.reset
          counter.disableClock
        }
        whenIsActive {
          when(counter.states.chipSelectTeardown) {
            goto(init)
          }
        }
      }
    }
  }
}
