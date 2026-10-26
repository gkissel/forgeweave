"""Shared data table for issue #873 (M6 epic #824, JC3 reversal): every compat metal that gets full
smeltery integration, its color/tier (read from its own material JSON), a computed fluid temperature,
and its provider item id(s) for the "is provider present" hiding helper. Single source of truth for
scripts/generate_compat_smeltery.py and the Java snippets pasted into ForgeweaveFluids/
CompatMaterialAvailability -- not itself part of the build, a codegen input.

Not meltable (gems/crystals/organics upstream never treated as meltable, or a non-metal synthetic):
black_quartz, certus_quartz, diamatine_crystal, dragonyst, emeradic_crystal, enori_crystal, fluix,
fluorite, palis_crystal, psigem, restonia_crystal, sky_stone, void_crystal, hdpe.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MATERIAL_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/material"

# Every compat metal (Track A batches 1-5 + #874 recovery) getting full smeltery integration.
# Order preserved for deterministic output.
METALS = [
    "aluminium", "bronze", "conductive_alloy", "constantan", "dark_steel", "draconium_awakened",
    "draconium", "electrum", "end_steel", "energetic_alloy", "iesnium", "invar", "iridium", "lead",
    "nickel", "osmium", "platinum", "psimetal", "ebony_psimetal", "ivory_psimetal", "pulsating_alloy",
    "redstone_alloy", "refined_glowstone", "refined_obsidian", "silver", "soularium", "tin",
    "titanium", "tungsten", "uranium", "vibrant_alloy", "pink_slime", "graphite", "dark_matter",
    "red_matter", "cosmic_neutronium", "crystal_matrix", "infinity", "chaotic", "wyvern",
    "quartz_enriched_iron", "silicon", "energised_steel", "blutonium", "cyanite", "ludicrite",
    "uraninite",
    # 1.0.0 release blocker: these 21 shipped cast_only with no fluid, melting or casting row, so the
    # Part Builder refused them (craftCastableMaterials off) and nothing else could make a part.
    # Appended, not inserted, so every existing metal keeps its place in the tier counters below.
    "elementarium_vanadium", "elementarium_chromium", "elementarium_molybdenum",
    "elementarium_palladium", "elementarium_hafnium", "elementarium_tantalum",
    "allthemodium", "vibranium", "unobtainium", "vibranium_allthemodium_alloy",
    "unobtainium_allthemodium_alloy", "unobtainium_vibranium_alloy",
    "inferium", "prudentium", "tertium", "imperium", "supremium", "awakened_supremium", "insanium",
    "prosperity", "soulium",
]

# The ingot a casting table pours for a material whose own condition names no item. Elementarium's
# presets gate on neoforge:mod_loaded plus the elementariumMaterials toggle, so there is no item id
# to borrow; the mod registers every element's ingot as elementarium:<element>_ingot
# (Material#formId in its jar: name + "_" + form).
INGOT_RESULT_OVERRIDES = {
    f"elementarium_{element}": f"elementarium:{element}_ingot"
    for element in ("vanadium", "chromium", "molybdenum", "palladium", "hafnium", "tantalum")
}

# Eternal Ores only dedupes metals other mods already supply, so when it and the original provider
# are both installed the casting table pours the original provider's ingot. Every other provider
# keeps the order its material's own neoforge:or lists it in.
FALLBACK_LAST_NAMESPACES = ("eternalores",)

# Issue #953: the Draconic Evolution core materials are Part Builder only -- no fluid, no melting
# recipe, no casting recipe, no cast_only flag. They stay in METALS so that the per-tier temperature
# counter below keeps producing the numbers already registered in ForgeweaveFluids for every metal
# listed after them; SMELTERY_METALS is the list the generator actually walks.
#
# `awakened` (#953) and `draconium_core` (#965) are the two later core materials, and neither is in
# METALS at all: they were added after the counter above was already pinned, so listing them would
# shift every netherite-tier temperature after them. A core has no fluid to give a temperature to
# either way, which is why nothing is lost by leaving them out.
CORE_ONLY = {"wyvern", "chaotic"}

SMELTERY_METALS = [material_id for material_id in METALS if material_id not in CORE_ONLY]

TIER_BAND = {
    "minecraft:incorrect_for_stone_tool": 720,
    "minecraft:incorrect_for_iron_tool": 900,
    "minecraft:incorrect_for_diamond_tool": 1040,
    "minecraft:incorrect_for_netherite_tool": 1160,
    # The three Forgeweave rungs above netherite, continuing the band past ludicrite's 1328.
    "forgeweave:incorrect_for_hardcinder_tool": 1340,
    "forgeweave:incorrect_for_warspar_tool": 1370,
    "forgeweave:incorrect_for_resonite_tool": 1400,
}
TIER_STEP = {
    "minecraft:incorrect_for_stone_tool": 18,
    "minecraft:incorrect_for_iron_tool": 16,
    "minecraft:incorrect_for_diamond_tool": 14,
    "minecraft:incorrect_for_netherite_tool": 12,
    "forgeweave:incorrect_for_hardcinder_tool": 10,
    "forgeweave:incorrect_for_warspar_tool": 10,
    "forgeweave:incorrect_for_resonite_tool": 10,
}


def load_material(material_id: str) -> dict:
    return json.loads((MATERIAL_DIR / f"{material_id}.json").read_text())


# Issue #954: only 7 of 381 melting recipes carried an explicit `temperature` before this; every
# other recipe (Track A included) fell back to MeltingRecipe#calcTemperature deriving one from the
# output fluid's own temperature and the item amount, which put nearly every modded ingot within
# lava's reach (1300) regardless of tool tier -- a full block reached the fluid's own temperature,
# but an ingot only ever reached about half of it, so the tier gate the fluid temperatures
# (ForgeweaveFluids, band 700-1330) were meant to express never actually reached the recipe. This
# table keys a Track A material's melting temperature to its `incorrect_for_tool` tag and the fuel
# ladder (lava 1300, blazing blood 1500, molten magma 1700, brimspar 1900, pyrealloy 2100) instead,
# pinned once per material so every form (ingot, nugget, block, raw, dust) needs the same fuel --
# today a block derives harder than its own ingot, which this also fixes.
#
# Bucketed by minecraft's own four harvest tags rather than by amount: `incorrect_for_stone_tool`
# and `incorrect_for_iron_tool` cover the common iron/gold-tier compat metals (copper, tin, bronze,
# invar, ...) and stay under lava so they melt exactly as easily as their vanilla counterparts.
# `incorrect_for_diamond_tool` (the stronger alloys -- platinum, titanium, tungsten, uranium, ...)
# needs blazing blood. `incorrect_for_netherite_tool` (draconium, the "matter" tier, the fusion
# metals' base tier, ...) needs molten magma. See TrackAMeltingTemperatureTest (Java mirror) and the
# #954 PR for the worked table.
TIER_MELT_TEMPERATURE = {
    "minecraft:incorrect_for_stone_tool": 1200,
    "minecraft:incorrect_for_iron_tool": 1200,
    "minecraft:incorrect_for_diamond_tool": 1400,
    "minecraft:incorrect_for_netherite_tool": 1600,
    # The three rungs #877 minted above netherite (1.0.0 release blocker: Allthemodium and Mystical
    # Agriculture metals sit on them). Brimspar melts the first two, pyrealloy the top one -- the
    # rungs draconium_awakened and atomic_matter_alloy (resonite tier) already sit on below.
    "forgeweave:incorrect_for_hardcinder_tool": 1800,
    "forgeweave:incorrect_for_warspar_tool": 1800,
    "forgeweave:incorrect_for_resonite_tool": 2000,
}

# Materials whose lore tier runs hotter than their harvest tag alone would give them. draconium
# itself needs no entry here -- its netherite_tool tag already lands it on 1600, the "Draconic
# draconium 1600" the issue calls out explicitly. draconium_awakened and the three Draconic
# Evolution fusion metals (emberweld/starweld/voidweld, #946) are the endgame tier above that.
MATERIAL_MELT_TEMPERATURE_OVERRIDES = {
    "draconium_awakened": 1800,  # brimspar
    "emberweld": 1800,  # brimspar
    "starweld": 2000,  # pyrealloy
    "voidweld": 2000,  # pyrealloy
    # #993: atomic matter alloy sits at the same endgame tier. Its resonite-tier tag now lands on
    # 2000 through TIER_MELT_TEMPERATURE too; the row stays because it is not a TIER_BAND metal.
    "atomic_matter_alloy": 2000,  # pyrealloy
}


def melt_temperature(material_id: str) -> int:
    """A Track A material's explicit melting_recipe `temperature` (issue #954): the override table
    first, else its own material JSON's `incorrect_for_tool` tag looked up in the tier table."""
    if material_id in MATERIAL_MELT_TEMPERATURE_OVERRIDES:
        return MATERIAL_MELT_TEMPERATURE_OVERRIDES[material_id]
    tier = load_material(material_id)["incorrect_for_tool"]
    return TIER_MELT_TEMPERATURE[tier]


