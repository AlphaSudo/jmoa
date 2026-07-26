<#
.SYNOPSIS
    PETCLINIC_TARGET_ONLY_V1: target-only B0/V2 campaign with pair-scoped shared support.

.DESCRIPTION
    Consumes the same signed frozen-artifact manifest as run-petclinic-performance-campaign.ps1.
    Support memory is diagnostic only. Product deltas contain only customers-service process and
    exact target-cgroup metrics. The protocol order and all thresholds are frozen in this script.
#>
param(
    [Parameter(Mandatory)][string]$CampaignManifest,
    [Parameter(Mandatory)][string]$RunRoot,
    [Parameter(Mandatory)][string]$FixturesReport,
    [int]$SupportSettleSeconds=180,
    [int]$WarmupSeconds=20,
    [int]$SettleSeconds=5,
    [int]$HealthTimeoutSeconds=900,
    [int]$TrustedPssGateKb=-4096,
    [int]$ConfirmedPssGateKb=-1024,
    [int]$ConfirmedPrivateDirtyGateKb=-1024,
    [long]$ConfirmedMemoryCurrentGateBytes=-1048576,
    [int]$MaxNoisePssDriftKb=1024,
    [int]$MaxNoisePrivateDirtyDriftKb=1024,
    [long]$MaxNoiseMemoryCurrentDriftBytes=2097152,
    [string]$ContainerCli='',
    [switch]$DryRun
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')
. (Join-Path $PSScriptRoot 'scenario-ledger-common.ps1')

$protocol='PETCLINIC_TARGET_ONLY_V1'
$repositoryRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$supportLaunch=Join-Path $PSScriptRoot 'campaign-launch-petclinic-support.ps1'
$supportStop=Join-Path $PSScriptRoot 'campaign-stop-petclinic-support.ps1'
$targetLaunch=Join-Path $PSScriptRoot 'campaign-launch-petclinic-target.ps1'
$targetStop=Join-Path $PSScriptRoot 'campaign-stop-petclinic-target.ps1'
$transitionScript=Join-Path $PSScriptRoot 'campaign-verify-petclinic-target-transition.ps1'
$screenScript=Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
$workloadScript=Join-Path $PSScriptRoot 'campaign-workload-petclinic.ps1'
$noiseAnalyzer=Join-Path $PSScriptRoot 'analyze-same-artifact-noise.ps1'
foreach($path in @($supportLaunch,$supportStop,$targetLaunch,$targetStop,$transitionScript,$screenScript,$workloadScript,$noiseAnalyzer)){
    if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Required protocol script missing: $path"}
}

$manifest=Get-Content -Raw -LiteralPath $CampaignManifest|ConvertFrom-Json
if([string](Get-CampaignJsonProp $manifest 'schemaVersion')-ne 'jmoa-petclinic-campaign-manifest-v1'){throw 'Unsupported campaign manifest schema.'}
$recordedCampaignSha=[string](Get-CampaignJsonProp $manifest 'campaignSha256')
$actualCampaignSha=Get-CampaignManifestSha256 -ManifestObject $manifest
if($recordedCampaignSha.ToUpperInvariant()-ne $actualCampaignSha.ToUpperInvariant()){throw 'Campaign manifest integrity failed.'}
$images=Get-CampaignJsonProp $manifest 'images';$artifacts=Get-CampaignJsonProp $manifest 'artifacts';$config=Get-CampaignJsonProp $manifest 'configRepo';$environment=Get-CampaignJsonProp $manifest 'environment';$artifactLineage=Get-CampaignJsonProp $manifest 'artifactLineage'
$b0Image=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'baseline') 'ref')
$v2Image=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'candidate') 'ref')
$configImage=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'config') 'ref')
$discoveryImage=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'discovery') 'ref')
$expectedImageIds=[ordered]@{
    b0=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'baseline') 'id')
    v2=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'candidate') 'id')
    config=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'config') 'id')
    discovery=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'discovery') 'id')
}
$b0Artifact=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'baseline') 'path')
$v2Artifact=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'candidate') 'path')
$b0Sha=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'baseline') 'sha256')
$v2Sha=[string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'candidate') 'sha256')
$materializationManifestAsset=Get-CampaignJsonProp $artifacts 'materializationManifest'
$materializationManifest=[string](Get-CampaignJsonProp $materializationManifestAsset 'path')
$materializationManifestSha=[string](Get-CampaignJsonProp $materializationManifestAsset 'sha256')
$artifactLineagePath=[string](Get-CampaignJsonProp $artifactLineage 'path')
$artifactLineageSha=[string](Get-CampaignJsonProp $artifactLineage 'sha256')
$configRepo=[string](Get-CampaignJsonProp $config 'path')
$configTreeSha=[string](Get-CampaignJsonProp $config 'contentTreeSha256')
$maven=[string](Get-CampaignJsonProp $environment 'mavenExecutable')
$pluginCoordinates=[string](Get-CampaignJsonProp $environment 'pluginCoordinates')
$runtimePolicy=[string](Get-CampaignJsonProp $environment 'runtimePolicy')
$manifestContainerCli=[string](Get-CampaignJsonProp $environment 'containerCli')
if([string]::IsNullOrWhiteSpace($ContainerCli)){$ContainerCli=$manifestContainerCli}
if([string]::IsNullOrWhiteSpace($ContainerCli)){throw 'Campaign manifest does not define environment.containerCli.'}
if($runtimePolicy-ne 'NO_CDS_LOW_DIRTY'){throw "Protocol requires NO_CDS_LOW_DIRTY, manifest says $runtimePolicy."}
foreach($p in @($b0Artifact,$v2Artifact,$materializationManifest,$artifactLineagePath,$configRepo,$FixturesReport)){if(-not(Test-Path -LiteralPath $p)){throw "Frozen input missing: $p"}}
if((Get-JmoaSha256 $b0Artifact).ToUpperInvariant()-ne $b0Sha.ToUpperInvariant()){throw 'B0 artifact SHA mismatch.'}
if((Get-JmoaSha256 $v2Artifact).ToUpperInvariant()-ne $v2Sha.ToUpperInvariant()){throw 'V2 artifact SHA mismatch.'}
if((Get-JmoaSha256 $materializationManifest).ToUpperInvariant()-ne $materializationManifestSha.ToUpperInvariant()){throw 'Materialization manifest SHA mismatch.'}
if((Get-JmoaSha256 $artifactLineagePath).ToUpperInvariant()-ne $artifactLineageSha.ToUpperInvariant()){throw 'Artifact lineage SHA mismatch.'}
if((Get-CampaignTreeSha256 -Root $configRepo)-ne $configTreeSha){throw 'Config tree SHA mismatch.'}

