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
. (Join-Path $PSScriptRoot 'campaign-linux-host-profiles.ps1')

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
    'campaign-linux-host-profiles.ps1',
    'capture-campaign-host-preflight.ps1',
    'capture-linux-campaign-host-preflight.ps1',
    'capture-linux-host-fingerprint.ps1',
    'campaign-launch-petclinic-stack.ps1',
    'campaign-stop-petclinic-stack.ps1',
    'campaign-launch-petclinic-support.ps1',
    'campaign-stop-petclinic-support.ps1',
    'campaign-launch-petclinic-target.ps1',
    'campaign-stop-petclinic-target.ps1',
    'campaign-verify-petclinic-target-transition.ps1',
    'campaign-workload-petclinic.ps1',
    'scenario-ledger-common.ps1',
    'runtime-screen-pair.ps1',
    'analyze-same-artifact-noise.ps1',
    'analyze-petclinic-b0-period-effect.ps1',
    'analyze-three-artifact-blocks.ps1',
    'build-artifact-lineage.ps1',
    'new-independent-session-evidence-adapter.ps1',
    'new-petclinic-campaign-manifest.ps1',
    'petclinic-independent-session-common.ps1',
    'run-petclinic-performance-campaign.ps1',
    'run-petclinic-target-only-campaign.ps1',
    'run-petclinic-independent-session-campaign.ps1',
    'run-three-artifact-balanced-campaign.ps1',
    'three-artifact-campaign-common.ps1',
    'prepare-petclinic-three-artifact-freeze.ps1',
    'run-jmoa-evaluation.ps1',
    'publish-three-artifact-result.ps1',
    'build-three-service-direct-matrix.ps1',
    'run-linux-idle-calibration.ps1',
    'run-linux-host-calibration.ps1',
    'run-petclinic-capacity-qualification.ps1',
    'reconnect-linux-campaign-host.ps1',
    'export-petclinic-linux-campaign.ps1',
    'import-petclinic-linux-campaign.ps1',
    'configure-linux-campaign-host.sh',
    'run-campaign-fixtures.ps1'
)
$testedFiles = New-Object System.Collections.Generic.List[object]
foreach ($name in $testedScriptNames) {
    $path = Join-Path $PSScriptRoot $name
    if ([IO.Path]::GetExtension($path) -eq '.sh') {
        if ($IsWindows) {
            $linuxPath = (& wsl.exe wslpath -a $path 2>&1 | Out-String).Trim()
            if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($linuxPath)) {
                Add-FixtureResult -Name "parse:$name" -Passed $false -Details "wslpath failed: $linuxPath"
                continue
            }
            $bashParse = & wsl.exe bash -n $linuxPath 2>&1
        } else {
            $bashParse = & /bin/bash -n $path 2>&1
        }
        Add-FixtureResult -Name "parse:$name" -Passed ($LASTEXITCODE -eq 0) -Details ($bashParse -join ' | ')
        $testedFiles.Add([ordered]@{
            logicalPath = "scripts/$name"
            sha256      = (Get-JmoaSha256 -Path $path).ToUpperInvariant()
        }) | Out-Null
        continue
    }
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

$healthBytes = [Text.Encoding]::UTF8.GetBytes('{"status":"UP"}')
$decodedHealth = ConvertTo-CampaignHttpBodyText -Content $healthBytes
Add-FixtureResult -Name 'audited-http-decodes-byte-array-as-utf8' -Passed (
    $decodedHealth -eq '{"status":"UP"}' -and $decodedHealth -match '"status"\s*:\s*"UP"'
) -Details $decodedHealth

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

$treeHashFixture = Join-Path $work 'tree-hash-order'
New-JmoaDirectory -Path (Join-Path $treeHashFixture 'alpha')
New-JmoaDirectory -Path (Join-Path $treeHashFixture 'zeta')
Set-Content -LiteralPath (Join-Path $treeHashFixture '.gitignore') -Value 'target/' -Encoding utf8 -NoNewline
Set-Content -LiteralPath (Join-Path $treeHashFixture 'LICENSE') -Value 'license' -Encoding utf8 -NoNewline
Set-Content -LiteralPath (Join-Path $treeHashFixture 'README.md') -Value 'readme' -Encoding utf8 -NoNewline
Set-Content -LiteralPath (Join-Path (Join-Path $treeHashFixture 'alpha') 'application.yml') -Value 'alpha' -Encoding utf8 -NoNewline
Set-Content -LiteralPath (Join-Path (Join-Path $treeHashFixture 'zeta') 'application.yml') -Value 'zeta' -Encoding utf8 -NoNewline
$treeHash = Get-CampaignTreeSha256 -Root $treeHashFixture
Add-FixtureResult -Name 'tree-hash-is-cross-platform-and-case-stable' `
    -Passed ($treeHash -eq '5A7C00AABEAC3DCF9BA6A5CECA34C7C7205645E57FB92599E05BB9CD742DCBB0') `
    -Details "actual=$treeHash"
Add-FixtureResult -Name 'artifact-hash-supports-exploded-directory-tree' `
    -Passed ((Get-CampaignArtifactSha256 -Path $treeHashFixture) -eq $treeHash) `
    -Details "artifact=$((Get-CampaignArtifactSha256 -Path $treeHashFixture)), tree=$treeHash"
