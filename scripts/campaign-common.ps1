<#
.SYNOPSIS
    Shared helpers for the PetClinic performance campaign:
      - Memory parsing from screen-pair run directories (smaps_rollup + memory.current)
      - Hard artifact gates (Step 3): B0-clean and V2-transformed assertions against the running images
      - Semantic equivalence comparison (Step 4): per-request status + normalized body-hash divergence
      - Median / statistics helpers

    Dot-sourced by run-petclinic-performance-campaign.ps1. Depends on runtime-automation-common.ps1
    (dot-source that first).
#>
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-canonical-json.ps1')

# StrictMode-safe optional property read (JSON objects may legitimately omit fields).
function Get-CampaignJsonProp {
    param($Object, [string]$Name)
    if ($null -eq $Object) { return $null }
    $prop = $Object.PSObject.Properties[$Name]
    if ($prop) { return $prop.Value }
    return $null
}

function Get-CampaignMedian {
    param([double[]]$Values)
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    if ($sorted.Count % 2) { return $sorted[[int][math]::Floor($sorted.Count / 2)] }
    return ($sorted[$sorted.Count / 2 - 1] + $sorted[$sorted.Count / 2]) / 2
}

function Get-CampaignSmapsMetric {
    param([Parameter(Mandatory)][string]$Text, [Parameter(Mandatory)][string]$Name)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB")
    if (-not $match.Success) { throw "Metric $Name missing from smaps_rollup." }
    return [long]$match.Groups[1].Value
}

# Reads a single screen-pair run directory (b{N}/c{N}) and returns the parsed memory triple
# plus the run identity, exactly as V2-C consumes it.
function Read-CampaignRunMemory {
    param([Parameter(Mandatory)][string]$RunDirectory)
    $rollupPath = Join-Path $RunDirectory 'smaps_rollup.txt'
    $memoryCurrentPath = Join-Path $RunDirectory 'memory.current'
    $manifestPath = Join-Path $RunDirectory 'run-manifest.json'
    foreach ($required in @($rollupPath, $memoryCurrentPath, $manifestPath)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "Screen-pair run directory is missing $($required): the arm did not capture cleanly."
        }
    }
    $rollup = Get-Content -Raw -LiteralPath $rollupPath
    $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    return [ordered]@{
        runId              = $manifest.runId
        variant            = $manifest.variant
        pairIndex          = $manifest.pairIndex
        pssKb              = Get-CampaignSmapsMetric -Text $rollup -Name 'Pss'
        privateDirtyKb     = Get-CampaignSmapsMetric -Text $rollup -Name 'Private_Dirty'
        memoryCurrentBytes = [long]((Get-Content -Raw -LiteralPath $memoryCurrentPath).Trim())
        runDirectory       = $RunDirectory
    }
}

