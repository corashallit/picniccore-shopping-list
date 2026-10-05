"""Procedural candy gloss.

For every icon: polygonise each part (with its transform). A gloss shines on one shape (its host) and stays clear of
everything else: it keeps MARGIN (1u) between its stroke and the host's colour-trace, and between its stroke and every
part painted over the host (printed labels, windows, icons, detail lines, texture stripes and the parts drawn in front,
each with its own outline). Necks and narrow rays are left out (the host is opened with a disc of NECK_K times its
inscribed radius). Along what is left, take the run of the inset contour that faces the upper-left (200-290 deg from
the centre, y down, in bbox-normalised space), split it wherever it turns sharply (no hooks, chevrons or hairpins), and
stroke the longest piece. Main shape: 2.6u + an r .8 dot 1.6u past the lower end, the dot at least 1u from any stroke
(past the upper end when the lower one is blocked; none when both are). Sub-shapes at least 10u wide: 2.2u, no dot.

`trim_gloss` is the same rule as a safety net for hand-placed gloss (decor, baskets, the launcher): a gloss stroke is
cut back where it would cross its host's trace or a part painted over it, and a dot that would sit on one is dropped.
"""
import math
from shapely.geometry import Polygon, MultiPolygon, LineString, Point
from shapely.ops import unary_union, polylabel
from shapely.prepared import prep
from svgelements import Path as SPath
from engine import parse_tf, apply_tf, fmt, G, DOT, tone

MAIN_W, SUB_W, DOT_R = 2.6, 2.2, .8
A0, A1 = 200, 290
MARGIN = 1.0          # the gloss stroke's distance from its host's colour-trace
INNER = .5            # ... and from every part painted over the host (labels, icons, seeds, detail lines, parts in front)
DOT_GAP = 1.0         # the dot's distance from any stroke
NECK_K = .55          # necks and rays narrower than 2 x NECK_K x the host's inscribed radius get no gloss
TURN_MAX = 50         # degrees of turn within TURN_LEN: a sharper turn splits the run
TURN_LEN = 1.2
# the main gloss's tiers: (gap to the host's trace, gap to parts painted over it, width, shortest run, windows or None
# for the usual 200-290 deg widening to 176-304 deg, what it keeps off: 'strict' = everything painted over the host,
# 'loose' = the same less the texture's long stripes (a candy's swirl), which light may run across; a gloss never
# crosses a trace, a detail line, a texture's short marks (seeds, flecks), a printed part or a part in front)
TIERS = ((MARGIN, INNER, MAIN_W, 10, None, ('strict', 'loose')),
         (.7, .4, MAIN_W, 6, None, ('strict', 'loose')),
         (.5, .3, 2.4, 4, ((160, 320),), ('strict', 'loose')))
# when no tier fits (a body whose upper left is busy with a label, a window, seeds): a 2u stroke in a narrower corridor,
# down the left side too; then, on the small round shape furthest up and left (a grape, a yolk, a scoop), a short arc
NARROW = (.5, .3, 2.0, 4.3, ((140, 320),), ('strict', 'loose'))
# ... then the same stroke further in, inside a seam or a rim line that runs along the edge (a perogy, a plate)
DEEP = tuple((m, .3, 2.0, 4.3, ((176, 304),), ('strict', 'loose')) for m in (2.0, 3.0, 4.0))
SMALL = (.35, .3, 2.0, 3.0, ((180, 300),), ('strict', 'loose'))
SMALL_R = 5.5         # the small arc only on shapes whose inscribed radius is under this
# the sub-glosses' tiers (the same fields): a 2.2u stroke at least 4u long, then hugging closer, then a 2u stroke
SUB_TIERS = ((MARGIN, INNER, SUB_W, 4.4, ((A0, A1), (186, 300)), ('strict', 'loose')),
             (.7, .4, SUB_W, 4.4, ((A0, A1), (186, 300)), ('strict', 'loose')),
             (.5, .3, 2.0, 4.4, ((170, 310),), ('strict', 'loose')))


def _poly_pts(s):
    t = s[0]
    if t == 'circle':
        cx, cy, r = s[1:4]
        return [[(cx + r * math.cos(i * math.pi / 36), cy + r * math.sin(i * math.pi / 36)) for i in range(72)]]
    if t == 'ellipse':
        cx, cy, rx, ry = s[1:5]
        rot = s[5] if len(s) > 5 and s[5] else 0
        M = parse_tf(f'rotate({rot} {cx} {cy})')
        return [[apply_tf(M, cx + rx * math.cos(i * math.pi / 36), cy + ry * math.sin(i * math.pi / 36)) for i in range(72)]]
    if t == 'rect':
        x, y, w, h = s[1:5]
        return None  # handled with buffer
    p = SPath(s[1])
    out = []
    for sp in p.as_subpaths():
        sp = SPath(sp)
        L = sp.length(error=1e-3) if sp.length(error=1e-2) else 0
        n = max(24, int(L / .35))
        pts = [sp.point(i / n) for i in range(n + 1)]
        pts = [(q.x, q.y) for q in pts if q is not None]
        if len(pts) >= 3:
            out.append(pts)
        elif pts:
            out.append(pts * 2)
    return out


