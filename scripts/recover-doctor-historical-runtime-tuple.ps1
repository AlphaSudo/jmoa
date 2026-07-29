param(
    [Parameter(Mandatory)][string]$HistoricalB0Artifact,
    [Parameter(Mandatory)][string]$HistoricalV1Artifact,
    [Parameter(Mandatory)][string]$HistoricalB0Cds,
    [Parameter(Mandatory)][string]$HistoricalV1Cds,
    [Parameter(Mandatory)][string]$HistoricalCompose,
    [Parameter(Mandatory)][string]$HistoricalRunner,
    [Parameter(Mandatory)][string]$ImageInventoryJson,
    [Parameter(Mandatory)][string]$PrivateConfigRoot,
    [Parameter(Mandatory)][string]$PrivateInitSql,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$PrivateOutputDirectory,
    [int]$WorkloadRequestCount = 80,
    [int]$SettleSeconds = 20,
    [double[]]$HistoricalB0PssKb = @(336484,335792,340896),
    [double[]]$HistoricalB0PrivateDirtyKb = @(290148,289176,294320),
    [double[]]$HistoricalB0MemoryCurrentBytes = @(532750336,418836480,424136704)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
trap {
    [Console]::Error.WriteLine(
        'recover-doctor-historical-runtime-tuple.ps1 line ' +
        $_.InvocationInfo.ScriptLineNumber + ': ' + $_.Exception.Message)
    exit 1
}

foreach ($path in @($HistoricalB0Artifact,$HistoricalV1Artifact,$HistoricalB0Cds,$HistoricalV1Cds,
        $HistoricalCompose,$HistoricalRunner,$ImageInventoryJson)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file does not exist: $path" }
}
New-Item -ItemType Directory -Force -Path $OutputDirectory,$PrivateOutputDirectory | Out-Null

function Get-Identity([string]$Path) {
    $item = Get-Item -LiteralPath $Path
    [ordered]@{
        sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
        bytes = [long]$item.Length
    }
}

function Get-Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[[math]::Floor($sorted.Count / 2)] }
    return ([double]$sorted[$sorted.Count / 2 - 1] + [double]$sorted[$sorted.Count / 2]) / 2
}

function Get-RangeSummary([double[]]$Values) {
    [ordered]@{
        values = @($Values)
        min = [double](($Values | Measure-Object -Minimum).Minimum)
        median = Get-Median $Values
        max = [double](($Values | Measure-Object -Maximum).Maximum)
    }
}

function Get-DoctorRuntimePolicy([string]$ComposePath) {
    $lines = @(Get-Content -LiteralPath $ComposePath)
    $inDoctor = $false
    $inOptions = $false
    $flags = @()
    $targetServiceKey = 'doctor-' + 'management-service'
    foreach ($line in $lines) {
        if ($line -match ('^  ' + [regex]::Escape($targetServiceKey) + ':\s*$')) {
            $inDoctor = $true
            continue
        }
        if ($inDoctor -and $line -match '^  [A-Za-z0-9_-]+:\s*$') { break }
        if (-not $inDoctor) { continue }
        if ($line -match '^\s+JAVA_TOOL_OPTIONS:\s*>-') {
            $inOptions = $true
            continue
        }
        if ($inOptions) {
            if ($line -match '^\s+(-\S.*)$') { $flags += $Matches[1]; continue }
            if ($line.Trim().Length -gt 0) { $inOptions = $false }
        }
    }
    return $flags
}

function Get-ComposeImages([string]$ComposePath) {
    $roles = @()
    $service = $null
    foreach ($line in Get-Content -LiteralPath $ComposePath) {
        if ($line -match '^  ([A-Za-z0-9_-]+):\s*$') { $service = $Matches[1]; continue }
        if ($null -ne $service -and $line -match '^\s+image:\s*(.+?)\s*$') {
            $imageReference = $Matches[1].Trim('"',"'")
            $role = switch -Regex ($service) {
                'config' { 'CONFIGURATION_SUPPORT'; break }
                'discovery' { 'DISCOVERY_SUPPORT'; break }
                'postgres|database' { 'DATABASE_SUPPORT'; break }
                'doctor' { 'SERVICE_UNDER_TEST'; break }
                default { 'OTHER_SUPPORT' }
            }
            $roles += [ordered]@{ role = $role; service = $service; imageReference = $imageReference }
        }
    }
    return $roles
}

