from common import *
from book import book, got_in_walking_order, BOOK_DATE, showcase
from live import addbar_live, lsheet, menu, LIST_MENU, democtl

# ---------------- list data (walking order) ----------------
# item: (key, name, detail, checked, badge, state)
AISLES = [
    dict(n=1, name='Produce', basket='picnic', state='fold',
         items=[('strawberries', 'Strawberries', '1 L basket', 0), ('banana', 'Bananas', '6 bananas', 1), ('apple', 'McIntosh apples', '1.36 kg bag', 0),
                ('carrots', 'Carrots', '907 g bag', 1), ('broccoli', 'Broccoli', '1 crown', 1)]),
    dict(n=2, name='Bakery', basket='bread', state='fold',
         items=[('sourdough', 'Sourdough', '800 g boule', 0), ('croissant', 'Croissants', '4 pack', 1), ('baguette', 'Baguette', '1 stick', 0)]),
    dict(n=3, name='Deli & meat', basket='bento', state='fold',
         items=[('ham', 'Spiral ham', '1.5 kg', 1), ('peameal', 'Peameal bacon', '600 g roast', 1)]),
    dict(n=4, name='Baking', store='2', basket='bowl', state='open', current=True,
         items=[('flour', 'All-purpose flour', '2.5 kg bag', 1), ('sugar', 'White sugar', '2 kg bag', 1), ('bakingpowder', 'Baking powder', '225 g tin · from your recipe', 0, 'new')]),
    dict(n=5, name='Breakfast', basket='crate', state='fold',
         items=[('coffee', 'Coffee beans', '340 g bag', 1, 'fav'), ('maple', 'Maple syrup', '540 mL tin', 0, 'shiny')]),
    dict(n=6, name='Dairy & eggs', basket='eggbasket', state='fold',
         items=[('milkbag', 'Milk, 2 %', '4 L bag', 0), ('eggs', 'Eggs, large', 'dozen', 0, 'new'), ('butter', 'Butter, salted', '454 g brick', 1, 'new')]),
    dict(n=7, name='Frozen', basket='cooler', state='fold', doodle='brrr…',
         items=[('peas', 'Frozen peas', '750 g bag', 0), ('icecream', 'Ice cream', '1.5 L tub', 0)]),
    dict(n=8, name='Household', basket='tote', state='fold',
         items=[('toiletpaper', 'Toilet paper', '12 double rolls', 0), ('dishsoap', 'Dish soap', '740 mL', 0), ('kibble', 'Dry cat food', '7 kg bag · pets', 0), ('toothpaste', 'Toothpaste', '100 mL tube', 0)]),
]
for i, a in enumerate(AISLES):
    a['fam'], a['pat'], a['pic'] = FAMS[i], PATS[i], PICS[i]
TOTAL = sum(len(a['items']) for a in AISLES)
DONE = sum(it[3] for a in AISLES for it in a['items'])
RECIPE_KEPT = 5            # flour, baking powder, sugar, eggs, butter were stuck on at 10:12
DONE_1012 = DONE - 3       # flour, sugar and butter came with the recipe and were checked after it was added

HROT = [-12, -9, -15, -11, -14, -8, -13, -10]
CAT = {'peas': 'frozen', 'icecream': 'frozen', 'eggs': 'egg', 'sourdough': 'bread', 'croissant': 'bread', 'baguette': 'bread', 'loaf': 'bread',
       'bakingpowder': 'can', 'maple': 'can', 'tomatocan': 'can', 'beans': 'can', 'soup': 'can', 'strawberries': 'berry', 'blueberries': 'berry', 'cherries': 'berry'}


def mini_key(it):
    return it[0] + '-holo' if len(it) > 4 and it[4] == 'shiny' else it[0]


def waiting_inside(a):
    return [mini_key(it) for it in a['items'] if not it[3]], [mini_key(it) for it in a['items'] if it[3]]


def row(it, idx=0, cls='', live=False):
    key, name, qty, chk = it[:4]
    badge = it[4] if len(it) > 4 else None
    state = it[5] if len(it) > 5 else ''
    lab = f'{name}, {qty}'
    b, extra_dt, ikey, icls = '', '', key, ''
    if badge == 'new':
        b = '<svg class="sk-badgeico" aria-hidden="true"><use href="#sticker-i-star"/></svg>'
    elif badge == 'fav':
        b = '<svg class="sk-badgeico" aria-hidden="true"><use href="#sticker-i-heart"/></svg>'
    elif badge == 'shiny':
        b = '<svg class="sk-badgeico" aria-hidden="true"><use href="#sticker-i-star"/></svg>'
        ikey, icls = key + '-holo', ' is-shiny'
        extra_dt = '<span class="sk-shinychip">shiny!</span>'
    icon = ico(ikey, 52, label=lab, cls='sk-real', extra=glint_svg(key)) + ghost(key)
    cb = spot() + hanko(HROT[idx % len(HROT)], 40)
    c = 'sk-row' + (' is-checked' if chk else '') + (' is-pressed' if state == 'pressed' else '') + (' ' + cls if cls else '')
    dsk = ' data-sk="row"' if live else ''
    cat = f' data-cat="{CAT[key]}"' if key in CAT else ''
    dm = f' data-mini="{key}-holo"' if badge == 'shiny' else ''
    return (f'<li class="{c}" data-key="{key}"{dm}{cat}{dsk}><span class="sk-ico{icls}">{icon}{b}</span>'
            f'<span class="sk-txt"><span class="sk-nm">{name}</span><span class="sk-dt">{qty}{extra_dt}</span></span>'
            f'<span class="sk-cb" role="checkbox" tabindex="0" aria-checked="{"true" if chk else "false"}" aria-label="{name}">{cb}</span></li>')


def tab(a, cls=''):
    return (f'<span class="sk-tab {cls}" style="{avars(a["fam"], a["pat"])}"><span class="sk-num">{a["n"]}</span>'
            f'<span class="sk-tabname">{a["name"]}</span></span>')


def badge(a, left, fold=False):
    # a count sticker in the basket's top-right flag slot; a finished aisle shows no badge (its lid wears the hanko)
    return f'<span class="sk-badge" aria-hidden="true"{" hidden" if left == 0 else ""}>{left}</span>'


def fold_meta(a):
    w, i = waiting_inside(a)
    n = len(a['items'])
    return f'<b>{n} of {n}</b> in the basket' if not w else f'<b>{len(w)} left</b> of {n}'


