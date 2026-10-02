"""r9 · the live prototype on phones 1 + 6: a real add field (smart sort, magic words), the Write / Snap / Speak letter-set
sheet, toasts, the collapsed FAB, a more-options menu and demo controls. Data for the script is emitted as JSON."""
import json
from common import *
from deco import hanko_id

# the Saturday-shop store: icon family -> aisle number (walking order)
FAM2AISLE = {'produce': 1, 'bakery': 2, 'meat': 3, 'deli': 3, 'seafood': 3, 'baking': 4, 'pantry': 4, 'breakfast': 5, 'snacks': 5,
             'dairy': 6, 'drinks': 6, 'frozen': 7, 'household': 8, 'pets': 8, 'baby': 8, 'pharmacy': 8, 'unknown': 8}
BERRY = {'strawberries', 'blueberries', 'cherries', 'grapes'}
CANS = {'maple', 'bakingpowder', 'tomatocan', 'beans', 'soup', 'tuna'}
SYN = {
    'strawberries': ['strawberry', 'fraises', 'fraise', 'fresas', 'fresa', 'erdbeeren'],
    'apple': ['apples', 'apple', 'pommes', 'pomme', 'manzanas', 'manzana', 'mcintosh', 'gala apples'],
    'banana': ['banana', 'bananes'], 'lemon': ['lemon', 'citrons'], 'loaf': ['sandwich bread', 'bread', 'pain de mie'],
    'watermelon': ['watermelon', 'pasteque', 'pastèque', 'melon d’eau', "melon d'eau"], 'lemonade': ['lemonade', 'limonade'],
    'milkbag': ['milk', 'lait'], 'eggs': ['egg', 'oeufs', 'œufs'], 'catfood': ['cat food'], 'tortillas': ['tortilla', 'tortillas', 'wraps'],
    'crackers': ['cracker'], 'chips': ['chips', 'crisps'], 'icecream': ['ice cream', 'gelato'], 'toiletpaper': ['toilet paper', 'tp'],
}
EXTRA = [  # stickers that are not in the 121-icon catalog: the birthday egg and the six live-drawn AI stickers
    dict(k='cake', n='Birthday cake', q='1 cake · 8 slices', a=2, c='bread', t=['birthday cake', 'gateau', 'gâteau', 'gâteau d’anniversaire', 'cake'], bday=1),
    dict(k='cake', n='Birthday candles', q='24 pack', a=8, c='', t=['candles', 'candle', 'bougies'], bday=1),
    dict(k='ai-dragonfruit', n='Dragon fruit', q='each', a=1, c='', t=['dragon fruit', 'dragonfruit', 'pitaya'], ai=1),
    dict(k='ai-bokchoy', n='Bok choy', q='1 bunch', a=1, c='', t=['bok choy', 'bokchoy', 'pak choi'], ai=1),
    dict(k='ai-kimchi', n='Kimchi', q='500 mL jar', a=3, c='', t=['kimchi'], ai=1),
    dict(k='ai-pierogi', n='Pierogi, potato & cheddar', q='900 g bag', a=7, c='frozen', t=['pierogi', 'perogies', 'pierogies'], ai=1),
    dict(k='ai-bagel', n='Sesame bagels', q='6 pack', a=2, c='bread', t=['bagels', 'bagel'], ai=1),
    dict(k='ai-oatmilk', n='Oat milk', q='1.75 L carton', a=6, c='', t=['oat milk', 'oatmilk'], ai=1),
]
PICNIC = ['watermelon', 'loaf', 'lemonade', 'strawberries', 'cherries', 'grapes', 'chips', 'sparkling', 'baguette', 'cookies', 'sandwich']


def catalog():
    out = []
    for i in ICONS:
        if i.aisle == 'unknown':
            continue
        cat = 'berry' if i.key in BERRY else 'egg' if i.key == 'eggs' else 'bread' if i.aisle == 'bakery' else 'can' if i.key in CANS else 'frozen' if i.aisle == 'frozen' else ''
        terms = [i.label.lower()] + SYN.get(i.key, [])
        last = i.label.lower().split(',')[0].split()[-1]
        if last not in terms and len(last) > 3:
            terms.append(last)
        out.append(dict(k=i.key, n=i.label, q=i.qty, a=FAM2AISLE.get(i.aisle, 8), c=cat, t=terms))
    out += EXTRA
    return out


def script_data():
    bday = hanko_id(('HAPPY', 'B-DAY!'))
    jack = hanko_id(('JACK', 'POT!'))
    return json.dumps(dict(cat=catalog(), picnic=PICNIC, bday=bday, jack=jack), ensure_ascii=False, separators=(',', ':'))


