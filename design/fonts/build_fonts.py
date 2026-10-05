#!/usr/bin/env python3
"""Bundled fonts (docs/DESIGN_STICKER.md §2.7; checklist items 18-21): fetch the SIL OFL 1.1 sources from the Google
Fonts repository, instance DynaPuff's variable font, subset every face, and copy the five OFL texts.

    pip install -r design/fonts/requirements.txt
    python3 design/fonts/build_fonts.py            # fetch (cached), build app/src/main/res/font/*.ttf and assets/licenses/*
    python3 design/fonts/build_fonts.py --check    # rebuild in memory; exit 1 if a committed file differs

Sources are pinned by commit and SHA-256 (SOURCES), so a rebuild is byte-identical. Downloads are cached in
$KIRAKIRA_FONT_CACHE (default ~/.cache/kirakira-fonts).

What it makes (§2.7):
  res/font/dynapuff_semibold.ttf        DynaPuff[wdth,wght] instanced wght=600, wdth=100       Latin
  res/font/dynapuff_bold.ttf            DynaPuff[wdth,wght] instanced wght=700, wdth=100       Latin
  res/font/zen_maru_gothic_medium.ttf   ZenMaruGothic-Medium (500)                             Latin + kana
  res/font/zen_maru_gothic_bold.ttf     ZenMaruGothic-Bold (700)                               Latin + kana
  res/font/zen_maru_gothic_black.ttf    ZenMaruGothic-Black (900)                              Latin (hanko text)
  res/font/klee_one_semibold.ttf        KleeOne-SemiBold (600)                                 Latin
  res/font/hachi_maru_pop_regular.ttf   HachiMaruPop-Regular (400)                             Latin
  assets/licenses/<Family>.txt          the OFL text of DynaPuff, Zen Maru Gothic, Klee One, Hachi Maru Pop, and
                                        DotGothic16 (the source of the LCD glyphs, which are vectors, not a font)
  assets/licenses/licenses.json         the index the About page lists

Latin subset = U+0020-007E, U+00A0-00FF, U+0152-0153, U+2013-2014, U+2018-201F, U+2022, U+2026, U+00B7, U+00D7,
U+2190-2193. Kana = キ ラ き ら. Layout features kept: kern, liga, calt, tnum. Hinting dropped. Subsetting is a
modification under the OFL; none of these families declares a Reserved Font Name (checked below), so the subsets keep
their names, and each keeps its copyright and licence records in the `name` table.
"""
import hashlib
import io
import json
import os
import sys
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
FONT_DIR = os.path.join('app', 'src', 'main', 'res', 'font')
LICENSE_DIR = os.path.join('app', 'src', 'main', 'assets', 'licenses')
CACHE = os.environ.get('KIRAKIRA_FONT_CACHE', os.path.expanduser('~/.cache/kirakira-fonts'))

# google/fonts at a fixed commit (raw files), each pinned by SHA-256
REPO = 'https://raw.githubusercontent.com/google/fonts/{commit}/{path}'
COMMIT = '9710da1eacb3be272583c3224dcb70f9da6eadbb'   # google/fonts main on 2026-10-02
SOURCES = {
    'dynapuff': ('ofl/dynapuff/DynaPuff%5Bwdth,wght%5D.ttf', 'c3752fc6dd8aaa46a88637f061a9a4e6ab44c02c5f0afd65528b91ae8ff6f948'),
    'dynapuff_ofl': ('ofl/dynapuff/OFL.txt', 'ad0bc2ae83f6dbf6f3edde26752324260c2fbaf9cc021748d363a879dd5b749c'),
    'zen_medium': ('ofl/zenmarugothic/ZenMaruGothic-Medium.ttf', '3cfdb98a13571ede17fcc769f5093a97c38b80a7b9b2ab754a26b4d822092b3b'),
    'zen_bold': ('ofl/zenmarugothic/ZenMaruGothic-Bold.ttf', 'fe24426b9c8b5523a0146a8235c8674eccf0493af354a53ec895c3596d9eb745'),
    'zen_black': ('ofl/zenmarugothic/ZenMaruGothic-Black.ttf', '6bd74fe76cd39ee0ec18775c3661d845343fb3f6f8fa09a3076638417baf741f'),
    'zen_ofl': ('ofl/zenmarugothic/OFL.txt', '2a20cf7ce1909d8ee1e949095d340f7d7656705f7c810a2d6faf56800ad0cb3d'),
    'klee': ('ofl/kleeone/KleeOne-SemiBold.ttf', 'b031ec426c23ca1143ef1f7d58bee7a79efe119ed654152f121c922202b303fd'),
    'klee_ofl': ('ofl/kleeone/OFL.txt', 'e376b0df8e8a2345a9533db6f0a5333a1107975569ad9d1973a7ee557161ca38'),
    'hachi': ('ofl/hachimarupop/HachiMaruPop-Regular.ttf', '78408910c8f1a2f174a279cbc1484b48b71780039eba3fe1be2bfcc5d4df3f98'),
    'hachi_ofl': ('ofl/hachimarupop/OFL.txt', '8d3c434650e84f42ddb33e9b5929089ea51c39b231b798dacb33087d5f05d9f1'),
    'dotgothic_ofl': ('ofl/dotgothic16/OFL.txt', 'b6630c61ea078cacd7fabe37d14ffe557a0b45b06683374a9aa9e24262993e33'),
}

