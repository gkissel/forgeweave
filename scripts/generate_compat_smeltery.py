#!/usr/bin/env python3
"""Generates issue #873's compat-metal smeltery rows (M6 epic #824, JC3 reversal): melting recipes,
casting recipes and the cast_only flip for every Track A / recovery compat metal, plus melting/
casting for the three new PlusTiC-inspiration alloys (alumite, osgloglas, osmiridium). Sibling to
scripts/generate_track_b_recipes.py -- same 73-file cobalt casting template, same clone-by-substitution
approach -- extended with per-recipe `neoforge:conditions` (compat metals are gated; Track B never was)
and, for compat metals only, a concrete-item-id fix on the ingot casting row (see CONCRETE_INGOT below).

Casting scope note (disclosed, not silent): a compat metal's tool-part casting rows (68 of the 73
template files -- pickaxe head, sword blade, plating, etc.) all cast into Forgeweave's own generic part
items, so they clone unchanged. Only the ingot row casts into the *provider's* own item, and only that
one concrete id is independently verified (it is the same id the material's own `neoforge:item_exists`
condition already names). Nugget and block casting are cut from this pass: Forgeweave cannot safely
name a concrete nugget/block item for ~13 different provider mods without the per-mod verification
PR #874 did for its ten materials, and shipping a guessed id risks a hard decode failure the moment
that provider is actually installed. Melting still accepts nugget/block forms (tag-keyed, safe) --
only the *casting-output* side is narrowed. Follow-up if a maintainer wants it filled in per mod.

Usage: python3 scripts/generate_compat_smeltery.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _compat_smeltery_data import build_table, melt_temperature, METALS  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
MATERIAL_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/material"
CASTING_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/casting_recipe"
MELTING_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/melting_recipe"

# materials whose condition is a `neoforge:or` across more than one provider (JC2): the ingot casting
# row needs one file per provider instead of one file naming an id that might not be the one installed.
OR_MATERIALS = {"lead", "uranium"}

# The three new alumite/osgloglas/osmiridium alloys: Forgeweave-owned ingot/nugget/block items (added
# to dev.gkissel.forgeweave.trackb.TrackBAlloy.ALL), so their full 73-file casting template needs no
# result-id surgery -- only the condition injection every compat recipe gets.
ALLOYS = {
    "alumite": [{"type": "neoforge:item_exists", "item": "immersiveengineering:ingot_aluminum"}],
    "osgloglas": [
        {"type": "neoforge:item_exists", "item": "mekanism:ingot_osmium"},
        {"type": "neoforge:item_exists", "item": "mekanism:ingot_refined_obsidian"},
    ],
    "osmiridium": [
        {"type": "neoforge:item_exists", "item": "mekanism:ingot_osmium"},
        {"type": "neoforge:item_exists", "item": "modern_industrialization:iridium_ingot"},
    ],
    # Issue #946: the three Draconic Evolution fusion metals. Same treatment as the three alloys
    # above -- Forgeweave-owned ingot/nugget/block items, so the 73-file casting template clones
    # unchanged and only the condition is injected -- gated on the DE core each metal's fusion
    # recipe consumes. They deliberately get no alloy_recipe row anywhere: a fusion craft on DE's
    # own multiblock is the only thing that makes the ingot, and melting it back down and recasting
    # it is the only loop the smeltery closes.
    "emberweld": [{"type": "neoforge:item_exists", "item": "draconicevolution:wyvern_core"}],
    "starweld": [{"type": "neoforge:item_exists", "item": "draconicevolution:awakened_core"}],
    "voidweld": [{"type": "neoforge:item_exists", "item": "draconicevolution:chaotic_core"}],
}

VALUE_NUGGET = 16
VALUE_INGOT = 144
VALUE_BLOCK = VALUE_INGOT * 9


def write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2) + "\n")


def form_suffix(ingredient: dict) -> str:
    """Melting-recipe filename suffix for a crafting_items ingredient, or "" for a material's one
    and only form (a plain concrete item, or a tag with no ingots/nuggets/storage_blocks/raw_materials
    family segment -- e.g. silicon's bare c:silicon)."""
    tag = ingredient.get("tag")
    if tag is None:
        return ""
    if "ingots/" in tag:
        return "_ingot"
    if "nuggets/" in tag:
        return "_nugget"
    if "storage_blocks/" in tag:
        return "_block"
    if "raw_materials/" in tag:
        return "_raw"
    return ""


def cast_only_flip() -> None:
    """Deliverable 4 (issue #873 scope addition): every compat metal material flips to
    `cast_only: true`, mirroring material/cinderstone.json -- crafting_items stays for repair/reference,
    the Part Builder path is what the flag itself removes (PartBuilderRecipes reads it directly)."""
    count = 0
    for material_id in METALS:
        path = MATERIAL_DIR / f"{material_id}.json"
        data = json.loads(path.read_text())
        if data.get("cast_only"):
            continue
        # Insert right after crafting_items/repair_item, before enchantability -- matches
        # cinderstone.json's own key order.
        ordered = {}
        for key, value in data.items():
            ordered[key] = value
            if key == "repair_item":
                ordered["cast_only"] = True
        if "cast_only" not in ordered:
            ordered["cast_only"] = True
        write_json(path, ordered)
        count += 1
    print(f"flipped cast_only on {count} compat metal materials")


def melting_recipes() -> None:
    table = build_table()
    count = 0
    for material_id in METALS:
        info = table[material_id]
        fluid = f"forgeweave:molten_{material_id}"
        # Issue #954: pinned once per material so every form needs the same fuel, rather than each
        # form deriving its own temperature from its own amount (MeltingRecipe#calcTemperature).
        temperature = melt_temperature(material_id)
        for entry in info["crafting_items"]:
            suffix = form_suffix(entry["ingredient"])
            name = f"{material_id}{suffix}.json"
            write_json(MELTING_DIR / name, {
                "input": entry["ingredient"],
                "fluid": fluid,
                "amount": entry["value"],
                "temperature": temperature,
                "neoforge:conditions": info["condition"],
            })
            count += 1
    print(f"wrote {count} compat metal melting recipes")


ALLOY_FORM_TAGS = {"ingot": "ingots", "nugget": "nuggets", "block": "storage_blocks"}


def alloy_melting_recipes() -> None:
    count = 0
    for alloy_id, condition in ALLOYS.items():
        fluid = f"forgeweave:molten_{alloy_id}"
        temperature = melt_temperature(alloy_id)  # Issue #954, same one-per-material pin as above.
        for form, value in (("ingot", VALUE_INGOT), ("nugget", VALUE_NUGGET), ("block", VALUE_BLOCK)):
            write_json(MELTING_DIR / f"{alloy_id}_{form}.json", {
                "input": {"tag": f"c:{ALLOY_FORM_TAGS[form]}/{alloy_id}"},
                "fluid": fluid,
                "amount": value,
                "temperature": temperature,
                "neoforge:conditions": condition,
            })
            count += 1
    print(f"wrote {count} alloy melting recipes")


def casting_recipes() -> None:
    table = build_table()
    templates = sorted(CASTING_DIR.glob("*_cobalt.json"))
    assert len(templates) == 73, f"expected 73 cobalt casting recipes, found {len(templates)}"

    total = 0
    for material_id in METALS:
        info = table[material_id]
        condition = info["condition"]
        provider_items = info["provider_items"]
        for template in templates:
            out_name = template.name.replace("cobalt", material_id)
            if template.name.startswith(("nugget_cobalt", "block_cobalt", "clay_nugget_cobalt")):
                # Casting scope note above: nugget/block casting cut for compat metals.
                continue
            data = json.loads(template.read_text().replace("cobalt", material_id))
            if template.name.startswith(("ingot_cobalt", "clay_ingot_cobalt")):
                if material_id in OR_MATERIALS:
                    for provider_item in provider_items:
                        provider_mod = provider_item.split(":")[0]
                        variant = dict(data)
                        variant["result"] = {"id": provider_item}
                        variant["neoforge:conditions"] = [{"type": "neoforge:item_exists", "item": provider_item}]
                        variant_name = out_name.replace(f"_{material_id}.json", f"_{material_id}_{provider_mod}.json")
                        write_json(CASTING_DIR / variant_name, variant)
                        total += 1
                    continue
                data["result"] = {"id": provider_items[0]}
                data["neoforge:conditions"] = condition
            else:
                data["neoforge:conditions"] = condition
            write_json(CASTING_DIR / out_name, data)
            total += 1
        print(f"cloned casting recipes for {material_id}")
    print(f"wrote {total} compat metal casting recipes")


def alloy_casting_recipes() -> None:
    templates = sorted(CASTING_DIR.glob("*_cobalt.json"))
    total = 0
    for alloy_id, condition in ALLOYS.items():
        for template in templates:
            out_name = template.name.replace("cobalt", alloy_id)
            data = json.loads(template.read_text().replace("cobalt", alloy_id))
            data["neoforge:conditions"] = condition
            write_json(CASTING_DIR / out_name, data)
            total += 1
        print(f"cloned 73 casting recipes for {alloy_id}")
    print(f"wrote {total} alloy casting recipes")


def main() -> None:
    cast_only_flip()
    melting_recipes()
    alloy_melting_recipes()
    casting_recipes()
    alloy_casting_recipes()


if __name__ == "__main__":
    main()
