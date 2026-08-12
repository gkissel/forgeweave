#!/usr/bin/env python3
"""Generate Forgeweave's original dagger item-layer sprites.

Hand-drawn, not derived from any upstream art. The masks below are the source
of truth: '.' transparent, 'o' outline, 'm' mid body, 'l' lit facet.

Shapes stay greyscale so the runtime material tint multiplies cleanly: the lit
facet sits toward the upper-left of each 45-degree run, the outline closes the
lower-right, matching the diagonal idiom the other tool layers use.
"""

from pathlib import Path

from PIL import Image

OUT_DIR = Path(__file__).resolve().parent.parent / (
    "src/main/resources/assets/forgeweave/textures/tools"
)

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (58, 58, 58, 255),
    "m": (150, 150, 150, 255),
    "l": (214, 214, 214, 255),
}

# Short blade, tip at the top-right corner, base meeting the guard at row 7.
HEAD = [
    ".............oo.",
    "............olo.",
    "...........olmo.",
    "..........olmo..",
    ".........olmo...",
    "........olmo....",
    ".......olmo.....",
    "......olmo......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

# Stubby hilt: a one-pixel guard flare at row 8, grip running down-left, pommel
# knob in the bottom-left corner.
HANDLE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....olmlo......",
    ".....olmo.......",
    "....olmo........",
    "...olmo.........",
    "..olmo..........",
    ".olmo...........",
    "olmmo...........",
    ".ooo............",
]


def render(mask):
    assert len(mask) == 16, f"expected 16 rows, got {len(mask)}"
    img = Image.new("RGBA", (16, 16), PALETTE["."])
    for y, row in enumerate(mask):
        assert len(row) == 16, f"row {y} is {len(row)} wide"
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, mask in (("dagger_head", HEAD), ("dagger_handle", HANDLE)):
        img = render(mask)
        opaque = sum(row.count(ch) for row in mask for ch in "olm")
        assert opaque > 20, f"{name} only has {opaque} visible pixels"
        path = OUT_DIR / f"{name}.png"
        img.save(path, optimize=True)
        print(f"{path} {img.size} {img.mode} opaque={opaque}")


if __name__ == "__main__":
    main()
