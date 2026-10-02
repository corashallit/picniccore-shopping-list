# Direction C mockup generator

Builds `sticker.section.html`, the friend's live reference for the Kirakira Basket art pass (docs/DESIGN_STICKER.md
§18.4). It imports the sticker DSL from `../stickers/` (the same art the Android emitter writes into the app), so the
mockup and the app can never draw a sticker differently.

```sh
pip install -r design/stickers/requirements.txt
python3 design/mockup/build.py            # -> design/mockup/build/sticker.section.html (git-ignored)
python3 design/mockup/build.py --out /tmp/sticker.section.html
```

| File | What |
|---|---|
| `build.py` | page assembly, CSS scoping, the page-level lint (ids, selectors, gloss, colours) |
| `common.py` | page helpers (rows, trays, LCD, washi tiles); the tab patterns come from `../stickers/patterns.py` |
| `page_a.py` … `page_f.py`, `book.py`, `eggs.py`, `live.py` | the sections: phones 1–6, kit, baskets, motion, eggs, celebration |
| `script.js`, `script_live.js`, `script_eggs.js` | the page's interactions |
| `style*.css` | the page's styles (rounds r1–r9, concatenated in order) |
| `ai4/` | the six live-pipeline stickers shown on the Snap page (final4.json) and their cost ledger |

The page is self-contained HTML (inline SVG, CSS and JS; fonts from Google Fonts). The assertion suite
(`test/*.spec.mjs`, `controls.json`) and the sync pass belong to WP10 (§18.4).
