# ---------- round 3: silhouettes that stop the confusable pairs, off-model fixes ----------
# executed inside icons.py after the other batches; replace() keeps the sheet order


def replace(key, label, parts, qty, aisle):
    new = Icon(key, label, parts, [], qty=qty, aisle=aisle)
    for n, i in enumerate(ICONS):
        if i.key == key:
            ICONS[n] = new
            return
    ICONS.append(new)


# potato chips: peach pillow bag, top-right corner torn open, two lemon chips spilling out (peas keep the ramune crimped bag)
def chip(cx, cy, rx, ry, rot, seed):
    return ('path', blob(cx, cy, rx, ry, 9, .14, seed, rot))


replace('chips', 'Potato chips', [
    F(('path', 'M12.4 15.6H25.4L27.4 18.2L29.4 15.8L31.6 18.6L33.6 16.4L35.6 19.6C37.6 25.4 37.6 32 35.8 37.2L36.4 41.6H11.6L12.2 37.2C10.2 31.4 10.4 22 12.4 15.6Z'),
      'peach', 'p', band=.18),
    F(('path', 'M10.6 10.8H24.8Q26 10.8 26 12V15.2L24.4 16.8L22.4 15.2L20.4 16.8L18.4 15.2L16.4 16.8L14.4 15.2L12.4 16.8L10.6 15.2Q9.4 14.6 9.4 13.4V12Q9.4 10.8 10.6 10.8Z'),
      'peach', 'm', det=['M13.4 11.8V14.2M17.4 11.8V14.2M21.4 11.8V14.2'], det_w=1.1),
    F(('rect', 9.4, 37.2, 29.2, 5.2, 1.8), 'peach', 'm', det=['M13 38.4V41.2M17 38.4V41.2M21 38.4V41.2M25 38.4V41.2M29 38.4V41.2M33 38.4V41.2'], det_w=1.1),
    F(chip(23.6, 28.6, 6.6, 5.2, 18, 3), 'lemon', 'm', ol=1.5, cut=False, det=['M21 27.6h.1M25.6 30.2h.1M23.4 25.6h.1'], det_t='deep', det_w=1.7),
    F(chip(32.6, 11.6, 5.4, 4.2, -24, 5), 'lemon', 'm', ol=1.8, det=['M31.2 11h.1M34.2 12.4h.1'], det_t='deep', det_w=1.7),
    F(chip(39.4, 19.2, 4.4, 3.6, 30, 9), 'lemon', 'm', ol=1.8, det=['M39.4 19.2h.1'], det_t='deep', det_w=1.7),
], '235 g bag', 'snacks')

# cat food: tall grape tin, two ear bumps on the lid, paw print (no fish: the fish belongs to tuna)
replace('catfood', 'Cat food', [
    F(('path', 'M14.6 15.8C14.4 11 15.4 7.8 17.2 7C18.6 7.6 20.6 10.4 22 13.4Z'), 'grape', 'p', ol=2, det=['M16.6 12.6Q17 10.4 17.6 9.8'], det_t='m', det_w=1.4),
    F(('path', 'M33.4 15.8C33.6 11 32.6 7.8 30.8 7C29.4 7.6 27.4 10.4 26 13.4Z'), 'grape', 'p', ol=2, det=['M31.4 12.6Q31 10.4 30.4 9.8'], det_t='m', det_w=1.4),
    F(cyl(11.6, 36.4, 16, 42, 4.2), 'grape', 'p', band=.2),
    F(('rect', 12.5, 22.4, 23, 13.4), 'grape', 'w', ol=0, cut=False, det=['M12.5 22.4H35.5', 'M12.5 35.8H35.5'], det_w=1.3),
    F(('ellipse', 24, 31.2, 3.6, 3), 'grape', 'm', ol=1.2, cut=False),
    *[F(('ellipse', x, y, 1.5, 1.9, a), 'grape', 'm', ol=1.1, cut=False) for x, y, a in [(19.2, 27.4, -20), (22.6, 25.4, -6), (26.4, 25.6, 8), (29.4, 27.8, 22)]],
    F(ell_top(11.6, 36.4, 16, 4.2), 'grape', 'w'),
    F(('ellipse', 24, 16, 9.4, 2.6), 'grape', 'p', ol=1.2, cut=False),
], '6 × 156 g', 'pets')

