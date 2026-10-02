#!/usr/bin/env python3
"""Round-trip check: every emitted VectorDrawable, converted back to SVG (tools/vd2svg.py, drawn the way the app draws
it: shadow from the tinted `cut`, then the drawable), against the DSL's own SVG (the mockup's symbol), both rendered
by Chromium at 8x. Prints the per-image difference and writes side-by-side sheets of the worst ones.

    python3 design/stickers/tools/roundtrip.py [--out DIR] [--scale 8] [keys...]

Needs Node with Playwright (`npm i playwright && npx playwright install chromium`, or NODE_PATH pointing at a global
install, or PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs)."""
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import build_android as B  # noqa: E402
import engine  # noqa: E402
from engine import symbol, RJA  # noqa: E402
import vd2svg  # noqa: E402

BG = '#7A7A7A'


def dsl_icon_svg(icon, size):
    clips, sym = symbol(icon)
    holo = ''
    if 'sticker-holo' in sym:
        holo = ('<linearGradient id="sticker-holo" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFD6E8"/><stop offset=".25" stop-color="#CCEBFF"/>'
                '<stop offset=".5" stop-color="#C9F2E3"/><stop offset=".72" stop-color="#FFF3A6"/><stop offset="1" stop-color="#E4D6FF"/></linearGradient>')
    sid = re.search(r'<symbol id="([^"]+)"', sym).group(1)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="-2 -2 52 52"><defs>{holo}{clips}{sym}</defs>'
            f'<use href="#{sid}" width="52" height="52" x="-2" y="-2"/></svg>')


def dsl_basket_svg(b, layer, scale):
    clips, defs = b.defs()
    gid = {'back': 'back', 'front': 'front', 'lid_open': 'lidopen', 'lid_closed': 'lidclosed'}[layer]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{80 * scale}" height="{64 * scale}" viewBox="-8 -9 80 64"><defs>{clips}{defs}</defs>'
            f'<use href="#sticker-bk-{b.key}-{gid}"/></svg>')


def pairs(scale, only=None):
    report = {}
    out = B.build(report)
    res = lambda name: out[os.path.join(B.DRAWABLE, name + '.xml')]
    items = []
    for icon, key, *_ in report['stickers']:
        name = 'sk_i_' + B.res_name(key)
        items.append((name, dsl_icon_svg(icon, 52 * scale), vd2svg.convert(res(name), shadow=engine.SHADOW, scale=scale)))
    for icon, key, *_ in report['decor']:
        name = 'sk_d_' + key
        items.append((name, dsl_icon_svg(icon, 52 * scale), vd2svg.convert(res(name), shadow=engine.SHADOW, scale=scale)))
    for b, kind, layers, _ in report['baskets']:
        for layer in layers:
            if layer == 'deco':
                continue
            name = f'sk_b_{kind}_{layer}'
            shadow = engine.SHADOW if layer != 'front' else None
            items.append((name, dsl_basket_svg(b, layer, scale), vd2svg.convert(res(name), shadow=shadow, scale=scale)))
    from deco import PICTO, TB, picto_symbols, tb_symbols
    for k in PICTO:
        name = 'sk_p_' + k
        dsl = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{24 * scale}" height="{24 * scale}" viewBox="0 0 24 24" style="color:#25307C">'
               f'<defs>{picto_symbols()}</defs><use href="#sticker-pg-{k}"/></svg>')
        items.append((name, dsl, vd2svg.convert(res(name), scale=scale)))
    for spec, k in B.GLYPHS.items():
        name = 'sk_g_' + spec
        dsl = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{24 * scale}" height="{24 * scale}" viewBox="0 0 24 24" style="color:#25307C">'
               f'<defs>{tb_symbols()}</defs><use href="#sticker-tb-{k}"/></svg>')
        items.append((name, dsl, vd2svg.convert(res(name), scale=scale)))
    from patterns import PATTERNS
    for k, (svg, _) in PATTERNS.items():
        name = 'sk_t_' + k
        m = re.search(r"width='([\d.]+)' height='([\d.]+)'", svg)
        w, h = float(m.group(1)), float(m.group(2))
        dsl = svg.replace(f"width='{m.group(1)}' height='{m.group(2)}'", f"width='{w * scale}' height='{h * scale}'", 1)
        items.append((name, dsl, vd2svg.convert(res(name), scale=scale)))
    # launcher: the mockup's launch art vs background + foreground
    import launcher
    from engine import symbol as sym_
    clips_h, sym_h = sym_(launcher.hamper())
    clips_s, sym_s = sym_(launcher.star4holo())
    holo = ('<linearGradient id="sticker-holo" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFD6E8"/><stop offset=".25" stop-color="#CCEBFF"/>'
            '<stop offset=".5" stop-color="#C9F2E3"/><stop offset=".72" stop-color="#FFF3A6"/><stop offset="1" stop-color="#E4D6FF"/></linearGradient>')
    dsl = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{108 * scale // 2}" height="{108 * scale // 2}" viewBox="0 0 108 108"><defs>{holo}{clips_h}{sym_h}{clips_s}{sym_s}</defs>'
           '<rect width="108" height="108" fill="#E6F6EE"/>' + ''.join(f'<rect x="{x}" width="2" height="108" fill="#CDEEDD"/>' for x in range(0, 108, 10)) +
           '<use href="#sticker-i-launch" x="25" y="27" width="56" height="56"/><use href="#sticker-i-star4holo" x="64" y="27" width="19" height="19"/></svg>')
    bg = vd2svg.convert(res('sk_l_launcher_background'), scale=scale / 2)
    fg = vd2svg.convert(res('sk_l_launcher_foreground'), scale=scale / 2)
    vd = bg.replace('</svg>', '') + re.sub(r'^<svg[^>]*>', '', fg)
    items.append(('sk_l_launcher', dsl, vd))
    if only:
        items = [i for i in items if any(o in i[0] for o in only)]
    return items


