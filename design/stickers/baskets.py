"""Basket family (docs/DESIGN_STICKER.md §5): the 17 kinds, drawn on a 64 x 48 grid (base y 46), viewBox -8 -9 80 64.

Every kind has the same five layers (§5.1, §20.6 item 101), so the app draws each one the same way:
  back        the back wall, carrying the die-cut (and its shadow) of the whole basket; decorated kinds keep their
              decoration here, so it shows empty or full (R4)
  (minis)     the gotten stickers go between back and front, their bottoms tucked behind the front wall
  front       the front wall
  lid_open    the lid or cover in its open pose, drawn BEHIND the back wall: hinged open (bento, cooler, vanity case:
              it stands there until the aisle is done), swung back on its hinge, lifted off, folded out (flaps) or
              rolled up (the jar shelf's curtain). Every other kind shows it only while the lid is open on a done
              basket: the peek, and the frame before it shuts or after it re-opens
  lid_closed  the lid or cover shut over everything gotten, with the GOT IT hanko pressed on it at `hk`
  deco        (decorated kinds only) the slice of `back` that is decoration, as its own layer
"""
import copy
import re

from engine import *

VB = '-8 -9 80 64'


def swung(parts, angle, hinge, lift=0.0, dx=0.0):
    """The closed lid swung open on its hinge (a point on the back rim, DSL units): the same parts rotated by `angle`
    (clockwise positive, so a hinge on the right lifts the lid's left end) and moved by (dx, lift). Bands are drawn in
    screen space, at the lower edge of the swung shape, like every rotated part."""
    hx, hy = hinge
    pre = f'translate({fmt(dx)} {fmt(lift)}) ' if (dx or lift) else ''
    out = []
    for p in parts:
        q = copy.copy(p)
        q.tex, q.det = list(p.tex), list(p.det)
        q.tf = f'{pre}rotate({fmt(angle)} {fmt(hx)} {fmt(hy)})' + (f' {p.tf}' if p.tf else '')
        q.sband = True
        out.append(q)
    return out


def handle_down_to(tube, y):
    """a one-cubic handle TUBE cut to the part above y: the copy a closed lid draws under itself (over_lid) stops inside
    the lid, so the handle's round feet don't poke out below the lid's edge"""
    v = [float(x) for x in re.findall(r'-?\d*\.?\d+', tube.s[1])]
    P = [(v[i], v[i + 1]) for i in range(0, 8, 2)]

    def split(pts, t):
        a = [(p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t) for p, q in zip(pts, pts[1:])]
        b = [(p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t) for p, q in zip(a, a[1:])]
        c = [(b[0][0] + (b[1][0] - b[0][0]) * t, b[0][1] + (b[1][1] - b[0][1]) * t)]
        return [pts[0], a[0], b[0], c[0]], [c[0], b[1], a[2], pts[3]]

    def y_at(t):
        return split(P, t)[0][3][1]

    def solve(lo, hi):            # y(t) == y on [lo, hi], where it crosses once
        f_lo = y_at(lo) - y
        for _ in range(60):
            mid = (lo + hi) / 2
            if (y_at(mid) - y) * f_lo > 0:
                lo = mid
            else:
                hi = mid
        return (lo + hi) / 2
    t0, t1 = solve(0, .5), solve(.5, 1)
    mid = split(split(P, t1)[0], t0 / t1)[1]
    q = copy.copy(tube)
    q.det, q.tex = list(tube.det), list(tube.tex)
    q.s = ('path', 'M' + 'C'.join(' '.join(f'{fmt(round(c, 2))}' for c in pt) for pt in mid[:1]) + 'C' + ' '.join(f'{fmt(round(c, 2))}' for pt in mid[1:] for c in pt))
    return q


def lowered(dy, parts):
    """parts moved down by dy (a translate on each part; nothing else changes)"""
    out = []
    for p in parts:
        q = copy.copy(p)
        q.tf = f'translate(0 {fmt(dy)})' + (f' {p.tf}' if p.tf else '')
        out.append(q)
    return out


class Basket:
    def __init__(self, key, label, aisle, back, front, dy=0, lid_open=(), lid_closed=(), gloss=(), note='', lid_gloss=(), rim=20, hk=(21, 17), show_open=False, deco=None, swing=None, glass=False, over_lid=()):
        self.key, self.label, self.aisle = key, label, aisle
        self.back, self.front, self.dy = back, front, dy
        # swing=(angle, hinge, lift[, dx]): the open pose is the closed lid swung back on its hinge (see `swung`)
        if swing and not lid_open:
            lid_open = swung(lid_closed, *swing)
        # over_lid: back parts that stand above the closed lid (a carry handle). The closed lid draws them too, under
        # itself, so the handle shows over the lid and the lid's white border is the back's own there (no empty bump).
        self.lid_open, self.lid_closed, self.gloss, self.note = list(lid_open), list(over_lid) + list(lid_closed), list(gloss), note
        self.lid_gloss = list(lid_gloss)
        # glass=True: the back wall is clear glass, so the front layer's gloss is glare on it (over whatever sits inside)
        self.glass = glass
        self.rim, self.hk = rim, hk
        self.show_open = show_open   # boxes: the lid stands hinged open behind the back wall until the aisle is done
        # decorated kinds (§5.2): the slice of `back` that is decoration, kept when the basket is empty. Metadata only
        # (the Android emitter names it and writes it as its own layer); it never changes how the basket draws.
        self.deco = deco

    def defs(self, prefix='sticker'):
        clips = []
        allp = self.back + self.front
        a = Art('bk' + self.key, allp, prefix=prefix)
        nb = len(self.back)
        out = [a.defs(),
               f'<g id="{prefix}-bk-{self.key}-back" {RJA}>{cut_uses(a.kid)}{a.art(idx=set(range(nb)), clips=clips)}</g>',
               f'<g id="{prefix}-bk-{self.key}-front" {RJA}>{a.art(idx=set(range(nb, len(allp))), clips=clips)}{gloss_svg(self.gloss)}</g>']
        if self.lid_open:
            lo = Art('bklo' + self.key, self.lid_open, prefix=prefix)
            out.append(lo.defs() + f'<g id="{prefix}-bk-{self.key}-lidopen" {RJA}>{cut_uses(lo.kid)}{lo.art(clips=clips)}</g>')
        if self.lid_closed:
            lc = Art('bklc' + self.key, self.lid_closed, prefix=prefix)
            out.append(lc.defs() + f'<g id="{prefix}-bk-{self.key}-lidclosed" {RJA}>{cut_uses(lc.kid)}{lc.art(clips=clips)}{gloss_svg(self.lid_gloss)}</g>')
        return ''.join(clips), ''.join(out)


