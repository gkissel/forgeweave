![Forgeweave](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/banner.png)

# Forgeweave

Forgeweave brings modular tool building, material traits, modifiers, melting, alloying and casting to Minecraft 1.21.1 on NeoForge. You cut patterns, shape parts from the material you choose, and assemble them into a tool whose stats come from what it is made of. A tool that reaches 0 durability breaks but is never destroyed: you repair it at the Tool Station and keep going.

The mod ships 24 tool shapes, 8 armor pieces, 224 materials, 195 traits, 48 modifiers and a multiblock smeltery. It is MIT licensed and open source.

## ![Features](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/section-features.png)

### ![Build tools from parts](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-modular-tools.png)

There are 24 tool shapes and 2 kinds of ammo, and every one is assembled from interchangeable parts.

- 10 mining and farming tools: pickaxe, shovel, hatchet, mattock, kama, hammer, excavator, lumberaxe, scythe and vein hammer.
- 11 melee weapons: broadsword, longsword, rapier, battlesign, frying pan, dagger, battleaxe, scimitar, katana, warmace and cleaver.
- 3 ranged weapons: shortbow, longbow and crossbow.
- Ammo: material arrows and shuriken.

The first tool takes 3 blocks. The Stencil Table turns a blank pattern into a part pattern, the Part Builder turns a pattern and a material into a part, and the Tool Station assembles the parts. Large tools are built at the Tool Forge, which also takes 5% off repair costs. The Crafting Station is a crafting table that keeps its items when you close it, and the Pattern Chest and Part Chest keep their inventory when you break them.

![Modular tools](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/modular-tools.jpg)

### ![224 materials](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-materials.png)

Each material has its own stats and its own traits, and a material can grant different traits through a head than through a handle or an armor plate. The roster has 3 groups:

- 51 base materials that are always available, such as wood, stone, flint, bone, iron, copper, cobalt, ardite, manyullyn, obsidian, prismarine, slime and netherite.
- 29 original Forgeweave materials from the mod's own ores and alloys.
- 144 materials that load only when the mod that owns them is installed.

About 47 metals come in a full set of forms: ingot, nugget, plate, double plate, rod, gear, wire and 3 sizes of dust.

![Materials](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/materials.jpg)

### ![195 traits](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-traits.png)

A trait is what a material does beyond its numbers. The tooltip states the real figure for each one. A few examples, quoted from the game:

- Autosmelt (firewood): "Harvested blocks drop their furnace-smelted result."
- Lightweight (cobalt): "15% more mining, attack and bow draw speed."
- Petramor (ardite): "A 10% chance per stone block mined to repair 5 durability."
- Momentum (hepatizon): "Mining without pause builds speed, 1.25% a block up to 40% after 32."
- Cheap (stone): "A repair gives back 25% more durability than the material is worth."

Traits cover combat, mining, armor and utility. The mod adds 6 status effects for them to work with: Bleeding, Lacerated, Splintered, Enderference, Pierced and Grievous Wound.

![Traits](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/traits.jpg)

### ![48 modifiers and embossing](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-modifiers.png)

Modifiers are applied to a finished tool at the Tool Station and spend its modifier slots. Examples, quoted from the game:

- Veinmine: "Hold the Vein Mine key to take a whole run of ore, logs or soil in one swing: 4 blocks a level, up to 20 at level 5."
- Reinforced: "A 20% chance per level to negate durability damage outright, so level 5 never wears at all."
- Silky: "Grants Silk Touch, at the cost of 3 mining speed and 3 attack damage."
- Soulbound: "The tool stays with you even after you die."
- Elytra Flight: "Spends a real elytra to teach the chestplate to glide exactly as a worn elytra does."

Embossing spends a spare part to give a finished tool that part material's traits without changing its stats. It costs no modifier slot, and a tool takes 1 embossment.

The Modifier Worktable works on modifiers a tool already has. A wet sponge takes 1 level off a modifier and returns the slots that level held, a compass moves a modifier up or down the list, and you can hide or show modifier visuals.

