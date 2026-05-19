---
name: create-ip
description: This skill should be used when the user asks to "create a new IP core", "add a new peripheral", "add a new system IP", "create an IP similar to X", "update an existing IP", "add a new register to an IP", or wants to add hardware to the nafarr library. Covers hardware (SpinalHDL), tests, documentation, software drivers, and IpIdentification registration.
version: 0.1.0
---

# IP Core Creation and Update Guide

This skill covers creating or updating IP cores in the nafarr library. Every IP core follows
a consistent structure across hardware, tests, documentation, and software.

## Repository Layout

```
hardware/scala/nafarr/<category>/<name>/
    <Name>.scala          # Bus wrappers (Apb3<Name>, TileLink<Name>, Wishbone<Name>) + Core
    <Name>Ctrl.scala      # Parameter, Regs, Mapper, inner Component

test/scala/nafarr/<category>/<name>/
    <Name>Test.scala      # SpinalHDL simulation tests

docs/source/hardware/<category>/
    <name>.rst            # RST documentation page
    index.rst             # Add entry here

software/include/
    <name>.h              # C register layout and driver struct

software/driver/
    <name>.c              # C driver functions
```

Categories are `peripherals/com/`, `peripherals/io/`, `system/`, etc.

## 1. IpIdentification

Every IP must have a unique ID. Never change existing entries.

Add an entry to `hardware/scala/nafarr/IpIdentification.scala`:

```scala
object Ids extends SpinalEnum {
  val ExistingId = newElement() // 0
  // ...
  val NewIpName = newElement()  // N  ← add at the end
}
```

One `val X = newElement() // N` per line. The comment is the ordinal (binarySequential).

## 2. Hardware — `<Name>Ctrl.scala`

Structure inside `object <Name>Ctrl`:

```scala
object FooCtrl {
  def apply(p: Parameter = Parameter.default()) = FooCtrl(p)

  // --- Register address map ---
  object Regs {
    def apply(base: BigInt) = new Regs(base)
  }
  class Regs(base: BigInt) {
    val reg0 = base + 0x00
    val reg1 = base + 0x04
    // ...
  }

  // --- Parameter ---
  case class Parameter(/* fields */) {
    require(/* validation */, "message")
    // SpinalError(...) for checks that must fire during elaboration
  }
  object Parameter {
    // Provide named presets for common resource sizes, e.g.:
    def small()  = Parameter(...)
    def medium() = Parameter(...)
    def large()  = Parameter(...)
  }

  // --- Core component (no bus logic) ---
  case class FooCtrl(p: Parameter) extends Component {
    val io = new Bundle { ... }
    // pure hardware
  }

  // --- Bus mapper (BusSlaveFactory frontend) ---
  case class Mapper(
      busCtrl: BusSlaveFactory,
      ctrl: /* Io or FooCtrl */,
      p: Parameter
  ) extends Area {
    val idCtrl = IpIdentification(IpIdentification.Ids.FooName, major, minor, patch)
    idCtrl.driveFrom(busCtrl)
    val regs = Regs(idCtrl.length)   // registers start after the 8-byte IP ID header

    // busCtrl.read / busCtrl.write / busCtrl.onRead / busCtrl.onWrite
  }
}
```

Key rules:
- `IpIdentification` always occupies the first 8 bytes (`idCtrl.length = 8`). Register map starts at `idCtrl.length`.
- Use `require(...)` in `Parameter` for compile-time Scala checks.
- Use `SpinalError(...)` inside the `Component` body for elaboration-time checks (e.g. cross-field validation that references `ClockDomain`).
- Release takes priority over simultaneous claim for stateful resources (see Semaphore pattern).
- Prefer `when() / elsewhen() / otherwise()` over `Mux()` for conditional signal assignment.
- Do not align assignments across multiple lines with extra whitespace. Each line uses a single space around `:=` and `=`.
- Prefer multi-lines for simple `when()` or `if()` even when there is only one statement in the block.
- Every `for` loop that generates hardware inside a `Component` or `Area` **must** wrap its body in a named `Area` so that the signals inside are visible in simulation with a meaningful prefix:
  ```scala
  for (i <- 0 until p.count) {
    val myArea = new Area {
      val myReg = Reg(UInt(8 bits)) init(0)
      // ...
    }.setName(s"block_${i}")
  }
  ```
  Without `.setName(...)`, SpinalHDL collapses the signals into the parent scope and the simulator cannot distinguish signals from different iterations.
