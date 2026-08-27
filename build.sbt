// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

lazy val root = (project in file("."))
  .settings(
    name := "Nafarr",
    inThisBuild(
      List(
        organization := "com.github.spinalhdl",
        scalaVersion := "2.12.18",
        version := "2.0.0"
      )
    ),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17",
      "org.yaml" % "snakeyaml" % "1.8"
    ),
    Compile / scalaSource := baseDirectory.value / "hardware" / "scala",
    Test / scalaSource := baseDirectory.value / "test" / "scala",
    Test / parallelExecution := false,
    scalacOptions += s"-Xplugin:${(spinalHdlIdslPlugin / Compile / packageBin).value.getAbsolutePath}",
    scalacOptions += "-Xplugin-require:idsl-plugin",
    envVars += ("NAFARR_BASE" -> baseDirectory.value.getAbsolutePath)
  )
  .dependsOn(spinalCrypto, vexiiRiscv, spinalHdlIdslPlugin, spinalHdlCore, spinalHdlLib, spinalHdlSim)

val spinalCryptoPath = sys.env.getOrElse("SPINALCRYPTO_PATH", "./ext/SpinalCrypto")
val vexiiRiscvPath = sys.env.getOrElse("VEXIIRISCV_PATH", "./ext/VexiiRiscv")
val spinalHdlPath = sys.env.getOrElse("SPINALHDL_PATH", vexiiRiscvPath + "/ext/SpinalHDL")

lazy val spinalCrypto = RootProject(file(spinalCryptoPath))
lazy val vexiiRiscv = RootProject(file(vexiiRiscvPath))

lazy val spinalHdlIdslPlugin = ProjectRef(file(spinalHdlPath), "idslplugin")
lazy val spinalHdlCore = ProjectRef(file(spinalHdlPath), "core")
lazy val spinalHdlLib = ProjectRef(file(spinalHdlPath), "lib")
lazy val spinalHdlSim = ProjectRef(file(spinalHdlPath), "sim")

run / connectInput := true
fork := true
