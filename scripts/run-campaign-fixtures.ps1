<#
.SYNOPSIS
    Executes Gate A fixtures for the PetClinic performance campaign.

    The output is bound to the SHA-256 of every tested script. The campaign
    runner rejects a missing, failed, or stale fixture report.
#>
param(
    [string]$OutputDirectory = 'target/campaign-fixtures'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-canonical-json.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resolvedOutput = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory))
}
New-JmoaDirectory -Path $resolvedOutput
$work = Join-Path $resolvedOutput 'work'
if (Test-Path -LiteralPath $work -PathType Container) {
    $resolvedWork = [IO.Path]::GetFullPath($work)
    if (-not $resolvedWork.StartsWith($resolvedOutput, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean fixture work directory outside output root: $resolvedWork"
    }
    Remove-Item -LiteralPath $resolvedWork -Recurse -Force
}
New-JmoaDirectory -Path $work

$tests = New-Object System.Collections.Generic.List[object]
function Add-FixtureResult {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][bool]$Passed,
        [string]$Details = ''
    )
    $tests.Add([ordered]@{ name = $Name; passed = $Passed; details = $Details }) | Out-Null
}

$testedScriptNames = @(
    'campaign-audit-common.ps1',
    'campaign-canonical-json.ps1',
    'campaign-common.ps1',
    'campaign-launch-petclinic-stack.ps1',
    'campaign-stop-petclinic-stack.ps1',
    'campaign-workload-petclinic.ps1',
    'scenario-ledger-common.ps1',
    'runtime-screen-pair.ps1',
    'analyze-same-artifact-noise.ps1',
    'build-artifact-lineage.ps1',
    'new-petclinic-campaign-manifest.ps1',
    'run-petclinic-performance-campaign.ps1',
    'run-campaign-fixtures.ps1'
)
$testedFiles = New-Object System.Collections.Generic.List[object]
foreach ($name in $testedScriptNames) {
    $path = Join-Path $PSScriptRoot $name
    $tokens = $null
    $errors = $null
    [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors) | Out-Null
    $parseDetails = if ($errors.Count -gt 0) { @($errors | ForEach-Object Message) -join ' | ' } else { '' }
    Add-FixtureResult -Name "parse:$name" -Passed ($errors.Count -eq 0) -Details $parseDetails
    $testedFiles.Add([ordered]@{
        logicalPath = "scripts/$name"
        sha256      = (Get-JmoaSha256 -Path $path).ToUpperInvariant()
    }) | Out-Null
}

$jsonA = '{"b":2,"a":1,"timestamp":"first","items":[{"z":3,"a":4}]}'
$jsonB = '{"items":[{"a":4,"z":3}],"timestamp":"second","a":1,"b":2}'
$canonicalA = Get-JmoaCanonicalJsonResult -Body $jsonA -RuleId 'petclinic-owners-v1'
$canonicalB = Get-JmoaCanonicalJsonResult -Body $jsonB -RuleId 'petclinic-owners-v1'
Add-FixtureResult -Name 'canonical-json-property-order-and-volatile-fields' `
    -Passed ($canonicalA.sha256 -eq $canonicalB.sha256 -and $canonicalA.validJson -and $canonicalB.validJson) `
    -Details "A=$($canonicalA.sha256), B=$($canonicalB.sha256)"

$businessDateA = Get-JmoaCanonicalJsonResult -Body '{"date":"2026-01-01"}' -RuleId 'petclinic-owners-v1'
$businessDateB = Get-JmoaCanonicalJsonResult -Body '{"date":"2026-01-02"}' -RuleId 'petclinic-owners-v1'
Add-FixtureResult -Name 'canonical-json-preserves-business-date-fields' `
    -Passed ($businessDateA.sha256 -ne $businessDateB.sha256) `
    -Details "A=$($businessDateA.sha256), B=$($businessDateB.sha256)"

$arrayA = Get-JmoaCanonicalJsonResult -Body '[1,2,3]' -RuleId 'identity-v1'
$arrayB = Get-JmoaCanonicalJsonResult -Body '[3,2,1]' -RuleId 'identity-v1'
Add-FixtureResult -Name 'canonical-json-preserves-array-order' `
    -Passed ($arrayA.sha256 -ne $arrayB.sha256) `
    -Details "A=$($arrayA.sha256), B=$($arrayB.sha256)"

