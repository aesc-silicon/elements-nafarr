<!--
SPDX-FileCopyrightText: 2026 aesc silicon

SPDX-License-Identifier: CERN-OHL-W-2.0
-->

## What does this change?

<!-- One or two sentences. Link the issue it closes, if there is one. -->

## How was it tested?

<!--
Name the level of evidence — a passing simulation, a Verilog export, a synthesis
run, an emulation or real hardware are very different claims. Paste the relevant
output.

If a change is only simulated, say so. That is normal.
-->

## Checklist

- [ ] `sbt test` passes (`NAFARR_BASE` exported)
- [ ] `cd test/software && make run` passes, if a driver changed
- [ ] `sbt scalafmtCheck` passes
- [ ] `reuse lint` passes — SPDX headers on new files
- [ ] Register map changes reached the `.rst` **and** the bare-metal driver
- [ ] Every commit has `Signed-off-by:` (`git commit -s`)
- [ ] One logical change per commit
