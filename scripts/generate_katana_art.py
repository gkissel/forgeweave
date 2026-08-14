#!/usr/bin/env python3
"""Generates the katana's freshly-authored blade, guard and grip art (issues #160/#198/#279).

Neither pinned clone ships a katana, and issue #198's "derive it from the closest upstream shape"
decision resolved that by copying 1.12's `parts/large_sword_blade.png` unchanged. Playtest 0.3.2
(issue #279) rejected the result: the copied blade is the same wide, straight, double-edged diamond
the cleaver and broadsword wear, so the assembled katana was not recognisable as one in-game. Every
purely geometric rework of an upstream sprite that was tried for #279 -- narrowing the cutlass blade
per row, extending the longsword blade toward its base, splicing one sprite's tip onto another --
either fell apart into disconnected pixels or landed back on the longsword's exact silhouette.

So the katana goes back to freshly-authored art, which is where it sat before #198 and which
CLAUDE.md's parity directive explicitly leaves open ("never freshly-authored approximations *when
upstream art exists*" -- here none does). Being authored, all four files live outside the
`derived/` tree and carry no NOTICE.md row: the three tool layers under `textures/tools/` (which
`ToolArt#ORIGINAL_ART` routes the katana to, and `assets/minecraft/atlases/blocks.json` stitches)
and the part icon under `textures/item/`. `pattern_katana_blade.png` is a separate matter -- that
composite still runs the upstream-derived imprint algorithm over the derived pattern base, so it
stays under `derived/` with its NOTICE.md row (scripts/generate_pattern_textures.py).

The three cues the art is built around, in the order a player reads them at 16x:

* HEAD -- a two-pixel blade, dark spine up-left and bright edge down-right, so the blade is visibly
  single-edged rather than the symmetric diamond every other sword in the roster wears. It is
  capped by a flat chisel tip (the kissaki) instead of a needle point, which is what separates its
  silhouette from the longsword's at a glance.
* EXTRA -- a compact plate straddling the hilt at right angles to the blade (the tsuba), in place
  of the swept European cross guard the longsword and broadsword share.
* HANDLE -- a long grip banded light/dark down its length, reading as a wrapped tsuka. Upstream's
  own `broadsword/handle.png` already alternates 251/219 for the same reason; this widens the
  contrast to 251/160 so the banding survives being tinted by a dark handle material.

Greyscale values are the 68/160/196/219/251 ramp every tool sprite in the pack uses -- shared
because `ForgeweaveItemColors` multiplies each layer by its part's material colour, so a layer that
strays off the ramp tints differently from the rest of the tool. A greyscale ramp is not derived
material; the shapes below are original.

Usage: python3 scripts/generate_katana_art.py
Then re-run `scripts/derive_broken_art.py`, which chips `tools/katana_head_broken.png` out of the
head layer this writes (issue #284) and so goes stale whenever the blade below changes.
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

# Single-edged blade: spine `d` up-left, edge `w` down-right, flat chisel kissaki on row 0.
BLADE = [
    "............ooo.",
    "...........odwo.",
    "..........odwo..",
    ".........odwo...",
    "........odwo....",
    ".......odwo.....",
    "......odwo......",
    ".....odwo.......",
    "....odwo........",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

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
    write(BLADE, ASSETS / "tools/katana_head.png")
    write(TSUBA, ASSETS / "tools/katana_binding.png")
    write(GRIP, ASSETS / "tools/katana_handle.png")
    # The part icon is the blade layer's own pixels, the one-file-serves-both convention
    # `pickaxe_head.png` uses -- but under `item/`, since authored art stays out of `derived/`.
    write(BLADE, ASSETS / "item/katana_blade.png")


if __name__ == "__main__":
    main()
