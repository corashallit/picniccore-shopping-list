"""Furoku Sticker Album icon engine.
Layer order per spec: cut-shadow, cut-white, then per part (z-order): fill, puffy band (clipped),
texture lines (clipped), trace outline, detail lines; then gloss.
"""
import re

C = {
    'strawberry': ('#FFC4D1', '#FF6F8E', '#C3264F'),
    'peach': ('#FFDCC6', '#FFA477', '#AE4D1C'),
    'lemon': ('#FFF3A6', '#FFDA4A', '#8A6800'),
    'melon': ('#D3F4C5', '#8EDB76', '#2E8239'),
    'mint': ('#C9F2E3', '#6DD3B0', '#1C7558'),
    'ramune': ('#CCEBFF', '#7CC6F5', '#1E6AA3'),
    'periwinkle': ('#DCE1FF', '#9AA8F5', '#3E4BB0'),
    'grape': ('#E4D6FF', '#B597F2', '#6542B3'),
    'sakura': ('#FFD6E8', '#FF9FC8', '#B02E6B'),
    'cocoa': ('#F1DEC9', '#CB9C73', '#7A4A28'),
}
ORDER = ['strawberry', 'ramune', 'lemon', 'melon', 'grape', 'peach', 'mint', 'sakura', 'cocoa', 'periwinkle']
SHADOW = '#6E7BD0'
BP = '#25307C'
BP2 = '#4F5BA6'
HANKO = '#C8321F'
LP = '#FFF3A6'
W = '#FFFFFF'


def hx(c):
    c = c.lstrip('#')
    return [int(c[i:i + 2], 16) for i in (0, 2, 4)]


def blend(a, b, t):
    A, B = hx(a), hx(b)
    return '#%02X%02X%02X' % tuple(round(A[i] + (B[i] - A[i]) * t) for i in range(3))


def deep(fam):
    return blend(C[fam][1], C[fam][2], .32)


def fmt(v):
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def tone(fam, t):
    if t is None:
        return 'none'
    if t.startswith('#') or t.startswith('var(') or t.startswith('url('):
        return t
    if t == 'p':
        return C[fam][0]
    if t == 'm':
        return C[fam][1]
    if t == 'tr':
        return C[fam][2]
    if t == 'deep':
        return deep(fam)
    if t == 'w':
        return W
    if t == 'lp':
        return LP
    raise ValueError(t)


def el(s, attrs=''):
    t = s[0]
    if t == 'path':
        return f'<path d="{s[1]}" {attrs}/>'
    if t == 'circle':
        return f'<circle cx="{fmt(s[1])}" cy="{fmt(s[2])}" r="{fmt(s[3])}" {attrs}/>'
    if t == 'ellipse':
        tf = f' transform="rotate({fmt(s[5])} {fmt(s[1])} {fmt(s[2])})"' if len(s) > 5 and s[5] else ''
        return f'<ellipse cx="{fmt(s[1])}" cy="{fmt(s[2])}" rx="{fmt(s[3])}" ry="{fmt(s[4])}"{tf} {attrs}/>'
    if t == 'rect':
        x, y, w, h = s[1:5]
        r = s[5] if len(s) > 5 else 0
        tf = f' transform="rotate({fmt(s[6])} {fmt(x + w / 2)} {fmt(y + h / 2)})"' if len(s) > 6 and s[6] else ''
        rr = f' rx="{fmt(r)}"' if r else ''
        return f'<rect x="{fmt(x)}" y="{fmt(y)}" width="{fmt(w)}" height="{fmt(h)}"{rr}{tf} {attrs}/>'
    raise ValueError(t)


NUM = re.compile(r'[A-Za-z]|-?\d*\.?\d+(?:e-?\d+)?')


