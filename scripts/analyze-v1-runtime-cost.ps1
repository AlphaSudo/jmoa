<#
.SYNOPSIS
    Explains the direct B0-to-V1 cost from sealed three-artifact campaigns.

.DESCRIPTION
    This is a read-only existing-evidence analyzer. It never launches a service,
    mutates an artifact, or changes raw campaign evidence. Private service class
    and site names are represented by one-way hashes in public outputs.
#>
param(
    [Parameter(Mandatory)][string]$DoctorCampaignRoot,
    [Parameter(Mandatory)][string]$PatientCampaignRoot,
    [Parameter(Mandatory)][string]$PetClinicCampaignRoot,
    [Parameter(Mandatory)][string]$DoctorBuildReport,
    [Parameter(Mandatory)][string]$PatientBuildReport,
    [Parameter(Mandatory)][string]$PetClinicBuildReport,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\docs\product-evidence')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Read-Json {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required JSON does not exist: $Path"
    }
    Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Get-Property {
    param($Object, [Parameter(Mandatory)][string]$Name, $Default = $null)
    if ($null -eq $Object) { return $Default }
    if ($Object -is [Collections.IDictionary]) {
        if ($Object.Contains($Name)) { return $Object[$Name] }
        return $Default
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $Default }
    $property.Value
}

function Get-Median {
    param([object[]]$Values)
    $usable = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ } | Sort-Object)
    if ($usable.Count -eq 0) { return $null }
    if (($usable.Count % 2) -eq 1) { return [double]$usable[[math]::Floor($usable.Count / 2)] }
    ([double]$usable[$usable.Count / 2 - 1] + [double]$usable[$usable.Count / 2]) / 2.0
}

function Get-TextSha256 {
    param([Parameter(Mandatory)][string]$Value)
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Value)))
}

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

function Convert-HexAddress {
    param([Parameter(Mandatory)][string]$Value)
    [Convert]::ToInt64($Value, 16)
}

function Get-HeapMetrics {
    param([Parameter(Mandatory)][string]$Path)
    $text = Get-Content -Raw -LiteralPath $Path
    $used = 0L
    $committed = 0L
    $starts = [Collections.Generic.List[long]]::new()
    $ends = [Collections.Generic.List[long]]::new()
    foreach ($match in [regex]::Matches(
        $text,
        '(?m)^(?:\s*def new generation|\s*tenured generation|\s*DefNew|\s*Tenured)\s+total\s+(\d+)K,\s+used\s+(\d+)K\s+\[0x([0-9a-fA-F]+),\s*0x[0-9a-fA-F]+,\s*0x([0-9a-fA-F]+)\)'
    )) {
        $committed += [long]$match.Groups[1].Value
        $used += [long]$match.Groups[2].Value
        $starts.Add((Convert-HexAddress $match.Groups[3].Value))
        $ends.Add((Convert-HexAddress $match.Groups[4].Value))
    }
    $metaUsed = $null
    $metaCommitted = $null
    $classUsed = $null
    $classCommitted = $null
    if ($text -match 'Metaspace\s+used\s+(\d+)K,\s+committed\s+(\d+)K') {
        $metaUsed = [long]$matches[1]
        $metaCommitted = [long]$matches[2]
    }
    if ($text -match 'class space\s+used\s+(\d+)K,\s+committed\s+(\d+)K') {
        $classUsed = [long]$matches[1]
        $classCommitted = [long]$matches[2]
    }
    [ordered]@{
        usedKb = $used
        committedKb = $committed
        metaspaceUsedKb = $metaUsed
        metaspaceCommittedKb = $metaCommitted
        classSpaceUsedKb = $classUsed
        classSpaceCommittedKb = $classCommitted
        range = if ($starts.Count -gt 0) {
            [ordered]@{
                start = [long](($starts | Measure-Object -Minimum).Minimum)
                end = [long](($ends | Measure-Object -Maximum).Maximum)
            }
        } else { $null }
    }
}

