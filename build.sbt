val spinalVersion = "1.10.1"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.spinalhdl",
      scalaVersion := "2.11.12",
      version      := "1.0.0"
    )),
    libraryDependencies ++= Seq(
      "com.github.spinalhdl" % "spinalhdl-core_2.11" % spinalVersion,
      "com.github.spinalhdl" % "spinalhdl-lib_2.11" % spinalVersion,
      compilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.11" % spinalVersion),
      "org.scalatest" %% "scalatest" % "3.2.5",
      "org.yaml" % "snakeyaml" % "1.8"
    ),
    name := "Nafarr",
    scalaSource in Compile := baseDirectory.value / "hardware" / "scala",
    scalaSource in Test    := baseDirectory.value / "test" / "scala"
  )
  .dependsOn(spinalCrypto)

lazy val spinalCrypto = RootProject(file("../SpinalCrypto/"))

fork := true
