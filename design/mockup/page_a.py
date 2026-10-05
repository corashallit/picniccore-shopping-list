from common import *

CORE = [
    ('binder', '#E6F6EE', 'App background, mint vinyl'), ('binder-stripe', '#CDEEDD', 'Pinstripes, 2 dp every 10 dp'),
    ('pad', '#FFFFFF', 'Letter-pad pages, cards'), ('rule', '#BCD0FF', '1 dp ruled lines'), ('margin', '#FF9CC2', '2 dp pad margin line'),
    ('sticker-shadow', '#6E7BD0', 'Hard offset shadow (+1.6, +2.2)'), ('ballpoint', '#25307C', 'Primary text · 11.7:1 on pad'),
    ('ballpoint-soft', '#4F5BA6', 'Secondary text · 6.2:1 on pad'), ('hanko', '#C8321F', 'Stamp + errors · 5.3:1'),
    ('lcd', '#C9E5B8', 'LCD toy screen · 8.6:1'), ('highlighter', '#FFF3A6', 'Pressed / selected band'), ('backing', '#EEF0FA', 'Sticker-sheet backing'),
]


def story():
    items = [
        ('envelope', '<b>List</b> = a letter-pad page'), ('blanket', '<b>Aisle</b> = binder page + index tab'),
        ('strawberries', '<b>Item</b> = a puffy die-cut sticker'), (None, '<b>Check</b> = a vermilion hanko'),
        ('star', '<b>Progress</b> = summer stamp card'), ('lcd', '<b>Counts</b> = pastel LCD toy'),
        ('capsule', '<b>AI</b> = a gashapon capsule'), ('camera', '<b>Photo</b> = polaroid under washi'),
        ('cassette', '<b>Voice</b> = pink cassette recorder'), ('book', '<b>Trip</b> = a sticker-book page'),
    ]
    out = []
    for k, t in items:
        if k is None:
            g = hanko(-12, 40)
        elif k == 'lcd':
            g = lcd('12', 'LCD', star=True, p=1.6)
        elif k == 'book':
            g = ('<svg width="40" height="40" viewBox="0 0 40 40" aria-hidden="true"><rect x="3.6" y="9.6" width="34" height="24" rx="4" fill="#6E7BD0"/><rect x="2" y="7.4" width="34" height="24" rx="4" fill="#fff"/>'
                 '<rect x="4.4" y="9.8" width="13" height="19.2" rx="2" fill="#fff" stroke="#9AA8F5" stroke-width="1.2"/><rect x="20.6" y="9.8" width="13" height="19.2" rx="2" fill="#FFF0F7" stroke="#FF9FC8" stroke-width="1.2"/>'
                 '<rect x="17.6" y="9.8" width="3" height="19.2" fill="#DCE1FF"/><circle cx="11" cy="15" r="3" fill="none" stroke="#4F5BA6" stroke-width="1.2" stroke-dasharray="2 1.4"/><circle cx="11" cy="23.4" r="3" fill="#C3264F"/>'
                 '<use href="#sticker-i-strawberries" x="20.4" y="9.6" width="10" height="10"/><use href="#sticker-i-banana" x="23" y="18.6" width="10" height="10"/></svg>')
        else:
            g = ico(k, 40)
        out.append(f'<li>{g}<span>{t}</span></li>')
    return f'<ul class="sk-story">{"".join(out)}</ul>'


def sheet_art():
    s = [
        ('strawberries', 96, 24, 26, -8), ('bee', 70, 140, 20, 10), ('blanket', 104, 222, 34, 6), ('star', 58, 356, 20, -12),
        ('lemonade', 84, 352, 96, 8), ('heart', 54, 30, 140, -14), ('ladybug', 58, 104, 136, 12), ('cloud', 80, 170, 140, 0),
        ('jam', 78, 264, 150, -6), ('star4', 44, 20, 230, 0), ('basketdeco', 104, 66, 214, -4), ('ant', 70, 180, 236, 6),
        ('sun', 72, 266, 236, 0), ('teacup', 70, 354, 214, 10), ('sandwich', 66, 372, 300, -8), ('flower', 50, 300, 318, 0),
        ('cake', 70, 20, 316, 6), ('ribbon', 60, 104, 324, -10),
    ]
    out = []
    for k, w, x, y, r in s:
        tw = ' class="sk-twinkle"' if k in ('star', 'star4') else ''
        out.append(f'<g transform="rotate({r} {x + w / 2} {y + w / 2})"><g{tw} style="transform-origin:{x + w / 2}px {y + w / 2}px"><use href="#sticker-i-{k}" x="{x}" y="{y}" width="{w}" height="{w}"/></g></g>')
    hk = hanko(-14, 64, ('GOT', 'IT!')).replace('<svg ', '<svg x="186" y="318" ', 1)
    return (f'<div class="sk-sheetart" role="img" aria-label="A furoku sticker sheet: strawberries, bee, gingham blanket, lemonade, ladybug, picnic basket and more">'
            f'<svg viewBox="0 0 450 400" preserveAspectRatio="xMidYMid meet" style="position:absolute;left:16px;right:16px;top:14px;width:calc(100% - 32px);height:calc(100% - 64px)" aria-hidden="true">{"".join(out)}{hk}</svg>'
            f'<span class="sk-sa-cap">{heart_svg(14)} furoku · june 1994 · sticker sheet 1 of 2</span></div>')


