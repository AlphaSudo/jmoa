param(
    [Parameter(Mandatory)][string]$BaselineDirectory,
    [Parameter(Mandatory)][string]$CandidateDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$Comparison = 'V1_SECOND_MINUS_B0_FIRST',
    [string]$OutputBaseName = 'doctor-reconstructed-b0-v1'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Read-Json([string]$Path) {
    Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Read-KeyValues([string]$Path) {
    $values = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^:\s]+):?\s+(-?\d+)') {
            $values[$Matches[1]] = [long]$Matches[2]
        }
    }
    $values
}

function Get-Value($Map, [string]$Name) {
    if ($Map.Contains($Name)) { return [long]$Map[$Name] }
    return 0L
}

function Convert-Hex([string]$Value) {
    [Convert]::ToInt64($Value, 16)
}

function Read-Heap([string]$Path) {
    $text = Get-Content -Raw -LiteralPath $Path
    $generations = [Collections.Generic.List[object]]::new()
    foreach ($match in [regex]::Matches(
        $text,
        '(?m)^\s*(DefNew|Tenured|def new generation|tenured generation)\s+total\s+(\d+)K,\s+used\s+(\d+)K\s+\[0x([0-9a-fA-F]+),\s*0x[0-9a-fA-F]+,\s*0x([0-9a-fA-F]+)\)'
    )) {
        $generations.Add([pscustomobject][ordered]@{
            name = $match.Groups[1].Value
            committedKb = [long]$match.Groups[2].Value
            usedKb = [long]$match.Groups[3].Value
            start = Convert-Hex $match.Groups[4].Value
            end = Convert-Hex $match.Groups[5].Value
        })
    }
    $usedKb = 0L
    $committedKb = 0L
    $youngUsedKb = 0L
    $oldUsedKb = 0L
    foreach ($generation in $generations) {
        $usedKb += [long]$generation.usedKb
        $committedKb += [long]$generation.committedKb
        if ([string]$generation.name -match '(?i)new|DefNew') { $youngUsedKb += [long]$generation.usedKb }
        if ([string]$generation.name -match '(?i)Tenured') { $oldUsedKb += [long]$generation.usedKb }
    }
    [ordered]@{
        usedKb = $usedKb
        committedKb = $committedKb
        youngUsedKb = $youngUsedKb
        oldUsedKb = $oldUsedKb
        ranges = @($generations | ForEach-Object {
            [pscustomobject]@{ start = [long]$_.start; end = [long]$_.end }
        })
    }
}

function Test-RangeOverlap([long]$Start, [long]$End, [object[]]$Ranges) {
    foreach ($range in $Ranges) {
        if ($Start -lt [long]$range.end -and $End -gt [long]$range.start) { return $true }
    }
    return $false
}

function Get-MappingCategory([long]$Start, [long]$End, [string]$Permissions, [string]$Name, [object[]]$HeapRanges) {
    if (Test-RangeOverlap $Start $End $HeapRanges) { return 'JAVA_HEAP' }
    if ($Name -match '^\[stack') { return 'THREAD_STACK' }
    if ($Name -match '(?i)libjvm\.(so|dll|dylib)$') { return 'LIBJVM' }
    if ($Name -match '(?i)\.jsa$') { return 'JDK_CDS_IMAGE' }
    if ($Name -match '(?i)\.(jar|zip)$') { return 'JAR_ZIP' }
    if ($Name -match '(?i)\.so(?:\.\d+)*$') { return 'NATIVE_LIBRARY' }
    if ($Name -match '(?i)/(modules|classes\.jimage)$') { return 'JDK_IMAGE' }
    if ($Name -match '^\[(vdso|vvar|vsyscall)\]$') { return 'SPECIAL_MAPPING' }
    if ([string]::IsNullOrWhiteSpace($Name) -and $Permissions.Contains('x')) { return 'ANONYMOUS_EXECUTABLE' }
    if ([string]::IsNullOrWhiteSpace($Name) -and $Permissions.StartsWith('rw')) { return 'ANONYMOUS_RW_OUTSIDE_HEAP' }
    if ([string]::IsNullOrWhiteSpace($Name)) { return 'ANONYMOUS_OTHER' }
    if ($Name.StartsWith('/')) { return 'MAPPED_FILE_OTHER' }
    return 'SPECIAL_OR_OTHER'
}