# peanut butter: wide squat jar, cocoa lid, peanut on a white label
replace('peanutbutter', 'Peanut butter', [
    F(('path', 'M9.6 24H38.4Q41.4 24 41.4 27V37.6Q41.4 42 37 42H11Q6.6 42 6.6 37.6V27Q6.6 24 9.6 24Z'), 'peach', 'm', band=.22),
    F(('rect', 5.4, 16.4, 37.2, 8.8, 3.2), 'cocoa', 'm', det=['M9.6 18.6V23M14.2 18.6V23M18.8 18.6V23M23.4 18.6V23M28 18.6V23M32.6 18.6V23M37.2 18.6V23'], det_w=1.1),
    F(('rect', 13.4, 27.4, 21.2, 10.2, 3), 'cocoa', 'w', ol=1.5, cut=False),
    F(('path', 'M17.8 32.4C17.8 30.2 19.4 29.4 21 29.6C22.4 29.8 23 30.8 24 30.8C25 30.8 25.6 29.8 27 29.6C28.6 29.4 30.2 30.2 30.2 32.4C30.2 34.6 28.6 35.4 27 35.2C25.6 35 25 34 24 34C23 34 22.4 35 21 35.2C19.4 35.4 17.8 34.6 17.8 32.4Z'),
      'cocoa', 'p', ol=1.3, cut=False, det=['M20.6 32.2h.1M27.4 32.6h.1'], det_t='m', det_w=1.5),
], '1 kg jar', 'pantry')

# strawberry jam: round-shouldered jar under a gingham cloth cap with pinked edges, tied with string
replace('jam', 'Strawberry jam', [
    F(('path', 'M15.4 20H32.6C37.6 22.6 39.4 27 39.4 31.6V36.6Q39.4 42 34 42H14Q8.6 42 8.6 36.6V31.6C8.6 27 10.4 22.6 15.4 20Z'), 'strawberry', 'm', band=.22),
    F(('rect', 15.6, 27.4, 16.8, 10.4, 3.2), 'strawberry', 'w', ol=1.5, cut=False),
    F(berry(24, 29, 6.2, 7.2), 'strawberry', 'm', ol=1.3, cut=False),
    F(('path', calyx(24, 29, .56)), 'melon', 'm', ol=1, cut=False),
    F(('path', 'M15.2 8.6C18.4 6 29.6 6 32.8 8.6C35.4 10.6 37.6 14.4 41 19.6L38.6 20.6L39.2 23.2L36.4 22.8L35.8 25.4L33.6 23.4L31.8 25.4L30.2 23L28 24.8L26.6 22.6L24 24.4L21.4 22.6L20 24.8L17.8 23L16.2 25.4L14.4 23.4L12.2 25.4L11.6 22.8L8.8 23.2L9.4 20.6L7 19.6C10.4 14.4 12.6 10.6 15.2 8.6Z'),
      'strawberry', 'p', tex=['M13 4V27', 'M19 4V27', 'M25 4V27', 'M31 4V27', 'M37 4V27', 'M4 11H44', 'M4 17H44', 'M4 23H44'], tex_t='m', tex_w=2.4),
    TUBE('M12.6 19.4Q24 22.6 35.4 19.4', 'cocoa', 2.2, 'p'),
    F(('path', 'M35 19.6C37.6 17 40.6 17.6 40.4 19.8C40.2 21.6 37.4 21.4 35 19.6Z'), 'cocoa', 'p', ol=1.4),
], '500 mL jar', 'breakfast')

# mayonnaise: tall slim jar with a flip squeeze cap, egg pictogram
replace('mayo', 'Mayonnaise', [
    F(('rect', 20.4, 4.4, 7.2, 5.6, 2.4), 'ramune', 'm', ol=1.8),
    F(('path', 'M15 9.4H33Q35.4 9.4 35.4 11.8V14.2Q35.4 15.8 33.8 15.8H14.2Q12.6 15.8 12.6 14.2V11.8Q12.6 9.4 15 9.4Z'), 'ramune', 'm', det=['M16.6 12.6H31.4'], det_t='p', det_w=1.2),
    F(('path', 'M15.4 15.4H32.6C35.4 15.4 36.6 17.6 36.6 20.4V38Q36.6 42 32.6 42H15.4Q11.4 42 11.4 38V20.4C11.4 17.6 12.6 15.4 15.4 15.4Z'), 'ramune', 'w', band=.18, band_t='p'),
    F(('rect', 14.8, 21.4, 18.4, 14, 3.4), 'ramune', 'p', ol=1.5, cut=False),
    F(('ellipse', 24, 28.4, 4.6, 5.2), 'lemon', 'w', ol=1.4, cut=False),
    F(('circle', 24, 29.6, 2.3), 'lemon', 'm', ol=1.1, cut=False),
], '890 mL jar', 'pantry')

