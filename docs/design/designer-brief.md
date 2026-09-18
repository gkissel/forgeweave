# Designer brief: everything Forgeweave drew for itself

This is the work list. It names every visual asset in Forgeweave whose pixels did **not** come from Tinkers' Construct, plus the things that exist in the game with no art of their own yet. If an asset is on this list, a designer owns it.

It is a companion to [docs/texture-manifest.md](../texture-manifest.md), the art guide, which explains *how* to draw for this mod: canvas sizes, the greyscale tint rule, how a tool is assembled from layers, which scripts composite what, and the delivery checklist. This document is the *what* and the *in what order*. Read the rules below, then use the manifest when you sit down to draw.

Audited at commit [`ba4c0b8d`](https://github.com/gkissel/forgeweave/tree/ba4c0b8d53f1cf5dfcd0fe503de287838a819223). Every path link in this document is pinned to that commit, so it opens the file exactly as audited even after the art changes.

## The rules, in plain words

**Sprites are 16x16.** That is the standard and it is not moving. A batch of 32x32 assembled-tool renders was tried in 2026 and thrown out; whatever resolution you work at, deliver 16x16. The exceptions are not item sprites at all: worn armor sheets are 64x32, station GUI panels run 176x166 or 256x256, the status-effect icon is 18x18, the weapon slash particles are 32x32 and 16x32, and the fluid textures are tall animated strips. Each exception is called out where it appears below.

**Forged is what you draw.** Forgeweave ships two art sets. Forged is the default set, the art every player sees, and it is yours. Legacy is a built-in resource pack, off unless a player turns it on, that preserves the look Forgeweave had before the art rewrite started.

**Nothing you make goes into Legacy.** The Legacy pack carries art that came from Tinkers' Construct and nothing else. New things Forgeweave invented, the material forms such as plates and gears included, exist in Forged only. If you draw it, it ships at the normal path and never gets a Legacy copy. (Maintainer, 2026-09-18.)

**Delivering a finished sprite**, in the order it happens:

1. You hand over the PNG: RGBA, right size, right name. Pure greyscale if it is a tool part, a tool layer, or a worn armor sheet, because those get multiplied by the material's colour at runtime. Full colour for everything else.
2. A developer drops it at its normal path, replacing what was there.
3. If the file it replaced came from Tinkers' Construct, the developer copies the old file into the Legacy pack at the same relative path. If it came from Forgeweave, the old file is simply deleted.
4. The developer reruns the four generator scripts, so every pattern, cast, clay cast and broken-tool variant built from your sprite gets rebuilt in both sets.
5. Tests and datagen run, and the file is committed.

Step 1 is yours. Sections 4 and 7 of the art guide cover the rest if you want to read it.

**Greyscale and tinting.** Where an entry below says "tinted", the sprite is drawn in pure grey (R = G = B on every pixel) and the game multiplies it by the material's colour. Lightness is your only tool: a white pixel comes out as the material's raw colour, a mid-grey pixel at half intensity, a black pixel stays black. Use the full range, because your contrast becomes the finished piece's contrast. Where an entry says "not tinted", paint in whatever colours you like.

**The shared grey ramp.** Tool parts and tool layers are drawn on one five-value ramp: 68 for the outline, 160 for the deepest shade, then 196, 219 and 251 for the highlight. It exists because a built tool stacks three or four layers that all get multiplied by the same material colour, so a layer that strays off the ramp tints differently from the rest of the tool and the seam shows. Stay on it for anything in sections 6 and 7.

**New colours have to clear a test.** Forgeweave picks a hex for every material and fluid it invents, and `PaletteAuditTest` fails the build if two of them land closer than one just-noticeable step in OKLab. A 2026 playtest found two olive-yellow fuels, three teal ingots, three purples and three reds nobody could tell apart, which is why the test exists. You do not run it, but if you propose a colour for a new material, expect it to come back if it sits on top of an existing one. [`scripts/audit_palette.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/audit_palette.py) reports the same thing outside the build.

**References.** Every entry links to the file as it ships today, so you can open the placeholder in a browser and see what you are replacing. Vanilla Minecraft textures are fine as a style anchor. Tinkers' Construct art is not a reference for anything here: the whole point of Forged is that it is Forgeweave's own.

**Seeing your work.** A dev-only screenshot harness captures every screen, every held weapon, every worn armor set and every book page to a PNG, so a developer can send you the frame your sprite appears in without you installing anything. Entries below name the frame where one exists. The one to ask for when reviewing a replacement is `forged_legacy_compare`, which puts the Forged and Legacy versions of an icon side by side.

**Priority** means how often the maintainer sees the asset while playing:

- **Every session**: on screen constantly. Fix these first.
- **Sometimes**: seen in normal play, though not every minute.
- **Rare**: deep progression, an optional mod pairing, or a corner case.

## How this list was built

Forgeweave's root `NOTICE.md` carries one row per file that came from somewhere else. A texture with a row is Tinkers' Construct art (or Mantle, or the tool-leveling addon), so it is not on this list: it leaves on its own when a Forged sprite replaces it. A texture with no row is Forgeweave's own, and that is what this list collects. Where a file's origin was unclear, its commit history settled it.

The audit covered every PNG in the mod: 1,837 files in total. 1,139 have a `NOTICE.md` row. The remaining 698 are Forgeweave's, 617 of them at the normal default paths and 81 inside the Legacy pack. Those 81 are script-built pattern and cast composites that only exist because the two sets have different blanks; they are not anyone's to draw.

Of those 617, about 602 were written by a Python script recolouring a vanilla Minecraft texture. So most of this document is not authored art waiting to be improved; it is placeholder art that has never been designed.

## 1. Material forms

The biggest group on the list, and the cheapest to fix.

Every material in Forgeweave, 46 of them, has a set of intermediate items: dust you grind, plate you press, a rod, a gear, a wire. A player sees these constantly in inventory slots, in recipe books, and in other mods' machines. There are 374 of them, and they are all the same eight shapes in different colours.

They are made by [`scripts/generate_material_forms.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_material_forms.py), which takes one vanilla Minecraft item texture per form and hue-shifts it to each material's colour.

| Form | What it is | Count | Current placeholder |
| --- | --- | --- | --- |
| Dust | Ground material, the melting feedstock | 48 | Vanilla `glowstone_dust`, recoloured |
| Small dust | A third of a dust, 48 mB against 144 | 48 | Same, shrunk to 10x10 on a 16x16 canvas |
| Tiny dust | A ninth of a dust, 16 mB | 48 | Same, shrunk to 6x6 |
| Plate | Pressed sheet, a crafting output | 46 | Vanilla `paper`, recoloured |
| Double plate | Two plates pressed together | 46 | Two 13x13 `paper` copies offset 3px apart |
| Rod | Metal rod | 46 | Vanilla `blaze_rod`, recoloured |
| Gear | Toothed wheel | 46 | Vanilla `nether_star`, recoloured |
| Wire | Drawn wire | 46 | Vanilla `string`, recoloured |

Paths: `src/main/resources/assets/forgeweave/textures/item/<material>_<form>.png`. All 16x16. [Open the folder](https://github.com/gkissel/forgeweave/tree/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/item).

**What you actually draw: eight sprites.** One per form, one time. The gear is the worst offender and the most worth your attention: a recoloured nether star does not read as a gear at any size.

**There are no template files in the repository to open and edit.** The script reads its donor textures straight out of the Minecraft client jar at generation time, so nothing is checked in. Delivering eight new form shapes therefore also needs a developer to point the script at your files instead of at vanilla's. Draw them as ordinary 16x16 RGBA sprites in a mid-saturation neutral and hand them over; the wiring is a small change and not your problem.

**Tinting works differently here.** It is not the plain greyscale multiply used for tool parts. The script replaces each pixel's hue with the material's hue and scales its saturation and lightness by the ratio between the material's colour and the sprite's own average. Two consequences for you:

- Do not draw these in pure grey. A fully grey sprite has no saturation to scale, and the script falls back to painting a flat chroma across it, so all of your colour variation is lost. Draw them in colour, in a mid-saturation neutral, and the recolour keeps your shading.
- Keep clear lightness separation between the form's body and its shadow. The material's lightness is applied as a ratio to your average, so a flat sprite stays flat in all 46 colours.

**Priority: every session.** These are on screen more than anything else in the mod.

## 2. Ingots, nuggets, raw drops and crystals

The currency of every material: what the ore drops, what it smelts to, and the nugget it breaks into.

| Group | What it is | Path glob | Count | Current placeholder |
| --- | --- | --- | --- | --- |
| Ingots | Smelted bar, the castable unit | `textures/item/<material>_ingot.png` | 37 | Vanilla `iron`/`copper`/`gold`/`diamond`/`redstone`/`lapis`/`emerald` ingot, recoloured |
| Nuggets | A ninth of an ingot | `textures/item/<material>_nugget.png` | 37 | Same donor's nugget, recoloured. Copper falls back to iron, because vanilla ships no copper nugget |
| Raw drops | What the ore block drops before smelting | `textures/item/raw_<material>.png` | 12 | Vanilla `raw_iron`/`raw_copper`/`raw_gold`, recoloured |
| Gem crystals | Brimspar and fulmenite drop a faceted crystal instead of a raw chunk | `textures/item/{brimspar,fulmenite}_crystal.png` | 2 | Vanilla `amethyst_shard`, recoloured |

All 16x16. Written by [`generate_track_b_ore_textures.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_track_b_ore_textures.py) and [`generate_track_b_alloy_textures.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_track_b_alloy_textures.py); the two older raw drops, cobalt and ardite, come from [`recolor_raw_ore.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/recolor_raw_ore.py).

**What you draw: four sprites**, one ingot, one nugget, one raw chunk, one crystal. Same "no checked-in template" caveat and the same colour-not-grey rule as section 1.

Note that cobalt, ardite, manyullyn, rose gold, steel, knightslime, pig iron, amethyst bronze, queen's slime and hepatizon are **not** in these counts. Their ingots and nuggets are Tinkers' art with `NOTICE.md` rows, so they are outside this list until a Forged batch reaches them.

**Priority: every session.**

## 3. Ore blocks, storage blocks and raw storage blocks

What you see in the world while mining and in a storage room.

| Group | What it is | Path glob | Count | Current placeholder |
| --- | --- | --- | --- | --- |
| Ore blocks | The ore in the wall | `textures/block/<material>_ore.png` | 12 | A vanilla ore texture with the host rock left untouched and only the mineral blob recoloured. End-stone ores are painted onto vanilla `end_stone`, because vanilla has no end-stone ore to borrow |
| Storage blocks | Nine ingots compressed | `textures/block/<material>_block.png` | 37 | Vanilla metal block, fully recoloured |
| Raw storage blocks | Nine raw drops compressed | `textures/block/raw_<material>_block.png` | 10 | Vanilla raw block, fully recoloured |

All 16x16, not tinted at runtime, one baked file per material. [Open the folder](https://github.com/gkissel/forgeweave/tree/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/block).

**What you draw: three block faces**, one ore overlay shape, one storage block, one raw storage block. The ore mask is worth knowing about: the script decides which pixels are "ore" and which are "rock" by comparing the donor against the plain host texture, and it needs a clean separation between the two. A soft, blended ore blob would break the mask, so keep the mineral shape crisp against the rock.

Host rock varies by material (stone, deepslate, netherrack, end stone), so the ore overlay has to sit legibly on all four.

**Priority: every session** for the ores, **sometimes** for the storage blocks.

## 4. Smeltery tiers, one entry per tier

The smeltery's walls follow its core's tier, so each tier is a full recolour of 23 wall and floor faces. All of it is generated from one brick per tier by [`generate_seared_tier_textures.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_seared_tier_textures.py), which means the designer owns the brick and the core faces and the script does the other 20 or so.

### Standard, the seared tier

Nothing for the designer. All 23 faces plus the Standard Core's two fronts are Tinkers' art with `NOTICE.md` rows. The Standard Core's sides are the seared brick texture itself.

### Nether tier, 7 files

`textures/derived/block/seared_bricks_nether.png`, `seared_tank_side_nether.png`, `seared_tank_top_nether.png`, `nether_core_side.png`, `nether_core_front_active.png`, `nether_core_front_inactive.png`, `nether_core_v2_front_active.png`. All 16x16, full colour, not tinted. Already designer art as of PR #983 and #987, so these are a style reference for the two tiers below rather than a gap. [Open the folder](https://github.com/gkissel/forgeweave/tree/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/derived/block).

The v2 front is the Nether Core's look above 1600 degrees. It is an active-only override, which is why there is no matching inactive or side file.

### End tier, 4 files

`seared_bricks_end.png`, `end_core_side.png`, `end_core_front_active.png`, `end_core_front_inactive.png`. Designer art since PR #983. Note that the End tier's tank faces are script-generated, unlike the Nether tier's, so if the End tank reads wrong in game it needs two new hand-drawn files and a script change.

### Deep tier, 4 files

`seared_bricks_deep.png`, `deep_core_side.png`, `deep_core_front_active.png`, `deep_core_front_inactive.png`. Designer art since PR #987. Its cyan sculk detail sits outside the palette ramp on purpose.

### The constraint that governs all three tiers

When you hand in a tier's brick, the script derives the other 20 faces of that tier from the **standard** seared faces by swapping greys for your brick's colours. It only touches a pixel where red, green and blue are **exactly** equal. A pixel that is off by one in any channel is left as standard-tier grey, and it will show as a grey speck on a red, purple or black wall.

It also reads your brick in two fixed bands: grey 28 to 38 is mortar, grey 46 to 161 is body. Greys in 39 to 45 fall through to the darkest body colour. So when you draw a tier brick, put the mortar lines in the dark end and the block body in the wide middle, and keep both inside those bands.

**Priority: every session.** A smeltery is the centre of the game.

## 5. The energized tank

A smeltery wall that burns Forge Energy instead of lava, added in PR #1014. There is no Tinkers' counterpart, so all of it is Forgeweave's.

### Block textures, 3 files

`textures/block/energized_tank_side.png`, `energized_tank_side_overdrive.png`, `energized_tank_top.png`. All 16x16, hand-drawn by an agent, not tinted.

It shows **two** states, not three: the blockstate has one boolean, `overdrive`, and `energized_tank_top` serves as both top and bottom face. The screen displays heat and charge as numbers, but the block itself does not change with charge level. If it should, that is new art and a new blockstate property, so raise it before drawing.

- [energized_tank_side.png](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/block/energized_tank_side.png)
- [energized_tank_side_overdrive.png](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/block/energized_tank_side_overdrive.png)
- [energized_tank_top.png](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/block/energized_tank_top.png)

**Priority: sometimes.**

### Its screen, and the mod's only hand-made widget

PR #1026 gave the tank a screen. Every pixel of it is borrowed from another screen except one bar.

The background is Tinkers' blank station panel, [`derived/gui/blank.png`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/derived/gui/blank.png), 256x256 and drawn as a 176x166 window, the same file the pattern chest uses. Its whole upper half is empty. The fuel gauge and its 52x52 scale overlay are lifted from the smeltery screen's sheet.

The energy bar is drawn as two filled rectangles in code, with no sprite behind it:

- Track: 102 wide by 8 tall at panel coordinates (66, 36), colour `#373737`.
- Fill: inset one pixel on every side, so 100 by 6 at (67, 37), colour `#E8620E`, Forge Energy orange.
- No border, no end caps, no gradient.

The class comment says why outright: a charge level is not a fluid, and none of the ported sheets has a horizontal bar on it.

The rest of the right-hand column, for layout: heat at y=16, cost at y=26, the bar at y=36, the charge numbers at y=46, and the overdrive button at y=56, all at x=66 and 102 wide.

**There is no `ScreenshotHarness` frame for this screen.** Every other screen in the mod has one, so reviewing this one means launching the game and building a tank.

**Priority: sometimes.** It is the only screen that looks unfinished, so it may be worth more than its play frequency suggests.

## 6. Tool part silhouettes still waiting on a Forged sprite

These are Tinkers' art, so strictly they sit outside this list. They are here because they belong in the same queue and because one drawing goes a long way.

A tool part's silhouette drives four files. Redraw the part and the scripts rebuild its stencil pattern, its gold cast, its clay cast and, where it has one, its broken art, in both art sets. **17 part silhouettes are still Tinkers' art at their default path:**

`cross_guard`, `excavator_head`, `fletching`, `hammer_head`, `kama_head`, `large_sword_blade`, `maille`, `pan`, `pickaxe_head`, `shard`, `sharpening_kit`, `shovel_head`, `sign_plate`, `sword_blade`, `tough_tool_rod`, `vein_hammer_head`, `wide_guard`.

Paths: `textures/derived/item/<part>.png` and, for the assembled-tool layer, `textures/derived/tools/<tool>_<role>.png`. All 16x16. [Open the item folder](https://github.com/gkissel/forgeweave/tree/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/derived/item).

Twelve others are already Forged: `arrow_head`, `arrow_shaft`, `axe_head`, `bow_limb`, `bow_string`, `broad_axe_head`, `hand_guard`, `knife_blade`, `scythe_head`, `tool_binding`, `tool_handle`, `tough_binding`.

**These are tinted.** Draw them in pure grey, all three channels equal, and carry the shape in the alpha channel. Two script-level details follow from that:

- The stencil pattern script reads alpha with a threshold of 64. A pixel fainter than that is treated as absent and gets no imprint.
- Each part has a hand-picked offset so its imprint lands centred on the pattern. If your redraw moves the silhouette on the canvas, that offset needs re-measuring. Flag it when you hand the file over.

Frames to look at: `tool_station`, `part_builder`, `stencil_table`, and `weapon_<tool>` for each assembled tool.

**Priority: every session** for `pickaxe_head`, `shovel_head`, `sword_blade`, `tough_tool_rod`, `tool_handle`. **Sometimes** for the rest.

While you are in this folder, a note on the five rapier and scythe layers: `rapier_binding`, `rapier_handle`, `rapier_head`, `rapier_head_broken` and `scythe_binding` each carry one or two pixels that are not exactly grey. They will tint slightly off-hue. Invisible at 16 pixels, but worth cleaning up if you redraw that set.

## 7. Forgeweave's own weapons

The katana, the scimitar and the war mace do not exist in Tinkers' Construct. Neither do the `curved_blade` and `war_mace_head` parts. They have always been Forgeweave's, and **they never get a Legacy copy**: a test fails if one appears.

Their layers carry no `NOTICE.md` row, so all of them hold original art at the default path, redrawn in the 16px batches of PRs #810, #977, #982 and #1001.

| Tool | Layer files | Notes |
| --- | --- | --- |
| Katana | `tools/katana_handle.png`, `tools/katana_binding.png`, `derived/tools/katana_head.png`, `derived/tools/katana_head_broken.png` | The handle and binding live in `textures/tools/`, not `derived/tools/`, because they were always original. The broken head is hand-drawn, the one exception to the automatic chip transform |
| Scimitar | `derived/tools/scimitar_{handle,head,binding,head_broken}.png` | |
| War mace | `derived/tools/warmace_{handle,head,binding,head_broken}.png` | |
| Parts | `derived/item/curved_blade.png`, `war_mace_head.png`, `katana_blade.png` | The scimitar's blade part is `curved_blade` |

All 16x16, all tinted, all on the five-value grey ramp. Frames: `weapon_katana`, `weapon_scimitar`, `weapon_warmace`, each with `_firstperson` and `_offhand` variants.

**Two of these are not hand-drawn, and they are worth redrawing.** The katana's guard and wrapped grip come out of [`generate_katana_art.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_katana_art.py), which paints them pixel by pixel from two ASCII maps written into the script. No template, no donor, nothing to trace: an agent typed out a 16 by 16 grid of palette letters. They are on the grey ramp and they work, but nobody designed them. The grip's banding was widened from 251/219 to 251/160 so the wrap still reads after a dark handle material tints it, which is the kind of decision a designer should be making rather than inheriting.

`textures/mob_effect/lacerate.png` is the same story: the status-effect icon for the scimitar's bleed, three claw slashes, painted from an 18-row ASCII map in [`generate_scimitar_art.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_scimitar_art.py). **18x18**, not 16x16, because that is vanilla's mob-effect icon size. Not tinted, so it uses a real four-value red ramp (`#4A0C0E`, `#981E20`, `#CC3430`, `#EE6C60`) rather than greys, and you should paint it in full colour too. [Open it](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/src/main/resources/assets/forgeweave/textures/mob_effect/lacerate.png).

**Priority: every session** for the three weapons' layers if the current art does not satisfy. **Sometimes** for the lacerate icon.

## 8. Heavy armor

The set is in the game and playable, and it has no art of its own at all: every pixel it shows belongs to the light armor set.

Four items, `heavy_helmet`, `heavy_chestplate`, `heavy_leggings` and `heavy_boots`, built from three materials each: plating, maille, and a third large plate slot that the light set does not have.

That splits into three jobs, worth costing one at a time:

**(a) Item sprites.** The heavy pieces currently render the light set's sprites, because the code strips the `heavy_` prefix before looking up art. So a heavy chestplate and a light chestplate look identical in the inventory. Worse, the third material is invisible: the item model filters the large plate layer out entirely, with a code comment saying it does so because that layer has no sprite. Deliverable: 4 plating layers and, if the third material should show, 4 large plate layers. `textures/derived/tools/<piece>_{plating,maille}.png`, 16x16, tinted, greyscale.

**(b) Worn armor layers.** These are the sheets that render on the player's body, and they are **64x32**, Minecraft's legacy armor layout, not 16x16. Today there are four, shared between the light and heavy sets: `textures/models/armor/derived/{plating,maille}_layer_{1,2}.png`. Layer 1 covers helmet, chestplate and boots; layer 2 covers leggings. All four are Tinkers' art with `NOTICE.md` rows, and they are greyscale and tinted at render time by the part material's colour. Deliverable: up to 4 new sheets if only the heavy set gets its own look, 8 if both sets do.

**(c) 3D model work: none needed.** Both sets draw on vanilla's humanoid mesh, one pass per declared layer. There is no custom model class and nothing to rig. Bulkier heavy geometry would be a code change, so raise it as a request rather than assuming it.

Frames: `tool_forge_heavy_armor` for the station tab, `armor_heavy_iron` and `armor_heavy_iron_firstperson` for the worn look. Compare against `armor_iron`, `armor_cobalt` and `armor_obsidian_chestplate`.

**Priority: sometimes.** The light set carries the look today, so nothing is broken, but a tier that is visually identical to the tier below it is a real gap.

## 9. Things with no art at all

Each of these is a clean, self-contained deliverable.

| Asset | Where it goes | Size | State |
| --- | --- | --- | --- |
| Mod logo | jar root, wired via `logoFile` in `neoforge.mods.toml` | 128x128 or 256x256 | Does not exist. The manifest carries a written decision deferring it, on the grounds that deriving Tinkers' gear logo would read as impersonating their brand |
| `pack.png` for the mod | `src/main/resources/pack.png` | 128x128 | Does not exist |
| `pack.png` for the Legacy pack | `src/main/resources/resourcepacks/legacy/pack.png` | 128x128 | Does not exist, so the pack shows the missing-texture placeholder in the resource pack list |
| Advancement tab background | `textures/gui/advancements/backgrounds/` | tiling | Reuses vanilla's stone background. Optional |
| Longsword slash particle | `textures/particle/` plus a rewrite of `particles/slash_longsword.json` | 8 frames | Borrows vanilla's `sweep_0` to `sweep_7` outright. Every other weapon has its own sweep sheet. Optional, but it is the one weapon whose swing does not match the others |
| `slime_layer_2.png` | `textures/models/armor/derived/` | 64x32 | Missing, while plating and maille both have their layer 1 and layer 2 pair. Whether slime armor needs a leggings sheet is a code question, not a drawing one; see the open questions at the end |

**Priority: rare** for all of these, except the two `pack.png` files and the logo, which are the first thing anyone sees in a mod list. Call those **sometimes**.

## 10. One-off items

Three item sprites that no template covers.

| Item | What it is | Path | Origin |
| --- | --- | --- | --- |
| Dusk cage | A murkiron lantern cage that captures a mob | `textures/item/dusk_cage.png` | Drawn procedurally from nothing by [`generate_dusk_cage_texture.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_dusk_cage_texture.py), using a five-colour palette off murkiron's `#3A5C56` |
| Weldheart | The catalyst a Draconic Evolution fusion craft consumes | `textures/item/weldheart.png` | Drawn procedurally by [`generate_weldheart_texture.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_weldheart_texture.py): three nested diamonds in the three weld metals' colours |
| Nahuatl board | A crafting intermediate that makes nahuatl plating and maille reachable | `textures/item/nahuatl_board.png` | Hand-drawn by an agent in PR #740. The only sprite in `textures/item/` that no script produces |

All 16x16, not tinted, paint in full colour. **Priority: rare.**

## 11. What is already covered, so you can skip it

Worth stating so nobody spends a day on something that is not a gap.

- **Every GUI sheet.** All 21 are Tinkers' or Mantle art with `NOTICE.md` rows: the tool station, part builder, stencil table, smeltery, seared furnace, reservoir and duct, the chest panel, the shared side panel and info panel, the station icon sheet, the two bow crosshairs. The crafting station deliberately uses vanilla's crafting table panel, matching what Tinkers' itself does. The only GUI pixels Forgeweave draws are the energized tank's energy bar in section 5 and a flat hover highlight in the book.
- **Every book image.** Four files, none of them ours. The book spread and the cover, 512x512 each, come from Mantle, the SlimeKnights library the 1.12 book engine lives in. The modify page (256x256) and the smeltery diagram (854x480) come from Tinkers'. `appearance.json` references no image; it sets a cover tint of `#ffce85`. No book page points at a file that does not exist.
- **Every JEI panel and icon.** Four panel sheets, all with rows. Ten of eleven category icons are just an item stack, so they inherit whatever that item's sprite becomes. The eleventh, entity melting, crops a 16x16 square out of the melting sheet.
- **Every creative tab icon.** Six tabs, each showing an existing item: blue slime crystal, pickaxe, pickaxe head, seared tank, slime sling, green slime soil.
- **Every advancement icon.** Eleven advancements, all displaying an item stack.
- **Armor parts, casts and patterns.** Plating, maille and large plate, with their stencil patterns and gold and clay casts, are all present and all rowed. No gaps.
- **Fluids.** Six textures for the whole mod, all rowed, all animated: `molten_metal` and its flow, `liquid` and its flow, `liquid_stone` and its flow, 16 to 32 frames each, from 16x320 up to 32x1024. Every molten metal shares the same greyscale pair and is tinted per fluid, which is why there are six files and not a hundred.
- **Fluid buckets.** There is no bucket art to draw. NeoForge's dynamic container model draws the fluid inside a vanilla bucket and reads the fluid's own tint.
- **Entities.** The mod adds four entity types and ships one entity texture: the blue slime, 64x32, rowed, greyscale and tinted `#67f0f5` at runtime. Arrows, shuriken and indestructible items all render their own item model, so they have no entity texture.
- **Particles.** All 45 are rowed. Five heart overlays at 8x8; five weapon slash sheets at 8 frames each, sized 32x32 for axe and hammer, 16x32 for cleaver and rapier, 16x16 for the frying pan. The hatchet and lumberaxe share the axe sheet. The 16x16 rule does not apply to slash frames.
- **Ponder scenes.** The seven `.nbt` files are in-world block layouts, not drawings. How they look depends entirely on the block textures in sections 3, 4 and 5.
- **Track A material presets.** These never get their own sprite. They render through the greyscale part sprites of section 6, tinted by the material's `color` field. So every Track A material a compat pack adds inherits your part art for free, and there is nothing per-material to draw. Same for Occultism, Mystical Agriculture, Elementarium and the Allthemodium tiers: they are datapack material definitions pointing at other mods' items.
- **Mystical Agriculture crop and essence sprites** were expected here from PR #1036. That PR is still open at the audited commit, so nothing of it ships yet. If it merges, its sprites need their own pass.

## 12. Upcoming: the trident family

Planning only, issue #990, no code and no art yet. Listed so it can be scheduled rather than arriving as a surprise.

The maintainer asked for a Forgeweave trident. Tinkers' 1.12 predates the vanilla trident, so there is no upstream art and no upstream design. Everything about it, including the art, will be original.

Three questions on that issue decide what gets drawn: how many parts the trident has (the proposal floats a three-part shape like the deferred javelin), whether the head accepts non-metal materials, and whether riptide, channeling and loyalty become traits or modifiers. Riptide in particular decides whether the tool needs a thrown look distinct from its held look.

If the shape settles as head, handle and binding, expect the usual set: three greyscale tinted layers at `textures/derived/tools/trident_{handle,head,binding}.png`, a broken head, and one part silhouette at `textures/derived/item/trident_head.png`. The stencil pattern, gold cast and clay cast come free from the scripts. All 16x16.

**Priority: upcoming.** Do not start it; wait for the planning session to close.

## Summary

| Section | Assets | Templates to draw | Priority |
| --- | --- | --- | --- |
| 1. Material forms | 374 | **8** | Every session |
| 2. Ingots, nuggets, raw drops, crystals | 88 | **4** | Every session |
| 3. Ore, storage and raw storage blocks | 59 | **3** | Every session / sometimes |
| 4. Smeltery tiers (standard, Nether, End, Deep) | 15 designer files of 110 | 0 new; Nether, End and Deep already drawn | Every session |
| 5. Energized tank block and screen | 3 blocks + 1 energy bar | **4** | Sometimes |
| 6. Tool part silhouettes still on Tinkers' art | 17 parts driving ~68 generated files | **17** | Every session / sometimes |
| 7. Forgeweave's own weapons | 15 | 0 new; already Forged. Lacerate icon is the exception | Sometimes |
| 8. Heavy armor | 0 exist | **4 item layers + up to 8 worn sheets** | Sometimes |
| 9. Things with no art at all | 0 exist | **6** | Sometimes / rare |
| 10. One-off items | 3 | **3** | Rare |
| 11. Already covered | 1,139 rowed + borrowed | 0 | n/a |
| 12. Trident family | 0, planning | 0 until #990 closes | Upcoming |
| **Total** | **617 Forgeweave-owned sprites** | **about 57 drawings** | |

617 sprites ship today with Forgeweave's name on them, and roughly 57 original drawings would replace all of them.

## Open questions and things that could not be classified

Nothing in the audit was left unclassified: all 1,837 PNGs resolved to a `NOTICE.md` row, a named generator script, or a specific pull request. The six items below are judgement calls rather than classification failures.

1. **`brimspar_ore.png`.** 16x16, full colour, no row, so it is Forgeweave's either way. It arrived in PR #907 with the fuel-ladder work rather than in a Track B texture batch, and the ore script builds its donor table at import time, so whether brimspar is in that roster or was placed by hand could not be read off the table alone. Treat it as section 3 work regardless.
2. **`slime_layer_2.png`.** Confirmed absent while plating and maille both have their layer pair. Whether slime armor has a leggings piece is a code question and was out of this audit's scope.
3. **Two dead katana files.** `derived/tools/katana_binding.png` and `derived/tools/katana_handle.png` are byte-identical to the live pair in `textures/tools/`, and nothing in the generated models, the Java sources or the scripts references them. They arrived incidentally in PR #979. Redraw the pair at `textures/tools/`; the two under `derived/tools/` are for a developer to delete.
4. **Whether the material forms should keep using vanilla donors at all.** Replacing the eight form shapes needs a script change as well as eight sprites, since there is nothing checked in to overwrite. That is a small decision but it has to be made before section 1 can land.
5. **The energized tank has no screenshot frame.** Every other screen in the mod does. Reviewing new art for it means building a tank in game.
6. **Two generator scripts appear to have been overtaken by Forged batches.** [`derive_warmace_art.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/derive_warmace_art.py) builds the war mace's four sprites by copying and mirroring Tinkers' hammer, and [`generate_battleaxe_head.py`](https://github.com/gkissel/forgeweave/blob/ba4c0b8d53f1cf5dfcd0fe503de287838a819223/scripts/generate_battleaxe_head.py) copies the battleaxe's four layers straight out of the 1.12 clone. Neither war mace file carries a `NOTICE.md` row any more, and the two battleaxe heads have lost theirs too, so Forged sprites already sit at those paths. Rerunning either script would put derived pixels back. A developer should confirm whether they still need to exist. Not a drawing question, but it affects whether your war mace and battleaxe art stays put.

