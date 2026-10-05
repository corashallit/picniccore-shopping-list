"""Decorative stickers, stamp pictograms, toolbar line icons, hanko and LCD dot-matrix."""
import math
from engine import *

DECO = []


def d(*a, **k):
    DECO.append(Icon(*a, **k))


def face(cx, cy, fam, spread=5.2, s=1.0, blink=False):
    tr = C[fam][2]
    eyes = ([LINE(f'M{fmt(cx - spread - 1.4 * s)} {fmt(cy)}Q{fmt(cx - spread)} {fmt(cy + 1.4 * s)} {fmt(cx - spread + 1.4 * s)} {fmt(cy)}', fam, 1.5, t='tr', cut=False),
             LINE(f'M{fmt(cx + spread - 1.4 * s)} {fmt(cy)}Q{fmt(cx + spread)} {fmt(cy + 1.4 * s)} {fmt(cx + spread + 1.4 * s)} {fmt(cy)}', fam, 1.5, t='tr', cut=False)] if blink else
            [F(('circle', cx - spread, cy, 1.25 * s), tr, tr, ol=0, cut=False), F(('circle', cx + spread, cy, 1.25 * s), tr, tr, ol=0, cut=False)])
    return [*eyes,
            LINE(f'M{fmt(cx - 1.6 * s)} {fmt(cy + 1.6 * s)}Q{fmt(cx)} {fmt(cy + 3.2 * s)} {fmt(cx + 1.6 * s)} {fmt(cy + 1.6 * s)}', fam, 1.5, t='tr'),
            F(('ellipse', cx - spread - 2.2 * s, cy + 2.6 * s, 1.6 * s, 1 * s), 'sakura', 'm', ol=0, cut=False),
            F(('ellipse', cx + spread + 2.2 * s, cy + 2.6 * s, 1.6 * s, 1 * s), 'sakura', 'm', ol=0, cut=False)]


d('star', 'Star', [F(('path', star_path(24, 25.4, 18.4, 8.8)), 'lemon', 'm', band=.22, ol=2.2)],
  gloss=[G('M16.6 22.4L20.6 18.6', 2.6), DOT(14.4, 25)])
d('star4', 'Sparkle', [F(('path', 'M24 5C25.6 18.6 29.4 22.4 43 24C29.4 25.6 25.6 29.4 24 43C22.4 29.4 18.6 25.6 5 24C18.6 22.4 22.4 18.6 24 5Z'), 'sakura', 'm', band=.2)],
  gloss=[G('M21.2 19.4L22.6 14', 2.2)])
d('heart', 'Heart', [F(('path', heart_path(24, 25, 17)), 'sakura', 'm', band=.22)],
  gloss=[G('M12 20.4Q12.6 15.4 17.6 14.4'), DOT(11.6, 24.6)])
d('cloud', 'Cloud', [F(('path', 'M11.4 36C5.6 36 4.6 28 9.8 26.4C9 19.6 16.6 15.8 21.2 19.6C23.4 12.8 34 12.6 35.8 20.4C42.4 19.8 45 28.4 39.6 31.6C41.4 35 38.6 36 36.6 36Z'), 'ramune', 'w', band=.24, band_t='p')],
  gloss=[G('M10.4 30Q10.8 27.4 13 26.8', 2.2), G('M23.4 20Q24.8 16.8 28.2 16.4')])
