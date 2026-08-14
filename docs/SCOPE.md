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

## Milestone 3 — full tool roster, sword and combat tuning

Planned 2026-08-12. Combat-model decision in [ADR-0005](adr/0005-combat-model.md). Ranged weapons moved to M3.5 (maintainer, in-session); bolts are cut from the roadmap entirely — crossbows fire arrows.

### Acceptance test

In a fresh 1.21.1 survival world on a **dedicated server**, without cheats, a player can:

1. Craft a Tool Forge (seared bricks + any `c:storage_blocks` metal block, any-metal recipe like upstream); it does everything a Tool Station does, repairs cost 5% less material there, and it is required to assemble large tools — the Tool Station visibly rejects large-tool assembly.
2. Assemble every M3 tool from parts: broadsword, longsword, rapier, battlesign, frying pan, mattock, kama, dagger, battleaxe, scimitar, katana at the Tool Station; hammer, excavator, lumberaxe, scythe, cleaver, vein hammer, warmace (mace-alike; final name is a maintainer pick on its issue) at the Tool Forge.
3. Observe each tool's combat innate (table below): the longsword leap, rapier %-health strike, katana ramping in combat and resetting out of it, scimitar's damage-over-time, warmace smash after a fall, cleaver dropping a head, dagger backstab, hammer mining 3×3, vein hammer taking a whole ore vein, lumberaxe felling a tree — and the M1 retrofits: pickaxe pierce, shovel flatten, hatchet sunder.
4. Apply all 8 combat modifiers at a station and observe each effect: smite (vs undead), bane of arthropods (vs arthropods), fiery (ignite), necrotic (lifesteal), knockback, shulking (levitation), webbed (slow), beheading (head drop, including the actual player's head on a PvP kill).
5. Emboss a tool: one donor part + the substituted reagent set adds the donor material's traits without changing stats; a second embossment on the same tool is rejected.
6. Load a world saved on the previous alpha (fixture corpus in CI + manual load). At milestone end, the first **beta** tags — the save-compat promise activates.

All assembly, embossing, and modifier recipes are visible in JEI, including the Tool Forge as a crafting location.

### Content manifest

| Kind | Contents |
| --- | --- |
| Tools (18) | broadsword, longsword, rapier, battlesign, frying pan, mattock, kama, dagger (station) · battleaxe (deviation: upstream code exists but never shipped — maintainer decision 2026-08-12) · scimitar, katana, warmace (new modern-era shapes, ours) · hammer, excavator, lumberaxe, scythe, cleaver, vein hammer (Tool Forge tier). Dagger and vein hammer are shapes from upstream's modern branch — recorded deviation from the 1.12 generation (maintainer, 2026-08-12, addon/upstream survey in-session) |
| Parts (~11 new) | sword blade, wide/hand/cross guards, sign plate, pan, knife blade, large sword blade, tough tool rod, tough binding, large plate, hammer head, excavator head, scythe head, kama head, broad axe head — exact set derived from the clone's per-tool part lists at issue time; patterns for each |
| Blocks (1) | Tool Forge (station superset; large-tool gate; 5% repair discount — recorded deviation) |
| Modifiers (8) | smite, bane of arthropods, fiery, necrotic, knockback, shulking, webbed, beheading — clone constants |
| Embossing | 1.12 `ModExtraTrait` semantics: one per tool, consumes a donor part + reagent set, adds the donor material's traits only (stats unchanged). Reagent substitution: the clone's green/blue/magma slime crystals are unavailable until slime content ships; substituted with **slime block + magma block + gold block** (maintainer decision on issue #154, 2026-08-12). **Revert note**: when the world-content milestone adds slime crystals, the recipe reverts to parity — tracked in the deferred backlog. |
| Serialization | katana combat-ramp state (new tool component); embossment stored as a generated per-material modifier id at level 1 (`embossment.<material>`), staying inside ADR-0004's id+level rule like upstream's generated identifiers |
| UI | Tool Forge GUI (clone parity layout); JEI: embossing category, Tool Forge crafting location |
| Recipes | Tool Forge vanilla-table recipe; assembly recipes for all 16 tools; embossing; modifier application (datapack JSON per ADR-0004) |

### Combat innates

Every tool carries a unique combat/utility innate (maintainer directive 2026-08-12). Parity innates come from the clone; new shapes get proposals that are **named maintainer-decision points on their issues** (M2's #103 pattern).

Utility tools (mattock, kama, hammer, excavator, lumberaxe, vein hammer) additionally carry a **small combat rider** proposed on their issues (maintainer, 2026-08-12) — e.g. vein hammer "crushing blow" (bonus knockback vs armored targets).

| Tool | Innate | Source |
| --- | --- | --- |
| pickaxe (M1 retrofit) | pierce — a small flat amount of damage ignores armor | new (mirrors upstream 1.20 piercing) |
| shovel (M1 retrofit) | flatten — hits briefly slow the target | new |
| hatchet (M1 retrofit) | sunder — disables shields (vanilla-axe rule) + bonus damage vs blocking targets | new |
| dagger | backstab — bonus damage when striking from behind | new (shape from upstream 1.20) |
| vein hammer | vein-mines a connected ore vein; combat rider on issue | new (shape from upstream 1.20) |
| broadsword | sweeps a full-charge, grounded hit onto everything nearby (parity) plus a maintainer-decided parry window (issue #303 re-verify: upstream's 1.12 innate was never blocking, that premise was wrong) | parity + decision |
| longsword | charged leap attack | parity |
| rapier | deals a % of the target's current health as armor-bypassing damage (magnitude on issue) | maintainer redesign |
| battlesign | blocking reflects projectiles | parity |
| frying pan | heavy knockback | parity |
| battleaxe | proposal on issue | decision |
| scimitar | applies damage-over-time on hit (magnitude/duration on issue) | new |
| katana | damage builds up while in combat, resets when combat ends (serialized ramp state) | new |
| warmace | smash: bonus damage scaling with fall distance, riding vanilla 1.21 mace mechanics | new |
| cleaver | innate beheading levels | parity |
| hammer | 3×3 mining | parity |
| excavator | 3×3 digging | parity |
| lumberaxe | fells the whole tree | parity |
| scythe | 3×3×3 harvest + AoE attack | parity |
| kama | shears; right-click crop harvest | parity |
| mattock | axe+shovel dual tool; tills soil | parity |

### In scope (systems)

Combat model per ADR-0005: vanilla 1.21 attack cooldown and attribute system; clone damage/attack-speed constants ported as attribute modifiers; innates and combat modifiers hang off shared per-hit event seams (which ADR-0004's M6 extraction treats as future parameterized-library entry points). Tool Forge tier gating. Embossing. Large-tool AoE behaviors. Beheading head-drop utility covering the six vanilla head items plus player heads on PvP kills; mobs without a head item drop nothing.

### Non-goals for M3

Ranged family — shortbow, longbow, crossbow, shuriken, material arrows, javelin, throwing axe, energy-consuming ranged tool (all M3.5) · bolts (cut from the roadmap; crossbows fire arrows) · pickadze and hand axe (surveyed 2026-08-12, cut) · fishing rod, flint & brick, melting pan (gadget-shaped, revisit at M5) · staffs · tool leveling (M7) · armors (M4) · slime-crystal embossing cost (reverts at the world-content milestone) · dual-wielding · new materials.

### CI and release gates

- **GameTest coverage**: Tool Forge gates large-tool assembly (station rejects, forge accepts); 5% repair discount math; one test per combat innate (leap, %-health strike, ramp build+reset, DoT tick, smash-after-fall, head drop, backstab, vein-mine, 3×3/tree-fell/AoE, and the M1 retrofits pierce/flatten/sunder); one test per combat modifier's effect; embossing adds traits without stat change + second embossment rejected; embossment and ramp components survive the fixture-corpus decode.
- **Save-compat fixtures (in the same PR as the format, corpus is CI-gating)**: katana ramp component; embossment modifier entries; any new tool component fields.
- **Manual release checklist adds**: screenshot-harness review of the Tool Forge GUI and in-world scenes for every new tool's held/third-person render; JEI sanity; previous-release world load.
- Alpha tags during the milestone; an explicit post-alpha playtest-fix round is budgeted before the final tag. **At milestone end the first beta tags** (`mc1.21.1-v0.3.0-beta.1`) — confirmed at M3 planning (2026-08-12); from that tag the save-compat promise is binding.

## Milestone 3.2 — material roster

Planned 2026-08-13 (planning epic #221; original request #180). Pulled forward from M6 by maintainer decision 2026-08-12. Sequencing (maintainer, 2026-08-13): implementation proceeds in parallel with M3's post-alpha playtest round (#169); the `mc1.21.1-v0.3.2` line tags only after `mc1.21.1-v0.3.0-beta.1` exists. Every serialized format M3.2 adds ships its save-compat fixture in the same PR regardless of tag order.

### Acceptance test

In a fresh 1.21.1 survival world on a **dedicated server**, without cheats, a player can:

1. Craft parts at the Part Builder from each vanilla-sourced new material — cactus, obsidian, prismarine, endstone, paper, sponge, netherrack — and assemble an all-paper tool that shows **+2 modifier slots** (writable).
2. Furnace-smelt a slime block into a **green slime crystal** and a magma block into a **magma slime crystal**; craft a **blue slime crystal** (recipe per its issue); make slime, blue slime, and magma slime parts; observe a slimey tool occasionally spawning a slime and a magma slime head gaining superheat damage vs burning targets.
3. Alloy **knightslime**, **pig iron**, and **steel** in the smeltery (Forgeweave-substituted inputs, recorded per issue), cast their parts, and assemble tools; craft **firewood** and observe autosmelt replacing drops with furnace results.
4. With a test mod supplying `c:ingots/bronze`, craft bronze parts at the Part Builder; without any supplying mod, bronze/lead/silver/electrum are unobtainable (tag-gated).
5. Alloy **amethyst bronze** (copper + molten amethyst), produce **nahuatl** by pouring molten obsidian over wood (composite casting), and craft **chorus** and **ancient** parts; observe crumbling, lacerating, enderference, and vintage.
6. Emboss a tool and observe the reagent cost is now the three slime crystals + gold block (1.12 parity — the M3 substitute recipe is reverted).
7. Load a world saved on the previous release (fixture corpus in CI + manual load).

All new part, casting, alloying, and melting recipes are visible in JEI with no JEI code changes.

### Content manifest

| Kind | Contents |
| --- | --- |
| Materials (22) | **1.12 parity (18)**: cactus, obsidian, prismarine, endstone, paper, sponge, netherrack, firewood, slime, blue slime, magma slime, knightslime, pig iron, steel + tag-gated bronze, lead, silver, electrum · **modern-branch additions (4, by-name deviation)**: amethyst bronze, nahuatl, chorus, ancient. Stats/traits/colors from the pinned 1.12 clone; additions from the 1.20 clone. **Alubrass is dropped** — it is not a tool material upstream (fluid/cast-only); listing it in #221 was an error. |
| Traits (~27 new Java) | table below; all parameter-shaped per ADR-0004, combat traits attach via the #150 seams only |
| Items | green/blue/magma slime crystals (derived textures); ingot/nugget/block for knightslime, pig iron, steel; firewood block |
| Fluids | molten obsidian, molten slime (alloy input substitutions per issue), knightslime, pig iron, steel, molten amethyst, amethyst bronze |
| Recipes | crystal smelting/crafting; alloys: knightslime, pig iron, steel (substituted inputs — named decisions per issue), amethyst bronze, obsidian (water + lava → 36 mB, upstream `obsidianAlloy` behavior); nahuatl composite casting; melting/casting rows for every castable new metal; part-builder `crafting_items` for everything craftable |
| Tag-gating | bronze/lead/silver/electrum materials key `crafting_items`/`repair_item` on `c:` ingot tags; no Forgeweave ores, fluids, or casting for these four (Part Builder path only) — parity with upstream's ore-dict gating |
| Embossing | reagent recipe reverts to 1.12 parity (three slime crystals + gold block) — executes the deferred-backlog revert note |
| Serialization | alien progressive-stat state; shocking charge state — both new tool-component surfaces with fixtures in the same PR |
| Retrofits | bone gains splintering (head); flint's crude reaches upstream level 3 (+15% vs unarmored) |

### Trait table

Magnitudes are clone constants (upstream paths in NOTICE.md rows). Scope `(head)` = head-part-restricted; upstream's redundant double registrations (aquadynamic, hellish, tasty) are collapsed to the general registration.

| Trait | Material | Behavior |
| --- | --- | --- |
| prickly (head) | cactus | secondary armor-bypassing hit, mean ≈0.5 |
| spiky | cactus | thorns: reflects tool damage, halved when held, full when blocking |
| duritos | obsidian | durability event: 10% double cost, 40% zero cost |
| jagged (head) | prismarine | +ln((dmg)/72+1)×2 attack as durability drops |
| aquadynamic | prismarine | mining ≥2× base; +550% of base underwater, rain bonus |
| alien (head) | endstone | 800-point pool slowly self-assigns durability/speed/attack (serialized) |
| enderference | endstone, chorus | hitting a teleporter blocks its teleports for 5 s |
| aridiculous (head) | netherrack | mining/attack scale with biome heat, can go negative in cold/wet |
| hellish (head) | netherrack | +4 damage vs non-fire-immune targets |
| writable | paper | +1 free modifier slot per part (paper's pair grants +2 — upstream behavior, not the naming) |
| squeaky | sponge | Silk Touch always on; attack damage hard 0 |
| autosmelt | firewood | drops replaced by furnace results (shares Searing's smelt logic) |
| slimey_green | slime | 0.33% on break/kill: spawns a small slime |
| slimey_blue | blue slime | same, blue slime entity |
| superheat (head) | magma slime | +35% base damage vs burning targets |
| flammable | magma slime | ignites attackers; blocking cancels fire damage for 3 durability |
| crumbling (head) | knightslime, amethyst bronze | ×1.5 speed on blocks needing no tool |
| unnatural | knightslime | +1 speed per tool-tier level above the block's requirement |
| baconlicious (head) | pig iron | bacon: 0.5%/block, 5%/kill |
| tasty | pig iron | while held and hungry, occasionally feeds the holder for 5 durability |
| splintering (head) | bone (retrofit) | stacking +0.3/hit bleed-splinter, caps +1.8 |
| dense | bronze | up to ~42% chance to halve durability cost, scaling as the tool wears |
| poisonous | lead | Poison I 5 s on hit |
| heavy | lead | full knockback resistance while held |
| holy | silver | +5 vs undead + Weakness 2.5 s |
| shocking | electrum | 0–100 charge from movement/mining/hits; discharge: +5 lightning damage (serialized) |
| sharp (head) | steel | armor-ignoring bleed DoT (~0.33/15 ticks, 6 s) |
| stiff | steel | −1 incoming damage while blocking |
| lacerating | nahuatl | bleed DoT on hit — reuses the scimitar Lacerate seam |
| vintage | ancient | +1 modifier slot at a mobility cost (magnitude per issue) |

### In scope (systems)

Material datapack batches per ADR-0002 (a material without a new trait is one JSON + one lang line). New trait behaviors as parameter-shaped ADR-0004 library candidates; combat-touching traits consume the ADR-0005 seams only. Tag-gated material pattern (`crafting_items` on `c:` tags, unobtainable until a mod supplies the ingot). Composite casting (pour-over-item) for nahuatl. Crystal items + smelting. New alloy chains. Lang-coverage guard test (every `material/*.json` has its lang line — nothing enforces this today) and a material registry sync encoded-size unit test (SCOPE performance budget at 33 materials).

### Non-goals for M3.2

Forgeweave ores or worldgen for lead/silver/tin (tag-gated only) · slime islands, purple slime, blue slime spawns (world-content milestone; blue slime crystal gets a crafting recipe instead) · bowstring/arrow-shaft/fletching material stats (M3.5) · alubrass as a tool material · modern-branch materials needing mod-only items (slimesteel, queens slime, cinderslime, hepatizon, blazing bone, necrotic bone, venombone, seared/scorched stone, slimewood, whitestone) · Twilight Forest compat materials · mod-compat metals beyond the four tag-gated ones (M8) · bow stat axes (M3.5).

### CI and release gates

- **GameTest coverage**: one test per new trait behavior (all ~27, including retrofits); crystal furnace recipes; each new alloy ratio; nahuatl composite casting; tag-gating both ways (synthetic mod item present → craftable; absent → no part); emboss reagent revert; writable slot math on an all-paper tool; squeaky silk-touch + zero-damage; autosmelt drop replacement.
- **Unit gates**: material lang-coverage test; material registry sync encoded-size test with an explicit budget.
- **Save-compat fixtures (same PR as the format; corpus is CI-gating)**: alien progressive-stat component; shocking charge component; a fixture snapshotting a tool built from new-roster materials.
- **Manual release checklist adds**: screenshot-harness scene rendering parts/tools tinted with every new material, visually inspected; JEI sanity; spark profile; previous-release world load; dedicated-server acceptance playthrough.
- Alphas during the milestone; a maintainer playtest-fix round (regression tests per the regression rule) precedes the final tag. The line tags only after `mc1.21.1-v0.3.0-beta.1` exists; once that beta is live the save-compat promise is binding for every M3.2 format.

## Milestone ladder

Each milestone ships a playable release under the tag scheme in [releasing.md](releasing.md).

| # | Milestone | Depends on |
| --- | --- | --- |
| M1 | Tools slice (this document) | — |
| M2 | Smeltery, metal materials, modifiers. Melts/casts any mod's ores and ingots via standard `c:` tags, so modded metals (Mekanism, Create, Thermal, …) work without per-mod code | M1 |
| M3 | Full melee/harvest tool roster incl. modern-era shapes (katana, scimitar, warmace), combat tuning, Tool Forge, embossing (this document, planned 2026-08-12) | M2 |
| M3.2 | Material roster: the full always-on 1.12 material set with per-part traits, tag-gated compat metals, and four by-name modern-branch additions (this document, planned 2026-08-13; pulled forward from M6 by maintainer decision 2026-08-12) | M3 |
| M3.5 | Ranged weapons: shortbow, longbow, crossbow (fires arrows — bolts are cut), shuriken, material arrows; energy-consuming ranged tool | M3.2 |
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
| M3 | — | PlusTiC: katana as a modern-era shape (Forgeweave's design differs: damage builds while in combat, decided 2026-08-12) · TiC 1.20 branch: vein hammer + dagger shapes and pickaxe-pierce idea (feature-scope deviation authorized by maintainer 2026-08-12 — the standing "1.20 never sets feature scope" rule is explicitly overridden for these three, by name) |
| M3.2 | — | TiC 1.20 branch: **amethyst bronze, nahuatl, chorus, ancient** as material additions (feature-scope deviation authorized by maintainer 2026-08-13, by name — the standing "1.20 never sets feature scope" rule is explicitly overridden for these four) |
| M3.5 | — | PlusTiC: energy-consuming ranged tool |
| M4 | — | Construct's Armory (LGPL): two-station split, exactly four armor slots, variety carried by traits and modifiers |
| M6 | — | TAIGA + Moar Tinkers progression ladders. Sizing target: the material schema and picker UI stay usable at 50–70 materials / 30–45 traits |
| M7 | **Tinkers' Tool Leveling** (MIT, 16 classes — direct port allowed) | Ships behind a config flag; interaction with the modifier cap is decided at M7 planning, not discovered in play |
| M1/M8 | **Tinker's JEI** (MIT, 4 classes — reference for the JEI plugin) | — |

**Excluded sources**: Ceramics — MIT and derivation-eligible, but its content (clay fluid handling, cisterns) is not on Forgeweave's roadmap. Tinkers' Complement — unlicensed and not used even as a design reference; Forgeweave's smeltery design is its own.

## Testing strategy

Applies across all milestones; the M1 CI/release gates above are the first instance.

### Save compatibility

- Alpha releases (0.x before the first beta tag) may break world saves; every alpha's release notes state this. **First beta: end of M3 — confirmed at M3 planning (2026-08-12)**, once combat/roster work settles tool components. Every serialized format M3 adds ships its save-compat fixture in the same PR.
- From the first beta tag onward, saves must survive every Forgeweave upgrade within the same Minecraft line.
- Enforcement: a **fixture decode corpus** — each release adds its serialized formats (tool item components, material data) as test resources; CI must decode the entire corpus on every PR thereafter. Plus one manual load of a previous-release world in the release checklist. A golden-world CI boot is added only if a save break ever escapes the corpus.
- Where it lives: SNBT snapshots in `src/test/resources/fixtures/save_compat/`, walked by `SaveCompatCorpusTest` under `./gradlew build`. That class's javadoc is the how-to for adding a release's formats; a deliberately corrupt sample in `fixtures/corrupt/` keeps the walk honest.

### Performance budgets

- Idle stations and an *unformed* smeltery cost ~zero tick time: block entities tick only while doing work. A *formed* smeltery keeps a once-a-second heartbeat alive regardless of melt work (issue #290's dropped-item pickup, upstream parity), which is still far below upstream's own forever-ticking cadence.
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
- ~~Embossing reagent revert~~ **Executed in M3.2** (slime crystals ship there, ahead of the world-content milestone; maintainer decision 2026-08-13): `data/forgeweave/forgeweave/embossing_recipe/embossment.json` reverts to green/blue/magma slime crystals + gold block.
- Embossing's per-tool donor-part gate (#154): upstream refuses a donor part the tool itself does not use (`ModExtraTrait#canApplyCustom`). Forgeweave accepts any buildable part until the tool roster's per-tool part table exists; add the gate with that table.

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
