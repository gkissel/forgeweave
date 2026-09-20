# Twilight Forest and Ice and Fire compat survey

**Survey date:** 2026-09-18.

**Method:** primary sources only. Both mods were fetched from their real 1.21.1 NeoForge branches (GitHub REST API for repository metadata, file contents and tags; Modrinth's API to confirm which Ice and Fire fork actually ships for 1.21.1). Every item id, tag and stat below was read from the mod's own Java source or generated tag JSON at the pinned commit, not from memory or a wiki paraphrase. Claims that could not be read from source are marked "from general knowledge" or "not independently verified" rather than stated as fact; none of those claims feed a preset.

## The Twilight Forest

**Version and source.** `TeamTwilight/twilightforest`, branch `1.21.1` (the branch actually built against `minecraft_version=1.21.1`), commit history current as of 2026-09-18. `gradle.properties`: `mod_version=4.8`, `minecraft_version=1.21.1`, `neo_version=21.1.209`. NeoForge, confirmed from `build.gradle`'s `neoForge { ... }` block.

**License.** Split, as the issue expected. `LICENSE` (repository root) is the GNU Lesser General Public License 2.1, covering code. `ASSET_LICENSE` is Creative Commons Attribution-NonCommercial-ShareAlike 4.0, covering "non-code, non-sound and non-structure" assets under `src/main/resources/assets`. The mod's own `README.md` adds a third bucket: sound assets and structure NBT/JSON are **All Rights Reserved**, carved out from the CC license entirely. GitHub's license detector reports "Other"/`NOASSERTION` for the repository as a whole because no single SPDX id covers all three buckets -- that matches the issue's own expectation. None of the three licenses is MIT, so nothing here is derivation-eligible under ADR-0003: this section is read-only inspiration, the same rule the TAIGA and Moar Tinkers clones already follow.

### Material roster

Every material below was cross-checked against `TFArmorMaterials.java` (armor), `TFToolMaterials.java` (tool tiers), `BlockTagGenerator.java` (harvest-tier tag inheritance, which is how the mod expresses "this material mines at iron/diamond/netherite level" without vanilla's old integer harvest level), and `ItemTagGenerator.java` (`c:` tags). All four "read in source."

| Material | Item id(s) | `c:` tag | Harvest tier (source: `BlockTagGenerator`) | Tool set | Armor set |
| --- | --- | --- | --- | --- | --- |
| Ironwood | `twilightforest:ironwood_ingot` (ingot), `raw_ironwood` (raw), `ironwood_block` (storage) | `c:ingots/ironwood`, `c:raw_materials/ironwood`, `c:storage_blocks/ironwood` | `minecraft:incorrect_for_iron_tool` (`INCORRECT_FOR_IRONWOOD_TOOL` adds that vanilla tag wholesale) | sword/shovel/pickaxe/axe/hoe (`TFToolMaterials.IRONWOOD`: 512 durability, 6.5 speed, +2 damage, ench 25) | full 4-piece (`TFArmorMaterials.IRONWOOD`) |
| Steeleaf | `twilightforest:steeleaf_ingot`, `steeleaf_block` | `c:ingots/steeleaf`, `c:storage_blocks/steeleaf` | `minecraft:incorrect_for_diamond_tool` | sword/shovel/pickaxe/axe/hoe (`STEELEAF`: 131 durability, 8.0 speed, +3 damage, ench 9) | full 4-piece |
| Knightmetal | `twilightforest:knightmetal_ingot`, `knightmetal_block` | `c:ingots/knightmetal`, `c:storage_blocks/knightmetal` | `minecraft:incorrect_for_diamond_tool` | sword/pickaxe/axe (no shovel/hoe item; the mod's own `KnightmetalSwordItem`/`PickItem`/`AxeItem` plus a `KnightmetalShieldItem`) (`KNIGHTMETAL`: 512 durability, 8.0 speed, +3 damage, ench 8) | full 4-piece, plus the separate `phantom` set built from the same ingot |
| Fiery | `twilightforest:fiery_ingot` | `c:ingots/fiery` | `minecraft:incorrect_for_netherite_tool` | sword/pickaxe only (`FierySwordItem`/`FieryPickItem`) (`FIERY`: 1024 durability, 9.0 speed, +4 damage, ench 10) | full 4-piece, every piece `.fireResistant()` |
| Naga scale | `twilightforest:naga_scale` | none (bare item) | n/a, armor only | none | chestplate + leggings only (`TFArmorMaterials.NAGA`; no helmet/boots piece exists) |
| Arctic fur | `twilightforest:arctic_fur` | `twilightforest:arctic_fur` (mod-namespace tag, not `c:`) | n/a, armor only | none | full 4-piece, two-layer render (dyed + overlay) |
| Alpha yeti fur | `twilightforest:alpha_yeti_fur` | none (bare item) | n/a, armor only | none | full 4-piece (`TFArmorMaterials.YETI`, the roster's highest toughness at 3.0) |
| Carminite | `twilightforest:carminite` | `c:gems/carminite` | n/a, no gear at all | none | none |

Ironwood, steeleaf, knightmetal and fiery are exactly the four the issue expected. Corrections to the issue's own list: **raven feathers are not a tool-worthy material** -- `RAVEN_FEATHER` is a plain item folded into vanilla's `c:feathers` tag (`ItemTagGenerator` line 187), used as a crafting reagent, with no armor or tool use anywhere in the source. **Armor shards are not a fifth material either** -- `armor_shard` and `armor_shard_cluster` are knightmetal's own raw-material intermediate (`RAW_MATERIALS_KNIGHTMETAL` tags `armor_shard_cluster`, not a separate ore), not a distinct gear tier.

**Tier assignment.** `BlockTagGenerator` lines 814-817 add each material's own `incorrect_for_<material>_tool` tag onto a real vanilla tag: ironwood inherits `incorrect_for_iron_tool` (iron-equivalent harvest), fiery inherits `incorrect_for_netherite_tool` (netherite-equivalent -- surprisingly high for a material some players get before their first Naga fight), and both steeleaf and knightmetal inherit `incorrect_for_diamond_tool`. This is a direct source read, not an inference from the raw stat numbers (which use the mod's own unit scale and do not map onto Forgeweave's durability/speed/damage envelope 1:1 -- the same caveat the Just Dire Things batch already recorded).

Naga scale, arctic fur, alpha yeti fur and carminite carry no harvest tag of their own (no tool exists to hang one on). Tier assignment for these four is a judgment call based on the mod's own progression, not a source-read fact: `TFBlocks`' trophy registration order (`NAGA_TROPHY`, `LICH_TROPHY`, `MINOSHROOM_TROPHY`, `HYDRA_TROPHY`, `KNIGHT_PHANTOM_TROPHY`, `UR_GHAST_TROPHY`, `ALPHA_YETI_TROPHY`, `SNOW_QUEEN_TROPHY`) mirrors the mod's well-documented boss order (Naga first, Alpha Yeti and Snow Queen last, in the Glacier/Aurora biome). That ordering is read in source; the wiki's progression page returned a Cloudflare challenge page during this survey and could not be fetched, so no wiki citation backs it. Naga scale (early boss) is assigned iron tier; arctic fur, alpha yeti fur and carminite (Glacier biome and the Ur-Ghast/carminite reactor line, both late-game) are assigned diamond tier.

### Beyond materials

| Feature | What it is | What Forgeweave would do | Seam | Size | Data or Java | Do it? |
| --- | --- | --- | --- | --- | --- | --- |
| Biome progression locks | The Twilight Forest dimension gates its own towers behind killing the previous boss (`TFAdvancements`, structure-based) | Nothing -- this gates the mod's own content, not Forgeweave's. A Forgeweave tool mined from knightmetal works the moment the player has the ingot, same as any other Track A material | none | n/a | n/a | No |
| Mazebreaker pickaxe (`MazebreakerPickItem`) | A `Tiers.DIAMOND` pickaxe with `setNoRepair()` that can break the Labyrinth's unbreakable maze stone | Nothing planned. It is diamond-tier vanilla, not a new Forgeweave material, and "can break an otherwise unbreakable block" is a mod-specific block-interaction hook Forgeweave's own tools have no reason to duplicate | none identified | n/a | n/a | No |
| Giant tools (`GiantPickItem`, `GiantSwordItem`, `TFToolMaterials.GIANT`) | Oversized tools with a bonus reach/knockback profile, tier `incorrect_for_stone_tool` (stone-equivalent despite the huge stats) | No craftable ingredient exists for the `GIANT` tier outside giant-sized loot -- it is a found-item tier, not a smeltery material, so there is nothing to make a Track A preset out of | none | n/a | n/a | No |
| Ore magnet (`OreMagnetItem`) | A wearable/held item that pulls nearby ore drops toward the player | A real Forgeweave analog would be a modifier (e.g. a magnetism trait on a pickaxe), which is exactly D-M8-... territory the modifier library already covers in shape (see `magnetic pull` in the existing modifier roster, `docs/SCOPE.md`'s modifier-behavior table) -- nothing new to build, the seam already exists generically | Existing modifier library (`magnetic pull` already listed as an event-driven Java modifier) | n/a | n/a | Already covered generically |
| Knight Phantom armor's `noClip`-adjacent visuals | Phantom armor set makes the wearer partially translucent | Cosmetic-only, client rendering hook on a specific `PhantomArmorItem` subclass -- not a stat or seam Forgeweave's material system reaches | none | n/a | n/a | No |
| Dark Tower / Fiery lava fortress | Fiery mobs and their loot are Highlands/lava-biome content, not gated behind a boss kill the way the later towers are | Nothing new -- this only affects when a player can reach fiery ingots, not what Forgeweave does with them once they have one | none | n/a | n/a | No |

Nothing in the "beyond materials" pass turned up a station-facing seam worth a new issue: the mod's special tool behaviors (mazebreaker, giant tools, the ore magnet) are either single found-items with no craftable material backing them, or already covered by an existing generic Forgeweave seam (the modifier library's magnetism entry). This matches the low expectations the issue itself set for this half of the survey.

## Ice and Fire

**Which fork ships in ATM10 8.1.** Confirmed on Modrinth: the original `AlexModGuy/ice-and-fire-dragons` project has no release past Minecraft 1.20.1 -- there is no 1.21.1 build to survey. The fork that does ship 1.21.1 NeoForge builds is **IceAndFire Community Edition**, Modrinth slug `iceandfire-ce`, confirmed as a dependency of a separate Modrinth addon (`excalibur-ice-and-fire-ce-tablas-dragon-retextures`) that explicitly targets it. Repository: `IAFEnvoy/IceAndFire-CE`.

**Version and source.** Branch `1.21.1` (the branch built for `minecraft_version=1.21.1`), read at commit `a16787ae2f` (2026-09-15, the branch head at survey time). `gradle.properties`: `mod_version=2.1.2`, `minecraft_version=1.21.1`, `neo_version=21.1.248`. The newest Modrinth release at survey time is `2.1-beta.1` (2026-07-18); the branch head is unreleased dev work past that, the same "branch head vs. newest release" situation the Just Dire Things survey (#1031) already documented for its own mod.

**License.** LGPL-3.0-or-later, confirmed from the repository's own `LICENSE` file (`Copyright (c) 2024 IAFEnvoy`, GNU LGPL v3 text). One license for the whole repository -- no separate asset carve-out was found (no `ASSET_LICENSE`-equivalent file). Not MIT: read-only inspiration, same rule as Powah and Occultism's own LGPL-gated presets.

### Material roster

Read from `com.iafenvoy.iceandfire.registry.IafItems`, `IafArmorMaterials`, `IafTiers` (the mod's `Tier` enum), `IafTrollTypes`/`TrollType`, `CommonItemTags`, and the shipped `data/c/tags/item/*` JSON (which confirms exactly which items actually carry a `c:` tag, rather than assuming one from the item id).

| Material | Item id(s) | `c:` tag | Harvest tier (`IafTiers`) | Tool set | Armor set |
| --- | --- | --- | --- | --- | --- |
| Silver | `iceandfire:silver_ingot`, `raw_silver`, `silver_nugget`, `silver_block` | `c:ingots/silver`, `c:raw_materials/silver`, `c:nuggets/silver`, `c:storage_blocks/silver` | `minecraft:incorrect_for_iron_tool` (`SILVER_TOOL_MATERIAL`: 460 durability, 1.0 damage bonus, 11.0 speed, ench 18) | full sword/shovel/pickaxe/axe/hoe | full 4-piece (`IafArmorMaterials.SILVER`) |
| Dragon bone | `iceandfire:dragonbone` | `c:bones` (flat tag, shared with `witherbone` -- no per-material `c:bones/dragon` subtag exists) | `minecraft:incorrect_for_iron_tool` (`DRAGONBONE_TOOL_MATERIAL`: 1660 durability, 4.0 damage bonus, 10.0 speed, ench 22) | full sword/shovel/pickaxe/axe/hoe | none -- no `DragonBone` `ArmorMaterial` exists |
| Dragonsteel (fire) | `iceandfire:dragonsteel_fire_ingot`, `dragonsteel_fire_block` | none (concrete item id only) | `minecraft:incorrect_for_netherite_tool` | full sword/pickaxe/axe/shovel/hoe (`ActivePostHit*Item`) | full 4-piece (`DragonSteelArmorItem`) |
| Dragonsteel (ice) | `iceandfire:dragonsteel_ice_ingot`, `dragonsteel_ice_block` | none | `minecraft:incorrect_for_netherite_tool` | full tool set | full 4-piece |
| Dragonsteel (lightning) | `iceandfire:dragonsteel_lightning_ingot`, `dragonsteel_lightning_block` | none | `minecraft:incorrect_for_netherite_tool` | full tool set | full 4-piece |
| Death worm chitin (yellow) | `iceandfire:deathworm_chitin_yellow` | none | n/a, armor only | none | full 4-piece (`IafArmorMaterials.DEATHWORM_YELLOW`) |
| Death worm chitin (white) | `iceandfire:deathworm_chitin_white` | none | n/a, armor only | none | full 4-piece |
| Death worm chitin (red) | `iceandfire:deathworm_chitin_red` | none | n/a, armor only | none | full 4-piece |
| Troll leather (mountain) | `iceandfire:troll_leather_mountain` | none | n/a, armor only | none | full 4-piece (`IafArmorMaterials.TROLL_MOUNTAIN`) |
| Troll leather (forest) | `iceandfire:troll_leather_forest` | none | n/a, armor only | none | full 4-piece |
| Troll leather (frost) | `iceandfire:troll_leather_frost` | none | n/a, armor only | none | full 4-piece |

All three death worm armor sets carry identical protection numbers in source (`new int[]{2, 5, 7, 3}`, enchantability 5, toughness 1.5) -- the colors are a reskin, not a power tier, which is why the Track A presets below share one trait and one stat block across the three ids. The same is true of the three troll leather sets (`new int[]{2, 5, 7, 3}`, enchantability 10, toughness 1.0).

Corrections to the issue's own expected list, all read in source:

- **Copper is not a new material.** `IafArmorMaterials.COPPER` and `IafTiers.COPPER_TOOL_MATERIAL` both repair from plain `Items.COPPER_INGOT` -- vanilla's own copper ingot, not an `iceandfire:copper_ingot` of its own. There is no `c:ingots/copper` file under this mod's own tag tree either. Forgeweave already treats copper as a native material; there is nothing here to gate a preset on.
- **Dragon scales are not tool-worthy.** `IafItems` registers twelve `DragonScalesItem`s (`dragonscales_red/green/bronze/gray/blue/white/sapphire/silver/electric/amethyst/copper/black`), but none of them backs an `ArmorMaterial` or a `Tier` -- `IafArmorMaterials.java` (99 lines, read in full) has no scale-based entry at all. They are trophy/crafting items, most plausibly feeding the banner-pattern and dragon-whistle recipes the same file registers nearby. The mod's actual player-facing dragon gear tier is dragonsteel (fire/ice/lightning ingots), not raw scales.
- **Sea serpent scales are not tool-worthy either.** `SeaSerpentScaleItem` (read in full) is a plain `Item` subclass whose only override adds a colored tooltip line -- no armor or tool use.
- **Myrmex chitin is absent from this fork entirely.** `IafEntities.java` (144 lines, read in full) has zero references to "myrmex" anywhere -- no entity, no chitin item, no hive structure. The original AlexModGuy mod has Myrmex content; the Community Edition port at this commit does not. This is a real gap, not a naming mismatch, and it means the issue's "myrmex chitin (desert and jungle)" expectation cannot be filled from this mod at all right now.
- **Hippogryph and amphithere materials exist only as single found-item swords, not tool-worthy materials.** `IafTiers` does define `HIPPOGRYPH_SWORD_TOOL_MATERIAL` and `AMPHITHERE_SWORD_TOOL_MATERIAL`, but each backs exactly one sword (`HippogryphSwordItem`, `AmphithereMacuahuitlItem`), has no pickaxe/axe/shovel counterpart, no armor set, no ingot, and no `c:` tag family -- its "repair material" is a single creature-drop item (`hippogryph_talon`, `amphithere_feather`) consumed by that one recipe. The same shape covers `stymphalian_sword`, `hippocampus_sword`, `troll_weapon`, `dread_sword`, `dread_knight_sword`, `dread_queen` and `ghost_sword`: every one of them is a single legendary/boss weapon with a wood-tier harvest tag (they were never meant to mine), not a material family. None of these get a preset, the same call the Just Dire Things survey made for the abilities it declined to port.
- **"Blooded" dragon bone is not a separate craftable material.** `BLOODED_DRAGONBONE_TOOL_MATERIAL` exists as a `Tier`, but no `iceandfire:blooded_dragonbone` item does -- it backs `DRAGONBONE_SWORD_FIRE/ICE/LIGHTNING`, three upgraded versions of the base dragonbone sword made by applying dragon blood, not a second ingot. Dragon bone's own preset already covers the base tier this upgrades from.

### Beyond materials

| Feature | What it is | What Forgeweave would do | Seam | Size | Data or Java | Do it? |
| --- | --- | --- | --- | --- | --- | --- |
| Dragon forge / dragon blood upgrades | A vanilla-smithing-table-shaped recipe that upgrades a dragonbone sword to its fire/ice/lightning "blooded" variant using dragon blood | No natural analog -- Forgeweave tools take modifiers and levels, not a fixed one-shot smithing upgrade, and the upgrade only ever applies to the mod's own `DragonboneSwordItem` class the same way Just Dire Things' upgrades only apply to `ToggleableTool` (D-M8-22's precedent) | none identified | n/a | n/a | No |
| Dragon loot and dragon dens | Dragons drop scales, blood, hearts and bones as loot from a den structure, gated by dragon age/size | Nothing Forgeweave-side -- this only affects how a player gets dragon bone and dragonsteel ingredients, not what happens once they have them | none | n/a | n/a | No |
| Myrmex hives (desert/jungle) | Absent from this fork (see roster note above) | Nothing to build against; revisit if a future Community Edition release ports Myrmex back in | none, mod does not have the content | n/a | n/a | No, mod gap |
| Dragon armor stands / mounted dragon armor (`DragonArmorItem`, `buildDragonArmor`) | Decorative armor a *dragon* wears while ridden, built from iron/copper/silver/gold/diamond/netherite/dragonsteel -- not player-wearable | Nothing -- this is the IAF equivalent of horse armor, keyed to `DragonArmorPart`/`DragonArmorMaterial`, not `ArmorItem.Type`, so it cannot equip a player at all and Forgeweave's material system has no seam that reaches mount equipment | none | n/a | n/a | No |
| Sea serpent / hippocampus / hippogryph taming and riding | Standard IAF creature-taming loop (tempt/breed/heal item tags) | Nothing -- no tool, armor, or material gap; a Forgeweave-crafted item never enters this loop | none | n/a | n/a | No |

## Roster summary and what ships

**Twilight Forest, 8 presets:** ironwood, steeleaf, knightmetal, fiery (all four melt, full tool + armor), naga scale, arctic fur, alpha yeti fur, carminite (all four Part Builder only -- no melting/casting rows, the same shape `blazing_crystal.json` already ships).

**Ice and Fire, 10 new presets plus 1 dedupe:** dragon bone, dragonsteel fire, dragonsteel ice, dragonsteel lightning (all four melt, full tool + armor except dragon bone which has no armor material of its own), death worm chitin yellow/white/red and troll leather mountain/forest/frost (six Part Builder only, identical stats across each trio of color/biome variants since the source mod gives them identical stats too -- each variant still gets its own trait id over the same behavior body, since issue #876's dedupe policy forbids one trait id claimed by more than one material). Silver widens Forgeweave's existing `silver` material's `neoforge:conditions` with a fourth OR branch (`iceandfire:silver_ingot`), the same dedupe shape #1031 already used for Eternal Ores -- no new preset, no new material id.

Stats for every melting material sit inside the existing Track A envelope at their assigned harvest tier (iron: ~240-260/6.2-6.4/3.5-3.7, `ench` 12-14; diamond: ~480-560/6.5-7.5/5.0-5.8, `ench` 15-19; netherite: ~900-940/7.4-7.8/6.5-6.9, `ench` 14-18), read off the nearest-tier compat metals already shipped (ferricore/celestigem/blazegold/eclipsealloy), not off either mod's own raw numbers, which use a different unit scale entirely -- the same caveat the Just Dire Things batch recorded and this survey repeats rather than re-derives.

Every trait below reuses an existing `TraitBehaviors` class rather than inventing new Java, chosen to echo the material's identity in its own mod:

| Material | Trait id | Behavior | Why |
| --- | --- | --- | --- |
| Ironwood | `ironwood_footing` | `movement_bonus` (step height) | Ironwood is TF's sturdy, reliable early-mid tier -- the same "ease of movement" idea `ferricore_footing` already uses for a different mod's entry tier |
| Steeleaf | `steeleaf_precision` | `crit_multiplier_bonus` | Upstream 1.20's own steeleaf preset (`MaterialTraitsDataProvider`, MIT, read for sizing only) pairs it with `experienced` and a looting bonus on ammo; Forgeweave's library has no loot-multiplier behavior today (see the recommendation below), so its fastest-in-family mining speed (8.0 in source) becomes a precision-strike bonus instead |
| Knightmetal | `knightmetal_breach` | `bonus_damage_vs` with `condition: armored` | Matches the issue's own "armour piercing" expectation directly -- `HitCondition.ARMORED`'s own javadoc already calls this exact gate "the vein hammer's crushing blow rider" |
| Fiery | `fiery_ember` | `damage_type_immunity` vs `minecraft:is_fire` | Every fiery item in source is built with `.fireResistant()` -- a direct, verified match, not an analogy |
| Naga scale | `naga_ward` | `stacking_resistance` | A coiling guardian's thick scale armor hardens as the blows keep coming. **Corrected by #1091**: this row first picked `damage_floor` and read it as "never take less than a floor amount", which is the behaviour backwards -- it is the library's drawback, and it raises a blow rather than blunting it. (The comparison to `piercing_guard` was wrong too: that trait is `effect_on_attacker`.) |
| Arctic fur | `arctic_insulation` | `stacking_resistance` | Layered fur insulation against repeated environmental damage |
| Alpha yeti fur | `alpha_yeti_resilience` | `invulnerability_window` | The roster's tankiest set (toughness 3.0 in source) reads as extended post-hit resilience |
| Carminite | `carminite_flicker` | `evasion` | Carminite's reality-distorting, Ur-Ghast-summoning theme reads as an unstable, flickering defense |
| Dragon bone | `dragonbone_edge` | `crit_multiplier_bonus` | A honed ancient-bone blade's precision, distinct from steeleaf's own precision trait in magnitude only |
| Dragonsteel (fire) | `dragonsteel_fire_ward` | `damage_type_immunity` vs `minecraft:is_fire` | Matches the fire dragon's own element directly |
| Dragonsteel (ice) | `dragonsteel_ice_calm` | `stacking_resistance` | An icy, composed defense against repeated effect stacking |
| Dragonsteel (lightning) | `dragonsteel_lightning_surge` | `bonus_damage_vs` with `condition: full_charge` | A lightning strike lands hardest on a fully charged swing |
| Death worm chitin (yellow/white/red) | `deathworm_venom_yellow`/`_white`/`_red` | `effect_on_hit` (poison) | Death worms are burrowing, poison-themed creatures in source; one trait id per color over the same behavior body, since #876's dedupe policy forbids sharing one id across materials |
| Troll leather (mountain/forest/frost) | `troll_regeneration_mountain`/`_forest`/`_frost` | `self_repair_when` | Classic troll self-healing, one trait id per biome variant the same way |

## Recommendations left for the maintainer

- **A loot-multiplier trait behavior does not exist in `TraitBehaviors` today.** Upstream 1.20's own steeleaf preset pairs it with a looting bonus; this survey substitutes a precision-combat trait instead of inventing new Java for one material. Worth a real `bonus_loot`/`fortune_bonus` behavior if a future batch wants it -- several other mods' materials (this one included) would reach for it.
- **No autosmelt behavior exists either.** Fiery's real upstream trait is `autosmelt`; Forgeweave ships the fire-immunity half of fiery's identity (which is directly source-verified) but not the smelt-on-mine half, the same "not every ability gets a seam invented for it" rule the Just Dire Things survey set.
- **Myrmex is a real content gap in IceAndFire Community Edition**, not a Forgeweave decision. If the fork ports Myrmex content back in a future release, desert and jungle chitin become straightforward Part Builder-only presets in the same shape as death worm chitin.
- **No station or machine integration is recommended for either mod.** Twilight Forest's special tools (mazebreaker, giant tools, the ore magnet) and Ice and Fire's dragon forge are either single found-items with nothing to gate a preset on, or already covered by an existing generic Forgeweave seam. Nothing here rises to a `compat/<mod>/` Java integration the way Mekanism or Draconic Evolution did.
