# ---------- batch 1: Ontario specials + produce ----------
# peameal bacon: two round slices on edge, lemon cornmeal rim with dots, pink pork centre
add('peameal', 'Peameal bacon', [
    F(('circle', 30.4, 24.4, 11.6), 'lemon', 'p', tex=[dots_ring(30.4, 24.4, 10, 14, 10)], tex_t='m', tex_w=2.2),
    F(('circle', 30.4, 24.4, 8), 'strawberry', 'p', ol=1.6, cut=False),
    F(('circle', 19.4, 29.4, 12.6), 'lemon', 'p', band=.22, tex=[dots_ring(19.4, 29.4, 10.9, 16, 0)], tex_t='m', tex_w=2.4),
    F(('circle', 19.4, 29.4, 8.8), 'strawberry', 'p', ol=1.8, cut=False, band=.26, band_t='m', det=['M15.4 29.6Q19.4 25.6 23.6 29.4'], det_t='m', det_w=1.8),
], gloss=[G('M10.2 26Q11.2 21.2 15.4 18.8'), DOT(9.8, 29.8), G('M15.4 25.6Q16.6 23.6 18.8 23.2', 1.8)], qty='600 g roast', aisle='meat')

# cheese curds: lemon-pastel bag, four lumpy curds, twist tie
CURDS = [(18.6, 27.6, 4.6, 4, 2), (29.4, 27.2, 4.8, 4.2, 5), (17.4, 35.2, 4.8, 3.8, 9), (28.8, 35.4, 5, 4, 12)]
add('curds', 'Cheese curds', [
    F(('path', 'M20.6 14.6L18.6 8.2Q18.4 6.6 20 7L24 8.6L28 7Q29.6 6.6 29.4 8.2L27.4 14.6Z'), 'lemon', 'w', ol=1.8, det=['M22.8 10.6V13.2', 'M25.2 10.6V13.2'], det_t='m', det_w=1.2),
    F(('path', 'M15.6 17.6C11.4 21.6 9.4 29.6 10 36.4Q10.4 42 16 42H32Q37.6 42 38 36.4C38.6 29.6 36.6 21.6 32.4 17.6Z'), 'lemon', 'p', band=.22),
    *[F(('path', blob(x, y, rx, ry, 7, .16, sd)), 'lemon', 'w', ol=1.5, cut=False) for x, y, rx, ry, sd in CURDS],
    F(('rect', 18.4, 13.4, 11.2, 4.4, 2.2), 'lemon', 'm'),
], gloss=[G('M13.6 26Q14.2 22 16.6 19.6', 2.4), DOT(13.2, 29.6), G('M15.6 26.4Q16.4 24.8 18 24.4', 1.4)], qty='450 g bag', aisle='dairy')

# butter tart: wide peach scalloped shell, glossy cocoa filling pool just above the rim, two raisins, a drip
SH = 'M6.6 27.4' + scallop_top(6.6, 41.4, 27.4, 8, 1.7) + 'L37.6 39Q37 42 34 42H14Q11 42 10.4 39Z'
add('buttertart', 'Butter tarts', [
    F(('path', 'M9 27.6C9.4 22.8 15.6 20.6 24 20.6C32.4 20.6 38.6 22.8 39 27.6Z'), 'cocoa', 'm', band='M5 25.6H43V30H5Z'),
    F(('ellipse', 18.4, 24.4, 2.1, 1.4, -14), 'cocoa', 'tr', ol=0, cut=False),
    F(('ellipse', 30.2, 23.8, 2.1, 1.4, 12), 'cocoa', 'tr', ol=0, cut=False),
    F(('path', SH), 'peach', 'p', band=.26, det=['M14.6 31V38.4', 'M19.8 31.4V39.4', 'M24 31.4V39.6', 'M28.2 31.4V39.4', 'M33.4 31V38.4'], det_t='m', det_w=1.6),
    F(('path', 'M25.4 26.6C26.8 26.6 27.2 28.4 27.2 30.6C27.2 32.2 26.4 33 25.4 33C24.4 33 23.6 32.2 23.6 30.6C23.6 28.4 24 26.6 25.4 26.6Z'), 'cocoa', 'm', ol=1.5),
], gloss=[G('M12.6 24.6Q16 22.2 21.4 22', 2.2), DOT(24.4, 22), G('M11.6 31.4L12.6 36', 2.2)], qty='6 pack', aisle='bakery')

