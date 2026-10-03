# ---------- art pass, batch 3 (design/stickers/MISSING.md): the centre aisles: baking, condiments, pantry, snacks, frozen ----------
# Executed inside icons.py after the r9 files (shared namespace: every helper here is prefixed _c3 so it never shadows a
# helper another batch relies on). DSL key = catalog key, so catalog_map.py needs no entry. Gloss is generated.
import math as _c3m
from shapely.geometry import LineString as _C3Line, Point as _C3Pt
from shapely.ops import unary_union as _c3_union
from shapely import affinity as _c3_aff


def _c3_poly(g, tol=.04):
    """the largest polygon of a shapely geometry (holes included) as an absolute path."""
    g = g.simplify(tol)
    if g.geom_type != 'Polygon':
        g = max(g.geoms, key=lambda x: x.area)
    rings = [g.exterior] + list(g.interiors)
    return ''.join('M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in list(r.coords)[:-1]) + 'Z' for r in rings)


def _c3_dots(pts):
    return ''.join(f'M{fmt(x)} {fmt(y)}h.1' for x, y in pts)


def _c3_stick(x0, y0, x1, y1, w):
    """a straight rod with round ends from (x0, y0) to (x1, y1), w wide (chopsticks, spoon handles, sprinkles)."""
    return _c3_poly(_C3Line([(x0, y0), (x1, y1)]).buffer(w / 2, quad_segs=6))


def _c3_arc_pts(cx, cy, r, a0, a1, n=24, ry=None):
    ry = r if ry is None else ry
    return [(cx + r * _c3m.cos(_c3m.radians(a0 + (a1 - a0) * i / n)), cy + ry * _c3m.sin(_c3m.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def _c3_arc(cx, cy, r, a0, a1, w):
    """a thick arc with round ends (elbow macaroni, cashews)."""
    return _c3_poly(_C3Line(_c3_arc_pts(cx, cy, r, a0, a1)).buffer(w / 2, quad_segs=6))


def _c3_line(pts):
    return 'M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in pts)


def _c3_tri(cx, cy, R, rot=0, rc=2.2, bulge=0):
    """a triangle (circumradius R, pointing up when rot = 0) with rounded corners; bulge bows each edge outward."""
    v = [(cx + R * _c3m.cos(_c3m.radians(rot - 90 + k * 120)), cy + R * _c3m.sin(_c3m.radians(rot - 90 + k * 120))) for k in range(3)]

    def toward(a, b, d):
        L = _c3m.dist(a, b)
        return (a[0] + (b[0] - a[0]) * d / L, a[1] + (b[1] - a[1]) * d / L)
    s = ''
    for k in range(3):
        p, c, n = v[k - 1], v[k], v[(k + 1) % 3]
        a, b = toward(c, p, rc), toward(c, n, rc)
        if k == 0:
            s += f'M{fmt(a[0])} {fmt(a[1])}'
        else:
            m = ((p[0] + c[0]) / 2, (p[1] + c[1]) / 2)
            o = (m[0] - cx, m[1] - cy)
            L = _c3m.hypot(*o)
            q = (m[0] + o[0] / L * bulge * 2, m[1] + o[1] / L * bulge * 2)
            s += f'Q{fmt(q[0])} {fmt(q[1])} {fmt(a[0])} {fmt(a[1])}'
        s += f'Q{fmt(c[0])} {fmt(c[1])} {fmt(b[0])} {fmt(b[1])}'
    m = ((v[2][0] + v[0][0]) / 2, (v[2][1] + v[0][1]) / 2)
    o = (m[0] - cx, m[1] - cy)
    L = _c3m.hypot(*o)
    q = (m[0] + o[0] / L * bulge * 2, m[1] + o[1] / L * bulge * 2)
    a0 = toward(v[0], v[2], rc)
    return s + f'Q{fmt(q[0])} {fmt(q[1])} {fmt(a0[0])} {fmt(a0[1])}Z'


def _c3_drop(cx, cy, s=1.0):
    """a droplet pointing up, its round end centred on (cx, cy)"""
    return (f'M{fmt(cx)} {fmt(cy - 4.4 * s)}C{fmt(cx + 1.4 * s)} {fmt(cy - 2.4 * s)} {fmt(cx + 2.8 * s)} {fmt(cy - 1.2 * s)} {fmt(cx + 2.8 * s)} {fmt(cy + .6 * s)}'
            f'C{fmt(cx + 2.8 * s)} {fmt(cy + 2.4 * s)} {fmt(cx + 1.6 * s)} {fmt(cy + 3.4 * s)} {fmt(cx)} {fmt(cy + 3.4 * s)}'
            f'C{fmt(cx - 1.6 * s)} {fmt(cy + 3.4 * s)} {fmt(cx - 2.8 * s)} {fmt(cy + 2.4 * s)} {fmt(cx - 2.8 * s)} {fmt(cy + .6 * s)}'
            f'C{fmt(cx - 2.8 * s)} {fmt(cy - 1.2 * s)} {fmt(cx - 1.4 * s)} {fmt(cy - 2.4 * s)} {fmt(cx)} {fmt(cy - 4.4 * s)}Z')


def _c3_snowflake(cx, cy, r, w=2.2):
    lines = []
    for k in range(6):
        a = _c3m.radians(90 + k * 60)
        ux, uy = _c3m.cos(a), -_c3m.sin(a)
        lines.append(_C3Line([(cx, cy), (cx + ux * r, cy + uy * r)]))
        bx, by = cx + ux * r * .58, cy + uy * r * .58
        for side in (-1, 1):
            b = a + side * _c3m.radians(42)
            lines.append(_C3Line([(bx, by), (bx + _c3m.cos(b) * r * .36, by - _c3m.sin(b) * r * .36)]))
    g = _c3_union([ln.buffer(w / 2, cap_style=1, join_style=1, quad_segs=4) for ln in lines] + [_C3Pt(cx, cy).buffer(w * .9)])
    return _c3_poly(g, .04)


def _c3_bumpy(cx, cy, rx, ry, n, amp, a0=0):
    """a closed outline of n round bumps"""
    pts = []
    for i in range(n * 2):
        a = _c3m.radians(a0) + i * _c3m.pi / n
        f = 1 + (amp if i % 2 == 0 else -amp * .4)
        pts.append((cx + rx * f * _c3m.cos(a), cy + ry * f * _c3m.sin(a)))
    return smooth(pts)


def _c3_mound(x0, x1, base, top, n, amp, seed=0):
    """a bumpy heap on a flat base (macaroni, noodles, salsa): bumps along the upper half-ellipse, closed by the base."""
    cx, rx, ry = (x0 + x1) / 2, (x1 - x0) / 2, base - top
    pts = []
    for i in range(n * 2 + 1):
        a = _c3m.radians(180 + 180 * i / (n * 2))
        f = 1 + (amp if i % 2 else -amp * .3)
        pts.append((cx + rx * f * _c3m.cos(a), base + ry * f * _c3m.sin(a)))
    pts[0], pts[-1] = (x0, base), (x1, base)
    return smooth(pts[:-1] + [(x1, base), (cx, base + .6)])


def _c3_kiss(cx, by, w, h, lean=1.2):
    """a chocolate chip: a wide round base, a bell-shaped shoulder, a short curled point"""
    l, r = cx - w / 2, cx + w / 2
    return (f'M{fmt(l)} {fmt(by - 1.6)}C{fmt(l)} {fmt(by - h * .42)} {fmt(cx - w * .36)} {fmt(by - h * .56)} {fmt(cx - w * .2)} {fmt(by - h * .66)}'
            f'C{fmt(cx - w * .08)} {fmt(by - h * .74)} {fmt(cx - w * .04 + lean * .3)} {fmt(by - h * .86)} {fmt(cx + lean)} {fmt(by - h)}'
            f'C{fmt(cx + w * .04 + lean * .3)} {fmt(by - h * .86)} {fmt(cx + w * .08)} {fmt(by - h * .74)} {fmt(cx + w * .2)} {fmt(by - h * .66)}'
            f'C{fmt(cx + w * .36)} {fmt(by - h * .56)} {fmt(r)} {fmt(by - h * .42)} {fmt(r)} {fmt(by - 1.6)}'
            f'Q{fmt(r)} {fmt(by)} {fmt(r - 1.8)} {fmt(by)}H{fmt(l + 1.8)}Q{fmt(l)} {fmt(by)} {fmt(l)} {fmt(by - 1.6)}Z')


# ================================================================ baking

# yeast: three yellow sachets fanned out (crimped seal bands, a little rising loaf printed on each), granules in front
def _c3_yeast(cx, cy, rot):
    tf = f'rotate({rot} {cx} {cy})' if rot else None
    w, h = 12.4, 23
    x = cx
    ticks = ''.join(f'M{fmt(cx - w / 2 + 1.6 + k * (w - 3.2) / 6)} {fmt(y0)}V{fmt(y0 + 2.4)}' for k in range(7) for y0 in (cy - h / 2 + .8, cy + h / 2 - 3.2))
    return [F(('rect', cx - w / 2, cy - h / 2, w, h, 2.2), 'lemon', 'm', band=.16, band_t='deep', tf=tf,
              tex=[f'M0 {fmt(cy - h / 2 + 2)}H48', f'M0 {fmt(cy + h / 2 - 2)}H48'], tex_t='#DAB632', tex_w=3.6, det=[ticks], det_t='tr', det_w=1.2),
            F(('path', f'M{fmt(x - 2.8)} {fmt(cy + 2)}C{fmt(x - 2.8)} {fmt(cy - 2)} {fmt(x + 2.8)} {fmt(cy - 2)} {fmt(x + 2.8)} {fmt(cy + 2)}'
                       f'Q{fmt(x + 2.8)} {fmt(cy + 3.2)} {fmt(x + 1.6)} {fmt(cy + 3.2)}H{fmt(x - 1.6)}Q{fmt(x - 2.8)} {fmt(cy + 3.2)} {fmt(x - 2.8)} {fmt(cy + 2)}Z'),
              'cocoa', 'm', ol=1.2, cut=False, tf=tf, det=[f'M{fmt(x - .9)} {fmt(cy - .4)}l-.7 1.3M{fmt(x + 1.1)} {fmt(cy - .4)}l-.7 1.3'], det_t='p', det_w=1.2)]


add('yeast', 'Yeast', [
    *_c3_yeast(14.8, 27.4, -13),
    *_c3_yeast(33.2, 27.4, 13),
    *_c3_yeast(24, 26.2, 0),
    F(('path', 'M15.8 42C15.8 37.2 19.4 34 24 34C28.6 34 32.2 37.2 32.2 42Z'), 'cocoa', 'p', band=.3,
      tex=[_c3_dots([(18.6, 39), (21.6, 36.6), (25, 37.8), (28.4, 36.8), (29.6, 40.2), (23, 40.6), (26.4, 41)])], tex_t='#CB9C73', tex_w=1.8),
], qty='3 × 8 g', aisle='baking')

# chocolate chips: a periwinkle measuring cup heaped with glossy chocolate chips, one spilt in front
add('chocolate_chips', 'Chocolate chips', [
    TUBE('M34.6 27.8C40.8 27.8 40.8 36.4 33.8 36.6', 'periwinkle', 5, 'p'),
    F(('path', _c3_kiss(19.6, 19.8, 10, 10.4, -1)), 'cocoa', 'deep', band=.26, band_t='tr', tf='rotate(-6 19.6 19.8)'),
    F(('path', _c3_kiss(28.4, 19.6, 10, 10.4, 1.2)), 'cocoa', 'deep', band=.26, band_t='tr', tf='rotate(7 28.4 19.6)'),
    F(('path', _c3_kiss(15.2, 27, 10.2, 10.6, -1)), 'cocoa', 'deep', band=.26, band_t='tr'),
    F(('path', _c3_kiss(24, 26.6, 10.4, 10.8, 1)), 'cocoa', 'p', band=.26, band_t='m'),
    F(('path', _c3_kiss(32.6, 27, 10.2, 10.6, 1.2)), 'cocoa', 'deep', band=.26, band_t='tr'),
    F(('path', 'M11.6 25.6H36.4L34.6 38.8Q34.2 42 31 42H17Q13.8 42 13.4 38.8Z'), 'periwinkle', 'p', band=.26,
      det=['M28.8 30.2H32.8', 'M30 33.6H32.4', 'M28.8 37H32.2'], det_t='m', det_w=1.4),
    F(('rect', 9.8, 23.2, 28.4, 4.6, 2.3), 'periwinkle', 'm'),
    F(('path', _c3_kiss(11.6, 42, 8.6, 9, -1)), 'cocoa', 'deep', band=.26, band_t='tr'),
], qty='300 g bag', aisle='baking')

# cocoa powder: an open red tin heaped with cocoa, its lid leaning behind, a wooden spoon standing in it
add('cocoa_powder', 'Cocoa powder', [
    F(('ellipse', 32.8, 21.6, 8.6, 9.4, 12), 'strawberry', 'm', band=.22),
    F(('ellipse', 33.4, 21.4, 6, 6.8, 12), 'strawberry', 'p', ol=1.4, cut=False),
    F(('path', _c3_stick(19.6, 19.6, 11.4, 8.4, 3.8)), 'cocoa', 'p', ol=1.8, det=['M12.6 8.8l1.2 1.6M13.8 11.4h.1'], det_t='m', det_w=1.6),
    F(cyl(7.6, 31.6, 21.4, 42, 4), 'strawberry', 'm', band=.2),
    F(('rect', 8.5, 26.4, 22.2, 11.4), 'strawberry', 'w', ol=0, cut=False, det=['M8.5 26.4H30.7', 'M8.5 37.8H30.7'], det_w=1.4),
    F(('path', 'M15 29.4H22.8V33.6Q22.8 35.8 20.6 35.8H17.2Q15 35.8 15 33.6Z'), 'cocoa', 'm', ol=1.2, cut=False),
    LINE('M22.8 30.4Q25.4 30.6 25.2 32.4Q25 34 22.8 34', 'cocoa', 1.3, cut=False),
    F(ell_top(7.6, 31.6, 21.4, 4), 'strawberry', 'w'),
    F(('path', 'M9.6 21.8C10.6 18.6 14.6 16.2 19.6 16.2C24.6 16.2 28.6 18.6 29.6 21.8C26.4 24 12.8 24 9.6 21.8Z'), 'cocoa', 'm',
      tex=[_c3_dots([(14.4, 19.8), (18.2, 18.4), (22.4, 19.4), (26, 20.6), (16.6, 21.6), (21, 21.8)])], tex_t='#B1825B', tex_w=1.6),
], qty='227 g tin', aisle='baking')

# sprinkles: a glass shaker jar full of rainbow rods under a pink shaker cap, a few rods spilt in front
_C3_RODS = [(16.2, 26.4, 30, '#FF9FC8'), (21.4, 25.2, -40, '#7CC6F5'), (26.8, 26, 70, '#FFDA4A'), (31.6, 26.8, -10, '#FF9FC8'),
            (14.8, 31.6, -60, '#FFDA4A'), (19.6, 30.8, 10, '#FF9FC8'), (24.6, 31.4, -30, '#7CC6F5'), (29.4, 31.2, 50, '#FF9FC8'), (33.4, 32.4, -50, '#7CC6F5'),
            (16.8, 36.4, 40, '#7CC6F5'), (21.6, 35.8, -70, '#FFDA4A'), (26.6, 36.6, 20, '#FF9FC8'), (31.2, 36.6, -30, '#FFDA4A')]


def _c3_rod(x, y, a, L=3.4):
    dx, dy = L / 2 * _c3m.cos(_c3m.radians(a)), L / 2 * _c3m.sin(_c3m.radians(a))
    return f'M{fmt(x - dx)} {fmt(y - dy)}L{fmt(x + dx)} {fmt(y + dy)}'


add('sprinkles', 'Sprinkles', [
    F(('path', 'M14.6 15.4C14.6 10.6 18.6 8.2 24 8.2C29.4 8.2 33.4 10.6 33.4 15.4Z'), 'sakura', 'm',
      det=[_c3_dots([(19.6, 12.4), (24, 11), (28.4, 12.4), (21.8, 14), (26.2, 14)])], det_t='#FFFFFF', det_w=1.8),
    F(('path', 'M14.6 19.6H33.4Q36.6 19.6 36.6 22.8V38.4Q36.6 42 33 42H15Q11.4 42 11.4 38.4V22.8Q11.4 19.6 14.6 19.6Z'), 'ramune', 'w', band=.2, band_t='p'),
    *[LINE(_c3_rod(x, y, a), 'ramune', 2.2, t=c, cut=False) for x, y, a, c in _C3_RODS],
    F(('rect', 12.6, 14.4, 22.8, 6, 2.6), 'sakura', 'p', det=['M15.4 16V18.8M18.6 16V18.8M21.8 16V18.8M25 16V18.8M28.2 16V18.8M31.4 16V18.8'], det_t='m', det_w=1.2),
    F(('path', _c3_stick(38.4, 41, 41.4, 37.2, 2.6)), 'sakura', 'm', ol=1.4),
    F(('path', _c3_stick(39.6, 32.4, 41.6, 28.6, 2.6)), 'lemon', 'm', ol=1.4),
    F(('path', _c3_stick(7, 38.4, 10.6, 40.4, 2.6)), 'ramune', 'm', ol=1.4),
    F(('path', _c3_stick(7.4, 33.8, 8.6, 29.8, 2.6)), 'sakura', 'm', ol=1.4),
], qty='75 g jar', aisle='baking')

# cake & brownie mix: a purple box with a darker top band, a slice of chocolate layer cake (cream layers, a dollop) on a plate
add('cake_mix', 'Cake & brownie mix', [
    F(('rect', 7.2, 10.4, 33.6, 31.6, 3.6), 'grape', 'p', band=.14, tex=['M4 13.4H44'], tex_t='#B597F2', tex_w=6, det=['M7.8 16.4H40.2']),
    F(('ellipse', 24, 36.4, 14, 3.2), 'grape', 'w', ol=1.4, cut=False),
    F(('path', 'M11.8 35.2V29.4L32.8 21.4Q35.8 20.4 35.8 23.4V34Q35.8 35.2 34.6 35.2H13Q11.8 35.2 11.8 34Z'), 'cocoa', 'm', ol=1.5, cut=False,
      band='M8 33.4H40V38H8Z', band_t='deep', tex=['M8 30.6H40', 'M8 26.4H40', 'M11 30.2L35 21'], tex_t='#FFFFFF', tex_w=1.8),
    F(('path', 'M29.2 22.4C28 20.2 29.4 17.8 31.8 18C32.4 16.2 35.2 16 36 17.8C38 18.2 38.4 20.6 37 22C34.6 23.4 31.4 23.4 29.2 22.4Z'), 'cocoa', 'w', ol=1.3, cut=False),
    F(('circle', 33.4, 17.4, 1.6), 'cocoa', 'tr', ol=0, cut=False),
], qty='432 g box', aisle='baking')

# ================================================================ spices, condiments and oils

# pepper: a wooden pepper mill (round knob, domed cap, waisted body) with three peppercorns beside it
add('black_pepper', 'Pepper', [
    F(('circle', 21, 9.6, 3), 'cocoa', 'm'),
    F(('path', 'M13.4 17.8C13.4 13.8 16.8 11.6 21 11.6C25.2 11.6 28.6 13.8 28.6 17.8Z'), 'cocoa', 'm', det=['M17.6 13.6Q16.4 15.2 16.2 17.2', 'M24.4 13.6Q25.6 15.2 25.8 17.2'], det_t='deep', det_w=1.3),
    F(('path', 'M14.4 19.4H27.6C27.6 23.2 26 25 26 28.2C26 31.6 28.6 33.6 28.6 37.4H13.4C13.4 33.6 16 31.6 16 28.2C16 25 14.4 23.2 14.4 19.4Z'), 'cocoa', 'm', band=.22, band_t='deep',
      det=['M18.2 22.6Q19.2 25.6 18.6 28.6'], det_t='p', det_w=1.4),
    F(('rect', 12.4, 16.4, 17.2, 4.4, 2.2), 'cocoa', 'p'),
    F(('rect', 11.4, 36, 19.2, 6, 2.6), 'cocoa', 'p', band=.34, band_t='m'),
    *[F(('circle', x, y, r), 'cocoa', 'tr', ol=1.6) for x, y, r in [(37.6, 33.8, 2.4), (34.2, 39.2, 2.6), (39.6, 39.4, 2.4)]],
    *[F(('circle', x - .8, y - .8, .7), 'cocoa', 'w', ol=0, cut=False) for x, y in [(37.6, 33.8), (34.2, 39.2), (39.6, 39.4)]],
], qty='70 g mill', aisle='condiments')

# spices: a small glass jar of rust-red ground spice with a cork stopper and a plain label, two cinnamon sticks leaning on it
_C3_CIN = 'rotate(-13 35 28.4)'
add('spice_jar', 'Spices', [
    F(('rect', 12.6, 9, 18.8, 7.8, 2.6), 'cocoa', 'p', tex=[_c3_dots([(15.4, 11.4), (19, 13.6), (22.6, 11.2), (26.4, 13.8), (29, 11.6), (17, 15.4), (24.6, 15.6)])], tex_t='#CB9C73', tex_w=1.6),
    F(('path', 'M14.2 15.8H29.8Q32.8 15.8 32.8 18.8V38.4Q32.8 42 29.2 42H14.8Q11.2 42 11.2 38.4V18.8Q11.2 15.8 14.2 15.8Z'), 'peach', 'w'),
    F(('path', 'M12.2 21.6C15.4 20.4 18.4 22.2 22 21.2C25.4 20.2 28.6 21.8 31.8 20.8V38Q31.8 41 28.8 41H15.2Q12.2 41 12.2 38Z'), 'peach', 'm', ol=0, cut=False,
      band='M10 36H34V44H10Z', band_t='deep', tex=[_c3_dots([(15.2, 25.4), (19.8, 24), (24.4, 25.8), (28.6, 24.4), (17.4, 38.6), (22.6, 39.4), (27.6, 38.4)])], tex_t='#E5885A', tex_w=1.4),
    F(('rect', 11.6, 27.6, 20.8, 7.6), 'peach', 'w', ol=0, cut=False, det=['M11.6 27.6H32.4', 'M11.6 35.2H32.4'], det_w=1.4),
    LINE('M16.6 31.4H27.4', 'peach', 1.6, t='m', cut=False),
    F(('rect', 30.2, 15.6, 5.2, 26, 2.4), 'cocoa', 'm', band=.2, tf=_C3_CIN, det=['M32.4 18V39'], det_t='deep', det_w=1.3),
    F(('ellipse', 32.8, 16.6, 2.6, 1.4), 'cocoa', 'p', ol=1.3, tf=_C3_CIN, det=['M32.2 16.6Q32.8 15.8 33.6 16.6'], det_w=1.2),
    F(('rect', 34.8, 15, 5.2, 26.6, 2.4), 'cocoa', 'm', band=.2, tf=_C3_CIN, det=['M37 17.4V39'], det_t='deep', det_w=1.3),
    F(('ellipse', 37.4, 16, 2.6, 1.4), 'cocoa', 'p', ol=1.3, tf=_C3_CIN, det=['M36.8 16Q37.4 15.2 38.2 16'], det_w=1.2),
], qty='45 g jar', aisle='condiments')

# salad dressing: a tall bottle of creamy dressing flecked with herbs, a green flip cap, a light-green label with a leaf
add('salad_dressing', 'Salad dressing', [
    F(('rect', 19, 6.2, 10, 6.4, 2.4), 'melon', 'm', det=['M19.4 9.4H28.6'], det_t='p', det_w=1.2),
    F(('path', 'M20.4 12.2H27.6V14.6C31.8 16 34.8 18.8 34.8 23.4V38.4Q34.8 42 31.2 42H16.8Q13.2 42 13.2 38.4V23.4C13.2 18.8 16.2 16 20.4 14.6Z'), 'lemon', 'w', band=.2,
      tex=['M16.6 21.4l1.4 .6M20.6 18.8l1.4 -.6M25.6 18.4l1.2 .8M30 21l1.4 -.4M18.6 24l1 -1M28 23.8l1.2 .6M23.2 21.8l1.2 -.6', 'M16.4 38.8l1.4 -.4M22.6 39.6l1.2 .6M28.8 38.6l1.4 -.6'],
      tex_t='#6FBF62', tex_w=1.5),
    F(('rect', 15.8, 25.8, 16.4, 11.4, 3), 'melon', 'p', ol=1.5, cut=False),
    F(('path', 'M19.4 34.6C18.8 30.4 22.4 27.4 28.4 28C28.8 32.4 25.2 35.4 19.4 34.6Z'), 'melon', 'm', ol=1.3, cut=False, det=['M20.8 33.2Q24 30.8 27 29.2'], det_w=1.2),
], qty='475 mL bottle', aisle='condiments')

# canola oil: a clear jug of golden oil (a side handle, a yellow cap, a white label with an oil drop and a leaf)
add('canola_oil', 'Canola & vegetable oil', [
    F(('rect', 15.4, 6.4, 9.6, 6.2, 2), 'lemon', 'm', det=['M17.8 8V11M20.2 8V11M22.6 8V11'], det_w=1.2),
    TUBE('M32.6 19.6C40.6 19.6 40.6 32.6 32.6 32.6', 'lemon', 5.2, 'p'),
    F(('path', 'M17.2 11.8H23.2V14.4C29.8 15 33.6 18 33.6 23V38.4Q33.6 42 30 42H10.4Q6.8 42 6.8 38.4V23C6.8 18 10.6 15 17.2 14.4Z'), 'lemon', 'w'),
    F(('path', 'M7.8 22.6C11.2 21.4 14.6 23.2 18.2 22.2C21.8 21.2 25.4 22.8 28.8 21.8C30.2 21.4 31.6 21.6 32.6 22.2V38Q32.6 41 29.6 41H10.8Q7.8 41 7.8 38Z'), 'lemon', 'm', ol=0, cut=False,
      band='M5 35.4H36V44H5Z', band_t='deep'),
    F(('rect', 11.4, 25.6, 17.6, 11.4, 3), 'lemon', 'w', ol=1.4, cut=False),
    F(('path', _c3_drop(18.2, 31.4, .95)), 'lemon', 'm', ol=1.2, cut=False),
    F(('path', 'M22 34.2C21.4 30.8 23.6 28.4 27.2 28.4C27.6 31.8 25.4 34.2 22 34.2Z'), 'melon', 'm', ol=1.2, cut=False, det=['M22.8 33.4Q24.6 31.2 26.4 29.4'], det_w=1.2),
], qty='3 L jug', aisle='condiments')

# vinegar: a conical glass cruet with a knob stopper and a blank tag on a string, amber vinegar in the lower half
add('vinegar', 'Vinegar', [
    F(('path', 'M22.6 15.2V12.6C21.2 12 20.4 10.8 20.4 9.4C20.4 7.4 22 6 24 6C26 6 27.6 7.4 27.6 9.4C27.6 10.8 26.8 12 25.4 12.6V15.2Z'), 'peach', 'p', ol=1.8),
    F(('path', 'M21.8 16.4H26.2V20.6C26.2 24 35.2 28.6 35.2 35.6V38.4Q35.2 42 31.6 42H16.4Q12.8 42 12.8 38.4V35.6C12.8 28.6 21.8 24 21.8 20.6Z'), 'peach', 'w'),
    F(('path', 'M15.6 30.4C18.4 29.4 21 30.8 24 30C27 29.2 29.6 30.6 32.4 29.8C33.6 31.4 34.2 33.4 34.2 35.6V38.2Q34.2 41 31.4 41H16.6Q13.8 41 13.8 38.2V35.6C13.8 33.6 14.4 31.8 15.6 30.4Z'),
      'peach', 'm', ol=0, cut=False, band='M10 37.4H38V44H10Z', band_t='deep', det=['M16.6 33.6Q17 36.4 19.4 38'], det_t='p', det_w=1.6),
    F(('rect', 20.2, 14.4, 7.6, 3.2, 1.6), 'peach', 'm', ol=1.6),
    LINE('M26.4 19.6Q29.4 19.6 31.4 22', 'peach', 1.2),
    F(('rect', 29.2, 21.2, 7.6, 5.4, 2, 24), 'peach', 'w', ol=1.5, det=['M31.6 22.8h.1'], det_w=1.6),
], qty='500 mL cruet', aisle='condiments')

# salsa: an open jar of chunky red salsa heaped above the rim (onion and green pepper bits), the green lid set aside
_C3_SALSA_BITS = [(13.6, 26.6, 2.6, 2.2, 14, 'w'), (20.6, 29.4, 2.4, 2.4, -10, 'g'), (27.4, 26, 2.6, 2.2, 20, 'w'), (29.6, 33, 2.4, 2.4, 8, 'g'),
                  (15.8, 34.6, 2.4, 2.2, -16, 'g'), (23.2, 36, 2.6, 2.2, 10, 'w'), (14.6, 16.6, 2.6, 2.2, 10, 'g'), (20.4, 13.4, 2.6, 2.2, -14, 'w'),
                  (26.4, 12.6, 2.4, 2.4, 18, 'g'), (31, 16.4, 2.6, 2.2, -8, 'w'), (23.2, 17.2, 2.4, 2.2, 30, 'd')]
add('salsa', 'Salsa', [
    F(('path', _c3_mound(10.4, 33.6, 20.6, 9.6, 5, .14)), 'strawberry', 'm'),
    F(('path', 'M11.6 20.6H32.4Q35.4 20.6 35.4 23.6V38Q35.4 42 31.4 42H12.6Q8.6 42 8.6 38V23.6Q8.6 20.6 11.6 20.6Z'), 'strawberry', 'm', band=.2),
    *[F(('rect', x - w / 2, y - h / 2, w, h, .9, a), 'melon' if c == 'g' else 'strawberry', {'g': 'm', 'w': 'w', 'd': 'deep'}[c], ol=1.2, cut=False) for x, y, w, h, a, c in _C3_SALSA_BITS],
    F(('rect', 9.8, 18.4, 24.4, 4.6, 2.3), 'strawberry', 'w', det=['M13 19.8V21.6M17 19.8V21.6M21 19.8V21.6M25 19.8V21.6M29 19.8V21.6'], det_t='p', det_w=1.2),
    F(cyl(29.8, 42, 36.4, 42, 2), 'melon', 'm', band=.4, band_t='deep', det=['M32 38.4V40.4M35 38.8V41M38 38.8V41M40.4 38.4V40.2'], det_t='p', det_w=1.2),
    F(ell_top(29.8, 42, 36.4, 2), 'melon', 'p'),
], qty='650 mL jar', aisle='condiments')

# ================================================================ pasta, grains, canned and international

# mac & cheese: a blue polka-dot bowl heaped with orange elbow macaroni, a fork standing in it
_C3_ELBOWS = [(13.6, 22.6, 3, 160), (19.6, 18.2, 3, 210), (26.6, 16.4, 3, 120), (33.4, 21.6, 3, 250), (17.6, 24.4, 2.8, 320),
              (24.4, 22, 3, 30), (30.8, 25, 2.8, 190), (11.6, 26.4, 2.6, 280), (36.6, 26.2, 2.6, 300)]
add('mac_and_cheese', 'Mac & cheese', [
    F(('path', _c3_mound(8.6, 39.4, 27.4, 13.6, 5, .08)), 'peach', 'p'),
    F(('path', _c3_stick(30.6, 21.4, 37.8, 7.4, 3.4)), 'ramune', 'p', ol=1.8),
    *[F(('path', _c3_arc(x, y, r, a, a + 190, 3.4)), 'peach', 'm', ol=1.5, det=[_c3_line(_c3_arc_pts(x, y, r, a + 40, a + 150, 8))], det_t='deep', det_w=1.2)
      for x, y, r, a in _C3_ELBOWS],
    F(('path', 'M6.6 26.2H41.4C41.4 34.4 35.2 39.4 28.4 39.4H19.6C12.8 39.4 6.6 34.4 6.6 26.2Z'), 'ramune', 'm', band=.28,
      tex=[_c3_dots([(12, 30), (18, 33.4), (24, 30), (30, 33.4), (36, 30), (15, 36.4), (21, 37.2), (27, 37.2), (33, 36.4)])], tex_t='#FFFFFF', tex_w=2.2),
    F(('rect', 16, 37.6, 16, 4.4, 2.2), 'ramune', 'm'),
    F(('rect', 5.4, 24.2, 37.2, 4.6, 2.3), 'ramune', 'p'),
], qty='225 g box', aisle='pantry')

# noodles & ramen: a white bowl with a red band, wavy noodles, half an egg, red chopsticks across the rim
_C3_WAVE = lambda y: f'M6 {fmt(y)}q2 -2.2 4 0t4 0t4 0t4 0t4 0t4 0t4 0t4 0t4 0'
add('noodles', 'Noodles & ramen', [
    F(('path', _c3_mound(8.8, 39.2, 26, 10.6, 7, .05)), 'lemon', 'm', tex=[_C3_WAVE(13.8), _C3_WAVE(17.4), _C3_WAVE(21), _C3_WAVE(24.6)], tex_t='#DAB632', tex_w=1.6),
    F(('ellipse', 16.8, 17, 6.2, 4.8, 12), 'lemon', 'w', ol=1.6),
    F(('circle', 16.6, 17.4, 2.6), 'lemon', 'm', ol=1.2, cut=False),
    F(('path', 'M6.6 25.4H41.4C41.4 33.8 35.6 39.2 28.8 39.4H19.2C12.4 39.2 6.6 33.8 6.6 25.4Z'), 'strawberry', 'w', band=.24, band_t='p',
      tex=['M4 30.6H44'], tex_t='#FF6F8E', tex_w=3.2),
    F(('rect', 15.6, 37.6, 16.8, 4.4, 2.2), 'strawberry', 'm'),
    F(('rect', 5.4, 23.4, 37.2, 4.4, 2.2), 'strawberry', 'm'),
    F(('path', _c3_stick(23.4, 22.2, 41, 12.2, 2.4)), 'strawberry', 'm', ol=1.5),
    F(('path', _c3_stick(25.8, 24.8, 41.4, 16.6, 2.4)), 'strawberry', 'm', ol=1.5),
], qty='5 × 85 g', aisle='pantry')

# lentils & dried beans: a clear bag of orange-red and green lentils in two layers, a twist tie, lentils scattered in front
add('lentils', 'Lentils & dried beans', [
    F(('path', 'M19.8 14.2C18 11.6 17.6 8.6 18.6 6.8C20.2 7.8 22 8 24 6.8C26 8 27.8 7.8 29.4 6.8C30.4 8.6 30 11.6 28.2 14.2Z'), 'peach', 'w', ol=1.8,
      det=['M21.8 9.6L22.6 13.2', 'M26.2 9.6L25.4 13.2'], det_t='p', det_w=1.2),
    F(('path', 'M19.6 15.6C13.6 19.2 9.4 26.4 9.4 33C9.4 38.6 12.6 42 18 42H30C35.4 42 38.6 38.6 38.6 33C38.6 26.4 34.4 19.2 28.4 15.6Z'), 'peach', 'w', det=['M20.4 18.6Q19.4 21 19.6 22.6', 'M27.6 18.6Q28.6 21 28.4 22.6'], det_t='p', det_w=1.2),
    F(('path', 'M13.2 24.2C16.8 22.8 20.4 24.6 24 23.6C27.6 22.6 31.2 24 34.8 23.2C36.2 25.4 37.2 28 37.6 30.8C33.2 32 28.6 30.2 24 31.4C19.4 32.6 14.8 30.8 10.4 32.2C10.8 29.2 11.8 26.6 13.2 24.2Z'),
      'melon', 'm', ol=1.4, cut=False, tex=[_c3_dots([(15.4, 27.4), (19.4, 26.2), (23.4, 27.6), (27.6, 26.4), (31.6, 27.8), (34.8, 26.6), (17.4, 29.6), (29.6, 29.4)])], tex_t='#D3F4C5', tex_w=1.8),
    F(('path', 'M10.4 32.2C14.8 30.8 19.4 32.6 24 31.4C28.6 30.2 33.2 32 37.6 30.8C37.6 31.6 37.6 32.4 37.6 33C37.6 38 34.6 41 30 41H18C13.4 41 10.4 38 10.4 33Z'),
      'peach', 'm', ol=1.4, cut=False, band='M8 37.4H40V44H8Z', band_t='deep',
      tex=[_c3_dots([(14, 34.6), (18.4, 35.4), (22.8, 34.4), (27.2, 35.4), (31.6, 34.4), (35.2, 35.2), (16.2, 38.2), (25, 38.4), (33, 38)])], tex_t='#FFDCC6', tex_w=1.8),
    F(('rect', 18, 12.6, 12, 4, 2), 'melon', 'deep', ol=1.6),
    LINE('M29.6 13.6L33.4 10.6', 'melon', 1.6, t='deep'),
    F(('ellipse', 7.8, 40.2, 2, 1.4, -20), 'peach', 'm', ol=1.3),
    F(('ellipse', 40.2, 40.4, 2, 1.4, 15), 'melon', 'm', ol=1.3),
    F(('ellipse', 41, 36.4, 1.8, 1.3, -30), 'peach', 'm', ol=1.3),
], qty='900 g bag', aisle='pantry')

# broth & stock: a pale-yellow carton with a golden band (a bay leaf), a screw cap, two bouillon cubes stacked beside it
add('broth', 'Broth & stock', [
    F(('rect', 21.4, 6.2, 7.2, 5.8, 2), 'peach', 'm', det=['M23.4 7.8V10.8M25 7.8V10.8M26.6 7.8V10.8'], det_w=1.2),
    F(('path', 'M9.4 14.2Q9.4 11.2 12.4 11.2H28.6Q31.6 11.2 31.6 14.2V39Q31.6 42 28.6 42H12.4Q9.4 42 9.4 39Z'), 'lemon', 'p', band=.16, det=['M10 15.6H31']),
    F(('rect', 10.3, 21.2, 20.4, 12.8), 'lemon', 'm', ol=0, cut=False, det=['M10.3 21.2H30.7', 'M10.3 34H30.7'], det_w=1.4),
    F(('path', 'M14 31.6C14.8 26.6 20.2 23.6 26.8 24.2C26 29.2 20.6 32.4 14 31.6Z'), 'lemon', 'w', ol=1.4, cut=False,
      det=['M15.8 30.4Q20.6 28 25 25.4'], det_t='m', det_w=1.2),
    F(('rect', 29.4, 33.2, 9.4, 8.8, 2.2), 'peach', 'm', band=.26, det=['M31.6 35.2H36.6'], det_t='p', det_w=1.3),
    F(('rect', 32, 24.8, 8.6, 8.2, 2.2), 'peach', 'm', band=.26, tf='rotate(12 36.3 28.9)', det=['M34.2 26.8H38.4'], det_t='p', det_w=1.3),
], qty='900 mL carton', aisle='pantry')

# pickles: a Mason jar of brine packed with bumpy dill pickles, a dill sprig and a garlic clove, a gold lid
_C3_PICKLE_DOTS = lambda x, y: _c3_dots([(x - 1.2, y - 5), (x + 1, y - 2.4), (x - 1, y + .4), (x + 1.2, y + 3.2), (x - .8, y + 6)])
add('pickles', 'Pickles', [
    F(('rect', 13, 8.4, 22, 7.8, 2.6), 'lemon', 'm', det=['M16 10.4V14.2M19.4 10.4V14.2M22.8 10.4V14.2M26.2 10.4V14.2M29.6 10.4V14.2M32.4 10.4V14.2'], det_w=1.2),
    F(('path', 'M15.6 15.6H32.4V18.4C36.4 19.4 38.6 22.2 38.6 26V38Q38.6 42 34.6 42H13.4Q9.4 42 9.4 38V26C9.4 22.2 11.6 19.4 15.6 18.4Z'), 'melon', 'lp', band=.16, band_t='p',
      det=['M15.8 16.8H32.2'], det_t='p', det_w=1.2),
    *[F(('ellipse', x, y, 3.8, 9, a), 'melon', 'm', ol=1.4, cut=False, band=.22, band_t='deep', tex=[_C3_PICKLE_DOTS(x, y)], tex_t='#D3F4C5', tex_w=1.6)
      for x, y, a in [(16.2, 30.4, -8), (24, 29.2, 3), (31.8, 30.6, 9)]],
    LINE('M20.2 23.6L19 21.4M19 21.4l-1.6 -.4M19 21.4l.2 -1.6M27.8 23.4L29 21.2M29 21.2l1.6 -.4M29 21.2l-.2 -1.6', 'melon', 1.2, t='tr', cut=False),
    F(('path', 'M20.6 41C19 41 18.4 39.4 18.8 38C19.2 36.6 20.4 35.6 21.6 35.4C22.4 36.6 22.8 38 22.6 39.2C22.4 40.4 21.6 41 20.6 41Z'), 'lemon', 'w', ol=1.2, cut=False),
], qty='1 L jar', aisle='pantry')

# olives: a little dish of green and black olives, the front green one stuffed with a red pimento
add('olives', 'Olives', [
    F(('ellipse', 14.2, 23, 6, 5, -20), 'grape', 'tr', ol=1.6),
    F(('ellipse', 33.8, 22.8, 6, 5, 24), 'grape', 'tr', ol=1.6),
    F(('ellipse', 24, 18.6, 6.2, 5.2, 8), 'melon', 'm', band=.26, band_t='deep'),
    F(('ellipse', 30.4, 27, 6.2, 5.2, -12), 'melon', 'm', band=.26, band_t='deep'),
    F(('circle', 18.2, 27, 5.6), 'melon', 'm', band=.24, band_t='deep'),
    F(('circle', 18.2, 27, 2.4), 'strawberry', 'm', ol=1.3, cut=False),
    *[F(('circle', x, y, .9), 'grape', 'w', ol=0, cut=False) for x, y in [(12.2, 21), (32.4, 20.4)]],
    F(('path', 'M6.4 32.6H41.6C41.6 38.4 34.4 42 24 42C13.6 42 6.4 38.4 6.4 32.6Z'), 'grape', 'w', band=.3, band_t='p'),
    F(('rect', 5.4, 30.4, 37.2, 4.4, 2.2), 'grape', 'p'),
], qty='375 mL jar', aisle='pantry')

# coconut milk: a mint tin with a white label (a milk drop), a halved hairy coconut in front showing its white flesh
_C3_HAIR = ''.join(f'M{fmt(x)} {fmt(y)}l{fmt(dx)} {fmt(dy)}' for x, y, dx, dy in
                   [(24.6, 34.6, -.6, 1.4), (26.4, 38, -.2, 1.4), (29.4, 40.2, .2, 1.2), (33, 41, .6, 1), (36.6, 39.8, .8, .8), (39.4, 37.2, 1, .6), (40.6, 33.6, 1.2, .2)])
add('coconut_milk', 'Coconut milk', [
    F(cyl(6.6, 28.6, 13.6, 42, 3.6), 'mint', 'p', band=.16),
    F(('rect', 7.5, 19.8, 20.2, 14.6), 'mint', 'w', ol=0, cut=False, det=['M7.5 19.8H27.7', 'M7.5 34.4H27.7'], det_w=1.4),
    F(('path', _c3_drop(17.6, 27.6, 1.1)), 'mint', 'm', ol=1.3, cut=False),
    F(ell_top(6.6, 28.6, 13.6, 3.6), 'mint', 'w'),
    F(('ellipse', 17.6, 13.6, 8, 2.2), 'mint', 'p', ol=1.3, cut=False),
    F(('path', 'M22.4 31.8C22.4 37.8 26.6 42 32 42C37.4 42 41.6 37.8 41.6 31.8Z'), 'cocoa', 'm', band=.3, band_t='deep', tex=[_C3_HAIR], tex_t='#B1825B', tex_w=1.4),
    F(('ellipse', 32, 31.4, 9.8, 4.6), 'cocoa', 'w', ol=2),
    F(('ellipse', 32, 31.8, 6.4, 2.6), 'cocoa', 'lp', ol=1.3, cut=False),
], qty='400 mL can', aisle='pantry')

# curry & simmer sauces: an orange jar with a yellow lid and a flame label, a wooden spoon of sauce leaning on it, dripping
add('curry_sauce', 'Curry & simmer sauces', [
    F(('path', _c3_stick(13.4, 19.4, 16.6, 39.4, 3.6)), 'lemon', 'p', ol=1.8),
    F(('ellipse', 12.8, 13.4, 5.6, 6.8, -8), 'lemon', 'p', band=.26, band_t='m'),
    F(('ellipse', 12.8, 12.4, 3.8, 4.4, -8), 'peach', 'm', ol=1.3, cut=False),
    F(('path', 'M8.4 16C9.6 17.6 10.2 20 10.2 22.2C10.2 24.2 9.2 25.4 8 25.4C6.8 25.4 6 24.2 6.4 22.4C6.8 20.2 7.4 17.8 8.4 16Z'), 'peach', 'm', ol=1.6),
    F(('path', 'M17.4 20.4H37.4Q40.4 20.4 40.4 23.4V38Q40.4 42 36.4 42H18.4Q14.4 42 14.4 38V23.4Q14.4 20.4 17.4 20.4Z'), 'peach', 'm', band=.2),
    F(('rect', 18.4, 25.6, 18, 11.4, 3), 'peach', 'w', ol=1.5, cut=False),
    F(('path', 'M27.4 26.4C30.2 28.8 31.4 30.6 31.4 32.6C31.4 34.8 29.6 36.2 27.4 36.2C25.2 36.2 23.4 34.8 23.4 32.6C23.4 31.2 24.2 30 25.2 29.2C25.2 30.4 25.6 31.4 26.4 31.6C26 29.8 26.4 27.8 27.4 26.4Z'), 'peach', 'deep', ol=1.2, cut=False),
    F(('path', _c3_drop(27.4, 33.4, .52)), 'lemon', 'm', ol=1.2, cut=False),
    F(('rect', 12.8, 13.8, 29.2, 7.6, 2.6), 'lemon', 'm', det=['M16.6 15.8V19.6M20.6 15.8V19.6M24.6 15.8V19.6M28.6 15.8V19.6M32.6 15.8V19.6M36.6 15.8V19.6'], det_w=1.2),
], qty='350 mL jar', aisle='pantry')

# ================================================================ snacks and candy

# tortilla chips: a heap of golden triangles with toasty spots, each turned so its gloss is a straight glint (never a tick)
_C3_CHIPS = [(24.4, 15.4, 8.4, 15), (14.6, 22.6, 8.6, 5), (33.4, 22.4, 8.6, 25), (12.8, 33.8, 8.6, 20), (35.2, 34, 8.6, 10), (24, 33.4, 12.4, 15)]
add('tortilla_chips', 'Tortilla chips', [
    *[F(('path', _c3_tri(x, y, R, a, 2, .4)), 'lemon', 'm', band=.24, band_t='deep', sband=False,
        tex=[_c3_dots([(x - R * .26, y + R * .16), (x + R * .2, y - R * .12)])], tex_t='#DAB632', tex_w=1.8) for x, y, R, a in _C3_CHIPS],
], qty='300 g bag', aisle='snacks')

# candy: a striped hard candy in a clear twist wrapper, a little gummy bear in front (no face: list sticker)
def _c3_bear(cx, by):
    g = _c3_union([_C3Pt(cx, by - 15.6).buffer(4.6), _C3Pt(cx - 3.6, by - 19.2).buffer(2.1), _C3Pt(cx + 3.6, by - 19.2).buffer(2.1),
                   _c3_aff.scale(_C3Pt(cx, by - 7.6).buffer(1), 5.6, 6.6), _c3_aff.rotate(_c3_aff.scale(_C3Pt(cx - 5.4, by - 11.2).buffer(1), 2.2, 3.2), 35),
                   _c3_aff.rotate(_c3_aff.scale(_C3Pt(cx + 5.4, by - 11.2).buffer(1), 2.2, 3.2), -35), _C3Pt(cx - 3.4, by - 2.4).buffer(2.4),
                   _C3Pt(cx + 3.4, by - 2.4).buffer(2.4)])
    return _c3_poly(g.buffer(.4).buffer(-.4), .05)


def _c3_twist(cx, cy, r, side, L=5.2, h=4.8):
    """one twisted end of a candy wrapper: pinched at the candy, fanning out to a pleated edge"""
    x0, x1 = cx + side * (r - 1.6), cx + side * (r + L)
    return (f'M{fmt(x0)} {fmt(cy)}L{fmt(x1)} {fmt(cy - h)}Q{fmt(x1 + side * .8)} {fmt(cy - h - .6)} {fmt(x1 + side * .8)} {fmt(cy - h + .8)}'
            f'L{fmt(x1 + side * .8)} {fmt(cy + h - .8)}Q{fmt(x1 + side * .8)} {fmt(cy + h + .6)} {fmt(x1)} {fmt(cy + h)}Z')


_C3_CX, _C3_CY, _C3_CR = 20.4, 18.4, 8.2
_C3_CTF = f'rotate(26 {_C3_CX} {_C3_CY})'
_C3_PLEAT = lambda side: [f'M{fmt(_C3_CX + side * (_C3_CR + 1.4))} {fmt(_C3_CY - 1.8)}V{fmt(_C3_CY + 1.8)}',
                          f'M{fmt(_C3_CX + side * (_C3_CR + 3.4))} {fmt(_C3_CY - 3)}V{fmt(_C3_CY + 3)}']
add('candy', 'Candy', [
    F(('path', _c3_twist(_C3_CX, _C3_CY, _C3_CR, -1, 4.6)), 'sakura', 'p', tf=_C3_CTF, det=_C3_PLEAT(-1), det_t='m', det_w=1.2),
    F(('path', _c3_twist(_C3_CX, _C3_CY, _C3_CR, 1, 4.6)), 'sakura', 'p', tf=_C3_CTF, det=_C3_PLEAT(1), det_t='m', det_w=1.2),
    F(('circle', _C3_CX, _C3_CY, _C3_CR), 'sakura', 'w', tf=_C3_CTF,
      tex=[''.join(f'M{fmt(_C3_CX - 12 + k * 6.4)} {fmt(_C3_CY + 10)}L{fmt(_C3_CX + k * 6.4)} {fmt(_C3_CY - 10)}' for k in range(4))], tex_t='#FF9FC8', tex_w=3),
    F(('path', _c3_bear(32.6, 42)), 'ramune', 'm', band=.22, band_t='deep'),
    F(('ellipse', 32.6, 34.6, 2.8, 3.4), 'ramune', 'p', ol=1.2, cut=False),
], qty='200 g bag', aisle='snacks')

# nuts & trail mix: a walnut half and a peanut in its shell behind, two almonds and a cashew in front
_C3_ALMOND = 'M0 -6.6C3.4 -4.6 4.4 -.6 4.2 2.4C3.8 5.4 2 6.8 0 6.8C-2 6.8 -3.8 5.4 -4.2 2.4C-4.4 -.6 -3.4 -4.6 0 -6.6Z'


def _c3_peanut(x0, y0, x1, y1, r0, r1):
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    g = _c3_union([_C3Pt(x0, y0).buffer(r0), _C3Pt(x1, y1).buffer(r1), _C3Pt(mx, my).buffer(min(r0, r1) * .78)])
    return _c3_poly(g.buffer(1.4).buffer(-1.4), .05)


add('nuts', 'Nuts & trail mix', [
    F(('path', _c3_peanut(11.8, 22.6, 19.6, 17.4, 4.8, 4.4)), 'cocoa', 'p', band=.2, band_t='m',
      tex=['M8 18L20 30M10 14L24 28M15 11L25 21M6 24L14 32', 'M8 26L18 14M10 30L22 16M14 32L25 20'], tex_t='#CB9C73', tex_w=1.2),
    F(('path', _c3_bumpy(31, 21.4, 8.4, 7.2, 7, .07, 0)), 'cocoa', 'm', band=.24, band_t='deep',
      det=['M31 14.8V27.8', 'M27 17.6Q25 20.4 27.4 22.6Q25 24.6 27 26.6', 'M35 17.6Q37 20.4 34.6 22.6Q37 24.6 35 26.6'], det_t='deep', det_w=1.3),
    F(('path', _C3_ALMOND), 'cocoa', 'm', band=.26, band_t='deep', tf='translate(13 34.6) rotate(-24)', det=['M-1.4 -3.4Q-2 0 -1.2 3.6', 'M1.6 -2.6Q2 .4 1.4 3.4'], det_t='deep', det_w=1.2),
    F(('path', _c3_arc(33.6, 33.6, 4.4, 160, 380, 5.6)), 'cocoa', 'lp', band=.26, band_t='p', det=[_c3_line(_c3_arc_pts(33.6, 33.6, 4.4, 200, 330, 8))], det_t='p', det_w=1.3),
    F(('path', _C3_ALMOND), 'cocoa', 'm', band=.26, band_t='deep', tf='translate(23.4 35) rotate(14)', det=['M-1.4 -3.4Q-2 0 -1.2 3.6', 'M1.6 -2.6Q2 .4 1.4 3.4'], det_t='deep', det_w=1.2),
], qty='500 g bag', aisle='snacks')

# dried fruit: wrinkly purple raisins heaped behind two flat, wrinkled dried apricot halves (the big one carries the gloss)
_C3_RAISINS = [(20.6, 14.6, 0), (14.4, 18.8, 1), (26.6, 17.6, 2), (32.6, 21.4, 3), (9.8, 24.6, 4), (20, 21.8, 5), (36.6, 27.2, 6), (9.6, 31.4, 7)]
_C3_WRINKLE = lambda x, y: [f'M{fmt(x - 2.2)} {fmt(y - .4)}Q{fmt(x - .8)} {fmt(y - 1.8)} {fmt(x + .6)} {fmt(y - .8)}', f'M{fmt(x - .6)} {fmt(y + 1.2)}Q{fmt(x + 1)} {fmt(y + .2)} {fmt(x + 2.2)} {fmt(y + .8)}']
add('dried_fruit', 'Dried fruit', [
    *[F(('path', blob(x, y, 4.2, 3.4, 7, .16, s + 3)), 'grape', 'deep', band=.24, band_t='tr', det=_C3_WRINKLE(x, y), det_t='tr', det_w=1.3) for x, y, s in _C3_RAISINS],
    F(('path', blob(14.6, 36.4, 8, 5.4, 10, .09, 2)), 'peach', 'm', band=.28, band_t='deep',
      det=['M9.6 35.2Q11.4 33.4 13.4 34.6', 'M15.4 33.4Q17.8 33 19.6 34.8', 'M11.2 38.6Q13.6 39.8 16 38.8', 'M17.6 37.4Q19.6 37.8 20.8 36.8'], det_t='deep', det_w=1.3),
    F(('path', blob(30, 33.4, 11, 8.2, 12, .07, 5)), 'peach', 'm', band=.26, band_t='deep',
      det=['M22.6 30.6Q24.8 28 27.6 29', 'M30.4 27.4Q33.8 26.4 36.4 28.4', 'M25 35.2Q28 33.2 31 34.6', 'M33.2 32.4Q36.4 32 38.6 34.2', 'M27.4 38.4Q30.4 39.6 33.6 38.6'],
      det_t='deep', det_w=1.4),
], qty='400 g bag', aisle='snacks')

# marshmallows: two puffy marshmallows on the ground, a third toasted golden on a roasting stick
_C3_MTF = 'translate(16.4 14.6) rotate(32)'
add('marshmallows', 'Marshmallows', [
    F(('path', _c3_stick(-10.4, 0, 29, 0, 2.4)), 'cocoa', 'p', ol=1.6, tf=_C3_MTF),
    F(('rect', -6.6, -5.8, 13.2, 11.6, 4), 'cocoa', 'm', tf=_C3_MTF, band=.3, band_t='deep',
      det=['M-1.4 -2.6h.1M2.8 -3.4h.1M1.2 .6h.1'], det_t='tr', det_w=1.6),
    F(('ellipse', -5, 0, 2.2, 4.6), 'cocoa', 'p', ol=1.3, cut=False, tf=_C3_MTF),
    F(('rect', 7, 28.8, 13.4, 13.2, 4.4), 'sakura', 'w', band=.24, band_t='p'),
    F(('ellipse', 13.7, 31.6, 4.8, 1.6), 'sakura', 'p', ol=1.2, cut=False),
    F(('rect', 19.4, 30.4, 13.4, 11.6, 4.4), 'sakura', 'p', band=.24, band_t='m'),
    F(('ellipse', 26.1, 33.2, 4.8, 1.5), 'sakura', 'w', ol=1.2, cut=False),
], qty='400 g bag', aisle='snacks')

# ================================================================ frozen

# frozen fruit: a frosty clear bag of red and blue berries with ice sparkles, a sealed crimped top, a blue snowflake
_C3_BERRIES = [(15.4, 24.6, 'r'), (22.4, 27.8, 'b'), (29, 24.8, 'r'), (13.4, 31.2, 'b'), (19.8, 33.8, 'r'), (27, 33.6, 'b'), (33.6, 31.2, 'r'), (16.2, 38.4, 'b'), (23.8, 38.8, 'r'), (31.4, 38.4, 'b')]
add('frozen_berries', 'Frozen fruit', [
    F(('path', 'M11.6 12.6H36.4C39.8 18.6 41.2 27 40.4 34.6Q39.8 41.6 34.6 41.8H13.4Q8.2 41.6 7.6 34.6C6.8 27 8.2 18.6 11.6 12.6Z'), 'ramune', 'w', band=.14, band_t='p'),
    *[F(('circle', x, y, 3.4), 'strawberry', 'm', ol=1.3, cut=False, tex=[_c3_dots([(x - 1.2, y - 1), (x + 1.2, y - .8), (x, y + 1.2)])], tex_t='#FFC4D1', tex_w=1.2)
      if c == 'r' else F(('circle', x, y, 3), 'ramune', 'm', ol=1.3, cut=False, det=[f'M{fmt(x - .8)} {fmt(y - 1.6)}l.8 .8l.8 -.8'], det_w=1.2)
      for x, y, c in _C3_BERRIES],
    LINE(_c3_dots([(18.6, 23), (25.4, 31), (31.6, 28.2), (11.6, 35.6), (35.4, 35.6), (20.4, 41), (27.6, 41.2), (24.8, 22.4)]), 'ramune', 1.6, t='#FFFFFF', cut=False),
    F(('path', _c3_snowflake(31.6, 19, 4.4, 1.8)), 'ramune', 'm', ol=1.2, cut=False),
    F(('path', 'M10.8 9.2L12.4 8.2L14 9.2L15.6 8.2L17.2 9.2L18.8 8.2L20.4 9.2L22 8.2L23.6 9.2L25.2 8.2L26.8 9.2L28.4 8.2L30 9.2L31.6 8.2L33.2 9.2L34.8 8.2L36.4 9.2L37.6 8.6V13.4H10.4V8.6Z'), 'ramune', 'w',
      det=['M12.6 10.4V12.2M15.6 10.4V12.2M18.6 10.4V12.2M21.6 10.4V12.2M24.6 10.4V12.2M27.6 10.4V12.2M30.6 10.4V12.2M33.6 10.4V12.2M36.2 10.4V12.2'], det_t='m', det_w=1.2),
], qty='600 g bag', aisle='frozen')

# perogies: three plump half-moon perogies with crimped edges and pan-fried spots, sprinkled with green onion
def _c3_perogy(cx, by, rx, ry, n=7):
    pts = []
    for i in range(n * 2 + 1):
        a = _c3m.radians(180 + 180 * i / (n * 2))
        f = 1 + (.08 if i % 2 else -.02)
        pts.append((cx + rx * f * _c3m.cos(a), by + ry * f * _c3m.sin(a)))
    pts[0], pts[-1] = (cx - rx, by - 1.2), (cx + rx, by - 1.2)
    return smooth(pts + [(cx + rx * .7, by), (cx - rx * .7, by)])


def _c3_crimp(cx, by, rx, ry, n=9):
    out = ''
    for i in range(1, n):
        a = _c3m.radians(180 + 180 * i / n)
        c, s = _c3m.cos(a), _c3m.sin(a)
        out += f'M{fmt(cx + (rx - 3) * c)} {fmt(by + (ry - 3) * s)}L{fmt(cx + (rx - 1.2) * c)} {fmt(by + (ry - 1.2) * s)}'
    return out


_C3_BROWN = lambda pts: [_c3_dots(pts)]
add('perogies', 'Perogies & dumplings', [
    F(('path', _c3_perogy(24, 33.4, 10, 9.6)), 'cocoa', 'p', band=.22, band_t='m', det=[_c3_crimp(24, 33.4, 10, 9.6)], det_t='m', det_w=1.3,
      tex=_C3_BROWN([(20.4, 28.4), (27.4, 27.4)]), tex_t='#CB9C73', tex_w=3.4),
    F(('path', _c3_perogy(15.2, 42, 8.4, 9.4)), 'cocoa', 'p', band=.24, band_t='m', det=[_c3_crimp(15.2, 42, 8.4, 9.4)], det_t='m', det_w=1.3,
      tex=_C3_BROWN([(12.6, 37.6), (17.6, 36.4)]), tex_t='#CB9C73', tex_w=3.4),
    F(('path', _c3_perogy(32.8, 42, 8.4, 9.4)), 'cocoa', 'p', band=.24, band_t='m', det=[_c3_crimp(32.8, 42, 8.4, 9.4)], det_t='m', det_w=1.3,
      tex=_C3_BROWN([(30.2, 36.4), (35.2, 37.8)]), tex_t='#CB9C73', tex_w=3.4),
    *[F(('rect', x - 1.6, y - .9, 3.2, 1.8, .9, a), 'melon', 'm', ol=1.2, cut=False) for x, y, a in [(22.2, 26.4, -20), (27.6, 29.6, 15), (13.4, 35, 25), (18.2, 39, -10), (31.4, 39.2, 20), (35.6, 35.6, -25)]],
], qty='907 g bag', aisle='frozen')

# nuggets & fish sticks: three golden breaded nuggets beside a little white cup of ketchup
_C3_CRUMBS = lambda x, y: [_c3_dots([(x - 3.6, y - 1.6), (x - .6, y - 3), (x + 2.6, y - 1.4), (x - 2, y + 1.6), (x + 1.4, y + 1.4), (x + 4, y + .6)])]
add('chicken_nuggets', 'Nuggets & fish sticks', [
    F(('path', blob(16.4, 21.4, 9, 7, 7, .14, 4, 10)), 'peach', 'm', band=.26, band_t='deep', tex=_C3_CRUMBS(16.4, 21.4), tex_t='#FFDCC6', tex_w=1.7),
    F(('path', blob(30.4, 20.4, 8.4, 6.6, 7, .14, 9, -14)), 'peach', 'm', band=.26, band_t='deep', tex=_C3_CRUMBS(30.4, 20.4), tex_t='#FFDCC6', tex_w=1.7),
    F(('path', blob(16.6, 33.8, 9.6, 7.4, 7, .13, 2, -6)), 'peach', 'm', band=.26, band_t='deep', tex=_C3_CRUMBS(16.6, 33.8), tex_t='#FFDCC6', tex_w=1.7),
    F(('path', 'M25.6 30H41L39.6 39.6Q39.2 42 36.8 42H29.8Q27.4 42 27 39.6Z'), 'strawberry', 'w', band=.3, band_t='p', det=['M28.6 34.6V39', 'M33.3 34.6V39.6', 'M38 34.6V39'], det_t='p', det_w=1.2),
    F(('ellipse', 33.3, 30, 7.7, 2.6), 'strawberry', 'm', ol=1.8),
], qty='800 g box', aisle='frozen')

# bag of ice: a clear sack knotted at the top, ice cubes inside, a blue snowflake printed on it, drips of melt
_C3_CUBES = [(16.6, 23.4, 14), (24.4, 20.6, -8), (31.6, 24, 20), (13.6, 31.4, -16), (34.6, 31.8, 10), (17.4, 37.6, 6), (30.8, 37.8, -12)]
add('ice_bag', 'Bag of ice', [
    F(('path', 'M23.4 13.6C20.6 11.6 17.6 8.8 17.2 7.2Q17 6 18.2 6.4C20.6 7.4 22.8 9.6 24 11.6Z'), 'ramune', 'w', ol=1.8, det=['M19.8 8.6L22.4 11.2'], det_t='p', det_w=1.2),
    F(('path', 'M24.6 13.6C27.4 11.6 30.4 8.8 30.8 7.2Q31 6 29.8 6.4C27.4 7.4 25.2 9.6 24 11.6Z'), 'ramune', 'w', ol=1.8, det=['M28.2 8.6L25.6 11.2'], det_t='p', det_w=1.2),
    F(('path', 'M21.6 15.2C14.4 18.4 9.4 25 9.4 31.8C9.4 38 13 42 18.6 42H29.4C35 42 38.6 38 38.6 31.8C38.6 25 33.6 18.4 26.4 15.2Z'), 'ramune', 'w', band=.18, band_t='p',
      det=['M21.8 17.6Q20.6 19.6 20.8 21.4', 'M26.2 17.6Q27.4 19.6 27.2 21.4'], det_t='p', det_w=1.2),
    *[F(('rect', x - 3, y - 3, 6, 6, 1.8, a), 'ramune', 'p', ol=1.3, cut=False) for x, y, a in _C3_CUBES],
    F(('path', _c3_snowflake(24, 31, 6, 2.2)), 'ramune', 'm', ol=1.2, cut=False),
    F(('ellipse', 24, 13.8, 3.2, 2.4), 'ramune', 'w', ol=1.8),
    F(('path', _c3_drop(7.6, 37.6, .72)), 'ramune', 'p', ol=1.4),
    F(('path', _c3_drop(40.4, 38.4, .72)), 'ramune', 'p', ol=1.4),
], qty='2 kg bag', aisle='frozen')