def build_table():
    """Returns id -> {color:int, temperature:int, condition:dict, provider_items:[str], forms:[(kind,ingredient,value)]}."""
    tier_counters: dict[str, int] = {}
    table = {}
    for material_id in METALS:
        data = load_material(material_id)
        color = int(data["color"].lstrip("#"), 16)
        tier = data["incorrect_for_tool"]
        i = tier_counters.get(tier, 0)
        tier_counters[tier] = i + 1
        temperature = TIER_BAND[tier] + i * TIER_STEP[tier]
        condition = data["neoforge:conditions"]
        provider_items = extract_provider_items(condition)
        table[material_id] = {
            "color": color,
            "temperature": temperature,
            "condition": condition,
            "provider_items": provider_items,
            "crafting_items": data["crafting_items"],
        }
    return table


def extract_provider_items(condition) -> list[str]:
    """Every neoforge:item_exists item id a (possibly neoforge:or) condition list touches, in
    casting preference order: the condition's own order, with FALLBACK_LAST_NAMESPACES moved last."""
    items = []
    for entry in condition:
        _collect(entry, items)
    return sorted(items, key=lambda item: item.split(":")[0] in FALLBACK_LAST_NAMESPACES)


# Gates that name no item. Elementarium's presets use both, so they carry no provider item id.
ITEMLESS_CONDITIONS = ("neoforge:mod_loaded", "forgeweave:compat_toggle")


def _collect(entry: dict, items: list[str]) -> None:
    if entry["type"] == "neoforge:item_exists":
        items.append(entry["item"])
    elif entry["type"] == "neoforge:or":
        for value in entry["values"]:
            _collect(value, items)
    elif entry["type"] not in ITEMLESS_CONDITIONS:
        raise ValueError(f"unhandled condition type: {entry}")


if __name__ == "__main__":
    table = build_table()
    for material_id, info in table.items():
        print(material_id, hex(info["color"]), info["temperature"], info["provider_items"])
