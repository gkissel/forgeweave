"""Fill common-tag melting gaps for supported metals without replacing existing recipes."""

import json
from pathlib import Path

from _compat_smeltery_data import melt_temperature

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src/main/resources/data/forgeweave/forgeweave"
ORE_METALS = {
    "aluminium", "copper", "gold", "iesnium", "iridium", "iron", "lead", "nickel",
    "osmium", "platinum", "silver", "tin", "titanium", "tungsten", "uranium",
}
FORMS = {
    "dust": ("dusts", 144, False),
    "small_dust": ("small_dusts", 36, False),
    "tiny_dust": ("tiny_dusts", 16, False),
}
ORE_FORMS = {
    "ore": ("ores", 144, True),
    "raw": ("raw_materials", 144, True),
    "raw_block": ("storage_blocks", 1296, True),
    "clump": ("clumps", 144, False),
    "shard": ("shards", 144, False),
    "crystal": ("crystals", 144, False),
    "dirty_dust": ("dirty_dusts", 144, False),
}


def main():
    count = 0
    existing = {
        (recipe["fluid"], json.dumps(recipe["input"], sort_keys=True))
        for path in (DATA / "melting_recipe").glob("*.json")
        for recipe in [json.loads(path.read_text())]
    }
    for ingot in sorted((DATA / "melting_recipe").glob("*_ingot.json")):
        name = ingot.stem.removesuffix("_ingot")
        material_path = DATA / "material" / f"{name}.json"
        if not material_path.exists() and name != "gold":
            continue
        material = json.loads(material_path.read_text()) if material_path.exists() else {"cast_only": True}
        if not material.get("cast_only"):
            continue
        base = json.loads(ingot.read_text())
        if base["fluid"] != f"forgeweave:molten_{name}":
            continue
        forms = FORMS | (ORE_FORMS if name in ORE_METALS else {})
        for suffix, (tag, amount, ore) in forms.items():
            target = DATA / "melting_recipe" / f"{name}_{suffix}.json"
            if target.exists():
                continue
            tag_name = f"raw_{name}" if suffix == "raw_block" else name
            recipe = {"input": {"tag": f"c:{tag}/{tag_name}"}, "fluid": base["fluid"], "amount": amount}
            key = (base["fluid"], json.dumps(recipe["input"], sort_keys=True))
            if key in existing:
                continue
            if ore:
                recipe["ore"] = True
            if "temperature" in base:
                recipe["temperature"] = base["temperature"]
            if "neoforge:conditions" in base:
                recipe["temperature"] = melt_temperature(name)
                recipe["neoforge:conditions"] = base["neoforge:conditions"]
            target.write_text(json.dumps(recipe, indent=2) + "\n")
            existing.add(key)
            count += 1
    print(f"wrote {count} missing melting forms")


if __name__ == "__main__":
    main()
