# Addon surface audit

**Audit date:** 2026-09-18. **Tree audited:** `origin/master` at `416c1a72`.

**Question:** for each thing Forgeweave contains, can a datapack, a KubeJS script or another mod's Java add one today without patching Forgeweave? This is the audit step of [#1008](https://github.com/gkissel/forgeweave/issues/1008). No production code changes in this pass.

**Method:** read the shipped source. Every claim below cites a path, and a line number where the claim is about a specific declaration. Where reading could not settle a question, the row says so instead of guessing.

## Summary

### What a datapack author can already do

Ten datapack registries are registered in `Forgeweave#registerDataPackRegistries` (`src/main/java/dev/gkissel/forgeweave/Forgeweave.java:361-399`), each with its codec passed as the network codec, so each one syncs to the client and each one accepts a top-level `neoforge:conditions` array for existence gating. A pack author can define, in JSON, in their own namespace, with no Java at all:

- materials and all their part stats (`material`),
- traits, as parameters over one of 32 library behaviors (`trait_definition`),
- modifiers, as parameters over one of 16 library behaviors (`modifier_definition`),
- modifier application recipes (`modifier_recipe`), embossing recipes (`embossing_recipe`),
- melting, alloying, casting, entity melting, smeltery fuel and core transform recipes.

That covers the whole content axis of the mod except tools, parts, armor pieces and stations. The surface was built deliberately: the shapes are documented in `docs/SCOPE.md` (M6 section for `trait_definition`, M8 section for `modifier_definition`), the collision rule is uniform (a built-in id always wins), and unknown ids are kept inertly on a stack rather than dropped.

### What a Java addon can do today

Very little, and nothing that was designed for an addon. There is no `dev.gkissel.forgeweave.api` package, no Forgeweave-owned `Event` class anywhere in the tree, and no registry event. The entire Java-side extension surface is three static `register` calls plus one leveling seam:

| Seam | Path | Intentional? |
| --- | --- | --- |
| `CombatSeams.register(Provider)` | `combat/CombatSeams.java:95` | Yes, but internal. Documented as "registered once at mod construction in `Forgeweave`". |
| `UpgradeHosts.register(Host)` | `tool/UpgradeHosts.java:61` | Yes. Names no partner type, `CopyOnWriteArrayList`, javadoc states the contract a host signs. The closest thing in the tree to a public API. |
| `ForgeweaveTraits.registerScripted(id, Trait)` | `trait/ForgeweaveTraits.java:3862` | Half. Built for KubeJS; a Java caller can use it, but the lookup is gated on `compat.kubejsTraits` (`ForgeweaveTraits.java:3846`), which reads as a KubeJS toggle, not an addon toggle. |
| `ToolLeveling.addXp(stack, amount, player)` | `tool/ToolLeveling.java:87` | Yes as a seam, and its javadoc calls it "the one API every XP source calls". A partner mod can grant XP for its own activity today. |

Everything else a Java addon would want is closed. There is no way to register a modifier from Java at all: `ForgeweaveModifiers.REGISTRY` is a private `Map.ofEntries` (`modifier/ForgeweaveModifiers.java:1792`) and the only second source is the datapack snapshot. There is no way to register a trait behavior type, a modifier behavior type, a part kind, a tool, an armor piece, a smeltery wall block, a JEI category or a book page.

The seven in-tree `compat/<mod>/` packages are the strongest evidence of the gap. They are mods-within-the-mod, and not one of them plugs in through a public surface: each is wired by a hard-coded `ModList.isLoaded` branch inside `Forgeweave`'s own constructor (`Forgeweave.java:320-352`). An outside mod cannot join that list.

### The five closed tables that block "a new tool from outside"

A new tool needs all five, plus art. None of them has a seam.

1. **`ToolConstants`** (`tool/ToolConstants.java`) plus **`ToolAssemblyRecipes.ENTRIES`** (`menu/ToolAssemblyRecipes.java:185`). The part list, stat constants and station recipe. `ENTRIES` is `public static final List<Entry> ... = List.of(...)`, immutable, and `Entry#part(int)` resolves each slot's part item as `forgeweave:<partId>` with the namespace hard-coded (`ToolAssemblyRecipes.java:120`), so a foreign part item cannot be named at all.
2. **`PartBuilderRecipes.ENTRIES`** (`menu/PartBuilderRecipes.java:96`). Which pattern makes which part at which cost. Private and immutable.
3. **`StencilTableMenu.PATTERNS`** (`menu/StencilTableMenu.java:68`). Which blank-pattern conversions the Stencil Table offers; the screen sizes itself off this list's length.
4. **`ToolStationTabs.TABS`** (`menu/ToolStationTabs.java:148`). The station's tab buttons and per-tab slot positions, hand-laid pixel coordinates.
5. **`ToolArt`** (`tool/ToolArt.java`). `ROLE_LAYERS` (line 88), `BROKEN_LAYERS` (line 175) and `DRAW_THRESHOLDS` (line 247) are `Map.of`/`EnumMap` keyed by Forgeweave's own tool name strings. A tool absent from `BROKEN_LAYERS` has no broken texture.

