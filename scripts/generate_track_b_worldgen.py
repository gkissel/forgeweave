#!/usr/bin/env python3
"""Generates Track B's ore worldgen JSON (issue #839, epic #824): one configured_feature + placed_feature
pair per material plus the two biome_modifier files, and (issue #903) the same pair for the one
standalone non-Track-B ore that follows their shape -- brimspar, the Nether fuel ore -- from the
distribution table also encoded in
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

# (id, target_block, vein_size, rate_per_chunk, min_y, max_y) -- must match TrackBOre.ALL. Issue
# #884 (1) removed "cinderstone": basalt replaces it and is not an ore (no worldgen presence).
# Issue #883 moved voidglass to the End (end_stone host, full column, its own four-biome modifier)
# and bumped starfall_stone's rate to 2; that landed as hand edits to the JSON and left this table
# stale until #903 re-ran the script and caught the drift. The table is the source of truth again.
#
# Issue #909 (maintainer directive, 2026-09-02, out of the reference-ladder parity audit) re-homed
# eight of the eleven so each ore generates in the dimension its reference counterpart does: three in
# the Nether, four in the End (voidglass among them -- #883 is the one deliberate departure, the
# reference puts it in the Overworld), the remaining four in the Overworld. The *target block* column
# is what decides an ore's dimension (see DIMENSION_BY_HOST below), so re-homing an ore is a one-cell
# edit here. Heights and rates are Forgeweave's own: the reference's 1.12 y bands are a guide only,
# since 1.21's Nether/End columns and its vein-count semantics both differ. Rows group by dimension.
ORES = [
    # Nether (netherrack, y 0-127). fulmenite is the roster's entry ore and stays the most common of
    # the eleven; warspar spans nearly the whole column but is the rarest Nether row, since it
    # anchors the second-from-top mining rung.
    ("fulmenite", "minecraft:netherrack", 5, 6, 10, 108),
    ("murkiron", "minecraft:netherrack", 4, 3, 8, 64),
    ("warspar", "minecraft:netherrack", 4, 2, 0, 120),
    # End (end_stone, outer islands only -- see END_BIOMES). duskspar and nightshale take narrow
    # bands inside the islands' own body; hollowstone spans it; voidglass keeps #883's full column
    # and rate 1, still the game's uniquely rarest ore.
    ("duskspar", "minecraft:end_stone", 4, 3, 32, 56),
    ("nightshale", "minecraft:end_stone", 4, 3, 44, 72),
    ("hollowstone", "minecraft:end_stone", 4, 2, 0, 96),
    ("voidglass", "minecraft:end_stone", 3, 1, 0, 255),
    # Overworld. voltcinder is the deepest and rarest of the four: the reference replaces bedrock
    # with it at the very bottom of its world, and the bottom 16 blocks of the deepslate column is
    # this generation's equivalent of that band.
    ("hardcinder", "minecraft:deepslate", 4, 4, -48, 16),
    ("voltcinder", "minecraft:deepslate", 4, 2, -64, -48),
    ("resonite", "minecraft:deepslate", 4, 3, -64, -16),
    ("starfall_stone", "minecraft:stone", 3, 2, 62, 90),
]

# Which dimension a host block puts an ore in (issue #909). Derived from the table above rather than
# kept as a second per-ore id list, so the two can never drift: an ore's host rock and its dimension
# are one decision, not two.
DIMENSION_BY_HOST = {
    "minecraft:stone": "overworld",
    "minecraft:deepslate": "overworld",
    "minecraft:netherrack": "nether",
    "minecraft:end_stone": "end",
}
# #883: the End's outer-island biomes only, never the central dragon island -- see TrackBOre.Host.END.
END_BIOMES = ["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens",
              "minecraft:small_end_islands"]

# Issue #903: brimspar is a Nether *fuel* ore, not a Track B tool material -- it has no ingot, nugget,
# raw item, storage block, molten-metal tool fluid or TrackBOre entry, so it stays out of ORES above
# (which must mirror TrackBOre.ALL exactly). Its worldgen files are the same shape as every row there,
# just emitted from this second table and carried by their own biome modifier rather than
# track_b_nether_ores.json -- the same "its own biome modifier" treatment cobalt/ardite already get.
# Rarer than voltcinder/hardcinder's 6 veins per chunk (rate 3): one vein of it is worth a lot of
# smeltery uptime, and every one of them is a coin flip on blowing up in your face.
STANDALONE_ORES = [
    ("brimspar", "minecraft:netherrack", 4, 3, 0, 127),
]


def write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2) + "\n")
    print(f"wrote {path.relative_to(ROOT)}")


def write_ore_pair(ore_id: str, target: str, size: int, min_y: int, max_y: int, count_modifier: dict) -> None:
    """One configured_feature + placed_feature pair. `count_modifier` is the placement modifier that
    answers "how many veins per chunk" -- Track B's config-aware `forgeweave:track_b_ore_rate` for the
    roster above, plain `minecraft:count` for anything outside it."""
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
            count_modifier,
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


def main() -> None:
    (WORLDGEN / "configured_feature").mkdir(parents=True, exist_ok=True)
    (WORLDGEN / "placed_feature").mkdir(parents=True, exist_ok=True)
    BIOME_MODIFIERS.mkdir(parents=True, exist_ok=True)

    features = {"overworld": [], "nether": [], "end": []}

    for ore_id, target, size, rate, min_y, max_y in ORES:
        write_ore_pair(ore_id, target, size, min_y, max_y,
                       {"type": "forgeweave:track_b_ore_rate", "count": rate})
        features[DIMENSION_BY_HOST[target]].append(f"forgeweave:{ore_id}_ore")

    for ore_id, target, size, rate, min_y, max_y in STANDALONE_ORES:
        write_ore_pair(ore_id, target, size, min_y, max_y, {"type": "minecraft:count", "count": rate})
        write_json(BIOME_MODIFIERS / f"{ore_id}_ore.json", {
            "type": "neoforge:add_features",
            "biomes": "#minecraft:is_nether",
            "features": [f"forgeweave:{ore_id}_ore"],
            "step": "underground_ores",
        })

    write_json(BIOME_MODIFIERS / "track_b_overworld_ores.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": features["overworld"],
        "step": "underground_ores",
    })
    write_json(BIOME_MODIFIERS / "track_b_nether_ores.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_nether",
        "features": features["nether"],
        "step": "underground_ores",
    })
    write_json(BIOME_MODIFIERS / "track_b_end_ores.json", {
        "type": "neoforge:add_features",
        "biomes": END_BIOMES,
        "features": features["end"],
        "step": "underground_ores",
    })


if __name__ == "__main__":
    main()