# pasta sauce: Mason jar, shoulders up to a narrow neck, two-piece lemon lid, tomato + basil label
replace('pastasauce', 'Tomato pasta sauce', [
    F(('path', 'M16.6 15.4H31.4V18.6C35.6 19.6 38 22.6 38 26.6V38Q38 42 34 42H14Q10 42 10 38V26.6C10 22.6 12.4 19.6 16.6 18.6Z'), 'strawberry', 'm', band=.2),
    F(('rect', 14.4, 8.2, 19.2, 7.8, 2.4), 'lemon', 'm', det=['M17 10.2V14.2M20.4 10.2V14.2M24 10.2V14.2M27.6 10.2V14.2M31 10.2V14.2'], det_w=1.1),
    F(('rect', 14, 24.6, 20, 12.6, 3), 'strawberry', 'w', ol=1.5, cut=False),
    F(('circle', 22, 31.4, 3.6), 'strawberry', 'm', ol=1.2, cut=False),
    F(('path', 'M24.6 31C24.4 27.6 27.2 25.6 30.6 26.2C30.8 29.6 28.2 31.6 24.6 31Z'), 'melon', 'm', ol=1.2, cut=False),
], '650 mL jar', 'pantry')

# baby food: little round pot with a ridged peach lid and the feeding spoon
replace('babyfood', 'Baby food', [
    TUBE('M33.4 32.6L41.6 13.6', 'ramune', 2.6, 'p'),
    F(('ellipse', 41.6, 11.4, 3.4, 4.4, 22), 'ramune', 'p', ol=1.6),
    F(('path', 'M15 23.4H33Q36.6 23.4 36.8 27C37.2 33 35.4 42 26.4 42H21.6C12.6 42 10.8 33 11.2 27Q11.4 23.4 15 23.4Z'), 'peach', 'p', band=.32, band_t='m'),
    F(('rect', 10.6, 16.4, 26.8, 7.6, 3.8), 'peach', 'm', det=['M14.4 18.6V21.8M18.4 18.6V21.8M22.4 18.6V21.8M26.4 18.6V21.8M30.4 18.6V21.8M34 18.6V21.8'], det_w=1.1),
    F(('path', heart_path(24, 30.4, 3.6)), 'peach', 'w', ol=1.3, cut=False),
], '128 mL jar', 'baby')

# peameal bacon: front-view roast (1:3 top ellipse) in a stippled cornmeal coat, one cut slice leaning on it
replace('peameal', 'Peameal bacon', [
    F(cyl(6.6, 30.6, 17.4, 42, 4), 'lemon', 'p', band=.2,
      tex=['M9.8 23.8h.1M14 25.6h.1M19 23.8h.1M24 25.6h.1M27.6 23.8h.1M11 28.6h.1M16.4 30.2h.1M21.6 28.6h.1M26.4 30.2h.1M9.8 32.2h.1M20 32.2h.1M14.6 36.2h.1M25 36.2h.1'],
      tex_t='deep', tex_w=2),
    F(('ellipse', 18.6, 17.4, 12, 4), 'lemon', 'm', det=['M8.8 17.4h.1M28.4 17.4h.1M12.6 14.8h.1M24.6 14.8h.1M12.6 20h.1M24.6 20h.1'], det_t='deep', det_w=1.8),
    F(('ellipse', 18.6, 17.4, 8.6, 2.6), 'strawberry', 'p', ol=1.4, cut=False),
    F(('ellipse', 34, 31.4, 7.6, 10, 14), 'lemon', 'm', det=['M28.4 27.6h.1M28.6 36h.1M39.4 27h.1M39.6 35.4h.1M33 22h.1M35 40.8h.1'], det_t='deep', det_w=1.8),
    F(('ellipse', 34, 31.4, 5.2, 7.4, 14), 'strawberry', 'p', ol=1.5, cut=False, band=.26, band_t='m',
      det=['M31.6 30.4Q34 28 36.6 30.8'], det_t='m', det_w=1.6),
], '600 g roast', 'meat')

# celery: three pale U-section stalks fanning out of one base, a big leaf crown, melon puffy band
def stalk(rot):
    tf = f'rotate({rot} 24 42)'
    return [
        *[F(('path', smooth([(24 + dx - 4.6, 15 + dy), (24 + dx - 3.6, 9.6 + dy), (24 + dx, 7 + dy), (24 + dx + 3.8, 9.8 + dy), (24 + dx + 4.8, 15.2 + dy), (24 + dx, 17 + dy)])),
             'melon', 'm', ol=1.6, tf=tf, det=[f'M{fmt(24 + dx)} 16.4V{fmt(10.6 + dy)}'], det_w=1.2) for dx, dy in [(-3.6, 2.2), (3.6, 2.2), (0, -.6)]],
        F(('path', 'M20.2 16.2H27.8L27 39.4Q26.8 42 24 42Q21.2 42 21 39.4Z'), 'melon', 'p', band=.24, tf=tf,
          det=['M22.7 27L23 38.6', 'M25.4 18.6L25 38.6'], det_t='m', det_w=1.4),
    ]


replace('celery', 'Celery', [*stalk(-17), *stalk(17), *stalk(0)], '1 bunch', 'produce')