function Get-SmapsMetrics {
    param(
        [Parameter(Mandatory)][string]$Path,
        $HeapRange
    )
    $totals = [ordered]@{
        heapPssKb = 0L
        heapPrivateDirtyKb = 0L
        anonymousRwPssKb = 0L
        anonymousRwPrivateDirtyKb = 0L
        anonymousExecutablePssKb = 0L
        mappedFilePssKb = 0L
    }
    $current = $null
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^([0-9a-fA-F]+)-([0-9a-fA-F]+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s*(.*)$') {
            $start = Convert-HexAddress $matches[1]
            $end = Convert-HexAddress $matches[2]
            $permissions = $matches[3]
            $name = $matches[4].Trim()
            $heap = $null -ne $HeapRange -and $start -lt [long]$HeapRange.end -and $end -gt [long]$HeapRange.start
            $category = if ($heap) { 'HEAP' }
                elseif ([string]::IsNullOrWhiteSpace($name) -and $permissions.StartsWith('rw')) { 'ANONYMOUS_RW' }
                elseif ([string]::IsNullOrWhiteSpace($name) -and $permissions.Contains('x')) { 'ANONYMOUS_EXECUTABLE' }
                elseif (-not [string]::IsNullOrWhiteSpace($name) -and $name.StartsWith('/')) { 'MAPPED_FILE' }
                else { 'OTHER' }
            $current = [ordered]@{ category = $category; pssKb = 0L; privateDirtyKb = 0L }
            continue
        }
        if ($null -eq $current) { continue }
        if ($line -match '^Pss:\s+(\d+)\s+kB') { $current.pssKb = [long]$matches[1] }
        elseif ($line -match '^Private_Dirty:\s+(\d+)\s+kB') {
            $current.privateDirtyKb = [long]$matches[1]
            switch ($current.category) {
                'HEAP' {
                    $totals.heapPssKb += $current.pssKb
                    $totals.heapPrivateDirtyKb += $current.privateDirtyKb
                }
                'ANONYMOUS_RW' {
                    $totals.anonymousRwPssKb += $current.pssKb
                    $totals.anonymousRwPrivateDirtyKb += $current.privateDirtyKb
                }
                'ANONYMOUS_EXECUTABLE' { $totals.anonymousExecutablePssKb += $current.pssKb }
                'MAPPED_FILE' { $totals.mappedFilePssKb += $current.pssKb }
            }
            $current = $null
        }
    }
    $totals
}

function Get-NmtMetrics {
    param([Parameter(Mandatory)][string]$Path)
    $text = Get-Content -Raw -LiteralPath $Path
    $result = [ordered]@{
        totalCommittedKb = $null
        classCommittedKb = $null
        metadataCommittedKb = $null
        codeCommittedKb = $null
        loadedClasses = $null
        threads = $null
    }
    if ($text -match 'Total:\s+reserved=\d+KB,\s+committed=(\d+)KB') {
        $result.totalCommittedKb = [long]$matches[1]
    }
    if ($text -match '-\s+Class \(reserved=\d+KB, committed=(\d+)KB\)') {
        $result.classCommittedKb = [long]$matches[1]
    }
    if ($text -match '(?s)\(\s*Metadata:\s*\).*?reserved=\d+KB,\s+committed=(\d+)KB') {
        $result.metadataCommittedKb = [long]$matches[1]
    }
    if ($text -match '-\s+Code \(reserved=\d+KB, committed=(\d+)KB\)') {
        $result.codeCommittedKb = [long]$matches[1]
    }
    if ($text -match '\(classes #(\d+)\)') { $result.loadedClasses = [long]$matches[1] }
    if ($text -match '\(thread #(\d+)\)') { $result.threads = [long]$matches[1] }
    $result
}

function Get-MetaspaceMetrics {
    param([Parameter(Mandatory)][string]$Path)
    $text = Get-Content -Raw -LiteralPath $Path
    $result = [ordered]@{ classLoaders = $null; classes = $null }
    if ($text -match 'Total Usage -\s+(\d+)\s+loaders,\s+(\d+)\s+classes') {
        $result.classLoaders = [long]$matches[1]
        $result.classes = [long]$matches[2]
    }
    $result
}

function Get-HistogramMetrics {
    param([Parameter(Mandatory)][string]$Path)
    $result = [ordered]@{
        totalInstances = 0L
        totalBytes = 0L
        classRows = 0
        jmoaObjectInstances = 0L
        jmoaObjectBytes = 0L
        jmoaRuntimeClassRows = 0
        adapterClassRows = 0
        lambdaObjectInstances = 0L
        lambdaObjectBytes = 0L
        methodHandleInstances = 0L
        methodHandleBytes = 0L
        lambdaFormInstances = 0L
        lambdaFormBytes = 0L
        observedClasses = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -notmatch '^\s*\d+:\s+(\d+)\s+(\d+)\s+(\S+)') { continue }
        $instances = [long]$matches[1]
        $bytes = [long]$matches[2]
        $name = $matches[3]
        $result.totalInstances += $instances
        $result.totalBytes += $bytes
        $result.classRows++
        [void]$result.observedClasses.Add($name)
        if ($name -match '(?i)(^|\.)(jmoa\.runtime|com\.yourorg\.jmoa)|JmoaPkgAdapters') {
            $result.jmoaObjectInstances += $instances
            $result.jmoaObjectBytes += $bytes
            $result.jmoaRuntimeClassRows++
            if ($name -match 'JmoaPkgAdapters') { $result.adapterClassRows++ }
        }
        if ($name -match '\$\$Lambda|/0x[0-9a-fA-F]+|lambda') {
            $result.lambdaObjectInstances += $instances
            $result.lambdaObjectBytes += $bytes
        }
        if ($name -match '^java\.lang\.invoke\.(?:MethodHandle|BoundMethodHandle|DirectMethodHandle|DelegatingMethodHandle|MemberName|MethodType|ResolvedMethodName)') {
            $result.methodHandleInstances += $instances
            $result.methodHandleBytes += $bytes
        }
        if ($name -match '^java\.lang\.invoke\.LambdaForm') {
            $result.lambdaFormInstances += $instances
            $result.lambdaFormBytes += $bytes
        }
    }
    $result
}

