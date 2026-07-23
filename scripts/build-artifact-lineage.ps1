<#
.SYNOPSIS
    Builds (or backfills) artifact-lineage.json for a PetClinic baseline-vs-V2 adoption run.

    The campaign gate (Test-CampaignArtifactLineage) requires a lineage document that PROVES how each
    artifact was produced, rather than reconstructing provenance from the running image (review Issue
    #4). This builder derives every field from the frozen run's own evidence:

      - the scenario command ledger (*-commands.ndjson / *-command-ledger.md) for the source revision,
        the B0 build command identity, and the reused frozen-input SHAs;
      - artifacts/petclinic-customers-b0.jar and petclinic-customers-v2.jar for the artifact SHAs;
      - exploded-baseline for the B0 no-JMOA proof, the effective POM, and the dependency tree index;
      - exploded-v2/jmoa-materialization-manifest.json for the materialization manifest SHA;
      - work/.../target/jmoa-lambda-report.json (transformation) and the raw-reducer reports
        (reducer report + byte-preservation failures) for the V2 transform/reduce chain.

    It is safe to re-run: it recomputes from the on-disk evidence and overwrites the output.
#>
param(
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$SourceRevision = '',
    [string]$OutputPath = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Get-Prop { param($Object, [string]$Name) if ($null -eq $Object) { return $null }; $p = $Object.PSObject.Properties[$Name]; if ($p) { return $p.Value }; return $null }
function Get-FileSha256Upper { param([string]$Path) if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }; (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant() }
function Read-JsonFile { param([string]$Path) if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }; Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json }

# Parses the Markdown ledger's "## Asset: <Role>" blocks for the recorded SHA-256 of a reused input.
function Get-LedgerAssetSha {
    param([string]$MarkdownPath, [string]$Role)
    if (-not (Test-Path -LiteralPath $MarkdownPath -PathType Leaf)) { return '' }
    $text = Get-Content -Raw -LiteralPath $MarkdownPath
    $pattern = '## Asset:\s*' + [regex]::Escape($Role) + '\s*[\r\n]+(?:(?!##\s).*[\r\n]+)*?- SHA-256:\s*([0-9A-Fa-f]+)'
    $m = [regex]::Match($text, $pattern)
    if ($m.Success) { return $m.Groups[1].Value.ToUpperInvariant() }
    return ''
}

# Reads the ndjson ledger and returns the first record whose step matches the supplied regex.
function Find-LedgerRecord {
    param([string]$NdjsonPath, [string]$StepPattern)
    if (-not (Test-Path -LiteralPath $NdjsonPath -PathType Leaf)) { return $null }
    foreach ($line in (Get-Content -LiteralPath $NdjsonPath)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $record = $null
        try { $record = $line | ConvertFrom-Json } catch { continue }
        if ([string](Get-Prop $record 'step') -match $StepPattern) { return $record }
    }
    return $null
}

if (-not (Test-Path -LiteralPath $RunRoot -PathType Container)) { throw "Run root does not exist: $RunRoot" }
$RunRoot = (Resolve-Path -LiteralPath $RunRoot).Path
if ([string]::IsNullOrWhiteSpace($OutputPath)) { $OutputPath = Join-Path $RunRoot 'artifact-lineage.json' }

$ndjson = Get-ChildItem -LiteralPath $RunRoot -Filter '*-commands.ndjson' -File | Select-Object -First 1
$markdown = Get-ChildItem -LiteralPath $RunRoot -Filter '*-command-ledger.md' -File | Select-Object -First 1
$ndjsonPath = if ($ndjson) { $ndjson.FullName } else { '' }
$markdownPath = if ($markdown) { $markdown.FullName } else { '' }

$b0Jar = Join-Path $RunRoot 'artifacts\petclinic-customers-b0.jar'
$v2Jar = Join-Path $RunRoot 'artifacts\petclinic-customers-v2.jar'
$explodedBaseline = Join-Path $RunRoot 'exploded-baseline'
$materializationManifestPath = Join-Path $RunRoot 'exploded-v2\jmoa-materialization-manifest.json'

# Locate the customers-service target directory (holds the plugin transform/reducer reports).
$serviceTarget = Get-ChildItem -LiteralPath $RunRoot -Recurse -Directory -Filter 'target' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'spring-petclinic-customers-service' -and (Test-Path (Join-Path $_.FullName 'jmoa-lambda-report.json')) } |
    Select-Object -First 1
