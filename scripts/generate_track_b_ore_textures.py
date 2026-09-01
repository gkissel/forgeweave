#!/usr/bin/env python3
"""Generates Track B's ore family art (issue #878, epic #824 Track B follow-up; supersedes the
procedural approach from issue #839 / PR #864) for the ore block, storage block, raw-storage block,
raw item, ingot and nugget of each of the 11 materials in dev.gkissel.forgeweave.trackb.TrackBOre.
Issue #888 folds ingot/nugget into this script too -- #878 had left them "untouched... as they shipped
in #864" (still the old procedural art); #888 is the maintainer directive that completes the vanilla-art
pipeline for those last two sprites.

Maintainer directive (2026-08-31, issue #878; extended 2026-09-01, issue #888): this art is now
**vanilla-derived** instead of procedurally drawn from flat shapes. Each of the six sprites this script
owns takes a real vanilla Minecraft texture as its template and recolors it to the material's own
flavor color (dev.gkissel.forgeweave.trackb.TrackBOre#color, the same hex the old procedural script
used):

  * **Ore block**: a vanilla ore texture (stone/deepslate/netherrack host baked in, per the material's
    TrackBOre#host) with its mineral blob recolored, the host rock pixels left byte-identical. The mask
    is a straight pixel diff against the matching plain host texture (stone.png/deepslate.png/
    netherrack.png) -- verified against every template this script uses: differing pixels cleanly
    separate into "identical to host" (distance 0) and "blob" (distance >= ~12), no middle ground, so a
    distance-10 threshold reliably isolates the blob with no manual touch-up needed on any of the 11.
    Issue #883 (voidglass's move to the End) is the one exception: there is no vanilla end-stone ore to
    diff against, so voidglass's blob mask is computed the normal way against its *donor's own* natural
    host (deepslate, since its ore template is still `deepslate_gold_ore` per TEMPLATES below) and that
    mask shape is then painted onto end_stone.png instead -- end_stone's own pixels stay intact outside
    the blob, exactly like every other material's host rock does. See the `host == "end_stone"` branch
    in `main()`.
  * **Storage block** and **raw-storage block**: a vanilla metal-block/raw-block texture (solid panel,
    no separate host to preserve), recolored across the whole image.
  * **Raw item**: a vanilla raw-ore item icon, recolored across the whole image -- same technique
    scripts/recolor_raw_ore.py already established for raw_cobalt/raw_ardite (issue #140).
  * **Ingot** and **nugget** (issue #888): a vanilla ingot/nugget item icon, recolored across the whole
    image, drawn from the *same donor family as the material's own raw item* (TEMPLATES[id]["raw_family"]
    below) -- e.g. murkiron's raw item is iron-templated, so murkiron_ingot recolors vanilla iron_ingot
    and murkiron_nugget recolors vanilla iron_nugget. Vanilla has no copper_nugget texture at all, so
    any material whose raw_family is "copper" falls back to iron_nugget as its nugget donor instead --
    the closest metal, deterministic, recorded per-material in TEMPLATES[id]["nugget_family"].

All six use the same hue-recolor: shift every opaque pixel's hue to the material color's hue, and
scale saturation/value by the ratio of the material color's own saturation/value to the *source
sub-image's* average (the masked blob's average for the ore block, the whole image's average for the
other five) -- this preserves the source's shading/highlight variation while landing on the right
color, exactly recolor_raw_ore.py's algorithm, generalized to operate on a pixel subset.

**Template assignment** is deterministic (seeded by material id + sprite purpose, `random.Random`,
same reproducible-RNG idiom the old script used) and recorded as a literal table below (TEMPLATES) so
it's reviewable without re-running anything; re-running the derivation (see the comment above
TEMPLATES) reproduces the same table byte-for-byte. Three independent pools, since a material's ore
template, storage-block template and raw template don't need to agree:

  * Ore/storage pool: iron, copper, gold, diamond, redstone, lapis, emerald (issue #878's named
    "vanilla ore set", 7 families) -- deepslate-prefixed when TrackBOre#host is OVERWORLD_DEEPSLATE
    (8 of 11 materials), left at the surface family when OVERWORLD_STONE (starfall_stone).
    voltcinder/hardcinder (TrackBOre#host NETHER) aren't stone or deepslate at all, so they draw from a
    separate 2-member nether pool (nether_gold_ore, nether_quartz_ore) instead -- the 7-family pool has
    no netherrack-based member, and a stone/deepslate-templated ore block would look wrong sitting in
    Nether worldgen. voidglass (TrackBOre#host END, issue #883) draws its donor template the same way
    OVERWORLD_DEEPSLATE materials do -- there's no netherrack-style dedicated pool for it, since its
    donor's *shape*, not its host rock, is what gets reused (see the ore-block bullet above).
  * Storage-block pool: the metal-block half of the same 7 families (iron_block .. emerald_block) --
    independent draw from the ore template, e.g. a material whose ore uses gold_ore can still have a
    storage block drawn from diamond_block.
  * Raw pool: iron, copper, gold only -- the only three vanilla metals with a "raw" item/block pair.
    Feeds the raw item and the raw-storage block (same family for both, matching how vanilla's own
    raw_iron / raw_iron_block are a pair) -- and, per issue #888, the ingot/nugget donor family too
    (with the copper->iron nugget fallback described above).

Provenance (issue #878 deliverable 3): this derives from Minecraft's own client assets, not from any
of the MIT Tinkers'/Mantle clones CLAUDE.md's NOTICE.md rule covers -- no NOTICE.md row for this file's
output. Precedent: #823 (Wooden Hopper) already builds directly off vanilla assets (its blockstate
parents `block/hopper` and reuses vanilla's own hopper GUI) without a NOTICE.md row, on the reasoning
that vanilla-derived work sits outside the MIT-clone provenance question CLAUDE.md's rule exists to
answer. Vanilla textures are never committed to this repo by this script -- they're extracted at
generation time from the dev environment's own Minecraft client jar (see find_vanilla_jar()) straight
into memory, and only this script's *recolored* output is written to the working tree.

Usage: python3 scripts/generate_track_b_ore_textures.py
Requires Pillow (`pip install pillow`). Requires a Minecraft 1.21.1 client jar reachable from Gradle's
caches (present after a `./gradlew build` in this project, or any other NeoForge 1.21.1 project sharing
the same `~/.gradle` home).
"""
import colorsys
import glob
import io
import os
import random
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/block"
ITEM_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/item"