def shape_poly(s, tf=None, w=None):
    """polygon of a part shape in icon space. w: stroke width for tubes (open paths)."""
    M = parse_tf(tf) if tf else None
    if w is not None:
        rings = _poly_pts(s)
        geo = unary_union([LineString(r).buffer(w / 2, cap_style=1, join_style=1) for r in rings if len(r) > 1])
    elif s[0] == 'rect':
        x, y, ww, hh = s[1:5]
        r = min(s[5] if len(s) > 5 else 0, ww / 2, hh / 2)
        geo = Polygon([(x + r, y + r), (x + ww - r, y + r), (x + ww - r, y + hh - r), (x + r, y + hh - r)])
        geo = geo.buffer(r, quad_segs=8) if r > 0 else Polygon([(x, y), (x + ww, y), (x + ww, y + hh), (x, y + hh)])
        if len(s) > 6 and s[6]:
            R = parse_tf(f'rotate({s[6]} {x + ww / 2} {y + hh / 2})')
            geo = Polygon([apply_tf(R, *p) for p in geo.exterior.coords])
    else:
        rings = _poly_pts(s)
        polys = [Polygon(r).buffer(0) for r in rings]
        geo = unary_union(polys) if polys else Polygon()
    if M and not geo.is_empty:
        def tr(g):
            if isinstance(g, MultiPolygon):
                return MultiPolygon([tr(x) for x in g.geoms])
            return Polygon([apply_tf(M, *p) for p in g.exterior.coords], [[apply_tf(M, *p) for p in i.coords] for i in g.interiors])
        geo = tr(geo).buffer(0)
    return geo


def _largest(g):
    if g.is_empty:
        return g
    if isinstance(g, Polygon):
        return g
    gs = [x for x in getattr(g, 'geoms', []) if isinstance(x, Polygon)]
    return max(gs, key=lambda x: x.area) if gs else Polygon()


def _runs(pts, ok):
    if all(ok):
        return [pts]
    k = ok.index(False)
    pts, ok = pts[k:] + pts[:k], ok[k:] + ok[:k]
    runs, cur = [], []
    for p, o in zip(pts, ok):
        if o:
            cur.append(p)
        elif cur:
            runs.append(cur); cur = []
    if cur:
        runs.append(cur)
    return runs


def _len(pts):
    return sum(math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1))


def _heading(a, b):
    return math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))


def _smooth_split(run):
    """split a run wherever it turns more than TURN_MAX within TURN_LEN (a hook, a chevron, a hairpin, a corner of a
    lumpy contour), so a gloss never folds back on itself."""
    if len(run) < 4:
        return [run]
    step = max(1, int(round(TURN_LEN / max(_len(run) / (len(run) - 1), 1e-6))))
    cuts = []
    i = step
    while i + step < len(run):
        h0, h1 = _heading(run[i - step], run[i]), _heading(run[i], run[i + step])
        if abs((h1 - h0 + 180) % 360 - 180) > TURN_MAX:
            cuts.append(i)
            i += step
        i += 1
    if not cuts:
        return [run]
    out, a = [], 0
    for c in cuts:
        out.append(run[a:c + 1])
        a = c
    out.append(run[a:])
    return [r for r in out if len(r) >= 2]


def _inradius(g):
    try:
        c = polylabel(g, tolerance=.2)
        return g.exterior.distance(c) if isinstance(g, Polygon) else g.boundary.distance(c)
    except Exception:  # pragma: no cover
        x0, y0, x1, y1 = g.bounds
        return min(x1 - x0, y1 - y0) / 2


