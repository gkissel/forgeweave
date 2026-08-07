# ADR-0002: Materials are datapack data; everything else is code

Status: accepted (2026-08-07)

## Context

The roadmap (docs/SCOPE.md) includes a TAIGA-scale material expansion (M6) and datapack/addon extensibility. Migrating hardcoded materials to data later would touch world-save data, networking, and recipes. Conversely, making tools, parts, and traits fully data-driven (Tinkers' 1.16+ style) is a serialization and UI framework project that would delay the first playable slice by weeks.

## Decision

- **Material definitions live in datapack JSON**: stats, trait assignment, repair item, colors. Adding a material must not require Java code.
- **Trait behavior is Java code**; data only assigns a trait id to a material.
- Tools, parts, stations, and GUIs are **plain code**.
- Recipes, models, and lang files are produced by **standard NeoForge datagen**; generated output is committed and checked for freshness in CI.
- No addon API surface in M1 beyond "add a material via datapack".

## Consequences

- Pack authors and M6 get material extensibility from day one; material data must be synced server→client.
- New trait behaviors and new tool types require a mod update — accepted until a milestone proves otherwise.
- Revisit the code/data boundary if M6 or addon demand pushes against it; do not widen it speculatively.
