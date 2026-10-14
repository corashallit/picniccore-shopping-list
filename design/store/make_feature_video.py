#!/usr/bin/env python3
"""The feature video for Kirakira Basket: the app's own recorded screens, framed like the Play screenshots.

    PICNIC_VIDEO_FRAMES=/tmp/kk-frames ./gradlew :app:testDebugUnitTest \\
        --tests 'earth.cora.picnic.store.FeatureVideoRecorder' --rerun     # records the segments
    python3 design/store/make_feature_video.py --frames /tmp/kk-frames    # writes design/store/video/
    python3 design/store/make_feature_video.py --frames /tmp/kk-frames --preview --out /tmp/kk.mp4 \\
        --sheet /tmp/kk_sheet.png --every 15 --stills /tmp/kk_stills      # a quick half-size pass, for review

The recorder writes one folder per segment (write, recipe, shop, glow, celebrate, album): 00000.png, 00001.png, ...
(1080 x 2280, the whole phone screen at 30 fps of app time) and segment.json, which names the frames where the
beats happen ("sent", "rows_landed", ...), where a finger tapped or dragged, and the screen regions worth a closer
look ("add_field"). SCENES below cuts each segment by those marks and gives it a caption, a backdrop, a washi tape
and a few stickers, exactly as make_store_images.py lays out a screenshot; the title card at each end is TITLE. A
later edit to the cut, the wording or the timing is an edit to those two tables.

Each output frame is the scene's backdrop, the phone with the screen resampled into it, the decoration stickers and
the caption, and at a few beats a loupe (a sticker card showing the add field enlarged while someone types) or a
highlighter mark laid on the screen itself (the recipe card's pantry rows). Everything but the screen and the loupe
is the same from one frame to the next while nothing moves, so it is drawn once and kept; a frame at rest costs one
screen resample. Frames stream to ffmpeg (libx264) as raw RGB.

The output is deterministic: no randomness and no timestamps, so the same frames (and the same Pillow, cairosvg,
numpy and ffmpeg versions) always give the same video. Requirements: design/store/requirements.txt and ffmpeg.
"""
import argparse
import hashlib
import io
import json
import math
import multiprocessing
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from collections import OrderedDict, namedtuple
from pathlib import Path

try:
    import numpy as np
except ImportError:  # pragma: no cover
    sys.exit('make_feature_video.py needs numpy: pip install -r design/store/requirements.txt')
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_store_images as store  # noqa: E402  (the screenshots' backdrop, phone, lettering and stickers)

REPO = store.REPO
OUT_DIR = REPO / 'design/store/video'
W, H = store.W, store.H                     # 1080 x 1920, as the phone screenshots
SEG_W, SEG_H = 1080, 2280                   # a recorded frame: the whole phone screen
FPS = 30
CRF = 22                                    # x264 quality: no visible loss or banding, under MAX_BYTES
MAX_BYTES = 12_000_000
LENGTH = (30.0, 75.0)                       # the whole video, in seconds
MIN_CAPTION = 3.1                           # every caption stays up at least this long, in seconds
SPEED = (0.5, 1.6)                          # a speed span's factor (see SCENES)

# The phone sits exactly where a screenshot puts it (make_store_images.screenshot)
PHONE_X = round((W - (store.SCREEN_W + 2 * store.BEZEL)) / 2 - (store.DIECUT + 6))
PHONE_Y = store.PHONE_TOP - (store.DIECUT + 6)
SCREEN_X = PHONE_X + store.DIECUT + 6 + store.BEZEL
SCREEN_Y = PHONE_Y + store.DIECUT + 6 + store.BEZEL
SCREEN_BOX = (SCREEN_X, SCREEN_Y, SCREEN_X + store.SCREEN_W, SCREEN_Y + store.SCREEN_H)
SCREEN_K = store.SCREEN_W / SEG_W           # a recorded pixel on the frame
# the phone's art (die-cut, side buttons and shadow) at rest on the frame
PHONE_BOX = (PHONE_X + 6, PHONE_Y + 6, PHONE_X + 2 * (store.DIECUT + 6) + store.SCREEN_W + 2 * store.BEZEL
             + store.PHONE_SHADOW[0], PHONE_Y + 2 * (store.DIECUT + 6) + store.SCREEN_H + 2 * store.BEZEL
             + store.PHONE_SHADOW[1])

# ---------------------------------------------------------------------------------------------------------------------
# Motion (seconds). Calm and cute: things pop in with a small overshoot, settle, and leave quickly. The app's own
# motion always plays at 1x; the reading time around it is generous (people read slower than one guesses).
# ---------------------------------------------------------------------------------------------------------------------
XFADE = 0.3            # a crossfade, between two nearly alike screens
WIPE = 0.35            # a wipe, between two busy ones: the next screen comes in from the top down behind a soft edge
WIPE_FEATHER = 80      # px of the frame: the wipe's soft edge
TRANSITIONS = {'fade': XFADE, 'wipe': WIPE}
GROUND_FADE = 0.4      # the backdrop crossfades between scenes
CAPTION_IN = 0.4       # a caption pops in: scale 0.7 -> 1.06 -> 1
CAPTION_OUT = 0.16     # and shrinks and fades
STICKER_IN = 0.44      # a sticker pops in: scale 0.45 -> 1.1 -> 1, its turn settling from a few degrees off
STICKER_OUT = 0.22
STICKER_TURN = 7.0     # degrees off at the start of a sticker's pop, alternating direction
BOOK_IN, BOOK_OUT = 0.5, 0.25
DECOR_DELAY = 0.16     # after a scene change, before its first sticker pops
DECOR_STAGGER = 0.12   # between one sticker's pop and the next
DECOR_LEAD = 0.3       # before a scene change, when its stickers start leaving
DECOR_OUT_STAGGER = 0.06
RISE = 0.6             # the phone rises in from below (ease-out)...
DROP = 0.5             # ...and drops away at the end (ease-in)
PHONE_TRAVEL = 1650    # px below its place: wholly off the frame
CAPTION_ON_RISE = 0.25  # the first caption pops this long after the phone starts rising
# A tap the recorder logged ("taps" in segment.json) shows as a soft disc with a ring under the finger: it presses in
# for TAP_PRESS before the tap and spreads and fades for TAP_LIFT after it. A drag presses in the same way, follows
# the finger along its path (the recorder's smoothstep) and lifts where the finger left the screen.
TAP_RADIUS, TAP_RING = 62, 7     # px of the recorded screen: wider than a check spot (about 45 px), so the ring shows
                                 # round the spot instead of lying on its dashes (still inside a row and a pill)
TAP_PRESS, TAP_LIFT = 0.12, 0.36
TAP_STYLE = {                    # by the recording's theme: disc colour and opacity, ring colour and opacity
    'day': ('#FFF3A6', 0.45, store.BALLPOINT, 0.7),  # the highlighter (a pressed row's band, §2.1) and the ballpoint
    'glow': (store.WHITE, 0.22, '#C9FFDE', 0.9),     # glowStar: Glow's spots are dashed in inkSoft, so not that
}
# While a drag moves, the content it carries (a header, a row) slides along under the finger, so the disc steps back:
# smaller and fainter, still plainly a finger. It eases to that over DRAG_EASE after the press and back to the full
# disc over DRAG_EASE before the finger leaves (the recorder's smoothstep is all but still by then), then lifts as a
# tap does.
DRAG_LOOK = (0.7, 0.4, 0.6)      # the disc's scale, and its opacity and the ring's, as a share of a press's
DRAG_EASE = 0.2
# The loupe: a white die-cut sticker card with the house's hard periwinkle shadow, floating over the phone just above
# the region it shows (the add field), with that region live and enlarged in it. It pops in and out as a sticker
# does, but opaque on every frame: it grows from small with a little overshoot and settles from a few degrees off,
# and leaves by shrinking away, so a part-faded card never lies over the text under it. It never comes out wider than
# the phone.
LOUPE_SCALE = 1.7      # x the phone screen's own scale (about 1.1 recorded px a frame px, so the text stays crisp)
LOUPE_BORDER = 16      # px of white die-cut round the region
LOUPE_RADIUS = 30      # the card's corners (the region's are LOUPE_RADIUS - LOUPE_BORDER)
LOUPE_SHADOW = (8, 11)  # the hard shadow's offset
LOUPE_GAP = 70         # px between the card (with its shadow) and the region it shows, above it: clear of the dock's
                       # top edge, which stands about 50 px above the add field
LOUPE_TURN = 3.0       # degrees off at the start of its pop (smaller than a sticker's: it is a big card)
LOUPE_POP = (0.3, 1.06)  # its scale on the way in: from, and the overshoot's peak
LOUPE_STYLE = 'loupe'
# The highlighter: a mark of the app's own highlighter (Day's `highlighter`, the band of a pressed row and of the
# 400 ms flash on a new row's words, §4.11, §6.2) drawn on the screen itself, so it rides with the phone and scales
# with it. It multiplies, as ink on paper does, so the words under it stay dark and crisp. It swipes in from left to
# right as a chisel-tipped marker would (one mark after another), and fades away. Its edges are drawn by hand: a
# slanted start and end, a slight tilt like the app's band (-0.6°), a little wobble along the top and the bottom, and
# a faint streak in the ink, all from fixed curves (nothing random).
HIGHLIGHT_INK = {'day': '#FFF3A6'}   # by theme; Glow's band (#3A438A) is a light on navy, not an ink, so it has none
HIGHLIGHT_IN = 0.3     # each mark's swipe (ease-out)
HIGHLIGHT_STAGGER = 0.2  # from one mark's swipe to the next's
HIGHLIGHT_OUT = 0.2    # the fade
HIGHLIGHT_INSET = 5    # recorded px: the mark keeps this far inside its rect, wobble and tilt aside (they go inward)
HIGHLIGHT_SLANT = 12   # recorded px: how far the chisel's slanted start and end lean over the mark's height
HIGHLIGHT_WOBBLE = 3.5  # recorded px: the most the top and the bottom edges waver
HIGHLIGHT_TILT = -0.6  # degrees, as the app's band
# For recordings that name no regions (the first recorder): the add field, in recorded px
FALLBACK_REGIONS = {'add_field': (22, 2104, 540, 2252)}
POSTER_AT = 'celebrate:banner+0.6s'   # the poster: the composed frame at this beat (scene:beat)

# ---------------------------------------------------------------------------------------------------------------------
# The title card, at the start and again at the end. The wordmark and tagline, the trip's real sticker book taped
# onto the blanket (as on the feature graphic) and a few stickers. Items: (what, size px, centre x, centre y, turn in
# degrees, at). `what` is 'wordmark', 'tagline', 'book', a drawable or 'sparkle:<family>'. The wordmark and the
# tagline are centred by their white face (the stroke, the patch), so their hard shadows don't pull them off the
# middle; the rest by their art. Each item pops in `at` seconds into the card, except the TITLE_AT_REST items, which
# are on the very first frame (so a player showing frame 0 shows the name). The card holds until TITLE_CLEAR, then
# clears from the bottom up, one item a frame; the phone rises once nothing is left in its way. At the end the card
# comes back once the phone has dropped away and holds for TITLE_END_HOLD.
# ---------------------------------------------------------------------------------------------------------------------
TITLE = dict(fam='mint', tape='sakura', items=[
    ('wordmark', 104, 540, 640, 0, 0.0),
    ('tagline', 42, 540, 810, 0, 0.25),
    ('book', 880, 540, 1270, 2, 0.45),
    ('sparkle:lemon', 64, 994, 556, 0, 0.70),       # the twinkle pair, at the wordmark's top right, clear of the 't'
    ('sk_d_star', 44, 1032, 622, 8, 0.82),
    ('sk_i_strawberry', 170, 250, 1610, 10, 0.94),
    ('sk_i_lemonade', 140, 840, 1600, -12, 1.06),
    ('sk_d_bee', 120, 196, 466, -10, 1.18),
])
TITLE_AT_REST = ('wordmark', 'tagline')
TITLE_CLEAR = 3.2      # seconds: the card starts clearing
TITLE_RETURN = 0.3     # after the phone starts dropping, before the card comes back
TITLE_END_HOLD = 1.9
TITLE_CLEARANCE = 6    # px: no title sticker comes nearer the wordmark's or the tagline's art