$inventory = Get-Content -Raw -LiteralPath $ImageInventoryJson | ConvertFrom-Json
$composeImages = @(Get-ComposeImages $HistoricalCompose)
$imageResolution = @()
foreach ($expected in $composeImages) {
    $match = @($inventory | Where-Object {
        $names = @()
        if ($_.PSObject.Properties.Name -contains 'Names') { $names += @($_.Names) }
        if ($_.PSObject.Properties.Name -contains 'RepoTags') { $names += @($_.RepoTags) }
        $names -contains $expected.imageReference
    } | Select-Object -First 1)
    $imageResolution += [ordered]@{
        role = $expected.role
        expectedReference = $expected.imageReference
        availableByExactReference = $match.Count -eq 1
        localImageId = if ($match.Count -eq 1) { [string]$match[0].Id } else { $null }
        localDigest = if ($match.Count -eq 1) { [string]$match[0].Digest } else { $null }
    }
}

$reconstructedCandidates = @(
    $inventory | Where-Object {
        $_.PSObject.Properties.Name -contains 'Names' -and
        (@($_.Names) -join ' ') -match 'jmoa-recovery-(doctor|config|discovery)'
    } |
        ForEach-Object {
            [ordered]@{
                logicalId = 'RECONSTRUCTED_IMAGE_' + ([string]$_.Id).Substring(0,12).ToUpperInvariant()
                imageId = [string]$_.Id
                digest = [string]$_.Digest
                names = @($_.Names)
                size = [long]$_.Size
            }
        }
)

$runtimeFlags = @(Get-DoctorRuntimePolicy $HistoricalCompose)
$private = [ordered]@{
    schemaVersion = 'jmoa-doctor-historical-runtime-tuple-private-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    exactInputs = [ordered]@{
        historicalB0Artifact = [ordered]@{ path = (Resolve-Path $HistoricalB0Artifact).Path; identity = Get-Identity $HistoricalB0Artifact }
        historicalV1Artifact = [ordered]@{ path = (Resolve-Path $HistoricalV1Artifact).Path; identity = Get-Identity $HistoricalV1Artifact }
        historicalB0Cds = [ordered]@{ path = (Resolve-Path $HistoricalB0Cds).Path; identity = Get-Identity $HistoricalB0Cds }
        historicalV1Cds = [ordered]@{ path = (Resolve-Path $HistoricalV1Cds).Path; identity = Get-Identity $HistoricalV1Cds }
        compose = [ordered]@{ path = (Resolve-Path $HistoricalCompose).Path; identity = Get-Identity $HistoricalCompose }
        runner = [ordered]@{ path = (Resolve-Path $HistoricalRunner).Path; identity = Get-Identity $HistoricalRunner }
        privateConfigRoot = [ordered]@{
            path = [IO.Path]::GetFullPath($PrivateConfigRoot)
            exists = Test-Path -LiteralPath $PrivateConfigRoot -PathType Container
        }
        privateInitSql = [ordered]@{
            path = [IO.Path]::GetFullPath($PrivateInitSql)
            exists = Test-Path -LiteralPath $PrivateInitSql -PathType Leaf
            identity = if (Test-Path -LiteralPath $PrivateInitSql -PathType Leaf) { Get-Identity $PrivateInitSql } else { $null }
        }
    }
    exactImageResolution = $imageResolution
    reconstructedCandidates = $reconstructedCandidates
    runtimePolicy = [ordered]@{
        flags = $runtimeFlags
        settleSeconds = $SettleSeconds
        workloadRequestCount = $WorkloadRequestCount
        pairOrder = 'B0_THEN_V1'
        freshStackPerArm = $true
        captureSet = @('smaps_rollup','memory.current','startup','workload')
    }
}
$privatePath = Join-Path $PrivateOutputDirectory 'doctor-historical-runtime-tuple.private.json'
$private | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $privatePath -Encoding UTF8