def fold_page(a, extra_cls=''):
    w, i = waiting_inside(a)
    done = not w
    bk = basket_svg(BBY[a['basket']], i, 'done' if done else 'open', 80, 64, live=True)
    plain = re.sub('<[^>]+>', '', fold_meta(a))
    return (f'<div class="sk-page sk-folded {extra_cls}" style="{avars(a["fam"], a["pat"])}">'
            f'<button class="sk-fold" type="button" data-sk="fold" aria-expanded="false" aria-label="Aisle {a["n"]}, {a["name"]}: {plain}. Expand">'
            f'<span class="sk-fl">{tab(a)}<span class="sk-fmeta">{fold_meta(a)}</span></span>'
            f'<span class="sk-bkw">{bk}{badge(a, len(w), True)}</span><span class="sk-chev"><span>{tb("chev-down", 20)}</span></span></button>'
            f'<i class="sk-ribbon" aria-hidden="true"><svg width="30" height="30"><use href="#sticker-i-ribbon"/></svg></i></div>')


def aisle_chip(n):
    return (f'<span class="sk-achip" title="Store aisle {n}"><svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 2V6M17 2V6" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"/><rect x="4" y="6" width="16" height="8" rx="2" fill="none" stroke="currentColor" stroke-width="2.6"/></svg>'
            f'<span class="sk-vh">store aisle </span>A{n}</span>')


def open_page(a, extra_cls='', rows_cls='', dark=False):
    w, i = waiting_inside(a)
    cur = a.get('current')
    meta = aisle_chip(a['store']) if a.get('store') else ''
    if a.get('doodle'):
        meta += f'<span class="sk-tdoodle" aria-hidden="true">{maru_svg(a["doodle"])}</span>'
    bk = basket_svg(BBY[a['basket']], i, 'done' if not w else 'open', 64, 51, live=True)
    bee = f'<span class="sk-beefly" aria-hidden="true"><span class="sk-beeloop2">{deco("bee", 22, "position:static", "")}</span></span>' if cur else ''
    lady = '<span class="sk-ladywalk" aria-hidden="true"><svg width="14" height="14"><use href="#sticker-i-ladybug"/></svg></span>' if a['n'] == 4 else ''
    rows = ''.join(row(it, j + a['n'], live=True) for j, it in enumerate(a['items']))
    c = 'sk-page sk-ruled sk-opened' + (' is-current' if cur else '') + (' ' + extra_cls if extra_cls else '')
    return (f'<div class="{c}" style="{avars(a["fam"], a["pat"])}">'
            f'<div class="sk-tabrow">{tab(a)}<span class="sk-tmeta">{meta}</span><span class="sk-bkw">{bk}{badge(a, len(w))}</span>'
            f'<button class="sk-chev" type="button" data-sk="fold" aria-expanded="true" aria-label="Collapse {a["name"]}"><span>{tb("chev-up", 20)}</span></button>{bee}</div>'
            f'<ul class="sk-rows {rows_cls}">{rows}</ul><i class="sk-trcap" aria-hidden="true"></i>{lady}</div>')


def maru_svg(text):
    """Hachi Maru doodle text where ♡ and ☆ are drawn as vectors"""
    out = ''
    for part in re.split(r'(♡|☆)', text):
        if part == '♡':
            out += heart_svg(12)
        elif part == '☆':
            out += star_svg(12)
        elif part:
            out += f'<span>{part}</span>'
    return out


def stampstrip(done=(3,), now=4):
    out = []
    for a in AISLES:
        inner = (f'<svg class="sk-pg" style="--st:var(--sk-{a["fam"]}-t)" aria-hidden="true"><use href="#sticker-pg-{a["pic"]}"/></svg>' if a['n'] in done
                 else f'<span class="sk-sn">{a["n"]}</span>')
        c = 'sk-stamp' + (' is-now' if a['n'] == now else '') + (' is-done' if a['n'] in done else '')
        lab = f'{a["name"]}: {"stamped" if a["n"] in done else "not yet"}'
        out.append(f'<span class="{c}" data-n="{a["n"]}" data-pic="{a["pic"]}" data-fam="{a["fam"]}" role="img" aria-label="{lab}">{ring28()}{inner}</span>')
    return f'<div class="sk-stampstrip" aria-label="Stamp card">{"".join(out)}</div>'


def appbar(title='Saturday shop', sub=None, right=None, egg='sun', lcdtext=None, pet=False):
    sub = sub or f'{star_svg(13)} Hillcrest Market · 8 stops'
    lt = lcdtext or f'{DONE}/{TOTAL}'
    if right is None:
        petsvg = f'<span class="sk-pet" aria-hidden="true">{pixbee()}</span>' if pet else ''
        right = (f'<span class="sk-lcd sk-lcdmain{" has-pet" if pet else ""}" data-p="1.9" role="img" aria-label="{lt.replace("/", " of ")} in the basket">{petsvg}{lcd_svg(lt, p=1.9, star=not pet)}</span>'
                 + rbtn('more', 'More options'))
    eg = ''
    if egg == 'sun':
        eg = (f'<button class="sk-sunegg" type="button" data-sk="sunegg" aria-label="Sun sticker">{ico("sun", 26)}<span class="sk-moonegg" aria-hidden="true">{ico("moon", 24)}</span></button>')
    elif egg == 'moon':
        eg = f'<span class="sk-sunegg is-static" aria-hidden="true">{ico("moon", 24)}</span>'
    return (f'<header class="sk-appbar"><div class="sk-abt"><h3 class="sk-puff">{title}</h3>{eg}<p class="sk-sub">{sub}</p></div>{right}</header>')


def pixbee():
    pet = ['001101100', '010010010', '001111100', '010101011', '110101011', '010101011', '001111100']
    px = ''.join(f'<rect x="{x * 2}" y="{y * 2}" width="1.6" height="1.6"/>' for y, rr in enumerate(pet) for x, ch in enumerate(rr) if ch == '1')
    return f'<svg width="18" height="14" viewBox="0 0 18 14" fill="currentColor" aria-hidden="true">{px}</svg>'


def addbar(tape_cls='sk-t-pumpkin'):
    return (f'<div class="sk-addbar">' + tape('', tape_cls) +
            f'<span class="sk-adddoodle" aria-hidden="true">{maru_svg("♡ picnic day ☆")}</span>'
            '<div class="sk-field" aria-label="Type, snap or speak"><span class="sk-fv is-ph">Write a note…<span class="sk-phx">“pancakes from scratch”</span></span></div>'
            f'<button class="sk-sbtn" type="button" aria-label="Snap a photo of a list">{ico("camera", 52)}</button>'
            f'<button class="sk-sbtn" type="button" aria-label="Speak a voice note">{ico("cassette", 52)}</button>'
            f'<button class="sk-sbtn" type="button" aria-label="Send">{ico("envelope", 52)}</button>'
            '</div>')


def list_pages(dark=False):
    out = []
    for a in AISLES:
        op = a['state'] == 'open'
        w, i = waiting_inside(a)
        out.append(f'<div class="sk-aw{" is-open" if op else ""}{" is-complete" if not w else ""}" data-n="{a["n"]}" data-name="{a["name"]}" data-fam="{a["fam"]}" data-pat="{a["pat"]}" data-pic="{a["pic"]}">'
                   f'{fold_page(a)}{open_page(a, dark=dark)}</div>')
    return ''.join(out)


