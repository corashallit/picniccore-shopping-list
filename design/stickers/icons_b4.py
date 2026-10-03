# ---------- batch 4: household, pets, baby, pharmacy, breakfast, seasonal ----------
# laundry detergent jug: grape body, handle cut-out, cap, bubbles pictogram
add('laundry', 'Laundry detergent', [
    F(('rect', 12.4, 5.6, 9.6, 5.6, 1.8), 'grape', 'm', det=['M14.6 7.4V9.6M17.2 7.4V9.6M19.8 7.4V9.6'], det_w=1),
    F(('path', 'M13.4 10.6H21C24 10.6 25.4 12.4 26.4 14.6H33.6C37.4 14.6 39.4 17 39.4 20.6V38.4Q39.4 42 35.8 42H12.2Q8.6 42 8.6 38.4V16.6C8.6 13 10.4 10.6 13.4 10.6Z'), 'grape', 'p', band=.2),
    F(('path', 'M28.4 18.2H33.6Q35.8 18.2 35.8 20.4V23Q35.8 25.2 33.6 25.2H28.4Z'), 'grape', 'w', ol=1.6, cut=False),
    F(('circle', 18.6, 30.6, 4.6), 'ramune', 'w', ol=1.5, cut=False), F(('circle', 26, 33.6, 3.2), 'ramune', 'w', ol=1.4, cut=False), F(('circle', 25, 26.2, 2.2), 'ramune', 'w', ol=1.3, cut=False),
], gloss=[G('M11.8 17.4V25'), DOT(11.8, 28.4)], qty='2.95 L jug', aisle='household')

# paper towel: tall ramune-traced roll with a hanging sheet
add('papertowel', 'Paper towel', [
    F(cyl(9.6, 31.6, 10.6, 41.6, 3.4), 'ramune', 'w', band=.18, band_t='p', tex=['M12 18q2.4-2 4.8 0t4.8 0t4.8 0t4.8 0', 'M12 25.6q2.4-2 4.8 0t4.8 0t4.8 0t4.8 0', 'M12 33.2q2.4-2 4.8 0t4.8 0t4.8 0t4.8 0'], tex_t='p', tex_w=1.8),
    F(ell_top(9.6, 31.6, 10.6, 3.4), 'ramune', 'w'),
    F(('ellipse', 20.6, 10.6, 3.6, 1.2), 'ramune', 'm', ol=1.3, cut=False),
    F(('path', 'M29.4 16.4H36.4Q39 16.4 39 19V38L37 36.6L35 38L33 36.6L31 38L29.4 37.2Z'), 'ramune', 'w', ol=1.8, det=['M30.6 27H37.8'], det_t='m', det_w=1.4),
], gloss=[G('M13.2 17V24'), DOT(13.2, 27.4)], qty='6 rolls', aisle='household')

# garbage bags: a tied periwinkle bag
add('garbagebags', 'Garbage bags', [
    F(('path', 'M19.4 12.6L15.4 6.6Q14.8 5.4 16.2 5.6L24 7.6L31.8 5.6Q33.2 5.4 32.6 6.6L28.6 12.6Z'), 'periwinkle', 'm', ol=1.8),
    F(('path', 'M19.6 12.4C12.4 15.6 7.6 22.6 7.6 30.4C7.6 37.4 12 42 18 42H30C36 42 40.4 37.4 40.4 30.4C40.4 22.6 35.6 15.6 28.4 12.4Z'), 'periwinkle', 'm', band=.22, det=['M20 18.4Q17.4 24 18.4 30', 'M30.2 20Q32 25.4 30.6 31'], det_t='p', det_w=1.6),
    F(('rect', 18.6, 10.6, 10.8, 4, 2), 'periwinkle', 'p'),
], gloss=[G('M11.6 28Q12.2 22.4 16 18.4'), DOT(11.2, 31.6)], qty='40 bags', aisle='household')

