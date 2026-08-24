#!/usr/bin/env python3
"""Generates the Ponder scene schematics (issue #664).

Ponder (net.createmod.ponder, the standalone library extracted from Create) loads a scene's world
from a gzipped vanilla structure-template NBT at ``assets/<namespace>/ponder/<path>.nbt``
(``PonderSceneRegistry.loadSchematic``). Upstream authors capture these in-game with a structure
block; Forgeweave's smeltery scene is small and exactly specified by ``SmelteryScan`` (and pinned by
``SmelteryGameTests``), so this script writes it directly instead -- rerun it after changing a
layout below and commit the output.

The smeltery schematic is the *finished* minimum structure (the scene reveals it in stages):

* y0 -- a 5x5 checkered base plate (Ponder's own convention, see its debug scenes: the plate is part
  of the schematic, shown by ``scene.showBasePlate()``).
* y1 -- a 3x3 seared-brick base: the scan only requires the floor block under the interior column,
  the outer ring is how a player would actually build it (allowed -- the scan never looks there).
* y2/y3 -- the wall rings around the 1x1x2 interior, corners included (``SmelteryScan`` never checks
  corner columns), with one seared tank mid-east and the standard core mid-south facing outward.

``PonderSchematicGameTests`` rebuilds this exact NBT server-side and asserts the scan accepts it.
"""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT = REPO_ROOT / "src/main/resources/assets/forgeweave/ponder/smeltery.nbt"

DATA_VERSION = 3955  # 1.21.1; Ponder loads the template without datafixing, so this must be current.

SNOW = {"Name": "minecraft:snow_block"}
CONCRETE = {"Name": "minecraft:white_concrete"}
BRICKS = {"Name": "forgeweave:seared_bricks"}
TANK = {"Name": "forgeweave:seared_tank"}
TOOL_STATION = {"Name": "forgeweave:tool_station", "Properties": {"facing": "south"}}
CORE = {
    "Name": "forgeweave:standard_core",
    # FACING points out of the structure (south wall -> south); the scene flips ACTIVE on as its
    # completion cue, so the schematic stores the unformed state.
    "Properties": {"facing": "south", "active": "false"},
}


def smeltery_blocks() -> tuple[list[dict], list[dict]]:
    """Palette and block list for the smeltery scene, mirroring SmelteryGameTests.buildWalls."""
    palette: list[dict] = []
    blocks: list[dict] = []

    def place(x: int, y: int, z: int, state: dict) -> None:
        if state not in palette:
            palette.append(state)
        blocks.append({"pos": [x, y, z], "state": palette.index(state)})

    # y0: the 5x5 checkered base plate.
    for x in range(5):
        for z in range(5):
            place(x, 0, z, SNOW if (x + z) % 2 == 0 else CONCRETE)
    # y1: the 3x3 seared base under the walls and interior.
    for x in range(1, 4):
        for z in range(1, 4):
            place(x, 1, z, BRICKS)
    # y2/y3: wall rings around the interior column at (2, y, 2).
    for y in (2, 3):
        for x in range(1, 4):
            for z in range(1, 4):
                if x == 2 and z == 2:
                    continue  # the interior
                if y == 2 and x == 2 and z == 3:
                    place(x, y, z, CORE)  # mid-south, facing the default camera
                elif y == 2 and x == 3 and z == 2:
                    place(x, y, z, TANK)  # mid-east
                else:
                    place(x, y, z, BRICKS)
    return palette, blocks


# --- minimal big-endian NBT writer (stdlib only, same idiom as the other scripts/ generators) ---


def _tag(value) -> int:
    if isinstance(value, int):
        return 3  # TAG_Int
    if isinstance(value, str):
        return 8
    if isinstance(value, list):
        return 9
    if isinstance(value, dict):
        return 10
    raise TypeError(f"unsupported NBT value: {value!r}")


def _payload(value) -> bytes:
    if isinstance(value, int):
        return struct.pack(">i", value)
    if isinstance(value, str):
        raw = value.encode()
        return struct.pack(">H", len(raw)) + raw
    if isinstance(value, list):
        element = _tag(value[0]) if value else 0
        return struct.pack(">bi", element, len(value)) + b"".join(_payload(v) for v in value)
    if isinstance(value, dict):
        out = b""
        for k, v in value.items():
            raw = k.encode()
            out += struct.pack(">bH", _tag(v), len(raw)) + raw + _payload(v)
        return out + b"\x00"
    raise TypeError(f"unsupported NBT value: {value!r}")


def write_structure(path: Path, size: list[int], palette: list[dict], blocks: list[dict]) -> None:
    root = {
        "size": size,
        "entities": [],
        "blocks": blocks,
        "palette": palette,
        "DataVersion": DATA_VERSION,
    }
    payload = b"\x0a\x00\x00" + _payload(root)  # unnamed root compound
    path.parent.mkdir(parents=True, exist_ok=True)
    # mtime=0 keeps the gzip output byte-stable across reruns.
    path.write_bytes(gzip.compress(payload, mtime=0))
    print(f"wrote {path.relative_to(REPO_ROOT)} ({len(blocks)} blocks)")


def tool_station_blocks() -> tuple[list[dict], list[dict]]:
    """The armor assembly scene (#682): a Tool Station alone in the middle of the base plate."""
    palette: list[dict] = [SNOW, CONCRETE, TOOL_STATION]
    blocks: list[dict] = []
    for x in range(5):
        for z in range(5):
            blocks.append({"pos": [x, 0, z], "state": 0 if (x + z) % 2 == 0 else 1})
    blocks.append({"pos": [2, 1, 2], "state": 2})
    return palette, blocks


def main() -> None:
    palette, blocks = smeltery_blocks()
    write_structure(OUTPUT, [5, 4, 5], palette, blocks)
    palette, blocks = tool_station_blocks()
    write_structure(OUTPUT.with_name("tool_station.nbt"), [5, 2, 5], palette, blocks)


if __name__ == "__main__":
    main()
