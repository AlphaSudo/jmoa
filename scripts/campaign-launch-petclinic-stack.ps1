<#
.SYNOPSIS
    Frozen-image full-stack PetClinic launcher that satisfies the runtime-screen-pair.ps1
    launch contract: & <script> -RunDirectory <dir> -ContainerName <name> -Variant <BASELINE|CANDIDATE> @LaunchArguments

    Starts config-server + discovery-server + customers-service using pre-built, immutable
    images. Config/discovery names and the isolated network are derived from -ContainerName so
    campaign-stop-petclinic-stack.ps1 can tear the whole stack down deterministically.

    This launcher never builds an image; it only runs the frozen artifacts supplied by the
    performance campaign. NO_CDS_LOW_DIRTY policy is enforced through JVM flags + MALLOC_ARENA_MAX=1.

    Review hardening:
      - Issue #1: when -LedgerDirectory is supplied, every podman command (network create, image
        inspect, run, container inspect, exec, logs, pre-clean) is routed through the audited child
        ledger (campaign-audit-common.ps1), including each health probe.
      - Issue #2: all four images (config, discovery, B0/V2 customers) are resolved ONCE to their
        full immutable image IDs; containers are launched BY resolved image ID; after startup the
        live container image ID is proven equal to the resolved ID. stack-launch-info.json records
        requestedReference / resolvedImageId / launchedImageId per service.
      - Issue #10: the in-container JDK fingerprint (java -version, java -XshowSettings:properties
        -version, java.vendor / java.runtime.version / java.home) is captured for the customers
        container and written to runtime-jdk-fingerprint.json + embedded in stack-launch-info.json.
#>
param(
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$Image,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [int]$Port = 8081,
    [int]$ConfigPort = 8888,
    [int]$DiscoveryPort = 8761,
    [int]$ConfigReadyTimeoutSeconds = 180,
    [int]$DiscoveryReadyTimeoutSeconds = 180,
    [int]$CustomerReadyTimeoutSeconds = 900,
    [string]$ContainerCli = 'podman',
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'launch',
    [string]$LedgerVariant = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

if (-not (Test-Path -LiteralPath $ConfigRepo -PathType Container)) {
    throw "Config repository directory does not exist: $ConfigRepo"
}
New-JmoaDirectory -Path $RunDirectory

if ([string]::IsNullOrWhiteSpace($LedgerVariant)) { $LedgerVariant = $Variant }
if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $LedgerVariant -Description "Frozen full-stack launch for $Variant ($ContainerName): image resolution, podman run, health probes, image-ID proof, JDK fingerprint." | Out-Null
}

$network = "$ContainerName-net"
$configName = "$ContainerName-cfg"
$discoveryName = "$ContainerName-disc"

$configFlags = '-XX:+UseContainerSupport -XX:+UseSerialGC -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off'
$discoveryFlags = '-XX:+UseContainerSupport -XX:+UseSerialGC -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off'
$customerFlags = '-XX:+UseContainerSupport -XX:+UseSerialGC -Xms32m -Xmx256m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -XX:NativeMemoryTracking=summary -Xshare:off'

# Audited podman wrapper: routes through the child ledger when one is active, else a plain call.
function Invoke-Cli {
    param([string]$Description, [string[]]$CliArguments, [switch]$AllowFailure)
    return Invoke-AuditedExternal -Executable $ContainerCli -Arguments $CliArguments -LedgerDirectory $LedgerDirectory -Step $Description -AllowFailure:$AllowFailure
}