function Get-CaptureDirectory {
    param([Parameter(Mandatory)][string]$SessionDirectory)
    $matches = @(Get-ChildItem -LiteralPath (Join-Path $SessionDirectory 'capture') -Recurse -Filter 'run-manifest.json' -File)
    if ($matches.Count -ne 1) { throw "Expected one run-manifest below $SessionDirectory." }
    $matches[0].Directory.FullName
}

function Get-SessionMetrics {
    param([Parameter(Mandatory)][string]$SessionDirectory)
    $result = Read-Json (Join-Path $SessionDirectory 'session-result.json')
    $capture = Get-CaptureDirectory $SessionDirectory
    foreach ($file in @(
        'smaps.txt', 'memory.stat', 'heap-info.txt', 'nmt-summary.txt',
        'metaspace.txt', 'class-histogram.txt'
    )) {
        if (-not (Test-Path -LiteralPath (Join-Path $capture $file) -PathType Leaf)) {
            throw "Required V1 cost input is missing: $capture\$file"
        }
    }
    $heap = Get-HeapMetrics (Join-Path $capture 'heap-info.txt')
    $smaps = Get-SmapsMetrics -Path (Join-Path $capture 'smaps.txt') -HeapRange $heap.range
    $memoryStat = Get-KeyValueFile (Join-Path $capture 'memory.stat')
    $nmt = Get-NmtMetrics (Join-Path $capture 'nmt-summary.txt')
    $metaspace = Get-MetaspaceMetrics (Join-Path $capture 'metaspace.txt')
    $histogram = Get-HistogramMetrics (Join-Path $capture 'class-histogram.txt')
    [pscustomobject]@{
        SessionId = [string]$result.sessionId
        Block = [int]$result.block
        Variant = [string]$result.variant
        PssKb = [long]$result.pssKb
        PrivateDirtyKb = [long]$result.privateDirtyKb
        MemoryCurrentBytes = [long]$result.memoryCurrentBytes
        HeapPssKb = [long]$smaps.heapPssKb
        HeapUsedKb = [long]$heap.usedKb
        AnonymousRwPssKb = [long]$smaps.anonymousRwPssKb
        AnonymousExecutablePssKb = [long]$smaps.anonymousExecutablePssKb
        MappedFilePssKb = [long]$smaps.mappedFilePssKb
        CgroupAnonBytes = [long](Get-Property $memoryStat 'anon' 0)
        CgroupFileBytes = [long](Get-Property $memoryStat 'file' 0)
        LoadedClasses = [long](Get-Property $nmt 'loadedClasses' 0)
        ClassLoaders = [long](Get-Property $metaspace 'classLoaders' 0)
        NmtClassCommittedKb = [long](Get-Property $nmt 'classCommittedKb' 0)
        NmtMetadataCommittedKb = [long](Get-Property $nmt 'metadataCommittedKb' 0)
        NmtCodeCommittedKb = [long](Get-Property $nmt 'codeCommittedKb' 0)
        Threads = [long](Get-Property $nmt 'threads' 0)
        HistogramInstances = [long]$histogram.totalInstances
        HistogramBytes = [long]$histogram.totalBytes
        HistogramClassRows = [long]$histogram.classRows
        JmoaObjectInstances = [long]$histogram.jmoaObjectInstances
        JmoaObjectBytes = [long]$histogram.jmoaObjectBytes
        JmoaRuntimeClassRows = [long]$histogram.jmoaRuntimeClassRows
        AdapterClassRows = [long]$histogram.adapterClassRows
        LambdaObjectInstances = [long]$histogram.lambdaObjectInstances
        LambdaObjectBytes = [long]$histogram.lambdaObjectBytes
        MethodHandleInstances = [long]$histogram.methodHandleInstances
        MethodHandleBytes = [long]$histogram.methodHandleBytes
        LambdaFormInstances = [long]$histogram.lambdaFormInstances
        LambdaFormBytes = [long]$histogram.lambdaFormBytes
        ObservedClasses = $histogram.observedClasses
    }
}

