"""Android emitter: turns the sticker DSL into VectorDrawable XML (docs/DESIGN_STICKER.md §3.2, §18.3 step 2).

Two front ends feed one writer:

* `icon_layers(icon)` walks a DSL `Icon` (or basket layer) part by part, mirroring `engine.Art.part()` at stage 6,
  and returns primitives sorted into the §3.2 groups. Every `<use>` the mockup would make is inlined, every shape
  (circle, ellipse, rect, path) becomes path data, every transform is baked into the coordinates (arcs become
  cubics), and colours are resolved to literal tokens.
* `flatten_svg(snippet)` does the same for the plain SVG snippets of the DSL (pictograms, toolbar glyphs, pattern
  tiles, the launcher ground): inheritance of presentation attributes, `currentColor`, transforms.

`vector_xml()` writes the result. Nothing here reads files or knows about the app; `build_android.py` decides what
is emitted where.

Group contract for item and decor stickers (§3.2):

  cut       one path: the union silhouette of every die-cut part, white fill + 8u round-join white stroke. Its
            alpha is the whole sticker, so a tinted draw is the hard shadow; stroked dashed it is the ghost outline.
  fills     the art below the holo foil: every part up to and including the dominant shape's fill and puffy band.
  lines     the art above the holo foil: the dominant shape's trace and detail lines, then every later part, in
            the DSL's own z-order (so a part drawn over the dominant shape keeps covering it, as in the mockup's
            holo variant, which inserts the foil right after the dominant part).
  gloss     the procedural bonbon gloss (white strokes and dots).
  dominant  clip-only: a group whose only child is a clip-path of the dominant shape (the holo clip).
  face      (separate `sk_f_<key>.xml` overlays only) the kawaii face, never inside a list sticker.

Coordinates: the 52-unit sticker box (viewBox -2 -2 52 52) maps to a 52 x 52 viewport, i.e. every DSL coordinate
is shifted by (+2, +2). Baskets: viewBox -8 -9 80 64 -> 80 x 64 viewport, shift (+8, +9).
"""
import math
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import List, Optional, Union

from svgelements import Path as SPath, Matrix, Move, Line, Close, CubicBezier, QuadraticBezier, Arc
from shapely.geometry import Polygon, MultiPolygon, LineString, Point, GeometryCollection
from shapely.ops import unary_union
from shapely.geometry.polygon import orient

import engine
from engine import C, tone, parse_tf, apply_tf, sample_pts, bbox, endpoints

ANDROID_NS = 'http://schemas.android.com/apk/res/android'
AAPT_NS = 'http://schemas.android.com/aapt'
WHITE = '#FFFFFF'
BALLPOINT = engine.BP
CUT_W = 8.0                      # the die-cut stroke: 4u of white outside the art
HOLO_STOPS = [(0, '#FFD6E8'), (.25, '#CCEBFF'), (.5, '#C9F2E3'), (.72, '#FFF3A6'), (1, '#E4D6FF')]   # §2.11 sticker holo


# ---------------------------------------------------------------- primitives
@dataclass
class Grad:
    """A linear gradient in viewport coordinates (VectorDrawable gradients are userSpaceOnUse)."""
    x0: float
    y0: float
    x1: float
    y1: float
    stops: list


@dataclass
class Prim:
    d: str
    fill: Optional[Union[str, Grad]] = None
    stroke: Optional[str] = None
    width: float = 0.0
    alpha: float = 1.0          # group opacity of the part (fillAlpha and strokeAlpha)
    fill_alpha: float = 1.0     # SVG fill-opacity
    name: Optional[str] = None  # path name (recolourable through Compose VectorConfig)
    evenodd: bool = False


@dataclass
class Grp:
    name: Optional[str] = None
    clip: Optional[str] = None
    children: list = field(default_factory=list)


# ---------------------------------------------------------------- numbers and colours
def num(v):
    """2-decimal fixed point, no trailing zeros, no '-0' (VectorDrawable path data and attributes)."""
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    if s in ('-0', '', '-'):
        return '0'
    if s.startswith('0.'):
        s = s[1:]
    elif s.startswith('-0.'):
        s = '-' + s[2:]
    return s


