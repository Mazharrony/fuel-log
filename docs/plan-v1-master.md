# Fuel Log — MPG & Car Expenses · v1 Build Plan

## Context

`D:\All Source Codes\Fuel Log` is empty on disk. Git holds six abandoned commits from a
previous attempt; **you chose to start completely fresh**, so none of it is restored or
copied. It stays in history as reference only.

We are building an Android app, package `com.fuelexpenselog.app`, that logs fuel fill-ups
and other vehicle costs for cars, bikes and vans. It targets EU / UK / USA first but works
worldwide. The users are individuals, sole traders (delivery riders, rideshare drivers) who
need defensible records at tax time, and small companies. First launch asks for country,
which sets currency and units. History is organised by month. The app works entirely
offline with no account, no ads and no analytics.

One thing to be clear about up front: the discarded history contained a tested consumption
engine. Rewriting it is the single biggest cost in this plan and the place most likely to
produce subtly wrong numbers. Chunks 1–2 exist to retire that risk before any UI is built.

---

## Decisions locked in

| Decision | Choice |
| --- | --- |
| Prior code | Not reused. Fresh build. |
| Income tracking | Out. Expenses only. |
| Vehicles | Multiple, each with own units + currency |
| Business/personal | Every entry tagged, filterable and exportable |
| EV / hybrid | Schema ready for kWh now; v1 UI exposes liquid fuel only |
| Expense categories | Fixed 12, stored by name |
| Reminders | In-app rows by default; notifications are an opt-in toggle |
| Permissions | `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED`. No INTERNET. |
| Auto Backup | **Left on** (your call) |
| CSV | Export **and** import (aCar, Drivvo, Fuelio, generic) |
| Backup | `.fuellogbak` zip via the system file picker |
| Theme | Yellow + charcoal, light and dark |

### Consequence of leaving Auto Backup on

The system copies the database to the user's Google Drive without the app holding INTERNET.
On Android 9+ it is end-to-end encrypted with a key derived from the device lockscreen, so
Google cannot read it — this is a defensible position. Two obligations follow:

1. `backup_rules.xml` and `data_extraction_rules.xml` must list `fuel-log.db` **plus its
   `-wal` and `-shm` siblings**. Room runs in WAL mode; backing up the main file alone can
   capture a snapshot missing recent entries.
2. Store and in-app copy must say *"no internet permission, no account, no ads, no
   analytics"* and must **not** say *"nothing ever leaves your device."* The first is true
   and checkable; the second would not be.

---

## Architecture

Three Gradle modules. The two `kotlin("jvm")` modules are the point: in them,
`import android.*` is a **compile error**, so the riskiest logic is structurally forced to
stay pure and fast to test.

```
:app          com.android.application   Room, Compose, SAF, alarms, DI
:core:domain  kotlin("jvm")             engine, units, money, dates, validation
:core:csv     kotlin("jvm")             tokenizer, dialects, export
```

```
com.fuelexpenselog.app                         (:app)
├─ FuelLogApp.kt · MainActivity.kt
├─ di/            AppContainer, ViewModelFactory      (manual DI, ~120 lines)
├─ data/
│  ├─ db/         FuelLogDatabase, Converters, entity/, dao/
│  ├─ prefs/      AppPrefs (SharedPreferences, Flow-backed)
│  └─ repo/       Vehicle, Entry, Reminder, Stats repositories
├─ backup/        BackupWriter, BackupReader
├─ transfer/      SafGateway (Uri→Stream only), Export/Import coordinators
├─ notify/        NotificationGate, ReminderScheduler, DailyCheckReceiver, BootReceiver
├─ format/        CurrencyFormatter, DateFormatter, LocaleDefaults (android.icu)
└─ ui/            theme/ common/ chart/ nav/ onboarding/ vehicles/ entry/
                  history/ stats/ reminders/ settings/ importflow/

com.fuelexpenselog.domain                      (:core:domain)
  model/ unit/ money/ parse/ time/ consumption/ stats/ reminder/ validate/

com.fuelexpenselog.csv                         (:core:csv)
  CsvTokenizer, CsvWriter, DelimiterSniffer, CharsetSniffer
  export/  imprt/ (dialects, DialectDetector, DateFormatResolver, StagedImport)
```

