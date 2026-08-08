# Third-party notices

Forgeweave derives some files from [Tinkers' Construct](https://github.com/SlimeKnights/TinkersConstruct) by SlimeKnights, used under the MIT License. Per its README, Tinkers' Construct code, textures, and binaries are MIT-licensed:

> MIT License
>
> Copyright (c) SlimeKnights
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Derived files

One row per derived file (ADR-0003). Maintained in PR review: a PR introducing derived material without matching rows is not mergeable. Reference commits: branch `1.12` = `c01173c0408352c50a2e8c5017552323ce42f5b4`, branch `1.20.1` = `de26560d26c15edf93e6078520202d1c0518394e`.

| Forgeweave path | Upstream path | Upstream commit | License |
| --- | --- | --- | --- |
| `src/main/resources/data/forgeweave/forgeweave/material/wood.json` | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/material/stone.json` | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/material/flint.json` | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/material/bone.json` | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern.png` | `resources/assets/tconstruct/textures/items/pattern.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pickaxe_head.png` | `resources/assets/tconstruct/textures/items/pickaxe/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/shovel_head.png` | `resources/assets/tconstruct/textures/items/shovel/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/axe_head.png` | `resources/assets/tconstruct/textures/items/hatchet/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/tool_binding.png` | `resources/assets/tconstruct/textures/items/parts/binding.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/tool_handle.png` | `resources/assets/tconstruct/textures/items/parts/tool_rod.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/part_builder_top.png` (the table model's {@code #top} face -- own top art, never retextured) | `resources/assets/tconstruct/textures/blocks/partbuilder_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/part_builder_side.png` (the table model's {@code #side} face -- fixed trim, shared by both stations, never retextured) | `resources/assets/tconstruct/textures/blocks/table_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (blank pattern recipe) | `resources/assets/tconstruct/recipes/tools/pattern.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Part Builder block recipe) | `resources/assets/tconstruct/recipes/tools/table/part_builder.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/PartBuilderRecipes.java` (per-part material costs) | `src/main/java/slimeknights/tconstruct/tools/TinkerTools.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/tool_station_top.png` (the table model's {@code #top} face -- own top art, never retextured) | `resources/assets/tconstruct/textures/blocks/toolstation_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolStats.java` (durability/mining-speed/attack formula) | `src/main/java/slimeknights/tconstruct/library/tools/ToolNBT.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/ToolAssemblyRecipes.java` (head/binding/handle part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/Pickaxe.java`, `Shovel.java`, `Hatchet.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Tool Station block recipe shape) | `resources/assets/tconstruct/recipes/tools/table/tool_station.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ToolItem.java` (broken-state semantics, per-block durability cost) | `src/main/java/slimeknights/tconstruct/library/utils/ToolHelper.java`, `src/main/java/slimeknights/tconstruct/library/tools/ToolCore.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ForgeweaveItems.java` (per-tool attack speed and damage potential) | `src/main/java/slimeknights/tconstruct/tools/tools/Pickaxe.java`, `Shovel.java`, `Hatchet.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolRepair.java` (repair-amount formula) | `src/main/java/slimeknights/tconstruct/library/tinkering/TinkersItem.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/ToolAssemblyRecipes.java` (repair recipe resolution) | `src/main/java/slimeknights/tconstruct/library/tinkering/TinkersItem.java`, `src/main/java/slimeknights/tconstruct/library/utils/ToolHelper.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`ECOLOGICAL` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitEcological.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`CHEAP` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitCheap.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`CRUDE` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitCrude.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`FRACTURED` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitBonusDamage.java`, `src/main/java/slimeknights/tconstruct/tools/TinkerTraits.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (same-trait-applies-once stacking rule) | `src/main/java/slimeknights/tconstruct/library/traits/AbstractTraitLeveled.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveLanguageProvider.java` (trait names and descriptions) | `resources/assets/tconstruct/lang/en_us.lang` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/part_builder.json` (table element geometry) | `resources/assets/tconstruct/models/block/table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/tool_station.json` (table element geometry) | `resources/assets/tconstruct/models/block/table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/PartBuilderBlock.java` (`TABLE_SHAPE` collision box shape) | `src/main/java/slimeknights/tconstruct/shared/block/TableBlock.java` | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/ToolStationBlock.java` (`TABLE_SHAPE` collision box shape) | `src/main/java/slimeknights/tconstruct/shared/block/TableBlock.java` | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_pickaxe_head.png` (composite: pattern.png + pickaxe_head.png, darkened per `PatternTexture.java`'s algorithm via `scripts/generate_pattern_textures.py`) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_shovel_head.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_axe_head.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_tool_binding.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_tool_handle.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/part_builder.png` (cropped to the 176x166 panel region) | `resources/assets/tconstruct/textures/gui/partbuilder.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/tool_station.png` (176x166 panel region, cropped as-is; the baked-in name-textfield/button-tab chrome at x 67-174, y 2-19 -- meaningless without upstream's renaming/tool-selection buttons -- is flattened to the panel's own plain gray, no other pixels touched) | `resources/assets/tconstruct/textures/gui/toolstation.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/pickaxe_head.png` | `resources/assets/tconstruct/textures/items/pickaxe/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/pickaxe_handle.png` | `resources/assets/tconstruct/textures/items/pickaxe/handle.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/pickaxe_binding.png` | `resources/assets/tconstruct/textures/items/pickaxe/binding.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/shovel_head.png` | `resources/assets/tconstruct/textures/items/shovel/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/shovel_handle.png` | `resources/assets/tconstruct/textures/items/shovel/handle.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/shovel_binding.png` | `resources/assets/tconstruct/textures/items/shovel/binding.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/hatchet_head.png` | `resources/assets/tconstruct/textures/items/hatchet/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/hatchet_handle.png` (upstream's hatchet reuses the pickaxe's handle art) | `resources/assets/tconstruct/textures/items/pickaxe/handle.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/tools/hatchet_binding.png` | `resources/assets/tconstruct/textures/items/hatchet/binding.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/shard.png` | `resources/assets/tconstruct/textures/items/parts/shard.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/PartBuilderRecipes.java` (crafting-item value table and shard-unit normalization) and `src/main/resources/data/forgeweave/forgeweave/material/{wood,stone,flint,bone}.json` (`crafting_items` values) | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` (`setupMaterials`), `src/main/java/slimeknights/tconstruct/library/materials/Material.java` (`VALUE_Ingot`/`VALUE_Shard` constants) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/jei/ForgeweaveJeiPlugin.java` (plugin structure: registering categories separately from recipes, pairing each station item with its recipe category as a recipe catalyst) | `src/main/java/com/possibletriangle/tinkersjei/TConstructModule.java` | `47240382a962caaae023bfd3051c7d05f62587b7` | MIT |
| `src/main/java/dev/gkissel/forgeweave/jei/AssemblyRecipes.java` (showing a representative/cycling material set per tool type instead of enumerating every head x binding x handle combination) | `src/main/java/com/possibletriangle/tinkersjei/StatsWrapper.java`, `StatsCategory.java` | `47240382a962caaae023bfd3051c7d05f62587b7` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ToolTooltip.java` (compact-vs-Shift tooltip structure; green-to-red durability color formula) | `src/main/java/slimeknights/tconstruct/library/tinkering/TinkersItem.java` (`addInformation`), `src/main/java/slimeknights/tconstruct/library/tools/ToolCore.java` (`getTooltip`/`getInformation`/`getTooltipComponents`), `src/main/java/slimeknights/tconstruct/library/utils/TooltipBuilder.java`, `src/main/java/slimeknights/tconstruct/library/client/CustomFontColor.java` (`valueToColorCode`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/crafting_station.json` (table element geometry) | `resources/assets/tconstruct/models/block/table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/CraftingStationBlock.java` (`TABLE_SHAPE` collision box shape) | `src/main/java/slimeknights/tconstruct/shared/block/TableBlock.java` | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Crafting Station block recipe shape) | `resources/assets/tconstruct/recipes/tools/table/crafting_station.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/CraftingStationBlockEntity.java` (persistent 3x3 grid instead of vanilla's transient one; `findSideInventory`'s horizontal-neighbor scan for an item-handler block) | `src/main/java/slimeknights/tconstruct/tools/common/tileentity/TileCraftingStation.java`, `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerCraftingStation.java` (neighbor scan), `src/main/java/slimeknights/tconstruct/tools/common/inventory/CraftingStationItemHandler.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/CraftingStationMenu.java` (grid + output + side-inventory slot composition; real `RecipeManager` resolution against the persistent grid) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerCraftingStation.java`, `src/main/java/slimeknights/tconstruct/shared/inventory/InventoryCraftingPersistent.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/jei/CraftingStationTransferInfo.java` (recipe slots = the 3x3 grid; inventory/fill-source slots = everything after the station's own slots, including the side inventory) | `src/main/java/slimeknights/tconstruct/plugin/jei/CraftingStationRecipeTransferInfo.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/stencil_table.json` (table element geometry) | `resources/assets/tconstruct/models/block/table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/StencilTableBlock.java` (`TABLE_SHAPE` collision box shape) | `src/main/java/slimeknights/tconstruct/shared/block/TableBlock.java` | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/stencil_table.png` (cropped to the 176x166 panel region) | `resources/assets/tconstruct/textures/gui/stenciltable.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Stencil Table block recipe: blank pattern + planks) | `resources/assets/tconstruct/recipes/tools/table/stencil_table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/StencilTableMenu.java` (input/output slot layout and coordinates; selecting a pattern determines the output, taking it consumes one blank pattern -- one-way) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerStencilTable.java`, `src/main/java/slimeknights/tconstruct/tools/common/inventory/SlotStencil.java`, `src/main/java/slimeknights/tconstruct/tools/common/tileentity/TileStencilTable.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |

Each material JSON derives its stat values and tint color from that file; the Java that loads them is an
independent reimplementation against NeoForge's datapack registry API and carries no row.

The `pattern.png` texture is shared by the blank pattern and all five part-pattern item models (they
render identically upstream too; only the name/tooltip differs). The five part textures are the
greyscale/base variants from `items/parts/*.png` and `items/<tool>/head.png`, chosen per ADR-0003 and
the M1 issue brief because they are designed for client-side tinting rather than a fixed material
color, matching Forgeweave's `RegisterColorHandlersEvent.Item`-based tint approach. All item model
JSONs referencing these textures are written fresh for 1.21's `item/generated` format and carry no
row of their own.

**Superseded by issue #43** (station and item visual fidelity): the Part Builder and Tool Station
block models are no longer plain top/side/bottom cubes, and their GUI backgrounds are no longer
freshly-authored flat panels -- see the rows above (table geometry, GUI crops, tool layer art) and
below for what replaced them. The `part_builder_top.png`/`part_builder_side.png`/
`tool_station_top.png` textures those old cube models used (moved to `textures/derived/block/`) are
reused by the new table models too -- the table's own top/rim faces keep this fixed art rather than
being retextured to the crafting wood (issue #43 regression fix: an earlier cut of the table model
retextured every quad, including these).

Trait behavior (issue #12) is ported semantics, not copied code: the rates, conditions and magnitudes
are upstream's, the `Trait` interface and its registry are written fresh against 1.21's data
components and NeoForge events. Two deliberate deviations, both forced by ADR-0002's one-trait-id-per-
material data model: upstream's flint grants `crude2` on the head part plus `crude` elsewhere, which
stacks to level 3 (+15% vs unarmored), while Forgeweave's flint grants plain `crude` (level 1, +5%);
and upstream's stone grants `cheapskate` on the head part on top of `cheap`, a durability *penalty*
Forgeweave does not ship (stone's lower durability is already in its material JSON). `Trait.java`
carries only the four hooks the four shipped traits use rather than upstream's full `ITrait` surface,
so it is not a derived file and carries no row.

Both stations are now table-shaped (tabletop + 4 legs, hollow underside) and retain the appearance
of the wood block they were crafted from: `WoodTexturedBlockEntity` stores the crafting wood as a
`Block`, `RetexturedShapedRecipe` copies it from whichever `BlockItem` ingredient was used onto the
crafted item's `TEXTURE` data component, and `RetexturedTableBakedModel`
(`dev.gkissel.forgeweave.client.model`) remaps the table model's baked quads from the default
(oak) sprite to the stored wood's particle sprite at render time -- the standard NeoForge
`IGeometryLoader`/`IUnbakedGeometry`/`ModelData` approach, since Mantle's real
`RetexturedBlock`/`RetexturedHelper`/`IRetexturedBlockEntity` (which this would otherwise port) is
not in the 1.20.1 reference clone. None of that machinery is copied from either clone, so it carries
no additional row beyond the table-geometry/collision-shape rows above.

Part pattern items are single-layer now: the five composite PNGs above replace the old two-layer
"pattern base (layer0) + faint greyscale part overlay (layer1)" item models. The blank pattern has
no part to etch onto it, so it stays the plain `pattern.png` base with no composite.

Assembled tool item models (`ToolItem`) now use the dedicated per-tool layer textures rowed above
(`textures/derived/tools/<tool>_{handle,head,binding}.png`), positioned for the assembled tool,
instead of the standalone part sprites (`pickaxe_head.png` etc., still used for the loose `PartItem`
icons) -- those are centered for a loose inventory item and looked jumbled/overlapping when reused
as tool layers. `ForgeweaveItemColors#toolMaterialTint`'s tintIndex-to-material mapping was updated
to match the new layer0=handle/layer1=head/layer2=binding order (upstream's own tool models use the
same order); this is a code change, not a new derived file, so it carries no row of its own.

The `pattern.png` texture is shared by the blank pattern's item model and as the base every composite
pattern PNG above is built from.

The Crafting Station (issue #40) reuses issue #43's table-shape/wood-retexture machinery verbatim
(`WoodTexturedBlockEntity`, `RetexturedTableGeometry`, `RetexturedShapedRecipe`) for family
consistency with the Part Builder and Tool Station, rather than porting upstream's unique
`craftingstation_top.png`/`craftingstation_side.png` block textures -- this project's own precedent
already consolidates every table face onto one retexturing `#texture` slot (see the "Both stations"
paragraph above), so there is no unique block art to derive and no new `textures/derived/block/`
directory or block atlas entry is needed. Its crafting recipe (a vanilla crafting table, not a wood
tag) means a crafted Crafting Station's `TEXTURE` component resolves to `minecraft:crafting_table`
rather than a log/plank block -- CraftingStationBlock's javadoc covers this outcome.

The Crafting Station's GUI background is vanilla's own `textures/gui/container/crafting_table.png`,
referenced by resource location at render time rather than copied into `textures/derived/gui/`:
upstream 1.12's `GuiCraftingStation` does exactly this too (no TinkersConstruct-original art of its
own for this screen), so there is nothing to derive and no NOTICE.md row for it. The side-inventory
panel (when an adjacent item-handler block is present) is composited at render time from repeated
blits of that same texture's own crafting-grid slot tile rather than a second pre-baked image, since
its slot count varies per placement -- see `CraftingStationScreen`.

The two `jei/` rows above (issue #11) cite **`PssbleTrngle/TinkersJEI`**, a separate MIT-licensed
repository from the TinkersConstruct clones the rest of this document cites -- docs/SCOPE.md names it
"Tinker's JEI" and marks it derivation-eligible for M1/M8's JEI plugin. Its 1.12 Forge `IModPlugin`/
`IRecipeCategory` API is unrelated to and incompatible with JEI's current NeoForge 1.21 API, so what
carries over is structure and design choices (how the plugin registers itself, and rendering a
cycling material set instead of every combination), not code: `ForgeweaveJeiPlugin`/the `jei/`
package are written fresh against the modern API. Every other Forgeweave file in `jei/` (the
`*Category`/`*Recipe`/`*Recipes` classes not rowed above) is fresh code with no upstream analog and
carries no row.

The Stencil Table (issue #44) reuses issue #43's table-shape/wood-retexture machinery verbatim
(`WoodTexturedBlockEntity`, `RetexturedTableGeometry`, `RetexturedShapedRecipe`) for family
consistency with the other three stations, same as the Crafting Station paragraph above -- its
crafting recipe (blank pattern + planks) matches upstream's real `#STENCIL_TABLE` tag resolution
(`plankWood`) exactly, so no maintainer deviation was needed for the recipe shape, unlike the Tool
Station and Crafting Station recipes.

Selecting a pattern (issue #44) is ported semantics, not copied code: upstream's `TinkerRegistry`
dynamically registers one stencil-table candidate per material variant of each part pattern (since
1.12 patterns carry an NBT material tag) and syncs the selection with a bespoke
`StencilTableSelectionPacket`. Forgeweave's five part patterns are plain, material-less items
(`ForgeweaveItems`), so `StencilTableMenu#PATTERNS` is a fixed, ordered list instead of a dynamic
registry, and the selection syncs through the standard vanilla menu-button/`DataSlot` mechanism
(`AbstractContainerMenu#clickMenuButton`, the same one `StonecutterMenu`/`LoomMenu` use) rather
than a custom packet -- no NOTICE.md row for that substitution since it carries no upstream code.
The five pattern-selection buttons in `StencilTableScreen` have no baked art to derive (upstream's
`GuiButtonsStencilTable` draws them from its own button-icon sprite sheet, which isn't part of the
cropped `stenciltable.png` panel), so they're drawn procedurally from `GuiGraphics` primitives
instead, the same approach `CraftingStationScreen` uses for its side-inventory panel.

The five blank-pattern-to-part-pattern vanilla-table conversion recipes issue #42 shipped in
`ForgeweaveRecipeProvider` (blank + matching wooden tool/stick, shapeless) are removed by issue #44:
the Stencil Table's GUI is now the only conversion path, matching upstream 1.12's real
stencil-shaping flow (a dedicated GUI, not a vanilla-table recipe) instead of the vanilla-table
stand-in #42 shipped before the Stencil Table existed.

Tool tooltips (issue #54) port upstream 1.12's compact-by-default/Shift-for-detail structure
(`TinkersItem#addInformation`) and its durability green-to-red color math (`CustomFontColor
#valueToColorCode`), but not its third Ctrl-held components view (`ToolCore#getTooltipComponents`)
or its modifier lines (`TooltipBuilder#addModifierInfo`/`#addFreeModifiers`) -- Forgeweave has no
modifier system yet (M2) and no separate Ctrl view, so the parts/traits content upstream shows on
Ctrl is folded into Forgeweave's Shift view instead. Tool tier is displayed by deriving a word from
each material's `incorrect_for_<tier>_tool` block tag path rather than porting upstream's numeric
`HarvestLevels` name table, since CONTEXT.md already requires the vanilla-tag tier system.
