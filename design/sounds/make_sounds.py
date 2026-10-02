#!/usr/bin/env python3
"""Kirakira Basket's ten sound clips (docs/DESIGN_STICKER.md §14.2; checklist item 275).

    python3 design/sounds/make_sounds.py           # write app/src/main/res/raw/sk_<name>.wav
    python3 design/sounds/make_sounds.py --check   # render in memory; exit 1 if a committed clip differs (CI: :app:checkSounds)

Original, synthesized clips: no samples, no third-party audio, pure Python (`math` + `wave`), so they are ours to ship
(see LICENCE.md here). Each recipe is the mockup's WebAudio `tone(f, d, type, vol, when, slide)` call, exactly:

  - an oscillator of `type` (sine, square, triangle, sawtooth) at `f` Hz, gliding exponentially to `slide` Hz over
    `d` seconds when a slide is given (WebAudio's exponentialRampToValueAtTime);
  - its gain starts at `vol` and decays exponentially to 0.0005 at `d` (exponentialRampToValueAtTime), with a 3 ms
    linear fade-in so no clip starts with a click;
  - tones start `when` seconds into the clip and are summed;
  - square, triangle and sawtooth are band-limited (additive harmonics below 0.9 x Nyquist), as WebAudio's oscillators
    are, so the high ticks do not alias at 22.05 kHz.

Every clip is 22.05 kHz, 16-bit mono PCM, as long as its last tone ends (start + duration), and normalised so its peak
is -6 dBFS (0.5 full scale). The recipe levels set the balance inside a clip (clunk's second knock, pon's second note);
`LEVELS` keeps each clip's loudest level so the player can restore the mockup's balance between clips
(`SoundPool` volume = level / 0.18) and stay soft.
"""
import io
import math
import os
import struct
import sys
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
RAW = os.path.join('app', 'src', 'main', 'res', 'raw')
RATE = 22050
FADE_IN = .003
FLOOR = .0005
PEAK = .5                     # -6.02 dBFS

# name -> [(frequency Hz, duration s, oscillator, level, start s, glide-to Hz or None)]  (§14.2, the mockup's SND table)
RECIPES = {
    'thunk': [(150, .12, 'triangle', .18, 0, 60)],
    'tk': [(1800, .03, 'square', .03, 0, None)],
    'pff': [(420, .09, 'sine', .07, 0, 220)],
    'clunk': [(110, .08, 'square', .08, 0, None), (70, .08, 'square', .06, .06, None)],
    'beep': [(1320, .05, 'square', .035, 0, None)],
    'pon': [(660, .08, 'sine', .1, 0, None), (990, .12, 'sine', .08, .07, None)],
    'kira': [(1568, .07, 'sine', .05, 0, None), (2093, .1, 'sine', .04, .06, None)],
    'peel': [(900, .05, 'sawtooth', .02, 0, 500)],
    'jingle': [(f, .1, 'square', .04, i * .11, None) for i, f in enumerate([523, 659, 784, 1047, 784, 1047])],
    'print': [(2400 + (i % 2) * 300, .025, 'square', .018, i * .12, None) for i in range(10)],
}
LEVELS = {name: max(t[3] for t in tones) for name, tones in RECIPES.items()}


def length(name):
    return max(start + d for _, d, _, _, start, _ in RECIPES[name])


def harmonics(kind, f_max):
    """(k, amplitude) of a band-limited oscillator, normalised like WebAudio's periodic waves (peak about 1)"""
    nyq = RATE / 2 * .9
    if kind == 'sine':
        return [(1, 1.0)]
    out = []
    k = 1
    while k * f_max < nyq:
        if kind == 'square' and k % 2:
            out.append((k, 4 / math.pi / k))
        elif kind == 'sawtooth':
            out.append((k, 2 / math.pi * (-1) ** (k + 1) / k))
        elif kind == 'triangle' and k % 2:
            out.append((k, 8 / math.pi ** 2 * (-1) ** ((k - 1) // 2) / k ** 2))
        k += 1
    return out or [(1, 1.0)]


def render_tone(buf, f0, d, kind, level, start, f1):
    n0, n = round(start * RATE), round(d * RATE)
    hs = harmonics(kind, max(f0, f1 or f0))
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = f0 * (f1 / f0) ** (t / d) if f1 else f0
        gain = level * (FLOOR / level) ** (t / d)
        if t < FADE_IN:
            gain *= t / FADE_IN
        s = sum(a * math.sin(k * phase) for k, a in hs)
        buf[n0 + i] += gain * s
        phase += 2 * math.pi * f / RATE


def render(name):
    total = max(round(start * RATE) + round(d * RATE) for _, d, _, _, start, _ in RECIPES[name])
    buf = [0.0] * total
    for tone in RECIPES[name]:
        render_tone(buf, *tone)
    peak = max(abs(v) for v in buf) or 1.0
    k = PEAK / peak
    pcm = struct.pack(f'<{total}h', *(max(-32768, min(32767, round(v * k * 32767))) for v in buf))
    out = io.BytesIO()
    with wave.open(out, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm)
    return out.getvalue()


def same(a, b):
    """equal, or the same header and samples within one LSB (libm can differ in the last ulp across machines)"""
    if a == b:
        return True
    if len(a) != len(b) or a[:44] != b[:44]:
        return False
    n = (len(a) - 44) // 2
    sa, sb = struct.unpack(f'<{n}h', a[44:]), struct.unpack(f'<{n}h', b[44:])
    return all(abs(x - y) <= 1 for x, y in zip(sa, sb))


def main(argv):
    check = '--check' in argv
    drift = []
    want = {}
    for name in RECIPES:
        path = os.path.join(RAW, f'sk_{name}.wav')
        want[path] = data = render(name)
        full = os.path.join(ROOT, path)
        old = open(full, 'rb').read() if os.path.exists(full) else None
        if old is None or not same(old, data):
            drift.append(path)
            if not check:
                os.makedirs(os.path.dirname(full), exist_ok=True)
                with open(full, 'wb') as f:
                    f.write(data)
    d = os.path.join(ROOT, RAW)
    stale = sorted(f for f in os.listdir(d) if f.startswith('sk_') and os.path.join(RAW, f) not in want) if os.path.isdir(d) else []
    size = sum(len(v) for v in want.values())
    print(f'sounds: {len(want)} clips, {size // 1024} KB; {len(drift)} differ' + (f'; stale: {", ".join(stale)}' if stale else ''))
    for name in RECIPES:
        print(f'  sk_{name}.wav  {length(name) * 1000:6.0f} ms  level {LEVELS[name]}')
    if check:
        for p in drift:
            print('drift: ' + p)
        return 1 if (drift or stale) else 0
    for f in stale:
        os.remove(os.path.join(d, f))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
