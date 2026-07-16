param(
    [Parameter(Mandatory)][string]$LaunchScript,
    [Parameter(Mandatory)][string]$StopScript,
    [Parameter(Mandatory)][string]$WorkloadScript,
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ExpectedArchive,
    [string[]]$LaunchArguments = @(),
    [string[]]$StopArguments = @(),
    [string[]]$WorkloadArguments = @(),
    [string]$ContainerCli = 'podman',
    [string]$JcmdExecutable = 'jcmd',
    [int]$HealthTimeoutSeconds = 180,
    [int]$ReadinessSettleSeconds = 20
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
foreach($path in @($LaunchScript,$StopScript,$WorkloadScript)){
    if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Required script does not exist: $path"}
}
New-JmoaDirectory -Path $RunDirectory
if(Test-Path -LiteralPath $ExpectedArchive){Remove-Item -LiteralPath $ExpectedArchive -Force}
$started=[DateTime]::UtcNow
$launched=$false
try{
    & $LaunchScript -RunDirectory $RunDirectory -ContainerName $ContainerName -Variant $Variant @LaunchArguments
    if($LASTEXITCODE -ne 0){throw "Training launch failed with exit code $LASTEXITCODE."}
    $launched=$true
    $health=Wait-JmoaHttpHealth -HealthUrl $HealthUrl -TimeoutSeconds $HealthTimeoutSeconds
    if(-not $health.passed){throw "Training health check failed: $($health.error)"}
    if($ReadinessSettleSeconds -gt 0){Start-Sleep -Seconds $ReadinessSettleSeconds}
    $javaPid=Get-JmoaJavaPid -ContainerCli $ContainerCli -ContainerName $ContainerName -JavaProcessPattern 'java'
    if([string]::IsNullOrWhiteSpace($javaPid)){throw 'Could not locate training JVM.'}
    $workloadPath=Join-Path $RunDirectory 'training-workload.json'
    & $WorkloadScript -OutputPath $workloadPath -BaseUrl $HealthUrl -ContainerName $ContainerName -Variant $Variant @WorkloadArguments
    if($LASTEXITCODE -ne 0){throw "Training workload failed with exit code $LASTEXITCODE."}
    $cleanJcmd="env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS $JcmdExecutable"
    foreach($capture in @(
        @{name='classloader-stats.txt';command="$cleanJcmd $javaPid VM.classloader_stats"},
        @{name='metaspace.txt';command="$cleanJcmd $javaPid VM.metaspace"},
        @{name='nmt-summary.txt';command="$cleanJcmd $javaPid VM.native_memory summary"}
    )){
        $result=Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command $capture.command
        Write-JmoaText -Value $result.output -Path (Join-Path $RunDirectory $capture.name)
        if($result.exitCode -ne 0){throw "Training diagnostic failed: $($capture.command)"}
    }
} finally {
    try {
        if($launched){
            $logs=Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('logs',$ContainerName)
            Write-JmoaText -Value $logs.output -Path (Join-Path $RunDirectory 'application.log')
            $stop=Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('stop','--time','120',$ContainerName)
            Write-JmoaText -Value $stop.output -Path (Join-Path $RunDirectory 'application-stop.log')
            if($stop.exitCode -ne 0){throw "Could not stop training JVM cleanly: $($stop.output)"}
            $archiveDeadline=[DateTime]::UtcNow.AddSeconds(30)
            while(-not(Test-Path -LiteralPath $ExpectedArchive -PathType Leaf) -and [DateTime]::UtcNow -lt $archiveDeadline){Start-Sleep -Seconds 1}
        }
    } finally {
        & $StopScript -RunDirectory $RunDirectory -ContainerName $ContainerName -Variant $Variant @StopArguments
    }
}
if(-not(Test-Path -LiteralPath $ExpectedArchive -PathType Leaf)){throw "CDS training did not produce archive: $ExpectedArchive"}
$workload=Get-Content -Raw -LiteralPath (Join-Path $RunDirectory 'training-workload.json')|ConvertFrom-Json
$manifest=[ordered]@{
    metadataVersion='jmoa-deterministic-cds-training-v1';variant=$Variant;status=if($workload.errorCount -eq 0){'PASSED'}else{'FAILED'}
    startedAt=$started.ToString('o');completedAt=[DateTime]::UtcNow.ToString('o');readinessSettleSeconds=$ReadinessSettleSeconds
    workloadRequests=$workload.requests;workloadErrors=$workload.errorCount;archiveSha256=Get-JmoaSha256 $ExpectedArchive
    archiveBytes=[long](Get-Item -LiteralPath $ExpectedArchive).Length
    claimBoundary='Variant-specific diagnostic CDS training. The archive must only be used with its matching artifact.'
}
Write-JmoaJson -Value $manifest -Path (Join-Path $RunDirectory 'cds-training-manifest.json')
if($manifest.status -ne 'PASSED'){exit 1}
Write-Host "CDS training completed for ${Variant}: $ExpectedArchive"
