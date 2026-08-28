"""Generates the Ponder scene schematics (issues #664, #682, #700, #754).

Ponder (net.createmod.ponder, the standalone library extracted from Create) loads a scene's world
from a gzipped vanilla structure-template NBT at ``assets/<namespace>/ponder/<path>.nbt``
(``PonderSceneRegistry.loadSchematic``). Upstream authors capture these in-game with a structure
block; Forgeweave's scenes are small and exactly specified by ``SmelteryScan`` (and pinned by
``SmelteryGameTests``), so this script writes them directly instead -- rerun it after changing a
layout below and commit the output.

**Orientation (#700).** Ponder's default camera (``PonderScene.SceneTransform``: yaw 145, pitch
-35, no mirroring) looks at the scene from the north-west, so a block's NORTH and WEST faces are
the ones a player sees. Every directional block below therefore sits in a north or west wall,
facing out of it; ``PonderSceneWiringTest`` pins each facing.

Every schematic starts with Ponder's checkered base plate at y0 (its own convention, see its debug
scenes: the plate is part of the schematic, shown by ``scene.showBasePlate()``), and every
smeltery in them is the *finished* structure the scene reveals in stages: a seared floor under the
interior footprint plus the outer ring a player would actually build (allowed -- the scan never
looks there), wall rings corners included (``SmelteryScan`` never checks corner columns), at least
one seared tank, and a core stored inactive (the scene flips ``active`` as its completion cue). #754:
a wall that is otherwise all ``forgeweave:seared_bricks`` also carries a drain and a seared glass
pane, so the multiblock actually shows some of the distinct blocks its callouts describe rather than
a uniform brick box with two special cells.

``PonderSchematicGameTests`` rebuilds every smeltery here server-side and asserts the scan accepts it.
"""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = REPO_ROOT / "src/main/resources/assets/forgeweave/ponder"

DATA_VERSION = 3955  # 1.21.1; Ponder loads the template without datafixing, so this must be current.

SNOW = {"Name": "minecraft:snow_block"}
CONCRETE = {"Name": "minecraft:white_concrete"}
BRICKS = {"Name": "forgeweave:seared_bricks"}
TANK = {"Name": "forgeweave:seared_tank"}
GLASS = {"Name": "forgeweave:seared_glass"}
ARMOR_STATION = {"Name": "forgeweave:armor_station", "Properties": {"facing": "south"}}
CASTING_TABLE = {"Name": "forgeweave:casting_table"}
CASTING_BASIN = {"Name": "forgeweave:casting_basin"}


def core(facing: str) -> dict:
    return {"Name": "forgeweave:standard_core", "Properties": {"facing": facing, "active": "false"}}


def drain(facing: str) -> dict:
    return {"Name": "forgeweave:seared_drain", "Properties": {"facing": facing}}


def faucet(facing: str) -> dict:
    """``facing`` is the faucet's *input* side (upstream ``BlockFaucet.FACING``), never down."""
    return {"Name": "forgeweave:faucet", "Properties": {"facing": facing}}


def channel(down: bool = False, **sides: str) -> dict:
    """``sides`` are ``north``/``south``/``west``/``east`` -> ``in``/``out``; the rest default to ``none``."""
    return {"Name": "forgeweave:seared_channel", "Properties": {"down": str(down).lower(), **sides}}


class Structure:
    def __init__(self, size: tuple[int, int, int]) -> None:
        self.size = list(size)
        self.palette: list[dict] = []
        self.blocks: list[dict] = []

    def place(self, x: int, y: int, z: int, state: dict) -> None:
        if state not in self.palette:
            self.palette.append(state)
        self.blocks.append({"pos": [x, y, z], "state": self.palette.index(state)})

    def base_plate(self) -> None:
        for x in range(self.size[0]):
            for z in range(self.size[2]):
                self.place(x, 0, z, SNOW if (x + z) % 2 == 0 else CONCRETE)

    def smeltery(self, x0: int, z0: int, width: int, depth: int, height: int, special: dict[tuple[int, int, int], dict]) -> None:
        """A smeltery whose *interior* starts at (x0, 1+1, z0) and spans width x depth x height.

        The floor (y1) and wall rings (y2..) cover the interior plus a one-block ring; ``special``
        swaps individual wall positions (core, tank, drain) for other blocks.
        """
        for x in range(x0 - 1, x0 + width + 1):
            for z in range(z0 - 1, z0 + depth + 1):
                self.place(x, 1, z, BRICKS)
        for y in range(2, 2 + height):
            for x in range(x0 - 1, x0 + width + 1):
                for z in range(z0 - 1, z0 + depth + 1):
                    if x0 <= x < x0 + width and z0 <= z < z0 + depth:
                        continue  # the interior
                    self.place(x, y, z, special.get((x, y, z), BRICKS))


