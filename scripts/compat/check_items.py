"""check_items.py <place.idx> <dump.txt> [<fixture-dir>]

Per-fixture summary of a dump, keyed by fixture name; exits 1 when a placed fixture item is missing.
With a fixture dir it also lists each component that differs from what the fixture wrote. Those diffs
are informational: a load-time migration (tool_materials gaining `parts`, say) shows up there too.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from snbt import parse
from fixtures_to_cmds import value_after

idx = [l.split('\t') for l in open(sys.argv[1]).read().splitlines()]
dump = {}
for l in open(sys.argv[2]).read().splitlines():
    pos, _, rest = l.partition('|')
    dump[pos] = parse(rest.split('block data: ', 1)[1]) if 'block data: ' in rest else rest


def placed(where):
    w = where.split()
    chest = dump.get(' '.join(w[1:4]))
    items = {int(i['Slot'][2]): i for i in chest.get('Items', [])} if isinstance(chest, dict) else {}
    return items.get(int(w[5]))


missing = 0
for kind, fx, where in idx:
    if kind != 'item':
        continue
    it = placed(where)
    missing += it is None
    print(fx, '->', 'MISSING' if it is None else f"{it['id']} components={len(it.get('components', {}))}")
print('missing fixture items:', missing)

if len(sys.argv) > 3:
    bad = 0
    for kind, fx, where in idx:
        if kind != 'item':
            continue
        want = parse(value_after(open(os.path.join(sys.argv[3], fx)).read(), 'nbt')).get('components', {})
        got = (placed(where) or {}).get('components', {})
        for k in sorted(set(want) | set(got)):
            if want.get(k) != got.get(k):
                bad += 1
                print('DIFF', fx, k, '\n   written:', want.get(k), '\n   loaded: ', got.get(k))
    print('component differences vs fixture nbt (informational):', bad)
sys.exit(1 if missing else 0)
