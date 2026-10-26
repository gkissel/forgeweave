"""spark_tree.py <file> <thread> <cls#method substring>... : print subtree under matching nodes, plus key totals."""
import sys, runpy
src = open(__file__.replace('spark_tree.py', 'spark_fw.py')).read().split('data = open')[0]
exec(src)
data = open(sys.argv[1], 'rb').read(); want_thread = sys.argv[2]; pats = sys.argv[3:]
for f, w, v in fields(data):
    if f != 2: continue
    name, nodes, roots = '', [], []
    for f2, w2, v2 in fields(v):
        if f2 == 1: name = v2.decode()
        elif f2 == 3:
            n = {'cls': '', 'm': '', 't': [], 'c': []}
            for f3, w3, v3 in fields(v2):
                if f3 == 3: n['cls'] = v3.decode()
                elif f3 == 4: n['m'] = v3.decode()
                elif f3 == 8: n['t'] += doubles(w3, v3)
                elif f3 == 9: n['c'] += ints(w3, v3)
            nodes.append(n)
        elif f2 == 5: roots += ints(w2, v2)
    if name != want_thread: continue
    agg = {}
    def walk(i, depth_print):
        n = nodes[i]; key = n['cls'].rsplit('.', 1)[-1] + '#' + n['m']; t = sum(n['t'])
        hit = any(p in n['cls'] + '#' + n['m'] for p in pats)
        if depth_print is None and hit: depth_print = 0
        if depth_print is not None and depth_print <= 7 and t >= 4:
            print('  ' * depth_print + f'{t:7.0f} {key}')
        for c in sorted(n['c'], key=lambda c: -sum(nodes[c]['t'])):
            walk(c, None if depth_print is None else depth_print + 1)
    for r in roots: walk(r, None)
