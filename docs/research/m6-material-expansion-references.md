# M6 material expansion — reference notes from TAIGA, PlusTiC, Moar Tinkers and Tinkers' Evolution

**Date:** 2026-08-31. Prepared for the M6 planning session (SCOPE milestone: "Material expansion at
TAIGA scale; modded metals become tool materials via the datapack registry",
[docs/SCOPE.md](../SCOPE.md)).

**Sources.** Four local read-only clones, all **inspiration-only** under
[ADR-0003](../adr/0003-provenance.md) — read the design, derive nothing (no code, no text, no assets):

| Clone | Pinned commit | License | Why it matters for M6 |
| --- | --- | --- | --- |
| `~/development/minecraft/references/taiga-1.12` | `4ba49b23` | GPL-3.0 | Self-contained progression: own worldgen ores, own alloy ladder |
| `~/development/minecraft/references/plustic-1.12` | `107f2b71` | Apache-2.0 (inadmissible under ADR-0003) | Cleanest 1.12 per-mod conditional registration |
| `~/development/minecraft/references/moar-tinkers-1.12` | `afdb44af` | Unresolved (no license file) | Closest to M6's actual scope: a large roster sourced entirely from other mods |
| `~/development/minecraft/references/tinkers-evolution-1.12` | `bcdabe00` | Custom (MIT + "Good not Evil" clause — not admissible under ADR-0003) | The largest of the four: 97 materials across 19 source mods, ~150 trait/modifier instances. See §6. |

Everything below is described in this document's own words. Counts are from the pinned trees, and
where they disagree with [tic2-addon-ecosystem.md](tic2-addon-ecosystem.md) (which used CurseForge
page claims for some numbers), section 5 records the correction.

---

## 1. Conditional per-mod registration

M6's central technical decision: **a modded-metal material must not exist at all** — not in the
creative tab, not in JEI, not in the Part Builder's material matching, not in the guide book — when
its source mod is absent. The 1.12 addons solved (or half-solved) this in three different ways.

### 1.1 The upstream substrate both addons build on

Tinkers' 1.12 itself (`~/development/minecraft/references/tinkers-1.12`, MIT) implements compat-metal
gating as *register hidden, integrate visible, prune the rest*:

- `library/MaterialIntegration.java` — `preInit()` always registers the material (stable IDs
  regardless of load order); `integrate()` checks every `oreRequirement` string against the
  OreDictionary and returns early if any is empty, so only satisfied integrations get melting/casting
  recipes and a `material.setVisible()` call.
- `tools/TinkerMaterials.java` (`mat()` helper) — the base mod's own compat metals are constructed
  hidden-by-default; integration is what un-hides them.
- `TinkerIntegration.java:170` → `TinkerRegistry.removeHiddenMaterials()` — at the end of postInit,
  any material still hidden is **deleted from the registry**, so the book, JEI and creative subitems
  (which iterate the registry) never see it.

So 1.12's "does not exist" is really "registered, then removed before anything user-facing runs".
It works, but only for materials that opted into hidden-by-default.

### 1.2 PlusTiC: guard the whole module, never construct the material

- `landmaster/plustic/modules/IModule.java` — a module interface with `init()`/`init2()`/`init3()`
  hooks; `landmaster/plustic/PlusTiC.java` (`preInit`) instantiates 27 modules (roughly one per
  integrated mod) and runs each hook across the FML lifecycle phases.
- Each module wraps its **entire body** in a double gate, e.g.
  `landmaster/plustic/modules/ModuleMekanism.java#init`: a per-module config flag (`Config.mekanism`)
  AND `Loader.isModLoaded("mekanism")` (both id casings, for 1.12 reasons). If either fails, the
  `Material` object is never constructed, never added to `PlusTiC.materials`, never handed to
  `TinkerRegistry` — absence means *it never existed*, which is exactly the M6 requirement.
- Modules also dedupe against other addons before creating shared metals:
  `TinkerRegistry.getMaterial("osmium") == Material.UNKNOWN` first, create second.
- Fluids are created inside the same guard (`Utils.fluidMetal(...)`), so nothing dangles; where a
  fluid might already exist (invar), it checks `FluidRegistry.isFluidRegistered` before making one.
- `PlusTiC#preIntegrate` then funnels every material it *did* create through upstream's
  `MaterialIntegration` (`TinkerRegistry.integrate(mi).preInit()`), reusing the oredict machinery for
  melting/casting/toolforge recipes.

**Lesson:** gate at construction time, per source mod, with a config override, and keep the gate
around *everything* the material drags in (fluid, recipes, oredict registrations).

### 1.3 Moar Tinkers: declarative roster, but only half a gate

- `registry/ModMaterials.java` — one static array of 50 builder-style `MaterialRegistration` entries
  (identifier, color, melt temperature, head/handle/extra/bow stats, traits, source item or oredict);
  `preInit()`/`init()` walk the array in two phases.
- `registry/MaterialRegistration.java#register` has the right gate *in code*:
  `mod == null || Loader.isModLoaded(mod)`, plus a per-material user allowlist
  (`ConfigOptions.materialIsAllowed`) and a dedupe check against `TinkerRegistry`. But at the pinned
  commit **no roster entry ever calls `setMod(...)`** — the shipped gating is entirely oredict-based,
  through `MoarMaterialIntegration.java` (a re-implementation of upstream's `MaterialIntegration`
  whose `integrateRecipes()` likewise bails when `OreDictionary.getOres(ore).isEmpty()`).
- The failure mode that makes this the cautionary tale: addon materials are constructed with the
  ordinary `new Material(name, color)` — **not hidden** — so upstream's `removeHiddenMaterials()`
  prune never touches them. A Moar Tinkers material whose source mod is missing stays registered and
  visible: ghost part variants in creative and JEI with no way to obtain them. The item-cast path is
  buggier still: `getCastItem()` returns `ItemStack.EMPTY` when the item id is unknown, and the
  late-phase `register(true)` null-check never fires, so the material gets an *empty* crafting item.
- `Loader.isModLoaded` does appear where it matters less: alloy recipes
  (`registry/ModAlloys.java` — skip constantan if Immersive Engineering already provides it, extra
  melting recipes only with Thermal Foundation) and trait compat helpers
  (`compat/CompatHelper.java` — Botania/Psi API calls behind mod checks).

**Lesson:** gating only obtainability (recipes) while leaving the registry entry alive produces
exactly the ghost-material UX M6 is chartered to prevent. Gate existence, not just recipes.

### 1.4 Delta to Forgeweave's ADR-0002 pipeline

Current state ([ADR-0002](../adr/0002-content-data-model.md)): `Material` is a NeoForge **datapack
registry** — registered in
[`Forgeweave.java`](../../src/main/java/dev/gkissel/forgeweave/Forgeweave.java)
(`registerDataPackRegistries`, ~line 264) with `Material.CODEC` doubling as the network codec, so the
server loads all 43 JSONs under
[`data/forgeweave/forgeweave/material/`](../../src/main/resources/data/forgeweave/forgeweave/material)
unconditionally and syncs them to clients for free. The four M3.2 compat metals
(bronze/lead/silver/electrum, e.g. [`bronze.json`](../../src/main/resources/data/forgeweave/forgeweave/material/bronze.json))
are gated **only on obtainability**: their `crafting_items`/`repair_item` are `c:` tags, and when no
mod fills the tag the Part Builder simply never matches
([`PartBuilderRecipes.java`](../../src/main/java/dev/gkissel/forgeweave/menu/PartBuilderRecipes.java) ~line 347).
That is Moar Tinkers' half-gate, and it has Moar Tinkers' symptom: the material still exists
everywhere the registry is iterated —

