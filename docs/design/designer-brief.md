# Designer brief: everything Forgeweave drew for itself

This is the work list. It names every visual asset in Forgeweave whose pixels did **not** come from Tinkers' Construct, plus the things that exist in the game with no art of their own yet. If an asset is on this list, a designer owns it.

It is a companion to [docs/texture-manifest.md](../texture-manifest.md), the art guide, which explains *how* to draw for this mod: canvas sizes, the greyscale tint rule, how a tool is assembled from layers, which scripts composite what, and the delivery checklist. This document is the *what* and the *in what order*. Read the rules below, then use the manifest when you sit down to draw.

Audited at commit [`c733cfd8`](https://github.com/gkissel/forgeweave/tree/c733cfd890ed3188ccb29003ee928614a635364f). Every path link in this document is pinned to that commit, so it opens the file exactly as audited even after the art changes.

## The rules, in plain words

**Sprites are 16x16.** That is the standard and it is not moving. A batch of 32x32 assembled-tool renders was tried in 2026 and thrown out; whatever resolution you work at, deliver 16x16. The exceptions are not sprites at all: worn armor sheets are 64x32, station GUI panels run 176x166 or 256x256, and the molten metal strips are tall animated films. Each exception is called out where it appears below.

**Forged is what you draw.** Forgeweave ships two art sets. Forged is the default set, the art every player sees, and it is yours. Legacy is a built-in resource pack, off unless a player turns it on, that preserves the look Forgeweave had before the art rewrite started.

**Nothing you make goes into Legacy.** The Legacy pack carries art that came from Tinkers' Construct and nothing else. New things Forgeweave invented, the material forms such as plates and gears included, exist in Forged only. If you draw it, it ships at the normal path and never gets a Legacy copy. (Maintainer, 2026-09-18.)

**Delivering a finished sprite**, in the order it happens:

1. You hand over the PNG: RGBA, right size, right name. Pure greyscale if it is a tool part, a tool layer, or a worn armor sheet, because those get multiplied by the material's colour at runtime. Full colour for everything else.
2. A developer drops it at its normal path, replacing what was there.
3. If the file it replaced came from Tinkers' Construct, the developer copies the old file into the Legacy pack at the same relative path. If it came from Forgeweave, the old file is simply deleted.
4. The developer reruns the four generator scripts, so every pattern, cast, clay cast and broken-tool variant built from your sprite gets rebuilt in both sets.
5. Tests and datagen run, and the file is committed.

You do steps 1 only. Sections 4 and 7 of the art guide spell out the rest if you want to read it.

**Greyscale and tinting.** Where an entry below says "tinted", the sprite is drawn in pure grey (R = G = B on every pixel) and the game multiplies it by the material's colour. Lightness is your only tool: a white pixel comes out as the material's raw colour, a mid-grey pixel at half intensity, a black pixel stays black. Use the full range, because your contrast becomes the finished piece's contrast. Where an entry says "not tinted", paint in whatever colours you like.

**References.** Every entry links to the file as it ships today, so you can open the placeholder in a browser and see what you are replacing. Vanilla Minecraft textures are fine as a style anchor. Tinkers' Construct art is not a reference for anything here: the whole point of Forged is that it is Forgeweave's own.

**Priority** means how often the maintainer sees the asset while playing:

- **Every session** — on screen constantly. Fix these first.
- **Sometimes** — seen in normal play, not every minute.
- **Rare** — deep progression, an optional mod pairing, or a corner case.

<!-- SECTIONS PENDING -->