function Get-FinalSessions {
    param([Parameter(Mandatory)][string]$CampaignRoot)
    $sessions = foreach ($directory in @(Get-ChildItem -LiteralPath (Join-Path $CampaignRoot 'sessions') -Directory |
            Where-Object Name -Like 'block-*-position-*' | Sort-Object Name)) {
        Get-SessionMetrics $directory.FullName
    }
    if (@($sessions).Count -ne 18) { throw "Expected 18 final sessions below $CampaignRoot." }
    @($sessions)
}

function Get-Delta {
    param($Candidate, $Baseline)
    if ($null -eq $Candidate -or $null -eq $Baseline) { return $null }
    [double]$Candidate - [double]$Baseline
}

function Get-ServiceCensus {
    param(
        [Parameter(Mandatory)][string]$Id,
        [Parameter(Mandatory)][string]$Service,
        [Parameter(Mandatory)][string]$CampaignRoot
    )
    $sessions = Get-FinalSessions $CampaignRoot
    $metricNames = @(
        'PssKb', 'PrivateDirtyKb', 'MemoryCurrentBytes', 'HeapPssKb', 'HeapUsedKb',
        'AnonymousRwPssKb', 'AnonymousExecutablePssKb', 'MappedFilePssKb',
        'CgroupAnonBytes', 'CgroupFileBytes', 'LoadedClasses', 'ClassLoaders',
        'NmtClassCommittedKb', 'NmtMetadataCommittedKb', 'NmtCodeCommittedKb',
        'Threads', 'HistogramInstances', 'HistogramBytes', 'HistogramClassRows',
        'JmoaObjectInstances', 'JmoaObjectBytes', 'JmoaRuntimeClassRows',
        'AdapterClassRows', 'LambdaObjectInstances', 'LambdaObjectBytes',
        'MethodHandleInstances', 'MethodHandleBytes', 'LambdaFormInstances',
        'LambdaFormBytes'
    )
    $blocks = foreach ($block in 1..6) {
        $b0 = @($sessions | Where-Object { $_.Block -eq $block -and $_.Variant -eq 'B0' })[0]
        $v1 = @($sessions | Where-Object { $_.Block -eq $block -and $_.Variant -eq 'V1' })[0]
        $delta = [ordered]@{}
        foreach ($metric in $metricNames) { $delta[$metric] = Get-Delta $v1.$metric $b0.$metric }
        [ordered]@{
            block = $block
            b0Session = $b0.SessionId
            v1Session = $v1.SessionId
            delta = $delta
        }
    }
    $summary = [ordered]@{}
    foreach ($metric in $metricNames) {
        $summary[$metric] = [ordered]@{
            medianDelta = Get-Median @($blocks | ForEach-Object { $_.delta[$metric] })
            values = @($blocks | ForEach-Object { $_.delta[$metric] })
        }
    }
    $v1Classes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($session in @($sessions | Where-Object Variant -eq 'V1')) {
        foreach ($className in $session.ObservedClasses) { [void]$v1Classes.Add($className) }
    }
    [ordered]@{
        id = $Id
        service = $Service
        campaignProtocol = [string](Read-Json (Join-Path $CampaignRoot 'campaign-freeze.json')).protocol
        validFinalSessions = @($sessions).Count
        blocks = @($blocks)
        medianDeltas = $summary
        availability = [ordered]@{
            codeCacheUsed = 'NOT_CAPTURED'
            codeCommitted = 'CAPTURED_NMT'
            exactLoadedClassNames = 'NOT_CAPTURED_CLASS_LOAD_LOG_DISABLED'
            histogramObjectClassNames = 'CAPTURED'
            perSiteExecutionCounters = 'NOT_CAPTURED'
        }
        observedV1HistogramClasses = $v1Classes
    }
}

function Get-SamSimpleName {
    param([Parameter(Mandatory)][string]$SiteKey)
    if ($SiteKey -match '#\d+\|[^|]+\|\(\)L([^;]+);') {
        return ($matches[1] -split '/')[-1]
    }
    'UnknownSam'
}

