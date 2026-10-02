"""Procedural candy gloss.
For every icon: polygonise each part (with its transform), subtract the parts drawn above it, inset the visible
region so the stroke clears the 2u colour-trace, take the arc of the inset contour that faces the upper-left
(200-290 deg from the region's centroid, y down) and stroke it. Main shape: 2.6u + an r .8 dot 1.6u past the
lower end. Sub-shapes at least 10u wide: 2.2u, no dot. Narrower shapes get none."""
import math
from shapely.geometry import Polygon, MultiPolygon, LineString, Point
from shapely.ops import unary_union
from svgelements import Path as SPath
from engine import parse_tf, apply_tf, fmt, G, DOT, tone

MAIN_W, SUB_W, DOT_R = 2.6, 2.2, .8
A0, A1 = 200, 290


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


def _ang(p, c):
    return math.degrees(math.atan2(p[1] - c[1], p[0] - c[0])) % 360


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


def arc(region, inset, a0=A0, a1=A1, step=.2, clip=None):
    """longest run of the inset contour whose bearing from the centre lies in [a0, a1]. Bearings are taken in
    bbox-normalised space so long and tall shapes get the same share of their contour as round ones.
    clip: the visible region; the run is cut where a shape drawn above covers it."""
    g = _largest(region.buffer(-(inset + 1.6), quad_segs=12).buffer(1.6, quad_segs=12))
    if g.is_empty or g.area < .5:
        return None
    ring = LineString(g.exterior.coords)
    L = ring.length
    n = max(8, int(L / step))
    x0, y0, x1, y1 = g.bounds
    c = ((x0 + x1) / 2, (y0 + y1) / 2)
    hw, hh = max((x1 - x0) / 2, .1), max((y1 - y0) / 2, .1)
    pts = [ring.interpolate(i * L / n) for i in range(n)]
    pts = [(p.x, p.y) for p in pts]

    def ang(p):
        return math.degrees(math.atan2((p[1] - c[1]) / hh, (p[0] - c[0]) / hw)) % 360
    keep = clip.buffer(-(inset - 1.0)) if clip is not None else None
    ok = [a0 <= ang(p) <= a1 and (keep is None or keep.contains(Point(p))) for p in pts]
    if not any(ok):
        return None
    runs = _runs(pts, ok)
    best = max(runs, key=_len)
    if len(best) < 3:
        return None
    if ang(best[0]) > ang(best[-1]):
        best = best[::-1]
    return best, g, c


def _len(pts):
    return sum(math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1))


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


