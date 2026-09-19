# Fuel Log — MPG & Car Expenses

An Android app for logging fuel fill-ups and vehicle expenses. Fully offline: no account,
no ads, no analytics, and **no INTERNET permission**.

`com.fuelexpenselog.app` · Kotlin · Jetpack Compose · Room · minSdk 26 · targetSdk 36

## Building

This machine has no JDK on `PATH`. Gradle provisioned one via the foojay resolver; point
`JAVA_HOME` at it:

```powershell
$env:JAVA_HOME = 'C:\Users\User\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
.\gradlew.bat :app:assembleDebug
```

The IDE's bundled JetBrains Runtime is **not** usable for the build: it has `javac` but no
`jlink`, which AGP's `JdkImageTransform` requires.

```powershell
.\gradlew.bat check                          # all tests + the permission ratchet
.\gradlew.bat :core:domain:test              # pure JVM, milliseconds
.\gradlew.bat :app:verifyDebugPermissions    # the offline guarantee
.\gradlew.bat resolveAndLockAll --write-locks  # after any dependency change
```

## Invariants

These are load-bearing. Breaking one is a bug even if nothing fails to compile.

**No INTERNET permission, enforced by the build.** `AndroidManifest.xml` carries
`tools:node="remove"` entries so a transitive dependency cannot reintroduce networking, and
`verify<Variant>Permissions` fails the build if the *merged* manifest declares anything
outside `{POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED}`. `tools:node="remove"` fails silently
when mistyped, which is exactly why the assertion reads the merged result rather than trusting
the directive. A debug-only `StrictMode` policy kills the process on any network call.

**Dependency locking is a compliance control, not hygiene.** A new transitive dependency fails
the build until someone regenerates `gradle.lockfile` and commits it as a reviewable diff.

**`:core:domain` and `:core:csv` are pure `kotlin("jvm")` modules.** `import android.*` there
is a compile error, not a review comment. The consumption engine lives in `:core:domain`
because it is the code where a wrong answer looks right.

**No `REAL` columns.** Distance is `Long` metres, energy `Long` micro-units, money `Long`
micros. Dates are stored twice: epoch millis for ordering, and an `Int` `yyyymmdd` civil date
so month bucketing is timezone-free forever.

**Canonical storage is metric.** Changing a vehicle's display unit changes display only and
never rewrites a stored row.

**Nothing derived is persisted.** Consumption, month totals and cost-per-distance all
recompute from Room `Flow`s.

**Warn, never block.** The only blocking validation is an empty required field.

**A dash, never a zero.** When a figure is unknowable the engine emits a `Gap` with a reason.
Gaps occupy a slot in the timeline so the UI cannot accidentally elide one.

**Never convert between currencies.** The app holds no exchange rates. Mixed-currency totals
render as per-currency subtotals.

## Auto Backup

`allowBackup` is **on**. Android's system backup copies the database to the user's Google
Drive without the app holding INTERNET; on Android 9+ it is end-to-end encrypted with a key
derived from the device lockscreen, so Google cannot read it.

Consequence for copy: say *"no internet permission, no account, no ads, no analytics."*
Do **not** say *"nothing ever leaves your device."*

`backup_rules.xml` and `data_extraction_rules.xml` list `fuel-log.db` plus its `-wal` and
`-shm` siblings — Room runs in WAL mode and backing up the main file alone can capture a
snapshot missing recent entries.

## Layout

```
:app          Room, Compose, SAF, alarms, manual DI
:core:domain  engine, units, money, dates, validation   (pure JVM)
:core:csv     tokenizer, dialects, export               (pure JVM)
```