function Get-ActivationReport {
    param(
        [Parameter(Mandatory)]$Census,
        [Parameter(Mandatory)][string]$BuildReportPath,
        [Parameter(Mandatory)][bool]$PublicNames
    )
    $report = Read-Json $BuildReportPath
    $decisions = @(Get-Property (Get-Property $report 'filterSummary' $null) 'frameworkDecisions' @())
    $admitted = @($decisions | Where-Object { [bool](Get-Property $_ 'allowed' $false) })
    $observedClasses = $Census.observedV1HistogramClasses
    $sharedRuntimeObserved = @($observedClasses | Where-Object { $_ -match '(?i)(^|\.)(jmoa\.runtime|com\.yourorg\.jmoa)' }).Count -gt 0
    $sites = foreach ($site in $admitted) {
        $siteKey = [string]$site.siteKey
        $owner = [string]$site.ownerClass
        $ownerDot = $owner.Replace('/', '.')
        $package = if ($ownerDot.Contains('.')) { $ownerDot.Substring(0, $ownerDot.LastIndexOf('.')) } else { '' }
        $sam = Get-SamSimpleName $siteKey
        $adapter = if ([string]::IsNullOrWhiteSpace($package)) {
            "JmoaPkgAdapters`$$sam"
        } else {
            "$package.JmoaPkgAdapters`$$sam"
        }
        $ownerObjectObserved = $observedClasses.Contains($ownerDot)
        $adapterObjectObserved = $observedClasses.Contains($adapter)
        [ordered]@{
            siteId = Get-TextSha256 $siteKey
            ownerId = Get-TextSha256 $ownerDot
            siteKey = if ($PublicNames) { $siteKey } else { $null }
            ownerClass = if ($PublicNames) { $owner } else { $null }
            sam = $sam
            levels = [ordered]@{
                ARTIFACT_ONLY = 'PROVEN_BY_BUILD_REPORT'
                CLASS_LOADED = if ($ownerObjectObserved -or $adapterObjectObserved) {
                    'PROVEN_MINIMUM_BY_LIVE_OBJECT'
                } else { 'NOT_CAPTURED' }
                METHOD_REACHED = 'NOT_CAPTURED'
                TRANSFORMED_PATH_EXECUTED = if ($adapterObjectObserved) {
                    'FAMILY_ADAPTER_OBJECT_OBSERVED_NOT_SITE_EXACT'
                } elseif ($sharedRuntimeObserved) {
                    'SHARED_RUNTIME_OBJECT_OBSERVED_NOT_SITE_EXACT'
                } else { 'NOT_CAPTURED' }
                RUNTIME_OBJECT_OBSERVED = if ($adapterObjectObserved) {
                    'PACKAGE_SAM_ADAPTER_FAMILY'
                } elseif ($sharedRuntimeObserved) {
                    'SHARED_RUNTIME_FAMILY'
                } else { 'NOT_OBSERVED' }
            }
        }
    }
    $rewrittenEvents = [long](Get-Property (Get-Property $report 'modeCRewriteSummary' $null) 'rewrittenSites' 0)
    $plannedSites = [long](Get-Property (Get-Property $report 'weaveSummary' $null) 'plannedSites' 0)
    [ordered]@{
        schemaVersion = 'jmoa-v1-runtime-activation-v1'
        service = $Census.service
        buildReport = [ordered]@{
            sha256 = (Get-FileHash -LiteralPath $BuildReportPath -Algorithm SHA256).Hash
            timestamp = [string](Get-Property $report 'timestamp' '')
            admittedLogicalSiteDecisions = $admitted.Count
            plannedSiteEvents = $plannedSites
            rewrittenSiteEvents = $rewrittenEvents
            reportCoverage = if ($rewrittenEvents -eq $admitted.Count) {
                'ONE_TO_ONE'
            } else {
                'LOGICAL_ADMISSION_DECISIONS_NOT_ONE_TO_ONE_WITH_REWRITE_EVENTS'
            }
            provenanceBoundary = 'Recovered build report matches the accepted optimization lineage by phase and artifact family; the campaign seal did not hash this report as a separate artifact manifest.'
        }
        evidence = [ordered]@{
            exactClassLoadLog = 'NOT_CAPTURED'
            perMethodExecution = 'NOT_CAPTURED'
            perSiteExecutionCounter = 'NOT_CAPTURED'
            postWorkloadClassHistogram = 'CAPTURED_SIX_V1_SESSIONS'
            sharedRuntimeObjectObserved = $sharedRuntimeObserved
        }
        exactActivationRatio = $null
        exactActivationDecision = 'UNMEASURABLE_FROM_EXISTING_CAPTURES'
        sites = @($sites)
        claimBoundary = 'An object proves that class family is live. It does not prove every declaring method or transformed site executed.'
        privacyBoundary = if ($PublicNames) {
            'Public service site and owner names are retained.'
        } else {
            'Private site and owner names are represented by SHA-256 identifiers.'
        }
    }
}

