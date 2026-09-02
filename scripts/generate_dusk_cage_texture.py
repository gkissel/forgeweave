"""Draws the Dusk Cage's 16x16 item sprite (issue #886).

Original art: the cage is Forgeweave's own item, so there is nothing upstream to derive from and no
NOTICE.md row to write. Procedural rather than hand-painted for the same reason the Track B ore icons
are (scripts/generate_track_b_ore_textures.py): a handful of shapes and a fixed palette, regenerable.

The shape is a murkiron-coloured lantern-cage -- a lid, a hoop handle, four vertical bars over a
smoky void, and a base -- with the palette taken from murkiron's own material colour (#3A5C56) in
data/forgeweave/forgeweave/material/murkiron.json, so it reads as murkiron's item on sight.

Usage: python3 scripts/generate_dusk_cage_texture.py
"""
from pathlib import Path

from PIL import Image

OUT = (Path(__file__).resolve().parent.parent
       / "src/main/resources/assets/forgeweave/textures/item/dusk_cage.png")

FRAME = (0x3A, 0x5C, 0x56, 255)       # murkiron
FRAME_LIT = (0x58, 0x82, 0x79, 255)   # its highlight
FRAME_DARK = (0x22, 0x38, 0x35, 255)  # its shadow
VOID = (0x0D, 0x12, 0x16, 255)        # the dusk inside
VOID_LIT = (0x1B, 0x26, 0x2E, 255)


def main() -> None:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = colour

    # Hoop handle.
    for x in (6, 9):
        fill(x, 1, x, 2, FRAME)
    fill(7, 0, 8, 0, FRAME_LIT)

    # Lid and base.
    fill(3, 3, 12, 4, FRAME)
    fill(3, 3, 12, 3, FRAME_LIT)
    fill(3, 13, 12, 14, FRAME)
    fill(3, 14, 12, 14, FRAME_DARK)

    # The dusk between them, lighter towards the top-left the way every vanilla sprite is lit.
    fill(4, 5, 11, 12, VOID)
    fill(4, 5, 11, 6, VOID_LIT)

    # Side walls and the two inner bars.
    for x in (3, 12):
        fill(x, 5, x, 12, FRAME)
    px[3, 5] = FRAME_LIT
    px[12, 12] = FRAME_DARK
    fill(6, 5, 6, 12, FRAME_LIT)
    fill(9, 5, 9, 12, FRAME)

    img.save(OUT)


if __name__ == "__main__":
    main()
