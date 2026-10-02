# Sticker DSL and the Android emitter

Every library sticker, basket, decor sticker, stamp pictogram, toolbar glyph, tab pattern, LCD glyph and the launcher
art of Kirakira Basket is authored here, in Python, once. The mockup (`design/mockup/`) draws it as inline SVG; the
emitter writes it into the app as VectorDrawables plus generated Kotlin manifests (docs/DESIGN_STICKER.md §3.2, §18.3).

```sh
pip install -r design/stickers/requirements.txt     # Python 3.11+
python3 design/stickers/build_android.py            # regenerate the app's art (writes only generated files)
python3 design/stickers/build_android.py --check    # what CI runs: exit 1 on drift or a validator error
./gradlew :app:checkStickers                        # the same check as a Gradle task
python3 design/stickers/tools/roundtrip.py [keys]   # render emitted vs DSL side by side (needs Node + Playwright)
python3 design/stickers/tools/contact.py [--ref k,k] keys  # review sheets: 52 / 30 / 22 dp on Day, Glow, Dusk + 120 dp detail
python3 design/stickers/tools/missing.py            # rewrite MISSING.md (the keys still to author, in 4 batches)
```

## Files

| File | Owner | What |
|---|---|---|
| `engine.py` | WP1 | palette (`C`, `deep`), parts (`F`, `TUBE`, `LINE`, `G`, `DOT`), `Art` (die-cut, fills, clipped puffy band, trace), path helpers |
| `gloss.py` | WP1 | the procedural bonbon gloss (`autogloss`) and its report |
| `icons.py`, `icons_b1..b4.py`, `icons_r3.py` | WP1 | the 121 r9 stickers; new keys go in `icons_batch1..4.py` or `icons_c<N>.py` (MISSING.md), both loaded after the r9 files |
| `catalog_map.py` | WP1 | DSL key → catalog key, orphans, the `peameal_bacon` addition, hop personalities |
| `variants.py` | WP1 | kawaii faces (album only) and the mockup's holo demo parts |
| `baskets.py` | WP2 | the 17 basket kinds: back, front, open and closed lids, rim, hanko point, decoration slice |
| `deco.py` | WP2 | decor stickers, stamp pictograms, toolbar glyphs, hanko, LCD glyph table `G57` |
| `patterns.py`, `launcher.py` | WP2 | the 10 tab-pattern tiles; the launcher hamper and holo star |
| `emit_android.py` | WP1 | the emitter (below) |
| `build_android.py` | WP1 | what is emitted where; the Kotlin manifests; `--check` |
| `validate.py`, `lintcol.py` | WP1 | build-time validators (below); `lintcol` is the mockup's part-level colour lint |
| `checkStickers.gradle.kts` | WP1 | `:app:checkStickers`, applied by `app/build.gradle.kts` |
| `tools/` | WP1 | `roundtrip.py` + `roundtrip.mjs` + `vd2svg.py` (round-trip render check), `contact.py` + `contact.mjs` (review contact sheets), `missing.py` |

## What is generated

| Output | From | Viewport |
|---|---|---|
| `res/drawable/sk_i_<key>.xml` | one per catalog key with art (`_capsule` → `sk_i_capsule`) | 52 × 52 |
| `res/drawable/sk_f_<key>.xml` | kawaii face overlay (album, achievements, empty states; never list rows) | 52 × 52 |
| `res/drawable/sk_b_<kind>_{back,front,lid_open,lid_closed,deco}.xml` | each basket kind (`BasketKind.key`); lids and deco only where drawn | 80 × 64 |
| `res/drawable/sk_d_<name>.xml` | decor stickers (bee, ladybug, ant, stars, hearts, sun, moon, seasonal, cat…) | 52 × 52 |
| `res/drawable/sk_p_<name>.xml` | stamp pictograms; paths named `ink` (aisle trace) and `paper` (white) | 24 × 24 |
| `res/drawable/sk_g_<name>.xml` | toolbar glyphs by the spec's names (§3.4) | 24 × 24 |
| `res/drawable/sk_t_<name>.xml` | tab-pattern mask tiles (white on transparent) | tile size |
| `res/drawable/sk_l_launcher_{background,foreground,monochrome}.xml` | adaptive launcher layers (shadow baked in; mono as alpha cut-outs) | 108 × 108 |
| `ui/theme/gen/StickerManifest.kt` | key → drawable, two families, `core.catalog.Personality`, face, source; the keys still missing | |
| `ui/theme/gen/BasketManifest.kt` | per kind: layers, rim, hanko point, lid behaviour; mini slots, badge slot, landing spot | |
| `ui/theme/gen/DecorManifest.kt` | decor stickers, pictograms, glyphs, launcher layers | |
| `ui/theme/gen/LcdGlyphs.kt` | the 5 × 7 glyph table from `deco.G57` | |
| `ui/theme/gen/PatternTiles.kt` | pattern tiles with their dp size | |

