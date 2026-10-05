# ---------- batch 3: meat, frozen, dairy, bakery, drinks ----------
TRAY = ('path', 'M6.6 29.8H41.4L38.6 39.8Q38 42 35.6 42H12.4Q10 42 9.4 39.8Z')

# ground beef: strawberry mound with squiggle texture on a foam tray, film glint
add('groundbeef', 'Lean ground beef', [
    F(('path', smooth([(8.6, 30), (10.4, 22.4), (17, 18.4), (24, 17.4), (31.4, 18.4), (37.6, 22.6), (39.4, 30)])), 'strawberry', 'm',
      tex=['M12.4 26.6q1.4-2 2.8 0t2.8 0', 'M20 22.6q1.4-2 2.8 0t2.8 0', 'M27.6 25.6q1.4-2 2.8 0t2.8 0', 'M17.4 28q1.4-2 2.8 0t2.8 0', 'M28.6 20.6q1.2-1.6 2.4 0'], tex_t='p', tex_w=1.6),
    F(TRAY, 'ramune', 'p', band=.32),
    F(('rect', 25.6, 32.4, 9.6, 5, 1.4), 'ramune', 'w', ol=1.2, cut=False),
], gloss=[G('M19 28.2L30.2 19.4', 2), DOT(32.2, 18.2, .9), G('M8.6 31.6H16', 2.2)], qty='454 g pack', aisle='meat')

# sliced deli meat (catalog deli_meat): three thin round slices shingled in a fan, each a flat pink disc in a fat pale rim
# with a little marbling: sliced meat from the counter, never a whole ham
def deli_slice(cx, cy, r):
    marble = [f'M{fmt(cx - 4.2)} {fmt(cy - 1.4)}q1.8 -1.6 3.6 0', f'M{fmt(cx + .2)} {fmt(cy + 3)}q1.8 -1.6 3.6 0', f'M{fmt(cx + 1.4)} {fmt(cy - 4.2)}q1.2 -1 2.4 0']
    return [F(('circle', cx, cy, r), 'sakura', 'p'),
            F(('circle', cx, cy, r - 3), 'sakura', 'm', ol=1.4, cut=False, band=None, tex=marble, tex_t='p', tex_w=1.5)]
add('ham', 'Sliced deli meat', [*deli_slice(30.6, 17.4, 10.6), *deli_slice(24.6, 24.6, 10.6), *deli_slice(17.8, 31.4, 10.6)],
    qty='175 g pack', aisle='deli')

# sausages: three linked peach sausages
SAU = 'M-9 -4.2H9C11.4 -4.2 12.6 -2.2 12.6 0C12.6 2.2 11.4 4.2 9 4.2H-9C-11.4 4.2 -12.6 2.2 -12.6 0C-12.6 -2.2 -11.4 -4.2 -9 -4.2Z'
add('sausages', 'Pork sausages', [
    *[F(('path', SAU), 'peach', 'm', band=.32, tf=f'translate({x} {y}) rotate({r})', det=['M-6 -1.6Q0 -2.8 6 -1.6'], det_t='p', det_w=1.4) for x, y, r in [(16, 17.6, -14), (31.6, 23.4, 12), (18.4, 34, -6)]],
    LINE('M28.2 15.8l1.6 -1.6', 'peach', 1.6, t='tr'), LINE('M35.6 34.6l1.8 1.4', 'peach', 1.6, t='tr'),
], gloss=[G('M-7.6 -1.8H-1', 2.2, 'translate(16 17.6) rotate(-14)'), G('M-7.6 -1.8H-1', 2.2, 'translate(18.4 34) rotate(-6)')], qty='500 g pack', aisle='meat')

# bacon: two wavy strips with white fat stripes
def strip(y, ph):
    top = ' '.join(f'Q{fmt(8 + i * 8 + 4)} {fmt(y - 3 + (2.6 if (i + ph) % 2 else -2.6))} {fmt(8 + i * 8 + 8)} {fmt(y - 3)}' for i in range(4))
    bot = ' '.join(f'Q{fmt(40 - i * 8 - 4)} {fmt(y + 3 + (2.6 if (i + ph) % 2 else -2.6))} {fmt(40 - i * 8 - 8)} {fmt(y + 3)}' for i in range(4))
    return f'M8 {fmt(y - 3)}{top}L40 {fmt(y + 3)}{bot}Z'
def wav(y, ph):
    return f'M8 {fmt(y)}' + ''.join(f'Q{fmt(8 + i * 8 + 4)} {fmt(y + (2.6 if (i + ph) % 2 else -2.6))} {fmt(8 + i * 8 + 8)} {fmt(y)}' for i in range(4))
