# DengueWatch BD — Android

An Android port of the DengueWatch iOS app: dengue surveillance for Bangladesh,
built on the published DGHS feed, in English and Bengali.

This is a rewrite in Kotlin and Jetpack Compose, not a wrapper. It carries the
same design system, the same domain model and the same copy as the iOS build —
the deliberate decisions in that app (the two-token risk palette, the
largest-remainder apportioning, the containment-before-distance geography) are
reproduced here rather than re-derived.

The original Swift sources are kept under `ios-reference/` as the reference the
port was made against, refreshed to the iOS app of 16 October 2026 (WHO 2025
guideline, fluid log, deleting checks). Nothing in the Android build reads them,
and they can be deleted once the port stops being compared against them.

## Building

```sh
./gradlew assembleDebug              # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest          # rules, stores, strings, feed, geography
./gradlew connectedDebugAndroidTest  # UI tests, on a running emulator or phone
```

Requires JDK 17 or newer and Android SDK platform 37. Android Studio's bundled
JDK works; `local.properties` points at the SDK.

### Tests

The unit tests run on the JVM and cover what each rule and store does: triage,
the WHO care plan, the fluid log, string parity, the feed parser, geography and
the layout rules (no text capped to a line count, no truncation).

The UI tests in `app/src/androidTest` tap through My health on the real app:
add a drink and see the total, log and undo a urination, complete a symptom
check, delete the latest check and a past one, and export the log as a PDF.
They need an emulator or a phone connected:

```sh
~/Library/Android/sdk/emulator/emulator -avd <name> -no-window &
./gradlew connectedDebugAndroidTest
```

Each test resets the app's own files and preferences (records empty, English,
past the first-run disclaimer) and writes the records it needs, so the app
carries no test mode. Buttons are triggered through their click action rather
than a touch at their centre: the floating tab bar sits over the bottom of My
health, and a touch aimed at a row half under it lands on the bar. The export
test checks the finished PDF was written for the share sheet; the sheet itself
is the system's. Gradle uninstalls the app after the run.

#### Shared clinical cases

`app/src/test/resources/clinical-rules.json` holds the cases both apps must
agree on: triage outcomes, the WHO care-plan group and its reasons, reading
ranges, what a typed number is read as, what a scanned report line is read
as, trends, and the fluid log's waking hours. `ClinicalConformanceTest` runs
every case here; the iOS app's `ClinicalConformanceTests` runs the same file.
Change a clinical rule in one app, add or update its case, and copy the file
byte for byte to the other repository
(`DengueWatchBD/DengueWatchBDTests/clinical-rules.json`). When both
repositories sit side by side in `~/Developer`, a test here fails if the two
copies differ.

The plumbing has its own tests: the stores read and write real files in a
temporary folder, the alert and digest timing is tested as plain functions,
and `BackgroundScheduleTest` (instrumented) checks that re-applying the
periodic work on every launch leaves its schedule alone.

## Layout

```
model/       Domain types and the feed wire format
data/        Feed loading, sync, stores, preferences, location
i18n/        Language, number and date formatting, the two string tables
ui/theme/    Palette, typography, spacing, surfaces, icon vocabulary
feature/     One package per tab, plus shared components and charts
```

## My health and WHO's 2025 guideline

The symptom check and care plan follow WHO's *Guidelines for clinical
management of arboviral diseases* (2025), as the iOS app does, rule for rule:

- Three levels in the guideline's terms — Care at home (non-severe), Hospital
  today and Emergency care now (severe) — shown with an icon, not the 2009
  A / B / C letters.
- Being unable to drink is a warning sign. Pregnancy, age under 5 or over 60,
  or a long-term condition with any symptom means "see a doctor today";
  previous dengue is a stated reason that changes no advice on its own.
- A haematocrit that rises on two reports in a row is pointed out for the
  doctor, but like every lab value it never changes the advice.
- A check's advice is worked out from its saved answers by the current rules,
  so a check made before a rule changed agrees with the care plan beside it.

The fluid log is WHO's protocolized oral fluids: drinks in millilitres and the
times urine was passed, against WHO's aim of four to six a day, with no volume
target. Six waking hours without urine logged (the night, 11 pm to 7 am, does
not count) raises the symptom check's own warning sign. Fluids join the log by
day, and the PDF gains a fluid summary over whole days, daily intake and urine
charts, and a fluid card per day.

A single check can be deleted from the ⋮ menu on the symptom check card, and any
earlier one from its list of past checks. My log keeps Delete all reachable
when only checks exist.

Readings are coloured on the risk palette everywhere — green in the usual
range, amber to red outside it — always beside their word (In the usual range,
Low, High), so colour is never the only signal.

## Where the figures come from

Two documents, published at `druzzal.github.io/dengue-bd-dashboard`:

| file | size | role |
| --- | --- | --- |
| `summary.json` | ~350 B | headline figures and the DGHS report date |
| `latest.json` | ~40 KB | every chart series and table |

Each refresh asks the small one what DGHS's latest report date is, and only
downloads the large one when that date is newer than what is already on screen.
A copy of `latest.json` ships in `assets/`, so the app opens with real figures
on first run and keeps working offline afterwards.

## Alerts

Three local alerts, all worked out on the phone from data already downloaded.
Nothing is registered with a push server and no location or identity leaves the
device.

- **Your area got worse.** Fires at most once a day, and only when your chosen
  area actually reaches the band you asked about. The daily gate is the reason
  anyone keeps these on: DGHS publishes daily and a division can sit in one band
  for weeks, so alerting on every refresh would be noise inside a week.
