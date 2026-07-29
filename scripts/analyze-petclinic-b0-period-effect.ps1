<#
.SYNOPSIS
    Attributes the PETCLINIC_TARGET_ONLY_V1 B0 first/second execution shift.

.DESCRIPTION
    Reads the four completed B0 control arms. It never launches a service and
    never modifies raw evidence. Output is a sanitized JSON/Markdown report.
#>
param(
    [Parameter(Mandatory)][string]$CaptureRoot,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Get-KeyValueFile {
    param([Parameter(Mandatory)][string]$Path)
    $result = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^:\s]+):?\s+(-?\d+)') {
            $result[$matches[1]] = [long]$matches[2]
        }
    }
    $result
}

function Get-NmtMetrics {
    param([Parameter(Mandatory)][string]$Path)
    $text = Get-Content -Raw -LiteralPath $Path
    $result = [ordered]@{
        totalCommittedKb = $null
        javaHeapCommittedKb = $null
        classCommittedKb = $null
        metaspaceCommittedKb = $null
        codeCommittedKb = $null
        loadedClasses = $null
        threads = $null
    }
    if ($text -match 'Total:\s+reserved=\d+KB,\s+committed=(\d+)KB') {
        $result.totalCommittedKb = [long]$matches[1]
    }
    foreach ($entry in @(
        @{ name = 'javaHeapCommittedKb'; pattern = '-\s+Java Heap \(reserved=\d+KB, committed=(\d+)KB\)' },
        @{ name = 'classCommittedKb'; pattern = '-\s+Class \(reserved=\d+KB, committed=(\d+)KB\)' },
        @{ name = 'metaspaceCommittedKb'; pattern = '-\s+Metaspace \(reserved=\d+KB, committed=(\d+)KB\)' },
        @{ name = 'codeCommittedKb'; pattern = '-\s+Code \(reserved=\d+KB, committed=(\d+)KB\)' }
    )) {
        if ($text -match $entry.pattern) { $result[$entry.name] = [long]$matches[1] }
    }
    if ($text -match '\(classes #(\d+)\)') { $result.loadedClasses = [long]$matches[1] }
    if ($text -match '\(thread #(\d+)\)') { $result.threads = [long]$matches[1] }
    $result
}

function Get-HeapMetrics {
    param([Parameter(Mandatory)][string]$Path)
    $text = Get-Content -Raw -LiteralPath $Path
    $newTotal = 0L
    $newUsed = 0L
    $oldTotal = 0L
    $oldUsed = 0L
    $metaUsed = $null
    $metaCommitted = $null
    $classUsed = $null
    $classCommitted = $null
    if ($text -match 'def new generation\s+total\s+(\d+)K,\s+used\s+(\d+)K') {
        $newTotal = [long]$matches[1]
        $newUsed = [long]$matches[2]
    }
    if ($text -match 'tenured generation\s+total\s+(\d+)K,\s+used\s+(\d+)K') {
        $oldTotal = [long]$matches[1]
        $oldUsed = [long]$matches[2]
    }
    if ($text -match 'Metaspace\s+used\s+(\d+)K,\s+committed\s+(\d+)K') {
        $metaUsed = [long]$matches[1]
        $metaCommitted = [long]$matches[2]
    }
    if ($text -match 'class space\s+used\s+(\d+)K,\s+committed\s+(\d+)K') {
        $classUsed = [long]$matches[1]
        $classCommitted = [long]$matches[2]
    }
    [ordered]@{
        usedKb = $newUsed + $oldUsed
        committedKb = $newTotal + $oldTotal
        youngUsedKb = $newUsed
        youngCommittedKb = $newTotal
        oldUsedKb = $oldUsed
        oldCommittedKb = $oldTotal
        metaspaceUsedKb = $metaUsed
        metaspaceCommittedKb = $metaCommitted
        classSpaceUsedKb = $classUsed
        classSpaceCommittedKb = $classCommitted
    }
}

function Get-HistogramMetrics {
    param([Parameter(Mandatory)][string]$Path)
    $line = Get-Content -LiteralPath $Path -Tail 16 |
        Where-Object { $_ -match '^Total\s+(\d+)\s+(\d+)' } |
        Select-Object -Last 1
    if ($null -eq $line -or $line -notmatch '^Total\s+(\d+)\s+(\d+)') {
        return [ordered]@{ instances = $null; bytes = $null }
    }
    [ordered]@{ instances = [long]$matches[1]; bytes = [long]$matches[2] }
}

