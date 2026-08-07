# ADR-0001: Target Minecraft 1.21.1 on NeoForge

Status: accepted (2026-08-07)

## Context

Forgeweave has no code yet. The first supported platform pins the Java version, mappings, every API surface, and which companion mods (JEI, Jade, EMI) can be integrated. `docs/releasing.md` already models `mc/<version>` maintenance branches and `mc<version>-v<version>` tags.

## Decision

- First target: **Minecraft 1.21.1** with **NeoForge 21.1**.
- **Java 21**, **official Mojang mappings** (NeoForge default).
- `master` is the active 1.21.1 line. Newer Minecraft lines follow the branch progression in [releasing.md](../releasing.md); no `mc/` branch is created until 1.21.1 enters maintenance.

## Consequences

- Largest current mod ecosystem and documentation base; JEI integration (required by M1) is stable on this line.
- Vanilla 1.21 tool-tier tags are the harvest model (see CONTEXT.md invariants).
- Chasing newer 1.21.x features requires a deliberate line migration, not an in-place upgrade.
