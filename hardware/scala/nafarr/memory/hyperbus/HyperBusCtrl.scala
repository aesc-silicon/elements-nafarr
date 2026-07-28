// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.misc.InterruptCtrl
import spinal.lib.bus.misc.BusSlaveFactory

import nafarr.IpIdentification

import scala.math._
import BigDecimal._

object HyperBusCtrl {
  def apply(p: Parameter) = HyperBusCtrl(p)

  object Errors {
    val address = 0 // access outside any partition
    val permission = 1 // read of a non-readable partition
    val timeout = 2 // transaction exceeded the timeout window
    val unaligned = 3 // odd-address (address(0)) access - straddle not implemented
    val width = 4
  }

  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val resetTrigger = base + 0x08
    val resetPulse = base + 0x0c
    val resetHalt = base + 0x10
    val latency = base + 0x18
    val register = base + 0x28
    val fifoStatus = base + 0x2c
    val errorPending = base + 0x38
  }

  case class InitParameter(
      resetPulse: Int = 15,
      resetHalt: Int = 15,
      latencyCycles: Int = 7
  ) {
    require(latencyCycles > 2 && latencyCycles < 8, "Select latency cycles between 3 and 7.")
  }
  object InitParameter {
    def default = InitParameter()
  }

  case class Parameter(
      hyperbus: HyperBusParameter,
      phy: PhyParameter,
      frontend: FrontendParameter,
      init: InitParameter
  ) {}
  object Parameter {
    def default(
        partitions: List[(BigInt, Boolean)],
        clockDivider: Int = 8,
        latencyCycles: Int = 7
    ) = Parameter(
      hyperbus = HyperBusParameter(partitions),
      phy = PhyParameter(clockDivider = clockDivider),
      frontend = FrontendParameter(),
      init = InitParameter(latencyCycles = latencyCycles)
    )
  }
  case class HyperBusParameter(
      partitions: List[(BigInt, Boolean)],
      dataWidth: Int = 8,
      registerCmdFifoDepth: Int = 4,
      registerRspFifoDepth: Int = 4
  ) {
    val supportedDevices = partitions.length
    val memorySpace = (for (partition <- partitions) yield partition._1).sum
    require(supportedDevices < 9, "Only up to 8 devices supported for one HyperBus interface.")
  }
  case class PhyParameter(
      resetPulseMaxWidth: TimeNumber = 1 us,
      resetHaltMaxWidth: TimeNumber = 2 us,
      transactionMaxWidth: TimeNumber = 20 us,
      clockDivider: Int = 2,
      synchronizerDepth: Int = 2,
      dataWidth: Int = 8
  ) {
    require(resetPulseMaxWidth >= (200 ns), "Minimum reset pulse width is 200 ns")
    require(resetHaltMaxWidth >= (400 ns), "Minimum reset halt width is 400 ns")

    def resetPulseWidth: Int = {
      (resetPulseMaxWidth / ClockDomain.current.frequency.getValue.toTime)
        .setScale(0, RoundingMode.CEILING)
        .intValue()
    }
    def resetHaltWidth: Int = {
      (resetHaltMaxWidth / ClockDomain.current.frequency.getValue.toTime)
        .setScale(0, RoundingMode.CEILING)
        .intValue()
    }
    def resetWidth: Int = {
      resetPulseWidth + resetHaltWidth
    }
    def transactionWidth: Int = {
      (transactionMaxWidth / ClockDomain.current.frequency.getValue.toTime)
        .setScale(0, RoundingMode.CEILING)
        .intValue()
    }

    require(dataWidth == 8, "HyperBus only supports 8 data bits")
  }
  case class FrontendParameter(
      addrWidth: Int = 32,
      dataWidth: Int = 32,
      idLength: Int = 6,
      storageDepth: Int = 16
  ) {
    require(dataWidth % 8 == 0, "Data width has to be a multiple of 8")
  }

  case class Partition(p: Parameter) extends Bundle {
    val low = UInt(32 bits)
    val high = UInt(32 bits)
    val readable = Bool()
  }

  case class Config(p: Parameter) extends Bundle {
    val phy = slave(HyperBus.Phy.Config(p))
    val latencyCycles = in(UInt(log2Up(6) bits))
    val cmd = slave(Stream(Bits(32 bits)))
    val rsp = master(Stream(Bits(17 bits)))
  }

  case class Io(p: Parameter) extends Bundle {
    val phy = master(HyperBus.Phy.Interface(p))
    val frontend = master(Stream(HyperBus.FrontendInterface(p)))
    val controller = slave(Stream(HyperBus.ControllerInterface(p)))
    val config = Config(p)
    val error = out(Bits(Errors.width bits))
  }

  case class HyperBusCtrl(p: Parameter) extends Component {
    val io = Io(p)

    val errorPulse = Bits(Errors.width bits)
    errorPulse := 0
    io.error := errorPulse

    io.phy.config <> io.config.phy

    val frontend = StreamFifo(HyperBus.ControllerInterface(p), p.frontend.storageDepth)
    frontend.io.pop.ready := False

    val respFifo = StreamFifo(HyperBus.FrontendInterface(p), p.frontend.storageDepth)
    io.frontend << respFifo.io.pop
    respFifo.io.push.valid := False
    respFifo.io.push.payload.id := 0
    respFifo.io.push.payload.read := False
    respFifo.io.push.payload.data := 0
    respFifo.io.push.payload.last := False
    respFifo.io.push.payload.error := False

    val pendingBurstsInc = False
    val pendingBurstsDec = False
    val pendingBursts = Reg(UInt(log2Up(p.frontend.storageDepth + 1) bits)).init(0)
    pendingBursts := pendingBursts + pendingBurstsInc.asUInt - pendingBurstsDec.asUInt

    val funnel = new StateMachine {
      frontend.io.push.valid := False
      frontend.io.push.payload := io.controller.payload
      io.config.cmd.ready := False
      io.controller.ready := False

      val init: State = new State with EntryPoint {
        whenIsActive {
          when(io.config.cmd.valid) {
            frontend.io.push.payload.id := 0
            frontend.io.push.payload.read := io.config.cmd.payload(15)
            frontend.io.push.payload.unaligned := False
            frontend.io.push.payload.memory := False
            frontend.io.push.payload.addr :=
              (B(17 bits, default -> False) ## io.config.cmd.payload(14 downto 0)).asUInt
            frontend.io.push.payload.data :=
              B(16 bits, default -> False) ## io.config.cmd.payload(31 downto 16)
            frontend.io.push.payload.strobe := B"0011"
            frontend.io.push.payload.last := True

            frontend.io.push.valid := True
            when(frontend.io.push.fire) {
              io.config.cmd.ready := True
            }
          } elsewhen (io.controller.valid) {
            frontend.io.push.valid := True
            when(frontend.io.push.fire) {
              io.controller.ready := True
              when(!io.controller.payload.last) {
                goto(axiBurst)
              }
            }
          }
        }
      }
      val axiBurst: State = new State {
        whenIsActive {
          when(io.controller.valid) {
            frontend.io.push.valid := True
            when(frontend.io.push.fire) {
              io.controller.ready := True
              when(io.controller.payload.last) {
                goto(init)
              }
            }
          }
        }
      }
    }

    when(frontend.io.push.fire && frontend.io.push.payload.last) {
      pendingBurstsInc := True
    }

    io.phy.cmd.mode := HyperBus.Phy.CmdMode.START
    io.phy.cmd.args := 0
    io.phy.cmd.valid := False
    io.phy.rdata.ready := False

    io.config.rsp.valid := False
    io.config.rsp.payload := 0

    val partitions = Vec(Reg(Partition(p)), p.hyperbus.partitions.length)
    var lowAddress = BigInt(0)
    for ((partition, idx) <- p.hyperbus.partitions.zipWithIndex) {
      partitions(idx).low := lowAddress
      lowAddress = lowAddress + partition._1
      partitions(idx).high := lowAddress
      partitions(idx).readable := Bool(partition._2)
    }

    val fsm = new StateMachine {
      val ca = Reg(Bits(48 bits))
      val maxBurst = p.frontend.storageDepth
      val words = Vec(Reg(Bits(p.frontend.dataWidth bits)), maxBurst)
      val strobes = Vec(Reg(Bits(p.frontend.dataWidth / 8 bits)), maxBurst)
      val burstLen = Reg(UInt(log2Up(maxBurst + 1) bits))
      val baseAddr = Reg(UInt(p.frontend.addrWidth bits))
      val sendIdx = Reg(UInt(log2Up(maxBurst + 1) bits))
      val ackIdx = Reg(UInt(log2Up(maxBurst + 1) bits))
      val loadIdx = Reg(UInt(log2Up(maxBurst + 1) bits))
      val idLatch = Reg(cloneOf(frontend.io.pop.payload.id))
      val readLatch = Reg(Bool)
      val memoryLatch = Reg(Bool)

      // Build the 48-bit CA into `ca` (used next state); return the CS index and an
      // error flag combinationally for this cycle's START.
      def buildCa(addr: UInt, read: Bool, memory: Bool): (UInt, Bool) = {
        ca(47) := read
        ca(46) := !memory
        ca(45) := True
        val wordAddr = (addr >> 1).resize(widthOf(addr))
        ca(44 downto 16) := wordAddr.asBits(31 downto 3)
        ca(15 downto 3) := B(13 bits, default -> False)
        ca(2 downto 0) := wordAddr.asBits(2 downto 0)

        val addrLow = addr(log2Up(p.hyperbus.memorySpace) - 1 downto 0)
        val index = UInt(Math.max(log2Up(p.hyperbus.supportedDevices), 1) bits)
        val addressError = False
        val permissionError = False
        if (partitions.length == 1) {
          index := 0
          when(read && !partitions(0).readable) {
            permissionError := True
          }
        } else {
          val (inPartitions, sel) =
            partitions.sFindFirst(x => x.low <= addrLow && addrLow < x.high)
          index := sel.resized
          when(!inPartitions) {
            addressError := True
          }
          when(read && !partitions(sel).readable) {
            permissionError := True
          }
        }
        when(addressError) {
          errorPulse(Errors.address) := True
        }
        when(permissionError) {
          errorPulse(Errors.permission) := True
        }
        (index, addressError || permissionError)
      }

      // [1,0,3,2] byte lane order, self-inverse (same for write out and read in).
      def reorder(word: Bits): Bits =
        word(23 downto 16) ## word(31 downto 24) ## word(7 downto 0) ## word(15 downto 8)
      def reorderMask(s: Bits): Bits =
        ~(s(2) ## s(3) ## s(0) ## s(1))

      val init: State = new State with EntryPoint {
        whenIsActive {
          when(frontend.io.pop.valid && pendingBursts =/= 0) {
            loadIdx := 0
            sendIdx := 0
            idLatch := frontend.io.pop.payload.id
            readLatch := frontend.io.pop.payload.read
            memoryLatch := frontend.io.pop.payload.memory
            baseAddr := frontend.io.pop.payload.addr
            when(frontend.io.pop.payload.unaligned) {
              errorPulse(Errors.unaligned) := True
              goto(drainError)
            } otherwise {
              goto(load)
            }
          }
        }
      }

      val load: State = new State {
        whenIsActive {
          when(frontend.io.pop.valid) {
            words(loadIdx.resize(log2Up(maxBurst))) := frontend.io.pop.payload.data
            strobes(loadIdx.resize(log2Up(maxBurst))) := frontend.io.pop.payload.strobe
            frontend.io.pop.ready := True
            loadIdx := loadIdx + 1
            when(frontend.io.pop.payload.last) {
              burstLen := loadIdx + 1
              pendingBurstsDec := True
              goto(startCmd)
            }
          }
        }
      }

      val startCmd: State = new State {
        whenIsActive {
          val (index, hasError) = buildCa(baseAddr, readLatch, memoryLatch)
          when(hasError) {
            goto(errorResp)
          } otherwise {
            val s = HyperBus.Phy.CmdStart(p)
            s.index := index
            s.read := readLatch
            s.memory := memoryLatch
            s.latency := io.config.latencyCycles.resized
            when(!memoryLatch && !readLatch) {
              s.latency := 0
            }
            s.burstLen := (burstLen - 1).resized
            io.phy.cmd.mode := HyperBus.Phy.CmdMode.START
            io.phy.cmd.args := s.asBits.resized
            io.phy.cmd.valid := True
            when(io.phy.cmd.ready) {
              goto(caCmd)
            }
          }
        }
      }

      val caCmd: State = new State {
        whenIsActive {
          val c = HyperBus.Phy.CmdCa(p)
          c.ca := ca
          io.phy.cmd.mode := HyperBus.Phy.CmdMode.CA
          io.phy.cmd.args := c.asBits.resized
          io.phy.cmd.valid := True
          when(io.phy.cmd.ready) {
            sendIdx := 0
            ackIdx := 0
            when(readLatch) {
              goto(readDrain)
            } otherwise {
              goto(writeStream)
            }
          }
        }
      }

      // Non-posted write: emit the WRITE beats and, concurrently, drain the PHY's
      // per-word acks into the response path. Draining while emitting keeps the
      // PHY's ack FIFO from backing up. Done once every word is acked.
      val writeStream: State = new State {
        whenIsActive {
          when(sendIdx =/= burstLen) {
            val w = HyperBus.Phy.CmdWrite(p)
            w.data := reorder(words(sendIdx.resize(log2Up(maxBurst))))
            w.mask := reorderMask(strobes(sendIdx.resize(log2Up(maxBurst))))
            w.last := sendIdx === burstLen - 1
            io.phy.cmd.mode := HyperBus.Phy.CmdMode.WRITE
            io.phy.cmd.args := w.asBits.resized
            io.phy.cmd.valid := True
            when(io.phy.cmd.ready) {
              sendIdx := sendIdx + 1
            }
          }
          when(io.phy.rdata.valid) {
            when(memoryLatch) {
              respFifo.io.push.payload.id := idLatch
              respFifo.io.push.payload.read := False
              respFifo.io.push.payload.last := (ackIdx === burstLen - 1)
              respFifo.io.push.valid := True
              io.phy.rdata.ready := respFifo.io.push.ready
              when(respFifo.io.push.fire) {
                ackIdx := ackIdx + 1
                when(ackIdx === burstLen - 1) {
                  goto(init)
                }
              }
            } otherwise {
              io.config.rsp.payload := 0
              io.config.rsp.valid := True
              io.phy.rdata.ready := io.config.rsp.ready
              when(io.config.rsp.fire) {
                ackIdx := ackIdx + 1
                when(ackIdx === burstLen - 1) {
                  goto(init)
                }
              }
            }
          }
        }
      }

      val readDrain: State = new State {
        whenIsActive {
          when(io.phy.rdata.valid) {
            val word = reorder(io.phy.rdata.payload.data)
            when(io.phy.rdata.payload.error) {
              io.phy.rdata.ready := True
              errorPulse(Errors.timeout) := True
              goto(errorResp)
            } elsewhen (memoryLatch) {
              respFifo.io.push.payload.id := idLatch
              respFifo.io.push.payload.read := True
              respFifo.io.push.payload.data := word
              respFifo.io.push.payload.last := io.phy.rdata.payload.last
              respFifo.io.push.valid := True
              io.phy.rdata.ready := respFifo.io.push.ready
              when(respFifo.io.push.fire && io.phy.rdata.payload.last) {
                goto(init)
              }
            } otherwise {
              io.config.rsp.payload := False ## word(15 downto 0)
              io.config.rsp.valid := True
              io.phy.rdata.ready := io.config.rsp.ready
              when(io.config.rsp.fire && io.phy.rdata.payload.last) {
                goto(init)
              }
            }
          }
        }
      }

      val errorResp: State = new State {
        whenIsActive {
          when(memoryLatch) {
            respFifo.io.push.payload.id := idLatch
            respFifo.io.push.payload.read := readLatch
            respFifo.io.push.payload.error := True
            respFifo.io.push.payload.last := True
            respFifo.io.push.valid := True
            when(respFifo.io.push.fire) {
              goto(init)
            }
          } otherwise {
            io.config.rsp.payload := True ## B(16 bits, default -> False)
            io.config.rsp.valid := True
            when(io.config.rsp.fire) {
              goto(init)
            }
          }
        }
      }

      val drainError: State = new State {
        whenIsActive {
          when(frontend.io.pop.valid) {
            when(frontend.io.pop.payload.memory) {
              respFifo.io.push.payload.id := frontend.io.pop.payload.id
              respFifo.io.push.payload.read := frontend.io.pop.payload.read
              respFifo.io.push.payload.error := True
              respFifo.io.push.payload.last := frontend.io.pop.payload.last
              respFifo.io.push.valid := True
              frontend.io.pop.ready := respFifo.io.push.ready
            } otherwise {
              io.config.rsp.payload := True ## B(16 bits, default -> False)
              io.config.rsp.valid := True
              frontend.io.pop.ready := io.config.rsp.ready
            }
            when(frontend.io.pop.fire && frontend.io.pop.payload.last) {
              pendingBurstsDec := True
              goto(init)
            }
          }
        }
      }
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: Io,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Hyperbus, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)

    // RESET
    ctrl.config.phy.reset.trigger := False
    busCtrl.onWrite(regs.resetTrigger) {
      ctrl.config.phy.reset.trigger := True
    }
    val resetPulse = Reg(ctrl.config.phy.reset.pulse)
    val resetHalt = Reg(ctrl.config.phy.reset.halt)
    if (p.init != null && p.init.resetPulse != 0)
      resetPulse.init(U(p.init.resetPulse, widthOf(ctrl.config.phy.reset.pulse) bit))
    if (p.init != null && p.init.resetHalt != 0)
      resetHalt.init(U(p.init.resetHalt, widthOf(ctrl.config.phy.reset.halt) bit))

    busCtrl.readAndWrite(resetPulse, regs.resetPulse)
    busCtrl.readAndWrite(resetHalt, regs.resetHalt)
    ctrl.config.phy.reset.pulse := resetPulse
    ctrl.config.phy.reset.halt := resetHalt

    // TIMINGS
    val latencyCycles = Reg(ctrl.config.latencyCycles)
    if (p.init != null && p.init.latencyCycles != 0)
      latencyCycles.init(U(p.init.latencyCycles, widthOf(ctrl.config.latencyCycles) bit))
    busCtrl.readAndWrite(latencyCycles, regs.latency)
    ctrl.config.latencyCycles := latencyCycles

    // REGISTER access
    val cmdLogic = new Area {
      val streamUnbuffered =
        busCtrl.createAndDriveFlow(Bits(32 bits), address = regs.register).toStream
      val (stream, fifoAvailability) =
        streamUnbuffered.queueWithAvailability(p.hyperbus.registerCmdFifoDepth)
      ctrl.config.cmd << stream
      busCtrl.read(fifoAvailability, address = regs.fifoStatus, 16)
    }

    val rspLogic = new Area {
      val (stream, fifoOccupancy) =
        ctrl.config.rsp.queueWithOccupancy(p.hyperbus.registerRspFifoDepth)
      busCtrl.readStreamNonBlocking(
        stream,
        address = regs.register,
        validBitOffset = 31,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = regs.fifoStatus, 0)
    }

    // ERROR controller: an InterruptCtrl latches the address/permission/timeout
    // faults; the combined masked output drives io.error out to the ESM.
    val errorCtrl = InterruptCtrl(Errors.width)
    errorCtrl.io.inputs := ctrl.error
    errorCtrl.driveFrom(busCtrl, regs.errorPending.toInt)
    val error = errorCtrl.io.pendings.orR
  }
}
