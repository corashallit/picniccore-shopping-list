"""r9 · the trip sticker book (one per grocery trip).

A ring-bound mini sticker album lying open under the app bar. It merges the old stamp strip:
  left page  = the trip's stamp card (date label + the 8 aisle stamp circles, 2 rows of 4)
  spine      = cloth tape with three binder rings
  right page = glossy release paper with one dotted "place sticker here" spot per item on the trip
               (3 rows x 8 = 24 per page; longer trips get more pages that turn)
Checked stickers are laid down in the next free spot, each a little crooked as if stuck by hand.
"""
from common import *

BOOK_DATE = 'Sat 3 Oct'
PER_PAGE = 24
# hand-stuck tilt per spot (degrees); repeats every 12 spots
ROTS = [-6, 4, -2, 7, -5, 3, 6, -4, 2, -7, 5, -3]


def slot(i, key=None):
    r = ROTS[i % len(ROTS)]
    if key:
        sym = f'sticker-{key}' if key.startswith('ai-') else f'sticker-i-{key}'
        return (f'<li class="sk-bk-slot is-on" data-key="{key}" style="--r:{r}deg"><svg class="sk-bk-st" width="26" height="26" aria-hidden="true">'
                f'<use href="#{sym}"/></svg></li>')
    return f'<li class="sk-bk-slot" style="--r:{r}deg"></li>'


def stamps(aisles, done=(), now=None, labels=True):
    out = []
    for a in aisles:
        is_done = a['n'] in done
        inner = (f'<svg class="sk-pg" style="--st:var(--sk-{a["fam"]}-t)" aria-hidden="true"><use href="#sticker-pg-{a["pic"]}"/></svg>' if is_done
                 else f'<span class="sk-sn">{a["n"]}</span>')
        c = 'sk-stamp' + (' is-now' if a['n'] == now else '') + (' is-done' if is_done else '')
        lab = f' role="img" aria-label="{a["name"]}: {"stamped" if is_done else "not yet"}"' if labels else ' aria-hidden="true"'
        out.append(f'<span class="{c}" data-n="{a["n"]}" data-pic="{a["pic"]}" data-fam="{a["fam"]}"{lab}>{ring28()}{inner}</span>')
    return ''.join(out)


