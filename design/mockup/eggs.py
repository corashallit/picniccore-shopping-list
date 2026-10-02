"""r9 · easter egg catalogue: every one of the 18 eggs is TRY-able, either on its tile (a working demo) or live in phones 1 / 6.
Each tile has a "Try it" line naming the trigger. Text that an egg shows is held >= 1.2 s + 0.3 s/word."""
from common import *
from deco import hanko_id
from page_b import row, pixbee
from book import slot as bkslot


def tryit(t):
    return f'<p class="sk-tryit"><span class="sk-tryk">{tb("sparkle", 14)}Try it</span><span>{t}</span></p>'


def chipbtn(label, sk, attrs=''):
    return f'<button class="sk-chip sk-ebtn" type="button" data-sk="{sk}" {attrs}>{label}</button>'


def mini_addbar(tape_cls='sk-t-ging', inner=''):
    return f'<div class="sk-eaddbar"><i class="sk-perfrow"></i>{tape("", "sk-tape " + tape_cls)}{inner}</div>'


def eggs():
    E = []

    def egg(n, title, stage, trig, eff, attrs='', cls='', where='', try_=''):
        w = f'<span class="sk-where">{where}</span>' if where else ''
        E.append(f'<div class="sk-egg" data-egg="{n}"><div class="sk-estage {cls}" {attrs}>{stage}</div><div class="sk-ecap"><h4><span class="sk-enum">{n}</span>{title}{w}</h4>'
                 f'{tryit(try_) if try_ else ""}<dl><dt>Trigger</dt><dd>{trig}</dd><dt>Effect</dt><dd>{eff}</dd></dl></div></div>')

    # 1 · dusk binder
    stars = ''.join(f'<svg class="sk-abs sk-duskstars sk-twinkle" style="left:{x}px;top:{y}px" width="{s}" height="{s}" aria-hidden="true"><use href="#sticker-i-star4"/></svg>' for x, y, s in [(30, 24, 22), (90, 50, 16), (150, 20, 20)])
    st = (f'{stars}<span class="sk-puff sk-abs" style="left:16px;top:84px;font-size:22px;color:var(--sk-bp)">Saturday shop</span>'
          f'<button class="sk-moonbtn" type="button" aria-label="Moon: undo dusk"><svg width="44" height="44" aria-hidden="true"><use href="#sticker-i-moon"/></svg></button>'
          f'<button class="sk-sunbtn" type="button" data-sk="sun" aria-label="Sun sticker, tap 7 times">{ico("sun", 52)}</button><span class="sk-tapcount" data-sk="suncount" aria-live="polite">tap the sun · 0 / 7</span>')
    egg(1, 'Dusk binder', st, 'Tap the sun sticker beside the title 7 times.', 'Over 1.2 s the sun sinks behind the title in 3 poses and a full moon rises with a sparkle burst; three posterised dusk bands (peach → lavender → periwinkle) wash the top of the binder, five sparkles twinkle in the gutters and the LCD star becomes a little full moon. Tap the moon to undo.', 'data-sk="dusk"', where='live in phone 1', try_='tap the sun 7 times here, or by the title in phone 1; tap the moon to undo')
    # 2 · ladybug stroll
    lrows = row(("eggs", "Eggs, large", "dozen", 0), 0) + row(("butter", "Butter, salted", "454 g brick", 0), 1)
    bk = f'<span class="sk-trbk sk-ladybk" data-sk="ladybk" role="button" tabindex="0" aria-label="Egg basket: press and hold">{basket_svg(BBY["eggbasket"], ["milkbag"], "open", 60, 48)}</span>'
    st = (tray('peach', 'clouds', 6, 'Dairy &amp; eggs', lrows, cls='is-compact sk-e-tray', right=bk)
          + f'<span class="sk-e-lady"><span class="sk-e-walk"><svg width="18" height="18" aria-hidden="true"><use href="#sticker-i-ladybug"/></svg></span><span class="sk-e-fly"><svg width="28" height="28" aria-hidden="true"><use href="#sticker-i-ladybugfly"/></svg></span></span>')
    egg(2, 'Ladybug stroll', st, 'Press and hold a folded basket for one second (a shorter hold just peeks inside).', 'A ladybug walks along the aisle tray’s end cap in six stepped hops with a two-frame waddle, pauses by an item, then opens its wings and flies off the edge. It never crosses item text.', where='live in phone 1', try_='press and hold the egg basket for 1 s (in phone 1, any folded basket)')
    # 3 · rolling produce
    st = (f'<div class="sk-abs sk-rollbk" style="right:8px;top:30px">{basket_svg(BBY["picnic"], ["banana", "carrots"], "open", 96, 77)}</div>'
          f'<span class="sk-abs sk-roll3" aria-hidden="true"></span><p class="sk-abs sk-rollout" data-sk="rollout3" aria-live="polite"></p>'
          f'<div class="sk-abs sk-echips">{chipbtn("+ apples", "roll3", "data-k=apple")}{chipbtn("+ strawberries", "roll3", "data-k=strawberries")}{chipbtn("+ fraises", "roll3", "data-k=strawberries data-w=fraises")}</div>')
    egg(3, 'Rolling produce', st, 'Add “strawberries”, “apples” or “fraises”, in any language.', 'The round fruit sticker rolls down the page and bumps the Produce basket, which wobbles hello, then settles on its new line: it is still to get, so it only hops into the basket once you check it off. A berry punnet hops instead.', where='live in phone 1', try_='tap a chip, or type “apples” into phone 1’s add bar and send')
    # 4 · petal shake
    egg(4, 'Petal shake', f'<div class="sk-center sk-petalflower">{ico("flower", 54)}</div><div class="sk-abs sk-echips" style="left:12px;bottom:12px">{chipbtn(tb("sparkle", 14) + "Shake", "shake4")}</div>',
        'Shake the phone (with reduced motion off).', 'Sakura petals scatter over the binder, settle as tiny stickers for 3 s, then blow away. Haptic: one soft tick.', 'data-sk="petalstage"', where='live in phones 1 + 6',
        try_='tap Shake, shake your phone, or grab a phone’s frame on this page and wiggle it left-right')
    # 5 · midnight snack mode
    gl = ''.join(f'<svg class="sk-glowstar sk-snackonly" style="left:{x}px;top:{y}px;animation-delay:-{d}s" width="{s}" height="{s}" aria-hidden="true"><use href="#sticker-i-glowstar"/></svg>' for x, y, s, d in [(232, 104, 14, 0), (150, 128, 10, 2)])
    st = (f'<div class="sk-snack" data-sk="snack">{gl}'
          f'<svg class="sk-abs sk-snackmoon" style="right:14px;top:10px" width="36" height="36" aria-hidden="true"><use href="#sticker-i-moon"/></svg>'
          f'<svg class="sk-abs sk-snacksun" style="right:14px;top:10px" width="36" height="36" aria-hidden="true"><use href="#sticker-i-sun"/></svg>'
          f'<div class="sk-abs" style="left:10px;top:12px">{lcd("10/24", "10 of 24", star=True, p=1.8, cls="sk-snacklcd")}</div>'
          f'<div class="sk-abs sk-snackrow"><span class="sk-tab" style="{avars("mint", "check")};height:32px"><span class="sk-num">7</span><span class="sk-tabname" style="font-size:17px">Frozen</span></span>'
          f'<span class="sk-snackstar sk-snackonly">{ico("star", 22)}</span><span class="sk-maru sk-snackdoodle sk-snackonly" aria-hidden="true">snack?</span></div>'
          f'<div class="sk-abs sk-clock"><button class="sk-kbtn" type="button" data-sk="clock" data-d="-30" aria-label="30 minutes earlier">−</button>'
          f'<span class="sk-lcd sk-clocklcd" data-p="1.6" role="img" aria-label="22:30">{lcd_svg("22:30", p=1.6)}</span>'
          f'<button class="sk-kbtn" type="button" data-sk="clock" data-d="30" aria-label="30 minutes later">+</button></div></div>')
    egg(5, 'Midnight snack mode', st, 'Open the app between 23:00 and 04:00.', 'Glow-in-the-dark theme, glow stars, the moon by the title and an LCD that asks “SNACK?”. A snack-time star and a “snack?” doodle sit beside the Frozen tab; the walking order never changes.',
        try_='step the clock past 23:00 with + (it also follows your real clock)')
    # 6 · shiny sticker
    st = (f'<div class="sk-center" style="display:flex;gap:16px;align-items:center"><span data-sk="shinyimg" class="sk-shinyimg sk-ico">{ico("apple", 80, extra=glint_svg("apple"))}</span>'
          f'<div style="display:flex;flex-direction:column;gap:6px;align-items:flex-start"><button class="sk-btn2" type="button" data-sk="roll" style="height:40px;font-size:14px;padding:0 14px">{tb("sparkle", 16)}Add an item</button>'
          f'<span class="sk-small" data-sk="rollout" aria-live="polite" style="font-weight:700;display:inline-flex;align-items:center;gap:4px">odds 1 in 200</span></div></div>')
    egg(6, 'Shiny sticker', st, 'Any newly added item, 1 in 200 chance.', 'The sticker arrives in holo foil clipped to its body with a glint every few seconds and a lemon star, and is saved to the album. Screen 1’s maple tin is one.', where='live in phone 1',
        try_='tap Add an item (this demo cheats: every 3rd is shiny; in phone 1 every 4th thing you add)')
    # 7 · magic word
    st = (f'<div class="sk-abs sk-kirawm" style="left:0;right:0;top:22px;text-align:center"><span class="sk-wm sk-kiraword">Kirakira</span></div>'
          + ''.join(deco(k, s, f'left:{x}px;top:{y}px', f'sk-stk sk-kirastk {d}') for k, s, x, y, d in [('star4', 24, 22, 18, ''), ('star', 20, 236, 24, 'sk-d2'), ('star4', 18, 40, 76, 'sk-d3'), ('heart', 18, 226, 78, '')]) +
          f'<div class="sk-abs sk-kirafield" data-sk="kiratile"><label class="sk-field"><input class="sk-fin" type="text" autocomplete="off" spellcheck="false" placeholder="type kirakira…" aria-label="Type the magic word"></label>'
          f'<button class="sk-sbtn" type="button" data-sk="kirasend" aria-label="Send">{ico("envelope", 44)}</button></div><p class="sk-abs sk-kiraout" aria-live="polite"></p>')
    egg(7, 'Magic word', st, 'Type “kirakira” (or キラキラ) in the add box and send.', 'Nothing is added. Instead every sticker on the page twinkles once and the wordmark turns holo for 5 s.', where='live in phones 1 + 6',
        try_='type kirakira here and send, or in phone 1’s add bar')
    # 8 · nap time
    st = (f'<div class="sk-abs sk-napbk" style="left:50%;top:22px;margin-left:-60px"><svg class="bk" viewBox="-8 -9 80 64" width="120" height="96" aria-hidden="true"><use href="#sticker-bk-picnic-back"/>'
          f'<circle class="bk-wait" cx="32" cy="18.5" r="7"/><g class="sk-napcat"><g class="sk-breathe"><use href="#sticker-i-cat" x="8" y="-6" width="48" height="48"/></g></g><use href="#sticker-bk-picnic-front"/></svg>'
          f'<button class="sk-catbtn" type="button" data-sk="nap" aria-label="Tap the napping cat"></button></div>'
          f'<span class="sk-zz sk-napz" style="left:64%;top:22px">z</span><span class="sk-zz sk-napz" style="left:68%;top:18px;animation-delay:-1.2s">Z</span>'
          f'<div class="sk-abs sk-echips" style="left:10px;bottom:10px">{chipbtn("End the trip, basket empty", "napgo")}</div>')
    egg(8, 'Nap time', st, 'An aisle basket stays empty for a whole trip.', 'A ginger tabby curls up in it and snoozes. Tap it: it stretches, the basket wobbles, and it trots off the page. Plain pastel face; its gloss sits on its back.', 'data-sk="napstage"',
        try_='tap “End the trip, basket empty”, then tap the cat')
    # 9 · birthday cake
    bdrow = (f'<li class="sk-row" data-key="cake" data-sk="bdrow"><span class="sk-ico"><svg class="sk-real" width="40" height="40" role="img" aria-label="Birthday cake"><use href="#sticker-i-cake"/></svg>'
             f'<svg class="sk-ghost" viewBox="0 0 52 52" width="40" height="40" aria-hidden="true"><circle class="sk-g2" cx="26" cy="27" r="21"/><use href="#sticker-i-cake" width="52" height="52" opacity=".16"/></svg>'
             f'<svg class="sk-badgeico" aria-hidden="true"><use href="#sticker-i-star"/></svg></span><span class="sk-txt"><span class="sk-nm">Birthday cake</span><span class="sk-dt">1 cake · 8 slices</span></span>'
             f'<span class="sk-cb" role="checkbox" tabindex="0" aria-checked="false" aria-label="Birthday cake">{spot()}{hanko(-12, 40, ("HAPPY", "B-DAY!"), label="Happy birthday")}</span></li>')
    st = (f'<div class="sk-abs sk-bdrow sk-m-pad" hidden><ul class="sk-rows">{bdrow}</ul></div>'
          f'<div class="sk-abs sk-echips">{chipbtn("+ birthday cake", "bday")}{chipbtn("+ candles", "bday")}{chipbtn("+ gâteau", "bday")}</div>')
    egg(9, 'Birthday cake', st, 'Add “birthday cake”, “candles” or “gâteau”.', 'Confetti stickers rain for 2 s and the check-off hanko for that item reads “HAPPY B-DAY!”.', 'data-sk="bdstage"', where='live in phone 1',
        try_='tap a chip, then tap the new row to check it off (or type “birthday cake” in phone 1)')
    # 10 · bonus stage
    pix = '<svg class="sk-pixel sk-abs" style="left:50%;top:14px;margin-left:-32px" width="64" height="64" viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">' + ''.join(
        f'<rect x="{x}" y="{y}" width="1" height="1" fill="{c}"/>' for y, row_ in enumerate([
            '......GG.GG.....', '.......GGG......', '....RRRGRRRR....', '...RRRRRRRRRR...', '..RRYRRRRRYRRR..', '..RRRRRYRRRRRR..', '..RRRYRRRRYRRR..',
            '...RRRRRRRRRR...', '...RRYRRRYRRR...', '....RRRRRRRR....', '.....RRYRRR.....', '......RRRR......', '.......RR.......']) for x, ch in enumerate(row_) if ch != '.'
        for c in [{'G': '#2E8239', 'R': '#FF6F8E', 'Y': '#FFF3A6'}[ch]]) + '</svg>'
    st = (f'{pix}<svg class="sk-normal sk-abs" style="left:50%;top:14px;margin-left:-32px" width="64" height="64" aria-hidden="true"><use href="#sticker-i-strawberries"/></svg>'
          f'<div class="sk-abs" style="left:10px;top:10px">{lcd("10/24", "10 of 24", star=True, p=1.6, cls="sk-bonuslcd")}</div>'
          f'<div class="sk-abs" style="left:0;right:0;bottom:12px;display:flex;justify-content:center;gap:8px">'
          + ''.join(f'<button class="sk-kbtn" type="button" data-sk="kon" data-n="{i}" aria-label="Stamp {i}">{i}</button>' for i in range(1, 5)) +
          f'</div><span class="sk-abs sk-small" data-sk="konout" aria-live="polite" style="right:10px;top:10px;font-weight:700">1 1 2 2 3 4 3 4</span>')
    egg(10, 'Bonus stage', st, 'Tap the stamp-card numbers 1 1 2 2 3 4 3 4.', 'Every sticker turns into an 8-bit sprite and the LCD plays a jingle until you leave the screen.', 'data-sk="bonus"', where='live in phones 1 + 6',
        try_='tap 1 1 2 2 3 4 3 4 here, or the stamps in phone 1’s sticker book; one more tap ends it')
    # 11 · seasonal sheets
    tapes = ''.join(f'<button type="button" class="sk-seastape{" is-on" if k == "pumpkin" else ""}" data-sk="season" data-k="{k}" data-pg="{pg}" aria-pressed="{"true" if k == "pumpkin" else "false"}"><i class="sk-tape sk-t-{k}"></i><b>{t}</b><small>{dt}</small></button>' for k, t, dt, pg in
                    [('maple', 'Canada Day', 'Jul 1', 'maple'), ('pumpkin', 'Thanksgiving', 'Oct 12 · next!', 'pumpkin'), ('halloween', 'Halloween', 'Oct 31', 'ghost'),
                     ('snow', 'First snow', 'by forecast', 'snowflake'), ('hearts', 'Valentine’s', 'Feb 14', 'heart'), ('redenv', 'Lunar New Year', 'Feb 17', 'envelope')])
    egg(11, 'Seasonal sticker sheets', f'<div class="sk-season">{tapes}</div>', 'The calendar (Ontario dates), or the first snowy forecast.', 'A themed washi tape on the add bar, a seasonal sticker sheet in the album and a matching stamp on the trip’s sticker book.', where='live in phones 1 + 5',
        try_='tap a tape: phone 1’s add bar, its sticker book stamp and the album’s Seasonal page switch to it')
    # 12 · golden hour
    st = (f'<div class="sk-golden" data-sk="golden"><div class="sk-goldsky"></div><svg class="sk-abs sk-goldsun" width="56" height="56" aria-hidden="true"><use href="#sticker-i-sun"/></svg>'
          f'<div class="sk-abs sk-goldpad"></div><div class="sk-abs sk-goldstk" style="left:20px;top:70px">{ico("lemonade", 48)}</div><div class="sk-abs sk-goldstk" style="left:84px;top:78px">{ico("sandwich", 42)}</div>'
          f'<div class="sk-abs sk-goldstk" style="left:140px;top:74px">{ico("strawberries", 44)}</div>'
          f'<label class="sk-abs sk-goldctl"><span>sunset in <b data-sk="goldmin">60 min</b></span><input type="range" min="0" max="60" value="60" step="1" data-sk="goldrange" aria-label="Minutes until sunset"></label></div>')
    egg(12, 'Golden hour', st, 'Within 30 minutes of local sunset (18:57 in Toronto on 3 Oct).', 'The binder warms to apricot, sticker shadows lengthen to +3 / +4 dp in a rosy tone, and the sun sticker sinks a little each minute.',
        try_='drag the slider toward sunset (under 30 min the binder warms)')
    # 13 · gashapon jackpot
    st = (f'<div class="sk-jackw" data-sk="jackstage"><svg class="sk-abs sk-jcap" width="70" height="70" aria-hidden="true"><use href="#sticker-i-capsule"/></svg>'
          f'<svg class="sk-abs sk-jitem" width="58" height="58" aria-hidden="true"><use href="#sticker-ai-dragonfruit"/></svg>'
          f'<span class="sk-abs sk-jhk" aria-hidden="true">{hanko(-10, 62, ("JACK", "POT!"), label=None)}</span><p class="sk-abs sk-jout" aria-live="polite"></p>'
          f'<div class="sk-abs sk-echips" style="left:10px;bottom:10px">{chipbtn(tb("sparkle", 14) + "Add a mystery item", "jack")}</div></div>')
    egg(13, 'Gashapon jackpot', st, 'The AI adds an item it has no icon for, 1 in 100.', 'The mystery capsule comes out gold, wobbles twice and stamps “JACKPOT!”. Its live-generated icon gets the holo treatment.', where='live in phone 1',
        try_='tap Add a mystery item (every 2nd is gold here); in phone 1 the first live-drawn sticker you add, e.g. “dragon fruit”')
    # 14 · perfect week
    sc = ''.join(f'<span class="sk-stamp" data-w="{i}">{ring28()}<svg class="sk-pg" style="--st:var(--sk-{fm}-t)" aria-hidden="true"><use href="#sticker-pg-{pg}"/></svg></span>' for i, (fm, pg) in enumerate(zip(FAMS[:4], PICS[:4])))
    st = (f'<div class="sk-pweek" data-sk="pweekw" data-n="0"><p class="sk-abs sk-pwlab"><b data-sk="pwn">week 0 of 4</b></p>'
          f'<div class="sk-stampstrip sk-pwcard">{sc}<span class="sk-pwhk">{hanko(-12, 54, ("PER-", "FECT!"), label=None)}</span></div>'
          f'<span class="sk-abs sk-pwchip">sakura picnic sheet unlocked ♡</span>'
          f'<div class="sk-abs sk-pwpocket" aria-hidden="true"><i></i><span>sticker sheet 2 of 2</span></div>'
          f'<div class="sk-abs sk-echips" style="left:10px;bottom:10px">{chipbtn(star_svg(14) + "Finish a list", "pweek")}</div></div>')
    egg(14, 'Perfect week', st, 'Finish four lists in a row, every item checked.', 'The stamp card turns holo for a week and a new sticker sheet (sakura picnic) unlocks in the album. After ten finished trips the binder’s back pocket opens with a page curl: “sticker sheet 2 of 2”, closing the loop the hero sheet starts.', where='in phone 5',
        try_='tap Finish a list four times (keep going to 10 for the back pocket)')
    # 15 · LCD pet
    st = (f'<div class="sk-center" style="display:flex;flex-direction:column;align-items:center;gap:10px"><span class="sk-lcd sk-petlcd" data-p="2" data-sk="pettile" role="button" tabindex="0" aria-label="10 of 24 in the basket: press and hold">{lcd_svg("10/24", p=2, star=True)}</span>'
          f'{chipbtn(tb("check", 14) + "Check an item", "petfeed")}</div>')
    egg(15, 'LCD pet', st, 'Press and hold the LCD counter.', 'A pixel bee moves into the LCD for this trip and the digits read FED for a beat. Each checked item feeds it (a two-frame chomp); finish the list and it does a 4-frame dance.', where='live in phones 1, 2, 6 + finale',
        try_='press and hold the LCD, then tap Check an item to feed it')
    # 16 · rainy day
    drops = ''.join(f'<i class="sk-conf sk-raindrop" style="left:{x}%;width:5px;height:11px;border-radius:3px;background:#7CC6F5;animation-duration:1.4s;animation-delay:-{d}s;--fx:-6px;--fr:0deg"></i>' for x, d in [(8, 0), (22, .5), (36, .9), (52, .2), (68, 1.1), (84, .7), (94, .3)])
    st = (f'<div class="sk-rain" data-sk="rain">{drops}{mini_addbar("sk-t-ging", "<span class=sk-eafield>Write a note…</span>")}'
          f'<span class="sk-abs sk-umbrella" aria-hidden="true">{umbrella_svg(64)}</span><div class="sk-abs sk-raintoast" role="status">bring the tote! ♡</div>'
          f'<div class="sk-abs sk-echips" style="left:10px;top:10px">{chipbtn("☂ Make it rain", "raingo")}</div></div>')
    egg(16, 'Rainy day', st, 'Rain in the forecast for the store’s postcode.', 'A sakura umbrella sticker shelters the add bar, raindrop washi tape appears, and the toast says “bring the tote!”.', where='live in phone 6',
        try_='tap Make it rain (phone 6 gets the umbrella, tape and toast too)')
    # 17 · ant parade
    ants = ''.join(f'<span class="sk-ant sk-eant" style="--d:{i * .42:.2f}s"><svg width="{30 if i == 2 else 22}" height="{30 if i == 2 else 22}" aria-hidden="true"><use href="#sticker-i-ant"/></svg></span>' for i in range(5))
    st = (f'<div class="sk-antw" data-sk="antstage">{mini_addbar("sk-t-dots", "<span class=sk-eafield>Write a note…</span>")}{ants}'
          f'<div class="sk-abs sk-antlist" data-sk="antlist" aria-live="polite"></div>'
          f'<div class="sk-abs sk-echips" style="left:10px;top:10px">{chipbtn("+ watermelon", "antadd", "data-k=watermelon")}{chipbtn("+ sandwich bread", "antadd", "data-k=loaf")}{chipbtn("+ lemonade", "antadd", "data-k=lemonade")}</div></div>')
    egg(17, 'Ant parade', st, 'The list holds three or more picnic things (watermelon, sandwich bread, lemonade…).', 'Once per session five ants march along the add bar’s perforation, the middle one carrying a strawberry, in 2-frame steps. They never cross the text field.', where='live in phone 1',
        try_='add all three picnic things (phone 1 already has strawberries + a baguette: type “watermelon”)')
    # 18 · LCD receipt
    mb = ''.join(bkslot(i, k) for i, k in enumerate(['strawberries', 'banana', 'croissant', 'milkbag', 'coffee', 'maple-holo', 'eggs', 'flour']))
    st = (f'<div class="sk-minibook sk-book is-full" aria-hidden="true"><div class="sk-bk-r"><ol class="sk-bk-page">{mb}</ol><i class="sk-bk-foil"></i></div><span class="sk-bk-receipt"><i></i><i></i><i></i></span></div>'
          f'<div class="sk-abs sk-rcptl"><span class="sk-lcd" data-p="1.8" data-sk="rcptlcd" role="button" tabindex="0" aria-label="24 of 24 in the basket: press and hold to print the receipt">{lcd_svg("24/24", p=1.8, star=True)}</span></div>'
          f'<p class="sk-abs sk-small" data-sk="rcptout" aria-live="polite" style="left:12px;bottom:10px;font-weight:700">trip complete · 24 / 24</p>')
    egg(18, 'LCD receipt', st, 'After the finale, press and hold the LCD.', 'The LCD prints a pastel thermal-paper receipt sticker line by line in DotGothic16 with the trip’s stats (24 stickers · 8 baskets · 41 min · shiny count); tap it and it is tucked into the trip’s sticker book (and its album page).', where='live in phones 1 + 6',
        try_='press and hold the LCD, then tap the receipt; in phone 1, finish the trip (or tap “Finish the trip” under it), then hold its LCD')
    return '<div class="sk-eggs">' + ''.join(E) + '</div>'


