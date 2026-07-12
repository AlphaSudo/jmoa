param(
    [Parameter(Mandatory = $true)]
    [string]$Service,

    [Parameter(Mandatory = $true)]
    [string]$ArtifactPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputDir,

    [string]$LaunchMode = "UNKNOWN",
    [string]$RuntimePolicy = "DIAGNOSTIC",
    [string]$ReducerEngine = "none",
    [string]$FamilyRegistryVersion = "generated-family-registry-v2u",
    [string]$ScannerVersion = "v2u-generated-lifecycle-capture",

    [string]$LaunchCommand = "",
    [string]$WarmupCommand = "",
    [string]$WorkloadCommand = "",
    [int]$Pid = 0,
    [string]$PidFile = "",
    [string]$ImageId = "",
    [string]$ContainerId = "",
    [string]$ClassLoadLogPath = "",
    [string]$CgroupMemoryCurrentPath = "",
    [string]$JcmdPath = "jcmd",
    [int]$StartupDelaySeconds = 10,
    [int]$PidWaitSeconds = 60
)

$ErrorActionPreference = "Stop"

function Get-JmoaSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Artifact path does not exist: $Path"
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant()
}

function Invoke-JmoaShellCommand {
    param(
        [string]$Command,
        [string]$StdoutPath,
        [string]$StderrPath
    )
    if ([string]::IsNullOrWhiteSpace($Command)) {
        return @{ command = ""; exitCode = $null; skipped = $true }
    }
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = "powershell"
    $psi.ArgumentList.Add("-NoProfile")
    $psi.ArgumentList.Add("-ExecutionPolicy")
    $psi.ArgumentList.Add("Bypass")
    $psi.ArgumentList.Add("-Command")
    $psi.ArgumentList.Add($Command)
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    Set-Content -LiteralPath $StdoutPath -Value $stdout -Encoding UTF8
    Set-Content -LiteralPath $StderrPath -Value $stderr -Encoding UTF8
    return @{ command = $Command; exitCode = $process.ExitCode; skipped = $false }
}

