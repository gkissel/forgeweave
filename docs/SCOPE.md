# Forgeweave scope

Vocabulary in [CONTEXT.md](../CONTEXT.md). Platform and architecture decisions in [docs/adr/](adr/). Branch/tag/release mechanics in [releasing.md](releasing.md).

Each milestone section below records what was decided at the time it was planned. Where a later milestone section or the shipped code disagrees with an earlier one, the later section or the code wins — earlier text stays as the historical record, marked resolved rather than deleted (issue #1068).

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
4. Pour blazing blood over the Standard Core to make it a Nether Core (maintainer decision 2026-09-06; it was netherite-built until then) and observe 2× yields.
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
| World gen | cobalt + ardite nether ore (datapack features). ~~Slime islands~~ and End content: explicit non-goal until the world-content milestone is scoped at M6 planning. **Shipped**: slime islands and magma slime islands generate (`worldgen/SlimeIslandStructure`, `worldgen/MagmaSlimeIslandStructure`; issues #629/#632 and #450/#637). End content remains a non-goal |
| UI | smeltery controller GUI (contents/fuel, clone parity), casting flow, modifier application at Tool Station |
| Schema | material JSON gains per-part trait lists (M1's 4 materials migrated); melting recipes carry base yield + required temperature; smeltery fuels and alloy recipes are datapack JSON |
| Integration | JEI categories: melting, alloying, casting table, casting basin, modifier application; Ponder soft dependency (scenes when present, one-time chat hint when absent) |

### In scope (systems)

Smeltery multiblock ported from the 1.12 clone: rectangular interiors 1×1 to 9×9 (`MAX_SIZE = 9`), automatic in-tank alloying, temperature-gated multi-fuel system (`registerSmelteryFuel` model — fuels have temperature + burn duration, melting recipes have required temperature; lava is the only fuel registered in M2, more fuels are datapack/M6 content). Tiered smeltery cores set ore yield: Standard 1.5×, Nether 2×; melting recipes hold base amounts, the core multiplies. Ore blocks melt as their raw-drop equivalent — no separate silk-touch yield axis. Casts are gold-only and reusable (pure parity). Modifiers per ADR-0004: Java behavior, datapack application recipes, `id + level` serialization, 3 free slots.

**Recorded deviation (maintainer directive, issue #932):** the smeltery screen and JEI display a temperature as the same effective number the recipe/fuel datapack JSON uses, not upstream 1.12's celsius conversion (`Util#temperatureString` subtracting the 300-unit ambient baseline for display only). The `temperatureCelsius` client option is removed; there is one scale everywhere.

### Non-goals for M2

End/Ancient cores, End ore, ~~slime islands~~ (world-content milestone, scoped at M6 planning; **shipped**, see the World gen row above) · sand casts (revisit on playtest feedback) · per-smeltery alloy on/off toggle (deferred backlog) · electric/tiered heater (M8) · ~~combat modifiers (smite, bane, fiery, necrotic, knockback, shulking, webbed, beheading) and embossing (M3)~~ **shipped in M3**: combat modifiers batch 1 — smite, bane, fiery, necrotic (issue #162, PR #178), batch 2 — knockback, shulking, webbed (issue #163, PR #177), beheading (issue #158, PR #197), embossing (issue #154, PR #175) · GuideME in-game guide, EMI (M8) · tool forge / large tools (M3).

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
4. With a test mod supplying `c:ingots/bronze`, craft bronze parts at the Part Builder; without any supplying mod, bronze/lead/silver/electrum are unobtainable (tag-gated). **Superseded by M6 (issue #826, maintainer-accepted reversal):** these four now carry `neoforge:conditions` existence gates keyed on a real provider's item id (Mekanism/Immersive Engineering), so without a supplying mod the material does not exist at all — absent from the registry, the creative tab, JEI, and the guide book, not merely uncraftable.
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
| Tag-gating | bronze/lead/silver/electrum materials key `crafting_items`/`repair_item` on `c:` ingot tags; no Forgeweave ores, fluids, or casting for these four (Part Builder path only) — parity with upstream's ore-dict gating. **Superseded by M6 (issue #826):** the four also carry a `neoforge:conditions` existence gate now, so a missing supplying mod removes the material from the registry entirely rather than leaving it registered-but-uncraftable — see `Material`'s class javadoc for the convention. |
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

Forgeweave ores or worldgen for lead/silver/tin (tag-gated only) · ~~slime islands~~, purple slime and blue slime mob spawns (world-content milestone; blue slime crystal gets a crafting recipe instead). **Slime islands shipped** (`worldgen/SlimeIslandStructure`, issues #629/#632 and #450/#637); purple/blue slime mob spawns remain a non-goal · bowstring/arrow-shaft/fletching material stats (M3.5) · alubrass as a tool material · modern-branch materials needing mod-only items — slimesteel, cinderslime, blazing bone, venombone, scorched stone, whitestone still hold. ~~queens slime, hepatizon, necrotic bone, seared stone, slimewood~~ **shipped at M6** (issue #843, closes #180; see § Milestone sources' M6 row) · ~~Twilight Forest compat materials~~ **shipped in M8**: ironwood, steeleaf, knightmetal, fiery and four more as Track A presets (issue #1059, PR #1062, D-M8-25) · mod-compat metals beyond the four tag-gated ones (M8) · bow stat axes (M3.5).

### CI and release gates

- **GameTest coverage**: one test per new trait behavior (all ~27, including retrofits); crystal furnace recipes; each new alloy ratio; nahuatl composite casting; tag-gating both ways (synthetic mod item present → craftable; absent → no part); emboss reagent revert; writable slot math on an all-paper tool; squeaky silk-touch + zero-damage; autosmelt drop replacement.
- **Unit gates**: material lang-coverage test; material registry sync encoded-size test with an explicit budget.
- **Save-compat fixtures (same PR as the format; corpus is CI-gating)**: alien progressive-stat component; shocking charge component; a fixture snapshotting a tool built from new-roster materials.
- **Manual release checklist adds**: screenshot-harness scene rendering parts/tools tinted with every new material, visually inspected; JEI sanity; spark profile; previous-release world load; dedicated-server acceptance playthrough.
- Alphas during the milestone; a maintainer playtest-fix round (regression tests per the regression rule) precedes the final tag. The line tags only after `mc1.21.1-v0.3.0-beta.1` exists; once that beta is live the save-compat promise is binding for every M3.2 format.

## Milestone 3.5 — ranged weapons (bows)

Planned 2026-08-15 (grilling session; maintainer decisions recorded inline). Ships **after** `mc1.21.1-v0.3.0-beta.1` (#170), so it is the first milestone built entirely under the save-compat promise.

### Acceptance test

Fresh world, dedicated server, no cheats. Build a Tool Station and Part Builder; craft two wood **bow limbs** (3 ingots' worth of material each) and a **bow string** from string; assemble a **shortbow**. Draw and release: a vanilla arrow flies, damage and flight match the limb material's BOW stats. Build a Tool Forge; assemble a **longbow** (2 limbs + large plate + string) and a **crossbow** (tough rod + limb + tough binding + string). Load the crossbow (right-click, wait draw time), swap hotbar slots, come back, fire — it fires. Apply Haste (redstone) to the shortbow: draw is faster. Attempt Luck (lapis) on any bow: refused. Apply Fiery to the longbow: the arrow ignites its target. Save, restart the server, load: crossbow is still loaded, all bows keep their parts and modifiers.

### Content manifest

| Item | Parts (1.12 `PartMaterialType` order) | Station |
| --- | --- | --- |
| Shortbow | bow limb, bow limb, bow string | Tool Station |
| Longbow | bow limb, bow limb, large plate (extra), bow string | Tool Forge |
| Crossbow | tough tool rod (crossbow body), bow limb, tough binding (extra), bow string | Tool Forge |
| Bow limb (part) | cost 3 ingots (`TinkerTools.java:210`) | Part Builder + gold/clay cast |
| Bow string (part) | cost 1 ingot (`:211`); materials: string, vine (slime vines wait for world content) | Part Builder |

New material stat types (values ported from `TinkerMaterials.java` at the pinned commit): **BOW** (drawspeed, range, bonusDamage) on every 1.12 material that carries `BowMaterialStats`; the four M3.2 modern-branch materials (amethyst bronze, nahuatl, chorus, ancient) get analogy-derived values flagged for maintainer review in the PR. **BOWSTRING** (modifier ≈ 1.0) on string and vine.

Ammunition: **vanilla arrows only** (tipped/spectral included as vanilla allows). Pickup follows the fired arrow's own `PickupStatus`, matching upstream `BowCore.java:224-233`: `DISALLOWED` only for extra multishot arrows that didn't consume ammo, `CREATIVE_ONLY` in creative, otherwise the vanilla default (`ALLOWED`) — it is not globally disabled (corrected 2026-08-18, T77).

Rendering: per-stage draw art derived from the 1.12 clone (limbs bend, string stretches — three `pull` stages via item-property overrides, same mechanism as the broken-tool swap #352); crossbow loaded/unloaded models. Screenshot harness gains held-and-drawn poses.

Modifiers/traits: no ranged-exclusive modifiers; **every** modifier/trait upstream adapts by category is ported — Haste → draw speed on launchers (`ModHaste.java:41,115`), Luck refuses launchers (`ModLuck.java:35`). Hit-effect modifiers (fiery, necrotic, knockback, beheading, smite, bane...) travel with the arrow: upstream resolves them **ammo-side**, from the fired `EntityProjectileBase`'s own traits (`IProjectileTrait`, `EntityProjectileBase.java:193-264`) — only two traits actually implement that interface, `TraitEndspeed` and `TraitHovering` (movement, not hit effects); no other trait has a launcher/projectile branch. Forgeweave takes a deliberate deviation instead, resolving hit-effect modifiers from the **bow** the arrow was shot from (`CombatSeams.java:197-231`, PR #410) so the vanilla arrow itself needs no Forgeweave-specific state — one GameTest per launcher-adapted modifier (corrected 2026-08-18, T77; the earlier "~15 traits with an ILauncher/projectile branch" line was wrong on both the count and the mechanism).

Gating: longbow and crossbow join `forgeweave:large_tools` (#348 mechanism); shortbow is a station tool.

### Non-goals for M3.5 (maintainer decisions 2026-08-15)

- **Material arrows and the shuriken** — deferred together to a follow-up (M3.6 candidate): both ride upstream's `ProjectileCore` item-projectile infrastructure (ammo counter as durability, own entity, reload at the station). Deferring arrows means deferring that infra once; when it lands the bows already accept both ammo kinds as 1.12 does. Deviation from 1.12 parity recorded here.
- Bolts (cut earlier), javelin, throwing axe, energy-consuming ranged tool (PlusTiC) — deferred backlog, no 1.12 counterpart.
- Fins modifier (projectile-only) — with arrows.
- Slime-vine bowstrings — world-content milestone.

### CI and release gates

- **GameTest coverage**: bow assembly at station vs forge gating; BOW/BOWSTRING stat math into the tool component (drawspeed/range/damage per material); draw progress = drawSpeed × ticks / drawTime (`BowCore.java:112-114`); arrow entity damage carries bonusDamage; crossbow load → persists across hotbar swap and save/reload → fires; Haste raises draw speed on a bow; Luck refused on a bow; one test per launcher-adapted hit-effect modifier; fired arrow keeps the vanilla `PickupStatus` rule (disallowed only for unconsumed multishot arrows).
- **Save-compat fixtures (same PR as the format; corpus is CI-gating; first milestone under the binding promise)**: bow tool components with BOW/BOWSTRING-derived stats; crossbow `loaded` state + loaded ammo; any draw-state component.
- **Manual release checklist adds**: screenshot-harness review of drawn poses (3 stages) and crossbow loaded/unloaded for each bow; third-person hold; JEI shows bow assembly + limb/string casting; previous-release (beta.1) world load with a bow in inventory.
- Alpha tags during the milestone; post-alpha playtest-fix round; **final tag `mc1.21.1-v0.3.5`** (beta series, since beta.1 precedes it).

## Milestone 4 — armors

Planned 2026-08-24 (grilling session on #25; maintainer decisions recorded inline, numbered D1–D24). Ships under the save-compat promise (after `mc1.21.1-v0.3.5-beta.1`). The alpha.4 playtest-fix round runs in parallel and lands as beta.2 through the normal pipeline (D1).

**Source of truth (D2)**: Tinkers' Construct 1.12 has no armor. Construct's Armory (LGPL) stays *inspire only* — no clone, no NOTICE rows, ideas only. For M4 the **1.20 clone becomes a derivation source by name** — plate armor: part model (plating + maille), `PlatingMaterialStats` values, the layered armor model and its grayscale base textures, the ARMOR-scope material trait table, the defense modifier family — an explicit override of the standing "1.20 never sets feature scope" rule, recorded in § Milestone sources like M3/M3.2. Custom armor models by the maintainer's designer arrive in **M9** (D18), not during M4.

### Acceptance test

Fresh world, dedicated server, no cheats. Craft an **obsidian chestplate plating** at the Part Builder (non-metal platings are Part Builder parts, the cast bootstrap) and a **vine maille**; pour gold over the plating to get the plating cast. Melt iron in the smeltery, cast **iron plating** for all four pieces and an **iron maille**. At the Tool Station (issue #1006 retired issue #782's Armor Station — the light set assembles at the Tool Station or Tool Forge, the heavy set at the Tool Forge only), assemble helmet, chestplate, leggings, boots from plating + maille; each piece's tooltip shows armor/toughness/knockback resistance/durability matching the 1.20-derived iron values (chestplate armor 5, durability 240). Wear the set: third-person render shows the iron-tinted plate layer over the maille layer. Take damage: durability drops on the plating, damage is reduced by the computed armor. Break a piece to 0 durability: it stays equipped but protects nothing; repair it with an iron ingot at the Tool Station. Apply **fire protection** (defense modifier) to the chestplate and **thorns** to the leggings; walk into lava briefly and get hit by a zombie — fire damage is reduced and the zombie takes thorns damage. Assemble a cobalt-plated helmet and observe **melee protection** (cobalt's ARMOR trait). Save, restart, reload: all four pieces keep parts, modifiers and durability. Everything shows in JEI with no JEI code changes; the book's armor section and the armor Ponder scene open.

### Content manifest

| Kind | Contents |
| --- | --- |
| Pieces (D3, D9) | Exactly four: helmet, chestplate, leggings, boots. **Two parts each**: `plating_<piece>` (all stats) + `maille` (statless: traits + inner texture layer). No fixed-material sets (travellers' gear, slimesuit, slime wings are non-goals). |
| Part items | 4 platings + 1 maille; patterns and gold/clay casts for all five; Part Builder recipes for non-cast-only materials; smeltery casting for metals (D12 — same M2 flow, no crafting-table bootstrap). |
| Plating materials (D10) | The 15 Forgeweave materials with a 1.20 `PlatingMaterialStats` row — iron, copper, cobalt, manyullyn, knightslime, pig iron, steel, bronze, lead, silver, electrum, amethyst bronze, rose gold, obsidian, ancient — with per-piece `durability/armor/toughness/knockback_resistance` derived from the clone; plus **ardite, netherite, nahuatl** by interpolation (ardite ≈ cobalt, netherite > manyullyn, nahuatl ≈ obsidian; values proposed on the issue, maintainer-reviewed). 18 total. Wood/stone/bone/paper/etc. make no plating (parity). |
| Maille materials (D11) | The 18 above + vine, chorus, **bone, cactus, blue slime vine** = 23. |
| Material schema (D14, D17) | New `PartItem.Kind.PLATING`/`MAILLE`; `Material` gains `plating{helmet,chestplate,leggings,boots}` blocks, `maille` marker, and `traits.armor` (scoped list read by both plating and maille). New `ARMOR_STATS` data component (precedent `LAUNCHER_STATS`); `TOOL_STATS` shape untouched. `ToolConstants.Category.ARMOR`. |
| Station (D13, reversed by issue #782, **restored 2026-09-18 by maintainer decision, issue #1006**) | The Tool Station **and** Tool Forge assemble armor (plating + maille, positional `ENTRIES` rows). Issue #782 briefly moved that onto a dedicated **Armor Station** block; #1006 retired it and brought armor back, with one change to D13 as written: the light set builds at either block, the heavy set (#735) is in `#forgeweave:large_tools` and so builds at the Tool Forge alone. The generic `ENTRIES`/`ToolConstants.Category.ARMOR` machinery was never duplicated, so #1006 is a deletion: the category gate in `ToolStationTabs#visible` and `ToolAssemblyRecipes#resolveAssembly` is gone and the large-tool gate already there answers for the heavy set. Armor Stations placed in existing worlds convert to Tool Stations, contents kept, through registry aliases on the block and item ids. |
| ARMOR traits (D17) | 12 ported from the clone's ARMOR-scope table: iron projectile_protection · copper depth_protection · obsidian blast_protection · cobalt melee_protection · manyullyn warded · amethyst bronze crystalstrike · silver consecrated · knightslime overshield + overslime (#728: the clone's overslime pool, 50 per trait, -0.5 armor without an overslime_friend maille — blue slime vine, chorus — refilled with slime at the station; armor only) · bone piercing_guard · cactus thorns · chorus enderclearance · blue slime vine skyfall. The four protections share one implementation with the modifier of the same name (trait = level 1, parity). Materials without a clone ARMOR row keep only their general traits; general traits attach unfiltered — hooks that do not apply simply never fire. |
| Modifiers (D15, D16) | Single slot pool (`DEFAULT_SLOTS`), no DEFENSE slot type (was a candidate for M7 alongside the cap decision; **M7 planning resolved both** — see § Milestone 7, D-M7-1: leveling grants into the single pool and the DEFENSE slot type is a permanent non-goal). New armor-only (predicate `armorOnly()` next to `harvestOnly()`): fire/blast/projectile/magic/melee protection, knockback resistance, thorns. Existing generic modifiers (reinforced, mending moss, soulbound, extra slot…) apply where the predicate allows. 1.20 abilities (double jump, zoom, bouncy, flamewake…) are backlog for M5/M6 — bouncy would collide with M5 slime boots. |
| Defense seam (D8) | `CombatSeams.defensePass` walks the defender's four equipped pieces; new `Trait.onDefend(CombatDefense)` hook. This is also M7's leveling entry point for armor — see § Milestone 7 — tool leveling, **D-M7-2**, which adds the per-piece attribution `DefendedBlow` deliberately left out here. |
| Render (D18) | Derive only the clone's **grayscale bases** (plating armor/leggings, maille armor/leggings) + port the copier as `scripts/derive_armor_art.py`, tinting with `Material.color` at render time (`getArmorLayerTintColor`; maintainer decision #726 replaced #679's generation-time tint) — covers ardite/netherite/nahuatl, which have no clone PNG. Two-layer armor model (plating over maille). NOTICE rows for the bases only. |
| Durability/repair (D19) | Durability = plating's `durability`; repair with the plating material's `repair_item` at the Tool Station, same 5% discount; maille never affects durability. |
| Enchanting (D20) | Same `allowVanillaEnchanting` flag as tools; `enchantability` from the plating material. |
| Compat (D7) | Vanilla armor slots only; chestplate excludes Elytra as vanilla does (Elytra stays the flight option for now). Curios, Apotheosis, wings — M8. |
| Book / Ponder / harness (D21) | Data-driven `armor.json` book section; armor-assembly Ponder scene (second registered storyboard); screenshot-harness scene with the four pieces worn in third person. |

### Non-goals for M4

Travellers' gear, slimesuit, slime wings, shields (1.20 content, not mechanics) · DEFENSE slot type · 1.20 armor abilities · plating for non-metal materials beyond obsidian/ancient/nahuatl · Curios/Elytra integration (M8) · custom designer models (M9) · armor leveling (M7 plugs into `onDefend`) · an "armor forge" tier (the smeltery and the existing Tool Forge tier are the only gates). An armor station block stays a non-goal: issue #782 shipped one and issue #1006 retired it again on the maintainer's call — see the Station row above.

### CI and release gates

- **GameTest coverage (D23)**: assembly of each of the four pieces at the Tool Station and the Forge with derived iron stats; wrong-piece plating rejected (helmet plating in the chestplate row); incoming damage reduces plating durability and is attenuated by the computed armor value; a 0-durability piece stays equipped and protects nothing; repair with `repair_item`; Part Builder accepts obsidian plating and refuses iron plating; plating cast via smeltery; **one test per ARMOR trait (12)** and **one per new modifier (7)**.
- **Save-compat fixtures (D22, same PR as the format)**: `ARMOR_STATS` component; an `item_stack` fixture per piece with parts + modifiers filled; a `material` fixture with `plating`/`maille`/`traits.armor`; any stateful ARMOR trait component (overslime; the alpha.1 overshield charge stays registered as a legacy component so old stacks decode, #728).
- **Manual release checklist adds**: screenshot-harness review of the worn set (first + third person) for iron, cobalt, and one non-metal-plated piece; JEI sanity (plating/maille casting, armor assembly); previous-release (beta.1) world load with a worn set; dedicated-server acceptance playthrough.
- Alpha tags during the milestone; post-alpha playtest-fix round; final tag continues the beta series.

### Issue roadmap (D24)

| # | Deliverable | Depends on |
| --- | --- | --- |
| 1 | `PLATING`/`MAILLE` scopes, `Material` schema (`plating` per piece, `maille`, `traits.armor`), data for the 23 materials incl. interpolated ardite/netherite/nahuatl, fixtures | — |
| 2 | Part items (4 platings + maille), patterns/casts, Part Builder + casting recipes, cast bootstrap via obsidian plating | 1 |
| 3 | `Category.ARMOR`, `ARMOR_STATS`, part-built `ArmorPieceItem`, Tool Station/Forge assembly rows, repair, enchantability, fixtures | 1 |
| 4 | Render: grayscale bases + `scripts/derive_armor_art.py`, two-layer tinted armor model, NOTICE | 3 |
| 5 | `defensePass` per piece, `Trait.onDefend`, the 12 ARMOR traits | 3 |
| 6 | Armor modifiers: 5 protections + knockback resistance + thorns, `armorOnly()` predicate | 3 |
| 7 | Book `armor.json`, Ponder armor scene, harness worn-set scene, lang | 4, 5, 6 |
| 8 | Acceptance playthrough, release-checklist lines, alpha tag | 7 |

## Milestone 6 — material expansion: existence-gated compat + a self-contained ladder

Planned 2026-08-31 (epic [#824](https://github.com/gkissel/forgeweave/issues/824); prep doc [docs/research/m6-material-expansion-references.md](research/m6-material-expansion-references.md)). M6 has two content tracks. **Track A — cross-mod compat**: existence-gated materials sourced from other mods' metals, with Tinkers' Evolution's 97-material roster (prep doc §6) as the parity target; needs the `neoforge:conditions` mechanism, since every material must be absent unless its provider mod is installed. **Track B — self-contained original materials**: a TAIGA-style ladder of Forgeweave's own — own ores, own worldgen, own alloy table, own traits, no external mod required; this is the half a Forgeweave-only install actually sees. The two tracks share the ADR-0004 trait behavior library and the UI/schema-hardening work; everything else is independent, and the conditions mechanism blocks only Track A. Epic #824's "Scope (decided — do not relitigate)" section and its judgment calls JC1–JC11 are the source of truth for what follows; JCs without a settled answer are called out as open below rather than guessed at.

### Acceptance test

Fresh 1.21.1 world, dedicated server, no cheats. Draft — tune while writing the child issues:

1. With **no** compat mods installed, open creative / JEI / the guide book / the Part Builder and confirm that **no** modded-metal material exists anywhere — not an unobtainable ghost entry, absent. This is M6's headline, and it reverses M3.2's tag-gated behavior for bronze/lead/silver/electrum.
2. With one supported mod installed (Mekanism is the reference), its materials appear in all four surfaces and craft into parts at the Part Builder from that mod's own ingots.
3. Assemble a tool from a modded material and observe its trait firing.
4. Assemble a tool carrying one of the new parameterized behaviors from each library batch.
5. Charge a tool carrying the energy trait from any Forge Energy source and observe energy spent before durability.
6. Melt an overworld mob in the smeltery for blood; melt a blaze for blazing blood and burn it as a fuel hotter than lava.
7. Transform a Nether Core into an End Core and then a Deep Core, and observe the yield step at each tier.
8. Load a world saved on the previous release.

All new melting, alloying, casting, existence-gated Part Builder, and trait-application recipes are visible in JEI with no JEI code changes.

### Content manifest

| Kind | Contents |
| --- | --- |
| Materials (Track A, ~35–45) | Existence-gated presets sourced from 1.21.1 NeoForge mods verified against the Modrinth API and each mod's own git tree (epic #824 availability table): Mekanism, AE2, Immersive Engineering, Occultism, Modern Industrialization, Twilight Forest, Mystical Agriculture, Powah, Create, Silent Gear, Ad Astra, Allthemodium as the stable core; Ender IO, Draconic Evolution, Industrial Foregoing, Actually Additions, ProjectE gated with care (beta/renamed/no-`c:`-tag callouts per mod). Batched across #833–#837. |
| Materials (Track B, ~30) | Self-contained ladder, own ores/alloys, TAIGA-inspired shape, no mining tiers above netherite (JC10) and no meteor-fall sourcing (JC11) — #838–#841 |
| Trait/behavior classes (~30 new Java) | ADR-0004 parameterized-behavior-library batches: damage-scaling (#827), on-hit effect (#828), utility/economy + reuse audit (#829), Forge Energy tool buffer + energy/solar/kinetic behaviors (#830); ~~armor behaviors on the M4 `onDefend` seam (#831) are JC8-gated, not committed to M6 yet~~ **JC8 answered — shipped in M6** (issue #831, PR #908) |
| Blocks/fluids | Track B ore blocks and worldgen features; Track B molten fluids, melting/casting rows, and alloy table (#840); End Core and Deep Core smeltery tiers with a pour-to-transform mechanic (#845) |
| World content | Generic overworld-mob blood melting and a `smeltery_fuel` entry for blazing blood, plus a meltable dragon breath (#844) — the remaining pieces of #181; blood/blazing blood/deep blood items and their entity-melting recipes already shipped via #270 |
| Schema | `neoforge:conditions` on `Material` and its companion melting/casting/alloy/embossing datapack-registry entries (existence gating, mechanism already in NeoForge 21.1's `RegistryDataLoader`, prep doc §1.4); bronze/lead/silver/electrum migrate off tag-only gating to conditions (#826); ADR-0004 parameterized behavior classes with `id + level` serialization; a Forge Energy tool-component buffer |
| Compat mechanism | `neoforge:mod_loaded` / `neoforge:item_exists` conditions, `or`-combined across every known 1.21.1 provider of a metal name for re-homed materials (JC2, #833) rather than one preset per modid |

### In scope (systems)

`neoforge:conditions` existence gating on datapack-registry entries, covering every consuming surface (creative tab, JEI, guide book, Part Builder, tool assembly) with zero call-site changes (prep doc §1.4). The ADR-0004 parameterized behavior library (four batches shipped, including the armor batch once JC8 answered yes — see the Trait/behavior classes row above). The Forge Energy tool buffer and its energy/solar/kinetic behaviors. Track B's own ore/alloy/trait ladder with full smeltery integration (Track B materials always exist, so the reasoning that once kept Track A Part-Builder-only never applied to them). The two new smeltery core tiers (End, Deep) with pour-to-transform. Generic overworld blood melting and the blazing-blood fuel entry. UI/schema hardening at the final 128-material scale (#846): creative-tab part-variant volume, registry sync payload size, guide-book material-section pagination, Part Builder `crafting_items` match performance.

**JC3 reversed (#873, 2026-08-31 session 2): every compat metal (Track A batches 1-5 plus the recovery batch, #872) also gets full smeltery integration**, not Part-Builder-only. Each compat metal's molten fluid + bucket registers unconditionally in Java (the NeoForge platform constraint every fluid lives under) and hides from the creative tab/JEI when its material's provider is absent (`dev.gkissel.forgeweave.material.CompatMaterialAvailability`, the runtime mirror of the material's own `neoforge:conditions`); melting/casting recipes carry the same conditions as their material. Every compat metal material flips to `cast_only: true` — parts come from casting molten metal only, `crafting_items` stays for repair/reference, matching how Track B's own metals (`material/cinderstone.json`) and the base four (cobalt/ardite/manyullyn/steel) already work. Non-metal compat materials (gems, crystals, organics upstream never melts) keep the Part Builder. This also unblocks three PlusTiC-inspiration alloys built from compat inputs — alumite, osgloglas, osmiridium — and two unconditional vanilla-gem materials, emerald and amethyst (Part Builder, not cast_only, mirroring the 1.12 clone's own molten emerald).

**JC10 reversed (#877, 2026-08-31 session 3, post-M6): real mining levels above netherite, not a stats-only ladder.** #838's original decision (no new tool-tier block tags; Track B differentiates by stats/traits/obtainability instead, collapsed onto the five existing vanilla rungs) is superseded. Three `forgeweave:incorrect_for_<tier>_tool` tags now mint above `minecraft:incorrect_for_netherite_tool` — `hardcinder`, `warspar`, `resonite`, each named after the Track B material anchoring it (`TrackBOre.Tier`, `ForgeweaveModifiers#TIER_TAGS`) — and eight of the pre-existing 26 netherite-tier Track B materials plus manyullyn and ancient re-rung onto them (see #877's PR body for the full table). Compat metals (Track A) stay within the five vanilla rungs regardless: other mods' blocks are not in Forgeweave's own tier tags, so moving a compat material's tools onto a Forgeweave-only rung would gate nothing real.

### Datapack trait definitions and KubeJS traits (#832, JC6 resolved 2026-09-02)

ADR-0004 item 3, delivered for traits only (maintainer decision on #832; modifier definitions stay deferred to M8). Two additive ways to create a trait without a Forgeweave code change; both produce an id that material JSON names like any built-in, and the pack supplies the id's `trait.<namespace>.<path>.name` / `.description` lang keys, which is all tooltips, the Tool Station panel and the guide book need. Built-in ids always win a collision; saved tools keep the plain id list.

**Datapack** — one file per trait under `data/<namespace>/forgeweave/trait_definition/<name>.json`, flat: `behavior` picks a parameterized class from the M6 library (`TraitBehaviors`), the other fields are its parameters. Optional `neoforge:conditions` existence-gates it exactly like a material. A wrong `behavior` id or a missing parameter fails the data load with the known ids listed, never a silent no-op trait.

```json
{ "behavior": "forgeweave:effect_on_hit",
  "effect": "minecraft:poison", "duration": 100, "amplifier": 0 }
```

| `behavior` | Parameters (snake_case; `[default]`) |
| --- | --- |
| Combat seams, all accepting an optional gate `condition` (a `HitCondition` name: `any`, `full_health`, `armored`, `not_fire_immune`, `burning`, `undead`, `below_wielder_health`, `harmful_effect`, `full_charge`, `wielder_full_health`, `night`, `day`, `wielder_sneaking`) `[any]` and `chance` 0..1 `[1.0]` — so `charged_bonus_damage` is `bonus_damage_vs` + `"condition": "full_charge"`, and the #828 `chargedOnly` flags are the same field | |
| `damage_scales_with` | `source` (`remaining_durability`, `wielder_health`, `target_missing_health`, `target_max_health`, `impact_velocity`), `coefficient`, `cap` |
| `bonus_damage_vs` | `amount` |
| `crit_multiplier_bonus` | `extra` |
| `effect_on_hit` | `effect` (mob effect id), `duration` (ticks), `amplifier` `[0]`, `stacking_cap` `[0]` |
| `effect_on_self_on_hit` | `effect`, `duration`, `amplifier` `[0]` |
| `strip_effects` | `count` `[1]` |
| `reduce_target_healing` | `fraction` 0..1, `duration` |
| `shorten_invulnerability` | `ticks` |
| `lifesteal` | `fraction`, `cap` |
| `chain_arc` | `range` (blocks), `damage_fraction`, `max_targets` |
| `lightning_on_hit` | — (gate only) |
| `kinetic_charge` | `fraction` (FE per point of damage dealt) |
| Non-seam behaviours | |
| `self_repair_when` | `condition` (`always`, `sunlit`, `night`) `[always]`, `ticks_per_point` |
| `cascading_break` | `blocks` (block tag id) `[vanilla gravity blocks]` |
| `fertilize_on_use` | `durability_cost`, `chance` 0..1 |
| `extra_modifier_slots` | `count` |
| `energized` | `capacity` (FE), `energy_per_durability_point` |
| `solar_recharge` | `rate_per_tick` |
| Worn-piece behaviours (#831's M6-7 armor library). All ride `Trait#onDefend` or `Trait#armorAttributes`, so they fire for a worn piece only: a general trait built on one of these does nothing on the tool half of its material | |
| `damage_floor` | **A drawback, not protection** (#1091): every blow on the wearer deals at least `minimum_hearts`, capped at the blow's own damage, so it only ever undoes a reduction another trait made. Give it to a trait whose name and description own up to the cost. Parameters: `minimum_hearts` |
| `effect_on_attacker` | `effect`, `duration`, `amplifier` `[0]`, `chance` 0..1 `[1.0]` |
| `effect_on_hurt` | `effect`, `duration`, `amplifier` `[0]` |
| `amplify_incoming_healing` | `factor` |
| `convert_damage_to_healing` | `damage_type` (damage type tag), `fraction` 0..1 |
| `stacking_resistance` | `per_hit`, `cap`, `decay` (ticks) |
| `death_save` | `cooldown` (ticks), `cost` (durability) |
| `invulnerability_window` | `ticks`, `condition` (a `DefenseCondition` name) `[any]` |
| `evasion` | `chance` 0..1 |
| `conceal_in_darkness` | `light_threshold` 0..15, `visibility` 0..1 |
| `movement_bonus` | `kind` (`movement_speed`, `flying_speed`, `step_height`, `jump_strength`), `magnitude` |
| `stat_scales_with_wear` | `stat` (`mining_speed`, `protection`), `coefficient` |
| `damage_type_immunity` | `damage_type` (damage type tag) |
| `vent_explosions` | `knockback_factor` |
| Held-tool attribute (#1091) | |
| `knockback_resistance` | `resistance` 0..1, flat on the wielder's knockback resistance attribute while held; `1` is full immunity, the hardcoded `heavy`'s own value |

**KubeJS** (optional dependency, `[2101.7,)`; the mod is unchanged without it) — for logic parameters cannot express. One startup event, one builder; every `on*` callback mirrors a `Trait` hook by name and signature (`ScriptTrait` is the full list), the rest set the constant a hook returns.

```js
// kubejs/startup_scripts/forgeweave_traits.js
ForgeweaveEvents.traits(event => {
    event.register('mypack:frosty')
        .onAfterHit((stack, level, attacker, target) => target.potionEffects.add('minecraft:slowness', 60))
        .onMiningSpeed((stack, effective, originalSpeed, speed) => level_is_cold(stack) ? speed * 1.25 : speed)
        .bonusSlots(1)
})
```

Verification: `TraitBehaviorsTest` (codec round-trip per behaviour, unknown `behavior` fails loudly, a failing `neoforge:conditions` decodes to nothing), `ScriptTraitTest` and `ForgeweaveKubeJSPluginTest` (plugin loads with KubeJS on the test classpath; no KubeJS import outside the `kubejs` package), `DatapackTraitGameTests` (a gametest-only definition reaches a Tool-Station-assembled tool and fires; a conditioned one never registers).

### Non-goals for M6

Bolts (cut earlier, unrelated to M6) · Track B material names — JC9 decided: original Forgeweave coinages, not the reference ladder's own names (epic #824; a starting id-vocabulary proposal for #839–#841 to consume is in the [research doc](research/m6-material-expansion-references.md) §7.3) · meteor-fall ore sourcing — JC11's recommended answer cuts it for M6 in favor of ore veins or a rare surface feature (#839) · slime islands, purple/blue slime spawns (world-content milestone, unchanged non-goal since M2/M3.2) · **sceptres, Artifacts and fusion crafting** — JC7 resolved (maintainer decision 2026-09-02, #847): each is a tool-family or station-multiblock project, not material work, and none is M6; all three move to the deferred backlog below · GTCEu (skip until ids can be dumped from a running instance, JC5). (Track A dedicated molten fluids/casting per modded metal was a non-goal under JC3's original recommendation; JC3 was reversed on #873 — see the JC3-reversal paragraph above — so it is in scope now.)

The other two JC7 line items landed in M6 itself (#847, maintainer decision 2026-09-02): the smeltery gained a `meltSpeedMultiplier` config entry (`ForgeweaveConfig`, default `1.0`, applied in `SmelteryControllerBlockEntity#meltTick`); the tool damage-cap toggle turned out to be a no-op — Forgeweave has no attack-damage ceiling, only the `CombatSeams` 1.0 floor, so nothing was built; and the guide book's materials handbook was audited against Tinkers' Evolution's "Materials and You" handbook and closed with no gaps (per-part-kind stats, trait descriptions with hover text, and obtain hints via the representative item/Part Builder/Casting Basin icons already match the parity target's own `ContentMaterial` page 1:1).

**Open — maintainer decisions pending, tracked on their epic #824 child issue, not guessed at here:**

- ~~JC1 (#842) — whether the ~54 Tinkers' Evolution materials with no 1.21.1 mod build (Botania, Blood Magic, Thermal Series, Thaumcraft, IndustrialCraft 2, Environmental Tech, Natura, Astral Sorcery, Forestry, Advanced Solar Panels, AE2's fluix-steel) ship as dormant condition-gated presets or are skipped entirely.~~ **Answered, option (b)** (#842 closed): Botania and Blood Magic ship as dormant, condition-gated presets, tracked to activate when their 1.21.1 artifacts publish ([#857](https://github.com/gkissel/forgeweave/issues/857), [#858](https://github.com/gkissel/forgeweave/issues/858), both still open and watching upstream); the rest of the ~54 were skipped.
- JC4 — whether mods with no 1.12-addon-roster ancestor (Create, Ars Nouveau, Twilight Forest, Ad Astra, Allthemodium, Silent Gear, Mystical Agriculture, Powah, Occultism, Modern Industrialization, GTCEu) are in scope beyond Modern Industrialization and Powah. **Largely settled by M8, mod by mod, without a single JC4 ruling**: Twilight Forest (D-M8-25, #1059), Allthemodium (D-M8-19, #998), Silent Gear (D-M8-24, #1058), Mystical Agriculture (D-M8-20, #999) and Occultism (D-M8-18, #997) all shipped Track A presets; Create stays recipe/tag-only, no material roster (D-M8-16). Ars Nouveau, Ad Astra and GTCEu remain unaddressed.
- ~~JC6 (#832) — whether ADR-0004's datapack-creatable trait-definition registry ships in M6 (scoped to traits) or the ADR is amended to defer it.~~ Resolved 2026-09-02: ships in M6, traits only, plus the KubeJS binding — see the section above.
- ~~JC8 (#831) — whether the armor trait behavior library ships in M6 or a later armor milestone.~~ **Answered — shipped in M6** (issue #831, PR #908).

### CI and release gates

- **GameTest coverage**: existence-gating negative path (a conditioned material is absent from the synced registry, creative tab, JEI, and book) and positive path (`mod_loaded`/`item_exists` against a modid loaded in the dev/test environment or a gametest-registered item, prep doc §1.4 item 6); one test per new parameterized behavior class per library batch; Forge Energy spent before durability; each smeltery core tier's yield and the End→Deep pour-to-transform step; generic overworld-mob blood melting; blazing-blood `smeltery_fuel` entry burns hotter than lava; Track B alloy ratios and ore-to-ingot yield.
- **Unit gates**: `MaterialSyncSizeTest`'s budget re-measured at M6's final 128-material roster (#846): 95,235 bytes against the 96 KiB (98,304-byte) budget #837 raised it to — ~3 KB (3%) of headroom left, no further revisit needed since the roster is now closed. Creative-tab part-variant volume (128 materials x 37 part items -> ~3,850 part-material stacks, built in ~15ms), the Part Builder's per-material-slot-change scan (~300 crafting items across the roster, sub-millisecond per call) and the JEI recipe-list build (~2,400 part-crafting + assembly + repair recipes, built in ~20ms) all measured comfortably inside budget — no code changes needed on those three; `ForgeweaveCreativeTabScaleTest`, `PartBuilderRecipesScaleTest` and `JeiRecipesScaleTest` pin the numbers. The guide book's materials section (one `IconGridPage` per issue #846's audit) already spans multiple leaves via the block-level paginator issue #428 built; `BookMaterialsScaleTest` pins that behavior at the real roster instead of leaving it unverified.
- **Save-compat fixtures (same PR as the format; corpus is CI-gating)**: any new tool-component fields from the Forge Energy buffer and new leveled trait definitions; End Core/Deep Core block-entity NBT (structure bounds, tier state); a fixture snapshotting a tool built from a Track A and a Track B material each.
- **Manual release checklist adds**: JEI-installed sanity check twice — once with Mekanism present (Track A reference mod, materials appear) and once absent (confirm existence gating: no ghost entries in creative, JEI, the book, or the Part Builder); manual spark profile opening the creative tab, the guide book's materials section, and the Part Builder at the full 128-material roster (#846 -- automated timing already covers the pure enumeration cost, spark catches anything render/GL-side that a JUnit test cannot); dedicated-server acceptance playthrough; previous-release world load.
- Alpha tags during the milestone; the save-compat promise stays binding throughout (M6 ships after `mc1.21.1-v0.4.0-beta.4`).

## Milestone 7 — tool leveling

Planned 2026-09-02 (planning epic [#28](https://github.com/gkissel/forgeweave/issues/28); execution epic [#917](https://github.com/gkissel/forgeweave/issues/917)). Ships after `mc1.21.1-v0.5.0-beta.4`, under the save-compat promise. Depends on M3 (the tool roster and `ToolConstants`), M3.5 (the launcher stats the ranged grant reads) and M4 (the `onDefend` defense seam armor leveling extends).

**Source of truth**: [Tinkers' Tool Leveling](https://github.com/SlimeKnights/TinkerToolLeveling) by boni/SlimeKnights, **MIT**, pinned clone in CLAUDE.md's upstream table (16 Java classes, `en_us.lang`, `sounds.json`, `sounds/chime.ogg`). **Direct port allowed** under [ADR-0003](adr/0003-provenance.md) — every derived file, code and the audio asset alike, gets a `NOTICE.md` row in the PR that derives it, the same rule the 1.12 and Mantle clones follow. **Armor leveling has no upstream** — Tool Leveling never touched armor, so M7's armor half is original Forgeweave design on M4's `Trait#onDefend` seam (D-M7-2), not a derivation.

### Acceptance test

Fresh 1.21.1 world, dedicated server, no cheats. A player can:

1. Assemble a pickaxe at the Tool Station; its tooltip shows `Level: Like new` and `XP: 0 / 500`, and the station panel shows 3 free modifier slots.
2. Mine 500 blocks the pickaxe is effective against. On the 500th: a chat status message, the level-up chime, the tooltip level line turns `Clumsy` in a new colour, and the station panel now shows **4** free slots — spend the new slot on a modifier and it applies.
3. Keep mining: the next level also costs 500, the one after 1000, then 2000 (upstream's curve, ported exactly — see D-M7-5).
4. Assemble a hammer and confirm its first level costs 4500 (the AoE ×9 base XP).
5. Kill a mob with a broadsword: the sword gains XP equal to the rounded killing-blow damage. Hit a second mob with the broadsword, swap to a cleaver, kill it with the cleaver: **both** weapons are paid the damage each of them dealt, on the kill, not before.
6. Fire an arrow from a longbow and hit a mob: the bow gains `ceil(5 × drawTimeSeconds)`. Miss: nothing.
7. Till dirt with a mattock, harvest crops with a scythe, path a block with a shovel: +1 XP each. Block an attack with a battlesign: XP equal to the rounded incoming damage, floor 1.
8. Wear a full iron armor set and take hits: each worn, unbroken piece gains XP for the damage it mitigated; a piece levels up with the same chat line, chime and tooltip line, and gains one modifier slot of its own.
9. Save, restart, reload: every level, XP total and earned slot survives, on tools and on armor, and a mob that was damaged-but-not-killed before the restart still pays out when it dies afterwards.
10. Set `toolLeveling = false` and reload: no XP accrues anywhere, no level or XP tooltip line appears, no chime — and every already-earned slot still counts, with the modifiers spent into it still working.

No new JEI categories; the guide book's leveling page opens.

### Content manifest

| Kind | Contents |
| --- | --- |
| State (D-M7-7) | One new data component `forgeweave:tool_level` — `{level, xp, bonus_slots}`, persistent + network-synchronised like every other component in `ForgeweaveDataComponents`. **Absent = level 0**, so no migration and no backfill: tools and armor built before M7 simply start at zero. Named `tool_level`, not `level`, because "level" already means *modifier* level everywhere in `ForgeweaveModifiers`. |
| Per-tool numbers (D-M7-5) | `ToolConstants.Entry` gains a `baseXp` field — M3's home for per-tool numbers — defaulted from config and multiplied ×9 for the AoE shapes (hammer, excavator, lumberaxe, scythe, vein_hammer). `pickaxe`/`shovel`/`hatchet` have no `Entry` (they predate `ToolConstants` and pass constants inline) and take the config default. |
| Config (D-M7-3) | `toolLeveling` (default `true`) in `ForgeweaveConfig`'s `content` section beside `harvestTools`/`armor`/`modifiers`; plus `defaultBaseXP` (500), `levelMultiplier` (2.0, floored at 2.0) and `maximumLevels` (-1 = no cap). `ForgeweaveConfig` is a `SERVER` spec, so NeoForge already syncs it on join — upstream's hand-rolled `ConfigSync`/`ConfigSyncPacket` pair is **not** ported. Neither is `newToolMinModifiers`: it exists upstream to force new tools to 3 slots, and Forgeweave's `DEFAULT_SLOTS` is already 3. |
| Feedback (derived) | Chat status message per level (`message.forgeweave.levelup.2`–`.11` + `.generic`); the level-up sound registered as `forgeweave:tool_level_up` from the derived `chime.ogg` (one `ForgeweaveSounds.register` line, one hand-edited `sounds.json` entry — that file is hand-written by design); tooltip line 1 = the level name from the 0–11 adjective ladder with the four easter-egg levels (19, 42, 66, 99) and `+` suffixes on wrap, tinted by the rotating hue `frac(0.277777 × level)` at s 0.75 / v 0.8; tooltip line 2 = `XP: n / needed`, hidden at the cap. Ladder, hue and easter eggs are ported verbatim per maintainer decision — the four easter-egg strings name upstream contributors and are kept as attribution, not repurposed. |
| Armor (D-M7-2, original) | The four worn pieces level independently on the M4 defense seam, same curve, same feedback, +1 slot each. Needs per-piece attribution added to `DefendedBlow`, whose own javadoc defers exactly this to M7. |
| Multi-hit ledger (D-M7-4) | A NeoForge **data attachment** on `LivingEntity` holding `player UUID → tool stack → accumulated damage`, serialized with the entity, paid out on death. A 1:1 port of upstream's `IDamageXp`/`DamageXpHandler` capability. |
| Book / Ponder | One data-driven guide-book page under `assets/forgeweave/book/sections/`. A Ponder scene only if one genuinely fits — leveling is a slow numeric process, and a storyboard that cannot show 500 block breaks is worse than no scene. |

### In scope (systems)

**D-M7-1 — no level cap; each level grants exactly one modifier slot.** Upstream 1:1: `maximumLevels` defaults to `-1` and the config entry stays for pack authors. The grant is a **third additive term in `ForgeweaveModifiers.freeSlots()`**, next to `DEFAULT_SLOTS` and the existing modifier/trait `bonusSlots` sums — one line, no new plumbing, and every existing caller (`StationText`, `ToolTooltip`, `ToolAssemblyRecipes`, `ModifierApplication`) picks it up unchanged. The slot count is **stored** on the component (`bonus_slots`), not recomputed from `level`, so changing `levelMultiplier` or `defaultBaseXP` never retroactively removes a slot from a tool that already earned it — upstream's reason, kept. Forgeweave has no global slot cap to interact with (only per-modifier level caps), so there is nothing to reconcile; that closes the "interaction with the modifier cap is decided at M7 planning" line in the § Milestone sources table. It also settles M4's D15/D16 dangling candidate: **the DEFENSE slot type stays a non-goal** — leveling grants into the single existing pool, which is precisely what makes armor leveling work against the modifiers M4 already shipped.

**D-M7-2 — armor leveling ships in M7, as original design.** *Proposal below; **maintainer confirms magnitudes** before M7-6 is implemented.* `CombatSeams#armorPass` currently walks the four pieces sharing one mutable `DefendedBlow` accumulator with no per-piece attribution. The proposal adds that attribution — each piece's contribution is the damage removed by the `addProtection` / `addFlatReduction` / `setDamage` calls made during *its* leg of the walk, plus any overslime it absorbed in its `onDefend` — and grants that piece `max(1, round(mitigated))` XP whenever it was worn, unbroken, and the blow actually dealt damage. Base XP per piece comes from the piece's `ToolConstants.ARMOR` / `HEAVY_ARMOR` entry with no AoE multiplier; heavy pieces take the same base as light ones. Fallback if per-piece attribution proves more invasive than it looks: split `round(damageTaken)` evenly across the worn unbroken pieces. That is explicitly the *second* choice — even splitting makes boots and a chestplate level at the same rate, which is the wrong incentive.

**D-M7-3 — `toolLeveling` config flag, on by default, matching the other family toggles.** Off means the mechanic is fully inert: no XP accrual on any path, no tooltip lines, no chat line, no chime. It does **not** mean levels are revoked — `bonus_slots` keeps counting in `freeSlots()` regardless, because a flag flip that silently invalidates modifiers already applied into an earned slot is a save-corruption bug wearing a config's clothes. Read through `ForgeweaveConfig.enabled(...)`, never `.get()`, per that helper's existing contract.

**D-M7-4 — the multi-hit damage ledger is ported 1:1 as a data attachment on `LivingEntity`.** Melee XP is paid on the kill, never on the hit: `round(damageDealt)` to the killing weapon, and every other tool that damaged the same mob is paid its own accumulated total from the attachment at the same moment. Payout runs inside the `LivingDeathEvent` handler `CombatSeams` already owns (`CombatSeams#onDeath` → `CombatSeam#postKill`, whose javadoc already names M7). Upstream's inventory re-scan (match the exact stack, else match an equal tool elsewhere in the inventory) ports as-is — a player can pocket a sword and still be paid when the mob bleeds out.

**D-M7-5 — the curve, ported exactly, including its shape at the bottom.** `xpForLevelup(level) = level <= 1 ? baseXp : xpForLevelup(level - 1) × levelMultiplier`. Read it carefully: **levels 0→1 and 1→2 both cost `baseXp`**, 2→3 costs `baseXp × 2`, 3→4 costs `baseXp × 4`. That is upstream's actual behavior, not a transcription slip, and the GameTest below pins it so a later "cleanup" cannot quietly change the progression. Per-tool base XP lives on `ToolConstants.Entry`; `defaultBaseXP`, `levelMultiplier` and `maximumLevels` stay in config. Upstream's `Map<Item, Integer>` config table is not ported — it existed because 1.12 had no per-tool constants table, and it forced a config rewrite (`insertDefaults`) every time the tool registry changed.

**D-M7-6 — ranged XP maps onto Forgeweave's existing two quantities.** Upstream computes `drawTimeSeconds = drawTime / (20 × drawSpeed)` and grants `ceil(5 × drawTimeSeconds)` on projectile impact. Forgeweave has both halves already: `BowItem#drawTime()` is a per-item constructor int (shortbow 12, longbow 30, crossbow 45) and `LauncherStats#drawSpeed` is the dimensionless rate multiplier — so the formula ports literally. There is **no shared vanilla-`BowItem` draw-time constant** in the tree to hang it on; `drawTime()` is what replaces upstream's `BowCore#getDrawTime()`. The grant hangs off `CombatSeams#onHit`, which already resolves the live launcher stack on projectile hits (#416), rather than off `ArrowEntity#onHitEntity` — same moment, existing seam, and it inherits the impact-speed gate's intent without duplicating it. A miss grants nothing (parity). Crossbows had no upstream counterpart and take the same formula with their own `drawTime`. The shuriken has no draw at all — **proposal**: treat it as melee, `round(damageDealt)` on kill, since it already routes through `CombatSeams`.

**D-M7-7 — state, and what does not need one.** `forgeweave:tool_level` as above. No migration, no backfill, no fixture rewrite: absent means level 0 means today's behavior.

**D-M7-8 — the key-probing has to go.** Upstream picks its level name and chat line by asking `I18n.canTranslate("tooltip.level." + n)` at the point of use — a 1.12 server-side idiom that does not survive 1.21.1's client/server split, where the server emits `Component.translatable` and only the client resolves it. Forgeweave makes the key sets **explicit constants in Java**: the 0–11 ladder, the `{19, 42, 66, 99}` easter eggs, and the 2–11 chat range. Wrap arithmetic is unchanged (`level % 12` for the name, `level / 12` `+` suffixes). This is also what `ForgeweaveLanguageProvider` and `LocalizationAuditTest` require — every one of those keys is a declared lang entry, and no level-up text is a `Component.literal`.

**D-M7-9 — upstream's cap off-by-one is corrected.** `Config.canLevelUp` reads `maximumLevels >= currentLevel`, so a cap of `N` actually lets a tool reach `N + 1`. Forgeweave uses `cap <= 0 || level < cap`. Unobservable at the default `-1`, and a one-line deviation recorded here rather than a bug faithfully reproduced.

**D-M7-10 — blocking and the utility grants land on existing call sites, with no new event bus.** Blocking: XP to the held blocking tool = `max(1, round(originalDamage))`, read off `CombatDefense#blocking`/`using` (upstream grants the *incoming* damage, not the amount absorbed — kept), with `ForgeweaveInnates.Deflect` covering upstream's battlesign projectile-block special case. Utility: +1 per mattock hoe-till (`MattockItem#useOn`), +1 per AoE crop harvest (the `CUBE_3X3X3` branch of `ToolItem#useOn`, plus `KamaItem`'s equivalent), +1 per shovel path (`ShovelPath#flattenOne`). Forgeweave fires no events at any of those three sites, so upstream's three `TinkerToolEvent` subscriptions become three direct calls. Note the mattock deliberately omits `SHOVEL_FLATTEN` from `canPerformAction`, so it never earns the path grant. Mining: +1 per effective block break, the same `wasEffective` gate as upstream.

**Not ported**: `ConfigSync`/`ConfigSyncPacket` (NeoForge's `SERVER` config spec syncs already), `ClientProxy`/`CommonProxy` (no sided-proxy pattern in 1.21.1), `EventHandler#onToolBuild`'s `newToolMinModifiers` adjustment (a no-op at Forgeweave's `DEFAULT_SLOTS = 3`), and the `/levelupTool` debug command (GameTests call the XP API directly, and `/data` edits the component).

### Non-goals for M7

Any level reward beyond the one modifier slot — no level-gated abilities, no stat scaling with level (upstream grants exactly `+1 bonusModifiers` and nothing else) · the DEFENSE slot type (M4's D15/D16 named it an M7 candidate; resolved above as a permanent non-goal) · retroactive XP or level backfill for pre-M7 tools · XP transfer, level reset, or level sharing between tools · leveling for gadgets (slime sling, slime boots — no damage or harvest seam to hang XP on) · a level leaderboard, statistic, or advancement family · the upstream debug command · a Ponder scene if none fits the mechanic (M7-7 decides on the evidence, and "no scene" is an acceptable answer).

### CI and release gates

- **GameTest coverage**: 500 effective block breaks level a pickaxe 0→1 and add exactly one free slot, which is then spendable at the Tool Station; an *ineffective* break grants nothing · a hammer's first level costs 4500 (AoE ×9) · the curve's shape at the bottom — 0→1 and 1→2 both cost base, 2→3 costs base×2 · a melee kill grants `round(damageDealt)` to the killing weapon · damage that does not kill grants nothing until death, then pays every contributing tool from the attachment, including one the player has since moved out of the main hand · the attachment survives an entity save/load round trip · a ranged impact grants `ceil(5 × drawTime / (20 × drawSpeed))` and a miss grants nothing · blocking grants `max(1, round(damage))` · mattock till, AoE crop harvest and shovel path each grant 1 · a worn armor piece gains XP per D-M7-2 while a broken piece gains none, and an overslime absorb counts · `toolLeveling = false` accrues nothing and shows nothing, while an already-earned `bonus_slots` still counts in `freeSlots()` · `maximumLevels = N` stops the tool at exactly N and hides the XP tooltip line there.
- **Unit gates**: `forgeweave:tool_level` codec + stream-codec round trip · the level-name ladder — 0–11 direct, 12 wraps to `Like new+`, 19/42/66/99 hit their own keys, 24 gets two `+` · the hue is `frac(0.277777 × level)` · `LocalizationAuditTest` stays green (every ladder and level-up string is a declared lang key, no `Component.literal`).
- **Save-compat fixtures (same PR as the format)**: `m7_tool_level.snbt` (a pickaxe with `tool_level` filled and a modifier spent into the earned slot) and `m7_armor_level.snbt` (a chestplate ditto). The `LivingEntity` attachment serializes into *entity* NBT, not an item stack, so it does not fit the item/material-shaped corpus — **M7-8 answered that question: the corpus does not grow an entity case**, and the attachment is covered by the save/load GameTest above instead, because the round trip that matters is behavior (a mob saved, reloaded and only then killed still pays the tool) and an entity fixture would need a live `ServerLevel` these plain JUnit tests deliberately avoid. The reasoning is recorded in `SaveCompatCorpusTest`'s javadoc; `m7_tool_level.snbt` still carries the `tool_id` the ledger keys by, so the item-side component is pinned.
- **Manual release-checklist adds**: level a tool by hand on the dedicated server and confirm the chat line, the chime, and that the tooltip colour actually rotates between levels (a hue no automated test can see) · check the level line at a wrapped level and an easter-egg level by editing `tool_level` with `/data` on a held tool · JEI sanity (no new categories; the Tool Station panel reflects the earned slot) · previous-release world load carrying both a pre-M7 tool and a leveled one · load a world with leveled tools under `toolLeveling = false` and confirm nothing breaks and no slot is lost.
- Alpha tags during the milestone; the save-compat promise stays binding throughout.

### Issue roadmap

| # | Deliverable | Depends on | Status |
| --- | --- | --- | --- |
| M7-1 | `tool_level` data component, the level curve, `ToolConstants.Entry.baseXp`, the `toolLeveling`/`defaultBaseXP`/`levelMultiplier`/`maximumLevels` config entries, and the shared `addXp` API | — | shipped ([#918](https://github.com/gkissel/forgeweave/issues/918)) |
| M7-2 | XP gain: mining + melee, with the `LivingEntity` damage attachment paid out on death | M7-1 | shipped ([#919](https://github.com/gkissel/forgeweave/issues/919)) |
| M7-3 | XP gain: ranged impact + the utility grants (mattock till, AoE harvest, shovel path, blocking) | M7-1 | shipped ([#920](https://github.com/gkissel/forgeweave/issues/920)) |
| M7-4 | Level-up grants a modifier slot through `freeSlots()`; the Tool Station shows it | M7-1 | shipped ([#921](https://github.com/gkissel/forgeweave/issues/921)) |
| M7-5 | Level-up feedback: chat line, `chime.ogg`, the tooltip ladder and its hue | M7-1 | shipped ([#922](https://github.com/gkissel/forgeweave/issues/922)) |
| M7-6 | Armor leveling on the defense seam, incl. per-piece attribution in `DefendedBlow` | M7-1, M7-4 | shipped ([#923](https://github.com/gkissel/forgeweave/issues/923)), magnitudes still the maintainer's call |
| M7-7 | Guide-book page, plus a Ponder scene only if one fits | M7-1 … M7-6 | in flight ([#924](https://github.com/gkissel/forgeweave/issues/924)) |
| M7-8 | Save-compat fixtures, GameTest sweep, release-checklist lines, acceptance playthrough | all | fixtures, sweep and checklist lines shipped ([#925](https://github.com/gkissel/forgeweave/issues/925)); the acceptance playthrough and the alpha tag are the maintainer's, run off `docs/playtest/checklist-0.5.0-beta.5.pt-BR.md` |

## Milestone 8 — deep compat

Planned 2026-09-04 (planning epic [#29](https://github.com/gkissel/forgeweave/issues/29); execution epic [#967](https://github.com/gkissel/forgeweave/issues/967)). Under the save-compat promise. Depends on M2 (the smeltery and its fuel ladder), M4 (armor, since affixes and sockets apply to it), M6 (Track A presets and the `trait_definition` registry the modifier registry copies) and M7 (tool leveling, because sockets and levels compete for the same modifier slots and that interaction is specified here rather than discovered).

M8 makes Forgeweave a good citizen in a large modpack. Every integration is optional, existence-gated and reversible: the mod runs identically with none of them installed, and a save made with an integration on still loads with it off.

Replanned 2026-09-07 in a second session, which roughly tripled the integration count and added decisions D-M8-6 to D-M8-20 below. D-M8-1 to D-M8-5 stand as written; D-M8-3's heater block is the one thing replaced, by D-M8-11's energized tank.

Mod versions verified 2026-09-04 on Modrinth, NeoForge 1.21.1, release channel: Apotheosis `1.21.1-8.7.0`, Curios `9.5.1+1.21.1`, EMI `1.1.24+1.21.1+neoforge`, Create `6.0.10+mc1.21.1`, Mekanism `10.7.19.85`.

**Provenance**: no code is derived from any integrated mod. Each one is a `compileOnly` API dependency behind a Forgeweave-side seam class that names no upstream type, verified by a source-isolation test, the pattern the Draconic layer already follows (`ForgeweaveDraconicCompat`, `DraconicSourceIsolationTest`). Licenses for Apotheosis and EMI were not checked at planning, so derivation stays off the table until someone checks them; the API-dependency pattern needs no license review to be safe. Tinker's JEI (MIT) remains the M1/M8 reference for the JEI plugin per the § Milestone sources table.

### Acceptance test

Fresh 1.21.1 world, dedicated server, no cheats, with Apotheosis, EMI, Mekanism, Draconic Evolution, Jade and KubeJS installed. A player can:

1. Assemble a pickaxe at the Tool Station and apply the `socketed` modifier. It costs a modifier slot per level, the tool shows that many empty sockets, and the station panel's free-slot count drops accordingly.
2. Seat an Apotheosis gem in a socket. Its bonus applies to the tool and the tooltip shows it, on the same lines Forgeweave's own modifiers and traits use.
3. Level that pickaxe under M7 and spend the earned slot on another level of `socketed`. The new socket takes a second gem, and a gem bonus that shares a quantity with a trait or modifier adds to it rather than replacing it.
4. Find a Forgeweave tool with an Apotheosis affix in a loot chest. The affix text renders alongside Forgeweave's own tooltip lines, the affix bonus applies, and the tool still repairs, takes modifiers and levels.
5. Set `allowVanillaEnchanting = true` and enchant a Forgeweave tool at Apotheosis' enchanting table. The enchantment coexists with its modifiers, sockets and level, and consumes no modifier slot. Set the flag back to `false` and the table refuses the tool.
6. Uninstall JEI, install EMI alone, and open it: every Forgeweave recipe category renders and every transfer handler still fills a station, whether through EMI's JEI-plugin bridge or through the native plugin written for the categories the bridge failed.
7. Build an energized tank into a smeltery, drop a bucket of molten magma in as its fuel sample, and feed it Forge Energy from a Mekanism generator. The smeltery melts at molten magma's temperature and the tank's buffer drains per melt tick. Let the buffer run dry and melting stops rather than slowing. Press the overdrive button and melting doubles at double the energy cost. The JEI smeltery-fuel category shows the tank and its cost formula.
8. Swap the Mekanism generator for a Powah one, or any other Forge Energy source, and get the same behavior. Nothing in the seam is Mekanism-specific. Add a second tank holding a hotter sample and only the hotter one pays.
9. Turn every compat toggle off and reload. Recipes are absent, capabilities are not attached, the plugins are inert, and the world still loads. The socketed tool, the affixed tool, the module-hosting evolved gear and the fusion-upgraded tool all keep their components untouched and inert. Turn the toggles back on and every one of them works again, with nothing lost.
10. Save, restart, reload: socket contents, gem bonuses, affix state, module state, fusion upgrades and tool levels all survive.

The guide book opens and covers the sockets, the affix and enchanting rules, and the heater. No GuideME.

### Content manifest

| Kind | Contents |
| --- | --- |
| Modifier (D-M8-1) | `forgeweave:socketed`, level = socket count, applied at the Tool Station like any modifier and consuming a slot per level. Its datapack application recipe carries an explicit level cap. |
| State (D-M8-1) | One new data component holding the gem stack per socket index. **Absent = no sockets**, so no migration and no backfill: tools built before M8 start with none. Affix state is **not** Forgeweave state: it stays on Apotheosis' own components, uncopied and unmirrored (JC-D). |
| Block (D-M8-3, replaced by D-M8-11) | ~~Seared heater wall~~ — the **energized tank**: a smeltery wall tank holding an unconsumed one-bucket fuel sample plus a Forge Energy buffer. The sample's fluid sets the temperature; the buffer pays `rfPerMeltTickBase x temperature / 1000` per melt tick. Numbers, the hottest-tank rule, the overdrive button and the empty-buffer case are on [#972](https://github.com/gkissel/forgeweave/issues/972). Texture derived from the 1.12 clone with a `NOTICE.md` row if upstream art fits, freshly authored otherwise. |
| Items (D-M8-6) | The full form set on every Forgeweave material that has an ingot: ingot, nugget, storage block, dust, small dust, tiny dust, plate, double plate, rod, gear, wire. Gem-type materials (brimspar, fulmenite) get dusts only. Sprites come from the existing vanilla-donor recolor scripts, not hand art. |
| Metal (D-M8-13) | `atomic_matter_alloy`, made only in Mekanism's Antiprotonic Nucleosynthesizer, carrying the `infused` trait at one level and sitting on the `resonite` harvest rung. |
| Modifiers (D-M8-15, D-M8-13) | `surgebound` I-V, applied in Powah crystal order, +25% energy capacity and +5% mining speed a level with the nitro step doubling both. A partial radiation-shielding modifier for any armor, levels 1-4 = 25-100%, fed by `c:ingots/lead`. |
| Config (D-M8-8) | `config/forgeweave/` as a folder of files (compat, smeltery, leveling, materials, ...) rather than one flat file. Every number in it is a config value; every integration beyond materials has a toggle. |
| EMI (D-M8-2) | A spike table covering all 14 recipe types across the 12 category classes through EMI's JEI-plugin bridge, then a native plugin for exactly the categories the bridge fails. If the bridge carries everything, the plugin is one paragraph saying so. Spike posted 2026-09-18 ([#971](https://github.com/gkissel/forgeweave/issues/971) comment, from reading EMI's bridge source, not a live client): the bridge forwards every category's real draw/transfer/catalyst/subtype/exclusion-area code, so no plugin ships. Live-client confirmation stays an open release-checklist line. |
| Config (D-M8-5) | Eleven toggles in a new `compat` section of `ForgeweaveConfig`, all default `true`, all read through `ForgeweaveConfig.enabled(...)`: Apotheosis sockets, Apotheosis affixes, Apotheosis enchanting, the EMI plugin, the FE heater, Draconic fusion recipes and upgrades, Draconic module hosting, Jade and WTHIT overlays, the KubeJS trait binding, datapack modifier definitions. |
| Datapack (M8-5, confirmed 2026-09-18) | `forgeweave:modifier_definition`, shaped exactly like M6's `trait_definition`: a `behavior` field dispatching over one codec per library class, `neoforge:conditions` gating, built-in ids winning collisions, serialization staying `modifier id + level` per ADR-0004 item 2. Closes ADR-0004 item 3. |
| Book | Pages for the sockets, the affix and enchanting rules, and the heater. The existing book stays; GuideME is a non-goal (M8-6, confirmed 2026-09-18). |

Already shipped under this milestone before the planning session ran, and not relitigated here: the JEI plugin and its 14 recipe types across 12 category classes, one for every recipe type Forgeweave has (`ForgeweaveJeiPlugin` plus 43 sibling classes, with catalysts, subtype interpreters, GUI handlers and transfer handlers); Jade and WTHIT overlays for casting cooling and smeltery contents; the KubeJS trait binding delivered early at M6 by [#832](https://github.com/gkissel/forgeweave/issues/832); Draconic Evolution fusion metals and the 8-line by 4-tier upgrade ladder ([#915](https://github.com/gkissel/forgeweave/issues/915), [#946](https://github.com/gkissel/forgeweave/issues/946), [#961](https://github.com/gkissel/forgeweave/issues/961), [#965](https://github.com/gkissel/forgeweave/issues/965)); the Draconic material roster ([#953](https://github.com/gkissel/forgeweave/issues/953)); Draconic module hosting and the tool-active module effects ([#956](https://github.com/gkissel/forgeweave/issues/956), phases [#962](https://github.com/gkissel/forgeweave/issues/962) and [#963](https://github.com/gkissel/forgeweave/issues/963)); Track A existence-gated smeltery presets across 13 provider mods and 47 material ids (`scripts/_compat_smeltery_data.py`); and Track A melting temperatures placed on the fuel ladder ([#954](https://github.com/gkissel/forgeweave/issues/954)).

### In scope (systems)

**D-M8-1 — Apotheosis integration ships in full: sockets, affixes and enchanting.** Gem sockets are a Forgeweave modifier named `socketed` whose level is the socket count, not a parallel item system. Gems apply their bonuses through the existing modifier and trait seams (`CombatSeams`, the mining-speed and defense hooks, the attribute pipeline) rather than through a second stat path, so a gem that grants attack damage adds to the same quantity a trait does. The mapping from Apotheosis gem effect to Forgeweave quantity is written down on [#969](https://github.com/gkissel/forgeweave/issues/969), including which effects are deliberately unmapped; an unmapped effect grants nothing rather than getting a seam invented for it. Forgeweave tools and armor are eligible for Apotheosis loot affixes, and how a loot-generated tool gets its material and parts is decided on [#970](https://github.com/gkissel/forgeweave/issues/970) rather than left ambiguous. Apotheosis enchanting works through the existing top-level `allowVanillaEnchanting` flag, which the integration toggle does not replace: enchanting needs both.

**Answered on [#970](https://github.com/gkissel/forgeweave/issues/970) (2026-09-18).** A loot-generated tool gets its parts from a Forgeweave-side loot function, `forgeweave:assemble_tool`, which is the third of the three options the issue offered and the only one that can be built: no vanilla loot function can write Forgeweave's material components, so a fixed material per loot table and a random pick from a tag are both spellings of this one field. It takes a list of material ids and picks one per roll for every part slot, so a single-element list is the fixed-material option and a longer one the random pick. Forgeweave ships no loot table that uses it: which chests hold Forgeweave gear is a pack's decision. Affix eligibility itself needs nothing registered, because Apotheosis decides it by a predicate walk over the item and Forgeweave's shapes already satisfy it — every one except the ranged family, whose items are not vanilla `BowItem`s and are corrected by three rows in an `apotheosis:loot_category_overrides` data map. `ApotheosisAffixes` holds the per-shape reading and the two toggles.

The interactions are specified, not discovered. Sockets consume modifier slots like any modifier and compete for the same pool. A slot earned through M7 leveling can be spent on `socketed`. Gem bonuses stack additively with trait and modifier bonuses of the same kind. An enchantment consumes no modifier slot and coexists with all three. Affix state stays Apotheosis' own data (JC-D): Forgeweave neither copies nor migrates it, and the only thing Forgeweave can break there is the decode, which the fixture below pins.

**D-M8-2 — EMI gets a spike before it gets a plugin.** Install EMI, walk all 14 recipe types across the 12 category classes through EMI's JEI-plugin bridge, and record for each whether it renders correctly (including the custom drawing in `JeiCategoryChrome`, `JeiCategoryGeometry` and `ModifierPanel`) and whether its transfer handler survives. Write a native EMI plugin only for the categories that fail. A second full plugin for categories the bridge already carries is work with no player-visible result, and the spike costs an afternoon. If the bridge covers everything, that outcome closes [#971](https://github.com/gkissel/forgeweave/issues/971) as a success. This closes the standing "EMI support vs. JEI-only long-term" open question.

**D-M8-3 — the smeltery heat seam is Forge Energy, not a per-mod block.** A seared heater wall accepts Forge Energy through the standard block capability and acts as a fuel rung whose temperature scales with RF/t across the tiers [#954](https://github.com/gkissel/forgeweave/issues/954) already established. Mekanism, Powah, and anything else exposing the capability drive it identically. **There is no Create blaze burner seam**: one block against a standard capability beats a growing family of per-mod blocks, and the family only ever grows. This delivers the deferred-backlog line on electric and tiered smeltery heating.

> **Superseded 2026-09-07 by D-M8-11.** The heater block never shipped. The energized tank takes its place: same mod-agnostic Forge Energy seam, but the temperature comes from a fuel sample the tank holds rather than from an RF/t curve, and the energy is what buys melt ticks. The reasoning that made D-M8-3 a capability rather than a per-mod block still stands and carries over unchanged; only the block does not. [#972](https://github.com/gkissel/forgeweave/issues/972) was rewritten in place rather than closed and refiled.

**D-M8-4 — Curios is deferred, and stays deferred.** No Forgeweave item needs an extra equipment slot today. Armor occupies vanilla slots, gadgets are held or worn as boots. Curios becomes worth doing when a back or charm item exists to put in a slot, and not before; wiring the API for zero items is a dependency bought with nothing to spend it on. Until then it is a non-goal, not a task.

**D-M8-5 — every integration beyond materials gets its own server config toggle** (maintainer decision 2026-09-04). Eleven toggles in a new `compat` section of `ForgeweaveConfig`, following the family-toggle idiom `HARVEST_TOOLS`, `MODIFIERS` and `TOOL_LEVELING` set, read through `ForgeweaveConfig.enabled(...)` and never `.get()`. They live in `compat` rather than `content` because `content` means "this family of Forgeweave items exists" while these mean "this bridge to another mod exists".

Off means the integration registers nothing: recipes absent, capability not attached, plugin inert. Off never breaks a save. Gear that already carries sockets, modules or fusion upgrades keeps its components untouched and inert until the toggle returns, which is D-M7-3's rule applied to compat for the same reason: a flag flip that silently discards state is a save-corruption bug wearing a config's clothes. Each integration ships its toggle and its config-off test in its own PR (JC-C), modelled on `ContentFamilyGameTests`; [#968](https://github.com/gkissel/forgeweave/issues/968) backfills the four integrations that shipped before this decision existed.

Track A material presets are **not** toggled. They stay active whenever the source mod's item exists, because a preset that vanishes takes a material out of a world that was built with it, and existence gating already does the job a toggle would.

**JC-A through JC-D, resolved at planning.** No code is derived from any integrated mod; the `compileOnly` plus isolation-test pattern applies to all of them (JC-A). Compat mods are not on the GameTest classpath, so M8's automated coverage tests Forgeweave's own seams and config-off behavior while anything needing a live Apotheosis or EMI instance is a manual checklist line (JC-B). Config-off tests are per-PR, not a sweep at the end (JC-C). Affix state belongs to Apotheosis (JC-D).

### Maintainer decisions of 2026-09-06 (Draconic Evolution slice)

- **Welds host modules, cores take fusion upgrades.** A tool with a fusion-metal part (duskweld, emberweld, starweld, voidweld) is a Draconic module host and fusion crafting refuses it; a tool with a Draconic core part (draconium core, wyvern, awakened, chaotic) takes fusion upgrades and hosts no modules. The tier still comes from the `evolved` ladder; which of the two a tool gets comes from the second trait its material grants (`ForgeweaveDraconicCompat#WELD_MARKERS` / `#CORE_MARKERS`). A mixed tool gets both.
- **Chaotic hurts the Chaos Guardian's crystals.** A chaotic-tier tool (voidweld or chaotic core) hits a guardian crystal with the `forgeweave:chaotic_strike` damage type, tagged into DE's `chaotic` damage tag, so DE's own crystal shield rule lets the hit through.
- **Shieldbreaker.** The awakened core's second trait: each hit drains four times the wielder's attack damage out of a Draconic shield the target wears.

### Maintainer decisions of 2026-09-07 (second planning session)

A second grilling reopened M8 and widened it well past the four systems the first session planned. D-M8-1 to D-M8-5 stand as written. Everything below is new, and where it contradicts an earlier decision it says so.

The shape of the widening: Forgeweave stops being a mod that other mods can melt and starts being a mod other mods can *process*. That means item forms other machines already know how to consume, `c:` tags they look those forms up by, and recipes in their own recipe types. Almost none of it is new Forgeweave gameplay. It is surface area.

#### Foundations

**D-M8-6 — every material with an ingot gets the full form set, and full `c:` tags.** Ingot, nugget, storage block, dust, small dust, tiny dust, plate, double plate, rod, gear, wire. That covers Track B's ore and alloy rosters, the four fusion welds, `atomic_matter_alloy` (D-M8-13), and the ten Forgeweave metals that already own an item (cobalt, ardite, manyullyn, rose gold, steel, knightslime, pig iron, amethyst bronze, queen's slime, hepatizon). Gem-type materials get dust, small dust and tiny dust only: brimspar, which has no ingot at all, and fulmenite, whose ore drops a crystal. Fluids stay molten-only; no liquid or gas forms.

Every form goes into its `c:` tag: `c:ingots/`, `c:nuggets/`, `c:storage_blocks/`, `c:raw_materials/`, `c:dusts/`, `c:small_dusts/`, `c:tiny_dusts/`, `c:plates/`, `c:double_plates/`, `c:rods/`, `c:gears/`, `c:wires/`, plus the parent tag in each family the way `ForgeweaveItemTagsProvider` already extends `c:storage_blocks`. Fluids get a `FluidTagsProvider` of their own on the same convention, so a molten Forgeweave metal is discoverable as a molten metal rather than by id.

Melting reads the tag, not the item. A dust melting recipe keys off `c:dusts/<id>`, so another mod's dust of the same material melts in a Forgeweave smeltery without a second recipe. That is the same call the M2 smeltery already made for ores and ingots, applied one layer further out.

Plates, double plates, rods, gears and wires exist **for other mods to consume**. Forgeweave adds no recipe that uses one. They are output, not currency. Track A materials get no forms at all: their own mods own those items, and minting a second copy of another mod's plate is how a modpack ends up with four incompatible steel plates.

All sprites come from the existing vanilla-donor recolor scripts (`generate_track_b_ore_textures.py` and the pipeline built on it), not from hand art. A dozen forms across three dozen materials is not a drawing job.

**D-M8-7 — the ten own-item materials and the four welds get the `c:` tags they are missing today.** Cobalt and ardite have ores and storage blocks tagged but no plate or dust family, because neither existed. The welds have nothing. This is the same work as D-M8-6 and ships in the same pass rather than as a follow-up nobody files.

**D-M8-8 — config becomes a folder.** `config/forgeweave/` holding separate files (compat, smeltery, leveling, materials, and whatever else the split falls out to) instead of one growing file. Two standing rules come with it. Everything numeric is a config value, not a constant in Java. And every integration beyond materials has a toggle, which is D-M8-5 restated now that the integration count has roughly tripled. Materials are never toggled, for D-M8-5's own reason: a preset that vanishes takes a material out of a world built with it.

**D-M8-9 — Jade and WTHIT show mining level.** The looked-at block's required level and the held tool's level, side by side, so a player can see why a pick bounces off an ore instead of guessing. This matters more now than it did at M6: Track B put three rungs above netherite and nothing in the game explains them.

**D-M8-10 — no new harvest tiers.** The welds, the Draconic cores and `atomic_matter_alloy` all sit on `resonite`, the existing top rung. A fourth rung above it would need its own `incorrect_for_*_tool` tag, its own block tagging pass, and its own explanation to the player, in exchange for ordering four materials that nothing needs ordered.

#### Smeltery

**D-M8-11 — the energized tank replaces the Forge Energy heater of D-M8-3.** A smeltery wall tank that holds a one-bucket fuel sample and a Forge Energy buffer. The sample is never consumed: it is a sample, and it says which fuel the tank is imitating. That fluid's own temperature is the tank's temperature. Energy is what actually burns: the buffer pays `rfPerMeltTickBase x temperature / 1000` per melt tick, both numbers config.

Among all tanks on a smeltery the hottest valid fuel wins, and only that tank pays. Cooler tanks contribute nothing and spend nothing. A tank with an empty buffer contributes no heat, which is the same "no partial heat" rule D-M8-3 already set.

An overdrive button, its state saved with the block entity, multiplies both the energy cost and the melt progress per tick by config factors, both defaulting to 2.0. Faster melting, proportionally more expensive, and the player chooses.

**The tank is a GUI block (maintainer decision 2026-09-18, [#1018](https://github.com/gkissel/forgeweave/issues/1018)).** [#972](https://github.com/gkissel/forgeweave/issues/972) read "a button on the block" literally and shipped overdrive as an empty-hand press with action-bar feedback. That is overruled: right-clicking the tank with anything but a fluid container opens a screen showing the fuel sample as a gauge, the Forge Energy buffer as a bar with its numbers, the heat the sample gives, what a melt cycle costs, and the overdrive button. A bucket still fills and drains the sample without opening it. The saved overdrive flag and the block entity's NBT are unchanged, so the format the fixture pins is the same one.

It gets a JEI row, a mention in the guide book, and a toggle. Why this and not the heater: an RF/t curve is a second temperature scale a player has to learn, while a fuel sample is a fuel they already know, read through a meter they already have.

**The numbers, settled while building it ([#972](https://github.com/gkissel/forgeweave/issues/972), 2026-09-18).** All six live in a new `compat` section of `ForgeweaveConfig`, and every one of them is pinned by `EnergizedHeatTest`.

| Option | Default | What it means |
| --- | --- | --- |
| `energizedTank` | `true` | The D-M8-5 toggle |
| `energizedTankBuffer` | `100000` FE | About two minutes of melting at lava's heat |
| `energizedTankRfPerMeltTickBase` | `100` | The `rfPerMeltTickBase` of the cost |
| `energizedTankTemperatureDivisor` | `1000` | The divisor, so the base is the price at 1000 degrees |
| `energizedTankOverdriveCost` | `2.0` | What overdrive multiplies the cost by |
| `energizedTankOverdriveProgress` | `2.0` | What overdrive multiplies melt progress by |

At those defaults one melt tick costs 130 FE on a lava sample, 150 on blazing blood, 170 on molten magma, 190 on brimspar and 210 on pyrealloy. A melt tick runs every four game ticks, so lava works out at roughly 33 FE per game tick.

Four more answers the issue asked for, all settled the same way:

- **It competes with a lit fuel below the smeltery rather than replacing it.** The smeltery runs at whichever of the two is hotter, and only the winner spends anything. A tie goes to the tank.
- **An invalid sample is refused at the fill**, not accepted and ignored, so a player pouring water in finds out at the moment they try.
- **A hottest tank with an empty buffer falls through to the next one down.** It is not heating, so it is not the hottest heat source, and a spare tank behind it is what a player built it for.
- **Overdrive is a property of the paying tank only.** It never stacks across tanks: a second tank idling in the wall with its button pressed changes nothing.

Two more, forced by how the mod is actually built: an energized tank counts as a wall tank on its own, so a smeltery heated entirely by energy forms with no seared tank in its walls; and the toggle makes the block **dormant, not unregistered**, because a server config is not loaded when registries freeze. A dormant tank keeps its sample, its buffer and its overdrive setting and heats nothing, which is D-M8-5's inert-not-destructive contract.

**D-M8-12 — the basic alloys are craftable in other mods' machines.** An alloy qualifies as basic when it takes ingot plus ingot, no catalyst fluid and no fuel material: manyullyn, alumite, rose gold and pig iron. Those get `mod_loaded`-gated recipe JSON for Create's heated mixer, Immersive Engineering's arc furnace and EnderIO's alloy smelter. Alloys that need a catalyst or a fuel stay smeltery-only, because those are the recipes the smeltery is actually for. Nobody should have to build a smeltery to get rose gold if they already run an alloy smelter, and nobody should be able to skip the smeltery for the alloys that are its point.

**D-M8-13 — Forgeweave's molten fluids are Powah thermo generator heat sources.** Registered through Powah's `powah:heat_source` fluid data map: molten magma 1700, brimspar 1900, pyrealloy 2100, and the rest of the ladder in step. The fuel ladder already ranks these fluids by heat; this exposes that ranking to a mod that pays for heat.

**D-M8-14 — [#986](https://github.com/gkissel/forgeweave/issues/986) stays parked.** The tiered smeltery automation block is a planning note, not M8 work. No milestone picks it up here.

#### Mekanism

**D-M8-15 — one metal, one module container, and the ore chains.** Mekanism is MIT and its API publishes to modmaven.dev (`mekanism:Mekanism:1.21.1-10.7.19.85:api`), so the usual `compileOnly` seam applies without a license question.

*The metal.* `atomic_matter_alloy`, made only in the Antiprotonic Nucleosynthesizer (`mekanism:nucleosynthesizing`, atomic alloy plus antimatter, amounts config). It carries `infused` at one level and sits on the `resonite` rung per D-M8-10. One metal, not a roster: the point is the module container, and the metal is how a player earns it.

*The container.* A tool with an `atomic_matter_alloy` part is a Mekanism module container, and the same thing a MekaTool is: `MekanismIMC.addModuleContainer` with `ADD_MEKA_TOOL_MODULES`, and `IModuleHelper.applyModuleContainerProperties` called at registration through a compat item factory, so the plain `ToolItem` stays Mekanism-free. Armor with `atomic_matter_alloy` plating gets the MekaSuit module set for its slot. Energy runs through the [#830](https://github.com/gkissel/forgeweave/issues/830) `EnergyBuffer` acting as the container's energy, the same way the Draconic module layer already spends it.

*Upgrades are never lost to a part swap* (maintainer rule, 2026-09-18, binding on every integration whose upgrades or modules Forgeweave gear hosts). If replacing a part makes an installed upgrade invalid, the upgrade turns back into items and the player gets them, into the inventory or dropped at their feet the way the station already returns a displaced part. It is never silently dropped and never stranded on gear that cannot use it. The seam is `dev.gkissel.forgeweave.tool.UpgradeHosts` in Forgeweave core, asked once from `ToolAssemblyRecipes#resolveExchange` and answered by a handler each `compat/<mod>/` package registers behind `ModList.isLoaded`; nothing in core names a partner mod's type. For Mekanism the trigger is losing the metal, since Mekanism decides which modules an item accepts per item rather than per stack. A handler whose own toggle is off returns nothing and strips nothing, because off is inert rather than destructive. Draconic module hosting predated the rule and now carries it too ([#1033](https://github.com/gkissel/forgeweave/issues/1033)): losing the weld part returns every installed module, and a swap to a lower weld returns only the modules that no longer fit the new tech level or grid, reusing Draconic Evolution's own `ModuleEntity#isPosValid` bounds check rather than inventing a packing rule. The same issue audited the other four shipped integrations for the same gap and none needed a handler: Apotheosis sockets, the Draconic fusion upgrade ladder, the Create goggles modifier and Mystical Agriculture augments all store their state as either a plain Forgeweave modifier entry, which rides `toolStack.copy()` untouched and is rejected outright rather than silently stripped when a swap would leave it over its slot budget, or, for augments, an `ITinkerable` slot count that is a constant of the Java item class rather than of the stack, so a part swap can never change what Mystical Agriculture believes the tool can still hold.

Every effect is replicated in Forgeweave's own hooks rather than by running Mekanism's, exactly as [#956](https://github.com/gkissel/forgeweave/issues/956) did for Draconic, and in the same two phases. Phase 1: the container, the Modification Station GUI, the free `ICustomModule` hooks, silk touch and fortune through `getAllEnchantments` delegating to the container, excavation escalation, vein mining (`ModuleVeinMiningUnit.findPositions` called from `mineBlock`), blasting mapped onto Forgeweave's own area sweep, MekaSuit damage absorption (the `getDamageAbsorbed` orchestration reimplemented as an armor trait that pays energy), and radiation shielding through the `IRadiationShielding` capability at 100% on the metal. Phase 2: teleportation on `use`, farming and shearing on `useOn`, and the jetpack, gravitational modulation and elytra modules.

*Radiation, for everyone else.* A partial radiation-shielding modifier applies to any armor, levels 1 to 4 giving 25% to 100%, consuming `c:ingots/lead` and costing a modifier slot. Its name comes from the existing trait naming family, not from Mekanism's vocabulary.

*The ore chains.* Explicit Mekanism 2x to 5x recipes for the eleven Track B ores: crushing, enriching, purifying and injecting, generated by script the way the Track B recipes already are. The outputs are Forgeweave's own dusts and clumps as each chain step needs, which is where D-M8-6's dust forms get consumed and why the two are ordered.

*What phase 2 actually shipped* ([#994](https://github.com/gkissel/forgeweave/issues/994), 2026-09-18). The seven modules phase 1 deferred are wired and none is left marked deferred: teleportation on `ToolItem#use`, farming and shearing on `useOn` and `interactLivingEntity`, attack amplification on the attack path, the elytra unit on `canElytraFly`, gravitational modulation through `CreativeFlightHandler`, and the jetpack on the worn `inventoryTick` running Mekanism's own `IJetpackItem.handleJetpackMotion`. The radiation modifier is `forgeweave:rayward`, four levels of `c:ingots/lead` at a slot each, a quarter of the shielding a level, config-tuned; it rides the `modifiers` content toggle rather than `mekanismModules`, because it is a Forgeweave modifier that a mod modelling radiation happens to read. The ore chains needed three forms M8-8 did not ship -- clump, dirty dust and shard, added to the eleven Track B ores with their `c:` tags -- and no crystal: the 5x chain runs through a slurry, a chemical form of a Forgeweave metal, which D-M8-6 already rules out, so the chains stop at the four recipe types named above.

#### The processing mods

**D-M8-16 — Create, Immersive Engineering and EnderIO get recipe JSON, not code.** Create 6.0.x: the heated mixer for D-M8-12's basic alloys, crushing wheels turning Track B ores into dust, pressing turning our ingots into plates and rods, and a deployer accepting a Forgeweave tool as a checklist line. **No Create part factory**: Forgeweave parts are made at Forgeweave stations. Immersive Engineering: the arc furnace for the basic alloys (tag-driven, so it picks the ingots up by `c:ingots/`), crusher recipes to dust, and metal press plate recipes by tag. EnderIO (`com.enderio:enderio:8.2.11-beta`, maven.rover656.dev): the alloy smelter for the basic alloys and the SAG mill for ores.

All of it is `mod_loaded`-gated JSON generated from the same material tables the smeltery recipes come from. None of it is a Java seam, which is why it is one issue and not four.

**D-M8-17 — Powah materials and `surgebound`.** Powah is LGPL-3.0, so API only and nothing derived. Its materials become Track A presets with traits: uraninite, energized steel, and the blazing, niotic, spirited and nitro crystals. `surgebound` is a modifier applied in crystal order (energized steel, then blazing, niotic, spirited, nitro), one slot a level, +25% energy capacity and +5% mining speed a level, with the nitro step doubling both. Numbers are config. D-M8-13's heat sources ship alongside it.

**D-M8-18 — Occultism.** MIT, from `dl.cloudsmith.io/public/klikli-dev/mods/maven`. Materials: iesnium, silver and spirit attuned gem as Track A presets. `occultism:crushing` recipes keyed by `c:ores/<id>` and `c:raw_materials/<id>` with `min_tier` set from the ore's own tier. `occultism:miner` recipes so the mining spirits can find Track B ores, weighted by tier. And a Forgeweave recipe type implementing Occultism's ritual interface that applies a modifier to a tool placed as the ritual item: the Occultism analogue of the Draconic fusion upgrade, and the second instance of the pattern `FusionUpgradeRecipe` established.

**D-M8-19 — Allthemodium and Elementarium.** Allthemodium has no license, so tags only and nothing derived, ever. Allthemodium, vibranium, unobtainium and the three alloys become Track A presets gated by item. Tier equivalence runs through tags in both directions: allthemodium sits with hardcinder, vibranium with warspar, unobtainium with resonite, so our tools mine their ores and theirs mine ours. Track B ores also generate in `allthemodium:mining` through a `neoforge:add_features` biome modifier targeting `#allthemodium:mining_features/mining_biomes`, `mod_loaded` gated, which is the same biome-modifier shape `generate_track_b_worldgen.py` already emits for the Nether and the End.

Elementarium is MIT but closed source. Its metals become Track A presets generated by script from its `c:` tag families with interpolated stats. Registration keys on tags only. Its `config/elementarium/minerals/*.json` route is noted as a reserve if the tag route turns out to be too coarse.

**D-M8-20 — Mystical Agriculture.** MIT, from maven.blakesmods.com (`com.blakebr0.mysticalagriculture:MysticalAgriculture`, which needs Cucumber). Three pieces.

Crops, registered in Java through `IMysticalAgriculturePlugin` and `ICropRegistry.register` against a `compileOnly` API, for the eleven Track B ores and for the fuel materials. Essence tier follows mining level: iron maps to prudentium, diamond to tertium, netherite to imperium, and hardcinder, warspar and resonite to supremium, with awakened supremium for the top. A crop's crux is the material's own storage block. Which fuel materials get a crop depends on which of them have an item form at all, and that answer is settled on the issue rather than assumed here.

Mystical Agriculture's own metals become Track A presets: prosperity, soulium, the inferium through awakened supremium ladder, and insanium where Agradditions supplies it.

Augments. A tool or armor with a part made of a Mystical Agriculture essence metal becomes `ITinkerable`, so Mystical Agriculture's own Tinkering Table accepts it. That happens through a compat subclass of `ToolItem` and the armor items, instantiated by a registration factory only when Mystical Agriculture is loaded, so the base classes stay clean. Tier comes from the material, one augment slot, two for awakened.

**D-M8-21 — Just Dire Things and Eternal Ores join Track A (issue #1031, M8-16).** Asked for by the maintainer on 2026-09-18, after the planning sessions above, so it carries no earlier decision of its own.

*Just Dire Things* (Direwolf20-MC/JustDireThings, MIT, verified at tag `v1.5.7`/commit `8390e0e`, the newest tag actually built for `minecraft_version=1.21.1`). Its four tool tiers become Track A presets, each keyed on its own concrete ingot or gem id: `ferricore` (iron-equivalent, `justdirethings:ferricore_ingot`), `blazegold` (diamond-equivalent, `justdirethings:blazegold_ingot`), `celestigem` (diamond-equivalent, `justdirethings:celestigem` -- a gem with no ingot, raw-material or fluid form on the mod's own tree, so it is Part Builder only, the same shape `blazing_crystal` already ships) and `eclipsealloy` (netherite-equivalent, `justdirethings:eclipsealloy_ingot`). No nugget item exists for any of the four, and the mod's `c:storage_blocks` tag is one flat family shared by all four tiers (plus vanilla charcoal) rather than four per-material subtags, so each tier's storage-block row keys on the mod's own concrete block id. Each tier's Forgeweave trait is a datapack `trait_definition` over an existing `TraitBehaviors` class chosen to echo that tier's own identity in the source mod, never a copy of its code: `ferricore_footing` (`movement_bonus`, step height) for the entry tier's ability-through-durability, no-battery kit; `blazegold_ember` (`damage_type_immunity` against fire) for its fire-resistant items and lava-repair/auto-smelt kit; `celestigem_charge` (`energized`) for the mod's first Forge-Energy-battery tier; and, on the top tier, both `eclipsealloy_charge` (`energized`, the roster's largest capacity) and `eclipsealloy_ward` (`death_save`) for its largest battery and its death-protection ability.

*Eternal Ores* (Catalyst-Studios/Eternal-Ores, all rights reserved for code and assets both, verified against its `main` branch, latest 1.21.1 NeoForge release "2.1-hotfix" 2026-08-14) adds roughly 185 metals and gems and tags most of them into the same `c:` families other mods already use. It ships no new material here: every metal it supplies that Forgeweave already had a preset for is deduped rather than doubled, by widening that preset's existing `neoforge:conditions` (and every melting/casting row that mirrors it) with an `eternalores:<id>[_ingot]` branch -- the same `neoforge:or` shape `lead` and `uranium` already used for their own multiple providers. Nineteen materials: `aluminium`, `bronze`, `constantan`, `electrum`, `graphite`, `invar`, `iridium`, `lead`, `nickel`, `osmium`, `platinum`, `silver`, `tin`, `titanium`, `tungsten`, `uranium`, `uraninite`, `quartz_enriched_iron`, `silicon`. `quartz_enriched_iron` and `silicon` also needed a tag-shape fix, not just a new branch: RefinedStorage ships `quartz_enriched_iron` under no `c:` tag at all (a bare concrete item, now joined by Eternal Ores' own `c:ingots/quartz_enriched_iron` as a second `crafting_items` row rather than a replacement, since `Material`'s lenient item codec only tolerates an unregistered id standing alone); `silicon`'s existing row read the flat `c:silicon` tag, which Eternal Ores does not populate, so its own `c:ingots/silicon` joins as a second row the same way. Every other match (`copper`, `cobalt`, `iron`, `pig_iron`, `rose_gold`, `steel`, `hepatizon`, and the other materials that carry no `neoforge:conditions` block at all) already keyed its ingredient rows on the shared tag Eternal Ores also populates, so none of those needed a change. About 145 further Eternal Ores metals and gems have no Forgeweave equivalent and get no preset here: a full roster expansion is separate, larger work than this issue's dedupe plus a four-material roster.

Stats for the four new materials sit inside the existing Track A envelope at their harvest tier, read off the nearest-tier compat metals already shipped (iron tier: `nickel`/`iron`; diamond tier: `titanium`/`platinum`/`tungsten`; netherite tier: `netherite`/`iridium`) rather than off Just Dire Things' own raw tool-tier numbers, which use a different unit scale:

| Material | Tier | Head (durability / speed / damage) | Enchantability |
| --- | --- | --- | --- |
| `ferricore` | iron | 240 / 6.2 / 3.5 | 12 |
| `blazegold` | diamond | 480 / 7.4 / 5.0 | 22 |
| `celestigem` | diamond | 520 / 6.6 / 5.8 | 16 |
| `eclipsealloy` | netherite | 900 / 7.6 / 6.8 | 15 |

No harvest rung above `resonite` (D-M8-10): `eclipsealloy` sits on the existing `incorrect_for_netherite_tool` rung, matching the mod's own tier. No item forms for either mod's materials (D-M8-6) and neither preset family is ever toggled (D-M8-5). No Java import of either mod: every material and recipe here is existence-gated JSON keyed on a concrete item id, the `PowahGameTests`/`PresetBatch5GameTests` shape.

**D-M8-22 — Just Dire Things upgrades cannot reach Forgeweave gear, and the blocker is upstream** (researched 2026-09-18 against Just Dire Things 1.5.7 for 1.21.1, MIT, `Direwolf20-MC/JustDireThings` branch `1.21.1`). Issue [#1032](https://github.com/gkissel/forgeweave/issues/1032) ordered three routes and route 3 is the one the code supports: the mod recognises only its own item classes and publishes no seam a foreign item can use.

Three facts settle it, all read off the source. An upgrade is installed by `AbilityRecipe`, a `SmithingRecipe` whose `isBaseIngredient` is `stack.getItem() instanceof ToggleableTool` and whose `assemble` repeats that test before writing the `justdirethings:<ability>_upgrade_installed` boolean component. Which abilities an item can take is an `EnumSet<Ability>` field on the item instance, filled by `registerAbility` calls in each item class constructor, so there is no table a third party can add a row to. And the recipes themselves are generated one per own item with `Ingredient.of(tool.get())`, so no tag sits between the recipe and the item list. The consumers agree with the producer: the keybind packets, the settings screen, the tooltip helper, the light-texture mixin and every entry in `AbilityMethods` all gate on `instanceof ToggleableTool`, `ToggleableItem` or `PoweredItem`. There is no API package, no `InterModComms` handler and no registry keyed by item.

Route 1 is therefore closed: a Forgeweave datapack could register a `justdirethings:ability` smithing recipe naming a Forgeweave tool as its base, and `assemble` would still return an empty stack. Route 2 is closed for a different reason. `ToggleableTool` is a published interface and is implementable from outside, but `instanceof` reads the item's own class, so only `ToolItem` and `ArmorPieceItem` could carry it, and #1032's own rule is that Forgeweave's item classes never name the mod. Reaching it would take either a mixin adding the interface to Forgeweave's items or a compat subclass in the shape D-M8-20 uses for Mystical Agriculture, and neither is worth it: an item that satisfies the `instanceof` still gets only the globally hooked abilities, because the rest are wired through each Just Dire Things item's own `mineBlock`, `hurtEnemy`, `useOn` and `inventoryTick` overrides. A tool that accepts every upgrade and honours some of them is worse than one that accepts none.

What the mod would need to expose, in rising order of effort: an item tag for `isBaseIngredient` and `assemble` to test instead of `instanceof ToggleableTool`; a static registry from `Item` to `EnumSet<Ability>` so a foreign item can declare its own ability set, replacing the per-instance field; and ability dispatch moved off the item overrides onto the events that already exist for the passive ones. The first alone would not be enough, since an item with no registered abilities fails `hasAbility`. The first two together would make this integration a few dozen lines.

So nothing ships and nothing is toggled. There is no `justDireThingsUpgrades` flag, because D-M8-5 gives a toggle to an integration and there is no integration to switch off. Nothing is derived from the mod, no material moves (its metals are [#1031](https://github.com/gkissel/forgeweave/issues/1031)'s Track A presets and are unaffected), and no Forgeweave tooltip mentions an upgrade, since a Forgeweave stack can never legitimately hold one.

Three answers are recorded here for whoever picks this up if the mod changes. An installed upgrade costs no Forgeweave modifier slot, for the reason [#1007](https://github.com/gkissel/forgeweave/issues/1007) made the Create goggles slot free: the upgrade is the partner mod's own progression and has already been paid for once. Tier maps by harvest tier rather than by material, since Just Dire Things gates its own abilities by `GooTier` and `ArmorTiers`, which are its four harvest rungs. And an ability that needs energy spends the tool's own `EnergyBuffer` where a material trait gave it one, which needs no new code: `PoweredItem.getAvailableEnergy` and `consumeEnergy` are static and read the plain `Capabilities.EnergyStorage.ITEM` capability, so an energy-backed Forgeweave tool already answers them. Without a buffer the mod's own fallback applies, `ability.getDurabilityCost()` through `Helpers.damageTool`.

**The part-swap return rule** (maintainer rule 2026-09-18, binding on every mod whose upgrades Forgeweave gear can host): if replacing a part leaves an installed upgrade invalid, that upgrade is returned to the player as its item, into the inventory or dropped at the player's feet, never silently dropped and never stranded on gear that cannot use it. Here the rule is live but vacuous: a Forgeweave tool can never carry a Just Dire Things upgrade, so a part swap has no eligibility to break and no item to hand back. Handing an upgrade item back for a component that no supported route could have written would duplicate items. `JustDireThingsIsolationTest#forgeweaveStoresNoJustDireThingsUpgradeState` pins that premise by scanning the source tree for the namespace, and its failure message says that the rule stops being vacuous the moment the scan goes red. The seam itself belongs to whichever integration first has upgrades to lose; [#993](https://github.com/gkissel/forgeweave/issues/993) carries the same rule for Mekanism modules and is the one that needs it.

**D-M8-23 — Better Combat and Epic Fight get movesets, both data-only, no toggle** (researched 2026-09-18: Better Combat `2.3.1+1.21.1-neoforge`, `github.com/ZsoltMolnarrr/BetterCombat` commit `616829c5`, code all-rights-reserved but its datapack schema is public, third-party-facing API surface, same as every vanilla item it ships its own `weapon_attributes` file for; Epic Fight `21.15.6-mc1.21.1-neoforge`, `github.com/Antikythera-Studios/epicfight` branch `1.21.1`, code GPL-3.0-or-later, its animation assets separately all-rights-reserved under `LICENSE-ASSETS`). Neither mod bakes a static damage, speed or range number into these files: Better Combat multiplies whatever the stack's live `attack_damage`/`attack_speed` attributes already are (a temporary attribute modifier around the vanilla attack call), and Epic Fight reads `itemstack.getAttributeModifiers()` directly and layers its own impact/armor-negation stats on top. Forgeweave already computes both per stack (`ToolItem#getDefaultAttributeModifiers`), so both mods see the real numbers with nothing Forgeweave-side to add. That makes this the same shape as a Track A material preset, not a `compat/<mod>/` integration: **existence-gated by construction, no D-M8-5 toggle**, because both mods' own loaders already silently skip a file naming an unregistered item, and neither scans its data folder at all unless the mod itself is loaded -- no `neoforge:conditions` gate is written or needed, and the issue's own carve-out for this case ("data-only integrations get no toggle") applies.

Generated by `scripts/generate_combat_movesets.py` from one mapping table, sibling to the M8-11 processing-recipe generator. Better Combat gets `data/forgeweave/weapon_attributes/<item>.json` (`{"parent": "bettercombat:<preset>"}`) for every Forgeweave weapon and tool; Epic Fight gets `data/forgeweave/capabilities/weapons/<item>.json` (`{"type": "epicfight:<type>"}`) for the same set, plus `data/forgeweave/capabilities/armors/<item>.json` for all eight armor pieces (Better Combat has no armor schema at all). `shuriken` and `arrow` are the two deliberate exclusions: both are `AmmoToolItem`s, thrown or fired rather than swung, and the shuriken's own stat block carries no melee attack attributes to animate in the first place. `GeneratedCombatMovesetTest` re-derives the expected id set from `ToolAssemblyRecipes#ENTRIES` -- the Tool Station's own assembly table -- rather than a second hand-kept roster, so a new tool or weapon issue fails this test until its entry is added.

| Item | Better Combat preset | Epic Fight type | Reasoning |
| --- | --- | --- | --- |
| `broadsword` | `sword` | `sword` | baseline one-handed blade with a wide guard |
| `longsword` | `sword` | `longsword` | Better Combat has no distinct longsword preset; Epic Fight has one by that exact name |
| `rapier` | `rapier` | `dagger` | Better Combat names a rapier preset outright; Epic Fight has none, so its fast, low-cutoff profile fits the low-impact dagger type |
| `dagger` | `dagger` | `dagger` | exact name and shape match on both |
| `scimitar` | `cutlass` | `tachi` | a curved single-edge blade: Better Combat's curved-blade preset, Epic Fight's curved-saber type |
| `katana` | `katana` | `uchigatana` | exact match: Better Combat names it outright, Epic Fight's uchigatana is its katana-style type |
| `cleaver` | `claymore` | `greatsword` | a big blade plus an extra plate on a tough handle reads as a heavy two-handed sword on both |
| `warmace` | `vanilla_mace` | none | `WarmaceItem` delegates its combat hooks straight to vanilla's `MaceItem`, so Better Combat's preset for that exact vanilla item is an exact behavioral match. Epic Fight ships no capability for vanilla's own mace at all, and the maintainer decided on 2026-09-18 that the war mace behaves exactly like vanilla's mace under both mods, so it gets no Epic Fight file. It stays the first candidate for a Forgeweave-owned move set |
| `battlesign` | `staff` | `axe` | a flat implement whose innate blocks and reflects. Epic Fight's `shield` type looked thematic but its own moveset carries no attack combo at all, only block poses (`EpicFightMovesets#SHIELD`), so a main-hand item given it could not attack; `axe`'s real swing is the closest held-weapon animation, and the block/reflect behavior stays Forgeweave's own `DEFLECT` innate through `CombatSeams`, off Epic Fight entirely. Better Combat has no shield-shaped preset either, so `staff` (its closest guard implement) stands in there |
| `frying_pan` | `hammer` | `axe` | a single blunt head on a handle, matching Better Combat's blunt-impact `hammer` preset. Epic Fight's `fist` is bare-knuckle punching, wrong for the same reason it is wrong for the warmace; `axe`'s real one-handed swing is the closest built-in animation |
| `hatchet` | `axe` | `axe` | exact match, single axe head |
| `battleaxe` | `double_axe` | `axe` | two axe heads on a tough rod is literally Better Combat's double-headed-axe preset; Epic Fight has one axe type for the family |
| `lumberaxe` | `heavy_axe` | `axe` | a broad axe head plus a large plate is bigger than the hatchet's single head |
| `mattock` | `axe` | `axe` | its axe head leads the part list and its axe mining tag comes first of its two; no dual-tool preset exists on either mod |
| `kama` | `sickle` | `hoe` | Better Combat names a sickle preset outright; Epic Fight has none, so `hoe` -- the exact tag the kama mines -- is the strongest match |
| `scythe` | `scythe` | `hoe` | exact name match on Better Combat; Epic Fight has no scythe type, so its own mined tag (hoe) stands in |
| `hammer` | `hammer` | `pickaxe` | exact name match on Better Combat; Epic Fight has no hammer type, so its own mined tag (pickaxe) stands in |
| `vein_hammer` | `hammer` | `pickaxe` | same reasoning as the hammer, same mined tag |
| `excavator` | `pickaxe` | `shovel` | Better Combat ships no shovel preset, so its one generic mining moveset is closest; Epic Fight's shovel type matches the mined tag exactly |
| `pickaxe` | `pickaxe` | `pickaxe` | exact match on both |
| `shovel` | `pickaxe` | `shovel` | Better Combat has no shovel preset; Epic Fight's shovel type is exact |
| `shortbow` | `bow_two_handed_light` | `bow` | the fastest-drawing, most mobile launcher; Epic Fight has one bow type for the family |
| `longbow` | `bow_two_handed_heavy` | `bow` | the slowest-drawing bow, no charge speed-up |
| `crossbow` | `crossbow_two_handed_heavy` | `crossbow` | the slowest draw of all three launchers; Epic Fight's one crossbow type is exact |

Armor (Epic Fight only): the four light pieces each carry `weight 0.5`, `stun_armor 0.15`; the four heavy pieces carry both numbers scaled by `ToolConstants#HEAVY_ARMOR_FACTOR` (1.4, the same multiplier the heavy set's own defense already applies) rather than a second, disconnected heaviness constant, so the heavy set is heavier as its own defense already is.

Custom Epic Fight animations for Forgeweave weapons are out of scope (the maintainer wants them eventually, but they need the mod's own armature, exporter and a designer): every row above names a built-in `type`, and a future issue swaps one row's `type` for a Forgeweave-owned moveset id without touching any other row, the table's mapping shape or this test. Neither mod's own animation or model assets are derived; only its public, third-party-facing data schema is targeted. The warmace is the first candidate for one: Epic Fight has no mace moveset at all, so `axe` is a workable stand-in rather than a real match, and a Forgeweave-owned smash animation would fit the vanilla-mace delegation `WarmaceItem` already carries better than any built-in type can.

Confirmed, but not confirmed for the specific packs the maintainer plays: [docs/research/atm10-compat-survey.md](research/atm10-compat-survey.md), read for this issue, names neither Better Combat nor Epic Fight anywhere in either pack's full dependency listing (ATM10 or All the Mons). That survey is a real primary-source read of both packs' CurseForge dependency pages, so this integration lands as general-purpose 1.21.1 NeoForge coverage rather than as a confirmed fix for a modpack the maintainer is known to run; a #975 checklist line still calls for hands-on confirmation with each mod installed.

**D-M8-24 — Track A presets for Silent Gear, PneumaticCraft: Repressurized, Forbidden and Arcanus, The Aether and L_Ender's Cataclysm (issue #1058, M8-19).** Maintainer decision, 2026-09-18, picking from `docs/research/atm10-compat-survey.md`. Same shape as D-M8-21: materials that exist only when the partner mod is installed, built from that mod's own items, pure data gated by `neoforge:conditions`. No item forms (D-M8-6), no toggle (D-M8-5), no Java import of any of the five mods, no rung above `resonite` (D-M8-10).

*Silent Gear* (`SilentChaos512/Silent-Gear`, MIT, branch `1.21.1` commit `aa936c3ec4d54ccec0fad178488b60fae0243543`). Five metals, all living in the base mod rather than a Metalworks or Silent's Gems compat pack, each with a real `c:ingots`/`c:nuggets`/`c:storage_blocks`/`c:dusts` tag family: `crimson_steel` (netherite tier, the mod's own `flame_ward`/`magmatic` traits), `azure_silver` (diamond tier, the roster's one ore-sourced metal, the mod's own `moonwalker` trait), `azure_electrum` (netherite tier, `light`/`accelerate`), `blaze_gold` (iron tier, `fiery`), `tyrian_steel` (netherite tier, the roster's toughest metal, `void_ward`). This is materials only, not an integration with Silent Gear's own tool system, which stays a recorded skip: Silent Gear is itself a competing material-based tool/armor mod, corrected below.

*PneumaticCraft: Repressurized* (`TeamPneumatic/pnc-repressurized`, GPL-3.0, branch `1.21` commit `824ad514ecbdcc02cca59deccecefaa0a9b913a0`, `gradle.properties` pins `minecraft_version=1.21.1`). `compressed_iron` (`pneumaticcraft:ingot_iron_compressed`), tagged `c:ingots`/`c:gears`/`c:storage_blocks`, no nugget or dust tag, no ore or raw form. The mod ships no `ToolMaterial` for it, only an `ArmorMaterial` (defense `[2, 5, 6, 2]`, toughness `1.0f`, durability factor `24`, between vanilla iron's `15` and diamond's `33`), so Forgeweave places it at the iron tier by stat comparison rather than a ported tool number.

*Forbidden and Arcanus* (`stal111/Forbidden-Arcanus`, all rights reserved, branch `1.21.1` commit `34f83feb76204fd773e1d2e1d69ecc5b2f2bb933`). The survey's "aetherium" name does not exist in the mod's current 1.21.1 tree; its real top metal is `deorum` (`forbidden_arcanus:deorum_ingot`, tagged `c:ingots`/`c:nuggets`/`c:storage_blocks`, no ore or raw form), reinforced into `ModTiers#REINFORCED_DEORUM` (`uses=2561, speed=9.0F, damage=3.5F, enchantmentValue=26`) for the mod's top tool tier, one step under its dragon-scale `DRACO_ARCANUS` tier (not an ingot metal, out of scope here). Netherite tier.

*The Aether* (`The-Aether-Team/The-Aether`, LGPL-3.0, branch `1.21.1-develop` commit `e07d30e16fbd0f09cc067b39594035e395dcf996`). Ships no `c:` tags for any of the three items, only its own `aether:` namespace tags, so all three key on a concrete item id: `zanite` (`aether:zanite_gemstone`, iron tier, `AetherItemTiers.ZANITE` `uses=250, speed=6.0F, damage=2.0F, enchantability=14`), `gravitite` (`aether:enchanted_gravitite`, diamond tier, `AetherItemTiers.GRAVITITE` `uses=1561, speed=8.0F, damage=3.0F, enchantability=10`, the metal behind the Aether's own flight-granting Gravitite Boots), `ambrosium` (`aether:ambrosium_shard`, iron tier by placement -- the mod gives it no tool tier of its own, only a healing/light-themed utility role).

*L_Ender's Cataclysm* (Modrinth `l_enders-cataclysm`, CC-BY-NC-ND-4.0, the strictest license in this batch). The mod's public source tree (`lender544/new1.20.1`) has not been ported past 1.20.1 Forge, so item ids and tag coverage were read from the shipped jar itself (`L_Ender's Cataclysm 1.21.1-3.33.jar`, NeoForge, published 2026-08-22, the latest 1.21.1 NeoForge release at research time); stat numbers came from the mod's own `Armortier` enum on its still-public 1.20.1 source (commit `6dd12cb6b44965ef5157091b6962d9d13d7fc099`), read rather than guessed, since Cataclysm's own armor numbers have carried forward unchanged across past version ports. `ignitium` (`cataclysm:ignitium_ingot`, dropped by the Ignis boss, `Armortier.IGNITIUM` defense `[6, 11, 9, 6]` toughness `4.0f` -- strictly above vanilla netherite's `[3, 8, 6, 3]`/`3.0f`, and non-damageable in the mod's own item class) and `cursium` (`cataclysm:cursium_ingot`, dropped by the Maledictus boss, `Armortier.CURSIUM` defense `[5, 10, 8, 5]` toughness `4.0f`), both netherite tier per D-M8-10. Neither carries a `c:` tag. A third above-netherite tier the jar ships, `Armortier.BONE_REPTILE` (`ancient_metal`, smelted rather than boss-drop), is left for a follow-up batch: the maintainer's roster named ignitium plus "any other tool-worthy boss metal", and ignitium/cursium share the boss-drop, non-damageable-armor shape that pairs them.

**Stats.** Every material's Forgeweave numbers sit inside the existing Track A envelope at its harvest tier, read off the nearest-tier compat metals already shipped rather than off each mod's own raw unit scale:

| Material | Tier | Head (durability / speed / damage) | Enchantability |
| --- | --- | --- | --- |
| `blaze_gold` | iron | 260 / 6.6 / 3.8 | 20 |
| `compressed_iron` | iron | 280 / 6.4 / 3.6 | 11 |
| `zanite` | iron | 270 / 6.8 / 4.0 | 15 |
| `ambrosium` | iron | 230 / 6.2 / 3.4 | 24 |
| `azure_silver` | diamond | 560 / 7.2 / 5.2 | 26 |
| `gravitite` | diamond | 620 / 6.8 / 4.8 | 12 |
| `crimson_steel` | netherite | 850 / 7.6 / 7.2 | 17 |
| `azure_electrum` | netherite | 780 / 8.0 / 6.8 | 24 |
| `tyrian_steel` | netherite | 980 / 7.4 / 7.6 | 14 |
| `deorum` | netherite | 900 / 7.8 / 6.4 | 20 |
| `ignitium` | netherite | 920 / 7.6 / 6.6 | 15 |
| `cursium` | netherite | 880 / 7.4 / 6.5 | 15 |

Each material gets one trait echoing what makes it distinct in its own mod, through an existing `TraitBehaviors` class and a `trait_definition`, never a copy of the source mod's code: `crimson_steel_temper`/`blaze_gold_cinder`/`ignitium_blaze` (`damage_type_immunity` against fire), `azure_silver_moonstep`/`azure_electrum_swift`/`gravitite_levity` (`movement_bonus`, jump/move/fly speed respectively), `tyrian_steel_ward` (`death_save`), `compressed_iron_heft` (`knockback_resistance` 0.1 -- **corrected by #1091**, which shipped as `damage_floor` at 1 heart and was described as "every hit deals at least a fixed amount of damage"; `damage_floor` is a drawback, not a protective or offensive trait, so the heft of a dense metal is now the knockback resistance the name always implied), `deorum_temper` (`stacking_resistance`), `zanite_growth` (`stat_scales_with_wear` on mining speed -- the issue's own "zanite growing stronger as it wears" hint), `ambrosium_glow` (`effect_on_hurt`, regeneration), `cursium_blight` (`reduce_target_healing`).

**Dedupe rule from D-M8-21 applies but finds nothing to dedupe here**: none of these twelve metals overlaps an existing Forgeweave material by real-world identity, so every one is a new material JSON rather than a widened `neoforge:conditions` branch.

**D-M8-25 — Twilight Forest and Ice and Fire join Track A, researched in full (issue #1059).** Asked for by the maintainer on 2026-09-18 because the earlier ATM10 compat survey only skimmed both mods (Ice and Fire at low confidence); the full survey is `docs/research/twilight-forest-and-ice-and-fire.md`.

*The Twilight Forest* (`TeamTwilight/twilightforest`, branch `1.21.1`, LGPL-2.1 code with a separate CC BY-NC-SA 4.0 asset license and all-rights-reserved sounds/structures). Eight presets. `ironwood`, `steeleaf`, `knightmetal` and `fiery` melt, keyed on the mod's own `c:ingots/<id>` tags (`crafting_items`/`repair_item` stay tag-keyed, unlike a Just Dire Things-style concrete item, because Twilight Forest does tag its own ingots); their harvest tiers come straight from `BlockTagGenerator`'s tag inheritance (`incorrect_for_iron_tool` for ironwood, `incorrect_for_diamond_tool` for steeleaf and knightmetal, `incorrect_for_netherite_tool` for fiery), not an inference from the mod's own raw stat numbers, which use a different unit scale entirely. `naga_scale`, `arctic_fur`, `alpha_yeti_fur` and `carminite` have no tool tier of their own in source (armor-only, or no gear at all for carminite) and are Part Builder only, the same shape `blazing_crystal.json` already ships; their tier is a judgment call off the mod's own boss/trophy progression order (`TFBlocks`' registration order), not a source-read fact.

*IceAndFire Community Edition* (`IAFEnvoy/IceAndFire-CE`, branch `1.21.1`, LGPL-3.0-or-later) — confirmed as the fork ATM10 8.1 actually ships for 1.21.1, since the original `AlexModGuy/ice-and-fire-dragons` has no build past Minecraft 1.20.1. Ten presets plus one dedupe. `dragon_bone` and the three `dragonsteel_fire`/`dragonsteel_ice`/`dragonsteel_lightning` tiers melt; dragon bone keys on the mod's own concrete item (its `c:bones` tag is flat, shared with witherbone, not a per-material subtag) while the three dragonsteels key on their own concrete ingot ids (the mod ships no `c:` tag for any of them at all). Harvest tiers come from `IafTiers` directly: dragon bone on `incorrect_for_iron_tool`, all three dragonsteels on `incorrect_for_netherite_tool` (D-M8-10: no rung above `resonite`). `deathworm_chitin_yellow`/`white`/`red` and `troll_leather_mountain`/`forest`/`frost` are Part Builder only — six materials with identical armor stats across each trio of color/biome variants, since the source mod gives them identical stats too; each variant still gets its own trait id over the same behavior body (issue #876's dedupe policy forbids sharing one trait id across materials). **Silver dedupes rather than duplicates**: `iceandfire:silver_ingot` carries the same `c:ingots/silver` tag Immersive Engineering and Occultism already use, so the existing `silver` material's `neoforge:conditions` widens with a fourth `neoforge:or` branch and a new `ingot_silver_iceandfire.json`/`clay_ingot_silver_iceandfire.json` casting pair, the same shape #1031 used for Eternal Ores.

Corrections to the issue's own expected roster, all read in source rather than assumed: copper is not a new material (both mods' copper tools repair from plain vanilla `Items.COPPER_INGOT`, no `iceandfire:copper_ingot` exists); dragon scales are not tool-worthy (twelve `DragonScalesItem` colors exist but none backs an `ArmorMaterial` or `Tier` — they are trophy/crafting items, and dragonsteel is the mod's actual dragon-gear tier); sea serpent scales are the same shape (a plain `Item` with a tooltip line, no gear use); myrmex chitin is absent from this fork entirely (zero references anywhere in `IafEntities.java`, a real content gap rather than a naming mismatch); hippogryph, amphithere, stymphalian, hippocampus, troll, dread and ghost materials back exactly one boss/creature sword each with a wood-tier harvest tag and no ingot or `c:` tag family, the same "single found-item, not a material family" shape the Just Dire Things survey already declined to port; raven feathers and armor shards (The Twilight Forest) are crafting reagents, not separate gear tiers.

Every trait reuses an existing `TraitBehaviors` class: `bonus_damage_vs` with `condition: armored` gives knightmetal the armor-piercing identity the issue named directly (`HitCondition.ARMORED`'s own javadoc already calls this gate "the vein hammer's crushing blow rider"); `damage_type_immunity` gives fiery and dragonsteel (fire) their real, source-verified fire resistance; the rest (`movement_bonus`, `crit_multiplier_bonus`, `stacking_resistance`, `invulnerability_window`, `evasion`, `effect_on_hit`, `self_repair_when`) are the closest available analog to each material's own identity where no exact-match behavior exists — steeleaf's real upstream trait pairs with a loot bonus (`docs/research/tinkers-1.20-ideas.md`'s 1.20-clone reference, MIT, read for sizing only) and fiery's real trait is autosmelt, neither of which `TraitBehaviors` has a seam for today; both are left as maintainer recommendations rather than invented Java. **Corrected by #1091:** naga scale shipped with `naga_ward` over `damage_floor` at 1 heart, on the reading that "a coiling guardian's thick scale armor" means "never take less than a floor amount". That is the behaviour backwards -- `damage_floor` is the library's drawback, and naga scale is armour in its own mod -- so `naga_ward` is now `stacking_resistance` (0.5 per hit, cap 4, 100-tick decay -- up to +2 protection, 8% off the post-armor blow, one notch under the hardcoded `bracingplate`'s 4.5): scales that harden as blows keep landing. `MaterialSyncSizeTest`'s budget got a measured raise for the eighteen-material roster; the full stat table, roster and beyond-materials recommendations are in `docs/research/twilight-forest-and-ice-and-fire.md`.

**Repairs #1031's own fluid gap.** Auditing this batch's own fluid registration turned up the same gap in the batch before it: `ferricore`, `blazegold` and `eclipsealloy` shipped `cast_only` material JSON with melting and casting rows naming `forgeweave:molten_ferricore`, `molten_blazegold` and `molten_eclipsealloy`, but `ForgeweaveFluids` never registered any of the three -- Just Dire Things being absent from the build/test classpath meant `neoforge:conditions` hid every one of those rows from a GameTest server, so a decode failure that would only happen with the mod actually installed went uncaught. Registered here the same way as this issue's own eight (lang, bucket, fluid tags), with one deliberate gap: their `CompatMaterialAvailability` entry is absent, because a literal `justdirethings:` item id there trips `JustDireThingsIsolationTest`'s scan (D-M8-22), so their bucket stays visible in creative and JEI without the mod installed, unlike every other compat metal -- the fluid, melting and casting rows themselves stay correctly existence-gated in JSON regardless, which is the part #1041's bug actually broke. `FluidReferenceAuditTest` (new) walks every JSON under both data trees, collects every `forgeweave:`-namespaced fluid id a melting recipe, casting recipe, fluid tag or data map names, and asserts each resolves against the real fluid registry -- so this class of gap fails a unit test from now on instead of waiting for a live install to surface it.

**D-M8-26 — Actually Additions' empowered crystals join, and the whole roster is proven buildable at the Part Builder (issue #1069).** Asked for by the maintainer on 2026-09-18: "add the Actually Additions materials, all of them at the Part Builder." Verified against the shipped `actuallyadditions-1.3.24+mc1.21.1.jar` (NeoForge, MIT, `github.com/Ellpeck/ActuallyAdditions`) -- its `data/actuallyadditions/tags/item/crystals.json` and `crystal_blocks.json` list the six plain crystal items and blocks, its `data/actuallyadditions/recipe/empowering/*.json` files name each Empowerer recipe's result, and the per-color `data/c/tags/item/storage_blocks/empowered_*_crystal.json` tags confirm the six empowered items and blocks are real, registered content. Six new presets, one per empowered crystal: `empowered_restonia_crystal`, `empowered_palis_crystal`, `empowered_diamatine_crystal`, `empowered_void_crystal`, `empowered_emeradic_crystal`, `empowered_enori_crystal` -- exactly the roster the issue expected, confirmed rather than guessed. No other Actually Additions item is tool-worthy: `crystallized_canola_seed` is an oil-production seed, the `*_crystal_shard`/`*_crystal_cluster` items are drops and decorative growth blocks, and `dust_quartz`/`dust_quartz_black` are marked `(WIP)` in the mod's own lang file.

All six sit at the `minecraft:incorrect_for_netherite_tool` rung, one step above their plain counterparts' diamond tier -- "clearly above its plain counterpart" per the issue, still no rung above `resonite` (D-M8-10). The jar ships no decompilable `ToolMaterial`/`Tier` class for any crystal color (only a `Crystals` enum carrying particle/cluster colors, confirmed by decompiling `de/ellpeck/actuallyadditions/mod/items/metalists/Crystals.class`), so tier and stats are Forgeweave's own placement within the existing netherite-tier envelope, the same "no ported tool number" shape D-M8-24 used for PneumaticCraft's `compressed_iron`. All six share one stat block (head 820/7.6/6.4, enchantability 20), matching the plain six's own shared-block precedent, and sit inside the netherite envelope D-M8-24 measured (780-980 durability, 7.4-8.0 speed, 6.4-7.6 damage).

Each empowered crystal's trait is the plain crystal's own trait family at a higher level, per the issue's own steer, expressed through the same `TraitBehaviors` JSON vocabulary D-M8-24 introduced rather than the legacy hardcoded `ForgeweaveTraits` class the plain seven still use: `empowered_restonia_bloodsurge` (`damage_scales_with` target max health, coefficient 0.06/cap 6.0, up from plain `bloodgem`'s 0.04/4.0), `empowered_palis_tempest` (`damage_scales_with` impact velocity, coefficient 7.0/cap 4.5, up from `stormglass`'s 5.0/3.0), `empowered_diamatine_prism` (`bonus_damage_vs` on a full charge, 5.0, up from `radiant_edge`'s 3.0), `empowered_void_maw` (`bonus_damage_vs` unconditional, 2.0, up from `voidtouched`'s flat 1.0), `empowered_emeradic_bulwark` (`knockback_resistance` 0.3, double plain `verdant_ward`'s 0.15 -- **corrected by #1091**, which shipped it as `damage_floor` at 2 hearts on the reasoning that no `TraitBehaviors` behaviour exposed flat knockback resistance. That was true, and the substitute it reached for was the library's drawback: it made every blow on the wearer deal at least 2 hearts, so the empowered crystal was worse to wear than the plain one while its lang text promised a health floor. #1091 added the missing `knockback_resistance` behaviour instead, so the empowered crystal is now what the decision meant all along, the plain crystal's own trait at a higher level. For an anchor: vanilla netherite armour gives 0.1 per piece), `empowered_enori_radiance` (`effect_on_hit` glowing, 200 ticks, double `luminous`'s 100).

**All of them at the Part Builder, including a bug in the seven that already shipped.** #837 batch 5's own PR body documented, correctly at the time, that none of the six plain crystals ships a `c:gems/*` tag for its raw item (confirmed again here: only `c:storage_blocks/<crystal>` exists), so their `crafting_items`/`repair_item` keyed on that block tag alone. Issue #872 later added `Material.LENIENT_INGREDIENT_CODEC`, letting `crafting_items`/`repair_item` key on a concrete item id when no tag exists -- unblocking exactly this case -- but nobody revisited the six crystals to use it. The result: a single restonia crystal (or any of its five siblings) could not build a Part Builder part on its own, only a compacted 3x3 block of nine could, unlike `black_quartz`, which has always listed both its `c:gems/black_quartz` tag (144) and its `c:storage_blocks/black_quartz` tag (1296). Fixed here: each of the six plain crystals' `crafting_items` gains a `{"item": "actuallyadditions:<x>_crystal"}` row at 144 (one ingot's worth, matching black_quartz's own gem row), and `repair_item` moves from the storage-block tag to that same concrete item id. The six new empowered crystals ship with both rows from the start: `{"item": "actuallyadditions:empowered_<x>_crystal"}` at 144 and `{"tag": "c:storage_blocks/empowered_<x>_crystal"}` at 1296. None of the thirteen carries `cast_only`, a `melting_recipe`, a `casting_recipe`, or a `ForgeweaveFluids` entry -- crystals do not melt, and none of the seven shipped ones had a fluid route to begin with, so there was nothing to remove.

No item forms (D-M8-6), no toggle (D-M8-5), no Java import of Actually Additions. `MaterialTest`/`ArmorMaterialTest` extend their existing rosters; `EmpoweredCrystalsGameTests` gives the six new materials the same negative-existence proof `PresetBatch5GameTests` gives the plain seven, since `actuallyadditions` is not a build/test dependency.

### Datapack modifier definitions (#973, ADR-0004 item 3 closed 2026-09-18)

ADR-0004 item 3's second half, delivered for modifiers and closing the ADR. One file per modifier under `data/<namespace>/forgeweave/modifier_definition/<name>.json`, flat, shaped exactly like M6's `trait_definition` so a pack author learns one idiom: `behavior` picks a parameterized class from the library (`ModifierLibrary`), the other fields are its parameters. Optional `neoforge:conditions` existence-gates it exactly like a material. Built-in ids always win a collision. A wrong `behavior` id, a missing parameter or an unknown enum value fails the data load with the known ids listed, never a silent no-op modifier. The pack supplies the id's `modifier.<namespace>.<path>.name` / `.description` lang keys, which is all tooltips, the Tool Station panel and the guide book need.

Serialization does not move: a tool stores `id + level` and nothing else (ADR-0004 item 2), so every existing save and every save-compat fixture stays valid, and a tool whose pack is gone keeps the entry inertly the way any unimplemented id already does. Modifiers keep their datapack *application recipes* unchanged -- a pack-defined modifier needs a `modifier_recipe` to reach a tool, the same as a built-in one. **No KubeJS modifier builder**: a `ForgeweaveEvents.modifiers` sibling waits until the first pack author asks for it.

```json
{ "behavior": "forgeweave:stat_bonus",
  "stat": "durability", "flat": 500.0 }
```

| `behavior` | Parameters (snake_case; `[default]`) |
| --- | --- |
| Every behavior also accepts the three shared application fields: `units_per_level` `[1]` (how many applications make one displayed level, upstream's `countPerLevel`), `slots` (`per_level`, `first_level_only`, `none`) `[per_level]`, and `tools` (`any`, `harvest`, `armor`, `armor_or_held`, `chestplate`, `helmet`, `projectile`, `not_launcher`) `[any]`. A field named `*_per_level` is per displayed level; a field named `*_per_unit` is per raw application unit | |
| `stat_bonus` | `stat` (`durability`, `attack_damage`, `mining_speed`), `flat` `[0]`, `per_level` `[0]`, `fraction_of_base` `[0]`, `minimum` `[none]` |
| `attribute_bonus` | `attribute` (`knockback_resistance`, `armor_toughness`, `submerged_mining_speed`, `block_interaction_range`), `flat` `[0]`, `per_level` `[0]` |
| `tool_tier` | `bump` `[0]`, `at_least` `[0]`, `cap` `[none]` -- applied as `min(max(tier + bump, at_least), cap)` |
| `durability_negation` | `chance_per_level` 0..1, capped at a certain 1 |
| `bonus_slots` | `flat` `[0]`, `per_level` `[0]` |
| `bonus_experience` | `fraction_per_level` |
| `grant_enchantment` | `enchantment` (enchantment id), `level` `[the applied display level]`, `max_level` `[none]` |
| `fire_resistant` | — |
| `aoe_expansion` | `axis` (`width`, `height`) |
| Combat seams | |
| `knockback_on_hit` | `per_unit` |
| `effect_on_hit` | `effect` (mob effect id), `amplifier` `[0]`, `duration_per_unit` (ticks), `duration_offset` `[0]` |
| `bonus_damage_vs` | `entities` (entity type tag), `damage_per_level` |
| `ignite_on_hit` | `seconds_per_unit` `[0]`, `seconds_offset` `[0]`, `damage_per_unit` `[0]` |
| `lifesteal_on_hit` | `fraction_per_level` |
| `thorns_counter` | `chance_per_level` 0..1, `constant_damage`, `random_damage` |
| `protection` | `per_level`, `damage_type` (damage type tag) `[every protectable source]`, `direct_only` `[false]` |

Every one of the sixteen was extracted from a shipped Java modifier -- diamond, emerald, netherite and silky are `stat_bonus`; knockback resistance, netherite's toughness, aquadynamic and far reach are `attribute_bonus`; reinforced is `durability_negation`; extra_slot is `bonus_slots`; resonant is `bonus_experience`; wind burst is `grant_enchantment`; Width++ and Height++ are `aoe_expansion`; knockback, shulking, webbed, smite, bane of arthropods, fiery, necrotic, thorns and the five protections are the seams. Nothing was invented for the library, and the modifiers no parameter set can express stay Java (`ModifierBehaviors`' javadoc lists them): haste's and sharpness's diminishing-returns curves, the event-driven ones (searing, blasting, veinmine, magnetic pull, mending moss, soulbound, glowing, luck's self-growth, beheading), the per-material generated ids (embossment, fortification), and the compat-owned ones.

Toggle: `compat.modifierDefinitions`, default `true` (D-M8-5). Read at lookup rather than at registration, because a SERVER config does not exist yet when datapack registries load and sync -- so "registers nothing" is unreachable for a synced registry, and inert-at-lookup gives the same player-visible result: a pack-defined id resolves to nothing, the entry keeps its id, its level and its slot, and it acts again the moment the toggle returns, with no reload. `ForgeweaveTraits#lookup`'s own precedent (#968) for the same reason.

Verification: `ModifierBehaviorsTest` (codec round trip per behavior, unknown `behavior` and unknown enum value fail loudly with the alternatives listed, a failing `neoforge:conditions` decodes to nothing, a pack-defined entry still saves as `id + level` and still decodes with no definition loaded), `DatapackModifierGameTests` (a gametest-only definition is applied at a real Tool Station and fires; a conditioned one never registers), `CompatToggleGameTests#modifierDefinitionsOffResolveNoPackDefinedModifier`, and the `m973_tool_pack_modifier.snbt` corpus fixture.

### Non-goals for M8

Curios (D-M8-4, until a back or charm item exists) · a Create blaze burner seam or any other per-mod heat block, superseded by the energized tank (D-M8-3, D-M8-11) · Ars Nouveau · a Create part factory: Forgeweave parts are made at Forgeweave stations (D-M8-16) · a fourth harvest tier above `resonite` (D-M8-10) · liquid or gas forms of any Forgeweave material, which stay molten-only (D-M8-6) · a Mekanism heat handler, since D-M8-11's tank already takes Forge Energy and a second heat protocol buys nothing · Forgeweave recipes that consume its own plates, rods, gears or wires, which exist for other mods (D-M8-6) · item forms for Track A materials, whose own mods own them (D-M8-6) · the tiered smeltery automation block of [#986](https://github.com/gkissel/forgeweave/issues/986), parked (D-M8-14) · GuideME, and any replacement of the in-game book, which already carries the materials handbook, the leveling page and the station pages at 1.12-parity shape ([#974](https://github.com/gkissel/forgeweave/issues/974), confirmed 2026-09-18) · deriving code or assets from Apotheosis, EMI, Curios or Draconic Evolution (JC-A) · toggling Track A material presets (D-M8-5) · activating the Botania and Blood Magic presets, which keep waiting for 1.21.1 artifacts on [#857](https://github.com/gkissel/forgeweave/issues/857) and [#858](https://github.com/gkissel/forgeweave/issues/858) · mirroring, migrating or owning Apotheosis affix data (JC-D) · a Forgeweave enchanting station, or any change to what `allowVanillaEnchanting` means · GameTests that require a live Apotheosis or EMI instance (JC-B: those are checklist lines) · Just Dire Things upgrades on Forgeweave tools and armor, which the mod's own `instanceof` gating makes unreachable (D-M8-22), along with the mixin and the reimplementation that would work around it · custom Epic Fight animations for Forgeweave weapons, until the mod's own animation pipeline and a designer are available (D-M8-23).

### CI and release gates

- **GameTest coverage** (Forgeweave-side seams only, per JC-B, since no compat mod except JEI is on the gametest classpath): applying `socketed` consumes a modifier slot and an M7-earned slot can be spent on it · a socketed tool's free-slot count matches `ForgeweaveModifiers.freeSlots()` · `allowVanillaEnchanting` refuses and accepts the enchant, and an enchanted tool still takes a modifier and still levels · a formed smeltery with an energized tank and an energy source melts at its fuel sample's temperature, melts nothing with an empty buffer, leaves a material gated above that temperature unmelted, charges only the hottest tank when two are present, and doubles both cost and progress under overdrive · a gametest-only `modifier_definition` reaches a Tool-Station-assembled tool and fires, and a conditioned one never registers · **one config-off test per toggle**, modelled on `ContentFamilyGameTests` (set, assert nothing registers, restore in a `finally`), including the stateful cases: a stack carrying socket, module or fusion state round-trips with its toggle off, keeps its component, and works again when the toggle returns · a world containing a placed energized tank still loads with `energizedTank = false` · a tool carrying an `atomic_matter_alloy` part keeps its module container component with the Mekanism toggle off · a Mystical Agriculture augment slot count survives a toggle-off round trip.
- **Unit gates**: the socket-contents component's codec and stream-codec round trip · every material with an ingot has all eleven forms and every form is in its `c:` tag, walked off the roster rather than listed by hand, so a new material cannot ship a hole · gem-type materials have dusts and no plate family · the energy cost formula `rfPerMeltTickBase x temperature / 1000` at the ladder's own temperatures and under overdrive · the Mekanism ore-chain generator's output is total over the eleven Track B ores and every form it names exists · `surgebound`'s capacity and speed curve per level including the nitro doubling · the radiation modifier's four levels map to 25/50/75/100% · the Mystical Agriculture essence tier mapping is total over the mining ladder · the gem-effect mapping table is total, every mapped effect resolving and every unmapped one listed rather than silently dropped · a Forgeweave tool stack carrying unknown foreign components decodes without losing its own · the RF/t to temperature curve at each rung boundary and below the lowest rung · `modifier_definition` codec round trip per behavior class, an unknown `behavior` failing loudly with the known ids listed, a failing `neoforge:conditions` decoding to nothing (model: `TraitBehaviorsTest`) · a source-isolation test per integration, in the style of `DraconicSourceIsolationTest` · `LocalizationAuditTest` stays green: every socket, affix, heater and book string is a declared lang key.
- **Dependency shape**: every compat mod is `compileOnly` plus `testCompileOnly` plus `testRuntimeOnly` with `transitive = false`, with an optional entry in `neoforge.mods.toml`, the way Draconic Evolution and KubeJS are declared today. None of them joins `runGameTestServer`'s classpath. Jade stays off `localRuntime` because its login payload crashes gametests.
- **Save-compat fixtures (same PR as the format)**: `m8_socketed.snbt` (a tool with `socketed` and its socket-contents component filled) · `m8_affixed.snbt` (a Forgeweave tool carrying foreign Apotheosis components alongside its own, pinning JC-D's decode rule) · the energized tank's block-entity NBT (fuel sample, buffer, overdrive state) · a tool carrying a datapack-defined modifier · a tool carrying an `atomic_matter_alloy` part with Mekanism modules installed · a tool carrying `surgebound` and an armor piece carrying the radiation modifier. All walked by `SaveCompatCorpusTest`.

- **Dependency additions this milestone**: Mekanism (`mekanism:Mekanism:1.21.1-10.7.19.85:api`, modmaven.dev, MIT) · EnderIO (`com.enderio:enderio:8.2.11-beta`, maven.rover656.dev) · Powah (LGPL-3.0, API only, nothing derived) · Occultism (MIT, dl.cloudsmith.io/public/klikli-dev/mods/maven) · Mystical Agriculture (`com.blakebr0.mysticalagriculture:MysticalAgriculture`, maven.blakesmods.com, MIT, needs Cucumber) · Create, Immersive Engineering, Allthemodium and Elementarium are recipe and tag targets only, with no compile dependency. Allthemodium has no license at all, so tags only and nothing derived, ever.
- **Manual release-checklist adds**, per the gate template's rule that from M8 on the checklist runs with each integrated mod present: one block per mod. **Apotheosis**: a gem seats and its bonus applies, an affixed Forgeweave tool drops from a loot chest and renders, enchanting is refused at `false` and accepted at `true`. **EMI**: the categories [#971](https://github.com/gkissel/forgeweave/issues/971)'s spike flagged, rendering and transferring. **Mekanism or any FE source**: the energized tank drives a smeltery and its temperature tracks its fuel sample (D-M8-11 replaced the heater; there is no heater block). **Draconic Evolution**: fusion upgrades apply, the module screen opens on evolved gear, module effects fire. **Jade, then WTHIT**: casting cooling and smeltery contents read correctly. **KubeJS**: a scripted trait registers and fires. **Better Combat**: swing a broadsword, a katana, a hammer and a bow and confirm each plays its own animation rather than the vanilla swing, and that the displayed damage still matches the tool's own tooltip number. **Epic Fight**: equip a katana (`uchigatana`), a warmace (`axe`) and a full heavy armor set, confirm the weapon's combo and stance match its assigned type, that the armor's weight is felt (slower attacks, a shorter stagger when hit) and heavier on the heavy set than the light one, and that Forgeweave's own tooltip damage number is unchanged by either mod. **None installed**: Forgeweave alone behaves identically, with no ghost recipes, no missing textures and no log noise. Plus one pass with **every toggle off**, confirming the mod starts, the world loads, and socket, affix, module and fusion state is inert rather than broken. Plus the standing JEI sanity check and the previous-release world load.
- Alpha tags during the milestone; the save-compat promise stays binding throughout.

### Issue roadmap

| # | Deliverable | Depends on | Status |
| --- | --- | --- | --- |
| M8-0 | Compat config toggles for the four integrations that shipped before D-M8-5, the `config/forgeweave/` folder split (D-M8-8), and mining level in the Jade and WTHIT overlays (D-M8-9) | — | shipped ([#968](https://github.com/gkissel/forgeweave/issues/968), PR [#1016](https://github.com/gkissel/forgeweave/pull/1016)), extended 2026-09-07 |
| M8-1 | The `socketed` modifier, the socket-contents component, and gem bonuses on the existing seams | — | shipped ([#969](https://github.com/gkissel/forgeweave/issues/969)); the gem-effect mapping is `ApotheosisSockets.EFFECT_MAP`, six of Apotheosis' fourteen bonus types mapped and the rest recorded as unmapped, and the config toggle's own key is [#968](https://github.com/gkissel/forgeweave/issues/968)'s |
| M8-2 | Apotheosis loot affixes on Forgeweave gear, and the `allowVanillaEnchanting` interplay | M8-1 | shipped ([#970](https://github.com/gkissel/forgeweave/issues/970)); the loot-parts answer is the `forgeweave:assemble_tool` loot function, affixability needs nothing registered beyond a three-row loot-category override for the bows, and both toggles live in `ApotheosisAffixes` |
| M8-3 | EMI bridge spike across all 14 recipe types across the 12 category classes, then a native plugin only for the gaps | — | spike posted, bridge covers everything, no plugin ([#971](https://github.com/gkissel/forgeweave/issues/971)) |
| M8-4 | Energized tank: the fuel sample, the energy cost per melt tick, the hottest-tank rule, overdrive, the JEI row | — | shipped ([#972](https://github.com/gkissel/forgeweave/issues/972), PRs [#1014](https://github.com/gkissel/forgeweave/pull/1014) and [#1026](https://github.com/gkissel/forgeweave/pull/1026)), rewritten in place 2026-09-07 (was the FE heater wall); the overdrive button lives on the tank's own screen |
| M8-5 | Datapack `modifier_definition` registry over the modifier library; ADR-0004 item 3 close-out | — | shipped ([#973](https://github.com/gkissel/forgeweave/issues/973)); sixteen behaviors in `ModifierLibrary`, all extracted from shipped Java modifiers, tabled in the section above, and no KubeJS builder until a pack author asks. ADR-0004 is closed |
| M8-6 | GuideME decision and the in-game book close-out | — | closed ([#974](https://github.com/gkissel/forgeweave/issues/974)), confirmed by the maintainer 2026-09-18: the book stays |
| M8-8 | Material forms and `c:` tags for every material with an ingot, the `FluidTagsProvider`, tag-keyed dust melting (D-M8-6, D-M8-7) | — | shipped ([#992](https://github.com/gkissel/forgeweave/issues/992), PR [#1019](https://github.com/gkissel/forgeweave/pull/1019)) |
| M8-9 | Mekanism phase 1: `atomic_matter_alloy`, the module container, the Modification Station, the free effects (D-M8-15) | M8-8 | shipped ([#993](https://github.com/gkissel/forgeweave/issues/993), PR [#1040](https://github.com/gkissel/forgeweave/pull/1040)) |
| M8-10 | Mekanism phase 2: teleport, farming, flight, the radiation modifier, the Track B ore chains (D-M8-15) | M8-9 | shipped ([#994](https://github.com/gkissel/forgeweave/issues/994), PR [#1060](https://github.com/gkissel/forgeweave/pull/1060)); no 5x rung and no crystal form, so the chains run 2x, 3x and 4x, and phase 2 added the `rayward` modifier |
| M8-11 | Create, Immersive Engineering, EnderIO and the Powah heat sources, as generated recipe JSON (D-M8-12, D-M8-13, D-M8-16) | M8-8 | shipped ([#995](https://github.com/gkissel/forgeweave/issues/995), PR [#1028](https://github.com/gkissel/forgeweave/pull/1028)) |
| M8-12 | Powah materials as Track A presets, and the `surgebound` modifier (D-M8-17) | — | shipped ([#996](https://github.com/gkissel/forgeweave/issues/996), PR [#1015](https://github.com/gkissel/forgeweave/pull/1015)) |
| M8-13 | Occultism materials, crushing and miner recipes, and a ritual that applies a modifier (D-M8-18) | M8-8 | shipped ([#997](https://github.com/gkissel/forgeweave/issues/997), PR [#1037](https://github.com/gkissel/forgeweave/pull/1037)) |
| M8-14 | Allthemodium tier equivalence and the mining dimension, plus Elementarium's metals by tag (D-M8-19) | M8-8 | shipped ([#998](https://github.com/gkissel/forgeweave/issues/998), PR [#1034](https://github.com/gkissel/forgeweave/pull/1034)) |
| M8-15 | Mystical Agriculture crops, presets, and Forgeweave gear at its Tinkering Table (D-M8-20) | M8-8 | shipped ([#999](https://github.com/gkissel/forgeweave/issues/999), PR [#1036](https://github.com/gkissel/forgeweave/pull/1036)); `mysticalAgricultureAugments` is a runtime read and needs no restart, and `ITinkerable` carries no per-stack tier, so every Forgeweave tool is accepted at tier 5 with one augment slot and nine tool shapes are not augmentable |
| M8-16 | Just Dire Things' four tool tiers as Track A presets, and the Eternal Ores dedupe across 19 existing materials (D-M8-21) | — | shipped ([#1031](https://github.com/gkissel/forgeweave/issues/1031)) |
| M8-17 | Just Dire Things upgrades on Forgeweave tools and armor (D-M8-22) | — | blocked upstream ([#1032](https://github.com/gkissel/forgeweave/issues/1032)); the mod gates upgrades on `instanceof ToggleableTool` and hardcodes each item's ability set, so routes 1 and 2 both reach nothing. Nothing ships beyond the decision and the test that pins it |
| M8-18 | Better Combat and Epic Fight movesets for every Forgeweave weapon, tool and armor piece, generated and data-only (D-M8-23) | — | shipped ([#1046](https://github.com/gkissel/forgeweave/issues/1046)) |
| M8-19 | Track A presets for Silent Gear, PneumaticCraft: Repressurized, Forbidden and Arcanus, The Aether and L_Ender's Cataclysm (D-M8-24) | — | shipped ([#1058](https://github.com/gkissel/forgeweave/issues/1058)) |
| M8-20 | The Twilight Forest and Ice and Fire, researched in full, as Track A presets (D-M8-25) | — | shipped ([#1059](https://github.com/gkissel/forgeweave/issues/1059)) |
| M8-21 | Actually Additions' six empowered crystals, and the whole thirteen-material roster proven buildable at the Part Builder (D-M8-26) | — | shipped ([#1069](https://github.com/gkissel/forgeweave/issues/1069)) |
| M8-22 | The addon surface: `dev.gkissel.forgeweave.api` and its trait, modifier, combat and upgrade seams; tools and parts registered from Java; smeltery walls by block tag; the test addon, `docs/addons.md`, the stability ADR and the KubeJS binding for every datapack registry | — | shipped ([#1008](https://github.com/gkissel/forgeweave/issues/1008), parts [#1065](https://github.com/gkissel/forgeweave/issues/1065), [#1066](https://github.com/gkissel/forgeweave/issues/1066), [#1067](https://github.com/gkissel/forgeweave/issues/1067) and [#1083](https://github.com/gkissel/forgeweave/issues/1083)); scoped by the audit in `docs/research/addon-surface-audit.md`, whose eight open questions the maintainer answered on 2026-09-18. A tool from outside is Java-registered and never datapack-defined (Q1), both behavior tables are open (Q2), a Java-registered trait is not gated on `compat.kubejsTraits` (Q3), the guide book stays closed (Q7), and what is promised is [ADR-0006](adr/0006-addon-api-stability.md) (Q8). KubeJS reaches the recipe registries through `ServerEvents.registry`, not a `RecipeSchema`: none of the twelve is a recipe-manager recipe |
| M8-7 | Per-mod release-checklist lines, save-compat fixtures, GameTest sweep, acceptance playthrough | all | checklist and sweep shipped ([#975](https://github.com/gkissel/forgeweave/issues/975)): `docs/playtest/checklist-0.6.0-beta.1.pt-BR.md` carries a block per integrated mod, the all-toggles-off pass and the acceptance playthrough; the fixture audit found every child had already shipped its own, so the corpus floor rose to the real count rather than gaining files; every one of the twenty `compat` toggles already had a config-off GameTest. The playthrough and the tag are the maintainer's |

M8-0 landed the surface the rest of the milestone writes against. `ForgeweaveConfig` is four `SERVER` specs under `config/forgeweave/` — `general-server.toml`, `content-server.toml`, `compat-server.toml`, `worldgen-server.toml` — split on the sections the options already grouped into rather than D-M8-8's sketched compat/smeltery/leveling/materials shape, which the options did not bear out: there is no materials option, and the smeltery and leveling keys are members of the `content` family roster. An existing flat `forgeweave-server.toml` is read once and its values are split across the four before anything registers, then renamed to `forgeweave-server.toml.migrated`; nothing a pack had tuned is reset. Each file's section comment carries D-M8-8's two standing rules. The `compat` file holds the four backfilled toggles — `draconicFusion`, `draconicModules`, `overlays`, `kubejsTraits` — plus `createGoggles`, which [#1007](https://github.com/gkissel/forgeweave/issues/1007) left to this mechanism; adding the next integration's toggle is one `define` there plus one `ForgeweaveConfig.enabled(...)` read at the point that integration answers. All of them are read at runtime rather than at registration, because a `SERVER` spec does not exist until a world does and every serializer, capability and overlay plugin is registered during mod loading; the gates sit at the earliest point each integration is actually asked anything, in the half of its package that names no third-party type, which is also what lets `runGameTestServer` test them.

Slice order, dependency first and value second: 0 toggles, config folder and overlays (M8-0) · 1 material forms and tags (M8-8) · 2 Apotheosis (M8-1, M8-2) · 3 the energized tank (M8-4) · 4 Mekanism (M8-9, M8-10) · 5 the processing mods and Powah (M8-11, M8-12) · 6 Occultism (M8-13) · 7 Allthemodium and Elementarium (M8-14) · 8 Mystical Agriculture (M8-15) · 9 EMI, `modifier_definition` and the GuideME decision (M8-3, M8-5, M8-6) · 10 release (M8-7). Slices 2, 3 and 9 do not depend on slice 1 and can run alongside it; slices 4 through 8 all do.

## Milestone ladder

Each milestone ships a playable release under the tag scheme in [releasing.md](releasing.md).

| # | Milestone | Depends on |
| --- | --- | --- |
| M1 | Tools slice (this document) | — |
| M2 | Smeltery, metal materials, modifiers. Melts/casts any mod's ores and ingots via standard `c:` tags, so modded metals (Mekanism, Create, Thermal, …) work without per-mod code | M1 |
| M3 | Full melee/harvest tool roster incl. modern-era shapes (katana, scimitar, warmace), combat tuning, Tool Forge, embossing (this document, planned 2026-08-12) | M2 |
| M3.2 | Material roster: the full always-on 1.12 material set with per-part traits, tag-gated compat metals, and four by-name modern-branch additions (this document, planned 2026-08-13; pulled forward from M6 by maintainer decision 2026-08-12) | M3 |
| M3.5 | Ranged weapons: shortbow, longbow, crossbow firing vanilla arrows (planned 2026-08-15, this document). Material arrows + shuriken deferred to a follow-up; javelin/throwing axe/energy tool to backlog | M3.2 + `mc1.21.1-v0.3.0-beta.1` |
| M4 | Armors: part-based plate armor (plating + maille), Armory-inspired, 1.20-derived by name (this document, planned 2026-08-24) | M3.5 (reuses parts/traits/modifiers/combat seams) |
| M5 | Gadgets: slime sling (#453), slime boots (#452), wooden hopper (#822, half-speed 1:1 port). The rest of the parity audit's unplanned gadget roster — piggyback pack, punji sticks, item/drying racks, glow ball, EFLN, fancy frames, wooden rails, stone torch/ladder, dried clay and brownstone families, spaghetti, slime channels — stays open in [#487](https://github.com/gkissel/forgeweave/issues/487) | M2 |
| M6 | Material expansion: existence-gated modded materials (Track A) plus a self-contained TAIGA-style ladder (Track B), at Tinkers'-Evolution-scale parity, folding in the deferred #180/#181 world content (this document, planned 2026-08-31; epic [#824](https://github.com/gkissel/forgeweave/issues/824)) | Stable material data model (M1), metals (M2), armor traits and the `onDefend` seam this milestone's armor library extends (M4) |
| M7 | Tool leveling: tools and armor gain XP from intended use, each level granting one modifier slot. Tool half derived from Tinkers' Tool Leveling (MIT, direct port); armor half original Forgeweave design on M4's `onDefend` seam (this document, planned 2026-09-02; epic [#917](https://github.com/gkissel/forgeweave/issues/917)) | M3 (tool roster, `ToolConstants`), M3.5 (`LauncherStats`), M4 (the `onDefend` defense seam) |
| M8 | Deep compat: Apotheosis, Curios, Jade/WTHIT, EMI, Mekanism, and other major mods by adoption. First slice shipped: **Draconic Evolution fusion crafting** ([#915](https://github.com/gkissel/forgeweave/issues/915), maintainer decision 2026-09-02; re-aimed by [#946](https://github.com/gkissel/forgeweave/issues/946), maintainer directive 2026-09-03) -- three fusion-made tool metals (emberweld, starweld, voidweld) that only DE's multiblock can produce, sitting above the draconium/wyvern/awakened/chaotic preset materials as the tier those four feed, plus the tool-upgrade ladder (8 modifier lines x DE's 4 tech levels) that runs on the same multiblock and now only accepts a tool made of one of the three. #946 removed #915's two smeltery-core promotion recipes: promoting Forgeweave's own blocks is not what the parity target does with fusion crafting, and the core tiers keep the pour-to-transform route [#845](https://github.com/gkissel/forgeweave/issues/845) gave them. Second slice, phase 1: gear carrying `evolved` is a **DE module host** ([#956](https://github.com/gkissel/forgeweave/issues/956), maintainer decision 2026-09-04) -- DE's own module screen opens on it through the plain `DECapabilities.Host.ITEM` capability, with a grid sized by the `evolved` tier and module categories read off the tool's shape. Phase 2 of #956 wires the tool-active effects to the hooks `ToolItem` and `BowItem` already own: a speed module multiplies the tool's own dig speed, an area module widens Forgeweave's own AoE sweep (a radius of `n` grows the box by `2n` on each axis) rather than running DE's second block breaker, a damage module adds to the tool's own attack damage, an area module also sweeps DE's own `dealAOEDamage` on a left-click hit, and a projectile module scales a bow shot's speed, spread and arrow damage. Every powered effect spends the `EnergyBuffer` the `energized` trait and DE's energy modules share. Three deviations from DE's own equipment, all so a module only ever adds: the dig-speed multiplier is clamped at 1 and drops DE's `1 + aoe * 10` divisor (a Forgeweave hammer already mines 3x3 at full speed), the damage bonus lands after the `cutoffDamage` curve rather than inside it, and an empty buffer means a module does nothing rather than zeroing the dig speed or blocking the shot. Arrow penetration and anti-gravity are not wired: both live on DE's own arrow entity, which would replace Forgeweave's. Third slice: the ladder grows to Draconic Evolution's own four tech levels ([#965](https://github.com/gkissel/forgeweave/issues/965), maintainer directive 2026-09-04). A sixth preset material, `draconium_core`, joins the three cores; a fourth fusion metal, `duskweld`, joins the three welds at DE's inert tier; `evolvedLevel` runs 1 to 4 over a new bottom marker, `evolving`, so the three shipped `evolved` ids keep the tiers they already meant and a tool sitting in a save does not move; and the module grid table becomes 2x3 / 2x6 / 4x5 / 6x6, width by height, so the one-cell allowance is 6 / 12 / 20 / 36. That one table in `DraconicModules` feeds both the module host and #955's tooltip. DE's 2x2 shield controller fits every tier including the inert one; its 4x4 energy link needs the awakened or chaotic grid, since the two lower ones are two cells wide whatever their area. compileOnly, `mod_loaded`-gated, no code derived from DE (its "Don't Be a Jerk" license permits the library dependency but not derivation, so ADR-0003 treats it like the inspiration-only clones). **Planned 2026-09-04, replanned 2026-09-07** (planning epic [#29](https://github.com/gkissel/forgeweave/issues/29), execution epic [#967](https://github.com/gkissel/forgeweave/issues/967)); scope, decisions D-M8-1 to D-M8-20 and the issue roadmap are in this document's § Milestone 8. The second session widened the milestone from four systems to eleven mods: material forms and `c:` tags so other mods can process Forgeweave metals, the energized tank in place of the FE heater, Mekanism module hosting, and recipe or tag layers for Create, Immersive Engineering, EnderIO, Powah, Occultism, Allthemodium, Elementarium and Mystical Agriculture. Curios is a non-goal until a back or charm item exists (D-M8-4), so this row's Curios dependency is historical | M4 (Curios needs armors/gadgets) |
| M9 | Original-asset rewrite. **Premise changed 2026-08-28 (issue #796, maintainer decision):** rather than a single milestone that removes upstream-derived assets outright, Forgeweave ships two art sets as each Forged sprite arrives -- **Forged** (new original art, the default) and **Legacy** (the pre-#796 look, demoted to an optional built-in resource pack, not deleted). M9 as "remove the derived tree" no longer happens; the rewrite instead proceeds incrementally, sprite batch by sprite batch, through the machinery #796 built (see `scripts/sprite_sets.py`, `ForgeweaveResourcePacks`). This row stays as the historical record of the milestone; new sprite batches ship as their own issues rather than waiting on an M9 freeze | Content freeze of M1–M8 |

### Milestone sources

Per-milestone source policy, decided from the [addon ecosystem survey](research/tic2-addon-ecosystem.md). **Derive** = MIT upstream, code/assets may be ported with `NOTICE.md` rows per ADR-0003. **Inspire** = design lessons only, no code or assets copied, regardless of how good the reference is.

**Standing rule (maintainer, 2026-08-09)**: Tinkers' Construct 1.12 itself is a derivation source for **every** milestone — complete assets and code may be ported wherever the milestone's feature has a 1.12 counterpart, per CLAUDE.md's 1.12-parity default. The table below lists *additional* sources per milestone.

| Milestone | Derive from | Inspire from |
| --- | --- | --- |
| M2 | Tinkers' Construct 1.12 (smeltery, casting, metals, modifiers — full assets/code as needed) | TAIGA (alloy table), Tinkers' Addons (modifier worked examples) |
| M3 | — | PlusTiC: katana as a modern-era shape (Forgeweave's design differs: damage builds while in combat, decided 2026-08-12) · TiC 1.20 branch: vein hammer + dagger shapes and pickaxe-pierce idea (feature-scope deviation authorized by maintainer 2026-08-12 — the standing "1.20 never sets feature scope" rule is explicitly overridden for these three, by name) |
| M3.2 | — | TiC 1.20 branch: **amethyst bronze, nahuatl, chorus, ancient** as material additions (feature-scope deviation authorized by maintainer 2026-08-13, by name — the standing "1.20 never sets feature scope" rule is explicitly overridden for these four) |
| M3.5 | — | PlusTiC: energy-consuming ranged tool |
| M4 | **TiC 1.20 branch: plate armor** — plating + maille part model, `PlatingMaterialStats` values, layered armor model + grayscale base textures, ARMOR-scope trait table, defense modifier family (feature-scope deviation authorized by maintainer 2026-08-24, by name — the standing "1.20 never sets feature scope" rule is explicitly overridden for plate armor; Tinkers' 1.12 has no armor) | Construct's Armory (LGPL): exactly four armor slots, variety carried by traits and modifiers. Its two-station split was considered and rejected 2026-08-24 (the Tool Station is generic over `ENTRIES`) |
| M6 | **TiC 1.20 branch: seared stone, necrotic bone, queen's slime, hepatizon, slimewood** as material additions (issue #843, closes #180; feature-scope deviation authorized by maintainer 2026-08-31, by name — the standing "1.20 never sets feature scope" rule is explicitly overridden for these five, same precedent as the M3.2/M4 rows above). Declined by name on the same issue: whitestone (redundant with the existing `endstone.json`), scorched stone and cinderslime (both need Foundry-scale prerequisites Forgeweave does not have), slimesteel (duplicates knightslime's recipe pattern), venombone (deferred, needs a new venom fluid + two new traits) | TAIGA, PlusTiC, Moar Tinkers and Tinkers' Evolution progression ladders and rosters — the last is Track A's parity target specifically (epic #824), MIT text plus a "Good, not Evil" clause, so inspiration-only like the other three, not plain MIT. Sizing target (rewritten from the old single-number band, [research/m6-material-expansion-references.md](research/m6-material-expansion-references.md) §5 and §6.7): budget **distinct behaviors** and **trait definitions** separately, since M6's own library batches reach a large definition count by instantiating one parameterized behavior at several levels (radioactive I–III, aftershock I–III), exactly like the 1.12 addons surveyed. Distinct behavior classes: ~25–35 (the four ADR-0004 library batches land near 30). Trait definitions: ~110, once every leveled instance and Track A/B material assignment is counted (`ForgeweaveTraits` already registers 67 at M4 — the old 30–45 band was exceeded two milestones ago). Materials: Forgeweave ships 46 material JSONs today; M6's parity target (Tinkers' Evolution's 97-material roster) plus the Track B ladder lands the total near **170**, well above the old 50–70 plateau — the number the schema, sync payload, creative tab, book and Part Builder are sized against (#846) |
| M7 | **Tinkers' Tool Leveling** (MIT, 16 classes — direct port allowed; clone pinned in CLAUDE.md's upstream table). Armor leveling has no upstream and is original Forgeweave design — no `NOTICE.md` rows for that half | Both questions this row used to defer are answered in the M7 section above: it ships behind `toolLeveling` (on by default, D-M7-3), and there is no modifier cap to interact with — levels add a third additive term to `freeSlots()` and the DEFENSE slot type stays a non-goal (D-M7-1) |
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

- ~~Shape of Apotheosis integration: vanilla-enchanting flag interplay vs. gem sockets as Modifiers (revisit at M8, seam exists via `allowVanillaEnchanting` and the Modifier system).~~ **Answered at M8 planning (D-M8-1, 2026-09-04)**: both, not either. Gem sockets are the `socketed` modifier, Forgeweave gear is affix-eligible, and enchanting runs through the existing flag. See § Milestone 8.
- Which additional 1.12.2 addons beyond TAIGA and Tool Leveling to mine for inspiration.
- ~~EMI support vs. JEI-only long-term (revisit at M8).~~ **Answered at M8 planning (D-M8-2, 2026-09-04)**: spike EMI's JEI-plugin bridge across all 13 categories first, then write a native plugin only for the categories the bridge fails. See § Milestone 8.
- **World-content milestone** (candidate; scoped at M6 planning, epic #824): the End Core and Deep Core smeltery tiers and generic blood melting move into M6 itself (#844, #845), settling the part of this open question M6 planning could answer. ~~A new End ore and slime islands remain unscoped and stay an explicit non-goal everywhere~~ **slime islands shipped** (`worldgen/SlimeIslandStructure`, `worldgen/MagmaSlimeIslandStructure`; issues #629/#632 and #450/#637) — a new End ore, End content, and the remaining island variants (sky, ocean sky, clay) stay unscoped and non-goals until a future milestone picks them up.
- ~~**In-game guide**: JEI + advancements + Ponder scenes carried discovery through M2 to M7, and the in-game book carries it from M4 on. Revisited at M8.~~ **Answered at M8 (maintainer, 2026-09-18, [#974](https://github.com/gkissel/forgeweave/issues/974))**: the book stays and GuideME is a non-goal. See § Milestone 8's non-goals.

## Deferred backlog (decided, awaiting a milestone)

- Per-smeltery GUI toggle to enable/disable auto-alloying (M2 planning, 2026-08-09).
- Sand casts (single-use) if playtests find the gold-cast gate too steep.
- ~~Electric/tiered smeltery heating (M8, alongside Create/Mekanism compat).~~ **Scoped into M8 as D-M8-3 (2026-09-04)**, and not per-mod: one seared heater wall accepting Forge Energy, acting as a fuel rung whose temperature scales with RF/t, driven identically by Mekanism, Powah or anything else exposing the capability. No Create blaze burner seam. See § Milestone 8 and [#972](https://github.com/gkissel/forgeweave/issues/972).
- ~~Embossing reagent revert~~ **Executed in M3.2** (slime crystals ship there, ahead of the world-content milestone; maintainer decision 2026-08-13): `data/forgeweave/forgeweave/embossing_recipe/embossment.json` reverts to green/blue/magma slime crystals + gold block.
- **Material arrows + shuriken (`ProjectileCore` item-projectile infra)** — deferred from M3.5 (2026-08-15); bows already accept both ammo kinds when it lands. Also deferred: javelin, throwing axe, PlusTiC-style energy ranged tool, Fins modifier.
- Embossing's per-tool donor-part gate (#154): upstream refuses a donor part the tool itself does not use (`ModExtraTrait#canApplyCustom`). Forgeweave accepts any buildable part until the tool roster's per-tool part table exists; add the gate with that table.
- **Sceptres** (JC7, #847, maintainer decision 2026-09-02): the parity target's dual melee/ranged magic weapons that fire projectile volleys. A new tool family — item, parts, patterns, assembly, projectile entity, rendering, JEI — M3/M3.5-shaped work needing its own planning issue, not material work.
- **Artifacts** (JC7, #847, maintainer decision 2026-09-02): the parity target's powerful loot-chest gear that cannot be modified or repaired until "unsealed". A tool state plus loot-table integration that touches repair, modifiers and the station UI everywhere; needs its own planning issue.
- ~~**Fusion crafting** (JC7, #847, maintainer decision 2026-09-02): the parity target's multiblock upgrade path that promotes a tool material to its next tier. A new station/multiblock plus a tier-promotion concept the Forgeweave tool model does not have yet; the parity target uses it to serve a beta-on-1.21.1 mod's tier chain (Draconic Evolution, JC5), so it is a poor first use case besides being out of scope for M6.~~ **Answered in M8 by [#915](https://github.com/gkissel/forgeweave/issues/915)** (maintainer decision 2026-09-02), and not the way this bullet assumed: Forgeweave builds no multiblock and invents no tier-promotion concept. It ships a compat layer on Draconic Evolution's own Fusion Crafting multiblock -- the same call the parity target made -- so what a fusion craft produces is a Forgeweave tool with one modifier raised, which the tool model already expresses. The "poor first use case" reservation still stands on its own terms and is what keeps the layer optional: every recipe carries a `mod_loaded` gate, DE is a compileOnly dependency with no dev or gametest runtime presence, and a Forgeweave-only install never classloads a line of it. A Forgeweave station of its own remains out of scope, here and for now.

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
