# ---------- C2 · batch 2: fresh counters (greens and herbs, bakery, deli, meat, seafood, dairy, plant-based) ----------
# Executed inside icons.py after the r9 files (design/stickers/MISSING.md, batch 2). Each DSL key is its catalog key, so
# catalog_map.py needs no entry. Helpers are prefixed _c2 so later batch files never collide with them. Gloss is never
# hand-placed: gloss.autogloss draws it. Every shape is authored in icon space (no scale transforms) so the 2u trace
# never changes width.
import math as _c2m


def _c2_poly(pts, closed=True):
    s = 'M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in pts)
    return s + ('Z' if closed else '')


def _c2_xf(pts, tf):
    """points through a transform string"""
    M = parse_tf(tf)
    return [apply_tf(M, x, y) for x, y in pts]


def _c2_leaf(l, w):
    """pointed oval leaf, base at the origin, tip at (0, -l)"""
    return (f'M0 0C{fmt(-w * 1.15)} {fmt(-l * .18)} {fmt(-w * 1.1)} {fmt(-l * .78)} 0 {fmt(-l)}'
            f'C{fmt(w * 1.1)} {fmt(-l * .78)} {fmt(w * 1.15)} {fmt(-l * .18)} 0 0Z')


def _c2_spoon(l, w, s=0):
    """spoon-shaped leaf blade from (0, -s) to (0, -l): narrow at the stem, round at the top"""
    b = l - s
    return (f'M0 {fmt(-s)}C{fmt(-w * .45)} {fmt(-s - b * .16)} {fmt(-w)} {fmt(-s - b * .36)} {fmt(-w)} {fmt(-s - b * .62)}'
            f'C{fmt(-w)} {fmt(-l + b * .14)} {fmt(-w * .5)} {fmt(-l)} 0 {fmt(-l)}'
            f'C{fmt(w * .5)} {fmt(-l)} {fmt(w)} {fmt(-l + b * .14)} {fmt(w)} {fmt(-s - b * .62)}'
            f'C{fmt(w)} {fmt(-s - b * .36)} {fmt(w * .45)} {fmt(-s - b * .16)} 0 {fmt(-s)}Z')


def _c2_dots(pts):
    return ''.join(f'M{fmt(x)} {fmt(y)}h.1' for x, y in pts)


def _c2_bubble(x, y, r):
    """a soft lopsided blister (bread bubbles)"""
    return (f'M{fmt(x - r)} {fmt(y + r * .2)}C{fmt(x - r)} {fmt(y - r * .9)} {fmt(x + r * .9)} {fmt(y - r)} {fmt(x + r)} {fmt(y - r * .1)}'
            f'C{fmt(x + r * 1.1)} {fmt(y + r * .7)} {fmt(x - r)} {fmt(y + r * 1.1)} {fmt(x - r)} {fmt(y + r * .2)}Z')


def _c2_squiggles(x0, y0, x1, y1, step=4.2, seed=3):
    """rows of little S-curls: cheese shreds"""
    import random as _r
    g = _r.Random(seed)
    out, y, row = [], y0, 0
    while y < y1:
        x = x0 + (step * .5 if row % 2 else 0)
        while x < x1:
            a = g.uniform(-1, 1)
            out.append(f'M{fmt(x)} {fmt(y)}c1 -1.8 2.2 -1.8 2.6 {fmt(a * .4)}s1.6 1.8 2.6 {fmt(a * .3)}')
            x += step * 1.4
        y += step * .62
        row += 1
    return out


# 1 green onions: three scallions in a diagonal bundle, each splitting into two blades; white bulbs and roots at the
# lower left; a sakura rubber band
_C2_GO = 'translate(24.4 23.3) rotate(-44)'


def _c2_blade(dy, x0, ln, hw):
    return (f'M{fmt(x0)} {fmt(dy - hw)}C{fmt(x0 + 9)} {fmt(dy - hw - .3)} {fmt(ln - 9)} {fmt(dy - hw)} {fmt(ln - 2.2)} {fmt(dy - hw * .55)}'
            f'Q{fmt(ln + .8)} {fmt(dy)} {fmt(ln - 2.2)} {fmt(dy + hw * .55)}C{fmt(ln - 9)} {fmt(dy + hw)} {fmt(x0 + 9)} {fmt(dy + hw + .3)} {fmt(x0)} {fmt(dy + hw)}Z')


def _c2_scallion(dy, fan, ln, split, hw=2.7):
    tf = f'{_C2_GO} rotate({fan} -14 {fmt(dy)})'
    bulb = (f'M-8.4 {fmt(dy - 2.8)}C-12.6 {fmt(dy - 4.2)} -17.4 {fmt(dy - 3.8)} -17.4 {fmt(dy)}'
            f'C-17.4 {fmt(dy + 3.8)} -12.6 {fmt(dy + 4.2)} -8.4 {fmt(dy + 2.8)}Z')
    return [F(('path', _c2_blade(dy, 0, ln - 4, 2.3)), 'melon', 'm', band=.34, sband=False, tf=f'{tf} rotate({split} 0 {fmt(dy)})'),
            F(('path', _c2_blade(dy, -11, ln, hw)), 'melon', 'm', band=.34, sband=False, tf=tf),
            F(('path', bulb), 'melon', 'w', band=.32, sband=False, tf=tf),
            LINE(f'M-17.4 {fmt(dy - 1)}l-2.2 -1.4M-17.6 {fmt(dy + .2)}h-2.6M-17.4 {fmt(dy + 1.2)}l-2.2 1.4', 'melon', 1.3, tf=tf)]


add('green_onion', 'Green onions', [
    *_c2_scallion(-4.6, -6, 18.4, -9), *_c2_scallion(0, 0, 20.4, 11), *_c2_scallion(4.6, 6, 18.4, 12),
    F(('rect', -7.2, -10.2, 4.8, 20.4, 2.4), 'sakura', 'm', tf=_C2_GO),
], qty='1 bunch', aisle='produce')

# 2 ginger: a hand of tan root: one big smooth lobe on the left (so the gloss is one arc), knobby fingers pointing up
# and out on the right with ring lines across them, a cut coin of pale-lemon flesh in front
def _c2_ginger():
    from shapely.geometry import Point as _Pt
    from shapely import affinity as _aff
    from shapely.ops import unary_union as _un
    lobes = [_aff.scale(_Pt(16.8, 30.6).buffer(1, quad_segs=24), 11.6, 9.6),
             _aff.rotate(_aff.scale(_Pt(29.8, 24.6).buffer(1, quad_segs=24), 4.8, 9.6), 24),
             _aff.rotate(_aff.scale(_Pt(36.6, 20.6).buffer(1, quad_segs=24), 3.7, 7.2), 48),
             _aff.scale(_Pt(26.6, 35.6).buffer(1, quad_segs=24), 8.6, 5.2)]
    g = _un(lobes).buffer(1.4, quad_segs=12).buffer(-1.4, quad_segs=12)
    return 'M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in list(g.simplify(.04).exterior.coords)[:-1]) + 'Z'


_C2_GRINGS = ['M27.2 21.6Q30 23.6 33 21.2', 'M26.6 28.4Q28.8 30.2 31.6 28.4', 'M34.2 21.8Q36.8 23 39 20.6', 'M37 17.8Q38.4 18.4 39.4 17.4']
add('ginger', 'Ginger', [
    F(('path', _c2_ginger()), 'cocoa', 'p', band=.26, det=_C2_GRINGS, det_t='m', det_w=1.6),
    F(('circle', 35.8, 35.4, 6.2), 'cocoa', 'lp', det=[dots_ring(35.8, 35.4, 3.4, 7, 10), 'M35.8 35.4h.1'], det_t='m', det_w=1.3),
], qty='each', aisle='produce')

