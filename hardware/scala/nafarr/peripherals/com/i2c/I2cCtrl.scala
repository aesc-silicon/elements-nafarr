package nafarr.peripherals.com.i2c

object I2cCtrl {
  case class PermissionParameter(
      busCanWriteClockDividerConfig: Boolean
  ) {
    require(busCanWriteClockDividerConfig)
  }
  object PermissionParameter {
    def full = PermissionParameter(true)
    def restricted = PermissionParameter(false)
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
      io: I2c.Parameter,
      timerWidth: Int = 16,
      timeoutWidth: Int = 16,
      samplerWidth: Int = 3,
      addressWidth: Int = 7
  ) {
    require(timerWidth > 1, "Timer width needs to be at least 1 bit")
    require(timeoutWidth > 1, "Timeout width needs to be at least 1 bit")
    require(samplerWidth > 2, "Sample window size should be at least 3")
    require(addressWidth == 7, "Address width can only be 7") // 10 bit not supported yet
  }

  object Parameter {
    def lightweight = Parameter(
      permission = PermissionParameter.full,
      memory = MemoryMappedParameter.lightweight,
      io = I2c.Parameter()
    )
    def default = Parameter(
      permission = PermissionParameter.full,
      memory = MemoryMappedParameter.default,
      io = I2c.Parameter()
    )
    def full = Parameter(
      permission = PermissionParameter.full,
      memory = MemoryMappedParameter.full,
      io = I2c.Parameter()
    )
    def unmapped = Parameter(
      permission = null,
      memory = null,
      io = I2c.Parameter()
    )
  }
}