Manual DI, not Hilt: the graph is ~15 nodes and Hilt costs a Gradle plugin, a KSP processor
and generated code to audit.

---

## Data model

**No `REAL` columns anywhere.** Floats produce comparison bugs in the engine and lossy CSV
round-trips.

- Distance → `Long` **metres**
- Energy → `Long` **micro-units** (µL or µkWh, per `energy_kind`)
- Money → `Long` **micros** (1e-6). Fuel prices carry 3+ decimals: €1.459/L, $3.459⁹⁄₁₀/gal.
- Dates → `Long` epoch millis **and** `Int` `yyyymmdd`

### Why `yyyymmdd` for the date

Store the civil date the user actually typed. Month is `/100`, year is `/10000`, ranges are
integer `BETWEEN`, and it indexes perfectly. Crucially it is **timezone-free forever** — a
user who flies Tokyo→London never sees entries hop between months, and historical UTC offset
changes cannot rewrite their history. `ZoneId` is used in exactly one place: deciding what
"today" is in the reminder evaluator.

### `vehicle`

`id`, `name`, `type` (CAR/BIKE/VAN/OTHER), `fuel_type`, `distance_unit` (KM/MILE),
`volume_unit` (LITRE/US_GALLON/IMP_GALLON), `energy_unit` (default KWH, unused in v1),
`consumption_format` **nullable** (null = inherit app default), `currency_code`,
`tank_capacity_micro?`, `battery_capacity_micro?`, `default_tag` (BUSINESS/PERSONAL),
`make?`, `model?`, `plate?`, `model_year?`, `notes?`, `sort_order`, `is_archived`,
`created_at_millis`.

Index: `(is_archived, sort_order)` — covers the only query, the garage list.

`consumption_format` is per-vehicle-with-fallback because a UK car and a US car in one
garage want MPG(UK) and MPG(US). One nullable column now; a painful retrofit later.

### `fill_up` — the EV-ready table

`id`, `vehicle_id` (FK CASCADE), `occurred_local_date`, `occurred_at_millis`,
`odometer_m?`, **`energy_kind`** (LIQUID/ELECTRIC), **`energy_micro`**,
`energy_unit_entered`, `is_full`, `missed_previous`, `total_micros?`,
`unit_price_micros?`, `currency_code`, `tag`, `station?`, `fuel_grade?`,
`payment_method?`, `note?`, `import_source?`, `import_batch_id?`, `import_row_hash?`,
`created_at_millis`, `updated_at_millis`.

`CHECK`: at least one of `total_micros` / `unit_price_micros` is non-null. Persist what the
user typed and derive the other at display — computing and storing both puts a rounded
number they never entered into their tax export.

Indices, each tied to a named query:

| Index | Serves |
| --- | --- |
| `(vehicle_id, occurred_local_date, odometer_m)` | engine read + monthly range scan; covers the ORDER BY |
| `(vehicle_id, odometer_m)` | `MAX(odometer_m)` for every distance reminder, on every screen open |
| `(vehicle_id, tag, occurred_local_date)` | business/personal tax export |
| `(import_row_hash)` | duplicate probe on re-import — **not unique** |
| `(import_batch_id)` | undo-an-import delete |

**Why this EV shape.** A plug-in hybrid needs both a petrol fill and a charge session, at
different odometers — so they cannot be columns on one row. One row per energy event with a
`kind` discriminator is the only model that survives a PHEV. The engine then computes
energy-per-distance *per kind* for free: a PHEV yields two independent series with zero
schema change. Charge-specific fields (AC/DC, network, session fee) later go in an additive
`charge_detail` table keyed by `fill_up.id` — a pure `CREATE TABLE`, the cheapest migration
there is.

The cost is honest: `energy_micro`'s unit depends on a sibling column. Mitigated by keeping
`energy_kind` NOT NULL and adjacent, a `CHECK` constraint, and a domain type
`Energy(kind, micro)` so no bare `Long` ever crosses a boundary.

### `expense`