# 3 salad mix: a pale-green tinted clamshell (so the gloss sits on a surface), green and red baby leaves inside the dome
# and the tub, melon rim
_C2_SL = [  # (x, y, rx, ry, rot, fam)
    (17, 16.4, 4.8, 3.2, -25, 'melon'), (24.4, 14.4, 5, 3.2, 8, 'strawberry'), (31.2, 16.6, 4.6, 3, 28, 'melon'),
    (19.2, 29.4, 5, 3.4, 20, 'strawberry'), (24.6, 28, 5.4, 3.6, -12, 'melon'), (31, 29.6, 4.4, 3.2, -24, 'strawberry'),
    (20.6, 35.6, 5, 3.2, -8, 'melon'), (28.4, 35.4, 4.8, 3.2, 14, 'melon'),
]


def _c2_salad_leaves(sel):
    out = []
    for n, (x, y, rx, ry, a, fam) in enumerate(_C2_SL):
        if n not in sel:
            continue
        c, s = _c2m.cos(_c2m.radians(a)), _c2m.sin(_c2m.radians(a))
        out.append(F(('path', blob(x, y, rx, ry, 9, .12, n + 3, a)), fam, 'm', ol=1.3, cut=False,
                     det=[f'M{fmt(x - rx * .6 * c)} {fmt(y - rx * .6 * s)}L{fmt(x + rx * .6 * c)} {fmt(y + rx * .6 * s)}'],
                     det_t='p', det_w=1.2))
    return out


add('salad_mix', 'Salad mix', [
    F(('path', 'M9.4 21.4C9.4 13.6 15 9.4 24 9.4C33 9.4 38.6 13.6 38.6 21.4Z'), 'melon', 'p'),
    *_c2_salad_leaves({0, 1, 2}),
    F(('path', 'M9.4 22.6H38.6L37.2 38.8Q36.8 42 33.4 42H14.6Q11.2 42 10.8 38.8Z'), 'melon', 'p', band=.18, band_t='m'),
    *_c2_salad_leaves({3, 4, 5, 6, 7}),
    F(('rect', 7.2, 19.4, 33.6, 4.6, 2.3), 'melon', 'p'),
], qty='142 g box', aisle='produce')

# 4 spinach: a bunch of green leaves (deeper green veins) at uneven heights and angles on teal stems, gathered by a pale band
# (melon leaves keep it apart from the mint cabbage)


def _c2_spleaf(l, w, s):
    """smooth oval leaf blade with a soft point, from (0, -s) to (0, -l)"""
    b = l - s
    return (f'M0 {fmt(-s)}C{fmt(-w * .8)} {fmt(-s - b * .06)} {fmt(-w)} {fmt(-s - b * .3)} {fmt(-w)} {fmt(-s - b * .52)}'
            f'C{fmt(-w)} {fmt(-s - b * .8)} {fmt(-w * .45)} {fmt(-l + .4)} 0 {fmt(-l)}'
            f'C{fmt(w * .45)} {fmt(-l + .4)} {fmt(w)} {fmt(-s - b * .8)} {fmt(w)} {fmt(-s - b * .52)}'
            f'C{fmt(w)} {fmt(-s - b * .3)} {fmt(w * .8)} {fmt(-s - b * .06)} 0 {fmt(-s)}Z')


_C2_SP = [(-50, 19.6, 4.6, 8.6, -1.6), (-22, 30.4, 7, 10.8, -.8), (6, 28, 6.8, 10.6, .2), (34, 25.4, 4.8, 9.6, 1), (58, 16.4, 4.4, 7.6, 1.4)]


def _c2_spinach():
    out = []
    for a, l, w, s, dx in _C2_SP:
        tf = f'translate({fmt(24 + dx)} 40.6) rotate({a})'
        out.append(TUBE(f'M0 -1V{fmt(-s - 1.4)}', 'mint', 3, 'm', tf=tf))
        out.append(F(('path', _c2_spleaf(l, w, s)), 'melon', 'm', band=.3, tf=tf,
                     det=[f'M0 {fmt(-s - 1)}V{fmt(-l + 3)}', f'M0 {fmt(-s - (l - s) * .4)}l{fmt(-w * .4)} -1.9', f'M0 {fmt(-s - (l - s) * .58)}l{fmt(w * .4)} -1.9'],
                     det_t='deep', det_w=1.5))
    return out


add('spinach', 'Spinach & leafy greens', [*_c2_spinach(), F(('rect', 19.4, 33.8, 9.2, 4.4, 2.2), 'mint', 'p')], qty='312 g box', aisle='produce', subs=1)

# 5 green beans: four slender pods fanned out from their stem ends like a hand of cards, each pod bumpy along the top
# where the beans swell, with an upturned pointed tip (green onions and zucchini are straight bundles)
def _c2_pod(L=30, hw=2.9, bumps=5, amp=1.0, k=1.6):
    h = L / 2
    top, bot = [], []
    for i in range(bumps * 2 + 1):
        x = -h + 2.4 + i * (L - 6.4) / (bumps * 2)
        c = k * (x / h) ** 2
        top.append((x, c - hw - (amp if i % 2 else 0)))
    for i in range(7):
        x = h - 3.6 - i * (L - 5.6) / 6
        bot.append((x, k * (x / h) ** 2 + hw * .85))
    return smooth([(-h - .6, k * .2)] + top + [(h - .4, k - hw * .9), (h + 2.8, k - hw - 1.6), (h - .6, k + hw * .2)] + bot)


add('green_beans', 'Green beans', [
    *[F(('path', _c2_pod(L, hw, 5, .8, 1.6)), 'melon', 'm', band=.34, sband=False, tf=f'translate(7.6 {fmt(y)}) rotate({a}) translate({fmt(L / 2 + .6)} 0)',
        det=[f'M{fmt(-L / 2 + (14 if hw > 3 else 4))} {fmt(.5 if hw <= 3 else .2)}Q{fmt(3 if hw > 3 else 0)} {fmt(-.3)} {fmt(L / 2 - 4)} {fmt(1.4)}'], det_t='p', det_w=1.5)
      for y, a, L, hw in [(28.6, -34, 31, 2.8), (31.4, -22, 31.6, 2.8), (34.4, -10, 31.6, 2.9), (37.8, 1, 31.2, 3.8)]],
    *[F(('ellipse', 7.6, y, 1.6, 2.2, a), 'melon', 'deep', ol=1.5) for y, a in [(28.4, -34), (31.2, -22), (34.2, -10), (37.6, 1)]],
], qty='454 g', aisle='produce')

# 6 asparagus: five spears fanning up out of a tight bundle, pointed scaly tips with purple scale marks, a grape band


def _c2_front_spear(top=7, hw=5):
    """the front spear, 10u wide, its rounded scaly tip part of the same shape (so the gloss is one curved run)"""
    body = (f'M{fmt(24 - hw)} {fmt(top + 9)}C{fmt(24 - hw)} {fmt(top + 3)} {fmt(24 - 2.4)} {fmt(top - .4)} 24 {fmt(top - 1.4)}'
            f'C{fmt(24 + 2.4)} {fmt(top - .4)} {fmt(24 + hw)} {fmt(top + 3)} {fmt(24 + hw)} {fmt(top + 9)}V39.6Q{fmt(24 + hw)} 41.8 {fmt(24 + hw - 2.2)} 41.8'
            f'H{fmt(24 - hw + 2.2)}Q{fmt(24 - hw)} 41.8 {fmt(24 - hw)} 39.6Z')
    return [F(('path', body), 'melon', 'm', band=.14,
              det=[f'M{fmt(24 - 2.6)} {fmt(top + 6.4)}L24 {fmt(top + 3.8)}L{fmt(24 + 2.6)} {fmt(top + 6.4)}', f'M{fmt(24 - 3.2)} {fmt(top + 10.4)}L24 {fmt(top + 7.8)}L{fmt(24 + 3.2)} {fmt(top + 10.4)}',
                   f'M{fmt(24 + hw - 2.1)} {fmt(top + 18)}l-1.2 1.1', f'M{fmt(24 + hw - 2.1)} {fmt(top + 25)}l-1.2 1.1'], det_t='#9B7CDE', det_w=1.5)]


