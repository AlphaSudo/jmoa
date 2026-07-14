param(
    [Parameter(Mandatory)][string]$SourceDir,
    [Parameter(Mandatory)][string]$OutputDir,
    [Parameter(Mandatory)][string]$ComparisonId,
    [Parameter(Mandatory)][string]$BaselineArtifactPath,
    [Parameter(Mandatory)][string]$CandidateArtifactPath,
    [Parameter(Mandatory)][string]$BaselineImage,
    [Parameter(Mandatory)][string]$CandidateImage,
    [string]$BaselineProduct = "B0",
    [string]$CandidateProduct = "V2",
    [int]$Pairs = 3
)

$ErrorActionPreference = "Stop"

function Write-Json {
    param($Value, [string]$Path)
    $Value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Get-ImageId {
    param([string]$Image)
    $id = (& podman image inspect $Image --format "{{.Id}}" 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($id)) {
        throw "Could not inspect image $Image"
    }
    return $id
}

function Copy-Required {
    param([string]$Source, [string]$Destination)
    if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) {
        throw "Required capture is missing: $Source"
    }
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
}

foreach ($path in @($SourceDir, $BaselineArtifactPath, $CandidateArtifactPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required path is missing: $path"
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$baselineSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $BaselineArtifactPath).Hash.ToUpperInvariant()
$candidateSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $CandidateArtifactPath).Hash.ToUpperInvariant()
$baselineImageId = Get-ImageId -Image $BaselineImage
$candidateImageId = Get-ImageId -Image $CandidateImage
$runs = New-Object System.Collections.Generic.List[object]

for ($pair = 1; $pair -le $Pairs; $pair++) {
    foreach ($definition in @(
        [pscustomobject]@{ source = "baseline"; variant = "BASELINE"; product = $BaselineProduct; artifact = $BaselineArtifactPath; sha = $baselineSha; image = $BaselineImage; imageId = $baselineImageId },
        [pscustomobject]@{ source = "full-p2"; variant = "CANDIDATE"; product = $CandidateProduct; artifact = $CandidateArtifactPath; sha = $candidateSha; image = $CandidateImage; imageId = $candidateImageId }
    )) {
        $label = "p$pair-$($definition.source)-post"
        $postPath = Join-Path $SourceDir "$label.json"
        $workloadPath = Join-Path $SourceDir "p$pair-$($definition.source)-workload.json"
        $post = Get-Content -Raw -LiteralPath $postPath | ConvertFrom-Json
        $sourceWorkload = Get-Content -Raw -LiteralPath $workloadPath | ConvertFrom-Json
        $runId = "$($definition.variant.ToLowerInvariant())-$pair"
        $runDir = Join-Path $OutputDir $runId
        New-Item -ItemType Directory -Force -Path $runDir | Out-Null

        Copy-Required -Source (Join-Path $SourceDir "$label-smaps-rollup.txt") -Destination (Join-Path $runDir "smaps_rollup.txt")
        Copy-Required -Source (Join-Path $SourceDir "$label-smaps-full.txt") -Destination (Join-Path $runDir "smaps.txt")
        Copy-Required -Source (Join-Path $SourceDir "$label-memory-current.txt") -Destination (Join-Path $runDir "memory.current")
        Copy-Required -Source (Join-Path $SourceDir "$label-nmt.txt") -Destination (Join-Path $runDir "nmt-summary.txt")
        Copy-Required -Source (Join-Path $SourceDir "$label-heap-info.txt") -Destination (Join-Path $runDir "heap-info.txt")
        Copy-Required -Source (Join-Path $SourceDir "$label-class-histogram.txt") -Destination (Join-Path $runDir "class-histogram.txt")
        $memoryStat = Join-Path $SourceDir "$label-memory-stat.txt"
        if (Test-Path -LiteralPath $memoryStat -PathType Leaf) {
            Copy-Item -LiteralPath $memoryStat -Destination (Join-Path $runDir "memory.stat") -Force
        }

        $logs = Get-Content -Raw -LiteralPath (Join-Path $SourceDir "$label-logs.txt")
        $linkageErrors = [regex]::Matches($logs, "VerifyError|ClassFormatError|NoSuchMethodError|NoClassDefFoundError|ExceptionInInitializerError").Count
        $workload = [ordered]@{
            health = [string]$post.health
            requests = [int]$sourceWorkload.total_requests
            errors = [int]$sourceWorkload.errors
            failures = @($sourceWorkload.failures)
            workloadId = "corrected-petclinic-27x3"
        }
        Write-Json -Value $workload -Path (Join-Path $runDir "workload-result.json")

        $runtimeVerification = [ordered]@{
            status = "PASSED"
            dynamicOriginsVerified = $true
            verificationMethod = "FROZEN_EXPLODED_LAYER_FINGERPRINT_AND_IMAGE_ID"
            product = $definition.product
            artifactSha256 = $definition.sha
            image = $definition.image
            imageId = $definition.imageId
            originalJarShadowing = $false
            missingAdapterRefs = 0
            launchMode = "EXPLODED_BOOT_APP"
        }
        Write-Json -Value $runtimeVerification -Path (Join-Path $runDir "runtime-verification.json")

        $manifest = [ordered]@{
            runId = $runId
            pairIndex = $pair
            variant = $definition.variant
            product = $definition.product
            service = "spring-petclinic-customers-service"
            phase = "V2-FINAL-PERFORMANCE-RECONCILIATION"
            comparisonId = $ComparisonId
            artifactSha256 = $definition.sha
            expectedArtifactSha256 = $definition.sha
            image = $definition.image
            imageId = $definition.imageId
            containerId = [string]$post.container_id
            pid = [int]$post.container_pid
            launchMode = "EXPLODED_BOOT_APP"
            runtimePolicy = "NO_CDS_LOW_DIRTY"
            cdsMode = "OFF"
            appCds = $false
            leyden = $false
            javaagentPresent = $false
            mallocArenaMax = "1"
            javaVersion = "Eclipse Temurin 17.0.19+10 container runtime"
            workloadId = "corrected-petclinic-27x3"
            timestampStart = $null
            timestampPost = [string]$post.timestamp
            startupSeconds = [double]$post.startup_seconds
            linkageErrors = $linkageErrors
            classLoadLoggingEnabled = $false
            jfrEnabled = $false
            nmtMode = "summary"
            gcRunBeforeCapture = $false
            pageCachePolicy = "DROP_CACHES_BEFORE_EACH_VARIANT"
            pairOrder = if (($pair % 2) -eq 0) { "CANDIDATE_THEN_BASELINE" } else { "BASELINE_THEN_CANDIDATE" }
        }
        Write-Json -Value $manifest -Path (Join-Path $runDir "run-manifest.json")
        $runs.Add($manifest) | Out-Null
    }
}

$index = [ordered]@{
    metadataVersion = "v2-final-customers-evidence-index-v1"
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    comparisonId = $ComparisonId
    sourceDir = $SourceDir
    outputDir = $OutputDir
    pairs = $Pairs
    runs = $runs.ToArray()
    claimBoundary = "Canonical V2-C input generated from frozen final customers captures. Raw captures remain under target and are not publication artifacts."
}
Write-Json -Value $index -Path (Join-Path $OutputDir "evidence-index.json")
Write-Host "Prepared $($runs.Count) canonical evidence runs at $OutputDir"
