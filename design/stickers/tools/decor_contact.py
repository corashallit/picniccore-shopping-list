#!/usr/bin/env python3
"""Decor contact sheet (WP2): every emitted decor sticker, pictogram, glyph, LCD glyph, tab pattern, washi tape and the
launcher (under the circle, squircle and teardrop masks, and themed mono), read back from the EMITTED drawables through
tools/vd2svg.py, on the Day and Glow binders. Rendered by Chromium.

    python3 design/stickers/tools/decor_contact.py [--out DIR] [--scale 2] [--quantize]

Writes decor_contact_day.png and decor_contact_glow.png to DIR (default: a temp dir).
"""
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import build_android as B  # noqa: E402
import vd2svg  # noqa: E402
from baskets_contact import GROUNDS, unique_ids, inner  # noqa: E402
from deco import G57, LCD_EXTRAS  # noqa: E402
from engine import C  # noqa: E402

TRACES = [C[f][2] for f in ('strawberry', 'ramune', 'lemon', 'melon', 'grape', 'peach', 'mint', 'sakura', 'cocoa', 'periwinkle')]
MIDS = [C[f][1] for f in ('strawberry', 'ramune', 'lemon', 'melon', 'grape', 'peach', 'mint', 'sakura', 'cocoa', 'periwinkle')]
PASTELS = [C[f][0] for f in ('strawberry', 'ramune', 'lemon', 'melon', 'grape', 'peach', 'mint', 'sakura', 'cocoa', 'periwinkle')]
# AOSP mask paths on a 100 x 100 box (config_icon_mask)
MASKS = {
    'circle': 'M50 0A50 50 0 1 1 50 100A50 50 0 1 1 50 0Z',
    'squircle': 'M50 0C10 0 0 10 0 50S10 100 50 100 100 90 100 50 90 0 50 0Z',
    'teardrop': 'M50 0A50 50 0 0 1 100 50V85A15 15 0 0 1 85 100H50A50 50 0 0 1 50 0Z',
}


def tinted(svg, colour):
    """recolour every fill and stroke (a tinted glyph or pictogram ink), keeping white paper"""
    svg = re.sub(r'fill="#(?!FFFFFF|fff\b)[0-9A-Fa-f]{6}"', f'fill="{colour}"', svg)
    return re.sub(r'stroke="#(?!FFFFFF|fff\b)[0-9A-Fa-f]{6}"', f'stroke="{colour}"', svg)


def section(title, body):
    return f'<section><h2>{title}</h2><div class="row">{body}</div></section>'


def cell(svg, label):
    return f'<figure>{svg}<figcaption>{label}</figcaption></figure>'


def lcd_text(text, p=3.0):
    """the LCD glyph table drawn the way LcdText will: 5 x 7 dots, unlit dots at 50 %, vector extras"""
    x, out = 0.0, []
    for ch in text:
        if ch in LCD_EXTRAS:
            e = LCD_EXTRAS[ch]
            sw = f' stroke="#25307C" stroke-width="{e["stroke"]}" stroke-linejoin="round"' if e['stroke'] else ''
            out.append(f'<path transform="translate({x} 0)" d="{e["d"]}" fill="#25307C" fill-rule="evenodd"{sw}/>')
            x += e['advance']
            continue
        rows = G57.get(ch, G57[' ']).split(' ')
        w = len(rows[0])
        for r, row in enumerate(rows):
            for c, v in enumerate(row):
                col = '#25307C' if v == '1' else '#B6D4A4'
                op = '' if v == '1' else ' opacity=".5"'
                out.append(f'<rect x="{x + c + .1}" y="{r + .1}" width=".8" height=".8" fill="{col}"{op}/>')
        x += w + 1
    return (f'<svg width="{(x + 1) * p}" height="{8 * p}" viewBox="-.5 -.5 {x + 1} 8" style="background:#C9E5B8;'
            f'border:3px solid #fff;border-radius:9px">{"".join(out)}</svg>')


