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

Forgeweave ores or worldgen for lead/silver/tin (tag-gated only) · slime islands, purple slime, blue slime spawns (world-content milestone; blue slime crystal gets a crafting recipe instead) · bowstring/arrow-shaft/fletching material stats (M3.5) · alubrass as a tool material · modern-branch materials needing mod-only items (slimesteel, queens slime, cinderslime, hepatizon, blazing bone, necrotic bone, venombone, seared/scorched stone, slimewood, whitestone) · Twilight Forest compat materials · mod-compat metals beyond the four tag-gated ones (M8) · bow stat axes (M3.5).

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

Fresh world, dedicated server, no cheats. Craft an **obsidian chestplate plating** at the Part Builder (non-metal platings are Part Builder parts, the cast bootstrap) and a **vine maille**; pour gold over the plating to get the plating cast. Melt iron in the smeltery, cast **iron plating** for all four pieces and an **iron maille**. At the Armor Station (issue #782, reversing D13 — armor no longer assembles at the Tool Station or Tool Forge), assemble helmet, chestplate, leggings, boots from plating + maille; each piece's tooltip shows armor/toughness/knockback resistance/durability matching the 1.20-derived iron values (chestplate armor 5, durability 240). Wear the set: third-person render shows the iron-tinted plate layer over the maille layer. Take damage: durability drops on the plating, damage is reduced by the computed armor. Break a piece to 0 durability: it stays equipped but protects nothing; repair it with an iron ingot at the Armor Station. Apply **fire protection** (defense modifier) to the chestplate and **thorns** to the leggings; walk into lava briefly and get hit by a zombie — fire damage is reduced and the zombie takes thorns damage. Assemble a cobalt-plated helmet and observe **melee protection** (cobalt's ARMOR trait). Save, restart, reload: all four pieces keep parts, modifiers and durability. Everything shows in JEI with no JEI code changes; the book's armor section and the armor Ponder scene open.

### Content manifest

| Kind | Contents |
| --- | --- |
| Pieces (D3, D9) | Exactly four: helmet, chestplate, leggings, boots. **Two parts each**: `plating_<piece>` (all stats) + `maille` (statless: traits + inner texture layer). No fixed-material sets (travellers' gear, slimesuit, slime wings are non-goals). |
| Part items | 4 platings + 1 maille; patterns and gold/clay casts for all five; Part Builder recipes for non-cast-only materials; smeltery casting for metals (D12 — same M2 flow, no crafting-table bootstrap). |
| Plating materials (D10) | The 15 Forgeweave materials with a 1.20 `PlatingMaterialStats` row — iron, copper, cobalt, manyullyn, knightslime, pig iron, steel, bronze, lead, silver, electrum, amethyst bronze, rose gold, obsidian, ancient — with per-piece `durability/armor/toughness/knockback_resistance` derived from the clone; plus **ardite, netherite, nahuatl** by interpolation (ardite ≈ cobalt, netherite > manyullyn, nahuatl ≈ obsidian; values proposed on the issue, maintainer-reviewed). 18 total. Wood/stone/bone/paper/etc. make no plating (parity). |
| Maille materials (D11) | The 18 above + vine, chorus, **bone, cactus, blue slime vine** = 23. |
| Material schema (D14, D17) | New `PartItem.Kind.PLATING`/`MAILLE`; `Material` gains `plating{helmet,chestplate,leggings,boots}` blocks, `maille` marker, and `traits.armor` (scoped list read by both plating and maille). New `ARMOR_STATS` data component (precedent `LAUNCHER_STATS`); `TOOL_STATS` shape untouched. `ToolConstants.Category.ARMOR`. |
| Station (D13, **reversed 2026-08-28 by maintainer decision, issue #782**) | ~~Tool Station **and** Tool Forge assemble armor (plating + maille, positional `ENTRIES` rows); no `large_tools` gate — the smeltery is the real gate. No armor station: Armory's two-station split existed because it could not touch the Tinkers' station; Forgeweave can.~~ **Reversed:** a new **Armor Station** block assembles armor exclusively (plating + maille, the same positional `ENTRIES` rows, still no `large_tools` gate — the smeltery is still the real gate); the Tool Station and Tool Forge no longer build armor at all. The generic `ENTRIES`/`ToolConstants.Category.ARMOR` machinery is unchanged and shared, not duplicated — only which station's tab list and assembly resolver accept `Category.ARMOR` entries changed. |
| ARMOR traits (D17) | 12 ported from the clone's ARMOR-scope table: iron projectile_protection · copper depth_protection · obsidian blast_protection · cobalt melee_protection · manyullyn warded · amethyst bronze crystalstrike · silver consecrated · knightslime overshield + overslime (#728: the clone's overslime pool, 50 per trait, -0.5 armor without an overslime_friend maille — blue slime vine, chorus — refilled with slime at the station; armor only) · bone piercing_guard · cactus thorns · chorus enderclearance · blue slime vine skyfall. The four protections share one implementation with the modifier of the same name (trait = level 1, parity). Materials without a clone ARMOR row keep only their general traits; general traits attach unfiltered — hooks that do not apply simply never fire. |
| Modifiers (D15, D16) | Single slot pool (`DEFAULT_SLOTS`), no DEFENSE slot type (candidate for M7 alongside the cap decision). New armor-only (predicate `armorOnly()` next to `harvestOnly()`): fire/blast/projectile/magic/melee protection, knockback resistance, thorns. Existing generic modifiers (reinforced, mending moss, soulbound, extra slot…) apply where the predicate allows. 1.20 abilities (double jump, zoom, bouncy, flamewake…) are backlog for M5/M6 — bouncy would collide with M5 slime boots. |
| Defense seam (D8) | `CombatSeams.defensePass` walks the defender's four equipped pieces; new `Trait.onDefend(CombatDefense)` hook. This is also M7's leveling entry point for armor. |
| Render (D18) | Derive only the clone's **grayscale bases** (plating armor/leggings, maille armor/leggings) + port the copier as `scripts/derive_armor_art.py`, tinting with `Material.color` at render time (`getArmorLayerTintColor`; maintainer decision #726 replaced #679's generation-time tint) — covers ardite/netherite/nahuatl, which have no clone PNG. Two-layer armor model (plating over maille). NOTICE rows for the bases only. |
| Durability/repair (D19) | Durability = plating's `durability`; repair with the plating material's `repair_item` at the Tool Station, same 5% discount; maille never affects durability. |
| Enchanting (D20) | Same `allowVanillaEnchanting` flag as tools; `enchantability` from the plating material. |
| Compat (D7) | Vanilla armor slots only; chestplate excludes Elytra as vanilla does (Elytra stays the flight option for now). Curios, Apotheosis, wings — M8. |
| Book / Ponder / harness (D21) | Data-driven `armor.json` book section; armor-assembly Ponder scene (second registered storyboard); screenshot-harness scene with the four pieces worn in third person. |

### Non-goals for M4

Travellers' gear, slimesuit, slime wings, shields (1.20 content, not mechanics) · DEFENSE slot type · 1.20 armor abilities · plating for non-metal materials beyond obsidian/ancient/nahuatl · Curios/Elytra integration (M8) · custom designer models (M9) · armor leveling (M7 plugs into `onDefend`) · an "armor forge" tier (issue #782: the smeltery is still the only gate, one Armor Station is enough). ~~An armor station block was a planned non-goal~~ — **reversed by issue #782** (see the Station row above): Forgeweave now ships one.

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
| Trait/behavior classes (~30 new Java) | ADR-0004 parameterized-behavior-library batches: damage-scaling (#827), on-hit effect (#828), utility/economy + reuse audit (#829), Forge Energy tool buffer + energy/solar/kinetic behaviors (#830); armor behaviors on the M4 `onDefend` seam (#831) are JC8-gated, not committed to M6 yet |
| Blocks/fluids | Track B ore blocks and worldgen features; Track B molten fluids, melting/casting rows, and alloy table (#840); End Core and Deep Core smeltery tiers with a pour-to-transform mechanic (#845) |
| World content | Generic overworld-mob blood melting and a `smeltery_fuel` entry for blazing blood, plus a meltable dragon breath (#844) — the remaining pieces of #181; blood/blazing blood/deep blood items and their entity-melting recipes already shipped via #270 |
| Schema | `neoforge:conditions` on `Material` and its companion melting/casting/alloy/embossing datapack-registry entries (existence gating, mechanism already in NeoForge 21.1's `RegistryDataLoader`, prep doc §1.4); bronze/lead/silver/electrum migrate off tag-only gating to conditions (#826); ADR-0004 parameterized behavior classes with `id + level` serialization; a Forge Energy tool-component buffer |
| Compat mechanism | `neoforge:mod_loaded` / `neoforge:item_exists` conditions, `or`-combined across every known 1.21.1 provider of a metal name for re-homed materials (JC2, #833) rather than one preset per modid |

### In scope (systems)

`neoforge:conditions` existence gating on datapack-registry entries, covering every consuming surface (creative tab, JEI, guide book, Part Builder, tool assembly) with zero call-site changes (prep doc §1.4). The ADR-0004 parameterized behavior library (three non-armor batches committed; the armor batch is JC8-gated). The Forge Energy tool buffer and its energy/solar/kinetic behaviors. Track B's own ore/alloy/trait ladder with full smeltery integration (Track B materials always exist, so JC3's Part-Builder-only reasoning does not apply to them). The two new smeltery core tiers (End, Deep) with pour-to-transform. Generic overworld blood melting and the blazing-blood fuel entry. UI/schema hardening at the ~170-material scale (#846): creative-tab part-variant volume, registry sync payload size, guide-book material-section pagination, Part Builder `crafting_items` match performance.

### Non-goals for M6

Bolts (cut earlier, unrelated to M6) · mining tiers above netherite — JC10 decided: no new tool-tier block tags, differentiate Track B by stats/traits/obtainability instead (epic #824, #838 records the decision and the tier-scaffold mapping onto the five existing rungs; [research doc](research/m6-material-expansion-references.md) §7.1) · Track B material names — JC9 decided: original Forgeweave coinages, not the reference ladder's own names (epic #824; a starting id-vocabulary proposal for #839–#841 to consume is in the [research doc](research/m6-material-expansion-references.md) §7.3) · meteor-fall ore sourcing — JC11's recommended answer cuts it for M6 in favor of ore veins or a rare surface feature (#839) · slime islands, purple/blue slime spawns (world-content milestone, unchanged non-goal since M2/M3.2) · the parity target's non-material mechanics — sceptres, Artifacts, fusion crafting, an in-game materials handbook, the melt-speed multiplier, and the damage-cap toggle are split per JC7 (#847): the two config tweaks and the handbook audit may ride any M6 batch, but sceptres/Artifacts/fusion crafting belong to their own milestone · Track A dedicated molten fluids/casting per modded metal — JC3's recommended answer keeps Track A Part-Builder-only, matching the four existing compat metals, revisited at M8 · GTCEu (skip until ids can be dumped from a running instance, JC5).

**Open — maintainer decisions pending, tracked on their epic #824 child issue, not guessed at here:**

- JC1 (#842) — whether the ~54 Tinkers' Evolution materials with no 1.21.1 mod build (Botania, Blood Magic, Thermal Series, Thaumcraft, IndustrialCraft 2, Environmental Tech, Natura, Astral Sorcery, Forestry, Advanced Solar Panels, AE2's fluix-steel) ship as dormant condition-gated presets or are skipped entirely.
- JC4 — whether mods with no 1.12-addon-roster ancestor (Create, Ars Nouveau, Twilight Forest, Ad Astra, Allthemodium, Silent Gear, Mystical Agriculture, Powah, Occultism, Modern Industrialization, GTCEu) are in scope beyond Modern Industrialization and Powah.
- JC6 (#832) — whether ADR-0004's datapack-creatable trait-definition registry ships in M6 (scoped to traits) or the ADR is amended to defer it.
- JC8 (#831) — whether the armor trait behavior library ships in M6 or a later armor milestone.

### CI and release gates

- **GameTest coverage**: existence-gating negative path (a conditioned material is absent from the synced registry, creative tab, JEI, and book) and positive path (`mod_loaded`/`item_exists` against a modid loaded in the dev/test environment or a gametest-registered item, prep doc §1.4 item 6); one test per new parameterized behavior class per library batch; Forge Energy spent before durability; each smeltery core tier's yield and the End→Deep pour-to-transform step; generic overworld-mob blood melting; blazing-blood `smeltery_fuel` entry burns hotter than lava; Track B alloy ratios and ore-to-ingot yield.
- **Unit gates**: `MaterialSyncSizeTest`'s budget re-measured at M6 scale (11 materials measured ~430 B each at 32 KiB; ~170 materials projects to ~73 KiB, over twice the current budget — #846 resolves the budget itself).
- **Save-compat fixtures (same PR as the format; corpus is CI-gating)**: any new tool-component fields from the Forge Energy buffer and new leveled trait definitions; End Core/Deep Core block-entity NBT (structure bounds, tier state); a fixture snapshotting a tool built from a Track A and a Track B material each.
- **Manual release checklist adds**: JEI-installed sanity check twice — once with Mekanism present (Track A reference mod, materials appear) and once absent (confirm existence gating: no ghost entries in creative, JEI, the book, or the Part Builder); spark profile at ~170-material scale; dedicated-server acceptance playthrough; previous-release world load.
- Alpha tags during the milestone; the save-compat promise stays binding throughout (M6 ships after `mc1.21.1-v0.4.0-beta.4`).

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
| M7 | Tool leveling (derived from Tinkers' Tool Leveling) | M3 |
| M8 | Deep compat: Apotheosis, Curios, Jade/WTHIT, EMI, Mekanism, and other major mods by adoption | M4 (Curios needs armors/gadgets) |
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
- **World-content milestone** (candidate; scoped at M6 planning, epic #824): the End Core and Deep Core smeltery tiers and generic blood melting move into M6 itself (#844, #845), settling the part of this open question M6 planning could answer. A new End ore and slime islands remain unscoped and stay an explicit non-goal everywhere until a future milestone picks them up.
- **In-game guide**: JEI + advancements + Ponder scenes carry discovery through M2–M7; a full GuideME-based guide is revisited at M8.

## Deferred backlog (decided, awaiting a milestone)

- Per-smeltery GUI toggle to enable/disable auto-alloying (M2 planning, 2026-08-09).
- Sand casts (single-use) if playtests find the gold-cast gate too steep.
- Electric/tiered smeltery heating (M8, alongside Create/Mekanism compat).
- ~~Embossing reagent revert~~ **Executed in M3.2** (slime crystals ship there, ahead of the world-content milestone; maintainer decision 2026-08-13): `data/forgeweave/forgeweave/embossing_recipe/embossment.json` reverts to green/blue/magma slime crystals + gold block.
- **Material arrows + shuriken (`ProjectileCore` item-projectile infra)** — deferred from M3.5 (2026-08-15); bows already accept both ammo kinds when it lands. Also deferred: javelin, throwing axe, PlusTiC-style energy ranged tool, Fins modifier.
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
