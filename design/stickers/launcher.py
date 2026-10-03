"""Launcher art (§20.3 item 49): the picnic hamper with three strawberries and a holo star on the pinstripe binder.

Shared by the mockup (page_d.launcher_symbols) and the Android emitter (launcher layers)."""
import copy
from engine import Icon, F, TUBE, G, DOT, berry, fmt
from icons import calyx
from deco import DBY

# 108 dp adaptive-icon canvas; safe zone = the 66 dp circle centred at 54,54 (r 33)
CANVAS = 108
BINDER, STRIPE = '#E6F6EE', '#CDEEDD'
HAMPER_BOX = (26, 30, 54)      # x, y, size of the hamper sticker (52-unit box) on the canvas
STAR_BOX = (61, 27, 18)        # x, y, size of the holo sparkle: both, die-cut and shadow included, inside the safe zone


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


def mono_icon(icon):
    """what the themed (monochrome) launcher layer draws for each foreground sticker: the hamper simplified to its
    berries, basket and rim (no handle, seeds, leaves or weave, which turn to noise at 48 dp in one tint);
    the star as it is"""
    if icon.key != 'launch':
        return icon
    from shapely.geometry import LineString, MultiLineString
    import emit_android as E
    # the three berries as one mound, with only the edges you see (the side berries lie over the middle one), so the
    # outlines never cross
    geo = [E.fill_geom(E.shape_path(berry(cx, top, w, h), E.Matrix())) for cx, top, w, h in [(24, 9.6, 11.6, 14.4), (15, 13.4, 10.6, 12.6), (33, 13.4, 10.6, 12.6)]]
    mound = geo[0].union(geo[1]).union(geo[2]).simplify(.03)
    seen = []
    for side in geo[1:]:
        cut = side.exterior.intersection(geo[0].buffer(-.4))
        lines = list(cut.geoms) if isinstance(cut, MultiLineString) else [cut] if isinstance(cut, LineString) else []
        seen += ['M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in ln.simplify(.03).coords) for ln in lines if ln.length > 1]
    outline = 'M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in list(mound.exterior.coords)[:-1]) + 'Z'
    return Icon('launchmono', 'Picnic hamper (mono)', [         # the handle hides behind the berries, so it is left out
        F(('path', outline), 'strawberry', 'm', det=seen),
        F(('path', 'M7.6 26H40.4L37.6 39.6Q37 42 34.6 42H13.4Q11 42 10.4 39.6Z'), 'melon', 'p'),
        F(('rect', 5.6, 23.4, 36.8, 5.2, 2.6), 'melon', 'm'),
    ])


def star4holo():
    st = copy.deepcopy(DBY['star4']); st.key = 'star4holo'
    st.parts[0].t = 'url(#sticker-holo)'; st.parts[0].band = None; st.parts[0].fam = 'grape'
    return st


# The hamper is also a decor sticker (`sk_d_hamper`): the splash screen's sticker (§20.3 item 50) and the celebration's
# hamper. Registered here because the hamper needs `icons.calyx` and `deco` must not import `icons`.
def _register_hamper():
    from deco import DECO, DBY
    if 'hamper' not in DBY:
        h = hamper()
        h.key, h.label = 'hamper', 'Picnic hamper'
        DECO.append(h)
        DBY['hamper'] = h


_register_hamper()


def safe_zone_errors():
    """the hamper and the star, white border and hard shadow included, must sit inside the 66 dp safe zone (a circle
    of radius 33 at 54,54), or a launcher mask cuts them (§20.3 item 49)."""
    from shapely import affinity
    from shapely.geometry import Point
    import emit_android as E
    safe = Point(CANVAS / 2, CANVAS / 2).buffer(33).buffer(.05)
    errors = []
    for icon, (x, y, size) in ((hamper(), HAMPER_BOX), (star4holo(), STAR_BOX)):
        k = size / 52
        _, _, sil = E.icon_layers(icon, E.Matrix(k, 0, 0, k, x + 2 * k, y + 2 * k))
        cut = sil.buffer(E.CUT_W / 2 * k)
        whole = cut.union(affinity.translate(cut, 1.6 * k, 2.2 * k))
        if not safe.contains(whole):
            errors.append(f'launcher: the {icon.label.lower()} leaves the 66 dp safe zone ({whole.difference(safe).area:.2f} dp²)')
    return errors
