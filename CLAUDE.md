<!-- SPDX-FileCopyrightText: 2026 aesc silicon -->
<!-- SPDX-License-Identifier: CERN-OHL-W-2.0 -->

# Coding Rules

## Assignment Operator Formatting

Never pad `=` or `:=` with extra spaces to align them vertically across multiple lines.
Use exactly one space on each side. This applies to both Scala `=` and SpinalHDL `:=`.

**Wrong:**
```scala
val foo     = 1
val longVar = 2

io.interrupt   := ctrl.io.interrupt
io.error       := ctrl.io.error
```

**Correct:**
```scala
val foo = 1
val longVar = 2

io.interrupt := ctrl.io.interrupt
io.error := ctrl.io.error
```

## Creating or Updating IP Cores

When creating a new IP core, adding a peripheral, or modifying an existing IP, use the
`.claude/skills/create-ip/` skill. It covers hardware (SpinalHDL), tests, documentation,
software drivers, and IpIdentification registration.
