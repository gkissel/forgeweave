"""Palette audit for every Forgeweave-owned color (issue #928).

The 2026-09-02 playtest found molten fluids and Track B sprites that no player could tell apart:
two olive-yellow fuel rows, three teal ingots side by side, three purples, three reds. This script
reads every color Forgeweave picks for itself, converts it to OKLab, and reports the pairs that sit
too close together. `src/test/java/dev/gkissel/forgeweave/fluid/PaletteAuditTest.java` runs the same
rules over the same tables inside the build, so a new hex has to clear them before it can land.

Sources read (one hex per material, wherever that material spells its color out):

  * `ForgeweaveFluids.java` -- every `register("name", 0xRRGGBB, temp)` tint.
  * `TrackBOre.java` / `TrackBAlloy.java` -- the Track B flavor hex, which is also what those
    materials' fluids register.
  * `scripts/generate_track_b_ore_textures.py` (`ORES`, `STANDALONE_CRYSTAL_ORES`) and
    `scripts/generate_track_b_alloy_textures.py` (`ALLOYS`) -- the hex the ore/ingot/nugget/block/
    crystal sprites are recolored to.
  * `UnstableOreBlock.BRIMSPAR_CRYSTAL_COLOR` -- brimspar's crystal tint.
  * `data/forgeweave/forgeweave/material/<id>.json` -- the material's text color, for Track B ids.

Out of scope, per the issue: vanilla fluids and the compat-mod fluids that take their color from
another mod's own item. Only tints Forgeweave picks are audited.

Distance is Euclidean in OKLab, written dEok below. For reference, dEok 0.02 is roughly one
just-noticeable step on a large flat area; a 16x16 sprite or a fluid tile needs considerably more
than that before two materials read as two materials.

Rules, in the order the report prints them:

  R0 family sync   -- a material's fluid tint, sprite hex, crystal tint and material JSON color are
                      one hex. A material whose fluid and ingot disagree is one material painted two
                      colors, which is the same bug from the other end.
  R1 floor 0.030   -- no two Forgeweave-owned tints anywhere may be closer than this. This is the
                      "these are the same hex" line; several grey metals sat under it. Two pairs are
                      reported but not enforced: see PARITY_LOCKED.
  R2 Track B 0.085 -- the 33 Track B materials (11 ores, brimspar, 21 alloys) are shown as one block
                      in the creative tab and one ladder in the book, so they get a much wider
                      spacing than the global floor.
  R3 fuels 0.20    -- the five smeltery fuels each get their own hue band, and no two may be closer
                      than 0.20. Blazing blood is the only yellow, lava keeps vanilla orange
                      (maintainer comment on #928, 2026-09-02).
  R4 playtest 0.080 -- the specific pairs the playtest named by hand stay this far apart, whatever
                      else moves around them. They are all cross-group, so no other rule covers them:
                      three teals (draconium, psimetal, emerald), two blues (cobalt, osmium) and
                      three purples (manyullyn, draconium awakened, pulsating alloy).

Usage: python3 scripts/audit_palette.py [--verbose]
Exits 1 when any rule fails. No third-party dependency: pure standard library.
"""
import argparse
import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

FLUIDS_JAVA = ROOT / "src/main/java/dev/gkissel/forgeweave/fluid/ForgeweaveFluids.java"
ORE_JAVA = ROOT / "src/main/java/dev/gkissel/forgeweave/trackb/TrackBOre.java"
ALLOY_JAVA = ROOT / "src/main/java/dev/gkissel/forgeweave/trackb/TrackBAlloy.java"
UNSTABLE_JAVA = ROOT / "src/main/java/dev/gkissel/forgeweave/block/UnstableOreBlock.java"
ORE_SCRIPT = ROOT / "scripts/generate_track_b_ore_textures.py"
ALLOY_SCRIPT = ROOT / "scripts/generate_track_b_alloy_textures.py"
MATERIAL_DIR = ROOT / "src/main/resources/data/forgeweave/forgeweave/material"

FLOOR = 0.030
TRACK_B_MIN = 0.085
FUEL_MIN = 0.20
PLAYTEST_MIN = 0.080

# Vanilla lava is not ours to retune, but the fuel rule has to keep the other four clear of it, so
# it enters the audit as a fixed reference point: the orange the lava still texture averages out to.
LAVA_REFERENCE = 0xD45A12

FUELS = ["lava", "blazing_blood", "magma", "brimspar", "pyrealloy"]

# OKLab hue arcs the fuel rule keeps one fuel each. Lava owns orange and blazing blood owns yellow
# by maintainer directive; the other three take a band of their own from what is left.
HUE_BANDS = [
    ("red", 348.0, 40.0),
    ("orange", 40.0, 82.0),
    ("yellow", 82.0, 122.0),
    ("green", 122.0, 178.0),
    ("blue", 178.0, 282.0),
    ("violet", 282.0, 330.0),
    ("pink", 330.0, 348.0),
]

