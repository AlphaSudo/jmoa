param(
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'target/comparator-reconstruction-tests')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$tempRoot = Join-Path $OutputDirectory 'fixtures'
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Assertion failed: $Message" }
}

function Write-Zip([string]$Path, [hashtable]$Entries) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $parent = Split-Path $Path -Parent
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew)
    $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entryName in $Entries.Keys) {
            $entry = $archive.CreateEntry($entryName)
            $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
            try { $writer.Write([string]$Entries[$entryName]) } finally { $writer.Dispose() }
        }
    } finally {
        $archive.Dispose()
        $stream.Dispose()
    }
}

# A historical-only JMOA class must disqualify a supposedly clean baseline.
$auditFixture = Join-Path $tempRoot 'audit'
$historicalJar = Join-Path $auditFixture 'historical.jar'
$currentJar = Join-Path $auditFixture 'current.jar'
$manifest = "Manifest-Version: 1.0`r`nImplementation-Title: fixture`r`n`r`n"
Write-Zip -Path $historicalJar -Entries @{
    'META-INF/MANIFEST.MF' = $manifest
    'BOOT-INF/classes/io/github/example/jmoa/Generated.class' = 'fixture-jmoa-output'
}
Write-Zip -Path $currentJar -Entries @{ 'META-INF/MANIFEST.MF' = $manifest }
$auditOutput = Join-Path $OutputDirectory 'audit-output'
& (Join-Path $PSScriptRoot 'audit-comparator-entry-diffs.ps1') `
    -HistoricalArtifact $historicalJar `
    -CurrentArtifact $currentJar `
    -Service petclinic `
    -OutputDirectory $auditOutput
$audit = Get-Content -Raw -LiteralPath (Join-Path $auditOutput 'petclinic-comparator-entry-audit.json') | ConvertFrom-Json
Assert-True ($audit.summary.comparatorDecision -eq 'HISTORICAL_BASELINE_CONTAMINATED') 'JMOA contamination must reject the comparator.'
Assert-True (-not [bool]$audit.privacy.logicalPathsPublished) 'Comparator audit must not publish logical paths.'
$auditText = Get-Content -Raw -LiteralPath (Join-Path $auditOutput 'petclinic-comparator-entry-audit.json')
Assert-True ($auditText -notmatch [regex]::Escape($tempRoot)) 'Comparator audit leaked a fixture path.'

# The Patient recovery scanner must distinguish known-current and incompatible universes.
$patientFixture = Join-Path $tempRoot 'patient-search'
$knownJar = Join-Path $patientFixture 'patient-current.jar'
$incompatibleJar = Join-Path $patientFixture 'patient-incompatible.jar'
Write-Zip -Path $knownJar -Entries @{
    'META-INF/MANIFEST.MF' = "Manifest-Version: 1.0`r`nImplementation-Title: patient-service`r`nStart-Class: example.PatientApplication`r`nSpring-Boot-Version: 4.0.5`r`nBuild-Jdk-Spec: 26`r`n`r`n"
}
Write-Zip -Path $incompatibleJar -Entries @{
    'META-INF/MANIFEST.MF' = "Manifest-Version: 1.0`r`nImplementation-Title: patient-service`r`nStart-Class: example.PatientApplication`r`nSpring-Boot-Version: 3.1.0`r`nBuild-Jdk-Spec: 17`r`n`r`n"
}
$lineagePath = Join-Path $tempRoot 'patient-lineage.json'
$knownSha = (Get-FileHash -LiteralPath $knownJar -Algorithm SHA256).Hash
@{
    variants = @(@{ id = 'B0'; artifact = @{ sha256 = $knownSha } })
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $lineagePath -Encoding UTF8
$protocolPath = Join-Path $tempRoot 'patient-protocol.json'
@{
    artifacts = @(@{ logicalPath = 'baseline/archive.jsa' })
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $protocolPath -Encoding UTF8
$imagesPath = Join-Path $tempRoot 'images.json'
@(@{ Id = 'dangling-without-names' }, @{ Names = @('unrelated/image:latest') }) |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $imagesPath -Encoding UTF8
$patientPublic = Join-Path $OutputDirectory 'patient-public'
$patientPrivate = Join-Path $OutputDirectory 'patient-private'
& (Join-Path $PSScriptRoot 'recover-patient-historical-comparator.ps1') `
    -SearchRootList $patientFixture `
    -ImageInventoryJson $imagesPath `
    -HistoricalProtocolInventoryJson $protocolPath `
    -CurrentArtifactLineageJson $lineagePath `
    -PrivateOutputDirectory $patientPrivate `
    -PublicOutputDirectory $patientPublic
$patient = Get-Content -Raw -LiteralPath (Join-Path $patientPublic 'patient-historical-comparator-recovery.json') | ConvertFrom-Json
Assert-True ($patient.decision -eq 'PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE') 'Fixture must remain unrecoverable.'
Assert-True (@($patient.candidates | Where-Object classification -eq 'KNOWN_CURRENT_OR_LATER_ARTIFACT').Count -eq 1) 'Known current Patient artifact was not classified.'
Assert-True (@($patient.candidates | Where-Object classification -eq 'INCOMPATIBLE_SERVICE_UNIVERSE').Count -eq 1) 'Incompatible Patient artifact was not classified.'
foreach ($candidate in $patient.candidates) {
    Assert-True ($null -eq $candidate.PSObject.Properties['path']) 'Public Patient candidate exposed a full path.'
}

# Final repository reports must preserve the three explicit closure decisions.
$closureDir = Join-Path $repoRoot 'docs/product-evidence/comparator-reconstruction'
$closure = Get-Content -Raw -LiteralPath (Join-Path $closureDir 'comparator-reconstruction-closure.json') | ConvertFrom-Json
Assert-True ($closure.status -eq 'CLOSED_WITHOUT_NEW_PRODUCT_CLAIM') 'Closure status changed.'
Assert-True (-not [bool]$closure.broadCampaignAuthorized) 'Closure unexpectedly authorized a campaign.'
$decisions = @($closure.services | ForEach-Object decision)
foreach ($expected in @(
    'DOCTOR_HISTORICAL_V1_NOT_REPRODUCED',
    'PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE',
    'PETCLINIC_HISTORICAL_B0_CONTAMINATED'
)) {
    Assert-True ($decisions -contains $expected) "Missing closure decision $expected."
}

$budget = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'docs/product-evidence/historical-baseline-recovery/historical-expected-engineering-budget.json') | ConvertFrom-Json
Assert-True ($budget.schemaVersion -eq 'jmoa-historical-expected-engineering-budget-v2') 'Budget schema was not upgraded.'
foreach ($row in $budget.services) {
    Assert-True ($row.evidenceLabel -eq 'HISTORICAL_EXPECTED_ENGINEERING_BUDGET') 'Historical budget label missing.'
    Assert-True ($row.currentEffectLabel -eq 'CURRENT_DIRECT_PRODUCT_EFFECT') 'Current effect label missing.'
    Assert-True (-not [bool]$row.authoritative) 'Historical budget became authoritative.'
}

Write-Host 'Comparator reconstruction tooling tests passed.'