# Resolve a mutable image reference to its full immutable image ID (+ metadata) so the campaign can
# launch by ID and prove the live container ran exactly these bytes (Issue #2).
function Resolve-ImageInfo {
    param([string]$Reference, [string]$Role)
    $fmt = '{{.Id}}||{{.Created}}||{{.Architecture}}||{{range .RepoDigests}}{{.}} {{end}}'
    $probe = Invoke-Cli -Description "resolve $Role image ID ($Reference)" -CliArguments @('image', 'inspect', '--format', $fmt, $Reference)
    $line = ($probe.output -split '\r?\n' | Where-Object { $_ -match '\S' } | Select-Object -First 1)
    $parts = @($line -split '\|\|')
    $imageId = ($parts[0]).Trim()
    if ([string]::IsNullOrWhiteSpace($imageId)) {
        throw "Could not resolve immutable image ID for $Role image '$Reference'."
    }
    return [pscustomobject]@{
        role               = $Role
        requestedReference = $Reference
        resolvedImageId    = $imageId
        created            = if ($parts.Count -gt 1) { $parts[1].Trim() } else { '' }
        architecture       = if ($parts.Count -gt 2) { $parts[2].Trim() } else { '' }
        repoDigests        = if ($parts.Count -gt 3) { $parts[3].Trim() } else { '' }
    }
}

# Prove that a live container is running exactly the resolved image ID.
function Assert-LiveImageId {
    param([string]$Name, [string]$ExpectedImageId, [string]$Role)
    $probe = Invoke-Cli -Description "verify live image ID for $Role ($Name)" -CliArguments @('inspect', '--format', '{{.Image}}', $Name)
    $live = ($probe.output -split '\r?\n' | Where-Object { $_ -match '\S' } | Select-Object -First 1).Trim()
    $normExpected = ($ExpectedImageId -replace '^sha256:', '')
    $normLive = ($live -replace '^sha256:', '')
    $match = ($normLive -eq $normExpected) -or $normLive.StartsWith($normExpected) -or $normExpected.StartsWith($normLive)
    if (-not $match) {
        throw "Live container image ID mismatch for $Role ($Name): live '$live' != resolved '$ExpectedImageId'."
    }
    return $live
}

# In-container JDK fingerprint for the customers arm (Issue #10).
function Get-InContainerJdkFingerprint {
    param([string]$Name)
    $version = Invoke-Cli -Description 'customers JDK: java -version' -CliArguments @('exec', $Name, 'java', '-version')
    $settings = Invoke-Cli -Description 'customers JDK: java -XshowSettings:properties -version' -CliArguments @('exec', $Name, 'java', '-XshowSettings:properties', '-version')
    $settingsText = "$($settings.output)"
    function Read-Prop {
        param([string]$Key)
        $m = [regex]::Match($settingsText, [regex]::Escape($Key) + '\s*=\s*(.+)')
        if ($m.Success) { return $m.Groups[1].Value.Trim() }
        return ''
    }
    $stable = [ordered]@{
        javaVersionRaw     = "$($version.output)".Trim()
        javaVendor         = (Read-Prop 'java.vendor')
        javaRuntimeVersion = (Read-Prop 'java.runtime.version')
        javaVmVersion      = (Read-Prop 'java.vm.version')
        javaHome           = (Read-Prop 'java.home')
        osArch             = (Read-Prop 'os.arch')
    }
    foreach ($required in @('javaVersionRaw', 'javaVendor', 'javaRuntimeVersion', 'javaVmVersion', 'javaHome', 'osArch')) {
        if ([string]::IsNullOrWhiteSpace([string]$stable[$required])) {
            throw "Could not prove in-container JDK property '$required' for $Name."
        }
    }
    $fingerprint = [ordered]@{
        metadataVersion    = 'jmoa-runtime-jdk-fingerprint-v1'
        variant            = $Variant
        containerName      = $Name
        javaVersionRaw     = $stable.javaVersionRaw
        javaVendor         = $stable.javaVendor
        javaRuntimeVersion = $stable.javaRuntimeVersion
        javaVmVersion      = $stable.javaVmVersion
        javaHome           = $stable.javaHome
        osArch             = $stable.osArch
        capturedAtUtc      = [DateTime]::UtcNow.ToString('o')
    }
    $fingerprint.fingerprintSha256 = Get-JmoaTextSha256 -Value (
        "$($stable.javaVersionRaw)|$($stable.javaVendor)|$($stable.javaRuntimeVersion)|" +
        "$($stable.javaVmVersion)|$($stable.javaHome)|$($stable.osArch)"
    )
    return $fingerprint
}

