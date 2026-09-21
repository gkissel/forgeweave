# Trait pairing: one side that hits, one side that holds

> **Superseded in part, 2026-09-21 (issue #1103).** The rule below still holds: every material has
> to carry something that fires on a tool and something that fires on a worn piece, and
> `TraitReachabilityTest` still enforces it. What no longer holds is the id-level detail. #1103
> withdrew #876's "no two materials may name one trait id" rule, so the per-material companion ids
> this document pairs up were merged into shared, leveled families -- `ironwood_grip`, `prismward`
> and `rubberize` are all Heft I now, and the eight Mystical Agriculture wards are Magic Protection
> I-III. Read the pairs here as pairs of *mechanics*; for the id a material names today, read its
> material JSON, and for which ids a retired one became, read `TraitFamilies`.

Issue #1093. Every material carries a trait that does something on a tool or weapon **and** a trait
that does something on armor, and the two belong together: the same idea seen from the attacking
side and from the defending side.

The input is `build/trait-audit/materials.md`, written by `TraitReachabilityTest` (issue #1092): per
material, which sides it builds and which of its traits reach which side. Run
`./gradlew test --tests '*TraitReachabilityTest'` to regenerate it.

## The rule

For every material, for each side it can build:

- **Tool side** (head, handle, extra, bow or an ammo stat): at least one carried trait with a hook
  that fires on a held tool or weapon.
- **Armor side** (plating or maille): at least one carried trait with a hook that fires on a worn
  piece.

`TraitReachabilityTest#everySideAMaterialBuildsHasATraitThatWorksThere` fails the build on a
material that breaks it.

## Where the companions come from

Two things happened before any material was touched.

1. **`knockbackResistance` grew a worn half.** `ForgeweaveTraits#armorAttributes` now pays a worn
   piece a quarter of the trait's held value, so a full four-piece set reaches the held figure and
   no further. That is the whole pair for nine materials whose one idea is weight: their held trait
   plants the wielder and the same trait now steadies the wearer. #1091 had rejected paying the flat
   value per piece, because four pieces of the empowered emeradic crystal would have summed past
   total immunity; a quarter each avoids that.
2. **`forgeweave:protection` became a datapack behavior.** `Protection` is the class the six
   hardcoded `*_protection` traits ride. A `trait_definition` can now name a damage-type tag and an
   amount, which is what the `magic_protection` and `tideward` companions below use.

Everything else reuses a behavior that already shipped.

## The two groups #1097 re-paired

Two companions had turned into catch-alls: `melee_protection` covered thirteen materials and
`magic_protection` covered all seventeen of batch 3. The rule is that the armor trait is the
material's own idea turned around, and "an edge that bites through armor blunts a direct blow when
worn" does not describe what `pristine`, `dominant`, `elektronbond`, `predatory`, `lacerating` or a
crit multiplier actually do.

The maintainer's test on 2026-09-20: judge each pair by reading the tool trait's code, not its name,
and write the one-sentence tie first. Where an honest sentence could not be written, the companion
was wrong and changed. `melee_protection` stayed on the four materials whose tool trait really is
about beating armor (`steel`, `tungsten`, `knightmetal`, `certus_quartz`); `magic_protection` is on
none of the seventeen any more. Three new `trait_definition` families came out of it -- the essence
ladder's seven rungs, the reactor metals' four radiation companions, and `temperward` -- and
everything else reuses a companion that already shipped. The changed rows are marked `#1097` in the
tables below.

## The pairs, by batch

### Batch 0: the two shared mechanics

| Material | Builds | Tool side | Armor side | What ties them | New or existing | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `amethyst` | tools and armor | `prismward` | `prismward` | one trait, both sides: a crystal ward softens a shove whichever way you carry it | existing, worn half new | own |
| `compressed_iron` | tools and armor | `compressed_iron_heft` | `compressed_iron_heft` | same | existing, worn half new | own |
| `emeradic_crystal` | tools and armor | `verdant_ward` | `verdant_ward` | same | existing, worn half new | own |
| `empowered_emeradic_crystal` | tools and armor | `empowered_emeradic_bulwark` | `empowered_emeradic_bulwark` | same | existing, worn half new | own |
| `end_steel` | tools and armor | `crystalline_ward` | `crystalline_ward` | same | existing, worn half new | own |
| `lead` | tools and armor | `poisonous`, `gravitic` | `gravitic` | leaden weight shoves and resists the shove | existing, worn half new | own |
| `osmiridium` | tools and armor | `ballast` | `ballast` | same | existing, worn half new | own |
| `osmium` | tools and armor | `leadfoot`, `heavy` | `heavy` | same | existing, worn half new | own |
| `pink_slime` | tools and armor | `rubberize` | `rubberize` | same | existing, worn half new | own |

### Batch 1: the armor side, 74 materials

Each of these builds both halves and carried only tool-side traits. The `Armor side` column is the companion added to its `traits.armor` list.

| Material | Builds | Tool side | Armor side | What ties them | New or existing | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `allthemodium` | tools and armor | `wellspring` | `depth_protection` | what it does underground, it also survives underground | existing | own |
| `aluminium` | tools and armor | `featherfall` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `alumite` | tools and armor | `skyborne` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `ardite` | tools and armor | `stonebound`, `petramor` | `depth_protection` | what it does underground, it also survives underground | existing | own |
| `black_quartz` | tools and armor | `unyielding` | `warded` | it hits hardest while the tool is whole; worn, a point comes off every blow while the wearer is whole | existing, #1097 | own |
| `certus_quartz` | tools and armor | `armor_breaker` | `melee_protection` | an edge made to beat armor, and plate made to blunt the blow that tries the same (kept by #1097) | existing | own |
| `cinderforge` | tools and armor | `magmaforge` | `fire_protection` | it works heat into stone, so heat does not work into the wearer | existing | 1.20 |
| `conductive_alloy` | tools and armor | `arcing` | `stormward` | it carries the lightning, so the lightning passes the wearer by | new | own |
| `cosmic_neutronium` | tools and armor | `colossal` | `deadweight` | the mass that shoves the target steadies the wearer | new | own |
| `crystal_matrix` | tools and armor | `vigorous` | `vigorward` | it strikes hardest while you are whole; worn, it keeps you whole | new | own |
| `cursium` | tools and armor | `cursium_blight` | `blightward` | it withers what it hits; worn, it withers whoever hits you | new | own |
| `dark_matter` | tools and armor | `shackling` | `voidward` | dark matter holds a target still; worn, it holds a blow out entirely | new | own |
| `dark_steel` | tools and armor | `predatory` | `battleworn` | it hits hardest at what is already worn down; worn, it protects hardest once it is worn down itself | existing, #1097 | own |
| `deathworm_chitin_red` | tools and armor | `deathworm_venom_red` | `venomward` | it poisons what it strikes; worn, it poisons whoever strikes you | new | own |
| `deathworm_chitin_white` | tools and armor | `deathworm_venom_white` | `venomward` | it poisons what it strikes; worn, it poisons whoever strikes you | new | own |
| `deathworm_chitin_yellow` | tools and armor | `deathworm_venom_yellow` | `venomward` | it poisons what it strikes; worn, it poisons whoever strikes you | new | own |
| `diamatine_crystal` | tools and armor | `radiant_edge`, `surging` | `surgeward` | a wound-up swing lands; worn, you get a beat longer before the next one can | new | own |
| `draconium` | tools and armor | `batteredge` | `surgeward` | a wound-up swing lands; worn, you get a beat longer before the next one can | new | own |
| `draconium_awakened` | tools and armor | `surging3` | `surgeward` | a wound-up swing lands; worn, you get a beat longer before the next one can | new | own |
| `dragon_bone` | tools and armor | `dragonbone_edge` | `surgeward` | it makes a critical blow land harder; worn, you get a beat longer before the next one can | existing, #1097 | own |
| `dragonsteel_lightning` | tools and armor | `dragonsteel_lightning_surge` | `stormward` | it carries the lightning, so the lightning passes the wearer by | new | own |
| `dragonyst` | tools and armor | `kinetic_charge` | `kinetic_reserve` | damage dealt becomes stored charge; worn, the charge is still there to spend | new | own |
| `dreadalloy` | tools and armor | `dreadgrip` | `blightward` | it withers what it hits; worn, it withers whoever hits you | new | own |
| `emerald` | tools and armor | `pristine` | `warded` | it hits hardest while the tool is whole; worn, a point comes off every blow while the wearer is whole | existing, #1097 | own |
| `empowered_diamatine_crystal` | tools and armor | `empowered_diamatine_prism` | `surgeward` | a wound-up swing lands; worn, you get a beat longer before the next one can | new | own |
| `empowered_enori_crystal` | tools and armor | `empowered_enori_radiance` | `revealward` | it lights up a target; worn, it lights up an attacker | new | own |
| `empowered_palis_crystal` | tools and armor | `empowered_palis_tempest` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `empowered_restonia_crystal` | tools and armor | `empowered_restonia_bloodsurge` | `bloodward` | it drinks blood from a target; worn, it closes the wearer's own wound | new | own |
| `empowered_void_crystal` | tools and armor | `empowered_void_maw` | `voidward` | a void-forged edge cuts through; worn, the void swallows the blow | new | own |
| `energetic_alloy` | tools and armor | `sparkforge` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `energised_steel` | tools and armor | `amberflow` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `enori_crystal` | tools and armor | `luminous` | `revealward` | it lights up a target; worn, it lights up an attacker | new | own |
| `faultsteel` | tools and armor | `cascading` | `depth_protection` | what it does underground, it also survives underground | existing | own |
| `hardcinder` | tools and armor | `leanharvest` | `depth_protection` | what it does underground, it also survives underground | existing | own |
| `hdpe` | tools and armor | `buoyant` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `hollowstone` | tools and armor | `hollowyield` | `depth_protection` | what it does underground, it also survives underground | existing | own |
| `infinity` | tools and armor | `escalating` | `surgeward` | a wound-up swing lands; worn, you get a beat longer before the next one can | new | own |
| `iridium` | tools and armor | `kinetic` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `ironbrand` | tools and armor | `gamedrop` | `bloodward` | the hunt feeds you: a kill drops meat, and a blow taken starts you mending | new | own |
| `knightmetal` | tools and armor | `knightmetal_breach` | `melee_protection` | an edge made to beat armor, and plate made to blunt the blow that tries the same (kept by #1097) | existing | own |
| `mendalloy` | tools and armor | `merciful` | `mendward` | a mercy that heals what it strikes heals the wearer harder too | new | own |
| `murkiron` | tools and armor | `harrying`, `blighted` | `blightward` | it withers what it hits; worn, it withers whoever hits you | new | own |
| `nahuatl` | tools and armor | `lacerating` | `venomward` | it opens a wound that keeps bleeding; worn, it poisons whoever opens one on you | existing, #1097 | own |
| `nickel` | tools and armor | `coilcharge` | `stormward` | it carries the lightning, so the lightning passes the wearer by | new | own |
| `nightshale` | tools and armor | `nocturnal_edge` | `duskward` | it strikes better after dark; worn, the dark hides you | new | own |
| `osgloglas` | tools and armor | `elektronbond` | `temperward` | a flat point onto every blow it lands, a flat point off every blow it stops | new, #1097 | own |
| `palis_crystal` | tools and armor | `stormglass` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `pulsating_alloy` | tools and armor | `brasswind` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `quakestone` | tools and armor | `quakecrumble` | `depth_protection` | what it does underground, it also survives underground | existing | own |
| `red_matter` | tools and armor | `voidrend`, `opportunist` | `voidward` | a void-forged edge cuts through; worn, the void swallows the blow | new | own |
| `refined_glowstone` | tools and armor | `revealing` | `revealward` | it lights up a target; worn, it lights up an attacker | new | own |
| `refined_obsidian` | tools and armor | `seismic` | `deadweight` | the mass that shoves the target steadies the wearer | new | own |
| `resonite` | tools and armor | `dominant` | `vigorward` | it hits hardest while it has the upper hand; worn, a blow taken gives absorption back so it keeps it | existing, #1097 | own |
| `restonia_crystal` | tools and armor | `bloodgem` | `bloodward` | it drinks blood from a target; worn, it closes the wearer's own wound | new | own |
| `riftalloy` | tools and armor | `riftstep` | `voidward` | a void-forged edge cuts through; worn, the void swallows the blow | new | own |
| `rose_gold` | tools and armor | `quick` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `shardline` | tools and armor | `keenedge` | `warded` | it hits hardest while the tool is whole; worn, a point comes off every blow while the wearer is whole | existing, #1097 | own |
| `skipalloy` | tools and armor | `quickstep` | `swiftward` | quick in the hand, quick on the feet | new | own |
| `soularium` | tools and armor | `leeching` | `bloodward` | it drinks blood from a target; worn, it closes the wearer's own wound | new | own |
| `starfall_stone` | tools and armor | `obliterate`, `swiftdig` | `depth_protection` | what it does underground, it also survives underground | existing | own |
| `steel` | tools and armor | `sharp`, `stiff` | `melee_protection` | a keen edge and a stiff guard; worn, the same steel blunts a direct blow (kept by #1097) | existing | own |
| `steeleaf` | tools and armor | `steeleaf_precision` | `surgeward` | it makes a critical blow land harder; worn, you get a beat longer before the next one can | existing, #1097 | own |
| `stormalloy` | tools and armor | `unraveling` | `unravelward` | it unravels what the target has; worn, it unravels the blow | new | own |
| `sunsteel` | tools and armor | `avalanche` | `deadweight` | the mass that shoves the target steadies the wearer | new | own |
| `tideiron` | tools and armor | `tidebreaker` | `tideward` | it clears the water ahead of you; worn, the water cannot drown you | new | own |
| `titanium` | tools and armor | `quartzheart` | `vigorward` | it strikes hardest while you are whole; worn, it keeps you whole | new | own |
| `truesteel` | tools and armor | `berserker_stance` | `bloodward` | it drinks blood from a target; worn, it closes the wearer's own wound | new | own |
| `tungsten` | tools and armor | `shattermail` | `melee_protection` | an edge made to beat armor, and plate made to blunt the blow that tries the same (kept by #1097) | existing | own |
| `unobtainium` | tools and armor | `unraveling3` | `unravelward` | it unravels what the target has; worn, it unravels the blow | new | own |
| `uranium` | tools and armor | `enfeebling` | `blightward` | it withers what it hits; worn, it withers whoever hits you | new | own |
| `vibranium` | tools and armor | `unraveling2` | `unravelward` | it unravels what the target has; worn, it unravels the blow | new | own |
| `vibrant_alloy` | tools and armor | `stormcaller` | `stormward` | it carries the lightning, so the lightning passes the wearer by | new | own |
| `void_crystal` | tools and armor | `voidtouched` | `voidward` | a void-forged edge cuts through; worn, the void swallows the blow | new | own |
| `voltcinder` | tools and armor | `overburdened` | `deadweight` | the mass that shoves the target steadies the wearer | new | own |

### Batch 2: the tool side, 11 materials

These carried only armor-side traits. `emberdrink` and `bracingplate` needed no companion at all: both already run on a held tool (`onDefend` and `inventoryTick` are reachable from both halves), and it was the `armor` list scope that stranded them, so the id moved to `general` instead. The `Armor side` column here names the trait that was already there.

**Corrected by #1097.** Five of these batch-2 materials kept their armor-only trait on the `general`
list, so it also landed on their tool parts and did nothing there -- the audit's decision 4.
`azure_electrum_swift`, `azure_silver_moonstep`, `ferricore_footing`, `gravitite_levity` and
`ironwood_footing` are on their materials' `armor` lists now. `ferricore_grip` and `ironwood_grip`
run on both sides, so they are on both lists, which is what the `Armor side` column below already
said.

| Material | Builds | Tool side | Armor side | What ties them | New or existing | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `azure_electrum` | tools and armor | `azure_electrum_rush` | `azure_electrum_swift` | swift worn, swift struck | new | own |
| `azure_silver` | tools and armor | `azure_silver_plunge` | `azure_silver_moonstep` | moonstep sends you up; the tool side is what comes down | new | own |
| `ferricore` | tools and armor | `ferricore_grip` | `ferricore_footing`, `ferricore_grip` | sure footing worn, sure footing held | new | own |
| `fluorite` | tools and armor | `fluorite_focus` | `fluorite_focus` | one buffer, spent before durability, on a tool or on a worn piece | new | own |
| `gravitite` | tools and armor | `gravitite_dive` | `gravitite_levity` | levity carries you fast; the tool side is what that speed is worth in a blow | new | own |
| `ironwood` | tools and armor | `ironwood_grip` | `ironwood_footing`, `ironwood_grip` | sure footing worn, sure footing held | new | own |
| `slimevine_blue` | tools and armor | `slimevine_snap` | `skyfall` | a living string mends itself whichever part it is | new | own |
| `unobtainium_allthemodium_alloy` | tools and armor | `emberdrink` | `emberdrink` | one trait, both sides: fire feeds the wearer and the wielder alike | existing | own |
| `unobtainium_vibranium_alloy` | tools and armor | `mendreach` | `mendbond` | healing you receive goes further; held, it takes healing from the target | new | own |
| `vibranium_allthemodium_alloy` | tools and armor | `bracingplate` | `bracingplate` | one trait, both sides: protection builds with every blow taken | existing | own |
| `vine` | tools and armor | `vine_weave` | `vine_weave` | one living weave, mending itself on a bowstring or on maille | new | own |

### Batch 3: the 17 materials that carried no trait at all

Mystical Agriculture's essence ladder, Extreme Reactors' four reactor metals, Applied Energistics'
three quartz metals and Occultism's iesnium shipped with an empty `traits` block. Each got a tool
trait of its own, and all 17 shared `magic_protection` on the armor side.

**Re-paired by #1097.** One companion for seventeen materials was the same shortcut
`melee_protection` was: "charged matter against a charged blow" is a sentence about magic damage,
not about what any of these materials actually does. Each family answers its own tool trait now, and
`magic_protection` is on none of them. The rows below are the current pairs.

| Material | Builds | Tool side | Armor side | What ties them | New or existing | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `inferium` | tools and armor | `inferium_edge` (+0.5 damage) | `inferium_ward` (0.75 magic protection) | one essence ladder, one rung per tier, climbing on both sides at once | both new | own |
| `prudentium` | tools and armor | `prudentium_edge` (+1.0) | `prudentium_ward` (1.25) | the ladder's second rung, blade and plate together | both new | own |
| `tertium` | tools and armor | `tertium_edge` (+1.5) | `tertium_ward` (1.75) | the ladder's third rung, blade and plate together | both new | own |
| `imperium` | tools and armor | `imperium_edge` (+2.0) | `imperium_ward` (2.25) | the ladder's fourth rung, blade and plate together | both new | own |
| `supremium` | tools and armor | `supremium_edge` (+2.5) | `supremium_ward` (2.75) | the ladder's fifth rung, blade and plate together | both new | own |
| `awakened_supremium` | tools and armor | `awakened_supremium_edge` (+3.0) | `awakened_supremium_ward` (3.25) | the ladder's sixth rung, blade and plate together | both new | own |
| `insanium` | tools and armor | `insanium_edge` (+3.5) | `insanium_ward` (3.75) | the ladder's top rung, blade and plate together | both new | own |
| `prosperity` | tools and armor | `prosperity_bloom` | `mendward` | it makes what it touches grow; worn, it makes healing the wearer receives go a quarter further | tool new, armor existing | own |
| `soulium` | tools and armor | `soulium_reap` | `bloodward` | it takes life from a target; worn, it closes the wearer's own wound | tool new, armor existing | own |
| `uraninite` | tools and armor | `uraninite_decay` | `uraninite_sickness` | raw ore, the weakest of the four: it stops a target's wounds closing, and three times in ten it weakens whoever strikes the wearer | both new | own |
| `cyanite` | tools and armor | `cyanite_chill` | `cyanite_chillback` | it chills what it strikes; worn, two times in five it chills whoever strikes you | both new | own |
| `blutonium` | tools and armor | `blutonium_pulse` | `blutonium_fallout` | it withers what it strikes; worn, half the time it withers whoever strikes you | both new | own |
| `ludicrite` | tools and armor | `ludicrite_surge` | `ludicrite_meltdown` | the hottest of the four reactor metals: three times in five, whoever strikes the wearer withers | both new | own |
| `fluix` | tools and armor | `fluix_arc` | `stormward` | charged quartz arcs out of the blade; worn, the charge passes the wearer by | tool new, armor existing | own |
| `quartz_enriched_iron` | tools and armor | `quartz_enriched_edge` | `temperward` | quartz through the grain both ways: a flat point onto the blow it lands, a flat point off the blow it stops | both new | own |
| `silicon` | tools and armor | `silicon_lattice` | `silicon_lattice` | one trait, both sides: the lattice holds one more modifier on a tool or on a worn piece | new | own |
| `iesnium` | tools and armor | `iesnium_rite` | `blightward` | a ritual metal weakens what it strikes; worn, it weakens whoever strikes you | tool new, armor existing | own |

The reactor metals climb the way the essence ladder does, weakest ore to hottest alloy:
`uraninite_sickness` (weakness, three seconds, 30%), `cyanite_chillback` (slowness, four seconds,
40%), `blutonium_fallout` (wither, four seconds, 50%), `ludicrite_meltdown` (wither, five seconds,
60%). `silicon` needed no companion at all: `silicon_lattice` grants a modifier slot through
`bonusSlots`, which runs on a worn piece as readily as on a held tool, so its armor list came off
rather than gaining an id -- the `emberdrink` and `bracingplate` case from batch 2.

## One-sided materials

Twenty-five materials build tools and no armor at all, so the rule asks nothing of them on the armor
side and none of them was given plating it did not have. The Elementarium presets are the clearest
case: `scripts/generate_elementarium_materials.py` says in its own docstring that those six are tool
only on purpose.

Seven materials build only a bowstring or a fletching and carry no trait at all: `string`, `vine`
aside, `feather`, `leaf`, the three `slimeleaf_*` colours and `slimevine_purple`. Upstream 1.12
gives string, vine, feathers and leaves no trait either, and the repository's 1.12-parity default
says the tool side of a ported material keeps upstream's trait. Inventing one for them is a
maintainer decision rather than a gap this rule should force, so the guard names them and skips
them. `vine` itself is not in that list: it builds maille as well as a bowstring, so it got
`vine_weave`, a self-repair that works on either.

`blaze`, `endrod` and `reed` build a shaft and carry `hovering`, `endspeed` and `breakable`. Those
three override no hook at all -- `ArrowEntity` and `BowItem` read them by id -- so reflection cannot
see where they run. The guard carries a six-entry table of traits like that (issue #1092 listed them)
rather than exempting anything hookless.

## The guard

`TraitReachabilityTest#everySideAMaterialBuildsHasATraitThatWorksThere` is the rule, read off the
registries rather than a hand-written list: for every material, for every side it can build, at least
one trait granted on that side must have a hook that runs there. It shares the hook-to-side map and
the material reader with the #1092 guard beside it, so a hook added without a decided side still
fails `everyHookIsClassified` first.

Two things it does not check. A reachable hook is not a reachable condition: `duskward` needs a dark
place and `stormward` needs lightning. And "belongs together" is a judgement, not a property -- the
table above is where that is argued, and a reviewer is the check.

## Signature traits (issue #1114)

Issue #1103 made the shared families strong. It added almost no character, and the scan behind
04-impact.md still read 29 of 216 materials as inert on a side they can be built for: their only
traits were numbers too small to attribute to anything, a drawback with no upside, or nothing at all.
This section is what closed that to zero, and the table at the end is the before and after.

### Four behaviours, not forty traits

Each one is a `TraitBehaviors` entry with a codec, so a datapack tunes it and several materials use
it at different numbers. That is the constraint the issue set: keep the behaviour classes few and
general rather than one class per material.

| behaviour | what it does | parameters | who has it |
| --- | --- | --- | --- |
| `vein_break` | One swing takes the connected run of the same block, and each extra block costs durability | `max_blocks`, `durability_per_block` | Veinseeker I (12 blocks) on `hollowstone` and `faultsteel`, II (28) on `hollowsteel`, both at 4 durability a block |
| `banked_strike` | A kill banks a charge; the next blow spends the whole bank | `per_charge`, `cap`, `decay` | Warcharge I on `warspar` and `voltcinder`, II on `truesteel` |
| `stored_retaliation` | A worn piece stores part of every blow and erupts once the store is full | `stored_fraction`, `threshold`, `radius`, `release_fraction` | Backlash I on `quakestone` and `faultsteel`, II on `hollowsteel` |
| `conditional_mining_speed` | Mines faster where the world suits the metal, slower where it does not | `condition`, `bonus`, `penalty` | Sunforged on `sunsteel` and `daybrass`, Stormfed on `stormalloy`, Netherkeen on `cinderforge`, `embercast` and `hardcinder` |

`vein_break` reuses `AoeHarvest`'s own flood fill and break loop rather than adding a third one,
the way `cascading_break` already reuses the second. `banked_strike` and `stored_retaliation` keep
their state in a `TraitStacks` component, the shape `momentum`, `insatiable` and
`stacking_resistance` already use. `conditional_mining_speed` is the general form of the two
upstream traits that each do this for one fixed condition, `aquadynamic`'s water and `aridiculous`'
biome heat, with a penalty half neither of them has.

The first three announce a proc through `TraitFeedback.fire` (issue #1115). The fourth does not, on
that issue's own guidance: a bonus that holds for as long as the weather does is not an event.

### Everything else was reassignment

Twenty-two of the thirty materials below got no new behaviour at all. They got an id that already
existed, because #1103 withdrew the rule that no two materials may share one. `glowveil` had a
drawback and no upside, so it gained `luminous` and `duskward` and kept `fallout` as the price.
`fulmenite` gained `arcing` and kept `unstable_core` as its price. `cinderforge`'s
`fire_protection` became outright `fireward` immunity, which is what a material made in a fire
should read as. `mendstone`, the material named for mending, moved from the slowest rung of the
self-repair ladder to the fastest.

The seven bowstring-and-fletching-only materials get a trait each, which settles the open decision
this document recorded above. The four Forgeweave-own ones (`slimeleaf_blue`, `slimeleaf_orange`,
`slimeleaf_purple`, `slimevine_purple`) get `skyborne` for the draw speed and `ecological` for the
regrowth.

The three vanilla ones keep upstream's draw speed and get a ranged effect of their own instead,
through three new hooks on `Trait`. Draw speed was the only ranged number `Trait` exposed, and every
1.12 bow-parity GameTest pins it for a vanilla-material bow, so `string` could not have it. Each hook
is one line at a call site that already existed.

| material | part | trait | hook | call site |
| --- | --- | --- | --- | --- |
| `string` | bowstring | Truestring, a fully drawn shot strays 60% less | `shotInaccuracyFactor(drawProgress)` | `BowItem#shoot`, beside `endspeed`'s own hardcoded factor |
| `feather` | fletching | Featherglide, the arrow drops half as fast | `projectileGravityFactor()` | `ArrowEntity#getDefaultGravity`, where `hovering`'s 5% already lives |
| `leaf` | fletching | Leafsprung, one shot in four costs no arrow | `ammoSaveChance()` | `BowItem#consumeAmmo` |

Air drag would have been the other half of "the arrow keeps its speed", but vanilla's 0.99 is written
into `AbstractArrow#tick` with no override, so reaching it would mean a mixin. Gravity is the half
that is both reachable and the half an archer aims with. The saved arrow is a discrete event, so it
fires a `TraitFeedback` cue; the other two are standing bonuses and stay silent.

### Before and after

Thirty material trait blocks moved. The rest of the roster is untouched; what changed for those is
the magnitude behind an id they already named, which the PR body lists.

| material | acquisition | before | after |
| --- | --- | --- | --- |
| `hollowsteel` | multistep alloy (depth 4) | general=[bloodtally] | general=[bloodtally, veinseeker2]; armor=[backlash2] |
| `truesteel` | multistep alloy (depth 4) | general=[berserker_stance]; armor=[bloodward] | general=[berserker_stance, warcharge2]; armor=[bloodward] |
| `stormalloy` | multistep alloy (depth 3) | general=[unraveling]; armor=[voidward] | general=[unraveling, stormfed]; armor=[voidward, stormward] |
| `sunsteel` | multistep alloy (depth 3) | general=[seismic]; armor=[heft2] | general=[seismic, sunforged]; armor=[heft2] |
| `cinderforge` | multistep alloy (depth 2) | general=[magmaforge]; armor=[fire_protection] | general=[magmaforge, netherkeen]; armor=[fireward] |
| `daybrass` | multistep alloy (depth 2) | general=[daybound] | general=[daybound, sunforged] |
| `faultsteel` | multistep alloy (depth 2) | general=[cascading]; armor=[depth_protection] | general=[cascading, veinseeker]; armor=[backlash] |
| `glowveil` | multistep alloy (depth 2) | general=[fallout] | general=[luminous, fallout]; armor=[duskward] |
| `embercast` | alloy (depth 1) | general=[sunmend] | general=[sunmend, netherkeen] |
| `mendstone` | alloy (depth 1) | general=[ecological] | general=[ecological3]; armor=[mendward] |
| `quakestone` | alloy (depth 1) | general=[quakecrumble]; armor=[depth_protection] | general=[quakecrumble]; armor=[backlash] |
| `fulmenite` | melt | general=[unstable_core] | general=[unstable_core, arcing]; armor=[stormward] |
| `hardcinder` | melt | general=[leanharvest]; armor=[depth_protection] | general=[leanharvest, netherkeen]; armor=[depth_protection] |
| `hollowstone` | melt | general=[hollowyield]; armor=[depth_protection] | general=[hollowyield, veinseeker]; armor=[depth_protection] |
| `voltcinder` | melt | general=[overburdened]; armor=[heft2] | general=[overburdened, warcharge]; armor=[heft2] |
| `warspar` | melt | general=[warmemory] | general=[warmemory, warcharge] |
| `slime` | melt | general=[slimey_green] | general=[slimey_green, ecological] |
| `blueslime` | craft/no-melt | general=[slimey_blue] | general=[slimey_blue, ecological] |
| `bronze` | melt | general=[steadfast] | general=[steadfast, stonewake] |
| `tin` | melt | general=[steadfast] | general=[steadfast, quick] |
| `invar` | melt | general=[steadfast] | general=[steadfast, emberwake]; armor=[temperward] |
| `carminite` | craft/no-melt | general=[voidward2] | general=[voidward2, chaosmark] |
| `vibranium_allthemodium_alloy` | craft/no-melt | general=[bracingplate] | general=[bracingplate2] |
| `feather` | craft/no-melt | nothing | general=[featherglide] |
| `leaf` | craft/no-melt | nothing | general=[leafsprung] |
| `string` | craft/no-melt | nothing | general=[truestring] |
| `slimeleaf_blue` | craft/no-melt | nothing | general=[skyborne, ecological] |
| `slimeleaf_orange` | craft/no-melt | nothing | general=[skyborne, ecological] |
| `slimeleaf_purple` | craft/no-melt | nothing | general=[skyborne, ecological] |
| `slimevine_purple` | craft/no-melt | nothing | general=[skyborne, ecological] |

### What the scan says now

`impact_scan.py` in the review folder, rerun against this branch: 0 of 216 materials inert on a side
they can be built for, down from 29. 0 with no trait at all, down from 7. 0 whose best trait across
every side is one a player cannot feel, down from 19. 0 trait ids that no material names, down from
one. Seven trait ids are still in the scan's `invisible` bucket and every one is there on purpose:
`baconlicious`, `prickly`, `slimey_green` and `slimey_blue` are upstream 1.12 magnitudes kept as they
are, `fallout` and `unstable_core` are drawbacks that pay for a material's upside, and `temperward`
is issue #1113's own number. Each of the seven sits on a material that also carries something felt.

The scan needed two corrections to read the tree honestly, both recorded in the PR: a worn trait is
judged by what a full set totals rather than by what one piece pays, and the hand-read table of Java
magnitudes was stale for the eight ids issues #1103 and #1114 moved.
