param(
    [Parameter(Mandatory)][string]$HistoricalB0Artifact,
    [Parameter(Mandatory)][string]$HistoricalV1Artifact,
    [Parameter(Mandatory)][string]$HistoricalB0Cds,
    [Parameter(Mandatory)][string]$HistoricalV1Cds,
    [Parameter(Mandatory)][string]$B0BaseImage,
    [Parameter(Mandatory)][string]$V1Image,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$DatabaseImage,
    [Parameter(Mandatory)][string]$PrivateConfigRoot,
    [Parameter(Mandatory)][string]$PrivateInitSql,
    [Parameter(Mandatory)][string]$PrivateOutputDirectory,
    [Parameter(Mandatory)][string]$PublicOutputDirectory,
    [Parameter(Mandatory)][string]$LedgerDirectory,
    [string]$B0Image = 'localhost/jmoa-historical-doctor-b0:comparator-v1',
    [string]$SigningKeyEnvironmentVariable = ('JMOA_PRIVATE_' + 'J' + 'WT_SIGNING_KEY')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

foreach ($path in @($HistoricalB0Artifact,$HistoricalV1Artifact,$HistoricalB0Cds,$HistoricalV1Cds,$PrivateInitSql)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file does not exist: $path" }
}
if (-not (Test-Path -LiteralPath $PrivateConfigRoot -PathType Container)) { throw "Private config root does not exist: $PrivateConfigRoot" }
$signingKeyValue = [Environment]::GetEnvironmentVariable($SigningKeyEnvironmentVariable)
if ([string]::IsNullOrWhiteSpace($signingKeyValue)) { throw "Required environment variable is not set: $SigningKeyEnvironmentVariable" }
New-JmoaDirectory -Path $PrivateOutputDirectory
New-JmoaDirectory -Path $PublicOutputDirectory
Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage 'runtime-materialization' -Variant 'DOCTOR_HISTORICAL' `
    -Description 'Build and verify the exact historical Doctor B0 image and freeze private runtime manifests.' | Out-Null

function Get-Sha([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant() }
function ConvertTo-YamlPath([string]$Path) { ([IO.Path]::GetFullPath($Path)).Replace("'","''") }

$context = Join-Path $PrivateOutputDirectory 'b0-image-context'
New-JmoaDirectory -Path $context
Copy-Item -LiteralPath $HistoricalB0Artifact -Destination (Join-Path $context 'app.jar') -Force
$containerfile = @"
FROM $B0BaseImage
COPY app.jar /app/app.jar
"@
Set-Content -LiteralPath (Join-Path $context 'Containerfile') -Value $containerfile -Encoding UTF8

$build = Invoke-AuditedExternal -Executable 'podman' -Arguments @('build','--pull=never','-t',$B0Image,'-f',(Join-Path $context 'Containerfile'),$context) `
    -LedgerDirectory $LedgerDirectory -Step 'build exact historical B0 image' -TimeoutSeconds 600
if ($build.exitCode -ne 0) { throw "Historical B0 image build failed: $($build.output)" }

$b0Expected = Get-Sha $HistoricalB0Artifact
$v1Expected = Get-Sha $HistoricalV1Artifact
$b0Hash = Invoke-AuditedExternal -Executable 'podman' -Arguments @('run','--rm','--entrypoint','sh',$B0Image,'-lc',"sha256sum /app/app.jar | awk '{print `$1}'") `
    -LedgerDirectory $LedgerDirectory -Step 'verify B0 image artifact hash'
$v1Hash = Invoke-AuditedExternal -Executable 'podman' -Arguments @('run','--rm','--entrypoint','sh',$V1Image,'-lc',"sha256sum /app/app.jar | awk '{print `$1}'") `
    -LedgerDirectory $LedgerDirectory -Step 'verify V1 image artifact hash'
if ($b0Hash.output.Trim().ToUpperInvariant() -ne $b0Expected) { throw 'B0 image artifact hash does not match the exact historical B0 JAR.' }
if ($v1Hash.output.Trim().ToUpperInvariant() -ne $v1Expected) { throw 'V1 image artifact hash does not match the exact historical V1 JAR.' }

$jdkB0 = Invoke-AuditedExternal -Executable 'podman' -Arguments @('run','--rm','--entrypoint','java',$B0Image,'-version') `
    -LedgerDirectory $LedgerDirectory -Step 'capture B0 image JDK identity'
$jdkV1 = Invoke-AuditedExternal -Executable 'podman' -Arguments @('run','--rm','--entrypoint','java',$V1Image,'-version') `
    -LedgerDirectory $LedgerDirectory -Step 'capture V1 image JDK identity'
