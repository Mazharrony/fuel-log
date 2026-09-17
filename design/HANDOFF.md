# Handoff: Fuel Log — MPG & Car Expenses (Android v1)

## Overview

Fuel Log is an Android app for logging fuel fill-ups and car expenses, computing
consumption (MPG / L/100km / km/L) and cost per distance, and reporting spend by
month. Primary markets are the US and UK. It ships **without the INTERNET
permission**: no account, no sync, no ads, no analytics. CSV import/export is the
only data path in or out.

This bundle contains the full v1 UI: 14 screens in flow order, the motion spec,
the design tokens, and the Play Store assets.

## About the Design Files

The files in this bundle are **design references authored as HTML**. They are
prototypes that show intended look, layout, copy and behavior. They are **not
production code to copy**.

The task is to **recreate these designs in the target codebase** using its
established patterns. The intended target is **Android, Kotlin + Jetpack Compose,
minSdk 26** (per the product build plan). If you are implementing in a different
environment, keep the token values and layout rules below and map the components
onto that environment's idioms.

Two notes specific to reading the HTML:

- `Fuel Log App UI.dc.html` is one file containing all 14 phone screens laid out
  side by side on a canvas, each wrapped in a `390 x 844` frame with a
  `data-screen-label` attribute. Grep for `data-screen-label` to enumerate them.
  The 390x844 frame is the design reference viewport, not a fixed app size —
  implement responsively.
- The HTML uses inline styles only and a small runtime for loops/conditionals.
  Ignore the runtime; read the inline styles as the spec.

## Fidelity

**High-fidelity.** Colors, type scale, spacing, copy, and motion timings are
final. Recreate pixel-accurately using Compose and the token table below. Every
hex value, font size and duration in this README is authoritative; where the HTML
and this README disagree, this README wins.

## Design Tokens

### Color

One yellow, one ink, cool blue-grey neutrals. No second accent. No gradients.
Yellow is a *marker*, never a surface behind body text — **one yellow action per
screen**.

| Role | Hex | Use |
| --- | --- | --- |
| Brand yellow | `#F5D211` | The single primary action per screen, full-tank state, selected chip, latest bar in a chart, splash ground |
| Ink | `#0D1117` | Text, hairline emphasis, dark screens, icon tile |
| Paper | `#F7F8FA` | Default screen background |
| Sunken | `#EEF1F5` | Grouped panels, "last done at", footers |
| Hairline | `#E3E7ED` | Row dividers |
| Frame / border | `#D2D8E0` | Screen edge, inactive outline |
| Strong hairline | `#BFC7D1` | Secondary input underline |
| Label ink | `#5E6773` | 10-11px uppercase labels (5.4:1 on paper) |
| Body grey | `#55606C` | Secondary body copy (6.0:1 on paper) |
| Yellow wash | `#FFF6CE` | Inline warnings, current-month row |
| Warning ink | `#4A4218` | Text on yellow wash |
| Ink-on-yellow secondary | `#6B5B00` | Splash subtitle, status bar over yellow (4.5:1) |
| Destructive | `#9B2C2C` | Delete vehicle |

Dark screens (statistics, the privacy screenshot):

| Role | Hex |
| --- | --- |
| Ground | `#0D1117` |
| Divider | `#222A33` |
| Inactive bar | `#3D4752` |
| Empty/null bar | `#2A323C` |
| Secondary text | `#9AA5B1` |
| Tertiary text | `#8794A3` |

Rules:
- Every figure that matters is ink on paper, never grey.
- Dark mode is the same palette with paper and ink swapped; yellow is unchanged.
- Do not introduce Material dynamic color. The brand yellow is fixed.

### Typography

**Instrument Sans**, bundled with the app (not downloaded). Weights used: 500 and
600, plus 400 for long body copy.

