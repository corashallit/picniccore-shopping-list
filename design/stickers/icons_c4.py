# ---------- art pass, batch 4 (design/stickers/MISSING.md): household, health, baby, pet, garden and flowers, picnic
# extras and the drinks ----------
# Executed inside icons.py after the r9 files (shared namespace: every helper here is prefixed _c4 so it never shadows
# an r9 helper or another batch's). DSL key = catalog key, so catalog_map.py needs no entry. Gloss is generated.
import math as _c4m
from shapely.geometry import Point as _C4Pt, Polygon as _C4Poly
from shapely.ops import unary_union as _c4_union


def _c4_map(d, f):
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
            raise ValueError('_c4_map: unsupported command ' + cmd)
        out.append(cmd + ' '.join('%s %s' % tuple(fmt(v) for v in f(x, y)) for x, y in pts))
    for tk in NUM.findall(d):
        if tk.isalpha():
            flush(); st['cmd'] = tk; st['nums'] = []
        else:
            st['nums'].append(float(tk))
    flush()
    return ''.join(out)


def _c4_xf(d, s=1.0, ox=0.0, oy=0.0, dx=0.0, dy=0.0):
    """scale an absolute path by s about (ox, oy), then move it by (dx, dy)."""
    return _c4_map(d, lambda x, y: (ox + (x - ox) * s + dx, oy + (y - oy) * s + dy))


def _c4_poly(g):
    """a shapely polygon as an absolute path (exterior only)."""
    return 'M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in list(g.exterior.coords)[:-1]) + 'Z'


def _c4_flower(cx, cy, r, n=5, a0=-90):
    """a round-petalled flower: n petal discs around a centre disc."""
    g = _c4_union([_C4Pt(cx + r * .52 * _c4m.cos(_c4m.radians(a0 + k * 360 / n)), cy + r * .52 * _c4m.sin(_c4m.radians(a0 + k * 360 / n))).buffer(r * .5)
                   for k in range(n)] + [_C4Pt(cx, cy).buffer(r * .52)])
    return _c4_poly(g.simplify(.03))


def _c4_sparkle(cx, cy, r):
    """four-point sparkle"""
    k = r * .3
    return (f'M{fmt(cx)} {fmt(cy - r)}Q{fmt(cx + k)} {fmt(cy - k)} {fmt(cx + r)} {fmt(cy)}Q{fmt(cx + k)} {fmt(cy + k)} {fmt(cx)} {fmt(cy + r)}'
            f'Q{fmt(cx - k)} {fmt(cy + k)} {fmt(cx - r)} {fmt(cy)}Q{fmt(cx - k)} {fmt(cy - k)} {fmt(cx)} {fmt(cy - r)}Z')


def _c4_drop(x, y, h):
    """a water drop, tip up at (x, y - h * .55)"""
    w = h * .36
    return (f'M{fmt(x)} {fmt(y - h * .55)}C{fmt(x + w * .6)} {fmt(y - h * .2)} {fmt(x + w)} {fmt(y + h * .02)} {fmt(x + w)} {fmt(y + h * .18)}'
            f'C{fmt(x + w)} {fmt(y + h * .36)} {fmt(x + w * .55)} {fmt(y + h * .45)} {fmt(x)} {fmt(y + h * .45)}'
            f'C{fmt(x - w * .55)} {fmt(y + h * .45)} {fmt(x - w)} {fmt(y + h * .36)} {fmt(x - w)} {fmt(y + h * .18)}'
            f'C{fmt(x - w)} {fmt(y + h * .02)} {fmt(x - w * .6)} {fmt(y - h * .2)} {fmt(x)} {fmt(y - h * .55)}Z')


def _c4_dots(pts):
    return ''.join(f'M{fmt(x)} {fmt(y)}h.1' for x, y in pts)


def _c4_grid(x0, y0, x1, y1, step, ph=0.0):
    """gingham stripes (vertical and horizontal) across a box, for a part's texture."""
    xs = [x0 + ph + i * step for i in range(int((x1 - x0) / step) + 2)]
    ys = [y0 + ph + i * step for i in range(int((y1 - y0) / step) + 2)]
    return [f'M{fmt(x)} {fmt(y0 - 2)}V{fmt(y1 + 2)}' for x in xs] + [f'M{fmt(x0 - 2)} {fmt(y)}H{fmt(x1 + 2)}' for y in ys]


def _c4_cross(x0, y0, x1, y1, step, s, ph=0.0):
    """the darker squares where gingham stripes cross (small rect centres)."""
    return [(x0 + ph + i * step - s / 2, y0 + ph + j * step - s / 2)
            for i in range(int((x1 - x0) / step) + 2) for j in range(int((y1 - y0) / step) + 2)]


_C4_APPLE = 'M24 16C20 12.5 9.5 12 8.2 23.5C7 33.5 14 41.8 19.5 41.5C21.5 41.4 22.5 40.6 24 40.6C25.5 40.6 26.5 41.4 28.5 41.5C34 41.8 41 33.5 39.8 23.5C38.5 12 28 12.5 24 16Z'

# ================================================================ drinks

# apple juice & cider: a clear glass bottle of golden juice (white headspace in the neck), red cap, a red apple in front
add('apple_juice', 'Apple juice & cider', [
    F(('rect', 15.2, 6.4, 9.6, 6, 2), 'strawberry', 'm', det=['M17.6 8.2V10.6M20 8.2V10.6M22.4 8.2V10.6'], det_w=1.2),
    F(('path', 'M16.6 11.6H23.4V15C27.2 16.4 29.6 19.2 29.6 23V38.6Q29.6 42 26.2 42H13.8Q10.4 42 10.4 38.6V23C10.4 19.2 12.8 16.4 16.6 15Z'),
      'lemon', 'm', band=.2, tex=['M6 14.6H34'], tex_t='lp', tex_w=7.6, det=['M13 18.4H27']),
    F(('rect', 13.4, 24.4, 13.2, 10.6, 3), 'lemon', 'w', ol=1.5, cut=False),
    F(('path', heart_path(20, 29.4, 2.8)), 'strawberry', 'm', ol=1.2, cut=False),
    LINE('M33.6 30C33.6 28 34.2 26.4 35.4 25.2', 'strawberry', 2),
    F(('path', _c4_xf(_C4_APPLE, .5, 24, 42, 9.8, 0)), 'strawberry', 'm', band=.26),
    F(('ellipse', 37.6, 33.2, 2.2, 1.6, -35), 'lemon', 'lp', ol=0, cut=False),
], qty='1.89 L bottle', aisle='drinks')

# juice boxes: a little grape juice box, sealed top seam, a bent white straw, a grape-bunch label
add('juice_box', 'Juice boxes', [
    TUBE('M27 15V10.4Q27 8 29.4 7.8L36.4 7.2', 'grape', 3.6, 'w'),
    F(('rect', 10.6, 13.6, 24.8, 4.6, 2), 'grape', 'm'),
    F(('rect', 11.4, 16.4, 23.2, 25.6, 3.4), 'grape', 'p', band=.2),
    F(('rect', 14.4, 21.4, 17.2, 15.4, 3), 'grape', 'w', ol=1.5, cut=False),
    TUBE('M23 26.4C23 24.4 23.8 23.4 25.2 22.8', 'melon', 1.8, 'm', cut=False),
    F(('path', 'M24.6 23.6C25.4 21.4 28 20.6 29.8 21.4C29 23.4 26.6 24.4 24.6 23.6Z'), 'melon', 'm', ol=1.2, cut=False),
    *[F(('circle', x, y, 2.1), 'grape', 'm', ol=1.2, cut=False) for x, y in [(19.2, 27.6), (23, 27.6), (26.8, 27.6), (21.1, 31), (24.9, 31), (23, 34.2)]],
], qty='8 × 200 mL', aisle='drinks')

