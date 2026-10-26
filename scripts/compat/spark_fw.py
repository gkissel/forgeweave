"""spark_fw.py <file.sparkprofile> : per-thread total time and forgeweave share (raw protobuf decode)."""
import sys, struct
from collections import defaultdict
def varint(b, i):
    r = s = 0
    while True:
        c = b[i]; i += 1; r |= (c & 0x7f) << s; s += 7
        if c < 0x80: return r, i
def fields(b):
    i, out = 0, []
    while i < len(b):
        k, i = varint(b, i); f, w = k >> 3, k & 7
        if w == 0: v, i = varint(b, i)
        elif w == 1: v = b[i:i+8]; i += 8
        elif w == 5: v = b[i:i+4]; i += 4
        elif w == 2: n, i = varint(b, i); v = b[i:i+n]; i += n
        else: raise ValueError(w)
        out.append((f, w, v))
    return out
def doubles(w, v):
    if w == 1: return [struct.unpack('<d', v)[0]]
    return list(struct.unpack('<%dd' % (len(v)//8), v))
def ints(w, v):
    if w == 0: return [v]
    r, i = [], 0
    while i < len(v): x, i = varint(v, i); r.append(x)
    return r
data = open(sys.argv[1], 'rb').read()
for f, w, v in fields(data):
    if f != 2: continue
    name, nodes, ttimes, roots = '', [], [], []
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
        elif f2 == 4: ttimes += doubles(w2, v2)
        elif f2 == 5: roots += ints(w2, v2)
    total = sum(ttimes)
    fw = defaultdict(float); fw_total = 0.0
    def walk(idx, inside):
        global fw_total
        n = nodes[idx]; is_fw = n['cls'].startswith('dev.gkissel.forgeweave')
        if is_fw and not inside:
            t = sum(n['t']); fw[n['cls'] + '#' + n['m']] += t; fw_total += t
        for c in n['c']: walk(c, inside or is_fw)
    sys.setrecursionlimit(100000)
    for r in roots: walk(r, False)
    if total == 0: continue
    print(f'{name}: total {total:.0f} ms, forgeweave {fw_total:.1f} ms ({100*fw_total/total:.3f}%)')
    for k, t in sorted(fw.items(), key=lambda x: -x[1])[:12]:
        print(f'    {t:9.1f} ms  {k}')