def field_live(ph1='Write a note…', ph2='“pancakes from scratch”', cls='sk-addf', label='Write an item, a few items or a recipe'):
    return (f'<label class="sk-field {cls}"><input class="sk-fin" type="text" enterkeyhint="send" autocomplete="off" spellcheck="false" aria-label="{label}">'
            f'<span class="sk-fv is-ph" aria-hidden="true">{ph1}<span class="sk-phx">{ph2}</span></span>'
            f'<svg class="sk-fheart" viewBox="0 0 24 24" aria-hidden="true"><use href="#sticker-i-heart" width="24" height="24"/></svg></label>')


def addbar_live(tape_cls='sk-t-pumpkin'):
    return (f'<div class="sk-addbar">' + tape('', tape_cls) +
            f'<span class="sk-adddoodle" aria-hidden="true">{heart_svg(12)}<span>picnic day</span>{star_svg(12)}</span>'
            + field_live() +
            f'<button class="sk-sbtn" type="button" data-sk="snap" aria-label="Snap a photo of a list">{ico("camera", 52)}</button>'
            f'<button class="sk-sbtn" type="button" data-sk="speak" aria-label="Speak a voice note">{ico("cassette", 52)}</button>'
            f'<button class="sk-sbtn" type="button" data-sk="send" aria-label="Send">{ico("envelope", 52)}</button>'
            '</div>'
            f'<button class="sk-fab sk-livefab" type="button" data-sk="fab" aria-label="Add to the list" hidden>{fab()}</button>')


def fab():
    return ('<svg viewBox="-4 -4 72 72" width="64" height="64" aria-hidden="true">'
            '<circle cx="32" cy="32" r="30" fill="var(--sk-shadow,#6E7BD0)" transform="translate(2.4 3.4)"/><circle cx="32" cy="32" r="30" fill="#fff"/>'
            '<circle cx="32" cy="32" r="26.5" fill="#FF6F8E" stroke="#C3264F" stroke-width="2"/>'
            '<path d="M32 20V44M20 32H44" stroke="#C3264F" stroke-width="10" stroke-linecap="round"/><path d="M32 20V44M20 32H44" stroke="#fff" stroke-width="5" stroke-linecap="round"/>'
            '<path d="M14 24Q16 16 23 13" stroke="#fff" stroke-width="3" stroke-linecap="round" fill="none"/><circle cx="12.6" cy="29" r="1.4" fill="#fff"/>'
            '<use href="#sticker-i-star" x="44" y="-4" width="22" height="22"/></svg>')


def _tabs(on='write'):
    t = [('write', 'Write', 'lemon', 'pencil'), ('snap', 'Snap', 'ramune', 'cam'), ('speak', 'Speak', 'sakura', 'mic')]
    return ('<div class="sk-stabs" role="tablist">' + ''.join(
        f'<button type="button" class="sk-stab{" is-on" if k == on else ""}" style="{avars(f)}" role="tab" aria-selected="{"true" if k == on else "false"}" data-sk="ltab" data-tab="{k}">{tb(i, 20)}{l}<span class="sk-recdot" aria-hidden="true"></span></button>'
        for k, l, f, i in t) + '</div>')


def _keep(on, name):
    inner = f'{spot()}<svg class="sk-ks" aria-hidden="true"><use href="#sticker-i-star"/></svg>'
    return f'<button class="sk-keep" type="button" role="switch" aria-checked="{"true" if on else "false"}" aria-label="Keep {name}" data-sk="keep">{inner}</button>'


def _irow(key, name, det, on, extra=''):
    sym = f'sticker-{key}' if key.startswith('ai-') else f'sticker-i-{key}'
    return (f'<li class="sk-irow{"" if on else " is-off"}" data-key="{key}"{extra}><span class="sk-ico"><svg width="40" height="40" role="img" aria-label="{name}"><use href="#{sym}"/></svg></span>'
            f'<span class="sk-txt"><span class="sk-nm">{name}</span><span class="sk-dt">{det}</span></span>{_keep(on, name)}</li>')


