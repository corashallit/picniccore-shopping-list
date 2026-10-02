"""Build-time validators (docs/DESIGN_STICKER.md §18.3 step 3, checklist items 29-32 and 101).

run(report) -> (errors, warnings, measurements). Errors fail `build_android.py` and `:app:checkStickers`.

  palette    every fill and stroke of an item sticker is one of the 40 family tokens or white (lemon pastel is a
             token); decor, baskets, pictograms, glyphs, tiles and the launcher may also use the few named extras below.
  gloss      one main gloss (>= 2.4u wide, >= 10u long) per sticker, covering 25-35 % of its shape's contour (a
             shorter run is allowed only where a shape drawn above cuts it); sub-glosses >= 4u; every gloss stroke
             inside the art (no stray fragments); no gloss inside a critter's head.
  geometry   base on y 42 and inside the live area 6-42 (the 2u trace may overhang by 1u); smallest die-cut feature
             >= 2u (printed details may be finer: seeds and dots are 1.6u by design); a union cut is present; no
             text anywhere in the DSL's SVG.
  baskets    all 17 kinds; back, front and closed lid present; decorated kinds carry their decoration slice; the
             hanko point lies on the closed lid; the rim sits inside the art.
"""
import math

from shapely.geometry import Point
from shapely.ops import unary_union

import engine
from engine import C, deep
import emit_android as E

TOKENS = {E.WHITE}
for _f in C:
    TOKENS |= {C[_f][0], C[_f][1], C[_f][2], deep(_f)}
assert len(TOKENS) == 41, len(TOKENS)       # 40 family tokens + white

# Named extras outside the item palette (each is a §2 token of its own surface).
EXTRAS = {
    'decor': {'#C9FFDE': 'glowStar (§2.4)', '#7FD6A6': 'glow star trace', 'HOLO': 'holo foil (§2.11, kira only)'},
    'basket': {'#E6F5FF': 'ice cube (ice bucket)', '#F3F6FF': 'jar glass (candy jar)'},
    'pictogram': {engine.BP: 'ballpoint ink'},
    'glyph': {engine.BP: 'ballpoint'},
    'launcher': {'#E6F6EE': 'binder', '#CDEEDD': 'binderStripe', engine.SHADOW: 'stickerShadow', engine.BP: 'ballpoint (mono)',
                 'HOLO': 'holo star'},
    'tile': {},
}

# critter heads (centre x, centre y, radius): no gloss inside (§3.1, §20.3 item 30)
HEADS = {'cat': (15.8, 31.4, 9.6), 'bee': (14.6, 28.2, 7), 'ladybug': (24, 13.4, 6.4), 'ladybugfly': (24, 15.4, 6), 'ant': (12.4, 32, 3.6)}
BASKET_KINDS = {'wicker_picnic', 'bread_basket', 'fruit_crate', 'berry_punnet', 'wire_basket', 'egg_basket', 'milk_crate',
                'cooler', 'ice_bucket', 'bento_box', 'jar_shelf', 'canvas_tote', 'flower_bucket', 'bottle_carrier',
                'candy_jar', 'vanity_case', 'mixing_bowl'}
DECORATED = {'ice_bucket', 'flower_bucket', 'bottle_carrier', 'candy_jar'}
OFF = 2.0                                     # sticker viewport offset (viewBox -2 -2)


def _colours(nodes):
    for p in E.walk_prims(nodes):
        if p.fill is not None:
            yield 'HOLO' if (p.fill == 'HOLO' or isinstance(p.fill, E.Grad)) else p.fill
        if p.stroke is not None:
            yield p.stroke


def check_palette(label, nodes, kind, errors):
    allowed = TOKENS | set(EXTRAS.get(kind, {}))
    bad = sorted({c for c in _colours(nodes) if c not in allowed})
    if bad:
        errors.append(f'palette: {label} uses {", ".join(bad)} (not a family token, white or a named {kind} extra)')


def _gloss_geoms(icon):
    out = []
    for g in icon.gloss:
        if g[0] == 'g':
            d = E.bake(g[1], E.translate(OFF, OFF))
            out.append(('stroke', E.stroke_geom(d, g[2]), g))
        else:
            out.append(('dot', Point(g[1] + OFF, g[2] + OFF).buffer(g[3]), g))
    return out


