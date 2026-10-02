from engine import *

ICONS = []


def add(*a, **k):
    ICONS.append(Icon(*a, **k))


def calyx(cx, top, s=1.0):
    return (f'M{fmt(cx - 4.2 * s)} {fmt(top + 1.6 * s)}Q{fmt(cx - 2.4 * s)} {fmt(top - 1.6 * s)} {fmt(cx - .6 * s)} {fmt(top + .2 * s)}'
            f'Q{fmt(cx)} {fmt(top - 2.6 * s)} {fmt(cx + .6 * s)} {fmt(top + .2 * s)}Q{fmt(cx + 2.4 * s)} {fmt(top - 1.6 * s)} {fmt(cx + 4.2 * s)} {fmt(top + 1.6 * s)}'
            f'Q{fmt(cx)} {fmt(top + 3.8 * s)} {fmt(cx - 4.2 * s)} {fmt(top + 1.6 * s)}Z')


def seeds(pts, r=.8):
    return [F(('circle', x, y, r), 'lemon', 'lp', ol=0, cut=False) for x, y in pts]


BASKET_BODY = ('path', 'M8.5 26H39.5L36.8 39.6Q36.3 42 33.9 42H14.1Q11.7 42 11.2 39.6Z')
BASKET_TEX = ['M14.5 26V42', 'M20.2 26V42', 'M25.8 26V42', 'M31.5 26V42', 'M8 32H40', 'M8 37H40']
RIM = ('rect', 6.5, 23, 35, 5.5, 2.75)

# 1 strawberries 1 L basket
add('strawberries', 'Strawberries', [
    F(berry(24, 8.6, 12.4, 15.5), 'strawberry', 'm'),
    *seeds([(21.5, 14.5), (26.5, 14.5), (24, 17.5), (21.8, 20), (26.2, 20)]),
    F(('path', calyx(24, 8.6)), 'melon', 'm', ol=1.5),
    F(berry(14.8, 12.6, 11.4, 13.4), 'strawberry', 'm'),
    *seeds([(12.5, 18), (16.8, 18), (14.6, 21)]),
    F(('path', calyx(14.8, 12.6, .9)), 'melon', 'm', ol=1.5),
    F(berry(33.2, 12.6, 11.4, 13.4), 'strawberry', 'm'),
    *seeds([(31, 18), (35.4, 18), (33.2, 21)]),
    F(('path', calyx(33.2, 12.6, .9)), 'melon', 'm', ol=1.5),
    F(BASKET_BODY, 'melon', 'p', band=.26, tex=BASKET_TEX),
    F(RIM, 'melon', 'm'),
], gloss=[G('M19.6 15.2Q19.8 12.4 22.2 11.6', 2.2), G('M10.6 19Q10.8 16.6 12.6 15.8', 2.2), G('M9.5 25.2H17', 2.2), DOT(20.4, 25.2, .9)],
    qty='1 L basket', aisle='produce')

# 2 blueberries pint
BB = [(18.5, 13.8, 5), (29.5, 13.8, 5), (12.6, 19.2, 5.2), (24, 18.4, 5.4), (35.4, 19.2, 5.2)]
parts = []
for x, y, r in BB:
    parts.append(F(('circle', x, y, r), 'periwinkle', 'm', det=[f'M{fmt(x + r * .15)} {fmt(y - r * .62)}l1.3 -1.3M{fmt(x + r * .15)} {fmt(y - r * .62)}l1.5 .6'], det_w=1.2))
add('blueberries', 'Blueberries', parts + [
    F(BASKET_BODY, 'melon', 'p', band=.26, tex=BASKET_TEX),
    F(RIM, 'melon', 'm'),
], gloss=[G('M20.2 17.4Q20.8 14.6 23 14', 2.2), G('M9.2 18.6Q9.6 16.2 11.6 15.6', 2.2), G('M32 18.6Q32.4 16.2 34.4 15.6', 2.2), G('M9.5 25.2H17', 2.2), DOT(20.4, 25.2, .9)],
    qty='551 mL pint', aisle='produce')

# 3 banana bunch
add('banana', 'Bananas', [
    F(('path', 'M12.6 11C21 4.6 36 7.6 41.6 17.4Q42.6 20 40 20.6C32.4 16 22.4 15.6 15 19.6Z'), 'lemon', 'm', band=.3,
      det=['M18.4 12.8C25.4 11.2 32.8 13 37.8 16.6']),
    F(('path', 'M12 14.6C15 25.4 27.6 31.8 40.6 28.4Q42.6 28.4 42 30.8C30 39 14.4 33 9.6 20Z'), 'lemon', 'm', band=.3,
      det=['M14 21.4C18.8 28.4 27.6 32.4 36.8 31.6']),
    F(('path', 'M10.6 15.8C7.4 29 17 41.6 32.8 41.6Q35 41.4 34.2 39C21.6 34.6 15.6 27.4 16 16.6Z'), 'lemon', 'm', band=.25,
      det=['M11.6 24.6C13.2 32 19.4 37.6 28 39.4']),
    F(('rect', 8.4, 9.2, 7.6, 8, 2.6, -35), 'cocoa', 'm'),
    F(('circle', 40.8, 18.8, 1.6), 'cocoa', 'tr', ol=0, cut=False),
    F(('circle', 41, 29.8, 1.6), 'cocoa', 'tr', ol=0, cut=False),
    F(('circle', 33.4, 40.2, 1.6), 'cocoa', 'tr', ol=0, cut=False),
], gloss=[G('M18.6 11Q24.6 8.6 30.4 9.6', 2.2), G('M12.4 23Q13.4 29 17.6 33.4'), DOT(20.4, 35.8)], qty='6 bananas', aisle='produce')

# 4 lemon
add('lemon', 'Lemons', [
    F(('path', 'M6.5 27.5C7.5 26.5 8.6 25.5 9.4 24.2C11 17.4 17 13.5 24 13.5C31 13.5 37 17.4 38.6 24.2C39.4 25.5 40.5 26.5 41.5 27.5C40.5 28.5 39.4 29.5 38.6 30.8C37 37.6 31 41.5 24 41.5C17 41.5 11 37.6 9.4 30.8C8.6 29.5 7.5 28.5 6.5 27.5Z'),
      'lemon', 'm', band=.24, det=['M30 35.6Q33.6 34 35.2 31'], det_t='deep'),
    F(('path', 'M24.6 13.8C26.4 9 31.8 7 37 8.2C35.2 12.8 29.8 14.8 24.6 13.8Z'), 'melon', 'm', det=['M26.4 12.8Q31 10.8 34.6 9.2'], det_w=1.2),
], gloss=[G('M12.6 23.4Q14.6 18.2 19.8 16.4'), DOT(11.4, 27)], qty='bag of 4', aisle='produce')

# 5 McIntosh apple
add('apple', 'McIntosh apples', [
    LINE('M24 16.8C24 13.2 24.8 10.8 26.2 8.8', 'strawberry', 2.4),
    F(('path', 'M26.6 11.6C29.4 7.2 34.4 6.8 37.2 8.4C34.8 12.8 30 13.8 26.6 11.6Z'), 'melon', 'm', det=['M28.4 11Q32 9.6 35.4 8.8'], det_w=1.2),
    F(('path', 'M24 16C20 12.5 9.5 12 8.2 23.5C7 33.5 14 41.8 19.5 41.5C21.5 41.4 22.5 40.6 24 40.6C25.5 40.6 26.5 41.4 28.5 41.5C34 41.8 41 33.5 39.8 23.5C38.5 12 28 12.5 24 16Z'),
      'strawberry', 'm', band=.24),
    F(('ellipse', 32.6, 22.6, 4.6, 3.2, -35), 'melon', 'p', ol=0, cut=False),
    F(('ellipse', 22.4, 31.2, .9, 1.4, 20), 'lemon', 'lp', ol=0, cut=False),
    F(('ellipse', 27.6, 34.2, .9, 1.4, -20), 'lemon', 'lp', ol=0, cut=False),
], gloss=[G('M12.8 23Q13.6 18.2 18.4 16.8'), DOT(12.2, 27)], qty='1.36 kg bag', aisle='produce')