# Executes a shell command inside a transient container spawned from an immutable image, so the
# assertion is made against exactly what the running image contains (not a host-side directory).
function Invoke-CampaignImageProbe {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$Image,
        [Parameter(Mandatory)][string]$ShellCommand,
        [string]$LedgerDirectory = '',
        [string]$Step = 'image artifact probe'
    )
    # PowerShell here-strings use CRLF on Windows. Passing those bytes to Linux `sh -lc`
    # leaves a trailing CR in command names and arguments (`sort\r`, `cut -f1\r`).
    $linuxShellCommand = $ShellCommand -replace "`r`n", "`n"
    if ([string]::IsNullOrWhiteSpace($LedgerDirectory)) {
        $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('run', '--rm', '--entrypoint', 'sh', $Image, '-lc', $linuxShellCommand)
    } else {
        $result = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('run', '--rm', '--entrypoint', 'sh', $Image, '-lc', $linuxShellCommand) `
            -LedgerDirectory $LedgerDirectory -Step $Step
    }
    if ($result.exitCode -ne 0) {
        throw "Image probe failed for $Image (exit $($result.exitCode)): $($result.output)"
    }
    return $result.output.Trim()
}

# Step 3 (B0): the running baseline image must contain NO JMOA transformation evidence.
function Test-CampaignBaselineClean {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$Image,
        [string]$LedgerDirectory = ''
    )
    $jmoaJars = [int](Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -name '*-jmoa.jar' 2>/dev/null | wc -l" -LedgerDirectory $LedgerDirectory -Step 'baseline gate: reduced JMOA jars')
    $runtimeLib = [int](Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -name 'jmoa-runtime-lib-*.jar' 2>/dev/null | wc -l" -LedgerDirectory $LedgerDirectory -Step 'baseline gate: runtime library jars')
    $materialization = [int](Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -name 'jmoa-materialization-manifest.json' 2>/dev/null | wc -l" -LedgerDirectory $LedgerDirectory -Step 'baseline gate: materialization manifests')
    $jmoaRuntimeClasses = [int](Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -path '*BOOT-INF/classes/jmoa/*' 2>/dev/null | wc -l" -LedgerDirectory $LedgerDirectory -Step 'baseline gate: JMOA runtime classes')
    $agentRefs = [int](Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -name 'MANIFEST.MF' 2>/dev/null -exec grep -l -i 'jmoa\|Launcher-Agent-Class' {} \; 2>/dev/null | wc -l" -LedgerDirectory $LedgerDirectory -Step 'baseline gate: manifest agent references')
    $reasons = New-Object System.Collections.Generic.List[string]
    if ($jmoaJars -ne 0) { $reasons.Add("baseline image contains $jmoaJars *-jmoa.jar file(s)") | Out-Null }
    if ($runtimeLib -ne 0) { $reasons.Add("baseline image contains $runtimeLib jmoa-runtime-lib jar(s)") | Out-Null }
    if ($materialization -ne 0) { $reasons.Add("baseline image contains a jmoa materialization manifest") | Out-Null }
    if ($jmoaRuntimeClasses -ne 0) { $reasons.Add("baseline image carries $jmoaRuntimeClasses materialized BOOT-INF/classes/jmoa entries") | Out-Null }
    if ($agentRefs -ne 0) { $reasons.Add("baseline image manifest references a JMOA runtime/javaagent") | Out-Null }
    return [ordered]@{
        arm                     = 'BASELINE'
        image                   = $Image
        jmoaReducedJarCount     = $jmoaJars
        runtimeLibraryJarCount  = $runtimeLib
        materializationManifest = $materialization
        jmoaRuntimeClassCount   = $jmoaRuntimeClasses
        manifestAgentReferences = $agentRefs
        passed                  = ($reasons.Count -eq 0)
        reasons                 = @($reasons)
    }
}

# Reads one immutable image and fingerprints its Spring Boot layered artifact in a SINGLE probe: every
# BOOT-INF/lib/*.jar (name -> SHA-256), the BOOT-INF/classes tree fingerprint, and the Spring Boot
# loader fingerprint. Used to verify all 24 reduced dependencies plus the non-replaced layer.
function Get-CampaignImageArtifactFingerprints {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$Image,
        [string]$LedgerDirectory = '',
        [string]$Step = 'image artifact fingerprints'
    )
    $probe = @'
echo "===LIBS==="
find / -path "*BOOT-INF/lib/*.jar" 2>/dev/null -exec sha256sum {} \; | sort
echo "===APPCLASSES==="
find / -path "*BOOT-INF/classes/*" -type f 2>/dev/null -exec sha256sum {} \; | sort | sha256sum | cut -d" " -f1
echo "===LOADER==="
find / -path "*org/springframework/boot/loader/*" -type f 2>/dev/null -exec sha256sum {} \; | sort | sha256sum | cut -d" " -f1
echo "===END==="
'@
    $output = Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand $probe -LedgerDirectory $LedgerDirectory -Step $Step
    $libs = @{}
    $libPaths = @{}
    $appClassesFingerprint = ''
    $loaderFingerprint = ''
    $libraryPattern = '(?m)^([0-9a-fA-F]{64})\s+(.+BOOT-INF/lib/[^/\r\n]+\.jar)\r?$'
    $libraryMatches = [regex]::Matches($output, $libraryPattern)
    Write-Verbose "Image fingerprint probe for $Image returned $($output.Length) characters and $($libraryMatches.Count) library records."
    foreach ($libraryMatch in $libraryMatches) {
        $path = $libraryMatch.Groups[2].Value.Trim()
        $name = [IO.Path]::GetFileName($path)
        $libs[$name] = $libraryMatch.Groups[1].Value.ToUpperInvariant()
        $libPaths[$name] = $path
    }
    $appMatch = [regex]::Match($output, '(?ms)^===APPCLASSES===\r?\n([0-9a-fA-F]{64})\s*$')
    if ($appMatch.Success) {
        $appClassesFingerprint = $appMatch.Groups[1].Value.ToUpperInvariant()
    }
    $loaderMatch = [regex]::Match($output, '(?ms)^===LOADER===\r?\n([0-9a-fA-F]{64})\s*$')
    if ($loaderMatch.Success) {
        $loaderFingerprint = $loaderMatch.Groups[1].Value.ToUpperInvariant()
    }
    return [ordered]@{
        image                 = $Image
        libraryCount          = $libs.Count
        libraries             = $libs
        libraryPaths          = $libPaths
        appClassesFingerprint = $appClassesFingerprint
        loaderFingerprint     = $loaderFingerprint
    }
}

# Step 3 (V2): the running candidate image must carry the full transformation evidence, cross-checked
# by SHA-256 against the frozen materialization manifest that produced it. EVERY manifest replacement
# (reportedly 24) is verified by deployed reduced hash - not just one (review Issue #3). When the
# baseline fingerprints are supplied, non-replaced dependencies are also proven byte-identical and any
# unexpected/missing library is reported.
function Test-CampaignCandidateTransformed {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$Image,
        [Parameter(Mandatory)][string]$MaterializationManifestPath,
        [System.Collections.IDictionary]$BaselineFingerprints = $null,
        [string]$LedgerDirectory = ''
    )
    $candidate = Get-CampaignImageArtifactFingerprints -ContainerCli $ContainerCli -Image $Image -LedgerDirectory $LedgerDirectory -Step 'candidate gate: artifact fingerprints'
    $runtimeLib = [int](Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -name 'jmoa-runtime-lib-*.jar' 2>/dev/null | wc -l" -LedgerDirectory $LedgerDirectory -Step 'candidate gate: runtime library jars')
    $jmoaRuntimeClasses = [int](Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -path '*BOOT-INF/classes/jmoa/*' 2>/dev/null | wc -l" -LedgerDirectory $LedgerDirectory -Step 'candidate gate: JMOA runtime classes')
    $imageRuntimeLibSha = (Invoke-CampaignImageProbe -ContainerCli $ContainerCli -Image $Image -ShellCommand "find / -name 'jmoa-runtime-lib-*.jar' 2>/dev/null -exec sha256sum {} \; | head -n 1 | cut -d' ' -f1" -LedgerDirectory $LedgerDirectory -Step 'candidate gate: runtime library SHA-256').Trim().ToUpperInvariant()

    $reasons = New-Object System.Collections.Generic.List[string]
    $manifestReplacedJars = 0
    $manifestRuntimeLibraryPresent = $false
    $runtimeLibraryName = ''
    $manifestVersion = $null
    $runtimeLibSha256Match = $false
    $replacementCountExpected = 0
    $replacementCountFound = 0
    $replacementCountHashMatched = 0
    $missingDependencyFiles = 0
    $unexpectedDependencyFiles = 0
    $nonReplacedUnchanged = $null
    $appClassesUnchanged = $null
    $loaderUnchanged = $null
    $replacementResults = New-Object System.Collections.Generic.List[object]

    if (-not (Test-Path -LiteralPath $MaterializationManifestPath -PathType Leaf)) {
        $reasons.Add("frozen materialization manifest not found: $MaterializationManifestPath") | Out-Null
    } else {
        $manifest = Get-Content -Raw -LiteralPath $MaterializationManifestPath | ConvertFrom-Json
        $manifestVersion = $manifest.metadataVersion
        $manifestReplacedJars = [int]$manifest.replacedJars
        $manifestRuntimeLibraryPresent = ($null -ne (Get-CampaignJsonProp $manifest 'runtimeLibrary') -and $null -ne (Get-CampaignJsonProp $manifest.runtimeLibrary 'targetSha256'))
        if ($manifestVersion -ne 'v2-public-materialization-v2') { $reasons.Add("unexpected materialization metadataVersion: $manifestVersion") | Out-Null }
        if ($manifestReplacedJars -le 0) { $reasons.Add('materialization manifest reports zero replaced jars') | Out-Null }
        if (-not $manifestRuntimeLibraryPresent) { $reasons.Add('materialization manifest has no runtime library record') | Out-Null }
        if ($manifestRuntimeLibraryPresent) {
            $expectedRuntimeLibSha = ([string]$manifest.runtimeLibrary.targetSha256).ToUpperInvariant()
            $runtimeLibraryName = [IO.Path]::GetFileName([string]$manifest.runtimeLibrary.target)
            $runtimeLibSha256Match = ($imageRuntimeLibSha -eq $expectedRuntimeLibSha)
            if (-not $runtimeLibSha256Match) { $reasons.Add("runtime-lib SHA in image ($imageRuntimeLibSha) does not match manifest ($expectedRuntimeLibSha)") | Out-Null }
        }
        # Verify EVERY replacement is deployed with its reduced hash (Issue #3).
        $replacements = @($manifest.replacements)
        $replacementCountExpected = $replacements.Count
        $replacedNames = New-Object System.Collections.Generic.HashSet[string]
        foreach ($replacement in $replacements) {
            $name = [string]$replacement.originalName
            [void]$replacedNames.Add($name)
            $expectedReducedSha = ([string]$replacement.afterSha256).ToUpperInvariant()
            $found = $candidate.libraries.ContainsKey($name)
            $actualSha = if ($found) { [string]$candidate.libraries[$name] } else { '' }
            $hashMatched = ($found -and $actualSha -eq $expectedReducedSha)
            if ($found) { $replacementCountFound++ } else { $missingDependencyFiles++ }
            if ($hashMatched) { $replacementCountHashMatched++ }
            $replacementResults.Add([ordered]@{ originalName = $name; expectedReducedSha256 = $expectedReducedSha; actualImageSha256 = $actualSha; found = $found; hashMatched = $hashMatched }) | Out-Null
            if (-not $found) { $reasons.Add("reduced dependency $name is missing from the candidate image") | Out-Null }
            elseif (-not $hashMatched) { $reasons.Add("reduced dependency $name SHA in image ($actualSha) != reduced hash ($expectedReducedSha)") | Out-Null }
        }
        # Non-replaced dependencies + loader/app-classes fingerprints versus B0 (Issue #3).
        if ($null -ne $BaselineFingerprints) {
            $baselineLibs = $BaselineFingerprints.libraries
            $mismatchedNonReplaced = 0
            $missingFromCandidate = 0
            foreach ($baseName in @($baselineLibs.Keys)) {
                if ($replacedNames.Contains($baseName)) { continue }
                if (-not $candidate.libraries.ContainsKey($baseName)) {
                    $missingFromCandidate++
                    $reasons.Add("non-replaced dependency $baseName present in B0 but missing from V2") | Out-Null
                } elseif ($candidate.libraries[$baseName] -ne $baselineLibs[$baseName]) {
                    $mismatchedNonReplaced++
                    $reasons.Add("non-replaced dependency $baseName changed between B0 and V2") | Out-Null
                }
            }
            $missingDependencyFiles += $missingFromCandidate
            $nonReplacedUnchanged = ($mismatchedNonReplaced -eq 0 -and $missingFromCandidate -eq 0)
            foreach ($candName in @($candidate.libraries.Keys)) {
                if (
                    -not $baselineLibs.ContainsKey($candName) -and
                    -not $replacedNames.Contains($candName) -and
                    $candName -ne $runtimeLibraryName
                ) {
                    $unexpectedDependencyFiles++
                    $reasons.Add("candidate image carries unexpected dependency $candName not present in B0 or manifest") | Out-Null
                }
            }
            # App classes are legitimately rewritten by JMOA; the loader is not. Record both, but only
            # the non-replaced dependency layer + reduced hashes are hard gates here.
            $appClassesUnchanged = ($candidate.appClassesFingerprint -eq $BaselineFingerprints.appClassesFingerprint)
            $loaderUnchanged = ($candidate.loaderFingerprint -eq $BaselineFingerprints.loaderFingerprint)
            if ([string]::IsNullOrWhiteSpace($candidate.appClassesFingerprint)) {
                $reasons.Add('candidate application-class fingerprint is empty') | Out-Null
            }
            if ([string]::IsNullOrWhiteSpace($candidate.loaderFingerprint)) {
                $reasons.Add('candidate Spring Boot loader fingerprint is empty') | Out-Null
            }
            if ([string]::IsNullOrWhiteSpace($BaselineFingerprints.appClassesFingerprint)) {
                $reasons.Add('baseline application-class fingerprint is empty') | Out-Null
            }
            if ([string]::IsNullOrWhiteSpace($BaselineFingerprints.loaderFingerprint)) {
                $reasons.Add('baseline Spring Boot loader fingerprint is empty') | Out-Null
            } elseif (-not $loaderUnchanged) {
                $reasons.Add('Spring Boot loader fingerprint changed between B0 and V2') | Out-Null
            }
        }
        if ($replacementCountExpected -gt 0 -and $replacementCountHashMatched -ne $replacementCountExpected) {
            $reasons.Add("only $replacementCountHashMatched/$replacementCountExpected reduced dependencies matched their manifest hash") | Out-Null
        }
    }
    if ($runtimeLib -le 0) { $reasons.Add('candidate image contains no jmoa-runtime-lib jar') | Out-Null }
    if ($jmoaRuntimeClasses -le 0) { $reasons.Add('candidate image carries no materialized BOOT-INF/classes/jmoa runtime classes') | Out-Null }
    return [ordered]@{
        arm                              = 'CANDIDATE'
        image                            = $Image
        runtimeLibraryJarCount           = $runtimeLib
        jmoaRuntimeClassCount            = $jmoaRuntimeClasses
        imageRuntimeLibSha256            = $imageRuntimeLibSha
        runtimeLibSha256Match            = $runtimeLibSha256Match
        replacementCountExpected         = $replacementCountExpected
        replacementCountFound            = $replacementCountFound
        replacementCountHashMatched      = $replacementCountHashMatched
        missingDependencyFiles           = $missingDependencyFiles
        unexpectedDependencyFiles        = $unexpectedDependencyFiles
        nonReplacedDependenciesUnchanged = $nonReplacedUnchanged
        appClassesFingerprint            = $candidate.appClassesFingerprint
        appClassesUnchangedVsBaseline    = $appClassesUnchanged
        loaderFingerprint                = $candidate.loaderFingerprint
        loaderUnchangedVsBaseline        = $loaderUnchanged
        manifestMetadataVersion          = $manifestVersion
        manifestReplacedJars             = $manifestReplacedJars
        manifestRuntimeLibraryPresent    = $manifestRuntimeLibraryPresent
        replacementResults               = $replacementResults.ToArray()
        passed                           = ($reasons.Count -eq 0)
        reasons                          = @($reasons)
    }
}

# Step 4: compare two semantic-requests.json captures request-for-request.
function Compare-CampaignSemantics {
    param(
        [Parameter(Mandatory)][string]$BaselineSemanticPath,
        [Parameter(Mandatory)][string]$CandidateSemanticPath,
        [Parameter(Mandatory)][int]$PairIndex
    )
    foreach ($required in @($BaselineSemanticPath, $CandidateSemanticPath)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "Semantic capture missing: $required"
        }
    }
    $baseline = Get-Content -Raw -LiteralPath $BaselineSemanticPath | ConvertFrom-Json
    $candidate = Get-Content -Raw -LiteralPath $CandidateSemanticPath | ConvertFrom-Json
    $baselineRequests = @($baseline.requests)
    $candidateRequests = @($candidate.requests)
    $divergences = New-Object System.Collections.Generic.List[object]
    if ($baselineRequests.Count -ne $candidateRequests.Count) {
        $divergences.Add([ordered]@{ seq = 0; kind = 'REQUEST_COUNT'; baseline = $baselineRequests.Count; candidate = $candidateRequests.Count }) | Out-Null
    }
    $count = [math]::Min($baselineRequests.Count, $candidateRequests.Count)
    for ($i = 0; $i -lt $count; $i++) {
        $b = $baselineRequests[$i]
        $c = $candidateRequests[$i]
        if ($b.path -ne $c.path -or $b.method -ne $c.method) {
            $divergences.Add([ordered]@{ seq = $b.seq; kind = 'SEQUENCE'; baseline = "$($b.method) $($b.path)"; candidate = "$($c.method) $($c.path)" }) | Out-Null
            continue
        }
        if ([int]$b.status -ne [int]$c.status) {
            $divergences.Add([ordered]@{ seq = $b.seq; kind = 'STATUS'; path = $b.path; baseline = $b.status; candidate = $c.status }) | Out-Null
        }
        if ([bool]$b.comparable -ne [bool]$c.comparable) {
            $divergences.Add([ordered]@{ seq = $b.seq; kind = 'COMPARABILITY'; path = $b.path; baseline = $b.comparable; candidate = $c.comparable }) | Out-Null
        }
        if ([bool]$b.comparable -and [bool]$c.comparable -and [string]$b.canonicalRuleId -ne [string]$c.canonicalRuleId) {
            $divergences.Add([ordered]@{ seq = $b.seq; kind = 'CANONICAL_RULE'; path = $b.path; baseline = $b.canonicalRuleId; candidate = $c.canonicalRuleId }) | Out-Null
        }
        if ($b.comparable -and $c.comparable -and ($b.bodySha256 -ne $c.bodySha256)) {
            $divergences.Add([ordered]@{ seq = $b.seq; kind = 'BODY_HASH'; path = $b.path; baseline = $b.bodySha256; candidate = $c.bodySha256 }) | Out-Null
        }
    }
    return [ordered]@{
        pairIndex     = $PairIndex
        baselinePath  = $BaselineSemanticPath
        candidatePath = $CandidateSemanticPath
        requestCount  = $count
        semanticErrors = $divergences.Count
        divergences   = $divergences.ToArray()
    }
}

# Step 4 (data-state proof): the baseline and candidate arms must start from the same initial data
# state, apply the same successful mutations, and reach the same final state (review Issue #6). Both
# data-state.json captures come from campaign-workload-petclinic.ps1 (jmoa-petclinic-data-state-v1).
function Compare-CampaignDataState {
    param(
        [Parameter(Mandatory)][string]$BaselineDataStatePath,
        [Parameter(Mandatory)][string]$CandidateDataStatePath,
        [Parameter(Mandatory)][int]$PairIndex
    )
    foreach ($required in @($BaselineDataStatePath, $CandidateDataStatePath)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "Data-state capture missing: $required"
        }
    }
    $baseline = Get-Content -Raw -LiteralPath $BaselineDataStatePath | ConvertFrom-Json
    $candidate = Get-Content -Raw -LiteralPath $CandidateDataStatePath | ConvertFrom-Json
    $reasons = New-Object System.Collections.Generic.List[string]
    $bInitial = [string](Get-CampaignJsonProp $baseline 'initialStateSha256')
    $cInitial = [string](Get-CampaignJsonProp $candidate 'initialStateSha256')
    $bFinal = [string](Get-CampaignJsonProp $baseline 'finalStateSha256')
    $cFinal = [string](Get-CampaignJsonProp $candidate 'finalStateSha256')
    $bMut = [bool](Get-CampaignJsonProp $baseline 'mutationsProven')
    $cMut = [bool](Get-CampaignJsonProp $candidate 'mutationsProven')
    if ([string]::IsNullOrWhiteSpace($bInitial) -or [string]::IsNullOrWhiteSpace($cInitial)) {
        $reasons.Add('one or both arms did not record an initial data-state fingerprint') | Out-Null
    } elseif ($bInitial -ne $cInitial) {
        $reasons.Add("initial data state differs (B0 $bInitial vs V2 $cInitial)") | Out-Null
    }
    if ([string]::IsNullOrWhiteSpace($bFinal) -or [string]::IsNullOrWhiteSpace($cFinal)) {
        $reasons.Add('one or both arms did not record a final data-state fingerprint') | Out-Null
    } elseif ($bFinal -ne $cFinal) {
        $reasons.Add("final data state differs (B0 $bFinal vs V2 $cFinal)") | Out-Null
    }
    if (-not $bMut -or -not $cMut) {
        $reasons.Add('mutations were not proven in one or both arms') | Out-Null
    }
    return [ordered]@{
        pairIndex               = $PairIndex
        baselineInitialSha256   = $bInitial
        candidateInitialSha256  = $cInitial
        baselineFinalSha256     = $bFinal
        candidateFinalSha256    = $cFinal
        initialStateMatched     = ($bInitial -eq $cInitial -and -not [string]::IsNullOrWhiteSpace($bInitial))
        finalStateMatched       = ($bFinal -eq $cFinal -and -not [string]::IsNullOrWhiteSpace($bFinal))
        mutationsProvenBaseline = $bMut
        mutationsProvenCandidate = $cMut
        passed                  = ($reasons.Count -eq 0)
        reasons                 = @($reasons)
    }
}

# Step 5 (config freeze): freeze the config repository by Git HEAD + working-tree status + a
# deterministic content-tree hash so drift is detectable before each arm (review Issue #5).
function Get-CampaignConfigFreeze {
    param([Parameter(Mandatory)][string]$ConfigRepo, [string]$LedgerDirectory = '')
    if (-not (Test-Path -LiteralPath $ConfigRepo -PathType Container)) {
        throw "Config repository directory does not exist: $ConfigRepo"
    }
    $head = ''
    $porcelain = ''
    $isGit = $false
    $gitDir = Join-Path $ConfigRepo '.git'
    if (Test-Path -LiteralPath $gitDir) {
        $isGit = $true
        $revResult = if ([string]::IsNullOrWhiteSpace($LedgerDirectory)) {
            Invoke-JmoaExternal -Executable 'git' -Arguments @('-C', $ConfigRepo, 'rev-parse', 'HEAD')
        } else {
            Invoke-AuditedExternal -Executable 'git' -Arguments @('-C', $ConfigRepo, 'rev-parse', 'HEAD') -LedgerDirectory $LedgerDirectory -Step 'freeze config Git HEAD'
        }
        if ($revResult.exitCode -eq 0) { $head = $revResult.output.Trim() }
        $statusResult = if ([string]::IsNullOrWhiteSpace($LedgerDirectory)) {
            Invoke-JmoaExternal -Executable 'git' -Arguments @('-C', $ConfigRepo, 'status', '--porcelain')
        } else {
            Invoke-AuditedExternal -Executable 'git' -Arguments @('-C', $ConfigRepo, 'status', '--porcelain') -LedgerDirectory $LedgerDirectory -Step 'freeze config Git status'
        }
        if ($statusResult.exitCode -eq 0) { $porcelain = $statusResult.output.Trim() }
    }
    $contentTreeSha = Get-CampaignTreeSha256 -Root $ConfigRepo -ExcludeRegex '(^|[\\/])\.git([\\/]|$)'
    return [ordered]@{
        schema             = 'jmoa-config-freeze-v1'
        configRepo         = $ConfigRepo
        isGitRepository    = $isGit
        gitHead            = $head
        gitStatusPorcelain = $porcelain
        workingTreeClean   = ($isGit -and [string]::IsNullOrWhiteSpace($porcelain))
        contentTreeSha256  = $contentTreeSha
        frozenAt           = [DateTime]::UtcNow.ToString('o')
    }
}

# Step 5 (config drift gate): re-freeze and prove the config content tree is byte-identical to the
# frozen baseline. Returns terminal STOPPED_CONFIG_DRIFT context when it drifts.
function Test-CampaignConfigUnchanged {
    param(
        [Parameter(Mandatory)][string]$ConfigRepo,
        [Parameter(Mandatory)][string]$ExpectedContentTreeSha256,
        [string]$LedgerDirectory = ''
    )
    $freeze = Get-CampaignConfigFreeze -ConfigRepo $ConfigRepo -LedgerDirectory $LedgerDirectory
    $matched = ($freeze.contentTreeSha256 -eq $ExpectedContentTreeSha256)
    $reasons = New-Object System.Collections.Generic.List[string]
    if (-not $matched) {
        $reasons.Add("config content tree drifted (expected $ExpectedContentTreeSha256, got $($freeze.contentTreeSha256))") | Out-Null
    }
    return [ordered]@{
        configRepo                = $ConfigRepo
        expectedContentTreeSha256 = $ExpectedContentTreeSha256
        actualContentTreeSha256   = $freeze.contentTreeSha256
        gitHead                   = $freeze.gitHead
        workingTreeClean          = $freeze.workingTreeClean
        passed                    = $matched
        reasons                   = @($reasons)
    }
}

# Step 4 (artifact lineage): validate the artifact-lineage.json produced by the adoption scenario
# (review Issue #4). B0 must prove NO JMOA participation; V2 must prove the full transform/reduce/
# materialize chain with zero preservation failures. Lineage is NOT reconstructed from the image.
function Test-CampaignArtifactLineage {
    param(
        [Parameter(Mandatory)][string]$LineagePath,
        [string]$ExpectedB0Sha256 = '',
        [string]$ExpectedV2Sha256 = '',
        [string]$ExpectedMaterializationManifestSha256 = ''
    )
    if (-not (Test-Path -LiteralPath $LineagePath -PathType Leaf)) {
        throw "artifact-lineage.json not found: $LineagePath"
    }
    $lineage = Get-Content -Raw -LiteralPath $LineagePath | ConvertFrom-Json
    $reasons = New-Object System.Collections.Generic.List[string]
    $b0 = Get-CampaignJsonProp $lineage 'baseline'
    $v2 = Get-CampaignJsonProp $lineage 'candidate'
    if ($null -eq $b0) { $reasons.Add('artifact-lineage.json has no baseline section') | Out-Null }
    if ($null -eq $v2) { $reasons.Add('artifact-lineage.json has no candidate section') | Out-Null }

    $b0Sha = ''
    $v2Sha = ''
    $b0Revision = ''
    $v2Revision = ''
    if ($null -ne $b0) {
        $b0Sha = [string](Get-CampaignJsonProp $b0 'artifactSha256')
        $b0Revision = [string](Get-CampaignJsonProp $b0 'sourceRevision')
        foreach ($field in @('sourceRevision', 'artifactSha256', 'buildCommandId', 'effectivePomSha256', 'dependencyTreeSha256')) {
            if ([string]::IsNullOrWhiteSpace([string](Get-CampaignJsonProp $b0 $field))) {
                $reasons.Add("baseline lineage is missing required field: $field") | Out-Null
            }
        }
        if ([bool](Get-CampaignJsonProp $b0 'jmoaPluginExecuted')) { $reasons.Add('baseline lineage reports the JMOA plugin executed on B0') | Out-Null }
        if ([bool](Get-CampaignJsonProp $b0 'jmoaDependencyPresent')) { $reasons.Add('baseline lineage reports a JMOA dependency on B0') | Out-Null }
        if ([bool](Get-CampaignJsonProp $b0 'jmoaClassesPresent')) { $reasons.Add('baseline lineage reports materialized JMOA classes on B0') | Out-Null }
        if ([bool](Get-CampaignJsonProp $b0 'jmoaReportsPresent')) { $reasons.Add('baseline lineage reports JMOA reports on B0') | Out-Null }
        if (-not [string]::IsNullOrWhiteSpace($ExpectedB0Sha256) -and $b0Sha.ToUpperInvariant() -ne $ExpectedB0Sha256.ToUpperInvariant()) {
            $reasons.Add("baseline lineage artifact SHA ($b0Sha) does not match expected ($ExpectedB0Sha256)") | Out-Null
        }
    }
    if ($null -ne $v2) {
        $v2Sha = [string](Get-CampaignJsonProp $v2 'artifactSha256')
        $v2Revision = [string](Get-CampaignJsonProp $v2 'sourceRevision')
        foreach ($field in @('sourceRevision', 'artifactSha256', 'profileSha256', 'admissionSha256', 'allowlistSha256', 'transformationReportSha256', 'reducerReportSha256', 'materializationManifestSha256')) {
            if ([string]::IsNullOrWhiteSpace([string](Get-CampaignJsonProp $v2 $field))) {
                $reasons.Add("candidate lineage is missing required field: $field") | Out-Null
            }
        }
        $preservationFailures = [int](Get-CampaignJsonProp $v2 'preservationFailures')
        if ($preservationFailures -ne 0) { $reasons.Add("candidate lineage reports $preservationFailures preservation failure(s)") | Out-Null }
        $reducedClassCount = [int](Get-CampaignJsonProp $v2 'reducedClassCount')
        if ($reducedClassCount -le 0) { $reasons.Add('candidate lineage reports zero reduced classes') | Out-Null }
        if (-not [string]::IsNullOrWhiteSpace($ExpectedV2Sha256) -and $v2Sha.ToUpperInvariant() -ne $ExpectedV2Sha256.ToUpperInvariant()) {
            $reasons.Add("candidate lineage artifact SHA ($v2Sha) does not match expected ($ExpectedV2Sha256)") | Out-Null
        }
        if (-not [string]::IsNullOrWhiteSpace($ExpectedMaterializationManifestSha256)) {
            $actualManifestSha = [string](Get-CampaignJsonProp $v2 'materializationManifestSha256')
            if ($actualManifestSha.ToUpperInvariant() -ne $ExpectedMaterializationManifestSha256.ToUpperInvariant()) {
                $reasons.Add("candidate lineage materialization manifest SHA ($actualManifestSha) does not match expected ($ExpectedMaterializationManifestSha256)") | Out-Null
            }
        }
    }
    if ($null -ne $b0 -and $null -ne $v2 -and -not [string]::IsNullOrWhiteSpace($b0Revision) -and -not [string]::IsNullOrWhiteSpace($v2Revision) -and $b0Revision -ne $v2Revision) {
        $reasons.Add("baseline ($b0Revision) and candidate ($v2Revision) were built from different source revisions") | Out-Null
    }
    return [ordered]@{
        lineagePath                     = $LineagePath
        baselineArtifactSha256          = $b0Sha
        candidateArtifactSha256         = $v2Sha
        baselineSourceRevision          = $b0Revision
        candidateSourceRevision         = $v2Revision
        sourceRevisionMatched           = ($b0Revision -eq $v2Revision -and -not [string]::IsNullOrWhiteSpace($b0Revision))
        passed                          = ($reasons.Count -eq 0)
        reasons                         = @($reasons)
    }
}

# Gate C (campaign manifest integrity, review Issue #15 + item 12): the campaign manifest carries every
# frozen image ID / artifact SHA / config freeze / lineage reference plus a self-certifying campaign
# SHA-256 computed over its canonical body (the campaignSha256 field itself excluded). Both the builder
# (new-petclinic-campaign-manifest.ps1) and the runner compute the digest identically so tampering with
# any recorded identity is detectable before a single container is launched.
function Get-CampaignManifestSha256 {
    param([Parameter(Mandatory)]$ManifestObject)
    $clone = $ManifestObject | ConvertTo-Json -Depth 32 | ConvertFrom-Json
    if ($clone.PSObject.Properties['campaignSha256']) { $clone.PSObject.Properties.Remove('campaignSha256') }
    $json = $clone | ConvertTo-Json -Depth 32
    $canonical = ConvertTo-JmoaCanonicalJson -Json $json -RuleId 'identity-v1'
    return (Get-JmoaTextSha256 -Value $canonical)
}

# Per-arm runtime JDK identity for cross-arm parity (review item 12): reads a captured run-manifest.json
# and returns a single canonical identity string (java -version banner + the in-container JDK fingerprint)
# so every arm can be proven to have run on the exact same runtime.
function Get-CampaignArmJdkIdentity {
    param([Parameter(Mandatory)][string]$RunDirectory)
    $manifestPath = Join-Path $RunDirectory 'run-manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "run-manifest.json missing for JDK identity in $RunDirectory"
    }
    $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    $fingerprint = Get-CampaignJsonProp $manifest 'runtimeJdkFingerprint'
    if ($null -eq $fingerprint) {
        throw "runtimeJdkFingerprint missing from $manifestPath"
    }
    $fingerprintSha = [string](Get-CampaignJsonProp $fingerprint 'fingerprintSha256')
    $javaVersion = [string](Get-CampaignJsonProp $fingerprint 'javaVersionRaw')
    if ([string]::IsNullOrWhiteSpace($fingerprintSha) -or [string]::IsNullOrWhiteSpace($javaVersion)) {
        throw "Stable JDK fingerprint fields are missing from $manifestPath"
    }
    return [ordered]@{
        runDirectory = $RunDirectory
        javaVersion  = $javaVersion.Trim()
        fingerprintSha256 = $fingerprintSha.ToUpperInvariant()
        identity     = $fingerprintSha.ToUpperInvariant()
    }
}
