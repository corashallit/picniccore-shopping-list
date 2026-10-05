import copy, json, base64
from common import *
from deco import face

AI3 = json.load(open('ai4/final4.json'))   # r4: gpt-image-2.5-flare (real alpha), same post-processing as v3
LEDGER = [json.loads(l) for l in open('ai4/ledger.jsonl')]
AI3['oatmilk']['qty'] = '1.75 L bottle · dairy'
AI_ORDER = ['dragonfruit', 'bokchoy', 'kimchi', 'pierogi', 'bagel', 'oatmilk']


def ai_symbols():
    """each AI sticker once: vector die-cut + shadow (theme-aware) and the 3x RGBA art"""
    return ''.join(f'<symbol id="sticker-ai-{k}" viewBox="-2 -2 52 52"><path d="{v["cut"]}" transform="translate(1.6 2.2)" fill="var(--sk-shadow,#6E7BD0)"/>'
                   f'<path d="{v["cut"]}" fill="#fff"/><image href="data:image/png;base64,{v["b64"]}" x="-2" y="-2" width="52" height="52"/></symbol>' for k, v in AI3.items())


def airow(icon, name, detail, new=False):
    b = '<svg class="sk-badgeico" aria-hidden="true"><use href="#sticker-i-star"/></svg>' if new else ''
    return (f'<li class="sk-row"><span class="sk-ico">{icon}{b}</span><span class="sk-txt"><span class="sk-nm">{name}</span>'
            f'<span class="sk-dt">{detail}</span></span><span class="sk-cb" role="checkbox" tabindex="0" aria-checked="false" aria-label="{name}">{spot()}</span></li>')


def aitrays():
    """AI stickers at real size, inside their aisle trays next to hand-built SVG neighbours"""
    fz = airow(ai_ico('pierogi', 52, 'Pierogi'), 'Pierogi, potato &amp; cheddar', '900 g bag · new AI icon', True) + airow(ico('peas', 52, label='Frozen peas'), 'Frozen peas', '750 g bag · SVG icon')
    dl = airow(ai_ico('kimchi', 52, 'Kimchi'), 'Kimchi', '500 mL jar · new AI icon', True) + airow(ico('ham', 52, label='Spiral ham'), 'Spiral ham', '1.5 kg · SVG icon')
    return tray('mint', 'check', 7, 'Frozen', fz, meta='2 left', big=True) + tray('lemon', 'stripes', 3, 'Deli &amp; meat', dl, meta='2 left', big=True)


def ai_ico(k, size=52, label=None):
    lab = f' role="img" aria-label="{label}"' if label else ' aria-hidden="true"'
    return f'<svg width="{size}" height="{size}"{lab}><use href="#sticker-ai-{k}"/></svg>'
from variants import HOLO, FACES   # shared with the Android emitter (design/stickers/variants.py)


def mono_symbols(keys):
    return ''.join(f'<symbol id="sticker-m-{k}" viewBox="-2 -2 52 52">{IBY[k].mono()}</symbol>' for k in sorted(keys) if k in IBY)


def extra_symbols():
    """ghost (peeled) outlines, mono variants, holo + face variants, anatomy stages"""
    clips, syms = [], []
    # mono variants are emitted later by build.py, only for the keys the page references
    # holo variants: foil clipped to the dominant shape at 60 %, trace on top
    for k, idx in HOLO.items():
        v = copy.deepcopy(IBY[k]); v.key = k + '-holo'
        body = v.parts[idx]
        holo = P(body.s, body.fam, 'url(#sticker-holo)', ol=2, cut=False, op='.62', tf=body.tf)
        v.parts.insert(idx + 1, holo)
        c, s = symbol(v); clips.append(c); syms.append(s)
    # kawaii variants
    for k, (cx, cy, fam, sp) in FACES.items():
        v = copy.deepcopy(IBY[k]); v.key = k + '-face'
        v.parts += face(cx, cy, fam, sp, .9)
        c, s = symbol(v); clips.append(c); syms.append(s)
        bl = copy.deepcopy(IBY[k]); bl.key = k + '-blink'
        bl.parts += face(cx, cy, fam, sp, .9, blink=True)
        c, s = symbol(bl); clips.append(c); syms.append(s)
    # anatomy stages of strawberries
    base = IBY['strawberries']
    for st in range(1, 7):
        _, s = symbol(base, stage=st, sid=f'sticker-i-anat{st}')  # shapes + clips already defined by the main symbol
        syms.append(s)
    holo_grad = ('<linearGradient id="sticker-holo" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFD6E8"/><stop offset=".25" stop-color="#CCEBFF"/>'
                 '<stop offset=".5" stop-color="#C9F2E3"/><stop offset=".72" stop-color="#FFF3A6"/><stop offset="1" stop-color="#E4D6FF"/></linearGradient>')
    return holo_grad + ''.join(clips), ''.join(syms)


