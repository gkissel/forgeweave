#!/usr/bin/env python3
"""Generates Track B's smeltery datapack rows (issue #840, epic #824 Track B): melting recipes for
every ore/raw/ingot/nugget/block, casting recipes for every ingot/nugget/block/part, and the alloy
table connecting the 12 ore metals + 7 catalysts into the 18 alloy tool materials.

Casting recipes are cloned from the 73-file cobalt template (every part Forgeweave casts, per
dev.gkissel.forgeweave.item.ForgeweaveItems' PART_* roster) by substituting the material id -- amounts
are a part's fixed ingot-value cost, not material-dependent, so cobalt's own numbers are correct for
every new metal (confirmed against hepatizon's 71-file set, #843: every "amount" matches cobalt's row
for row). Melting and alloy amounts are this issue's own design (deliverable 2 and 4).

Usage: python3 scripts/generate_track_b_recipes.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CASTING_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/casting_recipe"
MELTING_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/melting_recipe"
ALLOY_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/alloy_recipe"

# The 11 ore-sourced tool materials (dev.gkissel.forgeweave.trackb.TrackBOre, issue #839). Issue
# #884 (1) retired "cinderstone" from this roster -- the stone-tier Basalt-flavored material is now
# vanilla basalt (material/basalt.json), not a TrackBOre entry, so it is Part-Builder-only and gets
# no casting/melting rows from this script (see BASALT_MELTING below for its one hand-picked recipe).
ORES = [
    "fulmenite", "duskspar", "voltcinder", "murkiron", "hardcinder",
    "nightshale", "warspar", "hollowstone", "resonite", "starfall_stone", "voidglass",
]

# Issue #929: fulmenite drops a crystal instead of a raw ore item (the brimspar shape, #903) --
# TrackBOre#dropsCrystal in Java. Its ore block has no melting row of its own; only the crystal does
# (crystal_melting_recipes below), at the same ingot value the raw item used to melt for.
CRYSTAL_ORES = {"fulmenite"}

# The 18 alloy tool materials (dev.gkissel.forgeweave.trackb.TrackBAlloy).
ALLOYS = [
    "ironbrand", "quakestone", "shardline", "embercast", "riftalloy", "tideiron",
    "cinderforge", "dreadalloy", "sunsteel", "hollowsteel", "truesteel", "stormalloy",
    "glowveil", "daybrass", "faultsteel", "skipalloy", "mendalloy", "mendstone",
]

# The 6 smeltery-only catalysts: id -> (source vanilla item, amount mB). No ingot/nugget/block item of
# their own (deliverable 5's "fluids/items with no Material entry at all" branch, see
# ForgeweaveFluids' own javadoc on the six CATALYST fields) -- sourced by melting a common vanilla
# item chosen to fit the id's theme, at a small catalyst-scale amount.
#
# Issue #910 retired the seventh, "twinalloy" (and its amethyst-shard melting row): brimspar (#903)
# already fills the same role -- the mined fuel that is also an alloy input -- so the two merged, and
# every recipe that took molten_twinalloy now takes molten_brimspar at the same amount. Brimspar is
# not listed here because it melts from its own ore's crystals, not from a vanilla item.
CATALYSTS = {
    "flarealloy": ("minecraft:blaze_powder", 32),
    "deepalloy": ("minecraft:echo_shard", 32),
    "sparkalloy": ("minecraft:glowstone_dust", 32),
    "redcinder": ("minecraft:redstone", 32),
    "pearlcinder": ("minecraft:ender_pearl", 32),
    "ambercinder": ("minecraft:honeycomb", 32),
}

VALUE_NUGGET = 16
VALUE_INGOT = 144
VALUE_BLOCK = VALUE_INGOT * 9


def write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2) + "\n")


def clone_casting_recipes() -> None:
    """Clones cobalt's full 73-recipe casting set onto every ore + alloy metal (deliverable 3)."""
    templates = sorted(CASTING_DIR.glob("*_cobalt.json"))
    assert len(templates) == 73, f"expected 73 cobalt casting recipes, found {len(templates)}"

    for material_id in ORES + ALLOYS:
        for template in templates:
            text = template.read_text()
            out_name = template.name.replace("cobalt", material_id)
            out_text = text.replace("cobalt", material_id)
            (CASTING_DIR / out_name).write_text(out_text)
        print(f"cloned 73 casting recipes for {material_id}")