def umbrella_svg(size=64):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 48 48" aria-hidden="true">'
            f'<path d="M6 24C6 12 16 6 24 6S42 12 42 24C39 21 35 21 33 24C31 21 27 21 24 24C21 21 17 21 15 24C13 21 9 21 6 24Z" fill="var(--sk-shadow,#6E7BD0)" transform="translate(1.6 2.2)"/>'
            f'<path d="M6 24C6 12 16 6 24 6S42 12 42 24C39 21 35 21 33 24C31 21 27 21 24 24C21 21 17 21 15 24C13 21 9 21 6 24Z" fill="#FF9FC8" stroke="#fff" stroke-width="5" stroke-linejoin="round" paint-order="stroke"/>'
            f'<path d="M6 24C6 12 16 6 24 6S42 12 42 24C39 21 35 21 33 24C31 21 27 21 24 24C21 21 17 21 15 24C13 21 9 21 6 24Z" fill="#FF9FC8" stroke="#B02E6B" stroke-width="2" stroke-linejoin="round"/>'
            f'<path d="M24 23V38Q24 42 20 42Q17 42 17 39" fill="none" stroke="#B02E6B" stroke-width="2.4" stroke-linecap="round"/><path d="M12 16Q14 11 19 9" stroke="#fff" stroke-width="2.4" fill="none" stroke-linecap="round"/></svg>')