def ic_cell(i):
    k = i.key
    face_u = f'<use href="#sticker-i-{k}-face" x="-2" y="-2" width="52" height="52" class="sk-icface"/>' if k in FACES else ''
    return (f'<figure class="sk-ic" tabindex="0" data-sk="peel"><span class="sk-icw"><svg viewBox="-2 -2 52 52" width="56" height="56" role="img" aria-label="{i.label}, {i.qty}">'
            f'<use href="#sticker-i-{k}" x="-2" y="-2" width="52" height="52" class="sk-icbase"/>{face_u}'
            f'<use href="#{kid(k)}" class="sk-icholo"/></svg></span><figcaption><b>{i.label}</b><br><span>{i.qty}</span></figcaption></figure>')


FAMILY = [('produce', 'Produce'), ('bakery', 'Bakery'), ('dairy', 'Dairy & eggs'), ('meat', 'Meat & deli'), ('deli', 'Meat & deli'), ('seafood', 'Meat & deli'),
          ('baking', 'Baking'), ('breakfast', 'Breakfast'), ('pantry', 'Pantry & canned'), ('snacks', 'Snacks'), ('frozen', 'Frozen'), ('drinks', 'Drinks'),
          ('household', 'Household'), ('pets', 'Pets'), ('baby', 'Baby'), ('pharmacy', 'Pharmacy'), ('unknown', 'Fallback')]