Sixth, adjacent: `ForgeweaveItemColors.tintedPartItems` walks `ForgeweaveItems.ITEMS` (`client/ForgeweaveItemColors.java:76`), Forgeweave's own `DeferredRegister`, so a foreign `PartItem` would render untinted even if everything else were opened.

### Recommended order of work

Sizes are S (a day or less), M (a few days), L (a week or more of design plus code). "Conflicts" names open work the change would fight.

| # | Work | Size | Conflicts |
| --- | --- | --- | --- |
| 1 | Create `dev.gkissel.forgeweave.api` and move the four existing seams behind it (`UpgradeHosts.Host`, `CombatSeams.Provider`, `Trait`, `Modifier`, `ToolLeveling.addXp`). Nothing new, just a named package plus a stability note. | S | None |
| 2 | Open the two behavior tables. `TraitBehaviors.register` (line 289) and `ModifierBehaviors.register` (line 198) are already the right shape; make them public and call them from a `RegisterEvent`-style hook. A pack then reaches an addon's behavior through the existing `trait_definition` codec with no new registry. | S | None |
| 3 | Add a Java modifier registration seam mirroring `registerScripted`, and split the `compat.kubejsTraits` gate so a Java-registered trait is not switched off by a KubeJS toggle. | S | #968's toggle roster |
| 4 | Widen `ToolConstants.PartSlot#partId` from `String` to `ResourceLocation`. Unblocks foreign part items in every table at once. | S | Every open tool ticket touching `ToolConstants` |
| 5 | Put a registry or an event in front of `PartBuilderRecipes.ENTRIES`, `StencilTableMenu.PATTERNS` and `ToolAssemblyRecipes.ENTRIES`. Same shape for all three: keep the shipped `List.of` as the seed, append registered rows. | M | Any in-flight tool or part PR editing those lists |
| 6 | Make `ToolStationTabs.TABS` derive its slot geometry from a part count rather than hand coordinates, so a registered tool gets a tab without pixel work. | M | Station GUI work |
| 7 | Make `ToolArt` fall back by convention (`<namespace>:item/<tool>_broken`, role layers by `Role`) instead of failing on an unlisted tool. | M | The Forged/Legacy sprite rollout (#796) |
| 8 | Open `SmelteryScan.Valid` (`block/SmelteryScan.java:313-364`) from `Set.of` of concrete blocks to block tags. | S | Nothing open |
| 9 | Make the book readable from the resource manager instead of the mod classpath (`client/book/BookStructure.java:117`), so a pack or addon can add a section. | L | The book's own layout code, which assumes the shipped section set |
| 10 | KubeJS recipe schemas. There are none today: `ForgeweaveKubeJSPlugin` (78 lines) registers one event group and one trait event, nothing else. | M | None |

Items 1 to 4 and 8 are each small and independent. Items 5 to 7 carry the "a new tool from outside" goal and should land together: a tool that assembles but has no pattern to build its parts from, no station tab and no broken texture is not usable.

## Per extension point

### Materials and part stats

| | |
| --- | --- |
| **Datapack** | Yes, fully. `data/<ns>/forgeweave/material/<name>.json`, codec `Material.CODEC` (`material/Material.java:499`), registry key `Material.REGISTRY` (line 99). 180 shipped files under `src/main/resources/data/forgeweave/forgeweave/material/`; example `iron.json`. Head, handle, extra, bow, bowstring, shaft, fletching and plating stat blocks are all fields on the record (lines 81-97). `neoforge:conditions` gates existence, and `LENIENT_INGREDIENT_CODEC` (line 317) lets a material name an item another mod owns without failing the load when that mod is absent. |
| **KubeJS** | No binding. A KubeJS pack writes the same JSON. |
| **Java** | No registration seam, and none needed: a mod ships the JSON in its own `data/` folder, which is the documented path (CONTEXT.md invariant: "adding a material must never require Java code unless it needs a new Trait behavior"). |
| **Closed** | Nothing, for the material itself. Item forms are a separate closed table, see below. |
| **Art** | Free. Parts are greyscale sprites tinted by the material's `color` field (`client/ForgeweaveItemColors.java:91`), so a datapack material needs no art. |

### Item forms for a material (ingot, nugget, dust, plate, gear, wire, ore chain)

| | |
| --- | --- |
| **Datapack** | No. `material/MaterialForms.java` builds `ALL` (line 66) by walking `TrackBOre.ALL` and `TrackBAlloy.ALL` plus a ten-entry `OWN_ITEM_METALS` `List.of` (line 42). Java table, and every registration, model, lang line, `c:` tag and recipe derives from it. |
| **KubeJS / Java** | No seam. |
| **Note** | Deliberate, per D-M8-6: Track A materials' forms belong to the mods that own them. An addon ships its own items rather than asking Forgeweave to generate them, so this is a low-priority gap. |

### Traits

| | |
| --- | --- |
| **Datapack** | Yes. `data/<ns>/forgeweave/trait_definition/<name>.json`, codec `TraitDefinition.CODEC` (`trait/TraitDefinition.java:44`), which is `TraitBehaviors.CODEC` dispatching on a `behavior` field. 16 shipped files; example `src/main/resources/data/forgeweave/forgeweave/trait_definition/coremend.json`. |
| **KubeJS** | Yes, for logic parameters cannot express. `ForgeweaveEvents.traits` (`kubejs/ForgeweaveKubeJSPlugin.java:52`) hands out a `ScriptTrait` builder whose callbacks mirror the `Trait` hooks. Startup scripts only. Gated on `compat.kubejsTraits`. |
| **Java** | `ForgeweaveTraits.registerScripted` (`trait/ForgeweaveTraits.java:3862`) works from Java, but it is the KubeJS path: it throws on a built-in id, stores into `SCRIPTED`, and the lookup reads it only when `compat.kubejsTraits` is on. A Java addon's trait disappearing because a pack switched off a KubeJS toggle is a bug waiting to be filed. |
| **Closed** | `TraitBehaviors.TYPES` (`trait/TraitBehaviors.java:70`) is a private `LinkedHashMap` filled by a private `register` (line 289). A new behavior type with its own codec needs a Forgeweave code change. The javadoc argues against a registry ("Behaviour classes are Java and only a mod update adds one"), which was true before addons were in scope and is the exact assumption #1008 overturns. Opening it is small: make `register` public and call it from a mod-construction hook. |

### Modifiers

| | |
| --- | --- |
| **Datapack (definition)** | Yes, since #973. `data/<ns>/forgeweave/modifier_definition/<name>.json`, codec `ModifierDefinition.CODEC` (`modifier/ModifierDefinition.java:50`) dispatching over `ModifierBehaviors`. No shipped example file exists yet: `src/main/resources/data/forgeweave/forgeweave/` has no `modifier_definition` folder, so the only worked examples are `docs/SCOPE.md`'s M8 section and the class javadoc. A pack author has nothing in-tree to copy. |
| **Datapack (application recipe)** | Yes, and required. A definition alone never reaches a tool; it needs a `modifier_recipe`. Codec `ModifierRecipe.CODEC`, registry `ModifierRecipe.REGISTRY`; 52 shipped files, example `haste.json`. |
| **KubeJS** | No. Deliberate, recorded in ADR-0004 item 3 and `docs/SCOPE.md` line 799: no `ForgeweaveEvents.modifiers` sibling until a pack author asks. |
| **Java** | No. `ForgeweaveModifiers.REGISTRY` is a private `Map.ofEntries` (line 1792) and `get` (line 1878) consults it then the datapack snapshot (line 1903). There is no third source and no `register` method. A partner mod that wants a modifier must ship a `modifier_definition` JSON, which caps it at the 16 parameterized behaviors. |
| **Closed** | `ModifierBehaviors.TYPES` (`modifier/ModifierBehaviors.java:75`), private, same shape and same fix as `TraitBehaviors`. `ModifierLibrary.Behavior` (line 93) is a public interface, so the type an addon would implement already exists. |

### Part kinds

| | |
| --- | --- |
| **Any** | No. `PartItem.Kind` (`item/PartItem.java:62`) and `ToolConstants.Role` (`tool/ToolConstants.java:94`) are enums. A new stat block would need a new `Material` field too, so "add a part kind" is a schema change, not a registration. |
| **Assessment** | Redesign, not a small change, and probably not worth it. The 11 kinds cover head, handle, extra, bow, bowstring, shaft, fletching, projectile, plating, maille and none. An addon wanting a new stat block is asking for a new stat system. Recommend documenting this as closed rather than opening it. |

### Tool definitions

| | |
| --- | --- |
| **Datapack** | No. |
| **KubeJS** | No. |
| **Java** | No. `ToolAssemblyRecipes.ENTRIES` (line 185) is `public static final List<Entry> ... = List.of(...)`. Public so JEI and `ToolStationTabs` read the same table, not so anything can add to it. |
| **What an outside tool would have to touch** | `ToolConstants` (its `Entry` and part list), `ToolAssemblyRecipes.ENTRIES`, `PartBuilderRecipes.ENTRIES` for each new part, `StencilTableMenu.PATTERNS` for each new pattern, `ToolStationTabs.TABS` for the station button and slot layout, `ToolArt.ROLE_LAYERS`/`BROKEN_LAYERS`/`DRAW_THRESHOLDS`, `ForgeweaveItemColors.tintedToolItems` (which reads `ENTRIES`, so it follows for free), `ContentFamilies` (`menu/ContentFamilies.java:51`, which switches on `ToolConstants.Category`), `jei/AssemblyRecipes` (follows `ENTRIES` for free), and the book's `index.json`. |
| **Assessment** | Item 5 in the order of work. Three of the tables take the same seed-plus-registered treatment; `ToolStationTabs` and `ToolArt` need a convention-over-table pass first, which is the M-sized part. |

### Armor pieces

| | |
| --- | --- |
| **Any** | No, and the surface is narrower than tools: `ArmorItem.Type` is vanilla's four-value enum, so "a fifth armor slot" is not a Forgeweave question. Armor **materials** are datapack-definable today through `Material.plating` and `Material.maille` (`material/Material.java:95-96`), which is the part an addon actually wants. |
| **Recommend** | Document armor piece kinds as closed and armor materials as open. No work. |

### Station recipes

Every one of these is datapack-open, with the codec and one shipped example.

| Recipe | Registry and codec | Shipped example |
| --- | --- | --- |
| Melting | `recipe/MeltingRecipe.java`, registered `Forgeweave.java:372` | 539 files under `melting_recipe/` |
| Alloying | `recipe/AlloyRecipe.java`, `Forgeweave.java:380` | `alloy_recipe/` |
| Casting | `casting/CastingRecipe.java`, `Forgeweave.java:369` | `casting_recipe/` |
| Smeltery fuel | `recipe/SmelteryFuel.java`, `Forgeweave.java:375` | `smeltery_fuel/` |
| Entity melting | `recipe/EntityMeltingRecipe.java`, `Forgeweave.java:377` | `entity_melting_recipe/` |
| Core transform | `recipe/CoreTransformRecipe.java`, `Forgeweave.java:387` | `core_transform_recipe/` |
| Modifier application | `modifier/ModifierRecipe.java`, `Forgeweave.java:366` | 52 files under `modifier_recipe/` |
| Embossing | `modifier/EmbossingRecipe.java`, `Forgeweave.java:383` | `embossing_recipe/` |

Part builder "recipes" are the exception, and are not in this list on purpose: they are not a datapack registry at all, they are `PartBuilderRecipes.ENTRIES`.

**KubeJS:** no schemas for any of the eight. `ForgeweaveKubeJSPlugin` implements `registerEvents` and `afterScriptsLoaded` only; there is no `registerRecipeSchemas` override and no `RecipeSchema` anywhere in the tree. `event.recipes.forgeweave.melting(...)` does not work today. A KubeJS pack can still write the JSON, so this is convenience rather than capability, which is why it sits at item 10.

**Java:** a mod ships the JSON. No seam needed.

### Smeltery wall blocks and tiers

| | |
| --- | --- |
| **Any** | No. `SmelteryScan.Valid` (`block/SmelteryScan.java:313`) holds four `Set.of` collections of concrete `ForgeweaveBlocks` entries: `TANKS` (314), `FLOOR` (319), `IO` (343), `ENERGIZED` (359), with `WALL` derived (361). A partner mod's decorative seared variant cannot be a wall. |
| **Assessment** | Small. Swap each `Set.of` for a block tag, ship the shipped members as the tag's contents. Nothing else in the scan changes. Item 8. |

### Leveling XP sources

| | |
| --- | --- |
| **Java** | Yes. `ToolLeveling.addXp` (`tool/ToolLeveling.java:87`) is public and its javadoc names it the one API every XP source calls. A partner mod granting XP for its own activity works today. |
| **Datapack / KubeJS** | No. The curve is config (`defaultBaseXP`, `levelMultiplier`, `maximumLevels` in `ForgeweaveConfig`), the sources are Java seams. |
| **Assessment** | Already adequate. It only needs to be named in the API package and documented. |

### Trait and modifier behavior types

Covered under Traits and Modifiers above. Both tables are private maps with private `register` methods that already take exactly the right arguments, so opening them is two visibility changes plus a call site.

### Tooltip state lines

| | |
| --- | --- |
| **Any** | Indirect only. `item/ToolTooltip.java` renders what traits and modifiers report through their own hooks, so an addon trait or modifier that returns a tooltip line gets one. There is no separate registry of tooltip contributors, and nothing found in reading suggests one is needed. |

### Book pages

| | |
| --- | --- |
| **Any** | No. `BookStructure.load()` (`client/book/BookStructure.java:74`) reads `appearance.json`, `index.json` and `sections/*.json` through `BookStructure.class.getResourceAsStream` (line 117), explicitly "from the mod classpath, not the resource manager, so a resource pack cannot override" (line 33). A missing file throws. Nothing outside the Forgeweave jar can add a section or a page. |
| **Assessment** | L. Moving to the resource manager is mechanical, but the layout code downstream assumes the shipped section set, and the `staticLangKeys` coverage test (`BookLangCoverageTest`) assumes a closed page roster. Lowest priority of the ten. |

### JEI

| | |
| --- | --- |
| **Any** | No. `ForgeweaveJeiPlugin#registerCategories` (`jei/ForgeweaveJeiPlugin.java:157`) passes 12 category instances to one `addRecipeCategories` call; `registerRecipes` (line 215) builds each list from a named recipe source. A foreign recipe type gets no category. |
| **Mitigation that already works** | An addon's melting, casting, alloy or modifier recipes are in the same datapack registries Forgeweave's own are, so they show up in the existing categories with no work. The gap is only a genuinely new recipe type, which an addon would register with JEI itself under its own plugin. |
| **Assessment** | Low priority. Document the mitigation rather than building a category registry. |

### Jade and WTHIT

| | |
| --- | --- |
| **Any** | No Forgeweave-side seam, and none needed. `jade/ForgeweaveJadePlugin.java` and `wthit/ForgeweaveWthitPlugin.java` register three providers each against Jade's and WTHIT's own plugin systems. An addon registers its own plugin the same way. |

### Config toggles

| | |
| --- | --- |
| **Any** | No. `ForgeweaveConfig` declares every value as a `public static final ModConfigSpec.*Value` field across four specs (`config/ForgeweaveConfig.java:78-90` and onward). An addon cannot add a toggle to Forgeweave's file, and should not want to: it has its own config. |
| **The one real gap** | `ForgeweaveConfigCondition.TOGGLES` (`config/ForgeweaveConfigCondition.java:78`) is a five-entry `Map.of`. The `forgeweave:compat_toggle` condition can therefore only name those five toggles. An addon shipping conditional recipes must use `neoforge:mod_loaded` or write its own condition type, which is fine, so this is documentation rather than work. |

### Upgrade hosts

| | |
| --- | --- |
| **Java** | Yes, and this is the model the rest of the API should follow. `UpgradeHosts.register(Host)` (`tool/UpgradeHosts.java:61`), `Host` (line 42) is a one-method interface over `ItemStack` only, `CopyOnWriteArrayList` for the read-on-every-part-swap access pattern, `clear()` for tests, and a javadoc section headed "The contract a host signs" spelling out four rules. Two in-tree callers (`compat/draconic/modules/DraconicModuleHost.java:100`, `compat/mekanism/modules/MekanismModuleContainer.java:111`) plus two gametest callers. |
| **Caveat** | The registration window is undocumented beyond "during mod construction". Nothing enforces it, and nothing clears it between registrations, so a mod registering twice gets called twice. |

### Art for a new material or tool

| | |
| --- | --- |
| **Material** | Free, as noted above: greyscale plus tint off `Material.color`. |
| **Tool or part** | Not free, and not reachable. `ToolArt` keys everything on Forgeweave's own tool name strings, and `ForgeweaveItemColors.tintedPartItems` (line 76) walks `ForgeweaveItems.ITEMS` rather than the item registry, so a foreign `PartItem` renders untinted. An addon shipping its own fully-painted sprites sidesteps the tint problem but still needs `ToolArt` entries for layers and the broken state. |
| **Sprite pipeline** | `data/sprite/MaterialPartSprites.java` is a deliberate four-material proof slice (line 30), not a general pipeline, and the `scripts/*.py` generators all walk shipped rosters. An addon generates its own art with its own tooling. |

## Closed table inventory

Every hard-coded roster found, what adding a foreign entry would need, and whether opening it is small or a redesign.

| Table | Path and line | Shape | To add from outside | Size |
| --- | --- | --- | --- | --- |
| `ToolAssemblyRecipes.ENTRIES` | `menu/ToolAssemblyRecipes.java:185` | `public ... List.of` | Seed-plus-registered list, plus `PartSlot#partId` widened to `ResourceLocation` | S once `partId` moves |
| `PartBuilderRecipes.ENTRIES` | `menu/PartBuilderRecipes.java:96` | `private ... List.of` | Same treatment | S |
| `StencilTableMenu.PATTERNS` | `menu/StencilTableMenu.java:68` | `public ... List.of` | Same treatment; the screen already sizes off `.size()` | S |
| `ToolStationTabs.TABS` | `menu/ToolStationTabs.java:148` | `public ... List.of` of hand-laid pixel coordinates | Derive slot geometry from part count | M |
| `ToolConstants` entries | `tool/ToolConstants.java` | `public static final Entry` constants | Registration seam, or accept that an addon builds its own `Entry` and registers it with the assembly table | S given the above |
| `ToolConstants.Category` | `tool/ToolConstants.java:70` | enum | Redesign. Feeds the `content` config section through `ContentFamilies` | L, and probably not wanted |
| `ToolConstants.Role` | `tool/ToolConstants.java:94` | enum | Redesign, tied to `Material`'s stat block fields | L, not recommended |
| `PartItem.Kind` | `item/PartItem.java:62` | enum | Same as `Role` | L, not recommended |
| `ForgeweaveModifiers.REGISTRY` | `modifier/ForgeweaveModifiers.java:1792` | `private Map.ofEntries` | A third source in `get`, mirroring `SCRIPTED` for traits | S |
| `ForgeweaveTraits.REGISTRY` | `trait/ForgeweaveTraits.java:3602` | `private Map.ofEntries` | Already has a third source (`SCRIPTED`); only the config gate needs splitting | S |
| `TraitBehaviors.TYPES` | `trait/TraitBehaviors.java:70` | `private LinkedHashMap`, private `register` at 289 | Make `register` public, call it from a registration hook | S |
| `ModifierBehaviors.TYPES` | `modifier/ModifierBehaviors.java:75` | same, `register` at 198 | same | S |
| `ToolArt.ROLE_LAYERS` | `tool/ToolArt.java:88` | `EnumMap` over `Role` | Falls out once `Role` is accepted as closed; no work | none |
| `ToolArt.BROKEN_LAYERS` | `tool/ToolArt.java:175` | `Map.ofEntries` keyed by tool name string | Convention fallback `<ns>:item/<tool>_broken` | M |
| `ToolArt.DRAW_THRESHOLDS` | `tool/ToolArt.java:247` | `Map.of` keyed by bow name | Same fallback shape, or a field on the tool's entry | M |
| `SmelteryScan.Valid.*` | `block/SmelteryScan.java:313-364` | four `Set.of` of concrete blocks | Block tags | S |
| `ForgeweaveJeiPlugin` categories | `jei/ForgeweaveJeiPlugin.java:157` | one `addRecipeCategories` call | An addon registers its own JEI plugin; no Forgeweave change needed | none |
| `ForgeweaveConfigCondition.TOGGLES` | `config/ForgeweaveConfigCondition.java:78` | five-entry `Map.of` | An addon writes its own `ICondition`; document it | none |
| `MaterialForms.OWN_ITEM_METALS` / `ALL` | `material/MaterialForms.java:42,66` | `List.of` plus roster walk | Deliberate per D-M8-6; document as closed | none |
| Book structure | `client/book/BookStructure.java:74,117` | classpath-only JSON | Resource-manager load | L |
| Compat wiring | `Forgeweave.java:320-352` | `ModList.isLoaded` branches in the constructor | Nothing outside the jar can join the list, which is why no in-tree integration is evidence of a working addon path | covered by items 1 to 3 |
| `ForgeweaveItemColors.tintedPartItems` | `client/ForgeweaveItemColors.java:76` | walks `ForgeweaveItems.ITEMS` | Walk `BuiltInRegistries.ITEM` instead | S |

## Test findings

The hand-maintained rosters a test addon would fight, and what each one means.

Three of the four rosters named in the issue do not assume a closed world in the way the issue expected:

- `MaterialTest` (`src/test/java/.../material/MaterialTest.java`) walks `src/main/resources/data/forgeweave/forgeweave/material` from disk (lines 853, 1018, 1114). It reads Forgeweave's own shipped files, not a registry, so an addon's materials are invisible to it. It cannot fail because of an addon. Its `noTwoMaterialsShareANonExemptTraitId` (line 1017) would be worth extending to a test addon's own folder, but that is an addition, not a conflict.
- `ArmorMaterialTest` (line 164) does the same walk over the same folder. Same conclusion.
- `JeiRecipesScaleTest` (line 55) builds its material map from the same shipped folder and asserts a 250 ms budget on building three display-recipe lists. An addon's materials do not enter the measurement. The real-world risk is the opposite one: the budget was measured against Forgeweave's own roster, so an install with several addons would have display lists larger than anything CI measures. Worth a note in `docs/addons.md`, not a code change.

**The one that genuinely assumes a closed world:** `MaterialSyncSizeTest` (`src/test/java/.../material/MaterialSyncSizeTest.java:148`) asserts the summed registry-sync payload of the shipped roster stays under a 136 KB budget. The budget's own javadoc (lines 40-129) is a chronology of revisions as the roster grew, each one recording "no further planned growth to budget for". The test itself is safe, because it also walks only the shipped folder. The **budget** is not: it is a whole-roster budget on a payload that in a real world includes every addon's materials too, and nothing measures or enforces that. A test addon adding one material proves nothing about it.

`ArmorModifiersTest` walks `ForgeweaveModifiers.ids()` (line 86), which returns `REGISTRY.keySet()` only (`ForgeweaveModifiers.java:1864`) and so excludes datapack and addon modifiers by construction. Its `ARMOR_ONLY` set is a hand list, but it is only ever compared against built-ins. No conflict.

**The precedent a test addon should copy:** the seven `*SourceIsolationTest` classes under `src/test/java/.../compat/` are exactly the "imports only the API package" scan the issue asks for, already written seven times. `DraconicSourceIsolationTest` is the clearest: it resolves the project root by walking up for `settings.gradle` (line 35), scans `src/main/java`, `src/test/java` and `src/gametest/java` for a package prefix (line 49), and allows it under one directory. Inverting it to "the test addon source set may import only `dev.gkissel.forgeweave.api`" is a copy with two strings changed.

**The source set precedent** is already in `build.gradle`: `sourceSets { gametest }` (line 29-31) with its own resources, kept out of `sourceSets.main` so it never reaches the published jar, and a second `runs` binding folding it in (lines 131-139). A `testaddon` source set follows the same three declarations.

## Sync and save compatibility

**What syncs to clients.** All ten datapack registries pass their codec as the network codec (`Forgeweave.java:363-398`), so materials, trait definitions, modifier definitions and all eight recipe registries are sent to every client on login. An addon's content rides the same path with no extra work, and a vanilla-ish client with only Forgeweave installed still receives an addon server's definitions, because the payload is data, not classes.

**The budget.** 136 KB for materials alone (`MaterialSyncSizeTest.java:148`), measured at about 95 KB for a 128-material roster (javadoc line 83). Addon materials add to the real payload and are not measured. This is the single sync risk the audit found.

**What a stack stores.** Three things, all ids:

- modifiers: `List<ModifierEntry>` under `ForgeweaveDataComponents.MODIFIERS` (`item/ForgeweaveDataComponents.java:146`), each entry an id plus a level, per ADR-0004 item 2;
- materials: `ToolMaterials` under `TOOL_MATERIALS` (line 50), one `ResourceLocation` per part slot plus the head/binding/handle triple (`tool/ToolMaterials.java:46`);
- stats: `ToolStats.Stats` under `TOOL_STATS` (line 60), computed at assembly and stored.

**What happens when the defining addon is removed.** Four cases, and three of them are already safe:

| Removed | Effect |
| --- | --- |
| A modifier's addon | The entry keeps its id and level and contributes nothing. `ForgeweaveModifiers.get` returns null, logs once, and keeps the entry (line 1885, javadoc: "Unknown ids are kept, not dropped"). It works again the moment the addon returns. |
| A trait's addon | Same. `ForgeweaveTraits.lookup` (line 3846) returns null for an unknown id and every hook sites treats that as no trait. |
| A material's addon | The finished tool keeps working, because its stats are baked into `TOOL_STATS` at assembly. What breaks is anything that looks the material up live: the tooltip's material name (`item/ToolTooltip.java:230` through `MaterialDisplay.lookup`), repair (which needs the material's repair item), and traits granted by that material. Unassembled **parts** are worse off: `PartItem.hasUnusableMaterial` (`item/PartItem.java:120`) exists precisely to detect this, and the stations refuse them with a message. |
| A tool kind's addon | Outside Forgeweave's control. The item id itself is gone, so vanilla's own behavior applies and the stack is lost. Nothing Forgeweave can do, and worth saying out loud in `docs/addons.md`. |

For pack authors: removing an addon that defined traits or modifiers is safe and reversible. Removing one that defined materials degrades tools rather than breaking them, and strands loose parts. Removing one that defined tools loses them.

## Naming and packaging

**What the public API package would contain.** From the audit, the surface an addon actually needs is small:

- `Trait` (`trait/Trait.java:43`) and `Modifier` (`modifier/Modifier.java:32`), both already public interfaces;
- `ModifierLibrary.Behavior` (`modifier/ModifierLibrary.java:93`), already public;
- `UpgradeHosts.Host` (`tool/UpgradeHosts.java:42`) and the `register` call;
- `CombatSeams.Provider` (`combat/CombatSeams.java:88`), `CombatSeam`, `CombatHit`;
- `ToolLeveling.addXp`;
- the behavior-type registration entry points once items 2 and 3 land;
- `ToolConstants.Entry`, `PartSlot`, `Role` and `PartItem.Kind` as read-only types a tool definition is expressed in;
- `Material` and its stat records, which an addon reads but never constructs.

**Does anything there leak a partner-mod type?** No. Checked directly: the seven `*SourceIsolationTest` classes already prove that no file outside each `compat/<mod>/` package names that mod's types, and every candidate above lives outside those packages. `UpgradeHosts`' javadoc states the property explicitly: "Nothing here names a partner mod's type, so the class is safe to classload on any install." `CompatItems` (`compat/CompatItems.java`) is the one place a partner type is reached, and it reaches it through an indirection for exactly this reason (its "Why the branch is here and the `new` is not" section). The API package would be clean on day one.

**What a semantic-versioning promise could cover.** Three tiers, of which only the first two can be promised firmly today:

1. **Datapack JSON shapes.** Ten codecs, already versioned in practice by their accept-old-write-new posture (`Material`'s `TRAITS_CODEC` at line 434, `ModifierRecipe`'s `reagent`/`reagents` pair). These could carry a real promise: a minor release never removes a field or changes its meaning.
2. **The stack serialization contract.** `modifier id + level`, material ids per part. ADR-0004 item 2 already treats this as immovable and the save-compat fixture corpus is CI-gating. Promisable.
3. **The Java surface.** Not promisable until it exists as a named package with no internals in it. Today's four seams are all reachable, but three of them sit in classes full of internals, so any promise made today would be a promise about `ForgeweaveTraits`, a 3,900-line class. The API package has to land before the promise can be written.

What a promise should explicitly not cover: `ToolConstants`' stat constants (they are parity numbers and the maintainer rebalances them), `ForgeweaveModifiers`' behavior constants, and anything in `client/`.

## Open questions for the maintainer

Each is a concrete choice with a recommendation.

**Q1. Does "a new tool from outside" mean Java-registered or datapack-defined?** The issue asks for both and notes tools may be "Java or KubeJS only" if the model and part tables do not allow data. They do not: a tool needs an `Item` instance, a model with per-layer tint indices, and `ToolArt` layer entries, none of which a datapack can produce. **Recommend Java-registered tools only**, with the tool's stats and part list expressible as data an addon loads if it wants. Say so in `docs/addons.md` rather than leaving it implied.

**Q2. Should the two behavior tables open to Java, or stay closed with the library as the ceiling?** Opening `TraitBehaviors` and `ModifierBehaviors` lets an addon ship a behavior with its own codec and lets pack authors reach it through the existing definition registries, which buys the most for the fewest lines changed. Staying closed keeps the promise simple and keeps every behavior parameterizable. **Recommend opening both**, because the alternative forces every addon trait through `registerScripted`, which is a KubeJS path with a KubeJS toggle in front of it.

**Q3. Should a Java-registered trait be gated on `compat.kubejsTraits`?** Today it is (`ForgeweaveTraits.java:3846`). **Recommend splitting the gate**: keep `compat.kubejsTraits` for script traits, add nothing for Java-registered ones. An addon's content should be governed by the addon's own config, and D-M8-5's rule is that a toggle exists per integration, not per mechanism.

**Q4. One API package or several?** A single `dev.gkissel.forgeweave.api` with subpackages (`api.trait`, `api.modifier`, `api.tool`) versus several top-level ones. **Recommend one package with subpackages**, matching the issue's own naming and making the source-scan test a single prefix check.

**Q5. Does the test addon ship?** The issue says not shipped. The `gametest` source set is the precedent: its own resources, out of `sourceSets.main`, in its own run config. **Recommend the same shape**, named `testaddon`, with its own mod id so a GameTest can assert it loaded as a separate mod.

**Q6. Does the material sync budget become a runtime concern?** `MaterialSyncSizeTest`'s budget guards Forgeweave's own roster and cannot see an addon's. **Recommend leaving the test alone** and adding a documented per-material size guideline plus a startup log line reporting the actual total, so a pack author with six addons can see the number rather than discover it as a login timeout.

**Q7. Does the book open, or is it documented as closed?** Moving `BookStructure` to the resource manager is the largest item in the list and the least asked for. **Recommend documenting it as closed for now** and revisiting when an addon author asks, the same posture ADR-0004 took on the KubeJS modifier builder.

**Q8. What does the stability ADR promise, and from which version?** **Recommend**: datapack JSON shapes and the stack serialization contract from the version the API package lands in; the Java API marked provisional for one minor release, then stable. Waiting one release means the first real addon gets to find the wrong shapes before they are frozen.
