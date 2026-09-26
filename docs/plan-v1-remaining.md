# Fuel Log v1 — plan for the remaining chunks

Written 2026-09-26 against `main` at `6b9eb81` (clean). Companion to the master build plan
(`docs/plan-v1-master.md`), which stays authoritative for the *why*; this file
says what is left and how to do it. One chunk per session, one commit per chunk, long-form
commit messages as on `main`.

Paths: `:app` under `app/src/main/java/com/fuelexpenselog/app/`, `:core:domain` under
`core/domain/src/main/kotlin/com/fuelexpenselog/domain/`, `:core:csv` under
`core/csv/src/main/kotlin/com/fuelexpenselog/csv/`.

Baseline verified today: `.\gradlew.bat check --offline` is green in 55 s (107 domain
tests, 41 app tests per variant, lint clean, permission ratchet reports exactly
`POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED`).

---

## 1. Where we are

**Done.** Chunk 0 skeleton and permission ratchet (`4b30aa4`), chunk 1 domain primitives
(`15d79e3`), chunk 2 consumption engine (`97e99c6`), chunk 3 Room data layer (`bccbe63`),
and the chunk-4 validation delivered early (`6b9eb81`: `EntryValidation`, `FillUpDraft`,
`ExpenseDraft`, `EntryContext`, `ValidationResult.canSave`).

**Not yet built.** No DI container, no prefs, no navigation, no formatters, no screens, no
splash; `Type.kt` has no font family; `strings.xml` holds only `app_name`; `:core:csv` has
no sources; no `stats/` or `reminder/` package in domain; no `MigrationTestHelper` harness;
Compose test dependencies are in the catalog but not wired or locked; configuration cache is
off; the bytecode network scan and the aapt2 release check from the master plan do not exist.

**Deviations already taken that later chunks must honour.**

| Decision (commit) | What the UI must do |
| --- | --- |
| A full tank with no reading is skipped as an anchor, not a chain break (`97e99c6`) | The span across it is `AVERAGED`; say "covers N fill-ups" from `fillUpsSpanned`. `MISSING_ODOMETER` only appears when a whole segment has no readings. |
| A missing odometer warns, never blocks (`6b9eb81`) | `ODOMETER_MISSING` copy: "No consumption figure will come from this entry." Save stays enabled. |
| No `SUSPECT_ODOMETER` / `IMPLAUSIBLE_RESULT` gap reasons (`97e99c6`) | Suspect readings arrive as `OdometerProposal`s (a card); implausible figures are shown with a warning glyph via `Measured.isPlausible`, never replaced by a dash. |
| Lifetime and "recent" figures are sum-over-sum (`ConsumptionSummary`) | Never average bars in the UI. Use `summary.lifetimeAs(format)` and `ConsumptionSummary.recentKmPerUnit(measured, n)`. |
| Proposals are never applied automatically (`Model.kt`) | Every proposal needs a confirm tap: `SUSPECTED_TYPO` → `updateFillUp(copy(odometerM = correctedM))`; `ROLLOVER` → `addSegment(DeclaredSegment(date, ROLLOVER, offsetM))`; `RESET` → `addSegment(..., UNIT_REPLACED or PURCHASED_USED, null)` after the user picks the reason. |
| `missedPrevious` means a fill-up happened BEFORE this one (`Model.kt`) | Exact copy: **"I missed a fill-up before this one"**. |
| Zero cost is silent; only an empty volume/total blocks (`6b9eb81`) | Total paid is required in v1; unit-price entry is out of v1 (decision 7). |
| Master-plan theme hexes supersede the handoff (`Color.kt`: `#F5C518`, `#16181C`) | Recovered vector assets must be recoloured `#F5D211→#F5C518`, `#0D1117→#16181C`. |
| 48 dp touch targets (`Dimens.kt`) | Keep 48, not the handoff's 44. |
| README copy rule | "No internet permission, no account, no ads, no analytics." Never "nothing leaves your device" or "nothing is ever uploaded" (that string is in the design HTML footer; replace it). |
| Unreadable rows are read-only (`FuelLogRepository.requireEditable` throws) | Show a "Created by a newer version" badge, disable Save, keep Delete. Archive such a vehicle through a targeted `UPDATE` (chunk 4a). |

---

## 2. Sequence

| # | Chunk | Master-plan origin |
| --- | --- | --- |
| 4a | App shell: test wiring, font and tokens, DI, prefs, formatters, nav, common components, Garage list, vehicle editor | first half of chunk 4 |
| 4b | Entry screens: add/edit fill-up and expense (the critical path) | second half of chunk 4 |
| 4c | Onboarding, Settings shell, "What this app collects", region defaults | rest of chunk 4 |
| 5 | Vehicle detail, history, statistics chart, months, month detail, proposal cards, garage figures | chunk 5 |
| 6 | Reminders, in-app only | chunk 6 |
| 7 | CSV export, backup and restore | chunk 7 |
| 8 | CSV import | chunk 8 |
| 9 | Opt-in notifications | chunk 9 |
| 10 | Release polish and the release-only gates | chunk 10 |

**Why this shape.** The master plan's chunk 4 is four screens plus DI, prefs, navigation,
formatters, the font and the shared components; that is three sessions, not one. Entry
screens come before onboarding so the plan's own acceptance test (log a real fill-up, match
the figure on paper) is reached one session earlier; the vehicle editor in 4a creates
vehicle 1 until onboarding exists. Cheap gates that later tests depend on (Compose test
deps, the migration harness, configuration cache) land in 4a. The release-only gates (dex
scan, aapt2 dump, 16 KB alignment) land in chunk 10 with the R8 run, because no shipped
dependency changes after 4a and the lockfile diff review covers that one change. Chunks 5
to 10 keep the master plan's order and reasons: export before import, notifications last.

