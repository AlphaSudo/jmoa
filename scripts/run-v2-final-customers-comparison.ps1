param(
    [Parameter(Mandatory)][string]$ComparisonId,
    [Parameter(Mandatory)][string]$PetclinicRoot,
    [Parameter(Mandatory)][string]$OutputDir,
    [Parameter(Mandatory)][string]$BaselineExplodedRoot,
    [Parameter(Mandatory)][string]$CandidateExplodedRoot,
    [Parameter(Mandatory)][string]$BaselineJar,
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$BaselineImage,
    [Parameter(Mandatory)][string]$CandidateImage,
    [string]$BaselineProduct = "B0",
    [string]$CandidateProduct = "V2",
    [int]$Pairs = 3,
    [int]$Port = 8081,
    [int]$WarmupSeconds = 20,
    [int]$PostWorkloadSettleSeconds = 5,
    [int]$CustomerReadyTimeoutSeconds = 900,
    [switch]$SkipImageBuild,
    [switch]$AnalyzeExisting
)

$ErrorActionPreference = "Stop"

$RunDir = $OutputDir
$PetclinicOut = Join-Path $PetclinicRoot "out"
$Phase33KDir = Join-Path $PetclinicOut "phase33k"
$Phase33LDir = Join-Path $PetclinicOut "phase33l"
$Phase33LRunDir = Join-Path $Phase33LDir "run7-exploded-boot"
$ConfigDir = Join-Path $PetclinicRoot "spring-petclinic-microservices-config"
$CaptureScript = Join-Path $Phase33KDir "run2-clean\capture-evidence-v2.ps1"

$OriginProofJson = Join-Path $Phase33LDir "phase33l-exploded-dynamic-origin-proof.json"
$MaterializerJson = Join-Path $Phase33LDir "phase33l-exploded-boot-materializer.json"
$OutputJson = Join-Path $RunDir "v2-final-$ComparisonId-confirmation.json"
$OutputMd = Join-Path $RunDir "v2-final-$ComparisonId-confirmation.md"
$VerdictMd = Join-Path $RunDir "v2-final-$ComparisonId-verdict.md"
$CheckpointMd = Join-Path $RunDir "v2-final-$ComparisonId-checkpoint.md"
$CheckpointJson = Join-Path $RunDir "v2-final-$ComparisonId-checkpoint.json"

$CustomerFlags = "-XX:+UseContainerSupport -XX:+UseSerialGC -Xms32m -Xmx256m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -XX:NativeMemoryTracking=summary -Xshare:off"
$ConfigFlags = "-XX:+UseContainerSupport -XX:+UseSerialGC -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off"
$DiscoveryFlags = "-XX:+UseContainerSupport -XX:+UseSerialGC -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

function Join-Lines {
    param([object[]]$Value)
    return ($Value | ForEach-Object { "$_" }) -join "`n"
}

function Write-JsonFile {
    param($Value, [string]$Path)
    New-Item -ItemType Directory -Force -Path (Split-Path $Path -Parent) | Out-Null
    $Value | ConvertTo-Json -Depth 18 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-Lines {
    param([string[]]$Lines, [string]$Path)
    New-Item -ItemType Directory -Force -Path (Split-Path $Path -Parent) | Out-Null
    $Lines | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Format-Kb {
    param($Kb)
    if ($null -eq $Kb) { return "n/a" }
    return ("{0:n1} MB" -f ([double]$Kb / 1024.0))
}

function Format-Bytes {
    param($Bytes)
    if ($null -eq $Bytes) { return "n/a" }
    return ("{0:n1} MB" -f ([double]$Bytes / 1MB))
}

function Get-Median {
    param([object[]]$Values)
    $numbers = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ } | Sort-Object)
    if ($numbers.Count -eq 0) { return $null }
    $middle = [int][math]::Floor($numbers.Count / 2)
    if (($numbers.Count % 2) -eq 1) { return $numbers[$middle] }
    return [math]::Round((($numbers[$middle - 1] + $numbers[$middle]) / 2.0), 3)
}

function Invoke-Podman {
    param(
        [string]$Description,
        [object[]]$PodmanArgs
    )
    Write-Host $Description -ForegroundColor DarkCyan
    & podman @PodmanArgs
    if ($LASTEXITCODE -ne 0) {
        throw "podman failed: $Description"
    }
}

function Remove-ContainerIfExists {
    param([string]$Name)
    & podman container exists $Name *> $null
    if ($LASTEXITCODE -eq 0) {
        & podman rm -f $Name *> $null
    }
}

function Remove-NetworkIfExists {
    param([string]$Name)
    & podman network exists $Name *> $null
    if ($LASTEXITCODE -eq 0) {
        & podman network rm $Name *> $null
    }
}

function Stop-Stack {
    param(
        [string]$Prefix,
        [string]$Network
    )
    foreach ($suffix in @("customers-service", "discovery-server", "config-server")) {
        Remove-ContainerIfExists "$Prefix-$suffix"
    }
    Remove-NetworkIfExists $Network
}

function Wait-HttpUp {
    param(
        [string]$Name,
        [string]$Uri,
        [int]$TimeoutSeconds = 240,
        [switch]$RequireStatusUp
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Uri -TimeoutSec 8
            if (-not $RequireStatusUp -or $response.status -eq "UP") {
                Write-Host "$Name is up: $Uri" -ForegroundColor Green
                return
            }
            $lastError = "status=$($response.status)"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 3
    }
    throw "$Name did not become ready at $Uri. Last error: $lastError"
}

