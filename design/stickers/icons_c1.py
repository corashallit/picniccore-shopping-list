# ---------- art pass, batch 1 (design/stickers/MISSING.md): the 19 remaining generics and the produce aisle ----------
# Executed inside icons.py after the r9 files (shared namespace: every helper here is prefixed _c1 so it never shadows
# an r9 helper another batch relies on). DSL key = catalog key, so catalog_map.py needs no entry. Gloss is generated.
import math as _c1m
from shapely.geometry import LineString as _C1Line, Point as _C1Pt
from shapely.ops import unary_union as _c1_union


def _c1_map(d, f):
    """apply f(x, y) -> (x, y) to every point of an absolute path (M L H V C Q S T Z; H and V become L)."""
    out, st = [], dict(cmd=None, nums=[], cur=(0.0, 0.0), start=(0.0, 0.0))

    def flush():
        cmd, nums = st['cmd'], st['nums']
        if cmd is None:
            return
        if cmd in 'Zz':
            out.append('Z')
            st['cur'] = st['start']
            return
        if cmd in 'HV':
            pts = []
            for v in nums:
                st['cur'] = (v, st['cur'][1]) if cmd == 'H' else (st['cur'][0], v)
                pts.append(st['cur'])
            cmd = 'L'
        elif cmd in 'MLCQST':
            pts = [(nums[i], nums[i + 1]) for i in range(0, len(nums) - 1, 2)]
            if pts:
                st['cur'] = pts[-1]
                if cmd == 'M':
                    st['start'] = pts[0]
        else:
            raise ValueError('_c1_map: unsupported command ' + cmd)
        out.append(cmd + ' '.join('%s %s' % tuple(fmt(v) for v in f(x, y)) for x, y in pts))
    for tk in NUM.findall(d):
        if tk.isalpha():
            flush(); st['cmd'] = tk; st['nums'] = []
        else:
            st['nums'].append(float(tk))
    flush()
    return ''.join(out)


def _c1_xf(d, s=1.0, ox=0.0, oy=0.0, dx=0.0, dy=0.0):
    """scale an absolute path by s about (ox, oy), then move it by (dx, dy)."""
    return _c1_map(d, lambda x, y: (ox + (x - ox) * s + dx, oy + (y - oy) * s + dy))