function Get-MetaspaceLoaderMetrics {
    param([Parameter(Mandatory)][string]$Directory)
    $metaText = Get-Content -Raw -LiteralPath (Join-Path $Directory 'metaspace.txt')
    $loaderText = Get-Content -Raw -LiteralPath (Join-Path $Directory 'classloader-stats.txt')
    $loaders = $null
    $classes = $null
    if ($metaText -match 'Total Usage -\s+(\d+)\s+loaders,\s+(\d+)\s+classes') {
        $loaders = [long]$matches[1]
        $classes = [long]$matches[2]
    } elseif ($loaderText -match 'Total =\s+(\d+)\s+(\d+)') {
        $loaders = [long]$matches[1]
        $classes = [long]$matches[2]
    }
    [ordered]@{ classLoaderCount = $loaders; loadedClasses = $classes }
}

function Convert-HexAddress {
    param([Parameter(Mandatory)][string]$Value)
    [Convert]::ToInt64($Value, 16)
}

function Get-JavaHeapRange {
    param([Parameter(Mandatory)][string]$HeapInfoPath)
    $text = Get-Content -Raw -LiteralPath $HeapInfoPath
    $starts = [Collections.Generic.List[long]]::new()
    $ends = [Collections.Generic.List[long]]::new()
    foreach ($match in [regex]::Matches($text, '\[0x([0-9a-fA-F]+),\s*0x[0-9a-fA-F]+,\s*0x([0-9a-fA-F]+)\)')) {
        $starts.Add((Convert-HexAddress $match.Groups[1].Value))
        $ends.Add((Convert-HexAddress $match.Groups[2].Value))
    }
    if ($starts.Count -eq 0) { return $null }
    [ordered]@{ start = ($starts | Measure-Object -Minimum).Minimum; end = ($ends | Measure-Object -Maximum).Maximum }
}

function Get-SmapsHeapMetrics {
    param(
        [Parameter(Mandatory)][string]$SmapsPath,
        [Parameter(Mandatory)][string]$HeapInfoPath
    )
    $range = Get-JavaHeapRange -HeapInfoPath $HeapInfoPath
    if ($null -eq $range) {
        return [ordered]@{ pssKb = $null; privateDirtyKb = $null; rssKb = $null }
    }
    $totals = [ordered]@{ pssKb = 0L; privateDirtyKb = 0L; rssKb = 0L }
    $include = $false
    foreach ($line in Get-Content -LiteralPath $SmapsPath) {
        if ($line -match '^([0-9a-fA-F]+)-([0-9a-fA-F]+)\s') {
            $start = Convert-HexAddress $matches[1]
            $end = Convert-HexAddress $matches[2]
            $include = ($start -lt [long]$range.end -and $end -gt [long]$range.start)
            continue
        }
        if (-not $include) { continue }
        if ($line -match '^Rss:\s+(\d+)\s+kB') { $totals.rssKb += [long]$matches[1] }
        elseif ($line -match '^Pss:\s+(\d+)\s+kB') { $totals.pssKb += [long]$matches[1] }
        elseif ($line -match '^Private_Dirty:\s+(\d+)\s+kB') { $totals.privateDirtyKb += [long]$matches[1] }
    }
    $totals
}