# the face sits wholly inside the body's trace, the stripes behind it on the back half, the stinger tucked behind the
# body, and the gloss and its dot inside the right shoulder (inset 2.6u), clear of the stripes (the head is HEADS['bee'])
d('bee', 'Bee', [
    F(('ellipse', 18.4, 15.6, 6.4, 8.6, -28), 'ramune', 'w', ol=1.8),
    F(('ellipse', 29.2, 14.4, 6.4, 8.6, 22), 'ramune', 'w', ol=1.8),
    LINE('M35.4 28.8L42.4 30.2', 'cocoa', 2.2),
    F(('ellipse', 24, 29, 14.4, 11.4), 'lemon', 'm', band=.2, tex=['M25.7 16V42', 'M31.3 16V42'], tex_t='#7A4A28', tex_w=2.8),
    *face(17.5, 28.4, 'lemon', 3.0, .85),
], gloss=[G('M34.2 24.5Q36 26.9 35.8 29.6', 2.2), DOT(34.6, 32.8, .8)])
d('ladybug', 'Ladybug', [
    F(('circle', 24, 13.4, 6.4), 'periwinkle', 'tr', ol=0),
    LINE('M21 8.4C19.6 5.6 17.4 5 16 5.8M27 8.4C28.4 5.6 30.6 5 32 5.8', 'periwinkle', 1.6),
    F(('path', 'M24 15.4C33.6 15.4 40.6 21.6 40.6 29.4C40.6 37 33.4 41.6 24 41.6C14.6 41.6 7.4 37 7.4 29.4C7.4 21.6 14.4 15.4 24 15.4Z'), 'strawberry', 'm', band=.2,
      det=['M24 16V41.4']),
    *[F(('circle', x, y, r), 'periwinkle', 'tr', ol=0, cut=False) for x, y, r in [(16.4, 26, 3), (31.6, 26, 3), (18, 34.6, 2.4), (30, 34.6, 2.4)]],
], gloss=[G('M11.8 27Q12.6 21.6 17.6 19.2'), DOT(11.6, 30.8)])
d('ladybugfly', 'Ladybug in flight', [
    F(('ellipse', 11.4, 22.4, 9.6, 5.4, -30), 'ramune', 'w', ol=1.6, det=['M5 26L17 18'], det_t='p', det_w=1.2),
    F(('ellipse', 36.6, 22.4, 9.6, 5.4, 30), 'ramune', 'w', ol=1.6, det=['M43 26L31 18'], det_t='p', det_w=1.2),
    F(('circle', 24, 15.4, 6), 'periwinkle', 'tr', ol=0),
    LINE('M21 10.6C19.6 7.8 17.4 7.2 16 8M27 10.6C28.4 7.8 30.6 7.2 32 8', 'periwinkle', 1.6),
    F(('path', 'M24 19.6C30.4 20.4 34.6 26 33.4 33.4C32.6 38.4 28.4 41.6 24 40.8Z'), 'strawberry', 'm', band=.2, tf='rotate(14 24 20)'),
    F(('path', 'M24 19.6C17.6 20.4 13.4 26 14.6 33.4C15.4 38.4 19.6 41.6 24 40.8Z'), 'strawberry', 'm', band=.2, tf='rotate(-14 24 20)'),
    *[F(('circle', x, y, r), 'periwinkle', 'tr', ol=0, cut=False) for x, y, r in [(19, 28, 2.4), (29, 28, 2.4), (20.6, 35, 2), (27.4, 35, 2)]],
], gloss=[G('M16.6 27Q16.8 24 19 22.6', 2)])
d('ant', 'Ant with strawberry', [
    F(berry(26, 6.6, 15, 17.4), 'strawberry', 'm', band=.2),
    F(('path', 'M21.6 7.4Q23.6 4 25.6 6.4Q26 2.6 28 4.6Q30 3.4 30.4 7Q28 9.8 21.6 7.4Z'), 'melon', 'm', ol=1.4),
    LINE('M14 30L18.6 22M20.4 30.4L22.6 22.4', 'cocoa', 1.8),
    LINE('M10.6 41L13.4 35.6M17.4 41.4L18 35.8M24.8 41L22 35.8M30 41.4L26.6 36.4', 'cocoa', 1.8),
    F(('circle', 12.4, 32, 3.6), 'cocoa', 'm'),
    F(('ellipse', 19.4, 34.6, 4.2, 3.4), 'cocoa', 'm'),
    F(('ellipse', 27.4, 36, 5, 4), 'cocoa', 'm', band=.3),
    LINE('M10.6 29.4C9 26.6 7.4 26 6.2 26.6', 'cocoa', 1.6),
], gloss=[G('M20.6 13Q21.8 10.4 24.6 10', 2.2), DOT(20.8, 16.4, .8)])
# r10 (WP2) · the ant parade's other four ants (egg 17, 22 dp): ants always carry food, never stand on it (§4.27)
d('antcrumb', 'Ant with a cookie', [
    F(('circle', 25.4, 14.6, 8.6), 'cocoa', 'p', band=.24, det=['M21.6 11.4h.1M28.4 12.6h.1M24.6 17.8h.1M29.4 18h.1'], det_t='tr', det_w=2.2),
    LINE('M14 30L18.6 22M20.4 30.4L22.6 22.4', 'cocoa', 1.8),
    LINE('M10.6 41L13.4 35.6M17.4 41.4L18 35.8M24.8 41L22 35.8M30 41.4L26.6 36.4', 'cocoa', 1.8),
    F(('circle', 12.4, 32, 3.6), 'cocoa', 'm'),
    F(('ellipse', 19.4, 34.6, 4.2, 3.4), 'cocoa', 'm'),
    F(('ellipse', 27.4, 36, 5, 4), 'cocoa', 'm', band=.3),
    LINE('M10.6 29.4C9 26.6 7.4 26 6.2 26.6', 'cocoa', 1.6),
], gloss=[G('M19.4 13.2Q20.2 9.8 23.6 8.6', 2.2), DOT(19.2, 16.4, .8)])
d('blanket', 'Picnic blanket', [
    F(('path', 'M8 15.4L38.4 9.6Q41 9.2 41.4 11.8L42 35.6Q42 38 39.6 38.4L11.2 42Q8.6 42.4 8.2 39.8Z'), 'strawberry', 'p',
      tex=['M14 8V44', 'M20.6 8V44', 'M27.2 8V44', 'M33.8 8V44', 'M6 18.6L44 12', 'M6 25.6L44 19', 'M6 32.6L44 26', 'M6 39.6L44 33'], tex_w=3.6),
    F(('path', 'M33.6 37.4L41.6 36.4L41.4 28.6Z'), 'strawberry', 'w', ol=1.8),
], gloss=[G('M11.4 20.6L12 30', 2.4), DOT(12.2, 33.6)])
d('sun', 'Sun', [
    *[F(('rect', 22.2, 3.6, 3.6, 7, 1.8), 'lemon', 'm', ol=1.6, tf=f'rotate({a} 24 24)') for a in range(0, 360, 45)],
    F(('circle', 24, 24, 11.6), 'lemon', 'm', band=.22),
    *face(24, 23.4, 'lemon', 4.2, .9),
], gloss=[G('M15.6 21.4Q16.6 17 20.8 15.6', 2.4)])
d('glowstar', 'Glow star', [F(('path', star_path(24, 25.4, 18.4, 8.8)), '#7FD6A6', '#C9FFDE', ol=1.8)], gloss=[G('M16.6 22.4L20.6 18.6', 2.6)])
d('moon', 'Full moon', [
    F(('circle', 24, 24, 17), 'lemon', 'p', band=.22),
    F(('circle', 30.4, 19, 3.6), 'lemon', 'm', ol=1.4, cut=False),
    F(('circle', 20, 31, 2.6), 'lemon', 'm', ol=1.4, cut=False),
    F(('circle', 31.6, 30.4, 1.8), 'lemon', 'm', ol=1.2, cut=False),
], gloss=[G('M11.6 22.4Q12.8 15.6 18.6 12.4'), DOT(11.4, 26.6)])
d('flower', 'Flower', [
    *[F(('ellipse', 24, 13.6, 6, 8.4), 'sakura', 'p', band=.25, tf=f'rotate({a} 24 24.4)') for a in range(0, 360, 72)],
    F(('circle', 24, 24.4, 5.6), 'lemon', 'm'),
], gloss=[G('M20.4 9.8Q21.4 7.6 23.6 7', 2)])
d('teacup', 'Teacup', [
    TUBE('M34.6 23C42 22.4 42 33.6 34 33', 'mint', 5, 'p'),
    F(('path', 'M8.6 19.4H37.4V27.4C37.4 35.2 31.6 40 23 40C14.4 40 8.6 35.2 8.6 27.4Z'), 'mint', 'p', band=.24),
    F(('ellipse', 23, 19.4, 14.4, 3.8), 'mint', 'm'),
    F(('rect', 5.6, 38.4, 34.8, 4, 2), 'mint', 'm'),
    F(('path', heart_path(23, 29.6, 3.6)), 'sakura', 'm', ol=1.3, cut=False),
    LINE('M18.4 13.6C16.4 11 20.4 9.6 18.4 6.4M25.4 13.6C23.4 11 27.4 9.6 25.4 6.4', 'mint', 1.8, t='m'),
], gloss=[G('M12.6 24.6Q12.8 30 15.6 33.6', 2.4)])
d('sandwich', 'Sandwich', [
    F(('path', 'M8.4 34.4L24 9.6Q25.4 7.6 26.8 9.6L40.4 34.4Z'), 'cocoa', 'p', ol=2),
    F(('path', 'M7.2 34L41.6 34L40 37.2Q38.6 39.6 36 38.6Q33.4 40.8 30.6 38.6Q28 40.8 25.4 38.6Q22.6 40.8 20 38.6Q17.2 40.8 14.6 38.6Q11.4 39.8 9.4 37.6Z'), 'melon', 'm', ol=1.6),
    F(('rect', 7.4, 36.6, 34.2, 5.6, 2.4), 'cocoa', 'p'),
    F(('rect', 8.4, 32.4, 32, 3.4, 1.6), 'sakura', 'm', ol=1.4),
], gloss=[G('M15.4 28.6L21.4 18.2', 2.4)])
d('envelope', 'Envelope', [
    F(('rect', 6.6, 12.6, 34.8, 26, 4), 'strawberry', 'w', band=.22, band_t='p', det=['M8 15L24 27.6L40 15']),
    F(('path', heart_path(24, 27.2, 5.4)), 'strawberry', 'm', ol=1.6),
], gloss=[G('M10.2 21V30', 2.4)])
d('cassette', 'Cassette', [
    F(('rect', 5.6, 11.6, 36.8, 25.4, 4), 'sakura', 'p', band=.22),
    F(('rect', 11, 16.4, 26, 11, 3), 'sakura', 'w', ol=1.6, cut=False),
    F(('circle', 17.8, 21.9, 3.2), 'sakura', 'm', ol=1.4, cut=False, det=['M17.8 19.6V24.2M15.6 21.9H20'], det_t='w', det_w=1),
    F(('circle', 30.2, 21.9, 3.2), 'sakura', 'm', ol=1.4, cut=False, det=['M30.2 19.6V24.2M28 21.9H32.4'], det_t='w', det_w=1),
    F(('path', 'M13 37L15.6 31H32.4L35 37Z'), 'sakura', 'm', ol=1.6, cut=False),
], gloss=[G('M8.8 15.6V20', 2.2)])
d('camera', 'Instant camera', [
    F(('rect', 6, 13.4, 36, 26.6, 5), 'ramune', 'p', band=.24),
    F(('rect', 8.6, 10, 11, 5.4, 2), 'ramune', 'm'),
    F(('circle', 24, 27.4, 8.2), 'ramune', 'w', ol=2),
    F(('circle', 24, 27.4, 4.4), 'ramune', 'm', ol=1.6, cut=False),
    F(('rect', 32.4, 17, 5.6, 3.6, 1.4), 'lemon', 'p', ol=1.3, cut=False),
], gloss=[G('M9.6 19.6V25', 2.4), G('M20.2 25.6Q21 23.4 23.2 23', 1.6)])
d('basketdeco', 'Picnic basket', [
    TUBE('M13.6 24C13.6 8 34.4 8 34.4 24', 'cocoa', 4.6, 'p'),
    F(('path', 'M6 24.4H42L39.6 39.4Q39 42 36.4 42H11.6Q9 42 8.4 39.4Z'), 'cocoa', 'p', band=.26, tex=['M5 30H43', 'M5 35.6H43', 'M13 24V42', 'M19 24V42', 'M25 24V42', 'M31 24V42', 'M37 24V42']),
    F(('rect', 4.4, 21.6, 39.2, 5, 2.5), 'cocoa', 'm'),
    F(('path', 'M7 22.4H20L15 31.4Q14 33 13 31.4Z'), 'strawberry', 'p', tex=['M10 20V34', 'M14 20V34', 'M18 20V34', 'M6 25.4H22', 'M6 29.4H22'], tex_w=2),
], gloss=[G('M8 23.8H11', 2.2)])
d('scissors', 'Scissors', [
    TUBE('M14 13L33 32', 'periwinkle', 4.2, 'w'), TUBE('M14 35L33 16', 'periwinkle', 4.2, 'w'),
    F(('circle', 12, 13, 5.4), 'periwinkle', 'p', ol=2), F(('circle', 12, 35, 5.4), 'periwinkle', 'p', ol=2),
    F(('circle', 12, 13, 2.2), 'periwinkle', 'w', ol=1.6, cut=False), F(('circle', 12, 35, 2.2), 'periwinkle', 'w', ol=1.6, cut=False),
    F(('circle', 23.6, 24, 1.6), 'periwinkle', 'm', ol=1.2, cut=False),
])
# the tail ring is drawn behind the body, both ends tucked inside it, with a white gap between ring and body; the loaf's
# left end is tucked behind the head (no doubled outline); the ears are mirror images about the face's axis (x 15.8)
# with their bases sunk in the head; the gloss and its dot sit inside the right shoulder (inset 3u), clear of the stripes
d('cat', 'Sleeping tabby', [
    TUBE('M34.6 35.6C37.4 38.4 42.1 37.6 42.1 31.8C42.1 27.4 37.8 25.2 34.4 27.4', 'peach', 4.6, 'm'),
    F(('path', 'M12.8 31.4C14.8 27 18.8 19.6 25 19.6C31.4 19.6 37.6 24.4 37.6 31.6C37.6 37.6 31.4 41.4 23.4 41.4C19.6 41.4 17.4 40.4 16.4 37.6Z'),
      'peach', 'm', band=.2, tex=['M25.8 18V25.2', 'M29.2 18.2V24.4'], tex_t='deep', tex_w=2.4),
    F(('path', 'M8.4 27.6L9.4 18.6Q9.8 17.2 10.8 18.2L15.2 24.6Z'), 'peach', 'm', ol=1.8),
    F(('path', 'M23.2 27.6L22.2 18.6Q21.8 17.2 20.8 18.2L16.4 24.6Z'), 'peach', 'm', ol=1.8),
    F(('ellipse', 15.8, 31.4, 9.6, 7.6), 'peach', 'p', ol=1.8),
    LINE('M10.6 30.4Q12.2 31.8 13.8 30.4M17.8 30.4Q19.4 31.8 21 30.4', 'peach', 1.4, t='tr'),
    F(('ellipse', 9.8, 33.6, 1.6, 1), 'sakura', 'm', ol=0, cut=False),
    F(('ellipse', 22, 33.6, 1.6, 1), 'sakura', 'm', ol=0, cut=False),
    LINE('M15.2 33.6l.6 .6l.6 -.6', 'peach', 1.2, t='tr'),
], gloss=[G('M31.7 25.2Q33.9 27.2 34.4 29.8', 2.2), DOT(34.3, 33.3, .8)])
d('pumpkin', 'Pumpkin', [
    F(('rect', 22, 8.4, 4, 7, 1.6), 'melon', 'm', ol=1.6),
    F(('ellipse', 15.4, 28.6, 9.6, 12), 'peach', 'm', band=.22),
    F(('ellipse', 32.6, 28.6, 9.6, 12), 'peach', 'm', band=.22),
    F(('ellipse', 24, 28.6, 9.6, 13), 'peach', 'm', band=.22),
], gloss=[G('M18.6 22Q19.4 18.4 22.4 17.2', 2.2), G('M8.8 26Q9.2 22.4 11.4 20.4', 2)])
d('mapleleaf', 'Maple leaf', [
    F(('path', 'M24 5.6L27.2 12.4L31.6 10.4L30.4 19.4L37 13.6L38.6 17.6L43 17L40.4 23.6L42.6 25.4L33.4 32.4L34.4 35.4L25.4 33.8V42H22.6V33.8L13.6 35.4L14.6 32.4L5.4 25.4L7.6 23.6L5 17L9.4 17.6L11 13.6L17.6 19.4L16.4 10.4L20.8 12.4Z'), 'strawberry', 'm', band=.2),
], gloss=[G('M14.8 21.8L18 24.6', 2.2)])
d('snowflake', 'Snowflake', [
    LINE('M24 6V42M8.4 15L39.6 33M8.4 33L39.6 15', 'ramune', 6.4, t='tr'),
    LINE('M24 6V42M8.4 15L39.6 33M8.4 33L39.6 15', 'ramune', 3, t='p'),
    F(('circle', 24, 24, 5), 'ramune', 'w'),
])
d('cake', 'Birthday cake', [
    LINE('M24 12.4V7.4', 'lemon', 3.4, t='tr'), F(('path', 'M24 3.6C26 5.8 26 7.6 24 8.6C22 7.6 22 5.8 24 3.6Z'), 'peach', 'm', ol=1.3),
    F(('rect', 21.8, 9.6, 4.4, 9, 1.4), 'ramune', 'p', ol=1.4),
    F(('rect', 8.6, 17.6, 30.8, 10, 4), 'sakura', 'p', ol=2, det=['M10 23.6Q13 26.4 16 23.6Q19 26.4 22 23.6Q25 26.4 28 23.6Q31 26.4 34 23.6Q36 25.6 38 23.6'], det_t='w', det_w=1.8),
    F(('rect', 5.6, 26.6, 36.8, 14.4, 4), 'sakura', 'm', band=.26),
    F(('rect', 3.6, 39.4, 40.8, 3.4, 1.7), 'lemon', 'm', ol=1.6),
], gloss=[G('M9.6 31V35', 2.2), G('M12 20.4H16', 2)])
d('ribbon', 'Ribbon bow', [
    F(('path', 'M24 22C19 14.6 9.6 12.6 7.6 17.6C6 22 9.6 28.4 24 26.4Z'), 'sakura', 'm', band=.25),
    F(('path', 'M24 22C29 14.6 38.4 12.6 40.4 17.6C42 22 38.4 28.4 24 26.4Z'), 'sakura', 'm', band=.25),
    F(('path', 'M21.6 25.6L15.6 39.4L19.6 38.4L21.6 41.4L25 26.4Z'), 'sakura', 'm'),
    F(('path', 'M26.4 25.6L32.4 39.4L28.4 38.4L26.4 41.4L23 26.4Z'), 'sakura', 'm'),
    F(('rect', 20.6, 19.6, 6.8, 8, 2.6), 'sakura', 'p'),
], gloss=[G('M11 18.6Q12 16.2 15 16.4', 2.2)])
d('redenvelope', 'Red envelope', [
    F(('rect', 11, 6.6, 26, 35, 4), 'strawberry', 'm', band=.2, det=['M11.6 17Q24 24.6 36.4 17']),
    F(('circle', 24, 21.4, 4.6), 'lemon', 'm', ol=1.6),
], gloss=[G('M14.6 25V33', 2.4)])
d('kira', 'Holo sparkle', [F(('path', 'M24 2.2C26 17.6 30.4 22 45.8 24C30.4 26 26 30.4 24 45.8C22 30.4 17.6 26 2.2 24C17.6 22 22 17.6 24 2.2Z'), '#3E4BB0', 'url(#sticker-holo)', ol=2.2),
                        F(('path', 'M24 12.4C25 20.6 27.4 23 35.6 24C27.4 25 25 27.4 24 35.6C23 27.4 20.6 25 12.4 24C20.6 23 23 20.6 24 12.4Z'), '#FFFFFF', '#FFFFFF', ol=0, cut=False, op='.55')],
  gloss=[G('M20.6 16.4L22.4 9.6', 2.4)])
