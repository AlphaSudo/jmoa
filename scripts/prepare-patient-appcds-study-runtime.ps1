param(
    [Parameter(Mandatory)][string]$EvidenceRoot,
    [string]$StudyDirectory=''
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if([string]::IsNullOrWhiteSpace($StudyDirectory)){$StudyDirectory=Join-Path $EvidenceRoot 'runtime/appcds-study'}
New-JmoaDirectory $StudyDirectory
$StudyDirectory=(Resolve-Path -LiteralPath $StudyDirectory).Path
$EvidenceRoot=(Resolve-Path -LiteralPath $EvidenceRoot).Path
$runtimeRoot=Join-Path $EvidenceRoot 'runtime'
foreach($version in @('v1','v2')){
    foreach($relative in @("overlay-$version-nocds.yml","overlay-$version-measure.yml","overlay-$version-train.yml","patient-$version-nocds-launch.ps1","patient-$version-launch.ps1","patient-$version-train-launch.ps1")){
        if(-not(Test-Path -LiteralPath (Join-Path $runtimeRoot $relative)-PathType Leaf)){throw "Missing runtime template: $relative"}
    }
}
function Write-Generated([string]$content,[string]$path){Set-Content -LiteralPath $path -Value $content -Encoding utf8}
function New-Launch([string]$template,[string]$overlay,[string]$destination){
    $text=Get-Content -Raw -LiteralPath $template;$updated=[regex]::Replace($text,"-OverlayFile\s+'[^']+'",("-OverlayFile '"+$overlay.Replace("'","''")+"'"))
    if($updated-eq$text){throw "Could not replace OverlayFile in launch template: $template"};Write-Generated $updated $destination
}
$records=@()
foreach($version in @('v1','v2')){
    $noCdsOverlay=Join-Path $runtimeRoot "overlay-$version-nocds.yml";$measureOverlay=Join-Path $runtimeRoot "overlay-$version-measure.yml";$trainOverlay=Join-Path $runtimeRoot "overlay-$version-train.yml"
    $baseOverlay=Join-Path $StudyDirectory "overlay-$version-base.yml"
    $base=(Get-Content -Raw -LiteralPath $noCdsOverlay).Replace('-Xshare:off','-Xshare:on')
    if($base-match'SharedArchiveFile|ArchiveClassesAtExit|AOTCache|AOTMode'){throw 'Generated BASE overlay contains an application archive or AOT cache flag.'}
    Write-Generated $base $baseOverlay
    New-Launch (Join-Path $runtimeRoot "patient-$version-nocds-launch.ps1") $baseOverlay (Join-Path $StudyDirectory "patient-$version-base-launch.ps1")
    foreach($profile in @('startup','representative')){
        foreach($copy in @('a','b')){
            $stem="$version-$profile-$copy";$containerArchive="/opt/leyden/appcds-study/$stem.jsa";$containerLog="/opt/leyden/appcds-study/$stem-training.log"
            $train=Get-Content -Raw -LiteralPath $trainOverlay
            $train=[regex]::Replace($train,'-XX:ArchiveClassesAtExit=\S+',"-XX:ArchiveClassesAtExit=$containerArchive")
            $train=[regex]::Replace($train,'-Xlog:class\+load=info:file=\S+',"-Xlog:cds+dynamic=debug,cds=debug,class+load=info:file=$containerLog")
            if($train-notmatch[regex]::Escape($containerArchive)-or$train-match'AOTCache|AOTMode'){throw "Generated training overlay failed validation: $stem"}
            $trainPath=Join-Path $StudyDirectory "overlay-$stem-train.yml";Write-Generated $train $trainPath
            New-Launch (Join-Path $runtimeRoot "patient-$version-train-launch.ps1") $trainPath (Join-Path $StudyDirectory "patient-$stem-train-launch.ps1")
            $app=Get-Content -Raw -LiteralPath $measureOverlay
            $app=[regex]::Replace($app,'-XX:SharedArchiveFile=\S+',"-XX:SharedArchiveFile=$containerArchive")
            if($app-notmatch[regex]::Escape($containerArchive)-or$app-match'ArchiveClassesAtExit|AOTCache|AOTMode'){throw "Generated APP overlay failed validation: $stem"}
            $appPath=Join-Path $StudyDirectory "overlay-$stem-app.yml";Write-Generated $app $appPath
            New-Launch (Join-Path $runtimeRoot "patient-$version-launch.ps1") $appPath (Join-Path $StudyDirectory "patient-$stem-app-launch.ps1")
            $records += [ordered]@{version=$version;profile=$profile.ToUpperInvariant();copy=$copy.ToUpperInvariant();archiveFile="$stem.jsa";trainingLogFile="$stem-training.log";trainingOverlaySha256=Get-JmoaSha256 $trainPath;appOverlaySha256=Get-JmoaSha256 $appPath}
        }
    }
}
$manifest=[ordered]@{
    metadataVersion='patient-dynamic-appcds-runtime-preparation-v1';status='PREPARED';profiles=@('STARTUP','REPRESENTATIVE');copies=@('A','B')
    policyArms=@('OFF','BASE','APP');aotCacheFlagsAllowed=$false;records=$records
    claimBoundary='Generated private runtime inputs. Raw overlays and service configuration remain under target and must not be committed.'
}
Write-JmoaJson $manifest (Join-Path $StudyDirectory 'runtime-preparation-manifest.json')
Write-Host "Prepared Patient AppCDS study runtime under: $StudyDirectory"
exit 0
