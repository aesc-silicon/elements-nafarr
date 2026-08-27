<!--
SPDX-FileCopyrightText: 2026 aesc silicon

SPDX-License-Identifier: CERN-OHL-W-2.0
-->

# Contributing to Nafarr

Thanks for your interest. Nafarr is an open-source library of reusable IP cores
written in SpinalHDL, exportable to Verilog and VHDL, and usable in any digital
design. Contributions of every size are welcome — a corrected register offset in
the documentation is as useful as a new IP core.

## What lives here

An IP core in Nafarr is more than its RTL. A complete core has:

* **Hardware** — `hardware/scala/nafarr/**`, as a `<Name>Ctrl.scala` holding the
  parameters, register map and logic, plus a `<Name>.scala` providing the APB3,
  TileLink and Wishbone wrappers.
* **Tests** — `test/scala/nafarr/**`, run under SpinalHDL's simulator.
* **Documentation** — `docs/source/hardware/**`, with the parameter table and the
  register map.
* **A bare-metal driver** — `software/driver/<name>.c` and
  `software/include/<name>.h`, with a host-run test in `test/software/testcases/`.
* **Optionally a Renode model** — a `<Name>Ctrl.cs` beside the RTL, so firmware
  can be emulated without hardware.

You do not have to deliver all of these in one pull request, and several of them
are good contributions on their own — see "Good first contributions" below.

## Setting up

You need Java 11, sbt, and a recent Verilator. Nafarr depends on two external
projects, SpinalCrypto and VexiiRiscv, which are vendored as git submodules under
`ext/`. Clone recursively so they come along:

```bash
git clone --recurse-submodules https://github.com/aesc-silicon/elements-nafarr.git nafarr
```

If you already have a checkout, or cloned without `--recurse-submodules`, fetch
them afterwards:

```bash
git submodule update --init --recursive
```

`--recursive` is not optional: VexiiRiscv carries its own submodules, and sbt
cannot load the build without them. Both submodules are pinned to a known-good
commit, so you get the same versions CI does. To work against your own checkouts
instead, point `SPINALCRYPTO_PATH` and `VEXIIRISCV_PATH` at them.

SpinalHDL's simulator needs a newer Verilator than most distributions ship. Use
the OSS CAD Suite:

```bash
wget https://github.com/YosysHQ/oss-cad-suite-build/releases/download/2024-01-01/oss-cad-suite-linux-x64-20240101.tgz
tar -xf oss-cad-suite-linux-x64-20240101.tgz
export PATH=$PWD/oss-cad-suite/bin/:$PATH
```

Then, from the Nafarr checkout:

```bash
sbt compile
sbt test
```

The [Getting Started guide](https://aesc-silicon.github.io/elements-nafarr/getting-started/)
covers the same ground plus building the documentation.

## Before you open a pull request

Run what CI runs.

**Hardware tests** — SpinalHDL simulations:

```bash
sbt test                      # everything
sbt "testOnly *GpioTest"      # one core
```

**Software driver tests** — plain C, on the host, no simulator and no hardware:

```bash
cd test/software && make run
```

**Scala formatting**:

```bash
sbt scalafmt        # fix
sbt scalafmtCheck   # what CI runs
```

**Licensing** — enforced by `reuse lint`. Every file needs an
`SPDX-FileCopyrightText` and an `SPDX-License-Identifier` header, or an entry in
`REUSE.toml`. Two licences, and the boundary matters:

* `CERN-OHL-W-2.0` for hardware, tooling and documentation
* `Apache-2.0` for software — drivers, firmware, host tests

Copy the header style from a neighbouring file in the same directory.

**Documentation**:

```bash
virtualenv venv && source venv/bin/activate
pip install -r docs/requirements.txt
cd docs && make html
```

## Adding or changing an IP core

Every core registers an ID in `IpIdentification.Ids` and exposes an
8-byte identification header at the start of its register block, so software can
discover the core and its version at runtime. All register offsets in the
documentation and in drivers are relative to the **end** of that header.

When adding a core:

- [ ] `IpIdentification.Ids` entry, one per line with its ordinal in a comment
- [ ] `<Name>Ctrl.scala` — `Parameter`, `Regs`, the component, and the `Mapper`
- [ ] `<Name>.scala` — `Core[T]` plus the Apb3, TileLink and Wishbone case classes
- [ ] `<Name>Test.scala` — generation checks and simulation tests
- [ ] `<name>.rst` — title as "Full Name (ACRONYM)", parameter table, register map
- [ ] the relevant `index.rst` updated
- [ ] `software/include/<name>.h` and `software/driver/<name>.c`
- [ ] `test/software/testcases/test_<name>.c`

When changing an existing core, the same list applies to the parts you touched —
in particular, a register-map change must reach the `.rst` **and** the driver, or
they drift apart silently.

Self-disclosure is preferred over hardcoding: where a core can report its FIFO
depths, widths or capabilities in a read-only register, drivers should read them
rather than assume them.

## Coding style

**SpinalHDL / Scala.** `scalafmt` handles most of it, with two rules it cannot
enforce:

* Never pad `=` or `:=` with extra spaces to align them across lines. Exactly one
  space on each side.
* A Scala `if` used directly as the right-hand side of a `:=` must be wrapped in
  parentheses, or the parser rejects it:
  `d.data := (if (cond) a else b)`

**C.** Follow the style of the surrounding files. Prefer explicit offset defines
and accessors over a register struct overlay — a struct assumes a field order the
RTL is free to change, and that has already caused a driver to drift out of sync.

**Comments.** Explain why something is the way it is, not what the next line does.

## Commits and pull requests

Commit subjects use the area prefix visible in the history:

```
hardware: nafarr: peripherals: io: gpio: Add open-drain mode
software: Fix i2c register offsets
docs: source: hardware: Document the PLIC
```

One logical change per commit, with a body explaining *why*, wrapped at about 72
columns. Every commit needs a `Signed-off-by:` line, certifying the
[Developer Certificate of Origin](https://developercertificate.org/):

```bash
git commit -s
```

In the pull request, say what you tested and how. For hardware changes, name the
level of evidence — a passing simulation, a synthesis run, an emulation, or real
hardware are very different claims. If a change is only simulated, say so; that is
normal and is not a reason to hold it back.

## Good first contributions

Issues labelled `good first issue` are scoped to be finishable without a tour of
the whole codebase. Three categories need no SpinalHDL knowledge at all:

* **Renode models** — a functional model of an IP core is self-contained C#
  implementing `IDoubleWordPeripheral` and `IKnownSize`. The register map comes
  from the core's `Regs` class. Several cores still have none.
* **Bare-metal driver tests** — plain C, run on the host.
* **Documentation** — a few cores have no page, and some register tables have
  drifted from the RTL. Small, verifiable and genuinely useful.

## Reporting bugs

Use the bug report template. The details that matter most are which IP core, which
bus wrapper (APB3, TileLink or Wishbone), and whether you saw the problem in
simulation, in synthesis or on hardware — those usually have different causes.

## Questions

Open a
[discussion](https://github.com/aesc-silicon/elements-nafarr/discussions) if you
are not sure whether something is a bug, want to propose a new core, or are
looking for a place to start.
