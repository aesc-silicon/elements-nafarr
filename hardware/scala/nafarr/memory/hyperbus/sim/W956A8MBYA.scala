// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.memory.hyperbus.sim

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import nafarr.memory.hyperbus.phy.SoftDdr

object W956A8MBYA {
  def apply() = W956A8MBYA()

  case class W956A8MBYADdr() extends Component {
    val io = new Bundle {
      val clock = in(Bool)
      val ck = in(Bool)
      val ckN = in(Bool)
      val dqIn = in(Bits(8 bits))
      val dqOut = out(Bits(8 bits))
      val rwdsIn = in(Bool)
      val rwdsOut = out(Bool)
      val csN = in(Bool)
      val resetN = in(Bool)
    }

    val cd = ClockDomain(
      clock = io.clock,
      reset = io.csN,
      config = ClockDomainConfig(resetKind = ASYNC, resetActiveLevel = HIGH)
    )

    object St extends SpinalEnum {
      val CA, LATENCY, READ, WRITE = newElement()
    }

    val device = new ClockingArea(cd) {
      // Alignment constants (cycles from CS assertion), tuned against the PHY.
      val caStart = 5 // cycle the first CA byte pair arrives
      val writeLatency = 14 // CA end -> first write-data capture window

      // De-mux what the PHY drives. The SoftDdr rise path lags the fall path by
      // one cycle, so delay the fall samples once more to line up byte pairs.
      val dqInDdr = SoftDdr.Input(8)
      dqInDdr.io.d := io.dqIn
      val rwdsInDdr = SoftDdr.Input(1)
      rwdsInDdr.io.d := io.rwdsIn.asBits
      val riseByte = dqInDdr.io.rise
      val fallByte = RegNext(dqInDdr.io.fall)
      val maskRise = rwdsInDdr.io.rise(0)
      val maskFall = RegNext(rwdsInDdr.io.fall(0))

      // Drive read data + strobe back out.
      val outRise = Reg(Bits(8 bits)).init(0)
      val outFall = Reg(Bits(8 bits)).init(0)
      val strobe = Reg(Bool).init(False) // read data strobe (RWDS)
      val latencyReq = Reg(Bool).init(True) // request 2x latency during CA
      val dqOutDdr = SoftDdr.Output(8)
      dqOutDdr.io.rise := outRise
      dqOutDdr.io.fall := outFall
      // RWDS is a source-synchronous strobe: it toggles (rise=1, fall=0) both to
      // request extra latency during CA and to clock read data back out. Holding
      // fall low keeps it toggling instead of parking high during reads.
      val rwdsOutDdr = SoftDdr.Output(1)
      rwdsOutDdr.io.rise := (latencyReq | strobe).asBits
      rwdsOutDdr.io.fall := B"0"

      val state = RegInit(St.CA)
      val counter = CounterFreeRun(1024)
      val data = Mem(UInt(8 bits), 8 MB)
      val isWrite = Reg(Bool).init(False).addTag(crossClockDomain)
      val address = Reg(UInt(log2Up(8 MB) bits)).init(0)
      val latCount = Reg(UInt(12 bits)).init(0)

      def writeByte(a: UInt, masked: Bool, d: Bits): Unit = {
        when(!masked) {
          data(a) := d.asUInt
        }
      }

      switch(state) {
        is(St.CA) {
          when(counter.value === caStart) {
            isWrite := !riseByte(7)
            address(22 downto 19) := fallByte(3 downto 0).asUInt
          }
          when(counter.value === caStart + 1) {
            address(18 downto 11) := riseByte.asUInt
            address(10 downto 3) := fallByte.asUInt
          }
          when(counter.value === caStart + 2) {
            address(2 downto 0) := fallByte(2 downto 0).asUInt
            latencyReq := False
            latCount := 0
            state := St.LATENCY
          }
        }
        is(St.LATENCY) {
          latCount := latCount + 1
          val byteAddr = (address << 1).resize(widthOf(address))
          // Both directions align with the PHY's own latency; the PHY read is
          // strobe-gated, so it waits in its read state for our data + strobe.
          when(latCount === writeLatency) {
            address := byteAddr
            when(isWrite) {
              state := St.WRITE
            } otherwise {
              state := St.READ
            }
          }
        }
        is(St.READ) {
          strobe := True
          outRise := data(address).asBits
          outFall := data(address + 1).asBits
          address := address + 2
        }
        is(St.WRITE) {
          writeByte(address, maskRise, riseByte)
          writeByte(address + 1, maskFall, fallByte)
          address := address + 2
        }
      }
    }

    io.dqOut := device.dqOutDdr.io.q
    io.rwdsOut := device.rwdsOutDdr.io.q(0)
  }