LATIN = ([*range(0x20, 0x7F), *range(0xA0, 0x100), 0x152, 0x153, 0x2013, 0x2014, *range(0x2018, 0x2020), 0x2022, 0x2026,
          0xB7, 0xD7, *range(0x2190, 0x2194)])
KANA = [0x30AD, 0x30E9, 0x304D, 0x3089]          # キ ラ き ら, so "キラキラ" echoes in the field (egg 7)
# What every face must actually cover (the subset keeps whatever of LATIN a source has; a few rare code points such as
# U+201B, U+201F, and DynaPuff's ¬ µ ← ↑ ↓, are not in the sources): printable ASCII, NBSP, the French letters,
# guillemets, curly quotes, dashes, the ellipsis, the bullet, the middle dot and the multiplication sign.
FRENCH = 'ÀÂÄÇÈÉÊËÎÏÔÖÙÛÜàâäçèéêëîïôöùûüÿŒœ«»'
REQUIRED = sorted({*range(0x20, 0x7F), 0xA0, *map(ord, FRENCH), 0x2018, 0x2019, 0x201C, 0x201D, 0x2013, 0x2014, 0x2026,
                   0x2022, 0xB7, 0xD7})
FEATURES = ['kern', 'liga', 'calt', 'tnum']

# output file -> (source key, variable-font axes or None, code points, OS/2 weight it must report)
FONTS = {
    'dynapuff_semibold.ttf': ('dynapuff', {'wght': 600, 'wdth': 100}, LATIN, 600),
    'dynapuff_bold.ttf': ('dynapuff', {'wght': 700, 'wdth': 100}, LATIN, 700),
    'zen_maru_gothic_medium.ttf': ('zen_medium', None, LATIN + KANA, 500),
    'zen_maru_gothic_bold.ttf': ('zen_bold', None, LATIN + KANA, 700),
    'zen_maru_gothic_black.ttf': ('zen_black', None, LATIN, 900),
    'klee_one_semibold.ttf': ('klee', None, LATIN, 600),
    'hachi_maru_pop_regular.ttf': ('hachi', None, LATIN, 400),
}
# assets/licenses: file -> (source key, family, what it covers)
LICENSES = {
    'DynaPuff.txt': ('dynapuff_ofl', 'DynaPuff', 'Titles, the wordmark, tab labels and buttons'),
    'ZenMaruGothic.txt': ('zen_ofl', 'Zen Maru Gothic', 'Item names, body text and the hanko'),
    'KleeOne.txt': ('klee_ofl', 'Klee One', 'Handwritten notes, transcripts and the aisles text'),
    'HachiMaruPop.txt': ('hachi_ofl', 'Hachi Maru Pop', 'Doodles'),
    'DotGothic16.txt': ('dotgothic_ofl', 'DotGothic16', 'The LCD dot-matrix glyphs are derived from its shapes (drawn as vectors; the font is not bundled)'),
}