By default a Forgeweave tool cannot be enchanted at the enchanting table or with a book at the anvil. A server option turns that on.

![Modifiers](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/modifiers.jpg)

### ![Tools and armor level up with use](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-leveling.png)

Every tool and armor piece earns experience by doing its job: breaking blocks, killing mobs, landing shots, or keeping damage off you. Each level grants 1 modifier slot and nothing else.

The first level costs 500 experience and so does the second. After that each level costs twice the one before: 1000, 2000, 4000. There is no level cap by default. The 5 area tools (hammer, excavator, lumberaxe, scythe and vein hammer) cost 9 times as much, so their first level is 4500.

A level-up plays a chime, prints a line in chat, and gives the tool a named rank on its tooltip. The ranks for levels 0 to 11 run from "Like new" through Clumsy, Comfortable, Accustomed, Adept, Expert, Master, Grandmaster, Heroic, Legendary and Godlike to "Awesome".


### ![A multiblock smeltery](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-smeltery.png)

![The 4 smeltery tiers](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/smeltery-tiers.png)

![The 4 tiers in the game](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/smeltery-tiers.jpg)

Build the smeltery from grout and seared bricks, then melt, alloy and cast. Casting tables, casting basins, faucets and channels move the metal, and casts come as reusable gold casts or single-use clay casts. The mod ships 35 alloy recipes, 5 fuels and 144 fluids.

The core in the wall decides how much metal an ore gives. There are 4 cores, and each one after the first is made by pouring fluid over the one before:

| Core | Ingots per ore | How you get it |
|---|---|---|
| Standard | 1.5 | Crafted from 8 seared bricks |
| Nether | 2 | Half a bucket of blazing blood over a Standard Core |
| End | 2.5 | 1 bucket of molten dragon breath over a Nether Core |
| Deep | 3 | 2 buckets of deep blood over an End Core |

Mobs that walk into a smeltery melt. 7 recipes cover them, and deep blood comes from a warden.

The Seared Furnace and the Seared Reservoir are separate multiblocks with their own controllers. The Energized Tank heats a smeltery with Forge Energy instead of burning fuel, and its overdrive button doubles both the melting speed and the energy cost.

Seared blocks come in 12 decorative variants, each with stairs and a slab.

![Smeltery](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/smeltery.jpg)

### ![Modular armor](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-armor.png)

Armor is built the way tools are, from plating and maille at the Tool Station. Each piece carries its materials' traits and has its own modifier slots. There are 8 pieces: a light helmet, chestplate, leggings and boots, and a heavy version of each.

Heavy armor adds a large plate behind the plating and maille. It gives 1.4 times the armor of the light piece, slows you by 5% per piece, and is built only at the Tool Forge.

Each worn piece levels on its own, from the damage it kept off you.

![Armor](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/armor.jpg)

### ![Forgeweave's own ores and alloys](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-trackb-ores.png)

Forgeweave adds 11 ores and 18 alloys that need no other mod. The ores generate in all 3 dimensions:

- Nether: fulmenite, murkiron and warspar.
- End, outer islands only: duskspar, nightshale, hollowstone and voidglass.
- Overworld: hardcinder, voltcinder and resonite in deepslate, and starfall stone in surface stone.

3 of them define mining tiers above netherite: hardcinder, then warspar, then resonite. Fulmenite ore is unstable and can explode.

The Nether also has cobalt and ardite, plus brimspar, which is a smeltery fuel. Slime islands generate too, with magma slime islands in the Nether.

![Forgeweave ores](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/trackb-ores.jpg)

### ![Materials for more than 25 mods](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-mod-materials.png)

144 materials appear when the mod that owns them is installed, with no setup. If the mod is missing, its materials do not load. The roster covers Allthemodium, Mekanism, Draconic Evolution, Mystical Agriculture, Twilight Forest, Ice and Fire, EnderIO, AE2, Avaritia, ProjectE and more. The full list is under Integrations below.

![Mod materials](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/mod-materials.jpg)