# sponge: lemon sponge with melon scrub layer and melon-traced bubbles
add('sponge', 'Sponges', [
    F(('rect', 7.6, 22.4, 32.8, 19.6, 4), 'lemon', 'p', band=.24, tex=['M13 29h.1M19.6 32.4h.1M26.4 28.4h.1M33.4 31.4h.1M16.4 36.4h.1M29.6 35.6h.1'], tex_t='m', tex_w=2.4),
    F(('rect', 7.6, 17.4, 32.8, 7, 3), 'melon', 'm'),
    F(('circle', 32.6, 11.2, 4.4), 'melon', 'w', ol=1.5), F(('circle', 23.6, 9.6, 2.8), 'melon', 'w', ol=1.4), F(('circle', 39.4, 6.6, 2.4), 'melon', 'w', ol=1.3),
], gloss=[G('M11 27.6V33'), DOT(11, 36), G('M30.6 9.8Q31 8.6 32.4 8.2', 1.4)], qty='3 pack', aisle='household')

# light bulb: lemon glass, filament, periwinkle screw base
add('lightbulb', 'LED light bulbs', [
    F(('path', 'M24 5.6C31.6 5.6 37 11 37 18C37 22.6 34.6 25.4 32.4 27.8C31 29.4 30.6 31 30.6 33H17.4C17.4 31 17 29.4 15.6 27.8C13.4 25.4 11 22.6 11 18C11 11 16.4 5.6 24 5.6Z'), 'lemon', 'p', band=.18, band_t='m',
      det=['M20.6 32.6V24.6L22.4 21.6L24 24.6L25.6 21.6L27.4 24.6V32.6'], det_t='m', det_w=1.5),
    F(('rect', 16.4, 32.4, 15.2, 8, 2.6), 'periwinkle', 'p', det=['M16.6 35H31.4', 'M16.6 37.8H31.4'], det_t='m', det_w=1.4),
    F(('path', 'M20.4 40H27.6L26.4 42.6Q26 43.6 25 43.6H23Q22 43.6 21.6 42.6Z'), 'periwinkle', 'm', ol=1.6),
], gloss=[G('M15.4 16.4Q16.4 11.4 21 9.4'), DOT(14.8, 20)], qty='4 pack', aisle='household')

# batteries: two AA cells, periwinkle bodies, lemon tops
def cell(x):
    return [F(('rect', x - 1.6, 7.2, 3.2, 3.4, 1), 'lemon', 'm', ol=1.5),
            F(('rect', x - 5.4, 9.6, 10.8, 32.4, 3), 'periwinkle', 'm', band=.18),
            F(('rect', x - 5.4, 9.6, 10.8, 9, 3), 'lemon', 'm', ol=2, cut=False),
            LINE(f'M{fmt(x - 2)} 30H{fmt(x + 2)}', 'periwinkle', 1.8, t='#FFFFFF', cut=False)]
add('batteries', 'AA batteries', [*cell(30.6), *cell(17.4)], gloss=[G('M14.6 22V27', 2.2), DOT(14.6, 34), G('M27.8 22V27', 2.2)], qty='8 pack', aisle='household')

# dry cat food: grape bag, fish pictogram, mid kibble band
add('kibble', 'Dry cat food', [
    F(('path', 'M12 12.4H36L38 16C40 23 40 33 38.6 38.6Q37.8 42 34.4 42H13.6Q10.2 42 9.4 38.6C8 33 8 23 10 16Z'), 'grape', 'p', band=.27,
      tex=['M13 36h.1M17.4 38.6h.1M21.6 35.8h.1M26 38.4h.1M30.4 36h.1M34.8 38.4h.1'], tex_t='p', tex_w=2.8),
    F(('rect', 10.6, 8.2, 26.8, 5.4, 2.2), 'grape', 'm', det=['M13 10.9H35'], det_t='p', det_w=1.2),
    F(('path', 'M15.6 23.6C18.2 19.6 26.2 19.2 30 23.6C26.2 28 18.2 27.6 15.6 23.6Z'), 'grape', 'w', ol=1.5, cut=False, det=['M19.4 23h.1'], det_w=1.8),
    F(('path', 'M29.6 23.6L34 20.6V26.6Z'), 'grape', 'w', ol=1.5, cut=False),
], gloss=[G('M12.6 18.6V26'), DOT(12.6, 29.4)], qty='7 kg bag', aisle='pets')

