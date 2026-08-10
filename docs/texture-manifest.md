# Texture manifest for designers

*(Versão em português: [texture-manifest.pt-BR.md](texture-manifest.pt-BR.md))*

Every texture the game renders is a real PNG under `src/main/resources/assets/forgeweave/textures/`. Nothing is composited at runtime. Replacing a file with a **same-named PNG of the same dimensions** and rebuilding the mod (`./gradlew build`) is all it takes — no other step. CI's `TextureReferenceAuditTest` fails the build if a referenced texture goes missing, so this list cannot silently drift from what's in game.

## Folder rules

| Folder | Meaning |
| --- | --- |
| `textures/derived/**` | Art derived from upstream Tinkers' Construct 1.12 (provenance per file in the repo-root `NOTICE.md`). **These are the replacement targets for the original-art rewrite (M9)** — replacing them with original art is the goal. |
| `textures/item`, `textures/block`, `textures/gui` | Original Forgeweave art. Already ours; restyle freely. |

## Flags used below

- **T (tinted greyscale)** — the file is greyscale and the game tints it in code. Part/tool textures are tinted per material (wood brown, cobalt blue, …); the two molten-metal strips are shared by **all nine fluids** and tinted per fluid. Keep these greyscale — painting color into them breaks every tinted variant. The tint colors themselves are code/data, not pixels.
- **G (script-generated)** — produced by a script in `scripts/` (`generate_cast_textures.py`, `recolor_raw_ore.py`). Hand-painted replacements are fine, but re-running the script overwrites them.
- **L (layout contract)** — GUI panel: code draws slots, gauges, tabs and text at fixed pixel coordinates aligned to this art (one unit test even pins the smeltery melt-grid geometry to the PNG). Restyle colors/detail freely, but keep panel dimensions and every slot/window/gauge position.
- **A (functional alpha)** — transparency is gameplay-visible: tank/gauge/window holes are where fluid renders; cast cavities read as recesses. Keep the alpha shapes.
- **M (animated)** — has a `.png.mcmeta` sibling controlling animation; keep both files and the frame layout (vertical strip).

## Blocks — `derived/block/` (all 16×16 unless noted)

| File(s) | Notes |
| --- | --- |
| `seared_bricks`, `seared_stone`, `seared_cobblestone`, `seared_cracked_bricks`, `seared_fancy_bricks`, `seared_square_bricks`, `seared_triangle_bricks`, `seared_small_bricks`, `seared_creeper`, `seared_paver`, `seared_road`, `seared_tile` | Decorative seared family. `seared_bricks` is also the Standard Core's side/top. |
| `standard_core_front_active`, `standard_core_front_inactive` | Smeltery Standard Core front (lit/unlit). |
| `nether_core_front_active`, `nether_core_front_inactive`, `nether_core_side` | Nether Core — deliberately reddish to distinguish the tier. |
| `seared_tank_side`, `seared_tank_top`, `seared_gauge_side`, `seared_window_side`, `seared_window_top` | **A** — window holes are alpha cutouts; fluid renders through them. |
| `seared_drain_front`, `seared_drain_back` | Drain. |
| `faucet.png` | Faucet. |
| `casting_table_top/side/bottom`, `casting_basin_top/side/bottom` | Casting blocks. |
| `cobalt_ore`, `ardite_ore` | Nether ores (netherrack-composited). |
| `grout.png` | Grout block. |
| `molten_metal.png` (16×320), `molten_metal_flow.png` (32×512) | **T, M** — single greyscale still/flow pair shared by all 9 molten fluids, tinted per fluid in code. Animated strips with `.mcmeta`. |
| `part_builder_top/side`, `tool_station_top`, `crafting_station_top/side`, `stencil_table_top`, `pattern_chest_front/side/top`, `part_chest_front/side/top` | M1 station blocks. |

## GUI — `derived/gui/`

| File | Size | Notes |
| --- | --- | --- |
| `tool_station.png` | 256×256 | **L** |
| `part_builder.png` | 176×166 | **L** |
| `stencil_table.png` | 176×166 | **L** |
| `smeltery.png` | 256×256 | **L** — L-shaped panel; melt grid sits in the transparent notch (geometry pinned by a test). |
| `generic.png` | 64×64 | **L** — shared slot/frame tiles (incl. the empty-slot tile the melt grid reuses). |
| `info_panel.png` | 256×256 | **L** — side info panel. |
| `station_icons.png` | 256×256 | **L** — station tab icons sheet. |

## Items — `derived/item/` (16×16)

| File(s) | Notes |
| --- | --- |
| `pickaxe_head`, `shovel_head`, `axe_head`, `tool_binding`, `tool_handle`, `shard` | **T** — greyscale part sprites, tinted per material. |
| `pattern`, `pattern_pickaxe_head`, `pattern_shovel_head`, `pattern_axe_head`, `pattern_tool_binding`, `pattern_tool_handle` | Patterns (untinted). |
| `cast.png`, `cast_ingot`, `cast_nugget` | Gold casts; cavity uses alpha (**A**). |
| `cast_pickaxe_head`, `cast_shovel_head`, `cast_axe_head`, `cast_tool_binding`, `cast_tool_handle` | **G, A** — composited by `generate_cast_textures.py` (gold base + punched part cavity). |
| `cobalt_ingot/nugget`, `ardite_ingot/nugget`, `manyullyn_ingot/nugget` | Metal items (upstream-derived). |
| `rose_gold_ingot/nugget` | Recolour-derived from manyullyn art. |
| `moss`, `mending_moss`, `reinforced_plate`, `silky_cloth`, `silky_jewel`, `extra_modifier` | Modifier reagents. |
| `seared_brick.png` | Seared brick item. |

## Tools — `derived/tools/` (16×16)

| File(s) | Notes |
| --- | --- |
| `pickaxe_head/binding/handle`, `shovel_head/binding/handle`, `hatchet_head/binding/handle` | **T** — greyscale per-part layers of the held tool, tinted per material. |

## Original art — `item/` (16×16)

| File(s) | Notes |
| --- | --- |
| `raw_cobalt.png`, `raw_ardite.png` | **G** — `recolor_raw_ore.py` from vanilla raw gold / netherite scrap (maintainer-specified). |
| `raw_manyullyn.png`, `raw_rose_gold.png` | Original Forgeweave art. |

## Rules of thumb

1. Same name + same dimensions + same folder → drop-in replacement, just rebuild.
2. New files or renames need model/code changes — talk to a developer first.
3. Keep **T** files greyscale, keep **A** files' alpha shapes, keep **L** files' layout geometry, keep **M** files' frame strips + `.mcmeta`.
4. `NOTICE.md` at the repo root lists each derived file's upstream source — when you replace one with original art, its NOTICE row gets removed in the same change.
