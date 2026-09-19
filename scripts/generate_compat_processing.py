#!/usr/bin/env python3
"""Generates issue #995's (M8-11, docs/SCOPE.md D-M8-12/D-M8-13/D-M8-16) recipe JSON for three
processing mods and one data map for a fourth, from the same material tables
scripts/generate_track_b_recipes.py and scripts/generate_material_forms.py already keep. One script,
four output trees, sibling to those two and to scripts/generate_compat_smeltery.py.

Every row carries a `neoforge:conditions` gate on both the target mod (`neoforge:mod_loaded`, so a
pack without it simply drops the row rather than failing to load a recipe type nobody registered) and
this issue's own compat toggle (`forgeweave:compat_toggle`, dev.gkissel.forgeweave.config
.ForgeweaveConfigCondition) -- see that class's javadoc for why a custom condition is the smallest
thing that reaches an off path for a recipe type Forgeweave does not own the lookup code for.

The four basic alloys (manyullyn, rose_gold, embercast, osmiridium) are BASIC_ALLOYS below, not the
issue's own guess (manyullyn, alumite, rose gold, pig iron). The issue asks to confirm its guess
"against the shipped alloying recipes rather than trusting this list" -- see
BasicAlloyClassifierTest, which re-derives the same four independently from
data/forgeweave/forgeweave/alloy_recipe/*.json and is what actually enforces this list, not this
script. alumite and pig_iron both take three inputs (alumite: aluminium + iron + obsidian; pig_iron:
iron + blood + clay), not "ingot plus ingot" -- obsidian and blood/clay are catalyst/fuel fluids with
no ingot form of their own (ForgeweaveFluids' own class javadoc calls both out by name). embercast
(duskspar + ardite) and osmiridium (osmium + iridium) are the two the issue's guess missed: both take
exactly two inputs, and neither input is itself the result of another alloy_recipe (the "ingot", not
"alloy", half of the rule) -- see BasicAlloyClassifierTest's own javadoc for the full rule.

Usage: python3 scripts/generate_compat_processing.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RECIPE_DIR = ROOT / "src/main/resources/data/forgeweave/recipe"
POWAH_DATA_MAP = ROOT / "src/main/resources/data/powah/data_maps/fluid/heat_source.json"

# Issue #995's own confirmed set -- see this module's docstring and BasicAlloyClassifierTest.
# (output_id, input1_material, input2_material)
BASIC_ALLOYS = [
    ("manyullyn", "cobalt", "ardite"),
    ("rose_gold", "copper", "gold"),
    ("embercast", "duskspar", "ardite"),
    ("osmiridium", "osmium", "iridium"),
]

# dev.gkissel.forgeweave.trackb.TrackBOre.ALL, all 11 -- every one has an ore block regardless of
# TrackBOre#dropsCrystal (fulmenite's block still exists and is still silk-touchable; dropsCrystal only
# changes what falls out when it is NOT silk touched, per MaterialForms' own javadoc).
TRACK_B_ORES = [
    "fulmenite", "duskspar", "voltcinder", "murkiron", "hardcinder",
    "nightshale", "warspar", "hollowstone", "resonite", "starfall_stone", "voidglass",
]

# The 45 materials D-M8-6 gives a plate form (dev.gkissel.forgeweave.material.MaterialForms.ALL minus
# fulmenite and brimspar, the two dust-only gem-type entries -- see MaterialForm#PLATE_FAMILY's own
# javadoc, "output-only, made for other mods' presses").
TRACK_B_ORES_WITH_PLATE = [o for o in TRACK_B_ORES if o != "fulmenite"]
TRACK_B_ALLOYS = [
    "ironbrand", "quakestone", "shardline", "embercast", "riftalloy", "tideiron",
    "cinderforge", "dreadalloy", "sunsteel", "hollowsteel", "truesteel", "stormalloy",
    "glowveil", "daybrass", "faultsteel", "skipalloy", "mendalloy", "mendstone",
    "alumite", "osgloglas", "osmiridium",
    "duskweld", "emberweld", "starweld", "voidweld",
]
OWN_ITEM_METALS = [
    "cobalt", "ardite", "manyullyn", "rose_gold", "steel", "knightslime",
    "pig_iron", "amethyst_bronze", "queens_slime", "hepatizon",
]
PLATE_MATERIALS = TRACK_B_ORES_WITH_PLATE + TRACK_B_ALLOYS + OWN_ITEM_METALS

# docs/SCOPE.md D-M8-11's fuel ladder rungs that are Forgeweave's own fluids (D-M8-13: "Forgeweave's
# molten fluids", not vanilla lava, which Powah already carries its own entry for). Temperatures are
# ForgeweaveFluids' own registered numbers (dev.gkissel.forgeweave.fluid.ForgeweaveFluids); pinned
# against them by PowahHeatSourceTest rather than retyped from a second table.
POWAH_HEAT_SOURCES = [
    ("forgeweave:blazing_blood", 1500),
    ("forgeweave:molten_magma", 1700),
    ("forgeweave:molten_brimspar", 1900),
    ("forgeweave:molten_pyrealloy", 2100),
]


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def conditions(mod_id: str, toggle: str) -> list:
    return [
        {"type": "neoforge:mod_loaded", "modid": mod_id},
        {"type": "forgeweave:compat_toggle", "toggle": toggle},
    ]


def ingot_tag(material_id: str) -> dict:
    return {"tag": f"c:ingots/{material_id}"}


def create_mixing_recipes() -> int:
    count = 0
    for alloy_id, input1, input2 in BASIC_ALLOYS:
        write_json(RECIPE_DIR / "create/mixing" / f"{alloy_id}.json", {
            "neoforge:conditions": conditions("create", "createRecipes"),
            "type": "create:mixing",
            "heat_requirement": "heated",
            "ingredients": [ingot_tag(input1), ingot_tag(input2)],
            "results": [{"id": f"forgeweave:{alloy_id}_ingot"}],
        })
        count += 1
    return count


def create_crushing_recipes() -> int:
    count = 0
    for ore_id in TRACK_B_ORES:
        write_json(RECIPE_DIR / "create/crushing" / f"{ore_id}.json", {
            "neoforge:conditions": conditions("create", "createRecipes"),
            "type": "create:crushing",
            "ingredients": [{"tag": f"c:ores/{ore_id}"}],
            "processing_time": 200,
            "results": [{"id": f"forgeweave:{ore_id}_dust", "count": 2}],
        })
        count += 1
    return count


def create_pressing_recipes() -> int:
    count = 0
    for material_id in PLATE_MATERIALS:
        write_json(RECIPE_DIR / "create/pressing" / f"{material_id}.json", {
            "neoforge:conditions": conditions("create", "createRecipes"),
            "type": "create:pressing",
            "ingredients": [ingot_tag(material_id)],
            "results": [{"id": f"forgeweave:{material_id}_plate"}],
        })
        count += 1
    return count


def ie_arc_furnace_recipes() -> int:
    count = 0
    for alloy_id, input1, input2 in BASIC_ALLOYS:
        write_json(RECIPE_DIR / "immersive_engineering/arc_furnace" / f"{alloy_id}.json", {
            "neoforge:conditions": conditions("immersiveengineering", "immersiveEngineeringRecipes"),
            "type": "immersiveengineering:arc_furnace",
            "input": ingot_tag(input1),
            "additives": [ingot_tag(input2)],
            "results": [{"id": f"forgeweave:{alloy_id}_ingot"}],
            "energy": 51200,
            "time": 200,
        })
        count += 1
    return count


def ie_crusher_recipes() -> int:
    count = 0
    for ore_id in TRACK_B_ORES:
        write_json(RECIPE_DIR / "immersive_engineering/crusher" / f"{ore_id}.json", {
            "neoforge:conditions": conditions("immersiveengineering", "immersiveEngineeringRecipes"),
            "type": "immersiveengineering:crusher",
            "input": {"tag": f"c:ores/{ore_id}"},
            "result": {"id": f"forgeweave:{ore_id}_dust", "count": 2},
            "energy": 3000,
        })
        count += 1
    return count


def ie_metal_press_recipes() -> int:
    count = 0
    for material_id in PLATE_MATERIALS:
        write_json(RECIPE_DIR / "immersive_engineering/metal_press" / f"{material_id}.json", {
            "neoforge:conditions": conditions("immersiveengineering", "immersiveEngineeringRecipes"),
            "type": "immersiveengineering:metal_press",
            "input": ingot_tag(material_id),
            "mold": "immersiveengineering:mold_plate",
            "result": {"id": f"forgeweave:{material_id}_plate"},
            "energy": 2400,
        })
        count += 1
    return count


def enderio_alloy_smelting_recipes() -> int:
    count = 0
    for alloy_id, input1, input2 in BASIC_ALLOYS:
        write_json(RECIPE_DIR / "enderio/alloy_smelting" / f"{alloy_id}.json", {
            "neoforge:conditions": conditions("enderio", "enderIoRecipes"),
            "type": "enderio:alloy_smelting",
            "inputs": [
                {"count": 1, "tag": f"c:ingots/{input1}"},
                {"count": 1, "tag": f"c:ingots/{input2}"},
            ],
            "output": {"count": 1, "id": f"forgeweave:{alloy_id}_ingot"},
            "energy": 6400,
            "experience": 0.5,
        })
        count += 1
    return count


def enderio_sag_milling_recipes() -> int:
    count = 0
    for ore_id in TRACK_B_ORES:
        write_json(RECIPE_DIR / "enderio/sag_milling" / f"{ore_id}.json", {
            "neoforge:conditions": conditions("enderio", "enderIoRecipes"),
            "type": "enderio:sag_milling",
            "input": {"tag": f"c:ores/{ore_id}"},
            "outputs": [{"item": {"id": f"forgeweave:{ore_id}_dust", "count": 2}}],
            "energy": 2400,
            "bonus": "none",
        })
        count += 1
    return count


# Issue #994 (M8-10, D-M8-15): Mekanism's own ore chains for the eleven Track B ores. The chemicals
# are Mekanism's own, read off its shipped rows (data/mekanism/recipe/processing/lead/*) rather than
# retyped from the wiki, and so are the multipliers: 2x enriching, 3x purifying, 4x injecting, then
# the shard -> clump -> dirty dust -> dust walk back down at 1:1.
#
# No 5x chain. That one starts at the Chemical Dissolution Chamber, which turns the ore into a
# *slurry* and washes and crystallizes it -- a chemical form of a Forgeweave metal, which D-M8-6
# names as a non-goal ("no liquid or gas forms of Forgeweave materials"). So the chains stop at the
# four the issue names, and no crystal form is registered. See MaterialForm.ORE_CHAIN.
MEKANISM_OXYGEN = "mekanism:oxygen"
MEKANISM_HYDROGEN_CHLORIDE = "mekanism:hydrogen_chloride"


def mekanism_ore_chain_recipes() -> int:
    """Six rows per ore: the three chain heads, and the three steps back down to a dust."""
    count = 0
    for ore_id in TRACK_B_ORES:
        for name, recipe in mekanism_chain_rows(ore_id):
            recipe["neoforge:conditions"] = conditions("mekanism", "mekanismModules")
            write_json(RECIPE_DIR / "mekanism" / ore_id / f"{name}.json", recipe)
            count += 1
    return count


def mekanism_chain_rows(ore_id: str) -> list:
    ore = {"count": 1, "tag": f"c:ores/{ore_id}"}
    return [
        ("dust_from_ore", enriching(ore, form_output(ore_id, "dust", 2))),
        ("clump_from_ore", purifying(ore, MEKANISM_OXYGEN, form_output(ore_id, "clump", 3))),
        ("shard_from_ore", injecting(ore, MEKANISM_HYDROGEN_CHLORIDE, form_output(ore_id, "shard", 4))),
        ("clump_from_shard", purifying({"count": 1, "tag": f"c:shards/{ore_id}"}, MEKANISM_OXYGEN,
                                       form_output(ore_id, "clump", 1))),
        ("dirty_dust_from_clump", crushing({"count": 1, "tag": f"c:clumps/{ore_id}"},
                                           form_output(ore_id, "dirty_dust", 1))),
        ("dust_from_dirty_dust", enriching({"count": 1, "tag": f"c:dirty_dusts/{ore_id}"},
                                           form_output(ore_id, "dust", 1))),
    ]


def form_output(ore_id: str, form: str, count: int) -> dict:
    return {"count": count, "id": f"forgeweave:{ore_id}_{form}"}


def enriching(item_input: dict, output: dict) -> dict:
    return {"type": "mekanism:enriching", "input": item_input, "output": output}


def crushing(item_input: dict, output: dict) -> dict:
    return {"type": "mekanism:crushing", "input": item_input, "output": output}


def purifying(item_input: dict, chemical: str, output: dict) -> dict:
    return chemical_recipe("mekanism:purifying", item_input, chemical, output)


def injecting(item_input: dict, chemical: str, output: dict) -> dict:
    return chemical_recipe("mekanism:injecting", item_input, chemical, output)


def chemical_recipe(recipe_type: str, item_input: dict, chemical: str, output: dict) -> dict:
    return {
        "type": recipe_type,
        "chemical_input": {"amount": 1, "chemical": chemical},
        "item_input": item_input,
        "output": output,
        "per_tick_usage": True,
    }


def powah_heat_source_data_map() -> int:
    values = {}
    for fluid_id, temperature in POWAH_HEAT_SOURCES:
        values[fluid_id] = {
            "temperature": temperature,
            "neoforge:conditions": conditions("powah", "powahHeatSources"),
        }
    write_json(POWAH_DATA_MAP, {"values": values})
    return len(values)


def main() -> None:
    total = 0
    total += create_mixing_recipes()
    total += create_crushing_recipes()
    total += create_pressing_recipes()
    total += ie_arc_furnace_recipes()
    total += ie_crusher_recipes()
    total += ie_metal_press_recipes()
    total += enderio_alloy_smelting_recipes()
    total += enderio_sag_milling_recipes()
    total += mekanism_ore_chain_recipes()
    print(f"wrote {total} recipe files")
    print(f"wrote {powah_heat_source_data_map()} Powah heat_source entries")


if __name__ == "__main__":
    main()