add('bacon', 'Smoked bacon', [
    F(('path', strip(19, 0)), 'strawberry', 'm', tex=[wav(19, 0)], tex_t='#FFFFFF', tex_w=2.2),
    F(('path', strip(32, 1)), 'strawberry', 'm', tex=[wav(32, 1)], tex_t='#FFFFFF', tex_w=2.2, band=.3),
], gloss=[G('M10.6 15.4Q12 14 13.6 13.8', 1.8), G('M10.6 28.4Q12 27 13.6 26.8', 1.8)], qty='375 g pack', aisle='meat')

# striploin steak: strawberry slab, white fat rim on top, marbling, small round bone
add('steak', 'Striploin steak', [
    F(('path', smooth([(6.6, 29), (9.4, 20.4), (18.4, 15.4), (30.4, 15), (39.8, 19.4), (41.6, 29), (36.4, 38.6), (24.4, 41.6), (12.4, 39.2)])), 'strawberry', 'w', ol=2),
    F(('path', smooth([(8.6, 31), (12, 23.6), (20, 19.8), (30, 19.6), (37.6, 23), (39.6, 30.4), (34.6, 37.4), (24.4, 39.6), (13.6, 37.6)])), 'strawberry', 'm', ol=1.6, cut=False, band=.24,
      det=['M13.6 28.6L19.4 25.4', 'M24.4 24.4L31 22.6', 'M18.6 34.4L25.6 31.4'], det_t='p', det_w=1.4),
    F(('circle', 33.4, 31.4, 3.6), 'strawberry', 'w', ol=1.5, cut=False),
], gloss=[G('M10.6 22.6Q13.4 18.8 18.4 17.4'), DOT(22, 16.8)], qty='2 × 300 g', aisle='meat')

# rotisserie chicken: peach roast, two plump drumsticks up, white bone knuckles, ramune plate
DRUM = 'M0 0C-4.6 0 -6 -4 -5.2 -8.6C-4.6 -12.2 -2.4 -14.6 0 -14.6C2.4 -14.6 4.6 -12.2 5.2 -8.6C6 -4 4.6 0 0 0Z'
add('roastchicken', 'Rotisserie chicken', [
    F(('path', 'M5.4 36.6H42.6Q42.6 42 37.2 42H10.8Q5.4 42 5.4 36.6Z'), 'ramune', 'p', band=.4),
    F(('path', smooth([(5.6, 32), (8.6, 23.6), (16.6, 18.8), (26, 18.6), (34, 22.6), (37.6, 30), (33.4, 37.6), (16, 38.2)])), 'peach', 'm', band=.24,
      det=['M10.8 29.4Q15.6 24.4 22.4 26.6'], det_t='deep', det_w=1.6),
    F(('path', DRUM), 'peach', 'm', tf='translate(32 33.6) rotate(42)', band=.3),
    TUBE('M38.6 21.2L40.6 18.6', 'peach', 3.2, 'w'),
    F(('circle', 41.4, 16.2, 2), 'peach', 'w', ol=1.4), F(('circle', 43.2, 18.6, 2), 'peach', 'w', ol=1.4),
], gloss=[G('M9.4 27.4Q11.8 22.6 16.6 21'), DOT(20.6, 20.4)], qty='whole, hot', aisle='deli')

# pizza slice: lemon cheese, golden lemon crust, strawberry pepperoni
add('pizza', 'Frozen pizza', [
    F(('path', 'M24 42.2C21.6 42.2 6.8 15.4 7.8 13C11.4 9.8 17.4 8.4 24 8.4C30.6 8.4 36.6 9.8 40.2 13C41.2 15.4 26.4 42.2 24 42.2Z'), 'lemon', 'p', band=.18,
      det=['M15 18.4q.9 2.8 -.1 4.6', 'M33 21.6q-1 2.6 .1 4.2'], det_t='m', det_w=1.6),
    F(('path', 'M6.8 12.6C10.6 8 17 6 24 6C31 6 37.4 8 41.2 12.6C41.8 13.4 41.4 14.6 40.4 14.6C36.4 12.2 30.6 11 24 11C17.4 11 11.6 12.2 7.6 14.6C6.6 14.6 6.2 13.4 6.8 12.6Z'), 'lemon', 'm', ol=2),
    *[F(('circle', x, y, r), 'strawberry', 'm', ol=1.4, cut=False) for x, y, r in [(18.4, 17.6, 3.2), (29, 19.6, 3), (23.4, 27.6, 2.8)]],
    F(('path', 'M26.6 33.2C28 33.4 28.2 35.4 27.4 37.6C26.6 39.4 25.4 39.2 25.4 37.6Z'), 'lemon', 'p', ol=1.6),
], gloss=[G('M12.4 17.4L15.8 24.4', 2.2), DOT(17.4, 27.4, .9)], qty='800 g box', aisle='frozen')

