#!/usr/bin/env python3
"""Derives the per-modifier tool overlay textures from upstream 1.12 (issue #257), byte-for-byte.

Upstream 1.12 renders an applied modifier as an extra untinted layer on the tool item, from a
per-tool overlay texture `items/<tool>/mod_<modifier>.png` (composited by its
`BakedToolModel#addModifierQuads`, textures declared per tool in `models/item/modifiers/*.json`).
This script copies every such overlay for every Forgeweave tool x every Forgeweave modifier that has
an upstream counterpart into `textures/derived/tools/mods/<tool>_<modifier>.png` -- straight
`shutil.copyfile`, no pixel is touched.

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
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    NOTICE_ROWS.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    for tool, upstream_tool in TOOL_SOURCES.items():
        donor = tool != upstream_tool or tool == "frying_pan"
        for modifier, stem in MODIFIER_SOURCES.items():
            source = UPSTREAM_1_12 / upstream_tool / f"{stem}.png"
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
    NOTICE_ROWS.write_text("\n".join(rows) + "\n")
    print(f"copied {len(rows)} overlays to {OUT}")
    print(f"NOTICE rows written to {NOTICE_ROWS}")


if __name__ == "__main__":
    main()
