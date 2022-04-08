package nafarr.peripherals.com.chip2chip

import spinal.core._
import spinal.lib._
import spinal.crypto.misc.LFSR
import nafarr.peripherals.com.linecode._

object LinkLayer {

  case class LinkLayer(txFifoDepth: Int, rxFifoDepth: Int, txIoPins: Int = 16, rxIoPins: Int = 16)
      extends Component {
    val dataBlock = 8
    val encodedBlock = 10

    val io = new Bundle {
      val fromFrontend = slave(Stream(Bits(128 bits)))
      val toPhy = master(Stream(Bits(160 bits)))
      val toLinkLayer = slave(Stream(Bits(160 bits)))
      val toFrontend = master(Stream(Bits(128 bits)))
    }

    val outgoing = new Area {

      val fifo = new StreamFifo(Bits(128 bits), txFifoDepth)
      fifo.io.push.payload := io.fromFrontend.payload
      fifo.io.push.valid := io.fromFrontend.valid
      io.fromFrontend.ready := fifo.io.push.ready

      val stall = Bool()
      val stallOneTwo = Bool()
      val lfsr = Reg(Bits(32 bits)).init(10)
      fifo.io.pop.ready := !stallOneTwo
      /*
      val stageHamming = new Area {
        val output = Vec(Reg(Bits(16 bits)), txIoPins)
        val forSim = output.asBits
        for (index <- 0 until txIoPins) {
          val hammingCode = new HammingCode1611.Encoder()
          hammingCode.io.dataword := fifo.io.pop.payload.subdivideIn(8 bits)(index)
          val data = B"00" ## lfsr(0) ## hammingCode.io.codeword // fill with zeros to fit 16 bit

          when (!stallOneTwo) {
            output(index) := data
          }
        }
        val valid = RegNextWhen(fifo.io.pop.fire, !stallOneTwo).init(False)
      }
       */
      val stageScrambled = new Area {
//        when (!stallOneTwo && stageHamming.valid) {
        when(!stallOneTwo && fifo.io.pop.fire) {
          lfsr := LFSR.Fibonacci(lfsr, Seq(32, 22, 2, 1))
        }

        // val output = Vec(Reg(Bits(16 bits)), txIoPins)
        val output = Vec(Reg(Bits(8 bits)), txIoPins)
        val forSim = output.asBits
        for (index <- 0 until txIoPins) {
          when(!stallOneTwo) {
            val data = fifo.io.pop.payload.subdivideIn(8 bits)(index)
            output(index) := data ^ lfsr(index + 7 downto index)
//            output(index) := stageHamming.output(index) ^ lfsr(index + 15 downto index)
          }

        }
        // val valid = RegNextWhen(stageHamming.valid, !stallOneTwo).init(False)
        val valid = RegNextWhen(fifo.io.pop.fire, !stallOneTwo).init(False)
      }

      val stage8b10b = new Area {
        /*
        val stallFor8b10b = Reg(Bool()).init(false)

        when (!stall && stageScrambled.valid) {
          when (!stallFor8b10b) {
            stallFor8b10b := True
          } otherwise {
            stallFor8b10b := False
          }
        }
         */
        val output = Vec(Reg(Bits(10 bits)), txIoPins)
        val forSim = output.asBits
        for (index <- 0 until txIoPins) {
          val encoder8b10b = new Encoding8b10b.Encoder()
          encoder8b10b.io.stall := stall
          when(!stall && stageScrambled.valid) {
            encoder8b10b.io.data := stageScrambled.output(index)
            /*
            when (!stallFor8b10b) {
              encoder8b10b.io.data := stageScrambled.output(index)(7 downto 0)
            } otherwise {
              encoder8b10b.io.data := stageScrambled.output(index)(15 downto 8)
            }
             */
          } otherwise {
            encoder8b10b.io.data := B"00000000"
          }
          encoder8b10b.io.kWord := False

          when(!stall) {
            output(index) := encoder8b10b.io.encoded
          }
        }

        val valid1 = RegNextWhen(stageScrambled.valid, !stall).init(False)
        val valid2 = RegNextWhen(valid1, !stall).init(False)
//        val valid3 = RegNextWhen(valid2, !stall).init(False)
        val valid = RegNextWhen(valid2 /* || valid3*/, !stall).init(False)
      }

      val outputForSim = stage8b10b.output(0)
      io.toPhy.valid := stage8b10b.valid
      io.toPhy.payload := stage8b10b.output.asBits
      stall := stage8b10b.valid && !io.toPhy.ready
      stallOneTwo := stall
//      stallOneTwo := stage8b10b.valid && !io.toPhy.ready || stage8b10b.stallFor8b10b
    }

    val incoming = new Area {

      val fifo = new StreamFifo(Bits(160 bits), rxFifoDepth)
      fifo.io.push.payload := io.toLinkLayer.payload
      fifo.io.push.valid := io.toLinkLayer.valid
      io.toLinkLayer.ready := fifo.io.push.ready

      val stall = Bool()
      val lfsr = Reg(Bits(32 bits)).init(10)
      fifo.io.pop.ready := !stall

      val stage10b8b = new Area {
        val output = Vec(Reg(Bits(8 bits)), txIoPins)
        val forSim = output.asBits
        for (index <- 0 until rxIoPins) {
          val decoder10b8b = new Encoding8b10b.Decoder()
          decoder10b8b.io.stall := stall
          when(!stall && fifo.io.pop.valid) {
            decoder10b8b.io.encoded := fifo.io.pop.payload.subdivideIn(10 bits)(index)
          } otherwise {
            decoder10b8b.io.encoded := B"1001110100"
          }
          when(!stall) {
            output(index) := decoder10b8b.io.data
          }
        }

        val valid1 = RegNextWhen(fifo.io.pop.fire, !stall).init(False)
        val valid = RegNextWhen(valid1, !stall).init(False)
      }

      val stageDescrambled = new Area {
        when(!stall && stage10b8b.valid) {
          lfsr := LFSR.Fibonacci(lfsr, Seq(32, 22, 2, 1))
        }

        val output = Vec(Reg(Bits(8 bits)), txIoPins)
        val forSim = output.asBits
        for (index <- 0 until txIoPins) {
          when(!stall) {
            val data = stage10b8b.output(index)
            output(index) := data ^ lfsr(index + 7 downto index)
          }

        }
        val valid = RegNextWhen(stage10b8b.valid, !stall).init(False)
      }

      stall := stageDescrambled.valid && !io.toFrontend.ready

      io.toFrontend.valid := stageDescrambled.valid
      io.toFrontend.payload := stageDescrambled.output.asBits
    }
  }
}
