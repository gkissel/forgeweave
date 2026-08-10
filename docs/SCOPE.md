# Forgeweave scope

Vocabulary in [CONTEXT.md](../CONTEXT.md). Platform and architecture decisions in [docs/adr/](adr/). Branch/tag/release mechanics in [releasing.md](releasing.md).

## Milestone 1 — first playable: modular tools, no smeltery

Target: Minecraft 1.21.1 / NeoForge 21.1 / Java 21 (ADR-0001).

### Acceptance test

In a fresh 1.21.1 survival world on a **dedicated server**, without cheats, a player can:

1. Craft a blank pattern at a vanilla crafting table and convert it into part patterns at a Stencil Table.
2. Craft parts from at least 3 different materials at a Part Builder.
3. Assemble a working pickaxe at a Tool Station.
4. Mine with it; durability drops; at 0 durability the tool becomes Broken, not destroyed.
5. Repair it at the Tool Station with its head material.

All part, assembly, and repair recipes are visible in JEI.

### Content manifest

| Kind | Contents |
| --- | --- |
| Tools (3) | pickaxe, shovel, hatchet |
| Materials (4) | wood, stone, flint, bone — one Trait each |
| Parts (5) | pickaxe head, shovel head, axe head, tool binding, tool handle |
| Blocks (6) | Part Builder, Tool Station, Crafting Station, Stencil Table, Pattern Chest, Part Chest |
| Items | blank pattern + 5 part patterns + per-material shards |
| UI (4) | Part Builder GUI, Tool Station GUI, Crafting Station GUI (each with an attached-chest side panel), Stencil Table GUI (pattern selection) |
| Config | `allowVanillaEnchanting` (default `false`) |
| Recipes | vanilla-table recipes for blocks/patterns; part, assembly, and repair recipes in-station |

### In scope (systems)

Stations, patterns, parts, tool assembly, material stats, material item values with shard change (1.12 mechanic: e.g. plank=1, log=4; excess returned as shards), traits, durability, broken state, repair, vanilla tool-tier tags, datapack-driven materials (ADR-0002), datagen, JEI integration (including recipe-click transfer into open stations), attached-chest side inventory in station GUIs, dedicated-server multiplayer.

### Non-goals for M1

Smeltery, melting, alloying, casting, metal materials, modifiers, sword/combat tuning, guidebook, armors, gadgets, tool leveling, Jade/WTHIT, EMI, Curios, Apotheosis integration, addon API beyond datapack materials. Deferred items enter scope only via their milestone below.

### CI and release gates

- **PR gate**: `./gradlew build` (includes unit tests for stat/durability math), headless GameTest suite (craft part → assemble → mine → repair), committed datagen output is current.
- **Release gate**: PR gate plus one human playthrough of the acceptance test on a dedicated server, checklist recorded in the release PR.
- No coverage-percentage gate.

## Milestone 2 — smeltery, metal materials, modifiers

Planned 2026-08-09. Architecture decision for modifiers in [ADR-0004](adr/0004-modifier-architecture.md).

### Acceptance test

In a fresh 1.21.1 survival world on a **dedicated server**, without cheats, a player can:

1. Craft grout (clay + sand + gravel), smelt it into seared bricks, and build a minimum 3×3×3 smeltery with a Standard Core, drain, faucet, tank, and casting table; the controller reports why an invalid structure fails to form.
2. Fuel it with lava and melt raw iron/copper (vanilla ores) at 1.5× yield; pour molten gold over a crafted part to create a reusable gold cast; cast a metal part and assemble a metal tool.
3. Mine cobalt and ardite ore in the Nether, melt both, and obtain manyullyn by in-tank alloying; melt ancient debris and alloy molten scrap + gold into netherite; alloy copper + gold into rose gold.
4. Replace the Standard Core with a Nether Core (netherite-built) and observe 2× yields.
5. Apply modifiers at the Tool Station: redstone (haste) partially fills one of exactly 3 modifier slots; a 4th distinct modifier is rejected; an extra-slot item raises the cap.
6. Melt any `c:`-tagged modded ore/ingot with no Forgeweave code changes (verified with one arbitrary test mod providing a tagged ore).

