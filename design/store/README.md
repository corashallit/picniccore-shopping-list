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
| `feature.mp4` | 1080 × 1920, 30 fps, H.264 (yuv420p, BT.709), no audio, under 12 MB, 30 to 75 s | the title card, six scenes (write a list, a recipe, shopping, the night theme, the celebration, the album), the title card again |
| `poster.png` | 1080 × 1920, 24-bit, no alpha | the celebration as the video shows it (phone, caption and backdrop), for a thumbnail |

Google Play takes a promo video only as a YouTube link (fastlane supply reads it from a `video.txt` beside
`title.txt`), so the MP4 is for sharing and for uploading there.

### Recording the frames

```sh
PICNIC_VIDEO_FRAMES=/tmp/kk-frames ./gradlew :app:testDebugUnitTest \
    --tests 'earth.cora.picnic.store.FeatureVideoRecorder' --rerun
```

The recorder plays each flow on the app's own screens and writes one folder per segment (`write`, `recipe`,
`shop`, `glow`, `celebrate`, `album`): `00000.png`, `00001.png`, … (1080 × 2280, the whole screen, 30 frames per
second of app time) and a `segment.json` with the segment's name, fps, size, frame count, theme (`day` or `glow`),
its marks (the frames where the beats happen: `typing_start`, `typing_end`, `sent`, `sheet_up`, `rows_landed`,
`first_tick`, …), its taps and its regions. A tap is a frame and a screen position, with `"kind": "tap"` or
`"drag"`; a drag also gives where the finger lifted (`to_x`, `to_y`) and on which frame (`until`), and between its
first frame and `until` the finger moves along a smoothstep, u²(3 − 2u) of the way at u of the time. An entry with no
kind is a tap. `regions` names boxes of the screen worth a closer look, such as `"add_field": [x0, y0, x1, y1]`; a
region can also be a list of boxes (`"pantry_rows": [[x0, y0, x1, y1], …]`).

### Building the video

```sh
pip install -r design/store/requirements.txt      # and ffmpeg with libx264 (apt or brew: ffmpeg)
python3 design/store/make_feature_video.py --frames /tmp/kk-frames      # writes feature.mp4 and poster.png
python3 design/store/make_feature_video.py --frames /tmp/kk-frames --preview --out /tmp/kk.mp4 \
    --sheet /tmp/kk_sheet.png --every 15 --stills /tmp/kk_stills         # a quick half-size pass, for review
python3 design/store/make_feature_video.py --frames /tmp/kk-frames --no-video --stills /tmp/kk_stills \
    --poster /tmp/kk_poster.png                                          # only the stills, full size, no MP4
```

`--sheet` writes a contact sheet of every Nth frame and `--stills` a PNG at every caption, scene change,
transition, tap, drag, loupe and highlight (and mid-pop), which is the quickest way to look the cut over;
`--no-video` renders just those frames. The poster is the composed frame at `--poster-at` (default
`celebrate:banner+0.6s`, any `scene:beat`). `--frames` defaults to `$PICNIC_VIDEO_FRAMES`.

Two tables at the top of the script hold every choice, so a recut is an edit there. `SCENES` cuts each segment by
its marks (`'typing_start-1.8s'`, `'rows_landed+1.6s'`, `'end'`). Each scene can leave out `skip` spans, play
`speed` spans faster or slower, `hold` on its last frame, choose how it comes in (`enter`), show a `loupe` and lay
a `highlight` on the screen; it
times the captions to marks and gives the scene a backdrop colour, washi tape and decoration stickers in the
screenshots' layout. `TITLE` lays out the title card (the wordmark, the tagline on a pad patch, the trip's sticker
book from the feature graphic and a few stickers) and when each piece pops in.

**Pacing.** People read slower than one tends to guess, so the cut leaves room: every caption stays up at least
3.1 s, and the rests before and after a beat, the title card (3.2 s) and the closing card's hold (1.9 s) are
generous. The app's own motion always plays at 1x, and so do the typing and the celebration, its little ambient
decor included. The recorder films the rests long enough to be read (the empty list, the recipe's steps, the
recipe card's pantry rows, the list before a tick), so the cut needs no slow motion; dead air is left out with a
skip, and only a wait (the capsule turning while the answer comes back) plays faster, never above 1.6x. A factor
under 1 is there to stretch a lead-in that is wholly still.

**Motion.** Captions pop in with a small overshoot and shrink away; stickers pop in one after another and settle
from a few degrees off; the backdrop crossfades between scenes. The title card is already there on the first frame
(the wordmark and tagline; the book and stickers pop in), clears from the bottom up, and the phone rises only once
nothing is left in its way. Between two clips the screen either crossfades (`fade`, 0.3 s, for nearly alike
screens) or wipes (`wipe`, 0.35 s: the next screen comes in from the top down behind an 80 px soft edge, eased in
and out), which keeps two busy screens from making a muddy double exposure; a skip names its transition as a third
item, `('card_scroll+2.5s', 'stuck-0.75s', 'wipe')`, and a scene its `enter`. Before a skip, the screen rests on the
last frame while the next one comes in, so a beat just before the jump plays out whole.

**Loupe.** While someone types, the add field is small at video size, so `loupe=[('typing_start',
'sent+0.4s', 'add_field')]` floats a white die-cut sticker card with the house's hard periwinkle shadow just above
the field (clear of the dock's top edge), showing it live at 1.7x the phone screen's scale; it opens on the first
letter, so it never shows the field's grey example line enlarged. The write and recipe scenes each use one on the add
field and no others: a card over a dense list slices the lines round it and shows a second copy of what lies under
it. A fourth item gives options: `{'scale': 1.35, 'y': 1462, 'width': 584}` sets the scale, the card's top (frame
px, the phone at rest) and the width of its face (more white either side), and `{'margin': 14}` draws out 14
recorded px of the region's own paper above and below it (its first and last rows repeated, never the screen beyond
them). The recipe's card uses the first three to sit in the band between the list and the dock, 20 px under the last
aisle strip, clear of the dock's washi and covering the "end of the list" pill whole, and stays up until just before
the sheet rises, so "pancakes for 12" reads whole for 1.5 s; write's uses the margin, because its two lines fill the
field from top to bottom with "picnic day" just above it. The card pops in and out as a sticker does but opaque on
every frame: it grows from 0.3 with a small
overshoot (1.06) and settles from 3° off, and leaves by shrinking away in 0.22 s, so it never lies part-faded over
the words under it. The region is cut from the full-size recorded frame, so its text stays crisp, and the card is
never wider than the phone. Only the taps made while it is up show in it. A region is a name from `segment.json`'s
`regions` or a box of the screen; for a recording that names none, the script falls back to its own box for the add
field.

