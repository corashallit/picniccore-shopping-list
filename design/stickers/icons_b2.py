# ---------- batch 2: condiments, snacks, canned, pantry ----------
def squeeze(fam, label_fam=None, cap='w', nozzle=False):
    """tapered squeeze bottle body + cap; returns parts"""
    lf = label_fam or fam
    parts = [F(('path', 'M15 19.4C15 15.4 17.4 13.4 20.4 13.4H27.6C30.6 13.4 33 15.4 33 19.4V38.4Q33 42 29.4 42H18.6Q15 42 15 38.4Z'), fam, 'm', band=.2),
             F(('rect', 18.6, 8.4, 10.8, 6.4, 2.2), fam, cap)]
    if nozzle:
        parts.insert(1, F(('path', 'M22.4 9L23.4 3.8Q24 2.6 24.6 3.8L25.6 9Z'), fam, cap, ol=1.6))
    return parts

# ketchup: strawberry squeeze bottle, white flip cap, tomato pictogram on a white oval label
add('ketchup', 'Ketchup', [
    *squeeze('strawberry', nozzle=True),
    F(('rect', 18.2, 21.6, 11.6, 13.4, 3), 'strawberry', 'w', ol=1.5, cut=False),
    F(('circle', 24, 29.4, 3.8), 'strawberry', 'm', ol=1.4, cut=False),
    F(('path', star_path(24, 25.8, 2.6, 1.1, 5)), 'melon', 'm', ol=1, cut=False),
], gloss=[G('M18 19.4Q18.2 17 20 16.2', 2.2), G('M18 23V31'), DOT(18, 34.2)], qty='750 mL bottle', aisle='pantry')

# yellow mustard: lemon squeeze bottle, pointed nozzle, drizzle squiggle
add('mustard', 'Yellow mustard', [
    *squeeze('lemon', cap='p', nozzle=True),
    F(('rect', 18.4, 22.6, 11.2, 11, 3), 'lemon', 'w', ol=1.5, cut=False, det=['M20.6 28.6Q22.2 25.4 24 28.6T27.4 28.6'], det_t='m', det_w=1.8),
], gloss=[G('M18 19.4Q18.2 17 20 16.2', 2.2), G('M17.6 24V31'), DOT(17.6, 34.2)], qty='400 mL bottle', aisle='pantry')

# olive oil: tall melon glass bottle, cork, olive sprig label
add('oliveoil', 'Olive oil', [
    F(('rect', 20.6, 4.6, 6.8, 4.6, 1.6), 'cocoa', 'm'),
    F(('path', 'M21 8.6H27V16.4C27 18.4 33.4 20.8 33.4 26V38.8Q33.4 42 30.2 42H17.8Q14.6 42 14.6 38.8V26C14.6 20.8 21 18.4 21 16.4Z'), 'melon', 'm', band=.18),
    F(('rect', 17, 25.4, 14, 11.6, 2.6), 'melon', 'w', ol=1.5, cut=False),
    TUBE('M19.6 33.6Q24 31 28.4 27.6', 'melon', 2.4, 'tr', cut=False),
    F(('ellipse', 22.4, 29.2, 1.7, 2.4, -40), 'melon', 'p', ol=1.2, cut=False),
    F(('ellipse', 26.6, 31.8, 1.7, 2.4, 40), 'melon', 'p', ol=1.2, cut=False),
], gloss=[G('M17.4 25Q17.8 22.2 20 20.8', 2.2), G('M17.4 28.6V35'), DOT(17.4, 38)], qty='1 L bottle', aisle='pantry')

