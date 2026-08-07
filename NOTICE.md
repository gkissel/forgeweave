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
| `src/main/resources/assets/forgeweave/textures/item/pattern.png` | `resources/assets/tconstruct/textures/items/pattern.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/item/pickaxe_head.png` | `resources/assets/tconstruct/textures/items/pickaxe/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/item/shovel_head.png` | `resources/assets/tconstruct/textures/items/shovel/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/item/axe_head.png` | `resources/assets/tconstruct/textures/items/hatchet/head.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/item/tool_binding.png` | `resources/assets/tconstruct/textures/items/parts/binding.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/item/tool_handle.png` | `resources/assets/tconstruct/textures/items/parts/tool_rod.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/block/part_builder_top.png` | `resources/assets/tconstruct/textures/blocks/partbuilder_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/block/part_builder_side.png` | `resources/assets/tconstruct/textures/blocks/table_side.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (blank pattern recipe) | `resources/assets/tconstruct/recipes/tools/pattern.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/data/ForgeweaveRecipeProvider.java` (Part Builder block recipe) | `resources/assets/tconstruct/recipes/tools/table/part_builder.json` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/java/dev/gkissel/forgeweave/menu/PartBuilderRecipes.java` (per-part material costs) | `src/main/java/slimeknights/tconstruct/tools/TinkerTools.java` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
| `src/main/resources/assets/forgeweave/textures/block/tool_station_top.png` | `resources/assets/tconstruct/textures/blocks/toolstation_top.png` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | MIT |
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

Each material JSON derives its stat values and tint color from that file; the Java that loads them is an
independent reimplementation against NeoForge's datapack registry API and carries no row.

The `pattern.png` texture is shared by the blank pattern and all five part-pattern item models (they
render identically upstream too; only the name/tooltip differs). The five part textures are the
greyscale/base variants from `items/parts/*.png` and `items/<tool>/head.png`, chosen per ADR-0003 and
the M1 issue brief because they are designed for client-side tinting rather than a fixed material
color, matching Forgeweave's `RegisterColorHandlersEvent.Item`-based tint approach. All item model
JSONs referencing these textures are written fresh for 1.21's `item/generated` format and carry no
row of their own.

The Part Builder block model is a plain top/side/bottom cube (`cube_bottom_top`), not upstream's
compound "tabletop + 4 legs" model (`models/block/table.json`) -- upstream's own top/side textures
for this block are near-solid-color placeholders, so the extra leg geometry wasn't worth porting for
the same visual payoff; the bottom face reuses vanilla's `oak_log` texture directly (as upstream's
model does too), so it isn't copied and carries no row. The Part Builder GUI background
(`assets/forgeweave/textures/gui/part_builder.png`) is freshly authored, not derived from upstream's
`partbuilder.png` -- that texture's non-rectangular (rounded-corner) panel and 5-slot layout didn't
fit this GUI's simpler 3-slot layout, so per the issue #9 brief it uses a plain generic background
instead. Neither carries a NOTICE row.

The Tool Station block model (issue #10) is the same plain top/side/bottom cube approach, reusing
`part_builder_side.png` directly for its side texture rather than duplicating the file -- upstream's
own `toolstation.json` and `partbuilder.json` both point at the same `table_side.png`, so this isn't
a new derivation and carries no additional row. The Tool Station GUI background
(`assets/forgeweave/textures/gui/tool_station.png`) is likewise freshly authored, not derived from
upstream's `toolstation.png`, for the same reason as the Part Builder's: this GUI has a different
slot count/layout (4 slots vs. upstream's 5) and upstream's texture is a non-rectangular panel.
Assembled tool item models (`ToolItem`) are three-layer composites of the five existing derived part
textures (`pickaxe_head.png`/`shovel_head.png`/`axe_head.png`/`tool_binding.png`/`tool_handle.png`,
already rowed above), tinted per layer by `ForgeweaveItemColors`; no new texture files were added for
tools, so there is nothing further to row.

Trait behavior (issue #12) is ported semantics, not copied code: the rates, conditions and magnitudes
are upstream's, the `Trait` interface and its registry are written fresh against 1.21's data
components and NeoForge events. Two deliberate deviations, both forced by ADR-0002's one-trait-id-per-
material data model: upstream's flint grants `crude2` on the head part plus `crude` elsewhere, which
stacks to level 3 (+15% vs unarmored), while Forgeweave's flint grants plain `crude` (level 1, +5%);
and upstream's stone grants `cheapskate` on the head part on top of `cheap`, a durability *penalty*
Forgeweave does not ship (stone's lower durability is already in its material JSON). `Trait.java`
carries only the four hooks the four shipped traits use rather than upstream's full `ITrait` surface,
so it is not a derived file and carries no row.
