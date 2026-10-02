"""Launcher art (§20.3 item 49): the picnic hamper with three strawberries and a holo star on the pinstripe binder.

Shared by the mockup (page_d.launcher_symbols) and the Android emitter (launcher layers)."""
import copy
from engine import Icon, F, TUBE, G, DOT, berry
from icons import calyx
from deco import DBY

# 108 dp adaptive-icon canvas; safe zone = the 66 dp circle centred at 54,54 (r 33)
CANVAS = 108
BINDER, STRIPE = '#E6F6EE', '#CDEEDD'
HAMPER_BOX = (25, 27, 56)      # x, y, size of the hamper sticker (52-unit box) on the canvas
STAR_BOX = (64, 27, 19)        # x, y, size of the holo sparkle


def hamper():
    berries = []
    for cx, top, w, h in [(24, 9.6, 11.6, 14.4), (15, 13.4, 10.6, 12.6), (33, 13.4, 10.6, 12.6)]:
        berries += [F(berry(cx, top, w, h), 'strawberry', 'm'), *[F(('circle', cx + dx, top + dy, .8), 'lemon', 'lp', ol=0, cut=False) for dx, dy in [(-2.2, 5), (2.2, 5), (0, 8.2)]],
                    F(('path', calyx(cx, top, .9)), 'melon', 'm', ol=1.4)]
    return Icon('launch', 'Picnic hamper', [
        TUBE('M11.4 26C11.4 7.6 36.6 7.6 36.6 26', 'melon', 4.6, 'p'),
        *berries,
        F(('path', 'M7.6 26H40.4L37.6 39.6Q37 42 34.6 42H13.4Q11 42 10.4 39.6Z'), 'melon', 'p', band=.3,
          tex=['M7 31.4H41', 'M7 36.4H41', 'M13.6 26V42', 'M19 26V42', 'M24.4 26V42', 'M29.8 26V42', 'M35 26V42'], tex_w=1.8),
        F(('rect', 5.6, 23.4, 36.8, 5.2, 2.6), 'melon', 'm'),
    ], gloss=[G('M18.6 15.6Q19 13.2 21 12.4', 2), G('M7.4 26H14', 2.2), DOT(17, 26)])


def star4holo():
    st = copy.deepcopy(DBY['star4']); st.key = 'star4holo'
    st.parts[0].t = 'url(#sticker-holo)'; st.parts[0].band = None; st.parts[0].fam = 'grape'
    return st