def norm_hex(c):
    c = c.strip()
    if not c.startswith('#'):
        raise ValueError(f'not a colour: {c}')
    h = c[1:]
    if len(h) == 3:
        h = ''.join(ch * 2 for ch in h)
    if len(h) != 6:
        raise ValueError(f'not a #RRGGBB colour: {c}')
    return '#' + h.upper()


VAR = re.compile(r'var\(--([\w-]+)\s*,\s*(#[0-9A-Fa-f]{3,6})\)')


def resolve(c):
    """DSL colour -> (literal colour or Grad marker, name). var(--x,#hex) keeps its fallback and is named 'x' so the app
    can recolour it (the gashapon capsule is tinted by its aisle)."""
    if c is None or c == 'none':
        return None, None
    m = VAR.fullmatch(c.strip())
    if m:
        return norm_hex(m.group(2)), m.group(1).replace('-', '_')
    if c.startswith('url(#sticker-holo)'):
        return 'HOLO', None
    return norm_hex(c), None


# ---------------------------------------------------------------- geometry
def to_matrix(tf):
    """engine transform string -> svgelements Matrix (same composition rules as engine.parse_tf)."""
    if not tf:
        return Matrix()
    a, b, c, d, e, f = parse_tf(tf)
    return Matrix(a, b, c, d, e, f)


def _mul(inner_acc, outer):
    # apply inner_acc first, then outer
    r = Matrix(inner_acc)
    r.post_cat(outer)
    return r


def mat_scale(m):
    """the uniform scale of a similarity transform (stroke widths scale by it)."""
    return math.sqrt(abs(m.a * m.d - m.b * m.c))


def shape_d(s):
    """SVG path data of a DSL shape tuple, in its own coordinates (including the ellipse / rect's own rotation)."""
    t = s[0]
    if t == 'path':
        return s[1], None
    if t == 'circle':
        cx, cy, r = s[1:4]
        return (f'M{cx + r} {cy}A{r} {r} 0 1 1 {cx - r} {cy}A{r} {r} 0 1 1 {cx + r} {cy}Z'), None
    if t == 'ellipse':
        cx, cy, rx, ry = s[1:5]
        rot = s[5] if len(s) > 5 and s[5] else 0
        d = f'M{cx + rx} {cy}A{rx} {ry} 0 1 1 {cx - rx} {cy}A{rx} {ry} 0 1 1 {cx + rx} {cy}Z'
        return d, (f'rotate({rot} {cx} {cy})' if rot else None)
    if t == 'rect':
        x, y, w, h = s[1:5]
        r = s[5] if len(s) > 5 else 0
        r = min(r or 0, w / 2, h / 2)
        rot = s[6] if len(s) > 6 and s[6] else 0
        if r:
            d = (f'M{x + r} {y}H{x + w - r}A{r} {r} 0 0 1 {x + w} {y + r}V{y + h - r}A{r} {r} 0 0 1 {x + w - r} {y + h}'
                 f'H{x + r}A{r} {r} 0 0 1 {x} {y + h - r}V{y + r}A{r} {r} 0 0 1 {x + r} {y}Z')
        else:
            d = f'M{x} {y}H{x + w}V{y + h}H{x}Z'
        return d, (f'rotate({rot} {x + w / 2} {y + h / 2})' if rot else None)
    raise ValueError(t)