# pop: a red can with a white printed band of fizz, a silver lid with its pull tab, bubbles fizzing above
add('soda', 'Pop', [
    F(('circle', 37.2, 9.4, 2.6), 'strawberry', 'w', ol=1.5),
    F(('circle', 33.4, 5.8 + 2.2, 1.7), 'strawberry', 'w', ol=1.4),
    F(('circle', 40.2, 14.6, 1.6), 'strawberry', 'w', ol=1.4),
    F(('path', 'M14.4 14.6H33.6C34.8 15.8 35.6 17.4 35.6 19V38.4A11.6 3.6 0 0 1 12.4 38.4V19C12.4 17.4 13.2 15.8 14.4 14.6Z'),
      'strawberry', 'm', band=.18),
    F(('rect', 13.4, 25.8, 21.2, 9.6), 'strawberry', 'w', ol=0, cut=False, det=['M13.6 25.8H34.4', 'M13.6 35.4H34.4'], det_w=1.4),
    *[F(('circle', x, y, r), 'strawberry', 'm', ol=1.2, cut=False) for x, y, r in [(18.6, 31.2, 2.6), (24.6, 29.6, 1.8), (29.8, 31.8, 2.2)]],
    F(('ellipse', 24, 14.6, 9.6, 3.2), 'strawberry', 'w'),
    F(('rect', 21.6, 12.6, 6.4, 3, 1.5), 'strawberry', 'p', ol=1.2, cut=False),
    F(('circle', 23.4, 14.1, .7), 'strawberry', 'w', ol=0, cut=False),
], qty='12 × 355 mL', aisle='drinks')

# beer: a brown long-neck bottle with a gold crown cap, a blank oval label and frost drops, a foamy glass beside it
add('beer', 'Beer', [
    F(('path', 'M17.4 6.6H24.6L24.2 9.6L23.2 8.8L22.2 9.6L21 8.8L19.8 9.6L18.8 8.8L17.8 9.6Z'), 'lemon', 'm', ol=1.6),
    F(('path', 'M18.2 9H23.8V16.6C23.8 19.4 28.8 20.8 28.8 25.6V38.6Q28.8 42 25.4 42H16.6Q13.2 42 13.2 38.6V25.6C13.2 20.8 18.2 19.4 18.2 16.6Z'),
      'cocoa', 'm', band=.16, tex=['M10 13.4H32'], tex_t='lp', tex_w=2.6),
    F(('ellipse', 21, 31.4, 5.6, 4.4), 'lemon', 'lp', ol=1.4, cut=False, det=['M17.6 31.4H24.4'], det_t='m', det_w=1.2),
    *[F(('path', _c4_drop(x, y, h)), 'cocoa', 'w', ol=1.2, cut=False) for x, y, h in [(16.4, 37.6, 3.4), (25.6, 24.6, 2.8)]],
    F(('path', 'M28.6 24.6H40.4L39.4 39.4Q39.2 42 36.6 42H32.4Q29.8 42 29.6 39.4Z'), 'lemon', 'm', band=.22,
      det=['M33 31.4V38.4', 'M36.4 30.4V37'], det_t='p', det_w=1.4),
    F(('path', 'M28 25.6C26.8 23.6 28 21.2 30.2 21.2C30.8 19 33.8 18.4 35.2 20C36.8 18.6 39.8 19.4 40 21.6C42 22 42.2 24.6 40.8 25.8C38.6 27.4 37 26 35.4 26.6C33.4 27.4 30.6 27.4 28 25.6Z'),
      'lemon', 'w', ol=1.6),
], qty='6 × 341 mL', aisle='drinks')

# wine: a dark green bottle with a burgundy foil capsule and a blank label, beside a stemmed glass of red wine
add('wine', 'Wine', [
    F(('path', 'M15.6 5.8H21.4V12.4H15.6Z'), 'strawberry', 'deep', ol=1.8),
    F(('path', 'M15.8 12H21.2V16.2C21.2 18.4 26.2 18.8 26.2 23.6V38.8Q26.2 42 23 42H14Q10.8 42 10.8 38.8V23.6C10.8 18.8 15.8 18.4 15.8 16.2Z'),
      'melon', 'deep', band=.16, band_t='tr'),
    F(('rect', 12.8, 26.4, 11.4, 10.2, 2.4), 'melon', 'w', ol=1.5, cut=False, det=['M15.4 29.4H21.6'], det_t='m', det_w=1.3),
    TUBE('M34.6 32.2V39.2', 'strawberry', 2.8, 'w'),
    F(('ellipse', 34.6, 40.2, 6.2, 1.8), 'strawberry', 'w', ol=1.6),
    F(('path', 'M27 18.8H42.2C42.6 26.6 39.6 33.2 34.6 33.2C29.6 33.2 26.6 26.6 27 18.8Z'), 'strawberry', 'w', ol=2,
      band='M25 24.4H44V36H25Z', band_t='tr', det=['M27.4 24.4H41.8'], det_t='tr', det_w=1.5),
], qty='750 mL bottle', aisle='drinks')

# ================================================================ baby and pet

# baby formula: an open periwinkle tin heaped with powder, a pink scoop resting on top, a baby-bottle label
add('baby_formula', 'Baby formula', [
    F(cyl(9.6, 38.4, 17.6, 42, 4.6), 'periwinkle', 'p', band=.18),
    # the label leaves the can's wall 3.6u each side: room for the wall's gloss clear of the white label
    F(('rect', 14.2, 23.6, 19.6, 13.2), 'periwinkle', 'w', ol=0, cut=False, det=['M14.2 23.6H33.8', 'M14.2 36.8H33.8'], det_w=1.3),
    F(('path', 'M22.4 26.2C22.4 25 23.1 24.4 24 24.4C24.9 24.4 25.6 25 25.6 26.2Z'), 'sakura', 'm', ol=1.2, cut=False),
    F(('rect', 21.4, 26.2, 5.2, 1.8, .9), 'sakura', 'm', ol=1.2, cut=False),
    F(('path', 'M21.8 28H26.2V33.4Q26.2 34.6 25 34.6H23Q21.8 34.6 21.8 33.4Z'), 'sakura', 'p', ol=1.2, cut=False),
    F(('path', heart_path(17, 30, 1.8)), 'sakura', 'm', ol=1.2, cut=False),
    F(('path', heart_path(31, 30, 1.8)), 'sakura', 'm', ol=1.2, cut=False),
    F(('ellipse', 24, 17.6, 14.4, 4.6), 'periwinkle', 'w'),
    F(('path', 'M12.6 17.6C14.4 14.6 18.2 13 21.4 14C23.8 12 28.4 12.4 30.2 14.2C33 13.4 35.6 15.4 35.4 17.6C31.6 20.6 16.6 20.6 12.6 17.6Z'),
      'periwinkle', 'p', ol=1.4, cut=False, tex=['M16 16.4h.1M20.4 15.6h.1M25.6 15.2h.1M30.6 16.2h.1'], tex_t='w', tex_w=1.6),
    TUBE('M27.6 14.4L37.6 6.6', 'sakura', 3, 'm'),
    F(('ellipse', 24.2, 16.4, 4.8, 3.2, -14), 'sakura', 'm', band=.4, band_t='deep'),
], qty='900 g tin', aisle='baby')

# dog food: a tall green kibble bag, a floppy-eared puppy in profile on its label, kibble pieces spilled in front
_C4_DOG = _c4_poly(_c4_union([_C4Pt(22.6, 25.4).buffer(5.2, quad_segs=16),
                              _C4Pt(27.6, 27).buffer(3, quad_segs=12).union(_C4Pt(30.4, 26.8).buffer(2.5, quad_segs=12)).convex_hull,
                              _C4Poly([(18.4, 27), (26.4, 27.4), (26.4, 35), (17.4, 35)]).buffer(.01)]).simplify(.03))
