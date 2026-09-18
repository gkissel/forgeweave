#!/usr/bin/env python3
"""Generates Elementarium's Track A material presets (issue #998, D-M8-19).

Elementarium is closed source (the jar ships compiled classes and no public repo), so this script
cannot read its actual data at generation time the way generate_track_b_worldgen.py reads
TrackBOre. What is public and verified (see the PR body for how) is that Elementarium tags every
metal ingot it adds under the NeoForge Common Conventions family c:ingots/<element_id>, using plain
lowercase English element names as ids, and that its own worldgen ore data never asks for more than
a vanilla diamond-tier tool (its MiningTier enum caps at DIAMOND, no netherite-or-above rung).

Registration keys on the tag, not a concrete item id (unlike Allthemodium, whose ingot ids are
public): each generated material's neoforge:conditions carries neoforge:mod_loaded("elementarium")
for existence, plus {"type": "forgeweave:compat_toggle", "toggle": "elementariumMaterials"}
(ForgeweaveConfigCondition, shared with #995's four processing-mod toggles) for the
elementariumMaterials toggle -- D-M8-5's one deliberate exception, since these presets are generated
rather than hand-authored. A first attempt at this toggle (ElementariumEnabledCondition, a dedicated
condition that called ForgeweaveConfig.enabled directly) never actually gated anything: a datapack
registry loads before ForgeweaveConfig.loaded() is ever true on any boot, so that call always took
the permissive "spec not loaded" branch. ForgeweaveConfigCondition is the real fix -- see its own
javadoc -- and this script emits its condition instead. crafting_items/repair_item key on the
c:ingots/<id> tag itself (the "obtainability gate", Material.java's own term): a material still
registers if the tag turns out empty for a given element, it just never resolves a craft, matching
Material.LENIENT_INGREDIENT_CODEC.

Roster and interpolation rule. Elementarium adds all 118 periodic-table elements, but nowhere near
all of them are metals with a plausible tool-part ingot (noble gases, halogens, etc. are not), and
nothing public ranks their relative in-game power beyond two data fields: atomic number and mining
tier (capped at DIAMOND). Rather than guess at a huge roster this script cannot verify, it ships a
small, hand-curated set of real transition/refractory metals that (a) are not already a Forgeweave
material id under any other integration and (b) plausibly exist as ingot items in a "the whole
periodic table" mod: vanadium, chromium, molybdenum, palladium, hafnium, tantalum.

Every numeric stat is a linear interpolation between two materials this repository already ships
and anchors its own stat curve on: iron.json (a common-metal floor, fraction 0.0) and tungsten.json
(a diamond-tier ceiling already used by another real-world-metal Track A preset, fraction 1.0). The
interpolation parameter is the element's atomic number, clamped to [23, 73] -- vanadium (23) to
tantalum (73), the curated roster's own bounds -- and normalized to [0.0, 1.0] against that range.
incorrect_for_tool is a two-bucket split on the same fraction (iron-tier below the midpoint,
diamond-tier at or above it) rather than a third tag, since nothing in Elementarium's own public
data ever asks for more than diamond. This rule is deliberately simple and is the whole rule: no
hidden per-element overrides. A retune means editing ELEMENTS below and rerunning this script, which
shows up as a real diff rather than silent drift.

No plating/maille block: Elementarium provides no armor-flavored progression data to interpolate
from, so these presets are tool-only, the same scope the graphite.json precedent (a real material
with no plausible armor use) already established for a metal with nothing to say about armor.

Usage: python3 scripts/generate_elementarium_materials.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MATERIAL_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/material"

# (element_id, atomic_number, hex_color, trait_id) -- the curated roster. See this script's module
# docstring for why these six and not the full 118-element table. Each trait id is a distinct,
# previously-unassigned Forgeweave trait constant (issue #876's dedupe policy: no two materials may
# share a non-exempt trait id) rather than a thematically "correct" one -- picked from the pool
# ForgeweaveTraits.REGISTRY already has behavior for but no material names yet.
ELEMENTS = [
    ("vanadium", 23, "#8A8F94", "emberwake"),
    ("chromium", 24, "#B8C4C8", "fertilizing"),
    ("molybdenum", 42, "#8C9296", "obsidian_heart"),
    ("palladium", 46, "#CED0CE", "smokehouse"),
    ("hafnium", 72, "#4F5D63", "surging2"),
    ("tantalum", 73, "#3B4750", "warbond"),
]

ATOMIC_NUMBER_MIN = 23  # vanadium
ATOMIC_NUMBER_MAX = 73  # tantalum

# The two in-repo anchors (see docstring). Kept as plain literals rather than read from the shipped
# JSON, so this script has no runtime dependency on file layout beyond where it writes.
IRON_ANCHOR = {
    "durability": 204, "mining_speed": 6.0, "attack_damage": 4.0,
    "handle_durability_modifier": 0.85, "handle_durability": 60, "extra_durability": 50,
    "bow_drawspeed": 0.5, "bow_range": 1.5, "bow_bonus_damage": 7.0,
    "enchantability": 14,
}
TUNGSTEN_ANCHOR = {
    "durability": 620, "mining_speed": 5.4, "attack_damage": 6.0,
    "handle_durability_modifier": 1.15, "handle_durability": 30, "extra_durability": 30,
    "bow_drawspeed": 0.35, "bow_range": 1.4, "bow_bonus_damage": 6.0,
    "enchantability": 10,
}


def lerp(low: float, high: float, fraction: float) -> float:
    return low + (high - low) * fraction


def round2(value: float) -> float:
    return round(value, 2)


def material_json(element_id: str, atomic_number: int, color: str, trait: str) -> dict:
    clamped = max(ATOMIC_NUMBER_MIN, min(ATOMIC_NUMBER_MAX, atomic_number))
    fraction = (clamped - ATOMIC_NUMBER_MIN) / (ATOMIC_NUMBER_MAX - ATOMIC_NUMBER_MIN)

    tier_tag = "minecraft:incorrect_for_diamond_tool" if fraction >= 0.5 else "minecraft:incorrect_for_iron_tool"

    return {
        "head": {
            "durability": round(lerp(IRON_ANCHOR["durability"], TUNGSTEN_ANCHOR["durability"], fraction)),
            "mining_speed": round2(lerp(IRON_ANCHOR["mining_speed"], TUNGSTEN_ANCHOR["mining_speed"], fraction)),
            "attack_damage": round2(lerp(IRON_ANCHOR["attack_damage"], TUNGSTEN_ANCHOR["attack_damage"], fraction)),
        },
        "handle": {
            "durability_modifier": round2(lerp(IRON_ANCHOR["handle_durability_modifier"],
                                                TUNGSTEN_ANCHOR["handle_durability_modifier"], fraction)),
            "durability": round(lerp(IRON_ANCHOR["handle_durability"], TUNGSTEN_ANCHOR["handle_durability"],
                                      fraction)),
        },
        "extra_durability": round(lerp(IRON_ANCHOR["extra_durability"], TUNGSTEN_ANCHOR["extra_durability"],
                                        fraction)),
        "bow": {
            "drawspeed": round2(lerp(IRON_ANCHOR["bow_drawspeed"], TUNGSTEN_ANCHOR["bow_drawspeed"], fraction)),
            "range": round2(lerp(IRON_ANCHOR["bow_range"], TUNGSTEN_ANCHOR["bow_range"], fraction)),
            "bonus_damage": round2(lerp(IRON_ANCHOR["bow_bonus_damage"], TUNGSTEN_ANCHOR["bow_bonus_damage"],
                                         fraction)),
        },
        "incorrect_for_tool": tier_tag,
        "traits": {"general": [f"forgeweave:{trait}"]},
        "crafting_items": [
            {"ingredient": {"tag": f"c:ingots/{element_id}"}, "value": 144},
            {"ingredient": {"tag": f"c:nuggets/{element_id}"}, "value": 16},
            {"ingredient": {"tag": f"c:storage_blocks/{element_id}"}, "value": 1296},
        ],
        "repair_item": {"tag": f"c:ingots/{element_id}"},
        "cast_only": True,
        "enchantability": round(lerp(IRON_ANCHOR["enchantability"], TUNGSTEN_ANCHOR["enchantability"], fraction)),
        "color": color,
        "neoforge:conditions": [
            {"type": "neoforge:mod_loaded", "modid": "elementarium"},
            {"type": "forgeweave:compat_toggle", "toggle": "elementariumMaterials"},
        ],
    }


def main() -> None:
    MATERIAL_DIR.mkdir(parents=True, exist_ok=True)
    for element_id, atomic_number, color, trait in ELEMENTS:
        path = MATERIAL_DIR / f"elementarium_{element_id}.json"
        path.write_text(json.dumps(material_json(element_id, atomic_number, color, trait), indent=2) + "\n")
        print(f"wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
