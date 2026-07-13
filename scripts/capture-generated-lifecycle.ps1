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
    [string]$ScannerVersion = "v2v-generated-lifecycle-capture",

    [string]$LaunchCommand = "",
    [string]$WarmupCommand = "",
    [string]$WorkloadCommand = "",
    [int]$TargetPid = 0,
    [string]$PidFile = "",
    [string]$ImageId = "",
    [string]$ContainerId = "",
    [string]$ClassLoadLogPath = "",
    [string]$ContainerClassLoadLogPath = "",
    [string]$CgroupMemoryCurrentPath = "",
    [string]$JcmdPath = "jcmd",
    [int]$StartupDelaySeconds = 10,
    [int]$PidWaitSeconds = 60,
    [string]$ExpectedArtifactSha256 = "",
    [string]$ArtifactOriginPath = "",
    [switch]$PreflightOnly,
    [switch]$ReuseOutput,
    [switch]$RequireArtifactOriginProof
)

$ErrorActionPreference = "Stop"

function Get-JmoaSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Artifact path does not exist: $Path"
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant()
}

function Write-JmoaPreflight {
    param([Parameter(Mandatory)][string]$Status, [Parameter(Mandatory)][array]$Checks)
    $record = [ordered]@{
        metadataVersion = 'v2v-generated-capture-preflight'
        generatedAt = (Get-Date).ToUniversalTime().ToString('o')
        status = $Status
        service = $Service
        artifactPath = if (Test-Path -LiteralPath $ArtifactPath -PathType Leaf) { (Resolve-Path -LiteralPath $ArtifactPath).Path } else { $ArtifactPath }
        artifactSha256 = if (Test-Path -LiteralPath $ArtifactPath -PathType Leaf) { Get-JmoaSha256 -Path $ArtifactPath } else { '' }
        expectedArtifactSha256 = $ExpectedArtifactSha256
        launchMode = $LaunchMode
        runtimePolicy = $RuntimePolicy
        reducerEngine = $ReducerEngine
        familyRegistryVersion = $FamilyRegistryVersion
        scannerVersion = $ScannerVersion
        outputDir = $OutputDir
        outputReuse = [bool]$ReuseOutput
        checks = $Checks
        diagnosticOnly = $true
        boundaries = @(
            'Preflight does not prove runtime class origin until a PID/container is available.',
            'A READY preflight permits diagnostic capture only; it does not create V2-C memory evidence.'
        )
    }
    $record | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $OutputDir 'generated-capture-preflight.json') -Encoding utf8
    $markdown = @"
# V2-V Generated Capture Preflight

- Status: ``$Status``
- Service: ``$Service``
- Artifact SHA-256: ``$($record.artifactSha256)``
- Launch mode: ``$LaunchMode``
- Runtime policy: ``$RuntimePolicy``
- Reducer engine: ``$ReducerEngine``
- Output reuse: ``$($record.outputReuse)``

| Check | Status | Detail |
| --- | --- | --- |
$(($Checks | ForEach-Object { "| $($_.name) | ``$($_.status)`` | $($_.detail) |" }) -join "`n")

This record gates V2-V diagnostic capture. It is not a performance claim.
"@
    $markdown | Set-Content -LiteralPath (Join-Path $OutputDir 'generated-capture-preflight.md') -Encoding utf8
    return $record
}

