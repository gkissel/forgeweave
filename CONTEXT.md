# Forgeweave domain context

Forgeweave is an independent NeoForge mod inspired by the Minecraft 1.12.2 generation of Tinkers' Construct. This file defines the project's vocabulary and invariants. Scope and milestones live in [docs/SCOPE.md](docs/SCOPE.md); architectural decisions live in [docs/adr/](docs/adr/).

## Glossary

| Term | Meaning |
| --- | --- |
| **Material** | A substance (wood, stone, flint, bone, …) that parts are made from. Defined in datapack JSON: stats, traits (general plus per-part), repair item, colors. See ADR-0002. |
| **Part** | A component of a tool (pickaxe head, shovel head, axe head, tool binding, tool handle), crafted from one Material at a Part Builder. |
| **Pattern** | A physical item that selects which Part the Part Builder produces. A **blank pattern** is crafted at a vanilla crafting table and converted into part-specific patterns. |
| **Part Builder** | Block + GUI where a Pattern plus Material input produces a Part. |
| **Tool Station** | Block + GUI where Parts are assembled into a Tool and where Tools are repaired. |
| **Station** | Collective term for Part Builder and Tool Station (and future work blocks). |
| **Tool** | An item assembled from Parts. Its stats derive from its Parts' Materials. |
| **Trait** | A gameplay behavior a Material grants to Tools containing it. Trait behavior is Java code; the Material→Trait assignment is data. |
| **Modifier** | A post-assembly upgrade applied to a finished Tool (redstone, lapis, …). Deferred to M2; distinct from Trait. |
| **Broken** | Tool state at 0 durability: unusable but never destroyed; restored by repair at a Tool Station. |
| **Head material** | The Material of a Tool's head Part; the primary repair material. Tools with several repair parts (hammer, mattock, scythe, shortbow) also accept those parts' materials -- see `ToolConstants.Entry#repairSlots()`. |

## Invariants

- Tools break at 0 durability but are **never destroyed**.
- Tools are **not enchantable** at the vanilla enchanting table by default; the `allowVanillaEnchanting` config flag (default `false`) can enable it.
- Patterns are physical items; part crafting always goes through a Station.
- Harvest capability uses **vanilla 1.21 tool-tier tags** (`needs_iron_tool`, …), never a custom numeric harvest-level system.
- Material definitions are datapack JSON; adding a material must never require Java code unless it needs a new Trait behavior (ADR-0002).
- Dedicated-server multiplayer correctness is required for every shipped feature.
- Any file derived from upstream Tinkers' Construct has a row in `NOTICE.md` (ADR-0003).

## Avoided terminology

- **"Tinkers"/"TiC"** in mod id, player-facing names, or code identifiers — Forgeweave is an independent identity (see README). Upstream references belong only in attribution and provenance docs.
- **"Harvest level"** — implies the retired numeric system; say *tool tier* (vanilla tags).
- **"Upgrade"** as a synonym for Modifier — use *Modifier*; use *Trait* for material-inherent behavior. The two are different systems.
