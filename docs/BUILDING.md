# Building and installing Picnic List

This page covers getting the APK onto a phone, building it yourself, and the tests and CI behind it.
The design itself is in [ARCHITECTURE.md](ARCHITECTURE.md); making an OpenAI key for someone is in
[OPENAI_KEY.md](OPENAI_KEY.md).

## 1. Get the APK

Every push builds the sideloadable APK (`.github/workflows/android.yml`): the `sideload` build type,
which is the debug build's code, signed with the app's private key (registered for `earth.cora.picnic`
in Android Developer Console, see [RELEASING.md](RELEASING.md)), and **not debuggable**, so nobody
with brief USB access can read the app's stored OpenAI key with `adb shell run-as` or a debugger.
Pick whichever is easiest:

| Where | How | Who can get it |
|---|---|---|
| **GitHub Releases** (recommended for the phone) | Push a tag such as `v0.2.0`. The release page gets `picnic-list-0.2.0.apk` attached. Open the page on the phone and tap the APK. | Anyone who can see the repository |
| **Actions artifact** (any branch, any commit) | Repository → **Actions** → **Android** → pick a run → **Artifacts** → `picnic-list-<version>.apk`. It downloads as a bare `.apk` (not a zip), so it can be fetched on the phone directly. Artifacts expire after 90 days. | Signed-in GitHub users with access |
| **GitHub CLI** | `gh run download <run-id> --name picnic-list-<version>.apk` | Same as above |

The version name is `0.1.0-<commit>` for branch builds and the tag without the `v` for releases. The
version code is the CI run number, so every CI build is newer than the one before.

## 2. Install it on the phone (sideloading)

