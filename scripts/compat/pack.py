"""Read a compat pack file, assemble its mods, and check a server log against it.

usage:
  python3 -I pack.py mods  <pack> <mods-dir>         copy every jar into mods-dir (exit 2 = a local jar is missing)
  python3 -I pack.py jar   <pack> <directive>        resolve the jar a directive names (upgrade-from) and print its path
  python3 -I pack.py get   <pack> <directive>        print a directive's values, one line each
  python3 -I pack.py check <pack> <log> <phase> [<config-before>]
                                                     check a server log; one PASS/FAIL/INFO line per check, exit 1 on FAIL.
                                                     phase on/off also checks the counts and material lists
                                                     (the -off directives for off); any other phase only the log.
                                                     With config-before (a copy of config/forgeweave from before the
                                                     boot), NeoForge's "is not correct. Correcting" WARN for a file
                                                     is allowed when the correction changed no setting.

Pack format (one directive per line, # starts a comment, see README.md):
  include <pack>
  mod modrinth <slug> <version> <sha512> <file name>
  mod local <sha512 or -> <file name or glob>
  recipes <n> | recipes-off <n> | materials <n> | materials-off <n>
  material <id>... | no-material <id>... | material-off <id>... | no-material-off <id>...
  fixture <cmds file> | without <glob>... | upgrade-from modrinth <slug> <version> <sha512> <file name> | spark

Environment:
  FW_COMPAT_CACHE     download cache (default ~/.cache/forgeweave-compat)
  FW_COMPAT_JAR_DIRS  colon-separated directories searched by file name before downloading,
                      and the only source of `mod local` jars
"""
import fnmatch, hashlib, json, os, re, shutil, sys, tomllib, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.expanduser(os.environ.get('FW_COMPAT_CACHE', '~/.cache/forgeweave-compat'))
JAR_DIRS = [os.path.expanduser(d) for d in os.environ.get('FW_COMPAT_JAR_DIRS', '').split(':') if d]
NEVER_TOGETHER = [('jei', 'emi'), ('jade', 'wthit'), ('better-combat', 'epic-fight')]


def parse(path, seen=None):
    """Directives in file order, includes expanded: [(keyword, [args], raw rest-of-line)]."""
    seen = seen or set()
    path = os.path.abspath(path)
    if path in seen:
        sys.exit(f'{path}: include cycle')
    seen.add(path)
    out = []
    for n, line in enumerate(open(path), 1):
        line = line.split('#', 1)[0].strip()
        if not line:
            continue
        kw, _, rest = line.partition(' ')
        if kw == 'include':
            out += parse(os.path.join(os.path.dirname(path), rest.strip()), seen)
        else:
            out.append((kw, rest.split(), rest.strip()))
    return out


def sha512(path):
    h = hashlib.sha512()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def find_local(pattern, want):
    for d in JAR_DIRS:
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            p = os.path.join(d, f)
            if fnmatch.fnmatchcase(f, pattern) and (want == '-' or sha512(p) == want):
                return p
    return None


def modrinth(want, name):
    """A Modrinth jar by sha512: a jar dir first, then the cache, then the Modrinth API."""
    hit = find_local(name, want)
    if hit:
        return hit
    dest = os.path.join(CACHE, 'jars', want[:16], name)
    if os.path.exists(dest) and sha512(dest) == want:
        return dest
    req = urllib.request.Request(f'https://api.modrinth.com/v2/version_file/{want}?algorithm=sha512',
                                 headers={'User-Agent': 'gkissel/forgeweave compat matrix'})
    version = json.load(urllib.request.urlopen(req, timeout=60))
    url = next(f['url'] for f in version['files'] if f['hashes'].get('sha512') == want)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + '.part'
    with urllib.request.urlopen(urllib.request.Request(url, headers=req.headers), timeout=300) as r, open(tmp, 'wb') as f:
        shutil.copyfileobj(r, f)
    if sha512(tmp) != want:
        os.remove(tmp)
        sys.exit(f'{name}: downloaded file does not match its sha512')
    os.replace(tmp, dest)
    return dest


def resolve(args, raw):
    """Path of the jar a `mod ...`/`upgrade-from ...` line names, or None for a missing local jar."""
    if args[0] == 'modrinth':
        return modrinth(args[3], raw.split(None, 4)[4])
    if args[0] == 'local':
        return find_local(raw.split(None, 2)[2], args[1])
    sys.exit(f'unknown jar source {args[0]!r}')


def cmd_mods(pack, mods_dir):
    lines = [d for d in parse(pack) if d[0] == 'mod']
    slugs = {a[1] for _, a, _ in lines if a[0] == 'modrinth'}
    for a, b in NEVER_TOGETHER:
        if a in slugs and b in slugs:
            sys.exit(f'{pack}: {a} and {b} never load together')
    missing = []
    os.makedirs(mods_dir, exist_ok=True)
    for _, args, raw in lines:
        path = resolve(args, raw)
        if path is None:
            missing.append(raw.split(None, 2)[2])
        else:
            shutil.copy(path, mods_dir)
    if missing:
        print('missing local jars (put them in a FW_COMPAT_JAR_DIRS directory): ' + ', '.join(missing))
        sys.exit(2)
    print(len(lines), 'mods')