def nextcard():
    """r9: less text, longer hold. Just "NEXT ✦" + the next aisle's tab sticker (number + name); no duplicate pill.
    Rises in 300 ms, holds still >= max(2.5 s, 1.2 s + 0.3 s/word), leaves in 300 ms; one tap skips."""
    sp = (deco('star4', 24, 'left:12px;top:10px', 'sk-stk sk-twinkle') + deco('star', 20, 'right:14px;top:14px', 'sk-stk sk-twinkle sk-d2')
          + deco('heart', 18, 'right:22px;bottom:12px', 'sk-stk sk-twinkle sk-d3'))
    return ('<div class="sk-nextcard" role="status" aria-live="polite" aria-label="Next: aisle 5, Breakfast" data-sk="skipnext" hidden>' + sp +
            '<span class="sk-nx">NEXT ' + star_svg(18, '#FFF3A6', '#8A6800', n=4) + '</span>'
            '<span class="sk-tab sk-nxtab"><span class="sk-num">5</span><span class="sk-tabname">Breakfast</span></span></div>')


GLOW = [(236, 6, 14, 0), (351.5, 300, 9, 1.6), (-.5, 452, 9, 3.1), (351.5, 590, 9, 4.2)]   # binder only: app-bar gap + gutters


def screen_list(dark=False):
    cls = ' sk-dark' if dark else ''
    stars = ''
    if dark:
        stars = ''.join(f'<svg class="sk-glowstar" style="left:{x}px;top:{y}px;animation-delay:-{d}s" width="{s}" height="{s}" aria-hidden="true"><use href="#sticker-i-glowstar"/></svg>'
                        for x, y, s, d in GLOW)
    dusk = '' if dark else ''.join(f'<svg class="sk-duskstar sk-twinkle" style="left:{x}px;top:{y}px;animation-delay:-{d}s" width="9" height="9" aria-hidden="true"><use href="#sticker-i-star4"/></svg>'
                                   for x, y, d in [(-.5, 170, 0), (351.5, 236, .12), (-.5, 330, .25), (351.5, 420, .06), (-.5, 520, .18)])
    bk = book(AISLES, got_in_walking_order(AISLES), done=(3,), now=4)
    return (f'<div class="sk-screen sk-s-list{cls}" data-screen="list">{status("10:24")}{appbar(egg="moon" if dark else "sun")}{bk}{stars}{dusk}'
            f'<div class="sk-scroll" tabindex="0" aria-label="Saturday shop, sorted by aisle">{list_pages(dark)}'
            f'<p class="sk-endnote">{heart_svg(14)} end of the list · {TOTAL} stickers</p></div>'
            f'{addbar_live("sk-t-snow" if dark else "sk-t-pumpkin")}{nextcard()}{lsheet()}{menu(LIST_MENU)}<div class="sk-celehost"></div></div>').replace(
        '<button class="sk-rbtn " type="button" aria-label="More options">', '<button class="sk-rbtn " type="button" aria-label="More options" aria-haspopup="menu" aria-expanded="false" data-sk="more">', 1)


# ---------------- screen 2: add sheet (10:12, before the list screen) ----------------
RECIPE = [
    ('Produce', 'strawberry', 1, [('blueberries', 'Blueberries', 'optional · skipped', 0)]),
    ('Baking', 'melon', 4, [('flour', 'All-purpose flour', '2.5 kg bag · 2 cups', 1), ('bakingpowder', 'Baking powder', '225 g tin · 1 tbsp', 1, 'new'),
                            ('sugar', 'White sugar', '2 kg bag · 2 tbsp', 1), ('salt', 'Sea salt', 'in your pantry · ½ tsp', 0), ('vanilla', 'Vanilla extract', 'in your pantry · 1 tsp', 0),
                            ('maple', 'Maple syrup', 'already on your list', 0)]),
    ('Dairy & eggs', 'peach', 6, [('eggs', 'Eggs, large', 'dozen · 2 eggs', 1), ('butter', 'Butter, salted', '454 g brick · 3 tbsp', 1),
                                  ('milkbag', 'Milk, 2 %', 'already on your list', 0)]),
]


def keep(on, name):
    inner = f'{spot()}<svg class="sk-ks" aria-hidden="true"><use href="#sticker-i-star"/></svg>' if on else spot()
    return f'<button class="sk-keep" type="button" role="switch" aria-checked="{"true" if on else "false"}" aria-label="Keep {name}" data-sk="keep">{inner}</button>'


def irow(it, cls=''):
    key, name, qty, on = it[:4]
    badge_ = it[4] if len(it) > 4 else None
    b = '<svg class="sk-badgeico" aria-hidden="true" style="position:absolute;right:-5px;top:-5px;width:17px;height:17px"><use href="#sticker-i-star"/></svg>' if badge_ == 'new' else ''
    c = 'sk-irow' + ('' if on else ' is-off') + (' ' + cls if cls else '')
    return (f'<li class="{c}"><span class="sk-ico">{ico(key, 40, label=name)}{b}</span><span class="sk-txt"><span class="sk-nm">{name}</span>'
            f'<span class="sk-dt">{qty}</span></span>{keep(on, name)}</li>')


def stabs(on='write', rec=False):
    t = [('write', 'Write', 'lemon', 'pencil'), ('snap', 'Snap', 'ramune', 'cam'), ('speak', 'Speak', 'sakura', 'mic')]
    out = []
    for k, lab, fam, icn in t:
        c = 'sk-stab' + (' is-on' if k == on else '')
        rd = '<span class="sk-recdot" aria-label="recording"></span>' if (rec and k == 'speak') else ''
        out.append(f'<button type="button" class="{c}" style="{avars(fam)}" role="tab" aria-selected="{"true" if k == on else "false"}" data-sk="stab2" data-tab="{k}">{tb(icn, 20)}{lab}{rd}</button>')
    return f'<div class="sk-stabs" role="tablist">{"".join(out)}</div>'


POPN = [0]