function Get-JmoaPreflightChecks {
    $checks = @()
    $artifactExists = Test-Path -LiteralPath $ArtifactPath -PathType Leaf
    $checks += [ordered]@{ name = 'artifact'; status = if ($artifactExists) { 'PASS' } else { 'BLOCK_ARTIFACT_MISSING' }; detail = $ArtifactPath }
    $identityFields = @{
        service = $Service
        launchMode = $LaunchMode
        runtimePolicy = $RuntimePolicy
        reducerEngine = $ReducerEngine
        familyRegistryVersion = $FamilyRegistryVersion
        scannerVersion = $ScannerVersion
    }
    foreach ($field in $identityFields.Keys) {
        $value = [string]$identityFields[$field]
        $checks += [ordered]@{ name = $field; status = if (-not [string]::IsNullOrWhiteSpace($value) -and $value -ne 'UNKNOWN') { 'PASS' } else { 'BLOCK_IDENTITY_INCOMPLETE' }; detail = if ($value) { $value } else { 'missing' } }
    }
    if ($artifactExists -and -not [string]::IsNullOrWhiteSpace($ExpectedArtifactSha256)) {
        $actual = Get-JmoaSha256 -Path $ArtifactPath
        $checks += [ordered]@{ name = 'artifactSha256'; status = if ($actual -eq $ExpectedArtifactSha256.ToUpperInvariant()) { 'PASS' } else { 'BLOCK_ARTIFACT_FINGERPRINT_MISMATCH' }; detail = $actual }
    } else {
        $checks += [ordered]@{ name = 'artifactSha256'; status = if ($artifactExists) { 'PASS_COMPUTED' } else { 'BLOCK_ARTIFACT_MISSING' }; detail = 'computed after artifact check' }
    }
    $outputExists = Test-Path -LiteralPath $OutputDir
    $outputEntries = if ($outputExists) { @(Get-ChildItem -LiteralPath $OutputDir -Force -ErrorAction SilentlyContinue) } else { @() }
    $checks += [ordered]@{ name = 'outputDir'; status = if (-not $outputExists -or $ReuseOutput -or $outputEntries.Count -eq 0) { 'PASS' } else { 'BLOCK_OUTPUT_NOT_CLEAN' }; detail = "$($outputEntries.Count) existing entries" }
    return $checks
}

function Get-JmoaArtifactOriginEvidence {
    param([int]$TargetPid)
    if (-not [string]::IsNullOrWhiteSpace($ContainerId)) {
        return [ordered]@{ status = if (-not [string]::IsNullOrWhiteSpace($ImageId)) { 'CONTAINER_IMAGE_DECLARED' } else { 'CONTAINER_ORIGIN_UNPROVEN' }; detail = "container=$ContainerId image=$ImageId" }
    }
    if ($TargetPid -le 0) {
        return [ordered]@{ status = 'ORIGIN_UNPROVEN'; detail = 'No target PID was available.' }
    }
    try {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$TargetPid" -ErrorAction Stop
        $commandLine = [string]$process.CommandLine
        $expected = @($ArtifactPath, $ArtifactOriginPath, (Split-Path -Leaf $ArtifactPath)) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        $matched = $expected | Where-Object { $commandLine.IndexOf($_, [System.StringComparison]::OrdinalIgnoreCase) -ge 0 }
        return [ordered]@{ status = if ($matched) { 'VERIFIED' } else { 'ORIGIN_UNPROVEN' }; detail = $commandLine }
    } catch {
        return [ordered]@{ status = 'ORIGIN_UNPROVEN'; detail = $_.Exception.Message }
    }
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
    if ($TargetPid -gt 0) {
        return $TargetPid
    }
    if ([string]::IsNullOrWhiteSpace($PidFile)) {
        throw "Provide -TargetPid or -PidFile. The harness needs the target JVM PID for jcmd captures."
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
        if (-not [string]::IsNullOrWhiteSpace($ContainerId)) {
            $arguments = @($TargetPid) + @($Command -split '\s+')
            & podman exec -e JAVA_TOOL_OPTIONS= $ContainerId $JcmdPath @arguments *> $OutputPath
        } else {
            & $JcmdPath $TargetPid $Command *> $OutputPath
        }
        return @{ command = $Command; output = $OutputPath; error = ""; exitCode = $LASTEXITCODE }
    } catch {
        Set-Content -LiteralPath $errorPath -Value $_.Exception.Message -Encoding UTF8
        return @{ command = $Command; output = $OutputPath; error = $errorPath; exitCode = 1 }
    }
}

