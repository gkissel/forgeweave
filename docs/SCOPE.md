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
| Modifiers (D15, D16) | Single slot pool (`DEFAULT_SLOTS`), no DEFENSE slot type (was a candidate for M7 alongside the cap decision; **M7 planning resolved both** — see § Milestone 7, D-M7-1: leveling grants into the single pool and the DEFENSE slot type is a permanent non-goal). New armor-only (predicate `armorOnly()` next to `harvestOnly()`): fire/blast/projectile/magic/melee protection, knockback resistance, thorns. Existing generic modifiers (reinforced, mending moss, soulbound, extra slot…) apply where the predicate allows. 1.20 abilities (double jump, zoom, bouncy, flamewake…) are backlog for M5/M6 — bouncy would collide with M5 slime boots. |
| Defense seam (D8) | `CombatSeams.defensePass` walks the defender's four equipped pieces; new `Trait.onDefend(CombatDefense)` hook. This is also M7's leveling entry point for armor — see § Milestone 7 — tool leveling, **D-M7-2**, which adds the per-piece attribution `DefendedBlow` deliberately left out here. |
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

`neoforge:conditions` existence gating on datapack-registry entries, covering every consuming surface (creative tab, JEI, guide book, Part Builder, tool assembly) with zero call-site changes (prep doc §1.4). The ADR-0004 parameterized behavior library (three non-armor batches committed; the armor batch is JC8-gated). The Forge Energy tool buffer and its energy/solar/kinetic behaviors. Track B's own ore/alloy/trait ladder with full smeltery integration (Track B materials always exist, so the reasoning that once kept Track A Part-Builder-only never applied to them). The two new smeltery core tiers (End, Deep) with pour-to-transform. Generic overworld blood melting and the blazing-blood fuel entry. UI/schema hardening at the final 128-material scale (#846): creative-tab part-variant volume, registry sync payload size, guide-book material-section pagination, Part Builder `crafting_items` match performance.

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

- JC1 (#842) — whether the ~54 Tinkers' Evolution materials with no 1.21.1 mod build (Botania, Blood Magic, Thermal Series, Thaumcraft, IndustrialCraft 2, Environmental Tech, Natura, Astral Sorcery, Forestry, Advanced Solar Panels, AE2's fluix-steel) ship as dormant condition-gated presets or are skipped entirely.
- JC4 — whether mods with no 1.12-addon-roster ancestor (Create, Ars Nouveau, Twilight Forest, Ad Astra, Allthemodium, Silent Gear, Mystical Agriculture, Powah, Occultism, Modern Industrialization, GTCEu) are in scope beyond Modern Industrialization and Powah.
- ~~JC6 (#832) — whether ADR-0004's datapack-creatable trait-definition registry ships in M6 (scoped to traits) or the ADR is amended to defer it.~~ Resolved 2026-09-02: ships in M6, traits only, plus the KubeJS binding — see the section above.
- JC8 (#831) — whether the armor trait behavior library ships in M6 or a later armor milestone.

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
- **Save-compat fixtures (same PR as the format)**: `m7_tool_level.snbt` (a pickaxe with `tool_level` filled and a modifier spent into the earned slot) and `m7_armor_level.snbt` (a chestplate ditto). The `LivingEntity` attachment serializes into *entity* NBT, not an item stack, so it does not fit the item/material-shaped corpus — M7-8 confirms whether `SaveCompatCorpusTest` grows an entity-NBT case or the attachment is covered by the save/load GameTest above, and records which.
- **Manual release-checklist adds**: level a tool by hand on the dedicated server and confirm the chat line, the chime, and that the tooltip colour actually rotates between levels (a hue no automated test can see) · check the level line at a wrapped level and an easter-egg level by editing `tool_level` with `/data` on a held tool · JEI sanity (no new categories; the Tool Station panel reflects the earned slot) · previous-release world load carrying both a pre-M7 tool and a leveled one · load a world with leveled tools under `toolLeveling = false` and confirm nothing breaks and no slot is lost.
- Alpha tags during the milestone; the save-compat promise stays binding throughout.

### Issue roadmap

| # | Deliverable | Depends on |
| --- | --- | --- |
| M7-1 | `tool_level` data component, the level curve, `ToolConstants.Entry.baseXp`, the `toolLeveling`/`defaultBaseXP`/`levelMultiplier`/`maximumLevels` config entries, and the shared `addXp` API | — |
| M7-2 | XP gain: mining + melee, with the `LivingEntity` damage attachment paid out on death | M7-1 |
| M7-3 | XP gain: ranged impact + the utility grants (mattock till, AoE harvest, shovel path, blocking) | M7-1 |
| M7-4 | Level-up grants a modifier slot through `freeSlots()`; the Tool Station shows it | M7-1 |
| M7-5 | Level-up feedback: chat line, `chime.ogg`, the tooltip ladder and its hue | M7-1 |
| M7-6 | Armor leveling on the defense seam, incl. per-piece attribution in `DefendedBlow` | M7-1, M7-4 |
| M7-7 | Guide-book page, plus a Ponder scene only if one fits | M7-1 … M7-6 |
| M7-8 | Save-compat fixtures, GameTest sweep, release-checklist lines, acceptance playthrough | all |

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
| M8 | Deep compat: Apotheosis, Curios, Jade/WTHIT, EMI, Mekanism, and other major mods by adoption. First slice shipped: **Draconic Evolution fusion crafting** ([#915](https://github.com/gkissel/forgeweave/issues/915), maintainer decision 2026-09-02) -- fusion recipes for the two smeltery core tiers that have a single route each today, plus a tool-upgrade ladder (8 modifier lines x DE's 4 tech levels) that runs on DE's own multiblock. compileOnly, `mod_loaded`-gated, no code derived from DE (its "Don't Be a Jerk" license permits the library dependency but not derivation, so ADR-0003 treats it like the inspiration-only clones) | M4 (Curios needs armors/gadgets) |
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