def screen_add():
    groups = []
    kept = 0
    for gi, (aname, fam, n, items) in enumerate(RECIPE):
        rows = []
        for j, it in enumerate(items):
            kept += it[3]
            rows.append(irow(it).replace('<li class="sk-irow', f'<li style="--rd:{.08 * POPN[0]:.2f}s" class="sk-irow sk-rpop', 1))
            POPN[0] += 1
        a_ = next(x for x in AISLES if x['n'] == n)
        k_ = sum(it[3] for it in items)
        meta = f'{k_} of {len(items)} kept'
        groups.append(tray(fam, a_['pat'], n, aname, ''.join(rows), meta=meta, cls='sk-agrp', big=True))
    think = (f'<div class="sk-think" role="status"><svg class="sk-wobble" width="52" height="52" aria-hidden="true" style="--cap:var(--sk-melon-m);--cap-tr:var(--sk-melon-t);--cap-p:var(--sk-melon-p)"><use href="#sticker-i-capsule"/></svg>'
             f'<div><p class="sk-tt">Looking up a recipe…</p><ol>'
             f'<li class="sk-st1">{tb("check", 14)} Searched the web: “pancakes from scratch”</li>'
             f'<li class="sk-st2">{tb("check", 14)} Compared 3 recipes · makes 12</li>'
             f'<li class="sk-st3">{tb("check", 14)} Checked your list + pantry, sorted into 8 aisles</li></ol></div></div>')
    total = sum(len(g[3]) for g in RECIPE)
    recipe = (f'<div class="sk-recipewrap"><div class="sk-recipe"><h4>Pancakes from scratch</h4>'
              f'<p class="sk-from">from the kitchen of {heart_svg(15)} the web</p>'
              f'<p class="sk-rmeta"><span class="sk-chip" style="--cp:var(--sk-ramune-p);min-height:28px;padding:2px 10px">{tb("web", 14)} web search</span><span>3 recipes · {kept} of {total} kept</span></p>'
              f'{"".join(groups)}</div></div>')
    body = (f'<div class="sk-field is-focus" style="margin-top:2px"><span class="sk-flab">Note:</span><span class="sk-fv">ingredients for pancakes from scratch</span>'
            f'<svg class="sk-fheart" viewBox="0 0 24 24" aria-hidden="true"><use href="#sticker-i-heart" width="24" height="24"/></svg></div>'
            f'{think}{recipe}')
    a0 = dict(AISLES[1]); a0['items'] = [(k, n, q, 0 if k in ('croissant',) else c) for k, n, q, c, *_ in AISLES[1]['items']]
    g1012 = [k for k in got_in_walking_order(AISLES) if k not in ('flour', 'sugar', 'butter')]
    behind = (f'{status("10:12")}{appbar(lcdtext=f"{DONE_1012}/{TOTAL - RECIPE_KEPT}", egg=None, pet=True)}{book(AISLES, g1012, total=TOTAL - RECIPE_KEPT, done=(3,), now=None, live=False)}'
              f'<div class="sk-scroll" aria-hidden="true">{fold_page(AISLES[0])}{fold_page(a0)}{fold_page(AISLES[2])}</div>')
    sheet = (f'<div class="sk-sheet" style="top:96px" role="dialog" aria-label="Add to your list"><i class="sk-handle" aria-hidden="true"></i>{stabs("write")}'
             f'<div class="sk-sheetbody">{body}</div>'
             f'<div class="sk-sheetfoot"><button class="sk-btn sk-wide" type="button" data-sk="stick2">{star_svg(18, "#FFF3A6", "#C3264F")}Stick all {kept} on my list</button></div></div>')
    return f'<div class="sk-screen sk-s-add">{behind}<div class="sk-scrim"></div>{sheet}</div>'


# ---------------- screen 3: snap & speak ----------------
def notebook():
    lines = ['milk (bag!)', 'eggs x12', '<s>bread</s> sourdough', 'bananas', 'old cheddar', '<s>chips</s>', 'tomatoes 796', 'dish soap <svg width="12" height="11" viewBox="0 0 24 22" aria-hidden="true"><path d="M12 20C5 15 2 11.4 2 7.6C2 4.4 4.4 2.4 7 2.4C9 2.4 10.6 3.6 12 5.4C13.4 3.6 15 2.4 17 2.4C19.6 2.4 22 4.4 22 7.6C22 11.4 19 15 12 20Z" fill="none" stroke="#2B3A8C" stroke-width="2.2"/></svg>']
    return '<div class="sk-paper">' + '<br>'.join(lines) + '</div>'


def cassette(rec=True):
    reels = ''.join(f'<g class="sk-reel"><circle cx="{cx}" cy="32" r="11" fill="#FF9FC8" stroke="#B02E6B" stroke-width="2"/>'
                    f'<path d="M{cx} 24V40M{cx - 8} 32H{cx + 8}M{cx - 5.6} 26.4L{cx + 5.6} 37.6M{cx + 5.6} 26.4L{cx - 5.6} 37.6" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/>'
                    f'<circle cx="{cx}" cy="32" r="3" fill="#fff" stroke="#B02E6B" stroke-width="1.4"/></g>' for cx in (30, 100))
    return (f'<svg class="sk-cassette" viewBox="-3 -3 136 92" role="img" aria-label="Recording a voice note">'
            f'<rect x="0" y="0" width="130" height="86" rx="12" fill="#6E7BD0" transform="translate(2.4 3.4)"/>'
            f'<rect x="0" y="0" width="130" height="86" rx="12" fill="#FFD6E8" stroke="#fff" stroke-width="4"/>'
            f'<rect x="2" y="2" width="126" height="82" rx="10" fill="none" stroke="#B02E6B" stroke-width="2"/>'
            f'<rect x="14" y="12" width="102" height="40" rx="8" fill="#fff" stroke="#B02E6B" stroke-width="2"/>'
            f'<rect x="38" y="22" width="54" height="20" rx="4" fill="#FFF3A6" stroke="#B02E6B" stroke-width="1.6"/>'
            f'<path d="M50 32H80" stroke="#8A6800" stroke-width="5" stroke-linecap="round" opacity=".55"/>{reels}'
            f'<path d="M30 86L36 68H94L100 86" fill="#FFD6E8" stroke="#B02E6B" stroke-width="2" stroke-linejoin="round"/>'
            f'<circle cx="52" cy="77" r="3" fill="#fff" stroke="#B02E6B" stroke-width="1.4"/><circle cx="78" cy="77" r="3" fill="#fff" stroke="#B02E6B" stroke-width="1.4"/>'
            f'<path d="M10 10Q12 5 18 5" stroke="#fff" stroke-width="3" stroke-linecap="round" fill="none"/></svg>')


def eq(cols=7):
    hs = [2, 4, 6, 3, 5, 2, 4][:cols]
    return '<div class="sk-eqrow" aria-hidden="true">' + ''.join(
        f'<div class="sk-eqbar" style="--d:-{i * .23:.2f}s">' + '<i></i>' * 6 + '</div>' for i, h in enumerate(hs)) + '</div>'