# (id, color, host) -- host picks the ore block's base rock. Must match TrackBOre.ALL. Issue #884
# (1) removed "cinderstone": basalt replaces it and uses vanilla's own basalt texture, no derived art.
ORES = [
    ("fulmenite", 0xC8D94A, "deepslate"),
    ("duskspar", 0x8A5FD9, "deepslate"),
    ("voltcinder", 0x38D9D0, "netherrack"),
    ("murkiron", 0x3A5C56, "deepslate"),
    ("hardcinder", 0xC23B2B, "netherrack"),
    ("nightshale", 0x3B3F7A, "deepslate"),
    ("warspar", 0xA4283F, "deepslate"),
    ("hollowstone", 0xD8D3C2, "deepslate"),
    ("resonite", 0x3FAE9E, "deepslate"),
    ("starfall_stone", 0xBCD6F2, "stone"),
    ("voidglass", 0x2A1740, "end_stone"),
]

ORE_STORAGE_POOL = ["iron", "copper", "gold", "diamond", "redstone", "lapis", "emerald"]
NETHER_POOL = ["nether_gold_ore", "nether_quartz_ore"]
RAW_POOL = ["iron", "copper", "gold"]

HOST_PLAIN_TEXTURE = {"stone": "stone", "deepslate": "deepslate", "netherrack": "netherrack", "end_stone": "end_stone"}