def _c2_spear(a, top, hw=2.4):
    tf = f'rotate({a} 24 54)'
    th = hw + .8
    tip = (f'M{fmt(24 - th)} {fmt(top + 7.4)}C{fmt(24 - th - .3)} {fmt(top + 2.6)} {fmt(24 - 1.4)} {fmt(top - .4)} 24 {fmt(top - 1.6)}'
           f'C{fmt(24 + 1.4)} {fmt(top - .4)} {fmt(24 + th + .3)} {fmt(top + 2.6)} {fmt(24 + th)} {fmt(top + 7.4)}Q24 {fmt(top + 9)} {fmt(24 - th)} {fmt(top + 7.4)}Z')
    return [F(('path', f'M{fmt(24 - hw)} {fmt(top + 5)}H{fmt(24 + hw)}V40Q{fmt(24 + hw)} 41.8 24 41.8Q{fmt(24 - hw)} 41.8 {fmt(24 - hw)} 40Z'),
              'melon', 'm', band=.14, tf=tf, sband=False),
            F(('path', tip), 'melon', 'm', ol=1.6, tf=tf,
              det=[f'M{fmt(24 - th + 1.8)} {fmt(top + 5.2)}L24 {fmt(top + 3.4)}L{fmt(24 + th - 1.8)} {fmt(top + 5.2)}', f'M{fmt(24 - .8)} {fmt(top + 1.8)}L24 {fmt(top + 1)}L{fmt(24 + .8)} {fmt(top + 1.8)}'],
              det_t='#9B7CDE', det_w=1.3)]


add('asparagus', 'Asparagus', [
    *_c2_spear(-17, 11.6), *_c2_spear(17, 11.6), *_c2_spear(-8.6, 8.6), *_c2_spear(8.6, 8.6), *_c2_front_spear(7.4, 5),
    F(('rect', 15.4, 31.4, 17.2, 4.2, 2.1), 'grape', 'm'),
], qty='1 bunch', aisle='produce')

# 7 basil: one sprig, a stem with opposite pairs of broad cupped leaves and a small top cluster


def _c2_basil_leaf(x, y, a, l, w):
    tf = f'translate({fmt(x)} {fmt(y)}) rotate({a})'
    return F(('path', _c2_leaf(l, w)), 'melon', 'm', band=.28, tf=tf,
             det=[f'M0 -1.6V{fmt(-l * .76)}', f'M{fmt(-w * .36)} {fmt(-l * .32)}Q{fmt(-w * .58)} {fmt(-l * .56)} {fmt(-w * .24)} {fmt(-l * .72)}'],
             det_t='p', det_w=1.2)


add('basil', 'Basil', [
    TUBE('M24 40.2C24 32 24.4 22 23.8 13', 'melon', 3.4, 'm'),
    _c2_basil_leaf(23.8, 14.4, 0, 8.4, 4.2),
    _c2_basil_leaf(23.8, 16.4, -24, 9.4, 4.6), _c2_basil_leaf(23.8, 16.4, 24, 9.4, 4.6),
    _c2_basil_leaf(24, 25, -44, 13.6, 6.4), _c2_basil_leaf(24, 25, 44, 13.6, 6.4),
    _c2_basil_leaf(24, 34.6, -60, 16, 7.2), _c2_basil_leaf(24, 34.6, 60, 16, 7.2),
], qty='1 bunch', aisle='produce')

# 8 parsley: a loose bunch, thin stems fanning out to frilly, toothed flat-leaf clusters


def _c2_frill(cx, cy, R, a0, seed):
    import random as _r
    g = _r.Random(seed)
    pts = []
    n = 30
    for i in range(n):
        th = _c2m.radians(a0) + i * 2 * _c2m.pi / n
        lobe = .74 + .26 * abs(_c2m.cos(2.5 * (th - _c2m.radians(a0))))
        tooth = 1.0 if i % 2 else .86
        r = R * lobe * tooth * (1 + g.uniform(-.04, .04))
        pts.append((cx + r * _c2m.cos(th), cy + r * _c2m.sin(th)))
    return _c2_poly(pts)


_C2_PS = [(18.6, 27.6, 6.2, 5), (30.2, 28, 6, 9), (13.6, 19.4, 7.4, 3), (34.2, 18.8, 7.8, 7), (23.8, 13.6, 8.6, 11)]
add('parsley', 'Parsley & cilantro', [
    *[TUBE(f'M24 40.6Q{fmt((24 + x) / 2)} {fmt((41 + y) / 2 + 4)} {fmt(x)} {fmt(y + R * .5)}', 'melon', 2.6, 'm') for x, y, R, s in _C2_PS],
    *[F(('path', _c2_frill(x, y, R, -90 + s * 7, s)), 'melon', 'm', band=.26,
        det=[f'M{fmt(x)} {fmt(y + R * .38)}L{fmt(x)} {fmt(y - R * .45)}', *([f'M{fmt(x)} {fmt(y + R * .1)}L{fmt(x - R * .45)} {fmt(y - R * .2)}'] if R < 8 else []),
             f'M{fmt(x)} {fmt(y + R * .1)}L{fmt(x + R * .45)} {fmt(y - R * .2)}'], det_t='p', det_w=1.2) for x, y, R, s in _C2_PS],
], qty='1 bunch', aisle='produce')

# 9 bagel: one golden sesame bagel in 3/4 view, the hole showing its inner wall
_C2_SEED = [(14.4, 26.2, -30), (17.8, 23.2, 10), (22.4, 21.8, -10), (28.4, 22, 20), (32.8, 24, -20), (35, 27.6, 30),
            (13, 30.6, 15), (16, 33.2, -25), (32, 33.2, 25), (35.2, 31.4, -10), (20.6, 34.6, 5), (27.2, 34.8, -15)]
add('bagel', 'Bagels', [
    F(('ellipse', 24, 31, 17.6, 11), 'peach', 'm', band=.3),
    F(('ellipse', 24, 28.4, 5.4, 2.8), 'peach', 'deep', ol=1.6, cut=False),
    F(('ellipse', 24, 29.4, 4.2, 1.8), 'peach', 'w', ol=0, cut=False),
    *[F(('ellipse', x, y, 1.3, .8, a), 'peach', 'w', ol=0, cut=False) for x, y, a in _C2_SEED],
], qty='6 pack', aisle='bakery')

# 10 buns: two soft round golden hamburger buns, split to show the crumb, the front one with sesame seeds


