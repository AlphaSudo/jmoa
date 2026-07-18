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
    [string]$BaselineRuntimePolicy = "",
    [string]$CandidateRuntimePolicy = "",
    [Nullable[bool]]$BaselineCdsEnabled = $null,
    [Nullable[bool]]$CandidateCdsEnabled = $null,
    [string]$BaselineCdsArchivePath = "",
    [string]$CandidateCdsArchivePath = "",
    [string]$BaselineRuntimeArtifactPath = "",
    [string]$CandidateRuntimeArtifactPath = "",
    [string]$BaselineMallocArenaMax = "",
    [string]$CandidateMallocArenaMax = "",
    [string]$MallocArenaMax = "",
    [bool]$CdsEnabled = $false,
    [bool]$AppCdsEnabled = $false,
    [bool]$LeydenEnabled = $false,
    [bool]$JavaagentPresent = $false,
    [string]$WorkloadId = "runtime-screen",
    [int]$WarmupSeconds = 20,
    [int[]]$PostWorkloadSnapshotSeconds = @(0),
    [switch]$CapturePostGcSnapshot,
    [int]$PostGcSettleSeconds = 5,
    [switch]$DeferHistogramUntilFinalSnapshot,
    [switch]$DiagnosticOnly,
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
function Resolve-VariantPolicy {
    param(
        [string]$PolicyOverride,
        [Nullable[bool]]$CdsOverride,
        [string]$ArchivePath,
        [string]$MallocOverride
    )
    $policy = if ([string]::IsNullOrWhiteSpace($PolicyOverride)) { $RuntimePolicy } else { $PolicyOverride }
    $normalized = $policy.Trim().ToUpperInvariant().Replace('-', '_').Replace(' ', '_')
    $cds = if ($null -eq $CdsOverride) { $CdsEnabled } else { [bool]$CdsOverride }
    $malloc = if ([string]::IsNullOrWhiteSpace($MallocOverride)) { $MallocArenaMax } else { $MallocOverride }
    if ($normalized -eq 'NO_CDS_LOW_DIRTY' -and $malloc -ne '1') {
        throw 'NO_CDS_LOW_DIRTY requires MALLOC_ARENA_MAX=1 for the affected variant.'
    }
    if ($normalized.StartsWith('NO_CDS') -and ($cds -or $AppCdsEnabled -or $LeydenEnabled -or $JavaagentPresent)) {
        throw 'A no-CDS variant requires CDS, AppCDS, Leyden, and runtime javaagent to be disabled.'
    }
    $kind = if ($normalized.StartsWith('NO_CDS') -or $normalized -eq 'OFF') { 'OFF' } `
        elseif ($normalized -in @('BASE_CDS','JDK_CDS_BASE','JDK_BASE_CDS_LOW_DIRTY','BASE')) { 'BASE' } `
        elseif ($normalized.Contains('CDS')) { 'APP' } else { 'UNKNOWN' }
    if ($kind -eq 'UNKNOWN') { throw "Unsupported runtime policy: $policy" }
    if ($kind -eq 'BASE' -and -not $cds) { throw 'BASE_CDS requires CDS to be explicitly enabled.' }
    if ($kind -eq 'BASE' -and -not [string]::IsNullOrWhiteSpace($ArchivePath)) { throw 'BASE_CDS must not specify an application archive.' }
    if ($kind -eq 'APP') {
        if (-not $cds) { throw 'A CDS variant requires CDS to be explicitly enabled.' }
        if ([string]::IsNullOrWhiteSpace($ArchivePath) -or -not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) {
            throw "A CDS variant requires an existing variant-specific archive: $ArchivePath"
        }
    }
    [ordered]@{ policy = $policy; normalized = $normalized; kind = $kind; cdsEnabled = $cds; archivePath = $ArchivePath; mallocArenaMax = $malloc }
}