function Write-ExplodedDockerContext {
    param(
        [string]$Slug,
        [string]$ExplodedRoot
    )
    if (-not (Test-Path -LiteralPath $ExplodedRoot)) {
        throw "Missing exploded Boot root: $ExplodedRoot"
    }
    $context = Join-Path $RunDir "image-$Slug"
    if (Test-Path -LiteralPath $context) {
        Remove-Item -LiteralPath $context -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $context | Out-Null
    foreach ($layer in @("dependencies", "spring-boot-loader", "snapshot-dependencies", "application")) {
        $src = Join-Path $ExplodedRoot $layer
        if (Test-Path -LiteralPath $src) {
            Copy-Item -LiteralPath $src -Destination (Join-Path $context $layer) -Recurse -Force
        } else {
            New-Item -ItemType Directory -Force -Path (Join-Path $context $layer) | Out-Null
        }
    }
    @"
FROM eclipse-temurin:17
WORKDIR application
ENV SPRING_PROFILES_ACTIVE=docker
COPY dependencies/ ./
RUN true
COPY spring-boot-loader/ ./
RUN true
COPY snapshot-dependencies/ ./
RUN true
COPY application/ ./
EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
"@ | Set-Content -LiteralPath (Join-Path $context "Dockerfile") -Encoding ASCII
    return $context
}

function Build-Images {
    if ($SkipImageBuild) { return }
    $baselineContext = Write-ExplodedDockerContext -Slug "exploded-baseline" -ExplodedRoot $BaselineExplodedRoot
    $candidateContext = Write-ExplodedDockerContext -Slug "exploded-candidate" -ExplodedRoot $CandidateExplodedRoot
    Invoke-Podman "Build final baseline image" @("build", "-t", $BaselineImage, $baselineContext)
    Invoke-Podman "Build final candidate image" @("build", "-t", $CandidateImage, $candidateContext)
}

function Start-Stack {
    param(
        [string]$Prefix,
        [string]$Network,
        [string]$Image
    )
    Invoke-Podman "Create network $Network" @("network", "create", $Network)
    Invoke-Podman "Start config server" @(
        "run", "-d",
        "--name", "$Prefix-config-server",
        "--network", $Network,
        "--network-alias", "config-server",
        "-p", "8888:8888",
        "-v", "${ConfigDir}:/app/config-repo:ro",
        "-e", "SPRING_PROFILES_ACTIVE=native",
        "-e", "GIT_REPO=/app/config-repo",
        "-e", "MANAGEMENT_TRACING_ENABLED=false",
        "-e", "MANAGEMENT_METRICS_ENABLED=false",
        "-e", "JAVA_TOOL_OPTIONS=$ConfigFlags",
        "localhost/pc33-config-server:latest"
    )
    Wait-HttpUp -Name "config-server" -Uri "http://localhost:8888/actuator/health" -TimeoutSeconds 180

    Invoke-Podman "Start discovery server" @(
        "run", "-d",
        "--name", "$Prefix-discovery-server",
        "--network", $Network,
        "--network-alias", "discovery-server",
        "-p", "8761:8761",
        "-e", "SPRING_PROFILES_ACTIVE=docker",
        "-e", "CONFIG_SERVER_URI=http://config-server:8888",
        "-e", "JAVA_TOOL_OPTIONS=$DiscoveryFlags",
        "localhost/pc33-discovery-server:latest"
    )
    Wait-HttpUp -Name "discovery-server" -Uri "http://localhost:8761/actuator/health" -TimeoutSeconds 180

    Invoke-Podman "Start customers-service" @(
        "run", "-d",
        "--name", "$Prefix-customers-service",
        "--network", $Network,
        "--network-alias", "customers-service",
        "-p", "${Port}:8081",
        "-e", "SPRING_PROFILES_ACTIVE=docker",
        "-e", "CONFIG_SERVER_URI=http://config-server:8888",
        "-e", "JAVA_TOOL_OPTIONS=$CustomerFlags",
        "-e", "MALLOC_ARENA_MAX=1",
        $Image
    )
    Wait-HttpUp -Name "customers-service" -Uri "http://localhost:$Port/actuator/health" -TimeoutSeconds $CustomerReadyTimeoutSeconds -RequireStatusUp
}

function Invoke-Workload {
    param(
        [string]$Label,
        [int]$TargetPort = 8081
    )
    $base = "http://localhost:$TargetPort"
    $endpoints = @(
        @{ method = "GET"; path = "/actuator/health" },
        @{ method = "GET"; path = "/actuator/info" },
        @{ method = "GET"; path = "/owners" },
        @{ method = "GET"; path = "/owners/1" },
        @{ method = "GET"; path = "/owners/2" },
        @{ method = "GET"; path = "/owners/3" },
        @{ method = "GET"; path = "/owners/4" },
        @{ method = "GET"; path = "/owners/5" },
        @{ method = "GET"; path = "/owners/6" },
        @{ method = "GET"; path = "/owners/7" },
        @{ method = "GET"; path = "/owners/8" },
        @{ method = "GET"; path = "/owners/9" },
        @{ method = "GET"; path = "/owners/10" },
        @{ method = "GET"; path = "/petTypes" },
        @{ method = "GET"; path = "/owners/1/pets/1" },
        @{ method = "GET"; path = "/owners/2/pets/2" },
        @{ method = "GET"; path = "/owners/3/pets/3" },
        @{ method = "GET"; path = "/owners/4/pets/4" },
        @{ method = "GET"; path = "/owners/5/pets/5" },
        @{ method = "GET"; path = "/owners/6/pets/6" },
        @{ method = "POST"; path = "/owners"; body = '{"firstName":"CDS","lastName":"Test","address":"1 CDS St","city":"CDSCity","telephone":"5551234567"}'; contentType = "application/json" },
        @{ method = "POST"; path = "/owners"; body = '{"firstName":"CDS2","lastName":"Test2","address":"2 CDS St","city":"CDSCity2","telephone":"5551234568"}'; contentType = "application/json" },
        @{ method = "PUT"; path = "/owners/1"; body = '{"id":1,"firstName":"George","lastName":"Franklin","address":"110 W. Liberty St.","city":"Madison","telephone":"6085551023"}'; contentType = "application/json" },
        @{ method = "GET"; path = "/owners/1" },
        @{ method = "GET"; path = "/owners/2" },
        @{ method = "GET"; path = "/owners/3" },
        @{ method = "GET"; path = "/actuator/health" }
    )
    $errors = 0
    $failures = New-Object System.Collections.Generic.List[object]
    for ($round = 1; $round -le 3; $round++) {
        foreach ($ep in $endpoints) {
            try {
                $params = @{ Uri = "$base$($ep.path)"; Method = $ep.method; TimeoutSec = 20 }
                if ($ep.ContainsKey("body")) {
                    $params.Body = $ep.body
                    $params.ContentType = $ep.contentType
                }
                $response = Invoke-WebRequest @params -ErrorAction Stop
                if ($response.StatusCode -ge 400) {
                    $errors++
                    $failures.Add([pscustomobject]@{ round = $round; method = $ep.method; path = $ep.path; status = $response.StatusCode }) | Out-Null
                }
            } catch {
                $errors++
                $failures.Add([pscustomobject]@{ round = $round; method = $ep.method; path = $ep.path; error = "$($_.Exception.Message)" }) | Out-Null
            }
            Start-Sleep -Milliseconds 200
        }
    }
    $result = [ordered]@{
        label = $Label
        endpoints = $endpoints.Count
        rounds = 3
        total_requests = $endpoints.Count * 3
        errors = $errors
        failures = $failures.ToArray()
    }
    Write-JsonFile -Value $result -Path (Join-Path $RunDir "$Label-workload.json")
    if ($errors -ne 0) {
        throw "Workload $Label had $errors errors"
    }
}

function Save-ExtraCaptures {
    param(
        [string]$Label,
        [string]$ContainerName
    )
    $heapInfo = Join-Lines (& podman exec $ContainerName jcmd 1 GC.heap_info 2>&1)
    Set-Content -LiteralPath (Join-Path $RunDir "$Label-heap-info.txt") -Value $heapInfo -Encoding UTF8
    $hist = Join-Lines (& podman exec $ContainerName jcmd 1 GC.class_histogram 2>&1)
    Set-Content -LiteralPath (Join-Path $RunDir "$Label-class-histogram.txt") -Value $hist -Encoding UTF8
    $envText = Join-Lines (& podman exec $ContainerName sh -lc "env | sort | grep -E '^(MALLOC_|JAVA_TOOL_OPTIONS=)' || true" 2>&1)
    Set-Content -LiteralPath (Join-Path $RunDir "$Label-env.txt") -Value $envText -Encoding UTF8
    $memoryStat = Join-Lines (& podman exec $ContainerName sh -lc "cat /sys/fs/cgroup/memory.stat" 2>&1)
    Set-Content -LiteralPath (Join-Path $RunDir "$Label-memory-stat.txt") -Value $memoryStat -Encoding UTF8
}

function Reset-PodmanPageCache {
    $output = Join-Lines (& podman machine ssh "sudo sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' && echo DROP_OK" 2>&1)
    if ($LASTEXITCODE -ne 0 -or $output -notmatch "DROP_OK") {
        throw "Could not establish a cold page-cache precondition: $output"
    }
}

function Measure-Variant {
    param(
        [int]$Pair,
        [string]$Variant,
        [string]$Image
    )
    $label = "p$Pair-$Variant"
    $prefix = "k33m-p$Pair-$Variant"
    $network = "$prefix-net"
    $container = "$prefix-customers-service"
    Stop-Stack -Prefix $prefix -Network $network
    try {
        Write-Step "Pair $Pair / $Variant"
        Reset-PodmanPageCache
        Start-Stack -Prefix $prefix -Network $network -Image $Image
        Start-Sleep -Seconds $WarmupSeconds
        Invoke-Workload -Label $label -TargetPort $Port
        if ($PostWorkloadSettleSeconds -gt 0) {
            Start-Sleep -Seconds $PostWorkloadSettleSeconds
        }
        & $CaptureScript -Label "$label-post" -ContainerName $container -Port $Port -OutDir $RunDir -PostWorkload | Out-Host
        Save-ExtraCaptures -Label "$label-post" -ContainerName $container
    } finally {
        Stop-Stack -Prefix $prefix -Network $network
    }
}

function Get-SmapsCategory {
    param([string]$Perms, [string]$Path)
    $p = ($Path ?? "").Trim()
    if ($p -eq "[heap]") { return "heap" }
    if ($p -match "^\[stack") { return "stack" }
    if (($Perms -match "x") -and [string]::IsNullOrWhiteSpace($p)) { return "anonymous_executable_code" }
    if ([string]::IsNullOrWhiteSpace($p)) {
        if ($Perms -match "^rw") { return "anonymous_rw" }
        return "anonymous_other"
    }
    if ($p -match "\.jar($|!|/|\s)" -or $p -match "BOOT-INF|spring-boot|/application/") { return "jar_or_boot_mapping" }
    if ($p -match "\.so($|\.|\s)|/libjvm\.so|/usr/lib|/lib64|/lib/") { return "native_library" }
    if ($p -match "^\[") { return "special_mapping" }
    return "mapped_file"
}

function Read-SmapsCategoryTotals {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return @{} }
    $totals = @{}
    $script:totalsForSmaps = $totals
    $script:categoryForSmaps = $null
    $script:pssForSmaps = 0L
    $script:dirtyForSmaps = 0L
    $headerPattern = "^(?<range>[0-9a-f]+-[0-9a-f]+)\s+(?<perms>\S+)\s+(?<offset>[0-9a-f]+)\s+(?<dev>\S+)\s+(?<inode>\d+)\s*(?<path>.*)$"
    function Add-Region {
        if ($null -eq $script:categoryForSmaps) { return }
        if (-not $script:totalsForSmaps.ContainsKey($script:categoryForSmaps)) {
            $script:totalsForSmaps[$script:categoryForSmaps] = [ordered]@{ category = $script:categoryForSmaps; pss_kb = 0L; private_dirty_kb = 0L }
        }
        $script:totalsForSmaps[$script:categoryForSmaps].pss_kb += $script:pssForSmaps
        $script:totalsForSmaps[$script:categoryForSmaps].private_dirty_kb += $script:dirtyForSmaps
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match $headerPattern) {
            Add-Region
            $script:categoryForSmaps = Get-SmapsCategory -Perms $Matches.perms -Path $Matches.path.Trim()
            $script:pssForSmaps = 0L
            $script:dirtyForSmaps = 0L
            continue
        }
        if ($line -match "^Pss:\s+(\d+)\s+kB") {
            $script:pssForSmaps = [long]$Matches[1]
        } elseif ($line -match "^Private_Dirty:\s+(\d+)\s+kB") {
            $script:dirtyForSmaps = [long]$Matches[1]
        }
    }
    Add-Region
    $result = @{}
    foreach ($key in $script:totalsForSmaps.Keys) {
        $result[$key] = [pscustomobject]$script:totalsForSmaps[$key]
    }
    Remove-Variable -Scope Script -Name totalsForSmaps,categoryForSmaps,pssForSmaps,dirtyForSmaps -ErrorAction SilentlyContinue
    return $result
}

