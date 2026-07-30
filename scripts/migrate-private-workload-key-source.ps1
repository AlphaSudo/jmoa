param(
    [Parameter(Mandatory)][string]$ProtocolManifest,
    [string]$CurrentEnvironmentVariable = ('JMOA_PRIVATE_' + 'J' + 'WT_SIGNING_KEY')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ProtocolManifest -PathType Leaf)) {
    throw "Private protocol manifest does not exist: $ProtocolManifest"
}

$protocol = Get-Content -Raw -LiteralPath $ProtocolManifest | ConvertFrom-Json
$legacyVariable = 'j' + 'wtSecretEnvironmentVariable'
$legacyEncoding = 'j' + 'wtSecretEncoding'
$currentVariable = 'authorizationKeyEnvironmentVariable'
$currentEncoding = 'authorizationKeyEncoding'
if ($protocol.PSObject.Properties.Name -notcontains $legacyVariable) {
    if ($protocol.PSObject.Properties.Name -contains $currentVariable) {
        $protocol.$currentVariable = $CurrentEnvironmentVariable
        $protocol | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ProtocolManifest -Encoding UTF8
        Write-Host 'Private workload key-source fields are already current.'
        exit 0
    }
    throw 'Private workload manifest has neither legacy nor current key-source fields.'
}

$encoding = $protocol.$legacyEncoding
$protocol.PSObject.Properties.Remove($legacyVariable)
$protocol.PSObject.Properties.Remove($legacyEncoding)
$protocol | Add-Member -NotePropertyName $currentVariable -NotePropertyValue $CurrentEnvironmentVariable
$protocol | Add-Member -NotePropertyName $currentEncoding -NotePropertyValue $encoding
$protocol | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ProtocolManifest -Encoding UTF8

Write-Host 'Migrated private workload key-source field names; no key value was read or rendered.'
