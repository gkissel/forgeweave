#!/usr/bin/env python3
"""Derives the per-modifier tool overlay textures from upstream 1.12 (issue #257), byte-for-byte.

Upstream 1.12 renders an applied modifier as an extra untinted layer on the tool item, from a
per-tool overlay texture `items/<tool>/mod_<modifier>.png` (composited by its
`BakedToolModel#addModifierQuads`, textures declared per tool in `models/item/modifiers/*.json`).
This script copies every such overlay for every Forgeweave tool x every Forgeweave modifier that has
an upstream counterpart into `textures/derived/tools/mods/<tool>_<modifier>.png` -- straight
`shutil.copyfile`, no pixel is touched.

M3.5 issue #400 added the drawn-stage variants: a bow's `<bow>.tcon.json` pull overrides each carry a
`modifier_suffix` of `1`/`2`/`3`, which `ToolModelLoader` turns into a lookup of the `<tool><N>`
texture key in every `models/item/modifiers/*.json` -- the base map is filled in first and the
staged keys overwrite it, so a modifier with no art for a stage keeps its undrawn overlay there.
Only the three bow folders ship any (`items/<bow>/mod_<x>_<N>.png`); every staged file that exists is
copied to `<tool>_<modifier>_draw<N>.png`, and `ModifierArt#STAGED_OVERLAYS` is the Java-side mirror
that `ModifierArtTest` pins to the files on disk in both directions.

Two mapping tables below carry the naming differences:

- `TOOL_SOURCES`: Forgeweave tool id -> upstream tool folder. Same-name for the sixteen tools that
  exist in 1.12 (`frying_pan` -> `frypan` is the one spelling difference). The five Forgeweave
  shapes with no 1.12 counterpart reuse the closest upstream tool's overlays, following the same
  donor choices issue #198 made for their base art (NOTICE.md): dagger/scimitar -> broadsword
  (their handles already reuse broadsword art; upstream's cutlass folder only carries 7 of the 17
  overlays, so it cannot be the donor), katana -> longsword (straight long blade), warmace ->
  hammer (its head derives from the hammer's), vein_hammer -> hammer (its base art comes from the
  1.20 clone, which has no per-tool overlay art at all -- 1.20 dropped that render path -- so the
  closest 1.12 tool donates).

- `MODIFIER_SOURCES`: Forgeweave modifier id -> upstream texture stem. Forgeweave modifiers with no
  upstream overlay art (the Forgeweave originals: searing, magnetic_pull, aquadynamic, resonant,
  far_reach, extra_slot, wind_burst, embossments) deliberately get NO overlay -- upstream itself
  ships overlay-less modifiers (e.g. its creative modifier), and a freshly-authored approximation
  is exactly what the 1.12-parity default forbids. Recorded per modifier in the issue #257 PR for
  maintainer review.

Also writes `build/modifier_overlay_notice_rows.md` with one NOTICE.md row per copied file.

Usage: python3 scripts/derive_modifier_overlays.py
Requires the 1.12 clone at the path CLAUDE.md pins.
"""
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM_1_12 = Path.home() / "development/minecraft/references/tinkers-1.12/resources/assets/tconstruct/textures/items"
UPSTREAM_COMMIT = "c01173c0408352c50a2e8c5017552323ce42f5b4"
OUT = ROOT / "src/main/resources/assets/forgeweave/textures/derived/tools/mods"
NOTICE_ROWS = ROOT / "build/modifier_overlay_notice_rows.md"

# Forgeweave tool id -> upstream 1.12 tool folder (see module docstring for the donor choices).
TOOL_SOURCES = {
    "pickaxe": "pickaxe",
    "shovel": "shovel",
    "hatchet": "hatchet",
    "mattock": "mattock",
    "kama": "kama",
    "broadsword": "broadsword",
    "longsword": "longsword",
    "rapier": "rapier",
    "battlesign": "battlesign",
    "frying_pan": "frypan",
    "battleaxe": "battleaxe",
    "cleaver": "cleaver",
    "hammer": "hammer",
    "excavator": "excavator",
    "lumberaxe": "lumberaxe",
    "scythe": "scythe",
    "shortbow": "shortbow",  # M3.5 #394; the drawn-stage overlays (mod_*_1/2/3) are M3.5-6's
    "longbow": "longbow",  # M3.5 #395
    "crossbow": "crossbow",  # M3.5 #395
    # Forgeweave-original shapes: closest-upstream donors, issue #198's precedent.
    "dagger": "broadsword",
    "scimitar": "broadsword",
    "katana": "longsword",
    "warmace": "hammer",
    "vein_hammer": "hammer",
}

