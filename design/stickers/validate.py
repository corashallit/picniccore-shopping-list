"""Build-time validators (docs/DESIGN_STICKER.md §18.3 step 3; checklist items 29-32 and 101).

run(report) -> (errors, warnings, measurements). Errors fail `build_android.py` and `./gradlew :app:checkStickers`.

  palette   every fill and stroke in an item sticker (sk_i_, sk_f_) is one of the 40 family tokens or white (lemon
            pastel is a token). Decor, baskets, pictograms, glyphs, tiles and the launcher may also use the named
            extras in EXTRAS, each a token of its own surface.
  gloss     one main gloss per sticker, >= 2.4u wide and >= 10u long (gloss.lint); it covers 25-35 % of its shape's
            contour, measured as the angle it spans around the bbox-normalised shape, as gloss.autogloss draws it
            (90 deg = 25 %, the widened windows up to 128 deg = 35.5 %), and ends in its dot. A run that fills the
            90 deg window measures 24.1-24.9 % (the contour is sampled every 0.2u), so the floor is COVERAGE_MIN (24 %
            measured); a shorter run or a missing dot is an error. Sub-glosses >= 4u. Every gloss mark lies on the art
            (no stray fragments on the white border or across a gap) and never crosses the visible colour-trace of a
            die-cut shape (a white notch in an outline). No gloss inside a critter's head or an item's face-like head
            (HEADS).
  printed   every printed part (cut=False fills, lines and tubes, with their detail lines) stays inside the die-cut art:
            paint in a non-trace colour inside the die-cut shapes' outlines (each shape less half its trace: -1u for the
            2u trace), paint in a trace colour inside the outlines' outer edge (+1u), unless the part's own trace
            repaints it.
  hairline  every detail line, texture line, plain line and outline is at least HAIRLINE (1.2u) wide.
  families  at most two families per sticker plus white and lemon-pastel details (§3.1): a third family (lemon pastel
            counts as lemon) is allowed only as a small accent (under FAMILY_ACCENT of the coloured area: stems,
            leaves, seeds, a cap), on the composite generics in COMPOSITE (a bowl of mixed fruit, a breakfast plate),
            or on the stickers in MULTICOLOURED, which are many-coloured by nature (sprinkles).
  geometry  the union cut is present; the base sits on y 42 (41.4-42.6); the art stays in the live area 6-42 (the 2u
            trace may overhang by 1u, so 5-43); every die-cut fill part fits a 2u disc; die-cut rectangles have
            corners r >= 2u; no die-cut line is thinner than 1.2u; no <text> or <image> anywhere.
  baskets   all 17 kinds; back, front, open and closed lid present; exactly the decorated kinds carry a decoration
            slice; the hanko point lies on the closed lid; the rim sits inside the art; every layer's die-cut (4u
            border included) stays inside the 80 x 64 viewport; palette as above.

GRANDFATHERED lists the approved r9 stickers (§18.2: "the 121 approved stickers ship as they are") that miss a
geometry or gloss rule as drawn. They are reported, not failed; any other sticker, including every new key, must pass.
An entry that starts passing is reported so the list only shrinks. The M2 contact-sheet review (§20 item 34) decides
whether to redraw them.
"""
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
    'washi': {'#FFFAD6': 'lemon-dots tape cream (§2.12)', '#EAF6FF': 'raindrop tape paper (egg 16)'},
}

# critter heads (centre x, centre y, radius) in DSL units: no gloss inside (§3.1, §20.3 item 30)
HEADS = {'cat': (15.8, 31.4, 9.6), 'bee': (14.6, 28.2, 7), 'ladybug': (24, 13.4, 6.4), 'ladybugfly': (24, 15.4, 6), 'ant': (12.4, 32, 3.6),
         'antcrumb': (12.4, 32, 3.6), 'dustbunny': (22.4, 29.6, 6.4),   # the dust bunny's face; its gloss is on the fluff
         # item stickers with a head (no face on a list sticker, but a white stroke in the head still reads as one)
         'candy': (32.6, 26.4, 4.6)}