# soy sauce: classic dispenser, cocoa body, strawberry cap with spout
add('soysauce', 'Soy sauce', [
    F(('path', 'M20.4 9.6L21.4 4.8Q21.8 3.6 23 3.8L27.8 5.2Q28.6 5.6 28 6.4L25.8 9.6Z'), 'strawberry', 'm', ol=1.6),
    F(('path', 'M18.6 9H29.4Q31.4 9 31.4 11V13.6H16.6V11Q16.6 9 18.6 9Z'), 'strawberry', 'm'),
    F(('path', 'M17 13.4H31C35 17.4 36 22 36 27.6V38.4Q36 42 32.4 42H15.6Q12 42 12 38.4V27.6C12 22 13 17.4 17 13.4Z'), 'cocoa', 'm', band=.2, band_t='deep'),
    F(('rect', 16.6, 24, 14.8, 10, 2.4), 'cocoa', 'w', ol=1.5, cut=False),
    F(('path', 'M24 26.4C25.6 28.6 26.6 30 26.6 31.4C26.6 32.8 25.4 33.6 24 33.6C22.6 33.6 21.4 32.8 21.4 31.4C21.4 30 22.4 28.6 24 26.4Z'), 'cocoa', 'm', ol=1.2, cut=False),
], gloss=[G('M15.6 22Q16.2 18.8 18.6 17', 2.4), DOT(15.2, 25.4)], qty='500 mL bottle', aisle='pantry')

def jar(fam, lidfam, lid_t='m', body_t='p', lab=True, wide=False):
    x0, x1 = (8.6, 39.4) if wide else (10.4, 37.6)
    parts = [F(('path', f'M{x0 + 2.6} 16.6H{x1 - 2.6}Q{x1} 16.6 {x1} 19.4V38Q{x1} 42 {x1 - 4} 42H{x0 + 4}Q{x0} 42 {x0} 38V19.4Q{x0} 16.6 {x0 + 2.6} 16.6Z'), fam, body_t, band=.2),
             F(('rect', x0 - 1.4, 9.6, x1 - x0 + 2.8, 8.2, 2.8), lidfam, lid_t, det=[f'M{x0 + 3} 11.6V15.8M{x0 + 8} 11.6V15.8M{x0 + 13} 11.6V15.8M{x0 + 18} 11.6V15.8M{x0 + 23} 11.6V15.8M{x0 + 28} 11.6V15.8'] if wide else [], det_w=1.2)]
    if lab:
        parts.append(F(('rect', x0 + 3.2, 22.4, x1 - x0 - 6.4, 13.6, 3), fam, 'w', ol=1.5, cut=False))
    return parts

# mayo: white jar, ramune lid, egg pictogram
add('mayo', 'Mayonnaise', [
    *jar('ramune', 'ramune', body_t='w', wide=True),
    F(('ellipse', 24, 29.4, 4.6, 5.4), 'lemon', 'w', ol=1.4, cut=False),
    F(('circle', 24, 30.6, 2.4), 'lemon', 'm', ol=1.1, cut=False),
], gloss=[G('M12.2 22V31', 2.4), DOT(12.2, 34.2), G('M10 12.2H16', 2)], qty='890 mL jar', aisle='pantry')

# honey: lemon-mid jar, cocoa lid, hex label, dipper leaning
add('honey', 'Liquid honey', [
    TUBE('M33.6 22L40.4 6.6', 'cocoa', 2.8, 'p', cut=True),
    F(('ellipse', 41, 5.6, 3, 2.2, -66), 'cocoa', 'p', ol=1.5, det=['M39.6 4.4L42 7.2'], det_w=1),
    *jar('lemon', 'cocoa', body_t='m', lab=False),
    F(('path', 'M24 22.6L29.4 25.7V31.9L24 35L18.6 31.9V25.7Z'), 'lemon', 'w', ol=1.5, cut=False, det=['M21.8 27.6L24 26.4L26.2 27.6V30.2L24 31.4L21.8 30.2Z'], det_t='m', det_w=1.4),
    F(('path', 'M10.6 16.6H37.4V20Q34.6 22.6 32.6 20.6Q30.6 23.8 28 20.8Q26 22.6 24 20.6Q21.4 23.2 19 20.6Q16.4 22.4 14.6 20.6Q12.6 22.6 10.6 20Z'), 'lemon', 'p', ol=1.5, cut=False),
], gloss=[G('M13.6 23.6V32', 2.4), DOT(13.6, 35)], qty='500 g jar', aisle='breakfast')

