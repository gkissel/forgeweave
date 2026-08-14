#!/usr/bin/env python3
"""Derives the dagger's art from the 1.20 clone's own dagger (issue #278).

The dagger has no 1.12 counterpart (`derive_m3_weapon_art.py`'s docstring), so its placeholder art
so far has reused unrelated 1.12 sprites: `dagger_head.png`/`knife_blade.png` were 1.12's
`items/parts/knife_blade.png` -- which upstream 1.12 actually registers for the Shuriken, not any
blade tool (`TinkerTools#knifeBlade`, consumed only by `Shuriken.java`) -- and `dagger_handle.png`
reused the broadsword's handle. The 1.20 branch, unlike 1.12, ships a real dagger
(`tool_definitions/dagger.json`: parts `small_blade` + `tool_handle`, matching Forgeweave's own
`ToolConstants#DAGGER` composition exactly), so per CLAUDE.md's upstream-reference table this is the
sanctioned art source and every remaining reused/mismatched sprite is replaced with 1.20's own.

All three outputs are straight copies -- no reshaping needed, upstream's dagger already matches
Forgeweave's two-part (head/handle) composition:

| Output | Upstream source | Why |
| --- | --- | --- |
| `derived/tools/dagger_head.png` | `textures/item/tool/dagger/blade.png` | the dagger model's own `head` layer texture (`models/item/tool/dagger/display.json`'s parent, `textures: {"head": ".../dagger/blade"}`) |
| `derived/tools/dagger_handle.png` | `textures/item/tool/dagger/crossguard.png` | the dagger model's `crossguard` layer -- Forgeweave has no separate EXTRA/crossguard part slot (`ToolConstants#DAGGER` is HEAD+HANDLE only), so this is the texture that lands on the single `TOOL_HANDLE` layer, the same one-handle-layer-covers-both-visually shape 1.20 itself uses |
| `derived/item/knife_blade.png` | `textures/item/tool/parts/small_blade.png` | the standalone part-builder icon for `small_blade`, the part `ToolConstants#DAGGER`'s HEAD slot maps to Forgeweave's `knife_blade` part id; distinct file from `dagger/blade.png` upstream too (the tool layer only shows the portion of the blade that clears the handle, the part icon shows the whole blade) |

`derived/item/cast_knife_blade.png` and `derived/item/pattern_knife_blade.png` are downstream
composites of `knife_blade.png` -- regenerate both with `generate_cast_textures.py` and
`generate_pattern_textures.py` after running this script.

Usage: python3 scripts/derive_dagger_art.py
Requires Pillow (`pip install pillow`), and the 1.20 clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM_1_20 = Path.home() / "development/minecraft/references/tinkers-1.20/src/main/resources/assets/tconstruct/textures/item/tool"

ASSETS = ROOT / "src/main/resources/assets/forgeweave/textures"
DERIVED_ITEM = ASSETS / "derived/item"
DERIVED_TOOLS = ASSETS / "derived/tools"


def load(*relative: str) -> Image.Image:
    return Image.open(UPSTREAM_1_20.joinpath(*relative)).convert("RGBA")


def main() -> None:
    if not UPSTREAM_1_20.is_dir():
        raise SystemExit(f"1.20 clone not found at {UPSTREAM_1_20} -- see CLAUDE.md for how to re-create it")

    outputs = {
        DERIVED_TOOLS / "dagger_head.png": load("dagger/blade.png"),
        DERIVED_TOOLS / "dagger_handle.png": load("dagger/crossguard.png"),
        DERIVED_ITEM / "knife_blade.png": load("parts/small_blade.png"),
    }

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