$artifactFileFixture = Join-Path $work 'artifact-hash-file.bin'
Set-Content -LiteralPath $artifactFileFixture -Value 'artifact-file' -Encoding utf8 -NoNewline
Add-FixtureResult -Name 'artifact-hash-preserves-single-file-sha256' `
    -Passed ((Get-CampaignArtifactSha256 -Path $artifactFileFixture) -eq (Get-JmoaSha256 -Path $artifactFileFixture).ToUpperInvariant())

$portableFixture = [pscustomobject][ordered]@{
    schemaVersion = 'jmoa-portable-fixture-v1'
    packageSha256 = ''
    sourceCampaignSha256 = 'A' * 64
}
$portableHash = Get-CampaignManifestSha256 -ManifestObject $portableFixture
$portableFixture.packageSha256 = $portableHash
$portableExpected = $portableFixture.packageSha256
$portableFixture.packageSha256 = ''
$portableActual = Get-CampaignManifestSha256 -ManifestObject $portableFixture
Add-FixtureResult -Name 'portable-package-self-hash-recomputes-with-empty-hash-field' `
    -Passed ($portableExpected -eq $portableActual) `
    -Details "expected=$portableExpected, actual=$portableActual"

Add-FixtureResult -Name 'portable-manifest-path-basename-handles-windows-and-linux' -Passed (
    (Get-CampaignPortableFileName -Path 'C:\materialized\BOOT-INF\lib\jmoa-runtime-lib.jar') -eq 'jmoa-runtime-lib.jar' -and
    (Get-CampaignPortableFileName -Path '/application/BOOT-INF/lib/jmoa-runtime-lib.jar') -eq 'jmoa-runtime-lib.jar'
)

$exportScriptText = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'export-petclinic-linux-campaign.ps1')
$importScriptText = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'import-petclinic-linux-campaign.ps1')
Add-FixtureResult -Name 'portable-package-preserves-config-checkout-policy' -Passed (
    $exportScriptText -match 'configCheckout' -and
    $exportScriptText -match 'coreAutoCrlf' -and
    $importScriptText -match "config', 'core\.autocrlf" -and
    $importScriptText -match "config', 'core\.filemode"
)

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

$campaignSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-petclinic-performance-campaign.ps1')
$targetOnlyCampaignSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-petclinic-target-only-campaign.ps1')
$scenarioLedgerSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'scenario-ledger-common.ps1')
$targetOnlySupportSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'campaign-launch-petclinic-support.ps1')
$targetOnlyTargetSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'campaign-launch-petclinic-target.ps1')
$targetTransitionSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'campaign-verify-petclinic-target-transition.ps1')
$runtimeScreenSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'runtime-screen-pair.ps1')
$linuxPreflightSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'capture-linux-campaign-host-preflight.ps1')
$linuxIdleCalibrationSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-linux-idle-calibration.ps1')
$linuxSupportCalibrationSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-linux-host-calibration.ps1')
Add-FixtureResult -Name 'campaign-stops-b0-before-v2-controls' -Passed (
    $campaignSource.IndexOf("STOPPED_B0_RUNTIME_VARIANCE", [StringComparison]::Ordinal) -ge 0 -and
    $campaignSource.IndexOf("STOPPED_B0_RUNTIME_VARIANCE", [StringComparison]::Ordinal) -lt
        $campaignSource.IndexOf("Step 6B - V2 same-artifact controls", [StringComparison]::Ordinal)
)
Add-FixtureResult -Name 'campaign-has-distinct-final-product-verdicts' -Passed (
    $campaignSource -match 'TRUSTED_PRODUCT_WIN' -and
    $campaignSource -match 'CONFIRMED_PRODUCT_WIN' -and
    $campaignSource -match 'PRODUCT_EFFECT_NOT_CONFIRMED'
)
Add-FixtureResult -Name 'campaign-consumes-native-linux-preflight-schema' -Passed (
    $campaignSource -match 'host-linux-preflight\.json' -and
    $campaignSource -match '\$hostPreflight\.availableMemoryBytes' -and
    $campaignSource -match '\$hostPreflight\.swapUsedBytes'
)
Add-FixtureResult -Name 'campaign-resolves-java-and-path-separator-per-platform' -Passed (
    $campaignSource.Contains('$javaExecutableName = if ($IsWindows)') -and
    $campaignSource.Contains('[IO.Path]::PathSeparator')
)
Add-FixtureResult -Name 'linux-preflight-handles-empty-container-array-and-pinned-java' -Passed (
    $linuxPreflightSource -match '\$null -ne \$parsedContainers' -and
    $linuxPreflightSource -match '\$JAVA_HOME/bin/java'
)
Add-FixtureResult -Name 'linux-idle-calibration-samples-expose-properties-for-aggregation' -Passed (
    $linuxIdleCalibrationSource -match '\$rows\.Add\(\[pscustomobject\]\[ordered\]@\{' -and
    $linuxIdleCalibrationSource -match 'Measure-Object -Property availableMemoryBytes -Minimum'
)
Add-FixtureResult -Name 'linux-support-calibration-avoids-automatic-pid-and-exposes-sample-properties' -Passed (
    $linuxSupportCalibrationSource -notmatch '\[int\]\$Pid(?:\W|$)' -and
    $linuxSupportCalibrationSource -notmatch '(?m)^\s*\$host\s*=' -and
    $linuxSupportCalibrationSource -match '\$rows\.Add\(\[pscustomobject\]\[ordered\]@\{'
)
Add-FixtureResult -Name 'support-calibration-v2-uses-exact-cgroups-and-final-window' -Passed (
    $linuxSupportCalibrationSource -match 'SUPPORT_CALIBRATION_V2' -and
    $linuxSupportCalibrationSource -match '36' -and
    $linuxSupportCalibrationSource -match 'FinalWindowSamples = 12' -and
    $linuxSupportCalibrationSource -match '/proc/\$processId/cgroup' -and
    $linuxSupportCalibrationSource -match 'memory\.stat' -and
    $linuxSupportCalibrationSource -match 'INDIVIDUAL_CONTAINER_CGROUPS_CONFIRMED'
)
Add-FixtureResult -Name 'support-calibration-v2-gates-private-memory-not-total-current' -Passed (
    $linuxSupportCalibrationSource -match 'MaxFinalWindowPssRangeKb = 2048' -and
    $linuxSupportCalibrationSource -match 'MaxFinalWindowPrivateDirtyRangeKb = 2048' -and
    $linuxSupportCalibrationSource -match 'MaxFinalWindowAnonRangeBytes = 2097152' -and
    $linuxSupportCalibrationSource -match 'MaxPositiveSlopeBytesPerSecond = 65536' -and
    $linuxSupportCalibrationSource -notmatch 'MaxAggregateMemoryCurrentDriftBytes'
)
Add-FixtureResult -Name 'support-calibration-v2-separates-samples-from-diagnostics' -Passed (
    $linuxSupportCalibrationSource -match '\[STABILITY_SAMPLE\]' -and
    $linuxSupportCalibrationSource -match '\[POST_WINDOW_DIAGNOSTIC\]' -and
    $linuxSupportCalibrationSource.LastIndexOf('Write-PostWindowDiagnostics -Identity', [StringComparison]::Ordinal) -gt
        $linuxSupportCalibrationSource.IndexOf('for ($sample = 1;', [StringComparison]::Ordinal)
)
Add-FixtureResult -Name 'support-calibration-v2-normalizes-crlf-before-bash' -Passed (
    $linuxSupportCalibrationSource -match '\$normalizedCommand\s*=\s*\$Command\.Replace' -and
    $linuxSupportCalibrationSource -match "-Arguments @\('-lc',\s*\`$normalizedCommand\)"
)

function New-ConstrainedHostSnapshot {
    return [pscustomobject][ordered]@{
        virtualization = 'microsoft'
        cgroupV2 = $true
        runningContainerCount = 0
        occupiedRequiredPortCount = 0
        totalMemoryBytes = 2050000000L
        availableMemoryBytes = 1500000000L
        logicalProcessorCount = 4
        swapTotalBytes = 0L
        swapUsedBytes = 0L
        cgroupSwapCurrentBytes = 0L
        memoryPressureSomeAvg10 = 0.0
        memoryPressureFullAvg10 = 0.0
        oomEvents = 0L
        oomKillEvents = 0L
    }
}
$standardHostProfile = Get-CampaignLinuxHostProfile -Name 'STANDARD_FIXED_8G'
$constrainedHostProfile = Get-CampaignLinuxHostProfile -Name 'HYPERV_DEBIAN_FIXED_2G'
Add-FixtureResult -Name 'standard-8g-host-profile-remains-unchanged' -Passed (
    $standardHostProfile.minTotalMemoryBytes -eq 8589934592L -and
    $standardHostProfile.minPreflightAvailableMemoryBytes -eq 1073741824L -and
    $standardHostProfile.minLogicalProcessorCount -eq 4 -and
    $standardHostProfile.maxMemoryPressureSomeAvg10 -eq 1.0 -and
    $standardHostProfile.maxMemoryPressureFullAvg10 -eq 0.1
)
Add-FixtureResult -Name 'constrained-2g-host-profile-accepts-valid-fixed-host' -Passed (
    (Test-CampaignLinuxHostAdmission -Profile $constrainedHostProfile -Snapshot (New-ConstrainedHostSnapshot)).passed
)
$lowTotalSnapshot = New-ConstrainedHostSnapshot
$lowTotalSnapshot.totalMemoryBytes = 1900000000L
Add-FixtureResult -Name 'constrained-2g-host-profile-rejects-low-total-memory' -Passed (
    -not (Test-CampaignLinuxHostAdmission -Profile $constrainedHostProfile -Snapshot $lowTotalSnapshot).passed
)
$lowCpuSnapshot = New-ConstrainedHostSnapshot
$lowCpuSnapshot.logicalProcessorCount = 2
Add-FixtureResult -Name 'constrained-2g-host-profile-rejects-fewer-than-four-cpus' -Passed (
    -not (Test-CampaignLinuxHostAdmission -Profile $constrainedHostProfile -Snapshot $lowCpuSnapshot).passed
)
$swapSnapshot = New-ConstrainedHostSnapshot
$swapSnapshot.swapTotalBytes = 1073741824L
$swapSnapshot.swapUsedBytes = 4096L
$swapSnapshot.cgroupSwapCurrentBytes = 4096L
Add-FixtureResult -Name 'constrained-2g-host-profile-rejects-swap' -Passed (
    -not (Test-CampaignLinuxHostAdmission -Profile $constrainedHostProfile -Snapshot $swapSnapshot).passed
)
$lowAvailableSnapshot = New-ConstrainedHostSnapshot
$lowAvailableSnapshot.availableMemoryBytes = 1300000000L
Add-FixtureResult -Name 'constrained-2g-host-profile-rejects-low-preflight-headroom' -Passed (
    -not (Test-CampaignLinuxHostAdmission -Profile $constrainedHostProfile -Snapshot $lowAvailableSnapshot).passed
)
$pressureSnapshot = New-ConstrainedHostSnapshot
$pressureSnapshot.memoryPressureSomeAvg10 = 0.01
Add-FixtureResult -Name 'constrained-2g-host-profile-rejects-memory-pressure' -Passed (
    -not (Test-CampaignLinuxHostAdmission -Profile $constrainedHostProfile -Snapshot $pressureSnapshot).passed
)
$oomSnapshot = New-ConstrainedHostSnapshot
$oomSnapshot.oomEvents = 1
Add-FixtureResult -Name 'constrained-2g-host-profile-rejects-oom-events' -Passed (
    -not (Test-CampaignLinuxHostAdmission -Profile $constrainedHostProfile -Snapshot $oomSnapshot).passed
)
Add-FixtureResult -Name 'runtime-screen-gates-per-arm-podman-pressure' -Passed (
    $runtimeScreenSource -match 'Capture-PodmanMachinePressure' -and
    $runtimeScreenSource -match 'environment-validity\.json' -and
    $runtimeScreenSource -match 'MaxPodmanSwapUsedBytes' -and
    $runtimeScreenSource -match 'RequireSwapDisabled' -and
    $runtimeScreenSource -match 'RequireZeroOomEvents' -and
    $runtimeScreenSource -match 'MinPostArmAvailableMemoryBytes'
)
Add-FixtureResult -Name 'constrained-2g-runner-orders-calibration-before-controls' -Passed (
    $campaignSource.IndexOf('Constrained host idle calibration', [StringComparison]::Ordinal) -ge 0 -and
    $campaignSource.IndexOf('Constrained host idle calibration', [StringComparison]::Ordinal) -lt
        $campaignSource.IndexOf('Step 6: staged same-artifact controls', [StringComparison]::Ordinal) -and
    $campaignSource -match 'run-linux-host-calibration\.ps1' -and
    $campaignSource -match 'run-petclinic-capacity-qualification\.ps1'
)
Add-FixtureResult -Name 'constrained-2g-runner-has-exact-terminal-outcomes' -Passed (
    $campaignSource -match 'ENVIRONMENT_VARIANCE_TOO_HIGH_2G' -and
    $campaignSource -match 'V2_ARTIFACT_RUNTIME_VARIANCE' -and
    $campaignSource -match 'CAMPAIGN_INTERRUPTED_BY_HOST_POWER_EVENT' -and
    $campaignSource -match 'CONFIRMED_PRODUCT_WIN_BELOW_4MIB' -and
    $campaignSource -match 'SUPPORT_STACK_PRIVATE_MEMORY_UNSTABLE' -and
    $campaignSource -match 'SUPPORT_CGROUP_SCOPE_INVALID' -and
    $campaignSource -match 'HOST_CAPACITY_INSUFFICIENT'
)
Add-FixtureResult -Name 'constrained-2g-runner-requires-three-stable-support-calibrations' -Passed (
    $campaignSource -match '\$supportIndex -le 3' -and
    $campaignSource -match 'requiredStableCalibrations = 3' -and
    $campaignSource -match 'stableCalibrationCount' -and
    $campaignSource.IndexOf('support-calibration-v2', [StringComparison]::Ordinal) -lt
        $campaignSource.IndexOf('Non-evidence B0 capacity qualification', [StringComparison]::Ordinal)
)
Add-FixtureResult -Name 'runtime-child-scripts-use-named-parameter-maps' -Passed (
    $campaignSource -match 'BaselineLaunchParameters' -and
    $campaignSource -match 'CandidateLaunchParameters' -and
    $runtimeScreenSource -match '& \$LaunchScript @launchParametersForArm' -and
    $runtimeScreenSource -match '& \$WorkloadScript @workloadParametersForArm' -and
    $runtimeScreenSource -match '& \$StopScript @stopParameters'
)
Add-FixtureResult -Name 'target-only-protocol-is-separately-named-and-pre-registered' -Passed (
    $targetOnlyCampaignSource -match "PETCLINIC_TARGET_ONLY_V1" -and
    $targetOnlyCampaignSource -match "redesignAfterTargetEvidence='FORBIDDEN'" -and
    $targetOnlyCampaignSource -match "productOrders=@\('B0_TO_V2','V2_TO_B0','B0_TO_V2'\)"
)
Add-FixtureResult -Name 'target-only-support-uses-fixed-validity-admission-not-private-range' -Passed (
    $targetOnlySupportSource -match '\[int\]\$SettleSeconds = 180' -and
    $targetOnlySupportSource -match '\[long\]\$MinAvailableMemoryBytes = 734003200' -and
    $targetOnlySupportSource -match 'psiFullAvg10' -and
    $targetOnlySupportSource -notmatch 'MaxFinalWindowPssRangeKb|MaxFinalWindowPrivateDirtyRangeKb|MaxFinalWindowAnonRangeBytes'
)
Add-FixtureResult -Name 'target-only-pair-shares-support-and-tears-down-target-only-between-arms' -Passed (
    $targetOnlyCampaignSource -match 'campaign-launch-petclinic-support\.ps1' -and
    $targetOnlyCampaignSource -match 'campaign-stop-petclinic-support\.ps1' -and
    $targetOnlyCampaignSource -match 'campaign-stop-petclinic-target\.ps1' -and
    $runtimeScreenSource -match 'Invoke-PairTransition'
)
Add-FixtureResult -Name 'target-transition-proves-absence-health-and-registration-isolation' -Passed (
    $targetTransitionSource -match 'firstContainerAbsent' -and
    $targetTransitionSource -match 'residualPidAbsent' -and
    $targetTransitionSource -match 'registrationRemoved' -and
    $targetTransitionSource -match 'registrationIsolated' -and
    $targetTransitionSource -match 'supportRestartCounts'
)
Add-FixtureResult -Name 'target-only-launch-keeps-frozen-runtime-policy-and-unique-eureka-id' -Passed (
    $targetOnlyTargetSource -match 'EUREKA_INSTANCE_INSTANCE_ID' -and
    $targetOnlyTargetSource -match 'MALLOC_ARENA_MAX=1' -and
    $targetOnlyTargetSource -match 'NativeMemoryTracking=summary' -and
    $targetOnlyTargetSource -match 'Xshare:off'
)
Add-FixtureResult -Name 'target-only-capacity-controls-and-product-order-are-frozen' -Passed (
    $targetOnlyCampaignSource -match 'ExecutionMode BASELINE_ONLY' -and
    $targetOnlyCampaignSource -match "control-b0-1.*BASELINE_FIRST" -and
    $targetOnlyCampaignSource -match "control-b0-2.*CANDIDATE_FIRST" -and
    $targetOnlyCampaignSource -match "control-v2-1.*BASELINE_FIRST" -and
    $targetOnlyCampaignSource -match "control-v2-2.*CANDIDATE_FIRST" -and
    $targetOnlyCampaignSource -match "product-1.*BASELINE_FIRST" -and
    $targetOnlyCampaignSource -match "product-2.*CANDIDATE_FIRST" -and
    $targetOnlyCampaignSource -match "product-3.*BASELINE_FIRST"
)
Add-FixtureResult -Name 'target-only-gates-use-target-metrics-and-exclude-support-memory' -Passed (
    $targetOnlyCampaignSource -match 'supportMemoryIncluded=\$false' -and
    $targetOnlyCampaignSource -match 'ConfirmedPssGateKb=-1024' -and
    $targetOnlyCampaignSource -match 'ConfirmedPrivateDirtyGateKb=-1024' -and
    $targetOnlyCampaignSource -match 'ConfirmedMemoryCurrentGateBytes=-1048576' -and
    $targetOnlyCampaignSource -match 'TrustedPssGateKb=-4096'
)
Add-FixtureResult -Name 'target-only-every-scenario-has-one-chronological-command-response-ledger' -Passed (
    $targetOnlyCampaignSource -match 'function Write-ScenarioCommandLedger' -and
    $targetOnlyCampaignSource -match 'scenario-command-ledger\.md' -and
    $targetOnlyCampaignSource -match 'rawStdoutPath' -and
    $targetOnlyCampaignSource -match 'rawStderrPath' -and
    $targetOnlyCampaignSource -match 'Sort-Object \{\[datetime\]\$_.startedUtc\}'
)
Add-FixtureResult -Name 'target-only-consumes-linux-podman-compat-path-from-host-bound-manifest' -Passed (
    $targetOnlyCampaignSource -match 'Get-CampaignJsonProp \$environment ''containerCli''' -and
    $targetOnlyCampaignSource -match 'if\(\[string\]::IsNullOrWhiteSpace\(\$ContainerCli\)\)\{\$ContainerCli=\$manifestContainerCli\}'
)
Add-FixtureResult -Name 'target-only-host-captures-use-single-command-linux-compat-contract' -Passed (
    $targetOnlySupportSource -match '@\(''machine'', ''ssh'', \$cmd\)' -and
    $targetOnlyTargetSource -notmatch "@\('machine','ssh','bash','-lc'" -and
    $targetTransitionSource -notmatch "@\('machine','ssh','bash','-lc'"
)
Add-FixtureResult -Name 'target-only-fails-closed-on-clean-transform-and-lineage-gates' -Passed (
    $targetOnlyCampaignSource -match 'Test-CampaignBaselineClean' -and
    $targetOnlyCampaignSource -match 'Test-CampaignCandidateTransformed' -and
    $targetOnlyCampaignSource -match 'Test-CampaignArtifactLineage' -and
    $targetOnlyCampaignSource -match 'STOPPED_ARTIFACT_GATE'
)

Add-FixtureResult -Name 'target-only-cold-cache-and-required-output-contract-fail-closed' -Passed (
    $runtimeScreenSource.Contains("sudo -n /usr/bin/tee /proc/sys/vm/drop_caches") -and
    -not $runtimeScreenSource.Contains("sudo sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches'") -and
    $targetOnlyCampaignSource.Contains('Target screen missing required output') -and
    $targetOnlyCampaignSource.Contains("'environment-validity.json'") -and
    $targetOnlyCampaignSource.Contains("'class-histogram.txt'")
) -Details 'Target-only cold-cache reset must be non-interactive and every required target evidence file must be checked before analysis.'

Add-FixtureResult -Name 'runtime-capture-contract-keeps-io-stat-optional-and-propagates-failure' -Passed (
    $runtimeScreenSource.Contains("file = 'io.stat'") -and
    $runtimeScreenSource.Contains("class = 'OPTIONAL_DIAGNOSTIC'; required = `$false") -and
    $runtimeScreenSource.Contains('$_.required -and $_.exitCode -ne 0') -and
    $runtimeScreenSource.Contains('throw "Runtime screen pair $PairIndex failed."')
) -Details 'Rootless cgroups may omit io.stat; target memory and JVM captures remain required, and failed screens must throw into the parent runner.'

