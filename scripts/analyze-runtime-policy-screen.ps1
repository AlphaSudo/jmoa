param(
    [Parameter(Mandatory)][string]$PairRoot,
    [Parameter(Mandatory)][string]$BaselineRunDirectory,
    [Parameter(Mandatory)][string]$CandidateRunDirectory,
    [string]$CandidateArchivePath = '',
    [string]$BaselineLabel = 'NO_CDS_LOW_DIRTY',
    [string]$CandidateLabel = 'CDS',
    [string]$OutputDir = ''
)

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if([string]::IsNullOrWhiteSpace($OutputDir)){$OutputDir=Join-Path $PairRoot 'analysis'}
New-JmoaDirectory $OutputDir

function Read-Rollup([string]$Path){
    $result=[ordered]@{}
    foreach($line in Get-Content -LiteralPath $Path){if($line -match '^(Rss|Pss|Private_Dirty|Private_Clean|Shared_Dirty|Shared_Clean):\s+(\d+)\s+kB'){$result[$Matches[1]]=[long]$Matches[2]}}
    return $result
}
function Read-SmapsCategories([string]$Path,[string]$ArchiveName){
    $totals=[ordered]@{heapPssKb=0;anonymousRwPssKb=0;anonymousRwPrivateDirtyKb=0;archiveMappedPssKb=0;archiveMappedPrivateDirtyKb=0}
    $current=$null
    foreach($line in Get-Content -LiteralPath $Path){
        if($line -match '^([0-9a-f]+)-([0-9a-f]+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s*(.*)$'){
            $current=[ordered]@{perms=$Matches[3];path=$Matches[4].Trim();pss=0;privateDirty=0;anonymous=0}
        }elseif($null-ne$current-and$line-match'^Pss:\s+(\d+)\s+kB'){$current.pss=[long]$Matches[1]
        }elseif($null-ne$current-and$line-match'^Private_Dirty:\s+(\d+)\s+kB'){$current.privateDirty=[long]$Matches[1]
        }elseif($null-ne$current-and$line-match'^Anonymous:\s+(\d+)\s+kB'){
            $current.anonymous=[long]$Matches[1]
            if($current.path -eq '[heap]'){$totals.heapPssKb+=$current.pss}
            if($current.perms.StartsWith('rw') -and [string]::IsNullOrWhiteSpace($current.path)){$totals.anonymousRwPssKb+=$current.pss;$totals.anonymousRwPrivateDirtyKb+=$current.privateDirty}
            if(-not[string]::IsNullOrWhiteSpace($ArchiveName)-and$current.path-like"*$ArchiveName*"){$totals.archiveMappedPssKb+=$current.pss;$totals.archiveMappedPrivateDirtyKb+=$current.privateDirty}
        }
    }
    return $totals
}
function Read-Nmt([string]$Path){
    $text=Get-Content -Raw -LiteralPath $Path;$result=[ordered]@{totalCommittedKb=$null;classCommittedKb=$null;sharedClassSpaceCommittedKb=0;metaspaceCommittedKb=$null;codeCommittedKb=$null}
    if($text-match'(?m)^Total:\s+reserved=\d+KB,\s+committed=(\d+)KB'){$result.totalCommittedKb=[long]$Matches[1]}
    foreach($pair in @(@('Class','classCommittedKb'),@('Shared class space','sharedClassSpaceCommittedKb'),@('Metaspace','metaspaceCommittedKb'),@('Code','codeCommittedKb'))){if($text-match("(?ms)^-\s+"+[regex]::Escape($pair[0])+"\s+\(reserved=\d+KB,\s+committed=(\d+)KB")){$result[$pair[1]]=[long]$Matches[1]}}
    return $result
}
function Read-Run([string]$Directory,[string]$ArchiveName){
    foreach($name in @('smaps_rollup.txt','smaps.txt','memory.current','nmt-summary.txt','workload-result.json','run-manifest.json')){if(-not(Test-Path -LiteralPath (Join-Path $Directory $name)-PathType Leaf)){throw "Missing required run evidence: $(Join-Path $Directory $name)"}}
    [ordered]@{
        rollup=Read-Rollup (Join-Path $Directory 'smaps_rollup.txt')
        categories=Read-SmapsCategories (Join-Path $Directory 'smaps.txt') $ArchiveName
        memoryCurrentBytes=[long](Get-Content -Raw -LiteralPath (Join-Path $Directory 'memory.current'))
        nmt=Read-Nmt (Join-Path $Directory 'nmt-summary.txt')
        workload=Get-Content -Raw -LiteralPath (Join-Path $Directory 'workload-result.json')|ConvertFrom-Json
        manifest=Get-Content -Raw -LiteralPath (Join-Path $Directory 'run-manifest.json')|ConvertFrom-Json
    }
}
$archiveName=if([string]::IsNullOrWhiteSpace($CandidateArchivePath)){''}else{[IO.Path]::GetFileName($CandidateArchivePath)}
$b=Read-Run $BaselineRunDirectory '';$c=Read-Run $CandidateRunDirectory $archiveName
function Delta($x,$y){if($null-eq$x-or$null-eq$y){return $null};return [long]$y-[long]$x}
$d=[ordered]@{
    pssKb=Delta $b.rollup.Pss $c.rollup.Pss;privateDirtyKb=Delta $b.rollup.Private_Dirty $c.rollup.Private_Dirty
    memoryCurrentBytes=Delta $b.memoryCurrentBytes $c.memoryCurrentBytes;heapPssKb=Delta $b.categories.heapPssKb $c.categories.heapPssKb
    anonymousRwPssKb=Delta $b.categories.anonymousRwPssKb $c.categories.anonymousRwPssKb
    archiveMappedPssKb=$c.categories.archiveMappedPssKb;nmtTotalCommittedKb=Delta $b.nmt.totalCommittedKb $c.nmt.totalCommittedKb
    nmtClassCommittedKb=Delta $b.nmt.classCommittedKb $c.nmt.classCommittedKb;nmtSharedClassSpaceCommittedKb=Delta $b.nmt.sharedClassSpaceCommittedKb $c.nmt.sharedClassSpaceCommittedKb
    nmtMetaspaceCommittedKb=Delta $b.nmt.metaspaceCommittedKb $c.nmt.metaspaceCommittedKb
    nmtCodeCommittedKb=Delta $b.nmt.codeCommittedKb $c.nmt.codeCommittedKb
}
$decision=if($d.pssKb-le-1024-and$d.privateDirtyKb-le-1024-and$d.memoryCurrentBytes-le-1048576){'CDS_POLICY_SCREEN_BENEFICIAL'}elseif($d.pssKb-gt1024-or$d.memoryCurrentBytes-gt1048576){'CDS_POLICY_SCREEN_REGRESSION'}else{'CDS_POLICY_SCREEN_MIXED'}
$report=[ordered]@{
    metadataVersion='jmoa-runtime-policy-screen-analysis-v1';status=$decision;baselinePolicy=$BaselineLabel;candidatePolicy=$CandidateLabel
    artifactSha256=$b.manifest.artifactSha256;artifactHeldFixed=$b.manifest.artifactSha256-eq$c.manifest.artifactSha256
    workload=[ordered]@{baselineErrors=$b.workload.errorCount;candidateErrors=$c.workload.errorCount}
    baseline=$b;candidate=$c;deltas=$d
    cdsNetEffectModel=[ordered]@{
        observedPssDeltaKb=$d.pssKb;archiveMappedPssKb=$d.archiveMappedPssKb;privateDirtyDeltaKb=$d.privateDirtyKb
        anonymousRwDeltaKb=$d.anonymousRwPssKb;nmtClassDeltaKb=$d.nmtClassCommittedKb;nmtSharedClassSpaceDeltaKb=$d.nmtSharedClassSpaceCommittedKb;nmtMetaspaceDeltaKb=$d.nmtMetaspaceCommittedKb
        limitation='A single screen cannot infer counterfactual shared-page savings. The listed deltas are observed components, not a causal sum.'
    }
    claimBoundary='Diagnostic single-pair fixed-artifact policy screen. This is not a V1-to-V2 claim or a confirmed runtime-policy result.'
}
Write-JmoaJson $report (Join-Path $OutputDir 'runtime-policy-screen-analysis.json')
$md=@"
# Runtime Policy Screen Analysis

- Status: ``$decision``
- Artifact held fixed: ``$($report.artifactHeldFixed)``
- PSS delta (CDS - no-CDS): ``$($d.pssKb) KB``
- Private_Dirty delta: ``$($d.privateDirtyKb) KB``
- memory.current delta: ``$($d.memoryCurrentBytes) bytes``
- Heap PSS delta: ``$($d.heapPssKb) KB``
- Anonymous writable PSS delta: ``$($d.anonymousRwPssKb) KB``
- CDS archive mapped PSS: ``$($d.archiveMappedPssKb) KB``
- NMT total committed delta: ``$($d.nmtTotalCommittedKb) KB``

This is one diagnostic pair. It isolates policy on one artifact but does not establish a confirmed policy claim.
"@
Write-JmoaText $md (Join-Path $OutputDir 'runtime-policy-screen-analysis.md')
exit 0