# the live recipe: "pancakes from scratch" checked against the list (most of it is already on it since 10:12) and the pantry
RECIPE_LIVE = [
    ('Produce', 'strawberry', 'gingham', 1, [('blueberries', 'Blueberries', 'optional · 1 cup', 1, 'Blueberries|551 mL pint|1|berry')]),
    ('Baking', 'melon', 'hearts', 4, [('flour', 'All-purpose flour', 'already on your list', 0, ''), ('bakingpowder', 'Baking powder', 'already on your list', 0, ''),
                                      ('sugar', 'White sugar', 'already on your list', 0, ''), ('salt', 'Sea salt', 'in your pantry · ½ tsp', 0, ''), ('vanilla', 'Vanilla extract', 'in your pantry · 1 tsp', 0, '')]),
    ('Dairy & eggs', 'peach', 'clouds', 6, [('eggs', 'Eggs, large', 'already on your list', 0, ''), ('butter', 'Butter, salted', 'already on your list', 0, ''),
                                            ('milkbag', 'Milk, 2 %', 'already on your list', 0, ''), ('cream', 'Whipping cream', 'for topping · 473 mL', 0, 'Whipping cream|473 mL carton|6|')]),
]
SNAP_LINES = [('lemons', 'lemons', 'lemon'), ('<s>chips</s> crackers', 'crackers', 'crackers'), ('oat milk', 'oat milk', 'ai-oatmilk'), ('tortillas', 'tortillas', 'tortillas'), ('dish soap ♡', 'dish soap', 'dishsoap')]


def lsheet():
    groups = []
    for aname, fam, pat, n, items in RECIPE_LIVE:
        rows = ''.join(_irow(k, nm, det, on, f' data-add="{add}"' if add else '') for k, nm, det, on, add in items)
        groups.append(tray(fam, pat, n, aname, rows, meta='', cls='sk-agrp', big=True))
    think = (f'<div class="sk-think sk-lthink" role="status" hidden><svg class="sk-wobble" width="52" height="52" aria-hidden="true" style="--cap:var(--sk-melon-m);--cap-tr:var(--sk-melon-t);--cap-p:var(--sk-melon-p)"><use href="#sticker-i-capsule"/></svg>'
             f'<div><p class="sk-tt">Looking up a recipe…</p><ol>'
             f'<li>{tb("check", 14)} Searched the web: “pancakes from scratch”</li><li>{tb("check", 14)} Compared 3 recipes · makes 12</li>'
             f'<li>{tb("check", 14)} Checked your list + pantry</li></ol></div></div>')
    recipe = (f'<div class="sk-recipewrap sk-lrecipe" hidden><div class="sk-recipe"><h4>Pancakes from scratch</h4><p class="sk-from">from the kitchen of {heart_svg(15)} the web</p>'
              f'<p class="sk-rmeta"><span class="sk-chip" style="--cp:var(--sk-ramune-p);min-height:28px;padding:2px 10px">{tb("web", 14)} web search</span><span data-sk="rkept">1 of 10 kept</span></p>'
              f'{"".join(groups)}</div></div>')
    write = (f'<div class="sk-lpanel" data-tab="write"><div class="sk-field is-focus sk-wfield"><span class="sk-flab">Note:</span>'
             f'<input class="sk-fin" type="text" enterkeyhint="send" autocomplete="off" spellcheck="false" aria-label="Note: an item, a few items or a recipe" placeholder="pancakes from scratch">'
             f'<svg class="sk-fheart" viewBox="0 0 24 24" aria-hidden="true"><use href="#sticker-i-heart" width="24" height="24"/></svg></div>'
             f'<p class="sk-whint">Try <b>pancakes from scratch</b>, or a few things: <b>lemons, oat milk</b>.</p>{think}{recipe}</div>')
    paper = '<div class="sk-paper">' + '<br>'.join(t for t, _, _ in SNAP_LINES).replace('♡', '<svg width="12" height="11" viewBox="0 0 24 22" aria-hidden="true"><path d="M12 20C5 15 2 11.4 2 7.6C2 4.4 4.4 2.4 7 2.4C9 2.4 10.6 3.6 12 5.4C13.4 3.6 15 2.4 17 2.4C19.6 2.4 22 4.4 22 7.6C22 11.4 19 15 12 20Z" fill="none" stroke="#2B3A8C" stroke-width="2.2"/></svg>') + '</div>'
    pol = (f'<div class="sk-polaroid">{tape("left:-14px;top:-8px;transform:rotate(-38deg)", "sk-t-ging")}'
           f'<div class="sk-photo" role="img" aria-label="Photo of a handwritten list">{paper}<i class="sk-shade"></i><i class="sk-scanband"></i><i class="sk-scanline"></i></div><span class="sk-pcap">note on the fridge</span></div>')
    tl = ''.join(f'<li data-i="{i}">{tb("check", 14)}{shown}</li>' for i, (_, shown, _) in enumerate(SNAP_LINES))
    snap = (f'<div class="sk-lpanel" data-tab="snap" hidden><div class="sk-snap">{pol}<div class="sk-trans"><p class="sk-tth">{tb("sparkle", 16)}Reading your list…</p><ol class="sk-ltrans">{tl}</ol></div></div>'
            + tray('periwinkle', 'stars', None, 'Popped into stickers', '', meta='<span data-sk="snapn">0 of 5</span>', lead=f'<span class="sk-trlead">{tb("sparkle", 14)}</span>').replace('class="sk-trb"', 'class="sk-trb sk-found sk-lfound"', 1) + '</div>')
    speak = (f'<div class="sk-lpanel" data-tab="speak" hidden><div class="sk-speak"><div class="sk-casswrap">{_cass()}<button class="sk-stopbtn" type="button" data-sk="lstop" aria-label="Stop recording">{tb("stop", 16)}</button></div>'
             f'<div class="sk-speakr"><div style="display:flex;align-items:center;gap:8px"><span class="sk-recdot" aria-hidden="true"></span>{lcd("0:00", "Recording, 0 seconds", star=False, cls="sk-ltimer")}</div>{_eq()}</div>'
             f'<p class="sk-voicetx sk-lvoice" aria-live="polite"></p></div>'
             + tray('sakura', 'waves', None, 'Heard', '', meta='<span data-sk="heardn">0 stickers</span>', lead=f'<span class="sk-trlead">{tb("mic", 14)}</span>').replace('class="sk-trb"', 'class="sk-trb sk-found sk-lheard"', 1) + '</div>')
    foot = (f'<div class="sk-sheetfoot"><button class="sk-btn sk-wide sk-lstick" type="button" data-sk="lstick" disabled>{star_svg(18, "#FFF3A6", "#C3264F")}<span>Stick it on my list</span></button></div>')
    return (f'<div class="sk-lsheetw" hidden><div class="sk-scrim" data-sk="lclose"></div><div class="sk-sheet sk-lsheet" role="dialog" aria-label="Add to your list">'
            f'<button class="sk-handle" type="button" data-sk="lclose" aria-label="Close"></button>{_tabs()}<div class="sk-sheetbody">{write}{snap}{speak}</div>{foot}</div></div>')


