#!/usr/bin/env python3
"""Generates Track B's 18 alloy tool materials' art (issue #840, epic #824 Track B; supersedes the
procedural approach from this file's own original version) for the ingot, nugget and storage block of
each material in dev.gkissel.forgeweave.trackb.TrackBAlloy.

Maintainer directive (2026-09-01, issue #888): same vanilla-derived treatment as the ore family
(scripts/generate_track_b_ore_textures.py, issue #878) instead of the flat-shape procedural art this
script used to draw. Alloys have no raw item to anchor a donor family to (unlike the ore family), so
issue #888 has each alloy draw an independent deterministic donor from the *same* pools issue #878
already established -- this script imports and reuses those pools and derivation helpers verbatim
rather than re-deriving its own, so there is exactly one donor table across both scripts:

  * **Ingot** and **nugget**: donor family drawn from generate_track_b_ore_textures.RAW_POOL (iron,
    copper, gold -- the only three vanilla metals with ingot/nugget textures), seeded by
    `_derive_raw_family(alloy_id)`, the same function the ore script uses for its own raw-item donor.
    Copper has no vanilla nugget texture, so alloys drawn onto copper fall back to iron_nugget for the
    nugget only, via the same `_nugget_family()` helper the ore script uses.
  * **Storage block**: donor drawn from generate_track_b_ore_textures.ORE_STORAGE_POOL (iron, copper,
    gold, diamond, redstone, lapis, emerald), seeded by `_derive_storage_template(alloy_id)`, the same
    function the ore script uses for its own storage block.

All three are full-image hue-recolors (recolor_pixels/full_mask, imported from the ore script) to the
material's own flavor color (dev.gkissel.forgeweave.trackb.TrackBAlloy#color) -- shift every opaque
pixel's hue to the material color's hue, scale saturation/value by the ratio of the material color's
own saturation/value to the source image's average. Same algorithm, same provenance reasoning as the
ore script (vanilla-derived, not a Tinkers'/Mantle clone derivation -- no NOTICE.md row).

Usage: python3 scripts/generate_track_b_alloy_textures.py
Requires Pillow (`pip install pillow`). Requires a Minecraft 1.21.1 client jar reachable from Gradle's
caches (present after a `./gradlew build` in this project) -- see
generate_track_b_ore_textures.find_vanilla_jar().
"""
from pathlib import Path

from generate_track_b_ore_textures import (
    VanillaAssets,
    _derive_raw_family,
    _derive_storage_template,
    _nugget_family,
    find_vanilla_jar,
    full_mask,
    hex_to_rgb,
    recolor_pixels,
)

ROOT = Path(__file__).resolve().parent.parent
BLOCK_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/block"
ITEM_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/item"

# (id, color) -- must match TrackBAlloy.ALL's ids and colors (research doc §7.3's "Alloy" table).
ALLOYS = [
    ("ironbrand", 0xB5502C),
    ("quakestone", 0x8FA35E),
    ("shardline", 0xA9D8E0),
    ("embercast", 0xE0611A),
    ("riftalloy", 0x7A3FA0),
    ("tideiron", 0x2F7A7A),
    ("cinderforge", 0xD1350B),
    ("dreadalloy", 0x2B3B2B),
    ("sunsteel", 0xE6C64A),
    ("hollowsteel", 0x9FB6C2),
    ("truesteel", 0xC7D6E8),
    ("stormalloy", 0x5C5B7A),
    ("glowveil", 0x4AE6C6),
    ("daybrass", 0xC9A227),
    ("faultsteel", 0x7A6852),
    ("skipalloy", 0x6FD1D1),
    ("mendalloy", 0x7FBF6B),
    ("mendstone", 0xC2A878),
    # #873 -- the three PlusTiC-inspiration alloys (M6 epic #824's JC3 reversal). Compat-gated
    # (dev.gkissel.forgeweave.trackb.TrackBAlloy's own javadoc), but their items reuse this exact
    # generator since item/block registration is unconditional either way.
    ("alumite", 0xB8B2C9),
    ("osgloglas", 0x7FA8A3),
    ("osmiridium", 0xC9C2D6),
]

# Recorded template-assignment table (issue #888), reproducible by re-running the imported _derive_*
# helpers against ALLOYS's ids -- kept as a literal-shaped dict here so the mapping is reviewable
# without running anything. family (ingot/nugget donor) + storage per material id.
TEMPLATES = {
    alloy_id: {
        "storage": _derive_storage_template(alloy_id),
        "family": _derive_raw_family(alloy_id),
    }
    for alloy_id, _color in ALLOYS
}


def main() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)

    assets = VanillaAssets(find_vanilla_jar())

    for alloy_id, color_hex in ALLOYS:
        color = hex_to_rgb(color_hex)
        tpl = TEMPLATES[alloy_id]
        family = tpl["family"]
        nugget_family = _nugget_family(family)

        storage_img = assets.block(tpl["storage"])
        recolor_pixels(storage_img, full_mask(storage_img), color).save(BLOCK_DIR / f"{alloy_id}_block.png")

        ingot_img = assets.item(f"{family}_ingot")
        recolor_pixels(ingot_img, full_mask(ingot_img), color).save(ITEM_DIR / f"{alloy_id}_ingot.png")

        nugget_img = assets.item(f"{nugget_family}_nugget")
        recolor_pixels(nugget_img, full_mask(nugget_img), color).save(ITEM_DIR / f"{alloy_id}_nugget.png")

        print(
            f"wrote {alloy_id}: storage<-{tpl['storage']} ingot<-{family}_ingot "
            f"nugget<-{nugget_family}_nugget"
        )


if __name__ == "__main__":
    main()