def arc(host, ol, forbid, w, a0=A0, a1=A1, step=.2, margin=MARGIN, inner_margin=INNER):
    """the longest clean run of `host`'s inset contour facing the upper-left: inset so a stroke of width w keeps
    `margin` from the host's trace (of width ol), kept `inner_margin` away from `forbid` (everything painted over it),
    out of necks and rays, and split at sharp turns. Returns (pts, inner, clipped) or None; clipped tells whether a
    part painted over the host, a neck or a turn shortened the run."""
    t = ol / 2 + margin + w / 2
    R = _inradius(host)
    rr = max(0.0, min(1.6, R - t - .2))          # corners rounded by up to 1.6u, as r9 (less on slim shapes)
    inner = _largest(host.buffer(-(t + rr), quad_segs=12).buffer(rr, quad_segs=12))
    if inner.is_empty or inner.area < .5:
        return None
    k = NECK_K * R
    body = _largest(host.buffer(-k, quad_segs=12).buffer(k, quad_segs=12)) if k > t else host
    bi = _largest(body.buffer(-(t + rr), quad_segs=12).buffer(rr, quad_segs=12))
    if bi.is_empty:
        return None
    ring = LineString(inner.exterior.coords)
    L = ring.length
    n = max(8, int(L / step))
    x0, y0, x1, y1 = bi.bounds
    c = ((x0 + x1) / 2, (y0 + y1) / 2)
    hw, hh = max((x1 - x0) / 2, .1), max((y1 - y0) / 2, .1)
    pts = [ring.interpolate(i * L / n) for i in range(n)]
    pts = [(p.x, p.y) for p in pts]

    def ang(p):
        return math.degrees(math.atan2((p[1] - c[1]) / hh, (p[0] - c[0]) / hw)) % 360
    in_body = prep(body.buffer(.05))
    bad = prep(forbid.buffer(inner_margin + w / 2)) if forbid is not None and not forbid.is_empty else None
    win = [a0 <= ang(p) <= a1 for p in pts]
    ok = [wn and in_body.contains(Point(p)) and not (bad and bad.contains(Point(p))) for wn, p in zip(win, pts)]
    if not any(ok):
        return None
    runs = []
    for r in _runs(pts, ok):
        runs += _smooth_split(r)
    best = max(runs, key=_len)
    if len(best) < 3:
        return None
    if ang(best[0]) > ang(best[-1]):
        best = best[::-1]
    full = max(_runs(pts, win), key=_len) if any(win) else best
    clipped = _len(best) < _len(full) - .5
    return best, bi, clipped


def _path(pts):
    ls = LineString(pts).simplify(.12)
    q = list(ls.coords)
    # chaikin smoothing keeps ends fixed
    for _ in range(2):
        if len(q) < 3:
            break
        nq = [q[0]]
        for i in range(len(q) - 1):
            a, b = q[i], q[i + 1]
            nq += [(a[0] * .75 + b[0] * .25, a[1] * .75 + b[1] * .25), (a[0] * .25 + b[0] * .75, a[1] * .25 + b[1] * .75)]
        nq.append(q[-1])
        q = nq
    q = list(LineString(q).simplify(.1).coords)
    return 'M' + 'L'.join(f'{x:.1f} {y:.1f}'.replace('.0 ', ' ').replace('.0L', 'L') for x, y in q)


def is_white(icon_part):
    try:
        c = tone(icon_part.fam, icon_part.t) if icon_part.fam in __import__('engine').C else icon_part.t
    except Exception:
        c = icon_part.t
    return isinstance(c, str) and c.upper() in ('#FFFFFF', 'W')


# ---------------------------------------------------------------- what each part paints
class Paint:
    """one part's geometry in icon space: fill (fills only), paint (everything it puts down, outline and details
    included), strokes (its trace, detail lines and line or tube strokes: what a gloss must never cover), tex (its
    texture stripes, clipped to the fill)."""

    def __init__(self, p):
        self.p = p
        self.fill, self.tex = None, None
        geos, strokes, soft = [], [], []
        try:
            import engine as _en
            trace = _en.C[p.fam][2] if p.fam in _en.C else p.fam
            det_col = tone(p.fam, p.det_t) if (p.det_t and p.fam in _en.C) else (p.det_t or trace)
            soft_det = str(det_col).upper() != str(trace).upper()
            if p.kind == 'fill':
                g = shape_poly(p.s, p.tf)
                self.fill = g
                geos.append(g.buffer(p.ol / 2) if p.ol else g)
                if p.ol:
                    strokes.append(g.boundary.buffer(p.ol / 2))
                if p.tex:
                    self.tex = unary_union([shape_poly(('path', d), p.tf, w=p.tex_w) for d in p.tex]).intersection(g)
            else:
                st = shape_poly(p.s, p.tf, w=p.w)
                geos.append(st)
                strokes.append(st)
            dets = []
            for d in p.det:
                dg = shape_poly(('path', d), p.tf, w=p.det_w)
                (soft if soft_det else dets).append(dg)
                strokes.append(dg)
            geos += dets
        except Exception:  # pragma: no cover
            dets = []
        self.firm = unary_union(geos) if geos else Polygon()          # less detail lines in a non-trace colour
        self.soft = unary_union(soft) if soft else Polygon()          # detail lines in a non-trace colour (ridges, veins)
        self.det_firm = unary_union(dets) if dets else Polygon()      # detail lines in the trace colour
        self.paint = self.firm.union(self.soft) if soft else self.firm
        self.strokes = unary_union(strokes) if strokes else Polygon()


def paints(parts):
    return [Paint(p) for p in parts]


