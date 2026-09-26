# Material color audit

The registry currently contains 216 material JSON files. A material's `color` is a single RGB tint applied to grayscale part sprites in the general renderer, so it cannot reproduce two colors, metallic highlights, or animated textures on its own. A matching hue is a useful approximation, not proof of texture parity.

The selected Forged finishes for broadsword, pickaxe and warmace are baked from these colors with `python3 scripts/generate_material_finishes.py`. Regenerate the committed sprites after changing a material color or one of those three tool layers. Broken tools and the Legacy resource pack continue to use their normal tint.

## Verified against Tinkers' Construct 1.12

I compared the literal RGB values in the pinned [TinkerMaterials.java](https://github.com/SlimeKnights/TinkersConstruct/blob/c01173c0408352c50a2e8c5017552323ce42f5b4/src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java) with every same-named JSON in `src/main/resources/data/forgeweave/forgeweave/material/`. All 39 matching entries agree byte for byte, ignoring letter case. This includes wood, stone, cobalt, ardite, manyullyn, obsidian, the slimes, and the listed metal integrations. The upstream `pigiron` is named `pig_iron` here; both use `#EF9E9B`. These colors do not need correction for 1.12 parity.

## Cross-mod colors

The six Allthemodium family JSON files have been edited in the working tree. The three base colors now follow the supplied ingot images: Allthemodium orange `#E98219`, Vibranium green/teal `#18A878`, Unobtainium purple `#8C36C8`. The three alloy colors are design approximations; a single tint cannot reproduce their multicolor source sprites. Check any final preview against the [AllTheModium source assets](https://github.com/AllTheMods/AllTheModium/tree/1.21.x/src/main/resources/assets/allthemodium/textures) and the exact mod version targeted by Forgeweave. The [ATM9 guide](https://allthemods.github.io/alltheguides/atm9/allthemodium/) is useful for identities, while the user's attached images are the direct visual reference for this task.

The [216-row inventory](material-color-inventory.tsv) records the final tint and the basis for each check: 40 exact 1.12 upstream colors, 37 Track B palette entries checked by the palette audit, 109 compatibility entries checked against source items, and 30 common or original materials reviewed against Forgeweave's own palette. "Source item reviewed" means the hue and visible character were checked. It does not mean the flat RGB is pixel-identical to a shaded or animated source sprite. A generic `c:` ingot also has no universal sprite shared by every provider.

For Psi, the historical [PlusTiC ModulePsi.java](https://github.com/TeamDman/PlusTiC/blob/107f2b7196961b87b7a4db559ff0c703b68067a9/src/main/java/landmaster/plustic/modules/ModulePsi.java) used different flat colors, but this audit follows [Psi's own 1.21 item sprites](https://github.com/VazkiiMods/Psi/tree/1.21/src/main/resources/assets/psi/textures/item) instead of a historical add-on.

### First-party sprite check

I sampled nontransparent, colored pixels from the official item PNGs (median per RGB channel, excluding saturation below 0.15 and brightness below 0.2). These samples identify hue, **not an exact tint to copy**: shadows, glints, and transparent overlays move the median. The large hue mismatches below are definite against the inspected branch:

| Material | Forgeweave | Source sprite's dominant hue | Source |
| --- | --- | --- | --- |
| Mystical Agriculture `prudentium` | `#E0812E` orange | green, sample `#007F15` | [Prudentium ingot](https://github.com/BlakeBr0/MysticalAgriculture/blob/1.21/src/main/resources/assets/mysticalagriculture/textures/item/prudentium_ingot.png) |
| Mystical Agriculture `tertium` | `#3FBFC7` cyan | orange, sample `#AA3B00` | [Tertium ingot](https://github.com/BlakeBr0/MysticalAgriculture/blob/1.21/src/main/resources/assets/mysticalagriculture/textures/item/tertium_ingot.png) |
| Mystical Agriculture `imperium` | `#F2E14C` yellow | blue, sample `#0066AA` | [Imperium ingot](https://github.com/BlakeBr0/MysticalAgriculture/blob/1.21/src/main/resources/assets/mysticalagriculture/textures/item/imperium_ingot.png) |
| Mystical Agriculture `awakened_supremium` | `#C77DF2` purple | orange/gold, sample `#A05D00` | [Awakened Supremium ingot](https://github.com/BlakeBr0/MysticalAgriculture/blob/1.21/src/main/resources/assets/mysticalagriculture/textures/item/awakened_supremium_ingot.png) |
| Powah `spirited_crystal` | `#3FD9C7` cyan | green, sample `#009700` | [Spirited crystal](https://github.com/Technici4n/Powah/blob/1.21.1/src/main/resources/assets/powah/textures/item/crystal_spirited.png) |
| Powah `nitro_crystal` | `#E63946` red | red, sample `#970000` | [Nitro crystal](https://github.com/Technici4n/Powah/blob/1.21.1/src/main/resources/assets/powah/textures/item/crystal_nitro.png); hue already correct, brightness differs |

The remaining inspected Powah crystal hues (`blazing`, `niotic`) agree broadly. Actually Additions crystal hues agree broadly too, except the `void`/`enori` sprites use strong neutral shading, which makes the sampling method unreliable for an exact tint. The six AllTheModium hues are broadly aligned with the [official 1.21.x item sprites](https://github.com/AllTheMods/AllTheModium/tree/1.21.x/src/main/resources/assets/allthemodium/textures/item), although the alloy sprites contain several hues and need visual judgement.

These colors were rechecked on the Mystical Agriculture `1.21` and Powah `1.21.1` branches. The new Forgeweave tints use the most common bright, saturated pixel from each corresponding 16×16 item sprite: prudentium `#00AD1A`, tertium `#D84800`, imperium `#0085DD`, awakened supremium `#D37B00`, and spirited crystal `#00BD00`. They are representative flat tints, not copies of the source textures.

### Draconic Evolution 1.21.1

I compared the material IDs to the item sprites in the project's resolved `Draconic-Evolution-1.21.1-3.1.4.633.jar`, under `assets/draconicevolution/textures/item/components/`. This is a build dependency of Forgeweave, and the [official Draconic Evolution repository](https://github.com/Draconic-Inc/Draconic-Evolution) identifies the source project. The old `draconium_core` gray, `wyvern` green, `awakened` yellow, and `chaotic` violet did not match their ingredient sprites. Their new tints are representative hues, since one RGB value cannot reproduce the source's lights and shadows:

| Material | Source sprite | New tint | Visual evidence |
| --- | --- | --- | --- |
| `draconium_core` | `draconium_core.png` | `#315AAB` | Blue arms and frame |
| `wyvern` | `wyvern_core.png` | `#75428D` | Purple frame and core |
| `awakened` | `awakened_core.png` | `#D7510A` | Orange and red frame |
| `chaotic` | `chaotic_core.png` | `#999999` | Black and gray frame with white center |

The existing `draconium` purple and `draconium_awakened` orange match the hues of their ingot sprites, so I left them intact.

Likewise, TAIGA's `vibranium` uses a gray Minecraft text color in its local 1.12 source (`MaterialTraits.java:72`), while the current Forgeweave entry is the green Allthemodium material. The shared name does not make these the same material. A naive name-based audit would incorrectly turn the ATM green back to gray.

## Safe next pass

For each remaining cross-mod material, resolve its `crafting_items` or compatibility namespace to the specific source mod and version, sample the visible part of that mod's ingot/gem/item sprite, and record the source path, current RGB, proposed RGB, and confidence. Compare alloys and animated items by visual preview because a single RGB average loses their defining colors. Keep Forgeweave-original materials as art-direction choices rather than attributing them to an upstream mod.

## Corrections made in this audit

Sources inspected for the other compatibility groups: [The Aether 1.21.1](https://github.com/The-Aether-Team/The-Aether/tree/1.21.1-develop/src/main/resources/assets/aether/textures/item), [Ender IO 1.21.1](https://github.com/Team-EnderIO/EnderIO/tree/1.21.1/enderio/src/main/resources/assets/enderio/textures/item), [Silent Gear 1.21.1](https://github.com/SilentChaos512/Silent-Gear/tree/1.21.1/src/main/resources/assets/silentgear/textures/item), [Psi 1.21](https://github.com/VazkiiMods/Psi/tree/1.21/src/main/resources/assets/psi/textures/item), [Occultism 1.21.1](https://github.com/klikli-dev/occultism/tree/version/1.21.1/src/main/resources/assets/occultism/textures/item), [Just Dire Things 1.21.1](https://github.com/Direwolf20-MC/JustDireThings/tree/1.21.1/src/main/resources/assets/justdirethings/textures/item), [Ice and Fire CE 1.21.1](https://github.com/IAFEnvoy/IceAndFire-CE/tree/1.21.1/src/main/resources/assets/iceandfire/textures/item), [Extreme Reactors 1.21](https://github.com/ZeroNoRyouki/ExtremeReactors2/tree/1.21/src/main/resources/assets/bigreactors/textures/item), [ProjectE 1.21.1](https://github.com/sinkillerj/ProjectE/tree/mc1.21.1/src/main/resources/assets/projecte/textures/item), [Re:Avaritia 1.21.1](https://github.com/Nova-Committee/Re-Avaritia/tree/neo/1.21.1/src/main/resources/assets/avaritia/textures/item), [Refined Storage v2.0.9](https://github.com/refinedmods/refinedstorage2/blob/v2.0.9/refinedstorage-common/src/main/resources/assets/refinedstorage/textures/item/quartz_enriched_iron.png), [PneumaticCraft 1.21](https://github.com/TeamPneumatic/pnc-repressurized/tree/1.21/src/main/resources/assets/pneumaticcraft/textures/item), [Forbidden & Arcanus 1.21.1](https://github.com/stal111/Forbidden-Arcanus/tree/1.21.1/neoforge/src/main/resources/assets/forbidden_arcanus/textures/item), [Industrial Foregoing 1.21](https://github.com/InnovativeOnlineIndustries/Industrial-Foregoing/tree/1.21/src/main/resources/assets/industrialforegoing/textures/item), and [Cataclysm 3.33](https://modrinth.com/mod/l_enders-cataclysm/version/mYBUDZWl). The 1.21 branches are the closest source version where a project did not keep a separate 1.21.1 branch. They were used to confirm broad hues, not to assert identical pixels across releases.

| Material | Before | After | Basis |
| --- | --- | --- | --- |
| `allthemodium` | `#2FBF71` | `#E98219` | ATM |
| `awakened` | `#D8A32A` | `#D7510A` | Draconic Evolution |
| `awakened_supremium` | `#C77DF2` | `#D37B00` | Mystical Agriculture |
| `azure_silver` | `#CBBAFF` | `#8E4BDA` | Silent Gear |
| `blaze_gold` | `#B56A2E` | `#E3A645` | Silent Gear |
| `blazegold` | `#E8871E` | `#D66C4C` | Just Dire Things |
| `celestigem` | `#8F5FE8` | `#19B1AD` | Just Dire Things |
| `chaotic` | `#6A1B9A` | `#999999` | Draconic Evolution |
| `compressed_iron` | `#2E4C6B` | `#858585` | PneumaticCraft |
| `conductive_alloy` | `#C4732E` | `#C78370` | Ender IO |
| `cosmic_neutronium` | `#3D1A5C` | `#3F424D` | Re:Avaritia |
| `cursium` | `#5B2C6F` | `#35BFA2` | Cataclysm |
| `cyanite` | `#1B1F3B` | `#8FC2ED` | Extreme Reactors |
| `deorum` | `#B8860B` | `#D29224` | Forbidden & Arcanus |
| `draconium_core` | `#3E4A63` | `#315AAB` | Draconic Evolution |
| `end_steel` | `#B29FE0` | `#B1B48A` | Ender IO |
| `energetic_alloy` | `#D8A23C` | `#E57E34` | Ender IO |
| `energised_steel` | `#FACC50` | `#BA9566` | Powah |
| `ferricore` | `#8C5344` | `#88B9BC` | Just Dire Things |
| `gravitite` | `#C9C9E8` | `#A52F9A` | Aether |
| `iesnium` | `#5C3A21` | `#75ACB3` | Occultism |
| `imperium` | `#F2E14C` | `#0085DD` | Mystical Agriculture |
| `inferium` | `#6EC14E` | `#94B200` | Mystical Agriculture |
| `insanium` | `#1E1E28` | `#7100B2` | Mystical Agradditions |
| `ludicrite` | `#E066CC` | `#AC48E0` | Extreme Reactors |
| `niotic_crystal` | `#8B5FBF` | `#00A4BD` | Powah |
| `pink_slime` | `#F49AC1` | `#D99BD9` | Industrial Foregoing |
| `prosperity` | `#C9E265` | `#84A2A2` | Mystical Agriculture |
| `prudentium` | `#E0812E` | `#00AD1A` | Mystical Agriculture |
| `psigem` | `#B7E8DC` | `#9574D5` | Psi |
| `psimetal` | `#6FCEDF` | `#9EACEB` | Psi |
| `pulsating_alloy` | `#7368C7` | `#43CBB0` | Ender IO |
| `quartz_enriched_iron` | `#C9A96A` | `#CDCCCA` | Refined Storage |
| `queens_slime` | `#236C45` | `#68C593` | Forgeweave ingot sprite |
| `red_matter` | `#BD2645` | `#B71B1B` | ProjectE |
| `redstone_alloy` | `#B0413E` | `#C8352B` | Ender IO |
| `soularium` | `#D9C98A` | `#A88863` | Ender IO |
| `soulium` | `#4C3468` | `#8C563C` | Mystical Agriculture |
| `spirited_crystal` | `#3FD9C7` | `#00BD00` | Powah |
| `supremium` | `#E5486B` | `#D30000` | Mystical Agriculture |
| `tertium` | `#3FBFC7` | `#D84800` | Mystical Agriculture |
| `unobtainium` | `#C724B1` | `#8C36C8` | ATM |
| `unobtainium_allthemodium_alloy` | `#8A5FC2` | `#B75A75` | ATM |
| `unobtainium_vibranium_alloy` | `#9A3FC0` | `#367E9A` | ATM |
| `vibranium` | `#6A3FA0` | `#18A878` | ATM |
| `vibranium_allthemodium_alloy` | `#5F4FA8` | `#8CA65A` | ATM |
| `vibrant_alloy` | `#4FD8B0` | `#D6F079` | Ender IO |
| `wyvern` | `#1F7A4D` | `#75428D` | Draconic Evolution |
| `zanite` | `#2FBFA0` | `#7A36E0` | Aether |

Each replacement is a representative flat tint. The source item sprites retain their own shading, highlights, or animation; a single material JSON color cannot reproduce those details.