def section_c():
    groups, seen = {}, []
    for i in ICONS:
        fam = dict(FAMILY).get(i.aisle, 'Other')
        if fam not in groups:
            groups[fam] = []; seen.append(fam)
        groups[fam].append(i)
    order = ['Produce', 'Bakery', 'Dairy & eggs', 'Meat & deli', 'Baking', 'Breakfast', 'Pantry & canned', 'Snacks', 'Frozen', 'Drinks', 'Household', 'Pets', 'Baby', 'Pharmacy', 'Fallback']
    cells = ''
    for fam in order:
        if fam not in groups:
            continue
        cells += f'<h4 class="sk-ifam"><span>{fam}</span><small>{len(groups[fam])}</small></h4>' + ''.join(ic_cell(i) for i in groups[fam])
    sheet = (f'<div class="sk-padcard sk-norule sk-iconpad">{tape("left:46%;top:-10px;transform:rotate(-3deg)", "sk-t-mint")}'
             f'<p class="sk-ihelp">Hover or tap a sticker: it lifts, its corner curls off the backing paper, and the apple, milk bag and sourdough show their album face. Long-press for the holo pull.</p>'
             f'<div class="sk-isheet">{cells}</div></div>')
    # ---- gpt-image-2.5-flare live icons (pipeline v4: blank-cell inpainting between three SVG neighbours, real alpha)
    qa = ''
    LAT = {r['key']: r['latency'] for r in LEDGER if r['status'] == 200}
    spend = sum(r['cost'] or 0 for r in LEDGER)
    for k in AI_ORDER:
        it = AI3[k]
        st = it['stats']
        ref = it['neigh'][0]
        nb = sum(st['neighbour_u2']) / 3
        qa += (f'<div class="sk-qa"><div class="sk-qapair"><span class="sk-qab">{ico(ref, 52, label=IBY[ref].label + " (SVG)")}{ai_ico(k, 52, it["name"] + ", AI sticker")}</span>'
               f'<span class="sk-qab is-navy">{ico(ref, 52)}{ai_ico(k, 52)}</span></div>'
               f'<div class="sk-qatx"><b>{it["name"]}</b><span class="sk-small">{it["qty"]} · next to {", ".join(IBY[n].label.split(",")[0].lower() for n in it["neigh"])}</span>'
               f'<dl><dt>outline</dt><dd>{st["outline_raw"]:.1f}u raw → <b>{st["outline_u"]:.1f}u</b></dd><dt>area</dt><dd>{st["area_u2"]:.0f} u² <span class="sk-small">(neighbours {nb:.0f})</span></dd>'
               f'<dt>palette ΔE</dt><dd>{st["dE"]:.1f}</dd><dt>gloss</dt><dd>2.6u × {st["gloss"]["len"]:.0f}u, procedural</dd></dl>'
               f'<span class="sk-qachips"><span class="sk-score">1 flare call · {LAT[k]:.0f} s</span><span class="sk-norm">model gloss replaced</span></span></div></div>')
    core = ('This is a 2 × 2 sticker sheet on a fully transparent background. Three cells already hold finished sticker artworks; the bottom-right cell is empty. Draw ONE new artwork in the empty bottom-right cell only: '
            '<mark>{subject}</mark>, grouped into one compact pile no wider than 1.3 times its height. Match the three neighbours exactly as if drawn by the same hand: the same size in the cell, '
            'the base at the same height, the same outline thickness in pixels, the same interior line thickness, and the same white gloss … No specks smaller than the outline thickness, '
            'at most three interior marks. Colours only: <mark>{pastel} {mid} {trace} {accent trio}</mark> #FFFFFF. Bottom quarter is a flat <mark>{mid}</mark> band following the curve. '
            'No face, text, border, sticker outline or shadow. Keep everything around the new artwork fully transparent. Do not change the other three cells.')
    ai = (f'<div class="sk-card sk-aicard">{tape("right:30px;top:-10px;transform:rotate(5deg)", "sk-t-dots")}'
          f'<h3 class="sk-h3">{ico("capsule", 34)}Rendered with gpt-image-2.5-flare <span class="sk-chip" style="--cp:var(--sk-lemon-p)">live-icon pipeline · v4</span></h3>'
          f'<ol class="sk-steps">'
          f'<li><b>Inpaint a blank cell.</b> A 1024² sheet on a transparent background: the three closest SVG stickers (art only, 512 px per cell) and an empty fourth cell. The mask opens only that cell, so the model copies its neighbours’ line weight and gloss instead of guessing. One gpt-image-2.5-flare edit call (the production live-icon model), background transparent, quality medium: 11–13 s and about US$0.023 per icon here (US$0.015 at quality low in the app).</li>'
          f'<li><b>Cut out and snap.</b> Flare returns real alpha, so the art is cut at 50 % alpha with no colour key (enclosed holes stay see-through, so a bagel keeps its hole); every pixel snaps to the item’s pastel / mid / deep / trace tokens in Lab.</li>'
          f'<li><b>Clean.</b> Specks under 2u go; thin white strokes (the model’s own gloss) take their surrounding colour; thick trace blobs become the deep band.</li>'
          f'<li><b>Normalise.</b> The trace is grown to 2u; size is matched to the neighbours’ silhouette area (capped at a 39u box), base on y 42.</li>'
          f'<li><b>Gloss and cut in code.</b> The same candy-gloss generator as the SVG set; the die-cut and the hard shadow are a vector path that follows the theme. Art ships as a 156 px (3×) PNG flattened onto its white die-cut, so edges stay anti-aliased: 256 colours, 2.5–3.6 KB each.</li></ol>'
          f'<details class="sk-prompt" open><summary>Prompt core (v4)</summary><p>{core}</p><p class="sk-small">Sent to <code>images/edits</code> with <code>model=gpt-image-2.5-flare</code>, <code>background=transparent</code>, <code>quality=medium</code>, <code>image[]=sheet.png</code> and <code>mask=mask.png</code>; only the empty cell is transparent in the mask. All six stickers: {len(LEDGER)} calls, US${spend:.2f}.</p></details>'
          f'<h4 class="sk-h4">QA panel · SVG neighbour vs AI sticker at 52 px, on the binder and on navy</h4><div class="sk-qagrid">{qa}</div>'
          f'<p style="font:700 15px/1.3 var(--sk-f-body);margin:16px 0 8px">At real size, inside their aisle trays</p>'
          f'<div class="sk-aitrays">{aitrays()}</div></div>')
    # ---- variants
    tbset = ''.join(f'<span class="sk-kl" style="color:var(--sk-bp)">{tb(k, 24)}</span>' for k in ['search', 'sort', 'more', 'back', 'close', 'plus', 'pencil', 'gear', 'cam', 'mic', 'undo', 'resort', 'drag', 'eye', 'link', 'web'])
    mono = ''.join(f'<svg width="44" height="44" role="img" aria-label="{k} monochrome"><use href="#sticker-m-{k}"/></svg>' for k in ['strawberries', 'milkbag', 'maple', 'apple', 'eggs', 'loaf', 'butter', 'tomatocan', 'carrots', 'chickentray', 'toiletpaper', 'capsule'])
    caps = ''.join(f'<span class="sk-kl" style="--cap:var(--sk-{f}-m);--cap-tr:var(--sk-{f}-t);--cap-p:var(--sk-{f}-p)">{ico("capsule", 52)}{f} aisle</span>' for f in ['ramune', 'peach', 'melon', 'sakura'])
    flags = (f'<span class="sk-kl"><span style="position:relative;display:inline-block">{ico("baguette", 52)}<svg aria-hidden="true" style="position:absolute;right:-4px;top:-4px;width:20px;height:20px"><use href="#sticker-i-star"/></svg></span>new / AI-suggested</span>'
             f'<span class="sk-kl"><span style="position:relative;display:inline-block">{ico("coffee", 52)}<svg aria-hidden="true" style="position:absolute;right:-4px;top:-4px;width:20px;height:20px"><use href="#sticker-i-heart"/></svg></span>favourite</span>'
             + ''.join(f'<span class="sk-kl"><span class="sk-ico is-shiny" style="position:relative;display:inline-block;width:52px;height:52px">{ico(k + "-holo", 52, "Holo " + IBY[k].label, extra=glint_svg(k))}</span>holo · {IBY[k].label.split(",")[0].lower()}</span>' for k in HOLO) +
             f'<span class="sk-kl">{ghost("apple")}peeled (checked)</span>')
    faces = ''.join(f'<span class="sk-kl">{ico(k + "-face", 56)}{t}</span>' for k, t in [('apple', 'album'), ('milkbag', 'achievement'), ('sourdough', 'empty state')])
    anat_lab = ['cut-shadow', '+ cut-white (4u)', '+ flat fills', '+ puffy band, texture', '+ colour-trace lines', '+ candy gloss']
    anat = ''.join(f'<figure><span>{ico(f"anat{i + 1}", 84)}</span><figcaption><b>{i + 1}</b>{t}</figcaption></figure>' for i, t in enumerate(anat_lab))
    variants = (f'<div class="sk-card" style="padding:18px 20px 20px"><h3 class="sk-h3">Build order &amp; variants</h3>'
                f'<div class="sk-anat">{anat}</div>'
                f'<div class="sk-variants" style="margin-top:18px"><div class="sk-vgrp"><h4>Flags, holo pulls &amp; states</h4><div>{flags}</div></div>'
                f'<div class="sk-vgrp"><h4>Unknown item: gashapon capsule in the aisle’s colour, lemon star sticker on top</h4><div>{caps}</div></div>'
                f'<div class="sk-vgrp"><h4>Kawaii-face variants · album, achievements, empty states only</h4><div>{faces}</div></div>'
                f'<div class="sk-vgrp"><h4>Monochrome (themed icon, widgets, peeled ghosts)</h4><div>{mono}</div></div>'
                f'<div class="sk-vgrp"><h4>24 dp toolbar set · ballpoint 2u line, no border, no gloss</h4><div>{tbset}</div></div></div></div>')
    head = (f'<div class="sk-bhead"><span class="sk-btab" style="--tp:var(--sk-lemon-p);--tm:var(--sk-lemon-m);--tt:var(--sk-lemon-t);--pat:var(--sk-pat-stripes);--pats:10px"><span class="sk-bnum">C</span><h2>Sticker sheet</h2></span>'
            f'<p class="sk-bsub">{len(ICONS)} hand-built SVG stickers in 15 families (48u art in a 52u die-cut box, shown at 56 px), including the Ontario set: 4 L milk bag, 1 L and 3 L baskets, maple tin, McIntosh, butter tarts, peameal bacon and cheese curds. '
            f'Same rules everywhere: front view, chibi squash, colour-trace 2u outlines, max two sticker colours, puffy bottom band, one candy gloss (generated: 2.6u along the upper-left of the outline inset 3u, plus a dot), 4u white border, hard periwinkle shadow.</p>'
            f'<span class="sk-doodle">{heart_svg(14)} peel me {star_svg(14)}</span></div>')
    return f'<div class="sk-block" id="sticker-icons">{head}<div class="sk-iconwrap">{sheet}{ai}{variants}</div></div>'


