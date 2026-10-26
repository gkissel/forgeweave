"""vanished.py <before-dump> <after-dump> : item stacks that disappeared between two dumps.

Compares, per position, the item ids of every stack inside the block data (chest Items, station
inventories, casting slots alike), and only ids: a stack that loses a mod's own components when that
mod is removed still counts as kept. Exits 1 when any stack is gone.
"""
import collections, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from snbt import parse


def stacks(node, out):
    if isinstance(node, dict):
        if isinstance(node.get('id'), str) and 'count' in node:
            out[node['id']] += 1
        for v in node.values():
            stacks(v, out)
    elif isinstance(node, list):
        for v in node:
            stacks(v, out)
    return out


def load(path):
    dump = {}
    for line in open(path).read().splitlines():
        pos, _, rest = line.partition('|')
        data = parse(rest.split('block data: ', 1)[1]) if 'block data: ' in rest else {}
        dump[pos] = stacks(data, collections.Counter())
    return dump


before, after = load(sys.argv[1]), load(sys.argv[2])
gone = 0
for pos, items in before.items():
    lost = items - after.get(pos, collections.Counter())
    for item, n in sorted(lost.items()):
        gone += n
        print(f'{pos}: {n} x {item} vanished')
print('vanished stacks:', gone)
sys.exit(1 if gone else 0)