### ![Deep compat with 5 mods](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-deep-compat.png)

With Mekanism, Draconic Evolution, Apotheosis, Create or Occultism installed, Forgeweave gear takes part in that mod's own systems: Mekanism modules, Draconic fusion crafting and modules, Apotheosis gem sockets and affixes, Create goggles, and Occultism rituals. Each one has its own section under Integrations.

![Deep compat](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/deep-compat.jpg)

### ![Guide book and Ponder scenes](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-guide-book.png)

New players get the guide book "Materials and You" the first time they join. It has 9 sections: intro, tools, materials, casting, smeltery, alloys, modifiers, armor and leveling.

Forgeweave also ships 7 Ponder scenes: the smeltery, smeltery sizes, casting with faucet and channel, armor assembly, the Seared Furnace, the Seared Reservoir and the 4 core tiers.

![Guide book](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/guide-book.jpg)

### ![2 art sets](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-art-sets.png)

The default look is Forged, new original 16x16 art. The pack "Forgeweave Legacy Art" is built in and off by default. Turn it on in the resource pack screen to switch 145 textures to the Legacy look.

![Art sets](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/art-sets.jpg)

### ![Server options](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-config.png)

Server settings live in 4 files under `config/forgeweave/`: general, content, compat and worldgen.

- 20 compat toggles switch integrations on or off. Turning one off never deletes anything: gear keeps what it carries and it works again when the toggle comes back.
- Content toggles switch off whole families, such as melee weapons, ranged weapons, armor, the smeltery, modifiers or tool leveling.
- Worldgen options set whether cobalt, ardite, Forgeweave's ores and slime islands generate, and how often.

Players get client options too, including extra tooltips, the held bow pose and whether trait feedback shows.


### ![Addon API, datapacks and KubeJS](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/feature-addon-api.png)

Materials are datapack JSON, so adding one needs no code and no art. Forgeweave reads 12 datapack registries, and a datapack can define new traits by naming one of the mod's built-in behaviors and supplying its numbers. KubeJS startup scripts can register traits through the `ForgeweaveEvents.traits` event. Other mods can build against the public API in `dev.gkissel.forgeweave.api` to add tools, parts, traits and modifiers.


## ![Integrations](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/section-integrations.png)

Every integration is optional. Forgeweave runs with none of them installed.

### ![JEI](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-jei.png)

At least 13 recipe categories, including part crafting, tool assembly, tool repair, modifier application, embossing, melting, alloying, casting, smeltery fuel, entity melting and core transform.

![JEI](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/jei.jpg)

### ![EMI](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-emi.png)

EMI shows Forgeweave recipes through its JEI bridge. There is no native EMI plugin.

### ![Ponder](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-ponder.png)

Ponder and Flywheel are bundled in the Forgeweave jar, so the 7 scenes work without a separate download.

### ![Jade and WTHIT](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-jade-wthit.png)

Both overlays show Forgeweave lines: casting cooldown, smeltery fluid and mining level.

### ![KubeJS](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-kubejs.png)

The `ForgeweaveEvents.traits` startup event registers script traits, and `ServerEvents.registry` can write entries into any of the 12 Forgeweave registries.

### ![Dark Mode Everywhere](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-dark-mode-everywhere.png)

Forgeweave tells Dark Mode Everywhere not to recolor the smeltery fluid display and the Tool Station's tool layers, so both keep their real colors.

### ![Apotheosis](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-apotheosis.png)

- The Socketed modifier adds 1 gem socket per level, up to 3, and gems seat in them.
- Forgeweave gear found as loot can carry Apotheosis affixes. Gear you build is never affixed.
- With Apothic Enchanting installed and the `allowVanillaEnchanting` server option on, Forgeweave gear can be enchanted at the Apotheosis enchanting table.


### ![Create](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-create.png)

The Goggles modifier mounts Create's goggles on a helmet at no modifier slot cost. Forgeweave also adds 60 mixing, crushing and pressing recipes, and 4 materials: zinc, brass, andesite alloy and rose quartz.