# 3 L peach basket: cocoa woven punnet, three peach-mid peaches, one leaf
def peach(cx, cy, r, crease=True):
    return [F(('circle', cx, cy, r), 'peach', 'm', band=.22, det=[f'M{fmt(cx + r * .1)} {fmt(cy - r * .86)}Q{fmt(cx + r * .5)} {fmt(cy - r * .1)} {fmt(cx + r * .15)} {fmt(cy + r * .6)}'] if crease else [])]
add('peachbasket', 'Peaches', [
    *peach(24, 13.2, 7.6),
    F(('path', 'M24.6 6.4C26.4 2.8 31 2.2 33.6 3.4C32 7 27.6 8.2 24.6 6.4Z'), 'melon', 'm', ol=1.5, det=['M26.4 5.6Q29.6 4.4 32.2 3.8'], det_w=1),
    *peach(14.4, 17, 7.6), *peach(33.6, 17, 7.6),
    F(('path', 'M8.6 23.6H39.4L36.8 39.4Q36.4 42 33.8 42H14.2Q11.6 42 11.2 39.4Z'), 'cocoa', 'p', band=.24,
      tex=['M8 29.2H40', 'M8 34.8H40', 'M14.6 24V42', 'M19.8 24V42', 'M24.8 24V42', 'M29.8 24V42', 'M35 24V42'], tex_w=1.8),
    F(('rect', 6.4, 21, 35.2, 5, 2.5), 'cocoa', 'm'),
], gloss=[G('M9.6 15.6Q10.2 12.6 12.6 11.4', 2.2), G('M19.6 11Q20.4 8.4 22.8 7.6', 2), G('M8.4 23.4H16', 2.2), DOT(19, 23.4, .9)], qty='3 L basket', aisle='produce')

# lettuce: ruffled round head, melon, vein lines, two front leaves
LET = smooth(ruffle(24, 26.4, 16.6, 15, 9, .1, 180, 360) + [(40.6, 32), (37, 39.4), (30, 42), (18, 42), (11, 39.4), (7.4, 32)])
add('lettuce', 'Green leaf lettuce', [
    F(('path', LET), 'melon', 'p', band=.2, det=['M24 14V30', 'M24 21L18.6 16.8', 'M24 24.4L29.6 19.6'], det_t='m', det_w=1.6),
    F(('path', smooth([(7.6, 30), (12, 24.6), (18.4, 26.4), (22, 32), (21.4, 39.2), (16, 42), (10.4, 40), (7.4, 35)])), 'melon', 'm', band=.26, det=['M10.6 30.4Q15 31.6 17.8 36.4'], det_t='p', det_w=1.4),
    F(('path', smooth([(40.4, 30), (36, 24.6), (29.6, 26.4), (26, 32), (26.6, 39.2), (32, 42), (37.6, 40), (40.6, 35)])), 'melon', 'm', band=.26, det=['M37.4 30.4Q33 31.6 30.2 36.4'], det_t='p', det_w=1.4),
], gloss=[G('M11.4 23.4Q12.6 17.6 17.6 14.2'), DOT(19.8, 12.8)], qty='1 head', aisle='produce')

