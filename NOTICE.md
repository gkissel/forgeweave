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

Each material JSON derives its stat values and tint color from that file; the Java that loads them is an
independent reimplementation against NeoForge's datapack registry API and carries no row.

The `pattern.png` texture is shared by the blank pattern and all five part-pattern item models (they
render identically upstream too; only the name/tooltip differs). The five part textures are the
greyscale/base variants from `items/parts/*.png` and `items/<tool>/head.png`, chosen per ADR-0003 and
the M1 issue brief because they are designed for client-side tinting rather than a fixed material
color, matching Forgeweave's `RegisterColorHandlersEvent.Item`-based tint approach. All item model
JSONs referencing these textures are written fresh for 1.21's `item/generated` format and carry no
row of their own.
