param(
    [Parameter(Mandatory)][string]$CampaignManifest,
    [string]$OutputDir = "",
    [switch]$FailOnIncomplete
)

$ErrorActionPreference = 'Stop'

function Resolve-V2vPath {
    param([string]$Value, [string]$BaseDirectory)
    if ([string]::IsNullOrWhiteSpace($Value)) { return '' }
    if ([System.IO.Path]::IsPathRooted($Value)) { return $Value }
    return [System.IO.Path]::GetFullPath((Join-Path $BaseDirectory $Value))
}

function Read-V2vJson {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Get-V2vStagePath {
    param($Manifest, [string]$Root, [string]$Stage)
    $node = $Manifest.stages.$Stage
    if ($null -eq $node) { return '' }
    if (-not [string]::IsNullOrWhiteSpace([string]$node.runtimeAttribution)) {
        return Resolve-V2vPath -Value ([string]$node.runtimeAttribution) -BaseDirectory $Root
    }
    $directory = if (-not [string]::IsNullOrWhiteSpace([string]$node.directory)) { [string]$node.directory } else { $Stage }
    return Join-Path (Join-Path $Root $directory) 'generated-class-runtime-attribution.json'
}

function Get-V2vLifecycleRows {
    param($Report)
    if ($null -eq $Report -or $null -eq $Report.lifecycle) { return @() }
    return @($Report.lifecycle)
}

function Get-V2vFamilyRows {
    param($TargetReports)
    $families = @{}
    foreach ($target in $TargetReports) {
        foreach ($row in (Get-V2vLifecycleRows -Report $target.report)) {
            $key = [string]$row.family
            if (-not $families.ContainsKey($key)) {
                $families[$key] = [ordered]@{
                    family = $key
                    services = New-Object System.Collections.Generic.List[string]
                    matchedServices = New-Object System.Collections.Generic.List[string]
                    packagedClasses = 0
                    startupLoaded = 0
                    warmupNew = 0
                    workloadNew = 0
                    runtimeGeneratedStartup = 0
                    runtimeGeneratedWarmup = 0
                    runtimeGeneratedWorkload = 0
                    histogramPersistentClasses = 0
                    histogramPersistentBytes = 0
                }
            }
            $entry = $families[$key]
            if (-not $entry.services.Contains([string]$target.name)) { $entry.services.Add([string]$target.name) }
            if ($target.status -eq 'MATCHED_DIAGNOSTIC_EVIDENCE' -and -not $entry.matchedServices.Contains([string]$target.name)) {
                $entry.matchedServices.Add([string]$target.name)
            }
            $entry.packagedClasses += [int]$row.packagedClasses
            $entry.startupLoaded += [int]$row.startupLoaded
            $entry.warmupNew += [int]$row.newlyLoadedDuringWarmup
            $entry.workloadNew += [int]$row.newlyLoadedDuringWorkload
            $entry.histogramPersistentClasses += [int]$row.histogramPersistentClasses
            $entry.histogramPersistentBytes += [long]$row.workloadHistogramBytes
            switch ([string]$row.classification) {
                'RUNTIME_GENERATED_STARTUP' { $entry.runtimeGeneratedStartup += [int]$row.runtimeOnlyStartup }
                'RUNTIME_GENERATED_WARMUP' { $entry.runtimeGeneratedWarmup += [int]$row.runtimeOnlyWarmup }
                'RUNTIME_GENERATED_WORKLOAD' { $entry.runtimeGeneratedWorkload += [int]$row.runtimeOnlyWorkload }
            }
        }
    }
    return @($families.Values | Sort-Object family)
}

if (-not (Test-Path -LiteralPath $CampaignManifest -PathType Leaf)) {
    throw "V2-V campaign manifest does not exist: $CampaignManifest"
}
$manifestPath = (Resolve-Path -LiteralPath $CampaignManifest).Path
$manifestRoot = Split-Path -Parent $manifestPath
$campaign = Read-V2vJson -Path $manifestPath
$destination = if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    Join-Path $manifestRoot 'v2v-generated-campaign'
} else { $OutputDir }
New-Item -ItemType Directory -Force -Path $destination | Out-Null