- [`ForgeweaveCreativeTab.java`](../../src/main/java/dev/gkissel/forgeweave/item/ForgeweaveCreativeTab.java)
  ~line 346: part variants for every registry material with matching stats (ghost bronze parts show
  in creative today);
- [`ForgeweaveJeiPlugin.java`](../../src/main/java/dev/gkissel/forgeweave/jei/ForgeweaveJeiPlugin.java)
  ~line 382 and [`BookContent.java`](../../src/main/java/dev/gkissel/forgeweave/client/book/BookContent.java)
  ~line 77: JEI and guide-book material listings iterate the synced registry.

`SteelAndTagGatedGameTests` documents this as M3.2's *intended* upstream-oredict-parity behavior
(unobtainable, not nonexistent) — M6 raises the bar.

**The mechanism is already in the platform.** NeoForge 21.1 (verified against the project's
`neo_version=21.1.248` patched sources) decodes every datapack-registry element through
`ConditionalOps.createConditionalCodec` in its `RegistryDataLoader` patch and skips entries whose
conditions fail ("Skipping loading registry entry … as its conditions were not met"). So the core of
M6's condition support is a JSON field, no Forgeweave code:

```json
"neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "mekanism" }]
```

An entry that fails the condition is never loaded server-side and never synced, so every consumer
above — creative tab, JEI, book, Part Builder, tool assembly — sees nothing, with zero changes to
those call sites. This is strictly cleaner than 1.12's register-hidden-then-prune.

Constraints and the work that *does* remain:

1. **Condition vocabulary.** Registry-element conditions evaluate with
   `ICondition.IContext.TAGS_INVALID` — tag-based conditions **throw** at datapack-registry load
   time (tags are not loaded yet). Usable primitives: `neoforge:mod_loaded`,
   `neoforge:item_exists` (item registry is frozen by then), and the and/or/not/true/false
   combinators. Consequence: the current `c:`-tag obtainability gate cannot be lifted 1:1 into an
   existence gate; a metal provided by any-of-several mods needs an `or` of `mod_loaded`/
   `item_exists` on concrete ids, or keeps tag-gating.
2. **Companion registry entries.** Melting, casting, alloy and embossing recipes for a conditional
   metal live in the other datapack registries registered in the same `registerDataPackRegistries`
   block — they support the same `neoforge:conditions` field and must carry the same condition, or
   they dangle against a missing material/fluid.
3. **Java-registered content.** Fluids and items
   ([`ForgeweaveFluids.java`](../../src/main/java/dev/gkissel/forgeweave/fluid/ForgeweaveFluids.java),
   [`ForgeweaveItems.java`](../../src/main/java/dev/gkissel/forgeweave/item/ForgeweaveItems.java))
   register unconditionally on NeoForge. Today's four compat metals dodge this entirely — they ship
   no Forgeweave fluid or item, Part Builder from the other mod's ingots is their only path. M6 must
   decide per metal: stay Part-Builder-only (cheapest), or register an always-present molten fluid
   that is inert and hidden from creative/buckets when the condition fails (the
   `ModList.get().isLoaded` idiom already used for ponder/darkmodeeverywhere in `Forgeweave.java`
   ~lines 245–255 is the precedent for that gating).
4. **Lang/datagen.** `material.forgeweave.*` keys can ship unconditionally in
   `ForgeweaveLanguageProvider`; dead keys are harmless.
5. **The existing four.** Migrating bronze/lead/silver/electrum from tag-gating to conditions changes
   observable behavior (their parts vanish from creative/JEI/book on packs without a provider). That
   is the M6 goal, but it reverses an M3.2 decision — record the maintainer call in the PR.
6. **Testing.** GameTests can fake a `c:` tag with the gametest-only datapack (how
   `SteelAndTagGatedGameTests` proves the bronze positive path) but cannot fake a modid or another
   mod's item id. The negative path (conditioned material absent from the registry) is directly
   testable; a positive `mod_loaded` path needs a modid we actually load in the dev/test environment
   (e.g. `ponder`, jar-in-jar embedded) or an `item_exists` condition pointing at a
   gametest-registered item.

---

## 2. TAIGA roster — self-contained progression with own worldgen

TAIGA adds no cross-mod dependencies: 15 ore blocks plus basalt and two meteor-fall rocks
(`Blocks.java` — meteorite/obsidiorite arrive as falling meteors), a smeltery alloy ladder
(`Alloys.java`, 28 `registerTinkerAlloy` calls producing 21 distinct outputs), and 37 integrated
materials (`TAIGA.java#registerTinkerMaterials`: 30 with full tool stats, 7 smeltery-only alloy
ingredients). Progression is enforced with block hardness plus **custom harvest levels above
cobalt** (`MaterialTraits.java`: `DURANITE = 5`, `VALYRIUM = 6`, `VIBRANIUM = 7`) — worth noting
that Forgeweave has no numeric harvest levels (tag-based `incorrect_for_tool` per CONTEXT.md), so a
TAIGA-style ladder means minting new tool-tier block tags, not new numbers.

Tier below = harvest level of the material's head stats (mining-capability rung, vanilla names plus
TAIGA's three custom rungs). Trait glosses are paraphrases of the behavior, not upstream text.

### Ore-sourced tool materials (12)

