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

## Feature video

`make_feature_video.py` cuts the app's own recorded screens into a short feature video in the same frame as the
screenshots, and writes it to `design/store/video/`:

| File | Size | What |
|---|---|---|
| `feature.mp4` | 1080 × 1920, 30 fps, H.264 (yuv420p, BT.709), no audio, under 10 MB | the title card, six scenes (write a list, a recipe, shopping, the celebration, the album, the night theme), the title card again |
| `poster.png` | 1080 × 1920, 24-bit, no alpha | the closing title card, for a thumbnail |

Google Play takes a promo video only as a YouTube link (fastlane supply reads it from a `video.txt` beside
`title.txt`), so the MP4 is for sharing and for uploading there.

### Recording the frames

```sh
PICNIC_VIDEO_FRAMES=/tmp/kk-frames ./gradlew :app:testDebugUnitTest \
    --tests 'earth.cora.picnic.store.FeatureVideoRecorder' --rerun
```

The recorder plays each flow on the app's own screens and writes one folder per segment (`write`, `recipe`,
`shop`, `celebrate`, `album`, `glow`): `00000.png`, `00001.png`, … (1080 × 2280, the whole screen, 30 frames per
second of app time) and a `segment.json` with the segment's name, fps, size, frame count, theme (`day` or `glow`),
its marks, the frames where the beats happen (`sent`, `rows_landed`, `first_tick`, …), and optionally its taps
(frame and screen position), which the video shows as a soft ring under the finger.

### Building the video

```sh
pip install -r design/store/requirements.txt      # and ffmpeg with libx264 (apt or brew: ffmpeg)
python3 design/store/make_feature_video.py --frames /tmp/kk-frames      # writes feature.mp4 and poster.png
python3 design/store/make_feature_video.py --frames /tmp/kk-frames --preview --out /tmp/kk.mp4 \
    --sheet /tmp/kk_sheet.png --every 15 --stills /tmp/kk_stills         # a quick half-size pass, for review
```

`--sheet` writes a contact sheet of every Nth frame and `--stills` a PNG at every caption change and scene change
(and mid-pop), which is the quickest way to look the cut over. `--frames` defaults to `$PICNIC_VIDEO_FRAMES`.

Two tables at the top of the script hold every choice, so a recut is an edit there. `SCENES` cuts each segment by
its marks (`'typing_start-0.6s'`, `'rows_landed+1.8s'`, `'end'`), with optional `skip` spans to leave out, `speed`
spans to play faster (the typing and the wait for the answer run at 2x) and a `hold` at the end; it times the
captions to marks and gives each scene a backdrop colour, washi tape and decoration stickers in the screenshots'
layout. `TITLE` lays out the title card (the wordmark, the tagline on a pad patch, the trip's sticker book from the
feature graphic and a few stickers) and when each piece pops in. The motion is the same everywhere: captions pop in
with a small overshoot and shrink away, stickers pop in one after another and settle from a few degrees off, the
backdrop crossfades between scenes, and the screen crossfades between clips. A scene whose segment was not recorded
is left out.

Each frame is drawn from the same pieces as the screenshots (`backdrop`, `washi`, `phone`, `puffy`, the decoration
stickers), so a frame at rest has a screenshot's layout exactly. Everything but the screen is drawn once per
resting state; a frame at rest costs one resample of the recorded screen into the phone (Lanczos, as the
screenshots do). Frames stream to ffmpeg as raw RGB over several processes. Nothing is random and nothing is
timestamped, so the same frames give the same video (with the same Pillow, cairosvg, numpy and ffmpeg versions).

### Rules the script checks

- every segment matches the recorder's contract: `segment.json` names it, 30 fps, 1080 × 2280, contiguous frames
  `00000.png` to the frame count, every frame that size, every mark inside the segment;
- every cut, skip, speed span and caption beat names a mark the recording has, in order and inside the cut, and
  every tap is on the screen;
- a dusk backdrop goes with a Glow recording, and only then;
- every caption has two lines, fits the width at the screenshots' size, and stays up at least 2.5 s;
- no decoration sticker covers the screen or runs off the frame at rest, and the title card stays on the frame;
- the video runs 30 to 60 s, and the MP4 is under 10 MB (it stops with an error otherwise: raise `--crf`).