# fries: strawberry carton with lemon fries
FRY = [(15.6, 9.6, -8), (20, 7.4, -3), (24.4, 6.4, 0), (28.8, 7.6, 4), (33, 9.8, 9), (18, 12.4, -6), (30.4, 12.6, 6)]
add('fries', 'Crinkle-cut fries', [
    *[F(('rect', x - 2.2, y, 4.4, 18, 1.6), 'lemon', 'm', tf=f'rotate({r} {x} {y + 18})', band=.2, sband=False, det=[f'M{fmt(x - .6)} {fmt(y + 2)}V{fmt(y + 6)}'], det_t='p', det_w=1.2) for x, y, r in FRY],
    F(('path', 'M10.6 21.4C14 24 18.6 25.4 24 25.4C29.4 25.4 34 24 37.4 21.4L34.8 39.6Q34.4 42 31.8 42H16.2Q13.6 42 13.2 39.6Z'), 'strawberry', 'm', band=.2),
    F(('path', heart_path(24, 33, 3.8)), 'strawberry', 'w', ol=1.3, cut=False),
], gloss=[G('M13.6 26.6L14.8 33.4', 2.4), DOT(15.2, 36.6)], qty='1 kg bag', aisle='frozen')

# waffles: two square waffles, butter pat, syrup drip
def waffle(x, y, w, tf=None):
    g = [f'M{fmt(x + w * i / 4)} {fmt(y)}V{fmt(y + w)}' for i in (1, 2, 3)] + [f'M{fmt(x)} {fmt(y + w * i / 4)}H{fmt(x + w)}' for i in (1, 2, 3)]
    return F(('rect', x, y, w, w, 4), 'lemon', 'm', tex=g, tex_t='deep', tex_w=1.8, band=.22, tf=tf, sband=False)
add('waffles', 'Frozen waffles', [
    waffle(18, 9.6, 21, 'rotate(10 28.5 20.1)'),
    waffle(8.6, 19.6, 22.6),
    F(('rect', 15.4, 21.2, 9, 6.4, 1.8), 'lemon', 'p', ol=1.5, cut=False),
    F(('path', 'M24.6 26.4C27.4 26.2 28.8 27.6 28.8 29.6V33.6C28.8 35 27.4 35 27.2 33.6L26.8 31.4C26.4 30.4 25.4 30.6 25.2 31.6L24.8 34.4C24.6 35.6 23.4 35.6 23.4 34.4V28.4C23.4 27.2 23.6 26.4 24.6 26.4Z'), 'cocoa', 'm', ol=1.4, cut=False),
], gloss=[G('M11.4 26V33', 2.4), DOT(11.4, 36.2)], qty='12 waffles', aisle='frozen')

# ice pops: sakura and ramune pops on white sticks traced in their own colour
def pop(cx, fam, rot):
    tf = f'rotate({rot} {cx} 40)'
    return [F(('rect', cx - 2, 26, 4, 15.6, 2), fam, 'w', tf=tf, sband=False),
            F(('path', f'M{fmt(cx - 7.4)} 13.6C{fmt(cx - 7.4)} 8.6 {fmt(cx - 4)} 6.4 {fmt(cx)} 6.4C{fmt(cx + 4)} 6.4 {fmt(cx + 7.4)} 8.6 {fmt(cx + 7.4)} 13.6V29Q{fmt(cx + 7.4)} 31.6 {fmt(cx + 4.8)} 31.6H{fmt(cx - 4.8)}Q{fmt(cx - 7.4)} 31.6 {fmt(cx - 7.4)} 29Z'),
              fam, 'm', band=.26, tf=tf, sband=False, det=[f'M{fmt(cx - 4)} 22.6Q{fmt(cx)} 20.6 {fmt(cx + 4)} 22.6'], det_t='p', det_w=1.6)]
add('icepops', 'Ice pops', [*pop(17.4, 'sakura', -10), *pop(30.8, 'ramune', 10)],
    gloss=[G('M13.4 10.4V16', 2.2, 'rotate(-10 17.4 40)'), G('M26.8 10.4V16', 2.2, 'rotate(10 30.8 40)')], qty='12 pops', aisle='frozen')