$baselinePolicy = Resolve-VariantPolicy -PolicyOverride $BaselineRuntimePolicy -CdsOverride $BaselineCdsEnabled `
    -ArchivePath $BaselineCdsArchivePath -MallocOverride $BaselineMallocArenaMax
$candidatePolicy = Resolve-VariantPolicy -PolicyOverride $CandidateRuntimePolicy -CdsOverride $CandidateCdsEnabled `
    -ArchivePath $CandidateCdsArchivePath -MallocOverride $CandidateMallocArenaMax

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

function Capture-RuntimeState {
    param(
        [string]$ContainerName,
        [string]$JavaPid,
        [string]$Directory,
        [bool]$IncludeHistogram
    )
    New-JmoaDirectory -Path $Directory
    $cleanJcmd = "env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS $JcmdExecutable"
    $captures = @(
        (Capture-Command -ContainerName $ContainerName -ShellCommand "cat /proc/$JavaPid/smaps_rollup" -OutputPath (Join-Path $Directory 'smaps_rollup.txt')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand "cat /proc/$JavaPid/smaps" -OutputPath (Join-Path $Directory 'smaps.txt')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand 'cat /sys/fs/cgroup/memory.current' -OutputPath (Join-Path $Directory 'memory.current')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand 'cat /sys/fs/cgroup/memory.stat' -OutputPath (Join-Path $Directory 'memory.stat')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand 'cat /sys/fs/cgroup/io.stat' -OutputPath (Join-Path $Directory 'io.stat')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $JavaPid VM.native_memory summary" -OutputPath (Join-Path $Directory 'nmt-summary.txt')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $JavaPid GC.heap_info" -OutputPath (Join-Path $Directory 'heap-info.txt')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $JavaPid VM.metaspace" -OutputPath (Join-Path $Directory 'metaspace.txt')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $JavaPid VM.classloader_stats" -OutputPath (Join-Path $Directory 'classloader-stats.txt')),
        (Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $JavaPid VM.flags" -OutputPath (Join-Path $Directory 'vm-flags.txt'))
    )
    if ($IncludeHistogram) {
        $captures += Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $JavaPid GC.class_histogram" -OutputPath (Join-Path $Directory 'class-histogram.txt')
    }
    if ($captures | Where-Object { $_.exitCode -ne 0 }) { throw 'One or more required runtime captures failed.' }
    [ordered]@{ capturedAt = [DateTime]::UtcNow.ToString('o'); histogramIncluded = $IncludeHistogram; directory = $Directory }
}

function Capture-And-VerifyRuntimePolicy {
    param(
        [string]$ContainerName,
        [string]$JavaPid,
        [string]$Directory,
        [System.Collections.IDictionary]$Policy
    )
    $environment = Capture-Command -ContainerName $ContainerName -ShellCommand "tr '\0' '\n' < /proc/$JavaPid/environ" -OutputPath (Join-Path $Directory 'runtime-environment.txt')
    $commandLine = Capture-Command -ContainerName $ContainerName -ShellCommand "tr '\0' ' ' < /proc/$JavaPid/cmdline" -OutputPath (Join-Path $Directory 'runtime-command-line.txt')
    if ($environment.exitCode -ne 0 -or $commandLine.exitCode -ne 0) {
        throw 'Could not capture the live JVM environment and command line for no-CDS proof.'
    }
    $proofText = "$($environment.output)`n$($commandLine.output)"
    if ($Policy.normalized -eq 'NO_CDS_LOW_DIRTY' -and $proofText -notmatch '(?m)^MALLOC_ARENA_MAX=1\s*$') {
        throw 'NO_CDS_LOW_DIRTY requires live JVM environment proof: MALLOC_ARENA_MAX=1.'
    }
    if ($Policy.kind -eq 'OFF') {
        if ($proofText -notmatch '(?i)-Xshare:off') { throw 'No-CDS policy requires live JVM proof of -Xshare:off.' }
        if ($proofText -match '(?i)(-javaagent:|SharedArchiveFile|ArchiveClassesAtExit|UseAppCDS|AOTCache|AOTCacheOutput|AOTMode|AOTConfiguration)') {
            throw 'No-CDS policy proof found a CDS, Leyden, or javaagent option.'
        }
    } elseif ($Policy.kind -eq 'BASE') {
        if ($proofText -notmatch '(?i)-Xshare:(on|auto)') { throw 'BASE_CDS requires live JVM proof of -Xshare:on or -Xshare:auto.' }
        if ($proofText -match '(?i)-XX:SharedArchiveFile=') { throw 'BASE_CDS must use the default JDK archive without an application SharedArchiveFile override.' }
        if ($proofText -match '(?i)(-javaagent:|ArchiveClassesAtExit|AOTCache|AOTCacheOutput|AOTMode|AOTConfiguration)') { throw 'BASE_CDS found a training, AOT/Leyden, or javaagent option.' }
    } else {
        if ($proofText -notmatch '(?i)-Xshare:(on|auto)') { throw 'CDS policy requires live JVM proof of -Xshare:on or -Xshare:auto.' }
        if ($proofText -notmatch '(?i)-XX:SharedArchiveFile=') { throw 'CDS policy requires live JVM proof of -XX:SharedArchiveFile.' }
        if ($proofText -match '(?i)(-javaagent:|ArchiveClassesAtExit|AOTCache|AOTCacheOutput|AOTMode|AOTConfiguration)') { throw 'APP_CDS found a training, AOT/Leyden, or javaagent option.' }
    }
    $mapsCapture = Capture-Command -ContainerName $ContainerName -ShellCommand "cat /proc/$JavaPid/maps" -OutputPath (Join-Path $Directory 'runtime-cds-maps.txt')
    if ($mapsCapture.exitCode -ne 0) { throw 'Could not capture live JVM mappings for CDS policy proof.' }
    $jsaMappings = @()
    foreach ($line in ($mapsCapture.output -split "`r?`n")) {
        if ($line -match '^([0-9a-f]+)-([0-9a-f]+)\s+(\S+)\s+([0-9a-f]+)\s+(\S+)\s+(\d+)\s+(.+\.jsa)\s*$') {
            $jsaMappings += [ordered]@{ start=$Matches[1]; end=$Matches[2]; permissions=$Matches[3]; offset=$Matches[4]; device=$Matches[5]; inode=$Matches[6]; path=$Matches[7].Trim() }
        }
    }
    $defaultMappings = @($jsaMappings | Where-Object { $_.path -match '(?i)[/\\]lib[/\\]server[/\\]classes[^/\\]*\.jsa$' })
    if ($Policy.kind -eq 'OFF' -and $jsaMappings.Count -ne 0) { throw 'OFF policy unexpectedly mapped one or more CDS archives.' }
    if ($Policy.kind -in @('BASE','APP') -and $defaultMappings.Count -eq 0) { throw "$($Policy.kind) policy did not map the default JDK CDS archive." }
    if ($Policy.kind -eq 'BASE') {
        $unexpectedArchives = @($jsaMappings | Where-Object { $_.path -notmatch '(?i)[/\\]lib[/\\]server[/\\]classes[^/\\]*\.jsa$' })
        if ($unexpectedArchives.Count -ne 0) { throw 'BASE policy mapped an unexpected application or non-default CDS archive.' }
    }
    $defaultArchivePath = $null
    $defaultArchiveSha256 = $null
    $defaultArchiveDeviceInode = $null
    if ($Policy.kind -in @('BASE','APP')) {
        $defaultPaths = @($defaultMappings | ForEach-Object { $_.path } | Sort-Object -Unique)
        if ($defaultPaths.Count -ne 1) { throw 'CDS policy must map exactly one default JDK archive path.' }
        $defaultArchivePath = $defaultPaths[0]
        $defaultHash = Capture-Command -ContainerName $ContainerName -ShellCommand "sha256sum '$defaultArchivePath' | awk '{print `$1}'" -OutputPath (Join-Path $Directory 'default-cds-archive-sha256.txt')
        $defaultIdentity = Capture-Command -ContainerName $ContainerName -ShellCommand "stat -Lc '%d:%i' '$defaultArchivePath'" -OutputPath (Join-Path $Directory 'default-cds-archive-device-inode.txt')
        if ($defaultHash.exitCode -ne 0 -or $defaultIdentity.exitCode -ne 0) { throw 'Could not capture default JDK CDS archive identity.' }
        $defaultArchiveSha256 = $defaultHash.output.Trim().ToUpperInvariant()
        $defaultArchiveDeviceInode = $defaultIdentity.output.Trim()
    }
    $runtimeArchiveSha256 = $null
    if ($Policy.cdsEnabled -and $proofText -match '(?i)-XX:SharedArchiveFile=([^\s]+)') {
        $runtimeArchivePath = $Matches[1].Trim('"', "'")
        $hashCapture = Capture-Command -ContainerName $ContainerName -ShellCommand "sha256sum '$runtimeArchivePath' | awk '{print `$1}'" -OutputPath (Join-Path $Directory 'runtime-cds-archive-sha256.txt')
        if ($hashCapture.exitCode -ne 0) { throw 'Could not hash the live CDS archive inside the container.' }
        $runtimeArchiveSha256 = $hashCapture.output.Trim().ToUpperInvariant()
        if ($runtimeArchiveSha256 -ne (Get-JmoaSha256 -Path $Policy.archivePath)) { throw 'Live CDS archive SHA-256 does not match the host archive supplied to the screen.' }
        if (-not ($jsaMappings | Where-Object { $_.path -eq $runtimeArchivePath })) { throw 'Selected application archive is not present in the live JVM mappings.' }
    }
    [ordered]@{
        status = 'PASSED'
        runtimePolicy = $Policy.policy
        policyArm = $Policy.kind
        cdsMode = if ($Policy.cdsEnabled) { 'ON' } else { 'OFF' }
        cdsArchiveSha256 = if ($Policy.kind -eq 'APP') { Get-JmoaSha256 -Path $Policy.archivePath } else { $null }
        runtimeCdsArchiveSha256 = $runtimeArchiveSha256
        jsaMappings = $jsaMappings
        defaultJdkArchiveMapped = $defaultMappings.Count -gt 0
        defaultJdkArchivePath = $defaultArchivePath
        defaultJdkArchiveSha256 = $defaultArchiveSha256
        defaultJdkArchiveDeviceInode = $defaultArchiveDeviceInode
        applicationArchiveMapped = $Policy.kind -eq 'APP' -and $null -ne $runtimeArchiveSha256
        cdsArchiveBytes = if ($Policy.kind -eq 'APP') { [long](Get-Item -LiteralPath $Policy.archivePath).Length } else { 0 }
        leydenDisabled = $true
        javaagentAbsent = $true
        mallocArenaMax = if ([string]::IsNullOrWhiteSpace($Policy.mallocArenaMax)) { $null } else { $Policy.mallocArenaMax }
        sourceFiles = @('runtime-environment.txt', 'runtime-command-line.txt')
    }
}

function Reset-PageCache {
    if (-not $DropPageCacheBeforeVariant) {
        return [ordered]@{ policy = 'NOT_REQUESTED'; status = 'NOT_REQUESTED'; output = '' }
    }
    $attempts = @()
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @(
            'machine', 'ssh', "sudo sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' && echo DROP_OK"
        )
        $attempts += [ordered]@{
            attempt = $attempt
            exitCode = $result.exitCode
            output = $result.output
        }
        if ($result.exitCode -eq 0 -and $result.output -match 'DROP_OK') {
            $attemptText = ($attempts | ForEach-Object { "attempt=$($_.attempt) exitCode=$($_.exitCode) output=$($_.output)" }) -join [Environment]::NewLine
            return [ordered]@{ policy = 'DROP_CACHES_BEFORE_EACH_VARIANT'; status = 'PASSED'; attempts = $attempt; output = $attemptText }
        }
        if ($attempt -lt 3) { Start-Sleep -Seconds (2 * $attempt) }
    }
    $failureText = ($attempts | ForEach-Object { "attempt=$($_.attempt) exitCode=$($_.exitCode) output=$($_.output)" }) -join '; '
    throw "Could not establish a cold page-cache precondition after 3 attempts: $failureText"
}