# 6 carrots
CARROT = 'M-5 2C-5 -1.2 5 -1.2 5 2C5 10 2.4 22.8 .9 27.4Q0 29.6 -.9 27.4C-2.4 22.8 -5 10 -5 2Z'
LEAVES = ['M-1.2 -.4C-6 -3.6 -7 -9 -4.4 -12C-1.2 -9 -.4 -4.6 -1.2 -.4Z', 'M0 -.6C-.8 -6 1 -11 4.2 -12.6C5.8 -8.6 3.6 -3.6 0 -.6Z', 'M1.4 -.2C4.4 -4.2 9 -5.4 11.4 -3.6C9.4 -.2 5 .8 1.4 -.2Z']
add('carrots', 'Carrots', [
    *[F(('path', l), 'melon', 'm', tf='translate(32.6 12.4) rotate(32)', ol=1.6) for l in LEAVES],
    F(('path', CARROT), 'peach', 'm', band=.24, tf='translate(32.6 12.4) rotate(32)', det=['M-4.4 8H-1.6', 'M1.4 12.6H3.8', 'M-3.4 17.4H-1']),
    *[F(('path', l), 'melon', 'm', tf='translate(23 13.8) rotate(16)', ol=1.6) for l in LEAVES],
    F(('path', CARROT), 'peach', 'm', band=.24, tf='translate(23 13.8) rotate(16)', det=['M-4.4 8H-1.6', 'M1.4 12.6H3.8', 'M-3.4 17.4H-1']),
], gloss=[G('M-3 4.2Q-3 1.6 -.6 1.2', 2.2, 'translate(23 13.8) rotate(16)'), G('M-3 4.2Q-3 1.6 -.6 1.2', 2.2, 'translate(32.6 12.4) rotate(32)')],
    qty='907 g bag', aisle='produce')

# 7 broccoli
add('broccoli', 'Broccoli', [
    F(('path', 'M19.4 26L17.6 39.4Q17.4 42 20 42H28Q30.6 42 30.4 39.4L28.6 26Z'), 'melon', 'p', band=.3,
      det=['M24 30V37', 'M24 33L21 30.4', 'M24 33.4L27 30.8']),
    F(('path', 'M10 22C7 14.6 14.6 9.4 18.2 11.2C20 6.4 28.4 6.2 30.4 11C34 9 41.2 13.2 38.4 21C40.6 26 34.6 30.4 30.4 28.4C27.6 31.2 21 31.4 18.2 28.4C13.2 30.4 6.6 27.4 10 22Z'),
      'melon', 'm', band=.22, det=['M15.4 17.2Q17.6 15 20 17', 'M24.2 12.6Q26.4 10.6 28.6 12.8', 'M29.6 18.6Q31.8 16.6 34 18.6', 'M20.4 23Q22.6 21 24.8 23', 'M13 23.6Q14.6 22.2 16.4 23.4', 'M30 24.4Q31.8 23 33.6 24.4'], det_w=1.4),
], gloss=[G('M12.6 18.6Q13.2 14.4 17 13.4'), DOT(20.4, 12.6)], qty='1 crown', aisle='produce')

# 8 tomatoes on the vine
add('tomatoes', 'Tomatoes on the vine', [
    TUBE('M8.8 15.4C14 11.6 22 11.2 28 12.2C32.6 13 36.4 12.2 38.4 9.6C39.8 7.6 38 6 36.6 7.2', 'melon', 4, 'm'),
    F(('path', 'M28.6 12.4C29.8 8.4 33.2 7 35.4 7.6C34.6 10.8 31.6 12.8 28.6 12.4Z'), 'melon', 'm', ol=1.4),
    TUBE('M14.6 13.6L14.2 21.6', 'melon', 3.4, 'm'),
    TUBE('M33.4 13L33.8 21.6', 'melon', 3.4, 'm'),
    F(('ellipse', 13.8, 29.2, 7.6, 7), 'strawberry', 'm', band=.26),
    F(('ellipse', 34.2, 29.2, 7.6, 7), 'strawberry', 'm', band=.26),
    TUBE('M24 11.6V21', 'melon', 3.4, 'm'),
    F(('ellipse', 24, 32.6, 8.8, 8.2), 'strawberry', 'm', band=.26),
    F(('path', star_path(14, 22.6, 4.2, 1.8, 5)), 'melon', 'm', ol=1.3),
    F(('path', star_path(34, 22.6, 4.2, 1.8, 5)), 'melon', 'm', ol=1.3),
    F(('path', star_path(24, 25, 4.8, 2, 5)), 'melon', 'm', ol=1.3),
], gloss=[G('M17.6 31Q18.4 28 21 27.2', 2.4), DOT(17.2, 34.4), G('M8.6 28Q9.2 25.6 11 25', 2.2)], qty='680 g', aisle='produce')

# 9 yellow onion
add('onion', 'Yellow onions', [
    TUBE('M24 10C24 8 25 6.6 27 6', 'melon', 3.6, 'm'),
    F(('path', 'M24 8.6C25.6 13.2 29.6 15 33.6 17.4C40 21.4 41.2 31 35.4 36.2C31.2 40 16.8 40 12.6 36.2C6.8 31 8 21.4 14.4 17.4C18.4 15 22.4 13.2 24 8.6Z'),
      'peach', 'p', band=.26, det=['M23.8 12.4C18.6 18 16.6 28 18.6 38.6', 'M24.2 12.4C29.4 18 31.4 28 29.4 38.6', 'M24 13V39']),
    LINE('M21 39.2L19.6 41.4M24 39.4V42M27 39.2L28.4 41.4', 'peach', 1.6),
], gloss=[G('M12.6 25Q13.6 20.4 17.6 18.4'), DOT(11.8, 28.6)], qty='1.36 kg bag', aisle='produce')

# 10 garlic
add('garlic', 'Garlic', [
    F(('rect', 21.8, 6.4, 4.4, 6, 1.6), 'grape', 'w', ol=1.6),
    F(('path', 'M24 9.6C25.6 13.6 28 14.8 32 17.2C40 22 41.4 31.4 36 37.2C32.6 40.8 27.6 41.2 24 40.2C20.4 41.2 15.4 40.8 12 37.2C6.6 31.4 8 22 16 17.2C20 14.8 22.4 13.6 24 9.6Z'),
      'grape', 'w', band=.24, det=['M24 14C19 19.6 18 30.4 21.2 40.2', 'M24 14C29 19.6 30 30.4 26.8 40.2', 'M16.6 18.6C12.6 24.4 12.8 32.4 15.4 38', 'M31.4 18.6C35.4 24.4 35.2 32.4 32.6 38']),
    LINE('M21.4 40.4L20.4 42.4M24 40.6V42.8M26.6 40.4L27.6 42.4', 'grape', 1.4),
], gloss=[G('M12.4 25.4Q13.4 21 17.2 19'), DOT(11.6, 29)], qty='3 bulbs', aisle='produce')

