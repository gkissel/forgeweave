# Writing a Forgeweave addon

This is the guide for adding content to Forgeweave from outside it: a datapack, a KubeJS script, or another mod's Java. It closes the surface epic [#1008](https://github.com/gkissel/forgeweave/issues/1008).

Every example here comes from the test addon under `src/gametest/java/dev/gkissel/forgeweave/gametest/addon/` and `src/gametest/resources/data/gametest_addon/`, which is compiled by the build and driven by GameTests. `AddonDocsExampleTest` fails the build if an example here stops matching the file it names, so nothing below is invented.

The test addon is not published. It lives in the GameTest-only `gametest` source set, folded into the mod's file list for `runGameTestServer` alone.

## What you can add, and how

| | Datapack | KubeJS | Java |
| --- | --- | --- | --- |
| Materials and their part stats | yes | yes | ship the JSON |
| Traits, over a library behavior | yes | — | ship the JSON |
| Traits, your own behavior type | names it | — | `TraitRegistry.registerBehavior` |
| Traits, one finished trait | — | `ForgeweaveEvents.traits` | `TraitRegistry.register` |
| Modifiers, over a library behavior | yes | yes | ship the JSON |
| Modifiers, your own behavior type | names it | — | `ModifierRegistry.registerBehavior` |
| Modifiers, one finished modifier | — | — | `ModifierRegistry.register` |
| All twelve recipe registries | yes | yes | ship the JSON |
| Tools and tool parts | **no** | **no** | `ForgeweaveTools` |
| Smeltery wall, floor, tank and I-O blocks | block tag | block tag | block tag |
| Reclaiming an upgrade on a part swap | — | — | `UpgradeHosts.register` |
| Granting tool XP | — | — | `ToolLeveling.addXp` |
| Guide book sections and pages | **no** | **no** | **no** |
| Part kinds, armor piece kinds | **no** | **no** | **no** |

Everything in the Java column lives under `dev.gkissel.forgeweave.api`. That package imports nothing but Minecraft, NeoForge and itself, so it is safe to build against and safe to classload anywhere. What it promises and for how long is [ADR-0006](adr/0006-addon-api-stability.md).

## Materials

A material is JSON in your own namespace, at `data/<your namespace>/forgeweave/material/<name>.json`, and needs no code at all. Part sprites are greyscale and tinted from the `color` field, so a material needs no art either.

<!-- from src/gametest/resources/data/gametest_addon/forgeweave/material/addon_alloy.json -->
```json
{
  "head": {
    "durability": 320,
    "mining_speed": 5.5,
    "attack_damage": 4.0
  },
  "handle": {
    "durability_modifier": 1.1,
    "durability": 30
  },
  "extra_durability": 25,
  "incorrect_for_tool": "minecraft:incorrect_for_iron_tool",
  "traits": {
    "general": ["gametest_addon:addon_pack_trait", "gametest_addon:addon_java_trait"]
  },
  "crafting_items": [
    { "ingredient": { "item": "minecraft:sniffer_egg" }, "value": 144 }
  ],
  "repair_item": {
    "item": "minecraft:sniffer_egg"
  },
  "color": "#4FA3C8"
}
```

`head`, `handle`, `extra_durability`, `bow`, `bowstring`, `shaft`, `fletching`, `plating` and `maille` are all optional stat blocks; a material supplies the ones its parts need. `plating` and `maille` are the armor stats, so armor materials are datapack-definable even though armor piece kinds are not.

A top-level `neoforge:conditions` array gates whether the material exists at all, which is how Forgeweave's own compat metals wait for the mod that owns their ingot. The crafting-item ingredient is read leniently, so naming an item another mod owns does not fail the load when that mod is absent.

Add `"cast_only": true` if the material is a metal that is poured at the Smeltery rather than stamped at the Part Builder.

## Traits

### Over a library behavior