`id`, `vehicle_id` (FK CASCADE), `occurred_local_date`, `occurred_at_millis`, `odometer_m?`,
`category`, `total_micros`, `currency_code`, `tag`, **`vendor?`**, `note?`, `reminder_id?`,
`import_*`, timestamps.

Indices: `(vehicle_id, occurred_local_date)`, `(vehicle_id, category, occurred_local_date)`
(serves both the month breakdown and "when was the last OIL_CHANGE" for reminders),
`(vehicle_id, tag, occurred_local_date)`, `(reminder_id)`, `(import_batch_id)`.

`vendor` is free text so someone can record "Congestion Charge" or "Dartford Crossing" under
`TOLL` without ever opening the fixed enum. Tolls and parking are the highest-frequency
categories for delivery riders.

**Separate tables, not one `entry` table** — a unified table means eight fuel-specific null
columns on every parking ticket and a wider hot table for the engine to scan. Build the
combined timeline by merging two Room `Flow`s with `combine` in Kotlin, not a SQL `UNION`
(which cannot use an index for the merged ORDER BY, and is harder to test).

### `odometer_segment`

`id`, `vehicle_id`, `starts_at_local_date`, `starts_at_millis`, `reason`
(UNIT_REPLACED / ROLLOVER / PURCHASED_USED / MANUAL_CORRECTION), `offset_m?`, `note?`.

**Records user intent only.** The engine detects anomalies and *proposes*; nothing is written
until the user confirms. `offset_m` non-null means "bridge by this amount"; null is a hard break.

### `reminder` / `reminder_completion` / `import_batch`

`reminder`: `vehicle_id`, `title`, `kind` (DATE/DISTANCE/BOTH), `category?`,
`due_local_date?`, `repeat_months?`, `warn_days_before` (default 30), `due_odometer_m?`,
`repeat_distance_m?`, `warn_distance_m` (default 500 km), `anchor_local_date?`,
`anchor_odometer_m?`, `is_active`, `notify_enabled`, `last_notified_local_date?`.
Indices `(vehicle_id, is_active, due_local_date)` and
`(is_active, notify_enabled, due_local_date)` — the second serves the alarm receiver's
cross-vehicle scan, a different query with a different leading column.

`reminder_completion`: links a completion to an optional `expense_id`.
`import_batch`: `source`, `file_name`, `imported_at_millis`, `row_count` — makes **import
undoable as a single unit**, which matters because import is the highest-regret operation
in the app.

### Room configuration

- `exportSchema = true`, `app/schemas/` committed, and a `MigrationTestHelper` harness from
  day one. The first migration must not be the first time that machinery runs.
- Enums stored **by name, never ordinal**. Categories and types decode leniently to `OTHER`;
  **units and currency must not**, they decode to `Unknown(raw)`.
- **Rows holding an `Unknown` value are read-only**, enforced in the repository, with a
  "created by a newer version" badge. Otherwise: restore a newer backup on an older build,
  edit the note, and you permanently overwrite a real category with `OTHER`.
- `PRAGMA foreign_keys=ON` explicitly in a `RoomDatabase.Callback`.
- No views, triggers or computed columns — "nothing derived is persisted" is enforced by the
  schema containing nothing derived.

---

## Consumption engine

A pure function: `(List<FuelEvent>, List<DeclaredSegment>) → ConsumptionResult`.

**1. Order** by `(date, odometer if both present, instantMillis, id)`. Odometer as the
same-day tiebreak is correct and free — two fills on one date are ordered by the thing that
is actually monotone.

**2. Segment.** Open a new segment on a declared `odometer_segment`, on an unexplainable
odometer decrease, or on a `is_full` event with a null odometer. The nullable-odometer rule
most implementations get wrong:

- partial + null odometer → contributes energy to the enclosing span, **no break**
- full + null odometer → cannot anchor, **breaks the chain**

**3. Anomaly detection.** On a decrease at `i`, generate correction candidates for `odo[i]`
(×10, ÷10, adjacent transposition, single-digit substitution, dropped leading digit).