# peanut butter: jar, strawberry lid, peanut pictogram
add('peanutbutter', 'Peanut butter', [
    *jar('cocoa', 'strawberry', body_t='m', wide=True),
    F(('path', 'M20 25.4C20 22.8 22 21.6 24 21.6C26 21.6 28 22.8 28 25.4C28 26.8 27 27.6 27 28.8C27 30 28 30.8 28 32.2C28 34.8 26 36 24 36C22 36 20 34.8 20 32.2C20 30.8 21 30 21 28.8C21 27.6 20 26.8 20 25.4Z'),
      'cocoa', 'p', ol=1.4, cut=False, det=['M22.4 25h.1M25.6 26.4h.1M22.6 32.4h.1M25.4 31.6h.1'], det_t='m', det_w=1.6),
], gloss=[G('M12.2 22V31', 2.4), DOT(12.2, 34.2), G('M10 12.2H16', 2)], qty='1 kg jar', aisle='pantry')

# hot sauce: slim strawberry bottle, melon cap, chili pictogram
add('hotsauce', 'Hot sauce', [
    F(('rect', 20.2, 3.8, 7.6, 7.4, 2), 'melon', 'm', det=['M22.4 5.6V9.4M25.6 5.6V9.4'], det_w=1),
    F(('path', 'M21 10.6H27V15C30.6 17 31.6 20 31.6 23.6V38.4Q31.6 42 28 42H20Q16.4 42 16.4 38.4V23.6C16.4 20 17.4 17 21 15Z'), 'strawberry', 'm', band=.2),
    F(('rect', 18.8, 24, 10.4, 12, 2.6), 'strawberry', 'w', ol=1.5, cut=False),
    F(('path', 'M22 27.6C25.4 27.4 27.4 29.4 26.6 33.4C26.4 34.4 25.2 34.6 24.8 33.6C24 31.4 22.6 29.8 21.4 29.4Z'), 'strawberry', 'm', ol=1.2, cut=False),
    LINE('M21.8 28.4C21.2 27.2 21.4 26.2 22.6 25.6', 'melon', 1.4, cut=False),
], gloss=[G('M19 21.6Q19.4 19.2 21.2 18', 2.2), DOT(18.8, 25.2, .8)], qty='150 mL bottle', aisle='pantry')

# potato chips: pillow bag with crimped ends, chips in a window
CRIMP = 'M12 10.6V13.4M16 10.6V13.4M20 10.6V13.4M24 10.6V13.4M28 10.6V13.4M32 10.6V13.4M36 10.6V13.4'
add('chips', 'Potato chips', [
    F(('path', 'M11.6 12H36.4L35.8 16.4C38 22 38 31.6 35.8 37.4L36.4 41.6H11.6L12.2 37.4C10 31.6 10 22 12.2 16.4Z'), 'ramune', 'p', band=.18),
    F(('rect', 9.4, 9.2, 29.2, 5.6, 1.8), 'ramune', 'm', det=[CRIMP], det_w=1.1),
    F(('rect', 9.4, 37.4, 29.2, 5.6, 1.8), 'ramune', 'm', det=[CRIMP.replace('10.6', '38.8').replace('13.4', '41.6')], det_w=1.1),
    F(('path', blob(20.6, 25.4, 6.4, 5, 9, .16, 4, 20)), 'lemon', 'm', ol=1.5, cut=False, det=['M18 24.4h.1M22.4 27h.1'], det_t='deep', det_w=1.6),
    F(('path', blob(28.6, 28.6, 5.8, 4.6, 9, .16, 8, -10)), 'lemon', 'm', ol=1.5, cut=False),
], gloss=[G('M14.6 18.8Q14 24 14.4 29'), DOT(14.6, 32.4)], qty='235 g bag', aisle='snacks')

