# The sound clips

`app/src/main/res/raw/sk_*.wav` (thunk, tk, pff, clunk, beep, pon, kira, peel, jingle, print) are original works made
for Kirakira Basket: `make_sounds.py` synthesizes every sample from sine, square, triangle and sawtooth oscillators
with `math`, following the recipes in docs/DESIGN_STICKER.md §14.2. They contain no recorded samples, no sound-library
material and no third-party audio, so no third-party licence or credit applies, and nothing about them needs to appear
on the About page. They belong to this repository and ship under the same terms as the rest of it.

The jingle is the ascending C-major arpeggio C5 E5 G5 C6 G5 C6 (523, 659, 784, 1047, 784, 1047 Hz): a common musical
figure, not a quotation of any melody.

| Clip | Recipe (oscillator, frequency, length, level) | Length | Cue (§14.3) |
|---|---|---|---|
| `sk_thunk` | triangle 150 → 60 Hz glide, 120 ms, .18 | 120 ms | hanko, aisle done, stamp, PERFECT! |
| `sk_tk` | square 1800 Hz, 30 ms, .03 | 30 ms | peel before the hop, receipt tear |
| `sk_pff` | sine 420 → 220 Hz glide, 90 ms, .07 | 90 ms | sticker lands in the basket |
| `sk_clunk` | square 110 Hz 80 ms, .08; then square 70 Hz 80 ms at +60 ms, .06 | 140 ms | cassette start / stop |
| `sk_beep` | square 1320 Hz, 50 ms, .035 | 50 ms | LCD digits tick, pet "FED" |
| `sk_pon` | sine 660 Hz 80 ms, .1; then sine 990 Hz 120 ms at +70 ms, .08 | 190 ms | Next card, capsule pops, pull to refresh |
| `sk_kira` | sine 1568 Hz 70 ms, .05; then sine 2093 Hz 100 ms at +60 ms, .04 | 160 ms | book magic appear, petal shake |
| `sk_peel` | sawtooth 900 → 500 Hz glide, 50 ms, .02 | 50 ms | un-check, page turn, tear-off, curl |
| `sk_jingle` | square 523, 659, 784, 1047, 784, 1047 Hz, 100 ms each, 110 ms apart, .04 | 650 ms | celebration start, bonus stage |
| `sk_print` | ten square ticks alternating 2400 / 2700 Hz, 25 ms each, 120 ms apart, .018 | 1105 ms | receipt prints |

Every clip is 22.05 kHz, 16-bit mono PCM with a 3 ms fade-in and an exponential decay to 0.0005, normalised to a
−6 dBFS peak (about 111 KB for all ten). Because each clip is normalised on its own, the player restores the mockup's
balance between them with `SoundPool` volume = level ÷ 0.18 (the level column), so the loudest cue, the thunk, plays
at full sonification volume and the print ticks at a tenth of it: short and soft.
