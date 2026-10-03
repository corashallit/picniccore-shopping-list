#!/usr/bin/env python3
"""Contact sheets for sticker review (docs/DESIGN_STICKER.md §18.3 step 4, §20 item 34): the DSL's own SVG symbols at
52 / 30 / 22 dp on the Day, Glow and Dusk binders, rendered by Chromium at xxhdpi (3 px per dp), plus a large
detail sheet on Day.

    python3 design/stickers/tools/contact.py [--out DIR] [--scale 3] [--cols 4] [--ref key,key,...] [--name NAME] keys...

`keys` are DSL keys (a new sticker's DSL key is its catalog key); `--ref` adds a row of approved neighbours so a new
sticker can be judged against them (consistency, confusable pairs). Writes <name>_day.png, <name>_glow.png,
<name>_dusk.png and <name>_detail.png. Needs Node with Playwright (PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs
for a global install).
"""
import html
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from engine import symbol  # noqa: E402
from icons import IBY  # noqa: E402

# binder ground and sticker shadow per variant (§2.1, §2.4, §2.5)
BINDERS = {
    'day': dict(bg='#E6F6EE', stripe='#CDEEDD', shadow='#6E7BD0', ink='#25307C'),
    'glow': dict(bg='#161A3E', stripe='#1E2350', shadow='#0B0D24', ink='#FFFFFF'),
    'dusk': dict(bg='#E9DDF7', stripe='rgba(255,255,255,.4)', shadow='#6E7BD0', ink='#25307C'),
}
SIZES = (52, 30, 22)


def defs(keys):
    clips, syms = [], []
    for k in keys:
        c, s = symbol(IBY[k])
        clips.append(c)
        syms.append(s)
    return f'<svg width="0" height="0" style="position:absolute"><defs>{"".join(clips)}{"".join(syms)}</defs></svg>'


def cell(k, sizes):
    svgs = ''.join(f'<svg width="{s}" height="{s}" style="margin-right:8px"><use href="#sticker-i-{k}"/></svg>' for s in sizes)
    return (f'<div class="c"><div class="row">{svgs}</div>'
            f'<div class="lab">{html.escape(k)}</div></div>')


def page(keys, refs, binder, cols, sizes=SIZES, title=''):
    b = BINDERS[binder]
    cw = sum(sizes) + 8 * len(sizes) + 12
    grid = lambda ks: f'<div class="g">{"".join(cell(k, sizes) for k in ks)}</div>'
    body = f'<div class="t">{html.escape(title)} · {binder}</div>' + grid(keys)
    if refs:
        body += f'<div class="t">approved neighbours</div>' + grid(refs)
    return f'''<!doctype html><html><head><meta charset="utf-8"><style>
body{{margin:0;padding:12px 12px 16px;background-color:{b["bg"]};
 background-image:repeating-linear-gradient(90deg,{b["stripe"]} 0 2px,transparent 2px 10px);
 --sk-shadow:{b["shadow"]};font:600 9px/1.2 sans-serif;color:{b["ink"]};width:{cols * cw}px}}
.g{{display:grid;grid-template-columns:repeat({cols},{cw}px);row-gap:6px}}
.c{{padding:4px 0}} .row{{display:flex;align-items:flex-end}} .lab{{margin-top:2px}}
.t{{font-size:11px;margin:4px 0 6px}}
</style></head><body>{defs(list(dict.fromkeys(keys + refs)))}{body}</body></html>'''


def render(pages, out_dir, scale):
    job = os.path.join(out_dir, 'contact_jobs.txt')
    with open(job, 'w') as f:
        for name, htm in pages:
            p = os.path.join(out_dir, name + '.html')
            with open(p, 'w') as g:
                g.write(htm)
            f.write(f'{p}\t{os.path.join(out_dir, name + ".png")}\t{scale}\n')
    subprocess.run(['node', os.path.join(HERE, 'contact.mjs'), job], check=True)


def main(argv):
    def opt(name, default):
        if name in argv:
            i = argv.index(name)
            v = argv[i + 1]
            del argv[i:i + 2]
            return v
        return default
    out_dir = opt('--out', None) or tempfile.mkdtemp(prefix='contact-')
    scale = float(opt('--scale', '3'))
    cols = int(opt('--cols', '4'))
    refs = [r for r in opt('--ref', '').split(',') if r]
    name = opt('--name', 'contact')
    keys = argv
    missing = [k for k in keys + refs if k not in IBY]
    if missing:
        raise SystemExit('no DSL sticker for ' + ', '.join(missing))
    os.makedirs(out_dir, exist_ok=True)
    pages = [(f'{name}_{b}', page(keys, refs, b, cols, title=name)) for b in BINDERS]
    # detail: 120 dp (card / Edit hero size) on Day, two rows of sizes per sticker
    pages.append((f'{name}_detail', page(keys, refs, 'day', max(2, cols - 1), sizes=(120, 52), title=name + ' detail')))
    render(pages, out_dir, scale)
    for n, _ in pages:
        print(os.path.join(out_dir, n + '.png'))


if __name__ == '__main__':
    main(sys.argv[1:])