add('dog_food', 'Dog food', [
    F(('path', 'M11.6 12.4H36.4L37.6 16C39.6 23 39.8 33 38.6 38.6Q37.8 42 34.4 42H13.6Q10.2 42 9.4 38.6C8.2 33 8.4 23 10.4 16Z'),
      'melon', 'p', band=.2),
    F(('rect', 10.2, 8.2, 27.6, 5.6, 2.2), 'melon', 'm', det=['M13.4 10.4V11.8M16.6 10.4V11.8M19.8 10.4V11.8M28.2 10.4V11.8M31.4 10.4V11.8M34.6 10.4V11.8'], det_t='p', det_w=1.2),
    F(('rect', 13.4, 18.4, 21.2, 16.6, 3.2), 'melon', 'w', ol=1.5, cut=False),
    F(('path', _C4_DOG), 'cocoa', 'm', ol=1.4, cut=False, det=['M27.6 29.4Q29.4 30.2 31.2 29.4'], det_t='tr', det_w=1.2),
    F(('path', 'M17.9 31.4H26.4V33.6H17.6Z'), 'melon', 'm', ol=1.2, cut=False),
    F(('path', 'M20.4 21C17.4 20.8 15.8 23.6 16.4 27.4C16.8 30 18.8 30.8 20 29.4C21.4 27.6 22.4 23.4 20.4 21Z'), 'cocoa', 'deep', ol=1.2, cut=False),
    F(('circle', 32.4, 25.9, 1.3), 'cocoa', 'tr', ol=0, cut=False),
    F(('circle', 25.8, 24.4, .95), 'cocoa', 'tr', ol=0, cut=False),
    F(('path', blob(13.4, 39, 3.4, 2.8, 6, .14, 4)), 'cocoa', 'm', ol=1.6, det=['M12.6 38.4h.1'], det_t='p', det_w=1.4),
    F(('path', 'M32.6 36.4C31 35.6 29.4 36.6 30 38.2C29.2 39.4 30.2 41.2 31.8 40.6L37.4 40.8C38.8 41.6 40.4 40 39.6 38.6C40.4 37.2 39 35.6 37.4 36.4Z'),
      'cocoa', 'm', ol=1.6),
], qty='8 kg bag', aisle='pet')

# ================================================================ household

# napkins: a stack of square paper napkins, the top one in blue gingham set down a little askew, the layers below
_C4_NTF = 'rotate(-4 24 22.4)'
add('napkins', 'Napkins', [
    F(('rect', 9.6, 37.4, 29.6, 4.6, 2.3), 'ramune', 'p', ol=1.6),
    F(('rect', 8.4, 34.4, 30.4, 4.6, 2.3), 'ramune', 'w', ol=1.6),
    F(('rect', 9.2, 31.4, 30.2, 4.6, 2.3), 'ramune', 'p', ol=1.6),
    F(('rect', 8.6, 11, 30.8, 22.8, 3.6), 'ramune', 'w', tf=_C4_NTF, tex=_c4_grid(8.6, 11, 39.4, 33.8, 6.2, 1.4), tex_t='p', tex_w=3.1),
    *[F(('rect', x, y, 3.1, 3.1, .5), 'ramune', 'm', tf=_C4_NTF, ol=0, cut=False) for x, y in _c4_cross(8.6, 11, 39.4, 33.8, 6.2, 3.1, 1.4)
      if 9.8 < x and x + 3.1 < 38.2 and 12.2 < y and y + 3.1 < 32.6],
], qty='200 napkins', aisle='household')

# dishwasher detergent: a lemon tub of pods with a sparkle-plate label, one square blue-and-white tablet in front
add('dishwasher_tabs', 'Dishwasher detergent', [
    F(('path', 'M9.4 19H33.6L32.4 38.6Q32.2 41.6 29.2 41.6H13.8Q10.8 41.6 10.6 38.6Z'), 'lemon', 'p', band=.22),
    F(('rect', 7.8, 13.6, 27.4, 6.6, 3), 'lemon', 'm', det=['M11.4 16.9H17.8'], det_t='p', det_w=1.4),
    F(('circle', 18.4, 28.4, 5.4), 'lemon', 'w', ol=1.5, cut=False),
    F(('circle', 18.4, 28.4, 2.8), 'lemon', 'p', ol=1.2, cut=False),
    F(('path', _c4_sparkle(25.2, 23.2, 3.4)), 'lemon', 'm', ol=1.2, cut=False),
    F(('rect', 25.4, 26.4, 15.6, 15, 3.4), 'ramune', 'w', tf='rotate(-8 33.2 33.9)', band='M20 34H46V48H20Z', band_t='m', sband=False,
      det=['M25.6 34H40.8'], det_t='tr', det_w=1.4),
    F(('circle', 33.2, 34, 3.4), 'lemon', 'm', tf='rotate(-8 33.2 33.9)', ol=1.4, cut=False),
], qty='60 pods', aisle='household')

# cleaners & bleach: a sky-blue trigger spray bottle, a mint trigger head, a fine mist puffing from the nozzle
add('spray_cleaner', 'Cleaners & bleach', [
    *[F(('circle', x, y, r), 'ramune', 'w', ol=1.3) for x, y, r in [(9.6, 8.6, 1.6), (7.6, 13.4, 1.8), (11.4, 17.2, 1.4)]],
    F(('path', 'M17.2 8.4H32.2Q35.4 8.4 35.4 11.6V14.4Q35.4 16.6 33.2 16.6H24.4L21.6 13.8H15.6Q14.4 13.8 14.4 12.6V11.2Q14.4 8.4 17.2 8.4Z'),
      'mint', 'm', det=['M17.4 11.2H24'], det_t='p', det_w=1.3),
    TUBE('M22.6 14.6C22.6 18 21.2 20 18.6 20.8', 'mint', 3.2, 'm'),
    F(('rect', 24.2, 15.4, 9.6, 5.2, 2.2), 'mint', 'm'),
    F(('path', 'M24.8 19.6H33.2V22C36.4 23.6 38.2 26.4 38.2 30V38.6Q38.2 42 34.8 42H22.6Q19.2 42 19.2 38.6V30C19.2 26.4 21 23.6 24.8 22Z'),
      'ramune', 'm', band=.2),
    F(('rect', 22.4, 27.6, 12.6, 9.8, 2.6), 'ramune', 'w', ol=1.5, cut=False),
    F(('path', _c4_sparkle(28.7, 32.5, 3.6)), 'ramune', 'm', ol=1.2, cut=False),
], qty='950 mL', aisle='household')

# foil & food wrap: a long pink box on the base, a crinkled silver sheet pulled up out of its serrated front edge and
# curling over toward the viewer at the top, catching the light in soft facets
add('aluminum_foil', 'Foil & food wrap', [
    F(('path', 'M10.4 32.6L11.4 13.8Q14.4 11 17.8 12.8Q21 10.6 24.4 12.4Q27.8 10.4 31.2 12.2Q34.6 10.8 36.4 13.4L37.6 32.6Z'), 'periwinkle', 'p'),
    *[F(('path', d), 'periwinkle', 'm', ol=0, cut=False) for d in [
        'M20.4 18.4L26.4 16.4L24.4 23.4Z', 'M14.4 24.6L20.4 22.4L17.6 29Z', 'M29.6 22.4L35 20.2L35.6 26.8Z', 'M23.6 27.4L29.6 26.2L26.6 31.6Z']],
    LINE('M20.4 18.4L24.4 23.4L26.4 16.4M24.4 23.4L29.6 22.4M17.6 29L20.4 22.4L24.4 23.4M26.6 31.6L29.6 26.2', 'periwinkle', 1.5, t='m', cut=False),
    F(('path', 'M31.2 12.2Q34.6 10.8 36.4 13.4L36.9 21.4Q32.4 19.8 31.2 12.2Z'), 'periwinkle', 'm', ol=1.6, det=['M33.8 15.8Q34.5 17 35.2 17.8'], det_t='p', det_w=1.4),
    F(('rect', 6.4, 31.6, 35.2, 10.4, 3.2), 'sakura', 'm', band=.3, band_t='deep'),
    F(('rect', 6, 29.8, 36, 3.8, 1.9), 'sakura', 'deep', ol=1.6,
      det=['M8.6 32.1' + ''.join(f'L{fmt(9.8 + 1.2 * i)} {"31.3" if i % 2 == 0 else "32.1"}' for i in range(26))], det_t='p', det_w=1.2),
], qty='30 m roll', aisle='household')