# cucumber + slice
CTF = 'translate(22.2 23) rotate(-28)'
add('cucumber', 'English cucumber', [
    F(('path', 'M-17 -5.4H15.6C18.4 -5.4 20 -3 20 0C20 3 18.4 5.4 15.6 5.4H-17C-19.4 5.4 -20.6 3 -20.6 0C-20.6 -3 -19.4 -5.4 -17 -5.4Z'), 'melon', 'm', band=.28, tf=CTF,
      tex=['M-14 -2.4h.1M-6 -2.6h.1M2 -2.4h.1M10 -2.6h.1M-10 1.8h.1M-2 2h.1M6 1.8h.1M14 1.6h.1'], tex_t='p', tex_w=2),
    F(('rect', -22.4, -1.8, 2.8, 3.6, 1.2), 'melon', 'tr', ol=0, tf=CTF),
    F(('circle', 34, 34.6, 7.4), 'melon', 'm', band=None, ol=2),
    F(('circle', 34, 34.6, 5.4), 'melon', 'p', ol=0, cut=False, tex=[dots_ring(34, 34.6, 2.6, 6, 30)], tex_t='#FFF3A6', tex_w=1.8),
], gloss=[G('M-14 -3H-2', 2.4, CTF), DOT(1.6, -3, .9, CTF), G('M29.4 32.6Q30.2 30.6 32.2 30', 1.8)], qty='1 each', aisle='produce')

# red bell pepper
add('pepper', 'Red bell pepper', [
    TUBE('M24 14.4C24 10.4 25.4 8 28.2 7.2', 'melon', 4.4, 'm'),
    F(('path', 'M14.8 13.8C19 12 21.6 14 24 14C26.4 14 29 12 33.2 13.8C38.8 16.2 40.4 23.6 39.2 30.2C38.4 35 37.4 39.8 34 41.2C31.2 42.4 29.2 40.6 27.2 41.4C25.6 42 22.4 42 20.8 41.4C18.8 40.6 16.8 42.4 14 41.2C10.6 39.8 9.6 35 8.8 30.2C7.6 23.6 9.2 16.2 14.8 13.8Z'),
      'strawberry', 'm', band=.22, det=['M19.8 16.4C17.8 22 18 32.4 20.6 40.6', 'M28.2 16.4C30.2 22 30 32.4 27.4 40.6']),
    F(('path', 'M19 14.8C20.4 12.6 22.2 11.6 24 11.6C25.8 11.6 27.6 12.6 29 14.8C26.4 16.4 21.6 16.4 19 14.8Z'), 'melon', 'm', ol=1.6),
], gloss=[G('M12.4 22.6Q13.2 18.6 16.4 16.8'), DOT(11.8, 26.4)], qty='each', aisle='produce')

# potatoes: three lumpy cocoa potatoes with eyes
add('potatoes', 'Potatoes', [
    F(('path', blob(16, 22.4, 9.4, 7.2, 8, .08, 3, 10)), 'cocoa', 'p', band=.24, det=['M12.4 21h.1', 'M18.8 24.6h.1'], det_t='m', det_w=1.8),
    F(('path', blob(33, 22, 8.6, 7, 8, .08, 6, -12)), 'cocoa', 'p', band=.24, det=['M30.4 21.4h.1', 'M36 23.8h.1'], det_t='m', det_w=1.8),
    F(('path', blob(24.4, 33.4, 13.4, 8.6, 9, .07, 11)), 'cocoa', 'p', band=.24, det=['M18 32.6h.1', 'M25.6 30.6h.1', 'M30.6 35h.1', 'M22.4 36.6h.1'], det_t='m', det_w=2),
], gloss=[G('M13.4 31.8Q14.4 28.6 17.8 27.2'), DOT(12.6, 34.6), G('M9.6 20.6Q10.4 17.8 12.8 17', 2)], qty='4.54 kg bag', aisle='produce')

# grapes: purple bunch, cocoa stem, melon leaf
GR = [(16.4, 15.6), (24, 15.2), (31.6, 15.6), (20.2, 21.8), (27.8, 21.8), (35, 21), (13, 21.6), (16.6, 28), (24, 28.2), (31.4, 27.6), (20.4, 34.2), (27.6, 34.2), (24, 39.4)]
add('grapes', 'Red grapes', [
    TUBE('M24 12C24 9 24.8 7.2 26.6 6', 'cocoa', 3.6, 'm'),
    F(('path', 'M25 9.8C27 5.4 32.6 4.6 36.2 6.4C34.2 10.6 29.4 11.8 25 9.8Z'), 'melon', 'm', ol=1.5, det=['M27 9Q31 7.6 34.6 6.8'], det_w=1),
    *[F(('circle', x, y, 4.3), 'grape', 'm', band=.26) for x, y in GR],
], gloss=[G('M14.4 14.4Q15 12.6 16.6 12.2', 2), G('M10.8 20.6Q11.4 18.8 13 18.4', 2), G('M14.4 27Q15 25.2 16.6 24.8', 2)], qty='907 g bag', aisle='produce')