# crackers: strawberry box with two square crackers peeking out of the top
add('crackers', 'Crackers', [
    F(('rect', 12.6, 7.6, 14, 14, 2.4), 'cocoa', 'p', tf='rotate(-10 19.6 14.6)', tex=['M16 11h.1M19.6 11h.1M23.2 11h.1M16 14.6h.1M19.6 14.6h.1M23.2 14.6h.1M16 18.2h.1M19.6 18.2h.1M23.2 18.2h.1'], tex_t='m', tex_w=1.8, sband=False),
    F(('rect', 22.4, 9.2, 14, 14, 2.4), 'cocoa', 'p', tf='rotate(8 29.4 16.2)', tex=['M25.8 12.6h.1M29.4 12.6h.1M33 12.6h.1M25.8 16.2h.1M29.4 16.2h.1M33 16.2h.1M25.8 19.8h.1M29.4 19.8h.1M33 19.8h.1'], tex_t='m', tex_w=1.8, sband=False),
    F(('rect', 8.6, 19.4, 30.8, 22.6, 3.6), 'strawberry', 'p', band=.2),
    F(('rect', 14.4, 25, 19.2, 10.4, 2.6), 'strawberry', 'w', ol=1.5, cut=False),
    F(('rect', 19.4, 26.4, 9.2, 7.6, 1.6), 'cocoa', 'p', ol=1.2, cut=False, det=['M22 28.8h.1M24 30.2h.1M26 28.8h.1M22 31.6h.1M26 31.6h.1'], det_t='m', det_w=1.4),
], gloss=[G('M11.8 23.6V31'), DOT(11.8, 34.2)], qty='200 g box', aisle='snacks')

# chocolate-chip cookies: two cookies, one behind
CH = lambda pts: [F(('path', blob(x, y, 1.6, 1.2, 6, .2, int(x * 7))), 'cocoa', 'tr', ol=0, cut=False) for x, y in pts]
add('cookies', 'Chocolate chip cookies', [
    F(('path', blob(30.6, 21.4, 10.6, 10.2, 11, .05, 2)), 'cocoa', 'p', band=.22),
    *CH([(27, 17.4), (33.4, 16), (35.6, 22.4), (29.4, 24)]),
    F(('path', blob(19.4, 30.4, 12.2, 11.6, 11, .05, 5)), 'cocoa', 'p', band=.22),
    *CH([(14.6, 27.2), (21.6, 25.6), (25.4, 31.6), (17.4, 34), (12.2, 33)]),
], gloss=[G('M10.4 27.4Q11.6 22.4 16.4 20.4'), DOT(10.2, 31.2)], qty='300 g bag', aisle='snacks')

# popcorn: striped bucket heaped with white puffs
PUFFS = [(13.6, 16.4, 4.6), (20.4, 13.2, 5), (27.8, 13.2, 5), (34.4, 16.6, 4.6), (17.4, 9.4, 3.6), (24.4, 7.6, 4), (31, 9.6, 3.6)]
add('popcorn', 'Microwave popcorn', [
    *[F(('path', blob(x, y, r, r * .9, 7, .14, int(x * 3 + y))), 'lemon', 'w', ol=1.6, band=.3, band_t='p') for x, y, r in PUFFS],
    F(('path', 'M8.6 18.6H39.4L36 39.2Q35.6 42 32.6 42H15.4Q12.4 42 12 39.2Z'), 'strawberry', 'w', tex=['M15.6 18V42', 'M24 18V42', 'M32.4 18V42'], tex_t='m', tex_w=4.4),
    F(('rect', 7, 16.8, 34, 4.6, 2.3), 'strawberry', 'm'),
], gloss=[G('M10 15Q10.8 12.4 13 11.6', 2.2), G('M12.6 22.6L13.4 30', 2.2)], qty='3 × 85 g', aisle='snacks')

# granola bars: one wrapped (grape), one unwrapped (cocoa, oat dots)
add('granolabar', 'Granola bars', [
    F(('path', 'M7.6 10.4H9.6L10.6 12.4L11.6 10.4L12.6 12.4L13.6 10.4H34.4L35.4 12.4L36.4 10.4L37.4 12.4L38.4 10.4H40.4V24.4H38.4L37.4 22.4L36.4 24.4L35.4 22.4L34.4 24.4H13.6L12.6 22.4L11.6 24.4L10.6 22.4L9.6 24.4H7.6Z'),
      'grape', 'p', tf='rotate(-8 24 17.4)', band=.26, det=['M13.6 12.4V22.4', 'M34.4 12.4V22.4'], det_w=1.3),
    F(('rect', 18.6, 14.6, 10.8, 5.4, 1.6), 'grape', 'w', ol=1.3, cut=False, tf='rotate(-8 24 17.4)'),
    F(('rect', 8, 27.6, 32, 13.4, 3.6), 'cocoa', 'p', band=.26, tex=['M12.4 31h.1M17.6 33h.1M22.8 30.6h.1M28.4 32.6h.1M33.4 30.8h.1M15 36.4h.1M26 35.8h.1M31 36.8h.1M20.4 37h.1'], tex_t='m', tex_w=2),
], gloss=[G('M11.2 30.6H17', 2.2), DOT(19.8, 30.6, .9), G('M10.4 14.6H15', 2)], qty='6 bars', aisle='snacks')