All melting, alloying, casting, and modifier recipes are visible in JEI. The advancement chain (build → melt → cast → alloy → modify) completes. Without Ponder installed, a one-time chat hint appears on first controller use; with it, smeltery-assembly and casting scenes play.

### Content manifest

| Kind | Contents |
| --- | --- |
| Materials (7) | iron, copper, cobalt, ardite, manyullyn, rose gold, netherite — per-part traits ported from the 1.12 clone (iron: magnetic; cobalt: momentum/lightweight; ardite: stonebound/petramor; manyullyn: insatiable/coldblooded; copper: established); rose gold & netherite trait assignments are a maintainer pick at issue time |
| Fluids (9) | molten iron, copper, gold, cobalt, ardite, manyullyn, rose gold, netherite, netherite scrap |
| Blocks | grout, seared brick family (clone variants), Standard Core, Nether Core, seared tank family, drain, faucet, casting table, casting basin, cobalt ore, ardite ore |
| Items | seared brick, casts (ingot, nugget, 5 part casts), raw/ingot/nugget forms for new metals, modifier reagents (silky jewel, reinforced plate, mending moss, extra-slot items per clone) |
| Modifiers (15) | haste, luck, sharpness, diamond, emerald, reinforced, mending moss, silky, soulbound, extra-slot (parity, clone constants) + Searing (magma cream, auto-smelt), Magnetic (ender pearl, drops to inventory), Aquadynamic (turtle scute, full speed underwater), Resonant (echo shard, bonus XP), Far Reach (amethyst, +1 interaction range/level ×2) — modern-vanilla additions, numbers ours, deviation recorded |
| Traits (~8 new Java) | magnetic (2 lvl), momentum, lightweight, stonebound, petramor, insatiable, coldblooded, established |
| World gen | cobalt + ardite nether ore (datapack features). Slime islands and End content: explicit non-goal until the world-content milestone is scoped at M6 planning |
| UI | smeltery controller GUI (contents/fuel, clone parity), casting flow, modifier application at Tool Station |
| Schema | material JSON gains per-part trait lists (M1's 4 materials migrated); melting recipes carry base yield + required temperature; smeltery fuels and alloy recipes are datapack JSON |
| Integration | JEI categories: melting, alloying, casting table, casting basin, modifier application; Ponder soft dependency (scenes when present, one-time chat hint when absent) |

### In scope (systems)

Smeltery multiblock ported from the 1.12 clone: rectangular interiors 1×1 to 9×9 (`MAX_SIZE = 9`), automatic in-tank alloying, temperature-gated multi-fuel system (`registerSmelteryFuel` model — fuels have temperature + burn duration, melting recipes have required temperature; lava is the only fuel registered in M2, more fuels are datapack/M6 content). Tiered smeltery cores set ore yield: Standard 1.5×, Nether 2×; melting recipes hold base amounts, the core multiplies. Ore blocks melt as their raw-drop equivalent — no separate silk-touch yield axis. Casts are gold-only and reusable (pure parity). Modifiers per ADR-0004: Java behavior, datapack application recipes, `id + level` serialization, 3 free slots.

### Non-goals for M2

End/Ancient cores, End ore, slime islands (world-content milestone, scoped at M6 planning) · sand casts (revisit on playtest feedback) · per-smeltery alloy on/off toggle (deferred backlog) · electric/tiered heater (M8) · combat modifiers (smite, bane, fiery, necrotic, knockback, shulking, webbed, beheading) and embossing (M3) · GuideME in-game guide, EMI (M8) · tool forge / large tools (M3).

### CI and release gates

- **GameTest coverage**: multiblock forms/rejects (minimum, 9×9 maximum, wall holes); melting yield per core tier; temperature gating (recipe above fuel temperature does not melt); auto-alloy ratios (manyullyn, rose gold, netherite); cast creation + reuse; each of the 15 modifiers' effect; slot cap + extra-slot; `c:` tag melting with a synthetic tagged item; fixture-corpus decode.
- **Manual release checklist adds**: smeltery build UX on a dedicated server; spark profile confirms idle smeltery ≈ zero tick; JEI sanity check; Ponder present/absent both behave; previous-release world load.
- **Save-compat fixtures**: tool components with modifier lists; smeltery block-entity NBT (tank fluids, structure bounds, fuel state); casting table/basin NBT.
- Alpha tags throughout M2; release notes carry the save-break warning. **First beta intent: end of M3** (see testing strategy).

## Milestone ladder

Each milestone ships a playable release under the tag scheme in [releasing.md](releasing.md).

| # | Milestone | Depends on |
| --- | --- | --- |
| M1 | Tools slice (this document) | — |
| M2 | Smeltery, metal materials, modifiers. Melts/casts any mod's ores and ingots via standard `c:` tags, so modded metals (Mekanism, Create, Thermal, …) work without per-mod code | M1 |
| M3 | Full tool roster incl. modern-era tools (mace-alike), sword and combat tuning | M2 |
| M4 | Armors (Construct's Armory-inspired) | M2 (reuses parts/traits/modifiers) |
| M5 | Gadgets: slingshot, slime boots | M2 |
| M6 | Material expansion at TAIGA scale; modded metals become tool materials via the datapack registry | Stable material data model (M1), metals (M2) |
| M7 | Tool leveling (derived from Tinkers' Tool Leveling) | M3 |
| M8 | Deep compat: Apotheosis, Curios, Jade/WTHIT, EMI, Mekanism, and other major mods by adoption | M4 (Curios needs armors/gadgets) |
| M9 | Original-asset rewrite (removes upstream-derived assets) | Content freeze of M1–M8 |

### Milestone sources

Per-milestone source policy, decided from the [addon ecosystem survey](research/tic2-addon-ecosystem.md). **Derive** = MIT upstream, code/assets may be ported with `NOTICE.md` rows per ADR-0003. **Inspire** = design lessons only, no code or assets copied, regardless of how good the reference is.

**Standing rule (maintainer, 2026-08-09)**: Tinkers' Construct 1.12 itself is a derivation source for **every** milestone — complete assets and code may be ported wherever the milestone's feature has a 1.12 counterpart, per CLAUDE.md's 1.12-parity default. The table below lists *additional* sources per milestone.

| Milestone | Derive from | Inspire from |
| --- | --- | --- |
| M2 | Tinkers' Construct 1.12 (smeltery, casting, metals, modifiers — full assets/code as needed) | TAIGA (alloy table), Tinkers' Addons (modifier worked examples) |
| M3 | — | PlusTiC: katana (damage scales with kills) and an energy-consuming ranged tool as the modern-era shapes |
| M4 | — | Construct's Armory (LGPL): two-station split, exactly four armor slots, variety carried by traits and modifiers |
| M6 | — | TAIGA + Moar Tinkers progression ladders. Sizing target: the material schema and picker UI stay usable at 50–70 materials / 30–45 traits |
| M7 | **Tinkers' Tool Leveling** (MIT, 16 classes — direct port allowed) | Ships behind a config flag; interaction with the modifier cap is decided at M7 planning, not discovered in play |
| M1/M8 | **Tinker's JEI** (MIT, 4 classes — reference for the JEI plugin) | — |

**Excluded sources**: Ceramics — MIT and derivation-eligible, but its content (clay fluid handling, cisterns) is not on Forgeweave's roadmap. Tinkers' Complement — unlicensed and not used even as a design reference; Forgeweave's smeltery design is its own.

## Testing strategy

Applies across all milestones; the M1 CI/release gates above are the first instance.

### Save compatibility

- Alpha releases (0.x before the first beta tag) may break world saves; every alpha's release notes state this. **First-beta intent (decided at M2 planning): end of M3**, once combat/roster work settles tool components — stated as intent, not a promise; confirmed or moved at M3 planning.
- From the first beta tag onward, saves must survive every Forgeweave upgrade within the same Minecraft line.
- Enforcement: a **fixture decode corpus** — each release adds its serialized formats (tool item components, material data) as test resources; CI must decode the entire corpus on every PR thereafter. Plus one manual load of a previous-release world in the release checklist. A golden-world CI boot is added only if a save break ever escapes the corpus.
- Where it lives: SNBT snapshots in `src/test/resources/fixtures/save_compat/`, walked by `SaveCompatCorpusTest` under `./gradlew build`. That class's javadoc is the how-to for adding a release's formats; a deliberately corrupt sample in `fixtures/corrupt/` keeps the walk honest.

### Performance budgets

- Idle stations and idle smeltery cost ~zero tick time: block entities tick only while doing work.
- The material sync packet stays trivially small even at M6 scale (hundreds of materials).
- Checked by a manual spark profile on the release-checklist dedicated server from M2 onward. No automated performance gates unless a shipped regression proves the need.

### Regression rule

Every bug-fix PR whose defect is automatable includes a regression test that fails before the fix and passes after; a PR without one must state why the defect is not automatable. Pure GUI/rendering/feel bugs are exempt and go to the manual release checklist if they recur. Enforced in PR review.

### Per-milestone gate template

Each milestone M2–M9 must define, at its planning session and before implementation starts:

1. A written acceptance test in this document (fresh world, dedicated server, no cheats), like M1's.
2. Which new mechanics get GameTest coverage.
3. New manual release-checklist lines — including a JEI-installed sanity check every release, and from M8 on, a check with each integrated compat mod present.
4. Any new save-compat fixtures the milestone's serialized formats require.

No milestone-specific CI infrastructure beyond that.

## Open questions

- Shape of Apotheosis integration: vanilla-enchanting flag interplay vs. gem sockets as Modifiers (revisit at M8, seam exists via `allowVanillaEnchanting` and the Modifier system).
- Which additional 1.12.2 addons beyond TAIGA and Tool Leveling to mine for inspiration.
- EMI support vs. JEI-only long-term (revisit at M8).
- **World-content milestone** (candidate, scoped at M6 planning): End core (~2.5×, dragon breath/skull) and Ancient core (~3×, warden kill / rare loot) smeltery tiers, a new End ore, slime islands. Explicit non-goal everywhere until then.
- **In-game guide**: JEI + advancements + Ponder scenes carry discovery through M2–M7; a full GuideME-based guide is revisited at M8.

## Deferred backlog (decided, awaiting a milestone)

- Per-smeltery GUI toggle to enable/disable auto-alloying (M2 planning, 2026-08-09).
- Sand casts (single-use) if playtests find the gold-cast gate too steep.
- Electric/tiered smeltery heating (M8, alongside Create/Mekanism compat).

## M1 issue-ready roadmap

Ordered, issue-sized. Do not file as GitHub issues until authorized.

| # | Deliverable | Depends on | Verification |
| --- | --- | --- | --- |
| 1 | NeoForge 1.21.1 Gradle scaffold, mod id `forgeweave`, accepts `-Pmod_version` per releasing.md | — | `./gradlew build` produces JAR in `build/libs/`; client launches to title screen with mod listed |
| 2 | CI workflow: build + tests on PR | 1 | PR run green on the scaffold |
| 3 | Material datapack registry: JSON schema (stats, trait id, repair item, colors), loading, server→client sync | 1 | Unit tests for parsing; material visible on dedicated server + connected client |
| 4 | Pattern items (blank + 5) and part items rendering with material property | 3 | Items in creative tab; part tooltip shows material |
| 5 | Part Builder block, GUI, part-crafting recipes | 4 | GameTest: pattern + material → correct part |
| 6 | Tool Station block, GUI, assembly | 5 | GameTest: 3 parts → pickaxe with derived stats |
| 7 | Tool behavior: stats from parts, mining via vanilla tier tags, durability, Broken state, repair | 6 | GameTests: mine drops durability; 0-durability tool unusable but persists; repair restores |
| 8 | Four traits (wood, stone, flint, bone) | 7 | One GameTest or unit test per trait |
| 9 | `allowVanillaEnchanting` config flag | 7 | Test: enchanting table rejects tool when off, accepts when on |
| 10 | Datagen for models, recipes, lang + CI check that generated output is current | 4 | CI fails when datagen output is stale |
| 11 | JEI plugin: part, assembly, and repair recipe categories | 5, 6 | Manual: all recipes visible in JEI |
| 12 | GameTest suite wired into CI (`runGameTestServer`) | 7 | CI runs the suite headless |
| 13 | `NOTICE.md` provenance table; first rows added with first derived assets | — | Review checklist item; every derived file has a row (ADR-0003) |
| 14 | Release dry run: `mc1.21.1-v0.1.0-alpha.1` tag through release.yml to GitHub Releases (Modrinth/CurseForge IDs intentionally absent) | 1–13 | Workflow publishes GitHub prerelease with the built JAR |