d('petal', 'Sakura petal', [F(('path', 'M8.6 35.6C7.4 22.6 18.4 9.6 36.6 9.2C40.2 9.2 41.8 11.6 40.6 14.6L38.8 16.8L41.4 19.8C38 31.8 24.4 40 12.8 39.8Q9 39.6 8.6 35.6Z'), 'sakura', 'm', band=.24, det=['M14.4 33.2Q22 22 34 15.6'], det_t='p', det_w=1.6)],
  gloss=[G('M12.6 28.6Q14.6 20.6 22 15.6', 2.4)])


# r10 (WP2) · the decor the app needs that the mockup drew inline or not at all
UMBRELLA = 'M6 25C6 13.4 14 6.6 24 6.6S42 13.4 42 25C39.4 22.4 35.6 22.4 33 25C30.4 22.4 26.6 22.4 24 25C21.4 22.4 17.6 22.4 15 25C12.4 22.4 8.6 22.4 6 25Z'
d('umbrella', 'Sakura umbrella', [           # egg 16: shelters the add bar's left end on a rainy day
    TUBE('M24 24V35.6Q24 39.6 20.6 39.6Q17.6 39.6 17.6 36.6', 'cocoa', 4.4, 'm'),
    F(('path', UMBRELLA), 'sakura', 'm', band=.2, det=['M24 7.4V24.4', 'M15 24.6Q15.8 13.4 24 7.4', 'M33 24.6Q32.2 13.4 24 7.4']),
    F(('circle', 24, 5.6, 1.8), 'sakura', 'p', ol=1.5),
], gloss=[G('M11.2 18.6Q12.8 13.4 17.6 10.6', 2.4), DOT(10.2, 21.6)])