function Get-CategoryValue {
    param([hashtable]$Totals, [string]$Category, [string]$Metric)
    if (-not $Totals.ContainsKey($Category)) { return 0L }
    return [long]$Totals[$Category].$Metric
}

function Parse-HeapInfo {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return [pscustomobject]@{ used_kb = 0L; total_kb = 0L } }
    $text = Get-Content -LiteralPath $Path -Raw
    $used = 0L
    $total = 0L
    if ($text -match "def new generation\s+total\s+(\d+)K,\s+used\s+(\d+)K") {
        $total += [long]$Matches[1]
        $used += [long]$Matches[2]
    }
    if ($text -match "tenured generation\s+total\s+(\d+)K,\s+used\s+(\d+)K") {
        $total += [long]$Matches[1]
        $used += [long]$Matches[2]
    }
    return [pscustomobject]@{ used_kb = $used; total_kb = $total }
}

function Parse-ClassHistogramTotal {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return [pscustomobject]@{ total_instances = 0L; total_bytes = 0L } }
    $text = Get-Content -LiteralPath $Path -Raw
    if ($text -match "(?m)^Total\s+(\d+)\s+(\d+)") {
        return [pscustomobject]@{ total_instances = [long]$Matches[1]; total_bytes = [long]$Matches[2] }
    }
    return [pscustomobject]@{ total_instances = 0L; total_bytes = 0L }
}

