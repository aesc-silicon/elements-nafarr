package nafarr.networkonchip.autobahn

case class AutobahnConfig(
  busWidth: Int = 128,
  idWidth: Int = 12
) {
}

object AutobahnTransfers() extends SpinalEnums {
  val READ, WRITE,
      DUMMY2, DUMMY3, DUMMY4, DUMMY5, DUMMY6, DUMMY7, DUMMY8, DUMMY9,
      DUMMY10, DUMMY11, DUMMY12, DUMMY13, DUMMY14, DUMMY15, DUMMY16, DUMMY17,
      DUMMY18, DUMMY19, DUMMY20, DUMMY21, DUMMY22, DUMMY23, DUMMY24, DUMMY25,
      DUMMY26, DUMMY27, DUMMY28, DUMMY29, DUMMY30, DUMMY31 = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding") (
    READ -> 0,
    WRITE -> 1,
    DUMMY2 -> 2,
    DUMMY3 -> 3,
    DUMMY4 -> 4,
    DUMMY5 -> 5,
    DUMMY6 -> 6,
    DUMMY7 -> 7,
    DUMMY8 -> 8,
    DUMMY9 -> 9,
    DUMMY10 -> 10,
    DUMMY11 -> 11,
    DUMMY12 -> 12,
    DUMMY13 -> 13,
    DUMMY14 -> 14,
    DUMMY15 -> 15,
    DUMMY16 -> 16,
    DUMMY17 -> 17,
    DUMMY18 -> 18,
    DUMMY19 -> 19,
    DUMMY20 -> 20,
    DUMMY21 -> 21,
    DUMMY22 -> 22,
    DUMMY23 -> 23,
    DUMMY24 -> 24,
    DUMMY25 -> 25,
    DUMMY26 -> 26,
    DUMMY27 -> 27,
    DUMMY28 -> 28,
    DUMMY29 -> 29,
    DUMMY30 -> 30,
    DUMMY31 -> 31,
  )
}

class Autobahn(config: AutobahnConfig) extends Bundle {
  val bus = Stream(Bits(config.busWidth bits))

  def toGeneric(): AutobahnHeaderGeneric = {
    val generic = AutobahnHeaderGeneric(config)
    generic.sourceID := bus(0, config.idWidth)
    generic.destintationID := bus(config.idWidth, config.idWidth)
    generic.transfer.reqResp := bus(config.idWidth + config.idWidth, 1)
    generic.transfer.kind := bus(config.idWidth + config.idWidth + 1, 3)
    generic
  }

  def toHeader(): AutobahnHeader = {
    val header = AutobahnHeader(config)

    header.generic := this.toGeneric()
    header.custom := bus(config.idWidth + config.idWidth + 4, 100)

    header
  }

  def toHeaderRW(): AutobahnHeaderRW = {
    val header = AutobahnHeaderRW(config)
    val genericWidth = 30

    header.generic := this.toGeneric()
    header.config.read := bus(genericWidth, 1)
    header.config.write := bus(genericWidth + 1, 1)
    header.config.execute := bus(genericWidth + 2, 1)
    header.config.fec := bus(genericWidth + 3, 1)
    header.config.valid := bus(genericWidth + 4, 16)
    header.config.id := bus(genericWidth + 20, 8)
    header.config.count := bus(genericWidth + 28, 5)
    header.config.prio := bus(genericWidth + 33, 3)
    header.config.address := bus(genericWidth + 36, 64)

    header
  }
}

class AutobahnHeaderGeneric(config: AutobahnConfig) extends Bundle {
  val sourceID = Bits(config.idWidth bits)
  val destinationID = Bits(config.idWidth bits)
  val transfer = new Bundle {
    val reqResp = Bool()
    val kind = AutobahnTransfers()
  }
}

class AutobahnHeader(config: AutobahnConfig) extends Bundle {
  val generic = AutobahnHeaderGeneric(config)
  val custom = Bits(100 bits)

class AutobahnHeaderRW(config: AutobahnConfig) extends Bundle {
  val generic = AutobahnHeaderGeneric(config)
  val config = Bundle {
    val read = Bool()
    val write = Bool()
    val execute = Bool()
    val fec = Bool()
  }
  val valid = Bits(16 bits)
  val id = UInt(6 bits)
  val count = UInt(5 bits)
  val prio = UInt(3 bits)
  val address = Bits(64 bits)
}

class AutobahnData(config: AutobahnConfig) extends Bundle {
  val data = Bits(config.busWidth bits)
}