| Material | Tier | Trait(s) — one-phrase idea |
| --- | --- | --- |
| Basalt | stone | Digging dirt-like blocks heals the wielder |
| Tiberium | diamond | Unstable — random explosions during use |
| Aurorium | cobalt | Tool slowly self-repairs at night |
| Eezo | cobalt | XP arrives in feast-or-famine bursts; sustained digging fatigues you |
| Prometheum | duranite | Handle: darkness debuffs; also randomly captures mobs for later release |
| Duranite | duranite | Fewer block drops, more experience |
| Palladium | duranite | Stronger at night, but kills build up a chance of random self-harm |
| Valyrium | valyrium | Bonus damage against enemy types you have already fought |
| Uru | valyrium | Blocks yield XP instead of their loot |
| Vibranium | vibranium | Handle: hits can fling enemies; head: grows stronger near death |
| Meteorite | obsidian | Faster on soft blocks; smashes a share of drops to nothing |
| Obsidiorite | cobalt | Stats drift upward randomly over time (upstream's alien) |

### Alloy tool materials (18)

| Alloy | Main inputs | Tier | Trait(s) — one-phrase idea |
| --- | --- | --- | --- |
| Terrax | karmesine + ovium + jauxum | cobalt | Kills drop extra meat instead of XP |
| Triberium | tiberium + basalt (or dilithium) | diamond | Mining cracks nearby blocks and bleeds durability |
| Fractum | triberium + obsidian + abyssum | diamond | Breaks blocks onward in a straight line |
| Violium | aurorium + ardite | cobalt | Night-time durability regeneration |
| Proxii | prometheum + palladium + eezo | duranite | Random teleport effects on you or your surroundings |
| Tritonite | cobalt + terrax | cobalt | Clears water in a radius while mining |
| Ignitz | ardite + terrax + osram | cobalt | Melts mined blocks to lava; handle adds fiery drops |
| Imperomite | duranite + prometheum + abyssum | duranite | Stupefies enemies (AI-numbing debuffs) |
| Solarium | valyrium + uru + nucleum | vibranium | Very heavy: mining fatigue, but stone crumbles into gravel/sand drops |
| Nihilite | vibranium + solarium | valyrium | Weapon permanently strengthens with kill count |
| Adamant | vibranium + solarium + iox | vibranium | Activatable berserk mode: burn health for speed and damage |
| Dyonite | triberium/tiberium + fractum + seismum | duranite | Stores explosions to release later without hurting you |
| Nucleum | proxii/imperomite/niob + catalysts | valyrium | Radioactive: decays over time; mined blocks can mutate into other blocks |
| Lumix | palladium + terrax | cobalt | Daytime glow buff on the handle; head grants night-vision chances |
| Seismum | obsidian + triberium + eezo | cobalt | Earthquake-style chained block breaking |
| Astrium | terrax + aurorium | cobalt | Right-click escape teleport (unreliable by design) |
| Niob | palladium + duranite + osram | cobalt | Struck enemies can get back up again |
| Yrdeen | uru + valyrium + osram/eezo/abyssum | cobalt | Tool repairs itself from breaking natural blocks |

### Smeltery-only ingredients (7)

Osram, Abyssum, Iox, Karmesine, Ovium, Jauxum, Dilithium — fluids/ingots that exist purely as alloy
inputs and catalysts (`integrateOre` in `TAIGA.java`), no tool stats. A useful M6 shape: not every
new metal needs part stats; some can exist only to make the alloy table interesting.

TAIGA also registers trait *instances* beyond one-per-material: 36 `Trait*.java` classes, 35
instances in `MaterialTraits.java`, plus two upstream traits reused (alien, crumbling).

---

## 3. Moar Tinkers roster — materials 100% sourced from other mods

The closest analogue to M6's real scope. 50 roster entries at the pinned commit (49 distinct ids —
tungsten appears twice with different stats; the registry dedupe drops the second). Source-mod
attributions below follow each entry's oredict/item ids; several generic metals (tin, nickel,
aluminum, brass, zinc, platinum, iridium…) resolve against whichever installed mod fills the
oredict, with Thermal Foundation the usual 1.12 provider. Trait glosses are paraphrased from the
trait classes (`traits/`, `registry/ModTraits.java`).

| Material(s) | Primary 1.12 source | Trait idea (one phrase) |
| --- | --- | --- |
| Enderium | Thermal Foundation | Enderference plus head-scoped "ender magnet" that warps drops to you |
| Signalum | Thermal Foundation | Upstream unnatural (speed vs. wrong-tool blocks) |
| Lumium | Thermal Foundation | Faster/stronger in bright light |
| Platinum, Invar, Nickel, Tin, Brass, Aluminum, Zinc, Constantan | Thermal Foundation / generic oredict metals | Mostly upstream traits (dense, magnetic, crumbling); constantan scales with temperature extremes; tin/mithril grant defensive potion shields |
| Iridium | Thermal / generic | "Weee" — mining-speed chaos effect |
| Mithril | Thermal / generic | Defensive potion-effect shield while held |
| Refined Obsidian, Refined Glowstone, Osmium | Mekanism | Darkness (blind+slow on hit) on obsidian; light boost on glowstone; osmium traitless |
| Manasteel, Terrasteel, Elven Elementium, Gaia | Botania (+ addons for gaia ingot) | Mana repair (spend mana pool to mend) / mana eater (spend mana to boost); gaia adds retaliation damage |
| Yellorium, Cyanite, Blutonium, Ludicrite | Extreme Reactors | Radioactive I–III: poison/hunger auras on use, leveled per metal |
| Quartz Enriched Iron | Refined Storage | — |
| Draconium, Wyvern, Awakened, Chaotic | Draconic Evolution (cores as casts) | RF repair + RF-powered boost + extra-modifier "OP" stack, tiers scaling to absurd endgame stats |
| Psi, Psi Gem, Ivory Psi, Ebony Psi | Psi | Psi-energy repair / psi-eater boost; ivory adds holy, ebony adds darkness |
| Dark Matter, Red Matter | ProjectE | Darkness on hit; dark matter has a chance-of-instant-kill head trait |
| Electrum Flux | Redstone Arsenal | RF repair scoped to handle/extra/shaft, RF-eater head |
| Refined Iron, Advanced Alloy | IC2-era tech mods | — |
| Tungsten (×2), Titanium, Chrome | assorted tech mods | — (pure stat materials) |
| Ruby, Sapphire, Peridot | Project Red / Tech Reborn | Reflect (block reprisal), launch (uppercut hits), shock |
| Yellow Garnet, Red Garnet | Tech Reborn | Autosmelt+superheat; red garnet head *heals* what it hits (novelty) |
| Rubber | IC2 / Tech Reborn | Slingshot knockback scaling, near-zero damage |
| Certus Quartz, Fluix | Applied Energistics 2 | Shocking on certus; fluix traitless |

Traits: 23 `Trait*.java` classes, 29 registered instances (`ModTraits.java` — several are leveled
variants of one class: `TraitRadioactive(1..3)`, `TraitInstantDeath(1..3)`, `TraitMoarWritable(1..2)`).
The leveled-instance pattern maps directly onto ADR-0004's parameterized behavior library plan for M6.

Note: the addon's public blurb (echoed in the ecosystem survey) lists EnderIO among the sources, but
the pinned tree has no EnderIO-specific entry — EnderIO metals only ever arrived via shared oredict
names. Neither PlusTiC nor Moar Tinkers has a dedicated EnderIO module.

---

## 4. PlusTiC integration list — sizing reference for M6 preset JSONs

One module per integrated mod (`landmaster/plustic/modules/`), each behind
`Config.<mod> && Loader.isModLoaded(<modid>)`. ~68 tool materials across the modules, 59 tool trait
classes plus 6 armor trait classes (`traits/`, `traits/armor/`). The per-module material counts are
the realistic sizing for M6's per-mod preset JSON files:

