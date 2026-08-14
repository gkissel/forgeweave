#!/usr/bin/env python3
"""Generates the katana's freshly-authored guard and grip art (issues #160/#198/#279/#375).

This script used to generate the katana's blade as well. Issue #375's maintainer decision re-sourced
that blade from Spartan Weaponry (Apache-2.0), so it now comes out of
`scripts/derive_spartan_blade_art.py` into the `derived/` tree, and what is left here is the two
layers Spartan Weaponry has nothing to donate for.

That is not a shortcut: Spartan Weaponry splits its weapon art by *render role* -- a fixed-colour
grip layer, one fused greyscale body layer, an untinted white gloss -- not by tool part, so there
is no guard or grip sprite to port. Its tsuba is three pixels fused into the body layer, far too
small to read as a standalone `hand_guard` part icon in the Part Builder, and its grip is drawn in
fixed browns that would fight `ForgeweaveItemColors`' per-material tint instead of taking it. Both
layers below therefore stay **authored, not derived**: they carry no NOTICE.md row and keep living
under `textures/tools/`, which `ToolArt#ORIGINAL_ART` (keyed per layer since #375) routes them to
and `assets/minecraft/atlases/blocks.json` stitches.

The two cues the art is built around, in the order a player reads them at 16x:

* EXTRA -- a compact plate straddling the hilt at right angles to the blade (the tsuba), in place
  of the swept European cross guard the longsword and broadsword share.
* HANDLE -- a long grip banded light/dark down its length, reading as a wrapped tsuka. Upstream's
  own `broadsword/handle.png` already alternates 251/219 for the same reason; this widens the
  contrast to 251/160 so the banding survives being tinted by a dark handle material.

Both were authored for #279 against that issue's blade and are kept unchanged by #375: the Spartan
Weaponry blade lands one row lower and one column right of the one it replaces, which the tsuba
still straddles and the grip still meets, so re-cutting them would have churned the art without
improving the join.

Greyscale values are the 68/160/196/219/251 ramp every tool sprite in the pack uses -- shared
because `ForgeweaveItemColors` multiplies each layer by its part's material colour, so a layer that
strays off the ramp tints differently from the rest of the tool. A greyscale ramp is not derived
material; the shapes below are original.

Usage: python3 scripts/generate_katana_art.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures"

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (68, 68, 68, 255),   # outline
    "d": (160, 160, 160, 255),  # deepest shade -- the blade's spine, the grip's dark band
    "m": (196, 196, 196, 255),
    "l": (219, 219, 219, 255),
    "w": (251, 251, 251, 255),  # highlight -- the blade's cutting edge, the grip's light band
}

# Tsuba: a plate across the hilt at right angles to the blade, not a swept cross guard.
TSUBA = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..oo............",
    "..olmo..........",
    "...olmo.........",
    "....olmo........",
    ".....oo.........",
    "................",
    "................",
    "................",
    "................",
]

# Tsuka: banded light/dark down its length so it reads as wrapped.
GRIP = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....ow.........",
    "....olo.........",
    "...owo..........",
    "..odo...........",
    ".owo............",
    "odo.............",
    "oo..............",
]


def write(rows: list[str], path: Path) -> None:
    image = Image.new("RGBA", (16, 16), PALETTE["."])
    pixels = image.load()
    for y, row in enumerate(rows):
        if len(row) != 16:
            raise ValueError(f"{path.name} row {y} is {len(row)} wide, expected 16")
        for x, char in enumerate(row):
            pixels[x, y] = PALETTE[char]
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print(f"wrote {path}")


def main() -> None:
    write(TSUBA, ASSETS / "tools/katana_binding.png")
    write(GRIP, ASSETS / "tools/katana_handle.png")


if __name__ == "__main__":
    main()