function Get-ArmMetrics {
    param(
        [Parameter(Mandatory)][string]$Directory,
        [Parameter(Mandatory)][string]$ArmId
    )
    foreach ($file in @(
        'run-manifest.json', 'smaps_rollup.txt', 'smaps.txt', 'memory.current',
        'memory.stat', 'heap-info.txt', 'nmt-summary.txt', 'class-histogram.txt',
        'metaspace.txt', 'classloader-stats.txt', 'workload-result.json'
    )) {
        if (-not (Test-Path -LiteralPath (Join-Path $Directory $file) -PathType Leaf)) {
            throw "Required attribution input missing for ${ArmId}: $file"
        }
    }
    $manifest = Get-Content -Raw -LiteralPath (Join-Path $Directory 'run-manifest.json') | ConvertFrom-Json
    $workload = Get-Content -Raw -LiteralPath (Join-Path $Directory 'workload-result.json') | ConvertFrom-Json
    $rollup = Get-KeyValueFile (Join-Path $Directory 'smaps_rollup.txt')
    $memoryStat = Get-KeyValueFile (Join-Path $Directory 'memory.stat')
    $heap = Get-HeapMetrics (Join-Path $Directory 'heap-info.txt')
    $heapMapping = Get-SmapsHeapMetrics -SmapsPath (Join-Path $Directory 'smaps.txt') -HeapInfoPath (Join-Path $Directory 'heap-info.txt')
    $nmt = Get-NmtMetrics (Join-Path $Directory 'nmt-summary.txt')
    $histogram = Get-HistogramMetrics (Join-Path $Directory 'class-histogram.txt')
    $loaders = Get-MetaspaceLoaderMetrics $Directory
    $start = [datetime]$manifest.timestampStart
    $post = [datetime]$manifest.timestampPost
    $workloadEnd = [datetime]$workload.generatedAt
    [ordered]@{
        armId = $ArmId
        directoryName = Split-Path -Leaf $Directory
        pairIndex = [int]$manifest.pairIndex
        variant = [string]$manifest.variant
        timestampStart = $start.ToUniversalTime().ToString('o')
        timestampPost = $post.ToUniversalTime().ToString('o')
        startupMillis = [long]$manifest.startupMillis
        jvmAgeAtCaptureSeconds = [math]::Round(($post - $start).TotalSeconds, 3)
        healthToCaptureSeconds = [math]::Round(($post - $start).TotalSeconds - ([long]$manifest.startupMillis / 1000.0), 3)
        workloadCompletedAt = $workloadEnd.ToUniversalTime().ToString('o')
        workloadToCaptureSeconds = [math]::Round(($post - $workloadEnd).TotalSeconds, 3)
        requests = [int]$workload.requests
        workloadErrors = [int]$workload.errors
        mutationsProven = [bool]$workload.mutationsProven
        process = [ordered]@{
            rssKb = [long]$rollup.Rss
            pssKb = [long]$rollup.Pss
            privateDirtyKb = [long]$rollup.Private_Dirty
            sharedCleanKb = [long]$rollup.Shared_Clean
            privateCleanKb = [long]$rollup.Private_Clean
            anonymousKb = [long]$rollup.Anonymous
            anonHugePagesKb = [long]$rollup.AnonHugePages
            swapKb = [long]$rollup.Swap
        }
        targetCgroup = [ordered]@{
            memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $Directory 'memory.current')).Trim()
            anonBytes = [long]$memoryStat.anon
            fileBytes = [long]$memoryStat.file
            slabBytes = [long]$memoryStat.slab
            pagetablesBytes = [long]$memoryStat.pagetables
            kernelStackBytes = [long]$memoryStat.kernel_stack
            pageFaults = [long]$memoryStat.pgfault
            majorPageFaults = [long]$memoryStat.pgmajfault
        }
        javaHeap = $heap
        javaHeapMapping = $heapMapping
        nmt = $nmt
        histogram = $histogram
        classMetadata = $loaders
        unavailable = @('gcCount', 'lastGcTime', 'codeCacheUsedKb')
    }
}