def _coverage(info, icon):
    """main gloss length / inset contour length of the part it sits on."""
    m = info.get('main')
    if not m or m['part'] is None or m['part'] < 0:
        return None
    p = icon.parts[m['part']]
    base = E.translate(OFF, OFF)
    g = E.part_geom(p, base)
    if g is None or g.is_empty:
        return None
    inset = g.buffer(-(3.0 + 1.6), quad_segs=12).buffer(1.6, quad_segs=12)    # gloss.arc's main inset (1 + 2.6/2 + .7)
    if inset.is_empty:
        inset = g.buffer(-(1.9 + 1.6), quad_segs=12).buffer(1.6, quad_segs=12)
    if inset.is_empty:
        return None
    ring = max(getattr(inset, 'geoms', [inset]), key=lambda x: x.area).exterior
    return m['len'] / ring.length


def check_gloss(stickers, gloss_report, errors, warnings, measures, critters=()):
    from gloss import lint as lint_gloss
    errors += lint_gloss(gloss_report)
    infos = {r['key']: r for r in gloss_report}
    covs = []
    for icon, key, groups, dom, sil in stickers:
        info = infos.get(icon.key)
        geos = _gloss_geoms(icon)
        # every gloss mark inside the art: the union of the filled shapes (not the white border)
        base = E.translate(OFF, OFF)
        art = unary_union([g for g in (E.fill_geom(E.shape_path(p.s, E._mul(E.to_matrix(p.tf), base) if p.tf else base))
                                       if p.kind == 'fill' else E.stroke_geom(E.bake(p.s[1], E._mul(E.to_matrix(p.tf), base) if p.tf else base), p.w)
                                       for p in icon.parts) if not g.is_empty])
        for kind, geo, g in geos:
            outside = geo.difference(art.buffer(.35)).area
            if outside > .05 * geo.area + .05:
                errors.append(f'gloss: {key} has a stray {kind} outside the art ({outside:.2f} u² of {geo.area:.2f})')
        subs = [g for g in icon.gloss if g[0] == 'g' and g[2] < 2.4]
        for g in subs:
            ln = E.stroke_geom(E.bake(g[1], base), .01).length / 2
            if ln < 4:
                errors.append(f'gloss: {key} sub-gloss shorter than 4u ({ln:.1f})')
        if info is not None:
            cov = _coverage(info, icon)
            if cov is not None:
                covs.append((cov, key))
    for k, (cx, cy, r) in HEADS.items():
        for icon, key, *_ in critters:
            if icon.key != k:
                continue
            for kind, geo, g in _gloss_geoms(icon):
                if geo.intersects(Point(cx + OFF, cy + OFF).buffer(r)):
                    errors.append(f'gloss: white gloss inside the head of {k}')
    if covs:
        covs.sort()
        lo = [f'{k} {c:.0%}' for c, k in covs if c < .25]
        hi = [f'{k} {c:.0%}' for c, k in covs if c > .35]
        measures.append(f'gloss coverage of the main contour: {covs[0][0]:.0%} ({covs[0][1]}) .. {covs[-1][0]:.0%} ({covs[-1][1]}), '
                        f'median {covs[len(covs) // 2][0]:.0%}')
        if lo:
            warnings.append(f'gloss coverage below 25 % (a shape above cuts the run): {", ".join(lo)}')
        if hi:
            errors.append(f'gloss coverage above 35 %: {", ".join(hi)}')