def bake(d, m):
    """path data -> absolute path data with the matrix applied; arcs become cubic Beziers, T/S become Q/C."""
    p = SPath(d)
    if not (m.a == 1 and m.b == 0 and m.c == 0 and m.d == 1 and m.e == 0 and m.f == 0):
        p = p * m
    p.reify()
    out = []
    cur = None
    for seg in p:
        if isinstance(seg, Move):
            out.append(f'M{num(seg.end.x)} {num(seg.end.y)}')
            cur = seg.end
        elif isinstance(seg, Close):
            out.append('Z')
            cur = seg.end
        elif isinstance(seg, Line):
            out.append(f'L{num(seg.end.x)} {num(seg.end.y)}')
            cur = seg.end
        elif isinstance(seg, QuadraticBezier):
            out.append(f'Q{num(seg.control.x)} {num(seg.control.y)} {num(seg.end.x)} {num(seg.end.y)}')
            cur = seg.end
        elif isinstance(seg, CubicBezier):
            out.append(f'C{num(seg.control1.x)} {num(seg.control1.y)} {num(seg.control2.x)} {num(seg.control2.y)} {num(seg.end.x)} {num(seg.end.y)}')
            cur = seg.end
        elif isinstance(seg, Arc):
            for cb in seg.as_cubic_curves():
                out.append(f'C{num(cb.control1.x)} {num(cb.control1.y)} {num(cb.control2.x)} {num(cb.control2.y)} {num(cb.end.x)} {num(cb.end.y)}')
            cur = seg.end
        else:  # pragma: no cover
            raise ValueError(type(seg))
    return compact(''.join(out))


def compact(d):
    """drop the space before a negative number and the redundant command letters VectorDrawable does not need."""
    return re.sub(r' -', '-', d)


def shape_path(s, m):
    d, own = shape_d(s)
    if own:
        m = _mul(to_matrix(own), m)
    return bake(d, m)


def bounds_of(d):
    p = SPath(d)
    x0, y0, x1, y1 = p.bbox()
    return x0, y0, x1, y1


def has_area(d):
    """False for paths made only of open 2-point segments (an SVG default black fill would paint nothing)."""
    for sub in SPath(d).as_subpaths():
        segs = [s for s in sub if not isinstance(s, Move)]
        if len(segs) >= 2 or any(isinstance(s, (CubicBezier, QuadraticBezier, Arc, Close)) for s in segs):
            return True
    return False


# ---------------------------------------------------------------- silhouettes (shapely)
def _poly_from_d(d, step=.3):
    """polygons of an absolute path (each subpath closed), sampled every `step` units."""
    p = SPath(d)
    polys = []
    for sp in p.as_subpaths():
        sp = SPath(sp)
        try:
            L = sp.length(error=1e-3)
        except Exception:
            L = 0
        if not L:
            continue
        n = max(24, int(L / step))
        pts = [sp.point(i / n) for i in range(n + 1)]
        pts = [(q.x, q.y) for q in pts if q is not None]
        if len(pts) >= 3:
            g = Polygon(pts).buffer(0)
            if not g.is_empty:
                polys.append(g)
    return polys


def _line_from_d(d, step=.3):
    p = SPath(d)
    lines = []
    for sp in p.as_subpaths():
        sp = SPath(sp)
        try:
            L = sp.length(error=1e-3)
        except Exception:
            L = 0
        if L < 1e-6:
            q = list(sp)[0].end
            lines.append([(q.x, q.y)])
            continue
        n = max(8, int(L / step))
        pts = [sp.point(i / n) for i in range(n + 1)]
        lines.append([(q.x, q.y) for q in pts if q is not None])
    return lines


def stroke_geom(d, w):
    """the area an SVG stroke of width w with round caps and joins covers."""
    geos = []
    for pts in _line_from_d(d):
        if len(pts) == 1:
            geos.append(Point(pts[0]).buffer(w / 2, quad_segs=12))
        else:
            geos.append(LineString(pts).buffer(w / 2, cap_style=1, join_style=1, quad_segs=12))
    return unary_union(geos) if geos else Polygon()


def fill_geom(d):
    polys = _poly_from_d(d)
    return unary_union(polys) if polys else Polygon()


def geom_d(g, tol=.03):
    """shapely (Multi)Polygon -> path data (exteriors and holes, use with evenOdd)."""
    if g.is_empty:
        return ''
    g = g.simplify(tol, preserve_topology=True)
    polys = [g] if isinstance(g, Polygon) else [x for x in getattr(g, 'geoms', []) if isinstance(x, Polygon)]
    out = []
    for poly in polys:
        poly = orient(poly, 1.0)
        for ring in [poly.exterior, *poly.interiors]:
            pts = list(ring.coords)[:-1]
            if len(pts) < 3:
                continue
            out.append('M' + 'L'.join(f'{num(x)} {num(y)}' for x, y in pts) + 'Z')
    return compact(''.join(out))


