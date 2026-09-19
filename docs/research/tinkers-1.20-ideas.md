# What Tinkers' Construct 1.20 has that Forgeweave could take

Research for issue #1047, asked by the maintainer on 2026-09-18: "see what 1.20 has that we could add that would be cool, and bring me ideas." This document does not change Forgeweave's feature target. The 1.12 generation stays the default (CLAUDE.md, ADR-0003), and every item below needs an explicit maintainer decision before it enters scope, the way the Modifier Worktable and the armor stand preview were picked before.

**Source**: `~/development/minecraft/references/tinkers-1.20` at `b98c7867f7fcb66d3f60a90d30deeeae26ec032f` (MIT). **Comparison target**: `docs/SCOPE.md`, `docs/adr/`, and the source tree under `src/main/java/dev/gkissel/forgeweave/`. Every claim below is marked "read in source" (I opened the file) or "inferred" (reasoned from adjacent evidence, not directly confirmed). No code changes in this PR. No new issues: the maintainer chooses first.

## Ranked summary

**Most player value for least work:**

1. **Modifier sorting** (worktable recipe, no cost, swaps which of two overlapping modifiers runs first): S, pure data/small Java, no conflicts. A one-recipe quality-of-life add once the Modifier Worktable itself exists.
2. **The dagger**: fast, dual-wieldable, stacks to 2, no new mechanics beyond stat tuning on the existing sword item class. S, data-only if Forgeweave's tool item classes already support a stack-to-2 override; otherwise S-M Java.
3. **Modifier crystals + extraction**: take a modifier off a tool as a reusable item, put it on another. M: one new item, a handful of worktable-style recipes. Straightforward within Forgeweave's existing modifier-application recipe family.
4. **Toggling a modifier between left-click and right-click**: small, self-contained mechanic (one NBT list swap) that pays off the moment Forgeweave ships a tool with two candidate interactions (shears, till, fish). M, needs the interaction-hook plumbing 1.20 has and Forgeweave does not yet.
5. **Fluid cannon**: a redstone-triggered block that fires a tool's held fluid as a combat/utility projectile, reusing 1.20's fluid-effects idea. M, needs Forgeweave to have a fluid-effects table first (it doesn't).
6. **Ancient tools as mob loot flavor**: a hidden, uncraftable tier-5 material handed to specific mobs in their equipment loot table. S, no new item classes, cosmetic/flavor value only.

**Big, but worth a milestone if the maintainer wants it:**

7. **The Modifier Worktable as a whole**: modifier removal, extraction, enchantment conversion, sorting, interaction toggling, invisible ink. L. This is the system the maintainer specifically asked about; see the full section below. Composable in slices (removal and sorting are cheap; enchantment conversion needs an enchant-to-modifier-levels ladder Forgeweave has never had to build).
8. **Armor modifier hooks worth having** (`OnAttacked`/thorns-style retaliation, `ArmorWalk`, `ElytraFlight`, `EquipmentChange`): M-L per hook. Forgeweave's M4 armor already has a defense pass (`CombatSeams.defensePass`); most of these are new hook *kinds* layered onto that pass rather than a new armor system.
9. **Channels, ducts, chutes, proxy tanks**: smeltery fluid/item automation peripherals beyond the drain and faucet Forgeweave already has. M each. No conflict with parity (the 1.12 clone has an equivalent channel/faucet layer); mostly new block entities wired to the existing smeltery tank.

**Leave alone, and why:**

- **The Foundry, Scorched block family, and the Alloyer**: a second, larger heating multiblock parallel to the Smeltery. SCOPE.md already declined scorched stone and cinderslime at M6 by name because they need "Foundry-scale prerequisites Forgeweave does not have" (`docs/SCOPE.md:867`). Building the Foundry just to unlock those two materials is the tail wagging the dog; the 1.12 parity target has no Foundry at all.
- **Typed modifier slots (upgrade/ability/defense/soul)**: Forgeweave has one untyped free-slot pool (`ForgeweaveModifiers.freeSlots()`), and the DEFENSE slot type was named and then explicitly closed as a **permanent non-goal** at D-M7-1 (`docs/SCOPE.md:497`). Re-opening slot typing to match 1.20 would touch the modifier architecture ADR-0004 closed at M8 and invalidate that decision for no player-facing win big enough to justify it.
- **Whitestone as its own material**: 1.20 folded endstone into a "whitestone" base material. SCOPE.md already declined this by name: "redundant with the existing `endstone.json`" (`docs/SCOPE.md:867`).
- **The fully data-driven "everything is a JSON module" tool architecture**: 1.20 abandoned per-tool Java classes for a module/JSON composition model. That is an internal engineering choice with no player-facing content of its own; adopting it wholesale is a rewrite, not a menu item, and Forgeweave's current architecture already supports data-driven traits and modifiers (ADR-0002, ADR-0004).
- **Slime armor's material-trait-bleed design and the four slime staffs**: both lean on 1.20's slime subsystem (overslime capacity, remapping materials, dual-option interaction) that has no 1.12 analog and no Forgeweave equivalent yet. Slime gadgets are already scoped as a future milestone (`docs/SCOPE.md:848`, `#487`); revisit staffs there, not from this doc.
- **More slime island variants, geodes, and other world content**: Forgeweave already generates slime islands and magma slime islands (`worldgen/SlimeIslandStructure`, `worldgen/MagmaSlimeIslandStructure`, with their `genSlimeIslands` config family). The SCOPE.md lines that call islands a non-goal (`docs/SCOPE.md:75`, `:247`) are early-milestone text that the code has since overtaken. Upstream's extra variants (sky, ocean sky, clay, end) and its geodes have no milestone here, and nothing in this document changes that.
- **Smithing trims**: a vanilla-1.20-only integration point with no 1.12 analog at all.

---

## The Modifier Worktable

*All facts below are read in source at `b98c7867` unless marked inferred. Paths are relative to `~/development/minecraft/references/tinkers-1.20` unless given absolutely.*

### What it is

The Worktable (`tconstruct:tables/modifier_worktable`) is a single block whose recipes all "modify your modifiers" (its own book blurb). Seven recipe families share one base class, `AbstractWorktableRecipe` (`src/main/java/slimeknights/tconstruct/library/recipe/worktable/AbstractWorktableRecipe.java`), which supplies a default ingredient/tool-tag match, a "which modifiers on this tool are eligible" query, and input consumption. Every recipe type below overrides pieces of it.

One fact worth flagging up front: the Worktable only ever operates on **recipe-added modifiers** (`tool.getUpgrades()`), not on inherent material traits (`tool.getModifiers()`). A modifier baked into the material never shows up as a worktable target.

Forgeweave has no equivalent station today. It has a Tool Station/Tool Forge for building and modifying tools, a datapack `modifier_definition` registry (M8, ADR-0004 item 3) for defining modifier *behavior*, and application recipes that add a modifier to a tool: but nothing that removes, extracts, converts, reorders, or hides a modifier already on a tool.

### 1. Modifier removal

**Class**: `tools/recipe/ModifierRemovalRecipe.java`, serializer `tconstruct:remove_modifier`. Two shipped recipes: a reusable wet sponge (returns as a plain sponge, `remove_modifier_sponge.json`) and venom (`remove_modifier_venom.json`, fully consumed, no leftovers). Both remove exactly one level of any modifier not on a removal blacklist tag, from any `tconstruct:modifiable` tool/armor piece. The modifier is destroyed, not refunded. The book says so outright: "the material you used to apply the modifier is lost forever."

**What Forgeweave would need**: a new worktable-style recipe type (input item + tool + target-modifier selection → tool minus one level), reusing the existing modifier-removal machinery any "remove a bad roll" flow would already need. No slot types required. **Size S**, data + a small recipe class.

### 2. Extraction into crystals

**Class**: `tools/recipe/ExtractModifierRecipe.java`, extends removal. Gives back a `ModifierCrystalItem` (`tools/item/ModifierCrystalItem.java`) instead of the removal recipe's plain leftovers: a single dynamic item whose NBT stores a `ModifierId`, foil-glinted, tooltip describing the wrapped modifier. Upstream gates the crystal type by slot: `sky_slime_crystal` for upgrades, `earth_slime_crystal` for defense, `ichor_slime_crystal` for abilities, `amethyst_shard` for slotless, `ender_slime_crystal` for anything. Each family also has a "dagger" variant costing 2 daggers instead of a modifiable tool: a cheap bulk-extraction path.

**Re-applying a crystal**: the only mechanism found is a **creative/OP shift-click-stack** shortcut (`ModifierCrystalItem.overrideStackedOnOther`), gated by `player.isCreative()` or a `quickApplyToolModifiersSurvival` config + permission check. **No survival-mode worktable recipe that consumes a crystal to re-apply its modifier was found** in the worktable recipe folder (`gaps` section below flags this explicitly: it may live in `library/recipe/modifiers/adding/`, which the survey did not fully open). If Forgeweave wants a genuine survival crystal-swap loop, this needs a follow-up read of that folder before assuming the mechanic exists upstream at all.

**What Forgeweave would need**: one new item (`ModifierCrystalItem`-equivalent), one extraction recipe type, and, if the re-apply-in-survival gap turns out to be real upstream too, a new "apply crystal" recipe Forgeweave would have to design itself rather than port. Because Forgeweave has no typed slots, the five slot-gated crystal variants collapse to one generic crystal type; no slot-type predicate needed. **Size M.**

### 3. Enchantment converting

**Class**: `tools/recipe/EnchantmentConvertingRecipe.java`, serializer `tconstruct:enchantment_converting`. Converts an enchanted book or an enchanted tool into a modifier crystal, at a cost scaling with enchantment level (currency tag × level, e.g. 3 lapis per level for the "upgrades" family, 1 diamond for "abilities", 1 gold ingot for "defense", 1 amethyst shard for "slotless"). A separate "unenchant" variant (5 dragon's breath) strips one level of an enchantment and returns the partially-disenchanted book/tool instead of destroying it outright.

**What Forgeweave would need**: this only makes sense once Forgeweave has a mapping from vanilla enchantment to modifier, i.e. an enchantment-to-modifier ladder like upstream's, which does not exist today. Forgeweave already has `allowVanillaEnchanting` (a config flag letting vanilla enchantments coexist with modifiers, consuming no modifier slot; see `docs/SCOPE.md:564`). Converting an enchantment *into* a modifier crystal is a different mechanic from allowing enchantment and modifiers to coexist, and would need its own per-enchantment mapping table. **Size L**, mostly because of the mapping table, not the recipe machinery.

### 4. Modifier sorting

**Class**: `tools/recipe/ModifierSortingRecipe.java`, serializer `tconstruct:modifier_sorting`. Single input: a compass, **never consumed**. Swaps a chosen modifier's position with its neighbor in the tool's ordered modifier list: matters because when two modifiers hook the same effect (two on-hit effects, say), list order decides which runs first. Slot in the recipe grid (upper vs. lower) picks direction. Needs at least 2 modifiers on the tool.

**What Forgeweave would need**: `ForgeweaveModifiers` already stores modifiers as an ordered list (needed for `freeSlots()`'s additive bonuses and the general hook-merge pattern). Exposing a swap-order recipe is a small, self-contained add: one recipe class, no new items beyond a vanilla compass, no slot-type dependency. **Size S.** This is the single cheapest, most self-contained item on the whole list.

### 5. Toggling interaction modifiers between left-click and right-click

**Class**: `tools/recipe/ToggleInteractionWorktableRecipe.java`, serializer `tconstruct:toggle_interaction`. Input: a lever, fully consumed. Only applies to modifiers tagged `tconstruct:dual_interaction`: a fixed list (`bucketing`, `splashing`, `glowing`, `firestarter`, `stripping`, `tilling`, `pathing`, `shears`, `silkyShears`, `harvest`, `fishing`, `slimeball`, `sliver`, `pockets`) that can fire on either left-click or right-click depending on a per-tool NBT flag. One lever use flips which click triggers the modifier.

**What Forgeweave would need**: this requires the underlying interaction-hook split 1.20 has (`GeneralInteractionModifierHook`/`BlockInteractionModifierHook`/`EntityInteractionModifierHook`, plus a per-tool persisted "which side is this modifier on" list), none of which Forgeweave has today, because Forgeweave has none of the dual-purpose ability modifiers (fishing, shearing, tilling) this toggle exists to configure. This is a prerequisite-gated item: build the interaction modifiers first (see the modifier-system section below), then this recipe is cheap. **Size M once the prerequisite hooks exist, otherwise this item doesn't apply.**

### 6. Invisible ink

**Class** (shared with the toggle recipe's plumbing): `library/recipe/worktable/ModifierSetWorktableRecipe.java`, a generic "toggle a modifier ID into/out of a named NBT set" recipe. Adding: pour a sky slime bottle on the tool. Removing: a bucket of milk. The modifier's tooltip and gameplay effect are untouched: only its rendered model overlay is skipped when baking the tool's quads (`ToolModel.addModifierQuads`), so the tool looks like it doesn't have the modifier while still behaving like it does. `allow_traits: false` means it can only hide recipe-added modifiers, not material traits.

**What Forgeweave would need**: a client-side render hook that can skip a specific modifier's layer when baking a tool's model, plus the same generic NBT-set toggle recipe as above. Cosmetic only, no combat/balance implications. **Size M**, mostly client-side tool-rendering work; Forgeweave's tool rendering pipeline was not inspected in this pass, so the actual size depends on how modifier layers are currently composited (flag for follow-up before sizing this more precisely).

### 7. The part-swap / recycling refusal rule, compared with Forgeweave's part exchange

Two independent 1.20 call sites enforce the same rule: **a tool carrying any recipe-added modifier cannot be recycled back into parts or sacrificed as raw material for someone else's part swap, until the modifiers are cleared.**

- **Recycling** (`tables/recipe/PartBuilderToolRecycle.java`): the recycling recipe simply refuses to match if `tool.getUpgrades()` is non-empty, and surfaces a translated error (`recipe.tool_recycling.no_modifiers`) in the Part Builder UI.
- **Part/material swap** (`library/recipe/tinkerstation/building/ToolMaterialSwappingRecipe.java`): a tool offered up as the "sacrifice" supplying a new material must itself be modifier-free, or the recipe fails with `recipe.part_swapping.no_modifiers`. JEI won't even suggest a modifier-bearing tool as a sacrifice option.

Both routes check the same underlying signal (`ToolStack.getUpgrades()` emptiness), just duplicated across two recipe classes rather than shared through one guard method: upstream did not "fix once, all callers route through it" here, worth noting as an observation rather than a pattern to copy.

**Comparison with Forgeweave's part exchange**: Forgeweave took the opposite design choice, deliberately. The maintainer's 2026-09-18 rule (`docs/SCOPE.md:695`) is that *upgrades are never lost to a part swap*: replacing a part that would invalidate an installed upgrade returns the upgrade as items to the player rather than silently destroying it or blocking the swap outright, through the `dev.gkissel.forgeweave.tool.UpgradeHosts` seam. Where 1.20 **blocks** modifier-bearing tools from being swapped or recycled at all, Forgeweave **allows** the swap and **refunds** the modifier. Porting 1.20's refusal rule outright would directly conflict with this recorded decision; if the maintainer wants any part of 1.20's behavior here, it would need to be a new, explicitly-chosen exception to the refund rule, not a straight port.

### Gaps carried over from the original research pass

The survey that produced sections 1–7 above flagged two things worth checking before implementation, not just noting here:

1. Whether a **survival-mode worktable recipe** (as opposed to the creative-only click-to-apply) exists anywhere to consume a modifier crystal and re-apply its modifier to a tool. Not found in `tools/recipe/*worktable*`; the next place to look is `library/recipe/modifiers/adding/`, which this pass did not open. If it doesn't exist upstream either, that's a genuine gap in the 1.20 design Forgeweave would have to fill itself, not port.
2. Three of five extraction "dagger" variant JSONs and three of six enchantment-converting "_book" variant JSONs were not individually opened; their contents are inferred from a strongly consistent sibling-file pattern, not confirmed file-by-file.

---

## Stations and tables

| Candidate | What it is | 1.20 path | Forgeweave status | Size | Data/Java | Conflicts |
|---|---|---|---|---|---|---|
| Anvil vs. Tinker Station | Same block entity/menu, just more slots (4 vs. 6) and a tougher block, not a different recipe system | `tables/block/{TinkerStationBlock,TinkersAnvilBlock}.java` | Forgeweave already has this exact pattern: Tool Station (small) and Tool Forge (large-tool tier, 5% cheaper repairs, required for broad tools, `docs/SCOPE.md:105`) | n/a | n/a | None; Forgeweave already matches this design, nothing to take |
| Part Chest | 8-per-slot passive storage for tool parts, manual insert/retrieval | `tables/block/entity/chest/PartChestBlockEntity.java` | Forgeweave already ships a Part Chest (M1, `docs/SCOPE.md:28`) | n/a | n/a | None |
| Cast Chest | 4-per-slot passive storage for casting molds | `tables/block/entity/chest/CastChestBlockEntity.java` | Not found in Forgeweave's shipped block list | S | Data + a small block entity, same pattern as the existing Part Chest | None; purely organizational |
| Scorched block family / Foundry / Alloyer | A parallel, larger-capacity heating multiblock to the Smeltery, with byproducts instead of self-alloying (needs a separate Alloyer block) | `smeltery/TinkerSmeltery.java`, `smeltery/block/entity/controller/{FoundryBlockEntity,AlloyerBlockEntity}.java` | Not present. SCOPE.md declined scorched stone/cinderslime by name at M6 for lacking "Foundry-scale prerequisites" (`docs/SCOPE.md:867`) | L | Java (new multiblock structure detection, new tank/byproduct modules) + data | Conflicts with 1.12 parity (no Foundry in 1.12) and a recorded SCOPE decision; leave alone unless the maintainer wants a real exception |
| Melter | Single-block melting station, no alloying, 24-ingot tank, melts 3 items/tick-cycle, fuel from a tank or a Heater below it | `smeltery/block/entity/controller/MelterBlockEntity.java` | Not present as a standalone block; Forgeweave's smeltery already covers early melting | M | Java (a `TinyMultiblockControllerBlock`-style single-block station) + data | None functional; a "starter melter" is a reasonable early-game QoL add, low priority since the Smeltery already exists at M2 |
| Casting Tank | Auto-fills/-drains small fluid containers (buckets, bottles) from an internal tank via redstone-triggered slot swap | `smeltery/block/CastingTankBlock.java` | Not present | S-M | Java (item-slot automation logic) + data | None |
| Channel | Standalone fluid-transport pipe block, splits flow across multiple "OUT" neighbors | `smeltery/block/ChannelBlock.java` | Not present; SCOPE.md lists "slime channels" as a deferred *gadget* item (`docs/SCOPE.md:848`, different concept: an item, not this fluid pipe) | M | Java (per-side connection state + tick-based fluid push) | None |
| Duct | Filtered fluid extractor attached to the smeltery, pulls one player-chosen fluid via a filter-item slot | `smeltery/block/component/SearedDuctBlock.java` | Not present | S-M | Java (extends existing smeltery-servant pattern) | None |
| Drain | Unfiltered fluid in/out servant of the smeltery | `smeltery/block/component/SearedDrainBlock.java` | **Already shipped**: Forgeweave's M2 smeltery has a drain (`docs/SCOPE.md:71`) | n/a | n/a | None |
| Chute | Item in/out servant of the smeltery, hopper-friendly | `smeltery/block/entity/component/SmelteryInputOutputBlockEntity.java` (`ChuteBlockEntity`) | Not present | S | Java (item-handler variant of the existing servant pattern) | None |
| Fluid cannon | Redstone-triggered block that sprays a held fluid as a projectile/beam applying "fluid effects" | `smeltery/block/entity/FluidCannonBlockEntity.java` | Not present; depends on a fluid-effects table Forgeweave doesn't have (see Fluids section) | M | Java (projectile entity + redstone trigger) + data | Prerequisite-gated on the fluid effects system below |
| Proxy tank | Lets a variable-capacity fluid item (a tool with a tank modifier) sit in a slot and be filled/drained by faucets/channels as if it were a tank block | `smeltery/block/entity/ProxyTankBlockEntity.java` | Not present; Forgeweave's energized tank (D-M8-11) is a different concept: a fixed smeltery-wall fuel/energy tank, not an item-fluid adapter | S-M | Java (dual item/fluid capability wrapper) | None |

---

## Tools and gear

1.20's tool/armor architecture is fully data-driven (`ToolDefinition` JSON + a small set of generic item classes), a first-order divergence from a per-tool-class model, independent of which specific tools exist (read in source, `tools/TinkerTools.java`). That architectural choice is not itself a candidate: Forgeweave already has data-driven traits/modifiers via ADR-0002/ADR-0004 without needing to drop per-tool classes.

| Candidate | What it is | 1.20 path | Forgeweave status | Size | Conflicts / art |
|---|---|---|---|---|---|
| The dagger | Fast (attack_speed 2.0, fastest melee weapon in the mod), weak-per-hit, stacks to 2, dual-wieldable via a trait. **No backstab/sneak-attack logic anywhere**: a targeted search of the trait packages returned zero hits | `tools/item/ModifiableSwordItem.java`, `tool_definitions/dagger.json` | Not shipped. SCOPE.md's M3 combat-modifier deferred list does not mention a dagger by name | S | No parity conflict (1.12 has no dagger, but nothing bars adding one); new item model needed (small blade + handle, no upstream art to derive since it's a stat variant of the sword shape) |
| Slime staffs + sling ability modifiers | Four material-flavored utility tools (earth/sky/ichor/ender) that block like a shield and host swappable "sling" ability modifiers (knockback, self-launch, teleport) | `TinkerTools.java` lines 218-221, `tools/modules/interaction/sling/*.java` | Explicitly deferred: "staffs" appears in the M3.5-adjacent deferred list (`docs/SCOPE.md:163`) | L | Needs 1.20's slime/overslime subsystem Forgeweave doesn't have; leave to the gadget milestone that already owns this |
| Flint and brick | Disposable fire-starter, crafted from vanilla flint + a Smeltery byproduct brick, not part-built | `tool_definitions/flint_and_brick.json` | Explicitly deferred: "gadget-shaped, revisit at M5" (`docs/SCOPE.md:163`) | S | Already scoped for a later milestone; nothing new to add here |
| Plate armor | Part-built (2 parts/piece: plating + maille), defense-focused, no ability slots | `ArmorDefinitions.java`, `tool_definitions/plate_*.json` | Forgeweave's M4 heavy armor already matches this shape (plating + maille, Tool Forge-only) per the M4 playtest script (`docs/SCOPE.md:307`) | n/a | None; already at parity |
| Travelers' gear | Fixed-material (rose_gold + leather), the only set with an ability slot on body pieces: the "light/utility" tier | `tool_definitions/travelers_*.json` | Forgeweave's M4 light armor set (assembled at the Tool Station) is the parity-target equivalent | n/a | None; already at parity |
| Slime armor / slime wings | Fixed/remapped-material set where the slime material's own traits bleed into the armor; no defense slots, all ability/upgrade | `tool_definitions/slime_*.json`, `slime_wings.json` | Not present; depends on the slime subsystem above | L | Same slime-subsystem prerequisite as staffs; leave to the gadget/slime milestone |
| Shields (plate, travelers) | Their own tool category, block stats, usable as a melee weapon while blocking | `tool_definitions/{plate,travelers}_shield.json` | Not confirmed present; SCOPE.md's M4 armor section doesn't mention shields | M | No conflict; a natural pairing with the existing light/heavy armor tiers if the maintainer wants a shield category |
| Ancient tools | Themed mob-only loot gear (melting pan, war pick, battlesign, swasher, minotaur axe) backed by a hidden, uncraftable tier-5 material handed out in specific mobs' equipment loot tables | `TinkerTools.java` lines 224-236, `materials/definition/ancient.json` | Not present | S | No parity conflict; needs the mob-equipment loot hook (`MobEquipmentManager`) 1.20 has and Forgeweave would need to build or approximate; no new art needed if reusing existing tool models |
| Sweeping (sword/cleaver only) | A base `sweep_melee` module plus the vanilla Sweeping Edge enchantment mapped to a modifier | `library/tools/definition/module/weapon/SweepWeaponAttack.java` | Not confirmed present as a Forgeweave modifier | S | Combat modifiers generally are deferred backlog at M3 (`docs/SCOPE.md:88`) |
| Broad tool family (excavator, sledge hammer, vein hammer, broad axe, scythe, cleaver) | Large multi-part tools with AOE variants (box/circle/vein/tree) | `TinkerTools.java` "broad tools" group | Forgeweave's Tool Forge already gates "broadest of tools" (`docs/SCOPE.md:105`); specific tool identities not verified in this pass | M per tool | Likely already scoped generically under the Tool Forge's broad-tool umbrella; verify against the shipped tool roster before treating as new |
| Crossbow / longbow (parts: `bow_limb`, `bow_grip`, `bowstring`, no "bolt core") | Standard ranged weapons, longbow uses two limb parts instead of a unique part type | `tool_definitions/{crossbow,longbow}.json` | Forgeweave's M3.5 ranged family already plans shortbow/longbow/crossbow (`docs/SCOPE.md:163`) | n/a | Confirms Forgeweave's planned part shape roughly matches upstream's; not a new candidate, a confirmation to check against when M3.5 ships |

---

## Modifier system ideas

### Typed slots (upgrade/ability/defense/soul)

An open, extensible registry (`SlotType.getOrCreate`), not an enum: four built-ins baked flatly into each tool/armor definition (e.g. every "small" tool gets 3 upgrade + 1 ability slots, no defense; plate armor gets 2 upgrade + 3 defense). **Forgeweave status**: explicitly declined. `ForgeweaveModifiers.freeSlots()` is a single untyped pool, and D-M7-1 (`docs/SCOPE.md:497`) closed the door on a DEFENSE slot type as a **permanent non-goal**, specifically because leveling grants into the one existing pool. Adopting typed slots would reopen a closed architectural decision for a mechanic (slot-type-gated recipes) whose only real consumer in 1.20 is the Worktable's per-slot crystal/enchant-conversion variants: which, per the Worktable section above, Forgeweave can implement generically (one crystal type, no slot filter) without typed slots at all. **Leave alone.**

### Modifier crystals and creative slots

Covered in the Worktable section (crystals) above. The creative-only slot item (`CreativeSlotItem` + `CreativeSlotModifier`) is a debug/testing tool that retroactively grants arbitrary slots of any type to any tool: **size S**, low priority, dev-tool value only, no player-facing content.

### Incremental modifiers

The norm in 1.20, not an edge case: a modifier level is subdivided into N "applications" (e.g. sharpness needs 36 quartz gems per level, up to level 5), tracked via `amount`/`needed`/`effectiveLevel` fields feeding a fractional stat scale before the level completes. **Forgeweave status**: not confirmed present as a general mechanic; Forgeweave's M6/M8 leveled traits (radioactive I-III, aftershock I-III) are whole-level, not partial-progress. **Size M** if Forgeweave wants partial-progress application recipes generally, since it's a new recipe/NBT shape, not just a data value. Worth doing only if a specific modifier calls for gradual investment (e.g. an expensive upgrade a player buys into over many applications): otherwise it adds recipe-authoring complexity for no clear win over Forgeweave's existing flat-cost application recipes.

### Interaction modifiers and toggle modifiers

The `GeneralInteractionModifierHook`/`BlockInteractionModifierHook`/`EntityInteractionModifierHook` family, plus the toggle mechanic covered in the Worktable section. Fourteen modifiers use this in 1.20 (firestarter, harvest, shears/silky shears, fishing, tilling/pathing/stripping, bucketing, splashing, glowing, slimeball, sliver, pockets). **Forgeweave status**: none of these dual-purpose ability modifiers exist. **Size L** for the underlying hook split (a genuine new modifier-hook category), **S** per individual modifier once the hook exists. This is a real prerequisite for several other items on this list (the toggle recipe, tilling/shearing/fishing as modifiers at all): if the maintainer wants any of the ability-modifier family, size the hook infrastructure once and amortize it across all of them rather than building it per-modifier.

### Armor modifiers worth having

Two Java packages worth mining for ideas, all implementing hooks Forgeweave's `CombatSeams.defensePass` (M4) doesn't yet expose:

| Modifier | Effect | Hook needed |
|---|---|---|
| Thorns (upgrades/armor) | Reflects 1-4 damage back to the attacker, scaled by level | `OnAttackedModifierHook` |
| Springy (upgrades/armor) | +0.5 knockback resistance/level, 25%/level chance to fully negate knockback | existing attribute pipeline |
| Ambidextrous (ability/armor) | Enables offhand attacks | existing equipment/attack pipeline |
| Double jump (ability/armor) | +1 extra jump while worn | existing movement hooks |
| Flamewake (ability/armor) | Sets ground on fire while walking (Frost Walker-analog for fire) | `ArmorWalkModifierHook` (boots-slot only) |
| Zoom (ability/armor) | Spyglass-style zoom on hold | client-only, no combat hook |

**Forgeweave status**: none confirmed shipped as modifiers; Forgeweave's M4 armor has a defense pass but the specific hook contracts (`OnAttackedModifierHook`, `ArmorWalkModifierHook`, `ElytraFlightModifierHook`, `EquipmentChangeModifierHook`) were not found. **Size M per hook family**, S per modifier once a hook exists. Thorns and Springy are the cheapest, highest-value adds since they only need the existing attribute/attack pipeline, not a new hook kind.

### Modifier hooks for fishing, shearing, tilling, lighting

Read in source against the full `ModifierHooks.java` registry (an exhaustive enumeration, so absence is a reliable negative signal, not an unlucky grep):

- **Shearing** gets a dedicated observer sub-hook (`SHEAR_ENTITY`) other modifiers can hook into after a shear succeeds.
- **Tilling/pathing/stripping** share one generic `BLOCK_TRANSFORM` sub-hook.
- **Fishing** and **lighting** have **no dedicated sub-hook at all**: both are direct producers of the general interaction hooks, an asymmetry upstream itself has, not something Forgeweave would be missing relative to a "complete" design.

**Forgeweave status**: none of these four modifiers exist. **Size M-L as a set** (mostly the interaction-hook prerequisite above); low urgency without a concrete tool that needs them (a hoe-equivalent, shears-equivalent, or fishing rod, none of which Forgeweave has shipped or scoped yet outside the flint & brick / fishing rod gadget-milestone mention).

---

## Materials, traits, and material stat types

This area is large in 1.20 (roughly 170 materials across four tiers plus a slimesuit-exclusive subsystem) and mostly out of scope for a menu-of-ideas doc, since Forgeweave already has its own Track A/Track B material expansion plan at M6/M8 (`docs/SCOPE.md`, `docs/research/m6-material-expansion-references.md`). The specific mechanics worth flagging:

| Candidate | What it is | Forgeweave status | Size | Notes |
|---|---|---|---|---|
| `PlatingMaterialStats` (5 stat types: helmet/chestplate/leggings/boots/shield, one Java record) | Armor plating durability/armor/toughness/knockback | Forgeweave already derived this in full for M4 (per the task's own framing) | n/a | Confirms Forgeweave's model matches upstream's field list and builder shape |
| "Maille"/"bowstring" as presence-only marker stats | Zero-field `StatlessMaterialStats` entries that only gate which materials are legal for a slot, contribute no numbers | Not directly comparable; Forgeweave's material-stat model wasn't audited in this pass | n/a | Worth knowing this is a systemic 1.20 pattern (9 marker-only stat types) before assuming every "stat type" in the source carries numbers |
| Repair kit as two separate mechanisms (a presence flag + a real `RepairStats` value class for ribcage/shell/laces) | Slimesuit-specific repair-pool stat | Not applicable; Forgeweave has no slimesuit subsystem | n/a | Only relevant if/when a slime armor set is scoped |
| Smithing trims | Vanilla-1.20 armor-trim integration for named materials | Not applicable, no 1.12 analog | n/a | Leave alone (listed above) |
| Overslime (`SlimeStats`: durability + overslime capacity) | A modifier-level stat feeding the slime armor's absorb-before-durability mechanic | Not present | L | Tied to the slime subsystem; not a standalone candidate |

No specific new material is recommended for adoption here beyond what M6/M8 already plan to mine from the 1.20 branch (seared stone, necrotic bone, queen's slime, hepatizon, slimewood: already shipped per `docs/SCOPE.md:867`).

---

## World content

*This section was surveyed directly against the 1.20 source in this pass (the original sub-agent assigned to it died early); everything below is read in source.*

| Candidate | What it is | 1.20 path | Forgeweave status |
|---|---|---|---|
| Slime islands (6 structures: earth/sky/ocean-sky/clay/blood/end, across 4 structure sets spanning overworld ocean, overworld sky, nether, and the end) | Floating slime-biome structures | `world/TinkerStructures.java`, `world/worldgen/islands/` | Partly shipped: Forgeweave generates slime islands and magma slime islands (`worldgen/SlimeIslandStructure`, `worldgen/MagmaSlimeIslandStructure`). Upstream's sky, ocean sky, clay and end variants are not here, and no milestone owns them. SCOPE.md lines 75 and 247 predate the shipped islands |
| Geodes (earth/sky/ichor/ender, each yielding a slime crystal + shard + cluster) | Budding-crystal ore-analog structures | `world/TinkerWorld.java` lines 251-272 (`GeodeItemObject`) | Not present; bundled with the same world-content non-goal |
| Cobalt ore (small + large vein worldgen) | Nether ore, feeds cobalt/knightmetal-line materials | `world/TinkerWorld.java` lines 136-146, 316-322 | Forgeweave already has cobalt/ardite nether ore via datapack features (`docs/SCOPE.md:75`): the *ore itself* is in scope, the worldgen-heavy geode/island content around it is not |
| Mobs (sky slime, ender slime, terracube, armored slime, travelers'-plate slime) | New hostile mobs tied to the slime biomes | `world/entity/*.java` | Not present; same non-goal bundle |
| Slime wood/foliage (greenheart, skyroot, bloodshroom, enderbark + grass/leaves/vine/sapling variants per slime type) | Themed plant sets per slime biome | `world/TinkerWorld.java` lines 149-249 | Not present; same non-goal bundle |

Nothing in this section is recommended for adoption independent of the world-content milestone; it exists to give that future milestone a concrete list to start from, not to argue for doing it now.

---

## Quality of life

| Candidate | What it is | 1.20 path (where confirmed) | Forgeweave status | Size | Notes |
|---|---|---|---|---|---|
| Armor stand preview | Preview a tool/armor build before committing | `tables/client/inventory/TinkerStationScreen.java`, `ToolTableScreen.java`, `ModifierWorktableScreen.java` (read in source: all three reference an armor stand) | Not in Forgeweave yet. The maintainer picked it on 2026-09-18 and issue #1043 is building it for the Tool Station and Tool Forge as a reusable widget | M | In progress (#1043) |
| Material and modifier JEI pages | Dedicated JEI categories for browsing material stats and modifier effects | Not independently confirmed | Forgeweave already ships a comprehensive JEI plugin: 14 recipe types across 12 category classes, plus Jade/WTHIT overlays (`docs/SCOPE.md:589`) | n/a | Already shipped |
| Encyclopedia / guide books | In-game book system covering materials, stations, leveling | `book/` assets throughout | Forgeweave already has a guide book covering the materials handbook, the leveling page, and station pages "at 1.12-parity shape" (`docs/SCOPE.md:796`) | n/a | Already shipped |
| Tool renaming | Rename a tool at the crafting station | Confirmed as an Anvil-tier perk: "let you rename tools during any modification" (`book/mighty_smelting/en_us/intro/tinkers_anvil.json`) | Already shipped: `menu/ToolStationMenu` carries a rename field with vanilla's length cap, and assembly, repair, modify and rename share one result path | n/a | Already shipped |
| Tooltips with held-key detail | Extra tooltip lines shown while holding a modifier key | Not independently confirmed in this pass | Forgeweave's tool leveling already has a two-line tooltip (level name + XP) with hue rotation (`docs/SCOPE.md:490`); a held-key-detail toggle for *additional* info wasn't found either way | S | Worth a look once a concrete "what extra info" need shows up (e.g. per-modifier breakdown) |
| Tool stat and modifier info panels | The Tool Station/Forge panel showing free slots, stats | `tables/menu/*` (not independently re-derived this pass) | Forgeweave's Tool Station panel already shows free modifier slots and the leveling tooltip integrates with it (`docs/SCOPE.md:470`) | n/a | Already at or near parity |
| JEI recipe-click transfer into open stations | Click a JEI recipe to auto-fill an open station | `tables/menu/*` (not independently re-derived) | **Already shipped**: Forgeweave's M1 JEI integration explicitly includes "recipe-click transfer into open stations" (`docs/SCOPE.md:36`) | n/a | None; already matches |
| Tags that make datapack authoring easier | Convenience tags for pack authors | Throughout `common/data/tags/` | Not audited in this pass | n/a | Low priority without a concrete pack-authoring pain point to fix |

Most of the "quality of life" items the issue asked about are already shipped in Forgeweave (JEI transfer, the guide book, the leveling tooltip, the Tool Station panel, tool renaming). The armor stand preview is the exception and is being built under #1043. The one open item after that is **held-key tooltip detail**, and only if a concrete use case shows up.

---

## Fluids and effects

*Surveyed directly against the 1.20 source in this pass.*

### Fluid effects

A full effects table (`library/modifiers/fluid/FluidEffectManager`/`FluidEffects`) maps each fluid to what happens when it's thrown, sprayed, or drunk: entity effects (damage, mob effects, push, freeze, breath), block effects (place/break/melt a block, spawn a mob-effect cloud), and general effects (explosions, item drops, conditional/scaling variants). Read in full at `tools/data/FluidEffectProvider.java` (354 lines, every fluid in the mod). Examples: lava sets fire and burns; milk cures effects; earth slime slows and pulls blocks; venom poisons and boosts strength; molten glass deals spike damage and can place glass panes/blocks depending on amount; potion fluid applies a fractional dose of whatever potion effect it's tagged with; soups (beetroot, mushroom, rabbit, meat) restore hunger like their item counterparts.

**Forgeweave status**: Forgeweave has molten fluids (Smeltery output) but no effects table: fluids melt/cast, they don't do anything when thrown or sprayed. **Size L** as a full system (it's the prerequisite for spilling/bursting modifiers and the fluid cannon), but it decomposes cleanly: a handful of fluid-effect classes (damage, mob-effect, block-place/break) cover most of the table, and per-fluid entries are pure data once the classes exist.

### Spilling and bursting modifiers

Both reuse the fluid effects table above rather than being independent mechanics:

- **Spilling** (`tools/modules/combat/SpillingModule.java`): on a fully-charged melee hit or a ranged hit, applies the tool's held fluid's entity-effect to the target and consumes fluid from the tool's internal tank proportional to level.
- **Bursting** (`tools/modifiers/ability/fluid/BurstingModifier.java`): an armor-side retaliation modifier; when hit by direct damage, it splashes the wearer's held fluid's effect back at the attacker (thorns-shaped, but fluid-flavored).

**Forgeweave status**: not present; depends entirely on the fluid effects table. **Size S-M each**, once that table exists: both are thin wrappers (one hook each) around it.

### Potion fluids and soups

Potion fluid (`TinkerFluids.potion`) applies a fractional dose (25% per "sip", so 4 sips = a full potion effect) of whatever potion tag the fluid carries, both on drink and on block-splash. Soups (beetroot/mushroom/rabbit/meat) restore hunger matching their vanilla item counterparts. Both are thin entries in the same fluid effects table, not separate systems.

**Forgeweave status**: not present. **Size S** each, once the fluid effects table exists; genuinely trivial additions after that prerequisite.

### The fluid cannon

Covered in the Stations section above: a redstone-triggered block that fires the fluid effects table's projectile/block-effect path. **Prerequisite-gated** on the fluid effects table existing first.

---

## What this doc deliberately does not resolve

- Whether the fluid effects table is worth building at all before there's a concrete fluid-combat feature that needs it (fluid cannon, spilling, bursting, potion fluids, soups all sit behind it as one shared prerequisite: size that once, not four times).
- Whether the interaction-hook split (general/block/entity) is worth building before a concrete dual-purpose tool (fishing rod, shears, hoe-equivalent) needs it: same "one shared prerequisite" shape as the fluid effects table.
- Exact art cost per item. Where 1.20 ships original art for a candidate, Forgeweave's own Forged-art pipeline applies as normal (freshly authored 16x16 sprites); nothing here specifically calls for deriving 1.20 textures under MIT with a `NOTICE.md` row, since none of the recommended small items (dagger, sorting recipe, crystal item, ancient-tools flavor, chute/duct/channel/proxy-tank blocks) have a strong reason to reuse upstream pixels over authoring Forgeweave's own: flag this per-item if a specific texture turns out to be worth deriving once implementation starts.