def bbox(s):
    t = s[0]
    if t == 'circle':
        return s[1] - s[3], s[2] - s[3], s[1] + s[3], s[2] + s[3]
    if t == 'ellipse':
        r = max(s[3], s[4]) if (len(s) > 5 and s[5]) else None
        if r:
            return s[1] - r, s[2] - r, s[1] + r, s[2] + r
        return s[1] - s[3], s[2] - s[4], s[1] + s[3], s[2] + s[4]
    if t == 'rect':
        return s[1], s[2], s[1] + s[3], s[2] + s[4]
    # path: crude bbox from absolute coords (we only author absolute commands, A/a args handled)
    toks = NUM.findall(s[1])
    xs, ys, cmd, nums = [], [], None, []

    def flush():
        if cmd is None:
            return
        if cmd in 'MLT':
            for i in range(0, len(nums) - 1, 2):
                xs.append(nums[i]); ys.append(nums[i + 1])
        elif cmd in 'CSQ':
            for i in range(0, len(nums) - 1, 2):
                xs.append(nums[i]); ys.append(nums[i + 1])
        elif cmd == 'H':
            xs.extend(nums)
        elif cmd == 'V':
            ys.extend(nums)
        elif cmd == 'A':
            for i in range(0, len(nums), 7):
                if i + 6 < len(nums):
                    xs.append(nums[i + 5]); ys.append(nums[i + 6])
    for tk in toks:
        if re.match(r'[A-Za-z]', tk):
            flush(); cmd = tk; nums = []
        else:
            nums.append(float(tk))
    flush()
    return min(xs), min(ys), max(xs), max(ys)


class P:
    """A part. kind: 'fill' (closed shape) | 'tube' (open path, outlined stroke) | 'line' (plain stroke)."""

    def __init__(self, s, fam, t='p', band=None, band_t=None, tex=(), tex_t=None, det=(), det_t=None, ol=2.0,
                 kind='fill', w=0, cut=True, det_w=1.5, tex_w=2.0, op=None, tf=None, sband=None):
        self.s, self.fam, self.t, self.band, self.band_t = s, fam, t, band, band_t
        self.tex, self.tex_t, self.det, self.det_t, self.ol = list(tex), tex_t, list(det), det_t, ol
        self.kind, self.w, self.cut, self.det_w, self.tex_w, self.op, self.tf = kind, w, cut, det_w, tex_w, op, tf
        # screen-space band for rotated parts (clip to the lower edge as drawn)
        self.sband = sband if sband is not None else bool(tf and 'rotate' in tf)


def F(s, fam, t='p', **k):
    return P(s, fam, t, **k)


def TUBE(d, fam, w=5, t='p', **k):
    return P(('path', d), fam, t, kind='tube', w=w, **k)


def LINE(d, fam, w=2, t='tr', **k):
    return P(('path', d), fam, t, kind='line', w=w, **k)


def G(d, w=2.6, tf=None):
    return ('g', d, w, tf) if tf else ('g', d, w)


def DOT(x, y, r=.9, tf=None):
    return ('dot', x, y, r, tf) if tf else ('dot', x, y, r)


# ---------- transforms (for screen-space bands and lint) ----------
import math


def parse_tf(tf):
    M = [1, 0, 0, 1, 0, 0]

    def mul(A, B):
        a, b, c, d, e, f = A
        a2, b2, c2, d2, e2, f2 = B
        return [a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2, a * e2 + c * f2 + e, b * e2 + d * f2 + f]
    for name, args in re.findall(r'(\w+)\(([^)]*)\)', tf or ''):
        v = [float(x) for x in re.split(r'[\s,]+', args.strip()) if x]
        if name == 'translate':
            T = [1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0]
        elif name == 'scale':
            T = [v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0]
        elif name == 'rotate':
            a = math.radians(v[0]); ca, sa = math.cos(a), math.sin(a)
            cx, cy = (v[1], v[2]) if len(v) > 2 else (0, 0)
            T = mul(mul([1, 0, 0, 1, cx, cy], [ca, sa, -sa, ca, 0, 0]), [1, 0, 0, 1, -cx, -cy])
        else:
            continue
        M = mul(M, T)
    return M


def apply_tf(M, x, y):
    return M[0] * x + M[2] * y + M[4], M[1] * x + M[3] * y + M[5]


def sample_pts(s):
    t = s[0]
    if t in ('circle', 'ellipse'):
        cx, cy = s[1], s[2]
        rx, ry = (s[3], s[3]) if t == 'circle' else (s[3], s[4])
        rot = s[5] if t == 'ellipse' and len(s) > 5 and s[5] else 0
        M = parse_tf(f'rotate({rot} {cx} {cy})')
        return [apply_tf(M, cx + rx * math.cos(i * math.pi / 12), cy + ry * math.sin(i * math.pi / 12)) for i in range(24)]
    if t == 'rect':
        x, y, w, h = s[1:5]
        rot = s[6] if len(s) > 6 and s[6] else 0
        M = parse_tf(f'rotate({rot} {x + w / 2} {y + h / 2})')
        return [apply_tf(M, px, py) for px, py in ((x, y), (x + w, y), (x + w, y + h), (x, y + h))]
    toks = NUM.findall(s[1])
    pts, cmd, nums = [], None, []

    def flush():
        if cmd and cmd in 'MLTCSQ':
            for i in range(0, len(nums) - 1, 2):
                pts.append((nums[i], nums[i + 1]))
    for tk in toks:
        if re.match(r'[A-Za-z]', tk):
            flush(); cmd = tk; nums = []
        else:
            nums.append(float(tk))
    flush()
    return pts