Forgeweave ships a library of parameterized trait behaviors. Naming one and supplying its parameters is the whole of a trait, in your own namespace at `data/<ns>/forgeweave/trait_definition/<name>.json`:

<!-- from src/gametest/resources/data/gametest_addon/forgeweave/trait_definition/addon_pack_trait.json -->
```json
{
  "behavior": "forgeweave:extra_modifier_slots",
  "count": 2
}
```

A material then names `<ns>:<name>` in its `traits` block, and your pack supplies the `trait.<ns>.<name>.name` and `.description` lang keys. `neoforge:conditions` gates a definition the same way it gates a material.

The full behavior list is `TraitBehaviors`. If a bad `behavior` id fails the parse, the error names every behavior Forgeweave knows about, including the ones addons registered.

### Your own behavior type

When the logic is not something parameters can express, register the behavior *type* from Java and leave the parameters in data. One registration then serves every pack that installs your mod:

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
public record FlatAttackBonus(float amount) implements Trait {

    public static final MapCodec<FlatAttackBonus> CODEC =
            Codec.FLOAT.fieldOf("amount").xmap(FlatAttackBonus::new, FlatAttackBonus::amount);

    @Override
    public float attackDamageBonus(ItemStack stack) {
        return amount;
    }
}
```

Registered in your mod's constructor:

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
TraitRegistry.registerBehavior(TRAIT_BEHAVIOR_ID, FlatAttackBonus.CODEC);
```

and named from a definition file exactly like a built-in one:

<!-- from src/gametest/resources/data/gametest_addon/forgeweave/trait_definition/addon_java_trait.json -->
```json
{
  "behavior": "gametest_addon:flat_attack_bonus",
  "amount": 2.5
}
```

### One finished trait

`TraitRegistry.register(id, trait)` adds a single finished `Trait` under an id, for logic with nothing to parameterize. A material naming that id gets the behavior the same way it gets a built-in one. Nothing here is gated by `compat.kubejsTraits`. That toggle governs KubeJS script traits only, and your content is governed by your own config.

### From a KubeJS script

Startup scripts get a builder whose callbacks mirror the `Trait` hooks:

```js
// kubejs/startup_scripts/forgeweave_traits.js
ForgeweaveEvents.traits(event => {
    event.register('mypack:frosty')
        .onAfterHit((stack, level, attacker, target) => target.potionEffects.add('minecraft:slowness', 60))
        .onMiningSpeed((stack, effective, originalSpeed, speed) => speed * 1.25)
        .bonusSlots(1)
})
```

This path is behind the `compat.kubejsTraits` config toggle.

## Modifiers

The modifier side is deliberately the same shape as the trait side, one registry over.

### Over a library behavior

`data/<ns>/forgeweave/modifier_definition/<name>.json`:

<!-- from src/gametest/resources/data/gametest_addon/forgeweave/modifier_definition/addon_pack_modifier.json -->
```json
{
  "behavior": "forgeweave:bonus_slots",
  "flat": 2
}
```

### Your own behavior type

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
public record FlatAttackModifier(float amount) implements Modifier {

    public static final MapCodec<FlatAttackModifier> CODEC =
            Codec.FLOAT.fieldOf("amount").xmap(FlatAttackModifier::new, FlatAttackModifier::amount);

    @Override
    public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
        return attackDamage + amount * level;
    }
}
```

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
ModifierRegistry.registerBehavior(MODIFIER_BEHAVIOR_ID, FlatAttackModifier.CODEC);
```

### One finished modifier

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
public static final Modifier WHETTED = new Modifier() {
    @Override
    public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
        return attackDamage + WHETTED_PER_LEVEL * level;
    }
};
```

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
ModifierRegistry.register(MODIFIER_ID, WHETTED);
```

### A modifier still needs a recipe

Registering or defining a modifier does not put it within a player's reach. A modifier reaches a tool only through a `modifier_recipe` naming its id, whatever defined it:

<!-- from src/gametest/resources/data/gametest_addon/forgeweave/modifier_recipe/addon_whetted.json -->
```json
{
  "modifier": "gametest_addon:whetted",
  "reagents": [
    {
      "ingredient": {
        "item": "minecraft:goat_horn"
      }
    }
  ],
  "cost": 1,
  "max_level": 1
}
```

Your addon supplies the `modifier.<ns>.<name>.name` and `.description` lang keys.

There is no `ForgeweaveEvents.modifiers` KubeJS builder. That is deliberate (ADR-0004 item 3) and stays so until a pack author asks for one.

## The registration window

`TraitRegistry`, `ModifierRegistry`, `ForgeweaveTools` and `UpgradeHosts` all take registrations during **mod construction**: from your mod's constructor, or from an `FMLConstructModEvent` listener.

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
public GameTestAddon(IEventBus modEventBus) {
    ITEMS.register(modEventBus);
    BLOCKS.register(modEventBus);
    ForgeweaveTools.registerPart(PartDefinition.stamped(BLADE_ID, PartKind.HEAD, BLADE, PATTERN, BLADE_COST));
    ForgeweaveTools.registerTool(TOOL_DEFINITION, TOOL);
    TraitRegistry.registerBehavior(TRAIT_BEHAVIOR_ID, FlatAttackBonus.CODEC);
    ModifierRegistry.registerBehavior(MODIFIER_BEHAVIOR_ID, FlatAttackModifier.CODEC);
    ModifierRegistry.register(MODIFIER_ID, WHETTED);
    UpgradeHosts.register(host());
}
```

A registration that arrives after the window closes throws, naming the id, rather than being quietly dropped into a table nothing will read again. Two addons claiming one id also throws: that is a packaging bug, not a precedence question.

## Id collisions

**A built-in Forgeweave id always wins**, and this is the one collision that does not throw. Forgeweave keeps the built-in, ignores the registration or the file, and logs the collision. The rule is uniform across traits, modifiers, definitions and behavior types, and it has been the datapack rule since the definition registries shipped.

Use your own namespace and the question never comes up.

## Recipes

All twelve of Forgeweave's data-driven content registries take JSON in your own namespace:

| Registry | Path under `data/<ns>/forgeweave/` |
| --- | --- |
| Materials | `material/` |
| Trait definitions | `trait_definition/` |
| Modifier definitions | `modifier_definition/` |
| Melting | `melting_recipe/` |
| Casting | `casting_recipe/` |
| Alloying | `alloy_recipe/` |
| Smeltery fuel | `smeltery_fuel/` |
| Entity melting | `entity_melting_recipe/` |
| Core transform | `core_transform_recipe/` |
| Modifier application | `modifier_recipe/` |
| Embossing | `embossing_recipe/` |
| Modifier Worktable | `worktable_recipe/` |

Copy a shipped file from `src/main/resources/data/forgeweave/forgeweave/<folder>/` for the exact shape. Every one of them accepts a top-level `neoforge:conditions` array.

Your recipes are in the same registries Forgeweave's own are, so **JEI shows them in the existing categories with no work**. A genuinely new recipe type is yours to register with JEI under your own plugin; Forgeweave's JEI categories are closed.

### Recipes from KubeJS

`event.recipes.forgeweave.melting(...)` does not exist and will not. KubeJS's recipe schema API edits the vanilla recipe manager, whose entries live under `data/<ns>/recipe/` and carry a `type` field. None of the twelve above is one of those: each is a datapack **registry** loaded by `RegistryDataLoader`, which the recipe manager never sees. A `RecipeSchema` for them would describe something KubeJS has no way to write.

The seam that does reach a datapack registry is `ServerEvents.registry`, and Forgeweave hands it every one of the twelve:

```js
// kubejs/server_scripts/forgeweave_recipes.js
ServerEvents.registry('forgeweave:melting_recipe', event => {
    event.createFromJson('mypack:molten_mythril', {
        input: { tag: 'c:ingots/mythril' },
        fluid: 'forgeweave:molten_iron',
        amount: 144
    })
})
```

The object is read through Forgeweave's own codec, so a bad field fails exactly the way the same file in a `data/` folder would. Materials and definitions work the same way, under `ServerEvents.registry('forgeweave:material', ...)`, `'forgeweave:trait_definition'` and so on. That is what makes scripting worth the trouble: a loop over your metals writes forty melting recipes without forty files.

## Tools and tool parts

**A tool is registered in Java and cannot be defined by a datapack.** A tool needs an `Item` instance and a model with one tinted layer per part, and a datapack can produce neither. This was the audit's first open question and the maintainer's answer on 2026-09-18; it is not a gap waiting to be filled.

A part and a tool, through `ForgeweaveTools`:

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
public static final DeferredItem<Item> BLADE =
        ITEMS.registerItem("test_blade", properties -> ForgeweaveTools.partItem(PartKind.HEAD, properties));
```

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
public static final ToolDefinition TOOL_DEFINITION = ToolDefinition.builder(TOOL_ID, ToolFamily.MELEE)
        .part(PartRole.HEAD, BLADE_ID)
        .part(PartRole.HANDLE, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "tool_handle"))
        .weapon()
        .attackSpeed(1.6f)
        .flatAttackBonus(2.0f)
        .build();