def _fluff(cx, cy, r, n=11, bulge=1.2):
    """a fluffy round outline: n scalloped puffs round a circle (a quadratic bulge between neighbouring points)"""
    pt = lambda rr, a: (cx + rr * math.cos(a), cy + rr * math.sin(a))
    a0 = -math.pi / 2
    d = 'M%s %s' % tuple(fmt(v) for v in pt(r, a0))
    for i in range(n):
        a1, am = a0 + 2 * math.pi * (i + 1) / n, a0 + 2 * math.pi * (i + .5) / n
        (qx, qy), (x, y) = pt(r * bulge, am), pt(r, a1)
        d += f'Q{fmt(qx)} {fmt(qy)} {fmt(x)} {fmt(y)}'
    return d + 'Z'


d('dustbunny', 'Dust bunny', [               # the empty pantry shelf (§6.13)
    F(('path', _fluff(22.6, 29.6, 10.4)), 'periwinkle', 'p', band=.24,
      det=['M14.6 22.6Q15.4 21.4 16.8 21.2', 'M28.4 35.8Q29.8 35.4 30.6 34.2'], det_t='m', det_w=1.5),
    F(('circle', 18.4, 28.4, 1.3), 'periwinkle', 'tr', ol=0, cut=False),
    F(('circle', 26.4, 28.4, 1.3), 'periwinkle', 'tr', ol=0, cut=False),
    F(('circle', 39.4, 38.6, 2.4), 'periwinkle', 'p', ol=1.6),
    F(('circle', 41.2, 31.6, 1.6), 'periwinkle', 'p', ol=1.4),
], gloss=[G('M13.6 26.4Q14 23.4 16.2 21.8', 2.2)])
# hand-placed gloss is held to the same rule as the generated gloss (gloss.clean): cut back where it would cross a trace
# or a part painted over its shape
from gloss import trim_gloss as _trim_gloss
for _d in DECO:
    _d.gloss = _trim_gloss(_d.parts, _d.gloss)