BASKET_KINDS = {'wicker_picnic', 'bread_basket', 'fruit_crate', 'berry_punnet', 'wire_basket', 'egg_basket', 'milk_crate',
                'cooler', 'ice_bucket', 'bento_box', 'jar_shelf', 'canvas_tote', 'flower_bucket', 'bottle_carrier',
                'candy_jar', 'vanity_case', 'mixing_bowl'}
DECORATED = {'ice_bucket', 'flower_bucket', 'bottle_carrier', 'candy_jar'}
OFF = 2.0                                     # sticker viewport offset (viewBox -2 -2 52 52)
BASE_Y = (41.4, 42.6)
LIVE = (5.0, 43.0)
COVERAGE_MIN = .24          # 25 % of the contour as measured (see the module docstring)
CROSS_MAX = .6              # u² of gloss over a visible die-cut outline before it reads as a notch
PRINTED_MAX = .3            # u² of printed paint outside its allowed zone (after dropping slivers under 0.3u wide)
HAIRLINE = 1.2             # thinnest detail, texture or printed outline (§3.1: 1.5u detail; under 1.2u it vanishes at 30 dp)
FAMILY_ACCENT = .06         # a third family under 6 % of the coloured area is an accent (stems, leaves)
# composite generics: one sticker standing for a whole aisle of mixed things (§3.1 exception)
COMPOSITE = {'fruit_generic', 'vegetables_generic', 'breakfast_generic', 'deli_generic', 'household_generic'}
# things that are many-coloured by nature and may show more than two families (§3.1 exception)
MULTICOLOURED = {'sprinkles'}

# approved r9 stickers that miss a rule as drawn (catalog keys); see the module docstring
GRANDFATHERED = {
    'base': {'bacon', 'bandages', 'carrot', 'chicken_drumstick', 'croissant', 'diapers', 'garlic', 'granola_bar', 'grapes',
        'onion', 'pasta', 'pet_treats', 'sausages', 'tomato', 'toothpaste', 'tortillas'},
    'live_area': {'_capsule', 'baby_food', 'baguette', 'carrot', 'cherries', 'chicken_breast', 'cucumber', 'deli_meat',
        'grapes', 'honey', 'hot_sauce', 'ketchup', 'mayonnaise', 'mushrooms', 'mustard', 'olive_oil', 'onion',
        'pasta', 'peach', 'pear', 'popcorn', 'potato_chips', 'rotisserie_chicken', 'sausages', 'soy_sauce', 'sponge',
        'tomato', 'toothpaste', 'watermelon'},
    'thin': {'frozen_pizza'},
    'corner': {'bottled_water', 'cream', 'french_fries', 'frozen_vegetables', 'garlic', 'laundry_detergent', 'olive_oil',
        'orange_juice', 'potato_chips'},
    'stray_gloss': {'bacon', 'celery', 'grapes'},
    'coverage': {'baby_wipes', 'bandages', 'bottled_water', 'butter_tart', 'diapers', 'honey'},
    'dot': {'baguette', 'diapers', 'lettuce'},
    'cross': set(),
    'printed': {'bakery_generic', 'canned_tomatoes', 'canned_tuna', 'cheddar', 'donut', 'maple_syrup', 'oatmeal', 'pasta',
        'soup_can', 'sunscreen', 'sweet_potato', 'tea', 'tissues', 'yogurt'},
    'families': set(),
    'hairline': {'baby_food', 'cat_food', 'cat_litter', 'cherries', 'granola', 'grapes', 'honey', 'hot_sauce', 'ice_cream', 'jam',
        'ketchup', 'laundry_detergent', 'lemonade', 'maple_syrup', 'mayonnaise', 'oatmeal', 'orange', 'pasta_sauce', 'peach',
        'peanut_butter', 'pear', 'potato_chips', 'salt', 'sparkling_water', 'tea', 'toothpaste', 'vitamins', 'yogurt'},
}