def _c1_poly(g):
    """a shapely polygon (holes included) as an absolute path."""
    rings = [g.exterior] + list(g.interiors)
    return ''.join('M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in list(r.coords)[:-1]) + 'Z' for r in rings)


def _c1_snowflake(cx, cy, r, w=2.4):
    lines = []
    for k in range(6):
        a = _c1m.radians(90 + k * 60)
        ux, uy = _c1m.cos(a), -_c1m.sin(a)
        lines.append(_C1Line([(cx, cy), (cx + ux * r, cy + uy * r)]))
        bx, by = cx + ux * r * .58, cy + uy * r * .58
        for side in (-1, 1):
            b = a + side * _c1m.radians(42)
            lines.append(_C1Line([(bx, by), (bx + _c1m.cos(b) * r * .36, by - _c1m.sin(b) * r * .36)]))
    g = _c1_union([ln.buffer(w / 2, cap_style=1, join_style=1, quad_segs=4) for ln in lines] + [_C1Pt(cx, cy).buffer(w * .9)])
    return _c1_poly(g.simplify(.04))


def _c1_sparkle(cx, cy, r):
    """four-point sparkle"""
    k = r * .3
    return (f'M{fmt(cx)} {fmt(cy - r)}Q{fmt(cx + k)} {fmt(cy - k)} {fmt(cx + r)} {fmt(cy)}Q{fmt(cx + k)} {fmt(cy + k)} {fmt(cx)} {fmt(cy + r)}'
            f'Q{fmt(cx - k)} {fmt(cy + k)} {fmt(cx - r)} {fmt(cy)}Q{fmt(cx - k)} {fmt(cy - k)} {fmt(cx)} {fmt(cy - r)}Z')


def _c1_flower(cx, cy, r, n=5):
    g = _c1_union([_C1Pt(cx + r * .52 * _c1m.cos(_c1m.radians(-90 + k * 360 / n)), cy + r * .52 * _c1m.sin(_c1m.radians(-90 + k * 360 / n))).buffer(r * .5)
                   for k in range(n)] + [_C1Pt(cx, cy).buffer(r * .5)])
    return _c1_poly(g.simplify(.03))


def _c1_dots(pts):
    return ''.join(f'M{fmt(x)} {fmt(y)}h.1' for x, y in pts)


def _c1_bumpy(cx, cy, rx, ry, n, amp, a0=0):
    """a closed outline of n round bumps (drupelets, florets, kibble heaps)."""
    pts = []
    for i in range(n * 2):
        a = _c1m.radians(a0) + i * _c1m.pi / n
        f = 1 + (amp if i % 2 == 0 else -amp * .4)
        pts.append((cx + rx * f * _c1m.cos(a), cy + ry * f * _c1m.sin(a)))
    return smooth(pts)


# ================================================================ generics (priority 1)

# fruit: a red apple in front, a purple grape bunch behind it on the right, a banana arching over the back
_C1_APPLE = 'M24 16C20 12.5 9.5 12 8.2 23.5C7 33.5 14 41.8 19.5 41.5C21.5 41.4 22.5 40.6 24 40.6C25.5 40.6 26.5 41.4 28.5 41.5C34 41.8 41 33.5 39.8 23.5C38.5 12 28 12.5 24 16Z'
_C1_GR = [(30, 19.4), (36.6, 19.4), (26.8, 25.2), (33.3, 25.2), (39.2, 25.4), (30, 31), (36.6, 30.8), (33.3, 36.4)]
add('fruit_generic', 'Fruit', [
    F(('path', 'M6.8 27.6C5.4 14.6 17 5.6 30.4 6.2L31.4 12.6C20.8 12.6 13.8 18.4 12.8 27.2Q10.4 31.4 6.8 27.6Z'), 'lemon', 'm', band=.28,
      det=['M10.6 19.4C13.6 13.4 19.4 9.8 27 9.2']),
    F(('rect', 29.2, 5.4, 4.8, 7.6, 2, -6), 'lemon', 'deep', ol=1.6),
    F(('circle', 9, 28.6, 1.5), 'lemon', 'tr', ol=0, cut=False),
    TUBE('M33.3 17.6C33.3 14.6 34.2 12.8 36 11.6', 'grape', 3, 'tr'),
    *[F(('circle', x, y, 3.6), 'grape', 'm', band=.26) for x, y in _C1_GR],
    LINE('M19.4 23.8C19.4 20.6 20.2 18.6 21.8 17', 'strawberry', 2.4),
    F(('path', _c1_xf(_C1_APPLE, .72, 24, 41.6, -5)), 'strawberry', 'm', band=.24),
    F(('ellipse', 16.2, 34.6, .8, 1.2, 20), 'lemon', 'lp', ol=0, cut=False),
    F(('ellipse', 21, 36.4, .8, 1.2, -20), 'lemon', 'lp', ol=0, cut=False),
], qty='mixed', aisle='produce')

# vegetables: a broccoli floret behind, a carrot leaning across, a tomato in front
_C1_BHEAD = 'M10 22C7 14.6 14.6 9.4 18.2 11.2C20 6.4 28.4 6.2 30.4 11C34 9 41.2 13.2 38.4 21C40.6 26 34.6 30.4 30.4 28.4C27.6 31.2 21 31.4 18.2 28.4C13.2 30.4 6.6 27.4 10 22Z'
_C1_VTF = 'translate(30.6 17.8) rotate(32)'
add('vegetables_generic', 'Vegetables', [
    F(('path', 'M12.8 21.6L11.8 32.4Q11.6 34.4 13.6 34.4H18.2Q20.2 34.4 20 32.4L19 21.6Z'), 'melon', 'p', band=.3, det=['M15.9 24.6V30.4'], det_t='m', det_w=1.4),
    F(('path', _c1_xf(_C1_BHEAD, .64, 24, 18, -8.2, -2.4)), 'melon', 'm', band=.22,
      det=['M10.8 15.8Q12.4 14.2 14.2 15.6', 'M16.6 11.6Q18.2 10 20 11.4', 'M21.2 16.2Q22.8 14.6 24.6 16', 'M14 20.4Q15.6 18.8 17.4 20.2'], det_w=1.4),
    *[F(('path', l), 'melon', 'm', tf=_C1_VTF, ol=1.6) for l in LEAVES],
    F(('path', CARROT), 'peach', 'm', band=.24, tf=_C1_VTF, det=['M-4.4 8H-1.6', 'M1.4 12.6H3.8', 'M-3.4 17.4H-1']),
    F(('ellipse', 31.6, 33.6, 9.6, 8.4), 'strawberry', 'm', band=.26),
    F(('path', star_path(31.6, 26.2, 4.4, 1.9, 5)), 'melon', 'm', ol=1.3),
], qty='mixed', aisle='produce')

# deli: a wooden board with a holey cheese wedge and two salami slices
add('deli_generic', 'Deli & charcuterie', [
    F(('rect', 34.4, 33.4, 8.6, 5.6, 2.8), 'cocoa', 'p'),
    F(('circle', 39.8, 36.2, 1.1), 'cocoa', 'm', ol=1.2, cut=False),
    F(('path', 'M8.6 30H34.4Q37.2 30 37.2 32.8V39.2Q37.2 42 34.4 42H8.6Q5.8 42 5.8 39.2V32.8Q5.8 30 8.6 30Z'), 'cocoa', 'p', band=.34, band_t='m',
      det=['M9.6 34.2Q13 33.2 16.4 34.2', 'M22.4 33.8Q26.6 32.8 30.8 33.8'], det_t='m', det_w=1.4),
    F(('path', 'M8.6 31.6L24.2 17.4Q27 15 27 18.8V31.6Z'), 'lemon', 'm', band=.26,
      tex=['M14.4 26.6h.1M21.6 22.4h.1M22 28.6h.1'], tex_t='deep', tex_w=3.4),
    F(('circle', 30.6, 22.6, 6.6), 'strawberry', 'm', tex=[_c1_dots([(28.2, 20.2), (32.2, 19.6), (30, 23.4), (33.6, 23.8), (28, 25.8), (31.6, 27)])], tex_t='w', tex_w=1.6),
    F(('circle', 36, 26.8, 6.4), 'strawberry', 'm', band=.26, tex=[_c1_dots([(34, 24.6), (37.6, 24), (35.8, 27.6), (38.8, 28.4), (33.4, 29.4)])], tex_t='w', tex_w=1.6),
], qty='mixed', aisle='deli')

# meat: a marbled red cut with a thin fat rim and a round bone, sitting in an open butcher-paper wrap tied with twine
def _c1_inset(d, by):
    from svgelements import Path as _SP
    from shapely.geometry import Polygon as _Poly
    p = _SP(d)
    n = 160
    g = _Poly([(p.point(i / n).x, p.point(i / n).y) for i in range(n)]).buffer(-by, quad_segs=8)
    return _c1_poly(g.simplify(.05))
_C1_STEAK = smooth([(8.2, 30), (8, 18.6), (14.6, 10.8), (24.4, 8.6), (34.6, 10.6), (40, 18.4), (40, 30), (24, 31.4)])
add('meat_generic', 'Meat', [
    F(('path', _C1_STEAK), 'strawberry', 'w', ol=2),
    F(('path', _c1_inset(_C1_STEAK, 2.4)), 'strawberry', 'm', ol=1.6, cut=False,
      det=['M13.4 22.4Q17.4 18.6 22 20.4', 'M24.4 15.4Q27.6 13.8 31 15', 'M18.4 26.2Q22.6 24.2 26 25.8'], det_t='w', det_w=1.5),
    F(('circle', 31.4, 21.4, 3.4), 'strawberry', 'w', ol=1.5, cut=False),
    F(('circle', 31.4, 21.4, 1.3), 'strawberry', 'p', ol=1.2, cut=False),
    F(('path', 'M6 21.4L15.4 27.6L24 25L32.6 27.6L42 21.4L40.4 38.6Q40 42 36.6 42H11.4Q8 42 7.6 38.6Z'), 'cocoa', 'p', band=.26,
      det=['M8.4 26.4L13.2 36.6', 'M39.6 26.4L34.8 36.6'], det_t='m', det_w=1.4),
    F(('path', 'M7.4 31H40.6L40.3 34.2H7.7Z'), 'strawberry', 'w', ol=1.2, cut=False, tex=[''.join(f'M{x} 35.6L{x + 3.4} 29.6' for x in range(4, 44, 4))], tex_t='m', tex_w=1.6),
], qty='mixed', aisle='meat')

# fish & seafood: a whole blue fish with a forked tail on a mound of crushed ice, a lemon wedge in front
add('seafood_generic', 'Fish & seafood', [
    F(('path', 'M30.4 20.6L39.8 12.4Q42.2 10.6 42 13.4L40.2 20.8L42 28.2Q42.2 31 39.8 29.2L30.4 22Z'), 'ramune', 'm', det=['M35 18.4L38.8 15.6', 'M35 22.8L38.8 25.8'], det_t='p', det_w=1.3),
    F(('path', 'M15.6 11.8C17.4 8.4 22.2 7.6 25 10.6L21.4 13.6Z'), 'ramune', 'p', ol=1.6),
    F(('path', smooth([(6.8, 21.8), (9.6, 15.6), (17, 12.2), (25.4, 12.8), (32.4, 18.6), (32.6, 23.8), (25.4, 29.2), (16.6, 29.8), (9.6, 27.4)])), 'ramune', 'm', band=.26,
      tex=['M17.6 20q2 -2.4 4 0M23 18.6q2 -2.4 4 0M20.4 23.6q2 -2.4 4 0M25.8 22.6q2 -2.4 4 0'], tex_t='p', tex_w=1.6, det=['M15.6 15.8Q17.6 20.8 15.6 26.2']),
    F(('circle', 11.6, 19.6, 2.4), 'ramune', 'w', ol=1.4, cut=False),
    F(('circle', 11.9, 19.8, 1.1), 'ramune', 'tr', ol=0, cut=False),
    F(('path', smooth([(6.6, 39.6), (7.6, 34.6), (11.8, 31.4), (17.6, 29.8), (24, 29.4), (30.4, 29.8), (36.2, 31.4), (40.4, 34.6), (41.4, 39.6), (39.4, 41.8), (24, 42), (8.6, 41.8)])), 'ramune', 'w', band=.3, band_t='p'),
    *[F(('rect', x, y, 4.6, 4.2, 1.2, a), 'ramune', 'p', ol=1.2, cut=False) for x, y, a in [(9.6, 34.4, 16), (16.4, 32.6, -12), (22.6, 34.8, 8), (13.4, 37.4, -6), (20.4, 38.2, 20)]],
    F(('path', 'M27.6 31.4H41A6.7 6.7 0 0 1 27.6 31.4Z'), 'lemon', 'm'),
    F(('path', 'M29.6 31.4H39A4.7 4.7 0 0 1 29.6 31.4Z'), 'lemon', 'p', ol=1.3, cut=False, det=['M34.3 31.4V35.9', 'M34.3 31.4L31.2 34.6', 'M34.3 31.4L37.4 34.6'], det_t='m', det_w=1.2),
], qty='mixed', aisle='seafood')

# dairy: a vintage glass milk bottle: slim neck, sloping shoulders, a pink paper cap, white milk up to the shoulders, a blue band
add('dairy_generic', 'Dairy', [
    F(('rect', 17.6, 5.4, 12.8, 5.8, 2.4), 'sakura', 'm', det=['M20.4 7.4V9.4M24 7.4V9.4M27.6 7.4V9.4'], det_w=1.2),
    F(('path', 'M19.2 11H28.8V13.6C28.8 17.4 35.6 19 35.6 26V38.4Q35.6 42 32 42H16Q12.4 42 12.4 38.4V26C12.4 19 19.2 17.4 19.2 13.6Z'), 'ramune', 'w',
      band=.2, band_t='p', tex=['M10 14.4H38'], tex_t='p', tex_w=7, det=['M17.2 18Q20.6 16.8 24 18T30.8 18']),
    F(('rect', 18.2, 9.8, 11.6, 3.6, 1.8), 'ramune', 'p', ol=1.6),
    F(('rect', 12.6, 25.4, 22.8, 7.4), 'ramune', 'm', ol=0, cut=False, det=['M12.6 25.4H35.4', 'M12.6 32.8H35.4'], det_w=1.4),
    F(('path', star_path(24, 29.2, 2.9, 1.3, 5)), 'ramune', 'w', ol=1.2, cut=False),
], qty='bottle', aisle='dairy')

# plant-based: a two-leaf sprout growing out of a little cream bowl of soil
add('plant_based_generic', 'Plant-based', [
    TUBE('M24 27.6C24 23.6 23.6 20.4 24 16.8', 'melon', 3.4, 'm'),
    F(('ellipse', 16.6, 14.4, 7.6, 5, 22), 'melon', 'm', band=.26, det=['M10.6 12.6Q16.6 13.4 22.6 16.6'], det_t='p', det_w=1.4),
    F(('ellipse', 31.6, 12, 8, 5.4, -22), 'melon', 'm', band=.26, det=['M25.4 15.4Q31.4 12.4 37.6 9.8'], det_t='p', det_w=1.4),
    F(('path', 'M10.4 28C12.6 24.6 35.4 24.6 37.6 28Z'), 'cocoa', 'm', ol=1.6, det=['M17 26.4h.1M27.6 25.8h.1M32.4 26.8h.1'], det_t='p', det_w=1.6),
    F(('path', 'M8.4 28.6H39.6C39.6 36.4 33.8 42 27 42H21C14.2 42 8.4 36.4 8.4 28.6Z'), 'cocoa', 'p', band=.3),
    F(('rect', 6.4, 26.2, 35.2, 4.8, 2.4), 'cocoa', 'm'),
], qty='mixed', aisle='plant_based')

# breakfast: a sunny-side-up egg beside a buttered slice of toast on a round plate
_C1_SLICE = 'M9 22C9 17 12 14.6 16.4 15C18.6 12.4 25.4 12.4 27.6 15C32 14.6 35 17 35 22C35 25 33.4 26.4 32 27V38.8Q32 41.6 29.2 41.6H14.8Q12 41.6 12 38.8V27C10.6 26.4 9 25 9 22Z'
_C1_CRUMB = 'M11.8 22C11.8 18.8 13.9 17.4 17.1 17.8C19.1 15.4 24.9 15.4 26.9 17.8C30.1 17.4 32.2 18.8 32.2 22C32.2 24.2 30.8 25.2 29.4 25.6V37.8Q29.4 39 28.2 39H15.8Q14.6 39 14.6 37.8V25.6C13.2 25.2 11.8 24.2 11.8 22Z'
add('breakfast_generic', 'Breakfast', [
    F(('ellipse', 24, 36.4, 18.4, 5.6), 'ramune', 'w'),
    F(('ellipse', 24, 36, 13.6, 3.4), 'ramune', 'p', ol=1.3, cut=False),
    F(('path', _c1_xf(_C1_SLICE, .74, 9, 41.6, -2, -6)), 'peach', 'm'),
    F(('path', _c1_xf(_C1_CRUMB, .74, 9, 41.6, -2, -6)), 'peach', 'w', ol=0, cut=False),
    F(('rect', 11.6, 19.6, 7.4, 5.4, 1.6, -10), 'lemon', 'p', ol=1.2, cut=False),
    F(('path', blob(30.4, 34.6, 10.4, 4.8, 9, .08, 4)), 'lemon', 'w', ol=2),
    F(('circle', 31.2, 32.4, 4), 'lemon', 'm', band=.32, band_t='deep'),
], qty='mixed', aisle='breakfast')

# baking: a pink mixing bowl heaped with flour, a wire whisk leaning in, a puff of flour above
def _c1_cloud(cx, cy, s=1.0):
    g = _c1_union([_C1Pt(cx - 3.6 * s, cy + .8 * s).buffer(3 * s), _C1Pt(cx, cy - 1 * s).buffer(3.8 * s), _C1Pt(cx + 3.8 * s, cy + .8 * s).buffer(3 * s),
                   _C1Pt(cx, cy + 1.8 * s).buffer(2.2 * s)])
    return _c1_poly(g.simplify(.03))
add('baking_generic', 'Baking supplies', [
    F(('path', _c1_cloud(12.4, 12.4)), 'periwinkle', 'w', ol=1.6),
    F(('path', 'M10.6 25C11.8 20.6 16.6 18.6 21 20.2C23.6 17.2 30.4 17.6 32 21.6C35.4 21.4 37.6 23 37.4 25Z'), 'sakura', 'w', ol=1.6),
    F(('rect', 32.6, 4.8, 4.6, 11.4, 2.3, 32), 'periwinkle', 'm', det=['M33.4 14.6H36.4'], det_t='p', det_w=1.2),
    LINE('M31.2 15.6C24.6 17 20.6 21.6 20.8 26M31.2 15.6C27.4 18.6 25.6 22.4 25.8 26M31.2 15.6C30.4 19.4 30.4 22.8 31 26', 'periwinkle', 1.8),
    F(('path', 'M6.6 25.8H41.4C41.4 34.2 35 39.2 28.2 39.2H19.8C13 39.2 6.6 34.2 6.6 25.8Z'), 'sakura', 'm', band=.26),
    F(('rect', 15.6, 37.4, 16.8, 4.6, 2.3), 'sakura', 'm'),
    F(('rect', 5.4, 23.4, 37.2, 4.8, 2.4), 'sakura', 'p'),
], qty='mixed', aisle='baking')

# condiments: a red and a yellow squeeze bottle with a little shaker in front
def _c1_squeeze(x0, x1, fam, body='m', cap='w'):
    cx = (x0 + x1) / 2
    return [F(('path', f'M{fmt(cx - 1.6)} 8.6L{fmt(cx - .8)} 6.2Q{fmt(cx)} 4.8 {fmt(cx + .8)} 6.2L{fmt(cx + 1.6)} 8.6Z'), fam, cap, ol=1.6),
            F(('rect', cx - 4.4, 7.8, 8.8, 5.6, 2.2), fam, cap),
            F(('path', f'M{fmt(x0)} 17.4C{fmt(x0)} 14.2 {fmt(x0 + 2)} 12.8 {fmt(x0 + 4.4)} 12.8H{fmt(x1 - 4.4)}C{fmt(x1 - 2)} 12.8 {fmt(x1)} 14.2 {fmt(x1)} 17.4V38.6Q{fmt(x1)} 42 {fmt(x1 - 3.4)} 42H{fmt(x0 + 3.4)}Q{fmt(x0)} 42 {fmt(x0)} 38.6Z'), fam, body, band=.2)]
add('condiments_generic', 'Condiments', [
    *_c1_squeeze(6.6, 20.6, 'strawberry'),
    F(('rect', 9.4, 18.6, 8.4, 7.6, 2.4), 'strawberry', 'w', ol=1.4, cut=False),
    F(('circle', 13.6, 22.4, 2), 'strawberry', 'm', ol=1.2, cut=False),
    *_c1_squeeze(27.4, 41.4, 'lemon', cap='p'),
    F(('rect', 30.2, 18.6, 8.4, 7.6, 2.4), 'lemon', 'w', ol=1.4, cut=False, det=['M31.8 22.6Q33 20.4 34.4 22.6T37 22.6'], det_t='m', det_w=1.5),
    F(('path', 'M17.8 28.2C17.8 23 20.6 20.6 24 20.6C27.4 20.6 30.2 23 30.2 28.2Z'), 'periwinkle', 'm'),
    *[F(('circle', x, y, .9), 'periwinkle', 'w', ol=0, cut=False) for x, y in [(21.6, 25.2), (24, 23.6), (26.4, 25.2)]],
    F(('path', 'M17.2 27.6H30.8L31.8 38.8Q32 42 28.8 42H19.2Q16 42 16.2 38.8Z'), 'periwinkle', 'w', band=.4, band_t='p'),
    F(('rect', 16.4, 26, 15.2, 3.8, 1.9), 'periwinkle', 'm'),
], qty='mixed', aisle='condiments')

# pantry staples: a little wooden shelf with a tin, a jar of jam and a box lined up on it
add('pantry_generic', 'Pantry staples', [
    F(cyl(7, 18.6, 20.8, 37.4, 1.9), 'periwinkle', 'p'),
    F(('rect', 7.6, 25, 10.4, 7.6), 'periwinkle', 'm', ol=0, cut=False, det=['M7.6 25H18', 'M7.6 32.6H18'], det_w=1.3),
    F(ell_top(7, 18.6, 20.8, 1.9), 'periwinkle', 'w'),
    F(('path', 'M20.8 18.6H29.6Q31.4 18.6 31.4 20.6V34.4Q31.4 36.4 29.4 36.4H21Q19 36.4 19 34.4V20.6Q19 18.6 20.8 18.6Z'), 'strawberry', 'm', band=.2),
    F(('rect', 21, 24, 8.4, 7, 2), 'strawberry', 'w', ol=1.3, cut=False),
    F(('rect', 18.4, 13.6, 13.6, 6, 2.2), 'cocoa', 'm', det=['M21 15.4V17.8M24 15.4V17.8M27 15.4V17.8M29.6 15.4V17.8'], det_w=1.2),
    F(('rect', 32.2, 10.4, 9.4, 26, 2.4), 'periwinkle', 'm', det=['M32.6 14.6H41.2'], det_t='p'),
    F(('rect', 34.4, 19.6, 5, 8.4, 1.6), 'periwinkle', 'w', ol=1.2, cut=False),
    F(('rect', 5.4, 35, 37.2, 7, 2.6), 'cocoa', 'm', band=.34, band_t='deep', det=['M10.4 37.8H17', 'M25.4 38.6H33.4'], det_t='p', det_w=1.3),
], qty='mixed', aisle='pantry')

# snacks: an open pink paper snack bag with a pretzel poking out, a cracker and a cookie spilled beside it
_C1_PZ = lambda d: _c1_xf(d, .46, 24, 25, -7.6, -10.4)
add('snacks_generic', 'Snacks', [
    TUBE(_C1_PZ(PRZ), 'cocoa', 4.4, 'm'),
    TUBE(_C1_PZ('M17.4 40.4L30.6 24.4'), 'cocoa', 4.4, 'm'),
    TUBE(_C1_PZ('M30.6 40.4L17.4 24.4'), 'cocoa', 4.4, 'm'),
    F(('path', 'M7.6 20.4L10 18.2L12.4 20.4L14.8 18.2L17.2 20.4L19.6 18.2L22 20.4L24.4 18.4L25.4 38.6Q25.6 42 22.2 42H10.6Q7.2 42 7.4 38.6Z'), 'sakura', 'p', band=.22,
      det=['M9 25H24'], det_t='m'),
    F(('path', heart_path(16.4, 32.4, 3.6)), 'sakura', 'm', ol=1.2, cut=False),
    F(('rect', 28.6, 15.6, 11.4, 11.4, 2.4, 14), 'cocoa', 'p', band=.26, sband=False,
      tex=[_c1_dots([(31.6, 19.4), (34.6, 19.8), (37.4, 20.4), (31, 22.6), (34, 23), (37, 23.4)])], tex_t='m', tex_w=1.8),
    F(('path', blob(33.4, 34.6, 7.6, 7.2, 10, .05, 7)), 'cocoa', 'p', band=.24),
    *[F(('path', blob(x, y, 1.4, 1.1, 6, .2, int(x * 7))), 'cocoa', 'tr', ol=0, cut=False) for x, y in [(30.4, 32), (35.6, 31), (33.6, 36.2), (37.4, 35.4), (29.8, 36.8)]],
], qty='mixed', aisle='snacks')

# drinks: a tall glass of pink fizz with ice cubes, a striped straw and a lemon slice on the rim
add('beverages_generic', 'Drinks', [
    F(('rect', 28.2, 4.8, 3.6, 20.4, 1.8, 18), 'sakura', 'w', ol=1.5, tex=['M24 11L36 7M24 15L36 11M24 19L36 15M24 23L36 19M24 27L36 23'], tex_t='m', tex_w=1.8, sband=False),
    F(('path', 'M11.6 12.2H36.4L34 38.4Q33.6 42 30 42H18Q14.4 42 14 38.4Z'), 'sakura', 'm', band=.22, band_t='deep',
      tex=['M9 14.4H39'], tex_t='w', tex_w=5.6, det=['M12.6 17.2H35.4']),
    F(('rect', 16.4, 20, 6.6, 6.6, 1.8, -14), 'sakura', 'w', ol=1.3, cut=False),
    F(('rect', 24.4, 23.4, 6.6, 6.6, 1.8, 12), 'sakura', 'w', ol=1.3, cut=False),
    *[F(('circle', x, y, r), 'sakura', 'p', ol=1.2, cut=False) for x, y, r in [(20, 32.6, 1.4), (28, 35.4, 1.1), (22.4, 37.2, .9)]],
    F(('circle', 13.6, 12.6, 6.2), 'lemon', 'm'),
    F(('circle', 13.6, 12.6, 4.2), 'lemon', 'p', ol=1.2, cut=False, det=['M13.6 8.8V16.4', 'M10.3 10.7L16.9 14.5', 'M10.3 14.5L16.9 10.7'], det_t='m', det_w=1.2),
], qty='mixed', aisle='beverages')

# frozen: a frosty blue box with frost dripping off its lid and a big white snowflake on its blank front
add('frozen_generic', 'Frozen food', [
    F(('path', _c1_sparkle(8.6, 9.4, 3.6)), 'ramune', 'w', ol=1.4),
    F(('path', _c1_sparkle(39.6, 8, 3)), 'ramune', 'w', ol=1.4),
    F(('rect', 9, 14.4, 30, 27.6, 3.6), 'ramune', 'm', band=.22, band_t='deep'),
    F(('path', 'M11 12.4H37Q40 12.4 40 15.4V17.8Q38.6 19.8 36.8 18Q35.6 22.4 33.2 18.6Q31 20.4 29 18.4Q27 23.2 24.4 18.6Q22.2 21 20 18.4Q18 21.8 15.8 18.4Q13.6 20.6 11.6 18.2Q9.6 19.6 8 17.8V15.4Q8 12.4 11 12.4Z'), 'ramune', 'w'),
    F(('path', _c1_snowflake(24, 30.2, 8.4)), 'ramune', 'w', ol=1.4, cut=False),
], qty='mixed', aisle='frozen')

# baby: a baby bottle, milk inside, a lemon collar and a soft rounded nipple
add('baby_generic', 'Baby', [
    F(('path', 'M19.8 14.2C19.8 11 20.8 9.4 22.2 8.8C22 6.6 26 6.6 25.8 8.8C27.2 9.4 28.2 11 28.2 14.2Z'), 'lemon', 'p', ol=2),
    F(('path', 'M17.2 18.6H30.8L32.4 38.4Q32.6 42 28.8 42H19.2Q15.4 42 15.6 38.4Z'), 'sakura', 'w', band=.2, band_t='p',
      tex=['M13 21.4H35'], tex_t='p', tex_w=5.4, det=['M18.6 26.4H21.6', 'M18.6 30.4H23', 'M18.6 34.4H21.6'], det_t='m', det_w=1.5),
    F(('path', heart_path(27.4, 31.4, 2.8)), 'sakura', 'm', ol=1.2, cut=False),
    F(('rect', 15.4, 12.8, 17.2, 7, 2.6), 'lemon', 'm', det=['M18.6 14.6V18M21.8 14.6V18M25 14.6V18M28.2 14.6V18'], det_w=1.2),
], qty='mixed', aisle='baby')

# pet: a red pet dish with a white paw print, heaped with kibble
_C1_KIB = [(15.4, 24.4, 3.6, 0), (21, 21.4, 3.8, 3), (27.4, 21.8, 3.8, 5), (32.8, 24.4, 3.6, 8), (24.4, 25.6, 3.6, 11), (18.6, 26.6, 3.4, 13), (30, 26.8, 3.4, 15)]
add('pet_generic', 'Pet supplies', [
    *[F(('path', blob(x, y, r, r * .86, 6, .16, sd)), 'cocoa', 'm', ol=1.6, det=[f'M{fmt(x - .6)} {fmt(y - .4)}h.1'], det_t='p', det_w=1.6) for x, y, r, sd in _C1_KIB],
    F(('path', 'M9.6 27.6H38.4L41.6 38.4Q42.4 42 38.8 42H9.2Q5.6 42 6.4 38.4Z'), 'strawberry', 'm', band=.26, band_t='deep'),
    F(('rect', 7.6, 25.2, 32.8, 5, 2.5), 'strawberry', 'p'),
    F(('path', 'M24 33.8C26.6 33.8 28.4 35.8 28.4 37.6C28.4 39 27.2 39.6 26 39.4C25.2 39.2 24.6 38.8 24 38.8C23.4 38.8 22.8 39.2 22 39.4C20.8 39.6 19.6 39 19.6 37.6C19.6 35.8 21.4 33.8 24 33.8Z'), 'strawberry', 'w', ol=1.2, cut=False),
    *[F(('ellipse', x, y, 1.5, 1.9, a), 'strawberry', 'w', ol=1.2, cut=False) for x, y, a in [(18.6, 32.6, -24), (21.8, 30.4, -8), (26.2, 30.4, 8), (29.4, 32.6, 24)]],
], qty='mixed', aisle='pet')

# household: a mint cleaning caddy with a centre handle, a spray bottle on one side and a sponge on the other
add('household_generic', 'Household', [
    F(('path', 'M9.4 8.6H19.6Q21.4 8.6 21.4 10.4V13.2Q21.4 15 19.6 15H13L11 13.2H7Q5.8 13.2 5.8 12V10.4Q5.8 8.6 7.6 8.6Z'), 'ramune', 'm', ol=1.8),
    TUBE('M13.6 14.6C13.4 17.4 11.8 18.6 10 18.8', 'ramune', 2.6, 'm'),
    F(('rect', 12.2, 14.4, 7.4, 4.4, 2), 'ramune', 'm', ol=1.6),
    F(('path', 'M12.6 17.8H19.2Q21.6 17.8 21.6 20.6V30H9.6V21.2Q9.6 17.8 12.6 17.8Z'), 'ramune', 'p'),
    F(('rect', 21.8, 12, 4.4, 15, 2), 'mint', 'm', ol=1.6),
    F(('rect', 17.6, 8.2, 12.8, 5.6, 2.6), 'mint', 'm', det=['M20.6 11H27.4'], det_t='p', det_w=1.4),
    F(('rect', 28.4, 17.4, 12.6, 10.6, 2.6, -8), 'lemon', 'm', sband=False, tex=['M29 19.6H42'], tex_t='deep', tex_w=4.2, det=['M31.4 24.6h.1M35 26h.1M38.2 24h.1'], det_t='deep', det_w=1.8),
    F(('path', 'M6.6 27.6H41.4L39.4 39Q38.8 42 35.8 42H12.2Q9.2 42 8.6 39Z'), 'mint', 'm', band=.26, band_t='deep'),
    F(('rect', 5.4, 25.2, 37.2, 4.8, 2.4), 'mint', 'p'),
    F(('rect', 19.4, 32, 9.2, 5, 2.2), 'mint', 'p', ol=1.3, cut=False),
], qty='mixed', aisle='household')

# health & personal care: a pastel zip toiletry bag (a printed flower) with a toothbrush and a lotion tube poking out of the top
add('health_generic', 'Health & personal care', [
    F(('rect', 12, 6, 4.2, 22, 2.1, -16), 'ramune', 'm'),
    F(('rect', 15.8, 6.2, 4.6, 7.4, 2, -16), 'ramune', 'w', ol=1.5, det=['M16.4 8.4L20.2 7.4', 'M16.8 10.6L20.6 9.6'], det_w=1.2),
    F(('rect', 27.8, 5.6, 8, 5.4, 2, 14), 'sakura', 'm'),
    F(('path', 'M27.4 10.6H36.6L35.6 26H28.4Z'), 'sakura', 'w', ol=2, tf='rotate(14 32 18)', det=['M29.4 15.6H34.6'], det_t='m', det_w=1.4),
    F(('path', 'M10.8 21.2H37.2C40.4 21.2 41.6 23.6 41.4 26.6L40.4 37.8Q40 42 35.8 42H12.2Q8 42 7.6 37.8L6.6 26.6C6.4 23.6 7.6 21.2 10.8 21.2Z'), 'sakura', 'p', band=.26,
      det=['M9.4 25H38.6', 'M11.6 23.6V26.4M14.4 23.6V26.4M17.2 23.6V26.4M20 23.6V26.4M22.8 23.6V26.4M25.6 23.6V26.4M28.4 23.6V26.4M31.2 23.6V26.4'], det_w=1.2),
    F(('rect', 33.6, 25.2, 3.6, 6.4, 1.6), 'sakura', 'm', ol=1.3, cut=False),
    F(('path', _c1_flower(22, 33.6, 3.4)), 'sakura', 'm', ol=1.2, cut=False),
    F(('circle', 22, 33.6, 1.2), 'sakura', 'w', ol=1.2, cut=False),
], qty='mixed', aisle='health')

# garden: a blue watering can tipped to sprinkle a tiny daisy on a strip of grass
_C1_CAN = 'rotate(14 16 24)'
add('garden_generic', 'Garden', [
    TUBE('M15.4 15.6C14.2 9.6 6.8 9.8 7 16.4C7.2 20.4 8.4 22.6 10.2 24.4', 'ramune', 3.2, 'm', tf=_C1_CAN),
    TUBE('M23.2 30.4L33 19.2', 'ramune', 3.8, 'm', tf=_C1_CAN),
    F(('ellipse', 34.4, 17.8, 3.6, 2.2, -50), 'ramune', 'p', tf=_C1_CAN, ol=1.6),
    F(('path', 'M10 18.2Q10 15.2 13 15.2H21Q24 15.2 24 18.2V31Q24 34 21 34H13Q10 34 10 31Z'), 'ramune', 'm', band=.26, band_t='deep', tf=_C1_CAN,
      det=['M10.2 20.6H23.8'], det_t='p'),
    *[F(('path', f'M{fmt(x)} {fmt(y - 2.6)}C{fmt(x + 1.9)} {fmt(y)} {fmt(x + 1.9)} {fmt(y + 2)} {fmt(x)} {fmt(y + 2)}C{fmt(x - 1.9)} {fmt(y + 2)} {fmt(x - 1.9)} {fmt(y)} {fmt(x)} {fmt(y - 2.6)}Z'),
         'ramune', 'p', ol=1.3) for x, y in [(36.4, 25.6), (40.4, 23.4), (39.6, 30.2)]],
    TUBE('M31.6 38.4C31.6 35.4 31.4 33 31.2 31', 'melon', 2.8, 'm'),
    F(('ellipse', 27.6, 35.4, 3.2, 1.8, 28), 'melon', 'm', ol=1.4),
    *[F(('ellipse', 31.2 + 4.3 * _c1m.cos(_c1m.radians(a)), 29.4 + 4.3 * _c1m.sin(_c1m.radians(a)), 2.6, 1.7, a), 'ramune', 'w', ol=1.3) for a in range(0, 360, 45)],
    F(('circle', 31.2, 29.4, 2.5), 'lemon', 'm', ol=1.3),
    F(('path', 'M6 39.4C9 37 12.6 38.4 15.4 37.2C18.6 35.8 21.8 37.8 25 37C28.2 36.2 31.6 37.6 34.8 36.8C37.4 36.2 40 37 42 38.4V39.4Q42 42 39.4 42H8.6Q6 42 6 39.4Z'),
      'melon', 'm', det=['M11 40.6l.6-1.4M20 40.6l.6-1.4M38 40.6l.6-1.4'], det_t='p', det_w=1.2),
], qty='mixed', aisle='garden')

# ================================================================ produce

# clementines: three small flat mandarins stacked in a little pyramid, a pair of dark leaves on the top one
def _c1_clem(cx, cy, rx=8.6, ry=7.2, seed=0):
    d = [(cx - rx * .5, cy - ry * .3), (cx + rx * .1, cy - ry * .55), (cx + rx * .55, cy - ry * .1), (cx - rx * .2, cy + ry * .15), (cx + rx * .25, cy + ry * .35)]
    return F(('ellipse', cx, cy, rx, ry), 'peach', 'm', band=.26, band_t='deep', tex=[_c1_dots(d)], tex_t='p', tex_w=1.4)
add('clementine', 'Clementines', [
    _c1_clem(24, 25.4),
    F(('path', 'M24 18.4C20.6 13.2 14.4 11.8 10.2 13.6C13 18.2 18.8 20.2 24 18.4Z'), 'melon', 'deep', ol=1.6, det=['M12.2 14Q18.4 15.2 22.6 17.8'], det_t='p', det_w=1.2),
    F(('path', 'M24 18.4C26.4 12.8 32.2 10 37.4 10.8C35.6 16.2 30 19.2 24 18.4Z'), 'melon', 'deep', ol=1.6, det=['M25.6 17.2Q30.6 13.4 35.6 11.6'], det_t='p', det_w=1.2),
    F(('circle', 24, 18.8, 1.3), 'melon', 'tr', ol=0, cut=False),
    _c1_clem(14.8, 34.8),
    _c1_clem(33.2, 34.8),
], qty='2 lb box', aisle='produce')

# raspberries: three bumpy deep-pink berries, the front one tipped to show its hollow
add('raspberries', 'Raspberries & blackberries', [
    F(('path', 'M24.4 9.6C25.6 5.6 31 4.2 34.6 6C32.6 10 28 11.4 24.4 9.6Z'), 'melon', 'm', ol=1.5, det=['M26.4 8.8Q30 7.4 33.2 6.4'], det_w=1.2),
    F(('path', _c1_bumpy(16.2, 19.6, 8.2, 8.8, 9, .07, 10)), 'strawberry', 'm', band=.24,
      tex=[_c1_dots([(12.6, 15.6), (16.6, 14.4), (20.4, 16), (11.8, 20.4), (15.8, 19.6), (19.8, 20.8), (14, 24.4), (18.2, 24.6)])], tex_t='p', tex_w=2),
    F(('path', _c1_bumpy(31.4, 18.4, 8.2, 8.8, 9, .07, 30)), 'strawberry', 'm', band=.24,
      tex=[_c1_dots([(27.8, 14.4), (31.8, 13.2), (35.6, 14.8), (27, 19.2), (31, 18.4), (35, 19.6), (29.2, 23.2), (33.4, 23.4)])], tex_t='p', tex_w=2),
    F(('path', _c1_bumpy(25.4, 33, 11.8, 8.8, 11, .06, 0)), 'strawberry', 'm', band=.26,
      tex=[_c1_dots([(20.6, 29), (25.2, 27.6), (29.8, 28.6), (34, 31), (22.4, 33.2), (27, 32.6), (31.4, 34.4), (24.4, 37.4), (28.8, 37.6)])], tex_t='p', tex_w=2),
    F(('ellipse', 16.4, 33.2, 3, 4.8, 8), 'strawberry', 'p', ol=1.5, cut=False),
    F(('ellipse', 16.1, 33.2, 1.4, 2.8, 8), 'strawberry', 'deep', ol=0, cut=False),
], qty='170 g', aisle='produce')

# pineapple: a golden oval with a diamond crosshatch and a spiky green crown
_C1_PH = ['M10 30L28 12', 'M10 37L33 14', 'M13 41L37 17', 'M20 42L39 23', 'M28 42L40 30', 'M10 23L21 12',
          'M38 30L20 12', 'M38 37L15 14', 'M35 41L11 17', 'M28 42L9 23', 'M20 42L8 30', 'M38 23L27 12']
add('pineapple', 'Pineapple', [
    F(('path', 'M24 21.4C20 16.6 15.8 11 15.2 6.6C19.4 9 22.2 12.4 24 16.4C25.8 12.4 28.6 9 32.8 6.6C32.2 11 28 16.6 24 21.4Z'), 'melon', 'm', ol=1.6),
    F(('path', 'M24 22C19.4 19.2 13.6 17.6 9.6 18.4C12.8 14.8 18.8 15.2 24 18.8C29.2 15.2 35.2 14.8 38.4 18.4C34.4 17.6 28.6 19.2 24 22Z'), 'melon', 'm', ol=1.6),
    F(('path', 'M24 19.6C22.4 14 22.4 9.2 24 5.6C25.6 9.2 25.6 14 24 19.6Z'), 'melon', 'p', ol=1.6),
    F(('ellipse', 24, 30.6, 11.4, 11.4), 'lemon', 'm', band=.22, band_t='deep', tex=_C1_PH, tex_t='deep', tex_w=1.5),
], qty='each', aisle='produce')

# mango: a plump kidney shape lying on a slant, green at the stem shoulder blushing to orange, a tiny stem and leaf
_C1_MTF = 'translate(24.6 28.1) rotate(24)'
add('mango', 'Mangoes', [
    TUBE('M-14.6 -8.6C-15.4 -11.4 -14.8 -13.6 -13 -15', 'melon', 2.8, 'tr', tf=_C1_MTF),
    F(('path', 'M-13.6 -12.8C-12.4 -17.6 -6.8 -20 -2.6 -18.8C-4 -14.2 -9.2 -12 -13.6 -12.8Z'), 'melon', 'm', ol=1.5, tf=_C1_MTF, det=['M-11.8 -13.8Q-7.8 -16.2 -4.2 -18'], det_w=1.2),
    F(('path', smooth([(-18.4, -1), (-16, -8.6), (-8.4, -11.4), (1.4, -10.2), (9.6, -9.4), (16.4, -5.4), (18.6, 1.6), (14.4, 8.6), (4.6, 11.6), (-6, 11.2), (-14.6, 7.6)])),
      'peach', 'm', band=.26, band_t='deep', tf=_C1_MTF, tex=['M-22 2C-18 -10 -8 -14 2 -12'], tex_t='#8EDB76', tex_w=12),
], qty='each', aisle='produce')

# kiwis: a fuzzy brown kiwi behind its cut half (green flesh, pale centre, a ring of dark seeds)
add('kiwi', 'Kiwis', [
    F(('ellipse', 18.4, 23.6, 12.2, 9.6, -22), 'cocoa', 'm', band=.24, band_t='deep',
      tex=[_c1_dots([(11.6, 25.6), (14.6, 20.4), (19.4, 17.6), (16.4, 26.4), (21.6, 22.6), (24.6, 18.4), (12.6, 30)])], tex_t='p', tex_w=1.3),
    F(('ellipse', 30.6, 32.2, 10.4, 9.8), 'cocoa', 'm'),
    F(('ellipse', 30.6, 32.2, 8, 7.4), 'melon', 'm', ol=1.4, cut=False, band=.26, band_t='deep',
      tex=['M30.6 32.2L30.6 25.6M30.6 32.2L36.4 28.6M30.6 32.2L37.4 33.6M30.6 32.2L34.4 38M30.6 32.2L27 38M30.6 32.2L23.8 33.6M30.6 32.2L24.8 28.6'], tex_t='p', tex_w=1.2),
    F(('ellipse', 30.6, 32.2, 2.8, 2.4), 'lemon', 'lp', ol=1.2, cut=False),
    LINE(_c1_dots([(30.6 + 4.4 * _c1m.cos(_c1m.radians(a)), 32.2 + 4 * _c1m.sin(_c1m.radians(a))) for a in range(0, 360, 36)]), 'cocoa', 1.6, cut=False),
], qty='each', aisle='produce')

# cauliflower: a creamy white cloud of knobbly florets cupped by two big green leaves
add('cauliflower', 'Cauliflower', [
    F(('path', _c1_bumpy(24, 22.4, 16.4, 13.4, 9, .09, 180)), 'cocoa', 'w', band=.3, band_t='p',
      det=['M14.6 18.4Q16.4 16 18.8 17.2', 'M22 13.4Q24 11.2 26.2 12.6', 'M29.2 18.4Q31.4 16.2 33.6 17.6', 'M18.6 23.6Q20.6 21.4 22.8 22.8', 'M25.8 24Q27.8 21.8 30 23.2',
           'M11 25Q12.4 23.4 14.2 24.2', 'M34.2 25Q35.8 23.4 37.4 24.4'], det_t='m', det_w=1.4),
    F(('path', 'M24 42C14.2 42 6.6 36.4 5.8 26C5.6 22.6 6.8 20.2 8.2 18.6C10.4 25 15.6 30.4 24 32.4Z'), 'melon', 'm', band=.3, band_t='deep',
      det=['M22 40.6Q12.8 36 9.4 24'], det_t='p', det_w=1.4),
    F(('path', 'M24 42C33.8 42 41.4 36.4 42.2 26C42.4 22.6 41.2 20.2 39.8 18.6C37.6 25 32.4 30.4 24 32.4Z'), 'melon', 'm', band=.3, band_t='deep',
      det=['M26 40.6Q35.2 36 38.6 24'], det_t='p', det_w=1.4),
], qty='1 head', aisle='produce')

# cabbage: a round, tightly packed mint-green head with veined outer leaves curling open
_C1_CLEAF = smooth([(24, 41.8), (14, 41.2), (7.4, 35.6), (6, 27.4), (7.8, 20.4), (11.6, 17.6), (13.4, 21), (14.4, 28), (18.4, 33.4), (24.4, 35.4)])
add('cabbage', 'Cabbage', [
    F(('circle', 24, 22.8, 13.2), 'mint', 'p', ol=2, det=['M18.6 10.8C22.8 15 23.8 23 21.6 31.4', 'M29.6 10.6C26 15 25.6 22.4 27.4 30.4', 'M12.6 19.6C16.4 22.6 17.8 27.4 17 32'], det_t='m', det_w=1.6),
    F(('path', _C1_CLEAF), 'mint', 'm', band=.26, band_t='deep', det=['M22.4 40.4Q14 37.4 10.2 24', 'M15 37.6L10 36', 'M12 31.2L8.2 31'], det_t='p', det_w=1.4),
    F(('path', _c1_map(_C1_CLEAF, lambda x, y: (48 - x, y))), 'mint', 'm', band=.26, band_t='deep', det=['M25.6 40.4Q34 37.4 37.8 24', 'M33 37.6L38 36', 'M36 31.2L39.8 31'], det_t='p', det_w=1.4),
], qty='1 head', aisle='produce')

# hot peppers: a slender red chili with a curled tip and a green cap, a smaller green one behind
_C1_CHILI = 'M-3.2 -14.6C1 -14.6 3.4 -11.4 3.6 -6C3.8 1.6 2 10 -2.6 15.4C-4.6 17.6 -7.6 17.4 -8 15.4C-8.2 14.2 -6.8 13.6 -6 12.4C-4 8.6 -4.4 0 -6 -6C-7 -10 -7 -14.6 -3.2 -14.6Z'
add('chili_pepper', 'Hot peppers', [
    F(('path', _c1_xf(_C1_CHILI, .94)), 'melon', 'm', band=.26, tf='translate(18 25) rotate(-22)', det=['M-2 -8.6Q-.8 -1.8 -2.6 5.2'], det_t='p', det_w=1.4),
    F(('path', _c1_xf('M-5.6 -14C-6 -17 -3 -18.8 -.6 -17.6C1.8 -18.6 4.2 -16.8 3.6 -13.6C1.4 -12.4 -3.4 -12.4 -5.6 -14Z', .94)), 'melon', 'deep', ol=1.4, tf='translate(18 25) rotate(-22)'),
    F(('path', _C1_CHILI), 'strawberry', 'm', band=.26, tf='translate(27.6 29.2) rotate(28)', det=['M-2.4 -10Q-1 -2 -3 6'], det_t='p', det_w=1.5),
    TUBE('M-.8 -16.4C-.6 -19.6 .8 -21.6 3.4 -22.4', 'melon', 3, 'm', tf='translate(27.6 29.2) rotate(28)'),
    F(('path', 'M-5.8 -13.6C-6.4 -17 -3.2 -19 -.6 -17.8C2 -19 4.6 -17 4 -13.4C1.6 -12 -3.4 -12 -5.8 -13.6Z'), 'melon', 'm', ol=1.5, tf='translate(27.6 29.2) rotate(28)'),
], qty='bag', aisle='produce')

# zucchini: two long straight dark-green zucchini with pale stripes and flared cut stem ends
_C1_ZUC = 'M-17.4 -4.8H13.6C15.4 -4.8 16.6 -4 17.6 -2.6H20.6Q21.6 -2.6 21.6 -1.6V1.6Q21.6 2.6 20.6 2.6H17.6C16.6 4 15.4 4.8 13.6 4.8H-17.4C-20 4.8 -21.4 2.6 -21.4 0C-21.4 -2.6 -20 -4.8 -17.4 -4.8Z'
_C1_ZTEX = ['M-19 -1.6H14', 'M-17 1.8H12', _c1_dots([(-12, -2.8), (-4, -3), (4, -2.8), (-8, .2), (0, .4), (8, .2)])]
add('zucchini', 'Zucchini', [
    F(('path', _c1_xf(_C1_ZUC, .84)), 'melon', 'deep', band=.28, band_t='tr', tf='translate(25.4 20.1) rotate(-24)', tex=_C1_ZTEX, tex_t='m', tex_w=1.4),
    F(('ellipse', 17.6, 0, 1, 2), 'melon', 'p', ol=1.2, cut=False, tf='translate(25.4 20.1) rotate(-24)'),
    F(('path', _c1_xf(_C1_ZUC, .84)), 'melon', 'deep', band=.28, band_t='tr', tf='translate(22.6 32.1) rotate(-24)', tex=_C1_ZTEX, tex_t='m', tex_w=1.4),
    F(('ellipse', 17.6, 0, 1, 2), 'melon', 'p', ol=1.2, cut=False, tf='translate(22.6 32.1) rotate(-24)'),
], qty='each', aisle='produce')

# butternut squash: a whole beige butternut leaning behind a cut half that shows its orange flesh and seed cavity
_C1_SQ = smooth([(-21, 0), (-19.4, -7.6), (-12.6, -10.6), (-5.6, -8.6), (1, -5.4), (12.4, -5), (19.4, -3.4), (20.6, 0), (19.4, 3.4), (12.4, 5), (1, 5.4), (-5.6, 8.6), (-12.6, 10.6), (-19.4, 7.6)])
_C1_SQIN = smooth([(-18.6, 0), (-17.2, -5.8), (-12.4, -8.2), (-6.4, -6.6), (0, -3.4), (11.6, -3), (17.4, -1.6), (18.2, 0), (17.4, 1.6), (11.6, 3), (0, 3.4), (-6.4, 6.6), (-12.4, 8.2), (-17.2, 5.8)])
_C1_SWF = 'translate(32.2 24.4) rotate(-70)'
_C1_SHF = 'translate(17.2 25.2) rotate(-100)'
add('squash', 'Squash', [
    F(('path', _c1_xf(_C1_SQ, .76)), 'cocoa', 'p', band=.3, band_t='m', tf=_C1_SWF, tex=['M-10.4 -4.4Q-7.6 0 -10.4 4.4', 'M4 -3.4Q6 0 4 3.4'], tex_t='m', tex_w=1.4),
    F(('rect', 14.6, -2, 4.6, 4, 2), 'cocoa', 'm', tf=_C1_SWF),
    F(('path', _c1_xf(_C1_SQ, .8)), 'cocoa', 'p', tf=_C1_SHF),
    F(('path', _c1_xf(_C1_SQIN, .8)), 'peach', 'm', ol=1.4, cut=False, tf=_C1_SHF, band=.3, band_t='deep'),
    F(('ellipse', -9.6, 0, 4.8, 4), 'peach', 'p', ol=1.3, cut=False, tf=_C1_SHF),
    *[F(('ellipse', x, y, 1, .6, a), 'lemon', 'lp', ol=1.2, cut=False, tf=_C1_SHF) for x, y, a in [(-11.8, -1.4, 20), (-8.6, -1.9, -10), (-10.8, 1.6, -20), (-7.4, 1.2, 15)]],
], qty='each', aisle='produce')

# pumpkin: a round ribbed orange pumpkin with a curly green stem and a little leaf
add('pumpkin', 'Pumpkins', [
    F(('ellipse', 14.6, 30.4, 8.6, 11.4), 'peach', 'm', band=.24, band_t='deep'),
    F(('ellipse', 33.4, 30.4, 8.6, 11.4), 'peach', 'm', band=.24, band_t='deep'),
    LINE('M27.2 17.6C29.6 13.8 33.6 14.6 33 17.8C32.6 19.8 30.2 19.4 30.6 17.6', 'melon', 1.6, t='m'),
    F(('path', 'M28.4 15.6C30.4 11 36 9.6 39.6 11.4C37.4 15.8 32.2 17.4 28.4 15.6Z'), 'melon', 'm', ol=1.5, det=['M30.4 14.8Q34.2 13 37.8 12'], det_w=1.2),
    F(('ellipse', 24, 30, 10.4, 12), 'peach', 'm', band=.24, band_t='deep'),
    F(('path', 'M21.6 19.2C21.4 15 22.6 11 25.6 9.4L28 11.4C26.2 13.2 26 16 26.6 19.2Z'), 'melon', 'm', ol=1.6),
], qty='each', aisle='produce')