function Read-Sample {
    param([int]$Pair, [string]$Variant)
    $captureVariant = $Variant
    $label = "p$Pair-$captureVariant-post"
    $jsonPath = Join-Path $RunDir "$label.json"
    if ($Variant -eq "candidate" -and -not (Test-Path -LiteralPath $jsonPath)) {
        $captureVariant = "full-p2"
        $label = "p$Pair-$captureVariant-post"
        $jsonPath = Join-Path $RunDir "$label.json"
    }
    if (-not (Test-Path -LiteralPath $jsonPath)) { return $null }
    $capture = Read-JsonFile $jsonPath
    $smaps = Read-SmapsCategoryTotals (Join-Path $RunDir "$label-smaps-full.txt")
    $heap = Parse-HeapInfo (Join-Path $RunDir "$label-heap-info.txt")
    $hist = Parse-ClassHistogramTotal (Join-Path $RunDir "$label-class-histogram.txt")
    $envText = if (Test-Path -LiteralPath (Join-Path $RunDir "$label-env.txt")) { Get-Content -LiteralPath (Join-Path $RunDir "$label-env.txt") -Raw } else { "" }
    $workload = Read-JsonFile (Join-Path $RunDir "p$Pair-$captureVariant-workload.json")
    return [pscustomobject][ordered]@{
        pair = $Pair
        variant = $Variant
        health = $capture.health
        workload_errors = if ($workload) { [long]$workload.errors } else { $null }
        cds_xshare_off = [bool]$capture.cds_xshare_off
        cds_shared_archive = [bool]$capture.cds_shared_archive
        javaagent_present = [bool]$capture.javaagent_present
        malloc_arena_max_1 = ($envText -match "MALLOC_ARENA_MAX=1")
        rss_kb = [long]$capture.smaps_rss_kb
        smaps_pss_kb = [long]$capture.smaps_pss_kb
        smaps_private_dirty_kb = [long]$capture.smaps_private_dirty_kb
        memory_current_bytes = [long]$capture.memory_current_bytes
        smaps_heap_pss_kb = Get-CategoryValue -Totals $smaps -Category "heap" -Metric "pss_kb"
        smaps_heap_private_dirty_kb = Get-CategoryValue -Totals $smaps -Category "heap" -Metric "private_dirty_kb"
        anonymous_rw_pss_kb = Get-CategoryValue -Totals $smaps -Category "anonymous_rw" -Metric "pss_kb"
        anonymous_rw_private_dirty_kb = Get-CategoryValue -Totals $smaps -Category "anonymous_rw" -Metric "private_dirty_kb"
        nmt_total_committed_kb = [long]($capture.nmt_total_committed_kb ?? 0)
        nmt_java_heap_committed_kb = [long]($capture.nmt_java_heap_committed_kb ?? 0)
        heap_info_used_kb = [long]$heap.used_kb
        heap_info_total_kb = [long]$heap.total_kb
        class_histogram_total_instances = [long]$hist.total_instances
        class_histogram_total_bytes = [long]$hist.total_bytes
        loaded_classes = [long]($capture.nmt_classes ?? 0)
        startup_seconds = [double]($capture.startup_seconds ?? 0.0)
    }
}