def _spots(tex):
    """the short marks of a texture (seeds, flecks, dimples, pores): a gloss never crosses those, though it may cross
    a long stripe (a candy's swirl) the way light runs over it"""
    if tex is None or tex.is_empty:
        return Polygon()
    keep = []
    for g in getattr(tex, 'geoms', [tex]):
        if g.is_empty or g.geom_type != 'Polygon':
            continue
        r = g.minimum_rotated_rectangle
        xs = list(r.exterior.coords) if r.geom_type == 'Polygon' else [(0, 0), (0, 0)]
        side = max((math.dist(xs[k], xs[k + 1]) for k in range(len(xs) - 1)), default=0)
        if side < 4.5:
            keep.append(g)
    return unary_union(keep) if keep else Polygon()


def forbidden(pp, i, tex=True, soft=True):
    """everything painted over part i: the parts drawn after it (whole) and its own detail lines (and texture).
    tex='spots' keeps out of the texture's short marks only (its long stripes may be crossed); soft=False leaves out
    detail lines in a non-trace colour."""
    geos = [(q.paint if soft else q.firm) for q in pp[i + 1:]]
    geos.append(pp[i].det_firm)
    if soft:
        geos.append(pp[i].soft)
    if tex == 'spots':
        geos.append(_spots(pp[i].tex))
    elif tex and pp[i].tex is not None and not pp[i].tex.is_empty:
        geos.append(pp[i].tex)
    geos = [g for g in geos if not g.is_empty]
    return unary_union(geos) if geos else Polygon()


def _host_geo(q):
    p = q.p
    if p.kind == 'fill':
        return q.fill, (p.ol or 0)
    if p.kind == 'tube':
        return q.paint, 0.0       # the tube's own trace is inside its stroke: the inset below starts at its edge + 2u
    return None, 0


def _dot(pp, i, inner, pts, w, forbid, host, ol):
    """(the dot, the run) : the dot past the run's lower end, else past its upper end, at least DOT_GAP from any
    stroke; when both are blocked the run is drawn back from its lower end (then its upper end), up to 3u and never
    under 4u, to make room. (None, pts) when there is no room at all."""
    from shapely.ops import substring
    t = ol / 2 + DOT_GAP + DOT_R + .1                 # the dot's own track: DOT_GAP inside the trace
    track = _largest(host.buffer(-t, quad_segs=12))
    if not track.is_empty:
        inner = track
    ring = LineString(inner.exterior.coords)
    L = ring.length
    room = prep(host.buffer(-(ol / 2 + DOT_GAP + DOT_R)))
    bad = prep(forbid.buffer(DOT_GAP + DOT_R)) if forbid is not None and not forbid.is_empty else None
    run = LineString(pts)

    def place(r, lower):
        end = r.coords[0] if lower else r.coords[-1]
        far = r.interpolate(min(1.5, r.length / 2) if lower else max(r.length - 1.5, r.length / 2))
        s0, s1 = ring.project(Point(end)), ring.project(far)
        dirn = -1 if (s1 - s0) % L < L / 2 else 1
        for extra in (0, .2, .4, .6, -.2, .8, 1.0):
            q = ring.interpolate((s0 + dirn * (w / 2 + 1.6 + DOT_R + extra)) % L)
            if room.contains(q) and not (bad and bad.contains(q)) and r.distance(q) >= w / 2 + DOT_R + 1.0:
                return q
        return None
    for back in (0, .6, 1.2, 1.8, 2.4, 3.0):
        if run.length - back < 4:
            break
        for lower in (True, False):
            r = substring(run, back, run.length) if lower else substring(run, 0, run.length - back)
            rp = list(r.coords)
            if len(rp) < 2:
                continue
            q = place(r, lower)
            if q is not None:
                return q, rp
    return None, pts


def _stroke_pts(g):
    lines, _ = _gloss_line(g)
    return [list(ln.coords) for ln in lines]


def _sharp(pts, limit=60):
    """True when a polyline turns more than `limit` degrees within TURN_LEN (a hook, a chevron, a hairpin)"""
    ln = LineString(pts)
    if ln.length < 2 * TURN_LEN:
        return False
    n = max(4, int(ln.length / .2))
    q = [ln.interpolate(k * ln.length / n) for k in range(n + 1)]
    q = [(p.x, p.y) for p in q]
    step = max(1, int(round(TURN_LEN / (ln.length / n))))
    for k in range(step, len(q) - step):
        h0, h1 = _heading(q[k - step], q[k]), _heading(q[k], q[k + step])
        if abs((h1 - h0 + 180) % 360 - 180) > limit:
            return True
    return False