Only files matching `sk_[a-z]_*.xml` and those five Kotlin files are ever written or deleted. The launcher layers are
not wired into `mipmap-anydpi/ic_launcher.xml` yet (WP2 / the lead: point its background, foreground and monochrome at
the `sk_l_` layers).

## The emitter

`emit_android.icon_layers()` walks a sticker part by part, mirroring `engine.Art.part()` at stage 6, so the app gets
exactly what the mockup draws:

- every `<use>` is inlined; circles, ellipses and rects become path data; transforms (the parts' `tf`, an ellipse's or
  rect's own rotation, the viewport shift) are baked into the coordinates; arcs become cubics, `T`/`S` become `Q`/`C`;
- the puffy band and texture become a `<group>` whose `<clip-path>` is the part's shape (screen-space bands of
  rotated parts are computed exactly as the engine does);
- a fill and its outline with nothing between them merge into one path (VectorDrawable fills, then strokes);
- colours resolve to literal tokens; `var(--x,#hex)` keeps its fallback and names the path `x` (the capsule's `cap`,
  `cap_p`, `cap_tr` paths, recolourable per aisle through Compose's `VectorConfig`); the holo foil becomes an aapt
  linear gradient over the path's bounds.

Groups of a sticker (§3.2), in drawing order:

| Group | Contents |
|---|---|
| `cut` | one path: the union silhouette of every die-cut part (shapely; vertices kept, 0.03u simplification), even-odd, white fill + 8u round-join white stroke. Tinted, it is the hard shadow; stroked dashed, the ghost outline (`DashSpec.forPath` can measure it: it is a single silhouette) |
| `fills` | the art below the holo foil: every part up to the dominant shape, and the dominant's fill and puffy band |
| `lines` | the art above the holo foil: the dominant's trace and detail lines, then every later part, in the DSL's z-order |
| `gloss` | the procedural gloss strokes and dots |
| `dominant` | clip-only: the dominant shape (the largest die-cut fill part), for the holo clip |

Keeping the DSL's z-order is why `lines` can hold later parts' fills: the mockup's holo variant inserts the foil right
after the dominant part, and a strict "all fills, then all outlines" split would draw earlier outlines over later
shapes. In Compose, a `<clip-path>` becomes a child group carrying `clipPathData`: the holo clip is
`dominant`'s first child group's `clipPathData`.

## Validators (`validate.py`)

Run on every build and in `--check`; errors fail the task.

- **Palette**: every fill and stroke of `sk_i_` / `sk_f_` is one of the 40 family tokens or white. Decor, baskets,
  pictograms, glyphs, tiles and the launcher may also use the named extras in `validate.EXTRAS`.
- **Gloss**: one main gloss ≥2.4u × ≥10u per sticker; it covers 25–35 % of its shape's contour (the angle it spans,
  as `autogloss` draws it; a run cut short by a shape above it is a warning); sub-glosses ≥4u; every gloss mark on
  the art; none in a critter's head.
- **Geometry**: the union cut is present; base on y 42 (41.4–42.6); art inside 5–43 (live area 6–42 plus the 1u
  trace); every die-cut fill fits a 2u disc; die-cut rect corners r ≥2u; die-cut lines ≥1.2u; no text or images.
- **Baskets**: all 17 kinds; back, front, closed lid; decoration slice on exactly the four decorated kinds; hanko point
  on the closed lid; rim inside the art; open-standing lids drawn.

`validate.GRANDFATHERED` lists the approved r9 stickers that miss a geometry or gloss rule as drawn (they ship as the
friend approved them, §18.2); they are reported, not failed, and the list can only shrink (a stale entry warns). New
keys get no exemptions.

## Drift check

`--check` regenerates everything in memory and compares it with the tree: a missing, changed or stale generated file
fails. Numbers may differ by one rounding step (0.02) so a different libm or GEOS build on CI cannot fail it; any real
art change moves far more. The output is deterministic (no timestamps, stable order).

## Verification

`tools/roundtrip.py` renders every emitted drawable (converted back to SVG by `tools/vd2svg.py`, with the shadow drawn
from the tinted `cut` the way `ItemIcon` will) next to the DSL's own SVG symbol, both in Chromium at 8×, and reports
the share of art pixels that differ by more than 48/255 (anti-aliasing along curves only; see the numbers in the WP1
M1 report). The same drawables were also rendered through Android's VectorDrawable and Compose's `VectorPainter`
under Robolectric (native graphics) and compared with the DSL.