DBY = {i.key: i for i in DECO}


# ---------- stamp-card pictograms (monochrome, fill currentColor, 24 grid) ----------
def pg_heart():
    return f'<path d="{heart_path(12, 12.6, 8.6)}"/>'


PICTO = {
    'strawberry': '<path d="M5 9.6C5 5.6 19 5.6 19 9.6C19 14.6 14.6 20.6 12 20.6C9.4 20.6 5 14.6 5 9.6Z"/><path d="M7.4 6.4Q9.4 3.6 11 5.2Q12 2 13 5.2Q14.6 3.6 16.6 6.4Q12 8.4 7.4 6.4Z"/><g fill="#fff"><circle cx="10" cy="11" r="1"/><circle cx="14" cy="11" r="1"/><circle cx="12" cy="14.6" r="1"/></g>',
    'bee': '<ellipse cx="8.6" cy="7.4" rx="3.4" ry="4.4" transform="rotate(-26 8.6 7.4)"/><ellipse cx="15.4" cy="7" rx="3.4" ry="4.4" transform="rotate(22 15.4 7)"/><ellipse cx="12" cy="14.6" rx="8" ry="6.2"/><g fill="#fff"><rect x="9.2" y="9" width="2" height="11.4" rx="1"/><rect x="13.6" y="9" width="2" height="11.4" rx="1"/></g>',
    'star': f'<path d="{star_path(12, 12.6, 10, 4.8)}" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>',
    'heart': pg_heart(),
    'cloud': '<path d="M6 18C2.4 18 1.8 13 5 12C4.6 7.8 9.2 5.4 12 7.8C13.4 3.6 20 3.4 21.2 8.4C25 8 26.4 13.6 22.6 15.6C23.6 17.6 22 18 20.8 18Z" transform="translate(-1 0)"/>',
    'flower': ''.join(f'<ellipse cx="12" cy="6.6" rx="3.6" ry="4.8" transform="rotate({a} 12 12)"/>' for a in range(0, 360, 72)) + '<circle cx="12" cy="12" r="3" fill="#fff"/>',
    'ladybug': '<circle cx="12" cy="6.4" r="3.4"/><path d="M12 8C17.4 8 20.6 11.6 20.6 15.4C20.6 19 17 21 12 21C7 21 3.4 19 3.4 15.4C3.4 11.6 6.6 8 12 8Z"/><g fill="#fff"><rect x="11.3" y="8.6" width="1.4" height="12" rx=".7"/><circle cx="8" cy="14" r="1.6"/><circle cx="16" cy="14" r="1.6"/><circle cx="8.8" cy="18" r="1.2"/><circle cx="15.2" cy="18" r="1.2"/></g>',
    'teacup': '<path d="M3.6 9H18.4V12.6C18.4 17 15.6 19.6 11 19.6C6.4 19.6 3.6 17 3.6 12.6Z"/><path d="M18 10.4C22 10.4 22 16 17.6 16" fill="none" stroke="currentColor" stroke-width="2.2"/><rect x="2" y="19" width="18" height="2.2" rx="1.1"/><path d="M8.6 7C7.6 5.6 9.6 4.8 8.6 3M13.4 7C12.4 5.6 14.4 4.8 13.4 3" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>',
    'sandwich': '<path d="M3.6 16.6L11.2 4.4Q12 3.2 12.8 4.4L20.4 16.6Z"/><rect x="3" y="15.6" width="18" height="2.6" rx="1.3" fill="#fff" stroke="currentColor" stroke-width="1.2"/><rect x="3" y="18.2" width="18" height="3" rx="1.5"/>',
    'sun': ''.join(f'<rect x="10.8" y="1.4" width="2.4" height="4" rx="1.2" transform="rotate({a} 12 12)"/>' for a in range(0, 360, 45)) + '<circle cx="12" cy="12" r="6"/>',
    # r9 · seasonal sheet stamps (egg 11)
    'maple': '<path d="M12 2L13.6 6.4L16.4 5L15.8 9.4L19.6 8L18.6 11L21 12L17.4 15L18 17L13 16.4L12.8 21.6H11.2L11 16.4L6 17L6.6 15L3 12L5.4 11L4.4 8L8.2 9.4L7.6 5L10.4 6.4Z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>',
    'pumpkin': '<path d="M12 7C6 7 3 10 3 14C3 18 6.5 21 12 21C17.5 21 21 18 21 14C21 10 18 7 12 7Z"/><path d="M12 7.6V20.6M7.6 8.6C6 12 6 16 7.6 20.2M16.4 8.6C18 12 18 16 16.4 20.2" stroke="#fff" stroke-width="1.4" fill="none"/><path d="M12 7.6C12 5 13 3.6 15 3" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round"/>',
    'ghost': '<path d="M5 21V11C5 6.6 8 3.6 12 3.6S19 6.6 19 11V21L16.6 19.2L14.3 21L12 19.2L9.7 21L7.4 19.2Z"/><circle cx="9.4" cy="11" r="1.5" fill="#fff"/><circle cx="14.6" cy="11" r="1.5" fill="#fff"/>',
    'snowflake': '<g stroke="currentColor" stroke-width="2.2" stroke-linecap="round" fill="none"><path d="M12 2.6V21.4M3.9 7.3L20.1 16.7M3.9 16.7L20.1 7.3"/><path d="M9.6 4.4L12 6.6L14.4 4.4M9.6 19.6L12 17.4L14.4 19.6"/></g>',
    'envelope': ('<rect x="2.4" y="5.4" width="19.2" height="13.6" rx="2.4"/>'
                 '<path d="M4 7.2L12 13.2L20 7.2" stroke="#fff" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'
                 f'<path d="{heart_path(12, 14.6, 2.4)}" fill="#fff"/>'),
    # r10 (WP2) · "Everything else": the gashapon capsule (top half inked, bottom half paper in an ink ring, a sparkle)
    'capsule': ('<path d="M3.2 11.6A8.8 8.8 0 0 1 20.8 11.6Z"/>'
                '<path d="M4.1 13.2A7.9 7.9 0 0 0 19.9 13.2Z" fill="#fff"/>'
                '<path d="M4.1 13.2A7.9 7.9 0 0 0 19.9 13.2Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>'
                '<rect x="2.6" y="11.6" width="18.8" height="1.6" rx=".8"/>'
                '<path d="M12 4.6L12.7 6.8L14.9 7.5L12.7 8.2L12 10.4L11.3 8.2L9.1 7.5L11.3 6.8Z" fill="#fff"/>'),
}


def picto_symbols():
    return ''.join(f'<symbol id="sticker-pg-{k}" viewBox="0 0 24 24"><g fill="currentColor">{v}</g></symbol>' for k, v in PICTO.items())