function Remove-StackResiduals {
    foreach ($name in @($ContainerName, $discoveryName, $configName)) {
        Invoke-Cli -Description "pre-clean $name" -CliArguments @('rm', '-f', $name) -AllowFailure | Out-Null
    }
    Invoke-Cli -Description "pre-clean network $network" -CliArguments @('network', 'rm', $network) -AllowFailure | Out-Null
}

# Audited health wait: each probe is a ledgered HTTP request (Issue #1).
function Wait-AuditedHealth {
    param([string]$HealthUrl, [int]$TimeoutSeconds, [string]$Role)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $probe = 0
    $lastStatus = 0
    $lastError = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $probe++
        $resp = Invoke-AuditedHttp -Method 'GET' -Uri $HealthUrl -LedgerDirectory $LedgerDirectory -Step "$Role health probe $probe" -TimeoutSeconds 10
        $lastStatus = [int]$resp.status
        $lastError = "$($resp.error)"
        if ($lastStatus -eq 200) {
            return [pscustomobject]@{ passed = $true; statusCode = $lastStatus; probes = $probe; error = '' }
        }
        Start-Sleep -Seconds 2
    }
    return [pscustomobject]@{ passed = $false; statusCode = $lastStatus; probes = $probe; error = $lastError }
}

# Pre-clean any residual containers/network with the derived names.
Remove-StackResiduals

