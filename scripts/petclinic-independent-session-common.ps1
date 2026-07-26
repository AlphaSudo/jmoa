Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

function Write-PetclinicScenarioCommandLedger {
    param(
        [Parameter(Mandatory)][string]$ScenarioId,
        [Parameter(Mandatory)][string]$LedgerRoot,
        [Parameter(Mandatory)][string]$OutputDirectory
    )
    $records = [Collections.Generic.List[object]]::new()
    foreach ($ndjson in @(Get-ChildItem -LiteralPath $LedgerRoot -Filter commands.ndjson -Recurse -File)) {
        foreach ($line in @(Get-Content -LiteralPath $ndjson.FullName | Where-Object { $_ -match '\S' })) {
            $record = $line | ConvertFrom-Json
            $record | Add-Member -NotePropertyName sourceLedgerDirectory -NotePropertyValue $ndjson.Directory.FullName -Force
            $records.Add($record)
        }
    }
    $ordered = @($records | Sort-Object { [datetime]$_.startedUtc }, sequence)
    $builder = [Text.StringBuilder]::new()
    [void]$builder.AppendLine("# $ScenarioId Command Ledger")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('Every external command and HTTP response executed for this scenario is preserved below.')
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
        $kindProperty = $record.PSObject.Properties['kind']
        $kind = if ($null -eq $kindProperty) { 'PROCESS' } else { [string]$kindProperty.Value }
        $commandText = if ($kind -eq 'HTTP') { "$($record.method) $($record.uri)" } else { [string]$record.commandLine }
        $resultText = if ($kind -eq 'HTTP') { "HTTP $($record.status); error=$($record.error)" } else { "exitCode=$($record.exitCode)" }
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("## $($record.startedUtc) - $($record.step)")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("- Command: ``$commandText``")
        [void]$builder.AppendLine("- Result: $resultText")
        [void]$builder.AppendLine("- Source ledger: ``$($record.sourceLedgerDirectory)``")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine('stdout:')
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
        schemaVersion = 'jmoa-independent-session-command-ledger-v1'
        scenarioId = $ScenarioId
        commandCount = $ordered.Count
        markdownPath = 'scenario-command-ledger.md'
        markdownSha256 = (Get-JmoaSha256 $path).ToUpperInvariant()
    }
    Write-JmoaJson $summary (Join-Path $OutputDirectory 'scenario-command-ledger.json')
    $summary
}

function Invoke-PetclinicSupportDiagnosticCapture {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$ConfigContainer,
        [Parameter(Mandatory)][string]$DiscoveryContainer,
        [Parameter(Mandatory)][string]$Point,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [Parameter(Mandatory)][string]$LedgerDirectory
    )
    New-JmoaDirectory $OutputDirectory
    if (-not (Test-Path -LiteralPath $LedgerDirectory)) {
        Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage 'support-diagnostic' -Variant SUPPORT -Description "Support/host diagnostics at $Point." | Out-Null
    }
    $hostCommand = @'
printf '%s\n' '---MEMINFO---'; cat /proc/meminfo
printf '%s\n' '---MEMORY_PRESSURE---'; cat /proc/pressure/memory
printf '%s\n' '---PODMAN_PS---'; podman ps --no-trunc
printf '%s\n' '---PODMAN_MOUNTS---'; findmnt -rn -t overlay,fuse.overlayfs 2>/dev/null || true
'@.Replace("`r", '')
    $hostCapture = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('machine', 'ssh', $hostCommand) -LedgerDirectory $LedgerDirectory -Step "$Point host memory, pressure, and storage state"
    Write-JmoaText $hostCapture.stdout (Join-Path $OutputDirectory 'host-state.txt')
    foreach ($entry in @(
        @{ role = 'config'; name = $ConfigContainer },
        @{ role = 'discovery'; name = $DiscoveryContainer }
    )) {
        $command = "pid=`$(ps -eo pid,args | grep -F java | grep -v grep | awk '{print `$1; exit}'); echo PID=`$pid; cat /proc/`$pid/smaps_rollup; echo ---MEMORY_STAT---; cat /sys/fs/cgroup/memory.stat"
        $capture = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('exec', $entry.name, 'sh', '-lc', $command) -LedgerDirectory $LedgerDirectory -Step "$Point $($entry.role) process and cgroup memory"
        Write-JmoaText $capture.stdout (Join-Path $OutputDirectory "$($entry.role)-memory.txt")
    }
}