$targetReports = @()
foreach ($target in @($campaign.targets)) {
    $name = [string]$target.name
    $staticInventoryPath = Resolve-V2vPath -Value ([string]$target.staticInventory) -BaseDirectory $manifestRoot
    $lifecyclePath = Resolve-V2vPath -Value ([string]$target.lifecycleManifest) -BaseDirectory $manifestRoot
    $reportPath = Resolve-V2vPath -Value ([string]$target.matchedReport) -BaseDirectory $manifestRoot
    $lifecycle = Read-V2vJson -Path $lifecyclePath
    $report = Read-V2vJson -Path $reportPath
    $missing = @()
    if (-not (Test-Path -LiteralPath $staticInventoryPath -PathType Leaf)) { $missing += 'staticInventory' }
    if (-not (Test-Path -LiteralPath $lifecyclePath -PathType Leaf)) { $missing += 'generated-lifecycle-manifest' }
    if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) { $missing += 'v2u-generated-family-matched-evidence' }
    foreach ($stage in @('startup', 'warmup', 'workload')) {
        $stagePath = Get-V2vStagePath -Manifest $lifecycle -Root (Split-Path -Parent $lifecyclePath) -Stage $stage
        if ([string]::IsNullOrWhiteSpace($stagePath) -or -not (Test-Path -LiteralPath $stagePath -PathType Leaf)) {
            $missing += "$stage-attribution"
        }
    }
    $status = if ($missing.Count -gt 0) {
        'EVIDENCE_INCOMPLETE'
    } elseif ($null -ne $report -and -not [string]::IsNullOrWhiteSpace([string]$report.evidenceStatus)) {
        [string]$report.evidenceStatus
    } else {
        'EVIDENCE_INCOMPLETE'
    }
    $targetReport = [ordered]@{
        name = $name
        service = [string]$target.service
        launchMode = [string]$target.launchMode
        runtimePolicy = [string]$target.runtimePolicy
        reducerEngine = [string]$target.reducerEngine
        status = $status
        matched = $status -eq 'MATCHED_DIAGNOSTIC_EVIDENCE'
        staticInventory = $staticInventoryPath
        lifecycleManifest = $lifecyclePath
        matchedReport = $reportPath
        missing = $missing
        identityTupleMatch = if ($null -ne $report) { [bool]$report.identityTupleMatch } else { $false }
        lifecycleStageCount = if ($null -ne $lifecycle) { @($lifecycle.stages.PSObject.Properties).Count } else { 0 }
        report = $report
    }
    $targetReports += $targetReport
    $targetMarkdown = @"
# V2-V $name Capture

- Status: ``$status``
- Service: ``$($targetReport.service)``
- Launch mode: ``$($targetReport.launchMode)``
- Runtime policy: ``$($targetReport.runtimePolicy)``
- Matched identity tuple: ``$($targetReport.identityTupleMatch)``
- Complete bundle: ``$($targetReport.matched)``

## Missing Inputs

$(if ($missing.Count -eq 0) { '- none' } else { $missing | ForEach-Object { "- ``$_``" } })

This is a diagnostic generated-family campaign result. It is not a V2-C memory claim.
"@
    $targetReport | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $destination "v2v-$name-capture.json") -Encoding utf8
    $targetMarkdown | Set-Content -LiteralPath (Join-Path $destination "v2v-$name-capture.md") -Encoding utf8
}

$completeTargets = @($targetReports | Where-Object matched)
$familyRows = Get-V2vFamilyRows -TargetReports $targetReports
$familyMatrix = @($familyRows | ForEach-Object {
    $matchedCount = $_.matchedServices.Count
    $runtimeRelevant = ($_.startupLoaded + $_.warmupNew + $_.workloadNew + $_.runtimeGeneratedStartup + $_.runtimeGeneratedWarmup + $_.runtimeGeneratedWorkload + $_.histogramPersistentClasses) -gt 0
    $blocked = $_.family -in @('SPRING_CGLIB', 'JDK_PROXY', 'BYTEBUDDY', 'HIBERNATE_PROXY', 'SPRING_AOT_BEAN_DEFINITIONS', 'SPRING_AOT_REGISTRATION')
    $classification = if ($matchedCount -eq 0) { 'EVIDENCE_INCOMPLETE' }
        elseif (-not $runtimeRelevant) { 'STATIC_ONLY_IN_MATCHED_CAPTURES' }
        elseif ($blocked) { 'SAFETY_BLOCKED_RUNTIME_RELEVANT' }
        elseif ($matchedCount -gt 1 -and (@($targetReports | Where-Object { $_.matched }).runtimePolicy | Select-Object -Unique).Count -gt 1) { 'MULTI_PROTOCOL_RUNTIME_RELEVANT' }
        elseif ($matchedCount -gt 1) { 'MULTI_SERVICE_RUNTIME_RELEVANT' }
        else { 'SINGLE_SERVICE_RUNTIME_RELEVANT' }
    [ordered]@{
        family = $_.family
        services = @($_.services)
        matchedServices = @($_.matchedServices)
        classification = $classification
        packagedClasses = $_.packagedClasses
        startupLoaded = $_.startupLoaded
        warmupNew = $_.warmupNew
        workloadNew = $_.workloadNew
        runtimeGeneratedStartup = $_.runtimeGeneratedStartup
        runtimeGeneratedWarmup = $_.runtimeGeneratedWarmup
        runtimeGeneratedWorkload = $_.runtimeGeneratedWorkload
        histogramPersistentClasses = $_.histogramPersistentClasses
        histogramPersistentBytes = $_.histogramPersistentBytes
        safetyBlocked = $blocked
    }
})

