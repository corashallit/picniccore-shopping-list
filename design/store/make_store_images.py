#!/usr/bin/env python3
"""Google Play listing images for Kirakira Basket, in the layout fastlane supply reads.

    python3 design/store/make_store_images.py            # writes fastlane/metadata/android/en-US/images/
    python3 design/store/make_store_images.py --sheet x.png   # also a contact sheet of the set, for review

Everything is drawn from what the repository already has, so the images can be refreshed whenever the app changes:

- the real screens are the app's own golden renders (app/src/test/screenshots, 1080 x 2280, Day and Glow);
- the lettering is DynaPuff and Zen Maru Gothic from app/src/main/res/font, in the app's puffy-title style;
- the stickers, washi tapes and the launcher icon are the generated VectorDrawables in app/src/main/res/drawable
  (from the sticker DSL in design/stickers), read back through design/stickers/tools/vd2svg.py and rasterised by
  cairosvg.

The output is deterministic: no randomness and no timestamps, so the same inputs (and the same Pillow, cairosvg and
Cairo versions) always give the same bytes. Requirements: design/store/requirements.txt.
"""
import argparse
import io
import math
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
GOLDENS = REPO / 'app/src/test/screenshots'
FONTS = REPO / 'app/src/main/res/font'
DRAWABLE = REPO / 'app/src/main/res/drawable'
OUT = REPO / 'fastlane/metadata/android/en-US/images'

sys.path.insert(0, str(REPO / 'design/stickers/tools'))
import vd2svg  # noqa: E402  (the sticker tools' VectorDrawable -> SVG reader)

try:
    import cairosvg  # noqa: E402
except ImportError:  # pragma: no cover
    sys.exit('make_store_images.py needs cairosvg: pip install -r design/store/requirements.txt')

# ---------------------------------------------------------------------------------------------------------------------
# Tokens (docs/DESIGN_STICKER.md §2.1, §2.2, §2.4, §2.10)
# ---------------------------------------------------------------------------------------------------------------------
BALLPOINT = '#25307C'
BALLPOINT_SOFT = '#4F5BA6'
STICKER_SHADOW = '#6E7BD0'
WHITE = '#FFFFFF'
FAMILIES = {  # pastel, mid, deep, trace
    'strawberry': ('#FFC4D1', '#FF6F8E', '#EC587A', '#C3264F'),
    'peach': ('#FFDCC6', '#FFA477', '#E5885A', '#AE4D1C'),
    'lemon': ('#FFF3A6', '#FFDA4A', '#DAB632', '#8A6800'),
    'melon': ('#D3F4C5', '#8EDB76', '#6FBF62', '#2E8239'),
    'mint': ('#C9F2E3', '#6DD3B0', '#53B594', '#1C7558'),
    'ramune': ('#CCEBFF', '#7CC6F5', '#5EA9DB', '#1E6AA3'),
    'periwinkle': ('#DCE1FF', '#9AA8F5', '#7D8ADF', '#3E4BB0'),
    'grape': ('#E4D6FF', '#B597F2', '#9B7CDE', '#6542B3'),
    'sakura': ('#FFD6E8', '#FF9FC8', '#E67BAA', '#B02E6B'),
    'cocoa': ('#F1DEC9', '#CB9C73', '#B1825B', '#7A4A28'),
}
DAY_BINDER, DAY_STRIPE = '#E6F6EE', '#CDEEDD'

W, H = 1080, 1920          # phone screenshots
FG_W, FG_H = 1024, 500     # feature graphic
ICON = 512                 # hi-res icon


def rgb(c):
    c = c.lstrip('#')
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    """a blended toward b by t (0 = a, 1 = b), as an (r, g, b) tuple."""
    a, b = rgb(a) if isinstance(a, str) else a, rgb(b) if isinstance(b, str) else b
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


# ---------------------------------------------------------------------------------------------------------------------
# Vector art: the app's own drawables
# ---------------------------------------------------------------------------------------------------------------------
_art_cache = {}


def svg_png(svg, w=None, h=None):
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=w, output_height=h)
    return Image.open(io.BytesIO(png)).convert('RGBA')


def drawable(name, px, shadow=STICKER_SHADOW, offset=(1.6, 2.2), pad=4.0):
    """The drawable `name` (sk_*), its viewport scaled to `px` wide, with its hard die-cut shadow as the app draws a
    sticker (§3.3: the `cut` group tinted at +1.6, +2.2), on a transparent canvas padded by `pad` viewport units."""
    key = (name, px, shadow, offset, pad)
    if key not in _art_cache:
        xml = (DRAWABLE / f'{name}.xml').read_text()
        root = ET.fromstring(xml)
        vw = float(root.get(vd2svg.A + 'viewportWidth'))
        vh = float(root.get(vd2svg.A + 'viewportHeight'))
        svg = vd2svg.convert(xml, shadow=shadow, offset=offset)
        s = px / vw
        svg = re.sub(r'width="[^"]+" height="[^"]+" viewBox="[^"]+"',
                     f'width="{(vw + 2 * pad) * s:.3f}" height="{(vh + 2 * pad) * s:.3f}" '
                     f'viewBox="{-pad} {-pad} {vw + 2 * pad} {vh + 2 * pad}"', svg, count=1)
        _art_cache[key] = svg_png(svg)
    return _art_cache[key].copy()