# cat litter: mint pail with handle and paw print
add('litter', 'Cat litter', [
    TUBE('M12.6 17.4C12.6 6.6 35.4 6.6 35.4 17.4', 'mint', 3.4, 'm'),
    F(('path', 'M9.4 16.6H38.6L36.4 38.8Q36 42 32.8 42H15.2Q12 42 11.6 38.8Z'), 'mint', 'p', band=.22),
    F(('rect', 7.6, 14.6, 32.8, 5.2, 2.6), 'mint', 'm'),
    F(('ellipse', 24, 31.6, 4.4, 3.6), 'mint', 'm', ol=1.3, cut=False),
    *[F(('circle', x, y, 1.7), 'mint', 'm', ol=1.1, cut=False) for x, y in [(18.6, 26.4), (22, 24), (26, 24), (29.4, 26.4)]],
], gloss=[G('M13.6 22L14.4 30', 2.4), DOT(14.8, 33.4)], qty='9 kg pail', aisle='pets')

# dog treats: big cocoa bone biscuit
add('dogtreats', 'Dog biscuits', [
    F(('path', 'M13.4 19.4C10 15.4 4.6 18.4 6.6 22.6C3.6 25 6 30.6 10.6 29.2C12.2 28.6 13.2 27.6 14.4 27.4H33.6C34.8 27.6 35.8 28.6 37.4 29.2C42 30.6 44.4 25 41.4 22.6C43.4 18.4 38 15.4 34.6 19.4C33.8 20.2 33.4 20.4 32.6 20.4H15.4C14.6 20.4 14.2 20.2 13.4 19.4Z'),
      'cocoa', 'p', band=.3, tex=['M17.6 23.8h.1M22.6 23.6h.1M27.6 23.8h.1M31.4 24.6h.1'], tex_t='m', tex_w=2),
    F(('path', 'M15.6 32.4C13.2 30.4 9.6 32 10.8 35C9.2 36.6 10.8 40 13.6 39.2C14.6 38.8 15.2 38.4 16 38.2H27.4C28.2 38.4 28.8 38.8 29.8 39.2C32.6 40 34.2 36.6 32.6 35C33.8 32 30.2 30.4 27.8 32.4C27.2 32.8 26.8 33 26.4 33H17C16.6 33 16.2 32.8 15.6 32.4Z'),
      'cocoa', 'm', band=.3),
], gloss=[G('M8.6 21.4Q9.2 19.6 11 19.2', 2), G('M17.4 22H25', 2)], qty='900 g box', aisle='pets')

# diapers: white diaper with ramune tabs and a sakura heart print
add('diapers', 'Diapers, size 3', [
    F(('path', 'M7.6 12.6H40.4Q41.4 12.6 41.2 13.8L39.6 21.6C36.4 24.6 32.4 28.2 31.6 34.6Q31.2 40 26.4 40.6H21.6Q16.8 40 16.4 34.6C15.6 28.2 11.6 24.6 8.4 21.6L6.8 13.8Q6.6 12.6 7.6 12.6Z'), 'ramune', 'w', band=.24, band_t='p'),
    F(('rect', 5, 13.4, 7.4, 6, 2.4), 'ramune', 'm', ol=1.6), F(('rect', 35.6, 13.4, 7.4, 6, 2.4), 'ramune', 'm', ol=1.6),
    F(('path', heart_path(24, 23.6, 3.6)), 'sakura', 'm', ol=1.3, cut=False),
    LINE('M13.2 16H34.8', 'ramune', 1.4, t='p', cut=False),
], gloss=[G('M12.4 20.4Q14.6 22.4 16.4 25.6', 2.2)], qty='84 pack', aisle='baby')