# pretzel: one big twist with salt
PRZ = 'M24 36C17 30 9.6 26.6 9.6 19.4C9.6 13.6 14 10 18.6 10.6C24 11.4 24.4 19.6 24 24C23.6 19.6 24 11.4 29.4 10.6C34 10 38.4 13.6 38.4 19.4C38.4 26.6 31 30 24 36'
add('pretzel', 'Pretzels', [
    TUBE(PRZ, 'cocoa', 6.6, 'm'),
    TUBE('M17.4 40.4L30.6 24.4', 'cocoa', 6.6, 'm'),
    TUBE('M30.6 40.4L17.4 24.4', 'cocoa', 6.6, 'm'),
    LINE('M13 16.4h.1M34.6 15h.1M20.8 14.6h.1M28.8 27.6h.1M16.6 27h.1M31.8 22.4h.1M21 36.8h.1M27 36.8h.1', 'cocoa', 1.8, t='w', cut=False),
], gloss=[G('M11.8 19Q12.2 15.4 15.2 13.6', 2)], qty='300 g bag', aisle='snacks')

# chocolate bar: ramune wrapper, foil, chocolate squares showing at the top
add('chocolate', 'Milk chocolate', [
    F(('rect', 12.4, 6.6, 23.2, 16, 2.4), 'cocoa', 'm', tex=['M12 12.2H36', 'M18.2 6V23', 'M24 6V23', 'M29.8 6V23'], tex_t='deep', tex_w=1.4),
    F(('path', 'M11.6 18.6L14.4 16.6L17 18.6L19.8 16.4L22.4 18.4L25.2 16.4L27.8 18.6L30.6 16.4L33.4 18.4L36.4 16.6V22H11.6Z'), 'periwinkle', 'w', ol=1.4),
    F(('rect', 10.6, 20.6, 26.8, 21.4, 3), 'ramune', 'm', band=.2),
    F(('path', heart_path(24, 31.2, 4.4)), 'ramune', 'w', ol=1.4, cut=False),
], gloss=[G('M13.6 24.6V31', 2.4), DOT(13.6, 34.4), G('M15 9.4H17.4', 1.8)], qty='100 g bar', aisle='snacks')

# canned beans: periwinkle tin, white label, three red kidney beans (two families, §3.1)
def can(fam_tin, fam_lab, x0=10, x1=38, top=14.4, lab_y=19.6, lab_h=16.4, lab_t='p'):
    return [F(cyl(x0, x1, top, 42), fam_tin, 'p', band=.18),
            F(('rect', x0 + .9, lab_y, x1 - x0 - 1.8, lab_h), fam_lab, lab_t, ol=0, cut=False, det=[f'M{x0 + .9} {lab_y}H{x1 - .9}', f'M{x0 + .9} {lab_y + lab_h}H{x1 - .9}'], det_w=1.4),
            F(ell_top(x0, x1, top), fam_tin, 'w'),
            F(('ellipse', (x0 + x1) / 2, top, (x1 - x0) / 2 - 3.4, (x1 - x0) / 6 - 1.2), fam_tin, 'p', ol=1.3, cut=False)]
BEAN = 'M-3.6 -1.4C-3.6 -3.6 -1 -4.2 .6 -3.2C2 -2.2 3.6 -2.8 4.2 -1.2C5 1.2 3.4 3.6 0 3.4C-2.6 3.2 -3.6 1.2 -3.6 -1.4Z'
add('beans', 'Kidney beans', [
    *can('periwinkle', 'periwinkle', lab_t='w'),
    *[F(('path', BEAN), 'strawberry', 'm', ol=1.3, cut=False, tf=f'translate({x} {y}) rotate({r})') for x, y, r in [(19.4, 25.6, -20), (28.4, 25.4, 24), (24, 31, 4)]],
], gloss=[G('M13.4 21V30'), DOT(13.4, 33.4), G('M15.6 13.4Q18 12 21 11.8', 1.8)], qty='540 mL can', aisle='pantry')

