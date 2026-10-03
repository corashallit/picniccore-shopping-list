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

# Washi tape tiles (§2.12, egg 11, egg 16): full-colour tiles, repeated along an 18 dp strip whose torn ends and
# rotation the theme draws (Washi.kt). Each entry is (svg, width, height) in dp. The four everyday tapes follow the
# mockup's CSS gradients exactly (sakura 45° stripes 6 dp sakura mid 78 % / 6 dp white: one period across a 17 dp
# tile, 12.02 dp at 45°; pink gingham; lemon dots 2.4 dp every 10 dp on cream; mint stripes); the seasonal and the
# raindrop tapes are the mockup's 24 x 20 motif tiles (common.TAPES).
_S, _W = '#FF9FC8', '#FFFFFF'
WASHI = {
    'sakura': (sv(f"<rect width='17' height='17' fill='{_W}' fill-opacity='.78'/>"
                  f"<path d='M0 0H8.5L17 8.5V17Z' fill='{_S}' fill-opacity='.78'/><path d='M0 8.5L8.5 17H0Z' fill='{_S}' fill-opacity='.78'/>", 17, 17), 17, 17),
    'gingham': (sv(f"<rect width='10' height='10' fill='{_W}' fill-opacity='.75'/><rect width='5' height='10' fill='{_S}' fill-opacity='.5'/>"
                   f"<rect y='5' width='10' height='5' fill='{_S}' fill-opacity='.5'/>", 10, 10), 10, 10),
    'dots': (sv("<rect width='10' height='10' fill='#FFFAD6' fill-opacity='.82'/><circle cx='5' cy='5' r='2.4' fill='#FFDA4A' fill-opacity='.95'/>", 10, 10), 10, 10),
    'mint': (sv(f"<rect width='10' height='10' fill='{_W}' fill-opacity='.8'/><rect width='4' height='10' fill='#6DD3B0' fill-opacity='.6'/>", 10, 10), 10, 10),
    'pumpkin': (sv("<rect width='24' height='20' fill='#FFDCC6'/><path d='M0 0H24' stroke='#FFA477' stroke-width='3'/>"
                   "<ellipse cx='8' cy='11.6' rx='4.6' ry='3.8' fill='#FFA477' stroke='#AE4D1C' stroke-width='1'/>"
                   "<path d='M8 8V6.4' stroke='#2E8239' stroke-width='1.4' stroke-linecap='round'/>"
                   "<path d='M18.6 4.6l1.2 2.6l2.6-.6l-1 2.4l1.6 1.4l-2.6.4l-.4 2.4l-1.4-2.2l-1.4 2.2l-.4-2.4l-2.6-.4l1.6-1.4l-1-2.4l2.6.6z' fill='#FF6F8E' fill-opacity='.8'/>", 24, 20), 24, 20),
    'maple': (sv("<rect width='24' height='20' fill='#FFFFFF'/><rect width='5' height='20' fill='#FF6F8E' fill-opacity='.85'/>"
                 "<rect x='19' width='5' height='20' fill='#FF6F8E' fill-opacity='.85'/>"
                 "<path d='M12 4.4l1.2 2.6l2.4-1l-.6 3.4l2.2-1.2l.4 1.6l1.6.2l-1 2.2l.8.8l-3.6 2.4l.4 1.2l-3.2-.6v3h-1.2v-3l-3.2.6l.4-1.2l-3.6-2.4l.8-.8l-1-2.2l1.6-.2l.4-1.6l2.2 1.2l-.6-3.4l2.4 1z' fill='#FF6F8E'/>", 24, 20), 24, 20),
    'snow': (sv("<rect width='24' height='20' fill='#CCEBFF'/><g stroke='#FFFFFF' stroke-width='1.6' stroke-linecap='round'>"
                "<path d='M7 4.4V13.6M3 6.7L11 11.3M3 11.3L11 6.7'/><path d='M18 11V17M15.4 12.5L20.6 15.5M15.4 15.5L20.6 12.5'/></g>"
                "<circle cx='19' cy='4' r='1.2' fill='#FFFFFF'/><circle cx='3' cy='17' r='1' fill='#FFFFFF'/>", 24, 20), 24, 20),
    'hearts': (sv("<rect width='24' height='20' fill='#FFD6E8'/>"
                  "<path d='M7 15.2C4 13 2.4 11.4 2.4 9.4C2.4 7.8 3.6 6.8 4.9 6.8C5.8 6.8 6.5 7.3 7 8C7.5 7.3 8.2 6.8 9.1 6.8C10.4 6.8 11.6 7.8 11.6 9.4C11.6 11.4 10 13 7 15.2Z' fill='#FF6F8E'/>"
                  "<path d='M18 9.2C16.4 8 15.6 7.2 15.6 6.2C15.6 5.4 16.2 4.8 16.9 4.8C17.4 4.8 17.8 5.1 18 5.4C18.2 5.1 18.6 4.8 19.1 4.8C19.8 4.8 20.4 5.4 20.4 6.2C20.4 7.2 19.6 8 18 9.2Z' fill='#FFFFFF'/>", 24, 20), 24, 20),
    'redenvelope': (sv("<rect width='24' height='20' fill='#FF6F8E'/><rect x='4' y='4' width='8' height='12' rx='1.6' fill='#C3264F'/>"
                       "<circle cx='8' cy='9' r='1.8' fill='#FFDA4A'/><path d='M14 14q3-4 6 0' stroke='#FFDA4A' stroke-width='1.4' fill='none'/>", 24, 20), 24, 20),
    'ghost': (sv("<rect width='24' height='20' fill='#E4D6FF'/>"
                 "<path d='M3 13C3 8.4 5.6 6 8 6S13 8.4 13 13L11.4 11.8L9.8 13L8 11.8L6.2 13L4.6 11.8Z' fill='#FFFFFF' stroke='#6542B3' stroke-width='1'/>"
                 "<circle cx='6.6' cy='9.4' r='.8' fill='#6542B3'/><circle cx='9.4' cy='9.4' r='.8' fill='#6542B3'/>"
                 "<ellipse cx='18.4' cy='12' rx='3.4' ry='2.8' fill='#FFA477'/>", 24, 20), 24, 20),
    'raindrop': (sv("<rect width='24' height='20' fill='#EAF6FF'/><path d='M0 0H24' stroke='#7CC6F5' stroke-width='3'/>"
                    "<path d='M7 6C7 6 4.6 9.4 4.6 11A2.4 2.4 0 0 0 9.4 11C9.4 9.4 7 6 7 6Z' fill='#7CC6F5' stroke='#1E6AA3' stroke-width='.8'/>"
                    "<path d='M17 10C17 10 15 12.8 15 14.2A2 2 0 0 0 19 14.2C19 12.8 17 10 17 10Z' fill='#7CC6F5' stroke='#1E6AA3' stroke-width='.8'/>"
                    "<circle cx='12' cy='16' r='1' fill='#7CC6F5'/>", 24, 20), 24, 20),
}
# which tape each season wears (calendar `Season`, §12.2 egg 11); the everyday default is `sakura`
SEASON_WASHI = {'CANADA_DAY': 'maple', 'THANKSGIVING': 'pumpkin', 'HALLOWEEN': 'ghost', 'FIRST_SNOW': 'snow',
                'VALENTINES': 'hearts', 'LUNAR_NEW_YEAR': 'redenvelope'}
