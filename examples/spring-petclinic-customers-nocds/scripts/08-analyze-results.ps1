param(
    [string]$InputDir = "./measurements"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $InputDir)) {
    throw "InputDir does not exist: $InputDir"
}

Write-Host "Analyze paired result JSON files under $InputDir."
Write-Host "Expected schema: schemas/paired-result.schema.json"
Write-Host "Win gate: median PSS, Private_Dirty, and memory.current lower by at least 1 MB with at least 2/3 paired wins."
