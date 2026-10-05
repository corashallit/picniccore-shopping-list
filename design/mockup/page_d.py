import copy
from common import *
from page_b import AISLES, row, tab, fold_page, maru_svg
from icons import calyx
from launcher import hamper as launcher_hamper, star4holo as launcher_star4holo


def launcher_symbols():
    clips, syms = [], []
    hamper = launcher_hamper()   # the art lives with the DSL (design/stickers/launcher.py)
    c, s = symbol(hamper); clips.append(c); syms.append(s)
    syms.append(f'<symbol id="sticker-m-launch" viewBox="-2 -2 52 52">{hamper.mono()}</symbol>')
    st = launcher_star4holo()
    c, s = symbol(st); clips.append(c); syms.append(s)
    # 108 dp canvas, safe zone = 66 dp circle centred at 54,54 (r 33): hamper 56 dp box + 20 dp star both inside it
    art = ('<symbol id="sticker-launchart" viewBox="0 0 108 108"><rect width="108" height="108" fill="#E6F6EE"/>'
           + ''.join(f'<rect x="{x}" width="2" height="108" fill="#CDEEDD"/>' for x in range(0, 108, 10)) +
           '<use href="#sticker-i-launch" x="25" y="27" width="56" height="56"/><use href="#sticker-i-star4holo" x="64" y="27" width="19" height="19"/></symbol>')
    syms.append(art)
    return ''.join(clips), ''.join(syms)


