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
| `src/main/resources/assets/forgeweave/textures/derived/block/part_builder_side.png` (the table model's {@code #side} face -- fixed trim, shared by the Part Builder, Tool Station and Stencil Table, never retextured) | `resources/assets/tconstruct/textures/blocks/table_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/crafting_station_top.png` (the table model's `#top` face -- own top art, never retextured; issue #68 fix 2) | `resources/assets/tconstruct/textures/blocks/craftingstation_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/crafting_station_side.png` (the table model's `#side` face -- the Crafting Station has its own trim upstream, unlike the other three tables; issue #68 fix 2) | `resources/assets/tconstruct/textures/blocks/craftingstation_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/stencil_table_top.png` (the table model's `#top` face -- own top art, never retextured; issue #68 fix 2) | `resources/assets/tconstruct/textures/blocks/stenciltable_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/generic.png` (copied unmodified; the nine-sliced border, slot tile and empty-slot tile every station's side-inventory panel is drawn from; issue #68 fix 3) | `resources/assets/tconstruct/textures/gui/generic.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/SideInventoryPanel.java` (border/slot/empty-slot sprite regions, 6-column grid, row cap and scroll-by-row behaviour) | `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiSideInventory.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiGeneric.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiWidgetBorder.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/crafting_station.json` (`#top`/`#side`/`#texture` slot split and which upstream texture fills each) | `resources/assets/tconstruct/models/block/craftingstation.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/stencil_table.json` (`#top`/`#side`/`#texture` slot split and which upstream texture fills each) | `resources/assets/tconstruct/models/block/stenciltable.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/StencilTableScreen.java` (pattern buttons as a left-hand 4-column side-button grid drawn from the wood-style button sprites; the pattern hint glyph in the input slot) | `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiButtonsStencilTable.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiSideButtons.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/GuiStencilTable.java`, `src/main/java/slimeknights/tconstruct/library/client/Icons.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (blank pattern recipe) | `resources/assets/tconstruct/recipes/tools/pattern.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Part Builder block recipe) | `resources/assets/tconstruct/recipes/tools/table/part_builder.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/PartBuilderRecipes.java` (per-part material costs) | `src/main/java/slimeknights/tconstruct/tools/TinkerTools.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/tool_station_top.png` (the table model's {@code #top} face -- own top art, never retextured) | `resources/assets/tconstruct/textures/blocks/toolstation_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolStats.java` (durability/mining-speed/attack formula) | `src/main/java/slimeknights/tconstruct/library/tools/ToolNBT.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/ToolAssemblyRecipes.java` (head/binding/handle part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/Pickaxe.java`, `Shovel.java`, `Hatchet.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Tool Station block recipe shape) | `resources/assets/tconstruct/recipes/tools/table/tool_station.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ToolItem.java` (broken-state semantics, per-block durability cost, per-hit durability cost `attackDurabilityCost`) | `src/main/java/slimeknights/tconstruct/library/utils/ToolHelper.java`, `src/main/java/slimeknights/tconstruct/library/tools/ToolCore.java` (`reduceDurabilityOnHit`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ForgeweaveItems.java` (per-tool attack speed and damage potential) | `src/main/java/slimeknights/tconstruct/tools/tools/Pickaxe.java`, `Shovel.java`, `Hatchet.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolRepair.java` (repair-amount formula) | `src/main/java/slimeknights/tconstruct/library/tinkering/TinkersItem.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/ToolAssemblyRecipes.java` (repair recipe resolution) | `src/main/java/slimeknights/tconstruct/library/tinkering/TinkersItem.java`, `src/main/java/slimeknights/tconstruct/library/utils/ToolHelper.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`ECOLOGICAL` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitEcological.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`CHEAP` behavior: the repair bonus from `TraitCheap`, the head-only 20% durability penalty from `TraitCheapskate`) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitCheap.java`, `src/main/java/slimeknights/tconstruct/tools/traits/TraitCheapskate.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
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
| `src/main/resources/assets/forgeweave/textures/derived/gui/tool_station.png` (the whole 256x256 sheet, copied unmodified: issue #47 puts every region back in use -- the 176x174 panel, the rename field's highlight strip, the translucent item cover, the slot background/border sprites and the beam pieces -- so issue #43's cropped-and-flattened variant of this file is gone) | `resources/assets/tconstruct/textures/gui/toolstation.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/station_icons.png` (copied unmodified; used for the Tool Station's sidebar button sprites, its anvil glyph, and both stations' empty-slot hint icons) | `resources/assets/tconstruct/textures/gui/icons.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/info_panel.png` (copied unmodified; the nine-sliced frame both stations' information panels are drawn from) | `resources/assets/tconstruct/textures/gui/panel.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/ToolStationTabs.java` (per-tool slot-position tables and the repair layout) | `src/main/java/slimeknights/tconstruct/library/client/ToolBuildGuiInfo.java`, `src/main/java/slimeknights/tconstruct/tools/harvest/HarvestClientProxy.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/GuiButtonRepair.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/InfoPanel.java` (nine-slice geometry, wood-style sheet offset, caption/body text layout) | `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiInfoPanel.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/ToolStationScreen.java` (texture regions, sidebar grid rule, item-preview/cover/slot-sprite draw order, rename-field placement, info-panel content split) | `src/main/java/slimeknights/tconstruct/tools/common/client/GuiToolStation.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiSideButtons.java`, `src/main/java/slimeknights/tconstruct/library/client/Icons.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/PartBuilderScreen.java` (information panel, "Material Value" readout, empty-slot hint icons) | `src/main/java/slimeknights/tconstruct/tools/common/client/GuiPartBuilder.java`, `src/main/java/slimeknights/tconstruct/library/client/Icons.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveLanguageProvider.java` (station info-panel and material-value wording) | `resources/assets/tconstruct/lang/en_us.lang` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/StationText.java` (per-stat text colors, the durability wear ramp, and trait names in the granting material's color) | `src/main/java/slimeknights/tconstruct/library/materials/HeadMaterialStats.java`, `HandleMaterialStats.java`, `ExtraMaterialStats.java`, `AbstractMaterialStats.java`, `src/main/java/slimeknights/tconstruct/library/client/CustomFontColor.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/ToolStationScreen.java` (per-tab tool preview built from fixed GUI material colors, oversized repair anvil, repair-slot glyph order) | `src/main/java/slimeknights/tconstruct/tools/common/client/GuiToolStation.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/GuiButtonRepair.java`, `src/main/java/slimeknights/tconstruct/tools/ToolClientProxy.java`, `src/main/java/slimeknights/tconstruct/library/tinkering/TinkersItem.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/PartItem.java` (per-part-kind stat block plus trait on the hover text, gated behind Shift) | `src/main/java/slimeknights/tconstruct/library/tools/ToolPart.java`, `src/main/java/slimeknights/tconstruct/library/materials/PartMaterialType.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ForgeweaveItems.java` (patterns stack to the vanilla maximum) | `src/main/java/slimeknights/tconstruct/library/tools/Pattern.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
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
| `src/main/java/dev/gkissel/forgeweave/block/CraftingStationBlockEntity.java` (persistent 3x3 grid instead of vanilla's transient one) | `src/main/java/slimeknights/tconstruct/tools/common/tileentity/TileCraftingStation.java`, `src/main/java/slimeknights/tconstruct/tools/common/inventory/CraftingStationItemHandler.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SideInventory.java` (`find`'s horizontal-neighbor scan for an item-handler block -- extracted from `CraftingStationBlockEntity#findSideInventory` when this issue's follow-up reused it for the Part Builder and Tool Station) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerCraftingStation.java` (neighbor scan) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/CraftingStationMenu.java` (grid + output + side-inventory slot composition; real `RecipeManager` resolution against the persistent grid) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerCraftingStation.java`, `src/main/java/slimeknights/tconstruct/shared/inventory/InventoryCraftingPersistent.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/jei/CraftingStationTransferInfo.java` (recipe slots = the 3x3 grid; inventory/fill-source slots = everything after the station's own slots, including the side inventory) | `src/main/java/slimeknights/tconstruct/plugin/jei/CraftingStationRecipeTransferInfo.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/stencil_table.json` (table element geometry) | `resources/assets/tconstruct/models/block/table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/StencilTableBlock.java` (`TABLE_SHAPE` collision box shape) | `src/main/java/slimeknights/tconstruct/shared/block/TableBlock.java` | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/stencil_table.png` (cropped to the 176x166 panel region) | `resources/assets/tconstruct/textures/gui/stenciltable.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Stencil Table block recipe: blank pattern + planks) | `resources/assets/tconstruct/recipes/tools/table/stencil_table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/StencilTableMenu.java` (input/output slot layout and coordinates; selecting a pattern determines the output, taking it consumes one blank pattern -- one-way) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerStencilTable.java`, `src/main/java/slimeknights/tconstruct/tools/common/inventory/SlotStencil.java`, `src/main/java/slimeknights/tconstruct/tools/common/tileentity/TileStencilTable.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/pattern_chest_front.png` | `resources/assets/tconstruct/textures/blocks/chest/pattern_front.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/pattern_chest_side.png` | `resources/assets/tconstruct/textures/blocks/chest/pattern_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/pattern_chest_top.png` | `resources/assets/tconstruct/textures/blocks/chest/pattern_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/part_chest_front.png` | `resources/assets/tconstruct/textures/blocks/chest/part_front.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/part_chest_side.png` | `resources/assets/tconstruct/textures/blocks/chest/part_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/part_chest_top.png` | `resources/assets/tconstruct/textures/blocks/chest/part_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Pattern Chest recipe shape: blank pattern over a vanilla chest) | `resources/assets/tconstruct/recipes/tools/table/chest/pattern.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Pattern Chest second recipe shape: a ring of 8 planks around a blank pattern, same `pattern_chest` recipe group as the row above) | `resources/assets/tconstruct/recipes/tools/table/chest/pattern_simple.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Part Chest recipe shape: blank pattern + 2 sticks over a vanilla chest, plank below) | `resources/assets/tconstruct/recipes/tools/table/chest/part.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |

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
components and NeoForge events. Three deliberate deviations, all forced by ADR-0002's
one-trait-id-per-material data model, which cannot express upstream's per-part trait assignment:

- **Flint's `crude2`**: upstream's flint grants `crude2` on the head part plus `crude` elsewhere,
  which stacks to level 3 (+15% vs unarmored), while Forgeweave's flint grants plain `crude`
  (level 1, +5%).
- **Stone's `cheapskate`**: upstream's stone grants `cheapskate` on the head part
  (`TinkerMaterials`: `stone.addTrait(cheapskate, HEAD)`) on top of `cheap`. Both behaviors ship,
  folded into the single `forgeweave:cheap` id (`ForgeweaveTraits#CHEAP`): `cheap`'s +5% repair from
  `TraitCheap#onToolHeal`, and `cheapskate`'s `max(1, durability * 80 / 100)` from
  `TraitCheapskate#onToolBuilding`, applied at assembly through `Trait#headDurability` so it stays
  head-only exactly as upstream's assignment is. (Issue #79 corrected this entry, which previously
  claimed the penalty was already baked into stone's material JSON stats and so did not need to
  ship. It was not: those stats are upstream's `HeadMaterialStats`/`HandleMaterialStats`/
  `ExtraMaterialStats` values verbatim, which upstream itself then multiplies by 0.8 at build time,
  so every stone-headed tool was about 25% too durable.)