def autogloss(icon, report=None):
    parts = icon.parts
    geos = []
    for p in parts:
        try:
            if p.kind == 'fill':
                g = shape_poly(p.s, p.tf)
            elif p.kind == 'tube':
                g = shape_poly(p.s, p.tf, w=p.w + (p.ol or 0))
            else:
                g = None
        except Exception as e:  # pragma: no cover
            g = None
        geos.append(g)
    cands = []
    for i, p in enumerate(parts):
        g = geos[i]
        if p.kind not in ('fill', 'tube') or not p.cut or g is None or g.is_empty or g.area < 30:
            continue
        # only separate die-cut shapes drawn above hide this one; printed details (cut=False) sit under the laminate gloss
        above = [geos[j] for j in range(i + 1, len(parts)) if geos[j] is not None and not geos[j].is_empty and parts[j].kind != 'line' and
                 parts[j].cut and (parts[j].kind == 'tube' or geos[j].area >= 6)]
        vis = g.difference(unary_union(above)) if above else g
        vis = _largest(vis.buffer(-.05).buffer(.05))
        if vis.is_empty:
            continue
        x0, y0, x1, y1 = vis.bounds
        cands.append(dict(i=i, vis=vis, full=g, area=vis.area, w=x1 - x0, white=is_white(p), tube=p.kind == 'tube'))
    cands.sort(key=lambda d: -d['area'])
    out, info = [], dict(key=icon.key, main=None, subs=[])
    main = None
    RANGES = ((A0, A1), (190, 296), (176, 304))

    def try_main(regions, insets):
        for d in regions:
            for ins in insets:
                for a0, a1 in RANGES:
                    r = arc(d['full'], ins, a0, a1, clip=d['vis'])          # hug the shape's own contour
                    if not r or _len(r[0]) < 10:
                        r = arc(d['vis'], ins, a0, a1)                    # else the visible part's contour
                    if r and _len(r[0]) >= 10:
                        return (d, r)
        return None
    main = try_main(cands, [1 + MAIN_W / 2 + .7])
    if main is None:   # slim main shapes (bananas, carrots, penne): hug closer to the trace
        main = try_main(cands, [2.6, 2.2, 1.9])
    if main is None:   # clusters (grapes, pretzel loops): gloss the whole die-cut silhouette
        sil = unary_union([g for p, g in zip(parts, geos) if g is not None and not g.is_empty and p.cut and p.kind != 'line'])
        hull = sil.convex_hull
        d = dict(i=-1, vis=hull, full=hull, area=sil.area, w=0, white=False, tube=False)
        main = try_main([d], [3.0, 2.4, 2.0])
    if main is None and cands:
        best = None
        for d in cands:
            for ins in (3.0, 2.4, 2.0, 1.7):
                r = arc(d['full'], ins, 170, 310, clip=d['vis']) or arc(d['vis'], ins, 170, 310)
                if r and (best is None or _len(r[0]) > _len(best[1][0])):
                    best = (d, r)
        main = best
    if main:
        d, (pts, g, c) = main
        out.append(G(_path(pts), MAIN_W))
        bx0, by0, bx1, by1 = g.bounds
        cx_, cy_, hw_, hh_ = (bx0 + bx1) / 2, (by0 + by1) / 2, max((bx1 - bx0) / 2, .1), max((by1 - by0) / 2, .1)
        angs = [math.degrees(math.atan2((q[1] - cy_) / hh_, (q[0] - cx_) / hw_)) % 360 for q in pts]
        span = sum((angs[k + 1] - angs[k] + 180) % 360 - 180 for k in range(len(angs) - 1))
        # span: the share of the contour the main gloss covers, in degrees around the (bbox-normalised) shape (coverage lint)
        info['main'] = dict(part=d['i'], w=MAIN_W, len=round(_len(pts), 1), span=round(abs(span), 1))
        # dot: continue along the inset ring past the lower-left end
        ring = LineString(g.exterior.coords)
        s0 = ring.project(Point(pts[0]))
        s1 = ring.project(Point(pts[1]))
        dirn = -1 if (s1 - s0) % ring.length < ring.length / 2 else 1
        q = ring.interpolate((s0 + dirn * (MAIN_W / 2 + 1.6 + DOT_R)) % ring.length)
        if d['vis'].buffer(-.9).contains(q):
            out.append(DOT(round(q.x, 2), round(q.y, 2), DOT_R))
            info['dot'] = True
    used = {main[0]['i']} if main else set()
    info['_lines'] = [dict(pts=main[1][0])] if main else []
    subs = 0
    need_colour = main is not None and main[0]['white']
    for d in cands:
        if d['i'] in used or d['w'] < 10 or subs >= 2:
            continue
        if d['white'] and not need_colour and subs >= 1:
            continue
        if d['tube']:
            continue
        r = arc(d['full'], 1 + SUB_W / 2 + .6, clip=d['vis'])
        if not r or _len(r[0]) < 4:
            r = arc(d['vis'], 1 + SUB_W / 2 + .6)
        if not r or _len(r[0]) < 4:
            r = arc(d['vis'], 1 + SUB_W / 2 + .4, 186, 300)
        if not r or _len(r[0]) < 4:
            continue
        # keep sub glosses off the main gloss
        line = LineString(r[0])
        if any(LineString(pp).distance(line) < 2.4 for pp in [x['pts'] for x in info.get('_lines', [])]):
            continue
        out.append(G(_path(r[0]), SUB_W))
        info['subs'].append(dict(part=d['i'], len=round(_len(r[0]), 1)))
        info.setdefault('_lines', []).append(dict(pts=r[0]))
        subs += 1
        used.add(d['i'])
        if not d['white']:
            need_colour = False
    if need_colour:   # white-bodied sticker: let a printed colour panel carry a 2.2u gloss so it still reads glossy
        extra = []
        for i, p in enumerate(parts):
            g = geos[i]
            if p.kind != 'fill' or p.cut or g is None or g.is_empty or g.area < 40 or is_white(p):
                continue
            x0, y0, x1, y1 = g.bounds
            if x1 - x0 < 10:
                continue
            above = [geos[j] for j in range(i + 1, len(parts)) if geos[j] is not None and not geos[j].is_empty and parts[j].kind != 'line' and geos[j].area >= 6]
            vis = _largest(g.difference(unary_union(above)) if above else g)
            extra.append((vis.area, i, g, vis))
        for _, i, g, vis in sorted(extra, key=lambda e: -e[0]):
            r = arc(g, 1 + SUB_W / 2 + .5, clip=vis) or arc(vis, 1 + SUB_W / 2 + .5)
            if r and _len(r[0]) >= 4 and all(LineString(x['pts']).distance(LineString(r[0])) >= 2.4 for x in info['_lines']):
                out.append(G(_path(r[0]), SUB_W))
                info['subs'].append(dict(part=i, len=round(_len(r[0]), 1), printed=True))
                break
    info.pop('_lines', None)
    if report is not None:
        report.append(info)
    return out


def lint(infos):
    errs = []
    for f in infos:
        m = f['main']
        if not m:
            errs.append(f'gloss: {f["key"]} has no main gloss')
        elif m['w'] < 2.4 or m['len'] < 10:
            errs.append(f'gloss: {f["key"]} main gloss {m["w"]}u x {m["len"]}u (needs >=2.4u wide, >=10u long)')
    return errs
