param(
    [Parameter(Mandatory)][string]$B0Artifact,
    [Parameter(Mandatory)][string]$V1Artifact,
    [Parameter(Mandatory)][string]$V2DependencyTree,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$MavenExecutable,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$SourceRevision = '305a1f13e4f961001d4e6cb50a9db51dc3fc5967',
    [string]$AcceptedV2ArtifactId = '',
    [string]$ImagePrefix = 'localhost/jmoa-three-artifact-petclinic',
    [string]$ContainerCli = 'podman'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

foreach ($path in @($B0Artifact, $V1Artifact)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required artifact file is missing: $path" }
}
foreach ($path in @($V2DependencyTree, $ConfigRepo, $JavaHome)) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Required directory is missing: $path" }
}
if (-not (Test-Path -LiteralPath $MavenExecutable -PathType Leaf)) {
    throw "Maven executable is missing: $MavenExecutable"
}
if (-not [string]::IsNullOrWhiteSpace($AcceptedV2ArtifactId) -and $AcceptedV2ArtifactId -notmatch '^[0-9A-Fa-f]{64}$') {
    throw 'AcceptedV2ArtifactId must be a 64-character hexadecimal identifier when supplied.'
}
if (Test-Path -LiteralPath $OutputDirectory) {
    if (@(Get-ChildItem -LiteralPath $OutputDirectory -Force -ErrorAction SilentlyContinue).Count -gt 0) {
        throw "Freeze output must be a new or empty directory: $OutputDirectory"
    }
}

New-JmoaDirectory $OutputDirectory
$ledger = Join-Path $OutputDirectory 'freeze-command-ledger'
Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'freeze' -Variant 'B0,V1,V2' `
    -Description 'PetClinic three-artifact materialization: exact input hashes, exploded Boot extraction, V2 dependency replacement, immutable image builds, and private campaign config.' | Out-Null

$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw "Java executable is missing: $java" }
$materialized = Join-Path $OutputDirectory 'materialized'
$b0Root = Join-Path $materialized 'b0'
$v1Root = Join-Path $materialized 'v1'
$v2Root = Join-Path $materialized 'v2'
New-JmoaDirectory $materialized

function Invoke-FreezeCommand {
    param([string]$Step, [string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory = $OutputDirectory)
    Invoke-AuditedExternal -Executable $Executable -Arguments $Arguments -WorkingDirectory $WorkingDirectory `
        -LedgerDirectory $ledger -Step $Step -TimeoutSeconds 1800
}

function Write-ExplodedDockerfile {
    param([string]$Root)
    $text = @'
FROM eclipse-temurin:17
WORKDIR application
ENV SPRING_PROFILES_ACTIVE=docker
COPY dependencies/ ./
RUN true
COPY spring-boot-loader/ ./
RUN true
COPY snapshot-dependencies/ ./
RUN true
COPY application/ ./
EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
'@
    Write-JmoaText -Value $text -Path (Join-Path $Root 'Dockerfile')
}