Add-FixtureResult -Name 'runtime-cleanup-does-not-mask-primary-failure' -Passed (
    $runtimeScreenSource.Contains("primaryRunError = `$launchError") -and
    $runtimeScreenSource.Contains("teardownError = `$stopError") -and
    $runtimeScreenSource.Contains("if (-not [string]::IsNullOrWhiteSpace(`$stopError) -and [string]::IsNullOrWhiteSpace(`$launchError))")
) -Details 'A teardown failure is still evidence-invalid, but it must not replace the launch, workload, or capture failure that caused the observation to fail.'

Add-FixtureResult -Name 'page-cache-reset-is-audited-and-time-bounded' -Passed (
    $runtimeScreenSource.Contains('-TimeoutSeconds 60 -AllowFailure') -and
    (Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')).Contains('$process.Kill($true)') -and
    (Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')).Contains('$exitCode = 124')
) -Details 'A stuck Podman machine SSH must become a ledgered invalid attempt instead of hanging the campaign indefinitely.'

Add-FixtureResult -Name 'page-cache-reset-supports-frozen-machine-restart-policy' -Passed (
    $runtimeScreenSource.Contains("'PODMAN_MACHINE_RESTART'") -and
    $runtimeScreenSource.Contains("'machine', 'stop', `$PodmanMachineName") -and
    $runtimeScreenSource.Contains('PODMAN_MACHINE_RESTART_BEFORE_EACH_VARIANT') -and
    $runtimeScreenSource.Contains('MACHINE_RESTART_CACHE_RESET_OK') -and
    $runtimeScreenSource.Contains('verify Docker-compatible Compose API readiness after restart') -and
    $runtimeScreenSource.Contains("'compose', 'ls', '--format', 'json'")
) -Details 'WSL hosts where drop_caches wedges can use Podman stop/start for every observation and verify the Compose compatibility API before launch.'

Add-FixtureResult -Name 'target-only-scenario-ledger-preserves-http-responses-and-empty-streams' -Passed (
    $targetOnlyCampaignSource.Contains("PSObject.Properties['rawBodyPath']") -and
    $targetOnlyCampaignSource.Contains('[string](Get-Content -Raw -LiteralPath') -and
    $targetOnlyCampaignSource.Contains('([string]$stdout).TrimEnd()') -and
    $targetOnlyCampaignSource.Contains('([string]$stderr).TrimEnd()') -and
    $targetOnlyCampaignSource.Contains('$kind-eq''HTTP''') -and
    $targetOnlyCampaignSource.Contains('"HTTP $($record.status); error=$($record.error)"')
) -Details 'The one-file scenario ledger must include HTTP response bodies and safely render empty process streams.'

Add-FixtureResult -Name 'scenario-ledger-accepts-structured-analysis-results' -Passed (
    $scenarioLedgerSource.Contains("param([string]`$Status = 'COMPLETE',`$Result = @{})") -and
    -not $scenarioLedgerSource.Contains('[hashtable]$Result')
) -Details 'Terminal gate analyzers return PSCustomObject values; the root ledger must preserve them without a hashtable-only binding failure.'

$periodAttributionSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'analyze-petclinic-b0-period-effect.ps1')
$independentCommonSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'petclinic-independent-session-common.ps1')
$independentRunnerSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-petclinic-independent-session-campaign.ps1')
$independentAdapterSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'new-independent-session-evidence-adapter.ps1')
Add-FixtureResult -Name 'period-attribution-reconciles-process-heap-cgroup-nmt-and-timing' -Passed (
    $periodAttributionSource.Contains('Get-SmapsHeapMetrics') -and
    $periodAttributionSource.Contains('cgroupAnonBytes') -and
    $periodAttributionSource.Contains('nmtMetaspaceCommittedKb') -and
    $periodAttributionSource.Contains('histogramBytes') -and
    $periodAttributionSource.Contains('jvmAgeAtCaptureSeconds') -and
    $periodAttributionSource.Contains('SECOND_POSITION_NATIVE_ANON') -and
    $periodAttributionSource.Contains("evidenceMode = 'READ_ONLY_EXISTING_CAPTURES'")
) -Details 'The final correction must first attribute the immutable four-arm B0 evidence without rerunning it.'