function New-PairDelta {
    param($Baseline, $Candidate)
    $deltaPss = $Candidate.smaps_pss_kb - $Baseline.smaps_pss_kb
    $deltaPrivateDirty = $Candidate.smaps_private_dirty_kb - $Baseline.smaps_private_dirty_kb
    $deltaMemoryCurrent = $Candidate.memory_current_bytes - $Baseline.memory_current_bytes
    $pass = (
        $deltaPss -le -1024 -and
        $deltaPrivateDirty -le -1024 -and
        $deltaMemoryCurrent -le -1MB -and
        $Baseline.workload_errors -eq 0 -and
        $Candidate.workload_errors -eq 0 -and
        $Baseline.health -eq "UP" -and
        $Candidate.health -eq "UP" -and
        $Baseline.cds_xshare_off -and
        $Candidate.cds_xshare_off -and
        -not $Baseline.cds_shared_archive -and
        -not $Candidate.cds_shared_archive -and
        -not $Baseline.javaagent_present -and
        -not $Candidate.javaagent_present -and
        $Baseline.malloc_arena_max_1 -and
        $Candidate.malloc_arena_max_1
    )
    return [pscustomobject][ordered]@{
        pair = $Baseline.pair
        pass = [bool]$pass
        deltas = [ordered]@{
            rss_kb = $Candidate.rss_kb - $Baseline.rss_kb
            smaps_pss_kb = $deltaPss
            smaps_private_dirty_kb = $deltaPrivateDirty
            memory_current_bytes = $deltaMemoryCurrent
            smaps_heap_pss_kb = $Candidate.smaps_heap_pss_kb - $Baseline.smaps_heap_pss_kb
            smaps_heap_private_dirty_kb = $Candidate.smaps_heap_private_dirty_kb - $Baseline.smaps_heap_private_dirty_kb
            anonymous_rw_pss_kb = $Candidate.anonymous_rw_pss_kb - $Baseline.anonymous_rw_pss_kb
            anonymous_rw_private_dirty_kb = $Candidate.anonymous_rw_private_dirty_kb - $Baseline.anonymous_rw_private_dirty_kb
            nmt_total_committed_kb = $Candidate.nmt_total_committed_kb - $Baseline.nmt_total_committed_kb
            nmt_java_heap_committed_kb = $Candidate.nmt_java_heap_committed_kb - $Baseline.nmt_java_heap_committed_kb
            heap_info_used_kb = $Candidate.heap_info_used_kb - $Baseline.heap_info_used_kb
            class_histogram_total_bytes = $Candidate.class_histogram_total_bytes - $Baseline.class_histogram_total_bytes
            loaded_classes = $Candidate.loaded_classes - $Baseline.loaded_classes
            startup_seconds = [math]::Round(($Candidate.startup_seconds - $Baseline.startup_seconds), 3)
        }
    }
}