def screen_snap():
    trans = [('milk (bag)', 'done'), ('eggs ×12', 'done'), ('sourdough', 'done'), ('bananas', 'done'), ('old cheddar', 'now'), ('', 'wait')]
    tl = []
    for t, st in trans:
        if st == 'done':
            tl.append(f'<li class="is-done">{tb("check", 14)}{t}</li>')
        elif st == 'now':
            tl.append(f'<li>{tb("sparkle", 14)}{t}<span class="sk-caret"></span></li>')
        else:
            tl.append('<li>&nbsp;</li>')
    found = [('milkbag', 'Milk, 2 %', '4 L bag'), ('eggs', 'Eggs', 'dozen'), ('sourdough', 'Sourdough', '800 g boule'), ('banana', 'Bananas', '6 bananas')]
    frows = ''.join(f'<li class="sk-irow{" is-pop" if i == 3 else ""}"><span class="sk-ico">{ico(k, 36, label=n)}</span><span class="sk-txt"><span class="sk-nm">{n}</span><span class="sk-dt">{q}</span></span></li>' for i, (k, n, q) in enumerate(found))
    pol = (f'<div class="sk-polaroid">{tape("left:-14px;top:-8px;transform:rotate(-38deg)", "sk-t-ging")}{tape("right:-14px;top:-8px;left:auto;transform:rotate(38deg)", "sk-t-dots")}'
           f'<div class="sk-photo" role="img" aria-label="Photo of a handwritten list on lined paper">{notebook()}<i class="sk-shade"></i><i class="sk-scanband"></i><i class="sk-scanline"></i></div>'
           f'<span class="sk-pcap">fridge list · Sat</span></div>')
    snap = (f'<div class="sk-snap">{pol}<div class="sk-trans"><p class="sk-tth">{tb("sparkle", 16)}Reading your list…</p><ol>{"".join(tl)}</ol></div></div>'
            + tray('periwinkle', 'stars', None, 'Popped into stickers', frows, meta='4 of 7', lead=f'<span class="sk-trlead">{tb("sparkle", 14)}</span>').replace('class="sk-trb"', 'class="sk-trb sk-found"', 1))
    speak = (f'<div class="sk-divider">{deco("scissors", 18, "position:static", "")}<span class="sk-small" style="font-weight:700">Anything the paper forgot? Say it.</span></div>'
             f'<div class="sk-speak"><div class="sk-casswrap">{cassette()}<button class="sk-stopbtn" type="button" aria-label="Stop recording" data-sk="stop3">{tb("stop", 16)}</button></div>'
             f'<div class="sk-speakr"><div style="display:flex;align-items:center;gap:8px"><span class="sk-recdot" aria-hidden="true"></span>{lcd("0:12", "Recording, 12 seconds", star=False)}</div>{eq()}</div>'
             f'<p class="sk-voicetx">“…oh and <b>frozen peas</b>, and the big bag of <b>cat food</b> for Mochi<span class="sk-caret"></span>”</p></div>'
             + tray('sakura', 'waves', None, 'Heard', ''.join(f'<li class="sk-irow{" is-pop" if i == 1 else ""}"><span class="sk-ico">{ico(k, 36, label=n)}</span><span class="sk-txt"><span class="sk-nm">{n}</span><span class="sk-dt">{q}</span></span></li>'
                                              for i, (k, n, q) in enumerate([('peas', 'Frozen peas', '750 g bag'), ('kibble', 'Dry cat food', '7 kg bag · pets')])),
                    meta='2 more stickers', lead=f'<span class="sk-trlead">{tb("mic", 14)}</span>').replace('class="sk-trb"', 'class="sk-trb sk-found"', 1))
    foot = (f'<div class="sk-sheetfoot"><button class="sk-btn sk-wide is-disabled" type="button" disabled data-sk="stick3" aria-label="Reading, 6 of 9. Stick all 9 on my list when done">'
            f'<span class="sk-spin" aria-hidden="true">{ico("star4", 18)}</span>Reading… 6 of 9</button></div>')
    behind = f'{status("09:41")}{appbar(lcdtext="0/9", egg=None)}'
    sheet = (f'<div class="sk-sheet" style="top:88px" role="dialog" aria-label="Snap and speak"><i class="sk-handle" aria-hidden="true"></i>{stabs("snap", rec=True)}'
             f'<div class="sk-sheetbody" style="padding-bottom:96px">{snap}{speak}</div>{foot}</div>')
    return f'<div class="sk-screen sk-s-snap">{behind}<div class="sk-scrim"></div>{sheet}</div>'


# ---------------- screen 4: aisles + settings (r9: live editor, own OpenAI key, Surprises switches) ----------------
def sw(on, label, k, extra=''):
    return f'<button class="sk-tog{" is-off" if not on else ""}" type="button" role="switch" aria-checked="{"true" if on else "false"}" aria-label="{label}" data-sk="tog" data-k="{k}"{extra}></button>'


def aedit_panel(name, fam='grape', pat='stars'):
    swc = ''.join(f'<button type="button" class="sk-sw{" is-on" if f == fam else ""}" style="background:var(--sk-{f}-m)" aria-label="{f}" aria-pressed="{"true" if f == fam else "false"}" data-sk="swcol" data-f="{f}"></button>' for f in FAMS)
    swp = ''.join(f'<button type="button" class="sk-sw sk-pt{" is-on" if p == pat else ""}" style="background:#fff;--pat:var(--sk-pat-{p});--pats:{PATTERNS[p][1]}px" aria-label="{p}" aria-pressed="{"true" if p == pat else "false"}" data-sk="swpat" data-p="{p}"></button>' for p in PATS)
    return (f'<div class="sk-aedit"><label class="sk-field is-focus" style="height:30px;padding-bottom:4px"><input class="sk-fin sk-aname" type="text" value="{name}" aria-label="Aisle name" data-sk="aname">'
            f'<svg class="sk-fheart" viewBox="0 0 24 24" aria-hidden="true"><use href="#sticker-i-heart" width="24" height="24"/></svg></label>'
            f'<div class="sk-swatches" role="group" aria-label="Colour">{swc}</div><div class="sk-swatches" role="group" aria-label="Pattern">{swp}</div></div>')


