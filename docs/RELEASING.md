# Releasing Picnic List to testers

Picnic List isn't on Google Play. Testers install the APK that CI builds (docs/BUILDING.md), signed with a private key that Android developer verification ties to Cora through a free **limited distribution account** in [Android Developer Console](https://developer.android.com/developer-verification/guides/limited-distribution):

- up to 20 devices, each authorized by its owner;
- no fee and no government ID;
- nothing public.

Researched 2026-10-02; the console is new and changes, so the [sources](#8-sources) are linked at the end.

**The order, at a glance:**

| Step | Once or every time | Section |
|---|---|---|
| Make the signing key and back it up | once (done 2026-10-02) | [1](#1-the-signing-key) |
| Register `earth.cora.picnic` with that key | once (done 2026-10-02) | [2](#2-android-developer-console) |
| GitHub secrets | once (done 2026-10-02) | [3](#3-github-secrets) |
| Authorize each tester's phone | once per phone | [4](#4-testers-phones) |
| Build and send an APK | every release | [5](#5-releases) |

---

## 1. The signing key

It signs every APK testers install, and it is the key registered for `earth.cora.picnic`. **It can't be reset or recovered.**
- Android only installs an update signed with the same key as the installed copy.
- Google can't recover a lost key, and without it the package can't be registered again.

Lose it, and installed copies can never be updated.

| | |
|---|---|
| Keystore | `~/keys/picnic/picnic-upload.jks` on Cora's Mac: PKCS12, alias `upload`, RSA 4096, valid until 2054 |
| Password | macOS login Keychain, item "Picnic List upload keystore" (account `upload`). A PKCS12 keystore has one password: the key password is the store password. |
| Certificate | `~/keys/picnic/upload_certificate.pem` |
| SHA-256 | `A4:B1:24:97:04:CB:2C:77:3F:D7:36:19:08:B8:47:BD:27:0B:AD:B8:50:D8:0A:E0:F7:82:79:17:35:16:09:A5` |
| Backups | the password manager, with the `.jks` attached next to its password. An offline encrypted copy (for example `age -p picnic-upload.jks > picnic-upload.jks.age` on a USB stick) is a good second. |

"upload" in the file and alias names is left over from an earlier plan for Google Play, where the key you hold is called the *upload key*. Here it is the app's real signing key. Renaming the alias would only make the copies differ, so it stays.

**Never commit it.** `.gitignore` ignores `*.jks` and `*.keystore`; the only exception is the public `app/debug.keystore`.

How it was made, for reference. `keytool` comes with any JDK: `brew install openjdk@21` puts it in `/opt/homebrew/opt/openjdk@21/bin`, and without a JDK, macOS's `/usr/bin/keytool` is only a stub.

```bash
mkdir -p "$HOME/keys/picnic" && chmod 700 "$HOME/keys/picnic" && cd "$HOME/keys/picnic"
keytool -genkeypair -v \
  -keystore picnic-upload.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Picnic List upload key, O=cora.earth, C=CA"
keytool -list -v -keystore picnic-upload.jks -alias upload | grep SHA256
keytool -exportcert -rfc -keystore picnic-upload.jks -alias upload -file upload_certificate.pem
```

## 2. Android Developer Console

- **2.1 The account.** A limited distribution account needs:
  - a Google Account with 2-Step Verification;
  - a Google payments profile, which holds your legal name and address and isn't shown publicly;
  - a contact email, which Google uses only to reach you.

  It shares apps with up to 20 devices whose owners explicitly authorize them.
- **2.2 The package name** (done 2026-10-02). `earth.cora.picnic` is registered under the friendly name "Kirakira Basket", with the SHA-256 from §1. The steps, on the **Package names** page:
  1. Enter the package name.
  2. Add the key's SHA-256.
  3. Prove you own the key, only if Google has already seen the name installed somewhere. Put the snippet the console gives you in `app/src/main/assets/adi-registration.properties` (exactly that name), build a release APK signed with the key (§5.2), and upload it. `earth.cora.picnic` was new, so it needed no proof.
- **2.3 More keys** go on the package's page with **Add key**. **Never add the committed debug key** (SHA-256 `69:7C:19:5B:…:83:8C`, docs/BUILDING.md §6): its private key is in the repository, so registering it would let anyone sign apps that verify as yours.
- **2.4 When it matters.** As of 2026-10-02, Google enforces registration only:
  - in Brazil, Indonesia, Singapore and Thailand;
  - for installs from participating app stores.

  Google says sideloaded apps aren't affected yet. It plans to expand worldwide in 2027. ADB installs never need registration. So today the APK installs on any phone in Canada, and because the package is already registered, nothing changes for testers when enforcement arrives.

## 3. GitHub secrets

The repository is private on GitHub Free, so the key goes in **repository secrets**, not an environment. Free doesn't offer the environment branch rule for private repositories, so any workflow on any branch can read these secrets. That's fine while only Cora pushes. Before giving anyone else write access, move them to an environment limited to `main` and `v*` tags (needs GitHub Pro).

| Secret | Value |
|---|---|
| `PICNIC_SIGNING_KEYSTORE_BASE64` | `picnic-upload.jks`, base64-encoded |
| `PICNIC_SIGNING_STORE_PASSWORD` | the keystore password |

**The key alias is not a secret.** The workflow uses `upload`. For a key with another alias, set the **repository variable** `PICNIC_SIGNING_KEY_ALIAS` (`gh variable set PICNIC_SIGNING_KEY_ALIAS --body <alias>`). Don't make the alias a secret: GitHub masks a secret's value everywhere in the log, so a common word like `upload` would become `***` all through it.

Setting them (done 2026-10-02), from `$HOME/keys/picnic` so nothing passes through the checkout:

```bash
repo=corashallit/picniccore-shopping-list
cd "$HOME/keys/picnic"
base64 < picnic-upload.jks | tr -d '\n' | gh secret set PICNIC_SIGNING_KEYSTORE_BASE64 -R "$repo"
printf '%s' "$(security find-generic-password -s 'Picnic List upload keystore' -a upload -w)" \
  | gh secret set PICNIC_SIGNING_STORE_PASSWORD -R "$repo"
gh secret list -R "$repo"
```

**What `android.yml` does with them.** The tests run in the `build` job, which has no secrets. The key only reaches the `apk` job, on its own fresh runner, which:

1. stops at once, naming any missing secret;
2. decodes the key into the runner's temp directory and opens it with keytool. A wrong password, alias or base64 fails here. So does a key whose certificate isn't the registered one, which is pinned as `REGISTERED_CERT_SHA256` at the top of the workflow;
3. builds the signed APK with `./gradlew --no-daemon --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process :app:assembleSideload`. No Gradle or Kotlin daemon outlives the step with the password in its environment, and no configuration cache entry holds it. Its Gradle cache is read-only;
4. deletes the key, then checks with `apksigner` that the APK has exactly one signer, the registered certificate. A build that fell back to the debug key fails here.

Every action in the workflow is pinned to a full commit SHA, so a moved tag can't change what runs next to the key. Dependabot (`.github/dependabot.yml`) opens a weekly pull request to update them. Dependabot's own runs don't get Actions secrets, so they skip `apk`.

## 4. Testers' phones

- **Authorize each phone** in Android Developer Console, up to 20. Google describes a handshake: you share a QR code or link, the phone's owner consents on the phone, and the device is registered in the console. The exact screens weren't documented when this was written, so follow the console.
- **Install** as in docs/BUILDING.md §2.
- **A phone with an older build signed by the debug key** (any CI APK from before 2026-10-02, or a local debug build) has to **uninstall it first**: Android won't update across signing keys. Uninstalling deletes the lists on that phone.

## 5. Releases

- **5.1 From CI.** Every push builds a signed APK as an Actions artifact, and a `v*` tag also attaches it to a GitHub Release (docs/BUILDING.md §1 and §5):

  ```bash
  git tag v0.2.0 && git push origin v0.2.0
  ```

  The repository is private, so testers can't open its releases. Download the APK and send it to them yourself.
- **5.2 Locally.** For the ownership-proof APK (§2.2), or a build that must install over CI ones:

  ```bash
  PICNIC_SIGNING_STORE_PASSWORD="$(security find-generic-password -s 'Picnic List upload keystore' -a upload -w)"
  export PICNIC_SIGNING_STORE_PASSWORD
  export PICNIC_SIGNING_STORE_FILE="$HOME/keys/picnic/picnic-upload.jks" PICNIC_SIGNING_KEY_ALIAS=upload
  ./gradlew --no-configuration-cache :app:assembleSideload -Ppicnic.versionCode=<n> -Ppicnic.versionName=<name>
  unset PICNIC_SIGNING_STORE_PASSWORD
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
    app/build/outputs/apk/sideload/picnic-list-sideload.apk | grep SHA-256
  ```

  - The printed digest must be the SHA-256 from §1 (lower case, no colons).
  - CI's version code is the run number. A non-debuggable APK only installs over an older code, so pick `<n>` above the latest run.
  - Use `--no-configuration-cache` for signed builds. Gradle encrypts cache entries, but the password would still be written to disk.
- **5.3 The signing settings.** `app/build.gradle.kts` reads each one from the environment or from a Gradle property, for example in `~/.gradle/gradle.properties`. Never put them in the repository's `gradle.properties`.

  | Environment variable | Gradle property |
  |---|---|
  | `PICNIC_SIGNING_STORE_FILE` | `picnic.signing.storeFile` (relative paths resolve against the repository root) |
  | `PICNIC_SIGNING_STORE_PASSWORD` | `picnic.signing.storePassword` |
  | `PICNIC_SIGNING_KEY_ALIAS` | `picnic.signing.keyAlias` |
  | `PICNIC_SIGNING_KEY_PASSWORD` | `picnic.signing.keyPassword` (optional; defaults to the store password) |

  - **All set:** `sideload` and `release` builds are signed with the private key.
  - **None set:** `sideload` falls back to the debug key, and `release` is unsigned.
  - **Some set, or a missing store file:** the build stops and names what's missing.

## 6. Moving to Google Play later

Publishing on Play costs US$25 once and needs a verified identity with a public developer name. Until 2026-10-02 this repository carried a full, researched Play setup, removed when the app went with limited distribution. It's still in git history:
- the checklist covers Play App Signing, the Play Console forms, Data safety answers, the content rating and the service account;
- the workflow tests, signs and uploads to a Play track.

```bash
git show 7be9b87:docs/RELEASING.md
git show 7be9b87:.github/workflows/play-release.yml
```

If it ever happens:
- choose **use your own app signing key** in Play App Signing and give Play *this* key, so Play installs can update the copies testers already have;
- `docs/PRIVACY.md` is the privacy policy draft Play would need.

## 7. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Workflow: "Missing repository secrets: …" | Add them (§3). |
| Workflow: "Couldn't open the signing key (keytool error: … password was incorrect / Alias <x> does not exist)" | Wrong `PICNIC_SIGNING_STORE_PASSWORD`, or the key's alias isn't `upload`: set the variable `PICNIC_SIGNING_KEY_ALIAS` (§3). |
| Workflow: "… is not valid base64" | Set `PICNIC_SIGNING_KEYSTORE_BASE64` again with the command in §3. |
| Workflow: "The signing key's certificate is …, not the registered …" | The secret holds a different key. Upload `~/keys/picnic/picnic-upload.jks` again (§3). If the registered key really changed, register the new one (§2.3), then update `REGISTERED_CERT_SHA256` in `android.yml`. |
| Workflow: "The APK is not signed with exactly the registered key" | The Gradle step didn't see the `PICNIC_SIGNING_*` settings and fell back to the debug key. |
| Gradle: "Signing is only partly configured" | Set all of store file, store password and alias, or none (§5.3). |
| Phone: "App not installed" / "package conflicts with an existing package" | The installed copy has a different signing key (a debug build, or a CI APK from before 2026-10-02). Uninstall it once, which deletes its lists, then install. |
| Phone: install refused, or an "unverified developer" warning (once enforcement reaches Canada) | The phone isn't authorized (§4), or the APK isn't signed with the registered key. |

## 8. Sources

All fetched 2026-10-02.

- Android developer verification: [overview of guides](https://developer.android.com/developer-verification/guides), [Register on Android Developer Console](https://developer.android.com/developer-verification/guides/android-developer-console), [Limited distribution](https://developer.android.com/developer-verification/guides/limited-distribution), [FAQ](https://developer.android.com/developer-verification/guides/faq)
- Android Developer Console Help: [Registering Android package names](https://support.google.com/android-developer-console/answer/16640821), [Adding additional keys](https://support.google.com/android-developer-console/answer/16641418)
- GitHub: [Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments) (which plans have environment secrets for private repositories)
