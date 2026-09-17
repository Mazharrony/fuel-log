# Fuel Log — UI and motion handoff

App: **Fuel Log: MPG & Car Expenses** (28 chars, Play title limit 30)
Platform: Android, Kotlin + Compose, minSdk 26, no INTERNET permission
Design source: `Fuel Log App UI.dc.html` (14 screens), `Store Assets.dc.html` (icon, feature graphic, 5 screenshots)

---

## 1. Color

Palette follows the PDF Toolkit reference: one yellow, one ink, cool blue-grey neutrals. No second accent, no gradients.

| Role | Hex | Use |
| --- | --- | --- |
| Brand yellow | `#F5D211` | The single primary action per screen, full-tank state, selected chip, latest bar in a chart, splash ground |
| Ink | `#0D1117` | Text, hairline emphasis, dark screens, icon tile |
| Paper | `#F7F8FA` | Default screen background |
| Sunken | `#EEF1F5` | Grouped panels, "last done at", footers |
| Hairline | `#E3E7ED` | Row dividers |
| Frame / border | `#D2D8E0` | Screen edge, inactive outline |
| Strong hairline | `#BFC7D1` | Secondary input underline |
| Label ink | `#5E6773` | 10–11px uppercase labels (5.4:1 on paper) |
| Body grey | `#55606C` | Secondary body copy (6.0:1 on paper) |
| Yellow wash | `#FFF6CE` | Inline warnings, current-month row |
| Warning ink | `#4A4218` | Text on yellow wash |
| Ink-on-yellow secondary | `#6B5B00` | Splash subtitle, status bar on yellow (4.5:1) |
| Destructive | `#9B2C2C` | Delete vehicle |

Dark screens (statistics, privacy shot): ground `#0D1117`, dividers `#222A33`, inactive bars `#3D4752`, empty bars `#2A323C`, secondary text `#9AA5B1`, tertiary `#8794A3` / `#7C8796`.

Rules
- Yellow is a marker, never a surface for text-heavy areas. One yellow action per screen.
- Every figure that matters is ink on paper, never grey.
- Dark mode is the same palette with paper and ink swapped; yellow is unchanged.

## 2. Type

