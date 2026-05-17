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
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

import nafarr.CheckTester._
import nafarr.IpIdentification
import nafarr.IpIdentificationTest
import nafarr.SimTest

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

  def init(dut: Uart.Core[Apb3]): (Apb3Driver, UartCtrl.Regs) = {
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
    val regs = UartCtrl.Regs(dut.mapper.idCtrl.length)

    /* Init */
    dut.io.uart.rxd #= true
    dut.io.uart.cts #= false

    /* Wait for reset and check initialized state */
    dut.clockDomain.waitSampling(2)
    dut.clockDomain.waitFallingEdge()

    return (apb, regs)
  }


  test("basic") {
    val compiled = SimConfig.withWave.compile(genCore(UartCtrl.Parameter.default, Apb3Uart(_)))

    compiled.doSim("basicRegisters") { dut =>
      val (apb, regs) = init(dut)

      /* Check IP identification */
      IpIdentificationTest.V0.checkApi(apb, IpIdentification.Ids.Uart)
      IpIdentificationTest.V0.checkVersion(apb, 1, 1, 0)

      /* Read dataWidthMin, dataWidthMax, clockDividerWidth */
      SimTest.readField(apb, regs.dataWidth, 23, 16, 5, "UART minimal data width")
      SimTest.readField(apb, regs.dataWidth, 15, 8, 9, "UART maximal data width")
      SimTest.readField(apb, regs.dataWidth, 7, 0, 20, "UART clock divider width")

      /* Read preSamplingSize, samplingSize, postSamplingSize */
      SimTest.readField(apb, regs.samplingSize, 23, 16, 1, "UART pre-sampling size")
      SimTest.readField(apb, regs.samplingSize, 15, 8, 5, "UART sampling size")
      SimTest.readField(apb, regs.samplingSize, 7, 0, 2, "UART post-sampling size")

      /* Read rx/tx FIFO depth */
      SimTest.readField(apb, regs.fifoDepth, 15, 8, 16, "UART TX FIFO depth")
      SimTest.readField(apb, regs.fifoDepth, 7, 0, 16, "UART RX FIFO depth")

      /* Read permissions */
      SimTest.readField(apb, regs.permissions, 1, 1, 1, "UART permissions - frame config")
      SimTest.readField(apb, regs.permissions, 0, 0, 1, "UART permissions - clock divider")

      /* Read FIFO status */
      SimTest.readField(apb, regs.fifoStatus, 31, 24, 0, "UART RX occupancy")
      SimTest.readField(apb, regs.fifoStatus, 23, 16, 16, "UART TX vacany")
    }

    compiled.doSim("testIO") { dut =>
      val (apb, regs) = init(dut)

      /* Init IP-Core */
      apb.write(regs.clockDivider, BigInt("0000006B", 16))
      apb.write(regs.frameConfig, BigInt("00000007", 16))

      val receive = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
      receive.join()
      SimTest.read(apb, regs.readWrite, BigInt("00010047", 16), "Didn't received 0x47/'G'")

      /* Transmit 'G' */
      apb.write(regs.readWrite, BigInt("00000047", 16))
      val transmit = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit.join()
    }

    compiled.doSim("testIRQ-TX") { dut =>
      val (apb, regs) = init(dut)

      /* Init IP-Core */
      apb.write(regs.clockDivider, BigInt("0000006B", 16))
      apb.write(regs.frameConfig, BigInt("00000007", 16))

      apb.write(regs.interruptPending, BigInt("00000001", 16))
      apb.write(regs.interruptEnable, BigInt("00000001", 16))

      apb.write(regs.transmitTrigger, BigInt("00000002", 16))

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "RX interrupt is pending")

      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "RX interrupt is pending")

      val transmit = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit.join()

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "RX interrupt is pending")

      val transmit2 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit2.join()

      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000001", 16), "RX interrupt isn't pending")

      val transmit3 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit3.join()

      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000001", 16), "RX interrupt isn't pending")

      val transmit4 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit4.join()

      apb.write(regs.interruptEnable, BigInt("00000000", 16))
      apb.write(regs.interruptPending, BigInt("00000001", 16))
      apb.write(regs.interruptEnable, BigInt("00000001", 16))
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")

      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "RX interrupt is pending")

      val transmit5 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit5.join()

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "RX interrupt is pending")

      val transmit6 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit6.join()

      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000001", 16), "RX interrupt isn't pending")
    }

    compiled.doSim("testIRQ-RX") { dut =>
      val (apb, regs) = init(dut)

      /* Init IP-Core */
      apb.write(regs.clockDivider, BigInt("0000006B", 16))
      apb.write(regs.frameConfig, BigInt("00000007", 16))

      apb.write(regs.interruptPending, BigInt("00000002", 16))
      apb.write(regs.interruptEnable, BigInt("00000002", 16))

      val receive = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
      receive.join()
      SimTest.read(apb, regs.readWrite, BigInt("00010047", 16), "Didn't received 0x47/'G'")
      SimTest.read(apb, regs.interruptPending, BigInt("00000002", 16), "RX interrupt isn't pending")
      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      apb.write(regs.interruptEnable, BigInt("00000000", 16))
      apb.write(regs.interruptPending, BigInt("00000002", 16))
      apb.write(regs.interruptEnable, BigInt("00000002", 16))
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt isn't pending")
    }

    compiled.doSim("testIRQ-TX Idle") { dut =>
      val (apb, regs) = init(dut)

      /* Init IP-Core */
      apb.write(regs.clockDivider, BigInt("0000006B", 16))
      apb.write(regs.frameConfig, BigInt("00000007", 16))

      apb.write(regs.interruptPending, BigInt("00000004", 16))
      apb.write(regs.interruptEnable, BigInt("00000004", 16))

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "TX idle interrupt is pending")

      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "TX idle interrupt is pending")

      val transmit = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit.join()

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "TX idle interrupt is pending")

      val transmit2 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit2.join()

      dut.clockDomain.waitSampling(1000)

      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000004", 16), "TX idle interrupt isn't pending")

      apb.write(regs.interruptEnable, BigInt("00000000", 16))
      apb.write(regs.interruptPending, BigInt("00000004", 16))
      apb.write(regs.interruptEnable, BigInt("00000004", 16))
      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")

      apb.write(regs.readWrite, BigInt("00000047", 16))
      apb.write(regs.readWrite, BigInt("00000047", 16))

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "TX idle interrupt is pending")

      val transmit3 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit3.join()

      SimTest.checkPins(dut.io.interrupt.toBigInt, 0, f"Interrupt is pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000000", 16), "TX idle interrupt is pending")

      val transmit4 = UartDecoder(dut.io.uart.txd, 8640, BigInt("47", 16))
      transmit4.join()

      dut.clockDomain.waitSampling(1000)

      SimTest.checkPins(dut.io.interrupt.toBigInt, 1, f"Interrupt isn't pending")
      SimTest.read(apb, regs.interruptPending, BigInt("00000004", 16), "TX idle interrupt isn't pending")
    }

    compiled.doSim("test error - frame") { dut =>
      val (apb, regs) = init(dut)

      /* Init IP-Core */
      apb.write(regs.clockDivider, BigInt("0000006B", 16))
      apb.write(regs.frameConfig, BigInt("00000107", 16))

      apb.write(regs.errorPending, BigInt("00000001", 16))
      apb.write(regs.errorEnable, BigInt("00000001", 16))

      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "Framing error detected")

      val receive = UartEncoder(dut.io.uart.rxd, 8640, BigInt("047", 16))
      receive.join()
      val receive2 = UartEncoder(dut.io.uart.rxd, 8640, BigInt("047", 16))
      receive2.join()

      SimTest.read(apb, regs.errorPending, BigInt("00000001", 16), "No framing error detected")

      apb.write(regs.errorEnable, BigInt("00000000", 16))
      apb.write(regs.errorPending, BigInt("00000001", 16))
      apb.write(regs.errorEnable, BigInt("00000001", 16))
      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "Parity error detected")

      val receive3 = UartEncoder(dut.io.uart.rxd, 8640, BigInt("047", 16))
      receive3.join()
      val receive4 = UartEncoder(dut.io.uart.rxd, 8640, BigInt("047", 16))
      receive4.join()

      SimTest.read(apb, regs.errorPending, BigInt("00000001", 16), "No parity error detected")
    }

    compiled.doSim("test error - parity") { dut =>
      val (apb, regs) = init(dut)

      /* Init IP-Core */
      apb.write(regs.clockDivider, BigInt("0000006B", 16))
      apb.write(regs.frameConfig, BigInt("00000107", 16))

      apb.write(regs.errorPending, BigInt("00000002", 16))
      apb.write(regs.errorEnable, BigInt("00000002", 16))

      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "Parity error detected")

      val receive = UartEncoder(dut.io.uart.rxd, 8640, BigInt("047", 16))
      receive.join()

      dut.clockDomain.waitSampling(100)

      SimTest.read(apb, regs.errorPending, BigInt("00000002", 16), "No parity error detected")

      apb.write(regs.errorEnable, BigInt("00000000", 16))
      apb.write(regs.errorPending, BigInt("00000002", 16))
      apb.write(regs.errorEnable, BigInt("00000002", 16))
      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "Parity error detected")

      val receive2 = UartEncoder(dut.io.uart.rxd, 8640, BigInt("047", 16))
      receive2.join()

      dut.clockDomain.waitSampling(100)

      SimTest.read(apb, regs.errorPending, BigInt("00000002", 16), "No parity error detected")
    }

    compiled.doSim("test error - RX FIFO full") { dut =>
      val (apb, regs) = init(dut)

      /* Init IP-Core */
      apb.write(regs.clockDivider, BigInt("0000006B", 16))
      apb.write(regs.frameConfig, BigInt("00000007", 16))

      apb.write(regs.errorPending, BigInt("00000004", 16))
      apb.write(regs.errorEnable, BigInt("00000004", 16))

      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "RX FIFO is full")

      for (_ <- 0 until dut.ctrl.p.memory.rxFifoDepth - 1) {
        val receive = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
        receive.join()
        SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "RX FIFO is full")
      }

      val receive2 = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
      receive2.join()
      dut.clockDomain.waitSampling(100)
      SimTest.read(apb, regs.errorPending, BigInt("00000004", 16), "RX FIFO isn't full")

      for (_ <- 0 until dut.ctrl.p.memory.rxFifoDepth) {
        SimTest.read(apb, regs.readWrite, BigInt("00010047", 16), "Didn't received 0x47/'G'")
      }

      apb.write(regs.errorEnable, BigInt("00000000", 16))
      apb.write(regs.errorPending, BigInt("00000004", 16))
      apb.write(regs.errorEnable, BigInt("00000004", 16))
      SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "RX FIFO is full")

      for (_ <- 0 until dut.ctrl.p.memory.rxFifoDepth - 1) {
        val receive3 = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
        receive3.join()
        SimTest.read(apb, regs.errorPending, BigInt("00000000", 16), "RX FIFO is full")
      }

      val receive4 = UartEncoder(dut.io.uart.rxd, 8640, BigInt("47", 16))
      receive4.join()
      dut.clockDomain.waitSampling(100)
      SimTest.read(apb, regs.errorPending, BigInt("00000004", 16), "RX FIFO isn't full")
    }

  }
}
