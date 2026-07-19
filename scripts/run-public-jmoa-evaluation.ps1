param(
  [ValidateSet('Audit','HistoricalReplay','Noise','ThreeArm','Full')][string]$Mode='Audit',
  [ValidateSet('petclinic','doctor','patient')][string]$Service='petclinic',
  [string]$ConfigPath='',
  [string]$OutputDirectory='target/public-evaluation',
  [switch]$AcknowledgePrivateService
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if($Service-ne'petclinic'-and-not$AcknowledgePrivateService){throw 'Doctor and Patient require private service assets. Pass -AcknowledgePrivateService only after provisioning them.'}
New-JmoaDirectory $OutputDirectory
$auditDir=Join-Path $OutputDirectory 'audit'
& (Join-Path $PSScriptRoot 'invoke-audited-command.ps1') -CommandId "$Service-command-ledger" -Executable (Get-Command pwsh).Source -ArgumentList @('-NoProfile','-File',(Join-Path $PSScriptRoot 'recover-historical-command-ledgers.ps1')) -WorkingDirectory (Resolve-Path (Join-Path $PSScriptRoot '..')).Path -AuditDirectory $auditDir
& (Join-Path $PSScriptRoot 'capture-runtime-fingerprint.ps1') -OutputDirectory (Join-Path $OutputDirectory 'fingerprint') -WorkloadScript $(if($ConfigPath){$ConfigPath}else{''}) | Out-Null
if($Mode-eq'Audit'){return}
if(-not$ConfigPath-or-not(Test-Path $ConfigPath-PathType Leaf)){throw "$Mode requires -ConfigPath with the service's audited campaign configuration."}
$config=Get-Content $ConfigPath -Raw|ConvertFrom-Json
if($config.service-ne$Service){throw 'Config service does not match -Service.'}
if($Mode-in@('HistoricalReplay','Full')){
  if(-not($config.PSObject.Properties.Name-contains'historicalReplayScript')){throw 'Config does not define historicalReplayScript.'}
  & (Join-Path $PSScriptRoot 'invoke-audited-command.ps1') -CommandId "$Service-historical-replay" -Executable (Get-Command pwsh).Source -ArgumentList @('-NoProfile','-File',$config.historicalReplayScript) -WorkingDirectory $config.workingDirectory -AuditDirectory $auditDir -AcceptedEvidence
}
if($Mode-in@('Noise','Full')){
  if(-not($config.PSObject.Properties.Name-contains'noiseInputPath')){throw 'Config does not define noiseInputPath.'}
  & (Join-Path $PSScriptRoot 'analyze-same-artifact-noise.ps1') -InputPath $config.noiseInputPath -OutputDirectory (Join-Path $OutputDirectory 'noise') | Out-Null
}
if($Mode-in@('ThreeArm','Full')){
  if(-not($config.PSObject.Properties.Name-contains'variantRunnerScript')){throw 'Config does not define variantRunnerScript.'}
  if(-not($config.PSObject.Properties.Name-contains'admission')){throw 'Three-arm execution requires an explicit admission block.'}
  foreach($gate in @('lineageQualified','historicalReplayQualified','sameArtifactNoiseQualified')){
    if(-not($config.admission.PSObject.Properties.Name-contains$gate)-or-not[bool]$config.admission.$gate){throw "Three-arm campaign blocked: admission gate '$gate' is not qualified."}
  }
  & (Join-Path $PSScriptRoot 'runtime-three-arm-block.ps1') -VariantRunnerScript $config.variantRunnerScript -ConfigPath $ConfigPath -OutputDirectory (Join-Path $OutputDirectory 'three-arm') -FailOnFailure
}