# per basket: the aisle's stickers in check-off order, and how many of them are already gotten (the middle card)
BFILL = {'picnic': (['banana', 'carrots', 'broccoli', 'strawberries', 'apple'], 3), 'bread': (['croissant', 'sourdough', 'baguette'], 1),
         'bento': (['ham', 'peameal', 'curds'], 2), 'bowl': (['flour', 'sugar', 'bakingpowder'], 2), 'crate': (['coffee', 'jam', 'maple'], 1),
         'eggbasket': (['milkbag', 'butter', 'eggs'], 2), 'cooler': (['peas', 'icecream'], 1), 'tote': (['dishsoap', 'toothpaste', 'toiletpaper', 'kibble'], 2),
         'wire': (['chips', 'pasta', 'capsule'], 2), 'punnet': (['blueberries', 'strawberries', 'peachbasket'], 1),
         'milkcrate': (['milkbag', 'yogurt', 'cheese'], 2), 'icebucket': (['sparkling', 'lemonade', 'water'], 1), 'flowerbucket': (['flower', 'cherries', 'lemon'], 1),
         'bottlecarrier': (['lemonade', 'sparkling', 'orangejuice'], 2), 'candyjar': (['chocolate', 'cookies', 'popcorn'], 1), 'jarshelf': (['jam', 'honey', 'peanutbutter'], 2),
         'vanity': (['shampoo', 'soap', 'sunscreen'], 1)}