def screen_aisles():
    labels = []
    for a in AISLES:
        nm = a['name'] + (f'<small>store aisle {a["store"]}</small>' if a.get('store') else '')
        c = 'sk-alabel'
        extra = ''
        if a['n'] == 5:
            c += ' is-edit'
            extra = aedit_panel('Breakfast &amp; spreads')
            nm = 'Breakfast<small>store aisle 5 · editing</small>'
        if a['n'] == 6:
            c += ' is-shift'
        if a['n'] == 7:
            c += ' is-drag'
        if a['n'] == 8:
            c += ' is-shift2'
        labels.append(f'<li class="{c}" style="{avars(a["fam"], a["pat"])}" data-fam="{a["fam"]}" data-pat="{a["pat"]}"><span class="sk-drag" role="button" tabindex="0" aria-label="Drag {a["name"]} to reorder" data-sk="adrag">{tb("drag", 18)}</span>'
                      f'<span class="sk-num">{a["n"]}</span><span class="sk-an">{nm}</span><span class="sk-pat" aria-hidden="true"></span>'
                      f'<button class="sk-apen" type="button" aria-label="Edit {a["name"]}" aria-expanded="{"true" if a["n"] == 5 else "false"}" data-sk="aedit">{tb("check" if a["n"] == 5 else "pencil", 18)}</button>{extra}</li>')
    sheet = (f'<div class="sk-asheet"><ol class="sk-alabels">{"".join(labels)}</ol>'
             f'<div class="sk-arow"><button class="sk-addspot" type="button" data-sk="addaisle">{tb("plus", 18)}Add aisle</button>'
             f'<span class="sk-resortw"><button class="sk-btn2" type="button" data-sk="resort" style="height:40px;padding:0 12px;font-size:14.5px">{tb("resort", 18)}Re-sort my list</button>'
             f'<span class="sk-ladyring" aria-hidden="true"><svg width="14" height="14"><use href="#sticker-i-ladybug"/></svg></span></span></div></div>')
    store = (f'<div class="sk-store"><label class="sk-field sk-inline"><span class="sk-flab">Store:</span><input class="sk-fin" type="text" value="Hillcrest Market" aria-label="Store name">'
             f'{tb("pencil", 16)}</label></div>')
    oklcd = (f'<span class="sk-lcd sk-oklcd" role="img" aria-label="Connected"><span class="sk-la">{lcd_svg("OK", p=1.6)}</span><span class="sk-lb">{lcd_svg("OK.", p=1.6)}</span><span class="sk-lc">{lcd_svg("OK..", p=1.6)}</span></span>')
    pill = lambda t, cls='': f'<span class="sk-vpill{cls}">{t}</span>'
    settings = (f'<div class="sk-settings"><h4>{ico("capsule", 24)}AI connection {oklcd}<span style="flex:1"></span>{tb("web", 18)}{sw(True, "Web search for recipes", "web")}</h4>'
                f'<div class="sk-set"><span class="sk-sk">OpenAI key</span><span class="sk-sv">{pill("sk-•••••••••• 3f9a", " sk-keypill")}</span>'
                f'<button class="sk-ibtn" type="button" data-sk="reveal" aria-label="Show key" aria-pressed="false">{tb("eye", 18)}</button><button class="sk-ibtn" type="button" data-sk="paste" aria-label="Paste a key">{tb("key", 18)}</button></div>'
                f'<div class="sk-set"><span class="sk-sk">Smart · sort</span><span class="sk-sv">{pill("gpt-5.6-terra")} {pill("gpt-6-luna")}</span></div>'
                f'<div class="sk-set"><span class="sk-sk">Voice · icons</span><span class="sk-sv">{pill("gpt-transcribe")} {pill("gpt-image-2.5-flare")}</span></div>'
                f'<p class="sk-keynote">Your own key, stored on this phone only. Nothing goes through anyone else’s server.</p></div>')
    surp = [('Easter eggs', 'eggs', True, 'all 18 secrets'), ('Sounds', 'sounds', False, 'thunk, tk, pff, pon!'), ('Haptics', 'haptics', True, 'hanko thunk, peel tick'),
            ('“Next stop” card', 'next', True, 'between aisles'), ('Celebration', 'cele', True, 'when the list is done')]
    surprises = (f'<div class="sk-settings sk-surprises"><h4>{ico("star4", 22)}Surprises</h4>'
                 + ''.join(f'<div class="sk-set"><span class="sk-sk">{t}<small>{d}</small></span><span style="flex:1"></span>{sw(on, t, k)}</div>' for t, k, on, d in surp) +
                 f'<p class="sk-keynote">These switch the live phones on this page too. Reduced motion follows the system setting.</p></div>')
    ab = appbar('My store', 'walking order · colours · AI', rbtn('back', 'Back'), egg=None)
    ab = ab.replace('<header class="sk-appbar">', '<header class="sk-appbar" style="padding-left:6px">')
    ab = ab.replace('<div class="sk-abt">', '<button class="sk-rbtn " type="button" aria-label="Back" data-sk="saved">' + tb('back') + '</button><div class="sk-abt">', 1)
    ab = ab.replace(rbtn('back', 'Back') + '</header>', '<button class="sk-tbtn" type="button" style="margin-right:6px" data-sk="saved">Done</button></header>')
    return (f'<div class="sk-screen sk-s-aisles">{status("10:40")}{ab}<div class="sk-scroll" style="top:88px;padding:0 0 30px" tabindex="0" aria-label="Store settings">'
            f'{store}{sheet}{settings}{surprises}</div></div>')


# ---------------- screen 5: sticker album ----------------
ALBUM = [('apple', 'face', 3), ('maple', 'holo', 3), ('strawberries', '', 1), ('croissant', 'holo', 2),
         ('milkbag', 'face', 2), ('buttertart', '', 3), ('peameal', '', 2), ('curds', '', 2),
         ('sourdough', 'face', 1), ('peachbasket', '', 2), ('eggs', '', 1), ('coffee', '', 1),
         (None, '', 0), ('jam', '', 1), (None, '', 0), (None, '', 0)]


PICNIC_ALB = [('blanket', '', 2), ('basketdeco', 'holo', 3), ('lemonade', '', 1), ('sandwich', '', 1), ('watermelon', '', 2), ('cherries', '', 1),
              ('teacup', '', 2), ('bee', '', 3), (None, '', 0), ('strawberries', '', 1), (None, '', 0), (None, '', 0)]
SEASON_ALB = [('pumpkinpie', '', 2), ('mapleleaf', 'holo', 3), ('apple', '', 1), ('pumpkin', '', 2), (None, '', 0), (None, '', 0), (None, '', 0), (None, '', 0)]


def pockets(items, tab):
    out = []
    for i, (k, v, rar) in enumerate(items):
        if k is None:
            out.append('<li class="sk-pocket is-empty" aria-label="Not collected yet"><span class="sk-q" aria-hidden="true">?</span></li>')
            continue
        key = k + ('-holo' if v == 'holo' and k in ('apple', 'maple', 'croissant') else '-face' if v == 'face' else '')
        lab = (IBY[k].label if k in IBY else {'blanket': 'Picnic blanket', 'basketdeco': 'Picnic basket', 'teacup': 'Teacup', 'bee': 'Bee', 'sandwich': 'Sandwich', 'pumpkin': 'Pumpkin', 'mapleleaf': 'Maple leaf'}.get(k, k)) + (' (holo)' if v == 'holo' else '')
        stars = ''.join(star_svg(9, '#FFDA4A' if j < rar else '#fff', '#8A6800' if j < rar else '#9AA8F5') for j in range(3))
        cls = 'sk-pocket' + (' is-holo' if v == 'holo' else '') + (' is-face' if v == 'face' else '')
        blink = f'<svg class="sk-blinkface" width="56" height="56" aria-hidden="true"><use href="#sticker-i-{k}-blink"/></svg>' if v == 'face' else ''
        out.append(f'<li class="{cls}"><span class="sk-pst">{ico(key, 56, label=lab)}{blink}</span><span class="sk-rar" aria-label="rarity {rar} of 3">{stars}</span></li>')
    return f'<ul class="sk-pockets" data-tab="{tab}"{" hidden" if tab != "groceries" else ""}>{"".join(out)}</ul>'