function Build-Analysis {
    $origin = Read-JsonFile $OriginProofJson
    $materializer = Read-JsonFile $MaterializerJson
    $originPassed = [bool]($origin.status -eq "passed" -and $origin.proof.dynamic_origin_proven)
    $materializerReady = [bool]($materializer.acceptance.ready_for_runtime_smoke)

    $samples = @()
    for ($pair = 1; $pair -le $Pairs; $pair++) {
        $samples += Read-Sample -Pair $pair -Variant "baseline"
        $samples += Read-Sample -Pair $pair -Variant "candidate"
    }
    $pairResults = @()
    for ($pair = 1; $pair -le $Pairs; $pair++) {
        $baseline = $samples | Where-Object { $_.pair -eq $pair -and $_.variant -eq "baseline" } | Select-Object -First 1
        $candidate = $samples | Where-Object { $_.pair -eq $pair -and $_.variant -eq "candidate" } | Select-Object -First 1
        if (-not $baseline -or -not $candidate) { throw "Missing sample for pair $pair" }
        $pairResults += New-PairDelta -Baseline $baseline -Candidate $candidate
    }

    $metrics = @(
        "rss_kb",
        "smaps_pss_kb",
        "smaps_private_dirty_kb",
        "memory_current_bytes",
        "smaps_heap_pss_kb",
        "smaps_heap_private_dirty_kb",
        "anonymous_rw_pss_kb",
        "anonymous_rw_private_dirty_kb",
        "nmt_total_committed_kb",
        "nmt_java_heap_committed_kb",
        "heap_info_used_kb",
        "class_histogram_total_bytes",
        "loaded_classes",
        "startup_seconds"
    )
    $medians = [ordered]@{}
    foreach ($metric in $metrics) {
        $medians[$metric] = [ordered]@{
            baseline = Get-Median @($samples | Where-Object { $_.variant -eq "baseline" } | ForEach-Object { $_.$metric })
            candidate = Get-Median @($samples | Where-Object { $_.variant -eq "candidate" } | ForEach-Object { $_.$metric })
            delta = Get-Median @($pairResults | ForEach-Object { $_.deltas.$metric })
        }
    }

    $pairedWins = @($pairResults | Where-Object pass).Count
    $workloadErrors = @($samples | Measure-Object -Property workload_errors -Sum).Sum
    $allNoCds = (@($samples | Where-Object { -not $_.cds_xshare_off -or $_.cds_shared_archive }).Count -eq 0)
    $allNoJavaagent = (@($samples | Where-Object { $_.javaagent_present }).Count -eq 0)
    $allMalloc = (@($samples | Where-Object { -not $_.malloc_arena_max_1 }).Count -eq 0)
    $confirmed = (
        $pairedWins -ge 2 -and
        $medians.smaps_pss_kb.delta -le -1024 -and
        $medians.smaps_private_dirty_kb.delta -le -1024 -and
        $medians.memory_current_bytes.delta -le -1MB -and
        $workloadErrors -eq 0 -and
        $allNoCds -and
        $allNoJavaagent -and
        $allMalloc -and
        $originPassed -and
        $materializerReady
    )
    $marginal = (
        -not $confirmed -and
        $pairedWins -ge 2 -and
        $medians.smaps_pss_kb.delta -le -1024 -and
        $medians.smaps_private_dirty_kb.delta -le -1024 -and
        $medians.memory_current_bytes.delta -gt -1MB -and
        $workloadErrors -eq 0
    )

    return [pscustomobject][ordered]@{
        phase = "V2-FINAL"
        comparison_id = $ComparisonId
        baseline_product = $BaselineProduct
        title = "Final V2 B0 vs V2 direct no-CDS confirmation"
        generated_at = (Get-Date).ToString("o")
        candidate = $CandidateProduct
        launch_mode = "EXPLODED_BOOT_APP"
        runtime_policy = [ordered]@{
            no_cds = $true
            no_appcds = $true
            no_leyden = $true
            no_javaagent = $allNoJavaagent
            malloc_arena_max = "1"
            java_tool_options = $CustomerFlags
            class_load_logging = "not enabled during memory pairs; referenced 33L.8 dynamic origin proof"
        }
        artifacts = [ordered]@{
            baseline_jar = $BaselineJar
            candidate_jar = $CandidateJar
            baseline_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $BaselineJar).Hash
            candidate_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $CandidateJar).Hash
            baseline_exploded_root = $BaselineExplodedRoot
            candidate_exploded_root = $CandidateExplodedRoot
        }
        gates = [ordered]@{
            materializer_ready = $materializerReady
            materializer_source = $MaterializerJson
            dynamic_origin_proven = $originPassed
            dynamic_origin_source = $OriginProofJson
            dynamic_origin_sample_accepted_count = $origin.proof.sample_accepted_count
            workload_errors_total = [long]$workloadErrors
            no_cds_all_samples = $allNoCds
            no_javaagent_all_samples = $allNoJavaagent
            malloc_arena_max_1_all_samples = $allMalloc
        }
        samples = $samples
        pair_results = $pairResults
        medians = $medians
        win_gate = [ordered]@{
            median_pss_delta_le_minus_1mb = ($medians.smaps_pss_kb.delta -le -1024)
            median_private_dirty_delta_le_minus_1mb = ($medians.smaps_private_dirty_kb.delta -le -1024)
            median_memory_current_delta_le_minus_1mb = ($medians.memory_current_bytes.delta -le -1MB)
            paired_wins = $pairedWins
            paired_wins_at_least_2_of_3 = ($pairedWins -ge 2)
            workload_errors_zero = ($workloadErrors -eq 0)
            no_cds = $allNoCds
            no_javaagent = $allNoJavaagent
            dynamic_origins_verified = $originPassed
            confirmed = [bool]$confirmed
            marginal = [bool]$marginal
        }
        verdict = if ($confirmed) {
            "confirmed-public-nocds-win"
        } elseif ($marginal) {
            "marginal-run-5pair-confirmation"
        } else {
            "not-confirmed-run-package-isolation-under-exploded-boot"
        }
        next_action = if ($confirmed) {
            "Write final public no-CDS case study with full P2 under exploded Boot and MALLOC_ARENA_MAX=1."
        } elseif ($marginal) {
            "Run a 5-pair exploded Boot confirmation before claiming a public win."
        } else {
            "Proceed to package isolation under EXPLODED_BOOT_APP, not fat JAR."
        }
    }
}