function Copy-JmoaClassLoadLog {
    param([string]$TargetPath)
    if (-not [string]::IsNullOrWhiteSpace($ContainerId) -and
        -not [string]::IsNullOrWhiteSpace($ContainerClassLoadLogPath)) {
        try {
            & podman exec $ContainerId sh -c "cat '$ContainerClassLoadLogPath'" *> $TargetPath
            return $LASTEXITCODE -eq 0
        } catch {
            Set-Content -LiteralPath $TargetPath -Value "" -Encoding UTF8
            return $false
        }
    }
    if ([string]::IsNullOrWhiteSpace($ClassLoadLogPath) -or -not (Test-Path -LiteralPath $ClassLoadLogPath -PathType Leaf)) {
        Set-Content -LiteralPath $TargetPath -Value "" -Encoding UTF8
        return $false
    }
    Copy-Item -LiteralPath $ClassLoadLogPath -Destination $TargetPath -Force
    return $true
}

function Read-JmoaMemoryCurrent {
    param([string]$TargetPath)
    if (-not [string]::IsNullOrWhiteSpace($ContainerId)) {
        try {
            $fullId = (& podman inspect $ContainerId --format "{{.Id}}" 2>$null | Select-Object -First 1).Trim()
            $path = (& podman machine ssh "find /sys/fs/cgroup -path '*$fullId*' -type f -name memory.current 2>/dev/null | head -n1" 2>$null | Select-Object -First 1).Trim()
            if (-not [string]::IsNullOrWhiteSpace($path)) {
                $value = (& podman machine ssh "cat '$path'" 2>$null | Select-Object -First 1).Trim()
                Set-Content -LiteralPath $TargetPath -Value $value -Encoding ASCII
                return $true
            }
        } catch {
        }
        Set-Content -LiteralPath $TargetPath -Value "" -Encoding UTF8
        return $false
    }
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

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}
$preflightChecks = Get-JmoaPreflightChecks
$preflightBlocked = @($preflightChecks | Where-Object { $_.status -like 'BLOCK_*' }).Count -gt 0
$preflightStatus = if ($preflightBlocked) { 'BLOCKED' } else { 'READY' }
$preflight = Write-JmoaPreflight -Status $preflightStatus -Checks $preflightChecks
if ($PreflightOnly) {
    if ($preflightBlocked) { exit 2 }
    Write-Host "V2-V generated capture preflight passed: $OutputDir"
    exit 0
}
if ($preflightBlocked) {
    throw "V2-V capture preflight blocked. See $(Join-Path $OutputDir 'generated-capture-preflight.md')."
}
$artifactSha = Get-JmoaSha256 -Path $ArtifactPath
$launchRecord = Invoke-JmoaShellCommand -Command $LaunchCommand `
    -StdoutPath (Join-Path $OutputDir "launch.stdout.txt") `
    -StderrPath (Join-Path $OutputDir "launch.stderr.txt")
if (-not [string]::IsNullOrWhiteSpace($LaunchCommand)) {
    Start-Sleep -Seconds $StartupDelaySeconds
}
$targetPid = Wait-JmoaPid
$originEvidence = Get-JmoaArtifactOriginEvidence -TargetPid $targetPid
if ($RequireArtifactOriginProof -and $originEvidence.status -notin @('VERIFIED', 'CONTAINER_IMAGE_DECLARED')) {
    throw "V2-V artifact origin proof failed: $($originEvidence.detail)"
}

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
    metadataVersion = "v2v-generated-lifecycle-manifest"
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    service = $Service
    artifactPath = (Resolve-Path -LiteralPath $ArtifactPath).Path
    artifactSha256 = $artifactSha
    imageId = $ImageId
    containerId = $ContainerId
    pid = $targetPid
    artifactOrigin = $originEvidence
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