def palette():
    toks = ''.join(f'<div class="sk-tok"><i style="background:{h}"></i><div><b>{n}</b><code>{h}</code><span>{r}</span></div></div>' for n, h, r in CORE)
    trios = []
    for f in FAMS:
        p, m, t = C[f]
        dp = deep(f)
        trios.append(f'<div class="sk-trio"><div class="sk-tsw"><i style="background:{p}"></i><i style="background:{m}"></i><i style="background:{dp}"></i><i style="background:{t}"></i></div>'
                     f'<b style="color:{t}">{f}</b><code>{p}</code><code>{m}</code><code>{dp}</code><code>{t}</code></div>')
    dark = [('#161A3E', 'background'), ('#1E2350', 'stripes'), ('#222861', 'page'), ('#3A438A', 'rules'), ('#FF7FB0', 'margin'),
            ('#FFFFFF', 'text 13.6:1'), ('#C9CFF5', 'secondary 8.9'), ('#C9FFDE', 'glow-star'), ('#FF8A7A', 'hanko 5.9'), ('#0B0D24', 'shadow')]
    dk = ''.join(f'<span><i style="background:{h}"></i>{n}</span>' for h, n in dark)
    return (f'<div class="sk-pal"><div><h3>Mint Binder · light</h3><div class="sk-tokens">{toks}</div>'
            f'<h3 style="margin-top:22px">Glow-in-the-dark · dark</h3><div class="sk-darkpal">{dk}</div>'
            f'<p class="sk-small" style="margin-top:8px">Stickers keep their colours in the dark; their white borders read at 16.8:1 on the navy binder.</p></div>'
            f'<div><h3>Sticker colours · pastel / mid / deep / trace</h3><div class="sk-trios">{"".join(trios)}</div>'
            f'<p class="sk-small" style="margin-top:12px">Pastel = body fill, mid = accent + puffy band, <b>deep</b> = the puffy band on mid-bodied stickers (mid mixed 32 % toward trace; Android <code>sk_&lt;name&gt;_deep</code>), trace = outline. The icon validator rejects any fill outside these 40 tokens + white. Trace text only at ≥20 sp bold; small text on pastels is always ballpoint.</p>'
            f'<h3 style="margin-top:18px">Holo prism foil · reward moments only</h3><div class="sk-holo-sw" role="img" aria-label="Holo prism foil gradient"></div>'
            f'<p class="sk-small" style="margin-top:6px"><code>conic-gradient(from 210deg, #FFD6E8, #CCEBFF, #C9F2E3, #FFF3A6, #E4D6FF)</code> + a 30 dp glint band at 35 %.</p></div></div>')