  case class W956A8MBYA() extends Component {
    val io = new Bundle {
      val clock = in(Bool)
      val ck = in(Bool)
      val ckN = in(Bool)
      val dqIn = in(Bits(8 bits))
      val dqOut = out(Bits(8 bits))
      val rwdsIn = in(Bool)
      val rwdsOut = out(Bool)
      val csN = in(Bool)
      val resetN = in(Bool)
    }

    val dummyClockDomain2 = ClockDomain(
      clock = io.clock,
      reset = io.csN,
      config = ClockDomainConfig(
        resetKind = ASYNC,
        resetActiveLevel = HIGH
      )
    )

    object HyperBusState extends SpinalEnum {
      val START, LATENCY, READ, WRITE = newElement()
    }

    val device = new ClockingArea(dummyClockDomain2) {
      val state = RegInit(HyperBusState.START)
      val counter = CounterFreeRun(512)
      val rwds = Reg(Bool).init(True)
      val data = Mem(UInt(8 bits), 8 MB)
      val output = Reg(Bits(8 bits)).init(B"00000000")
      val address = Reg(UInt(log2Up(8 MB) bits)).init(0)
      val isWrite = Reg(Bool).init(False).addTag(crossClockDomain)

      switch(state) {
        is(HyperBusState.START) {
          when(counter.value === 7) {
            // CA[47]: 1 = read, 0 = write
            isWrite := !io.dqIn(7)
          }
          when(counter.value === 11) {
            // CA[39:32]: A[26:19] — only A[22:19] relevant for 8MB
            address(22 downto 19) := io.dqIn(3 downto 0).asUInt
          }
          when(counter.value === 15) {
            // CA[31:24]: A[18:11]
            address(18 downto 11) := io.dqIn.asUInt
          }
          when(counter.value === 19) {
            // CA[23:16]: A[10:3]
            address(10 downto 3) := io.dqIn.asUInt
          }
          when(counter.value === 23) {
            // CA[15:8]
          }
          when(counter.value === 27) {
            // CA[7:0]: A[2:0] in bits [2:0]
            address(2 downto 0) := io.dqIn(2 downto 0).asUInt
            rwds := False
            counter.clear()
            state := HyperBusState.LATENCY
          }
        }
        is(HyperBusState.LATENCY) {
          when(counter.value === 104) {
            counter.clear()
            val byteAddr = (address << 1).resize(widthOf(address))
            when(isWrite) {
              rwds := False
              state := HyperBusState.WRITE
              address := byteAddr
            } otherwise {
              rwds := True
              state := HyperBusState.READ
              output := data(byteAddr ^ 1).asBits
              address := byteAddr + 1
            }
          }
        }
        is(HyperBusState.READ) {
          when(counter.value === 3) {
            rwds := !rwds
            output := data(address ^ 1).asBits
            address := address + 1
            counter.clear()
          }
        }
        is(HyperBusState.WRITE) {
          when(counter.value === 3) {
            // RWDS low = write the byte (HyperBus data mask), high = masked.
            when(!io.rwdsIn) {
              data(address ^ 1) := io.dqIn.asUInt
            }
            address := address + 1
            counter.clear()
          }
        }
      }
    }

    io.rwdsOut := device.rwds
    io.dqOut := device.output
  }
}
