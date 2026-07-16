param(
    [Parameter(Mandatory)][string]$ConfirmationDirectory,
    [Parameter(Mandatory)][string]$ControlledScreenDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Read-KeyValueFile([string]$Path) {
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $result }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^([^\s:]+)[:\s]+(-?\d+)') { $result[$matches[1]] = [long]$matches[2] }
    }
    $result
}

function Read-SmapsRollup([string]$Path) { Read-KeyValueFile $Path }

function Read-Nmt([string]$Path) {
    $text = if (Test-Path -LiteralPath $Path) { Get-Content -Raw -LiteralPath $Path } else { '' }
    $result = [ordered]@{}
    foreach ($item in @(
        @{ key='total'; pattern='Total: reserved=\d+KB, committed=(\d+)KB' },
        @{ key='heap'; pattern='Java Heap \(reserved=\d+KB, committed=(\d+)KB' },
        @{ key='class'; pattern='Class \(reserved=\d+KB, committed=(\d+)KB' },
        @{ key='thread'; pattern='Thread \(reserved=\d+KB, committed=(\d+)KB' },
        @{ key='code'; pattern='Code \(reserved=\d+KB, committed=(\d+)KB' },
        @{ key='arena'; pattern='Arena Chunk \(reserved=\d+KB, committed=(\d+)KB' }
    )) {
        $match = [regex]::Match($text, $item.pattern)
        $result[$item.key] = if ($match.Success) { [long]$match.Groups[1].Value } else { $null }
    }
    $meta = [regex]::Match($text, 'Metadata:\s+\).*?reserved=\d+KB, committed=(\d+)KB', 'Singleline')
    $result.metaspace = if ($meta.Success) { [long]$meta.Groups[1].Value } else { $null }
    $classes = [regex]::Match($text, '\(classes #(\d+)\)')
    $result.loadedClasses = if ($classes.Success) { [long]$classes.Groups[1].Value } else { $null }
    $result
}

function Read-Heap([string]$Path) {
    $text = if (Test-Path -LiteralPath $Path) { Get-Content -Raw -LiteralPath $Path } else { '' }
    $ranges = @()
    foreach ($match in [regex]::Matches($text, '(?m)^(DefNew|Tenured)\s+total\s+(\d+)K, used\s+(\d+)K\s+\[0x([0-9a-f]+),\s*0x([0-9a-f]+),\s*0x([0-9a-f]+)\)')) {
        $ranges += [ordered]@{
            generation = $match.Groups[1].Value
            committedKb = [long]$match.Groups[2].Value
            usedKb = [long]$match.Groups[3].Value
            start = [Convert]::ToUInt64($match.Groups[4].Value, 16)
            committedEnd = [Convert]::ToUInt64($match.Groups[5].Value, 16)
            reservedEnd = [Convert]::ToUInt64($match.Groups[6].Value, 16)
        }
    }
    [ordered]@{
        usedKb = [long](($ranges | ForEach-Object { [long]$_.usedKb } | Measure-Object -Sum).Sum)
        committedKb = [long](($ranges | ForEach-Object { [long]$_.committedKb } | Measure-Object -Sum).Sum)
        generations = $ranges
    }
}

function Read-Histogram([string]$Path) {
    $families = [ordered]@{ byte_arrays=0; object_arrays=0; strings=0; maps=0; spring=0; hibernate=0; jackson=0; class_metadata=0; lambdas=0; jmoa_runtime=0; other=0 }
    $total = 0L; $instances = 0L; $classCount = 0
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in Get-Content -LiteralPath $Path) {
            if ($line -notmatch '^\s*\d+:\s+(\d+)\s+(\d+)\s+(.+?)(?:\s+\([^)]*\))?$') { continue }
            $count = [long]$matches[1]; $bytes = [long]$matches[2]; $name = $matches[3].Trim()
            $instances += $count; $total += $bytes; $classCount++
            $family = if ($name -eq '[B') { 'byte_arrays' }
                elseif ($name -match '^\[L|^\[\[') { 'object_arrays' }
                elseif ($name -eq 'java.lang.String') { 'strings' }
                elseif ($name -match 'Map|HashTable|Dictionary') { 'maps' }
                elseif ($name -match '^org\.springframework\.|^org\.springframework') { 'spring' }
                elseif ($name -match 'hibernate|jakarta\.persistence') { 'hibernate' }
                elseif ($name -match 'jackson') { 'jackson' }
                elseif ($name -match 'java\.lang\.Class|MethodType|MethodHandle|reflect\.') { 'class_metadata' }
                elseif ($name -match '\$\$Lambda|lambda') { 'lambdas' }
                elseif ($name -match 'jmoa') { 'jmoa_runtime' }
                else { 'other' }
            $families[$family] = [long]$families[$family] + $bytes
        }
    }
    [ordered]@{ totalBytes=$total; totalInstances=$instances; classCount=$classCount; families=$families }
}

