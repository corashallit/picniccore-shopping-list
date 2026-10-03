# Contact sheets (WP2, for review)

Rendered from the **emitted** drawables (`res/drawable/sk_*.xml` read back through `tools/vd2svg.py`, so they show what
the app gets, not the DSL's own SVG) on the Day and Glow binders, by Chromium. Regenerate after any change to
`baskets.py`, `deco.py`, `patterns.py` or `launcher.py`, and review them before merging (docs/DESIGN_STICKER.md §20
review criteria):

```sh
export PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs     # or npm i playwright
python3 design/stickers/tools/baskets_contact.py --out design/stickers/sheets --scale 1.25 --quantize
python3 design/stickers/tools/decor_contact.py --out design/stickers/sheets --scale 1.5 --quantize
```

(`--quantize` stores 256-colour PNGs, which keeps the repository small.)

| Sheet | What it shows |
|---|---|
| `baskets_contact_{day,glow}.png` | `baskets_contact` (checklist item 101): all 17 kinds, each at 80 × 64 (strip), 64 × 51 (tray header) and 120 × 96 dp (card), in every state: empty with the dotted landing spot (decorated kinds keep their decoration, R4; the bento, cooler and vanity case stand their lids open behind), one, three and seven gotten (the newest five + "+2"), done (lid shut, GOT IT on the lid at the hanko point, minis hidden), the peek (lid open, minis raised 8), the open lid alone, and the decoration layer alone |
| `decor_contact_{day,glow}.png` | every decor sticker at 52 and 22 dp, the stamp pictograms in their dashed circles inked in the aisle traces (the capsule for "Everything else"), the glyphs, the LCD glyph table with its vector extras, the ten tab patterns printed on their aisle pastels, the washi tapes, and the launcher under the circle, squircle and teardrop masks with the 66 dp safe zone dashed, and its themed monochrome layer |

The Android renderer's view of the same art is checked by `GeneratedArtTest` (every drawable inflates as a
VectorDrawable and as a Compose ImageVector) and the `launcher_*` and `critters_contact_*` goldens; `tools/roundtrip.py`
measures the emitter against the DSL (WP2 art: 168 drawables, at most 0.29 % of art pixels differ by more than
48/255, anti-aliasing only).
