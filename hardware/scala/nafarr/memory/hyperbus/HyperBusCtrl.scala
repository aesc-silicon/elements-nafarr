// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.misc.BusSlaveFactory

import scala.math._
import BigDecimal._

object HyperBusCtrl {
  def apply(p: Parameter) = HyperBusCtrl(p)

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
    def default(partitions: List[(BigInt, Boolean)]) = Parameter(
      hyperbus = HyperBusParameter(partitions),
      phy = PhyParameter(),
      frontend = FrontendParameter(),
      init = InitParameter()
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
      transactionMaxWidth: TimeNumber = 4 us,
      clockDivider: Int = 8,
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

    val clocksPerEdge = clockDivider / 2
    require(dataWidth == 8, "HyperBus only supports 8 data bits")
  }
  case class FrontendParameter(
      addrWidth: Int = 32,
      dataWidth: Int = 32,
      idLength: Int = 6,
      storageDepth: Int = 12
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
  }

  case class HyperBusCtrl(p: Parameter) extends Component {
    val io = Io(p)

    io.phy.config <> io.config.phy

    val frontend = StreamFifo(HyperBus.ControllerInterface(p), 12)
    frontend.io.pop.ready := False

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

    io.phy.cmd.mode := HyperBus.Phy.CmdMode.CS
    io.phy.cmd.args := 0
    io.phy.cmd.valid := False
    io.phy.rsp.ready := False

    io.frontend.valid := False
    io.frontend.payload.id := frontend.io.pop.payload.id
    io.frontend.payload.read := frontend.io.pop.payload.read
    io.frontend.payload.data := 0
    io.frontend.payload.last := frontend.io.pop.payload.last
    io.frontend.payload.error := False

    val partitions = Vec(Reg(Partition(p)), p.hyperbus.partitions.length)
    var lowAddress = BigInt(0)
    for ((partition, idx) <- p.hyperbus.partitions.zipWithIndex) {
      partitions(idx).low := lowAddress
      lowAddress = lowAddress + partition._1
      partitions(idx).high := lowAddress
      partitions(idx).readable := Bool(partition._2)
    }

    val fsm = new StateMachine {
      val counter = Reg(UInt(3 bits))
      val ca = Reg(Bits(48 bits))
      val data = Reg(Bits(32 bits))

      io.config.rsp.valid := False
      io.config.rsp.payload := 0

      val init: State = new State with EntryPoint {
        whenIsActive {
          when(frontend.io.pop.valid) {
            counter := 0
            data := 0

            ca(47) := frontend.io.pop.payload.read
            ca(46) := !frontend.io.pop.payload.memory
            ca(45) := True // always linear burst
            ca(44 downto 16) := frontend.io.pop.payload.addr.asBits(31 downto 3)
            ca(15 downto 3) := B(13 bits, default -> False)
            ca(2 downto 0) := frontend.io.pop.payload.addr.asBits(2 downto 0)

            val cmd = HyperBus.Phy.CmdCs(p)
            val addr = frontend.io.pop.payload.addr(log2Up(p.hyperbus.memorySpace) - 1 downto 0)
            if (partitions.length == 1) {
              // TODO error when !inPartitions
              // TODO error when frontend.io.pop.payload.read && !_.read
              cmd.index := 0
            } else {
              val (inPartitions, index) = partitions.sFindFirst(x => x.low <= addr && addr < x.high)
              // TODO error when !inPartitions
              // TODO error when frontend.io.pop.payload.read && !_.read
              // TODO split access over partition boundary
              cmd.index := index
            }
            cmd.latencyCycles := io.config.latencyCycles
            when(!frontend.io.pop.payload.memory && !frontend.io.pop.payload.read) {
              cmd.latencyCycles := 0
            }
            cmd.read := frontend.io.pop.payload.read
            io.phy.cmd.args := cmd.asBits.resized
            io.phy.cmd.valid := True
            when(io.phy.cmd.ready) {
              goto(cmdAddr)
            }
          }
        }
      }

      val cmdAddr: State = new State {
        whenIsActive {
          val cmd = HyperBus.Phy.CmdAddr(p)
          cmd.addr := counter.mux(
            0 -> ca(5 * 8 + 8 - 1 downto 5 * 8),
            1 -> ca(4 * 8 + 8 - 1 downto 4 * 8),
            2 -> ca(3 * 8 + 8 - 1 downto 3 * 8),
            3 -> ca(2 * 8 + 8 - 1 downto 2 * 8),
            4 -> ca(1 * 8 + 8 - 1 downto 1 * 8),
            5 -> ca(0 * 8 + 8 - 1 downto 0 * 8),
            default -> B(8 bits, default -> False)
          )
          io.phy.cmd.mode := HyperBus.Phy.CmdMode.ADDR
          io.phy.cmd.args := cmd.asBits.resized
          io.phy.cmd.valid := True
          when(io.phy.cmd.ready) {
            counter := counter + 1
            when(counter === 5) {
              counter := 0
              goto(write)
            }
          }
        }
      }

      val write: State = new State {
        whenIsActive {
          val cmd = HyperBus.Phy.CmdData(p)
          cmd.data := 0
          cmd.mask := True
          cmd.last := False

          when(frontend.io.pop.payload.strobe === B"0001") {
            cmd.data := frontend.io.pop.payload.data(7 downto 0)
            when(frontend.io.pop.payload.unaligned && counter === 0) {
              cmd.mask := False
            }
            when(!frontend.io.pop.payload.unaligned && counter === 1) {
              cmd.mask := False
            }
            when(counter === 1) {
              cmd.last := True
            }
          }
          when(frontend.io.pop.payload.strobe === B"0011") {
            when(frontend.io.pop.payload.unaligned) {
              cmd.data := counter.mux(
                0 -> frontend.io.pop.payload.data(0 * 8 + 8 - 1 downto 0 * 8),
                3 -> frontend.io.pop.payload.data(1 * 8 + 8 - 1 downto 1 * 8),
                default -> B(8 bits, default -> False)
              )
              when(counter === 1 || counter === 3) {
                cmd.mask := False
              }
              when(counter === 3) {
                cmd.last := True
              }
            } otherwise {
              cmd.data := counter.mux(
                0 -> frontend.io.pop.payload.data(1 * 8 + 8 - 1 downto 1 * 8),
                1 -> frontend.io.pop.payload.data(0 * 8 + 8 - 1 downto 0 * 8),
                default -> B(8 bits, default -> False)
              )
              cmd.mask := False
              when(counter === 1) {
                cmd.last := True
              }
            }
          }
          when(frontend.io.pop.payload.strobe === B"1111") {
            when(!frontend.io.pop.payload.unaligned) {
              cmd.data := counter.mux(
                0 -> frontend.io.pop.payload.data(1 * 8 + 8 - 1 downto 1 * 8),
                1 -> frontend.io.pop.payload.data(0 * 8 + 8 - 1 downto 0 * 8),
                2 -> frontend.io.pop.payload.data(3 * 8 + 8 - 1 downto 3 * 8),
                3 -> frontend.io.pop.payload.data(2 * 8 + 8 - 1 downto 2 * 8),
                default -> B(8 bits, default -> False)
              )
              cmd.mask := False
              when(counter === 3) {
                cmd.last := True
              }
            } otherwise {
              cmd.data := counter.mux(
                0 -> frontend.io.pop.payload.data(0 * 8 + 8 - 1 downto 0 * 8),
                2 -> frontend.io.pop.payload.data(2 * 8 + 8 - 1 downto 2 * 8),
                3 -> frontend.io.pop.payload.data(1 * 8 + 8 - 1 downto 1 * 8),
                5 -> frontend.io.pop.payload.data(3 * 8 + 8 - 1 downto 3 * 8),
                default -> B(8 bits, default -> False)
              )
              when(counter === 1 || counter === 2 || counter === 3 || counter === 5) {
                cmd.mask := False
              }
              when(counter === 3) {
                cmd.last := True
              }
            }
          }

          io.phy.cmd.mode := HyperBus.Phy.CmdMode.DATA
          io.phy.cmd.args := cmd.asBits.resized
          io.phy.cmd.valid := True
          when(io.phy.cmd.ready) {
            counter := counter + 1
            when(frontend.io.pop.memory && cmd.last) {
              counter := 0
              goto(read)
            }
            when(!frontend.io.pop.memory && counter === 1) {
              counter := 0
              goto(read)
            }
          }
        }
      }

      val read: State = new State {
        whenIsActive {
          when(io.phy.rsp.valid) {
            val result = io.phy.rsp.payload.data

            when(frontend.io.pop.payload.strobe === B"0001") {
              when(frontend.io.pop.payload.unaligned && counter === 0) {
                data(7 downto 0) := result
              }
              when(!frontend.io.pop.payload.unaligned && counter === 1) {
                data(7 downto 0) := result
              }
            }
            when(frontend.io.pop.payload.strobe === B"0011") {
              when(frontend.io.pop.payload.unaligned) {
                data := counter.mux(
                  0 -> data(31 downto 8) ## result,
                  3 -> data(31 downto 16) ## result ## data(7 downto 0),
                  default -> data
                )
              } otherwise {
                data := counter.mux(
                  0 -> data(31 downto 16) ## result ## data(7 downto 0),
                  1 -> data(31 downto 8) ## result,
                  default -> data
                )
              }
            }
            when(frontend.io.pop.payload.strobe === B"1111") {
              when(frontend.io.pop.payload.unaligned) {
                data := counter.mux(
                  0 -> data(31 downto 8) ## result,
                  2 -> data(31 downto 24) ## result ## data(15 downto 0),
                  3 -> data(31 downto 16) ## result ## data(7 downto 0),
                  5 -> result ## data(23 downto 0),
                  default -> data
                )
              } otherwise {
                data := counter.mux(
                  0 -> data(31 downto 16) ## result ## data(7 downto 0),
                  1 -> data(31 downto 8) ## result,
                  2 -> result ## data(23 downto 0),
                  3 -> data(31 downto 24) ## result ## data(15 downto 0),
                  default -> data
                )
              }
            }

            io.phy.rsp.ready := True
            counter := counter + 1
            when(io.phy.rsp.payload.last) {
              counter := 0
              goto(response)
            }
          }
        }
      }
      val response: State = new State {
        whenIsActive {
          when(frontend.io.pop.memory) {
            io.frontend.payload.data := data
            when(!frontend.io.pop.payload.read) {
              io.frontend.payload.data := 0
            }
            io.frontend.valid := True
            when(io.frontend.fire) {
              frontend.io.pop.ready := True
              goto(init)
            }
          } otherwise {
            io.config.rsp.payload := False ## data(15 downto 0)
            when(!frontend.io.pop.payload.read) {
              io.config.rsp.payload := 0
            }
            io.config.rsp.valid := True
            when(io.config.rsp.fire) {
              frontend.io.pop.ready := True
              goto(init)
            }
          }
        }
      }
      val error: State = new State {
        whenIsActive {
          when(frontend.io.pop.memory) {
            io.frontend.error := True
            io.frontend.valid := True
            when(io.frontend.fire) {
              frontend.io.pop.ready := True
              goto(init)
            }
          } otherwise {
            io.config.rsp.payload := True ## data(15 downto 0)
            io.config.rsp.valid := True
            when(io.config.rsp.fire) {
              frontend.io.pop.ready := True
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
    // 0x000x IP information

    // 0x001x RESET
    ctrl.config.phy.reset.trigger := False
    busCtrl.onWrite(0x10) {
      ctrl.config.phy.reset.trigger := True
    }
    val resetPulse = Reg(ctrl.config.phy.reset.pulse)
    val resetHalt = Reg(ctrl.config.phy.reset.halt)
    if (p.init != null && p.init.resetPulse != 0)
      resetPulse.init(U(p.init.resetPulse, widthOf(ctrl.config.phy.reset.pulse) bit))
    if (p.init != null && p.init.resetHalt != 0)
      resetHalt.init(U(p.init.resetHalt, widthOf(ctrl.config.phy.reset.halt) bit))

    busCtrl.readAndWrite(resetPulse, 0x14)
    busCtrl.readAndWrite(resetHalt, 0x18)
    ctrl.config.phy.reset.pulse := resetPulse
    ctrl.config.phy.reset.halt := resetHalt

    // 0x002x TIMINGS
    val latencyCycles = Reg(ctrl.config.latencyCycles)
    if (p.init != null && p.init.latencyCycles != 0)
      latencyCycles.init(U(p.init.latencyCycles, widthOf(ctrl.config.latencyCycles) bit))
    busCtrl.readAndWrite(latencyCycles, 0x20)
    ctrl.config.latencyCycles := latencyCycles

    // 0x003x REGISTER
    val cmdLogic = new Area {
      val streamUnbuffered = busCtrl.createAndDriveFlow(Bits(32 bits), address = 0x30).toStream
      val (stream, fifoAvailability) =
        streamUnbuffered.queueWithAvailability(p.hyperbus.registerCmdFifoDepth)
      ctrl.config.cmd << stream
      busCtrl.read(fifoAvailability, address = 0x34, 16)
    }

    val rspLogic = new Area {
      val (stream, fifoOccupancy) =
        ctrl.config.rsp.queueWithOccupancy(p.hyperbus.registerRspFifoDepth)
      busCtrl.readStreamNonBlocking(
        stream,
        address = 0x30,
        validBitOffset = 31,
        payloadBitOffset = 0
      )
      busCtrl.read(fifoOccupancy, address = 0x34, 0)
    }
  }
}
