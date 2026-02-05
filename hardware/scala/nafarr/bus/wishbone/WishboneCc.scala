// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.bus.wishbone

import spinal.core._
import spinal.lib._
import spinal.lib.bus.wishbone._

import spinal.core._
import spinal.lib._
import spinal.lib.bus.wishbone._

case class WishboneCcFifo(
    cfg: WishboneConfig,
    inputCd: ClockDomain,
    outputCd: ClockDomain,
    depth: Int = 4
) extends Component {
  val io = new Bundle {
    val input = slave(Wishbone(cfg))
    val output = master(Wishbone(cfg))
  }

  // Calculate total width for request payload
  def calcReqWidth: Int = {
    var width = cfg.addressWidth + cfg.dataWidth + cfg.selWidth + 1 // ADR + DAT + SEL + WE
    if (cfg.useCTI) width += 3
    if (cfg.useBTE) width += 2
    width
  }

  // Request payload bundle
  case class WishboneReq() extends Bundle {
    val adr = UInt(cfg.addressWidth bits)
    val dat = Bits(cfg.dataWidth bits)
    val sel = Bits(cfg.selWidth bits)
    val we = Bool()
    val cti = if (cfg.useCTI) Bits(3 bits) else null
    val bte = if (cfg.useBTE) Bits(2 bits) else null
  }

  // Response payload bundle
  case class WishboneResp() extends Bundle {
    val dat = Bits(cfg.dataWidth bits)
    val err = if (cfg.useERR) Bool() else null
    val rty = if (cfg.useRTY) Bool() else null
  }

  // Request FIFO (input domain -> output domain)
  val reqFifo = StreamFifoCC(
    dataType = WishboneReq(),
    depth = depth,
    pushClock = inputCd,
    popClock = outputCd
  )

  // Response FIFO (output domain -> input domain)
  val respFifo = StreamFifoCC(
    dataType = WishboneResp(),
    depth = depth,
    pushClock = outputCd,
    popClock = inputCd
  )

  // Input clock domain logic
  val inputArea = new ClockingArea(inputCd) {
    val req = WishboneReq()
    req.adr := io.input.ADR
    req.dat := io.input.DAT_MOSI
    req.sel := io.input.SEL
    req.we := io.input.WE
    if (cfg.useCTI) req.cti := io.input.CTI
    if (cfg.useBTE) req.bte := io.input.BTE

    // Push request when STB is asserted and FIFO has space
    reqFifo.io.push.valid := io.input.STB && io.input.CYC
    reqFifo.io.push.payload := req

    // Pop response when available
    respFifo.io.pop.ready := True

    // Generate ACK when response is available
    io.input.ACK := respFifo.io.pop.fire
    io.input.DAT_MISO := respFifo.io.pop.payload.dat

    if (cfg.useERR) {
      io.input.ERR := respFifo.io.pop.valid && respFifo.io.pop.payload.err
    }
    if (cfg.useRTY) {
      io.input.RTY := respFifo.io.pop.valid && respFifo.io.pop.payload.rty
    }

    // Apply backpressure when request FIFO is full or response FIFO has no space
    // This prevents accepting requests we can't respond to
    val canAccept = reqFifo.io.push.ready
    when(!canAccept && io.input.STB) {
      io.input.ACK := False
    }
  }

  // Output clock domain logic
  val outputArea = new ClockingArea(outputCd) {
    val busy = Reg(Bool()) init (False)
    val currentReq = Reg(WishboneReq())

    // Pop request from FIFO when not busy
    reqFifo.io.pop.ready := !busy

    // Latch new request
    when(reqFifo.io.pop.fire) {
      busy := True
      currentReq := reqFifo.io.pop.payload
    }

    // Drive output Wishbone interface
    io.output.CYC := busy
    io.output.STB := busy
    io.output.ADR := currentReq.adr
    io.output.DAT_MOSI := currentReq.dat
    io.output.SEL := currentReq.sel
    io.output.WE := currentReq.we
    if (cfg.useCTI) io.output.CTI := currentReq.cti
    if (cfg.useBTE) io.output.BTE := currentReq.bte

    // Capture response
    val resp = WishboneResp()
    resp.dat := io.output.DAT_MISO
    if (cfg.useERR) resp.err := io.output.ERR
    if (cfg.useRTY) resp.rty := io.output.RTY

    // Push response when ACK received and response FIFO has space
    respFifo.io.push.valid := False
    respFifo.io.push.payload := resp

    when(busy && io.output.ACK) {
      busy := False
      respFifo.io.push.valid := True
    }

    // Handle error/retry responses
    if (cfg.useERR || cfg.useRTY) {
      when(busy && (io.output.ERR || io.output.RTY)) {
        busy := False
        respFifo.io.push.valid := True
      }
    }
  }
}

