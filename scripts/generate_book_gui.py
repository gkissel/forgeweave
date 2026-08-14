"""Generates the guide book's GUI sheet (issue #273): `textures/gui/book.png`.

Freshly authored, NOT derived: the 1.12 book's chrome art (cover, page spread, page-turn arrows)
belongs to the separate Mantle library, which is not part of the pinned Tinkers' clone, so per the
repo's derived-texture rule this authors an original brown-leather-and-parchment look in the same
spirit instead. Region layout is mirrored by the constants in
`client/book/BookScreen.java` -- keep the two in sync:

  - two-page spread: (0, 0) 320x200
  - closed cover:    (322, 0) 130x180
  - arrows: prev (322, 182) 18x10, next (342, 182) 18x10; hover variants 12px below

Usage: python3 scripts/generate_book_gui.py  (requires Pillow)
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/gui/book.png"

LEATHER_DARK = (62, 39, 20, 255)
LEATHER = (89, 55, 28, 255)
LEATHER_LIGHT = (120, 76, 40, 255)
PARCHMENT = (238, 224, 187, 255)
PARCHMENT_SHADE = (216, 198, 158, 255)
PARCHMENT_DEEP = (196, 176, 136, 255)
GOLD = (196, 154, 62, 255)

SPREAD_W, SPREAD_H = 320, 200
COVER_X, COVER_W, COVER_H = 322, 130, 180
ARROW_W, ARROW_H = 18, 10


def mottle(draw: ImageDraw.ImageDraw, box, base, rng, density=0.06):
    """Sparse single-pixel speckles so the parchment doesn't read as one flat fill."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        for y in range(y0, y1):
            if rng.random() < density:
                delta = rng.choice((-8, -5, 5))
                draw.point((x, y), tuple(max(0, min(255, c + delta)) for c in base[:3]) + (255,))


def draw_page(draw: ImageDraw.ImageDraw, box, rng, spine_side):
    """One parchment page with a shaded outer edge and a deeper shade toward the spine."""
    x0, y0, x1, y1 = box
    draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=PARCHMENT)
    # edge shading ring
    draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=PARCHMENT_SHADE)
    draw.rectangle((x0 + 1, y0 + 1, x1 - 2, y1 - 2), outline=(228, 212, 172, 255))
    # spine-side gradient: three columns darkening toward the centre crease
    cols = range(3)
    for i in cols:
        shade = (PARCHMENT_DEEP, PARCHMENT_SHADE, (228, 212, 172, 255))[i]
        x = (x1 - 1 - i) if spine_side == "right" else (x0 + i)
        draw.line((x, y0 + 1, x, y1 - 2), fill=shade)
    mottle(draw, (x0 + 3, y0 + 3, x1 - 3, y1 - 3), PARCHMENT, rng)


def draw_spread(draw: ImageDraw.ImageDraw, rng):
    # leather backing with rounded-feel corners (notched pixels)
    draw.rectangle((0, 0, SPREAD_W - 1, SPREAD_H - 1), fill=LEATHER)
    draw.rectangle((0, 0, SPREAD_W - 1, SPREAD_H - 1), outline=LEATHER_DARK)
    draw.rectangle((1, 1, SPREAD_W - 2, SPREAD_H - 2), outline=LEATHER_LIGHT)
    for cx, cy in ((0, 0), (SPREAD_W - 1, 0), (0, SPREAD_H - 1), (SPREAD_W - 1, SPREAD_H - 1)):
        draw.point((cx, cy), (0, 0, 0, 0))
    # pages
    draw_page(draw, (8, 6, 158, 194), rng, spine_side="right")
    draw_page(draw, (162, 6, 312, 194), rng, spine_side="left")
    # centre spine crease
    draw.line((159, 4, 159, 195), fill=LEATHER_DARK)
    draw.line((160, 4, 160, 195), fill=LEATHER_DARK)


def draw_cover(image: Image.Image, draw: ImageDraw.ImageDraw, rng):
    x0, y0 = COVER_X, 0
    x1, y1 = COVER_X + COVER_W - 1, COVER_H - 1
    draw.rectangle((x0, y0, x1, y1), fill=LEATHER)
    draw.rectangle((x0, y0, x1, y1), outline=LEATHER_DARK)
    draw.rectangle((x0 + 1, y0 + 1, x1 - 1, y1 - 1), outline=LEATHER_LIGHT)
    for cx, cy in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        draw.point((cx, cy), (0, 0, 0, 0))
    # tooled inner frame with corner studs
    draw.rectangle((x0 + 7, y0 + 7, x1 - 7, y1 - 7), outline=LEATHER_DARK)
    draw.rectangle((x0 + 9, y0 + 9, x1 - 9, y1 - 9), outline=GOLD)
    for cx in (x0 + 5, x1 - 5):
        for cy in (y0 + 5, y1 - 5):
            draw.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=GOLD)
    # a small anvil-ish emblem block between title and subtitle areas
    ex, ey = x0 + COVER_W // 2, y0 + 132
    draw.rectangle((ex - 12, ey - 3, ex + 12, ey + 1), fill=GOLD)
    draw.rectangle((ex - 5, ey + 2, ex + 5, ey + 6), fill=GOLD)
    draw.rectangle((ex - 9, ey + 7, ex + 9, ey + 9), fill=GOLD)
    mottle(ImageDraw.Draw(image), (x0 + 2, y0 + 2, x1 - 2, y1 - 2), LEATHER, rng, density=0.03)


def draw_arrow(draw: ImageDraw.ImageDraw, x, y, direction, color, outline):
    """An 18x10 page-turn arrow: triangle head plus a short tail."""
    if direction == "next":
        head = [(x + 17, y + 5), (x + 9, y), (x + 9, y + 9)]
        tail = (x, y + 3, x + 9, y + 6)
    else:
        head = [(x, y + 5), (x + 8, y), (x + 8, y + 9)]
        tail = (x + 8, y + 3, x + 17, y + 6)
    draw.polygon(head, fill=color, outline=outline)
    draw.rectangle(tail, fill=color, outline=outline)


def main() -> None:
    rng = random.Random(273)
    image = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw_spread(draw, rng)
    draw_cover(image, draw, rng)
    arrow_leather = (74, 46, 22, 255)
    for i, direction in enumerate(("prev", "next")):
        x = 322 + i * 20
        draw_arrow(draw, x, 182, direction, arrow_leather, LEATHER_DARK)
        draw_arrow(draw, x, 194, direction, GOLD, LEATHER_DARK)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