function Wait-JmoaPid {
    if ($Pid -gt 0) {
        return $Pid
    }
    if ([string]::IsNullOrWhiteSpace($PidFile)) {
        throw "Provide -Pid or -PidFile. The harness needs the target JVM PID for jcmd captures."
    }
    $deadline = (Get-Date).AddSeconds($PidWaitSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $PidFile -PathType Leaf) {
            $value = (Get-Content -Raw -LiteralPath $PidFile).Trim()
            if ($value -match '^\d+$') {
                return [int]$value
            }
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for JVM PID file: $PidFile"
}

function Invoke-JcmdCapture {
    param(
        [int]$TargetPid,
        [string]$Command,
        [string]$OutputPath
    )
    $errorPath = "$OutputPath.err"
    try {
        & $JcmdPath $TargetPid $Command *> $OutputPath
        return @{ command = $Command; output = $OutputPath; error = ""; exitCode = $LASTEXITCODE }
    } catch {
        Set-Content -LiteralPath $errorPath -Value $_.Exception.Message -Encoding UTF8
        return @{ command = $Command; output = $OutputPath; error = $errorPath; exitCode = 1 }
    }
}

function Copy-JmoaClassLoadLog {
    param([string]$TargetPath)
    if ([string]::IsNullOrWhiteSpace($ClassLoadLogPath) -or -not (Test-Path -LiteralPath $ClassLoadLogPath -PathType Leaf)) {
        Set-Content -LiteralPath $TargetPath -Value "" -Encoding UTF8
        return $false
    }
    Copy-Item -LiteralPath $ClassLoadLogPath -Destination $TargetPath -Force
    return $true
}

function Read-JmoaMemoryCurrent {
    param([string]$TargetPath)
    if ([string]::IsNullOrWhiteSpace($CgroupMemoryCurrentPath) -or -not (Test-Path -LiteralPath $CgroupMemoryCurrentPath -PathType Leaf)) {
        Set-Content -LiteralPath $TargetPath -Value "" -Encoding UTF8
        return $false
    }
    Copy-Item -LiteralPath $CgroupMemoryCurrentPath -Destination $TargetPath -Force
    return $true
}

function Capture-JmoaStage {
    param(
        [int]$TargetPid,
        [string]$Stage,
        [scriptblock]$BeforeCapture
    )
    $stageDir = Join-Path $OutputDir $Stage
    New-Item -ItemType Directory -Force -Path $stageDir | Out-Null
    if ($BeforeCapture) {
        & $BeforeCapture
    }
    $timestamp = (Get-Date).ToUniversalTime().ToString("o")
    Set-Content -LiteralPath (Join-Path $stageDir "timestamp.txt") -Value $timestamp -Encoding UTF8
    $classLoadCopied = Copy-JmoaClassLoadLog -TargetPath (Join-Path $stageDir "class-load.log")
    $memoryCurrentCopied = Read-JmoaMemoryCurrent -TargetPath (Join-Path $stageDir "memory.current")
    $captures = @(
        Invoke-JcmdCapture -TargetPid $TargetPid -Command "GC.class_histogram" -OutputPath (Join-Path $stageDir "class-histogram.txt")
        Invoke-JcmdCapture -TargetPid $TargetPid -Command "VM.native_memory summary" -OutputPath (Join-Path $stageDir "nmt-summary.txt")
        Invoke-JcmdCapture -TargetPid $TargetPid -Command "VM.classloader_stats" -OutputPath (Join-Path $stageDir "classloader-stats.txt")
        Invoke-JcmdCapture -TargetPid $TargetPid -Command "VM.command_line" -OutputPath (Join-Path $stageDir "vm-command-line.txt")
        Invoke-JcmdCapture -TargetPid $TargetPid -Command "VM.flags" -OutputPath (Join-Path $stageDir "vm-flags.txt")
        Invoke-JcmdCapture -TargetPid $TargetPid -Command "VM.metaspace" -OutputPath (Join-Path $stageDir "vm-metaspace.txt")
    )
    return [ordered]@{
        timestamp = $timestamp
        directory = $Stage
        classLoadLog = "$Stage/class-load.log"
        classLoadLogCopied = $classLoadCopied
        classHistogram = "$Stage/class-histogram.txt"
        nmtSummary = "$Stage/nmt-summary.txt"
        classLoaderStats = "$Stage/classloader-stats.txt"
        vmCommandLine = "$Stage/vm-command-line.txt"
        vmFlags = "$Stage/vm-flags.txt"
        vmMetaspace = "$Stage/vm-metaspace.txt"
        memoryCurrent = "$Stage/memory.current"
        memoryCurrentCopied = $memoryCurrentCopied
        runtimeAttribution = "$Stage/generated-class-runtime-attribution.json"
        jcmdCaptures = $captures
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$artifactSha = Get-JmoaSha256 -Path $ArtifactPath
$launchRecord = Invoke-JmoaShellCommand -Command $LaunchCommand `
    -StdoutPath (Join-Path $OutputDir "launch.stdout.txt") `
    -StderrPath (Join-Path $OutputDir "launch.stderr.txt")
if (-not [string]::IsNullOrWhiteSpace($LaunchCommand)) {
    Start-Sleep -Seconds $StartupDelaySeconds
}
$targetPid = Wait-JmoaPid

$startupStage = Capture-JmoaStage -TargetPid $targetPid -Stage "startup" -BeforeCapture $null
$warmupRecord = $null
$warmupStage = Capture-JmoaStage -TargetPid $targetPid -Stage "warmup" -BeforeCapture {
    $script:warmupRecord = Invoke-JmoaShellCommand -Command $WarmupCommand `
        -StdoutPath (Join-Path $OutputDir "warmup.stdout.txt") `
        -StderrPath (Join-Path $OutputDir "warmup.stderr.txt")
}
$workloadRecord = $null
$workloadStage = Capture-JmoaStage -TargetPid $targetPid -Stage "workload" -BeforeCapture {
    $script:workloadRecord = Invoke-JmoaShellCommand -Command $WorkloadCommand `
        -StdoutPath (Join-Path $OutputDir "workload.stdout.txt") `
        -StderrPath (Join-Path $OutputDir "workload.stderr.txt")
}

$manifest = [ordered]@{
    metadataVersion = "v2u-generated-lifecycle-manifest"
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    service = $Service
    artifactPath = (Resolve-Path -LiteralPath $ArtifactPath).Path
    artifactSha256 = $artifactSha
    imageId = $ImageId
    containerId = $ContainerId
    pid = $targetPid
    launchMode = $LaunchMode
    runtimePolicy = $RuntimePolicy
    reducerEngine = $ReducerEngine
    familyRegistryVersion = $FamilyRegistryVersion
    scannerVersion = $ScannerVersion
    diagnosticOnly = $true
    v2cMemoryPair = $false
    launch = $launchRecord
    warmup = $warmupRecord
    workload = $workloadRecord
    stages = [ordered]@{
        startup = $startupStage
        warmup = $warmupStage
        workload = $workloadStage
    }
    boundaries = @(
        "Class-load logging and jcmd diagnostics are perturbing diagnostic evidence.",
        "This lifecycle bundle is not V2-C claimable memory-pair evidence.",
        "Generated-family mutation remains disabled until a separate admission phase."
    )
}

$manifestPath = Join-Path $OutputDir "generated-lifecycle-manifest.json"
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$summary = @"
# Generated Lifecycle Capture

- Service: ``$Service``
- Artifact SHA-256: ``$artifactSha``
- Launch mode: ``$LaunchMode``
- Runtime policy: ``$RuntimePolicy``
- Reducer engine: ``$ReducerEngine``
- Target PID: ``$targetPid``
- Diagnostic-only: ``true``

Stages captured:

```text
startup
warmup
workload
```

This capture must be converted into generated-class runtime-attribution reports
before ``jmoa:analyze-generated-evidence`` can join it with a static inventory.
"@

Set-Content -LiteralPath (Join-Path $OutputDir "generated-lifecycle-manifest.md") -Value $summary -Encoding UTF8
Write-Host "Generated lifecycle manifest written to $manifestPath"
