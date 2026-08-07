# Tinkers' Construct 2 (1.12.2) addon ecosystem survey

**Survey date:** 2026-08-07.

**Method:** primary sources only — each addon's own git repository (README, actual `LICENSE` file contents read via the GitHub REST API, recursive source-tree listings) and its own CurseForge project page. No wikis or community write-ups were used as evidence. Download counts are CurseForge's own lifetime totals. File and class counts are exact counts from git trees, not estimates.

**Provenance rule applied:** per [ADR-0003](../adr/0003-provenance.md), only MIT-licensed upstream material is derivation-eligible; everything else is **inspiration-only** — read it, learn the design, write our own code. Apache-2.0 is flagged inspiration-only because ADR-0003 scopes derivation to MIT specifically; admitting Apache-2.0 would need its own ADR.

## Summary

| Addon | What it adds | License (from the repo's own file) | Derivable? | Scale | Milestone |
| --- | --- | --- | --- | --- | --- |
| TAIGA | 20 ores, 28 alloys, 28 traits | GPL-3.0 | No | 51 Java, 144 textures | M2, M6 |
| Tinkers' Tool Leveling | Tools gain XP, level into extra modifier slots | **MIT** | **Yes** | 16 Java | M7 |
| Construct's Armory | 4 armor slots, 2 stations, 41 traits, 40 modifiers | LGPL-3.0-or-later | No | 199 Java, 245 textures | M4 |
| Tinkers' Complement | Melter, alloy tank, high oven, extra tools, 3 armor sets | **None (all rights reserved)** | No | 93 Java, 178 textures | M2, M3, M4 |
| PlusTiC | Katana + laser gun, 23+ mod material integrations | Apache-2.0 | No | 172 Java, 63 traits | M3, M6, M8 |
| Moar Tinkers | 63 materials, 43 with traits | Unresolved (no mod license file) | No | 44 Java | M6, M8 |
| Tinkers' Addons | Restores TiC1-era modifiers | GPL-3.0 + LGPL, both files present | No | 13 Java | M2 |
| Tinker's JEI | JEI tab for material stats and tool stats | **MIT** | **Yes** | 4 Java | M8 |
| Ceramics | Clay/porcelain buckets, cisterns, faucets, channels | **MIT** | **Yes** | (1.20 default branch) | M2, M5, M8 |
| Tinkers' Mechworks | TConstruct redstone-machinery expansion | None in repo | No | — | low priority |

---

## TAIGA (Tinkers Alloying Addon)

20 new mineable ores and 28 alloys on a difficulty ladder (Tiberium easiest through Vibranium hardest; alloys Triberium through Adamant), each with its own tool trait, integrated into the in-game Tinkers' book — 9.3M downloads, MC 1.10–1.12.2, authors zkafaceTV and randomtz ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/taiga-tinkers-alloying-addon)).

**License: GPL-3.0 — inspiration-only.** The canonical source at [somegit.dev/TAIGA/TAIGA](https://somegit.dev/TAIGA/TAIGA) states "This project is licensed under the conditions of the GNU GPL 3.0", and the GitHub mirror [TartaricAcid/TAIGA](https://github.com/TartaricAcid/TAIGA) carries a `LICENCE` file opening "GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007".

**Scale (mirror's tree):** 473 files, 51 Java classes, 144 textures, 28 `Trait*.java` classes, plus `Alloys.java`, `Blocks.java`, `Fluids.java`, `MaterialTraits.java`.

**Repo status:** accessible but unmaintained — the somegit.dev repo's last commit is 2020-08-27 and its README says "This project needs new maintainers, see #15". GitHub mirrors and forks are readable but staler (TartaricAcid's last push: 2016-07-25).

**Maps to:** **M6** (SCOPE names "material expansion at TAIGA scale" explicitly) and **M2** (the alloy table is smeltery content).

## Tinkers' Tool Leveling

Tools accumulate XP from intended use and each level-up grants an extra modifier slot, with each level costing double the previous level's XP; starting modifiers and XP requirements are configurable. The page warns the system "fundamentally alters game balance since it bypasses Tinkers' Construct's designed modifier limitations" and states "There are currently no plans to update this past 1.12.2" ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-tool-leveling)). 76.4M downloads.

**License: MIT — derivation-eligible.** The repo's `LICENSE` file is the MIT License ([SlimeKnights/TinkersToolLeveling](https://github.com/SlimeKnights/TinkersToolLeveling)).

**Scale:** 54 files, 16 Java classes — a mechanic, not a content pack.

**Repo status: archived** (read-only, last push 2022-01-31), still fully readable.

**Maps to:** **M7**, which SCOPE already names as "Tool Leveling addon-inspired". Being MIT and SlimeKnights-authored, M7 can derive directly rather than only take inspiration — record derived files in `NOTICE.md` per ADR-0003.

## Construct's Armory

Four armor pieces (helmet, chestplate, leggings, boots) built from material parts the way TiC tools are, plus two stations — Armor Station and Armor Forge — and an in-game guidebook, "Materials and You - Armory Addendum". Because "armor isn't quite as abstract as tools are", variety is carried by traits and modifiers rather than by many armor types ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/constructs-armory)). 41.4M downloads, MC 1.12.2 and 1.16.5.