def book(aisles, got, total=None, done=(), now=None, cls='', live=True, date=BOOK_DATE, perfect=False):
    """got = sticker keys in check order (the order they were laid down)."""
    total = total if total is not None else sum(len(a['items']) for a in aisles)
    n = len(got)
    pages = max(1, -(-total // PER_PAGE))
    page = min(pages - 1, n // PER_PAGE) if n < total else pages - 1
    first = page * PER_PAGE
    cnt = min(PER_PAGE, total - first)
    slots = ''.join(slot(first + i, got[first + i] if first + i < n else None) for i in range(cnt))
    if n < total and n >= first:
        slots = slots.replace('<li class="sk-bk-slot" ', '<li class="sk-bk-slot is-next" ', 1)
    full = n >= total
    c = 'sk-book' + (' is-full' if full else '') + (' is-perfect' if perfect else '') + (' ' + cls if cls else '')
    pno = f'<span class="sk-bk-pno" aria-hidden="true">{page + 1}/{pages}</span>' if pages > 1 else '<span class="sk-bk-pno" aria-hidden="true" hidden></span>'
    hk = f'<span class="sk-bk-perfect" aria-hidden="true">{hanko(-12, 40, ("PER-", "FECT!"), label=None)}</span>'
    lab = f'Trip sticker book, {date}: {n} of {total} stickers, {len(done)} of {len(aisles)} aisles stamped'
    attrs = ' data-sk="book"' if live else ''
    return (f'<div class="{c}"{attrs} data-total="{total}" data-page="{page}" role="group" aria-label="{lab}">'
            f'<div class="sk-bk-l"><span class="sk-bk-date">{heart_svg(12)}{date}</span>'
            f'<div class="sk-stampstrip sk-bk-stamps" aria-label="Stamp card">{stamps(aisles, done, now)}</div>{hk}</div>'
            f'<i class="sk-bk-spine" aria-hidden="true"><i></i><i></i><i></i></i>'
            f'<div class="sk-bk-r"><ol class="sk-bk-page" aria-hidden="true">{slots}</ol>{pno}<i class="sk-bk-foil" aria-hidden="true"></i></div>'
            f'<span class="sk-vh" aria-live="polite" data-sk="booklive"></span></div>')


def got_in_walking_order(aisles):
    out = []
    for a in aisles:
        for it in a['items']:
            if it[3]:
                out.append(it[0] + '-holo' if len(it) > 4 and it[4] == 'shiny' else it[0])
    return out


def _frame(t, label, key, tf, kira=None, holo=False):
    k = (f'<svg class="sk-sbkira" width="40" height="40" style="transform:scale({kira})" aria-hidden="true"><use href="#sticker-i-kira"/></svg>' if kira else '')
    st = f'<svg class="sk-sbst" width="52" height="52" style="transform:{tf}" aria-hidden="true"><use href="#sticker-i-{key}"/></svg>' if tf else ''
    spot = '<i class="sk-sbspot' + (' is-holo' if holo else '') + '"></i>'
    return f'<li><span class="sk-sbcell">{spot}{st}{k}</span><code>{t}</code><span>{label}</span></li>'


def showcase(aisles):
    """section B: the trip sticker book in all its states, the magic-appear storyboard and the anatomy notes"""
    full = [dict(a, items=[tuple(it[:3]) + (1,) + tuple(it[4:]) for it in a['items']]) for a in aisles]
    allgot = got_in_walking_order(full)
    now = got_in_walking_order(aisles)
    alln = tuple(a['n'] for a in aisles)
    b_new = book(aisles, [], done=(), now=1, live=False, date='Sat 10 Oct')
    b_now = book(aisles, now, done=(3,), now=4, live=False)
    b_full = book(full, allgot, done=alln, live=False, perfect=True).replace('<span class="sk-vh"', '<span class="sk-bk-receipt" aria-hidden="true"><i></i><i></i><i></i></span><span class="sk-vh"', 1)
    b_dark = book(aisles, now, done=(3,), now=4, live=False)
    b_p2 = book(aisles, allgot + ['lemon', 'ai-oatmilk'], total=30, done=(), now=None, live=False)
    figs = [('New trip · Sat 10 Oct', 'A fresh book: every spot dotted, the first one holo (“next sticker goes here”), all 8 stamps waiting.', b_new, ''),
            ('In the store · 10 of 24 (phone 1)', 'Stickers laid down in the order they were gotten, each stuck a little crooked; the Deli stamp is in, aisle 4 is “now”.', b_now, ''),
            ('Done · 24 of 24 · PERFECT!', 'After the finale the stamp page turns holo and PER-FECT! is pressed across the spine; a printed receipt (egg 18) pokes out of the fore-edge.', b_full, ''),
            ('Glow-in-the-dark (phone 6)', 'Navy pages, a violet release paper with glow specks; the stickers and the white kiss-cut edge stay as they are.', b_dark, ' sk-dark sk-bkdark'),
            ('A longer trip turns the page', '3 rows × 8 = 24 spots a page. Trip 26 onwards starts page 2 (the page turns in 3 frames, 240 ms) and a tiny LCD reads 2/2.', b_p2, '')]
    cards = ''.join(f'<figure class="sk-bkfig{c}"><div class="sk-bkwrap">{b}</div><figcaption><b>{t}</b>{d}</figcaption></figure>' for t, d, b, c in figs)
    sb = ''.join([_frame('before', 'the next spot waits, holo', 'strawberries', None, holo=True), _frame('0 ms', 'kira bloom · the twin at .2', 'strawberries', 'scale(.2) rotate(-16deg)', 1.1),
                  _frame('+64', 'pose 1 · 1.3', 'strawberries', 'scale(1.3) rotate(-8deg)', 1.4), _frame('+144', 'pose 2 · .86', 'strawberries', 'scale(.86) rotate(5deg)', .8),
                  _frame('+224', 'pressed flat · .95 (stuck on)', 'strawberries', 'translate(1px,1.4px) scale(.95)', .4), _frame('+320', 'settled, tilted −6°; page lifts 2 dp', 'strawberries', 'rotate(-6deg)')])
    sbr = ''.join([_frame('0', 'un-check: corner lifts', 'strawberries', 'translate(-1px,-2px) rotate(-18deg) skewX(-8deg)', .6), _frame('+64', 'peels up · 1.12', 'strawberries', 'translate(-1px,-3px) rotate(-22deg) scale(1.12)', 1.1),
                   _frame('+144', 'shrinks · .55, sparks run inward', 'strawberries', 'scale(.55)', 1.3), _frame('+320', 'gone; the spot turns holo', 'strawberries', None, holo=True)])
    notes = ('<ul class="sk-bknotes">'
             '<li><b>One book per trip</b>, pinned under the app bar in place of the old stamp strip. A new trip (menu or “New trip” under phone 1) files today’s book in the album and starts a fresh one.</li>'
             '<li><b>Left page = the stamp card.</b> Date label, the 8 aisle stamps in walking order (the current one spins), and the bonus-stage secret: tap 1 1 2 2 3 4 3 4.</li>'
             '<li><b>Spine</b>: periwinkle cloth tape with three binder rings. <b>Right page</b>: glossy release paper with one dotted “place sticker here” spot per item; the next spot is holo.</li>'
             '<li><b>Rule 5:</b> the only flight is row → basket. The book updates a beat later by a magic appear in place, even when the list is scrolled far below it.</li>'
             '<li><b>Rule 6:</b> it is part of the pinned header: a solid pad backing, white kiss-cut edge and hard shadow, so no row ever shows above or around it.</li>'
             '<li><b>TalkBack:</b> one summary (“Trip sticker book, Sat 3 Oct: 10 of 24 stickers, 1 of 8 aisles stamped”) and a polite “Strawberries added to today’s sticker book”; the stickers themselves are decorative twins.</li>'
             '<li><b>Reduced motion:</b> no bloom, no sparks, no lift: the twin simply appears (120 ms fade).</li></ul>')
    return (f'<div class="sk-bkshow" id="sticker-book"><div class="sk-bhead2"><h3 class="sk-h3">Trip sticker book <span class="sk-chip" style="--cp:var(--sk-sakura-p)">new · one per trip</span></h3>'
            f'<p class="sk-small">Like a page of a 90s sticker album (シール帳): every grocery you get on this trip hops into its basket, then its twin is laid down here by magic. It fills up as you shop and is the trip’s keepsake.</p></div>'
            f'<div class="sk-bkgrid">{cards}<div class="sk-bkfig sk-bkboard"><b>Magic appear, frame by frame</b><span class="sk-small">It starts 320 ms after the sticker lands in its basket; 4 stepped frames, 320 ms.</span><ol class="sk-story2">{sb}</ol><b>…and peeling off (un-check)</b><ol class="sk-story2">{sbr}</ol></div>'
            f'<div class="sk-bkfig sk-bknotesw">{notes}</div></div></div>')