def _c2_bun(cx, bot, w, seeds=False):
    l, r = cx - w / 2, cx + w / 2
    hs, ht = w * .24, w * .62
    yb = bot - hs
    out = [F(('path', f'M{fmt(l)} {fmt(yb)}H{fmt(r)}V{fmt(bot - 2.8)}Q{fmt(r)} {fmt(bot)} {fmt(r - 2.8)} {fmt(bot)}H{fmt(l + 2.8)}Q{fmt(l)} {fmt(bot)} {fmt(l)} {fmt(bot - 2.8)}Z'),
             'peach', 'm', band=.36),
           F(('rect', l + .4, yb - 2.2, w - .8, 3.4, 1.7), 'lemon', 'lp', ol=1.4),
           F(('path', f'M{fmt(l - .8)} {fmt(yb - 1.6)}C{fmt(l - .8)} {fmt(yb - ht)} {fmt(r + .8)} {fmt(yb - ht)} {fmt(r + .8)} {fmt(yb - 1.6)}'
                      f'Q{fmt(r + .8)} {fmt(yb + .4)} {fmt(r - 1.2)} {fmt(yb + .4)}H{fmt(l + 1.2)}Q{fmt(l - .8)} {fmt(yb + .4)} {fmt(l - .8)} {fmt(yb - 1.6)}Z'),
             'peach', 'm', band=.2)]
    if seeds:
        out += [F(('ellipse', cx + dx * w, yb - ht * dy, 1.3, .8, a), 'peach', 'w', ol=0, cut=False)
                for dx, dy, a in [(-.24, .52, -30), (-.04, .66, 0), (.16, .6, 25), (.3, .4, 40), (-.34, .3, -40), (.06, .42, 10), (-.14, .38, -15), (.2, .3, 20)]]
    return out


add('buns', 'Buns & rolls', [*_c2_bun(31.6, 37, 18.4), *_c2_bun(18.4, 42, 22.8, seeds=True)], qty='8 pack', aisle='bakery')

# 11 pita & naan: a round flatbread disc lying behind, a half pita standing in front of it with its cut edge on top and
# the pocket open (the disc and the D read as bread at 22 dp; potatoes are lumpy ovals with eyes, cookies have chips)
_C2_PITA_D = 'M-9.6 -6.6H9.6Q12 -6.6 12 -4.2C12 2.2 6.8 7 0 7C-6.8 7 -12 2.2 -12 -4.2Q-12 -6.6 -9.6 -6.6Z'
_C2_PTF = 'translate(29.4 34.6) rotate(10)'
add('pita', 'Pita & naan', [
    F(('ellipse', 21.4, 23.2, 15, 10.2), 'cocoa', 'm'),
    F(('ellipse', 21.4, 20.8, 15, 10.2), 'cocoa', 'p'),
    *[F(('path', _c2_bubble(x, y, r)), 'cocoa', 'm', ol=0, cut=False) for x, y, r in [(27.6, 15.2, 2.6), (31.6, 21.4, 2.3), (22.4, 19.2, 2.1)]],
    F(('path', _C2_PITA_D), 'cocoa', 'p', band=.3, tf=_C2_PTF),
    F(('path', 'M-9.4 -4.6C-5.4 -.2 5.4 -.2 9.4 -4.6C5.4 -3.2 -5.4 -3.2 -9.4 -4.6Z'), 'cocoa', 'deep', ol=0, cut=False, tf=_C2_PTF,
      det=['M-9.4 -4.6C-5.4 -.2 5.4 -.2 9.4 -4.6'], det_t='tr', det_w=2),
    *[F(('path', _c2_bubble(x, y, r)), 'cocoa', 'm', ol=0, cut=False, tf=_C2_PTF) for x, y, r in [(-3.4, 2.8, 1.8), (4.6, 2.2, 1.9)]],
], qty='4 pack', aisle='bakery')

# 12 cake: a round white-frosted layer cake, pink glaze dripping over the top edge, one cherry


def _c2_drip(x0, x1, cy, rx, ry, drips):
    """glaze over a top ellipse (cy, rx, ry), with drips (x, depth, half-width) hanging from its front edge"""
    cx = (x0 + x1) / 2
    edge = lambda x: cy + ry * _c2m.sqrt(max(0, 1 - ((x - cx) / rx) ** 2)) + 1.4
    s = f'M{fmt(x0)} {fmt(cy)}C{fmt(x0)} {fmt(cy - ry * 1.3)} {fmt(x1)} {fmt(cy - ry * 1.3)} {fmt(x1)} {fmt(cy)}'
    s += f'L{fmt(x1)} {fmt(edge(x1 - .01) + .6)}'
    for x, d, hw in sorted(drips, reverse=True):
        y0 = edge(x)
        s += (f'Q{fmt(x + hw + .6)} {fmt(edge(x + hw + .6))} {fmt(x + hw)} {fmt(y0 + 1)}V{fmt(y0 + d - hw)}'
              f'A{fmt(hw)} {fmt(hw)} 0 0 1 {fmt(x - hw)} {fmt(y0 + d - hw)}V{fmt(y0 + 1)}')
    s += f'Q{fmt(x0 + .4)} {fmt(edge(x0 + .4))} {fmt(x0)} {fmt(cy + .6)}Z'
    return s


add('cake', 'Cake', [
    F(cyl(9.4, 38.6, 20.6, 42, 4.6), 'sakura', 'w', band=.2, band_t='p', det=['M11.8 31.1Q24 34.5 36.2 31.1'], det_t='p', det_w=1.6),
    F(('path', _c2_drip(9.4, 38.6, 20.6, 14.6, 4.6, [(12.8, 4.4, 1.6), (18.4, 7, 1.8), (24.6, 3.6, 1.6), (30.4, 6.2, 1.8), (35.6, 3.8, 1.5)])),
      'sakura', 'm'),
    LINE('M24.4 10Q25.6 7 28.6 6.4', 'strawberry', 1.5),
    F(('circle', 24, 13, 3.8), 'strawberry', 'm', band=.3),
], qty='1 cake', aisle='bakery')

# 13 salami: a chubby log in a string-tied cocoa casing, its cut face red with white fat specks, two loose slices
_C2_SAL = 'translate(23.4 20.2) rotate(-20)'
_C2_SPK = lambda x, y, r: _c2_dots([(x + r * dx, y + r * dy) for dx, dy in [(-.45, -.35), (.3, -.5), (.5, .2), (-.1, .1), (-.5, .45), (.15, .6)]])
add('salami', 'Salami & pepperoni', [
    F(('path', 'M-11.4 -7.8H9.6V7.8H-11.4C-14.6 7.8 -16.6 4 -16.6 0C-16.6 -4 -14.6 -7.8 -11.4 -7.8Z'), 'cocoa', 'm', band=.28, sband=False, tf=_C2_SAL,
      tex=['M-9.6 -9V9', 'M-2.8 -9V9', 'M4 -9V9'], tex_t='p', tex_w=1.4),
    F(('ellipse', 9.6, 0, 4.4, 7.8), 'strawberry', 'm', tf=_C2_SAL, tex=[_C2_SPK(9.6, 0, 5.4)], tex_t='w', tex_w=1.6),
    LINE('M-16.4 0H-18.6', 'cocoa', 1.6, tf=_C2_SAL),
    F(('circle', 33.4, 33.2, 7.6), 'cocoa', 'm'),
    F(('circle', 33.4, 33.2, 5.6), 'strawberry', 'm', ol=1.4, cut=False, tex=[_C2_SPK(33.4, 33.2, 5.4)], tex_t='w', tex_w=1.6),
    F(('circle', 21, 35.4, 6.6), 'cocoa', 'm'),
    F(('circle', 21, 35.4, 4.8), 'strawberry', 'm', ol=1.4, cut=False, tex=[_C2_SPK(21, 35.4, 4.6)], tex_t='w', tex_w=1.6),
], qty='250 g', aisle='deli')