$roiRows = @($familyMatrix | ForEach-Object {
    $status = if ($_.classification -eq 'EVIDENCE_INCOMPLETE') { 'GENERATED_REPORT_ONLY' }
        elseif ($_.safetyBlocked) { 'GENERATED_MUTATION_BLOCKED' }
        elseif ($_.classification -in @('SINGLE_SERVICE_RUNTIME_RELEVANT', 'MULTI_SERVICE_RUNTIME_RELEVANT', 'MULTI_PROTOCOL_RUNTIME_RELEVANT')) { 'GENERATED_REPORT_ONLY' }
        else { 'APPLICATION_LOW_ROI_ARTIFACT_ONLY' }
    [ordered]@{
        family = $_.family
        classification = $_.classification
        admission = $status
        matchedServiceCount = @($_.matchedServices).Count
        meaningfulRuntimeSurface = ($_.workloadNew + $_.runtimeGeneratedWorkload + $_.histogramPersistentClasses) -gt 0
        uniqueFootprint = [long]$_.packagedClasses
        semanticRisk = if ($_.safetyBlocked) { 'HIGH' } else { 'UNKNOWN_OR_FAMILY_SPECIFIC' }
        mutationComplexity = if ($_.safetyBlocked) { 'HIGH' } else { 'UNPROVEN' }
        rollbackBurden = 'REQUIRED'
    }
})

$admissionReasons = @(
    'V2-V is diagnostic/report-only and admits at most one family.',
    'A candidate requires matched identity, complete lifecycle stages, runtime relevance, a bounded mutation concept, semantic tests, rollback, V2-C, and V2-D plans.',
    'No generated-family mutation is enabled by this campaign.'
)
$prototypeAdmitted = $false
$candidateFamily = ''
if ($completeTargets.Count -lt @($campaign.targets).Count) {
    $admissionReasons += "Only $($completeTargets.Count) of $(@($campaign.targets).Count) target bundles are complete."
} else {
    $admissionReasons += 'All target bundles are matched, but no family is admitted without a bounded mutation contract and separate prototype phase.'
}

$closureType = if ($completeTargets.Count -eq @($campaign.targets).Count -and @($campaign.targets).Count -gt 0) { 'CLOSED_CONFIRMED_INFRASTRUCTURE' } else { 'PARTIAL_INFRASTRUCTURE' }
$bundleValidation = [ordered]@{
    metadataVersion = 'v2v-bundle-validation'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    campaign = [string]$campaign.campaign
    targetCount = @($campaign.targets).Count
    completeBundleCount = $completeTargets.Count
    prototypeAdmitted = $prototypeAdmitted
    targets = $targetReports | ForEach-Object { $_ | Select-Object name, service, launchMode, runtimePolicy, reducerEngine, status, matched, missing, identityTupleMatch }
    boundaries = @('Only MATCHED_DIAGNOSTIC_EVIDENCE enters cross-service ranking.', 'No lifecycle diagnostic is V2-C claimable memory evidence.', 'Private raw captures remain outside the public repository.')
}
$lifecycleMatrix = [ordered]@{
    metadataVersion = 'v2v-lifecycle-matrix'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    targets = $targetReports | ForEach-Object {
        [ordered]@{ name = $_.name; status = $_.status; rows = @(Get-V2vLifecycleRows -Report $_.report) }
    }
}
$roiReport = [ordered]@{
    metadataVersion = 'v2v-matched-roi-ranking'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    matchedBundlesOnly = $true
    rows = $roiRows
    boundaries = @('Incomplete bundles are never treated as runtime relevance.', 'High runtime relevance plus high safety risk remains blocked.', 'ROI ranking does not enable mutation.')
}
$admission = [ordered]@{
    metadataVersion = 'v2v-prototype-admission'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    prototypeAdmitted = $prototypeAdmitted
    candidateFamily = $candidateFamily
    reasons = $admissionReasons
    requiredGates = @('MATCHED_DIAGNOSTIC_EVIDENCE', 'complete lifecycle stages', 'meaningful unique footprint', 'runtime relevance', 'bounded mutation concept', 'semantic test plan', 'rollback plan', 'V2-C protocol', 'V2-D attribution plan')
}
$verdict = [ordered]@{
    metadataVersion = 'v2v-final-verdict'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    closureType = $closureType
    completeBundles = $completeTargets.Count
    targetBundles = @($campaign.targets).Count
    prototypeAdmitted = $prototypeAdmitted
    candidateFamily = $candidateFamily
    runtimeClaimAdded = $false
    mutationEnabled = $false
    summary = if ($closureType -eq 'CLOSED_CONFIRMED_INFRASTRUCTURE') { 'All requested matched diagnostic bundles are complete; no generated-family prototype is admitted.' } else { 'The V2-V campaign infrastructure is executable, but one or more fresh matched bundles remain unavailable.' }
    nextAction = if ($closureType -eq 'CLOSED_CONFIRMED_INFRASTRUCTURE') { 'Decide whether to run one bounded V2-W prototype or close generated-family mutation as discovery-only.' } else { 'Complete the missing target lifecycle bundles and rerun validation.' }
}