# ---------- toolbar line icons (24dp, ballpoint 2u line) ----------
TB = {
    'more': '<circle cx="12" cy="5.6" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="12" cy="18.4" r="1.6"/>',
    'sort': '<path d="M5 6.5H19M5 12H15M5 17.5H11"/><path d="M18 12.5V20M15.4 17.6L18 20.2L20.6 17.6"/>',
    'back': '<path d="M14.6 5.6L8.2 12L14.6 18.4M8.6 12H20"/>',
    'close': '<path d="M6.6 6.6L17.4 17.4M17.4 6.6L6.6 17.4"/>',
    'plus': '<path d="M12 5V19M5 12H19"/>',
    'search': '<circle cx="10.6" cy="10.6" r="6"/><path d="M15.2 15.2L19.6 19.6"/>',
    'gear': '<circle cx="12" cy="12" r="3.2"/><path d="M12 3.4V6M12 18V20.6M3.4 12H6M18 12H20.6M5.9 5.9L7.8 7.8M16.2 16.2L18.1 18.1M5.9 18.1L7.8 16.2M16.2 7.8L18.1 5.9"/>',
    'pencil': '<path d="M5 19L6 14.6L15.6 5Q16.6 4 17.6 5L19 6.4Q20 7.4 19 8.4L9.4 18Z"/><path d="M14 6.6L17.4 10"/>',
    'chev-down': '<path d="M6.6 9.4L12 14.8L17.4 9.4"/>',
    'chev-up': '<path d="M6.6 14.6L12 9.2L17.4 14.6"/>',
    'drag': '<circle cx="9" cy="6.4" r="1.6"/><circle cx="15" cy="6.4" r="1.6"/><circle cx="9" cy="12" r="1.6"/><circle cx="15" cy="12" r="1.6"/><circle cx="9" cy="17.6" r="1.6"/><circle cx="15" cy="17.6" r="1.6"/>',
    'eye': '<path d="M2.8 12C5.4 7.4 8.6 5.6 12 5.6C15.4 5.6 18.6 7.4 21.2 12C18.6 16.6 15.4 18.4 12 18.4C8.6 18.4 5.4 16.6 2.8 12Z"/><circle cx="12" cy="12" r="2.8"/>',
    'resort': '<path d="M4.6 9.4C5.6 6.2 8.6 4.2 12 4.2C15.2 4.2 17.8 6 19 8.6M19.4 4.6V8.8H15.2M19.4 14.6C18.4 17.8 15.4 19.8 12 19.8C8.8 19.8 6.2 18 5 15.4M4.6 19.4V15.2H8.8"/>',
    'undo': '<path d="M9 6L4.6 10.4L9 14.8"/><path d="M5 10.4H14.4C17.4 10.4 19.4 12.4 19.4 15S17.4 19.6 14.4 19.6H11"/>',
    'web': '<circle cx="12" cy="12" r="8"/><path d="M4 12H20M12 4C9.6 6.4 9.6 17.6 12 20M12 4C14.4 6.4 14.4 17.6 12 20"/>',
    'check': '<path d="M5.4 12.6L10 17L18.6 7.6"/>',
    'stop': '<rect x="6.6" y="6.6" width="10.8" height="10.8" rx="2.6"/>',
    'mic': '<rect x="9" y="3.6" width="6" height="11" rx="3"/><path d="M5.6 11.6C5.6 15.2 8.4 17.8 12 17.8S18.4 15.2 18.4 11.6M12 17.8V20.6"/>',
    'cam': '<rect x="3.6" y="7" width="16.8" height="12" rx="3"/><circle cx="12" cy="13" r="3.4"/><path d="M8.6 7L10 4.6H14L15.4 7"/>',
    'key': '<circle cx="8" cy="12" r="4"/><path d="M12 12H20.4M17.4 12V15.4M20.2 12V14.4"/>',
    'link': '<path d="M10 14L14 10M8.4 11.4L6.6 13.2C5 14.8 5 17.2 6.6 18.6S10.4 20 11.8 18.6L13.6 16.8M10.4 7.2L12.2 5.4C13.8 3.8 16.2 3.8 17.6 5.4S19 9.2 17.6 10.6L15.8 12.4"/>',
    'sparkle': '<path d="M12 3.6C12.8 9 15 11.2 20.4 12C15 12.8 12.8 15 12 20.4C11.2 15 9 12.8 3.6 12C9 11.2 11.2 9 12 3.6Z"/>',
    # r10 (WP2) · the rest of the §3.4 set, and the few small glyphs other sections name
    'gallery': '<rect x="3.6" y="5" width="16.8" height="14" rx="3"/><path d="M4.8 17.6L9.4 12.6L12.8 16L15.2 13.6L19.2 17.6"/><circle cx="15.4" cy="9.4" r="1.6"/>',
    'send': '<path d="M4.2 11.4L19.8 4.2L13.2 19.8L11.2 12.8Z"/><path d="M11.2 12.8L19.8 4.2"/>',
    'store': ('<path d="M4.4 9.6L6.2 4.6H17.8L19.6 9.6"/><path d="M4.4 9.6Q6.3 12.2 8.2 9.6Q10.1 12.2 12 9.6Q13.9 12.2 15.8 9.6Q17.7 12.2 19.6 9.6"/>'
              '<path d="M5.8 12V19.6H18.2V12"/><path d="M10 19.6V15.4H14V19.6"/>'),
    'lists': '<path d="M9.6 6.6H19.4M9.6 12H19.4M9.6 17.4H19.4"/><path d="M5 6.6h.1M5 12h.1M5 17.4h.1" stroke-width="2.8"/>',
    'trash': '<path d="M4.6 7H19.4"/><path d="M9.4 7V4.8H14.6V7"/><path d="M6.6 7L7.6 19.4H16.4L17.4 7"/><path d="M10.2 10.6V16M13.8 10.6V16"/>',
    'scissors': '<circle cx="6.6" cy="7" r="2.6"/><circle cx="6.6" cy="17" r="2.6"/><path d="M8.8 8.4L19.6 17M8.8 15.6L19.6 7"/>',
    'album': ('<rect x="6" y="3.6" width="13.4" height="16.8" rx="2.4"/><path d="M6 7.4H3.8M6 12H3.8M6 16.6H3.8"/>'
              f'<path d="{heart_path(12.7, 12.2, 3)}"/>'),
    'book': '<path d="M12 6.6C9.8 5 7 4.6 3.6 5V18.6C7 18.2 9.8 18.6 12 20.2C14.2 18.6 17 18.2 20.4 18.6V5C17 4.6 14.2 5 12 6.6Z"/><path d="M12 6.6V20.2"/>',
    'cassette': ('<rect x="3" y="5.6" width="18" height="12.8" rx="2.4"/><circle cx="8.6" cy="10.8" r="1.8"/><circle cx="15.4" cy="10.8" r="1.8"/>'
                 '<path d="M10.4 10.8H13.6"/><path d="M7 18.4L8.4 15.2H15.6L17 18.4"/>'),
    'envelope': ('<rect x="3.4" y="5.4" width="17.2" height="13.2" rx="2.4"/><path d="M4 6.4L12 12.4L20 6.4"/>'
                 f'<path d="{heart_path(12, 13.6, 2.6)}" fill="currentColor" stroke-width="1.2"/>'),
    'polaroid': ('<rect x="3.4" y="5.4" width="17.2" height="15.2" rx="3"/><path d="M6.6 5.4V3.6H10.6V5.4"/><circle cx="12" cy="11.4" r="2.8"/>'
                 '<path d="M16.8 8.4h.1"/><path d="M7.8 17.4H16.2"/>'),
    'jar': ('<path d="M7.4 4.2H16.6"/><path d="M8.2 4.2V7.2Q5.4 8.6 5.4 11.6V17.8Q5.4 20 7.6 20H16.4Q18.6 20 18.6 17.8V11.6Q18.6 8.6 15.8 7.2V4.2"/>'
            '<path d="M9 12.2H15V16.4H9Z"/>'),
    'basket': '<path d="M4.6 10H19.4L17.8 18.4Q17.6 19.6 16.4 19.6H7.6Q6.4 19.6 6.2 18.4Z"/><path d="M8.6 10L11 4.8M15.4 10L13 4.8"/><path d="M9.8 13V16.4M14.2 13V16.4"/>',
    'torn': '<path d="M6 3.6H14.4L18.4 7.6V16.6L16.4 18.6L14.2 16.8L12 18.8L9.8 16.8L7.8 18.6L6 16.8Z"/><path d="M14.4 3.6V7.6H18.4"/>',
    'paste': '<rect x="5.6" y="5" width="12.8" height="15.4" rx="2.4"/><path d="M9.4 5V3.6H14.6V5Q14.6 6.6 13 6.6H11Q9.4 6.6 9.4 5Z"/><path d="M8.8 11H15.2M8.8 14.4H13"/>',
    'heart': f'<path d="{heart_path(12, 12.6, 8.2)}"/>',
    'star': f'<path d="{star_path(12, 12.9, 9.4, 4.3)}" fill="currentColor"/>',
    'star-outline': f'<path d="{star_path(12, 12.9, 9.4, 4.3)}"/>',
    'note': ('<path d="M11.4 17.4V4.2Q12 7.6 15.6 8.6Q17.6 9.4 16.8 12"/>'
             '<ellipse cx="8.4" cy="17.6" rx="3.2" ry="2.5" transform="rotate(-18 8.4 17.6)" fill="currentColor"/>'),
}