def endpoints(d):
    """first and last absolute point of a simple absolute path (None if relative commands)"""
    if re.search(r'[a-z]', d):
        return None
    toks = NUM.findall(d)
    cur, first, cmd, nums = None, None, None, []
    pts = []

    def flush():
        nonlocal cur
        if cmd is None:
            return
        if cmd in 'MLTCSQ' and len(nums) >= 2:
            pts.append((nums[-2], nums[-1]))
        elif cmd == 'H' and nums and pts:
            pts.append((nums[-1], pts[-1][1]))
        elif cmd == 'V' and nums and pts:
            pts.append((pts[-1][0], nums[-1]))
        elif cmd == 'A' and len(nums) >= 7:
            pts.append((nums[-2], nums[-1]))
    for tk in toks:
        if re.match(r'[A-Za-z]', tk):
            flush(); cmd = tk; nums = []
        else:
            nums.append(float(tk))
    flush()
    m = re.match(r'\s*M\s*(-?[\d.]+)[\s,]+(-?[\d.]+)', d)
    if not m or not pts:
        return None
    return (float(m.group(1)), float(m.group(2))), pts[-1]


SH = f'var(--sk-shadow,{SHADOW})'
RJ = ''   # round joins/caps are inherited from the wrapper set on every symbol / basket group
RJA = 'stroke-linejoin="round" stroke-linecap="round" stroke-width="2"'
_CODES = {}


def code(key):
    if key not in _CODES:
        n = len(_CODES); c = ''
        while True:
            c = 'abcdefghijklmnopqrstuvwxyz'[n % 26] + c; n //= 26
            if not n:
                break
        _CODES[key] = c
    return _CODES[key]


def kid(key, prefix='sticker'):
    return f'{prefix}-k{code(key)}'


def cut_uses(kid, cw=8, shadow=True, white=True):
    out = ''
    if shadow:
        out += f'<use href="#{kid}" transform="translate(1.6 2.2)" style="--cw:{cw}px;fill:{SH};stroke:{SH}" stroke-width="{cw}" />'
    if white:
        out += f'<use href="#{kid}" style="--cw:{cw}px" fill="#fff" stroke="#fff" stroke-width="{cw}" />'
    return out


