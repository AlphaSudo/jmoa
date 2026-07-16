param(
    [Parameter(Mandatory)][string]$LeftArchivePath,
    [Parameter(Mandatory)][string]$RightArchivePath,
    [Parameter(Mandatory)][string]$LeftClassLoadLog,
    [Parameter(Mandatory)][string]$RightClassLoadLog,
    [string]$LeftSmapsPath = '',
    [string]$RightSmapsPath = '',
    [string]$LeftLabel = 'V1',
    [string]$RightLabel = 'V2',
    [string]$OutputDir = 'target/jmoa-cds-archive-comparison'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
foreach ($path in @($LeftArchivePath,$RightArchivePath,$LeftClassLoadLog,$RightClassLoadLog)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required input does not exist: $path" }
}
New-JmoaDirectory -Path $OutputDir

function Get-NormalizedClassSet {
    param([string]$Path)
    $set = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in Get-Content -LiteralPath $Path) {
        $name = $null
        if ($line -match '\[class,load[^\]]*\]\s+([^\s]+)') { $name = $Matches[1] }
        elseif ($line -match '\[class,load\]\s+([^\s]+)') { $name = $Matches[1] }
        if ([string]::IsNullOrWhiteSpace($name)) { continue }
        $name = $name -replace '/0x[0-9a-fA-F]+$','/<hidden>'
        $name = $name -replace '\$\$Lambda\$\d+','$$Lambda$<generated>'
        $name = $name -replace '\$\$SpringCGLIB\$\$\d+','$$SpringCGLIB$$<generated>'
        $name = $name -replace '\$Proxy\d+','$Proxy<generated>'
        [void]$set.Add($name)
    }
    return $set
}

function Get-ClassFamilyCounts {
    param([Collections.Generic.HashSet[string]]$Set)
    $counts = [ordered]@{ total=0; lambda=0; hidden=0; springCglib=0; jdkProxy=0; byteBuddy=0; hibernateProxy=0; jmoa=0; springAot=0 }
    foreach ($name in $Set) {
        $counts.total++
        if ($name -match '\$\$Lambda\$|lambda\$') { $counts.lambda++ }
        if ($name -match '/<hidden>|/0x') { $counts.hidden++ }
        if ($name -match 'SpringCGLIB|EnhancerBySpringCGLIB|FastClass') { $counts.springCglib++ }
        if ($name -match '(^|\.)\$Proxy|jdk\.proxy') { $counts.jdkProxy++ }
        if ($name -match 'ByteBuddy') { $counts.byteBuddy++ }
        if ($name -match 'HibernateProxy') { $counts.hibernateProxy++ }
        if ($name -match '(?i)(^|\.)jmoa(\.|$)') { $counts.jmoa++ }
        if ($name -match '__BeanDefinitions|__BeanFactoryRegistrations|ApplicationContextInitializer') { $counts.springAot++ }
    }
    return $counts
}

function Get-ArchiveMappings {
    param([string]$SmapsPath,[string]$ArchivePath)
    if ([string]::IsNullOrWhiteSpace($SmapsPath) -or -not (Test-Path -LiteralPath $SmapsPath -PathType Leaf)) {
        return [ordered]@{ available=$false; mappingCount=0; pssKb=0; privateDirtyKb=0; ranges=@() }
    }
    $archiveName = [IO.Path]::GetFileName($ArchivePath)
    $ranges = @(); $current = $null
    foreach ($line in Get-Content -LiteralPath $SmapsPath) {
        if ($line -match '^([0-9a-f]+)-([0-9a-f]+)\s+\S+\s+\S+\s+\S+\s+\S+\s*(.*)$') {
            $current = if ($Matches[3] -like "*$archiveName*") { [ordered]@{ start=$Matches[1]; end=$Matches[2]; path=$Matches[3].Trim(); pssKb=0; privateDirtyKb=0 } } else { $null }
            if ($null -ne $current) { $ranges += $current }
        } elseif ($null -ne $current -and $line -match '^Pss:\s+(\d+)\s+kB') { $current.pssKb=[int]$Matches[1] }
        elseif ($null -ne $current -and $line -match '^Private_Dirty:\s+(\d+)\s+kB') { $current.privateDirtyKb=[int]$Matches[1] }
    }
    [ordered]@{
        available=$true; mappingCount=$ranges.Count
        pssKb=[int](($ranges | Measure-Object pssKb -Sum).Sum)
        privateDirtyKb=[int](($ranges | Measure-Object privateDirtyKb -Sum).Sum)
        ranges=$ranges
    }
}

