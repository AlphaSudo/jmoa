param(
    [Parameter(Mandatory)][string]$B0V2EvidenceDir,
    [Parameter(Mandatory)][string]$V1V2EvidenceDir,
    [Parameter(Mandatory)][string]$OutputDir,
    [string]$DocsDir = "docs/v2-final",
    [string]$ArchiveName = "jmoa-v2-public-evidence-rc2.zip"
)

$ErrorActionPreference = "Stop"

$allowedRunFiles = @(
    "run-manifest.json",
    "runtime-verification.json",
    "workload-result.json",
    "smaps_rollup.txt",
    "smaps.txt",
    "memory.current",
    "memory.stat",
    "nmt-summary.txt",
    "heap-info.txt",
    "class-histogram.txt"
)
$summaryDocs = @(
    "v2-final-baseline-vs-v2-confirmation.md",
    "v2-final-baseline-vs-v2-confirmation.json",
    "v2-final-baseline-vs-v2-v2c.md",
    "v2-final-baseline-vs-v2-v2c.json",
    "v2-final-baseline-vs-v2-v2d.md",
    "v2-final-baseline-vs-v2-v2d.json",
    "v2-rc2-frozen-image-replication.md",
    "v2-rc2-frozen-image-replication.json",
    "v2-final-v1-vs-v2-confirmation.md",
    "v2-final-v1-vs-v2-confirmation.json",
    "v2-final-v1-vs-v2-v2c.md",
    "v2-final-v1-vs-v2-v2c.json",
    "v2-final-v1-vs-v2-v2d.md",
    "v2-final-v1-vs-v2-v2d.json",
    "v2-final-memory-win-matrix.md",
    "v2-final-memory-win-matrix.json",
    "v2-performance-artifact-freeze.md",
    "v2-performance-artifact-freeze.json",
    "v2-claim-matrix.md",
    "v2-claim-matrix.json",
    "v2-final-performance-acceptance.md",
    "v2-final-performance-acceptance.json"
)
$forbiddenPatterns = @(
    '(?i)[a-z]:\\(?:users|java developer|documents and settings)\\',
    '(?i)\\\\[^\\]+\\(?:users|shares?|home)\\',
    '(?i)begin (?:rsa |ec |openssh )?private key',
    '(?i)(?:password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token)\s*[:=]',
    '(?i)authorization:\s*bearer\s+',
    '(?i)jdbc:[a-z]+://[^\s]+'
)

function Write-JsonFile {
    param($Value, [string]$Path)
    $Value | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Test-PublicText {
    param([string]$Path)
    $text = Get-Content -LiteralPath $Path -Raw
    foreach ($pattern in $forbiddenPatterns) {
        if ($text -match $pattern) {
            throw "Public evidence safety scan rejected $Path because it matched $pattern"
        }
    }
}

function Copy-EvidenceSet {
    param([string]$Name, [string]$Source, [string]$Destination)

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        throw "Evidence source is missing: $Source"
    }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    $runs = @()
    foreach ($runDirectory in Get-ChildItem -LiteralPath $Source -Directory | Sort-Object Name) {
        $manifestPath = Join-Path $runDirectory.FullName "run-manifest.json"
        if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
            continue
        }
        $targetRun = Join-Path $Destination $runDirectory.Name
        New-Item -ItemType Directory -Path $targetRun -Force | Out-Null
        foreach ($fileName in $allowedRunFiles) {
            $sourceFile = Join-Path $runDirectory.FullName $fileName
            if (Test-Path -LiteralPath $sourceFile -PathType Leaf) {
                Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $targetRun $fileName) -Force
            }
        }
        foreach ($required in @("run-manifest.json", "runtime-verification.json", "workload-result.json", "smaps_rollup.txt", "smaps.txt", "memory.current", "nmt-summary.txt", "heap-info.txt", "class-histogram.txt")) {
            if (-not (Test-Path -LiteralPath (Join-Path $targetRun $required) -PathType Leaf)) {
                throw "Evidence run $($runDirectory.Name) is missing required file $required"
            }
        }
        $manifest = Get-Content -LiteralPath (Join-Path $targetRun "run-manifest.json") -Raw | ConvertFrom-Json
        $runs += [ordered]@{
            runId = $manifest.runId
            pairIndex = $manifest.pairIndex
            variant = $manifest.variant
            product = $manifest.product
            artifactSha256 = $manifest.artifactSha256
            launchMode = $manifest.launchMode
            runtimePolicy = $manifest.runtimePolicy
        }
    }
    if ($runs.Count -eq 0) {
        throw "Evidence set $Name contains no canonical run directories. Run prepare-v2-final-customers-evidence.ps1 first."
    }
    Write-JsonFile -Value ([ordered]@{
        metadataVersion = "jmoa-v2-public-evidence-index-v1"
        evidenceSet = $Name
        public = $true
        runCount = $runs.Count
        runs = $runs
        includedFilesPerRun = $allowedRunFiles
        excludedData = @("container logs", "environment dumps", "command lines", "images", "application artifacts", "private configuration")
    }) -Path (Join-Path $Destination "evidence-index.json")
}

