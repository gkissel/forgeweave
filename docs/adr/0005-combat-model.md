# ADR-0005: Combat model — vanilla cooldown plus per-hit event seams

Status: accepted (2026-08-12)

## Context

M3 ships the full melee roster, per-tool combat innates, and eight combat modifiers. Two candidate models: port the 1.12 clone's attack utility wholesale (custom crit, cooldown, and knockback handling), or ride vanilla 1.21's attack-cooldown and attribute system. The clone itself rode the vanilla 1.9+ cooldown — its per-tool feel came from attack-speed/damage attributes plus targeted per-hit logic — and upstream's 1.20 branch kept that approach on modern APIs. A parallel custom combat system would fight vanilla mechanics and every other installed mod.

## Decision

1. **Vanilla combat is the base**: attack damage and attack speed derive from parts and are applied as standard attribute modifiers on the tool's data components. No custom cooldown, crit, or knockback pipeline.
2. **1.12 constants port as attributes**: each tool's damage/speed numbers come from the pinned clone (NOTICE.md rows per ADR-0003 where files are derived).
3. **Per-hit event seams**: a single shared pipeline (pre-hit, on-hit, post-kill hooks driven by NeoForge attack/damage/death events) is the only place combat innates and combat modifiers attach. Innates (rapier %-health strike, katana ramp, scimitar damage-over-time, cleaver beheading, …) and modifiers (smite, fiery, necrotic, …) are both consumers of the same seams — no tool class reimplements event handling.
4. **Warmace smash rides vanilla mace mechanics** (fall-distance-scaled damage) rather than a parallel implementation.
5. **ADR-0004 constraint carried forward**: seam consumers are written as candidates for the M6 parameterized behavior library (`bonus_damage_vs`, `potion_effect_on_hit`, …) — behavior classes stay parameter-shaped and avoid coupling that would block that extraction. Serialization stays strictly `modifier id + level`; embossing serializes as a generated per-material id (`embossment.<material>`) at level 1, mirroring upstream's generated identifiers.

## Consequences

- Forgeweave tools behave predictably alongside vanilla and other mods (enchant interactions, shields, mace synergies); no combat-feel divergence to maintain.
- Every combat behavior added later (M3.5 ranged, M7 leveling XP-on-hit) reuses the seams instead of adding event handlers per feature.
- Katana's in-combat ramp requires serialized state on the tool — a new component with a save-compat fixture in the same PR (corpus is CI-gating from the first beta).
- Pure 1.12 combat feel (pre-1.9 spam clicking, custom crits) is explicitly not a goal; deviations from clone feel that follow from vanilla cooldown are accepted without per-case maintainer review.