class Lint:
    def __init__(self):
        self.errors, self.warnings, self.measures = [], [], []
        self.hit = {k: set() for k in GRANDFATHERED}
        self.held = {k: [] for k in GRANDFATHERED}

    def fail(self, rule, key, msg):
        if rule in GRANDFATHERED and key in GRANDFATHERED[rule]:
            self.hit[rule].add(key)
            self.held[rule].append(msg)
        else:
            self.errors.append(msg)

    def finish(self):
        for rule, keys in GRANDFATHERED.items():
            if self.held[rule]:
                self.measures.append(f'grandfathered {rule}: ' + '; '.join(self.held[rule]))
            for k in sorted(keys - self.hit[rule]):
                self.warnings.append(f'grandfathered {rule} exemption for {k} is no longer needed: remove it from validate.py')


def _colours(nodes):
    for p in E.walk_prims(nodes):
        if p.fill is not None:
            yield 'HOLO' if (p.fill == 'HOLO' or isinstance(p.fill, E.Grad)) else p.fill
        if p.stroke is not None:
            yield p.stroke


def check_palette(lint, label, nodes, kind):
    allowed = TOKENS | set(EXTRAS.get(kind, {}))
    bad = sorted({c for c in _colours(nodes) if c not in allowed})
    if bad:
        lint.errors.append(f'palette: {label} uses {", ".join(bad)} (not a family token, white or a named {kind} extra)')


def _m(p, base):
    return E._mul(E.to_matrix(p.tf), base) if p.tf else base


def _art_geom(icon, base):
    """the painted art (fills and strokes, before the white border)."""
    geos = []
    for p in icon.parts:
        m = _m(p, base)
        if p.kind == 'fill':
            g = E.fill_geom(E.shape_path(p.s, m))
        else:
            g = E.stroke_geom(E.bake(p.s[1], m), p.w)
        if not g.is_empty:
            geos.append(g)
    return unary_union(geos)


def _length(d):
    return sum(E.SPath(sp).length() for sp in E.SPath(d).as_subpaths())


def check_gloss(lint, stickers, gloss_report, critters):
    from gloss import lint as lint_gloss
    lint.errors += lint_gloss(gloss_report)
    infos = {r['key']: r for r in gloss_report}
    base = E.translate(OFF, OFF)
    spans = []
    for icon, key, groups, dom, sil in stickers:
        art = _art_geom(icon, base).buffer(.35)
        for g in icon.gloss:
            if g[0] == 'g':
                d = E.bake(g[1], base)
                geo = E.stroke_geom(d, g[2])
                if g[2] < 2.4 and _length(d) < 4:
                    lint.errors.append(f'gloss: {key} sub-gloss is {_length(d):.1f}u long (>= 4u)')
            else:
                geo = Point(g[1] + OFF, g[2] + OFF).buffer(g[3])
            outside = geo.difference(art).area
            if outside > .05 * geo.area + .05:
                lint.fail('stray_gloss', key, f'gloss: {key} has a {"stroke" if g[0] == "g" else "dot"} off the art '
                                              f'({outside:.2f} of {geo.area:.2f} u²)')
        info = infos.get(icon.key)
        if info and info.get('main'):
            cov = info['main']['span'] / 360
            spans.append((cov, key))
            if cov > .36:
                lint.errors.append(f'gloss: {key} main gloss covers {cov:.0%} of its contour (25-35 %)')
            elif cov < COVERAGE_MIN:
                lint.fail('coverage', key, f'gloss: {key} main gloss covers {cov:.1%} of its contour (25-35 %): '
                                           f'a shape drawn above cuts the run short, or the shape has no clear upper-left curve')
            if not info.get('dot'):
                lint.fail('dot', key, f'gloss: {key} main gloss has no dot (the dot would leave the shape)')
    for icon, key, *_ in list(critters) + list(stickers):
        if icon.key in HEADS:
            cx, cy, r = HEADS[icon.key]
            head = Point(cx + OFF, cy + OFF).buffer(r)
            for g in icon.gloss:
                geo = E.stroke_geom(E.bake(g[1], base), g[2]) if g[0] == 'g' else Point(g[1] + OFF, g[2] + OFF).buffer(g[3])
                if geo.intersects(head):
                    lint.errors.append(f'gloss: white gloss inside the head of {icon.key}')
    if spans:
        spans.sort()
        lint.measures.append(f'gloss coverage (angle of the contour): {spans[0][0]:.1%} ({spans[0][1]}) .. {spans[-1][0]:.1%} '
                             f'({spans[-1][1]}), median {spans[len(spans) // 2][0]:.1%}')


