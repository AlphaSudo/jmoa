param(
    [Parameter(ParameterSetName = 'workflow', Mandatory)][string]$ConfigPath,
    [Parameter(ParameterSetName = 'replay', Mandatory)][string]$ReplaySuite,
    [ValidateSet('analyze', 'execute', 'replay')][string]$Mode = 'analyze',
    [ValidateSet('recommendations', 'preflight', 'all')][string]$ExecuteThrough = 'recommendations',
    [string]$OutputDir = '',
    [string]$MavenExecutable = 'mvn',
    [string]$PluginCoordinates = 'com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2',
    [switch]$FailOnBlocked
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$WorkflowStates = @(
    'NOT_STARTED', 'REDUCER_RECOMMENDED', 'REDUCER_BLOCKED',
    'RUNTIME_POLICY_RECOMMENDED', 'RUNTIME_POLICY_BLOCKED',
    'PREFLIGHT_READY_FOR_SMOKE', 'PREFLIGHT_BLOCKED',
    'MATERIALIZATION_PASSED', 'MATERIALIZATION_FAILED',
    'SEMANTIC_SMOKE_PASSED', 'SEMANTIC_SMOKE_FAILED',
    'SCREEN_PASSED', 'SCREEN_FAILED', 'CONFIRMATION_PASSED',
    'CONFIRMATION_FAILED', 'CLAIM_ALLOWED', 'CLAIM_BLOCKED'
)

function Get-JmoaWorkflowReport {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    try { return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json } catch { throw "Workflow input is not JSON: $Path" }
}

function Get-JmoaWorkflowValue {
    param($Report, [string[]]$Names, [string]$Default = '')
    if ($null -eq $Report) { return $Default }
    foreach ($name in $Names) {
        $property = $Report.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value -and -not [string]::IsNullOrWhiteSpace([string]$property.Value)) {
            return [string]$property.Value
        }
    }
    return $Default
}

function Test-JmoaPassedReport {
    param([string]$Path)
    $report = Get-JmoaWorkflowReport -Path $Path
    return $null -ne $report -and (Get-JmoaWorkflowValue -Report $report -Names @('status')) -eq 'PASSED'
}