# ---------------------------------------------------------------- the part walker (mirrors engine.Art.part, stage 6)
def _stroke(d, col, w, alpha=1.0, name=None):
    c, n = resolve(col)
    return Prim(d, stroke=c, width=w, alpha=alpha, name=name or n)


def _fill(d, col, alpha=1.0, name=None):
    c, n = resolve(col)
    return Prim(d, fill=c, alpha=alpha, name=name or n)


def part_prims(p, base):
    """(body, trace) primitives of one part. body = what sits below the holo for the dominant part (fill, clipped
    puffy band and texture); trace = its outline and detail lines. Lines and tubes are all body."""
    fam = p.fam
    tr = C[fam][2] if fam in C else fam
    alpha = float(p.op) if p.op else 1.0
    m = _mul(to_matrix(p.tf), base) if p.tf else base
    sc = mat_scale(m)
    if p.kind == 'line':
        return [_stroke(bake(p.s[1], m), tone(fam, p.t), p.w * sc, alpha)], []
    if p.kind == 'tube':
        col = tone(fam, p.t)
        body = [_stroke(bake(p.s[1], m), tr, p.w * sc, alpha),
                _stroke(bake(p.s[1], m), col, max(p.w - 4, .8) * sc, alpha)]
        body += [_stroke(bake(dd, m), tone(fam, p.det_t) if p.det_t else tr, p.det_w * sc, alpha) for dd in p.det]
        return body, []
    # fill part
    sd = shape_path(p.s, m)
    body = [_fill(sd, tone(fam, p.t), alpha)]
    if p.band is not None or p.tex:
        tex = []
        if p.tex:
            tc = tone(fam, p.tex_t) if p.tex_t else (C[fam][1] if p.t in ('p', 'w', 'lp') else C[fam][0])
            tex = [_stroke(bake(dd, m), tc, p.tex_w * sc, alpha) for dd in p.tex]
        band = []
        if p.band is not None:
            bt = p.band_t or ('m' if p.t == 'p' else 'p' if p.t in ('w',) else 'deep' if p.t == 'm' else 'm')
            bc = tone(fam, bt)
            if p.tf and p.sband and not isinstance(p.band, str):
                # screen-space band: computed from the transformed outline, in the parent's coordinates
                M = parse_tf(p.tf)
                pts = [apply_tf(M, x, y) for x, y in sample_pts(p.s)]
                x0, x1 = min(x for x, _ in pts), max(x for x, _ in pts)
                y0, y1 = min(y for _, y in pts), max(y for _, y in pts)
                y = y1 - (y1 - y0) * p.band
                band = [_fill(bake(_rect_d(x0 - 2, y, x1 - x0 + 4, y1 - y + 2), base), bc, alpha)]
            elif isinstance(p.band, str):
                band = [_fill(bake(p.band, m), bc, alpha)]
            else:
                x0, y0, x1, y1 = bbox(p.s)
                y = y1 - (y1 - y0) * p.band
                band = [_fill(bake(_rect_d(x0 - 2, y, x1 - x0 + 4, y1 - y + 2), m), bc, alpha)]
        body.append(Grp(clip=sd, children=tex + band))
    trace = []
    if p.ol:
        trace.append(_stroke(sd, tr, p.ol * sc, alpha))
    if p.det:
        dc = tone(fam, p.det_t) if p.det_t else tr
        trace += [_stroke(bake(dd, m), dc, p.det_w * sc, alpha) for dd in p.det]
    return body, trace


def _rect_d(x, y, w, h):
    return f'M{x} {y}H{x + w}V{y + h}H{x}Z'