$outputs = @{
    'v2v-bundle-validation' = $bundleValidation
    'v2v-lifecycle-matrix' = $lifecycleMatrix
    'v2v-cross-service-family-matrix' = [ordered]@{ metadataVersion = 'v2v-cross-service-family-matrix'; generatedAt = $verdict.generatedAt; families = $familyMatrix }
    'v2v-matched-roi-ranking' = $roiReport
    'v2v-prototype-admission' = $admission
    'v2v-final-verdict' = $verdict
}
foreach ($name in $outputs.Keys) {
    $jsonPath = Join-Path $destination "$name.json"
    $outputs[$name] | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $jsonPath -Encoding utf8
}

$bundleMarkdown = "# V2-V Bundle Validation`n`n- Complete bundles: ``$($bundleValidation.completeBundleCount)/$($bundleValidation.targetCount)```n- Prototype admitted: ``false```n`n" + (($targetReports | ForEach-Object { "| $($_.name) | ``$($_.status)`` | $($_.matched) | $((@($_.missing) -join ', ')) |" }) -join "`n")
Set-Content -LiteralPath (Join-Path $destination 'v2v-bundle-validation.md') -Value ($bundleMarkdown + "`n") -Encoding utf8
$matrixMarkdown = "# V2-V Lifecycle Matrix`n`n" + (($familyMatrix | ForEach-Object { "| $($_.family) | $($_.classification) | $((@($_.matchedServices) -join ', ')) | $($_.startupLoaded) | $($_.warmupNew) | $($_.workloadNew) | $($_.histogramPersistentClasses) |" }) -join "`n")
Set-Content -LiteralPath (Join-Path $destination 'v2v-lifecycle-matrix.md') -Value ($matrixMarkdown + "`n") -Encoding utf8
$familyMarkdown = "# V2-V Cross-Service Generated-Family Matrix`n`n" + (($familyMatrix | ForEach-Object { "| $($_.family) | ``$($_.classification)`` | $((@($_.matchedServices) -join ', ')) |" }) -join "`n")
Set-Content -LiteralPath (Join-Path $destination 'v2v-cross-service-family-matrix.md') -Value ($familyMarkdown + "`n") -Encoding utf8
$roiMarkdown = "# V2-V Matched-Evidence ROI Ranking`n`n" + (($roiRows | ForEach-Object { "| $($_.family) | ``$($_.admission)`` | $($_.matchedServiceCount) | $($_.meaningfulRuntimeSurface) |" }) -join "`n")
Set-Content -LiteralPath (Join-Path $destination 'v2v-matched-roi-ranking.md') -Value ($roiMarkdown + "`n") -Encoding utf8
Set-Content -LiteralPath (Join-Path $destination 'v2v-prototype-admission.md') -Value ("# V2-V Prototype Admission`n`n- Prototype admitted: ``false```n- Candidate family: ``none```n`n" + (($admissionReasons | ForEach-Object { "- $_" }) -join "`n")) -Encoding utf8
Set-Content -LiteralPath (Join-Path $destination 'v2v-final-verdict.md') -Value ("# V2-V Final Verdict`n`n- Closure type: ``$closureType```n- Complete bundles: ``$($completeTargets.Count)/$(@($campaign.targets).Count)```n- Prototype admitted: ``false```n- Runtime claim added: ``false```n- Mutation enabled: ``false```n`n$($verdict.summary)`n`nNext: $($verdict.nextAction)`n") -Encoding utf8

if ($FailOnIncomplete -and $closureType -ne 'CLOSED_CONFIRMED_INFRASTRUCTURE') { exit 2 }
Write-Host "V2-V campaign validation written to $destination"
