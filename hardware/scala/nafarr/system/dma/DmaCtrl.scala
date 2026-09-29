// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.dma

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.bus.tilelink.{Bus => TileLinkBus, BusParameter => TileLinkParameter, Opcode}
import spinal.lib.misc.InterruptCtrl

import nafarr.IpIdentification

object DmaCtrl {
  def apply(p: Parameter = Parameter.default()) = new DmaCtrl(p)

  case class Parameter(
      channels: Int = 2,
      requestLines: Int = 4,
      burstBytes: Int = 64,
      addressWidth: Int = 32,
      sourceWidth: Int = 1
  ) {
    require(channels >= 1 && channels <= 8, "channels must be 1..8")
    require(requestLines >= 0 && requestLines <= 16, "requestLines must be 0..16")
    require(
      burstBytes >= 4 && burstBytes <= 4096 && isPow2(burstBytes),
      "burstBytes must be a power of two between 4 and 4096"
    )
    require(addressWidth >= 12 && addressWidth <= 32, "addressWidth must be 12..32")
    require(sourceWidth >= 1 && sourceWidth <= 8, "sourceWidth must be 1..8")
    val dataWidth = 32
    val dataBytes = 4
    val burstLog2 = log2Up(burstBytes)
    val beatMax = burstBytes / dataBytes
    val channelStride = 0x20
    val irqSources = channels * 2
    def memParam = TileLinkParameter.simple(addressWidth, dataWidth, burstBytes, sourceWidth)
  }
  object Parameter {
    def default() = Parameter()
    def small() = Parameter(channels = 1, requestLines = 4, burstBytes = 16)
    def medium() = Parameter(channels = 2, requestLines = 8, burstBytes = 64)
    def large() = Parameter(channels = 8, requestLines = 16, burstBytes = 256)
  }

  object Regs {
    def apply(base: BigInt, p: Parameter) = new Regs(base, p)
  }
  class Regs(base: BigInt, p: Parameter) {
    val info = base + 0x00
    val irqPending = base + 0x04
    val irqMask = base + 0x08
    val status = base + 0x0c
    private def channelBase(ch: Int): BigInt = base + 0x10 + ch * p.channelStride
    def control(ch: Int) = channelBase(ch) + 0x00
    def config(ch: Int) = channelBase(ch) + 0x04
    def src(ch: Int) = channelBase(ch) + 0x08
    def dst(ch: Int) = channelBase(ch) + 0x0c
    def length(ch: Int) = channelBase(ch) + 0x10
    def next(ch: Int) = channelBase(ch) + 0x14
  }

  /** Bit positions of the channel config register (also word 0 of a descriptor). */
  object Config {
    val srcInc = 0
    val dstInc = 1
    val width = 2 // 2 bits: log2 of element size (0=1B, 1=2B, 2=4B)
    val reqEnable = 4
    val reqSel = 5 // 4 bits
    val linked = 9
    val irqDone = 10
    val burstLimit = 16 // 4 bits: log2 of max burst, 0 = burstBytes
  }

  case class ChannelIo(p: Parameter) extends Bundle {
    val start = in Bool ()
    val abort = in Bool ()
    val configWrite = slave Flow (Bits(32 bits))
    val srcWrite = slave Flow (UInt(p.addressWidth bits))
    val dstWrite = slave Flow (UInt(p.addressWidth bits))
    val lengthWrite = slave Flow (UInt(32 bits))
    val nextWrite = slave Flow (UInt(p.addressWidth bits))
    val config = out Bits (32 bits)
    val src = out UInt (p.addressWidth bits)
    val dst = out UInt (p.addressWidth bits)
    val length = out UInt (32 bits)
    val next = out UInt (p.addressWidth bits)
    val busy = out Bool ()
    val error = out Bool ()
    val requestActive = out Bool ()
    val done = out Bool ()
    val fault = out Bool ()
  }

  case class Io(p: Parameter) extends Bundle {
    val mem = master(TileLinkBus(p.memParam))
    val request = (p.requestLines > 0) generate Vec.fill(p.requestLines)(slave(DmaHandshake()))
    val channel = Vec(ChannelIo(p), p.channels)
  }

