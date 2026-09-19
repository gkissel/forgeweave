"""Rebuilds the battleaxe's four upstream layers into the Legacy pack only (issue #159, revised by
#1049).

Upstream 1.12's unshipped battleaxe model (`models/item/tools/battleaxe.tcon.json`) is four layers:
handle, backhead, fronthead, binding -- two head layers, because upstream's battleaxe takes two
independently-materialled `broadAxeHead` parts. Forgeweave assembles it the same way: since #155's
generalized N-part assembly the Tool Station gives every part slot its own input slot, so
`ToolConstants.BATTLEAXE`'s four parts are four slots and the two heads can differ in material.

Layer names follow `ToolArt#layers`: the second slot of a repeated role takes a numeric suffix, so
upstream's `backhead`/`fronthead` land as `battleaxe_head.png`/`battleaxe_head2.png`.

**Issue #1049.** The battleaxe is Tinkers'-native (its art is real upstream 1.12 pixels, just from an
unshipped model), so unlike the war mace or the katana it is allowed a Legacy-pack copy -- but a
Forged designer batch has since replaced `battleaxe_head.png`/`battleaxe_head2.png` at the default
path (third designer batch, 2026-09-06; their pre-Forged bytes now live under the Legacy pack,
`NOTICE.md` row moved with them, the same #796 treatment every other Forged swap gets), while
`battleaxe_handle.png`/`battleaxe_binding.png` are still the original upstream pixels at the default
path, not yet Forged. The old version of this script wrote straight to the default tree and would
have silently overwritten the Forged heads with upstream pixels on a rerun. It now goes through
`sprite_sets.save_legacy_if_different` instead, the same two-set-aware helper every other generator
script uses: a layer identical to what is already at the default path (handle, binding, today) writes
nothing, and a layer that differs from the Forged default (head, head2, today) writes the Legacy-pack
override -- so the *next* Forged swap of handle or binding is also captured automatically, with no
further edits to this script.

Usage: python3 scripts/generate_battleaxe_head.py
Requires the pinned 1.12 clone (see CLAUDE.md).
"""
from pathlib import Path

from PIL import Image

from sprite_sets import save_legacy_if_different

CLONE = Path.home() / "development/minecraft/references/tinkers-1.12"
UPSTREAM = CLONE / "resources/assets/tconstruct/textures/items/battleaxe"
SUBDIR = "derived/tools"

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
    for source, target in LAYERS.items():
        image = Image.open(UPSTREAM / f"{source}.png").convert("RGBA")
        filename = f"{target}.png"
        save_legacy_if_different(image, SUBDIR, filename)
        print(f"checked {SUBDIR}/{filename} against upstream {source}.png")


if __name__ == "__main__":
    main()
