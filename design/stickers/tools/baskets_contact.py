#!/usr/bin/env python3
"""Basket contact sheet (docs/DESIGN_STICKER.md §5, §20.6 item 101, `baskets_contact`): every one of the 17 kinds in
every state, composed from the EMITTED layers (res/drawable/sk_b_*.xml read back through tools/vd2svg.py), so the
sheet shows exactly what the app gets, at the three sizes (80 x 64 strip, 64 x 51 tray header, 120 x 96 card) on the
Day and Glow binders. Rendered by Chromium.

    python3 design/stickers/tools/baskets_contact.py [--out DIR] [--scale 2] [--from-tree] [--states a,b] [--quantize] [kinds...]

States (columns), drawn the way BasketManifest documents the layer order:
  empty    lid_open (only kinds whose lid stands open behind), back (decoration included), landing spot, front
  one      one gotten mini
  three    three minis, fanned
  seven    the newest five + the "+2" tag
  done     back, front, lid_closed, the GOT IT hanko on the lid (minis hidden under it)
  peek     done, held: lid_open instead of lid_closed, the minis raised 8
  lid_open the open lid alone behind the empty basket (no minis, no spot), to judge its pose
  deco     the `deco` layer alone (decorated kinds only)

Writes baskets_contact_day.png and baskets_contact_glow.png (and the HTML) to DIR (default: a temp dir).
Needs Node with Playwright (PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs for a global install).
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
from baskets import FILL, SI, LIFT, MAXIN  # noqa: E402

GROUNDS = {
    'day': dict(binder='#E6F6EE', stripe='#CDEEDD', pad='#FFFFFF', ink='#25307C', shadow='#6E7BD0', spot_fill='#FFFFFF',
                spot_line='#3E4BB0', hanko='#C8321F'),
    'glow': dict(binder='#161A3E', stripe='#1E2350', pad='#222861', ink='#FFFFFF', shadow='#0B0D24', spot_fill='none',
                 spot_line='#C9CFF5', hanko='#FF8A7A'),
}
MINIS = ['strawberry', 'milk_bag', 'apple', 'carrot', 'sliced_bread', 'eggs', 'maple_syrup']
SIZES = [(80, 64), (64, 51), (120, 96)]
STATES = ['empty', 'one', 'three', 'seven', 'done', 'peek', 'lid_open', 'deco']

ONLY = []
_uid = [0]


def unique_ids(svg):
    """vd2svg numbers its ids per call; prefix them so many drawables can share one HTML document."""
    _uid[0] += 1
    p = f'u{_uid[0]}_'
    svg = re.sub(r'id="([^"]+)"', lambda m: f'id="{p}{m.group(1)}"', svg)
    return re.sub(r'url\(#([^)]+)\)', lambda m: f'url(#{p}{m.group(1)})', svg)


def inner(svg):
    """the drawable's body without its outer <svg> (its viewBox is 0 0 80 64 = ours)"""
    return re.sub(r'^<svg[^>]*>|</svg>$', '', svg)


def hanko(cx, cy, colour, size=34, rot=-12):
    s = size / 40
    return (f'<g transform="translate({cx} {cy}) rotate({rot}) scale({s}) translate(-20 -20)" fill="{colour}" stroke="{colour}">'
            '<circle cx="20" cy="20" r="16.4" fill="none" stroke-width="2" stroke-dasharray="40 3.2 51.8 3.2"/>'
            '<circle cx="20" cy="20" r="13.6" fill="none" stroke-width=".8" stroke-dasharray="20 2 30 1.6 31.4"/>'
            '<text x="20" y="18.6" text-anchor="middle" stroke="none" style="font:900 10.4px sans-serif">GOT</text>'
            '<text x="20" y="28.4" text-anchor="middle" stroke="none" style="font:900 10.4px sans-serif">IT!</text></g>')


def compose(art, state, layers, minis_svg, g):
    """one basket in one state, in viewport units (80 x 64)"""
    L = lambda k: inner(unique_ids(layers[k])) if k in layers else ''
    S = lambda k: inner(unique_ids(layers[k + '#shadow'])) if k + '#shadow' in layers else L(k)
    rim = art['rim'] + 9
    out = []
    if state == 'deco':
        return L('deco') if 'deco' in layers else ''
    if state == 'lid_open':
        return (S('lid_open') if 'lid_open' in layers else '') + S('back') + L('front')
    got = {'empty': 0, 'one': 1, 'three': 3, 'seven': 7, 'done': 7, 'peek': 7}[state]
    done = state in ('done', 'peek')
    if 'lid_open' in layers and ((art['show_open'] and not done) or state == 'peek'):
        out.append(S('lid_open'))
    out.append(S('back'))
    if got == 0:
        out.append(f'<circle cx="40" cy="{rim - 1.5}" r="7" fill="{g["spot_fill"]}" stroke="{g["spot_line"]}" '
                   'stroke-width="1.8" stroke-dasharray="3.2 2.4"/>')
    elif state != 'done':
        shown = MINIS[:got][-MAXIN:]
        slots = FILL[len(shown)]
        lift = -8 if state == 'peek' else 0
        order = sorted(zip(shown, slots), key=lambda s: s[1][1] >= 0)
        for key, (cx, dy, rot) in order:
            x, y = cx + 8 - SI / 2, rim - SI * LIFT + dy + lift
            m = inner(unique_ids(minis_svg[key]))
            out.append(f'<g transform="rotate({rot} {x + SI / 2} {y + SI})"><svg x="{x}" y="{y}" width="{SI}" height="{SI}" '
                       f'viewBox="0 0 52 52" overflow="visible">{m}</svg></g>')
    out.append(L('front'))
    if got > MAXIN and state != 'done':
        out.append(f'<g transform="translate(57 39)"><rect x="1.6" y="2.2" width="18" height="12" rx="6" fill="{g["shadow"]}"/>'
                   '<rect width="18" height="12" rx="6" fill="#fff"/><rect x="1.4" y="1.4" width="15.2" height="9.2" rx="4.6" '
                   'fill="none" stroke="#9AA8F5"/><text x="9" y="9" text-anchor="middle" '
                   f'style="font:700 8.6px sans-serif;fill:#25307C">+{got - MAXIN}</text></g>')
    if state == 'done':
        out.append(S('lid_closed'))
        out.append(hanko(art['hk'][0] + 8, art['hk'][1] + 9, g['hanko']))
    return ''.join(out)