**License: LGPL-3.0-or-later — inspiration-only.** The repo's `LICENSE` reads "Copyright (C) 2018-2019 C4 … under the terms of the GNU Lesser General Public License … either version 3 of the License, or (at your option) any later version" ([illusivesoulworks/constructsarmory](https://github.com/illusivesoulworks/constructsarmory)). GitHub reports the SPDX id as NOASSERTION, so the sidebar is unreliable here — the file is authoritative.

**Scale:** 887 files, 199 Java classes, 245 textures, **41** armor trait classes under `common/armor/traits/` and **40** classes under `modifiers/`. The largest addon surveyed.

**Repo status:** active, not archived, last push 2024-08-04; branches `master` (the 1.12.2 line) and `1.16.5`. Moved from `TheIllusiveC4/ConstructsArmory` to the `illusivesoulworks` org; old URLs redirect.

**Maps to:** **M4** primarily; also worth reading at **M2**, since armor modifiers reuse the tool modifier machinery.

## Tinkers' Complement

Breaks the smeltery into automatable components and fills gaps TiC itself declined to fill: a Melter (melts ores/ingots without ore doubling), a bucket cast, an Alloy Tank for easier alloy automation, a High Oven multiblock producing steel, knightslime and pig iron, and porcelain variants of the melter, casting table, basin, heater, tanks and alloy tanks when Ceramics is present. Adds a TiC chisel (with Chisel) and a sledge hammer (with Ex Nihilo), plus Manyullyn, Knightslime and Steel armor sets ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-complement)). 31.5M downloads; author KnightMiner, lead developer of Tinkers' Construct itself.

**License: none — inspiration-only, treat as strictly all-rights-reserved.** CurseForge states "All Rights Reserved" and the repo has **no license file at all**: the root listing is `.gitignore, Icon.png, Jenkinsfile, README.md, build.gradle, build.properties, gradle, gradlew, gradlew.bat, settings.gradle, src`, and GitHub's license endpoint returns 404 ([KnightMiner/TinkersComplement](https://github.com/KnightMiner/TinkersComplement)). Read for design; copy nothing.

**Scale:** 574 files, 93 Java classes, 178 textures. **Repo status:** accessible, not archived, last push 2020-08-22.