# 11 avocado
add('avocado', 'Avocados', [
    F(('path', 'M24 6.6C30 6.6 33 12.6 35 18.6C37.5 24.6 40 27.6 40 32.6C40 38.6 33 42 24 42C15 42 8 38.6 8 32.6C8 27.6 10.5 24.6 13 18.6C15 12.6 18 6.6 24 6.6Z'),
      'melon', 'm', band=.18),
    F(('path', 'M24 10.4C28.4 10.4 30.6 15 32.1 20C34 25.4 36.4 28.4 36.4 32.6C36.4 37 31 39 24 39C17 39 11.6 37 11.6 32.6C11.6 28.4 14 25.4 15.9 20C17.4 15 19.6 10.4 24 10.4Z'),
      'melon', 'p', ol=1.5, cut=False),
    F(('circle', 24, 30.2, 6.4), 'cocoa', 'm', band=.26),
], gloss=[G('M20.6 30Q20.8 26.6 23.8 25.8', 2.2), G('M12.6 25.6Q14 19 16.6 13.8'), DOT(18.4, 10.8)], qty='2 ripe', aisle='produce')

# 12 baguette
BTF = 'translate(24.6 23.6) rotate(-38)'
BTF = 'translate(24 26) rotate(-25)'
add('baguette', 'Baguette', [
    F(('path', 'M-22 0C-22 -4.6 -18.6 -5.4 -14.4 -5.4H14.4C18.6 -5.4 22 -4.6 22 0C22 4.6 18.6 5.4 14.4 5.4H-14.4C-18.6 5.4 -22 4.6 -22 0Z'),
      'peach', 'm', band=.28, tf=BTF),
    *[F(('ellipse', x, -1.2, 3.5, 1.3, -24), 'peach', 'p', ol=1.3, cut=False, tf=BTF) for x in (-4.4, 2.6, 9.6, 16.4)],
    F(('path', 'M-20.4 -7.6H-9.4L-6.4 7.6H-20.4Q-21.8 7.6 -21.8 6.2V-6.2Q-21.8 -7.6 -20.4 -7.6Z'), 'cocoa', 'p', band=.3, tf=BTF,
      det=['M-12.6 -7.4L-10.8 -4.8L-13 -2'], det_w=1.3),
    F(('path', heart_path(-15.6, .4, 2.6)), 'strawberry', 'm', ol=1.2, cut=False, tf=BTF),
], gloss=[G('M-1 -3.2H12', 2.4, BTF), DOT(15.4, -3.2, .9, BTF)], qty='1 stick', aisle='bakery')

# 13 croissant
add('croissant', 'Croissants', [
    F(('ellipse', 10.6, 34.2, 3.6, 5.6, 58), 'peach', 'm', band=.3),
    F(('ellipse', 37.4, 34.2, 3.6, 5.6, -58), 'peach', 'm', band=.3),
    F(('ellipse', 15.2, 28.6, 5.4, 8.2, 52), 'peach', 'm', band=.3),
    F(('ellipse', 32.8, 28.6, 5.4, 8.2, -52), 'peach', 'm', band=.3),
    F(('path', 'M24 15.2C30.4 15.2 33.4 21.4 33 28C32.6 34.4 29 37.6 24 37.6C19 37.6 15.4 34.4 15 28C14.6 21.4 17.6 15.2 24 15.2Z'), 'peach', 'm', band=.26,
      det=['M18.4 24.4Q24 21.6 29.6 24.4', 'M17.4 30Q24 27.4 30.6 30'], det_t='p', det_w=1.6),
], gloss=[G('M18.4 20.6Q20.2 17.6 23.4 17.2'), DOT(17.2, 23.6), G('M10 25.6Q11.2 23.4 13.4 22.6', 2.2)], qty='4 pack', aisle='bakery')

# 14 sourdough boule
add('sourdough', 'Sourdough boule', [
    F(('path', 'M7 36.6C7 22.4 15 13.2 24 13.2C33 13.2 41 22.4 41 36.6Q41 41.6 36.4 41.6H11.6Q7 41.6 7 36.6Z'), 'cocoa', 'm', band=.22),
    F(('path', 'M13.6 25.8C18.4 18.8 29.4 17.4 35.6 22.2C29.6 21.6 20.6 22.6 13.6 25.8Z'), 'cocoa', 'p', ol=1.5, cut=False),
    *[F(('ellipse', x, y, 2.9, 1.1, a), 'cocoa', 'p', ol=1.2, cut=False) for x, y, a in [(16.6, 31.2, -30), (24, 29.6, 0), (31.4, 31.2, 30)]],
    *[F(('circle', x, y, .7), 'cocoa', 'w', ol=0, cut=False) for x, y in [(20, 17), (28.4, 16.4), (34, 19), (14.8, 21.2), (25, 15.2)]],
], gloss=[G('M10.6 30.4Q11.6 22.8 16.4 18.6'), DOT(19.4, 16.4)], qty='800 g boule', aisle='bakery')

# 15 sliced loaf (bag clip)
SLICE = 'M9 22C9 17 12 14.6 16.4 15C18.6 12.4 25.4 12.4 27.6 15C32 14.6 35 17 35 22C35 25 33.4 26.4 32 27V38.8Q32 41.6 29.2 41.6H14.8Q12 41.6 12 38.8V27C10.6 26.4 9 25 9 22Z'
CRUMB = 'M11.8 22C11.8 18.8 13.9 17.4 17.1 17.8C19.1 15.4 24.9 15.4 26.9 17.8C30.1 17.4 32.2 18.8 32.2 22C32.2 24.2 30.8 25.2 29.4 25.6V37.8Q29.4 39 28.2 39H15.8Q14.6 39 14.6 37.8V25.6C13.2 25.2 11.8 24.2 11.8 22Z'
add('loaf', 'Sandwich loaf', [
    F(('path', SLICE), 'peach', 'm', tf='translate(5.6 -3.8)', band=.2),
    F(('path', SLICE), 'peach', 'm', tf='translate(2.8 -1.9)', band=.2),
    F(('path', SLICE), 'peach', 'm'),
    F(('path', CRUMB), 'peach', 'w', ol=0, cut=False, band='M10 34H34V42H10Z', band_t='p'),
    *[F(('circle', x, y, r), 'peach', 'm', ol=0, cut=False) for x, y, r in [(19, 27, 1), (25.4, 24.6, .9), (23, 31.4, 1.1), (27, 34.2, .8), (18.4, 35, .8)]],
], gloss=[G('M14.4 23Q14.4 20 17.2 19.6', 2.2), DOT(17.4, 26.2)], qty='675 g loaf', aisle='bakery')

# 16 milk bag in pitcher: ramune pitcher, white periwinkle pouch, corner flopped over the rim, snipped corner up-right
add('milkbag', 'Milk, 2 %', [
    TUBE('M34.6 26C41.8 26 41.8 37 34 37', 'ramune', 5.4, 'p'),
    F(('path', 'M11.8 23H36.2L34.4 38.8Q34 42 30.8 42H17.2Q14 42 13.6 38.8Z'), 'ramune', 'p', band=.28),
    F(('path', 'M12.8 22C11.6 17 12.6 12.4 15.6 10.6C19 8.6 25 9 29.2 10.2L32.6 7.2Q33.8 6.2 34.6 7.4L36.6 10.2Q37.4 11.4 36.2 12.2L33.6 13.8C34.8 16.4 35.2 19.4 34.8 22Z'),
      'periwinkle', 'w', tex=['M8 16.4H40'], tex_t='#FF6F8E', tex_w=3, det=['M30.4 11.4L33.2 14'], det_w=1.2),
    F(('rect', 10, 19.8, 28, 4.6, 2.3), 'ramune', 'm'),
    F(('path', 'M12.6 19C15.4 18.6 18.6 19.4 21 20.8C20.2 23.8 17.8 27.6 15.8 30.2C14.8 31.6 13 31.4 12.4 30C11.2 26.6 11.2 22.4 12.6 19Z'),
      'periwinkle', 'w', band=.3, band_t='p', tex=['M10 21.4L22 23.6'], tex_t='#FF6F8E', tex_w=2.4),
], gloss=[G('M18.4 28.6V35.4'), DOT(18.4, 38.4), G('M16 18.4Q16 14.6 19.2 13', 2.2)], qty='4 L bag', aisle='dairy')