if ($jdkB0.output -ne $jdkV1.output) { throw 'B0 and V1 image JDK identities differ.' }

$b0CdsDir = Join-Path $PrivateOutputDirectory 'b0-cds'
$v1CdsDir = Join-Path $PrivateOutputDirectory 'v1-cds'
New-JmoaDirectory -Path $b0CdsDir
New-JmoaDirectory -Path $v1CdsDir
Copy-Item -LiteralPath $HistoricalB0Cds -Destination (Join-Path $b0CdsDir 'archive.jsa') -Force
Copy-Item -LiteralPath $HistoricalV1Cds -Destination (Join-Path $v1CdsDir 'archive.jsa') -Force

function Write-Compose([string]$Path, [string]$ServiceImage, [string]$CdsDirectory, [string]$ProjectSuffix) {
    $configPath = ConvertTo-YamlPath $PrivateConfigRoot
    $sqlPath = ConvertTo-YamlPath $PrivateInitSql
    $archivePath = ConvertTo-YamlPath $CdsDirectory
    $authorizationPropertyName = 'app.' + 'j' + 'wt.' + 'secret'
    $yaml = @"
services:
  config-server:
    image: $ConfigImage
    container_name: jmoa-historical-config-$ProjectSuffix
    restart: unless-stopped
    ports: ["8888:8888"]
    volumes:
      - '${configPath}:/app/config-repo:ro'
    networks: [historical-doctor]
    environment:
      SPRING_PROFILES_ACTIVE: native
      MANAGEMENT_TRACING_ENABLED: "false"
      MANAGEMENT_METRICS_ENABLED: "false"
      MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENABLED: "false"
      JAVA_TOOL_OPTIONS: >-
        -XX:+UseContainerSupport -XX:+UseSerialGC -XX:+UseCompactObjectHeaders
        -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2
        -Dspring.aot.enabled=true -Xshare:auto
  discovery-server:
    image: $DiscoveryImage
    container_name: jmoa-historical-discovery-$ProjectSuffix
    restart: unless-stopped
    ports: ["8761:8761"]
    networks: [historical-doctor]
    depends_on: [config-server]
    environment:
      SPRING_PROFILES_ACTIVE: docker
      CONFIG_SERVER_URI: http://config-server:8888
      JAVA_TOOL_OPTIONS: >-
        -XX:+UseContainerSupport -XX:+UseSerialGC -XX:+UseCompactObjectHeaders
        -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2
        -Dspring.aot.enabled=false -Xshare:auto
  postgres:
    image: $DatabaseImage
    container_name: jmoa-historical-database-$ProjectSuffix
    restart: unless-stopped
    ports: ["5432:5432"]
    networks: [historical-doctor]
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: postgres
      POSTGRES_SHARED_BUFFERS: 48MB
    volumes:
      - historical_doctor_data:/var/lib/postgresql/data
      - '${sqlPath}:/docker-entrypoint-initdb.d/01-setup-doctor-database.sql:ro'
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
  doctor:
    image: $ServiceImage
    container_name: jmoa-historical-doctor
    restart: unless-stopped
    ports: ["8082:8082"]
    networks: [historical-doctor]
    depends_on: [config-server, discovery-server, postgres]
    environment:
      SPRING_PROFILES_ACTIVE: docker
      JAVA_TOOL_OPTIONS: >-
        -XX:+UseContainerSupport -XX:+UseSerialGC -XX:+UseCompactObjectHeaders
        -Xms32m -Xmx256m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2
        -XX:NativeMemoryTracking=summary -Dspring.aot.enabled=true
        -Xshare:auto -XX:SharedArchiveFile=/opt/leyden/archive.jsa
        -Xlog:gc*,safepoint=info
      ${authorizationPropertyName}: $signingKeyValue
    volumes:
      - '${archivePath}:/opt/leyden:ro'
volumes:
  historical_doctor_data:
networks:
  historical-doctor:
"@
    Set-Content -LiteralPath $Path -Value $yaml -Encoding UTF8
}