# One rule everywhere: a basket holds what has ALREADY BEEN GOTTEN. Each checked sticker hops in and sits in the basket,
# full colour, its bottom tucked behind the front wall: one upright, two tilted apart, three in a fanned row, then a
# raised back row so the basket visibly heaps up as the aisle is checked off (max 5 shown, then a '+n' tag). Items still to
# get are never drawn in or on a basket; the count badge beside it says how many are left. Nothing gotten yet = an empty
# basket with a dotted landing spot in its mouth. A finished aisle = the full basket with its lid shut over everything and
# the GOT IT hanko pressed onto the lid (the minis reappear under the open lid when you press and hold to peek).
FILL = {1: [(32, 0, 0)], 2: [(22, 0, -7), (42, 0, 7)], 3: [(13, 1.5, -9), (32, 0, 0), (51, 1.5, 9)],
        4: [(13, 1.5, -9), (51, 1.5, 9), (23, -6, -4), (41, -6, 4)],
        5: [(13, 1.5, -9), (32, 0, 0), (51, 1.5, 9), (23, -7, -4), (41, -7, 4)]}     # (centre x, raise, tilt) in fill order
SI, LIFT, MAXIN = 30, .7, 5


def mini(key, x, y, s, rot=0, cls=''):
    c = f' class="{cls}"' if cls else ''
    u = f'<use href="#sticker-i-{key}" x="{fmt(x)}" y="{fmt(y)}" width="{fmt(s)}" height="{fmt(s)}"{c}/>'
    # the tilt lives on a wrapper so CSS transforms on the mini itself (peek, lift) never undo it
    return f'<g transform="rotate({fmt(rot)} {fmt(x + s / 2)} {fmt(y + s)})">{u}</g>' if rot else u


def minis(b, got=()):
    """only gotten stickers; an empty basket shows a dotted landing spot half-sunk in its mouth"""
    g = list(got)[:MAXIN]
    if not g:
        return f'<circle class="bk-wait" cx="32" cy="{fmt(b.rim - 1.5)}" r="7"/>'
    slots = sorted(zip(g, FILL[len(g)]), key=lambda s: s[1][1] >= 0)      # raised back row first, so the front row overlaps it
    return ''.join(mini(k, cx - SI / 2, b.rim - SI * LIFT + dy, SI, rot, 'bk-in' + (' bk-back' if dy < 0 else '')) for k, (cx, dy, rot) in slots)


def tag(n):
    vis = '' if n > 0 else ' style="display:none"'
    return f'<g class="bk-tag"{vis} transform="translate(49 30)"><use href="#sticker-bktag"/><text x="9" y="9">+{max(n, 1)}</text></g>'


TAG_SYMBOL = ('<g id="sticker-bktag"><rect x="1.6" y="2.2" width="18" height="12" rx="6" fill="var(--sk-shadow,#6E7BD0)"/><rect width="18" height="12" rx="6" fill="#fff"/>'
              '<rect x="1.4" y="1.4" width="15.2" height="9.2" rx="4.6" fill="none" stroke="#9AA8F5" stroke-width="1"/></g>'
              '<g id="sticker-spotg"><circle cx="15" cy="15" r="13.5" style="fill:var(--sk-spot-fill,#fff);stroke:var(--sk-spot-line,#3E4BB0)" stroke-width="2" stroke-dasharray="4 3"/></g>')


def basket_svg(b, got=(), state='open', w=80, h=64, cls='', extra='', label=None, live=False):
    """got: the stickers already gotten from this aisle (the only ones a basket ever shows).
    state: open (no lid) | done (lid shut + hanko) | live: lid + hanko present, shown by CSS when .is-done"""
    from deco import hanko_id
    done = state == 'done'
    parts = [f'<use href="#sticker-bk-{b.key}-lidopen" class="bk-lidopen"/>'] if (b.show_open and b.lid_open and (live or not done)) else []
    parts += [f'<use href="#sticker-bk-{b.key}-back"/>', f'<g class="bk-minis">{minis(b, got)}</g>',
              f'<use href="#sticker-bk-{b.key}-front"/>', tag(len(got) - MAXIN)]
    if live or done:
        parts.append(f'<g class="bk-lid"><use href="#sticker-bk-{b.key}-lidclosed" class="bk-lidclosed"/>'
                     f'<use href="#{hanko_id()}" class="bk-hk" x="{fmt(b.hk[0] - 17)}" y="{fmt(b.hk[1] - 17)}" width="34" height="34" transform="rotate(-12 {fmt(b.hk[0])} {fmt(b.hk[1])})"/></g>')
    aria = f' role="img" aria-label="{label}"' if label else ' aria-hidden="true"'
    c = 'bk' + (' is-done' if done else '') + (' ' + cls if cls else '')
    return f'<svg class="{c}" data-rim="{fmt(b.rim)}" data-k="{b.key}" viewBox="{VB}" width="{w}" height="{h}"{aria}>{"".join(parts)}{extra}</svg>'


WEAVE_H = ['M4 29.4H60', 'M4 35.6H60', 'M4 41.4H60']
WEAVE_V = ['M13 23V46', 'M21 23V46', 'M29 23V46', 'M37 23V46', 'M45 23V46', 'M53 23V46']
GINGHAM = ['M10 18V40', 'M15 18V40', 'M20 18V40', 'M25 18V40', 'M6 23.4H30', 'M6 28.4H30', 'M6 33.4H30']

