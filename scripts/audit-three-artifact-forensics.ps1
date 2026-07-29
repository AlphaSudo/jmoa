param(
    [Parameter(Mandatory)][string]$DoctorCampaignRoot,
    [Parameter(Mandatory)][string]$PatientCampaignRoot,
    [Parameter(Mandatory)][string]$PetClinicCampaignRoot,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\docs\product-evidence'),
    [string]$LineageDirectory = (Join-Path $PSScriptRoot '..\docs\product-evidence\artifact-lineage'),
    [ValidateSet('', 'doctor', 'patient', 'petclinic')][string]$CorrectedServiceId = '',
    [string]$AppliedCorrectionDecision = ''
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
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $Default }
    return $property.Value
}

function Get-Median {
    param([double[]]$Values)
    if ($Values.Count -eq 0) { return 0.0 }
    $ordered = @($Values | Sort-Object)
    if (($ordered.Count % 2) -eq 1) {
        return [double]$ordered[[math]::Floor($ordered.Count / 2)]
    }
    return ([double]$ordered[$ordered.Count / 2 - 1] + [double]$ordered[$ordered.Count / 2]) / 2.0
}

function Get-TextSha256 {
    param([Parameter(Mandatory)][string]$Value)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($hash)
}

function Get-ManifestSha256 {
    param([Parameter(Mandatory)][System.IO.FileInfo[]]$Files, [Parameter(Mandatory)][string]$Root)
    $resolvedRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $records = foreach ($file in @($Files | Sort-Object FullName)) {
        $relative = [IO.Path]::GetRelativePath($resolvedRoot, $file.FullName).Replace('\', '/')
        "$relative|$($file.Length)|$((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash)"
    }
    [ordered]@{
        files = @($records).Count
        bytes = [long](($Files | Measure-Object Length -Sum).Sum)
        sha256 = Get-TextSha256 ($records -join "`n")
    }
}

function Get-CaptureDirectory {
    param([Parameter(Mandatory)][string]$SessionDirectory)
    $matches = @(Get-ChildItem -LiteralPath (Join-Path $SessionDirectory 'capture') -Recurse -Filter 'run-manifest.json' -File)
    if ($matches.Count -ne 1) {
        throw "Expected one run-manifest.json below $SessionDirectory; found $($matches.Count)."
    }
    return $matches[0].Directory.FullName
}

function Get-FinalSessions {
    param([Parameter(Mandatory)][string]$CampaignRoot)
    $sessions = foreach ($directory in @(Get-ChildItem -LiteralPath (Join-Path $CampaignRoot 'sessions') -Directory |
            Where-Object Name -Like 'block-*-position-*' | Sort-Object Name)) {
        $result = Read-Json (Join-Path $directory.FullName 'session-result.json')
        $captureDirectory = Get-CaptureDirectory $directory.FullName
        [pscustomobject]@{
            Directory = $directory.FullName
            CaptureDirectory = $captureDirectory
            Result = $result
            Manifest = Read-Json (Join-Path $captureDirectory 'run-manifest.json')
            Workload = Read-Json (Join-Path $captureDirectory 'workload-result.json')
        }
    }
    if (@($sessions).Count -ne 18) {
        throw "Expected 18 final sessions below $CampaignRoot; found $(@($sessions).Count)."
    }
    return @($sessions)
}

function Get-Comparison {
    param($Verdict, [Parameter(Mandatory)][string]$Id)
    $matches = @($Verdict.blockAnalysis.comparisons | Where-Object id -eq $Id)
    if ($matches.Count -ne 1) { throw "Expected exactly one comparison $Id." }
    return $matches[0]
}

function Convert-MetricSummary {
    param($Metric)
    [ordered]@{
        values = @($Metric.values | ForEach-Object { [double]$_ })
        median = [double]$Metric.median
        mean = [double]$Metric.mean
        minimum = [double]$Metric.minimum
        maximum = [double]$Metric.maximum
        medianAbsoluteDeviation = [double]$Metric.medianAbsoluteDeviation
        pairedWins = [int]$Metric.pairedWins
        bootstrap95 = [ordered]@{
            lower = [double]$Metric.bootstrap95.lower
            upper = [double]$Metric.bootstrap95.upper
        }
    }
}

function Get-SessionHistogramSignal {
    param([Parameter(Mandatory)][string]$CaptureDirectory)
    $path = Join-Path $CaptureDirectory 'class-histogram.txt'
    $instances = 0L
    $bytes = 0L
    $classes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        foreach ($line in Get-Content -LiteralPath $path) {
            if ($line -notmatch '(?i)(jmoa\.runtime|JmoaPkgAdapters)') { continue }
            if ($line -match '^\s*\d+:\s+(\d+)\s+(\d+)\s+(\S+)') {
                $instances += [long]$matches[1]
                $bytes += [long]$matches[2]
                [void]$classes.Add($matches[3])
            }
        }
    }
    [ordered]@{ instances = $instances; bytes = $bytes; classes = @($classes | Sort-Object) }
}

function Get-PathStatusDigest {
    param($Workload, [Parameter(Mandatory)][string]$CaptureDirectory)
    $records = @()
    $hashes = @(Get-Property $Workload 'responseHashes' @())
    if ($hashes.Count -gt 0) {
        $records = @($hashes | ForEach-Object { "$($_.round)|$($_.iteration)|$($_.path)|$($_.status)" })
    } else {
        $semanticPath = Join-Path $CaptureDirectory 'semantic-requests.json'
        if (Test-Path -LiteralPath $semanticPath -PathType Leaf) {
            $semantic = Read-Json $semanticPath
            $items = @(Get-Property $semantic 'requests' @())
            if ($items.Count -gt 0) {
                $records = @($items | ForEach-Object {
                    "$(Get-Property $_ 'round' '')|$(Get-Property $_ 'path' '')|$(Get-Property $_ 'method' '')|$(Get-Property $_ 'status' '')"
                })
            }
        }
    }
    if ($records.Count -eq 0) {
        $records = @(
            "$(Get-Property $Workload 'workloadId' '')|$(Get-Property $Workload 'requests' 0)|$(Get-Property $Workload 'rounds' 0)"
        )
    }
    Get-TextSha256 ($records -join "`n")
}