# ---------------------------------------------------------------------------------------------------------------------
# The scenes, in order. A scene whose segment was not recorded is left out.
#   segment            the folder under --frames
#   cut_in, cut_out    the first and last frame shown: 'start', 'end', a mark from segment.json, either with an offset
#                      in seconds ('sent+1.5s', 'end-0.5s'), or a frame number
#   enter              how the scene comes in over the one before: 'fade' (the default) or 'wipe'
#   skip               optional spans to leave out, [(from, to)] or [(from, to, 'wipe')]: 'from' is the last frame
#                      before the jump, 'to' the first after it; the screen rests on 'from' while 'to' comes in, by a
#                      crossfade (the default, for nearly alike screens) or a wipe (two busy screens would make a
#                      muddy double exposure)
#   speed              optional spans to play at another speed, [(from, to, factor)]: faster only where the app waits
#                      or nothing moves, and never above 1.6x; never over the typing (typing_start to typing_end, or
#                      to sent), so the app's own motion plays at 1x; a factor under 1 stretches a wholly still lead-in
#   hold               optional seconds to rest on the last frame
#   loupe              optional [(from, to, region)] or [(from, to, region, options)]: from 'from' to 'to' a loupe
#                      shows `region` (a name from segment.json's "regions", such as 'add_field', or a rect (x0, y0, x1,
#                      y1) of the recorded screen) live and enlarged, on a card LOUPE_GAP above it. Options: 'scale'
#                      (LOUPE_SCALE unless given), 'y' (the card's top, frame px with the phone at rest), 'width'
#                      (the card's face, frame px: more white either side, to cover what would peek out round it) and
#                      'margin' (recorded px of the region's own paper above and below it, its first and last rows
#                      drawn out: room round text that fills its region, where the screen above holds something else).
#                      Only over a small region with room round it: over a dense list a card slices the lines it
#                      covers
#   highlight          optional [(from, to, regions)]: from 'from' to 'to' a highlighter mark lies on each rect of
#                      `regions` (a region name, which may name several rects, a rect, or a list of names and rects).
#                      Day recordings only, and only while the rects' content stands still
#   captions           [(two lines, beat)]: the first is up from the scene's start ('start'); each next one takes
#                      over when its beat plays
#   fam, dusk, tape    backdrop family (dusk: the deeper night-theme ground), washi pattern, as in SHOTS
#   decor              (drawable or 'sparkle:<family>', size px, side 'L' / 'R', centre y, angle), as in SHOTS
# Pacing: a caption stays up at least MIN_CAPTION; rests before and after a beat are about 1.25x what reads at a
# glance, and the recorder films them that long. The app's motion plays at 1x, its ambient decor included: dead air is
# left out with a skip, and only the wait for an answer (the capsule turning in the send button) goes faster.
# Celebrate starts after the list's scroll to Household, where the sticker book folds to its strip in 2 frames.
# ---------------------------------------------------------------------------------------------------------------------
SCENES = [
    # write's two lines fill the field from top to bottom ("picnic day" stands just above it), so its loupe draws out
    # a little of the field's own paper above and below them
    dict(segment='write', cut_in='typing_start-1.8s', cut_out='rows_landed+1.6s',
         speed=[('sent+0.4s', 'card_shown-0.2s', 1.6)],
         loupe=[('typing_start', 'sent+0.4s', 'add_field', {'margin': 14})],
         captions=[(['Write your list', 'any old way'], 'start'),
                   (['Your list, sorted', 'aisle by aisle'], 'card_shown+0.2s')],
         fam='mint', tape='sakura',
         decor=[('sk_i_strawberry', 176, 'L', 1520, 8), ('sk_d_bee', 132, 'R', 1250, -10),
                ('sk_d_ladybug', 112, 'R', 1760, 12)]),
    # the loupe is up until just before the sheet rises, so "pancakes for 12" reads whole for 1.5 s. It sits in the
    # band between the list and the dock: 20 px under the Dairy & eggs strip (its shadow ends at frame y 1442) and
    # clear of the dock's washi, perforation and "picnic day" (from y 1654), so a little smaller than write's, and
    # wide enough to cover the "end of the list" pill (frame x 262 to 823) whole
    dict(segment='recipe', enter='wipe', cut_in='typing_start-1.3s', cut_out='rows_landed+1.6s',
         speed=[('sent+0.6s', 'card_shown-0.2s', 1.6)],
         loupe=[('typing_start', 'card_shown-0.25s', 'add_field', {'scale': 1.35, 'y': 1462, 'width': 584})],
         highlight=[('card_scroll+2.4s', 'stuck-0.8s', 'pantry_rows')],
         captions=[(['Turn a recipe into', 'a shopping list'], 'start'),
                   (['Recipes leave out', 'what’s in your pantry'], 'card_scroll+1.2s')],
         fam='lemon', tape='gingham',
         decor=[('sk_i_eggs', 168, 'L', 1520, 8), ('sk_i_flour', 150, 'R', 1250, -8),
                ('sk_i_maple_syrup', 124, 'R', 1760, 10)]),
    dict(segment='shop', enter='wipe', cut_in='start', cut_out='next_card+2.1s',
         captions=[(['Check it off and', 'its sticker hops in'], 'start')],
         fam='ramune', tape='dots',
         decor=[('sk_i_banana', 160, 'L', 1520, 8), ('sk_i_croissant', 140, 'R', 1250, -8),
                ('sk_d_heart', 104, 'R', 1760, 10)]),
    dict(segment='glow', enter='wipe', cut_in='start', cut_out='second_tick+1.4s',
         captions=[(['Glow-in-the-dark', 'night theme'], 'start')],
         fam='periwinkle', dusk=True, tape='dots',
         decor=[('sk_d_kira', 150, 'L', 1520, 8), ('sk_d_glowstar', 120, 'R', 1250, -8),
                ('sk_d_glowstar', 92, 'R', 1760, 14)]),
    dict(segment='celebrate', enter='wipe', cut_in='last_tick-1.7s', cut_out='banner+2.8s',
         captions=[(['Every finished list', 'gets a celebration'], 'start')],
         fam='sakura', tape='sakura',
         decor=[('sk_i_lemonade', 150, 'L', 1520, 8), ('sparkle:lemon', 112, 'R', 1250, -8),
                ('sk_d_star', 104, 'R', 1760, 10)]),
    dict(segment='album', enter='wipe', cut_in='start', cut_out='end-0.4s',
         captions=[(['Collect 200+ stickers', 'in your album'], 'start')],
         fam='grape', tape='gingham',
         decor=[('sk_d_ribbon', 160, 'L', 1520, 8), ('sk_d_flower', 128, 'R', 1250, -8),
                ('sk_d_heart', 104, 'R', 1760, 10)]),
]
SCENE_KEYS = {'segment', 'cut_in', 'cut_out', 'enter', 'skip', 'speed', 'hold', 'loupe', 'highlight', 'captions',
              'fam', 'dusk', 'tape', 'decor'}


def frames(seconds):
    return round(seconds * FPS)


# ---------------------------------------------------------------------------------------------------------------------
# Easing
# ---------------------------------------------------------------------------------------------------------------------
def ease_out_cubic(p):
    return 1 - (1 - p) ** 3


def ease_in_cubic(p):
    return p ** 3


def ease_in_out_sine(p):
    return 0.5 - 0.5 * math.cos(math.pi * p)


def ease_out_back(p, c=1.70158):
    return 1 + (c + 1) * (p - 1) ** 3 + c * (p - 1) ** 2


def ease_in_back(p, c=1.70158):
    return (c + 1) * p ** 3 - c * p ** 2


def smoothstep(p):
    return p * p * (3 - 2 * p)


def overshoot(p, start, peak, at=0.55):
    """A pop's scale: from `start` up to `peak` (ease-out) by `at`, then back to exactly 1 (ease-in-out), so both
    joins are smooth."""
    if p >= 1:
        return 1.0
    if p < at:
        return start + (peak - start) * ease_out_cubic(p / at)
    return peak + (1 - peak) * ease_in_out_sine((p - at) / (1 - at))


# Each style: (in seconds, out seconds, in curve, out curve, peak scale); a curve maps progress p (0..1] and the
# item's turn offset to (scale, extra turn in degrees, opacity).
STYLES = {
    'lettering': (CAPTION_IN, CAPTION_OUT,
                  lambda p, d: (overshoot(p, 0.7, 1.06), 0.0, ease_out_cubic(min(1.0, p / 0.35))),
                  lambda p, d: (1 - 0.2 * p * p, 0.0, 1 - p * p), 1.06),
    'sticker': (STICKER_IN, STICKER_OUT,
                lambda p, d: (overshoot(p, 0.45, 1.1, 0.5), d * (1 - ease_out_back(p)),
                              ease_out_cubic(min(1.0, p / 0.3))),
                lambda p, d: (1 - 0.6 * ease_in_back(p), 0.0, 1 - ease_in_cubic(p)), 1.1),
    'book': (BOOK_IN, BOOK_OUT,
             lambda p, d: (overshoot(p, 0.9, 1.03), d * (1 - ease_out_back(p)), ease_out_cubic(min(1.0, p / 0.4))),
             lambda p, d: (1 - 0.25 * ease_in_back(p), 0.0, 1 - ease_in_cubic(p)), 1.03),
    # the sticker's pop with the opacity left at 1: a card over words never shows them through it. It leaves by
    # shrinking, quicker and quicker, to about a quarter of its size on its last frame
    'loupe': (STICKER_IN, STICKER_OUT,
              lambda p, d: (overshoot(p, *LOUPE_POP, 0.5), d * (1 - ease_out_back(p)), 1.0),
              lambda p, d: (1 - p ** 1.5, 0.0, 1.0), LOUPE_POP[1]),
}


class Showing:
    """One stretch of time a sprite is on screen: it pops in at frame `n_in` and starts leaving at `n_out`."""

    def __init__(self, sid, layer, style, n_in, n_out, turn=0.0):
        self.sid, self.layer, self.style, self.turn = sid, layer, style, turn
        self.n_in, self.n_out = n_in, n_out
        t_in, t_out, self.curve_in, self.curve_out, _ = STYLES[style]
        self.len_in, self.len_out = max(1, frames(t_in)), max(1, frames(t_out))

    def at(self, n):
        """(scale, extra turn, opacity) at frame n, or None while hidden."""
        if n < self.n_in or n >= self.n_out + self.len_out:
            return None
        if n >= self.n_out:
            p = (n - self.n_out + 1) / self.len_out
            return None if p >= 1 else self.curve_out(p, self.turn)
        if n < self.n_in + self.len_in:
            return self.curve_in((n - self.n_in + 1) / self.len_in, self.turn)
        return 1.0, 0.0, 1.0

    def settled(self):
        return self.n_in + self.len_in

    def gone(self):
        """The first frame it no longer shows."""
        return self.n_out + self.len_out


def turned_box(box, scale, turn, cx, cy):
    """The bounding box of `box` scaled by `scale` and turned by `turn` degrees about (cx, cy)."""
    rad = math.radians(turn)
    cos, sin = math.cos(rad), math.sin(rad)
    xs, ys = [], []
    for x in (box[0], box[2]):
        for y in (box[1], box[3]):
            dx, dy = (x - cx) * scale, (y - cy) * scale
            xs.append(cx + cos * dx + sin * dy)
            ys.append(cy - sin * dx + cos * dy)
    return min(xs), min(ys), max(xs), max(ys)