# baby food: small jar, peach lid, puree, tiny spoon
add('babyfood', 'Baby food', [
    TUBE('M33.4 32.6L41.6 13.6', 'ramune', 2.6, 'p'),
    F(('ellipse', 41.6, 11.4, 3.4, 4.4, 22), 'ramune', 'p', ol=1.6),
    F(('path', 'M15.6 21H32.4Q35.4 21 35.4 24V38.4Q35.4 42 31.8 42H16.2Q12.6 42 12.6 38.4V24Q12.6 21 15.6 21Z'), 'peach', 'p', band=.36, band_t='m'),
    F(('rect', 11.4, 14.6, 25.2, 7.6, 2.6), 'peach', 'm', det=['M14.4 16.6V20.2M18.4 16.6V20.2M22.4 16.6V20.2M26.4 16.6V20.2M30.4 16.6V20.2M34 16.6V20.2'], det_w=1.1),
    F(('path', heart_path(24, 29, 3.6)), 'peach', 'w', ol=1.3, cut=False),
], gloss=[G('M15.6 25V31', 2.2), DOT(15.6, 34)], qty='128 mL jar', aisle='baby')

# wipes: mint soft pack, flip lid, a wipe popping out
add('wipes', 'Baby wipes', [
    F(('path', 'M10.6 18.6H37.4Q41 18.6 41 22.2V38.4Q41 42 37.4 42H10.6Q7 42 7 38.4V22.2Q7 18.6 10.6 18.6Z'), 'mint', 'p', band=.22),
    F(('rect', 14.6, 18.4, 18.8, 8.4, 4), 'mint', 'm', ol=1.8, cut=False),
    F(('path', 'M18.4 20.6C17.4 15.4 19.6 9.4 24.4 8.4C26.8 11.6 30.4 12.4 32.6 11.4C32.8 15.6 30.4 19 29.4 20.6Z'), 'mint', 'w', ol=1.6, det=['M22.4 14.6Q25 15.8 27.4 14.4'], det_t='m', det_w=1.2),
    F(('path', heart_path(24, 33.4, 3.6)), 'sakura', 'm', ol=1.3, cut=False),
], gloss=[G('M10.6 24V30'), DOT(10.6, 33.4)], qty='80 wipes', aisle='baby')

# vitamins: amber peach bottle, white cap, a capsule and a white tablet in front
add('vitamins', 'Multivitamins', [
    F(('rect', 13.4, 6, 17.2, 7.6, 2.6), 'peach', 'w', det=['M16 7.8V11.8M19.4 7.8V11.8M22 7.8V11.8M24.6 7.8V11.8M28 7.8V11.8'], det_t='p', det_w=1.1),
    F(('path', 'M14.6 12.8H29.4Q32.6 12.8 32.6 16V35H11.4V16Q11.4 12.8 14.6 12.8Z'), 'peach', 'm', band=.18),
    F(('rect', 14, 18.4, 16, 10.6, 2.4), 'peach', 'w', ol=1.5, cut=False),
    F(('path', 'M17.6 23.6C17.6 22 18.8 21 20.2 21H23.8C25.2 21 26.4 22 26.4 23.6C26.4 25.2 25.2 26.2 23.8 26.2H20.2C18.8 26.2 17.6 25.2 17.6 23.6Z'), 'peach', 'm', ol=1.2, cut=False, det=['M22 21.2V26'], det_w=1),
    F(('path', 'M14 36.2C14 33.4 16 31.8 18.2 31.8H23V40.6H18.2C16 40.6 14 39 14 36.2Z'), 'sakura', 'm', ol=1.6),
    F(('path', 'M23 31.8H27.8C30 31.8 32 33.4 32 36.2C32 39 30 40.6 27.8 40.6H23Z'), 'sakura', 'w', ol=1.6),
    F(('circle', 37.6, 37.4, 4.2), 'peach', 'w', ol=1.6),
], gloss=[G('M14.4 17.6V20', 2), G('M16 34.4H19.4', 1.6)], qty='120 tablets', aisle='pharmacy')