function Write-JsonAndMarkdown {
    param(
        [Parameter(Mandatory)]$Value,
        [Parameter(Mandatory)][string]$BaseName,
        [Parameter(Mandatory)][AllowEmptyString()][string[]]$Markdown
    )
    Write-JmoaJson -Value $Value -Path (Join-Path $OutputDirectory "$BaseName.json")
    Write-JmoaText -Value ($Markdown -join "`n") -Path (Join-Path $OutputDirectory "$BaseName.md")
}

New-JmoaDirectory $OutputDirectory

$doctor = Get-ServiceCensus -Id 'doctor' -Service 'doctor-service' -CampaignRoot $DoctorCampaignRoot
$patient = Get-ServiceCensus -Id 'patient' -Service 'patient-service' -CampaignRoot $PatientCampaignRoot
$petclinic = Get-ServiceCensus -Id 'petclinic' -Service 'spring-petclinic-customers-service' -CampaignRoot $PetClinicCampaignRoot
$services = @($doctor, $patient, $petclinic)

$census = [ordered]@{
    schemaVersion = 'jmoa-v1-runtime-cost-census-v1'
    comparison = 'B0_TO_V1'
    services = @($services | ForEach-Object {
        [ordered]@{
            service = $_.service
            campaignProtocol = $_.campaignProtocol
            validFinalSessions = $_.validFinalSessions
            blocks = $_.blocks
            medianDeltas = $_.medianDeltas
            availability = $_.availability
        }
    })
    interpretation = 'All three unified campaigns show positive median B0-to-V1 PSS. The recurring V1 cost is not explained by one universal category; service-specific deltas and uncertainty remain material.'
}
$censusLines = @(
    '# V1 Runtime-Cost Census', '',
    'All values are direct within-block `V1 - B0` medians from the sealed six-order campaigns.', '',
    '| Service | PSS KB | Heap PSS KB | Anonymous RW PSS KB | Loaded classes | Class loaders | NMT Class KB | NMT metadata KB | NMT Code KB | JMOA objects / bytes | MethodHandle bytes | LambdaForm bytes |',
    '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|'
)
foreach ($service in $services) {
    $m = $service.medianDeltas
    $censusLines += "| $($service.service) | $($m.PssKb.medianDelta) | $($m.HeapPssKb.medianDelta) | $($m.AnonymousRwPssKb.medianDelta) | $($m.LoadedClasses.medianDelta) | $($m.ClassLoaders.medianDelta) | $($m.NmtClassCommittedKb.medianDelta) | $($m.NmtMetadataCommittedKb.medianDelta) | $($m.NmtCodeCommittedKb.medianDelta) | $($m.JmoaObjectInstances.medianDelta) / $($m.JmoaObjectBytes.medianDelta) | $($m.MethodHandleBytes.medianDelta) | $($m.LambdaFormBytes.medianDelta) |"
}
$censusLines += '', 'Code-cache **used** bytes and exact class-load names were not captured in the claim runs; NMT Code committed and live histogram classes are reported instead.'
Write-JsonAndMarkdown -Value $census -BaseName 'v1-runtime-cost-census' -Markdown $censusLines

$activationReports = [ordered]@{
    doctor = Get-ActivationReport -Census $doctor -BuildReportPath $DoctorBuildReport -PublicNames $false
    patient = Get-ActivationReport -Census $patient -BuildReportPath $PatientBuildReport -PublicNames $false
    petclinic = Get-ActivationReport -Census $petclinic -BuildReportPath $PetClinicBuildReport -PublicNames $true
}
foreach ($id in @('doctor', 'patient', 'petclinic')) {
    $activation = $activationReports[$id]
    $lines = @(
        "# V1 Runtime Activation: $($activation.service)", '',
        "- Admitted logical site decisions: ``$($activation.buildReport.admittedLogicalSiteDecisions)``",
        "- Rewritten site events: ``$($activation.buildReport.rewrittenSiteEvents)``",
        "- Exact activation ratio: ``UNMEASURABLE_FROM_EXISTING_CAPTURES``",
        "- Shared JMOA runtime objects observed: ``$($activation.evidence.sharedRuntimeObjectObserved)``", '',
        'The memory campaigns disabled class-load logging and did not capture per-method or per-site execution counters. Live adapter/runtime objects prove family-level use only; they do not prove that every admitted site executed.'
    )
    Write-JsonAndMarkdown -Value $activation -BaseName "v1-runtime-activation-$id" -Markdown $lines
}