---

## 3. Chunks

### 4a — App shell, Garage list, vehicle editor

**Goal.** Cold start shows the splash then Garage; "Add vehicle" opens the editor; a saved
vehicle appears with a dash and "Needs two full tanks"; archiving hides it; deleting cascades
after a confirm that names the entry count. `check` runs a Compose test and the migration
harness; the second build reuses the configuration cache.

**Build and test wiring (do first, one commit-worthy step inside the chunk).**
- `app/build.gradle.kts`: `testImplementation(libs.compose.ui.test.junit4)`,
  `testImplementation(libs.compose.ui.test.manifest)`, `testImplementation(libs.androidx.test.espresso.core)`.
  Never `debugImplementation` for these: `androidx.test:core`'s AAR declares
  `REORDER_TASKS` and `verifyDebugPermissions` would fail.
- `gradle/libs.versions.toml`: add `espresso = "3.7.0"` and
  `androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }`.
  Reason: `ui-test-android` 1.11.4 requests espresso-core 3.5.0, which is not in the Gradle
  cache; 3.7.0 and its whole POM graph are.
- `app/gradle.lockfile`: `.\gradlew.bat resolveAndLockAll --write-locks --offline`. The diff
  may only touch `*UnitTest*` configurations. Any `releaseRuntimeClasspath` line is a stop.
- `app/src/test/resources/robolectric.properties`: `sdk=26,34` and `graphicsMode=NATIVE`
  (Robolectric 4.16.1 defaults to LEGACY, which cannot render Compose). Only the API 26 and
  34 `android-all` jars are cached locally, so the matrix stays `[26, 34]` until one online
  fetch adds 33 and 36 (decision 10).
- `app/build.gradle.kts`: `android.sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")`.
  `MigrationTestHelper` in room-testing 2.8.4 loads `<version>.json` from the
  instrumentation context's assets; the `File` parameter of the newer constructor is the
  database file, not the schema directory.
- `app/src/test/java/com/fuelexpenselog/app/data/MigrationTest.kt`:
  `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FuelLogDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())`;
  `createDatabase("m", 1)` then `runMigrationsAndValidate("m", 1, true)`.
- `gradle.properties`: `org.gradle.configuration-cache=true`. `resolveAndLockAll` already
  opts out; `VerifyPermissions` uses providers only, so nothing else should need changing.
- Add a short `CLAUDE.md` at the repo root: the README invariants in one screen, "read
  `git log -5` first", one chunk per session with a long-form commit, the cross-cutting UI
  rules from §4. Every later session reads it for free.

**Theme completion (before any composable, so no screen is re-opened later).**
- Recover `res/font/instrument_sans.ttf` and `res/raw/instrument_sans_ofl.txt` from
  `e0ba646` (`git show e0ba646:app/src/main/res/font/instrument_sans.ttf > ...`); add
  `val InstrumentSans = FontFamily(Font(R.font.instrument_sans, ...))` and set
  `fontFamily` on every style in `Type.kt`. Add the styles the screens need: `figureXxl` 52,
  `figureChart` 40, `figureInput` 26/30, `wordmark` 36, `chip` 13/500, `button` 14/600,
  `chartValue` 10.5, `chartLabel` 10, `delta` 11; figure styles inherit `Figures` (tnum).
- `Color.kt`: add `outlineStrong`, `onPrimarySecondary`, `warningRule`, `chartInactive`,
  `chartEmpty`, and a forced-dark set for the Statistics screen.
- `Dimens.kt`: `entryBar 3`, `toggleWidth 52`, `toggleHeight 28`, `toggleKnob 22×20`,
  `underlineFocused 2`, `underlineResting 1`, `warningRule 3`, `splitBar 8`, `categoryBar 6`,
  `statusDot 7`, `const CHART_BARS = 9`.
- Recover the five vector drawables (`ic_arrow_back`, `ic_chevron_down`, `ic_gauge`,
  `ic_plus`, `ic_warning`) and recolour. No icon library (decision 2).
- Splash with no new dependency (decision 1): recover `splash_icon.xml`, `splash_gauge.xml`,
  `animator/needle_sweep.xml`; `res/values-v31/themes.xml` sets
  `windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon`,
  `windowSplashScreenAnimationDuration=1000`; for API 26-30 a `Theme.FuelLog.Starting`
  whose `windowBackground` is a layer-list (yellow + centred gauge), swapped to
  `Theme.FuelLog` in `MainActivity.onCreate` before `super.onCreate`. Never hold the user
  for the animation.

**App code.**
- `di/AppContainer.kt`: owns `database: FuelLogDatabase` (rebuildable, see chunk 7),
  `repository`, `prefs`, `clock: java.time.Clock`, `zone: () -> ZoneId`. `FuelLogApp.container`
  is lazy. `di/ViewModelFactory.kt` via `CreationExtras` + `SavedStateHandle`.
- `FuelLogRepository`: primary constructor becomes `(database: () -> FuelLogDatabase, now: () -> Long)`
  with the existing `(db, now)` as a secondary constructor so current tests compile; the
  private DAO vals become getters. This is the one-line preparation that lets chunk 7 swap
  the database after a restore without touching ViewModels. Add
  `suspend fun setVehicleArchived(id: Long, archived: Boolean)` backed by
  `VehicleDao.setArchived` (`@Query("UPDATE vehicle SET isArchived = :archived WHERE id = :id")`),
  because `updateVehicle` runs `requireEditable` and an unreadable vehicle must still be
  archivable. Add `fun observeEntryCount(vehicleId): Flow<Int>` over `observeHistory`.
