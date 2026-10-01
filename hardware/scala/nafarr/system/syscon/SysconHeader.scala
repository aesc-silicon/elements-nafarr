// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr.system.syscon

import spinal.core._

import nafarr.{IpIdentification, Vendor, Platform, PlatformClass, Product, Feature}

/** Renders `software/include/syscon_defs.h` from the syscon register map and the SoC ID enums.
  *
  * The header holds everything the C driver must agree on with the hardware: the register
  * block layout and the vendor, platform, platform class, product and feature ordinals. Every
  * enum also gets a SYSCON_<GROUP>_COUNT and an X(NAME, ordinal) list, SYSCON_FOR_EACH_<GROUP>.
  */
object SysconHeader {
  val fileName = "syscon_defs.h"

  /** "SpiFlash" -> "SPI_FLASH", "I2c" -> "I2C", "ElemRV" -> "ELEMRV". */
  def macroName(name: String): String =
    name.replaceAll("([a-z0-9])([A-Z][a-z])", "$1_$2").toUpperCase

  private def define(name: String, value: String): String = f"#define $name%-34s $value"

  /** Element names and ordinals of a SpinalEnum, in ordinal order.
    *
    * SpinalHDL names enum elements only during elaboration, so read them from the enum object.
    */
  def elements(enum: SpinalEnum): Seq[(String, Int)] = {
    // Only the vals of the enum object itself: inherited methods such as newElement() would add
    // elements when invoked.
    val named = enum.getClass.getDeclaredMethods.toSeq
      .filter(m => m.getParameterCount == 0)
      .filter(m => classOf[SpinalEnumElement[_]].isAssignableFrom(m.getReturnType))
      .map(m => m.invoke(enum).asInstanceOf[SpinalEnumElement[_]].position -> m.getName)
      .toMap
    enum.elements.map { e =>
      require(named.contains(e.position), s"no name for ordinal ${e.position} of $enum")
      named(e.position) -> e.position
    }
  }

  /** Ordinal defines, the element count and an X(NAME, ordinal) list for one enum. */
  private def ordinals(title: String, prefix: String, enum: SpinalEnum): Seq[String] = {
    val named = elements(enum)
    val entries = named.map { case (name, position) => s"\tX(${macroName(name)}, $position)" }
    Seq(s"/* $title */") ++
      named.map { case (name, position) =>
        define(s"SYSCON_${prefix}_${macroName(name)}", position.toString)
      } ++
      Seq(
        define(s"SYSCON_${prefix}_COUNT", named.size.toString),
        s"#define SYSCON_FOR_EACH_${prefix}(X) \\"
      ) ++ entries.init.map(_ + " \\") :+ entries.last
  }

  private def registerBlock(): Seq[String] = {
    val regs = Syscon.Regs(IpIdentification.length)
    val words = regs.featureRegCount
    val fields = Seq(
      ("ip_header", BigInt(0), "IP identification header"),
      ("ip_version", BigInt(4), "IP identification version"),
      ("identity", regs.identity, "class, product, platform, vendor"),
      ("silicon_rev", regs.siliconRev, "major [31:16], minor [15:0]"),
      ("build_date", regs.buildDate, "build UNIX timestamp (s)"),
      ("ref_clock", regs.refClock, "reference oscillator (Hz)"),
      ("feature_info", regs.featureInfo, "feature words [7:0]"),
      ("features[SYSCON_FEATURE_WORDS]", regs.features(0), "ordinal N: bit N % 32, word N / 32")
    )
    var offset = BigInt(0)
    val lines = fields.flatMap { case (name, at, comment) =>
      require(at >= offset, s"syscon register $name at 0x${at.toString(16)} overlaps")
      val gap = (at - offset) / 4
      val reserved =
        if (gap > 0) Seq(f"\tunsigned int reserved_${offset.toInt}%03x[$gap];") else Nil
      offset = at + (if (name.startsWith("features")) 4 * words else 4)
      reserved :+ f"\tunsigned int ${name + ";"}%-31s /* 0x${at.toInt}%03X: $comment */"
    }
    Seq(
      "/* Register block */",
      define("SYSCON_FEATURE_WORDS", words.toString),
      "",
      "struct syscon_regs {"
    ) ++ lines :+ "};"
  }

  def render(): String = {
    val sections = Seq(
      registerBlock(),
      ordinals("Vendor ordinals: identity [7:0]", "VENDOR", Vendor),
      ordinals("Platform ordinals: identity [15:8]", "PLATFORM", Platform),
      ordinals("Product ordinals: identity [23:16]", "PRODUCT", Product),
      ordinals(
        "Platform class ordinals: identity [31:24]",
        "PLATFORM_CLASS",
        PlatformClass
      ),
      ordinals("Feature ordinals", "FEATURE", Feature)
    )
    // The license header of the generated file, not of this one.
    // REUSE-IgnoreStart
    val header = Seq(
      "/*",
      " * SPDX-FileCopyrightText: 2026 aesc silicon",
      " *",
      " * SPDX-License-Identifier: Apache-2.0",
      " *",
      // REUSE-IgnoreEnd
      " * Generated from the Scala sources by nafarr.GenerateHeaders. Do not edit; regenerate",
      " * with: sbt \"runMain nafarr.GenerateHeaders\"",
      " */",
      "",
      "#ifndef ELEMENTS_SYSCON_DEFS_H",
      "#define ELEMENTS_SYSCON_DEFS_H",
      ""
    )
    (header ++ sections.flatMap(_ :+ "") :+ "#endif").mkString("\n") + "\n"
  }
}