def merge_fill_trace(body, trace):
    """a fill with its outline and nothing between them becomes one path (VectorDrawable fills, then strokes)."""
    if len(body) == 1 and isinstance(body[0], Prim) and trace and isinstance(trace[0], Prim):
        f, t = body[0], trace[0]
        if (f.fill is not None and f.stroke is None and t.fill is None and t.d == f.d and f.alpha == 1 and t.alpha == 1
                and f.name is None and t.name is None):
            return [Prim(f.d, fill=f.fill, stroke=t.stroke, width=t.width)] + trace[1:]
    return body + trace


def gloss_prims(gloss, base):
    out = []
    for g in gloss:
        tf = g[-1] if isinstance(g[-1], str) and len(g) in (4, 5) else None
        m = _mul(to_matrix(tf), base) if tf else base
        sc = mat_scale(m)
        if g[0] == 'g':
            out.append(Prim(bake(g[1], m), stroke=WHITE, width=g[2] * sc))
        else:
            out.append(Prim(shape_path(('circle', g[1], g[2], g[3]), m), fill=WHITE))
    return out


def part_geom(p, base):
    """the die-cut contribution of a part (shapely, viewport coordinates), or None."""
    if not p.cut:
        return None
    m = _mul(to_matrix(p.tf), base) if p.tf else base
    if p.kind == 'fill':
        return fill_geom(shape_path(p.s, m))
    return stroke_geom(bake(p.s[1], m), p.w * mat_scale(m))


def silhouette(parts, base, cutx=()):
    geos = [g for g in (part_geom(p, base) for p in parts) if g is not None and not g.is_empty]
    geos += [fill_geom(shape_path(s, base)) for s in cutx]
    return unary_union(geos) if geos else Polygon()


def cut_prim(parts, base, cutx=()):
    sil = silhouette(parts, base, cutx)
    return Prim(geom_d(sil), fill=WHITE, stroke=WHITE, width=CUT_W * mat_scale(base), evenodd=True), sil


def dominant_index(parts, base):
    """the dominant shape: the die-cut fill part with the largest area (the holo clip and the first family)."""
    best, area = None, -1.0
    for i, p in enumerate(parts):
        if p.kind != 'fill' or not p.cut:
            continue
        g = part_geom(p, base)
        if g is not None and g.area > area:
            best, area = i, g.area
    return best


def families(parts, base):
    """the two palette families an icon is drawn in, by filled area (dominant first)."""
    score = {}
    for p in parts:
        if p.fam not in C:
            continue
        m = _mul(to_matrix(p.tf), base) if p.tf else base
        if p.kind == 'fill':
            g = fill_geom(shape_path(p.s, m))
        else:
            g = stroke_geom(bake(p.s[1], m), p.w * mat_scale(m))
        score[p.fam] = score.get(p.fam, 0) + g.area
    return [f for f, _ in sorted(score.items(), key=lambda kv: -kv[1])[:2]]


def icon_layers(icon, base, dominant=None, include_cut=True):
    """the §3.2 groups of a sticker: [Grp(cut), Grp(fills), Grp(lines), Grp(gloss), Grp(dominant)]."""
    parts = icon.parts
    d = dominant_index(parts, base) if dominant is None else dominant
    fills, lines = [], []
    for i, p in enumerate(parts):
        body, trace = part_prims(p, base)
        if d is None or i < d:
            fills += merge_fill_trace(body, trace)
        elif i == d:
            fills += body
            lines += trace
        else:
            lines += merge_fill_trace(body, trace)
    groups = []
    sil = None
    if include_cut:
        cp, sil = cut_prim(parts, base, getattr(icon, 'cutx', ()))
        groups.append(Grp('cut', children=[cp]))
    groups += [Grp('fills', children=fills), Grp('lines', children=lines), Grp('gloss', children=gloss_prims(icon.gloss, base))]
    if d is not None:
        groups.append(Grp('dominant', clip=shape_path(parts[d].s, _mul(to_matrix(parts[d].tf), base) if parts[d].tf else base)))
    return groups, d, sil


def art_prims(parts, base):
    """plain z-ordered art of a list of parts (baskets, faces)."""
    out = []
    for p in parts:
        body, trace = part_prims(p, base)
        out += merge_fill_trace(body, trace)
    return out


