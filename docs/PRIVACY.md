# Kirakira Basket privacy policy

> **Draft for Cora.** Host it as a plain public web page, for example `https://cora.earth/picnic/privacy`. It is written for a Google Play listing, which the app doesn't have yet (docs/RELEASING.md §6): put the URL in Play Console then, and link it from the app's Settings.
>
> Before publishing:
> - Replace every **[FILL IN: …]**.
> - Delete this box and the "Notes for Cora" section at the end.
>
> It describes the app as built on 2026-10-03. Re-check it whenever what the app sends changes. It is not legal advice.

**Effective date:** [FILL IN: date of publication]

Kirakira Basket is a grocery-list app for Android, made by [FILL IN: your name as shown on Google Play] in Ontario, Canada ("I", "me" below). If you have a question about this policy or your data, write to **[FILL IN: contact email]**.

## The short version

- **Your lists live on your phone.** There's no Kirakira Basket account, and I can't see your lists.
- **Nothing leaves your phone until you connect the app to OpenAI** (apart from Android’s own backup of your lists and sticker album, if you have it turned on). You connect it by pasting an OpenAI API key in Settings: your own, or one I set up for you. Without one, the app works as a plain list. (The other exception is a switch that stays off unless you turn it on: **Weather surprises** asks Environment Canada for a city forecast. See below.)
- **Once you're connected,** what you add goes **directly from your phone to OpenAI** so it can be understood, sorted into aisles and given a little sticker: typed requests, photos of handwritten lists, voice notes, item names, and the list of things you keep at home (your pantry).
- **There is no Kirakira Basket server.** Nothing you add passes through a server of mine.
- **Your location is never needed.** If you turn on **Use my area**, the app uses your *approximate* area for its sunset and forecast surprises. Your area stays on this phone; only the forecast request names the nearest city.
- **No ads, no analytics, no tracking, no selling of data.** The app contains no advertising or analytics code.

## What stays on your phone