def _pm(p, base):
    return E._mul(E.to_matrix(p.tf), base) if p.tf else base


def _paint(p, base):
    """the paint of one part as [(region, colour, kind)] in drawing order: fill, outline, details (lines and tubes:
    the stroke; a tube's outer stroke is its trace)."""
    m = _pm(p, base)
    sc = E.mat_scale(m)
    tr = C[p.fam][2] if p.fam in C else p.fam
    out = []
    if p.kind == 'fill':
        g = E.fill_geom(E.shape_path(p.s, m))
        out.append((g, engine.tone(p.fam, p.t), 'fill'))
        if p.ol:
            out.append((g.boundary.buffer(p.ol * sc / 2), tr, 'trace'))
    else:
        d = E.bake(p.s[1], m)
        if p.kind == 'tube':
            out.append((E.stroke_geom(d, p.w * sc), tr, 'trace'))
            out.append((E.stroke_geom(d, max(p.w - 4, .8) * sc), engine.tone(p.fam, p.t), 'fill'))
        else:
            out.append((E.stroke_geom(d, p.w * sc), engine.tone(p.fam, p.t), 'line'))
    dc = engine.tone(p.fam, p.det_t) if p.det_t else tr
    for dd in p.det:
        out.append((E.stroke_geom(E.bake(dd, m), p.det_w * sc), dc, 'det'))
    return out


def _cover(icon, base):
    """for each part, the union of what the parts drawn after it cover (fills and tubes)."""
    geos = []
    for p in icon.parts:
        m = _pm(p, base)
        if p.kind == 'fill':
            geos.append(E.fill_geom(E.shape_path(p.s, m)))
        elif p.kind == 'tube':
            geos.append(E.stroke_geom(E.bake(p.s[1], m), p.w * E.mat_scale(m)))
        else:
            geos.append(None)
    out, acc = [None] * len(geos), None
    for i in range(len(geos) - 1, -1, -1):
        out[i] = acc
        if geos[i] is not None and not geos[i].is_empty:
            acc = geos[i] if acc is None else acc.union(geos[i])
    return out


