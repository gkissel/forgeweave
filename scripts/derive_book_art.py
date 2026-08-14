"""Derives the guide book's upstream art (issue #273) from the pinned 1.12 Tinkers' clone.

Two files, both straight copies (NOTICE.md):
  - the book item icon `items/book.png` -> `derived/item/guide_book.png`
  - the smeltery scene the book's smeltery intro page shows,
    `gui/book/smeltery.png` -> `derived/gui/book/smeltery.png`

The book *chrome* (cover, page spread, arrows) is NOT derived: upstream's book engine and its
chrome art live in the separate Mantle library, which is not part of the pinned clone, so that art
is freshly authored instead (scripts/generate_book_gui.py).

Usage: python3 scripts/derive_book_art.py
"""
import shutil
from pathlib import Path

UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12/resources/assets/tconstruct/textures"
DEST = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived"

COPIES = [
    (UPSTREAM / "items/book.png", DEST / "item/guide_book.png"),
    (UPSTREAM / "gui/book/smeltery.png", DEST / "gui/book/smeltery.png"),
]


def main() -> None:
    for source, dest in COPIES:
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        print(f"copied {source} -> {dest}")


if __name__ == "__main__":
    main()
