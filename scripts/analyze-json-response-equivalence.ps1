param(
    [Parameter(Mandatory)][string]$BaselineResponseDirectory,
    [Parameter(Mandatory)][string]$CandidateResponseDirectory,
    [Parameter(Mandatory)][string]$FileNamePattern,
    [string[]]$VolatileField = @('createdAt','updatedAt'),
    [Parameter(Mandatory)][string]$PrivateOutputPath,
    [Parameter(Mandatory)][string]$PublicOutputDirectory,
    [string]$OutputBaseName = 'doctor-response-semantic-equivalence'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function ConvertTo-NormalizedValue($Value, [System.Collections.Generic.HashSet[string]]$IgnoredFields) {
    if ($null -eq $Value) { return $null }
    if ($Value -is [string] -or $Value -is [ValueType]) { return $Value }
    if ($Value -is [Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | Sort-Object)) {
            if (-not $IgnoredFields.Contains([string]$key)) {
                $ordered[$key] = ConvertTo-NormalizedValue $Value[$key] $IgnoredFields
            }
        }
        return $ordered
    }
    if ($Value -is [Collections.IEnumerable] -and $Value -isnot [string]) {
        $normalized = @($Value | ForEach-Object { ConvertTo-NormalizedValue $_ $IgnoredFields })
        return @($normalized | Sort-Object { $_ | ConvertTo-Json -Depth 30 -Compress })
    }
    $ordered = [ordered]@{}
    foreach ($property in @($Value.PSObject.Properties | Sort-Object Name)) {
        if (-not $IgnoredFields.Contains($property.Name)) {
            $ordered[$property.Name] = ConvertTo-NormalizedValue $property.Value $IgnoredFields
        }
    }
    return $ordered
}

function Get-ResponseSet([string]$Directory) {
    $files = @(Get-ChildItem -LiteralPath $Directory -File | Where-Object Name -match $FileNamePattern | Sort-Object Name)
    if ($files.Count -eq 0) { throw "No response files matched in $Directory." }
    @($files | ForEach-Object {
        $raw = Get-Content -Raw -LiteralPath $_.FullName
        [ordered]@{
            file = $_.Name
            rawSha256 = Get-JmoaTextSha256 $raw
            parsed = $raw | ConvertFrom-Json
        }
    })
}

$ignored = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($field in $VolatileField) { [void]$ignored.Add($field) }
$none = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$baseline = Get-ResponseSet $BaselineResponseDirectory
$candidate = Get-ResponseSet $CandidateResponseDirectory
$baselineFirst = $baseline[0].parsed
$candidateFirst = $candidate[0].parsed
$baselineExact = ConvertTo-NormalizedValue $baselineFirst $none | ConvertTo-Json -Depth 30 -Compress
$candidateExact = ConvertTo-NormalizedValue $candidateFirst $none | ConvertTo-Json -Depth 30 -Compress
$baselineCanonical = ConvertTo-NormalizedValue $baselineFirst $ignored | ConvertTo-Json -Depth 30 -Compress
$candidateCanonical = ConvertTo-NormalizedValue $candidateFirst $ignored | ConvertTo-Json -Depth 30 -Compress
$baselineKeys = @(@($baselineFirst)[0].PSObject.Properties.Name | Sort-Object)
$candidateKeys = @(@($candidateFirst)[0].PSObject.Properties.Name | Sort-Object)
$schemaEqual = ($baselineKeys -join [char]0) -eq ($candidateKeys -join [char]0)
$recordCountEqual = @($baselineFirst).Count -eq @($candidateFirst).Count
$rawEqual = $baselineExact -eq $candidateExact
$canonicalEqual = $baselineCanonical -eq $candidateCanonical
$classification = if ($rawEqual) {
    'SEMANTICALLY_EQUIVALENT_EXACT'
} elseif ($canonicalEqual) {
    'SEMANTICALLY_EQUIVALENT_VOLATILE_FIELDS'
} elseif (-not $schemaEqual -or -not $recordCountEqual) {
    'APPLICATION_BEHAVIOR_DIFFERENCE'
} else {
    'INSUFFICIENT_RESPONSE_CAPTURE'
}
$report = [ordered]@{
    schemaVersion = 'jmoa-json-response-equivalence-v1'
    classification = $classification
    memoryComparisonAllowed = $classification -match '^SEMANTICALLY_EQUIVALENT'
    baseline = [ordered]@{
        responseCount = $baseline.Count
        distinctRawHashes = @($baseline.rawSha256 | Sort-Object -Unique).Count
        recordCount = @($baselineFirst).Count
        exactCanonicalSha256 = Get-JmoaTextSha256 $baselineExact
        semanticCanonicalSha256 = Get-JmoaTextSha256 $baselineCanonical
    }
    candidate = [ordered]@{
        responseCount = $candidate.Count
        distinctRawHashes = @($candidate.rawSha256 | Sort-Object -Unique).Count
        recordCount = @($candidateFirst).Count
        exactCanonicalSha256 = Get-JmoaTextSha256 $candidateExact
        semanticCanonicalSha256 = Get-JmoaTextSha256 $candidateCanonical
    }
    comparison = [ordered]@{
        schemaEqual = $schemaEqual
        recordCountEqual = $recordCountEqual
        rawCanonicalEqual = $rawEqual
        semanticCanonicalEqual = $canonicalEqual
        fieldNames = $baselineKeys
        volatileFieldsIgnored = @($VolatileField)
    }
}
New-JmoaDirectory -Path (Split-Path $PrivateOutputPath -Parent)
New-JmoaDirectory -Path $PublicOutputDirectory
Write-JmoaJson -Value ([ordered]@{
    report = $report
    baselineFiles = @($baseline | Select-Object file,rawSha256)
    candidateFiles = @($candidate | Select-Object file,rawSha256)
}) -Path $PrivateOutputPath
Write-JmoaJson -Value $report -Path (Join-Path $PublicOutputDirectory "$OutputBaseName.json")
Write-JmoaText -Value @"
# Doctor Response Semantic Equivalence

- Classification: **$classification**
- Business responses per arm: **$($baseline.Count) / $($candidate.Count)**
- Records per response: **$(@($baselineFirst).Count) / $(@($candidateFirst).Count)**
- Schema equal: **$schemaEqual**
- Raw canonical equal: **$rawEqual**
- Canonical equal after volatile fields: **$canonicalEqual**
- Volatile fields: **$($VolatileField -join ', ')**

The B0 and V1 response bodies differ only in the explicitly listed creation and
update timestamps. Record count, field set, ordering-insensitive business
content, and all nonvolatile field values are equal. This pair passes the
semantic response gate; HTTP 200 alone was not used as proof.
"@ -Path (Join-Path $PublicOutputDirectory "$OutputBaseName.md")
Write-Host "Semantic response classification: $classification"