class Art:
    """Parts rendered with shared shape definitions: each fill shape is written once (with an id) and referenced
    by the cut silhouette, the fill, the clip, the trace outline, the mono and the ghost."""

    def __init__(self, key, parts, cutx=(), prefix='sticker'):
        self.key, self.parts, self.cutx, self.prefix = key, parts, list(cutx), prefix
        self.kid = kid(key, prefix)

    def sid(self, i):
        return f'{self.prefix}-s{code(self.key)}{i}'

    def defs(self):
        cut, hidden = [], []
        for i, p in enumerate(self.parts):
            if p.kind == 'fill':
                e = el(p.s, f'id="{self.sid(i)}"')
                if p.cut:
                    cut.append(f'<g transform="{p.tf}">{e}</g>' if p.tf else e)
                else:
                    hidden.append(e)
            elif p.cut:
                e = el(p.s, f'fill="none" style="stroke-width:calc(var(--cw) + {fmt(p.w)}px)"')
                ep = endpoints(p.s[1])
                if ep:
                    e += ''.join(f'<circle cx="{fmt(x)}" cy="{fmt(y)}" r="{fmt(p.w / 2)}"/>' for x, y in ep)
                cut.append(f'<g transform="{p.tf}">{e}</g>' if p.tf else e)
        for s in self.cutx:
            cut.append(el(s))
        out = f'<g id="{self.kid}">{"".join(cut)}</g>'
        if hidden:
            out += f'<g>{"".join(hidden)}</g>'
        return out

    def art(self, idx=None, stage=6, clips=None):
        clips = clips if clips is not None else []
        out = []
        for i, p in enumerate(self.parts):
            if idx is not None and i not in idx:
                continue
            out.append(self.part(i, p, stage, clips))
        return ''.join(out)

    def part(self, i, p, stage, clips):
        fam = p.fam
        tr = C[fam][2] if fam in C else fam
        op = f' opacity="{p.op}"' if p.op else ''
        wrap = (lambda s: f'<g transform="{p.tf}">{s}</g>') if p.tf else (lambda s: s)
        if p.kind == 'line':
            if stage < 5:
                return ''
            col = tone(fam, p.t)
            return wrap(f'<g{op}>' + el(p.s, f'fill="none" stroke="{col}" stroke-width="{fmt(p.w)}"') + '</g>')
        if p.kind == 'tube':
            col = tone(fam, p.t)
            if stage < 5:
                return wrap(el(p.s, f'fill="none" stroke="{col}" stroke-width="{fmt(p.w)}"'))
            o = el(p.s, f'fill="none" stroke="{tr}" stroke-width="{fmt(p.w)}"')
            ii = el(p.s, f'fill="none" stroke="{col}" stroke-width="{fmt(max(p.w - 4, .8))}"')
            extra = ''.join(f'<path d="{d}" fill="none" stroke="{tone(fam, p.det_t) if p.det_t else tr}" stroke-width="{fmt(p.det_w)}" />' for d in p.det)
            return wrap(f'<g{op}>{o}{ii}{extra}</g>')
        sid = self.sid(i)
        fill = tone(fam, p.t)
        local, screen = [f'<use href="#{sid}" fill="{fill}"/>'], ''
        need_clip = stage >= 4 and (p.band is not None or p.tex)
        if need_clip:
            cid = f'{self.prefix}-c{code(self.key)}{i}'
            tex = ''
            if p.tex:
                tc = tone(fam, p.tex_t) if p.tex_t else (C[fam][1] if p.t in ('p', 'w', 'lp') else C[fam][0])
                tex = ''.join(f'<path d="{d}" fill="none" stroke="{tc}" stroke-width="{fmt(p.tex_w)}" stroke-linecap="round"/>' for d in p.tex)
            band = ''
            bc = None
            if p.band is not None:
                bt = p.band_t or ('m' if p.t == 'p' else 'p' if p.t in ('w',) else 'deep' if p.t == 'm' else 'm')
                bc = tone(fam, bt)
            if p.tf and p.sband and p.band is not None and not isinstance(p.band, str):
                # band computed in screen space, clip carries the transform
                M = parse_tf(p.tf)
                pts = [apply_tf(M, x, y) for x, y in sample_pts(p.s)]
                x0, x1 = min(x for x, _ in pts), max(x for x, _ in pts)
                y0, y1 = min(y for _, y in pts), max(y for _, y in pts)
                y = y1 - (y1 - y0) * p.band
                clips.append(f'<clipPath id="{cid}"><use href="#{sid}" transform="{p.tf}"/></clipPath>')
                band = f'<rect x="{fmt(x0 - 2)}" y="{fmt(y)}" width="{fmt(x1 - x0 + 4)}" height="{fmt(y1 - y + 2)}" fill="{bc}"/>'
                texg = ('<g transform="' + p.tf + '">' + tex + '</g>') if tex else ''
                screen = f'<g clip-path="url(#{cid})">{texg}{band}</g>'
            else:
                clips.append(f'<clipPath id="{cid}"><use href="#{sid}"/></clipPath>')
                if p.band is not None:
                    if isinstance(p.band, str):
                        band = f'<path d="{p.band}" fill="{bc}"/>'
                    else:
                        x0, y0, x1, y1 = bbox(p.s)
                        y = y1 - (y1 - y0) * p.band
                        band = f'<rect x="{fmt(x0 - 2)}" y="{fmt(y)}" width="{fmt(x1 - x0 + 4)}" height="{fmt(y1 - y + 2)}" fill="{bc}"/>'
                local.append(f'<g clip-path="url(#{cid})">{tex}{band}</g>')
        trace = []
        if stage >= 5:
            if p.ol:
                sw = '' if p.ol == 2 else f' stroke-width="{fmt(p.ol)}"'
                trace.append(f'<use href="#{sid}" fill="none" stroke="{tr}"{sw}/>')
            if p.det:
                dc = tone(fam, p.det_t) if p.det_t else tr
                trace.append(''.join(f'<path d="{d}" fill="none" stroke="{dc}" stroke-width="{fmt(p.det_w)}" />' for d in p.det))
        if screen:
            res = wrap(''.join(local)) + screen + (wrap(''.join(trace)) if trace else '')
        else:
            res = wrap(''.join(local) + ''.join(trace))
        return f'<g{op}>{res}</g>' if op else res

    def mono_body(self, lw=1.3):
        out = [f'<use href="#{self.kid}" style="--cw:3px" fill="{BP}" stroke="{BP}" stroke-width="3"/>', f'<g fill="none" stroke="#fff" stroke-width="{lw}">']
        for i, p in enumerate(self.parts):
            seg = ''
            if p.kind == 'fill':
                seg += f'<use href="#{self.sid(i)}"/>'
                seg += ''.join(f'<path d="{d}"/>' for d in p.det)
            elif p.kind == 'tube':
                seg += el(p.s, f'stroke-width="{fmt(max(p.w - 3.4, .9))}"')
            if seg:
                out.append(f'<g transform="{p.tf}">{seg}</g>' if p.tf else seg)
        out.append('</g>')
        return f'<g {RJA}>' + ''.join(out) + '</g>'


