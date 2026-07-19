param(
    [Parameter(Mandatory)][string]$ConfigPath,
    [string]$OutputDirectory = 'target/runtime-equivalence/artifact-lineage',
    [string]$PublicOutputDirectory = '',
    [switch]$FailOnInvalidB0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-JmoaDirectory $OutputDirectory
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json

function Inspect-Jar([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return [ordered]@{ path=$Path; exists=$false } }
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Path).Path)
    try {
        $entries = @($archive.Entries | ForEach-Object FullName)
        function Get-EntryHash($Entry){
            $stream=$Entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
            try{return([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','')}finally{$sha.Dispose();$stream.Dispose()}
        }
        $nested = @($archive.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' } | Sort-Object FullName | ForEach-Object { "$($_.FullName)|$(Get-EntryHash $_)" })
        $appEntries=@($archive.Entries|Where-Object{$_.Name-and$_.FullName-like'BOOT-INF/classes/*'-and$_.FullName-notmatch'(?i)jmoa'}|Sort-Object FullName)
        $appNames=@($appEntries|ForEach-Object FullName)
        $appClassNames=@($appEntries|Where-Object{$_.FullName-like'*.class'}|ForEach-Object FullName)
        $appResources=@($appEntries|Where-Object{$_.FullName-notlike'*.class'}|ForEach-Object{"$($_.FullName)|$(Get-EntryHash $_)"})
        $jmoa = @($entries | Where-Object { $_ -match '(?i)(^|/)(jmoa|com/.*/jmoa|io/github/.*/jmoa)' })
        [ordered]@{
        path=(Resolve-Path $Path).Path; exists=$true; bytes=(Get-Item $Path).Length; sha256=Get-JmoaSha256 $Path
            entryCount=$entries.Count; jmoaEntries=$jmoa; nestedDependencyCount=$nested.Count
            nestedDependencyFingerprint=Get-JmoaTextSha256 ($nested -join "`n")
            nestedDependencyNameFingerprint=Get-JmoaTextSha256 ((@($archive.Entries|Where-Object{$_.FullName-like'BOOT-INF/lib/*.jar'-and$_.FullName-notmatch'(?i)jmoa'}|Sort-Object FullName|ForEach-Object FullName))-join"`n")
            applicationEntryNameFingerprint=Get-JmoaTextSha256 ($appNames-join"`n")
            applicationClassNameFingerprint=Get-JmoaTextSha256 ($appClassNames-join"`n")
            applicationResourceFingerprint=Get-JmoaTextSha256 ($appResources-join"`n")
        }
    } finally { $archive.Dispose() }
}

$variants = @()
foreach ($variant in $config.variants) {
    $artifact = if($variant.PSObject.Properties.Name -contains 'declaredArtifactSha256' -and $variant.declaredArtifactSha256){
        [ordered]@{path=$variant.artifactPath;exists=if($variant.artifactPath){Test-Path -LiteralPath $variant.artifactPath}else{$false};bytes=if($variant.PSObject.Properties.Name-contains'declaredArtifactBytes'){$variant.declaredArtifactBytes}else{$null};sha256=$variant.declaredArtifactSha256;entryCount=$null;jmoaEntries=@();nestedDependencyCount=if($variant.PSObject.Properties.Name-contains'declaredDependencyCount'){$variant.declaredDependencyCount}else{$null};nestedDependencyFingerprint=if($variant.PSObject.Properties.Name-contains'declaredDependencyFingerprint'){$variant.declaredDependencyFingerprint}else{''};nestedDependencyNameFingerprint='';applicationEntryNameFingerprint='';applicationClassNameFingerprint='';applicationResourceFingerprint='';identitySource='DECLARED_AUDITED_MANIFEST'}
    }else{Inspect-Jar $variant.artifactPath}
    $runtime = if ($variant.PSObject.Properties.Name -contains 'runtimeArtifactPath' -and $variant.runtimeArtifactPath) { Inspect-Jar $variant.runtimeArtifactPath } else { $null }
    $isB0 = $variant.id -eq 'B0'
    $b0Checks = if ($isB0) { [ordered]@{
        noJmoaEntries=($artifact.exists -and $artifact.jmoaEntries.Count -eq 0)
        noRuntimeDependency=(-not ($variant.PSObject.Properties.Name -contains 'jmoaRuntimeDependency') -or -not [bool]$variant.jmoaRuntimeDependency)
        noPluginExecution=(-not ($variant.PSObject.Properties.Name -contains 'jmoaPluginExecuted') -or -not [bool]$variant.jmoaPluginExecuted)
        noReducerManifest=(-not ($variant.PSObject.Properties.Name -contains 'reducerManifestPath') -or -not $variant.reducerManifestPath)
        noJavaagent=(-not ($variant.PSObject.Properties.Name -contains 'javaagentPresent') -or -not [bool]$variant.javaagentPresent)
    } } else { $null }
    $variants += [ordered]@{
        id=$variant.id; classification=$variant.classification; sourceRevision=$variant.sourceRevision
        artifact=$artifact; runtimeArtifact=$runtime; logicalArtifactPath=if($variant.PSObject.Properties.Name -contains 'logicalArtifactPath'){$variant.logicalArtifactPath}else{[IO.Path]::GetFileName($variant.artifactPath)}; transformCommandIds=@($variant.transformCommandIds)
        parentVariant=if ($variant.PSObject.Properties.Name -contains 'parentVariant') { $variant.parentVariant } else { $null }
        b0Proof=$b0Checks
    }
}
$invalidB0 = @($variants | Where-Object { $_.id -eq 'B0' -and ($_.b0Proof.Values -contains $false) })
$sourceRevisions = @($variants | ForEach-Object sourceRevision | Where-Object { $_ } | Sort-Object -Unique)
$report = [ordered]@{
    schemaVersion='jmoa-artifact-lineage-v1'; service=$config.service; generatedUtc=[DateTime]::UtcNow.ToString('o')
    variants=$variants; sameSourceUniverse=($sourceRevisions.Count -le 1); sourceRevisions=$sourceRevisions
    strictB0Valid=($invalidB0.Count -eq 0); comparisonAdmissible=($invalidB0.Count -eq 0 -and $sourceRevisions.Count -le 1)
}
Write-JmoaJson $report (Join-Path $OutputDirectory "$($config.service)-artifact-lineage.json")
    $rows = $variants | ForEach-Object { "| $($_.id) | $($_.classification) | $($_.sourceRevision) | $($_.artifact.sha256) | $($_.artifact.jmoaEntries.Count) |" }
$md = "# $($config.service) Artifact Lineage`n`n| Variant | Classification | Source revision | Artifact SHA-256 | JMOA entries |`n|---|---|---|---|---:|`n$($rows -join "`n")`n`nComparison admissible: **$($report.comparisonAdmissible)**."
Write-JmoaText $md (Join-Path $OutputDirectory "$($config.service)-artifact-lineage.md")
if(-not[string]::IsNullOrWhiteSpace($PublicOutputDirectory)){
    New-JmoaDirectory $PublicOutputDirectory
    $publicVariants=@($variants|ForEach-Object{
        [ordered]@{id=$_.id;classification=$_.classification;sourceRevision=$_.sourceRevision;logicalArtifactPath=$_.logicalArtifactPath;artifact=[ordered]@{exists=$_.artifact.exists;bytes=$_.artifact.bytes;sha256=$_.artifact.sha256;entryCount=$_.artifact.entryCount;jmoaEntryCount=$_.artifact.jmoaEntries.Count;nestedDependencyCount=$_.artifact.nestedDependencyCount;nestedDependencyFingerprint=$_.artifact.nestedDependencyFingerprint;nestedDependencyNameFingerprint=$_.artifact.nestedDependencyNameFingerprint;applicationEntryNameFingerprint=$_.artifact.applicationEntryNameFingerprint;applicationClassNameFingerprint=$_.artifact.applicationClassNameFingerprint;applicationResourceFingerprint=$_.artifact.applicationResourceFingerprint};transformCommandIds=$_.transformCommandIds;parentVariant=$_.parentVariant;b0Proof=$_.b0Proof}
    })
    $public=[ordered]@{schemaVersion=$report.schemaVersion;service=$report.service;variants=$publicVariants;sameSourceUniverse=$report.sameSourceUniverse;sourceRevisions=$report.sourceRevisions;strictB0Valid=$report.strictB0Valid;comparisonAdmissible=$report.comparisonAdmissible}
    Write-JmoaJson $public (Join-Path $PublicOutputDirectory "$($config.service).json")
    Write-JmoaText $md (Join-Path $PublicOutputDirectory "$($config.service).md")
}
if ($FailOnInvalidB0 -and $invalidB0.Count) { throw 'Strict B0 proof failed.' }
$report | ConvertTo-Json -Depth 15