def check_geometry(stickers, errors, warnings, measures):
    base_ys, boxes = [], []
    for icon, key, groups, dom, sil in stickers:
        if sil is None or sil.is_empty:
            errors.append(f'geometry: {key} has no union cut')
            continue
        cut = next((g for g in groups if g.name == 'cut'), None)
        if cut is None or not cut.children or not cut.children[0].d:
            errors.append(f'geometry: {key} has no cut group')
        x0, y0, x1, y1 = (v - OFF for v in sil.bounds)
        base_ys.append((y1, key))
        boxes.append((x0, y0, x1, y1, key))
        if not (41.0 <= y1 <= 42.8):
            errors.append(f'geometry: {key} base at y {y1:.2f} (must sit on y 42)')
        if x0 < 5 or y0 < 3 or x1 > 43:
            errors.append(f'geometry: {key} art {x0:.1f},{y0:.1f} .. {x1:.1f},{y1:.1f} leaves the live area 6-42 by more than the trace')
        elif x0 < 6 - .01 or y0 < 6 - .01 or x1 > 42 + .01:
            warnings.append(f'geometry: {key} art {x0:.1f},{y0:.1f} .. {x1:.1f},{y1:.1f} overhangs the live area 6-42 (within the 1u trace allowance)')
        base = E.translate(OFF, OFF)
        for i, p in enumerate(icon.parts):
            if not p.cut:
                continue
            g = E.part_geom(p, base)
            if g is None or g.is_empty:
                continue
            if p.kind == 'fill' and g.buffer(-.99).is_empty:
                errors.append(f'geometry: {key} part {i} ({p.s[0]}) is thinner than 2u')
            if p.kind in ('tube', 'line') and p.w < 1.2:
                errors.append(f'geometry: {key} part {i} line {p.w}u is thinner than the 1.2u minimum')
    if base_ys:
        base_ys.sort()
        measures.append(f'base y: {base_ys[0][0]:.2f} ({base_ys[0][1]}) .. {base_ys[-1][0]:.2f} ({base_ys[-1][1]})')
        measures.append('art box: x {:.1f}..{:.1f}, y {:.1f}..{:.1f}'.format(min(b[0] for b in boxes), max(b[2] for b in boxes),
                                                                         min(b[1] for b in boxes), max(b[3] for b in boxes)))


def check_no_text(icons, errors):
    for icon in icons:
        d, body = icon.render()
        if '<text' in d + body or '<image' in d + body:
            errors.append(f'geometry: {icon.key} draws text or an image')


def check_baskets(baskets, errors):
    seen = set()
    for b, kind, layers, lsil in baskets:
        seen.add(kind)
        for need in ('back', 'front', 'lid_closed'):
            if need not in layers:
                errors.append(f'basket: {kind} has no {need} layer')
        if kind in DECORATED and 'deco' not in layers:
            errors.append(f'basket: {kind} is decorated but has no deco slice')
        if kind not in DECORATED and 'deco' in layers:
            errors.append(f'basket: {kind} is not a decorated kind but has a deco slice')
        if 'lid_closed' in lsil and not lsil['lid_closed'].buffer(4).contains(Point(b.hk[0] + 8, b.hk[1] + 9)):
            errors.append(f'basket: {kind} hanko point {b.hk} is off the closed lid')
        x0, y0, x1, y1 = lsil['back'].bounds
        if not (y0 - 9 <= b.rim <= y1 - 9):
            errors.append(f'basket: {kind} rim {b.rim} outside its art')
        if b.show_open and 'lid_open' not in layers:
            errors.append(f'basket: {kind} stands its lid open but has no lid_open layer')
        for name, nodes in layers.items():
            check_palette(f'sk_b_{kind}_{name}', nodes, 'basket', errors)
    for k in sorted(BASKET_KINDS - seen):
        errors.append(f'basket: no art for {k}')


def run(report):
    errors, warnings, measures = [], [], []
    from icons import GLOSS_REPORT, ICONS
    from deco import DECO
    stickers = report['stickers']
    for icon, key, groups, dom, sil in stickers:
        check_palette(f'sk_i_{key}', groups, 'item', errors)
        if dom is None:
            errors.append(f'groups: {key} has no dominant shape')
    for icon, key, groups, dom, sil in report['decor']:
        check_palette(f'sk_d_{key}', groups, 'decor', errors)
    check_gloss(stickers, [r for r in GLOSS_REPORT if r['key'] in {s[0].key for s in stickers}], errors, warnings, measures,
                critters=report['decor'])
    check_geometry(stickers, errors, warnings, measures)
    check_no_text(ICONS + DECO, errors)
    check_baskets(report['baskets'], errors)
    out = report['out']
    for path, xml in out.items():
        if '<text' in xml:
            errors.append(f'{path}: contains text')
    return errors, warnings, measures