def build_sheet(variant, read, report, scale):
    g = GROUNDS[variant]
    ink = '#25307C' if variant == 'day' else '#FFFFFF'
    parts = []
    # decor stickers at 52 and 22 dp
    cells = []
    for icon, key, *_ in report['decor']:
        svg = vd2svg.convert(read('sk_d_' + key), shadow=g['shadow'])
        big = unique_ids(svg).replace('<svg ', f'<svg style="width:{52 * scale}px;height:{52 * scale}px" ', 1)
        small = unique_ids(svg).replace('<svg ', f'<svg style="width:{22 * scale}px;height:{22 * scale}px" ', 1)
        cells.append(cell(big + small, key))
    parts.append(section(f'decor stickers (sk_d_, 52 + 22 dp) · {variant}', ''.join(cells)))
    # pictograms: in a dashed stamp circle, inked in each aisle trace (Glow: the aisle mid; the traces fall to 1.7-2.8 : 1
    # on the Glow page #222861, the mids reach 5.1-10 : 1, §4.13)
    cells = []
    from deco import PICTO
    for i, k in enumerate(PICTO):
        svg = vd2svg.convert(read('sk_p_' + k))
        if variant == 'glow':
            col = MIDS[i % 10] if k != 'capsule' else '#C9CFF5'
        else:
            col = TRACES[i % 10] if k != 'capsule' else '#4F5BA6'
        s = unique_ids(tinted(svg, col)).replace('<svg ', f'<svg style="width:{18 * scale}px;height:{18 * scale}px;transform:rotate(-10deg)" ', 1)
        stamp = (f'<span style="display:inline-grid;place-items:center;width:{24 * scale}px;height:{24 * scale}px;border-radius:50%;'
                 f'background:{g["pad"]};border:{2 * scale}px dashed #4F5BA6">{s}</span>')
        cells.append(cell(stamp, k))
    parts.append(section(f'stamp pictograms (sk_p_, 24 dp stamps, aisle {"mids" if variant == "glow" else "traces"})', ''.join(cells)))
    # glyphs
    cells = []
    for name in B.GLYPHS:
        svg = vd2svg.convert(read('sk_g_' + name))
        s = unique_ids(tinted(svg, ink)).replace('<svg ', f'<svg style="width:{24 * scale}px;height:{24 * scale}px" ', 1)
        cells.append(cell(s, name))
    parts.append(section('glyphs (sk_g_, 24 dp, ballpoint; Glow: ink)', ''.join(cells)))
    # LCD
    text = ''.join(G57) + ''.join(LCD_EXTRAS)
    lcd = (lcd_text('ABCDEFGHIJKLM') + '<br>' + lcd_text('NOPQRSTUVWXYZ') + '<br>' + lcd_text('0123456789 /:?!.-+') + '<br>'
           + lcd_text("#%&'(),·♪★●♡") + '<br>' + lcd_text('10/24★ BONUS★') + '<br>' + lcd_text('THANK YOU ♪'))
    missing = [ch for ch in "!#%&'()+,-./0123456789:?ABCDEFGHIJKLMNOPQRSTUVWXYZ·" if ch not in G57]
    parts.append(section(f'LCD glyphs ({len(G57)} dot glyphs + {len(LCD_EXTRAS)} vector extras; missing: {missing or "none"})',
                         f'<div>{lcd}</div>'))
    # tab patterns: masks printed in the aisle mid at 50 % on the aisle pastel
    cells = []
    from patterns import PATTERNS, WASHI
    for i, k in enumerate(PATTERNS):
        svg = vd2svg.convert(read('sk_t_' + k))
        m = re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', svg)
        w, h = float(m.group(1)), float(m.group(2))
        tile = 'data:image/svg+xml;utf8,' + tinted(svg, MIDS[i % 10]).replace('#FFFFFF', MIDS[i % 10]).replace('#', '%23').replace('"', "'")
        cells.append(cell(f'<span style="display:inline-block;width:{60 * scale}px;height:{32 * scale}px;border-radius:{6 * scale}px;'
                          f'background:{PASTELS[i % 10]};position:relative;overflow:hidden"><i style="position:absolute;right:0;top:0;bottom:0;'
                          f'width:{26 * scale}px;opacity:.5;background:url(&quot;{tile}&quot;) 0 0/{w * scale}px {h * scale}px"></i></span>', k))
    parts.append(section('tab patterns (sk_t_, aisle mid at 50 % on the right 26 dp)', ''.join(cells)))
    cells = []
    for k, (_, w, h) in WASHI.items():
        svg = vd2svg.convert(read('sk_t_washi_' + k))
        tile = 'data:image/svg+xml;utf8,' + svg.replace('#', '%23').replace('"', "'")
        cells.append(cell(f'<span style="display:inline-block;width:{90 * scale}px;height:{18 * scale}px;transform:rotate(-3deg);opacity:.9;'
                          f'background:url(&quot;{tile}&quot;) 0 0/{w * scale}px {h * scale}px"></span>', k))
    parts.append(section('washi tapes (sk_t_washi_, 18 dp)', ''.join(cells)))
    # launcher
    bg = inner(unique_ids(vd2svg.convert(read('sk_l_launcher_background'))))
    fg = inner(unique_ids(vd2svg.convert(read('sk_l_launcher_foreground'))))
    mono = inner(unique_ids(vd2svg.convert(read('sk_l_launcher_monochrome'))))
    cells = []
    for name, d in MASKS.items():
        cid = f'mask_{name}_{variant}'
        cells.append(cell(f'<svg width="{72 * scale}" height="{72 * scale}" viewBox="18 18 72 72"><defs><clipPath id="{cid}">'
                          f'<path transform="translate(18 18) scale(.72)" d="{d}"/></clipPath></defs><g clip-path="url(#{cid})">{bg}{fg}</g>'
                          f'<circle cx="54" cy="54" r="33" fill="none" stroke="#C8321F" stroke-width=".3" stroke-dasharray="1 1"/></svg>', name))
    for bgc, fgc, nm in (('#E4E8FF', '#25307C', 'mono (light)'), ('#25307C', '#DCE1FF', 'mono (dark)')):
        cid = f'mono_{nm[6:-1]}_{variant}'
        cells.append(cell(f'<svg width="{72 * scale}" height="{72 * scale}" viewBox="18 18 72 72"><defs><clipPath id="{cid}">'
                          f'<path transform="translate(18 18) scale(.72)" d="{MASKS["circle"]}"/></clipPath></defs><g clip-path="url(#{cid})">'
                          f'<rect width="108" height="108" fill="{bgc}"/>{tinted(mono, fgc)}</g></svg>', nm))
    parts.append(section('launcher (108 dp layers, 72 dp masked; dashed = the 66 dp safe zone)', ''.join(cells)))
    stripe = (f"background-color:{g['binder']};background-image:repeating-linear-gradient(90deg,{g['stripe']} 0 2px,"
              f"transparent 2px 10px)")
    return (f'<html><head><meta charset="utf-8"><style>body{{margin:0;padding:{8 * scale}px;{stripe};font:{6 * scale}px sans-serif;color:{ink}}}'
            f'h2{{font-size:{7 * scale}px;margin:{6 * scale}px 0 {3 * scale}px}}.row{{display:flex;flex-wrap:wrap;gap:{6 * scale}px;align-items:flex-end}}'
            f'figure{{margin:0;display:flex;flex-direction:column;align-items:center;gap:{2 * scale}px}}'
            f'figure>svg,figure>span{{flex:none}}figcaption{{font-size:{5 * scale}px}}</style></head><body>{"".join(parts)}</body></html>')