# navel oranges: two dimpled oranges + leaf
add('oranges', 'Navel oranges', [
    F(('circle', 30.4, 22.6, 11), 'peach', 'm', band=.22, tex=['M26 17h.1M33 15.6h.1M36.6 21h.1M29.6 22.4h.1M34 27.6h.1M26.4 27.6h.1'], tex_t='p', tex_w=1.4),
    F(('circle', 18.6, 29.4, 12.2), 'peach', 'm', band=.22, tex=['M13.6 26h.1M20 23.4h.1M24.6 28.6h.1M17 31.6h.1M22 35.2h.1M12.4 33.4h.1M27 33.2h.1'], tex_t='p', tex_w=1.4),
    F(('path', 'M18.8 17.4C19.6 13 24 10.6 28.2 11.4C27.2 15.6 23 18 18.8 17.4Z'), 'melon', 'm', ol=1.5, det=['M20.6 16.2Q23.8 13.8 26.8 12.4'], det_w=1),
    F(('circle', 18.6, 18.4, 1.6), 'melon', 'tr', ol=0, cut=False),
], gloss=[G('M9.4 26.6Q10.4 22.4 13.8 20.4'), DOT(9.2, 30.4), G('M24 17.6Q25.2 15 27.8 14', 2)], qty='1.36 kg bag', aisle='produce')

# sweet corn cob with husks
KER = ''.join(f'M{fmt(x)} {fmt(y)}h.1' for y in (13.6, 18, 22.4, 26.8, 31.2) for x in ((20.4, 24, 27.6) if y < 30 else (21.6, 26.4)))
add('corn', 'Sweet corn', [
    F(('path', 'M24 6.4C29.4 6.4 31.6 12.4 31.6 20.6C31.6 28 29.6 34 24 34C18.4 34 16.4 28 16.4 20.6C16.4 12.4 18.6 6.4 24 6.4Z'), 'lemon', 'm', ol=2,
      tex=[KER], tex_t='p', tex_w=3.2),
    F(('path', 'M24 42C17 41.4 10.6 36 9.4 26.4C9.2 24.6 10.8 24.2 11.6 25.6C14.4 31 18.8 33.6 23 33.8Z'), 'melon', 'm', band=.3, det=['M15.4 33Q17.8 35.8 21.6 37'], det_t='p', det_w=1.4),
    F(('path', 'M24 42C31 41.4 37.4 36 38.6 26.4C38.8 24.6 37.2 24.2 36.4 25.6C33.6 31 29.2 33.6 25 33.8Z'), 'melon', 'm', band=.3, det=['M32.6 33Q30.2 35.8 26.4 37'], det_t='p', det_w=1.4),
    F(('path', 'M18.6 33.4C20.4 36.4 22 40.4 24 42C26 40.4 27.6 36.4 29.4 33.4C27.4 35 20.6 35 18.6 33.4Z'), 'melon', 'p', ol=1.6),
], gloss=[G('M19.4 19Q19.6 13 22.4 10'), DOT(19.6, 22.6)], qty='3 cobs', aisle='produce')