def gloss_svg(gl):
    out = []
    for g in gl:
        tf = f' transform="{g[-1]}"' if isinstance(g[-1], str) and len(g) in (4, 5) else ''
        if g[0] == 'g':
            out.append(f'<path d="{g[1]}" fill="none" stroke="#fff" stroke-width="{fmt(g[2])}" {tf}/>')
        else:
            out.append(f'<circle cx="{fmt(g[1])}" cy="{fmt(g[2])}" r="{fmt(g[3])}" fill="#fff"{tf}/>')
    return ''.join(out)


class Icon:
    def __init__(self, key, label, parts, gloss=(), cutx=(), qty='', aisle='', fam=None, subs=2):
        self.key, self.label, self.parts, self.gloss, self.cutx = key, label, parts, list(gloss), list(cutx)
        self.qty, self.aisle = qty, aisle
        self.fam = fam
        self.subs = subs          # the most sub-glosses autogloss may add (a bunch of leaves reads cleaner with one)

    def artobj(self, prefix='sticker'):
        return Art(self.key, self.parts, self.cutx, prefix)

    def render(self, prefix='sticker', stage=6, cut=True):
        """returns (defs, body). stage: 1 shadow, 2 +white, 3 +fills, 4 +band/texture, 5 +trace, 6 +gloss"""
        a = self.artobj(prefix)
        clips = []
        body = ''
        if cut and stage >= 1:
            body += cut_uses(a.kid, 8, shadow=True, white=stage >= 2)
        if stage >= 3:
            body += a.art(stage=stage, clips=clips)
        if stage >= 6:
            body += gloss_svg(self.gloss)
        return ''.join(clips) + a.defs(), body

    def mono(self, prefix='sticker'):
        return self.artobj(prefix).mono_body()


def symbol(icon, prefix='sticker', stage=6, cut=True, sid=None):
    d, body = icon.render(prefix, stage, cut)
    return d, f'<symbol id="{sid or prefix + "-i-" + icon.key}" viewBox="-2 -2 52 52"><g {RJA}>{body}</g></symbol>'


def berry(cx, top, w, h):
    """strawberry body: broad shoulders, rounded tip"""
    l, r = cx - w / 2, cx + w / 2
    return ('path', f'M{fmt(l)} {fmt(top + h * .28)}C{fmt(l)} {fmt(top - h * .04)} {fmt(r)} {fmt(top - h * .04)} {fmt(r)} {fmt(top + h * .28)}'
                    f'C{fmt(r)} {fmt(top + h * .62)} {fmt(cx + w * .16)} {fmt(top + h)} {fmt(cx)} {fmt(top + h)}'
                    f'C{fmt(cx - w * .16)} {fmt(top + h)} {fmt(l)} {fmt(top + h * .62)} {fmt(l)} {fmt(top + h * .28)}Z')


