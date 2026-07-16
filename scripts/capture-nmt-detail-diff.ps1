param(
    [Parameter(Mandatory)][string]$LaunchScript,
    [Parameter(Mandatory)][string]$StopScript,
    [Parameter(Mandatory)][string]$WorkloadScript,
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [string[]]$LaunchArguments = @(),
    [string[]]$StopArguments = @(),
    [string[]]$WorkloadArguments = @(),
    [string]$ContainerCli = 'podman',
    [string]$JcmdExecutable = 'jcmd',
    [string]$OutputDir = 'target/jmoa-nmt-detail-diff',
    [int]$HealthTimeoutSeconds = 180,
    [int]$ReadySettleSeconds = 20,
    [int]$PostWorkloadSettleSeconds = 20
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
foreach ($path in @($LaunchScript,$StopScript,$WorkloadScript)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required script does not exist: $path" }
}
New-JmoaDirectory -Path $OutputDir
$launched = $false
try {
    & $LaunchScript -RunDirectory $OutputDir -ContainerName $ContainerName -Variant $Variant @LaunchArguments
    if ($LASTEXITCODE -ne 0) { throw "Launch failed with exit code $LASTEXITCODE." }
    $launched = $true
    $health = Wait-JmoaHttpHealth -HealthUrl $HealthUrl -TimeoutSeconds $HealthTimeoutSeconds
    if (-not $health.passed) { throw "Health check failed: $($health.error)" }
    if ($ReadySettleSeconds -gt 0) { Start-Sleep -Seconds $ReadySettleSeconds }
    $pid = Get-JmoaJavaPid -ContainerCli $ContainerCli -ContainerName $ContainerName -JavaProcessPattern 'java'
    if ([string]::IsNullOrWhiteSpace($pid)) { throw 'Could not locate the Java PID.' }
    $jcmd = "env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS $JcmdExecutable"
    $baseline = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command "$jcmd $pid VM.native_memory baseline"
    Write-JmoaText -Value $baseline.output -Path (Join-Path $OutputDir 'nmt-baseline-command.txt')
    if ($baseline.exitCode -ne 0 -or $baseline.output -notmatch '(?i)baseline') { throw 'NMT baseline failed. The JVM must run with NativeMemoryTracking=detail.' }
    $workloadPath = Join-Path $OutputDir 'workload-result.json'
    & $WorkloadScript -OutputPath $workloadPath -BaseUrl $HealthUrl.TrimEnd('/') -ContainerName $ContainerName -Variant $Variant @WorkloadArguments
    if ($LASTEXITCODE -ne 0) { throw "Workload failed with exit code $LASTEXITCODE." }
    if ($PostWorkloadSettleSeconds -gt 0) { Start-Sleep -Seconds $PostWorkloadSettleSeconds }
    foreach ($capture in @(
        @{ name='nmt-detail-diff.txt'; command="$jcmd $pid VM.native_memory detail.diff" },
        @{ name='nmt-detail.txt'; command="$jcmd $pid VM.native_memory detail" },
        @{ name='vm-flags.txt'; command="$jcmd $pid VM.flags" },
        @{ name='heap-info.txt'; command="$jcmd $pid GC.heap_info" },
        @{ name='metaspace.txt'; command="$jcmd $pid VM.metaspace" },
        @{ name='smaps-rollup.txt'; command="cat /proc/$pid/smaps_rollup" },
        @{ name='smaps.txt'; command="cat /proc/$pid/smaps" }
    )) {
        $result = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command $capture.command
        Write-JmoaText -Value $result.output -Path (Join-Path $OutputDir $capture.name)
        if ($result.exitCode -ne 0) { throw "Diagnostic capture failed: $($capture.command)" }
    }
    $manifest = [ordered]@{
        metadataVersion = 'patient-cds-nmt-detail-diagnostic-v1'
        status = 'CAPTURED_DIAGNOSTIC_ONLY'
        variant = $Variant
        readySettleSeconds = $ReadySettleSeconds
        postWorkloadSettleSeconds = $PostWorkloadSettleSeconds
        nmtMode = 'DETAIL'
        claimable = $false
        claimBoundary = 'NMT detail and baseline/diff perturb the process. This capture is diagnostic-only and must not enter confirmation medians.'
    }
    Write-JmoaJson -Value $manifest -Path (Join-Path $OutputDir 'nmt-detail-diagnostic-manifest.json')
} finally {
    if ($launched) { & $StopScript -RunDirectory $OutputDir -ContainerName $ContainerName -Variant $Variant @StopArguments }
}
exit 0
