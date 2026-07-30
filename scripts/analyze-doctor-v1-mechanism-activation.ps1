param(
    [Parameter(Mandatory)][string]$ProfilePath,
    [Parameter(Mandatory)][string]$BuildReportPath,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

foreach ($path in @($ProfilePath, $BuildReportPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required activation input does not exist: $path"
    }
}
New-JmoaDirectory -Path $OutputDirectory

function Get-Percentile {
    param([long[]]$Values, [double]$Percentile)
    if ($Values.Count -eq 0) { return 0 }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Min($sorted.Count - 1, [Math]::Floor(($sorted.Count - 1) * $Percentile))
    return [long]$sorted[$index]
}

function Get-ActivationFamily {
    param([string]$OwnerInternalName)
    if ($OwnerInternalName -match '(?i)(__BeanDefinitions|__BeanFactoryRegistrations|autoconfigure|configuration|<clinit>)') {
        return 'STARTUP_CONFIGURATION_AOT'
    }
    if ($OwnerInternalName -match '(?i)(actuator|micrometer|prometheus|observation|tracing)') {
        return 'ACTUATOR_MICROMETER'
    }
    if ($OwnerInternalName -like 'com/pro/doctormanagementservice/*') {
        return 'DOCTOR_APPLICATION'
    }
    return 'FRAMEWORK_OTHER'
}

$profile = Get-Content -Raw -LiteralPath $ProfilePath | ConvertFrom-Json
$build = Get-Content -Raw -LiteralPath $BuildReportPath | ConvertFrom-Json
$buildReportVersion = if ($null -ne $build.PSObject.Properties['version']) { [string]$build.version } else { 'NOT_RECORDED' }

$profileSitesByKey = @{}
foreach ($site in @($profile.lambdaSites)) {
    $profileSitesByKey[[string]$site.siteKey] = $site
}
$loadedClasses = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($className in @($profile.loadedClasses)) {
    [void]$loadedClasses.Add(([string]$className).Replace('.', '/'))
}

$admittedDecisions = @($build.filterSummary.frameworkDecisions | Where-Object { [bool]$_.allowed })
$admittedKeys = @($admittedDecisions.siteKey | Sort-Object -Unique)
$tier1Keys = @($build.tier1RuntimeSummary.supportedPlans.siteKey | Sort-Object -Unique)
$observedAdmitted = @($admittedKeys | Where-Object { $profileSitesByKey.ContainsKey($_) } | ForEach-Object { $profileSitesByKey[$_] })
$observedTier1 = @($tier1Keys | Where-Object { $profileSitesByKey.ContainsKey($_) } | ForEach-Object { $profileSitesByKey[$_] })

$familyRows = @(
    $observedAdmitted |
        Group-Object { Get-ActivationFamily -OwnerInternalName ([string]$_.ownerInternalName) } |
        Sort-Object Name |
        ForEach-Object {
            $invocations = @($_.Group | ForEach-Object { [long]$_.invocationCount })
            [ordered]@{
                family = $_.Name
                profileObservedSites = $_.Count
                profileInvocationCount = [long](($invocations | Measure-Object -Sum).Sum)
                medianInvocationCount = Get-Percentile -Values $invocations -Percentile 0.5
                p90InvocationCount = Get-Percentile -Values $invocations -Percentile 0.9
            }
        }
)

$invocationCounts = @($observedAdmitted | ForEach-Object { [long]$_.invocationCount })
$loadedOwnerCount = @(
    $observedAdmitted |
        Where-Object { $loadedClasses.Contains(([string]$_.ownerInternalName).Replace('.', '/')) }
).Count
$modeC = $build.modeCRewriteSummary
$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-v1-mechanism-activation-study-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    classification = 'PROFILE_DERIVED_NON_CLAIM'
    service = 'doctor-service'
    inputs = [ordered]@{
        profileSha256 = (Get-JmoaSha256 -Path $ProfilePath).ToUpperInvariant()
        buildReportSha256 = (Get-JmoaSha256 -Path $BuildReportPath).ToUpperInvariant()
        profileVersion = [string]$profile.version
        buildReportVersion = $buildReportVersion
    }
    buildSurface = [ordered]@{
        totalLambdaSites = [int]$modeC.totalSites
        eligibleSites = [int]$modeC.eligibleSites
        rewrittenOperations = [int]$modeC.rewrittenSites
        rewrittenClasses = [int]$modeC.rewrittenClasses
        admittedDecisionSites = $admittedKeys.Count
        exactTier1SupportedPlanSites = $tier1Keys.Count
        note = 'rewrittenOperations is a build-summary operation count and is not asserted to be a unique site count.'
    }
    recoveredProfile = [ordered]@{
        loadedClasses = @($profile.loadedClasses).Count
        hotClasses = @($profile.hotClasses).Count
        lambdaSites = @($profile.lambdaSites).Count
        trainingDurationSeconds = [int]$profile.trainingDurationSeconds
        trainingDurationReliable = ([int]$profile.trainingDurationSeconds -gt 0)
    }
    activationCoverage = [ordered]@{
        admittedSitesObservedInProfile = $observedAdmitted.Count
        admittedSiteCoveragePercent = if ($admittedKeys.Count -eq 0) { 0 } else { [Math]::Round(100.0 * $observedAdmitted.Count / $admittedKeys.Count, 2) }
        admittedOwnerClassesLoaded = $loadedOwnerCount
        exactTier1SitesObservedInProfile = $observedTier1.Count
        exactTier1CoveragePercent = if ($tier1Keys.Count -eq 0) { 0 } else { [Math]::Round(100.0 * $observedTier1.Count / $tier1Keys.Count, 2) }
        profileInvocationCount = [long](($invocationCounts | Measure-Object -Sum).Sum)
        sitesWithPositiveProfileCount = @($observedAdmitted | Where-Object { [long]$_.invocationCount -gt 0 }).Count
        medianProfileInvocationCount = Get-Percentile -Values $invocationCounts -Percentile 0.5
        p90ProfileInvocationCount = Get-Percentile -Values $invocationCounts -Percentile 0.9
        maximumProfileInvocationCount = if ($invocationCounts.Count -eq 0) { 0 } else { [long](($invocationCounts | Measure-Object -Maximum).Maximum) }
        families = $familyRows
    }
    unavailablePostRewriteCounters = [ordered]@{
        transformedBranchExecutions = 'NOT_CAPTURED'
        adapterInstances = 'NOT_CAPTURED'
        adapterInvocations = 'NOT_CAPTURED'
        fallbackInvocations = 'NOT_CAPTURED'
        allocationsAvoided = 'NOT_CAPTURED'
        workloadPhasePartition = 'NOT_CAPTURED'
    }
    workloadQualityDecision = [ordered]@{
        classification = 'PROFILE_COVERAGE_HIGH_RUNTIME_PHASE_UNATTRIBUTABLE'
        explanation = 'The recovered training profile observed nearly all admitted sites, but it does not partition startup, Actuator, and business phases and it predates the rewrite. It therefore cannot prove transformed-path activation in the reconstructed 80-request runtime diagnostic.'
    }
    claimBoundary = @(
        'This is a profile-derived mechanism study, not a memory result.',
        'Profile invocation counts are pre-rewrite observations used for admission and are not post-rewrite adapter invocation counts.',
        'No per-phase activation, allocation avoidance, or transformed-branch count was captured.',
        'The report does not upgrade the reconstructed Doctor V1 result into an exact historical replay.'
    )
}

