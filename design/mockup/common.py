import urllib.parse, random
from engine import *
from icons import ICONS, IBY
from baskets import BASKETS, BBY, basket_svg, mini
from deco import DECO, DBY, hanko, lcd_svg, tb, picto_symbols, tb_symbols, PICTO

random.seed(7)


def uri(svg):
    return 'url("data:image/svg+xml,' + urllib.parse.quote(svg, safe=" =:/,;'()-.") + '")'


# sv() and the tab PATTERNS live with the DSL (design/stickers/patterns.py): the Android emitter prints them too
from patterns import sv, PATTERNS

ZIG = sv("<path d='M0 0L3 3L0 6L3 9L0 12L3 15L0 18L3 21L0 24H100L97 21L100 18L97 15L100 12L97 9L100 6L97 3L100 0Z' fill='#000'/>", 100, 24)
ZIG = ZIG.replace("width='100' height='24'", "width='100' height='24' preserveAspectRatio='none'")
WAVY = sv("<path d='M0 3Q3 0 6 3T12 3' fill='none' stroke='#FF9FC8' stroke-width='2'/>", 12, 5)

FAMS = ['strawberry', 'ramune', 'lemon', 'melon', 'grape', 'peach', 'mint', 'sakura', 'cocoa', 'periwinkle']
PATS = ['gingham', 'polka', 'stripes', 'hearts', 'stars', 'clouds', 'check', 'waves', 'flowers', 'plain']
PICS = ['strawberry', 'bee', 'star', 'heart', 'cloud', 'flower', 'ladybug', 'teacup', 'sandwich', 'sun']


def avars(fam, pat=None):
    s = f'--ap:var(--sk-{fam}-p);--am:var(--sk-{fam}-m);--at:var(--sk-{fam}-t);'
    if pat:
        s += f'--pat:var(--sk-pat-{pat});--pats:{PATTERNS[pat][1]}px;'
    return s


def ico(key, size=52, label=None, cls='', extra=''):
    lab = f' role="img" aria-label="{label}"' if label else ' aria-hidden="true"'
    c = f' class="{cls}"' if cls else ''
    return f'<svg{c} width="{size}" height="{size}"{lab}><use href="#sticker-i-{key}"/>{extra}</svg>'


# r6: sticker glint clipped to the die-cut. The band lives in the light DOM (so .is-glint / .is-shiny on the row can drive it)
# and is masked by the icon's own white cut silhouette (fill + 8 px stroke, the same shape the holo pull uses), so it can never
# paint outside the sticker edge (the old 50 % circle drew a grey disc on the navy panel).
GLINT_KEYS = set()


def glint_svg(key):
    GLINT_KEYS.add(key)
    return (f'<svg class="sk-gl" viewBox="-2 -2 52 52" aria-hidden="true" focusable="false"><g mask="url(#sticker-gm{code(key)})">'
            f'<g transform="rotate(25 24 24)"><rect class="sk-glb" x="-28" y="-32" width="10" height="112"/></g></g></svg>')


def glint_masks():
    return ''.join(f'<mask id="sticker-gm{code(k)}" maskUnits="userSpaceOnUse" x="-8" y="-8" width="64" height="64"><g {RJA}>'
                   f'<use href="#{kid(k)}" style="--cw:8px" fill="#fff" stroke="#fff" stroke-width="8"/></g></mask>' for k in sorted(GLINT_KEYS))


def ghost(key, size=52):
    k = kid(key)
    return (f'<svg class="sk-ghost" viewBox="-2 -2 52 52" width="{size}" height="{size}" aria-hidden="true"><use href="#{k}" class="sk-gd"/><use href="#{k}" class="sk-gc"/>'
            f'<use href="#sticker-m-{key}" x="-2" y="-2" width="52" height="52" opacity=".18"/></svg>')


def spot(size=30):
    return f'<svg class="sk-spot" viewBox="0 0 30 30" width="{size}" height="{size}" aria-hidden="true"><use href="#sticker-spotg"/></svg>'


def check_svg(size=14, cls='sk-ck'):
    return f'<svg class="{cls}" width="{size}" height="{size}" viewBox="0 0 24 24" aria-hidden="true"><use href="#sticker-tb-check" style="stroke-width:3.4"/></svg>'


def num_circle(n, size=22, strike=False, col='#3E4BB0'):
    s = f'<line x1="3" y1="21" x2="21" y2="3" stroke="#C8321F" stroke-width="2.2" stroke-linecap="round"/>' if strike else ''
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="#fff" stroke="{col}" stroke-width="2"/>'
            f'<text x="12" y="16.4" text-anchor="middle" style="font:700 12.5px var(--sk-f-puff);fill:{col}">{n}</text>{s}</svg>')