def boxes_meet(a, b, gap=0):
    return a[0] < b[2] + gap and b[0] < a[2] + gap and a[1] < b[3] + gap and b[1] < a[3] + gap


# ---------------------------------------------------------------------------------------------------------------------
# Segments: the recorder's frames, checked against the contract
# ---------------------------------------------------------------------------------------------------------------------
Tap = namedtuple('Tap', 'frame x y kind to_x to_y until')


class Segment:
    def __init__(self, folder):
        self.folder = folder
        self.name = folder.name
        meta_path = folder / 'segment.json'
        if not meta_path.exists():
            raise SystemExit(f'{folder}: no segment.json')
        meta = json.loads(meta_path.read_text())
        bad = lambda why: SystemExit(f'segment {self.name}: {why}')  # noqa: E731
        if meta.get('name') != self.name:
            raise bad(f'segment.json names it {meta.get("name")!r}')
        if meta.get('fps') != FPS:
            raise bad(f'recorded at {meta.get("fps")} fps, not {FPS}')
        if (meta.get('width'), meta.get('height')) != (SEG_W, SEG_H):
            raise bad(f'{meta.get("width")} x {meta.get("height")}, not {SEG_W} x {SEG_H}')
        if meta.get('theme') not in TAP_STYLE:
            raise bad(f'theme {meta.get("theme")!r} is neither day nor glow')
        self.theme = meta['theme']
        self.count = meta.get('frames')
        if not isinstance(self.count, int) or self.count < 2:
            raise bad(f'frames is {self.count!r}')
        self.paths = [folder / f'{i:05d}.png' for i in range(self.count)]
        found = sorted(p.name for p in folder.glob('*.png'))
        if found != [p.name for p in self.paths]:
            missing = sorted({p.name for p in self.paths} - set(found))[:3]
            extra = sorted(set(found) - {p.name for p in self.paths})[:3]
            raise bad(f'frames are not 00000.png to {self.count - 1:05d}.png (missing {missing}, extra {extra})')
        for p in self.paths:
            with Image.open(p) as im:
                if im.size != (SEG_W, SEG_H) or im.mode not in ('RGB', 'RGBA'):
                    raise bad(f'{p.name} is {im.size[0]} x {im.size[1]} {im.mode}')
        self.marks = {}
        for m in meta.get('marks', []):
            if not isinstance(m.get('frame'), int) or not 0 <= m['frame'] < self.count:
                raise bad(f'mark {m.get("name")!r} at {m.get("frame")!r} is outside 0..{self.count - 1}')
            if m.get('name') in self.marks:
                raise bad(f'mark {m["name"]!r} appears twice')
            self.marks[m['name']] = m['frame']
        on_screen = lambda x, y: (isinstance(x, (int, float)) and isinstance(y, (int, float))  # noqa: E731
                                  and 0 <= x < SEG_W and 0 <= y < SEG_H)
        self.taps = []
        for t in meta.get('taps', []):
            f, x, y, kind = t.get('frame'), t.get('x'), t.get('y'), t.get('kind', 'tap')   # no kind: a tap
            if not isinstance(f, int) or not 0 <= f < self.count:
                raise bad(f'a tap at frame {f!r} is outside 0..{self.count - 1}')
            if not on_screen(x, y):
                raise bad(f'the tap at frame {f} is off the screen: ({x!r}, {y!r})')
            if kind == 'tap':
                self.taps.append(Tap(f, x, y, kind, x, y, f))
            elif kind == 'drag':
                to_x, to_y, until = t.get('to_x'), t.get('to_y'), t.get('until')
                if not on_screen(to_x, to_y):
                    raise bad(f'the drag at frame {f} ends off the screen: ({to_x!r}, {to_y!r})')
                if not isinstance(until, int) or not f < until < self.count:
                    raise bad(f'the drag at frame {f} lifts at {until!r}, not after it and inside the segment')
                self.taps.append(Tap(f, x, y, kind, to_x, to_y, until))
            else:
                raise bad(f'the tap at frame {f} is a {kind!r}, neither a tap nor a drag')
        self.regions = {}          # name -> (rect, ...): a region is a box, or a list of boxes
        for name, spec in (meta.get('regions') or {}).items():
            many = isinstance(spec, (list, tuple)) and spec and all(isinstance(r, (list, tuple)) for r in spec)
            self.regions[name] = tuple(self._rect(r, f'region {name!r}') for r in (spec if many else [spec]))
        self._noted = set()

    def _rect(self, rect, what):
        if not (isinstance(rect, (list, tuple)) and len(rect) == 4 and all(isinstance(v, (int, float)) for v in rect)):
            raise SystemExit(f'segment {self.name}: {what} is not (x0, y0, x1, y1): {rect!r}')
        x0, y0, x1, y1 = rect
        if not (0 <= x0 < x1 <= SEG_W and 0 <= y0 < y1 <= SEG_H):
            raise SystemExit(f'segment {self.name}: {what} {tuple(rect)} is not a box on the screen')
        return tuple(rect)

    def rects(self, spec, what):
        """The rects `spec` names: a name from segment.json's regions (or, for a recording that names none,
        FALLBACK_REGIONS), a rect, or a list of names and rects."""
        if isinstance(spec, str):
            if spec in self.regions:
                return self.regions[spec]
            if not self.regions and spec in FALLBACK_REGIONS:
                if spec not in self._noted:
                    print(f'note: segment {self.name} names no regions; {spec!r} is the fallback '
                          f'{FALLBACK_REGIONS[spec]}')
                    self._noted.add(spec)
                return (FALLBACK_REGIONS[spec],)
            raise SystemExit(f'segment {self.name}: {what} {spec!r} is not a region the recording names '
                             f'(it has {", ".join(sorted(self.regions)) or "none"})')
        if isinstance(spec, (list, tuple)) and spec and all(isinstance(r, (str, list, tuple)) for r in spec):
            return tuple(r for part in spec for r in self.rects(part, what))
        return (self._rect(spec, what),)

    def region(self, spec, what):
        """The one rect a loupe's region names (see rects)."""
        rects = self.rects(spec, what)
        if len(rects) != 1:
            raise SystemExit(f'segment {self.name}: {what} {spec!r} is {len(rects)} boxes; a loupe shows one')
        return rects[0]

    def rings_at(self, idx, head, tail):
        """[(x, y, scale, disc opacity, ring opacity)] of the tap rings showing at frame idx of a stretch of the
        segment played from `head` to `tail` without a jump: a press that would start before `head`, or a finger that
        comes down after `tail`, never shows (it would pop up mid-press, or press on a screen that does not answer).
        The opacities are shares of the theme's (TAP_STYLE)."""
        out = []
        press, lift, settle = frames(TAP_PRESS), frames(TAP_LIFT), frames(DRAG_EASE)
        for t in self.taps:
            if t.frame - press < head or t.frame > tail:
                continue
            rel = idx - t.frame
            if -press <= rel < 0:
                p = (rel + press + 1) / (press + 1)
                out.append((t.x, t.y, 0.6 + 0.4 * ease_out_cubic(p), ease_out_cubic(p), ease_out_cubic(p)))
            elif t.kind == 'drag' and 0 <= rel <= t.until - t.frame:
                span = t.until - t.frame
                s = smoothstep(rel / span)
                # how far into the drag's lighter look: eased in after the press, out before the lift
                g = ease_in_out_sine(min(1.0, min(rel, span - rel) / settle))
                scale, disc, ring = (1 + (v - 1) * g for v in DRAG_LOOK)
                out.append((t.x + (t.to_x - t.x) * s, t.y + (t.to_y - t.y) * s, scale, disc, ring))
            elif 0 <= idx - t.until - (t.kind == 'drag') < lift:
                p = (idx - t.until - (t.kind == 'drag') + 1) / (lift + 1)
                fade = 1 - ease_in_out_sine(p)
                out.append((t.to_x, t.to_y, 1 + 0.35 * ease_out_cubic(p), fade, fade))
        return out

    BEAT = re.compile(r'^([a-z_][a-z0-9_]*)(?:([+-])(\d+(?:\.\d+)?)s)?$')

    def beat(self, spec, what):
        """The frame a beat names (see SCENES)."""
        if isinstance(spec, int):
            n = spec
        else:
            m = self.BEAT.match(spec.replace(' ', ''))
            if not m:
                raise SystemExit(f'scene {self.name}: {what} {spec!r} is not a beat')
            name, sign, secs = m.groups()
            if name == 'start':
                n = 0
            elif name == 'end':
                n = self.count - 1
            elif name in self.marks:
                n = self.marks[name]
            else:
                raise SystemExit(f'scene {self.name}: {what} {spec!r} names a mark the recording does not have '
                                 f'(it has {", ".join(sorted(self.marks)) or "none"})')
            if secs:
                n += (1 if sign == '+' else -1) * frames(float(secs))
        if not 0 <= n < self.count:
            raise SystemExit(f'scene {self.name}: {what} {spec!r} is frame {n}, outside 0..{self.count - 1}')
        return n

    def typing(self):
        """(first, last) frame of the typing, or None when the recording marks none."""
        if 'typing_start' not in self.marks:
            return None
        end = self.marks.get('typing_end', self.marks.get('sent'))
        return None if end is None else (self.marks['typing_start'], end)


def load_segments(root):
    if not root.is_dir():
        raise SystemExit(f'{root}: no such folder of recorded segments')
    names = [sc['segment'] for sc in SCENES]
    stray = sorted(p.name for p in root.iterdir() if p.is_dir() and p.name not in names)
    if stray:
        print('note: ignoring folders no scene uses:', ', '.join(stray))
    segs = {n: Segment(root / n) for n in names if (root / n).is_dir()}
    if not segs:
        raise SystemExit(f'{root}: none of the segments {", ".join(names)} is there')
    return segs


# ---------------------------------------------------------------------------------------------------------------------
# Placing the title card and the loupe (shared by the plan's checks and the renderer)
# ---------------------------------------------------------------------------------------------------------------------
_title_art = {}


def title_image(item):
    what, size, _, _, angle, _ = item
    if what == 'wordmark':
        return store.wordmark(size)
    if what == 'tagline':
        return store.pad_patch(store.FEATURE_TAGLINE, size)
    if what == 'book':
        # the trip's sticker book, taped down at two corners (as on the feature graphic)
        book = store.book_strip(size, angle=angle)
        scale = size / store.FG_BOOK_W
        tape = store.washi(TITLE['tape'], round(78 * scale), dp=1.7 * scale, angle=44)
        m = tape.width
        canvas = Image.new('RGBA', (book.width + 2 * m, book.height + 2 * m), (0, 0, 0, 0))
        canvas.alpha_composite(book, (m, m))
        x0, y0, x1, y1 = book.getbbox()
        for cx, cy in (store.FG_TAPE_TL, store.FG_TAPE_BR):
            px = m + (x0 + cx * scale if cx >= 0 else x1 + cx * scale)
            py = m + (y0 + cy * scale if cy >= 0 else y1 + cy * scale)
            store.place_centred(canvas, tape, px, py)
        return canvas.crop(canvas.getbbox())
    return store.decor_image((what, size, None, None, angle))


def title_art(item):
    """(RGBA image, x, y) of a title item at rest on the full-size frame."""
    if item not in _title_art:
        img = title_image(item)
        if item[0] in TITLE_AT_REST:
            # the white face: the stroke round the letters, or the patch, without the hard shadow
            a = np.asarray(img)
            ys, xs = np.nonzero((a[..., 3] >= 235) & (a[..., :3].min(axis=2) >= 235))
            x0, y0, x1, y1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
        else:
            x0, y0, x1, y1 = img.getbbox()
        _title_art[item] = (img, round(item[2] - (x0 + x1) / 2), round(item[3] - (y0 + y1) / 2))
    return _title_art[item]