def cyl(x0, x1, top, bot, ry=None):
    """cylinder body (front) with rounded bottom; top ellipse separate"""
    rx = (x1 - x0) / 2
    ry = ry if ry is not None else rx / 3
    return ('path', f'M{fmt(x0)} {fmt(top)}V{fmt(bot - ry)}A{fmt(rx)} {fmt(ry)} 0 0 0 {fmt(x1)} {fmt(bot - ry)}V{fmt(top)}Z')


def ell_top(x0, x1, top, ry=None):
    rx = (x1 - x0) / 2
    ry = ry if ry is not None else rx / 3
    return ('ellipse', x0 + rx, top, rx, ry)


def star_path(cx, cy, R, r, n=5, rot=-90):
    import math
    pts = []
    for i in range(n * 2):
        a = math.radians(rot + i * 180 / n)
        rr = R if i % 2 == 0 else r
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return 'M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in pts) + 'Z'


def heart_path(cx, cy, s):
    """heart centred at cx,cy, width ~2s"""
    return (f'M{fmt(cx)} {fmt(cy + s * .95)}C{fmt(cx - s * .35)} {fmt(cy + s * .65)} {fmt(cx - s)} {fmt(cy + s * .2)} {fmt(cx - s)} {fmt(cy - s * .3)}'
            f'C{fmt(cx - s)} {fmt(cy - s * .85)} {fmt(cx - s * .25)} {fmt(cy - s * .95)} {fmt(cx)} {fmt(cy - s * .45)}'
            f'C{fmt(cx + s * .25)} {fmt(cy - s * .95)} {fmt(cx + s)} {fmt(cy - s * .85)} {fmt(cx + s)} {fmt(cy - s * .3)}'
            f'C{fmt(cx + s)} {fmt(cy + s * .2)} {fmt(cx + s * .35)} {fmt(cy + s * .65)} {fmt(cx)} {fmt(cy + s * .95)}Z')


# ---------- organic path helpers ----------
def smooth(pts, closed=True, k=1.0):
    """Catmull-Rom through pts -> cubic bezier path"""
    n = len(pts)
    P_ = pts + pts[:2] if closed else [pts[0]] + pts + [pts[-1]]
    out = f'M{fmt(pts[0][0])} {fmt(pts[0][1])}'
    rng = range(n) if closed else range(n - 1)
    for i in rng:
        if closed:
            p0, p1, p2, p3 = pts[(i - 1) % n], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        else:
            p0, p1, p2, p3 = P_[i], P_[i + 1], P_[i + 2], P_[i + 3]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6 * k, p1[1] + (p2[1] - p0[1]) / 6 * k)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6 * k, p2[1] - (p3[1] - p1[1]) / 6 * k)
        out += f'C{fmt(c1[0])} {fmt(c1[1])} {fmt(c2[0])} {fmt(c2[1])} {fmt(p2[0])} {fmt(p2[1])}'
    return out + ('Z' if closed else '')


def blob(cx, cy, rx, ry, n=8, amp=.1, seed=1, rot=0):
    import random as _r
    g = _r.Random(seed)
    pts = []
    for i in range(n):
        a = math.radians(rot) + i * 2 * math.pi / n
        f = 1 + g.uniform(-amp, amp)
        pts.append((cx + rx * f * math.cos(a), cy + ry * f * math.sin(a)))
    return smooth(pts)


def ruffle(cx, cy, rx, ry, n=10, amp=.12, a0=180, a1=360, base=None):
    """ruffled dome: scallops along the arc a0..a1 (deg), closed by a flat base line at y=base"""
    pts = []
    steps = n * 2
    for i in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * i / steps)
        f = 1 + (amp if i % 2 else -amp * .3)
        pts.append((cx + rx * f * math.cos(a), cy + ry * f * math.sin(a)))
    return pts


def scallop_top(x0, x1, y, n, h):
    """a row of n bumps from x0 to x1 at baseline y, bumps height h (path segment, absolute, starting at x0,y)"""
    w = (x1 - x0) / n
    s = ''
    for i in range(n):
        xa = x0 + i * w
        s += f'C{fmt(xa)} {fmt(y - h * 1.3)} {fmt(xa + w)} {fmt(y - h * 1.3)} {fmt(xa + w)} {fmt(y)}'
    return s


def dots_ring(cx, cy, r, n, a0=0):
    return ''.join(f'M{fmt(cx + r * math.cos(math.radians(a0 + i * 360 / n)))} {fmt(cy + r * math.sin(math.radians(a0 + i * 360 / n)))}h.1' for i in range(n))