def screen_album():
    weeks = ''.join(f'<span class="sk-stamp is-done">{ring28()}<svg class="sk-pg" style="--st:var(--sk-{f}-t)" aria-hidden="true"><use href="#sticker-pg-{p}"/></svg></span>'
                    for f, p in [('strawberry', 'strawberry'), ('ramune', 'bee'), ('lemon', 'star'), ('melon', 'heart')])
    season = ''.join(f'<span class="sk-sst">{ico(k, 40, label=n)}<span>{n}</span></span>' for k, n in [('pumpkinpie', 'Pumpkin pie'), ('apple', 'McIntosh'), ('mapleleaf', 'Maple leaf')])
    tabs = ''.join(f'<button type="button" class="sk-stab{" is-on" if k == "groceries" else ""}" style="{avars(f)}" role="tab" aria-selected="{"true" if k == "groceries" else "false"}" data-sk="atab" data-tab="{k}">{t}</button>'
                   for k, t, f in [('groceries', 'Groceries', 'sakura'), ('picnic', 'Picnic', 'lemon'), ('seasonal', 'Seasonal', 'peach')])
    trips = [('Sat 26 Sep', ['banana', 'milkbag', 'eggs', 'sourdough', 'coffee', 'apple'], '19 of 19 stickers · perfect', True),
             ('Sat 19 Sep', ['strawberries', 'croissant', 'butter', 'peas', 'jam', 'lemon'], '21 of 22 stickers', False)]
    tb_ = ''.join(f'<li class="sk-tbk{" is-perfect" if p else ""}"><span class="sk-tbkst">' + ''.join(f'<svg width="20" height="20" aria-hidden="true"><use href="#sticker-i-{k}"/></svg>' for k in ks) +
                  f'</span><b>{d}</b><span>{m}</span></li>' for d, ks, m, p in trips)
    body = (f'<div class="sk-albumtabs" role="tablist">{tabs}</div>'
            f'<div class="sk-albumpage">{pockets(ALBUM, "groceries")}{pockets(PICNIC_ALB, "picnic")}{pockets(SEASON_ALB, "seasonal")}</div>'
            f'<div class="sk-tripbooks"><b class="sk-tbh">Trip sticker books</b><ol data-sk="tripbooks">{tb_}</ol></div>'
            f'<div class="sk-weekcard"><div><b>Perfect weeks</b><span class="sk-small">week 4 of 4 · sakura sheet unlocked</span></div><div class="sk-weeks">{weeks}</div>'
            f'<span class="sk-weekhk">{hanko(-10, 44, ("PER-", "FECT!"), label="Perfect")}</span></div>'
            f'<div class="sk-seasoncard">{tape("left:40%;top:-9px;transform:rotate(-3deg)", "sk-t-pumpkin")}<div class="sk-sshead"><b data-sk="seasonname">Autumn sheet</b><span class="sk-nextchip">next! · Thanksgiving Oct 12</span></div>'
            f'<div class="sk-ssrow">{season}</div></div>')
    ab = appbar('Sticker album', f'{star_svg(13)} 64 of 121 collected · 3 holo', (lcd('64/121', '64 of 121 stickers collected', p=1.8) + rbtn('more', 'More options')), egg=None)
    ab = ab.replace('<button class="sk-rbtn " type="button" aria-label="More options">', '<button class="sk-rbtn " type="button" aria-label="More options" aria-haspopup="menu" aria-expanded="false" data-sk="more">', 1)
    return (f'<div class="sk-screen sk-s-album">{status("11:10")}{ab}<div class="sk-scroll" style="top:88px;padding:6px 10px 30px" tabindex="0" aria-label="Sticker album">{body}</div>'
            + menu([('Back to Saturday shop', 'back', 'list'), ('My store &amp; AI', 'gear', 'store'), ('Surprises', 'sparkle', 'surprises')]) + '</div>')


def phone(screen, n, title, notes, stickers='', pid=''):
    nt = ''.join(f'<div class="sk-note"><span class="sk-nk">{i + 1}</span>{t}</div>' for i, t in enumerate(notes))
    return (f'<div class="sk-pblock"{pid}><div class="sk-pcol"><span class="sk-plabel"><span class="sk-cnum">{n}</span>{title}</span>'
            f'<div class="sk-phone">{stickers}{screen}</div></div><div class="sk-notes">{nt}</div></div>')