# sandwich & freezer bags: a clear, slightly puffed zip bag with a ramune zip track and slider, a slice of bread inside
_C4_SLICE = ('M9 22C9 17 12 14.6 16.4 15C18.6 12.4 25.4 12.4 27.6 15C32 14.6 35 17 35 22C35 25 33.4 26.4 32 27V38.8Q32 41.6 29.2 41.6H14.8'
             'Q12 41.6 12 38.8V27C10.6 26.4 9 25 9 22Z')
_C4_CRUMB = ('M11.8 22C11.8 18.8 13.9 17.4 17.1 17.8C19.1 15.4 24.9 15.4 26.9 17.8C30.1 17.4 32.2 18.8 32.2 22C32.2 24.2 30.8 25.2 29.4 25.6'
             'V37.8Q29.4 39 28.2 39H15.8Q14.6 39 14.6 37.8V25.6C13.2 25.2 11.8 24.2 11.8 22Z')
add('zip_bags', 'Sandwich & freezer bags', [
    F(('path', 'M10.4 12.4H37.6Q39.6 12.4 39.8 14.4C40.8 22.4 40.8 31.4 40 38.6Q39.6 42 36.2 42H11.8Q8.4 42 8 38.6C7.2 31.4 7.2 22.4 8.2 14.4Q8.4 12.4 10.4 12.4Z'),
      'ramune', 'w', band=.18, band_t='p', det=['M12.2 23.4Q11.6 28.6 12.2 33.6'], det_t='p', det_w=2),
    TUBE('M10.6 18.2H37.4', 'ramune', 3.4, 'm', cut=False),
    F(('path', _c4_xf(_C4_SLICE, .6, 22, 27.6, 2, 3.4)), 'cocoa', 'm', ol=1.5, cut=False),
    F(('path', _c4_xf(_C4_CRUMB, .6, 22, 27.6, 2, 3.4)), 'cocoa', 'p', ol=0, cut=False),
    F(('rect', 31.4, 9.4, 6.4, 11.6, 2.4), 'ramune', 'm', ol=1.7, det=['M33.6 12.6V17.8'], det_t='p', det_w=1.3),
], qty='50 bags', aisle='household')

# shopping bag: a brown kraft-paper grocery bag standing open, the top folded down into a pinked cuff, side pleats
add('paper_bag', 'Shopping bag', [
    F(('path', 'M12.4 9.4L14.4 7.6L16.4 9.4L18.4 7.6L20.4 9.4L22.4 7.6L24.4 9.4L26.4 7.6L28.4 9.4L30.4 7.6L32.4 9.4L34.4 7.6L35.6 8.8V20H12.4Z'), 'cocoa', 'deep', ol=2),
    F(('path', 'M11 16.4H37L38.6 38.8Q38.8 42 35.6 42H12.4Q9.2 42 9.4 38.8Z'), 'cocoa', 'p', band=.18,
      det=['M14.6 22.4L15.4 39', 'M33.4 22.4L32.6 39'], det_t='m', det_w=1.5),
    F(('path', 'M10.4 14H37.6V20.2L36 22L34.2 20.4L32.4 22L30.6 20.4L28.8 22L27 20.4L25.2 22L23.4 20.4L21.6 22L19.8 20.4L18 22L16.2 20.4L14.4 22L12.6 20.4L10.4 22Z'),
      'cocoa', 'm'),
    F(('path', heart_path(24, 30.6, 3.4)), 'cocoa', 'm', ol=1.3, cut=False),
], qty='each', aisle='household')

# ================================================================ health and personal care

# toothbrushes & floss: a chunky pink toothbrush lying on the diagonal, a blue rubber grip, white and blue bristle tufts
_C4_TB = 'translate(24 27) rotate(-38)'
add('toothbrush', 'Toothbrushes & floss', [
    F(('path', 'M-20.6 -1.6C-20.6 -3.8 -19 -4.4 -17 -4.4H2.6C5 -4.4 6.4 -2.8 8.4 -2.8H18.6Q21.2 -2.8 21.2 -.2V.4Q21.2 3 18.6 3H8.4C6.4 3 5 4.4 2.6 4.4H-17C-19 4.4 -20.6 3.8 -20.6 1.6Z'),
      'sakura', 'm', tf=_C4_TB, band=.3, band_t='deep'),
    F(('rect', -12.6, -3.4, 9.6, 6.8, 2.6), 'ramune', 'm', tf=_C4_TB, ol=1.4, cut=False, det=['M-9.6 -1.4V1.4M-7.8 -1.4V1.4M-6 -1.4V1.4'], det_t='p', det_w=1.2),
    F(('rect', 8.6, -10.4, 12, 8.2, 2.2), 'ramune', 'w', tf=_C4_TB, sband=False, tex=['M11.6 -12V-1', 'M15.6 -12V-1', 'M19.6 -12V-1'], tex_t='m', tex_w=2,
      det=['M9 -6.4H20.4'], det_t='p', det_w=1.2),
], qty='2 pack', aisle='health')

# deodorant: an uncapped stick, its creamy dome of product swirled above a grape collar, a sparkle label
_C4_DEO = 'translate(1 0)'
add('deodorant', 'Deodorant', [
    F(('path', 'M15.8 15.2C15.8 9.4 18.8 6.6 23 6.6C27.2 6.6 30.2 9.4 30.2 15.2V17.6H15.8Z'), 'grape', 'lp', tf=_C4_DEO, ol=2,
      det=['M19.4 12.6Q21.6 11.4 23.6 12.4T27.6 12.2', 'M18.2 15.6Q20.6 14.4 23 15.4T27.8 15.2'], det_t='p', det_w=1.4),
    F(('path', 'M13.6 16.4H32.4C33 21 31.6 24.4 31.6 28.6C31.6 32.4 33.2 35.4 32.8 38.8Q32.4 42 29.2 42H16.8Q13.6 42 13.2 38.8C12.8 35.4 14.4 32.4 14.4 28.6C14.4 24.4 13 21 13.6 16.4Z'),
      'grape', 'p', tf=_C4_DEO, band=.2),
    F(('path', 'M13.7 16.4H32.3L32.2 20.4H13.8Z'), 'grape', 'm', tf=_C4_DEO, ol=1.5, cut=False),
    F(('rect', 17.2, 24.2, 11.6, 10.4, 3), 'grape', 'w', tf=_C4_DEO, ol=1.4, cut=False),
    F(('path', _c4_sparkle(23, 29.4, 3.6)), 'lemon', 'm', tf=_C4_DEO, ol=1.2, cut=False),
], qty='85 g stick', aisle='health')

# razors & shaving: a periwinkle disposable razor on the diagonal, blade head with a lemon glide strip, a puff of foam
_C4_RZ = 'rotate(40 31.4 13.8)'
add('razor', 'Razors & shaving', [
    F(('path', _c4_poly(_c4_union([_C4Pt(x, y).buffer(r, quad_segs=16) for x, y, r in [(28.6, 38, 4), (33.6, 35, 5.2), (38.2, 38.4, 3.6)]]).simplify(.03))),
      'periwinkle', 'w', ol=1.8, band=.3, band_t='p', det=[_c4_dots([(31.4, 34.4), (35.6, 36.4), (29.6, 37.6)])], det_t='p', det_w=1.8),
    F(('circle', 39.4, 29.4, 1.8), 'periwinkle', 'w', ol=1.4),
    F(('circle', 26.2, 31.4, 1.4), 'periwinkle', 'w', ol=1.3),
    F(('path', _c4_poly(__import__('shapely.geometry', fromlist=['LineString']).LineString(
        [(9.6 + (26.2 - 9.6) * t + 4 * t * (1 - t) * -1.6, 39.6 + (18.8 - 39.6) * t + 4 * t * (1 - t) * -1.2) for t in [i / 16 for i in range(17)]]
    ).buffer(2.6, quad_segs=8).simplify(.03))), 'periwinkle', 'm', band=.3, band_t='deep'),
    F(('rect', 23.4, 10.4, 16, 6.8, 2.6), 'periwinkle', 'p', tf=_C4_RZ, sband=False, band=.36, band_t='m',
      det=['M25.8 12.8H37', 'M25.8 14.6H37'], det_t='tr', det_w=1.2),
    F(('rect', 24.4, 8.8, 14, 2.8, 1.4), 'lemon', 'lp', tf=_C4_RZ, ol=1.4),
], qty='4 pack', aisle='health')

