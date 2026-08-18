"""Derives the heart-effect particle sprites (issue #482, parity audit T51) from the pinned 1.12 clone.

Upstream keeps all five in one 128x128 sheet, `textures/particle/particles.png`, indexed the way a
vanilla 1.12 particle indexes the vanilla particle sheet: 16 columns of 8x8 cells, and
`ParticleEffect.Type` names the pixel offset of each cell (`HEART_FIRE(0, 0)`, `HEART_CACTUS(8, 0)`,
`HEART_ELECTRO(16, 0)`, `HEART_BLOOD(24, 0)`, `HEART_ARMOR(32, 0)`), which its constructor divides by
8 back into a column index. 1.21 has no such indexing: every particle sprite is its own file, stitched
into the particle atlas from `assets/<ns>/particles/<id>.json`. So each cell is chipped out into its
own 8x8 PNG, pixel for pixel.

Destination is `textures/particle/derived/` rather than the repo's usual `textures/derived/<kind>/`:
the particle atlas resolves a sprite name strictly under `textures/particle/`, so a derived particle
sprite can only carry the `derived` marker below that root. One NOTICE.md row per output file.

Usage: python3 scripts/derive_particle_art.py
"""
from pathlib import Path

from PIL import Image

UPSTREAM = (Path.home()
            / "development/minecraft/references/tinkers-1.12"
            / "resources/assets/tconstruct/textures/particle/particles.png")
DEST = (Path(__file__).resolve().parent.parent
        / "src/main/resources/assets/forgeweave/textures/particle/derived")

CELL = 8

# name -> upstream ParticleEffect.Type pixel offset (x, y)
HEARTS = {
    "heart_fire": (0, 0),
    "heart_cactus": (8, 0),
    "heart_electro": (16, 0),
    "heart_blood": (24, 0),
    "heart_armor": (32, 0),
}


def main() -> None:
    sheet = Image.open(UPSTREAM).convert("RGBA")
    DEST.mkdir(parents=True, exist_ok=True)
    for name, (x, y) in HEARTS.items():
        cell = sheet.crop((x, y, x + CELL, y + CELL))
        if not any(pixel[3] for pixel in cell.getdata()):
            raise SystemExit(f"{name}: upstream cell at ({x}, {y}) is empty -- wrong offset?")
        out = DEST / f"{name}.png"
        cell.save(out)
        print(f"chipped {UPSTREAM.name} ({x}, {y}) {CELL}x{CELL} -> {out}")


if __name__ == "__main__":
    main()
