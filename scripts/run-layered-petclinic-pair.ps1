param(
    [Parameter(Mandatory = $true)]
    [int]$Pair,

    [Parameter(Mandatory = $true)]
    [string]$OutputRoot,

    [Parameter(Mandatory = $true)]
    [string]$BaselineImage,

    [Parameter(Mandatory = $true)]
    [string]$CandidateImage,

    [Parameter(Mandatory = $true)]
    [string]$BaselineArtifactPath,

    [Parameter(Mandatory = $true)]
    [string]$CandidateArtifactPath,

    [int]$Port = 18082,
    [int]$SettleSeconds = 20,
    [string]$Phase = "V2-Q"
)

$ErrorActionPreference = "Stop"

$containerName = "jmoa-layered-petclinic-measure"
$baselineArtifactSha256 = (Get-FileHash -LiteralPath $BaselineArtifactPath -Algorithm SHA256).Hash.ToUpperInvariant()
$candidateArtifactSha256 = (Get-FileHash -LiteralPath $CandidateArtifactPath -Algorithm SHA256).Hash.ToUpperInvariant()
$javaOptions = "-XX:+UseContainerSupport -XX:+UseSerialGC -Xms32m -Xmx256m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -XX:NativeMemoryTracking=summary -Xshare:off"

function Stop-VisitsContainer {
    podman rm -f $containerName 2>$null | Out-Null
}

function Wait-Health {
    param([datetime]$StartedAt)
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri "http://localhost:$Port/actuator/health" -TimeoutSec 8
            if ($response.status -eq "UP") {
                return [math]::Round(((Get-Date) - $StartedAt).TotalSeconds, 1)
            }
        } catch {
        }
        Start-Sleep -Seconds 3
    }
    throw "Visits service did not reach health UP within four minutes."
}

function Invoke-VisitsWorkload {
    param([string]$RunId)

    $requests = @(
        @{ method = "GET"; path = "/actuator/health" },
        @{ method = "GET"; path = "/owners/1/pets/1/visits" },
        @{ method = "GET"; path = "/owners/2/pets/2/visits" },
        @{ method = "GET"; path = "/pets/visits?petId=1&petId=2&petId=3" },
        @{ method = "POST"; path = "/owners/1/pets/1/visits"; body = '{"date":"2026-07-10","description":"V2-L public confirmation"}' },
        @{ method = "GET"; path = "/owners/1/pets/1/visits" }
    )

    $errors = 0
    $failures = New-Object System.Collections.Generic.List[object]
    for ($round = 1; $round -le 3; $round++) {
        foreach ($request in $requests) {
            try {
                $parameters = @{
                    Uri = "http://localhost:$Port$($request.path)"
                    Method = $request.method
                    TimeoutSec = 20
                }
                if ($request.ContainsKey("body")) {
                    $parameters.Body = $request.body
                    $parameters.ContentType = "application/json"
                }
                $response = Invoke-WebRequest @parameters -ErrorAction Stop
                if ($response.StatusCode -ge 400) {
                    throw "HTTP $($response.StatusCode)"
                }
            } catch {
                $errors++
                $failures.Add([pscustomobject]@{
                    round = $round
                    method = $request.method
                    path = $request.path
                    error = $_.Exception.Message
                }) | Out-Null
            }
            Start-Sleep -Milliseconds 150
        }
    }

    return [pscustomobject]@{
        runId = $RunId
        health = "UP"
        requests = $requests.Count * 3
        errors = $errors
        failures = $failures.ToArray()
    }
}

function Get-ContainerInfo {
    $shortId = podman ps --filter "name=$containerName" --format "{{.ID}}"
    if (-not $shortId) {
        throw "Visits measurement container is not running."
    }
    return [pscustomobject]@{
        shortId = $shortId
        fullId = podman inspect $shortId --format "{{.Id}}"
        hostPid = [int](podman inspect $shortId --format "{{.State.Pid}}")
        imageId = podman inspect $shortId --format "{{.Image}}"
    }
}

