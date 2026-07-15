param(
    [Parameter(Mandatory)][string]$BaselineLaunchScript,
    [Parameter(Mandatory)][string]$CandidateLaunchScript,
    [Parameter(Mandatory)][string]$BaselineContainerName,
    [Parameter(Mandatory)][string]$CandidateContainerName,
    [Parameter(Mandatory)][string]$WorkloadScript,
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string]$LaunchMode,
    [Parameter(Mandatory)][string]$RuntimePolicy,
    [Parameter(Mandatory)][string]$BaselineArtifactPath,
    [Parameter(Mandatory)][string]$CandidateArtifactPath,
    [string[]]$BaselineLaunchArguments = @(),
    [string[]]$CandidateLaunchArguments = @(),
    [string[]]$WorkloadArguments = @(),
    [string]$StopScript = "",
    [string[]]$StopScriptArguments = @(),
    [string]$ContainerCli = "podman",
    [string]$JcmdExecutable = "jcmd",
    [string]$JavaProcessPattern = "java",
    [int]$PairIndex = 1,
    [ValidateSet('BASELINE_FIRST', 'CANDIDATE_FIRST')][string]$FirstVariant = 'BASELINE_FIRST',
    [string]$CaptureRoot = "target/jmoa-runtime-screen",
    [string]$BaselineRuntimeVerificationPath = "",
    [string]$CandidateRuntimeVerificationPath = "",
    [string]$MallocArenaMax = "",
    [bool]$CdsEnabled = $false,
    [bool]$AppCdsEnabled = $false,
    [bool]$LeydenEnabled = $false,
    [bool]$JavaagentPresent = $false,
    [string]$WorkloadId = "runtime-screen",
    [int]$WarmupSeconds = 20,
    [switch]$DropPageCacheBeforeVariant,
    [int]$HealthTimeoutSeconds = 90,
    [switch]$FailOnFailure
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

