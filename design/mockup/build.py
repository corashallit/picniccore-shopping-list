"""Builds the direction C mockup page (the friend's reference, docs/DESIGN_STICKER.md §18.4).

    python3 design/mockup/build.py [--out PATH]

The sticker DSL (engine, gloss, icons, baskets, deco) is imported from design/stickers/, the single source of the art
that the Android emitter also reads. All paths are relative to this file; the page lands in design/mockup/build/
(git-ignored) unless --out says otherwise."""
import re, sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'stickers'))
sys.path.insert(0, HERE)
os.chdir(HERE)
from common import *
from page_a import section_a
from page_b import section_b
from page_c import section_c, section_c2, extra_symbols, mono_symbols, ai_symbols
from page_d import section_d, launcher_symbols
from page_f import section_f, MOTION_CSS, SCRIPT

OUT = os.path.join('build', 'sticker.section.html')
if '--out' in sys.argv:
    OUT = os.path.abspath(os.path.join(os.environ.get('PWD', os.getcwd()), sys.argv[sys.argv.index('--out') + 1]))
FONTS = ('https://fonts.googleapis.com/css2?family=DynaPuff:wght@600;700&family=Zen+Maru+Gothic:wght@500;700;900'
         '&family=Klee+One:wght@600&family=DotGothic16&family=Hachi+Maru+Pop&family=Nanum+Pen+Script&display=swap')


def css():
    s = open('style.css').read() + '\n' + MOTION_CSS + '\n' + open('style_r3.css').read() + '\n' + open('style_r4.css').read() + '\n' + open('style_r5.css').read() + '\n' + open('style_r6.css').read() + '\n' + open('style_r9.css').read()
    pv = ''.join(f'--sk-pat-{k}:{uri(v[0])};' for k, v in PATTERNS.items())
    pv += f'--sk-zig:{uri(ZIG)};--sk-wavy:{uri(WAVY)};'
    from page_f import HEART_MASK
    pv += f'--sk-heart:{HEART_MASK};'
    pv += ''.join(f'--sk-tape-{k}:{uri(v[1])};' for k, v in TAPES.items())
    s = s.replace('§{--sk-binder', '§{' + pv + '--sk-binder', 1)
    s = s.replace('§', '.mk-sticker')
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    from page_f import hop_css
    s += '\n' + hop_css()
    s = re.sub(r'\n\s*\n', '\n', s)
    return s


def defs(mono_keys=()):
    clips, syms = [], [mono_symbols(mono_keys)]
    for i in ICONS + DECO:
        c, s = symbol(i); clips.append(c); syms.append(s)
    for b in BASKETS:
        c, s = b.defs(); clips.append(c); syms.append(s)
    c, s = extra_symbols(); clips.append(c); syms.append(s)
    c, s = launcher_symbols(); clips.append(c); syms.append(s)
    syms.append(picto_symbols()); syms.append(tb_symbols())
    from deco import hanko_symbols, lcd_patterns
    syms.append(hanko_symbols()); clips.append(lcd_patterns())
    from baskets import TAG_SYMBOL
    syms.append(TAG_SYMBOL)
    syms.append(ai_symbols())
    from common import glint_masks
    clips.append(glint_masks())
    # r9: egg 10 bonus stage pixelates every sticker (4 px blocks); mockup-only (the app swaps in 16 x 16 sprites)
    clips.append('<filter id="sticker-pixf" x="0" y="0" width="1" height="1" color-interpolation-filters="sRGB"><feFlood x="1.5" y="1.5" height="1" width="1"/>'
                 '<feComposite width="4" height="4"/><feTile result="a"/><feComposite in="SourceGraphic" in2="a" operator="in"/><feMorphology operator="dilate" radius="2"/></filter>')
    return f'<svg class="sk-defs" width="0" height="0" aria-hidden="true" focusable="false"><defs>{"".join(clips)}{"".join(syms)}</defs></svg>'


HEADS = {'cat': (15.8, 31.4, 9.6), 'bee': (17.5, 28.4, 7), 'ladybug': (24, 13.4, 6.4), 'ladybugfly': (24, 15.4, 6), 'ant': (12.4, 32, 3.6)}