def sheet(variant, arts, layer_svgs, minis_svg, scale):
    g = GROUNDS[variant]
    stripe = (f"background-color:{g['binder']};background-image:repeating-linear-gradient(90deg,{g['stripe']} 0 2px,"
              f"transparent 2px 10px)")
    rows = []
    head = ''.join(f'<th>{s}</th>' for s in STATES)
    for art in arts:
        if ONLY and not any(o in art['kind'] for o in ONLY):
            continue
        cells = []
        for state in STATES:
            stack = []
            for w, h in SIZES:
                body = compose(art, state, layer_svgs[art['kind']][variant], minis_svg[variant], g)
                stack.append(f'<svg width="{w * scale}" height="{h * scale}" viewBox="0 0 80 64" overflow="visible">{body}</svg>')
            cells.append('<td><div class="c">' + ''.join(stack) + '</div></td>')
        lo = 'lid stands open' if art['show_open'] else ''
        rows.append(f'<tr><th class="k">{art["kind"]}<small>{art["label"]} · {art["aisle"]}{" · " + lo if lo else ""}'
                    f'{" · decorated" if "deco" in art["res"] else ""}</small></th>{"".join(cells)}</tr>')
    return (f'<html><head><meta charset="utf-8"><style>body{{margin:0;{stripe};font:12px sans-serif;color:{g["ink"]}}}'
            f'table{{border-collapse:separate;border-spacing:{6 * scale}px {4 * scale}px}}th{{font-weight:700;text-align:left}}'
            f'th.k{{width:{110 * scale}px;font-size:{7 * scale}px}}th.k small{{display:block;font-weight:400;font-size:{5 * scale}px}}'
            f'th{{font-size:{6 * scale}px}}.c{{display:flex;align-items:flex-end;gap:{4 * scale}px}}</style></head>'
            f'<body><table><tr><th>baskets_contact · {variant}</th>{head}</tr>{"".join(rows)}</table></body></html>')


def quantize(paths):
    """256-colour PNGs (no dither): the committed review sheets stay small"""
    from PIL import Image
    for path in paths:
        Image.open(path).convert('RGB').quantize(colors=256, method=Image.Quantize.MEDIANCUT,
                                                 dither=Image.Dither.NONE).save(path, optimize=True)


def main(argv):
    out_dir = argv[argv.index('--out') + 1] if '--out' in argv else tempfile.mkdtemp(prefix='baskets-')
    scale = float(argv[argv.index('--scale') + 1]) if '--scale' in argv else 2
    os.makedirs(out_dir, exist_ok=True)
    work = tempfile.mkdtemp(prefix='sheets-')     # the HTML and pages.json; only the PNGs go to --out
    if '--states' in argv:
        STATES[:] = argv[argv.index('--states') + 1].split(',')
    opts = {argv[i + 1] for i, a in enumerate(argv) if a in ('--out', '--scale', '--states') and i + 1 < len(argv)}
    ONLY[:] = [a for a in argv if not a.startswith('--') and a not in opts]
    report = {}
    if '--from-tree' in argv:
        read = lambda name: open(os.path.join(B.ROOT, B.DRAWABLE, name + '.xml'), encoding='utf-8').read()
        B.build(report)
    else:
        out = B.build(report)
        read = lambda name: out[os.path.join(B.DRAWABLE, name + '.xml')]
    arts = []
    for b, kind, layers, _ in report['baskets']:
        arts.append(dict(kind=kind, label=b.label, aisle=b.aisle, rim=b.rim, hk=b.hk, show_open=b.show_open,
                         res={k: f'sk_b_{kind}_{k}' for k in layers}))
    layer_svgs = {}
    for a in arts:
        layer_svgs[a['kind']] = {}
        for variant, g in GROUNDS.items():
            d = {}
            for layer, name in a['res'].items():
                xml = read(name)
                d[layer] = vd2svg.convert(xml)
                if layer in ('back', 'lid_open', 'lid_closed'):
                    d[layer + '#shadow'] = vd2svg.convert(xml, shadow=g['shadow'])
            layer_svgs[a['kind']][variant] = d
    minis = {v: {k: vd2svg.convert(read('sk_i_' + k), shadow=g['shadow']) for k in MINIS} for v, g in GROUNDS.items()}
    pages = []
    for variant in GROUNDS:
        html = sheet(variant, arts, layer_svgs, minis, scale)
        path = os.path.join(work, f'baskets_contact_{variant}.html')
        with open(path, 'w', encoding='utf-8') as f:
            f.write(html)
        pages.append(dict(html=path, png=os.path.join(out_dir, f'baskets_contact_{variant}.png')))
    with open(os.path.join(work, 'pages.json'), 'w') as f:
        json.dump(pages, f)
    subprocess.run(['node', os.path.join(HERE, 'shoot_pages.mjs'), os.path.join(work, 'pages.json')], check=True)
    if '--quantize' in argv:
        quantize([p['png'] for p in pages])
    for p in pages:
        print(p['png'])


if __name__ == '__main__':
    main(sys.argv[1:])
