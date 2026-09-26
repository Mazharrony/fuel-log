# Import fixtures

Synthetic, best effort. No real Fuelio, aCar or Drivvo export was available when the
importer was written, so these files are built from public descriptions of those formats:
Fuelio's `## ` sections and `CostCategories`, aCar's units in header text and its inverted
"Partial Fill-up", Drivvo's headers in the user's language. They test the importer's
behaviour, not the apps' real headers. Replace or add real exports as soon as there are any;
the alias tables in `imprt/Dialect.kt` are where a mismatch gets fixed.

`german-semicolon.csv` is deliberately Windows-1252 with an Excel `sep=;` line and comma
decimals, the way German Excel saves a CSV.