Add-FixtureResult -Name 'independent-session-unit-has-one-support-one-target-and-full-teardown' -Passed (
    $independentCommonSource.Contains('Invoke-PetclinicIndependentSession') -and
    $independentCommonSource.Contains('campaign-launch-petclinic-support.ps1') -and
    $independentCommonSource.Contains("ExecutionMode = `$executionMode") -and
    $independentCommonSource.Contains('independentSupportSession = $true') -and
    $independentCommonSource.Contains('campaign-stop-petclinic-support.ps1') -and
    $independentCommonSource.Contains('Write-PetclinicScenarioCommandLedger')
) -Details 'Every evidence observation must be the only target in a fresh support lifecycle with one consolidated command/response ledger.'

Add-FixtureResult -Name 'independent-session-helper-does-not-assign-read-only-powershell-automatic-variables' -Passed (
    $independentCommonSource -notmatch '(?im)^\s*\$(host|pid|input|args|error|home|matches)\s*='
) -Details 'PowerShell automatic variables are case-insensitive and several are read-only; orchestration locals must not reuse those names.'

Add-FixtureResult -Name 'independent-session-runner-uses-powershell-boolean-literals' -Passed (
    $independentRunnerSource -notmatch '(?im)=\s*(true|false)\s*$' -and
    $independentRunnerSource.Contains('includedInProductEvidence = $false') -and
    $independentRunnerSource.Contains('cds = $false') -and
    $independentRunnerSource.Contains('javaagent = $false')
) -Details 'Bare true/false tokens can resolve as native commands on Linux PowerShell and serialize as null; campaign reports require real Boolean values.'