| Module (gate modid) | Materials added |
| --- | --- |
| Base (always on) | tnt, emerald, alumite, nickel*, invar*, iridium* (\* only if no other addon already registered them) |
| Natura (`natura`) | ghostwood, darkwood, bloodwood, fusewood, flamestring (5) |
| Thermal Foundation (`thermalfoundation`) | enderium, lumium, signalum, platinum (4) |
| Mekanism (`mekanism`) | osmium, refined obsidian, refined glowstone, osgloglas, osmiridium (5) |
| Botania (`botania`, + `botanicaladdons`) | manasteel, terrasteel, elementium, livingwood, mirion (5) |
| Gems (per-gem gates: `biomesoplenty`, `projectred-core`, `erebus`, `aoa3`) | amber, amethyst, ruby, sapphire, peridot, topaz, tanzanite, malachite, jade, aoa sapphire (10) |
| Actually Additions (`actuallyadditions`) | black quartz, restonia, palis, diamatine, emeradic, enori, void (7) |
| Environmental Tech (`environmentaltech`) | litherite, erodium, kyronite, pladium, ionite, aethium, mica (7) |
| Draconic Evolution (`draconicevolution`) | wyvern/awakened/chaotic core materials (3) |
| Applied Energistics 2 (`appliedenergistics2`) | certus quartz, fluix (2) |
| Psi (`psi`) | psi metal, psi gem (2) |
| ProjectE (`projecte`) | dark matter, red matter (2) |
| Galacticraft (`galacticraftcore`/`galacticraftplanets`) | desh, titanium (2) |
| ArmorPlus (`armorplus`) | guardian scale, wither bone (2) |
| Advanced Rocketry (`libvulpes`) | titanium (1) |
| Astral Sorcery (`astralsorcery`) | starmetal (1) |
| Avaritia (`avaritia`) | infinity (1) |
| Thaumcraft (`thaumcraft`) | thaumium (1) |
| GemsPlus (`gemsplus`) | phoenixite (1) |
| Industrial Foregoing (`industrialforegoing`) | pink slime (1) |
| LandCraft / LandCore (`landcraft`/`landcore`) | that mod family's own metals |
| Construct's Armory bridge (`conarm`, + `simplyjetpacks`) | armor-stat bridging for existing PlusTiC materials, no new metals |

Relevance to M6 target picking: of the mods the milestone is likely to target on NeoForge 1.21,
PlusTiC covers **Mekanism, Thermal and Botania** (the 1.12 ancestors of today's likely targets);
Create has no 1.12 ancestor here, and EnderIO has no dedicated module in either addon (see §3 note).
Typical per-mod preset size lands at **1–7 materials**, median ~2–3 — per-mod JSON files stay small
even if the total roster is large.

---

## 5. Sizing observations

Real counts from the pinned clones, vs. the plateau estimate in
[tic2-addon-ecosystem.md](tic2-addon-ecosystem.md) ("roughly 50–70 materials with 30–45 distinct
traits is where 1.12.2 addons plateaued"):

| Addon | Materials (pinned source) | Trait classes / instances | Survey figure |
| --- | --- | --- | --- |
| TAIGA | 37 integrated (30 with tool stats + 7 smeltery-only); 15 ores + basalt + 2 meteor rocks; 28 alloy recipes (21 outputs) | 36 / 35 (+2 upstream reused) | "20 ores, 28 alloys, 28 traits" — ore figure high, trait figure low |
| Moar Tinkers | 50 entries, 49 distinct | 23 / 29 | "63 materials, 43 with traits" — CurseForge page claim, overstates the pinned source |
| PlusTiC | ~68 across ~21 modules | 59 tool + 6 armor | "63 traits" — close (65 counting armor) |

**Verdict on the plateau:** the 50–70 material band is confirmed at the top end (PlusTiC ~68), but
the per-addon reality is wider: 37 / 49 / 68. Forgeweave ships 43 materials today
(`data/forgeweave/forgeweave/material/`), so an M6 that adds a TAIGA-scale self-contained ladder
*or* a Moar-Tinkers-scale compat roster lands the total in the **80–110** range — above the survey
band, and the number the schema and UI should actually be sized against. The trait band of 30–45 is
right for what M6 should *ship*, with two qualifiers: PlusTiC proves 60+ is reachable, and both
TAIGA and Moar Tinkers get mileage from **parameterized/leveled instances of one behavior class**
(radioactive 1–3, instant-death 1–3) — which is exactly ADR-0004's planned M6 shape (datapack-
creatable trait definitions over a parameterized Java behavior library), so "distinct behaviors"
(~25–35) and "trait definitions" (~40–60) should be budgeted separately.

Where the current UI/schema feels the pressure first:

- **Creative tab part variants** — `ForgeweaveCreativeTab` emits every material × every part kind it
  has stats for; at ~100 materials × ~25 part items that is a few thousand stacks. The
  `listAllPartMaterials` client config (default on, `ForgeweaveClientConfig`) already exists as the
  relief valve; M6 should re-test the tab at scale and consider flipping the default.
- **Registry sync payload** — `Material.java`'s codec deliberately omits defaulted fields
  (the "material sync-packet budget" note); conditions help here too, since unloadable materials
  cost zero bytes.
- **Guide book** — `BookContent` builds one page per registry material; the section needs pagination
  behavior verified at ~100 entries.
- **Part Builder matching** — `PartBuilderRecipes` scans all materials' `crafting_items` per input
  change; linear today, fine at 44, worth a quick profile at 100+.
- **JEI** — one subtype per material per part (`SubtypeKeys`); JEI handles thousands of stacks
  routinely, but category registration for per-metal casting/melting recipes multiplies with the
  roster.
- **Alloy table** — TAIGA's 28 recipes over 21 outputs (several alternative recipes per output,
  catalyst metals that exist only for alloying) is the reference for how expressive
  `alloy_recipe/*.json` needs to be; Forgeweave's remainder-rule minimal-unit format already covers
  multi-input ratios.

---

## 6. Tinkers' Evolution — the largest single 1.12 addon surveyed

The maintainer asked M6 to cover "the same as" this addon specifically, so it gets its own section
rather than folding into the tables above. It is also, by every count taken below, the biggest of the
four 1.12 addons surveyed for M6 — bigger material roster than PlusTiC, far bigger trait roster than
all three others combined.

### 6.1 Identity and license verdict

- **Author:** phantamanta44 (E. Geng). **Minecraft:** 1.12.2 only. **CurseForge downloads:** ~870K.
  Depends on the author's own `libnine` library and on Tinkers' Construct.
