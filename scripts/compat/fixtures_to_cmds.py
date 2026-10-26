"""Turn save-compat fixtures into console commands that place them in a world.
usage: python3 -I fixtures_to_cmds.py <fixture_dir> <out_cmds> <out_index>"""
import os, re, sys

def value_after(text, key):
    """Return raw SNBT text of the value of top-level `key:` (brace/bracket matched)."""
    m = re.search(r'^\s{4}' + key + r':\s*', text, re.M)
    if not m:
        return None
    i = m.end()
    if text[i] == '"':
        j = i + 1
        while text[j] != '"':
            j += 2 if text[j] == '\\' else 1
        return text[i:j + 1]
    depth, j, inq = 0, i, False
    while True:
        c = text[j]
        if inq:
            if c == '\\': j += 1
            elif c == '"': inq = False
        elif c == '"': inq = True
        elif c in '{[': depth += 1
        elif c in '}]':
            depth -= 1
            if depth == 0:
                return text[i:j + 1]
        j += 1

def compact(s):
    out, inq, i = [], False, 0
    while i < len(s):
        c = s[i]
        if inq:
            out.append(c)
            if c == '\\': out.append(s[i + 1]); i += 1
            elif c == '"': inq = False
        elif c == '"': inq = True; out.append(c)
        elif not c.isspace(): out.append(c)
        i += 1
    return ''.join(out)

def main():
    fdir, out_cmds, out_idx = sys.argv[1:4]
    items, bes = [], []
    for f in sorted(os.listdir(fdir)):
        t = open(os.path.join(fdir, f)).read()
        dec = value_after(t, 'decoder').strip('"')
        nbt = compact(value_after(t, 'nbt'))
        if dec == 'item_stack':
            items.append((f, nbt))
        elif dec == 'block_entity':
            bes.append((f, value_after(t, 'block').strip('"'), nbt))

    cmds, idx = [], []
    # items: chests along x at y=-60, z=0 ; 27 per chest
    for n, (f, nbt) in enumerate(items):
        chest, slot = divmod(n, 27)
        idx.append(f'item\t{f}\tchest {chest*2} -60 0 slot {slot}')
    chunks = [items[i:i + 27] for i in range(0, len(items), 27)]
    for c, chunk in enumerate(chunks):
        entries = ','.join('{Slot:%db,%s' % (s, nbt[1:]) for s, (f, nbt) in enumerate(chunk))
        cmds.append(f'setblock {c*2} -60 0 minecraft:chest{{Items:[{entries}]}} replace')
    # block entities along z=4
    for n, (f, block, nbt) in enumerate(bes):
        x = n * 2
        cmds.append(f'setblock {x} -60 4 {block}{nbt} replace')
        idx.append(f'be\t{f}\t{block} {x} -60 4')
    open(out_cmds, 'w').write('\n'.join(cmds) + '\n')
    open(out_idx, 'w').write('\n'.join(idx) + '\n')
    print(len(items), 'items in', len(chunks), 'chests;', len(bes), 'block entities')


if __name__ == '__main__':
    main()