$semanticBaseline = Join-Path $work 'semantic-baseline.json'
$semanticEqual = Join-Path $work 'semantic-equal.json'
$semanticDrift = Join-Path $work 'semantic-drift.json'
$semanticBody = [ordered]@{
    requests = @(
        [ordered]@{ seq = 1; method = 'GET'; path = '/owners'; status = 200; comparable = $true; bodySha256 = $canonicalA.sha256; canonicalRuleId = 'petclinic-owners-v1' },
        [ordered]@{ seq = 2; method = 'GET'; path = '/actuator/health'; status = 200; comparable = $false; bodySha256 = 'IGNORED' }
    )
}
Write-JmoaJson -Value $semanticBody -Path $semanticBaseline
Write-JmoaJson -Value $semanticBody -Path $semanticEqual
$semanticChanged = $semanticBody | ConvertTo-Json -Depth 8 | ConvertFrom-Json
$semanticChanged.requests[0].bodySha256 = 'DIFFERENT'
Write-JmoaJson -Value $semanticChanged -Path $semanticDrift
$semanticPass = Compare-CampaignSemantics -BaselineSemanticPath $semanticBaseline -CandidateSemanticPath $semanticEqual -PairIndex 1
$semanticFail = Compare-CampaignSemantics -BaselineSemanticPath $semanticBaseline -CandidateSemanticPath $semanticDrift -PairIndex 2
Add-FixtureResult -Name 'semantic-equivalence-accepts-canonical-match' -Passed ($semanticPass.semanticErrors -eq 0)
Add-FixtureResult -Name 'semantic-equivalence-rejects-body-drift' -Passed ($semanticFail.semanticErrors -eq 1)

$semanticRuleDrift = Join-Path $work 'semantic-rule-drift.json'
$ruleChanged = $semanticBody | ConvertTo-Json -Depth 8 | ConvertFrom-Json
$ruleChanged.requests[0].canonicalRuleId = 'different-rule-v1'
Write-JmoaJson -Value $ruleChanged -Path $semanticRuleDrift
$ruleFail = Compare-CampaignSemantics -BaselineSemanticPath $semanticBaseline -CandidateSemanticPath $semanticRuleDrift -PairIndex 3
Add-FixtureResult -Name 'semantic-equivalence-rejects-canonical-rule-drift' -Passed ($ruleFail.semanticErrors -eq 1)

$semanticComparableDrift = Join-Path $work 'semantic-comparability-drift.json'
$comparableChanged = $semanticBody | ConvertTo-Json -Depth 8 | ConvertFrom-Json
$comparableChanged.requests[0].comparable = $false
Write-JmoaJson -Value $comparableChanged -Path $semanticComparableDrift
$comparableFail = Compare-CampaignSemantics -BaselineSemanticPath $semanticBaseline -CandidateSemanticPath $semanticComparableDrift -PairIndex 4
Add-FixtureResult -Name 'semantic-equivalence-rejects-comparability-drift' -Passed ($comparableFail.semanticErrors -eq 1)

$jdkRunA = Join-Path $work 'jdk-a'
$jdkRunB = Join-Path $work 'jdk-b'
New-JmoaDirectory -Path $jdkRunA
New-JmoaDirectory -Path $jdkRunB
$stableJdkSha = 'A' * 64
Write-JmoaJson -Value ([ordered]@{
    javaVersion = 'volatile legacy banner'
    runtimeJdkFingerprint = [ordered]@{
        fingerprintSha256 = $stableJdkSha
        javaVersionRaw = 'openjdk version "26"'
        variant = 'BASELINE'
        containerName = 'container-a'
        capturedAtUtc = '2026-01-01T00:00:00Z'
    }
}) -Path (Join-Path $jdkRunA 'run-manifest.json')
Write-JmoaJson -Value ([ordered]@{
    javaVersion = 'different volatile legacy banner'
    runtimeJdkFingerprint = [ordered]@{
        fingerprintSha256 = $stableJdkSha
        javaVersionRaw = 'openjdk version "26"'
        variant = 'CANDIDATE'
        containerName = 'container-b'
        capturedAtUtc = '2026-02-02T00:00:00Z'
    }
}) -Path (Join-Path $jdkRunB 'run-manifest.json')
$jdkA = Get-CampaignArmJdkIdentity -RunDirectory $jdkRunA
$jdkB = Get-CampaignArmJdkIdentity -RunDirectory $jdkRunB
Add-FixtureResult -Name 'jdk-parity-ignores-volatile-arm-fields' -Passed ($jdkA.identity -eq $jdkB.identity -and $jdkA.identity -eq $stableJdkSha)