# lotion & lip balm: a squat peach pump bottle (the pump's spout turned to the left) and a swirled dollop of cream
add('lotion', 'Lotion & lip balm', [
    F(('path', 'M13 9.6H25.4Q27.4 9.6 27.4 11.6V12.6Q27.4 14.4 25.6 14.4H13Q11.4 14.4 11.4 12.8V11.2Q11.4 9.6 13 9.6Z'), 'peach', 'm'),
    F(('rect', 19.8, 13.6, 4.4, 6.4, 2), 'peach', 'w', ol=1.6),
    F(('rect', 16.2, 18.8, 11.6, 5, 2.2), 'peach', 'm'),
    F(('path', 'M14.6 23.4H29.4C33.4 23.4 35.6 26 35.6 29.6V38.4Q35.6 42 32 42H12Q8.4 42 8.4 38.4V29.6C8.4 26 10.6 23.4 14.6 23.4Z'),
      'peach', 'p', band=.26),
    F(('ellipse', 21.4, 32.4, 6.6, 4.6), 'peach', 'w', ol=1.4, cut=False),
    F(('path', _c4_drop(21.4, 32.8, 6)), 'peach', 'm', ol=1.2, cut=False),
    F(('path', 'M32.6 41.8C30.6 41.8 30 39.6 31.4 38.6C30.6 36.4 32.6 34.6 34.8 35.4C35.2 33.4 37.6 32.6 39 34C39.8 32.6 39.6 31.4 38.6 30.4C41.4 30.6 42.6 33.6 41.2 36C42.8 37.2 42.6 41.8 39.6 41.8Z'),
      'peach', 'w', ol=1.7, det=['M33 39.2Q36 39.8 39 38.4'], det_t='p', det_w=1.3),
], qty='400 mL', aisle='health')

# pads & tampons: a square pink box with a flower and a tab-top lid, an unwrapped winged pad lying in front of it
add('feminine_care', 'Pads & tampons', [
    F(('rect', 8.4, 8.2, 27.2, 27.6, 3.6), 'sakura', 'p', band=.2, det=['M10.6 13.4H33.4'], det_t='m'),
    F(('path', 'M17.6 13.4Q22 16.6 26.4 13.4'), 'sakura', 'm', ol=1.4, cut=False),
    F(('path', _c4_flower(22, 22.8, 6.4)), 'sakura', 'm', ol=1.4, cut=False),
    F(('circle', 22, 22.8, 2.1), 'lemon', 'm', ol=1.2, cut=False),
    F(('path', 'M18.8 31.4H24.2L25.4 29.2H31L32.2 31.4H37.6C40.6 31.4 42 33.4 42 35.6C42 37.8 40.6 39.8 37.6 39.8H32.2L31 42H25.4L24.2 39.8H18.8'
               'C15.8 39.8 14.4 37.8 14.4 35.6C14.4 33.4 15.8 31.4 18.8 31.4Z'), 'sakura', 'w', band=.3, band_t='p',
      det=['M19.6 33.8H36.8Q39 33.8 39 35.6Q39 37.4 36.8 37.4H19.6Q17.4 37.4 17.4 35.6Q17.4 33.8 19.6 33.8Z'], det_t='m', det_w=1.2),
], qty='36 pads', aisle='health')

# pain & allergy medicine: a mint blister card of round white tablets (one popped), a loose tablet and a capsule
add('pills', 'Pain & allergy medicine', [
    F(('rect', 9.2, 7.4, 23.6, 31, 3.6, -10), 'mint', 'p', band=.16, sband=False),
    *[F(('circle', x, y, 3.8), 'mint', 'w', ol=1.5, cut=False, det=[f'M{fmt(x - 1.8)} {fmt(y + .3)}L{fmt(x + 1.8)} {fmt(y - .3)}'], det_t='m', det_w=1.2)
      for x, y in [(15.2, 13.4), (25, 11.6), (16.6, 22.2), (26.4, 20.4), (18, 31)]],
    F(('circle', 27.8, 29.2, 3.8), 'mint', 'm', ol=1.5, cut=False, det=['M26.2 28.2L27.4 29.4L26.4 30.6', 'M29 28L28.4 29.2L29.4 30.2'], det_t='p', det_w=1.2),
    F(('circle', 34.8, 35.6, 6), 'mint', 'w', band=.3, band_t='p', det=['M31.8 35.9L37.8 35.3'], det_t='m', det_w=1.5),
    F(('path', 'M15.6 39.6C14.2 38.2 14.2 36.2 15.6 34.8L18.4 32L22.6 36.2L19.8 39C18.4 40.4 17 41 15.6 39.6Z'), 'peach', 'm', ol=1.6),
    F(('path', 'M18.4 32L21.2 29.2C22.6 27.8 24.6 27.8 26 29.2C27.4 30.6 27.4 32.6 26 34L22.6 36.2Z'), 'peach', 'w', ol=1.6),
], qty='100 tablets', aisle='health')

# cold & flu medicine: a red syrup bottle, a clear dosing cup turned upside down over the cap, a spoon of syrup
add('cough_syrup', 'Cold & flu medicine', [
    F(('path', 'M17.4 14H26.6V17C30.6 18.2 32.4 20.6 32.4 24V38.6Q32.4 42 29 42H15Q11.6 42 11.6 38.6V24C11.6 20.6 13.4 18.2 17.4 17Z'),
      'strawberry', 'm', band=.18),
    F(('rect', 14.6, 25.2, 14.8, 11.6, 2.8), 'strawberry', 'w', ol=1.5, cut=False),
    F(('path', heart_path(22, 30.8, 3.2)), 'strawberry', 'm', ol=1.2, cut=False),
    F(('path', 'M14.6 16.2H29.4L27.4 7.4Q27.2 6.2 26 6.2H18Q16.8 6.2 16.6 7.4Z'), 'ramune', 'w', ol=2,
      band='M10 13.4H34V18H10Z', band_t='p', det=['M19 9.4H21.6', 'M18.6 11.6H22', 'M18.2 13.8H21.6'], det_t='m', det_w=1.2),
    TUBE('M31.6 38.6L40.6 31.8', 'ramune', 2.8, 'w'),
    F(('ellipse', 30.2, 39.4, 4.8, 2.6, -14), 'ramune', 'w', ol=1.7),
    F(('ellipse', 30.2, 39.6, 3, 1.4, -14), 'strawberry', 'm', ol=0, cut=False),
], qty='230 mL', aisle='health')

# bug spray: a green aerosol can, a crossed-out mosquito on its label, a puff of spray from the nozzle
add('bug_spray', 'Bug spray', [
    *[F(('circle', x, y, r), 'melon', 'w', ol=1.3) for x, y, r in [(32, 7.4, 1.5), (36.4, 9.6, 1.8), (35.2, 14.4, 1.4)]],
    F(('rect', 19.8, 6.4, 8.4, 6.8, 2.2), 'melon', 'm'), LINE('M28.2 8.8H29.8', 'melon', 1.6),
    F(('path', 'M17.6 13.2H30.4C32.8 14.6 34 16.4 34 18.8H14C14 16.4 15.2 14.6 17.6 13.2Z'), 'melon', 'w', ol=2),
    F(cyl(14, 34, 18, 42, 3.4), 'melon', 'm', band=.18, band_t='deep'),
    F(('circle', 24, 29.6, 7), 'melon', 'w', ol=1.5, cut=False),
    LINE('M21.6 27.4L18.8 25.6M21.2 30L18.2 30.6M26.2 27.2L29 25.4M26.6 29.8L29.6 30.4M21.8 32.8L20 35M26 32.8L27.8 35', 'melon', 1.2, t='tr', cut=False),
    F(('ellipse', 24, 29.6, 2.2, 3.6), 'melon', 'm', ol=1.2, cut=False),
    F(('circle', 24, 25.4, 1.6), 'melon', 'm', ol=1.2, cut=False),
    F(('ellipse', 21.2, 26.6, 2.2, 1.2, 20), 'melon', 'p', ol=1.2, cut=False),
    F(('ellipse', 26.8, 26.6, 2.2, 1.2, -20), 'melon', 'p', ol=1.2, cut=False),
    LINE('M19.05 24.65L28.95 34.55', 'strawberry', 2.2, t='m', cut=False),
    LINE('M24 22.6A7 7 0 1 1 23.99 22.6', 'strawberry', 2.2, t='m', cut=False),
], qty='300 g can', aisle='health')

