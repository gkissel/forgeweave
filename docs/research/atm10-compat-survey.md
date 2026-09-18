# All the Mods 10 and All the Mons compat survey

**Survey date:** 2026-09-18.

**Method:** primary sources only. Mod lists come from each pack's own CurseForge "Relations > Dependencies" listing (`pageSize=100`, all pages read), which is the pack's own declared dependency set, not a wiki or community summary. Licenses come from each mod's own GitHub repository (the GitHub REST API's `license` field, or the repository's `LICENSE`/`ASSET_LICENSE` file where the API reports none) or its CurseForge sidebar where no repository is public. Forgeweave's own coverage comes from `docs/SCOPE.md` § Milestone 8 and epic [#967](https://github.com/gkissel/forgeweave/issues/967) and its children, read the same day.

**Packs confirmed for NeoForge 1.21.1:**

- **All the Mods 10 (ATM10)**, CurseForge slug `all-the-mods-10`. Current file read: version 8.1, Minecraft 1.21.1, NeoForge, last updated 2026-08-29. Source repository: [AllTheMods/ATM-10](https://github.com/AllTheMods/ATM-10).
- **All the Mons**, CurseForge slug `all-the-mons`, listed on CurseForge as "All the Mons - ATMons". This is the pack's exact name; there is no separate "All the Mons 10". Current file read: version 1.3.0, Minecraft 1.21.1, NeoForge, last updated 2026-09-06. Source repository: [AllTheMods/All-the-Mons](https://github.com/AllTheMods/All-the-Mons). It shares the large majority of its dependency list with ATM10 and adds the Cobblemon stack on top.

**What could not be verified:** the raw `manifest.json` for either pack file was not downloaded (that needs the CurseForge app or an authenticated API call); the dependency listing used instead is the pack's own primary-source page, but a manifest would additionally confirm exact mod version pins. Per-mod `c:` tag names below are stated only where a primary source (the mod's own repo, wiki, or CurseForge page) confirms them; every other cell says so and is marked accordingly. Nobody's jar was decompiled; roster claims for mods not already flagged "verified from source" rest on the mod's own public description and are marked "from the mod's public description".

## Ranked recommendation

1. **Ship what M8 already has in flight; it is aimed at the right mods.** Every major materials or integration mod either pack ships is already a shipped or `ready-for-agent` M8 child: Mekanism ([#993](https://github.com/gkissel/forgeweave/issues/993), [#994](https://github.com/gkissel/forgeweave/issues/994)), Powah ([#996](https://github.com/gkissel/forgeweave/issues/996), shipped), Create/Immersive Engineering/EnderIO ([#995](https://github.com/gkissel/forgeweave/issues/995)), Occultism ([#997](https://github.com/gkissel/forgeweave/issues/997)), Allthemodium ([#998](https://github.com/gkissel/forgeweave/issues/998)), Mystical Agriculture and Mystical Agradditions ([#999](https://github.com/gkissel/forgeweave/issues/999)), Draconic Evolution and Apotheosis (shipped), Just Dire Things ([#1031](https://github.com/gkissel/forgeweave/issues/1031), [#1032](https://github.com/gkissel/forgeweave/issues/1032)). No new epic is needed for either pack; keep landing the M8 roadmap.
2. **Cheap, data-only, next in line:** four more Track A material presets on the existing pattern (tags-only, gated by `neoforge:conditions`, no code, no derived assets), all present in both packs: Aetherium (Forbidden and Arcanus), Ambrosium/Zanite/Gravitite (The Aether), and Ignitium (L_Ender's Cataclysm). Each is S-sized on the model #1031 already set for Just Dire Things.
3. **Cheap, data-only, ATM10 only:** Knightmetal/Steeleaf/Fiery Ingot (The Twilight Forest) and, lower confidence, Dragon Bone/Dragon Scale (IceAndFire Community Edition). Same pattern, S-sized, but only worth it if the maintainer wants ATM10-specific coverage beyond what All the Mons already shares.
4. **Verify, no code needed:** ATO - All the Ores and Railcraft Reborn's steel need nothing new. Forgeweave's tag-keyed melting (D-M8-6) already picks up any mod's `c:ingots/<id>` item; confirm this at the #975 acceptance playthrough rather than filing a new issue.
5. **Deprioritize, not cancel:** EMI and WTHIT. Neither ships in ATM10 or All the Mons; both packs ship JEI and Jade only, which Forgeweave already covers in full. #971's EMI spike and any future WTHIT overlay work stay correct for other packs, just not exercised by this pair.
6. **Skip: Cobblemon-specific hooks.** No natural tool, armor, or material seam turned up in the ~40 Cobblemon-only mods All the Mons adds (see § Beyond materials). Revisit only if Cobblemon or an add-on ships a wearable accessory that would finally justify D-M8-4's Curios trigger.
7. **Skip: Silent Gear family, Extreme Reactors, XyCraft.** A competing material-based tool mod, reactor fuel that is not tool-worthy, and a low-confidence legacy mod, in that order. Reasons below.
8. **Watch, don't reopen:** Eternal Ores and Elementarium, the two mods [#1031](https://github.com/gkissel/forgeweave/issues/1031) and D-M8-19 already target, are **absent from both surveyed packs**. The existing work still has value for other ATM10-family packs (Lite, To the Sky) where they may appear; this survey just cannot confirm they matter for ATM10 or All the Mons specifically.

## Materials table

For every mod in either pack that adds metals, gems, alloys, or another tool-worthy material. "Covered" reflects `docs/SCOPE.md` § Milestone 8 and the epic #967 child issues as of 2026-09-18.

| Mod | Pack(s) | What it adds | `c:` tags | Forgeweave coverage | Confidence |
| --- | --- | --- | --- | --- | --- |
| Mekanism (+ Tools, Generators, Covers, More Machine) | Both | `atomic_matter_alloy` from the Antiprotonic Nucleosynthesizer; standard `c:ingots/` metals (osmium, tin, lead, uranium etc.) | `c:ingots/*`, `c:ores/*` per its own convention | Covered: Mekanism phase 1 shipped, phase 2 (radiation modifier, Track B ore chains) [#994](https://github.com/gkissel/forgeweave/issues/994) open | Verified from source (SCOPE D-M8-15) |
| Powah! (Rearchitected) | Both | Uraninite, energized steel, blazing/niotic/spirited/nitro crystals | `c:ingots/`, `c:gems/` per its own convention | Covered: shipped as Track A presets plus `surgebound` modifier ([#996](https://github.com/gkissel/forgeweave/issues/996)) | Verified from source (SCOPE D-M8-17) |
| Create (+ Crafts & Additions, Enchantment Industry, Hypertubes, etc.) | Both | Zinc, brass (alloy) | `c:ingots/zinc`, `c:ingots/brass` | Covered: tag-keyed recipe JSON for the basic alloys and Create's own machines ([#995](https://github.com/gkissel/forgeweave/issues/995), D-M8-12/16) | Verified from source (SCOPE) |
| Immersive Engineering | Both | Constantan, electrum (alloys) | `c:ingots/constantan`, `c:ingots/electrum` | Covered: tag-keyed arc furnace and crusher recipes ([#995](https://github.com/gkissel/forgeweave/issues/995), D-M8-16) | Verified from source (SCOPE) |
| Ender IO | Both | Its own alloy set (dark steel, pulsating/vibrant alloys) | `c:ingots/*` per its own convention | Covered: tag-keyed alloy smelter and SAG mill recipes ([#995](https://github.com/gkissel/forgeweave/issues/995), D-M8-16) | Verified from source (SCOPE) |
| Occultism (+ KubeJS addon) | Both | Iesnium, silver, spirit-attuned gem | `c:ingots/iesnium`, `c:gems/spirit_attuned` (from SCOPE) | Covered: Track A presets plus crushing/miner recipes and a ritual upgrade type ([#997](https://github.com/gkissel/forgeweave/issues/997), D-M8-18) | Verified from source (SCOPE) |
| Allthemodium | Both | Allthemodium, vibranium, unobtainium, plus three alloys | tags per SCOPE's tier-equivalence rule | Covered: Track A presets with two-way tier equivalence to hardcinder/warspar/resonite ([#998](https://github.com/gkissel/forgeweave/issues/998), D-M8-19) | Verified from source (SCOPE) |
| Mystical Agriculture + Mystical Agradditions | Both | Prosperity, soulium, inferium through awakened supremium, insanium | `c:ingots/`, `c:gems/` per its own convention | Covered: crops plus Track A presets plus `ITinkerable` compat ([#999](https://github.com/gkissel/forgeweave/issues/999), D-M8-20) | Verified from source (SCOPE) |
| Draconic Evolution | Both | Draconium, wyvern, awakened, chaotic cores | fusion-produced, own item ids | Covered and shipped: fusion metals, module hosting, tool-upgrade ladder ([#915](https://github.com/gkissel/forgeweave/issues/915) and successors) | Verified from source (SCOPE) |
| Apotheosis (+ Apothic Attributes, Apothic Enchanting, Apothic Spawners) | Both | Gems (socketable), not a metal roster | n/a, gem items | Covered and shipped: `socketed` modifier, loot affixes, enchanting interplay ([#969](https://github.com/gkissel/forgeweave/issues/969), [#970](https://github.com/gkissel/forgeweave/issues/970)) | Verified from source (SCOPE) |
| Just Dire Things | Both | Ferricore, blazegold, celestigem, eclipse alloy (its own four tool tiers) | unresolved, `c:` names not yet confirmed | In flight: material presets in [#1031](https://github.com/gkissel/forgeweave/issues/1031), upgrade-item integration in [#1032](https://github.com/gkissel/forgeweave/issues/1032), both `ready-for-agent` | From the mod's own issue text, not yet independently checked against the 1.21.1 jar |
| ATO - All the Ores | Both | Unifies common ATM-pack ores: copper, iron, bronze, invar, platinum and similar | `c:ingots/`, `c:ores/` (it is an ore-unification mod by design) | No new work needed: Forgeweave's tag-keyed melting already picks up any mod's item under the shared `c:` tag (D-M8-6) | From the mod's public description |
| Railcraft Reborn | Both | Steel, among other Railcraft-native items | `c:ingots/steel` | No new work needed: Forgeweave already ships its own `steel` material, so this is a tag-compat check, not new content | Verified from source (Forgeweave's own material list, SCOPE D-M8-6) |
| Forbidden and Arcanus | Both | Aetherium (boss-tier ingot) | `c:ingots/aetherium` expected, not independently confirmed | Not covered. Recommended new Track A preset (see below) | From the mod's public description; license is "All Rights Reserved" (GitHub `stal111/Forbidden-Arcanus` reports no license), so tags-only, nothing derived, same treatment as Allthemodium |
| The Aether | Both | Ambrosium (shard/ore), Zanite, Gravitite | `c:ingots/`, `c:ores/` not independently confirmed for 1.21.1 | Not covered. Recommended new Track A preset (see below) | Roster from the Aether Wiki's own Tools/Ores pages, not the 1.21.1 jar itself. Repository `The-Aether-Team/The-Aether` reports LGPL-3.0 on its `1.21.1-develop` branch: inspiration-only, tags/API-only per ADR-0003, matching Powah's treatment |
| L_Ender's Cataclysm | Both | Ignitium (boss-drop ingot, Ignis) | not independently confirmed | Not covered. Recommended new Track A preset (see below) | From the mod's public description (Cataclysm wiki). License is CC-BY-NC-ND-4.0 (Modrinth project page): inspiration-only, tags-only, no code or asset copy, stricter than GPL |
| The Twilight Forest | ATM10 only | Knightmetal, Steeleaf, Fiery Ingot | not independently confirmed | Not covered. Recommended new Track A preset (see below), lower priority since absent from All the Mons | Roster is long-standing public knowledge (FTB wiki); GitHub `TeamTwilight/twilightforest` reports license "Other"/`NOASSERTION` with separate `LICENSE` and `ASSET_LICENSE` files: inspiration-only |
| IceAndFire Community Edition | ATM10 only | Dragon bone, dragon scale (used in the mod's own weapon/armor sets) | not independently confirmed | Not covered. Possible Track A preset, lowest confidence of the group | From general knowledge of the original IceAndFire mod; the "Community Edition" 1.21.1 fork's exact item roster was not independently checked |
| Iron's Gems 'n Jewelry | ATM10 only | Seven gemstones for its own jewelcrafting system | not independently confirmed | Not covered, and not recommended now: its output is rings/necklaces (Curios-slot items), not tool or armor parts, so it does not fit Forgeweave's material pipeline today | From the mod's public description |
| Extreme Reactors | Both | Yellorium, blutonium, ludicrite | reactor-fuel items, not conventional `c:ingots/` | Deliberately not recommended: these are reactor fuel and turbine coil materials with no ore-to-tool-tier shape, not tool-worthy in Forgeweave's sense | From the mod's public description |
| Silent Gear, Silent's Gems, Silent Gear Metalworks | Both | Its own large material roster, built from other mods' metals via optional compat packs (e.g. `SGear-Basic-Metals`, MIT/Unlicense) | n/a, competing material system | Deliberately not recommended: Silent Gear is itself a material-based tool-and-armor mod occupying Forgeweave's own niche; integrating it risks shipping two parallel progression systems in the same pack rather than one | Verified from source: `SilentChaos512/Silent-Gear` is MIT |
| XyCraft, XyCraft: Machines/Override/World | ATM10 only | Legacy ore/machine family (originally Arcane Crystal and similar) | not independently confirmed | Not recommended: low confidence on its current 1.21.1 material roster, and low player-facing value given the mod's age and niche adoption | Not independently verified; flagged low-confidence rather than guessed at |
| Eternal Ores | **Absent from both packs** | n/a here | n/a | [#1031](https://github.com/gkissel/forgeweave/issues/1031) already targets it; that work is not wasted, but this survey found no copy of the mod in either pack's dependency list | Verified absent from both CurseForge dependency listings read 2026-09-18 |
| Elementarium | **Absent from both packs** | n/a here | n/a | D-M8-19 already targets it; same note as Eternal Ores | Verified absent from both CurseForge dependency listings read 2026-09-18 |

## Beyond materials

Candidates where something beyond a material preset is worth having, evaluated against the integration seams Forgeweave already has: Track A/B material presets, the modifier and trait library, `compat/<mod>/` Java seams with a source-isolation test (the Draconic and Mekanism pattern), the JEI plugin, the Jade/WTHIT overlay, the KubeJS trait binding, the energized tank's Forge Energy seam, and the per-integration config toggle family (D-M8-5).

| Mod | What it offers | What Forgeweave would do | Seam | Size | Data or Java | Risks | License |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Apotheosis, Apothic Attributes, Apothic Enchanting | Sockets, affixes, an enchanting-table replacement | Nothing new: D-M8-1/D-M8-2 already integrated sockets, affixes and `allowVanillaEnchanting`. Apothic Attributes/Enchanting are the same author's companion mods riding on the same Apotheosis APIs Forgeweave already reads | Existing `socketed` modifier and `ApotheosisAffixes` seam | n/a, already shipped | n/a | Low: confirm at the #975 checklist that the two companion mods don't change Apotheosis' own affix or enchant behavior in a way the existing seam misreads | Not independently re-checked this survey; SCOPE already notes licenses were unchecked at planning, so derivation stays off regardless |
| EMI | Alternate recipe viewer to JEI | Nothing new needed for these two packs specifically: neither ships EMI, both ship JEI, which Forgeweave's plugin already covers in full | JEI plugin (already shipped) | n/a | n/a | None for this pair of packs; #971's spike result (bridge covers everything) still stands for packs that do add EMI | Not re-checked; SCOPE flags EMI's license as unchecked at planning |
| Jade / WTHIT | Look-at overlays | Jade already covered (casting cooling, smeltery contents, mining level per D-M8-9). WTHIT does not ship in either pack, so its overlay work is not exercised here | Existing Jade/WTHIT plugin classes | n/a | n/a | None for this pair; keep the WTHIT code for packs that do ship it | Not re-checked |
| Curios API / Accessories | Extra equipment slots | Nothing yet: D-M8-4 defers Curios until Forgeweave has a back or charm item. Worth noting All the Mons ships **both** Curios and Accessories (two competing accessory APIs, likely because different Cobblemon add-ons pick different ones), while ATM10 ships only Curios | Future: a Forgeweave charm/back item, not yet designed | S once triggered | Data + small Java | Committing to one API (or both, via the `accessories-compat-layer` project) before a real item exists is exactly the "dependency bought with nothing to spend it on" D-M8-4 warns against | Curios is LGPL-3.0 (known from SCOPE); Accessories (`wisp-forest/accessories`) is MIT |
| Iron's Gems 'n Jewelry | Curios-slot rings and necklaces built from its own gemstones | Same as above: nothing to build until Forgeweave has a slot-eligible item. Its gemstones are a possible future Track A source for a ring/charm's gem, not for tool parts | Same future seam as Curios/Accessories | n/a | n/a | Same as above | Not independently checked |
| Sophisticated Storage / Sophisticated Backpacks (+ Create integration) | Tag-and-filter-driven storage and backpacks | No gap found: both already move any tagged item, including Forgeweave's own forms and dusts, without Forgeweave-side code | None needed | n/a | n/a | None | Not checked; not relevant enough to need it |
| FTB Quests | Modpack quest lines | Already free: Forgeweave emits ordinary advancements per milestone, and FTB Quests reads any advancement or item check through its own editor. No Forgeweave-side hook is missing | None needed | n/a | n/a | None | Not checked |
| Cobblemon (+ ~40 Cobblemon add-ons: Berry Pouch, Battle Extras/Tower, PokeNav, Poképedia, Raid Dens, Stone Statues, Ultra Wormholes, Utility+, Extra Structures, Legendary Monuments, Mega Showdown, CobbleFurnies, Cobbleloots, Cobbleworkers, Lootrmon, Radical Cobblemon Trainers (+API, +Textures), Radical Gyms & Structures, SimpleTMs, Torchmaster Cobblemon Compat, Create: Cobblemon Balls Overhaul, Create: Apokinetics, Wild Battle API, MoreCobblemonTweaks, Navas ZA Megas, Maxi's JourneyMap Cobblemon Minimap Icons, PKGBadges) | A full Pokémon-battling layer: capture, training, battling, gyms | No natural Forgeweave seam found. Cobblemon's progression (battling, breeding, TMs) does not touch tool tiers, harvesting, or armor in a way that needs a Forgeweave hook; the closest candidates (a Poké Ball recipe using a Forgeweave metal, a harvesting bonus on apricorn trees) are wood/iron-tier interactions vanilla tools already satisfy | None identified | n/a | n/a | Building something here risks scope creep into Cobblemon's own design space for no player-visible gain | Cobblemon is GPL-3.0 (well-known, not independently re-checked this survey); inspiration-only regardless per ADR-0003 |

## What to skip and why

- **Silent Gear, Silent's Gems, Silent Gear Metalworks.** A second material-based tool-and-armor mod in the same pack as Forgeweave is not a compat target, it is a competitor for the same player attention. Nothing about Forgeweave needs to talk to it, and building a bridge between two full tool-progression systems is a design problem with no clear win.
- **Extreme Reactors.** Yellorium, blutonium and ludicrite are reactor fuel and turbine coils, not ore-to-ingot-to-tool materials. There is no tool-worthy shape here.
- **XyCraft family.** Old, low-adoption, and this survey could not independently confirm its current 1.21.1 material roster. Not worth speculative work.
- **Cobblemon-specific hooks.** Covered above: no seam found. Revisit only if a future Cobblemon-adjacent mod adds a wearable accessory, which would also finally justify undeferring Curios (D-M8-4).
- **Iron's Gems 'n Jewelry as a materials source.** Its gems feed jewelry, not tools or armor parts; nothing in Forgeweave's material pipeline consumes a ring.
- **A native EMI plugin and further WTHIT work, for this pair of packs specifically.** Both are absent from ATM10 and All the Mons; the packs' own choices (JEI, Jade) are already fully covered. This does not undo #971's general-purpose spike result, it just says these two packs do not need it exercised.

## Appendix A: All the Mods 10, full dependency list

CurseForge reported 491 dependency entries across 5 pages at `pageSize=100`; 490 distinct titles were transcribed (one page-boundary duplicate was likely double-counted by the source list, not a missing mod). Read 2026-09-18.

| Mod | What it is | Relevant |
| --- | --- | --- |
| Accelerated Decay | Speeds up cave-vine and other decay-aged blocks | No |
| Actually Additions | Classic tech/decoration utility mod | No |
| Ad Astra | Space travel, rockets, planets | No |
| Ad Astra: Giselle Addon | Extra content for Ad Astra | No |
| Additional Enchanted Miner | Automated ore-mining add-on | No |
| Additional Lights | Extra light source blocks | No |
| Advanced Peripherals | ComputerCraft peripheral add-on | No |
| AdvancedAE | Applied Energistics 2 utility add-on | No |
| AE2 Import Export Card | AE2 logistics accessory | No |
| AE2 JEI Integration | AE2 recipe-viewer bridge | No |
| AE2 Network Analyser | AE2 network diagnostics tool | No |
| AE2: Crafting Tree | AE2 crafting-plan visualizer | No |
| AEInfinityBooster | AE2 storage-capacity booster | No |
| AI Improvements: Performance Tuning | Mob AI performance optimization | No |
| Akashic Tome | Universal in-game book reader | No |
| All The Arcanist Gear | Ars Nouveau equipment add-on | No |
| All The Tweaks | ATM's own pack-tuning mod | No |
| All the Wizard Gear | Ars Nouveau equipment add-on | No |
| AllTheCompressed | Block-compression storage chain | No |
| AllTheLeaks (Memory Leak Fix) | Server memory-leak patch set | No |
| Allthemodium | Allthemodium/vibranium/unobtainium tool-tier mod | Yes, covered (#998) |
| Almost Unified | Cross-mod item/tag unifier | No |
| Amendments | Bug-fix patch collection | No |
| Apotheosis | Loot, enchanting, gem affix overhaul | Yes, covered (shipped) |
| Apothic Attributes | Apotheosis attribute companion mod | Yes, checklist-only |
| Apothic Enchanting | Apotheosis enchanting companion mod | Yes, checklist-only |
| Apothic Spawners | Apotheosis spawner companion mod | No |
| AppleSkin | Food/saturation HUD overlay | No |
| Applied Energistics 2 | ME digital storage network | No |
| Applied Energistics 2 Wireless Terminals | AE2 wireless terminal add-on | No |
| Applied Flux | AE2/Flux Networks bridge | No |
| Applied Mekanistics | AE2/Mekanism bridge | No |
| Aquaculture 2 | Fishing overhaul mod | No |
| Architectury API | Cross-loader mod library | No |
| Ars Additions | Ars Nouveau content add-on | No |
| Ars Controle | Ars Nouveau content add-on | No |
| Ars Creo | Ars Nouveau content add-on | No |
| Ars Elemancy | Ars Nouveau content add-on | No |
| Ars Elemental | Ars Nouveau content add-on | No |
| Ars Énergistique | Ars Nouveau/AE2 bridge | No |
| Ars Nouveau | Spell-crafting magic mod | No |
| Ars Ocultas | Ars Nouveau/Occultism bridge | No |
| Ars Polymorphia | Ars Nouveau content add-on | No |
| Ars Technica | Ars Nouveau content add-on | No |
| Ars Unification | Ars Nouveau tag unifier | No |
| Artifacts | Curios-slot trinket items | No |
| Athena | Shared developer library | No |
| Atlas API | Map/world-data library | No |
| ATO - All the Ores | Cross-mod ore unification | Yes, tag compat only |
| AttributeFix | Attribute-system bug fixes | No |
| Auroral | Structure/biome content pack | No |
| Auroras | Aurora sky-visual effect | No |
| Bad Wither No Cookie - Reloaded | Wither-explosion griefing tweak | No |
| Balm | Shared developer library | No |
| Baubley Heart Canisters | Curios-slot extra-health item | No |
| Better Advanced Tooltips | Extended item tooltip info | No |
| Better Advancements | Advancement-screen UI overhaul | No |
| Better Compatibility Checker | Mod-conflict warning tool | No |
| Bibliobiomes Legacy | Bookshelf/library biome content | No |
| Bibliocraft Legacy | Furniture and library blocks | No |
| Bibliowoods Legacy | Bibliocraft wood-variant add-on | No |
| BlockUI | GUI-building developer library | No |
| Blue Flame Burning | Visual fire-color tweak | No |
| Bookshelf | Shared developer library | No |
| Borderless Window | Fullscreen window-mode fix | No |
| Botany Pots | Compact crop-growing pots | No |
| Botany Pots - Mystical Agriculture Compat | Botany Pots/MA bridge | No |
| Botany Trees | Compact tree-growing pots | No |
| Brandon's Core | Draconic Evolution's shared library | No |
| Bridging Mod | Client-side bridging QoL tool | No |
| BSL Shaders | Shader pack | No |
| Building Gadgets | Building-tool gadget set | No |
| Byzantine Styles Pack for Minecolonies | MineColonies building-style pack | No |
| Cable Tiers | Tiered cable/conduit visuals | No |
| Caelus API | Elytra-slot shared library | No |
| Camol | Structure-generation content pack | No |
| Cat Jammies | Decorative cat-cosmetic mod | No |
| CC: Tweaked | ComputerCraft programmable computers | No |
| Charging Gadgets | Building Gadgets charging station | No |
| Chipped | Furniture/decoration block set | No |
| Chisel Reborn | Decorative block-variant mod | No |
| ChoiceTheorem's Overhauled Village | Village structure overhaul | No |
| Chroma Carvings | Decorative carved-block set | No |
| Clean Swing Through Grass | Attack-swing visual tweak | No |
| Cloth Config API | Config-screen shared library | No |
| Cloud Glass | Decorative glass block set | No |
| Clumps | XP-orb merging optimization | No |
| Cobblegen Galore | Cobblestone/basalt generator block set | No |
| Cobweb | Shared developer library | No |
| CodeChicken Lib 1.8.+ | Shared developer library | No |
| Colorful Hearts | Recolored heart HUD icons | No |
| Colorwheel | Color-family shared library | No |
| Colorwheel Patcher | Colorwheel compatibility patcher | No |
| Comforts | Sleeping-bag/hammock items | No |
| Common Capabilities | Shared capability library | No |
| Common Storage Lib | Shared storage-mod library | No |
| Compact Machines | Pocket-dimension machine rooms | No |
| Complementary Shaders - Reimagined | Shader pack | No |
| Complementary Shaders - Unbound | Shader pack | No |
| Connected Glass | Connected-texture glass blocks | No |
| ConnectedTexturesMod | Connected-texture rendering library | No |
| Connectivity | Machine-connection QoL tool | No |
| Construction Sticks | Building-alignment tool | No |
| Controlling | Keybind-conflict warning tool | No |
| Cooking for Blockheads | Kitchen appliance/cooking mod | No |
| Corail Tombstone | Death-chest/tombstone mod | No |
| CorgiLib | Shared developer library | No |
| Cosmetic Armor Reworked | Cosmetic armor overlay slots | No |
| Crafting on a stick | Portable crafting-table item | No |
| Crafting Tweaks | Crafting-grid QoL buttons | No |
| Crash Assistant | Crash-report diagnostics helper | No |
| Crash Utilities | Crash-report diagnostics helper | No |
| Create | Rotational-power building/automation mod | Yes, covered (recipe JSON) |
| Create Crafts & Additions | Create content add-on | No |
| Create: Aquatic Ambitions | Create underwater-content add-on | No |
| Create: Bells & Whistles | Create decoration add-on | No |
| Create: Dragons Plus | Create/Draconic Evolution bridge | No |
| Create: Enchantment Industry | Create enchanting-automation add-on | No |
| Create: Hypertubes | Create item-transport add-on | No |
| Creeper Overhaul | Creeper visual-variant mod | No |
| CreeperHost Presents Steve's Carts | Automated minecart farming carts | No |
| Cristel Lib | Shared developer library | No |
| Cryonic Config | Config-file shared library | No |
| Crystalix | Crystal-growing decoration mod | No |
| Cucumber Library | Shared developer library (BlakeBr0) | No |
| Cupboard | Shared developer library | No |
| Curios API | Extra equipment-slot API | Yes, D-M8-4 watch item |
| Cyclops Core | Shared developer library (Integrated family) | No |
| Dark Mode Everywhere | UI dark-mode reskin | No |
| Deeper and Darker | Deep Dark biome/content expansion | No |
| Deimos Lib | Shared developer library | No |
| DimStorage | Cross-dimension storage blocks | No |
| Dis-Enchanting Table | Enchantment-removal table | No |
| Domum Ornamentum | MineColonies decorative-block library | No |
| Draconic Evolution | End-game tech and energy mod | Yes, covered (shipped) |
| Drippy Loading Screen | Custom loading-screen art | No |
| Dungeon Crawl | Procedural dungeon structures | No |
| Dyenamics | Extended dye-color palette | No |
| Dyenamics and Friends | Dyenamics cross-mod compat | No |
| Dyson Cube Project | End-game energy/automation structure | No |
| Easy Villagers | Villager-breeding QoL tool | No |
| EdivadLib | Shared developer library | No |
| Enchantment Descriptions | Enchantment tooltip descriptions | No |
| Ender IO | Tech/automation mod with its own alloys | Yes, covered (recipe JSON) |
| Ender Storage 1.8.+ | Ender-chest-style networked storage | No |
| EnderDrives | AE2-adjacent portable storage drives | No |
| Enderman Overhaul | Enderman visual-variant mod | No |
| Energy Meter | Forge Energy display block | No |
| Entangled | Linked inventory/tank blocks | No |
| Eternal Starlight | Starlight-themed dimension/content mod | No |
| Euphoria Patches | Shader-compatibility patch set | No |
| Everything is Copper | Copper-variant block set | No |
| EvilCraft | Blood-magic-adjacent evil-themed mod | No |
| Expanded AE | AE2 content add-on | No |
| ExperienceLib | Shared developer library | No |
| Explorer's Compass | Structure-locating compass item | No |
| Explorify – Dungeons & Structures | Dungeon/structure generation pack | No |
| Extended Industrialization | Modern Industrialization content add-on | No |
| ExtendedAE | AE2 content add-on | No |
| Extra Disks | AE2 storage-cell add-on | No |
| ExtraStorage | Compact tag-based storage system | No |
| Extreme Reactors | Big-Reactors-style nuclear power mod | Yes, excluded (fuel, not tool material) |
| Extreme sound muffler | Redstone-machine sound dampener | No |
| Factory Blocks | Industrial decoration block set | No |
| FancyMenu | Custom main-menu layout tool | No |
| Farmer's Delight | Cooking and farming expansion | No |
| Farming for Blockheads | Automated farming plots | No |
| FastFurnace | Furnace-smelting speed tweak | No |
| FastSuite | Client-side performance patch set | No |
| FastWorkbench | Crafting-table performance patch | No |
| FerriteCore | Memory-usage optimization mod | No |
| Fireproof Boats | Lava-proof boat item | No |
| FlickerFix | Light-flicker rendering fix | No |
| Flux Networks | Wireless Forge Energy network | No |
| Forbidden and Arcanus | Magic-themed boss and gear mod | Yes, new material candidate |
| Formations (Structure Library) | Structure-generation shared library | No |
| Formations Nether | Nether structure-generation pack | No |
| Formations Overworld | Overworld structure-generation pack | No |
| FramedBlocks | Framed decorative block shapes | No |
| Framework | Shared developer library | No |
| FTB Chunks | Chunk-claiming and map protection | No |
| FTB Essentials | FTB's core QoL command set | No |
| FTB Filter System | Item-filter shared component | No |
| FTB JEI Extras | FTB/JEI integration extras | No |
| FTB Library | FTB's shared developer library | No |
| FTB Quests | Quest-line pack progression | No |
| FTB Ranks | Permission-rank management | No |
| FTB Teams | Player-team management | No |
| FTB Ultimine | Vein-mining QoL tool | No |
| FTB XMod Compat | FTB cross-mod compatibility layer | No |
| Fuel Goes Here | Furnace fuel-slot QoL tool | No |
| Functional Storage | Compact drawer-style storage | No |
| Fusion (Connected Textures) | Connected-texture rendering add-on | No |
| Fzzy Config | Config-screen shared library | No |
| Gateways to Eternity | Boss-summoning arena structures | No |
| GeckoLib | Animated-model shared library | No |
| Generator Galore | Extra power-generator block set | No |
| Get It Together, Drops! | Item-drop merging QoL tool | No |
| Glassential Renewed | Decorative glass block set | No |
| Glodium | Glowstone/redstone-themed block set | No |
| Gravitational Modulating Additional Unit | Draconic Evolution flight module item | No |
| GuideME | In-game guidebook framework | No |
| Handcrafted | Furniture and decoration block set | No |
| Hardened Armadillos | Armadillo mob-variant tweak | No |
| Hey Berry! SHUT UP | Sweet Berry Bush sound tweak | No |
| Hostile Neural Networks | AI-generated hostile mob farming | No |
| I'm Fast | Player sprint/speed tweak | No |
| IceAndFire Community Edition | Dragons, hydras, myrmex mod | Yes, new material candidate (low confidence) |
| Iceberg | Shared developer library | No |
| Illager Warship | Illager-raid airship structure | No |
| ImmediatelyFast | Client-side rendering performance mod | No |
| Immersive Energistics | Immersive Engineering/AE2 bridge | No |
| Immersive Engineering | Realistic-style tech and power mod | Yes, covered (recipe JSON) |
| In Control! | Mob-spawn rule scripting mod | No |
| Industrial Foregoing | Automation and resource-generation mod | No |
| Industrial Foregoing Souls | Industrial Foregoing content add-on | No |
| Industrialization Overdrive | Modern Industrialization content add-on | No |
| Integrated Crafting | Integrated Dynamics crafting add-on | No |
| Integrated Dynamics | Logic-network automation mod | No |
| Integrated Scripting | Integrated Dynamics scripting add-on | No |
| Integrated Terminals | Integrated Dynamics terminal add-on | No |
| Integrated Tunnels | Integrated Dynamics transport add-on | No |
| Interdimensional Wireless Transmitter | Cross-dimension item/energy transmitter | No |
| Invasive Optimizations | Server-side performance optimization | No |
| Inventory Tweaks - ReFoxed | Inventory sorting QoL tool | No |
| Iris & Oculus Search | Shader-loader search/compat tool | No |
| Iris Shaders | Shader-loader mod | No |
| Iron Furnaces | Tiered upgradeable furnace blocks | No |
| Iron Jetpacks | Powered flight jetpack items | No |
| Iron's Gems 'n Jewelry | Modular jewelcrafting mod | Yes, noted, not a tool-material source |
| Iron's Lib | Iron's-family shared developer library | No |
| Iron's Spells 'n Spellbooks | Spell-casting magic mod | No |
| Item Collectors | Automatic item-pickup block | No |
| Jade | Look-at block/entity overlay | Yes, covered (shipped) |
| Jonn's Trophies | Boss-trophy decoration items | No |
| JourneyMap | Real-time minimap and world map | No |
| Jumpy Boats | Boat-jump physics tweak | No |
| Jupiter | Structure/dimension content pack | No |
| Just Dire Things | Direwolf20's boss/tool/upgrade content mod | Yes, in flight (#1031, #1032) |
| Just Enough Archaeology | Archaeology recipe-viewer plugin | No |
| Just Enough Breeding (JEBr) | Animal-breeding recipe-viewer plugin | No |
| Just Enough Items (JEI) | Recipe-viewer mod | Yes, covered (shipped plugin) |
| Just Enough Mekanism Multiblocks | Mekanism multiblock recipe-viewer plugin | No |
| Just Enough Professions (JEP) | Villager-profession recipe-viewer plugin | No |
| Just Zoom | Camera-zoom keybind tool | No |
| KeyBind Bundles | Multi-key keybind macro tool | No |
| KeybindsPurger | Duplicate-keybind cleanup tool | No |
| Konkrete | Shared developer library | No |
| Kotlin for Forge | Kotlin-language runtime library | No |
| KubeJS | In-pack scripting/data mod | Yes, covered (trait binding shipped) |
| KubeJS Tweaks | KubeJS QoL add-on | No |
| L_Ender's Cataclysm | Boss-raid content mod | Yes, new material candidate |
| Laser Bridges & Doors | Laser-gate bridge/door blocks | No |
| LaserIO | Laser-based item/energy transport | No |
| Lionfish API | Shared developer library | No |
| Lithostitched | Worldgen-stitching shared library | No |
| Little Big Redstone | Redstone-related decoration/utility blocks | No |
| Load My F***ing Tags | Tag-loading performance patch | No |
| Logistics Network | Item-logistics automation network | No |
| Lootr | Per-player lootable chests | No |
| Luminax | Light-source decoration block set | No |
| Macaw's Bridges | Decorative bridge block set | No |
| Macaw's Doors | Decorative door block set | No |
| Macaw's Fences and Walls | Decorative fence block set | No |
| Macaw's Furniture | Decorative furniture block set | No |
| Macaw's Holidays | Seasonal decoration block set | No |
| Macaw's Lights and Lamps | Decorative lamp block set | No |
| Macaw's Paths and Pavings | Decorative path block set | No |
| Macaw's Roofs | Decorative roof block set | No |
| Macaw's Stairs | Decorative stair block set | No |
| Macaw's Trapdoors | Decorative trapdoor block set | No |
| Macaw's Windows | Decorative window block set | No |
| Mahou Tsukai | Magical-girl-themed magic mod | No |
| MakeUp - Ultra Fast | Shaders | Lightweight shader pack |
| Mama's Herbs and Harvest | Cooking/farming ingredient mod | No |
| Mama's Merrymaking | Christmas-themed decoration mod | No |
| McJtyLib | Shared developer library (McJty mods) | No |
| ME Requester | AE2 crafting-request terminal | No |
| Measurements | Distance/coordinate measuring tool | No |
| MEGA Cells | Oversized AE2 storage cells | No |
| Mekanism | Multiblock tech and energy mod | Yes, covered (in flight) |
| Mekanism Covers | Mekanism pipe/cable cover blocks | No |
| Mekanism Generators | Mekanism power-generation add-on | No |
| Mekanism Tools | Mekanism weapon/armor add-on | No |
| Mekanism:More Machine | Mekanism extra-machine add-on | No |
| Mekanistic Routers | Mekanism/Modular Routers bridge | No |
| Melody | Music-disc/jukebox content mod | No |
| Memory Settings | JVM memory-allocation launcher tool | No |
| MES - Moog's End Structures | End-dimension structure pack | No |
| MineColonies | Village-building colony-simulation mod | No |
| Mining Gadgets | Configurable mining-laser tool | No |
| MmmMmmMmmMmm (Target Dummy) | Combat-testing target dummy | No |
| MNS - Moog's Nether Structures | Nether structure pack | No |
| Mo' Structures | Overworld structure pack | No |
| Mob Grinding Utils | Mob-farm automation blocks | No |
| Model Gap Fix | Third-party model rendering fix | No |
| Modern Dynamics | Item/fluid/energy transport pipes | No |
| Modern Industrialization | Tech and machine progression mod | No |
| ModernFix | General performance/bugfix mod | No |
| Modonomicon | In-game guidebook framework | No |
| Modular Bees | Configurable beekeeping mod | No |
| Modular Force Field Systems (MFFS) | Force-field generator machines | No |
| Modular Routers | Configurable item-routing modules | No |
| MonoLib | Shared developer library | No |
| Moog's Structure Lib | Moog's structure-pack shared library | No |
| Moonlight Lib | Shared developer library | No |
| More Dragon Eggs | Extra dragon-egg variant items | No |
| More Industrial Foregoing Addons (MIFA) | Industrial Foregoing content add-on | No |
| More Overlays Updated | HUD overlay information mod | No |
| More Red | Redstone-related utility block set | No |
| More Red x CC:Tweaked Compat | More Red/CC:Tweaked bridge | No |
| Mouse Tweaks | Mouse-based inventory QoL tool | No |
| MrCrayfish's Furniture Mod: Refurbished | Furniture and decoration block set | No |
| MSS - Moog's Soaring Structures | Sky structure pack | No |
| Multi-Piston | Multi-block piston mover | No |
| MVS - Moog's Voyager Structures | Voyage-themed structure pack | No |
| Mystical Agradditions | Mystical Agriculture end-game content add-on | Yes, covered (#999) |
| Mystical Agriculture | Crop-based ore/material farming mod | Yes, covered (#999) |
| Mystical Customization | Mystical Agriculture config helper | No |
| Nature's Aura | Nature-magic ambient mod | No |
| Nature's Compass | Biome-locating compass item | No |
| Neo Vitae | Blood-magic-style life-energy mod | No |
| NeoAuth | Server authentication helper | No |
| Nether Trials & Chambers | Nether trial-chamber structure pack | No |
| NetherPortalFix | Nether-portal linking bug fix | No |
| No Chat Reports | Chat-report telemetry blocker | No |
| No Villager Death Messages | Villager death-message suppressor | No |
| Not Enough Animations | Extra player animation mod | No |
| Not Enough Glyphs | Extra font-glyph rendering mod | No |
| Nullscape | End-dimension terrain overhaul | No |
| Observable | Shared developer library | No |
| Occultism | Ritual/spirit-summoning magic mod | Yes, covered (#997) |
| Occultism KubeJS | Occultism/KubeJS scripting bridge | No |
| Oh The Biomes We've Gone | Overworld biome-generation pack | No |
| Oh The Trees You'll Grow | Extra tree-variant pack | No |
| Omega Config | Config-file shared library | No |
| Open Loader | Mod-loading performance patch | No |
| OpenBlocks Elevator | Vertical elevator block | No |
| Oracle Index | JEI-adjacent recipe-lookup tool | No |
| Oritech | Tech and machine progression mod | No, low confidence on material roster |
| Overloaded Armor Bar | Armor-durability HUD overlay | No |
| oωo (owo-lib) | Shared developer library | No |
| Pam's HarvestCraft 2 - Crops | Extended farming crop set | No |
| Pam's HarvestCraft 2 - Food Core | Extended cooking/food shared library | No |
| Pam's HarvestCraft 2 - Food Extended | Extended cooking/food recipe pack | No |
| Pam's HarvestCraft 2 - Trees | Extended fruit-tree pack | No |
| Patchouli | In-game guidebook framework | No |
| Pipez | Configurable item/fluid/energy pipe | No |
| Placebo | Shared developer library | No |
| playerAnimator | Animated player-pose shared library | No |
| PneumaticCraft: Repressurized | Air-pressure-based tech mod | No |
| Pocket Storage | Portable personal storage item | No |
| PolyLib | Shared developer library | No |
| Polymorph | Recipe-conflict resolution tool | No |
| Polymorphic Energistics | Polymorph/AE2 compatibility bridge | No |
| Ponder for KubeJS | Create's Ponder tutorials for KubeJS content | No |
| Potions Master | Potion-brewing QoL overhaul | No |
| Powah! (Rearchitected) | Energy-crystal power-generation mod | Yes, covered (#996) |
| Prickle | Cactus/prickly-plant decoration mod | No |
| Prism | Client-side rendering shared library | No |
| Productive Bees | Configurable beekeeping mod | No |
| Productive Metalworks | Foundry multiblock smelting/casting mod | Yes, beyond-materials candidate (not recommended now) |
| Productive Trees | Configurable fruit-tree mod | No |
| Pylons | Area-effect pylon block set | No |
| Quests Lang Splitter | Quest-language file splitting tool | No |
| Railcraft Reborn | Rail transport and steel-machine mod | Yes, tag compat only |
| Rainbows! | Rainbow weather-visual effect | No |
| Ranged Pumps | Long-range fluid pump block | No |
| Rebind Narrator | Accessibility narrator-key rebinder | No |
| Rechiseled | Chisel-style block-variant system | No |
| Rechiseled: Chipped | Rechiseled/Chipped bridge | No |
| Rechiseled: Create | Rechiseled/Create bridge | No |
| Redstone Pen | Compact redstone-wiring tool | No |
| Refined Storage | ME-style digital storage network | No |
| Refined Storage - Curios Integration | Refined Storage/Curios bridge | No |
| Refined Storage - JEI Integration | Refined Storage/JEI bridge | No |
| Refined Storage - Mekanism Integration | Refined Storage/Mekanism bridge | No |
| Refined Storage - Quartz Arsenal | Refined Storage weapon/tool add-on | No |
| Refined Types | Refined Storage shared library | No |
| Regions Unexplored | Overworld biome-generation pack | No |
| Relics | Curios-slot artifact item set | No |
| Reliquary Reincarnations | Curios-slot artifact item set | No |
| Reliquified Artifacts | Reliquary/Artifacts bridge | No |
| Repeatable Trial Vaults | Repeatable trial-chamber vault mod | No |
| Repurposed Structures | Cross-dimension structure variant pack | No |
| Resourceful Config | Config-screen shared library | No |
| Resourceful Lib | Shared developer library | No |
| Restrictions | Dimension/area access restriction tool | No |
| RFTools Base | RFTools shared developer library | No |
| RFTools Builder | Programmable building-shape mod | No |
| RFTools Power | RFTools energy-generation add-on | No |
| RFTools Storage | RFTools storage add-on | No |
| RFTools Utility | RFTools utility block set | No |
| Rhino | JavaScript engine (KubeJS dependency) | No |
| Roots Classic | Nature-magic ritual mod | No |
| Sawmill | Wood-processing sawmill block | No |
| Scalable Cat's Force | Client-side rendering performance mod | No |
| Searchables | Search-box UI shared library | No |
| Security Craft | Base-security and trap block set | No |
| ShatterLib \| OctoLib | Shared developer library | No |
| Shiny! Mobs | Shiny mob-variant cosmetic mod | No |
| Shrink. | Player shrinking/size-change tool | No |
| Silent Gear | Modular material-based tool/armor mod | Yes, excluded (competing mod) |
| Silent Gear Metalworks | Silent Gear metalworking add-on | No |
| Silent Lib | Silent-family shared developer library | No |
| Silent's Gems | Silent Gear gem-tool add-on | No |
| Simple Backups | Automated world-backup tool | No |
| Simple Magnets | Item-attracting magnet gadget | No |
| Simple Weather | Weather-control command tool | No |
| Simply Light | Placeable light-source item | No |
| SmartBrainLib | Mob-AI shared developer library | No |
| Smithing Template Viewer for JEI/EMI | Smithing template recipe-viewer plugin | No |
| Sodium | Client-side rendering performance mod | No |
| Sodium Extra | Sodium extra-options add-on | No |
| Sophisticated Backpacks | Upgradeable tiered backpack items | No |
| Sophisticated Backpacks Create Integration | Sophisticated Backpacks/Create bridge | No |
| Sophisticated Core | Sophisticated-family shared developer library | No |
| Sophisticated Storage | Upgradeable tiered storage blocks | No |
| Sophisticated Storage Create Integration | Sophisticated Storage/Create bridge | No |
| Sophisticated Storage in Motion | Sophisticated Storage/Create Contraptions bridge | No |
| Soulplied Energistics | AE2 soul-energy add-on | No |
| spark | Server performance-profiling tool | No |
| Spice of Life: Carrot Edition | Food-variety hunger-penalty mod | No |
| StarbuncleMania | Ars Nouveau starbuncle-pet content | No |
| Step Crafter | Automatic block-stepping/climbing item | No |
| Storage Delight | Farmer's Delight storage add-on | No |
| Structory | Structure-generation content pack | No |
| Structory: Towers | Structory tower-structure add-on | No |
| StructureOverlapless | Structure-overlap prevention tool | No |
| Structurize | MineColonies building shared library | No |
| Stylecolonies | MineColonies building-style pack | No |
| Super Factory Manager (SFM) | Programmable factory-logic block | No |
| SuperMartijn642's Config Lib | Config-screen shared library | No |
| SuperMartijn642's Core Lib | Shared developer library | No |
| Supplementaries | Decoration and utility block set | No |
| Sushi Go Crafting | Food-crafting recipe expansion | No |
| Sussy Sniffers | Sniffer mob cosmetic-variant mod | No |
| Tempad | Draconic Evolution teleport-device item | No |
| TerraBlender | Biome-blending worldgen shared library | No |
| Tesseract API | Cross-dimension linked-block shared library | No |
| The Aether | Sky-dimension exploration mod | Yes, new material candidate |
| The Bumblezone | Bee-dimension exploration mod | No |
| The Twilight Forest | Forest-dimension exploration/boss mod | Yes, new material candidate |
| The Undergarden | Underground mirror-dimension mod | No |
| Theurgy | Alchemy and ritual magic mod | No |
| Theurgy KubeJS | Theurgy/KubeJS scripting bridge | No |
| Time in a Bottle | AFK/idle-time management tool | No |
| Titanium | Shared developer library (Mods by iTitanium) | No |
| Toast Control | HUD toast-notification manager | No |
| Tool Belt | Curios-slot tool-storage belt | No |
| Torchmaster | Light-radius mob-spawn prevention torch | No |
| Towns and Towers | Village/tower structure pack | No |
| TownTalk | Villager dialogue/chat mod | No |
| Transfer Labels | Item-transfer UI labeling tool | No |
| Trash Cans | Item-deletion trash-can block | No |
| TrashSlot | Item-deletion inventory slot | No |
| Tree Tap | Tappable tree resin/sap item | No |
| Underground Villages | Underground village structure variant | No |
| Universal Grid | Cross-mod item-grid bridge | No |
| Uranus | Structure/dimension content pack | No |
| Utilitarian | QoL utility block/item set | No |
| Utility Vest | Curios-slot storage vest item | No |
| Valhelsia Core | Valhelsia shared developer library | No |
| Variants&Ventures | Mob/biome variant content pack | No |
| Villages&Pillages | Village raid-event content pack | No |
| Waystones | Fast-travel waystone blocks | No |
| When Dungeons Arise - Forge! | Dungeon structure-generation pack | No |
| Wireless Chargers | Wireless energy-charging block | No |
| Wither Skeleton Tweaks | Wither skeleton drop-rate tweak | No |
| WITS (What Is This Structure?) | Structure-identifying overlay tool | No |
| XNet | Networked pipe transport system | No |
| Xtones Reworked | Decorative stone-variant block set | No |
| XyCraft, XyCraft: Machines/Override/World | Legacy ore, machine and worldgen mod family | Yes, excluded (low confidence, niche) |
| Yeetus Experimentus | Player-launching cannon item | No |
| YetAnotherConfigLib | Config-screen shared library | No |
| YUNG's API and Better-* family | Structure-generation overhaul family (dungeons, mineshafts, strongholds, etc.) | No |
| ZeroCore 2 | Shared developer library (RFTools/Mekanism-adjacent) | No |

## Appendix B: All the Mons, mods not already in Appendix A

All the Mons shares roughly 360 of its 402 dependency entries with ATM10 (see Appendix A for those). This table lists only what All the Mons adds on top; where ATM10 has a mod All the Mons drops (for example Ad Astra, MineColonies, the Macaw's family, Twilight Forest, XyCraft), that is a straightforward removal, not a new candidate, and is not repeated here.

| Mod | What it is | Relevant |
| --- | --- | --- |
| Accessories | Data-driven accessory/trinket slot API | Yes, D-M8-4 watch item (see Curios row) |
| All The Mons - Coremod | Pack's own internal coremod | No |
| Berry Pouch [Cobblemon] | Cobblemon berry-storage pouch item | No |
| CobbleFurnies | Cobblemon-themed furniture block set | No |
| Cobbleloots: Loot Balls and More! | Cobblemon Poké Ball loot-table add-on | No |
| Cobblemon | Pokémon-battling and capture mod | No, no material/tool seam found |
| Cobblemon Battle Extras | Cobblemon battle-mechanic add-on | No |
| Cobblemon Battle Tower | Cobblemon competitive-battle structure | No |
| Cobblemon: Extra Structures | Cobblemon world-structure add-on | No |
| Cobblemon Fight or Flight Reborn | Cobblemon wild-encounter difficulty mod | No |
| Cobblemon: Legendary Monuments | Cobblemon legendary-encounter structure pack | No |
| Cobblemon: Mega Showdown | Cobblemon Mega Evolution add-on | No |
| Cobblemon PokeNav | Cobblemon Pokédex/navigation HUD tool | No |
| Cobblemon Poképedia: Cobblepedia | Cobblemon in-game Pokédex book | No |
| Cobblemon Raid Dens | Cobblemon raid-battle den structures | No |
| Cobblemon Stone Statues | Cobblemon decorative statue blocks | No |
| Cobblemon Ultra Wormholes | Cobblemon Ultra Space dimension add-on | No |
| Cobblemon Utility+ (IVs, EVs, & More) | Cobblemon stat-management utility mod | No |
| Cobbleworkers | Cobblemon villager-job integration mod | No |
| Complete Cobblemon Collection Mod Version | Expanded Cobblemon species roster | No |
| Create: Apokinetics | Create/Cobblemon-adjacent kinetic content add-on | No |
| Create: Cobblemon Balls Overhaul | Create-automated Poké Ball crafting | No |
| Default Options | Pack default-settings preset mod | No |
| Epitaphs | Player death-location grave marker mod | No |
| Global Packs | Shared resource-pack loading tool | No |
| Lootrmon: Lootr & Cobblemon Gilded Chest Compatibility | Lootr/Cobblemon loot-chest bridge | No |
| Maxi's JourneyMap Cobblemon Minimap Icons | JourneyMap Cobblemon icon add-on | No |
| MoreCobblemonTweaks | Cobblemon config-tweak collection | No |
| Navas ZA Megas | Cobblemon Mega Evolution species add-on | No |
| Pick Up Notifier | Item-pickup toast notification tool | No |
| PKGBadges / CobbleBadges | Cobblemon gym-badge display mod | No |
| Productive Farming | Configurable crop-farming add-on | No |
| Puzzles Lib | Shared developer library | No |
| Radical Cobblemon Trainers (+ API, + Textures) | Cobblemon NPC trainer battle mod | No |
| Radical Gyms & Structures [Cobblemon] | Cobblemon gym-structure generation pack | No |
| SimpleTMs: TMs and TRs for Cobblemon | Cobblemon move-teaching item mod | No |
| Summoning Rituals | Ritual-based mob-summoning mod | No |
| Terralith | Overworld biome-generation pack | No |
| Torchmaster Cobblemon Compat | Torchmaster/Cobblemon compatibility bridge | No |
| Wild Battle API | Cobblemon wild-encounter shared library | No |

## Sources

Primary sources, all accessed 2026-09-18 unless noted:

- All the Mods 10, CurseForge project page: https://www.curseforge.com/minecraft/modpacks/all-the-mods-10
- All the Mods 10, dependency listing (pages 1-5, `pageSize=100`): https://www.curseforge.com/minecraft/modpacks/all-the-mods-10/relations/dependencies
- All the Mods 10, source repository: https://github.com/AllTheMods/ATM-10
- All the Mons, CurseForge project page: https://www.curseforge.com/minecraft/modpacks/all-the-mons
- All the Mons, dependency listing (pages 1-6, `pageSize=100`): https://www.curseforge.com/minecraft/modpacks/all-the-mons/relations/dependencies
- All the Mons, source repository: https://github.com/AllTheMods/All-the-Mons
- Forbidden and Arcanus, CurseForge project page: https://www.curseforge.com/minecraft/mc-mods/forbidden-arcanus
- Forbidden and Arcanus, source repository (license check): https://github.com/stal111/Forbidden-Arcanus
- The Aether, source repository (license and 1.21.1 branch check): https://github.com/The-Aether-Team/The-Aether
- The Aether Wiki, Ores and Tools pages: https://aether.fandom.com/wiki/Ores, https://aether.fandom.com/wiki/Tools
- The Twilight Forest, source repository (license check): https://github.com/TeamTwilight/twilightforest
- L_Ender's Cataclysm, Modrinth project page (license): https://modrinth.com/mod/l_enders-cataclysm
- Cataclysm Wiki, Ignitium page: https://www.minecraft-guides.com/wiki/cataclysm/burning-arena/ignitium/
- Silent Gear, source repository (license check): https://github.com/SilentChaos512/Silent-Gear
- Accessories, source repository (license check): https://github.com/wisp-forest/accessories
- Productive Metalworks, CurseForge project page: https://www.curseforge.com/minecraft/mc-mods/productive-metalworks
- Extreme Reactors, CurseForge project page: https://www.curseforge.com/minecraft/mc-mods/extreme-reactors
- Oritech, CurseForge project page: https://www.curseforge.com/minecraft/mc-mods/oritech
- Iron's Gems 'n Jewelry, CurseForge project page: https://www.curseforge.com/minecraft/mc-mods/irons-jewelry
- ATO - All the Ores, CurseForge project page: https://www.curseforge.com/minecraft/mc-mods/ato

Repository sources (read the same day):

- `docs/SCOPE.md` § Milestone 8 (this repository)
- `docs/research/tic2-addon-ecosystem.md` (this repository, style and method reference)
- Epic [#967](https://github.com/gkissel/forgeweave/issues/967) and its child issues [#968](https://github.com/gkissel/forgeweave/issues/968)-[#975](https://github.com/gkissel/forgeweave/issues/975), [#992](https://github.com/gkissel/forgeweave/issues/992)-[#999](https://github.com/gkissel/forgeweave/issues/999), [#1031](https://github.com/gkissel/forgeweave/issues/1031), [#1032](https://github.com/gkissel/forgeweave/issues/1032)