function Get-Delta {
    param($First, $Second)
    [ordered]@{
        pssKb = [long]$Second.process.pssKb - [long]$First.process.pssKb
        privateDirtyKb = [long]$Second.process.privateDirtyKb - [long]$First.process.privateDirtyKb
        rssKb = [long]$Second.process.rssKb - [long]$First.process.rssKb
        anonymousKb = [long]$Second.process.anonymousKb - [long]$First.process.anonymousKb
        anonHugePagesKb = [long]$Second.process.anonHugePagesKb - [long]$First.process.anonHugePagesKb
        memoryCurrentBytes = [long]$Second.targetCgroup.memoryCurrentBytes - [long]$First.targetCgroup.memoryCurrentBytes
        cgroupAnonBytes = [long]$Second.targetCgroup.anonBytes - [long]$First.targetCgroup.anonBytes
        cgroupFileBytes = [long]$Second.targetCgroup.fileBytes - [long]$First.targetCgroup.fileBytes
        cgroupSlabBytes = [long]$Second.targetCgroup.slabBytes - [long]$First.targetCgroup.slabBytes
        cgroupPagetablesBytes = [long]$Second.targetCgroup.pagetablesBytes - [long]$First.targetCgroup.pagetablesBytes
        heapPssKb = [long]$Second.javaHeapMapping.pssKb - [long]$First.javaHeapMapping.pssKb
        heapPrivateDirtyKb = [long]$Second.javaHeapMapping.privateDirtyKb - [long]$First.javaHeapMapping.privateDirtyKb
        heapUsedKb = [long]$Second.javaHeap.usedKb - [long]$First.javaHeap.usedKb
        heapCommittedKb = [long]$Second.javaHeap.committedKb - [long]$First.javaHeap.committedKb
        youngUsedKb = [long]$Second.javaHeap.youngUsedKb - [long]$First.javaHeap.youngUsedKb
        oldUsedKb = [long]$Second.javaHeap.oldUsedKb - [long]$First.javaHeap.oldUsedKb
        histogramInstances = [long]$Second.histogram.instances - [long]$First.histogram.instances
        histogramBytes = [long]$Second.histogram.bytes - [long]$First.histogram.bytes
        nmtJavaHeapCommittedKb = [long]$Second.nmt.javaHeapCommittedKb - [long]$First.nmt.javaHeapCommittedKb
        nmtClassCommittedKb = [long]$Second.nmt.classCommittedKb - [long]$First.nmt.classCommittedKb
        nmtMetaspaceCommittedKb = [long]$Second.nmt.metaspaceCommittedKb - [long]$First.nmt.metaspaceCommittedKb
        nmtCodeCommittedKb = [long]$Second.nmt.codeCommittedKb - [long]$First.nmt.codeCommittedKb
        loadedClasses = [long]$Second.classMetadata.loadedClasses - [long]$First.classMetadata.loadedClasses
        classLoaders = [long]$Second.classMetadata.classLoaderCount - [long]$First.classMetadata.classLoaderCount
        threads = [long]$Second.nmt.threads - [long]$First.nmt.threads
        startupMillis = [long]$Second.startupMillis - [long]$First.startupMillis
        jvmAgeAtCaptureSeconds = [math]::Round([double]$Second.jvmAgeAtCaptureSeconds - [double]$First.jvmAgeAtCaptureSeconds, 3)
        healthToCaptureSeconds = [math]::Round([double]$Second.healthToCaptureSeconds - [double]$First.healthToCaptureSeconds, 3)
        workloadToCaptureSeconds = [math]::Round([double]$Second.workloadToCaptureSeconds - [double]$First.workloadToCaptureSeconds, 3)
    }
}

function Get-Median {
    param([double[]]$Values)
    $ordered = @($Values | Sort-Object)
    if ($ordered.Count -eq 0) { return $null }
    if ($ordered.Count % 2 -eq 1) { return [double]$ordered[[int]($ordered.Count / 2)] }
    ([double]$ordered[$ordered.Count / 2 - 1] + [double]$ordered[$ordered.Count / 2]) / 2.0
}

function Select-Classification {
    param($Median)
    $pss = [math]::Abs([double]$Median.pssKb)
    if ($pss -lt 1) { return 'UNRESOLVED' }
    $heapPssShare = [math]::Abs([double]$Median.heapPssKb) / $pss
    $heapUsedSmall = [math]::Abs([double]$Median.heapUsedKb) -le 1024
    $histogramSmall = [math]::Abs([double]$Median.histogramBytes) -le 1048576
    if ($heapPssShare -ge 0.6 -and $heapUsedSmall -and $histogramSmall) {
        return 'SECOND_POSITION_HEAP_PAGE_TOUCH'
    }
    if ([math]::Abs([double]$Median.heapUsedKb) -ge 0.5 * $pss -and -not $histogramSmall) {
        return 'SECOND_POSITION_RETAINED_HEAP'
    }
    if ([math]::Abs([double]$Median.nmtClassCommittedKb + [double]$Median.nmtMetaspaceCommittedKb) -ge 0.5 * $pss) {
        return 'SECOND_POSITION_CLASS_METADATA'
    }
    if ([math]::Abs([double]$Median.nmtCodeCommittedKb) -ge 0.5 * $pss) {
        return 'SECOND_POSITION_CODE_CACHE'
    }
    if ([math]::Abs([double]$Median.cgroupFileBytes / 1024.0) -ge 0.5 * $pss) {
        return 'SECOND_POSITION_FILE_MAPPING'
    }
    if ([math]::Abs([double]$Median.cgroupAnonBytes / 1024.0) -ge 0.5 * $pss) {
        return 'SECOND_POSITION_NATIVE_ANON'
    }
    if ([math]::Abs([double]$Median.memoryCurrentBytes / 1024.0) -ge 2 * $pss) {
        return 'SECOND_POSITION_CGROUP_ACCOUNTING'
    }
    'MIXED'
}