def fetch(key):
    path, sha = SOURCES[key]
    os.makedirs(CACHE, exist_ok=True)
    local = os.path.join(CACHE, f'{COMMIT}__' + path.replace('/', '__').replace('%5B', '[').replace('%5D', ']'))
    if not os.path.exists(local):
        url = REPO.format(commit=COMMIT, path=path)
        with urllib.request.urlopen(url, timeout=60) as r:
            data = r.read()
        with open(local, 'wb') as f:
            f.write(data)
    with open(local, 'rb') as f:
        data = f.read()
    got = hashlib.sha256(data).hexdigest()
    if sha and got != sha:
        raise SystemExit(f'{path}: SHA-256 {got} does not match the pinned {sha}')
    return data, got


def no_reserved_name(text, family):
    """a Reserved Font Name is declared in the copyright statement(s) above the licence; a subset (a Modified Version)
    could not keep that name"""
    head = text.split('This Font Software is licensed')[0]
    if 'reserved font name' in head.lower():
        raise SystemExit(f'{family}: the OFL text declares a Reserved Font Name; a subset must be renamed')


def build_font(name):
    key, axes, codepoints, weight = FONTS[name]
    data, _ = fetch(key)
    font = TTFont(io.BytesIO(data))
    if axes:
        font = instancer.instantiateVariableFont(font, axes, updateFontNames=True)
        font['OS/2'].usWeightClass = weight
    opts = subset.Options()
    opts.layout_features = FEATURES
    opts.hinting = False
    opts.name_IDs = ['*']                     # keep copyright, licence and URL records
    opts.name_languages = ['*']
    opts.notdef_outline = True
    opts.glyph_names = False
    opts.drop_tables += ['DSIG']
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=codepoints)
    sub.subset(font)
    cmap = font.getBestCmap()
    lacking = [f'U+{c:04X}' for c in REQUIRED + (KANA if KANA[0] in codepoints else []) if c not in cmap]
    if lacking:
        raise SystemExit(f'{name}: the subset lacks {", ".join(lacking)}')
    if font['OS/2'].usWeightClass != weight:
        raise SystemExit(f'{name}: OS/2 weight {font["OS/2"].usWeightClass}, expected {weight}')
    # deterministic output: keep the source's timestamps instead of stamping the build time
    font.recalcTimestamp = False
    out = io.BytesIO()
    font.save(out, reorderTables=True)
    return out.getvalue()


def build():
    files = {}
    for name in FONTS:
        files[os.path.join(FONT_DIR, name)] = build_font(name)
    index = []
    for fname, (key, family, use) in LICENSES.items():
        data, _ = fetch(key)
        text = data.decode('utf-8').replace('\r\n', '\n')
        no_reserved_name(text, family)
        files[os.path.join(LICENSE_DIR, fname)] = text.encode('utf-8')
        index.append(dict(family=family, file=fname, licence='SIL Open Font License 1.1', use=use,
                          source='https://fonts.google.com/specimen/' + family.replace(' ', '+')))
    files[os.path.join(LICENSE_DIR, 'licenses.json')] = (json.dumps(index, indent=2, ensure_ascii=False) + '\n').encode('utf-8')
    return files


def main(argv):
    if '--hashes' in argv:
        for k in SOURCES:
            print(f"    '{k}': ('{SOURCES[k][0]}', '{fetch(k)[1]}'),")
        return 0
    check = '--check' in argv
    files = build()
    drift = []
    for path, data in sorted(files.items()):
        full = os.path.join(ROOT, path)
        old = open(full, 'rb').read() if os.path.exists(full) else None
        if old != data:
            drift.append(path)
            if not check:
                os.makedirs(os.path.dirname(full), exist_ok=True)
                with open(full, 'wb') as f:
                    f.write(data)
    stale = [f for f in os.listdir(os.path.join(ROOT, FONT_DIR)) if os.path.join(FONT_DIR, f) not in files] \
        if os.path.isdir(os.path.join(ROOT, FONT_DIR)) else []
    total = sum(len(v) for k, v in files.items() if k.startswith(FONT_DIR))
    print(f'fonts: {len(FONTS)} files, {total // 1024} KB in res/font; {len(LICENSES)} licence texts; '
          f'{len(drift)} differ{"; stale: " + ", ".join(stale) if stale else ""}')
    if check:
        for d in drift:
            print('drift: ' + d)
        return 1 if (drift or stale) else 0
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
