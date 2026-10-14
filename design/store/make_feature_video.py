#!/usr/bin/env python3
"""The feature video for Kirakira Basket: the app's own recorded screens, framed like the Play screenshots.

    PICNIC_VIDEO_FRAMES=/tmp/kk-frames ./gradlew :app:testDebugUnitTest \\
        --tests 'earth.cora.picnic.store.FeatureVideoRecorder' --rerun     # records the segments
    python3 design/store/make_feature_video.py --frames /tmp/kk-frames    # writes design/store/video/
    python3 design/store/make_feature_video.py --frames /tmp/kk-frames --preview --out /tmp/kk.mp4 \\
        --sheet /tmp/kk_sheet.png --every 15 --stills /tmp/kk_stills      # a quick half-size pass, for review

The recorder writes one folder per segment (write, recipe, shop, celebrate, album, glow): 00000.png, 00001.png, ...
(1080 x 2280, the whole phone screen at 30 fps of app time) and segment.json, which names the frames where the
beats happen ("sent", "rows_landed", ...). SCENES below cuts each segment by those marks and gives it a caption,
a backdrop, a washi tape and a few stickers, exactly as make_store_images.py lays out a screenshot; the title card
at each end is TITLE. A later edit to the cut, the wording or the timing is an edit to those two tables.

Each output frame is the scene's backdrop, the phone with the screen resampled into it, the decoration stickers and
the caption. Everything but the screen is the same from one frame to the next while nothing moves, so it is drawn
once and kept; a frame at rest costs one screen resample. Frames stream to ffmpeg (libx264) as raw RGB.

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
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_store_images as store  # noqa: E402  (the screenshots' backdrop, phone, lettering and stickers)

REPO = store.REPO
OUT_DIR = REPO / 'design/store/video'
W, H = store.W, store.H                     # 1080 x 1920, as the phone screenshots
SEG_W, SEG_H = 1080, 2280                   # a recorded frame: the whole phone screen
FPS = 30
CRF = 22                                    # x264 quality: no visible loss or banding, well under MAX_BYTES
MAX_BYTES = 10_000_000
LENGTH = (30.0, 60.0)                       # the whole video, in seconds
MIN_CAPTION = 2.5                           # every caption stays up at least this long, in seconds

# The phone sits exactly where a screenshot puts it (make_store_images.screenshot)
PHONE_X = round((W - (store.SCREEN_W + 2 * store.BEZEL)) / 2 - (store.DIECUT + 6))
PHONE_Y = store.PHONE_TOP - (store.DIECUT + 6)
SCREEN_X = PHONE_X + store.DIECUT + 6 + store.BEZEL
SCREEN_Y = PHONE_Y + store.DIECUT + 6 + store.BEZEL
SCREEN_BOX = (SCREEN_X, SCREEN_Y, SCREEN_X + store.SCREEN_W, SCREEN_Y + store.SCREEN_H)

# ---------------------------------------------------------------------------------------------------------------------
# Motion (seconds). Calm and cute: things pop in with a small overshoot, settle, and leave quickly.
# ---------------------------------------------------------------------------------------------------------------------
XFADE = 0.3            # the screen crossfades between clips (and between scenes)
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
# A tap the recorder logged ("taps" in segment.json) shows as a soft white disc with a ballpoint ring under the
# finger: it presses in for TAP_PRESS before the tap and spreads and fades for TAP_LIFT after it.
TAP_RADIUS, TAP_RING = 44, 6     # px of the recorded screen
TAP_PRESS, TAP_LIFT = 0.12, 0.36

# ---------------------------------------------------------------------------------------------------------------------
# The title card, at the start and again at the end. The wordmark and tagline, the trip's real sticker book taped
# onto the blanket (as on the feature graphic) and a few stickers, each popping in at `at` seconds into the card.
# Items: (what, size px, centre x, centre y, turn in degrees, at). `what` is 'wordmark', 'tagline', 'book', a
# drawable or 'sparkle:<family>'. The card holds until TITLE_CLEAR, then clears as the phone rises; at the end it
# comes back once the phone has dropped away and holds for TITLE_END_HOLD.
# ---------------------------------------------------------------------------------------------------------------------
TITLE = dict(fam='mint', tape='sakura', items=[
    ('wordmark', 104, 520, 640, 0, 0.15),
    ('tagline', 42, 540, 810, 0, 0.40),
    ('book', 880, 540, 1270, 2, 0.62),
    ('sparkle:lemon', 74, 968, 584, 0, 0.88),
    ('sk_d_star', 50, 1000, 650, 8, 1.00),
    ('sk_i_strawberry', 170, 250, 1610, 10, 1.12),
    ('sk_i_lemonade', 140, 840, 1600, -12, 1.24),
    ('sk_d_bee', 120, 196, 466, -10, 1.36),
])
TITLE_CLEAR = 2.5      # seconds: the card starts clearing, and the phone rises
TITLE_RETURN = 0.3     # after the phone starts dropping, before the card comes back
TITLE_END_HOLD = 1.5

# ---------------------------------------------------------------------------------------------------------------------
# The scenes, in order. A scene whose segment was not recorded is left out.
#   segment            the folder under --frames
#   cut_in, cut_out    the first and last frame shown: 'start', 'end', a mark from segment.json, either with an offset
#                      in seconds ('sent+1.5s', 'end-0.5s'), or a frame number
#   skip               optional spans to leave out, [(from, to)]: 'from' is the last frame before the jump, 'to' the
#                      first after it; the screen rests on 'from' while it crossfades to 'to'
#   speed              optional spans to play faster (or slower), [(from, to, factor)], e.g. typing at 2x
#   hold               optional seconds to rest on the last frame
#   captions           [(two lines, beat)]: the first is up from the scene's start ('start'); each next one takes
#                      over when its beat plays
#   fam, dusk, tape    backdrop family (dusk: the deeper night-theme ground), washi pattern, as in SHOTS
#   decor              (drawable or 'sparkle:<family>', size px, side 'L' / 'R', centre y, angle), as in SHOTS
# ---------------------------------------------------------------------------------------------------------------------
SCENES = [
    dict(segment='write', cut_in='typing_start-0.6s', cut_out='end', speed=[('typing_start', 'card_shown-0.2s', 2)],
         skip=[('stuck', 'rows_landed')],
         captions=[(['Write your list', 'any old way'], 'start'),
                   (['Your list, sorted', 'aisle by aisle'], 'card_shown+0.6s')],
         fam='mint', tape='sakura',
         decor=[('sk_i_strawberry', 176, 'L', 1520, 8), ('sk_d_bee', 132, 'R', 1250, -10),
                ('sk_d_ladybug', 112, 'R', 1760, 12)]),
    dict(segment='recipe', cut_in='typing_start-0.4s', cut_out='rows_landed+1.8s',
         speed=[('typing_start', 'card_shown-0.2s', 2)],
         skip=[('card_scroll+2s', 'stuck-0.6s'), ('stuck', 'rows_landed')],
         captions=[(['Turn a recipe into', 'a shopping list'], 'start'),
                   (['Recipes leave out', 'what\u2019s in your pantry'], 'card_scroll+1s')],
         fam='lemon', tape='gingham',
         decor=[('sk_i_eggs', 168, 'L', 1520, 8), ('sk_i_flour', 150, 'R', 1250, -8),
                ('sk_i_maple_syrup', 124, 'R', 1760, 10)]),
    dict(segment='shop', cut_in='first_tick-0.8s', cut_out='next_card+2s',
         captions=[(['Check it off and', 'its sticker hops in'], 'start')],
         fam='ramune', tape='dots',
         decor=[('sk_i_banana', 160, 'L', 1520, 8), ('sk_i_croissant', 140, 'R', 1250, -8),
                ('sk_d_heart', 104, 'R', 1760, 10)]),
    dict(segment='celebrate', cut_in='last_tick-0.6s', cut_out='banner+1.6s',
         speed=[('last_tick+0.4s', 'celebration', 2)],
         captions=[(['Every finished list', 'gets a celebration'], 'start')],
         fam='sakura', tape='sakura',
         decor=[('sk_i_lemonade', 150, 'L', 1520, 8), ('sparkle:lemon', 112, 'R', 1250, -8),
                ('sk_d_star', 104, 'R', 1760, 10)]),
    dict(segment='album', cut_in='scroll_start-0.8s', cut_out='end',
         captions=[(['Collect 200+ stickers', 'in your album'], 'start')],
         fam='grape', tape='gingham',
         decor=[('sk_d_ribbon', 160, 'L', 1520, 8), ('sk_d_flower', 128, 'R', 1250, -8),
                ('sk_d_heart', 104, 'R', 1760, 10)]),
    dict(segment='glow', cut_in='first_tick-0.8s', cut_out='end',
         captions=[(['Glow-in-the-dark', 'night theme'], 'start')],
         fam='periwinkle', dusk=True, tape='dots',
         decor=[('sk_d_kira', 150, 'L', 1520, 8), ('sk_d_glowstar', 120, 'R', 1250, -8),
                ('sk_d_glowstar', 92, 'R', 1760, 14)]),
]


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


def overshoot(p, start, peak, at=0.55):
    """A pop's scale: from `start` up to `peak` (ease-out) by `at`, then back to exactly 1 (ease-in-out), so both
    joins are smooth."""
    if p >= 1:
        return 1.0
    if p < at:
        return start + (peak - start) * ease_out_cubic(p / at)
    return peak + (1 - peak) * ease_in_out_sine((p - at) / (1 - at))


# Each style: (in seconds, out seconds, in curve, out curve); a curve maps progress p (0..1] and the item's turn
# offset to (scale, extra turn in degrees, opacity).
STYLES = {
    'lettering': (CAPTION_IN, CAPTION_OUT,
                  lambda p, d: (overshoot(p, 0.7, 1.06), 0.0, ease_out_cubic(min(1.0, p / 0.35))),
                  lambda p, d: (1 - 0.2 * p * p, 0.0, 1 - p * p)),
    'sticker': (STICKER_IN, STICKER_OUT,
                lambda p, d: (overshoot(p, 0.45, 1.1, 0.5), d * (1 - ease_out_back(p)),
                              ease_out_cubic(min(1.0, p / 0.3))),
                lambda p, d: (1 - 0.6 * ease_in_back(p), 0.0, 1 - ease_in_cubic(p))),
    'book': (BOOK_IN, BOOK_OUT,
             lambda p, d: (overshoot(p, 0.9, 1.03), d * (1 - ease_out_back(p)), ease_out_cubic(min(1.0, p / 0.4))),
             lambda p, d: (1 - 0.25 * ease_in_back(p), 0.0, 1 - ease_in_cubic(p))),
}


class Showing:
    """One stretch of time a sprite is on screen: it pops in at frame `n_in` and starts leaving at `n_out`."""

    def __init__(self, sid, layer, style, n_in, n_out, turn=0.0):
        self.sid, self.layer, self.style, self.turn = sid, layer, style, turn
        self.n_in, self.n_out = n_in, n_out
        t_in, t_out, self.curve_in, self.curve_out = STYLES[style]
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


# ---------------------------------------------------------------------------------------------------------------------
# Segments: the recorder's frames, checked against the contract
# ---------------------------------------------------------------------------------------------------------------------
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
        if meta.get('theme') not in ('day', 'glow'):
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
        self.taps = []
        for t in meta.get('taps', []):
            f, x, y = t.get('frame'), t.get('x'), t.get('y')
            if not isinstance(f, int) or not 0 <= f < self.count:
                raise bad(f'a tap at frame {f!r} is outside 0..{self.count - 1}')
            if not (isinstance(x, (int, float)) and isinstance(y, (int, float)) and 0 <= x < SEG_W and 0 <= y < SEG_H):
                raise bad(f'the tap at frame {f} is off the screen: ({x!r}, {y!r})')
            self.taps.append((f, x, y))

    def taps_at(self, idx):
        """[(x, y, scale, opacity)] of the tap rings showing on frame idx."""
        out = []
        press, lift = frames(TAP_PRESS), frames(TAP_LIFT)
        for f, x, y in self.taps:
            rel = idx - f
            if -press <= rel < 0:
                p = (rel + press + 1) / (press + 1)
                out.append((x, y, 0.6 + 0.4 * ease_out_cubic(p), ease_out_cubic(p)))
            elif 0 <= rel < lift:
                p = (rel + 1) / (lift + 1)
                out.append((x, y, 1 + 0.35 * ease_out_cubic(p), 1 - ease_in_out_sine(p)))
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
# The plan: what every output frame shows
# ---------------------------------------------------------------------------------------------------------------------
class Clip(namedtuple('Clip', 'seg a b speed hold start')):
    """Segment frames a..b played at `speed`, then `hold` frames resting on b, from output frame `start`."""

    def length(self):
        return math.ceil((self.b - self.a + 1) / self.speed) + self.hold

    def frame(self, n):
        return min(self.a + math.floor((n - self.start) * self.speed + 1e-9), self.b)

    def output(self, f):
        return self.start + math.floor((f - self.a) / self.speed + 1e-9)


FrameState = namedtuple('FrameState', 'ground under phone over screen')


class Plan:
    def __init__(self, segs):
        self.segs = segs
        self.scenes = [sc for sc in SCENES if sc['segment'] in segs]
        for sc in SCENES:
            if sc['segment'] not in segs:
                print(f'note: no {sc["segment"]!r} segment recorded; leaving that scene out')
        self.sprites = {}          # sprite id -> what to draw (resolved by the renderer)
        self.showings = []
        self.events = []           # (frame, label) worth a still
        xf = frames(XFADE)
        title_items = TITLE['items']

        # opening: the title card pops in, holds, clears as the phone rises
        n_clear = frames(TITLE_CLEAR)
        self.n_rise = n_clear + 2
        self.n_land = self.n_rise + frames(RISE)
        top_first = sorted(range(len(title_items)), key=lambda j: title_items[j][3])
        for j, item in enumerate(title_items):
            sid = ('title', j)
            self.sprites[sid] = ('title', item)
            # the card clears from the top down, one item a frame, so the caption's place is clear first
            self.showings.append(Showing(sid, 'under', self._title_style(item), frames(item[5]),
                                         n_clear + top_first.index(j), self._title_turn(item, j)))
        self.events += [(frames(title_items[0][5]) + 5, 'title_pop'), (n_clear - 1, 'title'),
                        (self.n_rise + frames(RISE) // 2, 'rise')]

        # the scenes' clips, end to end: across a skip or a scene change the next clip overlaps the one before by
        # the crossfade; where only the speed changes it follows on directly
        self.clips, scene_clips = [], []
        n = self.n_land
        for sc in self.scenes:
            seg = segs[sc['segment']]
            a, b = seg.beat(sc['cut_in'], 'cut_in'), seg.beat(sc['cut_out'], 'cut_out')
            if b - a < 2 * xf:
                raise SystemExit(f'scene {seg.name}: cut_in {sc["cut_in"]!r} is not well before cut_out')
            pieces, lo = [], a
            for s0, s1 in sc.get('skip', []):
                f0, f1 = seg.beat(s0, 'skip'), seg.beat(s1, 'skip')
                if not lo + xf < f0 < f1 < b - xf:
                    raise SystemExit(f'scene {seg.name}: skip {s0!r}..{s1!r} is not inside the cut, in order')
                pieces.append((lo, f0))
                lo = f1
            pieces.append((lo, b))
            fast = []
            for s0, s1, factor in sc.get('speed', []):
                f0, f1 = seg.beat(s0, 'speed'), seg.beat(s1, 'speed')
                if not (0.25 <= factor <= 4 and f0 < f1 and any(p0 <= f0 and f1 <= p1 for p0, p1 in pieces)
                        and all(f0 > g1 for _, g1, _ in fast)):
                    raise SystemExit(f'scene {seg.name}: speed {s0!r}..{s1!r} x{factor} is not inside one piece '
                                     'of the cut, in order, at 0.25x to 4x')
                fast.append((f0, f1, factor))
            mine = []
            for k, (p0, p1) in enumerate(pieces):
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
                    if mine and r == 0 or not mine and self.clips:
                        n -= xf            # crossfades in from the clip before
                    hold = 0
                    if r == len(runs) - 1:
                        # the scene's last clip rests for its `hold`; a clip before a skip rests on its last frame
                        # while the next piece crossfades in, so a beat just before the jump plays out whole
                        hold = frames(sc.get('hold', 0)) if k == len(pieces) - 1 else xf
                    clip = Clip(seg.name, f0, f1, factor, hold, n)
                    mine.append(clip)
                    n += clip.length()
            self.clips += mine
            scene_clips.append(mine)
        last = self.clips[-1]
        self.n_drop = last.start + last.length()
        if any(c.start + c.length() > after.start for c, after in zip(self.clips, self.clips[2:])):
            raise SystemExit('a clip is shorter than its crossfades: move the cuts, skips or speed changes apart')

        # scene changes: halfway through the crossfade from one scene's last clip to the next one's first
        changes = [clips[0].start + xf // 2 for clips in scene_clips[1:]]
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
        for c, (lines, at, name) in enumerate(caps):
            out = caps[c + 1][1] - frames(CAPTION_OUT) if c + 1 < len(caps) else self.n_drop
            if out - at < frames(MIN_CAPTION):
                raise SystemExit(f'scene {name}: caption {" / ".join(lines)!r} is up {(out - at) / FPS:.2f} s, '
                                 f'under {MIN_CAPTION} s')
            sid = ('caption', c)
            self.sprites[sid] = ('caption', lines)
            sh = Showing(sid, 'over', 'lettering', at, out)
            self.showings.append(sh)
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

        # ending: the phone drops away, the card comes back and holds
        n_back = self.n_drop + frames(TITLE_RETURN)
        settle = 0
        for j, item in enumerate(title_items):
            sid = ('title', j)
            sh = Showing(sid, 'under', self._title_style(item), n_back + frames(item[5]) - frames(title_items[0][5]),
                         10 ** 9, self._title_turn(item, j))
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
    def _output_frame(clips, f, name, beat):
        for c in clips:
            if c.a <= f <= c.b:
                return c.output(f)
        raise SystemExit(f'scene {name}: beat {beat!r} falls in a skipped span or outside the cut')

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
            p = (n - self.n_rise + 1) / (self.n_land - self.n_rise + 1)
            return round(PHONE_TRAVEL * (1 - ease_out_cubic(p)))
        if n < self.n_drop:
            return 0
        p = (n - self.n_drop + 1) / (frames(DROP) + 1)
        return round(PHONE_TRAVEL * ease_in_cubic(p))

    def screen(self, n):
        """[(segment, frame, weight)] for output frame n: one clip, or two while they crossfade."""
        xf = frames(XFADE)
        first, last = self.clips[0], self.clips[-1]
        if n < first.start:
            return ((first.seg, first.a, 1.0),)
        live = [(c, c.frame(n)) for c in self.clips if c.start <= n < c.start + c.length()]
        if not live:
            return ((last.seg, last.b, 1.0),)
        if len(live) == 1:
            return ((live[0][0].seg, live[0][1], 1.0),)
        (ca, fa), (cb, fb) = live
        w = round(ease_in_out_sine((n - cb.start + 1) / (xf + 1)), 5)
        return ((ca.seg, fa, 1 - w), (cb.seg, fb, w))

    def state(self, n):
        under, over = [], []
        for sh in self.showings:
            s = sh.at(n)
            if s is not None:
                (under if sh.layer == 'under' else over).append((sh.sid,) + tuple(round(v, 5) for v in s))
        return FrameState(self.ground(n), tuple(under), self.phone(n), tuple(over), self.screen(n))


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
        img = title_image(spec)
        x0, y0, x1, y1 = img.getbbox()
        sp = Sprite(img, round(spec[2] - (x0 + x1) / 2), round(spec[3] - (y0 + y1) / 2), self.f)
        ax0, ay0, ax1, ay1 = sp.box
        if ax0 < 0 or ax1 > W or ay0 < 0 or ay1 > H:
            raise SystemExit(f'title {spec[0]} runs off the frame')
        return sp

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
        im = im.resize((self.phone.sw, self.phone.sh), Image.LANCZOS)
        arr = np.asarray(im)
        if self.phone.margin:
            m = self.phone.margin
            arr = np.pad(arr, ((m, m), (m, m), (0, 0)), mode='edge')
        self._screens[key] = arr
        if len(self._screens) > 6:
            self._screens.popitem(last=False)
        return arr

    def draw_taps(self, scr, taps):
        """Tap rings onto a screen image (float32 in 0..255, the phone's screen size plus its margin), in place."""
        k = self.phone.sw / SEG_W
        m = self.phone.margin
        ring = np.array(store.rgb(store.BALLPOINT_SOFT), np.float32)
        for x, y, scale, alpha in taps:
            cx, cy = m + x * k, m + y * k
            r, rw = TAP_RADIUS * k * scale, TAP_RING * k * scale
            x0, y0 = max(0, math.floor(cx - r - 2)), max(0, math.floor(cy - r - 2))
            x1, y1 = min(scr.shape[1], math.ceil(cx + r + 2)), min(scr.shape[0], math.ceil(cy + r + 2))
            if x0 >= x1 or y0 >= y1:
                continue
            yy, xx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
            d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
            disc = (np.clip(r - d + 0.5, 0, 1) * (0.5 * alpha))[..., None]
            edge = (np.clip(rw / 2 - np.abs(d - (r - rw / 2)) + 0.5, 0, 1) * (0.85 * alpha))[..., None]
            region = scr[y0:y1, x0:x1]
            region += (255 - region) * disc
            region += (ring - region) * edge

    def frame(self, n):
        st = self.plan.state(n)
        img, screen = self.base(st)
        if screen is None:
            return img
        (x0, y0, x1, y1), under, k, (ox, oy) = screen
        parts = []
        for seg, idx, w in st.screen:
            part = self.screen_image(seg, idx)
            taps = self.plan.segs[seg].taps_at(idx)
            if taps:
                part = part.astype(np.float32)
                self.draw_taps(part, taps)
            parts.append(part if w == 1 else part * np.float32(w))
        scr = parts[0] if len(parts) == 1 else parts[0] + parts[1]
        val = k * scr[oy:oy + (y1 - y0), ox:ox + (x1 - x0)]
        val += under
        np.clip(val, 0, 255, out=val)
        img = img.copy()
        img[y0:y1, x0:x1] = val.astype(np.uint8)
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


def main():
    global _renderer
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--frames', type=Path, default=os.environ.get('PICNIC_VIDEO_FRAMES'),
                    help='the recorded segments (default: $PICNIC_VIDEO_FRAMES)')
    ap.add_argument('--out', type=Path, help=f'the video (default: {OUT_DIR.relative_to(REPO)}/feature.mp4; '
                                             'with --preview, a file in the temp folder)')
    ap.add_argument('--poster', type=Path, help=f'the poster still (default: next to the default video; '
                                                'with --preview or --out, none unless given)')
    ap.add_argument('--sheet', type=Path, help='also write a contact sheet of every Nth frame here')
    ap.add_argument('--every', type=int, default=15, help='N for --sheet (default 15: two a second)')
    ap.add_argument('--stills', type=Path, help='also write a PNG at every caption change and scene change here')
    ap.add_argument('--preview', action='store_true', help='half size, for a quick look')
    ap.add_argument('--crf', type=int, default=CRF, help=f'x264 quality (default {CRF})')
    ap.add_argument('--jobs', type=int, default=min(4, os.cpu_count() or 1), help='worker processes')
    ap.add_argument('--ffmpeg', default=shutil.which('ffmpeg'), help='the ffmpeg to use')
    args = ap.parse_args()
    if args.frames is None:
        raise SystemExit('give --frames DIR (or set PICNIC_VIDEO_FRAMES)')
    if not args.ffmpeg:
        raise SystemExit('ffmpeg not found (apt: ffmpeg, brew: ffmpeg); or give --ffmpeg')
    out = args.out or (Path(tempfile.gettempdir()) / 'kirakira_feature_preview.mp4' if args.preview
                       else OUT_DIR / 'feature.mp4')
    poster = args.poster or (OUT_DIR / 'poster.png' if not (args.preview or args.out) else None)
    f = 0.5 if args.preview else 1.0

    t0 = time.time()
    plan = Plan(load_segments(args.frames))
    print(f'{len(plan.scenes)} scenes, {plan.total} frames ({plan.length:.1f} s); building the layers')
    _renderer = Renderer(plan, f)
    t1 = time.time()
    print(f'layers ready in {t1 - t0:.1f} s; rendering')

    ow, oh = _renderer.ow, _renderer.oh
    out.parent.mkdir(parents=True, exist_ok=True)
    enc = subprocess.Popen(ffmpeg_command(args.ffmpeg, out, ow, oh, args.crf), stdin=subprocess.PIPE)
    still_at = {}
    for n, label in plan.events:
        still_at.setdefault(n, []).append(label)
    if args.stills:
        args.stills.mkdir(parents=True, exist_ok=True)
    thumbs, last = [], None
    if args.jobs > 1 and 'fork' in multiprocessing.get_all_start_methods():
        pool = multiprocessing.get_context('fork').Pool(args.jobs)
        frames_iter = pool.imap(_render, range(plan.total), chunksize=4)
    else:
        pool, frames_iter = None, map(_render, range(plan.total))
    try:
        for n, buf in enumerate(frames_iter):
            enc.stdin.write(buf)
            want_still = args.stills and n in still_at
            if want_still or (args.sheet and n % args.every == 0) or n == plan.total - 1:
                im = Image.frombytes('RGB', (ow, oh), buf)
                if want_still:
                    im.save(args.stills / f'{n:04d}_{"+".join(still_at[n])}.png')
                if args.sheet and n % args.every == 0:
                    thumbs.append((n, im))
                last = im
            if n % 150 == 0:
                print(f'  {n / FPS:5.1f} s')
    finally:
        if pool:
            pool.close()
            pool.join()
        enc.stdin.close()
    if enc.wait() != 0:
        raise SystemExit('ffmpeg failed')
    t2 = time.time()
    size = out.stat().st_size
    print(f'wrote {out.relative_to(REPO) if out.is_relative_to(REPO) else out}: {plan.length:.1f} s, '
          f'{ow} x {oh}, {size / 1e6:.2f} MB')
    print(f'render {t2 - t1:.1f} s for {plan.total} frames ({(t2 - t1) / plan.total * 1000:.0f} ms a frame, '
          f'{args.jobs} jobs); total {t2 - t0:.1f} s')
    if poster:
        store.save_png(last, poster, 'RGB')
        print('wrote', poster.relative_to(REPO) if poster.is_relative_to(REPO) else poster)
    if args.sheet:
        contact_sheet(thumbs, args.sheet)
        print('wrote', args.sheet)
    if size > MAX_BYTES:
        raise SystemExit(f'{out} is {size / 1e6:.2f} MB, over the {MAX_BYTES / 1e6:.0f} MB budget: raise --crf')


if __name__ == '__main__':
    main()