function Get-JmoaStepSpec {
    param($Config, [string]$Name)
    $property = $Config.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Invoke-JmoaStepSpec {
    param($Step, [string]$Name)
    if ($null -eq $Step) { throw "V2-P cannot execute missing $Name step." }
    $scriptPath = Get-JmoaWorkflowValue -Report $Step -Names @('script')
    if ([string]::IsNullOrWhiteSpace($scriptPath) -or -not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        throw "V2-P $Name step script is missing: $scriptPath"
    }
    $arguments = @()
    $argumentProperty = $Step.PSObject.Properties['arguments']
    if ($null -ne $argumentProperty -and $null -ne $argumentProperty.Value) { $arguments = @($argumentProperty.Value) }
    & $scriptPath @arguments
    if ($LASTEXITCODE -ne 0) { throw "V2-P $Name step failed with exit code $LASTEXITCODE." }
}

function Write-JmoaWorkflowMarkdown {
    param($Report, [string]$Path)
    $lines = [System.Collections.Generic.List[string]]::new()
    [void]$lines.Add('# JMOA Runtime Workflow Report')
    [void]$lines.Add('')
    [void]$lines.Add("- Service: ``$($Report.service)``")
    [void]$lines.Add("- Workflow state: ``$($Report.workflowState)``")
    [void]$lines.Add("- Claim allowed: ``$($Report.claimAllowed)``")
    [void]$lines.Add("- Execution mode: ``$($Report.executionMode)``")
    [void]$lines.Add('')
    [void]$lines.Add('## Gates')
    foreach ($gate in $Report.gates) { [void]$lines.Add("- ``$($gate.name)``: ``$($gate.status)`` ($($gate.detail))") }
    [void]$lines.Add('')
    [void]$lines.Add('## Claim Boundary')
    foreach ($boundary in $Report.claimBoundary) { [void]$lines.Add("- $boundary") }
    [void]$lines.Add('')
    [void]$lines.Add('This workflow report coordinates existing gates. It does not create a performance claim or update the claim register.')
    Write-JmoaText -Value ($lines -join [Environment]::NewLine) -Path $Path
}

function New-JmoaWorkflowReport {
    param($Config, [string]$ExecutionMode)
    $reducer = Get-JmoaWorkflowReport (Get-JmoaWorkflowValue -Report $Config -Names @('reducerRecommendationReport'))
    $runtime = Get-JmoaWorkflowReport (Get-JmoaWorkflowValue -Report $Config -Names @('runtimeRecommendationReport'))
    $preflight = Get-JmoaWorkflowReport (Get-JmoaWorkflowValue -Report $Config -Names @('preflightReport'))
    $reducerDecision = Get-JmoaWorkflowValue $Config @('reducerDecision') (Get-JmoaWorkflowValue $reducer @('decision') 'NOT_RUN')
    $runtimeDecision = Get-JmoaWorkflowValue $Config @('runtimeDecision') (Get-JmoaWorkflowValue $runtime @('decision') 'NOT_RUN')
    $preflightState = Get-JmoaWorkflowValue $Config @('preflightState') (Get-JmoaWorkflowValue $preflight @('readiness', 'state') 'NOT_RUN')
    $materializationPassed = if ($null -ne $Config.PSObject.Properties['materializationPassed']) { [bool]$Config.materializationPassed } else { Test-JmoaPassedReport (Get-JmoaWorkflowValue $Config @('materializationProofReport')) }
    $baselineSmokePassed = if ($null -ne $Config.PSObject.Properties['baselineSemanticSmokePassed']) { [bool]$Config.baselineSemanticSmokePassed } else { Test-JmoaPassedReport (Get-JmoaWorkflowValue $Config @('baselineSemanticSmokeReport')) }
    $candidateSmokePassed = if ($null -ne $Config.PSObject.Properties['candidateSemanticSmokePassed']) { [bool]$Config.candidateSemanticSmokePassed } else { Test-JmoaPassedReport (Get-JmoaWorkflowValue $Config @('candidateSemanticSmokeReport')) }
    $screen = Get-JmoaWorkflowReport (Get-JmoaWorkflowValue $Config @('screenReport'))
    $confirmation = Get-JmoaWorkflowReport (Get-JmoaWorkflowValue $Config @('confirmationReport', 'v2cReport'))
    $attribution = Get-JmoaWorkflowReport (Get-JmoaWorkflowValue $Config @('attributionReport', 'v2dReport'))
    $screenStatus = Get-JmoaWorkflowValue $Config @('screenStatus') (Get-JmoaWorkflowValue $screen @('status', 'verdict') 'NOT_RUN')
    $confirmationVerdict = Get-JmoaWorkflowValue $Config @('confirmationVerdict') (Get-JmoaWorkflowValue $confirmation @('verdict', 'status') 'NOT_RUN')
    $attributionStatus = Get-JmoaWorkflowValue $Config @('attributionStatus') (Get-JmoaWorkflowValue $attribution @('status', 'verdict') 'NOT_RUN')
    $state = 'NOT_STARTED'
    if ($reducerDecision -like 'BLOCK*') { $state = 'REDUCER_BLOCKED' }
    elseif ($runtimeDecision -like 'BLOCK*') { $state = 'RUNTIME_POLICY_BLOCKED' }
    elseif ($preflightState -like 'BLOCK*') { $state = 'PREFLIGHT_BLOCKED' }
    elseif ($preflightState -like 'READY_FOR_*') { $state = 'PREFLIGHT_READY_FOR_SMOKE' }
    elseif ($reducerDecision -like 'RECOMMEND*') { $state = 'REDUCER_RECOMMENDED' }
    elseif ($runtimeDecision -like 'RECOMMEND*') { $state = 'RUNTIME_POLICY_RECOMMENDED' }
    if ($state -notlike '*BLOCKED' -and $materializationPassed) { $state = 'MATERIALIZATION_PASSED' }
    elseif ($state -notlike '*BLOCKED' -and (Get-JmoaWorkflowValue $Config @('materializationProofReport'))) { $state = 'MATERIALIZATION_FAILED' }
    if ($state -eq 'MATERIALIZATION_PASSED' -and $baselineSmokePassed -and $candidateSmokePassed) { $state = 'SEMANTIC_SMOKE_PASSED' }
    elseif ($state -eq 'MATERIALIZATION_PASSED' -and ((Get-JmoaWorkflowValue $Config @('baselineSemanticSmokeReport')) -or (Get-JmoaWorkflowValue $Config @('candidateSemanticSmokeReport')))) { $state = 'SEMANTIC_SMOKE_FAILED' }
    if ($state -eq 'SEMANTIC_SMOKE_PASSED' -and $screenStatus -match 'PASSED') { $state = 'SCREEN_PASSED' }
    elseif ($state -eq 'SEMANTIC_SMOKE_PASSED' -and $screenStatus -ne 'NOT_RUN') { $state = 'SCREEN_FAILED' }
    if ($state -eq 'SCREEN_PASSED' -and $confirmationVerdict -eq 'CONFIRMED_WIN' -and $attributionStatus -match '^(PASSED|ANALYZED|CONFIRMED)') { $state = 'CONFIRMATION_PASSED' }
    elseif ($state -eq 'SCREEN_PASSED' -and $confirmationVerdict -ne 'NOT_RUN') { $state = 'CONFIRMATION_FAILED' }
    $claimAllowed = $state -eq 'CONFIRMATION_PASSED'
    if ($claimAllowed) { $state = 'CLAIM_ALLOWED' }
    elseif ($state -match '(BLOCKED|FAILED)' -or $confirmationVerdict -match '(REGRESSION|INVALID|NO_CLAIM)') { $state = 'CLAIM_BLOCKED' }
    $gates = @(
        [ordered]@{ name = 'reducerRecommendation'; status = $reducerDecision; detail = (Get-JmoaWorkflowValue $Config @('reducerRecommendationReport') 'not supplied') },
        [ordered]@{ name = 'runtimePolicyRecommendation'; status = $runtimeDecision; detail = (Get-JmoaWorkflowValue $Config @('runtimeRecommendationReport') 'not supplied') },
        [ordered]@{ name = 'preflight'; status = $preflightState; detail = (Get-JmoaWorkflowValue $Config @('preflightReport') 'not supplied') },
        [ordered]@{ name = 'materialization'; status = $(if ($materializationPassed) {'PASSED'} else {'NOT_PASSED'}); detail = (Get-JmoaWorkflowValue $Config @('materializationProofReport') 'not supplied') },
        [ordered]@{ name = 'semanticSmoke'; status = $(if ($baselineSmokePassed -and $candidateSmokePassed) {'PASSED'} else {'NOT_PASSED'}); detail = 'Both baseline and candidate smoke reports are required.' },
        [ordered]@{ name = 'screen'; status = $screenStatus; detail = (Get-JmoaWorkflowValue $Config @('screenReport') 'not supplied') },
        [ordered]@{ name = 'confirmation'; status = $confirmationVerdict; detail = (Get-JmoaWorkflowValue $Config @('confirmationReport', 'v2cReport') 'not supplied') },
        [ordered]@{ name = 'attribution'; status = $attributionStatus; detail = (Get-JmoaWorkflowValue $Config @('attributionReport', 'v2dReport') 'not supplied') }
    )
    return [ordered]@{
        metadataVersion = 'v2p-runtime-workflow-report'
        generatedAt = (Get-Date).ToUniversalTime().ToString('o')
        executionMode = $ExecutionMode
        service = Get-JmoaWorkflowValue $Config @('service') 'UNKNOWN'
        artifact = Get-JmoaWorkflowValue $Config @('artifact') 'UNKNOWN'
        scope = Get-JmoaWorkflowValue $Config @('scope') 'UNKNOWN'
        reducerEngine = Get-JmoaWorkflowValue $Config @('reducerEngine') 'UNKNOWN'
        launchMode = Get-JmoaWorkflowValue $Config @('launchMode') 'UNKNOWN'
        runtimePolicy = Get-JmoaWorkflowValue $Config @('runtimePolicy') 'UNKNOWN'
        workflowState = $state
        claimAllowed = $claimAllowed
        claimDeclared = $false
        claimRegisterReference = Get-JmoaWorkflowValue $Config @('claimRegisterReference') ''
        gates = $gates
        claimBoundary = @(
            'A workflow replay or dry run is not new runtime evidence.',
            'V2-C remains the confirmation verdict gate and V2-D remains the attribution gate.',
            'CLAIM_ALLOWED is eligibility only; a human must review and update the claim register separately.'
        )
    }
}

function Invoke-JmoaWorkflowReplay {
    param([string]$SuitePath, [string]$Destination)
    $suite = Get-JmoaWorkflowReport -Path $SuitePath
    $results = @()
    foreach ($case in @($suite.cases)) {
        $config = [pscustomobject]$case.input
        $report = New-JmoaWorkflowReport -Config $config -ExecutionMode 'replay'
        $passed = $report.workflowState -eq $case.expectedWorkflowState -and $report.claimAllowed -eq [bool]$case.expectedClaimAllowed
        $results += [ordered]@{ id = $case.id; expectedWorkflowState = $case.expectedWorkflowState; actualWorkflowState = $report.workflowState; expectedClaimAllowed = [bool]$case.expectedClaimAllowed; actualClaimAllowed = $report.claimAllowed; passed = $passed }
    }
    $replay = [ordered]@{ metadataVersion = 'v2p-workflow-replay'; generatedAt = (Get-Date).ToUniversalTime().ToString('o'); cases = $results.Count; passedCases = @($results | Where-Object passed).Count; failedCases = @($results | Where-Object {-not $_.passed}).Count; results = $results }
    New-JmoaDirectory -Path $Destination
    Write-JmoaJson -Value $replay -Path (Join-Path $Destination 'jmoa-runtime-workflow-replay.json')
    Write-JmoaText -Value ("# JMOA Runtime Workflow Replay`n`nCases: $($replay.cases)`nPassed: $($replay.passedCases)`nFailed: $($replay.failedCases)") -Path (Join-Path $Destination 'jmoa-runtime-workflow-replay.md')
    if ($replay.failedCases -gt 0) { throw "V2-P workflow replay failed with $($replay.failedCases) mismatch(es)." }
}

if ($Mode -eq 'replay') {
    $destination = if ($OutputDir) { $OutputDir } else { Join-Path (Split-Path $ReplaySuite -Parent) 'target-v2p-workflow-replay' }
    Invoke-JmoaWorkflowReplay -SuitePath $ReplaySuite -Destination $destination
    Write-Host "V2-P workflow replay written to $destination"
    exit 0
}

$config = Get-JmoaWorkflowReport -Path $ConfigPath
if ($null -eq $config) { throw "Workflow config is required: $ConfigPath" }
$destination = if ($OutputDir) { $OutputDir } elseif ((Get-JmoaWorkflowValue $config @('outputDir'))) { Get-JmoaWorkflowValue $config @('outputDir') } else { Join-Path (Split-Path $ConfigPath -Parent) 'jmoa-runtime-workflow' }
New-JmoaDirectory -Path $destination

if ($Mode -eq 'execute') {
    $reducerInput = Get-JmoaWorkflowValue $config @('reducerRecommendationInputDir')
    $runtimeInput = Get-JmoaWorkflowValue $config @('runtimeRecommendationInputDir')
    if ([string]::IsNullOrWhiteSpace($reducerInput) -or [string]::IsNullOrWhiteSpace($runtimeInput)) { throw 'Execute mode requires reducerRecommendationInputDir and runtimeRecommendationInputDir.' }
    $reducerOut = Join-Path $destination 'reducer-recommendation'
    $runtimeOut = Join-Path $destination 'runtime-recommendation'
    $reducerArgs = @('-N', "${PluginCoordinates}:recommend-reducer", '-Djmoa.recommendation.enabled=true', "-Djmoa.recommendation.inputDir=$reducerInput", "-Djmoa.recommendation.outputDir=$reducerOut", "-Djmoa.recommendation.service=$(Get-JmoaWorkflowValue $config @('service'))", "-Djmoa.recommendation.launchMode=$(Get-JmoaWorkflowValue $config @('launchMode'))", "-Djmoa.recommendation.runtimePolicy=$(Get-JmoaWorkflowValue $config @('runtimePolicy'))", "-Djmoa.recommendation.confirmationScope=$(Get-JmoaWorkflowValue $config @('scope'))")
    $reducerResult = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments $reducerArgs
    Write-JmoaText -Value $reducerResult.output -Path (Join-Path $destination 'reducer-recommendation-command.log')
    if ($reducerResult.exitCode -ne 0) { throw 'Reducer recommendation failed; later gates were not started.' }
    $runtimeArgs = @('-N', "${PluginCoordinates}:recommend-runtime", '-Djmoa.runtimeRecommendation.enabled=true', "-Djmoa.runtimeRecommendation.inputDir=$runtimeInput", "-Djmoa.runtimeRecommendation.outputDir=$runtimeOut", "-Djmoa.runtimeRecommendation.service=$(Get-JmoaWorkflowValue $config @('service'))", "-Djmoa.runtimeRecommendation.launchMode=$(Get-JmoaWorkflowValue $config @('launchMode'))", "-Djmoa.runtimeRecommendation.runtimePolicy=$(Get-JmoaWorkflowValue $config @('runtimePolicy'))", "-Djmoa.runtimeRecommendation.reducerEngine=$(Get-JmoaWorkflowValue $config @('reducerEngine'))", "-Djmoa.runtimeRecommendation.scope=$(Get-JmoaWorkflowValue $config @('scope'))")
    $registryPath = Get-JmoaWorkflowValue $config @('runtimeRegistryPath')
    if (-not [string]::IsNullOrWhiteSpace($registryPath)) { $runtimeArgs += "-Djmoa.runtimeRecommendation.registry=$registryPath" }
    $runtimeResult = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments $runtimeArgs
    Write-JmoaText -Value $runtimeResult.output -Path (Join-Path $destination 'runtime-recommendation-command.log')
    if ($runtimeResult.exitCode -ne 0) { throw 'Runtime policy recommendation failed; later gates were not started.' }
    $config | Add-Member -Force NoteProperty reducerRecommendationReport (Join-Path $reducerOut 'jmoa-reducer-recommendation.json')
    $config | Add-Member -Force NoteProperty runtimeRecommendationReport (Join-Path $runtimeOut 'jmoa-runtime-recommendation.json')
    $intermediate = New-JmoaWorkflowReport -Config $config -ExecutionMode 'execute'
    if ($intermediate.workflowState -match 'BLOCKED') { if ($FailOnBlocked) { throw "Workflow blocked at $($intermediate.workflowState)." } }
    if ($ExecuteThrough -in @('preflight', 'all')) {
        $reducerGate = @($intermediate.gates | Where-Object name -eq 'reducerRecommendation' | Select-Object -First 1)
        $runtimeGate = @($intermediate.gates | Where-Object name -eq 'runtimePolicyRecommendation' | Select-Object -First 1)
        if ($reducerGate.status -notlike 'RECOMMEND*' -or $runtimeGate.status -notlike 'RECOMMEND*') {
            throw 'Workflow cannot preflight until reducer and runtime-policy recommendations are positive.'
        }
        Invoke-JmoaStepSpec -Step (Get-JmoaStepSpec $config 'preflightStep') -Name 'preflight'
    }
    if ($ExecuteThrough -eq 'all') {
        $requiredStates = [ordered]@{
            materializationStep = 'PREFLIGHT_READY_FOR_SMOKE'
            semanticSmokeStep = 'MATERIALIZATION_PASSED'
            screenStep = 'SEMANTIC_SMOKE_PASSED'
            confirmationStep = 'SCREEN_PASSED'
        }
        foreach ($name in $requiredStates.Keys) {
            $before = New-JmoaWorkflowReport -Config $config -ExecutionMode 'execute'
            if ($before.workflowState -ne $requiredStates[$name]) {
                throw "Workflow cannot execute $name while state is $($before.workflowState); required state is $($requiredStates[$name])."
            }
            Invoke-JmoaStepSpec -Step (Get-JmoaStepSpec $config $name) -Name $name
        }
    }
}

$report = New-JmoaWorkflowReport -Config $config -ExecutionMode $Mode
Write-JmoaJson -Value $report -Path (Join-Path $destination 'jmoa-runtime-workflow-report.json')
Write-JmoaWorkflowMarkdown -Report $report -Path (Join-Path $destination 'jmoa-runtime-workflow-report.md')
if ($FailOnBlocked -and $report.workflowState -match '(BLOCKED|FAILED)') { throw "V2-P workflow ended at $($report.workflowState)." }
Write-Host "V2-P workflow report written to $destination with state $($report.workflowState)."