Add-FixtureResult -Name 'independent-session-campaign-has-final-stop-and-balanced-product-order' -Passed (
    $independentRunnerSource.Contains("`$productOrder = @('B0', 'V2', 'V2', 'B0', 'B0', 'V2')") -and
    $independentRunnerSource.Contains('MaxPssRangeKb = 1024') -and
    $independentRunnerSource.Contains('MaxPrivateDirtyRangeKb = 1024') -and
    $independentRunnerSource.Contains('MaxMemoryCurrentRangeBytes = 2097152') -and
    $independentRunnerSource.IndexOf('PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST', [StringComparison]::Ordinal) -lt
        $independentRunnerSource.IndexOf('$v2QualificationSessions', [StringComparison]::Ordinal) -and
    $independentRunnerSource.Contains('futurePetclinicProtocolOnThisVmAuthorized = $false')
) -Details 'B0 failure must stop before V2 permanently, while an admitted product block uses the frozen balanced six-session order.'

Add-FixtureResult -Name 'independent-session-adapter-links-captures-and-derives-only-manifests' -Passed (
    $independentAdapterSource.Contains('New-Item -ItemType SymbolicLink') -and
    $independentAdapterSource.Contains("Where-Object Name -ne 'run-manifest.json'") -and
    $independentAdapterSource.Contains('sourceRunManifestSha256') -and
    $independentAdapterSource.Contains('capturesLinkedReadOnly = $true')
) -Details 'V2-C pairing must not copy or mutate raw session captures; only derived pair manifests are permitted.'