# mushrooms: two button mushrooms
add('mushrooms', 'White mushrooms', [
    F(('path', 'M33.4 30H39.6L40.2 40Q40.2 42 38.2 42H34.8Q32.8 42 32.8 40Z'), 'cocoa', 'w', band=.26, band_t='p'),
    F(('path', 'M27.4 30.6C27.4 24.4 31.6 21.4 36.4 21.4C41.2 21.4 45 24.4 45 30.6C45 31.8 44 32.2 43 32.2H29.4C28.4 32.2 27.4 31.8 27.4 30.6Z'), 'cocoa', 'p', band=.3, band_t='m'),
    F(('path', 'M15.4 26.8H24.6L25.6 39.6Q25.6 42 23.2 42H16.8Q14.4 42 14.4 39.6Z'), 'cocoa', 'w', band=.24, band_t='p', det=['M16.6 30.2Q20 31.4 23.4 30.2'], det_w=1.2),
    F(('path', 'M5.4 25.6C5.4 15.6 12 10.6 20 10.6C28 10.6 34.6 15.6 34.6 25.6C34.6 27.4 33.4 28.2 31.8 28.2H8.2C6.6 28.2 5.4 27.4 5.4 25.6Z'), 'cocoa', 'p', band=.24, band_t='m',
      tex=['M13 17.6h.1M24.6 15.6h.1M28.4 21.4h.1'], tex_t='w', tex_w=2.6),
], gloss=[G('M9.4 21Q10.4 15.6 15.6 13.4'), DOT(18.6, 12.8), G('M30.8 26.4Q31.4 24 33.4 23.2', 1.8)], qty='227 g pack', aisle='produce')

# Bartlett pear
add('pear', 'Bartlett pears', [
    TUBE('M24 10.4C24 7.6 23.4 6 22 5', 'cocoa', 3.4, 'm'),
    F(('path', 'M24.8 8.4C26.6 4.6 31.4 3.8 34 5.2C32.4 8.8 27.8 10 24.8 8.4Z'), 'melon', 'm', ol=1.5, det=['M26.6 7.6Q29.8 6.2 32.6 5.6'], det_w=1),
    F(('path', 'M24 9.6C28.2 9.6 29.6 13.6 30.4 17.6C31 20.6 33 22 35.4 25.4C38.6 30 37.8 42 24 42C10.2 42 9.4 30 12.6 25.4C15 22 17 20.6 17.6 17.6C18.4 13.6 19.8 9.6 24 9.6Z'),
      'lemon', 'm', band=.24, tex=['M30 33h.1M18 29.6h.1M26.4 37.4h.1'], tex_t='p', tex_w=1.6),
], gloss=[G('M16 29.6Q16 25.6 18.8 23'), DOT(15.6, 33.2)], qty='each', aisle='produce')

# cherries pair
add('cherries', 'Cherries', [
    TUBE('M15.6 27C17.4 19.6 21.6 12.4 27.6 8.6', 'melon', 3.2, 'm'),
    TUBE('M32.6 28.4C31.4 20.4 29.4 13.4 27.6 8.6', 'melon', 3.2, 'm'),
    F(('path', 'M27.6 8.6C30.2 4.4 35.8 3.6 39.4 5.4C37.6 9.8 31.8 11 27.6 8.6Z'), 'melon', 'm', ol=1.5, det=['M29.6 8Q33.6 6.4 37.8 5.6'], det_w=1),
    F(('circle', 33.4, 33, 8.4), 'strawberry', 'm', band=.24),
    F(('circle', 15.4, 32.2, 9), 'strawberry', 'm', band=.24),
], gloss=[G('M9.4 30.6Q10.2 27.4 13 26', 2.4), DOT(9.4, 34), G('M28.6 30.6Q29.2 28.4 31.2 27.6', 2)], qty='454 g bag', aisle='produce')

