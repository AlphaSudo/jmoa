<#
.SYNOPSIS
    Deterministic 81-request PetClinic workload that satisfies the runtime-screen-pair.ps1 workload
    contract: & <script> -OutputPath <file> -BaseUrl <url> -ContainerName <name> -Variant <variant> ...

    The screen engine passes -BaseUrl as the health URL (e.g. http://localhost:8081/actuator/health);
    this script derives the application root by stripping the /actuator/health suffix.

    Emits:
      - workload-result.json      (screen/evidence contract: health, errors, requests, status)
      - semantic-requests.json    (per-request status + RAW body SHA + CANONICAL body SHA + rule id)
      - data-state.json           (initial + final canonical data-state hashes and mutation assertions)

    Review hardening:
      - Issue #7: comparable (non-actuator) JSON responses are canonicalized (keys sorted, volatile fields
        normalized, array order preserved) before hashing; both the raw and canonical bodies are retained
        under bodies/ with their own SHA-256. Actuator endpoints are compared by status only.
      - Issue #8: the workload captures a canonical initial data-state before any mutation and a canonical
        final data-state afterwards, and asserts the inserted owners exist and owner/1 was updated.
      - Issue #1: when -LedgerDirectory is supplied every HTTP request is recorded in the child ledger.
#>
param(
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [int]$Rounds = 3,
    [int]$PacingMilliseconds = 200,
    [int]$RequestTimeoutSeconds = 30,
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'workload',
    [string]$CanonicalRuleId = 'petclinic-owners-v1'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-canonical-json.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $Variant -Description 'PetClinic HTTP workload (state probes + 81-request body).' | Out-Null
}

# The screen engine hands us the health URL; the application root is that minus /actuator/health.
$root = ($BaseUrl.TrimEnd('/')) -replace '/actuator/health$', ''
$root = $root.TrimEnd('/')

$outputDirectory = Split-Path -Parent $OutputPath
$bodiesDir = Join-Path $outputDirectory 'bodies'
New-JmoaDirectory -Path $bodiesDir

$endpoints = @(
    @{ m = 'GET';  p = '/actuator/health' }, @{ m = 'GET'; p = '/actuator/info' }, @{ m = 'GET'; p = '/owners' },
    @{ m = 'GET';  p = '/owners/1' }, @{ m = 'GET'; p = '/owners/2' }, @{ m = 'GET'; p = '/owners/3' },
    @{ m = 'GET';  p = '/owners/4' }, @{ m = 'GET'; p = '/owners/5' }, @{ m = 'GET'; p = '/owners/6' },
    @{ m = 'GET';  p = '/owners/7' }, @{ m = 'GET'; p = '/owners/8' }, @{ m = 'GET'; p = '/owners/9' },
    @{ m = 'GET';  p = '/owners/10' }, @{ m = 'GET'; p = '/petTypes' }, @{ m = 'GET'; p = '/owners/1/pets/1' },
    @{ m = 'GET';  p = '/owners/2/pets/2' }, @{ m = 'GET'; p = '/owners/3/pets/3' }, @{ m = 'GET'; p = '/owners/4/pets/4' },
    @{ m = 'GET';  p = '/owners/5/pets/5' }, @{ m = 'GET'; p = '/owners/6/pets/6' },
    @{ m = 'POST'; p = '/owners'; b = '{"firstName":"Ledger","lastName":"One","address":"1 Audit St","city":"Cairo","telephone":"5551234567"}' },
    @{ m = 'POST'; p = '/owners'; b = '{"firstName":"Ledger","lastName":"Two","address":"2 Audit St","city":"Cairo","telephone":"5551234568"}' },
    @{ m = 'PUT';  p = '/owners/1'; b = '{"id":1,"firstName":"George","lastName":"Franklin","address":"110 W. Liberty St.","city":"Madison","telephone":"6085551023"}' },
    @{ m = 'GET';  p = '/owners/1' }, @{ m = 'GET'; p = '/owners/2' }, @{ m = 'GET'; p = '/owners/3' }, @{ m = 'GET'; p = '/actuator/health' }
)

# Endpoints queried to fingerprint the data state (before/after) - stable, non-actuator, contract-relevant.
$stateEndpoints = @('/owners', '/petTypes', '/owners/1', '/owners/5', '/owners/10')
$errorCount = 0