function Read-Smaps([string]$Path, [object[]]$HeapRanges) {
    $categories = [ordered]@{}
    $mappings = [Collections.Generic.List[object]]::new()
    $current = $null

    function Complete-Mapping($Mapping) {
        if ($null -eq $Mapping) { return }
        if (-not $categories.Contains($Mapping.category)) {
            $categories[$Mapping.category] = [ordered]@{
                mappingCount = 0; rssKb = 0L; pssKb = 0L; privateDirtyKb = 0L
                privateCleanKb = 0L; sharedCleanKb = 0L; anonymousKb = 0L; anonymousHugePagesKb = 0L
            }
        }
        $bucket = $categories[$Mapping.category]
        $bucket.mappingCount++
        foreach ($metric in @('rssKb','pssKb','privateDirtyKb','privateCleanKb','sharedCleanKb','anonymousKb','anonymousHugePagesKb')) {
            $bucket[$metric] += [long]$Mapping[$metric]
        }
        $mappings.Add($Mapping)
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^([0-9a-fA-F]+)-([0-9a-fA-F]+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s*(.*)$') {
            Complete-Mapping $current
            $start = Convert-Hex $Matches[1]
            $end = Convert-Hex $Matches[2]
            $permissions = $Matches[3]
            $name = $Matches[4].Trim()
            $current = [ordered]@{
                start = $start; end = $end; permissions = $permissions; name = $name
                category = Get-MappingCategory $start $end $permissions $name $HeapRanges
                rssKb = 0L; pssKb = 0L; privateDirtyKb = 0L; privateCleanKb = 0L
                sharedCleanKb = 0L; anonymousKb = 0L; anonymousHugePagesKb = 0L
            }
            continue
        }
        if ($null -eq $current) { continue }
        $fieldMap = @{
            Rss = 'rssKb'; Pss = 'pssKb'; Private_Dirty = 'privateDirtyKb'
            Private_Clean = 'privateCleanKb'; Shared_Clean = 'sharedCleanKb'
            Anonymous = 'anonymousKb'; AnonHugePages = 'anonymousHugePagesKb'
        }
        if ($line -match '^([A-Za-z_]+):\s+(\d+)\s+kB' -and $fieldMap.ContainsKey($Matches[1])) {
            $current[$fieldMap[$Matches[1]]] = [long]$Matches[2]
        }
    }
    Complete-Mapping $current
    [ordered]@{ categories = $categories; mappings = $mappings.ToArray() }
}

function Read-Nmt([string]$Path) {
    $text = Get-Content -Raw -LiteralPath $Path
    $result = [ordered]@{ categories = [ordered]@{}; loadedClasses = $null; threads = $null; mallocKb = $null }
    if ($text -match 'Total:\s+reserved=(\d+)KB,\s+committed=(\d+)KB') {
        $result.reservedKb = [long]$Matches[1]
        $result.committedKb = [long]$Matches[2]
    }
    if ($text -match '(?m)^\s*malloc:\s+(\d+)KB') { $result.mallocKb = [long]$Matches[1] }
    foreach ($match in [regex]::Matches($text, '(?m)^-\s+(.+?)\s+\(reserved=(\d+)KB,\s+committed=(\d+)KB\)')) {
        $name = ($match.Groups[1].Value.Trim() -replace '\s+', '_').ToUpperInvariant()
        $result.categories[$name] = [ordered]@{
            reservedKb = [long]$match.Groups[2].Value
            committedKb = [long]$match.Groups[3].Value
        }
    }
    if ($text -match '\(classes #(\d+)\)') { $result.loadedClasses = [long]$Matches[1] }
    if ($text -match '\(threads? #(\d+)\)') { $result.threads = [long]$Matches[1] }
    $result
}

function Read-Metaspace([string]$Path) {
    $text = Get-Content -Raw -LiteralPath $Path
    $result = [ordered]@{}
    if ($text -match 'Metaspace\s+used\s+(\d+)K,\s+committed\s+(\d+)K') {
        $result.usedKb = [long]$Matches[1]; $result.committedKb = [long]$Matches[2]
    }
    if ($text -match 'class space\s+used\s+(\d+)K,\s+committed\s+(\d+)K') {
        $result.classSpaceUsedKb = [long]$Matches[1]; $result.classSpaceCommittedKb = [long]$Matches[2]
    }
    if ($text -match 'Total Usage -\s+(\d+)\s+loaders,\s+(\d+)\s+classes') {
        $result.classLoaders = [long]$Matches[1]; $result.classes = [long]$Matches[2]
    }
    $result
}

