"""Derives the attack-slash particle sprites (issue #584, parity audit T51) from the pinned 1.12 clone.

Upstream ships one sheet per weapon shape under `textures/particle/`, each a 4x2 grid of eight
animation phases that `library/client/particle/ParticleAttack#renderParticle` steps through by hand:
it computes `i = (int) ((life + partialTicks) / lifeTime * 8)` and derives that cell's UV rectangle
every frame. 1.21 has no manual UV stepping -- a particle animates from a *list* of atlas sprites
(`assets/<ns>/particles/<id>.json`), so each phase has to be its own file.

So every sheet is chipped into its eight cells, pixel for pixel, in upstream's own index order
(`i % 4` across, `i / 4` down). Cells are not square on two of the sheets (the cleaver's and the
rapier's are 16x32); that is upstream's art and upstream squashes it into a square quad exactly the
same way, so the cells are copied at their native size rather than padded.

Destination is `textures/particle/derived/` rather than the repo's usual `textures/derived/<kind>/`:
the particle atlas resolves a sprite name strictly under `textures/particle/`, so a derived particle
sprite can only carry the `derived` marker below that root (same call as issue #482's hearts). One
NOTICE.md row per output file.

The longsword is deliberately absent: upstream's `ParticleAttackLongsword` points at vanilla's own
`textures/entity/sweep.png`, which on 1.21 is already the `minecraft:sweep_0..7` particle sprites --
so `assets/forgeweave/particles/slash_longsword.json` names those directly and derives nothing.

Usage: python3 scripts/derive_slash_art.py
"""
from pathlib import Path

from PIL import Image

UPSTREAM = (Path.home()
            / "development/minecraft/references/tinkers-1.12"
            / "resources/assets/tconstruct/textures/particle")
DEST = (Path(__file__).resolve().parent.parent
        / "src/main/resources/assets/forgeweave/textures/particle/derived")

# ParticleAttack#init: animPhases = 8 laid out animPerRow = 4 across.
PHASES = 8
PER_ROW = 4

# Upstream sheet -> the weapons that bind it (ParticleAttack* subclasses). The axe sheet is shared
# by the hatchet and the lumber axe, which differ only in size/lifeTime, so it is chipped once.
SHEETS = ("slash_axe", "slash_cleaver", "slash_frypan", "slash_hammer", "slash_rapier")


def main() -> None:
    DEST.mkdir(parents=True, exist_ok=True)
    for name in SHEETS:
        sheet = Image.open(UPSTREAM / f"{name}.png").convert("RGBA")
        rows = -(-PHASES // PER_ROW)
        cell_w = sheet.width // PER_ROW
        cell_h = sheet.height // rows
        for phase in range(PHASES):
            x = (phase % PER_ROW) * cell_w
            y = (phase // PER_ROW) * cell_h
            cell = sheet.crop((x, y, x + cell_w, y + cell_h))
            if not any(pixel[3] for pixel in cell.getdata()):
                raise SystemExit(f"{name} phase {phase}: cell at ({x}, {y}) is empty -- wrong grid?")
            out = DEST / f"{name}_{phase}.png"
            cell.save(out)
            print(f"chipped {name}.png phase {phase} ({x}, {y}) {cell_w}x{cell_h} -> {out.name}")


if __name__ == "__main__":
    main()