| What | Why | Backed up by Android? |
|---|---|---|
| Your lists and items, your aisles and store name, the aisle corrections you've taught the app, the icons you picked for items, your favourites, and **your pantry** (the things you keep at home, written on the My pantry screen, with the sticker each one shows) | That's the app | **Yes**, if you've turned on Android backup or move to a new phone. This file and your sticker album (next row) are the only parts of the app that are backed up. Backups are handled by Google under your Google account. |
| Your sticker album: the sticker books of the trips you've finished and the stickers you've collected (item names and their icons) | So your album keeps its pages | **Yes**, together with your lists. |
| Settings: the "Draw new stickers with AI" switch, the Surprises switches (Easter eggs, Sounds, Haptics, "Next stop" card, Celebration, Weather surprises, Use my area), a random installation ID, a masked hint of your key (like `sk-…a1b2`), and whether Android has stopped asking you about location for the app | So the app remembers your setup (and, once Android has stopped asking, offers its settings page instead of a prompt that can't appear) | No |
| With **Use my area** on: your approximate area, rounded to 0.1° (about 10 km), the code of the nearest Environment Canada forecast city, and when it was last checked | So sunset and the forecast fit where you are, without asking the phone more than every 6 hours | No. It is deleted when you turn Use my area off or take away the location permission. |
| With **Weather surprises** on: the last forecast the app read (rain today, snow seen, and which forecast city it was for) and when it was fetched, the first-snow date of this season and the day of the last rain note | So the forecast is asked for at most every 3 hours, and the first snow and the rain note each come once | No |
| Your OpenAI key | To connect | No. It is encrypted with a key kept in your phone's Android Keystore and can't be decrypted on any other device. The app never shows it again. |
| Icons drawn for your items, a count of today's drawings, and any item names the image service refused to draw | So each icon is drawn once, and a refused name isn't retried | No |
| A photo or voice note while it's being processed | To send it | No. It is deleted as soon as it has been read; leftovers from an interrupted session are removed within a day. |

To remove everything from your phone, including what the app has learned, **uninstall the app** or clear its storage (Android Settings → Apps → Kirakira Basket → Storage → Clear storage). Deleting a list removes that list.

## What leaves your phone, and when

Only after you've added an OpenAI key in Settings. From then on, the app sends these directly to OpenAI:

| When you… | What is sent |
|---|---|
| **Ask the AI** in "Write" (for example "ingredients for pancakes") | Your text. Also, to sort the results: your aisle names in walking order, the names of unchecked items on the list (to avoid duplicates), up to 60 of your aisle corrections, the icons you've chosen for item names, and **your pantry** (the first 200 things on its shelves, as you wrote them), so a recipe can leave out what you already have. |
| **Snap or pick a photo** of a handwritten list | The photo, re-saved as a JPEG of at most 1600 pixels, plus the same context as above. **Location tags and other photo metadata are not sent:** the app re-encodes the image without them. |
| **Record a voice note** | The recording (a short audio file), with a hint list so names come out spelled right: the names of items on the list (ticked ones too) and of your aisle corrections, up to 100 names. The transcript then goes on like typed text. |
| **Add items** (including "Add as typed"), **rename** them, or **change your aisles** | Item names and aisle names, so the app can sort items into aisles, choose a basket picture for each aisle and pick an icon for each item. This happens in the background. |
| **Add to your pantry** something the built-in sticker library doesn't recognise | Those pantry names, once each time you save the My pantry screen, so the app can pick a sticker for each. Pantry stickers are never drawn. |
| Have **"Draw new stickers with AI"** on (the default) | For an item with no fitting icon: its name and the app's built-in style sheet image, to draw a small icon. At most 40 automatic drawings a day. You can turn this off in Settings. |
| Tap **Test connection** | Nothing about you: it only asks OpenAI whether the AI model is available. |

**With every request:** your OpenAI key, so OpenAI knows the request is allowed.

**Requests to the language model** also carry a *safety identifier*. It is a one-way scrambled form of a random ID the app makes when it is first used. OpenAI uses it to spot abuse from one installation; it contains nothing about you, your phone or your Google account.

**Your pantry** is stored on your phone and goes with every request to the AI (the text, photo and voice requests above), so the AI can leave out what you keep at home. It is never sent anywhere without a key: without one, the pantry is only a list on your phone.

**Location.** When the AI looks something up on the web, the app tells it a **fixed** approximate location, "Ontario, Canada", so products and units fit. Nothing about your location is ever sent to OpenAI. If the AI used web pages, the app shows links to them; opening one opens your web browser.

## Weather surprises and Use my area (off unless you turn them on)

- **Weather surprises** (Settings → Surprises, off by default) lets two little surprises happen: a rainy-day umbrella and the first-snow sheet. With it on, the app asks **Environment Canada** (`weather.gc.ca`, the Government of Canada's weather service) for one city's public forecast, at most every 3 hours, when you open your list. The request names that city by its published coordinates (the same for everyone near it), never your own location; it carries no key, no installation ID and nothing about you. Without the switch, the app never contacts Environment Canada. It works without an OpenAI key.
- **Use my area** (Settings → Surprises, off by default): Kirakira Basket can use your approximate area to find tonight's sunset and the local forecast for its weather surprises. Your area stays on this phone; only the forecast request names the nearest city. Turning it on asks Android for **approximate location only** (never precise location, and never in the background). The app rounds what Android gives it to 0.1° (about 10 km) before using or keeping it, checks again at most every 6 hours, and keeps only that rounded point and the code of the nearest forecast city. Sunset is worked out on the phone. When the switch is off, or you don't allow location, everything uses Toronto, as it always has.

**Every connection is encrypted** (HTTPS). The app refuses unencrypted connections.

## Who receives it

### OpenAI

The AI features are provided by OpenAI, a company based in the United States. Your phone talks to OpenAI directly; nothing passes through a server of mine.

- **With your own key,** the requests are made under your own OpenAI account. OpenAI's own terms and privacy policy apply between you and OpenAI.
- **With a key I set up for you,** the requests are made under my OpenAI account (a project set up just for your key), and OpenAI processes them on my behalf. OpenAI's dashboard shows me how much the key is used and what it costs, for example requests and tokens per day and model.

According to OpenAI's published API data controls, as of the effective date:
- OpenAI **does not use API data to train its models**.
- The app asks OpenAI **not to store** the answers (`store: false`).
- OpenAI may keep requests, including text, photos and icon drawings, in **abuse-monitoring logs for up to 30 days** before deleting them, unless the law requires longer.
- Voice-transcription requests are not kept in those logs.
- OpenAI may process data in the United States and other countries.

### Nobody else

- I don't sell, rent or share your data with advertisers, data brokers or anyone else.
- I don't use it for advertising or profiling.
- **Google** distributes the app through Google Play and handles Android backups; its own privacy policy covers that. Google Play may show me anonymous, combined statistics such as install counts and crash rates.

## Permissions the app asks for

- **Microphone:** asked only when you first tap the microphone. The app records only while you're on the voice screen, and stops when you leave the app.
- **Internet:** to reach the AI service (and Environment Canada, if you turn on Weather surprises).
- **Approximate location:** asked only when you turn on **Use my area**, after a note saying what it is for. Never precise location, never in the background. You can say no: everything then uses Toronto.

The app has **no camera permission**: photos come from your camera app or Android's photo picker, and the app sees only the photo you choose. It doesn't ask for precise location, contacts, files or anything else.

## How long things are kept

| Data | Kept |
|---|---|
| Lists, pantry, sticker album, settings and drawn icons on your phone | Until you delete them or uninstall the app |
| Your rounded area (Use my area) | Until you turn the switch off, take away the permission, or uninstall the app; refreshed at most every 6 hours |
| Android backup of your lists and sticker album | As long as Google keeps your device backup (your Google account settings) |
| Weather surprises cache | Until you uninstall; the city is forgotten when you turn Use my area off |
| Photos and voice notes on your phone | Deleted right after they're sent; leftovers within a day |
| OpenAI's abuse-monitoring logs | Up to 30 days (no logs for voice transcription) |

## Your choices and rights

- **Use the app without AI.** Don't add a key: nothing leaves your phone, apart from Android’s own backup of your lists and sticker album if you have it turned on.
- **Turn off "Draw new stickers with AI"** in Settings, or **remove your key** there.
- **Leave Weather surprises and Use my area off** (they are off unless you turn them on), or turn them off at any time; turning Use my area off deletes the stored area.
- **Edit or empty your pantry** on the My pantry screen.
- **Delete** items or lists in the app, or uninstall it to remove everything.
- **Ask me** to turn off a key I set up for you, or anything else about this policy: write to [FILL IN: contact email]. I'll answer within 30 days. For anything OpenAI holds under *your own* key, contact OpenAI.
- If you're not satisfied with my answer, you can contact the [Office of the Privacy Commissioner of Canada](https://www.priv.gc.ca/).

## Security

- **Encrypted connections.** Everything sent travels over HTTPS.
- **Your OpenAI key** is encrypted on the phone with an Android Keystore key that never leaves your phone, and it is never included in backups. Other apps can't read it. Anyone who has your unlocked phone can still use the app's AI features, so keys I set up have a monthly spending cap.
- **No built-in secrets.** The app contains no passwords or keys of its own.

## Children

Kirakira Basket is meant for adults. It isn't directed at children, and I don't knowingly collect anything from children. If you think a child has used it with a key I set up, write to me and I'll turn that key off.

## Changes to this policy

If what the app collects or sends changes, I'll update this page and its effective date before the change reaches the app, and mention it in the release notes.

## Contact

[FILL IN: your name], [FILL IN: contact email], Ontario, Canada.

---

## Notes for Cora (delete before publishing)

**What to fill in:**
- the effective date;
- your name as shown on Google Play;
- the contact email (it can be the Play listing's support address).

**Data safety notes** (for the Play Console form, if the app is ever listed):
- *Data collected:* none by the developer. Everything the app sends goes from the phone straight to OpenAI (with the user's key) or, with Weather surprises on, to Environment Canada; no server of the developer's receives anything.
- *Data shared:* with a key set, user content (text, photos, audio, item names, the pantry) goes to OpenAI to provide the app's features ("App functionality"). With Weather surprises on, one forecast request to Environment Canada names a city, never the user's location or an identifier. Approximate location (Use my area) is used on the device only: the forecast request names the nearest forecast city by that city's published coordinates (the same for everyone near it), never the user's own location or an identifier.
- *Location permission:* ACCESS_COARSE_LOCATION only, requested only when the user turns on Use my area, after the in-app note; optional.
- *Encryption in transit:* yes (HTTPS only). *Deletion:* clearing storage or uninstalling deletes everything; turning Use my area off deletes the stored area.

**Where each statement comes from** (re-check these when the app changes):

| Statement | Source in the repository |
|---|---|
| On-device files and the backup allow-list | `app/src/main/res/xml/data_extraction_rules.xml`, `backup_rules.xml` (only `state.json` and `album.json`); docs/ARCHITECTURE.md §6.1 |
| Token encryption | `settings/TokenStore.kt` (`noBackupFilesDir/token.bin`), `platform/TokenCipher.kt` |
| Settings and the install ID; the safety identifier is a hash of it | `settings/DataStoreSettingsRepository.kt` (`llmConfig()`); core `OpenAiClient.responses()` adds `safety_identifier` to Responses calls only |
| Requests go only to OpenAI | core `LlmConfig.DEFAULT_BASE_URL` (`https://api.openai.com/v1`); the app has no address setting (`DataStoreSettingsRepository.llmConfig()`) |
| What Cora sees for a key she set up | OpenAI's usage dashboard for that project; setup in docs/OPENAI_KEY.md |
| What a request contains | core `StateOps.ingestContext()` (aisles, ≤ 60 corrections, unchecked names, icon overrides, the first 200 pantry entries); `ui/add/AddViewModel.keywords()` (transcription hints); `assistant/OpenAiGroceryAssistant.kt`, `IconDecider.kt`; `icons/AppIconRepository.kt` (drawing, daily cap 40) |
| Photos lose their metadata; capture and voice files are deleted | `platform/Captures.kt` (`toJpeg`: decode, then `Bitmap.compress`; `sweepStale` after 24 h); `AddViewModel` deletes each file after reading it |
| Fixed web-search location | core `llm/ModelDefaults.kt` (`WEB_SEARCH`: CA / Ontario / America/Toronto) |
| `store: false`; no request bodies in logs | core `OpenAiClient.kt` (`LlmLog`: method, path, status, request id, latency); app `platform/PicnicLog.kt` |
| Permissions | `app/src/main/AndroidManifest.xml`. Merged manifest checked 2026-10-03: INTERNET, RECORD_AUDIO, ACCESS_COARSE_LOCATION and androidx's internal receiver permission only; no ACCESS_FINE_LOCATION, no ACCESS_BACKGROUND_LOCATION, no AD_ID (`ManifestPermissionsTest`) |
| The pantry | core `model/Pantry.kt` (`PANTRY_IN_CONTEXT` = 200, stored in state.json); `ui/pantry/PantryIcons.kt` (stickers: learned override, then the local library, then one IconDecider call per save, only with a key; never drawn) |
| Use my area | `platform/PlaceSource.kt` (`AreaPlaceSource`: switch and permission checked on every use; `EcSites.round` to 0.1° before use or storage; 6 h refresh; Toronto fallback; the `place` DataStore keeps only `place_lat`, `place_lon`, `place_city`, `place_at` and is emptied when off); `platform/EcSites.kt` (the bundled forecast cities); the rationale text in `res/values/strings_screens.xml` (`settings_area_rationale`) |
| Weather surprises | core `weather/` and `platform/WeatherCache.kt` (WP9): the Environment Canada feed of the place's city, at most every 3 h, only with the switch on |
| OpenAI retention | [OpenAI: data controls in the OpenAI platform](https://developers.openai.com/api/docs/guides/your-data): no training on API data; abuse monitoring up to 30 days for `/v1/responses` and `/v1/images/edits`, none for `/v1/audio/transcriptions` |