# ================================================================ flowers and garden

# flowers: a little bouquet of pink and yellow blooms with leaves in a white paper cone tied with a pink bow
def _c4_tulip(cx, cy, s=1.0):
    """a tulip head, three pointed petals, opening upward"""
    p = [(-4.8, -2.6), (-4.6, 2.6), (-2.6, 4.8), (2.6, 4.8), (4.6, 2.6), (4.8, -2.6), (2.6, -.6), (0, -4.8), (-2.6, -.6)]
    return (f'M{fmt(cx + p[0][0] * s)} {fmt(cy + p[0][1] * s)}C{fmt(cx - 5 * s)} {fmt(cy + 1 * s)} {fmt(cx - 4.4 * s)} {fmt(cy + 4.8 * s)} {fmt(cx)} {fmt(cy + 4.8 * s)}'
            f'C{fmt(cx + 4.4 * s)} {fmt(cy + 4.8 * s)} {fmt(cx + 5 * s)} {fmt(cy + 1 * s)} {fmt(cx + 4.8 * s)} {fmt(cy - 2.6 * s)}'
            f'L{fmt(cx + 2.4 * s)} {fmt(cy - .4 * s)}L{fmt(cx)} {fmt(cy - 4.6 * s)}L{fmt(cx - 2.4 * s)} {fmt(cy - .4 * s)}Z')


add('bouquet', 'Flowers', [
    *[TUBE(d, 'melon', 2.4, 'm') for d in ('M22.4 34L21 40.6', 'M24 34V40.8', 'M25.6 34L27 40.6')],
    F(('path', 'M9.8 14.8C6.6 9.8 8.4 6.8 11 7.2C13.6 7.6 14.2 11.4 14 15.8Z'), 'melon', 'm', ol=1.6, det=['M11.2 9.6Q12 12 12.4 14.4'], det_t='p', det_w=1.2),
    F(('path', 'M38.2 14.8C41.4 9.8 39.6 6.8 37 7.2C34.4 7.6 33.8 11.4 34 15.8Z'), 'melon', 'm', ol=1.6, det=['M36.8 9.6Q36 12 35.6 14.4'], det_t='p', det_w=1.2),
    TUBE('M15.8 21V15.4', 'melon', 2.2, 'm'), TUBE('M32.2 21V15.4', 'melon', 2.2, 'm'),
    F(('path', _c4_tulip(15.8, 12.6)), 'sakura', 'm', ol=1.7, det=['M15.8 13.6V16.4'], det_t='deep', det_w=1.3),
    F(('path', _c4_tulip(32.2, 12.6)), 'sakura', 'm', ol=1.7, det=['M32.2 13.6V16.4'], det_t='deep', det_w=1.3),
    F(('path', _c4_flower(24, 12.2, 6.6, a0=-90)), 'sakura', 'w', ol=1.7),
    F(('circle', 24, 12.2, 2.4), 'lemon', 'm', ol=1.4),
    F(('path', 'M8.6 17.6C13.4 20.4 34.6 20.4 39.4 17.6L28.4 33.6H19.6Z'), 'sakura', 'w', band=.34, band_t='p',
      det=['M15 21L21.4 31.4', 'M33 21L26.6 31.4'], det_t='p', det_w=1.5),
    F(('path', 'M20.2 32.6H27.8L29.6 37H18.4Z'), 'sakura', 'w', ol=1.7),
    F(('path', 'M24 32.6C20.6 28.6 15.4 29.6 16 33C16.6 36.2 21.4 34.8 24 32.6C26.6 34.8 31.4 36.2 32 33C32.6 29.6 27.4 28.6 24 32.6Z'), 'sakura', 'm', ol=1.6,
      det=['M18.6 32.2Q20.6 32.2 22.4 32.8', 'M29.4 32.2Q27.4 32.2 25.6 32.8'], det_t='p', det_w=1.2),
    F(('circle', 24, 32.8, 2.1), 'sakura', 'm', ol=1.4),
], qty='1 bunch', aisle='garden')

# potted plant: a terracotta pot with a rolled rim, a leafy green plant and one yellow flower on a stem
_C4_LEAF = 'M0 0C-3.4 -2.6 -3.6 -8.4 0 -11.6C3.6 -8.4 3.4 -2.6 0 0Z'
add('potted_plant', 'Potted plant', [
    TUBE('M24 25C24.4 20 25.8 16.4 28.6 13.4', 'melon', 2.2, 'm'),
    *[F(('path', _C4_LEAF), 'melon', 'm', tf=f'translate({x} {y}) rotate({a})', ol=1.7, det=['M0 -1.4V-9'], det_t='p', det_w=1.2)
      for x, y, a in [(21.6, 26.4, -58), (26.4, 26.4, 58), (22.4, 25.6, -24), (25.8, 25.6, 22)]],
    F(('path', _c4_flower(29.4, 12.8, 7.2, a0=-60)), 'peach', 'w', ol=1.6),
    F(('circle', 29.4, 12.8, 2.4), 'lemon', 'm', ol=1.4),
    F(('path', 'M13.2 28.4H34.8L32.4 39.2Q31.8 42 29 42H19Q16.2 42 15.6 39.2Z'), 'peach', 'm', band=.26, band_t='deep'),
    F(('rect', 10.8, 24.4, 26.4, 6.2, 2.6), 'peach', 'm', det=['M13.8 27.5H20.6'], det_t='p', det_w=1.4),
], qty='12 cm pot', aisle='garden')

# seeds: a yellow paper seed packet with a sunflower picture, its top torn open and seeds tumbling out
add('seed_packet', 'Seeds', [
    *[F(('ellipse', x, y, 1.4, 2.2, a), 'cocoa', 'm', ol=1.3)
      for x, y, a in [(34.6, 9.6, 30), (39.4, 13.6, -20), (36.4, 17.4, 60)]],
    F(('path', 'M11.4 15.6L14 13.4L16.4 15.2L18.8 13.2L21.2 15.2L23.6 13.4L26 15.4L28.4 13.4L30.8 15L33.2 13.2L36.6 15.4V38.6Q36.6 42 33.2 42H14.8Q11.4 42 11.4 38.6Z'),
      'lemon', 'p', band=.18, band_t='m'),
    F(('rect', 16, 18.6, 16, 16.4, 2.6), 'lemon', 'w', ol=1.5, cut=False),
    TUBE('M24 34.4V29.4', 'melon', 2.4, 'm', cut=False),
    F(('path', 'M24 32.8C21.4 31.4 19.6 32.2 19.4 34C21.6 34.8 23 34.4 24 32.8Z'), 'melon', 'm', ol=1.2, cut=False),
    *[F(('ellipse', 24 + 4 * _c4m.cos(_c4m.radians(a)), 25.6 + 4 * _c4m.sin(_c4m.radians(a)), 2.4, 1.6, a), 'lemon', 'm', ol=1.5, cut=False) for a in range(0, 360, 45)],
    F(('circle', 24, 25.6, 3), 'cocoa', 'm', ol=1.4, cut=False, det=[_c4_dots([(23, 24.8), (25, 24.8), (24, 26.6)])], det_t='p', det_w=1.4),
], qty='1 packet', aisle='garden')