$stateBaseline = Join-Path $work 'state-baseline.json'
$stateEqual = Join-Path $work 'state-equal.json'
$stateDrift = Join-Path $work 'state-drift.json'
$state = [ordered]@{ initialStateSha256 = 'INITIAL'; finalStateSha256 = 'FINAL'; mutationsProven = $true }
Write-JmoaJson -Value $state -Path $stateBaseline
Write-JmoaJson -Value $state -Path $stateEqual
$changedState = [ordered]@{ initialStateSha256 = 'OTHER'; finalStateSha256 = 'FINAL'; mutationsProven = $true }
Write-JmoaJson -Value $changedState -Path $stateDrift
$statePass = Compare-CampaignDataState -BaselineDataStatePath $stateBaseline -CandidateDataStatePath $stateEqual -PairIndex 1
$stateFail = Compare-CampaignDataState -BaselineDataStatePath $stateBaseline -CandidateDataStatePath $stateDrift -PairIndex 2
Add-FixtureResult -Name 'data-state-equivalence-accepts-match' -Passed $statePass.passed
Add-FixtureResult -Name 'data-state-equivalence-rejects-initial-drift' -Passed (-not $stateFail.passed)

$manifest = [pscustomobject][ordered]@{
    schemaVersion  = 'fixture'
    campaignSha256 = ''
    sourceRevision = 'abc'
    images         = [ordered]@{ baseline = [ordered]@{ id = 'one' } }
}
$manifest.campaignSha256 = Get-CampaignManifestSha256 -ManifestObject $manifest
$manifestBefore = Get-CampaignManifestSha256 -ManifestObject $manifest
$manifest.images.baseline.id = 'tampered'
$manifestAfter = Get-CampaignManifestSha256 -ManifestObject $manifest
Add-FixtureResult -Name 'campaign-manifest-detects-tampering' `
    -Passed ($manifestBefore -ne $manifestAfter) `
    -Details "before=$manifestBefore, after=$manifestAfter"

$lineagePath = Join-Path $work 'artifact-lineage.json'
$lineage = [ordered]@{
    baseline = [ordered]@{
        sourceRevision = 'rev'; artifactSha256 = 'B0'; buildCommandId = 'cmd'
        effectivePomSha256 = 'pom'; dependencyTreeSha256 = 'deps'
        jmoaPluginExecuted = $false; jmoaDependencyPresent = $false
        jmoaClassesPresent = $false; jmoaReportsPresent = $false
    }
    candidate = [ordered]@{
        sourceRevision = 'rev'; artifactSha256 = 'V2'; profileSha256 = 'profile'
        admissionSha256 = 'admission'; allowlistSha256 = 'allow'
        transformationReportSha256 = 'transform'; reducerReportSha256 = 'reducer'
        materializationManifestSha256 = 'materialize'; preservationFailures = 0
        reducedClassCount = 1
    }
}
Write-JmoaJson -Value $lineage -Path $lineagePath
$lineagePass = Test-CampaignArtifactLineage -LineagePath $lineagePath -ExpectedB0Sha256 'B0' -ExpectedV2Sha256 'V2' -ExpectedMaterializationManifestSha256 'materialize'
$lineage.candidate.preservationFailures = 1
Write-JmoaJson -Value $lineage -Path $lineagePath
$lineageFail = Test-CampaignArtifactLineage -LineagePath $lineagePath -ExpectedB0Sha256 'B0' -ExpectedV2Sha256 'V2' -ExpectedMaterializationManifestSha256 'materialize'
Add-FixtureResult -Name 'artifact-lineage-accepts-complete-chain' -Passed $lineagePass.passed
Add-FixtureResult -Name 'artifact-lineage-rejects-preservation-failure' -Passed (-not $lineageFail.passed)