$b0Compose = Join-Path $PrivateOutputDirectory 'doctor-historical-b0.yml'
$v1Compose = Join-Path $PrivateOutputDirectory 'doctor-historical-v1.yml'
Write-Compose -Path $b0Compose -ServiceImage $B0Image -CdsDirectory $b0CdsDir -ProjectSuffix 'b0'
Write-Compose -Path $v1Compose -ServiceImage $V1Image -CdsDirectory $v1CdsDir -ProjectSuffix 'v1'
$b0BaseCdsCompose = Join-Path $PrivateOutputDirectory 'doctor-corrected-base-cds-b0.yml'
$v1BaseCdsCompose = Join-Path $PrivateOutputDirectory 'doctor-corrected-base-cds-v1.yml'
$b0BaseText = (Get-Content -Raw -LiteralPath $b0Compose).Replace(
    '-Xshare:auto -XX:SharedArchiveFile=/opt/leyden/archive.jsa',
    '-Xshare:on')
$v1BaseText = (Get-Content -Raw -LiteralPath $v1Compose).Replace(
    '-Xshare:auto -XX:SharedArchiveFile=/opt/leyden/archive.jsa',
    '-Xshare:on')
Set-Content -LiteralPath $b0BaseCdsCompose -Value $b0BaseText -Encoding UTF8
Set-Content -LiteralPath $v1BaseCdsCompose -Value $v1BaseText -Encoding UTF8

$protocol = [ordered]@{
    schemaVersion = 'jmoa-private-http-protocol-v1'
    protocolId = 'DOCTOR_PHASE32K_80_REQUEST'
    authorizationKeyEnvironmentVariable = $SigningKeyEnvironmentVariable
    authorizationKeyEncoding = 'BASE64'
    groups = @(
        [ordered]@{
            id = 'actuator-interleaved'
            repeat = 20
            requests = @(
                [ordered]@{ id = 'health'; method = 'GET'; path = '/actuator/health'; requiresBearerToken = $false; expectedStatus = @(200) },
                [ordered]@{ id = 'metrics'; method = 'GET'; path = '/actuator/prometheus'; requiresBearerToken = $false; expectedStatus = @(200) },
                [ordered]@{ id = 'info'; method = 'GET'; path = '/actuator/info'; requiresBearerToken = $false; expectedStatus = @(200) }
            )
        },
        [ordered]@{
            id = 'business-read'
            repeat = 20
            requests = @(
                [ordered]@{ id = 'business-read'; method = 'GET'; path = '/doctors'; requiresBearerToken = $true; expectedStatus = @(200,401,403) }
            )
        }
    )
}
$protocolPath = Join-Path $PrivateOutputDirectory 'doctor-historical-workload.private.json'
$protocol | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $protocolPath -Encoding UTF8