def _cass():
    reels = ''.join(f'<g class="sk-reel"><circle cx="{cx}" cy="32" r="11" fill="#FF9FC8" stroke="#B02E6B" stroke-width="2"/>'
                    f'<path d="M{cx} 24V40M{cx - 8} 32H{cx + 8}" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/><circle cx="{cx}" cy="32" r="3" fill="#fff" stroke="#B02E6B" stroke-width="1.4"/></g>' for cx in (30, 100))
    return (f'<svg class="sk-cassette" viewBox="-3 -3 136 92" role="img" aria-label="Cassette recorder">'
            f'<rect x="0" y="0" width="130" height="86" rx="12" fill="#6E7BD0" transform="translate(2.4 3.4)"/><rect x="0" y="0" width="130" height="86" rx="12" fill="#FFD6E8" stroke="#fff" stroke-width="4"/>'
            f'<rect x="2" y="2" width="126" height="82" rx="10" fill="none" stroke="#B02E6B" stroke-width="2"/><rect x="14" y="12" width="102" height="40" rx="8" fill="#fff" stroke="#B02E6B" stroke-width="2"/>'
            f'<rect x="38" y="22" width="54" height="20" rx="4" fill="#FFF3A6" stroke="#B02E6B" stroke-width="1.6"/>{reels}'
            f'<path d="M30 86L36 68H94L100 86" fill="#FFD6E8" stroke="#B02E6B" stroke-width="2" stroke-linejoin="round"/><path d="M10 10Q12 5 18 5" stroke="#fff" stroke-width="3" stroke-linecap="round" fill="none"/></svg>')


def _eq(cols=7):
    return '<div class="sk-eqrow" aria-hidden="true">' + ''.join(f'<div class="sk-eqbar" style="--d:-{i * .23:.2f}s">' + '<i></i>' * 6 + '</div>' for i in range(cols)) + '</div>'


def menu(items):
    return ('<div class="sk-menu" role="menu" hidden>' + ''.join(f'<button type="button" role="menuitem" data-sk="menu" data-go="{g}">{tb(i, 18)}{t}</button>' for t, i, g in items) + '</div>')


LIST_MENU = [('New trip (fresh sticker book)', 'plus', 'newtrip'), ('My store &amp; AI', 'gear', 'store'), ('Surprises', 'sparkle', 'surprises'), ('Sticker album', 'sort', 'album')]


def democtl():
    return (f'<div class="sk-democtl" role="group" aria-label="Demo controls for phone 1">'
            f'<button class="sk-chip" type="button" data-sk="demo" data-do="next">{tb("check", 14)}Check the next item</button>'
            f'<button class="sk-chip" type="button" data-sk="demo" data-do="finish">{star_svg(14)}Finish the trip</button>'
            f'<button class="sk-chip" type="button" data-sk="demo" data-do="newtrip">{tb("resort", 14)}New trip</button></div>')