def title_box(item):
    img, x, y = title_art(item)
    x0, y0, x1, y1 = img.getbbox()
    return x + x0, y + y0, x + x1, y + y1


def check_title():
    """The card stays on the frame, and no sticker on it comes near the wordmark's or the tagline's art."""
    items = TITLE['items']
    for item in items:
        x0, y0, x1, y1 = title_box(item)
        if x0 < 0 or x1 > W or y0 < 0 or y1 > H:
            raise SystemExit(f'title {item[0]} runs off the frame')
    lettering = [it for it in items if it[0] in TITLE_AT_REST]
    for item in items:
        if item[0] in TITLE_AT_REST or item[0] == 'book':
            continue
        img, x, y = title_art(item)
        grown = img.getchannel('A').filter(ImageFilter.MaxFilter(2 * TITLE_CLEARANCE + 1))
        g = np.asarray(grown) > 16
        for word in lettering:
            wimg, wx, wy = title_art(word)
            w = np.asarray(wimg.getchannel('A')) > 16
            ox0, oy0 = max(x, wx), max(y, wy)
            ox1, oy1 = min(x + g.shape[1], wx + w.shape[1]), min(y + g.shape[0], wy + w.shape[0])
            if ox0 < ox1 and oy0 < oy1 and (g[oy0 - y:oy1 - y, ox0 - x:ox1 - x]
                                            & w[oy0 - wy:oy1 - wy, ox0 - wx:ox1 - wx]).any():
                raise SystemExit(f'title {item[0]} comes within {TITLE_CLEARANCE} px of the {word[0]}')


def region_on_frame(rect):
    """A recorded-screen rect on the full-size frame, with the phone at rest."""
    return (SCREEN_X + rect[0] * SCREEN_K, SCREEN_Y + rect[1] * SCREEN_K,
            SCREEN_X + rect[2] * SCREEN_K, SCREEN_Y + rect[3] * SCREEN_K)


# pad: more white at either side; margin: the region's paper drawn out above and below it (both px of the card)
Loupe = namedtuple('Loupe', 'seg rect scale crop_w crop_h pad margin x y w h')


def loupe_layout(seg, rect, scale, y=None, width=None, margin=0):
    """Where a loupe sits: its card (with the shadow) is centred on the screen, LOUPE_GAP above the region it shows,
    or with its top at frame px `y`. A `width` (of the card's face, frame px) widens it with white either side, and a
    `margin` (recorded px) gives the region that much of its own paper above and below."""
    s = scale * SCREEN_K
    cw, ch = round((rect[2] - rect[0]) * s), round((rect[3] - rect[1]) * s)
    pad, mh = 0, round(margin * s)
    if width is not None:
        if width < cw + 2 * LOUPE_BORDER:
            raise SystemExit(f'scene {seg}: a loupe {width} px wide is narrower than its region ({cw} px at that '
                             'scale) and its border')
        pad = (width - cw - 2 * LOUPE_BORDER) // 2
    w = cw + 2 * (LOUPE_BORDER + pad) + LOUPE_SHADOW[0]
    h = ch + 2 * (LOUPE_BORDER + mh) + LOUPE_SHADOW[1]
    top = region_on_frame(rect)[1]
    x = round((SCREEN_BOX[0] + SCREEN_BOX[2]) / 2 - (w - LOUPE_SHADOW[0]) / 2)
    if y is None:
        y = math.floor(top - LOUPE_GAP - h)
    return Loupe(seg, rect, s, cw, ch, pad, mh, x, y, w, h)


class Highlight(namedtuple('Highlight', 'seg rects n_in n_out ink')):
    """Highlighter marks on `rects` of a segment's screen, swiping in one after another from output frame `n_in` and
    fading from `n_out`."""

    def swipe(self):
        return max(1, frames(HIGHLIGHT_IN))

    def fade(self):
        return max(1, frames(HIGHLIGHT_OUT))

    def starts(self):
        return [self.n_in + frames(HIGHLIGHT_STAGGER * j) for j in range(len(self.rects))]

    def gone(self):
        return self.n_out + self.fade()

    def at(self, n):
        """(each mark's swipe progress 0..1, the marks' opacity) at output frame n, or None while none shows."""
        if n < self.n_in or n >= self.gone():
            return None
        reach = tuple(round(ease_out_cubic(min(1.0, max(0.0, (n - s + 1) / self.swipe()))), 5) for s in self.starts())
        q = (n - self.n_out + 1) / (self.fade() + 1) if n >= self.n_out else 0.0
        return reach, round(1 - ease_in_out_sine(q), 5)


# ---------------------------------------------------------------------------------------------------------------------
# The plan: what every output frame shows
# ---------------------------------------------------------------------------------------------------------------------
class Clip(namedtuple('Clip', 'seg a b speed hold start head tail join')):
    """Segment frames a..b played at `speed`, then `hold` frames resting on b, from output frame `start`. head..tail is
    the stretch of the segment it belongs to (played through without a jump); `join` is how it comes in over the
    clip before ('fade' or 'wipe'), or None where it follows on directly."""

    def length(self):
        return math.ceil((self.b - self.a + 1) / self.speed) + self.hold

    def end(self):
        return self.start + self.length()

    def frame(self, n):
        return min(self.a + math.floor((n - self.start) * self.speed + 1e-9), self.b)

    def ring_frame(self, n):
        """The frame the tap rings follow: the screen's, and while it rests on b, one more each output frame (so a
        ring lifts as it would have instead of freezing)."""
        rest = self.end() - self.hold
        return self.frame(n) if n < rest else self.b + n - rest + 1

    def output(self, f):
        return self.start + math.floor((f - self.a) / self.speed + 1e-9)


FrameState = namedtuple('FrameState', 'ground under phone over screen loupes marks')
Layer = namedtuple('Layer', 'seg idx ring head tail')     # ring: the frame the rings follow, or None for none
Screen = namedtuple('Screen', 'layers blend w')            # blend: None, 'fade' or 'wipe'; w: the second's share


def transition(join):
    """The frames a transition ('fade' or 'wipe') takes."""
    return frames(TRANSITIONS[join])


