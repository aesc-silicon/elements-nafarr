// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  Opcode,
  SlaveFactory => TileLinkSlaveFactory
}

import nafarr.Feature
import nafarr.memory.hyperbus.phy.{HyperBusGenericPhy, HyperBusGenericDdrPhy}
import nafarr.peripherals.SysconFeatures

object TileLinkHyperBus {

  /** Converts TileLink requests into 32-bit word commands of the HyperBus controller.
    *
    * A request covers the 32-bit words from its 4-byte aligned address, at least one. Each
    * word becomes one controller command; `last` marks the final word of the burst. Read
    * responses are packed into D beats of `dataWidth / 32` words, starting at the request's
    * word position within the beat. Writes take the data and byte strobes of each covered
    * word (all strobes for PUT_FULL_DATA), drain one frontend ACK per word and answer with a
    * single ACCESS_ACK.
    */
  case class BusAdapter(p: HyperBusCtrl.Parameter, busConfig: TileLinkParameter) extends Component {
    require(p.frontend.dataWidth == 32, "the controller frontend must be 32 bits wide")
    require(Seq(32, 64, 128).contains(busConfig.dataWidth), "dataWidth must be 32, 64 or 128")
    require(
      busConfig.sizeBytes / 4 <= p.frontend.storageDepth,
      "a burst must fit into the controller's command storage"
    )

    val io = new Bundle {
      val bus = slave(TileLinkBus(busConfig))
      val controller = master(Stream(HyperBus.ControllerInterface(p)))
      val frontend = slave(Stream(HyperBus.FrontendInterface(p)))
    }

    val wordsPerBeat = busConfig.dataWidth / 32
    val laneWidth = log2Up(wordsPerBeat)
    val countWidth = log2Up((busConfig.sizeBytes / 4).max(1) + 1)
    val a = io.bus.a
    val d = io.bus.d

    val source = Reg(busConfig.source())
    val size = Reg(busConfig.size())
    val baseAddr = Reg(UInt(p.frontend.addrWidth bits))
    val totalWords = Reg(UInt(countWidth bits))
    val cmdIndex = Reg(UInt(countWidth bits))
    val rspIndex = Reg(UInt(countWidth bits))
    val lane = Reg(UInt(laneWidth bits))
    val words = Vec(Reg(Bits(32 bits)), wordsPerBeat)
    val full = RegInit(False)
    val fullLast = Reg(Bool())

    // Word count of the request and its first word position within a beat; lanes only
    // exist for buses wider than 32 bits.
    val requestWords = ((U(1, 11 bits) |<< a.size) + 3) >> 2
    val wordLane =
      if (laneWidth > 0) a.address(busConfig.dataBytesLog2Up - 1 downto 2) else U(0, 0 bits)
    val atLastLane = if (laneWidth > 0) lane === (wordsPerBeat - 1) else True
    val lastCommand = cmdIndex === totalWords - 1
    val lastResponse = rspIndex === totalWords - 1
    // Byte lanes a PUT_FULL_DATA covers: all lanes from a full beat on, else the lanes of
    // its size at its address (a sub-word write must not touch the rest of the word).
    val fullMask = Bits(busConfig.dataBytes bits)
    fullMask.setAll()
    for (k <- 0 until busConfig.dataBytesLog2Up) {
      when(a.size === k) {
        fullMask := (B((BigInt(1) << (1 << k)) - 1, busConfig.dataBytes bits) <<
          a.address(busConfig.dataBytesLog2Up - 1 downto 0)).resized
      }
    }
    val writeMask = Mux(a.opcode === Opcode.A.PUT_FULL_DATA(), fullMask, a.mask)
    val laneData = if (laneWidth > 0) a.data.subdivideIn(32 bits)(lane) else a.data
    val laneMask = if (laneWidth > 0) writeMask.subdivideIn(4 bits)(lane) else writeMask

    a.ready := False
    io.controller.valid := False
    io.controller.payload.id := 0
    io.controller.payload.read := False
    io.controller.payload.memory := True
    io.controller.payload.unaligned := False
    io.controller.payload.addr := (baseAddr + (cmdIndex << 2)).resized
    io.controller.payload.data := laneData
    io.controller.payload.strobe := laneMask
    io.controller.payload.last := lastCommand
    io.frontend.ready := False

    d.valid := False
    d.opcode := Opcode.D.ACCESS_ACK_DATA()
    d.param := 0
    d.size := size
    d.source := source
    d.sink := 0
    d.denied := False
    d.data := words.asBits
    d.corrupt := False

    val fsm = new StateMachine {
      val idle: State = new State with EntryPoint {
        whenIsActive {
          when(a.valid) {
            source := a.source
            size := a.size
            baseAddr := (a.address(a.address.high downto 2) @@ U(0, 2 bits)).resized
            totalWords := requestWords.resized
            cmdIndex := 0
            rspIndex := 0
            if (laneWidth > 0) lane := wordLane
            when(a.opcode === Opcode.A.GET()) {
              // A GET has a single A beat; take it here.
              a.ready := True
              goto(readCmd)
            } otherwise {
              // A PUT's beats are taken in writeCmd, one per issued beat.
              goto(writeCmd)
            }
          }
        }
      }

      val readCmd: State = new State {
        whenIsActive {
          io.controller.valid := True
          io.controller.payload.read := True
          io.controller.payload.data := 0
          io.controller.payload.strobe.setAll()
          when(io.controller.fire) {
            cmdIndex := cmdIndex + 1
            when(lastCommand) {
              goto(readRsp)
            }
          }
        }
      }

      val readRsp: State = new State {
        whenIsActive {
          io.frontend.ready := !full
          when(io.frontend.fire) {
            if (laneWidth > 0) words(lane) := io.frontend.payload.data
            else words(0) := io.frontend.payload.data
            rspIndex := rspIndex + 1
            when(atLastLane || lastResponse) {
              full := True
              fullLast := lastResponse
            } otherwise {
              if (laneWidth > 0) lane := lane + 1
            }
          }
          d.valid := full
          when(d.fire) {
            full := False
            if (laneWidth > 0) lane := 0
            when(fullLast) {
              goto(idle)
            }
          }
        }
      }

      val writeCmd: State = new State {
        whenIsActive {
          io.controller.valid := a.valid
          when(io.controller.fire) {
            cmdIndex := cmdIndex + 1
            when(atLastLane || lastCommand) {
              // All covered words of this beat are issued: take the beat.
              a.ready := True
              if (laneWidth > 0) lane := 0
            } otherwise {
              if (laneWidth > 0) lane := lane + 1
            }
            when(lastCommand) {
              goto(writeRsp)
            }
          }
        }
      }

      val writeRsp: State = new State {
        whenIsActive {
          io.frontend.ready := True
          when(io.frontend.valid) {
            rspIndex := rspIndex + 1
            when(lastResponse) {
              goto(writeAck)
            }
          }
        }
      }

      val writeAck: State = new State {
        whenIsActive {
          d.valid := True
          d.opcode := Opcode.D.ACCESS_ACK()
          when(d.ready) {
            goto(idle)
          }
        }
      }
    }
  }
}