def ore_melting_recipes() -> None:
    """Ore/raw/ingot/nugget/block melting rows for the ore metals (deliverable 2); a CRYSTAL_ORES
    member (#929) skips the ore-block and raw-item rows -- crystal_melting_recipes() below is its only
    way into the smeltery."""
    count = 0
    for ore_id in ORES:
        fluid = f"forgeweave:molten_{ore_id}"
        if ore_id not in CRYSTAL_ORES:
            write_json(MELTING_DIR / f"{ore_id}_ore.json",
                       {"input": {"tag": f"c:ores/{ore_id}"}, "fluid": fluid, "amount": VALUE_INGOT, "ore": True})
            write_json(MELTING_DIR / f"raw_{ore_id}.json",
                       {"input": {"tag": f"c:raw_materials/{ore_id}"}, "fluid": fluid, "amount": VALUE_INGOT, "ore": True})
            count += 2
        write_json(MELTING_DIR / f"{ore_id}_ingot.json",
                   {"input": {"tag": f"c:ingots/{ore_id}"}, "fluid": fluid, "amount": VALUE_INGOT})
        write_json(MELTING_DIR / f"{ore_id}_nugget.json",
                   {"input": {"tag": f"c:nuggets/{ore_id}"}, "fluid": fluid, "amount": VALUE_NUGGET})
        write_json(MELTING_DIR / f"{ore_id}_block.json",
                   {"input": {"tag": f"c:storage_blocks/{ore_id}"}, "fluid": fluid, "amount": VALUE_BLOCK})
        count += 3
    print(f"wrote {count} ore melting recipes")


def crystal_melting_recipes() -> None:
    """One melting row per CRYSTAL_ORES member (#929, the brimspar precedent, #903): the crystal item
    is the only mined-side input into the smeltery, at ingot value -- same shape as
    melting_recipe/brimspar_crystal.json."""
    for ore_id in sorted(CRYSTAL_ORES):
        write_json(MELTING_DIR / f"{ore_id}_crystal.json",
                   {"input": {"item": f"forgeweave:{ore_id}_crystal"}, "fluid": f"forgeweave:molten_{ore_id}",
                    "amount": VALUE_INGOT})
    print(f"wrote {len(CRYSTAL_ORES)} crystal melting recipes")


def alloy_melting_recipes() -> None:
    """Ingot/nugget/block melting rows for the 18 alloy-only metals -- no ore/raw form."""
    for alloy_id in ALLOYS:
        fluid = f"forgeweave:molten_{alloy_id}"
        write_json(MELTING_DIR / f"{alloy_id}_ingot.json",
                   {"input": {"tag": f"c:ingots/{alloy_id}"}, "fluid": fluid, "amount": VALUE_INGOT})
        write_json(MELTING_DIR / f"{alloy_id}_nugget.json",
                   {"input": {"tag": f"c:nuggets/{alloy_id}"}, "fluid": fluid, "amount": VALUE_NUGGET})
        write_json(MELTING_DIR / f"{alloy_id}_block.json",
                   {"input": {"tag": f"c:storage_blocks/{alloy_id}"}, "fluid": fluid, "amount": VALUE_BLOCK})
    print(f"wrote {len(ALLOYS) * 3} alloy melting recipes")


def catalyst_melting_recipes() -> None:
    """One melting row per catalyst, sourced from a common vanilla item (deliverable 5)."""
    for catalyst_id, (item, amount) in CATALYSTS.items():
        write_json(MELTING_DIR / f"{catalyst_id}.json",
                   {"input": {"item": item}, "fluid": f"forgeweave:molten_{catalyst_id}", "amount": amount})
    print(f"wrote {len(CATALYSTS)} catalyst melting recipes")


def basalt_melting_recipe() -> None:
    """Issue #884 (1): minecraft:basalt -> molten basalt, quakestone's replacement alloy input.
    Basalt is not a TrackBOre (Part-Builder-only, no ore/raw/nugget/block items of its own), so it
    gets one hand-picked melting row instead of the ORES loop's five."""
    write_json(MELTING_DIR / "basalt.json",
               {"input": {"item": "minecraft:basalt"}, "fluid": "forgeweave:molten_basalt", "amount": VALUE_INGOT})
    print("wrote 1 basalt melting recipe")


def fs(fluid_id: str, amount: int) -> dict:
    return {"fluid": f"forgeweave:molten_{fluid_id}", "amount": amount}


