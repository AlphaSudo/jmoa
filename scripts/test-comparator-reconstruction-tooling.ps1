param(
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'target/comparator-reconstruction-tests')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$tempRoot = Join-Path $OutputDirectory 'fixtures'
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

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

$sensitiveFixtureValue = 'synthetic-value-that-must-not-render'
$sensitiveName = 'API_' + 'TOKEN'
$protectedFixture = Protect-CampaignAuditText -Value "$sensitiveName=$sensitiveFixtureValue"
Assert-True ($protectedFixture.redactionCount -eq 1) 'Sensitive environment fixture was not redacted.'
Assert-True ($protectedFixture.text -notmatch [regex]::Escape($sensitiveFixtureValue)) 'Sensitive fixture value remained in rendered text.'
Assert-True ($protectedFixture.text -match '<REDACTED sha256=[A-F0-9]{64}>') 'Redaction marker does not contain a value hash.'
$pairScriptText = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'runtime-screen-pair.ps1')
Assert-True ($pairScriptText -match 'redactedStdout\s*=\s*Protect-CampaignAuditText') 'Consolidated arm stdout is not redacted.'
Assert-True ($pairScriptText -match 'renderedRedactionCount\s*=\s*\$renderedRedactionCount') 'Consolidated arm redaction count is not reported.'

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

# A synthetic activation join must remain pre-rewrite and non-claim.
$activationFixture = Join-Path $tempRoot 'activation'
New-Item -ItemType Directory -Force -Path $activationFixture | Out-Null
$siteKey = 'example/Service::run()V#0|get|()Ljava/util/function/Supplier;|8|example/Target::create()V'
$activationProfilePath = Join-Path $activationFixture 'profile.json'
$activationBuildPath = Join-Path $activationFixture 'build.json'
@{
    version = 'fixture'
    trainingDurationSeconds = 0
    loadedClasses = @('example.Service')
    hotClasses = @('example.Service')
    lambdaSites = @(@{
        siteKey = $siteKey
        ownerInternalName = 'example/Service'
        invocationCount = 7
    })
} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $activationProfilePath -Encoding UTF8
@{
    filterSummary = @{ frameworkDecisions = @(@{ siteKey = $siteKey; allowed = $true }) }
    tier1RuntimeSummary = @{ supportedPlans = @(@{ siteKey = $siteKey }) }
    modeCRewriteSummary = @{ totalSites = 1; eligibleSites = 1; rewrittenSites = 1; rewrittenClasses = 1 }
} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $activationBuildPath -Encoding UTF8
$activationFixtureOutput = Join-Path $OutputDirectory 'activation-output'
& (Join-Path $PSScriptRoot 'analyze-doctor-v1-mechanism-activation.ps1') `
    -ProfilePath $activationProfilePath `
    -BuildReportPath $activationBuildPath `
    -OutputDirectory $activationFixtureOutput
$activationFixtureReport = Get-Content -Raw -LiteralPath (Join-Path $activationFixtureOutput 'doctor-v1-mechanism-activation-study.json') | ConvertFrom-Json
Assert-True ($activationFixtureReport.classification -eq 'PROFILE_DERIVED_NON_CLAIM') 'Synthetic activation fixture became claimable.'
Assert-True ($activationFixtureReport.activationCoverage.admittedSitesObservedInProfile -eq 1) 'Synthetic admitted site did not join to the profile.'
Assert-True ($activationFixtureReport.activationCoverage.profileInvocationCount -eq 7) 'Synthetic profile invocation count changed.'
Assert-True ($activationFixtureReport.unavailablePostRewriteCounters.transformedBranchExecutions -eq 'NOT_CAPTURED') 'Synthetic activation fixture invented transformed executions.'

# Final repository reports must preserve the three explicit closure decisions.
$closureDir = Join-Path $repoRoot 'docs/product-evidence/comparator-reconstruction'
$closure = Get-Content -Raw -LiteralPath (Join-Path $closureDir 'comparator-reconstruction-closure.json') | ConvertFrom-Json
Assert-True ($closure.status -eq 'CLOSED_WITHOUT_NEW_PRODUCT_CLAIM') 'Closure status changed.'
Assert-True (-not [bool]$closure.broadCampaignAuthorized) 'Closure unexpectedly authorized a campaign.'
$decisions = @($closure.services | ForEach-Object decision)
foreach ($expected in @(
    'V1_RUNTIME_COST',
    'PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE',
    'HISTORICAL_PETCLINIC_B0_INVALID'
)) {
    Assert-True ($decisions -contains $expected) "Missing closure decision $expected."
}

$activation = Get-Content -Raw -LiteralPath (Join-Path $closureDir 'doctor-v1-mechanism-activation-study.json') | ConvertFrom-Json
Assert-True ($activation.classification -eq 'PROFILE_DERIVED_NON_CLAIM') 'Activation study claim boundary changed.'
Assert-True ($activation.workloadQualityDecision.classification -eq 'PROFILE_COVERAGE_HIGH_RUNTIME_PHASE_UNATTRIBUTABLE') 'Activation study workload decision changed.'
Assert-True ($activation.unavailablePostRewriteCounters.adapterInvocations -eq 'NOT_CAPTURED') 'Activation study invented post-rewrite counters.'

$budget = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'docs/product-evidence/historical-baseline-recovery/historical-expected-engineering-budget.json') | ConvertFrom-Json
Assert-True ($budget.schemaVersion -eq 'jmoa-historical-expected-engineering-budget-v2') 'Budget schema was not upgraded.'
foreach ($row in $budget.services) {
    Assert-True ($row.evidenceLabel -eq 'HISTORICAL_EXPECTED_ENGINEERING_BUDGET') 'Historical budget label missing.'
    Assert-True ($row.currentEffectLabel -eq 'CURRENT_DIRECT_PRODUCT_EFFECT') 'Current effect label missing.'
    Assert-True (-not [bool]$row.authoritative) 'Historical budget became authoritative.'
}

Write-Host 'Comparator reconstruction tooling tests passed.'