# Forgeweave modifier id -> upstream texture stem (items/<tool>/<stem>.png).
MODIFIER_SOURCES = {
    "haste": "mod_haste",
    "sharpness": "mod_sharpness",
    "diamond": "mod_diamond",
    "emerald": "mod_emerald",
    "reinforced": "mod_reinforced",
    "silky": "mod_silk",
    "luck": "mod_luck",
    "mending_moss": "mod_mending_moss",
    "soulbound": "mod_soulbound",
    "smite": "mod_smite",
    "bane_of_arthropods": "mod_bane_spider",
    "fiery": "mod_fiery",
    "necrotic": "mod_necrotic",
    "knockback": "mod_knockback",
    "beheading": "mod_beheading",
    "shulking": "mod_shulking",
    "webbed": "mod_web",
    "glowing": "mod_glowing",
    "blasting": "mod_blasting",  # T24 (#455); harvest tools only -- see NO_UPSTREAM_ART below
    "fortification": "mod_fortified",  # T70 (#501); harvest tools only -- see NO_UPSTREAM_ART below
}

# (tool, modifier) pairs that get no overlay, on purpose. Three reasons, all mirrored by
# ModifierArt#NO_UPSTREAM_ART:
#
#   * luck refuses launchers (ModLuck.java:35), so items/shortbow/ and items/longbow/ have no
#     mod_luck.png. items/crossbow/ inconsistently does, and it is copied like any other.
#   * blasting and fortification (T70, #501) are both ModifierAspect.harvestOnly, so upstream ships
#     mod_blasting.png/mod_fortified.png in exactly the nine Category.HARVEST folders (pickaxe,
#     hammer, shovel, excavator, hatchet, mattock, kama, scythe, lumberaxe) and nowhere else.
#     Forgeweave's vein hammer is Category.HARVEST too and takes the hammer's donor copy; the
#     warmace is MELEE, so even though its hammer donor does have the art, the modifier can never
#     land on it and the file would be dead weight.
#   * fortification alone: items/mattock/ ships every other harvest-only modifier's overlay but
#     genuinely has no mod_fortified.png -- verified against the pinned commit, an upstream art gap
#     rather than a Forgeweave omission, so mattock is excluded for fortification only.
_NON_HARVEST_TOOLS = (
    "broadsword", "longsword", "rapier", "battlesign", "frying_pan", "battleaxe", "cleaver",
    "shortbow", "longbow", "crossbow", "dagger", "scimitar", "katana", "warmace")
NO_UPSTREAM_ART = {("shortbow", "luck"), ("longbow", "luck")} | {
    (tool, "blasting") for tool in _NON_HARVEST_TOOLS
} | {
    (tool, "fortification") for tool in _NON_HARVEST_TOOLS
} | {("mattock", "fortification")}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    NOTICE_ROWS.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    for tool, upstream_tool in TOOL_SOURCES.items():
        donor = tool != upstream_tool or tool == "frying_pan"
        for modifier, stem in MODIFIER_SOURCES.items():
            source = UPSTREAM_1_12 / upstream_tool / f"{stem}.png"
            if (tool, modifier) in NO_UPSTREAM_ART:
                continue
            if not source.is_file():
                raise SystemExit(f"missing upstream overlay: {source}")
            target = OUT / f"{tool}_{modifier}.png"
            shutil.copyfile(source, target)
            note = ""
            if tool in ("dagger", "scimitar"):
                note = " (no 1.12 dagger/scimitar; broadsword donates, issue #198's donor precedent)"
            elif tool == "katana":
                note = " (no 1.12 katana; longsword donates, issue #198's donor precedent)"
            elif tool in ("warmace", "vein_hammer"):
                note = " (no 1.12 counterpart; hammer donates, issue #198's donor precedent)"
            rows.append(
                f"| `src/main/resources/assets/forgeweave/textures/derived/tools/mods/{tool}_{modifier}.png`{note} "
                f"| `resources/assets/tconstruct/textures/items/{upstream_tool}/{stem}.png` "
                f"| `{UPSTREAM_COMMIT}` | MIT |")
            # M3.5 #400: the drawn-stage variants of this overlay, where upstream ships them.
            for stage in (1, 2, 3):
                staged = UPSTREAM_1_12 / upstream_tool / f"{stem}_{stage}.png"
                if not staged.is_file():
                    continue
                shutil.copyfile(staged, OUT / f"{tool}_{modifier}_draw{stage}.png")
                rows.append(
                    f"| `src/main/resources/assets/forgeweave/textures/derived/tools/mods/"
                    f"{tool}_{modifier}_draw{stage}.png` (issue #400; the `{upstream_tool}{stage}` "
                    f"texture key of this modifier's `models/item/modifiers/*.json`, selected by the "
                    f"`modifier_suffix` on `{upstream_tool}.tcon.json`'s pull stage {stage}) "
                    f"| `resources/assets/tconstruct/textures/items/{upstream_tool}/{stem}_{stage}.png` "
                    f"| `{UPSTREAM_COMMIT}` | MIT |")
    NOTICE_ROWS.write_text("\n".join(rows) + "\n")
    print(f"copied {len(rows)} overlays to {OUT}")
    print(f"NOTICE rows written to {NOTICE_ROWS}")


if __name__ == "__main__":
    main()