# 14 shredded cheese: a clear resealable pouch (white, ramune zip and slider) tilted a little, packed with curly yellow
# and white shreds
_C2_SDT = 'translate(24 24.4) rotate(-9)'
add('shredded_cheese', 'Shredded cheese', [
    F(('path', 'M-13.6 -12.4H13.6C15.6 -4 15.8 6.4 14.2 13.6Q13.6 16 11 16H-11Q-13.6 16 -14.2 13.6C-15.8 6.4 -15.6 -4 -13.6 -12.4Z'),
      'ramune', 'w', band=.16, band_t='p', tf=_C2_SDT),
    F(('path', 'M-11.6 -2.6C-8.4 -5.6 -5 -3.2 -2.6 -5.4C0 -7.6 3.6 -4.8 6 -6.4C8.4 -7.8 11.4 -6 11.8 -3.4C12.6 2.4 12.6 8 11.8 11.6Q11.4 13.4 9.4 13.4H-9.4Q-11.4 13.4 -11.8 11.6C-12.6 8 -12.4 2 -11.6 -2.6Z'),
      'lemon', 'p', ol=1.5, cut=False, tf=_C2_SDT, tex=_c2_squiggles(-14, -7, 13, 15), tex_t='m', tex_w=1.8),
    LINE('M-11.6 -8.6H11.6', 'ramune', 1.6, t='m', cut=False, tf=_C2_SDT),
    F(('rect', 7.8, -11.4, 5, 5.6, 2), 'ramune', 'm', ol=1.5, tf=_C2_SDT),
    TUBE('M35.4 39.4c1-1.8 2.2-1.8 2.8-.2s1.4 1.6 2.4 .2', 'lemon', 3, 'm'),
    TUBE('M37.2 33.8c.8-1.4 1.8-1.6 2.6-.4', 'lemon', 3, 'm'),
], qty='320 g bag', aisle='deli')

# 15 brie: a white-rinded wheel seen a little from above, a wedge cut from the front, the cut faces creamy lemon
_C2_BR = dict(cx=24, ty=20.6, rx=17, ry=7.6, a0=58, a1=122)


def _c2_brie():
    cx, ty, rx, ry, a0, a1 = (_C2_BR[k] for k in ('cx', 'ty', 'rx', 'ry', 'a0', 'a1'))
    by = 42 - ry * _c2m.sin(_c2m.radians(a0))
    pt = lambda a, y: (cx + rx * _c2m.cos(_c2m.radians(a)), y + ry * _c2m.sin(_c2m.radians(a)))
    tr, tl, br, bl = pt(a0, ty), pt(a1, ty), pt(a0, by), pt(a1, by)
    body = (f'M{fmt(cx - rx)} {fmt(ty)}A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(cx + rx)} {fmt(ty)}V{fmt(by)}'
            f'A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(br[0])} {fmt(br[1])}L{fmt(cx)} {fmt(by)}L{fmt(bl[0])} {fmt(bl[1])}'
            f'A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(cx - rx)} {fmt(by)}Z')
    top = (f'M{fmt(cx - rx)} {fmt(ty)}A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(cx + rx)} {fmt(ty)}A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(tr[0])} {fmt(tr[1])}'
           f'L{fmt(cx)} {fmt(ty)}L{fmt(tl[0])} {fmt(tl[1])}A{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(cx - rx)} {fmt(ty)}Z')
    dots = _c2_dots([(10.6, 31), (13.6, 36.4), (9.4, 26.4), (37.4, 30.6), (34.4, 36.2), (38.6, 26.6), (14.4, 17.6), (20.6, 15.4), (28.8, 15.6), (33.6, 18.4), (12.4, 22), (35.4, 22.4)])
    return [F(('path', body), 'cocoa', 'w', band=.2, band_t='p', tex=[dots], tex_t='p', tex_w=2),
            F(('path', top), 'cocoa', 'w', ol=1.8, cut=False, tex=[dots], tex_t='p', tex_w=2),
            F(('path', _c2_poly([tl, (cx, ty), (cx, by), bl])), 'cocoa', 'lp', ol=1.6),
            F(('path', _c2_poly([tr, (cx, ty), (cx, by), br])), 'cocoa', 'lp', ol=1.6,
              det=[_c2_dots([(cx + 3, ty + 8), (cx + 5.4, ty + 13), (cx - 4, ty + 11.4)])], det_t='#FFDA4A', det_w=1.8)]


add('brie', 'Brie & soft cheese', _c2_brie(), qty='200 g wheel', aisle='deli')

# 16 mozzarella: two white balls floating in a blue deli tub of whey (on a plate they read as eggs or rice balls), one
# basil leaf on top
add('mozzarella', 'Mozzarella', [
    F(('ellipse', 24, 23.6, 17, 5.6), 'ramune', 'p'),
    F(('circle', 19.4, 22.4, 7.6), 'ramune', 'w', band=.3, band_t='p'),
    F(('circle', 31.2, 24.4, 5.6), 'ramune', 'w', band=.3, band_t='p'),
    F(('path', 'M7 23.6C7 27.2 14.6 29.8 24 29.8C33.4 29.8 41 27.2 41 23.6L38.6 38.8Q38.2 42 35 42H13Q9.8 42 9.4 38.8Z'), 'ramune', 'm', band=.24, band_t='deep'),
    F(('path', _c2_leaf(10, 4.6)), 'melon', 'm', band=.3, tf='translate(21.6 17.6) rotate(56)', det=['M0 -1.4V-8.4'], det_t='p', det_w=1.5),
], qty='200 g tub', aisle='deli')

# 17 parmesan: a tall chunk standing upright, its craggy broken face rising to a thick cocoa rind stamped with pin dots,
# lemon crystal flecks, a little heap of grated parmesan at its foot (cheddar is the low wide wedge with a peach rind)
add('parmesan', 'Parmesan', [
    F(('path', 'M27.6 9.6Q29.6 7.4 32.6 7.8Q37.4 8.4 37.8 13L38.6 38.4Q38.6 42 35 42H27.4Z'), 'cocoa', 'm', band=.2, band_t='deep',
      tex=[_c2_dots([(30.6, 12.4), (34.6, 12.6), (30.8, 17.6), (34.8, 17.8), (31, 22.8), (35, 23), (31.2, 28), (35.2, 28.2), (31.4, 33.2), (35.4, 33.4)])], tex_t='p', tex_w=2),
    F(('path', smooth([(11.2, 39.4), (12.2, 33.6), (14.8, 30.4), (15.4, 25.6), (18.4, 22.4), (19.2, 17.6), (22.6, 15), (24, 10.8), (27.6, 9), (30.2, 10.4)],
                      closed=False) + 'L30.4 42H13.6Q11 42 11.2 39.4Z'), 'lemon', 'p', band=.18, band_t='m',
      det=[_c2_dots([(21.4, 21.6), (25.6, 17.4), (19.4, 29.4), (25.4, 26.4), (22.6, 34.6), (27, 33.4)])], det_t='m', det_w=1.8),
    F(('path', 'M5.8 41.2C5.8 37.8 8.4 35.6 11.6 35.6C14.8 35.6 17.6 37.8 17.6 41.2Q17.6 42 16.6 42H6.8Q5.8 42 5.8 41.2Z'), 'lemon', 'p', band=.3, band_t='m',
      tex=['M7.8 39.6q1.1-1.3 2.2 0M10.8 37.8q1.1-1.3 2.2 0M13.6 39.8q1.1-1.3 2.2 0'], tex_t='#DAB632', tex_w=1.4),
], qty='250 g wedge', aisle='deli')