def host_of(pp, geo):
    """the shape a gloss mark shines on: the topmost fill (die-cut or printed: a steak's meat over its fat rim) that
    holds nearly all of it, else the die-cut fill (or tube) it overlaps most: (index, host region, trace width)"""
    for i in range(len(pp) - 1, -1, -1):
        q = pp[i]
        if q.p.kind == 'fill' and q.fill is not None and not q.fill.is_empty and q.fill.intersection(geo).area >= .85 * geo.area:
            return i, q.fill, q.p.ol or 0
    best = None
    for i, q in enumerate(pp):
        p = q.p
        if not p.cut or p.kind == 'line':
            continue
        h, ol = _host_geo(q)
        if p.kind == 'tube':
            h, ol = shape_poly(p.s, p.tf, w=max(p.w - 4, .8) + 2), 2.0
        if h is None or h.is_empty:
            continue
        a = h.intersection(geo).area
        if a > 0 and (best is None or a > best[0]):
            best = (a, i, h, ol)
    return best[1:] if best else None


def mark_geom(g):
    if g[0] == 'g':
        lines, _ = _gloss_line(g)
        return unary_union([ln.buffer(g[2] / 2) for ln in lines]) if lines else Polygon()
    tf = g[4] if len(g) > 4 else None
    c = apply_tf(parse_tf(tf), g[1], g[2]) if tf else (g[1], g[2])
    return Point(c).buffer(g[3])


def clean(pp, g, host=None, trace_gap=.45, gap=.25, dot_gap=DOT_GAP, soft_ok=False, tex=False):
    """'' when a gloss mark is clean, else why not. A stroke stays inside its host, trace_gap off the host's trace,
    gap off everything painted over the host (labels, icons, detail lines, the parts drawn in front), out of necks and
    rays, and without sharp turns; a dot keeps dot_gap off all of those. Used by autogloss (to keep the r9 gloss where
    it is clean) and by validate.py."""
    geo = mark_geom(g)
    if geo.is_empty:
        return 'empty'
    h = host if host is not None else host_of(pp, geo)
    if h is None:
        return 'off the art'
    i, hg, ol = h
    F = forbidden(pp, i, tex, soft=not soft_ok)
    if g[0] == 'dot':
        if geo.difference(hg.buffer(-(ol / 2 + dot_gap))).area > .02:
            return 'dot on or by the trace'
        Fd = forbidden(pp, i, False)
        if not Fd.is_empty and geo.intersection(Fd.buffer(dot_gap)).area > .02:
            return 'dot on or by a part painted over it'
        return ''
    if geo.difference(hg.buffer(-(ol / 2 + trace_gap))).area > .1:
        return 'on the trace or off its shape'
    if not F.is_empty and geo.intersection(F.buffer(gap)).area > .1:
        return 'over a part painted on its shape'
    for pts in _stroke_pts(g):
        if _sharp(pts):
            return 'sharp turn'
    k = NECK_K * _inradius(hg)
    if k > ol / 2 + 2.3:
        body = _largest(hg.buffer(-k, quad_segs=12).buffer(k, quad_segs=12)).buffer(.3)
        if geo.difference(body).area > .25 * geo.area:
            return 'in a neck or a ray'
    return ''


def _head(icon):
    """a critter's or an item's face-like head (validate.HEADS) never takes a gloss"""
    import validate
    h = validate.HEADS.get(icon.key)
    return Point(h[0], h[1]).buffer(h[2]) if h else None


def _same_as_below(parts, geos, i):
    """a printed region with no outline in the very colour of the die-cut shape it sits on (a bottle's lower band): it
    has no edge of its own for a gloss to follow"""
    p = parts[i]
    if p.ol:
        return False
    col = tone(p.fam, p.t) if p.fam in __import__('engine').C else p.t
    for j in range(i - 1, -1, -1):
        q, g = parts[j], geos[j]
        if q.kind == 'fill' and q.cut and g is not None and not g.is_empty and g.intersection(geos[i]).area > .5 * geos[i].area:
            return str(tone(q.fam, q.t) if q.fam in __import__('engine').C else q.t).upper() == str(col).upper()
    return False


