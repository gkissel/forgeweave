# Forgeweave parity audit — 2026-08-18

## 1. Method

Ten domain audits were run against the pinned read-only upstream clones, then each finding was re-checked by an independent skeptic pass; corrections from that pass are applied here (three findings were overturned or narrowed: guide-book page overflow, book module gating, vanilla story-advancement grants; two were split: InfiTool, ranged crossbow arm pose).

| Source | Branch | Pinned commit | Role |
| --- | --- | --- | --- |
| `~/development/minecraft/references/tinkers-1.12` | `1.12` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | Feature target — all parity claims resolve here |
| `~/development/minecraft/references/tinkers-1.20` | `1.20.1` | `de26560d26c15edf93e6078520202d1c0518394e` | Modern API idioms only; never sets scope |
| `~/development/minecraft/references/spartan-weaponry-1.12.2` | `1.12.2` | `af87ea162cc043f1ea4236e5da5e723c600001ed` | Apache-2.0, blade art for #375 only |

- Forgeweave master at audit time: `53df05c` — *fix: guide book pages paginate instead of drawing off the page* (2026-08-18).
- Decision sources treated as "recorded": `docs/SCOPE.md`, `docs/adr/*`, `CONTEXT.md`, merged PR bodies, and closed GitHub issues on `gkissel/forgeweave`.
- Domains audited: materials, traits, modifiers, tools, smeltery, stations, book/UX, world & gadgets, ranged, config/compat/misc.
- Status values: **have** (parity), **partial** (some of the feature), **deviates** (behaves differently), **missing** (absent), **forgeweave-only** (no 1.12 counterpart), **n/a** (upstream feature has no counterpart concept).
- Severity is player-visible impact, not effort: **blocker** (progression/data-loss), **high**, **medium**, **low**, **none**.
- Cites are abbreviated to `File.java:line` / data path; every row was verified against the clone at the pinned commit.

## 2. Executive summary

**476 features compared across 10 domains.**

| Status | Count |
| --- | --- |
| have | 193 |
| partial | 86 |
| missing | 111 |
| deviates | 47 |
| forgeweave-only | 38 |
| n/a | 1 |

| Severity | Count |
| --- | --- |
| blocker | 2 |
| high | 26 |
| medium | 72 |
| low | 148 |
| none | 228 |

| Domain | Items | have | partial | missing | deviates | FW-only | blocker | high | medium |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Materials | 51 | 31 | 5 | 6 | 3 | 6 | 1 | 1 | 2 |
| Traits | 50 | 29 | 9 | 4 | 5 | 3 | 0 | 1 | 3 |
| Modifiers | 38 | 6 | 15 | 6 | 4 | 7 | 0 | 1 | 9 |
| Tools | 64 | 30 | 8 | 13 | 5 | 8 | 0 | 2 | 9 |
| Smeltery | 59 | 25 | 10 | 13 | 6 | 5 | 0 | 4 | 7 |
| Stations | 45 | 23 | 10 | 5 | 5 | 2 | 1 | 3 | 5 |
| Book / UX | 41 | 10 | 12 | 9 | 7 | 2 | 0 | 3 | 9 |
| World & gadgets | 42 | 3 | 2 | 32 | 3 | 2 | 0 | 5 | 16 |
| Ranged | 49 | 26 | 7 | 12 | 3 | 1 | 0 | 4 | 5 |
| Config / compat | 37 | 10 | 8 | 11 | 6 | 2 | 0 | 2 | 7 |

Headline: the core loop (materials, traits, tools, smeltery, stations, ranged) is at or near parity; the two blockers are a mis-mapped harvest-tier ladder and silently-ignored station slots. The `missing` bulk (111) is concentrated in two unstarted areas — world content/gadgets (32) and the deferred projectile layer (12) — both already scoped as later milestones. 38 Forgeweave-only additions are all recorded decisions.

## 3. Domain findings

### 3.1 Materials