def section_b():
    p1 = phone(screen_list(), 1, 'List · Saturday shop', [
        '<b>Walking order.</b> Each aisle has an index tab: number + name + colour + pattern. Only the aisle you are standing in (Baking) stays open, as an aisle tray: its colour band and tab on top, the rows on the lighter panel with 1 dp rules, a patterned end cap below. Every other aisle folds into a white strip with its own basket.',
        '<b>One basket rule.</b> A basket holds only what you have already gotten. Each sticker you check off hops in and sits in it, its bottom tucked behind the front wall, so the basket fills up: bananas, carrots and broccoli in the Produce picnic basket, the croissant in the Bakery bread basket. Nothing gotten yet = an empty basket with a dotted landing spot (Frozen, Household). Items still to get never sit in a basket: the count sticker in its top-right flag slot says how many are left. A finished aisle (Deli) is a full basket with its lid shut and the GOT IT hanko pressed on top.',
        '<b>Trip sticker book.</b> Under the app bar sits today’s book: the stamp card on its left page, one dotted spot per item on the right (the next one is holo). A beat after a sticker lands in its basket, its twin appears in the next spot by magic; nothing flies there. Un-check and it peels back off.',
        '<b>Checked</b> = vermilion hanko + strike-through; 200 ms later the sticker’s corner peels, it hops into its aisle’s basket and squashes in, leaving a dotted outline with a faint ghost. Un-check it and it hops back out onto its line.',
        f'<b>Try it:</b> tap <i>Baking powder</i> (or swipe it right): peel → hop → its twin pops into the book; the bowl’s lid shuts, stamp 4 thunks into the book, the page curls away and “NEXT ✦ Breakfast” holds 2.6 s while Breakfast opens. Swipe a row left to tear it off (Undo). Type in the add bar (“lemons, oat milk”, “pancakes from scratch”, “dragon fruit”) or tap the camera or cassette. Pull the list down to refresh. “Finish the trip” below plays the finale; then hold the LCD to print the receipt.',
        '<b>Hidden in plain sight:</b> the sun by the title ×7 (dusk); tap the book’s stamps 1 1 2 2 3 4 3 4 (bonus stage); type <i>kirakira</i>, <i>apples</i>, <i>birthday cake</i> or <i>watermelon</i>; grab the phone’s frame and wiggle it (petals); hold the LCD (pixel pet); hold a folded basket (peek, then a ladybug); the maple tin is a shiny pull; the pumpkin washi is Thanksgiving; a ladybug walks the Baking end cap.'],
        deco('star4', 30, 'right:-10px;top:66px', 'sk-fstk sk-twinkle'), ' id="sticker-p1"')
    p2 = phone(screen_add(), 2, 'Add · “pancakes from scratch” · 10:12', [
        '<b>Letter-set sheet.</b> Perforated top edge, pink gingham washi as the handle, and index tabs: Write · Snap · Speak.',
        '<b>Thinking</b> is the gashapon capsule wobbling on threes while the steps tick off: web search, compare recipes, check your list and pantry.',
        f'<b>Results</b> arrive on a postage-stamp recipe card grouped by aisle. Keep = a star sticker on the dotted spot; skipped rows stay readable and say why (already on the list, in your pantry).',
        f'<b>Story order:</b> this is 10:12, after the fridge-list snap (09:41) and before the list screen (10:24). The LCD behind the scrim reads {DONE_1012}/{TOTAL - RECIPE_KEPT}; after “Stick all {RECIPE_KEPT}” the list becomes {TOTAL} items. The pixel-bee pet (egg 15) lives in the LCD.'],
        deco('star', 30, 'right:-12px;bottom:150px;transform:rotate(14deg)', 'sk-fstk sk-twinkle'))
    p3 = phone(screen_snap(), 3, 'Snap & speak · 09:41, at home', [
        '<b>Snap:</b> the photo lands as a polaroid under washi tape. A holo glint sweeps it while lines appear in Klee One, then pop into sticker rows.',
        '<b>Crossed-out words</b> (“bread”, “chips”) are dropped; “bread → sourdough” is read as a correction.',
        '<b>Speak runs alongside:</b> the Speak tab shows a blinking REC dot; stop is a round sticker on the cassette itself. Reels turn on eights; the equaliser lights whole round dots, never squashes them.',
        '<b>The primary pill waits:</b> “Reading… 6 of 9” stays disabled until the last paper line is read, then becomes “Stick all 9 on my list”.',
        '<b>Story order:</b> 09:41, at home before leaving: the fridge list starts the trip, so the LCD behind the scrim reads 0/9. The recipe (10:12) and the store (10:24) come after.'],
        deco('ladybug', 30, 'left:-10px;top:120px;transform:rotate(-20deg)', 'sk-fstk'))
    p4 = phone(screen_aisles(), 4, 'My store · aisles & AI', [
        '<b>Sticker sheet editor.</b> Each aisle is a kiss-cut label on backing paper: drag handle (2 × 3 dots), number circle, name, colour + pattern.',
        '<b>Live drag:</b> Frozen is lifted (tilted, deeper shadow) and drifts between Dairy and Household while the neighbours shift 6 dp; only its hard shadow ever meets Dairy’s border, never a name.',
        '<b>Re-sort my list</b> re-files every item with the fast model; a ladybug circles it while it works.',
        '<b>AI connection</b> uses your own OpenAI key, masked (the eye shows it, the key button pastes one); models sit on backing-paper pills. The OK LCD ticks.',
        '<b>Try it:</b> tap a pencil to rename an aisle and pick its colour and pattern; drag the dots to reorder; Add aisle; Re-sort (the ladybug runs the ring). <b>Surprises</b> switches eggs, sounds, haptics, the next-stop card and the celebration for the live phones on this page.'],
        deco('flower', 30, 'right:-12px;bottom:90px', 'sk-fstk sk-sway'))
    p5 = phone(screen_album(), 5, 'Sticker album', [
        '<b>Pinstripe vinyl pockets.</b> Every grocery you have ever checked off is collected here; rarity stars under each pocket; empty pockets wait with a “?”.',
        '<b>Holo pulls</b> (the maple tin from today’s list, croissants) glint in their sleeves. Kawaii-face variants live only here, never in the list; long-press one and it blinks.',
        '<b>Perfect weeks</b>: four stamped weeks give the PERFECT! hanko and unlock a new sheet.',
        '<b>Seasonal sheet:</b> autumn (pumpkin pie, McIntosh, maple leaf) is flagged “next!” because Thanksgiving is Oct 12.',
        '<b>Trip sticker books:</b> every finished trip’s book is filed here (a New trip on phone 1 adds one). <b>Try it:</b> switch the Groceries / Picnic / Seasonal tabs; long-press a face pocket.'],
        deco('star', 28, 'left:-12px;top:150px;transform:rotate(-10deg)', 'sk-fstk sk-twinkle'))
    p1 = p1.replace('</div></div><div class="sk-notes">', '</div>' + democtl() + '</div><div class="sk-notes">', 1)
    p6 = phone(screen_list(dark=True), 6, 'Glow-in-the-dark · List', [
        '<b>Dark theme:</b> navy pinstripe binder #161A3E, pages #222861, white text (13.6:1), secondary #C9CFF5.',
        '<b>Stickers keep their colours</b>; their shadows turn #0B0D24. The hanko switches to #FF8A7A (5.9:1 on the page).',
        '<b>Unchecked spots</b> are hollow #C9CFF5 dashed rings; peeled ghosts get a #3A438A fill and a #C9CFF5 dashed outline.',
        '<b>Glow stars</b> (#C9FFDE, white border) dot the binder gutters only, never a page: two brighter frames every 5 s. The moon replaces the sun by the title.',
        '<b>Same live prototype as phone 1</b>, book included. The snowflake washi on this add bar is the first-snow sheet (egg 11); tap “Make it rain” on egg 16 and the sakura umbrella shelters this bar with raindrop washi and a “bring the tote!” note.'],
        deco('moon', 30, 'right:-12px;top:120px', 'sk-fstk'))
    return (f'<div class="sk-block" id="sticker-screens"><div class="sk-bhead"><span class="sk-btab" style="--tp:var(--sk-ramune-p);--tm:var(--sk-ramune-m);--tt:var(--sk-ramune-t);--pat:var(--sk-pat-polka);--pats:10px"><span class="sk-bnum">B</span><h2>Six screens</h2></span>'
            f'<p class="sk-bsub">360 × 760 dp, drawn at 1 CSS px = 1 dp inside a pastel toy-phone shell. Phones 1 and 6 are working prototypes (check-off, the trip sticker book, add + Snap + Speak, swipe, the finale, the eggs); phones 2–5 work too: keep switches, Stop, the aisle editor, settings, album tabs.</p></div>'
            f'<div class="sk-phones">{p1}{p6}{p2}{p3}{p4}{p5}</div>{showcase(AISLES)}</div>')