$configRoot = Join-Path $work 'config'
New-JmoaDirectory -Path $configRoot
Set-Content -LiteralPath (Join-Path $configRoot 'application.yml') -Value 'value: one' -Encoding utf8
$configFreeze = Get-CampaignConfigFreeze -ConfigRepo $configRoot
Set-Content -LiteralPath (Join-Path $configRoot 'application.yml') -Value 'value: two' -Encoding utf8
$configDrift = Test-CampaignConfigUnchanged -ConfigRepo $configRoot -ExpectedContentTreeSha256 $configFreeze.contentTreeSha256
Add-FixtureResult -Name 'config-freeze-detects-byte-drift' -Passed (-not $configDrift.passed)

$ledgerDirectory = Join-Path $work 'child-ledger'
Initialize-CampaignAuditLedger -LedgerDirectory $ledgerDirectory -Stage 'fixture' -Variant 'TEST' -Description 'Fixture child ledger' | Out-Null
$shell = (Get-Command pwsh).Source
Invoke-AuditedExternal -Executable $shell -Arguments @(
    '-NoProfile', '-Command',
    '[Console]::Out.Write("fixture-out"); [Console]::Error.Write("fixture-err")'
) -LedgerDirectory $ledgerDirectory -Step 'capture stdout and stderr' | Out-Null
$ledgerSummary = Complete-CampaignAuditLedger -LedgerDirectory $ledgerDirectory -Status 'COMPLETE' -Stage 'fixture' -Variant 'TEST'
$ledgerText = Get-Content -Raw -LiteralPath (Join-Path $ledgerDirectory 'command-ledger.md')
$ledgerIntegrity = Get-Content -Raw -LiteralPath (Join-Path $ledgerDirectory 'child-ledger-integrity.json') | ConvertFrom-Json
Add-FixtureResult -Name 'child-ledger-captures-and-hashes-responses' -Passed (
    $ledgerSummary.commandCount -eq 1 -and
    $ledgerText -match 'fixture-out' -and
    $ledgerText -match 'fixture-err' -and
    @($ledgerIntegrity.files).Count -ge 4
)

