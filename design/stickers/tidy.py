"""Inner lines stop short of the outlines (docs/DESIGN_STICKER.md §3.1, Limits).

A band, label or gusset line drawn as a part's detail line (or as a printed line) whose round cap would sit on a
colour-trace is cut back so its cap keeps LINE_GAP from that trace: its end lands about 1.5u (LINE_GAP plus half its
width) inside the outline, and a line that would poke out of its shape is cut back inside. Lines that are clear already
are left exactly as written; a trimmed line keeps its own curves (the cut is made on the Bezier, not resampled).

Applied to every item sticker before the gloss is generated (icons.py), so the mockup, the emitter, the gloss and the
validators all see the same lines."""
import math
from shapely.geometry import Point
from shapely.ops import unary_union
from shapely.prepared import prep
from svgelements import Path as SPath, Move, Line, QuadraticBezier, CubicBezier, Arc, Close

from engine import fmt, parse_tf, apply_tf

LINE_GAP = .75        # a trimmed line's cap keeps this much off the trace
MIN_PIECE = 1.2       # shorter leftovers are dropped


def _lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def _split(pts, t0, t1):
    """control points of the piece t0..t1 of a Bezier (de Casteljau)"""
    def cut(ps, t):
        left, right, cur = [ps[0]], [ps[-1]], list(ps)
        while len(cur) > 1:
            cur = [_lerp(cur[i], cur[i + 1], t) for i in range(len(cur) - 1)]
            left.append(cur[0]); right.append(cur[-1])
        return left, right[::-1]
    _, right = cut(pts, t0)
    if t1 >= 1:
        return right
    u = (t1 - t0) / (1 - t0) if t0 < 1 else 1
    left, _ = cut(right, u)
    return left


def _segments(d):
    """subpaths as lists of Bezier control-point tuples (lines are 2-point Beziers, arcs become cubics)"""
    subs = []
    for sp in SPath(d).as_subpaths():
        segs = []
        for s in sp:
            if isinstance(s, Move):
                continue
            if isinstance(s, (Line, Close)):
                if s.start is not None and s.end is not None and (s.start.x, s.start.y) != (s.end.x, s.end.y):
                    segs.append(((s.start.x, s.start.y), (s.end.x, s.end.y)))
            elif isinstance(s, QuadraticBezier):
                segs.append(((s.start.x, s.start.y), (s.control.x, s.control.y), (s.end.x, s.end.y)))
            elif isinstance(s, CubicBezier):
                segs.append(((s.start.x, s.start.y), (s.control1.x, s.control1.y), (s.control2.x, s.control2.y), (s.end.x, s.end.y)))
            elif isinstance(s, Arc):
                for c in s.as_cubic_curves():
                    segs.append(((c.start.x, c.start.y), (c.control1.x, c.control1.y), (c.control2.x, c.control2.y), (c.end.x, c.end.y)))
        if segs:
            subs.append(segs)
    return subs


def _point(seg, t):
    cur = list(seg)
    while len(cur) > 1:
        cur = [_lerp(cur[i], cur[i + 1], t) for i in range(len(cur) - 1)]
    return cur[0]


def _write(pieces):
    """[(segment, t0, t1)] -> path data"""
    out = ''
    for k, (seg, t0, t1) in enumerate(pieces):
        ps = _split(seg, t0, t1)
        if k == 0:
            out += f'M{fmt(ps[0][0])} {fmt(ps[0][1])}'
        tail = ' '.join(f'{fmt(x)} {fmt(y)}' for x, y in ps[1:])
        out += {2: 'L', 3: 'Q', 4: 'C'}[len(ps)] + tail
    return out


def trim_path(d, ok, tf=None, step=.1):
    """the pieces of path d (local coordinates; tf maps them to icon space) where ok(point in icon space) holds,
    each kept on its own curves; d itself when every point is ok. Returns (new d or '' , changed)."""
    M = parse_tf(tf) if tf else None
    out, changed = [], False      # out: per subpath, the path data of what is kept
    for segs in _segments(d):
        samples = []
        for si, seg in enumerate(segs):
            L = sum(math.dist(_point(seg, i / 8), _point(seg, (i + 1) / 8)) for i in range(8))
            n = max(2, int(math.ceil(L / step)))
            for i in range(n + 1):
                if si and i == 0:
                    continue
                t = i / n
                p = _point(seg, t)
                q = apply_tf(M, *p) if M else p
                samples.append((si, t, p, ok(q)))
        total = sum(math.dist(samples[i][2], samples[i + 1][2]) for i in range(len(samples) - 1))
        if all(s[3] for s in samples) or total < 1.0:      # clear, or a dot (h.1), which is not a line with ends
            out.append(_write([(seg, 0.0, 1.0) for seg in segs]))
            continue
        runs, cur = [], []
        for s in samples:
            if s[3]:
                cur.append(s)
            elif cur:
                runs.append(cur); cur = []
        if cur:
            runs.append(cur)
        total = sum(math.dist(samples[i][2], samples[i + 1][2]) for i in range(len(samples) - 1))
        kept = 0.0
        for r in runs:
            length = sum(math.dist(r[i][2], r[i + 1][2]) for i in range(len(r) - 1))
            kept += length if length >= MIN_PIECE else 0.0
        if total > 0 and kept < KEEP_MIN * total and total > 1.0:
            out.append(_write([(seg, 0.0, 1.0) for seg in segs]))     # mostly on a trace by design: leave it
            continue
        changed = True
        for r in runs:
            length = sum(math.dist(r[i][2], r[i + 1][2]) for i in range(len(r) - 1))
            if length < MIN_PIECE:
                continue
            pieces = []
            (s0, t0), (s1, t1) = (r[0][0], r[0][1]), (r[-1][0], r[-1][1])
            for si in range(s0, s1 + 1):
                a = t0 if si == s0 else 0.0
                b = t1 if si == s1 else 1.0
                if b - a > 1e-6:
                    pieces.append((segs[si], a, b))
            if pieces:
                out.append(_write(pieces))
    if not changed:
        return d, False
    return ''.join(out), True