def section_d():
    a = AISLES
    # buttons
    btns = (f'<div class="sk-krow"><span class="sk-kl"><button class="sk-btn" type="button">{star_svg(18, "#FFF3A6", "#C3264F")}Stick all 5 on my list</button>primary</span>'
            f'<span class="sk-kl"><button class="sk-btn is-pressed" type="button">Stick all 5</button>pressed: shadow gone, +1.6 / +2.2</span></div>'
            f'<div class="sk-krow"><span class="sk-kl"><button class="sk-btn" type="button">Start my list</button>gloss: one crescent inside the left cap + a dot, clear of the label</span>'
            f'<span class="sk-kl"><button class="sk-btn2" type="button">{tb("resort", 18)}Re-sort</button>secondary</span></div>'
            f'<div class="sk-krow"><span class="sk-kl"><button class="sk-tbtn" type="button">Skip all</button>text button</span>'
            f'<span class="sk-kl"><button class="sk-btn is-disabled" type="button" disabled>Reading… 6 of 9</button>disabled</span>'
            f'<span class="sk-kl"><button class="sk-fab sk-bob" type="button" data-sk="startlist" aria-label="Add (opens phone 1’s add bar)">{fab_svg()}</button>FAB 64 dp · live on phone 1 when you scroll down</span></div>'
            f'<div class="sk-krow">' + ''.join(f'<span class="sk-kl">{ico(k, 52)}{t}</span>' for k, t in [('camera', 'snap'), ('cassette', 'speak'), ('envelope', 'send')]) +
            f'<span class="sk-kl"><span class="sk-rbtn" style="display:grid">{tb("more")}</span>40 dp round</span></div>')
    # chips
    chips = (f'<div class="sk-krow">' + ''.join(f'<button type="button" class="sk-chip sk-kchip" aria-pressed="false" data-sk="kchip" style="--cp:var(--sk-{x["fam"]}-p);--cm:var(--sk-{x["fam"]}-m);--ct:var(--sk-{x["fam"]}-t)"><span class="sk-cnum">{x["n"]}</span>{x["name"]}</button>' for x in a[:6]) + '</div>'
             f'<div class="sk-krow"><button type="button" class="sk-chip sk-kchip is-on" aria-pressed="true" data-sk="kchip" style="--cp:var(--sk-sakura-p);--cm:var(--sk-sakura-m)">{star_svg(13)}Favourites</button><button type="button" class="sk-chip sk-kchip" aria-pressed="false" data-sk="kchip" style="--cp:var(--sk-sakura-p);--cm:var(--sk-sakura-m)">{star_svg(13)}On sale</button>'
             f'<button type="button" class="sk-chip sk-kchip is-on" aria-pressed="true" data-sk="kchip" style="--cp:var(--sk-ramune-p);--cm:var(--sk-ramune-m)">{star_svg(13)}This week</button><button type="button" class="sk-chip sk-kchip" aria-pressed="false" data-sk="kchip" style="--cp:var(--sk-ramune-p);--cm:var(--sk-ramune-m)">{star_svg(13)}Pantry</button></div>'
             f'<p class="sk-kcap">Kiss-cut labels: 32 dp tall inside a 48 dp target, pastel fill, 3 dp white border, hard shadow. Selected = mid fill + a leading puffy star. Tap them.</p>')
    # checkbox states
    cbrows = (f'{row(("jam", "Strawberry jam", "500 mL jar · unchecked", 0), 0)}'
              f'{row(("tea", "Orange pekoe tea", "72 bags · pressed", 0, None, "pressed"), 1)}'
              f'{row(("coffee", "Coffee beans", "340 g bag · checked", 1), 2)}')
    cb = (tray('grape', 'stars', 5, 'Breakfast', cbrows, meta='2 left of 3', big=True) +
          f'<div class="sk-krow" style="margin-top:12px"><span class="sk-kl">{spot()}place-sticker spot</span><span class="sk-kl"><span class="sk-keep" style="display:grid">{spot()}<svg class="sk-ks" aria-hidden="true"><use href="#sticker-i-star"/></svg></span>kept</span>'
          + ''.join(f'<span class="sk-kl">{hanko(r, 44)}{r}°</span>' for r in (-16, -12, -8)) + '</div>')
    # aisle ornaments
    tabs = ''.join(f'<span class="sk-tab" style="{avars(f, p)};height:36px"><span class="sk-num">{i + 1}</span><span class="sk-tabname" style="font-size:17px">{f}</span></span>' for i, (f, p) in enumerate(zip(FAMS, PATS)))
    stampcard = ''.join(f'<span class="sk-stamp">{ring28()}<svg class="sk-pg" style="--st:var(--sk-{f}-t)" aria-hidden="true"><use href="#sticker-pg-{pg}"/></svg></span>' for f, pg in zip(FAMS, PICS))
    orn = (f'<div class="sk-krow">{tabs}</div>'
           f'<p class="sk-kcap">10 colour + pattern pairs in aisle order. The pattern prints at 35–50 % in the tab’s right end only, never under the name.</p>'
           f'<div class="sk-krow" style="margin-top:10px"><div class="sk-stampstrip" style="margin:0;flex:1 1 300px;max-width:400px">{stampcard}</div></div>'
           f'<p class="sk-kcap">Stamp-card pictograms: strawberry, bee, star, heart, cloud, flower, ladybug, teacup, sandwich, sun, each inked in its aisle’s trace colour.</p>'
           f'<div class="sk-krow" style="margin-top:10px"><div class="sk-stampstrip sk-holo-card" style="margin:0;flex:1 1 300px;max-width:400px;background:var(--sk-holo)">{stampcard}'
           f'<span style="position:absolute;right:-8px;top:-16px">{hanko(-12, 54, ("PER-", "FECT!"), label="Perfect")}</span></div></div>'
           f'<p class="sk-kcap">All aisles done: the card flips to holo foil and gets a “PERFECT!” hanko.</p>')
    # dividers
    div = (f'<div class="sk-divider">{deco("scissors", 18, "position:static", "")}</div>'
           f'<p class="sk-kcap" style="margin:0 0 10px">Major: perforation (2 dp dots, 2 on / 4 off) with 16 dp scissors.</p>'
           f'<div style="height:1px;background:var(--sk-rule);margin:6px 0"></div><p class="sk-kcap" style="margin:0 0 12px">Minor: the 1 dp rule line.</p>'
           f'<div class="sk-krow" style="gap:18px">'
           f'<span class="sk-kl"><i class="sk-tape" style="position:relative;display:block;transform:rotate(-4deg)"></i>sakura stripe</span>'
           f'<span class="sk-kl"><i class="sk-tape sk-t-ging" style="position:relative;display:block;transform:rotate(3deg)"></i>pink gingham</span>'
           f'<span class="sk-kl"><i class="sk-tape sk-t-dots" style="position:relative;display:block;transform:rotate(-3deg)"></i>lemon dots</span></div>'
           f'<p class="sk-kcap" style="margin:12px 0 8px">Seasonal sheets (Ontario dates) swap the add-bar tape:</p><div class="sk-krow" style="gap:16px 18px">'
           + ''.join(f'<span class="sk-kl"><i class="sk-tape sk-t-{k}" style="position:relative;display:block;transform:rotate({r}deg)"></i>{t}</span>' for k, t, r in
                     [('maple', 'Canada Day · Jul 1', -3), ('pumpkin', 'Thanksgiving · Oct 12', 3), ('halloween', 'Halloween · Oct 31', -2), ('snow', 'first snow', 2), ('hearts', 'Valentine’s · Feb 14', -4), ('redenv', 'Lunar New Year · Feb 17', 3)]) + '</div>'
           f'<div class="sk-recipewrap" style="margin-top:14px"><div class="sk-recipe" style="margin:0;padding:12px 16px"><b style="font:700 15px/1.2 var(--sk-f-puff)">Postage-stamp edge</b><br><span class="sk-small">6 dp semicircle bites every 10 dp on all four sides, dashed inner frame</span></div></div>')
    # fields
    fields = (f'<div style="display:flex;flex-direction:column;gap:16px">'
              f'<label class="sk-field sk-kfield"><span class="sk-flab">Note:</span><input class="sk-fin" type="text" placeholder="Write a note…" aria-label="Note"><svg class="sk-fheart" viewBox="0 0 24 24" aria-hidden="true"><use href="#sticker-i-heart" width="24" height="24"/></svg></label>'
              f'<label class="sk-field sk-kfield is-focus sk-keepfocus"><span class="sk-flab">Note (focused):</span><input class="sk-fin" type="text" value="pancakes from scratch" aria-label="Note, focused example"><svg class="sk-fheart" viewBox="0 0 24 24" aria-hidden="true"><use href="#sticker-i-heart" width="24" height="24"/></svg></label>'
              f'<div><label class="sk-field sk-kfield is-error"><span class="sk-flab">OpenAI key:</span><input class="sk-fin" type="text" value="sk-proj-…3f9" aria-label="OpenAI key" data-sk="keyfield"></label>'
              f'<p class="sk-ferr" data-sk="keyerr">{torn_svg()}<span>That key didn’t work. Check it and paste it again?</span></p></div>'
              f'<label class="sk-field" style="height:auto;padding-top:4px"><span class="sk-flab">Aisles as text:</span><textarea class="sk-ktext" rows="4" aria-label="Aisles as text">Produce\nBakery\nDeli &amp; meat\nBaking · aisle 2</textarea></label></div>'
              f'<p class="sk-kcap">Type in them: focus draws the 3 dp strawberry underline and the puffy heart; the key field shows the error state (hanko underline, torn-paper icon) until it looks like a key.</p>')
    toast = (f'<div style="padding:12px 0 6px 6px"><div class="sk-toast" role="status">{tape("", "sk-t-ging")}<span>Added 5 items to Saturday shop {heart_svg(14)}</span>'
             f'<span class="sk-tact"><span class="sk-small" style="font-weight:700">4 s</span><span class="sk-chip" style="--cp:#fff;min-height:30px">{tb("undo", 16)}Undo</span></span></div></div>'
             f'<p class="sk-kcap">Sticky note, 220 dp, −2°, washi on top. Bottom-left above the add bar for 4 s.</p>')
    empty_art = ('<svg viewBox="0 0 260 150" width="260" height="150" role="img" aria-label="A fresh sticker sheet with five unpeeled stickers and an envelope" style="max-width:100%">'
                 '<rect x="10" y="12" width="170" height="128" rx="10" fill="#fff" transform="translate(2.4 3.4)" opacity="0"/>'
                 '<rect x="12" y="10" width="168" height="130" rx="10" fill="#6E7BD0" transform="translate(2.4 3.4)"/><rect x="12" y="10" width="168" height="130" rx="10" fill="#F7F8FF" stroke="#fff" stroke-width="3"/>'
                 '<rect x="20" y="18" width="152" height="114" rx="6" fill="none" stroke="#9AA8F5" stroke-width="1.4" stroke-dasharray="3 3"/>'
                 '<use href="#sticker-i-blanket" x="22" y="20" width="56" height="56"/><use href="#sticker-i-basketdeco" x="74" y="18" width="58" height="58"/>'
                 '<use href="#sticker-i-strawberries" x="128" y="22" width="48" height="48"/><use href="#sticker-i-bee" x="34" y="74" width="54" height="54"/>'
                 '<use href="#sticker-i-lemonade" x="94" y="72" width="56" height="56"/>'
                 '<g transform="translate(176 66) rotate(8)"><rect width="70" height="50" rx="6" fill="#6E7BD0" transform="translate(2.4 3.4)"/><rect width="70" height="50" rx="6" fill="#fff" stroke="#fff" stroke-width="3"/>'
                 '<rect width="70" height="50" rx="6" fill="#fff" stroke="#C3264F" stroke-width="2"/><path d="M3 4L35 28L67 4" fill="none" stroke="#C3264F" stroke-width="2" stroke-linejoin="round"/>'
                 f'<path d="{heart_path(35, 28, 7)}" fill="#FF6F8E" stroke="#C3264F" stroke-width="2"/></g></svg>')
    empty = (f'<div class="sk-empty">{empty_art}<h5>A brand-new sticker sheet!</h5><p>Nothing on Saturday shop yet. Type it, snap your paper list, or just say it out loud.</p>'
             f'<span class="sk-maru" style="display:inline-flex;gap:5px;align-items:center;font-size:15px">{heart_svg(14)} let’s go shopping {heart_svg(14)}</span>'
             f'<button class="sk-btn" type="button" data-sk="startlist">Start my list</button></div>')
    loading = (f'<div class="sk-krow" style="justify-content:space-around">'
               f'<span class="sk-kl"><svg class="sk-wobble" width="64" height="64" aria-hidden="true"><use href="#sticker-i-capsule"/></svg>gashapon wobble</span>'
               f'<span class="sk-kl"><span style="position:relative;width:70px;height:64px;display:block">{deco("star4", 26, "left:2px;top:4px", "sk-stk sk-twinkle")}{deco("star", 22, "left:40px;top:0", "sk-stk sk-twinkle sk-d2")}{deco("star4", 20, "left:30px;top:38px", "sk-stk sk-twinkle sk-d3")}</span>sparkle twinkle</span>'
               f'<span class="sk-kl"><span style="position:relative;width:76px;height:64px;display:block"><span class="sk-beeloop">{deco("bee", 30, "position:static", "")}</span></span>bee loop</span></div>'
               f'<div class="sk-krow" style="margin-top:14px">{lcd("12/18", "12 of 18")}{lcd("0:12", "12 seconds", star=False)}{lcd("REC", "recording", star=False)}{lcd("PERFECT!", "perfect", star=False)}</div>')
    launch = (f'<div class="sk-launch">'
              f'<figure><span class="sk-lm" style="border-radius:8px;width:108px;height:108px;position:relative"><svg viewBox="0 0 108 108"><use href="#sticker-launchart"/></svg>'
              f'<i style="position:absolute;inset:21px;border:1.5px dashed rgba(200,50,31,.7);border-radius:50%"></i></span>108 dp · 66 dp safe zone</figure>'
              f'<figure><span class="sk-lm" style="border-radius:50%"><svg viewBox="9 9 90 90"><use href="#sticker-launchart"/></svg></span>circle</figure>'
              f'<figure><span class="sk-lm sk-sq"><svg viewBox="9 9 90 90"><use href="#sticker-launchart"/></svg></span>squircle</figure>'
              f'<figure><span class="sk-lm" style="border-radius:50% 50% 50% 14%"><svg viewBox="9 9 90 90"><use href="#sticker-launchart"/></svg></span>teardrop</figure>'
              f'<figure><span class="sk-lm" style="border-radius:50%;background:#DCE1FF;display:grid;place-items:center"><svg viewBox="0 0 52 52" style="width:62px;height:62px"><use href="#sticker-m-launch"/></svg></span>monochrome</figure></div>'
              f'<p class="sk-kcap">Adaptive icon: mint pinstripe background; one puffy melon wicker hamper (handle arch, weave band, three strawberries) with a holo 4-point star, both inside the 66 dp safe circle. The squircle mask draws its shadow inside the clip. No letters.</p>')
    nxt = (f'<div style="display:flex;gap:16px;flex-wrap:wrap;align-items:center"><div class="sk-next" role="img" aria-label="Next stop: aisle 5, Breakfast">'
           f'{deco("star4", 26, "left:20px;top:22px", "sk-stk sk-twinkle")}{deco("star", 22, "right:24px;top:40px", "sk-stk sk-twinkle sk-d2")}{deco("heart", 22, "left:30px;bottom:30px", "sk-stk sk-twinkle sk-d3")}'
           f'<span class="sk-nx">NEXT {star_svg(18)}</span>{basket_svg(BBY["crate"], ["coffee"], "open", 120, 96)}'
           f'<span class="sk-tab" style="{avars("grape", "stars")}"><span class="sk-num">5</span><span class="sk-tabname">Breakfast</span></span></div>'
           f'<p class="sk-small" style="flex:1 1 160px">“Next stop” prize card, r9: only “NEXT ✦” and the next aisle’s tab (no duplicate pill), held still for at least 2.5 s (1.2 s + 0.3 s per word), tap to skip, off switch under Surprises. The finished page curls away from its corner (4 frames, 300 ms) before it folds. Live on phone 1.</p></div>')
    gstars = ''.join(f'<svg class="sk-glowstar" style="{pos};animation-delay:-{dl}s" width="{sz}" height="{sz}" aria-hidden="true"><use href="#sticker-i-glowstar"/></svg>'
                     for pos, sz, dl in [('right:16px;top:10px', 18, 0), ('right:46px;top:24px', 12, 2), ('left:3px;top:64px', 9, 3.4), ('right:3px;top:118px', 9, 1)])
    grows = row(("icecream", "Ice cream", "1.5 L tub · frozen", 0), 0) + row(("peas", "Frozen peas", "750 g bag · frozen", 1), 1)
    glow = (f'<div class="sk-glow sk-dark">{gstars}'
            f'<p style="font:700 18px/1.2 var(--sk-f-puff);color:#fff;margin:0 0 2px 4px;-webkit-text-stroke:5px #222861;paint-order:stroke fill">Glow-in-the-dark</p>'
            + tray('mint', 'check', 7, 'Frozen', grows, meta='1 left of 2', big=True) +
            f'<p class="sk-kcap" style="color:#C9CFF5">Stickers and trays keep their colours; the tray’s inner panel is the navy page. Hanko #FF8A7A; hollow #C9CFF5 spots; ghosts #3A438A with a #C9CFF5 dashed edge; glow stars #C9FFDE in the gutters only. Full phone: screen 6.</p></div>')
    cards = [
        ('Buttons', btns, '', ''), ('Chips', chips, '', ''), ('Checkbox &amp; row states', cb, '', ''),
        ('Aisle header ornaments', orn, 'sk-span2', ''), ('Dividers, washi, edges', div, '', ''),
        ('Text fields', fields, '', ''), ('Toast · sticky note', toast, '', ''), ('Empty state', empty, '', ''),
        ('Loading &amp; sparkle', loading, '', ''), ('App launcher icon', launch, 'sk-span2', ''), ('“Next stop” interstitial', nxt, '', ''), ('Dark theme', glow, '', ''),
    ]
    body = ''.join(f'<div class="sk-kc {c}"><h4>{t}</h4>{h}</div>' for t, h, c, _ in cards)
    head = (f'<div class="sk-bhead"><span class="sk-btab" style="--tp:var(--sk-grape-p);--tm:var(--sk-grape-m);--tt:var(--sk-grape-t);--pat:var(--sk-pat-stars);--pats:12px"><span class="sk-bnum">E</span><h2>Flourish kit</h2></span>'
            f'<p class="sk-bsub">Every control is a sticker: kiss-cut labels, puffy candy pills, dotted “place sticker here” spots, hanko stamps, washi tape and LCD toys. Hard shadows only, never blur.</p></div>')
    return f'<div class="sk-block" id="sticker-kit">{head}<div class="sk-kit">{body}</div></div>'


