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

import nafarr.memory.hyperbus.phy.HyperBusGenericPhy

/** TileLink wrapper for the HyperBus controller.
  *
  * Exposes two TileLink slave ports and a raw PHY interface:
  *
  *  dataBus — TL-UH burst-capable memory port.  Accepts GET (burst read) and
  *             PUT_FULL_DATA (burst write) transactions.  The number of D-beats
  *             (for reads) or A-beats (for writes) is determined by a.size.
  *             A read-only cache (TileLinkCache) may be placed in front to
  *             accelerate repeated reads.
  *
  *  cfgBus  — TL-UL configuration port.  Mapped to the HyperBusCtrl register
  *             layout (reset pulse/halt timing, latency cycles, HyperBus
  *             register access FIFO).
  *
  *  phy     — Raw PHY command/response interface.  Connect to
  *             HyperBusGenericPhy or a technology-specific PHY.  For FPGA use
  *             TileLinkHyperBusGenericPhyCluster, which includes the PHY.
  *
  * Protocol mapping
  * ----------------
  * Read  (GET):          Single A-beat accepted in idle → N controller stream
  *                       commands → N D-channel beats.
  * Write (PUT_FULL_DATA): N A-beats paired one-for-one with N controller stream
  *                       commands → N frontend ACKs drained → single ACCESS_ACK.
  *
  * @param p            HyperBus controller parameter.
  * @param busConfig    TileLink parameter for the data bus (TL-UH).
  *                     dataWidth must equal 32.  sizeBytes sets the max burst.
  * @param cfgBusConfig TileLink parameter for the configuration bus (TL-UL).
  */