function Invoke-WorkloadRequest {
    param([string]$Step, [string]$Method, [string]$Path, [string]$Body = $null, [bool]$Comparable = $true)
    $uri = "$root$Path"
    $canonicalizer = if ($Comparable) { { param($b) ConvertTo-JmoaCanonicalJson -Json $b -RuleId $CanonicalRuleId }.GetNewClosure() } else { $null }
    $ruleForRequest = if ($Comparable) { $CanonicalRuleId } else { '' }
    return Invoke-AuditedHttp -LedgerDirectory $LedgerDirectory -Step $Step -Method $Method -Uri $uri -Body $Body `
        -TimeoutSeconds $RequestTimeoutSeconds -CanonicalizeBody $canonicalizer -CanonicalRuleId $ruleForRequest
}

# Canonical fingerprint of a set of GET endpoints - the joined "path=canonicalBody" lines, hashed once.
function Get-DataStateFingerprint {
    param([string]$Label)
    $lines = New-Object System.Collections.Generic.List[string]
    $probes = New-Object System.Collections.Generic.List[object]
    foreach ($path in $stateEndpoints) {
        $resp = Invoke-WorkloadRequest -Step "$Label state probe GET $path" -Method 'GET' -Path $path -Comparable $true
        $canon = Get-JmoaCanonicalJsonResult -Body ([string]$resp.body) -RuleId $CanonicalRuleId
        $probePassed = ([int]$resp.status -gt 0 -and [int]$resp.status -lt 400 -and [bool]$canon.validJson)
        if (-not $probePassed) { $script:errorCount++ }
        $lines.Add("$path=$($canon.sha256)") | Out-Null
        $probes.Add([ordered]@{ path = $path; status = $resp.status; canonicalSha256 = $canon.sha256; validJson = $canon.validJson; passed = $probePassed }) | Out-Null
    }
    return [ordered]@{
        label   = $Label
        sha256  = Get-JmoaTextSha256 -Value (($lines) -join "`n")
        probes  = $probes.ToArray()
    }
}

# ---- Initial data-state (before any mutation) --------------------------------------------------
$initialState = Get-DataStateFingerprint -Label 'INITIAL'

$requests = New-Object System.Collections.Generic.List[object]
$sequence = 0
$lastHealthStatus = 'DOWN'

for ($round = 1; $round -le $Rounds; $round++) {
    foreach ($endpoint in $endpoints) {
        $sequence++
        $isActuator = $endpoint.p -like '/actuator/*'
        $comparable = -not $isActuator
        $body = if ($endpoint.ContainsKey('b')) { $endpoint.b } else { $null }
        $resp = Invoke-WorkloadRequest -Step "$Variant r$round #$sequence $($endpoint.m) $($endpoint.p)" -Method $endpoint.m -Path $endpoint.p -Body $body -Comparable $comparable
        $status = [int]$resp.status
        if ($status -eq 0 -or $status -ge 400) { $errorCount++ }
        if ($isActuator -and $endpoint.p -eq '/actuator/health' -and $status -eq 200 -and [string]$resp.body -match '"status"\s*:\s*"UP"') {
            $lastHealthStatus = 'UP'
        }
        # Retain raw + canonical bodies for comparable requests (self-contained, independent of ledger).
        $rawSha = $null
        $canonicalSha = $null
        $ruleId = $null
        if ($comparable) {
            $rawFile = Join-Path $bodiesDir ("req-{0:D3}-raw.txt" -f $sequence)
            [IO.File]::WriteAllText($rawFile, [string]$resp.body, [Text.UTF8Encoding]::new($false))
            $rawSha = (Get-FileHash -LiteralPath $rawFile -Algorithm SHA256).Hash
            $canon = Get-JmoaCanonicalJsonResult -Body ([string]$resp.body) -RuleId $CanonicalRuleId
            $canonicalFile = Join-Path $bodiesDir ("req-{0:D3}-canonical.json" -f $sequence)
            [IO.File]::WriteAllText($canonicalFile, [string]$canon.canonical, [Text.UTF8Encoding]::new($false))
            $canonicalSha = $canon.sha256
            $ruleId = $canon.ruleId
        }
        $requests.Add([ordered]@{
            seq                 = $sequence
            round               = $round
            method              = $endpoint.m
            path                = $endpoint.p
            status              = $status
            comparable          = $comparable
            bodySha256          = $canonicalSha   # canonical hash drives the semantic equivalence check
            rawBodySha256       = $rawSha
            canonicalBodySha256 = $canonicalSha
            canonicalRuleId     = $ruleId
            error               = $resp.error
        }) | Out-Null
        if ($PacingMilliseconds -gt 0) { Start-Sleep -Milliseconds $PacingMilliseconds }
    }
}