function Read-ClassLoaderStats([string]$Path) {
    $text = Get-Content -Raw -LiteralPath $Path
    $result = [ordered]@{ loaderRows = 0L; classes = $null; chunkBytes = $null; blockBytes = $null; hiddenClasses = $null }
    $result.loaderRows = @($text -split '\r?\n' | Where-Object { $_ -match '^0x[0-9a-fA-F]+' }).Count
    if ($text -match '(?m)^\s+(\d+)\s+\d+\s+\d+\s+\+ hidden classes') { $result.hiddenClasses = [long]$Matches[1] }
    if ($text -match '(?m)^Total =\s+\d+\s+(\d+)\s+(\d+)\s+(\d+)') {
        $result.classes = [long]$Matches[1]; $result.chunkBytes = [long]$Matches[2]; $result.blockBytes = [long]$Matches[3]
    }
    $result
}

function Read-Histogram([string]$Path) {
    $instances = 0L; $bytes = 0L; $rows = 0L
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*\d+:\s+(\d+)\s+(\d+)\s+\S+') {
            $instances += [long]$Matches[1]; $bytes += [long]$Matches[2]; $rows++
        }
    }
    [ordered]@{ instances = $instances; bytes = $bytes; classRows = $rows }
}

function Read-Run([string]$Directory) {
    foreach ($name in @(
        'run-manifest.json','workload-result.json','smaps_rollup.txt','smaps.txt','memory.current',
        'memory.stat','nmt-summary.txt','heap-info.txt','metaspace.txt','classloader-stats.txt','class-histogram.txt'
    )) {
        if (-not (Test-Path -LiteralPath (Join-Path $Directory $name) -PathType Leaf)) {
            throw "Required Doctor pair evidence is missing: $Directory\\$name"
        }
    }
    $manifest = Read-Json (Join-Path $Directory 'run-manifest.json')
    $workload = Read-Json (Join-Path $Directory 'workload-result.json')
    $heap = Read-Heap (Join-Path $Directory 'heap-info.txt')
    $smaps = Read-Smaps (Join-Path $Directory 'smaps.txt') $heap.ranges
    $rollup = Read-KeyValues (Join-Path $Directory 'smaps_rollup.txt')
    $memoryStat = Read-KeyValues (Join-Path $Directory 'memory.stat')
    $nmt = Read-Nmt (Join-Path $Directory 'nmt-summary.txt')
    $metaspace = Read-Metaspace (Join-Path $Directory 'metaspace.txt')
    $classloaders = Read-ClassLoaderStats (Join-Path $Directory 'classloader-stats.txt')
    $histogram = Read-Histogram (Join-Path $Directory 'class-histogram.txt')
    $start = [datetime]$manifest.timestampStart
    $capture = [datetime]$manifest.timestampPost
    $workloadStart = [datetime]$workload.startedUtc
    $workloadEnd = [datetime]$workload.endedUtc
    [ordered]@{
        variant = [string]$manifest.variant
        artifactSha256 = [string]$manifest.artifactSha256
        rollup = $rollup
        memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $Directory 'memory.current')).Trim()
        memoryStat = $memoryStat
        heap = $heap
        smaps = $smaps
        nmt = $nmt
        metaspace = $metaspace
        classloaders = $classloaders
        histogram = $histogram
        timing = [ordered]@{
            containerStartUtc = $start.ToUniversalTime().ToString('o')
            firstHealthUtc = $start.AddMilliseconds([double]$manifest.startupMillis).ToUniversalTime().ToString('o')
            workloadStartUtc = $workloadStart.ToUniversalTime().ToString('o')
            workloadEndUtc = $workloadEnd.ToUniversalTime().ToString('o')
            claimCaptureUtc = $capture.ToUniversalTime().ToString('o')
            startupToHealthSeconds = [math]::Round([double]$manifest.startupMillis / 1000, 3)
            healthToWorkloadSeconds = [math]::Round(($workloadStart - $start.AddMilliseconds([double]$manifest.startupMillis)).TotalSeconds, 3)
            workloadSeconds = [math]::Round(($workloadEnd - $workloadStart).TotalSeconds, 3)
            postWorkloadSettleSeconds = [math]::Round(($capture - $workloadEnd).TotalSeconds, 3)
            jvmAgeAtCaptureSeconds = [math]::Round(($capture - $start).TotalSeconds, 3)
            gcCount = $null
            safepointCount = $null
            compilationActivity = 'NMT_COMPILER_AND_CODE_COMMITTED_ONLY'
        }
    }
}