# seasonal washi tapes: motif tiles as data-URI masks/backgrounds (inline, no external images)
def _tile(inner, w=24, h=20):
    return sv(inner, w, h)
TAPES = {
    'pumpkin': ('#FFDCC6', _tile("<rect width='24' height='20' fill='#FFDCC6'/><path d='M0 0H24' stroke='#FFA477' stroke-width='3'/><ellipse cx='8' cy='11.6' rx='4.6' ry='3.8' fill='#FFA477' stroke='#AE4D1C' stroke-width='1'/><path d='M8 8V6.4' stroke='#2E8239' stroke-width='1.4' stroke-linecap='round'/><path d='M18.6 4.6l1.2 2.6l2.6-.6l-1 2.4l1.6 1.4l-2.6.4l-.4 2.4l-1.4-2.2l-1.4 2.2l-.4-2.4l-2.6-.4l1.6-1.4l-1-2.4l2.6.6z' fill='#FF6F8E' fill-opacity='.8'/></svg>".replace('</svg>', ''))),
    'maple': ('#FFE3E8', _tile("<rect width='24' height='20' fill='#FFF'/><rect x='0' width='5' height='20' fill='#FF6F8E' fill-opacity='.85'/><rect x='19' width='5' height='20' fill='#FF6F8E' fill-opacity='.85'/><path d='M12 4.4l1.2 2.6l2.4-1l-.6 3.4l2.2-1.2l.4 1.6l1.6.2l-1 2.2l.8.8l-3.6 2.4l.4 1.2l-3.2-.6v3h-1.2v-3l-3.2.6l.4-1.2l-3.6-2.4l.8-.8l-1-2.2l1.6-.2l.4-1.6l2.2 1.2l-.6-3.4l2.4 1z' fill='#FF6F8E'/>")),
    'snow': ('#CCEBFF', _tile("<rect width='24' height='20' fill='#CCEBFF'/><g stroke='#fff' stroke-width='1.6' stroke-linecap='round'><path d='M7 4.4V13.6M3 6.7L11 11.3M3 11.3L11 6.7'/><path d='M18 11V17M15.4 12.5L20.6 15.5M15.4 15.5L20.6 12.5'/></g><circle cx='19' cy='4' r='1.2' fill='#fff'/><circle cx='3' cy='17' r='1' fill='#fff'/>")),
    'hearts': ('#FFD6E8', _tile("<rect width='24' height='20' fill='#FFD6E8'/><path d='M7 15.2C4 13 2.4 11.4 2.4 9.4C2.4 7.8 3.6 6.8 4.9 6.8C5.8 6.8 6.5 7.3 7 8C7.5 7.3 8.2 6.8 9.1 6.8C10.4 6.8 11.6 7.8 11.6 9.4C11.6 11.4 10 13 7 15.2Z' fill='#FF6F8E'/><path d='M18 9.2C16.4 8 15.6 7.2 15.6 6.2C15.6 5.4 16.2 4.8 16.9 4.8C17.4 4.8 17.8 5.1 18 5.4C18.2 5.1 18.6 4.8 19.1 4.8C19.8 4.8 20.4 5.4 20.4 6.2C20.4 7.2 19.6 8 18 9.2Z' fill='#fff'/>")),
    'redenv': ('#FF6F8E', _tile("<rect width='24' height='20' fill='#FF6F8E'/><rect x='4' y='4' width='8' height='12' rx='1.6' fill='#C3264F'/><circle cx='8' cy='9' r='1.8' fill='#FFDA4A'/><path d='M14 14q3-4 6 0' stroke='#FFDA4A' stroke-width='1.4' fill='none'/>")),
    'halloween': ('#E4D6FF', _tile("<rect width='24' height='20' fill='#E4D6FF'/><path d='M3 13C3 8.4 5.6 6 8 6S13 8.4 13 13L11.4 11.8L9.8 13L8 11.8L6.2 13L4.6 11.8Z' fill='#fff' stroke='#6542B3' stroke-width='1'/><circle cx='6.6' cy='9.4' r='.8' fill='#6542B3'/><circle cx='9.4' cy='9.4' r='.8' fill='#6542B3'/><ellipse cx='18.4' cy='12' rx='3.4' ry='2.8' fill='#FFA477'/>")),
}
TAPES['rain'] = ('#CCEBFF', _tile("<rect width='24' height='20' fill='#EAF6FF'/><path d='M0 0H24' stroke='#7CC6F5' stroke-width='3'/>"
    "<path d='M7 6C7 6 4.6 9.4 4.6 11A2.4 2.4 0 0 0 9.4 11C9.4 9.4 7 6 7 6Z' fill='#7CC6F5' stroke='#1E6AA3' stroke-width='.8'/>"
    "<path d='M17 10C17 10 15 12.8 15 14.2A2 2 0 0 0 19 14.2C19 12.8 17 10 17 10Z' fill='#7CC6F5' stroke='#1E6AA3' stroke-width='.8'/><circle cx='12' cy='16' r='1' fill='#7CC6F5'/>"))