def typespec():
    specs = [
        ('DynaPuff 600–700', 'wordmark · titles · tabs · buttons', '<span class="sk-puff" style="font-size:34px;color:var(--sk-strawberry-m);--sw:7px">Saturday shop</span>'),
        ('Zen Maru Gothic 500 / 700 / 900', 'item names · body · UI', '<span style="font:700 18px/1.3 var(--sk-f-body)">Strawberries</span><br><span style="font:500 14px/1.3 var(--sk-f-body);color:var(--sk-bp2)">1 L basket · produce · 540 mL tin · 454 g brick · 4 L bag</span>'),
        ('Klee One 600', 'raw transcription · notes', '<span style="font:600 17px/1.4 var(--sk-f-pencil);color:var(--sk-bp2)">“eggs ×12, butter 454 g” ← from your photo</span>'),
        ('DotGothic16 400', 'LCD counters · timers', f'<span style="display:inline-flex;gap:10px;align-items:center"><span class="sk-lcd" style="font:400 18px/1 var(--sk-f-lcd);padding:6px 10px;line-height:1">12/18</span>{lcd("12/18", "12 of 18", star=True)}<span class="sk-small">font · and its 5 × 7 vector twin with a drawn star</span></span>'),
        ('Hachi Maru Pop 400', 'maru-moji doodles · decorative only', f'<span class="sk-maru" style="font-size:17px;display:inline-flex;gap:6px;align-items:center">{heart_svg(16)} let’s go shopping {heart_svg(16)}</span>'),
        ('Nanum Pen Script', 'mockup photo prop only · not bundled', '<span style="font:400 24px/1.1 var(--sk-f-hand);color:#2B3A8C">milk (bag!) · eggs x12 · <s>chips</s></span>'),
    ]
    out = ''.join(f'<div class="sk-spec"><div class="sk-sm"><b>{a}</b><span>{b}</span></div><div>{c}</div></div>' for a, b, c in specs)
    rules = ''.join(f'<span class="sk-chip" style="--cp:var(--sk-{f}-p)">{t}</span>' for f, t in [
        ('lemon', 'Max 3 families per screen'), ('ramune', 'NUMCIRCLE'), ('sakura', 'Item names: body face ≥ 16 sp'),
        ('melon', 'Subset CJK fonts to Latin + used kana'), ('grape', 'OFL licences shown in-app'), ('peach', 'Stars + hearts drawn as vectors')])
    rules = rules.replace('NUMCIRCLE', f'Numbers in drawn circles {num_circle(4, 20)} never {num_circle(4, 20, strike=True, col="#4F5BA6")} glyphs')
    return f'<div class="sk-type"><h3>Type specimen</h3><div class="sk-specs">{out}</div><div class="sk-rules">{rules}</div></div>'


def section_a():
    left = (f'<div class="sk-padcard sk-hero-l">{tape("right:40px;top:-9px;transform:rotate(4deg)")}'
            f'<span class="sk-dirtag"><span class="sk-dot">C</span>Direction C · sticker</span>'
            f'<h1 class="sk-dirname sk-puff">Furoku Sticker Album</h1>'
            f'<div class="sk-wordmark"><span class="sk-wm" role="img" aria-label="Kirakira Basket">Kirakira <span class="sk-wm2">Basket</span></span>'
            f'<span class="sk-wmstars" aria-hidden="true">{deco("star4", 40, "position:relative;display:block", "sk-twinkle")}{deco("star", 30, "position:absolute;right:-26px;top:-6px", "sk-twinkle sk-d2")}</span></div>'
            f'<p class="sk-lead">Your grocery list as the free gift tucked into a June 1994 girls’ manga magazine: a picnic letter set and a sheet of puffy stickers, '
            f'clipped into a mint pinstripe binder. <span class="sk-hl">Every item is a die-cut sticker, every aisle a binder page, every check a vermilion hanko.</span></p>'
            f'{story()}'
            f'<div class="sk-mood" style="margin-top:18px">' + ''.join(f'<span class="sk-chip" style="--cp:var(--sk-{f}-p)">{t}</span>' for f, t in [
                ('strawberry', 'playful'), ('ramune', 'tactile'), ('lemon', 'collectible'), ('melon', 'Heisei-retro'), ('grape', 'colour-trace lines'), ('sakura', 'no dark ink'), ('peach', 'hard shadows only')]) +
            '</div></div>')
    right = (f'<div class="sk-hero-r">{sheet_art()}'
             f'<div class="sk-note" style="font-size:14.5px"><b>Line system:</b> no universal dark outline. Each shape is traced in its own deep colour, wrapped in a fat white die-cut border, '
             f'and lifted by a hard periwinkle shadow. Volume = a flat puffy band along the bottom; shine = one white candy-gloss stroke + dot, generated from each sticker’s own outline.</div></div>')
    head = ('<div class="sk-bhead" style="margin-top:64px"><span class="sk-btab" style="--tp:var(--sk-strawberry-p);--tm:var(--sk-strawberry-m);--tt:var(--sk-strawberry-t);--pat:var(--sk-pat-gingham);--pats:10px"><span class="sk-bnum">A</span><h2>Palette &amp; type</h2></span>'
            '<p class="sk-bsub">Milky pastels on a mint binder, ballpoint-blue text, one vermilion stamp colour; three type families per screen at most.</p></div>')
    return f'<div class="sk-hero">{left}{right}</div><div class="sk-block" id="sticker-palette" style="margin-top:0">{head}{palette()}{typespec()}</div>'