/** TileLink wrapper for the HyperBus controller.
  *
  * Exposes two TileLink slave ports and a raw PHY interface:
  *
  *  dataBus - TL-UH burst-capable memory port.  Accepts GET (burst read) and
  *             PUT_FULL_DATA (burst write) transactions.  The number of D-beats
  *             (for reads) or A-beats (for writes) is determined by a.size.
  *             A read-only cache (TileLinkCache) may be placed in front to
  *             accelerate repeated reads.
  *
  *  cfgBus  - TL-UL configuration port.  Mapped to the HyperBusCtrl register
  *             layout (reset pulse/halt timing, latency cycles, HyperBus
  *             register access FIFO).
  *
  *  phy     - Raw PHY command/response interface.  Connect to
  *             HyperBusGenericPhy or a technology-specific PHY.  For FPGA use
  *             TileLinkHyperBusGenericPhyCluster, which includes the PHY.
  *
  * Protocol mapping (see TileLinkHyperBus.BusAdapter)
  * ----------------
  * Read  (GET):  one controller command per covered 32-bit word; the frontend
  *               responses are packed into D beats of the bus width.
  * Write (PUT):  one controller command per covered 32-bit word of each A beat;
  *               the frontend ACKs are drained, then a single ACCESS_ACK is sent.
  *
  * @param p            HyperBus controller parameter.
  * @param busConfig    TileLink parameter for the data bus (TL-UH).
  *                     dataWidth may be 32, 64 or 128.  sizeBytes sets the max burst.
  * @param cfgBusConfig TileLink parameter for the configuration bus (TL-UL).
  */