# 17 egg carton
add('eggs', 'Eggs, large', [
    F(('rect', 8.4, 9.4, 31.2, 13, 3.4), 'periwinkle', 'p', det=['M12 13H36'], det_t='m'),
    F(('ellipse', 14.6, 23.4, 5, 6.4), 'lemon', 'w', band=.3, band_t='lp', ol=2),
    F(('ellipse', 33.4, 23.4, 5, 6.4), 'lemon', 'w', band=.3, band_t='lp', ol=2),
    F(('ellipse', 24, 22.4, 5.2, 6.6), 'lemon', 'w', band=.3, band_t='lp', ol=2),
    F(('path', 'M7 27.4H41V34C41 40 37.4 42 34.6 41.6C32.6 41.2 31.2 40 29.6 40C28 40 26.2 41.6 24 41.6C21.8 41.6 20 40 18.4 40C16.8 40 15.4 41.2 13.4 41.6C10.6 42 7 40 7 34Z'),
      'periwinkle', 'p', band=.3, det=['M18.6 30.4V37', 'M29.4 30.4V37']),
], gloss=[G('M21.4 19.6Q22 17.4 24 16.8', 2.2), G('M10.2 30.6Q10.4 29.2 11.6 28.8', 2.2), G('M11.8 21.6Q12.2 19.6 14 19', 2)], qty='dozen, large', aisle='dairy')

# 18 butter brick with curl
add('butter', 'Butter, salted', [
    F(('rect', 7, 22.6, 34, 19, 3.6), 'lemon', 'p', band=.28),
    F(('path', 'M10.6 22.6H28.4L32 41.6H10.6Q7 41.6 7 38V26.2Q7 22.6 10.6 22.6Z'), 'lemon', 'w', band=.28, band_t='lp',
      tex=['M7 32.2H31'], tex_t='m', tex_w=3, det=['M28.4 22.6L25 31.6L32 41.6'], det_w=1.4),
    F(('path', heart_path(17.6, 28, 2.8)), 'strawberry', 'm', ol=1.2, cut=False),
], gloss=[G('M11 26H13.4', 2.4)], qty='454 g brick', aisle='dairy')

# 19 old cheddar block: front view, crumbly cut corner showing the paler inside, two crumbs, no holes
CHB = 'M10.6 15.6H27.6L29.4 17.6L30.8 16.8L32.4 19.2L34 18.6L35.2 21.2L37.2 21.8L37.6 24.4V38.8Q37.6 42 34.4 42H10.6Q7.4 42 7.4 38.8V18.8Q7.4 15.6 10.6 15.6Z'
add('cheese', 'Old cheddar', [
    F(('path', CHB), 'peach', 'm', band=.22, tex=['M27.2 15.4L29 19.4L31.4 19.2L32.8 22.2L35.4 22.8L37.8 26.6'], tex_t='#FFF3A6', tex_w=4.6),
    F(('circle', 40.8, 39.4, 1.9), 'peach', 'm', ol=1.4),
    F(('circle', 41.6, 34.4, 1.3), 'peach', 'm', ol=1.2),
], gloss=[G('M11.4 20V27', 2.6), DOT(11.4, 30.2)], qty='400 g block', aisle='dairy')

# 20 vanilla yogurt tub: white tub, lemon lid + label, vanilla flower and a cocoa pod
add('yogurt', 'Yogurt, vanilla', [
    F(('path', 'M9.4 18H38.6L36.4 38.6Q36 42 32.4 42H15.6Q12 42 11.6 38.6Z'), 'lemon', 'w', band=.24, band_t='p'),
    F(('rect', 11.6, 24.6, 24.8, 11.2), 'lemon', 'p', ol=0, cut=False, det=['M11.4 24.6H36.6', 'M11.6 35.8H36.4'], det_w=1.4),
    TUBE('M15.6 34L30.4 26.2', 'cocoa', 3.4, 'm', cut=False),
    *[F(('ellipse', 24, 26.4, 2.2, 3.4), 'lemon', 'w', ol=1.3, cut=False, tf=f'rotate({a} 24 30)') for a in range(0, 360, 72)],
    F(('circle', 24, 30, 1.8), 'lemon', 'm', ol=1.1, cut=False),
    F(('rect', 7.6, 13.6, 32.8, 6.6, 3.3), 'lemon', 'm'),
], gloss=[G('M13.6 21.4L14.2 30', 2.4), G('M11 16.9H20', 2.2)], qty='650 g tub', aisle='dairy')

# 21 chicken drumstick
add('drumstick', 'Chicken drumsticks', [
    TUBE('M26.6 30.4L35.2 37.6', 'peach', 6.4, 'w'),
    F(('circle', 34.6, 40.4, 3.2), 'peach', 'w'),
    F(('circle', 38.8, 36.2, 3.2), 'peach', 'w'),
    F(('path', 'M10.8 22.4C9.4 12.6 21.6 6.6 29.8 12C36 16.6 35.6 25.6 30 31C27.8 33.2 25.6 34.6 23.4 35C20 35.4 17.4 33.6 15.4 31.4C12.6 28.8 11.2 25.8 10.8 22.4Z'),
      'peach', 'm', band=.26),
], gloss=[G('M14.6 20.6Q15.4 14.6 21.2 12.6'), DOT(14.4, 24.6)], qty='1 kg tray', aisle='meat')

# 22 salmon fillet with lemon wedge
add('salmon', 'Salmon fillet', [
    F(('path', 'M7 25C7 18.6 12 15.4 19 15.4H34C39 15.4 42 18.4 42 22C42 24.4 40.8 25.8 38.8 26.6L36.4 36.6C35.6 39.6 33.4 41.4 30 41.4H12.4C9 41.4 7 38.6 7 35.4Z'),
      'peach', 'm', band=.2, tex=['M15.4 16C12.2 23 12.2 32.4 15.4 41', 'M22 15.8C18.8 23 18.8 32.4 22 41', 'M28.6 15.8C25.4 23 25.4 32.4 28.6 41', 'M35.2 16.2C32.4 22 32.4 28.4 34.2 34.6'], tex_w=2.2),
], gloss=[G('M10.6 25Q10.8 20 15.8 18.8'), DOT(10.6, 28.8)], qty='450 g', aisle='seafood')

# 23 flour sack
add('flour', 'All-purpose flour', [
    F(('path', 'M14.4 17.6C12.8 13 15.8 8 18 8.6C20 7 22 9.4 24 8C26 9.4 28 7 30 8.6C32.2 8 35.2 13 33.6 17.6Z'), 'cocoa', 'w', ol=2),
    F(('path', 'M12.2 18C9.2 24 8.2 32 9 38Q9.6 42 13.6 42H34.4Q38.4 42 39 38C39.8 32 38.8 24 35.8 18Z'), 'cocoa', 'w', band=.24, band_t='p'),
    F(('rect', 12.6, 15.4, 22.8, 4.2, 2.1), 'cocoa', 'm'),
    LINE('M24 24.6V37.6', 'lemon', 1.6),
    *[F(('ellipse', x, y, 1.9, 3, a), 'lemon', 'm', ol=1.3, cut=False) for x, y, a in [(24, 25.2, 0), (21.4, 28.4, -32), (26.6, 28.4, 32), (21.4, 32.4, -32), (26.6, 32.4, 32)]],
], gloss=[G('M12.8 22.6Q11.8 27 12 31.4'), DOT(12.4, 35)], qty='2.5 kg bag', aisle='baking')