def check_printed(lint, stickers):
    """printed parts (cut=False) and every part's detail lines stay inside the die-cut art (module docstring)."""
    base = E.translate(OFF, OFF)
    for icon, key, groups, dom, sil in stickers:
        # inside the outlines (each die-cut shape shrunk by half its trace) and out to the outlines' outer edges
        ins, outs = [], []
        for p in icon.parts:
            if not p.cut:
                continue
            m = _pm(p, base)
            sc = E.mat_scale(m)
            if p.kind == 'fill':
                g = E.fill_geom(E.shape_path(p.s, m))
                ins.append(g.buffer(-(p.ol or 0) * sc / 2))
                outs.append(g.buffer((p.ol or 0) * sc / 2))
            else:
                d = E.bake(p.s[1], m)
                ins.append(E.stroke_geom(d, max(p.w - 4, .8) * sc) if p.kind == 'tube' else E.stroke_geom(d, p.w * sc))
                outs.append(E.stroke_geom(d, p.w * sc))
        outs += [E.fill_geom(E.shape_path(x, base)) for x in icon.cutx]
        ins += [E.fill_geom(E.shape_path(x, base)).buffer(-1.0) for x in icon.cutx]
        inner, outer = unary_union(ins), unary_union(outs)
        host = E.silhouette(icon.parts, base, icon.cutx)
        traces = {C[p.fam][2] for p in icon.parts if p.cut and p.fam in C}
        cover = _cover(icon, base)
        worst = []
        for i, p in enumerate(icon.parts):
            if p.cut:
                continue
            paint = _paint(p, base)
            own = [g for g, c, k in paint if k == 'trace' and c in traces]
            excess = []
            for g, c, k in paint:
                if c.upper() == E.WHITE and k == 'det':
                    allowed = host        # white details may run to the edge of the white-bordered art
                else:
                    allowed = outer if c in traces else inner
                x = g.difference(allowed)
                if k == 'fill' and own:
                    x = x.difference(own[0])
                if cover[i] is not None:
                    x = x.difference(cover[i])
                x = x.buffer(-.15).buffer(.15)      # drop slivers under 0.3u wide
                if x.area > PRINTED_MAX:
                    excess.append(f'{k} {x.area:.1f} u²')
            if excess:
                worst.append(f'part {i} ({p.kind}{", printed" if not p.cut else ""}) ' + ', '.join(excess))
        if worst:
            lint.fail('printed', key, f'printed: {key} paints outside its die-cut art: ' + '; '.join(worst))


def check_gloss_crossing(lint, stickers, gloss_report=()):
    """no gloss mark crosses the visible colour-trace of a die-cut shape (fill outlines and tube outer strokes). A cluster's
    main gloss (grapes, a bunch of scallions: autogloss strokes the whole silhouette when no single shape can carry it)
    spans several shapes by design and is not checked."""
    base = E.translate(OFF, OFF)
    cluster = {r['key'] for r in gloss_report if r.get('main') and r['main']['part'] == -1}
    for icon, key, groups, dom, sil in stickers:
        cover = _cover(icon, base)
        rings = []
        for i, p in enumerate(icon.parts):
            if not p.cut or p.kind == 'line':
                continue
            m = _pm(p, base)
            sc = E.mat_scale(m)
            if p.kind == 'fill':
                if not p.ol:
                    continue
                ring = E.fill_geom(E.shape_path(p.s, m)).boundary.buffer(p.ol * sc / 2)
            else:
                d = E.bake(p.s[1], m)
                ring = E.stroke_geom(d, p.w * sc).difference(E.stroke_geom(d, max(p.w - 4, .8) * sc))
            if cover[i] is not None:
                ring = ring.difference(cover[i])
            rings.append((i, ring))
        hits = []
        for n, g in enumerate(icon.gloss):
            if n == 0 and icon.key in cluster:
                continue
            geo = E.stroke_geom(E.bake(g[1], base), g[2]) if g[0] == 'g' else Point(g[1] + OFF, g[2] + OFF).buffer(g[3])
            geo = geo.buffer(-.45)        # a slim shape's gloss (inset 1.9u) may graze the trace's inner edge; a crossing goes deeper
            for i, ring in rings:
                a = geo.intersection(ring).area
                if a > CROSS_MAX:
                    hits.append(f'{"stroke" if g[0] == "g" else "dot"} over part {i} ({a:.1f} u²)')
        if hits:
            lint.fail('cross', key, f'gloss: {key} gloss crosses a visible outline: ' + ', '.join(hits))


_FAM_OF = {}
for _f in C:
    for _c in (C[_f][0], C[_f][1], C[_f][2], deep(_f)):
        _FAM_OF[_c] = _f


