"""Minimal SNBT parser -> python values (typed numbers normalised to (value, suffix) via str)."""
import re
NUM = re.compile(r'-?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?[bBsSlLfFdD]?')
BARE = re.compile(r'[A-Za-z0-9_\-.+]+')
class P:
    def __init__(s, t): s.t, s.i = t, 0
    def ws(s):
        while s.i < len(s.t) and s.t[s.i].isspace(): s.i += 1
    def val(s):
        s.ws(); c = s.t[s.i]
        if c == '{':
            s.i += 1; d = {}
            while True:
                s.ws()
                if s.t[s.i] == '}': s.i += 1; return d
                k = s.key(); s.ws(); assert s.t[s.i] == ':', s.t[s.i-20:s.i+20]; s.i += 1
                d[k] = s.val(); s.ws()
                if s.t[s.i] == ',': s.i += 1
        if c == '[':
            s.i += 1; s.ws(); pre = ''
            if re.match(r'[BIL];', s.t[s.i:s.i+2]): pre = s.t[s.i]; s.i += 2
            l = []
            while True:
                s.ws()
                if s.t[s.i] == ']': s.i += 1; return (pre + 'arr', tuple(l)) if pre else l
                l.append(s.val()); s.ws()
                if s.t[s.i] == ',': s.i += 1
        if c in '"\'': return s.string()
        m = BARE.match(s.t, s.i); s.i = m.end(); tok = m.group(0)
        if NUM.fullmatch(tok):
            suf = tok[-1].lower() if tok[-1].isalpha() and tok[-1].lower() in 'bslfd' else ''
            body = tok[:-1] if suf else tok
            return ('num', suf, float(body))
        if tok in ('true', 'false'): return ('num', 'b', 1.0 if tok == 'true' else 0.0)
        return tok
    def key(s):
        s.ws()
        if s.t[s.i] in '"\'': return s.string()
        m = BARE.match(s.t, s.i); s.i = m.end(); return m.group(0)
    def string(s):
        q = s.t[s.i]; s.i += 1; out = []
        while s.t[s.i] != q:
            if s.t[s.i] == '\\': s.i += 1
            out.append(s.t[s.i]); s.i += 1
        s.i += 1; return ''.join(out)
def parse(t): return P(t).val()
