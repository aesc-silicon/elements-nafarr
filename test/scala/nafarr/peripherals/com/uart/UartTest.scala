// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.peripherals.com.uart

import org.scalatest.funsuite.AnyFunSuite

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import nafarr.CheckTester._
import spinal.lib._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver


class UartTest extends AnyFunSuite {
  def genCore[T <: spinal.core.Data with IMasterSlave](
      parameter: UartCtrl.Parameter,
      constructor: UartCtrl.Parameter => Uart.Core[T]
  ): Uart.Core[T] = {
    val cd = ClockDomain.current.copy(frequency = FixedFrequency(100 MHz))
    val area = new ClockingArea(cd) {
      val dut = constructor(parameter)
    }
    area.dut
  }

  test("Apb3UartParameters") {
    generationShouldPass(genCore(UartCtrl.Parameter.lightweight, Apb3Uart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.default, Apb3Uart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(), Apb3Uart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(9600), Apb3Uart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(115200), Apb3Uart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter(interrupt = false), Apb3Uart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter(flowControl = false), Apb3Uart(_)))
    generationShouldPass {
      val parameter = UartCtrl.Parameter(
        init = UartCtrl.InitParameter.default(115200),
        permission = UartCtrl.PermissionParameter.restricted
      )
      genCore(parameter, Apb3Uart(_))
    }

    generationShouldFail {
      val parameter = UartCtrl.Parameter(
        init = UartCtrl.InitParameter.disabled,
        permission = UartCtrl.PermissionParameter.restricted
      )
      genCore(parameter, Apb3Uart(_))
    }
  }

  test("TileLinkUartPrameters") {
    generationShouldPass(genCore(UartCtrl.Parameter.lightweight, TileLinkUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.default, TileLinkUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(), TileLinkUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(9600), TileLinkUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(115200), TileLinkUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter(interrupt = false), TileLinkUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter(flowControl = false), TileLinkUart(_)))
    generationShouldPass {
      val parameter = UartCtrl.Parameter(
        init = UartCtrl.InitParameter.default(115200),
        permission = UartCtrl.PermissionParameter.restricted
      )
      genCore(parameter, TileLinkUart(_))
    }

    generationShouldFail {
      val parameter = UartCtrl.Parameter(
        init = UartCtrl.InitParameter.disabled,
        permission = UartCtrl.PermissionParameter.restricted
      )
      genCore(parameter, TileLinkUart(_))
    }
  }

  test("WishboneUartParameters") {
    generationShouldPass(genCore(UartCtrl.Parameter.lightweight, WishboneUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.default, WishboneUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(), WishboneUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(9600), WishboneUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter.full(115200), WishboneUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter(interrupt = false), WishboneUart(_)))
    generationShouldPass(genCore(UartCtrl.Parameter(flowControl = false), WishboneUart(_)))
    generationShouldPass {
      val parameter = UartCtrl.Parameter(
        init = UartCtrl.InitParameter.default(115200),
        permission = UartCtrl.PermissionParameter.restricted
      )
      genCore(parameter, WishboneUart(_))
    }

    generationShouldFail {
      val parameter = UartCtrl.Parameter(
        init = UartCtrl.InitParameter.disabled,
        permission = UartCtrl.PermissionParameter.restricted
      )
      genCore(parameter, WishboneUart(_))
    }
  }

  test("basic") {
    val compiled = SimConfig.withWave.compile(genCore(UartCtrl.Parameter.default, Apb3Uart(_)))

    compiled.doSim("basicRegisters") { dut =>
      dut.clockDomain.forkStimulus(10)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
      val staticOffset = dut.mapper.staticOffset
      val regOffset = dut.mapper.regOffset

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2)
      dut.clockDomain.waitFallingEdge()

      /* Check IP identification */
      assert(
        apb.read(BigInt(0)) == BigInt("00080003", 16),
        "IP Identification 0x0 should return 00080001 - API: 0, Length: 8, ID: 3"
      )
      assert(
        apb.read(BigInt(4)) == BigInt("01010000", 16),
        "IP Identification 0x4 should return 01010000 - 1.1.0"
      )

      /* Read dataWidthMin, dataWidthMax, clockDividerWidth */
      assert(
        apb.read(BigInt(staticOffset)) == BigInt("00050914", 16),
        "Unable to read 00050914 from UART data/clockDivider width declaration"
      )

      /* Read preSamplingSize, samplingSize, postSamplingSize */
      assert(
        apb.read(BigInt(staticOffset + 4)) == BigInt("00010502", 16),
        "Unable to read 00010502 from UART sampling size declaration"
      )

      /* Read rx/tx FIFO depth */
      assert(
        apb.read(BigInt(staticOffset + 8)) == BigInt("00001010", 16),
        "Unable to read 00001010 from UART FIFO depth declaration"
      )

      /* Read permissions */
      assert(
        apb.read(BigInt(staticOffset + 12)) == BigInt("00000003", 16),
        "Unable to read 00000003 from UART permission declaration"
      )

      /* Read FIFO status */
      assert(
        apb.read(BigInt(regOffset + 4)) == BigInt("00100000", 16),
        "Unable to read 00100000 from UART FIFO status"
      )
    }