def lint_heads():
    import math
    from deco import DBY
    from engine import sample_pts, NUM
    errs = []
    for k, (cx, cy, r) in HEADS.items():
        for g in DBY[k].gloss:
            pts = sample_pts(('path', g[1])) if g[0] == 'g' else [(g[1], g[2])]
            for x, y in pts:
                if math.hypot(x - cx, y - cy) < r:
                    errs.append(f'lint: white gloss inside the head of {k} at {x},{y}')
    return errs


def validate(html, cssText):
    errs = lint_heads()
    from icons import GLOSS_REPORT
    from gloss import lint as lint_gloss
    from lintcol import lint_colours
    errs += lint_gloss(GLOSS_REPORT) + lint_colours(ICONS)
    mains = [r['main'] for r in GLOSS_REPORT if r['main']]
    print(f'gloss: {len(mains)} icons, main gloss {min(m["w"] for m in mains)}u wide, length {min(m["len"] for m in mains)}-{max(m["len"] for m in mains)}u, '
          f'{sum(len(r["subs"]) for r in GLOSS_REPORT)} sub-glosses')
    # every selector begins with .mk-sticker
    body = re.sub(r'@keyframes[^{]+\{(?:[^{}]*\{[^}]*\})*[^}]*\}', '', cssText)
    for m in re.finditer(r'([^{}@]+)\{', body):
        sel = m.group(1).strip()
        if not sel or sel.startswith('@') or sel.startswith('media') or sel.startswith('supports') or sel.startswith('from') or sel.startswith('to') or re.match(r'^[\d%,\s]+$', sel):
            continue
        for part in sel.split(','):
            p = part.strip()
            if p and not p.startswith('.mk-sticker'):
                errs.append('selector: ' + p[:80])
    for k in re.findall(r'@keyframes\s+([\w-]+)', cssText):
        if not k.startswith('sticker-'):
            errs.append('keyframes: ' + k)
    for i in re.findall(r'\bid="([^"]+)"', html):
        if not i.startswith('sticker'):
            errs.append('id: ' + i)
    ids = re.findall(r'\bid="([^"]+)"', html)
    dup = {x for x in ids if ids.count(x) > 1}
    if dup:
        errs.append('dup ids: ' + ', '.join(sorted(dup))[:300])
    refs = set(re.findall(r'href="#([^"]+)"', html)) | set(re.findall(r'url\(#([^)]+)\)', html))
    missing = {r for r in refs - set(ids) if "'" not in r}
    if missing:
        errs.append('missing refs: ' + ', '.join(sorted(missing)))
    return errs


def build():
    sections = section_a() + section_b() + section_c() + section_c2() + section_d() + section_f()
    c = css()
    foot = (f'<div class="sk-foot"><span style="display:inline-flex;gap:8px;align-items:center">{ico("strawberries", 34)}<b style="font:700 18px/1 var(--sk-f-puff)">Kirakira Basket</b> · direction C · Furoku Sticker Album</span>'
            f'<span>Fonts: DynaPuff, Zen Maru Gothic, Klee One, DotGothic16, Hachi Maru Pop (all OFL, bundled + subset) · Nanum Pen Script (mockup photo prop only, not bundled) · all art is inline SVG/CSS except the six live-icon stickers rendered with gpt-image-2.5-flare.</span></div>')
    mono = set(re.findall(r'#sticker-m-([\w]+)"', sections + foot + SCRIPT))
    html = (f'<!-- Google Fonts css2: {FONTS} -->\n'
            f'<link rel="stylesheet" href="{FONTS}">\n'
            f'<style>\n{c}\n</style>\n'
            f'<section class="mk-sticker" id="sticker" aria-label="Direction C: Furoku Sticker Album">{defs(mono)}'
            f'<div class="sk-wrap">{sections}{foot}</div>{SCRIPT}</section>\n')
    errs = validate(html, c)
    print('mono symbols:', len(mono))
    os.makedirs(os.path.dirname(OUT) or '.', exist_ok=True)
    open(OUT, 'w', encoding='utf-8').write(html)
    print('wrote', OUT, len(html.encode()) // 1024, 'KB')
    for e in errs[:40]:
        print('  !', e)
    print(len(errs), 'issues')


if __name__ == '__main__':
    build()