# watermelon half-slice
add('watermelon', 'Mini watermelon', [
    F(('path', 'M5.6 20.6H42.4C42.4 32.6 34 42 24 42C14 42 5.6 32.6 5.6 20.6Z'), 'melon', 'm', ol=2),
    F(('path', 'M9.4 20.6H38.6C38.6 30.4 32 37.8 24 37.8C16 37.8 9.4 30.4 9.4 20.6Z'), 'strawberry', 'w', ol=0, cut=False),
    F(('path', 'M11 20.6H37C37 29.4 31.2 36 24 36C16.8 36 11 29.4 11 20.6Z'), 'strawberry', 'm', ol=1.6, cut=False, band='M5 31.6H43V44H5Z', band_t='deep'),
    *[F(('ellipse', x, y, 1, 1.7, r), 'strawberry', 'tr', ol=0, cut=False) for x, y, r in [(17, 25, -20), (24, 26.6, 0), (31, 25, 20), (20.4, 30.4, -14), (27.6, 30.4, 14)]],
    F(('rect', 4.4, 18.6, 39.2, 4, 2), 'melon', 'p'),
], gloss=[G('M5.6 20.6H15', 2.2), DOT(18.4, 20.6), G('M8.6 26Q9.4 31 12.6 34.6', 2)], qty='each', aisle='produce')

# celery bunch tied with a ribbon band
add('celery', 'Celery', [
    *[F(('path', smooth([(x - 3, 9 + o), (x - 1, 5 + o), (x + 2, 6 + o), (x + 4, 4.4 + o), (x + 6, 8 + o), (x + 3.4, 11 + o), (x, 11.6 + o)])), 'melon', 'm', ol=1.6) for x, o in [(12, 4), (19.4, 0), (27.4, 1.4), (33, 5)]],
    *[F(('path', f'M{x - 3.2} 13.6H{x + 3.2}L{x + 3} 40Q{x + 3} 42 {x + 1} 42H{x - 1}Q{x - 3} 42 {x - 3} 40Z'), 'melon', 'p', band=.18, det=[f'M{x - .8} 16V38'], det_t='m', det_w=1.4) for x in (15.6, 22, 28.4, 34.4)],
    F(('rect', 11, 27, 27, 5.2, 2.2), 'sakura', 'm'),
], gloss=[G('M13.6 17V23', 2.2), DOT(13.6, 35)], qty='1 bunch', aisle='produce')

# sweet potatoes: one whole, one cut showing orange flesh
add('sweetpotato', 'Sweet potatoes', [
    F(('path', smooth([(5.4, 31), (10, 22.6), (19.4, 20), (27.6, 22.6), (31, 30), (27, 37.6), (17.8, 39.6), (9.6, 37.8)])), 'strawberry', 'p', band=.26,
      det=['M11.6 28.6h.1', 'M20.6 26.4h.1', 'M24.6 33h.1'], det_t='m', det_w=1.8),
    F(('path', smooth([(24.6, 25.6), (31, 18.8), (38.4, 18), (42.6, 24.4), (42, 34), (36.6, 40.6), (29, 41), (25.4, 34.6)])), 'strawberry', 'p', band=.24),
    F(('ellipse', 35.6, 31.6, 5.6, 7.6, 18), 'peach', 'm', ol=1.8, cut=False, band=.3, band_t='deep', det=['M33.6 28.4Q35.6 30.8 34.6 34.4'], det_t='p', det_w=1.4),
], gloss=[G('M8.6 29.4Q9.8 25.6 13.6 24'), DOT(17, 23)], qty='1.36 kg bag', aisle='produce')

# limes: two limes + slice
add('limes', 'Limes', [
    F(('ellipse', 30.4, 22.4, 10.6, 8.8, -14), 'melon', 'm', band=.22), LINE('M40.4 19.2l1.4 -.5', 'melon', 2),
    F(('ellipse', 17.6, 30.6, 11.4, 9.4, 10), 'melon', 'm', band=.22),
    F(('circle', 35, 35.4, 6.8), 'melon', 'p', ol=2),
    F(('circle', 35, 35.4, 4.8), 'melon', 'p', ol=0, cut=False, det=['M35 31V39.8', 'M31.2 33.2L38.8 37.6', 'M31.2 37.6L38.8 33.2'], det_t='m', det_w=1.4),
], gloss=[G('M8.8 28.4Q10.2 24.6 14.2 23.2'), DOT(9.2, 32), G('M23.4 18.4Q25 15.6 28.2 14.6', 2)], qty='bag of 6', aisle='produce')