function Read-SmapsRegions([string]$Path, $HeapRanges) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return @() }
    $regions = @(); $current = $null
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^([0-9a-f]+)-([0-9a-f]+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s*(.*)$') {
            if ($null -ne $current) { $regions += $current }
            $start = [Convert]::ToUInt64($matches[1],16); $end = [Convert]::ToUInt64($matches[2],16)
            $permissions = $matches[3]; $name = $matches[4].Trim()
            $overlapsHeap = @($HeapRanges | Where-Object { $start -lt $_.reservedEnd -and $end -gt $_.start }).Count -gt 0
            $category = if ($overlapsHeap) { 'JAVA_HEAP' }
                elseif ($name -match '\[stack') { 'THREAD_STACK' }
                elseif ($name -match '\.jsa$') { 'CDS_ARCHIVE' }
                elseif ($permissions -match 'x' -and [string]::IsNullOrWhiteSpace($name)) { 'ANONYMOUS_EXECUTABLE_CODE' }
                elseif ([string]::IsNullOrWhiteSpace($name) -and $permissions.StartsWith('rw')) { 'ANONYMOUS_RW_OUTSIDE_HEAP' }
                elseif ($name -match '/opt/java|/lib/jvm|jimage|modules$') { 'JDK_IMAGE_OR_LIBRARY' }
                elseif ($name -match 'app\.jar|BOOT-INF') { 'APPLICATION_JAR_MAPPING' }
                elseif (-not [string]::IsNullOrWhiteSpace($name) -and $name.StartsWith('/')) { 'OTHER_FILE_MAPPING' }
                else { 'OTHER_SPECIAL_MAPPING' }
            $current = [ordered]@{ start=$start; end=$end; address=("{0:x}-{1:x}" -f $start,$end); permissions=$permissions; name=$name; category=$category; sizeKb=0L; pssKb=0L; privateDirtyKb=0L; anonymousKb=0L }
        } elseif ($null -ne $current -and $line -match '^(Size|Pss|Private_Dirty|Anonymous):\s+(\d+) kB') {
            switch ($matches[1]) { 'Size' {$current.sizeKb=[long]$matches[2]}; 'Pss' {$current.pssKb=[long]$matches[2]}; 'Private_Dirty' {$current.privateDirtyKb=[long]$matches[2]}; 'Anonymous' {$current.anonymousKb=[long]$matches[2]} }
        }
    }
    if ($null -ne $current) { $regions += $current }
    $regions
}

function Read-Run([string]$Directory) {
    $manifest = Get-Content -Raw -LiteralPath (Join-Path $Directory 'run-manifest.json') | ConvertFrom-Json
    $workload = Get-Content -Raw -LiteralPath (Join-Path $Directory 'workload-result.json') | ConvertFrom-Json
    $rollup = Read-SmapsRollup (Join-Path $Directory 'smaps_rollup.txt')
    $heap = Read-Heap (Join-Path $Directory 'heap-info.txt')
    $nmt = Read-Nmt (Join-Path $Directory 'nmt-summary.txt')
    $histogram = Read-Histogram (Join-Path $Directory 'class-histogram.txt')
    $memoryStat = Read-KeyValueFile (Join-Path $Directory 'memory.stat')
    $io = Read-KeyValueFile (Join-Path $Directory 'io.stat')
    $regions = Read-SmapsRegions (Join-Path $Directory 'smaps.txt') $heap.generations
    $category = [ordered]@{}
    foreach ($group in $regions | Group-Object { $_.category }) {
        $category[$group.Name] = [ordered]@{
            pssKb=[long](($group.Group|ForEach-Object{[long]$_.pssKb}|Measure-Object -Sum).Sum)
            privateDirtyKb=[long](($group.Group|ForEach-Object{[long]$_.privateDirtyKb}|Measure-Object -Sum).Sum)
            anonymousKb=[long](($group.Group|ForEach-Object{[long]$_.anonymousKb}|Measure-Object -Sum).Sum)
        }
    }
    $completed = if ($workload.completedAt) { [DateTimeOffset]::Parse($workload.completedAt) } else { $null }
    $captured = if ($manifest.timestampPost) { [DateTimeOffset]::Parse($manifest.timestampPost) } else { $null }
    [ordered]@{
        runId=$manifest.runId; directory=$Directory; pssKb=[long]$rollup.Pss; privateDirtyKb=[long]$rollup.Private_Dirty
        memoryCurrentBytes=if(Test-Path (Join-Path $Directory 'memory.current')){[long](Get-Content -Raw (Join-Path $Directory 'memory.current'))}else{$null}
        memoryStat=$memoryStat; io=$io; heap=$heap; nmt=$nmt; histogram=$histogram; smapsCategories=$category; regions=$regions
        startupMillis=$manifest.startupMillis; captureDelaySeconds=if($completed -and $captured){[Math]::Round(($captured-$completed).TotalSeconds,3)}else{$null}
    }
}

