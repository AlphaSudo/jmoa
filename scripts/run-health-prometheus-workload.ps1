param(
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][string]$BaseUrl,
    [string]$ContainerName = '',
    [string]$Variant = '',
    [int]$Rounds = 3,
    [int]$IterationsPerRound = 100,
    [int]$TimeoutSeconds = 15,
    [int]$InterRoundDelaySeconds = 2
)

$ErrorActionPreference = 'Stop'
if ($Rounds -lt 1 -or $IterationsPerRound -lt 1) { throw 'Rounds and IterationsPerRound must be positive.' }
$errors = [System.Collections.Generic.List[string]]::new()
$requests = 0
$started = Get-Date
$base = $BaseUrl.TrimEnd('/')

foreach ($round in 1..$Rounds) {
    foreach ($iteration in 1..$IterationsPerRound) {
        foreach ($path in @('/actuator/health', '/actuator/prometheus')) {
            $requests++
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri ($base + $path) -TimeoutSec $TimeoutSeconds -SkipHttpErrorCheck
                if ([int]$response.StatusCode -ne 200) {
                    $errors.Add("round=$round iteration=$iteration path=$path status=$($response.StatusCode)")
                }
            } catch {
                $errors.Add("round=$round iteration=$iteration path=$path error=$($_.Exception.Message)")
            }
        }
    }
    if ($round -lt $Rounds -and $InterRoundDelaySeconds -gt 0) {
        Start-Sleep -Seconds $InterRoundDelaySeconds
    }
}

$result = [ordered]@{
    metadataVersion = 'jmoa-health-prometheus-workload-v1'
    variant = $Variant
    containerName = $ContainerName
    rounds = $Rounds
    iterationsPerRound = $IterationsPerRound
    requests = $requests
    errorCount = $errors.Count
    errors = @($errors)
    elapsedSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 2)
    completedAt = (Get-Date).ToUniversalTime().ToString('o')
}
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Host "Workload completed: $requests requests, $($errors.Count) errors."
if ($errors.Count -gt 0) { exit 1 }