- Exactly one candidate fits between `odo[i-1]` and `odo[i+1]` with a plausible distance per
  day → mark `SUSPECTED_TYPO`, propose it, **do not write it**. Meanwhile the engine
  **excludes event `i` as an anchor without splitting the chain**: the span runs
  `i-1 → i+1` and still counts `i`'s energy.

  This works because **for full-to-full consumption, skipping an intermediate anchor is
  arithmetically lossless as long as every drop of energy between the surviving anchors is
  counted.** You lose granularity, not correctness. The typo isolates itself; the chain never
  breaks; the point is flagged `AVERAGED` so the UI can say "covers 2 fill-ups."
- Far below and subsequent readings climb from the new base → genuine reset, propose a segment.
- `odo[i-1]` near a 10ⁿ ceiling and `odo[i]` small → rollover, propose `offset_m = 10ⁿ`.
- Ambiguous → break the chain and show a "review this odometer" card. **Never guess silently.**

Equal odometers are not a decrease: zero distance → a dash, not a division by zero.

**4. Spans.** For consecutive anchors `(a, b)`: `between` = events strictly **after** `a` up
to and **including** `b`; `energy` = sum over `between`; `distance = b.odo − a.odo`.

Two comments that must be in the code, because they are the classic bugs:

- The excluded volume is **the opening anchor's own fill**; the closing anchor's fill **is**
  included. You are measuring fuel burned between leaving station A full and arriving at B.
- `missed_previous` on event `e` invalidates the span **containing or ending at** `e`, not
  the one starting at `e`. Because `between` excludes the opening anchor, the formula above
  already gets this right and the chain resumes immediately after.

Name the flag's direction in writing — `missed_previous = true` means *a fill-up happened
before this one that is not recorded* — and use exactly that wording in the UI. Ambiguity
here makes manual entry and CSV import disagree silently.

**5. Currency.** A span with mixed currencies emits `costMicros = null,
reason = MIXED_CURRENCY` but **keeps its consumption figure** — litres do not care what you
paid. Totals elsewhere are per-currency subtotals, never summed. No exchange rates, ever.

**6. Dense timeline.** The engine returns `List<TimelinePoint>` = `Measured | Gap`,
**including gaps**, never a filtered list of values:

```kotlin
data class Measured(index, endDate, startEventId, endEventId,
                    distanceM, energyMicro, kind, costMicros, currency,
                    fillUpsSpanned, confidence /* EXACT | AVERAGED */)
data class Gap(index, endDate, reason, eventIds)

enum class GapReason { FIRST_FILL_UP, MISSED_FILL_UP, PARTIAL_ONLY, ODOMETER_RESET,
                       MISSING_ODOMETER, SUSPECT_ODOMETER, ZERO_OR_NEGATIVE_DISTANCE,
                       IMPLAUSIBLE_RESULT }
```

Because a gap occupies a slot, the UI **cannot** accidentally elide it. "A dash, never a zero
or an interpolation" becomes a property of the type, not a convention someone must remember.

**Lifetime average = `sum(energy) / sum(distance)` over measured spans — never the mean of
the per-span ratios.** Averaging ratios is wrong by several percent and is the most common
bug in this category of app.

Performance is a non-issue: ten years of weekly fills is ~520 rows. Recompute on every Flow
emission on `Dispatchers.Default`, no caching.

---

## Reminders

**Default path — zero background work, zero permissions.** Evaluated on read:
`ReminderEvaluator.evaluate(reminders, latestOdometerByVehicle, today) → Ok | DueSoon |
Overdue | Unknown`. Wired as `combine(activeReminders, maxOdometer, todayFlow)` where
`todayFlow` re-reads `LocalDate.now(zone)` inside `repeatOnLifecycle(STARTED)`.

- Current odometer = `MAX(odometer_m)` across `fill_up` **and** `expense`.
- A vehicle with no odometer at all → `Unknown`, rendered as a dash. **Not "overdue."**
- Repeats recompute from `anchor_*` on completion, never from wall-clock drift.
  `plusMonths` clamps month ends: 31 Jan + 6 months = 31 Jul; 31 Aug + 6 = 28/29 Feb.

**Opt-in notification path.** `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)`, **one
alarm at a time** — the next daily check, not one per reminder. Inexact by design; no
`SCHEDULE_EXACT_ALARM`, which would cost a permission and a Play policy declaration for no
benefit on "your MOT expires in 30 days."

