Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

function Write-ThreeArtifactScenarioLedger {
    param(
        [Parameter(Mandatory)][string]$ScenarioId,
        [Parameter(Mandatory)][string]$LedgerRoot,
        [Parameter(Mandatory)][string]$OutputDirectory
    )
    $records = [Collections.Generic.List[object]]::new()
    foreach ($ndjson in @(Get-ChildItem -LiteralPath $LedgerRoot -Filter commands.ndjson -Recurse -File -ErrorAction SilentlyContinue)) {
        foreach ($line in @(Get-Content -LiteralPath $ndjson.FullName | Where-Object { $_ -match '\S' })) {
            $record = $line | ConvertFrom-Json
            $record | Add-Member sourceLedgerDirectory $ndjson.Directory.FullName -Force
            $records.Add($record)
        }
    }
    $ordered = @($records | Sort-Object { [datetime]$_.startedUtc }, sequence)
    $builder = [Text.StringBuilder]::new()
    [void]$builder.AppendLine("# $ScenarioId Command And Response Ledger")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('This is the chronological transcript for one independent runtime observation.')
    [void]$builder.AppendLine('Commands hidden inside a launcher are prohibited; child scripts must use the audited execution API.')
    foreach ($record in $ordered) {
        $stdoutProperty = $record.PSObject.Properties['rawStdoutPath']
        $stderrProperty = $record.PSObject.Properties['rawStderrPath']
        $bodyProperty = $record.PSObject.Properties['rawBodyPath']
        $stdout = if ($null -ne $stdoutProperty -and $stdoutProperty.Value) {
            [string](Get-Content -Raw -LiteralPath (Join-Path $record.sourceLedgerDirectory ([string]$stdoutProperty.Value)))
        } elseif ($null -ne $bodyProperty -and $bodyProperty.Value) {
            [string](Get-Content -Raw -LiteralPath (Join-Path $record.sourceLedgerDirectory ([string]$bodyProperty.Value)))
        } else { '' }
        $stderr = if ($null -ne $stderrProperty -and $stderrProperty.Value) {
            [string](Get-Content -Raw -LiteralPath (Join-Path $record.sourceLedgerDirectory ([string]$stderrProperty.Value)))
        } else { '' }
        $kind = if ($null -eq $record.PSObject.Properties['kind']) { 'PROCESS' } else { [string]$record.kind }
        $command = if ($kind -eq 'HTTP') { "$($record.method) $($record.uri)" } else { [string]$record.commandLine }
        $result = if ($kind -eq 'HTTP') { "HTTP $($record.status); error=$($record.error)" } else { "exitCode=$($record.exitCode)" }
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("## $($record.startedUtc) - $($record.step)")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("- Command: ``$command``")
        [void]$builder.AppendLine("- Result: $result")
        [void]$builder.AppendLine("- Source ledger: ``$($record.sourceLedgerDirectory)``")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine('stdout / HTTP body:')
        [void]$builder.AppendLine('```text')
        [void]$builder.AppendLine(([string]$stdout).TrimEnd())
        [void]$builder.AppendLine('```')
        [void]$builder.AppendLine('stderr:')
        [void]$builder.AppendLine('```text')
        [void]$builder.AppendLine(([string]$stderr).TrimEnd())
        [void]$builder.AppendLine('```')
    }
    New-JmoaDirectory $OutputDirectory
    $path = Join-Path $OutputDirectory 'scenario-command-ledger.md'
    Write-JmoaText $builder.ToString() $path
    $summary = [ordered]@{
        schemaVersion = 'jmoa-three-artifact-command-ledger-v1'
        scenarioId = $ScenarioId
        commandCount = $ordered.Count
        markdownPath = 'scenario-command-ledger.md'
        markdownSha256 = (Get-JmoaSha256 $path).ToUpperInvariant()
    }
    Write-JmoaJson $summary (Join-Path $OutputDirectory 'scenario-command-ledger.json')
    $summary
}

function Get-ThreeArtifactHeapPssKb {
    param([string]$SmapsPath)
    $sum = 0L
    $inHeap = $false
    foreach ($line in Get-Content -LiteralPath $SmapsPath) {
        if ($line -match '^[0-9a-fA-F]+-[0-9a-fA-F]+\s') {
            $inHeap = $line -match '\[heap\]'
        } elseif ($inHeap -and $line -match '^Pss:\s+(\d+)\s+kB') {
            $sum += [long]$Matches[1]
        }
    }
    $sum
}