# 18 hummus: a tapered white tub, the deeply swirled beige top seen a little from above, a pita chip standing in the dip,
# a golden olive-oil pool with three chickpeas, red paprika
add('hummus', 'Hummus & dips', [
    F(('path', 'M7 26H41L38 38.8Q37.4 42 34 42H14Q10.6 42 10 38.8Z'), 'cocoa', 'w', band=.26, band_t='p'),
    F(('path', 'M28.6 24Q27 23.8 27.8 22.4L33.6 9.4Q34.6 7.6 35.6 9.4L40.2 20.6Q40.8 22.2 39.2 22.4Z'), 'cocoa', 'p', band=.3, band_t='m',
      tex=['M36 16h.1M36.6 19h.1M34 20.6h.1'], tex_t='m', tex_w=2),
    F(('ellipse', 24, 26, 17, 7.6), 'cocoa', 'm'),
    F(('path', 'M9.4 26.4C9.4 21.4 15.6 18.4 24 18.4C32.4 18.4 38.6 21.4 38.6 26.4C34.6 29.6 13.4 29.6 9.4 26.4Z'), 'cocoa', 'p', ol=1.6, cut=False,
      det=['M25 21.6C30.6 21.2 33.4 23.4 31.2 25.8C28.2 28.6 18.6 28.2 16.8 25.4C15.2 22.6 19.4 20.4 26.2 20.6'], det_t='m', det_w=2.6),
    F(('ellipse', 24.4, 24.6, 4.6, 2), 'lemon', 'm', ol=1.2, cut=False),
    *[F(('circle', x, y, 1.6), 'cocoa', 'p', ol=1.2, cut=False) for x, y in [(27.6, 24.2), (29.8, 25.8), (26.4, 26.6)]],
    *[F(('circle', x, y, 1.05), 'strawberry', 'm', ol=0, cut=False) for x, y in [(13.4, 25.2), (34.6, 24.4), (18.6, 21.4), (21, 27.6), (17.4, 27.8), (35.4, 27.2), (30.4, 21)]],
], qty='227 g tub', aisle='deli')

# 19 pork chop: the cartoon chop, a comma with its round head at the upper left and its tail sweeping down to the right,
# a thick pale-pink fat cap (it must not read as the white border), the round marrow bone in the narrow end
_C2_CH = [(7.2, 22), (10.4, 13.4), (18.6, 8.8), (27.8, 10.4), (32.4, 16.8), (33.8, 24), (36.6, 30.6), (41.4, 35.6), (40.8, 40.4),
          (33.2, 41.6), (24.6, 38.6), (17.2, 34.4), (10.4, 29.8)]
_C2_CHI = [(12, 21.4), (14.4, 15.6), (19.8, 13.2), (25.6, 14.4), (28.8, 19.2), (29.6, 25.4), (32.2, 31.6), (36.6, 36.4), (35.4, 38.2),
           (30.4, 37.4), (24.6, 35), (18.4, 31.4), (13.6, 27.4)]
add('pork_chop', 'Pork chops', [
    F(('path', smooth(_C2_CH)), 'sakura', 'p'),
    F(('path', smooth(_C2_CHI)), 'sakura', 'm', ol=1.6, cut=False, band=.26, det=['M16.4 22.4Q20 18.6 24.6 19.6', 'M19.6 27.6Q23.4 25 27.4 26.4'], det_t='p', det_w=1.6),
    F(('circle', 32.6, 34.4, 3.4), 'sakura', 'w', ol=1.6, cut=False),
    F(('circle', 32.6, 34.4, 1.2), 'sakura', 'p', ol=0, cut=False),
], qty='4 chops', aisle='meat')

# 20 hot dogs: a grilled frank in a split golden bun, a squiggle of mustard
_C2_HD = 'translate(24 32.4) rotate(-14)'
add('hot_dogs', 'Hot dogs', [
    F(('path', 'M-16.4 -1.4C-16.4 -6.4 -12.6 -8.6 -8.6 -8.6H8.6C12.6 -8.6 16.4 -6.4 16.4 -1.4Z'), 'peach', 'p', tf=_C2_HD),
    F(('path', 'M-18.6 -3C-18.6 -6.4 -16 -7.6 -13.6 -7.6H13.6C16 -7.6 18.6 -6.4 18.6 -3C18.6 .4 16 1.6 13.6 1.6H-13.6C-16 1.6 -18.6 .4 -18.6 -3Z'),
      'peach', 'deep', tf=_C2_HD, det=['M-8 -6.4l1.6 2.4M-2 -6.4l1.6 2.4M4 -6.4l1.6 2.4'], det_t='tr', det_w=1.2),
    LINE('M-12 -4Q-10 -6.4 -8 -4T-4 -4T0 -4T4 -4T8 -4T12 -4', 'lemon', 2.2, t='lp', cut=False, tf=_C2_HD),
    F(('path', 'M-16.6 -1.6C-16.6 3.8 -13 7.4 -8.4 7.4H8.4C13 7.4 16.6 3.8 16.6 -1.6C10.6 1 -10.6 1 -16.6 -1.6Z'), 'peach', 'p', band=.36, tf=_C2_HD),
], qty='12 franks', aisle='meat')

# 21 whole turkey: a wide golden roast in 3/4 view on a lemon platter, the near drumstick in front at the right shoulder
# and the far one behind at the left, each with a white bone running up and out to its paper frill, pale roast streaks
# on the breast (two front drumsticks read as an owl's eyes; the far leg behind keeps the gloss run whole)
def _c2_leg(x, y, a):
    tf = f'translate({fmt(x)} {fmt(y)}) rotate({a})'
    return [TUBE('M0 -3.6V-9.6', 'cocoa', 2.4 + 2, 'w', tf=tf),
            F(('path', 'M-3 -9.2H3L3.8 -12.8L2.4 -11.6L1.2 -13.8L0 -11.8L-1.2 -13.8L-2.4 -11.6L-3.8 -12.8Z'), 'cocoa', 'w', ol=1.5, tf=tf),
            F(('ellipse', 0, 0, 4.4, 6), 'cocoa', 'm', band=.3, tf=tf)]


add('whole_turkey', 'Turkey', [
    F(('path', 'M5.6 36.4H42.4Q42.4 42 37 42H11Q5.6 42 5.6 36.4Z'), 'lemon', 'p', band=.4),
    *_c2_leg(15.6, 26.2, -32),
    F(('ellipse', 23.4, 27.8, 15.4, 10.2), 'cocoa', 'm', band=.22,
      det=['M18.4 22.6Q21 21 23.6 22.6', 'M15.6 27.4Q20 25.2 24.4 27.4', 'M17.4 32.2Q20.6 30.8 23.8 32.2'], det_t='p', det_w=1.6),
    *_c2_leg(32.4, 29.4, 36),
], qty='6 kg', aisle='meat')

# 22 white fish fillet: a long pale fillet (periwinkle pastel reads as white fish) tapering to a narrow notched tail,
# chevron flakes, a lemon wedge resting on it
_C2_FF = 'translate(23.6 27) rotate(-20)'
_C2_FW = 'translate(0 2.6) rotate(-10 31.9 34.4)'
add('fish_fillet', 'White fish fillets', [
    F(('path', 'M-16.72 -.7C-16.72 -7.57 -9.68 -9.33 -1.94 -8.27C4.93 -7.39 10.21 -4.75 14.08 -2.99L17.95 -4.05Q19.18 -4.4 18.83 -3.17L17.6 0L18.83 3.17'
               'Q19.18 4.4 17.95 4.05L14.08 2.99C10.21 4.75 4.93 7.74 -1.94 8.45C-9.68 9.33 -16.72 5.98 -16.72 -.7Z'),
      'periwinkle', 'p', band=.3, sband=False, tf=_C2_FF,
      tex=['M-11.1 -8.8L-7.9 0L-11.1 8.8', 'M-4.75 -8.8L-1.6 0L-4.75 8.8', 'M1.6 -8.8L4.75 0L1.6 8.8', 'M7.6 -7L10 0L7.6 7'], tex_t='m', tex_w=1.6),
    F(('path', 'M24.4 31.4H39.4A7.5 7.5 0 0 1 24.4 31.4Z'), 'lemon', 'm', ol=1.8, tf=_C2_FW),
    F(('path', 'M26.4 32.4H37.4A5.5 5.5 0 0 1 26.4 32.4Z'), 'lemon', 'lp', ol=0, cut=False, tf=_C2_FW,
      det=['M27.15 33.8A4.9 4.9 0 0 0 36.65 33.8', 'M31.9 32.8V36.6', 'M31.7 32.8L29.2 35.4', 'M32.1 32.8L34.6 35.4'], det_t='w', det_w=1.5),
], qty='400 g', aisle='seafood')