function Delta($Candidate, $Baseline) {
    if ($null -eq $Candidate -or $null -eq $Baseline) { return $null }
    [long]$Candidate - [long]$Baseline
}

function Compare-Map($Baseline, $Candidate, [string[]]$Keys) {
    $result = [ordered]@{}
    foreach ($key in $Keys) {
        $result[$key] = [ordered]@{
            baseline = Get-Value $Baseline $key
            candidate = Get-Value $Candidate $key
            delta = (Get-Value $Candidate $key) - (Get-Value $Baseline $key)
        }
    }
    $result
}

function Get-CategoryDelta($Categories, [string]$Category, [string]$Metric) {
    if (-not $Categories.Contains($Category)) { return 0L }
    return [long]$Categories[$Category][$Metric].delta
}

$baseline = Read-Run $BaselineDirectory
$candidate = Read-Run $CandidateDirectory
$categoryNames = @($baseline.smaps.categories.Keys + $candidate.smaps.categories.Keys | Sort-Object -Unique)
$mappingCategories = [ordered]@{}
foreach ($category in $categoryNames) {
    $b = if ($baseline.smaps.categories.Contains($category)) { $baseline.smaps.categories[$category] } else { [ordered]@{} }
    $c = if ($candidate.smaps.categories.Contains($category)) { $candidate.smaps.categories[$category] } else { [ordered]@{} }
    $mappingCategories[$category] = Compare-Map $b $c @(
        'mappingCount','rssKb','pssKb','privateDirtyKb','privateCleanKb','sharedCleanKb','anonymousKb','anonymousHugePagesKb'
    )
}

$nmtNames = @($baseline.nmt.categories.Keys + $candidate.nmt.categories.Keys | Sort-Object -Unique)
$nmtCategories = [ordered]@{}
foreach ($category in $nmtNames) {
    $b = if ($baseline.nmt.categories.Contains($category)) { $baseline.nmt.categories[$category] } else { [ordered]@{} }
    $c = if ($candidate.nmt.categories.Contains($category)) { $candidate.nmt.categories[$category] } else { [ordered]@{} }
    $nmtCategories[$category] = Compare-Map $b $c @('reservedKb','committedKb')
}