# cream: small gable carton, white with periwinkle trace, ramune gable
add('cream', 'Whipping cream', [
    F(('path', 'M14.4 15.6L18 8.4H30L33.6 15.6Z'), 'ramune', 'm', det=['M24 8.6V15.4'], det_w=1.3),
    F(('rect', 19.4, 5, 9.2, 4.2, 1.4), 'ramune', 'm'),
    F(('rect', 13.4, 15.4, 21.2, 26.6, 2.6), 'periwinkle', 'w', band=.2, band_t='p'),
    F(('path', 'M24 21.4C27.4 25.8 29.2 28.4 29.2 31C29.2 33.8 26.8 35.6 24 35.6C21.2 35.6 18.8 33.8 18.8 31C18.8 28.4 20.6 25.8 24 21.4Z'), 'periwinkle', 'w', ol=1.4, cut=False, band=.3, band_t='p'),
], gloss=[G('M16.6 19.6V27'), DOT(16.6, 30.4)], qty='473 mL carton', aisle='dairy')

# sour cream: short white tub, mint lid, dollop pictogram
add('sourcream', 'Sour cream', [
    F(('path', 'M10 22.6H38L36.2 38.8Q35.8 42 32.6 42H15.4Q12.2 42 11.8 38.8Z'), 'mint', 'w', band=.24, band_t='p'),
    F(('rect', 8, 17.4, 32, 6.6, 3.3), 'mint', 'm'),
    F(('rect', 15.6, 26.6, 16.8, 10.4, 2.6), 'mint', 'p', ol=1.4, cut=False),
    LINE('M19 34.4L22.6 28.6M23.6 34.4L25.8 28.4M28.2 34.4L28.8 28.8', 'melon', 1.8, t='m', cut=False),
], gloss=[G('M14 26L14.6 33', 2.4), G('M11 20.6H19', 2.2)], qty='500 mL tub', aisle='dairy')

# blueberry muffin: periwinkle pleated cup, cocoa dome with periwinkle berries (two families, §3.1)
add('muffins', 'Blueberry muffins', [
    F(('path', 'M8.4 24.4C8.4 15.4 15 10.6 24 10.6C33 10.6 39.6 15.4 39.6 24.4C39.6 26.6 38 27.4 36.4 27.4H11.6C10 27.4 8.4 26.6 8.4 24.4Z'), 'cocoa', 'p', band=.2,
      det=['M16 15.6h.1M30.6 15h.1'], det_t='m', det_w=1.8),
    *[F(('circle', x, y, 2.1), 'periwinkle', 'm', ol=1.3, cut=False) for x, y in [(19.4, 18.2), (27.6, 20.4), (33.4, 17.2), (14.2, 22), (23.4, 13.8)]],
    F(('path', 'M10.6 26.6H37.4L34.8 39.6Q34.4 42 31.8 42H16.2Q13.6 42 13.2 39.6Z'), 'periwinkle', 'p', band=.24, det=['M17 29V39.6', 'M21.4 29V40', 'M26.6 29V40', 'M31 29V39.6'], det_t='m', det_w=1.6),
], gloss=[G('M12.4 20Q13.6 15.4 18 13.4'), DOT(21.8, 12.6)], qty='6 pack', aisle='bakery')

# tortillas: a short stack of thin flour tortillas, its top open and spotted, and in front of it one folded in four, a
# soft wedge standing on the base with its layers showing along the round edge: thin wraps, never pancakes or a bun
add('tortillas', 'Flour tortillas', [
    *[F(('ellipse', 28.4, y, 13.4, 6.4), 'cocoa', 'p', band=.42, band_t='m') for y in (29.4, 26.8, 24.2)],
    F(('ellipse', 28.4, 21.6, 13.4, 6.4), 'cocoa', 'p',
      tex=['M21.4 20.6h.1M27.4 17.8h.1M34.6 18.8h.1M37.4 22.6h.1M30.6 23.4h.1'], tex_t='m', tex_w=2.4),
    F(('path', 'M7.4 42V20.4C19.4 20.4 29 30 29 42Z'), 'cocoa', 'p',
      tex=['M11.2 26.6h.1M11.8 35.6h.1M17.2 31h.1M22.4 38.2h.1M16.2 39.4h.1'], tex_t='m', tex_w=2.6,
      det=['M7.4 23.4C17.6 23.6 25.8 31.6 26 42'], det_t='m', det_w=1.5),
], qty='10 pack', aisle='bakery')