$fixtures=Get-Content -Raw -LiteralPath $FixturesReport|ConvertFrom-Json
if(-not[bool]$fixtures.passed){throw 'Gate A fixtures are not passing.'}
foreach($f in @($fixtures.testedFiles)){
    $local=Join-Path $repositoryRoot (([string]$f.logicalPath)-replace '/','\')
    if(-not(Test-Path $local)-or(Get-JmoaSha256 $local).ToUpperInvariant()-ne([string]$f.sha256).ToUpperInvariant()){throw "Gate A fixture report is stale for $($f.logicalPath)."}
}

$runId='petclinic-target-only-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$resolvedRunRoot=if([IO.Path]::IsPathRooted($RunRoot)){[IO.Path]::GetFullPath($RunRoot)}else{[IO.Path]::GetFullPath((Join-Path $repositoryRoot $RunRoot))}
$runDir=Join-Path $resolvedRunRoot $runId;$captures=Join-Path $runDir 'captures';$reports=Join-Path $runDir 'reports';$ledgers=Join-Path $runDir 'child-ledgers'
$b0Controls=Join-Path $captures 'controls-b0';$v2Controls=Join-Path $captures 'controls-v2';$product=Join-Path $captures 'product';$evidenceDir=Join-Path $runDir 'jmoa-evidence';$attributionDir=Join-Path $runDir 'jmoa-attribution'
foreach($d in @($runDir,$captures,$reports,$ledgers)){New-JmoaDirectory $d}
Start-ScenarioLedger -ScenarioId $runId -OutputDirectory $runDir -Description 'PETCLINIC_TARGET_ONLY_V1 pre-registered target-only comparison. Pair-scoped config/discovery are validity dependencies; only customers-service process and exact target cgroup enter product deltas.'
Add-ScenarioNote -Title 'Pre-registered protocol' -Text 'Artifacts, images, JMOA, flags, workload, warmup, settle, noise thresholds, V2-C thresholds, and strict 4 MiB gate are frozen. SUPPORT_CALIBRATION_V2 remains valid diagnostic history but is not an admission gate.'
Add-ScenarioAsset -Role 'Frozen campaign manifest' -Path $CampaignManifest -Provenance REUSED_FROZEN_INPUT -Note "campaignSha256=$actualCampaignSha"|Out-Null
Add-ScenarioAsset -Role 'Frozen B0 application jar' -Path $b0Artifact -Provenance REUSED_FROZEN_INPUT -Note "sha256=$b0Sha"|Out-Null
Add-ScenarioAsset -Role 'Frozen V2 application jar' -Path $v2Artifact -Provenance REUSED_FROZEN_INPUT -Note "sha256=$v2Sha"|Out-Null
Add-ScenarioAsset -Role 'Frozen V2 materialization manifest' -Path $materializationManifest -Provenance REUSED_FROZEN_INPUT -Note "sha256=$materializationManifestSha"|Out-Null
Add-ScenarioAsset -Role 'Frozen artifact lineage' -Path $artifactLineagePath -Provenance REUSED_FROZEN_INPUT -Note "sha256=$artifactLineageSha"|Out-Null

function Resolve-Image([string]$Ref,[string]$Role){
    (Invoke-ScenarioCommand -Step "resolve frozen $Role image" -Executable $ContainerCli -Arguments @('image','inspect','--format','{{.Id}}',$Ref)).stdout.Trim()
}
$resolvedImages=[ordered]@{b0=Resolve-Image $b0Image B0;v2=Resolve-Image $v2Image V2;config=Resolve-Image $configImage config;discovery=Resolve-Image $discoveryImage discovery}
$imagePassed=$true
foreach($key in @('b0','v2','config','discovery')){if(($resolvedImages[$key]-replace '^sha256:','')-ne($expectedImageIds[$key]-replace '^sha256:','')){$imagePassed=$false}}
Write-JmoaJson ([ordered]@{schemaVersion='jmoa-target-only-image-gate-v1';expected=$expectedImageIds;resolved=$resolvedImages;passed=$imagePassed}) (Join-Path $reports 'image-identity-gate.json')
if(-not$imagePassed){Complete-ScenarioLedger -Status STOPPED_ARTIFACT_GATE -Result @{reason='image mismatch'}|Out-Null;throw 'Image identity gate failed.'}

$artifactPreflightLedger=Join-Path $ledgers 'preflight-artifact'
Initialize-CampaignAuditLedger -LedgerDirectory $artifactPreflightLedger -Stage 'preflight-artifact' -Variant 'B0_V2' -Description 'Strict B0 cleanliness, complete V2 materialization, and runtime-library verification before target-only measurement.' | Out-Null
$baselineFingerprints=Get-CampaignImageArtifactFingerprints -ContainerCli $ContainerCli -Image $b0Image -LedgerDirectory $artifactPreflightLedger -Step 'baseline gate: artifact fingerprints'
$baselineGate=Test-CampaignBaselineClean -ContainerCli $ContainerCli -Image $b0Image -LedgerDirectory $artifactPreflightLedger
$candidateGate=Test-CampaignCandidateTransformed -ContainerCli $ContainerCli -Image $v2Image -MaterializationManifestPath $materializationManifest -BaselineFingerprints $baselineFingerprints -LedgerDirectory $artifactPreflightLedger
$artifactGate=[ordered]@{schemaVersion='jmoa-target-only-artifact-gate-v1';baseline=$baselineGate;candidate=$candidateGate;passed=($baselineGate.passed-and$candidateGate.passed)}
Write-JmoaJson $artifactGate (Join-Path $reports 'artifact-gate.json')
$artifactLedgerStatus=if($artifactGate.passed){'COMPLETE'}else{'FAILED'}
Complete-CampaignAuditLedger -LedgerDirectory $artifactPreflightLedger -Status $artifactLedgerStatus -Stage 'preflight-artifact' -Variant 'B0_V2' | Out-Null
if(-not$artifactGate.passed){Complete-ScenarioLedger -Status STOPPED_ARTIFACT_GATE -Result $artifactGate|Out-Null;throw 'B0/V2 transformation gate failed.'}

$lineageGate=Test-CampaignArtifactLineage -LineagePath $artifactLineagePath -ExpectedB0Sha256 $b0Sha -ExpectedV2Sha256 $v2Sha -ExpectedMaterializationManifestSha256 $materializationManifestSha
Write-JmoaJson $lineageGate (Join-Path $reports 'artifact-lineage-gate.json')
if(-not$lineageGate.passed){Complete-ScenarioLedger -Status STOPPED_ARTIFACT_GATE -Result $lineageGate|Out-Null;throw 'Artifact lineage gate failed.'}

$frozenConfig=Join-Path $runDir 'frozen-config-repo';New-JmoaDirectory $frozenConfig
foreach($entry in @(Get-ChildItem -LiteralPath $configRepo -Force|Where-Object Name -ne '.git')){Copy-Item -LiteralPath $entry.FullName -Destination $frozenConfig -Recurse -Force}
if((Get-CampaignTreeSha256 -Root $frozenConfig)-ne$configTreeSha){throw 'Frozen config copy hash mismatch.'}
$bootId=(Invoke-ScenarioCommand -Step 'capture initial host boot ID' -Executable '/bin/bash' -Arguments @('-lc','cat /proc/sys/kernel/random/boot_id')).stdout.Trim()

$protocolDoc=[ordered]@{
    schemaVersion='jmoa-petclinic-target-only-protocol-v1';protocol=$protocol;preRegisteredAt=[DateTime]::UtcNow.ToString('o')
    support=[ordered]@{scope='PAIR';fixedSettleSeconds=$SupportSettleSeconds;minReadyAvailableMemoryBytes=734003200;healthRequired=$true;zeroRestarts=$true;zeroSwap=$true;zeroOom=$true;psiFullAvg10=0;memoryExcludedFromProductDelta=$true}
    target=[ordered]@{warmupSeconds=$WarmupSeconds;settleSeconds=$SettleSeconds;requests=81;minInArmAvailableMemoryBytes=314572800;metrics=@('process.Pss','process.Private_Dirty','cgroup.memory.current','cgroup.memory.stat.anon','cgroup.memory.stat.file')}
    controls=[ordered]@{b0Orders=@('B0_A_TO_B0_B','B0_B_TO_B0_A');v2Orders=@('V2_A_TO_V2_B','V2_B_TO_V2_A');maxMedianAbsPssKb=$MaxNoisePssDriftKb;maxMedianAbsPrivateDirtyKb=$MaxNoisePrivateDirtyDriftKb;maxMedianAbsMemoryCurrentBytes=$MaxNoiseMemoryCurrentDriftBytes}
    productOrders=@('B0_TO_V2','V2_TO_B0','B0_TO_V2')
    confirmation=[ordered]@{requiredValidArms=6;minPairedWins=2;pssKb=$ConfirmedPssGateKb;privateDirtyKb=$ConfirmedPrivateDirtyGateKb;memoryCurrentBytes=$ConfirmedMemoryCurrentGateBytes;strictPssKb=$TrustedPssGateKb}
    redesignAfterTargetEvidence='FORBIDDEN'
}
Write-JmoaJson $protocolDoc (Join-Path $reports 'petclinic-target-only-v1-protocol.json')

if($DryRun){
    $readiness=[ordered]@{schemaVersion='jmoa-petclinic-target-only-readiness-v1';protocol=$protocol;ready=$true;manifestSha256=$actualCampaignSha;images=$resolvedImages;artifactHashes=[ordered]@{b0=$b0Sha;v2=$v2Sha;materializationManifest=$materializationManifestSha;artifactLineage=$artifactLineageSha};artifactGate=$artifactGate;lineageGate=$lineageGate;configTreeSha256=$configTreeSha;protocolDefinition=$protocolDoc}
    Write-JmoaJson $readiness (Join-Path $reports 'campaign-readiness.json')
    Complete-ScenarioLedger -Status READY -Result $readiness|Out-Null
    exit 0
}

function Assert-HostContinuity([string]$Context){
    $now=(Invoke-ScenarioCommand -Step "host continuity $Context" -Executable '/bin/bash' -Arguments @('-lc','cat /proc/sys/kernel/random/boot_id')).stdout.Trim()
    if($now-ne$bootId){Complete-ScenarioLedger -Status CAMPAIGN_INTERRUPTED_BY_HOST_POWER_EVENT -Result @{context=$Context}|Out-Null;throw 'Host boot ID changed.'}
}
function Write-ScenarioCommandLedger([string]$ScenarioId,[string]$OutputDirectory){
    $records=[Collections.Generic.List[object]]::new()
    foreach($ndjson in @(Get-ChildItem -LiteralPath $ledgers -Filter commands.ndjson -Recurse -File|Where-Object{$_.FullName -match [regex]::Escape($ScenarioId)})){
        foreach($line in @(Get-Content -LiteralPath $ndjson.FullName|Where-Object{$_-match'\S'})){
            $record=$line|ConvertFrom-Json
            $record|Add-Member -NotePropertyName sourceLedgerDirectory -NotePropertyValue $ndjson.Directory.FullName -Force
            $records.Add($record)
        }
    }
    $ordered=@($records|Sort-Object {[datetime]$_.startedUtc},sequence)
    $builder=[Text.StringBuilder]::new()
    [void]$builder.AppendLine("# $ScenarioId Command Ledger")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('Every support, target, workload, capture, transition, and teardown command is listed chronologically with its recorded response.')
    foreach($record in $ordered){
        $stdout=if($record.rawStdoutPath){Get-Content -Raw -LiteralPath (Join-Path $record.sourceLedgerDirectory ([string]$record.rawStdoutPath))}else{''}
        $stderr=if($record.rawStderrPath){Get-Content -Raw -LiteralPath (Join-Path $record.sourceLedgerDirectory ([string]$record.rawStderrPath))}else{''}
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("## $($record.startedUtc) - $($record.step)")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("- Command: ``$($record.commandLine)``")
        [void]$builder.AppendLine("- Exit code: $($record.exitCode)")
        [void]$builder.AppendLine("- Source ledger: ``$($record.sourceLedgerDirectory)``")
        [void]$builder.AppendLine()
        [void]$builder.AppendLine('stdout:')
        [void]$builder.AppendLine('```text')
        [void]$builder.AppendLine($stdout.TrimEnd())
        [void]$builder.AppendLine('```')
        [void]$builder.AppendLine('stderr:')
        [void]$builder.AppendLine('```text')
        [void]$builder.AppendLine($stderr.TrimEnd())
        [void]$builder.AppendLine('```')
    }
    New-JmoaDirectory $OutputDirectory
    $path=Join-Path $OutputDirectory 'scenario-command-ledger.md'
    Write-JmoaText $builder.ToString() $path
    $summary=[ordered]@{schemaVersion='jmoa-target-only-scenario-ledger-v1';scenarioId=$ScenarioId;commandCount=$ordered.Count;path=$path;sha256=(Get-JmoaSha256 $path).ToUpperInvariant()}
    Write-JmoaJson $summary (Join-Path $OutputDirectory 'scenario-command-ledger.json')
    $summary
}
function Invoke-SharedSupportScreen {
    param([string]$ScenarioId,[string]$CaptureRoot,[int]$PairIndex,[string]$FirstVariant,[string]$BaselineImage,[string]$CandidateImage,[string]$BaselineArtifact,[string]$CandidateArtifact,[string]$ExecutionMode='PAIR')
    if((Get-CampaignTreeSha256 -Root $frozenConfig)-ne$configTreeSha){throw 'Frozen config changed.'}
    $supportDir=Join-Path $captures "support/$ScenarioId";$supportLedger=Join-Path $ledgers "$ScenarioId-support-launch";$supportStopLedger=Join-Path $ledgers "$ScenarioId-support-stop"
    $safe=$ScenarioId-replace'[^A-Za-z0-9_.-]','-';$network="jmoa-$safe-net";$cfg="jmoa-$safe-cfg";$disc="jmoa-$safe-disc"
    & $supportLaunch -OutputDirectory $supportDir -PairId $ScenarioId -ConfigImage $configImage -DiscoveryImage $discoveryImage -ConfigRepo $frozenConfig -SettleSeconds $SupportSettleSeconds -MinAvailableMemoryBytes 734003200 -ContainerCli $ContainerCli -LedgerDirectory $supportLedger
    if(-not$?){throw "Support admission failed for $ScenarioId."}
    $screenOk=$false
    try{
        $common=@{SupportNetwork=$network;CustomerReadyTimeoutSeconds=$HealthTimeoutSeconds;MinAvailableMemoryBeforeTargetBytes=314572800;ContainerCli=$ContainerCli}
        $args=@{
            BaselineLaunchScript=$targetLaunch;CandidateLaunchScript=$targetLaunch;BaselineContainerName="$safe-b";CandidateContainerName="$safe-c";WorkloadScript=$workloadScript
            HealthUrl='http://localhost:8081/actuator/health';Service='customers-service';LaunchMode='EXPLODED_BOOT_APP_SHARED_SUPPORT';RuntimePolicy='NO_CDS_LOW_DIRTY'
            BaselineArtifactPath=$BaselineArtifact;CandidateArtifactPath=$CandidateArtifact;BaselineLaunchParameters=(@{}+$common+@{Image=$BaselineImage});CandidateLaunchParameters=(@{}+$common+@{Image=$CandidateImage})
            StopScript=$targetStop;TransitionScript=$transitionScript;TransitionScriptParameters=@{ConfigContainerName=$cfg;DiscoveryContainerName=$disc;ContainerCli=$ContainerCli}
            ContainerCli=$ContainerCli;PairIndex=$PairIndex;FirstVariant=$FirstVariant;ExecutionMode=$ExecutionMode;CaptureRoot=$CaptureRoot;LedgerDirectory=(Join-Path $ledgers $ScenarioId)
            MallocArenaMax='1';WarmupSeconds=$WarmupSeconds;PostWorkloadSnapshotSeconds=@($SettleSeconds);HealthTimeoutSeconds=$HealthTimeoutSeconds;FailOnFailure=$true
            CapturePodmanMachinePressure=$true;MinPodmanAvailableMemoryBytes=314572800;MinPostArmAvailableMemoryBytes=314572800;MaxPodmanSwapUsedBytes=0
            RequireSwapDisabled=$true;RequireZeroOomEvents=$true;MaxPodmanMemoryPressureSomeAvg10=999.0;MaxPodmanMemoryPressureFullAvg10=0.0;DropPageCacheBeforeVariant=$true
            WorkloadId='petclinic-corrected-27x3'
        }
        & $screenScript @args
        $screenOk=$?
        if(-not$screenOk){throw "Target screen failed for $ScenarioId."}
    }finally{
        & $supportStop -OutputDirectory $supportDir -PairId $ScenarioId -ContainerCli $ContainerCli -LedgerDirectory $supportStopLedger
        Write-ScenarioCommandLedger -ScenarioId $ScenarioId -OutputDirectory $supportDir|Out-Null
    }
    if(-not$screenOk){throw "Target screen did not complete for $ScenarioId."}
    Assert-HostContinuity "after $ScenarioId"
}
function Test-Semantics([string]$Root,[int]$Pair){
    $sem=Compare-CampaignSemantics -PairIndex $Pair -BaselineSemanticPath (Join-Path $Root "b$Pair/semantic-requests.json") -CandidateSemanticPath (Join-Path $Root "c$Pair/semantic-requests.json")
    $state=Compare-CampaignDataState -PairIndex $Pair -BaselineDataStatePath (Join-Path $Root "b$Pair/data-state.json") -CandidateDataStatePath (Join-Path $Root "c$Pair/data-state.json")
    [ordered]@{semanticErrors=[int]$sem.semanticErrors;dataStatePassed=[bool]$state.passed;semantic=$sem;dataState=$state}
}
function Test-Noise([string]$Label,[string]$Artifact,[string]$Root){
    $s1=Test-Semantics $Root 1;$s2=Test-Semantics $Root 2
    $input=[ordered]@{schema='jmoa-same-artifact-noise-input-v2';controls=@([ordered]@{label=$Label;artifactSha256=Get-JmoaSha256 $Artifact;pairs=@(
        [ordered]@{id="${Label}_p1";order='A_TO_B';first=Read-CampaignRunMemory (Join-Path $Root 'b1');second=Read-CampaignRunMemory (Join-Path $Root 'c1');semanticErrors=$s1.semanticErrors+$(if($s1.dataStatePassed){0}else{1})},
        [ordered]@{id="${Label}_p2";order='B_TO_A';first=Read-CampaignRunMemory (Join-Path $Root 'c2');second=Read-CampaignRunMemory (Join-Path $Root 'b2');semanticErrors=$s2.semanticErrors+$(if($s2.dataStatePassed){0}else{1})}
    )})}
    $inputPath=Join-Path $reports "noise-$($Label.ToLower())-input.json";$out=Join-Path $reports "noise-$($Label.ToLower())";Write-JmoaJson $input $inputPath
    & $noiseAnalyzer -InputPath $inputPath -OutputDirectory $out -MaxPssDriftKb $MaxNoisePssDriftKb -MaxPrivateDirtyDriftKb $MaxNoisePrivateDirtyDriftKb -MaxMemoryCurrentDriftBytes $MaxNoiseMemoryCurrentDriftBytes -MinReversedPairsPerControl 2|Out-Null
    Get-Content -Raw (Join-Path $out 'same-artifact-noise.json')|ConvertFrom-Json
}

# Capacity is one B0 arm and is never included in any median.
$capacityRoot=Join-Path $captures 'capacity'
Invoke-SharedSupportScreen -ScenarioId 'capacity-b0' -CaptureRoot $capacityRoot -PairIndex 1 -FirstVariant BASELINE_FIRST -BaselineImage $b0Image -CandidateImage $b0Image -BaselineArtifact $b0Artifact -CandidateArtifact $b0Artifact -ExecutionMode BASELINE_ONLY
$capacityWorkload=Get-Content -Raw (Join-Path $capacityRoot 'b1/workload-result.json')|ConvertFrom-Json
$capacityEnv=Get-Content -Raw (Join-Path $capacityRoot 'b1/environment-validity.json')|ConvertFrom-Json
$capacityPassed=([int]$capacityWorkload.requests-eq81-and[int]$capacityWorkload.errors-eq0-and[bool]$capacityEnv.passed)
$completeCapacityCaptures=@('smaps_rollup.txt','smaps.txt','memory.current','memory.stat','nmt-summary.txt','heap-info.txt','class-histogram.txt')|ForEach-Object{Test-Path (Join-Path $capacityRoot "b1/$_")}|Where-Object{$_-eq$false}|Measure-Object
$capacityPassed=$capacityPassed-and($completeCapacityCaptures.Count-eq0)
$capacityReport=[ordered]@{schemaVersion='jmoa-petclinic-target-only-capacity-v1';includedInMedians=$false;workload=$capacityWorkload;environment=$capacityEnv;completeTargetCaptures=($completeCapacityCaptures.Count-eq0);passed=$capacityPassed;terminalOutcome=if($capacityPassed){'CAPACITY_QUALIFICATION_PASSED'}else{'STOPPED_INSUFFICIENT_2G_TARGET_CAPACITY'}}
Write-JmoaJson $capacityReport (Join-Path $reports 'capacity-qualification.json')
if(-not$capacityPassed){Complete-ScenarioLedger -Status STOPPED_INSUFFICIENT_2G_TARGET_CAPACITY -Result $capacityReport|Out-Null;exit 2}

Invoke-SharedSupportScreen 'control-b0-1' $b0Controls 1 BASELINE_FIRST $b0Image $b0Image $b0Artifact $b0Artifact
Invoke-SharedSupportScreen 'control-b0-2' $b0Controls 2 CANDIDATE_FIRST $b0Image $b0Image $b0Artifact $b0Artifact
$b0Noise=Test-Noise B0 $b0Artifact $b0Controls;Write-JmoaJson $b0Noise (Join-Path $reports 'noise-b0-summary.json')
if(-not[bool]$b0Noise.qualified){Complete-ScenarioLedger -Status TARGET_B0_RUNTIME_VARIANCE -Result $b0Noise|Out-Null;exit 2}

Invoke-SharedSupportScreen 'control-v2-1' $v2Controls 1 BASELINE_FIRST $v2Image $v2Image $v2Artifact $v2Artifact
Invoke-SharedSupportScreen 'control-v2-2' $v2Controls 2 CANDIDATE_FIRST $v2Image $v2Image $v2Artifact $v2Artifact
$v2Noise=Test-Noise V2 $v2Artifact $v2Controls;Write-JmoaJson $v2Noise (Join-Path $reports 'noise-v2-summary.json')
if(-not[bool]$v2Noise.qualified){Complete-ScenarioLedger -Status TARGET_V2_RUNTIME_VARIANCE -Result $v2Noise|Out-Null;exit 2}

Invoke-SharedSupportScreen 'product-1' $product 1 BASELINE_FIRST $b0Image $v2Image $b0Artifact $v2Artifact
Invoke-SharedSupportScreen 'product-2' $product 2 CANDIDATE_FIRST $b0Image $v2Image $b0Artifact $v2Artifact
Invoke-SharedSupportScreen 'product-3' $product 3 BASELINE_FIRST $b0Image $v2Image $b0Artifact $v2Artifact

$semanticPairs=[Collections.Generic.List[object]]::new();$semanticErrors=0;$statePassed=$true
for($i=1;$i-le3;$i++){$s=Test-Semantics $product $i;$semanticPairs.Add($s);$semanticErrors+=$s.semanticErrors;if(-not$s.dataStatePassed){$statePassed=$false}}
Write-JmoaJson ([ordered]@{schemaVersion='jmoa-target-only-semantics-v1';semanticErrors=$semanticErrors;dataStatePassed=$statePassed;pairs=$semanticPairs.ToArray()}) (Join-Path $reports 'semantic-equivalence.json')

$evidenceArgs=@('-N',"${pluginCoordinates}:evidence",'-Djmoa.evidence.enabled=true',"-Djmoa.evidence.inputDir=$product","-Djmoa.evidence.outputDir=$evidenceDir","-Djmoa.evidence.expectedPolicy=$runtimePolicy",'-Djmoa.evidence.requireArtifactHashes=true','-Djmoa.evidence.requireWorkloadZeroErrors=true','-Djmoa.evidence.requireSmapsArithmetic=true','-Djmoa.evidence.failOnInvalidRun=true')
$evidenceCmd=Invoke-ScenarioCommand -Step 'V2-C target-only evidence analysis' -Executable $maven -Arguments $evidenceArgs -WorkingDirectory $repositoryRoot -AllowFailure
$attributionArgs=@('-N',"${pluginCoordinates}:attribution",'-Djmoa.attribution.enabled=true',"-Djmoa.attribution.inputDir=$product","-Djmoa.attribution.outputDir=$attributionDir","-Djmoa.evidence.expectedPolicy=$runtimePolicy",'-Djmoa.attribution.requireV2CValid=true','-Djmoa.attribution.diagnosticOnly=false')
$attributionCmd=Invoke-ScenarioCommand -Step 'V2-D target-only attribution' -Executable $maven -Arguments $attributionArgs -WorkingDirectory $repositoryRoot -AllowFailure
$confirmation=Get-Content -Raw (Join-Path $evidenceDir 'jmoa-paired-confirmation.json')|ConvertFrom-Json
$validation=Get-Content -Raw (Join-Path $evidenceDir 'jmoa-evidence-validation.json')|ConvertFrom-Json
$attribution=Get-Content -Raw (Join-Path $attributionDir 'jmoa-memory-attribution.json')|ConvertFrom-Json
$v2dPassed=($attributionCmd.exitCode-eq0-and[bool](Get-CampaignJsonProp $attribution 'v2cValid'))
$checks=[ordered]@{
    sixValidArms=([int]$validation.runs-eq6-and[int]$validation.invalidRuns-eq0);confirmedWin=([string]$confirmation.verdict-eq'CONFIRMED_WIN')
    pairedWins=([int]$confirmation.pairedWins-ge2);pss=([long]$confirmation.medianPssDeltaKb-le$ConfirmedPssGateKb)
    privateDirty=([long]$confirmation.medianPrivateDirtyDeltaKb-le$ConfirmedPrivateDirtyGateKb);memoryCurrent=([long]$confirmation.medianMemoryCurrentDeltaBytes-le$ConfirmedMemoryCurrentGateBytes)
    semantics=($semanticErrors-eq0-and$statePassed);v2d=$v2dPassed
}
$confirmed=-not($checks.Values-contains$false);$strict=$confirmed-and([long]$confirmation.medianPssDeltaKb-le$TrustedPssGateKb)
$terminal=if($strict){'TRUSTED_PRODUCT_WIN'}elseif($confirmed){'CONFIRMED_PRODUCT_WIN_BELOW_4MIB'}else{'PRODUCT_EFFECT_NOT_CONFIRMED'}
$gate=[ordered]@{schemaVersion='jmoa-petclinic-target-only-product-gate-v1';protocol=$protocol;terminalOutcome=$terminal;checks=$checks;medians=[ordered]@{pssKb=[long]$confirmation.medianPssDeltaKb;privateDirtyKb=[long]$confirmation.medianPrivateDirtyDeltaKb;memoryCurrentBytes=[long]$confirmation.medianMemoryCurrentDeltaBytes};pairedWins=[int]$confirmation.pairedWins;thresholds=$protocolDoc.confirmation;supportMemoryIncluded=$false;v2d=$attribution}
Write-JmoaJson $gate (Join-Path $reports 'product-gate.json')
Write-JmoaText -Path (Join-Path $reports 'campaign-report.md') -Value @"
# PetClinic Target-Only Campaign

- Protocol: **$protocol**
- Outcome: **$terminal**
- V2-C: $($confirmation.verdict), valid arms $([int]$validation.runs-[int]$validation.invalidRuns)/$($validation.runs)
- Paired wins: $($confirmation.pairedWins)/3
- Median target PSS: $($confirmation.medianPssDeltaKb) KB
- Median target Private_Dirty: $($confirmation.medianPrivateDirtyDeltaKb) KB
- Median target memory.current: $($confirmation.medianMemoryCurrentDeltaBytes) bytes
- Strict PSS gate: <= $TrustedPssGateKb KB
- Support memory included in product delta: **false**
- V2-D target-only attribution passed: **$v2dPassed**

SUPPORT_CALIBRATION_V2 remains preserved as diagnostic evidence of support JVM warmup. It did not
establish that customers-service B0 and V2 were incomparable.
"@
Complete-ScenarioLedger -Status $terminal -Result $gate|Out-Null
if($terminal-eq'PRODUCT_EFFECT_NOT_CONFIRMED'){exit 2}