$cgroupKeys = @(
    'anon','file','shmem','kernel','kernel_stack','pagetables','slab','sock',
    'active_file','inactive_file','pgfault','pgmajfault'
)
$jvmAgeDelta = [double]$candidate.timing.jvmAgeAtCaptureSeconds - [double]$baseline.timing.jvmAgeAtCaptureSeconds
$timingClass = if ([math]::Abs($jvmAgeDelta) -gt 2) { 'TIMING_CONFOUNDED' } else { 'AGE_MATCHED_WITHIN_2_SECONDS' }
$pssDelta = (Get-Value $candidate.rollup 'Pss') - (Get-Value $baseline.rollup 'Pss')
$dirtyDelta = (Get-Value $candidate.rollup 'Private_Dirty') - (Get-Value $baseline.rollup 'Private_Dirty')
$nmtDelta = Delta $candidate.nmt.committedKb $baseline.nmt.committedKb
$heapPssDelta = Get-CategoryDelta $mappingCategories 'JAVA_HEAP' 'pssKb'
$anonDelta = Get-CategoryDelta $mappingCategories 'ANONYMOUS_RW_OUTSIDE_HEAP' 'pssKb'
$primary = if ([math]::Abs($heapPssDelta) -ge [math]::Abs($anonDelta) -and [math]::Abs($heapPssDelta) -ge 1024) {
    'JAVA_HEAP_RESIDENCY'
} elseif ([math]::Abs($anonDelta) -ge 1024) {
    'ANONYMOUS_RW_OUTSIDE_HEAP'
} elseif ([math]::Abs((Get-CategoryDelta $mappingCategories 'THREAD_STACK' 'pssKb')) -ge 1024) {
    'THREAD_STACK'
} else {
    'MIXED_SMALL_MAPPING_DELTAS'
}
$classification = if ([math]::Abs($nmtDelta) -ge [math]::Abs($pssDelta) * 0.6) {
    'NMT_VISIBLE'
} elseif ($primary -eq 'ANONYMOUS_RW_OUTSIDE_HEAP') {
    'NMT_PARTIAL_ANONYMOUS_RW_OUTSIDE_HEAP'
} elseif ($primary -eq 'JAVA_HEAP_RESIDENCY') {
    'NMT_PARTIAL_HEAP_RESIDENCY'
} else {
    'NMT_PARTIAL_MIXED_MAPPINGS'
}

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-reconstructed-pair-attribution-v1'
    status = 'DIAGNOSTIC_EXISTING_PAIR'
    comparison = $Comparison
    semanticGate = 'SEMANTICALLY_EQUIVALENT_VOLATILE_FIELDS'
    headline = [ordered]@{
        pssDeltaKb = $pssDelta
        privateDirtyDeltaKb = $dirtyDelta
        memoryCurrentDeltaBytes = $candidate.memoryCurrentBytes - $baseline.memoryCurrentBytes
        nmtCommittedDeltaKb = $nmtDelta
        reconciliation = $classification
        primaryMappingMovement = $primary
    }
    smapsCategories = $mappingCategories
    cgroup = Compare-Map $baseline.memoryStat $candidate.memoryStat $cgroupKeys
    nmtCategories = $nmtCategories
    jvm = [ordered]@{
        heapUsedKb = [ordered]@{ baseline=$baseline.heap.usedKb; candidate=$candidate.heap.usedKb; delta=Delta $candidate.heap.usedKb $baseline.heap.usedKb }
        heapCommittedKb = [ordered]@{ baseline=$baseline.heap.committedKb; candidate=$candidate.heap.committedKb; delta=Delta $candidate.heap.committedKb $baseline.heap.committedKb }
        youngUsedKb = [ordered]@{ baseline=$baseline.heap.youngUsedKb; candidate=$candidate.heap.youngUsedKb; delta=Delta $candidate.heap.youngUsedKb $baseline.heap.youngUsedKb }
        oldUsedKb = [ordered]@{ baseline=$baseline.heap.oldUsedKb; candidate=$candidate.heap.oldUsedKb; delta=Delta $candidate.heap.oldUsedKb $baseline.heap.oldUsedKb }
        loadedClasses = [ordered]@{ baseline=$baseline.nmt.loadedClasses; candidate=$candidate.nmt.loadedClasses; delta=Delta $candidate.nmt.loadedClasses $baseline.nmt.loadedClasses }
        threads = [ordered]@{ baseline=$baseline.nmt.threads; candidate=$candidate.nmt.threads; delta=Delta $candidate.nmt.threads $baseline.nmt.threads }
        metaspaceUsedKb = [ordered]@{ baseline=$baseline.metaspace.usedKb; candidate=$candidate.metaspace.usedKb; delta=Delta $candidate.metaspace.usedKb $baseline.metaspace.usedKb }
        metaspaceCommittedKb = [ordered]@{ baseline=$baseline.metaspace.committedKb; candidate=$candidate.metaspace.committedKb; delta=Delta $candidate.metaspace.committedKb $baseline.metaspace.committedKb }
        classLoaders = [ordered]@{ baseline=$baseline.metaspace.classLoaders; candidate=$candidate.metaspace.classLoaders; delta=Delta $candidate.metaspace.classLoaders $baseline.metaspace.classLoaders }
        hiddenClasses = [ordered]@{ baseline=$baseline.classloaders.hiddenClasses; candidate=$candidate.classloaders.hiddenClasses; delta=Delta $candidate.classloaders.hiddenClasses $baseline.classloaders.hiddenClasses }
        histogramBytes = [ordered]@{ baseline=$baseline.histogram.bytes; candidate=$candidate.histogram.bytes; delta=Delta $candidate.histogram.bytes $baseline.histogram.bytes }
        histogramInstances = [ordered]@{ baseline=$baseline.histogram.instances; candidate=$candidate.histogram.instances; delta=Delta $candidate.histogram.instances $baseline.histogram.instances }
    }
    timing = [ordered]@{
        baseline = $baseline.timing
        candidate = $candidate.timing
        jvmAgeDeltaSeconds = [math]::Round($jvmAgeDelta, 3)
        classification = $timingClass
        unavailable = @('gcCount','safepointCount','exactCompilationCount')
    }
    boundaries = @(
        'The B0-first/V1-second order remains confounded until the one reversed diagnostic is evaluated.',
        'GC count, safepoint count, and exact compilation count were not captured and are not inferred.',
        'The class histogram is a post-claim perturbing diagnostic; it supports attribution but not claim medians.',
        'Exact historical application artifacts ran in a reconstructed support environment on the current JDK.'
    )
}