def shadow_prims(sil, base_offset, colour):
    """a baked hard shadow (launcher only; the app draws every other shadow at render time from `cut`)."""
    g = sil
    from shapely import affinity
    g = affinity.translate(g, base_offset[0], base_offset[1])
    return Prim(geom_d(g), fill=colour, stroke=colour, width=CUT_W, evenodd=True)


# ---------------------------------------------------------------- mono silhouettes (themed launcher)
def mono_geom(icon, base, lw=1.3, cw=3.0):
    """engine.Art.mono_body as one alpha shape: the ballpoint silhouette (cut stroked 3u) minus the white interior
    lines, so a system tint keeps the drawing (Android themed icons use alpha only)."""
    sc = mat_scale(base)
    sil = silhouette(icon.parts, base, icon.cutx).buffer(cw / 2 * sc, quad_segs=12)
    holes = []
    for p in icon.parts:
        m = _mul(to_matrix(p.tf), base) if p.tf else base
        if p.kind == 'fill':
            holes.append(stroke_geom(shape_path(p.s, m), lw * sc))
            holes += [stroke_geom(bake(dd, m), lw * sc) for dd in p.det]
        elif p.kind == 'tube':
            holes.append(stroke_geom(bake(p.s[1], m), max(p.w - 3.4, .9) * sc))
    return sil.difference(unary_union(holes)) if holes else sil


# ---------------------------------------------------------------- plain SVG snippets
_PRESENT = ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-opacity', 'fill-rule')


def flatten_svg(snippet, base=None, current='#25307C', names=None):
    """flatten an SVG fragment (no <use>, no clip) into Prims. currentColor -> `current`; `names` maps a literal colour
    to a path name (pictograms name their ink and paper)."""
    base = base or Matrix()
    root = ET.fromstring(f'<g xmlns="http://www.w3.org/2000/svg">{snippet}</g>')
    out = []

    def walk(el, ctx, m, opacity):
        tag = el.tag.split('}')[-1]
        ctx = dict(ctx)
        for k in _PRESENT:
            if k in el.attrib:
                ctx[k] = el.attrib[k]
        if 'transform' in el.attrib:
            m = _mul(to_matrix(el.attrib['transform']), m)
        if 'opacity' in el.attrib:
            opacity *= float(el.attrib['opacity'])
        if tag in ('g', 'svg'):
            for ch in el:
                walk(ch, ctx, m, opacity)
            return
        a = el.attrib
        f = lambda k, dflt=0.0: float(a.get(k, dflt))
        if tag == 'path':
            s = ('path', a['d'])
        elif tag == 'circle':
            s = ('circle', f('cx'), f('cy'), f('r'))
        elif tag == 'ellipse':
            s = ('ellipse', f('cx'), f('cy'), f('rx'), f('ry'))
        elif tag == 'rect':
            rx = f('rx', a.get('ry', 0))
            s = ('rect', f('x'), f('y'), f('width'), f('height'), rx)
        elif tag == 'line':
            s = ('path', f"M{a.get('x1', 0)} {a.get('y1', 0)}L{a.get('x2', 0)} {a.get('y2', 0)}")
        else:
            raise ValueError(f'unsupported SVG element {tag}')
        d = shape_path(s, m)
        fill = ctx.get('fill', '#000')
        stroke = ctx.get('stroke', 'none')
        fill = current if fill == 'currentColor' else fill
        stroke = current if stroke == 'currentColor' else stroke
        prim = Prim(d)
        if fill != 'none' and has_area(d):
            prim.fill = norm_hex(fill)
            prim.fill_alpha = float(ctx.get('fill-opacity', 1))
            prim.evenodd = ctx.get('fill-rule') == 'evenodd'
        if stroke != 'none':
            prim.stroke = norm_hex(stroke)
            prim.width = float(ctx.get('stroke-width', 1)) * mat_scale(m)
        prim.alpha = opacity
        if names:
            prim.name = names.get(prim.fill or prim.stroke)
        if prim.fill is not None or prim.stroke is not None:
            out.append(prim)

    walk(root, {}, base, 1.0)
    return out