$transformationReportPath = if ($serviceTarget) { Join-Path $serviceTarget.FullName 'jmoa-lambda-report.json' } else { '' }
$reducedLibs = if ($serviceTarget) { Join-Path $serviceTarget.FullName 'jmoa-reduced-libs' } else { '' }
$reducerReportPath = if ($reducedLibs) { Join-Path $reducedLibs 'reducer-build-report.json' } else { '' }
$bytePreservationPath = if ($reducedLibs) { Join-Path $reducedLibs 'raw-reducer-byte-preservation-report.json' } else { '' }

# --- Source revision (shared checkout) ---
$revision = $SourceRevision
if ([string]::IsNullOrWhiteSpace($revision)) {
    $revRecord = Find-LedgerRecord -NdjsonPath $ndjsonPath -StepPattern 'Record frozen PetClinic source revision'
    if ($revRecord) { $revision = ([string](Get-Prop $revRecord 'stdout')).Trim() }
}

# --- Baseline (B0) lineage ---
$b0BuildRecord = Find-LedgerRecord -NdjsonPath $ndjsonPath -StepPattern 'Build strict B0 customers-service'
$b0BuildCommandId = ''
if ($b0BuildRecord) {
    $stdoutSha = [string](Get-Prop $b0BuildRecord 'rawStdoutSha256')
    # Older ledgers (like the frozen run) predate raw-stdout hashing; hash the recorded stdout instead.
    if ([string]::IsNullOrWhiteSpace($stdoutSha)) { $stdoutSha = Get-JmoaTextSha256 -Value ([string](Get-Prop $b0BuildRecord 'stdout')) }
    $b0BuildCommandId = 'seq{0}:{1}' -f (Get-Prop $b0BuildRecord 'sequence'), $stdoutSha
}

$effectivePom = Get-ChildItem -LiteralPath $explodedBaseline -Recurse -Filter 'pom.xml' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'META-INF[\\/]maven' } | Select-Object -First 1
$effectivePomSha = if ($effectivePom) { Get-FileSha256Upper $effectivePom.FullName } else { '' }

$classpathIdx = Get-ChildItem -LiteralPath $explodedBaseline -Recurse -Filter 'classpath.idx' -ErrorAction SilentlyContinue | Select-Object -First 1
$dependencyTreeSha = if ($classpathIdx) { Get-FileSha256Upper $classpathIdx.FullName } else { '' }