- **You entered a high-risk area.** Noticed two ways, because one is not
  reliable enough on its own. Geofences are the fast path — Play Services wakes
  the app within a minute or two of the boundary. They are also the fragile one:
  registration is refused outright when the device's location mode cannot
  support it, Doze defers transitions, and several manufacturers kill the
  delivering process to save battery. So the periodic refresh also asks the
  simple question directly — where is this phone, which area is that, is it one
  you asked about — which depends on nothing but a location fix. Slower, but it
  arrives. Both share one per-area daily limit so a reader whose geofences work
  is not told twice.

  Armed against **your chosen threshold**, not a fixed band. It used to watch
  High and above regardless of the setting, which made the picker a lie in one
  direction and left the feature doing nothing at all in a season where no
  division is above Moderate.

  The first check after switching it on only records where you are; it does not
  report an arrival. Otherwise turning the warning on at home in a risky area
  fires immediately — "you are entering Dhaka North City" to someone who has
  lived there for years.
- **Weekly summary,** Sunday 09:00 — the start of the working week in
  Bangladesh.

`RefreshWorker` runs periodically to fetch, re-arm the fences and evaluate the
area alert while the app is closed. WorkManager decides when that actually
happens; the interval is a floor, not a schedule. Everything still works if it
never runs, just later, when the app is next opened.

**Arming can fail, and the app says so.** Play Services refuses geofence
registration in some states — `GEOFENCE_NOT_AVAILABLE` on a device whose
location mode does not support it, which is what happens on a stock emulator.
The first version of this swallowed that failure, leaving a switch reading "on"
with nothing watching. The result is now recorded and the settings screen
distinguishes three states: watching *n* areas, nothing is high right now, and
Android would not register them.

## Notes on the port

Five places where this build does something different from iOS, and why:

**The map is MapLibre over OpenFreeMap, not MapKit or Google Maps.** iOS puts
the risk bubbles over MapKit. Here they sit over OpenFreeMap's vector tiles
through MapLibre: no API key, no quota and no Play Services requirement. The
tiles were raster OSM first, but those bake each country's local names into the
image, so Bangladesh came out in Bengali whatever the app's language; vector
tiles carry names as data, so the style draws the name the reader chose. The
bubbles are painted largest-first so a small high-incidence area is never buried
under a big city. A high-traffic release should still consider its own tile
server rather than lean on a free one.

**The dashboard's map preview is still drawn, not tiled.** Deliberately
different from the map tab. The preview sits on the home screen, which has to
render completely on a cold start with no network; tiles would leave a grey
rectangle there on a first run offline. The national outline is already in the
binary — it is what decides whether a coordinate is in the country at all — so
the preview is always right and never waits, and tapping it opens the real map.

**The app icon is generated from the iOS artwork, not redrawn.**
`tools/MakeIcon.swift` reads `AppIcon1024.png` and emits the adaptive-icon
layers. It cannot simply be dropped in whole: Android masks an icon down to a
66dp circle inside a 108dp canvas, which would slice off the mosquito's wings
and legs. So the subject is cut out by alpha — measured as distance from the
red field, which catches the dark mosquito and the white map alike — and scaled
by its furthest pixel from centre, not by its bounding box, since the box's
corners are empty. The red field is resampled as the background layer, so it
takes the crop and the two layers share one red with no seam between them.

No `<monochrome>` layer: the artwork does not reduce to a legible silhouette —
the map fills in solid — and launchers fall back to the full-colour icon in
themed mode, which reads far better than a smudge. Re-run with:

```sh
swift tools/MakeIcon.swift \
  ios-reference/DengueWatchBD/Assets.xcassets/AppIcon.appiconset/AppIcon1024.png \
  app/src/main/res
```

**Letter-spacing is applied to Latin only.** The type scale carries tracking
values — negative on large text, positive on small — as an optical correction
for Latin letterforms. Applied to Bengali they do real damage: Compose measures
the text narrower than the glyphs it then draws, so the word is clipped at the
right edge rather than laid out to fit. On the hero card the risk band "মাঝারি"
rendered as "মাঝা", which is not a cosmetic defect — it is the single word the
screen exists to communicate, silently showing something else. Bengali is shaped
rather than spaced and needs no correction, so `typo()` zeroes tracking for it.
Found by running the app in Bengali; nothing in the build, the lint pass or the
36 tests could see it.

**The charts are drawn, not imported.** Every plot in the app is one of three
shapes, and each has to sit inside a colour system with two hand-tuned schemes
and a risk scale that must not be recoloured. A charting library would be
configured almost entirely out of its defaults to reach the same result.

**Hospitals nearby go to the maps app.** iOS searches MapKit directly. The free
equivalent here does not exist — Places needs a billed key — so the same job is
handed to whichever maps app the reader already has, which is where the
directions were going anyway.

**Feed parsing is hand-written.** The feed's points and cells are genuinely
polymorphic: a point is a bare number or a `{name, y}` object, a cell is a
string, a number or null. `org.json` readers express that more honestly than
annotations plus custom serializers, and drop a dependency.

## Background work

Carried over since the first port: background refresh runs on WorkManager
(`data/BackgroundRefresh.kt`), as iOS's `BGTask` does; the high-risk-area entry
alert registers geofences through Play Services (`data/Geofencing.kt`); and the
area and weekly-digest alerts post local notifications (`data/Notifications.kt`).
Nothing is registered with a push server.

## Not in this repository

The daily check that the published feed is still updating runs as a GitHub
workflow in the iOS repository, and covers the feed both apps read. This
project has no GitHub repository of its own.