# ---------------------------------------------------------------- writer
def _attr(k, v):
    return f'android:{k}="{v}"'


def _grad_xml(g, ind):
    stops = ''.join(f'{ind}        <item android:offset="{num(o)}" android:color="#FF{c[1:]}"/>\n' for o, c in g.stops)
    return (f'{ind}    <aapt:attr name="android:fillColor">\n'
            f'{ind}      <gradient android:type="linear" android:startX="{num(g.x0)}" android:startY="{num(g.y0)}" '
            f'android:endX="{num(g.x1)}" android:endY="{num(g.y1)}" android:tileMode="clamp">\n{stops}'
            f'{ind}      </gradient>\n{ind}    </aapt:attr>\n')


def _prim_xml(p, ind):
    attrs = []
    if p.name:
        attrs.append(_attr('name', p.name))
    attrs.append(_attr('pathData', p.d))
    grad = None
    if isinstance(p.fill, Grad):
        grad = p.fill
    elif p.fill == 'HOLO':
        x0, y0, x1, y1 = bounds_of(p.d)
        grad = Grad(x0, y0, x1, y1, HOLO_STOPS)
    elif p.fill is not None:
        attrs.append(_attr('fillColor', p.fill))
    fa = p.alpha * p.fill_alpha
    if (p.fill is not None) and fa != 1:
        attrs.append(_attr('fillAlpha', num(fa) if fa > 0 else '0'))
    if p.evenodd and p.fill is not None:
        attrs.append(_attr('fillType', 'evenOdd'))
    if p.stroke is not None:
        attrs += [_attr('strokeColor', p.stroke), _attr('strokeWidth', num(p.width)),
                  _attr('strokeLineCap', 'round'), _attr('strokeLineJoin', 'round')]
        if p.alpha != 1:
            attrs.append(_attr('strokeAlpha', num(p.alpha)))
    head = f'{ind}<path ' + f'\n{ind}    '.join(attrs)
    if grad:
        return head + '>\n' + _grad_xml(grad, ind) + f'{ind}</path>\n'
    return head + '/>\n'


def _node_xml(n, ind):
    if isinstance(n, Prim):
        return _prim_xml(n, ind)
    head = f'{ind}<group' + (f' {_attr("name", n.name)}' if n.name else '') + '>\n'
    body = ''
    if n.clip:
        body += f'{ind}  <clip-path {_attr("pathData", n.clip)}/>\n'
    body += ''.join(_node_xml(c, ind + '  ') for c in n.children)
    return head + body + f'{ind}</group>\n'


def uses_gradient(nodes):
    for n in nodes:
        if isinstance(n, Prim) and (n.fill == 'HOLO' or isinstance(n.fill, Grad)):
            return True
        if isinstance(n, Grp) and uses_gradient(n.children):
            return True
    return False


def vector_xml(nodes, vw, vh, w_dp=None, h_dp=None, comment=None):
    ns = f' xmlns:android="{ANDROID_NS}"' + (f'\n    xmlns:aapt="{AAPT_NS}"' if uses_gradient(nodes) else '')
    head = '<?xml version="1.0" encoding="utf-8"?>\n'
    if comment:
        head += f'<!-- {comment} -->\n'
    head += (f'<vector{ns}\n    android:width="{num(w_dp or vw)}dp"\n    android:height="{num(h_dp or vh)}dp"\n'
             f'    android:viewportWidth="{num(vw)}"\n    android:viewportHeight="{num(vh)}">\n')
    return head + ''.join(_node_xml(n, '  ') for n in nodes) + '</vector>\n'


def walk_prims(nodes):
    for n in nodes:
        if isinstance(n, Prim):
            yield n
        else:
            yield from walk_prims(n.children)


def translate(x, y):
    return Matrix(1, 0, 0, 1, x, y)


def scale_then_translate(s, x, y):
    return Matrix(s, 0, 0, s, x, y)