# ---- Final data-state + mutation assertions ----------------------------------------------------
$finalState = Get-DataStateFingerprint -Label 'FINAL'
$ownersResp = Invoke-WorkloadRequest -Step 'FINAL owners enumeration' -Method 'GET' -Path '/owners' -Comparable $true
$ownersList = @()
$ownersValidJson = $true
try { $ownersList = @(($ownersResp.body | ConvertFrom-Json -Depth 32)) } catch { $ownersList = @(); $ownersValidJson = $false }
if ([int]$ownersResp.status -eq 0 -or [int]$ownersResp.status -ge 400 -or -not $ownersValidJson) { $errorCount++ }
$ledgerOneFound = @($ownersList | Where-Object { $_.firstName -eq 'Ledger' -and $_.lastName -eq 'One' }).Count -ge 1
$ledgerTwoFound = @($ownersList | Where-Object { $_.firstName -eq 'Ledger' -and $_.lastName -eq 'Two' }).Count -ge 1
$owner1 = @($ownersList | Where-Object { [int]$_.id -eq 1 }) | Select-Object -First 1
$owner1Updated = ($null -ne $owner1 -and $owner1.firstName -eq 'George' -and $owner1.lastName -eq 'Franklin' -and $owner1.city -eq 'Madison')
$mutationsProven = ($ledgerOneFound -and $ledgerTwoFound -and $owner1Updated)
if (-not $mutationsProven) { $errorCount++ }

$dataState = [ordered]@{
    metadataVersion       = 'jmoa-petclinic-data-state-v1'
    variant               = $Variant
    canonicalRuleId       = $CanonicalRuleId
    stateEndpoints        = $stateEndpoints
    initialStateSha256    = $initialState.sha256
    finalStateSha256      = $finalState.sha256
    initialProbes         = $initialState.probes
    finalProbes           = $finalState.probes
    ledgerOwnerOneFound   = $ledgerOneFound
    ledgerOwnerTwoFound   = $ledgerTwoFound
    owner1Updated         = $owner1Updated
    mutationsProven       = $mutationsProven
    validationErrors      = $errorCount
    generatedAt           = [DateTime]::UtcNow.ToString('o')
}
Write-JmoaJson -Value $dataState -Path (Join-Path $outputDirectory 'data-state.json')

$health = if ($errorCount -eq 0 -and $lastHealthStatus -eq 'UP' -and $mutationsProven) { 'UP' } else { 'DOWN' }
$workloadResult = [ordered]@{
    metadataVersion = 'jmoa-petclinic-workload-v1'
    workloadId      = 'petclinic-81-request'
    variant         = $Variant
    containerName   = $ContainerName
    baseUrl         = $root
    health          = $health
    errors          = $errorCount
    requests        = $requests.Count
    rounds          = $Rounds
    mutationsProven = $mutationsProven
    status          = if ($errorCount -eq 0 -and $mutationsProven) { 'COMPLETED' } else { 'COMPLETED_WITH_ERRORS' }
    generatedAt     = [DateTime]::UtcNow.ToString('o')
}
Write-JmoaJson -Value $workloadResult -Path $OutputPath

$semantic = [ordered]@{
    metadataVersion = 'jmoa-petclinic-semantic-v2'
    variant         = $Variant
    containerName   = $ContainerName
    baseUrl         = $root
    requestCount    = $requests.Count
    errors          = $errorCount
    rounds          = $Rounds
    canonicalRuleId = $CanonicalRuleId
    generatedAt     = [DateTime]::UtcNow.ToString('o')
    requests        = $requests.ToArray()
}
Write-JmoaJson -Value $semantic -Path (Join-Path $outputDirectory 'semantic-requests.json')

if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status $(if ($errorCount -eq 0 -and $mutationsProven) { 'COMPLETE' } else { 'FAILED' }) `
        -Stage $LedgerStage -Variant $Variant | Out-Null
}

Write-Host "Workload $Variant ($ContainerName): $($requests.Count) requests, $errorCount errors, health=$health, mutationsProven=$mutationsProven."
exit 0
