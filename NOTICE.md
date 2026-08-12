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
| `src/main/resources/data/forgeweave/forgeweave/material/iron.json` (head/handle/extra stats, `magnetic`/`magnetic2` trait wiring) | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` (`registerToolMaterialStats`, `setupMaterials`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/material/copper.json` (head/handle/extra stats, `established` trait wiring) | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` (`registerToolMaterialStats`, `setupMaterials`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/material/cobalt.json` (head/handle/extra stats, `lightweight`/`momentum` trait wiring) | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` (`registerToolMaterialStats`, `setupMaterials`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/material/ardite.json` (head/handle/extra stats, `petramor`/`stonebound` trait wiring) | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` (`registerToolMaterialStats`, `setupMaterials`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/material/manyullyn.json` (head/handle/extra stats, `coldblooded`/`insatiable` trait wiring) | `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` (`registerToolMaterialStats`, `setupMaterials`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cobalt_ingot.png` | `resources/assets/tconstruct/textures/items/materials/ingot_cobalt.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cobalt_nugget.png` | `resources/assets/tconstruct/textures/items/materials/nugget_cobalt.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/ardite_ingot.png` | `resources/assets/tconstruct/textures/items/materials/ingot_ardite.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/ardite_nugget.png` | `resources/assets/tconstruct/textures/items/materials/nugget_ardite.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/manyullyn_ingot.png` | `resources/assets/tconstruct/textures/items/materials/ingot_manyullyn.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/manyullyn_nugget.png` | `resources/assets/tconstruct/textures/items/materials/nugget_manyullyn.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/rose_gold_ingot.png` (recoloured to rose gold's `#B76E79`; shape only, not upstream's colour -- rose gold has no 1.12 material to derive one from) | `resources/assets/tconstruct/textures/items/materials/ingot_manyullyn.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/rose_gold_nugget.png` (same recolour) | `resources/assets/tconstruct/textures/items/materials/nugget_manyullyn.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
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
| `src/main/resources/assets/forgeweave/models/block/crafting_station.json` (`#top`/`#side`/`#texture`/`#bottom`/`#legBottom` slot split and which upstream texture fills each) | `resources/assets/tconstruct/models/block/craftingstation.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/stencil_table.json` (`#top`/`#side`/`#texture`/`#bottom`/`#legBottom` slot split and which upstream texture fills each) | `resources/assets/tconstruct/models/block/stenciltable.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
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
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`DEFAULT_SLOTS = 3`) | `src/main/java/slimeknights/tconstruct/library/tools/ToolCore.java` (`DEFAULT_MODIFIERS`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`HASTE` behavior: the mining-speed steps, the flat per-level bonus, the attack-speed multiplier, 50 redstone per level) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModHaste.java`, `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (level-from-application-count stepping, free-slot accounting, extra-slot hook) | `src/main/java/slimeknights/tconstruct/library/modifiers/ModifierAspect.java` (`MultiAspect`, `LevelAspect`, `FreeModifierAspect`), `src/main/java/slimeknights/tconstruct/tools/modifiers/ModCreative.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ModifierEntry.java` (the id/level pair stored per modifier) | `src/main/java/slimeknights/tconstruct/library/modifiers/ModifierNBT.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ModifierApplication.java` (partial-fill application loop, reagent consumption) | `src/main/java/slimeknights/tconstruct/library/utils/ToolBuilder.java` (`tryModifyTool`), `src/main/java/slimeknights/tconstruct/library/modifiers/ModifierAspect.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/haste.json` (redstone, 50 per level, 5 levels) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveLanguageProvider.java` (modifier names and descriptions) | `resources/assets/tconstruct/lang/en_us.lang` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
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
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`MAGNETIC`/`MAGNETIC2` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitMagnetic.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`MOMENTUM` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitMomentum.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`LIGHTWEIGHT` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitLightweight.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`STONEBOUND` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitStonebound.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`PETRAMOR` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitPetramor.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`INSATIABLE` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitInsatiable.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`COLDBLOODED` behavior) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitColdblooded.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java` (`ESTABLISHED` behavior, kill-XP half only) | `src/main/java/slimeknights/tconstruct/tools/traits/TraitEstablished.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ForgeweaveItems.java` (patterns stack to the vanilla maximum) | `src/main/java/slimeknights/tconstruct/library/tools/Pattern.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/molten_metal.png` (shared greyscale still texture, tinted per fluid -- issue #92) | `resources/assets/tconstruct/textures/blocks/fluids/molten_metal.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/molten_metal_flow.png` (shared greyscale flowing texture, tinted per fluid -- issue #92) | `resources/assets/tconstruct/textures/blocks/fluids/molten_metal_flow.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/fluid/ForgeweaveFluids.java` (per-fluid temperature and tint color constants for iron, gold, cobalt, ardite, manyullyn, copper) | `src/main/java/slimeknights/tconstruct/shared/TinkerFluids.java` (`setupFluids`), `src/main/java/slimeknights/tconstruct/tools/TinkerMaterials.java` (`materialTextColor`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
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
| `src/main/java/dev/gkissel/forgeweave/menu/StationGroup.java` (the connected-station flood fill: horizontal neighbours only, one member per station kind, the gui-number tab order, the "no tabs without a Crafting Station" gate, and stashing the cursor stack across a tab switch) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerTinkerStation.java` (`detectedTinkerStationParts`, `TinkerBlockComp`), `src/main/java/slimeknights/tconstruct/tools/common/block/BlockToolTable.java` (`getGuiNumber`), `src/main/java/slimeknights/tconstruct/tools/common/network/TinkerStationTabPacket.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/StationScreen.java` (tab-row geometry: 4px inset from the panel's left edge, tabs drawn above the panel with the selected one re-drawn on top, block-icon tabs with the block's name as hover text) | `src/main/java/slimeknights/tconstruct/tools/common/client/GuiTinkerStation.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiTinkerTabs.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/PartBuilderScreen.java` (pattern-chest button sidebar: which patterns get a button, the four-column button grid, and hiding the chest's slots behind it) | `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiButtonsPartCrafter.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/GuiPartBuilder.java` (`drawSlot`/`isMouseOverSlot`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/PartBuilderMenu.java` (`partCrafter` conditions and `setPattern`'s exchange of the loaded pattern with the chest's) and `src/main/java/dev/gkissel/forgeweave/block/PartBuilderBlockEntity.java` (`isPartCrafter`) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerPartBuilder.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/grout.png` (cube_all block texture -- see `ForgeweaveBlocks#GROUT`, issue #129) | `resources/assets/tconstruct/textures/blocks/grout.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/seared_brick.png` | `resources/assets/tconstruct/textures/items/materials/seared_brick.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_stone.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_stone.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_cobblestone.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_cobble.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_paver.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_paver.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_bricks.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_brick.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_cracked_bricks.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_brick_cracked.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_fancy_bricks.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_brick_fancy.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_square_bricks.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_brick_square.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_triangle_bricks.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_brick_triangle.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_small_bricks.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_brick_small.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_road.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_road.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_tile.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_tile.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_creeper.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_creeper.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (grout recipe shape: clay ball + sand/red sand + gravel, shapeless, yields 2) | `resources/assets/tconstruct/recipes/smeltery/grout_simple.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (grout smelts into seared brick, 0.4 xp; seared bricks smelts into cracked seared bricks, 0.1 xp) | `src/main/java/slimeknights/tconstruct/smeltery/TinkerSmeltery.java` (`registerSmelting`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (four seared brick items, 2x2, craft one seared bricks block) | `resources/assets/tconstruct/recipes/smeltery/seared/bricks/bricks.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (the ten shapeless 1:1 block-variant conversions forming the stone->paver->bricks->fancy->square->triangle->creeper->small->tile->road->paver loop) | `resources/assets/tconstruct/recipes/smeltery/seared/bricks/{bricks_simple,paver_bricks_default,fancy_bricks,square_bricks,triangle_bricks,creeper_bricks,small_bricks,tile_bricks,road_bricks,paver_bricks}.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/ForgeweaveBlocks.java` (the 12-variant seared brick block family: stone, cobblestone, paver, bricks, cracked/fancy/square/triangle/small bricks, road, tile, creeper) | `src/main/java/slimeknights/tconstruct/smeltery/block/BlockSeared.java` (`SearedType`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/standard_core_front_active.png` | `resources/assets/tconstruct/textures/blocks/smeltery/smeltery_active.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/standard_core_front_inactive.png` | `resources/assets/tconstruct/textures/blocks/smeltery/smeltery_inactive.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/nether_core_front_active.png` (recoloured to a netherrack-red/crimson palette with a glowing ember rim around the aperture, issue #143; the Nether Core tier has no upstream equivalent, so its art is a hue/value shift of the Standard Core's) | `resources/assets/tconstruct/textures/blocks/smeltery/smeltery_active.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/nether_core_front_inactive.png` (recoloured, as above -- a dim ember rim and rust-toned brick even while idle) | `resources/assets/tconstruct/textures/blocks/smeltery/smeltery_inactive.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/nether_core_side.png` (issue #143: a rust-tinted recolour of this repo's own `seared_bricks.png` with crimson corner-inlay accents, so the Nether Core reads as its own tier from the side/top and not just the front) | `resources/assets/tconstruct/textures/blocks/smeltery/seared_brick.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_tank_side.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_tank_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_tank_top.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_tank_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_gauge_side.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_gauge_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_window_side.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_window_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_window_top.png` | `resources/assets/tconstruct/textures/blocks/smeltery/seared_window_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_drain_front.png` | `resources/assets/tconstruct/textures/blocks/smeltery/drain_front.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/seared_drain_back.png` | `resources/assets/tconstruct/textures/blocks/smeltery/drain_back.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryScan.java` (rectangular-interior detection: the wall walks, the 9x9 maximum, floor-then-layers order, corner-free wall checks and the at-least-one-tank requirement) | `src/main/java/slimeknights/tconstruct/smeltery/multiblock/{MultiblockDetection,MultiblockCuboid,MultiblockTinker,MultiblockSmeltery}.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryScan.java` (`Result`: a failure position plus a translatable reason, instead of 1.12's bare `null`) | `src/main/java/slimeknights/tconstruct/smeltery/block/entity/multiblock/MultiblockResult.java` | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryStructure.java` (the interior bounds a formed structure records) | `src/main/java/slimeknights/tconstruct/smeltery/multiblock/MultiblockDetection.java` (`MultiblockStructure`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryControllerBlockEntity.java` (formed/bounds state, its NBT shape, and the eight-ingots-per-interior-block tank capacity) | `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileMultiblock.java`, `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileSmeltery.java` (`CAPACITY_PER_BLOCK`, `updateStructureInfo`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryControllerBlock.java` (horizontal `facing` pointing out of the structure, the `active` property, and rescanning on placement) | `src/main/java/slimeknights/tconstruct/smeltery/block/BlockMultiblockController.java`, `src/main/java/slimeknights/tconstruct/smeltery/block/BlockSmelteryController.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/gui/smeltery.png` (the whole 256x256 sheet, copied unmodified: the 176x166 L-shaped panel, the 52x52 scale overlay drawn over the fluid column at (176, 76), and the slot tiles and melting heat bars issue #96 will use) | `resources/assets/tconstruct/textures/gui/smeltery.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryTank.java` (an ordered list of fluids sharing one capacity, the fill/drain rules over it, and `moveToBottom` making the clicked fluid the one a drain pours) | `src/main/java/slimeknights/tconstruct/library/smeltery/SmelteryTank.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/SmelteryMenu.java` (player-inventory origin at (8, 84), the three-column melt grid and its 22px cells, one item per interior block, and reading the displayed state off the block entity rather than syncing it through the menu) | `src/main/java/slimeknights/tconstruct/smeltery/inventory/ContainerSmeltery.java` (`calcColumns`), `src/main/java/slimeknights/tconstruct/smeltery/inventory/ContainerSmelterySideInventory.java`, `src/main/java/slimeknights/tconstruct/tools/common/client/module/GuiSideInventory.java` (slot placement and the hide-when-scrolled rule) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/SmelteryScreen.java` (fluid column and its (8, 16, 52x52) box, the scale overlay, the (71, 16, 12x52) fuel gauge, the 3px-minimum band heights and shave-the-tallest fitting, the bottom-up hit test, and the block/ingot/nugget-then-buckets tooltip breakdown) | `src/main/java/slimeknights/tconstruct/smeltery/client/GuiSmeltery.java`, `src/main/java/slimeknights/tconstruct/smeltery/client/GuiHeatingStructureFuelTank.java`, `src/main/java/slimeknights/tconstruct/library/client/GuiUtil.java`, `src/main/java/slimeknights/tconstruct/smeltery/client/SmelteryTankRenderer.java` (`calcLiquidHeights`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SearedTankBlock.java` and `SearedTankBlockEntity.java` (four-bucket capacity, bucket interaction, comparator scaling, keeping contents through a break) | `src/main/java/slimeknights/tconstruct/smeltery/block/BlockTank.java`, `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileTank.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SearedDrainBlock.java` and `SearedDrainBlockEntity.java` (a wall block re-exposing the smeltery's own tank to its neighbours) | `src/main/java/slimeknights/tconstruct/smeltery/block/BlockSmelteryIO.java`, `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileDrain.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveBlockStateProvider.java` (the core's orientable shape with seared brick on every face but the front, front swapping on `active`, and (issue #143) the Nether Core's own side/top texture instead of the shared seared brick; one cube per tank type with its own side/top textures) | `resources/assets/tconstruct/blockstates/smeltery_controller.json`, `resources/assets/tconstruct/blockstates/seared_tank.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (the smeltery core's brick ring, the drain's two brick columns, and the tank/gauge/window brick-and-glass shapes) | `resources/assets/tconstruct/recipes/smeltery/{smeltery_controller,smeltery_drain}.json`, `resources/assets/tconstruct/recipes/smeltery/seared/{tank,gauge,window}.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`REINFORCED` behavior: 20% per level chance to negate durability damage, level 5 reads unbreakable) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModReinforced.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`MENDING_MOSS` behavior: durability-per-XP and storage-cap formulas, XP banking on pickup, periodic self-repair) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModMendingMoss.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`onRightClickBookshelf`: moss + 10 XP levels at a bookshelf yields mending moss) | `src/main/java/slimeknights/tconstruct/tools/ToolEvents.java` (`onInteract`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`SILKY` behavior: grants Silk Touch, flat -3 mining speed and attack damage floored at 1) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModSilktouch.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`SOULBOUND` behavior: pull the item out of death drops, restore it on respawn) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModSoulbound.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`EXTRA_SLOT` behavior: each application's own `bonusSlots` returns `level + 1`, netting +1 free slot per level) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModCreative.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/reinforced.json` (reinforced plate, 1 per level, 5 levels) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/mending_moss.json` (mending moss, 1 per level, 3 levels) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/silky.json` (silky jewel, 1 level) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/soulbound.json` (nether star, 1 level) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (moss recipe shape: 9x mossy cobblestone) | `resources/assets/tconstruct/recipes/tools/materials/ball_of_moss.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (reinforced plate recipe shape: obsidian ring around a gold ingot) | `resources/assets/tconstruct/recipes/tools/materials/reinforcement.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (silky cloth recipe shape: string ring around a gold ingot) | `resources/assets/tconstruct/recipes/tools/materials/silky_cloth.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (silky jewel recipe shape: four silky cloth in a plus around an emerald) | `resources/assets/tconstruct/recipes/tools/materials/silky_jewel.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/moss.png` | `resources/assets/tconstruct/textures/items/materials/moss.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/mending_moss.png` | `resources/assets/tconstruct/textures/items/materials/mending_moss.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/reinforced_plate.png` | `resources/assets/tconstruct/textures/items/materials/reinforcement.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/silky_cloth.png` | `resources/assets/tconstruct/textures/items/materials/silky_cloth.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/silky_jewel.png` | `resources/assets/tconstruct/textures/items/materials/silky_jewel.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/extra_modifier.png` (upstream's own `creative_modifier` blockstate variant points at this file, not a `materials`-sheet icon) | `resources/assets/tconstruct/textures/items/skull_char_gold.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`LUCK` behavior: granting Fortune to every tool and Looting to weapon tools) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModLuck.java`, `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ModifierRecipe.java` (`cost_per_level`/`levelsReached`: datapack-expressible non-uniform per-level cost, and the triangular `getMaxForLevel`/`getLevel` walk luck's `[60, 120, 180]` reproduces) | `src/main/java/slimeknights/tconstruct/library/modifiers/ModifierAspect.java` (`LuckAspect` in `tools/modifiers/ModLuck.java`: `getMaxForLevel`, `getLevel`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`SHARPNESS` behavior: the attack-damage diminishing-returns steps, the flat per-level bonus, 72 quartz per level) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModSharpness.java`, `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`DIAMOND` behavior: the flat 500 durability bonus and its tool-tier bump cap; the extra `+1` attack damage / `+0.5` mining speed upstream's `ModDiamond` also grants are not ported -- issue #106's own scope names only durability and the tier bump, flagged for maintainer review in the PR) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModDiamond.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`EMERALD` behavior: the 50%-of-base durability bonus and its tool-tier bump cap, one rung below diamond's) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModEmerald.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (the `incorrect_for_*_tool` tier ladder diamond/emerald bump along, vanilla-tag equivalent of upstream's numeric `HarvestLevels`) | `src/main/java/slimeknights/tconstruct/library/utils/HarvestLevels.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ModifierApplication.java` (the one-shot tool-tier bump, applied only the moment a modifier is first added so a flat "+1, capped" rule never compounds) | `src/main/java/slimeknights/tconstruct/library/modifiers/ModifierAspect.java` (`SingleAspect`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ModifierApplication.java` (granting Fortune/Looting onto the tool's stored enchantments, gated on the tool being a weapon type for Looting) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModLuck.java` (`applyEnchantments`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/luck.json` (lapis lazuli; `cost_per_level: [60, 120, 180]`, `max_level: 360` -- upstream's 60/180/360 cumulative thresholds exactly) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/sharpness.json` (quartz, 72 per level, 5 levels) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/diamond.json` (one diamond, one-shot) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/modifier_recipe/emerald.json` (one emerald, one-shot) | `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/Embossing.java` (embossing whole: generated per-material identifier, one-embossment-per-tool scan, donor traits scoped by the donor part's kind and appended to the tool's trait list with no stat change, one of each reagent consumed) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModExtraTrait.java` (`generateIdentifier`, `ExtraTraitAspect#canApply`, `applyEffect`, `addCombination`, `EMBOSSMENT_ITEMS`), `src/main/java/slimeknights/tconstruct/tools/TinkerModifiers.java` (`registerExtraTraitModifiers`, `PartMaterialType#getApplicableTraitsForMaterial`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ForgeweaveModifiers.java` (`EMBOSSMENT` behavior and the free-slot exemption: an embossment occupies no modifier slot) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModExtraTrait.java` (its aspect list carries no `FreeModifierAspect`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/modifier/ModifierApplication.java` (`name`: an embossment reads `Embossment (<material>)` from one shared key plus the material's own name) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModExtraTrait.java` (`getLocalizedName`, `getLocalizedDesc`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/embossing_recipe/embossment.json` (one of each reagent alongside the donor part; the three slime crystals are **substituted** with slime/magma/gold blocks -- maintainer decision on issue #154, revert tracked in docs/SCOPE.md's deferred backlog) | `src/main/java/slimeknights/tconstruct/tools/modifiers/ModExtraTrait.java` (`getEmbossmentItems`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/ToolStationTabs.java` (the repair tab's 4th and 5th slot positions) and `ToolStationScreen.java` (their `ICON_Ingot`/`ICON_Gem` glyphs) | `src/main/java/slimeknights/tconstruct/tools/common/client/GuiButtonRepair.java` (static initializer), `src/main/java/slimeknights/tconstruct/tools/common/client/GuiToolStation.java` (`drawRepairSlotIcon`), `src/main/java/slimeknights/tconstruct/library/client/Icons.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/ToolStationMenu.java` (per-tab active input-slot count: slots the selected tab doesn't use are hidden and refuse everything) | `src/main/java/slimeknights/tconstruct/tools/common/inventory/ContainerToolStation.java` (`activeSlots`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/casting/CastingRecipe.java` (cast/fluid/amount/result/time/`consumes_cast`/`result_in_input` (upstream's `switchOutputs`) recipe shape, the `24 + (temperature - 300) * amount / 1600` cooldown formula, and the table/basin split into two recipe sets) | `src/main/java/slimeknights/tconstruct/library/smeltery/CastingRecipe.java`, `src/main/java/slimeknights/tconstruct/library/smeltery/ICastingRecipe.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/CastingBlockEntity.java` (two-slot input/output model, the interact rules, the recipe-sized tank whose fill is refused unless a recipe matches, and the finish sequence incl. `consumesCast`/`switchOutputs` and the comparator output) | `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileCasting.java`, `.../TileCastingTable.java`, `.../TileCastingBasin.java`, `src/main/java/slimeknights/tconstruct/library/fluid/FluidHandlerCasting.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/CastingBlock.java` (the table and basin collision/outline boxes, the right-click-to-swap-item behaviour, the comparator override) | `src/main/java/slimeknights/tconstruct/smeltery/block/BlockCasting.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/FaucetBlockEntity.java` (`LIQUID_TRANSFER = 6`, `TRANSACTION_AMOUNT = Material.VALUE_Ingot`, the simulate-drain/simulate-fill/drain-what-was-accepted transaction, the buffered trickle, the second-click stop, and the rising-edge redstone delay) | `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileFaucet.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/FaucetBlock.java` (`FACING` = input side, never down; placement facing the clicked block; the per-facing outline boxes) | `src/main/java/slimeknights/tconstruct/smeltery/block/BlockFaucet.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/casting_recipe/*.json` (52 rows: cast creation at `Material.VALUE_Ingot * 2` of molten gold; part casting at each part's `ToolPart` cost; ingot/nugget/block casting at `VALUE_Ingot`/`VALUE_Nugget`/`VALUE_Block`) | `src/main/java/slimeknights/tconstruct/smeltery/TinkerSmeltery.java`, `src/main/java/slimeknights/tconstruct/library/materials/Material.java`, `src/main/java/slimeknights/tconstruct/tools/TinkerTools.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/casting_table.json` | `resources/assets/tconstruct/models/block/casting_table.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/casting_basin.json` | `resources/assets/tconstruct/models/block/casting_basin.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/faucet.json` | `resources/assets/tconstruct/models/block/faucet.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/models/block/faucet_top.json` | `resources/assets/tconstruct/models/block/faucet_top.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast.png` (the blank cast, layer0 of the five part-cast item models) | `resources/assets/tconstruct/textures/items/cast.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast_ingot.png` | `resources/assets/tconstruct/textures/items/cast_ingot.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast_nugget.png` | `resources/assets/tconstruct/textures/items/cast_nugget.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast_pickaxe_head.png` (issue #140 art fix: `cast.png` with a hole punched through it in the part's silhouette and a darkened bevel around the hole, pre-composited by `scripts/generate_cast_textures.py`, replacing the old flat two-layer gold-square-plus-grey-part-sprite model) | `resources/assets/tconstruct/textures/items/cast.png`, `resources/assets/tconstruct/textures/items/pickaxe/head.png`, `src/main/java/slimeknights/tconstruct/library/client/texture/CastTexture.java` (compositing technique the script approximates offline) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast_shovel_head.png` (same issue #140 treatment) | `resources/assets/tconstruct/textures/items/cast.png`, `resources/assets/tconstruct/textures/items/shovel/head.png`, `src/main/java/slimeknights/tconstruct/library/client/texture/CastTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast_axe_head.png` (same issue #140 treatment) | `resources/assets/tconstruct/textures/items/cast.png`, `resources/assets/tconstruct/textures/items/hatchet/head.png`, `src/main/java/slimeknights/tconstruct/library/client/texture/CastTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast_tool_binding.png` (same issue #140 treatment) | `resources/assets/tconstruct/textures/items/cast.png`, `resources/assets/tconstruct/textures/items/parts/binding.png`, `src/main/java/slimeknights/tconstruct/library/client/texture/CastTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cast_tool_handle.png` (same issue #140 treatment) | `resources/assets/tconstruct/textures/items/cast.png`, `resources/assets/tconstruct/textures/items/parts/tool_rod.png`, `src/main/java/slimeknights/tconstruct/library/client/texture/CastTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/casting_table_top.png` | `resources/assets/tconstruct/textures/blocks/smeltery/castingtable_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/casting_table_side.png` | `resources/assets/tconstruct/textures/blocks/smeltery/castingtable_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/casting_table_bottom.png` | `resources/assets/tconstruct/textures/blocks/smeltery/castingtable_bottom.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/casting_basin_top.png` | `resources/assets/tconstruct/textures/blocks/smeltery/blockcast_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/casting_basin_side.png` | `resources/assets/tconstruct/textures/blocks/smeltery/blockcast_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/casting_basin_bottom.png` | `resources/assets/tconstruct/textures/blocks/smeltery/blockcast_bottom.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/faucet.png` | `resources/assets/tconstruct/textures/blocks/smeltery/faucet.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (the casting table's, casting basin's and faucet's seared-brick crafting shapes) | `resources/assets/tconstruct/recipes/smeltery/{casting_table,casting_basin,faucet}.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveBlockStateProvider.java` (the faucet's per-facing model choice and y-rotations) | `resources/assets/tconstruct/blockstates/faucet.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/recipe/MeltingRecipe.java` (the temperature model: `calcTemperature`'s log-base-9-of-2 curve off the output fluid's own temperature, `getUsableTemperature`, `TIME_FACTOR`, and the nugget/ingot/block fluid values) | `src/main/java/slimeknights/tconstruct/library/smeltery/MeltingRecipe.java`, `src/main/java/slimeknights/tconstruct/library/materials/Material.java` (`VALUE_Nugget`, `VALUE_Ingot`, `VALUE_Block`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/melting_recipe/*.json` except `vanilla_*.json` (which forms of a metal melt and for how much -- ore, raw, raw block, ingot, nugget, storage block -- keyed off 1.21's `c:` convention tags where 1.12 keyed off the ore dictionary; the two `vanilla_*` per-item ore overrides carry no row, their amounts come from 1.21.1's own block loot tables, not from upstream) | `src/main/java/slimeknights/tconstruct/smeltery/TinkerSmeltery.java` (`registerOredictMeltingCasting`, `addKnownOreFluid`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryControllerBlockEntity.java` (the melt loop: one slot per interior block, per-slot heat accumulating at a hundredth of the smeltery's working temperature every fourth tick, and parking rather than melting into a full tank) | `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileHeatingStructure.java` (`heatItems`, `heatSlot`, `TIME_FACTOR`), `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileSmeltery.java` (`updateHeatRequired`, `onItemFinishedHeating`, `getUpdatedInventorySize`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/recipe/SmelteryFuel.java` (a fuel's shape: fluid, mB drained per burn cycle, cycle duration in melt ticks, and a temperature defaulting to the fluid's own) | `src/main/java/slimeknights/tconstruct/library/TinkerRegistry.java` (`registerSmelteryFuel`, `isSmelteryFuel`, `consumeSmelteryFuel`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/smeltery_fuel/lava.json` (50 mB per burn cycle, a cycle lasting 100 melt ticks) | `src/main/java/slimeknights/tconstruct/smeltery/TinkerSmeltery.java` (`registerSmelteryFuel`: `registerSmelteryFuel(new FluidStack(FluidRegistry.LAVA, 50), 100)`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryControllerBlockEntity.java` (fuel consumption: searching the walls for a tank holding a registered fuel, draining one burn's worth only when the previous burn has run out, and only counting a burn tick down on a melt tick that actually heated something) | `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileHeatingStructureFuelTank.java` (`consumeFuel`, `searchForFuel`, `hasTankWithFuel`), `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileHeatingStructure.java` (`fuel`/`temperature` fields, `addFuel`, `hasFuel`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/recipe/AlloyRecipe.java` (an alloy as a list of input fluid amounts plus one result, the amounts read as a ratio rather than a batch, `matches` returning the largest whole multiple the tank allows and 0 when any input is missing or short, and the at-least-two-inputs/result-not-an-input validation) | `src/main/java/slimeknights/tconstruct/library/smeltery/AlloyRecipe.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/SmelteryControllerBlockEntity.java` (the alloying pass: sweeping every alloy recipe against the tank, draining each input at the matched multiple and filling the result back into the same tank; run on tank change instead of upstream's every-fourth-tick call, and without upstream's `ALLOYING_PER_TICK` rate cap, which only means something to a smeltery that ticks) | `src/main/java/slimeknights/tconstruct/smeltery/tileentity/TileSmeltery.java` (`alloyAlloys`, `ALLOYING_PER_TICK`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/alloy_recipe/manyullyn.json` (2 mB cobalt + 2 mB ardite = 2 mB manyullyn, i.e. one ingot of each into one ingot of manyullyn, in upstream's own minimal ratio units) | `src/main/java/slimeknights/tconstruct/smeltery/TinkerSmeltery.java` (`registerAlloys`: `registerAlloy(new FluidStack(manyullyn, 2), new FluidStack(cobalt, 2), new FluidStack(ardite, 2))`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/cobalt_ore.png` (upstream's overlay sprite composited onto vanilla's own `netherrack.png`, since Forgeweave has no `cube_overlay_all`-style runtime compositing loader and 1.21's own nether ore blocks -- e.g. `nether_gold_ore.png` -- already ship as one baked texture) | `resources/assets/tconstruct/textures/blocks/ore_cobalt.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/block/ardite_ore.png` (same netherrack-composite treatment) | `resources/assets/tconstruct/textures/blocks/ore_ardite.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/block/ForgeweaveBlocks.java` (`COBALT_ORE`/`ARDITE_ORE` properties: 10.0 hardness, `Material.ROCK`'s implicit `SoundType.STONE`) | `src/main/java/slimeknights/tconstruct/shared/block/BlockOre.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/worldgen/configured_feature/cobalt_ore.json`, `.../placed_feature/cobalt_ore.json` (vein size 5, netherrack target) | `src/main/java/slimeknights/tconstruct/shared/worldgen/NetherOreGenerator.java` (`cobaltGen = new WorldGenMinable(..., 5, BlockMatcher.forBlock(Blocks.NETHERRACK))`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/worldgen/configured_feature/ardite_ore.json`, `.../placed_feature/ardite_ore.json` (same vein size/target) | `src/main/java/slimeknights/tconstruct/shared/worldgen/NetherOreGenerator.java` (`arditeGen`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/data/forgeweave/forgeweave/worldgen/placed_feature/{cobalt,ardite}_ore.json` (count 20 per chunk) | `src/main/java/slimeknights/tconstruct/common/config/Config.java` (`cobaltRate = 20`, `arditeRate = 20`, both commented "max. * per chunk") | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/client/SearedTankBlockEntityRenderer.java` (technique only, no code copied: an inset quad column sized to fill fraction, textured with the fluid's still sprite and tinted, drawn per face) | `src/main/java/slimeknights/tconstruct/smeltery/client/TankRenderer.java`, `src/main/java/slimeknights/tconstruct/library/client/RenderUtil.java` (`renderFluidCuboid`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/combat/CombatSeams.java`, `.../combat/CombatSeam.java` (per-hit hook sequence and the point each hook runs at: damage adjustment before armor, after-hit once the blow lands, kill last) | `src/main/java/slimeknights/tconstruct/library/utils/ToolHelper.java` (`attackEntity`), `src/main/java/slimeknights/tconstruct/library/traits/ITrait.java` (`damage`, `afterHit`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`BROADSWORD`: attack speed 1.6, damage potential 1.0, +1.0 flat attack, durability ×1.1, part composition) | `src/main/java/slimeknights/tconstruct/tools/melee/item/BroadSword.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`LONGSWORD`: attack speed 1.4, damage potential 1.1, +0.5 flat attack, durability ×1.05, part composition) | `src/main/java/slimeknights/tconstruct/tools/melee/item/LongSword.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`RAPIER`: attack speed 3.0, damage potential 0.55, durability ×0.8, part composition) | `src/main/java/slimeknights/tconstruct/tools/melee/item/Rapier.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`BATTLESIGN`: attack speed 1.2, damage potential 0.86, part composition) | `src/main/java/slimeknights/tconstruct/tools/melee/item/BattleSign.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`FRYING_PAN`: attack speed 1.4, damage potential 1.0, part composition) | `src/main/java/slimeknights/tconstruct/tools/melee/item/FryPan.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`MATTOCK`: attack speed 0.9, damage potential 0.90, +3.0 flat attack, mining speed ×0.95, axe/shovel head average, part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/Mattock.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`KAMA`: attack speed 1.3, damage potential 1.0, part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/Kama.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`BATTLEAXE`: part composition only -- tough tool rod + 2× broad axe head + tough binding; damage/durability/speed/mining REBALANCED from scratch, maintainer decision 2026-08-12, issue #153 -- upstream's own unshipped numbers are not adopted) | `src/main/java/slimeknights/tconstruct/tools/melee/item/BattleAxe.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`CLEAVER`: attack speed 0.7, damage potential 1.2, ×1.3 pre-multiplier, +3.0 flat attack, durability ×2.0, head/plate average, part composition) | `src/main/java/slimeknights/tconstruct/tools/melee/item/Cleaver.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`HAMMER`: attack speed 0.8, damage potential 1.2, durability ×2.5, mining speed ×0.4, hammer head weighted double over two large plates, part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/Hammer.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`EXCAVATOR`: attack speed 0.7, damage potential 1.25, durability ×1.75, mining speed ×0.28, head/plate average, part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/Excavator.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`LUMBERAXE`: attack speed 0.8, damage potential 1.2, +2.0 flat attack, durability ×2.0, mining speed ×0.35, head/plate average, part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/LumberAxe.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`SCYTHE`: attack speed 0.9, damage potential 0.75, durability ×2.2, two tough handles averaged, part composition) | `src/main/java/slimeknights/tconstruct/tools/tools/Scythe.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`VEIN_HAMMER`: no 1.12 counterpart -- part weights (hammer head 0.75 / large plate 0.25), attack speed 0.85, damage potential 1.25, +3.0 flat attack, durability ×5.0, mining speed ×0.3; feature-scope deviation authorized by maintainer 2026-08-12, docs/SCOPE.md M3 sources table, numbers confirmed unchanged in issue #153's decision comment) | `src/main/java/slimeknights/tconstruct/tools/data/ToolDefinitionDataProvider.java` (`VEIN_HAMMER` definition) | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/java/dev/gkissel/forgeweave/tool/ToolConstants.java` (`DAGGER`: no 1.12 counterpart -- part composition (head + handle, no extra part) ported from the 1.20 branch; base damage 3.0 and attack speed 2.0 are issue #153's decision comment, which does not carry over 1.20's own attack/mining/durability multipliers) | `src/main/java/slimeknights/tconstruct/tools/data/ToolDefinitionDataProvider.java` (`DAGGER` definition) | `de26560d26c15edf93e6078520202d1c0518394e` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/sword_blade.png` | `resources/assets/tconstruct/textures/items/parts/sword_blade.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/wide_guard.png` | `resources/assets/tconstruct/textures/items/parts/wide_guard.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/hand_guard.png` (upstream has no generic guard part sprite for this shape -- the longsword's own tool-layer art is the only 1.12 source) | `resources/assets/tconstruct/textures/items/longsword/guard.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/cross_guard.png` (same reasoning as `hand_guard.png` above -- the rapier's own tool-layer art) | `resources/assets/tconstruct/textures/items/rapier/guard.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/sign_plate.png` (battlesign's head shape, upstream's `signHead` part) | `resources/assets/tconstruct/textures/items/battlesign/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pan.png` (frypan's head shape, upstream's `panHead` part) | `resources/assets/tconstruct/textures/items/frypan/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/knife_blade.png` | `resources/assets/tconstruct/textures/items/parts/knife_blade.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/large_sword_blade.png` | `resources/assets/tconstruct/textures/items/parts/large_sword_blade.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/tough_tool_rod.png` | `resources/assets/tconstruct/textures/items/parts/tough_tool_rod.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/tough_binding.png` | `resources/assets/tconstruct/textures/items/parts/tough_binding.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/large_plate.png` | `resources/assets/tconstruct/textures/items/parts/large_plate.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/hammer_head.png` | `resources/assets/tconstruct/textures/items/hammer/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/excavator_head.png` | `resources/assets/tconstruct/textures/items/excavator/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/scythe_head.png` | `resources/assets/tconstruct/textures/items/scythe/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/kama_head.png` | `resources/assets/tconstruct/textures/items/kama/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/broad_axe_head.png` (the shipped tool that actually uses upstream's `broadAxeHead` part is the lumberaxe -- battleaxe is unshipped, docs/SCOPE.md M3 content manifest) | `resources/assets/tconstruct/textures/items/lumberaxe/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_sword_blade.png` (composite, same algorithm as the five M1 patterns above) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_wide_guard.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_hand_guard.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_cross_guard.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_sign_plate.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_pan.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_knife_blade.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_large_sword_blade.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_tough_tool_rod.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_tough_binding.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_large_plate.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_hammer_head.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_excavator_head.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_scythe_head.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_kama_head.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_broad_axe_head.png` (composite, same algorithm) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/derived/item/pattern_vein_hammer_head.png` (composite, same algorithm -- run over `vein_hammer_head.png`, which is itself original art with no NOTICE.md row of its own; this composite still runs the derived algorithm over the derived `pattern.png` base, so it gets a row like every pattern here) | `src/main/java/slimeknights/tconstruct/library/client/texture/PatternTexture.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/PartBuilderRecipes.java` (M3 part costs: `MEDIUM_PART_COST`/`LARGE_HEAD_COST` and their per-part assignment) | `src/main/java/slimeknights/tconstruct/tools/TinkerTools.java` (`registerToolParts`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/item/ForgeweaveItems.java` (the M3 part list and each part's `PartItem.Kind` role) | `src/main/java/slimeknights/tconstruct/tools/TinkerTools.java` (`registerToolParts`), `src/main/java/slimeknights/tconstruct/tools/melee/item/BroadSword.java`, `LongSword.java`, `Rapier.java`, `BattleSign.java`, `FryPan.java`, `Cleaver.java`, `src/main/java/slimeknights/tconstruct/tools/tools/Hammer.java`, `Excavator.java`, `LumberAxe.java`, `Scythe.java`, `Kama.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/StencilTableMenu.java` (M3 additions to the fixed `PATTERNS` candidate list) | `src/main/java/slimeknights/tconstruct/tools/TinkerTools.java` (`registerToolParts`) | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |

The station tab row uses **vanilla's** creative-inventory tab sprites (`minecraft:container/creative_inventory/tab_top_{selected,unselected}_N`) and so has no derived-asset row. That is upstream's own choice, not a substitution: 1.12's `GuiTinkerTabs` hands its `GuiElement`s to Mantle's `GuiWidgetTabs`, which binds
`textures/gui/container/creative_inventory/tabs.png` and samples the unselected tab at `(0, 2, 28, 28)` and the selected one at `(*, 32, 28, 32)` -- i.e. the station tabs already *were* vanilla's creative tabs. Modern Minecraft split that sheet into the 26x32 per-column sprites used here.

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

`Trait.java` carries only the hooks the shipped traits use rather than upstream's full `ITrait`
surface (five as of M1, widened for M2's metal traits per issue #102 -- see its class javadoc), so
it is not a derived file and carries no row. Nor does `TraitStacks.java`: the tool-data-component
storage `ForgeweaveTraits#MOMENTUM`/`#INSATIABLE` use in place of upstream's player-scoped potion
effects (see their javadocs) is original engineering, not ported semantics.

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

Grout and the seared brick block family (docs/SCOPE.md M2 issue #93), ported from upstream 1.12's
smeltery pulse: one deliberate deviation.

Grout is its own single-state `Block` (`ForgeweaveBlocks#GROUT`, issue #129), not a state of a
multi-purpose `BlockSoil` shared with graveyard soil, consecrated soil, and slimy mud -- none of
which are in Forgeweave's scope (no world-content milestone yet; see docs/SCOPE.md's open
questions). Splitting grout out into its own block avoids either porting that whole unrelated block
family early or leaving three dead enum states on it; the furnace-smelt-into-seared-brick and
crafting-table behaviors upstream gives grout are unaffected. This is parity, not a deviation. (An
earlier PR, #115, shipped grout as a plain item instead of a block at all -- that deviation was
overruled by maintainer playtest feedback, issue #129, and is not carried forward.)

The one remaining deviation:

- **The 12 seared block variants are 12 separate `Block`s**, not one block with a 12-value
  `PropertyEnum` blockstate (`BlockSeared.SearedType`, upstream's 1.12-era pattern). Modern
  Minecraft's per-registry-name recipes/loot tables/tags favor one block per variant, matching how
  every other Forgeweave block is already registered (`ForgeweaveBlocks`); no upstream behavior is
  lost by the split; each variant's crafting/smelting recipe just names a different block instead of
  a different blockstate value.

Reinforced, mending moss, silky, soulbound and extra-slot (docs/SCOPE.md M2 issue #107), ported from
upstream 1.12's `tools/modifiers/` (NOTICE.md rows above), with four deliberate deviations flagged for
maintainer review:

- **The extra-slot reagent has a real survival crafting recipe.** Upstream's `creative_modifier`
  reagent (`ModCreative#isHidden`) has no crafting recipe at all -- it is admin/creative-tool only, and
  its slot bonus is uncapped. Forgeweave gives it a shapeless gold-block-plus-diamond recipe and a
  finite cap of 5 levels so docs/SCOPE.md acceptance test 5 ("an extra-slot item raises the cap") is
  reachable in survival at all.
- **Silky's Fortune/Looting/`luck`-modifier exclusion is not ported.** Forgeweave ships no
  Fortune-granting modifier in this PR, so `ModSilktouch#canApplyTogether`'s refusal has nothing to
  conflict with yet; left for whichever future issue adds one.
- **Mending moss heals from any inventory slot, not hotbar/offhand only.** Upstream's
  `ModMendingMoss#onUpdate` restricts healing to the hotbar and off hand; NeoForge's
  `Inventory#tick`/`Item#inventoryTick` seam doesn't expose the global slot index in a form
  `ForgeweaveModifiers#inventoryTick` can cheaply check against, so this is a minor QoL buff over
  upstream rather than a restriction, and the 150-tick delay between heals is an equal-average per-tick
  chance rather than a stored timestamp, matching the roll-instead-of-timer idiom
  `ForgeweaveTraits#ECOLOGICAL` already uses (ADR-0004 keeps modifier-adjacent state to a minimum).
- **Mending moss's bookshelf check is the concrete block, not a generic enchant-power query.**
  Upstream's `ToolEvents#onInteract` accepts any block whose `getEnchantPowerBonus >= 1.0f`; 1.21 has no
  equivalent query, and the vanilla bookshelf is the only block that ever qualified there, so
  `state.is(Blocks.BOOKSHELF)` is behaviorally identical for every vanilla or modded world in practice.

Seared cobblestone has no vanilla-table recipe in either direction, matching upstream exactly:
`BlockSeared.SearedType.COBBLE` never appears in any `recipes/smeltery/seared/**` file, so the block
here is likewise craftable from nothing yet -- both are entirely dependent on the smeltery/casting
system (issue #95). The same is true of the chain's own entry point, seared stone: nothing in this
issue can produce the first one either. These are not gaps introduced by this port; they are exactly
upstream's shape, ahead of the milestone that fills them in.

The smeltery multiblock (docs/SCOPE.md M2 issue #95) ports upstream 1.12's structure semantics
exactly -- rectangular 1x1 to 9x9 interior, seared floor, no frame, no ceiling, walls scanned upward
until one fails, at least one tank -- with four deliberate deviations:

- **The core has no ticker.** Upstream's `TileSmeltery.update` polls `checkMultiblockStructure`
  once a second while unformed and re-runs it every 15 seconds while formed, plus a one-block-per
  -second interior sweep. SCOPE.md's M2 performance budget requires an idle smeltery to cost
  approximately zero ticks, so every scan here is driven by an event on the core (placement,
  neighbour change, player use) or by revalidation the first time something reads the structure in a
  given second. See `SmelteryControllerBlockEntity`'s javadoc.
- **No servant block entities.** Upstream gives every structure block a `MultiServantLogic` tile
  entity pointing back at its master, which is how a distant wall break notifies the controller and
  how a drain finds its smeltery. Issue #93 already shipped the seared blocks as plain blocks, so
  Forgeweave keeps only the interior bounds, and the core hands each drain its position when a scan
  succeeds.
- **Failures carry a reason.** 1.12 returns `null` for every failure. The reason model is taken from
  the 1.20 clone's `MultiblockResult` (row above), which is what issue #95's "the controller reports
  why an invalid structure fails to form" needs.
- **Wall walks run one block further than upstream's.** `MultiblockCuboid` walks exactly `MAX_SIZE`
  blocks looking for a wall, which lets a 10-wide interior seeded from its own edge measure as a
  passing 9. Forgeweave walks `MAX_SIZE + 1` so the oversized case is always reported.

The Nether Core tier itself is not upstream: 1.12 has one smeltery controller. Its 2x yield, its
netherite-ingot recipe and its recoloured front texture are SCOPE.md's and Forgeweave's own.

### Casting (issue #100) deviations from upstream 1.12

- **Recipes are data, not code.** Upstream registers casting recipes from Java in
  `TinkerSmeltery#registerMeltingCasting`; Forgeweave loads the same rows from a datapack registry.
  Every constant is upstream's; only where they live changed.
- **One item per cast.** Upstream ships a single `cast` item whose NBT names the part and whose
  texture is composited at load time by `CustomTextureCreator`. Forgeweave registers seven cast
  items, which is the same split issue #93 made for the seared brick variants and lets a vanilla
  `Ingredient` match a cast. The blank/ingot/nugget casts use upstream's own sprites unmodified; the
  five part casts (issue #140 art fix) are plain single-layer models pointed at a pre-composited PNG
  -- the blank cast with a hole punched through it in the part's silhouette and a darkened bevel
  around the hole -- approximating offline what upstream's `CustomTextureCreator`/`CastTexture` build
  at texture-stitch time (`scripts/generate_cast_textures.py`, NOTICE.md rows above). The original
  two-layer model (blank cast + the part's own grey/white sprite laid flat on top, uncomposited)
  rendered as a solid yellow square with a white silhouette floating on it instead of a mold cavity.
- **No ticking block entities.** Upstream ticks every casting block and every faucet in the world
  forever. Forgeweave runs both off vanilla scheduled block ticks, which SCOPE.md's "block entities
  tick only while doing work" budget requires. Timings are identical.
- **Nothing is rendered on the table yet.** Upstream draws the held item and the pooling fluid on
  the casting block; that is block-entity rendering and follows with the smeltery's own fluid
  rendering in issue #101, the same call already made for the seared gauge and window.
- **No clay or sand casts** (upstream's clay casts are a config flag, off by default; sand casts are
  a SCOPE.md M2 non-goal) and **no seared-stone casting**: Forgeweave has no molten seared stone
  fluid, since SCOPE.md M2's fluid manifest is the nine molten metals.
- **The drain is not wrapped extract-only.** Upstream wraps its drain's handler only for the
  side-agnostic (`facing == null`) lookup; a faucet asks with a side and gets the raw tank there
  too, so Forgeweave's drain matches upstream for every path the faucet uses.

### Metal materials (issue #103) deviations from upstream 1.12

Iron, copper, cobalt, ardite and manyullyn are 1:1 ports of `TinkerMaterials`' head/handle/extra
stats and trait wiring (table rows above). Two deliberate deviations, both forced by 1.21 having
fewer tool-tier tags than upstream's five-rung `HarvestLevels` ladder:

- **`incorrect_for_tool` collapses upstream's top two rungs into one.** Upstream's
  `HarvestLevels` has five levels (`STONE`, `IRON`, `DIAMOND`, `OBSIDIAN`, `COBALT`); 1.21 ships
  vanilla tags for only four tiers (`ForgeweaveModifiers#TIER_TAGS`: stone/iron/diamond/netherite).
  Iron's `DIAMOND` level and copper's `IRON` level map straight across
  (`minecraft:incorrect_for_diamond_tool` / `minecraft:incorrect_for_iron_tool`, matching vanilla's
  own iron/stone-tool ceilings exactly). Cobalt, ardite and manyullyn's `COBALT` level has no vanilla
  tag at all -- they, and netherite alongside them, all sit on the ladder's top rung
  (`minecraft:incorrect_for_netherite_tool`), which is as far as `ForgeweaveModifiers`' four-tag
  ladder goes.
- **`crafting_items` lists ingot and block, never nugget.** `PartBuilderRecipes`' shard-unit system
  (NOTICE.md row above) is denominated in units of upstream's `VALUE_Shard = 72`; an ingot is exactly
  2 shard-units (`VALUE_Ingot = 144`) and a block 18, but a nugget's `VALUE_Nugget = 16` is not a
  whole number of shard-units and so cannot be listed as one of `CraftingItem`'s integer values.
  Nuggets remain usable at the Tool Station only as repair reagent input via `c:nuggets/*` melting
  (issue #96), not as Part Builder crafting input -- the same ceiling M1's shard-unit system already
  has for anything smaller than 72.

Rose gold and netherite have no 1.12 counterpart at all (issue #92 already recorded this for their
fluids); their stats, traits, mechanisms and item forms are all maintainer decisions recorded here
rather than upstream ports, per the maintainer decision comment on issue #103 (2026-08-10):

- **Rose gold's stats are invented** (`rose_gold.json`): low durability (90), high mining speed
  (10.0) and low attack damage (2.0), a soft handle (0.65x modifier, -40 flat) -- "fast and
  fragile" per the issue body, roughly gold-tool-shaped rather than matched to any specific upstream
  number.
- **Rose gold's `quick` trait is a new id**, not an upstream port: `ForgeweaveTraits#QUICK` follows
  `LIGHTWEIGHT`'s shape (a flat mining-speed multiplier plus a flat attack-speed fraction) at roughly
  2.5x lightweight's magnitude (+25%/+20% vs +10%/+10%), since speed is rose gold's whole identity
  per the issue body.
- **Netherite's stats sit between cobalt/ardite and manyullyn on durability and mining speed, but
  below manyullyn on attack damage** (`netherite.json`: 1050 durability, 8.0 speed, 7.0 attack, vs.
  manyullyn's 820/7.02/8.72) -- manyullyn stays the top attack-damage material, per the issue body's
  explicit "manyullyn stats buffed above netherite" instruction. Handle (1.0x, +150) and extra
  durability (350) are likewise invented, chosen to land between ardite's and manyullyn's handle
  numbers.
- **Netherite's `fireproof` trait has no per-`Item` equivalent to port.** Vanilla netherite items are
  fire-immune via `Item.Properties#fireResistant`, a flag on the shared `Item` instance -- every
  Forgeweave pickaxe is one `Item` regardless of material, so that flag can't vary per stack.
  `ForgeweaveTraits#onEntityInvulnerabilityCheck` listens on NeoForge's
  `EntityInvulnerabilityCheckEvent` (fired for every entity's `isInvulnerableTo` check, item entities
  included) and grants invulnerability to `DamageTypeTags.IS_FIRE` sources only when the dropped
  `ItemEntity` is carrying a tool with this trait -- the minimal per-stack mechanism the issue asked
  for.
- **Netherite's `reinforced_core` trait is a new hook, not an upstream one.**
  `Trait#bonusSlots` is a new method on the trait interface (mirroring `Modifier#bonusSlots`,
  ADR-0004's precedent); `ForgeweaveModifiers#freeSlots` sums it in alongside the existing
  modifier-slot total. Nothing about `ModifierEntry` serialization changes -- slots stay computed,
  never stored, exactly as issue #103 asked.
- **A second `modifier_recipe` for `forgeweave:extra_slot`**
  (`modifier_recipe/extra_slot_netherite.json`, netherite ingot as reagent) needed no code change:
  `ModifierApplication#recipeFor` already resolves a reagent by scanning every registered recipe and
  matching on `Ingredient#test`, not by a one-recipe-per-modifier-id assumption, so two registry
  entries naming the same `modifier` field with different reagents already both apply toward the same
  `ModifierEntry`'s level. Verified, not fixed.
- **The four metals with no vanilla item form** (cobalt, ardite, manyullyn, rose gold) get ingot,
  nugget and raw-ore item forms. Ingot/nugget art for cobalt/ardite/manyullyn is a straight upstream
  port (table rows above); rose gold's is a recolour of upstream's manyullyn ingot/nugget art (no
  1.12 material to derive rose gold's own shape from, per the issue body). The raw-ore forms have no
  upstream counterpart at all -- 1.12 predates the raw-ore item split introduced in vanilla 1.17.
  Raw manyullyn and raw rose gold have no ore-block source in this PR's scope either, so they stay
  freshly authored placeholder icons under the standard `textures/item/` folder rather than
  `textures/derived/`, and carry no NOTICE row (CLAUDE.md: only derived art gets one). Raw cobalt and
  raw ardite were switched to maintainer-specified **vanilla** recolors by issue #140 (not an
  upstream/TiC derivation, so the license table above doesn't apply -- noted here per repo convention
  since no prior vanilla-derived row exists to follow): `raw_cobalt.png` is vanilla's own
  `raw_gold.png` hue-shifted to cobalt's blue, `raw_ardite.png` is vanilla's own
  `netherite_scrap.png` recoloured yellowish-orange and brightened, both via
  `scripts/recolor_raw_ore.py`, which scales saturation/value by the ratio of a target average
  (matched to this repo's own cobalt/ardite ingot art) to the source average so each source pixel's
  shading and highlights are preserved. Their melting recipes and `c:raw_materials/*` tags exist for
  a future ore-block issue to make reachable in survival; they are not obtainable through any recipe
  shipped in this PR.
- **`ForgeweaveItemTagsProvider` is new** (issue #103): puts the four metals' ingot/nugget/raw items
  into `c:ingots/*`, `c:nuggets/*` and `c:raw_materials/*` so `MeltingRecipe`'s tag-keyed rows
  (`cobalt_ingot.json` and friends) resolve, the same convention NeoForge itself already applies to
  vanilla iron/copper/gold/netherite (see the shipped `iron_ingot.json`/`netherite_ingot.json`
  melting rows, issue #96).

### Cobalt + ardite nether ore (issue #104) deviations from upstream 1.12

- **Tool tier maps onto `minecraft:needs_diamond_tool`, not a custom "cobalt tier".**
  `BlockOre#<init>` sets `setHarvestLevel("pickaxe", HarvestLevels.COBALT)`, upstream's own
  five-rung ladder's top rung (`STONE, IRON, DIAMOND, OBSIDIAN, COBALT = 4`) -- reachable in 1.12
  because the `obsidian` tool material's own `HeadMaterialStats` is pinned to `COBALT`
  (`TinkerMaterials`), i.e. an obsidian-tier tool, not a cobalt one, is upstream's actual entry
  point. CONTEXT.md forbids a custom numeric harvest level, so this PR maps straight onto the
  vanilla tag ladder's tightest tier below netherite: `minecraft:needs_diamond_tool` (a plain
  diamond pickaxe). `ForgeweaveBlockTagsProvider` is new -- the first Forgeweave block tags
  provider -- solely to carry this and the paired `minecraft:mineable/pickaxe` tag.
- **Drops one raw item, not the ore block itself, and never a different amount under Silk Touch.**
  Upstream's `BlockOre` has no `getDrops`/`quantityDropped` override at all: it unconditionally
  self-drops the block, once, with no fortune scaling. Forgeweave's raw-ore item split (#103) means
  the equivalent drop is one raw cobalt/ardite rather than the block; SCOPE.md M2's "ore blocks melt
  as their raw-drop equivalent -- no separate silk-touch yield axis" is read here as applying to the
  *loot table* too, so `ForgeweaveBlockLootSubProvider`'s new `oreDrop` helper is a plain
  unconditional one-item pool -- deliberately not vanilla `BlockLootSubProvider#createOreDrop`'s
  silk-touch/fortune branch, which would let a silk-touched block skip the raw-item conversion.
- **Blast resistance is invented, not ported.** `BlockOre` never calls `setResistance`, so its
  resistance is whatever `Block`'s own unset default is -- not a value upstream chose. This PR uses
  NeoForge's single-argument `strength(10.0F)`, applying hardness 10 to resistance too, matching how
  every other Forgeweave block with no upstream resistance override (e.g. `searedProperties()`)
  already uses the one-argument form.
- **Also tagged into `c:ores/cobalt`/`c:ores/ardite`.** Not read by upstream at all (1.12 predates
  the raw-ore split and the `c:` tag convention); added so a smeltery melts the ore block's own item
  form at the same 144 mB base as its raw drop (`cobalt_ore.json`/`ardite_ore.json`, mirroring the
  shipped `c:ores/iron`/`c:ores/copper` rows, issue #96) for any path that nets the block itself
  (creative, `/give`, a future silk-touch change) -- consistent with the loot-table point above, not
  a second yield axis.
- **World generation adapts 1.12's `IWorldGenerator` to 1.21's data-driven configured/placed feature
  + NeoForge biome modifier pipeline**, which has no like-for-like Java hook to port:
  `NetherOreGenerator#generate` runs unconditionally whenever `WorldProviderHell` is the current
  dimension, so `"biomes": "#minecraft:is_nether"` on the biome modifier is the direct translation
  (every Nether biome, not a subset). Vein size (5) and per-chunk count (20 for each metal,
  `Config.cobaltRate`/`arditeRate`) are ported as-is into `minecraft:ore`'s `size` and
  `minecraft:count`'s `count`. **Simplified**: upstream's `generateNetherOre` loop places attempts
  across two overlapping y-ranges (`y 32-96` and `y 0-128`, weighting the middle band slightly
  higher); this PR uses one `minecraft:height_range` uniform across the Nether's full `y 0-127`,
  which 1.21's placement API expresses far more simply and only trades away that middle-band
  weighting, not the overall range. `Config.genCobalt`/`genArdite` (both default-on toggles) have no
  Forgeweave equivalent -- M2 has no per-feature worldgen config surface yet.
- **No headless GameTest for actual chunk generation.** `NetherOreGameTests` covers the block's own
  tool-tier gate and drop identity (loot table + `isCorrectToolForDrops`); `SmelteryMeltingGameTests`
  covers melting. The configured/placed feature and biome modifier JSON are only verified by datapack
  loading succeeding at all (a malformed file fails datapack load, which `./gradlew build`'s
  `runGameTestServer` already boots) plus their datagen-freshness check; actually finding ore in a
  generated Nether is left to the manual release checklist per docs/SCOPE.md M2's testing strategy.

### Embossing (issue #154) deviations from upstream 1.12

- **Reagent substitution, by maintainer decision on the issue (2026-08-12).** Upstream's
  `ModExtraTrait.EMBOSSMENT_ITEMS` is green + blue + magma slime crystal plus a gold block. Slime
  crystals are world content Forgeweave has not shipped yet (docs/SCOPE.md defers slime islands to
  the world-content milestone), so `embossment.json` substitutes slime block + magma block + gold
  block, one block loosely standing in for each crystal colour. The revert to parity is in
  docs/SCOPE.md's deferred backlog and, because the cost is datapack JSON, is a data edit rather
  than a code change when the crystals ship.
- **One generated identifier per *material*, where upstream generates one per material *and trait
  set*.** Upstream's `generateIdentifier` concatenates the material id with the sorted trait ids, so
  an iron pickaxe head and an iron tool rod produce two distinct `ModExtraTrait`s. Issue #154 fixes
  Forgeweave's on `embossment.<material>`, which is what keeps a modifier entry inside ADR-0004's
  strict `id + level` rule with nothing derived stuffed into it. The consequence is deliberate and
  harmless: both iron parts serialize `forgeweave:embossment.iron` while granting the trait set
  their own part kind scopes, because the traits live in the tool's own `forgeweave:traits`
  component (exactly as upstream's `ToolBuilder#addTrait` puts them in the tool's NBT) rather than
  being re-derived from the id at read time. Nothing decodes ambiguously; the id is a label for the
  material the player spent, which is also all the tooltip claims.
- **No per-tool donor-part gate yet.** Upstream's `ModExtraTrait#canApplyCustom` refuses a tool that
  does not itself use the donor part (`toolCores.contains`), built up from `addCombination` as each
  tool registers its `PartMaterialType`s. Forgeweave accepts any buildable part of any material
  (shards excluded -- they are `PartItem.Kind.NONE`, not a part). With M3-5's three shipped tools all
  using the same three part kinds the gate would be a no-op, and the per-tool part table it needs is
  what the tool-roster issues are adding; wiring a throwaway half-table here would be undone by them.
  **Flagged for maintainer review** in the PR for this issue: the gate belongs with that table.
- **The Tool Station gained two input slots, restoring upstream's 4th and 5th repair positions.**
  Upstream's `TileToolStation` has six input slots and `ContainerToolStation` shows `activeSlots` of
  them per tab; M1 shipped three, which is enough for assembly and repair but two short of what a
  four-item embossment costs. This PR restores positions 4 and 5 (`GuiButtonRepair`'s static
  initializer) with upstream's own per-tab active-slot rule and its `ICON_Ingot`/`ICON_Gem` glyphs.
  Upstream's 6th position stays unused -- nothing Forgeweave ships needs a fifth free slot. Switching
  to a tab that hides a slot hands its contents back to the player rather than stranding them, which
  upstream does not have to solve because its slot count never shrinks below what is filled.