| Token | Size / line-height | Weight | Letter-spacing | Use |
| --- | --- | --- | --- | --- |
| Figure XL | 72 / 0.86 | 600 | -0.045em | Headline consumption (vehicle screen) |
| Figure L | 44 / 1.0 | 600 | -0.04em | Odometer and amount inputs |
| Figure M | 30-32 | 600 | -0.03em | Screen totals, month figures |
| Figure S | 19 | 600 | 0 | Stat strip cells |
| Title | 30 / 1.1 | 600 | -0.025em | Screen titles ("Garage") |
| Subtitle | 18 | 600 | 0 | Nav bar titles |
| Body | 15 | 600 / 400 | 0 | Row titles / primary copy |
| Meta | 12.5 | 400 | 0 | Row subtitles |
| Label | 10 | 600 | 0.18em, uppercase | Field and section labels |
| Eyebrow | 11 | 600 | 0.14em, uppercase | Status chips |

**All numeric output is tabular.** In Compose:
`TextStyle(fontFeatureSettings = "tnum")`. Money, distance, volume and
consumption must align down a list.

### Spacing, radius, targets

- Screen gutter: **22dp** left and right.
- Row vertical padding: **13dp**.
- Section label to content: **8dp**.
- Reference viewport: 390 x 844.
- **Radius: 0 everywhere in-app.** The only rounded shapes are the launcher icon
  (26dp at 112dp) and status dots (fully round, 7dp).
- **No elevation, no cards, no shadows.** Structure comes from 1dp hairlines and
  one sunken panel per screen.
- Minimum touch target 44dp. The two entry screens use 56dp action heights.

## Components

### Row (history, garage, settings, months)
13dp vertical padding, `1dp` top border in `#E3E7ED`. Title (Body 600) and meta
(Meta 400, `#55606C`) stacked on the left; value (Body 600) and rate (Meta,
`#55606C`) right-aligned. A **3dp left bar** codes the entry type:

- yellow `#F5D211` — fill-up that produced a valid consumption figure
- grey `#BFC7D1` — partial fill (no figure; renders an em dash)
- ink `#0D1117` — expense

### Stat strip
Three equal-width cells separated by 1dp vertical rules, bounded top and bottom
by 1dp hairlines. Each cell: Label above, Figure S below. The month cell carries
a third line — the delta vs the previous month, Meta size, `#55606C`.

### Inputs
**No boxes.** Label (Label token) above, value at figure scale, underline below:
2dp ink `#0D1117` for the focused field, 1dp `#BFC7D1` otherwise. A **2dp yellow
bar** to the right of the value stands in for the caret. Numeric keypad only on
odometer / volume / amount.

### Toggle
52 x 28 square outline (1dp ink), 22 x 20 solid ink knob, **no track fill**. The
containing row's background goes yellow `#F5D211` to signal "full tank on".

### Chips
1dp outline `#BFC7D1`, filled yellow `#F5D211` with ink border when selected, no
radius. Where the option set is fixed and short (units), use an equal-width
segmented row instead of a wrapping chip group.

### Action bar
Pinned to the bottom edge, full width, 1dp ink top border. Split 50/50 when there
are two actions — the yellow half is **always** the primary. Standalone buttons
are 56dp tall, full width minus the gutter.

### Warning block
Yellow wash `#FFF6CE` with a 3dp yellow left rule, Meta-size text in
`#4A4218`. **Warn, never block**: odometer lower than previous, volume above tank
capacity, future date, suspected duplicate. Only an empty required field blocks
save.

## Screens / Views

All 14 in flow order. Frame reference 390 x 844.

### 01 Splash
**Purpose:** cold-start cover while the database opens.
**Layout:** full-bleed yellow `#F5D211`. Centered column, optically raised
(72dp bottom padding on the centering container): 112 x 112 ink tile, 26dp radius,
containing the 54dp gauge glyph in yellow; below it the wordmark "Fuel Log"
(36/1.0, 600, -0.035em) and subtitle "MPG & Car Expenses" (14, 600, 0.14em,
uppercase, `#6B5B00`). Footer: 56 x 3 progress bar over a 18%-ink track, and
"No account · no internet permission" (12, 600, 0.14em, uppercase, `#6B5B00`).
**Animated** — see Motion.

