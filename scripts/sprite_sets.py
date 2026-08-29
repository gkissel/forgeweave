"""Shared per-set (Forged default / built-in Legacy pack) plumbing for Forgeweave's art generator
scripts (issue #796).

Forgeweave ships two art sets: **Forged** (new original art, the default, committed straight under
`assets/forgeweave/textures/...`) and **Legacy** (the art Forgeweave shipped before #796, kept
available as a built-in resource pack under `resourcepacks/legacy/assets/forgeweave/textures/...` --
see `ForgeweaveResourcePacks` -- at the exact same relative paths, since a resource pack overrides by
path).

Only a handful of files actually differ between the two sets at any given time (whichever ones a
Forged sprite has replaced so far); every other input (an untouched part silhouette, an untouched
broken-tool layer) is identical between them and is read straight from the Forged/default tree either
way -- `legacy_input` below is the fallback that makes that automatic. **Adding the next Forged
sprite is: drop the new file at its normal default path, copy the file it replaced into the Legacy
pack at the same relative path, and rerun the generator scripts** -- no code change needed here.

Generated composites follow the same rule in the other direction: `save_legacy_if_different` only
actually writes a file into the Legacy pack's output tree when its pixels differ from what the
Forged/default pass just produced at the same relative path (and deletes a stale Legacy file if a
later run stops differing, so a reverted sprite can't leave an orphan behind). That keeps the Legacy
pack free of byte-identical duplicates of art the two sets still share, which in turn keeps
`NOTICE.md` rows singular (a file that has not been Forged-replaced yet still lists one location, the
default tree) and keeps `LegacyResourcePackTest`'s "no orphans" check meaningful.

Every generator script's `main()` follows the same two-pass shape:

    for part in PARTS:
        composite(...).save(FORGED_PATH)          # existing Forged/default pass, unchanged

    for part in PARTS:
        legacy_composite = composite(legacy_input(...), legacy_input(...))
        save_legacy_if_different(legacy_composite, ...)   # new Legacy pass
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TEXTURES = ROOT / "src/main/resources/assets/forgeweave/textures"
LEGACY_TEXTURES = ROOT / "src/main/resources/resourcepacks/legacy/assets/forgeweave/textures"


def legacy_input(subdir: str, filename: str) -> Path:
    """The Legacy set's input file for `filename` under `subdir` (e.g. `"derived/item"`): the Legacy
    pack's own override if it ships one (the file a Forged sprite replaced), else the shared
    Forged/default file -- see the module docstring for why most files never need a Legacy override.
    """
    override = LEGACY_TEXTURES / subdir / filename
    return override if override.is_file() else DEFAULT_TEXTURES / subdir / filename


def save_legacy_if_different(image: Image.Image, subdir: str, filename: str) -> None:
    """Writes `image` under the Legacy pack's tree at `subdir/filename` only if it differs from the
    Forged/default file already on disk at that same relative path. Call this *after* the Forged pass
    has written that file for this run. Removes a stale Legacy file if a later run stops differing
    (e.g. a sprite swap gets reverted), so the pack never accumulates orphans.
    """
    forged = DEFAULT_TEXTURES / subdir / filename
    legacy_out = LEGACY_TEXTURES / subdir / filename

    if forged.is_file():
        forged_pixels = list(Image.open(forged).convert("RGBA").getdata())
        if list(image.convert("RGBA").getdata()) == forged_pixels:
            if legacy_out.is_file():
                legacy_out.unlink()
            return

    legacy_out.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGBA").save(legacy_out)
    print(f"wrote legacy override {legacy_out.relative_to(ROOT)}")
