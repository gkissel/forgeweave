"""Draws the weldheart, the catalyst a Draconic Evolution fusion craft consumes to make one of the
three fusion metals (issue #946).

Original procedural art, drawn from nothing: no vanilla asset, no upstream clone, so no NOTICE.md
row and no client jar needed to run this (unlike the Track B sprite scripts, which recolor vanilla
donors). The shape is three nested diamonds, one per fusion tier, in the three metals' own colors
from dev.gkissel.forgeweave.trackb.TrackBAlloy -- emberweld on the outside, starweld inside it,
voidweld at the core -- so the item reads as "the thing all three tiers come out of". Corners stay
transparent, the same 16x16 sprite budget every other Forgeweave item icon uses.

Usage: python3 scripts/generate_weldheart_texture.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

ITEM_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/item"

SIZE = 16
CENTRE = (SIZE - 1) / 2

# TrackBAlloy's own hexes, outermost tier first. Each ring is (max Manhattan radius, fill, edge).
RINGS = [
    (7, (0xFF, 0x5A, 0x4A), (0xB8, 0x2E, 0x22)),   # emberweld
    (5, (0x28, 0x32, 0xD2), (0x16, 0x1C, 0x8C)),   # starweld
    (2, (0x8A, 0x2B, 0xE2), (0xE3, 0xBF, 0xFF)),   # voidweld, lit from the middle
]


def main() -> None:
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()

    for x in range(SIZE):
        for y in range(SIZE):
            distance = abs(x - CENTRE) + abs(y - CENTRE)
            # Innermost first, so a smaller diamond paints over the one that contains it.
            for radius, fill, edge in reversed(RINGS):
                if distance <= radius:
                    # The outer shell of each diamond takes the darker edge color, which is what
                    # gives the sprite its facets without any shading pass.
                    pixels[x, y] = (*(edge if distance > radius - 1.5 else fill), 255)
                    break

    image.save(ITEM_DIR / "weldheart.png")
    print(f"wrote {ITEM_DIR / 'weldheart.png'}")


if __name__ == "__main__":
    main()