def sticker(name, px, angle=0.0):
    """A library or decor sticker `px` wide (its 52-unit box), optionally turned by `angle` degrees (anticlockwise)."""
    big = drawable(name, px * 2)
    if angle:
        big = big.rotate(angle, resample=Image.BICUBIC, expand=True)
    return big.resize((big.width // 2, big.height // 2), Image.LANCZOS)


def sparkle(px, fill, trace, angle=0.0):
    """The wordmark's twinkle (Decor.kt TwinkleSparkle): a four-point sparkle, white border, colour-traced, with the
    hard shadow."""
    r = 50.0

    def path(rr):
        kk = rr * 0.22
        return (f'M50 {50 - rr}Q{50 + kk} {50 - kk} {50 + rr} 50Q{50 + kk} {50 + kk} 50 {50 + rr}'
                f'Q{50 - kk} {50 + kk} {50 - rr} 50Q{50 - kk} {50 - kk} 50 {50 - rr}Z')
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{px * 2}" height="{px * 2}" viewBox="-8 -8 120 120">'
           f'<path d="{path(r)}" fill="{STICKER_SHADOW}" transform="translate(3.2 4.4)"/>'
           f'<path d="{path(r)}" fill="#FFFFFF"/>'
           f'<path d="{path(r * .8)}" fill="{fill}" stroke="{trace}" stroke-width="{r * .09:.2f}" stroke-linejoin="round"/>'
           f'</svg>')
    big = svg_png(svg)
    if angle:
        big = big.rotate(angle, resample=Image.BICUBIC, expand=True)
    return big.resize((big.width // 2, big.height // 2), Image.LANCZOS)


# ---------------------------------------------------------------------------------------------------------------------
# Washi tape (§2.12, Washi.kt): an 18 dp strip of the tape's tile, zig-zag torn ends with 3 dp teeth, 75-90 % opaque
# ---------------------------------------------------------------------------------------------------------------------
def washi(pattern, length, dp=3.0, angle=0.0, alpha=0.88):
    name = f'sk_t_washi_{pattern}'
    xml = (DRAWABLE / f'{name}.xml').read_text()
    root = ET.fromstring(xml)
    tw = float(root.get(vd2svg.A + 'viewportWidth'))
    th = float(root.get(vd2svg.A + 'viewportHeight'))
    h = round(18 * dp)
    scale = h / th if th > 18 else dp                       # motif tiles (24 x 20) fill the strip's height
    tile = svg_png(vd2svg.convert(xml, scale=1.0), round(tw * scale), round(th * scale))
    strip = Image.new('RGBA', (length, h), (255, 255, 255, 255))
    for x in range(0, length, tile.width):
        for y in range(0, h, tile.height):
            strip.alpha_composite(tile, (x, y))
    # torn outline (Washi.kt washiOutline), drawn at 4x for smooth teeth
    ss = 4
    tooth = 3 * dp * ss
    Wl, Hl = length * ss, h * ss
    teeth = max(2, int(Hl // tooth))
    step = Hl / teeth
    pts = [(tooth, 0), (Wl - tooth, 0)]
    for i in range(teeth):
        pts += [(Wl - (0 if i % 2 == 0 else tooth), step * (i + .5)), (Wl - tooth, step * (i + 1))]
    pts += [(tooth, Hl)]
    for i in range(teeth - 1, -1, -1):
        pts += [((0 if i % 2 == 0 else tooth), step * (i + .5)), (tooth, step * i)]
    mask = Image.new('L', (Wl, Hl), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=round(255 * alpha))
    mask = mask.resize((length, h), Image.LANCZOS)
    strip.putalpha(ImageChops.multiply(strip.getchannel('A'), mask))
    if angle:
        strip = strip.rotate(angle, resample=Image.BICUBIC, expand=True)
    return strip


# ---------------------------------------------------------------------------------------------------------------------
# Puffy lettering (§2.7, PuffyText.kt): the stroke pass (white) on a hard shadow of the stroked word, then the fill.
# Small enclosed gaps in the stroke (between glyphs, or a counter no bigger than the pen) are closed, as PuffyText does.
# ---------------------------------------------------------------------------------------------------------------------
def font(name, size):
    layout = ImageFont.Layout.RAQM if ImageFont.core.HAVE_RAQM else ImageFont.Layout.BASIC
    return ImageFont.truetype(str(FONTS / name), size, layout_engine=layout)


def _close_pinholes(mask, max_area):
    """Fill the regions of `mask` that the outside cannot reach and that are at most `max_area` pixels."""
    inv = mask.point(lambda v: 255 if v < 128 else 0)
    w, h = inv.size
    # flood the open ground from a 1 px border
    framed = Image.new('L', (w + 2, h + 2), 255)
    framed.paste(inv, (1, 1))
    ImageDraw.floodfill(framed, (0, 0), 128)
    holes = framed.crop((1, 1, w + 1, h + 1)).point(lambda v: 255 if v == 255 else 0)
    if not holes.getbbox():
        return mask
    # label the holes one by one; fill only the small ones
    out = mask.copy()
    work = holes.copy()
    wpx = work.load()
    for y in range(h):
        for x in range(w):
            if wpx[x, y] == 255:
                ImageDraw.floodfill(work, (x, y), 100)
                region = work.point(lambda v: 255 if v == 100 else 0)
                area = region.histogram()[255]
                if area <= max_area:
                    out.paste(255, (0, 0), region)
                work.paste(0, (0, 0), region)
                wpx = work.load()
    return out


WORD_GAP = 0.2   # extra space between words, in em: the white stroke would otherwise close DynaPuff's narrow space


def _words(f, line, halo, extra):
    """Lay `line` out word by word, each space widened by `extra` px so the stroked words keep an open gap between
    them (§2.7: "the gaps between words stay open"). Returns ([(word, pen x)], ink left, ink right), where the ink
    edges are the stroked first and last words' bounding box edges, relative to a pen starting at 0."""
    space = f.getlength(' ') + extra
    placed, x = [], 0.0
    for word in line.split(' '):
        if word:
            placed.append((word, x))
            x += f.getlength(word)
        x += space
    first, last = placed[0], placed[-1]
    left = first[1] + f.getbbox(first[0], stroke_width=halo)[0]
    right = last[1] + f.getbbox(last[0], stroke_width=halo)[2]
    return placed, left, right


def puffy(lines, size, fill=BALLPOINT, stroke=WHITE, shadow=STICKER_SHADOW, fname='dynapuff_bold.ttf',
          halo=None, shadow_off=None, leading=1.15, colours=None, word_gap=WORD_GAP):
    """Centred puffy lines as one RGBA image. `halo` is half the stroke (default 12.5 % of the size: a 6 dp stroke on
    a 24 sp title); the hard shadow is the stroked word at (+0.067, +0.092) x size (hs on a 24 sp title).
    Each space is widened by `word_gap` em so neighbouring words' strokes never touch. `colours` optionally gives a
    fill per line."""
    f = font(fname, size)
    halo = round(size * 0.125) if halo is None else halo
    extra = round(size * word_gap)
    sx, sy = shadow_off or (round(size * 0.067), round(size * 0.092))
    pad = halo + 4
    layouts = [_words(f, ln, halo, extra) for ln in lines]
    widths = [right - left for _, left, right in layouts]
    line_h = round(size * leading)
    asc, desc = f.getmetrics()
    w = math.ceil(max(widths)) + 2 * pad + sx
    h = line_h * (len(lines) - 1) + asc + desc + 2 * pad + sy
    stroke_mask = Image.new('L', (w, h), 0)
    fill_layer = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ds, df = ImageDraw.Draw(stroke_mask), ImageDraw.Draw(fill_layer)
    for i, (placed, left, _) in enumerate(layouts):
        x0 = pad + (max(widths) - widths[i]) / 2 - left
        y = pad + i * line_h
        c = (colours[i] if colours else fill)
        for word, wx in placed:
            ds.text((round(x0 + wx), y), word, font=f, fill=255, stroke_width=halo, stroke_fill=255)
            df.text((round(x0 + wx), y), word, font=f, fill=c)
    stroke_mask = _close_pinholes(stroke_mask, max_area=round((halo * 1.6) ** 2))
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    out.paste(Image.new('RGBA', (w, h), rgb(shadow)), (sx, sy), stroke_mask)
    out.paste(Image.new('RGBA', (w, h), rgb(stroke)), (0, 0), stroke_mask)
    out.alpha_composite(fill_layer)
    return out


def text_width(s, size, fname='dynapuff_bold.ttf', halo=None, word_gap=WORD_GAP):
    """The stroked width of one puffy line, laid out as `puffy` lays it out (with the widened word gaps)."""
    f = font(fname, size)
    halo = round(size * 0.125) if halo is None else halo
    _, left, right = _words(f, s, halo, round(size * word_gap))
    return math.ceil(right - left)


# ---------------------------------------------------------------------------------------------------------------------
# Shapes, drawn at 4x and reduced for smooth edges
# ---------------------------------------------------------------------------------------------------------------------
SS = 4


def rr_mask(size, box, radius):
    """An 'L' mask of `size` with a rounded rectangle `box` (x0, y0, x1, y1) of `radius`, anti-aliased."""
    w, h = size
    m = Image.new('L', (w * SS, h * SS), 0)
    x0, y0, x1, y1 = box
    ImageDraw.Draw(m).rounded_rectangle((x0 * SS, y0 * SS, x1 * SS - 1, y1 * SS - 1), radius * SS, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def shift(mask, dx, dy):
    out = Image.new('L', mask.size, 0)
    out.paste(mask, (dx, dy))
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Backdrop: the binder's pinstripes in the image's pastel, and a gingham picnic blanket
# ---------------------------------------------------------------------------------------------------------------------
def pinstripes(w, h, base, stripe, pitch=30, width=6):
    im = Image.new('RGB', (w, h), base)
    d = ImageDraw.Draw(im)
    for x in range(0, w, pitch):
        d.rectangle((x, 0, x + width - 1, h), fill=stripe)
    return im


def gingham(w, h, colour, cell=34, alpha=0.42):
    """Pink-gingham style cloth: white ground, bands of `colour` at `alpha` both ways (crossings darker)."""
    base = Image.new('RGB', (w, h), WHITE)
    band = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(band)
    a = round(255 * alpha)
    for x in range(0, w, cell * 2):
        d.rectangle((x, 0, x + cell - 1, h), fill=a)
    hb = Image.new('L', (w, h), 0)
    d2 = ImageDraw.Draw(hb)
    for y in range(0, h, cell * 2):
        d2.rectangle((0, y, w, y + cell - 1), fill=a)
    col = Image.new('RGB', (w, h), colour)
    base.paste(col, (0, 0), band)
    base.paste(col, (0, 0), hb)
    return base


def blanket(w, h, colour, angle, border=14, radius=22, shadow_off=(12, 16)):
    """A gingham blanket sticker: cloth, white die-cut border, hard shadow; turned by `angle`."""
    pad = border + max(shadow_off) + 4
    cw, ch = w + 2 * pad, h + 2 * pad
    cloth = gingham(w, h, colour)
    out = Image.new('RGBA', (cw, ch), (0, 0, 0, 0))
    outer = rr_mask((cw, ch), (pad - border, pad - border, pad + w + border, pad + h + border), radius + border)
    out.paste(Image.new('RGBA', (cw, ch), rgb(STICKER_SHADOW)), (0, 0), shift(outer, *shadow_off))
    out.paste(Image.new('RGBA', (cw, ch), rgb(WHITE)), (0, 0), outer)
    inner = rr_mask((cw, ch), (pad, pad, pad + w, pad + h), radius)
    layer = Image.new('RGBA', (cw, ch), (0, 0, 0, 0))
    layer.paste(cloth, (pad, pad))
    out.paste(layer, (0, 0), inner)
    return out.rotate(angle, resample=Image.BICUBIC, expand=True)


# ---------------------------------------------------------------------------------------------------------------------
# The phone: a generic rounded phone, die-cut like a sticker (white border, hard periwinkle shadow), with the
# stickers' bonbon gloss on its frame
# ---------------------------------------------------------------------------------------------------------------------
SCREEN_W = 712
SCREEN_H = round(SCREEN_W * 2280 / 1080)   # 1503
BEZEL = 20
BODY_R = 66
SCREEN_R = 48
DIECUT = 12
PHONE_SHADOW = (11, 15)
BODY = BALLPOINT


def phone(screen):
    """`screen` (RGB, SCREEN_W x SCREEN_H) in the phone. Returns (RGBA image, (screen x, screen y) inside it)."""
    bw, bh = SCREEN_W + 2 * BEZEL, SCREEN_H + 2 * BEZEL
    pad = DIECUT + 6                         # room for the side buttons' die-cut
    cw = bw + 2 * pad + PHONE_SHADOW[0] + 8
    ch = bh + 2 * pad + PHONE_SHADOW[1] + 8
    ox, oy = pad, pad                        # body origin
    out = Image.new('RGBA', (cw, ch), (0, 0, 0, 0))
    body = rr_mask((cw, ch), (ox, oy, ox + bw, oy + bh), BODY_R)
    # side buttons on the right: volume (long) and power (short)
    buttons = Image.new('L', (cw, ch), 0)
    for y0, y1 in ((0.20, 0.30), (0.34, 0.39)):
        b = rr_mask((cw, ch), (ox + bw - 6, oy + round(bh * y0), ox + bw + 6, oy + round(bh * y1)), 5)
        buttons = ImageChops.lighter(buttons, b)
    silhouette = ImageChops.lighter(body, buttons)
    # die-cut: the silhouette grown by DIECUT (as a rounded rect around the body plus the buttons' bumps)
    cut = rr_mask((cw, ch), (ox - DIECUT, oy - DIECUT, ox + bw + DIECUT, oy + bh + DIECUT), BODY_R + DIECUT)
    for y0, y1 in ((0.20, 0.30), (0.34, 0.39)):
        b = rr_mask((cw, ch), (ox + bw - 6 - DIECUT, oy + round(bh * y0) - DIECUT, ox + bw + 6 + DIECUT,
                               oy + round(bh * y1) + DIECUT), 5 + DIECUT)
        cut = ImageChops.lighter(cut, b)
    out.paste(Image.new('RGBA', (cw, ch), rgb(STICKER_SHADOW)), (0, 0), shift(cut, *PHONE_SHADOW))
    out.paste(Image.new('RGBA', (cw, ch), rgb(WHITE)), (0, 0), cut)
    out.paste(Image.new('RGBA', (cw, ch), rgb(BODY)), (0, 0), silhouette)
    # a softer rim just inside the body edge (the frame's metal band, in periwinkle trace)
    rim = ImageChops.subtract(body, rr_mask((cw, ch), (ox + 4, oy + 4, ox + bw - 4, oy + bh - 4), BODY_R - 4))
    out.paste(Image.new('RGBA', (cw, ch), rgb(FAMILIES['periwinkle'][3])), (0, 0), rim)
    # screen
    sx, sy = ox + BEZEL, oy + BEZEL
    smask = rr_mask((cw, ch), (sx, sy, sx + SCREEN_W, sy + SCREEN_H), SCREEN_R)
    layer = Image.new('RGBA', (cw, ch), (0, 0, 0, 0))
    layer.paste(screen.convert('RGBA'), (sx, sy))
    out.paste(layer, (0, 0), smask)
    # front camera dot in the top bezel
    cam = Image.new('L', (cw * SS, ch * SS), 0)
    cx, cy, r = ox + bw / 2, oy + BEZEL / 2, 4.2
    ImageDraw.Draw(cam).ellipse(((cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS), fill=255)
    out.paste(Image.new('RGBA', (cw, ch), rgb('#0F1650')), (0, 0), cam.resize((cw, ch), Image.LANCZOS))
    # gloss: a white stroke round the top-left corner of the frame, ending in a dot (the stickers' bonbon gloss)
    gl = Image.new('L', (cw * SS, ch * SS), 0)
    gd = ImageDraw.Draw(gl)
    inset = BEZEL / 2
    gx0, gy0 = (ox + inset) * SS, (oy + inset) * SS
    rad = (BODY_R - inset) * 2 * SS
    gw = 7 * SS
    gd.arc((gx0 - gw / 2, gy0 - gw / 2, gx0 + rad + gw / 2, gy0 + rad + gw / 2), 192, 258, fill=255, width=gw)
    dot_a = 270 + 14
    dcx = gx0 + rad / 2 + rad / 2 * math.cos(math.radians(dot_a))
    dcy = gy0 + rad / 2 + rad / 2 * math.sin(math.radians(dot_a))
    dr = 3.2 * SS
    gd.ellipse((dcx - dr, dcy - dr, dcx + dr, dcy + dr), fill=255)
    # round caps on the arc
    for a in (192, 258):
        ex = gx0 + rad / 2 + rad / 2 * math.cos(math.radians(a))
        ey = gy0 + rad / 2 + rad / 2 * math.sin(math.radians(a))
        gd.ellipse((ex - gw / 2, ey - gw / 2, ex + gw / 2, ey + gw / 2), fill=255)
    gl = gl.resize((cw, ch), Image.LANCZOS).point(lambda v: round(v * 0.9))
    out.paste(Image.new('RGBA', (cw, ch), (255, 255, 255, 255)), (0, 0), gl)
    return out, (sx, sy)


def screen_from(golden, crop=None):
    """The golden scaled into the phone's screen. `crop` (x0, y0, x1, y1) picks a 1080 x 2280 window first (for the
    tall recipe render)."""
    im = Image.open(GOLDENS / golden).convert('RGBA')
    bg = Image.new('RGBA', im.size, (255, 255, 255, 255))
    bg.alpha_composite(im)
    im = bg.convert('RGB')
    if crop:
        im = im.crop(crop)
    if im.size != (1080, 2280):
        raise SystemExit(f'{golden}: expected a 1080 x 2280 screen, got {im.size}')
    return im.resize((SCREEN_W, SCREEN_H), Image.LANCZOS)


# ---------------------------------------------------------------------------------------------------------------------
# The phone screenshots
# ---------------------------------------------------------------------------------------------------------------------
CAPTION_SIZE = 82
CAPTION_TOP = 36
PHONE_TOP = 310
CAPTION_MAX_W = 1000

# Each: file stem, caption (two lines), golden, optional crop, family (backdrop colour), optional dusk (a deeper
# backdrop, for the night theme), tape, decor stickers.
# crop: 03 uses a 1080 x 2280 window of the tall recipe render (recorded on an 1800 dp phone). It starts 35 px above
# the sheet's taped top edge (a strip of the dimmed scrim, as under a phone's status bar) and ends at y 2990, in the
# blank between 'White sugar' and its size line, so no line of text is cut.
# Decor: (drawable or 'sparkle:<family>', size px, side 'L' / 'R', centre y, angle). A sticker sits in the margin
# beside the phone, its art overlapping the phone's frame but stopping DECOR_GAP px short of the screen
# (`screenshot` checks the pixels).
SHOTS = [
    dict(stem='01_list', lines=['Your list, sorted', 'aisle by aisle'], golden='list_midtrip_day.png', fam='mint',
         tape='sakura',
         decor=[('sk_i_strawberry', 176, 'L', 1520, 8), ('sk_d_bee', 132, 'R', 1250, -10), ('sk_d_ladybug', 112, 'R', 1760, 12)]),
    dict(stem='02_snap', lines=['Write it, snap it,', 'or say it out loud'], golden='snap_popped_day.png', fam='ramune',
         tape='dots',
         decor=[('sk_d_camera', 160, 'L', 1520, 8), ('sk_d_cassette', 150, 'R', 1250, -8), ('sk_d_heart', 104, 'R', 1760, 10)]),
    dict(stem='03_recipe', lines=['Turn a recipe into', 'a shopping list'], golden='recipe_card_day.png',
         crop=(0, 710, 1080, 710 + 2280), fam='lemon', tape='gingham',
         decor=[('sk_i_eggs', 168, 'L', 1520, 8), ('sk_i_flour', 150, 'R', 1250, -8), ('sk_i_maple_syrup', 124, 'R', 1760, 10)]),
    dict(stem='04_pantry', lines=['Recipes leave out', 'what\u2019s in your pantry'], golden='pantry_few.png', fam='peach',
         tape='mint',
         decor=[('sk_i_honey', 164, 'L', 1520, 8), ('sk_i_salt', 132, 'R', 1250, -8), ('sk_i_olive_oil', 128, 'R', 1760, 10)]),
    dict(stem='05_celebration', lines=['Every finished list', 'gets a celebration'], golden='celebration_strip_day_9500.png',
         fam='sakura', tape='sakura',
         decor=[('sk_i_lemonade', 150, 'L', 1520, 8), ('sparkle:lemon', 112, 'R', 1250, -8), ('sk_d_star', 104, 'R', 1760, 10)]),
    dict(stem='06_album', lines=['Collect 200+ stickers', 'in your album'], golden='album_picnic_day.png', fam='grape',
         tape='gingham',
         decor=[('sk_d_ribbon', 160, 'L', 1520, 8), ('sk_d_flower', 128, 'R', 1250, -8), ('sk_d_heart', 104, 'R', 1760, 10)]),
    dict(stem='07_glow', lines=['Glow-in-the-dark', 'night theme'], golden='list_midtrip_glow.png', fam='periwinkle',
         dusk=True, tape='dots',
         decor=[('sk_d_kira', 150, 'L', 1520, 8), ('sk_d_glowstar', 120, 'R', 1250, -8), ('sk_d_glowstar', 92, 'R', 1760, 14)]),
    dict(stem='08_surprises', lines=['18 little surprises', 'hidden in the app'], golden='egg_18_end_day.png',
         fam='melon', tape='mint',
         decor=[('sk_d_cat', 172, 'L', 1520, 6), ('sk_d_umbrella', 136, 'R', 1250, -8), ('sk_d_sun', 112, 'R', 1760, 10)]),
]
DECOR_GAP = 14     # px between a decor sticker's art and the screen's edge: it overlaps the frame, never the bezel line


def backdrop(fam, dusk=False):
    """The image's ground. `dusk` deepens the pastel toward the family's mid tone, for the night-theme image."""
    pastel, mid = FAMILIES[fam][0], FAMILIES[fam][1]
    if dusk:
        base, stripe = mix(mid, WHITE, 0.45), mix(mid, WHITE, 0.25)
    elif fam == 'mint':
        base, stripe = rgb(DAY_BINDER), rgb(DAY_STRIPE)     # the Day binder itself
    else:
        base, stripe = mix(pastel, WHITE, 0.55), mix(pastel, WHITE, 0.12)
    im = pinstripes(W, H, base, stripe).convert('RGBA')
    bl = blanket(1240, 760, mid, angle=4)
    im.alpha_composite(bl, (-90, 1240))
    return im


def paste(canvas, img, x, y):
    canvas.alpha_composite(img, (round(x), round(y)))


def decor_image(spec):
    name, px, _, _, angle = spec
    if name.startswith('sparkle:'):
        fam = name.split(':')[1]
        return sparkle(px, FAMILIES[fam][1], FAMILIES[fam][3], angle)
    return sticker(name, px, angle)


def screenshot(shot):
    canvas = backdrop(shot['fam'], shot.get('dusk', False))
    # washi at the two top corners, as if the page were taped into a scrapbook
    paste(canvas, washi(shot['tape'], 250, angle=38), -66, -40)
    paste(canvas, washi(shot['tape'], 250, angle=-38), W - 196, -40)
    # the phone
    ph, (sx, sy) = phone(screen_from(shot['golden'], shot.get('crop')))
    px = (W - (SCREEN_W + 2 * BEZEL)) / 2 - (DIECUT + 6)
    py = PHONE_TOP - (DIECUT + 6)
    paste(canvas, ph, px, py)
    screen_box = (round(px + sx), round(py + sy), round(px + sx) + SCREEN_W, round(py + sy) + SCREEN_H)
    # decor stickers in the margins, never over the screen
    for spec in shot['decor']:
        im = decor_image(spec)
        side, cy = spec[2], spec[3]
        x0, y0, x1, y1 = im.getbbox()
        x = screen_box[0] - DECOR_GAP - x1 if side == 'L' else screen_box[2] + DECOR_GAP - x0
        y = cy - (y0 + y1) / 2
        paste(canvas, im, x, y)
        art = (round(x) + x0, round(y) + y0, round(x) + x1, round(y) + y1)
        if art[0] < screen_box[2] and art[2] > screen_box[0] and art[1] < screen_box[3] and art[3] > screen_box[1]:
            raise SystemExit(f'{shot["stem"]}: decor {spec[0]} covers the screen')
        if art[0] < 0 or art[2] > W or art[1] < 0 or art[3] > H:
            raise SystemExit(f'{shot["stem"]}: decor {spec[0]} runs off the image')
    # the caption: two lines, the same size on every image
    if len(shot['lines']) != 2:
        raise SystemExit(f'{shot["stem"]}: a caption has two lines')
    for ln in shot['lines']:
        if text_width(ln, CAPTION_SIZE) > CAPTION_MAX_W:
            raise SystemExit(f'{shot["stem"]}: caption line too wide: {ln!r}')
    cap = puffy(shot['lines'], CAPTION_SIZE)
    paste(canvas, cap, (W - cap.width) / 2, CAPTION_TOP)
    return canvas.convert('RGB')


# ---------------------------------------------------------------------------------------------------------------------
# Feature graphic and icon
# ---------------------------------------------------------------------------------------------------------------------
def pad_patch(text, size, fname='zen_maru_gothic_bold.ttf', colour=BALLPOINT, padx=None, pady=None):
    """A pad patch (§4.1): white kiss-cut label, radius 9 dp, a hard shadow, ballpoint text."""
    f = font(fname, size)
    b = f.getbbox(text)
    tw, th = b[2] - b[0], b[3] - b[1]
    padx = round(size * 0.75) if padx is None else padx
    pady = round(size * 0.42) if pady is None else pady
    sx, sy = round(size * 0.16), round(size * 0.22)
    w, h = tw + 2 * padx, th + 2 * pady
    out = Image.new('RGBA', (w + sx + 2, h + sy + 2), (0, 0, 0, 0))
    m = rr_mask(out.size, (0, 0, w, h), round(size * 0.55))
    out.paste(Image.new('RGBA', out.size, rgb(STICKER_SHADOW)), (0, 0), shift(m, sx, sy))
    out.paste(Image.new('RGBA', out.size, rgb(WHITE)), (0, 0), m)
    ImageDraw.Draw(out).text((padx - b[0], pady - b[1]), text, font=f, fill=colour)
    return out


FEATURE_TAGLINE = 'Your grocery list, as a sticker book'
BOOK_GOLDEN = 'book_full.png'   # the trip's sticker book, full: date tag, stamp card and sticker grid


def book_strip(width, angle=0.0):
    """The trip's sticker book as the app draws it (the BOOK_GOLDEN render), cut out of the binder it was recorded on,
    `width` px wide and turned by `angle` degrees.

    The render's ground is the Day binder's pinstripes, which run top to bottom, so row 0 gives each column's ground
    colour. The ground connected to the image's edge becomes transparent; on the anti-aliased rim, each pixel is
    split back into the book's edge colour (its white die-cut border or its periwinkle hard shadow) and an alpha."""
    im = Image.open(GOLDENS / BOOK_GOLDEN).convert('RGB')
    w, h = im.size
    src = im.load()
    ground = [src[x, 0] for x in range(w)]
    cand = Image.new('L', (w, h), 0)
    c = cand.load()
    for y in range(h):
        for x in range(w):
            if src[x, y] == ground[x]:
                c[x, y] = 255
    framed = Image.new('L', (w + 2, h + 2), 255)
    framed.paste(cand, (1, 1))
    ImageDraw.floodfill(framed, (0, 0), 128)
    outside = framed.crop((1, 1, w + 1, h + 1)).point(lambda v: 255 if v == 128 else 0)
    rim = ImageChops.subtract(outside.filter(ImageFilter.MaxFilter(5)), outside)
    out = im.convert('RGBA')
    o, r, op = outside.load(), rim.load(), out.load()
    edges = (rgb(WHITE), rgb(STICKER_SHADOW))
    for y in range(h):
        for x in range(w):
            if o[x, y]:
                op[x, y] = (0, 0, 0, 0)
            elif r[x, y]:
                p, b = src[x, y], ground[x]
                best = None
                for f in edges:
                    d = [fi - bi for fi, bi in zip(f, b)]
                    a = sum((pi - bi) * di for pi, bi, di in zip(p, b, d)) / sum(di * di for di in d)
                    a = min(1.0, max(0.0, a))
                    err = sum((pi - (bi + a * di)) ** 2 for pi, bi, di in zip(p, b, d))
                    if best is None or err < best[0]:
                        best = (err, f, a)
                err, f, a = best
                if err < 300:            # explained by a blend of the ground and an edge colour
                    op[x, y] = f + (round(a * 255),)
    out = out.crop(out.getbbox())
    big = out.resize((width * 2, round(out.height * width * 2 / out.width)), Image.LANCZOS)
    if angle:
        big = big.rotate(angle, resample=Image.BICUBIC, expand=True)
    return big.resize((big.width // 2, big.height // 2), Image.LANCZOS)


def place_centred(canvas, img, cx, cy):
    """Paste `img` with its art's bounding box centred on (cx, cy); returns the art's box on the canvas."""
    x0, y0, x1, y1 = img.getbbox()
    x, y = round(cx - (x0 + x1) / 2), round(cy - (y0 + y1) / 2)
    canvas.alpha_composite(img, (x, y))
    return x + x0, y + y0, x + x1, y + y1


FG_MARGIN = 40   # nothing that matters comes nearer the feature graphic's edges than this
# Layout of the feature graphic: the wordmark's top, the gap under it to the tagline, the blanket's top, and the
# sticker book's width, centre y and turn (anticlockwise), its two tapes and the stickers laid beside it.
FG_WORDMARK_Y = 30
FG_TAG_GAP = 4
FG_BLANKET_Y = 300
FG_BOOK_W = 620
FG_BOOK_CY = 372
FG_BOOK_ANGLE = 2
FG_TAPE_TL = (22, 18)       # tape centres from the book's top-left and (negative) bottom-right art corners
FG_TAPE_BR = (-22, -20)
FG_STICKERS = (('sk_i_strawberry', 118, 150, 402, 10), ('sk_i_lemonade', 100, 884, 384, -12))


def feature_graphic():
    im = pinstripes(FG_W, FG_H, rgb(DAY_BINDER), rgb(DAY_STRIPE), pitch=25, width=5).convert('RGBA')
    # washi at the top corners, as on the screenshots
    paste(im, washi('sakura', 180, dp=2.4, angle=34), -52, -34)
    paste(im, washi('sakura', 180, dp=2.4, angle=-34), FG_W - 140, -34)
    # a gingham blanket along the bottom
    bl = blanket(1240, 300, FAMILIES['strawberry'][1], angle=-2.5, border=10, radius=16, shadow_off=(8, 11))
    paste(im, bl, -110, FG_BLANKET_Y)
    # the trip's real sticker book laid on the blanket, taped down at two corners
    book = book_strip(FG_BOOK_W, angle=FG_BOOK_ANGLE)
    box = place_centred(im, book, FG_W / 2, FG_BOOK_CY)
    for corner in (FG_TAPE_TL, FG_TAPE_BR):
        tape = washi('sakura', 78, dp=1.7, angle=44)
        cx = box[0] + corner[0] if corner[0] >= 0 else box[2] + corner[0]
        cy = box[1] + corner[1] if corner[1] >= 0 else box[3] + corner[1]
        place_centred(im, tape, cx, cy)
    # a few stickers on the blanket beside it
    for name, px, cx, cy, ang in FG_STICKERS:
        place_centred(im, sticker(name, px, ang), cx, cy)
    # the wordmark with its twinkles, and the tagline on a pad patch
    wm = wordmark(98)
    tw = sparkle(58, FAMILIES['lemon'][1], FAMILIES['lemon'][3])
    star = sticker('sk_d_star', 44)
    group_w = wm.width + 50
    x = (FG_W - group_w) / 2
    y = FG_WORDMARK_Y
    paste(im, wm, x, y)
    paste(im, tw, x + wm.width - 20, y - 4)
    paste(im, star, x + wm.width + 16, y + 64)
    tag = pad_patch(FEATURE_TAGLINE, 30)
    paste(im, tag, (FG_W - tag.width) / 2, y + wm.height + FG_TAG_GAP)
    if box[0] < FG_MARGIN or box[1] < FG_MARGIN or box[2] > FG_W - FG_MARGIN or box[3] > FG_H - FG_MARGIN:
        raise SystemExit(f'feature graphic: the sticker book {box} comes within {FG_MARGIN} px of the edge')
    return im.convert('RGB')


def wordmark(size):
    """'Kirakira' strawberry mid, 'Basket' ramune mid, white stroke, hard shadow (§2.7 wordmark), on one line."""
    f = font('dynapuff_bold.ttf', size)
    halo = round(size * 0.0875 * 1.15)
    sx, sy = round(size * 0.06), round(size * 0.085)
    full = 'Kirakira Basket'
    b = f.getbbox(full, stroke_width=halo)
    pad = halo + 4
    w = b[2] - b[0] + 2 * pad + sx
    asc, desc = f.getmetrics()
    h = asc + desc + 2 * pad + sy
    stroke_mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(stroke_mask).text((pad - b[0], pad), full, font=f, fill=255, stroke_width=halo, stroke_fill=255)
    stroke_mask = _close_pinholes(stroke_mask, max_area=round((halo * 1.6) ** 2))
    fill = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(fill)
    d.text((pad - b[0], pad), 'Kirakira', font=f, fill=FAMILIES['strawberry'][1])
    d.text((pad - b[0] + f.getlength('Kirakira '), pad), 'Basket', font=f, fill=FAMILIES['ramune'][1])
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    out.paste(Image.new('RGBA', (w, h), rgb(STICKER_SHADOW)), (sx, sy), stroke_mask)
    out.paste(Image.new('RGBA', (w, h), rgb(WHITE)), (0, 0), stroke_mask)
    out.alpha_composite(fill)
    return out


def launcher_icon(px=ICON):
    """The adaptive launcher icon as a launcher shows it: background and foreground layers (108 dp each), the centre
    72 dp the mask keeps, at `px` square, opaque, full bleed (Play rounds the corners itself)."""
    full = round(px * 108 / 72)
    bg = drawable('sk_l_launcher_background', full, shadow=None, pad=0)
    fg = drawable('sk_l_launcher_foreground', full, shadow=None, pad=0)
    bg = bg.resize((full, full), Image.LANCZOS) if bg.size != (full, full) else bg
    fg = fg.resize((full, full), Image.LANCZOS) if fg.size != (full, full) else fg
    bg.alpha_composite(fg)
    o = (full - px) // 2
    return bg.crop((o, o, o + px, o + px))


# ---------------------------------------------------------------------------------------------------------------------
def save_png(img, path, mode):
    path.parent.mkdir(parents=True, exist_ok=True)
    img = img.convert(mode)
    img.save(path, format='PNG', optimize=True)


def contact_sheet(paths, out):
    thumbs = [Image.open(p).convert('RGB') for p in paths]
    tw = 300
    ims = [t.resize((tw, round(t.height * tw / t.width)), Image.LANCZOS) for t in thumbs]
    gap = 16
    sheet = Image.new('RGB', (len(ims) * (tw + gap) + gap, max(i.height for i in ims) + 2 * gap), (245, 245, 245))
    for i, t in enumerate(ims):
        sheet.paste(t, (gap + i * (tw + gap), gap))
    sheet.save(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--out', type=Path, default=OUT, help='images directory (default: the fastlane en-US images)')
    ap.add_argument('--sheet', type=Path, help='also write a contact sheet of the screenshots here')
    args = ap.parse_args()
    shots_dir = args.out / 'phoneScreenshots'
    written = []
    if shots_dir.exists():
        for old in shots_dir.glob('*.png'):
            old.unlink()
    for shot in SHOTS:
        p = shots_dir / f'{shot["stem"]}.png'
        save_png(screenshot(shot), p, 'RGB')
        written.append(p)
        print('wrote', p.relative_to(REPO) if p.is_relative_to(REPO) else p)
    for name, img, mode in (('featureGraphic.png', feature_graphic(), 'RGB'), ('icon.png', launcher_icon(), 'RGBA')):
        p = args.out / name
        save_png(img, p, mode)
        print('wrote', p.relative_to(REPO) if p.is_relative_to(REPO) else p)
    if args.sheet:
        contact_sheet(written, args.sheet)


if __name__ == '__main__':
    main()
