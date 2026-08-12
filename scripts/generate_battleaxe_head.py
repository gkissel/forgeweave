"""Copies the battleaxe's two head layers from upstream (issue #159).

Upstream 1.12's unshipped battleaxe model (`models/item/tools/battleaxe.tcon.json`) is four layers:
handle, backhead, fronthead, binding -- two head layers, because upstream's battleaxe takes two
independently-materialled `broadAxeHead` parts. Forgeweave assembles it the same way: since #155's
generalized N-part assembly the Tool Station gives every part slot its own input slot, so
`ToolConstants.BATTLEAXE`'s four parts are four slots and the two heads can differ in material.

That is why this script only copies. #159 originally merged the two head layers into one, because
the pre-#155 station was fixed at three input slots and both heads therefore always shared a
material; with four real slots there is a second material for the second layer to express, and
merging them would throw it away.

Layer names follow `ToolArt#layers`: the second slot of a repeated role takes a numeric suffix, so
upstream's `backhead`/`fronthead` land as `battleaxe_head.png`/`battleaxe_head2.png`.
`handle.png` and `binding.png` are straight copies too. All four carry a NOTICE.md row.

Usage: python3 scripts/generate_battleaxe_head.py
Requires the pinned 1.12 clone (see CLAUDE.md).
"""
import shutil
from pathlib import Path

CLONE = Path.home() / "development/minecraft/references/tinkers-1.12"
UPSTREAM = CLONE / "resources/assets/tconstruct/textures/items/battleaxe"
OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/assets/forgeweave/textures/derived/tools"
)

# upstream file -> Forgeweave layer, in battleaxe.tcon.json's own layer0..layer3 order.
LAYERS = {
    "handle": "battleaxe_handle",
    "backhead": "battleaxe_head",
    "fronthead": "battleaxe_head2",
    "binding": "battleaxe_binding",
}


def main() -> None:
    if not UPSTREAM.is_dir():
        raise SystemExit(f"missing 1.12 clone at {CLONE} -- see CLAUDE.md for how to re-create it")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for source, target in LAYERS.items():
        destination = OUTPUT / f"{target}.png"
        shutil.copyfile(UPSTREAM / f"{source}.png", destination)
        print(f"wrote {destination}")


if __name__ == "__main__":
    main()