KEEP_MIN = .7        # a line is cut back only when at least this share of it is left; else it is left as written


def colour_of_trace(p):
    import engine as _en
    return str(_en.C[p.fam][2] if p.fam in _en.C else p.fam).upper()


def colour_of_line(p):
    """the colour a part's detail lines (fill) or its stroke (line) are drawn in"""
    import engine as _en
    if p.kind == 'line':
        return str(_en.tone(p.fam, p.t) if p.fam in _en.C else p.t).upper()
    if p.det_t:
        return str(_en.tone(p.fam, p.det_t) if p.fam in _en.C else p.det_t).upper()
    return colour_of_trace(p)


def visible_traces(pp, unlike=None):
    """the colour-traces you see: each fill part's outline, less what the parts drawn after it cover (their fills and
    their line strokes: a prohibition ring drawn over a label's outline hides it). unlike: only the traces of another
    colour (a line meeting a trace of its own colour merges into it, a T-junction; one of another colour shows its
    round cap on the outline)"""
    rings = []
    for i, q in enumerate(pp):
        if unlike is not None and q.p.kind == 'fill' and q.p.ol and colour_of_trace(q.p) == unlike:
            continue
        p = q.p
        if p.kind != 'fill' or not p.ol or q.fill is None or q.fill.is_empty:
            continue
        ring = q.fill.boundary.buffer(p.ol / 2)
        later = []
        for r in pp[i + 1:]:
            if r.p.kind == 'fill' and r.fill is not None and not r.fill.is_empty:
                later.append(r.fill)
            elif r.p.kind in ('line', 'tube') and not r.paint.is_empty:
                later.append(r.paint)
        if later:
            ring = ring.difference(unary_union(later))
        if not ring.is_empty:
            rings.append(ring)
    return unary_union(rings) if rings else None


def inset_lines(icon):
    """cut back the detail lines and printed lines of one sticker (see the module docstring); returns the number of
    lines changed."""
    import gloss as GL
    parts = icon.parts
    pp = GL.paints(parts)
    fills = [q.fill for q in pp]
    art = unary_union([f for f, p in zip(fills, parts) if f is not None and p.cut and not f.is_empty])
    changed, gone = 0, []
    cache = {}
    for i, p in enumerate(parts):
        jobs = []
        if p.kind == 'fill' and p.det:
            jobs.append(('det', p.det_w))
        if p.kind == 'line' and not p.cut:
            jobs.append(('line', p.w))
        for what, w in jobs:
            col = colour_of_line(p)
            if col not in cache:
                cache[col] = visible_traces(pp, unlike=col)
            traces = cache[col]
            # an end may run into a trace of its own colour (a T-junction) but never past the outline's outer edge
            inside = prep(art.buffer(max(0.0, 1.0 - w / 2 - .1)))
            bad = prep(traces.buffer(LINE_GAP + w / 2)) if traces is not None else None
            # what the parts drawn after this one paint over hides the line there: those stretches stay as they are
            cover = [q.paint for q in pp[i + 1:] if not q.paint.is_empty]
            hidden = prep(unary_union(cover).buffer(-w / 2)) if cover else None

            def ok(q, inside=inside, bad=bad, hidden=hidden):
                pt = Point(q)
                return (hidden is not None and hidden.contains(pt)) or (inside.contains(pt) and not (bad and bad.contains(pt)))
            if what == 'det':
                new = []
                for d in p.det:
                    nd, ch = trim_path(d, ok, p.tf)
                    if ch:
                        changed += 1
                    if nd:
                        new.append(nd)
                p.det = new
            else:
                nd, ch = trim_path(p.s[1], ok, p.tf)
                if ch:
                    changed += 1
                    if nd:
                        p.s = ('path', nd)
                    else:
                        gone.append(i)
    if gone:
        icon.parts = [p for i, p in enumerate(parts) if i not in gone]
    return changed