# charcoal: a pale periwinkle paper sack with a stitched top and a flame label, dark pillow briquettes heaped in front
def _c4_briq(cx, cy, rot, s=.86):
    tf = f'translate({fmt(cx)} {fmt(cy)}) rotate({rot})'      # the size is baked into the path (a scale() would thin the die-cut)
    return [F(('path', _c4_xf('M-5 -1.2C-5 -3.4 -3.6 -4 -1.6 -3.8C0 -3.6 1.2 -4.4 2.6 -4.2C4.6 -4 5.2 -2.6 5 -.6C4.8 1.8 4.6 3.8 2.4 4C.8 4.2 -.6 3.4 -2.4 3.8'
                              'C-4.4 4.2 -5 2.4 -5 -1.2Z', s)), 'periwinkle', 'tr', tf=tf, ol=1.6),
            F(('path', _c4_xf('M-3.2 -1.4C-3 -2.6 -1.6 -2.4 0 -2.2C1.4 -2 2.8 -2.8 3.2 -1.4C3.4 -.2 1.8 .6 0 .4C-1.6 .2 -3.4 .2 -3.2 -1.4Z', s)),
              'periwinkle', 'deep', tf=tf, ol=0, cut=False)]


add('charcoal', 'Charcoal', [
    F(('path', 'M10.6 13.4H33.4C35 20 35.4 30 34.4 37.6Q34 41.6 30.2 41.6H13.8Q10 41.6 9.6 37.6C8.6 30 9 20 10.6 13.4Z'), 'periwinkle', 'p', band=.2, band_t='m'),
    F(('rect', 9.2, 8.6, 25.6, 6.6, 2.4), 'periwinkle', 'm', det=['M12.4 11.9h.1M15.6 11.9h.1M18.8 11.9h.1M22 11.9h.1M25.2 11.9h.1M28.4 11.9h.1M31.6 11.9h.1'], det_t='w', det_w=1.6),
    F(('rect', 13.6, 18.8, 16.8, 14.6, 3), 'periwinkle', 'w', ol=1.5, cut=False),
    F(('path', 'M22 31.2C18.6 31.2 16.8 28.8 17.6 25.8C18.2 23.8 19.8 23 20.2 20.8C22.2 22 22.6 23.8 22.4 25C23.6 24.2 24.2 23 24.2 21.8C26.2 23.8 27.4 26.4 26.6 28.6C26 30.2 24.4 31.2 22 31.2Z'),
      'peach', 'm', ol=1.3, cut=False),
    F(('path', 'M22 30.2C20.6 30.2 19.8 29 20.4 27.8C20.8 27 21.6 26.6 21.8 25.8C23.2 26.6 24 27.8 23.6 29C23.4 29.8 22.8 30.2 22 30.2Z'), 'lemon', 'lp', ol=0, cut=False),
    *_c4_briq(29.8, 38, -6),
    *_c4_briq(37.6, 37.8, 8),
    *_c4_briq(34, 31.4, -14),
], qty='4.5 kg bag', aisle='garden')

# matches & lighters: an open red matchbox, the tray pushed up showing red match heads, one lit match leaning on it
add('matches', 'Matches & lighters', [
    F(('path', 'M34.6 13.4C32 12.6 30.8 9.8 32 7.2C32.8 5.6 34.4 5.4 35 4.6C36.6 6 39.2 7.6 38.8 10.4C38.6 12.6 36.6 13.8 34.6 13.4Z'),
      'peach', 'm', tf='translate(-.6 1.6)', ol=1.6),
    F(('path', 'M34.8 12.4C33.6 12 33.2 10.6 33.8 9.6C34.2 9 34.8 8.8 35 8.2C36 9 37 10 36.8 11.2C36.6 12.2 35.6 12.6 34.8 12.4Z'), 'lemon', 'lp',
      tf='translate(-.6 1.6)', ol=0, cut=False),
    TUBE('M33.4 19.2L30.8 40', 'peach', 2.6, 'lp'),
    F(('ellipse', 33.8, 17, 2.2, 2.8, 8), 'strawberry', 'm', ol=1.5),
    *[TUBE(f'M{fmt(x)} 26V{fmt(y)}', 'peach', 2.4, 'lp') for x, y in [(12.6, 19.6), (16.4, 17.8), (20.2, 19), (24, 17.4)]],
    *[F(('ellipse', x, y, 1.9, 2.4), 'strawberry', 'm', ol=1.4) for x, y in [(12.6, 18.6), (16.4, 16.8), (20.2, 18), (24, 16.4)]],
    F(('rect', 9.4, 21.6, 18, 7.4, 2), 'strawberry', 'p', det=['M9.6 24H27.2'], det_t='m', det_w=1.2),
    F(('rect', 7, 26.4, 23, 15.6, 3), 'strawberry', 'm', band=.2),
    F(('rect', 11.6, 31, 13.8, 7.6, 2.4), 'strawberry', 'w', ol=1.4, cut=False),
    F(('path', 'M18.4 37C16.6 37 15.8 35.6 16.2 34.2C16.6 33 17.6 32.6 17.8 31.2C19 32 19.4 33 19.2 33.8C19.8 33.4 20.2 32.8 20.2 32.2C21.4 33.4 21.8 34.8 21.2 35.8C20.8 36.6 19.8 37 18.4 37Z'),
      'peach', 'm', ol=1.2, cut=False, tf='translate(0 .8)'),
], qty='10 boxes', aisle='garden')

# candles: a cream pillar candle on a little peach saucer, wax dripping over its rim, the flame glowing with tiny rays
_C4_WAX = _c4_poly(_c4_union([_C4Poly([(24 + 8 * _c4m.cos(t * _c4m.pi / 36), 19.8 + 2.8 * _c4m.sin(t * _c4m.pi / 36)) for t in range(72)])] +
    [_C4Pt(x, y0).buffer(1.5, quad_segs=12).union(_C4Pt(x, y1).buffer(1.5, quad_segs=12)).convex_hull for x, y0, y1 in [(19.4, 21.4, 25), (28.2, 21.2, 27.6)]]).simplify(.03))
add('candle', 'Candles', [
    LINE('M24 6V6.6M16.6 9.4L18 10.6M31.4 9.4L30 10.6', 'lemon', 1.8, t='m'),
    F(('path', 'M24 19.2C21.2 19.2 19.6 17.4 20 15C20.4 12.6 22.6 11.2 24 8.6C25.4 11.2 27.6 12.6 28 15C28.4 17.4 26.8 19.2 24 19.2Z'), 'peach', 'm', ol=1.7),
    F(('path', 'M24 17.8C22.7 17.8 22 16.9 22.2 15.8C22.4 14.7 23.3 14.1 24 12.8C24.7 14.1 25.6 14.7 25.8 15.8C26 16.9 25.3 17.8 24 17.8Z'), 'lemon', 'lp', ol=0, cut=False),
    F(cyl(16, 32, 19.8, 39.6, 2.8), 'lemon', 'p', band=.22),
    F(('path', _C4_WAX), 'lemon', 'w', ol=1.6),
    F(('ellipse', 24, 20, 2.6, 1), 'lemon', 'p', ol=1.2, cut=False),
    LINE('M24 19.8V18.2', 'lemon', 1.6, t='tr', cut=False),
    F(('path', 'M9.6 36.4H38.4L36.8 40.2Q36 42 34 42H14Q12 42 11.2 40.2Z'), 'peach', 'm', band=.4, band_t='deep'),
], qty='2 pack', aisle='garden')