function Add-PetclinicSessionManifestFields {
    param(
        [Parameter(Mandatory)][string]$RunDirectory,
        [Parameter(Mandatory)][string]$SupportSessionId,
        [Parameter(Mandatory)][string]$TargetSessionId,
        [Parameter(Mandatory)][string]$LogicalVariant,
        [Parameter(Mandatory)][int]$ExecutionOrdinal,
        [Parameter(Mandatory)][string]$HostFingerprint
    )
    $path = Join-Path $RunDirectory 'run-manifest.json'
    $manifest = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    foreach ($entry in ([ordered]@{
        supportSessionId = $SupportSessionId
        targetSessionId = $TargetSessionId
        logicalVariant = $LogicalVariant
        executionOrdinal = $ExecutionOrdinal
        independentSupportSession = $true
        hostFingerprint = $HostFingerprint
    }).GetEnumerator()) {
        $manifest | Add-Member -NotePropertyName $entry.Key -NotePropertyValue $entry.Value -Force
    }
    Write-JmoaJson $manifest $path
}

function Invoke-PetclinicIndependentSession {
    param(
        [Parameter(Mandatory)]$Context,
        [Parameter(Mandatory)][ValidateSet('B0', 'V2')][string]$Variant,
        [Parameter(Mandatory)][string]$SessionId,
        [Parameter(Mandatory)][int]$ExecutionOrdinal,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [int]$SupportSettleSeconds = 180,
        [int]$WarmupSeconds = 20,
        [int]$SettleSeconds = 5,
        [int]$HealthTimeoutSeconds = 900
    )
    $supportLaunch = Join-Path $PSScriptRoot 'campaign-launch-petclinic-support.ps1'
    $supportStop = Join-Path $PSScriptRoot 'campaign-stop-petclinic-support.ps1'
    $targetLaunch = Join-Path $PSScriptRoot 'campaign-launch-petclinic-target.ps1'
    $targetStop = Join-Path $PSScriptRoot 'campaign-stop-petclinic-target.ps1'
    $screenScript = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
    $workloadScript = Join-Path $PSScriptRoot 'campaign-workload-petclinic.ps1'
    New-JmoaDirectory $OutputDirectory
    $supportDirectory = Join-Path $OutputDirectory 'support'
    $captureRoot = Join-Path $OutputDirectory 'capture'
    $ledgerRoot = Join-Path $OutputDirectory 'child-ledgers'
    $supportLaunchLedger = Join-Path $ledgerRoot 'support-launch'
    $supportStopLedger = Join-Path $ledgerRoot 'support-stop'
    $supportDiagnosticLedger = Join-Path $ledgerRoot 'support-diagnostics'
    $targetLedger = Join-Path $ledgerRoot 'target'
    $safe = $SessionId -replace '[^A-Za-z0-9_.-]', '-'
    $network = "jmoa-$safe-net"
    $configName = "jmoa-$safe-cfg"
    $discoveryName = "jmoa-$safe-disc"
    $image = if ($Variant -eq 'B0') { $Context.b0Image } else { $Context.v2Image }
    $artifact = if ($Variant -eq 'B0') { $Context.b0Artifact } else { $Context.v2Artifact }
    $executionMode = if ($Variant -eq 'B0') { 'BASELINE_ONLY' } else { 'CANDIDATE_ONLY' }
    $label = if ($Variant -eq 'B0') { 'b1' } else { 'c1' }
    $screenSucceeded = $false
    & $supportLaunch -OutputDirectory $supportDirectory -PairId $SessionId -ConfigImage $Context.configImage -DiscoveryImage $Context.discoveryImage -ConfigRepo $Context.frozenConfig -SettleSeconds $SupportSettleSeconds -MinAvailableMemoryBytes 734003200 -ContainerCli $Context.containerCli -LedgerDirectory $supportLaunchLedger
    if (-not $?) { throw "Support admission failed for independent session $SessionId." }
    try {
        Invoke-PetclinicSupportDiagnosticCapture -ContainerCli $Context.containerCli -ConfigContainer $configName -DiscoveryContainer $discoveryName -Point PRE_TARGET -OutputDirectory (Join-Path $supportDirectory 'pre-target') -LedgerDirectory $supportDiagnosticLedger
        $common = @{
            SupportNetwork = $network
            CustomerReadyTimeoutSeconds = $HealthTimeoutSeconds
            MinAvailableMemoryBeforeTargetBytes = 314572800
            ContainerCli = $Context.containerCli
        }
        $arguments = @{
            BaselineLaunchScript = $targetLaunch
            CandidateLaunchScript = $targetLaunch
            BaselineContainerName = "$safe-b"
            CandidateContainerName = "$safe-c"
            WorkloadScript = $workloadScript
            HealthUrl = 'http://localhost:8081/actuator/health'
            Service = 'customers-service'
            LaunchMode = 'EXPLODED_BOOT_APP_INDEPENDENT_SUPPORT'
            RuntimePolicy = 'NO_CDS_LOW_DIRTY'
            BaselineArtifactPath = $artifact
            CandidateArtifactPath = $artifact
            BaselineLaunchParameters = (@{} + $common + @{ Image = $image })
            CandidateLaunchParameters = (@{} + $common + @{ Image = $image })
            StopScript = $targetStop
            ContainerCli = $Context.containerCli
            PairIndex = 1
            FirstVariant = 'BASELINE_FIRST'
            ExecutionMode = $executionMode
            CaptureRoot = $captureRoot
            LedgerDirectory = $targetLedger
            MallocArenaMax = '1'
            WarmupSeconds = $WarmupSeconds
            PostWorkloadSnapshotSeconds = @($SettleSeconds)
            HealthTimeoutSeconds = $HealthTimeoutSeconds
            FailOnFailure = $true
            CapturePodmanMachinePressure = $true
            MinPodmanAvailableMemoryBytes = 314572800
            MinPostArmAvailableMemoryBytes = 314572800
            MaxPodmanSwapUsedBytes = 0
            RequireSwapDisabled = $true
            RequireZeroOomEvents = $true
            MaxPodmanMemoryPressureSomeAvg10 = 999.0
            MaxPodmanMemoryPressureFullAvg10 = 0.0
            DropPageCacheBeforeVariant = $true
            WorkloadId = 'petclinic-corrected-27x3'
        }
        & $screenScript @arguments
        if (-not $?) { throw "Target screen failed for independent session $SessionId." }
        $runDirectory = Join-Path $captureRoot $label
        foreach ($required in @(
            'workload-result.json', 'environment-validity.json', 'run-manifest.json',
            'smaps_rollup.txt', 'smaps.txt', 'memory.current', 'memory.stat',
            'nmt-summary.txt', 'heap-info.txt', 'class-histogram.txt'
        )) {
            if (-not (Test-Path -LiteralPath (Join-Path $runDirectory $required) -PathType Leaf)) {
                throw "Independent session $SessionId is missing required output: $required"
            }
        }
        $workload = Get-Content -Raw -LiteralPath (Join-Path $runDirectory 'workload-result.json') | ConvertFrom-Json
        $environment = Get-Content -Raw -LiteralPath (Join-Path $runDirectory 'environment-validity.json') | ConvertFrom-Json
        if ([int]$workload.errors -ne 0 -or [int]$workload.requests -ne 81 -or -not [bool]$workload.mutationsProven -or -not [bool]$environment.passed) {
            throw "Independent session $SessionId failed workload or environment validity."
        }
        Add-PetclinicSessionManifestFields -RunDirectory $runDirectory -SupportSessionId $SessionId -TargetSessionId "$SessionId-target" -LogicalVariant $Variant -ExecutionOrdinal $ExecutionOrdinal -HostFingerprint $Context.hostFingerprint
        Invoke-PetclinicSupportDiagnosticCapture -ContainerCli $Context.containerCli -ConfigContainer $configName -DiscoveryContainer $discoveryName -Point POST_TARGET -OutputDirectory (Join-Path $supportDirectory 'post-target') -LedgerDirectory $supportDiagnosticLedger
        Complete-CampaignAuditLedger -LedgerDirectory $supportDiagnosticLedger -Status COMPLETE -Stage 'support-diagnostic' -Variant SUPPORT | Out-Null
        $screenSucceeded = $true
    } finally {
        & $supportStop -OutputDirectory $supportDirectory -PairId $SessionId -ContainerCli $Context.containerCli -LedgerDirectory $supportStopLedger
        Write-PetclinicScenarioCommandLedger -ScenarioId $SessionId -LedgerRoot $ledgerRoot -OutputDirectory $OutputDirectory | Out-Null
    }
    if (-not $screenSucceeded) { throw "Independent session $SessionId did not complete." }
    $runDirectory = Join-Path $captureRoot $label
    $memory = Read-CampaignRunMemory $runDirectory
    $manifestPath = Join-Path $runDirectory 'run-manifest.json'
    $result = [ordered]@{
        schemaVersion = 'jmoa-petclinic-independent-session-result-v1'
        sessionId = $SessionId
        supportSessionId = $SessionId
        targetSessionId = "$SessionId-target"
        executionOrdinal = $ExecutionOrdinal
        variant = $Variant
        independentSupportSession = $true
        runDirectory = $runDirectory
        runManifestSha256 = (Get-JmoaSha256 $manifestPath).ToUpperInvariant()
        artifactSha256 = (Get-JmoaSha256 $artifact).ToUpperInvariant()
        pssKb = [long]$memory.pssKb
        privateDirtyKb = [long]$memory.privateDirtyKb
        memoryCurrentBytes = [long]$memory.memoryCurrentBytes
        workloadErrors = 0
        valid = $true
    }
    Write-JmoaJson $result (Join-Path $OutputDirectory 'session-result.json')
    $result
}