def _candidates(icon, pp):
    parts = icon.parts
    head = _head(icon)
    geos = []
    for q in pp:
        p = q.p
        geos.append(q.fill if p.kind == 'fill' else (shape_poly(p.s, p.tf, w=p.w + (p.ol or 0)) if p.kind == 'tube' else None))
    cands = []
    for i, p in enumerate(parts):
        g = geos[i]
        if p.kind not in ('fill', 'tube') or g is None or g.is_empty or g.area < 30:
            continue
        if not p.cut and (p.kind != 'fill' or is_white(p) or _same_as_below(parts, geos, i)):
            continue          # a printed colour region (a steak's meat, a label) may carry it; white, or a band in the body's colour, never
        # visible area (die-cut shapes drawn above hide it; printed regions are hidden by anything above) orders them
        above = [geos[j] for j in range(i + 1, len(parts)) if geos[j] is not None and not geos[j].is_empty and parts[j].kind != 'line' and
                 (parts[j].cut or not p.cut) and (parts[j].kind == 'tube' or geos[j].area >= 6)]
        vis = g.difference(unary_union(above)) if above else g
        vis = _largest(vis.buffer(-.05).buffer(.05))
        if vis.is_empty:
            continue
        host, ol = _host_geo(pp[i])
        if p.kind == 'tube':
            host = shape_poly(p.s, p.tf, w=max(p.w - 4, .8) + 2)     # the tube's fill, plus 1u: its trace is 2u
            ol = 2.0
        x0, y0, x1, y1 = vis.bounds
        cands.append(dict(i=i, vis=vis, host=host, ol=ol, area=vis.area, w=x1 - x0, white=is_white(p), tube=p.kind == 'tube',
                          strict=forbidden(pp, i, True), loose=forbidden(pp, i, 'spots'), soft=forbidden(pp, i, False, False)))
        if head is not None:
            for fk in ('strict', 'loose', 'soft'):
                cands[-1][fk] = cands[-1][fk].union(head)
    cands.sort(key=lambda d: -d['area'])
    return cands


def _span(inner, pts):
    bx0, by0, bx1, by1 = inner.bounds
    cx_, cy_, hw_, hh_ = (bx0 + bx1) / 2, (by0 + by1) / 2, max((bx1 - bx0) / 2, .1), max((by1 - by0) / 2, .1)
    angs = [math.degrees(math.atan2((q[1] - cy_) / hh_, (q[0] - cx_) / hw_)) % 360 for q in pts]
    return abs(sum((angs[k + 1] - angs[k] + 180) % 360 - 180 for k in range(len(angs) - 1)))


def _cap_span(pts, inner, limit=125.0):
    """a run trimmed to at most `limit` degrees of its shape's contour (35 %), shortened from whichever end lies
    further from the upper-left (245 deg)"""
    pts = list(pts)
    bx0, by0, bx1, by1 = inner.bounds
    cx_, cy_, hw_, hh_ = (bx0 + bx1) / 2, (by0 + by1) / 2, max((bx1 - bx0) / 2, .1), max((by1 - by0) / 2, .1)

    def ang(q):
        return math.degrees(math.atan2((q[1] - cy_) / hh_, (q[0] - cx_) / hw_)) % 360
    while len(pts) > 3 and _span(inner, pts) > limit:
        if abs(ang(pts[0]) - 245) > abs(ang(pts[-1]) - 245):
            pts = pts[1:]
        else:
            pts = pts[:-1]
    return pts