Roster and stat parity are strong: all 27 1.12 tool materials plus string/vine ship as datapack JSON with clone-exact numbers, per-part trait scoping and colors. Two systemic problems dominate: the harvest-level → vanilla-tag ladder is off by one for every material (PR #81 read `HarvestLevels.STONE` as the stone tier; it is the wooden tier), and every metal ships Part Builder `crafting_items` where 1.12 metals are cast-only.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Material data model (head/handle/extra/bow, traits, color) | `Material.java`, `TinkerMaterials.java:391-511` | stat-type JSON idiom | `material/Material.java:34-206` | have | Y (ADR-0002, #403) | none | — |
| Harvest level → tool-tier tag ladder | `HarvestLevels.java:15-19` (STONE=0=wood) | `MaterialStatsDataProvider` wood=WOOD | `material/*.json incorrect_for_*`; pinned by `ToolBehaviorGameTests:100-128` | deviates | N (PR #81 misread) | **blocker** | T1 |
| Craftable vs castable gating | `MaterialIntegration.java:100-108`; `Config` craftCastableMaterials=false | n/a | `material` JSON `cast_only` + `craftCastableMaterials` (default off) | have | Y (#435) | none | — |
| Crafting-item values (Ingot 144 / Shard 72 / Fragment 36 / Nugget 16) | `Material.java:45-51` | n/a | shard-units; `PartBuilderRecipes.java:27-42` | partial | Y (PR #247/#135) | medium | T58 |
| Storage blocks as crafting items (cobalt/ardite/manyullyn/rose gold) | `TinkerMaterials:305-315` | n/a | only ingot listed | partial | N | low | T61 |
| Repair items (any material item, value-scaled) | `TinkersItem.java:300-400`; shard match for all | n/a | single `repair_item`; `ToolAssemblyRecipes:855-896` | partial | N | medium | T30 |
| Per-material render info (multicolor/metal/block textures) | `assets/tconstruct/materials/*.json` | n/a | `ForgeweaveItemColors` flat tint | deviates | Y (ADR-0002, texture-manifest) | low | — |
| Trait scoping beyond HEAD (SHAFT, PROJECTILE) | `TinkerMaterials:264,272` | n/a | `Material.Traits` general/head only | missing | Y (SCOPE M3.5) | low | T17 |
| wood | `:392-395` 35/2.0/2.0, bow 1/1/0 | n/a | `wood.json` clone-exact | have | Y | none | — |
| stone | `:397-400` 120/4.0/3.0 | n/a | `stone.json`; cheapskate folded into `cheap` | have | Y (#94) | none | T62 |
| flint | `:401-404` 150/5.0/2.9 | n/a | `flint.json` clone-exact | have | Y | none | — |
| cactus | `:405-408` 210/4.0/3.4 | n/a | `cactus.json` clone-exact | have | Y | none | — |
| bone | `:409-412` 200/5.09/2.5 | n/a | `bone.json` (bonemeal, SHAFT absent) | have | Y (PR #247) | none | — |
| obsidian | `:413-416` 139/7.07/4.2 | n/a | `obsidian.json` + molten obsidian | have | Y | none | — |
| prismarine | `:417-420` 430/5.5/6.2 | n/a | `prismarine.json` (fragment rounding) | have | Y | none | — |
| endstone | `:421-424` 420/3.23/3.23 | n/a | `endstone.json` (no PROJECTILE) | have | Y | none | — |
| paper | `:425-428` 12/0.51/0.05 | n/a | `paper.json` (value 2× upstream) | have | Y | none | T58 |
| sponge | `:429-432` 1050/3.02/0.0 | n/a | `sponge.json` clone-exact | have | Y | none | — |
| firewood | `:467-470` 550/6.0/5.5 | n/a | `firewood.json` clone-exact | have | Y (SCOPE M3.2) | none | — |
| knightslime | `:442-445` 850/5.8/5.1 | n/a | `knightslime.json` clone-exact | have | Y (#232) | none | — |
| slime (green) | `:434-437` 1000/4.24/1.8 | n/a | `slime.json` clone-exact | have | Y | none | — |
| blueslime | `:438-441` 780/4.03/1.8 | n/a | `blueslime.json` clone-exact | have | Y (#232) | none | — |
| magmaslime | `:446-449` 600/2.1/7.0 | n/a | `magmaslime.json` clone-exact | have | Y | none | — |
| iron | `:472-475` 204/6.0/4.0 | n/a | `iron.json` clone-exact (cast-only since #435) | have | Y (PR #135) | none | — |
| pig iron | `:476-479` 380/6.2/4.5 | n/a | `pig_iron.json` clone-exact | have | Y | none | — |
| netherrack | `:451-454` 270/4.5/3.0 | n/a | `netherrack.json` clone-exact | have | Y | none | — |
| cobalt | `:455-458` 780/12.0/4.1 | n/a | `cobalt.json` (block missing) | have | Y | none | T61 |
| ardite | `:459-462` 990/3.5/3.6 | n/a | `ardite.json` clone-exact | have | Y | none | T61 |
| manyullyn | `:463-466` 820/7.02/8.72 | n/a | `manyullyn.json` clone-exact | have | Y | none | T61 |
| copper | `:481-484` 210/5.3/3.0 | n/a | `copper.json` clone-exact | have | Y | none | — |
| bronze (tag-gated) | `:486-489` 430/6.8/3.5 | n/a | `bronze.json`, `c:ingots/bronze` | have | Y (SCOPE M3.2) | none | — |
| lead (tag-gated) | `:491-494` 434/5.25/3.5 | n/a | `lead.json` | have | Y (PR #244) | none | — |
| silver (tag-gated) | `:496-499` 250/5.0/5.0 | n/a | `silver.json` | have | Y (PR #244) | none | — |
| electrum (tag-gated) | `:501-504` 50/12.0/3.0 | n/a | `electrum.json` | have | Y (PR #244) | none | — |
| steel | `:506-509` 540/7.0/6.0 | n/a | `steel.json` + FW alloy | have | Y (PR #244 / #234) | none | — |
| string (bowstring) | `:548-549` 1.0 | n/a | `string.json` | have | Y (PR #403) | none | — |
| vine (bowstring) | `:550` 1.0 | n/a | `vine.json` | have | Y (PR #403) | none | — |
| slimevine blue/purple bowstrings | `:551-552` | n/a | absent | missing | Y (SCOPE world-content) | low | T57 |
| Arrow-shaft materials (blaze/reed/ice/endrod…) | `:555-561` | n/a | absent | missing | Y (SCOPE M3.5) | low | T17 |
| Fletching materials (feather/leaf/slimeleaf) | `:563-568` | n/a | absent | missing | Y (SCOPE M3.5) | low | T17 |
| `xu` unstable (Extra Utilities compat) | `TinkerMaterials:184` | n/a | absent | missing | N (no 1.21 counterpart mod) | low | — |
| BOW stats on all 27 materials | `:513-546` | n/a | `bow` block on every tool material | have | Y (PR #403) | none | — |
| Fluid / melting / casting integration | `MaterialIntegration:100-108` | n/a | datapack melting/casting/alloy | partial | Y (SCOPE M2/M3.2) | none | — |
| Representative item / hidden flag | `Material.java:118,351` | n/a | JEI uses repair item | partial | N | low | — |
| rose gold | n/a | `MaterialStatsDataProvider:158` | `rose_gold.json` 90/10.0/2.0, quick | forgeweave-only | Y (#103) | none | — |
| netherite | n/a | n/a | `netherite.json` fireproof + reinforced core | forgeweave-only | Y (#103) | none | — |
| amethyst bronze | n/a | `:145` 720/7/1.5 | `amethyst_bronze.json` | forgeweave-only | Y (SCOPE M3.2) | none | — |
| nahuatl | n/a | `:149` 350/4.5/3 | `nahuatl.json`, composite casting only | forgeweave-only | Y (PR #250) | none | — |
| chorus | n/a | `:70` 180/3 | `chorus.json` | forgeweave-only | Y (PR #250) | none | — |
| ancient | n/a | `:231` 745/7/2.5 | `ancient.json` (worldbound dropped) | forgeweave-only | Y (PR #250) | none | — |
| Other 1.20-only materials (slimesteel, hepatizon…) | n/a | `MaterialIds.java` | absent | missing | Y (SCOPE non-goal) | none | — |

### 3.2 Traits

Every 1.12 tool-material trait is ported (44 ids across 34 materials, with head-list-replaces-general scoping). The only absent 1.12 traits are the five arrow-ammo traits (deferred with material arrows) and upstream's own unassigned classes. One correctness bug (sharp's bleed knocks the target back), three recorded-but-drifting adaptations (magnetic, autosmelt, blocking definition), and a handful of cosmetic omissions.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ecological | `TraitEcological:24-31` | n/a | `ForgeweaveTraits:119-132` | have | — | none | — |
| cheap + cheapskate | `TraitCheap`, `TraitCheapskate` (HEAD-only) | n/a | one `cheap` id does both | partial | N | low | T62 |
| crude / crude2 | `TraitCrude:23-36` | n/a | `:168-181` | have | — | none | — |
| fractured | `TraitBonusDamage(1.5)` | n/a | `:188-193` | have | — | none | — |
| magnetic / magnetic2 | `TraitMagnetic:32-41` (30t after use) | n/a | always-on inventory pull `:1500-1517` | deviates | Y (PR #119, justification stale) | medium | T28 |
| momentum | `TraitMomentum:25-41` | n/a | per-tool stacks `:281-306` | have | Y (PR #119) | none | — |
| lightweight | `TraitLightweight:28-51` | n/a | `:316-334` | have | — | none | — |
| stonebound | `TraitStonebound:22-36` | n/a | `:345-359` | have | Y (PR #119) | none | — |
| petramor | `TraitPetramor:23-27` | n/a | `:370-379` pickaxe-tag stand-in | have | Y (PR #119) | none | — |
| insatiable | `TraitInsatiable:20-42` (all durability loss) | n/a | `:389-410`, hits only | partial | Y (PR #119) | low | — |
| coldblooded | `TraitColdblooded:15-20` | n/a | `:419-424` | have | — | none | — |
| established | `TraitEstablished:22-56` (kill + block XP) | n/a | `:471-502` kill + block XP via `BlockDropsEvent` | have | Y | none | — |
| alien | `TraitAlien:26-119` | n/a | `:525-596` AlienProgress | have | Y (PR #241) | none | — |
| shocking | `TraitShocking:30-129` | n/a | `:653-762`, vanilla cue stand-ins | have | Y (#415/#419) | none | — |
| slimey green / blue | `TraitSlimey:38-58` (blue slime entity) | n/a | both spawn vanilla Slime | partial | Y (PR #241) | low | T57 |
| baconlicious | `TraitBaconlicious:20-37` (bacon) | n/a | cooked porkchop | partial | Y (PR #241) | low | — |
| tasty | `TraitTasty:25-67` | n/a | `:876-921` | have | — | none | — |
| duritos | `TraitDuritos:20-33` | n/a | `:949-961` | have | — | none | — |
| jagged | `TraitJagged:22-35` | n/a | `:970-975` | have | — | none | — |
| aquadynamic | `TraitAquadynamic:19-31` | n/a | `:987-999` | have | — | none | — |
| aridiculous | `TraitAridiculous:23-39` | n/a | `:1008-1032` | have | Y (PR #242) | none | — |
| hellish | `TraitHellish:22-27` | n/a | `:1216-1217` | have | — | none | — |
| crumbling | `TraitCrumbling:16-19` | n/a | `:1048-1057` base-speed variant | have | Y (PR #242) | none | — |
| unnatural | `TraitUnnatural:21-30` | n/a | `:1068-1102` tier ladder | have | Y (PR #242) | none | — |
| dense | `TraitDense:18-30` | n/a | `:1111-1122` | have | — | none | — |
| writable / writable2 | `TraitWritable:16-22` | n/a | `:1134-1152` | have | Y (#344/#367) | none | — |
| squeaky | `TraitSqueaky:21-50` (sound + compat refusals) | n/a | silk touch + zero damage only | partial | Y (PR #242) | low | T64 |
| autosmelt | `TraitAutosmelt:41-81` (XP, particles, gate, exclusions) | n/a | `ForgeweaveModifiers#smelt` (XP, particles, gate, exclusions since #458) | have | Y (#458) | none | — |
| prickly | `TraitPrickly:20-37` + HEART_CACTUS | n/a | `GaussianArmorPiercingHit`, no particle | have | — | low | T51 |
| spiky | `TraitSpiky:26-57` (both hands, shield block) | n/a | `ThornsReflectSeam`, main hand only | partial | Y (partial, PR #243) | low | T29 |
| superheat | `TraitSuperheat:23-29` | n/a | `:1226-1227` | have | — | none | — |
| flammable | `TraitFlammable:20-35` | n/a | `:1295-1297` (blocking def caveat) | have | — | low | T29 |
| holy | `TraitHoly:26-39` | n/a | `:1238-1241` undead tag | have | Y (PR #243) | none | — |
| poisonous | `TraitPoisonous:17-21` | n/a | `:1247` | have | — | none | — |
| heavy | `TraitHeavy:25-29` (both hands) | n/a | main hand only `ToolItem:315` | partial | Y (PR #243) | low | — |
| stiff | `TraitStiff:16-18` (shield/battlesign) | n/a | `:1267` on FW blocking def | have | N (blocking def) | low | T29 |
| sharp | `TraitSharp:25-64` (anti-knockback secondary) | n/a | `BleedEffect:69-79` indirect_magic | deviates | Y for armor bypass, N for knockback | **high** | T4 |
| splintering | `TraitSplintering:21-43` | n/a | `StackingHitBonus` | have | — | none | — |
| enderference | `TraitEnderference:27-46` (endermen only) | n/a | marks every target `:1310-1311` | deviates | Y (PR #243) | low | — |
| Blocking definition for onBlock traits | `TraitEvents:57-92` (shield or battlesign, both hands) | n/a | `CombatSeams:313-330` any FW tool use, main hand | deviates | N | medium | T29 |
| lacerating | n/a | 1.20 Lacerating modifier | `:1319` LACERATE_SEAM | forgeweave-only | Y (#159) | none | — |
| vintage | n/a | 1.20 Vintage | `:925-946` | forgeweave-only | Y (#230) | none | — |
| quick / fireproof / reinforced_core | n/a | n/a | `:462-500` | forgeweave-only | Y (#103) | none | — |
| Arrow-ammo traits (splitting, breakable, hovering, freezing, endspeed) | `TinkerTraits:104-108` | n/a | absent | missing | Y (SCOPE M3.5) | low | T17 |
| InfiTool creative showcase tools (Bane of Pigs, InfiDigger) | `ToolCore:429-444` | n/a | absent | missing | N | low | — |
| depthdigger / splinters / ToolGrowth / TraitBonusSpeed | registered, unassigned | n/a | absent | missing | — | none | — |
| Launcher trait branch policy (bow traits on arrows) | ammo-side only (`EntityProjectileBase:193-264`) | `ProjectileHitModifierHook` | traits ride the vanilla arrow | deviates | Y (PR #410) | low | T77 |
| Trait extra-info tooltip lines (`getExtraInfo`, 8 traits) | `TooltipBuilder:120-139` | n/a | `ForgeweaveTraits#extraInfo`, all eight (#457) | have | — | none | — |
| Per-part trait scoping (head replaces general) | `Material.java:307-315` | n/a | `Material.java:161-166`, all 34 JSONs match | have | — | none | — |
| Trait de-duplication / leveled stacking | `AbstractTraitLeveled:59-84` | n/a | `:1428-1440` | have | — | none | — |

### 3.3 Modifiers

20 of upstream's ~25 modifiers are ported with clone constants, per-level slot accounting and derived overlay art; six Forgeweave-only modifiers are recorded. The systemic gaps are the whole `canApplyTogether` incompatibility layer, per-modifier extra-info/leveled names, three unported modifiers (Blasting, Glowing, the AOE expanders) and a handful of missing alternative reagent forms.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Modifier framework (3 slots, per-level charge, free aspects) | `ModifierAspect.java:47-337` | datapack slot types | `ForgeweaveModifiers:1010,1041`; ADR-0004 | have | Y (#344) | none | — |
| Station application flow (two slots, all-or-none, errors) | `ToolBuilder#tryModifyTool` | n/a | `ModifierApplication:93-161` | have | Y (#259/#340) | none | T2 |
| Haste | `ModHaste:26-121` (5×50) | n/a | `:124-159`, `haste.json`, leveled names (#457) | have | — | none | — |
| Sharpness | `ModSharpness:15-53`, quartz block ×4 | n/a | `:301-327`, quartz only | partial | N | low | T59 |
| Luck | `ModLuck:24-150`, lapis block ×9, silk-touch refusal | n/a | `:529-630`, lapis only, no refusal, fortune on weapons | partial | Y in part (#106/#296) | medium | T23 / T59 |
| Diamond | `ModDiamond:12-31` | n/a | `:232`, exact item not tag | have | Y (#106/#265) | low | — |
| Emerald | `ModEmerald:12-30` | n/a | `:271` | have | Y (#106) | none | — |
| Reinforced | `ModReinforced:19-83`, plate center = gold cast | n/a | `:366`, plate center = gold ingot; "Unbreakable" name shipped (#457) | partial | N (reagent only) | low | T69 |
| Mending Moss | `ModMendingMoss:30-155` (hotbar/offhand, 150t timer) | n/a | `:416,1293` any slot, 1/150 roll; stored-XP line (#457) | deviates | Y (#107) | low | — |
| Silktouch | `ModSilktouch:17-45` + refusals | n/a | `:431`, no refusals | partial | Y in part (#107) | medium | T23 |
| Soulbound | `ModSoulbound:20-71` | n/a | `:456,1368,1383` | have | Y (#107/#344) | none | — |
| Creative modifier / extra slots | `ModCreative:12-34` (hidden, no recipe) | n/a | survival `extra_slot` cap 5 + netherite form | deviates | Y (#107, SCOPE M2) | low | — |
| Fortify | `ModFortify:24-76`, harvestOnly, per-material overlay | n/a | `Fortification.java`, bows only refused, no overlay | partial | N | low | T70 |
| Embossing / ExtraTrait | `ModExtraTrait:40-155` | n/a | `Embossing.java`, no donor-part gate, no compat check | partial | Y in part (SCOPE:381) | low | T23 |
| Smite | `ModAntiMonsterType` + consecrated soil | n/a | `:802`, glowstone stand-in | partial | Y (#162, #429 open) | medium | T59 |
| Bane of Arthropods | `TinkerModifiers:137-139` | n/a | `:819` + extra-info line (#457) | have | — | none | — |
| Fiery | `ModFiery:21-63` | n/a | `:849` + `IgniteOnHitSeam` + both extra-info lines (#457) | have | — | none | — |
| Necrotic | `ModNecrotic:18-44` + necrotic bone | n/a | `:877`, wither skull stand-in | partial | Y (#162, #429 open) | medium | T59 |
| Knockback | `TinkerModifiers:172-174` piston + sticky | n/a | `:705`, piston only | partial | Y (#163 flagged) | low | T59 |
| Shulking | `ModShulking:18-41` popped chorus | n/a | `:728`, shulker shell | deviates | Y (#163) | low | — |
| Webbed | `ModWebbed:13-20` | n/a | `:754` | have | Y (#163) | none | — |
| Beheading | `ModBeheading:33-150` pearl + obsidian | n/a | `:677` obsidian only, six head types | deviates | Y (PR #197) | low | — |
| Blasting | `ModBlasting:33-129` (3 TNT) | n/a | absent, unscoped | missing | N | medium | T24 |
| Glowing | `ModGlowing:17-42` | n/a | `:995` `glowing`, ender eye, `minecraft:light` in the dark | deviates | Y (#456) | low | — |
| Width++ / Height++ expanders | `ModHarvestSize:11-19`, `ToolEvents:38-70` | n/a | `expander_w`/`expander_h` + `harvest_width`/`harvest_height`, `AoeHarvest.Shape`'s per-tool magnitudes | have | Y (#438) | none | — |
| Fins | `ModFins:11-31` | n/a | absent | missing | Y (SCOPE:287) | low | T17 |
| Modifier/trait incompatibility layer | `Modifier.java:67-115` + `canApplyTogether` | ModifierRequirements | none (`ModifierApplication` has no compat check) | missing | N | medium | T23 |
| Modifier tooltips (colors, leveled names, extra info) | `TooltipBuilder:120-181` | n/a | per-modifier colours, leveled names, every upstream extra-info line (#457) | have | — | none | — |
| Modifier overlay art | 21 model files | n/a | `ModifierArt:33` 17 + 442 derived textures | partial | Y (#257) | low | — |
| Reagent items (silky cloth/jewel, plate, moss, expanders, soil, bone, kit) | `TinkerCommons:150-352` | n/a | most ship; expanders shipped (#438); soil/necrotic bone absent | partial | Y in part (#429, #438) | medium | T59 |
| InfiTool hidden trait + showcase tools | `TinkerModifiers:212` | n/a | absent | missing | N | low | — |
| Searing (auto-smelt) | n/a (trait only) | n/a | `:160`, magma cream | forgeweave-only | Y (SCOPE M2 / #108) | none | — |
| Magnetic Pull | n/a | n/a | `:259`, ender pearl | forgeweave-only | Y (#108) | none | — |
| Aquadynamic (modifier form) | trait only | n/a | `:329`, turtle scute | forgeweave-only | Y (#108) | none | — |
| Resonant | n/a | n/a | `:338`, echo shard | forgeweave-only | Y (#108) | none | — |
| Far Reach | n/a | n/a | `:346`, amethyst shard | forgeweave-only | Y (#108) | none | — |
| Wind Burst | n/a | n/a | `:903`, breeze rod, warmace only | forgeweave-only | Y (#223) | none | — |
| Netherite bonus slot + netherite extra-slot recipe | n/a | n/a | `:1010`, `extra_slot_netherite.json` | forgeweave-only | Y (PR #135) | none | — |

### 3.4 Tools

All 9 harvest tools, 6 shipped melee weapons, the cleaver and 3 bows exist with upstream part lists, costs, station/forge split, stat math, AoE shapes, repair math and the right-click abilities ported constant-for-constant. Six tools are recorded Forgeweave additions. The dominant unrecorded gap is that the station weapons carry `mineable/axe` at full mining speed, so a broadsword chops logs like a hatchet.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Pickaxe | `Pickaxe.java:32-66` | n/a | `ForgeweaveItems:200-202` | have | — | none | — |
| Shovel | `Shovel.java:42-105` | n/a | `:203-205` | have | — | none | — |
| Shovel/excavator grass paths | `Shovel.java:64-97` | ItemAbility SHOVEL_FLATTEN | no `canPerformAction`, no path | missing | N | medium | T33 |
| Hatchet | `Hatchet.java:31-96` | n/a | `:206-208` | have | — | none | — |
| Hatchet +0.5 flat attack | `Hatchet.java:100-104` | n/a | `flatAttackBonus 0.0f` | missing | N | low | T65 |
| Hatchet: leaves at full speed, 0 durability | `Hatchet.java:68-82` | n/a | no leaves carve-out | missing | N | low | T65 |
| Per-tool knockback (hatchet 1.3, mattock 1.1, lumberaxe 1.5, rapier 0.6) | `ToolHelper:737-740` | tool-definition modifier | `KnockbackMultiplierSeam` via `ForgeweaveInnates#collect` + `CombatSeams#onKnockback` | have | Y (#553) | none | — |
| Mattock | `Mattock.java:54-206` | n/a | `ToolConstants:225-229`, `MattockItem` | have | — | none | — |
| Mattock per-head tier and repair by either head | `Mattock.java:65-112` | n/a | one head material for both | partial | Y for tier (#294), N for repair | low | T66 |
| Kama | `Kama.java:56-249` | n/a | `ToolConstants:231-235`, `KamaItem` | have | — | none | — |
| Kama block-shearing | `Kama.java:84-92` | n/a | absent | missing | Y (PR #322) | low | — |
| Hammer | `Hammer.java:33-111` | n/a | `ToolConstants:261-265`, PLANE_3X3 | have | — | none | — |
| Hammer +3..6 vs undead | `Hammer.java:66-77` | n/a | concussion rider only | missing | N | medium | T35 |
| Hammer tier from hammer head only | `Hammer.java:105-106` | n/a | max across all HEAD slots | deviates | Y (#294) | low | — |
| Excavator | `Excavator.java:29-89` | n/a | `ToolConstants:267-271` | have | — | none | — |
| Lumber axe | `LumberAxe.java:52-300` | n/a | `:273-277`, tick-spread fell (#299) | have | — | none | — |
| Scythe | `Scythe.java:40-198` | n/a | `:279-283`, CUBE_3X3X3 + sweep | have | — | none | — |
| Scythe AoE entity shearing | `Scythe.java:149-176` | n/a | not a `KamaItem`; cannot shear at all | partial | N (silk-touch half recorded) | medium | T36 |
| Broadsword | `BroadSword.java:25-88` | n/a | `:191-195` + `BroadswordSweep` | have | — | none | — |
| Broadsword parry | none upstream | n/a | `ForgeweaveInnates:103-106,508-552` | forgeweave-only | Y (#155/#303) | none | — |
| Longsword | `LongSword.java:28-136` | n/a | `:197-204` + ChargedLeap | have | — | none | — |
| Longsword/frypan charge movement (0.9 / 0.7) | `LongSword:86-92`, `FryPan:144-150` | n/a | only bows use `BowDrawMovement` | missing | N | medium | T37 |
| Rapier | `Rapier.java:30-132` | n/a | `:206-213` + Lunge | have | — | none | — |
| Rapier hybrid damage | `Rapier.java:59-96` | n/a | 5% current-health strike | deviates | Y (#155) | none | — |
| Battlesign | `BattleSign.java:40-177` | n/a | `:215-218` + Deflect | have | — | none | — |
| Frying pan | `FryPan.java:44-190` | n/a | `:220-223` + ChargedLaunch | have | — | none | — |
| Frypan boing sound / Bane of Pigs | `FryPan.java:51-132` | n/a | absent | partial | N | low | T50 |
| Cleaver | `Cleaver.java:34-116` | n/a | `:252-259` + innate beheading | have | — | none | — |
| Cleaver swallows right-click | `Cleaver.java:44-49` | n/a | passes to off-hand | missing | N | low | T67 |
| Battleaxe | class exists, never registered | n/a | `:237-250` + SweepingBlow | forgeweave-only | Y (SCOPE M3 / #153) | none | — |
| Dagger | commented out | 1.20 DAGGER | `:299-306` + Backstab | forgeweave-only | Y (SCOPE M3) | none | — |
| Vein hammer | none | 1.20 VEIN_HAMMER | `:285-297`, cap 64 | forgeweave-only | Y (#153/#157) | none | — |
| Katana | none | n/a | `:318-325` + DamageRamp | forgeweave-only | Y (#153/#160) | none | — |
| Scimitar | none | n/a | `:308-316` + Lacerate | forgeweave-only | Y (#153/#159) | none | — |
| Warmace | none | n/a | `:327-335`, vanilla mace hooks | forgeweave-only | Y (ADR-0005 d.4 / #161) | none | — |
| Melee mining role (SwordCore: web/plants, 0.5×) | `SwordCore.java:15-51` | sword tag at 0.5× | `#minecraft:sword_efficient` + cobweb ×7.5 at 0.5×; pan/sign/warmace mine nothing | have | Y (#437) | none | — |
| Shortbow | `ShortBow.java:32-119` | n/a | `:337-349` | have | — | none | — |
| Longbow | `LongBow.java:27-99` | n/a | `:351-365` | have | — | none | — |
| Crossbow | `CrossBow.java:56-204` | n/a | `:367-382` + `CrossbowItem` | have | — | none | — |
| Crossbow ammo = bolts | `CrossBow.java:173-183` | n/a | fires vanilla arrows | deviates | Y (SCOPE M3, bolts cut) | none | — |
| Material arrows + shuriken | `Arrow.java`, `Shuriken.java`, ProjectileCore | n/a | absent | missing | Y (SCOPE M3.5) | **high** | T17 |
| Launcher projectile damage constants | `BowCore:284-298` | n/a | only bonusDamage rides the arrow | partial | Y (#394) | low | T17 |
| Part roster and costs | `TinkerTools:174-206` | n/a | `PartBuilderRecipes:60-130` | have | — | none | — |
| Arrow head / shaft / fletching / bolt core parts | `TinkerTools:203-207` | n/a | absent | missing | Y (SCOPE M3.5) | medium | T17 |
| Station vs Tool Forge gate | `TinkerHarvestTools:99-108` etc. | n/a | `large_tools` tag | have | Y (#152/#336) | none | — |
| Stat formula (head/extra/handle averaging, per-tool multipliers) | `ToolNBT:37-99`, `ToolHelper:88-120` | n/a | `ToolConstants:394-496` | have | — | none | — |
| Attack speed/damage as MAINHAND attributes, gated on !broken | `ToolCore:262-273` | n/a | `ToolItem:282-341` | have | — | none | — |
| Damage cutoff curve (15 / 13 / 18 / 25 / 30) | `ToolHelper:830-865` | n/a | `ToolItem:384-431` | have | Y (#295/#422) | none | — |
| Custom attack pipeline (1.12 crit/cooldown/hurt-resist) | `ToolHelper:626-828` | rides vanilla | vanilla `Player#attack` | deviates | Y (ADR-0005 d.1) | none | — |
| Per-hit and per-block durability cost | `ToolCore:162-168,529-552` | n/a | `ToolItem:629-690` | have | — | none | — |
| Broken state (never destroyed, 0.3 speed, no drops) | `ToolHelper:528-604` | n/a | `ToolItem:433-514` | have | Y (CONTEXT invariant) | none | — |
| Repair math (durability, modifier penalty, diminishing returns) | `TinkersItem:300-440` | n/a | `ToolRepair`, `ToolAssemblyRecipes:856-908` | have | Y (#281) | none | — |
| Multi-part repair + per-part repair modifiers (Hammer 2.5×, …) | `TinkersItem:290-296` + 11 overrides | n/a | first HEAD slot only, modifier 1.0 | partial | N | medium | T31 |
| Sharpening kit as repair material | `ToolCore:491-511` | n/a | kit only used by fortification | partial | N | low | T32 |
| Dual-tool offhand harvest | `DualToolHarvestUtils:19-56` | n/a | absent | missing | Y (SCOPE M3 non-goal) | medium | — |
| Categories driving durability/haste/looting | `Category.java:13-25` | n/a | `ToolConstants:48-73` + weapon flag | have | Y (#398) | none | — |
| Indestructible dropped tool/part entities | `TinkersItem:97-116` | gated variant | only netherite `fireproof` trait | missing | N | medium | T16 |
| Tool display name prefixed by material | `ToolCore:379-394` | material prefix | no `getName` override | missing | N | low | T15 |
| Tooltip (broken, durability, tier, speed, attack, slots) | `ToolCore:280-364` | n/a | `ToolTooltip` (#54/#379/#380) | have | — | none | — |
| Re-equip / mining-progress reset on identity change | `ToolCore:576-686` | n/a | `ToolItem:552-620` (#414) | have | — | none | — |
| Not enchantable at table / not book-enchantable | `TinkersItem:488-491` | n/a | `ToolItem:236-239` behind config | have | Y (CONTEXT invariant) | none | — |
| Creative tab per-material tools + Infi tools | `ToolCore:397-445` | n/a | one componentless entry each | partial | Y in part (PR #362) | low | T76 |
| Combat innates (pierce, heft, reap, concussion…) | none upstream | 1.20 piercing inspired | `ForgeweaveInnates:269-360` | forgeweave-only | Y (SCOPE M3 / #164) | none | — |
| Per-weapon full-charge attack particles | 7 `spawnAttackParticle` sites | n/a | absent | missing | N | low | T51 |

### 3.5 Smeltery

The core loop — multiblock rules, melting math, fuel model, in-tank alloying, casting table/basin/faucet, gold and clay casts, tanks/gauges/windows, drain, the seared block family, entity melting, GUI and JEI — is a faithful port with every deviation recorded. The unrecorded gaps are peripheral 1.12 content: no channels, no seared furnace, no tinker tank, no stone→seared-stone chain, no recipe-derived melting of vanilla metal items.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Multiblock detection (1×1..9×9, floor + walls, no ceiling) | `MultiblockCuboid:14-76`, MAX_SIZE 9 | HeatingStructureMultiblock | `SmelteryScan:46-142` | have | Y (#95) | none | — |
| Structure failure feedback | returns null silently | MultiblockResult | translatable reasons `SmelteryScan:37-67` | deviates | Y (SCOPE M2) | none | — |
| Valid wall/floor blocks, tank required | `TinkerSmeltery:246-252` | duct/chute in floor | `SmelteryScan:277-316` | have | Y (#95/#289/#277) | none | — |
| Claimed-by-another-core refusal, unloaded chunks | `MultiblockTinker:19-36` | n/a | `SmelteryScan:197-247` | have | Y (#288) | none | — |
| Servant TEs + always-ticking controller | `TileSmeltery:74-131` | still ticks | event-driven, no ticker | deviates | Y (SCOPE perf budget) | none | — |
| Tank capacity 8 ingots/block, melting slot per block | `TileSmeltery:55,250` | n/a | `SmelteryControllerBlockEntity:93,244` | have | — | none | — |
| Melting math (heat, TIME_FACTOR 8, 4-tick step) | `MeltingRecipe:44-73`, `TileHeatingStructure:23` | explicit temps | `MeltingRecipe:69-119`, `:259-444` | have | Y (#96/#97) | none | — |
| Ore melting temperature from doubled amount | `TinkerSmeltery:672-681` via `calcTemperature` | n/a | temperature from base 144 | deviates | N (code comment only) | low | T60 |
| Fuel model (fluid + duration, heat passes) | `TinkerRegistry:688-734` | rate + duration | `SmelteryFuel`, `:722-833` | have | Y (#97/#287) | low | — |
| Fuel list: lava only | `TinkerSmeltery:327` | + blazing blood | `smeltery_fuel/lava.json` | have | Y (SCOPE M2) | none | — |
| Ore doubling (VALUE_Ore ×2) | `Config.oreToIngotRatio=2` | 1.33× smeltery rate | tiered cores 1.5× / 2× | deviates | Y (SCOPE M2 / #99) | none | — |
| Nether ore yields / raw-drop equivalence | `TinkerCommons:255-256` | OreMeltingRecipe | `*_ore.json` base 144 ore:true | deviates | Y (SCOPE M2) | none | — |
| Tiered smeltery cores (Standard / Nether) | single controller | n/a | `SmelteryCore`, `nether_core.json` | forgeweave-only | Y (SCOPE M2 / #99) | none | — |
| Melting recipes as data + `c:` tag coverage | `TinkerSmeltery:672-681` | JSON melting | 76 melting recipe files | have | Y (#96) | none | — |
| Recipe-derived melting of metal-crafted items (+ rails, horse armor) | `TinkerSmeltery:392-399,746-800` | explicit rows | absent | missing | N | **high** | T7 |
| Stone / cobblestone / grout / stone parts → molten seared stone | `TinkerSmeltery:377-411` | kept | no such rows | partial | N | **high** | T8 |
| Seared brick/block/cobble/glass casting | `TinkerSmeltery:413-428` | kept | none | missing | Y (PR #246 follow-up) | medium | T38 |
| Alloying (auto, ratio-based, every 4 ticks) | `TileSmeltery:205-242` | AlloyingModule | `SmelteryControllerBlockEntity:914-1044` | have | Y (#98/#291) | none | — |
| Alloy set (manyullyn, pig iron, knightslime, obsidian, clay) | `TinkerSmeltery:511-545` | different set | 4 of 5; clay alloy absent | partial | Y in part (SCOPE M3.2) | low | — |
| FW alloys: rose gold, netherite, steel, amethyst bronze | n/a | 3 of 4 exist | `alloy_recipe/*.json` | forgeweave-only | Y (SCOPE M2/M3.2) | none | — |
| Entity melting (2 dmg/s, blood default, golem/snowman/emerald) | `TileSmeltery:154-202` | EntityMeltingModule | `:547-621` + recipes | have | Y (#270/#363) | none | — |
| FW entity melting: blaze, warden | n/a | blazing blood exists | `blaze.json`, `warden.json` | forgeweave-only | Y (#270) | none | — |
| Dropped items picked up and melted | `TileSmeltery:161-181` | n/a | `:267,547-565` | have | Y (#290) | none | — |
| Full-tank stall state in GUI | `TileSmeltery:122-148` | n/a | `:130,458-471` | have | Y (#290/#377) | none | — |
| Controller item handler (hopper feeds melting inventory) | Mantle TileInventory | chute instead | no handler on the core; chute only | deviates | N | medium | T39 |
| Casting table + basin (cooldown, consumesCast, comparator) | `TileCasting:36-138` | ItemCastingRecipe | `CastingRecipe`, `CastingBlockEntity` | have | Y (#100/#207) | none | — |
| Faucet (6 mB/tick, 144 transaction, redstone) | `TileFaucet:22-125` | POWERED continuous | `FaucetBlockEntity:25-56` | have | Y (#100/#207/#355) | none | — |
| Gold cast creation + full cast roster | `TinkerSmeltery:303-320,533-575` | CastDuplication | `cast_*.json` ×33, one item per cast | have | Y (#100/#222/#272) | none | — |
| Brass / alubrass as cast-creation fluids | `TinkerSmeltery:303-307` | n/a | absent | missing | Y (#292) | low | — |
| Clay single-use casts | `TinkerSmeltery:294-296` | sand casts | `clay_cast_*.json` + config | have | Y (#292/#387) | none | — |
| Shard cast + shard melting/casting (72 mB) | `TinkerSmeltery:248-251,646-650` | n/a | shard is a part, uncastable/unmeltable | missing | N | medium | T40 |
| Metal tool parts melt back at part cost | `TinkerSmeltery:533-549` | MaterialMeltingRecipe | `MeltingRecipe:167-199` | have | Y (#184) | none | — |
| Ingot/nugget/block casting per metal | `TinkerSmeltery:618-637` | n/a | full set | have | Y (#100/#206) | none | — |
| Plate / gear casting outputs | `TinkerSmeltery:630-637` | tag-based | casts exist, no outputs | partial | Y (PR #361) | low | — |
| Emerald ore/gem/block melting + block casting | `TinkerSmeltery:471-479` | kept | fluid + gem cast only | partial | N | medium | T41 |
| Molten glass, clear glass + pane casting | `TinkerSmeltery:481-489` | kept | no glass fluid | missing | N | medium | T42 |
| Molten clay casting (terracotta, bricks, stained) | `TinkerSmeltery:441-467` | kept | fluid + melting only | partial | N | low | T71 |
| Molten dirt + mud bricks | `TinkerSmeltery:434-439` | dropped | absent | missing | N | low | T71 |
| Water from ice/snow; blood slime ball | `TinkerSmeltery:356-373` | kept | rotten flesh → blood only | partial | Y in part (#232) | low | T71 |
| Bucket casting (fill any container) | `TinkerSmeltery:354` | ContainerFillingRecipe | `casting_recipe/bucket_*.json` | have | Y (#542) | none | — |
| Lavawood basin casting; red sand from blood | `TinkerSmeltery:497-501` | n/a | absent | missing | Y for lavawood (SCOPE M3.2) | low | T71 |
| Molten fluid roster (colors/temps) + blocks/buckets | `TinkerFluids:83-230` | +300 scale | `ForgeweaveFluids:94-243` | have | Y (#92/#285/#286) | none | — |
| Fluids absent (glass, dirt, milk, blue/purple slime, compat metals) | `TinkerFluids:44-146` | partly dropped | molten slime substitutes purple | partial | Y in part (SCOPE M3.2) | low | T57 |
| FW fluids: rose gold, netherite, carbon, amethyst, blazing/deep blood | n/a | some exist | `ForgeweaveFluids:110-183` | forgeweave-only | Y (SCOPE M2/M3.2, #270) | none | — |
| Molten fluid block physics (lava-like, light, viscosity) | `BlockMolten`, `TinkerFluids:188-200` | n/a | `ForgeweaveFluids:264-288` | have | Y (#285) | none | — |
| Seared tank / gauge / window | `TileTank:17-100` | TankBlockEntity | `SearedTankBlock(Entity)` | have | Y (#95/#145/#379) | none | — |
| Seared drain | `TileDrain:18-72` | DrainBlockEntity | `SearedDrainBlockEntity` | have | Y (#95/#183) | none | — |
| Seared duct + chute | n/a | 1.20 duct/chute | `SearedDuct*`, `SearedChute*` | forgeweave-only | Y (#277) | none | — |
| Channels (fluid transport) | `BlockChannel`, `TileChannel` | kept | absent, unscoped | missing | N | **high** | T9 |
| Seared furnace multiblock | `TileSearedFurnace`, `MultiblockSearedFurnace` | dropped | absent, unscoped | missing | N | **high** | T10 |
| Tinker tank multiblock | `TileTinkerTank`, `MultiblockTinkerTank` | dropped | absent, unscoped | missing | N | medium | T44 |
| Seared block family (12 variants, stairs, slabs, glass, brick chain) | `BlockSeared:25-38` | seared + scorched | `ForgeweaveBlocks:104-190` | have | Y (#93/#274/#289) | low | — |
| Seared stairs/slabs as ceiling blocks | `MultiblockSearedFurnace:43-64` | n/a | n/a for the smeltery | missing | Y (#369 open) | low | — |
| Grout (simple + bulk recipe) | `grout.json`, `grout_simple.json` | both kept | simple + bulk | have | Y (#503) | none | — |
| Controller block (FACING, ACTIVE, gated GUI, fire particles) | `BlockMultiblockController:30-140` | keeps particles | no `animateTick` | partial | N | low | T73 |
| Smeltery GUI (fluid column, fuel gauge, melt grid) | `GuiSmeltery`, `GuiSmelterySideInventory` | similar | `SmelteryScreen`, `SmelteryMenu` | have | Y (#101/#146/#308/#377) | low | — |
| JEI (melting, alloy, casting categories, catalysts) | `JEIPlugin:118-177` | split categories | `ForgeweaveJeiPlugin:143-147` | have | Y (#109) | low | — |
| Config surface (ore ratio, obsidian alloy, clay casts, °C) | `Config.java` | n/a | `ForgeweaveConfig:33-63` | partial | Y (#276) | none | — |
| Melting/casting Forge events (addon API) | `TinkerSmelteryEvent`, `TinkerCastingEvent` | n/a | datapack recipes instead | missing | Y (SCOPE non-goal) | low | — |

### 3.6 Stations

The six stations are ported close to 1.12 in layout, art, tabs, side inventories, info panels and error takeover, with JEI coverage well beyond 1.12's own plugin. Three findings are serious: the repair/modify tab accepts items in three slots it never reads (blocker), a tool cannot be renamed on its own and the name is per-menu, and the Part Builder silently destroys shard change.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Station group tab row (BFS, crafting-station gate) | `ContainerTinkerStation:53-127` | n/a | `StationGroup:112-193` | have | Y (#78) | none | — |
| Tab art (vanilla creative sprites) | `GuiTinkerTabs` | n/a | `StationScreen:52-84` | have | Y (#78) | none | — |
| Tool Station panel art | `GuiToolStation:60-483` | n/a | `ToolStationScreen:114-360` | have | — | none | — |
| Tool-selection sidebar (repair + buildable tools) | `GuiButtonsToolStation:31-58` | n/a | `ToolStationTabs:144-219` | have | Y (#47/#336) | none | — |
| Per-tool dynamic slot layouts | `GuiToolStation:165-219` | n/a | `ToolStationTabs:122-174` (upstream pixels) | have | Y (#47/#304) | none | — |
| Repair/modify tab reads all five free slots | `ContainerToolStation:308-315` | n/a | binds free slots 0,1 only; slots 3–5 accept and are ignored | partial | N (#340 asked for all) | **blocker** | T2 |
| Recipe precedence (repair → replace → modify → rename → build) | `ContainerToolStation:143-175` | n/a | `ToolAssemblyRecipes:281-330` | have | — | none | — |
| Rename tool (rename-only, synced to all players) | `ContainerToolStation:121-273` | n/a | per-menu name, no rename-only branch | partial | N | high | T11 |
| Output take: consume, events, craft sound | `ContainerToolStation:177-214` | n/a | `ToolStationMenu:542-613`, no sound | partial | N | low | T50 |
| Info panels (tool info + traits, warnings, error takeover) | `GuiToolStation:108-306` | n/a | `ToolStationScreen:270-343` | have | Y (#47/#378) | low | — |
| Info panel widget (nine-slice, wood/metal, slider, tooltips) | `GuiInfoPanel:30-322` | n/a | `InfoPanel.java` | have | Y (#47/#79/#376) | none | — |
| Tool preview, ghost part icons, repair glyphs | `GuiToolStation:369-528` | n/a | `ToolStationScreen:151-360` | have | — | none | — |
| Tool Forge (metal styling, own buildable set) | `GuiToolForge`, `ContainerToolForge` | n/a | forge flag + `large_tools`; 5% repair discount | deviates | Y (SCOPE:103) | none | — |
| Repair rules: material-value units, multi-part, per-part modifier | `TinkersItem:290-440` + 11 overrides | n/a | one ingredient at one unit | partial | N | medium | T30 / T31 |
| Crafting-grid sharpening-kit repair | `RepairRecipe:25-90` | n/a | absent | missing | N | medium | T32 |
| Part exchange | `ToolBuilder:270-390` | n/a | `ToolAssemblyRecipes:370-530` | have | Y (#264/#293) | none | — |
| Modifier application across free slots | `ToolBuilder:167-262` | n/a | two slots, one recipe each | partial | N | high | T2 |
| Repair-tab slots accept any item (explained refusal) | `SlotToolStationIn:34-50` | n/a | slot refuses foreign items up front | deviates | N | low | — |
| Part Builder GUI (slots, layout, art) | `ContainerPartBuilder:58-67` | n/a | `PartBuilderMenu:106-127` | have | Y (#9/#43/#45) | none | — |
| Part Builder blocks craft when change slot occupied | `ContainerPartBuilder:130-142` | n/a | crafts anyway; change discarded | deviates | N | high | T12 |
| Part Builder info panel (stats, traits, cost, warnings) | `GuiPartBuilder:138-234` | n/a | `PartBuilderScreen:130-172` | have | Y (#47/#64) | low | — |
| "Material Value: x" line + slot ghost icons | `GuiPartBuilder:102-135` | n/a | `PartBuilderScreen:83-84` | have | — | none | — |
| Part Crafter mode (pattern buttons, chest swap, title) | `ContainerPartBuilder:69-224` | n/a | buttons yes, title stays "Part Builder" | partial | Y (#78) | low | — |
| Pattern-chest side inventory placement | `ContainerPartBuilder:88-95` | n/a | any item-handler neighbour | deviates | Y (SCOPE:36) | none | — |
| Stencil Table (selection, reuseStencils, shift-click to chest) | `ContainerStencilTable:33-137` | removed | `StencilTableMenu:47-140` | have | Y (#44/#68/#276) | low | — |
| Crafting Station (persistent grid, remainders, synced result) | `ContainerCraftingStation:63-183` | n/a | `CraftingStationMenu:30-120` | have | Y (#40/#68) | none | — |
| Crafting Station side inventory (skip stations, blacklist config) | `ContainerCraftingStation:75-137` | n/a | first horizontal handler, no exclusions | partial | N | low | T74 |
| Side inventory panel widget | `GuiSideInventory:30-110` | n/a | `SideInventoryPanel` | have | Y (#68/#376) | none | — |
| Pattern/Part Chest storage (256 slots, filtered) | `TileTinkerChest:12-70` | n/a | `ChestBlockEntity:59-240`, 54-slot pages | have | Y (#66/#305/#342) | none | — |
| Chest GUI art (blank.png scaling chest + slider) | `GuiPatternChest:20-38` | n/a | vanilla generic_54 + paging | deviates | N (only javadoc) | medium | T45 |
| Pattern Chest one-of-each + cast-chest mode | `TilePatternChest:26-126` | separate cast chest | duplicates allowed, no cast branch | partial | N (javadoc stale) | medium | T46 |
| Chests keep inventory when broken | `BlockToolTable:158-161` | n/a | `minecraft:container` on the dropped item, behind `chestsKeepInventory` | have | Y (#478) | none | — |
| Right-click with held item inserts into chest | `BlockToolTable:94-107` | n/a | opens only | missing | N | low | T75 |
| Tables retain crafted wood/metal texture | `TableRecipeFactory:53-100` | n/a | `RetexturedShapedRecipe` + baked model | have | Y (#43/#73/#77) | low | — |
| Creative tab lists all table variants | `BlockToolTable:110-146` | n/a | one default each | missing | N | low | T75 |
| Items rendered on table tops | `TileTable:78-112` | n/a | no station BE renderer | missing | N | low | T75 |
| Tab / selection sync to other players at a station | `ContainerToolStation:66-84` | n/a | per-menu DataSlot | partial | Y (ToolStationTabs javadoc) | low | T11 |
| Station open flow / server-authoritative menus | `BlockToolTable:78-86` | n/a | `StationMenuHost:16-58` | have | — | none | — |
| Station GUI textures derived | 7 upstream gui textures | n/a | 6 derived; `blank.png` absent | partial | Y (#43/#68/#75) | low | T45 |
| DarkModeEverywhere shader blacklist | n/a | n/a | `ForgeweaveDarkModeCompat` | forgeweave-only | Y (#335) | none | — |
| JEI crafting-station catalyst + transfer | `JEIPlugin:145-151` | n/a | `CraftingStationTransferInfo` | have | — | none | — |
| JEI subtype interpreters | `JEIPlugin:83-118` | n/a | `SubtypeKeys` | have | Y (#307) | none | — |
| JEI station categories (part, assembly, repair, modifier, emboss) | none in base 1.12 | n/a | 6 categories + transfer | forgeweave-only | Y (SCOPE:330 / #15/#165) | none | — |
| Tooltips on every station screen | Mantle default | n/a | `StationScreen:24-133` | have | Y (#43/#57/#75) | none | — |
| Rename field length + key handling | `GuiToolStation:132-333` | n/a | `ToolStationScreen:222-226` | have | — | none | — |

### 3.7 Book & UX

The guide book exists (item, both recipes, cover/index/spreads, registry-driven pages) but the engine is a Forgeweave-authored fixed spread and every 1.12 page kind is an approximation — all of that belongs to the open #430 checklist (#428, page overflow, is already closed by `53df05c`). Outside the book: no first-join gift, no material-prefixed item names, no custom sounds or particles, one creative tab. Tooltips are the strongest area.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Guide book item ("Materials and You") | `ItemTinkerBook:25-45` | n/a | `GuideBookItem:23-46` | have | Y (PR #360) | none | — |
| Book recipes (book + pattern; paper + patterns) | `recipes/tools/book.json` | n/a | `ForgeweaveRecipeProvider:99-111` | have | Y (PR #360) | none | — |
| First-join book gift + `spawnWithBook` | `PlayerDataEvents:19-29`, default true | n/a | absent (no config, no handler) | missing | N (PR #360 misstated upstream) | high | T13 |
| Book engine (screen-scaled Mantle spread, chrome, book font) | Mantle `GuiBook`, `appearance.json` | Mantle BookScreen | fixed 320×200, authored chrome | deviates | Y then superseded (#430) | high | T14 |
| Book cover (title, subtitle/author, cover colour) | `appearance.json` | n/a | `BookScreen:174-182`, reworded subtitle | partial | N | low | T48 |
| Index page with item icons | `book/index.json` | n/a | `BookContent:72-115` | have | Y (PR #360) | none | — |
| Page chrome: hover tooltips, links, back arrow | Mantle elements + `ContentMaterial` tooltips | n/a | arrows + page numbers + index links | partial | Y (PR #360) | medium | T48 |
| Page text overflow | Mantle element boxes | n/a | `BookLayout` paginates + scissor clip | have | Y (#428 closed, `53df05c`) | low | — |
| Per-section listing pages | `ContentListingSectionTransformer:20-45` | n/a | sections open with a text page | missing | N | medium | T48 |
| Materials overview icon grid with links | `AbstractMaterialSectionTransformer:36-66` | n/a | alphabetical material pages, no grid | missing | N | medium | T48 |
| Material page (side column, part icons, hover desc, flavour) | `ContentMaterial:62-215` | n/a | title + flat stat lines + trait names | partial | Y (PR #360) | medium | T48 |
| Bow materials section | `BowMaterialSectionTransformer` | n/a | bow stats folded into material pages | partial | Y (#403/#412) | low | T48 |
| Tool page (properties list, station slot graphic, parts) | `ContentTool:83-146` | n/a | icon + name + station blurb | partial | Y (PR #360) | medium | T48 |
| Tools static pages (repairing2, offhand, bolt crafting) | `book/sections/tools.json` | n/a | one repairing page; others feature-gated | partial | Y (PR #360, SCOPE) | low | — |
| Tools section page order | `tools.json:21-124` | n/a | scythe/cleaver moved | deviates | N | low | T77 |
| Modifier page (colour title, effects list, demo tools, inputs) | `ContentModifier:96-192` | n/a | name + description, alphabetical | partial | Y (PR #360) | medium | T48 |
| Fortify page variant | `ContentModifierFortify:14-40` | n/a | absent | missing | N | low | T48 |
| Smeltery section (image, structure text, 3D structure page) | `book/sections/smeltery.json` | n/a | intro + structure + FW page; no 3D | partial | Y (PR #360) | low | T48 |
| Seared furnace / tinker tank sections | index entries | n/a | features absent | missing | N (feature-gated) | low | T10 / T44 |
| Intro section content | `intro_tmp` + placeholders | n/a | welcome + workshop, reworded | have | Y (PR #360) | none | — |
| Book sections gated on installed modules | pulse-based `ModuleFileRepository` | n/a | no pulse concept; config toggles are server-side | n/a | — | low | — |
| Book data as resource-pack JSON + per-locale folders | `assets/tconstruct/book/**` | n/a | hard-coded `BookContent` + lang keys | partial | Y (PR #360) | low | T48 |
| Tool tooltip compact tier | `TinkersItem:446-473` | n/a | `ToolTooltip:128-161` recomposed | deviates | Y (#105/#379) | low | — |
| Tool tooltip Shift tier | `ToolCore:293-321` | n/a | `ToolTooltip:148-160` | have | Y (#54/#254/#424) | none | — |
| Tool tooltip Ctrl tier (per-part sections) | `ToolCore:323-364` | n/a | folded into Shift | deviates | Y (#380) | low | — |
| `extraTooltips` gating | `Config` + `TinkersItem:466` | n/a | `ForgeweaveClientConfig:46` | have | Y (#276) | none | — |
| Material-prefixed display names (tools and parts) | `ToolCore:378-394`, `ToolPart:190-204` | material prefix | no `getName` override anywhere | missing | N | high | T15 |
| Part tooltip (trait groups, stat blocks, error lines, "added by") | `ToolPart:80-253` | n/a | most lines; no `missing_stats`, no "added by" | partial | Y in part (#379/#376) | low | T81 |
| Pattern cost / tank mB / book / chest tooltip lines | `Pattern:109-116`, `ItemTank:28-40` | n/a | all but chest count | partial | Y (#379) | low | — |
| Lang coverage (all player-facing strings) | `en_us.lang` + 12 locales | n/a | `ForgeweaveLanguageProvider`, en_us only | have | Y (#65/#166) | low | — |
| Station stat text (colours, hover descriptions) | `HeadMaterialStats`, `CustomFontColor` | n/a | `StationText:51-346` | have | Y (#47/#64/#376) | none | — |
| Vanilla story advancement grants (upgrade_tools, iron_tools) | `AchievementEvents:27-60` | own tree | not granted on station assembly | missing | N | medium | T49 |
| Forgeweave advancement chain | none upstream | own tree | `ForgeweaveAdvancementProvider` | forgeweave-only | Y (SCOPE M2 / #110) | none | — |
| Ponder scenes + first-use chat hint | none | n/a | `ponder/*` | forgeweave-only | Y (SCOPE M2) | none | — |
| Creative tabs (six upstream tabs) | `TinkerRegistry:76-81` | several | one tab | deviates | N | low | T76 |
| Creative tool listing per material | `ToolCore:398-425` | n/a | componentless, once each | deviates | Y (PR #362) | low | T76 |
| Custom sounds (saw, anvil, frypan boing, squeak, shocking) | `Sounds.java:27-38` (CC-BY/CC0) | n/a | none; vanilla stand-ins for shocking/crossbow | partial | Y for shocking (#415) | medium | T50 |
| Attack-slash and heart-effect particles | `Particles.java:16-30` + 6 slash textures | n/a | electric spark only | missing | N | medium | T51 |
| Ambient particles (controller fire, casting smoke, autosmelt flame) | `randomDisplayTick` sites | n/a | none | missing | N | low | T73 |
| Smeltery death message ("tried to create molten player") | bespoke fire damage source | two damage types | vanilla IN_FIRE / MAGIC | deviates | Y (PR #363) | low | — |
| Book smeltery structure text matches FW rules | `structure.json` (3×3..11×11) | n/a | "up to 9×9" per SCOPE M2 | have | Y (SCOPE M2) | none | — |

### 3.8 World & gadgets

Forgeweave ships one slice of TinkerWorld — cobalt/ardite ore with config-driven Nether generation, blood, and green/magma slimy mud → crystals. Everything else in TinkerWorld is a recorded SCOPE non-goal until the world-content milestone is scoped at M6 planning; TinkerGadgets is entirely absent, and only slime boots and the slingshot are named in M5, leaving ~15 gadgets unplanned and unrecorded.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Cobalt ore block | `BlockOre:22-31` | n/a | `ForgeweaveBlocks:214-223` | have | Y (#104) | none | — |
| Ardite ore block | `BlockOre:41-44` | n/a | `ForgeweaveBlocks:215` | have | Y (#104) | none | — |
| Ore blocks drop themselves (silk-touch axis) | no drop override | n/a | drops one raw item; block unobtainable | deviates | Y (SCOPE:82) | low | — |
| Nether ore worldgen + gen/rate configs | `NetherOreGenerator:26-58` | n/a | configured/placed features, uniform 0-127 | partial | Y (#104/#276) | low | T78 |
| Slime islands (shell, lakes, trees, plants) | `SlimeIslandGenerator:51-177` | biomes/structures | absent | missing | Y (SCOPE:75) | high | T18 |
| Magma slime islands (Nether) | `MagmaSlimeIslandGenerator:30-79` | n/a | absent | missing | Y (SCOPE:75) | high | T19 |
| Blue slime entity + island spawns | `EntityBlueSlime`, `WorldEvents:29-33` | sky/ender slimes | absent | missing | Y (SCOPE:245) | high | T20 |
| Magma cube spawn override on magma islands | `WorldEvents:22-27` | n/a | absent | missing | Y (SCOPE:75) | medium | T19 |
| Slime dirt (4 colours) | `BlockSlimeDirt` | per SlimeType | absent | missing | Y (SCOPE:75) | medium | T57 |
| Slime grass + foliage colorizer | `BlockSlimeGrass`, `SlimeColorizer` | n/a | absent | missing | Y (SCOPE:75) | medium | T57 |
| Tall slime grass / fern | `BlockTallSlimeGrass` | n/a | absent | missing | Y (SCOPE:75) | low | T57 |
| Slime leaves | `BlockSlimeLeaves` | n/a | absent | missing | Y (SCOPE:75) | medium | T57 |
| Slime sapling → slime tree | `SlimeTreeGenerator` | n/a | absent | missing | Y (SCOPE:75) | medium | T57 |
| Slime vines (+ bowstring material) | `BlockSlimeVine`, `TinkerWorld:77-102` | n/a | absent | missing | Y (SCOPE:271/288) | medium | T57 |
| Congealed slime blocks (5 colours) | `BlockSlimeCongealed` | n/a | absent | missing | Y (SCOPE:75/245) | medium | T57 |
| Colored slime blocks + matchVanillaSlimeblock | `BlockSlime:19-68` | n/a | absent | missing | Y (SCOPE:75) | low | T57 |
| Colored slime balls (edible) + slime drops | `TinkerCommons:140-144,293-297` | n/a | absent (magma mud substitutes cream) | missing | Y (#339) | medium | T57 |
| Blue + purple slime fluids | `TinkerFluids:60-61,194-236` | 4 slime fluids | green `molten_slime` substitutes | deviates | Y (#232) | medium | T57 |
| Purple slime → knightslime alloy chain | `TinkerSmeltery:369-374,536-540` | n/a | molten slime (green) input | deviates | Y (#232, SCOPE:195) | medium | T57 |
| Blood fluid (rotten flesh, entity melting) | `TinkerFluids:58,183-186` | n/a | `ForgeweaveFluids:142` + recipes | have | Y (#92/#270) | none | — |
| Blazing blood / deep blood | none | blazing blood exists | `ForgeweaveFluids:162-182` | forgeweave-only | Y (#270/#181) | none | — |
| Blood slime ball casting | `TinkerSmeltery:364-367` | n/a | absent | missing | N (transitively deferred) | low | T57 |
| Slimy mud → slime crystals | `BlockSoil` SLIMY_MUD ×3 | n/a | green + magma; blue via lapis recipe | partial | Y (#232/#339) | low | T57 |
| Slime boots (bounce, no fall damage) | `ItemSlimeBoots:29-162` | slimesuit boots | absent | missing | Y (SCOPE:309 / #26) | high | T21 |
| Slime sling (charged launch) | `ItemSlimeSling:56-82` | staffs | absent | missing | Y (SCOPE:309 / #26) | high | T22 |
| Piggyback pack | `ItemPiggybackPack` | PiggyBackPackItem | absent | missing | N (unplanned) | medium | T56 |
| Punji sticks | `BlockPunji:132-175` | PunjiBlock | absent | missing | N (unplanned) | medium | T56 |
| Wooden hopper | `BlockWoodenHopper` | dropped | absent | missing | N (unplanned) | medium | T56 |
| Item rack + drying rack (+ drying recipe type, JEI) | `BlockRack`, `TinkerGadgets:296-325` | dropped | absent | missing | N (unplanned) | medium | T56 |
| Jerky foods + slime drops (15 items) | `TinkerCommons:361-379` | n/a | absent | missing | N (unplanned) | low | T56 |
| Wooden rail | `BlockWoodRail` | n/a | absent | missing | N (unplanned) | low | T56 |
| Wooden rail dropper | `BlockWoodRailDropper` | DropperRailBlock | absent | missing | N (unplanned) | low | T56 |
| Stone torch + stone stick | `BlockStoneTorch`, `TinkerGadgets:214` | dropped | absent | missing | N (unplanned) | low | T56 |
| Stone ladder | `BlockStoneLadder` | dropped | absent | missing | N (unplanned) | low | T56 |
| Dried clay / dried brick family | `BlockDriedClay`, `TinkerGadgets:144-147` | n/a | absent | missing | N (unplanned) | low | T56 |
| Brownstone family (~26 blocks, speed boost) | `BlockBrownstone`, `TinkerGadgets:150-164` | n/a | absent | missing | N (unplanned) | low | T56 |
| Glow ball throwable + glow block | `ItemThrowball:94-96`, `BlockGlow` | GlowballEntity | absent | missing | N (unplanned) | medium | T56 |
| EFLN throwable + explosion | `ItemThrowball:96`, `ExplosionEFLN` | EFLNEntity | absent | missing | N (unplanned) | medium | T56 |
| Fancy item frames (5 variants) | `EntityFancyItemFrame` | kept | absent | missing | N (unplanned) | low | T56 |
| Spaghetti / Mom's spaghetti chain | `TinkerGadgets:219-233` | n/a | absent | missing | N (unplanned) | low | T56 |
| Slime channels (5 colours) | `BlockSlimeChannel` | n/a | absent | missing | N (unplanned) | low | T56 |
| Gadgets content-family config toggle | pulse system | n/a | `ForgeweaveConfig:105-109` (no gadgets yet) | forgeweave-only | Y (#398) | none | — |

### 3.9 Ranged

M3.5 ports the launcher core faithfully — the three bows, limb/bowstring math, BOW/BOWSTRING stats, draw/release, crossbow two-phase load, per-stage draw art with modifier overlays, `preventSlowDown`, station layouts, tooltip lines and the Haste/Lightweight/Luck/Fortify branches — with every deviation recorded on #402 and its PRs. The whole ammo/projectile layer is deferred to M3.6 (bolts cut outright). Two unrecorded gaps: no nocked arrow rendered on a drawn bow, and no 1.12 crosshairs.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Shortbow | `ShortBow.java:33-113` | n/a | `ForgeweaveItems:253`, `ToolConstants:346` | have | Y (PR #405) | none | — |
| Longbow | `LongBow.java:27-99` | n/a | `:263`, `:362`, durability ×1.4 | have | Y (PR #406) | none | — |
| Crossbow | `CrossBow.java:57-203` | n/a | `:270`, `:379`, bonusDamage ×1.5 | have | Y (PR #406) | none | — |
| Bow limb part (3 ingots, pattern, casts) | `TinkerTools:210` | n/a | `ForgeweaveItems:144,508` | have | Y (PR #404) | none | — |
| Bow string part (1 ingot, no cast) | `TinkerTools:211` | n/a | `ForgeweaveItems:153` | have | Y (PR #404) | none | — |
| BOW material stats on every material | `TinkerMaterials:538-576` | relative modifiers | `bow` block on 33 materials | have | Y (PR #403) | none | — |
| BOWSTRING material stats | `BowStringMaterialStats` | n/a | `ToolConstants:454-488` | have | Y (PR #403) | none | — |
| Launcher stat block (limb averaging, floors) | `ProjectileLauncherNBT:27-80` | n/a | `LauncherStats:56-79` component | have | Y (PR #405) | none | — |
| Draw cycle (72000 ticks, broken refusal, <5 ticks, creative arrow) | `BowCore:121-177` | n/a | `BowItem:101-251` | have | Y (PR #405) | none | — |
| Draw progress = drawSpeed × ticks / drawTime | `BowCore:96-113` | n/a | `BowItem:187-200` | have | Y (PR #405) | none | — |
| Launch velocity, crit at full draw, 1 durability, shoot sound | `BowCore:179-213` | n/a | `BowItem:258-288` | have | Y (PR #405) | none | — |
| Ammo lookup (any vanilla arrow, hand → hotbar → inventory) | `AmmoHelper:25-50` | n/a | `BowItem:309-337` | have | Y (PR #405) | none | — |
| Vanilla-arrow pickup rule | `BowCore:224-233` | n/a | `BowItem:300-304` (matches clone) | have | Y — SCOPE line is wrong | low | T77 |
| Launcher bonusDamage / base damage / damage modifier | `BowCore:284-298` (Tinker ammo only) | n/a | bonusDamage rides the vanilla arrow | deviates | Y (PR #405 finding 2) | medium | T17 |
| Crossbow two-phase load/fire + Loaded flag | `CrossBow:109-160` | n/a | `CrossbowItem:57-117` | have | Y (PR #406) | low | — |
| Crossbow ammo = bolts | `CrossBow:170-182` | n/a | vanilla arrows | deviates | Y (SCOPE:97, bolts cut) | low | — |
| Bows are LAUNCHER category only | `BowCore:64`, `Category:25` | n/a | `ToolConstants:65-66`, weapon=false | have | — | none | — |
| Tooltip/station launcher lines (drawspeed, range, bonus damage) | `TooltipBuilder:142-160` | n/a | `ToolTooltip:288`, `StationText:133` | have | Y (PR #412/#426) | none | — |
| Haste raises draw speed + bonus-speed line | `ModHaste:41-119` | n/a | `ForgeweaveModifiers:151`, `StationText:162` | have | Y (PR #410/#426) | none | — |
| Lightweight +10% draw speed | `TraitLightweight:40-44` (per part) | n/a | counted once per tool | have | Y (PR #410 dev. 4) | low | — |
| Luck / Fortify refused on launchers | `ModLuck:35`, `ModFortify:32` | n/a | `Modifier:86`, `Fortification:231` (with message) | have | Y (PR #410) | none | — |
| Hit-effect traits/modifiers on fired arrows | melee/ammo-side only | ProjectileHitModifierHook | resolve arrow → bow (`CombatSeams:197-231`) | deviates | Y (SCOPE:279, PR #410) | none | — |
| `preventSlowDown` while drawing (0.5 / 0.195 / vanilla) | `ShortBow:93-97` etc. | n/a | `BowItem:109`, `BowDrawMovement` (+#421 diagonal fix) | have | Y (PR #413/#421) | none | — |
| Per-stage draw art + modifier overlays | `.tcon.json` overrides + textures | n/a | `ToolArt:198-263`, derived draw textures | have | Y (PR #413) | none | — |
| Nocked ammo rendered on drawn bow / loaded crossbow | `BakedBowModel:55-77`, `getAmmoToRender` | baked into sprites | absent, unrecorded | missing | N | medium | T52 |
| Custom crosshairs (SQUARE / T, charge spread) | `Crosshairs`, `CrosshairRenderEvents` | n/a | `BowCrosshair` + derived `square.png`/`t.png` | have | Y (#484) | none | — |
| Third-person crossbow arm pose | `RangedRenderEvents:18-49` BOW_AND_ARROW | ModifiableCrossbowClientExtension | vanilla CROSSBOW_CHARGE/HOLD | have | Y (PR #427) | low | — |
| Tool Station slot layout for bows | `RangedClientProxy:43-67` | n/a | `ToolStationTabs:201-209` | have | — | none | — |
| Station preview tints for bow parts | `BowCore:304-313` | n/a | per-role colours (limb2/slot2 differ) | partial | Y in part (PR #405/#406) | low | — |
| Repair with either limb | `ShortBow:46-48` {0,1} | n/a | limb 0 only | partial | Y (PR #405) | low | T68 |
| Per-material limb/grip sprite variants | `_cactus/_contrast/_paper` textures | n/a | one greyscale sprite tinted | missing | Y (PR #413) | low | — |
| OnBowShoot multishot / inaccuracy hook | `TinkerToolEvent:138-158` | n/a | one arrow per shot | missing | Y (PR #405) | low | T17 |
| Re-equip animation after a shot | `BowCore:183-184` | n/a | none (matches vanilla 1.21 feel) | partial | Y (PR #417) | low | — |
| Bow item descriptions | `en_us.lang:826-829` | n/a | shortbow line rewritten on a now-stale reason | partial | N | low | T77 |
| Guide book bow pages + Bow Materials section | `book/en_us/tools/*`, `index.json:33-37` | n/a | bows in tool list; stats on material pages | partial | Y (PR #412) | low | T48 |
| JEI bow assembly, limb casting, part builder entries | `TinkerRangedWeapons:117-128` | n/a | assembly/casting/part categories | have | Y (PR #412) | none | — |
| Creative-tab default bow (assembled) | `ShortBow:53` etc. | n/a | componentless | partial | Y (M1 convention) | low | T76 |
| Ranged content-family config toggle | forced pulse | n/a | `ForgeweaveConfig:95` | forgeweave-only | Y (#398) | none | — |
| Material Arrow tool | `Arrow.java:28-73` | n/a | absent | missing | Y (SCOPE M3.5) | high | T17 |
| Arrow head / shaft / fletching parts + SHAFT/FLETCHING stats | `TinkerTools:213-215`, stat classes | n/a | absent | missing | Y (SCOPE M3.5) | high | T17 |
| Shuriken | `Shuriken.java:27-85` | n/a | absent | missing | Y (SCOPE M3.5) | high | T17 |
| Bolt + bolt core + bolt casting | `Bolt.java`, `BoltCore.java` | n/a | absent | missing | Y (bolts cut) | low | — |
| ProjectileCore ammo abstraction (ammo as durability, reload) | `ProjectileCore:39-171` | n/a | absent | missing | Y (SCOPE M3.5) | high | T17 |
| EntityProjectileBase flight/impact model | `EntityProjectileBase:157-507` | n/a | vanilla `AbstractArrow` | missing | Y (SCOPE M3.5) | medium | T17 |
| Projectile traits (endspeed, hovering, breakable, splitting) + Fins | `TraitEndspeed` etc., `ModFins` | n/a | absent | missing | Y (SCOPE M3.5) | medium | T17 |
| Projectile damage plumbing (looting/beheading/enderference from ammo) | `ToolEvents:144-160`, `ModBeheading:69-84` | n/a | bow-side covered by the arrow→bow pipeline | partial | Y (PR #410) | low | T17 |
| Ammo priority + Infinity not honoured | `TinkerRangedWeapons:140-153` | n/a | same behaviour | have | Y (PR #405) | none | — |
| Save-compat fixtures (launcher stats, loaded crossbow) | n/a | n/a | `SaveCompatCorpusTest` fixtures | have | Y (SCOPE M3.5 gates) | none | — |
| Javelin / throwing axe / energy ranged tool | no 1.12 counterpart | n/a | absent | missing | Y (SCOPE backlog) | none | — |

### 3.10 Config, compat & misc

11 of ~30 upstream config options were ported in PR #362 with careful SERVER/CLIENT bucketing and hot reload; three of the triaged-out ones now matter (`spawnWithBook`, `craftCastableMaterials`, `chestsKeepInventory`/`addFlintRecipe` are behaviours, not dead switches). JEI exceeds 1.12; Waila/TOP are M8. The largest non-config finding is that 1.12 makes every dropped tool indestructible and Forgeweave does not.

| Feature | 1.12 | 1.20 | Forgeweave | Status | Deliberate? | Severity | Ticket |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `allowVanillaEnchanting` | no switch (enchantability 0, no books) | ModifiableItem overrides | flag exists but ON side offers nothing (no enchantment value / tags) | partial | Y for the flag, N for the dead ON side | medium | T54 |
| `reuseStencils` | `Config:129-132` | n/a | `ForgeweaveConfig:160-162` | have | Y (#276) | none | — |
| `oreToIngotRatio` | `Config:207-212` (also shifts temperature) | smeltery ore rate | scalar around baseline 2.0; no temperature shift | deviates | Y (PR #362) | low | T60 |
| `obsidianAlloy` | `Config:167-171` | n/a | `ForgeweaveConfig:167-169`, hot-reloadable | have | Y (#276) | none | — |
| `enableClayCasts` | `Config:139-143` | n/a | `:170-172` + `CastingRecipe` | have | Y (#292/#387) | none | — |
| Worldgen configs (genCobalt/rate, genArdite/rate) | `Config:260-277` | n/a | `:210-223` + `NetherOrePlacement` | have | Y (#276) | none | — |
| Client configs (extraTooltips, temperatureCelsius, listAllPartMaterials) | `Config:298-323` | n/a | `ForgeweaveClientConfig:44-54` | have | Y (#276) | none | — |
| `spawnWithBook` (first-login gift) | `Config:145-148` + `PlayerDataEvents` | n/a | absent | missing | N (deferral now unblocked) | medium | T13 |
| `chestsKeepInventory` | `Config:155-158` + `BlockToolTable:158` | n/a | `ForgeweaveConfig:chestsKeepInventory` (default on) | have | Y (#478) | none | — |
| `craftCastableMaterials` (metals cast-only by default) | `Material:178-179`, `Config:180-183` | casting only | `ForgeweaveConfig:craftCastableMaterials` (default off) + `cast_only` | have | Y (#435) | none | — |
| `addFlintRecipe` (+ allowBrickCasting, leather drying) | `Config:167-205` + `flint.json` | conditional recipe | no flint recipe, no options | missing | N | medium | T55 |
| `AutosmeltFortuneInteraction` | `Config:173-176` | n/a | 1.21 loot applies fortune first | deviates | Y (PR #362) | none | — |
| Module toggles | pulse config | n/a | content-family toggles (richer) | have | Y (#398/#399) | none | — |
| Remaining upstream options (listAllTables, orePreference, render…) | `Config:32-342` | n/a | none registered, each triaged | deviates | Y (PR #362) | low | T76 |
| Config sync + in-game config GUI | `ConfigSync`, `ConfigGui` | Forge sync | NeoForge sync; no `IConfigScreenFactory` | partial | N | low | T80 |
| Commands (debug + client helpers) | `TinkerDebug` (@Pulse off by default) | `TConstructCommand` | none | missing | Y (PR #362) | low | — |
| JEI plugin (subtypes, categories, catalysts, transfer) | `JEIPlugin:76-192` | richer | `ForgeweaveJeiPlugin` (exceeds 1.12) | have | Y (SCOPE M1/M2/M3) | none | — |
| Waila / The One Probe providers | `WailaRegistrar`, `theoneprobe/*` | external | none | missing | Y (SCOPE M8) | low | — |
| Chisel / C&B / CraftingTweaks / Quark IMC | `plugin/*.java` | craftingtweaks only | none | missing | N (M8 umbrella only) | low | — |
| Ore-dict → tag integration for FW items | `TinkerOredict` | `c:` tags | metal `c:` tags only; no glass/brick/cast/pattern tags | partial | N | low | T79 |
| Tool-class exposure (vanilla tool tags, ItemAbilities) | Forge tool classes | 1.20 tool tags + actions | no tool tags; only mattock overrides `canPerformAction` | missing | N | medium | T33 |
| `c:` tag melting/casting of any mod's metals | ore-dict integration + IMC | JSON recipes | 76 tag-keyed melting recipes | have | Y (SCOPE M2/M3.2, ADR-0002) | none | — |
| Mod metadata (authors, logo, URL) + vanilla-server join | `mcmod.info`, `NetworkCheckHandler` | mods.toml | minimal mods.toml; payload not optional | partial | N | low | T80 |
| Vanilla achievement grants | `AchievementEvents:27-60` | own tree | own chain only | partial | N | low | T49 |
| Potion-effect trait plumbing | `TinkerPotion` effects | TinkerEffect | 4 effects; momentum/insatiable on components | deviates | Y (PR #242) | low | — |
| Indestructible dropped item entity (all tools) | `IndestructibleEntityItem`, `TinkersItem:98-115` | modifier-gated | only netherite `fireproof` | missing | N | **high** | T16 |
| Gadget entities (fancy frames, throwballs, EFLN) | `gadgets/entity/*` | kept | absent | missing | N (unplanned) | medium | T56 |
| Projectile entities | `EntityProjectileBase` + 3 | n/a | vanilla arrows | missing | Y (SCOPE M3.5) | low | T17 |
| Modifier ↔ enchantment conflict refusals | `Modifier:70-116` | ModifierRequirements | none | missing | N | medium | T23 |
| Anvil books refused; COMMON rarity for modifier enchants | `TinkersItem:483-491` | overrides | no `isBookEnchantable`/rarity override; warmace takes books | partial | N | low | T81 |
| Keybindings | none | none | none | have | — | none | — |
| CraftTweaker / scripting integration | external + IMC | external | datapack registries (ADR-0002/0004) | have | Y (SCOPE non-goal) | none | — |
| Creative tabs | six tabs | several | one | deviates | N | low | T76 |
| Misc TinkerCommons content (mud bricks, lavawood, soils, slime) | `TinkerCommons` | mostly kept | partial roster; soils tracked on #429 | partial | Y (SCOPE M3.2 / #429) | low | T59 |
| Ponder soft dependency | n/a | n/a | `ponder/*` | forgeweave-only | Y (SCOPE M2) | none | — |
| DarkModeEverywhere IMC | n/a | n/a | `ForgeweaveDarkModeCompat` | forgeweave-only | Y (#335) | none | — |
| Test/hygiene gaps (enchant-offer test, config-gated fixtures, metadata) | n/a | n/a | see PR #362 "honest limits" | partial | N | low | T80 |

## 4. Checklist for issues

Prioritized and deduplicated across domains. Already-filed issues are marked; **#428 is closed** (page overflow, merged as `53df05c`), **#429** (consecrated soil / necrotic bone) and **#430** (book engine) are open and absorb several rows below.

### Blockers

- [ ] **T1 — Harvest-level tag ladder is one tier too generous for every material** — re-map 33 material JSONs (STONE→wooden, IRON→stone, DIAMOND→iron, OBSIDIAN→diamond, COBALT→netherite), update `ToolBehaviorGameTests`/`MetalMaterialTest`, and decide cobalt/ardite ore gating alongside (materials, blocker, playtest-fix round before next tag).
- [ ] **T2 — Tool Station ignores repair items and reagents in free slots 3–5** — pool all five free slots in `resolveRepair` and generalise `ModifierApplication.resolve` beyond two stacks, per #340's original scope (stations/modifiers, blocker, M3.5 fix round).

### High

- [x] **T3 — Decide `craftCastableMaterials`: metals are Part-Builder craftable** (shipped, #435) — the flag landed with 1.12's default: `material` JSON gains `cast_only` (upstream's `castable && !craftable`) and `ForgeweaveConfig.craftCastableMaterials` (default off) turns the crafting items back on. Knightslime and obsidian stayed craftable (they set both upstream flags), as did the four tag-gated metals (no Forgeweave casting path at all) (materials/config, high, M3.x fix round).
- [ ] **T4 — Sharp's bleed knocks the target back every 15 ticks** — give the DoT a `forgeweave:bleed` damage type in `no_knockback` (+ `bypasses_armor` if the SCOPE deviation stays); add the blood-heart particle (traits, high, M3.5 fix round).
- [x] **T5 — Station swords/pan/sign/dagger/warmace mine like axes** (shipped, #437) — swap `mineable/axe` for the sword-efficient set, drop the mining-speed modifier to 0.5, consider the cobweb multiplier and creative block-break refusal (tools, high, M3 playtest-fix).
- [x] **T6 — Port the Width++/Height++ expander modifiers** (shipped, #438) — `expander_w`/`expander_h` items plus +1 AOE axis on harvest and large tools (modifiers, high, M3.5 playtest-fix).
- [ ] **T7 — Melt vanilla metal-crafted items** — recipe-derived (or generated) melting rows for iron/gold tools, armor, buckets, rails, horse armor, minecarts, with an ignore list (smeltery, high, M6/backlog).
- [ ] **T8 — Restore the stone → molten seared stone chain** — melting rows for stone/cobblestone (72 mB), grout (24) and stone tool parts (smeltery, high, backlog).
- [ ] **T9 — Port seared channels** — connection states, side/down flow, redstone gate, renderer, 5-brick recipe (smeltery, high, M6/backlog).
- [ ] **T10 — Port the seared furnace multiblock** — controller, structure rules (frame + tanks + stairs/slab ceiling), GUI, JEI catalyst, recipes; unblocks #369 and the book section (smeltery, high, M6/backlog).
- [ ] **T11 — Tool Station rename** — rename an existing tool with no other inputs, and sync the typed name across players at the same station (stations, high, M3.5 fix round).
- [ ] **T12 — Part Builder destroys shard change** — block the output when the change slot holds a non-stacking stack instead of dropping the leftover (stations, high, M3.5 fix round).
- [ ] **T13 — Guide book first-join gift + `spawnWithBook`** — SERVER option default true and a once-per-player `PlayerLoggedInEvent` grant; the #276 deferral is unblocked now that #273 shipped (book/config, high, M3.5).
- [ ] **T14 — Book engine 1:1 port** — Mantle 1.12 `GuiBook` + element/content system, derived chrome art with NOTICE rows, window-scaled spread, book font; drop the authored `book.png` (book, high, **#430**).
- [ ] **T15 — Material-prefixed display names** — `getName` on `ToolItem` (hyphenated for multi-material) and `PartItem`, with `material.forgeweave.<id>.prefix` keys and CUSTOM_NAME precedence (book/tools, high, M3.5).
- [ ] **T16 — Dropped tools and parts are destructible** — register an indestructible item entity (fire/lava/explosion-immune, no despawn) via `hasCustomEntity`, and redefine or retire the netherite `fireproof` trait (config/tools, high, M3.5 fix round).
- [ ] **T17 — Material arrows, shuriken and the ProjectileCore layer** — arrow/shaft/fletching parts, SHAFT/FLETCHING/PROJECTILE stats, ammo-as-durability, own projectile entities, ammo-side traits (splitting/hovering/breakable/freezing/endspeed) and Fins, launcher base/modifier damage constants, OnBowShoot multishot (ranged/tools/traits/modifiers, high, M3.6 candidate).
- [ ] **T18 — Slime islands** — structure/feature port of the island generator with lakes, trees and plants plus its configs (world, high, world-content milestone).
- [ ] **T19 — Magma slime islands** — Nether island feature plus the magma-cube spawn override (world, high, world-content milestone).
- [ ] **T20 — Blue slime entity** — entity type, tinted renderer, loot table and island spawn placement (world, high, world-content milestone).
- [ ] **T21 — Slime boots** — bounce handler and fall-damage cancel (world, high, M5 / #26).
- [ ] **T22 — Slime sling** — charged launch item with velocity packet (world, high, M5 / #26).

### Medium

- [ ] **T23 — Modifier/trait/enchantment incompatibility layer** — refusal hook covering silky↔luck, luck↔Silk Touch, squeaky↔silky/luck, autosmelt↔silk touch, embossing donor compat, with translatable errors (modifiers/config, medium, M3.5 playtest-fix).
- [ ] **T24 — Port the Blasting modifier** — 3 TNT, harvest-only, non-effective blocks minable with a drop-destroy chance, overlay art, JEI entry (modifiers, medium, M6).
- [x] **T25 — Port the Glowing modifier** (shipped, #456) — `ForgeweaveModifiers#GLOWING` plus the `glowingTick` half of `inventoryTick`: held, below light 8, the first of the holder's seven candidate positions that will take one gets a `minecraft:light`, for one durability. Two recorded deviations: the reagent is the ender eye alone (upstream's three-item `ItemCombination` -- two glowstone dust and an eye -- has no form in `ModifierRecipe`, the same reduction `beheading.json` already made), and vanilla's light block replaces upstream's own `BlockGlow`, so a placed light neither needs a solid face to cling to nor vanishes when one goes away.
- [x] **T26 — Extra-info tooltip lines for modifiers and traits** (shipped, #457) — `ForgeweaveModifiers#extraInfo` ports the seven modifier `getExtraInfo` implementations (smite/bane, fiery's two lines, necrotic, reinforced with its "Unbreakable" substitution, shulking, mending moss) alongside haste's existing one, `ForgeweaveTraits#extraInfo` the eight trait ones, `ModifierApplication#displayName` the `modifier.<id>.nameN` ladder plus reinforced's max-level rename, and `ForgeweaveModifiers#color` the per-modifier colours `ModifierNBT#getColorString` prefixes every modifier line with. Lines ride the modifier/trait row's hover rather than being panel rows of their own (issue #424's recorded deviation, unchanged).
- [x] **T27 — Autosmelt/searing drops no furnace XP or flame particles** (shipped, #458) — `ForgeweaveModifiers#smelt` (the shared Searing/autosmelt path, issue #228) now gates on `isCorrectToolForDrops` (the `isToolEffective2` approximation), refuses to fire alongside Silk Touch/squeaky, drops the smelted result's furnace XP with the upstream probabilistic round-up, and spawns 3 FLAME particles per effective break (traits, medium, M3.5 fix round).
- [ ] **T28 — Magnetic pulls constantly** — gate the pull on a 30-tick after-use window (marker effect or timer component) instead of always-on while carried (traits, medium, M3.5 fix round).
- [ ] **T29 — Blocking definition for defensive traits** — treat a raised vanilla shield (and battlesign use) as blocking, iterate both hands, and stop counting longsword charging as a block (traits, medium, M3.5 fix round).
- [ ] **T30 — Repair accepts only the plain repair item** — accept any of the material's crafting items (logs, blocks, shards, nuggets) scaled by value (materials/stations, medium, M6).
- [ ] **T31 — Multi-part repair and per-part repair modifiers** — repair by any `getRepairParts` slot with the per-tool factors (Hammer 2.5×, Cleaver 2×…) and the multi-material bonus (tools, medium, M3 playtest-fix).
- [x] **T32 — Sharpening kit repair** — accept a kit of the head material at the station and port the crafting-grid `RepairRecipe` (stations/tools, medium, M3.5 fix round).
- [ ] **T33 — Tool tags, ItemAbilities and grass paths** — tag tools into `minecraft:pickaxes/axes/shovels/hoes/swords` + `c:tools/*`, implement `canPerformAction` per tool kind, and give shovel/excavator `SHOVEL_FLATTEN` (tools/config, medium, M3 playtest-fix).
- [x] **T34 — Per-tool knockback multipliers** (shipped, #553) — hatchet 1.3, mattock 1.1, lumberaxe 1.5 and rapier 0.6 (`ToolCore#knockback()`), each as a `KnockbackMultiplierSeam` riding a fourth `CombatSeam#knockback` hook on NeoForge's own `LivingKnockBackEvent` rather than a custom pipeline (ADR-0005). Corrects the audit's own citation: the multiplier scales the flat per-hit knockback vanilla's `LivingEntity#hurt` already applies to every landed hit, not a separate attack-knockback-attribute push, and a seam's own knockback (`KnockbackOnHitSeam`, the frying pan's `HeavyKnockback`) is guarded against being double-scaled (tools, medium, M3 playtest-fix).
- [x] **T35 — Hammer's +3..6 vs undead** (shipped, #466) — a `ConditionalSeam(UNDEAD, ...)`/`RandomBonusDamage` seam alongside the concussion rider, composed into the hammer's one `Innate` (tools, medium, M3 playtest-fix).
- [ ] **T36 — Scythe cannot shear entities** — generalise `KamaItem#interactLivingEntity` to a 3×3 area around the clicked entity (tools, medium, M3 playtest-fix).
- [ ] **T37 — Longsword/frypan charge movement** — generalise `BowDrawMovement` to a per-use-action speed (longsword 0.9, frypan 0.7) (tools, medium, M3 playtest-fix).
- [ ] **T38 — Seared brick/block/cobble/glass casting** — the casting side of the seared-stone chain (recorded as a PR #246 follow-up) (smeltery, medium, backlog).
- [x] **T39 — Smeltery core has no item handler** (shipped, #470) — the core itself now registers an item-handler capability re-exposing `meltingContainer()`, so a hopper on any face of the core feeds it directly with no chute required, matching upstream's `TileMultiblock extends TileInventory` (Mantle, outside the pinned clone).
- [ ] **T40 — Shard cast + shard melting/casting** — gold and clay shard casts at 72 mB per metal (smeltery, medium, backlog).
- [ ] **T41 — Emerald melting and block casting** — ore/gem/block melting rows plus the emerald block basin recipe (smeltery, medium, backlog).
- [ ] **T42 — Molten glass** — fluid, sand/glass/pane melting, clear glass and pane casting, seared glass basin recipe (smeltery, medium, backlog).
- [x] **T43 — Bucket-filling casting recipe** (shipped, #542) — one `casting_recipe/bucket_<fluid>.json` row per fluid this mod already makes bucketable (issue #286) rather than upstream's single fluid-agnostic Java capability match, consistent with every other casting recipe already being one datapack row per (station, cast, fluid); same player-facing result (smeltery, medium, M3.5 fix round).
- [ ] **T44 — Tinker tank multiblock** — controller, structure rules, capacity math, GUI, drain integration (smeltery, medium, M6/backlog).
- [ ] **T45 — Pattern/Part Chest GUI** — derive upstream's `blank.png` scaling-chest art with a slider instead of vanilla `generic_54` paging (stations, medium, polish).
- [ ] **T46 — Pattern Chest rules** — one-of-each patterns, stack size 1, cast-chest mode now that casts ship; same-stack-only rule for the Part Chest (stations, medium, M3.5 fix round).
- [x] **T47 — Chests keep their inventory when broken** (shipped, #478) — both chests carry their contents on the dropped item as the vanilla `minecraft:container` component (upstream writes its own `inventory` NBT compound instead), copied off the block entity by the loot table and read back on placement; `ForgeweaveConfig.chestsKeepInventory` (default on, upstream's) gates it and the old spill is what the off branch still does. A creative break hands the packed chest over the way vanilla's shulker box does, rather than reproducing upstream's silent loss there (stations/config, medium, M3.5 fix round).
- [ ] **T48 — Book content parity pass** — per-section listing pages, material icon-grid overview, full material/tool/modifier page layouts, hover tooltips and links, fortify variant, bow-materials section, cover text, resource-pack JSON structure (book, medium, **#430**).
- [ ] **T49 — Grant vanilla `story/upgrade_tools` and `story/iron_tools`** — awarded on station assembly by head tier; root and shoot_arrow already fire from vanilla (book/config, medium, backlog).
- [ ] **T50 — Craft and hit sounds** — saw at the Tool Station, anvil at the Forge, frypan boing; decide CC-BY/CC0 sound derivation (attribution file, Spartan precedent) vs vanilla stand-ins (book/tools, medium, M3.5 playtest-fix).
- [ ] **T51 — Attack-slash and heart-effect particles** — derive the slash/particle sheets and hook fiery/rapier/jagged/prickly/spiky plus per-weapon full-charge bursts onto the combat seams (book/tools/traits, medium, backlog).
- [ ] **T52 — Nocked arrow not rendered** — draw the found ammo on the bow (and only when loaded for the crossbow) at upstream's ammoPosition (ranged, medium, M3.5-8 or M3.6).
- [x] **T53 — 1.12 draw crosshairs** (shipped, #484) — SQUARE for bows, T for the crossbow, spread by draw charge, replacing the vanilla crosshair (ranged, medium, M3.5-8).
- [ ] **T54 — `allowVanillaEnchanting=true` still offers nothing** — give tools an enchantment value and add them to `minecraft:enchantable/*` when the flag is on, or document the flag as anvil-only; add an offer GameTest (config, medium, M3.4 follow-up).
- [ ] **T55 — 3 gravel → flint recipe** — behind `addFlintRecipe` (upstream default on) (config, medium, M3.4 follow-up).
- [ ] **T56 — M5 planning: the unplanned gadget roster** — decide piggyback pack, punji sticks, wooden hopper, item/drying racks (+drying recipe type, jerky foods, JEI category), glow ball, EFLN, fancy frames, wooden rails, stone torch/ladder, dried clay and brownstone families, spaghetti, slime channels (world/config, medium, M5).
- [ ] **T57 — World-content content set** — slime dirt/grass/leaves/sapling/tree/vines, congealed and coloured slime blocks, coloured slime balls, blue/purple slime fluids, blood slime ball; then revert the knightslime alloy, magma mud and blue-crystal substitutions and add slime-vine bowstrings (world, medium, world-content milestone).
- [ ] **T58 — Crafting-value unit too coarse** — nuggets, fragments and bonemeal cannot be Part Builder inputs; needs a finer unit (nugget-units) (materials, medium, M6).
- [ ] **T59 — Reagent parity** — lapis block ×9 for luck, quartz block ×4 for sharpness, sticky piston for knockback, expander items, and consecrated/graveyard soil + necrotic bone (**#429** open) (modifiers, medium, M3.5 playtest-fix).

### Low

- [ ] **T60 — Ore-class melting temperature** — derive from the doubled ore amount like `calcTemperature`; a datapack `temperature` field already exists (smeltery, low, backlog).
- [ ] **T61 — Cobalt/ardite/manyullyn/rose gold blocks missing from `crafting_items`** — add value-18 rows (moot if T3 makes metals cast-only) (materials, low).
- [ ] **T62 — Split `cheap` into upstream's cheap (general) + cheapskate (head)** so a stone-head tool stops getting the repair bonus (traits, low).
- [x] **T63 — Established block-break XP** (shipped, issue #494) — a flat 33% roll of +1 block-break XP via `BlockDropsEvent`, riding the same seam `ForgeweaveModifiers#onBlockDrops` already uses for Searing/Magnetic Pull/Resonant/autosmelt (issue #108). Corrects the ticket's own "33%/3%" framing and this doc's earlier claim (row above) that NeoForge's block-break event has no XP field: `BlockEvent.BreakEvent` doesn't, but `BlockDropsEvent` does, and upstream's own roll (`r < 0.33f || (expToDrop == 0 && r < 0.03f)`) is a flat 33% regardless of xp once you notice 0.03 &lt; 0.33 makes the second clause dead (traits, low).
- [ ] **T64 — Squeaky: hit sound stand-in and luck/silky refusal** (traits, low; refusal folds into T23).
- [ ] **T65 — Hatchet: +0.5 flat attack and free full-speed leaf digging** (tools, low, M3 playtest-fix).
- [ ] **T66 — Mattock: repair with either head and per-family tier** (tools, low, M3 playtest-fix).
- [ ] **T67 — Cleaver should swallow right-click** (no off-hand use) (tools, low, backlog).
- [ ] **T68 — Bows repair with either limb** (`getRepairParts {0,1}`) (ranged, low, M3.5-8).
- [ ] **T69 — Reinforced plate center should be a gold cast now the smeltery ships**, plus the level-5 "Unbreakable" name (modifiers, low, M3.5 playtest-fix).
- [ ] **T70 — Fortify: gate to harvest tools and derive the per-tool material-tinted overlay** (modifiers, low, M3.5 playtest-fix).
- [ ] **T71 — Remaining melting/casting content** — terracotta/brick casting, molten dirt + mud bricks, ice/snow → water, red sand from blood (smeltery, low, backlog).
- [x] **T72 — Bulk grout recipe** (shipped, #503) (clay block + 4 sand + 4 gravel → 8) (smeltery, low, backlog).
- [ ] **T73 — Ambient particles** — active smeltery controller flame/smoke, casting-table cooling smoke, autosmelt flames (smeltery/book, low, backlog).
- [ ] **T74 — Crafting Station side inventory** — skip station-group blocks and add a `craftingStationBlacklist` config (stations, low, M8).
- [ ] **T75 — Station polish** — right-click insert into chests, creative-tab table variants behind `listAllTables`, table-top item rendering (stations, low, polish).
- [ ] **T76 — Creative tab shape** — split into General/Tools/Parts/Smeltery tabs and optionally list per-material assembled tools (`listAllToolMaterials`) (book/config/tools, low, M6).
- [x] **T77 — Doc corrections** (shipped, #508) — SCOPE M3.5's arrow-pickup line rewritten to describe the actual `PickupStatus` rule (never globally disabled); its "~15 traits with an `ILauncher`/projectile branch" sentence corrected — upstream implements `IProjectileTrait` on exactly two traits (Endspeed, Hovering; movement, not hit effects), and hit-effect modifiers resolve ammo-side upstream vs. bow-side in Forgeweave (`CombatSeams`, PR #410), not via any trait branch; shortbow's book/tooltip description restored to upstream's "allows for fast movements while shooting" sentence now that `preventSlowDown` is ported (`BowItem`, `BowDrawMovement`, PR #413/#421); guide book's scythe page moved back to right after kama, matching `tools.json:21-124`'s harvest order (docs, low, M3.5-8).
- [ ] **T78 — Nether ore vein height distribution** — half the veins in y32–95 as upstream (world, low, backlog).
- [ ] **T79 — `c:` convention tags** — clear glass (+dyed), seared brick, cast/pattern/part families (config, low, M6/M8).
- [ ] **T80 — Packaging hygiene** — mods.toml authors/logo/URL, register NeoForge's `ConfigurationScreen`, decide the vanilla-server join policy, add the enchant-offer GameTest and a config-gated save fixture (config, low, release polish).
- [ ] **T81 — Tooltip/rarity leftovers** — part `missing_stats` line, `isBookEnchantable=false` unless the flag is on, force COMMON rarity so modifier-granted enchantments don't recolour names (book/config, low, M3.4 polish).

## 5. Deliberate deviations to reconfirm

Recorded decisions that this audit re-read and considers worth a maintainer glance, because the reason given no longer holds or the consequence is larger than the record suggests.

- **Magnetic always-on pull** (PR #119): justified by "no potion-effect plumbing", but `ForgeweaveMobEffects` now exists — see T28.
- **Autosmelt follows Searing exactly** (PR #242): still true and no longer a gap -- see T27 (#458), which gave the shared path the furnace XP, particles, effectiveness gate and Silk Touch exclusion both features were missing.
- **Hit-effect traits/modifiers ride the vanilla arrow** (SCOPE M3.5, PR #410): a documented deviation from 1.12's ammo-side model, with a one-line revert flag; SCOPE's supporting sentence about launcher branches is factually wrong (T77).
- **Lightweight counted once per tool** rather than compounding per limb (PR #410) — flagged as an open question at the time.
- **Tier from the highest head material** (#294) for hammer/mattock/battleaxe: upstream keys the hammer off its hammer head only, and #294's premise missed that override.
- **Extra-slot item is survival-craftable with cap 5** (#107, SCOPE M2): upstream has no survival extra-slot item at all.
- **Tool Forge 5% repair discount** (SCOPE:103) — Forgeweave-only economy change.
- **Beheading reagent collapsed to obsidian** and no `alreadyContainsDrop` de-dup (PR #197): a wither skeleton can drop two skulls.
- **Mending Moss heals from any inventory slot on a probabilistic timer** (#107 ponytail note, explicitly left for maintainer review).
- **Any adjacent inventory becomes a station side inventory** (SCOPE:36): a Pattern/Part Chest beside a Crafting Station is silently adopted, which upstream refuses.
- **Chest GUI uses vanilla `generic_54` with paging** (ChestScreen javadoc only; #66 asked for derived GUIs) — see T45.
- **Book engine authored rather than ported** (PR #360) — already superseded by the #430 directive; listed here because the PR text still reads as the standing decision.
- **Enderference marks every target** (PR #243): players cannot chorus-teleport for 5 s after being hit; upstream only marks endermen.
- **Green molten slime substitutes purple slime** for the knightslime alloy (#232) — revert path in T57.
- **Ore yield via tiered cores (1.5× / 2×)** instead of the flat 2× ratio (SCOPE M2 / #99) — progression-shaping and worth a playtest confirmation alongside T1.

## 6. Forgeweave-only additions

All 38 are recorded decisions; grouped for review, not because any is in question.

- **Materials (6):** rose gold, netherite, amethyst bronze, nahuatl, chorus, ancient (#103, SCOPE M3.2, PR #250).
- **Traits (3):** lacerating (#159), vintage (#230), quick / fireproof / reinforced core (#103).
- **Modifiers (7):** searing, magnetic pull, aquadynamic, resonant, far reach (#108), wind burst (#223), netherite bonus slot + netherite extra-slot recipe (PR #135).
- **Tools (8):** battleaxe, dagger, vein hammer, katana, scimitar, warmace (SCOPE M3 / #153–#161), broadsword parry (#155), the combat innate set (pierce/heft/reap/concussion/timber/crushing blow, #164).
- **Smeltery (5):** tiered Standard/Nether cores (#99), FW alloys (rose gold, netherite, steel, amethyst bronze), FW entity melting (blaze, warden, #270), FW fluids (rose gold, netherite, scrap, carbon, amethyst, amethyst bronze, blazing blood, deep blood), seared duct + chute (#277).
- **Stations (2):** DarkModeEverywhere IMC blacklist (#335), the six JEI station categories with `[+]` transfer (1.12 shipped none; SCOPE:330).
- **Book / UX (2):** the Forgeweave advancement chain (#110), Ponder scenes plus the first-use chat hint (SCOPE M2).
- **World / config (3):** blazing and deep blood as world-content fluids (#270/#181), the `content.gadgets` toggle ahead of any gadget (#398), the ranged content-family toggle (#398).
- **Ranged (1):** save-compat fixtures and per-family gating around the launcher stack (SCOPE M3.5 CI gates).
- **Cross-cutting:** per-family content toggles (#398/#399) are richer than upstream's pulse config; datapack registries for materials/modifiers/casting (ADR-0002/0004) replace upstream's IMC + addon API, which SCOPE lists as a non-goal.