$diagnostics = Read-Json (Join-Path $OutputDirectory 'b0-v1-v2-leg-diagnostics.json')
function Get-Leg {
    param([Parameter(Mandatory)]$Service, [Parameter(Mandatory)][string]$Id)
    @($Service.comparisons | Where-Object id -eq $Id)[0]
}
$budgetServices = foreach ($service in $diagnostics.services) {
    $b0v1 = Get-Leg -Service $service -Id 'B0_TO_V1'
    $v1v2 = Get-Leg -Service $service -Id 'V1_TO_V2'
    $b0v2 = Get-Leg -Service $service -Id 'B0_TO_V2'
    $metricBudget = [ordered]@{}
    foreach ($metric in @('pssKb', 'privateDirtyKb', 'memoryCurrentBytes')) {
        $v1 = [double]$b0v1.metrics.$metric.median
        $v2 = [double]$v1v2.metrics.$metric.median
        $direct = [double]$b0v2.metrics.$metric.median
        $metricBudget[$metric] = [ordered]@{
            v1Contribution = $v1
            v2Contribution = $v2
            legSum = $v1 + $v2
            directCompleteProduct = $direct
            interactionVarianceRemainder = $direct - ($v1 + $v2)
        }
    }
    [ordered]@{ service = $service.service; metrics = $metricBudget }
}
$budget = [ordered]@{
    schemaVersion = 'jmoa-v1-v2-memory-budget-v1'
    equation = 'direct complete-product effect = V1 contribution + V2 contribution + interaction/variance remainder'
    services = @($budgetServices)
    warning = 'Medians of paired legs are not algebraically additive. The remainder is diagnostic and combines interaction, order, and sampling variance.'
}
$budgetLines = @('# V1/V2 Memory Budget', '', '| Service | V1 PSS KB | V2 PSS KB | Leg sum KB | Direct B0->V2 KB | Remainder KB |', '|---|---:|---:|---:|---:|---:|')
foreach ($service in $budget.services) {
    $p = $service.metrics.pssKb
    $budgetLines += "| $($service.service) | $($p.v1Contribution) | $($p.v2Contribution) | $($p.legSum) | $($p.directCompleteProduct) | $($p.interactionVarianceRemainder) |"
}
$budgetLines += '', $budget.warning
Write-JsonAndMarkdown -Value $budget -BaseName 'v1-v2-memory-budget' -Markdown $budgetLines

$doctorDelta = $doctor.medianDeltas
$differential = [ordered]@{
    schemaVersion = 'jmoa-doctor-vs-nonwins-v1-cost-model-v1'
    positiveControl = 'doctor-service'
    services = @($services | ForEach-Object {
        $activation = $activationReports[$_.id]
        [ordered]@{
            service = $_.service
            completeProductVerdict = @(
                (Read-Json (Join-Path $OutputDirectory 'b0-v1-v2-final-forensic-matrix.json')).services |
                    Where-Object service -eq $_.service
            )[0].directVerdict
            v1Cost = [ordered]@{
                pssKb = $_.medianDeltas.PssKb.medianDelta
                heapPssKb = $_.medianDeltas.HeapPssKb.medianDelta
                anonymousRwPssKb = $_.medianDeltas.AnonymousRwPssKb.medianDelta
                loadedClasses = $_.medianDeltas.LoadedClasses.medianDelta
                classLoaders = $_.medianDeltas.ClassLoaders.medianDelta
                nmtMetadataCommittedKb = $_.medianDeltas.NmtMetadataCommittedKb.medianDelta
                nmtCodeCommittedKb = $_.medianDeltas.NmtCodeCommittedKb.medianDelta
                jmoaObjectBytes = $_.medianDeltas.JmoaObjectBytes.medianDelta
            }
            activation = [ordered]@{
                admittedLogicalSites = $activation.buildReport.admittedLogicalSiteDecisions
                rewrittenSiteEvents = $activation.buildReport.rewrittenSiteEvents
                exactActivationRatio = $null
                decision = $activation.exactActivationDecision
            }
            memoryBudget = @($budget.services | Where-Object service -eq $_.service)[0].metrics.pssKb
        }
    })
    conclusion = 'Doctor does not demonstrate an independent V1 win. Its V2 reduction is large enough to overcome low-signal V1 overhead; current captures cannot determine whether higher transformed-site activation caused that difference.'
}
$differentialLines = @(
    '# Doctor Versus Non-Wins: V1 Cost Model', '',
    'Doctor is the complete-product positive control, not a standalone V1 positive control.', '',
    '| Service | V1 PSS KB | Heap PSS KB | Anonymous RW KB | Loaded classes | JMOA object bytes | Admitted logical sites | Exact activation | Direct verdict |',
    '|---|---:|---:|---:|---:|---:|---:|---|---|'
)
foreach ($service in $differential.services) {
    $differentialLines += "| $($service.service) | $($service.v1Cost.pssKb) | $($service.v1Cost.heapPssKb) | $($service.v1Cost.anonymousRwPssKb) | $($service.v1Cost.loadedClasses) | $($service.v1Cost.jmoaObjectBytes) | $($service.activation.admittedLogicalSites) | $($service.activation.decision) | $($service.completeProductVerdict) |"
}
$differentialLines += '', $differential.conclusion
Write-JsonAndMarkdown -Value $differential -BaseName 'doctor-vs-nonwins-v1-cost-model' -Markdown $differentialLines