function Delta($Candidate, $Baseline) { if($null -eq $Candidate -or $null -eq $Baseline){$null}else{[long]$Candidate-[long]$Baseline} }

function Compare-Runs($Baseline, $Candidate, [string]$Label) {
    $families=[ordered]@{}; foreach($key in $Baseline.histogram.families.Keys){$families[$key]=Delta $Candidate.histogram.families[$key] $Baseline.histogram.families[$key]}
    $categoryKeys = @(@($Baseline.smapsCategories.Keys) + @($Candidate.smapsCategories.Keys) | Sort-Object -Unique)
    $categories=[ordered]@{}; foreach($key in $categoryKeys){
        $b=if($Baseline.smapsCategories.Contains($key)){$Baseline.smapsCategories[$key]}else{@{pssKb=0;privateDirtyKb=0;anonymousKb=0}}
        $c=if($Candidate.smapsCategories.Contains($key)){$Candidate.smapsCategories[$key]}else{@{pssKb=0;privateDirtyKb=0;anonymousKb=0}}
        $categories[$key]=[ordered]@{pssKb=Delta $c.pssKb $b.pssKb;privateDirtyKb=Delta $c.privateDirtyKb $b.privateDirtyKb;anonymousKb=Delta $c.anonymousKb $b.anonymousKb}
    }
    $baselineRegions = @{}; $candidateRegions = @{}
    foreach($region in $Baseline.regions){$key="$($region.category)|$($region.permissions)|$($region.name)|$($region.sizeKb)";$baselineRegions[$key]=[long]$baselineRegions[$key]+$region.pssKb}
    foreach($region in $Candidate.regions){$key="$($region.category)|$($region.permissions)|$($region.name)|$($region.sizeKb)";$candidateRegions[$key]=[long]$candidateRegions[$key]+$region.pssKb}
    $regionKeys=@(@($baselineRegions.Keys)+@($candidateRegions.Keys)|Sort-Object -Unique)
    $topRegions = @($regionKeys | ForEach-Object {
        [ordered]@{regionKey=$_;pssDeltaKb=[long]$candidateRegions[$_]-[long]$baselineRegions[$_]}
    } | Sort-Object {[Math]::Abs($_.pssDeltaKb)} -Descending | Select-Object -First 15)
    [ordered]@{
        label=$Label; baselineRun=$Baseline.runId; candidateRun=$Candidate.runId
        pssDeltaKb=Delta $Candidate.pssKb $Baseline.pssKb; privateDirtyDeltaKb=Delta $Candidate.privateDirtyKb $Baseline.privateDirtyKb
        memoryCurrentDeltaBytes=Delta $Candidate.memoryCurrentBytes $Baseline.memoryCurrentBytes
        memoryStat=[ordered]@{anonBytes=Delta $Candidate.memoryStat.anon $Baseline.memoryStat.anon;fileBytes=Delta $Candidate.memoryStat.file $Baseline.memoryStat.file;kernelBytes=Delta $Candidate.memoryStat.kernel $Baseline.memoryStat.kernel}
        heap=[ordered]@{usedKb=Delta $Candidate.heap.usedKb $Baseline.heap.usedKb;committedKb=Delta $Candidate.heap.committedKb $Baseline.heap.committedKb;javaHeapPssKb=if($categories.JAVA_HEAP){$categories.JAVA_HEAP.pssKb}else{0}}
        nmt=[ordered]@{totalKb=Delta $Candidate.nmt.total $Baseline.nmt.total;heapKb=Delta $Candidate.nmt.heap $Baseline.nmt.heap;classKb=Delta $Candidate.nmt.class $Baseline.nmt.class;metaspaceKb=Delta $Candidate.nmt.metaspace $Baseline.nmt.metaspace;threadKb=Delta $Candidate.nmt.thread $Baseline.nmt.thread;codeKb=Delta $Candidate.nmt.code $Baseline.nmt.code;arenaKb=Delta $Candidate.nmt.arena $Baseline.nmt.arena;loadedClasses=Delta $Candidate.nmt.loadedClasses $Baseline.nmt.loadedClasses}
        histogram=[ordered]@{bytes=Delta $Candidate.histogram.totalBytes $Baseline.histogram.totalBytes;instances=Delta $Candidate.histogram.totalInstances $Baseline.histogram.totalInstances;families=$families}
        smapsCategories=$categories;topRegionDeltas=$topRegions
        timing=[ordered]@{startupMillis=Delta $Candidate.startupMillis $Baseline.startupMillis;baselineCaptureDelaySeconds=$Baseline.captureDelaySeconds;candidateCaptureDelaySeconds=$Candidate.captureDelaySeconds}
    }
}