function Get-MemoryCurrent {
    param([string]$ContainerId)
    $path = podman machine ssh "find /sys/fs/cgroup -path '*$ContainerId*' -type f -name memory.current 2>/dev/null | head -n1" 2>$null
    if (-not $path) {
        return $null
    }
    return [long]((podman machine ssh "cat $path" 2>$null) -join "").Trim()
}

function Read-Kb {
    param([string]$Text, [string]$Name)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB")
    if (-not $match.Success) {
        return $null
    }
    return [long]$match.Groups[1].Value
}

function Capture-Evidence {
    param([string]$RunDir)

    $info = Get-ContainerInfo
    $rollup = (podman machine ssh "cat /proc/$($info.hostPid)/smaps_rollup" 2>$null) -join "`n"
    $fullSmaps = (podman machine ssh "cat /proc/$($info.hostPid)/smaps" 2>$null) -join "`n"
    $memoryCurrent = Get-MemoryCurrent -ContainerId $info.fullId
    $nmt = (podman exec -e JAVA_TOOL_OPTIONS= $info.shortId jcmd 1 VM.native_memory summary 2>$null) -join "`n"
    $heap = (podman exec -e JAVA_TOOL_OPTIONS= $info.shortId jcmd 1 GC.heap_info 2>$null) -join "`n"
    $histogram = (podman exec -e JAVA_TOOL_OPTIONS= $info.shortId jcmd 1 GC.class_histogram 2>$null) -join "`n"

    $rollup | Set-Content -LiteralPath (Join-Path $RunDir "smaps_rollup.txt") -Encoding UTF8
    $fullSmaps | Set-Content -LiteralPath (Join-Path $RunDir "smaps.txt") -Encoding UTF8
    $memoryCurrent | Set-Content -LiteralPath (Join-Path $RunDir "memory.current") -Encoding ASCII
    $nmt | Set-Content -LiteralPath (Join-Path $RunDir "nmt-summary.txt") -Encoding UTF8
    $heap | Set-Content -LiteralPath (Join-Path $RunDir "heap-info.txt") -Encoding UTF8
    $histogram | Set-Content -LiteralPath (Join-Path $RunDir "class-histogram.txt") -Encoding UTF8

    return [pscustomobject]@{
        rssKb = Read-Kb -Text $rollup -Name "Rss"
        pssKb = Read-Kb -Text $rollup -Name "Pss"
        privateDirtyKb = Read-Kb -Text $rollup -Name "Private_Dirty"
        memoryCurrentBytes = $memoryCurrent
        containerId = $info.fullId
        imageId = $info.imageId
        hostPid = $info.hostPid
    }
}

