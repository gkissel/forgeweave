#!/usr/bin/env python3
"""Derives the per-draw-stage bow layer art (M3.5 issue #400), byte-for-byte.

Upstream 1.12 draws a bow's pull in three stages: `models/item/tools/<bow>.tcon.json` carries three
`overrides` entries keyed on the vanilla `pulling`/`pull` item properties, and each one re-points a
subset of the model's `layer<N>` textures at a `_1`/`_2`/`_3` variant. Read straight off those JSONs
at the pinned commit:

| Bow | stage 1 (`pull >= 0`) | stage 2 | stage 3 |
| --- | --- | --- | --- |
| `shortbow` (`pull >= 0.65`, `>= 0.9`) | `bowstring_1` | `limb_top_2`, `limb_bottom_2`, `bowstring_2` | `limb_top_3`, `limb_bottom_3`, `bowstring_3` |
| `longbow` (`pull >= 0.65`, `>= 0.9`) | `bowstring_1` | `limb_top_2`, `limb_bottom_2`, `bowstring_2` | `limb_top_3`, `limb_bottom_3`, `bowstring_3` |
| `crossbow` (`pull >= 0.5`, `>= 0.999`) | `bowstring_1` | `limb_2`, `bowstring_2` | `limb_3`, `bowstring_3` |

So the rule, uniform across all three: the **string** layer has art from stage 1 (the string starts
moving the instant you draw), the **limb** layers only from stage 2 (the limbs do not visibly bend
until the draw is well along), and every other layer -- the longbow's grip, the crossbow's body and
binding -- keeps its undrawn art at every stage. `ToolArt#drawLayer` is the Forgeweave side of that
rule and `BowDrawModelTest` pins the two together against the generated models.

The crossbow's fourth override, `"loaded": 1`, points at the *stage 3* textures (`limb_3`,
`bowstring_3`) rather than at art of its own, so nothing extra is derived for it -- a loaded crossbow
is a crossbow held at full crank, which is exactly what stage 3 draws.

Naming: `<tool>_<layer>_draw<N>.png` beside the tool's own `<tool>_<layer>.png`, where `<layer>` is
the Forgeweave layer name (`ToolArt#layers`) rather than upstream's file stem -- `limb`/`limb2` for
`limb_top`/`limb_bottom`, `string` for `bowstring`, matching the base layers issue #394/#395 already
derived. Straight `shutil.copyfile`, no pixel is touched.

Not derived: upstream's `_cactus`/`_contrast`/`_paper` variants of every limb and grip texture.
Those are 1.12's `MaterialRenderInfo` mechanism -- a material whose look cannot be reached by tinting
one greyscale sprite ships a whole second sprite per layer, and `BakedToolModel` picks it by
material. Forgeweave tints (`ForgeweaveItemColors#toolMaterialTint`) and has no per-material sprite
path at all, for any tool; adding one for the bows alone is a rendering-architecture decision, not
part of this issue. Recorded as a deviation in the PR.

Also writes `build/bow_draw_notice_rows.md` with one NOTICE.md row per copied file.

Usage: python3 scripts/derive_bow_draw_art.py
Requires the 1.12 clone at the path CLAUDE.md pins.
"""
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM_1_12 = Path.home() / "development/minecraft/references/tinkers-1.12/resources/assets/tconstruct/textures/items"
UPSTREAM_COMMIT = "c01173c0408352c50a2e8c5017552323ce42f5b4"
OUT = ROOT / "src/main/resources/assets/forgeweave/textures/derived/tools"
NOTICE_ROWS = ROOT / "build/bow_draw_notice_rows.md"

# Forgeweave layer name -> upstream file stem, per bow. Mirrors the base layers issue #394/#395
# derived (`ToolArt#ROLE_LAYERS` applied to each bow's part list).
LAYER_SOURCES = {
    "shortbow": {"limb": "limb_top", "limb2": "limb_bottom", "string": "bowstring"},
    "longbow": {"limb": "limb_top", "limb2": "limb_bottom", "string": "bowstring"},
    "crossbow": {"limb": "limb", "string": "bowstring"},
}

# Which stages each layer has art for; see the module docstring's table.
STAGES = {"string": (1, 2, 3), "limb": (2, 3), "limb2": (2, 3)}

STAGE_NOTE = {
    1: "the string alone moves",
    2: "the limbs start to bend",
    3: "full draw",
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    NOTICE_ROWS.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    for tool, layers in LAYER_SOURCES.items():
        for layer, stem in layers.items():
            for stage in STAGES[layer]:
                source = UPSTREAM_1_12 / tool / f"{stem}_{stage}.png"
                if not source.is_file():
                    raise SystemExit(f"missing upstream draw-stage art: {source}")
                target = OUT / f"{tool}_{layer}_draw{stage}.png"
                shutil.copyfile(source, target)
                rows.append(
                    f"| `{target.relative_to(ROOT)}` (issue #400; `{tool}.tcon.json`'s "
                    f"`pull` stage {stage} override -- {STAGE_NOTE[stage]}) | "
                    f"`resources/assets/tconstruct/textures/items/{tool}/{stem}_{stage}.png` | "
                    f"`{UPSTREAM_COMMIT}` | MIT |"
                )
    NOTICE_ROWS.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"derived {len(rows)} bow draw-stage textures into {OUT}")
    print(f"NOTICE.md rows written to {NOTICE_ROWS}")


if __name__ == "__main__":
    main()