# Pairs where both tints are ported 1:1 from the 1.12 clone and cited by hex in NOTICE.md (gold and
# electrum off `materialTextColor`, glass and silver likewise). Upstream shipped them this close;
# moving either side is a parity deviation, which CLAUDE.md reserves for an explicit maintainer
# decision. The floor reports them and carries on rather than failing the build over someone else's
# palette.
PARITY_LOCKED = {("gold", "electrum"), ("glass", "silver")}

# The pairs the 2026-09-02 playtest named by hand. They are all across-group pairs the wider Track B
# and fuel rules would not otherwise cover, so they carry their own floor.
PLAYTEST_PAIRS = [
    ("draconium", "psimetal"),
    ("draconium", "emerald"),
    ("psimetal", "emerald"),
    ("cobalt", "osmium"),
    ("manyullyn", "draconium_awakened"),
    ("manyullyn", "pulsating_alloy"),
    ("draconium_awakened", "pulsating_alloy"),
]


# ------------------------------------------------------------------ color space

def hex_to_rgb(color: int) -> tuple[int, int, int]:
    return (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF


def oklab(color: int) -> tuple[float, float, float]:
    """OKLab (L, a, b) of an 0xRRGGBB color, via Bjorn Ottosson's published matrices."""
    def linear(channel: int) -> float:
        u = channel / 255
        return u / 12.92 if u <= 0.04045 else ((u + 0.055) / 1.055) ** 2.4

    r, g, b = (linear(c) for c in hex_to_rgb(color))
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = (x ** (1 / 3) for x in (l, m, s))
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def oklch(color: int) -> tuple[float, float, float]:
    """OKLCh (lightness, chroma, hue in degrees) of an 0xRRGGBB color."""
    lightness, a, b = oklab(color)
    return lightness, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360


def distance(one: int, other: int) -> float:
    return math.dist(oklab(one), oklab(other))


def hue_band(color: int) -> str:
    hue = oklch(color)[2]
    for name, start, end in HUE_BANDS:
        if start <= end and start <= hue < end:
            return name
        if start > end and (hue >= start or hue < end):
            return name
    return "unknown"


# ------------------------------------------------------------------ sources

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def fluid_tints() -> dict[str, int]:
    """Every `register(...)` tint in ForgeweaveFluids, plus the Track B fluids, whose tint the static
    block takes from TrackBOre#color / TrackBAlloy#color. Keyed by the material id the call spells
    out, which is what the registered fluid name is built from and what a material JSON is named
    after -- `iron` for `molten_iron`, `molten_obsidian` for the handful that register a full name."""
    tints = {name: int(value, 16) for name, value
             in re.findall(r'register\(\s*"([a-z_]+)"\s*,\s*0x([0-9A-Fa-f]{6})', read(FLUIDS_JAVA))}
    tints.update(track_b_ore_java())
    tints.update(track_b_alloy_java())
    return tints


def track_b_ore_java() -> dict[str, int]:
    pattern = r'new TrackBOre\("([a-z_]+)",[^)]*?0x([0-9A-Fa-f]{6}),\s*(?:true|false)\)'
    return {name: int(value, 16) for name, value in re.findall(pattern, read(ORE_JAVA))}


def track_b_alloy_java() -> dict[str, int]:
    pattern = r'new TrackBAlloy\("([a-z_]+)",\s*0x([0-9A-Fa-f]{6})'
    return {name: int(value, 16) for name, value in re.findall(pattern, read(ALLOY_JAVA))}


def track_b_ore_script() -> dict[str, int]:
    text = read(ORE_SCRIPT)
    pattern = r'\(\s*"([a-z_]+)",\s*0x([0-9A-Fa-f]{6}),\s*"[a-z_]+"'
    return {name: int(value, 16) for name, value in re.findall(pattern, text)}


def track_b_alloy_script() -> dict[str, int]:
    pattern = r'\(\s*"([a-z_]+)",\s*0x([0-9A-Fa-f]{6})\s*\)'
    return {name: int(value, 16) for name, value in re.findall(pattern, read(ALLOY_SCRIPT))}


def brimspar_crystal_color() -> int:
    match = re.search(r'BRIMSPAR_CRYSTAL_COLOR\s*=\s*0x([0-9A-Fa-f]{6})', read(UNSTABLE_JAVA))
    if match is None:
        raise AssertionError("UnstableOreBlock no longer spells out BRIMSPAR_CRYSTAL_COLOR")
    return int(match.group(1), 16)


def material_color(material_id: str) -> int | None:
    path = MATERIAL_DIR / f"{material_id}.json"
    if not path.is_file():
        return None
    return int(json.loads(read(path))["color"].lstrip("#"), 16)


def track_b_ids() -> list[str]:
    return list(track_b_ore_java()) + ["brimspar"] + list(track_b_alloy_java())


# ------------------------------------------------------------------ rules

def check_family_sync() -> list[str]:
    """R0: every place a Track B material spells its color out has to spell out the same one."""
    failures = []
    ore_java, alloy_java = track_b_ore_java(), track_b_alloy_java()
    ore_script, alloy_script = track_b_ore_script(), track_b_alloy_script()
    tints = fluid_tints()

    for material_id, color in {**ore_java, **alloy_java}.items():
        sources = {
            "fluid tint": tints.get(material_id),
            "sprite script": ore_script.get(material_id, alloy_script.get(material_id)),
            "material JSON": material_color(material_id),
        }
        for label, other in sources.items():
            if other is not None and other != color:
                failures.append(f"{material_id}: {label} is #{other:06X}, "
                                f"but the Java roster says #{color:06X}")

    brimspar = ore_script.get("brimspar")
    for label, other in (("fluid tint", tints.get("brimspar")),
                         ("crystal tint", brimspar_crystal_color())):
        if other != brimspar:
            failures.append(f"brimspar: {label} is #{other:06X}, "
                            f"but the sprite script says #{brimspar:06X}")
    return failures


def close_pairs(palette: dict[str, int], names: list[str], minimum: float) -> list[tuple]:
    found = []
    for i, one in enumerate(names):
        for other in names[i + 1:]:
            gap = distance(palette[one], palette[other])
            if gap < minimum:
                found.append((gap, one, other))
    return sorted(found)


def is_parity_locked(one: str, other: str) -> bool:
    return (one, other) in PARITY_LOCKED or (other, one) in PARITY_LOCKED


def check_fuels(palette: dict[str, int]) -> list[str]:
    failures = []
    bands = {fuel: hue_band(palette[fuel]) for fuel in FUELS}
    for fuel, band in bands.items():
        clash = [other for other, other_band in bands.items() if other != fuel and other_band == band]
        if clash:
            failures.append(f"{fuel} shares the {band} hue band with {', '.join(sorted(clash))}")
    if bands["blazing_blood"] != "yellow":
        failures.append(f"blazing blood should be the yellow fuel, but reads {bands['blazing_blood']}")
    if bands["lava"] != "orange":
        failures.append(f"lava should stay vanilla orange, but reads {bands['lava']}")
    for gap, one, other in close_pairs(palette, FUELS, FUEL_MIN):
        failures.append(f"{one} and {other} are {gap:.4f} apart, under the {FUEL_MIN} fuel minimum")
    return sorted(set(failures))


def audit(verbose: bool = False) -> int:
    palette = fluid_tints()
    palette["lava"] = LAVA_REFERENCE
    track_b = track_b_ids()

    print(f"{len(palette)} Forgeweave-owned tints audited in OKLab "
          f"(floor {FLOOR}, Track B {TRACK_B_MIN}, fuels {FUEL_MIN}, playtest pairs {PLAYTEST_MIN})")

    failed = False

    sync = check_family_sync()
    print(f"\nR0 family sync: {'ok' if not sync else str(len(sync)) + ' mismatches'}")
    for line in sync:
        print(f"  {line}")
    failed |= bool(sync)

    floor_pairs = close_pairs(palette, list(palette), FLOOR)
    locked = [pair for pair in floor_pairs if is_parity_locked(pair[1], pair[2])]
    floor_pairs = [pair for pair in floor_pairs if not is_parity_locked(pair[1], pair[2])]
    print(f"\nR1 global floor {FLOOR}: {len(floor_pairs)} pairs too close")
    for gap, one, other in floor_pairs:
        print(f"  {gap:.4f}  {one} / {other}")
    for gap, one, other in locked:
        print(f"  {gap:.4f}  {one} / {other}  (1.12 parity pair, reported only)")
    failed |= bool(floor_pairs)

    track_b_pairs = close_pairs(palette, track_b, TRACK_B_MIN)
    print(f"\nR2 Track B roster {TRACK_B_MIN}: {len(track_b_pairs)} pairs too close")
    for gap, one, other in track_b_pairs:
        print(f"  {gap:.4f}  {one} / {other}")
    failed |= bool(track_b_pairs)

    fuel_failures = check_fuels(palette)
    print(f"\nR3 fuel ladder: {'ok' if not fuel_failures else str(len(fuel_failures)) + ' problems'}")
    for line in fuel_failures:
        print(f"  {line}")
    for fuel in FUELS:
        lightness, chroma, hue = oklch(palette[fuel])
        print(f"  {fuel:<16} #{palette[fuel]:06X}  {hue_band(palette[fuel]):<7} "
              f"L={lightness:.2f} C={chroma:.3f} h={hue:.0f}")
    failed |= bool(fuel_failures)

    playtest = []
    for one, other in PLAYTEST_PAIRS:
        gap = distance(palette[one], palette[other])
        if gap < PLAYTEST_MIN:
            playtest.append(f"{gap:.4f}  {one} / {other}")
    print(f"\nR4 playtest pairs {PLAYTEST_MIN}: {len(playtest)} pairs too close")
    for line in playtest:
        print(f"  {line}")
    failed |= bool(playtest)

    if verbose:
        print("\nclosest 30 pairs overall")
        for gap, one, other in close_pairs(palette, list(palette), 1.0)[:30]:
            print(f"  {gap:.4f}  {one} / {other}")

    print("\nFAIL" if failed else "\nPASS")
    return 1 if failed else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--verbose", action="store_true", help="also list the closest pairs overall")
    sys.exit(audit(parser.parse_args().verbose))
