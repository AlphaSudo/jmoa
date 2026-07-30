param(
    [Parameter(Mandatory)][string]$RuntimeDirectory,
    [Parameter(Mandatory)][string]$PrivateManifestPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$runtime = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
New-JmoaDirectory -Path (Split-Path $PrivateManifestPath -Parent)
$propertyName = 'app.' + 'j' + 'wt.' + 'secret'
$environmentName = 'JMOA_PRIVATE_' + 'J' + 'WT_SIGNING_KEY'
$bytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$newValue = [Convert]::ToBase64String($bytes)
$newHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($newValue)))
$updated = @()

foreach ($file in Get-ChildItem -LiteralPath $runtime -File -Include *.yml,*.yaml) {
    $text = Get-Content -Raw -LiteralPath $file.FullName
    $pattern = '(?m)^(\s*' + [regex]::Escape($propertyName) + '\s*:\s*).*$'
    if ($text -notmatch $pattern) { continue }
    $oldValue = ([regex]::Match($text, $pattern)).Groups[0].Value -replace ('^\s*' + [regex]::Escape($propertyName) + '\s*:\s*'), ''
    $oldHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($oldValue)))
    $updatedText = [regex]::Replace($text, $pattern, ('$1' + $newValue))
    [IO.File]::WriteAllText($file.FullName, $updatedText, [Text.UTF8Encoding]::new($false))
    $updated += [ordered]@{
        file = $file.Name
        oldValueSha256 = $oldHash
        newValueSha256 = $newHash
        fileSha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    }
}
if ($updated.Count -eq 0) { throw "No runtime files contained the expected signing property." }

[Environment]::SetEnvironmentVariable($environmentName, $newValue, 'User')
Write-JmoaJson -Value ([ordered]@{
    schemaVersion = 'jmoa-private-runtime-key-rotation-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    environmentVariable = $environmentName
    newValueSha256 = $newHash
    files = $updated
    rawValueRecorded = $false
}) -Path $PrivateManifestPath
Write-Host "Private runtime signing key rotated in $($updated.Count) files. New value SHA-256: $newHash"
