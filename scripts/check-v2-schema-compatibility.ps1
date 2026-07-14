param(
    [string]$Registry = "docs/v2-final/v2-schema-registry.json"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Registry -PathType Leaf)) { throw "Schema registry missing: $Registry" }
$registryData = Get-Content -LiteralPath $Registry -Raw | ConvertFrom-Json
$failures = @()
foreach ($schema in $registryData.schemas) {
    if (-not (Test-Path -LiteralPath $schema.fixture -PathType Leaf)) {
        $failures += "Missing fixture: $($schema.fixture)"
        continue
    }
    try { $fixture = Get-Content -LiteralPath $schema.fixture -Raw | ConvertFrom-Json } catch {
        $failures += "Invalid JSON fixture: $($schema.fixture)"
        continue
    }
    $actual = [string]$fixture.metadataVersion
    if ($actual -ne [string]$schema.metadataVersion) {
        $failures += "metadataVersion mismatch for $($schema.fixture): expected $($schema.metadataVersion), got $actual"
    }
    foreach ($field in $schema.requiredFields) {
        if ($null -eq $fixture.PSObject.Properties[$field]) { $failures += "Missing required field '$field' in $($schema.fixture)" }
    }
}
if ($failures.Count -gt 0) { throw ($failures -join [Environment]::NewLine) }
Write-Host "Schema compatibility passed for $(@($registryData.schemas).Count) shipped report families."
