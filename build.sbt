val spinalVersion = "1.10.2a"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "de.aesc-silicon",
      scalaVersion := "2.12.18",
      version      := "0.1.0"
    )),
    libraryDependencies ++= Seq(
      "com.github.spinalhdl" % "spinalhdl-core_2.12" % spinalVersion,
      "com.github.spinalhdl" % "spinalhdl-lib_2.12" % spinalVersion,
      compilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.12" % spinalVersion),
      "org.scalatest" %% "scalatest" % "3.2.17",
      "org.yaml" % "snakeyaml" % "1.8"
    ),
    name := "Nafarr",
    scalaSource in Compile := baseDirectory.value / "hardware" / "scala",
    scalaSource in Test    := baseDirectory.value / "test" / "scala"
  )
  .dependsOn(spinalCrypto)

lazy val spinalCrypto = RootProject(file("../SpinalCrypto/"))

fork := true
