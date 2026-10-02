# Bundled fonts

The app bundles four families, all SIL Open Font License 1.1, from the Google Fonts repository (docs/DESIGN_STICKER.md
§2.7). No downloadable fonts: Robolectric and offline phones get the same type.

```sh
pip install -r design/fonts/requirements.txt
python3 design/fonts/build_fonts.py           # fetch (cached in ~/.cache/kirakira-fonts), instance, subset, copy licences
python3 design/fonts/build_fonts.py --check   # rebuild in memory, exit 1 if res/font or assets/licenses differ
```

| File in `app/src/main/res/font/` | Source (google/fonts, pinned commit + SHA-256 in `build_fonts.py`) | Weight | Subset |
|---|---|---|---|
| `dynapuff_semibold.ttf` | `ofl/dynapuff/DynaPuff[wdth,wght].ttf`, instanced `wght=600, wdth=100` | 600 | Latin |
| `dynapuff_bold.ttf` | same, `wght=700, wdth=100` | 700 | Latin |
| `zen_maru_gothic_medium.ttf` | `ofl/zenmarugothic/ZenMaruGothic-Medium.ttf` | 500 | Latin + キ ラ き ら |
| `zen_maru_gothic_bold.ttf` | `ofl/zenmarugothic/ZenMaruGothic-Bold.ttf` | 700 | Latin + キ ラ き ら |
| `zen_maru_gothic_black.ttf` | `ofl/zenmarugothic/ZenMaruGothic-Black.ttf` | 900 | Latin (hanko text) |
| `klee_one_semibold.ttf` | `ofl/kleeone/KleeOne-SemiBold.ttf` | 600 | Latin |
| `hachi_maru_pop_regular.ttf` | `ofl/hachimarupop/HachiMaruPop-Regular.ttf` | 400 | Latin |

- **Latin** = U+0020–007E, U+00A0–00FF, U+0152–0153, U+2013–2014, U+2018–201F, U+2022, U+2026, U+00B7, U+00D7,
  U+2190–2193, as far as each source has them (none has U+201B or U+201F; DynaPuff also lacks ¬, µ, ←, ↑ and ↓, so
  the transcript arrow "bread → sourdough" is set in Klee One). The build fails unless every face covers printable
  ASCII, NBSP, the French letters, guillemets, curly quotes, dashes, the ellipsis, the bullet, the middle dot and ×.
  Layout features kept: `kern`, `liga`, `calt`, `tnum` (where a source has them); hinting dropped; the `name` table
  (copyright, licence) kept whole; timestamps kept from the source, so a rebuild is byte-identical.
- **Not bundled**: DotGothic16 (the LCD is a vector 5 × 7 dot matrix derived from its shapes, `deco.G57`) and Nanum
  Pen Script (a mockup prop). Hearts, stars and notes in copy are inline vectors (`sk_g_heart`, `sk_g_star`,
  `sk_g_star_outline`, `sk_g_note`), never glyphs.
- **Licences**: `app/src/main/assets/licenses/` holds the five OFL texts (DynaPuff, Zen Maru Gothic, Klee One,
  Hachi Maru Pop, and DotGothic16 as the source of the LCD glyphs) and `licenses.json`, the index the About page lists
  (family, file, licence, what it is used for, source). Subsetting is a modification under the OFL; none of the
  families declares a Reserved Font Name (the build checks), so the subsets keep their names.
- **Size**: about 223 KB in all (budget 1.5 MB, `FontSubsetTest`).
- **Tests**: `FontFilesTest` (the seven files, their `OS/2` weights, nothing else in `res/font`, no DotGothic16 or Nanum
  Pen Script), `FontSubsetTest` (cmap coverage, kana in Zen Maru 500/700, the size budget, the licences), both in
  `app/src/test/kotlin/earth/cora/picnic/assets/`.