BLOB_MASK_THRESHOLD = 10.0
BLOB_MIN_CONTRAST = 0.18


def _derive_ore_template(mat_id: str, host: str) -> str:
    """Deterministic per (id, host) -- see module docstring's "Template assignment" section. `end_stone`
    (voidglass, issue #883) reuses the deepslate branch: there is no vanilla end-stone ore to draw a
    donor from, so the donor stays a deepslate ore family -- only its blob *shape* gets reused, painted
    onto end_stone.png as the real host instead of deepslate.png (see main()'s `end_stone` branch)."""
    rng = random.Random(f"{mat_id}:ore")
    if host == "netherrack":
        return rng.choice(NETHER_POOL)
    family = rng.choice(ORE_STORAGE_POOL)
    return f"deepslate_{family}_ore" if host in ("deepslate", "end_stone") else f"{family}_ore"


def _derive_storage_template(mat_id: str) -> str:
    rng = random.Random(f"{mat_id}:storage")
    return f"{rng.choice(ORE_STORAGE_POOL)}_block"


def _derive_raw_family(mat_id: str) -> str:
    rng = random.Random(f"{mat_id}:raw")
    return rng.choice(RAW_POOL)


def _nugget_family(family: str) -> str:
    """Vanilla has no copper_nugget texture, so any material whose ingot/raw donor family is copper
    falls back to iron -- the closest metal, deterministic (issue #888)."""
    return "iron" if family == "copper" else family


# Recorded template-assignment table (issue #878 deliverable 2, extended by #888's ingot/nugget donor
# columns), reproducible by re-running the _derive_* helpers above -- kept as a literal dict here so the
# mapping is reviewable without running anything. ore/storage/raw_family per material id; raw_family
# doubles as the ingot donor and (via _nugget_family) the nugget donor, per #888's directive that ingot
# and nugget reuse the same donor family the raw item already picked.
TEMPLATES = {
    mat_id: {
        "ore": _derive_ore_template(mat_id, host),
        "storage": _derive_storage_template(mat_id),
        "raw_family": _derive_raw_family(mat_id),
        "nugget_family": _nugget_family(_derive_raw_family(mat_id)),
    }
    for mat_id, _color, host in ORES
}


def hex_to_rgb(color: int) -> tuple[int, int, int]:
    return (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF


# ---------------------------------------------------------------------------
# Vanilla asset extraction -- never committed, read straight into memory.
# ---------------------------------------------------------------------------

def find_vanilla_jar() -> str:
    """Locates a Minecraft 1.21.1 client jar (with the assets/minecraft/textures tree) reachable from
    Gradle's caches. Checked in order: this project's own ModDevGradle build artifacts (present after
    `./gradlew build`), then NeoGradle's ng_execute cache, then the raw NeoFormRuntime client jar --
    all populated the first time any NeoForge 1.21.1 project in this Gradle home is built.
    """
    home = os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))
    candidates = [
        str(ROOT / "build/moddev/artifacts/neoforge-*-client-extra*.jar"),
        f"{home}/caches/ng_execute/**/client-extra.jar",
        f"{home}/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar",
    ]
    for pattern in candidates:
        matches = glob.glob(pattern, recursive=True)
        if matches:
            return matches[0]
    raise FileNotFoundError(
        "No Minecraft 1.21.1 client jar found in Gradle's caches. Run `./gradlew build` once first "
        "(populates ~/.gradle/caches and/or build/moddev/artifacts with the vanilla asset jar)."
    )


class VanillaAssets:
    def __init__(self, jar_path: str):
        self._zip = zipfile.ZipFile(jar_path)
        self._cache: dict[str, Image.Image] = {}

    def block(self, name: str) -> Image.Image:
        return self._get(f"assets/minecraft/textures/block/{name}.png")

    def item(self, name: str) -> Image.Image:
        return self._get(f"assets/minecraft/textures/item/{name}.png")

    def _get(self, path: str) -> Image.Image:
        if path not in self._cache:
            data = self._zip.read(path)
            self._cache[path] = Image.open(io.BytesIO(data)).convert("RGBA")
        return self._cache[path]