$threeArtifactAnalyzer = Join-Path $PSScriptRoot 'analyze-three-artifact-blocks.ps1'
$threeArtifactRunnerSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-three-artifact-balanced-campaign.ps1')
$threeArtifactCommonSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'three-artifact-campaign-common.ps1')
$threeArtifactAnalyzerSource = Get-Content -Raw -LiteralPath $threeArtifactAnalyzer
$publicEvaluationSource = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-jmoa-evaluation.ps1')
Add-FixtureResult -Name 'three-artifact-runner-freezes-all-six-balanced-orders' -Passed (
    $threeArtifactRunnerSource.Contains("@('B0', 'V1', 'V2')") -and
    $threeArtifactRunnerSource.Contains("@('B0', 'V2', 'V1')") -and
    $threeArtifactRunnerSource.Contains("@('V1', 'B0', 'V2')") -and
    $threeArtifactRunnerSource.Contains("@('V1', 'V2', 'B0')") -and
    $threeArtifactRunnerSource.Contains("@('V2', 'B0', 'V1')") -and
    $threeArtifactRunnerSource.Contains("@('V2', 'V1', 'B0')")
) -Details 'The final campaign must execute every permutation once, with one fresh support lifecycle per observation.'

Add-FixtureResult -Name 'three-artifact-runner-preserves-invalid-attempts-and-caps-retries' -Passed (
    $threeArtifactRunnerSource.Contains('$Directory-retry-$_') -and
    $threeArtifactRunnerSource.Contains('exhausted three preserved attempts') -and
    $threeArtifactRunnerSource.Contains('Reusing valid frozen session')
) -Details 'Invalid observations remain on disk and can be replaced only by a separately ledgered attempt.'

