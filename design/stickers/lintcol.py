"""icon colour validator: every fill/stroke in an icon must be a palette token (pastel, mid, deep, trace), white or lemon-pastel."""
from engine import C, deep, tone


def allowed():
    a = {'#FFFFFF', '#FFF3A6', 'NONE'}
    for f in C:
        a |= {C[f][0], C[f][1], C[f][2], deep(f)}
    return a


def part_colours(p):
    fam = p.fam
    out = []
    def tc(t):
        if t is None:
            return None
        if fam in C:
            return tone(fam, t)
        return t
    out.append(tc(p.t))
    if p.band is not None:
        bt = p.band_t or ('m' if p.t == 'p' else 'p' if p.t in ('w',) else 'deep' if p.t == 'm' else 'm')
        out.append(tc(bt))
    if p.tex:
        out.append(tc(p.tex_t) if p.tex_t else (C[fam][1] if p.t in ('p', 'w', 'lp') else C[fam][0]) if fam in C else None)
    if p.det:
        out.append(tc(p.det_t) if p.det_t else (C[fam][2] if fam in C else fam))
    if p.ol and fam in C:
        out.append(C[fam][2])
    return [c for c in out if c]


def lint_colours(icons):
    ok, errs = allowed(), []
    for i in icons:
        bad = sorted({c.upper() for p in i.parts for c in part_colours(p) if c.startswith('#') and c.upper() not in ok})
        if bad:
            errs.append(f'colour: {i.key} uses non-token {", ".join(bad)}')
    return errs