# The alloy table (deliverable 4): 18 outputs, 21 recipes (3 outputs get an alternative recipe,
# mirroring the reference ladder's own "triberium = tiberium + basalt (or dilithium)" branching
# without reusing its numbers -- ADR-0003, inspiration-only). Each entry is
# (output_id, [([(input_id, amount), ...], output_amount), ...]) -- one sub-tuple per recipe variant,
# each with its own output amount (a variant's total input mass differs from its siblings', so its
# output does too). Inputs mix ore-sourced metals, existing Forgeweave base fluids, catalysts (small
# amounts, the issue's "catalyst-style inputs" ask) and, at deeper layers, other Track B alloys -- the
# multi-layer branching shape TAIGA's own graph has.
ALLOY_RECIPES = [
    ("ironbrand", [([("redcinder", 32), ("pearlcinder", 32), ("ambercinder", 32)], 72)]),
    # Issue #884 (1): cinderstone input replaced with basalt (same amount, same alloy-ratio shape) --
    # basalt melts via BASALT_MELTING below, not this script's ORES loop.
    # Issue #910: twinalloy merged into brimspar (#903's mined fuel), same amounts throughout.
    ("quakestone", [([("fulmenite", 144), ("basalt", 144)], 144),
                    ([("fulmenite", 144), ("brimspar", 32)], 144)]),
    ("embercast", [([("duskspar", 144), ("ardite", 144)], 144)]),
    ("riftalloy", [([("murkiron", 144), ("nightshale", 144), ("voltcinder", 144)], 216)]),
    ("dreadalloy", [([("hardcinder", 144), ("murkiron", 144), ("deepalloy", 32)], 144)]),
    ("mendalloy", [([("nightshale", 144), ("hardcinder", 144), ("flarealloy", 32)], 144)]),
    ("mendstone", [([("hollowstone", 144), ("warspar", 144), ("flarealloy", 32)], 144),
                   ([("hollowstone", 144), ("warspar", 144), ("deepalloy", 32)], 144)]),
    ("tideiron", [([("cobalt", 144), ("ironbrand", 72)], 144)]),
    ("cinderforge", [([("ardite", 144), ("ironbrand", 72), ("flarealloy", 32)], 144)]),
    ("skipalloy", [([("ironbrand", 72), ("duskspar", 144)], 144)]),
    ("daybrass", [([("nightshale", 144), ("ironbrand", 72)], 144)]),
    ("faultsteel", [([("obsidian", 144), ("quakestone", 144), ("voltcinder", 144)], 216)]),
    ("shardline", [([("quakestone", 144), ("obsidian", 144), ("deepalloy", 32)], 144)]),
    ("glowveil", [([("riftalloy", 216), ("sparkalloy", 32), ("brimspar", 32)], 216),
                  ([("dreadalloy", 144), ("sparkalloy", 32), ("brimspar", 32)], 144)]),
    ("sunsteel", [([("warspar", 144), ("hollowstone", 144), ("glowveil", 144)], 216)]),
    ("hollowsteel", [([("resonite", 144), ("sunsteel", 216)], 216)]),
    # truesteel's inputs are a superset of hollowsteel's (both start from resonite + sunsteel), so
    # without an explicit priority hollowsteel's shorter recipe would intercept every truesteel pour
    # the moment its first two inputs land in the tank -- AlloyRecipe#priority (issue #291) is exactly
    # this contention-break mechanism, lower resolves first, default 0.
    ("truesteel", [([("resonite", 144), ("sunsteel", 216), ("sparkalloy", 32)], 216, -1)]),
    ("stormalloy", [([("quakestone", 144), ("shardline", 144), ("faultsteel", 216)], 288)]),
]


def alloy_table_recipes() -> None:
    total = 0
    for output_id, recipe_variants in ALLOY_RECIPES:
        for i, variant in enumerate(recipe_variants):
            inputs, output_amount = variant[0], variant[1]
            priority = variant[2] if len(variant) > 2 else 0
            suffix = "" if i == 0 else f"_alt{i}"
            data = {
                "inputs": [fs(input_id, amount) for input_id, amount in inputs],
                "result": fs(output_id, output_amount),
            }
            if priority != 0:
                data["priority"] = priority
            write_json(ALLOY_DIR / f"{output_id}{suffix}.json", data)
            total += 1
    print(f"wrote {total} alloy recipes for {len(ALLOY_RECIPES)} outputs")


def main() -> None:
    CASTING_DIR.mkdir(parents=True, exist_ok=True)
    MELTING_DIR.mkdir(parents=True, exist_ok=True)
    ALLOY_DIR.mkdir(parents=True, exist_ok=True)

    clone_casting_recipes()
    ore_melting_recipes()
    crystal_melting_recipes()
    basalt_melting_recipe()
    alloy_melting_recipes()
    catalyst_melting_recipes()
    alloy_table_recipes()


if __name__ == "__main__":
    main()
