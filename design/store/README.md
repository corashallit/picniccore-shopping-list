# Store listing images

`make_store_images.py` draws every Google Play image for Kirakira Basket into the folder fastlane supply reads,
`fastlane/metadata/android/en-US/images/`:

| File | Size | What |
|---|---|---|
| `phoneScreenshots/01_list.png` … `08_surprises.png` | 1080 × 1920, 24-bit, no alpha | a puffy caption, a real screen in a die-cut phone, a pinstripe and gingham backdrop with a few of the app's stickers |
| `featureGraphic.png` | 1024 × 500, 24-bit, no alpha | the wordmark, a tagline on a pad patch, and the app's real trip sticker book taped onto a gingham blanket |
| `icon.png` | 512 × 512, 32-bit | the adaptive launcher icon (background and foreground layers), cropped to the part a launcher shows |

The text of the listing sits beside the images: `title.txt`, `short_description.txt` and `full_description.txt`.

## Refreshing them

```sh
pip install -r design/store/requirements.txt
python3 design/store/make_store_images.py                               # writes the images
python3 design/store/make_store_images.py --sheet /tmp/store_sheet.png   # also a contact sheet to review the set
```

Run it after the goldens are re-recorded, and look at the images before committing them. Nothing is random, so the
same inputs always give the same files (with the same Pillow, cairosvg and Cairo versions).

## Where everything comes from

- **Screens:** the app's own golden renders in `app/src/test/screenshots` (1080 × 2280), scaled into the phone. The
  recipe screenshot is a 1080 × 2280 window of the tall `recipe_card_day.png` render (recorded on a 1800 dp phone):
  it starts just above the sheet's taped top edge and ends between two lines of text. If that render changes, move
  the window in `SHOTS`; a golden recorded at real phone height would remove the need for it. Which golden each
  image uses, its caption, backdrop colour, washi tape and decoration stickers are listed in `SHOTS` at the top of
  the script.
- **Feature graphic:** the trip's sticker book is the `book_full.png` golden, cut out of the binder it is recorded on
  (the pinstripe ground is made transparent and the anti-aliased rim is split back into the book's edge colour and
  an alpha).
- **Lettering:** DynaPuff and Zen Maru Gothic from `app/src/main/res/font`, drawn the way `PuffyText` draws titles: a
  white stroke on a hard periwinkle shadow, with small gaps in the stroke closed. Captions are laid out word by word
  with each space widened by 0.2 em, so the gaps between words stay open under the stroke.
- **Stickers, washi and the icon:** the generated VectorDrawables in `app/src/main/res/drawable` (from the sticker DSL
  in `design/stickers`), read back with `design/stickers/tools/vd2svg.py` and rasterised by cairosvg, so they match
  what the app draws.

## Rules the script checks

- every caption has two lines and fits the width at the shared size (measured with the widened word gaps);
- no decoration sticker covers the screen or runs off the image;
- the sticker book on the feature graphic keeps 40 px clear of its edges;
- every screen is a full 1080 × 2280 golden (or a window of that size).

Play's own limits: screenshots and the feature graphic must be opaque (no alpha), the screenshots' long side at most
twice the short side, every file under 8 MB.