# B0 no-JMOA proof over the exploded baseline filesystem.
$jmoaClassFiles = @(Get-ChildItem -LiteralPath $explodedBaseline -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match 'BOOT-INF[\\/]classes[\\/]jmoa[\\/]' })
$jmoaDepFiles = @(Get-ChildItem -LiteralPath $explodedBaseline -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '(-jmoa\.jar$)|(^jmoa-runtime-lib.*\.jar$)' })
$jmoaReportFiles = @(Get-ChildItem -LiteralPath $explodedBaseline -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jmoa-materialization-manifest\.json$' })
$b0JmoaClassesPresent = ($jmoaClassFiles.Count -gt 0)
$b0JmoaDependencyPresent = ($jmoaDepFiles.Count -gt 0)
$b0JmoaReportsPresent = ($jmoaReportFiles.Count -gt 0)
$b0JmoaPluginExecuted = ($b0JmoaClassesPresent -or $b0JmoaDependencyPresent -or $b0JmoaReportsPresent)

$baseline = [ordered]@{
    sourceRevision        = $revision
    artifactSha256        = (Get-FileSha256Upper $b0Jar)
    artifactBytes         = if (Test-Path -LiteralPath $b0Jar) { (Get-Item -LiteralPath $b0Jar).Length } else { $null }
    buildCommandId        = $b0BuildCommandId
    effectivePomSha256    = $effectivePomSha
    dependencyTreeSha256  = $dependencyTreeSha
    jmoaPluginExecuted    = $b0JmoaPluginExecuted
    jmoaDependencyPresent = $b0JmoaDependencyPresent
    jmoaClassesPresent    = $b0JmoaClassesPresent
    jmoaReportsPresent    = $b0JmoaReportsPresent
}

# --- Candidate (V2) lineage ---
$lambdaReport = Read-JsonFile $transformationReportPath
$reducerReport = Read-JsonFile $reducerReportPath
$bytePreservation = Read-JsonFile $bytePreservationPath
$materialization = Read-JsonFile $materializationManifestPath

$rewriteSummary = if ($lambdaReport) { Get-Prop $lambdaReport 'modeCRewriteSummary' } else { $null }
$rewrittenSiteCount = if ($rewriteSummary) { [int](Get-Prop $rewriteSummary 'rewrittenSites') } else { 0 }
$rewrittenClassCount = if ($rewriteSummary) { [int](Get-Prop $rewriteSummary 'rewrittenClasses') } else { 0 }

# The aggregate reducer report carries the jar/class totals; the byte-preservation audit carries the
# reduced (audited) class count and the preservation-failure count.
$reducerInputClassCount = if ($reducerReport) { [int](Get-Prop $reducerReport 'classCount') } else { 0 }
$reducerJarCount = if ($reducerReport) { [int](Get-Prop $reducerReport 'jarCount') } else { 0 }
$reducedClassCount = if ($bytePreservation -and $null -ne (Get-Prop $bytePreservation 'auditedClassCount')) {
    [int](Get-Prop $bytePreservation 'auditedClassCount')
} else { $reducerInputClassCount }
# Preservation failures come from the byte-preservation audit; fall back to the reducer audit count.
$preservationFailures = if ($bytePreservation -and $null -ne (Get-Prop $bytePreservation 'failedAuditCount')) {
    [int](Get-Prop $bytePreservation 'failedAuditCount')
} elseif ($reducerReport -and $null -ne (Get-Prop $reducerReport 'failedAuditCount')) {
    [int](Get-Prop $reducerReport 'failedAuditCount')
} else { -1 }

$optimizedDepCount = if ($materialization) { [int](Get-Prop $materialization 'replacedJars') } else { 0 }

$candidate = [ordered]@{
    sourceRevision                = $revision
    artifactSha256                = (Get-FileSha256Upper $v2Jar)
    artifactBytes                 = if (Test-Path -LiteralPath $v2Jar) { (Get-Item -LiteralPath $v2Jar).Length } else { $null }
    profileSha256                 = (Get-LedgerAssetSha -MarkdownPath $markdownPath -Role 'Frozen PetClinic runtime profile')
    admissionSha256               = (Get-LedgerAssetSha -MarkdownPath $markdownPath -Role 'Frozen observed-site admission set')
    allowlistSha256               = (Get-LedgerAssetSha -MarkdownPath $markdownPath -Role 'Frozen additional safe SAM allowlist')
    transformationReportSha256    = (Get-FileSha256Upper $transformationReportPath)
    reducerReportSha256           = (Get-FileSha256Upper $reducerReportPath)
    bytePreservationReportSha256  = (Get-FileSha256Upper $bytePreservationPath)
    materializationManifestSha256 = (Get-FileSha256Upper $materializationManifestPath)
    rewrittenSiteCount            = $rewrittenSiteCount
    rewrittenClassCount           = $rewrittenClassCount
    reducerInputClassCount        = $reducerInputClassCount
    reducerJarCount               = $reducerJarCount
    reducedClassCount             = $reducedClassCount
    optimizedDependencyCount      = $optimizedDepCount
    preservationFailures          = $preservationFailures
}

$lineage = [ordered]@{
    schema        = 'jmoa-artifact-lineage-v1'
    generatedAt   = [DateTime]::UtcNow.ToString('o')
    runRoot       = $RunRoot
    sourceLedger  = $ndjsonPath
    baseline      = $baseline
    candidate     = $candidate
}
Write-JmoaJson $lineage $OutputPath
Write-Output "Wrote artifact lineage: $OutputPath"
$lineage | ConvertTo-Json -Depth 8