function Resolve-ImageId {
    param([string]$Reference, [string]$Role)
    $result = Invoke-FreezeCommand -Step "resolve immutable $Role image ID" -Executable $ContainerCli `
        -Arguments @('image', 'inspect', '--format', '{{.Id}}', $Reference)
    $id = ($result.stdout -split '\r?\n' | Where-Object { $_ -match '\S' } | Select-Object -First 1).Trim()
    if ([string]::IsNullOrWhiteSpace($id)) { throw "Could not resolve $Role image ID: $Reference" }
    return ($id -replace '^sha256:', '')
}

function Get-ExplodedFingerprint {
    param([string]$Root)
    $app = Join-Path $Root 'application'
    $dependencies = Join-Path $Root 'dependencies\BOOT-INF\lib'
    $loader = Join-Path $Root 'spring-boot-loader'
    [ordered]@{
        application = Get-CampaignTreeSha256 -Root $app
        dependencies = Get-CampaignTreeSha256 -Root $dependencies
        springBootLoader = Get-CampaignTreeSha256 -Root $loader
        dependencyJarCount = @(Get-ChildItem -LiteralPath $dependencies -Filter '*.jar' -File).Count
        dependencyBytes = [long](Get-ChildItem -LiteralPath $dependencies -Filter '*.jar' -File | Measure-Object Length -Sum).Sum
    }
}

function Get-JarJmoaEntryCount {
    param([string]$Jar)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        return @($archive.Entries | Where-Object { $_.FullName -match '(^|/)(jmoa-|io/github/.*/jmoa|com/yourorg/jmoa)' }).Count
    } finally {
        $archive.Dispose()
    }
}

$completed = $false
try {
    $inputHashes = [ordered]@{
        b0 = Get-CampaignArtifactSha256 -Path $B0Artifact
        v1 = Get-CampaignArtifactSha256 -Path $V1Artifact
        v2DependencyTree = Get-CampaignArtifactSha256 -Path $V2DependencyTree
        config = Get-CampaignTreeSha256 -Root $ConfigRepo
    }
    Invoke-FreezeCommand -Step 'record extraction JDK' -Executable $java -Arguments @('-version') | Out-Null
    Invoke-FreezeCommand -Step 'record Podman version' -Executable $ContainerCli -Arguments @('version') | Out-Null

    foreach ($variant in @(
        [pscustomobject]@{ id = 'B0'; artifact = $B0Artifact; root = $b0Root },
        [pscustomobject]@{ id = 'V1'; artifact = $V1Artifact; root = $v1Root }
    )) {
        New-JmoaDirectory $variant.root
        Invoke-FreezeCommand -Step "extract $($variant.id) exploded Boot artifact" -Executable $java `
            -Arguments @('-Djarmode=tools', '-jar', $variant.artifact, 'extract', '--launcher', '--layers', '--destination', $variant.root)
        Write-ExplodedDockerfile -Root $variant.root
    }

    Copy-Item -LiteralPath $v1Root -Destination $v2Root -Recurse
    $v2Libraries = Join-Path $v2Root 'dependencies\BOOT-INF\lib'
    $resolvedV2Libraries = [IO.Path]::GetFullPath($v2Libraries)
    $resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
    if (-not $resolvedV2Libraries.StartsWith($resolvedOutput, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to replace dependencies outside the freeze output: $resolvedV2Libraries"
    }
    Remove-Item -LiteralPath $resolvedV2Libraries -Recurse -Force
    New-JmoaDirectory $resolvedV2Libraries
    Copy-Item -Path (Join-Path $V2DependencyTree '*') -Destination $resolvedV2Libraries -Recurse -Force
    Write-ExplodedDockerfile -Root $v2Root

    $tagSuffix = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ').ToLowerInvariant()
    $tags = [ordered]@{
        B0 = "$ImagePrefix-b0:$tagSuffix"
        V1 = "$ImagePrefix-v1:$tagSuffix"
        V2 = "$ImagePrefix-v2:$tagSuffix"
    }
    foreach ($variant in @(
        [pscustomobject]@{ id = 'B0'; root = $b0Root },
        [pscustomobject]@{ id = 'V1'; root = $v1Root },
        [pscustomobject]@{ id = 'V2'; root = $v2Root }
    )) {
        Invoke-FreezeCommand -Step "build immutable $($variant.id) runtime image" -Executable $ContainerCli `
            -Arguments @('build', '--pull=never', '-t', $tags[$variant.id], $variant.root) | Out-Null
    }

    $imageIds = [ordered]@{
        B0 = Resolve-ImageId -Reference $tags.B0 -Role 'B0'
        V1 = Resolve-ImageId -Reference $tags.V1 -Role 'V1'
        V2 = Resolve-ImageId -Reference $tags.V2 -Role 'V2'
        config = Resolve-ImageId -Reference $ConfigImage -Role 'config support'
        discovery = Resolve-ImageId -Reference $DiscoveryImage -Role 'discovery support'
    }
    $fingerprints = [ordered]@{
        B0 = Get-ExplodedFingerprint -Root $b0Root
        V1 = Get-ExplodedFingerprint -Root $v1Root
        V2 = Get-ExplodedFingerprint -Root $v2Root
    }
    $jmoaProof = [ordered]@{
        b0EntryCount = Get-JarJmoaEntryCount -Jar $B0Artifact
        v1EntryCount = Get-JarJmoaEntryCount -Jar $V1Artifact
        v2RuntimeLibraryCount = @(Get-ChildItem -LiteralPath $V2DependencyTree -Filter 'jmoa-runtime-lib-*.jar' -File).Count
    }
    if ($jmoaProof.b0EntryCount -ne 0) { throw "Strict B0 contains $($jmoaProof.b0EntryCount) JMOA entries." }
    if ($jmoaProof.v1EntryCount -eq 0) { throw 'Accepted V1 does not contain the expected JMOA materialization entries.' }
    if ($jmoaProof.v2RuntimeLibraryCount -ne 1) { throw "V2 requires exactly one JMOA runtime library; found $($jmoaProof.v2RuntimeLibraryCount)." }
    if ($fingerprints.V1.application -ne $fingerprints.V2.application -or $fingerprints.V1.springBootLoader -ne $fingerprints.V2.springBootLoader) {
        throw 'V2 materialization changed application or Spring Boot loader bytes; only the dependency tree may differ.'
    }
    if ($fingerprints.V2.dependencies -ne $inputHashes.v2DependencyTree) {
        throw "Materialized V2 dependency hash mismatch: expected $($inputHashes.v2DependencyTree), got $($fingerprints.V2.dependencies)"
    }

    $launchScript = Join-Path $PSScriptRoot 'campaign-launch-petclinic-stack.ps1'
    $stopScript = Join-Path $PSScriptRoot 'campaign-stop-petclinic-stack.ps1'
    $workloadScript = Join-Path $PSScriptRoot 'campaign-workload-petclinic.ps1'
    $configPath = Join-Path $OutputDirectory 'petclinic-three-artifact-config.json'
    $config = [ordered]@{
        schemaVersion = 'jmoa-three-artifact-service-config-v1'
        protocol = 'PETCLINIC_CUSTOMERS_B0_V1_V2_BALANCED_V1'
        service = 'spring-petclinic-customers-service'
        launchMode = 'EXPLODED_BOOT_APP'
        runtimePolicy = 'NO_CDS_LOW_DIRTY'
        repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
        launchScript = $launchScript
        stopScript = $stopScript
        workloadScript = $workloadScript
        healthUrl = 'http://localhost:8081/actuator/health'
        workloadId = 'corrected-petclinic-27x3'
        expectedRequests = 81
        warmupSeconds = 20
        settleSeconds = 5
        healthTimeoutSeconds = 900
        runtimeArtifactPath = ''
        cdsEnabled = $false
        appCdsEnabled = $false
        mallocArenaMax = '1'
        dropPageCache = $true
        pageCacheResetStrategy = 'PODMAN_MACHINE_RESTART'
        podmanMachineName = 'podman-machine-default'
        captureHostPressure = $true
        minAvailableMemoryBytes = 1073741824
        maxMemoryPressureSomeAvg10 = 1.0
        maxMemoryPressureFullAvg10 = 0.1
        containerCli = $ContainerCli
        mavenExecutable = $MavenExecutable
        pluginCoordinates = 'com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2'
        launchParameters = [ordered]@{
            ConfigImage = $imageIds.config
            DiscoveryImage = $imageIds.discovery
            ConfigRepo = (Resolve-Path -LiteralPath $ConfigRepo).Path
            ContainerCli = $ContainerCli
        }
        stopParameters = [ordered]@{ ContainerCli = $ContainerCli }
        workloadParameters = [ordered]@{ Rounds = 3; PacingMilliseconds = 200; RequestTimeoutSeconds = 30 }
        variants = [ordered]@{
            B0 = [ordered]@{ artifactPath = (Resolve-Path $B0Artifact).Path; artifactSha256 = $inputHashes.b0; cdsArchivePath = ''; cdsArchiveSha256 = ''; image = $imageIds.B0; imageId = $imageIds.B0; launchParameters = [ordered]@{} }
            V1 = [ordered]@{ artifactPath = (Resolve-Path $V1Artifact).Path; artifactSha256 = $inputHashes.v1; cdsArchivePath = ''; cdsArchiveSha256 = ''; image = $imageIds.V1; imageId = $imageIds.V1; launchParameters = [ordered]@{} }
            V2 = [ordered]@{ artifactPath = (Resolve-Path $V2DependencyTree).Path; artifactSha256 = $inputHashes.v2DependencyTree; acceptedProductArtifactId = $AcceptedV2ArtifactId; cdsArchivePath = ''; cdsArchiveSha256 = ''; image = $imageIds.V2; imageId = $imageIds.V2; launchParameters = [ordered]@{} }
        }
    }
    Write-JmoaJson -Value $config -Path $configPath
    $freeze = [ordered]@{
        schemaVersion = 'jmoa-petclinic-three-artifact-private-freeze-v1'
        protocol = $config.protocol
        sourceRevision = $SourceRevision
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        deployment = $config.launchMode
        runtimePolicy = $config.runtimePolicy
        inputHashes = $inputHashes
        acceptedProductIds = [ordered]@{ V2 = $AcceptedV2ArtifactId }
        fingerprints = $fingerprints
        jmoaProof = $jmoaProof
        images = [ordered]@{
            B0 = [ordered]@{ tag = $tags.B0; id = $imageIds.B0 }
            V1 = [ordered]@{ tag = $tags.V1; id = $imageIds.V1 }
            V2 = [ordered]@{ tag = $tags.V2; id = $imageIds.V2 }
            config = [ordered]@{ requested = $ConfigImage; id = $imageIds.config }
            discovery = [ordered]@{ requested = $DiscoveryImage; id = $imageIds.discovery }
        }
        invariants = [ordered]@{
            v1V2ApplicationEqual = $true
            v1V2SpringBootLoaderEqual = $true
            v2DependencyInputMaterializedExactly = $true
            cdsDisabled = $true
            javaagentDisabled = $true
            mallocArenaMax = '1'
        }
        campaignConfig = $configPath
    }
    Write-JmoaJson -Value $freeze -Path (Join-Path $OutputDirectory 'petclinic-three-artifact-freeze.json')
    $completed = $true
} finally {
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status $(if ($completed) { 'COMPLETE' } else { 'FAILED' }) `
        -Stage 'freeze' -Variant 'B0,V1,V2' | Out-Null
}

Write-Host "PetClinic three-artifact freeze prepared at $OutputDirectory"