# ---------------------------------------------------------------------------
# Masking + recolor
# ---------------------------------------------------------------------------

def blob_mask(host_img: Image.Image, ore_img: Image.Image, threshold: float = BLOB_MASK_THRESHOLD) -> list[list[bool]]:
    """True where ore_img's pixel differs from host_img's by more than `threshold` (Euclidean RGB
    distance) -- the mineral blob. See module docstring: every template used here separates cleanly
    into a distance-0 cluster (host rock) and a distance->=12 cluster (blob), so a threshold of 10
    reliably isolates the blob with no per-material tuning.
    """
    hp, op = host_img.load(), ore_img.load()
    w, h = ore_img.size
    mask = [[False] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            r1, g1, b1, _ = hp[x, y]
            r2, g2, b2, _ = op[x, y]
            dist = ((r1 - r2) ** 2 + (g1 - g2) ** 2 + (b1 - b2) ** 2) ** 0.5
            mask[y][x] = dist > threshold
    return mask


def recolor_pixels(
    src: Image.Image,
    mask: list[list[bool]],
    target_rgb: tuple[int, int, int],
    min_contrast: float = 0.0,
) -> Image.Image:
    """Hue-recolors src's masked-True pixels to target_rgb's hue, scaling each pixel's saturation/value
    by the ratio of target_rgb's own saturation/value to the *masked region's* average -- preserves the
    source's shading while landing on the material color. Pixels where mask is False are copied
    unchanged (the "host rock stays intact" requirement for ore blocks; irrelevant for the full-image
    recolors, whose mask is all-True).

    `min_contrast` (0-1, in HSV value terms) guards against a flavor color landing too close to the
    unmasked host rock's own brightness to read as an ore blob at all -- cinderstone (0x8A8A86, a
    near-stone gray) is exactly this case: recolored at face value its blob nearly vanished into
    stone.png's own gray. When the gap between target_v and the unmasked pixels' average value is
    under this threshold, target_v is pushed further away (same brighter/darker direction, just a
    bigger gap) before the saturation/value ratios are computed. No-op when mask has no unmasked
    pixels (the full-image recolor calls) or the source has no unmasked opaque pixels.
    """
    w, h = src.size
    px = src.load()
    target_h, target_s, target_v = colorsys.rgb_to_hsv(*(c / 255 for c in target_rgb))

    if min_contrast > 0:
        host_vals = []
        for y in range(h):
            for x in range(w):
                if mask[y][x]:
                    continue
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                host_vals.append(v)
        if host_vals:
            host_avg_v = sum(host_vals) / len(host_vals)
            if abs(target_v - host_avg_v) < min_contrast:
                target_v = (
                    min(1.0, host_avg_v + min_contrast)
                    if target_v >= host_avg_v
                    else max(0.0, host_avg_v - min_contrast)
                )

    sats, vals = [], []
    for y in range(h):
        for x in range(w):
            if not mask[y][x]:
                continue
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            sats.append(s)
            vals.append(v)
    avg_sat = sum(sats) / len(sats) if sats else 0.0
    avg_val = sum(vals) / len(vals) if vals else 0.0
    # A fully achromatic source region (avg_sat == 0, e.g. vanilla's iron_ingot/iron_nugget are pure
    # grays) has no per-pixel saturation variation to scale by ratio -- 0 * anything is still 0. Force
    # such pixels flat to target_s instead of dividing by zero; value shading (highlights/lowlights)
    # still comes through via val_ratio either way.
    flat_sat = avg_sat == 0.0
    sat_ratio = 1.0 if flat_sat else target_s / avg_sat
    val_ratio = target_v / avg_val if avg_val else 1.0

    out = Image.new("RGBA", (w, h))
    out_px = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                out_px[x, y] = (0, 0, 0, 0)
                continue
            if not mask[y][x]:
                out_px[x, y] = (r, g, b, a)
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            new_s = target_s if flat_sat else min(1.0, max(0.0, s * sat_ratio))
            new_v = min(1.0, max(0.0, v * val_ratio))
            nr, ng, nb = colorsys.hsv_to_rgb(target_h, new_s, new_v)
            out_px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def full_mask(img: Image.Image) -> list[list[bool]]:
    w, h = img.size
    return [[True] * w for _ in range(h)]


def main() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)

    assets = VanillaAssets(find_vanilla_jar())

    for mat_id, color_hex, host in ORES:
        color = hex_to_rgb(color_hex)
        tpl = TEMPLATES[mat_id]

        # Ore block: mask the blob against the plain host texture, recolor only the blob.
        if host == "end_stone":
            # #883: no vanilla end-stone ore exists, so the mask comes from the donor's own natural
            # host (deepslate) instead, then that blob shape is painted onto end_stone.png -- the real
            # host -- so end_stone's own pixels stay intact everywhere the donor's blob isn't.
            donor_host_img = assets.block("deepslate")
            donor_ore_img = assets.block(tpl["ore"])
            mask = blob_mask(donor_host_img, donor_ore_img)
            end_stone_img = assets.block(HOST_PLAIN_TEXTURE[host])
            w, h = donor_ore_img.size
            composite = Image.new("RGBA", (w, h))
            comp_px, donor_px, host_px = composite.load(), donor_ore_img.load(), end_stone_img.load()
            for y in range(h):
                for x in range(w):
                    comp_px[x, y] = donor_px[x, y] if mask[y][x] else host_px[x, y]
            recolor_pixels(composite, mask, color, min_contrast=BLOB_MIN_CONTRAST).save(BLOCK_DIR / f"{mat_id}_ore.png")
        else:
            host_img = assets.block(HOST_PLAIN_TEXTURE[host])
            ore_img = assets.block(tpl["ore"])
            mask = blob_mask(host_img, ore_img)
            recolor_pixels(ore_img, mask, color, min_contrast=BLOB_MIN_CONTRAST).save(BLOCK_DIR / f"{mat_id}_ore.png")

        # Storage block: full-image recolor, no host to preserve.
        storage_img = assets.block(tpl["storage"])
        recolor_pixels(storage_img, full_mask(storage_img), color).save(BLOCK_DIR / f"{mat_id}_block.png")

        # Raw-storage block + raw item: full-image recolor, same vanilla raw family for both.
        raw_family = tpl["raw_family"]
        raw_block_img = assets.block(f"raw_{raw_family}_block")
        recolor_pixels(raw_block_img, full_mask(raw_block_img), color).save(BLOCK_DIR / f"raw_{mat_id}_block.png")

        raw_item_img = assets.item(f"raw_{raw_family}")
        recolor_pixels(raw_item_img, full_mask(raw_item_img), color).save(ITEM_DIR / f"raw_{mat_id}.png")

        # Ingot + nugget (issue #888): same donor family as the raw item, copper falling back to iron
        # for the nugget since vanilla has no copper_nugget texture.
        nugget_family = tpl["nugget_family"]
        ingot_img = assets.item(f"{raw_family}_ingot")
        recolor_pixels(ingot_img, full_mask(ingot_img), color).save(ITEM_DIR / f"{mat_id}_ingot.png")

        nugget_img = assets.item(f"{nugget_family}_nugget")
        recolor_pixels(nugget_img, full_mask(nugget_img), color).save(ITEM_DIR / f"{mat_id}_nugget.png")

        print(
            f"wrote {mat_id}: ore<-{tpl['ore']} storage<-{tpl['storage']} raw<-raw_{raw_family}(_block) "
            f"ingot<-{raw_family}_ingot nugget<-{nugget_family}_nugget"
        )


if __name__ == "__main__":
    main()