def families_used(icon, base=None):
    """the share of the coloured, visible art per family (white is free; lemon pastel counts as lemon, so a lemon-pastel
    detail stays under the accent share and a lemon-pastel body is a lemon mass)."""
    base = base or E.translate(OFF, OFF)
    cover = _cover(icon, base)
    area = {}
    for i, p in enumerate(icon.parts):
        for g, c, k in _paint(p, base):
            if c == E.WHITE:
                continue
            f = _FAM_OF.get(c)
            if f is None:
                continue
            if cover[i] is not None:
                g = g.difference(cover[i])
            area[f] = area.get(f, 0) + g.area
    tot = sum(area.values()) or 1
    return sorted(((f, a / tot) for f, a in area.items()), key=lambda kv: -kv[1])


def check_hairlines(lint, stickers):
    for icon, key, groups, dom, sil in stickers:
        thin = []
        for i, p in enumerate(icon.parts):
            ws = []
            if p.det:
                ws.append(('detail', p.det_w))
            if p.tex and p.kind == 'fill':
                ws.append(('texture', p.tex_w))
            if p.kind == 'line':
                ws.append(('line', p.w))
            if p.kind == 'fill' and p.ol:
                ws.append(('outline', p.ol))
            thin += [f'part {i} {n} {w}u' for n, w in ws if w < HAIRLINE - 1e-6]
        if thin:
            lint.fail('hairline', key, f'geometry: {key} has lines under {HAIRLINE}u: ' + ', '.join(thin))


def check_families(lint, stickers):
    for icon, key, groups, dom, sil in stickers:
        fams = families_used(icon)
        major = [(f, a) for f, a in fams if a >= FAMILY_ACCENT]
        if len(major) > 2 and key not in COMPOSITE and key not in MULTICOLOURED:
            lint.fail('families', key, f'families: {key} uses {len(major)} families as masses (' +
                      ', '.join(f'{f} {a:.0%}' for f, a in major) + '); at most two plus white and lemon pastel')
    stale = (COMPOSITE | MULTICOLOURED) - {k for _, k, *_ in stickers}
    if stale and len(stickers) > 100:
        lint.warnings.append('families: COMPOSITE or MULTICOLOURED lists keys with no sticker: ' + ', '.join(sorted(stale)))


def check_geometry(lint, stickers):
    base = E.translate(OFF, OFF)
    bases = []
    for icon, key, groups, dom, sil in stickers:
        cut = next((g for g in groups if g.name == 'cut'), None)
        if sil is None or sil.is_empty or cut is None or not cut.children or not cut.children[0].d:
            lint.errors.append(f'geometry: {key} has no union cut')
            continue
        x0, y0, x1, y1 = (v - OFF for v in sil.bounds)
        bases.append((y1, key))
        if not BASE_Y[0] <= y1 <= BASE_Y[1]:
            lint.fail('base', key, f'geometry: {key} base at y {y1:.2f}')
        if x0 < LIVE[0] or y0 < LIVE[0] or x1 > LIVE[1]:
            lint.fail('live_area', key, f'geometry: {key} art {x0:.1f},{y0:.1f}..{x1:.1f},{y1:.1f}')
        for i, p in enumerate(icon.parts):
            if not p.cut:
                continue
            if p.kind == 'fill':
                g = E.part_geom(p, base)
                if g is not None and not g.is_empty and g.buffer(-.99).is_empty:
                    lint.fail('thin', key, f'geometry: {key} part {i} ({p.s[0]}) is thinner than 2u')
                if p.s[0] == 'rect':
                    r = p.s[5] if len(p.s) > 5 else 0
                    if r < 2 and min(p.s[3], p.s[4]) >= 4:
                        lint.fail('corner', key, f'geometry: {key} part {i} rect corner r {r}u')
            elif p.w < 1.2:
                lint.errors.append(f'geometry: {key} part {i} die-cut line is {p.w}u (>= 1.2u)')
    if bases:
        bases.sort()
        lint.measures.append(f'base y: {bases[0][0]:.2f} ({bases[0][1]}) .. {bases[-1][0]:.2f} ({bases[-1][1]}); '
                             f'{sum(1 for y, _ in bases if BASE_Y[0] <= y <= BASE_Y[1])} of {len(bases)} on y 42')