function Write-Reports {
    param($Analysis)
    Write-JsonFile -Value $Analysis -Path $OutputJson

    $pairsRows = @($Analysis.pair_results | ForEach-Object {
        "| $($_.pair) | $($_.pass) | $(Format-Kb $_.deltas.smaps_pss_kb) | $(Format-Kb $_.deltas.smaps_private_dirty_kb) | $(Format-Bytes $_.deltas.memory_current_bytes) | $(Format-Kb $_.deltas.smaps_heap_pss_kb) | $(Format-Kb $_.deltas.anonymous_rw_pss_kb) | $($_.deltas.loaded_classes) | $($_.deltas.startup_seconds) |"
    })
    $med = $Analysis.medians
    $confirmationLines = New-Object System.Collections.Generic.List[string]
    foreach ($line in @(
        "# V2 Final $ComparisonId Confirmation",
        "",
        "Generated: $($Analysis.generated_at)",
        "",
        "## Verdict",
        "",
        "Result: **$($Analysis.verdict)**",
        "",
        "Next action: $($Analysis.next_action)",
        "",
        "## Candidate",
        "",
        "- Candidate: ``$($Analysis.candidate)``",
        "- Launch mode: ``$($Analysis.launch_mode)``",
        "- Runtime: no CDS, no AppCDS, no Leyden, no javaagent, ``MALLOC_ARENA_MAX=1``",
        "- Dynamic origin proof: ``$($Analysis.gates.dynamic_origin_proven)`` from ``$($Analysis.gates.dynamic_origin_source)``",
        "- Materializer ready: ``$($Analysis.gates.materializer_ready)`` from ``$($Analysis.gates.materializer_source)``",
        "",
        "## Win Gate",
        "",
        "- median PSS delta <= -1 MB: ``$($Analysis.win_gate.median_pss_delta_le_minus_1mb)``",
        "- median Private_Dirty delta <= -1 MB: ``$($Analysis.win_gate.median_private_dirty_delta_le_minus_1mb)``",
        "- median memory.current delta <= -1 MB: ``$($Analysis.win_gate.median_memory_current_delta_le_minus_1mb)``",
        "- paired wins >= 2/3: ``$($Analysis.win_gate.paired_wins_at_least_2_of_3)`` ($($Analysis.win_gate.paired_wins)/3)",
        "- workload errors = 0: ``$($Analysis.win_gate.workload_errors_zero)``",
        "- no CDS: ``$($Analysis.win_gate.no_cds)``",
        "- no javaagent: ``$($Analysis.win_gate.no_javaagent)``",
        "- dynamic origins verified: ``$($Analysis.win_gate.dynamic_origins_verified)``",
        "- confirmed: ``$($Analysis.win_gate.confirmed)``",
        "",
        "## Median Deltas",
        "",
        "| Metric | Baseline median | Full P2 median | Delta |",
        "|---|---:|---:|---:|",
        "| RSS | $(Format-Kb $med.rss_kb.baseline) | $(Format-Kb $med.rss_kb.candidate) | $(Format-Kb $med.rss_kb.delta) |",
        "| PSS | $(Format-Kb $med.smaps_pss_kb.baseline) | $(Format-Kb $med.smaps_pss_kb.candidate) | $(Format-Kb $med.smaps_pss_kb.delta) |",
        "| Private_Dirty | $(Format-Kb $med.smaps_private_dirty_kb.baseline) | $(Format-Kb $med.smaps_private_dirty_kb.candidate) | $(Format-Kb $med.smaps_private_dirty_kb.delta) |",
        "| memory.current | $(Format-Bytes $med.memory_current_bytes.baseline) | $(Format-Bytes $med.memory_current_bytes.candidate) | $(Format-Bytes $med.memory_current_bytes.delta) |",
        "| heap PSS | $(Format-Kb $med.smaps_heap_pss_kb.baseline) | $(Format-Kb $med.smaps_heap_pss_kb.candidate) | $(Format-Kb $med.smaps_heap_pss_kb.delta) |",
        "| anonymous_rw PSS | $(Format-Kb $med.anonymous_rw_pss_kb.baseline) | $(Format-Kb $med.anonymous_rw_pss_kb.candidate) | $(Format-Kb $med.anonymous_rw_pss_kb.delta) |",
        "| NMT total committed | $(Format-Kb $med.nmt_total_committed_kb.baseline) | $(Format-Kb $med.nmt_total_committed_kb.candidate) | $(Format-Kb $med.nmt_total_committed_kb.delta) |",
        "| Java heap committed | $(Format-Kb $med.nmt_java_heap_committed_kb.baseline) | $(Format-Kb $med.nmt_java_heap_committed_kb.candidate) | $(Format-Kb $med.nmt_java_heap_committed_kb.delta) |",
        "| heap used | $(Format-Kb $med.heap_info_used_kb.baseline) | $(Format-Kb $med.heap_info_used_kb.candidate) | $(Format-Kb $med.heap_info_used_kb.delta) |",
        "| class histogram bytes | $(Format-Bytes $med.class_histogram_total_bytes.baseline) | $(Format-Bytes $med.class_histogram_total_bytes.candidate) | $(Format-Bytes $med.class_histogram_total_bytes.delta) |",
        "| loaded classes | $($med.loaded_classes.baseline) | $($med.loaded_classes.candidate) | $($med.loaded_classes.delta) |",
        "| startup seconds | $($med.startup_seconds.baseline) | $($med.startup_seconds.candidate) | $($med.startup_seconds.delta) |",
        "",
        "## Pair Deltas",
        "",
        "| Pair | Pass | PSS | Private_Dirty | memory.current | heap PSS | anonymous_rw PSS | classes | startup s |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|"
    )) {
        $confirmationLines.Add($line)
    }
    foreach ($row in $pairsRows) {
        $confirmationLines.Add($row)
    }
    Write-Lines -Path $OutputMd -Lines $confirmationLines.ToArray()

    Write-Lines -Path $VerdictMd -Lines @(
        "# V2 Final $ComparisonId Verdict",
        "",
        "Generated: $($Analysis.generated_at)",
        "",
        "Verdict: **$($Analysis.verdict)**",
        "",
        "Candidate: ``candidate``",
        "",
        "Launch mode: ``EXPLODED_BOOT_APP``",
        "",
        "Runtime policy: no CDS, no AppCDS, no Leyden, no javaagent, ``MALLOC_ARENA_MAX=1``",
        "",
        "Median result:",
        "",
        "- PSS: $(Format-Kb $med.smaps_pss_kb.delta)",
        "- Private_Dirty: $(Format-Kb $med.smaps_private_dirty_kb.delta)",
        "- memory.current: $(Format-Bytes $med.memory_current_bytes.delta)",
        "- heap PSS: $(Format-Kb $med.smaps_heap_pss_kb.delta)",
        "- loaded classes: $($med.loaded_classes.delta)",
        "- paired wins: $($Analysis.win_gate.paired_wins)/3",
        "",
        "Next action: $($Analysis.next_action)"
    )

    $checkpoint = [ordered]@{
        phase = "V2-FINAL"
        generated_at = $Analysis.generated_at
        status = if ($Analysis.win_gate.confirmed) { "complete" } elseif ($Analysis.win_gate.marginal) { "marginal-needs-5pair" } else { "failed-return-to-33k8-exploded" }
        candidate = $Analysis.candidate
        launch_mode = $Analysis.launch_mode
        runtime_policy = "NO_CDS_LOW_DIRTY"
        confirmation = $OutputJson
        verdict = $Analysis.verdict
        next_action = $Analysis.next_action
    }
    Write-JsonFile -Value $checkpoint -Path $CheckpointJson
    Write-Lines -Path $CheckpointMd -Lines @(
        "# V2 Final $ComparisonId Checkpoint",
        "",
        "Generated: $($Analysis.generated_at)",
        "",
        "Status: **$($checkpoint.status)**",
        "",
        "- Candidate: ``$($checkpoint.candidate)``",
        "- Launch mode: ``$($checkpoint.launch_mode)``",
        "- Runtime policy: ``$($checkpoint.runtime_policy)``",
        "- Confirmation: ``$OutputJson``",
        "- Verdict: ``$($checkpoint.verdict)``",
        "",
        "Next action: $($checkpoint.next_action)"
    )
}

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

