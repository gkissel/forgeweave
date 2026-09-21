#!/usr/bin/env python3
"""Generates the art and the dust melting rows for issue #992's material forms (M8-8, docs/SCOPE.md
D-M8-6 and D-M8-7): the dust, small dust, tiny dust, plate, double plate, rod, gear and wire of every
Forgeweave material with an ingot, plus the three dusts alone for the two gem-type materials.

The Java side of the roster is dev.gkissel.forgeweave.material.MaterialForms; MATERIALS below mirrors
it, the same hand-kept mirroring generate_track_b_worldgen.py already documents for TrackBOre. The
Track B halves are imported from the two existing art scripts rather than retyped, so there is still
exactly one place each Track B id and flavor color is written down.

Same recolor math as scripts/generate_track_b_ore_textures.py (issue #878) and
generate_track_b_alloy_textures.py (#888): hue-recolor a template to the material's flavor color,
reusing that script's recolor_pixels/full_mask verbatim. Output stays 16x16 (CLAUDE.md's sprite
standard) and lands in the standard item texture folder, not derived/.

**Donor choice, now checked in (issue #1049).** Each of these eight forms has exactly one sensible
template, so the donor is fixed per form rather than per material. Every template below started as a
real vanilla item texture and is now a committed file under scripts/templates/material_forms/ (see
TEMPLATE_DIR) rather than something read out of the client jar at generation time -- issue #1049's
designer-brief gap: a designer had no file to open and replace. This script no longer touches a
Minecraft install or a client jar at all.

  * **Dust** <- `dust.png`, seeded from vanilla `glowstone_dust`, the loose pile of granules vanilla's
    dust icons are all drawn from.
  * **Small dust** and **tiny dust** <- `small_dust.png`/`tiny_dust.png`, seeded from the same
    `glowstone_dust` scaled to 10x10 and 6x6 with nearest-neighbour and centred on a transparent
    16x16 canvas (the pre-#1049 script did this scaling itself at generation time; the scaled result
    is now baked into the checked-in file, so a designer replacing `dust.png` does not also need to
    replace these two -- see the module's "Replacing a template" note below).
  * **Plate** <- `plate.png`, seeded from vanilla `paper`, the only vanilla item drawn as a flat
    rectangular sheet.
  * **Double plate** <- `double_plate.png`, seeded from two copies of `paper` scaled to 13x13 and
    offset three pixels, so the pair reads as one sheet lying on another rather than as a plate with
    a thicker edge (also baked in, same reasoning as the two dust sizes above).
  * **Rod** <- `rod.png`, seeded from vanilla `blaze_rod`, vanilla's one straight vertical rod.
  * **Gear** <- `gear.png`, seeded from vanilla `nether_star`, the only vanilla item with an evenly
    toothed radial silhouette, which is the gear read. (`clock` would be the other round candidate,
    but vanilla's clock is animated -- there is no `item/clock.png` to diff against, only
    `clock_00` .. `clock_63`.)
  * **Wire** <- `wire.png`, seeded from vanilla `string`, a tangle of thin strands that recolors into
    a coil of wire.
  * **Clump** (#994) <- `clump.png`, seeded from vanilla `clay_ball`, vanilla's one lumpy round
    handful of material.
  * **Dirty dust** (#994) <- `dirty_dust.png`, seeded from vanilla `gunpowder`, a coarser and darker
    pile than glowstone dust, so the dirty form reads as a step behind the clean one at a glance in
    the inventory.
  * **Shard** (#994) <- `shard.png`, seeded from vanilla `prismarine_shard`, which is literally
    vanilla's shard silhouette.

**Replacing a template.** Overwrite the form's own file under `scripts/templates/material_forms/`
and rerun this script; every material regenerates from it. `small_dust.png`/`tiny_dust.png` and
`double_plate.png` are independent files, not derived from `dust.png`/`plate.png` at generation time
any more, so redrawing the full-size form does not also redraw its derived sizes/stack -- replace all
three if a form's whole size ladder should change.

**Value range that survives tinting.** recolor_pixels() only reads each opaque pixel's saturation and
value (never its hue -- every output pixel takes the *material's* hue); a template drawn in flat
grayscale forces every pixel to one saturation (`flat_sat` below), discarding the per-pixel color
variation these particular templates carry, so the checked-in files stay the mid-saturation color the
brief already told a designer to draw in (see docs/design/designer-brief.md section 1), not a
desaturated grayscale copy. A future replacement should do the same: colour, not grey, with clear
value separation between a form's body and its shadow so the material's own lightness ratio has
something to scale.

Provenance: vanilla-derived, like both scripts it builds on, so no NOTICE.md row -- see
generate_track_b_ore_textures.py's own Provenance note for the full reasoning. The checked-in
templates are frozen copies of that same vanilla-derived pixel data, not new art.

**Melting rows.** D-M8-6 keys dust melting off `c:dusts/<id>` rather than the Forgeweave item, so
another mod's dust of the same material melts in a Forgeweave smeltery with no second recipe -- the
call M2 already made for ores and ingots, one layer out. Amounts follow the existing per-form ladder
(VALUE_INGOT/VALUE_SMALL_DUST/VALUE_NUGGET below): a dust melts as its ingot, a small dust as a third
of one, a tiny dust as a nugget. The plate family gets no melting row at all, because D-M8-6 makes it
output rather than currency and a melting recipe would spend one.

Usage: python3 scripts/generate_material_forms.py
Requires Pillow (`pip install pillow`). No Minecraft install and no client jar needed (issue #1049):
every donor template lives under scripts/templates/material_forms/.
"""
import json
from pathlib import Path

from PIL import Image