# soup: periwinkle tin, peach label, steaming bowl pictogram
add('soup', 'Chicken noodle soup', [
    *can('periwinkle', 'peach', top=12.6, lab_y=19.8, lab_h=16.6),
    F(('path', 'M16.4 28.4H31.6C31.6 32.6 28.4 35 24 35C19.6 35 16.4 32.6 16.4 28.4Z'), 'peach', 'w', ol=1.4, cut=False),
    F(('ellipse', 24, 28.4, 7.6, 1.6), 'peach', 'm', ol=1.3, cut=False),
    LINE('M19.6 25.6C18.6 24.4 20.6 23.2 19.6 21.6M24 25.6C23 24.4 25 23.2 24 21.6M28.4 25.6C27.4 24.4 29.4 23.2 28.4 21.6', 'peach', 1.3, cut=False),
], gloss=[G('M13.4 19.6V29'), DOT(13.4, 32.4), G('M15.6 11.6Q18 10.2 21 10', 1.8)], qty='540 mL can', aisle='pantry')

# tuna: short tin with pull tab, ramune label, fish
add('tuna', 'Flaked tuna', [
    F(cyl(8.6, 39.4, 25.4, 42, 5.2), 'periwinkle', 'p', band=.22),
    F(('rect', 9.5, 29.8, 29, 7), 'ramune', 'p', ol=0, cut=False, det=['M9.6 29.8H38.4', 'M9.6 36.8H38.4'], det_w=1.3),
    F(('path', 'M17.4 33.3C19.6 30.8 25 30.6 27.8 33.3C25 36 19.6 35.8 17.4 33.3Z'), 'ramune', 'm', ol=1.2, cut=False),
    F(('path', 'M27.8 33.3L31.2 31V35.6Z'), 'ramune', 'm', ol=1.2, cut=False),
    F(('ellipse', 24, 25.4, 15.4, 5.2), 'periwinkle', 'w'),
    F(('ellipse', 24, 25.4, 11.4, 3.4), 'periwinkle', 'p', ol=1.2, cut=False),
    F(('rect', 27.6, 22.4, 7, 4, 2), 'periwinkle', 'm', ol=1.3, cut=False),
], gloss=[G('M11.8 31.4V34.6', 2.4), G('M13.8 23Q17 21.2 21 21', 1.8)], qty='170 g can', aisle='pantry')

# spaghetti: tall pack with a window of strands, lemon band
add('spaghetti', 'Spaghetti', [
    F(('path', 'M14.6 9.6Q14.6 6.6 17.6 6.6H30.4Q33.4 6.6 33.4 9.6V38.8Q33.4 42 30.2 42H17.8Q14.6 42 14.6 38.8Z'), 'ramune', 'p', band=.18),
    F(('rect', 18, 12.4, 12, 20.4, 2.6), 'lemon', 'p', ol=1.5, cut=False, tex=['M20.4 12V33', 'M22.8 12V33', 'M25.2 12V33', 'M27.6 12V33'], tex_t='m', tex_w=1.3),
    F(('rect', 13, 34, 22, 4.4, 2.2), 'ramune', 'm', ol=1.5),
], gloss=[G('M17.2 10.6V18', 2.2), DOT(17.2, 21)], qty='900 g pack', aisle='pantry')

# pasta sauce: strawberry jar with a basil leaf
add('pastasauce', 'Tomato pasta sauce', [
    *jar('strawberry', 'lemon', body_t='m'),
    F(('path', 'M18.6 33.6C18 28.4 22.4 24 28.6 24.4C29.2 29.6 24.8 33.8 18.6 33.6Z'), 'melon', 'm', ol=1.4, cut=False, det=['M20.2 32Q23.4 28.6 27 25.8'], det_w=1),
], gloss=[G('M13.6 23.6V32', 2.4), DOT(13.6, 35), G('M11.4 12.2H17', 2)], qty='650 mL jar', aisle='pantry')