for _k, (_bg, _svg) in list(TAPES.items()):
    if not _svg.endswith('</svg>'):
        _svg = _svg + '</svg>'
    TAPES[_k] = (_bg, _svg)


def ring28(cls='sk-ring'):
    return (f'<svg class="{cls}" viewBox="0 0 28 28" width="28" height="28" aria-hidden="true">'
            f'<circle cx="14" cy="14" r="13" fill="#fff" stroke="currentColor" stroke-width="2" stroke-dasharray="3.4 2.6"/></svg>')


def heart_svg(size=14, col='#C3264F', fill='#FF9FC8'):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" aria-hidden="true"><path d="{heart_path(12, 12.6, 9.2)}" fill="{fill}" stroke="{col}" stroke-width="2.2" stroke-linejoin="round"/>'
            f'<path d="M7 9.6Q7.4 7.4 9.4 7" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round"/></svg>')


def star_svg(size=14, fill='#FFDA4A', col='#8A6800', n=5):
    p = star_path(12, 12.8, 10, 4.6, n) if n == 5 else 'M12 1.6C13 9 15 11 22.4 12C15 13 13 15 12 22.4C11 15 9 13 1.6 12C9 11 11 9 12 1.6Z'
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" aria-hidden="true"><path d="{p}" fill="{fill}" stroke="{col}" stroke-width="2" stroke-linejoin="round"/></svg>')


def deco(key, size, style='', cls='sk-stk', label=None):
    lab = f' role="img" aria-label="{label}"' if label else ' aria-hidden="true"'
    return f'<svg class="{cls}" width="{size}" height="{size}" style="{style}"{lab}><use href="#sticker-i-{key}"/></svg>'


def tape(style='', cls=''):
    return f'<i class="sk-tape {cls}" style="{style}" aria-hidden="true"></i>'


def lcd(text, label, star=True, p=2.2, cls=''):
    return f'<span class="sk-lcd {cls}" data-p="{p}" role="img" aria-label="{label}">{lcd_svg(text, p=p, star=star)}</span>'


def status(time='10:24'):
    return (f'<div class="sk-status" aria-hidden="true"><span>{time}</span><i class="sk-cam"></i><span class="sk-sys">'
            '<svg width="17" height="12" viewBox="0 0 17 12"><g fill="currentColor"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="4.6" y="5.4" width="3" height="6.6" rx="1"/><rect x="9.2" y="2.8" width="3" height="9.2" rx="1"/><rect x="13.8" y="0" width="3" height="12" rx="1"/></g></svg>'
            '<svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.4L.6 4Q8 -2.6 15.4 4Z" fill="currentColor"/></svg>'
            '<svg width="24" height="12" viewBox="0 0 24 12"><rect x=".8" y=".8" width="19.4" height="10.4" rx="3" fill="none" stroke="currentColor" stroke-width="1.6"/><rect x="2.8" y="2.8" width="12" height="6.4" rx="1.4" fill="currentColor"/><rect x="21.4" y="4" width="2" height="4" rx="1" fill="currentColor"/></svg>'
            '</span></div>')


def rbtn(icon, label, cls=''):
    return f'<button class="sk-rbtn {cls}" type="button" aria-label="{label}">{tb(icon)}</button>'


def tray(fam, pat, n, name, body, meta='', cls='', right='', big=False, attrs='', lead=''):
    """r4 aisle tray: one kiss-cut object in the aisle's colour that OWNS its rows. Pastel header band (number + name),
    rows on a lighter inner panel with 1 dp rules between them, and a patterned pastel end cap. White 3 dp border + hard shadow."""
    num = lead or (f'<span class="sk-num">{n}</span>' if n is not None else '')
    m = f'<span class="sk-trm">{meta}</span>' if meta else ''
    return (f'<div class="sk-tray{" " + cls if cls else ""}" style="{avars(fam, pat)}"{attrs}><div class="sk-trh">{num}'
            f'<span class="sk-trn{" is-big" if big else ""}">{name}</span>{m}{right}</div><ul class="sk-trb">{body}</ul><i class="sk-trcap" aria-hidden="true"></i></div>')