# bandages: two crossed peach strips, white pads, dot holes
BAND = 'M-16 -5H16Q19.6 -5 19.6 -1.4V1.4Q19.6 5 16 5H-16Q-19.6 5 -19.6 1.4V-1.4Q-19.6 -5 -16 -5Z'
add('bandages', 'Bandages', [
    F(('path', BAND), 'peach', 'p', tf='translate(24 24) rotate(-40)', band=.3, sband=False, tex=['M-15 -1.6h.1M-12 1.6h.1M12 -1.6h.1M15 1.6h.1'], tex_t='m', tex_w=1.6),
    F(('rect', -6, -4, 12, 8, 2), 'peach', 'w', tf='translate(24 24) rotate(-40)', ol=1.4, cut=False, tex=['M-3 -1.4h.1M0 1.4h.1M3 -1.4h.1'], tex_t='p', tex_w=1.6),
    F(('path', BAND), 'peach', 'p', tf='translate(24 25) rotate(40)', band=.3, sband=False, tex=['M-15 -1.6h.1M-12 1.6h.1M12 -1.6h.1M15 1.6h.1'], tex_t='m', tex_w=1.6),
    F(('rect', -6, -4, 12, 8, 2), 'peach', 'w', tf='translate(24 25) rotate(40)', ol=1.4, cut=False, tex=['M-3 -1.4h.1M0 1.4h.1M3 -1.4h.1'], tex_t='p', tex_w=1.6),
], gloss=[G('M-15 -2.4H-8', 2, 'translate(24 24) rotate(-40)')], qty='30 strips', aisle='pharmacy')

# tissues: mint box with flowers, a tissue puff
add('tissues', 'Facial tissues', [
    F(('path', 'M18.6 18C16.8 12.6 19.4 7 24 6C26 9.4 29.6 9.8 32 8.6C32.8 12.8 31 16.6 29.6 18.6Z'), 'mint', 'w', ol=1.7, det=['M22.4 12.4Q25.4 14.2 28.6 12.2'], det_t='m', det_w=1.2),
    F(('rect', 7.6, 17.4, 32.8, 24.6, 3.6), 'mint', 'p', band=.22),
    F(('rect', 15.6, 16.8, 16.8, 4.6, 2.3), 'mint', 'm', ol=1.5, cut=False),
    LINE('M10.6 31.6q3.4-3 6.8 0t6.8 0t6.8 0t6.8 0', 'mint', 2.6, t='m', cut=False),
], gloss=[G('M11 21.6V27'), DOT(11, 30)], qty='6 boxes', aisle='pharmacy')

# shampoo: sakura bottle, flip cap, bubbles
add('shampoo', 'Shampoo', [
    F(('rect', 17.6, 5.4, 12.8, 6.6, 2.4), 'sakura', 'm', det=['M17.8 8.8H30.2'], det_t='p', det_w=1.2),
    F(('path', 'M16.6 11.4H31.4C34 11.4 35.4 13.6 35.4 16.4V38.4Q35.4 42 31.8 42H16.2Q12.6 42 12.6 38.4V16.4C12.6 13.6 14 11.4 16.6 11.4Z'), 'sakura', 'p', band=.2),
    F(('path', 'M24 21.8C26.6 25.2 28 27.2 28 29.4C28 31.8 26.2 33.4 24 33.4C21.8 33.4 20 31.8 20 29.4C20 27.2 21.4 25.2 24 21.8Z'), 'sakura', 'w', ol=1.4, cut=False),
    F(('circle', 38.4, 22.4, 3), 'ramune', 'w', ol=1.4), F(('circle', 41, 15.6, 2), 'ramune', 'w', ol=1.2),
], gloss=[G('M16 17.4V27'), DOT(16, 30.4)], qty='400 mL', aisle='pharmacy')

# bar soap: ramune bar with embossed heart and bubbles
add('soap', 'Bar soap', [
    F(('rect', 6.6, 22.6, 34.8, 19.4, 7), 'ramune', 'p', band=.26),
    F(('path', heart_path(24, 31.6, 4.6)), 'ramune', 'm', ol=1.4, cut=False),
    F(('circle', 15.6, 14.4, 4.8), 'ramune', 'w', ol=1.5), F(('circle', 27, 11, 3.2), 'ramune', 'w', ol=1.4), F(('circle', 34.6, 16.6, 2.4), 'ramune', 'w', ol=1.3),
], gloss=[G('M11 27.6Q11.6 25.4 13.8 25', 2.4), DOT(17, 25), G('M13.4 13Q14 11.6 15.4 11.2', 1.4)], qty='4 bars', aisle='pharmacy')