**Highlight.** To point at something on a busy screen without covering it, `highlight=[('card_scroll+2.4s',
'stuck-0.8s', 'pantry_rows')]` lays a mark of the app's own highlighter (Day's `#FFF3A6`, the band of a pressed row
and of a new row's 400 ms flash) on each box the region names: the recipe card's two "in your pantry" rows, while
the caption says what they are. It is drawn on the screen itself, so it rides and scales with the phone, and it
multiplies like ink, so the words under it stay dark and crisp. Each mark swipes in from left to right in 0.3 s
(ease-out), 0.2 s after the one before, and all fade together in 0.2 s; its edges are drawn by hand from fixed curves
(a chisel's slanted start and end, the app band's −0.6° tilt, a slight wobble along the top and bottom, a faint
streak and a little more ink where the tip came down). Glow has no highlighter ink (its band is a light on navy), so
a highlight goes on Day recordings only.

**Taps.** Each tap shows as a disc of the highlighter (`#FFF3A6`, a pressed row's band) with a ballpoint ring under
the finger: it presses in just before the tap and spreads and fades after it. The ring is a little wider than the
list's check spots, so on a tick it shows round the spot instead of lying on its dashes. In Glow the ring is the
glow-star mint (`#C9FFDE`) over a faint white disc, so it reads on the navy page and stands apart from the spots'
lavender dashes. A drag presses in the same way, follows the finger along its path with the recorder's easing and
lifts where the finger left the screen; while it moves, the disc steps back (0.7 of its size, fainter, eased in
over 0.2 s after the press and out over 0.2 s before the lift), so the words it carries along stay readable under
it. No ring shows before the phone has landed, a press that would begin before its clip does is left out, and while
a clip rests on its last frame the ring goes on lifting.

Each frame is drawn from the same pieces as the screenshots (`backdrop`, `washi`, `phone`, `puffy`, the decoration
stickers), so a frame at rest has a screenshot's layout exactly. Everything but the screen is drawn once per
resting state; a frame at rest costs one resample of the recorded screen into the phone (Lanczos, as the
screenshots do). Frames stream to ffmpeg as raw RGB over several processes. Nothing is random and nothing is
timestamped, so the same frames give the same video (with the same Pillow, cairosvg, numpy and ffmpeg versions).

### Rules the script checks

- every segment matches the recorder's contract: `segment.json` names it, 30 fps, 1080 × 2280, contiguous frames
  `00000.png` to the frame count, every frame that size, every mark inside the segment, every tap a tap or a drag
  on the screen (a drag lifting after it lands, inside the segment), every region a box on the screen or a list of
  them;
- every scene names only keys the script knows;
- every cut, skip, speed span, caption beat, loupe, highlight and the poster's beat names a mark the recording has,
  in order and inside the cut, and every transition is `fade` or `wipe`;
- no speed span goes above 1.6x or runs over the typing;
- a dusk backdrop goes with a Glow recording, and only then;
- every caption has two lines, fits the width at the screenshots' size, and stays up at least 3.1 s;
- a loupe stays inside its scene with the phone at rest and never shows during a transition; its card is no wider
  than the phone at rest, and at rest and at the top of its pop it stays on the frame and off the region it shows;
- a loupe's card stays opaque on every frame of its pop, in and out;
- a highlight's boxes are on the screen, its marks all swipe in before they fade, it is gone before its scene ends,
  and its recording is a Day one;
- no decoration sticker covers the screen or runs off the frame at rest, the title card stays on the frame, and no
  sticker on it comes within 6 px of the wordmark or the tagline;
- the video runs 30 to 75 s, and the MP4 is under 12 MB (it stops with an error otherwise: raise `--crf`, then check
  the pastel stripes and the celebration's rays for banding).