`DailyCheckReceiver` wakes → evaluates → notifies only reminders **newly** crossing a
threshold (deduped via `last_notified_local_date`, so it cannot nag daily) → schedules
tomorrow. Self-perpetuating.

Not WorkManager: `androidx.work` merges `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and
`FOREGROUND_SERVICE` into the manifest. Bare `AlarmManager` costs **zero** dependencies.

Notification channel is created **lazily on first enable**, not in `Application.onCreate` —
a channel in system settings for a feature nobody turned on is sloppy.

**Revocation, the case everyone forgets.** If the user later revokes the permission or blocks
the channel: the alarm keeps firing and `notify()` silently no-ops. On each app foreground,
if `remindersEnabled && !areNotificationsEnabled()`, show an inline banner linking to
`ACTION_APP_NOTIFICATION_SETTINGS`. **Do not flip the stored toggle off** — that destroys
recorded intent. In-app rows keep working regardless; the feature degrades to the
zero-permission default, it never breaks.

---

## CSV, export and backup

Five pure stages; the Android layer only supplies an `InputStream`.

1. **Bytes → text.** BOM detection; strict UTF-8, falling back to ISO-8859-1 on
   `MalformedInputException` (older aCar/Drivvo exports). Show the guess in the preview —
   never silently mojibake someone's station names.
2. **Text → table.** Delimiter sniffing over the first five non-empty lines, honouring
   Excel's `sep=;` line. This matters enormously in the EU: German and French Excel writes
   `;` because `,` is the decimal separator.
3. **Table → dialect. Score, never guess.** Each dialect exposes
   `score(header, firstRows): Int`; below a threshold or on a tie, fall into **generic manual
   column mapping**. A wrong silent guess corrupts someone's tax records.
   - **Fuelio** — one file with `## ` section blocks; split on those *before* parsing. ISO dates.
   - **aCar** — separate files per type; **units embedded in header text** (`Odometer (km)` vs
     `(mi)`), and the fill column is **"Partial Fill-up" Yes/No, inverted** relative to `is_full`.
   - **Drivvo** — headers in the user's app language, so signatures must be multilingual alias
     sets (`Data|Date|Fecha|Datum`).

   Header strings vary by app version and language and cannot be verified offline, so a
   dialect is a **declarative table of `ColumnSpec` + alias sets, not code**. Fixing a
   mismatch is then a one-line data edit and a fourth importer is a data file.
4. **Staging.** Date resolution is the hard part — `01/02/2026` is ambiguous. In order:
   a pattern is viable only if it parses **100%** of rows; any row with day > 12 decides
   `d/M` vs `M/d` definitively; failing that, test each reading for consistency against the
   monotone odometer series; still ambiguous → **ask, with a concrete example from their
   file**. Numbers go through the same locale-independent parser as manual entry. Units come
   from the header where stated, else are inferred from magnitude and **confirmed in the
   preview**. Currency is never in these files — take it from the target vehicle.
5. **Preview → commit.** Summary card, an editable **assumptions** list, and a filterable row
   list (All / Warnings / Duplicates / Errors) with per-row include toggles. Changing an
   assumption re-runs only stage 4 — a pure function over tokenized data, so it is instant.
   Duplicates are hashed on `(vehicle, date, odometer, energy, total)` and **excluded by
   default**, because re-importing the same file is the common case. Commit in one
   transaction against an `import_batch` row, so Settings can offer "Undo import: Fuelio,
   412 rows, 14 Sep."

**Export.** Date range + business/personal filter → CSV via
`ActivityResultContracts.CreateDocument` and the share sheet. No storage permission needed.

**Backup.** `.fuellogbak` = a zip (`java.util.zip`) holding `manifest.json` (schema version,
app version, row counts) + the database. **Checkpoint WAL before copying**
(`PRAGMA wal_checkpoint(TRUNCATE)`) — the `.db` file on disk is stale until you do, and
skipping it ships users a backup missing their recent entries. JSON via
`android.util.JsonWriter`, no serialization plugin.

---

## Theme

Yellow + charcoal, defined as semantic tokens in `ui/theme/`. Dynamic colour **off** — it
would override the brand.