# sunscreen: lemon tube standing on its peach cap, sun pictogram
add('sunscreen', 'Sunscreen SPF 30', [
    F(('path', 'M14 6.6H34L33.4 9.4H14.6Z'), 'lemon', 'm', ol=1.8),
    F(('path', 'M14.6 8.8H33.4L31 32.6H17Z'), 'lemon', 'p', band=.18, band_t='m'),
    F(('rect', 15.4, 32, 17.2, 10, 3), 'peach', 'm', band=.3),
    *[F(('rect', 23.2, 14, 1.6, 2.6, .8), 'peach', 'm', ol=0, cut=False, tf=f'rotate({a} 24 21.6)') for a in range(0, 360, 45)],
    F(('circle', 24, 21.6, 3.6), 'peach', 'm', ol=1.4, cut=False),
], gloss=[G('M17.6 12.6L18.6 24', 2.2), DOT(19, 27.4, .9)], qty='240 mL tube', aisle='pharmacy')

# oatmeal canister: cocoa cylinder, lemon label with oat sprig
add('oatmeal', 'Large-flake oats', [
    F(cyl(11, 37, 12.6, 42, 3.4), 'cocoa', 'p', band=.16),
    F(('rect', 11.9, 19.6, 24.2, 15.6), 'lemon', 'p', ol=0, cut=False, det=['M11.9 19.6H36.1', 'M11.9 35.2H36.1'], det_w=1.4),
    F(ell_top(11, 37, 12.6, 3.4), 'cocoa', 'm'),
    LINE('M24 32.8V22.6', 'cocoa', 1.6, t='m', cut=False),
    *[F(('ellipse', 24 + dx, y, 1.6, 2.6, a), 'cocoa', 'm', ol=1.1, cut=False) for dx, y, a in [(-2.4, 25.6, -36), (2.4, 25.6, 36), (-2.4, 29.4, -36), (2.4, 29.4, 36), (0, 22, 0)]],
], gloss=[G('M14 21V30'), DOT(14, 33.4), G('M15 11.4Q18 9.8 21.4 9.6', 1.8)], qty='1 kg canister', aisle='breakfast')

# granola: lemon stand-up pouch, zip line, window of cocoa clusters
add('granola', 'Maple granola', [
    F(('path', 'M11.6 9.6H36.4L36.8 36C37.8 37.6 37.4 42 34.6 42H13.4C10.6 42 10.2 37.6 11.2 36Z'), 'lemon', 'p', band=.16),
    LINE('M12.6 14.6H35.4', 'lemon', 1.6, t='m', cut=False),
    F(('rect', 15.6, 19, 16.8, 15.4, 3.2), 'lemon', 'w', ol=1.5, cut=False),
    *[F(('path', blob(x, y, 2.4, 2, 6, .22, int(x * 5 + y))), 'cocoa', 'm', ol=1.2, cut=False) for x, y in [(19.6, 25), (25.4, 23.6), (28.6, 29), (21.8, 30.2)]],
], gloss=[G('M14 18V24', 2.2), DOT(14, 27.2)], qty='800 g bag', aisle='breakfast')

# pumpkin pie slice: peach filling, cocoa crust, whipped dollop (seasonal sheet)
add('pumpkinpie', 'Pumpkin pie', [
    F(('path', 'M6.8 33.6L36.6 16.4Q39.4 15 40.4 18L41.2 33.6Z'), 'peach', 'm', band=.3),
    F(('path', 'M6.8 33.6H41.2V38.8Q41.2 42 38 42H10Q6.8 42 6.8 38.8Z'), 'cocoa', 'p', band=.36, band_t='m', det=['M12 37.6h.1M18 38h.1M24 37.6h.1M30 38h.1M36 37.6h.1'], det_w=1.8),
    F(('path', 'M31 19.6C29.6 16.6 31.4 13.6 34 13.2C34.6 10.6 37.6 10.2 38.6 12.2C40.8 12.6 41.4 15.6 40 17.4C38 19.4 33.4 20.4 31 19.6Z'), 'peach', 'w', ol=1.6),
], gloss=[G('M12 31.4L22 25.6', 2.2), DOT(24.4, 24.2)], qty='seasonal', aisle='bakery')