# Toolbar and inline glyphs by the spec's names (§3.4; `sk_g_<name>`) -> TB key. The last group are the small glyphs
# other sections name: the jar (My pantry), the basket on the store-aisle chip, the torn paper of a field error, the key
# field's paste button, and the inline ♡ ★ ☆ ♪ that copy uses instead of font glyphs (§2.7).
GLYPH_NAMES = {
    'add': 'plus', 'close': 'close', 'check': 'check', 'back': 'back', 'more': 'more', 'camera': 'cam',
    'gallery': 'gallery', 'mic': 'mic', 'stop': 'stop', 'send': 'send', 'settings': 'gear', 'aisles': 'store',
    'lists': 'lists', 'edit': 'pencil', 'delete': 'trash', 'undo': 'undo', 'refresh': 'resort', 'sparkle': 'sparkle',
    'expand': 'chev-down', 'collapse': 'chev-up', 'link': 'link', 'drag': 'drag', 'key': 'key', 'eye': 'eye',
    'web': 'web', 'search': 'search', 'sort': 'sort', 'scissors': 'scissors', 'album': 'album', 'trip': 'book',
    'cassette': 'cassette', 'envelope': 'envelope', 'polaroid': 'polaroid',
    'jar': 'jar', 'basket': 'basket', 'torn': 'torn', 'paste': 'paste',
    'heart': 'heart', 'star': 'star', 'star_outline': 'star-outline', 'note': 'note',
}
GLYPH_FILLED = ('more', 'drag')            # tb_symbols fills these two with currentColor (others fill per path)


def tb_symbols():
    out = []
    for k, v in TB.items():
        fill = 'currentColor' if k in ('more', 'drag') else 'none'
        out.append(f'<symbol id="sticker-tb-{k}" viewBox="0 0 24 24"><g fill="{fill}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">{v}</g></symbol>')
    return ''.join(out)


def tb(k, size=24, cls='sk-tb'):
    return f'<svg class="{cls}" width="{size}" height="{size}" aria-hidden="true"><use href="#sticker-tb-{k}"/></svg>'


# ---------- hanko ----------
HANKO = {}


def hanko_symbols():
    out = []
    for (t1, t2), sid in HANKO.items():
        fs = min(10.4, 25.5 / (max(len(t1), len(t2)) * .7))
        out.append(f'<symbol id="{sid}" viewBox="0 0 40 40"><g fill="currentColor" stroke="currentColor">'
                   f'<circle cx="20" cy="20" r="16.4" fill="none" stroke-width="2" stroke-dasharray="40 3.2 51.8 3.2"/>'
                   f'<circle cx="20" cy="20" r="13.6" fill="none" stroke-width=".8" stroke-dasharray="20 2 30 1.6 31.4"/>'
                   f'<text x="20" y="18.6" text-anchor="middle" stroke="none" style="font:900 {fmt(fs)}px var(--sk-f-body);letter-spacing:.02em">{t1}</text>'
                   f'<text x="20" y="28.4" text-anchor="middle" stroke="none" style="font:900 {fmt(fs)}px var(--sk-f-body);letter-spacing:.02em">{t2}</text>'
                   f'<circle cx="37.6" cy="9" r=".7" stroke="none"/><circle cx="3.6" cy="28.6" r=".6" stroke="none"/><circle cx="31.6" cy="38" r=".5" stroke="none"/></g></symbol>')
    return ''.join(out)


def hanko_id(text=('GOT', 'IT!')):
    if text not in HANKO:
        HANKO[text] = f'sticker-hk{len(HANKO)}'
    return HANKO[text]


def hanko(rot=-12, size=40, text=('GOT', 'IT!'), cls='sk-hanko', col='var(--sk-hanko,#C8321F)', label='Checked'):
    lab = f' role="img" aria-label="{label}"' if label else ' aria-hidden="true"'
    return (f'<svg class="{cls}" viewBox="0 0 40 40" width="{size}" height="{size}"{lab} style="color:{col}">'
            f'<use href="#{hanko_id(text)}" transform="rotate({rot} 20 20)"/></svg>')