| Token | Light | Dark |
| --- | --- | --- |
| `background` | `#FAFAF7` | `#121418` |
| `surface` | `#FFFFFF` | `#1A1D22` |
| `surfaceAlt` (sunken panels) | `#F1F1EC` | `#22262C` |
| `outline` | `#E2E2DC` | `#2E333A` |
| `textPrimary` | `#16181C` | `#F2F3F5` |
| `textSecondary` | `#5A6069` | `#9BA3AD` |
| `primary` (yellow) | `#F5C518` | `#F5C518` |
| `onPrimary` | `#16181C` | `#16181C` |
| `warningWash` | `#FFF6D6` | `#2A2410` |
| `danger` | `#A32A2A` | `#E06C6C` |

Two rules that prevent the obvious bugs:

- **`onPrimary` never inverts.** Yellow is identical in both schemes, so text on it must stay
  charcoal. A naive light/dark swap puts near-white text on `#F5C518`.
- **Yellow is never text on a light surface** — it fails 4.5:1. Confine it to filled
  containers, accents, and the single primary action per screen.

All numeric output uses `fontFeatureSettings = "tnum"` so money, distance and consumption
align down a list.

---

## Screens

Onboarding: country picker → units confirm/override → first vehicle.
Garage (home, per-vehicle summary + per-currency subtotals) · Vehicle detail (headline
consumption, stat strip, combined history, due reminders) · Add/edit fill-up · Add/edit
expense · Statistics (bar chart + totals) · Months · Month detail · Reminders list + editor ·
Edit vehicle · Settings · Import preview · "What this app collects".

---

## Build configuration

Versions below were **verified present in this machine's Gradle cache**, so they resolve and
build here today. Do not bump during the build.

```toml
agp = "8.13.2"            # Gradle wrapper 8.13
kotlin = "2.3.21"         # NOT 2.4.10 — no matching KSP is cached
ksp = "2.3.11"
composeBom = "2026.06.01" # → material3 1.4.0, compose-ui 1.11.4
room = "2.8.4"
coreKtx = "1.19.0"; activity = "1.13.0"; lifecycle = "2.11.0"
navigation = "2.10.0"; coroutines = "1.11.0"
junit = "4.13.2"; truth = "1.4.5"; robolectric = "4.16.1"; androidxJunit = "1.3.0"
```

`compileSdk = 36`, `targetSdk = 36` (Play requires it for new apps), **`minSdk = 26`**.

The decisive reason for 26 is `java.time`: native from API 26, so **no core library
desugaring** — `desugar_jdk_libs` leaves the dependency tree entirely. This app is saturated
with date arithmetic, so that is not a marginal win. Notification channels are also native at
26, removing compat branching from the one permission-touching code path. Going to 24 would
cost desugaring plus branching for a device slice that is a rounding error by end-2026; going
to 28+ would exclude budget phones, which is exactly the delivery-rider audience.

Java/Kotlin target 17.

**Shipped dependencies, in full:** `core-ktx`, `activity-compose`,
`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `navigation-compose`,
`compose-bom` (ui, ui-graphics, material3, tooling-preview + debug tooling), `room-runtime`,
`room-ktx`, ksp `room-compiler`, `kotlinx-coroutines-android`. That is the whole list.

Explicitly rejected: Hilt, DataStore, WorkManager, any chart library, kotlinx-serialization,
`documentfile`, and **`material-icons-extended`** (~10 MB of vectors — use
`material-icons-core` plus a handful of hand-written `ImageVector`s).

Test-only dependencies are exempt from the minimal-dependency rule since they never ship:
junit, truth, robolectric, `androidx.test.ext:junit`, `room-testing`, `coroutines-test`,
`compose-ui-test-junit4`, `turbine`.

**Dependency locking — do this.** `dependencyLocking { lockAllConfigurations() }` with a
committed `gradle.lockfile`. It turns "minimal dependency tree" from an aspiration into a
build-enforced fact: a new transitive dependency **fails the build** until someone
regenerates and commits the lockfile as a reviewable diff. Given the threat model is
literally "a transitive dependency reintroduces INTERNET," this is the highest-leverage line
of build config in the project.

**Manifest ratchet** — `xmlns:tools` **must** be on `<manifest>` or every removal silently
does nothing:

```xml
<uses-permission android:name="android.permission.INTERNET"             tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"    tools:node="remove" />
<uses-permission android:name="android.permission.WAKE_LOCK"            tools:node="remove" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"   tools:node="remove" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />

