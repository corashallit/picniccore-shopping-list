# Releasing Picnic List on Google Play

This is the checklist for publishing Picnic List (`earth.cora.picnic`) to **Google Play internal testing** from Cora's personal developer account, first by hand and then from GitHub Actions. Sideloading (docs/BUILDING.md) doesn't change: those APKs keep the committed debug key and the `android.yml` workflow.

Researched 2026-10-01; Play Console moves things around, so the [sources](#13-sources) are linked at the end. The privacy policy draft is [PRIVACY.md](PRIVACY.md).

**The order, at a glance:**

| Step | Once or every time | Section |
|---|---|---|
| Make the upload key and back it up | once | [1](#1-the-upload-key) |
| Create the app in Play Console | once | [2](#2-create-the-app-in-play-console) |
| Google Cloud service account, invited in Play Console | once | [3](#3-google-play-developer-api-access-service-account) |
| GitHub environment and secrets | once | [4](#4-github-secrets) |
| First bundle, uploaded **by hand** | once | [5](#5-the-first-release-by-hand) |
| Internal testers | once, then as needed | [6](#6-internal-testers) |
| Releases from GitHub Actions | every release | [7](#7-releases-from-github-actions) |
| App content, store listing, closed test (before anyone outside internal testing) | once, kept up to date | [8](#8-before-closed-testing-or-production), [9](#9-store-listing-assets) |

---

## 1. The upload key

Play uses two keys (Play App Signing). **You** keep the *upload key* and sign every bundle with it. **Google** keeps the *app signing key* and re-signs what it delivers to phones. A lost or leaked upload key can be reset (§1.4), but a reset takes a support request, so treat it as a real secret. Play requires an RSA key of at least 2048 bits.

- [ ] **1.1 Generate it** on your own machine, not in CI. `keytool` ships with any JDK (Android Studio's included).

  ```bash
  mkdir -p "$HOME/keys/picnic" && chmod 700 "$HOME/keys/picnic" && cd "$HOME/keys/picnic"
  keytool -genkeypair -v \
    -keystore picnic-upload.jks -storetype PKCS12 \
    -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=Picnic List upload key, O=cora.earth, C=CA"
  ```

  keytool prompts for the password, so it never lands in your shell history. Use a long random one from your password manager. A PKCS12 keystore (keytool's default) has **one** password: the key password is the store password. `-validity 10000` is about 27 years.

- [ ] **1.2 Record the fingerprint and export the certificate.** You need the PEM for an upload key reset, and the fingerprint to check builds.

  ```bash
  keytool -list -v -keystore picnic-upload.jks -alias upload | grep SHA256
  keytool -export -rfc -keystore picnic-upload.jks -alias upload -file upload_certificate.pem
  ```

- [ ] **1.3 Back it up in two places:**
  - the password manager, with the `.jks` file attached next to its password;
  - an offline encrypted copy, for example `age -p picnic-upload.jks > picnic-upload.jks.age` on a USB stick.

  **Never commit it.** `.gitignore` already ignores `*.jks` and `*.keystore`; the only exception is `app/debug.keystore`.
- **1.4 If it is ever lost or leaked:**
  1. Make a new key (1.1) and its PEM (1.2).
  2. In Play Console, open the app's **Play app signing** page (under **Protected with Play**) and choose **Request upload key reset**.
  3. Upload the PEM.
  4. After Google confirms, update the GitHub secrets (§4).

  Installed copies are unaffected, because phones only ever see Google's app signing key.

## 2. Create the app in Play Console

- [ ] **2.1** Go to [Play Console](https://play.google.com/console), then **Home → Create app**:
  - **App name:** Picnic List (30 characters at most; can change later).
  - **Default language:** English (Canada) or English (United States).
  - **App or game:** App.
  - **Free or paid:** Free. A free app can never become paid.
  - **Contact email:** the address shown on the store listing.
  - **Declarations:** accept the Developer Program Policies and US export laws, and the **Play App Signing Terms of Service**.
- [ ] **2.2** Leave Play App Signing at its default, a Google-generated key. New apps are enrolled in Google's quantum-ready hybrid signing automatically. You can only switch to your own app signing key before a release reaches open testing or production, and there is no reason to.
- [ ] **2.3** Note the rules for a **personal account created after 13 November 2023**:
  - **Internal testing:** up to 100 testers. It works before the app setup is complete; the first upload shows testers a temporary name for up to 48 hours.
  - **Closed testing:** needs the app setup complete (§8).
  - **Production** (and open testing) stays locked until a **closed test has had at least 12 testers opted in continuously for 14 days**. Then you apply on the Dashboard by answering three sets of questions (your closed test, the app, its readiness). The review usually takes 7 days or less.

## 3. Google Play Developer API access (service account)

The workflow publishes through the Google Play Developer API as a service account. Play Console no longer needs a linked Cloud project; the account only has to be invited in **Users and permissions**.

- [ ] **3.1 Create a Google Cloud project** with the same Google account, for example `picnic-play`, at [console.cloud.google.com](https://console.cloud.google.com/projectcreate).
- [ ] **3.2 Enable the Google Play Android Developer API** (`androidpublisher.googleapis.com`): **APIs & Services → Library → "Google Play Android Developer API" → Enable**.
- [ ] **3.3 Create the service account:** **IAM & Admin → Service accounts → Create service account**.
  - **Name:** `picnic-play-publisher`.
  - **Roles:** none. Its permissions live in Play Console, not in Cloud IAM.
- [ ] **3.4 Create a JSON key:** open the service account → **Keys → Add key → Create new key → JSON**. The file downloads once. Move it to `$HOME/keys/picnic/`, next to the upload key and **outside the repository checkout**, so a `git add -A` can never pick it up. You upload it to GitHub in §4 and then delete it.

  The gcloud equivalent of 3.2–3.4:

  ```bash
  gcloud services enable androidpublisher.googleapis.com --project picnic-play
  gcloud iam service-accounts create picnic-play-publisher --project picnic-play \
    --display-name "Picnic List Play publisher"
  gcloud iam service-accounts keys create "$HOME/keys/picnic/picnic-play-publisher.json" \
    --iam-account picnic-play-publisher@picnic-play.iam.gserviceaccount.com
  ```

  > **"Service account key creation is disabled"?** That is the org policy `iam.disableServiceAccountKeyCreation`. Google enforces it by default on every Google Cloud *organization* created on or after 3 May 2024, and a Google Workspace or Cloud Identity domain (for example cora.earth) brings an organization with it. You have three ways out:
  > - create the project under a plain personal Google account with no organization;
  > - exempt just this project from the policy;
  > - skip keys and use Workload Identity Federation (§7.4).

- [ ] **3.5 Invite it in Play Console:** **Users and permissions → Invite new users**.
  - **Email address:** the service account's email (the `client_email` in the JSON).
  - **App permissions → Add app → Picnic List**, with **Release apps to testing tracks** ticked. That is enough for internal, closed and open testing. Add **Release to production, exclude devices, and use Play App Signing** later, only if CI should ever push production.
  - **Account permissions:** leave them all off.
  - **Invite user**. A service account needs no acceptance.

  New permissions can take a while to apply. If the first upload fails with "The caller does not have permission", wait and re-run.

## 4. GitHub secrets

The secrets go in a GitHub **environment** called `play`. The release workflow's `build` and `upload` jobs use that environment, and its **deployment branches and tags** rule lets only `main` and `play-v*` tags read the secrets. Repository secrets, by contrast, can be read by any workflow on any branch: an edited `android.yml` on a pushed branch, or `gh workflow run play-release.yml --ref <branch>`, which would run that branch's Gradle build with the key.

> **Which GitHub plan?** The repository is private. For a private repository on a personal account, environment secrets and the branch rule need **GitHub Pro**. On **GitHub Free**, put the same names in **repository secrets** instead (leave out `--env play` below). The workflow reads either. It also refuses to run from anything but `main` or a `play-v*` tag, but a pushed branch could edit that check, so this only protects against accidents. Required reviewers (a manual approval before each release) are only available on private repositories with GitHub Enterprise.

| Secret | Value | Required |
|---|---|---|
| `PICNIC_UPLOAD_KEYSTORE_BASE64` | `picnic-upload.jks`, base64-encoded | yes |
| `PICNIC_UPLOAD_STORE_PASSWORD` | the keystore password | yes |
| `PICNIC_UPLOAD_KEY_PASSWORD` | only for an old JKS keystore with a separate key password; leave unset for PKCS12 | no |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole service account JSON file | yes, unless a run only builds (§5) |

**The key alias is not a secret.** The workflow uses `upload` (§1.1). If you made the key with a different `-alias`, set the **repository variable** `PICNIC_UPLOAD_KEY_ALIAS` (`gh variable set PICNIC_UPLOAD_KEY_ALIAS --body <alias>`). Don't make the alias a secret: GitHub masks a secret's value everywhere in the log, so a common word like `upload` would become `***` all through it. It also drops any job output that contains a secret's value, which could empty the bundle's artifact name.

1. **Create the environment.** In the GitHub UI: **Settings → Environments → New environment** `play`, then **Deployment branches and tags → Selected branches and tags**, and add the branch rule `main` and the tag rule `play-v*`. With the CLI:

   ```bash
   repo=corashallit/picniccore-shopping-list
   gh api -X PUT "repos/$repo/environments/play" \
     -F 'deployment_branch_policy[protected_branches]=false' \
     -F 'deployment_branch_policy[custom_branch_policies]=true'
   gh api -X POST "repos/$repo/environments/play/deployment-branch-policies" -f name=main -f type=branch
   gh api -X POST "repos/$repo/environments/play/deployment-branch-policies" -f name='play-v*' -f type=tag
   ```

2. **Add the secrets** from `$HOME/keys/picnic`, not from the repository checkout. `-R` names the repository, so the JSON key never has to sit inside the checkout:

   ```bash
   cd "$HOME/keys/picnic"
   repo=corashallit/picniccore-shopping-list
   base64 < picnic-upload.jks | tr -d '\n' | gh secret set PICNIC_UPLOAD_KEYSTORE_BASE64 --env play -R "$repo"
   gh secret set PICNIC_UPLOAD_STORE_PASSWORD --env play -R "$repo"     # prompts; paste the password
   gh secret set PLAY_SERVICE_ACCOUNT_JSON --env play -R "$repo" < picnic-play-publisher.json
   shred -u picnic-play-publisher.json                                  # macOS: rm -P; make a new key if you ever need one
   ```

   On GitHub Free, leave out `--env play`, which makes them repository secrets.

3. **Check:** `gh secret list --env play -R "$repo"` lists the names, never the values.

Optional **repository variable**: `PLAY_TAG_STATUS=draft` (`gh variable set PLAY_TAG_STATUS --body draft`). It makes tag pushes create draft releases while the app is still a draft app (§5.4). Delete it afterwards.

**What the workflow does with them.**
- The upload key and the Play credential never meet in one job, and the repository's test code never runs next to either. Each job gets its own fresh runner:
  - `test` runs the core and app unit tests and has no secrets;
  - `build` has the key;
  - `upload` has the JSON and runs the third-party upload action.
- Every action in the workflow is pinned to a full commit SHA, so a moved tag can't change what runs next to the key. Dependabot (`.github/dependabot.yml`) opens a weekly pull request to update them.
- `build` first lists any missing secret by name and stops; it only ever checks whether a value is set.
- The upload key is decoded into the runner's temp directory just before signing. It is checked with keytool and deleted afterwards.
- The signing build runs as `./gradlew --no-daemon --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process`. No Gradle or Kotlin daemon outlives it with the passwords in its environment, and no Gradle cache entry holds them.

**Rotation.** Make a new JSON key (3.4), update `PLAY_SERVICE_ACCOUNT_JSON`, then delete the old key in Cloud Console. Once a year is plenty.

## 5. The first release, by hand

The Play Developer API can't create an app: the package name is bound by the first upload in Play Console. Until then, every API call fails with "Package not found".

- [ ] **5.1 Get a signed bundle.** Pick one way:
  - **From CI (no Play credential needed yet).** The workflow has to be on the default branch before **Run workflow** appears, so merge first.
    1. Go to **Actions → Play release → Run workflow** and **untick "Upload to Google Play"**. With the CLI: `gh workflow run play-release.yml -f upload=false`.
    2. When it is green, download the artifact `picnic-list-play-<version>-<code>`: `gh run download <run-id>`. It holds `picnic-list.aab` and `mapping.txt`.
  - **Locally:**

    ```bash
    read -rsp 'Upload keystore password: ' PICNIC_UPLOAD_STORE_PASSWORD; echo
    export PICNIC_UPLOAD_STORE_PASSWORD
    export PICNIC_UPLOAD_STORE_FILE="$HOME/keys/picnic/picnic-upload.jks" PICNIC_UPLOAD_KEY_ALIAS=upload
    ./gradlew --no-configuration-cache :app:bundleRelease \
      -Ppicnic.versionCode=$(( ($(date -u +%s) - 1767225600) / 60 )) -Ppicnic.versionName=0.1.0
    unset PICNIC_UPLOAD_STORE_PASSWORD
    keytool -printcert -jarfile app/build/outputs/bundle/release/picnic-list-release.aab | grep SHA256
    ```

    The bundle is `app/build/outputs/bundle/release/picnic-list-release.aab` and the R8 mapping is `app/build/outputs/mapping/release/mapping.txt`. The printed SHA-256 must match the upload key (1.2). The same settings also work as Gradle properties in `~/.gradle/gradle.properties` (`picnic.upload.storeFile`, `picnic.upload.storePassword`, `picnic.upload.keyAlias`, `picnic.upload.keyPassword`). Never put them in the repository's `gradle.properties`.
- [ ] **5.2 Upload it:** go to **Test and release → Testing → Internal testing → Create new release**.
  1. Upload the `.aab`.
  2. Release name: anything, for example `0.1.0`.
  3. Write short release notes.
  4. **Next → Save and publish** (roll out to internal testing).

  Play then shows its first warnings, which are expected for a new app (missing store listing, Data safety and so on). Internal testing doesn't need any of them.
- [ ] **5.3 Upload the mapping file** so crash reports are readable: **Test and release → App bundle explorer**, pick the version, then on its **Downloads** tab upload `mapping.txt` as the ReTrace (deobfuscation) file. Later releases from CI upload it automatically.
- [ ] **5.4 Is it still a "draft app"?** Play's API refuses anything but `status: draft` while the app counts as a draft app ("Only releases with status draft may be created on draft app"). Google doesn't document exactly when an app stops being one. Rolling out the first release by hand (5.2) is the step everyone agrees on. Until a `completed` run succeeds:
  - run the workflow with **status: draft** (and set `PLAY_TAG_STATUS=draft` for tags);
  - then open the draft release in Play Console and roll it out by hand.
- [ ] **5.5 Check the R8 build on a real phone.** Install from the internal-testing link (§6), then try every path:
  - type items, and add items with AI text, a photo and a voice note;
  - icons appear and get drawn;
  - change the aisles and the settings, and test the connection;
  - force-stop the app and reopen it: the lists must still be there.

  Release builds are the only ones R8 shrinks, and there is no emulator in CI.

## 6. Internal testers

- [ ] **6.1** Go to **Test and release → Testing → Internal testing → Testers → Create email list**. Add each tester's Google account email, up to 100 per app. A CSV upload replaces the list.
- [ ] **6.2** Tick the list.
  - Add a **feedback URL or email** (it shows on the opt-in page).
  - **Save**, then **copy the link** and send it to the testers.
  - Each tester opens it while signed in, accepts, then installs from Play. Later updates arrive through Play like any app.
- A tester who opts into internal testing can't also be in a closed or open test; they must opt out of internal testing first.
- **Sideloaded copies:** a phone that has the sideload APK must **uninstall** it before installing from Play. The signatures differ (§10), and uninstalling deletes the lists on that phone.

## 7. Releases from GitHub Actions

`.github/workflows/play-release.yml` runs only from `main` or a `play-v*` tag. A run started on any other branch stops at once, and so does the `play` environment (§4):

| Trigger | Track | Status | Version name |
|---|---|---|---|
| **Run workflow** on `main` (Actions tab, or `gh workflow run play-release.yml -f track=internal -f status=completed`) | input: `internal` (default), `alpha` (closed testing), `beta` (open testing), `production` | input: `completed` (default) or `draft` | `0.1.0-<commit>` (or the tag, if run on a `play-v*` tag) |
| `git tag play-v1.2.3 && git push origin play-v1.2.3` | `internal` | `completed`, or `PLAY_TAG_STATUS` | `1.2.3` |

A manual run has two more inputs: **upload** (untick to only build the signed bundle, §5.1) and **changes-not-sent-for-review** (§12).

The `play-v` tags don't trigger `android.yml`, which builds the sideload APK on `v*` tags.

**What a run does.** Three jobs, each on its own fresh runner:

1. **`test`** (no secrets):
   1. It checks the branch or tag.
   2. It runs the **core tests** (`./gradlew -p core test`) and the **app unit tests** (`./gradlew :app:testDebugUnitTest`).
2. **`build`** (environment `play`, the upload key), only once the tests have passed:
   1. **Check secrets.** It fails at once, naming each missing secret.
   2. **Version.** It computes the version code (§7.2) and name.
   3. **Decode the upload key** and check it: a wrong password, alias or base64 fails here with keytool's message.
   4. **Build the signed bundle:** `./gradlew --no-daemon --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process :app:bundleRelease`, with R8 and resource shrinking. Lint's release checks (`lintVitalRelease`) run as part of it.
   5. **Delete the key** and stop Gradle, then **verify the signature**: the bundle must be signed, and with exactly the upload certificate.
   6. **Upload the bundle and `mapping.txt` as a run artifact.** The artifact keeps your repository's retention, 90 days by default.
3. **`upload`** (environment `play`, the Play credential): **upload to Play** with [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play) v1.1.5, pinned to its commit. It sends the bundle, the mapping file and the release name (`<version name> (<version code>)`).

### 7.1 Why this upload action

- **r0adkll/upload-google-play** is maintained:
  - v1.1.5 (April 2026) runs on Node 24;
  - dependency updates landed in September 2026;
  - it only needs the finished `.aab`, so it doesn't care about the AGP version.
- **[Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)** 4.1.1 (August 2026, built against AGP 9.0) would also work, and can manage store listings and pick version codes itself. It was not chosen because it would put the Play credential inside the Gradle build, next to every build plugin, and couple publishing to AGP upgrades.

### 7.2 Version codes

- **Play builds:** `versionCode` = **minutes since 2026-01-01 00:00 UTC**, computed when the run starts. `-Ppicnic.versionCode` is the same property `android.yml` uses. Play only accepts a code higher than every code already uploaded, and this rule keeps that true:
  - It only grows. Runs are serialized (one `play-release` run at a time), and each takes minutes.
  - Re-running a failed run builds a new, higher code, so nothing is ever reused.
  - Renaming the workflow or moving the repository changes nothing (a run-number scheme would restart at 1).
  - It is about 394,000 in October 2026, grows about 526,000 a year, and stays below Play's maximum of 2,100,000,000 for thousands of years.
- **Manual uploads** use the same formula (5.1), so they fit in between.
- **Sideload APKs** (`android.yml`) use the small CI run number and the debug key. They are a separate line of versions that never meets Play's (§10).
- **versionName:** the tag without `play-v` (`1.2.3`), or `0.1.0-<commit>` for untagged runs. Testers see it in Play.
- Gradle refuses a code outside `1..2100000000`.

### 7.3 After every release

On the phone, Play shows the update within minutes for internal testers; the Play Store app's **Manage apps** can force a check. Testing feedback appears under **Monitor and improve → Ratings and reviews → Testing feedback**.

### 7.4 Optional: keyless authentication (Workload Identity Federation)

If key creation is blocked (3.4), or you'd rather have no long-lived key:

1. Create a workload identity pool and provider for GitHub in the Cloud project, following [google-github-actions/auth](https://github.com/google-github-actions/auth). Restrict it to `corashallit/picniccore-shopping-list`.
2. Let it impersonate `picnic-play-publisher` (`roles/iam.workloadIdentityUser`).
3. In the workflow's `upload` job:
   - set `permissions: { id-token: write }`;
   - add before the upload step:

     ```yaml
     - id: auth
       uses: google-github-actions/auth@v3
       with:
         workload_identity_provider: projects/<number>/locations/global/workloadIdentityPools/<pool>/providers/<provider>
         service_account: picnic-play-publisher@picnic-play.iam.gserviceaccount.com
     ```

   - in the upload step, replace `serviceAccountJsonPlainText: …` with `serviceAccountJson: ${{ steps.auth.outputs.credentials_file_path }}`;
   - drop `PLAY_SERVICE_ACCOUNT_JSON` from the secret check.

## 8. Before closed testing or production

Internal testing is exempt from the Data safety form and "might not be subject to standard Play policy or security reviews". A **closed test**, which a personal account needs before production, requires the whole app setup. Everything below lives in **Policy and programs → App content** unless noted otherwise. Do the ads declaration, sign-in details and privacy policy first: Play asks for them before **Target audience and content**.

### 8.1 App changes needed first (not in this pipeline)

These belong to the app's code owners. All three are Play policy requirements that apply to this app.

- [ ] **In-app privacy policy link.** Apps that use a sensitive permission (here `RECORD_AUDIO`) must link the privacy policy both on the store listing *and inside the app*. Settings' About footer is the natural place.
- [ ] **In-app reporting of AI-generated content.** The AI-Generated Content policy covers images generated from text prompts, which is what "Draw new icons with AI" does. Such apps "must contain in-app user reporting or flagging features that allow users to report or flag offensive content to developers without needing to exit the app". A "Report this icon" action in the edit sheet would do. It could open an email to Cora with the item name and icon attached, and offer "Draw a new icon".
- [ ] **A prominent disclosure for background sending.** Item names you type also go to the AI in the background, to sort them into aisles and pick or draw icons. Today only Settings says so (`settings_help_privacy`). The User Data policy wants a disclosure "displayed in the normal usage of the app", followed by an affirmative choice, when data use may not be expected. One option: a one-time dialog the first time an OpenAI key is saved, saying what is sent where, with "Turn on AI" / "Not now".

### 8.2 Policy declarations

| Declaration | Answer for Picnic List |
|---|---|
| **Privacy policy** | The URL where [PRIVACY.md](PRIVACY.md) is hosted, for example `https://cora.earth/picnic/privacy`: a public web page that isn't editable by others. |
| **Ads** | No ads. |
| **Sign-in details** (app access) | "All or some functionality is restricted." The AI features need an OpenAI API key. Give reviewers **a separate key made only for the review**, never a friend's key or your own: a project of its own (for example "Picnic List – Play review") with **Enforce a hard limit** on a small monthly budget (US$5 is plenty), and a restricted key with only List models: Read, Model capabilities: Request and Responses API: Write ([OPENAI_KEY.md](OPENAI_KEY.md), steps 2 to 5). Instructions for the form: *Settings → Your OpenAI key → paste the key → Save. Test connection runs by itself.* Delete the key when the review is over and make a new one for the next review. Typing items, aisles and lists work without a key. |
| **Content rating** | See 8.4. |
| **Target audience and content** | **18 and over** only. The app sends what you add to OpenAI, whose terms carry their own age rules, and adults-only keeps it clear of the Families policy. Answer the "could it appeal to children" question truthfully: the pastel magical-girl style is decoration on a grocery tool for adults. |
| **Data safety** | See 8.3. |
| **Advertising ID** | No. The app has no `AD_ID` permission: the merged manifest has only INTERNET, RECORD_AUDIO and androidx's internal receiver permission. |
| **Government apps / Financial features / Health apps / News apps** | No / none / none / no. |
| **Account deletion** | Not applicable: nobody creates an account in the app. OpenAI keys are made by hand outside it, in OpenAI's dashboard. |

### 8.3 Data safety form, tailored

Answers follow what the code does (see [PRIVACY.md](PRIVACY.md) for the details).

Some background for the "shared" and "ephemeral" questions:
- OpenAI processes requests on the developer's behalf, as a **service provider**, which Play does not count as sharing.
- With a user's own key, the user sends their data to their own OpenAI account.
- With a key Cora set up, the requests are made under her OpenAI account, with OpenAI as her service provider.
- Nothing goes off the phone until the user adds an OpenAI key.

**Overview questions:**

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes**. HTTPS only: every request goes to `https://api.openai.com/v1` (the app has no address setting), and Android blocks cleartext at targetSdk 36. |
| Do you provide a way for users to request that their data is deleted? | **Yes**: by email, per PRIVACY.md. Nothing reaches a server of Cora's, and OpenAI's abuse-monitoring logs expire within 30 days, well inside the 90 days Play accepts for this badge. |

**Data types:**

| Data type | Collected | Shared | Processed ephemerally | Required / optional | Purposes |
|---|---|---|---|---|---|
| **Photos and videos → Photos** (a photo of a handwritten list) | Yes | No | No: OpenAI may keep requests up to 30 days for abuse monitoring | Optional | App functionality |
| **Audio files → Voice or sound recordings** (voice notes) | Yes | No | No (conservative; OpenAI doesn't keep transcription requests, so "Yes" would also be defensible) | Optional | App functionality |
| **App activity → Other user-generated content** (typed requests, item names, aisle names, transcripts) | Yes | No | No | Optional (the app works without AI) | App functionality |
| **Device or other IDs** (a random per-install ID, hashed, sent as OpenAI's `safety_identifier`) | Yes | No | No | Required whenever AI is used | Fraud prevention, security, and compliance |

**Everything else: No.**
- **Location:** No. The web-search hint "Ontario, Canada" is fixed in the app, not read from the phone, and photos are re-encoded without EXIF, so no GPS tags.
- **Personal info, financial, health, messages, contacts, calendar, files, web browsing, installed apps, crash logs and diagnostics:** none. There is no analytics or crash reporting.

### 8.4 Content rating (IARC questionnaire)

1. Category: **Utility, Productivity, Communication, or Other**.
2. Answer from these facts:
   - No violence, fear, sexuality, gambling or bad language.
   - **Alcohol:** the bundled icon library has **beer and wine** bottles that appear when you add them to a list. Nothing shows anyone drinking or encourages it. If the questionnaire asks about alcohol references, answer yes and describe it that way. That may raise the rating in some regions, which is fine with an 18+ audience.
   - Users don't interact or share content with each other. The app doesn't share location, sell digital goods, or contain a web browser (source links open in the phone's browser).
   - **AI-generated content:** if asked, yes. Item lists come from a language model and icons from an image model, and only the user who asked sees them.
3. Re-take the questionnaire whenever features change.

### 8.5 The closed test (personal accounts)

- [ ] Create the closed-testing release: **Test and release → Testing → Closed testing**. The default track is called `alpha` in the API, so run the workflow with `track=alpha`.
- [ ] Recruit **12 or more** testers by email list or Google Group, and keep them opted in for **14 consecutive days**. Someone who opts out and back in starts over. Ask them to use every feature, and keep notes of their feedback: the production application asks for a summary.
- [ ] Then **Dashboard → Apply for production**.

## 9. Store listing assets

**Grow users → Store presence → Main store listing.** The listing is shared by every track.

| Asset | Requirement | Suggestion |
|---|---|---|
| App name | ≤ 30 characters | Picnic List |
| Short description | ≤ 80 characters | "A grocery list that sorts itself into your store's aisles, with AI help." |
| Full description | ≤ 4000 characters | What it does (README "What it does"), plus one plain paragraph saying it uses OpenAI (with an OpenAI API key you paste in) and what is sent. |
| **App icon** | **512 × 512** PNG, 32-bit with alpha, ≤ 1024 KB | Export from the art pass's launcher icon master, not the adaptive XML. |
| **Feature graphic** | **1024 × 500**, JPEG or 24-bit PNG, **no alpha**. Required to publish the listing. | Picnic blanket plus wordmark from the chosen art direction. |
| **Phone screenshots** | 2–8; JPEG or 24-bit PNG (no alpha); each side 320–3840 px; long side ≤ 2× short side. To be eligible for Play's large-format recommendations: ≥ 4 screenshots, 9:16, ≥ 1080 × 1920. | From the Roborazzi goldens after the art pass, see below. |
| Category, tags, contact | category and up to 5 tags; support email (required) and website | Food & Drink (or Shopping); `https://cora.earth`. |

**Screenshots from Roborazzi.** The screen goldens in `app/src/test/screenshots/` are 1233 × 2673 RGBA. That ratio is 2.17, over Play's 2:1 limit, and the images have an alpha channel. There are two ways to fix that:

- **Crop and flatten** (ImageMagick 7). This keeps the top 9:16 of the screen, so check that nothing important is at the bottom:

  ```bash
  mkdir -p store
  for s in list_mixed_light list_mixed_dark add_preview_light edit_icons_light aisles_editor_light settings_key_saved_light; do
    magick "app/src/test/screenshots/$s.png" -gravity north -crop 1233x2192+0+0 +repage \
      -resize 1080x1920 -background white -alpha remove -alpha off "store/$s.png"
  done
  ```

- **Render at the right size.** Add store-screenshot tests with the qualifier `w360dp-h640dp-xxhdpi` (exactly 1080 × 1920), rendering the same stateless screens with tidy sample data. Then flatten them as above. This is the better option once the art settles.

Give every screenshot alt text in Play Console.

## 10. Signing: Play builds vs sideloaded APKs

| | Debug / sideload APK | Play install |
|---|---|---|
| Built by | `android.yml` / `assembleDebug`, `assembleSideload` | `play-release.yml` / `bundleRelease` |
| Signed with | the **committed** `app/debug.keystore` (public: anyone with the repository can sign with it) | your **upload key** for the upload, then re-signed by **Google's app signing key** for delivery |
| Signing certificate on the phone | `69:7c:19:…:8c` (docs/BUILDING.md §6) | Google's app signing certificate. Its SHA-256 values are on the **Play app signing** page; new apps get several keys for hybrid signing. |
| Version codes | CI run number | minutes since 2026 |

- **Same package, different signatures.** Android won't update one with the other ("App not installed" / "package conflicts"). Switching a phone between them means uninstalling first, which deletes the lists on that phone. Android's backup holds only `state.json`; don't rely on it carrying over between the two signatures.
- **The upload key never reaches phones.** Leaking it doesn't let anyone update installed copies; it can be reset (1.4).
- **Two side-by-side builds** would need a different `applicationId` for the sideload build, for example an `applicationIdSuffix` of `.sideload`. That would break in-place updates for everyone who already has the sideload APK, so it isn't done.

### 10.1 Android developer verification (what changes in 2026–2027)

- **Since 30 September 2026,** certified Android devices (Android 7+) in **Brazil, Indonesia, Singapore and Thailand** only install apps from participating stores, Google Play among them, when the app is registered to a verified developer. Google expands this "globally for all apps on certified Android devices in 2027". **Canada is not in the first wave, so nothing changes for Ontario yet.**
- **Play installs:** nothing to do. Play registers apps that use Play App Signing automatically ("99% of apps"). Your Play Console identity is the verified developer, and the Play Console's **Android developer verification** page shows each app's registration status.
- **Sideloaded APKs**, once enforcement reaches Canada:
  - an **unregistered** app installs only through ADB (unchanged) or after the user turns on the **advanced flow**. That is a one-time setup: developer options, a "nobody is coaching you" check, a restart, a **24-hour wait**, then biometric confirmation. Afterwards each install shows an "unverified developer" warning with **Install anyway**.
  - To make the sideload APK install normally instead, register `earth.cora.picnic` together with the APK's signing key. For apps distributed both on and off Play, Google says to do this in Play Console, and a package can have more than one signing key.
- **Don't register the committed debug key.** Its private key is in a public repository: registering it would let anyone sign apps that verify as yours. Before 2027, pick one of these instead:
  - move friends to Play (internal or closed testing);
  - sign sideload APKs with a private key kept in CI secrets, and register that key;
  - distribute the universal APK Play generates, signed with Google's app signing key. Download it from **App bundle explorer → Downloads**.
- **Limited distribution accounts** share an app with up to 20 devices without ID verification or a fee. They are meant for students and hobbyists who don't want to verify; your Play account already covers you.

## 11. Gradle reference

- **Signing settings.** `app/build.gradle.kts` reads these from the environment or from Gradle properties:

  | Environment variable | Gradle property |
  |---|---|
  | `PICNIC_UPLOAD_STORE_FILE` | `picnic.upload.storeFile` |
  | `PICNIC_UPLOAD_STORE_PASSWORD` | `picnic.upload.storePassword` |
  | `PICNIC_UPLOAD_KEY_ALIAS` | `picnic.upload.keyAlias` |
  | `PICNIC_UPLOAD_KEY_PASSWORD` | `picnic.upload.keyPassword` (optional; defaults to the store password) |

  - Relative store paths resolve against the repository root.
  - **None set:** the release build is simply unsigned, and debug and sideload builds are unchanged.
  - **Some set:** the build stops and names the missing ones. The same happens for a store file that doesn't exist.
- **The release build type:** R8 (`isMinifyEnabled`), resource shrinking, not debuggable.
  - Outputs: `picnic-list-release.aab` and `mapping.txt`.
  - Verified 2026-10-01: signed with a test upload key (jarsigner "jar verified", bundletool universal APK passes `apksigner verify` with v2 and v3); targetSdk 36; no `AD_ID`.
  - Its two native libraries (androidx graphics-path and DataStore's shared counter) are **16 KB page-aligned**: every ELF LOAD segment is at 0x4000, and `zipalign -c -P 16` passes. Play requires 16 KB support for apps targeting Android 15+.
- **Gradle configuration cache.** Use `--no-configuration-cache` for signed builds. Gradle encrypts cache entries, but the passwords would still be written to disk.

## 12. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Workflow: "Play releases run only from main or a play-v* tag" | The run was started on another branch. Merge first, then run it on `main` or push a `play-v*` tag (§7). |
| Workflow: "Branch … is not allowed to deploy to play due to environment protection rules" | The same rule, enforced by the `play` environment (§4). |
| Workflow: "Missing secrets: …" | Add the named secrets to the `play` environment, or as repository secrets on GitHub Free (§4). |
| Workflow: "Couldn't open the upload key (keytool error: … password was incorrect / Alias <x> does not exist)" | Wrong `PICNIC_UPLOAD_STORE_PASSWORD`, or the key's alias isn't `upload`: set the repository variable `PICNIC_UPLOAD_KEY_ALIAS` to the `-alias` you used (§4). |
| Workflow: "… is not valid base64" | Re-create `PICNIC_UPLOAD_KEYSTORE_BASE64` with the command in §4. |
| Gradle: "Release signing is only partly configured" | Set all of store file, store password and alias, or none. |
| Play: "Package not found: earth.cora.picnic" | The first upload wasn't done by hand yet (§5), or the service account was invited for a different app. |
| Play: "Only releases with status draft may be created on draft app" | Run with **status: draft** and roll out from Play Console (§5.4). |
| Play: "The caller does not have permission" (403) | The service account isn't invited, lacks **Release apps to testing tracks** for this app, or the permission hasn't applied yet; wait and re-run. Check that the JSON's `client_email` is the invited address. |
| Play: "Changes cannot be sent for review automatically. Please set the query parameter changesNotSentForReview to true" | Play Console holds other changes that haven't been sent for review yet, for example a saved store listing or App content edit. Open **Publishing overview**, send those changes for review, then re-run. Or re-run with **changes-not-sent-for-review** ticked: the release is then saved, and you send it for review from **Publishing overview** yourself. |
| Play: "Version code … has already been used" | Two uploads computed the same minute, for example a manual one. Re-run: a new run gets a new code. |
| Play: "Your Android App Bundle is signed with the wrong key" | The secret holds a different key than the first upload used. Use the original, or reset the upload key (1.4). |
| Play: "…signed in debug mode" | Signed with `app/debug.keystore`. The workflow's signature check prevents this; for local builds, check the env vars. |
| Cloud: "Service account key creation is disabled" | Org policy (§3.4): use a project outside the organization, exempt the project, or use §7.4. |

## 13. Sources

All fetched 2026-10-01.

- Play Console Help:
  - [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
  - [Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334)
  - [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152)
  - [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
  - [Add preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)
  - [Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)
  - [Prepare your app for review (App content)](https://support.google.com/googleplay/android-developer/answer/9859455)
  - [Content rating requirements](https://support.google.com/googleplay/android-developer/answer/188189) and the [questionnaire category](https://support.google.com/googleplay/android-developer/answer/6159978)
  - [Target audience and app content](https://support.google.com/googleplay/android-developer/answer/9867159)
  - [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
  - [AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/13985936) and its [explainer](https://support.google.com/googleplay/android-developer/answer/14094294)
  - [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
  - [Users and permissions](https://support.google.com/googleplay/android-developer/answer/9844686)
- Google Play Developer API: [Getting started](https://developers.google.com/android-publisher/getting_started)
- Android developer verification: [guide](https://developer.android.com/developer-verification/guides), [FAQ](https://developer.android.com/developer-verification/guides/faq), [limited distribution](https://developer.android.com/developer-verification/guides/limited-distribution)
- Google Cloud: [Secure-by-default organizations](https://cloud.google.com/resource-manager/docs/secure-by-default-organizations)
- GitHub: [Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments) (environment secrets, deployment branches and tags, and which plans have them), [REST API for deployment branch policies](https://docs.github.com/en/rest/deployments/branch-policies)
- Upload action: [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play), tag `v1.1.5` = commit `e738b9dd8f2476ea806d921b64aacd24f34515a5`, `runs.using: node24`. Alternative: [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) 4.1.1.
- OpenAI: [Data controls in the OpenAI platform](https://developers.openai.com/api/docs/guides/your-data) (used in §8.3 and PRIVACY.md)