# ---------- LCD 5x7 dot matrix ----------
G57 = {
    '0': '01110 10001 10011 10101 11001 10001 01110', '1': '00100 01100 00100 00100 00100 00100 01110',
    '2': '01110 10001 00001 00010 00100 01000 11111', '3': '11111 00010 00100 00010 00001 10001 01110',
    '4': '00010 00110 01010 10010 11111 00010 00010', '5': '11111 10000 11110 00001 00001 10001 01110',
    '6': '00110 01000 10000 11110 10001 10001 01110', '7': '11111 00001 00010 00100 01000 01000 01000',
    '8': '01110 10001 10001 01110 10001 10001 01110', '9': '01110 10001 10001 01111 00001 00010 01100',
    '/': '00001 00010 00010 00100 01000 01000 10000', ':': '0 1 1 0 1 1 0', ' ': '00 00 00 00 00 00 00',
    'R': '11110 10001 10001 11110 10100 10010 10001', 'E': '11111 10000 10000 11110 10000 10000 11111',
    'C': '01110 10001 10000 10000 10000 10001 01110', 'S': '01111 10000 10000 01110 00001 00001 11110',
    'A': '01110 10001 10001 11111 10001 10001 10001', 'N': '10001 11001 10101 10011 10001 10001 10001',
    'O': '01110 10001 10001 10001 10001 10001 01110', 'K': '10001 10010 10100 11000 10100 10010 10001',
    'P': '11110 10001 10001 11110 10000 10000 10000', 'F': '11111 10000 10000 11110 10000 10000 10000',
    'T': '11111 00100 00100 00100 00100 00100 00100', 'I': '111 010 010 010 010 010 111',
    'L': '10000 10000 10000 10000 10000 10000 11111', 'M': '10001 11011 10101 10101 10001 10001 10001',
    'G': '01110 10001 10000 10111 10001 10001 01111', 'D': '11100 10010 10001 10001 10001 10010 11100',
    'H': '10001 10001 10001 11111 10001 10001 10001', 'U': '10001 10001 10001 10001 10001 10001 01110',
    'Y': '10001 10001 01010 00100 00100 00100 00100', 'W': '10001 10001 10001 10101 10101 10101 01010',
    '!': '1 1 1 1 1 0 1', '.': '0 0 0 0 0 0 1', '-': '000 000 000 111 000 000 000', 'X': '10001 10001 01010 00100 01010 10001 10001',
    'B': '11110 10001 10001 11110 10001 10001 11110', 'V': '10001 10001 10001 10001 10001 01010 00100',
    'J': '00111 00010 00010 00010 00010 10010 01100', 'Q': '01110 10001 10001 10001 10101 10010 01101',
    'Z': '11111 00001 00010 00100 01000 10000 11111', '?': '01110 10001 00001 00010 00100 00000 00100',
    '+': '00000 00100 00100 11111 00100 00100 00000', '♪': '00110 00101 00100 00100 01100 11100 11000',
    # r10 (WP2) · the rest of the §13.1 set: store names and the receipt print any of these
    '#': '01010 01010 11111 01010 11111 01010 01010', '%': '11000 11001 00010 00100 01000 10011 00011',
    '&': '01100 10010 10100 01000 10101 10010 01101', "'": '1 1 0 0 0 0 0',
    '(': '001 010 100 100 100 010 001', ')': '100 010 001 001 001 010 100',
    ',': '00 00 00 00 00 01 10', '·': '0 0 0 1 0 0 0',
    # dot fallbacks of the vector extras (LCD_EXTRAS): a renderer without vectors still prints something
    '★': '00100 00100 11111 01110 01010 10001 00000', '●': '00000 01110 11111 11111 11111 01110 00000',
    '♡': '00000 01010 10101 10001 01010 00100 00000',
}

# The vector extras (§13.1): drawn as shapes, not dots, in the units of the dot pitch p (x from the glyph's left edge,
# y from the top of the 7-row cell), each with the advance it takes. ★ is the LCD's star (it becomes the full moon ●
# in Dusk and Glow, never a crescent); ♪ and ♡ close the receipt and the toasts. `stroke` is the outline width in p
# (round joins), drawn in the same colour as the fill.
LCD_EXTRAS = {
    '★': dict(advance=7.4, stroke=.5, d=star_path(3, 3.4, 3.6, 1.6)),
    '●': dict(advance=7.4, stroke=0, d='M3 .2A3.2 3.2 0 1 1 3 6.6A3.2 3.2 0 1 1 3 .2Z'),
    '♡': dict(advance=7.0, stroke=0, d=heart_path(3, 3.2, 3.1) + 'M3 5.2C4.6 4 5.4 3.2 5.4 2.2C5.4 1.4 4.8 .9 4.2 .9C3.7 .9 3.3 1.2 3 1.7'
              'C2.7 1.2 2.3 .9 1.8 .9C1.2 .9 .6 1.4 .6 2.2C.6 3.2 1.4 4 3 5.2Z'),
    '♪': dict(advance=6.0, stroke=0, d='M2.2 .2H4.8V1.4L3.2 1.9V5.3A1.6 1.4 0 1 1 2.2 4.1Z'),
}


LCDP = set()


def pid(p):
    return 'sticker-lcdp' + fmt(p).replace('.', '_')


def lcd_patterns():
    return ''.join(f'<pattern id="{pid(p)}" width="{fmt(p)}" height="{fmt(p)}" patternUnits="userSpaceOnUse"><rect width="{fmt(p * .8)}" height="{fmt(p * .8)}" fill="#B6D4A4"/></pattern>' for p in sorted(LCDP))


def lcd_on(text, p):
    on, x = [], 0.0
    for ch in text:
        rows = G57[ch].split(' ')
        w = len(rows[0])
        for r, row in enumerate(rows):
            for c, v in enumerate(row):
                if v == '1':
                    on.append(f'M{fmt(x + c * p)} {fmt(r * p)}h{fmt(p * .8)}v{fmt(p * .8)}h-{fmt(p * .8)}z')
        x += (w + 1) * p
    return ''.join(on), x


def lcd_svg(text, p=2.2, star=False, label=None, ghost=True, cls='sk-lcdsvg'):
    LCDP.add(float(p))
    d, x = lcd_on(text, p)
    H = p * 7 - p * .2
    off = f'<rect class="sk-loff" width="{fmt(x - p * 1.2)}" height="{fmt(H)}" fill="url(#{pid(float(p))})" opacity=".5"/>' if ghost else ''
    starsvg = ''
    if star:
        starsvg = f'<path class="sk-lstar" d="{star_path(x + p * 3, p * 3.4, p * 3.6, p * 1.6)}" fill="currentColor" stroke="currentColor" stroke-width="{fmt(p * .5)}" stroke-linejoin="round"/>'
        x += p * 7.4
    W = x - p * .2
    lab = f' role="img" aria-label="{label}"' if label else ' aria-hidden="true"'
    return (f'<svg class="{cls}" viewBox="-.4 -.4 {fmt(W + .8)} {fmt(H + .8)}" width="{fmt(W + .8)}" height="{fmt(H + .8)}"{lab}>'
            f'{off}<path class="sk-lon" d="{d}" fill="currentColor"/>{starsvg}</svg>')
