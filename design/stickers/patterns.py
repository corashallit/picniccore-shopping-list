"""Tab patterns (§2.12): white-on-transparent mask tiles, printed in the aisle mid colour.

Each entry is (svg, tile size in px/dp). The mockup inlines them as CSS mask images; the Android emitter turns them
into vector tiles (`sk_t_<name>.xml`) listed in `PatternTiles.kt`.
"""
from engine import heart_path, star_path


def sv(inner, w, h):
    return f"<svg xmlns='http://www.w3.org/2000/svg' width='{w}' height='{h}' viewBox='0 0 {w} {h}'>{inner}</svg>"


# pattern masks (white = printed). size in px
PATTERNS = {
    'gingham': (sv("<rect width='5' height='10' fill='#fff' fill-opacity='.55'/><rect width='10' height='5' fill='#fff' fill-opacity='.55'/>", 10, 10), 10),
    'polka': (sv("<circle cx='3' cy='3' r='1.9' fill='#fff'/><circle cx='8' cy='8' r='1.9' fill='#fff'/>", 10, 10), 10),
    'stripes': (sv("<path d='M-2 2L2 -2M0 10L10 0M8 12L12 8' stroke='#fff' stroke-width='2.6'/>", 10, 10), 10),
    'hearts': (sv(f"<path d='{heart_path(5, 5.2, 3.2)}' fill='#fff'/>", 11, 11), 11),
    'stars': (sv(f"<path d='{star_path(6, 6.4, 4.4, 2)}' fill='#fff'/>", 12, 12), 12),
    'clouds': (sv("<path d='M3 9C1 9 .8 6.6 2.6 6.2C2.6 4 5.6 3 6.8 5C7.6 3 11 3.6 10.6 6.2C12.4 6.6 12 9 10.4 9Z' fill='#fff'/>", 13, 12), 13),
    'check': (sv("<rect width='3' height='3' fill='#fff'/><rect x='3' y='3' width='3' height='3' fill='#fff'/>", 6, 6), 6),
    'waves': (sv("<path d='M0 5Q3 2 6 5T12 5' fill='none' stroke='#fff' stroke-width='1.8'/>", 12, 8), 12),
    'flowers': (sv("<g fill='#fff'><circle cx='6' cy='3.6' r='2'/><circle cx='8.4' cy='6' r='2'/><circle cx='6' cy='8.4' r='2'/><circle cx='3.6' cy='6' r='2'/></g>", 12, 12), 12),
    'plain': (sv("<rect width='4' height='4' fill='#fff' fill-opacity='0'/>", 4, 4), 4),
}