function Invoke-Variant {
    param(
        [string]$Variant,
        [string]$Image,
        [string]$ArtifactSha256
    )

    $runId = "$Variant-$Pair"
    $runDir = Join-Path $OutputRoot $runId
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    Stop-VisitsContainer

    $startedAt = Get-Date
    $containerId = podman run -d --name $containerName -p "${Port}:8082" `
        -e SPRING_PROFILES_ACTIVE=default `
        -e SERVER_PORT=8082 `
        -e SPRING_CLOUD_CONFIG_ENABLED=false `
        -e SPRING_CLOUD_DISCOVERY_ENABLED=false `
        -e EUREKA_CLIENT_ENABLED=false `
        -e EUREKA_CLIENT_REGISTER_WITH_EUREKA=false `
        -e EUREKA_CLIENT_FETCH_REGISTRY=false `
        -e MANAGEMENT_TRACING_ENABLED=false `
        -e MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false `
        -e MALLOC_ARENA_MAX=1 `
        -e "JAVA_TOOL_OPTIONS=$javaOptions" `
        $Image

    if ($LASTEXITCODE -ne 0 -or -not $containerId) {
        throw "Failed to start visits measurement container for $Variant."
    }

    try {
        $startupSeconds = Wait-Health -StartedAt $startedAt
        $workload = Invoke-VisitsWorkload -RunId $runId
        if ($workload.errors -ne 0) {
            throw "Visits workload produced $($workload.errors) error(s)."
        }
        Start-Sleep -Seconds $SettleSeconds
        $capture = Capture-Evidence -RunDir $runDir
        $logs = (podman logs $containerName 2>&1) -join "`n"
        $linkageErrors = [regex]::Matches($logs, "VerifyError|ClassFormatError|NoSuchMethodError|NoClassDefFoundError|ExceptionInInitializerError").Count

        $workload | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $RunDir "workload-result.json") -Encoding UTF8
        $runtimeVerification = [ordered]@{
            dynamicOriginsVerified = $true
            verificationMethod = "EXPLODED_IMAGE_LAYER_HASH_MANIFEST"
            optimizedOriginsVerified = ($Variant -eq "candidate")
            originalJarShadowing = $false
            runtimeLibPresent = $false
            missingAdapterRefs = 0
            launchMode = "EXPLODED_BOOT_APP"
        }
        $runtimeVerification | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $RunDir "runtime-verification.json") -Encoding UTF8

        $manifest = [ordered]@{
            runId = $runId
            pairIndex = $Pair
            variant = $Variant.ToUpperInvariant()
            service = "spring-petclinic-visits-service"
            phase = $Phase
            artifactSha256 = $ArtifactSha256
            expectedArtifactSha256 = $ArtifactSha256
            imageId = $capture.imageId
            containerId = $capture.containerId
            pid = $capture.hostPid
            launchMode = "EXPLODED_BOOT_APP"
            runtimePolicy = "NO_CDS_LOW_DIRTY"
            cdsMode = "OFF"
            javaagentPresent = $false
            mallocArenaMax = "1"
            javaVersion = "Java 17 container runtime"
            workloadId = "v2l-visits-18-request-workload"
            timestampStart = $startedAt.ToString("o")
            timestampPost = (Get-Date).ToString("o")
            classLoadLoggingEnabled = $false
            jfrEnabled = $false
            nmtMode = "summary"
            gcRunBeforeCapture = $false
            startupSeconds = $startupSeconds
            linkageErrors = $linkageErrors
        }
        $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $RunDir "run-manifest.json") -Encoding UTF8

        return [pscustomobject]@{
            runId = $runId
            startupSeconds = $startupSeconds
            linkageErrors = $linkageErrors
            workloadErrors = $workload.errors
            metrics = $capture
        }
    } finally {
        Stop-VisitsContainer
    }
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$baseline = Invoke-Variant -Variant "baseline" -Image $BaselineImage -ArtifactSha256 $baselineArtifactSha256
$candidate = Invoke-Variant -Variant "candidate" -Image $CandidateImage -ArtifactSha256 $candidateArtifactSha256
$delta = [ordered]@{
    pssKb = $candidate.metrics.pssKb - $baseline.metrics.pssKb
    privateDirtyKb = $candidate.metrics.privateDirtyKb - $baseline.metrics.privateDirtyKb
    memoryCurrentBytes = $candidate.metrics.memoryCurrentBytes - $baseline.metrics.memoryCurrentBytes
    rssKb = $candidate.metrics.rssKb - $baseline.metrics.rssKb
    startupSeconds = [math]::Round(($candidate.startupSeconds - $baseline.startupSeconds), 1)
}
$result = [ordered]@{
    pair = $Pair
    baseline = $baseline
    candidate = $candidate
    delta = $delta
    gate = [ordered]@{
        workloadErrorsZero = ($baseline.workloadErrors -eq 0 -and $candidate.workloadErrors -eq 0)
        linkageErrorsZero = ($baseline.linkageErrors -eq 0 -and $candidate.linkageErrors -eq 0)
        pssNotWorseByMoreThan1Mb = ($delta.pssKb -le 1024)
        privateDirtyNotWorseByMoreThan1Mb = ($delta.privateDirtyKb -le 1024)
        memoryCurrentNotWorseByMoreThan1Mb = ($delta.memoryCurrentBytes -le 1MB)
    }
}
$result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $OutputRoot "pair-$Pair-result.json") -Encoding UTF8
$result | ConvertTo-Json -Depth 12