BASKETS = [
    Basket('picnic', 'Wicker picnic basket', 'Produce',
           # the handle's highlight runs down its middle (it crossed the outline before); closed, the handle stands over the lid
           back=[PICNIC_HANDLE := TUBE('M15 22C15 3.6 49 3.6 49 22', 'cocoa', 5.4, 'p', det=['M17.1 15Q18.7 12.4 22.3 10.4'], det_t='#FFFFFF', det_w=1.4),
                 F(('rect', 7.5, 17.4, 49, 5, 2.5), 'cocoa', 'm')],
           front=[F(('path', 'M6 23H58L54.6 42.6Q54 46 50.6 46H13.4Q10 46 9.4 42.6Z'), 'cocoa', 'p', band=.26, tex=WEAVE_H + WEAVE_V),
                  F(('rect', 4, 20, 56, 6, 3), 'cocoa', 'm', det=['M12 21.6l2 2.8M20 21.6l2 2.8M28 21.6l2 2.8M36 21.6l2 2.8M44 21.6l2 2.8M52 21.6l2 2.8'], det_w=1.2),
                  F(('path', 'M7.6 20.8H27L19.6 33.8Q18.4 35.6 17 34Z'), 'strawberry', 'p', tex=GINGHAM, tex_w=2.2)],
           rim=20, hk=(21, 16), dy=0, over_lid=[handle_down_to(PICNIC_HANDLE, 16)],
           lid_open=[F(('path', 'M7 18L3.4 7.6Q2.8 5.6 4.8 5.6H27Q29 5.6 29.4 7.6L31 18Z'), 'cocoa', 'p', tex=['M4 10.6H31', 'M4 14.4H31']),
                     F(('path', 'M33 18L34.6 7.6Q35 5.6 37 5.6H59.2Q61.2 5.6 60.6 7.6L57 18Z'), 'cocoa', 'p', tex=['M33 10.6H61', 'M33 14.4H61'])],
           lid_closed=[F(('rect', 5, 12.6, 27, 9, 3), 'cocoa', 'p', tex=['M5 15.6H32', 'M5 18.6H32']),
                       F(('rect', 32, 12.6, 27, 9, 3), 'cocoa', 'p', tex=['M32 15.6H59', 'M32 18.6H59']),
                       F(('rect', 29.4, 15.4, 5.2, 7.4, 1.6), 'strawberry', 'm')],
           gloss=[G('M10.4 29.4L11.2 35', 2.4), DOT(11.7, 38.6), G('M16 23.6H21', 2.2)]),
    Basket('bread', 'Cloth-lined bread basket', 'Bakery',
           back=[F(('path', 'M5 29.6C5 25.6 7.2 23.4 10.8 22.6C14.4 19.8 18.4 22.2 22 20.4C25.6 18.8 28.6 21.2 32 20C35.4 18.8 38.6 21.2 42 20.4C45.6 19.6 49.4 21.8 53.2 22.6C56.8 23.4 59 25.6 59 29.6Z'), 'ramune', 'p',
                   tex=['M9 16V31', 'M15 16V31', 'M21 16V31', 'M27 16V31', 'M33 16V31', 'M39 16V31', 'M45 16V31', 'M51 16V31', 'M57 16V31', 'M0 22H64', 'M0 27H64'], tex_w=2.6,
                   det=['M22 21.6Q24 24 23.4 26.6', 'M42 21.6Q40.4 24 41 26.6'], det_t='m', det_w=1.3)],
           front=[F(('path', 'M7 30H57L53.4 42.8Q52.6 46 49.2 46H14.8Q11.4 46 10.6 42.8Z'), 'cocoa', 'p', band=.3, tex=['M4 35.4H60', 'M4 40.6H60', 'M16 30V46', 'M24 30V46', 'M32 30V46', 'M40 30V46', 'M48 30V46']),
                  F(('rect', 5, 27, 54, 5.6, 2.8), 'cocoa', 'm'),
                  F(('path', 'M43.6 28.4H57.4Q58.6 28.4 58.2 29.6L54.8 40.4Q54 42.4 52.4 40.8Z'), 'ramune', 'p', tex=['M45 26V44', 'M50 26V44', 'M55 26V44', 'M42 32H60', 'M42 37H60'], tex_w=2.4)],
           rim=27, hk=(21, 21), dy=5, swing=(17, (61, 28), -5),
           lid_closed=[F(('path', 'M3 29C3 17.4 16 12.6 32 12.6C48 12.6 61 17.4 61 29Z'), 'ramune', 'p', band=.22, tex=['M10 10V30', 'M18 10V30', 'M26 10V30', 'M34 10V30', 'M42 10V30', 'M50 10V30', 'M0 17H64', 'M0 23H64'], tex_w=3.4), F(('circle', 32, 12, 3.2), 'ramune', 'm', ol=1.6)],
           gloss=[G('M8.6 29.8H14', 2.2), G('M12.8 35L13.4 39', 2.4)]),
    Basket('bento', 'Bento box', 'Deli & meat',
           back=[F(('rect', 6.4, 12.4, 51.2, 5.8, 2.9), 'sakura', 'm'),
                 F(('rect', 7.8, 16, 48.4, 7.6, 1.4), 'sakura', 'deep', ol=0, cut=False, det=['M32 16.4V23'], det_t='m', det_w=2.4)],
           front=[F(('rect', 5, 24, 54, 22, 5), 'sakura', 'p', band=.24, tex=['M5 33H59'], tex_w=3.4),
                  F(('rect', 3.4, 21.4, 57.2, 5.4, 2.7), 'sakura', 'm'),
                  F(('path', star_path(32, 37.4, 4.2, 2, 5)), 'lemon', 'p', ol=1.3, cut=False),
                  F(('circle', 32, 37.4, 1.2), 'lemon', 'm', ol=0, cut=False)],
           rim=21.4, hk=(46, 17), dy=1, show_open=True,
           lid_open=[F(('rect', 6.4, 1.6, 51.2, 10.8, 4), 'sakura', 'm', tf='translate(0 4.4) rotate(13 57.6 12.4)', tex=['M2 7H62'], tex_t='p', tex_w=2.6),
                     F(('rect', 30, 1.6, 4, 10.8, 1), 'lemon', 'p', ol=1.3, tf='translate(0 4.4) rotate(13 57.6 12.4)')],
           # the bow is two loops and a knot on the band: its tiny tails over the band read as a muddy clump
           lid_closed=[F(('rect', 3.4, 13, 57.2, 11, 4), 'sakura', 'm', tex=['M3 18.4H61'], tex_t='p', tex_w=2.6),
                       F(('rect', 30, 13, 4, 11, 1), 'lemon', 'p', ol=1.3),
                       F(('path', 'M32 13.8C30.4 10.6 27.4 10 26.4 11.6C25.6 13 27 15 32 14.2C37 15 38.4 13 37.6 11.6C36.6 10 33.6 10.6 32 13.8Z'), 'lemon', 'p', ol=1.4),
                       F(('circle', 32, 14, 1.8), 'lemon', 'm', ol=1.3)],
           gloss=[G('M7.8 30V36', 2.4), DOT(8, 39.6)]),
    Basket('bowl', 'Mixing bowl', 'Baking',
           back=[TUBE('M42 22L53.4 3.6', 'periwinkle', 4.6, 'p'),
                 LINE('M35 22C33 14.6 41.6 11.6 43.6 17M39.4 22.6C39 16 46.6 14.4 46.6 20', 'periwinkle', 1.8),
                 F(('rect', 7, 18.6, 50, 5.6, 2.8), 'lemon', 'm')],
           front=[F(('path', 'M5 23H59C59 37.4 47 46 32 46C17 46 5 37.4 5 23Z'), 'lemon', 'p', band=.26,
                    tex=['M14 30h.1M24 30h.1M34 30h.1M44 30h.1M54 30h.1M19 36h.1M29 36h.1M39 36h.1M49 36h.1M24 42h.1M34 42h.1M44 42h.1'], tex_w=3.4),
                  F(('rect', 3, 20.4, 58, 5.6, 2.8), 'lemon', 'm')],
           rim=20.4, hk=(23, 15), dy=0,
           lid_open=[F(('path', 'M5.4 18.4L8.6 -1.4Q9 -3.4 11 -3.2L28.4 -1.2Q30.4 -1 30.2 1L28 19.2Z'), 'periwinkle', 'p', band=.2, band_t='m',
                       tex=['M13.4 -5L11.6 21', 'M20 -4L18.8 21', 'M26.6 -3L25.8 21'], tex_t='m', tex_w=2.4)],
           lid_closed=[F(('path', 'M2 23.4C2 14 14.4 9 32 9C49.6 9 62 14 62 23.4Z'), 'periwinkle', 'p', band=.26,
                         tex=['M12 6V26', 'M22 6V26', 'M32 6V26', 'M42 6V26', 'M52 6V26'], tex_t='m', tex_w=2.4),
                       F(('path', 'M8.6 21.2H24.4L18.8 34Q17.6 36.4 16.4 34Z'), 'periwinkle', 'p', band=.2, band_t='m',
                         tex=['M12 18V36', 'M18.6 18V36'], tex_t='m', tex_w=2.4)],
           gloss=[G('M9.4 28.4Q10.6 33.6 14 37', 2.4), G('M7.4 23.2H14', 2)]),
    Basket('crate', 'Wooden crate', 'Breakfast',
           back=[F(('rect', 7, 18, 50, 5, 1.6), 'cocoa', 'm')],
           # the label sits on the lower plank: on the upper one the closed lid's white border covered half of it
           front=[F(('rect', 6, 22, 52, 11, 2.6), 'cocoa', 'p', det=['M12 27.8H26M32 29.8H48'], det_t='m', det_w=1.4),
                  F(('rect', 6, 35, 52, 11, 2.6), 'cocoa', 'p', band=.3, det=['M12 38.6H20M44 41.4H52'], det_t='m', det_w=1.4),
                  F(('rect', 3.6, 21, 7.2, 25, 2.2), 'cocoa', 'm', det=['M7.2 24.6h.1M7.2 42.4h.1'], det_w=1.8),
                  F(('rect', 53.2, 21, 7.2, 25, 2.2), 'cocoa', 'm', det=['M56.8 24.6h.1M56.8 42.4h.1'], det_w=1.8),
                  F(('rect', 24, 36.4, 16, 6.4, 2.2), 'peach', 'p', ol=1.5, cut=False),
                  F(('circle', 32, 39.6, 2.2), 'peach', 'm', ol=1.2, cut=False)],
           rim=21, hk=(21, 17), dy=1, swing=(-14, (2, 22), -1),
           lid_closed=[F(('rect', 3, 11.4, 58, 11, 2.6), 'cocoa', 'p', band=.26, det=['M12 15.4H26M34 18.4H50'], det_t='m', det_w=1.4), F(('rect', 1.6, 10.4, 7.2, 13, 2.2), 'cocoa', 'm'), F(('rect', 55.2, 10.4, 7.2, 13, 2.2), 'cocoa', 'm')],
           gloss=[G('M13 24.8H21', 2.2)]),
    Basket('eggbasket', 'Wire egg basket', 'Dairy & eggs',
           back=[TUBE('M13 24C13 5 51 5 51 24', 'grape', 4.2, 'p'),
                 F(('path', 'M7 25C7 20.6 10 18.4 13 19.6C14.6 16.4 19 16 21 18.6C23 15.6 28 15.2 30 18C32.4 15.2 37.4 15.6 39 18.6C41 16 45.6 16.4 47.2 19.6C50.4 18.4 57 20.6 57 25Z'), 'lemon', 'm', ol=1.5, det=['M14 21.6l2.4 -1.4M24 19.6l2.4 1M33.6 19.4l2.6 -1.2M43 20.6l2.2 1'], det_w=1.2)],
           front=[F(('path', 'M7 24H57C57 37 47 46 32 46C17 46 7 37 7 24Z'), 'grape', 'p', band=.24,
                    tex=['M10 24L30 50', 'M20 24L40 50', 'M30 24L50 50', 'M40 24L56 46', 'M50 24L58 34', 'M54 24L34 50', 'M44 24L24 50', 'M34 24L14 50', 'M24 24L8 44', 'M14 24L6 34']),
                  F(('rect', 5, 21.4, 54, 5, 2.5), 'grape', 'm'),
                  ],
           rim=21.4, hk=(21, 16), dy=1, swing=(17, (60, 22), -2.6),
           lid_closed=[F(('path', 'M4 23C4 12.6 16 8.4 32 8.4C48 8.4 60 12.6 60 23Z'), 'grape', 'p', band=.22, tex=['M12 18h.1M20 13.6h.1M28 18h.1M36 13.6h.1M44 18h.1M52 15h.1M16 21.4h.1M48 21.4h.1'], tex_t='m', tex_w=3)],
           gloss=[G('M10.7 29.3Q11.4 32.6 13.7 35.3', 2.6), DOT(16.2, 38)]),
    Basket('cooler', 'Little cooler', 'Frozen',
           back=[F(('rect', 6.6, 12.2, 50.8, 5.8, 2.9), 'ramune', 'w'),
                 F(('rect', 8, 15.8, 48, 7, 1.4), 'ramune', 'm', ol=0, cut=False)],
           front=[F(('rect', 1, 28, 7, 9, 2.4), 'ramune', 'w', ol=1.8), F(('rect', 56, 28, 7, 9, 2.4), 'ramune', 'w', ol=1.8),
                  F(('rect', 5, 21, 54, 25, 5), 'ramune', 'p', band=.24),
                  F(('rect', 5.9, 21.9, 52.2, 6.4, 3), 'ramune', 'w', ol=0, cut=False, det=['M6 28.4H58'], det_w=1.6),
                  LINE('M32 31.4V41.4M27.6 33.9L36.4 38.9M27.6 38.9L36.4 33.9', 'ramune', 2.2, t='tr'),
                  LINE('M32 31.4V41.4M27.6 33.9L36.4 38.9M27.6 38.9L36.4 33.9', 'ramune', .9, t='#FFFFFF')],
           rim=21, hk=(21, 18), dy=1, show_open=True,
           lid_open=[F(('rect', 6.6, 1.6, 50.8, 10.6, 4), 'ramune', 'w', tf='translate(0 4.4) rotate(-13 6.6 12.2)', det=['M10.6 7H53.4'], det_t='p', det_w=2),
                     F(('rect', 24, -1.6, 16, 4.6, 2.3), 'ramune', 'm', tf='translate(0 4.4) rotate(-13 6.6 12.2)')],
           lid_closed=[F(('rect', 4, 13, 56, 10.4, 4), 'ramune', 'w', det=['M8 18.2H56'], det_t='p', det_w=2),
                       F(('rect', 24, 9, 16, 5, 2.5), 'ramune', 'm')],
           gloss=[G('M9.2 32V38', 2.4), DOT(9.2, 41)]),
    Basket('tote', 'Canvas tote', 'Household',
           back=[TOTE_HANDLE := TUBE('M21 20.6C21 3.4 43 3.4 43 20.6', 'mint', 4.6, 'p'),
                 F(('rect', 9, 16.4, 46, 5, 2), 'mint', 'deep')],
           front=[F(('path', 'M8 19.4H56L53.4 43Q53 46 49.8 46H14.2Q11 46 10.6 43Z'), 'mint', 'p', band=.24),
                  F(('rect', 7, 17.4, 50, 5.2, 2.2), 'mint', 'm'),
                  F(('path', heart_path(38, 32.4, 6)), 'sakura', 'm', ol=1.6, cut=False,
                    det=['M33.8 31Q34 30 35 29.6'], det_t='p', det_w=1.2)],   # the patch's shine sits inside its left lobe
           rim=17.4, hk=(21, 14), dy=-2, swing=(-15, (7.6, 19.6), -1.6), over_lid=[handle_down_to(TOTE_HANDLE, 13)],
           lid_closed=[F(('path', 'M7.6 19.6H56.4V11.6Q56.4 8.6 53.4 8.6H10.6Q7.6 8.6 7.6 11.6Z'), 'mint', 'm', band=.24, band_t='deep'), F(('path', heart_path(32, 19.4, 3.4)), 'sakura', 'm', ol=1.4)],
           gloss=[G('M12.4 25.6L13.4 33', 2.4), DOT(13.8, 36.6)]),
    Basket('wire', 'Shopping basket', 'Everything else',
           # one arc for the handle (two halves met in a lumpy knot at the top); closed, it stands over the lid flaps
           back=[WIRE_HANDLE := TUBE('M14 21C14.4 -.4 49.6 -.4 50 21', 'periwinkle', 4.2, 'p'),
                 F(('rect', 7, 18, 50, 5, 2), 'strawberry', 'deep')],
           front=[F(('rect', 5, 21, 54, 25, 4.4), 'strawberry', 'm', band=.2,
                    tex=['M11 29.6H19M24 29.6H32M37 29.6H45M50 29.6H53', 'M11 36.6H19M24 36.6H32M37 36.6H45M50 36.6H53'], tex_w=3.6, tex_t='p'),
                  F(('rect', 3.4, 19.4, 57.2, 5.2, 2.6), 'strawberry', 'm')],
           rim=19.4, hk=(21, 15), dy=0, over_lid=[handle_down_to(WIRE_HANDLE, 14)],
           lid_open=[F(('path', 'M6.4 20.6L2.6 9.2Q2 7.2 4 7.2H27.6Q29.6 7.2 29.8 9.2L30.6 20.6Z'), 'strawberry', 'p', band=.24),
                     F(('path', 'M57.6 20.6L61.4 9.2Q62 7.2 60 7.2H36.4Q34.4 7.2 34.2 9.2L33.4 20.6Z'), 'strawberry', 'p', band=.24)],
           lid_closed=[F(('path', 'M4.6 20.6L7 10.6Q7.4 9 9 9H31V20.6Z'), 'strawberry', 'p', band=.24), F(('path', 'M33 20.6V9H55Q56.6 9 57 10.6L59.4 20.6Z'), 'strawberry', 'p', band=.24)],
           gloss=[G('M7.8 27.4V35', 2.4), DOT(8, 38.4)]),
    Basket('punnet', 'Berry punnet', 'Farmers’ market',
           back=[F(('rect', 9, 18, 46, 5, 2), 'melon', 'deep')],
           front=[F(('path', 'M8 22H56L52.4 42.6Q51.8 46 48.4 46H15.6Q12.2 46 11.6 42.6Z'), 'melon', 'p', band=.26,
                    tex=['M16 22V46', 'M24 22V46', 'M32 22V46', 'M40 22V46', 'M48 22V46', 'M6 30H58', 'M6 37.6H58']),
                  F(('rect', 6, 19.6, 52, 5.6, 2.8), 'melon', 'm')],
           rim=19.6, hk=(21, 16), dy=0, swing=(18, (59, 21), -2),
           lid_closed=[F(('path', 'M5 21.4C5 12.6 14 9.6 32 9.6C50 9.6 59 12.6 59 21.4Z'), 'melon', 'w', band=.2, tex=['M14 11V22', 'M24 10V22', 'M32 10V22', 'M40 10V22', 'M50 11V22'], tex_t='p', tex_w=1.8)],
           gloss=[G('M9.6 22.3H16', 2.2)]),
]