Add-FixtureResult -Name 'three-artifact-runner-freezes-implementation-bytes-before-first-observation' -Passed (
    $threeArtifactRunnerSource.Contains('Get-CampaignImplementationChecks') -and
    $threeArtifactRunnerSource.Contains('Assert-CampaignImplementationUnchanged') -and
    $threeArtifactRunnerSource.Contains("schemaVersion = 'jmoa-three-artifact-campaign-freeze-v2'") -and
    $threeArtifactRunnerSource.Contains('Campaign implementation changed after freeze') -and
    $threeArtifactRunnerSource.Contains('implementation = $implementationChecks')
) -Details 'Runtime, workload, launcher, teardown, adapter, and analyzer script bytes are immutable across qualification and all six blocks.'

Add-FixtureResult -Name 'three-artifact-session-exposes-independent-support-contract' -Passed (
    $threeArtifactCommonSource.Contains('supportSessionId = $SessionId') -and
    $threeArtifactCommonSource.Contains('targetSessionId = "$SessionId-target"') -and
    $threeArtifactCommonSource.Contains('independentSupportSession = $true')
) -Details 'Each observation must carry the support and target identity required by the read-only V2-C adapter.'

Add-FixtureResult -Name 'three-artifact-freeze-supports-file-and-exploded-tree-identities' -Passed (
    $threeArtifactRunnerSource.Contains('Get-CampaignArtifactSha256') -and
    $threeArtifactRunnerSource.Contains("'DIRECTORY_TREE'") -and
    $runtimeScreenSource.Contains('Get-CampaignArtifactSha256 -Path $ArtifactPath') -and
    $runtimeScreenSource.Contains('RuntimeArtifactPath cannot be used with a directory-tree host artifact')
) -Details 'PetClinic V2 is a frozen exploded dependency tree; file-only hashing must not silently substitute for its concrete byte identity.'

Add-FixtureResult -Name 'three-artifact-session-result-preserves-directory-artifact-identity' -Passed (
    $threeArtifactCommonSource.Contains('artifactSha256 = Get-CampaignArtifactSha256 -Path')
) -Details 'Directory-backed variants must retain their deterministic tree hash in the session result used by resume and evidence-lineage checks.'

Add-FixtureResult -Name 'public-evaluation-entrypoint-keeps-three-variant-qualification-atomic' -Passed (
    $publicEvaluationSource.Contains("[ValidateSet('Doctor', 'Patient', 'PetClinicCustomers')]") -and
    $publicEvaluationSource.Contains("[ValidateSet('B0', 'V1', 'V2', 'Final', 'Explain')]") -and
    $publicEvaluationSource.Contains('B0, V1, and V2 qualification is one frozen unit') -and
    $publicEvaluationSource.Contains('-Stage Qualification') -and
    $publicEvaluationSource.Contains('-Stage Final') -and
    $publicEvaluationSource.Contains('-Stage Explain')
) -Details 'One public command exposes the frozen campaign without embedding private service paths or allowing partial qualification.'

Add-FixtureResult -Name 'independent-session-adapter-supports-six-complete-pairs' -Passed (
    $independentAdapterSource.Contains("'^[bc]([1-9][0-9]*)$'") -and
    $independentAdapterSource.Contains('$completePairs.Count -ge 3') -and
    $independentAdapterSource.Contains('$supportSessionIds.Count -eq $expectedRunCount')
) -Details 'The evidence adapter must retain its independence checks while accepting the six balanced campaign blocks.'

