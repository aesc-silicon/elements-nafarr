// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.reset

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import scala.collection.mutable.Map

object ResetControllerCtrl {

  case class Parameter(domains: List[ResetParameter]) {
    for (domain <- domains)
      require(domain.delay > 0, s"Delay for reset domain ${domain.name} must at least 1 cycle!")
  }

  case class Config(parameter: Parameter) extends Bundle {
    val enable = UInt(parameter.domains.length bits)
    val trigger = UInt(parameter.domains.length bits)
    val acknowledge = Bool
  }

  abstract class ResetControllerBase(parameter: Parameter) extends Component {
    addAttribute("keep_hierarchy", "yes")
    val io = new Bundle {
      val mainReset = in(Bool())
      val mainClock = in(Bool())
      val resets = out(UInt(parameter.domains.length bits))
      val trigger = in(UInt(parameter.domains.length bits))
      val config = in(Config(parameter))
    }

    var resetDict = Map[String, (Bool, Int)]()
    var triggerDict = Map[String, Bool]()
    for ((domain, index) <- parameter.domains.zipWithIndex) {
      resetDict += domain.name -> (io.resets(index), index)
      triggerDict += domain.name -> io.trigger(index)
    }
    def getResetByName(name: String): (Bool, Int) = {
      resetDict.get(name).get
    }
    def triggerByNameWithCond(name: String, cond: Bool) {
      triggerDict.get(name).get.setWhen(cond)
    }
  }

  case class DummyResetController(parameter: Parameter) extends ResetControllerBase(parameter) {
    val resetCtrlClockDomain = ClockDomain(
      clock = io.mainClock,
      reset = io.mainReset,
      config = ClockDomainConfig(resetKind = spinal.core.SYNC, resetActiveLevel = LOW)
    )

    val internalResets = UInt(parameter.domains.length bits)
    val mainResetFanout = io.mainReset #* parameter.domains.length

    val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
      val configAcknowledge = BufferCC(io.config.acknowledge)
      val configTrigger = BufferCC(io.config.trigger)
      val configEnable = BufferCC(io.config.enable)
      val trigger = BufferCC(io.trigger)

      for (((domain), index) <- parameter.domains.zipWithIndex) {
        val resetUnbuffered = True
        // Extend external reset with one cycle.
        val locked = RegInit(False)
        val counter = Reg(UInt(log2Up(domain.delay) bits)).init(0)

        when(configAcknowledge && configTrigger(index)) {
          locked := False
        }
        when(configEnable(index) && trigger(index)) {
          locked := False
        }
        when(!locked && counter =/= U(domain.delay - 1)) {
          counter := counter + 1
          resetUnbuffered := False
        }
        when(counter === U(domain.delay - 1)) {
          counter := 0
          locked := True
        }
        internalResets(index) := RegNext(resetUnbuffered)
      }
    }

    io.resets := internalResets & mainResetFanout.asUInt
  }

  case class GeneratorResetController(parameter: Parameter) extends ResetControllerBase(parameter) {
    val resetCtrlClockDomain = ClockDomain(
      clock = io.mainClock,
      config = ClockDomainConfig(
        resetKind = BOOT
      )
    )

    val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
      for (((domain), index) <- parameter.domains.zipWithIndex) {
        val resetUnbuffered = True
        val counter = Reg(UInt(log2Up(domain.delay) bits)).init(0)
        when(counter =/= U(domain.delay - 1)) {
          counter := counter + 1
          resetUnbuffered := False
        }
        when(counter === U(domain.delay - 1) && BufferCC(io.trigger(index))) {
          counter := 0
        }
        io.resets(index) := RegNext(resetUnbuffered)
      }
    }
  }
}