- **Bone's `splintering`**: upstream's bone grants `splintering` on the head part plus `fractured`
  everywhere; Forgeweave's bone grants plain `fractured`, so the stacking `splinter` potion effect
  `TraitSplintering` applies on hit (+0.3 damage per stack, up to 5) is not shipped. Upstream's third
  bone trait, `splitting` on the SHAFT part, is a bow doubleshot chance with nothing to act on in M1
  and is not counted as a deviation.

`Trait.java` carries only the five hooks the four shipped traits use rather than upstream's full
`ITrait` surface, so it is not a derived file and carries no row.

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

The station GUIs (issue #47) follow upstream 1.12's layout, geometry and texture regions, with four
deliberate deviations, none of which change what is derived:

- **Tab selection travels as a `DataSlot` set from `AbstractContainerMenu#clickMenuButton`**, not as
  upstream's bespoke `ToolStationSelectionPacket`. Upstream needed a packet because its selection also
  carried a slot *count* for a container whose slot count varies per tool; every Forgeweave tool is a
  three-part tool and repair reuses the same three slots, so a single synced index is the whole state.
  This is the same mechanism the Stencil Table already uses.
- **Slot repositioning replaces `Slot` objects instead of mutating `Slot.xPos`/`yPos`**, because those
  fields are final in modern Minecraft. Same result, same coordinates.
- **Ghost part icons are the part's own item sprite blitted at 40% alpha**, where upstream renders a
  real item stack of an internal "GUI material" whose texture is generated at stitch time by
  `GuiOutlineTexture`. Forgeweave has no runtime texture-generation system (the same reason the
  pattern composites above are pre-baked), so the visible difference is a translucent part instead of
  a dark outline.
- **The information panels sit to the right of the main panel rather than the left**, per issue #47's
  brief; upstream's own stacked tool-info-over-traits structure, geometry and content split are
  otherwise unchanged. Upstream's scrollbar widget is replaced with mouse-wheel scrolling.

The Crafting Station (issue #40) reuses issue #43's table-shape/wood-retexture machinery verbatim
(`WoodTexturedBlockEntity`, `RetexturedTableGeometry`, `RetexturedShapedRecipe`) for family
consistency with the Part Builder and Tool Station. Issue #68 fix 2 finished that consistency: the
Crafting Station and Stencil Table models had kept every face on the single retexturing `#texture`
slot, which meant the crafting wood painted over their table tops, so both now use the same
`#top`/`#side`/`#texture` split the Part Builder and Tool Station got in #57 and derive upstream's
own `craftingstation_top.png`/`craftingstation_side.png`/`stenciltable_top.png` art (rowed above).
Its *recipe* no longer uses that machinery, though: issue #68 fix 7 restored upstream's own
`crafting_station.json`, a bare shapeless "any workbench", so a crafted Crafting Station carries no
`TEXTURE` component at all and renders in the model's default wood -- which is exactly what
upstream's does.

Upstream splits its four station recipes two ways: `part_builder.json` and `stencil_table.json` use
its retexturing `tconstruct:table_recipe` type, while `crafting_station.json` and `tool_station.json`
are plain recipes whose output has no wood variant at all. Forgeweave matches that split for three of
the four. **The Tool Station is the remaining deviation** (clarified in issue #79, which found this
paragraph reading as though all four matched): Forgeweave builds it with
`RetexturedShapedRecipe`, so a Tool Station crafted from a crafting table carries a `TEXTURE`
component and keeps that wood, where upstream's is a plain `forge:ore_shaped` recipe and upstream's
Tool Station has no per-wood variant. The recipe's *ingredients* are upstream's (blank pattern over a
crafting table, restored by #68 fix 7); only the retexturing is added, and it makes the Tool Station
consistent with the Part Builder and Stencil Table standing beside it.

Restoring the two upstream ingredients was not cosmetic. The Tool Station recipe had been deviated
from upstream's `workbench` to `#planks` and the Crafting Station from upstream's shapeless form to
the family's "pattern over an ingredient" shape; the first deviation made the Tool Station recipe
byte-for-byte identical to the Stencil Table's, so the recipe manager resolved that one shape to
whichever it indexed first and **the Stencil Table became uncraftable**. Both stations now use
upstream's ingredients, which are all distinct. `gametest.RecipeShapeGameTests` is the regression
guard.

All four table models carry `"parent": "minecraft:block/block"` solely for its display transforms
(issue #68 fix 6): a custom-loader model with no parent bakes with `ItemTransforms.NO_TRANSFORMS`,
which made every station's *item* form render unrotated and unscaled -- a flat slab in the creative
tab, inventory, hotbar and JEI outputs. Station items render as the model's default (oak) wood in
every item context; per-stack retexture of the item form would need an `ItemOverrides`-resolved
model variant per wood and was judged disproportionate to the defect, which the maintainer's issue
#68 note explicitly allows.

The Crafting Station's GUI background is vanilla's own `textures/gui/container/crafting_table.png`,
referenced by resource location at render time rather than copied into `textures/derived/gui/`:
upstream 1.12's `GuiCraftingStation` does exactly this too (no TinkersConstruct-original art of its
own for this screen), so there is nothing to derive and no NOTICE.md row for it. (Issue #68 fix 1 was
that this blit passed the 176x166 *panel* size as the source sheet size; vanilla's file is a 256x256
sheet, so the whole sheet was being squashed into the panel's footprint.)

The side-inventory panel every station shows next to an adjacent item-handler block is now upstream's
`GuiSideInventory` module, drawn from the derived `generic.png` rowed above (issue #68 fix 3); it
used to be a translucent rectangle behind a nine-column grid of whatever slot sprite each screen had
to hand, which with a double chest produced an unstyled 9x6 grid sprawling across the screen.
Upstream's slider widget is replaced with mouse-wheel scrolling, the same substitution `InfoPanel`
already makes for upstream's panel scrollbar.

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
consistency with the other three stations, same as the Crafting Station paragraph above.

**Deviation, corrected in issue #79**: this used to claim the Stencil Table recipe matched upstream's
`#STENCIL_TABLE` tag "exactly". It does not, and neither does the Part Builder's. Upstream's
`recipes/_constants.json` defines both as an ore dict *plus* three hand-listed extras:

| Upstream constant | Upstream ingredient | Forgeweave ingredient |
| --- | --- | --- |
| `#STENCIL_TABLE` | `plankWood`, `minecraft:rail`, `minecraft:melon_block`, `tconstruct:firewood` | `#minecraft:planks` |
| `#PART_BUILDER` | `logWood`, `minecraft:golden_rail`, `minecraft:cactus`, `tconstruct:firewood` (variant 1) | `#minecraft:logs` |

The vanilla tags cover the ore-dict half (`plankWood`/`logWood` resolve to vanilla planks/logs, and
modded planks and logs join `#minecraft:planks`/`#minecraft:logs` by the same convention). The three
extras per recipe are not covered: two of them are joke ingredients for a block that has nothing to
do with rails or melons, and `firewood` is a TinkersConstruct block Forgeweave does not have. They
are deliberately **not** added — a recipe that accepts a rail is a worse recipe, and every station
recipe already collided once over shape (issue #68 fix 7, below), so widening ingredients is a risk
with no gameplay payoff. Apart from these two ingredient sets, every station recipe is
upstream-literal.

Selecting a pattern (issue #44) is ported semantics, not copied code: upstream's `TinkerRegistry`
dynamically registers one stencil-table candidate per material variant of each part pattern (since
1.12 patterns carry an NBT material tag) and syncs the selection with a bespoke
`StencilTableSelectionPacket`. Forgeweave's five part patterns are plain, material-less items
(`ForgeweaveItems`), so `StencilTableMenu#PATTERNS` is a fixed, ordered list instead of a dynamic
registry, and the selection syncs through the standard vanilla menu-button/`DataSlot` mechanism
(`AbstractContainerMenu#clickMenuButton`, the same one `StonecutterMenu`/`LoomMenu` use) rather
than a custom packet -- no NOTICE.md row for that substitution since it carries no upstream code.
The five pattern-selection buttons in `StencilTableScreen` were originally drawn as procedural
coloured rectangles on top of the panel art. Issue #68 fix 5 replaced that with upstream's own
arrangement: `GuiButtonsStencilTable` is a `GuiSideButtons` grid `GuiStencilTable.Column_Count` (4)
wide sitting to the *left* of the panel, drawn from the wood-style button sprites in `icons.png` --
already derived here as `station_icons.png` for the Tool Station's tab sidebar, which is the same
upstream widget with the same 18px/4px geometry.

Issue #75 corrected where those button sprites actually live on that sheet. Upstream's `Icons` puts
`ICON_Button` at `(180, 216)` with hover/pressed at `±36` in x, and `GuiButtonsStencilTable#shiftButton`
shifts the set `(0, 18)` for the wood style, so the wood button row is at **v = 234**. Both
`StencilTableScreen` and `ToolStationScreen` had `v = 180`, 54px too high, which lands on a plain
decorative plank tile with no button bevel -- the sidebars rendered as flat wooden squares with no
idle/hover/pressed distinction. Issue #75 also added the pattern hint glyph `GuiStencilTable` draws
in its input slot (`Icons#ICON_Pattern`, `(0, 216)`).

The five blank-pattern-to-part-pattern vanilla-table conversion recipes issue #42 shipped in
`ForgeweaveRecipeProvider` (blank + matching wooden tool/stick, shapeless) are removed by issue #44:
the Stencil Table's GUI is now the only conversion path, matching upstream 1.12's real
stencil-shaping flow (a dedicated GUI, not a vanilla-table recipe) instead of the vanilla-table
stand-in #42 shipped before the Stencil Table existed.

The Pattern Chest and Part Chest (issue #66) port upstream 1.12's `TilePatternChest`/`TilePartChest`
filter semantics (`isItemValidForSlot`, NOTICE.md rows above cite the block textures and recipe
shapes; the filter logic itself is ported semantics, not copied code, so it carries no additional
row) with two deliberate M1 simplifications, both called out in the issue #66 brief itself:

- **Block shape**: a plain facing-aware cube (vanilla `orientable` front/side/top model, the same
  shape furnaces use) instead of upstream's table-with-drawers geometry (`models/block/
  patternchest.json`: legs, a drawer front/back, and handles on the same 1x1 footprint). The
  upstream chest textures above are reused on the plain cube's front/side/top faces; the
  drawer-specific texture regions (`*_drawer_front.png`/`*_drawer_side.png`) have no cube face to
  map onto and are not derived.
- **Capacity**: a fixed 54-slot (6x9, double-chest-sized) grid instead of upstream's `TileTinkerChest`
  virtual list (up to 256 items behind a GUI window that dynamically grows/shrinks with content,
  `GuiScalingChest`). `ChestBlockEntity`'s javadoc covers the reasoning; `ChestScreen` reuses vanilla's
  own double-chest `generic_54.png` background rather than cropping upstream's chest GUI art, which is
  drawn for that dynamic window and has no matching fixed-grid panel to crop.

Upstream's Pattern Chest additionally accepts `ICast` items ("cast chest" mode) -- Forgeweave has no
smeltery/casting system in scope yet, so that branch, and the "no duplicate item across slots"
polish upstream's `isItemValidForSlot` also applies to both chests, are not ported; a chest here can
hold multiple stacks of the same pattern/part across different slots, which is a strict superset of
what upstream allowed rather than a capability gap.

Tool tooltips (issue #54) port upstream 1.12's compact-by-default/Shift-for-detail structure
(`TinkersItem#addInformation`) and its durability green-to-red color math (`CustomFontColor
#valueToColorCode`), but not its third Ctrl-held components view (`ToolCore#getTooltipComponents`)
or its modifier lines (`TooltipBuilder#addModifierInfo`/`#addFreeModifiers`) -- Forgeweave has no
modifier system yet (M2) and no separate Ctrl view, so the parts/traits content upstream shows on
Ctrl is folded into Forgeweave's Shift view instead. Tool tier is displayed by deriving a word from
each material's `incorrect_for_<tier>_tool` block tag path rather than porting upstream's numeric
`HarvestLevels` name table, since CONTEXT.md already requires the vanilla-tag tier system.