$runtimeScreenPath = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
$runtimeTokens = $null
$runtimeErrors = $null
$runtimeAst = [Management.Automation.Language.Parser]::ParseFile($runtimeScreenPath, [ref]$runtimeTokens, [ref]$runtimeErrors)
$armFunctionAst = $runtimeAst.Find({
    param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Write-CampaignArmCommandLedger'
}, $true)
if ($null -eq $armFunctionAst) {
    Add-FixtureResult -Name 'arm-ledger-consolidates-all-four-stages' -Passed $false -Details 'function not found'
} else {
    Invoke-Expression $armFunctionAst.Extent.Text
    $PairIndex = 99
    $LedgerDirectory = Join-Path $work 'arm-ledger'
    foreach ($stage in @('launch', 'workload', 'capture', 'teardown')) {
        $stageDirectory = Join-Path $LedgerDirectory "b99-$stage"
        Initialize-CampaignAuditLedger -LedgerDirectory $stageDirectory -Stage $stage -Variant 'BASELINE' -Description "$stage fixture" | Out-Null
        Invoke-AuditedExternal -Executable $shell -Arguments @('-NoProfile', '-Command', "[Console]::Out.Write('$stage-response')") `
            -LedgerDirectory $stageDirectory -Step "$stage fixture command" | Out-Null
        Complete-CampaignAuditLedger -LedgerDirectory $stageDirectory -Status 'COMPLETE' -Stage $stage -Variant 'BASELINE' | Out-Null
    }
    $armSummary = Write-CampaignArmCommandLedger -Label 'b' -Variant 'BASELINE'
    $armMarkdown = Get-Content -Raw -LiteralPath (Join-Path $LedgerDirectory 'arm-ledgers\b99\b99-command-ledger.md')
    $allStageResponsesPresent = -not (@('launch', 'workload', 'capture', 'teardown') | Where-Object { $armMarkdown -notmatch "$_-response" })
    Add-FixtureResult -Name 'arm-ledger-consolidates-all-four-stages' -Passed (
        $armSummary.passed -and $armSummary.commandCount -eq 4 -and $allStageResponsesPresent
    )
}

$noiseAnalyzer = Join-Path $PSScriptRoot 'analyze-same-artifact-noise.ps1'
$noisePassInput = Join-Path $work 'noise-pass.json'
$noiseFailInput = Join-Path $work 'noise-fail.json'
function New-NoiseControl {
    param([string]$Label, [double]$DriftKb)
    return [ordered]@{
        label = $Label
        artifactSha256 = "$Label-SHA"
        pairs = @(
            [ordered]@{
                id = "$Label-1"; order = 'A->B'
                first = [ordered]@{ pssKb = 100000; privateDirtyKb = 90000; memoryCurrentBytes = 200000000 }
                second = [ordered]@{ pssKb = 100000 + $DriftKb; privateDirtyKb = 90000 + $DriftKb; memoryCurrentBytes = 200000000 + ($DriftKb * 1024) }
                semanticErrors = 0
            },
            [ordered]@{
                id = "$Label-2"; order = 'B->A'
                first = [ordered]@{ pssKb = 100000 + $DriftKb; privateDirtyKb = 90000 + $DriftKb; memoryCurrentBytes = 200000000 + ($DriftKb * 1024) }
                second = [ordered]@{ pssKb = 100000; privateDirtyKb = 90000; memoryCurrentBytes = 200000000 }
                semanticErrors = 0
            }
        )
    }
}
Write-JmoaJson -Value ([ordered]@{
    schema = 'jmoa-same-artifact-noise-input-v2'
    controls = @((New-NoiseControl -Label 'B0' -DriftKb 256), (New-NoiseControl -Label 'V2' -DriftKb 256))
}) -Path $noisePassInput
Write-JmoaJson -Value ([ordered]@{
    schema = 'jmoa-same-artifact-noise-input-v2'
    controls = @((New-NoiseControl -Label 'B0' -DriftKb 4096), (New-NoiseControl -Label 'V2' -DriftKb 4096))
}) -Path $noiseFailInput
$noisePassDir = Join-Path $work 'noise-pass'
$noiseFailDir = Join-Path $work 'noise-fail'
& $noiseAnalyzer -InputPath $noisePassInput -OutputDirectory $noisePassDir | Out-Null
& $noiseAnalyzer -InputPath $noiseFailInput -OutputDirectory $noiseFailDir | Out-Null
$noisePass = Get-Content -Raw -LiteralPath (Join-Path $noisePassDir 'same-artifact-noise.json') | ConvertFrom-Json
$noiseFail = Get-Content -Raw -LiteralPath (Join-Path $noiseFailDir 'same-artifact-noise.json') | ConvertFrom-Json
Add-FixtureResult -Name 'same-artifact-noise-qualifies-low-reversed-drift' -Passed ([bool]$noisePass.qualified)
Add-FixtureResult -Name 'same-artifact-noise-rejects-large-drift' -Passed (-not [bool]$noiseFail.qualified)

$passed = @($tests | Where-Object { -not $_.passed }).Count -eq 0
$report = [ordered]@{
    schemaVersion = 'jmoa-campaign-fixtures-v1'
    generatedAt   = [DateTime]::UtcNow.ToString('o')
    passed        = $passed
    testedFiles   = $testedFiles.ToArray()
    tests         = $tests.ToArray()
    passedCount   = @($tests | Where-Object passed).Count
    failedCount   = @($tests | Where-Object { -not $_.passed }).Count
}
$jsonPath = Join-Path $resolvedOutput 'campaign-fixtures.json'
Write-JmoaJson -Value $report -Path $jsonPath

$rows = @($report.tests | ForEach-Object { "| $($_.name) | $($_.passed) | $($_.details -replace '\|','/') |" })
$markdown = @"
# Campaign Gate A Fixtures

- Passed: **$($report.passed)**
- Passed tests: $($report.passedCount)
- Failed tests: $($report.failedCount)

| Fixture | Passed | Details |
| --- | ---: | --- |
$($rows -join "`n")

The report is bound to the SHA-256 of every file in `testedFiles`. The campaign
runner rejects a stale fixture report after any tested script changes.
"@
Write-JmoaText -Value $markdown -Path (Join-Path $resolvedOutput 'campaign-fixtures.md')
$report | ConvertTo-Json -Depth 12
if (-not $passed) { exit 1 }