- **Never** use an inline Scala `if` expression after a SpinalHDL `:=` assignment operator. `signal := if (cond) a else b` is illegal and causes "illegal start of simple expression". Use explicit `if/else` blocks instead:
  ```scala
  // WRONG — does not compile
  signal := if (p.locked) lockReg else False

  // CORRECT
  if (p.locked) {
    signal := lockReg
  } else {
    signal := False
  }
  ```

## 3. Hardware — `<Name>.scala`

Thin bus-wrapper file. Three variants: APB3, TileLink, Wishbone.

```scala
package nafarr.<category>.<name>

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.tilelink.{
  Bus => TileLinkBus,
  BusParameter => TileLinkParameter,
  SlaveFactory => TileLinkSlaveFactory
}
import spinal.lib.bus.wishbone._

object FooName {
  class Core[T <: spinal.core.Data with IMasterSlave](
      p: FooNameCtrl.Parameter,
      busType: HardType[T],
      factory: T => BusSlaveFactory
  ) extends Component {
    val io = new Bundle {
      val bus = slave(busType())
      // additional IO ports if needed
    }
    val busCtrl = factory(io.bus)
    val ctrl = FooNameCtrl(p)
    val mapper = FooNameCtrl.Mapper(busCtrl, ctrl, p)
  }
}

case class Apb3FooName(
    p: FooNameCtrl.Parameter,
    busConfig: Apb3Config = Apb3Config(8, 32)   // pick addressWidth to cover register map
) extends FooName.Core[Apb3](p, Apb3(busConfig), Apb3SlaveFactory(_))

case class TileLinkFooName(
    p: FooNameCtrl.Parameter,
    busConfig: TileLinkParameter = TileLinkParameter.simple(8, 32, 4, 1)
) extends FooName.Core[TileLinkBus](p, TileLinkBus(busConfig), new TileLinkSlaveFactory(_, false))

case class WishboneFooName(
    p: FooNameCtrl.Parameter,
    busConfig: WishboneConfig = WishboneConfig(8, 32)
) extends FooName.Core[Wishbone](p, Wishbone(busConfig), WishboneSlaveFactory(_))
```

**TileLinkParameter.simple(addressWidth, dataWidth, sourceWidth, sinkWidth):**
- `addressWidth`: match APB3 addressWidth (covers register map).
- `dataWidth`: always 32.
- `sourceWidth`: 4 for small IPs with few in-flight transactions; 32 for high-throughput IPs (UART).
- `sinkWidth`: 1 (minimal).

**APB3/Wishbone addressWidth guidance:**
- Small register maps (< 256 bytes): 8 bits.
- Medium (< 4 KB): 12 bits (default for most peripherals like UART, GPIO).
- Pinmux / large option arrays: 12 bits.

## 4. Tests — `<Name>Test.scala`

```scala
package nafarr.<category>.<name>

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.sim._
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

class FooNameTest extends AnyFunSuite {

  def generationShouldPass(p: FooNameCtrl.Parameter) = {
    SpinalVhdl(Apb3FooName(p))
  }

  def generationShouldFail(p: FooNameCtrl.Parameter) = {
    intercept[Throwable] { SpinalVhdl(Apb3FooName(p)) }
  }

  def init(dut: Apb3FooName) = {
    val driver = Apb3Driver(dut.io.bus, dut.clockDomain)
    dut.clockDomain.forkStimulus(10)
    driver
  }

  test("Parameter - valid") { generationShouldPass(FooNameCtrl.Parameter.default()) }
  test("Parameter - invalid <reason>") { generationShouldFail(FooNameCtrl.Parameter(...)) }

  test("Feature X") {
    SimConfig.withWave.compile(Apb3FooName(FooNameCtrl.Parameter.default())).doSim { dut =>
      val driver = init(dut)
      // use driver.read(address) / driver.write(address, value)
      // dut.clockDomain.waitSampling(N)
    }
  }
}
```

Rules:
- Use `Apb3<Name>` directly — no wrapper class needed.
- Use `intercept[Throwable]` (not `intercept[Exception]`) for `generationShouldFail`;
  SpinalHDL may throw `SpinalExit` which is not an `Exception`.
- Use `SpinalError(...)` in Component body (not just `require`) when the check must cause
  `generationShouldFail` — `require` (IllegalArgumentException) may be swallowed by
  SpinalVerilog internally but not SpinalVhdl.