- **Repository:** [github.com/phantamanta44/tinkers-evolution](https://github.com/phantamanta44/tinkers-evolution),
  default branch `1.12.2`. Cloned read-only, `--depth 1`, to
  `~/development/minecraft/references/tinkers-evolution-1.12` at pinned commit **`bcdabe00b58b2eed49f074a0bd8c57c3af4d8a8a`**
  (2026-06-30).
- **License verdict: not MIT, inspiration-only under ADR-0003.** The repo's own `LICENSE.md` (read
  directly, not GitHub's sidebar guess) is the MIT text with one clause inserted: *"The Software shall
  be used for Good, not Evil."* That extra clause is the well-known "JSON license" pattern — it is why
  GitHub's own SPDX detector calls this license `Other`/`NOASSERTION` rather than `MIT`, and why
  CurseForge's sidebar lists it as a "Custom License" rather than MIT. ADR-0003 admits plain MIT only;
  a look-alike with an added, unenforceable-but-present restriction does not qualify. Treat this clone
  exactly like TAIGA/PlusTiC/Moar Tinkers: read the design, derive nothing.

### 6.2 Conditional registration: a fourth pattern, closest to upstream's own

Section 1 above covers three patterns (upstream hidden-then-prune, PlusTiC's whole-module gate,
Moar Tinkers' half-gate). Tinkers' Evolution's is a fourth, and it is the most disciplined of the
four because it reuses upstream's own mechanism instead of reinventing it:

- **Behavioral hooks via reflective ASM injection.** Every optional integration (30 modids, see 6.3)
  declares an interface (e.g. `BotaniaHooks`) with a static `INSTANCE` field annotated
  `@IntegrationHooks.Inject("botania")` and defaulted to a `Noop` inner class that returns harmless
  values. `IntegrationManager.injectHooks` (`integration/IntegrationManager.java`) scans all such
  annotations at load time via FML's `ASMDataTable`; for each one it checks `Loader.isModLoaded`, a
  config blacklist (`disabledModHooks`), and an optional custom static check
  (`shouldLoadIntegration()`) before reflectively overwriting the field with the real implementation.
  A missing mod simply never overwrites the `Noop` default — no null checks scattered through call
  sites, and the same mechanism doubles as the addon's `doRegistration()`/`onPreInit()`/etc. lifecycle
  dispatch, so an unloaded mod's registration code never runs at all.
- **Material existence gating reuses upstream's own hidden-material machinery**
  (`material/MaterialBuilder.java`, `material/MaterialDefinition.java`). Every material is
  constructed hidden (`new Material(id, colour, true)`), same as upstream's own compat metals
  (§1.1). A `RegCondition` list attached at build time — `requiresMods(modId...)` (OR of
  `Loader.isModLoaded`), `requiresOres(oreKey...)` (OR of oredict-exists), `requiresMaterials(...)`
  (OR of another material being visible), or `overrides(matId...)` (config-gated priority override of
  another mod's material with the same id) — is evaluated once in `MaterialDefinition.tryActivate()`;
  only then does `material.setVisible()` run. Materials whose conditions never pass stay hidden and
  fall to upstream's own `removeHiddenMaterials()` prune at the end of postInit — Tinkers' Evolution
  never calls that method itself, it just participates in upstream's existing cleanup pass. Of the 97
  materials, 74 are oredict-gated (works with *any* mod filling that oredict key, not just the
  "expected" one) and 23 are hard mod-gated (used when no shared oredict key exists, e.g. Botania,
  Blood Magic, Draconic Evolution, ProjectE).
- **Config surface is generic, not per-mod.** Unlike PlusTiC's one `Config.<mod>` boolean per module,
  Tinkers' Evolution's config is four string collections: `disabledModHooks`, `disabledMaterials`,
  `disabledModifiers` (blacklists) and `forceLoadMaterials` (whitelist, bypasses the `RegCondition`
  checks), plus one global `overrideMaterials` boolean controlling whether it's allowed to reclaim a
  material id another mod already registered. Same expressive power as PlusTiC's per-flag approach,
  fewer generated config entries.
- **Layered detection.** At least one integration probes a sub-mod inside another mod's ecosystem:
  `ActuallyHooksImpl` separately checks `Loader.isModLoaded("actuallyadditions")` for the base mod and
  a second modid for its optional Baubles-compat submod, gating baubles-slot battery items
  independently of the main Actually Additions integration.

### 6.3 Mod integration list

30 modids gated through the mechanism above (`integration/<pkg>/*Hooks.java`), each adding some
combination of materials, item modifiers ("mods" in TC parlance — non-material tool/armor behaviors
attached via a craftable item rather than a part material), and API bridges. Paraphrased from the
repo's own README (verified against the gating code, not copied verbatim):

| Mod (gate modid) | Adds |
| --- | --- |
| Construct's Armoury (`conarm`) | Armor-material analogues for every tool material in this table, plus its own armor-only trait set (§6.5) |
| Actually Additions (`actuallyadditions`, + Baubles submod) | Black quartz + 6 AA crystal materials; battery items and the solar panel apply the energy-buffer / sunlight-recharge item mods |
| Advanced Solar Panels (`advanced_solar_panels`) | Sunnarium material; its panels apply the sunlight-recharge mod |
| Applied Energistics 2 (`appliedenergistics2`) | Sky stone, certus quartz, fluix materials, plus a fluix-steel material for the author's own AE2 add-on |
| Astral Sorcery (`astralsorcery`) | Aquamarine + starmetal materials; a constellation-alignment item mod (12 constellations, tool and armor variants) |
| Avaritia (`avaritia`) | Three escalating "cosmic" materials with custom stat classes plus indestructible/omnipotent item mods |
| Baubles (`baubles`) | API bridge only (inventory-slot lookup for other integrations), no materials of its own |
| Blood Magic (`bloodmagic`) | Bound/sentient materials; life-siphon and demon-will item mods, tool and armor |
| Botania (`botania`) | 10 materials (livingrock/livingwood through elven items); mana-repair and mana-spend item mods; Ancient Will helmet crit-effect mods themed on OSRS's Barrows brothers |
| Draconic Evolution (`draconicevolution`) | 4 upgradable materials (wyvern/draconic/chaotic tiers) plus 8 separate RF-spending item mods and a fusion-crafting upgrade path |
| Elenai Dodge 2 (`elenaidodge2`) | A dodge-prevention weapon mod and a dodge-friendlier armor mod |
| Ender IO (`enderio`) | 15 alloy/crystal materials, its widest single-mod roster; fire water as a smeltery fuel; inventory chargers/photovoltaic cells apply the energy item mods |
| Environmental Tech (`environmentaltech`) | 8 crystal-tier materials; its solar cells apply the sunlight-recharge mod |
| Forestry (`forestry`) | Apatite material; bee-protection armor mod; lets the kama harvest like Forestry's Scoop |
| Game Stages (`gamestages`) | Progression gating hook only (no materials) |
| Hbm's Nuclear Tech (`hbm`) | Contributes to the shared UU-Matter material gate alongside IC2/Tech Reborn |
| IndustrialCraft 2 (`ic2`) | Rubber, advanced alloy, energium (electricity-powered), carbon fiber, iridium, UU-matter materials; solar panels apply the sunlight-recharge mod; electricity-buffer item mod |
| Industrial Foregoing (`industrialforegoing`) | Essence/meat/pink-slime/pink-metal materials |
| Mekanism (`mekanism` + `mekanismgenerators`) | Osmium, refined obsidian/glowstone, HDPE materials; energy tablet and solar generators apply the energy item mods |
| Natura (`natura`) | 4 nether-wood materials |
| Natural Absorption (`naturalabsorption`) | An absorption-hearts armor mod |
| Natural Pledge (`naturalpledge`) | Listed as an integration point in the source tree; no distinct material or trait found at the pinned commit |
| ProjectE (`projecte`) | Dark matter / red matter materials with EMC-condensing item mods, tool and armor |
| Redstone Arsenal / Repository (`redstonerepository`) | Fluxed electrum/gem, gelid enderium/gem, fluxed string materials; gelid capacitor applies the energy item mod |
| Solar Flux Reborn (`solarflux`) | No materials; all its solar panel variants apply the sunlight-recharge item mod |
| Tech Reborn (`techreborn`) | Contributes to the shared UU-Matter gate and its solar panels apply the sunlight-recharge mod |
| Thaumcraft (`thaumcraft`) | Thaumium, void, primordial, amber, quicksilver, enchanted-fabric materials; a sanity-corruption ("warping") item mod, tool and armor; infusion-enchanting support with in-game thaumonomicon documentation |
| Thermal Series (`thermalexpansion` + `thermalfoundation`) | 9 generic metal materials (tin through enderium); flux capacitors across all 5 tiers apply the energy item mod |
| Tinkers' Tool Leveling (`tinkertoolleveling`) | API bridge hook only, no materials |
| CraftTweaker | Scripting hooks for the addon's own recipes (not a material/trait source) |
| JEI | Recipe-category display only |

Compared with PlusTiC's `Config.<mod> && Loader.isModLoaded` per-module gate (§1.2, ~21 modules) and
Moar Tinkers' oredict-only half-gate (§1.3, no modid gate at all in the shipped roster), Tinkers'
Evolution covers roughly 50% more source mods than PlusTiC while keeping each integration's gate
logic in one small annotated interface rather than one large `if` block per module.

### 6.4 Material roster (97 materials)

Full roster by source-mod group, material name and gloss paraphrased from each material's registered
traits (`init/TconEvoMaterials.java`) — not copied from any upstream text. Materials with no
listed trait use plain upstream Tinkers' Construct traits only (e.g. `crude`, `dense`, `magnetic`).

| Mod | Materials (count) | Roster |
| --- | --- | --- |
| Actually Additions | 7 | Black Quartz, Restonia, Palis, Diamantine, Void, Emeraldic, Enori — all durability-scales-damage (crystalline); Diamantine also gains bonus charged-hit magic damage |
| Advanced Solar Panels | 1 | Sunnarium — self-repairs and glows in sunlight |
| Applied Energistics 2 | 4 | Sky Stone, Certus Quartz, Fluix, Fluix Steel — certus pierces evasion on charged hits; fluix steel converts charged-hit damage to energy |
| Astral Sorcery | 2 | Aquamarine, Starmetal — both attunable to a constellation for a themed bonus |
| Avaritia | 3 | Crystal Matrix, Cosmic Neutronium, Infinity — escalating endgame tiers; Infinity is stat-maxed and its tool mod "defies gods" (bypasses normal damage-immunity rules) |
| Blood Magic | 2 | Bound Metal, Sentient Metal — HP-siphon self-shielding vs. demon-will power scaling |
| Botania | 10 | Livingrock, Livingwood, Dreamwood, Manasteel, Terrasteel, Elementium, Mana String, Mana Diamond, Mana Pearl, Dragonstone — mostly mana-fueled self-repair; Terrasteel fires Gaia beams on swing |
| Draconic Evolution | 4 | Draconium, Wyvern Metal, Draconic Metal, Chaotic Metal — energy-powered, each a tier upgrade of the last via fusion crafting, innate soul-steal |
| Ender IO | 15 | Redstone Alloy, Electrical Steel, Pulsating Iron, Conductive Iron, Energetic Alloy, Energetic Silver, Vibrant Alloy, Vivid Alloy, Crystalline Alloy, Melodic Alloy, Soularium, Ender/Pulsating/Vibrant/Weather Crystal — the widest single-mod roster; mostly combat traits (bonus damage variants), the four crystals are magic-tool-only |
| Environmental Tech | 8 | Litherite, Erodium, Kyronite, Pladium, Ionite, Aethium, Lonsdaleite, Mica — mid-to-late gem tier, mostly combat traits |
| Forestry | 1 | Apatite — can fertilize crops at a durability cost |
| Industrial Foregoing | 4 | Essence Metal, Meat Metal, Pink Slime, Pink Metal — Meat Metal is a joke material (edible-flavoured stats), Pink Metal is a late-game high-durability metal |
| IndustrialCraft 2 | 6 | Rubber, Advanced Alloy, Energium, Carbon Fiber, Iridium, UU-Matter — Energium runs on EU; UU-Matter is absurdly cheap durability offset by huge extra-material stats |
| Mekanism | 4 | Osmium, Refined Obsidian, Refined Glowstone, HDPE — dense/heavy stat profile, Refined Glowstone glows |
| Natura | 4 | Ghostwood, Bloodwood, Darkwood, Fusewood — wood-tier materials themed on their vanilla-Natura counterparts (speed, lifesteal, weaken, explosive) |
| ProjectE | 2 | Dark Matter, Red Matter — endgame stat-maxed pair, EMC-condensing on hit |
| Redstone Arsenal / Repository | 5 | Fluxed Electrum, Flux Crystal, Gelid Enderium, Gelid Gem, Fluxed String — energy-buffer-before-durability family |
| Thaumcraft | 6 | Thaumium, Void Metal, Primordial (Primal Metal), Amber, Quicksilver, Enchanted Fabric — Void Metal carries the sanity-corruption trait |
| Thermal Series | 9 | Tin, Aluminium, Nickel, Platinum, Invar, Constantan, Signalum, Lumium, Enderium — mostly plain stat metals; Signalum has an extremely fast draw speed ("machine gun bow", per the source comment) |

Two materials also get modified base-game stats rather than a new registration: vanilla Tinkers'
stone and (Tinkers' Construct's own) silver gain new magic-tool stat lines
(`init/TconEvoMaterials.java` top of `init()`).

### 6.5 Trait and mechanic catalogue

Every trait and item-mod description below is paraphrased from this addon's own tooltip text
(`assets/tconevo/lang/en_us.lang`, `modifier.tconevo.*.desc` keys) — read for the mechanic, not
copied. Leveled variants (Roman numerals in the source) are grouped into one row.

**Tool traits and item mods — base set (no source-mod dependency), ~34 instances**

| Trait/mod | Idea |
| --- | --- |
| Aftershock (I–III) | Bonus magic damage on a fully-charged hit |
| Battle Furor | Attack damage ramps up across consecutive charged hits |
| Blasting | Mining or attacking produces small explosions |
| Cascading | Destroys whole columns of falling blocks (sand/gravel) at once |
| Chain Lightning | Charged hits have a chance to arc to nearby enemies |
| Corrupting | Charged hits stack a wither effect |
| Crystalline | Deals more damage the higher the tool's remaining durability |
| Culling | Bonus damage against enemies with less health than the wielder |
| Deadly Precision | Large bonus critical-hit damage |
| Energized (I–II) | Spends an attached energy buffer before durability |
| Executor | Bonus damage scaling with the target's missing health |
| Fertilizing | Can fertilize crops, at a durability cost |
| Foot Fleet | Speed burst on a charged hit |
| Impact Force | More damage at high fall/swing velocity |
| Juggernaut | Damage scales with the wielder's *current* health (stronger near full health) |
| Luminiferous | Struck enemies glow, revealing their position |
| Modifiable (I–III) | Extra free modifier slots |
| Mortal Wounds | Temporarily reduces the target's healing |
| Opportunist | Bonus damage against already-debuffed targets |
| Overwhelm | Bonus damage against armored targets |
| Photosynthetic | Self-repairs in sunlight |
| Piezoelectric | Converts charged-hit damage into stored energy |
| Purging (I–III) | Chance to strip positive buffs off the target on a charged hit |
| Rejuvenating | Applies regeneration *to the struck enemy* (a deliberately unhelpful trait) |
| Relentless | Shortens the target's post-hit invulnerability window |
| Ruination | Charged-hit damage scales with the target's max health |
| Staggering | Briefly roots enemies on a charged hit |
| Sundering | Weakens (lowers attack) struck enemies |
| Thundergod's Wrath | Strikes lightning on enemies hit while at full health |
| True Strike | Charged hits ignore evasion/dodge |
| Vampiric | Lifesteal |
| Artifact | Item mod: locks modification/repair until "unsealed" — used for loot-only artifact gear |
| Fluxed / Photovoltaic | Item mod pair: runs off an attached battery before durability, recharges it in sunlight |
| Accuracy | Item mod: chance to ignore evasion |

**Armor traits (Construct's Armoury bridge), ~21 base instances**

Bulwark (flat 1-heart damage floor), Celestial (creative flight), Chilling Touch (slows attackers),
Divine Grace (amplifies incoming healing), Energized/Fluxed/Photovoltaic (armor side of the energy
family), Gale Force (faster flight), Hearth Embrace (fire damage taken converts to healing), Megaflip
(negates explosions, vents them as knockback), Phoenix Aspect (self-sacrificing death save), Radiant
(blinds attackers), Reactive (stacking resistance after being hit), Second Wind (regen after being
hit), Shadowstep (invisible in darkness), Spectral (evasion chance), Stifling (weakens attackers),
Stonebound (gets *better* as it wears down), Thundergod's Favour (lightning immunity), Will Strength
(immortal for a moment when struck at full health).

**Per-source-mod traits/mods, ~101 instances across the remaining mods**

| Mod | Idea |
| --- | --- |
| Astral Sorcery | Attune a tool/armor piece to one of 12 constellations at an in-world altar; each grants a themed bonus (regen, resistance, bonus damage, faster mining, speed, silk touch, ignite, brief time-freeze, illuminate, fortune, better underwater mining, self-repair over time) — tool and armor variants differ (e.g. on-hit vs. passive) |
| Avaritia | Condensing (rare bonus drop on kills/mining), Infinitum (indestructible gear), Omnipotence (bypasses normal damage immunities); armor: flat 25% damage reduction, and a brief immortality window |
| Blood Magic | Crystalys (shatters soul networks for a shard drop), Bloodbound (siphons wielder HP to shield the tool from durability loss), Sentient (consumes a "demon will" resource to grow stronger), Willful (strips demonic-will buffs from enemies); armor mirrors the HP-siphon and will-drain ideas |
| Botania | Mana-funded self-repair, converts damage dealt to mana, summons pixies to attack (or defend, armor side), fires damage beams on swing, passive mana generation, cheaper mana costs; plus six OSRS-Barrows-themed "Ancient Will" helmet crit add-ons (weakness, rage-style scaling, lifesteal, wither, slow, armor-pierce) |
| Draconic Evolution | Innate soul-steal (leveled), an RF-powered "evolved" upgrade path via fusion crafting (leveled wyvern/draconic/chaotic), 8 separate RF-spend mods (energy capacity, dig speed, dig AoE, attack damage, attack AoE, arrow damage, draw speed, arrow speed), bonus mob-soul drops, extra shield-piercing "entropy" damage, drains armor energy into bonus damage, converts damage to a "chaos" type; armor adds an energy shield with its own capacity/recovery mods plus move-speed, jump-boost, chaos-resistance and an energy-funded death save |
| Elenai Dodge 2 | Charged hits briefly disable the target's dodge; a lighter armor mod that doesn't impede the wearer's own dodge |
| Forestry | Bee-sting protection for armor; the fertilizing material trait and kama-as-scoop mechanic (§6.3) |
| IC2 / Tech Reborn / Hbm | Electricity (EU) spent before durability, tool and armor |
| Natural Absorption | Bonus absorption hearts |
| ProjectE | Converts strike energy into EMC (leveled dark/red matter); armor mitigates incoming damage (leveled) |
| Thaumcraft | Attacking or being struck inflicts Thaumcraft's sanity-corruption ("warping") stat, tool and armor (armor reflects it onto the attacker); cheaper vis costs |

**Beyond materials and traits — other mechanics from the README**

Sceptres (dual melee/ranged magic weapons that fire projectile volleys), a "Materials and You"
in-game handbook, fusion crafting (the Draconic Evolution upgrade path), Artifacts (powerful
non-repairable loot-chest gear), and two global tweaks (a configurable melt-speed multiplier, and an
option to disable the tool damage cap).

### 6.6 Counts

| Metric | Count | Note |
| --- | --- | --- |
| Java classes | 447 | Whole `src/main/java` tree at the pinned commit |
| Materials | 97 | `init/TconEvoMaterials.java`; 74 oredict-gated, 23 hard mod-gated |
| Tool trait/mod classes | 49 | `Trait*.java` outside `conarm/` |
| Armor trait/mod classes | 49 | `ArmourTrait*.java` (37) + `ArmourMod*.java` (12) under `integration/conarm/trait/` |
| Tool item-mod classes (non-material) | 19 | `Modifier*.java` under `trait/` |
| Trait/mod *instances* registered | ~156 | ~90 tool-side + ~66 armor-side, counting each leveled tier separately (§6.5) |
| Source mods integrated | 30 | Gated modids (§6.3); a few are API-bridge-only with no material/trait of their own |
| Textures | 105 | `src/main/resources/**/*.png` |

### 6.7 What this means for M6's target size

Section 5's plateau observation ("50–70 materials, 30–45 distinct traits") was built from TAIGA,
PlusTiC and Moar Tinkers alone. Tinkers' Evolution sits above that band on *both* axes at once: 97
materials (above PlusTiC's ~68) and ~156 trait/modifier instances (more than double PlusTiC's ~65,
and roughly five times TAIGA's or Moar Tinkers' instance counts) — while still resolving to a much
smaller number of *distinct behavioral ideas* than the instance count suggests, because it leans
heavily on the same parameterized/leveled-instance pattern noted in §5 (Aftershock I–III, Purging
I–III, Modifiable I–III, Energized I–II, Soul Rend I–III, Evolved I–III, Eternal Density I–II) and on
reusing one trait across many materials (Crystalline alone backs 8+ Actually Additions/AE2 gem
materials). If M6 is scoped "the same as" this addon specifically, the realistic target is nearer
**90–100 materials** and a trait *definition* count in the **50–70** range (distinct behaviors, well
under half that once leveled variants are counted as one definition each) — above the top of §5's
band on materials, and confirming that band's own note that PlusTiC's 60+ traits was "reachable," not
a ceiling.

---

## 7. Track B tier scaffold and naming vocabulary (JC9/JC10 resolution)

Written alongside [#838](https://github.com/gkissel/forgeweave/issues/838) once the maintainer
answered epic #824's JC9 and JC10 (session 2026-08-31, recorded on #824). This section is a
**starting scaffold** for #839 (ores/worldgen), #840 (fluids/alloy table) and #841 (the material
roster) to consume so the three land on one consistent vocabulary — it does not preempt #841's own
required deliverable, the stat curve, which stays "a single decision" proposed on #841's own thread
per that issue's text. Nothing here is a stat number, a trait assignment, or a final name; it is an
id list and a tier mapping so #839–#841 aren't inventing overlapping vocabulary independently.

> **Amendment (issue #877, 2026-09-01): §7.1's "JC10 = a, no new block tags" decision is reversed.**
> The maintainer now wants real, separate mining levels for progression. §7.3's naming table below is
> otherwise unaffected (JC9 stands — original Forgeweave ids, not the reference ladder's own names);
> only the *tier* each id maps to changed. See #877's PR body and `TrackBOre.Tier`'s own javadoc
> (`src/main/java/dev/gkissel/forgeweave/trackb/TrackBOre.java`) for the replacement ladder: three new
> rungs above netherite (`hardcinder`, `warspar`, `resonite`, named after the Track B material that
> anchors each one), minted as `forgeweave:incorrect_for_<tier>_tool` block tags. §7.1's table below is
> left as written for historical context; do not treat it as current.

### 7.1 Tier scaffold — no new block tags (JC10 = a) — superseded by #877, see amendment above

Track B mints **zero** new `incorrect_for_*_tool` tags. Every material collapses onto one of
Forgeweave's five existing rungs (the five values already in use across
`data/forgeweave/forgeweave/material/*.json`):

| Rung (`incorrect_for_tool`) | Existing Forgeweave materials on this rung |
| --- | --- |
| `minecraft:incorrect_for_wooden_tool` | (wood-tier materials) |
| `minecraft:incorrect_for_stone_tool` | most early metals |
| `minecraft:incorrect_for_iron_tool` | mid metals |
| `minecraft:incorrect_for_diamond_tool` | upper metals |
| `minecraft:incorrect_for_netherite_tool` | cobalt, ardite, iridium, manyullyn, netherite, obsidian, ancient — the shared top rung |

The reference ladder's four tiers above its own "diamond" (cobalt/duranite/valyrium/vibranium, §2's
"Tier" column) all collapse onto Forgeweave's `incorrect_for_netherite_tool` — the same rung as
cobalt, manyullyn, netherite and ancient today. A Track B material one reference-rung below that
(the reference ladder's "diamond") maps to Forgeweave's `incorrect_for_diamond_tool`; one further
below ("stone") maps to `incorrect_for_stone_tool`. Every Forgeweave tool that can mine netherite-tier
ore today can mine every Track B ore — progression pressure lives entirely in stats, traits and
obtainability (ore depth/biome/vein rarity, alloy chain length), per JC10(a).

### 7.2 Ore→alloy progression skeleton

Shape only (§2's own tables carry the reference ladder's actual input lists — read there for detail,
not reproduced here since the alloy ratios are #840's deliverable):

- **12 ore-sourced tool materials** — mined directly, tool stats on their own. Two of them (the
  reference ladder's meteor-fall pair) source from a rare ore vein or a rare surface feature instead
  of a meteor event, per JC11.
- **7 smeltery-only ingredients** — no tool stats of their own; exist purely as alloy inputs/catalysts
  in the melting/alloy table. Not part of #841's material roster; #840's alloy-table concern.
- **18 alloy tool materials** — each combines two or more of the above (ore-sourced metals,
  smeltery-only ingredients, and in a few cases each other) at the smeltery's alloy table. This is
  where progression pressure concentrates: reaching the top-rung alloys requires working through
  several intermediate alloys first, exactly the "progression pressure moves to the alloy chain"
  language of JC10's decision.

### 7.3 Naming vocabulary (JC9 = original Forgeweave names)

JC9's answer: mechanics, magnitudes and progression shape mirror the reference ladder; **identifiers
and player-facing text are minted fresh**. The table below proposes one original snake_case id per
reference-ladder entry from §2, grouped the same way, so #839/#840/#841 share one map instead of each
picking names independently. Ids are working proposals, not locked — #841 (or the maintainer) may
rename before the material JSONs land; what matters is that all three issues start from the same
sheet. No lang keys yet: display names/tooltips are `material.forgeweave.<id>` entries added by
whichever issue actually registers the material, per CLAUDE.md's localization rule.

**Ore-sourced (feeds #839 ore blocks + #841 tool materials):**

| Reference idea | Forgeweave id | Tier (§7.1) |
| --- | --- | --- |
| Basalt (heals digging dirt-like blocks) | `cinderstone` | stone |
| Tiberium (unstable, random explosions) | `fulmenite` | diamond |
| Aurorium (self-repairs at night) | `duskspar` | netherite (top) |
| Eezo (feast/famine XP, digging fatigue) | `voltcinder` | netherite (top) |
| Prometheum (handle darkness debuff, captures mobs) | `murkiron` | netherite (top) |
| Duranite (fewer drops, more XP) | `hardcinder` | netherite (top) |
| Palladium (stronger at night, self-harm risk) | `nightshale` | netherite (top) |
| Valyrium (bonus damage vs. fought enemy types) | `warspar` | netherite (top) |
| Uru (blocks yield XP instead of loot) | `hollowstone` | netherite (top) |
| Vibranium (handle flings enemies, stronger near death) | `resonite` | netherite (top) |
| Meteorite (faster on soft blocks, smashes some drops) — rare surface feature per JC11 | `starfall_stone` | netherite (top) |
| Obsidiorite (stats drift upward over time) — rare vein per JC11 | `voidglass` | netherite (top) |

**Alloy (feeds #840 alloy table + #841 tool materials):**

| Reference idea | Forgeweave id | Tier (§7.1) |
| --- | --- | --- |
| Terrax (kills drop extra meat, not XP) | `ironbrand` | netherite (top) |
| Triberium (mining cracks nearby blocks) | `quakestone` | diamond |
| Fractum (breaks blocks onward in a line) | `shardline` | diamond |
| Violium (night-time durability regen) | `embercast` | netherite (top) |
| Proxii (random teleport effects) | `riftalloy` | netherite (top) |
| Tritonite (clears water while mining) | `tideiron` | netherite (top) |
| Ignitz (melts mined blocks to lava) | `cinderforge` | netherite (top) |
| Imperomite (AI-numbing debuffs on hit) | `dreadalloy` | netherite (top) |
| Solarium (mining fatigue, stone crumbles to gravel/sand) | `sunsteel` | netherite (top) |
| Nihilite (permanently strengthens with kill count) | `hollowsteel` | netherite (top) |
| Adamant (activatable berserk mode) | `truesteel` | netherite (top) |
| Dyonite (stores explosions, releases later) | `stormalloy` | netherite (top) |
| Nucleum (radioactive decay, mutates mined blocks) | `glowveil` | netherite (top) |
| Lumix (daytime glow, night-vision chance) | `daybrass` | netherite (top) |
| Seismum (earthquake chained block breaking) | `faultsteel` | netherite (top) |
| Astrium (unreliable escape teleport) | `skipalloy` | netherite (top) |
| Niob (struck enemies get back up) | `mendalloy` | netherite (top) |
| Yrdeen (repairs from breaking natural blocks) | `mendstone` | netherite (top) |

**Smeltery-only ingredients (feeds #840 alloy table only, no tool stats, not in #841's roster):**

| Reference idea | Forgeweave id |
| --- | --- |
| Osram | `flarealloy` |
| Abyssum | `deepalloy` |
| Iox | `sparkalloy` |
| Karmesine | `redcinder` |
| Ovium | `pearlcinder` |
| Jauxum | `ambercinder` |
| Dilithium | `twinalloy` |

Two names flagged on the epic thread as Marvel trademarks (vibranium, uru) and one as
Tolkien-adjacent (adamant) are **not reused** above — every id in this table is an original coinage,
sidestepping the naming concern regardless of which specific reference names it would have applied
to.