# 23 shrimp: a plump curled peach shrimp, striped segments, a tail fan, a little eye and feelers


def _c2_shrimp(cx=22.6, cy=27.2, r0=18, r1=14, i0=7.6, i1=5.6, a0=-40, sweep=262):
    outer, inner, stripes = [], [], []
    n = 12
    for i in range(n + 1):
        t = i / n
        a = _c2m.radians(a0 - sweep * t)
        ro, ri = r0 + (r1 - r0) * t, i0 + (i1 - i0) * t
        outer.append((cx + ro * _c2m.cos(a), cy + ro * _c2m.sin(a)))
        inner.append((cx + ri * _c2m.cos(a), cy + ri * _c2m.sin(a)))
        if 1 <= i <= n - 1 and i % 2 == 0:
            stripes.append(f'M{fmt(cx + (ri - 2) * _c2m.cos(a))} {fmt(cy + (ri - 2) * _c2m.sin(a))}L{fmt(cx + (ro + 2) * _c2m.cos(a))} {fmt(cy + (ro + 2) * _c2m.sin(a))}')
    a = _c2m.radians(a0)
    head = (cx + (r0 + i0) / 2 * _c2m.cos(a) + 2.8, cy + (r0 + i0) / 2 * _c2m.sin(a) + 1.6)
    body = smooth([head] + outer[1:] + inner[::-1][:-1])
    a = _c2m.radians(a0 - sweep)
    tail = (cx + (r1 + i1) / 2 * _c2m.cos(a), cy + (r1 + i1) / 2 * _c2m.sin(a))
    return body, stripes, tail, head


_C2_SH, _C2_SHS, _C2_ST, _C2_SHD = _c2_shrimp()
_C2_STF = f'translate({fmt(_C2_ST[0] + .4)} {fmt(_C2_ST[1] - .4)}) rotate(-34)'
add('shrimp', 'Shrimp', [
    LINE(f'M{fmt(_C2_SHD[0] - 2)} {fmt(_C2_SHD[1] - 3)}C{fmt(_C2_SHD[0] + 1)} {fmt(_C2_SHD[1] - 7)} {fmt(_C2_SHD[0] + 3)} {fmt(_C2_SHD[1] - 8.4)} 41.4 6.8'
         f'M{fmt(_C2_SHD[0] - .4)} {fmt(_C2_SHD[1] - 1.6)}C{fmt(_C2_SHD[0] + 1.6)} {fmt(_C2_SHD[1] - 3.6)} 39 {fmt(_C2_SHD[1] - 3.6)} 41.6 {fmt(_C2_SHD[1] - 2)}', 'peach', 1.3),
    F(('path', 'M-1 -2.4L8.4 -5.6Q11.2 0 8.4 5.6L-1 2.4Z'), 'peach', 'm', tf=_C2_STF, det=['M1 0H8.4', 'M1 -.6L7.6 -3.6', 'M1 .6L7.6 3.6'], det_t='p', det_w=1.2),
    F(('path', _C2_SH), 'peach', 'm', band=.22, tex=_C2_SHS, tex_t='p', tex_w=2.2),
    F(('circle', _C2_SHD[0] - 4.2, _C2_SHD[1] + .2, 1.4), 'peach', 'tr', ol=0, cut=False),
], qty='340 g bag', aisle='seafood')

# 24 mussels: two closed blue-black mussels, one open half-shell in front showing its pearly lining and orange meat
_C2_MSB = 'M0 0C-1.8 -4 -7 -10 -7 -15.8C-7 -20.4 -3.6 -23 0 -23C4.2 -23 7 -19.4 6.8 -14.6C6.6 -8.6 3.2 -3.2 0 0Z'


def _c2_mussel(x, y, a):
    return F(('path', _C2_MSB), 'periwinkle', 'deep', band=.28, band_t='tr', tf=f'translate({fmt(x)} {fmt(y)}) rotate({a})',
             det=['M0 -2.4V-21.4', 'M-5 -17Q-3.6 -20.2 0 -20.6', 'M-3.6 -9.4Q-5 -14.8 -1.6 -17.8'], det_t='p', det_w=1.2)


_C2_MOP = 'translate(31.2 41.4) rotate(-62)'
add('mussels', 'Mussels & clams', [
    _c2_mussel(19, 38.4, 30), _c2_mussel(41.2, 40.6, -36),
    F(('path', 'M0 0C-2.2 -4.6 -8.6 -11.6 -8.6 -18.4C-8.6 -24 -4.4 -27 0 -27C5.2 -27 8.6 -22.8 8.4 -17C8.2 -10 4 -3.8 0 0Z'), 'periwinkle', 'deep', band=.2, band_t='tr', tf=_C2_MOP),
    F(('path', 'M0 -2.6C-1.8 -6.4 -6.8 -12.4 -6.8 -18.2C-6.8 -22.6 -3.4 -24.8 0 -24.8C4 -24.8 6.6 -21.4 6.4 -16.8C6.2 -11 3.2 -6 0 -2.6Z'), 'periwinkle', 'p', ol=1.5, cut=False, tf=_C2_MOP),
    F(('path', 'M0 -5.4C-1.4 -8.4 -5 -13 -5 -17.6C-5 -21 -2.6 -22.6 0 -22.6C3 -22.6 4.8 -20 4.6 -16.6C4.4 -12.2 2.4 -8.2 0 -5.4Z'), 'peach', 'm', ol=1.4, cut=False, tf=_C2_MOP,
      det=['M-2.4 -16.6Q0 -13.6 2.2 -16.6', 'M-1.6 -12Q0 -10.2 1.6 -12'], det_t='p', det_w=1.2),
], qty='908 g bag', aisle='seafood')

# 25 lobster: a red cooked lobster seen from above, two big claws, segmented tail with a fan, long feelers
_C2_CLAW = 'M-5 6C-7.2 0 -6.6 -6.6 -3 -10C-2 -10.8 -.6 -10.4 -.8 -9L-1.4 -4.6L1.2 -4.4L2.6 -9.8C3 -11 4.4 -11.2 5 -10C7.2 -5.6 6.8 1.8 3.6 6Z'
add('lobster', 'Lobster & crab', [
    LINE('M22 12.4C19 8.6 13.4 7 8.4 7.8M26 12.4C29 8.6 34.6 7 39.6 7.8', 'strawberry', 1.3),
    LINE('M18.8 22.6l-4.2 2.2M18.8 25.8l-3.8 2.8M29.2 22.6l4.2 2.2M29.2 25.8l3.8 2.8', 'strawberry', 1.6),
    TUBE('M19.6 18.8L14.4 21.6', 'strawberry', 4.4, 'm'),
    TUBE('M28.4 18.8L33.6 21.6', 'strawberry', 4.4, 'm'),
    F(('path', _C2_CLAW), 'strawberry', 'm', band=.3, tf='translate(13 18.4) rotate(-16)'),
    F(('path', _C2_CLAW), 'strawberry', 'm', band=.3, tf='translate(35 18.4) rotate(16) scale(-1 1)'),
    F(('path', 'M24 35.6L18.2 40.2Q17.2 42 19.4 42H28.6Q30.8 42 29.8 40.2Z'), 'strawberry', 'm', det=['M24 37.9V40.3', 'M21.9 39.2L21.6 40.3', 'M26.1 39.2L26.4 40.3'], det_t='p', det_w=1.2),
    *[F(('rect', 24 - w / 2, y, w, 4.6, 2.3), 'strawberry', 'm', band=.34) for y, w in [(32.4, 7.2), (29, 8.4), (25.6, 9.4)]],
    F(('ellipse', 24, 19.6, 6.4, 8), 'strawberry', 'm', band=.22, det=['M18.6 22.4Q24 25 29.4 22.4'], det_t='p', det_w=1.3),
    F(('circle', 21.6, 12.6, 1.5), 'strawberry', 'tr', ol=0), F(('circle', 26.4, 12.6, 1.5), 'strawberry', 'tr', ol=0),
], qty='1 cooked', aisle='seafood')