- `data/prefs/AppPrefs.kt`: `getSharedPreferences("fuel-log-settings", MODE_PRIVATE)` (the
  name is pinned by both backup XML files), `callbackFlow` over
  `OnSharedPreferenceChangeListener`. Keys: `onboarding_done`, `consumption_format` (enum
  name, `decodeOr(..., L_PER_100KM)`), `region_country`, `default_distance_unit`,
  `default_volume_unit`, `default_currency`, `reminders_notify`, `last_vehicle_id`,
  `dismissed_proposals` (string set).
- `format/`: `CurrencyFormatter` (`NumberFormat.getCurrencyInstance(locale)` with
  `Currency.getInstance(code)`, fallback `"$code $amount"` for an unknown code),
  `DistanceFormatter`, `ConsumptionFormatter` (null → em dash), `DateFormatter`
  (`DateTimeFormatter.ofLocalizedDate(MEDIUM)`). Domain gets `format/Display.kt` with
  `ConsumptionFormat.decimals()`, `DistanceUnit.wholeUnits(metres)`,
  `Money.perDistanceUnit(unit)` (= `this * (unit.metresPerUnit / 1000.0)` on a cost-per-km value).
- `ui/nav/Routes.kt` with string routes (`garage`, `vehicle/{id}`, `vehicle/{id}/edit`,
  `vehicle/new`, ...); type-safe routes need the serialization plugin the catalog rejects.
  `ui/nav/FuelNavHost.kt`: 220 ms fade-through, back handled per screen.
- `ui/common/`: `NavHeader`, `EntryRow` (3 dp type bar: yellow = fill-up with a figure, grey
  = partial, ink = expense), `StatStrip`, `UnderlineField` (`BasicTextField`,
  `KeyboardType.Decimal`, yellow cursor bar), `SquareToggle` (`Role.Switch`), `Segmented`
  (`Role.RadioButton`, `heightIn(48.dp)`), `ChipGroup`, `ActionBar` (split 50/50 or
  standalone, 56 dp, 1 dp ink top border), `WarningBlock` (polite live region),
  `SunkenPanel`, `DashOr`.
- `ui/garage/GarageScreen.kt` + `GarageViewModel.kt` (list only; figures come in chunk 5).
  `ui/vehicles/VehicleEditorScreen.kt` + `VehicleEditorViewModel.kt`: name, Distance 2-up,
  Volume 3-up from `EnergyUnit.liquid`, Currency dropdown from
  `Currency.getAvailableCurrencies()`, tank capacity optional, type, fuel type, default tag,
  "Consumption shown as: App default / ..." from `ConsumptionFormat.forKind(LIQUID)`,
  Active toggle = `!isArchived`, delete row, unreadable badge. Never construct a `Vehicle`
  with `volumeUnit = KWH` (the `init` block rejects it).
- Every visible string goes into `res/values/strings.xml` from this chunk on.

