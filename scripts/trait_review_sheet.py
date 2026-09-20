#!/usr/bin/env python3
"""Review sheet for the issue #1092 trait audit: each trait's code next to its lang text.

For every id in ForgeweaveTraits' REGISTRY, prints the constant's javadoc and body, the
hooks TraitReachabilityTest found it overriding, and the `trait.forgeweave.<id>.name` /
`.description` a player reads. That is the four columns the audit needs side by side; the
verdict is still a human read.

Usage: python3 scripts/trait_review_sheet.py [start] [count]
Read-only.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRAITS = ROOT / "src/main/java/dev/gkissel/forgeweave/trait/ForgeweaveTraits.java"
LANG = ROOT / "src/generated/resources/assets/forgeweave/lang/en_us.json"
HOOKS = ROOT / "build/trait-audit/traits.md"


def registry_ids(text: str) -> list[tuple[str, str]]:
    """(id, CONSTANT) pairs, in REGISTRY order."""
    start = text.index("Map<ResourceLocation, Trait> REGISTRY")
    body = text[start : text.index("\n\n", start)]
    return re.findall(r'Map\.entry\(id\("([^"]+)"\), (\w+)\)', body)


def constant_blocks(text: str) -> dict[str, str]:
    """CONSTANT -> its javadoc plus declaration, as source lines."""
    lines = text.splitlines()
    blocks = {}
    for i, line in enumerate(lines):
        m = re.match(r"\s*public static final Trait (\w+)\s*=", line)
        if not m:
            continue
        top = i
        while top > 0 and (
            lines[top - 1].strip().startswith(("*", "/**", "//"))
            or lines[top - 1].strip() == ""
            and lines[top - 2].strip().startswith("*")
        ):
            top -= 1
        depth = 0
        end = i
        for j in range(i, min(i + 120, len(lines))):
            depth += lines[j].count("{") - lines[j].count("}")
            end = j
            if lines[j].rstrip().endswith(";") and depth <= 0:
                break
        blocks[m.group(1)] = "\n".join(lines[top : end + 1])
    return blocks


def trim(block: str, javadoc_lines: int = 6) -> str:
    """The whole declaration, but only the opening of a long javadoc.

    A trait's javadoc is often a page of upstream parity notes. The audit needs the claim it
    opens with and then the code; pass --full to see all of it.
    """
    if "--full" in sys.argv:
        return block
    lines = block.splitlines()
    doc = [line for line in lines if line.strip().startswith(("*", "/**"))]
    code = lines[len(doc) :] if len(doc) < len(lines) else []
    if len(doc) > javadoc_lines:
        doc = doc[:javadoc_lines] + ["     * [...]", "     */"]
    return "\n".join(doc + code)


def main() -> int:
    text = TRAITS.read_text()
    lang = json.loads(LANG.read_text())
    blocks = constant_blocks(text)
    hooks = {}
    if HOOKS.exists():
        for row in HOOKS.read_text().splitlines():
            cells = [c.strip() for c in row.strip("|").split("|")]
            if len(cells) == 4 and cells[0].startswith("`"):
                hooks[cells[0].strip("`")] = f"{cells[1]}  (tool: {cells[2]}, armor: {cells[3]})"

    ids = registry_ids(text)
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    count = int(sys.argv[2]) if len(sys.argv) > 2 else len(ids)
    print(f"# {len(ids)} registry entries; showing {start}..{start + count}\n")
    for index, (trait_id, constant) in enumerate(ids[start : start + count], start):
        print(f"## [{index}] {trait_id}  ({constant})")
        print(f"HOOKS: {hooks.get(trait_id, '?')}")
        print(f"NAME:  {lang.get(f'trait.forgeweave.{trait_id}.name', '(MISSING)')}")
        print(f"DESC:  {lang.get(f'trait.forgeweave.{trait_id}.description', '(MISSING)')}")
        print(trim(blocks.get(constant, "(source block not found)")))
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