$jsonPath = Join-Path $OutputDirectory 'doctor-v1-mechanism-activation-study.json'
$mdPath = Join-Path $OutputDirectory 'doctor-v1-mechanism-activation-study.md'
Write-JmoaJson -Value $report -Path $jsonPath

$familyTable = @($familyRows | ForEach-Object {
    "| $($_.family) | $($_.profileObservedSites) | $($_.profileInvocationCount) | $($_.medianInvocationCount) | $($_.p90InvocationCount) |"
}) -join "`n"
$markdown = @"
# Doctor V1 Mechanism Activation Study

- Classification: **PROFILE_DERIVED_NON_CLAIM**
- Admitted decision sites: **$($admittedKeys.Count)**
- Admitted sites observed in the recovered profile: **$($observedAdmitted.Count) / $($admittedKeys.Count)** ($($report.activationCoverage.admittedSiteCoveragePercent)%)
- Exact Tier-1 supported sites observed: **$($observedTier1.Count) / $($tier1Keys.Count)**
- Recovered profile invocation count across admitted sites: **$($report.activationCoverage.profileInvocationCount)**
- Workload decision: **$($report.workloadQualityDecision.classification)**

| Family | Profile-observed sites | Profile invocation count | Median/site | P90/site |
|---|---:|---:|---:|---:|
$familyTable

The recovered profiler proves that the pre-rewrite training run observed nearly
all admitted site keys. It does **not** prove that the reconstructed runtime
diagnostic executed transformed branches. The historical profile has no phase
partition and reports a zero training duration, so startup, Actuator, metrics,
and business-path activity cannot be separated.

## Counters Not Captured

- transformed branch executions
- adapter instances
- adapter invocations
- fallback invocations
- allocations avoided
- workload-phase partition

This study is not included in the memory matrix and does not upgrade the
two-order reconstructed result into an exact historical replay.
"@
Write-JmoaText -Value $markdown -Path $mdPath
Write-Host "Wrote Doctor V1 mechanism activation study to $OutputDirectory"