  /** DMA controller.
    *
    * One shared transfer engine serves all channels round-robin, one chunk at a time.
    * A chunk is either a naturally aligned burst (both addresses incrementing, no request
    * line: largest power of two that fits address alignment, remaining length and the burst
    * limit) or a single element of `width` bytes (a fixed address or a request-gated channel).
    * Each chunk is one GET followed by one PUT on the memory port; data is staged in a FIFO
    * and lane-shifted so sub-word elements move between arbitrary byte lanes.
    *
    * A channel whose descriptor completes with `linked` set and `next != 0` fetches the next
    * descriptor (config, src, dst, length, next) from memory and continues. Starting a channel
    * with length 0 therefore runs a descriptor list from `next`.
    *
    * Request lines use the four-phase handshake of [[DmaHandshake]]: a gated channel is only
    * arbitrated while its selected `req` is high and `ack` is low, and moves exactly one
    * element per grant. After the element completes, `ack` is raised until `req` drops.
    *
    * The engine is not cache coherent; software manages coherency (uncached buffers or
    * Zicbom cache operations).
    */
  case class DmaCtrl(p: Parameter) extends Component {
    val io = Io(p)
    val mem = io.mem
    val beatCntWidth = log2Up(p.beatMax + 1)
    val selWidth = log2Up(p.channels) max 1
    val chunkWidth = log2Up(p.burstLog2 + 1)

    val requestVec = Vec(Bool(), 16)
    val ackVec = Vec(Bool(), 16)
    // One-hot request line to acknowledge, driven by the engine when a gated element completes.
    val ackSet = Bits(16 bits)
    for (i <- 0 until 16) {
      if (i < p.requestLines) {
        val ack = RegInit(False)
        when(ackSet(i)) {
          ack := True
        } elsewhen (!io.request(i).req) {
          ack := False
        }
        io.request(i).ack := ack
        requestVec(i) := io.request(i).req
        ackVec(i) := ack
      } else {
        requestVec(i) := False
        ackVec(i) := False
      }
    }

    val owned = Bits(p.channels bits)

    class Channel(i: Int) extends Area {
      val port = io.channel(i)
      val busy = RegInit(False)
      val error = RegInit(False)
      val abort = RegInit(False)
      val fetch = RegInit(False)
      val config = Reg(Bits(32 bits)) init 0
      val src = Reg(UInt(p.addressWidth bits)) init 0
      val dst = Reg(UInt(p.addressWidth bits)) init 0
      val length = Reg(UInt(32 bits)) init 0
      val next = Reg(UInt(p.addressWidth bits)) init 0

      val srcInc = config(Config.srcInc)
      val dstInc = config(Config.dstInc)
      val width = config(Config.width, 2 bits).asUInt
      val reqEnable = config(Config.reqEnable)
      val reqSel = config(Config.reqSel, 4 bits).asUInt
      val linked = config(Config.linked)
      val irqDone = config(Config.irqDone)
      val burstLimit = config(Config.burstLimit, 4 bits).asUInt
      val request = requestVec(reqSel)
      val elementMode = !srcInc || !dstInc || reqEnable
      val gated = reqEnable && length =/= 0 && !fetch
      val eligible = busy && !abort && (!gated || (request && !ackVec(reqSel)))

      when(!busy) {
        when(port.configWrite.valid) {
          config := port.configWrite.payload
        }
        when(port.srcWrite.valid) {
          src := port.srcWrite.payload
        }
        when(port.dstWrite.valid) {
          dst := port.dstWrite.payload
        }
        when(port.lengthWrite.valid) {
          length := port.lengthWrite.payload
        }
        when(port.nextWrite.valid) {
          next := port.nextWrite.payload
        }
      }
      when(port.start && !busy) {
        busy := True
        error := False
        fetch := False
      }
      when(port.abort) {
        abort := True
      }
      // Abort takes effect between chunks, once the engine no longer owns the channel.
      when(abort && !owned(i)) {
        abort := False
        busy := False
        fetch := False
      }

      port.config := config
      port.src := src
      port.dst := dst
      port.length := length
      port.next := next
      port.busy := busy
      port.error := error
      port.requestActive := request
      port.done := False
      port.fault := False
    }
    val channels = for (i <- 0 until p.channels) yield new Channel(i).setName(s"channel_$i")

    val engine = new Area {
      val sel = Reg(UInt(selWidth bits)) init 0
      val priority = Reg(Bits(p.channels bits)) init 1
      val eligible = Bits(p.channels bits)
      for (i <- 0 until p.channels) {
        eligible(i) := channels(i).eligible
      }
      val grant = OHMasking.roundRobin(eligible, priority)

      def view[T <: Data](f: Channel => T): T = {
        if (p.channels == 1) f(channels(0)) else Vec(channels.map(f))(sel)
      }
      def onSel(body: Channel => Unit): Unit = {
        for ((c, i) <- channels.zipWithIndex) {
          when(sel === i) {
            body(c)
          }
        }
      }

      val curSrc = view(_.src)
      val curDst = view(_.dst)
      val curLength = view(_.length)
      val curNext = view(_.next)
      val curFetch = view(_.fetch)
      val curWidth = view(_.width)
      val curSrcInc = view(_.srcInc)
      val curDstInc = view(_.dstInc)
      val curLinked = view(_.linked)
      val curIrqDone = view(_.irqDone)
      val curAbort = view(_.abort)
      val curElementMode = view(_.elementMode)
      val curBurstLimit = view(_.burstLimit)
      val curReqEnable = view(_.reqEnable)
      val curReqSel = view(_.reqSel)

      ackSet := 0

      // Largest naturally aligned power-of-two chunk for burst mode.
      val kMax = UInt(chunkWidth bits)
      when(curBurstLimit === 0 || curBurstLimit > p.burstLog2) {
        kMax := p.burstLog2
      } otherwise {
        kMax := curBurstLimit.resized
      }
      val addrOr = curSrc | curDst
      val kBurst = UInt(chunkWidth bits)
      kBurst := 0
      for (k <- 1 to p.burstLog2) {
        when(addrOr(k - 1 downto 0) === 0 && curLength >= (BigInt(1) << k) && kMax >= k) {
          kBurst := k
        }
      }
      val kSel = UInt(chunkWidth bits)
      when(curElementMode) {
        kSel := curWidth.resized
      } otherwise {
        kSel := kBurst
      }
      val low = (curSrc | curDst | curLength)(1 downto 0)
      val misaligned = curElementMode && (
        curWidth === 3 || (curWidth === 1 && low(0)) || (curWidth === 2 && low =/= 0)
      )

      // Per-chunk state
      val chunkLog2 = Reg(UInt(chunkWidth bits)) init 0
      val chunkBytes = Reg(UInt(p.burstLog2 + 1 bits)) init 0
      val beats = Reg(UInt(beatCntWidth bits)) init 0
      val beatsLeft = Reg(UInt(beatCntWidth bits)) init 0
      val srcShift = Reg(UInt(5 bits)) init 0
      val dstShift = Reg(UInt(5 bits)) init 0
      val rdMask = Reg(Bits(4 bits)) init 0
      val wrMask = Reg(Bits(4 bits)) init 0
      val denied = RegInit(False)
      val ackSeen = RegInit(False)
      val descIdx = Reg(UInt(3 bits)) init 0

      val laneMask = Bits(4 bits)
      when(kSel === 0) {
        laneMask := B"0001"
      } elsewhen (kSel === 1) {
        laneMask := B"0011"
      } otherwise {
        laneMask := B"1111"
      }

      val fifo = StreamFifo(Bits(32 bits), p.beatMax)
      fifo.io.push.valid := False
      fifo.io.push.payload := mem.d.data |>> srcShift
      fifo.io.pop.ready := False
      fifo.io.flush := False

      mem.a.valid := False
      mem.a.opcode := Opcode.A.GET()
      mem.a.param := 0
      mem.a.size := chunkLog2.resized
      mem.a.source := 0
      mem.a.address := curSrc
      mem.a.mask := rdMask
      // Lanes outside the write mask carry zeros instead of neighbouring source bytes, so
      // registers wider than the element (e.g. a 9-bit UART data register) see clean data.
      val writeLanes = Cat(wrMask.asBools.map(lane => Mux(lane, B(0xff, 8 bits), B(0, 8 bits))))
      mem.a.data := (fifo.io.pop.payload |<< dstShift) & writeLanes
      mem.a.corrupt := False
      mem.d.ready := False

      val idleFlag = Bool()
      idleFlag := False

      val fsm = new StateMachine {
        val idle: State = new State with EntryPoint {
          whenIsActive {
            idleFlag := True
            when(eligible.orR) {
              if (p.channels > 1) {
                sel := OHToUInt(grant)
              } else {
                sel := 0
              }
              priority := grant.rotateLeft(1)
              goto(calc)
            }
          }
        }

        val calc: State = new State {
          whenIsActive {
            when(curFetch) {
              descIdx := 0
              goto(descCmd)
            } elsewhen (curLength === 0) {
              goto(complete)
            } elsewhen (misaligned) {
              goto(fault)
            } otherwise {
              chunkLog2 := kSel
              chunkBytes := (U(1, p.burstLog2 + 1 bits) |<< kSel).resized
              val nBeats = ((U(1, p.burstLog2 + 1 bits) |<< kSel) + 3) >> 2
              beats := nBeats.resized
              beatsLeft := nBeats.resized
              srcShift := (curSrc(1 downto 0) << 3).resized
              dstShift := (curDst(1 downto 0) << 3).resized
              rdMask := (laneMask << curSrc(1 downto 0)).resized
              wrMask := (laneMask << curDst(1 downto 0)).resized
              denied := False
              ackSeen := False
              goto(readCmd)
            }
          }
        }

        val readCmd: State = new State {
          whenIsActive {
            mem.a.valid := True
            when(mem.a.ready) {
              goto(readData)
            }
          }
        }

        val readData: State = new State {
          whenIsActive {
            mem.d.ready := fifo.io.push.ready
            fifo.io.push.valid := mem.d.valid
            when(mem.d.fire) {
              beatsLeft := beatsLeft - 1
              when(beatsLeft === 1) {
                beatsLeft := beats
                when(denied || mem.d.denied) {
                  goto(fault)
                } otherwise {
                  goto(writeData)
                }
              }
              when(mem.d.denied) {
                denied := True
              }
            }
          }
        }

        val writeData: State = new State {
          whenIsActive {
            mem.a.valid := fifo.io.pop.valid
            mem.a.opcode := Opcode.A.PUT_FULL_DATA()
            mem.a.address := curDst
            mem.a.mask := wrMask
            fifo.io.pop.ready := mem.a.ready
            mem.d.ready := True
            when(mem.d.valid) {
              ackSeen := True
              denied := mem.d.denied
            }
            when(mem.a.fire) {
              beatsLeft := beatsLeft - 1
              when(beatsLeft === 1) {
                goto(writeAck)
              }
            }
          }
        }

        val writeAck: State = new State {
          whenIsActive {
            mem.d.ready := True
            when(ackSeen) {
              goto(update)
            } elsewhen (mem.d.valid) {
              denied := mem.d.denied
              goto(update)
            }
          }
        }

        val update: State = new State {
          whenIsActive {
            when(denied) {
              goto(fault)
            } otherwise {
              when(curReqEnable) {
                ackSet(curReqSel) := True
              }
              onSel { c =>
                when(c.srcInc) {
                  c.src := c.src + chunkBytes
                }
                when(c.dstInc) {
                  c.dst := c.dst + chunkBytes
                }
                c.length := c.length - chunkBytes
              }
              when(curLength === chunkBytes) {
                goto(complete)
              } otherwise {
                goto(idle)
              }
            }
          }
        }

        val complete: State = new State {
          whenIsActive {
            when(!curAbort) {
              onSel { c =>
                c.port.done := c.irqDone
                when(c.linked && c.next =/= 0) {
                  c.fetch := True
                } otherwise {
                  c.busy := False
                }
              }
            }
            goto(idle)
          }
        }

        val descCmd: State = new State {
          whenIsActive {
            mem.a.valid := True
            mem.a.size := 2
            mem.a.address := curNext + (descIdx << 2)
            mem.a.mask := B"1111"
            when(mem.a.ready) {
              goto(descRsp)
            }
          }
        }

        val descRsp: State = new State {
          whenIsActive {
            mem.d.ready := True
            when(mem.d.valid) {
              when(mem.d.denied) {
                goto(fault)
              } otherwise {
                onSel { c =>
                  switch(descIdx) {
                    is(0) { c.config := mem.d.data }
                    is(1) { c.src := mem.d.data.asUInt.resized }
                    is(2) { c.dst := mem.d.data.asUInt.resized }
                    is(3) { c.length := mem.d.data.asUInt }
                    is(4) { c.next := mem.d.data.asUInt.resized }
                  }
                }
                descIdx := descIdx + 1
                when(descIdx === 4) {
                  onSel { c => c.fetch := False }
                  goto(idle)
                } otherwise {
                  goto(descCmd)
                }
              }
            }
          }
        }

        val fault: State = new State {
          whenIsActive {
            fifo.io.flush := True
            onSel { c =>
              c.busy := False
              c.error := True
              c.fetch := False
              c.port.fault := True
            }
            goto(idle)
          }
        }
      }

      for (i <- 0 until p.channels) {
        owned(i) := !idleFlag && sel === i
      }
    }
  }

  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: DmaCtrl,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.Dma, 1, 0, 0)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length, p)

    // info: [7:0]=channels [15:8]=requestLines [23:16]=log2(burstBytes)
    busCtrl.read(
      B(0, 8 bits) ## B(p.burstLog2, 8 bits) ## B(p.requestLines, 8 bits) ## B(p.channels, 8 bits),
      regs.info
    )

    val irqCtrl = new InterruptCtrl(p.irqSources)
    irqCtrl.io.masks := B((BigInt(1) << p.irqSources) - 1, p.irqSources bits)
    val clearFlow = busCtrl.createAndDriveFlow(Bits(p.irqSources bits), regs.irqPending.toInt)
    irqCtrl.io.clears := 0
    when(clearFlow.valid) {
      irqCtrl.io.clears := clearFlow.payload
    }
    busCtrl.read(irqCtrl.io.pendings.resized, regs.irqPending.toInt)
    val maskReg = Reg(Bits(p.irqSources bits)) init 0
    busCtrl.readAndWrite(maskReg, regs.irqMask.toInt)
    val irqEvents = Vec(Bool(), p.irqSources)

    val busyBits = Bits(p.channels bits)
    for (ch <- 0 until p.channels) {
      busyBits(ch) := ctrl.io.channel(ch).busy
    }
    busCtrl.read(busyBits.resized, regs.status)

    for (ch <- 0 until p.channels) {
      new Area {
        val c = ctrl.io.channel(ch)
        val controlFlow = busCtrl.createAndDriveFlow(Bits(2 bits), regs.control(ch))
        c.start := controlFlow.valid && controlFlow.payload(0)
        c.abort := controlFlow.valid && controlFlow.payload(1)
        busCtrl.read(c.requestActive ## c.error ## c.busy, regs.control(ch))

        c.configWrite << busCtrl.createAndDriveFlow(Bits(32 bits), regs.config(ch))
        busCtrl.read(c.config, regs.config(ch))
        c.srcWrite << busCtrl.createAndDriveFlow(UInt(p.addressWidth bits), regs.src(ch))
        busCtrl.read(c.src, regs.src(ch))
        c.dstWrite << busCtrl.createAndDriveFlow(UInt(p.addressWidth bits), regs.dst(ch))
        busCtrl.read(c.dst, regs.dst(ch))
        c.lengthWrite << busCtrl.createAndDriveFlow(UInt(32 bits), regs.length(ch))
        busCtrl.read(c.length, regs.length(ch))
        c.nextWrite << busCtrl.createAndDriveFlow(UInt(p.addressWidth bits), regs.next(ch))
        busCtrl.read(c.next, regs.next(ch))

        irqEvents(ch * 2) := c.done
        irqEvents(ch * 2 + 1) := c.fault
      }.setName(s"channel_$ch")
    }

    irqCtrl.io.inputs := irqEvents.asBits
    val interrupt = (irqCtrl.io.pendings & maskReg).orR
  }
}