class Plan:
    def __init__(self, segs):
        self.segs = segs
        self.scenes = [sc for sc in SCENES if sc['segment'] in segs]
        for sc in SCENES:
            if sc['segment'] not in segs:
                print(f'note: no {sc["segment"]!r} segment recorded; leaving that scene out')
        self.sprites = {}          # sprite id -> what to draw (resolved by the renderer)
        self.showings = []
        self.loupes = {}           # loupe sprite id -> Loupe
        self.loupe_since = {}      # loupe sprite id -> the segment frame it comes in at (no older press shows in it)
        self.highlights = []       # Highlight
        self.events = []           # (frame, label) worth a still
        for sc in SCENES:
            stray = sorted(set(sc) - SCENE_KEYS)
            if stray:
                raise SystemExit(f'scene {sc.get("segment")!r}: {", ".join(map(repr, stray))} is not a scene key '
                                 f'({", ".join(sorted(SCENE_KEYS))})')
        title_items = TITLE['items']
        check_title()

        # opening: the wordmark and tagline are there from the first frame, the rest pops in; the card clears from
        # the bottom up, one item a frame, and the phone rises once nothing is left in its way
        n_clear = frames(TITLE_CLEAR)
        bottom_first = sorted(range(len(title_items)), key=lambda j: -title_items[j][3])
        opening = []
        for j, item in enumerate(title_items):
            sid = ('title', j)
            self.sprites[sid] = ('title', item)
            style = self._title_style(item)
            n_in = -max(1, frames(STYLES[style][0])) if item[0] in TITLE_AT_REST else frames(item[5])
            sh = Showing(sid, 'under', style, n_in, n_clear + bottom_first.index(j), self._title_turn(item, j))
            self.showings.append(sh)
            opening.append((sh, title_box(item)))
        self.n_rise = self._rise_after(opening, n_clear)
        self.n_land = self.n_rise + frames(RISE)
        self.events += [(0, 'title_first'), (frames(title_items[2][5]) + 5, 'title_pop'), (n_clear - 1, 'title'),
                        (n_clear + 4, 'title_clearing'), (self.n_rise + 3, 'rise_early'),
                        (self.n_rise + frames(RISE) // 2, 'rise')]

        # the scenes' clips, end to end: across a skip or a scene change the next clip overlaps the one before by
        # its transition; where only the speed changes it follows on directly
        self.clips, scene_clips = [], []
        n = self.n_land
        for i, sc in enumerate(self.scenes):
            seg = segs[sc['segment']]
            a, b = seg.beat(sc['cut_in'], 'cut_in'), seg.beat(sc['cut_out'], 'cut_out')
            enter = sc.get('enter', 'fade')
            if i == 0 and 'enter' in sc:
                raise SystemExit(f'scene {seg.name}: the first scene comes in with the phone, so it has no enter')
            for how in [enter] + [spec[2] for spec in sc.get('skip', []) if len(spec) == 3]:
                if how not in TRANSITIONS:
                    raise SystemExit(f'scene {seg.name}: {how!r} is not a transition ({", ".join(TRANSITIONS)})')
            if b - a < 2 * transition(enter):
                raise SystemExit(f'scene {seg.name}: cut_in {sc["cut_in"]!r} is not well before cut_out')
            pieces, lo, join = [], a, enter          # (from, to, how it comes in)
            for spec in sc.get('skip', []):
                if len(spec) not in (2, 3):
                    raise SystemExit(f'scene {seg.name}: a skip is (from, to) or (from, to, transition): {spec!r}')
                s0, s1 = spec[:2]
                how = spec[2] if len(spec) == 3 else 'fade'
                t = transition(how)
                f0, f1 = seg.beat(s0, 'skip'), seg.beat(s1, 'skip')
                if not lo + t < f0 < f1 < b - t:
                    raise SystemExit(f'scene {seg.name}: skip {s0!r}..{s1!r} is not inside the cut, in order')
                pieces.append((lo, f0, join))
                lo, join = f1, how
            pieces.append((lo, b, join))
            typing = seg.typing()
            fast = []
            for s0, s1, factor in sc.get('speed', []):
                f0, f1 = seg.beat(s0, 'speed'), seg.beat(s1, 'speed')
                if not SPEED[0] <= factor <= SPEED[1]:
                    raise SystemExit(f'scene {seg.name}: speed {s0!r}..{s1!r} x{factor} is outside {SPEED[0]}x to '
                                     f'{SPEED[1]}x (the app’s own motion plays at 1x; only a wait or a still '
                                     'stretch goes faster)')
                if not (f0 < f1 and any(p0 <= f0 and f1 <= p1 for p0, p1, _ in pieces)
                        and all(f0 > g1 for _, g1, _ in fast)):
                    raise SystemExit(f'scene {seg.name}: speed {s0!r}..{s1!r} x{factor} is not inside one piece '
                                     'of the cut, in order')
                if typing and factor != 1 and f0 <= typing[1] and typing[0] <= f1:
                    raise SystemExit(f'scene {seg.name}: speed {s0!r}..{s1!r} x{factor} runs over the typing '
                                     f'(frames {typing[0]}..{typing[1]}), which plays at 1x')
                fast.append((f0, f1, factor))
            mine = []
            for k, (p0, p1, join) in enumerate(pieces):
                runs, lo = [], p0          # the piece in runs of one speed: (from, to, speed)
                for f0, f1, factor in fast:
                    if p0 <= f0 and f1 <= p1:
                        if f0 > lo:
                            runs.append((lo, f0 - 1, 1))
                        runs.append((f0, f1, factor))
                        lo = f1 + 1
                if lo <= p1:
                    runs.append((lo, p1, 1))
                for r, (f0, f1, factor) in enumerate(runs):
                    how = None
                    if r == 0 and (mine or self.clips):
                        how = join
                        n -= transition(how)        # comes in over the clip before
                    hold = 0
                    if r == len(runs) - 1:
                        # the scene's last clip rests for its `hold`; a clip before a skip rests on its last frame
                        # while the next piece comes in, so a beat just before the jump plays out whole
                        hold = frames(sc.get('hold', 0)) if k == len(pieces) - 1 else transition(pieces[k + 1][2])
                    clip = Clip(seg.name, f0, f1, factor, hold, n, p0, p1, how)
                    mine.append(clip)
                    n += clip.length()
            self.clips += mine
            scene_clips.append(mine)
        last = self.clips[-1]
        self.n_drop = last.end()
        if any(c.end() > after.start for c, after in zip(self.clips, self.clips[2:])):
            raise SystemExit('a clip is shorter than its transitions: move the cuts, skips or speed changes apart')
        for c in self.clips:
            if c.join:
                mid = c.start + transition(c.join) // 2
                self.events.append((mid, f'{c.seg}_{c.join}'))

        # scene changes: halfway through the transition from one scene's last clip to the next one's first
        changes = [clips[0].start + transition(clips[0].join) // 2 for clips in scene_clips[1:]]
        starts = [self.n_rise] + changes
        ends = changes + [self.n_drop]

        # captions
        caps = []
        for i, sc in enumerate(self.scenes):
            seg = segs[sc['segment']]
            for c, (lines, beat) in enumerate(sc['captions']):
                if len(lines) != 2:
                    raise SystemExit(f'scene {seg.name}: a caption has two lines: {lines!r}')
                for ln in lines:
                    if store.text_width(ln, store.CAPTION_SIZE) > store.CAPTION_MAX_W:
                        raise SystemExit(f'scene {seg.name}: caption line too wide: {ln!r}')
                if c == 0:
                    if beat != 'start':
                        raise SystemExit(f'scene {seg.name}: the first caption is up from the start')
                    at = self.n_rise + frames(CAPTION_ON_RISE) if i == 0 else starts[i]
                else:
                    at = self._output_frame(scene_clips[i], seg.beat(beat, 'caption'), seg.name, beat)
                    if not starts[i] < at < ends[i]:
                        raise SystemExit(f'scene {seg.name}: caption beat {beat!r} falls outside the scene')
                caps.append((lines, at, seg.name))
        self.captions = []
        for c, (lines, at, name) in enumerate(caps):
            out = caps[c + 1][1] - frames(CAPTION_OUT) if c + 1 < len(caps) else self.n_drop
            if out - at < frames(MIN_CAPTION):
                raise SystemExit(f'scene {name}: caption {" / ".join(lines)!r} is up {(out - at) / FPS:.2f} s, '
                                 f'under {MIN_CAPTION} s')
            sid = ('caption', c)
            self.sprites[sid] = ('caption', lines)
            sh = Showing(sid, 'over', 'lettering', at, out)
            self.showings.append(sh)
            self.captions.append((lines, at, out))
            self.events += [(at + 4, f'{name}_caption{c}_pop'),
                            (min(out - 1, at + frames(1.0)), f'{name}_caption{c}')]

        # decoration stickers, staggered in after each scene change and out before the next
        for i, sc in enumerate(self.scenes):
            n_in = (self.n_land - frames(0.1)) if i == 0 else starts[i] + frames(DECOR_DELAY)
            n_out = (self.n_drop - frames(0.1)) if i == len(self.scenes) - 1 else ends[i] - frames(DECOR_LEAD)
            for k, spec in enumerate(sc['decor']):
                sid = ('decor', i, k)
                self.sprites[sid] = ('decor', spec)
                turn = STICKER_TURN * (1 if k % 2 == 0 else -1)
                self.showings.append(Showing(sid, 'over', 'sticker', n_in + frames(DECOR_STAGGER * k),
                                             n_out + frames(DECOR_OUT_STAGGER * k), turn))
        for i, b in enumerate(changes):
            self.events += [(b - frames(0.12), f'change{i + 1}_early'), (b, f'change{i + 1}'),
                            (b + frames(0.25), f'change{i + 1}_late')]

        # loupes: up while their scene's screen plays one clip at a time, never during a transition
        for i, sc in enumerate(self.scenes):
            seg = segs[sc['segment']]
            for j, spec in enumerate(sc.get('loupe', [])):
                opts = spec[3] if len(spec) == 4 else {}
                if (len(spec) not in (3, 4) or not isinstance(opts, dict)
                        or set(opts) - {'scale', 'y', 'width', 'margin'}
                        or not 1 <= opts.get('scale', LOUPE_SCALE) <= 2.5
                        or not all(isinstance(opts.get(o, 0), int) for o in ('y', 'width', 'margin'))
                        or not 0 <= opts.get('margin', 0) <= 40):
                    raise SystemExit(f'scene {seg.name}: a loupe is (from, to, region) or (from, to, region, '
                                     f'{{"scale": 1 to 2.5, "y": frame px, "width": frame px, "margin": 0 to 40 '
                                     f'recorded px}}): {spec!r}')
                b0, b1, region = spec[:3]
                lp = loupe_layout(seg.name, seg.region(region, 'loupe region'), opts.get('scale', LOUPE_SCALE),
                                  opts.get('y'), opts.get('width'), opts.get('margin', 0))
                self._check_loupe(lp, seg.name)
                f_in = seg.beat(b0, 'loupe')
                n_in = self._output_frame(scene_clips[i], f_in, seg.name, b0)
                n_out = self._output_frame(scene_clips[i], seg.beat(b1, 'loupe'), seg.name, b1)
                sid = ('loupe', i, j)
                sh = Showing(sid, 'loupe', LOUPE_STYLE, n_in, n_out, LOUPE_TURN)
                if not (max(starts[i], self.n_land) <= n_in < n_out and sh.gone() <= ends[i]):
                    raise SystemExit(f'scene {seg.name}: loupe {b0!r}..{b1!r} is not inside the scene, in order, '
                                     'with the phone at rest')
                for m in range(n_in, sh.gone()):
                    if self.screen(m).blend:
                        raise SystemExit(f'scene {seg.name}: loupe {b0!r}..{b1!r} is up during a transition')
                self.loupes[sid] = lp
                self.loupe_since[sid] = f_in
                self.showings.append(sh)
                self.events += [(n_in + 4, f'{seg.name}_loupe_pop'), (sh.settled() + frames(1.0), f'{seg.name}_loupe'),
                                (n_out + 3, f'{seg.name}_loupe_out')]

        # highlighter marks: inside their scene, every mark swiped in before they fade, and gone before the scene ends
        for i, sc in enumerate(self.scenes):
            seg = segs[sc['segment']]
            for spec in sc.get('highlight', []):
                if len(spec) != 3:
                    raise SystemExit(f'scene {seg.name}: a highlight is (from, to, regions): {spec!r}')
                b0, b1, regions = spec
                if seg.theme not in HIGHLIGHT_INK:
                    raise SystemExit(f'scene {seg.name}: a highlight needs a recording in a theme with a highlighter '
                                     f'ink ({", ".join(HIGHLIGHT_INK)}), not {seg.theme}')
                rects = seg.rects(regions, 'highlight region')
                n_in = self._output_frame(scene_clips[i], seg.beat(b0, 'highlight'), seg.name, b0)
                n_out = self._output_frame(scene_clips[i], seg.beat(b1, 'highlight'), seg.name, b1)
                hl = Highlight(seg.name, rects, n_in, n_out, HIGHLIGHT_INK[seg.theme])
                if not (max(starts[i], self.n_land) <= n_in and hl.starts()[-1] + hl.swipe() <= n_out
                        and hl.gone() <= ends[i]):
                    raise SystemExit(f'scene {seg.name}: highlight {b0!r}..{b1!r} is not inside the scene, in order, '
                                     f'with time for its {len(rects)} marks to swipe in before they fade')
                self.highlights.append(hl)
                self.events += [(n_in + hl.swipe() // 2, f'{seg.name}_highlight_swipe'),
                                (hl.starts()[-1] + hl.swipe() + frames(1.0), f'{seg.name}_highlight'),
                                (n_out + hl.fade() // 2, f'{seg.name}_highlight_out')]

        # taps worth a still: each press as it lands, and a drag halfway
        for c in self.clips:
            for t in self.segs[c.seg].taps:
                if c.a <= t.frame <= c.b and t.frame - frames(TAP_PRESS) >= c.head:
                    self.events.append((c.output(t.frame), f'{c.seg}_{t.kind}'))
                    mid = (t.frame + t.until) // 2
                    if t.kind == 'drag' and c.a <= mid <= c.b:
                        self.events.append((c.output(mid), f'{c.seg}_drag_mid'))

        # ending: the phone drops away, the card comes back and holds
        n_back = self.n_drop + frames(TITLE_RETURN)
        settle = 0
        for j, item in enumerate(title_items):
            sid = ('title', j)
            sh = Showing(sid, 'under', self._title_style(item), n_back + frames(item[5]), 10 ** 9,
                         self._title_turn(item, j))
            self.showings.append(sh)
            settle = max(settle, sh.settled())
        self.total = settle + frames(TITLE_END_HOLD)
        self.events += [(self.n_drop + frames(DROP) // 2, 'drop'), (self.total - 1, 'end')]

        # the backdrop: one ground per scene, crossfading at each change
        title_ground = (TITLE['fam'], False, TITLE['tape'])
        grounds = [(sc['fam'], sc.get('dusk', False), sc['tape']) for sc in self.scenes]
        for sc in self.scenes:
            if sc.get('dusk', False) != (segs[sc['segment']].theme == 'glow'):
                raise SystemExit(f'scene {sc["segment"]}: a dusk backdrop goes with a glow recording, and only then')
        self.ground_keys = set(grounds) | {title_ground}
        gf = frames(GROUND_FADE)
        self.ground_track = [(None, title_ground)]
        fades = list(zip(changes, grounds[:-1], grounds[1:])) + [(self.n_drop + frames(0.2), grounds[-1], title_ground)]
        if grounds[0] != title_ground:
            fades.insert(0, (self.n_rise + frames(RISE) // 2, title_ground, grounds[0]))
        self.fades = [(b - gf // 2, gf, ga, gb) for b, ga, gb in fades if ga != gb]

        self.scene_clips = scene_clips
        self.length = self.total / FPS
        if not LENGTH[0] <= self.length <= LENGTH[1]:
            raise SystemExit(f'the video runs {self.length:.1f} s; keep it within {LENGTH[0]:.0f} to {LENGTH[1]:.0f} s '
                             '(move the cuts in SCENES)')
        self.events = sorted((n, lab) for n, lab in self.events if 0 <= n < self.total)

    @staticmethod
    def _title_style(item):
        return {'wordmark': 'lettering', 'tagline': 'lettering', 'book': 'book'}.get(item[0], 'sticker')

    @staticmethod
    def _title_turn(item, j):
        return -3.0 if item[0] == 'book' else STICKER_TURN * (1 if j % 2 == 0 else -1)

    @staticmethod
    def _rise_after(opening, n_clear):
        """The first frame the phone can start rising so that its top edge never passes over a title item that is
        still showing."""
        rise = frames(RISE)
        for n_rise in range(n_clear + 1, n_clear + 10 * FPS):
            clear = True
            for k in range(rise):
                top = PHONE_BOX[1] + Plan._rise_offset(k)
                for sh, box in opening:
                    if (box[0] < PHONE_BOX[2] and box[2] > PHONE_BOX[0] and top < box[3]
                            and sh.at(n_rise + k) is not None):
                        clear = False
            if clear:
                return n_rise
        raise SystemExit('the title card never clears out of the phone’s way')

    @staticmethod
    def _rise_offset(k):
        """px below its place, k frames into the rise."""
        p = (k + 1) / (frames(RISE) + 1)
        return round(PHONE_TRAVEL * (1 - ease_out_cubic(p)))

    @staticmethod
    def _check_loupe(lp, name):
        field = region_on_frame(lp.rect)
        # at rest, the card (with its shadow) is no wider than the phone's art: a card hanging past the die-cut reads as
        # a second, bigger screen
        if lp.x < PHONE_BOX[0] or lp.x + lp.w > PHONE_BOX[2]:
            raise SystemExit(f'scene {name}: the loupe is wider than the phone ({lp.w} px): give it a smaller region '
                             'or scale')
        # opaque on every frame of its pop, in and out: a part-faded card over the screen is a double exposure
        t_in, t_out, curve_in, curve_out, peak = STYLES[LOUPE_STYLE]
        if any(curve(k / n, LOUPE_TURN)[2] != 1 for curve, n in ((curve_in, max(1, frames(t_in))),
                                                                  (curve_out, max(1, frames(t_out))))
               for k in range(1, n + 1)):
            raise SystemExit(f'the loupe style {LOUPE_STYLE!r} fades the card: keep its opacity at 1')
        # at rest and at the top of its pop (overshot and still turned a little), with its shadow
        cx, cy = lp.x + lp.w / 2, lp.y + lp.h / 2          # the pop turns and scales the card about its middle
        for scale, turn in ((1.0, 0.0), (peak, LOUPE_TURN)):
            box = turned_box((lp.x, lp.y, lp.x + lp.w, lp.y + lp.h), scale, turn, cx, cy)
            if box[0] < 0 or box[1] < 0 or box[2] > W or box[3] > H:
                raise SystemExit(f'scene {name}: the loupe runs off the frame')
            if boxes_meet(box, field):
                raise SystemExit(f'scene {name}: the loupe covers the region it shows')

    @staticmethod
    def _output_frame(clips, f, name, beat):
        for c in clips:
            if c.a <= f <= c.b:
                return c.output(f)
        raise SystemExit(f'scene {name}: beat {beat!r} falls in a skipped span or outside the cut')

    def poster_frame(self, spec):
        """The output frame a 'scene:beat' names."""
        name, _, beat = spec.partition(':')
        for sc, clips in zip(self.scenes, self.scene_clips):
            if sc['segment'] == name and beat:
                seg = self.segs[name]
                return self._output_frame(clips, seg.beat(beat, 'poster'), name, beat)
        raise SystemExit(f'--poster-at {spec!r} is not scene:beat for a scene in the cut '
                         f'({", ".join(sc["segment"] for sc in self.scenes)})')

    def ground(self, n):
        key = self.ground_track[0][1]
        for start, length, ga, gb in self.fades:
            if n < start:
                break
            if n >= start + length:
                key = gb
                continue
            p = ease_in_out_sine((n - start + 1) / (length + 1))
            return ga, gb, round(p, 5)
        return key, None, 0.0

    def phone(self, n):
        if n < self.n_rise or n >= self.n_drop + frames(DROP):
            return None
        if n < self.n_land:
            return self._rise_offset(n - self.n_rise)
        if n < self.n_drop:
            return 0
        p = (n - self.n_drop + 1) / (frames(DROP) + 1)
        return round(PHONE_TRAVEL * ease_in_cubic(p))

    def screen(self, n):
        """The Screen for output frame n: one clip, or two while the second comes in over the first."""
        first, last = self.clips[0], self.clips[-1]
        if n < first.start:
            return Screen((Layer(first.seg, first.a, None, first.head, first.tail),), None, 0.0)
        live = [c for c in self.clips if c.start <= n < c.end()]
        if not live:
            return Screen((Layer(last.seg, last.b, None, last.head, last.tail),), None, 0.0)
        layers = tuple(Layer(c.seg, c.frame(n), c.ring_frame(n) if n >= self.n_land else None, c.head, c.tail)
                       for c in live)
        if len(live) == 1:
            return Screen(layers, None, 0.0)
        cb = live[1]
        w = round(ease_in_out_sine((n - cb.start + 1) / (transition(cb.join) + 1)), 5)
        return Screen(layers, cb.join, w)

    def state(self, n):
        under, over, loupes = [], [], []
        for sh in self.showings:
            s = sh.at(n)
            if s is not None:
                {'under': under, 'over': over, 'loupe': loupes}[sh.layer].append(
                    (sh.sid,) + tuple(round(v, 5) for v in s))
        marks = tuple((j,) + s for j, hl in enumerate(self.highlights) for s in [hl.at(n)] if s is not None)
        return FrameState(self.ground(n), tuple(under), self.phone(n), tuple(over), self.screen(n), tuple(loupes),
                          marks)


# ---------------------------------------------------------------------------------------------------------------------
# Drawing: premultiplied float32 layers, composited with numpy
# ---------------------------------------------------------------------------------------------------------------------
def to_scale(img, x, y, f):
    """An RGBA image placed at integer (x, y) on the full-size frame, at output scale f (1 or 1/2): (premultiplied
    float32 array, x, y). At half size the image is first padded onto even pixels, so it halves exactly."""
    if f == 1:
        return np.asarray(img.convert('RGBa'), np.float32) / 255, x, y
    k = round(1 / f)
    px, py = x % k, y % k
    w, h = img.width + px, img.height + py
    w, h = w + (-w) % k, h + (-h) % k
    big = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    big.paste(img, (px, py))
    small = big.convert('RGBa').resize((w // k, h // k), Image.LANCZOS)
    return np.asarray(small, np.float32) / 255, (x - px) // k, (y - py) // k


def over(canvas, patch, x, y, kmap=None):
    """Composite the premultiplied `patch` onto `canvas` at (x, y), clipped; whatever it covers of the screen weight
    map `kmap` (array, x, y) is covered by the same amount."""
    ch, cw = canvas.shape[:2]
    ph, pw = patch.shape[:2]
    x0, y0, x1, y1 = max(x, 0), max(y, 0), min(x + pw, cw), min(y + ph, ch)
    if x0 >= x1 or y0 >= y1:
        return
    p = patch[y0 - y:y1 - y, x0 - x:x1 - x]
    a = p[..., 3:4]
    region = canvas[y0:y1, x0:x1]
    region *= 1 - a
    region += p[..., :3]
    if kmap is not None:
        k, kx, ky = kmap
        kx0, ky0 = max(x0, kx), max(y0, ky)
        kx1, ky1 = min(x1, kx + k.shape[1]), min(y1, ky + k.shape[0])
        if kx0 < kx1 and ky0 < ky1:
            k[ky0 - ky:ky1 - ky, kx0 - kx:kx1 - kx] *= 1 - a[ky0 - y0:ky1 - y0, kx0 - x0:kx1 - x0, 0]


class Sprite:
    """Something drawn over the backdrop: its full-size RGBA art at its resting place, kept premultiplied at the
    output scale for frames at rest, and turned and scaled about its art's centre while it pops."""

    def __init__(self, img, x, y, f):
        self.f = f
        x0, y0, x1, y1 = img.getbbox()
        self.ax, self.ay = (x0 + x1) / 2, (y0 + y1) / 2         # art centre, in the image
        self.cx, self.cy = x + self.ax, y + self.ay             # ...and on the frame, at rest
        self.box = (x + x0, y + y0, x + x1, y + y1)             # the art at rest, on the full-size frame
        self.size = img.size
        self.pad = 40                                           # transparent margin, so a box resize stays inside
        self.src = Image.new('RGBA', (img.width + 2 * self.pad, img.height + 2 * self.pad), (0, 0, 0, 0))
        self.src.paste(img, (self.pad, self.pad))
        self.src = self.src.convert('RGBa')
        self.rest = to_scale(img, x, y, f)

    def patch(self, scale, turn):
        """(premultiplied float32 array, x, y) of the art scaled by `scale` and turned by `turn` degrees
        (anticlockwise) about its centre's resting place, at output scale. Unscaled and unturned, it is the resting
        image itself, so a pop settles without a pixel of jump."""
        if scale == 1 and turn == 0:
            return self.rest
        f, pad = self.f, self.pad
        s = scale * f
        cx, cy = self.cx * f, self.cy * f
        iw, ih = self.size
        rad = math.radians(turn)
        cos, sin = math.cos(rad), math.sin(rad)
        corners = [(u - self.ax, v - self.ay) for u in (0, iw) for v in (0, ih)]
        xs = [cx + s * (cos * dx + sin * dy) for dx, dy in corners]
        ys = [cy + s * (-sin * dx + cos * dy) for dx, dy in corners]
        ox, oy = math.floor(min(xs)) - 2, math.floor(min(ys)) - 2
        pw, ph = math.ceil(max(xs)) + 2 - ox, math.ceil(max(ys)) + 2 - oy
        # output pixel edge (X, Y) <- source point (sx, sy) in the padded art; the same map either way
        a, b, d, e = cos / s, -sin / s, sin / s, cos / s
        c = self.ax + pad + a * (ox - cx) + b * (oy - cy)
        g = self.ay + pad + d * (ox - cx) + e * (oy - cy)
        if turn == 0:
            # scale only: a box resize keeps the sub-pixel place and filters the way down properly
            box = (c, g, c + pw / s, g + ph / s)
            if box[0] < 0 or box[1] < 0 or box[2] > self.src.width or box[3] > self.src.height:
                raise SystemExit(f'a sprite scaled to {scale:.3f} needs more margin than {pad} px')
            out = self.src.resize((pw, ph), Image.LANCZOS, box=box)
        else:
            out = self.src.transform((pw, ph), Image.AFFINE, (a, b, c, d, e, g), resample=Image.BICUBIC)
        return np.asarray(out, np.float32) / 255, ox, oy

    def draw(self, canvas, scale, turn, opacity, kmap=None):
        arr, x, y = self.patch(scale, turn)
        if opacity < 1:
            arr = arr * opacity
        over(canvas, arr, x, y, kmap)


class Phone:
    """The die-cut phone of the screenshots, with the weight each pixel gives the screen: the phone drawn round a
    black screen plus that weight times the screen is the phone drawn round the screen (the store script's pastes
    are linear in the screen)."""

    def __init__(self, f):
        black, (sx, sy) = store.phone(Image.new('RGB', (store.SCREEN_W, store.SCREEN_H), (0, 0, 0)))
        white, _ = store.phone(Image.new('RGB', (store.SCREEN_W, store.SCREEN_H), (255, 255, 255)))
        self.img, self.x, self.y = to_scale(black, PHONE_X, PHONE_Y, f)
        lit, _, _ = to_scale(white, PHONE_X, PHONE_Y, f)
        k = (lit[..., :3] - self.img[..., :3]).mean(axis=2)
        # the screen's box in the phone at output scale
        self.sx = math.floor((PHONE_X + sx) * f) - self.x
        self.sy = math.floor((PHONE_Y + sy) * f) - self.y
        self.sw, self.sh = round(store.SCREEN_W * f), round(store.SCREEN_H * f)
        # at half size the reduction filter spreads the screen's edge a few pixels into the bezel: keep a margin
        # there (the screen is extended into it by its edge pixels)
        self.margin = m = 0 if f == 1 else 4
        x0, y0, x1, y1 = self.sx - m, self.sy - m, self.sx + self.sw + m, self.sy + self.sh + m
        outside = k.copy()
        outside[y0:y1, x0:x1] = 0
        if outside.max() > 1.5 / 255:
            raise SystemExit('the phone lets the screen show outside its box')
        self.k = np.clip(k[y0:y1, x0:x1], 0, 1)

    def draw(self, canvas, dy):
        over(canvas, self.img, self.x, self.y + dy)
        m = self.margin
        return self.k.copy(), self.x + self.sx - m, self.y + dy + self.sy - m


def ground_image(key):
    """A scene's backdrop and its washi at the two top corners, exactly as make_store_images.screenshot draws them."""
    fam, dusk, tape = key
    canvas = store.backdrop(fam, dusk)
    store.paste(canvas, store.washi(tape, 250, angle=38), -66, -40)
    store.paste(canvas, store.washi(tape, 250, angle=-38), W - 196, -40)
    return canvas


def draw_rings(scr, rings, k, ox, oy, theme):
    """Tap rings onto an image of the screen (float32 in 0..255), in place: a recorded point (x, y) is at
    (ox + x k, oy + y k) on it."""
    disc_c, disc_a, ring_c, ring_a = TAP_STYLE[theme]
    disc_c = np.array(store.rgb(disc_c), np.float32)
    ring_c = np.array(store.rgb(ring_c), np.float32)
    for x, y, scale, disc_k, ring_k in rings:
        cx, cy = ox + x * k, oy + y * k
        r, rw = TAP_RADIUS * k * scale, TAP_RING * k * scale
        x0, y0 = max(0, math.floor(cx - r - 2)), max(0, math.floor(cy - r - 2))
        x1, y1 = min(scr.shape[1], math.ceil(cx + r + 2)), min(scr.shape[0], math.ceil(cy + r + 2))
        if x0 >= x1 or y0 >= y1:
            continue
        yy, xx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
        d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
        disc = (np.clip(r - d + 0.5, 0, 1) * (disc_a * disc_k))[..., None]
        edge = (np.clip(rw / 2 - np.abs(d - (r - rw / 2)) + 0.5, 0, 1) * (ring_a * ring_k))[..., None]
        region = scr[y0:y1, x0:x1]
        region += (disc_c - region) * disc
        region += (ring_c - region) * edge


def highlight_mark(rect, j, k, m, size, ink):
    """The j-th highlighter mark of a scene, over `rect` (recorded px), for an image of the screen `size` px with k
    px a recorded px and the screen's corner at (m, m): (take, along, x, y). `take` (float32 h x w x 3) is how much of
    each channel the mark's ink takes away at full (a multiply: the pixel times 1 - take), `along` (h x w) where each
    pixel lies along the stroke, in recorded px measured parallel to the chisel's slanted edge, and (x, y) the box's
    place."""
    x0, y0, x1, y1 = rect
    tilt = math.tan(math.radians(HIGHLIGHT_TILT))
    span = abs((x1 - x0) * tilt)

    def wobble(x, edge):
        # two slow waves of fixed phases (golden-angle steps, so no two edges of two marks waver alike)
        a = 2.39996 * (2 * j + edge + 1)
        w = 0.6 * math.sin(2 * math.pi * x / 263 + a) + 0.4 * math.sin(2 * math.pi * x / 97 + 1.7 * a)
        return HIGHLIGHT_WOBBLE * (0.5 + 0.5 * w)

    def top(x):
        return y0 + HIGHLIGHT_INSET + (x - x0) * tilt + (span if tilt < 0 else 0) + wobble(x, 0)

    def bottom(x):
        return y1 - HIGHLIGHT_INSET + (x - x0) * tilt - (0 if tilt < 0 else span) - wobble(x, 1)

    # the outline: the top edge left to right, the chisel's slanted end, the bottom edge back, the slanted start
    left, right, s = x0 + HIGHLIGHT_INSET, x1 - HIGHLIGHT_INSET, HIGHLIGHT_SLANT
    xs = np.linspace(left + s, right, max(2, round((right - left) / 4)))
    outline = [(x, top(x)) for x in xs] + [(x, bottom(x)) for x in xs[::-1] - s]
    ss = 4                                                  # supersampled, then boxed down: smooth edges
    bx0, by0 = max(0, math.floor(m + x0 * k) - 1), max(0, math.floor(m + y0 * k) - 1)
    bw, bh = min(size[0], math.ceil(m + x1 * k) + 1) - bx0, min(size[1], math.ceil(m + y1 * k) + 1) - by0
    big = Image.new('L', (bw * ss, bh * ss), 0)
    ImageDraw.Draw(big).polygon([((m + x * k - bx0) * ss, (m + y * k - by0) * ss) for x, y in outline], fill=255)
    big = big.filter(ImageFilter.GaussianBlur(0.5 * ss))   # a felt tip's edge is a touch soft
    cover = np.asarray(big.resize((bw, bh), Image.BOX), np.float32) / 255
    # each pixel in recorded px
    u = (np.arange(bw, dtype=np.float32) + bx0 + 0.5 - m)[None, :] / k
    v = (np.arange(bh, dtype=np.float32) + by0 + 0.5 - m)[:, None] / k
    mid_top, mid_bottom = top((x0 + x1) / 2), bottom((x0 + x1) / 2)
    along = u - s * np.clip((mid_bottom - v) / (mid_bottom - mid_top), 0, 1)
    # the ink: a faint streak along the stroke, and a little more where the tip came down and where it lifted
    streak = 0.5 + 0.5 * np.sin(2 * np.pi * v / 11 + 2.39996 * j) * np.sin(2 * np.pi * v / 29 + 1.3 * j)
    pool = 0.3 * np.exp(-np.maximum(along - left, 0) / 9) + 0.2 * np.exp(-np.maximum(right - s - along, 0) / 7)
    density = (1 - 0.08 * streak + pool)[..., None]
    c = np.array(store.rgb(ink), np.float32) / 255
    take = cover[..., None] * (1 - c ** density)
    return take.astype(np.float32), np.broadcast_to(along, (bh, bw)).astype(np.float32), bx0, by0


class Renderer:
    def __init__(self, plan, f):
        self.plan, self.f = plan, f
        self.ow, self.oh = round(W * f), round(H * f)
        self.grounds = {}
        for key in sorted(plan.ground_keys):
            arr, _, _ = to_scale(ground_image(key), 0, 0, f)
            self.grounds[key] = np.ascontiguousarray(arr[..., :3])
        self.phone = Phone(f)
        self.sprites = {}
        for sid, (kind, spec) in plan.sprites.items():
            self.sprites[sid] = self._sprite(kind, spec)
        self.cards = {sid: self._card(lp) for sid, lp in plan.loupes.items()}
        k, m = self.phone.sw / SEG_W, self.phone.margin
        size = (self.phone.sw + 2 * m, self.phone.sh + 2 * m)
        self.marks = [[highlight_mark(rect, r, k, m, size, hl.ink) for r, rect in enumerate(hl.rects)]
                      for hl in plan.highlights]
        self._bases = OrderedDict()
        self._screens = OrderedDict()

    def _sprite(self, kind, spec):
        if kind == 'caption':
            img = store.puffy(spec, store.CAPTION_SIZE)
            return Sprite(img, round((W - img.width) / 2), store.CAPTION_TOP, self.f)
        if kind == 'decor':
            img = store.decor_image(spec)
            side, cy = spec[2], spec[3]
            x0, y0, x1, y1 = img.getbbox()
            x = SCREEN_BOX[0] - store.DECOR_GAP - x1 if side == 'L' else SCREEN_BOX[2] + store.DECOR_GAP - x0
            y = cy - (y0 + y1) / 2
            sp = Sprite(img, round(x), round(y), self.f)
            ax0, ay0, ax1, ay1 = sp.box
            if ax0 < SCREEN_BOX[2] and ax1 > SCREEN_BOX[0] and ay0 < SCREEN_BOX[3] and ay1 > SCREEN_BOX[1]:
                raise SystemExit(f'decor {spec[0]} covers the screen')
            if ax0 < 0 or ax1 > W or ay0 < 0 or ay1 > H:
                raise SystemExit(f'decor {spec[0]} runs off the frame')
            return sp
        img, x, y = title_art(spec)
        return Sprite(img, x, y, self.f)

    def draw_marks(self, part, seg, marks):
        """The highlighter marks of `seg` showing in `marks` (FrameState.marks) onto its screen image `part` (float32),
        in place: each swiped in as far as its reach, all at the marks' opacity, multiplied in."""
        k = self.phone.sw / SEG_W
        for j, reach, opacity in marks:
            if self.plan.highlights[j].seg != seg:
                continue
            for (take, along, x, y), r in zip(self.marks[j], reach):
                if r <= 0:
                    continue
                lo, hi = along.min() - 1 / k, along.max() + 1 / k
                shown = np.clip((lo + (hi - lo) * r - along) * k + 0.5, 0, 1) * np.float32(opacity)
                region = part[y:y + take.shape[0], x:x + take.shape[1]]
                region *= 1 - take * shown[..., None]

    @staticmethod
    def _card(lp):
        """The loupe's card without its content: (RGBA card, mask of the window the region shows through)."""
        size = (lp.w, lp.h)
        face = (0, 0, lp.w - LOUPE_SHADOW[0], lp.h - LOUPE_SHADOW[1])
        card = Image.new('RGBA', size, (0, 0, 0, 0))
        m = store.rr_mask(size, face, LOUPE_RADIUS)
        card.paste(Image.new('RGBA', size, store.rgb(store.STICKER_SHADOW)), (0, 0), store.shift(m, *LOUPE_SHADOW))
        card.paste(Image.new('RGBA', size, store.rgb(store.WHITE)), (0, 0), m)
        b, wh = LOUPE_BORDER, lp.crop_h + 2 * lp.margin
        window = store.rr_mask((lp.crop_w, wh), (0, 0, lp.crop_w, wh), LOUPE_RADIUS - b)
        return card, window

    # one frame -------------------------------------------------------------------------------------------------------
    def base(self, st):
        """Everything but the screen, for a frame state: (frame as uint8 with a black screen, and if the phone shows,
        (box on the frame, that box as float32, screen weights, offset into the screen))."""
        key = (st.ground, st.under, st.phone, st.over)
        if key in self._bases:
            self._bases.move_to_end(key)
            return self._bases[key]
        ga, gb, u = st.ground
        canvas = self.grounds[ga].copy()
        if gb is not None:
            canvas += (self.grounds[gb] - canvas) * np.float32(u)
        for sid, s, t, o in st.under:
            self.sprites[sid].draw(canvas, s, t, o)
        screen = None
        if st.phone is not None:
            kmap = self.phone.draw(canvas, round(st.phone * self.f))
            for sid, s, t, o in st.over:
                self.sprites[sid].draw(canvas, s, t, o, kmap)
            k, kx, ky = kmap
            x0, y0 = max(kx, 0), max(ky, 0)
            x1, y1 = min(kx + k.shape[1], self.ow), min(ky + k.shape[0], self.oh)
            if x0 < x1 and y0 < y1:
                # kept in 0..255, with the half for rounding added in: a frame adds weight x screen and truncates
                screen = ((x0, y0, x1, y1), canvas[y0:y1, x0:x1] * 255 + 0.5,
                          k[y0 - ky:y1 - ky, x0 - kx:x1 - kx][..., None], (x0 - kx, y0 - ky))
        else:
            for sid, s, t, o in st.over:
                self.sprites[sid].draw(canvas, s, t, o)
        out = (np.rint(canvas * 255).clip(0, 255).astype(np.uint8), screen)
        self._bases[key] = out
        if len(self._bases) > 6:
            self._bases.popitem(last=False)
        return out

    def screen_image(self, seg, idx):
        """(the recorded frame as RGB, and resampled into the phone's screen as a uint8 array)."""
        path = self.plan.segs[seg].paths[idx]
        data = path.read_bytes()
        key = hashlib.blake2b(data, digest_size=16).digest()
        if key in self._screens:
            self._screens.move_to_end(key)
            return self._screens[key]
        im = Image.open(io.BytesIO(data))
        im.load()
        if im.mode != 'RGB':
            bg = Image.new('RGBA', im.size, (255, 255, 255, 255))
            bg.alpha_composite(im.convert('RGBA'))
            im = bg.convert('RGB')
        arr = np.asarray(im.resize((self.phone.sw, self.phone.sh), Image.LANCZOS))
        if self.phone.margin:
            m = self.phone.margin
            arr = np.pad(arr, ((m, m), (m, m), (0, 0)), mode='edge')
        self._screens[key] = (im, arr)
        if len(self._screens) > 6:
            self._screens.popitem(last=False)
        return im, arr

    def loupe(self, img, sid, scale, turn, opacity, layer):
        """Draw a loupe onto the finished frame `img` (uint8), in place: the region cut from the full-size recorded
        frame (so its text stays crisp), with the tap rings of the presses made while it is up, in its card."""
        lp = self.plan.loupes[sid]
        seg = self.plan.segs[layer.seg]
        full, _ = self.screen_image(layer.seg, layer.idx)
        if lp.margin:
            # the region's own paper, its first and last rows drawn out, resampled from the region alone (a resample
            # of the whole frame would bring in a trace of what lies just outside it, and draw that out too)
            m = round(lp.margin / lp.scale)
            src = np.pad(np.asarray(full.crop(lp.rect)), ((m, m), (0, 0), (0, 0)), mode='edge')
            crop = Image.fromarray(src).resize((lp.crop_w, lp.crop_h + 2 * lp.margin), Image.LANCZOS)
        else:
            crop = full.resize((lp.crop_w, lp.crop_h), Image.LANCZOS, box=lp.rect)
        if layer.ring is not None:
            rings = seg.rings_at(layer.ring, max(layer.head, self.plan.loupe_since[sid]), layer.tail)
            if rings:
                arr = np.asarray(crop, np.float32).copy()
                draw_rings(arr, rings, lp.scale, -lp.rect[0] * lp.scale, lp.margin - lp.rect[1] * lp.scale,
                           seg.theme)
                crop = Image.fromarray(np.rint(arr).clip(0, 255).astype(np.uint8))
        card, window = self.cards[sid]
        card = card.copy()
        card.paste(crop, (LOUPE_BORDER + lp.pad, LOUPE_BORDER), window)
        sp = Sprite(card, lp.x, lp.y, self.f)
        arr, x, y = sp.patch(scale, turn)
        if opacity < 1:
            arr = arr * opacity
        ph, pw = arr.shape[:2]
        x0, y0, x1, y1 = max(x, 0), max(y, 0), min(x + pw, self.ow), min(y + ph, self.oh)
        region = img[y0:y1, x0:x1].astype(np.float32) / 255
        over(region, arr[y0 - y:y1 - y, x0 - x:x1 - x], 0, 0)
        img[y0:y1, x0:x1] = np.rint(region * 255).clip(0, 255).astype(np.uint8)

    def frame(self, n):
        st = self.plan.state(n)
        img, screen = self.base(st)
        if screen is None:
            return img
        (x0, y0, x1, y1), under, k, (ox, oy) = screen
        parts = []
        m = self.phone.margin
        for layer in st.screen.layers:
            _, part = self.screen_image(layer.seg, layer.idx)
            seg = self.plan.segs[layer.seg]
            rings = seg.rings_at(layer.ring, layer.head, layer.tail) if layer.ring is not None else []
            if rings or st.marks or len(st.screen.layers) > 1:
                part = part.astype(np.float32)
            if st.marks:
                self.draw_marks(part, layer.seg, st.marks)
            if rings:
                draw_rings(part, rings, self.phone.sw / SEG_W, m, m, seg.theme)
            parts.append(part)
        if st.screen.blend == 'fade':
            scr = parts[0] + (parts[1] - parts[0]) * np.float32(st.screen.w)
        elif st.screen.blend == 'wipe':
            # the next screen comes in from the top behind a soft edge, which sweeps from above the screen to
            # below it
            feather = WIPE_FEATHER * self.f
            edge = -feather / 2 + st.screen.w * (self.phone.sh + feather)
            y = np.arange(parts[0].shape[0], dtype=np.float32) - m + 0.5
            t = np.clip((edge - y) / feather + 0.5, 0, 1)
            t = (t * t * (3 - 2 * t))[:, None, None]
            scr = parts[0] + (parts[1] - parts[0]) * t
        else:
            scr = parts[0]
        val = k * scr[oy:oy + (y1 - y0), ox:ox + (x1 - x0)]
        val += under
        np.clip(val, 0, 255, out=val)
        img = img.copy()
        img[y0:y1, x0:x1] = val.astype(np.uint8)
        for sid, s, t, o in st.loupes:
            self.loupe(img, sid, s, t, o, st.screen.layers[0])
        return img


# ---------------------------------------------------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------------------------------------------------
_renderer = None


def _render(n):
    return _renderer.frame(n).tobytes()


def ffmpeg_command(ffmpeg, path, w, h, crf):
    return [ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
            '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{w}x{h}', '-framerate', str(FPS), '-i', 'pipe:0',
            '-an', '-sn', '-dn',
            '-vf', 'scale=out_color_matrix=bt709:out_range=tv:flags=bicubic+accurate_rnd+full_chroma_int,'
                   'format=yuv420p',
            '-c:v', 'libx264', '-preset', 'slow', '-tune', 'animation', '-crf', str(crf),
            '-profile:v', 'high', '-threads', '4',
            '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
            '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:v', '+bitexact',
            '-movflags', '+faststart', str(path)]


def contact_sheet(thumbs, out, cols=10):
    """Thumbnails of (frame number, image) with their times, in a grid."""
    tw = 162
    ims = [(n, im.resize((tw, round(im.height * tw / im.width)), Image.LANCZOS)) for n, im in thumbs]
    th = ims[0][1].height
    gap, label = 10, 22
    rows = math.ceil(len(ims) / cols)
    sheet = Image.new('RGB', (cols * (tw + gap) + gap, rows * (th + label + gap) + gap), (245, 245, 245))
    d = ImageDraw.Draw(sheet)
    fnt = store.font('zen_maru_gothic_bold.ttf', 15)
    for i, (n, im) in enumerate(ims):
        x, y = gap + (i % cols) * (tw + gap), gap + (i // cols) * (th + label + gap)
        sheet.paste(im, (x, y))
        d.text((x, y + th + 3), f'{n / FPS:5.2f} s  #{n}', font=fnt, fill=store.BALLPOINT)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


def show(path):
    return path.relative_to(REPO) if path.is_relative_to(REPO) else path


def main():
    global _renderer
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--frames', type=Path, default=os.environ.get('PICNIC_VIDEO_FRAMES'),
                    help='the recorded segments (default: $PICNIC_VIDEO_FRAMES)')
    ap.add_argument('--out', type=Path, help=f'the video (default: {OUT_DIR.relative_to(REPO)}/feature.mp4; '
                                             'with --preview, a file in the temp folder)')
    ap.add_argument('--poster', type=Path, help=f'the poster still (default: next to the default video; '
                                                'with --preview or --out, none unless given)')
    ap.add_argument('--poster-at', default=POSTER_AT, help=f'the poster’s beat, scene:beat (default {POSTER_AT})')
    ap.add_argument('--sheet', type=Path, help='also write a contact sheet of every Nth frame here')
    ap.add_argument('--every', type=int, default=15, help='N for --sheet (default 15: two a second)')
    ap.add_argument('--stills', type=Path, help='also write a PNG at every caption change, scene change, transition, '
                                                'tap and loupe here')
    ap.add_argument('--no-video', action='store_true', help='render only the stills, sheet and poster frames')
    ap.add_argument('--preview', action='store_true', help='half size, for a quick look')
    ap.add_argument('--crf', type=int, default=CRF, help=f'x264 quality (default {CRF})')
    ap.add_argument('--jobs', type=int, default=min(4, os.cpu_count() or 1), help='worker processes')
    ap.add_argument('--ffmpeg', default=shutil.which('ffmpeg'), help='the ffmpeg to use')
    args = ap.parse_args()
    if args.frames is None:
        raise SystemExit('give --frames DIR (or set PICNIC_VIDEO_FRAMES)')
    if not args.ffmpeg and not args.no_video:
        raise SystemExit('ffmpeg not found (apt: ffmpeg, brew: ffmpeg); or give --ffmpeg')
    out = args.out or (Path(tempfile.gettempdir()) / 'kirakira_feature_preview.mp4' if args.preview
                       else OUT_DIR / 'feature.mp4')
    poster = args.poster or (OUT_DIR / 'poster.png' if not (args.preview or args.out or args.no_video) else None)
    f = 0.5 if args.preview else 1.0

    t0 = time.time()
    plan = Plan(load_segments(args.frames))
    n_poster = plan.poster_frame(args.poster_at)
    plan.events = sorted(plan.events + [(n_poster, 'poster')])
    print(f'{len(plan.scenes)} scenes, {plan.total} frames ({plan.length:.1f} s); building the layers')
    for lines, at, end in plan.captions:
        print(f'  {at / FPS:5.2f}-{end / FPS:5.2f} s ({(end - at) / FPS:.2f} s)  {" / ".join(lines)}')
    _renderer = Renderer(plan, f)
    t1 = time.time()
    print(f'layers ready in {t1 - t0:.1f} s; rendering')

    ow, oh = _renderer.ow, _renderer.oh
    still_at = {}
    for n, label in plan.events:
        still_at.setdefault(n, []).append(label)
    if args.stills:
        args.stills.mkdir(parents=True, exist_ok=True)
    wanted = range(plan.total)
    if args.no_video:
        wanted = sorted(set(still_at if args.stills else ()) | {n_poster}
                        | (set(range(0, plan.total, args.every)) if args.sheet else set()))
    enc = None
    if not args.no_video:
        out.parent.mkdir(parents=True, exist_ok=True)
        enc = subprocess.Popen(ffmpeg_command(args.ffmpeg, out, ow, oh, args.crf), stdin=subprocess.PIPE)
    thumbs, poster_im = [], None
    if args.jobs > 1 and 'fork' in multiprocessing.get_all_start_methods():
        pool = multiprocessing.get_context('fork').Pool(args.jobs)
        frames_iter = pool.imap(_render, wanted, chunksize=4)
    else:
        pool, frames_iter = None, map(_render, wanted)
    try:
        for n, buf in zip(wanted, frames_iter):
            if enc:
                enc.stdin.write(buf)
            want_still = args.stills and n in still_at
            if want_still or (args.sheet and n % args.every == 0) or n == n_poster:
                im = Image.frombytes('RGB', (ow, oh), buf)
                if want_still:
                    im.save(args.stills / f'{n:04d}_{"+".join(still_at[n])}.png')
                if args.sheet and n % args.every == 0:
                    thumbs.append((n, im))
                if n == n_poster:
                    poster_im = im
            if enc and n % 150 == 0:
                print(f'  {n / FPS:5.1f} s')
    finally:
        if pool:
            pool.close()
            pool.join()
        if enc:
            enc.stdin.close()
    t2 = time.time()
    if enc:
        if enc.wait() != 0:
            raise SystemExit('ffmpeg failed')
        size = out.stat().st_size
        print(f'wrote {show(out)}: {plan.length:.1f} s, {ow} x {oh}, {size / 1e6:.2f} MB')
    print(f'render {t2 - t1:.1f} s for {len(wanted)} frames ({(t2 - t1) / len(wanted) * 1000:.0f} ms a frame, '
          f'{args.jobs} jobs); total {t2 - t0:.1f} s')
    if poster:
        store.save_png(poster_im, poster, 'RGB')
        print(f'wrote {show(poster)} (frame {n_poster}, {n_poster / FPS:.2f} s, {args.poster_at})')
    if args.sheet:
        contact_sheet(thumbs, args.sheet)
        print('wrote', args.sheet)
    if enc and size > MAX_BYTES:
        raise SystemExit(f'{out} is {size / 1e6:.2f} MB, over the {MAX_BYTES / 1e6:.0f} MB budget: raise --crf')


if __name__ == '__main__':
    main()
