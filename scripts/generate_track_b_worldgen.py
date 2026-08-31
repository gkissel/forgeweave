#!/usr/bin/env python3
"""Generates Track B's ore worldgen JSON (issue #839, epic #824): one configured_feature + placed_feature
pair per material plus the two biome_modifier files, from the distribution table also encoded in
dev.gkissel.forgeweave.trackb.TrackBOre (keep the two in sync by hand -- this script has no Java
parser available at datagen time, same reason the block/item registration lives in Java instead of
being generated from this table). See TrackBOre's own javadoc for the distribution-table rationale.

Usage: python3 scripts/generate_track_b_worldgen.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORLDGEN = ROOT / "src/main/resources/data/forgeweave/worldgen"
BIOME_MODIFIERS = ROOT / "src/main/resources/data/forgeweave/neoforge/biome_modifier"

# (id, target_block, vein_size, rate_per_chunk, min_y, max_y) -- must match TrackBOre.ALL.
ORES = [
    ("cinderstone", "minecraft:stone", 6, 12, 0, 128),
    ("fulmenite", "minecraft:deepslate", 5, 6, -24, 32),
    ("duskspar", "minecraft:deepslate", 4, 3, -64, -16),
    ("voltcinder", "minecraft:netherrack", 4, 6, 0, 127),
    ("murkiron", "minecraft:deepslate", 4, 3, -64, -16),
    ("hardcinder", "minecraft:netherrack", 4, 6, 0, 127),
    ("nightshale", "minecraft:deepslate", 4, 3, -64, -16),
    ("warspar", "minecraft:deepslate", 4, 3, -64, -16),
    ("hollowstone", "minecraft:deepslate", 4, 3, -64, -16),
    ("resonite", "minecraft:deepslate", 4, 3, -64, -16),
    ("starfall_stone", "minecraft:stone", 3, 1, 62, 90),
    ("voidglass", "minecraft:deepslate", 3, 1, -64, -48),
]
NETHER_IDS = {"voltcinder", "hardcinder"}


def write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2) + "\n")
    print(f"wrote {path.relative_to(ROOT)}")


def main() -> None:
    (WORLDGEN / "configured_feature").mkdir(parents=True, exist_ok=True)
    (WORLDGEN / "placed_feature").mkdir(parents=True, exist_ok=True)
    BIOME_MODIFIERS.mkdir(parents=True, exist_ok=True)

    overworld_features = []
    nether_features = []

    for ore_id, target, size, rate, min_y, max_y in ORES:
        configured = {
            "type": "minecraft:ore",
            "config": {
                "targets": [
                    {
                        "target": {"predicate_type": "minecraft:block_match", "block": target},
                        "state": {"Name": f"forgeweave:{ore_id}_ore"},
                    }
                ],
                "size": size,
                "discard_chance_on_air_exposure": 0.0,
            },
        }
        write_json(WORLDGEN / "configured_feature" / f"{ore_id}_ore.json", configured)

        placed = {
            "feature": f"forgeweave:{ore_id}_ore",
            "placement": [
                {"type": "forgeweave:track_b_ore_rate", "count": rate},
                {"type": "minecraft:in_square"},
                {
                    "type": "minecraft:height_range",
                    "height": {
                        "type": "minecraft:uniform",
                        "min_inclusive": {"absolute": min_y},
                        "max_inclusive": {"absolute": max_y},
                    },
                },
            ],
        }
        write_json(WORLDGEN / "placed_feature" / f"{ore_id}_ore.json", placed)

        (nether_features if ore_id in NETHER_IDS else overworld_features).append(f"forgeweave:{ore_id}_ore")

    write_json(BIOME_MODIFIERS / "track_b_overworld_ores.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": overworld_features,
        "step": "underground_ores",
    })
    write_json(BIOME_MODIFIERS / "track_b_nether_ores.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_nether",
        "features": nether_features,
        "step": "underground_ores",
    })


if __name__ == "__main__":
    main()
