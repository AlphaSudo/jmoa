param(
    [string]$Maven = "mvn"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repoRoot
try {
    & $Maven -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean test
} finally {
    Pop-Location
}