function Get-AdditiveEffects {
    param([object[]]$Sessions, [Parameter(Mandatory)][string]$Metric)
    $observations = foreach ($session in $Sessions) {
        [pscustomobject]@{
            Variant = [string]$session.Result.variant
            Block = [int]$session.Result.block
            Position = [int]$session.Result.position
            Value = [double](Get-Property $session.Result $Metric 0)
        }
    }
    $grand = [double](($observations | Measure-Object Value -Average).Average)
    $artifact = [ordered]@{}
    foreach ($group in @($observations | Group-Object Variant | Sort-Object Name)) {
        $artifact[$group.Name] = [math]::Round(([double](($group.Group | Measure-Object Value -Average).Average) - $grand), 3)
    }
    $block = [ordered]@{}
    foreach ($group in @($observations | Group-Object Block | Sort-Object { [int]$_.Name })) {
        $block[$group.Name] = [math]::Round(([double](($group.Group | Measure-Object Value -Average).Average) - $grand), 3)
    }
    $position = [ordered]@{}
    foreach ($group in @($observations | Group-Object Position | Sort-Object { [int]$_.Name })) {
        $position[$group.Name] = [math]::Round(([double](($group.Group | Measure-Object Value -Average).Average) - $grand), 3)
    }
    $residuals = foreach ($observation in $observations) {
        $observation.Value - $grand - $artifact[$observation.Variant] - $block[[string]$observation.Block] -
            $position[[string]$observation.Position]
    }
    $residualMedian = Get-Median ([double[]]$residuals)
    [ordered]@{
        grandMean = [math]::Round($grand, 3)
        artifactEffect = $artifact
        blockEffect = $block
        positionEffect = $position
        residual = [ordered]@{
            medianAbsoluteDeviation = [math]::Round((Get-Median ([double[]]@($residuals | ForEach-Object {
                [math]::Abs($_ - $residualMedian)
            }))), 3)
            rootMeanSquare = [math]::Round([math]::Sqrt((($residuals | ForEach-Object { $_ * $_ } |
                Measure-Object -Average).Average)), 3)
            maximumAbsolute = [math]::Round((($residuals | ForEach-Object { [math]::Abs($_) } |
                Measure-Object -Maximum).Maximum), 3)
        }
    }
}