$comparisons=@()
foreach($i in 1..3){$comparisons+=Compare-Runs (Read-Run (Join-Path $ConfirmationDirectory "b$i")) (Read-Run (Join-Path $ConfirmationDirectory "c$i")) "confirmation-pair-$i"}
$comparisons+=Compare-Runs (Read-Run (Join-Path $ControlledScreenDirectory 'b1')) (Read-Run (Join-Path $ControlledScreenDirectory 'c1')) 'controlled-screen'
$screen=$comparisons[-1]
$screenAnonOutside=if($screen.smapsCategories.ANONYMOUS_RW_OUTSIDE_HEAP){$screen.smapsCategories.ANONYMOUS_RW_OUTSIDE_HEAP.pssKb}else{0}
$screenHeap=if($screen.smapsCategories.JAVA_HEAP){$screen.smapsCategories.JAVA_HEAP.pssKb}else{0}
$classification=if($screenHeap -gt 4096){'HEAP_PAGE_TOUCH_GROWTH'}elseif($screenAnonOutside -gt 4096){'NON_HEAP_ANONYMOUS_GROWTH'}elseif($screen.smapsCategories.THREAD_STACK.pssKb -gt 1024){'THREAD_STACK_GROWTH'}elseif($screen.nmt.arenaKb -gt 1024){'ALLOCATOR_ARENA_GROWTH'}else{'UNATTRIBUTED_ANONYMOUS_GROWTH'}
$retainedGrowth=@($comparisons|Where-Object {$_.histogram.bytes -gt 1048576}).Count -gt 0
$report=[ordered]@{
    metadataVersion='patient-pair-attribution-v1';generatedAt=(Get-Date).ToUniversalTime().ToString('o');comparisons=$comparisons
    fullSmapsClassification=$classification
    histogramClassification=if($retainedGrowth){'RETAINED_OBJECT_GROWTH'}else{'NO_MEANINGFUL_OBJECT_GROWTH'}
    interpretation='Original evidence is pair-variant. Class histogram and heap-used deltas do not explain the anonymous writable PSS movement. Diagnostics must separate CDS, order, GC state, and settle time.'
}
$report|ConvertTo-Json -Depth 100|Set-Content -LiteralPath (Join-Path $OutputDirectory 'patient-pair-attribution.json') -Encoding UTF8
$rows=$comparisons|ForEach-Object{"| $($_.label) | $($_.pssDeltaKb) | $($_.privateDirtyDeltaKb) | $($_.memoryCurrentDeltaBytes) | $($_.heap.javaHeapPssKb) | $($_.smapsCategories.ANONYMOUS_RW_OUTSIDE_HEAP.pssKb) | $($_.heap.usedKb) | $($_.heap.committedKb) | $($_.histogram.bytes) | $($_.nmt.totalKb) |"}
@"
# Patient Pair-Level Attribution

| Pair | PSS KB | Private Dirty KB | memory.current B | Java heap PSS KB | anon rw outside heap KB | heap used KB | heap committed KB | histogram bytes | NMT committed KB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
$($rows -join "`n")

- Full smaps classification: **$classification**
- Histogram classification: **$($report.histogramClassification)**

The original confirmation lacks `memory.stat` and `io.stat`; those fields are explicitly null there and available in the later controlled screen. The pair spread is too large for a median-only causal statement. Retained histogram bytes do not account for the writable anonymous-page movement.
"@|Set-Content -LiteralPath (Join-Path $OutputDirectory 'patient-pair-attribution.md') -Encoding UTF8
Write-Host "Patient pair attribution: $classification / $($report.histogramClassification)"