function Invoke-Variant {
    param(
        [ValidateSet('BASELINE', 'CANDIDATE')][string]$Variant,
        [string]$Label,
        [string]$LaunchScript,
        [string[]]$LaunchArguments,
        [string]$ContainerName,
        [string]$ArtifactPath,
        [string]$RuntimeVerificationPath,
        [System.Collections.IDictionary]$Policy,
        [string]$RuntimeArtifactPath
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
        $runtimePolicyProof = Capture-And-VerifyRuntimePolicy -ContainerName $ContainerName -JavaPid $javaPid -Directory $runDirectory -Policy $Policy
        $runtimeArtifactSha256 = $null
        if (-not [string]::IsNullOrWhiteSpace($RuntimeArtifactPath)) {
            $artifactHashCapture = Capture-Command -ContainerName $ContainerName -ShellCommand "sha256sum '$RuntimeArtifactPath' | awk '{print `$1}'" -OutputPath (Join-Path $runDirectory 'runtime-artifact-sha256.txt')
            if ($artifactHashCapture.exitCode -ne 0) { throw "Could not hash runtime artifact: $RuntimeArtifactPath" }
            $runtimeArtifactSha256 = $artifactHashCapture.output.Trim().ToUpperInvariant()
            if ($runtimeArtifactSha256 -ne (Get-JmoaSha256 -Path $ArtifactPath)) { throw 'Runtime artifact SHA-256 does not match the host artifact supplied to the screen.' }
        }

        $workloadPath = Join-Path $runDirectory 'workload-result.json'
        & $WorkloadScript -OutputPath $workloadPath -BaseUrl $HealthUrl.TrimEnd('/') -ContainerName $ContainerName -Variant $Variant @WorkloadArguments
        if ($LASTEXITCODE -ne 0) { throw "Workload script returned $LASTEXITCODE." }
        if (-not (Test-Path -LiteralPath $workloadPath -PathType Leaf)) { throw 'Workload script did not emit workload-result.json.' }

        $snapshotOffsets = @($PostWorkloadSnapshotSeconds | Where-Object { $_ -ge 0 } | Sort-Object -Unique)
        if ($snapshotOffsets.Count -eq 0) { $snapshotOffsets = @(0) }
        $snapshots = @(); $snapshotClock = [Diagnostics.Stopwatch]::StartNew()
        for ($snapshotIndex = 0; $snapshotIndex -lt $snapshotOffsets.Count; $snapshotIndex++) {
            $offset = $snapshotOffsets[$snapshotIndex]
            $remaining = $offset - [int][Math]::Floor($snapshotClock.Elapsed.TotalSeconds)
            if ($remaining -gt 0) { Start-Sleep -Seconds $remaining }
            $snapshotDirectory = if ($snapshotIndex -eq 0) { $runDirectory } else {
                Join-Path $runDirectory ("snapshots/t-plus-{0:D3}" -f $offset)
            }
            $includeHistogram = -not $DeferHistogramUntilFinalSnapshot -or $snapshotIndex -eq ($snapshotOffsets.Count - 1)
            $snapshot = Capture-RuntimeState -ContainerName $ContainerName -JavaPid $javaPid -Directory $snapshotDirectory -IncludeHistogram $includeHistogram
            $snapshot.offsetSeconds = $offset
            $snapshot.perturbed = $false
            $snapshots += $snapshot
        }
        $postGc = $null
        if ($CapturePostGcSnapshot) {
            $cleanJcmd = "env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS $JcmdExecutable"
            $gcResult = Capture-Command -ContainerName $ContainerName -ShellCommand "$cleanJcmd $javaPid GC.run" -OutputPath (Join-Path $runDirectory 'post-gc-command.txt')
            if ($gcResult.exitCode -ne 0) { throw 'Diagnostic GC.run failed.' }
            if ($PostGcSettleSeconds -gt 0) { Start-Sleep -Seconds $PostGcSettleSeconds }
            $postGc = Capture-RuntimeState -ContainerName $ContainerName -JavaPid $javaPid -Directory (Join-Path $runDirectory 'post-gc') -IncludeHistogram $true
            $postGc.perturbed = $true
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
            runtimeArtifactSha256 = $runtimeArtifactSha256
            imageId = Get-JmoaContainerImageId -ContainerCli $ContainerCli -ContainerName $ContainerName
            containerId = Get-JmoaContainerId -ContainerCli $ContainerCli -ContainerName $ContainerName
            pid = [int]$javaPid
            launchMode = $LaunchMode
            runtimePolicy = $Policy.policy
            cdsMode = if ($Policy.cdsEnabled) { 'ON' } else { 'OFF' }
            cdsArchiveSha256 = if ($Policy.kind -eq 'APP') { Get-JmoaSha256 -Path $Policy.archivePath } else { $null }
            appCds = $AppCdsEnabled
            leyden = $LeydenEnabled
            javaagentPresent = $JavaagentPresent
            mallocArenaMax = if ([string]::IsNullOrWhiteSpace($Policy.mallocArenaMax)) { $null } else { $Policy.mallocArenaMax }
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
            diagnosticOnly = [bool]$DiagnosticOnly
            postWorkloadSnapshots = $snapshots
            postGcSnapshot = $postGc
            runtimePolicyProof = $runtimePolicyProof
            noCdsStateProof = if ($Policy.kind -eq 'OFF') { $runtimePolicyProof } else { $null }
        }
        Write-JmoaJson -Value $manifest -Path (Join-Path $runDirectory 'run-manifest.json')
        $containerLog = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('logs', $ContainerName)
        Write-JmoaText -Value $containerLog.output -Path (Join-Path $runDirectory 'runtime-container.log')
        if ($containerLog.exitCode -ne 0) { throw 'Could not capture runtime container logs.' }
        $linkagePatterns = '(?i)(VerifyError|ClassFormatError|NoSuchMethodError|NoClassDefFoundError|ExceptionInInitializerError|LinkageError)'
        $linkageMatches = @($containerLog.output -split "`r?`n" | Where-Object { $_ -match $linkagePatterns })
        if ($linkageMatches.Count -ne 0) { throw "Runtime semantic/linkage error detected: $($linkageMatches[0])" }
        return [ordered]@{
            variant = $Variant
            runDirectory = $runDirectory
            status = 'CAPTURED'
            workload = (Get-Content -Raw -LiteralPath $workloadPath | ConvertFrom-Json)
            runtimePolicyProof = $runtimePolicyProof
            semanticLinkageErrors = 0
        }
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
        -RuntimeVerificationPath $BaselineRuntimeVerificationPath -Policy $baselinePolicy -RuntimeArtifactPath $BaselineRuntimeArtifactPath
    $candidate = Invoke-Variant -Variant 'CANDIDATE' -Label 'c' -LaunchScript $CandidateLaunchScript `
        -LaunchArguments $CandidateLaunchArguments -ContainerName $CandidateContainerName -ArtifactPath $CandidateArtifactPath `
        -RuntimeVerificationPath $CandidateRuntimeVerificationPath -Policy $candidatePolicy -RuntimeArtifactPath $CandidateRuntimeArtifactPath
} else {
    $candidate = Invoke-Variant -Variant 'CANDIDATE' -Label 'c' -LaunchScript $CandidateLaunchScript `
        -LaunchArguments $CandidateLaunchArguments -ContainerName $CandidateContainerName -ArtifactPath $CandidateArtifactPath `
        -RuntimeVerificationPath $CandidateRuntimeVerificationPath -Policy $candidatePolicy -RuntimeArtifactPath $CandidateRuntimeArtifactPath
    $baseline = Invoke-Variant -Variant 'BASELINE' -Label 'b' -LaunchScript $BaselineLaunchScript `
        -LaunchArguments $BaselineLaunchArguments -ContainerName $BaselineContainerName -ArtifactPath $BaselineArtifactPath `
        -RuntimeVerificationPath $BaselineRuntimeVerificationPath -Policy $baselinePolicy -RuntimeArtifactPath $BaselineRuntimeArtifactPath
}
$status = if ($baseline.status -eq 'CAPTURED' -and $candidate.status -eq 'CAPTURED') { 'CAPTURED' } else { 'FAILED' }
$baseArchiveIdentity = $null
if ($status -eq 'CAPTURED' -and $baselinePolicy.kind -eq 'BASE' -and $candidatePolicy.kind -eq 'BASE') {
    $sameHash = $baseline.runtimePolicyProof.defaultJdkArchiveSha256 -eq $candidate.runtimePolicyProof.defaultJdkArchiveSha256
    $samePath = $baseline.runtimePolicyProof.defaultJdkArchivePath -eq $candidate.runtimePolicyProof.defaultJdkArchivePath
    $sameDeviceInode = $baseline.runtimePolicyProof.defaultJdkArchiveDeviceInode -eq $candidate.runtimePolicyProof.defaultJdkArchiveDeviceInode
    $baseArchiveIdentity = [ordered]@{
        status = if ($sameHash -and $samePath -and $sameDeviceInode) { 'PASSED' } else { 'FAILED' }
        sameSha256 = $sameHash
        samePath = $samePath
        sameDeviceInode = $sameDeviceInode
        sha256 = $baseline.runtimePolicyProof.defaultJdkArchiveSha256
        path = $baseline.runtimePolicyProof.defaultJdkArchivePath
        deviceInode = $baseline.runtimePolicyProof.defaultJdkArchiveDeviceInode
    }
    if ($baseArchiveIdentity.status -ne 'PASSED') { $status = 'FAILED' }
}
$pair = [ordered]@{
    metadataVersion = 'v2o-runtime-screen-pair'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    pairIndex = $PairIndex
    status = $status
    service = $Service
    launchMode = $LaunchMode
    runtimePolicy = if ($baselinePolicy.policy -eq $candidatePolicy.policy) { $RuntimePolicy } else { 'MIXED_POLICY_DIAGNOSTIC' }
    baselineRuntimePolicy = $baselinePolicy.policy
    candidateRuntimePolicy = $candidatePolicy.policy
    firstVariant = $FirstVariant
    pageCachePolicy = if ($DropPageCacheBeforeVariant) { 'DROP_CACHES_BEFORE_EACH_VARIANT' } else { 'NOT_REQUESTED' }
    baseline = $baseline
    candidate = $candidate
    baseArchiveIdentity = $baseArchiveIdentity
    claimBoundary = 'One paired screen only. Run V2-C and V2-D after three valid pairs before making a runtime claim.'
    diagnosticOnly = [bool]$DiagnosticOnly
}
Write-JmoaJson -Value $pair -Path (Join-Path $CaptureRoot ("v2o-runtime-screen-pair-{0}.json" -f $PairIndex))
Write-Host "Runtime screen pair $PairIndex status: $status"
if ($FailOnFailure -and $status -ne 'CAPTURED') { exit 1 }
exit 0