case class TileLinkHyperBus(
    p: HyperBusCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter = TileLinkParameter.simple(10, 32, 4, 1)
) extends Component
    with SysconFeatures {

  val io = new Bundle {
    val dataBus = slave(TileLinkBus(busConfig))
    val cfgBus = slave(TileLinkBus(cfgBusConfig))
    val phy = master(HyperBus.Phy.Interface(p))
    val error = out Bool ()
  }

  override def sysconFeatures = Some(List(Feature.Hyperbus))

  // -------------------------------------------------------------------------
  // HyperBus controller + configuration register mapper
  // -------------------------------------------------------------------------
  val ctrl = HyperBusCtrl(p)
  io.phy <> ctrl.io.phy

  val cfgFactory = new TileLinkSlaveFactory(io.cfgBus, false)
  val mapper = HyperBusCtrl.Mapper(cfgFactory, ctrl.io, p)
  io.error := mapper.error

  // -------------------------------------------------------------------------
  // Data bus - TileLink <-> 32-bit controller command / frontend response streams
  // -------------------------------------------------------------------------
  val busAdapter = TileLinkHyperBus.BusAdapter(p, busConfig)
  busAdapter.io.bus <> io.dataBus
  ctrl.io.controller << busAdapter.io.controller
  busAdapter.io.frontend << ctrl.io.frontend
}

/** TileLinkHyperBus bundled with HyperBusGenericPhy for FPGA targets.
  *
  * Mirrors BmbHyperBusGenericPhyCluster: combines TileLinkHyperBus with
  * HyperBusGenericPhy and exposes dataBus, cfgBus, and the top-level HyperBus
  * IO pad bundle.
  *
  * @param p            HyperBus controller parameter.
  * @param busConfig    TileLink parameter for the data bus (TL-UH).
  * @param cfgBusConfig TileLink parameter for the configuration bus (TL-UL).
  */
abstract class TileLinkHyperBusCluster(
    p: HyperBusCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter
) extends Component {
  val io = new Bundle {
    val dataBus = slave(TileLinkBus(busConfig))
    val cfgBus = slave(TileLinkBus(cfgBusConfig))
    val hyperbus = master(HyperBus.Io(p))
    val error = out Bool ()
  }
}

case class TileLinkHyperBusGenericPhyCluster(
    p: HyperBusCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter = TileLinkParameter.simple(10, 32, 4, 1)
) extends TileLinkHyperBusCluster(p, busConfig, cfgBusConfig) {

  val ctrl = TileLinkHyperBus(p, busConfig, cfgBusConfig)
  val phy = HyperBusGenericPhy(p)

  ctrl.io.dataBus <> io.dataBus
  ctrl.io.cfgBus <> io.cfgBus
  ctrl.io.phy <> phy.io.phy
  io.hyperbus <> phy.io.hyperbus
  io.error := ctrl.io.error
}

/** TileLinkHyperBus bundled with the full-rate DDR PHY (ck at the domain clock
  * via SoftDdr, two bytes/cycle, no clock divider). Drop-in alternative to
  * TileLinkHyperBusGenericPhyCluster, selectable through the platform's
  * hyperBusLogic lambda.
  */
case class TileLinkHyperBusGenericDdrPhyCluster(
    p: HyperBusCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter = TileLinkParameter.simple(10, 32, 4, 1)
) extends TileLinkHyperBusCluster(p, busConfig, cfgBusConfig) {

  val ctrl = TileLinkHyperBus(p, busConfig, cfgBusConfig)
  val phy = HyperBusGenericDdrPhy(p)

  ctrl.io.dataBus <> io.dataBus
  ctrl.io.cfgBus <> io.cfgBus
  ctrl.io.phy <> phy.io.phy
  io.hyperbus <> phy.io.hyperbus
  io.error := ctrl.io.error
}