def last(pattern, text):
    found = re.findall(pattern, text)
    return found[-1] if found else None


# The without pass removes a mod, which takes Forgeweave's built-in data pack for it with it
# (CompatDataPacks), and vanilla notes the pack it can no longer find. The pack is always active, so it
# comes back with the mod. Allowed in that pass only.
REMOVED_MOD_PACK = re.compile(r'\]: Missing data pack mod/forgeweave:datapacks/[a-z0-9_]+$')
CORRECTING = re.compile(r'Configuration file (\S+) is not correct\. Correcting$')


def flat_toml(path):
    """A TOML file as {'table.key': value}."""
    def walk(table, prefix):
        for k, v in table.items():
            if isinstance(v, dict):
                yield from walk(v, prefix + k + '.')
            else:
                yield prefix + k, v
    with open(path, 'rb') as f:
        return dict(walk(tomllib.load(f), ''))


def settings_unchanged(before_dir, line):
    """True when `line` is NeoForge correcting a config file without touching a setting: it added keys
    a previous release didn't have (beta.12 has no emiPlugin), or refreshed comments that release worded
    differently (NeoForge rewrites a file whose comments differ). A changed, reset or dropped value is not."""
    m = CORRECTING.search(line)
    if not (before_dir and m):
        return False
    after = m.group(1)
    before = os.path.join(before_dir, os.path.basename(after))
    if not (os.path.isfile(before) and os.path.isfile(after)):
        return False
    old, new = flat_toml(before), flat_toml(after)
    return all(k in new and new[k] == v for k, v in old.items())


def cmd_check(pack, log, phase, config_before=None):
    d = parse(pack)
    text = open(log, errors='replace').read()
    allow = [re.compile(l.strip()) for l in open(os.path.join(HERE, 'log-allowlist.txt'))
             if l.strip() and not l.startswith('#')]
    results = []

    def result(ok, msg):
        results.append(ok)
        print(('PASS ' if ok else 'FAIL ') + msg)

    noisy = []
    for line in text.splitlines():
        flagged = (re.search(r'/(WARN|ERROR|FATAL)\]', line) and 'forgeweave' in line.lower()) \
            or re.match(r'\s+at dev\.gkissel\.forgeweave\.', line)
        if (flagged and not any(a.search(line) for a in allow) and not settings_unchanged(config_before, line)
                and not (phase == 'without' and REMOVED_MOD_PACK.search(line))):
            noisy.append(re.sub(r'^\[[^]]*\] ', '', line)[:300])
    result(not noisy, f'forgeweave WARN/ERROR lines: {len(noisy)}' + ''.join('\n     ' + l for l in noisy[:5]))

    sync = last(r'Material registry sync payload: (\d+) of (\d+) materials', text)
    result(bool(sync) and sync[0] == sync[1], 'material sync line: ' + (f'{sync[0]} of {sync[1]}' if sync else 'missing'))

    recipes = last(r'Loaded (\d+) recipes', text)
    mats = last(r'Loaded (\d+) materials: \[([^\]]*)\]', text)
    print(f'INFO recipes {recipes}, materials {mats[0] if mats else None}')
    if phase not in ('on', 'off'):  # a pass that changes the mod list pins nothing but log hygiene
        sys.exit(0 if all(results) else 1)
    names = set(mats[1].split(', ')) if mats else set()
    suffix = '-off' if phase == 'off' else ''
    for key, got in (('recipes', recipes), ('materials', mats[0] if mats else None)):
        want = [a[0] for kw, a, _ in d if kw == key + suffix]
        if want:
            result(got == want[-1], f'{key}{suffix}: {got}, expected {want[-1]}')
    for kw, present in (('material', True), ('no-material', False)):
        ids = [i for k, a, _ in d if k == kw + suffix for i in a]
        if ids:
            wrong = [i for i in ids if (i in names) != present]
            result(not wrong, f'{kw + suffix}: {len(ids) - len(wrong)}/{len(ids)}'
                   + (f' wrong: {" ".join(wrong)}' if wrong else ''))
    sys.exit(0 if all(results) else 1)


def main():
    cmd, pack = sys.argv[1], sys.argv[2]
    if cmd == 'mods':
        cmd_mods(pack, sys.argv[3])
    elif cmd == 'jar':
        for kw, args, raw in parse(pack):
            if kw == sys.argv[3]:
                print(resolve(args, raw))
    elif cmd == 'get':
        for kw, args, raw in parse(pack):
            if kw == sys.argv[3]:
                print(raw or kw)
    elif cmd == 'check':
        cmd_check(pack, sys.argv[3], sys.argv[4], sys.argv[5] if len(sys.argv) > 5 else None)
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main()