case class WishboneCcToggle(
    cfg: WishboneConfig,
    inputCd: ClockDomain,
    outputCd: ClockDomain
) extends Component {
  val io = new Bundle {
    val input = slave(Wishbone(cfg))
    val output = master(Wishbone(cfg))
  }

  // Cross-domain signals (declared outside ClockingAreas)
  val reqToggleAsync = Bool()
  val ackToggleAsync = Bool()
  val responseDataAsync = Bits(cfg.dataWidth bits)

  // Input clock domain area
  val inputArea = new ClockingArea(inputCd) {
    val reqToggle = Reg(Bool()) init (False)
    val ack = Reg(Bool()) init (False)

    // Store request data when STB goes high
    val addrReg = Reg(UInt(cfg.addressWidth bits))
    val dataReg = Reg(Bits(cfg.dataWidth bits))
    val selReg = Reg(Bits(cfg.selWidth bits))
    val weReg = Reg(Bool())
    val ctiReg = if (cfg.useCTI) Reg(Bits(3 bits)) else null
    val bteReg = if (cfg.useBTE) Reg(Bits(2 bits)) else null

    val busy = Reg(Bool()) init (False)
    val responseData = Reg(Bits(cfg.dataWidth bits))

    // Detect rising edge of input STB
    val stbRise = io.input.STB && !busy

    when(stbRise) {
      busy := True
      reqToggle := !reqToggle
      addrReg := io.input.ADR
      dataReg := io.input.DAT_MOSI
      selReg := io.input.SEL
      weReg := io.input.WE
      if (cfg.useCTI) ctiReg := io.input.CTI
      if (cfg.useBTE) bteReg := io.input.BTE
    }

    // Synchronize ack toggle from output domain
    val ackToggleSync = BufferCC(ackToggleAsync, False)
    val ackToggleLast = RegNext(ackToggleSync) init (False)

    // Synchronize response data
    val responseDataSync = BufferCC(responseDataAsync)

    // Detect when ack toggle changes (response received)
    when(ackToggleSync =/= ackToggleLast) {
      ack := True
      busy := False
      responseData := responseDataSync
    }.otherwise {
      ack := False
    }

    // Drive input interface
    io.input.ACK := ack
    io.input.DAT_MISO := responseData
    if (cfg.useERR) io.input.ERR := False
    if (cfg.useRTY) io.input.RTY := False

    // Connect async signal
    reqToggleAsync := reqToggle
  }

  // Output clock domain area
  val outputArea = new ClockingArea(outputCd) {
    val reqToggleSync = BufferCC(reqToggleAsync, False)
    val reqToggleLast = RegNext(reqToggleSync) init (False)
    val ackToggle = Reg(Bool()) init (False)

    val addr = Reg(UInt(cfg.addressWidth bits))
    val data = Reg(Bits(cfg.dataWidth bits))
    val sel = Reg(Bits(cfg.selWidth bits))
    val we = Reg(Bool())
    val cti = if (cfg.useCTI) Reg(Bits(3 bits)) else null
    val bte = if (cfg.useBTE) Reg(Bits(2 bits)) else null

    val stb = Reg(Bool()) init (False)
    val responseData = Reg(Bits(cfg.dataWidth bits))

    // Detect request toggle change
    val reqDetected = reqToggleSync =/= reqToggleLast

    when(reqDetected && !stb) {
      // Latch new request (sync the data registers from input domain)
      stb := True
      addr := BufferCC(inputArea.addrReg)
      data := BufferCC(inputArea.dataReg)
      sel := BufferCC(inputArea.selReg)
      we := BufferCC(inputArea.weReg)
      if (cfg.useCTI) cti := BufferCC(inputArea.ctiReg)
      if (cfg.useBTE) bte := BufferCC(inputArea.bteReg)
    }

    // Wait for ACK from output bus
    when(stb && io.output.ACK) {
      stb := False
      responseData := io.output.DAT_MISO
      ackToggle := !ackToggle
    }

    // Drive output interface
    io.output.ADR := addr
    io.output.DAT_MOSI := data
    io.output.SEL := sel
    io.output.WE := we
    io.output.STB := stb
    io.output.CYC := stb
    if (cfg.useCTI) io.output.CTI := cti
    if (cfg.useBTE) io.output.BTE := bte

    // Connect async signals
    ackToggleAsync := ackToggle
    responseDataAsync := responseData
  }
}