$leftSet = Get-NormalizedClassSet $LeftClassLoadLog
$rightSet = Get-NormalizedClassSet $RightClassLoadLog
$both = @($leftSet | Where-Object { $rightSet.Contains($_) } | Sort-Object)
$leftOnly = @($leftSet | Where-Object { -not $rightSet.Contains($_) } | Sort-Object)
$rightOnly = @($rightSet | Where-Object { -not $leftSet.Contains($_) } | Sort-Object)
$unionCount = $leftSet.Count + $rightSet.Count - $both.Count
$jaccard = if ($unionCount -eq 0) { 1.0 } else { [Math]::Round($both.Count / $unionCount, 6) }
$report = [ordered]@{
    metadataVersion='jmoa-cds-archive-comparison-v1'; generatedAt=[DateTime]::UtcNow.ToString('o')
    left=[ordered]@{ label=$LeftLabel; archiveSha256=Get-JmoaSha256 $LeftArchivePath; archiveBytes=[long](Get-Item $LeftArchivePath).Length; trainingLoadedClasses=$leftSet.Count; familyCounts=Get-ClassFamilyCounts $leftSet; mappings=Get-ArchiveMappings $LeftSmapsPath $LeftArchivePath }
    right=[ordered]@{ label=$RightLabel; archiveSha256=Get-JmoaSha256 $RightArchivePath; archiveBytes=[long](Get-Item $RightArchivePath).Length; trainingLoadedClasses=$rightSet.Count; familyCounts=Get-ClassFamilyCounts $rightSet; mappings=Get-ArchiveMappings $RightSmapsPath $RightArchivePath }
    normalizedClassSets=[ordered]@{ sharedInBoth=$both.Count; leftOnly=$leftOnly.Count; rightOnly=$rightOnly.Count; jaccard=$jaccard; leftOnlyClasses=$leftOnly; rightOnlyClasses=$rightOnly }
    evidenceBoundary=[ordered]@{
        trainingLogs='Class sets prove classes observed during training, not guaranteed archive inclusion.'
        mappings='Archive mapping values are emitted only when matching full smaps captures are supplied.'
        archivedClassCount='UNKNOWN_WITHOUT_JVM_ARCHIVE_DIAGNOSTICS'
        rejectedClasses='UNKNOWN_WITHOUT_CDS_TRAINING_LOG'
    }
}
Write-JmoaJson $report (Join-Path $OutputDir 'cds-archive-comparison.json')
$md = @"
# CDS Archive Comparison

| Metric | $LeftLabel | $RightLabel |
|---|---:|---:|
| Archive bytes | $($report.left.archiveBytes) | $($report.right.archiveBytes) |
| Training-loaded normalized classes | $($leftSet.Count) | $($rightSet.Count) |
| Archive mapped PSS KB | $($report.left.mappings.pssKb) | $($report.right.mappings.pssKb) |

- Shared normalized training classes: **$($both.Count)**
- $LeftLabel-only: **$($leftOnly.Count)**
- $RightLabel-only: **$($rightOnly.Count)**
- Jaccard similarity: **$jaccard**

Training class-load logs do not prove archive inclusion. Archived and rejected class counts remain unknown unless JVM archive diagnostics are supplied.
"@
Write-JmoaText $md (Join-Path $OutputDir 'cds-archive-comparison.md')
exit 0