def check_no_text(lint, icons):
    for icon in icons:
        d, body = icon.render()
        if '<text' in d + body or '<image' in d + body:
            lint.errors.append(f'geometry: {icon.key} draws text or an image')


def check_baskets(lint, baskets):
    from shapely.geometry import box
    viewport = box(0, 0, 80, 64).buffer(.05)
    seen = set()
    for b, kind, layers, lsil in baskets:
        seen.add(kind)
        for need in ('back', 'front', 'lid_open', 'lid_closed'):
            if need not in layers:
                lint.errors.append(f'basket: {kind} has no {need} layer')
        for name, sil in lsil.items():
            # a VectorDrawable clips to its viewport: the 4u white border must not be cut off
            if not viewport.contains(sil.buffer(E.CUT_W / 2)):
                x0, y0, x1, y1 = sil.buffer(E.CUT_W / 2).bounds
                lint.errors.append(f'basket: sk_b_{kind}_{name} die-cut leaves the 80 x 64 viewport '
                                   f'({x0:.1f},{y0:.1f}..{x1:.1f},{y1:.1f})')
        if (kind in DECORATED) != ('deco' in layers):
            lint.errors.append(f'basket: {kind} decoration slice {"missing" if kind in DECORATED else "on an undecorated kind"}')
        if 'lid_closed' in lsil and not lsil['lid_closed'].buffer(4).contains(Point(b.hk[0] + 8, b.hk[1] + 9)):
            lint.errors.append(f'basket: {kind} hanko point {b.hk} is off the closed lid')
        x0, y0, x1, y1 = lsil['back'].bounds
        if not (y0 - 9 <= b.rim <= y1 - 9):
            lint.errors.append(f'basket: {kind} rim {b.rim} outside its art')
        if b.show_open and 'lid_open' not in layers:
            lint.errors.append(f'basket: {kind} stands its lid open but has no lid_open layer')
        for name, nodes in layers.items():
            check_palette(lint, f'sk_b_{kind}_{name}', nodes, 'basket')
    for k in sorted(BASKET_KINDS - seen):
        lint.errors.append(f'basket: no art for {k}')


def run(report):
    lint = Lint()
    from icons import GLOSS_REPORT, ICONS
    from deco import DECO
    stickers = report['stickers']
    for icon, key, groups, dom, sil in stickers:
        check_palette(lint, f'sk_i_{key}', groups, 'item')
        names = {g.name for g in groups}
        for need in ('cut', 'fills', 'lines', 'gloss', 'dominant'):
            if need not in names:
                lint.errors.append(f'groups: sk_i_{key} has no {need} group')
    for icon, key, groups, dom, sil in report['decor']:
        check_palette(lint, f'sk_d_{key}', groups, 'decor')
    for path, (nodes, kind) in report['plain'].items():
        check_palette(lint, path, nodes, kind)
    dsl_keys = {s[0].key for s in stickers}
    check_gloss(lint, stickers, [r for r in GLOSS_REPORT if r['key'] in dsl_keys], report['decor'])
    check_geometry(lint, stickers)
    check_printed(lint, stickers)
    check_gloss_crossing(lint, stickers, [r for r in GLOSS_REPORT if r['key'] in dsl_keys])
    check_families(lint, stickers)
    check_hairlines(lint, stickers)
    check_no_text(lint, ICONS + DECO)
    check_baskets(lint, report['baskets'])
    import launcher
    lint.errors += launcher.safe_zone_errors()
    for path, xml in report['out'].items():
        if '<text' in xml:
            lint.errors.append(f'{path}: contains text')
    lint.finish()
    return lint.errors, lint.warnings, lint.measures