### 02 Country & currency
**Purpose:** set region, which sets distance, volume and currency together.
**Layout:** title block (32/1.07, 600) + explainer; a search field (1dp
`#BFC7D1` border, white fill, 12dp padding, magnifier icon); then a list of
country rows. Each row: name (15.5, 600) with the implied units beneath (12.5,
`#55606C`), currency code right-aligned (14, 600) and a tick when selected.
Selected row background is yellow. Bottom: a sunken summary strip ("Selected ·
United States · USD $ · miles") and the 56dp yellow "Continue".
**Seed list:** United States (mi / US gal / MPG / USD), United Kingdom (mi / UK
gal / MPG / GBP), Canada (km / L / L100km / CAD), Australia (km / L / L100km /
AUD), Germany (km / L / L100km / EUR), India (km / L / km/L / INR). Ship the full
ISO country list; these six are the defaults surfaced before search.

### 03 Units
**Purpose:** confirm or override the locale guess.
**Layout:** an ink-bordered block whose header is yellow and reads "Detected ·
en-US", with four rows — Region, Distance, Volume, Currency. Below it, three
alternative sets as selectable rows (US / UK / Metric). Footnote: each vehicle
keeps its own units and currency, so this only sets the first one.
**Behavior:** values come from `Locale.getDefault()` and
`android.icu.util.LocaleData` measurement system — device-local only, never a
network call.

### 04 First vehicle
**Purpose:** create vehicle 1 and enter the app.
**Layout:** "What are you tracking?" title, a single underlined name input at 26
figure scale, the unit set as three selectable rows, and a privacy note with a
lock glyph on yellow wash. Bottom: "Already logging elsewhere? Import a CSV"
(inline link, yellow 2dp underline) above the 56dp yellow "Start logging".
**No tutorial and no account step.**

### 05 Garage — HOME
**Purpose:** the app's home. Every later screen returns here.
**Layout:** "Garage" title (30, 600) with an "Offline" eyebrow (7dp yellow dot +
uppercase label) on the right. Then one block per vehicle, each 18dp padded with a
1dp top hairline:
- name (18, 600) and meta ("84,210 mi · gas", 12.5, `#55606C`)
- the headline consumption figure right-aligned (26, 600, -0.03em) with the unit
  label beneath
- a 9-bar sparkline, 26dp tall, 4dp gaps, last bar yellow and the rest `#BFC7D1`
- a footer line: "$212.40 this month · $233.80 last" on the left, "$0.13/mi" on
  the right
Then an "Add vehicle" row (plus glyph, `#55606C`), a sunken year-total panel
("Across all vehicles · 2026" + the figure at 32), and the split action bar:
**Add fill-up** (yellow) / **Add expense**.

### 06 Garage — mixed currencies
**Purpose:** the same home when one person's vehicles use different currencies.
**Layout:** compact vehicle rows (no sparkline), then a sunken "This month · by
currency" panel listing one subtotal per currency at 26 figure scale, and the
line: "Not added together. The app holds no exchange rates and will not invent one
offline."
**Rule: never convert between currencies.** Sum only within a currency.

### 07 Vehicle
**Purpose:** one vehicle's numbers and combined history.
**Layout:** header — vehicle name (22, 600) with a chevron (it is the vehicle
switcher) and the odometer right-aligned. Then the headline consumption at Figure
XL with "{unit} last tank" beside it. Then the stat strip: Average / Per mi /
September (+ delta). Then "History" — four combined rows (fill-ups and expenses
interleaved, newest first). Bottom: a sunken "Last done at" panel (oil change,
service, tires — each "83,898 · 312 ago"), then the split action bar.

### 08 Add fill-up — CRITICAL PATH
**Purpose:** log a fill-up at the pump in three taps.
**Layout:** back arrow + "Fill-up" + "Today · Sep 17" header. Odometer at Figure
L with a 2dp ink underline and "Last reading 83,898" beneath. Then Gallons and
Total paid side by side at 30 figure scale on 1dp underlines. Then the full-tank
row (yellow when on, with the square toggle). Then the warning block when
applicable. Bottom: a two-cell summary strip (This tank / Cost per mi), then a
56dp row — a 56 x 56 ink-outlined "+" (add a note or expense to the same stop) and
the yellow **Save fill-up**.
**Behavior:** date pre-filled to today, focus in odometer, numeric keypad up,
full-tank defaulted on. Computation is full-to-full: distance since the last full
tank divided by the volume added between them; a partial fill produces no figure
and shows an em dash.

### 09 Add expense
**Purpose:** log a non-fuel cost.
**Layout:** same shell as 08. Amount at Figure L. Then a wrapping chip group of
categories: Oil change, Service, Repair, Tires, Toll, Parking, Insurance, Tax,
Fine, Wash, Parts, Other (fixed enum — not user-extensible in v1). Then optional
odometer and a note field. A grey-ruled hint explains that recording the odometer
is what powers the "last done at" lines.

### 10 Statistics
**Purpose:** consumption over time and running totals.
**Layout:** dark screen. "Consumption · last 9 tanks" label, the average at 40
figure scale, then a 220dp-tall bar chart — 9 bars, 7dp gaps, value above each bar
and month label below; the latest bar is yellow, the rest `#3D4752`, and a null
span is a 3dp stub in `#2A323C` with an em dash as its value. Then a totals list
(Fuel, Maintenance and expenses, Total running cost, Cost per mi, Distance
logged). Footer: a month-on-month block — September to date vs August, each with
its own delta.

### 11 Months
**Purpose:** monitor previous months.
**Layout:** "Months" header with the year on the right. Monthly average at 32
figure scale and the highest month on the right. A legend: yellow = Fuel, ink =
Everything else. Then one row per month: label, delta, total, and an 8dp split bar
scaled against the highest month of the year. The current month's row has a
`#FFF6CE` background. Footer: "Tap a month for its categories. Months with no
entries are left out rather than shown as zero."

### 12 Month detail
**Purpose:** audit one past month.
**Layout:** header with the month name and prev/next arrows (the next arrow is
disabled at the current month, `#BFC7D1`). The total at 52 figure scale with its
delta beside it, then "14 entries · 4 fill-ups · 1,042 mi driven". Then "By
category" rows, each with a count, a value and a 6dp bar scaled against the
largest category. Footer: "Same month last year" with its figure, then two
buttons — "See 14 entries" (outlined) and "Export month" (yellow).

### 13 Edit vehicle
**Purpose:** per-vehicle units, currency and lifecycle.
**Layout:** header with the entry count. Name at 26 figure scale. Distance as a
2-up segmented control (Miles / Kilometres). Volume as a 3-up segmented control
(US gallons / UK gallons / Litres). Currency (dropdown) and Tank capacity side by
side. An "Active" row with a toggle — off hides a sold vehicle while keeping its
history. A yellow-wash note: changing units converts nothing on disk. Footer: a
destructive "Delete vehicle and its 1,284 entries" text row in `#9B2C2C`, then
the yellow "Save vehicle".

### 14 Settings & import
**Purpose:** app-level display preference, region default, data in and out.
**Layout:** "Consumption shown as" as a 4-up equal-width segmented control (MPG
US / MPG UK / L/100km / km/L). Distance and Currency side by side. A "Region ·
sets new-vehicle defaults" row. Then "Your data" rows: Export CSV, Import CSV,
"What this app collects". Then an ink-bordered import preview card with a dark
header ("Import preview · acar-export.csv") listing the detected date format,
volume unit and rows parsed, with Cancel / "Import 1,284" buttons. Footer: the
privacy statement in `#55606C`.

## Interactions & Behavior

### Units and currency model — read this before implementing
- **Units and currency are per vehicle**: `Vehicle.distanceUnit`,
  `Vehicle.volumeUnit`, `Vehicle.currency` (ISO 4217 string).
- **Only the consumption display convention is app-level** (MPG US / MPG UK /
  L/100km / km/L).
- **Canonical storage is kilometres and litres.** Changing a unit changes display
  only and never rewrites stored values.
- **Region in Settings sets defaults for new vehicles only.** It never touches
  existing records.
- There is **no country or currency detection over the network** — the app has no
  INTERNET permission. The only sources are `Locale.getDefault()` on first launch
  and the user's explicit choice, which persists.
- **Never convert between currencies.** Mixed-currency totals render as
  per-currency subtotals.

### Consumption computation
Full-to-full spans only. `consumption = distance(lastFull -> thisFull) /
volumeSum(between)`. A partial fill contributes its volume to the next full span
but produces no figure of its own. A missing entry breaks the chain — render an em
dash, never interpolate.

### Validation
Warn, do not block: odometer below the previous reading, volume above the
vehicle's tank capacity, a future date, a suspected duplicate. Block only on an
empty required field (odometer, volume, amount for an expense).

### Navigation
Garage (05) is home. Everything pops back to it. Back from an entry screen
discards with a confirm only when fields are dirty.

### CSV
Import supports aCar, Drivvo and Fuelio exports. Detect the date format and
volume unit, show the preview card (14) with the parsed row count, and require
explicit confirmation. Export writes one file containing fill-ups and expenses.

## Motion

Restrained and functional. Two places only: the splash, and state feedback on the
entry screens. **No parallax, no shared-element transitions, no bounce** beyond
the single needle overshoot.

### Splash — one pass

| Element | Animation | Timing |
| --- | --- | --- |
| Ink tile | opacity 0 -> 1, scale 0.90 -> 1.00 | 0-460ms, `cubic-bezier(0.2, 0.8, 0.2, 1)` |
| Gauge needle | rotate -78deg -> +6deg -> 0deg about (12, 16.5) in the 24-unit glyph viewBox | 230-1290ms, `cubic-bezier(0.34, 1.1, 0.3, 1)` — overshoot, then settle |
| Wordmark | opacity 0 -> 1, translateY 12dp -> 0 | 360-820ms |
| Subtitle | same, staggered | 480-940ms |
| Progress bar | width 0 -> 56dp | 300-2100ms, `cubic-bezier(0.4, 0, 0.2, 1)` |

Implementation: use the platform splash screen API with the ink tile as the icon
and `#F5D211` as `windowSplashScreenBackground`, and run the needle sweep as an
`AnimatedVectorDrawable`. Set `windowSplashScreenAnimationDuration` to 1000ms but
**dismiss as soon as the garage is ready** — never hold the user for the
animation. The HTML mock loops the sequence on a 3.8s cycle purely so it can be
reviewed; ship it as a single pass.

### In-app

| Where | Spec |
| --- | --- |
| Screen change | 220ms fade-through, no slide. Back is the same curve reversed |
| Full-tank toggle | Knob translates 24dp in 140ms ease-out; the row background crossfades to yellow over the same 140ms |
| Save | The yellow half of the action bar compresses to 96% for 90ms on press, then the screen pops. **No success toast** — the new row appearing is the confirmation |
| Charts | Bars grow from the baseline over 420ms with a 30ms stagger, once per screen entry. Never re-animate on data change |
| Warnings | The yellow wash block expands height over 160ms and must not move focus out of the field being edited |

Respect `Settings.Global.ANIMATOR_DURATION_SCALE`: at 0, every transition is
instant and the splash is a single static frame.

## State Management

Local only. Room database, no network layer, no repository sync.

Entities:
- `Vehicle` — id, name, distanceUnit, volumeUnit, currency, tankCapacity?,
  isActive, createdAt
- `FillUp` — id, vehicleId, date, odometerKm, volumeL, totalPaid, isFullTank,
  isMissedEntry, note?
- `Expense` — id, vehicleId, date, category (enum), amount, odometerKm?, note?
- `AppSettings` — consumptionConvention, regionCode

Derived, never stored: consumption per span, cost per distance, month totals,
"last done at" distances. Compute in the ViewModel from Room `Flow`s so a new
entry updates every screen without invalidation logic.

Screen state:
- Garage — list of vehicles with this-month and last-month totals per vehicle,
  plus per-currency subtotals
- Vehicle — selected vehicleId, combined history page, stat strip figures
- Add fill-up — draft entity, focused field, validation warnings, isFullTank
- Months — year selector, month aggregates
- Month detail — selected month, category aggregates, same-month-last-year figure
- Settings — consumption convention, region, import preview parse result

## Assets

In `assets/` in this bundle, each drawn at its final export size:

| File | Size | Notes |
| --- | --- | --- |
| `icon-512.png` | 512 x 512 | Play listing icon; glyph inside the middle 66% |
| `adaptive-background-432.png` | 432 x 432 | Flat `#F5D211` |
| `adaptive-foreground-432.png` | 432 x 432 | Ink glyph inside the 264px safe circle |
| `adaptive-monochrome-432.png` | 432 x 432 | Themed-icon layer |
| `feature-graphic-1024x500.png` | 1024 x 500 | No text in the outer 10% |
| `screens/01-garage.png` … `05-privacy.png` | 1080 x 1920 | Play phone screenshots |

**The mark** is a fuel gauge with the needle at full. Redraw it as a vector
drawable from this path set (24 x 24 viewBox, stroke only, round caps and joins,
stroke width 1.5-2.4 depending on render size):

```
M4.5 16.5a8 8 0 1 1 15 0     (dial arc)
M6.6 9.3 7.6 10.4            (left tick)
M12 5.2V6.7                  (top tick)
M17.4 9.3 16.4 10.4          (right tick)
M12 16.5 16.2 10.8           (needle — rotate this group)
circle cx=12 cy=16.5 r=1.5   (hub, filled)
```

Do not recolor, rotate, or add a drop shadow to the mark. The needle group is the
only part that animates.

All iconography in the app is stroke-only, 2dp, round caps, on a 24dp grid — the
same construction as the mark.

## Store listing

- **Title:** `Fuel Log: MPG & Car Expenses` (28 of 30 characters)
- **Short description (80):** `Track fuel, mileage & car costs. No account, no ads, works fully offline.`
- Lead the long description with the permission claim — it is checkable in the
  permission list, which is what makes it land.
- Name the import explicitly: "import your history from aCar, Drivvo or Fuelio".
- The data safety form declares no data collection, matching reality.

## Files

| File | What it is |
| --- | --- |
| `Fuel Log App UI.dc.html` | All 14 screens. Grep `data-screen-label` to enumerate; each is a 390 x 844 frame |
| `Fuel Log UI Guide.dc.html` | The printable UI and motion guide (same content as this README, formatted for print) |
| `Store Assets.dc.html` | Source for the icon, adaptive layers, feature graphic and the five screenshots |
| `handoff.md` | Earlier short-form version of this document |
| `doc-page.js`, `support.js` | Runtime for the HTML prototypes. **Not part of the design** — ignore when implementing |
| `assets/` | Exported PNGs at final sizes |

## Open questions for the product owner

1. Is the expense category list final for v1, or should it be user-extensible?
2. Should "Months" support a year picker beyond the current year in v1?
3. Reminders (service due at a distance interval) are implied by "last done at"
   but not designed. In scope for v1?