function Get-ThreeArtifactMetaspace {
    param([string]$Path)
    $text = Get-Content -Raw -LiteralPath $Path
    if ($text -notmatch 'Metaspace\s+used\s+(\d+)K,\s+committed\s+(\d+)K') {
        return [ordered]@{ usedKb = 0L; committedKb = 0L; loadedClasses = 0L }
    }
    $used = [long]$Matches[1]
    $committed = [long]$Matches[2]
    $classes = if ($text -match 'Total Usage - \d+ loaders,\s+(\d+) classes') { [long]$Matches[1] } else { 0L }
    [ordered]@{ usedKb = $used; committedKb = $committed; loadedClasses = $classes }
}

function Add-ThreeArtifactManifestFields {
    param(
        [Parameter(Mandatory)][string]$RunDirectory,
        [Parameter(Mandatory)][string]$Protocol,
        [Parameter(Mandatory)][string]$SessionId,
        [Parameter(Mandatory)][string]$LogicalVariant,
        [Parameter(Mandatory)][int]$ExecutionOrdinal,
        [int]$Block = 0,
        [int]$Position = 0
    )
    $path = Join-Path $RunDirectory 'run-manifest.json'
    $manifest = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    foreach ($entry in ([ordered]@{
        campaignProtocol = $Protocol
        independentSessionId = $SessionId
        supportSessionId = $SessionId
        targetSessionId = "$SessionId-target"
        logicalVariant = $LogicalVariant
        executionOrdinal = $ExecutionOrdinal
        block = $Block
        blockPosition = $Position
        independentSupportSession = $true
    }).GetEnumerator()) {
        $manifest | Add-Member -NotePropertyName $entry.Key -NotePropertyValue $entry.Value -Force
    }
    Write-JmoaJson $manifest $path
}

