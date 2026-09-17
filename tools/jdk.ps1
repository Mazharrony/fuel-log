<#
  Shared JDK discovery. Dot-source this: . "$PSScriptRoot\jdk.ps1"

  The JetBrains Runtime bundled with IntelliJ cannot build this project: it
  ships javac but not jlink (needed by AGP's JdkImageTransform), and its JIT
  crashes with a DEP violation while dexing under R8. Gradle provisions a full
  Temurin JDK under ~/.gradle/jdks via the foojay resolver declared in
  settings.gradle.kts, so prefer anything there that has jlink.
#>
function Find-BuildJdk {
    $candidates = @()
    $provisioned = Join-Path $env:USERPROFILE '.gradle\jdks'
    if (Test-Path $provisioned) {
        Get-ChildItem $provisioned -Directory -EA SilentlyContinue | ForEach-Object {
            $candidates += $_.FullName
            $candidates += (Get-ChildItem $_.FullName -Directory -EA SilentlyContinue | ForEach-Object { $_.FullName })
        }
    }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($c in $candidates) {
        if ((Test-Path "$c\bin\java.exe") -and (Test-Path "$c\bin\jlink.exe")) { return $c }
    }
    return $null
}

function Use-BuildJdk {
    $jdk = Find-BuildJdk
    if (-not $jdk) {
        throw "No JDK with jlink found. Run once with JAVA_HOME set to any JDK 17+ so Gradle can provision a full toolchain, then retry."
    }
    $env:JAVA_HOME = $jdk
    return $jdk
}