BNOTE = {'picnic': 'wicker weave, gingham flap; two lids flip shut', 'bread': 'shallow weave; one gingham napkin draped over the back rim, a corner hanging over the front', 'bento': 'lacquer box; the lid stands hinged open behind it, shuts with a lemon ribbon bow',
         'bowl': 'polka-dot mixing bowl, whisk; a striped tea towel when done', 'crate': 'two planks, corner posts; a plank lid', 'eggbasket': 'wire lattice with a straw nest; dotted cloth cover',
         'cooler': 'white lid hinged open behind it, snowflake, side handles', 'tote': 'canvas, heart patch; the flap snaps shut', 'wire': 'catch-all for unsorted items; two flaps', 'punnet': 'farmers’-market specials; clear clamshell lid',
         'milkcrate': 'plastic crate with carry slots; a gingham cloth when done', 'icebucket': 'decorated: its three ice cubes stay in it even when nothing is gotten; side handles, a domed lid',
         'flowerbucket': 'decorated: tulips and a daisy always stand in it; done = kraft-paper wrap and a ribbon', 'bottlecarrier': 'decorated: two bottle necks and the carry board stay put; a gingham cover when done',
         'candyjar': 'decorated: a layer of sweets always sits at the bottom of the glass; a domed lid when done', 'jarshelf': 'little pantry shelf with posts; a gingham curtain when done',
         'vanity': 'mirror lid stands open behind it until done, lemon clasp'}


def section_c2():
    cards = []
    for b in BASKETS:
        items, n = BFILL[b.key]
        cards.append(f'<div class="sk-bcard"><div class="sk-bpair"><div>{basket_svg(b, [], "open", 80, 64)}nothing yet</div>'
                     f'<div>{basket_svg(b, items[:n], "open", 80, 64)}{n} in · {len(items) - n} to get</div>'
                     f'<div>{basket_svg(b, items, "done", 80, 64)}aisle done</div></div>'
                     f'<h4>{b.label} <span class="sk-small" style="font-weight:500">· {b.aisle}</span></h4><p>{BNOTE[b.key]}</p></div>')
    pic = BFILL['picnic'][0]
    rule = (f'<div class="sk-brule"><div>{basket_svg(BBY["picnic"], [], "open", 120, 96)}<p><b>Nothing gotten yet:</b> the basket waits empty, still plainly its own kind, with a dotted landing spot in its mouth. The count sticker beside it says how many are still to get.</p></div>'
            f'<div>{basket_svg(BBY["picnic"], pic[:3], "open", 120, 96)}<p><b>Gotten:</b> each checked sticker hops in and sits in the basket, its bottom tucked behind the front wall, so the basket fills up (here 3 of 5). Items still to get never sit in or stand out of a basket.</p></div>'
            f'<div>{basket_svg(BBY["picnic"], pic, "done", 120, 96)}<p><b>Aisle done:</b> the basket is full, the lid shuts and the GOT IT hanko is pressed onto the lid, never onto the tab.</p></div></div>')
    head = (f'<div class="sk-bhead"><span class="sk-btab" style="--tp:var(--sk-melon-p);--tm:var(--sk-melon-m);--tt:var(--sk-melon-t);--pat:var(--sk-pat-hearts);--pats:11px"><span class="sk-bnum">D</span><h2>Basket family</h2></span>'
            f'<p class="sk-bsub">One container per aisle, matched to what lives there, built in two layers (back wall, front wall) so stickers tuck in between. All 16 kinds the app ships (plus the mixing bowl). Decorated kinds (ice bucket, flower bucket, bottle carrier, candy jar) keep their built-in decoration when empty: maximalism is cute. One rule for what a basket shows, everywhere:</p></div>')
    return f'<div class="sk-block" id="sticker-baskets">{head}{rule}<div class="sk-bfam">{"".join(cards)}</div></div>'