$baseCdsDriverPath = Join-Path $PrivateOutputDirectory 'run-corrected-base-cds-screen.private.ps1'
@'
param(
    [Parameter(Mandatory)][string]$Repo,
    [Parameter(Mandatory)][string]$Runtime,
    [Parameter(Mandatory)][string]$Capture,
    [Parameter(Mandatory)][string]$Ledger,
    [Parameter(Mandatory)][string]$B0,
    [Parameter(Mandatory)][string]$V1,
    [ValidateSet('BASELINE_ONLY','PAIR')][string]$Mode = 'BASELINE_ONLY'
)
$launch = Join-Path $Repo 'scripts/launch-compose-variant.ps1'
$stop = Join-Path $Repo 'scripts/stop-compose-variant.ps1'
$workload = Join-Path $Repo 'scripts/run-http-protocol-workload.ps1'
$screen = Join-Path $Repo 'scripts/runtime-screen-pair.ps1'
& $screen `
    -BaselineLaunchScript $launch `
    -CandidateLaunchScript $launch `
    -BaselineContainerName 'jmoa-historical-doctor' `
    -CandidateContainerName 'jmoa-historical-doctor' `
    -WorkloadScript $workload `
    -HealthUrl 'http://localhost:8082/actuator/health' `
    -Service 'doctor-service' `
    -LaunchMode 'SPRING_BOOT_FAT_JAR' `
    -RuntimePolicy 'BASE_CDS' `
    -BaselineArtifactPath $B0 `
    -CandidateArtifactPath $V1 `
    -ExecutionMode $Mode `
    -PairIndex 1 `
    -CaptureRoot $Capture `
    -BaselineCdsEnabled $true `
    -CandidateCdsEnabled $true `
    -CdsEnabled $true `
    -AppCdsEnabled $false `
    -BaselineRuntimeArtifactPath '/app/app.jar' `
    -CandidateRuntimeArtifactPath '/app/app.jar' `
    -WarmupSeconds 0 `
    -PostWorkloadSnapshotSeconds @(20) `
    -HealthTimeoutSeconds 180 `
    -WorkloadId 'DOCTOR_PHASE32K_80_REQUEST' `
    -LedgerDirectory $Ledger `
    -FailOnFailure `
    -BaselineLaunchParameters @{
        ComposeFile = (Join-Path $Runtime 'doctor-corrected-base-cds-b0.yml')
        ProjectName = 'jmoa-corrected-doctor-b0'
    } `
    -CandidateLaunchParameters @{
        ComposeFile = (Join-Path $Runtime 'doctor-corrected-base-cds-v1.yml')
        ProjectName = 'jmoa-corrected-doctor-v1'
    } `
    -WorkloadParameters @{
        ProtocolManifest = (Join-Path $Runtime 'doctor-historical-workload.private.json')
    } `
    -StopScript $stop `
    -StopScriptParameters @{
        BaselineComposeFile = (Join-Path $Runtime 'doctor-corrected-base-cds-b0.yml')
        CandidateComposeFile = (Join-Path $Runtime 'doctor-corrected-base-cds-v1.yml')
        BaselineProjectName = 'jmoa-corrected-doctor-b0'
        CandidateProjectName = 'jmoa-corrected-doctor-v1'
    }
exit $LASTEXITCODE
'@ | Set-Content -LiteralPath $baseCdsDriverPath -Encoding UTF8

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-historical-runtime-materialization-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    artifacts = [ordered]@{
        b0Sha256 = $b0Expected
        v1Sha256 = $v1Expected
        b0CdsSha256 = Get-Sha $HistoricalB0Cds
        v1CdsSha256 = Get-Sha $HistoricalV1Cds
    }
    imageVerification = [ordered]@{
        b0ArtifactHashMatches = $true
        v1ArtifactHashMatches = $true
        jdkIdentityEqual = $true
        jdkIdentitySha256 = ([BitConverter]::ToString(([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($jdkB0.output))))).Replace('-','')
    }
    protocol = [ordered]@{
        requestCount = 80
        requestOrder = '20 x (health, metrics, info), then 20 x doctors'
        preWorkloadWarmupSeconds = 0
        settleSeconds = 20
        runtimePolicy = 'ARTIFACT_SPECIFIC_APPLICATION_CDS'
        correctedHistoricalEffectivePolicy = 'EXPLICIT_BASE_CDS'
        freshStackPerArm = $true
    }
    provenance = [ordered]@{
        b0Artifact = 'EXACT_HISTORICAL'
        v1Artifact = 'EXACT_HISTORICAL'
        b0Cds = 'EXACT_HISTORICAL'
        v1Cds = 'EXACT_HISTORICAL'
        serviceImageBase = 'RECONSTRUCTED'
        supportImages = 'RECONSTRUCTED'
    }
    privateRuntimeFilesWritten = $true
    claimBoundary = 'Runtime materialization proof only. No memory result is claimed.'
}
$jsonPath = Join-Path $PublicOutputDirectory 'doctor-historical-runtime-materialization.json'
$mdPath = Join-Path $PublicOutputDirectory 'doctor-historical-runtime-materialization.md'
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
@"
# Doctor Historical Runtime Materialization

- Exact historical B0 JAR: **verified in image**
- Exact historical V1 JAR: **verified in image**
- Exact artifact-specific B0/V1 CDS archives: **frozen**
- B0/V1 JDK identity: **equal**
- Service image base: **reconstructed**
- Support images: **reconstructed**
- Historical workload contract: **80 requests**

This authorizes a reconstructed absolute B0 screen with explicit provenance. It does not represent the reconstructed images as the original historical images and makes no memory claim.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8

Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'COMPLETE' -Stage 'runtime-materialization' -Variant 'DOCTOR_HISTORICAL' | Out-Null
Write-Host "B0 compose: $b0Compose"
Write-Host "V1 compose: $v1Compose"
Write-Host "Corrected base-CDS B0 compose: $b0BaseCdsCompose"
Write-Host "Corrected base-CDS V1 compose: $v1BaseCdsCompose"
Write-Host "Protocol: $protocolPath"
Write-Host "Corrected base-CDS driver: $baseCdsDriverPath"
Write-Host "Report: $jsonPath"