![Create](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/create.jpg)

### ![Mekanism](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-mekanism.png)

Gear with an atomic matter alloy part is a Mekanism module container: the Modification Station installs MekaTool and MekaSuit modules into it. Atomic matter alloy is made in the Antiprotonic Nucleosynthesizer. The Rayward armor modifier blocks 25% of radiation per level, so 4 levels stop it. Forgeweave adds 66 Mekanism ore processing recipes and 12 Mekanism materials.

![Mekanism](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/mekanism.jpg)

### ![Draconic Evolution](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-draconic-evolution.png)

Fusion crafting upgrades a tool made of a Draconic core material, through the draconium, wyvern, draconic and chaotic tiers. Gear made of one of the 4 welds (duskweld, emberweld, starweld, voidweld) hosts Draconic modules. Chaotic tier hits damage the Chaos Guardian's crystals. 10 materials in total.

### ![Occultism](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-occultism.png)

A ritual binds a spirit into a finished tool and grants a modifier without spending reagents. 4 spirits answer: foliot, djinni, afrit and marid. The modifier still costs its modifier slots. Occultism's crusher spirits also grind Forgeweave ores. 3 materials: iesnium, spirit attuned gem and dragonyst.

![Occultism](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/occultism.jpg)

### ![Mystical Agriculture](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-mystical-agriculture.png)

12 resource crops for Forgeweave's ores and brimspar, and 9 essence materials. Gear with an essence metal part takes Mystical Agriculture augments at that mod's own table. Augment slots are separate from modifier slots. Essence gear has 1 augment slot, or 2 from awakened supremium upward.

![Mystical Agriculture](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/mystical-agriculture.jpg)

### ![Immersive Engineering and EnderIO](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-immersive-engineering-enderio.png)

Processing recipes for Forgeweave metals: 60 for Immersive Engineering's arc furnace, crusher and press, and 15 for EnderIO's alloy smelter and SAG mill. EnderIO also gets 8 alloy materials.

![Immersive Engineering](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/immersive-engineering.jpg)
![EnderIO](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/enderio.jpg)

### ![Powah](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-powah.png)

Forgeweave's molten fluids count as heat sources for Powah's thermo generator. The Surgebound modifier has 5 levels. In the game's words, it "raises the tool's energy capacity 25% and its mining speed 5% per level, and the nitro step at level 5 doubles both instead." 6 materials.

![Powah](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/publish/screens/powah.jpg)

### ![Allthemodium](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-allthemodium.png)

Forgeweave's ores generate in Allthemodium's mining dimension. Forgeweave tools can mine Allthemodium ore and Allthemodium tools can mine Forgeweave ore. 6 materials: allthemodium, vibranium, unobtainium and 3 alloys of them.

### ![Elementarium](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-elementarium.png)

6 materials: chromium, hafnium, molybdenum, palladium, tantalum and vanadium.

### ![Better Combat and Epic Fight](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-better-combat-epic-fight.png)

Forgeweave weapons come with movesets for both mods, in 24 weapon attribute files and 31 capability files.

### ![Material presets](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/integration-material-presets.png)

These 19 mods get materials and nothing else: Twilight Forest, Ice and Fire, Neo Vitae, Silent Gear, Just Dire Things, Actually Additions, Extreme Reactors, Avaritia, ProjectE, AE2, Psi, The Aether, L_Ender's Cataclysm, PneumaticCraft, Forbidden and Arcanus, Industrial Foregoing, Refined Storage, Modern Industrialization and Eternal Ores.


## ![What you need](https://raw.githubusercontent.com/gkissel/forgeweave/master/docs/launch/modpage/assets/section-requirements.png)

| | |
|---|---|
| Game version | Minecraft 1.21.1 |
| Loader | NeoForge 21.1.248 or newer |
| Java | 21 |
| Required mods | None |
| Bundled | Ponder and Flywheel |
| Optional | Every mod under Integrations |
| Install on | Client and server |
| License | MIT |
| Source | https://github.com/gkissel/forgeweave |