# 24 sugar bag
add('sugar', 'White sugar', [
    F(('rect', 12.4, 9.4, 23.2, 7.2, 2), 'ramune', 'm', det=['M18 11.6V14.6', 'M24 11.6V14.6', 'M30 11.6V14.6'], det_w=1.3),
    F(('path', 'M11 15.4H37V38.8Q37 42 33.8 42H14.2Q11 42 11 38.8Z'), 'ramune', 'w', band=.22, band_t='p'),
    F(('rect', 17.4, 27.4, 6.4, 6.4, 1.4), 'ramune', 'w', ol=1.4, cut=False),
    F(('rect', 24.4, 27.4, 6.4, 6.4, 1.4), 'ramune', 'w', ol=1.4, cut=False),
    F(('rect', 20.8, 20.8, 6.4, 6.4, 1.4), 'ramune', 'w', ol=1.4, cut=False),
], gloss=[G('M14.4 19V27'), DOT(14.4, 30.4)], qty='2 kg bag', aisle='baking')

# 25 maple syrup tin
add('maple', 'Maple syrup', [
    F(('rect', 13.4, 8.6, 8, 6.4, 2), 'lemon', 'm'),
    F(('path', 'M11.4 14.6H36.6Q39 14.6 39 17V38.8Q39 42 35.8 42H12.2Q9 42 9 38.8V17Q9 14.6 11.4 14.6Z'), 'lemon', 'p', band=.2,
      det=['M9.6 18.4H38.4']),
    F(('rect', 10, 22, 28, 12.4), 'strawberry', 'm', ol=0, cut=False, det=['M10 22H38', 'M10 34.4H38'], det_w=1.4),
    F(('path', 'M18.4 33.2V28L24 23.8L29.6 28V33.2Z'), 'strawberry', 'w', ol=1.3, cut=False),
    F(('rect', 26.2, 23.6, 2.2, 3.4, .6), 'strawberry', 'w', ol=1.2, cut=False),
    F(('rect', 22.6, 29.2, 2.8, 4, .8), 'strawberry', 'm', ol=1.1, cut=False),
], gloss=[G('M12.6 19.6V30.6'), DOT(12.6, 33.8), G('M15.4 10.8V12.4', 2)], qty='540 mL tin', aisle='baking')

# 26 coffee bag
add('coffee', 'Coffee beans', [
    F(('path', 'M12.4 14H35.6L37 38.6Q37 42 33.6 42H14.4Q11 42 11 38.6Z'), 'cocoa', 'm', band=.2),
    F(('rect', 11.2, 8.4, 25.6, 6.8, 2.2), 'cocoa', 'm'),
    F(('rect', 9.4, 10.4, 29.2, 3, 1.5), 'lemon', 'm', ol=1.4),
    F(('ellipse', 24, 27.4, 8.4, 7.4), 'cocoa', 'p', ol=1.6, cut=False),
    F(('ellipse', 24, 27.4, 3.4, 4.6, 28), 'cocoa', 'm', ol=1.4, cut=False, det=['M22.8 23.6C25.4 26 22.6 28.8 25.2 31.2'], det_w=1.2),
    F(('circle', 31.4, 18.4, 1.6), 'cocoa', 'p', ol=1.2, cut=False),
], gloss=[G('M14.6 17.8L14.2 26'), DOT(14.1, 29.4)], qty='340 g bag', aisle='breakfast')

# 27 tea box with the bag dangling over the front edge on its string
add('tea', 'Orange pekoe tea', [
    F(('rect', 7.6, 15.6, 32.8, 26.4, 3.6), 'mint', 'p', band=.2, det=['M8.2 20.6H39.8']),
    F(('path', 'M13.4 33.6C12.8 28.6 15.6 25.4 19.6 25C20 29.8 17.6 33.2 13.4 33.6Z'), 'mint', 'm', ol=1.3, cut=False, det=['M14.2 32.6Q16 29.4 18.6 26.6'], det_w=1),
    LINE('M24.8 16C24.8 11.4 29.6 11 29.6 16V23.6', 'mint', 1.4, cut=False),
    F(('rect', 23.8, 23, 11.6, 13.4, 2.4), 'lemon', 'w', band=.46, band_t='m', det=['M24 26.4H35.2'], det_w=1.2),
], gloss=[G('M11.2 25V31'), DOT(11.2, 34.2), G('M26.4 29V31.4', 1.8)], qty='72 bags', aisle='breakfast')

# 28 jam jar
add('jam', 'Strawberry jam', [
    F(('path', 'M12 19H36Q38.6 19 38.6 22V38Q38.6 42 34.6 42H13.4Q9.4 42 9.4 38V22Q9.4 19 12 19Z'), 'strawberry', 'm', band=.2),
    F(('rect', 15, 25.4, 18, 11.4, 3), 'strawberry', 'w', ol=1.5, cut=False),
    F(berry(24, 27.6, 6.6, 7.8), 'strawberry', 'm', ol=1.4, cut=False),
    F(('path', calyx(24, 27.6, .6)), 'melon', 'm', ol=1.1, cut=False),
    F(('path', 'M8 18.6C8 14.6 11 11.6 15 11.6H33C37 11.6 40 14.6 40 18.6C38.2 21 35.4 20.2 33.2 22C31 20.4 27.6 21.6 24 21C20.4 21.6 17 20.4 14.8 22C12.6 20.2 9.8 21 8 18.6Z'),
      'strawberry', 'p', tex=['M14 10V23', 'M20 10V23', 'M26 10V23', 'M32 10V23', 'M7 14.6H41', 'M7 18.6H41'], tex_w=2.2),
    TUBE('M9.4 18.2H38.6', 'strawberry', 3, 'tr'),
], gloss=[G('M12.4 25V33'), DOT(12.4, 36.2), G('M11.6 15Q12.6 13.2 14.6 12.9', 2.2)], qty='500 mL jar', aisle='breakfast')

# 29 canned tomatoes
add('tomatocan', 'Diced tomatoes', [
    F(cyl(10, 38, 14.4, 42), 'periwinkle', 'p', band=.18),
    F(('rect', 10.9, 19.6, 26.2, 16.4), 'strawberry', 'p', ol=0, cut=False, det=['M10.9 19.6H37.1', 'M10.9 36H37.1'], det_w=1.4),
    F(('circle', 24, 28.6, 5), 'strawberry', 'm', ol=1.5, cut=False),
    F(('path', star_path(24, 24.2, 2.8, 1.2, 5)), 'strawberry', 'tr', ol=0, cut=False),
    F(ell_top(10, 38, 14.4), 'periwinkle', 'w'),
    F(('ellipse', 24, 14.4, 10.6, 3.2), 'periwinkle', 'p', ol=1.3, cut=False),
], gloss=[G('M13.4 21V30'), DOT(13.4, 33.4), G('M15.6 13.4Q18 12 21 11.8', 1.8)], qty='796 mL can', aisle='pantry')