Instrument Sans (ship as the app's bundled sans; Compose: `FontFamily` with weights 400/500/600). Weights used: 500 and 600 only, plus 400 for long body copy.

| Token | Size / line | Weight | Tracking | Use |
| --- | --- | --- | --- | --- |
| Figure XL | 72 / 0.86 | 600 | −0.045em | Headline consumption on vehicle screen |
| Figure L | 44 / 1.0 | 600 | −0.04em | Odometer and amount inputs |
| Figure M | 30–32 | 600 | −0.03em | Screen totals, month figures |
| Figure S | 19 | 600 | 0 | Stat cells |
| Title | 30 / 1.1 | 600 | −0.025em | Screen titles (Garage) |
| Subtitle | 18 | 600 | 0 | Nav bar titles |
| Body | 15 | 600 / 400 | 0 | Row titles, primary copy |
| Meta | 12.5 | 400 | 0 | Row subtitles |
| Label | 10 | 600 | 0.18em, uppercase | Field and section labels |
| Eyebrow | 11 | 600 | 0.14em, uppercase | Status chips |

All numeric output uses `font-variant-numeric: tabular-nums` (Compose: `TextStyle(fontFeatureSettings = "tnum")`). Money, distance, volume and consumption are always tabular so columns align down a list.

## 3. Layout and components

Grid: 22px screen gutter, 844px reference height at 390px width. No cards, no elevation — structure comes from 1px hairlines and one sunken panel per screen.

- **Rows** — 13px vertical padding, `border-top: 1px solid #E3E7ED`, title + meta on the left, value + rate right-aligned. A 3px left bar codes the entry type: yellow = fill-up with a valid figure, grey `#BFC7D1` = partial, ink = expense.
- **Stat strip** — three equal cells divided by 1px verticals, label above figure, bounded top and bottom by hairlines.
- **Inputs** — no boxes. Label above, value at figure scale, underline: 2px ink for the focused field, 1px `#BFC7D1` otherwise. A 2px yellow bar stands in for the caret.
- **Toggle** — 52×28 square outline, 22×20 ink knob, no track fill. Yellow background on the row signals "on" for full tank.
- **Chips** — 1px outline, filled yellow when selected, no radius.
- **Action bar** — pinned to the bottom edge, split 50/50 when there are two actions; the yellow half is always the primary. 56px tall standalone buttons, full width minus gutter.
- **Warnings** — yellow wash with a 3px yellow left rule. Warn, never block: odometer lower than previous, volume above tank capacity, future date, duplicates. Only an empty required field blocks save.
- **Radius** — 0 everywhere in-app. The only rounded shapes are the launcher icon (26px at 112px) and status dots.

Minimum touch target 44px; the two entry screens use 56px action heights and figure-scale hit areas because they are used one-handed at a pump.

## 4. Screens (flow order)

| # | Screen | Notes |
| --- | --- | --- |
| 01 | Splash | Yellow ground, ink tile, gauge mark. Animated — see §5 |
| 02 | Country & currency | Searchable list; each country sets distance, volume, currency together |
| 03 | Units | Locale guess pre-selected, three alternative sets |
| 04 | First vehicle | Name + unit confirm, then straight into the app. No tutorial, no account |
| 05 | **Garage (home)** | Every later screen returns here. Vehicle rows with sparkline, month spend vs last month, year total |
| 06 | Garage · mixed currencies | Per-currency subtotals, never converted — the app holds no exchange rates |
| 07 | Vehicle | Headline consumption, stat strip with month-on-month delta, combined history, "last done at" |
| 08 | Add fill-up | The critical path. Date pre-filled, cursor in odometer, full tank on, saves in three taps |
| 09 | Add expense | Same screen minus fuel fields, plus fixed category enum. Odometer optional |
| 10 | Statistics | One chart: consumption per span. Dashes where the chain breaks. Month-on-month block |
| 11 | Months | Every month with total, delta, and a split bar (fuel vs everything else) |
| 12 | Month detail | One past month: total, delta, category breakdown, same month last year, export month |
| 13 | Edit vehicle | Per-vehicle distance, volume, currency, tank, active flag |
| 14 | Settings & import | App-level display unit, region default, CSV export/import with preview, privacy statement |

Units and currency are **per vehicle** (`Vehicle.distanceUnit`, `volumeUnit`, `currency`). Only the consumption display convention (MPG US / MPG UK / L/100km / km/L) is app-level. Region on screen 14 sets defaults for new vehicles only and never rewrites existing records — changing units changes display, not stored values (canonical storage: kilometres and litres).

## 5. Motion

Restrained and functional. Two places only: the splash, and state feedback on entry screens.

### Splash (implemented, 3.8s loop in the mock; ship as a single pass)

| Element | Animation | Timing |
| --- | --- | --- |
| Ink tile | opacity 0→1, scale 0.90→1.00 | 0–460ms, `cubic-bezier(0.2, 0.8, 0.2, 1)` |
| Gauge needle | rotate −78° → +6° → 0° about (12, 16.5) | 230ms–1290ms, `cubic-bezier(0.34, 1.1, 0.3, 1)` — overshoot then settle |
| Wordmark | opacity 0→1, translateY 12px→0 | 360ms–820ms |
| Subtitle | same, staggered | 480ms–940ms |
| Progress bar | width 0→56px | 300ms–2100ms, `cubic-bezier(0.4, 0, 0.2, 1)` |

Android: use the platform splash screen API with the ink tile as the icon and `#F5D211` as the window background, then run the needle sweep as an `AnimatedVectorDrawable` on the icon. Hard cap the splash at the time the database takes to open — never hold the user for the animation. `windowSplashScreenAnimationDuration` 1000ms, dismiss as soon as the garage is ready.

### In-app

- Screen transitions: 220ms fade-through, no slide. Back is the same curve reversed.
- Full-tank toggle: knob translates 24px in 140ms `ease-out`; the row background crossfades to yellow over the same 140ms.
- Save: the action bar's yellow half compresses to 96% for 90ms on press, then the screen pops back to the vehicle list. No success toast — the new row appearing is the confirmation.
- Chart: bars grow from the baseline over 420ms with a 30ms stagger, once per screen entry. Never re-animate on data change.
- Warnings: the yellow wash block expands height over 160ms; it must not shift focus out of the field being edited.
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE`; at 0 everything is instant and the splash is a static frame.

No parallax, no shared-element transitions, no bounce beyond the single needle overshoot.

## 6. Assets

Exported to `assets/`:

| File | Size | Notes |
| --- | --- | --- |
| `icon-512.png` | 512×512 | Play listing icon. Glyph inside the middle 66% |
| `adaptive-background-432.png` | 432×432 | Flat `#F5D211` |
| `adaptive-foreground-432.png` | 432×432 | Ink glyph, inside the 264px safe circle |
| `adaptive-monochrome-432.png` | 432×432 | Themed-icon layer |
| `feature-graphic-1024x500.png` | 1024×500 | No text in the outer 10% |
| `screens/*.png` | 1080×1920 | Five phone screenshots |

Mark: fuel gauge, needle at full. One stroke weight (1.5–2.4 depending on size), ink on yellow, no gradient, legible at 24px. Do not recolor, rotate, or add a drop shadow.

## 7. Store listing

- Title: `Fuel Log: MPG & Car Expenses`
- Short description (80): `Track fuel, mileage & car costs. No account, no ads, works fully offline.`
- Lead the long description with the permission claim — it is checkable in the permission list, which is what makes it land.
- Name the import explicitly: "import your history from aCar, Drivvo or Fuelio".
- Data safety form: declares no data collection, matching reality.