def main(argv):
    out_dir = argv[argv.index('--out') + 1] if '--out' in argv else tempfile.mkdtemp(prefix='roundtrip-')
    scale = int(argv[argv.index('--scale') + 1]) if '--scale' in argv else 8
    only = [a for a in argv if not a.startswith('--') and a not in (out_dir, str(scale))]
    os.makedirs(out_dir, exist_ok=True)
    items = pairs(scale, only)
    with open(os.path.join(out_dir, 'pairs.json'), 'w') as f:
        json.dump([dict(name=n, dsl=a, vd=b) for n, a, b in items], f)
    subprocess.run(['node', os.path.join(HERE, 'roundtrip.mjs'), out_dir, BG], check=True)
    import numpy as np
    from PIL import Image
    rows = []
    for name, _, _ in items:
        a = np.asarray(Image.open(os.path.join(out_dir, name + '.dsl.png')).convert('RGB')).astype(int)
        b = np.asarray(Image.open(os.path.join(out_dir, name + '.vd.png')).convert('RGB')).astype(int)
        if a.shape != b.shape:
            rows.append((1.0, 255.0, name)); continue
        d = np.abs(a - b).max(axis=2)
        fg = (np.abs(a - np.array([0x7A] * 3)).max(axis=2) > 0) | (np.abs(b - np.array([0x7A] * 3)).max(axis=2) > 0)
        n = max(int(fg.sum()), 1)
        rows.append((float((d > 48).sum()) / n, float(d.mean()), name))
    rows.sort(reverse=True)
    with open(os.path.join(out_dir, 'diff.tsv'), 'w') as f:
        for frac, mean, name in rows:
            f.write(f'{name}\t{frac:.5f}\t{mean:.3f}\n')
    fr = [r[0] for r in rows]
    print(f'{len(rows)} drawables: pixels differing by >48/255 (share of the art) max {max(fr):.3%}, '
          f'median {sorted(fr)[len(fr) // 2]:.3%}; mean |diff| max {max(r[1] for r in rows):.2f}/255')
    for frac, mean, name in rows[:12]:
        print(f'  {name:40s} {frac:.3%}  mean {mean:.2f}')
    # worst-first sheet: DSL | emitted | difference
    worst = rows[:24]
    tiles = []
    for _, _, name in worst:
        a = Image.open(os.path.join(out_dir, name + '.dsl.png')).convert('RGB')
        b = Image.open(os.path.join(out_dir, name + '.vd.png')).convert('RGB')
        b = b.resize(a.size)
        da = np.abs(np.asarray(a).astype(int) - np.asarray(b).astype(int)).max(axis=2)
        dimg = Image.fromarray((255 - np.clip(da * 4, 0, 255)).astype('uint8')).convert('RGB')
        row = Image.new('RGB', (a.width * 3 + 20, a.height), 'white')
        row.paste(a, (0, 0)); row.paste(b, (a.width + 10, 0)); row.paste(dimg, (2 * a.width + 20, 0))
        tiles.append(row.resize((row.width // 2, row.height // 2)))
    if tiles:
        W = max(t.width for t in tiles); H = sum(t.height + 6 for t in tiles)
        sheet = Image.new('RGB', (W, H), 'white'); y = 0
        for t in tiles:
            sheet.paste(t, (0, y)); y += t.height + 6
        sheet.save(os.path.join(out_dir, 'worst.png'))
    print('images and diff.tsv in', out_dir)


if __name__ == '__main__':
    main(sys.argv[1:])