foreach ($path in @($B0V2EvidenceDir, $V1V2EvidenceDir, $DocsDir)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required input is missing: $path"
    }
}

$OutputDir = [IO.Path]::GetFullPath($OutputDir)
$stage = Join-Path $OutputDir "public-evidence-stage"
$archive = Join-Path $OutputDir $ArchiveName
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null

Copy-EvidenceSet -Name "b0-v2" -Source $B0V2EvidenceDir -Destination (Join-Path $stage "b0-v2")
Copy-EvidenceSet -Name "v1-v2" -Source $V1V2EvidenceDir -Destination (Join-Path $stage "v1-v2")

$docsDestination = Join-Path $stage "reports"
New-Item -ItemType Directory -Path $docsDestination -Force | Out-Null
foreach ($fileName in $summaryDocs) {
    $source = Join-Path $DocsDir $fileName
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Required public summary document is missing: $source"
    }
    Copy-Item -LiteralPath $source -Destination (Join-Path $docsDestination $fileName) -Force
}

$readme = @(
    "# JMOA V2 Public Evidence Archive",
    "",
    "This archive contains sanitized run-level evidence for the final public Spring PetClinic customers-service comparisons.",
    "",
    "## Included comparisons",
    "",
    "- `b0-v2`: baseline B0 versus full V2; the five-pair replication is valid but mixed and is not a release memory-win claim.",
    "- `v1-v2`: V1 lambda/package-SAM product versus full V2; this is the confirmed, release-driving incremental comparison.",
    "",
    "## Protocol boundary",
    "",
    "EXPLODED_BOOT_APP, no CDS/AppCDS/Leyden, no runtime javaagent, `MALLOC_ARENA_MAX=1`, and the corrected 27 endpoint x 3 round workload.",
    "",
    "## Included per run",
    "",
    "Run manifest, runtime verification, workload result, smaps rollup/full capture, cgroup memory captures, NMT summary, heap info, and class histogram.",
    "",
    "## Deliberate exclusions",
    "",
    "Container logs, environment dumps, command lines, images, application artifacts, private configuration, credentials, and local machine paths are excluded. The build script rejects common sensitive patterns before packaging.",
    "",
    "The JSON/Markdown summaries under `reports/` define the claim boundary. This archive does not turn a service-specific result into a universal JVM claim or make the mixed B0-to-V2 result a win claim."
)
$readme | Set-Content -LiteralPath (Join-Path $stage "README.md") -Encoding utf8

$scannedFiles = @(Get-ChildItem -LiteralPath $stage -File -Recurse)
foreach ($file in $scannedFiles) { Test-PublicText -Path $file.FullName }

$checksums = @($scannedFiles | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($stage.Length).TrimStart('\') -replace '\\', '/'
    "{0}  {1}" -f (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant(), $relative
})
$checksums | Set-Content -LiteralPath (Join-Path $stage "SHA256SUMS.txt") -Encoding ascii
Compress-Archive -LiteralPath $stage -DestinationPath $archive -CompressionLevel Optimal -Force
$archiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
[ordered]@{
    archive = (Split-Path $archive -Leaf)
    sha256 = $archiveHash
    filesScanned = $scannedFiles.Count
    evidenceSets = @("b0-v2", "v1-v2")
    safetyScan = "PASSED"
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutputDir "public-evidence-archive-manifest.json") -Encoding utf8
Write-Host "Created sanitized public evidence archive: $archive"
Write-Host "SHA256: $archiveHash"