# 26 milk carton: the gable end of a carton (a little house), sky-blue roof with fold lines, a fin on top, a straight
# blue band across the white body with a little glass of milk on it (a wave is the water bottles' motif; cream is the
# narrow trapezoid gable with a drop)
add('milk_carton', 'Milk', [
    F(('rect', 21.4, 5.6, 5.2, 4.6, 2), 'ramune', 'm', ol=1.6),
    F(('path', 'M10.6 19.4L22.4 8.8Q24 7.4 25.6 8.8L37.4 19.4Z'), 'ramune', 'm', det=['M15 19.2L24 12.6L33 19.2'], det_t='p', det_w=1.4),
    F(('rect', 10.6, 18.6, 26.8, 23.4, 3), 'ramune', 'w', band=.18, band_t='p',
      tex=['M6 30.4H42'], tex_t='m', tex_w=9.2),
    F(('path', 'M20.8 26.6H27.2L26.3 33.6Q26.1 34.6 25.1 34.6H22.9Q21.9 34.6 21.7 33.6Z'), 'ramune', 'w', ol=1.4, cut=False,
      det=['M22.2 28.8H25.8'], det_t='p', det_w=1.4),
], qty='2 L carton', aisle='dairy')

# 27 cream cheese: a foil brick, its left half still wrapped (silver periwinkle with soft creases), torn open to the
# smooth white block, a butter knife stuck upright in its right end, a schmear where the blade goes in
add('cream_cheese', 'Cream cheese', [
    F(('path', 'M35.6 17.2H40L39.6 23.4H35.2Z'), 'periwinkle', 'p', ol=1.8),
    F(('rect', 35.4, 6.8, 5, 11.4, 2.5, 6), 'periwinkle', 'm', band=.3, band_t='deep', det=['M37.6 9.6L37.2 13.4'], det_t='p', det_w=1.5),
    F(('path', 'M22.6 22H37.4Q41 22 41 25.6V38.4Q41 42 37.4 42H22.6Z'), 'periwinkle', 'w', band=.22, band_t='p'),
    F(('path', 'M10.6 22H24.2L22.4 25L24.6 28L22.6 31.2L24.8 34.2L22.8 37.4L24.6 40.4L23.6 42H10.6Q7 42 7 38.4V25.6Q7 22 10.6 22Z'), 'periwinkle', 'p', band=.22,
      det=['M10.6 26.4L14.8 30.8L12.4 35.6', 'M17.4 24.6L19.2 29.4'], det_t='m', det_w=1.5),
    F(('rect', 9, 18.2, 14.6, 4.6, 2.3, -29), 'periwinkle', 'm'),
    F(('path', 'M32.8 23.8C33 21.4 35.4 20.2 37.4 21C39.8 20.4 41.4 22.2 40.6 24.2C39.4 26 35.2 26 32.8 23.8Z'), 'periwinkle', 'w', ol=1.5),
], qty='250 g brick', aisle='dairy')

# 28 plant milk: a tall slim oat-beige carton, green screw cap, an oat stalk with a leaf on its blank front
add('plant_milk', 'Plant milk', [
    F(('rect', 25.6, 5.2, 7.6, 5.4, 2), 'melon', 'm', det=['M27.8 6.8V9M30.8 6.8V9'], det_w=1.2),
    F(('path', 'M16.6 9.6H31.4L34 12.6V38.8Q34 42 30.8 42H17.2Q14 42 14 38.8V12.6Z'), 'cocoa', 'p', band=.16, det=['M14.4 14.4H33.6']),
    F(('rect', 17.4, 18.8, 13.2, 19.2, 3), 'cocoa', 'w', ol=1.4, cut=False),
    LINE('M24 35.6V23.4', 'cocoa', 1.5, t='m', cut=False),
    *[F(('ellipse', 24 + dx, y, 1.5, 2.5, a), 'cocoa', 'm', ol=1.2, cut=False) for dx, y, a in [(0, 22.4, 0), (-2.2, 25.6, -34), (2.2, 25.6, 34), (-2.2, 29.2, -34), (2.2, 29.2, 34)]],
    F(('path', 'M24.2 35.4C26 32.2 28.2 31 30 31.6C29.6 34 27 35.6 24.2 35.4Z'), 'melon', 'm', ol=1.2, cut=False),
], qty='1.89 L carton', aisle='dairy')

# 29 tofu: a white block traced in the tray's blue with pale pores, a smaller cube, standing in a shallow ramune tray of
# water (a cocoa trace made it read as bread or crackers)
add('tofu', 'Tofu', [
    F(('rect', 8.8, 12.4, 22, 22, 3.6), 'ramune', 'w', tex=[_c2_dots([(16.6, 26.6), (24.4, 18.4), (26.2, 25.4), (20.4, 21.4), (14.4, 30.4)])], tex_t='p', tex_w=2),
    F(('rect', 28.4, 21.4, 12, 12, 3), 'ramune', 'w', tex=[_c2_dots([(32, 26.2), (36.6, 29)])], tex_t='p', tex_w=2),
    F(('path', 'M6.4 31.2Q9.4 29.2 12.4 31.2T18.4 31.2T24.4 31.2T30.4 31.2T36.4 31.2T41.6 31V38.4Q41.6 42 38 42H10Q6.4 42 6.4 38.4Z'), 'ramune', 'p', band=.32),
], qty='454 g block', aisle='plant_based')

# 30 veggie burger: a burger with a green veggie patty (flecked with carrot), a frilly leaf, a sesame bun
add('veggie_burger', 'Veggie burgers & meat alternatives', [
    F(('path', 'M8 35.4H40V38.6Q40 42 36.6 42H11.4Q8 42 8 38.6Z'), 'peach', 'm', band=.4),
    F(('rect', 6.4, 27.2, 35.2, 9.4, 4.4), 'melon', 'deep', band=.34, band_t='tr',
      tex=[_c2_dots([(11.6, 31), (17.4, 29.6), (22.6, 32.4), (28.6, 30), (34, 32.2), (14.4, 33.6), (31.4, 33.4)])], tex_t='#FFA477', tex_w=2),
    F(('path', 'M6.6 27.8C8.6 24.6 10.6 27.6 12.6 25.2C14.6 27.8 16.4 24.6 18.6 26.8C20.6 24.6 22.4 27.6 24.4 25.2C26.6 27.6 28.2 24.6 30.4 26.8C32.4 24.6 34.2 27.6 36.2 25.2C38.2 27.8 39.8 25.6 41.4 27.8L40.6 29.4H7.4Z'),
      'melon', 'm', ol=1.6),
    F(('path', 'M8.4 25.6C8.4 16.6 15 11.2 24 11.2C33 11.2 39.6 16.6 39.6 25.6Q39.6 27.4 37.8 27.4H10.2Q8.4 27.4 8.4 25.6Z'), 'peach', 'm', band=.22),
    *[F(('ellipse', x, y, 1.3, .8, a), 'peach', 'w', ol=0, cut=False) for x, y, a in [(17.6, 19.2, -30), (22.8, 17, 0), (28.6, 15.8, 25), (33.2, 19.4, 40), (20, 22.2, -15), (26.6, 20.4, 10), (14.6, 22.8, -40)]],
], qty='4 patties', aisle='plant_based')
