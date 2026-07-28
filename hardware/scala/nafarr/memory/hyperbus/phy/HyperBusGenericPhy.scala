// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.phy

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import nafarr.memory.hyperbus.{HyperBus, HyperBusCtrl}

// Serial generic PHY: a pure slicer. Slices the controller-built CA and write
// words into single bytes and clocks them out one byte per HyperBus edge, and
// reassembles read bytes into words. The byte timing is unchanged from before.
object HyperBusGenericPhy {
  def apply(p: HyperBusCtrl.Parameter) = HyperBusGenericPhy.Phy(p)

  case class Phy(p: HyperBusCtrl.Parameter) extends Component {
    val io = new Bundle {
      val hyperbus = master(HyperBus.Io(p))
      val phy = slave(HyperBus.Phy.Interface(p))
    }

    val dataBytes = p.frontend.dataWidth / 8

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
      val clocksPerEdge = p.phy.clockDivider / 2
      val setupClockCount = 5 * clocksPerEdge - 1

      val chipSelects = Reg(Bits(p.hyperbus.supportedDevices bits))
        .init(B(p.hyperbus.supportedDevices bits, default -> True))
      val readTransaction = Reg(Bool)
      val additionalLatency = RegInit(False)
      val latencyCycles = Reg(UInt(log2Up(6) + log2Up(p.phy.clockDivider) + 3 bits))
      val burstLen = Reg(UInt(log2Up(p.frontend.storageDepth + 1) bits))

      val counter = new Area {
        val value = Reg(UInt(log2Up(p.phy.transactionWidth * p.phy.clockDivider + 50) bits)).init(0)
        val enableOutput = RegInit(False)
        value := value + 1

        def enableClock = enableOutput := True
        def disableClock = enableOutput := False
        def reset = value := 0
        def clock = !value(log2Up(clocksPerEdge)) & enableOutput

        val states = new Area {
          def chipSelectSetup = value === clocksPerEdge + 1
          def chipSelectTeardown = value === clocksPerEdge
          def edgeLast = value(log2Up(clocksPerEdge) - 1 downto 0) === U(1)
          // Fixed vs variable (2x) latency: the device raises RWDS during CA to
          // request the doubled window.
          def latencyCount1 = setupClockCount + (latencyCycles << log2Up(p.phy.clockDivider))
          def latencyCount2 = setupClockCount + (latencyCycles << (log2Up(p.phy.clockDivider) + 1))
          def latencyCount = (additionalLatency ? latencyCount2 | latencyCount1)
          def accessRead = value > latencyCount
          def accessWrite = value > latencyCount + (clocksPerEdge / 2)
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
      val rwdsEdge = synchronizer.rwds =/= RegNext(synchronizer.rwds)

      val cmdFifo = StreamFifo(HyperBus.Phy.Cmd(p), 12)
      cmdFifo.io.push << io.phy.cmd
      cmdFifo.io.pop.ready := False

      val rdataFifo = StreamFifo(HyperBus.Phy.Rdata(p), p.frontend.storageDepth)
      io.phy.rdata << rdataFifo.io.pop
      rdataFifo.io.push.valid := False
      rdataFifo.io.push.payload.data := 0
      rdataFifo.io.push.payload.last := False
      rdataFifo.io.push.payload.error := False
      rdataFifo.io.push.payload.aborted := False

      val byteIndex = Reg(UInt(3 bits))
      val recvData = Reg(Bits(p.frontend.dataWidth bits))
      val wordCount = Reg(UInt(log2Up(p.frontend.storageDepth + 1) bits))

      val init: State = new State with EntryPoint {
        whenIsActive {
          phyIsIdle := True
          counter.disableClock
          when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isStart && !reset.isReset) {
            cmdFifo.io.pop.ready := True
            val s = cmdFifo.io.pop.argsStart
            if (p.hyperbus.supportedDevices == 1) {
              chipSelects(0) := False
            } else {
              chipSelects(s.index) := False
            }
            readTransaction := s.read
            latencyCycles := s.latency.resized
            burstLen := s.burstLen
            counter.reset
            byteIndex := 0
            wordCount := 0
            recvData := 0
            goto(chipSelectSetup)
          }
        }
      }

      val chipSelectSetup: State = new State {
        whenIsActive {
          when(counter.states.chipSelectSetup) {
            counter.enableClock
            goto(ca)
          }
        }
      }

      val ca: State = new State {
        whenIsActive {
          when(cmdFifo.io.pop.valid && cmdFifo.io.pop.isCa) {
            io.hyperbus.dq.writeEnable := default -> true
            io.hyperbus.dq.write := cmdFifo.io.pop.argsCa.ca.subdivideIn(8 bits)(5 - byteIndex)
            when(counter.states.edgeLast) {
              byteIndex := byteIndex + 1
              when(byteIndex === 2) {
                additionalLatency := synchronizer.rwds
              }
              when(byteIndex === 5) {
                cmdFifo.io.pop.ready := True
                byteIndex := 0
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
          when(counter.states.accessRead && rwdsEdge) {
            val assembled = Bits(p.frontend.dataWidth bits)
            assembled := recvData
            assembled.subdivideIn(8 bits)(byteIndex.resize(log2Up(dataBytes))) := synchronizer.dq
            recvData := assembled
            when(byteIndex === dataBytes - 1) {
              byteIndex := 0
              rdataFifo.io.push.valid := True
              rdataFifo.io.push.payload.data := assembled
              when(wordCount === burstLen) {
                rdataFifo.io.push.payload.last := True
                goto(end)
              }
              wordCount := wordCount + 1
            } otherwise {
              byteIndex := byteIndex + 1
            }
          }
        }
      }

      val write: State = new State {
        whenIsActive {
          io.hyperbus.rwds.writeEnable := True
          when(counter.states.accessWrite && cmdFifo.io.pop.valid && cmdFifo.io.pop.isWrite) {
            val w = cmdFifo.io.pop.argsWrite
            io.hyperbus.dq.writeEnable := default -> true
            io.hyperbus.dq.write := w.data.subdivideIn(8 bits)(byteIndex.resize(log2Up(dataBytes)))
            io.hyperbus.rwds.write := w.mask(byteIndex.resize(log2Up(dataBytes)))
            when(counter.states.edgeLast) {
              when(byteIndex === dataBytes - 1) {
                byteIndex := 0
                cmdFifo.io.pop.ready := True
                // Ack each committed word so the write is non-posted.
                rdataFifo.io.push.valid := True
                rdataFifo.io.push.payload.data := 0
                rdataFifo.io.push.payload.last := w.last
                when(w.last) {
                  goto(end)
                }
              } otherwise {
                byteIndex := byteIndex + 1
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