    compiled.doSim("testIO") { dut =>
      dut.clockDomain.forkStimulus(10 * 1000)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10 * 1000)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5 * 1000)
        }
      }

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
      val staticOffset = dut.mapper.staticOffset
      val regOffset = dut.mapper.regOffset

      dut.io.uart.rxd #= true
      dut.io.uart.cts #= false

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2 * 1000)
      dut.clockDomain.waitFallingEdge()

      /* Init IP-Core */
      apb.write(BigInt(regOffset + 8), BigInt("0000006B", 16))
      apb.write(BigInt(regOffset + 12), BigInt("00000007", 16))

      val receive = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
      receive.join()
      assert(
        apb.read(BigInt(regOffset + 0)) == BigInt("00010047", 16),
        "Didn't received 0x47/'G'"
      )

      /* Transmit 'G' */
      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))
      val transmit = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit.join()
    }

    compiled.doSim("testIRQ-RX") { dut =>
      dut.clockDomain.forkStimulus(10 * 1000)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10 * 1000)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5 * 1000)
        }
      }

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
      val staticOffset = dut.mapper.staticOffset
      val regOffset = dut.mapper.regOffset

      dut.io.uart.rxd #= true
      dut.io.uart.cts #= false

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2 * 1000)
      dut.clockDomain.waitFallingEdge()

      /* Init IP-Core */
      apb.write(BigInt(regOffset + 8), BigInt("0000006B", 16))
      apb.write(BigInt(regOffset + 12), BigInt("00000007", 16))

      apb.write(BigInt(regOffset + 20), BigInt("00000002", 16))
      apb.write(BigInt(regOffset + 24), BigInt("00000002", 16))

      val receive = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
      receive.join()
      assert(
        apb.read(BigInt(regOffset + 0)) == BigInt("00010047", 16),
        "Doesn't received 0x47/'G'"
      )
      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000002", 16),
        "RX interrupt isn't pending"
      )
      assert(dut.io.interrupt.toBoolean == true, "UART interrupt isn't pending")
      apb.write(BigInt(regOffset + 24), BigInt("00000000", 16))
      apb.write(BigInt(regOffset + 20), BigInt("00000002", 16))
      apb.write(BigInt(regOffset + 24), BigInt("00000002", 16))
      assert(dut.io.interrupt.toBoolean == false, "UART interrupt isn't pending")
    }

    compiled.doSim("testIRQ-TX") { dut =>
      dut.clockDomain.forkStimulus(10 * 1000)
      fork {
        dut.clockDomain.fallingEdge()
        sleep(10 * 1000)
        while (true) {
          dut.clockDomain.clockToggle()
          sleep(5 * 1000)
        }
      }

      val apb = new Apb3Driver(dut.io.bus, dut.clockDomain)
      val staticOffset = dut.mapper.staticOffset
      val regOffset = dut.mapper.regOffset

      dut.io.uart.rxd #= true
      dut.io.uart.cts #= false

      /* Wait for reset and check initialized state */
      dut.clockDomain.waitSampling(2 * 1000)
      dut.clockDomain.waitFallingEdge()

      /* Init IP-Core */
      apb.write(BigInt(regOffset + 8), BigInt("0000006B", 16))
      apb.write(BigInt(regOffset + 12), BigInt("00000007", 16))

      apb.write(BigInt(regOffset + 20), BigInt("00000001", 16))
      apb.write(BigInt(regOffset + 24), BigInt("00000001", 16))

      apb.write(BigInt(regOffset + 16), BigInt("00000002", 16))

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000000", 16),
        "RX interrupt is pending"
      )
      assert(dut.io.interrupt.toBoolean == false, "UART interrupt is pending")

      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))
      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))
      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))
      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000000", 16),
        "RX interrupt is pending"
      )
      assert(dut.io.interrupt.toBoolean == false, "UART interrupt is pending")
      val transmit = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit.join()

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000000", 16),
        "RX interrupt is pending"
      )
      assert(dut.io.interrupt.toBoolean == false, "UART interrupt is pending")
      val transmit2 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit2.join()

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000001", 16),
        "RX interrupt isn't pending"
      )
      assert(dut.io.interrupt.toBoolean == true, "UART interrupt isn't pending")
      val transmit3 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit3.join()

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000001", 16),
        "RX interrupt isn't pending"
      )
      assert(dut.io.interrupt.toBoolean == true, "UART interrupt isn't pending")
      val transmit4 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit4.join()

      apb.write(BigInt(regOffset + 24), BigInt("00000000", 16))
      apb.write(BigInt(regOffset + 20), BigInt("00000001", 16))
      apb.write(BigInt(regOffset + 24), BigInt("00000001", 16))
      assert(dut.io.interrupt.toBoolean == false, "UART interrupt is pending")


      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))
      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))
      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))
      apb.write(BigInt(regOffset + 0), BigInt("00000047", 16))

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000000", 16),
        "RX interrupt is pending"
      )
      assert(dut.io.interrupt.toBoolean == false, "UART interrupt is pending")
      val transmit5 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit5.join()

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000000", 16),
        "RX interrupt is pending"
      )
      assert(dut.io.interrupt.toBoolean == false, "UART interrupt is pending")
      val transmit6 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit6.join()

      assert(
        apb.read(BigInt(regOffset + 20)) == BigInt("00000001", 16),
        "RX interrupt isn't pending"
      )
      assert(dut.io.interrupt.toBoolean == true, "UART interrupt isn't pending")
    }
  }
}
