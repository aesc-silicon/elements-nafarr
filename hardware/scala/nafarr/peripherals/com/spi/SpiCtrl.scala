package nafarr.peripherals.com.spi

import spinal.core._

object SpiCtrl {
  case class InitParameter(
      cpol: Boolean = false,
      cpha: Boolean = false,
      frequency: HertzNumber = 1 Hz
  )
  object InitParameter {
    def default = InitParameter(false, false, 100 kHz)
    def fast = InitParameter(false, false, 1 MHz)
  }

  case class PermissionParameter(
      busCanWriteModeConfig: Boolean,
      busCanWriteClockDividerConfig: Boolean
  ) {
    require(busCanWriteModeConfig)
    require(busCanWriteClockDividerConfig)
  }
  object PermissionParameter {
    def full = PermissionParameter(true, true)
    def restricted = PermissionParameter(false, false)
  }

  case class MemoryMappedParameter(
      cmdFifoDepth: Int,
      rspFifoDepth: Int
  ) {
    require(cmdFifoDepth > 0 && cmdFifoDepth < 256)
    require(rspFifoDepth > 0 && rspFifoDepth < 256)
  }
  object MemoryMappedParameter {
    def lightweight = MemoryMappedParameter(4, 4)
    def default = MemoryMappedParameter(16, 16)
    def full = MemoryMappedParameter(64, 64)
  }

  case class Parameter(
      permission: PermissionParameter,
      memory: MemoryMappedParameter,
      init: InitParameter,
      io: Spi.Parameter,
      timerWidth: Int = 16,
      dataWidth: Int = 8
  ) {
    require(timerWidth > 1)
    require(dataWidth > 0)
  }

  object Parameter {
    def lightweight(csWidth: Int = 1) = Parameter(
      permission = PermissionParameter.full,
      memory = MemoryMappedParameter.lightweight,
      init = InitParameter.default,
      io = Spi.Parameter(csWidth)
    )
    def default(csWidth: Int = 1) = Parameter(
      permission = PermissionParameter.full,
      memory = MemoryMappedParameter.default,
      init = InitParameter.default,
      io = Spi.Parameter(csWidth)
    )
    def xip(csWidth: Int = 1) = Parameter(
      permission = PermissionParameter.full,
      memory = MemoryMappedParameter.default,
      init = InitParameter.fast,
      io = Spi.Parameter(csWidth)
    )
    def full(csWidth: Int = 1) = Parameter(
      permission = PermissionParameter.full,
      memory = MemoryMappedParameter.full,
      init = InitParameter.fast,
      io = Spi.Parameter(csWidth)
    )
  }
}