# 30 pasta (farfalle)
BOW = 'M0 -1.4C-3 -5.6 -8 -6.2 -9.2 -4C-10.2 -1.6 -10.2 1.6 -9.2 4C-8 6.2 -3 5.6 0 1.4C3 5.6 8 6.2 9.2 4C10.2 1.6 10.2 -1.6 9.2 -4C8 -6.2 3 -5.6 0 -1.4Z'
BOWD = ['M-1.6 -2.2V2.2', 'M1.6 -2.2V2.2', 'M-8.2 -3.2L-7 -1.8L-8.2 -.2L-7 1.4L-8.2 3', 'M8.2 -3.2L7 -1.8L8.2 -.2L7 1.4L8.2 3']
PENNE = 'M-9.4 -3.8H4.2Q5.4 -3.8 6 -2.8L9.4 2.8Q10 3.8 8.8 3.8H-4.8Q-6 3.8 -6.6 2.8L-10 -2.8Q-10.6 -3.8 -9.4 -3.8Z'
PD = ['M-6.6 -1.3H5.8', 'M-5.2 1.3H7.2']
PE = ('ellipse', 7.8, 0, 1.5, 3.2, -28)
add('pasta', 'Penne', [
    F(('path', PENNE), 'lemon', 'p', band=.3, det=PD, det_t='m', det_w=1.2, tf='translate(15.6 33.2) rotate(-28)'),
    F(PE, 'lemon', 'm', ol=1.3, cut=False, tf='translate(15.6 33.2) rotate(-28)'),
    F(('path', PENNE), 'lemon', 'p', band=.3, det=PD, det_t='m', det_w=1.2, tf='translate(31.6 33.6) rotate(24)'),
    F(PE, 'lemon', 'm', ol=1.3, cut=False, tf='translate(31.6 33.6) rotate(24)'),
    F(('path', PENNE), 'lemon', 'p', band=.3, det=PD, det_t='m', det_w=1.2, tf='translate(24 19.2) rotate(-6)'),
    F(PE, 'lemon', 'm', ol=1.3, cut=False, tf='translate(24 19.2) rotate(-6)'),
], gloss=[G('M-6.4 -2.4H.6', 2, 'translate(24 19.2) rotate(-6)'), G('M-6.4 -2.4H.6', 2, 'translate(15.6 33.2) rotate(-28)'), G('M-6.4 -2.4H.6', 2, 'translate(31.6 33.6) rotate(24)')],
    qty='900 g bag', aisle='pantry')

# 31 rice bag
add('rice', 'Jasmine rice', [
    F(('path', 'M9 12.6C11 11.2 13 11.2 14.6 12.6H33.4C35 11.2 37 11.2 39 12.6C38 14.6 37.6 16.6 37.6 18.6V35.6C37.6 37.6 38 39.4 39 41.2C37 42.6 35 42.6 33.4 41.2H14.6C13 42.6 11 42.6 9 41.2C10 39.4 10.4 37.6 10.4 35.6V18.6C10.4 16.6 10 14.6 9 12.6Z'),
      'melon', 'w', band=.2, band_t='p'),
    F(('path', 'M15.4 27.4C15.4 21.6 19.2 18.8 24 18.8C28.8 18.8 32.6 21.6 32.6 27.4Z'), 'melon', 'w', ol=1.5, cut=False,
      det=['M20 23.6l1 .6', 'M24.6 21.8l1 .4', 'M27.4 24.6l1 .6'], det_w=1.2),
    F(('path', 'M14.4 27.2H33.6C33.6 32.6 29.6 35.8 24 35.8C18.4 35.8 14.4 32.6 14.4 27.2Z'), 'melon', 'm', ol=1.5, cut=False, det=['M15.6 30.6H32.4'], det_t='p', det_w=1.6),
], gloss=[G('M13.6 18.4V26'), DOT(13.6, 29.4)], qty='8 kg bag', aisle='pantry')

# 32 cereal box
add('cereal', 'Oat cereal', [
    F(('rect', 10.6, 7.6, 26.8, 34.4, 3.6), 'sakura', 'p', band=.16, det=['M11.2 12.2H36.8']),
    *[F(('circle', x, y, 2.5), 'lemon', 'm', ol=1.2, cut=False) for x, y in [(19, 24.6), (24.2, 23), (29.2, 24.6), (21.6, 19.2), (26.8, 19.2)]],
    *[F(('circle', x, y, .8), 'lemon', 'lp', ol=0, cut=False) for x, y in [(19, 24.6), (24.2, 23), (29.2, 24.6), (21.6, 19.2), (26.8, 19.2)]],
    F(('path', 'M14.6 27.4H33.4C33.4 33.4 29.4 36.6 24 36.6C18.6 36.6 14.6 33.4 14.6 27.4Z'), 'sakura', 'w', ol=1.5, cut=False, det=['M16.2 30.6H31.8'], det_t='m', det_w=1.6),
], gloss=[G('M14 15.4V22.6'), DOT(14, 26)], qty='750 g box', aisle='breakfast')

# 33 frozen peas
add('peas', 'Frozen peas', [
    F(('path', 'M11 12H37L38 37H10Z'), 'ramune', 'p', band=.2),
    F(('rect', 9, 8.4, 30, 5.4, 1.6), 'ramune', 'm', det=['M14 9.8V12.4', 'M19 9.8V12.4', 'M24 9.8V12.4', 'M29 9.8V12.4', 'M34 9.8V12.4'], det_w=1.2),
    F(('rect', 8.6, 36.4, 30.8, 5.4, 1.6), 'ramune', 'm', det=['M14 37.8V40.4', 'M19 37.8V40.4', 'M24 37.8V40.4', 'M29 37.8V40.4', 'M34 37.8V40.4'], det_w=1.2),
    F(('path', 'M13.4 27.4C17.6 21 30.4 21 34.6 27.4C30.4 33 17.6 33 13.4 27.4Z'), 'melon', 'm', ol=1.5, cut=False),
    *[F(('circle', x, 27.4, 2.6), 'melon', 'p', ol=1.2, cut=False) for x in (19.2, 24, 28.8)],
    LINE('M31.6 15.2V20.4M29 17.8H34.2M29.8 16L33.4 19.6M33.4 16L29.8 19.6', 'ramune', 1.3, t='#FFFFFF'),
], gloss=[G('M14.2 16.4L14 22'), DOT(14, 25)], qty='750 g bag', aisle='frozen')

# 34 ice cream tub with scoop
add('icecream', 'Ice cream', [
    F(('path', 'M14.4 17C14.4 10.2 19.4 6.8 24 6.8C28.6 6.8 33.6 10.2 33.6 17C31.6 19.2 29.8 16.8 28 18.6C26 16.6 22 16.6 20 18.6C18.2 16.8 16.4 19.2 14.4 17Z'), 'sakura', 'p', band=.2,
      ),
    *[LINE(d, 'cocoa', 1.6, t='m') for d in ['M19.4 11.6l1.4 1', 'M25.6 10.2l-1 1.4', 'M28.4 13.6l1.4 .6', 'M22.2 14.6l1.2 -.8']],
    F(('path', 'M10.4 19.6H37.6L35.6 38.6Q35.2 42 31.8 42H16.2Q12.8 42 12.4 38.6Z'), 'sakura', 'w', band=.24, band_t='p'),
    F(('rect', 8.4, 16, 31.2, 6, 3), 'sakura', 'm'),
    F(('path', 'M20.2 26.6H27.8L24 35.6Z'), 'cocoa', 'm', ol=1.4, cut=False, det=['M21.6 29.2H26.4', 'M22.8 32H25.2'], det_t='p', det_w=1),
    F(('circle', 24, 26, 3.8), 'sakura', 'p', ol=1.4, cut=False),
], gloss=[G('M17.4 13.6Q18 10.2 21.2 9'), DOT(16.6, 16.2), G('M13.6 25.6L14.2 31', 2.2)], qty='1.5 L tub', aisle='frozen')