from generate_track_b_alloy_textures import ALLOYS
from generate_track_b_ore_textures import ORES, full_mask, hex_to_rgb, recolor_pixels
from generate_track_b_recipes import melt_temperature

ROOT = Path(__file__).resolve().parent.parent
ITEM_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/item"
MELTING_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/melting_recipe"
TEMPLATE_DIR = Path(__file__).resolve().parent / "templates/material_forms"

VALUE_NUGGET = 16
VALUE_SMALL_DUST = 48
VALUE_INGOT = 144

# The three dusts, and the five output-only forms. Mirrors MaterialForm.DUSTS / PLATE_FAMILY.
DUSTS = ["dust", "small_dust", "tiny_dust"]
PLATE_FAMILY = ["plate", "double_plate", "rod", "gear", "wire"]
ALL_FORMS = DUSTS + PLATE_FAMILY

# Issue #994's three Mekanism ore-chain intermediates. Mirrors MaterialForm.ORE_CHAIN, and goes to
# the Track B ores alone -- an ore block is the only thing those chains start from.
ORE_CHAIN = ["clump", "dirty_dust", "shard"]

# Every form with a checked-in template under TEMPLATE_DIR.
TEMPLATE_FORMS = ALL_FORMS + ORE_CHAIN

# mB a form melts into, keyed by form. A form absent here has no melting row (the plate family).
MELT_AMOUNTS = {"dust": VALUE_INGOT, "small_dust": VALUE_SMALL_DUST, "tiny_dust": VALUE_NUGGET}

# The ten Forgeweave metals with an ingot item outside the Track B rosters (D-M8-7). Colors are each
# material's own molten-fluid tint from dev.gkissel.forgeweave.fluid.ForgeweaveFluids, which is the
# flavor color the rest of this mod's art already uses for them.
OWN_ITEM_METALS = [
    ("cobalt", 0x2882D4),
    ("ardite", 0xD14210),
    ("manyullyn", 0xA15CF8),
    ("rose_gold", 0xB76E79),
    ("steel", 0xA7A7A7),
    ("knightslime", 0xF18FF0),
    ("pig_iron", 0xEF9E9B),
    ("amethyst_bronze", 0xC687BD),
    ("queens_slime", 0x236C45),
    ("hepatizon", 0x60496B),
]

# Gem-type materials get the three dusts and nothing else (D-M8-6): brimspar has no ingot at all
# (#903, a Nether fuel ore) and fulmenite's ore drops a crystal (#929). Fulmenite keeps its shipped
# ingot, nugget and storage block untouched. Colors match UnstableOreBlock#BRIMSPAR_CRYSTAL_COLOR and
# TrackBOre.FULMENITE#color.
GEM_MATERIALS = [
    ("fulmenite", 0xC7FA4F),
    ("brimspar", 0x0FBD59),
]

GEM_IDS = {mat_id for mat_id, _color in GEM_MATERIALS}


def materials() -> list[tuple[str, int, list[str]]]:
    """(id, color, forms) for every material that gets forms -- the mirror of MaterialForms.ALL."""
    rows = []
    ore_ids = {mat_id for mat_id, _color, _host in ORES}
    for mat_id, color, _host in ORES:
        if mat_id in GEM_IDS:
            continue
        rows.append((mat_id, color, ALL_FORMS + ORE_CHAIN))
    for mat_id, color in ALLOYS:
        rows.append((mat_id, color, ALL_FORMS))
    for mat_id, color in OWN_ITEM_METALS:
        rows.append((mat_id, color, ALL_FORMS))
    for mat_id, color in GEM_MATERIALS:
        # #994: fulmenite is a Track B ore as well as a gem-type material, so it gets the ore chain
        # on top of its three dusts; brimspar has no ore block on the Track B roster.
        rows.append((mat_id, color, DUSTS + ORE_CHAIN if mat_id in ore_ids else DUSTS))
    return rows


def donors() -> dict[str, Image.Image]:
    """The fixed per-form template image, loaded from the checked-in files under TEMPLATE_DIR -- see
    the module docstring's donor-choice section. Issue #1049: each file already carries whatever
    scaling/offset the pre-#1049 script used to compute at generation time (small_dust/tiny_dust's
    downscale, double_plate's offset stack), so this is a plain load with no per-form transform left.
    """
    return {form: Image.open(TEMPLATE_DIR / f"{form}.png").convert("RGBA") for form in TEMPLATE_FORMS}


def write_sprites() -> None:
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    templates = donors()
    written = 0
    for mat_id, color_hex, forms in materials():
        color = hex_to_rgb(color_hex)
        for form in forms:
            src = templates[form]
            recolor_pixels(src, full_mask(src), color).save(ITEM_DIR / f"{mat_id}_{form}.png")
            written += 1
    print(f"wrote {written} material form sprites for {len(materials())} materials")


def write_melting_rows() -> None:
    MELTING_DIR.mkdir(parents=True, exist_ok=True)
    written = 0
    for mat_id, _color, forms in materials():
        for form in forms:
            amount = MELT_AMOUNTS.get(form)
            if amount is None:
                continue
            name = f"{mat_id}_{form}"
            data = {
                "input": {"tag": f"c:{form}s/{mat_id}"},
                "fluid": f"forgeweave:molten_{mat_id}",
                "amount": amount,
            }
            # Issue #1113: a dust of a deep alloy asks for the same fuel its ingot does, so the fuel
            # ladder cannot be walked around by grinding the ingot first.
            temperature = melt_temperature(mat_id)
            if temperature is not None:
                data["temperature"] = temperature
            (MELTING_DIR / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n")
            written += 1
    print(f"wrote {written} dust melting rows")


def main() -> None:
    write_sprites()
    write_melting_rows()


if __name__ == "__main__":
    main()