$resolvedRoot = (Resolve-Path -LiteralPath $CaptureRoot).Path
New-JmoaDirectory $OutputDirectory
$armDirectories = @('b1', 'c1', 'c2', 'b2')
$arms = [ordered]@{}
foreach ($name in $armDirectories) {
    $directory = Join-Path $resolvedRoot $name
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        throw "B0 control arm missing: $directory"
    }
    $arms[$name] = Get-ArmMetrics -Directory $directory -ArmId $name
}

$pairs = [Collections.Generic.List[object]]::new()
foreach ($pairDefinition in @(
    @{ id = 'B0_p1'; members = @('b1', 'c1') },
    @{ id = 'B0_p2'; members = @('c2', 'b2') }
)) {
    $orderedArms = @($pairDefinition.members | ForEach-Object { $arms[$_] } | Sort-Object { [datetime]$_.timestampStart })
    $pairs.Add([ordered]@{
        id = $pairDefinition.id
        firstArm = $orderedArms[0].armId
        secondArm = $orderedArms[1].armId
        first = $orderedArms[0]
        second = $orderedArms[1]
        delta = Get-Delta -First $orderedArms[0] -Second $orderedArms[1]
    })
}

$deltaNames = @($pairs[0].delta.Keys)
$medians = [ordered]@{}
foreach ($name in $deltaNames) {
    $medians[$name] = Get-Median @([double]$pairs[0].delta[$name], [double]$pairs[1].delta[$name])
}
$classification = Select-Classification $medians
$timingConverged = (
    [math]::Abs([double]$medians.jvmAgeAtCaptureSeconds) -le 2.0 -and
    [math]::Abs([double]$medians.workloadToCaptureSeconds) -le 0.5
)
$report = [ordered]@{
    schemaVersion = 'jmoa-petclinic-b0-period-effect-attribution-v1'
    sourceProtocol = 'PETCLINIC_TARGET_ONLY_V1'
    evidenceMode = 'READ_ONLY_EXISTING_CAPTURES'
    arms = @($arms.Values)
    pairs = $pairs.ToArray()
    medianSecondMinusFirst = $medians
    timing = [ordered]@{
        maximumJvmAgeDifferenceSeconds = 2.0
        maximumPostWorkloadDifferenceSeconds = 0.5
        converged = $timingConverged
    }
    classification = $classification
    unavailableDiagnostics = @('gcCount', 'lastGcTime', 'codeCacheUsedKb', 'supportPssAtTargetCapture')
    claimBoundary = 'Diagnostic attribution only. No B0-to-V2 result is inferred.'
}

$jsonPath = Join-Path $OutputDirectory 'petclinic-b0-period-effect-attribution.json'
Write-JmoaJson $report $jsonPath
$pairRows = @($report.pairs | ForEach-Object {
    "| $($_.id) | $($_.firstArm) | $($_.secondArm) | $($_.delta.pssKb) | $($_.delta.privateDirtyKb) | $($_.delta.heapPssKb) | $($_.delta.heapUsedKb) | $($_.delta.histogramBytes) | $($_.delta.memoryCurrentBytes) |"
})
$markdown = @"
# PetClinic B0 Period-Effect Attribution

- Source: **PETCLINIC_TARGET_ONLY_V1 existing B0 controls**
- Classification: **$classification**
- Timing converged under the frozen limits: **$timingConverged**
- Evidence mode: **read-only; no service was rerun**

| Pair | First | Second | PSS KB | Private_Dirty KB | Heap PSS KB | Heap used KB | Histogram bytes | memory.current bytes |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
$($pairRows -join "`n")

## Median Second Minus First

- PSS: $($medians.pssKb) KB
- Private_Dirty: $($medians.privateDirtyKb) KB
- Java-heap mapping PSS: $($medians.heapPssKb) KB
- Heap used: $($medians.heapUsedKb) KB
- Histogram bytes: $($medians.histogramBytes) bytes
- cgroup anonymous: $($medians.cgroupAnonBytes) bytes
- cgroup file: $($medians.cgroupFileBytes) bytes
- NMT Class: $($medians.nmtClassCommittedKb) KB
- NMT Metaspace: $($medians.nmtMetaspaceCommittedKb) KB
- NMT Code: $($medians.nmtCodeCommittedKb) KB

## Boundary

This report attributes the same-artifact period effect. It does not compare B0
with V2 and does not change any frozen campaign threshold.
"@
Write-JmoaText $markdown (Join-Path $OutputDirectory 'petclinic-b0-period-effect-attribution.md')
$report | ConvertTo-Json -Depth 16