**Maps to:** **M2** (the decomposed-smeltery design is the ecosystem's strongest reference for making the smeltery automatable), **M3** (extra tool types), **M4** (fixed-material armor sets as the cheap alternative to part-based armor).

## PlusTiC

Two tools — a Katana ("a fast two-handed weapon that deals increasing damage the more mobs you kill") and a Laser Gun ("a ranged weapon that requires durability and energy (Forge, Tesla, RF)") — plus material integrations for 23+ mods including Botania, Mekanism, Thermal Foundation, Draconic Evolution, Applied Energistics 2 and Thaumcraft ([TeamDman/PlusTiC README](https://github.com/TeamDman/PlusTiC)).

**License: Apache-2.0 — inspiration-only under ADR-0003.** `LICENSE.md` is the Apache License 2.0 on both the maintained fork ([TeamDman/PlusTiC](https://github.com/TeamDman/PlusTiC)) and upstream [Mike-T4/PlusTiC](https://github.com/Mike-T4/PlusTiC).

**Scale:** 431 files, 172 Java classes, 101 textures, **63** trait classes, **9** tool classes.

**Repo status, and a caution:** the original author's (Landmaster's) CurseForge project is gone. The surviving distribution is TeamDman's fork, published as [xXx_MoreToolMats_xXx](https://www.curseforge.com/minecraft/mc-mods/plusticminusbad) (11.2M downloads, Apache-2.0), whose own page describes it as a PlusTiC fork and states "the main concern has been confirmed removed, and I built the jar myself". Read source from GitHub; do not pull PlusTiC binaries from anywhere.

**Maps to:** **M3** (kill-stacking melee damage and an energy-consuming ranged tool are good shapes for the modern-era tool slot), **M6** (63 traits is a large design sample), **M8** (the clearest 1.12 example of cross-mod material registration).

## Moar Tinkers

63 tool material types drawn from Thermal Expansion, Mekanism, EnderIO, Botania, Draconic Evolution and others, 43 of them carrying a special trait — named examples include Energy Repair, Mana Eater, Radioactive, Critikill, Payback, Ender Magnetic and Shock ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/moar-tinkers)). 15.9M downloads, MC 1.10.2–1.12.2, author AshuraNoYami, last updated 2018-07-06.

**License: unresolved — inspiration-only.** CurseForge claims GPLv3 and links `github.com/YaibaToKen/MoarTinkers`, which redirects to [MinecraftModDevelopmentMods/MoarTinkers](https://github.com/MinecraftModDevelopmentMods/MoarTinkers), a fork of [Bartz24/MoarTinkers](https://github.com/Bartz24/MoarTinkers). Neither carries a mod license file: the only license-shaped file in Bartz24's root is `LICENSE-new.txt`, whose contents are the **Minecraft Forge MDK license** ("Minecraft Forge is licensed under the terms of the LGPL 2.1…"), not a grant covering the mod. GitHub reports NOASSERTION. The CurseForge GPLv3 claim is unsupported by anything in the repo — do not derive.

**Scale:** 77 files, 44 Java classes, 23 of them `Trait*.java`; content lives in `registry/ModMaterials.java`, `ModAlloys.java`, `ModFluids.java`, `ModTraits.java`. **Repo status:** accessible, not archived; fork last pushed 2021-05-04, upstream 2019-10-26.

**Maps to:** **M6** (an independent ~60-material data point alongside TAIGA), **M8** (per-mod material gating).

## Tinkers' Addons (oitsjustjose)

Restores modifier functionality that existed in TiC 1 but was dropped in TiC 2 — the description is "Adding Back old TiCon Modifiers to Tinkers' Construct 2" ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-addons)). 9.5M downloads, MC 1.10.2–1.12.2, last updated 2017-09-06. The source tree confirms the shape: `modifiers/ModAutoRepair.java`, `modifiers/ModExtraModifier.java`, `items/ItemModifier.java`.

**License: GPL-3.0 per CurseForge — inspiration-only.** The repo root contains **two** license files, `GNU GENERAL PUBLIC LICENSE.md` and `GNU LESSER GENERAL PUBLIC LICENSE.md`, so GitHub detects no SPDX license. Either way it is copyleft, not MIT.

**Scale:** 58 files, 13 Java classes, 6 textures. **Repo status: archived** on GitHub ([oitsjustjose/TiCon-Addons](https://github.com/oitsjustjose/TiCon-Addons)), README reads "This Repo has Moved!" and points at `https://git.oitsjustjose.com/me/TiCon-Addons`; the archived copy is still readable.

**Maps to:** **M2**, as a short worked example of adding a modifier item plus two modifier behaviours on top of a base modifier system.

## Tinker's JEI

A JEI tab for looking up material stats plus an info screen for tool stats. 19.9M downloads, and **1.12.2 is the only game version listed** — this is a genuine 1.12.2 addon ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-jei)).

**License: MIT — derivation-eligible.** The repo has a `LICENSE` file detected as MIT ([PssbleTrngle/TinkersJEI](https://github.com/PssbleTrngle/TinkersJEI)).

**Scale:** 41 files, 4 Java classes — one JEI plugin plus a stats screen. **Repo status:** accessible, not archived, last push 2025-06-12.

**Maps to:** **M8**, and it is the closest thing to a reference implementation for M1's JEI plugin deliverable.

## Ceramics

SlimeKnights-adjacent (same author as Tinkers' Construct). Clay and porcelain content: terracotta buckets for early-game fluid transport that crack when filled with hot fluids, clay plate armor, cisterns in 33 colour variants, fluid gauges, faucets and channels, plus porcelain and decorative brick blocks. It auto-generates fluid variants for fluids from other mods including Tinkers' Construct, and was "originally inspired by the clay bucket from Iguana Tinker Tweaks" ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ceramics)). 50.4M downloads.

**License: MIT — derivation-eligible.** The repo's `LICENSE` file is the MIT License ([KnightMiner/Ceramics](https://github.com/KnightMiner/Ceramics)).

**Repo status:** actively maintained — default branch `1.20`, last push 2025-07-10. The 1.12.2 code sits on an older branch, so scale counts from the default branch would not describe the 1.12 version and are omitted.

**Maps to:** **M2** (faucets, channels and fluid gauges are adjacent to smeltery plumbing), **M5** (the cracking bucket is a good gadget-tier failure mode), **M8** (auto-generating per-fluid block variants is a clean cross-mod compat pattern).

## Tinkers' Mechworks

SlimeKnights' own TConstruct expansion, description "TConstruct expansion", last push 2025-01-11 ([SlimeKnights/TinkersMechworks](https://github.com/SlimeKnights/TinkersMechworks)). **No license file exists in the repo root** and GitHub reports no license, so despite SlimeKnights authorship it is **not** derivation-eligible. It is redstone machinery rather than tool or smeltery content — low priority, listed for completeness.

## Correction: "Just the Tips"

There is no Tinkers' Construct addon by this name. The CurseForge project [JustTheTips](https://www.curseforge.com/minecraft/mc-mods/justthetips) (DeflatedPickle, MIT, 6.3M downloads, 1.10.2–1.12.2) "Adds some tips to loading screens" and is unrelated to Tinkers'. The 1.12.2 stat-display niche is covered by **Tinker's JEI** above. The older [TiC Tooltips](https://www.curseforge.com/minecraft/mc-mods/tic-tooltips) by squeek502 (19.6M downloads, Public Domain / Unlicense, [squeek502/TiC-Tooltips](https://github.com/squeek502/TiC-Tooltips)) filled that role in the TiC 1 era but **stops at 1.7.10** — last updated 2015-07-11 — because TiC 2 absorbed tooltip stats into the base mod.

---

## Implications for Forgeweave milestones

**M2 — smeltery, metal materials, modifiers.** Study **Tinkers' Complement** first: its Melter / Alloy Tank / High Oven decomposition is the ecosystem's strongest argument for designing the smeltery as separable, automatable blocks rather than one monolith. Read **TAIGA**'s `Alloys.java` for how a large alloy table is expressed, **Tinkers' Addons** (13 classes) as a minimal worked example of extending the modifier system, and **Construct's Armory**'s 40 modifier classes for the range a mature modifier system reaches. None are derivation-eligible — design lessons only.

**M3 — full tool roster, sword and combat tuning.** **PlusTiC**'s Katana (damage scaling with kill count) and Laser Gun (durability plus an energy budget) are the two most-copied non-vanilla tool shapes in 1.12.2 and a reasonable model for the modern-era tool slot. **Tinkers' Complement**'s chisel and sledge hammer show how to gate an extra tool behind another mod's presence.

**M4 — armors.** **Construct's Armory** is the reference, as SCOPE already says. Two things to lift as design (not code — it is LGPL): the two-station split (Armor Station for assembly, Armor Forge for modifiers), and the decision to keep exactly four armor slots and push all variety into 41 traits plus 40 modifiers. **Tinkers' Complement**'s fixed Manyullyn / Knightslime / Steel sets are the cheap fallback if part-based armor proves too expensive for the milestone.

**M5 — gadgets.** Thin pickings; no surveyed addon is gadget-focused. **Ceramics** (MIT, derivation-eligible) is the useful one — its terracotta bucket that cracks when filled with a hot fluid is a well-liked example of a gadget with a designed failure mode, which is the tone slime boots and the slingshot want.

**M6 — material expansion at TAIGA scale.** Three independent data points to size the registry against: **TAIGA** (20 ores + 28 alloys + 28 traits, self-contained progression from its own worldgen), **Moar Tinkers** (63 materials, 43 with traits, all sourced from other mods), and **PlusTiC** (63 trait classes). The lesson for the datapack material model (ADR-0002) is that roughly 50–70 materials with 30–45 distinct traits is where 1.12.2 addons plateaued — that is the number the JSON schema and the material-selection UI must stay usable at. All three are inspiration-only.

**M7 — tool leveling.** **Tinkers' Tool Leveling** is MIT and SlimeKnights-authored, making it the one addon here Forgeweave can derive from directly, and at 16 Java classes a port is small. Two design facts to carry across: XP-per-level doubles, and the mod's own page warns the mechanic bypasses TiC's deliberate modifier cap — so ship it behind a config flag with the cap interaction decided up front rather than discovered. Record derived files in `NOTICE.md` per ADR-0003.

**M8 — deep compat.** **Tinker's JEI** is MIT and only 4 Java classes; it is both the M8 reference and a usable starting point for the M1 JEI plugin deliverable. **Ceramics** (also MIT) shows auto-generating per-fluid block variants across mod boundaries. **PlusTiC** and **Moar Tinkers** are the two large examples of conditional per-mod material registration — read them for the gating pattern, write our own.