**Tests.** `MigrationTest` (above). `GarageScreenTest` (Compose under Robolectric: one
vehicle shows its name and "Needs two full tanks") proves the harness before the critical
path depends on it. `VehicleEditorViewModelTest`: changing `distanceUnit` KM→MILE leaves
every stored `odometerM` and `energyMicro` byte-identical (the master plan's mandated test);
an unreadable vehicle has `canSave = false` and `setVehicleArchived` still succeeds.
`AppPrefsTest` (unknown format name → `L_PER_100KM`; a change emits). `FormattersTest`
(`de-DE` grouping, unknown currency code). `GarageViewModelTest` (archived rows filtered;
empty → onboarding needed).

**Traps.** `observeVehicles()` includes archived rows; filter in the ViewModel. `addVehicle`
overwrites `createdAtMillis`. `ZoneId` comes from the container only. No fixed row heights;
`IntrinsicSize.Min` and sp everywhere. Do not copy Kotlin from `e0ba646`.

**Size.** ~30 files, ~8 tests. If the session runs short, defer the vehicle editor to the
start of 4b.

### 4b — Entry screens (critical path)

**Goal.** Log a real fill-up end to end. Smoke test with numbers: vehicle in km/L, entries
`48200 / 40 L / 60.00` then `48700 / 41.5 L / 62.00` must show **8.3 L/100 km** (500 km over
41.5 L; 12.05 km/L; 28.3 MPG US). Then `48100` must warn "lower than the last reading" and
still save. Rotate mid-entry and kill the process: the draft survives.

**Domain and data additions.**
- `validate/EntryPreview.kt`: `fun fillUpPreview(existing: List<FuelEvent>, draft: FuelEvent, kind: EnergyKind, displayUnit: DistanceUnit): Measured?`
  = `ConsumptionEngine.compute(existing + draft, kind, emptyList(), displayUnit).measured.lastOrNull { it.endEventId == draft.id }`
  with the draft's id fixed at `-1L`. The preview runs the real engine on every keystroke
  so it can never disagree with the saved figure. Do **not** compute `(odometer − previous) / volume`
  anywhere: that is the naive formula the engine commit rejects, and `currentOdometer`
  includes expense readings and ignores partial fills.
- Repository: `suspend fun fillUp(id): FillUp?`, `suspend fun expense(id): Expense?`,
  `suspend fun previousOdometer(vehicleId, excludingFillUpId = 0, excludingExpenseId = 0): Long?`
  over new `FillUpDao.maxOdometerExcluding` / `ExpenseDao.maxOdometerExcluding`
  (`WHERE vehicleId = :v AND id != :id`, still on the `(vehicleId, odometerM)` index; add
  an `EXPLAIN` assertion).

**Files.** `ui/entry/FillUpEditorViewModel.kt` (`SavedStateHandle`-backed text state for
odometer, volume, total, note, station; `uiState` with `warnings`, `canSave`, `preview`,
`previousReadingHint`, `isEditable`, `tag`, `isFull`, `missedPrevious`, `date`),
`FillUpEditorScreen.kt`, `ExpenseEditorViewModel.kt`, `ExpenseEditorScreen.kt`,
`DateRow.kt` (M3 `DatePickerDialog`; inherits the zero-radius theme), `TagSegmented`
(Personal / Business, default `vehicle.defaultTag`), a "More" disclosure holding the
missed-fill toggle ("I missed a fill-up before this one"), note and station. Strings for
every `EntryWarning` and `GapReason`.

**ViewModel flow.** `FillUpDraft(odometerText, volumeText, totalText, isFull, date, vehicle.volumeUnit, vehicle.distanceUnit)`;
`EntryContext(today = CivilDate.today(zone()), previousOdometerM = repo.previousOdometer(vehicleId, excludingFillUpId = editingId), tankCapacity = vehicle.tankCapacity, looksLikeDuplicate = odometerM?.let { repo.looksLikeDuplicate(vehicleId, instant, it, editingId) } ?: false)`;
`EntryValidation.validate`. Save: `DecimalParser.parseOrNull(volumeText, VOLUME)` →
`Energy.of(vehicle.volumeUnit, v)`; `MONEY_TOTAL` → `Money.of(t, currency)` where currency
is the row's own on edit and the vehicle's on add; `EntryValidation.odometerMetres`;
`FillUp(energyUnitEntered = vehicle.volumeUnit, unitPrice = null, ...)` → `addFillUp` /
`updateFillUp`. Expense: `ExpenseDraft`, chips in `ExpenseCategory.chipOrder`.

**Tests.** `FillUpEditorViewModelTest` (Robolectric, in-memory DB): below-previous →
`ODOMETER_LOWER_THAN_PREVIOUS` present, `canSave` true, save persists; empty total →
`canSave` false; editing the latest row and lowering its own reading produces no warning;
saving with `missedPrevious = true` yields `Gap(MISSED_FILL_UP)` containing the new id and
the previous span survives; preview equals `Measured.shownAs(format)` after save; all four
warnings at once still saves. `ExpenseEditorViewModelTest`: empty amount blocks; chip order
equals `chipOrder`; odometer optional. `FillUpEditorScreenTest` (Compose): type a lower
odometer, assert the warning text is displayed **and** "Save fill-up" is enabled, click,
assert one row exists (the master plan's mandated test).

**Traps.** Never prefill the odometer; the hint reads "Last reading 83,898".
`looksLikeDuplicate` needs a parsed odometer. Never `String.toDouble()`; `DecimalParser`
only. No success toast. Warnings must not move focus. The 56×56 "+" is outlined, not yellow.

**Size.** ~12 files, ~12 tests.

### 4c — Onboarding, Settings shell, region defaults

**Goal.** Fresh install: Country → Units → First vehicle → Garage. Settings changes
"Consumption shown as" and every figure re-renders. "What this app collects" opens.

**Domain.** `region/RegionDefaults.kt`: `data class RegionDefaults(countryCode, distanceUnit, volumeUnit, consumptionFormat, currencyCode)`;
`object Regions { val seed /* US, GB, CA, AU, DE, IN */; val all /* Locale.getISOCountries() + Currency.getInstance(Locale("", iso)) */; fun forCountry(iso2): RegionDefaults }`.
Miles + US gallon for US, LR, MM; miles + litres for GB (decision 8); metric elsewhere.
Format: US → `MPG_US`, GB → `MPG_UK`, IN → `KM_PER_L`, else `L_PER_100KM`. Countries with no
currency fall back to `USD`.
`:app` `format/LocaleDefaults.detect(locale): RegionDefaults`: country from the locale;
`android.icu.util.LocaleData.getMeasurementSystem(ULocale.forLocale(locale))` only when the
country is absent from the table. Device-local only; the eyebrow says "Read from your
phone, not the internet".

**Files.** `ui/onboarding/CountryScreen.kt` (search over `Regions.all`, seed six first,
sunken "Selected · ..." strip), `UnitsScreen.kt` (detected block + US / UK / Metric
alternates), `FirstVehicleScreen.kt` (reuses `VehicleEditorViewModel` with defaults `CAR`,
`PETROL`, `energyUnit = KWH`, `consumptionFormat = null`, `defaultTag = PERSONAL`; the
"Import a CSV" link appears in chunk 8), `OnboardingViewModel.kt`;
`ui/settings/SettingsScreen.kt` (4-up format, distance and currency defaults, Region row,
"Your data" rows disabled until chunks 7 and 8, "What this app collects", privacy footer
with the README copy), `SettingsViewModel.kt`, `CollectsScreen.kt`. NavHost start
destination depends on `prefs.onboardingDone`.

**Tests.** `RegionDefaultsTest` (domain: six seeds exact; unknown code → metric,
`L_PER_100KM`). `LocaleDefaultsTest` (Robolectric: `en-US`, `en-GB`, `de-DE`, `en-IN`).
`OnboardingViewModelTest`: finishing writes the vehicle with the chosen units and the prefs.
`SettingsViewModelTest`: a format change reaches `Vehicle.formatFor(LIQUID, appDefault)` for
a vehicle with `consumptionFormat = null` and not for one set to `MPG_UK`.

**Traps.** A region change touches no existing vehicle. **Size.** ~10 files, ~8 tests.

### 5 — Vehicle detail, history, statistics, months

**Goal.** With the golden dataset entered by hand, Vehicle shows the latest figure and the
average, the missed span as a dash with "A fill-up in this stretch was not recorded";
Statistics renders nine slots with stubs for gaps; Months lists only months with entries; a
typed odometer dip produces a proposal card whose confirm changes the figure.

**Domain, `stats/`.** `MonthBucket(month: MonthKey, entryCount, fillUpCount, fuel: List<Money>, other: List<Money>, total: List<Money>, distanceM: Long?)`;
`Delta(percent, improved)`; `CategoryBreakdown(category, count, subtotal: List<Money>)`;
`LastDone(category, date, odometerM, distanceAgoM)`;
`object StatsCalculator { months(entries); moneyDelta(current, previous); consumptionDelta(current, previous, format) /* inverts on lowerIsBetter */; categories(entries, month); yearTotals(buckets, year); distanceDriven(entries, month) /* max − min odometer in the month, null without readings */; lastDone(entries, category, currentOdometerM); recent(timeline, n) /* last n slots incl. gaps, plus the sum-over-sum header of the Measured among them */ }`.
Months with no entries are omitted, never zero. All money through `CurrencySubtotals.of`.

**`ui/chart/ChartLayout.kt`** (pure, no Compose imports):
`compute(points: List<TimelinePoint>, format, widthPx, heightPx): ChartGeometry`: last
`CHART_BARS` slots, bar height scaled against the max, a `Gap` is a 3 px stub with an em
dash label, latest highlighted. The `Canvas` block holds draw calls only.

**Repository.** `observeAllHistory(): Flow<List<Pair<Vehicle, HistoryEntry>>>` via
`observeVehicles().flatMapLatest { vs -> if (vs.isEmpty()) flowOf(emptyList()) else combine(vs.map { v -> observeHistory(v.id).map { v to it } }) { ... } }`.
The empty-list guard is required: `combine` over zero flows never emits, and the Garage
year panel would never render for a new user. Also `observeSegments(vehicleId)` and
`deleteSegment(id)` with `OdometerSegmentDao.deleteById`.

**Files.** `ui/vehicle/VehicleScreen.kt`, `VehicleViewModel.kt`
(`observeVehicle(id).filterNotNull().flatMapLatest { observeConsumption(it) }.flowOn(Dispatchers.Default)`),
`ProposalCard.kt` (actions per §1; dismissal key `"v$vehicleId:e$eventId:$anomaly:$recordedM"`
in prefs, so an edited reading changes the key); `ui/history/HistoryScreen.kt` (tag filter;
yellow bar iff the id is some `Measured.endEventId`); `ui/stats/StatisticsScreen.kt` (forced
dark via a theme wrapper), `ConsumptionChart.kt`; `ui/months/MonthsScreen.kt` (year picker),
`MonthDetailScreen.kt`. Garage gains the sparkline, this/last month, cost per distance
(`costPerKm().perDistanceUnit(unit)`), the year panel, and the mixed-currency variant with
the exact copy "Not added together. The app holds no exchange rates and will not invent one
offline."

**Tests.** `StatsCalculatorTest` (domain): mixed currency yields two subtotals and never a
sum (mandated); empty months omitted; `L_PER_100KM` delta inversion; same month last year
via `MonthKey.minusYears(1)`; `distanceDriven` null without readings. `ChartLayoutTest`
(mandated): nine slots, a `Gap` is a stub not a zero bar, max scaling, one highlighted.
`VehicleViewModelTest` (golden data through the DB): headline, `AVERAGED` label carries
`fillUpsSpanned`, gap copy keyed by reason. `ProposalActionsTest`: typo confirm calls
`updateFillUp` with `correctedM`; rollover confirm calls `addSegment(ROLLOVER, offsetM)`;
a dismissed proposal stays hidden on re-emission. `StatisticsScreenTest` (Compose): a gap
slot shows "—" and its reason, not "0" (mandated).

**Traps.** `lifetimeAs` and `ConsumptionFormat.of` throw on a kind mismatch; always go
through `vehicle.formatFor(LIQUID, appDefault)`. `Measured.cost == null` means mixed
currencies **or** no cost recorded; show a dash for cost and keep the figure. Bars animate
once per screen entry (`Animatable` keyed on entry), never on data change; no `delay()`
stagger. A rollover offset applies to every event with `date >= startsAt`; use the anomalous
event's date and test the same-day case.

**Size.** ~18 files, ~16 tests.

### 6 — Reminders, in-app

**Goal.** Create "Oil change every 6 months or 10,000 km"; Vehicle shows it Due soon or
Overdue; a vehicle with no reading shows a dash; completing it logs an expense and advances
the due date from the anchor.

**Domain, `reminder/` (designed once; chunk 9 adds only Android plumbing).**
`enum ReminderKind { DATE, DISTANCE, BOTH }`; `data class Reminder(...)` mirroring
`ReminderEntity` with `CivilDate` fields; `sealed interface ReminderStatus { Ok; DueSoon(daysLeft, metresLeft); Overdue(daysOver, metresOver); Unknown }`;
`object ReminderEvaluator { evaluate(reminders, latestOdometerByVehicle: Map<Long, Long?>, today): List<Pair<Reminder, ReminderStatus>>; advance(reminder, completedOn, completedOdometerM): Reminder /* anchor.plusMonths(repeatMonths), anchorOdometerM + repeatDistanceM */; shouldNotify(status, lastNotified, today): Boolean }`.
`BOTH` reports the worse of the two; a distance reminder with no odometer is `Unknown`,
never `Overdue`.

**Data.** `data/repo/ReminderMappers.kt`; `ReminderDao.observeAllActive()`; repository
`observeReminders(vehicleId)`, `observeAllActiveReminders()`, `reminder(id)`, `addReminder`,
`updateReminder`, `deleteReminder`, `completions(id)`, `notifiableReminders()`,
`markNotified(id, date)`, and `suspend fun completeReminder(id, on, odometerM, expense: Expense?)`
inside `db.withTransaction` (insert completion, optional `addExpense` with `reminderId`,
update the advanced reminder). Add the `EXPLAIN` assertion for the
`(isActive, notifyEnabled, dueLocalDate)` index now.

**Files.** `ui/reminders/RemindersScreen.kt`, `ReminderEditorScreen.kt` (3-up kind,
underline inputs; undesigned, tokens only), `RemindersViewModel.kt`; due rows on Vehicle; a
badge on Garage.

**Tests.** `ReminderEvaluatorTest` (domain, mandated): DATE / DISTANCE / BOTH thresholds;
`Unknown` not `Overdue`; `31 Jan + 6 → 31 Jul`; `31 Aug + 6 → 28 Feb 2027` and `29 Feb 2028`;
`shouldNotify` false when `lastNotified == today`. `ReminderRepositoryTest`: completion +
expense + advance in one transaction; a failure inside the transaction (use a DAO that
throws, not an invalid `Money`, whose `init` throws before any repository call) rolls the
completion back; vehicle delete cascades. `RemindersViewModelTest`: dash for `Unknown`.

**Size.** ~12 files, ~14 tests.

### 7 — CSV export, backup and restore

**Goal.** Export a month or year (with tag filter) through the system picker and open it in
a spreadsheet; write a `.fuellogbak`, clear app data, restore it, every figure returns;
restoring a file with a newer schema is refused with a message.

**`:core:csv`.** `CsvWriter` (RFC 4180, CRLF, quoting, optional BOM);
`export/FuelLogCsvExporter.write(vehicles, fillUps, expenses, out: Appendable)`:
`Locale.ROOT`, `Rounding.toPlainString`, `CivilDate.toString()`, enum names, values in the
row's entered unit plus canonical columns, and a `typed_field` column saying whether total or
unit price was typed. `ImportRowHash.fillUp(vehicleId, date, odometerM, energyMicro, totalMicros)`
(SHA-256 over a canonical string) is defined **here**, so chunk 8 cannot drift from it.
`CsvModulePurityTest` like the domain one.

**`:app`.** `transfer/SafGateway.kt` (`Uri → InputStream / OutputStream`, nothing else),
`transfer/ExportCoordinator.kt` (`CreateDocument("text/csv")`), `backup/BackupWriter.kt`
(`java.util.zip`; `manifest.json` via `android.util.JsonWriter` with `schemaVersion`,
`appVersion`, row counts; the database copied after `PRAGMA wal_checkpoint(TRUNCATE)`,
asserting the busy column is 0), `backup/BackupReader.kt` (unzip to cache, open read-only,
read `PRAGMA user_version`, refuse if greater than the app's schema version, then close the
live database, delete `.db`, `.db-wal`, `.db-shm`, copy the file in, rebuild the database in
`AppContainer`, and re-key the `NavHost` on a `restoreEpoch` so every ViewModel and collected
Flow is recreated against the new instance). Repository: `allSegments()`, `allReminders()`,
`allCompletions()`, `allImportBatches()`, `fillUpsInRange`, `expensesInRange`,
`taggedFillUpsInRange`, `taggedExpensesInRange`. Settings "Your data": Export, Back up,
Restore (confirm: replaces everything). Month detail: "Export month".

**Tests.** `CsvWriterTest` golden files under `core/csv/src/test/resources/golden/` (quotes,
newline in a note, CRLF, BOM) (mandated). `FuelLogCsvExporterTest`: the golden dataset →
a checked-in expected CSV; under `Locale.setDefault(ar-EG)` the output is ASCII.
`BackupRoundTripTest` (file-backed `Room.databaseBuilder`, mandated): golden rows + a
segment + a reminder + a completion, back up to a `ByteArrayOutputStream`, delete every
row, restore, assert identical rows including ids; a manifest with `schemaVersion + 1` is
refused; the checkpoint busy flag is 0. `ImportRowHashTest`: golden value.

**Traps.** Delete `-wal` and `-shm` before copying in; close before swapping; never
`toLocalisedString` in a file; no storage permission and no `documentfile` dependency.

**Size.** ~14 files, ~10 tests.

### 8 — CSV import

**Goal.** Import a Fuelio, aCar, Drivvo and a `;`-delimited German generic file; ambiguous
dates ask with an example row; re-importing the same file excludes duplicates by default;
"Undo import" removes exactly that batch.

**`:core:csv`.** `CharsetSniffer` (BOM, strict UTF-8, ISO-8859-1 fallback surfaced in the
preview), `DelimiterSniffer` (honours `sep=;`), `CsvTokenizer`; `imprt/ColumnSpec`,
`Dialect` as data tables of alias sets (Fuelio `## ` sections split first; aCar units in
header text and the inverted "Partial Fill-up"; Drivvo multilingual headers; Generic manual
mapping), `DialectDetector.score(header, firstRows)` with a threshold and tie → Generic,
`DateFormatResolver` (100 % rule → any day > 12 → odometer monotonic → `Ambiguous(exampleRow)`),
`StagedImport.stage(table, assumptions, vehicle): List<StagedRow>` (per row a fill-up or
expense candidate, warnings, errors via `RejectReason`, `duplicate`). `missedPrevious`
mapped with the Model.kt direction; `isFull = !partial` for aCar; unit-price-only rows keep
`unitPrice` with `total = null`.

**`:app`.** `Mappers.kt`: `FillUp.toEntity(now, createdAt, import: ImportStamp? = null)` and
the same for `Expense`, where `ImportStamp(source, batchId, rowHash)` replaces the hard-coded
nulls. Stay on schema v1: `ExpenseDao.countByRowHash(vehicleId, hash)` uses the existing
`(vehicleId, ...)` index prefix, which is fine at a few thousand rows. Repository:
`commitImport(source, fileName, vehicleId, fillUps, expenses): Long` in `db.withTransaction`
(batch row first, then `insertAll`), `isDuplicateFillUp(hash)`, `isDuplicateExpense(vehicleId, hash)`,
`observeImportBatches()`. `ui/importflow/ImportPreviewScreen.kt` (assumptions list, All /
Warnings / Duplicates / Errors filter, per-row toggles, "Import N"); Settings "Undo import:
Fuelio, 412 rows, 14 Sep"; the First-vehicle "Import a CSV" link.

**Tests.** Synthetic fixtures under `core/csv/src/test/resources/fixtures/` for fuelio, acar,
drivvo, generic and german-semicolon, labelled best-effort (no real export files exist;
decision 5). `DialectDetectorTest` confusion matrix (mandated). `DateFormatResolverTest`
asks on data that is only ever `01/02/2026`-style (mandated). `StagedImportTest` (aCar
inversion, missed-fill direction, `;` with `32,5`). `ImportCommitTest`: atomic (a failing
row leaves no batch), undo removes exactly the batch, re-import excludes duplicates,
hand-typed history (null hashes) is probed via `countSimilar`. `ExportImportRoundTripTest`:
the app's own export re-imports through the generic dialect losslessly.

**Size.** ~22 files, ~22 tests.

### 9 — Opt-in notifications

**Goal.** Turn reminders on in Settings (API 33+ asks for `POST_NOTIFICATIONS` at the
toggle); an overdue reminder notifies once; reboot re-arms; revoking shows a banner without
flipping the toggle.

**Files.** `notify/NotificationGate.kt`, `ReminderScheduler.kt`
(`AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP)`, one alarm for the next daily check),
`DailyCheckReceiver.kt` (`exported="false"`), `BootReceiver.kt` (`BOOT_COMPLETED` filter,
`android:enabled="false"` in the manifest and enabled with
`PackageManager.setComponentEnabledSetting` when the toggle turns on, so a user who never
opts in has no boot-time work), the channel created lazily on first enable,
`NotificationCompat` from `androidx.core` 1.18.0 with `FLAG_IMMUTABLE` pending intents.
Reuses `ReminderEvaluator.shouldNotify`, `notifiableReminders()`, `markNotified`. Settings
toggle; Garage banner linking `ACTION_APP_NOTIFICATION_SETTINGS` when
`remindersEnabled && !areNotificationsEnabled()`.

**Tests** (still `[26, 34]`; 26 and 34 straddle the API 33 boundary): `ReminderSchedulerTest`
(`ShadowAlarmManager`: exactly one pending alarm, re-armed after firing, mandated);
`DailyCheckReceiverTest` (`ShadowNotificationManager`: firing twice in a day posts once,
mandated); `NotificationGateTest` (SDK 26 needs no runtime request; SDK 34 denied; granted
then revoked → banner state, mandated). `verifyDebugPermissions` must still pass with the
receivers in the manifest.

**Traps.** No WorkManager. No channel in `Application.onCreate`. Never flip the stored
toggle on revocation. **Size.** ~9 files, ~8 tests.

### 10 — Release polish and release-only gates

- `verifyReleaseNoNetworkCode` in `app/build.gradle.kts`: a task over
  `SingleArtifact.APK` (via `builtArtifactsLoader`) that opens each APK with `ZipFile`, reads
  every `classes*.dex`, and fails on the byte sequences `Ljava/net/`, `Ljavax/net/`,
  `Lokhttp3/`, `Lretrofit2/`, `Landroid/webkit/`, `Landroid/net/ConnectivityManager`,
  `Landroid/net/Network`, `Landroid/net/http/`, `Lcom/google/firebase/`,
  `Lcom/google/android/gms/`; `Landroid/net/Uri;` allowed. Release only: the debug dex
  legitimately contains `java/net` strings from the stdlib. If R8 keeps a `java/net`
  reference the scan names it and the allowlist gets an explicit entry with a reason.
- `verifyReleaseApkPermissions`: `aapt2 dump permissions` (find `aapt2.exe` under the SDK's
  `build-tools/<version>/`) diffed against the same allowlist as the manifest ratchet.
- `verifyReleaseAlignment`: `zipalign -c -P 16 4` (16 KB page alignment for targetSdk 36).
- Prove each gate fails once (inject `java.net.URL("http://x").host` into `MainActivity`,
  watch the scan fail, revert), then wire all three into `check` and record it in the commit.
- Dark-mode audit through `FuelTheme.colors` only; font-scale 2.0 Compose test
  (`LocalDensity(fontScale = 2f)`: rows and strips do not clip; `TextAutoSize` on the 72 sp
  headline); RTL pass (`autoMirrored` on `ic_arrow_back`); `lint { error += "HardcodedText" }`;
  a plain-JUnit `StringsTest` that fails on "leaves your device" or "uploaded" in
  `strings.xml`; splash verified at animator scale 0 (static frame).
- Store assets regenerated in `#F5C518` / `#16181C` at 1080×1920; README gains the gate
  list; `versionCode` 1, `versionName` 1.0.0; final `check assembleRelease`.

**Size.** ~10 files touched, 3 tests.

---

## 4. Cross-cutting rules from chunk 4a onward

- Every user-visible string is an `R.string` (with plurals); no Kotlin literals.
- Every figure style carries `tnum`; new styles inherit `Figures`.
- One yellow action per screen; yellow is never text on a light surface; `onPrimary` never inverts.
- Warn, never block: `canSave` is the only thing that disables Save.
- A dash, never a zero: `null`, `Gap` and `summary == null` render an em dash with the
  reason; `Measured.cost == null` renders a dash beside a valid figure.
- Money only through `CurrencySubtotals.of` / `singleCurrencyTotal`; `Money.plus` throws
  across currencies, so group first.
- Copy: "No internet permission, no account, no ads, no analytics." `missedPrevious` copy
  is exactly "I missed a fill-up before this one".
- Animations via `tween` / `Animatable` only (compose-ui 1.11.4 honours
  `ANIMATOR_DURATION_SCALE`); nothing waits on an animation.
- 48 dp targets: icon buttons are 48 dp boxes with 20 dp glyphs; chips and segments
  `heightIn(48.dp)`; the whole toggle row is the target.
- Unreadable rows: "Created by a newer version" badge, Save disabled, Delete kept; wrap
  every save in `runCatching` and show the message.
- The current odometer is a hint under an empty field, never a prefill.
- `vehicle.formatFor(kind, appDefault)` is the only route to a `ConsumptionFormat`.
- `DecimalParser` for input, `Rounding.toLocalisedString` / `NumberFormat(locale)` for
  display, `Locale.ROOT` and `toPlainString` for files.
- `ZoneId` and the clock come from `AppContainer`; nothing in domain calls `systemDefault()`.
- Content descriptions on every glyph-only button; `Role.Switch` / `Role.RadioButton`;
  the warning block is a polite live region.

---

## 5. Gates: what runs when

| Gate | Built in | Runs from then on |
| --- | --- | --- |
| Permission ratchet on the merged manifest | done | every `check` |
| Lockfile diff review (unit-test configurations only) | 4a | the only dependency change planned |
| Compose tests under Robolectric (`graphicsMode=NATIVE`) | 4a | 4b, 5, 10 |
| `MigrationTest` harness on `app/schemas` as test assets | 4a | every `check`; first real migration whenever one is needed |
| Configuration cache | 4a | every build |
| Backup round trip on a file-backed DB | 7 | every `check` |
| `verifyReleaseNoNetworkCode`, `verifyReleaseApkPermissions`, `verifyReleaseAlignment` | 10 | every `check` (forces the R8 run) |

---

## 6. Open decisions, with recommendations

1. **Splash without `core-splashscreen`.** Use `values-v31` attributes plus a legacy
   `windowBackground` theme for API 26-30. Saves a shipped dependency and a lock
   regeneration; the backport only adds `setKeepOnScreenCondition`, which the handoff does
   not want anyway.
2. **Icons.** Recover the five vectors from `e0ba646`; no icon library. The app needs about
   six glyphs.
3. **Instrument Sans.** Bundle it (194 KB, OFL, has `tnum`). The handoff mandates it and
   `Type.kt` is the one place to set it. Do it in 4a so no screen is re-opened later.
4. **Months year picker.** In; `MonthKey` arithmetic makes it free.
5. **CSV fixtures without real export files.** Ship synthetic fixtures labelled best-effort,
   bias the detector threshold toward Generic manual mapping, and record in the chunk-8
   commit that the alias sets are unverified. Ask for real aCar / Drivvo / Fuelio exports
   before release.
6. **Boot receiver.** Declared only from chunk 9, disabled by default, enabled on opt-in.
7. **Unit-price entry.** Out of v1. `FillUpDraft` stays total-only; unit price shows as
   derived; import preserves unit-price-only rows. Adding it later is a domain-only change.
8. **UK default volume unit.** Litres with `MPG_UK`, not the handoff's UK gallons: UK pumps
   sell litres and `MPG_UK` works with either. The Units screen still offers UK gallons.
9. **Schema stays at v1 through release.** Expense duplicate probes use the existing index
   prefix rather than a new index; the harness proves v1 creates and validates. The first
   real migration is written when there is a reason for one.
10. **Robolectric 33 and 36.** Fetch the `android-all` jars once when online, then widen
    `robolectric.properties` to `26,33,34,36`; until then `[26, 34]`.
11. **Database swap after restore.** Rebuild the instance in `AppContainer` and re-key the
    `NavHost`, rather than threading a `StateFlow<FuelLogDatabase>` through the repository.
    Simpler, and the repository already takes a provider lambda from 4a.
12. **"Last 9" on the chart.** The last nine timeline slots including gaps, with a header
    that is sum-over-sum of the `Measured` among them, so header and bars describe the same
    data. Copy: "last 9 spans".

---

## 7. Definition of done and release checklist

**Done when** all chunks are committed; `.\gradlew.bat check assembleRelease` is green with
every gate; the smoke tests of 4a-9 pass on the release build on API 26 and 34 emulators
(fresh install → onboarding → the 8.3 L/100 km check → stats → reminder → export →
backup/restore → import → notification); animator-scale-0, font-scale 2.0, RTL and dark-mode
passes done; `aapt2 dump permissions` shows exactly the two permissions; the release dex
scan is clean; `releaseRuntimeClasspath` in the lockfile is unchanged since chunk 3.

**Release checklist.** Signing config outside the repo (keystores are already ignored);
Play Data Safety form: no data collected, no data shared, deletion via uninstall or in-app
delete, Auto Backup described as system backup, never as collection; store title
"Fuel Log: MPG & Car Expenses"; short description "Track fuel, mileage & car costs. No
account, no ads, works fully offline."; long description leads with "no internet
permission" and names aCar, Drivvo and Fuelio import; screenshots regenerated from the
shipped build in the plan palette; README updated with the gate list.
