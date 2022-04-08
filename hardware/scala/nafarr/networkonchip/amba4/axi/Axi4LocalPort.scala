package nafarr.networkonchip.amba4.axi

import scala.collection.mutable.ArrayBuffer

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.misc.SizeMapping

case class Axi4LocalPort(
    nocConfig: Axi4Config,
    coreConfig: Axi4Config,
    apb3Config: Apb3Config,
    hasMemoryExtension: Boolean = false,
    hasMemoryTranslation: Boolean = false
) extends Component {
  val io = new Bundle {
    val core = new Bundle {
      val input = slave(Axi4(coreConfig))
      val output = master(Axi4(coreConfig))
    }
    val local = new Bundle {
      val input = slave(Axi4(nocConfig))
      val output = master(Axi4(nocConfig))
    }
    val bus = new Bundle {
      val router = master(Apb3(apb3Config))
      val extension = if (hasMemoryExtension) master(Apb3(apb3Config)) else null
      val translation = if (hasMemoryTranslation) master(Apb3(apb3Config)) else null
    }
  }

  val partitionEntries = 16

  val apbMapping = ArrayBuffer[(Apb3, SizeMapping)]()

  val chipletId = new Axi4ChipletID(coreConfig, nocConfig, apb3Config)
  chipletId.io.fromCore.input <> io.core.input
  chipletId.io.fromNoc.output <> io.core.output

  val blockage = new Axi4Blockage(nocConfig)
  blockage.io.input <> chipletId.io.fromCore.output
  io.local.output <> blockage.io.output

  val partition = new Axi4MemoryPartition(nocConfig, apb3Config, partitionEntries)
  partition.io.output <> chipletId.io.fromNoc.input


  apbMapping += chipletId.io.bus -> (0x10000, 4 KiB)
  apbMapping += partition.io.bus -> (0x20000, 4 KiB)
  apbMapping += blockage.io.bus -> (0x30000, 4 KiB)
  apbMapping += io.bus.router -> (0x40000, 4 KiB)
  if (hasMemoryExtension) {
    apbMapping += io.bus.extension -> (0x50000, 4 KiB)
  }
  if (hasMemoryTranslation) {
    apbMapping += io.bus.translation -> (0x60000, 4 KiB)
  }

  val apbBus = new Area {
    val bridge = Axi4SharedToApb3Bridge(
      addressWidth = 20,
      dataWidth = 32,
      idWidth = nocConfig.idWidth
    )
    val config = bridge.axiConfig.copy(addressWidth = nocConfig.addressWidth, bUserWidth = nocConfig.bUserWidth, rUserWidth = nocConfig.rUserWidth)

    val decoder = Apb3Decoder(
      master = bridge.io.apb,
      slaves = apbMapping
    )
  }

  val apbInterconnect = new Area {

    val downsizer = Axi4Downsizer(nocConfig, apbBus.config)

    val axiCrossbar = Axi4CrossbarFactory()
    axiCrossbar.addSlave(partition.io.input, (0x00000000000000L, 8 PiB))
    axiCrossbar.addSlave(downsizer.io.input, (0x20000000000000L, 1 MiB))
    axiCrossbar.addConnections(
      io.local.input -> List(partition.io.input, downsizer.io.input)
    )
    axiCrossbar.build()

    val downsizerShared = downsizer.io.output.toShared()
    apbBus.bridge.io.axi.arw.write <> downsizerShared.arw.write
    apbBus.bridge.io.axi.arw.valid <> downsizerShared.arw.valid
    apbBus.bridge.io.axi.arw.ready <> downsizerShared.arw.ready
    apbBus.bridge.io.axi.arw.addr <> downsizerShared.arw.addr(19 downto 0)
    apbBus.bridge.io.axi.arw.id := downsizerShared.arw.id
    if (nocConfig.useLen) {
      apbBus.bridge.io.axi.arw.len <> downsizerShared.arw.len
    }
    if (nocConfig.useSize) {
      apbBus.bridge.io.axi.arw.size <> downsizerShared.arw.size
    }
    if (nocConfig.useBurst) {
      apbBus.bridge.io.axi.arw.burst <> downsizerShared.arw.burst
    }
    if (nocConfig.arwUserWidth > 0) {
      apbBus.bridge.io.axi.arw.user <> downsizerShared.arw.user
    }

    apbBus.bridge.io.axi.w <> downsizerShared.w
    apbBus.bridge.io.axi.r.valid <> downsizerShared.r.valid
    apbBus.bridge.io.axi.r.ready <> downsizerShared.r.ready
    apbBus.bridge.io.axi.r.data <> downsizerShared.r.data
    apbBus.bridge.io.axi.r.id <> downsizerShared.r.id
    apbBus.bridge.io.axi.r.resp <> downsizerShared.r.resp
    apbBus.bridge.io.axi.r.last <> downsizerShared.r.last

    apbBus.bridge.io.axi.b.valid <> downsizerShared.b.valid
    apbBus.bridge.io.axi.b.ready <> downsizerShared.b.ready
    apbBus.bridge.io.axi.b.id <> downsizerShared.b.id
    apbBus.bridge.io.axi.b.resp <> downsizerShared.b.resp

    downsizerShared.b.payload.user := B(0, nocConfig.bUserWidth bits)
    downsizerShared.r.payload.user := B(0, nocConfig.bUserWidth bits)
  }
}
