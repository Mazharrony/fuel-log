<#
  The release gate. Every check here must print nothing, or must say OK.

  The permission check is not engineering hygiene - it is a compliance control.
  The Play listing declares "no data collected", and an inaccurate data-safety
  declaration is a removable offence. If a dependency reintroduces a network
  permission, the declaration becomes false.
#>
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\jdk.ps1"
Use-BuildJdk | Out-Null   # apkanalyzer needs JAVA_HOME
$root = Split-Path -Parent $PSScriptRoot
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$failed = $false

function Check($name, [scriptblock]$body) {
    Write-Host "`n== $name ==" -ForegroundColor Cyan
    $result = & $body
    if ($result) { Write-Host $result -ForegroundColor Red; $script:failed = $true }
    else { Write-Host "OK" -ForegroundColor Green }
}

# 1. domain/ and csv/ must stay pure Kotlin - they are the unit-tested core.
Check "domain and csv are free of Android imports" {
    $dirs = @("$root\app\src\main\java\com\fuelexpenselog\app\domain",
              "$root\app\src\main\java\com\fuelexpenselog\app\csv") | Where-Object { Test-Path $_ }
    if ($dirs) {
        Get-ChildItem $dirs -Recurse -Filter *.kt -EA SilentlyContinue |
            Select-String -Pattern '^import (android|androidx)\.' |
            ForEach-Object { "$($_.Filename):$($_.LineNumber) $($_.Line.Trim())" }
    }
}

# 2. No network permission survived the merge.
Check "merged manifests carry no network permission" {
    Get-ChildItem "$root\app\build\intermediates" -Recurse -Filter AndroidManifest.xml -EA SilentlyContinue |
        Select-String -Pattern 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)' |
        ForEach-Object { "$($_.Path): $($_.Line.Trim())" }
}

# 3. The authoritative check - what a Play reviewer and the user's app-info
#    screen actually see. Expected output is an empty permission list.
$apk = @("$root\app\build\outputs\apk\release\app-release-unsigned.apk",
         "$root\app\build\outputs\apk\release\app-release.apk",
         "$root\app\build\outputs\apk\debug\app-debug.apk") | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($apk) {
    Check "APK declares zero permissions ($(Split-Path $apk -Leaf))" {
        & "$sdk\cmdline-tools\latest\bin\apkanalyzer.bat" manifest permissions $apk 2>&1 | Where-Object { $_ -match '\S' }
    }

    # 4. Compose pulls in androidx.graphics:graphics-path, so this app DOES ship
    #    native code and the 16 KB page-size rule (Play, from 2027-02-01) applies.
    #    AGP aligns these, but verify rather than assume.
    Check "native libs are 16 KB aligned" {
        # -notmatch against an array FILTERS it rather than returning a bool,
        # so join into one string first.
        $out = (& "$sdk\build-tools\36.1.0\zipalign.exe" -v -c -P 16 4 $apk 2>&1) -join "`n"
        if ($out -notmatch 'Verification successful') { $out }
    }
} else {
    Write-Host "`n!! No APK built - run assembleRelease first" -ForegroundColor Yellow
    $failed = $true
}

Write-Host ""
if ($failed) { Write-Host "RELEASE CHECK FAILED" -ForegroundColor Red; exit 1 }
Write-Host "RELEASE CHECK PASSED" -ForegroundColor Green