$disposition = [ordered]@{
    schemaVersion = 'jmoa-v1-forensic-disposition-v1'
    currentCampaignVerdictsPreserved = $true
    newPerformanceCampaignAuthorized = $false
    serviceStates = @($services | ForEach-Object {
        [ordered]@{
            service = $_.service
            runtimeDefectProven = $false
            exactMechanismActivationKnown = $false
            state = 'NO_RUNTIME_DEFECT_PROVEN_V1_COST_OBSERVED_ACTIVATION_COVERAGE_UNKNOWN'
            nextAction = 'Product engineering plus a separately labeled MECHANISM_ACTIVATION_STUDY only if exact execution proof is required.'
        }
    })
    productEngineering = @(
        'reduce V1 runtime-library initialization and retained support',
        'generate fewer package/SAM adapter families',
        'add profile-based admission tied to demonstrated production mechanisms',
        'emit per-site execution counters in diagnostic builds',
        'keep reducer-only mode independent from full V1 optimization'
    )
    rerunRule = 'No current B0/V1/V2 result-driven rerun. A bounded correction requires a newly proven artifact, origin, runtime-library, archive, materialization, or workload-contract defect.'
}
Write-JsonAndMarkdown -Value $disposition -BaseName 'v1-forensic-disposition' -Markdown @(
    '# V1 Forensic Disposition', '',
    '- Current campaign verdicts remain unchanged.',
    '- No artifact, runtime-origin, policy, archive, or materialization defect was proven.',
    '- Exact transformed-site activation was not captured; this is an evidence limitation, not proof of a runtime defect.',
    '- No new performance campaign is authorized.',
    '- The next engineering target is V1 overhead and activation-aware admission.', '',
    $disposition.rerunRule
)

$reducerOnly = [ordered]@{
    schemaVersion = 'jmoa-reducer-only-product-path-v1'
    mode = 'JMOA_METADATA_REDUCTION'
    artifact = 'B0R = clean B0 plus raw LocalVariableTable/LocalVariableTypeTable reduction'
    separateFrom = 'JMOA_FULL_OPTIMIZATION = V1 semantic transform plus V2 reducer'
    implementationStatus = 'SUPPORTED_BY_EXISTING_RAW_REDUCER_ENGINE'
    evidenceBoundary = 'Reducer-only evidence must use B0 versus B0R. It cannot inherit full-optimization claims or overwrite the B0/V1/V2 matrix.'
    currentEvidence = @(
        [ordered]@{
            service = 'spring-petclinic-visits-service'
            scope = 'HISTORICAL_PROTOCOL_SCOPED'
            medianPssDeltaKb = -2012
            comparison = 'public baseline versus same baseline plus raw reducer'
        }
    )
}
Write-JsonAndMarkdown -Value $reducerOnly -BaseName 'reducer-only-product-path' -Markdown @(
    '# Reducer-Only Product Path', '',
    '`JMOA Metadata Reduction` is a separate mode: clean B0 plus the raw LVT/LVTT reducer (`B0R`). It avoids V1 runtime support and adapter overhead.', '',
    'It must be evaluated and claimed separately from `JMOA Full Optimization`. The existing public Visits result is historical protocol-scoped evidence for this mode; it is not a three-service complete-product result.'
)

[ordered]@{
    census = 'v1-runtime-cost-census'
    activation = @('v1-runtime-activation-doctor', 'v1-runtime-activation-patient', 'v1-runtime-activation-petclinic')
    differential = 'doctor-vs-nonwins-v1-cost-model'
    budget = 'v1-v2-memory-budget'
    disposition = 'v1-forensic-disposition'
    reducerOnly = 'reducer-only-product-path'
} | ConvertTo-Json -Depth 4