# 35 lemonade bottle
add('lemonade', 'Pink lemonade', [
    F(('rect', 17.6, 6.6, 12.8, 5.4, 2.2), 'ramune', 'm'),
    F(('path', 'M19 11H29V16.6C33 18.6 36 21.6 36 26.6V38.6Q36 42 32.6 42H15.4Q12 42 12 38.6V26.6C12 21.6 15 18.6 19 16.6Z'), 'lemon', 'p', band=.2),
    F(('circle', 24, 30.4, 6.2), 'lemon', 'm', ol=1.5, cut=False,
      det=['M24 25.4V35.4', 'M19.8 27.8L28.2 33', 'M19.8 33L28.2 27.8'], det_t='p', det_w=1.2),
    F(('circle', 18.2, 20.6, 1.4), 'lemon', 'w', ol=1, cut=False),
    F(('circle', 30, 22.2, 1), 'lemon', 'w', ol=1, cut=False),
], gloss=[G('M15.2 25.4Q15.6 22 18.4 20.2', 2.2), G('M15.2 29V34.4'), DOT(15.2, 37.4)], qty='1.75 L bottle', aisle='drinks')

# 36 sparkling water can
add('sparkling', 'Sparkling water', [
    F(cyl(12.6, 35.4, 11.6, 42), 'ramune', 'p', band=.16),
    F(ell_top(12.6, 35.4, 11.6), 'ramune', 'w', det=['M20.6 11.2H25.4'], det_w=1.6),
    F(('circle', 24, 30, 5.4), 'mint', 'm', ol=1.5, cut=False, det=['M24 25.6V34.4', 'M20.2 27.8L27.8 32.2', 'M20.2 32.2L27.8 27.8'], det_t='p', det_w=1.1),
    *[F(('circle', x, y, r), 'ramune', 'w', ol=1.2, cut=False) for x, y, r in [(18.6, 21.4, 1.8), (27.8, 19.4, 1.3), (22.6, 18.2, 1)]],
], gloss=[G('M15.8 19.6V29'), DOT(15.8, 32.4)], qty='12 × 355 mL', aisle='drinks')

# 37 dish soap
add('dishsoap', 'Dish soap', [
    F(('rect', 22, 5.8, 4, 3.6, 1), 'mint', 'm', ol=1.5),
    F(('rect', 19.4, 8.6, 9.2, 6.8, 2), 'mint', 'w'),
    F(('path', 'M17.4 15H30.6C33.8 15 36 18 36 21.8V38.6Q36 42 32.6 42H15.4Q12 42 12 38.6V21.8C12 18 14.2 15 17.4 15Z'), 'mint', 'm', band=.2),
    F(('ellipse', 24, 30, 7, 6.6), 'mint', 'w', ol=1.5, cut=False),
    F(('circle', 22.2, 31.2, 2.6), 'mint', 'p', ol=1.2, cut=False),
    F(('circle', 26.4, 28.4, 1.8), 'mint', 'p', ol=1.2, cut=False),
    F(('circle', 39.4, 14.4, 2.8), 'mint', 'w', ol=1.4),
    F(('circle', 40.4, 21.2, 1.8), 'mint', 'w', ol=1.3),
], gloss=[G('M15.4 21.6Q15.6 19 17.4 18.2', 2.2), G('M15.2 25V33'), DOT(15.2, 36.2)], qty='740 mL', aisle='household')

# 38 toilet paper
add('toiletpaper', 'Toilet paper', [
    F(cyl(8, 34, 14.4, 41.6), 'periwinkle', 'w', band=.22, band_t='p',
      tex=['M12 20.4h.1M18 20.4h.1M24 20.4h.1M30 20.4h.1M15 25.4h.1M21 25.4h.1M27 25.4h.1M12 30.4h.1M18 30.4h.1M24 30.4h.1M30 30.4h.1'], tex_t='p', tex_w=2),
    F(ell_top(8, 34, 14.4), 'periwinkle', 'w'),
    F(('ellipse', 21, 14.4, 4.4, 1.6), 'periwinkle', 'm', ol=1.4, cut=False),
    F(('path', 'M31.6 18.6H38Q40.4 18.6 40.4 21V37.8L38.4 36.2L36.4 37.8L34.4 36.2L32.4 37.8L31.6 37.2Z'), 'periwinkle', 'w', ol=1.8,
      det=['M33 27.6h1.6M36.4 27.6H38'], det_t='m', det_w=1.4),
], gloss=[G('M11.4 20V28'), DOT(11.4, 31.4)], qty='12 double rolls', aisle='household')

# 39 toothpaste: crimped end, printed mint swirl, cap; lies at -25 deg
TTF = 'translate(24.4 26) rotate(-25)'
add('toothpaste', 'Toothpaste', [
    F(('path', 'M-19.6 -7.6H-14.6L-14.2 7.6H-19.6Q-20.6 7.6 -20.6 6.6V-6.6Q-20.6 -7.6 -19.6 -7.6Z'), 'mint', 'w', tf=TTF, det=['M-18.8 -5.6V5.6', 'M-16.6 -5.6V5.6'], det_w=1),
    F(('path', 'M-15 -7.2H7C10.6 -7.2 12.6 -5 12.6 -2.4V2.4C12.6 5 10.6 7.2 7 7.2H-15Z'), 'mint', 'w', tf=TTF, band=.26, band_t='p',
      tex=['M-12 1.6C-8 -4.6 -4 4.6 0 -1C4 -6.4 7 2.6 10.6 -1.4'], tex_t='m', tex_w=2.6),
    F(('rect', 12, -4.6, 7.4, 9.2, 2), 'mint', 'm', tf=TTF, det=['M15.4 -4.4V4.4'], det_w=1),
], gloss=[G('M-11.6 -4.4H-3', 2.2, TTF), DOT(.4, -4.4, .9, TTF)], qty='100 mL tube', aisle='household')

# 40 cat food tin
add('catfood', 'Cat food', [
    F(cyl(8.6, 39.4, 25, 42, 5.2), 'grape', 'p', band=.22),
    F(('rect', 9.5, 29.6, 29, 6.6), 'grape', 'w', ol=0, cut=False, det=['M9.6 29.6H38.4', 'M9.6 36.2H38.4'], det_w=1.3),
    F(('path', 'M18.2 32.9C20.2 30.6 25.4 30.4 28 32.9C25.4 35.4 20.2 35.2 18.2 32.9Z'), 'grape', 'm', ol=1.2, cut=False),
    F(('path', 'M28 32.9L31 30.8V35Z'), 'grape', 'm', ol=1.2, cut=False),
    F(('ellipse', 24, 25, 15.4, 5.2), 'grape', 'w'),
    F(('ellipse', 24, 25, 11.6, 3.6), 'grape', 'p', ol=1.2, cut=False),
    F(('ellipse', 30.4, 24.4, 3.4, 1.6), 'grape', 'w', ol=1.3, cut=False),
], gloss=[G('M11.8 31.4V34.6', 2.4), G('M13.8 22.6Q17 20.8 21 20.6', 1.8)], qty='6 × 156 g', aisle='household')