case class TileLinkHyperBus(
    p: HyperBusCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter = TileLinkParameter.simple(10, 32, 4, 1)
) extends Component {

  val io = new Bundle {
    val dataBus = slave(TileLinkBus(busConfig))
    val cfgBus = slave(TileLinkBus(cfgBusConfig))
    val phy = master(HyperBus.Phy.Interface(p))
  }

  // -------------------------------------------------------------------------
  // HyperBus controller + configuration register mapper
  // -------------------------------------------------------------------------
  val ctrl = HyperBusCtrl(p)
  io.phy <> ctrl.io.phy

  val cfgFactory = new TileLinkSlaveFactory(io.cfgBus, false)
  HyperBusCtrl.Mapper(cfgFactory, ctrl.io, p)

  // -------------------------------------------------------------------------
  // Data bus — TileLink ↔ HyperBus.ControllerInterface bridge
  // -------------------------------------------------------------------------
  private val dataBytesLog2 = busConfig.dataBytesLog2Up

  // Maximum word count per burst (Scala elaboration-time constant).
  private val maxWords = 1 << (busConfig.sizeMax - dataBytesLog2)
  private val cntWidth = log2Up(maxWords + 1)

  // Transaction metadata, registered on the first accepted A-beat.
  val regSource = Reg(busConfig.source())
  val regSize = Reg(busConfig.size())
  val regAddr = Reg(UInt(busConfig.addressWidth bits))

  // Word count for the current burst — combinatorial from regSize.
  val regWords = (U(1, cntWidth + 1 bits) << (regSize - dataBytesLog2)).resize(cntWidth)

  // Beat counters.
  val cmdCounter = Reg(UInt(cntWidth bits)) init 0 // controller commands issued
  val rspCounter = Reg(UInt(cntWidth bits)) init 0 // frontend responses consumed

  // ---- D-channel defaults (active state overrides where needed) ----------
  io.dataBus.a.ready := False
  io.dataBus.d.valid := False
  io.dataBus.d.opcode := Opcode.D.ACCESS_ACK_DATA()
  io.dataBus.d.param := 0
  io.dataBus.d.size := regSize
  io.dataBus.d.source := regSource
  io.dataBus.d.sink := 0
  io.dataBus.d.denied := False
  io.dataBus.d.data := ctrl.io.frontend.payload.data
  io.dataBus.d.corrupt := False

  // ---- Controller stream defaults ----------------------------------------
  ctrl.io.controller.valid := False
  ctrl.io.controller.payload.id := 0
  ctrl.io.controller.payload.read := False
  ctrl.io.controller.payload.memory := True
  ctrl.io.controller.payload.unaligned := False
  // Byte address of the current word in the burst.
  ctrl.io.controller.payload.addr :=
    (regAddr + (cmdCounter << dataBytesLog2)).resize(p.frontend.addrWidth)
  // Write data / strobe come directly from the live A-channel (valid in writeCmd).
  ctrl.io.controller.payload.data := io.dataBus.a.data
  ctrl.io.controller.payload.strobe := io.dataBus.a.mask
  // Assert last on the final word of every burst.
  ctrl.io.controller.payload.last := (cmdCounter === (regWords - 1).resized)

  ctrl.io.frontend.ready := False

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val fsm = new StateMachine {

    // ---- IDLE ---------------------------------------------------------------
    val idle: State = new State with EntryPoint {
      whenIsActive {
        when(io.dataBus.a.valid) {
          regSource := io.dataBus.a.source
          regSize := io.dataBus.a.size
          regAddr := io.dataBus.a.address
          cmdCounter := 0
          rspCounter := 0
          when(io.dataBus.a.opcode === Opcode.A.GET()) {
            // GET has exactly one A-beat; consume it here.
            io.dataBus.a.ready := True
            goto(readCmd)
          } otherwise {
            // PUT_FULL_DATA has N A-beats; writeCmd consumes them all.
            goto(writeCmd)
          }
        }
      }
    }

    // ---- READ: issue N controller commands ----------------------------------
    val readCmd: State = new State {
      whenIsActive {
        ctrl.io.controller.valid := True
        ctrl.io.controller.payload.read := True
        ctrl.io.controller.payload.strobe := B(busConfig.dataBytes bits, default -> true)
        ctrl.io.controller.payload.data := 0
        when(ctrl.io.controller.fire) {
          cmdCounter := cmdCounter + 1
          when(cmdCounter === (regWords - 1).resized) {
            cmdCounter := 0
            goto(readRsp)
          }
        }
      }
    }

    // ---- READ: forward N frontend responses as D-channel beats --------------
    val readRsp: State = new State {
      whenIsActive {
        io.dataBus.d.valid := ctrl.io.frontend.valid
        io.dataBus.d.opcode := Opcode.D.ACCESS_ACK_DATA()
        when(ctrl.io.frontend.valid && io.dataBus.d.ready) {
          ctrl.io.frontend.ready := True
          rspCounter := rspCounter + 1
          when(rspCounter === (regWords - 1).resized) {
            goto(idle)
          }
        }
      }
    }

    // ---- WRITE: pair N A-beats with N controller commands ------------------
    // a.ready follows controller.ready so the two streams move in lock-step.
    val writeCmd: State = new State {
      whenIsActive {
        io.dataBus.a.ready := ctrl.io.controller.ready
        ctrl.io.controller.valid := io.dataBus.a.valid
        ctrl.io.controller.payload.read := False
        when(io.dataBus.a.valid && ctrl.io.controller.ready) {
          cmdCounter := cmdCounter + 1
          when(cmdCounter === (regWords - 1).resized) {
            cmdCounter := 0
            goto(writeRsp)
          }
        }
      }
    }

    // ---- WRITE: drain N frontend ACKs (one per written word) ---------------
    val writeRsp: State = new State {
      whenIsActive {
        ctrl.io.frontend.ready := True
        when(ctrl.io.frontend.valid) {
          rspCounter := rspCounter + 1
          when(rspCounter === (regWords - 1).resized) {
            goto(writeAck)
          }
        }
      }
    }

    // ---- WRITE: return a single ACCESS_ACK to the initiator ----------------
    val writeAck: State = new State {
      whenIsActive {
        io.dataBus.d.valid := True
        io.dataBus.d.opcode := Opcode.D.ACCESS_ACK()
        when(io.dataBus.d.ready) {
          goto(idle)
        }
      }
    }
  }
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
case class TileLinkHyperBusGenericPhyCluster(
    p: HyperBusCtrl.Parameter,
    busConfig: TileLinkParameter,
    cfgBusConfig: TileLinkParameter = TileLinkParameter.simple(10, 32, 4, 1)
) extends Component {

  val io = new Bundle {
    val dataBus = slave(TileLinkBus(busConfig))
    val cfgBus = slave(TileLinkBus(cfgBusConfig))
    val hyperbus = master(HyperBus.Io(p))
  }

  val ctrl = TileLinkHyperBus(p, busConfig, cfgBusConfig)
  val phy = HyperBusGenericPhy(p)

  ctrl.io.dataBus <> io.dataBus
  ctrl.io.cfgBus <> io.cfgBus
  ctrl.io.phy <> phy.io.phy
  io.hyperbus <> phy.io.hyperbus
}