function Invoke-ThreeArtifactIndependentSession {
    param(
        [Parameter(Mandatory)]$Config,
        [Parameter(Mandatory)]$VariantConfig,
        [Parameter(Mandatory)][ValidateSet('B0', 'V1', 'V2')][string]$Variant,
        [Parameter(Mandatory)][string]$SessionId,
        [Parameter(Mandatory)][int]$ExecutionOrdinal,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [int]$Block = 0,
        [int]$Position = 0
    )
    $screenScript = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
    New-JmoaDirectory $OutputDirectory
    $captureRoot = Join-Path $OutputDirectory 'capture'
    $ledgerRoot = Join-Path $OutputDirectory 'child-ledgers'
    $targetLedger = Join-Path $ledgerRoot 'runtime'
    $safe = $SessionId -replace '[^A-Za-z0-9_.-]', '-'
    $runDirectory = Join-Path $captureRoot 'b1'
    $screenSucceeded = $false
    $launchParameters = @{}
    foreach ($property in @($Config.launchParameters.PSObject.Properties)) {
        $launchParameters[$property.Name] = $property.Value
    }
    foreach ($property in @($VariantConfig.launchParameters.PSObject.Properties)) {
        $launchParameters[$property.Name] = $property.Value
    }
    $launchParameters.ArtifactVariant = $Variant
    $launchParameters.ArtifactPath = [string]$VariantConfig.artifactPath
    $launchParameters.Image = [string]$VariantConfig.image
    $launchParameters.CdsArchivePath = [string]$VariantConfig.cdsArchivePath
    $launchParameters.ProjectName = $safe

    $workloadParameters = @{}
    foreach ($property in @($Config.workloadParameters.PSObject.Properties)) {
        $workloadParameters[$property.Name] = $property.Value
    }
    $stopParameters = @{ ProjectName = $safe }
    if ($Config.PSObject.Properties['stopParameters']) {
        foreach ($property in @($Config.stopParameters.PSObject.Properties)) {
            $stopParameters[$property.Name] = $property.Value
        }
    }
    try {
        $arguments = @{
            BaselineLaunchScript = [string]$Config.launchScript
            CandidateLaunchScript = [string]$Config.launchScript
            BaselineContainerName = "$safe-target"
            CandidateContainerName = "$safe-unused"
            WorkloadScript = [string]$Config.workloadScript
            HealthUrl = [string]$Config.healthUrl
            Service = [string]$Config.service
            LaunchMode = [string]$Config.launchMode
            RuntimePolicy = [string]$Config.runtimePolicy
            BaselineArtifactPath = [string]$VariantConfig.artifactPath
            CandidateArtifactPath = [string]$VariantConfig.artifactPath
            BaselineLaunchParameters = $launchParameters
            CandidateLaunchParameters = $launchParameters
            WorkloadParameters = $workloadParameters
            StopScript = [string]$Config.stopScript
            StopScriptParameters = $stopParameters
            ContainerCli = [string]$Config.containerCli
            PairIndex = 1
            FirstVariant = 'BASELINE_FIRST'
            ExecutionMode = 'BASELINE_ONLY'
            CaptureRoot = $captureRoot
            LedgerDirectory = $targetLedger
            BaselineRuntimePolicy = [string]$Config.runtimePolicy
            CandidateRuntimePolicy = [string]$Config.runtimePolicy
            BaselineCdsEnabled = [bool]$Config.cdsEnabled
            CandidateCdsEnabled = [bool]$Config.cdsEnabled
            BaselineCdsArchivePath = [string]$VariantConfig.cdsArchivePath
            CandidateCdsArchivePath = [string]$VariantConfig.cdsArchivePath
            BaselineRuntimeArtifactPath = [string]$Config.runtimeArtifactPath
            CandidateRuntimeArtifactPath = [string]$Config.runtimeArtifactPath
            CdsEnabled = [bool]$Config.cdsEnabled
            AppCdsEnabled = [bool]$Config.appCdsEnabled
            LeydenEnabled = $false
            JavaagentPresent = $false
            MallocArenaMax = [string]$Config.mallocArenaMax
            WorkloadId = [string]$Config.workloadId
            WarmupSeconds = [int]$Config.warmupSeconds
            PostWorkloadSnapshotSeconds = @([int]$Config.settleSeconds)
            HealthTimeoutSeconds = [int]$Config.healthTimeoutSeconds
            FailOnFailure = $true
            DropPageCacheBeforeVariant = [bool]$Config.dropPageCache
            PageCacheResetStrategy = if ($Config.PSObject.Properties['pageCacheResetStrategy']) { [string]$Config.pageCacheResetStrategy } else { 'DROP_CACHES' }
            PodmanMachineName = if ($Config.PSObject.Properties['podmanMachineName']) { [string]$Config.podmanMachineName } else { 'podman-machine-default' }
            CapturePodmanMachinePressure = [bool]$Config.captureHostPressure
            MinPodmanAvailableMemoryBytes = [long]$Config.minAvailableMemoryBytes
            MinPostArmAvailableMemoryBytes = [long]$Config.minAvailableMemoryBytes
            MaxPodmanSwapUsedBytes = 0
            RequireSwapDisabled = $false
            RequireZeroOomEvents = $true
            MaxPodmanMemoryPressureSomeAvg10 = [double]$Config.maxMemoryPressureSomeAvg10
            MaxPodmanMemoryPressureFullAvg10 = [double]$Config.maxMemoryPressureFullAvg10
        }
        & $screenScript @arguments
        if (-not $?) { throw "Runtime capture failed for $SessionId." }
        foreach ($required in @(
            'workload-result.json', 'environment-validity.json', 'run-manifest.json',
            'smaps_rollup.txt', 'smaps.txt', 'memory.current', 'memory.stat',
            'nmt-summary.txt', 'heap-info.txt', 'class-histogram.txt',
            'metaspace.txt', 'classloader-stats.txt'
        )) {
            if (-not (Test-Path -LiteralPath (Join-Path $runDirectory $required) -PathType Leaf)) {
                throw "Session $SessionId is missing required output: $required"
            }
        }
        $workload = Get-Content -Raw -LiteralPath (Join-Path $runDirectory 'workload-result.json') | ConvertFrom-Json
        $environment = Get-Content -Raw -LiteralPath (Join-Path $runDirectory 'environment-validity.json') | ConvertFrom-Json
        if ([int]$workload.errors -ne 0 -or [int]$workload.requests -ne [int]$Config.expectedRequests -or -not [bool]$environment.passed) {
            throw "Session $SessionId failed workload or environment validity."
        }
        $manifest = Get-Content -Raw -LiteralPath (Join-Path $runDirectory 'run-manifest.json') | ConvertFrom-Json
        $workloadCompletedAt = if ($workload.PSObject.Properties['completedAt']) {
            [DateTimeOffset]::Parse([string]$workload.completedAt)
        } elseif ($workload.PSObject.Properties['generatedAt']) {
            [DateTimeOffset]::Parse([string]$workload.generatedAt)
        } else {
            $null
        }
        if ($null -eq $workloadCompletedAt) {
            throw "Session $SessionId capture timing is invalid: workload completion timestamp is missing."
        }
        if (@($manifest.postWorkloadSnapshots).Count -eq 0 -or
            [string]::IsNullOrWhiteSpace([string]$manifest.postWorkloadSnapshots[0].capturedAt)) {
            throw "Session $SessionId capture timing is invalid: first post-workload snapshot timestamp is missing."
        }
        $snapshotAt = [DateTimeOffset]::Parse([string]$manifest.postWorkloadSnapshots[0].capturedAt)
        $actualCaptureLagSeconds = ($snapshotAt - $workloadCompletedAt).TotalSeconds
        $maximumCaptureLagSeconds = if ($Config.PSObject.Properties['maxPostWorkloadCaptureLagSeconds']) {
            [double]$Config.maxPostWorkloadCaptureLagSeconds
        } else {
            [double]$Config.settleSeconds + 60.0
        }
        if ($actualCaptureLagSeconds -lt 0 -or $actualCaptureLagSeconds -gt $maximumCaptureLagSeconds) {
            throw "Session $SessionId capture timing is invalid: workload-to-snapshot lag $([math]::Round($actualCaptureLagSeconds, 3)) seconds; allowed range 0..$maximumCaptureLagSeconds seconds."
        }
        Add-ThreeArtifactManifestFields -RunDirectory $runDirectory -Protocol ([string]$Config.protocol) -SessionId $SessionId -LogicalVariant $Variant -ExecutionOrdinal $ExecutionOrdinal -Block $Block -Position $Position
        $screenSucceeded = $true
    } finally {
        if (Test-Path -LiteralPath $ledgerRoot -PathType Container) {
            Write-ThreeArtifactScenarioLedger -ScenarioId $SessionId -LedgerRoot $ledgerRoot -OutputDirectory $OutputDirectory | Out-Null
        }
    }
    if (-not $screenSucceeded) { throw "Session $SessionId did not complete." }

    $memory = Read-CampaignRunMemory $runDirectory
    $manifest = Get-Content -Raw -LiteralPath (Join-Path $runDirectory 'run-manifest.json') | ConvertFrom-Json
    $metaspace = Get-ThreeArtifactMetaspace (Join-Path $runDirectory 'metaspace.txt')
    $result = [ordered]@{
        schemaVersion = 'jmoa-three-artifact-session-result-v1'
        protocol = [string]$Config.protocol
        service = [string]$Config.service
        sessionId = $SessionId
        executionOrdinal = $ExecutionOrdinal
        block = $Block
        position = $Position
        variant = $Variant
        independentSupportSession = $true
        supportSessionId = $SessionId
        targetSessionId = "$SessionId-target"
        runDirectory = $runDirectory
        artifactSha256 = Get-CampaignArtifactSha256 -Path ([string]$VariantConfig.artifactPath)
        image = [string]$VariantConfig.image
        imageId = [string]$manifest.imageId
        pssKb = [long]$memory.pssKb
        privateDirtyKb = [long]$memory.privateDirtyKb
        memoryCurrentBytes = [long]$memory.memoryCurrentBytes
        heapPssKb = Get-ThreeArtifactHeapPssKb (Join-Path $runDirectory 'smaps.txt')
        loadedClasses = [long]$metaspace.loadedClasses
        metaspaceUsedKb = [long]$metaspace.usedKb
        metaspaceCommittedKb = [long]$metaspace.committedKb
        startupMillis = [long]$manifest.startupMillis
        workloadErrors = 0
        semanticErrors = 0
        valid = $true
    }
    Write-JmoaJson $result (Join-Path $OutputDirectory 'session-result.json')
    $result
}