New-JmoaDirectory $OutputDirectory
Write-JmoaJson $report (Join-Path $OutputDirectory "$OutputBaseName-attribution.json")
$rows = foreach ($name in $mappingCategories.Keys) {
    $value = $mappingCategories[$name]
    "| $name | $($value.pssKb.baseline) | $($value.pssKb.candidate) | $($value.pssKb.delta) | $($value.privateDirtyKb.delta) | $($value.anonymousKb.delta) |"
}
$markdown = @"
# Doctor Reconstructed B0/V1 Attribution

- Status: **DIAGNOSTIC_EXISTING_PAIR**
- Comparison: **$Comparison**
- Semantic gate: **SEMANTICALLY_EQUIVALENT_VOLATILE_FIELDS**
- PSS: **$pssDelta KB**
- Private Dirty: **$dirtyDelta KB**
- memory.current: **$($report.headline.memoryCurrentDeltaBytes) bytes**
- NMT committed: **$nmtDelta KB**
- Reconciliation: **$classification**
- Primary mapping movement: **$primary**

| Mapping category | B0 PSS KB | V1 PSS KB | Delta PSS KB | Delta Private Dirty KB | Delta Anonymous KB |
|---|---:|---:|---:|---:|---:|
$($rows -join "`n")

## JVM

- Heap PSS delta: **$heapPssDelta KB**
- Heap used delta: **$($report.jvm.heapUsedKb.delta) KB**
- Heap committed delta: **$($report.jvm.heapCommittedKb.delta) KB**
- Loaded classes delta: **$($report.jvm.loadedClasses.delta)**
- Hidden classes delta: **$($report.jvm.hiddenClasses.delta)**
- Class loaders delta: **$($report.jvm.classLoaders.delta)**
- Metaspace committed delta: **$($report.jvm.metaspaceCommittedKb.delta) KB**
- Threads delta: **$($report.jvm.threads.delta)**
- Histogram bytes delta: **$($report.jvm.histogramBytes.delta)**

## Timing

- B0 JVM age at capture: **$($baseline.timing.jvmAgeAtCaptureSeconds) s**
- V1 JVM age at capture: **$($candidate.timing.jvmAgeAtCaptureSeconds) s**
- V1 - B0 age: **$($report.timing.jvmAgeDeltaSeconds) s**
- Classification: **$timingClass**

GC count, safepoint count, and exact compilation count were not captured. NMT
Compiler and Code categories are reported in JSON without pretending they are
event counters.

## Claim Boundary

This is one B0-first/V1-second diagnostic in a reconstructed support environment.
It does not prove a V1 regression. The result remains
`DOCTOR_HISTORICAL_V1_DIRECTION_NOT_REPRODUCED_IN_SINGLE_ORDER` until the one
reversed diagnostic is classified.
"@
Write-JmoaText $markdown (Join-Path $OutputDirectory "$OutputBaseName-attribution.md")
Write-JmoaJson $report.timing (Join-Path $OutputDirectory "$OutputBaseName-timing.json")
Write-JmoaText @"
# Doctor Reconstructed Pair Timing

- B0 startup to health: **$($baseline.timing.startupToHealthSeconds) s**
- V1 startup to health: **$($candidate.timing.startupToHealthSeconds) s**
- B0 workload: **$($baseline.timing.workloadSeconds) s**
- V1 workload: **$($candidate.timing.workloadSeconds) s**
- B0 settle: **$($baseline.timing.postWorkloadSettleSeconds) s**
- V1 settle: **$($candidate.timing.postWorkloadSettleSeconds) s**
- B0 JVM age: **$($baseline.timing.jvmAgeAtCaptureSeconds) s**
- V1 JVM age: **$($candidate.timing.jvmAgeAtCaptureSeconds) s**
- Age delta: **$($report.timing.jvmAgeDeltaSeconds) s**
- Classification: **$timingClass**

GC count, safepoint count, and exact compilation count are unavailable from this
capture. They are explicitly null in JSON.
"@ (Join-Path $OutputDirectory "$OutputBaseName-timing.md")

Write-Host "Doctor existing-pair attribution: $classification / $primary / $timingClass"
