param(
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$EndpointsFile,
    [string]$EndpointBaseUrl = "",
    [string]$HeadersFile = "",
    [string]$ContainerName = "",
    [string]$ContainerCli = "podman",
    [int]$HealthTimeoutSeconds = 90,
    [int[]]$ExpectedHealthStatus = @(200),
    [string[]]$ForbiddenLogPatterns = @('VerifyError', 'ClassFormatError', 'NoSuchMethodError', 'NoClassDefFoundError', 'ExceptionInInitializerError'),
    [string]$OutputDir = "target/jmoa-semantic-smoke",
    [switch]$FailOnFailure
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

if (-not (Test-Path -LiteralPath $EndpointsFile -PathType Leaf)) {
    throw "Endpoints file does not exist: $EndpointsFile"
}
New-JmoaDirectory -Path $OutputDir
$endpointDocument = Get-Content -Raw -LiteralPath $EndpointsFile | ConvertFrom-Json
$endpoints = if ($endpointDocument -is [System.Array]) { @($endpointDocument) } else { @($endpointDocument.endpoints) }
$sharedHeaders = @{}
if (-not [string]::IsNullOrWhiteSpace($HeadersFile)) {
    if (-not (Test-Path -LiteralPath $HeadersFile -PathType Leaf)) { throw "Headers file does not exist: $HeadersFile" }
    $sharedHeaders = ConvertTo-JmoaHashtable -Value (Get-Content -Raw -LiteralPath $HeadersFile | ConvertFrom-Json)
}

$health = Wait-JmoaHttpHealth -HealthUrl $HealthUrl -TimeoutSeconds $HealthTimeoutSeconds -ExpectedStatus $ExpectedHealthStatus
$endpointBase = if ([string]::IsNullOrWhiteSpace($EndpointBaseUrl)) { $HealthUrl } else { $EndpointBaseUrl }
$results = @()
$errors = 0
foreach ($endpoint in $endpoints) {
    $headers = @{} + $sharedHeaders
    $endpointHeaders = if ($endpoint.PSObject.Properties['headers']) { $endpoint.headers } else { $null }
    foreach ($entry in (ConvertTo-JmoaHashtable -Value $endpointHeaders).GetEnumerator()) { $headers[$entry.Key] = $entry.Value }
    $endpointUrl = if ($endpoint.PSObject.Properties['url']) { [string]$endpoint.url } else { '' }
    $endpointPath = if ($endpoint.PSObject.Properties['path']) { [string]$endpoint.path } else { '' }
    $endpointMethod = if ($endpoint.PSObject.Properties['method']) { [string]$endpoint.method } else { '' }
    $endpointExpectedStatus = if ($endpoint.PSObject.Properties['expectedStatus']) { [int]$endpoint.expectedStatus } else { 0 }
    $uri = if (-not [string]::IsNullOrWhiteSpace($endpointUrl)) { $endpointUrl } else { $endpointBase.TrimEnd('/') + '/' + $endpointPath.TrimStart('/') }
    $method = if (-not [string]::IsNullOrWhiteSpace($endpointMethod)) { $endpointMethod } else { 'GET' }
    $expected = if ($endpointExpectedStatus -gt 0) { $endpointExpectedStatus } else { 200 }
    $actual = 0
    $error = ''
    try {
        $response = Invoke-WebRequest -Uri $uri -Method $method -Headers $headers -SkipHttpErrorCheck -TimeoutSec 30
        $actual = [int]$response.StatusCode
    } catch {
        $error = $_.Exception.Message
    }
    $passed = $actual -eq $expected
    if (-not $passed) { $errors++ }
    $results += [ordered]@{ method = $method; url = $uri; expectedStatus = $expected; actualStatus = $actual; passed = $passed; error = $error }
}

$logMatches = @()
$logCollectionFailed = $false
if (-not [string]::IsNullOrWhiteSpace($ContainerName)) {
    $logs = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('logs', $ContainerName)
    if ($logs.exitCode -ne 0) {
        $logCollectionFailed = $true
    } else {
        foreach ($pattern in $ForbiddenLogPatterns) {
            if ($logs.output -match [regex]::Escape($pattern)) { $logMatches += $pattern }
        }
    }
}
$status = if (-not $health.passed) { 'FAILED_HEALTH' } elseif ($errors -gt 0) { 'FAILED_WORKLOAD' } elseif ($logCollectionFailed) { 'BLOCKED_LOG_COLLECTION' } elseif ($logMatches.Count -gt 0) { 'FAILED_JVM_LOG_SCAN' } else { 'PASSED' }
$report = [ordered]@{
    metadataVersion = 'v2o-semantic-smoke'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = $status
    health = if ($health.passed) { 'UP' } else { 'DOWN' }
    healthStatusCode = $health.statusCode
    endpointBaseUrl = $endpointBase
    workloadErrors = $errors
    requests = $results.Count
    endpointResults = $results
    forbiddenLogMatches = @($logMatches | Select-Object -Unique)
    logCollectionFailed = $logCollectionFailed
    claimBoundary = 'Semantic smoke only. A passed smoke is required before a screen but does not establish a runtime memory result.'
}
Write-JmoaJson -Value $report -Path (Join-Path $OutputDir 'jmoa-semantic-smoke.json')
$markdown = @"
# JMOA Semantic Smoke

- Status: ``$status``
- Health: ``$($report.health)``
- Requests: ``$($report.requests)``
- Workload errors: ``$errors``
- JVM/linkage log matches: ``$([string]::Join(', ', $report.forbiddenLogMatches))``

This smoke is a semantic gate, not a memory or startup claim.
"@
Write-JmoaText -Value $markdown -Path (Join-Path $OutputDir 'jmoa-semantic-smoke.md')
Write-Host "Semantic smoke report written to $OutputDir"
if ($FailOnFailure -and $status -ne 'PASSED') { exit 1 }
exit 0