# doughnuts: two ring donuts, sakura icing with sprinkles
def donut(cx, cy, r):
    hole = r * .34
    return [F(('circle', cx, cy, r), 'cocoa', 'p', band=.28, band_t='m'),
            F(('path', f'M{fmt(cx - r + 1.4)} {fmt(cy + 1.6)}C{fmt(cx - r)} {fmt(cy - r * .6)} {fmt(cx - r * .5)} {fmt(cy - r + 1)} {fmt(cx)} {fmt(cy - r + 1)}C{fmt(cx + r * .5)} {fmt(cy - r + 1)} {fmt(cx + r)} {fmt(cy - r * .6)} {fmt(cx + r - 1.4)} {fmt(cy + 1.6)}'
                      f'C{fmt(cx + r * .7)} {fmt(cy + 4)} {fmt(cx + r * .45)} {fmt(cy + 1.8)} {fmt(cx + r * .25)} {fmt(cy + 3.6)}C{fmt(cx)} {fmt(cy + 5)} {fmt(cx - r * .3)} {fmt(cy + 2.2)} {fmt(cx - r * .5)} {fmt(cy + 3.8)}C{fmt(cx - r * .8)} {fmt(cy + 5)} {fmt(cx - r + 1.6)} {fmt(cy + 3)} {fmt(cx - r + 1.4)} {fmt(cy + 1.6)}Z'),
              'sakura', 'm', ol=1.6, cut=False, tex=[f'M{fmt(cx - r * .5)} {fmt(cy - r * .4)}l1.4 .8M{fmt(cx + r * .1)} {fmt(cy - r * .7)}l1.4 -.6M{fmt(cx + r * .55)} {fmt(cy - r * .2)}l.6 1.4M{fmt(cx - r * .7)} {fmt(cy + 0)}l1 1'], tex_t='#FFFFFF', tex_w=1.6),
            F(('ellipse', cx, cy - 1, hole, hole * .86), 'cocoa', 'p', ol=1.6, cut=False)]
add('donuts', 'Glazed doughnuts', [*donut(31, 19.6, 10.4), *donut(18.6, 30.4, 11.6)],
    gloss=[G('M9.4 27.4Q10.2 23.4 13.6 21.4', 2.2), DOT(9, 31)], qty='6 pack', aisle='bakery')

# orange juice: peach jug with handle, orange slice pictogram, lemon cap
add('orangejuice', 'Orange juice', [
    F(('rect', 15.4, 5.6, 9.2, 4.6, 1.6), 'melon', 'm'),
    TUBE('M31 15.4C38.4 15.4 38.8 26 31.4 27.4', 'peach', 5, 'p'),
    F(('path', 'M16.4 9.8H23.6L25.4 13.4C30.6 14.6 34.4 17.6 34.4 23V38.4Q34.4 42 30.8 42H13.2Q9.6 42 9.6 38.4V20C9.6 15.4 13 12.6 16.4 9.8Z'), 'peach', 'm', band=.22),
    F(('circle', 22, 29.6, 6.4), 'peach', 'p', ol=1.6, cut=False, det=['M22 24.6V34.6', 'M17.6 27L26.4 32.2', 'M17.6 32.2L26.4 27'], det_t='m', det_w=1.3),
], gloss=[G('M12.6 20.4Q13 17.4 15.4 15.6', 2.4), G('M12.6 24V30'), DOT(12.6, 33.2)], qty='2.63 L jug', aisle='drinks')

# water bottles: two ramune bottles with wavy labels
def bottle(cx, h0):
    return [F(('rect', cx - 3, h0 - 3.6, 6, 4.2, 1.4), 'ramune', 'm'),
            F(('path', f'M{fmt(cx - 3)} {fmt(h0)}H{fmt(cx + 3)}C{fmt(cx + 3)} {fmt(h0 + 3)} {fmt(cx + 7.4)} {fmt(h0 + 4)} {fmt(cx + 7.4)} {fmt(h0 + 9)}V39.4Q{fmt(cx + 7.4)} 42 {fmt(cx + 4.8)} 42H{fmt(cx - 4.8)}Q{fmt(cx - 7.4)} 42 {fmt(cx - 7.4)} 39.4V{fmt(h0 + 9)}C{fmt(cx - 7.4)} {fmt(h0 + 4)} {fmt(cx - 3)} {fmt(h0 + 3)} {fmt(cx - 3)} {fmt(h0)}Z'),
              'ramune', 'p', band=.16, tex=[f'M{fmt(cx - 8)} {fmt(h0 + 15)}q2 -2 4 0t4 0t4 0t4 0'], tex_t='m', tex_w=3.4)]
add('water', 'Spring water', [*bottle(30.8, 8.6), *bottle(17.2, 12.4)],
    gloss=[G('M12.4 25V31', 2.2), DOT(12.4, 34), G('M26 21.6V27', 2)], qty='24 × 500 mL', aisle='drinks')