def autogloss(icon, report=None):
    """the r9 gloss (gloss_r9) where it passes `clean`, else the clipped gloss of this module: see the docstring"""
    import gloss_r9
    rep0 = []
    old = gloss_r9.autogloss(icon, rep0)
    info0 = rep0[0]
    parts = icon.parts
    pp = paints(parts)
    cands = _candidates(icon, pp)
    by_i = {d['i']: d for d in cands}
    out, info = [], dict(key=icon.key, main=None, subs=[])
    RANGES = ((A0, A1), (190, 296), (176, 304))
    # the r9 list: main, its dot, then the sub-glosses in info0['subs'] order
    k = 0
    old_main = old_dot = None
    if info0['main']:
        old_main, k = old[0], 1
        if info0.get('dot'):
            old_dot, k = old[1], 2
    old_subs = list(zip(old[k:], info0['subs']))
    lines = []
    main = None
    kept_main = False
    mi = info0['main']['part'] if info0['main'] else None
    if old_main is not None and mi is not None and mi >= 0:
        h = host_of(pp, mark_geom(old_main))
        if h is not None and h[0] in by_i:
            mi = h[0]
        d = by_i.get(mi)
        info['r9'] = clean(pp, old_main, (mi, d['host'], d['ol']), tex='spots') if d else 'host'
        if not info['r9']:
            pts = _stroke_pts(old_main)[0]
            out.append(old_main)
            kept_main = True
            info['main'] = dict(info0['main'], clipped=False)
            main = (d, pts)
            dot = old_dot if (old_dot is not None and not clean(pp, old_dot, (mi, d['host'], d['ol']))) else None
            if dot is None:
                dist = d['host'].exterior.distance(Point(pts[0])) if isinstance(d['host'], Polygon) else 3.0
                inner = _largest(d['host'].buffer(-dist, quad_segs=12))
                q, rp = _dot(pp, mi, inner, pts, old_main[2], d['loose'], d['host'], d['ol']) if not inner.is_empty else (None, pts)
                dot = DOT(round(q.x, 2), round(q.y, 2), DOT_R) if q is not None else None
                if q is not None and rp != pts:          # drawn back to make room for its dot
                    pts = rp
                    out[-1] = G(_path(pts), old_main[2])
                    main = (d, pts)
                    info['main']['len'] = round(_len(pts), 1)
                    info['main']['clipped'] = True
            if dot is not None:
                out.append(dot)
                info['dot'] = True
            else:
                info['dot_blocked'] = True
            lines.append(pts)

    def try_main(margin, inner_margin, w, min_len, ranges, keys, order=None, small=False, colour=False):
        """the first host (largest first) with a clean run at least min_len long: avoiding texture if it can.
        colour: only a host of colour (the last resorts are there to be seen: white on white never shows)"""
        for d in (order or cands):
            if d['tube'] or (small and _inradius(d['host']) >= SMALL_R) or (colour and d['white']):
                continue
            for fk in keys:
                for a0, a1 in ranges:
                    r = arc(d['host'], d['ol'], d[fk], w, a0, a1, margin=margin, inner_margin=inner_margin)
                    if r:
                        r = (_cap_span(r[0], r[1]), r[1], r[2])
                    if r and _len(r[0]) >= min_len:
                        return d, r, fk, w
        return None
    if main is None:
        # TIERS: the full rule first (1u off the trace, a 10u run); then slim or busy shapes (bananas, penne, a
        # labelled jar) hug closer and settle for a shorter run; a gloss never crosses a stroke at any tier
        found, kind = None, ''
        for margin, inner_margin, w, min_len, ranges, keys in TIERS:
            found = try_main(margin, inner_margin, w, min_len, ranges if ranges else RANGES, keys)
            if found:
                break
        if not found:
            found, kind = try_main(*NARROW, colour=True), 'narrow'
        for tier in (DEEP if not found else ()):
            found = try_main(*tier, colour=True)
            if found:
                break
        if not found:
            ul = sorted(cands, key=lambda d: d['vis'].centroid.x + d['vis'].centroid.y)
            found, kind = try_main(*SMALL, order=ul, small=True, colour=True), 'small'
        if not found:
            # a white body with no colour shape that can take one (a hummus tub): the narrow stroke on the white
            found, kind = try_main(*NARROW), 'narrow'
        if found:
            d, (pts, inner, clipped), fk, mw = found
            # a run along a wavy edge (a bean pod's bumps) is drawn as one smooth stroke where that stays clean
            sm = list(LineString(pts).simplify(.45).coords)
            if len(sm) >= 2 and _len(pts) > 1.03 * _len(sm) and not clean(pp, G(_path(sm), mw), (d['i'], d['host'], d['ol']), trace_gap=.35, gap=.15):
                pts = sm
            q, rp = _dot(pp, d['i'], inner, pts, mw, d['loose'], d['host'], d['ol'])
            if q is not None:
                pts = rp
            out.append(G(_path(pts), mw))
            info['main'] = dict(part=d['i'], w=mw, len=round(_len(pts), 1), span=round(_span(inner, pts), 1),
                                clipped=clipped or fk != 'strict' or _len(pts) < 10 or bool(kind), soft=fk == 'soft')
            if kind:
                info['main'][kind] = True
            if q is not None:
                out.append(DOT(round(q.x, 2), round(q.y, 2), DOT_R))
                info['dot'] = True
            else:
                info['dot_blocked'] = True
            lines.append(pts)
            main = (d, pts)
    used = {main[0]['i']} if main else set()
    limit = getattr(icon, 'subs', 2)
    # the r9 sub-glosses that are clean and keep off the main gloss
    dropped = False
    for g, si in old_subs:
        pi = si['part']
        h = by_i.get(pi)
        hh = (pi, h['host'], h['ol']) if h else None
        if hh is None and si.get('printed'):
            hh = (pi, pp[pi].fill, parts[pi].ol or 0)
        pts = _stroke_pts(g)[0]
        if hh is not None and not clean(pp, g, hh, tex='spots') and all(LineString(pl).distance(LineString(pts)) >= 2.4 for pl in lines):
            out.append(g)
            info['subs'].append(si)
            lines.append(pts)
            used.add(pi)
        else:
            dropped = True
    if dropped or not kept_main:
        def try_sub(host, ol, fz):
            """a clean sub-gloss run on one host, the best tier first, kept 2.4u off the other glosses"""
            for margin, inner_margin, w, min_len, ranges, keys in SUB_TIERS:
                for fk in keys:
                    for a0, a1 in ranges:
                        r = arc(host, ol, fz[fk], w, a0, a1, margin=margin, inner_margin=inner_margin)
                        if not r:
                            continue
                        pts = _cap_span(r[0], r[1])
                        if _len(pts) >= min_len and all(LineString(pl).distance(LineString(pts)) >= 2.4 for pl in lines):
                            return pts, w
            return None
        subs = len(info['subs'])
        need_colour = (main is None or main[0]['white']) and not any(
            not s.get('printed') and s['part'] in by_i and not by_i[s['part']]['white'] for s in info['subs'])
        for d in cands:
            if d['i'] in used or subs >= limit or d['tube']:
                continue
            if d['w'] < (7 if (need_colour and not d['white']) else 10):
                continue
            if d['white'] and not need_colour and subs >= 1:
                continue
            got = try_sub(d['host'], d['ol'], d)
            if not got:
                continue
            pts, w = got
            out.append(G(_path(pts), w))
            info['subs'].append(dict(part=d['i'], len=round(_len(pts), 1)))
            lines.append(pts)
            subs += 1
            used.add(d['i'])
            if not d['white']:
                need_colour = False
        if need_colour and not any(s.get('printed') for s in info['subs']):
            # white-bodied sticker: let a printed colour panel carry a 2.2u gloss so it still reads glossy
            extra = []
            for i, p in enumerate(parts):
                q = pp[i]
                if p.kind != 'fill' or p.cut or q.fill is None or q.fill.is_empty or q.fill.area < 40 or is_white(p):
                    continue
                x0, y0, x1, y1 = q.fill.bounds
                if x1 - x0 < 8:
                    continue
                extra.append((q.fill.area, i))
            for _, i in sorted(extra, key=lambda e: -e[0]):
                fz = dict(strict=forbidden(pp, i, True), loose=forbidden(pp, i, 'spots'), soft=forbidden(pp, i, False, False))
                got = try_sub(pp[i].fill, parts[i].ol or 0, fz)
                if got:
                    pts, w = got
                    out.append(G(_path(pts), w))
                    info['subs'].append(dict(part=i, len=round(_len(pts), 1), printed=True))
                    lines.append(pts)
                    break
    info['legacy'] = kept_main
    if report is not None:
        report.append(info)
    return out