try {
    # ---- Resolve all four images to immutable IDs ONCE (Issue #2) --------------------------------
    $configInfo = Resolve-ImageInfo -Reference $ConfigImage -Role 'config'
    $discoveryInfo = Resolve-ImageInfo -Reference $DiscoveryImage -Role 'discovery'
    $customerInfo = Resolve-ImageInfo -Reference $Image -Role 'customers'

    Invoke-Cli -Description "create network $network" -CliArguments @('network', 'create', $network) | Out-Null

    Invoke-Cli -Description 'start config-server' -CliArguments @(
        'run', '-d',
        '--name', $configName,
        '--network', $network,
        '--network-alias', 'config-server',
        '-p', "${ConfigPort}:8888",
        '-v', "${ConfigRepo}:/app/config-repo:ro",
        '-e', 'SPRING_PROFILES_ACTIVE=native',
        '-e', 'GIT_REPO=/app/config-repo',
        '-e', 'MANAGEMENT_TRACING_ENABLED=false',
        '-e', 'MANAGEMENT_METRICS_ENABLED=false',
        '-e', "JAVA_TOOL_OPTIONS=$configFlags",
        $configInfo.resolvedImageId
    ) | Out-Null
    $configHealth = Wait-AuditedHealth -HealthUrl "http://localhost:$ConfigPort/actuator/health" -TimeoutSeconds $ConfigReadyTimeoutSeconds -Role 'config-server'
    if (-not $configHealth.passed) { throw "config-server did not become healthy: $($configHealth.error)" }
    $configLiveImageId = Assert-LiveImageId -Name $configName -ExpectedImageId $configInfo.resolvedImageId -Role 'config'

    Invoke-Cli -Description 'start discovery-server' -CliArguments @(
        'run', '-d',
        '--name', $discoveryName,
        '--network', $network,
        '--network-alias', 'discovery-server',
        '-p', "${DiscoveryPort}:8761",
        '-e', 'SPRING_PROFILES_ACTIVE=docker',
        '-e', 'CONFIG_SERVER_URI=http://config-server:8888',
        '-e', "JAVA_TOOL_OPTIONS=$discoveryFlags",
        $discoveryInfo.resolvedImageId
    ) | Out-Null
    $discoveryHealth = Wait-AuditedHealth -HealthUrl "http://localhost:$DiscoveryPort/actuator/health" -TimeoutSeconds $DiscoveryReadyTimeoutSeconds -Role 'discovery-server'
    if (-not $discoveryHealth.passed) { throw "discovery-server did not become healthy: $($discoveryHealth.error)" }
    $discoveryLiveImageId = Assert-LiveImageId -Name $discoveryName -ExpectedImageId $discoveryInfo.resolvedImageId -Role 'discovery'

    Invoke-Cli -Description 'start customers-service' -CliArguments @(
        'run', '-d',
        '--name', $ContainerName,
        '--network', $network,
        '--network-alias', 'customers-service',
        '-p', "${Port}:8081",
        '-e', 'SPRING_PROFILES_ACTIVE=docker',
        '-e', 'CONFIG_SERVER_URI=http://config-server:8888',
        '-e', "JAVA_TOOL_OPTIONS=$customerFlags",
        '-e', 'MALLOC_ARENA_MAX=1',
        $customerInfo.resolvedImageId
    ) | Out-Null
    $customerHealth = Wait-AuditedHealth -HealthUrl "http://localhost:$Port/actuator/health" -TimeoutSeconds $CustomerReadyTimeoutSeconds -Role 'customers-service'
    if (-not $customerHealth.passed) { throw "customers-service did not become healthy: $($customerHealth.error)" }
    $customerLiveImageId = Assert-LiveImageId -Name $ContainerName -ExpectedImageId $customerInfo.resolvedImageId -Role 'customers'

    # ---- In-container JDK fingerprint (Issue #10) ----------------------------------------------
    $jdkFingerprint = Get-InContainerJdkFingerprint -Name $ContainerName
    Write-JmoaJson -Value $jdkFingerprint -Path (Join-Path $RunDirectory 'runtime-jdk-fingerprint.json')

    $launchInfo = [ordered]@{
        metadataVersion = 'jmoa-stack-launch-info-v2'
        variant         = $Variant
        containerName   = $ContainerName
        configName      = $configName
        discoveryName   = $discoveryName
        network         = $network
        images          = [ordered]@{
            customers = [ordered]@{ requestedReference = $customerInfo.requestedReference; resolvedImageId = $customerInfo.resolvedImageId; launchedImageId = $customerLiveImageId; created = $customerInfo.created; architecture = $customerInfo.architecture; repoDigests = $customerInfo.repoDigests }
            config    = [ordered]@{ requestedReference = $configInfo.requestedReference; resolvedImageId = $configInfo.resolvedImageId; launchedImageId = $configLiveImageId; created = $configInfo.created; architecture = $configInfo.architecture; repoDigests = $configInfo.repoDigests }
            discovery = [ordered]@{ requestedReference = $discoveryInfo.requestedReference; resolvedImageId = $discoveryInfo.resolvedImageId; launchedImageId = $discoveryLiveImageId; created = $discoveryInfo.created; architecture = $discoveryInfo.architecture; repoDigests = $discoveryInfo.repoDigests }
        }
        configRepo      = (Resolve-Path -LiteralPath $ConfigRepo).Path
        port            = $Port
        customerFlags   = $customerFlags
        mallocArenaMax  = '1'
        jdkFingerprint  = $jdkFingerprint
        healthProbes    = [ordered]@{ config = $configHealth.probes; discovery = $discoveryHealth.probes; customers = $customerHealth.probes }
        launchedAtUtc   = [DateTime]::UtcNow.ToString('o')
    }
    Write-JmoaJson -Value $launchInfo -Path (Join-Path $RunDirectory 'stack-launch-info.json')

    if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
        Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'COMPLETE' -Stage $LedgerStage -Variant $LedgerVariant | Out-Null
    }
    Write-Host "Launched $Variant stack (customers container: $ContainerName, image $($customerInfo.resolvedImageId))."
    exit 0
} catch {
    Write-Warning "Stack launch failed for $Variant ($ContainerName): $($_.Exception.Message)"
    foreach ($name in @($ContainerName, $discoveryName, $configName)) {
        $log = Invoke-Cli -Description "capture logs $name (launch failure)" -CliArguments @('logs', '--tail', '200', $name) -AllowFailure
        if ($log.exitCode -eq 0) {
            Write-JmoaText -Value $log.output -Path (Join-Path $RunDirectory "launch-failure-$name.log")
        }
    }
    Remove-StackResiduals
    if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
        Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'FAILED' -Stage $LedgerStage -Variant $LedgerVariant | Out-Null
    }
    exit 1
}
