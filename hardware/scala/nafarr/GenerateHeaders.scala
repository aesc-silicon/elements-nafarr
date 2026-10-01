// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import nafarr.system.syscon.SysconHeader

/** Writes the C headers that are generated from the Scala sources into software/include/.
  *
  * Run from the repository root, or set NAFARR_BASE: sbt "runMain nafarr.GenerateHeaders"
  */
object GenerateHeaders extends App {
  val includeDir = new File(sys.env.getOrElse("NAFARR_BASE", "."), "software/include")
  require(includeDir.isDirectory, s"${includeDir.getPath} not found")

  for ((name, content) <- Seq(SysconHeader.fileName -> SysconHeader.render())) {
    val file = new File(includeDir, name)
    Files.write(file.toPath, content.getBytes(StandardCharsets.UTF_8))
    println(s"Wrote ${file.getPath}")
  }
}