<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

`<application android:allowBackup="true" android:fullBackupContent="@xml/backup_rules"
android:dataExtractionRules="@xml/data_extraction_rules" android:usesCleartextTraffic="false">`
— both XML files listing `fuel-log.db`, `-wal`, `-shm` and the prefs file.

Release: `isMinifyEnabled`, `isShrinkResources`, R8 full mode, plus an explicit keep for
enums reached via `valueOf` in Room converters.

---

## Implementation sequence

Ordered so the riskiest, most valuable code is proven before any UI exists, and the
permission surface is touched last.

| # | Chunk | Tests it must carry |
| --- | --- | --- |
| **0** | Skeleton: 3 modules, version catalog, dependency locking, manifest ratchet, theme, Hello activity. **Ship the permission gate in this same commit.** | Prove the merged-manifest assertion fails by temporarily adding INTERNET; run `assembleRelease` so R8 actually executes |
| **1** | Domain primitives: `DecimalParser`, units + conversion constants, `Money` micros, `CivilDate`/`MonthKey`, enums + lenient decode | Exhaustive parser table: `"32,5"`, `"1.234,5"`, `"1 234,5"` (NBSP), Arabic-Indic `"١٢٣٫٥"`, `"1,23,456"` (Indian grouping), `""`, `"abc"`, `"-5"`. Conversion round-trip properties |
| **2** | **Consumption engine.** Segmentation, anomaly detection, spans, dense timeline, summary. No UI, no Room | ~40 scenarios (partials, missed-fill mid-span vs at anchor, null odometers, equal odometers, reset, rollover, ×10 typo, transposition, PHEV dual-kind); property tests (monotone data ⇒ zero reset gaps; injected ×10 typo ⇒ exactly one `AVERAGED` and **zero** gaps); one checked-in 3-year golden dataset |
| **3** | Room schema, DAOs, repositories, committed schema JSON | Robolectric DAO tests; `SchemaSnapshotTest`; **`EXPLAIN QUERY PLAN` tests** asserting each hot query reports `SEARCH … USING INDEX`, not `SCAN`; re-run the chunk-2 golden set through the DB for identical output |
| **4** | Entry UI: onboarding (country → units via `android.icu`), vehicle CRUD, add/edit fill-up + expense, business/personal toggle, warn-never-block validation | ViewModel warning-set tests; a Compose test asserting **a warning does not block save**; a test asserting a unit change alters no stored value |
| **5** | History, stats, Canvas charts, monthly buckets, MoM delta, per-currency subtotals | Pure `StatsCalculator` tests incl. mixed-currency (subtotals, never a sum). **Extract chart geometry into a pure `ChartLayout.compute(points, size)` and unit-test that** — the `Canvas` block then holds only draw calls |
| **6** | Reminders, in-app only. Zero permissions touched | Evaluator across DATE/DISTANCE/BOTH; unknown odometer → `Unknown` **not** `Overdue`; repeat anchoring; leap years; 31 Jan + 6 months clamping |
| **7** | CSV export + backup/restore | Exporter golden files (quote escaping, newlines in notes, CRLF, BOM); a **round-trip** test: write zip, wipe DB, restore, assert identical rows |
| **8** | CSV import: dialects, scoring detector, date resolver, staging, preview UI, batch commit, undo | Checked-in anonymised fixtures per app; a detector **confusion-matrix** test (each fixture scores its own dialect highest); ambiguity tests asserting it **asks** rather than guesses; the aCar `Partial Fill-up` inversion; a `;`-delimited German fixture |
| **9** | Opt-in notifications: Settings toggle, runtime request at the toggle (API 33+ gated), lazy channel, `AlarmManager` daily check, boot re-arm, revocation banner | `ShadowAlarmManager` (exactly one pending alarm, re-armed after firing); `ShadowNotificationManager` (firing twice in a day posts once); denied, granted-then-revoked, and `SDK_INT < 33` paths |
| **10** | Release polish: dark theme audit, font-scale + RTL passes, string extraction, R8 release, Play Data Safety form | — |