# 41 chicken thighs on a foam tray: strawberry thighs, sakura fat edge, white bone knuckles, film glint
TH1 = 'M8 28.6C7.4 23.4 11.4 19.4 17 19.4C22.2 19.4 25.6 22.4 25.4 27C25.2 30.6 22.4 32.6 17.6 32.6C12.4 32.6 8.4 31.6 8 28.6Z'
TH2 = 'M22.6 28.4C22.2 23.2 26 19.6 31.4 19.6C36.8 19.6 40.6 22.6 40.2 27.2C40 30.8 37 32.6 32.2 32.6C27 32.6 23 31.4 22.6 28.4Z'
add('chickentray', 'Chicken thighs', [
    TUBE('M10 25.6H5.6', 'strawberry', 3.6, 'w'),
    F(('circle', 4.6, 24, 1.9), 'strawberry', 'w', ol=1.4), F(('circle', 4.6, 27.2, 1.9), 'strawberry', 'w', ol=1.4),
    F(('path', TH1), 'strawberry', 'p', tex=['M9 26C9.6 21.8 13.4 20 17.6 20.2C21 20.4 24 22 24.8 25'], tex_t='#FF9FC8', tex_w=3.6, det=['M13.6 27.4Q16.8 25.6 20.4 27.4'], det_t='#FF9FC8', det_w=2),
    TUBE('M38 25.6H42.4', 'strawberry', 3.6, 'w'),
    F(('circle', 43.4, 24, 1.9), 'strawberry', 'w', ol=1.4), F(('circle', 43.4, 27.2, 1.9), 'strawberry', 'w', ol=1.4),
    F(('path', TH2), 'strawberry', 'p', tex=['M23.6 25.8C24.2 21.8 28 20 32.2 20.2C35.8 20.4 38.8 22 39.6 25'], tex_t='#FF9FC8', tex_w=3.6, det=['M28.2 27.4Q31.4 25.6 35 27.4'], det_t='#FF9FC8', det_w=2),
    F(('path', 'M6.6 29.8H41.4L38.6 39.8Q38 42 35.6 42H12.4Q10 42 9.4 39.8Z'), 'ramune', 'p', band=.32),
    F(('rect', 25.6, 32.4, 9.6, 5, 1.4), 'lemon', 'lp', ol=1.2, cut=False),
], gloss=[G('M19.4 31.2L30.6 21.4', 2), DOT(32.6, 19.8, .9), G('M8.6 31.6H16', 2.2)], qty='1 kg tray', aisle='meat')

# 42 baking powder tin
add('bakingpowder', 'Baking powder', [
    F(cyl(12.6, 35.4, 17.6, 42), 'grape', 'p', band=.2),
    F(('rect', 13.5, 23.6, 21, 11.6), 'grape', 'w', ol=0, cut=False, det=['M13.5 23.6H34.5', 'M13.5 35.2H34.5'], det_w=1.3),
    F(('path', 'M18.6 32.4C16.6 32.4 16.2 29.4 18.4 29C18.4 26.4 21.6 25.4 23.2 27.2C24.6 25.2 28.4 25.8 28.6 28.6C30.8 28.6 31.2 32.4 29 32.4Z'), 'grape', 'p', ol=1.3, cut=False),
    F(('rect', 11.4, 13.6, 25.2, 6.6, 3), 'grape', 'm', det=['M14 16.9H34'], det_t='p', det_w=1.2),
], gloss=[G('M15.4 23V31', 2.2), DOT(15.4, 34)], qty='225 g tin', aisle='baking')

# 43 salt shaker
add('salt', 'Sea salt', [
    F(('path', 'M15.4 19.6C15.4 12.6 19.4 9.4 24 9.4C28.6 9.4 32.6 12.6 32.6 19.6Z'), 'periwinkle', 'm'),
    *[F(('circle', x, y, .95), 'periwinkle', 'w', ol=0, cut=False) for x, y in [(20.8, 15), (24, 13.4), (27.2, 15), (22.4, 17.6), (25.6, 17.6)]],
    F(('path', 'M15 21.4H33L34.6 38.6Q35 42 31.6 42H16.4Q13 42 13.4 38.6Z'), 'periwinkle', 'w', band=.46, band_t='p'),
    F(('rect', 13.6, 18.2, 20.8, 4.2, 2.1), 'periwinkle', 'm'),
    *[F(('rect', x, y, 1.8, 1.8, .4, a), 'periwinkle', 'w', ol=1, cut=False) for x, y, a in [(18.6, 34.4, 20), (23.4, 33.2, -10), (28.4, 35, 30), (21.2, 38.2, 0), (26.4, 38.4, 15)]],
], gloss=[G('M16.6 25L17.2 31'), DOT(17.5, 34), G('M18.6 15.8Q19.2 13.4 21 12.6', 1.8)], qty='1 kg', aisle='baking')

# 44 vanilla extract
add('vanilla', 'Vanilla extract', [
    F(('rect', 18.4, 6.6, 11.2, 7.6, 2), 'cocoa', 'tr', ol=0),
    F(('path', 'M19 13.6H29V17C33 18.6 35 21.2 35 24.4V38.6Q35 42 31.6 42H16.4Q13 42 13 38.6V24.4C13 21.2 15 18.6 19 17Z'), 'cocoa', 'm', band=.2),
    F(('rect', 16.2, 25, 15.6, 12.4, 2.6), 'cocoa', 'w', ol=1.4, cut=False),
    F(('path', star_path(24, 31.2, 4.4, 2.4, 5)), 'lemon', 'p', ol=1.2, cut=False),
    F(('circle', 24, 31.4, 1.2), 'lemon', 'm', ol=0, cut=False),
], gloss=[G('M16 24.6Q16.4 22 18.6 20.6', 2.2), G('M20.4 9V11.4', 2)], qty='43 mL bottle', aisle='baking')

# 45 gashapon capsule (unknown fallback); aisle colour via CSS vars
CAP = 'var(--cap-tr,#6542B3)'
add('capsule', 'Something new', [
    F(('path', 'M8 25.6A16 16 0 0 1 40 25.6Z'), CAP, 'var(--cap,#B597F2)'),
    F(('path', 'M8 25.6A16 16 0 0 0 40 25.6Z'), CAP, '#FFFFFF', band='M6 35.6H42V44H6Z', band_t='var(--cap-p,#E4D6FF)'),
    F(('rect', 6.6, 23.6, 34.8, 4.2, 2.1), CAP, '#FFFFFF'),
    F(('circle', 24, 33.6, 2.2), CAP, 'var(--cap-p,#E4D6FF)', ol=1.3, cut=False),
    F(('path', star_path(37.8, 12.4, 6.6, 3.1)), 'lemon', 'm', ol=1.5),
], gloss=[G('M12.6 20.4Q14.4 14.2 20.2 12'), DOT(24.4, 11.2)], qty='mystery item', aisle='unknown')

import os as _os
import glob as _glob
_here = _os.path.dirname(_os.path.abspath(__file__))
# the r9 batches, then the authoring batches of the art pass (icons_batch*.py, one file per batch so batches never
# touch the same file; a new sticker's DSL key is its catalog key, see catalog_map.py)
for _p in [_os.path.join(_here, _f) for _f in ('icons_b1.py', 'icons_b2.py', 'icons_b3.py', 'icons_b4.py', 'icons_r3.py')] + \
        sorted(_glob.glob(_os.path.join(_here, 'icons_batch*.py'))):
    if _os.path.exists(_p):
        exec(open(_p).read())
IBY = {i.key: i for i in ICONS}
# candy gloss is generated, never hand-placed (see gloss.py)
from gloss import autogloss
GLOSS_REPORT = []
for _i in ICONS:
    _i.gloss = autogloss(_i, GLOSS_REPORT)
# capsule: the white lower half's sub-gloss crossed the knob; keep only the main arc and its dot
for _i in ICONS:
    if _i.key == 'capsule':
        _i.gloss = [g for g in _i.gloss if g[0] == 'dot' or g[2] >= 2.6]