foreach ($required in @($CaptureScript, $ConfigDir, $OriginProofJson, $MaterializerJson, $BaselineExplodedRoot, $CandidateExplodedRoot, $BaselineJar, $CandidateJar)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required file missing: $required"
    }
}

$transcriptPath = Join-Path $RunDir "run-33m-integrated-nocds-confirmation.log"
Start-Transcript -Path $transcriptPath -Force | Out-Null
try {
    if (-not $AnalyzeExisting) {
        Write-Step "Build final comparison images"
        Build-Images
        for ($pair = 1; $pair -le $Pairs; $pair++) {
            if (($pair % 2) -eq 0) {
                Measure-Variant -Pair $pair -Variant "candidate" -Image $CandidateImage
                Measure-Variant -Pair $pair -Variant "baseline" -Image $BaselineImage
            } else {
                Measure-Variant -Pair $pair -Variant "baseline" -Image $BaselineImage
                Measure-Variant -Pair $pair -Variant "candidate" -Image $CandidateImage
            }
        }
    }
    Write-Step "Analyze final comparison"
    $analysis = Build-Analysis
    Write-Reports -Analysis $analysis
    Write-Host "Final comparison verdict: $($analysis.verdict)" -ForegroundColor $(if ($analysis.win_gate.confirmed) { "Green" } elseif ($analysis.win_gate.marginal) { "Yellow" } else { "Red" })
    Write-Host "Report: $OutputMd"
    Write-Host "Verdict: $VerdictMd"
} finally {
    Stop-Transcript | Out-Null
}