1. Open the downloaded APK (from the browser's downloads or the Files app).
2. Android asks to allow installs from that app: **Settings → Allow from this source**, then go back.
3. Play Protect may warn about an unknown developer: **More details → Install anyway**.

Testers' phones also need to be authorized in Android Developer Console (up to 20). Today that only
matters once Google's enforcement reaches Canada (RELEASING.md §2.4, §4).

**Updates install over the previous version and keep the lists.** Every CI APK is signed with the same
private key, and CI version codes keep increasing.

**Local debug builds don't mix with CI APKs.** They are signed with the committed debug key
(`app/debug.keystore`), and Android only updates an app with the same key it was installed with. On
your own phone, either stay with debug builds (`adb install -r -d …/picnic-list-debug.apk`; `-d`
allows the downgrade, which Android permits only for debuggable apps) or with CI APKs. Switching means
uninstalling. A local build signed with the private key installs over CI APKs (RELEASING.md §5.2).

If Android ever says "App not installed" or "package conflicts with an existing package", the
installed copy was signed with a different key (a debug build, or a CI APK from before 2026-10-02,
which used the debug key). Uninstall it once, then install again. Uninstalling deletes the lists on
the phone.

### First run

Open **Settings** in the app:

- **Your OpenAI key.** Paste an OpenAI API key (your own, or one Cora set up for you following
  [OPENAI_KEY.md](OPENAI_KEY.md)) and tap **Save**; **Test connection** runs by itself. The app talks to
  `https://api.openai.com/v1` directly; there is no address to set. The key is encrypted on the phone
  with the Android Keystore, it is never shown again (only a hint like `sk-…a1b2`), and it is never
  included in backups.
- **Draw new icons with AI** (on by default). Items without a fitting icon get one drawn live: about
  10 s and about 1.5¢ (US) each, at most 40 automatic drawings a day.

There are no model settings. The models are fixed by the developers in core's `ModelDefaults`
(see ARCHITECTURE §7.1); changing one means shipping a new APK.

## 3. Build it yourself

### Requirements

- **JDK 21.** Nothing needs JDK 17: bytecode 17 is produced by JDK 21.
- **Android SDK** with `platforms;android-37.0` (compileSdk 37, needed by Compose BOM 2026.09),
  `build-tools;36.0.0` (AGP 9.4.1's default) and `platform-tools`:
  ```bash
  sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
  ```
- Point the build at the SDK with `ANDROID_HOME`, or a `local.properties` file at the repository root
  (it is gitignored):
  ```properties
  sdk.dir=/path/to/android-sdk
  ```

Always use `./gradlew` (Gradle 9.8.0 with a pinned distribution checksum). A system Gradle 8.x
cannot evaluate AGP 9 builds, and never run `gradle wrapper` in this repository.

### Commands

| What | Command |
|---|---|
| Debug APK (debuggable, for development) | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/picnic-list-debug.apk` |
| Sideload APK (what testers install; not debuggable; signed with the private key when the `PICNIC_SIGNING_*` settings are set, see RELEASING.md §5.2, else with the debug key) | `./gradlew :app:assembleSideload` → `app/build/outputs/apk/sideload/picnic-list-sideload.apk` |
| Install on a connected phone | `./gradlew :app:installDebug` (or `adb install -r -d <apk>`) |
| core tests (core is a standalone build) | `./gradlew -p core test` |
| core live tests (real API, costs a few cents) | `OPENAI_API_KEY=… ./gradlew -p core test -Plive=true` |
| core CLI for prompt work | `./gradlew -p core run --args="ingest 'ingredients for pancakes from scratch'"` |
| App unit tests (incl. Robolectric) | `./gradlew :app:testDebugUnitTest` |
| Record screenshot goldens | `./gradlew :app:recordRoborazziDebug` |
| Compare with goldens (never fails) | `./gradlew :app:compareRoborazziDebug` → diffs in `app/build/outputs/roborazzi/` |
| Verify against goldens (fails on a diff) | `./gradlew :app:verifyRoborazziDebug` |
| Lint (CI gate) | `./gradlew :app:lintDebug` |
| R8 release APK (not shipped today; signed when the `PICNIC_SIGNING_*` settings are set, unsigned otherwise) | `./gradlew :app:assembleRelease` |

Pass `-Ppicnic.versionCode=<n> -Ppicnic.versionName=<name>` to stamp a version (CI does this).
Never put an API key in a Gradle file or `gradle.properties`: the CLI and live tests read
`OPENAI_API_KEY` from the environment only, and the configuration cache would write a key passed
through Gradle to disk.

## 4. Screenshot tests (art passes start here)

The UI is rendered on the JVM with **Roborazzi 1.76 + Robolectric 4.17 native graphics**, so changes
can be reviewed without an emulator. Goldens are committed in `app/src/test/screenshots/`:

- `earth.cora.picnic.ui.theme.*Preview*.png`: every `@Preview` in the app is captured automatically
  (Roborazzi's preview scanner). The theme kit has one preview per component family in day ("picnic
  at noon") and dusk ("picnic at dusk"): controls, decor, item icons, baskets, list pieces, chrome.
- `<screen>_<state>_<light|dark>.png`: explicit screen tests (see ARCHITECTURE §14).

After an intentional visual change, run `recordRoborazziDebug` and commit the new PNGs. CI runs
`compareRoborazziDebug` and uploads the diff images with the reports; it does not fail on a diff
(moving CI to `verifyRoborazziDebug`, DESIGN_STICKER item 305, was retired on 4 October).

Gotchas (already configured in `app/build.gradle.kts` and `app/src/test/resources/robolectric.properties`):

- Robolectric runs at **SDK 36**: at 37, Compose/Espresso input injection crashes.
- Robolectric 4.17 needs `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED`.
- Fonts must be bundled files in `res/font`; downloadable fonts don't render in Robolectric.
- Robolectric downloads its `android-all` jars from Maven Central on first use. If that host rate
  limits you, point it at a mirror with a **local** init script (never commit it):
  `systemProperty 'robolectric.dependency.repo.url', '<mirror>'` on all `Test` tasks, passed with `-I`.

## 5. CI and releases

`.github/workflows/android.yml` runs on pushes to any branch, `v*` tags, pull requests and manual
dispatch, on `ubuntu-24.04` with JDK 21. Three jobs, each on its own runner:

1. **`build`** (no secrets): `./gradlew -p core test`, then
   `./gradlew :app:compareRoborazziDebug :app:lintDebug :app:assembleDebug`, and uploads the test,
   lint and screenshot reports.
2. **`apk`** (the signing key from repository secrets, RELEASING.md §3): builds
   `:app:assembleSideload -Ppicnic.versionCode=<run> -Ppicnic.versionName=<ver>`, checks that it is
   signed with exactly the registered certificate, and uploads the bare APK as
   `picnic-list-<ver>.apk` (`archive: false`). Dependabot's runs skip it (they get no secrets).
3. **`release`**, on a `v*` tag: attaches the APK to a GitHub Release.

To cut a release: `git tag v0.2.0 && git push origin v0.2.0`.

Live API tests never run in CI.

## 6. Signing: the private key and the debug key

- **What testers install** (CI's sideload APK) is signed with the app's **private key**, registered for
  `earth.cora.picnic` in Android Developer Console (certificate SHA-256
  `A4:B1:24:97:04:CB:2C:77:3F:D7:36:19:08:B8:47:BD:27:0B:AD:B8:50:D8:0A:E0:F7:82:79:17:35:16:09:A5`).
  It lives outside the repository and can't be replaced without losing updates for everyone. Where it
  is, its secrets and its registration: [RELEASING.md](RELEASING.md).
- **Debug builds**, and sideload builds made without the `PICNIC_SIGNING_*` settings, are signed with
  the **committed** `app/debug.keystore` (alias `androiddebugkey`, passwords `android`; certificate
  SHA-256 `69:7c:19:5b:83:2c:38:83:6e:d9:76:13:54:27:e4:5d:8f:c9:9d:23:b0:49:db:74:b2:54:fe:ee:8b:a2:83:8c`).
  Never regenerate or replace it: your own debug installs could no longer be updated in place.
- Anyone with the repository can sign an "update" with the debug key. That is why testers get the
  private key's builds, and why the debug key must **never** be registered in Android Developer Console.

## 7. What the APK contains and asks for

- Permissions: `INTERNET` and `RECORD_AUDIO` (asked at runtime, only when you tap the mic). No camera
  permission: photos go through the system camera app or the photo picker.
- Backups: only the lists (`files/state.json`). Settings, the encrypted OpenAI key and drawn icons are
  not backed up.
- No API keys are ever in the repository or the APK.

## 8. Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=…`. |
| `Unsupported class file major version` / AGP errors with plain `gradle` | Use `./gradlew`; the build needs Gradle 9.8 and JDK 21. |
| Robolectric: `Failed to interact with raw FileDescriptor internals` | The `--add-opens` flag is missing (it is set for `testDebugUnitTest`; custom test tasks need it too). |
| Robolectric download errors (HTTP 429) | Use a local mirror init script, section 4. |
| Screenshot diffs after an upgrade with no UI change | Anti-aliasing noise: re-record, or add a small `changeThreshold` to the compare options. |
| "App not installed" on the phone | Different signing key (debug build vs CI APK): uninstall once, then install (section 2). |