$exactImagesAvailable = @($imageResolution | Where-Object { -not $_.availableByExactReference }).Count -eq 0
$configAvailable = [bool]$private.exactInputs.privateConfigRoot.exists
$sqlAvailable = [bool]$private.exactInputs.privateInitSql.exists
$public = [ordered]@{
    schemaVersion = 'jmoa-doctor-historical-runtime-tuple-v1'
    generatedAt = $private.generatedAt
    service = 'doctor-service'
    exactArtifacts = [ordered]@{
        b0 = $private.exactInputs.historicalB0Artifact.identity
        v1 = $private.exactInputs.historicalV1Artifact.identity
        b0Cds = $private.exactInputs.historicalB0Cds.identity
        v1Cds = $private.exactInputs.historicalV1Cds.identity
    }
    historicalContract = [ordered]@{
        sourceComposeSha256 = $private.exactInputs.compose.identity.sha256
        sourceRunnerSha256 = $private.exactInputs.runner.identity.sha256
        runtimeFlags = $runtimeFlags
        settleSeconds = $SettleSeconds
        workloadRequestCount = $WorkloadRequestCount
        pairOrder = 'B0_THEN_V1'
        freshStackPerArm = $true
        cdsPolicy = 'ARTIFACT_SPECIFIC_APPLICATION_CDS'
    }
    historicalB0Envelope = [ordered]@{
        pssKb = Get-RangeSummary $HistoricalB0PssKb
        privateDirtyKb = Get-RangeSummary $HistoricalB0PrivateDirtyKb
        memoryCurrentBytes = Get-RangeSummary $HistoricalB0MemoryCurrentBytes
    }
    localReadiness = [ordered]@{
        exactHistoricalImageReferencesAvailable = $exactImagesAvailable
        reconstructedImageCandidateCount = $reconstructedCandidates.Count
        privateConfigAvailable = $configAvailable
        databaseInitAvailable = $sqlAvailable
        decision = if ($exactImagesAvailable -and $configAvailable -and $sqlAvailable) {
            'READY_FOR_EXACT_HISTORICAL_SCREEN'
        } elseif ($reconstructedCandidates.Count -gt 0 -and $configAvailable -and $sqlAvailable) {
            'READY_FOR_RECONSTRUCTED_SCREEN_WITH_EXPLICIT_PROVENANCE'
        } else {
            'BLOCKED_MISSING_RUNTIME_INPUT'
        }
    }
    privacyBoundary = 'Private paths, image references, service configuration, endpoint names, and credentials are retained only in the private tuple.'
}
$jsonPath = Join-Path $OutputDirectory 'doctor-historical-runtime-tuple.json'
$mdPath = Join-Path $OutputDirectory 'doctor-historical-runtime-tuple.md'
$public | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
@"
# Doctor Historical Runtime Tuple

- Readiness: **$($public.localReadiness.decision)**
- Exact historical image references available: **$exactImagesAvailable**
- Reconstructed image candidates: **$($reconstructedCandidates.Count)**
- Private configuration available: **$configAvailable**
- Database initialization available: **$sqlAvailable**
- Runtime policy: **artifact-specific application CDS**
- Settle: **$SettleSeconds seconds**
- Workload requests: **$WorkloadRequestCount**
- Pair order: **B0 then V1**

The B0/V1 JARs and their CDS archives are exact recovered historical artifacts. Runtime images are classified separately; reconstructed images are never described as original historical images. The absolute B0 screen must pass the recovered historical envelope before a B0/V1 diagnostic pair is authorized.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Doctor tuple: $($public.localReadiness.decision)"
Write-Host "Public: $jsonPath"
Write-Host "Private: $privatePath"
