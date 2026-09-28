# Publishing DengueWatch BD — step by step

Everything below uses your real values. Where a file is named, the path is
relative to this project unless it says `~/Desktop`.

Play Console's wording moves around between redesigns. Section *names* here may
not match exactly; the order and the substance do.

---

## Your details, in one place

| | |
|---|---|
| App name | `DengueWatch BD` |
| Package | `com.denguewatch.bd` — permanent, cannot change after first publish |
| Upload file | `~/Desktop/DengueWatch-build/app-release.aab` (22 MB, signed) |
| Privacy policy | `https://druzzal.github.io/denguewatch-bd-privacy/` |
| Support email | `dr.uzzaltang@gmail.com` |
| Category | Medical |
| Screenshots | `play-store/screenshots/` — 5 files, 1080×2340 |
| Feature graphic | `play-store/feature-graphic/feature-graphic-1024x500.png` |
| App icon | `~/Desktop/DengueWatch-build/play-store-icon-512.png` |

---

## Step 0 — Developer account

Needed once, before anything else. <https://play.google.com/console>

- US$25, one time, non-refundable.
- **Identity verification.** Personal accounts must verify with government ID
  and an address. This can take a few days, so start it first — everything else
  waits on it.
- Choose the account type carefully: **personal** accounts created after roughly
  November 2023 must run a closed test with **12 testers for 14 continuous days**
  before they can apply for production access. An organisation account does not
  have this requirement but needs a D-U-N-S number.

If the 12-tester rule applies to you, it sets your timeline: two weeks minimum
from first closed-test release, regardless of how finished the app is.

---

## Step 1 — Create the app

**All apps → Create app**

- App name: `DengueWatch BD`
- Default language: English (or Bengali — you can add the other afterwards)
- App or game: **App**
- Free or paid: **Free** (this cannot be changed to paid later)
- Confirm the declarations about Play policies and US export law.

---

## Step 2 — Upload to Internal testing first

Do this **before** the store listing. Internal testing accepts a build with no
review, so you find out immediately whether the AAB is accepted.

**Testing → Internal testing → Create new release**

1. When prompted about **Play App Signing**, accept it. Google then holds the
   real signing key and your `.jks` becomes the *upload* key — which is the
   recoverable arrangement described in `../KEYSTORE-FACTS.md`.
2. Upload `~/Desktop/DengueWatch-build/app-release.aab`.
3. Release name will default to `1 (1.0)` — fine.
4. Release notes: something plain, e.g.
   *First release. Dengue surveillance figures for Bangladesh from DGHS, a
   symptom guide based on WHO criteria, hospital directory and prevention
   information.*
5. Add yourself as a tester (Testers tab → create an email list with your
   address), then **Save → Review release → Start rollout**.

**Then install it from Play on your phone via the opt-in link.** This is the
one check that cannot be done locally: it proves Play App Signing re-signed the
bundle correctly and that what a real user downloads actually runs.

---

## Step 3 — Store listing

**Grow → Store presence → Main store listing**

Copy from `store-listing.md`:

- **App name** — `DengueWatch BD`
- **Short description** — 80 characters max, the draft is 76
- **Full description** — the long block in that file
- **App icon** — `~/Desktop/DengueWatch-build/play-store-icon-512.png`
- **Feature graphic** — `play-store/feature-graphic/feature-graphic-1024x500.png`
- **Phone screenshots** — all 5 from `play-store/screenshots/`

---

## Step 4 — App content declarations

**Policy → App content.** This is the long part. Each item must be completed
before you can submit for review.

### 4a. Privacy policy
Paste `https://druzzal.github.io/denguewatch-bd-privacy/`

### 4b. Ads
**No**, the app contains no ads. Verified: no advertising SDK in the dependency
list.

### 4c. App access
**All functionality is available without special access.** There is no login.

### 4d. Content rating
Complete the questionnaire. Answer honestly; expect the lowest rating.
- Category: **Utility, Productivity, Communication, or Other** (or Medical if
  offered)
- No violence, sexuality, profanity, gambling, drugs
- **Does it contain medical or health information?** Yes — public-health
  surveillance data and symptom guidance based on WHO criteria
- No user-to-user communication, no user-generated content sharing

### 4e. Target audience
- Target age: **18+** (or 13+). Do **not** target children — an app with a
  symptom checker should not be in the Designed for Families programme.
- Appeals to children: **No**

### 4f. Data safety
From `data-safety.md`. The short version:
- **Does your app collect or share any of the required user data types? → No**
- Data encrypted in transit: **Yes** (all endpoints HTTPS)
- The reasoning, with the code evidence, is in that file if a reviewer questions
  it. The key point: location and the symptom log are processed on-device and
  never transmitted, and Play does not count on-device processing as collection.

### 4g. Health apps declaration
The Medical category usually triggers this. Declare it as a **health information
/ public-health information** app. It does **not** provide diagnosis, treatment,
or clinical decision support — the app states this itself, in the first-run
notice and the medical disclaimer.

### 4h. Government apps
**No.** DengueWatch BD is independent and not affiliated with or endorsed by
DGHS. The app and policy both say so; keep that answer consistent.

### 4i. News, financial features
**No** to both.

---

## Step 5 — Background location declaration

The item most likely to delay you. Play reviews it individually.

**Policy → App content → Sensitive app permissions → Location permissions**

Use the declaration text in `background-location-declaration.md`, and record the
video it describes:

1. Prevent tab → **Alert me**
2. Show the "Alerts where you are" section and its on-screen explanation
3. Switch on "Warn me when I enter a high-risk area"
4. Show the Android permission prompt
5. Show the resulting status: "On, including in the background" and
   "Watching N high-risk areas"

Record on a real device with the release build. Upload it unlisted to YouTube or
Google Drive and give Play the link. No narration needed.

The three statements — in-app text, this declaration, and the privacy policy —
must agree. They currently do: on-device only, optional, never uploaded. If you
change one, change all three.

---

## Step 6 — Closed testing, if your account requires it

**Testing → Closed testing → Create new release**

- Same AAB.
- Recruit **12 testers** who opt in and keep the app installed **14 continuous
  days**. They must actually remain opted in for the duration; people dropping
  out resets your standing.
- After 14 days, **apply for production access** and answer Google's questions
  about your testing.

---

## Step 7 — Production

**Production → Create new release** → same AAB → roll out.

First review typically takes a few days and can take longer for a Medical app
requesting background location. Consider a **staged rollout** (start at 20%) so
a problem reaches few users.

---

## After you publish

- **Increment `versionCode`** in `app/build.gradle.kts` for every subsequent
  upload. Play rejects a repeat of `1`.
- Watch **Android vitals** for crashes on devices you never tested. This app has
  only run on an emulator (API 36) and one OnePlus 7 (API 31); `minSdk` is 26,
  so API 26–30 is unverified in the field.
- Keep the privacy policy reachable. If that URL 404s, Play can pull the app.

---

## Common rejection causes for this specific app

1. **Background location** — insufficient justification or a missing/unclear
   video. Most likely single cause.
2. **Data Safety mismatch** — if the form contradicts the privacy policy.
   They currently agree; keep them that way.
3. **Medical claims** — the app is careful (it says plainly it cannot diagnose),
   but if you edit any wording, avoid implying diagnosis.
4. **Broken privacy policy URL** — verify it loads before every submission.
