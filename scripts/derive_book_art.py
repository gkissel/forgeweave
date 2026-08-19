"""Derives the guide book's upstream art from the pinned 1.12 clones (issues #273, #430).

Four files, all straight copies (NOTICE.md):
  - from the Tinkers' clone (c01173c0):
      - the book item icon `items/book.png` -> `derived/item/guide_book.png`
      - the smeltery scene the book's smeltery intro page shows,
        `gui/book/smeltery.png` -> `derived/gui/book/smeltery.png`
  - from the Mantle clone (340a386a, the 1.12 book engine's home -- CLAUDE.md reference table):
      - the spread/arrow sheet `gui/book/book.png` -> `derived/gui/book/book.png`
      - the cover sheet `gui/book/bookfront.png` -> `derived/gui/book/bookfront.png`

Usage: python3 scripts/derive_book_art.py
"""
import shutil
from pathlib import Path

UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12/resources/assets/tconstruct/textures"
MANTLE = Path.home() / "development/minecraft/references/mantle-1.12/src/main/resources/assets/mantle/textures"
DEST = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived"

COPIES = [
    (UPSTREAM / "items/book.png", DEST / "item/guide_book.png"),
    (UPSTREAM / "gui/book/smeltery.png", DEST / "gui/book/smeltery.png"),
    (MANTLE / "gui/book/book.png", DEST / "gui/book/book.png"),
    (MANTLE / "gui/book/bookfront.png", DEST / "gui/book/bookfront.png"),
]


def main() -> None:
    for source, dest in COPIES:
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        print(f"copied {source} -> {dest}")


if __name__ == "__main__":
    main()
