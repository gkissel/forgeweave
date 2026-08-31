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
]

TIER_BAND = {
    "minecraft:incorrect_for_stone_tool": 720,
    "minecraft:incorrect_for_iron_tool": 900,
    "minecraft:incorrect_for_diamond_tool": 1040,
    "minecraft:incorrect_for_netherite_tool": 1160,
}
TIER_STEP = {
    "minecraft:incorrect_for_stone_tool": 18,
    "minecraft:incorrect_for_iron_tool": 16,
    "minecraft:incorrect_for_diamond_tool": 14,
    "minecraft:incorrect_for_netherite_tool": 12,
}


def load_material(material_id: str) -> dict:
    return json.loads((MATERIAL_DIR / f"{material_id}.json").read_text())


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
    """Every neoforge:item_exists item id a (possibly neoforge:or) condition list touches."""
    items = []
    for entry in condition:
        _collect(entry, items)
    return items


def _collect(entry: dict, items: list[str]) -> None:
    if entry["type"] == "neoforge:item_exists":
        items.append(entry["item"])
    elif entry["type"] == "neoforge:or":
        for value in entry["values"]:
            _collect(value, items)
    else:
        raise ValueError(f"unhandled condition type: {entry}")


if __name__ == "__main__":
    table = build_table()
    for material_id, info in table.items():
        print(material_id, hex(info["color"]), info["temperature"], info["provider_items"])