# ---------------------------------------------------------------- hand-placed gloss
def _gloss_line(g):
    tf = g[-1] if isinstance(g[-1], str) and len(g) in (4, 5) else None
    rings = _poly_pts(('path', g[1]))
    M = parse_tf(tf) if tf else None
    lines = []
    for r in rings:
        pts = [apply_tf(M, *q) for q in r] if M else list(r)
        if len(pts) >= 2:
            lines.append(LineString(pts))
    return lines, tf


def trim_gloss(parts, gloss, margin=.5):
    """cut hand-placed gloss back to its host (decor, baskets, the launcher): a mark that is `clean` stays exactly as
    written; a stroke that is not is cut back to its longest piece that keeps `margin` off its host's trace and off
    everything painted over the host (and goes when that piece is under 2u or still not clean); a dot that is not
    clean goes."""
    pp = paints(parts)
    out = []
    for g in gloss:
        why = clean(pp, g, trace_gap=.35, gap=.15, tex='spots')
        if not why:
            out.append(g)
            continue
        if g[0] == 'dot':
            continue
        lines, tf = _gloss_line(g)
        if tf or not lines:            # a transformed hand gloss that needs trimming: drop it rather than re-derive it
            continue
        h = host_of(pp, mark_geom(g))
        if h is None:
            continue
        i, host, ol = h
        bad = forbidden(pp, i, False)
        core = host.buffer(-(ol / 2 + margin + g[2] / 2)).difference(bad.buffer(margin + g[2] / 2))
        pieces = []
        for ln in lines:
            x = ln.intersection(core)
            for part in getattr(x, 'geoms', [x]):
                if isinstance(part, LineString) and part.length >= 2:
                    pieces.append(part)
        for piece in sorted(pieces, key=lambda s: -s.length):
            for run in _smooth_split(list(piece.coords)):
                if _len(run) < 2:
                    continue
                cand = G(_path(run), g[2])
                if not clean(pp, cand, (i, host, ol), trace_gap=.35, gap=.15, tex='spots'):
                    out.append(cand)
                    break
            else:
                continue
            break
    return out


def lint(infos):
    errs = []
    for f in infos:
        m = f['main']
        if not m:
            errs.append(f'gloss: {f["key"]} has no main gloss')
        elif m.get('narrow') or m.get('small'):
            if m['w'] < 2.0 or m['len'] < (SMALL[3] if m.get('small') else NARROW[3]):
                errs.append(f'gloss: {f["key"]} main gloss {m["w"]}u x {m["len"]}u (a narrow one needs >=2u wide, >=4u long; '
                            f'on a small shape >=3u)')
        elif m['w'] < 2.4 or m['len'] < (4 if m.get('clipped') else 10):
            errs.append(f'gloss: {f["key"]} main gloss {m["w"]}u x {m["len"]}u (needs >=2.4u wide, >=10u long)')
    return errs