def smeltery_scene() -> Structure:
    """The assembly scene (#664): the minimum structure, 1x1x2 interior at (2, 2..3, 2) on a 5x5 plate.

    #754: a drain and a seared glass pane sit above the core and tank so the structure actually shows
    the range of blocks the callouts and the item preview describe, instead of a wall that is all
    seared bricks apart from the one tank and one core cell.
    """
    s = Structure((5, 4, 5))
    s.base_plate()
    s.smeltery(2, 2, 1, 1, 2, {
        (2, 2, 1): core("north"),  # mid-north, facing the default camera
        (1, 2, 2): TANK,  # mid-west, the other face the camera sees
        (2, 3, 1): drain("north"),  # top layer, mid-north, above the core
        (1, 3, 2): GLASS,  # top layer, mid-west, above the tank
    })
    return s


def smeltery_sizes_scene() -> Structure:
    """The size-variants scene (#700): the smallest smeltery and a 3x3x3 one side by side on a 9x9 plate.

    The small one sits south-west, the large north-east, so neither stands between the other and
    the north-west camera.
    """
    s = Structure((9, 5, 9))
    s.base_plate()
    # The small one stays exactly "one tank, one core" -- that is what its own callout says. The large
    # one also gets a drain and a seared glass pane (#754) so the scene shows the fuller range of
    # valid wall blocks somewhere, not just seared bricks everywhere but two cells.
    s.smeltery(2, 6, 1, 1, 2, {
        (2, 2, 5): core("north"),
        (1, 2, 6): TANK,
    })
    s.smeltery(5, 1, 3, 3, 3, {
        (6, 2, 0): core("north"),
        (4, 3, 2): TANK,
        (7, 3, 0): drain("north"),  # north wall, mid layer
        (4, 2, 1): GLASS,  # west wall, bottom layer
    })
    return s


# The casting scene's directional blocks, shared with PonderSchematicGameTests' expectations through
# PonderSceneWiringTest: a 1x1x3 smeltery in the south-east, its drain mid-north, the faucet on the
# drain, a channel fork below it running west to a casting table and north to a casting basin.
CASTING_CORE = (4, 2, 5)
CASTING_DRAIN = (5, 3, 4)
CASTING_FAUCET = (5, 3, 3)
CASTING_TABLE_POS = (3, 1, 3)
CASTING_BASIN_POS = (5, 1, 2)


def casting_scene() -> Structure:
    s = Structure((7, 5, 7))
    s.base_plate()
    s.smeltery(5, 5, 1, 1, 3, {
        CASTING_CORE: core("west"),  # mid-west wall, facing the camera
        CASTING_DRAIN: drain("north"),  # mid-north wall, one up from the floor
        (5, 4, 4): TANK,  # the wall block above the drain
    })
    s.place(*CASTING_FAUCET, faucet("south"))  # its input is the drain behind it
    s.place(5, 2, 3, channel(west="out", north="out"))  # the fork under the faucet
    s.place(4, 2, 3, channel(east="in", west="out"))
    s.place(3, 2, 3, channel(down=True, east="in"))  # pours down into the table
    s.place(*CASTING_TABLE_POS, CASTING_TABLE)
    s.place(5, 2, 2, channel(down=True, south="in"))  # pours down into the basin
    s.place(*CASTING_BASIN_POS, CASTING_BASIN)
    return s


def armor_station_scene() -> Structure:
    """The armor assembly scene (#682, moved by #782): an Armor Station alone on the base plate."""
    s = Structure((5, 2, 5))
    s.base_plate()
    s.place(2, 1, 2, ARMOR_STATION)
    return s


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


def write_structure(name: str, structure: Structure) -> None:
    root = {
        "size": structure.size,
        "entities": [],
        "blocks": structure.blocks,
        "palette": structure.palette,
        "DataVersion": DATA_VERSION,
    }
    payload = b"\x0a\x00\x00" + _payload(root)  # unnamed root compound
    path = OUTPUT_DIR / f"{name}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    # mtime=0 keeps the gzip output byte-stable across reruns.
    path.write_bytes(gzip.compress(payload, mtime=0))
    print(f"wrote {path.relative_to(REPO_ROOT)} ({len(structure.blocks)} blocks)")


def main() -> None:
    write_structure("smeltery", smeltery_scene())
    write_structure("smeltery_sizes", smeltery_sizes_scene())
    write_structure("casting", casting_scene())
    write_structure("armor_station", armor_station_scene())


if __name__ == "__main__":
    main()