```

A slot may name one of Forgeweave's own parts as well as your own, as the handle slot does here.

A registered tool gets a Tool Station tab whose slots are laid out from its part count, assembles and repairs there, shows in the creative tab, renders its layers tinted by the materials it was built from, and answers the same modifier, trait and leveling machinery a built-in tool does. A registered part is offered at the Stencil Table and stamped at the Part Builder at the cost you named, in units where one ingot is `ForgeweaveTools.INGOT_VALUE`.

**Art.** Ship one texture per layer at `<your namespace>:textures/item/<tool>_<layer>.png`, plus a `_broken` variant of the layer that shows damage. Layer names come from the part roles in Forgeweave's back-to-front drawing order, numbered from the second occurrence of a role on: a handle-head-binding tool is `handle`, `head`, `binding`, and a two-headed one is `handle`, `head`, `head2`. Nothing has to be added to a Forgeweave table for those paths to resolve.

## Smeltery blocks

The smeltery scan decides its wall, floor, tank, I-O and energized roles by **block tag**, so your own block joins a role from a tag file with no Java and no Forgeweave change:

| Role | Tag |
| --- | --- |
| Wall | `forgeweave:smeltery/wall` |
| Floor | `forgeweave:smeltery/floor` |
| Tank | `forgeweave:smeltery/tanks` |
| I-O | `forgeweave:smeltery/io` |
| Energized | `forgeweave:smeltery/energized` |

Add your block to the tag from your own `data/forgeweave/tags/block/smeltery/<role>.json`; datapack tag files from different mods merge rather than replace, so you are adding to the shipped members rather than shadowing them.

## Reclaiming an upgrade on a part swap

If your mod attaches something to a Forgeweave tool (a module, a socket, an augment), register an `UpgradeHosts.Host` so a part swap that invalidates it hands it back as items instead of destroying it. Forgeweave asks every host at the one point a swap is resolved, and what the hosts return joins the parts the swap already gives back.

<!-- from src/gametest/java/dev/gkissel/forgeweave/gametest/addon/GameTestAddon.java -->
```java
public static UpgradeHosts.Host host() {
    return (original, replacement) -> original.is(TOOL.get()) && !replacement.is(TOOL.get())
            ? List.of(new ItemStack(PATTERN.get()))
            : List.of();
}
```

The contract: mutate `replacement` and never `original`; strip only what the replacement genuinely cannot use; return what the player should get, empty when nothing changed; and return nothing at all when your own config toggle is off. Off is inert, not destructive.

## Granting tool XP

`ToolLeveling.addXp(stack, amount, player)` is the one call every XP source makes, and your mod can grant XP for its own activity today.

It is **not** in the api package, and that is deliberate. It is a call into Forgeweave rather than a type an addon implements, and the api package may import only Minecraft, NeoForge and itself. Moving it would mean dragging `config`, `item`, `menu` and `tool` in behind it, or adding an indirection with exactly one implementation. It stays public where it is and carries the same promise as the api package ([ADR-0006](adr/0006-addon-api-stability.md) tier 3).

## What is closed

Say so up front in your own documentation rather than discovering it late.

- **A new tool from a datapack.** Java only, as above.
- **The guide book.** `BookStructure` reads its sections from the mod classpath rather than the resource manager, so nothing outside the Forgeweave jar can add a section or a page, and a resource pack cannot override one. This is the largest item the audit found and the least asked for; it stays closed until an addon author asks, the posture ADR-0004 took on the KubeJS modifier builder.
- **Part kinds and armor piece kinds.** `PartItem.Kind` and `ToolConstants.Role` are enums tied to `Material`'s stat block fields, and armor piece types are vanilla's four-value enum. A new part kind is a new stat system, not a registration. Armor *materials* are open, through `plating` and `maille`.
- **Item forms for a material** (ingot, nugget, dust, plate, gear, wire, ore chain). Forgeweave generates these for its own metals only. Ship your own items; the forms of a metal belong to the mod that owns it.
- **JEI categories.** Register your own JEI plugin for a genuinely new recipe type. Your entries in Forgeweave's registries need nothing.
- **Forgeweave's config file.** You cannot add a toggle to it, and `forgeweave:compat_toggle` can only name the five toggles it knows. Use `neoforge:mod_loaded` or your own condition type, and put your toggles in your own config.

## When your addon is removed

Four cases, and only the last one loses anything.

| What defined it | What happens to a player's stack |
| --- | --- |
| A modifier | The entry keeps its id and level and contributes nothing. Unknown ids are kept, not dropped, and work again the moment the addon returns. |
| A trait | The same. Every hook site treats an unresolvable trait id as no trait. |
| A material | A finished tool keeps working: its stats were baked in at assembly. What breaks is anything that looks the material up live: the tooltip's material name, repair, and the traits that material granted. Loose, unassembled **parts** are worse off: the stations refuse them with a message until the addon returns. |
| A tool or part item | Lost. The item id itself is gone, so vanilla's own behavior applies. Nothing Forgeweave can do, and worth saying in your own documentation. |

Removing an addon that defined traits or modifiers is safe and reversible. Removing one that defined materials degrades tools rather than breaking them. Removing one that defined tools loses them.

## The sync budget

Every one of the twelve registries passes its codec as its network codec, so all of it is sent to every client on login. Your content rides that path with no extra work, and a client with only Forgeweave installed still receives an addon server's definitions, because the payload is data rather than classes.

Materials are the bulk of it. Forgeweave's own roster is budgeted by a test at 136 KB, but that test walks Forgeweave's own data folder and cannot see yours. What actually goes out is logged at server start:

```
Material registry sync payload: 85 of 85 materials encode to 60880 bytes
```

That is a server where 85 of Forgeweave's own materials passed their `neoforge:conditions`, so a little over 700 bytes each. Read the line on your own install rather than guessing: a few hundred addon materials are fine and a few thousand are not. If you are shipping a large roster, keep stat blocks a material's parts do not use out of the JSON.

The same caveat applies to JEI: its display-recipe budget was measured against Forgeweave's own material roster, so an install with several large addons builds larger lists than anything CI measures.

## Stability

What is promised, for how long, and how a break is announced: [ADR-0006](adr/0006-addon-api-stability.md).

The short version. Datapack JSON shapes and the stack serialization contract are stable from the release the api package shipped in. The Java API under `dev.gkissel.forgeweave.api` is provisional for one minor release, then stable on the same terms. Everything outside that package is internal and may change without notice, whatever its visibility.