Chunk 7 deliberately precedes chunk 8: export gives you a format you fully control, and its
round-trip test proves the entire data layer, which de-risks import substantially.

---

## Verification

**No physical device is needed for any of v1.**

- **Tier 1 — pure JVM (~70% of the suite, milliseconds).** Everything in `:core:domain` and
  `:core:csv`. Because those modules are `kotlin("jvm")`, an accidental `import android.*` is
  a compile error — the architecture enforces the test strategy instead of relying on review.
- **Tier 2 — Robolectric, still on the JVM.** Room DAOs (in-memory, plus a real file DB for
  the backup round-trip), SharedPreferences, ICU locale defaults, `ShadowAlarmManager`,
  `ShadowNotificationManager`. Run `@Config(sdk = [26, 33, 36])` to cover the API-26 floor
  and the API-33 notification boundary. Keep all SAF code behind a thin `Uri → Stream`
  gateway so every byte of import/export logic tests against `ByteArrayInputStream`.
- **Tier 3 — Compose tests under Robolectric**, few and strictly behavioural: a below-previous
  odometer warns **and** saves; a gap bar renders a dash and its reason, not a zero.
- **Tier 4 — instrumented.** v1 needs none. Reserve for the real SAF picker and the first
  Room migration.

### Gating the no-INTERNET guarantee

Four independent checks, because each catches a different failure mode:

1. **Merged-manifest assertion, build-time hard fail.** A Gradle task reading
   `app/build/intermediates/merged_manifests/<variant>/AndroidManifest.xml`, extracting every
   `uses-permission` / `uses-permission-sdk-23`, and diffing against an allowlist of exactly
   `{POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED}`. Wired `finalizedBy` manifest processing and
   as a dependency of `check`. **This is the check that catches a library adding INTERNET.**
2. **Release-artifact assertion** via `aapt2 dump permissions` on the built AAB/APK. Needed
   separately because `tools:node="remove"` **fails silently when mistyped** — check 1 reads
   the merged manifest that the same broken directive produced, so it can share the failure.
   Check 2 reads what actually ships.
3. **Bytecode network scan.** A permission can be absent while a library still ships
   networking code. Scan release `classes.dex` and fail on `java.net.Socket`, `java.net.URL`,
   `HttpURLConnection`, `okhttp3.`, `retrofit2.`, `android.webkit.WebView`,
   `ConnectivityManager`, `com.google.firebase`, `com.google.android.gms`. Absence of the
   permission is the control; absence of the code is the proof.
4. **Lockfile diff** — stops 1–3 from ever needing to fire.

Plus a debug-only runtime tripwire: `StrictMode` with `detectNetwork().penaltyDeath()` under
`BuildConfig.DEBUG`, so an accidental network call crashes instantly in development and costs
nothing in release.

CI (Linux, JDK 17, no emulator):

```bash
./gradlew --no-daemon check assembleRelease verifyPermissions verifyNoNetworkCode
```

### Manual smoke test at the end of each chunk

Build and install the debug APK, then verify the chunk's own acceptance criterion by hand —
for chunk 4, that means logging a real fill-up end to end and confirming the figure that
appears matches one computed on paper.

---

## First files to create

- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `core/domain/src/main/kotlin/com/fuelexpenselog/domain/consumption/ConsumptionEngine.kt`
- `app/src/main/java/com/fuelexpenselog/app/data/db/FuelLogDatabase.kt`
- `core/csv/src/main/kotlin/com/fuelexpenselog/csv/imprt/DialectDetector.kt`

## Open items to confirm as we go

1. The abandoned commits stay in git history. The first new commit will record the working
   tree as deliberately reset; say if you would rather start the branch clean instead.
2. aCar / Drivvo / Fuelio header strings vary by app version and UI language and cannot be
   verified offline. Real export files from any of the three would materially improve import
   accuracy — otherwise chunk 8 ships on best-effort alias sets plus the generic mapper.
3. `Months` year picker beyond the current year: assumed in, since `MonthKey` arithmetic makes
   it nearly free.
