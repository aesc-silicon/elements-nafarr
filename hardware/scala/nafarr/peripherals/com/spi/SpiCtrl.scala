package nafarr.peripherals.com.spi

import spinal.core._

object SpiCtrl {
  case class InitParameter(
      cpol: Boolean = false,
      cpha: Boolean = false,
      frequency: HertzNumber = 1 Hz,
      busWidth: SpiControllerCtrl.SpiBusWidth.E = SpiControllerCtrl.SpiBusWidth.Single
  )
  object InitParameter {
    def disabled = InitParameter(false, false, 0 Hz)
    def default = InitParameter(false, false, 100 kHz)
    def fast = InitParameter(false, false, 1 MHz)
  }

  case class PermissionParameter(
      busCanWriteModeConfig: Boolean,
      busCanWriteClockDividerConfig: Boolean
  ) {}
  object PermissionParameter {
    def granted = PermissionParameter(true, true)
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
      io: Spi.Parameter,
      init: InitParameter = InitParameter.disabled,
      permission: PermissionParameter = PermissionParameter.granted,
      memory: MemoryMappedParameter = MemoryMappedParameter.default,
      clockDividerWidth: Int = 16
  ) {
    require(
      (init != null && init.frequency.toLong > 0) ||
        (permission != null && permission.busCanWriteClockDividerConfig),
      "Frequency value not set. Either configure an init or grant bus write access."
    )
    require(clockDividerWidth > 1, "Clock Divider width needs to be at least 1 bit")

    val dataWidth = 8
  }

  object Parameter {
    def lightweight(csWidth: Int = 1, busWidth: Int = 1) = Parameter(
      io = Spi.Parameter(csWidth, busWidth),
      memory = MemoryMappedParameter.lightweight
    )
    def default(csWidth: Int = 1, busWidth: Int = 1) = Parameter(
      io = Spi.Parameter(csWidth, busWidth)
    )
    def xip(csWidth: Int = 1, busWidth: Int = 4) = Parameter(
      io = Spi.Parameter(csWidth, busWidth),
      init = InitParameter.fast
    )
    def full(csWidth: Int = 1, busWidth: Int = 1) = Parameter(
      io = Spi.Parameter(csWidth, busWidth),
      init = InitParameter.fast,
      memory = MemoryMappedParameter.full
    )
  }
}