- For BOOT-reset registers in Verilator simulations, add
  `SimConfig.withWave.addSimulatorFlag("--x-initial 0")`.
- Pass a name to `doSim("name")` to give each simulation directory a meaningful name instead of `Apb3Foo_x`. The name argument goes on `doSim`, not on `SimConfig`:
  ```scala
  SimConfig.withWave.compile(Apb3FooName(p))
    .doSim("foo_feature_x") { dut => ... }
  ```

## 5. Documentation — `<name>.rst`

File: `docs/source/hardware/<category>/<name>.rst`

```rst
.. _hardware-<category>-<name>:

Full Name (ACRONYM)
###################

One-paragraph description of what the IP does.

Configuration
*************

Available bus architectures:

- APB3
- TileLink
- Wishbone

By default, all buses are defined with N bit address and 32 bit data width.

Parameter
=========

.. list-table:: FooNameCtrl.Parameter
   :widths: 25 25 25 25
   :header-rows: 1

   * - Name
     - Type
     - Description
     - Default
   * - fieldName
     - Int
     - What it controls.
     - default value

Register Mapping
****************

.. |ip-identification-id-value| replace:: 0xN
.. |ip-identification-major-version| replace:: 0x1
.. |ip-identification-minor-version| replace:: 0x0
.. |ip-identification-patch-version| replace:: 0x0

.. include:: ../ipidentification.rsti

.. flat-table:: Register Name
   :widths: 10 10 15 10 10 45
   :header-rows: 1

   * - Address
     - Bit
     - Field
     - Default
     - Permission
     - Description
   * - 0x008
     - 7 - 0
     - fieldName
     - 0x0
     - RW
     - Description.
```

Title format: **"Full Name (ACRONYM)"** — spell out first, abbreviation in parentheses.
The `|ip-identification-id-value|` is the decimal ordinal of the `Ids` enum entry.

Register permission codes: `RW` (read/write), `Rx` (read-only), `xW` (write-only).
Use `:rspan:`N`` for cells that span N+1 rows in `flat-table`.

Add the new file to `docs/source/hardware/<category>/index.rst`:

```rst
.. toctree::
   :maxdepth: 1

   existing.rst
   <name>.rst    ← add here
```

## 6. Software — `<name>.h` and `<name>.c`

Header `software/include/<name>.h`:

```c
/* SPDX-FileCopyrightText: 2026 aesc silicon
 * SPDX-License-Identifier: Apache-2.0 */

#ifndef <NAME>_H
#define <NAME>_H

#include <stdint.h>

#define <NAME>_REG0_OFFSET  0x008

struct <name>_regs {
    uint32_t ip_header;   /* 0x000 */
    uint32_t ip_version;  /* 0x004 */
    uint32_t reg0;        /* 0x008 */
    /* ... */
};

struct <name>_driver {
    volatile struct <name>_regs *regs;
};

int  <name>_init(struct <name>_driver *driver, unsigned int base_address);
/* function declarations */

#endif
```

Driver `software/driver/<name>.c`:

```c
/* SPDX-FileCopyrightText: 2026 aesc silicon
 * SPDX-License-Identifier: Apache-2.0 */

#include "<name>.h"

int <name>_init(struct <name>_driver *driver, unsigned int base_address)
{
    driver->regs = (struct <name>_regs *)base_address;
    return 1;
}
```

## Checklist

When creating a new IP:

- [ ] `IpIdentification.Ids` entry added (one per line with ordinal comment)
- [ ] `<Name>Ctrl.scala`: Parameter + Regs + Component + Mapper
- [ ] `<Name>.scala`: Core[T] + Apb3, TileLink, Wishbone case classes
- [ ] `<Name>Test.scala`: local wrapper, generationShouldPass/Fail, simulation tests
- [ ] `<name>.rst`: title "Full Name (ACRONYM)", parameter table, register map table
- [ ] `index.rst` updated with new `.rst` entry
- [ ] `software/include/<name>.h`: register struct + driver struct + declarations
- [ ] `software/driver/<name>.c`: driver implementation

When updating an existing IP (new register, new parameter):

- [ ] Update `<Name>Ctrl.scala` Regs + Mapper
- [ ] Update Parameter and its validation
- [ ] Add/update test cases
- [ ] Update `.rst` register map and parameter table
- [ ] Update software header and driver if register layout changed
