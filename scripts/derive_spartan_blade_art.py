#!/usr/bin/env python3
"""Derives the katana and scimitar blade art from Spartan Weaponry (issue #375).

Maintainer decision (issue #375): the katana's freshly-authored blade (#279) and the scimitar's
1.12-cutlass blade (#279) are both replaced by blades derived from **Spartan Weaponry**, a
separately-licensed mod. This is the first non-MIT-derived art in the tree, so it carries its own
licence file (`licenses/APACHE-2.0-SpartanWeaponry.txt`) beside the NOTICE.md rows the other
derived art uses; see the "Modification notice" section there, which Apache-2.0 section 4(b)
requires because the files below are modified, not copied.

Source: https://github.com/ObliviousSpartan/SpartanWeaponry branch `1.12.2`, pinned commit
`af87ea162cc043f1ea4236e5da5e723c600001ed`, Apache License 2.0 (verified verbatim against
https://www.apache.org/licenses/LICENSE-2.0.txt, no asset carve-out).

Spartan Weaponry has **no scimitar** -- verified by grepping every branch of the repository, whose
only curved single-edged sword is the *saber*. Issue #375's maintainer decision signs off on the
saber as the donor for `curved_blade` explicitly rather than substituting it silently; the scimitar
was already wearing another mod's non-scimitar (1.12's unregistered cutlass) before this.

## Why layer 1, and only layer 1

Spartan Weaponry draws its material-agnostic weapons as three files under
`textures/items/custom/<weapon>_layer_<n>.png`, and those layers split by *render role*, not by tool
part -- there is no blade/guard/handle split upstream to port:

| upstream layer | what it is | imported? |
| --- | --- | --- |
| `_layer_0` | the grip wrap, in fixed colours (84/158/198 browns) | no -- never tinted upstream |
| `_layer_1` | the whole weapon body in greyscale, the layer the material colour multiplies | **yes** |
| `_layer_2` | a pure-white gloss drawn over the blade | no -- Forgeweave has no untinted part layer |

Only `_layer_1` matches what a Forgeweave part texture is: greyscale pixels that
`ForgeweaveItemColors` multiplies by the part's material colour. Importing `_layer_2` would bake an
untinted highlight into a tinted layer (the gloss would take the material's colour and flatten the
blade into a solid bright band), and `_layer_0` is fixed-colour art that would fight the ramp
outright. Both are deliberately left behind -- see the licence file's modification notice.

## The three modifications

1. **Region extraction.** `_layer_1` holds the whole weapon; only the blade run is taken. The guard,
   grip and pommel pixels are dropped, because Forgeweave splits those into their own separately
   tinted parts (`hand_guard`/`tool_handle` for the katana, `cross_guard`/`tool_handle` for the
   scimitar) whose art this script does not touch. `BLADE_SPANS` below records the exact
   `(first_x, last_x)` run kept per row, read off the source sprites; every other pixel is dropped.
2. **Greyscale re-quantisation.** Spartan Weaponry's ramp is 40/68/107/150/193/216; Forgeweave's is
   68/160/196/219/251 (issue #356), shared by every tool sprite in the pack because a layer that
   strays off it tints differently from the rest of the tool. `RAMP` below is the monotone map
   between them. Its one collapse is 40 and 68 -> 68: upstream uses two distinct dark tones for
   outlines (the saber's spine outline is 68, its cutting-edge outline 40) and 68 is the darkest
   value Forgeweave's ramp has, so both land on it.
3. **Canvas fit.** None needed -- upstream already draws these weapons on the same 16x16 canvas in
   the same top-right diagonal "tool position" Forgeweave's blades use, so the extracted run is
   written at its source coordinates. The part icon and the tool's head layer are the same pixels,
   the one-file-serves-both convention `pickaxe_head.png` uses.

Usage: python3 scripts/derive_spartan_blade_art.py
Then re-run, in order, `scripts/derive_broken_art.py` (chips the head layers this writes),
`scripts/generate_pattern_textures.py` and `scripts/generate_cast_textures.py`, all three of which
read the art below off disk.
Requires Pillow (`pip install pillow`), and the Spartan Weaponry clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = (
    Path.home()
    / "development/minecraft/references/spartan-weaponry-1.12.2"
    / "src/main/resources/assets/spartanweaponry/textures/items/custom"
)

DERIVED_ITEM = ROOT / "src/main/resources/assets/forgeweave/textures/derived/item"
DERIVED_TOOLS = ROOT / "src/main/resources/assets/forgeweave/textures/derived/tools"

# Spartan Weaponry greyscale level -> Forgeweave ramp level. Monotone; see modification 2 above.
RAMP = {40: 68, 68: 68, 107: 160, 150: 196, 193: 219, 216: 251}

# row -> (first_x, last_x) inclusive of the blade run kept from `<weapon>_layer_1.png`.
# Read off the source sprites; everything outside these runs is guard, grip or pommel.
BLADE_SPANS = {
    # katana_layer_1: rows 1-9 are the blade. Row 9 also carries the tsuba's 40/150 pixels at
    # x3-x5, which the span excludes; rows 10-12 are the rest of the tsuba, 14-15 the pommel.
    "katana": {
        1: (12, 15),
        2: (11, 15),
        3: (10, 14),
        4: (9, 12),
        5: (8, 11),
        6: (7, 10),
        7: (6, 9),
        8: (5, 8),
        9: (6, 7),
    },
    # saber_layer_1: rows 1-9 are the blade. Row 9 also carries an isolated knuckle-bow pixel at
    # x2, which the span excludes; rows 10-13 are the guard and bow, 14-15 the pommel. Unlike the
    # katana the saber outlines its cutting edge in 40 and its spine in 68, so a luminance rule
    # cannot separate blade from guard here -- hence explicit spans for both weapons.
    "saber": {
        1: (13, 14),
        2: (11, 15),
        3: (10, 14),
        4: (9, 13),
        5: (8, 12),
        6: (7, 10),
        7: (6, 9),
        8: (5, 8),
        9: (4, 7),
    },
}

# upstream weapon -> the Forgeweave part icon and tool head layer its blade becomes.
OUTPUTS = {
    "katana": (DERIVED_ITEM / "katana_blade.png", DERIVED_TOOLS / "katana_head.png"),
    "saber": (DERIVED_ITEM / "curved_blade.png", DERIVED_TOOLS / "scimitar_head.png"),
}

TRANSPARENT = (0, 0, 0, 0)


def blade(weapon: str) -> Image.Image:
    """`<weapon>_layer_1.png`'s blade run, re-quantised onto Forgeweave's ramp."""
    source = Image.open(UPSTREAM / f"{weapon}_layer_1.png").convert("RGBA")
    width, height = source.size
    if (width, height) != (16, 16):
        raise SystemExit(f"{weapon}_layer_1.png is {width}x{height}, expected 16x16")
    source_px = source.load()

    out = Image.new("RGBA", (width, height), TRANSPARENT)
    out_px = out.load()
    for y, (first_x, last_x) in BLADE_SPANS[weapon].items():
        for x in range(first_x, last_x + 1):
            r, g, b, a = source_px[x, y]
            if a == 0:
                raise SystemExit(f"{weapon} span row {y} x{x} is transparent upstream -- stale span")
            if not r == g == b:
                raise SystemExit(f"{weapon} row {y} x{x} is not greyscale upstream: {(r, g, b)}")
            if r not in RAMP:
                raise SystemExit(f"{weapon} row {y} x{x} has level {r}, absent from RAMP")
            level = RAMP[r]
            out_px[x, y] = (level, level, level, 255)
    return out


def main() -> None:
    if not UPSTREAM.is_dir():
        raise SystemExit(
            f"Spartan Weaponry clone not found at {UPSTREAM} -- see CLAUDE.md for how to re-create it"
        )

    for weapon, paths in OUTPUTS.items():
        image = blade(weapon)
        for path in paths:
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
            print(f"wrote {path}")


if __name__ == "__main__":
    main()