def fab_svg():
    return ('<svg viewBox="-4 -4 72 72" width="64" height="64" role="img" aria-label="Add">'
            '<circle cx="32" cy="32" r="30" fill="#6E7BD0" transform="translate(2.4 3.4)"/><circle cx="32" cy="32" r="30" fill="#fff"/>'
            '<circle cx="32" cy="32" r="26.5" fill="#FF6F8E" stroke="#C3264F" stroke-width="2"/><path d="M32 33.5A26 26 0 0 1 6 33.5V40A26 26 0 0 0 58 40V33.5Z" fill="#E9506E" opacity="0" />'
            '<path d="M32 20V44M20 32H44" stroke="#C3264F" stroke-width="10" stroke-linecap="round"/><path d="M32 20V44M20 32H44" stroke="#fff" stroke-width="5" stroke-linecap="round"/>'
            '<path d="M14 24Q16 16 23 13" stroke="#fff" stroke-width="3" stroke-linecap="round" fill="none"/><circle cx="12.6" cy="29" r="1.4" fill="#fff"/>'
            '<use href="#sticker-i-star" x="44" y="-4" width="22" height="22"/></svg>')


def torn_svg():
    return ('<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 1.6H13V9L11 10.6L9.4 9L7.6 10.8L6 9.2L4.4 10.8L3 9.6Z" fill="#fff" stroke="#C8321F" stroke-width="1.4" stroke-linejoin="round"/>'
            '<path d="M3 12.4L4.4 11.2L6 12.8L7.6 11.2L9.4 13L11 11.4L13 13V14.4H3Z" fill="#fff" stroke="#C8321F" stroke-width="1.4" stroke-linejoin="round"/></svg>')