# maple granola: stand-up pouch (gusseted bottom, crimped top, hang notch), window of oat clusters, cocoa maple leaf
MAPLE_LEAF = ('M24 9.6L25.2 12.2L27.6 11.2L27 14.6L29.2 13.4L29.6 15L31.2 15.2L30.2 17.4L31 18.2L27.4 20.6L27.8 21.8L24.6 21.2V23.8H23.4'
              'V21.2L20.2 21.8L20.6 20.6L17 18.2L17.8 17.4L16.8 15.2L18.4 15L18.8 13.4L21 14.6L20.4 11.2L22.8 12.2Z')
replace('granola', 'Maple granola', [
    F(('path', 'M13.4 9.4H34.6L35.4 31.6C38.6 34 39.6 37.4 38.6 39.6Q37.8 42 35 42H13Q10.2 42 9.4 39.6C8.4 37.4 9.4 34 12.6 31.6Z'), 'lemon', 'p', band=.18),
    F(('path', 'M13.2 6.6H34.8Q36 6.6 36 7.8V11.2Q36 12.4 34.8 12.4H13.2Q12 12.4 12 11.2V7.8Q12 6.6 13.2 6.6Z'), 'lemon', 'm',
      det=['M15.4 7.6V11.4M18.4 7.6V11.4M21.4 7.6V11.4M26.6 7.6V11.4M29.6 7.6V11.4M32.6 7.6V11.4'], det_w=1),
    F(('path', MAPLE_LEAF), 'cocoa', 'm', ol=1.3, cut=False, tf='translate(24 18.6) scale(.68) translate(-24 -16.7)'),
    F(('rect', 15, 25.2, 18, 11.6, 3.4), 'lemon', 'w', ol=1.5, cut=False),
    *[F(('path', blob(x, y, 2.6, 2.1, 6, .22, int(x * 5 + y))), 'cocoa', 'm', ol=1.2, cut=False) for x, y in [(19.4, 29), (24.6, 28.2), (29, 30.6), (22, 33.2), (27, 33.8)]],
], '800 g bag', 'breakfast')

# old cheddar: a wedge; lemon-pastel cut face, peach rind on the back (curved) face, no bite
replace('cheese', 'Old cheddar', [
    F(('path', 'M8.8 38.4L33.2 14.6Q35.4 12.6 37.4 14.4L39.4 16.2Q41.4 18 41.4 21V38.6Q41.4 42 38 42H10.4Q6 42 8.8 38.4Z'), 'peach', 'm', band=.2),
    # the cut face meets the rind's own outline (a second outline beside it read doubled); one rind-coloured line parts
    # it from the curved back
    F(('path', 'M9.2 39.4L32.6 16.2Q34.6 14.6 34.8 17.6V41H10.6Q7.8 41 9.2 39.4Z'), 'lemon', 'p', ol=0, cut=False, band=.26, band_t='m',
      det=['M24.6 30.6h.1M29.4 26.6h.1M30.2 34.6h.1M19.6 36.4h.1'], det_t='#FFFFFF', det_w=1.8),
    LINE('M34.8 17.6V41', 'peach', 1.6, t='tr', cut=False),
], '400 g block', 'dairy')

# liquid honey: a skep-shaped pot (stacked rounded bands), cocoa lid knob, dipper leaning in
replace('honey', 'Liquid honey', [
    TUBE('M31.6 20.4L40.4 6.6', 'cocoa', 2.8, 'p', cut=True),
    F(('ellipse', 41, 5.6, 3, 2.2, -56), 'cocoa', 'p', ol=1.5, det=['M39.6 4.4L42 7.2'], det_w=1),
    F(('path', 'M17.4 12.6C19.4 10.6 28.6 10.6 30.6 12.6C34.4 14.6 34.8 17.4 34.4 18.8C38.4 20.8 38.8 24.6 38.2 26.6C41.4 29 41.4 33.4 40.2 35.6C40.6 39.4 38.6 42 35 42H13C9.4 42 7.4 39.4 7.8 35.6C6.6 33.4 6.6 29 9.8 26.6C9.2 24.6 9.6 20.8 13.6 18.8C13.2 17.4 13.6 14.6 17.4 12.6Z'),
      'lemon', 'm', band=.18, det=['M13.8 19H34.2', 'M9.8 26.8H38.2', 'M7.8 35.4H40.2'], det_t='deep', det_w=1.5),
    F(('rect', 20.4, 6.6, 7.2, 5.6, 2.4), 'cocoa', 'm'),
    F(('path', 'M24 22.4L28.4 24.9V30L24 32.5L19.6 30V24.9Z'), 'lemon', 'w', ol=1.4, cut=False),
], '500 g jar', 'breakfast')
