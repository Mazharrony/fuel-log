<#
  Gradle launcher for this project. Selects a usable JDK, then delegates.

  Usage:  .\tools\gw.ps1 :app:assembleDebug
#>
param([Parameter(ValueFromRemainingArguments = $true)] $GradleArgs)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\jdk.ps1"
Use-BuildJdk | Out-Null

$root = Split-Path -Parent $PSScriptRoot
& "$root\gradlew.bat" -p "$root" @GradleArgs
exit $LASTEXITCODE