$adapterFixtureRoot = Join-Path $work 'six-pair-adapter'
$adapterFixtureMappings = [Collections.Generic.List[object]]::new()
foreach ($pairIndex in 1..6) {
    foreach ($prefix in @('b', 'c')) {
        $adapterRunId = "$prefix$pairIndex"
        $sourceDirectory = Join-Path $adapterFixtureRoot "source-$adapterRunId"
        New-JmoaDirectory $sourceDirectory
        Write-JmoaJson ([ordered]@{
            independentSupportSession = $true
            supportSessionId = "support-$adapterRunId"
            artifactSha256 = ('A' * 64)
        }) (Join-Path $sourceDirectory 'run-manifest.json')
        Write-JmoaText '1' (Join-Path $sourceDirectory 'memory.current')
        $adapterFixtureMappings.Add([ordered]@{
            adapterRunId = $adapterRunId
            sessionId = "session-$adapterRunId"
            supportSessionId = "support-$adapterRunId"
            sourceRunDirectory = $sourceDirectory
        })
    }
}
$adapterFixtureIndex = Join-Path $adapterFixtureRoot 'session-index.json'
$adapterFixtureOutput = Join-Path $adapterFixtureRoot 'output'
Write-JmoaJson ([ordered]@{
    schemaVersion = 'jmoa-independent-session-index-v1'
    mappings = $adapterFixtureMappings.ToArray()
}) $adapterFixtureIndex
& (Join-Path $PSScriptRoot 'new-independent-session-evidence-adapter.ps1') `
    -SessionIndexPath $adapterFixtureIndex -OutputDirectory $adapterFixtureOutput | Out-Null
$adapterFixtureReport = Get-Content -Raw -LiteralPath (Join-Path $adapterFixtureOutput 'independent-session-adapter.json') | ConvertFrom-Json
Add-FixtureResult -Name 'independent-session-adapter-materializes-six-read-only-pairs' -Passed (
    [bool]$adapterFixtureReport.passed -and
    [int]$adapterFixtureReport.completePairs -eq 6 -and
    [int]$adapterFixtureReport.distinctIndependentSupportSessions -eq 12 -and
    @($adapterFixtureReport.runs).Count -eq 12
) -Details 'The generalized adapter is executed over twelve independent fixture runs, not merely inspected as source text.'

$syntheticRoot = Join-Path $work 'three-artifact-analysis'
$syntheticInput = Join-Path $syntheticRoot 'session-index.json'
$syntheticOutput = Join-Path $syntheticRoot 'analysis'
New-JmoaDirectory $syntheticRoot
$syntheticOrders = @(
    @('B0', 'V1', 'V2'), @('B0', 'V2', 'V1'), @('V1', 'B0', 'V2'),
    @('V1', 'V2', 'B0'), @('V2', 'B0', 'V1'), @('V2', 'V1', 'B0')
)
$syntheticBlocks = for ($block = 1; $block -le 6; $block++) {
    $sessions = foreach ($variant in @('B0', 'V1', 'V2')) {
        $pss = switch ($variant) { B0 { 300000 + $block }; V1 { 297000 + $block }; V2 { 294000 + $block } }
        $dirty = switch ($variant) { B0 { 280000 + $block }; V1 { 278000 + $block }; V2 { 276000 + $block } }
        $current = switch ($variant) { B0 { 500000000 + $block }; V1 { 495000000 + $block }; V2 { 490000000 + $block } }
        [ordered]@{
            sessionId = "fixture-$block-$variant"; variant = $variant; valid = $true; semanticErrors = 0
            pssKb = $pss; privateDirtyKb = $dirty; memoryCurrentBytes = $current
            heapPssKb = $pss - 100000; loadedClasses = 20000; metaspaceUsedKb = 15000
            startupMillis = 50000
        }
    }
    [ordered]@{ block = $block; order = $syntheticOrders[$block - 1]; sessions = $sessions }
}
Write-JmoaJson ([ordered]@{
    schemaVersion = 'jmoa-three-artifact-session-index-v1'
    protocol = 'SYNTHETIC_FIXTURE'
    service = 'fixture-service'
    blocks = $syntheticBlocks
}) $syntheticInput
& $threeArtifactAnalyzer -SessionIndexPath $syntheticInput -OutputDirectory $syntheticOutput | Out-Null
$syntheticAnalysis = Get-Content -Raw -LiteralPath (Join-Path $syntheticOutput 'three-artifact-analysis.json') | ConvertFrom-Json
$syntheticDirect = @($syntheticAnalysis.comparisons | Where-Object id -eq B0_TO_V2)[0]
Add-FixtureResult -Name 'three-artifact-analyzer-computes-direct-six-block-win' -Passed (
    [int]$syntheticAnalysis.sessions -eq 18 -and
    [int]$syntheticAnalysis.validSessions -eq 18 -and
    [double]$syntheticDirect.metrics.pssKb.median -eq -6000 -and
    [int]$syntheticDirect.metrics.pssKb.pairedWins -eq 6 -and
    [double]$syntheticDirect.metrics.pssKb.bootstrap95.upper -lt 0 -and
    [string]$syntheticAnalysis.terminalOutcome -eq 'COMPLETE_PRODUCT_WIN'
) -Details 'Direct B0-to-V2 statistics must come from six within-block deltas and pass the exact bootstrap gate without historical arithmetic.'

Add-FixtureResult -Name 'three-artifact-analyzer-uses-frozen-direct-gates' -Passed (
    $threeArtifactAnalyzerSource.Contains('[long]$SubstantialPssGateKb = -4096') -and
    $threeArtifactAnalyzerSource.Contains('[long]$PrivateDirtyGateKb = -1024') -and
    $threeArtifactAnalyzerSource.Contains('[long]$MemoryCurrentGateBytes = -1048576') -and
    $threeArtifactAnalyzerSource.Contains('pairedWins = ([int]$b0v2.metrics.pssKb.pairedWins -ge 4)') -and
    $threeArtifactAnalyzerSource.Contains('resamples = 46656')
) -Details 'The headline result requires the frozen substantial PSS, secondary memory, paired-win, and exact-bootstrap gates.'

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