# picnic blanket: a red-and-white gingham blanket rolled into a long cylinder, its rolled end a spiral, fringe tassels
# at the far end, buckled with a leather strap (no carry handle: with one it read as a handbag)
_C4_ROLL = 'M12.4 25.6H38.6Q41.6 25.6 41.6 28.6V39Q41.6 42 38.6 42H12.4Z'
add('picnic_blanket', 'Picnic blanket', [
    F(('path', _C4_ROLL), 'strawberry', 'w', band=.22, band_t='p', tex=_c4_grid(12.4, 25.6, 41.6, 42, 5.6, 1.8), tex_t='p', tex_w=2.8,
      det=['M39.2 28.4V30.6M39.2 32.6V34.8M39.2 36.8V39'], det_t='m', det_w=1.5),
    *[F(('rect', x, y, 2.8, 2.8, .5), 'strawberry', 'm', ol=0, cut=False) for x, y in _c4_cross(12.4, 25.6, 41.6, 42, 5.6, 2.8, 1.8)
      if 14.4 < x and x + 2.8 < 37.4 and 26.6 < y and y + 2.8 < 38.4],
    F(('ellipse', 12.4, 33.8, 6, 8.2), 'strawberry', 'p', band=.22, band_t='m',
      det=['M12.6 34C11.2 32.8 13 30.8 14.6 32C16.6 33.6 15.2 37.2 12 37C8.6 36.8 7.8 32.2 10 29.6C12.4 26.8 16.6 27.8 17.6 31.4'], det_t='m', det_w=1.5),
    F(('rect', 31.4, 24, 5.6, 18, 2.4), 'cocoa', 'm', band=.2, det=['M33 27V39'], det_t='p', det_w=1.2),
    F(('rect', 30.2, 30.2, 8, 6.2, 2), 'cocoa', 'p', ol=1.6),
    F(('rect', 32.6, 32.2, 3.2, 2.2, 1.1), 'cocoa', 'm', ol=1.2, cut=False),
], qty='1 blanket', aisle='garden')

# paper plates: a stack of three paper plates seen from a little above, the top one with a ribbed mint rim round a plain
# white well (seen face-on a lone plate read as a button or a bottle cap at 22-30 dp; the stack reads as plates)
def _c4_ribs(cx, cy, r0x, r0y, r1x, r1y, n, skip=None):
    """n radial ribs between two concentric ellipses (none at angles in `skip`, degrees: room for the gloss)"""
    out = ''
    for i in range(n):
        if skip and skip[0] <= 360 * i / n <= skip[1]:
            continue
        t = 2 * _c4m.pi * i / n
        c, s_ = _c4m.cos(t), _c4m.sin(t)
        out += f'M{fmt(cx + r0x * c)} {fmt(cy + r0y * s_)}L{fmt(cx + r1x * c)} {fmt(cy + r1y * s_)}'
    return out


_C4_PY = 26.4
add('paper_plates', 'Paper plates', [
    F(('ellipse', 24, _C4_PY + 6.8, 17.6, 8.8), 'mint', 'p', ol=1.8),
    F(('ellipse', 24, _C4_PY + 3.4, 17.6, 8.8), 'mint', 'w', ol=1.8),
    F(('ellipse', 24, _C4_PY, 17.6, 8.8), 'mint', 'p', band=.2, band_t='m',
      det=[_c4_ribs(24, _C4_PY, 12.2, 5.6, 15.2, 6.9, 24, (190, 260))], det_t='m', det_w=1.2),
    F(('ellipse', 24, _C4_PY - .3, 10.4, 4.2), 'mint', 'w', ol=1.5, cut=False),
], qty='50 plates', aisle='garden')


# paper & plastic cups: a stack of two sakura paper cups with white polka dots and rolled rims, the top one lifted and
# tipped a little so both cups read (three straight-nested rims read as one cup with a ridged lid)
def _c4_cup(cx, top, h, wt=19.6, wb=12.4, tf=None):
    l0, r0, l1, r1, y0, y1 = cx - wt / 2, cx + wt / 2, cx - wb / 2, cx + wb / 2, top + 1, top + h
    body = (f'M{fmt(l0)} {fmt(y0)}H{fmt(r0)}L{fmt(r1 + .3)} {fmt(y1 - 2.2)}Q{fmt(r1)} {fmt(y1)} {fmt(r1 - 2.2)} {fmt(y1)}'
            f'H{fmt(l1 + 2.2)}Q{fmt(l1)} {fmt(y1)} {fmt(l1 - .3)} {fmt(y1 - 2.2)}Z')
    dots = _c4_dots([(cx + dx + (2.4 if (i % 2) else 0), top + 5.4 + i * 4.2) for i in range(int((h - 6) / 4.2) + 1) for dx in (-4, .8, 5.6)])
    k = dict(tf=tf, sband=False) if tf else {}
    return [F(('path', body), 'sakura', 'p', band=.2, band_t='m', tex=[dots], tex_t='w', tex_w=2.4, **k),
            F(('ellipse', cx, top + .2, wt / 2 + 1.2, 2.6), 'sakura', 'm', **k),
            F(('ellipse', cx, top + .4, wt / 2 - .9, 1.3), 'sakura', 'deep', ol=1.3, cut=False, **k)]


add('paper_cups', 'Paper & plastic cups', [
    *_c4_cup(22.6, 17.2, 24.8),
    *_c4_cup(25.4, 8.8, 22.4, tf='rotate(7 25.4 20)'),
], qty='30 cups', aisle='garden')

# disposable cutlery: a chunky white plastic spoon, knife and fork fanned out and tied together with a pink ribbon bow; the
# fork has real tines (a flat-topped head read as a spatula), so the spoon's broad bowl, upper left, carries the gloss
_C4_FORK = ('M17.4 15V8.4Q17.4 6.6 19.1 6.6Q20.8 6.6 20.8 8.4V12.6Q20.8 13.6 21.6 13.6Q22.4 13.6 22.4 12.6V8.4Q22.4 6.6 24 6.6Q25.6 6.6 25.6 8.4'
            'V12.6Q25.6 13.6 26.4 13.6Q27.2 13.6 27.2 12.6V8.4Q27.2 6.6 28.9 6.6Q30.6 6.6 30.6 8.4V15C30.6 18.4 27.8 20.4 26.2 21.2V39.8Q26.2 42 24 42'
            'Q21.8 42 21.8 39.8V21.2C20.2 20.4 17.4 18.4 17.4 15Z')
_C4_SPOON = 'M24 5.4C28 5.4 30.4 9 30.4 13.4C30.4 17.6 28.2 20.2 26.2 21V39.8Q26.2 42 24 42Q21.8 42 21.8 39.8V21C19.8 20.2 17.6 17.6 17.6 13.4C17.6 9 20 5.4 24 5.4Z'
_C4_KNIFE = 'M21.4 22V9Q21.4 6.2 24 6.4C27.2 6.8 28 12 28 16.4C28 19.8 27.2 21.4 26.2 22V39.8Q26.2 42 24 42Q21.8 42 21.8 39.8V22Z'
add('plastic_cutlery', 'Disposable cutlery', [
    F(('path', _C4_SPOON), 'ramune', 'w', tf='rotate(-30 24 33)', band=.12, band_t='p'),
    F(('ellipse', 24, 13.6, 4, 5.6), 'ramune', 'p', tf='rotate(-30 24 33)', ol=0, cut=False),
    F(('path', _C4_FORK), 'ramune', 'w', tf='rotate(30 24 33)', band=.12, band_t='p'),
    F(('path', _C4_KNIFE), 'ramune', 'w', band=.12, band_t='p',
      det=['M25.2 11.2L26 11.8M25.4 13.8L26.2 14.4M25.4 16.4L26.2 17'], det_t='m', det_w=1.2),
    F(('path', 'M22.6 32.2L19.6 38.6L22 38.2L22.8 40.4L24.8 33Z'), 'sakura', 'm', ol=1.5),
    F(('path', 'M25.4 32.2L28.4 38.6L26 38.2L25.2 40.4L23.2 33Z'), 'sakura', 'm', ol=1.5),
    F(('path', 'M24 31.4C20.6 27.4 14.6 27.8 15 31.4C15.4 35 20.8 34.4 24 31.4C27.2 34.4 32.6 35 33 31.4C33.4 27.8 27.4 27.4 24 31.4Z'), 'sakura', 'm', ol=1.7,
      det=['M18.4 30.6Q20.4 30.8 22 31.4', 'M29.6 30.6Q27.6 30.8 26 31.4'], det_t='p', det_w=1.2),
    F(('circle', 24, 31.6, 2.3), 'sakura', 'm', ol=1.5),
], qty='48 pieces', aisle='garden')