def quantize(paths):
    """256-colour PNGs (no dither): the committed review sheets stay small"""
    from PIL import Image
    for path in paths:
        Image.open(path).convert('RGB').quantize(colors=256, method=Image.Quantize.MEDIANCUT,
                                                 dither=Image.Dither.NONE).save(path, optimize=True)


def main(argv):
    out_dir = argv[argv.index('--out') + 1] if '--out' in argv else tempfile.mkdtemp(prefix='decor-')
    scale = float(argv[argv.index('--scale') + 1]) if '--scale' in argv else 2
    os.makedirs(out_dir, exist_ok=True)
    work = tempfile.mkdtemp(prefix='sheets-')     # the HTML and pages.json; only the PNGs go to --out
    report = {}
    out = B.build(report)
    read = lambda name: out[os.path.join(B.DRAWABLE, name + '.xml')]
    pages = []
    for variant in GROUNDS:
        path = os.path.join(work, f'decor_contact_{variant}.html')
        with open(path, 'w', encoding='utf-8') as f:
            f.write(build_sheet(variant, read, report, scale))
        pages.append(dict(html=path, png=os.path.join(out_dir, f'decor_contact_{variant}.png'), width=int(1000 * scale / 2)))
    with open(os.path.join(work, 'pages.json'), 'w') as f:
        json.dump(pages, f)
    subprocess.run(['node', os.path.join(HERE, 'shoot_pages.mjs'), os.path.join(work, 'pages.json')], check=True)
    if '--quantize' in argv:
        quantize([p['png'] for p in pages])
    for p in pages:
        print(p['png'])


if __name__ == '__main__':
    main(sys.argv[1:])
