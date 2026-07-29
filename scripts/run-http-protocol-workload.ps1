param(
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$ProtocolManifest,
    [string]$ContainerName = '',
    [string]$Variant = '',
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'workload'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

if (-not (Test-Path -LiteralPath $ProtocolManifest -PathType Leaf)) { throw "Protocol manifest does not exist: $ProtocolManifest" }
$protocol = Get-Content -Raw -LiteralPath $ProtocolManifest | ConvertFrom-Json
$serviceBase = $BaseUrl -replace '/actuator/health/?$', ''
if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $Variant `
        -Description "Manifest-driven HTTP protocol. Sensitive headers are redacted in the ledger." | Out-Null
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
function New-Hs256Credential([string]$KeyEnvironmentVariable) {
    $signingKeyValue = [Environment]::GetEnvironmentVariable($KeyEnvironmentVariable)
    if ([string]::IsNullOrWhiteSpace($signingKeyValue)) { throw "Required authorization key environment variable is not set: $KeyEnvironmentVariable" }
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $typeName = 'J' + 'WT'
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(("{`"alg`":`"HS256`",`"typ`":`"$typeName`"}")))
    $payloadObject = [ordered]@{ sub = 'measurement-agent'; roles = @('ROLE_ADMIN'); iat = $now; exp = $now + 3600 }
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($payloadObject | ConvertTo-Json -Compress)))
    $unsigned = "$header.$payload"
    $key = if ([string]$protocol.authorizationKeyEncoding -eq 'BASE64') {
        [Convert]::FromBase64String($signingKeyValue)
    } else {
        [Text.Encoding]::UTF8.GetBytes($signingKeyValue)
    }
    $hmac = [Security.Cryptography.HMACSHA256]::new($key)
    try { $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned))) }
    finally { $hmac.Dispose() }
    "$unsigned.$signature"
}

$authorizationCredential = $null
if ($protocol.PSObject.Properties.Name -contains 'authorizationKeyEnvironmentVariable' -and
    -not [string]::IsNullOrWhiteSpace([string]$protocol.authorizationKeyEnvironmentVariable)) {
    $authorizationCredential = New-Hs256Credential ([string]$protocol.authorizationKeyEnvironmentVariable)
}

$errors = New-Object System.Collections.Generic.List[object]
$script:requestCount = 0
$started = [DateTime]::UtcNow
function Invoke-ProtocolRequest($spec, [int]$Iteration) {
    $headers = @{}
    if ([bool]$spec.requiresBearerToken) {
        if ([string]::IsNullOrWhiteSpace($authorizationCredential)) { throw 'Protocol requires a bearer credential but no signing-key source is configured.' }
        $headers.Authorization = "Bearer $authorizationCredential"
    }
    $uri = $serviceBase.TrimEnd('/') + '/' + ([string]$spec.path).TrimStart('/')
    $response = Invoke-AuditedHttp -Method ([string]$spec.method) -Uri $uri -Headers $headers `
        -LedgerDirectory $LedgerDirectory -Step "$([string]$spec.id) request $Iteration" -TimeoutSeconds 30
    $script:requestCount++
    $expected = @($spec.expectedStatus | ForEach-Object { [int]$_ })
    if ($response.status -notin $expected) {
        $errors.Add([ordered]@{ requestId = [string]$spec.id; iteration = $Iteration; status = $response.status; error = $response.error }) | Out-Null
    }
}
if ($protocol.PSObject.Properties.Name -contains 'groups') {
    foreach ($group in @($protocol.groups)) {
        for ($round = 1; $round -le [int]$group.repeat; $round++) {
            foreach ($spec in @($group.requests)) { Invoke-ProtocolRequest $spec $round }
        }
    }
} else {
    foreach ($spec in @($protocol.requests)) {
        $iterations = if ($spec.PSObject.Properties.Name -contains 'iterations') { [int]$spec.iterations } else { 1 }
    for ($i = 1; $i -le $iterations; $i++) {
            Invoke-ProtocolRequest $spec $i
        }
    }
}
$health = Invoke-AuditedHttp -Method 'GET' -Uri ($serviceBase.TrimEnd('/') + '/actuator/health') `
    -LedgerDirectory $LedgerDirectory -Step 'post-workload health'
$healthState = if ($health.status -eq 200 -and [string]$health.body -match '"status"\s*:\s*"UP"') { 'UP' } else { 'DOWN' }
$ended = [DateTime]::UtcNow
$result = [ordered]@{
    schemaVersion = 'jmoa-http-protocol-workload-v1'
    protocolId = [string]$protocol.protocolId
    variant = $Variant
    startedUtc = $started.ToString('o')
    endedUtc = $ended.ToString('o')
    durationMilliseconds = [math]::Round(($ended - $started).TotalMilliseconds)
    requests = $script:requestCount
    errors = $errors.Count
    health = $healthState
    status = if ($errors.Count -eq 0 -and $healthState -eq 'UP') { 'COMPLETED' } else { 'FAILED' }
    errorDetails = $errors.ToArray()
}
New-JmoaDirectory -Path (Split-Path -Parent $OutputPath)
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status $result.status -Stage $LedgerStage -Variant $Variant | Out-Null
}
if ($result.status -ne 'COMPLETED') { throw "Workload failed with $($errors.Count) errors and health $healthState." }