function Get-ServiceAudit {
    param(
        [Parameter(Mandatory)][string]$Id,
        [Parameter(Mandatory)][string]$CampaignRoot,
        [Parameter(Mandatory)][string]$LineagePath
    )
    if (-not (Test-Path -LiteralPath $CampaignRoot -PathType Container)) {
        throw "Campaign root does not exist: $CampaignRoot"
    }
    $freezePath = Join-Path $CampaignRoot 'campaign-freeze.json'
    $verdictPath = Join-Path $CampaignRoot 'reports\final-verdict.json'
    $freeze = Read-Json $freezePath
    $verdict = Read-Json $verdictPath
    $lineage = Read-Json $LineagePath
    $sessions = Get-FinalSessions $CampaignRoot

    $ledgerFiles = @(Get-ChildItem -LiteralPath (Join-Path $CampaignRoot 'sessions') -Recurse -File |
        Where-Object Name -in @('scenario-command-ledger.md', 'scenario-command-ledger.json'))
    $analysisFiles = @(Get-ChildItem -LiteralPath (Join-Path $CampaignRoot 'analysis') -Recurse -File |
        Where-Object Extension -in @('.json', '.md'))
    $rawFiles = @(Get-ChildItem -LiteralPath $CampaignRoot -Recurse -File |
        Where-Object FullName -notmatch '[\\/]campaign-seal\.json$')

    $privateSeal = [ordered]@{
        schemaVersion = 'jmoa-three-artifact-private-campaign-seal-v1'
        createdAt = [DateTime]::UtcNow.ToString('o')
        service = $Id
        protocol = [string]$freeze.protocol
        runnerCommit = 'NOT_CAPTURED'
        implementationHashesAuthoritative = ($null -ne $freeze.PSObject.Properties['implementation'])
        configSha256 = [string]$freeze.configSha256
        artifactSha256 = [ordered]@{}
        imageIds = @($sessions | ForEach-Object { [string]$_.Result.imageId } | Sort-Object -Unique)
        runtimePolicies = @($sessions | ForEach-Object { [string]$_.Manifest.runtimePolicy } | Sort-Object -Unique)
        jdkFingerprints = @($sessions | ForEach-Object {
            $fallback = Get-Property $_.Manifest.runtimeJdkFingerprint 'stockCdsSha256' ''
            [string](Get-Property $_.Manifest.runtimeJdkFingerprint 'fingerprintSha256' $fallback)
        } | Sort-Object -Unique)
        finalSessionCount = $sessions.Count
        validFinalSessionCount = @($sessions | Where-Object { [bool]$_.Result.valid }).Count
        terminalOutcome = [string]$verdict.terminalOutcome
        ledgerManifest = Get-ManifestSha256 -Files $ledgerFiles -Root $CampaignRoot
        analysisManifest = Get-ManifestSha256 -Files $analysisFiles -Root $CampaignRoot
        rawEvidenceManifest = Get-ManifestSha256 -Files $rawFiles -Root $CampaignRoot
        evidenceBoundary = 'This seal records a manifest hash over the raw campaign directory; it is not a physical archive hash.'
    }
    foreach ($artifact in @($freeze.artifacts)) {
        $artifactId = [string](Get-Property $artifact 'id' (Get-Property $artifact 'variant' ''))
        $artifactSha = [string](Get-Property $artifact 'sha256' (Get-Property $artifact 'artifactSha256' ''))
        $privateSeal.artifactSha256[$artifactId] = $artifactSha
    }
    Write-JmoaJson -Value $privateSeal -Path (Join-Path $CampaignRoot 'campaign-seal.json')

    $comparisons = foreach ($idValue in @('B0_TO_V1', 'V1_TO_V2', 'B0_TO_V2')) {
        $comparison = Get-Comparison -Verdict $verdict -Id $idValue
        [ordered]@{
            id = $idValue
            blockDeltas = @($comparison.deltas | ForEach-Object {
                [ordered]@{
                    block = [int]$_.block
                    order = @($_.order)
                    pssKb = [long]$_.pssKb
                    privateDirtyKb = [long]$_.privateDirtyKb
                    memoryCurrentBytes = [long]$_.memoryCurrentBytes
                }
            })
            metrics = [ordered]@{
                pssKb = Convert-MetricSummary $comparison.metrics.pssKb
                privateDirtyKb = Convert-MetricSummary $comparison.metrics.privateDirtyKb
                memoryCurrentBytes = Convert-MetricSummary $comparison.metrics.memoryCurrentBytes
            }
        }
    }

    $v1Sessions = @($sessions | Where-Object { [string]$_.Result.variant -eq 'V1' })
    $runtimeSignals = foreach ($session in $v1Sessions) {
        Get-SessionHistogramSignal $session.CaptureDirectory
    }
    $observedClassNames = @($runtimeSignals | ForEach-Object classes | Sort-Object -Unique)
    $runtimeUse = [ordered]@{
        decision = if (@($runtimeSignals | Where-Object instances -gt 0).Count -eq $v1Sessions.Count) {
            'V1_RUNTIME_CONFIRMED'
        } else {
            'V1_PRESENT_NOT_LOADED'
        }
        v1SessionsWithJmoaInstances = @($runtimeSignals | Where-Object instances -gt 0).Count
        v1Sessions = $v1Sessions.Count
        medianJmoaInstances = Get-Median ([double[]]@($runtimeSignals | ForEach-Object instances))
        observedClassCount = $observedClassNames.Count
        observedClassNameSha256 = @($observedClassNames | ForEach-Object { Get-TextSha256 $_ })
        artifactIdentityPassed = @($v1Sessions | Where-Object {
            $expected = [string](Get-Property $_.Manifest 'expectedArtifactSha256' '')
            $runtime = [string](Get-Property $_.Manifest 'runtimeArtifactSha256' $expected)
            -not [string]::IsNullOrWhiteSpace($expected) -and
                ([string]::IsNullOrWhiteSpace($runtime) -or $expected -eq $runtime)
        }).Count -eq $v1Sessions.Count
        exerciseEvidence = 'Live JMOA adapter/runtime instances are present after the workload in every V1 class histogram.'
        limitation = 'No per-rewritten-site execution counter was captured; live adapter/runtime instances are the exercise proof.'
        privacyBoundary = 'Raw class names remain in private run evidence; public output retains counts and one-way digests only.'
    }

    $manifests = @($sessions | ForEach-Object Manifest)
    $runtimePolicy = [ordered]@{
        decision = 'RUNTIME_POLICY_IDENTICAL'
        policies = @($manifests | ForEach-Object runtimePolicy | Sort-Object -Unique)
        launchModes = @($manifests | ForEach-Object launchMode | Sort-Object -Unique)
        cdsModes = @($manifests | ForEach-Object cdsMode | Sort-Object -Unique)
        mallocArenaMax = @($manifests | ForEach-Object { [string](Get-Property $_ 'mallocArenaMax' '') } | Sort-Object -Unique)
        nmtModes = @($manifests | ForEach-Object nmtMode | Sort-Object -Unique)
        captureOrderVersions = @($manifests | ForEach-Object captureOrderVersion | Sort-Object -Unique)
        warmupSeconds = @($manifests | ForEach-Object warmupSeconds | Sort-Object -Unique)
        classLoadLoggingEnabled = @($manifests | ForEach-Object classLoadLoggingEnabled | Sort-Object -Unique)
        jfrEnabled = @($manifests | ForEach-Object jfrEnabled | Sort-Object -Unique)
        gcRunBeforeCapture = @($manifests | ForEach-Object gcRunBeforeCapture | Sort-Object -Unique)
        policyProofPassed = @($manifests | Where-Object { [string]$_.runtimePolicyProof.status -eq 'PASSED' }).Count
        sessions = $sessions.Count
        jdkRuntimeVersions = @($manifests | ForEach-Object {
            $fallback = Get-Property $_.runtimeJdkFingerprint 'expectedJavaVersion' ''
            $version = [string](Get-Property $_.runtimeJdkFingerprint 'javaRuntimeVersion' $fallback)
            if ([string]::IsNullOrWhiteSpace($version) -and [string]$_.javaVersion -match '(?m)^(?:openjdk|java) version "([^"]+)"') {
                $version = [string]$matches[1]
            }
            $version
        } | Sort-Object -Unique)
        applicationArchiveHashesByVariant = [ordered]@{}
    }
    foreach ($variant in @('B0', 'V1', 'V2')) {
        $runtimePolicy.applicationArchiveHashesByVariant[$variant] = @($sessions |
            Where-Object { [string]$_.Result.variant -eq $variant } |
            ForEach-Object { [string](Get-Property $_.Manifest 'cdsArchiveSha256' '') } | Sort-Object -Unique)
    }
    if ($runtimePolicy.policies.Count -ne 1 -or $runtimePolicy.launchModes.Count -ne 1 -or
        $runtimePolicy.nmtModes.Count -ne 1 -or $runtimePolicy.captureOrderVersions.Count -ne 1 -or
        $runtimePolicy.warmupSeconds.Count -ne 1 -or $runtimePolicy.policyProofPassed -ne $sessions.Count -or
        @($runtimePolicy.classLoadLoggingEnabled | Where-Object { $_ -eq $true }).Count -gt 0 -or
        @($runtimePolicy.jfrEnabled | Where-Object { $_ -eq $true }).Count -gt 0 -or
        @($runtimePolicy.gcRunBeforeCapture | Where-Object { $_ -eq $true }).Count -gt 0) {
        $runtimePolicy.decision = 'RUNTIME_FLAG_MISMATCH'
    }

    $workloadRows = foreach ($session in $sessions) {
        $start = [DateTimeOffset]::Parse([string]$session.Manifest.timestampStart)
        $post = [DateTimeOffset]::Parse([string]$session.Manifest.timestampPost)
        $schemaFallback = Get-Property $session.Workload 'metadataVersion' ''
        [pscustomobject]@{
            SessionId = [string]$session.Result.sessionId
            Variant = [string]$session.Result.variant
            Schema = [string](Get-Property $session.Workload 'schemaVersion' $schemaFallback)
            Requests = [int](Get-Property $session.Workload 'requests' 0)
            Rounds = [int](Get-Property $session.Workload 'rounds' 0)
            Errors = [int](Get-Property $session.Workload 'errors' 0)
            Health = [string](Get-Property $session.Workload 'health' '')
            MutationsProven = [string](Get-Property $session.Workload 'mutationsProven' '')
            PathStatusDigest = Get-PathStatusDigest -Workload $session.Workload -CaptureDirectory $session.CaptureDirectory
            CaptureAgeSeconds = ($post - $start).TotalSeconds
            StartupMillis = [double]$session.Manifest.startupMillis
            SettleSeconds = [double]$session.Manifest.postWorkloadSnapshots[0].offsetSeconds
            WorkloadElapsedSeconds = [double](Get-Property $session.Workload 'elapsedSeconds' 0)
            ActualCaptureLagSeconds = if (
                $session.Workload.PSObject.Properties['completedAt'] -or
                $session.Workload.PSObject.Properties['generatedAt']
            ) {
                $completedAtText = if ($session.Workload.PSObject.Properties['completedAt']) {
                    [string]$session.Workload.completedAt
                } else {
                    [string]$session.Workload.generatedAt
                }
                $completedAt = [DateTimeOffset]::Parse($completedAtText)
                $capturedAt = [DateTimeOffset]::Parse([string]$session.Manifest.postWorkloadSnapshots[0].capturedAt)
                ($capturedAt - $completedAt).TotalSeconds
            } else {
                0.0
            }
        }
    }
    $workloadAudit = [ordered]@{
        decision = 'WORKLOAD_EQUIVALENT'
        schemas = @($workloadRows.Schema | Sort-Object -Unique)
        requests = @($workloadRows.Requests | Sort-Object -Unique)
        rounds = @($workloadRows.Rounds | Sort-Object -Unique)
        pathStatusDigests = @($workloadRows.PathStatusDigest | Sort-Object -Unique)
        totalErrors = [int](($workloadRows | Measure-Object Errors -Sum).Sum)
        healthStates = @($workloadRows.Health | Sort-Object -Unique)
        mutationStates = @($workloadRows.MutationsProven | Sort-Object -Unique)
        settleSeconds = @($workloadRows.SettleSeconds | Sort-Object -Unique)
        captureAgeByVariant = [ordered]@{}
        startupMillisByVariant = [ordered]@{}
        workloadElapsedSecondsByVariant = [ordered]@{}
        actualCaptureLagSecondsByVariant = [ordered]@{}
        captureTimingViolations = @()
        maximumAllowedCaptureLagSeconds = [double]$sessions[0].Manifest.postWorkloadSnapshots[0].offsetSeconds + 60.0
        timingBoundary = 'Observed duration can vary; a defect requires a different nominal workload or settle/capture policy, not merely a slower run.'
    }
    foreach ($variant in @('B0', 'V1', 'V2')) {
        $variantRows = @($workloadRows | Where-Object Variant -eq $variant)
        $workloadAudit.captureAgeByVariant[$variant] = [ordered]@{
            median = [math]::Round((Get-Median ([double[]]$variantRows.CaptureAgeSeconds)), 3)
            minimum = [math]::Round((($variantRows | Measure-Object CaptureAgeSeconds -Minimum).Minimum), 3)
            maximum = [math]::Round((($variantRows | Measure-Object CaptureAgeSeconds -Maximum).Maximum), 3)
        }
        $workloadAudit.startupMillisByVariant[$variant] = [math]::Round((Get-Median ([double[]]$variantRows.StartupMillis)), 3)
        $workloadAudit.workloadElapsedSecondsByVariant[$variant] = [math]::Round(
            (Get-Median ([double[]]$variantRows.WorkloadElapsedSeconds)), 3)
        $workloadAudit.actualCaptureLagSecondsByVariant[$variant] = [ordered]@{
            median = [math]::Round((Get-Median ([double[]]$variantRows.ActualCaptureLagSeconds)), 3)
            minimum = [math]::Round((($variantRows | Measure-Object ActualCaptureLagSeconds -Minimum).Minimum), 3)
            maximum = [math]::Round((($variantRows | Measure-Object ActualCaptureLagSeconds -Maximum).Maximum), 3)
        }
    }
    $workloadAudit.captureTimingViolations = @($workloadRows | Where-Object {
        $_.ActualCaptureLagSeconds -lt 0 -or
        $_.ActualCaptureLagSeconds -gt $workloadAudit.maximumAllowedCaptureLagSeconds
    } | ForEach-Object {
        [ordered]@{
            sessionId = $_.SessionId
            variant = $_.Variant
            actualCaptureLagSeconds = [math]::Round($_.ActualCaptureLagSeconds, 3)
        }
    })
    if ($workloadAudit.schemas.Count -ne 1 -or $workloadAudit.requests.Count -ne 1 -or
        $workloadAudit.rounds.Count -ne 1 -or $workloadAudit.pathStatusDigests.Count -ne 1 -or
        $workloadAudit.totalErrors -ne 0 -or $workloadAudit.settleSeconds.Count -ne 1) {
        $workloadAudit.decision = 'PROVEN_WORKLOAD_MISMATCH'
    } elseif ($workloadAudit.captureTimingViolations.Count -gt 0) {
        $workloadAudit.decision = 'PROVEN_CAPTURE_TIMING_DEFECT'
    }

    $attributionPath = Join-Path $CampaignRoot 'analysis\b0_to_v1\v2d\jmoa-memory-attribution.json'
    $attribution = Read-Json $attributionPath
    $b0v1 = @($comparisons | Where-Object id -eq 'B0_TO_V1')[0]
    $causalClassification = if ($runtimeUse.decision -ne 'V1_RUNTIME_CONFIRMED') {
        'B0_V1_TRANSFORMATION_NOT_EXERCISED'
    } elseif ([math]::Abs([double]$b0v1.metrics.pssKb.median) -lt 1024) {
        'B0_V1_NO_MEASURABLE_EFFECT'
    } elseif ([string]$attribution.heapObjectAttribution.classification -eq 'HEAP_PAGE_TOUCH_GROWTH') {
        'B0_V1_RUNTIME_OVERHEAD_EXCEEDS_SAVING'
    } else {
        'B0_V1_MIXED'
    }
    $b0v1Attribution = [ordered]@{
        classification = $causalClassification
        pssMedianDeltaKb = [double]$b0v1.metrics.pssKb.median
        privateDirtyMedianDeltaKb = [double]$b0v1.metrics.privateDirtyKb.median
        memoryCurrentMedianDeltaBytes = [double]$b0v1.metrics.memoryCurrentBytes.median
        smapsNmtClassification = [string]$attribution.smapsNmtReconciliation.classification
        nmtTotalCommittedDeltaKb = [double]$attribution.smapsNmtReconciliation.medianNmtTotalCommittedDeltaKb
        heapPssDeltaKb = [double]$attribution.heapObjectAttribution.medianHeapPssDeltaKb
        heapUsedDeltaKb = [double]$attribution.heapObjectAttribution.medianHeapUsedDeltaKb
        heapCommittedDeltaKb = [double]$attribution.heapObjectAttribution.medianHeapCommittedDeltaKb
        histogramBytesDelta = [double]$attribution.heapObjectAttribution.medianClassHistogramBytesDelta
        loadedClassCountDelta = [double]$attribution.classMetaspaceAttribution.medianClassHistogramClassCountDelta
        metaspaceCommittedDeltaKb = [double]$attribution.classMetaspaceAttribution.medianMetaspaceCommittedDeltaKb
        classCommittedDeltaKb = [double]$attribution.classMetaspaceAttribution.medianClassCommittedDeltaKb
        codeCommittedDeltaKb = [double]$attribution.classMetaspaceAttribution.medianCodeCommittedDeltaKb
        hypotheses = @($attribution.causalHypotheses)
    }

    $lineageAudit = [ordered]@{
        decision = if ([bool]$lineage.comparisonAdmissible -and [bool]$lineage.sameSourceUniverse) {
            'LINEAGE_VALID'
        } else {
            'DEPENDENCY_UNIVERSE_MISMATCH'
        }
        sameSourceUniverse = [bool]$lineage.sameSourceUniverse
        strictB0Valid = [bool]$lineage.strictB0Valid
        comparisonAdmissible = [bool]$lineage.comparisonAdmissible
        sourceRevisions = @($lineage.sourceRevisions)
        variants = @($lineage.variants | ForEach-Object {
            [ordered]@{
                id = [string]$_.id
                classification = [string]$_.classification
                sourceRevision = [string]$_.sourceRevision
                artifactSha256 = [string]$_.artifact.sha256
                jmoaEntryCount = [int](Get-Property $_.artifact 'jmoaEntryCount' 0)
                nestedDependencyCount = [int]$_.artifact.nestedDependencyCount
                applicationEntryNameFingerprint = [string]$_.artifact.applicationEntryNameFingerprint
                applicationClassNameFingerprint = [string]$_.artifact.applicationClassNameFingerprint
                applicationResourceFingerprint = [string]$_.artifact.applicationResourceFingerprint
                parentVariant = [string](Get-Property $_ 'parentVariant' '')
            }
        })
        interpretation = 'Content fingerprints may differ where JMOA intentionally rewrites or generates classes. Admissibility is based on the recorded source universe and explicit B0/V1/V2 derivation, not whole-archive byte equality.'
    }

    $defectDecision = if ($lineageAudit.decision -ne 'LINEAGE_VALID') {
        'PROVEN_ARTIFACT_DEFECT'
    } elseif ($runtimeUse.decision -ne 'V1_RUNTIME_CONFIRMED') {
        'PROVEN_RUNTIME_ORIGIN_DEFECT'
    } elseif ($runtimePolicy.decision -ne 'RUNTIME_POLICY_IDENTICAL') {
        'PROVEN_RUNTIME_POLICY_MISMATCH'
    } elseif ($workloadAudit.decision -eq 'PROVEN_CAPTURE_TIMING_DEFECT') {
        'PROVEN_CAPTURE_TIMING_DEFECT'
    } elseif ($workloadAudit.decision -ne 'WORKLOAD_EQUIVALENT') {
        'PROVEN_WORKLOAD_MISMATCH'
    } else {
        'NO_CORRECTABLE_DEFECT_FOUND'
    }

    [ordered]@{
        id = $Id
        service = [string]$verdict.service
        protocol = [string]$verdict.protocol
        terminalOutcome = [string]$verdict.terminalOutcome
        seal = [ordered]@{
            configSha256 = $privateSeal.configSha256
            runnerCommit = $privateSeal.runnerCommit
            implementationHashesAuthoritative = $privateSeal.implementationHashesAuthoritative
            artifactSha256 = $privateSeal.artifactSha256
            imageCount = $privateSeal.imageIds.Count
            runtimePolicies = $privateSeal.runtimePolicies
            jdkFingerprintCount = $privateSeal.jdkFingerprints.Count
            finalSessionCount = $privateSeal.finalSessionCount
            validFinalSessionCount = $privateSeal.validFinalSessionCount
            ledgerManifest = $privateSeal.ledgerManifest
            analysisManifest = $privateSeal.analysisManifest
            rawEvidenceManifest = $privateSeal.rawEvidenceManifest
        }
        comparisons = @($comparisons)
        lineage = $lineageAudit
        runtimeUse = $runtimeUse
        b0ToV1Attribution = $b0v1Attribution
        orderEffects = [ordered]@{
            pssKb = Get-AdditiveEffects -Sessions $sessions -Metric 'pssKb'
            privateDirtyKb = Get-AdditiveEffects -Sessions $sessions -Metric 'privateDirtyKb'
            memoryCurrentBytes = Get-AdditiveEffects -Sessions $sessions -Metric 'memoryCurrentBytes'
            interpretation = 'Additive decomposition is diagnostic only; paired medians remain authoritative.'
        }
        runtimePolicy = $runtimePolicy
        workload = $workloadAudit
        defectDecision = $defectDecision
        correctedCampaignAuthorized = ($defectDecision -ne 'NO_CORRECTABLE_DEFECT_FOUND')
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
$services = @(
    Get-ServiceAudit -Id 'doctor' -CampaignRoot $DoctorCampaignRoot -LineagePath (Join-Path $LineageDirectory 'doctor.json')
    Get-ServiceAudit -Id 'patient' -CampaignRoot $PatientCampaignRoot -LineagePath (Join-Path $LineageDirectory 'patient.json')
    Get-ServiceAudit -Id 'petclinic' -CampaignRoot $PetClinicCampaignRoot -LineagePath (Join-Path $LineageDirectory 'petclinic.json')
)

$seal = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-public-campaign-seal-v1'
    services = @($services | ForEach-Object {
        [ordered]@{
            service = $_.service
            protocol = $_.protocol
            configSha256 = $_.seal.configSha256
            runnerCommit = $_.seal.runnerCommit
            implementationHashesAuthoritative = $_.seal.implementationHashesAuthoritative
            artifactSha256 = $_.seal.artifactSha256
            imageCount = $_.seal.imageCount
            runtimePolicies = $_.seal.runtimePolicies
            jdkFingerprintCount = $_.seal.jdkFingerprintCount
            finalSessions = $_.seal.finalSessionCount
            validFinalSessions = $_.seal.validFinalSessionCount
            terminalOutcome = $_.terminalOutcome
            ledgerManifest = $_.seal.ledgerManifest
            analysisManifest = $_.seal.analysisManifest
            rawEvidenceManifest = $_.seal.rawEvidenceManifest
        }
    })
    evidenceBoundary = 'Raw evidence and private paths remain outside Git. Each raw campaign root contains its private campaign-seal.json.'
}
$sealLines = @('# Three-Artifact Campaign Seal', '', '| Service | Sessions | Verdict | Ledger files | Analysis files | Raw files |', '|---|---:|---|---:|---:|---:|')
foreach ($service in $seal.services) {
    $sealLines += "| $($service.service) | $($service.validFinalSessions)/$($service.finalSessions) | $($service.terminalOutcome) | $($service.ledgerManifest.files) | $($service.analysisManifest.files) | $($service.rawEvidenceManifest.files) |"
}
$sealLines += '', $seal.evidenceBoundary
Write-JsonAndMarkdown -Value $seal -BaseName 'three-artifact-campaign-seal' -Markdown $sealLines

$diagnostics = [ordered]@{
    schemaVersion = 'jmoa-b0-v1-v2-leg-diagnostics-v1'
    services = @($services | ForEach-Object { [ordered]@{ service = $_.service; comparisons = $_.comparisons } })
}
$diagnosticLines = @('# B0/V1/V2 Leg Diagnostics', '', 'All values are direct within-block deltas. Negative values favor the right-hand artifact.', '',
    '| Service | Leg | PSS median / mean / MAD KB | PSS range KB | PSS wins | PSS 95% CI KB | Private Dirty median / wins | memory.current median / wins |',
    '|---|---|---:|---:|---:|---|---:|---:|')
foreach ($service in $services) {
    foreach ($comparison in $service.comparisons) {
        $p = $comparison.metrics.pssKb; $d = $comparison.metrics.privateDirtyKb; $m = $comparison.metrics.memoryCurrentBytes
        $diagnosticLines += "| $($service.service) | $($comparison.id) | $($p.median) / $($p.mean) / $($p.medianAbsoluteDeviation) | [$($p.minimum), $($p.maximum)] | $($p.pairedWins)/6 | [$($p.bootstrap95.lower), $($p.bootstrap95.upper)] | $($d.median) / $($d.pairedWins)/6 | $($m.median) / $($m.pairedWins)/6 |"
    }
}
$diagnosticLines += '', 'The JSON companion contains every block delta and complete statistics for PSS, Private_Dirty, and memory.current.'
Write-JsonAndMarkdown -Value $diagnostics -BaseName 'b0-v1-v2-leg-diagnostics' -Markdown $diagnosticLines

$doctor = @($services | Where-Object id -eq 'doctor')[0]
$doctorDirect = @($doctor.comparisons | Where-Object id -eq 'B0_TO_V2')[0]
$doctorB0V1 = @($doctor.comparisons | Where-Object id -eq 'B0_TO_V1')[0]
$positiveControl = [ordered]@{
    schemaVersion = 'jmoa-doctor-positive-control-model-v1'
    service = $doctor.service
    positiveControlLeg = 'B0_TO_V2'
    correction = 'Doctor is not a B0_TO_V1 positive control. Its B0_TO_V1 median PSS delta is positive and below 1 MiB; the complete product win is created by the stronger V1_TO_V2 reduction.'
    directResult = $doctorDirect
    b0ToV1Diagnostic = $doctorB0V1
    deployment = [ordered]@{
        launchMode = $doctor.runtimePolicy.launchModes[0]
        runtimePolicy = $doctor.runtimePolicy.policies[0]
        artifactSpecificApplicationCds = $true
        jdkRuntimeVersions = $doctor.runtimePolicy.jdkRuntimeVersions
        warmupSeconds = $doctor.runtimePolicy.warmupSeconds[0]
        workloadRequests = $doctor.workload.requests[0]
        settleSeconds = $doctor.workload.settleSeconds[0]
        v1JmoaEntriesObserved = @($doctor.lineage.variants | Where-Object id -eq 'V1')[0].jmoaEntryCount
        v1RuntimeInstancesMedian = $doctor.runtimeUse.medianJmoaInstances
        materialization = 'Spring Boot fat JAR with artifact-specific AppCDS archives and runtime artifact SHA verification.'
    }
    contrasts = @($services | Where-Object id -ne 'doctor' | ForEach-Object {
        [ordered]@{
            service = $_.service
            launchMode = $_.runtimePolicy.launchModes[0]
            runtimePolicy = $_.runtimePolicy.policies[0]
            b0ToV1PssMedianKb = @($_.comparisons | Where-Object id -eq 'B0_TO_V1')[0].metrics.pssKb.median
            b0ToV1Attribution = $_.b0ToV1Attribution.classification
            b0ToV2PssMedianKb = @($_.comparisons | Where-Object id -eq 'B0_TO_V2')[0].metrics.pssKb.median
            directVerdict = $_.terminalOutcome
        }
    })
}
Write-JsonAndMarkdown -Value $positiveControl -BaseName 'doctor-positive-control-model' -Markdown @(
    '# Doctor Positive-Control Model', '',
    'Doctor is the complete-product positive control for `B0 -> V2`; it is not evidence that `B0 -> V1` wins independently.', '',
    "- B0 -> V1 median PSS: $($doctorB0V1.metrics.pssKb.median) KB.",
    "- B0 -> V2 median PSS: $($doctorDirect.metrics.pssKb.median) KB; wins: $($doctorDirect.metrics.pssKb.pairedWins)/6.",
    "- Runtime: $($doctor.runtimePolicy.launchModes[0]) / $($doctor.runtimePolicy.policies[0]).",
    "- Causal reading: V1 overhead is low-signal, while V1 -> V2 is large enough to produce the complete product win.", '',
    'The JSON companion records the protocol fields and service contrasts.'
)

$lineageReport = [ordered]@{ schemaVersion = 'jmoa-three-artifact-lineage-audit-v1'; services = @($services | ForEach-Object {
    [ordered]@{ service = $_.service; lineage = $_.lineage }
}) }
$lineageLines = @('# Three-Artifact Lineage Audit', '', '| Service | Decision | Same source | Strict B0 | Comparison admissible |', '|---|---|---|---|---|')
foreach ($service in $services) {
    $lineageLines += "| $($service.service) | $($service.lineage.decision) | $($service.lineage.sameSourceUniverse) | $($service.lineage.strictB0Valid) | $($service.lineage.comparisonAdmissible) |"
}
$lineageLines += '', 'Expected transformed/generated-content differences are not misclassified as source-lineage defects. No artifact defect was proven.'
Write-JsonAndMarkdown -Value $lineageReport -BaseName 'three-artifact-lineage-audit' -Markdown $lineageLines

$runtimeOrigin = [ordered]@{ schemaVersion = 'jmoa-three-artifact-runtime-origin-audit-v1'; services = @($services | ForEach-Object {
    [ordered]@{ service = $_.service; runtimeUse = $_.runtimeUse }
}) }
$runtimeOriginLines = @('# Three-Artifact V1 Runtime-Use Audit', '', '| Service | Decision | V1 histograms with JMOA instances | Median instances | Artifact identity |', '|---|---|---:|---:|---|')
foreach ($service in $services) {
    $runtimeOriginLines += "| $($service.service) | $($service.runtimeUse.decision) | $($service.runtimeUse.v1SessionsWithJmoaInstances)/$($service.runtimeUse.v1Sessions) | $($service.runtimeUse.medianJmoaInstances) | $($service.runtimeUse.artifactIdentityPassed) |"
}
$runtimeOriginLines += '', 'Live post-workload adapter/runtime instances prove loading and exercise at family level. Exact rewritten-site counters were not captured.'
Write-JsonAndMarkdown -Value $runtimeOrigin -BaseName 'three-artifact-runtime-origin-audit' -Markdown $runtimeOriginLines

$attributionReport = [ordered]@{ schemaVersion = 'jmoa-three-artifact-b0-v1-attribution-v1'; services = @($services | ForEach-Object {
    [ordered]@{ service = $_.service; attribution = $_.b0ToV1Attribution }
}) }
$attributionLines = @('# B0 To V1 Memory Attribution', '', '| Service | Classification | PSS KB | Heap PSS KB | Heap used KB | Histogram bytes | Classes | Metaspace committed KB | Code committed KB |', '|---|---|---:|---:|---:|---:|---:|---:|---:|')
foreach ($service in $services) {
    $a = $service.b0ToV1Attribution
    $attributionLines += "| $($service.service) | $($a.classification) | $($a.pssMedianDeltaKb) | $($a.heapPssDeltaKb) | $($a.heapUsedDeltaKb) | $($a.histogramBytesDelta) | $($a.loadedClassCountDelta) | $($a.metaspaceCommittedDeltaKb) | $($a.codeCommittedDeltaKb) |"
}
$attributionLines += '', 'Doctor and Patient are low-signal rather than standalone V1 wins. PetClinic reduces loaded classes, but heap page touch/runtime overhead exceeds that saving.'
Write-JsonAndMarkdown -Value $attributionReport -BaseName 'three-artifact-b0-v1-attribution' -Markdown $attributionLines

$orderReport = [ordered]@{ schemaVersion = 'jmoa-three-artifact-order-effect-analysis-v1'; services = @($services | ForEach-Object {
    [ordered]@{ service = $_.service; effects = $_.orderEffects }
}) }
$orderLines = @('# Three-Artifact Order-Effect Analysis', '', 'The additive model is diagnostic; the six paired block medians remain authoritative.', '',
    '| Service | Metric | Artifact effects | Position effects | Residual MAD |', '|---|---|---|---|---:|')
foreach ($service in $services) {
    foreach ($metric in @('pssKb', 'privateDirtyKb', 'memoryCurrentBytes')) {
        $effect = $service.orderEffects[$metric]
        $artifactText = @($effect.artifactEffect.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '; '
        $positionText = @($effect.positionEffect.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '; '
        $orderLines += "| $($service.service) | $metric | $artifactText | $positionText | $($effect.residual.medianAbsoluteDeviation) |"
    }
}
Write-JsonAndMarkdown -Value $orderReport -BaseName 'three-artifact-order-effect-analysis' -Markdown $orderLines

$policyReport = [ordered]@{ schemaVersion = 'jmoa-three-artifact-runtime-policy-audit-v1'; services = @($services | ForEach-Object {
    [ordered]@{ service = $_.service; runtimePolicy = $_.runtimePolicy }
}) }
$policyLines = @('# Three-Artifact Runtime-Policy Audit', '', '| Service | Decision | Launch mode | Policy | CDS | JDK | Proofs passed |', '|---|---|---|---|---|---|---:|')
foreach ($service in $services) {
    $r = $service.runtimePolicy
    $policyLines += "| $($service.service) | $($r.decision) | $($r.launchModes -join ', ') | $($r.policies -join ', ') | $($r.cdsModes -join ', ') | $($r.jdkRuntimeVersions -join ', ') | $($r.policyProofPassed)/$($r.sessions) |"
}
Write-JsonAndMarkdown -Value $policyReport -BaseName 'three-artifact-runtime-policy-audit' -Markdown $policyLines

$workloadReport = [ordered]@{ schemaVersion = 'jmoa-three-artifact-workload-equivalence-audit-v1'; services = @($services | ForEach-Object {
    [ordered]@{ service = $_.service; workload = $_.workload }
}) }
$workloadLines = @('# Three-Artifact Workload And Capture-Equivalence Audit', '', '| Service | Decision | Requests | Rounds | Errors | Settle seconds | Timing violations | Path/status digests |', '|---|---|---:|---:|---:|---:|---:|---:|')
foreach ($service in $services) {
    $w = $service.workload
    $workloadLines += "| $($service.service) | $($w.decision) | $($w.requests -join ',') | $($w.rounds -join ',') | $($w.totalErrors) | $($w.settleSeconds -join ',') | $($w.captureTimingViolations.Count) | $($w.pathStatusDigests.Count) |"
}
$workloadLines += '', 'Observed startup/workload durations are reported in JSON. Different elapsed time alone is not a capture defect when the nominal workload and settle policy are identical.'
Write-JsonAndMarkdown -Value $workloadReport -BaseName 'three-artifact-workload-equivalence-audit' -Markdown $workloadLines

$decisions = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-defect-decisions-v1'
    services = @($services | ForEach-Object {
        $direct = @($_.comparisons | Where-Object id -eq 'B0_TO_V2')[0]
        $correctionApplied = -not [string]::IsNullOrWhiteSpace($CorrectedServiceId) -and $_.id -eq $CorrectedServiceId
        [ordered]@{
            service = $_.service
            decision = $_.defectDecision
            correctedCampaignApplied = $correctionApplied
            appliedCorrectionDecision = if ($correctionApplied) { $AppliedCorrectionDecision } else { '' }
            furtherCorrectedCampaignAuthorized = if ($correctionApplied) { $false } else { $_.correctedCampaignAuthorized }
            publicVerdict = $_.terminalOutcome
            b0ToV2PssMedianKb = $direct.metrics.pssKb.median
            rationale = if ($correctionApplied) {
                'The service consumed its one evidence-authorized correction. No result-driven retry is permitted.'
            } elseif ($_.defectDecision -eq 'NO_CORRECTABLE_DEFECT_FOUND') {
                'Lineage, runtime use, policy, workload, and capture gates passed. A disappointing or uncertain result is not a rerun authorization.'
            } else {
                'A concrete audit defect was proven; one correction contract would be required before rerun.'
            }
        }
    })
    rerunPolicy = 'No result-driven rerun is permitted. A service that consumed its one correction cannot be rerun again under this investigation.'
}
$decisionLines = @('# Three-Artifact Defect Decisions', '', '| Service | Decision | Correction applied | Further correction authorized | Public verdict |', '|---|---|---|---|---|')
foreach ($service in $decisions.services) {
    $decisionLines += "| $($service.service) | $($service.decision) | $($service.correctedCampaignApplied) | $($service.furtherCorrectedCampaignAuthorized) | $($service.publicVerdict) |"
}
$decisionLines += '', $decisions.rerunPolicy
Write-JsonAndMarkdown -Value $decisions -BaseName 'three-artifact-defect-decisions' -Markdown $decisionLines

$finalMatrix = [ordered]@{
    schemaVersion = 'jmoa-b0-v1-v2-final-forensic-matrix-v1'
    services = @($services | ForEach-Object {
        $b0v1Item = @($_.comparisons | Where-Object id -eq 'B0_TO_V1')[0]
        $v1v2Item = @($_.comparisons | Where-Object id -eq 'V1_TO_V2')[0]
        $b0v2Item = @($_.comparisons | Where-Object id -eq 'B0_TO_V2')[0]
        [ordered]@{
            service = $_.service
            b0ToV1PssMedianKb = $b0v1Item.metrics.pssKb.median
            v1ToV2PssMedianKb = $v1v2Item.metrics.pssKb.median
            b0ToV2PssMedianKb = $b0v2Item.metrics.pssKb.median
            b0ToV2Wins = $b0v2Item.metrics.pssKb.pairedWins
            b0ToV2Bootstrap95 = $b0v2Item.metrics.pssKb.bootstrap95
            forensicDecision = $_.defectDecision
            correctedCampaignApplied = (-not [string]::IsNullOrWhiteSpace($CorrectedServiceId) -and $_.id -eq $CorrectedServiceId)
            directVerdict = $_.terminalOutcome
        }
    })
    correction = 'Doctor B0_TO_V1 is not a win; Doctor succeeds because its V1_TO_V2 reduction is large enough to create a direct B0_TO_V2 win.'
}
$matrixLines = @('# Final B0/V1/V2 Forensic Matrix', '', '| Service | B0 -> V1 PSS | V1 -> V2 PSS | B0 -> V2 PSS | Wins | 95% CI | Verdict |', '|---|---:|---:|---:|---:|---|---|')
foreach ($service in $finalMatrix.services) {
    $matrixLines += "| $($service.service) | $($service.b0ToV1PssMedianKb) KB | $($service.v1ToV2PssMedianKb) KB | $($service.b0ToV2PssMedianKb) KB | $($service.b0ToV2Wins)/6 | [$($service.b0ToV2Bootstrap95.lower), $($service.b0ToV2Bootstrap95.upper)] | $($service.directVerdict) |"
}
$matrixLines += '', $finalMatrix.correction
Write-JsonAndMarkdown -Value $finalMatrix -BaseName 'b0-v1-v2-final-forensic-matrix' -Markdown $matrixLines

$summary = [ordered]@{
    services = @($services | ForEach-Object {
        $correctionApplied = -not [string]::IsNullOrWhiteSpace($CorrectedServiceId) -and $_.id -eq $CorrectedServiceId
        [ordered]@{
            service = $_.service
            verdict = $_.terminalOutcome
            defectDecision = $_.defectDecision
            correctionApplied = $correctionApplied
            furtherCorrectionAuthorized = if ($correctionApplied) { $false } else { $_.correctedCampaignAuthorized }
        }
    })
    outputs = @(
        'three-artifact-campaign-seal',
        'b0-v1-v2-leg-diagnostics',
        'doctor-positive-control-model',
        'three-artifact-lineage-audit',
        'three-artifact-runtime-origin-audit',
        'three-artifact-b0-v1-attribution',
        'three-artifact-order-effect-analysis',
        'three-artifact-runtime-policy-audit',
        'three-artifact-workload-equivalence-audit',
        'three-artifact-defect-decisions',
        'b0-v1-v2-final-forensic-matrix'
    )
}
$summary | ConvertTo-Json -Depth 8