# ---------- r9 · the seven kinds the app has that the mockup lacked. Decorated kinds (ice bucket, flower bucket, bottle carrier,
# candy jar) carry their decoration in the BACK layer, so it is always there, empty or full; gotten minis are added in front of it ----------
GING2 = ['M12 9V27', 'M22 9V27', 'M32 9V27', 'M42 9V27', 'M52 9V27', 'M0 14H64', 'M0 19.4H64']
BASKETS += [
    Basket('milkcrate', 'Milk crate', 'Dairy',
           back=[F(('rect', 7, 17.6, 50, 5.4, 2), 'ramune', 'deep')],
           front=[F(('rect', 5, 21, 54, 25, 4.4), 'ramune', 'm', band=.2, tex=['M11 29.4H18.6M22.6 29.4H30.2M34.2 29.4H41.8M45.8 29.4H53', 'M11 37.4H18.6M22.6 37.4H30.2M34.2 37.4H41.8M45.8 37.4H53'], tex_w=4.2, tex_t='p'),
                  F(('rect', 3.4, 19.4, 57.2, 5.4, 2.7), 'ramune', 'm', det=['M26.4 22.1H37.6'], det_t='p', det_w=2.6)],
           rim=19.4, hk=(21, 15), dy=0, swing=(-16, (3, 22), -3.4),
           lid_closed=[F(('path', 'M3 22C3 13.4 13 9.6 32 9.6C51 9.6 61 13.4 61 22L57 25.6L52 22L47 25.6L42 22L37 25.6L32 22L27 25.6L22 22L17 25.6L12 22L7 25.6Z'), 'strawberry', 'p', tex=GING2, tex_w=2.6)],
           gloss=[G('M7.8 27.4V33', 2.4), DOT(8, 36.4)]),
    Basket('icebucket', 'Ice bucket', 'Drinks',
           # the cubes sit 3u down in the bucket, low enough that the closed lid's border covers theirs (no empty bumps)
           back=[*lowered(3, [F(('rect', 9, 8, 13, 13, 3.4), 'ramune', '#E6F5FF', tf='rotate(-14 15.5 14.5)', det=['M12.4 11.6L15.6 10.8'], det_t='#FFFFFF', det_w=1.8),
                 F(('rect', 25, 4.6, 14, 14, 3.6), 'ramune', '#E6F5FF', tf='rotate(9 32 11.6)', det=['M28.4 8.4L31.8 8'], det_t='#FFFFFF', det_w=1.8),
                 F(('rect', 42, 8.6, 13, 13, 3.4), 'ramune', '#E6F5FF', tf='rotate(20 48.5 15.1)', det=['M45.4 12.2L48.4 11.8'], det_t='#FFFFFF', det_w=1.8)]),
                 F(('rect', 8, 17.6, 48, 5, 2.4), 'mint', 'deep')],
           front=[F(('rect', 1.4, 25, 6.4, 8, 2.4), 'mint', 'm', ol=1.8), F(('rect', 56.2, 25, 6.4, 8, 2.4), 'mint', 'm', ol=1.8),
                  F(('path', 'M7 21H57L53.4 42.8Q52.8 46 49.4 46H14.6Q11.2 46 10.6 42.8Z'), 'mint', 'p', band=.26, tex=['M8 30H56', 'M9.4 37.4H54.6'], tex_w=2.2),
                  F(('rect', 5, 18.6, 54, 5.6, 2.8), 'mint', 'm')],
           rim=18.6, hk=(21, 14), dy=0, deco=(0, 3), swing=(15, (60.4, 20), -2.6),
           lid_closed=[F(('path', 'M3.6 20.6C3.6 12.4 15 9 32 9C49 9 60.4 12.4 60.4 20.6Z'), 'mint', 'm', band=.24, band_t='deep'), F(('rect', 27, 5.4, 10, 5, 2.5), 'mint', 'p')],
           gloss=[G('M11.6 27.6Q12.2 31.4 13.6 34.6', 2.4), DOT(14.6, 37.6)]),
    Basket('flowerbucket', 'Flower bucket', 'Florist',
           back=[*lowered(1.6, [TUBE('M18 21C17.4 14 15.4 9.4 12.6 7', 'melon', 2.6, 'm'), TUBE('M32 21V5', 'melon', 2.6, 'm'), TUBE('M46 21C46.6 14 48.6 9.4 51.4 7.4', 'melon', 2.6, 'm'),
                 F(('path', 'M32 15C27.4 14.4 25 10.6 25.4 8.2C29 8.6 31.6 11.4 32 15Z'), 'melon', 'p'),
                 F(('path', 'M46.6 17C50.4 15.8 53.6 16.6 54.6 18.6C51.6 20 48.4 19.6 46.6 17Z'), 'melon', 'p'),
                 F(('path', 'M7.6 6.4C7.6 1.6 9.6 -1.8 12.6 -2.8C15.6 -1.8 17.6 1.6 17.6 6.4C17.6 9.4 15.4 11.2 12.6 11.2C9.8 11.2 7.6 9.4 7.6 6.4Z'), 'sakura', 'm', det=['M12.6 -.4V8.4'], det_t='p', det_w=1.3),
                 *[F(('ellipse', 32 + 4.6 * math.cos(math.radians(a)), 1.8 + 4.6 * math.sin(math.radians(a)), 3, 1.9, a), 'sakura', 'w', ol=1.5) for a in range(-90, 270, 45)],
                 F(('circle', 32, 1.8, 2.8), 'lemon', 'm', ol=1.5),
                 F(('path', 'M46.4 6.8C46.4 2 48.4 -1.4 51.4 -2.4C54.4 -1.4 56.4 2 56.4 6.8C56.4 9.8 54.2 11.6 51.4 11.6C48.6 11.6 46.4 9.8 46.4 6.8Z'), 'grape', 'm', det=['M51.4 0V8.8'], det_t='p', det_w=1.3)]),
                 F(('rect', 8, 17.6, 48, 5, 2.4), 'periwinkle', 'deep')],
           front=[F(('path', 'M8 21H56L53 42.8Q52.4 46 49 46H15Q11.6 46 11 42.8Z'), 'periwinkle', 'p', band=.26, tex=['M8.6 28.4H55.4', 'M9.8 36H54.2'], tex_w=2),
                  F(('rect', 6, 18.6, 52, 5.6, 2.8), 'periwinkle', 'm'),
                  F(('path', heart_path(32, 33.4, 4.6)), 'sakura', 'm', ol=1.4, cut=False)],
           rim=18.6, hk=(21, 15), dy=0, deco=(0, 16),
           lid_open=[F(('path', 'M9 23L-1.6 3Q-2.6 .6 0 0L10.6 -2.8L21.4 0L32 -3.6L42.6 0L53.4 -2.8L64 0Q66.6 .6 65.6 3L55 23Z'), 'cocoa', 'p', band=.16,
                       tex=['M3 -1L14 23', 'M16 -2L22 23', 'M32 -3V23', 'M48 -2L42 23', 'M61 -1L50 23'], tex_w=1.4)],
           # the wrap's bottom corners end inside the band's round ends (they stuck out past them)
           lid_closed=[F(('path', 'M6 21.6L9.6 7.4Q10.6 5 13 5.8L32 11L51 5.8Q53.4 5 54.4 7.4L58 21.6Z'), 'cocoa', 'p', band=.2, tex=['M15 9V22', 'M24 11V22', 'M40 11V22', 'M49 9V22'], tex_w=1.4),
                       F(('rect', 4.6, 18.6, 54.8, 5, 2.5), 'sakura', 'm')],
           gloss=[G('M12.6 27.6Q13.2 31 14.4 33.6', 2.4), DOT(15.4, 36.4)]),
    Basket('bottlecarrier', 'Bottle carrier', 'Drinks',
           back=[F(('path', 'M11 22V12Q11 9.6 12.6 8.4V3.6Q12.6 2 14.2 2H15.8Q17.4 2 17.4 3.6V8.4Q19 9.6 19 12V22Z'), 'mint', 'p', det=['M13.4 5.2H16.6'], det_t='m', det_w=2),
                 F(('rect', 12.2, -1.2, 5.6, 3.6, 1.2), 'strawberry', 'm', ol=1.4),
                 F(('path', 'M45 22V12Q45 9.6 46.6 8.4V3.6Q46.6 2 48.2 2H49.8Q51.4 2 51.4 3.6V8.4Q53 9.6 53 12V22Z'), 'sakura', 'p', det=['M47.4 5.2H50.6'], det_t='m', det_w=2),
                 F(('rect', 46.2, -1.2, 5.6, 3.6, 1.2), 'lemon', 'm', ol=1.4),
                 # the middle tin stands low enough to go under the closed lid whole (it left a bump of border before)
                 F(('path', 'M26.6 22V13.4Q26.6 9.8 30.2 9.8H33.8Q37.4 9.8 37.4 13.4V22Z'), 'cocoa', 'p', det=['M30.2 14.4H33.8'], det_t='deep', det_w=3.2)],
           front=[F(('rect', 5, 22, 54, 24, 3), 'cocoa', 'p', band=.28, tex=['M5 30.6H59', 'M5 38.4H59'], tex_w=1.6, tex_t='m'),
                  F(('rect', 3.4, 20, 57.2, 5, 2.5), 'cocoa', 'm'),
                  F(('rect', 23, 31.4, 18, 8, 2.2), 'lemon', 'p', ol=1.3, cut=False), F(('circle', 32, 35.4, 1.8), 'strawberry', 'm', ol=1, cut=False)],
           rim=20, hk=(21, 16), dy=0, deco=(0, 5), swing=(-15, (3, 20), -3.4),
           lid_closed=[F(('path', 'M3 21C3 13 12 9.6 32 9.6C52 9.6 61 13 61 21Z'), 'lemon', 'p', tex=GING2, tex_w=2.6)],
           gloss=[G('M8.6 27.8V32.6', 2.4), DOT(8.6, 35)]),
    Basket('candyjar', 'Candy jar', 'Snacks',
           back=[F(('path', 'M10 21.6C6.4 23.6 5 27 5 31V40Q5 46 11 46H53Q59 46 59 40V31C59 27 57.6 23.6 54 21.6Z'), 'periwinkle', '#F3F6FF'),
                 F(('circle', 15.6, 40, 4), 'strawberry', 'm', ol=1.4, cut=False, det=['M13.6 39.4Q15.6 37.6 17.4 39.6'], det_t='#FFFFFF', det_w=1.2),
                 F(('circle', 24.6, 41.6, 3.6), 'lemon', 'm', ol=1.4, cut=False),
                 F(('circle', 33.6, 40.4, 4), 'melon', 'm', ol=1.4, cut=False, det=['M31.6 39.8Q33.6 38 35.4 40'], det_t='#FFFFFF', det_w=1.2),
                 F(('circle', 42.6, 41.6, 3.6), 'grape', 'm', ol=1.4, cut=False),
                 F(('circle', 50.8, 40.2, 3.8), 'peach', 'm', ol=1.4, cut=False),
                 # a wrapped sweet lying on the pile: a round middle between two twisted ends, clear of the heart label
                 F(('path', 'M36.4 33.8L39.4 35.2C40.2 33.4 44.6 33.4 45.4 35.2L48.4 33.8V38.6L45.4 37.2C44.6 39 40.2 39 39.4 37.2L36.4 38.6Z'), 'sakura', 'm', ol=1.3, cut=False)],
           front=[F(('rect', 8, 16.8, 48, 6.4, 3), 'periwinkle', 'm'),
                  F(('path', heart_path(32, 30.4, 3.6)), 'sakura', 'm', ol=1.3, cut=False)],
           rim=16.8, hk=(21, 13), dy=0, deco=(1, 7), swing=(17, (57, 18), -1.8),
           lid_closed=[F(('path', 'M7 18.8C7 12 17 8.6 32 8.6C47 8.6 57 12 57 18.8Z'), 'strawberry', 'm', band=.24), F(('circle', 32, 7.6, 3.4), 'strawberry', 'p')],
           glass=True, gloss=[G('M9.4 28Q9 32.6 10 36', 2.4), G('M54.2 28.6Q55 31 54.6 33.4', 1.8)]),
    Basket('jarshelf', 'Jar shelf', 'Pantry',
           back=[F(('rect', 7, 9.4, 50, 21, 2), 'cocoa', 'p', tex=['M19.4 8V31', 'M32 8V31', 'M44.6 8V31'], tex_t='m', tex_w=1.6),
                 F(('rect', 5.6, 6.6, 52.8, 4.6, 2.3), 'cocoa', 'm')],
           front=[F(('circle', 5.6, 4.6, 3.4), 'cocoa', 'm', band=.3, band_t='deep'), F(('circle', 58.4, 4.6, 3.4), 'cocoa', 'm', band=.3, band_t='deep'),
                  F(('rect', 3, 7.4, 5.2, 38.6, 2), 'cocoa', 'm'), F(('rect', 55.8, 7.4, 5.2, 38.6, 2), 'cocoa', 'm'),
                  F(('rect', 4, 31, 56, 15, 2.6), 'cocoa', 'p', band=.3),
                  F(('rect', 3, 28.4, 58, 5, 2), 'cocoa', 'm'),
                  F(('path', 'M8.6 32.6H55.4V34.4' + ''.join(f'Q{fmt(55.4 - 2.34 * (2 * i + 1))} 38.2 {fmt(55.4 - 4.68 * (i + 1))} 34.4' for i in range(10)) + 'Z'), 'lemon', 'm', ol=1.5)],
           rim=28.4, hk=(21, 19), dy=2,
           lid_open=[F(('rect', 6, -3, 52, 8.8, 4.4), 'sakura', 'p', band=.3,
                       tex=['M14 -4V7', 'M22 -4V7', 'M30 -4V7', 'M38 -4V7', 'M46 -4V7', 'M54 -4V7', 'M4 1.4H60'], tex_w=2.4),
                     F(('rect', 15, -4.2, 4.4, 11.2, 1.6), 'sakura', 'm', ol=1.5),
                     F(('rect', 44.6, -4.2, 4.4, 11.2, 1.6), 'sakura', 'm', ol=1.5)],
           # closed, the curtain hangs post to post and past the scallop trim, and its rod ends in the frame's own finials,
           # so the lid's border lines up with the frame's instead of leaving crescents, ledge ends and trim slivers
           lid_closed=[F(('path', 'M3 8H61V34Q55.2 37.4 49.4 34Q43.6 37.4 37.8 34Q32 37.4 26.2 34Q20.4 37.4 14.6 34Q8.8 37.4 3 34Z'), 'sakura', 'p',
                         tex=['M8 8V36', 'M16 8V36', 'M24 8V36', 'M32 8V36', 'M40 8V36', 'M48 8V36', 'M56 8V36', 'M3 14H61', 'M3 21H61', 'M3 28H61'], tex_w=2.4),
                       F(('rect', 4, 5.4, 56, 4, 2), 'cocoa', 'm'),
                       F(('circle', 5.6, 4.6, 3.4), 'cocoa', 'm', band=.3, band_t='deep'), F(('circle', 58.4, 4.6, 3.4), 'cocoa', 'm', band=.3, band_t='deep')],
           gloss=[G('M6.7 36V40.2', 2.4), DOT(7.6, 43.1)]),
    Basket('vanity', 'Vanity case', 'Pharmacy',
           back=[VANITY_HANDLE := TUBE('M22 15.6C22 7.6 42 7.6 42 15.6', 'grape', 4, 'p'), F(('rect', 7, 16, 50, 6, 2), 'grape', 'deep')],
           front=[F(('rect', 5, 20, 54, 26, 5), 'grape', 'p', band=.24),
                  F(('rect', 3.4, 18.4, 57.2, 5.6, 2.8), 'grape', 'm'),
                  F(('path', 'M5 27V24.4Q5 21 8.4 21H11V27Z'), 'lemon', 'm', ol=1.3, cut=False), F(('path', 'M59 27V24.4Q59 21 55.6 21H53V27Z'), 'lemon', 'm', ol=1.3, cut=False),
                  F(('rect', 28, 21.4, 8, 6, 2), 'lemon', 'm', ol=1.4)],
           rim=18.4, hk=(21, 15), dy=0, show_open=True, over_lid=[VANITY_HANDLE],
           lid_open=[F(('rect', 6, 1, 52, 15, 4.4), 'grape', 'm', tf='translate(0 1.8) rotate(-9 6 16)'),
                     F(('rect', 10.4, 3.4, 43.2, 10, 3), 'ramune', 'w', tf='translate(0 1.8) rotate(-9 6 16)', det=['M15 6.4L21 5.6'], det_t='#FFFFFF', det_w=1.8)],
           lid_closed=[F(('rect', 3.4, 10.6, 57.2, 13, 4.4), 'grape', 'm', band=.24, band_t='deep'), F(('rect', 28, 19, 8, 6.4, 2), 'lemon', 'm', ol=1.4)],
           gloss=[G('M9.2 30V37', 2.4), DOT(9.2, 40)]),
]
# hand-placed gloss is held to the same rule as the generated gloss (gloss.clean): the front wall's gloss keeps off the
# parts drawn in front of it (a flap, a post, a trim), the lid's off its own parts
from gloss import trim_gloss as _trim_gloss
for _b in BASKETS:
    _b.gloss = _trim_gloss((_b.back if _b.glass else []) + _b.front, _b.gloss)
    _b.lid_gloss = _trim_gloss(_b.lid_closed, _b.lid_gloss)
BBY = {b.key: b for b in BASKETS}