foreach ($path in @($BaselineLaunchScript, $CandidateLaunchScript, $WorkloadScript, $BaselineArtifactPath, $CandidateArtifactPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required path does not exist: $path" }
}
if (-not [string]::IsNullOrWhiteSpace($StopScript) -and -not (Test-Path -LiteralPath $StopScript -PathType Leaf)) {
    throw "Stop script does not exist: $StopScript"
}
New-JmoaDirectory -Path $CaptureRoot
$normalizedPolicy = $RuntimePolicy.Trim().ToUpperInvariant().Replace('-', '_').Replace(' ', '_')
if ($normalizedPolicy -eq 'NO_CDS_LOW_DIRTY' -and $MallocArenaMax -ne '1') {
    throw 'NO_CDS_LOW_DIRTY requires -MallocArenaMax 1 for every clean memory pair.'
}
if ($normalizedPolicy.StartsWith('NO_CDS') -and ($CdsEnabled -or $AppCdsEnabled -or $LeydenEnabled -or $JavaagentPresent)) {
    throw 'A no-CDS screen requires CDS, AppCDS, Leyden, and runtime javaagent to be explicitly disabled.'
}
if (-not $normalizedPolicy.StartsWith('NO_CDS') -and $normalizedPolicy.Contains('CDS') -and -not $CdsEnabled) {
    throw 'A CDS screen requires -CdsEnabled:$true and variant-specific materialization proof.'
}

function Stop-VariantContainer {
    param([string]$ContainerName, [string]$Variant, [string]$RunDirectory)
    if (-not [string]::IsNullOrWhiteSpace($StopScript)) {
        & $StopScript -RunDirectory $RunDirectory -ContainerName $ContainerName -Variant $Variant @StopScriptArguments
        if ($LASTEXITCODE -ne 0) { throw "Stop script failed for $Variant ($ContainerName)." }
        return
    }
    $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('rm', '-f', $ContainerName)
    if ($result.exitCode -ne 0 -and $result.output -notmatch 'no container') {
        throw "Could not remove $Variant container ${ContainerName}: $($result.output)"
    }
}

function Capture-Command {
    param([string]$ContainerName, [string]$ShellCommand, [string]$OutputPath)
    $result = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command $ShellCommand
    Write-JmoaText -Value $result.output -Path $OutputPath
    return $result
}

function Reset-PageCache {
    if (-not $DropPageCacheBeforeVariant) {
        return [ordered]@{ policy = 'NOT_REQUESTED'; status = 'NOT_REQUESTED'; output = '' }
    }
    $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @(
        'machine', 'ssh', "sudo sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' && echo DROP_OK"
    )
    if ($result.exitCode -ne 0 -or $result.output -notmatch 'DROP_OK') {
        throw "Could not establish a cold page-cache precondition: $($result.output)"
    }
    return [ordered]@{ policy = 'DROP_CACHES_BEFORE_EACH_VARIANT'; status = 'PASSED'; output = $result.output }
}

function Invoke-Variant {
    param(
        [ValidateSet('BASELINE', 'CANDIDATE')][string]$Variant,
        [string]$Label,
        [string]$LaunchScript,
        [string[]]$LaunchArguments,
        [string]$ContainerName,
        [string]$ArtifactPath,
        [string]$RuntimeVerificationPath
    )
    $runDirectory = Join-Path $CaptureRoot ("{0}{1}" -f $Label, $PairIndex)
    New-JmoaDirectory -Path $runDirectory
    $started = [DateTime]::UtcNow
    $launchError = ''
    try {
        $pageCache = Reset-PageCache
        Write-JmoaText -Value $pageCache.output -Path (Join-Path $runDirectory 'page-cache-reset.txt')
        & $LaunchScript -RunDirectory $runDirectory -ContainerName $ContainerName -Variant $Variant @LaunchArguments
        if ($LASTEXITCODE -ne 0) { throw "Launch script returned $LASTEXITCODE." }
        $health = Wait-JmoaHttpHealth -HealthUrl $HealthUrl -TimeoutSeconds $HealthTimeoutSeconds
        if (-not $health.passed) { throw "Health check did not pass: $($health.error)" }
        $healthyAt = [DateTime]::UtcNow
        if ($WarmupSeconds -gt 0) { Start-Sleep -Seconds $WarmupSeconds }
        $javaPid = Get-JmoaJavaPid -ContainerCli $ContainerCli -ContainerName $ContainerName -JavaProcessPattern $JavaProcessPattern
        if ([string]::IsNullOrWhiteSpace($javaPid)) { throw 'Could not locate the Java PID in the running container.' }

        $workloadPath = Join-Path $runDirectory 'workload-result.json'
        & $WorkloadScript -OutputPath $workloadPath -BaseUrl $HealthUrl.TrimEnd('/') -ContainerName $ContainerName -Variant $Variant @WorkloadArguments
        if ($LASTEXITCODE -ne 0) { throw "Workload script returned $LASTEXITCODE." }
        if (-not (Test-Path -LiteralPath $workloadPath -PathType Leaf)) { throw 'Workload script did not emit workload-result.json.' }

        # Do not let the application JVM's JAVA_TOOL_OPTIONS leak into diagnostic jcmd subprocesses.
        $cleanJcmd = "env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS $JcmdExecutable"
        $captureResults = @(
            (Capture-Command -ContainerName $ContainerName -ShellCommand "cat /proc/$javaPid/smaps_rollup" -OutputPath (Join-Path $runDirectory 'smaps_rollup.txt')),
            (Capture-Command -ContainerName $ContainerName -ShellCommand "cat /proc/$javaPid/smaps" -OutputPath (Join-Path $runDirectory 'smaps.txt')),
            (Capture-Command -ContainerName $ContainerName -ShellCommand 'cat /sys/fs/cgroup/memory.current' -OutputPath (Join-Path $runDirectory 'memory.current')),
            (Capture-Command -ContainerName $ContainerName -ShellCommand 'cat /sys/fs/cgroup/memory.stat' -OutputPath (Join-Path $runDirectory 'memory.stat')),
            (Capture-Command -ContainerName $ContainerName -ShellCommand 'cat /sys/fs/cgroup/io.stat' -OutputPath (Join-Path $runDirectory 'io.stat')),
            (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $javaPid VM.native_memory summary" -OutputPath (Join-Path $runDirectory 'nmt-summary.txt')),
            (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $javaPid GC.heap_info" -OutputPath (Join-Path $runDirectory 'heap-info.txt')),
            (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $javaPid GC.class_histogram" -OutputPath (Join-Path $runDirectory 'class-histogram.txt'))
        )
        if ($captureResults | Where-Object { $_.exitCode -ne 0 }) {
            throw 'One or more required runtime captures failed.'
        }
        if (-not [string]::IsNullOrWhiteSpace($RuntimeVerificationPath)) {
            if (-not (Test-Path -LiteralPath $RuntimeVerificationPath -PathType Leaf)) {
                throw "Runtime verification report does not exist: $RuntimeVerificationPath"
            }
            Copy-Item -LiteralPath $RuntimeVerificationPath -Destination (Join-Path $runDirectory 'runtime-verification.json') -Force
        }
        $ended = [DateTime]::UtcNow
        $manifest = [ordered]@{
            runId = ("{0}{1}" -f $Label, $PairIndex)
            pairIndex = $PairIndex
            variant = $Variant
            service = $Service
            phase = 'V2-O'
            artifactSha256 = Get-JmoaSha256 -Path $ArtifactPath
            expectedArtifactSha256 = Get-JmoaSha256 -Path $ArtifactPath
            imageId = Get-JmoaContainerImageId -ContainerCli $ContainerCli -ContainerName $ContainerName
            containerId = Get-JmoaContainerId -ContainerCli $ContainerCli -ContainerName $ContainerName
            pid = [int]$javaPid
            launchMode = $LaunchMode
            runtimePolicy = $RuntimePolicy
            cdsMode = if ($CdsEnabled) { 'ON' } else { 'OFF' }
            appCds = $AppCdsEnabled
            leyden = $LeydenEnabled
            javaagentPresent = $JavaagentPresent
            mallocArenaMax = if ([string]::IsNullOrWhiteSpace($MallocArenaMax)) { $null } else { $MallocArenaMax }
            javaVersion = (Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command 'java -version 2>&1 | head -n 1').output
            workloadId = $WorkloadId
            timestampStart = $started.ToString('o')
            timestampPost = $ended.ToString('o')
            startupMillis = [int][Math]::Round(($healthyAt - $started).TotalMilliseconds)
            warmupSeconds = $WarmupSeconds
            firstVariant = $FirstVariant
            pageCachePolicy = $pageCache.policy
            classLoadLoggingEnabled = $false
            jfrEnabled = $false
            nmtMode = 'SUMMARY'
            gcRunBeforeCapture = $false
        }
        Write-JmoaJson -Value $manifest -Path (Join-Path $runDirectory 'run-manifest.json')
        return [ordered]@{ variant = $Variant; runDirectory = $runDirectory; status = 'CAPTURED'; workload = (Get-Content -Raw -LiteralPath $workloadPath | ConvertFrom-Json) }
    } catch {
        $launchError = $_.Exception.Message
        $failure = [ordered]@{ health = 'DOWN'; errors = 1; requests = 0; status = 'FAILED'; error = $launchError }
        Write-JmoaJson -Value $failure -Path (Join-Path $runDirectory 'workload-result.json')
        return [ordered]@{ variant = $Variant; runDirectory = $runDirectory; status = 'FAILED'; error = $launchError }
    } finally {
        Stop-VariantContainer -ContainerName $ContainerName -Variant $Variant -RunDirectory $runDirectory
    }
}

$baseline = $null
$candidate = $null
if ($FirstVariant -eq 'BASELINE_FIRST') {
    $baseline = Invoke-Variant -Variant 'BASELINE' -Label 'b' -LaunchScript $BaselineLaunchScript `
        -LaunchArguments $BaselineLaunchArguments -ContainerName $BaselineContainerName -ArtifactPath $BaselineArtifactPath `
        -RuntimeVerificationPath $BaselineRuntimeVerificationPath
    $candidate = Invoke-Variant -Variant 'CANDIDATE' -Label 'c' -LaunchScript $CandidateLaunchScript `
        -LaunchArguments $CandidateLaunchArguments -ContainerName $CandidateContainerName -ArtifactPath $CandidateArtifactPath `
        -RuntimeVerificationPath $CandidateRuntimeVerificationPath
} else {
    $candidate = Invoke-Variant -Variant 'CANDIDATE' -Label 'c' -LaunchScript $CandidateLaunchScript `
        -LaunchArguments $CandidateLaunchArguments -ContainerName $CandidateContainerName -ArtifactPath $CandidateArtifactPath `
        -RuntimeVerificationPath $CandidateRuntimeVerificationPath
    $baseline = Invoke-Variant -Variant 'BASELINE' -Label 'b' -LaunchScript $BaselineLaunchScript `
        -LaunchArguments $BaselineLaunchArguments -ContainerName $BaselineContainerName -ArtifactPath $BaselineArtifactPath `
        -RuntimeVerificationPath $BaselineRuntimeVerificationPath
}
$status = if ($baseline.status -eq 'CAPTURED' -and $candidate.status -eq 'CAPTURED') { 'CAPTURED' } else { 'FAILED' }
$pair = [ordered]@{
    metadataVersion = 'v2o-runtime-screen-pair'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    pairIndex = $PairIndex
    status = $status
    service = $Service
    launchMode = $LaunchMode
    runtimePolicy = $RuntimePolicy
    firstVariant = $FirstVariant
    pageCachePolicy = if ($DropPageCacheBeforeVariant) { 'DROP_CACHES_BEFORE_EACH_VARIANT' } else { 'NOT_REQUESTED' }
    baseline = $baseline
    candidate = $candidate
    claimBoundary = 'One paired screen only. Run V2-C and V2-D after three valid pairs before making a runtime claim.'
}
Write-JmoaJson -Value $pair -Path (Join-Path $CaptureRoot ("v2o-runtime-screen-pair-{0}.json" -f $PairIndex))
Write-Host "Runtime screen pair $PairIndex status: $status"
if ($FailOnFailure -and $status -ne 'CAPTURED') { exit 1 }
exit 0
