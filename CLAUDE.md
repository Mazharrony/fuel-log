# Fuel Log

Offline Android app for fuel fill-ups and vehicle costs. Kotlin, Compose, Room, minSdk 26,
targetSdk 36. **No INTERNET permission**, no account, no ads, no analytics.

Start every session with `git log --oneline -12`: each chunk of `docs/plan-v1-remaining.md`
is one long-form commit, so the newest one says where the build stands. The master plan
(`docs/plan-v1-master.md`) holds the reasons. The visual spec is only in git history:
`git show e0ba646:design/HANDOFF.md` and `git show 'e0ba646:design/Fuel Log App UI.dc.html'`.
Its assets are recovered from that commit; its Kotlin is never copied.

## Build and run

```powershell
$env:JAVA_HOME = 'C:\Users\User\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
.\gradlew.bat check --offline          # every test, lint, the permission ratchet
.\gradlew.bat :app:installDebug --offline
```

Emulator: AVD `Pixel_9_Pro_XL` (API 36), `emulator-5554`. adb lives in
`C:\Users\User\AppData\Local\Android\Sdk\platform-tools`. After a dependency change:
`.\gradlew.bat resolveAndLockAll --write-locks --offline`, and the lockfile diff may only touch
`*UnitTest*` configurations - a `releaseRuntimeClasspath` change is a stop-and-ask.

## Invariants (breaking one is a bug even if it compiles)

- The merged manifest may declare only `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`;
  `verify<Variant>Permissions` enforces it. Never `debugImplementation` a test artifact.
- `:core:domain` and `:core:csv` are pure JVM. Logic that can live there does.
- No `REAL` columns: metres, micro-units and money micros as `Long`; dates as `yyyymmdd`.
- Canonical storage is metric. A unit change is a display change; it rewrites no row.
- Nothing derived is persisted; figures recompute from Room `Flow`s.
- Never convert between currencies. Group with `CurrencySubtotals`; `Money.plus` throws.
- Copy: "No internet permission, no account, no ads, no analytics." Never "nothing leaves
  your device" - Auto Backup exists.

## UI rules

- Every visible string is an `R.string` (plurals where counted). No Kotlin literals.
- Warn, never block: `canSave` is the only thing that disables Save.
- A dash, never a zero: `null`, `Gap` and a missing summary render an em dash with a reason.
- One yellow action per screen. Yellow is never text on a light surface; `onPrimary` never
  inverts.
- Figures use the theme's figure styles (all carry `tnum`). Colours only through
  `FuelTheme.colors`; radius 0; hairlines, not cards.
- 48dp touch targets; glyph buttons are `IconBox` with a content description.
- `DecimalParser` reads input; `Formatters` (via `LocalFormatters`) write display;
  `Locale.ROOT` and `Rounding.toPlainString` write files. Never `String.toDouble()`.
- The odometer is a hint under an empty field, never a prefill.
- `vehicle.formatFor(kind, appDefault)` is the only route to a `ConsumptionFormat`.
- `ZoneId` and the clock come from `AppContainer`; the domain never reads ambient time.
- Unreadable rows: "Created by a newer version", Save disabled, Delete kept.
- `missedPrevious` copy is exactly "I missed a fill-up before this one".
- Animations are `tween`/`Animatable`; nothing waits on one.

## Tests

Domain tests are plain JUnit. App tests run under Robolectric (`sdk=26,34`,
`graphicsMode=NATIVE`); Compose tests need `ComposeHostRule` at `order = 0` before
`createComposeRule()`. ViewModel tests use `MainDispatcherRule` and `TestDb`.
